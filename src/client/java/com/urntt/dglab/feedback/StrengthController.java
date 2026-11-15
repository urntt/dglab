package com.urntt.dglab.feedback;

import com.urntt.dglab.config.ChannelOption;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.protocol.AppCommands;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.protocol.StrengthReport;
import com.urntt.dglab.waveform.Waveform;
import com.urntt.dglab.waveform.WaveformSlot;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Turns game events into commands for the DG-LAB app. It holds the mod's view of both channels and applies the
 * strength rules once per client tick:
 *
 * <ul>
 *   <li>damage adds strength and starts a hold period,</li>
 *   <li>death raises the strength by a fixed amount and starts its own hold period,</li>
 *   <li>after the hold period the strength decays in steps, down to a floor that scales with lost health,</li>
 *   <li>the damage waveform loops during the hold period, the recovery waveform while strength remains.</li>
 * </ul>
 *
 * <p>The output multiplier scales every strength these rules produce: what damage and death add, and the floor.
 *
 * <p>The class has no dependency on the game, so the rules can be tested without a world.
 */
public final class StrengthController {
	/** Whose health an event or a floor refers to. */
	public enum Source {
		/** The player of this client. */
		LOCAL,
		/** The second player of two-player mode. */
		SECOND
	}

	private enum Phase {
		IDLE,
		DAMAGE,
		RECOVERY
	}

	/** Ticks between clearing a channel's pulse queue and sending the next waveform, so the app handles them in order. */
	private static final int CLEAR_SETTLE_TICKS = 1;
	private static final double PERCENT = 100.0;

	private final DgLabConfig config;
	private final Consumer<String> output;
	private final Map<Channel, ChannelState> channels = new EnumMap<>(Channel.class);
	private boolean bound = false;
	private boolean inWorld = false;
	private boolean twoPlayerActive = false;

	/**
	 * @param output receives each command for the app; it is only called while an app is bound
	 */
	public StrengthController(final DgLabConfig config, final Consumer<String> output) {
		this.config = config;
		this.output = output;
		for (Channel channel : Channel.values()) {
			this.channels.put(channel, new ChannelState());
		}
	}

	/** Called when an app has bound. Both channels start from a clean state. */
	public void onBound() {
		this.bound = true;
		this.channels.replaceAll((channel, state) -> new ChannelState());
	}

	public void onUnbound() {
		this.bound = false;
		this.channels.replaceAll((channel, state) -> new ChannelState());
	}

	/** Takes over the strengths and limits the app reports. */
	public void onReport(final StrengthReport report) {
		for (Channel channel : Channel.values()) {
			ChannelState state = this.channels.get(channel);
			state.limit = Math.clamp(report.limit(channel), 0, AppCommands.MAX_STRENGTH);
			state.strength = Math.clamp(report.strength(channel), 0, state.limit);
		}
	}

	/**
	 * Called when a player lost {@code amount} points of health.
	 */
	public void onDamage(final Source source, final float amount) {
		if (!this.isDriving() || !this.config.get(ToggleSetting.DAMAGE_RESPONSE) || amount <= 0.0F) {
			return;
		}
		for (Channel channel : Channel.values()) {
			double perDamage = this.config.value(channel, ChannelOption.STRENGTH_PER_DAMAGE) * this.multiplier();
			if (!this.responds(channel, source) || perDamage <= 0.0) {
				continue;
			}
			ChannelState state = this.channels.get(channel);
			int added = Math.max(1, (int) Math.floor(amount * perDamage));
			this.output.accept(AppCommands.increaseStrength(channel, added));
			state.strength = Math.min(state.strength + added, state.limit);
			state.hold = this.config.intValue(channel, ChannelOption.HOLD_TICKS);
		}
	}

	/**
	 * Called when a player died. A fatal hit is reported through {@link #onDamage} first.
	 */
	public void onDeath(final Source source) {
		if (!this.isDriving() || !this.config.get(ToggleSetting.DEATH_RESPONSE) || this.multiplier() <= 0.0) {
			return;
		}
		for (Channel channel : Channel.values()) {
			if (this.responds(channel, source)) {
				this.raise(channel, this.scaled(this.config.value(channel, ChannelOption.DEATH_STRENGTH)));
			}
		}
	}

	/**
	 * Called when the second player left the world while alive.
	 */
	public void onSecondPlayerLeft() {
		int strength = this.scaled(this.config.secondPlayerQuitStrength());
		if (this.isDriving() && strength > 0 && this.config.get(ToggleSetting.CHANNEL_B)) {
			this.raise(Channel.B, strength);
		}
	}

	/**
	 * Advances the rules by one client tick.
	 *
	 * @param inWorld whether the player is in a world
	 * @param twoPlayerActive whether a second player currently drives channel B
	 * @param localLostHealth the share of health the local player has lost, from 0 to 1
	 * @param secondLostHealth the same for the second player; ignored unless {@code twoPlayerActive}
	 */
	public void tick(final boolean inWorld, final boolean twoPlayerActive, final float localLostHealth,
			final float secondLostHealth) {
		this.inWorld = inWorld;
		this.twoPlayerActive = twoPlayerActive;
		if (!this.bound) {
			return;
		}

		for (Channel channel : Channel.values()) {
			ChannelState state = this.channels.get(channel);
			if (!this.isDriving() || !this.config.get(ToggleSetting.forChannel(channel))) {
				this.silence(channel, state);
				continue;
			}
			state.silenced = false;

			float lostHealth = this.sourceOf(channel) == Source.LOCAL ? localLostHealth : secondLostHealth;
			this.decay(channel, state, lostHealth);
			this.playWaveform(channel, state);
		}
	}

