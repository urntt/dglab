package com.urntt.dglab.protocol;

import java.util.Locale;

/**
 * One of the two output channels of a DG-LAB Coyote.
 */
public enum Channel {
	A(1),
	B(2);

	private final int number;

	Channel(final int number) {
		this.number = number;
	}

	/** The channel number used by strength and clear commands. */
	public int number() {
		return this.number;
	}

	/** The channel letter used by pulse commands. */
	public String letter() {
		return this.name();
	}

	/** The lower-case name used in the configuration file, commands, and translation keys. */
	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
