package com.urntt.dglab.feedback;

import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Watches the health of the second player of two-player mode. That only works while this client hosts a world
 * opened to LAN, because only then the other player's health is known here, on the integrated server.
 */
public final class SecondPlayerMonitor {
	/**
	 * The state of two-player mode for one client tick.
	 *
	 * @param active whether the second player is in the world
	 * @param name the second player's name, or an empty string while inactive
	 * @param lostHealth the share of health the second player has lost, from 0 to 1
	 * @param change the second player's damage and death since the previous tick
	 * @param left whether the second player left the world alive since the previous tick
	 */
	public record Reading(boolean active, String name, float lostHealth, HealthTracker.Change change, boolean left) {
		public static final Reading INACTIVE = new Reading(false, "", 0.0F, HealthTracker.Change.NONE, false);
	}

	/** A player's health as read on the server thread. */
	private record Sample(String name, boolean present, float health, float maxHealth) {
	}

	private final DgLabConfig config;
	private final HealthTracker tracker = new HealthTracker();
	private String trackedName = "";
	/** Written by the server thread, read by the client thread. */
	private volatile @Nullable Sample latest;

	public SecondPlayerMonitor(final DgLabConfig config) {
		this.config = config;
	}

	/**
	 * Called once per client tick.
	 */
	public Reading tick(final Minecraft client) {
		IntegratedServer server = client.getSingleplayerServer();
		String name = this.config.secondPlayer();
		if (!this.config.get(ToggleSetting.TWO_PLAYER) || client.player == null || server == null || !server.isPublished() || name.isEmpty()) {
			this.stopTracking();
			return Reading.INACTIVE;
		}

		// The player list belongs to the server thread, so the health is read there and picked up a tick later.
		UUID localId = client.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayerByName(name);
			boolean present = player != null && !player.getUUID().equals(localId);
			this.latest = new Sample(name, present, present ? player.getHealth() : 0.0F, present ? player.getMaxHealth() : 1.0F);
		});

		Sample sample = this.latest;
		if (sample == null || !sample.name().equals(name)) {
			this.tracker.reset();
			return Reading.INACTIVE;
		}
		if (!sample.present()) {
			boolean left = this.trackedName.equals(name) && this.tracker.isAlive();
			this.tracker.reset();
			return left ? new Reading(false, "", 0.0F, HealthTracker.Change.NONE, true) : Reading.INACTIVE;
		}

		if (!this.trackedName.equals(name)) {
			this.tracker.reset();
			this.trackedName = name;
		}
		HealthTracker.Change change = this.tracker.sample(sample.health());
		return new Reading(true, name, HealthTracker.lostShare(sample.health(), sample.maxHealth()), change, false);
	}

	private void stopTracking() {
		this.tracker.reset();
		this.trackedName = "";
		this.latest = null;
	}
}
