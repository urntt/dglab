package com.urntt.dglab.protocol;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the commands the DG-LAB app accepts in the {@code message} field of a {@link SocketMessage#TYPE_MSG} message.
 */
public final class AppCommands {
	/** The highest strength a channel can have. */
	public static final int MAX_STRENGTH = 200;
	/** The most pulse frames the app accepts in one command. */
	public static final int MAX_PULSE_FRAMES = 100;

	private static final int MODE_DECREASE = 0;
	private static final int MODE_INCREASE = 1;
	private static final int MODE_SET = 2;

	private AppCommands() {
	}

	public static String decreaseStrength(final Channel channel, final int amount) {
		return strength(channel, MODE_DECREASE, amount);
	}

	public static String increaseStrength(final Channel channel, final int amount) {
		return strength(channel, MODE_INCREASE, amount);
	}

	public static String setStrength(final Channel channel, final int value) {
		return strength(channel, MODE_SET, value);
	}

	/**
	 * Appends pulse frames to the channel's queue. Each frame is 8 bytes in hexadecimal and lasts 100 ms.
	 */
	public static String pulse(final Channel channel, final List<String> frames) {
		return frames.stream()
				.map(frame -> '"' + frame + '"')
				.collect(Collectors.joining(",", "pulse-" + channel.letter() + ":[", "]"));
	}

	/**
	 * Empties the channel's pulse queue.
	 */
	public static String clear(final Channel channel) {
		return "clear-" + channel.number();
	}

	/**
	 * The commands that leave a channel without output: zero strength and an empty pulse queue.
	 */
	public static List<String> silence(final Channel channel) {
		return List.of(setStrength(channel, 0), clear(channel));
	}

	/**
	 * The commands that leave both channels without output.
	 */
	public static List<String> silenceAll() {
		return Arrays.stream(Channel.values()).flatMap(channel -> silence(channel).stream()).toList();
	}

	private static String strength(final Channel channel, final int mode, final int value) {
		return "strength-" + channel.number() + "+" + mode + "+" + value;
	}
}
