package com.urntt.dglab.gametest;

import static com.urntt.dglab.gametest.GameTestSupport.APP_LIMIT;
import static com.urntt.dglab.gametest.GameTestSupport.LOGGER;
import static com.urntt.dglab.gametest.GameTestSupport.awaitCommands;
import static com.urntt.dglab.gametest.GameTestSupport.bindKey;
import static com.urntt.dglab.gametest.GameTestSupport.check;
import static com.urntt.dglab.gametest.GameTestSupport.checkEquals;
import static com.urntt.dglab.gametest.GameTestSupport.checkNoCommands;
import static com.urntt.dglab.gametest.GameTestSupport.clickButton;
import static com.urntt.dglab.gametest.GameTestSupport.configure;
import static com.urntt.dglab.gametest.GameTestSupport.connectApp;
import static com.urntt.dglab.gametest.GameTestSupport.get;
import static com.urntt.dglab.gametest.GameTestSupport.loadSavedConfig;
import static com.urntt.dglab.gametest.GameTestSupport.prepareWorld;
import static com.urntt.dglab.gametest.GameTestSupport.resetConfig;
import static com.urntt.dglab.gametest.GameTestSupport.setLanguage;
import static com.urntt.dglab.gametest.GameTestSupport.strength;
import static com.urntt.dglab.gametest.GameTestSupport.unbindKey;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.config.ChannelOption;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.hud.PauseMenuStatus;
import com.urntt.dglab.hud.StrengthHud;
import com.urntt.dglab.protocol.AppCommands;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.screen.ConnectionScreen;
import com.urntt.dglab.screen.DgLabConfigScreen;
import com.urntt.dglab.screen.PairingScreen;
import com.urntt.dglab.screen.StrengthScreen;
import com.urntt.dglab.screen.TwoPlayerScreen;
import com.urntt.dglab.screen.WaveformScreen;
import com.urntt.dglab.server.PairingServer;
import com.urntt.dglab.waveform.Waveform;
import com.urntt.dglab.waveform.WaveformPreset;
import com.urntt.dglab.waveform.WaveformSlot;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Plays in a singleplayer world with a fake DG-LAB app connected, and checks what the app receives when the player
 * is hurt, recovers, and dies, what every key binding and command does, and how the screens look.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DgLabClientGameTest implements FabricClientGameTest {
	private static final int HOLD_TICKS = 10;
	private static final int DECAY_INTERVAL = 2;
	private static final int DECAY_STEP = 4;
	/** Ticks the measured start of the decay may be off, because commands travel through a socket. */
	private static final int TIMING_TOLERANCE = 3;
	/** The GUI scale at which the screens are checked for fitting into a small window. */
	private static final int COMPACT_GUI_SCALE = 2;
	/** The GUI scale at which the long screens show all their rows at once. */
	private static final int FULL_GUI_SCALE = 1;
	private static final List<String> SILENCE = List.of("strength-1+2+0", "clear-1", "strength-2+2+0", "clear-2");

	@Override
	public void runTest(final ClientGameTestContext context) {
		checkDefaults(context);

		FakeDgLabApp app = null;
		try {
			int beforeLeaving;
			try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
				singleplayer.getConnection().waitForChunksRender();
				TestServerContext server = singleplayer.getServer();
				prepareWorld(context, server);
				checkPauseMenuStatus(context);

				app = connectApp(context);
				checkEquals(List.of(), pauseMenuLines(context), "pause menu with an app connected");
				checkDamageAndDecay(context, server, app);
				checkDeath(context, server, app);
				checkToggleKeys(context, server, app);
				app = checkServerToggle(context, app);
				checkCommands(context, app);
				checkOpenSettingsKey(context);
				checkScreens(context);

				// Leave the world with strength on both channels.
				resetConfig(context);
				int beforeDamage = app.commands().size();
				server.runCommand("damage @p 2");
				awaitCommands(context, app, beforeDamage, "after damage before leaving the world");
				beforeLeaving = app.commands().size();
			}

			List<String> commands = awaitCommands(context, app, beforeLeaving, "after leaving the world");
			LOGGER.info("Commands after leaving the world: {}", commands);
			check(commands.containsAll(SILENCE), "expected both channels to be silenced after leaving the world, got " + commands);
			checkEquals(0, app.strength(Channel.A), "strength of channel A in the app after leaving the world");
			checkEquals(0, app.strength(Channel.B), "strength of channel B in the app after leaving the world");
		} finally {
			if (app != null) {
				app.close();
			}
		}
	}

	/** A fresh installation: everything is at its default, in memory and in the saved file. */
	private static void checkDefaults(final ClientGameTestContext context) {
		DgLabConfig saved = loadSavedConfig();
		for (ToggleSetting setting : ToggleSetting.values()) {
			checkEquals(setting.defaultValue(), get(context, setting), "default of " + setting.id());
			checkEquals(setting.defaultValue(), saved.get(setting), "saved default of " + setting.id());
			KeyMapping key = KeyMapping.get(setting.keyName());
			check(key != null && key.isUnbound(), "the toggle key of " + setting.id() + " must exist and be unbound");
		}
		KeyMapping openSettingsKey = KeyMapping.get(DgLabClient.OPEN_SETTINGS_KEY_NAME);
		check(openSettingsKey != null && openSettingsKey.isUnbound(), "the open settings key must exist and be unbound");

		// The connection server starts with the game.
		context.waitFor(client -> DgLabClient.controller().server().state() == PairingServer.State.LISTENING);
		checkEquals(DgLabConfig.DEFAULT_PORT, context.computeOnClient(client -> DgLabClient.controller().server().port()), "port of the connection server");
	}

	/**
	 * Without an app, the HUD stays empty and the pause menu says why.
	 */
	private static void checkPauseMenuStatus(final ClientGameTestContext context) {
		checkEquals(List.of(), hudLines(context), "HUD before an app connected");
		checkEquals(List.of(PauseMenuStatus.NOT_CONNECTED.getString()), pauseMenuLines(context), "pause menu before an app connected");
		context.takeScreenshot("hud-not-connected");

		context.setScreen(() -> new PauseScreen(true));
		check(context.computeOnClient(client -> PauseMenuStatus.isPauseMenu(client.gui.screen())), "the pause menu must be recognized");
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		context.takeScreenshot("pause-menu-not-connected");
		context.setScreen(() -> new PauseScreen(false));
		check(!context.computeOnClient(client -> PauseMenuStatus.isPauseMenu(client.gui.screen())), "the bare pause screen is not the pause menu");
		context.setScreen(() -> null);

		// The HUD's switch also hides the hint on the pause menu.
		configure(context, config -> config.set(ToggleSetting.HUD, false));
		checkEquals(List.of(), pauseMenuLines(context), "pause menu with the HUD off");
		configure(context, config -> config.set(ToggleSetting.HUD, true));
	}

	private static void checkDamageAndDecay(final ClientGameTestContext context, final TestServerContext server, final FakeDgLabApp app) {
		configure(context, config -> {
			for (Channel channel : Channel.values()) {
				config.setValue(channel, ChannelOption.HOLD_TICKS, HOLD_TICKS);
				config.setValue(channel, ChannelOption.DEATH_HOLD_TICKS, HOLD_TICKS);
				config.setValue(channel, ChannelOption.DECAY_INTERVAL, DECAY_INTERVAL);
				config.setValue(channel, ChannelOption.DECAY_STEP, DECAY_STEP);
			}
		});
		String damagePulseA = AppCommands.pulse(Channel.A, WaveformPreset.THUMP.waveform().frames());
		String damagePulseB = AppCommands.pulse(Channel.B, WaveformPreset.THUMP.waveform().frames());
		String recoveryPulseA = AppCommands.pulse(Channel.A, WaveformPreset.SWELL.waveform().frames());
		String recoveryPulseB = AppCommands.pulse(Channel.B, WaveformPreset.SWELL.waveform().frames());

		// 4 points of damage at the default 3 strength per point: 12 on both channels and the damage waveform. After
		// the hold period the recovery waveform starts and the strength decays to the floor: the player lost 4 of 20
		// health, so the floor is a fifth of the default 40.
		int beforeDamage = app.commands().size();
		server.runCommand("damage @p 4");
		int damageTick = -1;
		int decayTick = -1;
		for (int tick = 0; decayTick < 0; tick++) {
			check(tick < 10 * (HOLD_TICKS + DECAY_INTERVAL), damageTick < 0
					? "expected commands after 4 points of damage, but the app received none"
					: "the strength did not start to decay, the app received " + app.commandsAfter(beforeDamage));
			context.waitTick();
			List<String> received = app.commandsAfter(beforeDamage);
			if (damageTick < 0 && received.contains("strength-1+1+12")) {
				damageTick = tick;
			}
			if (received.contains("strength-1+0+" + DECAY_STEP)) {
				decayTick = tick;
			}
		}
		List<String> commands = awaitCommands(context, app, beforeDamage, "after 4 points of damage");
		LOGGER.info("Commands after 4 points of damage: {}", commands);
		LOGGER.info("Decay started {} ticks after the damage, expected {}", decayTick - damageTick, HOLD_TICKS + DECAY_INTERVAL);
		checkEquals(List.of(
				"strength-1+1+12", "strength-2+1+12", "clear-1", "clear-2", damagePulseA, damagePulseB,
				"clear-1", "clear-2", recoveryPulseA, recoveryPulseB, "strength-1+0+4", "strength-2+0+4"), commands.subList(0, 12),
				"commands after 4 points of damage");
		check(commands.subList(12, commands.size()).stream().noneMatch(command -> command.startsWith("strength-")),
				"the strength must stop at the floor, got " + commands);
		check(Math.abs(decayTick - damageTick - (HOLD_TICKS + DECAY_INTERVAL)) <= TIMING_TOLERANCE,
				"decay started " + (decayTick - damageTick) + " ticks after the damage, expected about " + (HOLD_TICKS + DECAY_INTERVAL));
		checkEquals(16.0F, context.computeOnClient(client -> client.player.getHealth()), "health after 4 points of damage");
		checkEquals(8, strength(context, Channel.A), "strength of channel A resting on the floor");
		checkEquals(8, app.strength(Channel.B), "strength of channel B in the app resting on the floor");
		checkEquals(List.of("Channel A: 8", "Channel B: 8"), hudLines(context), "HUD after damage");

		// Without the floor it decays to zero, and the pulse queue is emptied.
		int beforeFloorOff = app.commands().size();
		configure(context, config -> config.set(ToggleSetting.HEALTH_FLOOR, false));
		context.waitFor(client -> DgLabClient.controller().strength().strength(Channel.A) == 0 && app.strength(Channel.B) == 0);
		List<String> afterFloorOff = awaitCommands(context, app, beforeFloorOff, "after switching the floor off").stream()
				.filter(command -> !command.startsWith("pulse-")).toList();
		checkEquals(List.of("strength-1+0+4", "strength-2+0+4", "strength-1+0+4", "clear-1", "strength-2+0+4", "clear-2"), afterFloorOff,
				"commands after switching the floor off");
		checkNoCommands(context, app, "with both channels at zero");

		// A strength the user sets in the app is taken over and decays like any other.
		int beforeAppChange = app.commands().size();
		app.setStrength(Channel.A, 2 * DECAY_STEP);
		List<String> afterAppChange = awaitCommands(context, app, beforeAppChange, "after raising the strength in the app").stream()
				.filter(command -> !command.startsWith("pulse-")).toList();
		checkEquals(List.of("clear-1", "strength-1+0+4", "strength-1+0+4", "clear-1"), afterAppChange, "commands after raising the strength in the app");
		checkEquals(0, strength(context, Channel.A), "strength of channel A after decaying again");
		configure(context, config -> config.set(ToggleSetting.HEALTH_FLOOR, true));
	}

	private static void checkDeath(final ClientGameTestContext context, final TestServerContext server, final FakeDgLabApp app) {
		// The player has 16 health left: the fatal damage adds 48, and death adds the default 50 on top.
		int beforeDeath = app.commands().size();
		server.runCommand("kill @p");
		List<String> afterDeath = awaitCommands(context, app, beforeDeath, "after the player died");
		LOGGER.info("Commands after death: {}", afterDeath);
		checkEquals(List.of("strength-1+1+48", "strength-2+1+48", "strength-1+2+98", "strength-2+2+98"), afterDeath.subList(0, 4),
				"commands after death");
		checkEquals(98, app.strength(Channel.A), "strength of channel A in the app after death");

		// Respawning sets the health without it counting as damage or healing.
		context.waitFor(client -> client.player != null && client.player.getHealth() == client.player.getMaxHealth() && client.player.onGround());
		context.waitTicks(HOLD_TICKS);
		check(app.commandsAfter(beforeDeath).stream().filter(command -> command.startsWith("strength-") && command.contains("+1+")).count() == 2,
				"respawning must not add strength, got " + app.commandsAfter(beforeDeath));

		// The strength command sets both channels directly.
		int beforeCommand = app.commands().size();
		runCommand(context, "dglab strength set 0 0");
		List<String> afterCommand = awaitCommands(context, app, beforeCommand, "after /dglab strength set");
		check(afterCommand.containsAll(List.of("strength-1+2+0", "strength-2+2+0")), "expected both channels to be set to zero, got " + afterCommand);
		context.waitFor(client -> DgLabClient.controller().strength().strength(Channel.A) == 0 && DgLabClient.controller().strength().strength(Channel.B) == 0);
		context.waitTicks(2 * HOLD_TICKS);
	}

	private static void checkToggleKeys(final ClientGameTestContext context, final TestServerContext server, final FakeDgLabApp app) {
		// Every toggle key flips its setting and saves it. The connection server is checked separately.
		for (ToggleSetting setting : ToggleSetting.values()) {
			if (setting == ToggleSetting.SERVER_ENABLED) {
				continue;
			}
			KeyMapping key = bindKey(context, setting.keyName());
			boolean before = get(context, setting);
			context.getInput().pressKey(key);
			context.waitTicks(2);
			checkEquals(!before, get(context, setting), setting.id() + " after pressing its key");
			checkEquals(!before, loadSavedConfig().get(setting), "saved " + setting.id() + " after pressing its key");
			if (setting == ToggleSetting.HUD_LIMIT) {
				checkEquals(List.of("Channel A: 0 / " + APP_LIMIT, "Channel B: 0 / " + APP_LIMIT), hudLines(context), "HUD with limits");
				context.takeScreenshot("hud-limit-toggled");
			}
			context.getInput().pressKey(key);
			context.waitTicks(2);
			checkEquals(before, get(context, setting), setting.id() + " after pressing its key twice");
			unbindKey(context, key);
		}
		context.waitTicks(HOLD_TICKS);

		// The master switch silences both channels and makes the mod ignore damage.
		int beforeDamage = app.commands().size();
		server.runCommand("damage @p 2");
		checkEquals(List.of("strength-1+1+6", "strength-2+1+6"), awaitCommands(context, app, beforeDamage, "after 2 points of damage").subList(0, 2),
				"commands after 2 points of damage");
		KeyMapping masterKey = bindKey(context, ToggleSetting.ENABLED.keyName());
		int beforeOff = app.commands().size();
		context.getInput().pressKey(masterKey);
		// Whatever the hold period was still sending, switching off ends with both channels at zero and emptied.
		List<String> afterOff = awaitCommands(context, app, beforeOff, "after switching the output off");
		checkEquals(SILENCE, afterOff.subList(Math.max(0, afterOff.size() - SILENCE.size()), afterOff.size()), "last commands after switching the output off");
		context.takeScreenshot("output-off");
		check(!get(context, ToggleSetting.ENABLED), "the output must be off");
		checkEquals(0, app.strength(Channel.A), "strength of channel A in the app with the output off");
		server.runCommand("damage @p 2");
		checkNoCommands(context, app, "after damage with the output off");
		context.getInput().pressKey(masterKey);
		context.waitTicks(2);
		unbindKey(context, masterKey);
		check(get(context, ToggleSetting.ENABLED), "the output must be on again");

		// With channel B off, only channel A reacts.
		KeyMapping channelKey = bindKey(context, ToggleSetting.CHANNEL_B.keyName());
		context.getInput().pressKey(channelKey);
		context.waitTicks(2);
		int beforeOneChannel = app.commands().size();
		server.runCommand("damage @p 2");
		List<String> oneChannel = awaitCommands(context, app, beforeOneChannel, "after damage with channel B off");
		checkEquals("strength-1+1+6", oneChannel.getFirst(), "first command after damage with channel B off");
		check(oneChannel.stream().noneMatch(command -> command.startsWith("strength-2") || command.startsWith("pulse-B")),
				"channel B must not react while it is off, got " + oneChannel);
		context.getInput().pressKey(channelKey);
		context.waitTicks(2);
		unbindKey(context, channelKey);
		runCommand(context, "dglab strength set 0 0");
		context.waitFor(client -> DgLabClient.controller().strength().strength(Channel.A) == 0);
		context.waitTicks(2 * HOLD_TICKS);
	}

	/**
	 * Stops and restarts the connection server with its toggle key, and returns a new app bound to it.
	 */
	private static FakeDgLabApp checkServerToggle(final ClientGameTestContext context, final FakeDgLabApp app) {
		KeyMapping key = bindKey(context, ToggleSetting.SERVER_ENABLED.keyName());
		context.getInput().pressKey(key);
		app.awaitClosed();
		context.waitFor(client -> DgLabClient.controller().server().state() == PairingServer.State.STOPPED);
		context.waitFor(client -> !DgLabClient.controller().strength().isBound());
		check(!loadSavedConfig().get(ToggleSetting.SERVER_ENABLED), "the stopped server must be saved");
		checkEquals(List.of(), hudLines(context), "HUD with the connection server off");
		checkEquals(List.of(), pauseMenuLines(context), "pause menu with the connection server off");
		app.close();

		context.getInput().pressKey(key);
		context.waitTicks(2);
		unbindKey(context, key);
		return connectApp(context);
	}

	private static void checkCommands(final ClientGameTestContext context, final FakeDgLabApp app) {
		int beforeSet = app.commands().size();
		runCommand(context, "dglab strength set 5 6");
		checkEquals(List.of("strength-1+2+5", "strength-2+2+6"), awaitCommands(context, app, beforeSet, "after /dglab strength set").subList(0, 2),
				"commands after /dglab strength set");
		runCommand(context, "dglab strength set 0 0");
		context.waitFor(client -> DgLabClient.controller().strength().strength(Channel.B) == 0 && app.strength(Channel.B) == 0);
		context.waitTicks(HOLD_TICKS);

		runCommand(context, "dglab toggle hud_limit");
		check(get(context, ToggleSetting.HUD_LIMIT), "/dglab toggle must switch the setting on");
		runCommand(context, "dglab toggle hud_limit");
		check(!get(context, ToggleSetting.HUD_LIMIT), "/dglab toggle must switch the setting off again");

		runCommand(context, "dglab config set decay_step 2 3");
		checkEquals(2.0, context.computeOnClient(client -> DgLabClient.config().value(Channel.A, ChannelOption.DECAY_STEP)), "decay step of channel A");
		checkEquals(3.0, loadSavedConfig().value(Channel.B, ChannelOption.DECAY_STEP), "saved decay step of channel B");

		int beforeTest = app.commands().size();
		runCommand(context, "dglab test b_recovery");
		checkEquals(List.of("clear-2", AppCommands.pulse(Channel.B, WaveformPreset.SWELL.waveform().frames())),
				awaitCommands(context, app, beforeTest, "after /dglab test"), "commands after /dglab test");

		runCommand(context, "dglab status");
		runCommand(context, "dglab config get");
		context.waitTicks(2);
		context.takeScreenshot("command-feedback");

		runCommand(context, "dglab settings");
		context.waitForScreen(DgLabConfigScreen.class);
		context.setScreen(() -> null);
		runCommand(context, "dglab pair");
		context.waitForScreen(PairingScreen.class);
		context.setScreen(() -> null);
	}

	private static void checkOpenSettingsKey(final ClientGameTestContext context) {
		KeyMapping key = bindKey(context, DgLabClient.OPEN_SETTINGS_KEY_NAME);
		context.getInput().pressKey(key);
		context.waitForScreen(DgLabConfigScreen.class);
		context.setScreen(() -> null);
		unbindKey(context, key);
	}

	/**
	 * Takes a screenshot of every screen in both languages, reads the pairing QR code back from its screenshot, and
	 * edits a waveform through the editor.
	 */
	private static void checkScreens(final ClientGameTestContext context) {
		resetConfig(context);

		for (String language : List.of("en_us", "zh_cn")) {
			setLanguage(context, language);
			setGuiScale(context, FULL_GUI_SCALE);
			screenshot(context, language + "-config-full", () -> new DgLabConfigScreen(null));
			screenshot(context, language + "-strength-full", () -> new StrengthScreen(new DgLabConfigScreen(null)));
			screenshot(context, language + "-waveforms-full", () -> new WaveformScreen(new DgLabConfigScreen(null)));
			context.setScreen(() -> null);

			setGuiScale(context, COMPACT_GUI_SCALE);
			runCommand(context, "dglab strength set 30 20");
			context.waitTicks(2);
			context.takeScreenshot(language + "-hud");
			screenshot(context, language + "-config", () -> new DgLabConfigScreen(null));
			screenshot(context, language + "-strength", () -> new StrengthScreen(new DgLabConfigScreen(null)));
			screenshot(context, language + "-connection", () -> new ConnectionScreen(new DgLabConfigScreen(null), DgLabClient.config()));
			screenshot(context, language + "-two-player", () -> new TwoPlayerScreen(new DgLabConfigScreen(null), DgLabClient.config()));
			screenshot(context, language + "-waveforms", () -> new WaveformScreen(new DgLabConfigScreen(null)));
			clickButton(context, "options.dglab.waveform.edit");
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(2);
			context.takeScreenshot(language + "-waveform-editor");
			clickButton(context, "gui.cancel");

			Path pairing = screenshot(context, language + "-pairing", () -> new PairingScreen(null));
			String expected = context.computeOnClient(client -> DgLabClient.controller().pairingUrl());
			String decoded = decodeQrCode(pairing);
			LOGGER.info("QR code read from {}: {}", pairing.getFileName(), decoded);
			checkEquals(expected, decoded, "text of the QR code on the pairing screen");
			context.setScreen(() -> null);
		}
		setLanguage(context, "en_us");

		// The editor, used through its buttons: add 0.1 s to the first slot's custom waveform and save it.
		Waveform before = context.computeOnClient(client -> DgLabClient.config().customWaveform(WaveformSlot.A_DAMAGE));
		context.setScreen(() -> new WaveformScreen(new DgLabConfigScreen(null)));
		clickButton(context, "options.dglab.waveform.edit");
		clickButton(context, "options.dglab.waveform.editor.add");
		clickButton(context, "gui.done");
		context.waitForScreen(WaveformScreen.class);
		context.setScreen(() -> null);
		Waveform after = context.computeOnClient(client -> DgLabClient.config().waveform(WaveformSlot.A_DAMAGE));
		check(context.computeOnClient(client -> DgLabClient.config().waveformPreset(WaveformSlot.A_DAMAGE).isEmpty()),
				"saving in the editor must make the slot use its custom waveform");
		checkEquals(before.frameCount() + 1, after.frameCount(), "frames of the edited waveform");
		checkEquals(before.samples(), after.samples().subList(0, before.samples().size()), "the samples that were not edited");
		checkEquals(after, loadSavedConfig().waveform(WaveformSlot.A_DAMAGE), "the saved edited waveform");

		runCommand(context, "dglab strength set 0 0");
		context.waitFor(client -> DgLabClient.controller().strength().strength(Channel.A) == 0);
	}

	private static void setGuiScale(final ClientGameTestContext context, final int scale) {
		context.runOnClient(client -> {
			client.options.guiScale().set(scale);
			client.resizeGui();
		});
	}

	/**
	 * Shows a screen and takes a screenshot of it, with the cursor out of the way so that no tooltip covers it.
	 */
	private static Path screenshot(final ClientGameTestContext context, final String name, final Supplier<Screen> screen) {
		context.setScreen(screen);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		return context.takeScreenshot(name);
	}

	private static String decodeQrCode(final Path screenshot) {
		try {
			BufferedImage image = ImageIO.read(screenshot.toFile());
			int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
			return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
		} catch (IOException | ReaderException e) {
			throw new AssertionError("no QR code could be read from " + screenshot, e);
		}
	}

	private static List<String> pauseMenuLines(final ClientGameTestContext context) {
		return context.computeOnClient(client -> PauseMenuStatus.lines(DgLabClient.config(), DgLabClient.controller())
				.stream().map(Component::getString).toList());
	}

	private static List<String> hudLines(final ClientGameTestContext context) {
		return context.computeOnClient(client -> StrengthHud.lines(DgLabClient.config(), DgLabClient.controller(), client.getUser().getName())
				.stream().map(Component::getString).toList());
	}

	private static void runCommand(final ClientGameTestContext context, final String command) {
		context.runOnClient(client -> client.player.connection.sendCommand(command));
	}
}
