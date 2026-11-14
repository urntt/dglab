package com.urntt.dglab.gametest;

import com.urntt.dglab.protocol.AppCommands;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.protocol.SocketMessage;
import com.urntt.dglab.protocol.StrengthReport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stands in for the DG-LAB app in the game tests. It connects to the address a pairing QR code holds, binds the way
 * the app does, records every message it receives, and answers strength commands with strength reports like the
 * app: the strength changes as commanded, but never beyond the limit.
 */
final class FakeDgLabApp implements WebSocket.Listener, AutoCloseable {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long POLL_MILLIS = 10;
	private static final Pattern STRENGTH_COMMAND = Pattern.compile("strength-([12])\\+([012])\\+(\\d+)");

	private final HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
	private final WebSocket socket;
	private final String terminalId;
	private final List<SocketMessage> messages = new CopyOnWriteArrayList<>();
	private final StringBuilder partialText = new StringBuilder();
	private volatile boolean closed = false;
	private CompletableFuture<?> sendQueue = CompletableFuture.completedFuture(null);
	private final int[] strength = new int[2];
	private final int[] limit = new int[2];

	private FakeDgLabApp(final String webSocketUrl) {
		this.terminalId = webSocketUrl.substring(webSocketUrl.lastIndexOf('/') + 1);
		this.socket = this.client.newWebSocketBuilder()
				.connectTimeout(TIMEOUT)
				.buildAsync(URI.create(webSocketUrl), this)
				.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
				.join();
	}

	/**
	 * Connects to the WebSocket address at the end of {@code pairingUrl}, as the app does after scanning the code.
	 */
	static FakeDgLabApp connect(final String pairingUrl) {
		return new FakeDgLabApp(pairingUrl.substring(pairingUrl.lastIndexOf('#') + 1));
	}

	/**
	 * Waits for the ID the server assigns to this connection.
	 */
	String awaitAppId() {
		return this.await("the assigned app ID",
				message -> message.type().equals(SocketMessage.TYPE_BIND) && message.message().equals(SocketMessage.MESSAGE_ASSIGN_ID)).clientId();
	}

	/**
	 * Asks to be bound to the terminal named {@code terminalId} and returns the server's status code.
	 */
	String requestBind(final String terminalId) {
		String appId = this.awaitAppId();
		int answered = this.count(FakeDgLabApp::isBindAnswer);
		this.send(new SocketMessage(SocketMessage.TYPE_BIND, terminalId, appId, SocketMessage.MESSAGE_BIND_REQUEST));
		this.awaitCondition("an answer to the bind request", () -> this.count(FakeDgLabApp::isBindAnswer) > answered);
		return this.messages.stream().filter(FakeDgLabApp::isBindAnswer).reduce((first, second) -> second).orElseThrow().message();
	}

	/**
	 * Binds to the terminal whose ID was in the pairing address, then reports the given limits with zero strength.
	 */
	void bind(final int limitA, final int limitB) {
		String code = this.requestBind(this.terminalId);
		GameTestSupport.check(code.equals(SocketMessage.CODE_OK), "expected the bind to succeed, got code " + code);
		synchronized (this) {
			this.limit[0] = limitA;
			this.limit[1] = limitB;
		}
		this.report();
	}

	/** Sends the current strengths and limits, as the app does whenever one of them changes. */
	synchronized void report() {
		String appId = this.awaitAppId();
		this.send(new SocketMessage(SocketMessage.TYPE_MSG, this.terminalId, appId,
				new StrengthReport(this.strength[0], this.strength[1], this.limit[0], this.limit[1]).toMessage()));
	}

	/** Changes a channel's strength as if the user had done it in the app. */
	synchronized void setStrength(final Channel channel, final int value) {
		this.strength[channel.ordinal()] = Math.clamp(value, 0, this.limit[channel.ordinal()]);
		this.report();
	}

	synchronized int strength(final Channel channel) {
		return this.strength[channel.ordinal()];
	}

