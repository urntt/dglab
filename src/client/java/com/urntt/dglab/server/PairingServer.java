package com.urntt.dglab.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.protocol.AppCommands;
import com.urntt.dglab.protocol.SocketMessage;
import com.urntt.dglab.protocol.StrengthReport;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The WebSocket server the DG-LAB app connects to after scanning the pairing QR code. In the app's socket control
 * protocol a relay server sits between the app and a controlling terminal; this class plays both of those parts.
 *
 * <p>Any number of apps may connect, but only one at a time is bound, and only by naming this server's terminal ID,
 * which is random for each game start and only shown in the QR code. An app that binds replaces the one bound
 * before it.
 */
public final class PairingServer {
	public enum State {
		STOPPED,
		STARTING,
		/** Listening, with no app bound. */
		LISTENING,
		/** An app is bound and receives commands. */
		BOUND,
		/** The port could not be opened. */
		FAILED
	}

	/** Receives the server's events, in the order they happened, through the executor given to the constructor. */
	public interface Listener {
		void onBound();

		void onUnbound();

		void onStrengthReport(StrengthReport report);
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(DgLabClient.MOD_ID);
	private static final int MAX_MESSAGE_BYTES = 65536;
	private static final long HEARTBEAT_SECONDS = 60;

	private final String terminalId = UUID.randomUUID().toString();
	private final Executor callbacks;
	private final Listener listener;
	private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(
			1, new ThreadFactoryBuilder().setNameFormat("dglab pairing server IO #%d").setDaemon(true).build(), NioIoHandler.newFactory());
	private final ChannelGroup connections = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

	private State state = State.STOPPED;
	private int port;
	private @Nullable Channel serverChannel;
	private @Nullable Channel boundChannel;
	private @Nullable String boundAppId;

	/**
	 * @param callbacks runs the listener's methods; it must run them one after another in submission order
	 */
	public PairingServer(final Executor callbacks, final Listener listener) {
		this.callbacks = callbacks;
		this.listener = listener;
	}

	/** The ID the app must name to bind. It is part of the pairing QR code. */
	public String terminalId() {
		return this.terminalId;
	}

	public synchronized State state() {
		return this.state;
	}

	/** The port of the last {@link #start(int)}. */
	public synchronized int port() {
		return this.port;
	}

	/**
	 * Starts listening on {@code port} on all network interfaces, after stopping the server if it runs.
	 */
	public synchronized void start(final int port) {
		this.stop();
		this.port = port;
		this.state = State.STARTING;

		ChannelFuture bind = new ServerBootstrap()
				.group(this.group)
				.channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<Channel>() {
					@Override
					protected void initChannel(final Channel channel) {
						channel.pipeline()
								.addLast(new HttpServerCodec())
								.addLast(new HttpObjectAggregator(MAX_MESSAGE_BYTES))
								// The app connects to the address in the QR code, whose path is the terminal ID.
								.addLast(new WebSocketServerProtocolHandler(WebSocketServerProtocolConfig.newBuilder()
										.websocketPath("/")
										.checkStartsWith(true)
										.maxFramePayloadLength(MAX_MESSAGE_BYTES)
										.build()))
								.addLast(new WebSocketFrameAggregator(MAX_MESSAGE_BYTES))
								.addLast(new AppConnection());
					}
				})
				.bind(port);
		Channel channel = bind.channel();
		this.serverChannel = channel;
		bind.addListener(future -> this.onBindFinished(channel, future.isSuccess(), future.cause()));
	}

	/**
	 * Stops listening and disconnects every app.
	 */
	public synchronized void stop() {
		if (this.serverChannel != null) {
			this.serverChannel.close();
			this.serverChannel = null;
		}
		this.connections.close();
		this.unbind();
		this.state = State.STOPPED;
	}

	/**
	 * Sends a command to the bound app. Returns false if no app is bound or the message would exceed the protocol's
	 * length limit.
	 */
	public synchronized boolean send(final String command) {
		if (this.boundChannel == null || this.boundAppId == null) {
			return false;
		}
		String json = new SocketMessage(SocketMessage.TYPE_MSG, this.terminalId, this.boundAppId, command).toJson();
		if (json.length() > SocketMessage.MAX_LENGTH) {
			LOGGER.warn("Dropped a command of {} characters, the app accepts at most {}", json.length(), SocketMessage.MAX_LENGTH);
			return false;
		}
		this.boundChannel.writeAndFlush(new TextWebSocketFrame(json));
		return true;
	}

	/**
	 * Stops the server for good and releases its thread.
	 */
	public void close() {
		this.stop();
		this.group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
	}

	private synchronized void onBindFinished(final Channel channel, final boolean success, final @Nullable Throwable cause) {
		if (channel != this.serverChannel) {
			// The server was stopped or restarted in the meantime.
			channel.close();
			return;
		}
		if (success) {
			this.state = State.LISTENING;
			LOGGER.info("Pairing server listening on port {}", this.port);
		} else {
			this.state = State.FAILED;
			this.serverChannel = null;
			LOGGER.warn("Pairing server could not listen on port {}", this.port, cause);
		}
	}