	/**
	 * Plays a waveform once on a channel, replacing whatever is queued. The regular waveform resumes afterwards.
	 */
	public void preview(final Channel channel, final Waveform waveform) {
		if (!this.bound) {
			return;
		}
		ChannelState state = this.channels.get(channel);
		this.output.accept(AppCommands.clear(channel));
		this.output.accept(AppCommands.pulse(channel, waveform.frames()));
		state.waveTimer = waveform.durationTicks();
	}

	/**
	 * Brings both channels to zero and empties their pulse queues, before the connection is given up.
	 */
	public void silenceAll() {
		if (!this.bound) {
			return;
		}
		this.channels.forEach((channel, state) -> {
			// Sent even if the channel already counts as silenced, because the strength may have been raised in the app.
			state.silenced = false;
			this.silence(channel, state);
		});
	}

	/**
	 * Sets a channel's strength, as the {@code /dglab strength set} command does.
	 */
	public void setStrength(final Channel channel, final int strength) {
		if (!this.bound) {
			return;
		}
		ChannelState state = this.channels.get(channel);
		state.strength = Math.clamp(strength, 0, state.limit);
		this.output.accept(AppCommands.setStrength(channel, state.strength));
	}

	public boolean isBound() {
		return this.bound;
	}

	/** The channel's strength as far as the mod knows: the app's last report, updated by the commands sent since. */
	public int strength(final Channel channel) {
		return this.channels.get(channel).strength;
	}

	/** The limit the user set in the app, or the protocol's maximum before the first report. */
	public int limit(final Channel channel) {
		return this.channels.get(channel).limit;
	}

	/** Whether a second player currently drives channel B. */
	public boolean isTwoPlayerActive() {
		return this.twoPlayerActive;
	}

	/** The factor the output multiplier setting stands for. */
	private double multiplier() {
		return this.config.outputMultiplier() / PERCENT;
	}

	/** Applies the output multiplier to a strength and rounds it to the nearest whole number. */
	private int scaled(final double strength) {
		return (int) Math.round(strength * this.multiplier());
	}

	/** Whether game events currently reach the device. */
	private boolean isDriving() {
		return this.bound && this.inWorld && this.config.get(ToggleSetting.ENABLED);
	}

	private Source sourceOf(final Channel channel) {
		return channel == Channel.B && this.twoPlayerActive ? Source.SECOND : Source.LOCAL;
	}

	private boolean responds(final Channel channel, final Source source) {
		return this.config.get(ToggleSetting.forChannel(channel)) && this.sourceOf(channel) == source;
	}

	/** Raises a channel by a fixed amount, up to the limit, and holds it as after a death. */
	private void raise(final Channel channel, final int amount) {
		ChannelState state = this.channels.get(channel);
		state.strength = Math.min(state.strength + amount, state.limit);
		this.output.accept(AppCommands.setStrength(channel, state.strength));
		state.hold = this.config.intValue(channel, ChannelOption.DEATH_HOLD_TICKS);
	}

	/** Brings a channel to zero once, when it stops being driven. */
	private void silence(final Channel channel, final ChannelState state) {
		if (state.silenced) {
			return;
		}
		AppCommands.silence(channel).forEach(this.output);
		state.silenced = true;
		state.strength = 0;
		state.hold = 0;
		state.phase = Phase.IDLE;
	}

	private void decay(final Channel channel, final ChannelState state, final float lostHealth) {
		int interval = this.config.intValue(channel, ChannelOption.DECAY_INTERVAL);
		if (state.hold > 0) {
			state.hold--;
			state.decayTimer = interval;
			return;
		}

		int step = this.config.intValue(channel, ChannelOption.DECAY_STEP);
		int floor = this.config.get(ToggleSetting.HEALTH_FLOOR)
				// Rounded to the nearest whole number, because the lost share is rarely an exact fraction.
				? this.scaled(this.config.value(channel, ChannelOption.FLOOR_STRENGTH) * Math.clamp(lostHealth, 0.0F, 1.0F))
				: 0;
		if (step <= 0 || state.strength <= floor) {
			state.decayTimer = interval;
			return;
		}
		if (--state.decayTimer > 0) {
			return;
		}

		state.decayTimer = interval;
		if (state.strength - step < floor) {
			state.strength = floor;
			this.output.accept(AppCommands.setStrength(channel, floor));
		} else {
			state.strength -= step;
			this.output.accept(AppCommands.decreaseStrength(channel, step));
		}
	}

	private void playWaveform(final Channel channel, final ChannelState state) {
		Phase phase = state.hold > 0 ? Phase.DAMAGE : state.strength > 0 ? Phase.RECOVERY : Phase.IDLE;
		if (phase != state.phase) {
			state.phase = phase;
			this.output.accept(AppCommands.clear(channel));
			state.waveTimer = CLEAR_SETTLE_TICKS;
		} else if (phase != Phase.IDLE && --state.waveTimer <= 0) {
			WaveformSlot.Kind kind = phase == Phase.DAMAGE ? WaveformSlot.Kind.DAMAGE : WaveformSlot.Kind.RECOVERY;
			Waveform waveform = this.config.waveform(WaveformSlot.of(channel, kind));
			this.output.accept(AppCommands.pulse(channel, waveform.frames()));
			state.waveTimer = waveform.durationTicks();
		}
	}

	private static final class ChannelState {
		private int strength = 0;
		private int limit = AppCommands.MAX_STRENGTH;
		/** Ticks left during which the strength does not decay. */
		private int hold = 0;
		/** Ticks until the next decay step. */
		private int decayTimer = 0;
		private Phase phase = Phase.IDLE;
		/** Ticks until the current phase's waveform is sent again. */
		private int waveTimer = 0;
		/** Whether the channel was brought to zero because it is not driven. */
		private boolean silenced = false;
	}
}
