package com.urntt.dglab.gametest;

import static com.urntt.dglab.gametest.GameTestSupport.LOGGER;
import static com.urntt.dglab.gametest.GameTestSupport.bindKey;
import static com.urntt.dglab.gametest.GameTestSupport.check;
import static com.urntt.dglab.gametest.GameTestSupport.checkEquals;
import static com.urntt.dglab.gametest.GameTestSupport.clickButton;
import static com.urntt.dglab.gametest.GameTestSupport.configure;
import static com.urntt.dglab.gametest.GameTestSupport.loadSavedConfig;
import static com.urntt.dglab.gametest.GameTestSupport.resetConfig;
import static com.urntt.dglab.gametest.GameTestSupport.setLanguage;
import static com.urntt.dglab.gametest.GameTestSupport.unbindKey;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.compat.oneconfig.DgLabOneConfig;
import com.urntt.dglab.compat.oneconfig.OneConfigModToggle;
import com.urntt.dglab.config.ChannelOption;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.screen.PairingScreen;
import com.urntt.dglab.screen.WaveformScreen;
import com.urntt.dglab.server.PairingServer;
import com.urntt.dglab.waveform.WaveformPreset;
import com.urntt.dglab.waveform.WaveformSlot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Node;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.backend.Backend;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigDestination;
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages;

/**
 * The OneConfig checks of {@link DgLabOneConfigGameTest}. Each change is made on one side, OneConfig or the mod,
 * and looked for on the other.
 */
@SuppressWarnings({"UnstableApiUsage", "unchecked"})
final class OneConfigChecks {
	/** A port nothing else in the tests uses. */
	private static final int OTHER_PORT = 12345;
	private static final int SCREENSHOT_GUI_SCALE = 2;
	/** The key that opens OneConfig unless the player changes it. */
	private static final String ONECONFIG_KEY = "key.keyboard.right.shift";
	private static final int ONECONFIG_KEY_HOLD_TICKS = 3;

	private OneConfigChecks() {
	}

	static void run(final ClientGameTestContext context) {
		resetConfig(context);
		Tree tree = context.computeOnClient(client -> ConfigManager.active().get(DgLabOneConfig.ID));
		check(tree != null, "OneConfig has no tree " + DgLabOneConfig.ID);
		checkStorage(context, tree);
		checkWritesFromOneConfig(context, tree);
		checkWritesFromTheMod(context, tree);
		checkDefaults(context, tree);
		checkListenPort(context, tree);
		checkModToggle(context);
		takeScreenshots(context, tree);
		resetConfig(context);
	}

