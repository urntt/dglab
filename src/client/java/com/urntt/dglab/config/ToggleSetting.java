package com.urntt.dglab.config;

import com.urntt.dglab.protocol.Channel;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The mod's on/off settings. Each one has an entry on the configuration screen, a key binding that toggles it, and a
 * name the {@code /dglab toggle} command accepts; all three are derived from this enum.
 */
public enum ToggleSetting {
	/** The master switch. While it is off, both channels are held at zero. */
	ENABLED("enabled", true),
	/** Whether the connection server the DG-LAB app pairs with is running. */
	SERVER_ENABLED("server_enabled", true),
	CHANNEL_A("channel_a", true),
	CHANNEL_B("channel_b", true),
	/** Whether damage raises the strength. */
	DAMAGE_RESPONSE("damage_response", true),
	/** Whether death raises the strength. */
	DEATH_RESPONSE("death_response", true),
	/** Whether the strength stops decaying at a floor that grows with lost health. */
	HEALTH_FLOOR("health_floor", true),
	HUD("hud", true),
	/** Whether the HUD also shows the limits set in the app. */
	HUD_LIMIT("hud_limit", false),
	/** Whether a second player on this client's LAN world drives channel B. */
	TWO_PLAYER("two_player", false);

	private final String id;
	private final boolean defaultValue;

	ToggleSetting(final String id, final boolean defaultValue) {
		this.id = id;
		this.defaultValue = defaultValue;
	}

	/** The name used in the configuration file, commands, and translation keys. */
	public String id() {
		return this.id;
	}

	public boolean defaultValue() {
		return this.defaultValue;
	}

	/** Translation key of the setting's name. */
	public String captionKey() {
		return "options.dglab." + this.id;
	}

	public String tooltipKey() {
		return this.captionKey() + ".tooltip";
	}

	/** Translation key and name of the key binding that toggles the setting. */
	public String keyName() {
		return "key.dglab.toggle." + this.id;
	}

	public Component caption() {
		return Component.translatable(this.captionKey());
	}

	public static ToggleSetting forChannel(final Channel channel) {
		return channel == Channel.A ? CHANNEL_A : CHANNEL_B;
	}

	public static Optional<ToggleSetting> byId(final String id) {
		return Arrays.stream(values()).filter(setting -> setting.id.equals(id)).findFirst();
	}
}