	void send(final SocketMessage message) {
		this.sendRaw(message.toJson());
	}

	/**
	 * Queues a text message. A WebSocket accepts one send at a time, and messages are sent both by the test and in
	 * answer to commands, so each send waits for the one before it.
	 */
	synchronized void sendRaw(final String text) {
		this.sendQueue = this.sendQueue.thenCompose(done -> this.socket.sendText(text, true));
	}

	/** The commands received so far, in order: the {@code message} of every {@code msg} message. */
	List<String> commands() {
		List<String> commands = new ArrayList<>();
		for (SocketMessage message : this.messages) {
			if (message.type().equals(SocketMessage.TYPE_MSG)) {
				commands.add(message.message());
			}
		}
		return commands;
	}

	/** The commands received after the first {@code skip} ones. */
	List<String> commandsAfter(final int skip) {
		List<String> commands = this.commands();
		return commands.subList(Math.min(skip, commands.size()), commands.size());
	}

	/**
	 * Waits until a message matching {@code condition} has been received and returns the first one.
	 */
	SocketMessage await(final String description, final Predicate<SocketMessage> condition) {
		this.awaitCondition(description, () -> this.messages.stream().anyMatch(condition));
		return this.messages.stream().filter(condition).findFirst().orElseThrow();
	}

	void awaitClosed() {
		this.awaitCondition("the connection to close", () -> this.closed);
	}

	/**
	 * Disconnects. The connection is dropped without waiting for a closing handshake, as when a phone leaves the
	 * network.
	 */
	@Override
	public void close() {
		this.socket.abort();
		this.client.shutdownNow();
		this.closed = true;
	}

	@Override
	public CompletionStage<?> onText(final WebSocket webSocket, final CharSequence data, final boolean last) {
		this.partialText.append(data);
		if (last) {
			String text = this.partialText.toString();
			this.partialText.setLength(0);
			Optional<SocketMessage> message = SocketMessage.parse(text);
			if (message.isEmpty()) {
				throw new AssertionError("the server sent something that is not a protocol message: " + text);
			}
			if (text.length() > SocketMessage.MAX_LENGTH) {
				throw new AssertionError("the server sent a message of " + text.length() + " characters");
			}
			this.messages.add(message.get());
			this.applyStrengthCommand(message.get());
		}
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(final WebSocket webSocket, final int statusCode, final String reason) {
		this.closed = true;
		return null;
	}

	@Override
	public void onError(final WebSocket webSocket, final Throwable error) {
		this.closed = true;
	}

	private synchronized void applyStrengthCommand(final SocketMessage message) {
		if (!message.type().equals(SocketMessage.TYPE_MSG)) {
			return;
		}
		Matcher matcher = STRENGTH_COMMAND.matcher(message.message());
		if (!matcher.matches()) {
			return;
		}
		int channel = Integer.parseInt(matcher.group(1)) - 1;
		int value = Integer.parseInt(matcher.group(3));
		int changed = switch (matcher.group(2)) {
			case "0" -> this.strength[channel] - value;
			case "1" -> this.strength[channel] + value;
			default -> value;
		};
		this.strength[channel] = Math.clamp(changed, 0, Math.min(this.limit[channel], AppCommands.MAX_STRENGTH));
		this.report();
	}

	private int count(final Predicate<SocketMessage> condition) {
		return (int) this.messages.stream().filter(condition).count();
	}

	private static boolean isBindAnswer(final SocketMessage message) {
		return message.type().equals(SocketMessage.TYPE_BIND) && !message.message().equals(SocketMessage.MESSAGE_ASSIGN_ID);
	}

	private void awaitCondition(final String description, final BooleanSupplier condition) {
		long deadline = System.nanoTime() + TIMEOUT.toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("timed out waiting for " + description + "; received " + this.messages);
			}
			try {
				Thread.sleep(POLL_MILLIS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("interrupted while waiting for " + description, e);
			}
		}
	}
}
