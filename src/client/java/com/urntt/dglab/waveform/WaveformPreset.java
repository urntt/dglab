package com.urntt.dglab.waveform;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The built-in waveforms a slot can use instead of a custom one.
 */
public enum WaveformPreset {
	/** A full-strength burst that fades out. */
	THUMP("thump", Waveform.ofIntensities(Waveform.MIN_FREQUENCY,
			100, 100, 100, 100,
			100, 100, 100, 100,
			100, 80, 60, 40,
			20, 0, 0, 0)),
	/** A slow rise and fall. */
	SWELL("swell", Waveform.ofIntensities(Waveform.MIN_FREQUENCY,
			20, 25, 30, 35,
			40, 45, 50, 55,
			60, 65, 70, 75,
			80, 75, 70, 65,
			60, 55, 50, 45,
			40, 35, 30, 25)),
	/** Constant output. */
	STEADY("steady", Waveform.ofIntensities(Waveform.MIN_FREQUENCY,
			100, 100, 100, 100)),
	/** Two beats and a pause. */
	HEARTBEAT("heartbeat", Waveform.ofIntensities(Waveform.MIN_FREQUENCY,
			100, 100, 50, 0,
			0, 0, 80, 80,
			40, 0, 0, 0,
			0, 0, 0, 0,
			0, 0, 0, 0,
			0, 0, 0, 0)),
	/** Four rising steps. */
	STAIRS("stairs", Waveform.ofIntensities(Waveform.MIN_FREQUENCY,
			25, 25, 25, 25,
			50, 50, 50, 50,
			75, 75, 75, 75,
			100, 100, 100, 100)),
	/** Rapid on and off. */
	FLUTTER("flutter", Waveform.ofIntensities(Waveform.MIN_FREQUENCY,
			100, 0, 100, 0,
			100, 0, 100, 0));

	private final String id;
	private final Waveform waveform;

	WaveformPreset(final String id, final Waveform waveform) {
		this.id = id;
		this.waveform = waveform;
	}

	/** The name used in the configuration file and in translation keys. */
	public String id() {
		return this.id;
	}

	public Waveform waveform() {
		return this.waveform;
	}

	public String labelKey() {
		return "options.dglab.waveform.preset." + this.id;
	}

	public Component label() {
		return Component.translatable(this.labelKey());
	}

	public static Optional<WaveformPreset> byId(final String id) {
		return Arrays.stream(values()).filter(preset -> preset.id.equals(id)).findFirst();
	}
}
