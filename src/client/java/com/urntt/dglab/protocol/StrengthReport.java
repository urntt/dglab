package com.urntt.dglab.protocol;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The app's report of both channels' current strength and of the limits the user set in the app. The app sends it
 * whenever one of the four values changes.
 */
public record StrengthReport(int strengthA, int strengthB, int limitA, int limitB) {
	private static final Pattern PATTERN = Pattern.compile("strength-(\\d{1,3})\\+(\\d{1,3})\\+(\\d{1,3})\\+(\\d{1,3})");

	/**
	 * Parses the {@code message} field of a message from the app, or returns an empty result if it is something
	 * other than a strength report.
	 */
	public static Optional<StrengthReport> parse(final String message) {
		Matcher matcher = PATTERN.matcher(message);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(new StrengthReport(
				Integer.parseInt(matcher.group(1)),
				Integer.parseInt(matcher.group(2)),
				Integer.parseInt(matcher.group(3)),
				Integer.parseInt(matcher.group(4))));
	}

	public int strength(final Channel channel) {
		return channel == Channel.A ? this.strengthA : this.strengthB;
	}

	public int limit(final Channel channel) {
		return channel == Channel.A ? this.limitA : this.limitB;
	}

	/** The text the app sends for this report. */
	public String toMessage() {
		return "strength-" + this.strengthA + "+" + this.strengthB + "+" + this.limitA + "+" + this.limitB;
	}
}
