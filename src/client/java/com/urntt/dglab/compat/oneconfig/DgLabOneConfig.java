package com.urntt.dglab.compat.oneconfig;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.config.ChannelOption;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.protocol.AppCommands;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.screen.PairingScreen;
import com.urntt.dglab.screen.WaveformScreen;
import com.urntt.dglab.server.PairingUrl;
import com.urntt.dglab.waveform.WaveformPreset;
import com.urntt.dglab.waveform.WaveformSlot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.Visualizer;
import org.polyfrost.oneconfig.api.config.v1.backend.Backend;

/**
 * Shows the mod's settings in OneConfig. The settings stay in {@link DgLabConfig}, the single source of truth:
 * every option reads and writes it directly, and the tree is marked UI-only, so OneConfig never stores a copy of its
 * own. Changes made anywhere else are pushed to the options, so an open OneConfig screen shows them at once.
 */
public final class DgLabOneConfig extends Config {
	/**
	 * The ID of the tree. OneConfig's convention is the name its file would have in the config directory, so it
	 * differs from the mod's own config file, which OneConfig must never touch, even though it stores nothing here.
	 */
	public static final String ID = DgLabClient.MOD_ID + "-oneconfig.json";
	/** Client ticks the listen port has to stay unchanged in OneConfig before the connection server moves to it. */
	public static final int LISTEN_PORT_SETTLE_TICKS = 20;
	/** What the address field accepts: a host name or an IPv4 address, an IPv6 address, or nothing for automatic. */
	public static final String HOST_PATTERN = "^(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*|[0-9A-Fa-f:.]+)$";
	public static final String PLAYER_NAME_PATTERN = "^[A-Za-z0-9_]{0,16}$";

	private static final String ICON = "assets/" + DgLabClient.MOD_ID + "/icon.png";
	private static final String SECONDS_KEY = "options.dglab.oneconfig.unit.seconds";
	private static final String PERCENT_UNIT = "%";
	private static final float PORT_STEP = 1.0F;

	private final DgLabConfig config;
	/**
	 * The options, set by {@link #makeTree()}. OneConfig's constructor may already call it, before the initializers
	 * of this class's fields run, so none of the fields has one.
	 */
	private @Nullable List<Property<?>> properties;
	/** Set while the options are told about a change that came from elsewhere, so they do not write it back. */
	private boolean pushing;
	/** Set while an option writes a change, so the change is not pushed back to the options. */
	private boolean writing;
	private @Nullable Integer pendingListenPort;
	private int listenPortSettleTicks;

	public DgLabOneConfig(final DgLabConfig config) {
		super(ID, ICON, DgLabClient.MOD_ID, Category.UTILITY);
		this.config = config;
		config.addListener(this::push);
		ClientTickEvents.END_CLIENT_TICK.register(client -> this.tick());
	}

	/** The listen port typed into OneConfig that is not applied yet, or null. */
	public @Nullable Integer pendingListenPort() {
		return this.pendingListenPort;
	}

