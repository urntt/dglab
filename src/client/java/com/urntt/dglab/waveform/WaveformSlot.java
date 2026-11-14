package com.urntt.dglab.waveform;

import com.urntt.dglab.protocol.Channel;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The four situations a waveform is configured for: each channel has one for the time right after damage and one for
 * the time its strength decays.
 */
public enum WaveformSlot {
	A_DAMAGE(Channel.A, Kind.DAMAGE),
	A_RECOVERY(Channel.A, Kind.RECOVERY),
	B_DAMAGE(Channel.B, Kind.DAMAGE),
	B_RECOVERY(Channel.B, Kind.RECOVERY);

	/** When a slot's waveform plays. */
	public enum Kind {
		/** During the hold period after damage or death. */
		DAMAGE("damage", WaveformPreset.THUMP),
		/** While the strength is above zero and no hold period is running. */
		RECOVERY("recovery", WaveformPreset.SWELL);

		private final String id;
		private final WaveformPreset defaultPreset;

		Kind(final String id, final WaveformPreset defaultPreset) {
			this.id = id;
			this.defaultPreset = defaultPreset;
		}
	}

	private final Channel channel;
	private final Kind kind;

	WaveformSlot(final Channel channel, final Kind kind) {
		this.channel = channel;
		this.kind = kind;
	}

	public static WaveformSlot of(final Channel channel, final Kind kind) {
		return Arrays.stream(values()).filter(slot -> slot.channel == channel && slot.kind == kind).findFirst().orElseThrow();
	}

	public static Optional<WaveformSlot> byId(final String id) {
		return Arrays.stream(values()).filter(slot -> slot.id().equals(id)).findFirst();
	}

	public Channel channel() {
		return this.channel;
	}

	public Kind kind() {
		return this.kind;
	}

	/** The name used in the configuration file, commands, and translation keys. */
	public String id() {
		return this.channel.id() + "_" + this.kind.id;
	}

	public WaveformPreset defaultPreset() {
		return this.kind.defaultPreset;
	}

	public String labelKey() {
		return "options.dglab.waveform.slot." + this.id();
	}

	public Component label() {
		return Component.translatable(this.labelKey());
	}
}
