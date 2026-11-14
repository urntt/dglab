package com.urntt.dglab;

import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.feedback.HealthTracker;
import com.urntt.dglab.feedback.SecondPlayerMonitor;
import com.urntt.dglab.feedback.StrengthController;
import com.urntt.dglab.protocol.StrengthReport;
import com.urntt.dglab.server.PairingServer;
import com.urntt.dglab.server.PairingUrl;
import java.util.concurrent.Executor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The single owner of the mod's runtime state. Once per client tick it brings the connection server in line with
 * the configuration and feeds the game's state into the {@link StrengthController}.
 */
public final class DgLabController implements PairingServer.Listener {
	/** Shown in chat when an app binds, and on the pairing screen. */
	public static final Component SAFETY_NOTICE = Component.translatable("message.dglab.safety_notice").withStyle(ChatFormatting.YELLOW);
	public static final Component BOUND_MESSAGE = Component.translatable("message.dglab.bound");
	public static final Component UNBOUND_MESSAGE = Component.translatable("message.dglab.unbound");

	private final DgLabConfig config;
	private final PairingServer server;
	private final StrengthController strength;
	private final SecondPlayerMonitor secondPlayer;
	private String secondPlayerName = "";

	/**
	 * @param clientThread runs tasks on the client thread, one after another in submission order
	 */
	public DgLabController(final DgLabConfig config, final Executor clientThread) {
		this.config = config;
		this.server = new PairingServer(clientThread, this);
		this.strength = new StrengthController(config, this.server::send);
		this.secondPlayer = new SecondPlayerMonitor(config);
	}

	public PairingServer server() {
		return this.server;
	}

	public StrengthController strength() {
		return this.strength;
	}

	/** The text of the pairing QR code for the current settings. */
	public String pairingUrl() {
		return PairingUrl.of(this.config, this.server.terminalId());
	}

	/** The name of the player who currently drives channel B, or an empty string outside two-player mode. */
	public String secondPlayerName() {
		return this.secondPlayerName;
	}

	/**
	 * Called at the end of every client tick, in a world or not.
	 */
	public void tick(final Minecraft client) {
		this.reconcileServer();

		LocalPlayer player = client.player;
		boolean inWorld = player != null && client.level != null;
		float localLostHealth = inWorld ? HealthTracker.lostShare(player.getHealth(), player.getMaxHealth()) : 0.0F;

		SecondPlayerMonitor.Reading second = this.secondPlayer.tick(client);
		this.secondPlayerName = second.name();
		this.onHealthChange(StrengthController.Source.SECOND, second.change());
		if (second.left()) {
			this.strength.onSecondPlayerLeft();
		}

		this.strength.tick(inWorld, second.active(), localLostHealth, second.lostHealth());
	}

	/**
	 * Applies what happened to a player's health since it was last looked at.
	 */
	public void onHealthChange(final StrengthController.Source source, final HealthTracker.Change change) {
		if (change.damage() > 0.0F) {
			this.strength.onDamage(source, change.damage());
		}
		if (change.died()) {
			this.strength.onDeath(source);
		}
	}

	/**
	 * Starts the connection server again, for example after its port turned out to be in use.
	 */
	public void restartServer() {
		this.stopServer();
		if (this.config.get(ToggleSetting.SERVER_ENABLED)) {
			this.server.start(this.config.listenPort());
		}
	}

	/**
	 * Brings both channels to zero and shuts the connection server down for good. Called when the game exits.
	 */
	public void close() {
		this.zeroOutput();
		this.server.close();
	}

	@Override
	public void onBound() {
		this.strength.onBound();
		notifyPlayer(BOUND_MESSAGE);
		notifyPlayer(SAFETY_NOTICE);
	}

	@Override
	public void onUnbound() {
		this.strength.onUnbound();
		notifyPlayer(UNBOUND_MESSAGE);
	}

	@Override
	public void onStrengthReport(final StrengthReport report) {
		this.strength.onReport(report);
	}

	private void reconcileServer() {
		PairingServer.State state = this.server.state();
		if (!this.config.get(ToggleSetting.SERVER_ENABLED)) {
			if (state != PairingServer.State.STOPPED) {
				this.stopServer();
			}
		} else if (state == PairingServer.State.STOPPED || this.server.port() != this.config.listenPort()) {
			// A server that failed to start is left alone until the port changes or the server is toggled.
			this.stopServer();
			this.server.start(this.config.listenPort());
		}
	}

	private void stopServer() {
		this.zeroOutput();
		this.server.stop();
	}

	/** Leaves the device silent before the connection goes away. */
	private void zeroOutput() {
		this.strength.silenceAll();
	}

	private static void notifyPlayer(final Component message) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(message);
		}
	}
}