	@Override
	protected Tree makeTree() {
		// OneConfig may build the tree before this class's constructor has run, so the config does not come from
		// its field.
		DgLabConfig config = DgLabClient.config();
		List<Property<?>> options = new ArrayList<>();
		Tree tree = Tree.tree(ID);
		tree.addMetadata(Backend.UI_ONLY_METADATA, Boolean.TRUE);

		Section connection = new Section(tree, options, "options.dglab.section.connection");
		connection.toggle(ToggleSetting.SERVER_ENABLED);
		connection.number("listen_port", "options.dglab.connection.listen_port", "options.dglab.connection.listen_port.tooltip",
				DgLabConfig.MIN_PORT, DgLabConfig.MAX_PORT, DgLabConfig.DEFAULT_PORT,
				() -> this.pendingListenPort != null ? this.pendingListenPort : config.listenPort(), this::editListenPort);
		connection.text("qr_address", "options.dglab.connection.qr_address", "options.dglab.oneconfig.qr_address.tooltip",
				HOST_PATTERN, "options.dglab.connection.qr_address.automatic", DgLabConfig.QR_ADDRESS_AUTOMATIC,
				config::qrAddress, address -> {
					String host = address.trim();
					if (host.equals(DgLabConfig.QR_ADDRESS_AUTOMATIC) || PairingUrl.isValidHost(host)) {
						config.setConnection(config.listenPort(), host, config.qrPort());
					}
				});
		connection.number("qr_port", "options.dglab.connection.qr_port", "options.dglab.oneconfig.qr_port.tooltip",
				DgLabConfig.QR_PORT_SAME_AS_LISTEN, DgLabConfig.MAX_PORT, DgLabConfig.QR_PORT_SAME_AS_LISTEN,
				config::qrPort, port -> config.setConnection(config.listenPort(), config.qrAddress(), port));
		connection.button("pair", "options.dglab.pair", "options.dglab.pair.tooltip", PairingScreen::new);

		Section output = new Section(tree, options, "options.dglab.section.output");
		output.toggle(ToggleSetting.ENABLED);
		output.slider("output_multiplier", "options.dglab.output_multiplier", "options.dglab.output_multiplier.tooltip",
				0, DgLabConfig.MAX_OUTPUT_MULTIPLIER, 1, PERCENT_UNIT, null, DgLabConfig.DEFAULT_OUTPUT_MULTIPLIER,
				() -> (float) config.outputMultiplier(), value -> config.setOutputMultiplier(Math.round(value)));
		output.toggle(ToggleSetting.CHANNEL_A);
		output.toggle(ToggleSetting.CHANNEL_B);
		output.toggle(ToggleSetting.DAMAGE_RESPONSE);
		output.toggle(ToggleSetting.DEATH_RESPONSE);
		output.toggle(ToggleSetting.HEALTH_FLOOR);

		Section strength = new Section(tree, options, "options.dglab.strength.title");
		for (ChannelOption option : ChannelOption.values()) {
			strength.subcategory(option.captionKey());
			for (Channel channel : Channel.values()) {
				String unitKey = option.unit() == ChannelOption.Unit.TICKS ? SECONDS_KEY : null;
				strength.slider(option.id() + "_" + channel.id(), DgLabClient.channelNameKey(channel), option.tooltipKey(),
						option.toDisplayUnit(option.min()), option.toDisplayUnit(option.max()), option.toDisplayUnit(option.step()),
						null, unitKey, option.toDisplayUnit(option.defaultValue()),
						() -> (float) option.toDisplayUnit(config.value(channel, option)),
						value -> config.setValue(channel, option, option.fromDisplayUnit(value)));
			}
		}

		Section waveforms = new Section(tree, options, "options.dglab.waveforms.title");
		for (WaveformSlot slot : WaveformSlot.values()) {
			waveforms.waveformChoice(slot);
		}
		waveforms.button("edit_waveforms", "options.dglab.waveforms", "options.dglab.waveforms.tooltip", WaveformScreen::new);

		Section hud = new Section(tree, options, "options.dglab.section.hud");
		hud.toggle(ToggleSetting.HUD);
		hud.toggle(ToggleSetting.HUD_LIMIT);
		hud.slider("hud_x", "options.dglab.hud_x", "options.dglab.hud_x.tooltip", 0, DgLabConfig.MAX_HUD_PERCENT, 1, PERCENT_UNIT, null,
				DgLabConfig.DEFAULT_HUD_PERCENT, () -> (float) config.hudX(), value -> config.setHudX(Math.round(value)));
		hud.slider("hud_y", "options.dglab.hud_y", "options.dglab.hud_y.tooltip", 0, DgLabConfig.MAX_HUD_PERCENT, 1, PERCENT_UNIT, null,
				DgLabConfig.DEFAULT_HUD_PERCENT, () -> (float) config.hudY(), value -> config.setHudY(Math.round(value)));

		Section twoPlayer = new Section(tree, options, "options.dglab.section.two_player");
		twoPlayer.toggle(ToggleSetting.TWO_PLAYER);
		twoPlayer.text("second_player", "options.dglab.two_player.player", "options.dglab.two_player.description",
				PLAYER_NAME_PATTERN, "options.dglab.two_player.player.hint", "",
				config::secondPlayer, name -> config.setTwoPlayer(name, config.secondPlayerQuitStrength()));
		twoPlayer.slider("quit_strength", "options.dglab.two_player.quit_strength", "options.dglab.two_player.quit_strength.tooltip",
				0, AppCommands.MAX_STRENGTH, 1, null, null, DgLabConfig.DEFAULT_QUIT_STRENGTH,
				() -> (float) config.secondPlayerQuitStrength(), value -> config.setTwoPlayer(config.secondPlayer(), Math.round(value)));

		this.properties = List.copyOf(options);
		return tree;
	}