	/** OneConfig shows the settings but keeps no copy of them. */
	private static void checkStorage(final ClientGameTestContext context, final Tree tree) {
		checkEquals(Boolean.TRUE, tree.getMetadata(Backend.UI_ONLY_METADATA), "UI-only marker of the tree");
		List<String> names = new ArrayList<>();
		tree.map.values().forEach(node -> names.add(node.getID()));
		LOGGER.info("OneConfig options: {}", names);
		// Every switch and every slider of the strength settings, one choice per waveform slot, two buttons, and the
		// rest by name: the ports, the address, the multiplier, the HUD position, and the two-player settings.
		int expected = ToggleSetting.values().length + ChannelOption.values().length * Channel.values().length
				+ WaveformSlot.values().length + 2 + List.of("listen_port", "qr_address", "qr_port", "output_multiplier", "hud_x", "hud_y",
						"second_player", "quit_strength").size();
		checkEquals(expected, names.size(), "number of entries in OneConfig");

		Path folder = context.computeOnClient(client -> ConfigManager.active().getFolder());
		try (Stream<Path> files = Files.exists(folder) ? Files.walk(folder) : Stream.empty()) {
			List<Path> copies = files.filter(path -> path.getFileName().toString().equals(DgLabOneConfig.ID)).toList();
			check(copies.isEmpty(), "OneConfig must not store the settings, found " + copies);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	/** A change made in OneConfig reaches the mod and its file right away. */
	private static void checkWritesFromOneConfig(final ClientGameTestContext context, final Tree tree) {
		set(context, tree, "hud_limit", Boolean.TRUE);
		check(loadSavedConfig().get(ToggleSetting.HUD_LIMIT), "a switch set in OneConfig must be saved");

		set(context, tree, "output_multiplier", 250.0F);
		checkEquals(250, loadSavedConfig().outputMultiplier(), "output multiplier set in OneConfig");

		// Times are shown in seconds and stored in ticks.
		set(context, tree, "hold_ticks_a", 1.5F);
		checkEquals(30.0, loadSavedConfig().value(Channel.A, ChannelOption.HOLD_TICKS), "hold time set in OneConfig");
		set(context, tree, "strength_per_damage_b", 4.2F);
		checkEquals(4.2, loadSavedConfig().value(Channel.B, ChannelOption.STRENGTH_PER_DAMAGE), "strength per damage set in OneConfig");

		set(context, tree, WaveformSlot.A_DAMAGE.id(), WaveformPreset.STAIRS.ordinal());
		checkEquals(WaveformPreset.STAIRS, loadSavedConfig().waveformPreset(WaveformSlot.A_DAMAGE).orElse(null), "preset chosen in OneConfig");
		set(context, tree, WaveformSlot.A_DAMAGE.id(), WaveformPreset.values().length);
		check(loadSavedConfig().waveformPreset(WaveformSlot.A_DAMAGE).isEmpty(), "the last choice in OneConfig is the custom waveform");

		set(context, tree, "second_player", "Alex");
		checkEquals("Alex", loadSavedConfig().secondPlayer(), "second player set in OneConfig");

		set(context, tree, "qr_address", "192.168.1.9");
		checkEquals("192.168.1.9", loadSavedConfig().qrAddress(), "QR address set in OneConfig");
		set(context, tree, "qr_address", "not a host!");
		checkEquals("192.168.1.9", loadSavedConfig().qrAddress(), "QR address after an invalid entry");
		set(context, tree, "qr_address", "");
		checkEquals(DgLabConfig.QR_ADDRESS_AUTOMATIC, loadSavedConfig().qrAddress(), "QR address after clearing it");
		set(context, tree, "qr_port", 23456);
		checkEquals(23456, loadSavedConfig().qrPort(), "QR port set in OneConfig");

		check("my-pc.example.com".matches(DgLabOneConfig.HOST_PATTERN), "the address field must accept host names");
		check("10.0.0.2".matches(DgLabOneConfig.HOST_PATTERN), "the address field must accept IPv4 addresses");
		check("fe80::1".matches(DgLabOneConfig.HOST_PATTERN), "the address field must accept IPv6 addresses");
		check(!"not a host!".matches(DgLabOneConfig.HOST_PATTERN), "the address field must reject spaces and symbols");
		check(!"Alex Smith".matches(DgLabOneConfig.PLAYER_NAME_PATTERN), "the player field must reject spaces");
		resetConfig(context);
	}

	/**
	 * A change made on the mod's side reaches OneConfig at once: the option reports the new value, and the callbacks
	 * OneConfig's widgets listen with run.
	 */
	private static void checkWritesFromTheMod(final ClientGameTestContext context, final Tree tree) {
		Property<Boolean> hudLimit = (Property<Boolean>) tree.getProp("hud_limit");
		Property<Float> multiplier = (Property<Float>) tree.getProp("output_multiplier");
		Property<Float> hudX = (Property<Float>) tree.getProp("hud_x");
		List<Object> seen = new ArrayList<>();
		Predicate<Boolean> hudLimitListener = value -> {
			seen.add(value);
			return false;
		};
		Predicate<Float> multiplierListener = value -> {
			seen.add(value);
			return false;
		};
		Predicate<Float> hudXListener = value -> {
			seen.add(value);
			return false;
		};
		context.runOnClient(client -> {
			hudLimit.addCallback(hudLimitListener);
			multiplier.addCallback(multiplierListener);
			hudX.addCallback(hudXListener);
		});
		try {
			// The game only handles key bindings and commands in a world.
			try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
				singleplayer.getConnection().waitForChunksRender();

				// The mod's key binding.
				Boolean flipped = context.computeOnClient(client -> !DgLabClient.config().get(ToggleSetting.HUD_LIMIT));
				KeyMapping key = bindKey(context, ToggleSetting.HUD_LIMIT.keyName());
				context.getInput().pressKey(key);
				context.waitTicks(2);
				unbindKey(context, key);
				checkEquals(flipped, context.computeOnClient(client -> hudLimit.get()), "switch in OneConfig after the key binding");
				check(seen.contains(flipped), "OneConfig's switch must be told about the key binding, saw " + seen);

				// The mod's command.
				seen.clear();
				context.runOnClient(client -> client.player.connection.sendCommand("dglab multiplier 50"));
				context.waitFor(client -> DgLabClient.config().outputMultiplier() == 50);
				checkEquals(50.0F, context.computeOnClient(client -> multiplier.get()), "multiplier in OneConfig after the command");
				check(seen.contains(50.0F), "OneConfig's slider must be told about the command, saw " + seen);
			}

			// The mod's own screens call the same setters.
			seen.clear();
			configure(context, config -> config.setHudX(40));
			checkEquals(40.0F, context.computeOnClient(client -> hudX.get()), "HUD position in OneConfig after the mod's screen");
			check(seen.contains(40.0F), "OneConfig's slider must be told about the mod's screen, saw " + seen);
		} finally {
			context.runOnClient(client -> {
				hudLimit.removeCallback(hudLimitListener);
				multiplier.removeCallback(multiplierListener);
				hudX.removeCallback(hudXListener);
			});
		}
		resetConfig(context);
	}

	/** OneConfig's reset restores the mod's defaults, not the values the settings had when the game started. */
	private static void checkDefaults(final ClientGameTestContext context, final Tree tree) {
		for (ToggleSetting setting : ToggleSetting.values()) {
			checkEquals(setting.defaultValue(), tree.getProp(setting.id()).getMetadata("default"), "default of " + setting.id() + " in OneConfig");
		}
		configure(context, config -> {
			config.set(ToggleSetting.HUD, false);
			config.setOutputMultiplier(700);
			config.setValue(Channel.B, ChannelOption.DECAY_STEP, 9);
			config.setWaveformPreset(WaveformSlot.B_RECOVERY, WaveformPreset.FLUTTER);
		});
		context.runOnClient(client -> Config.restoreCapturedDefaults(tree));
		DgLabConfig saved = loadSavedConfig();
		check(saved.get(ToggleSetting.HUD), "switch after OneConfig's reset");
		checkEquals(DgLabConfig.DEFAULT_OUTPUT_MULTIPLIER, saved.outputMultiplier(), "output multiplier after OneConfig's reset");
		checkEquals(ChannelOption.DECAY_STEP.defaultValue(), saved.value(Channel.B, ChannelOption.DECAY_STEP), "decay step after OneConfig's reset");
		checkEquals(WaveformSlot.B_RECOVERY.defaultPreset(), saved.waveformPreset(WaveformSlot.B_RECOVERY).orElse(null),
				"preset after OneConfig's reset");
		resetConfig(context);
	}

	/** The listen port moves only once typing has stopped, so the server is not restarted for every digit. */
	private static void checkListenPort(final ClientGameTestContext context, final Tree tree) {
		int original = context.computeOnClient(client -> DgLabClient.config().listenPort());
		for (int port : List.of(1, 12, 123, 1234, OTHER_PORT)) {
			set(context, tree, "listen_port", port);
		}
		checkEquals(OTHER_PORT, context.computeOnClient(client -> tree.getProp("listen_port").get()), "listen port shown while typing");
		context.waitTicks(DgLabOneConfig.LISTEN_PORT_SETTLE_TICKS / 2);
		checkEquals(original, context.computeOnClient(client -> DgLabClient.config().listenPort()), "listen port while typing");
		checkEquals(original, context.computeOnClient(client -> DgLabClient.controller().server().port()), "server port while typing");
		context.waitFor(client -> DgLabClient.config().listenPort() == OTHER_PORT);
		context.waitFor(client -> DgLabClient.controller().server().port() == OTHER_PORT
				&& DgLabClient.controller().server().state() == PairingServer.State.LISTENING);
		LOGGER.info("The connection server moved to port {} once typing stopped", OTHER_PORT);
		checkEquals(OTHER_PORT, loadSavedConfig().listenPort(), "saved listen port");

		set(context, tree, "listen_port", original);
		context.waitFor(client -> DgLabClient.controller().server().port() == original
				&& DgLabClient.controller().server().state() == PairingServer.State.LISTENING);
	}

	private static void checkModToggle(final ClientGameTestContext context) {
		OneConfigModToggle toggle = new OneConfigModToggle();
		check(context.computeOnClient(client -> toggle.isEnabled()), "the card's switch must show the output on");
		context.runOnClient(client -> toggle.setEnabled(false));
		check(!loadSavedConfig().get(ToggleSetting.ENABLED), "the card's switch must turn the output off");
		configure(context, config -> config.set(ToggleSetting.ENABLED, true));
		check(context.computeOnClient(client -> toggle.isEnabled()), "the card's switch must follow the master switch");
	}

	/** OneConfig's own screen, on the mod list and on the mod's page, in both languages. */
	/**
	 * Opens OneConfig in a world with its default key, as a player would, and takes screenshots of its mod list and
	 * of the mod's page in both languages. On the mod's page, it also checks the buttons that open the mod's own
	 * screens. OneConfig's key is a key binding of the game, which only works in a world.
	 */
	private static void takeScreenshots(final ClientGameTestContext context, final Tree tree) {
		context.runOnClient(client -> {
			client.options.guiScale().set(SCREENSHOT_GUI_SCALE);
			client.resizeGui();
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			for (String language : List.of("en_us", "zh_cn")) {
				setLanguage(context, language);
				context.setScreen(() -> null);
				// OneConfig looks at the held keys once per tick, so the key is held for longer than one.
				InputConstants.Key key = InputConstants.getKey(ONECONFIG_KEY);
				context.getInput().holdKey(key);
				context.waitTicks(ONECONFIG_KEY_HOLD_TICKS);
				context.getInput().releaseKey(key);
				context.waitFor(client -> client.gui.screen() != null);
				LOGGER.info("{} opened {}", ONECONFIG_KEY, context.computeOnClient(client -> client.gui.screen().getClass().getName()));
				context.getInput().setCursorPos(0, 0);
				context.waitTicks(20);
				context.takeScreenshot("oneconfig-" + language + "-mods");
				// What OneConfig calls the mod ID of a page is the ID of its tree.
				context.runOnClient(client -> OneConfigUI.open(new OneConfigDestination.ModConfig(OneConfigPages.modConfig(DgLabOneConfig.ID))));
				context.waitTicks(20);
				context.takeScreenshot("oneconfig-" + language + "-dglab");
				checkButtons(context, tree);
				context.setScreen(() -> null);
			}
		}
		setLanguage(context, "en_us");
	}

	/** Each button opens one of the mod's own screens, and "Done" there returns to OneConfig. */
	private static void checkButtons(final ClientGameTestContext context, final Tree tree) {
		Screen oneConfig = context.computeOnClient(client -> client.gui.screen());
		check(oneConfig != null, "OneConfig is not open");
		for (Map.Entry<String, Class<? extends Screen>> button : Map.of("pair", PairingScreen.class,
				"edit_waveforms", WaveformScreen.class).entrySet()) {
			Node node = tree.getProp(button.getKey());
			check(node != null, "OneConfig has no button " + button.getKey());
			Runnable action = (Runnable) node.getMetadata("runnable");
			check(action != null, "button " + button.getKey() + " does nothing");
			context.runOnClient(client -> action.run());
			context.waitFor(client -> button.getValue().isInstance(client.gui.screen()));
			clickButton(context, "gui.done");
			context.waitFor(client -> client.gui.screen() == oneConfig);
		}
		LOGGER.info("OneConfig's buttons open the mod's screens and return to {}", oneConfig.getClass().getName());
	}

	private static <T> void set(final ClientGameTestContext context, final Tree tree, final String id, final T value) {
		Node node = tree.getProp(id);
		check(node != null, "OneConfig has no option " + id);
		context.runOnClient(client -> ((Property<T>) node).set(value));
	}
}
