package com.urntt.dglab.waveform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An immutable pulse waveform in the form the DG-LAB Coyote 3.0 plays: a sequence of samples of 25 ms each, grouped
 * into frames of four samples. A frame is sent as 8 bytes in hexadecimal: the four frequencies, then the four
 * intensities.
 */
public final class Waveform {
	public static final int SAMPLES_PER_FRAME = 4;
	public static final int SAMPLE_MILLIS = 25;
	public static final int FRAME_MILLIS = SAMPLES_PER_FRAME * SAMPLE_MILLIS;
	/** A frame lasts two client ticks. */
	public static final int TICKS_PER_FRAME = 2;
	/**
	 * The longest waveform the mod handles. Its pulse command still fits into one message of the socket protocol.
	 */
	public static final int MAX_FRAMES = 80;

	public static final int MIN_FREQUENCY = 10;
	public static final int MAX_FREQUENCY = 240;
	public static final int MAX_INTENSITY = 100;

	private static final int FRAME_HEX_LENGTH = SAMPLES_PER_FRAME * 2 * 2;
	private static final Pattern FRAME_PATTERN = Pattern.compile("[0-9a-fA-F]{" + FRAME_HEX_LENGTH + "}");
	/** What may surround the frames in pasted waveform code: brackets, quotes, commas, and white space. */
	private static final Pattern CODE_SEPARATORS = Pattern.compile("[\\[\\]\",\\s]+");

	private final List<Sample> samples;

	/**
	 * One 25 ms step of a waveform.
	 *
	 * @param frequency the device's frequency value, from {@link #MIN_FREQUENCY} to {@link #MAX_FREQUENCY}; a higher
	 *                  value means slower pulses
	 * @param intensity the output as a percentage of the channel's strength, from 0 to {@link #MAX_INTENSITY}
	 */
	public record Sample(int frequency, int intensity) {
		public Sample {
			if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY) {
				throw new IllegalArgumentException("frequency out of range: " + frequency);
			}
			if (intensity < 0 || intensity > MAX_INTENSITY) {
				throw new IllegalArgumentException("intensity out of range: " + intensity);
			}
		}
	}

	private Waveform(final List<Sample> samples) {
		this.samples = List.copyOf(samples);
	}

	/**
	 * Creates a waveform from whole frames of samples.
	 *
	 * @throws IllegalArgumentException if the samples do not form between one and {@link #MAX_FRAMES} frames
	 */
	public static Waveform ofSamples(final List<Sample> samples) {
		if (samples.isEmpty() || samples.size() % SAMPLES_PER_FRAME != 0 || samples.size() > MAX_FRAMES * SAMPLES_PER_FRAME) {
			throw new IllegalArgumentException("invalid sample count: " + samples.size());
		}
		return new Waveform(samples);
	}

	/**
	 * Creates a waveform with a constant frequency from one intensity per sample.
	 */
	public static Waveform ofIntensities(final int frequency, final int... intensities) {
		List<Sample> samples = new ArrayList<>(intensities.length);
		for (int intensity : intensities) {
			samples.add(new Sample(frequency, intensity));
		}
		return ofSamples(samples);
	}

	/**
	 * Parses frames in hexadecimal. Returns an empty result if there are no frames, too many frames, or a frame that
	 * is malformed or holds a value the device does not accept.
	 */
	public static Optional<Waveform> parseFrames(final List<String> frames) {
		if (frames.isEmpty() || frames.size() > MAX_FRAMES) {
			return Optional.empty();
		}

		List<Sample> samples = new ArrayList<>(frames.size() * SAMPLES_PER_FRAME);
		for (String frame : frames) {
			if (frame == null) {
				return Optional.empty();
			}
			Matcher matcher = FRAME_PATTERN.matcher(frame);
			if (!matcher.matches()) {
				return Optional.empty();
			}
			for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
				int frequency = hexByte(frame, i);
				int intensity = hexByte(frame, SAMPLES_PER_FRAME + i);
				if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY || intensity > MAX_INTENSITY) {
					return Optional.empty();
				}
				samples.add(new Sample(frequency, intensity));
			}
		}
		return Optional.of(new Waveform(samples));
	}

	/**
	 * Parses waveform code as the DG-LAB app and other tools exchange it: the frames in hexadecimal, optionally
	 * quoted, separated by commas, and wrapped in brackets.
	 */
	public static Optional<Waveform> parseCode(final String code) {
		List<String> frames = new ArrayList<>();
		for (String part : CODE_SEPARATORS.split(code)) {
			if (!part.isEmpty()) {
				frames.add(part);
			}
		}
		return parseFrames(frames);
	}

	public List<Sample> samples() {
		return this.samples;
	}

	public int frameCount() {
		return this.samples.size() / SAMPLES_PER_FRAME;
	}

	public int durationTicks() {
		return this.frameCount() * TICKS_PER_FRAME;
	}

	public int durationMillis() {
		return this.frameCount() * FRAME_MILLIS;
	}

	/**
	 * Returns the frames in hexadecimal, as they are sent to the app.
	 */
	public List<String> frames() {
		List<String> frames = new ArrayList<>(this.frameCount());
		for (int start = 0; start < this.samples.size(); start += SAMPLES_PER_FRAME) {
			StringBuilder frame = new StringBuilder(FRAME_HEX_LENGTH);
			for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
				frame.append(String.format(Locale.ROOT, "%02X", this.samples.get(start + i).frequency()));
			}
			for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
				frame.append(String.format(Locale.ROOT, "%02X", this.samples.get(start + i).intensity()));
			}
			frames.add(frame.toString());
		}
		return frames;
	}

	/**
	 * Returns the waveform code that {@link #parseCode(String)} reads back.
	 */
	public String code() {
		return "\"" + String.join("\",\"", this.frames()) + "\"";
	}

	@Override
	public boolean equals(final Object other) {
		return other instanceof Waveform waveform && this.samples.equals(waveform.samples);
	}

	@Override
	public int hashCode() {
		return this.samples.hashCode();
	}

	@Override
	public String toString() {
		return this.code();
	}

	private static int hexByte(final String frame, final int index) {
		return Integer.parseInt(frame, index * 2, index * 2 + 2, 16);
	}
}