	/**
	 * Tells every option its current value, so OneConfig's widgets show changes made elsewhere: on the mod's own
	 * screens, with its key bindings, or with its commands.
	 */
	void push() {
		List<Property<?>> options = this.properties;
		if (options == null || this.writing || this.pushing) {
			return;
		}
		this.pushing = true;
		try {
			for (Property<?> property : options) {
				pushValue(property);
			}
		} finally {
			this.pushing = false;
		}
	}

	private static <T> void pushValue(final Property<T> property) {
		// Unlike set(), setReferential() runs the callbacks the widgets listen with even though the value is current.
		property.setReferential(property.get());
	}

	/**
	 * Wraps a setter so that values pushed by {@link #push()} are not written back, and so that a value written from
	 * OneConfig is not pushed back to it.
	 */
	private <T> Consumer<T> writer(final Consumer<T> setter) {
		return value -> {
			if (this.pushing || value == null) {
				return;
			}
			this.writing = true;
			try {
				setter.accept(value);
			} finally {
				this.writing = false;
			}
		};
	}

	/**
	 * OneConfig writes a number field on every keystroke, so a new listen port only takes effect once it has not
	 * changed for {@link #LISTEN_PORT_SETTLE_TICKS}. Otherwise typing 9999 would move the connection server, and drop
	 * the app, three times on the way.
	 */
	private void editListenPort(final int port) {
		if (port < DgLabConfig.MIN_PORT || port > DgLabConfig.MAX_PORT) {
			return;
		}
		this.pendingListenPort = port;
		this.listenPortSettleTicks = LISTEN_PORT_SETTLE_TICKS;
	}

	private void tick() {
		if (this.pendingListenPort == null || --this.listenPortSettleTicks > 0) {
			return;
		}
		int port = this.pendingListenPort;
		this.pendingListenPort = null;
		this.config.setConnection(port, this.config.qrAddress(), this.config.qrPort());
	}

	/**
	 * Adds the options of one category. Titles, descriptions, and choices are translation keys, which OneConfig
	 * translates into the game's current language.
	 */
	private final class Section {
		private final Tree tree;
		private final List<Property<?>> options;
		private final String categoryKey;
		private String subcategoryKey = "";

		private Section(final Tree tree, final List<Property<?>> options, final String categoryKey) {
			this.tree = tree;
			this.options = options;
			this.categoryKey = categoryKey;
		}

		private void subcategory(final String key) {
			this.subcategoryKey = key;
		}

		private void toggle(final ToggleSetting setting) {
			DgLabConfig config = DgLabClient.config();
			this.add(setting.id(), setting.captionKey(), setting.tooltipKey(), Visualizer.SwitchVisualizer.class, Boolean.class,
					setting.defaultValue(), () -> config.get(setting), value -> config.set(setting, value));
		}

		private void slider(final String id, final String titleKey, final String descriptionKey, final double min, final double max,
				final double step, final @Nullable String unit, final @Nullable String unitKey, final double defaultValue,
				final Supplier<Float> getter, final Consumer<Float> setter) {
			Property<Float> property = this.add(id, titleKey, descriptionKey, Visualizer.SliderVisualizer.class, Float.class,
					(float) defaultValue, getter, setter);
			property.addMetadata("min", (float) min);
			property.addMetadata("max", (float) max);
			property.addMetadata("step", (float) step);
			property.addMetadata("unit", unit);
			property.addMetadata("unitKey", unitKey);
		}

