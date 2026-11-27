package com.urntt.dglab.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The numeric settings each channel has. Values are stored as multiples of {@code 1 / stepsPerUnit}.
 */
public enum ChannelOption {
	/** Strength added per point of damage (half a heart). */
	STRENGTH_PER_DAMAGE("strength_per_damage", Unit.FACTOR, 0, 20, 10, 3),
	/** Ticks after damage during which the strength does not decay. */
	HOLD_TICKS("hold_ticks", Unit.TICKS, 0, 120, 1, 50),
	/** Ticks between two decay steps. */
	DECAY_INTERVAL("decay_interval", Unit.TICKS, 1, 120, 1, 5),
	/** Strength removed by one decay step. */
	DECAY_STEP("decay_step", Unit.STRENGTH, 0, 20, 1, 1),
	/** Strength added on death. */
	DEATH_STRENGTH("death_strength", Unit.STRENGTH, 0, 200, 1, 50),
	/** Ticks after death during which the strength does not decay. */
	DEATH_HOLD_TICKS("death_hold_ticks", Unit.TICKS, 0, 120, 1, 50),
	/** The floor the strength decays to when all health is lost; it scales with the share of health lost. */
	FLOOR_STRENGTH("floor_strength", Unit.STRENGTH, 0, 200, 1, 40);

	/** How a value is shown to the player. */
	public enum Unit {
		FACTOR,
		TICKS,
		STRENGTH
	}

	private static final double TICKS_PER_SECOND = 20.0;

	private final String id;
	private final Unit unit;
	private final int min;
	private final int max;
	private final int stepsPerUnit;
	private final double defaultValue;

	ChannelOption(final String id, final Unit unit, final int min, final int max, final int stepsPerUnit,
			final double defaultValue) {
		this.id = id;
		this.unit = unit;
		this.min = min;
		this.max = max;
		this.stepsPerUnit = stepsPerUnit;
		this.defaultValue = defaultValue;
	}

	/** The name used in the configuration file, commands, and translation keys. */
	public String id() {
		return this.id;
	}

	public double defaultValue() {
		return this.defaultValue;
	}

	public Unit unit() {
		return this.unit;
	}

	public double min() {
		return this.min;
	}

	public double max() {
		return this.max;
	}

	/** The smallest change between two values this option can take. */
	public double step() {
		return 1.0 / this.stepsPerUnit;
	}

	/**
	 * Converts a value to the unit the player sees: tick counts become seconds.
	 */
	public double toDisplayUnit(final double value) {
		return this.unit == Unit.TICKS ? value / TICKS_PER_SECOND : value;
	}

	/**
	 * Converts a value the player entered back from {@link #toDisplayUnit(double)}.
	 */
	public double fromDisplayUnit(final double value) {
		return this.unit == Unit.TICKS ? value * TICKS_PER_SECOND : value;
	}

	/**
	 * Returns the closest value this option can take.
	 */
	public double normalize(final double value) {
		if (Double.isNaN(value)) {
			return this.defaultValue;
		}
		return this.fromStep(this.toStep(value));
	}

	/** Position of {@code value} on a slider that runs from {@link #minStep()} to {@link #maxStep()}. */
	public int toStep(final double value) {
		return (int) Math.round(Math.clamp(value, this.min, this.max) * this.stepsPerUnit);
	}

	public double fromStep(final int step) {
		return (double) Math.clamp(step, this.minStep(), this.maxStep()) / this.stepsPerUnit;
	}

	public int minStep() {
		return this.min * this.stepsPerUnit;
	}

	public int maxStep() {
		return this.max * this.stepsPerUnit;
	}

	public String captionKey() {
		return "options.dglab.strength." + this.id;
	}

	public Component caption() {
		return Component.translatable(this.captionKey());
	}

	public String tooltipKey() {
		return this.captionKey() + ".tooltip";
	}

	public Component tooltip() {
		return Component.translatable(this.tooltipKey());
	}

	/**
	 * Formats a value for display. Tick counts are shown in seconds.
	 */
	public Component format(final double value) {
		return switch (this.unit) {
			case FACTOR -> Component.literal(String.format(Locale.ROOT, "%.1f", value));
			case TICKS -> Component.translatable("options.dglab.value.seconds",
					String.format(Locale.ROOT, "%.2f", this.toDisplayUnit(value)));
			case STRENGTH -> Component.literal(Integer.toString((int) value));
		};
	}

	public static Optional<ChannelOption> byId(final String id) {
		return Arrays.stream(values()).filter(option -> option.id.equals(id)).findFirst();
	}
}
