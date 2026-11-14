package com.urntt.dglab.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.protocol.AppCommands;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.waveform.Waveform;
import com.urntt.dglab.waveform.WaveformPreset;
import com.urntt.dglab.waveform.WaveformSlot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's settings, persisted as JSON. Every change is written to disk immediately.
 */
public final class DgLabConfig {
	public static final int DEFAULT_PORT = 9999;
	public static final int MIN_PORT = 1;
	public static final int MAX_PORT = 65535;
	/** The value of the QR code port that means "the port the server listens on". */
	public static final int QR_PORT_SAME_AS_LISTEN = 0;
	/** The value of the QR code address that means "pick a LAN address of this computer". */
	public static final String QR_ADDRESS_AUTOMATIC = "";
	public static final int MAX_HUD_PERCENT = 100;
	public static final int MAX_PLAYER_NAME_LENGTH = 16;
	/** The preset name that selects a slot's custom waveform. */
	public static final String CUSTOM_WAVEFORM = "custom";

	private static final Logger LOGGER = LoggerFactory.getLogger(DgLabClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.create();

	private final Path path;
	private final Settings settings;
	/** The waveform each slot currently plays, parsed once from the settings. */
	private final Map<WaveformSlot, Waveform> waveforms = new EnumMap<>(WaveformSlot.class);

	private DgLabConfig(final Path path, final Settings settings) {
		this.path = path;
		this.settings = settings;
		this.settings.normalize();
		this.resolveWaveforms();
	}

	/**
	 * Returns the location of the config file in the Fabric config directory.
	 */
	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(DgLabClient.MOD_ID + ".json");
	}

	/**
	 * Loads the configuration from {@code path}. A missing file is created with the defaults. A readable file is
	 * rewritten with any settings it lacks and with out-of-range values corrected. An unreadable file falls back to
	 * the defaults and is left untouched until the next change is saved.
	 */
	public static DgLabConfig load(final Path path) {
		if (Files.notExists(path)) {
			DgLabConfig config = new DgLabConfig(path, new Settings());
			config.save();
			return config;
		}

		Settings settings;
		try {
			settings = GSON.fromJson(Files.readString(path), Settings.class);
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to read config file {}, using defaults", path, e);
			return new DgLabConfig(path, new Settings());
		}

		DgLabConfig config = new DgLabConfig(path, settings != null ? settings : new Settings());
		config.save();
		return config;
	}

	public boolean get(final ToggleSetting setting) {
		return this.settings.toggles.get(setting.id());
	}

	public void set(final ToggleSetting setting, final boolean value) {
		this.settings.toggles.put(setting.id(), value);
		this.save();
	}

	/**
	 * Flips a setting and returns its new value.
	 */
	public boolean toggle(final ToggleSetting setting) {
		boolean value = !this.get(setting);
		this.set(setting, value);
		return value;
	}

	public double value(final Channel channel, final ChannelOption option) {
		return this.settings.channels.get(channel.id()).get(option.id());
	}

	/** A channel option whose values are whole numbers, such as a tick count or a strength. */
	public int intValue(final Channel channel, final ChannelOption option) {
		return (int) Math.round(this.value(channel, option));
	}

	public void setValue(final Channel channel, final ChannelOption option, final double value) {
		this.settings.channels.get(channel.id()).put(option.id(), option.normalize(value));
		this.save();
	}

	/** The port the connection server listens on. */
	public int listenPort() {
		return this.settings.connection.listenPort;
	}

	/** The host the QR code points the app to, or {@link #QR_ADDRESS_AUTOMATIC}. */
	public String qrAddress() {
		return this.settings.connection.qrAddress;
	}

	/** The port the QR code points the app to, or {@link #QR_PORT_SAME_AS_LISTEN}. */
	public int qrPort() {
		return this.settings.connection.qrPort;
	}

	public void setConnection(final int listenPort, final String qrAddress, final int qrPort) {
		this.settings.connection.listenPort = listenPort;
		this.settings.connection.qrAddress = qrAddress;
		this.settings.connection.qrPort = qrPort;
		this.settings.normalize();
		this.save();
	}

	/** Horizontal position of the HUD, as a percentage of the screen width. */
	public int hudX() {
		return this.settings.hud.x;
	}

	public void setHudX(final int percent) {
		this.settings.hud.x = Math.clamp(percent, 0, MAX_HUD_PERCENT);
		this.save();
	}

	/** Vertical position of the HUD, as a percentage of the screen height. */
	public int hudY() {
		return this.settings.hud.y;
	}

	public void setHudY(final int percent) {
		this.settings.hud.y = Math.clamp(percent, 0, MAX_HUD_PERCENT);
		this.save();
	}

	/** The name of the player who drives channel B in two-player mode. */
	public String secondPlayer() {
		return this.settings.twoPlayer.player;
	}

	/** The strength added to channel B when the second player leaves the world alive; 0 turns it off. */
	public int secondPlayerQuitStrength() {
		return this.settings.twoPlayer.quitStrength;
	}

	public void setTwoPlayer(final String player, final int quitStrength) {
		this.settings.twoPlayer.player = player;
		this.settings.twoPlayer.quitStrength = quitStrength;
		this.settings.normalize();
		this.save();
	}

	/**
	 * Returns the preset a slot uses, or an empty result if it uses its custom waveform.
	 */
	public Optional<WaveformPreset> waveformPreset(final WaveformSlot slot) {
		return WaveformPreset.byId(this.settings.waveforms.get(slot.id()).preset);
	}

	public void setWaveformPreset(final WaveformSlot slot, final WaveformPreset preset) {
		this.settings.waveforms.get(slot.id()).preset = preset.id();
		this.resolveWaveforms();
		this.save();
	}