	private synchronized void handleBind(final Channel channel, final String appId, final SocketMessage request) {
		if (this.serverChannel == null) {
			return;
		}
		String code;
		if (!SocketMessage.MESSAGE_BIND_REQUEST.equals(request.message())
				|| !this.terminalId.equals(request.clientId())
				|| !appId.equals(request.targetId())) {
			code = SocketMessage.CODE_NO_SUCH_TARGET;
		} else {
			code = SocketMessage.CODE_OK;
			if (channel != this.boundChannel) {
				this.releaseBoundApp();
				this.boundChannel = channel;
				this.boundAppId = appId;
				this.state = State.BOUND;
				this.scheduleHeartbeat(channel, appId);
				this.callbacks.execute(this.listener::onBound);
			}
		}
		write(channel, new SocketMessage(SocketMessage.TYPE_BIND, request.clientId(), request.targetId(), code));
	}

	/**
	 * Drops the bound app in favor of one that just named the terminal ID. Whoever scanned the current QR code
	 * takes over, because the previous app may be gone without having closed its connection, as when a phone leaves
	 * the network. In case it is still there, its device is left silent.
	 */
	private void releaseBoundApp() {
		Channel previous = this.boundChannel;
		String previousAppId = this.boundAppId;
		if (previous == null || previousAppId == null) {
			return;
		}
		for (String command : AppCommands.silenceAll()) {
			write(previous, new SocketMessage(SocketMessage.TYPE_MSG, this.terminalId, previousAppId, command));
		}
		write(previous, new SocketMessage(SocketMessage.TYPE_BREAK, this.terminalId, previousAppId, SocketMessage.CODE_PEER_DISCONNECTED));
		this.unbind();
		previous.close();
	}

	private synchronized void handleMessage(final Channel channel, final SocketMessage message) {
		if (channel != this.boundChannel) {
			write(channel, new SocketMessage(SocketMessage.TYPE_ERROR, message.clientId(), message.targetId(), SocketMessage.CODE_NOT_BOUND));
			return;
		}
		Optional<StrengthReport> report = StrengthReport.parse(message.message());
		report.ifPresent(strengthReport -> this.callbacks.execute(() -> this.listener.onStrengthReport(strengthReport)));
	}

	private synchronized void handleDisconnect(final Channel channel) {
		if (channel == this.boundChannel) {
			this.unbind();
			if (this.state == State.BOUND) {
				this.state = State.LISTENING;
			}
		}
	}

	private void unbind() {
		if (this.boundChannel != null) {
			this.boundChannel = null;
			this.boundAppId = null;
			this.callbacks.execute(this.listener::onUnbound);
		}
	}

	private void scheduleHeartbeat(final Channel channel, final String appId) {
		SocketMessage heartbeat = new SocketMessage(SocketMessage.TYPE_HEARTBEAT, appId, this.terminalId, SocketMessage.CODE_OK);
		ScheduledFuture<?> task = channel.eventLoop().scheduleAtFixedRate(
				() -> write(channel, heartbeat), HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
		channel.closeFuture().addListener(future -> task.cancel(false));
	}

	private static void write(final Channel channel, final SocketMessage message) {
		channel.writeAndFlush(new TextWebSocketFrame(message.toJson()));
	}

	/**
	 * Handles one connected app. Each connection gets its own app ID.
	 */
	private final class AppConnection extends SimpleChannelInboundHandler<TextWebSocketFrame> {
		private final String appId = UUID.randomUUID().toString();

		@Override
		public void userEventTriggered(final ChannelHandlerContext context, final Object event) throws Exception {
			if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
				PairingServer.this.connections.add(context.channel());
				// The protocol's first message tells a connection its own ID.
				write(context.channel(), new SocketMessage(SocketMessage.TYPE_BIND, this.appId, "", SocketMessage.MESSAGE_ASSIGN_ID));
			}
			super.userEventTriggered(context, event);
		}

		@Override
		protected void channelRead0(final ChannelHandlerContext context, final TextWebSocketFrame frame) {
			Optional<SocketMessage> parsed = SocketMessage.parse(frame.text());
			if (parsed.isEmpty()) {
				write(context.channel(), new SocketMessage(SocketMessage.TYPE_ERROR, "", "", SocketMessage.CODE_NOT_JSON));
				return;
			}
			SocketMessage message = parsed.get();
			switch (message.type()) {
				case SocketMessage.TYPE_BIND -> PairingServer.this.handleBind(context.channel(), this.appId, message);
				case SocketMessage.TYPE_MSG -> PairingServer.this.handleMessage(context.channel(), message);
				default -> {
				}
			}
		}

		@Override
		public void channelInactive(final ChannelHandlerContext context) throws Exception {
			PairingServer.this.handleDisconnect(context.channel());
			super.channelInactive(context);
		}

		@Override
		public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
			LOGGER.debug("Closing a pairing connection after an error", cause);
			context.close();
		}
	}
}