		private void number(final String id, final String titleKey, final String descriptionKey, final int min, final int max,
				final int defaultValue, final Supplier<Integer> getter, final Consumer<Integer> setter) {
			Property<Integer> property = this.add(id, titleKey, descriptionKey, Visualizer.NumberVisualizer.class, Integer.class,
					defaultValue, getter, setter);
			property.addMetadata("min", (float) min);
			property.addMetadata("max", (float) max);
			property.addMetadata("step", PORT_STEP);
		}

		private void text(final String id, final String titleKey, final String descriptionKey, final String regex,
				final String placeholderKey, final String defaultValue, final Supplier<String> getter, final Consumer<String> setter) {
			Property<String> property = this.add(id, titleKey, descriptionKey, Visualizer.TextVisualizer.class, String.class,
					defaultValue, getter, setter);
			property.addMetadata("regex", regex);
			property.addMetadata("placeholderKey", placeholderKey);
			property.addMetadata("multiline", Boolean.FALSE);
		}

		/** A slot's choice between the presets, by index, and its custom waveform, the last entry. */
		private void waveformChoice(final WaveformSlot slot) {
			DgLabConfig config = DgLabClient.config();
			WaveformPreset[] presets = WaveformPreset.values();
			List<String> labelKeys = new ArrayList<>(Arrays.stream(presets).map(WaveformPreset::labelKey).toList());
			labelKeys.add("options.dglab.waveform.preset.custom");
			Property<Integer> property = this.add(slot.id(), slot.labelKey(), "options.dglab.waveform.preset.tooltip",
					Visualizer.DropdownVisualizer.class, Integer.class, slot.defaultPreset().ordinal(),
					() -> config.waveformPreset(slot).map(Enum::ordinal).orElse(presets.length),
					index -> {
						if (index >= 0 && index < presets.length) {
							config.setWaveformPreset(slot, presets[index]);
						} else if (index == presets.length) {
							config.useCustomWaveform(slot);
						}
					});
			property.addMetadata("options", Collections.unmodifiableList(labelKeys));
			property.addMetadata("optionsKey", Collections.unmodifiableList(labelKeys));
		}

		/** A button that opens one of the mod's own screens, which returns to OneConfig when closed. */
		private void button(final String id, final String titleKey, final String descriptionKey, final Function<Screen, Screen> screen) {
			Property<Void> property = Properties.dummy(id, titleKey, descriptionKey);
			this.describe(property, titleKey, descriptionKey, Visualizer.ButtonVisualizer.class);
			property.addMetadata("text", titleKey);
			property.addMetadata("textKey", titleKey);
			property.addMetadata("runnable", (Runnable) () -> {
				Minecraft client = Minecraft.getInstance();
				client.gui.setScreen(screen.apply(client.gui.screen()));
			});
			this.tree.put(property);
		}

		private <T> Property<T> add(final String id, final String titleKey, final String descriptionKey,
				final Class<? extends Visualizer> visualizer, final Class<T> type, final T defaultValue,
				final Supplier<T> getter, final Consumer<T> setter) {
			Property<T> property = Properties.functional(getter, DgLabOneConfig.this.writer(setter), id, titleKey, descriptionKey, type);
			this.describe(property, titleKey, descriptionKey, visualizer);
			// OneConfig's reset restores this value instead of the one the option had when OneConfig started.
			property.addMetadata("default", defaultValue);
			this.tree.put(property);
			this.options.add(property);
			return property;
		}

		private void describe(final Property<?> property, final String titleKey, final String descriptionKey,
				final Class<? extends Visualizer> visualizer) {
			property.addMetadata("visualizer", visualizer);
			property.addMetadata("titleKey", titleKey);
			property.addMetadata("descriptionKey", descriptionKey);
			property.addMetadata("category", this.categoryKey);
			property.addMetadata("categoryKey", this.categoryKey);
			property.addMetadata("subcategory", this.subcategoryKey);
			property.addMetadata("subcategoryKey", this.subcategoryKey);
		}
	}
}