	/** Makes a slot play its custom waveform. */
	public void useCustomWaveform(final WaveformSlot slot) {
		this.settings.waveforms.get(slot.id()).preset = CUSTOM_WAVEFORM;
		this.resolveWaveforms();
		this.save();
	}

	/** A slot's custom waveform, which is kept while the slot uses a preset. */
	public Waveform customWaveform(final WaveformSlot slot) {
		return Waveform.parseFrames(this.settings.waveforms.get(slot.id()).custom).orElseThrow();
	}

	public void setCustomWaveform(final WaveformSlot slot, final Waveform waveform) {
		this.settings.waveforms.get(slot.id()).custom = waveform.frames();
		this.resolveWaveforms();
		this.save();
	}

	/** The waveform a slot currently plays. */
	public Waveform waveform(final WaveformSlot slot) {
		return this.waveforms.get(slot);
	}

	private void resolveWaveforms() {
		for (WaveformSlot slot : WaveformSlot.values()) {
			this.waveforms.put(slot, this.waveformPreset(slot).map(WaveformPreset::waveform).orElseGet(() -> this.customWaveform(slot)));
		}
	}

	private void save() {
		try {
			Files.createDirectories(this.path.getParent());
			Files.writeString(this.path, GSON.toJson(this.settings));
		} catch (IOException e) {
			LOGGER.error("Failed to write config file {}", this.path, e);
		}
	}

	/**
	 * The serialized form. Field initializers are the defaults; {@link #normalize()} fills in everything that depends
	 * on the enums and repairs whatever a hand-edited file got wrong.
	 */
	private static final class Settings {
		private Map<String, Boolean> toggles = new LinkedHashMap<>();
		private Map<String, Map<String, Double>> channels = new LinkedHashMap<>();
		private Connection connection = new Connection();
		private Hud hud = new Hud();
		private TwoPlayer twoPlayer = new TwoPlayer();
		private Map<String, WaveformChoice> waveforms = new LinkedHashMap<>();

		private void normalize() {
			Map<String, Boolean> toggles = new LinkedHashMap<>();
			for (ToggleSetting setting : ToggleSetting.values()) {
				Boolean value = this.toggles != null ? this.toggles.get(setting.id()) : null;
				toggles.put(setting.id(), value != null ? value : setting.defaultValue());
			}
			this.toggles = toggles;

			Map<String, Map<String, Double>> channels = new LinkedHashMap<>();
			for (Channel channel : Channel.values()) {
				Map<String, Double> stored = this.channels != null ? this.channels.get(channel.id()) : null;
				Map<String, Double> values = new LinkedHashMap<>();
				for (ChannelOption option : ChannelOption.values()) {
					Double value = stored != null ? stored.get(option.id()) : null;
					values.put(option.id(), value != null ? option.normalize(value) : option.defaultValue());
				}
				channels.put(channel.id(), values);
			}
			this.channels = channels;

			if (this.connection == null) {
				this.connection = new Connection();
			}
			if (this.connection.listenPort < MIN_PORT || this.connection.listenPort > MAX_PORT) {
				this.connection.listenPort = DEFAULT_PORT;
			}
			this.connection.qrAddress = this.connection.qrAddress != null ? this.connection.qrAddress.trim() : QR_ADDRESS_AUTOMATIC;
			if (this.connection.qrPort < MIN_PORT || this.connection.qrPort > MAX_PORT) {
				this.connection.qrPort = QR_PORT_SAME_AS_LISTEN;
			}

			if (this.hud == null) {
				this.hud = new Hud();
			}
			this.hud.x = Math.clamp(this.hud.x, 0, MAX_HUD_PERCENT);
			this.hud.y = Math.clamp(this.hud.y, 0, MAX_HUD_PERCENT);

			if (this.twoPlayer == null) {
				this.twoPlayer = new TwoPlayer();
			}
			String player = this.twoPlayer.player != null ? this.twoPlayer.player.trim() : "";
			this.twoPlayer.player = player.length() > MAX_PLAYER_NAME_LENGTH ? player.substring(0, MAX_PLAYER_NAME_LENGTH) : player;
			this.twoPlayer.quitStrength = Math.clamp(this.twoPlayer.quitStrength, 0, AppCommands.MAX_STRENGTH);

			Map<String, WaveformChoice> waveforms = new LinkedHashMap<>();
			for (WaveformSlot slot : WaveformSlot.values()) {
				WaveformChoice choice = this.waveforms != null ? this.waveforms.get(slot.id()) : null;
				if (choice == null) {
					choice = new WaveformChoice();
				}
				if (choice.custom == null || Waveform.parseFrames(choice.custom).isEmpty()) {
					choice.custom = slot.defaultPreset().waveform().frames();
				}
				if (!CUSTOM_WAVEFORM.equals(choice.preset) && WaveformPreset.byId(choice.preset).isEmpty()) {
					choice.preset = slot.defaultPreset().id();
				}
				waveforms.put(slot.id(), choice);
			}
			this.waveforms = waveforms;
		}
	}

	private static final class Connection {
		private int listenPort = DEFAULT_PORT;
		private String qrAddress = QR_ADDRESS_AUTOMATIC;
		private int qrPort = QR_PORT_SAME_AS_LISTEN;
	}

	private static final class Hud {
		private int x = 2;
		private int y = 2;
	}

	private static final class TwoPlayer {
		private String player = "";
		private int quitStrength = 0;
	}

	private static final class WaveformChoice {
		/** A {@link WaveformPreset} name, or {@link #CUSTOM_WAVEFORM}. */
		private String preset;
		/** The custom waveform's frames in hexadecimal. */
		private List<String> custom;
	}
}
