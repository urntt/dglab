package com.urntt.dglab.gametest;

import static com.urntt.dglab.gametest.GameTestSupport.LOGGER;
import static com.urntt.dglab.gametest.GameTestSupport.LOOPBACK;
import static com.urntt.dglab.gametest.GameTestSupport.check;
import static com.urntt.dglab.gametest.GameTestSupport.checkEquals;

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.qrcode.decoder.Decoder;
import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.DgLabController;
import com.urntt.dglab.command.DgLabCommand;
import com.urntt.dglab.config.ChannelOption;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.feedback.HealthTracker;
import com.urntt.dglab.hud.PauseMenuStatus;
import com.urntt.dglab.feedback.StrengthController;
import com.urntt.dglab.feedback.StrengthController.Source;
import com.urntt.dglab.protocol.AppCommands;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.protocol.SocketMessage;
import com.urntt.dglab.protocol.StrengthReport;
import com.urntt.dglab.screen.PairingScreen;
import com.urntt.dglab.server.PairingQr;
import com.urntt.dglab.server.PairingServer;
import com.urntt.dglab.server.PairingUrl;
import com.urntt.dglab.waveform.Waveform;
import com.urntt.dglab.waveform.WaveformPreset;
import com.urntt.dglab.waveform.WaveformSlot;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.locale.Language;

/**
 * Checks everything that needs no world: the protocol, waveforms, the configuration file, the strength rules, the
 * connection server, and the pairing QR code.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DgLabLogicGameTest implements FabricClientGameTest {
	private static final long TIMEOUT_NANOS = 10_000_000_000L;

	@Override
	public void runTest(final ClientGameTestContext context) {
		try {
			Path directory = Files.createTempDirectory("dglab-gametest");
			checkProtocol();
			checkWaveforms();
			checkTranslations();
			checkConfig(directory);
			checkHealthTracker();
			checkStrengthRules(directory);
			checkTwoPlayerRules(directory);
			checkPairingQr();
			checkPairingServer();
			checkPauseMenuStatus(directory);
		} catch (IOException | FormatException | ChecksumException e) {
			throw new AssertionError(e);
		}
	}

	private static void checkProtocol() {
		checkEquals("strength-1+1+12", AppCommands.increaseStrength(Channel.A, 12), "increase command");
		checkEquals("strength-2+0+3", AppCommands.decreaseStrength(Channel.B, 3), "decrease command");
		checkEquals("strength-2+2+0", AppCommands.setStrength(Channel.B, 0), "set command");
		checkEquals("clear-1", AppCommands.clear(Channel.A), "clear command");
		checkEquals("pulse-B:[\"0A0A0A0A64646464\",\"0A0A0A0A00000000\"]",
				AppCommands.pulse(Channel.B, List.of("0A0A0A0A64646464", "0A0A0A0A00000000")), "pulse command");

		SocketMessage message = new SocketMessage(SocketMessage.TYPE_MSG, "terminal", "app", AppCommands.pulse(Channel.A, List.of("0A0A0A0A64646464")));
		checkEquals("{\"type\":\"msg\",\"clientId\":\"terminal\",\"targetId\":\"app\",\"message\":\"pulse-A:[\\\"0A0A0A0A64646464\\\"]\"}",
				message.toJson(), "message JSON");
		checkEquals(message, SocketMessage.parse(message.toJson()).orElse(null), "message round trip");
		check(SocketMessage.parse("not json").isEmpty(), "text that is not JSON must be rejected");
		check(SocketMessage.parse("[1]").isEmpty(), "a JSON array must be rejected");
		check(SocketMessage.parse("{\"type\":\"msg\",\"clientId\":\"a\"}").isEmpty(), "a message without all four fields must be rejected");

		checkEquals(new StrengthReport(11, 7, 100, 35), StrengthReport.parse("strength-11+7+100+35").orElse(null), "strength report");
		checkEquals("strength-11+7+100+35", new StrengthReport(11, 7, 100, 35).toMessage(), "strength report text");
		check(StrengthReport.parse("strength-1+1+12").isEmpty(), "a strength command is not a strength report");
		check(StrengthReport.parse("feedback-3").isEmpty(), "a feedback message is not a strength report");
	}

	private static void checkWaveforms() {
		Waveform thump = WaveformPreset.THUMP.waveform();
		checkEquals(List.of("0A0A0A0A64646464", "0A0A0A0A64646464", "0A0A0A0A64503C28", "0A0A0A0A14000000"), thump.frames(), "frames of the thump preset");
		checkEquals(8, thump.durationTicks(), "ticks the thump preset lasts");
		checkEquals(thump, Waveform.parseFrames(thump.frames()).orElse(null), "frames round trip");
		checkEquals(thump, Waveform.parseCode(thump.code()).orElse(null), "code round trip");
		checkEquals(thump, Waveform.parseCode("[" + thump.code().toLowerCase(Locale.ROOT) + "]\n").orElse(null), "code in brackets and lower case");
		checkEquals(thump, Waveform.parseCode(String.join(" ", thump.frames())).orElse(null), "code without quotes");

		check(Waveform.parseCode("").isEmpty(), "empty code must be rejected");
		check(Waveform.parseFrames(List.of("0A0A0A0A646464")).isEmpty(), "a short frame must be rejected");
		check(Waveform.parseFrames(List.of("0A0A0A0A6464646G")).isEmpty(), "a frame that is not hexadecimal must be rejected");
		check(Waveform.parseFrames(List.of("090A0A0A64646464")).isEmpty(), "a frequency below the minimum must be rejected");
		check(Waveform.parseFrames(List.of("F10A0A0A64646464")).isEmpty(), "a frequency above the maximum must be rejected");
		check(Waveform.parseFrames(List.of("0A0A0A0A64646465")).isEmpty(), "an intensity above 100 must be rejected");
		check(Waveform.parseFrames(Collections.nCopies(Waveform.MAX_FRAMES + 1, "0A0A0A0A64646464")).isEmpty(), "too many frames must be rejected");

		// The longest waveform must fit into one protocol message, with IDs as long as the server's.
		Waveform longest = Waveform.parseFrames(Collections.nCopies(Waveform.MAX_FRAMES, "F0F0F0F064646464")).orElseThrow();
		String id = UUID.randomUUID().toString();
		int length = new SocketMessage(SocketMessage.TYPE_MSG, id, id, AppCommands.pulse(Channel.A, longest.frames())).toJson().length();
		LOGGER.info("Longest pulse message: {} of {} characters", length, SocketMessage.MAX_LENGTH);
		check(length <= SocketMessage.MAX_LENGTH, "the longest pulse message has " + length + " characters");
		check(Waveform.MAX_FRAMES <= AppCommands.MAX_PULSE_FRAMES, "the longest waveform has more frames than one command may carry");

		for (WaveformSlot slot : WaveformSlot.values()) {
			checkEquals(slot, WaveformSlot.byId(slot.id()).orElse(null), "slot looked up by its ID");
			checkEquals(slot, WaveformSlot.of(slot.channel(), slot.kind()), "slot looked up by channel and kind");
		}
	}

	/** Every name derived from an enum needs its translation. */
	private static void checkTranslations() {
		List<String> keys = new ArrayList<>();
		for (ToggleSetting setting : ToggleSetting.values()) {
			keys.add(setting.captionKey());
			keys.add(setting.tooltipKey());
			keys.add(setting.keyName());
		}
		for (ChannelOption option : ChannelOption.values()) {
			keys.add(option.captionKey());
			keys.add(option.tooltipKey());
		}
		for (WaveformPreset preset : WaveformPreset.values()) {
			keys.add(preset.labelKey());
		}
		for (WaveformSlot slot : WaveformSlot.values()) {
			keys.add(slot.labelKey());
		}
		for (Channel channel : Channel.values()) {
			keys.add(DgLabClient.channelNameKey(channel));
		}
		for (PairingServer.State state : PairingServer.State.values()) {
			keys.add(DgLabCommand.serverStateKey(state));
			keys.add(PairingScreen.stateKey(state));
		}
		for (String key : keys) {
			check(Language.getInstance().has(key), "missing translation: " + key);
		}
	}

	private static void checkConfig(final Path directory) throws IOException {
		Path path = directory.resolve("config.json");
		DgLabConfig defaults = DgLabConfig.load(path);
		check(Files.exists(path), "a missing config file must be created");
		for (ToggleSetting setting : ToggleSetting.values()) {
			checkEquals(setting.defaultValue(), defaults.get(setting), "default of " + setting.id());
		}
		for (Channel channel : Channel.values()) {
			for (ChannelOption option : ChannelOption.values()) {
				checkEquals(option.defaultValue(), defaults.value(channel, option), "default of " + option.id());
			}
		}
		checkEquals(DgLabConfig.DEFAULT_PORT, defaults.listenPort(), "default listen port");
		checkEquals(DgLabConfig.QR_ADDRESS_AUTOMATIC, defaults.qrAddress(), "default QR address");
		checkEquals(DgLabConfig.QR_PORT_SAME_AS_LISTEN, defaults.qrPort(), "default QR port");
		checkEquals(0, defaults.secondPlayerQuitStrength(), "default strength added when the second player leaves");
		for (WaveformSlot slot : WaveformSlot.values()) {
			checkEquals(slot.defaultPreset(), defaults.waveformPreset(slot).orElse(null), "default preset of " + slot.id());
		}

		defaults.set(ToggleSetting.HUD_LIMIT, true);
		defaults.setValue(Channel.B, ChannelOption.STRENGTH_PER_DAMAGE, 1.26);
		defaults.setConnection(12345, "192.168.1.20", 23456);
		defaults.setHudX(40);
		defaults.setTwoPlayer("  Alex  ", 30);
		Waveform custom = Waveform.ofIntensities(100, 10, 20, 30, 40);
		defaults.setCustomWaveform(WaveformSlot.B_RECOVERY, custom);
		defaults.useCustomWaveform(WaveformSlot.B_RECOVERY);
		defaults.setWaveformPreset(WaveformSlot.A_DAMAGE, WaveformPreset.STAIRS);
		DgLabConfig saved = DgLabConfig.load(path);
		check(saved.get(ToggleSetting.HUD_LIMIT), "a toggle must be saved");
		checkEquals(1.3, saved.value(Channel.B, ChannelOption.STRENGTH_PER_DAMAGE), "a value is saved rounded to its step");
		checkEquals(3.0, saved.value(Channel.A, ChannelOption.STRENGTH_PER_DAMAGE), "the other channel's value");
		checkEquals(12345, saved.listenPort(), "saved listen port");
		checkEquals("192.168.1.20", saved.qrAddress(), "saved QR address");
		checkEquals(23456, saved.qrPort(), "saved QR port");
		checkEquals(40, saved.hudX(), "saved HUD position");
		checkEquals("Alex", saved.secondPlayer(), "saved second player");
		checkEquals(30, saved.secondPlayerQuitStrength(), "saved strength added when the second player leaves");
		check(saved.waveformPreset(WaveformSlot.B_RECOVERY).isEmpty(), "the slot must use its custom waveform");
		checkEquals(custom, saved.waveform(WaveformSlot.B_RECOVERY), "saved custom waveform");
		checkEquals(WaveformPreset.STAIRS.waveform(), saved.waveform(WaveformSlot.A_DAMAGE), "saved preset");
		checkEquals("ws://192.168.1.20:23456/terminal", lastPart(PairingUrl.of(saved, "terminal")), "pairing address from the settings");

		// A file from an older version, or edited by hand, keeps what it has and gains everything else.
		Path partial = directory.resolve("partial.json");
		Files.writeString(partial, "{\"toggles\":{\"enabled\":false},\"channels\":{\"a\":{\"hold_ticks\":7}}}");
		DgLabConfig completed = DgLabConfig.load(partial);
		check(!completed.get(ToggleSetting.ENABLED), "a stored toggle must be kept");
		check(completed.get(ToggleSetting.CHANNEL_A), "a missing toggle must get its default");
		checkEquals(7.0, completed.value(Channel.A, ChannelOption.HOLD_TICKS), "a stored value must be kept");
		checkEquals(ChannelOption.HOLD_TICKS.defaultValue(), completed.value(Channel.B, ChannelOption.HOLD_TICKS), "a missing value must get its default");
		check(Files.readString(partial).contains("\"two_player\""), "a completed file must be written back");

		Path invalid = directory.resolve("invalid.json");
		Files.writeString(invalid, "{\"connection\":{\"listen_port\":70000,\"qr_port\":-1},\"hud\":{\"x\":500},"
				+ "\"channels\":{\"a\":{\"decay_interval\":0,\"strength_per_damage\":99}},"
				+ "\"two_player\":{\"player\":\"a name that is far too long\",\"quit_strength\":999},"
				+ "\"waveforms\":{\"a_damage\":{\"preset\":\"nope\",\"custom\":[\"zz\"]}}}");
		DgLabConfig repaired = DgLabConfig.load(invalid);
		checkEquals(DgLabConfig.DEFAULT_PORT, repaired.listenPort(), "an invalid listen port");
		checkEquals(DgLabConfig.QR_PORT_SAME_AS_LISTEN, repaired.qrPort(), "an invalid QR port");
		checkEquals(DgLabConfig.MAX_HUD_PERCENT, repaired.hudX(), "an out-of-range HUD position");
		checkEquals(1.0, repaired.value(Channel.A, ChannelOption.DECAY_INTERVAL), "a decay interval below the minimum");
		checkEquals(20.0, repaired.value(Channel.A, ChannelOption.STRENGTH_PER_DAMAGE), "a strength per damage above the maximum");
		checkEquals(DgLabConfig.MAX_PLAYER_NAME_LENGTH, repaired.secondPlayer().length(), "length of a player name that is too long");
		checkEquals(AppCommands.MAX_STRENGTH, repaired.secondPlayerQuitStrength(), "a strength above the maximum");
		checkEquals(WaveformPreset.THUMP, repaired.waveformPreset(WaveformSlot.A_DAMAGE).orElse(null), "an unknown preset");
		checkEquals(WaveformPreset.THUMP.waveform(), repaired.customWaveform(WaveformSlot.A_DAMAGE), "an invalid custom waveform");

		Path unreadable = directory.resolve("unreadable.json");
		Files.writeString(unreadable, "{ not json");
		DgLabConfig fallback = DgLabConfig.load(unreadable);
		check(fallback.get(ToggleSetting.ENABLED), "an unreadable file must fall back to the defaults");
		checkEquals("{ not json", Files.readString(unreadable), "an unreadable file must be left untouched");
	}

	private static void checkHealthTracker() {
		HealthTracker tracker = new HealthTracker();
		checkEquals(HealthTracker.Change.NONE, tracker.sample(12.0F), "the first sample only sets the baseline");
		checkEquals(new HealthTracker.Change(4.5F, false), tracker.sample(7.5F), "lost health is damage");
		checkEquals(HealthTracker.Change.NONE, tracker.sample(9.0F), "healing is no damage");
		checkEquals(new HealthTracker.Change(9.0F, true), tracker.sample(0.0F), "losing all health is a death");
		check(!tracker.isAlive(), "the tracker must know the player is dead");
		checkEquals(HealthTracker.Change.NONE, tracker.sample(0.0F), "staying dead is no second death");
		checkEquals(HealthTracker.Change.NONE, tracker.sample(20.0F), "respawning is no damage");
		tracker.reset();
		checkEquals(HealthTracker.Change.NONE, tracker.sample(3.0F), "after a reset the first sample sets the baseline again");

		checkEquals(0.0F, HealthTracker.lostShare(20.0F, 20.0F), "lost share at full health");
		checkEquals(0.2F, HealthTracker.lostShare(16.0F, 20.0F), "lost share after losing a fifth");
		checkEquals(1.0F, HealthTracker.lostShare(-3.0F, 20.0F), "lost share when dead");
	}

	/** Collects the commands a controller sends, tick by tick. */
	private static final class Rules {
		private final DgLabConfig config;
		private final List<String> sent = new ArrayList<>();
		private final StrengthController controller;
		private boolean twoPlayer = false;
		private float localLostHealth = 0.0F;
		private float secondLostHealth = 0.0F;

		private Rules(final Path path) {
			this.config = DgLabConfig.load(path);
			this.controller = new StrengthController(this.config, this.sent::add);
		}

		/**
		 * Binds an app with the given limit on both channels, then lets it report the given strength, as if the user
		 * had raised it in the app.
		 */
		private void bind(final int strength, final int limit) {
			this.controller.onBound();
			this.controller.onReport(new StrengthReport(0, 0, limit, limit));
			check(this.tick().isEmpty(), "a tick with nothing to do must send nothing");
			this.controller.onReport(new StrengthReport(strength, strength, limit, limit));
		}

		/** Runs one tick in a world and returns what it sent. */
		private List<String> tick() {
			return this.tick(true);
		}

		private List<String> tick(final boolean inWorld) {
			this.sent.clear();
			this.controller.tick(inWorld, this.twoPlayer, this.localLostHealth, this.secondLostHealth);
			return List.copyOf(this.sent);
		}

		/** Runs ticks and returns what each one sent. */
		private List<List<String>> ticks(final int count) {
			List<List<String>> timeline = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				timeline.add(this.tick());
			}
			return timeline;
		}

		/** Returns what an event sent. */
		private List<String> during(final Runnable event) {
			this.sent.clear();
			event.run();
			return List.copyOf(this.sent);
		}

		private void set(final ChannelOption option, final double value) {
			for (Channel channel : Channel.values()) {
				this.config.setValue(channel, option, value);
			}
		}
	}

	private static void checkStrengthRules(final Path directory) {
		String damageA = AppCommands.pulse(Channel.A, WaveformPreset.THUMP.waveform().frames());
		String damageB = AppCommands.pulse(Channel.B, WaveformPreset.THUMP.waveform().frames());
		String recoveryA = AppCommands.pulse(Channel.A, WaveformPreset.SWELL.waveform().frames());
		String recoveryB = AppCommands.pulse(Channel.B, WaveformPreset.SWELL.waveform().frames());
		List<String> silence = List.of("strength-1+2+0", "clear-1", "strength-2+2+0", "clear-2");
		List<String> nothing = List.of();

		// Without an app, nothing is sent at all.
		Rules unbound = new Rules(directory.resolve("unbound.json"));
		check(unbound.tick().isEmpty(), "an unbound controller must send nothing");
		check(unbound.during(() -> unbound.controller.onDamage(Source.LOCAL, 4.0F)).isEmpty(), "damage without an app must send nothing");

		// Damage, hold, decay, and the two waveforms, with the default per-damage strength of 3.
		Rules rules = new Rules(directory.resolve("rules.json"));
		rules.set(ChannelOption.HOLD_TICKS, 4);
		rules.set(ChannelOption.DECAY_INTERVAL, 2);
		rules.set(ChannelOption.DECAY_STEP, 5);
		rules.bind(0, 100);
		checkEquals(List.of("strength-1+1+12", "strength-2+1+12"), rules.during(() -> rules.controller.onDamage(Source.LOCAL, 4.0F)), "4 points of damage");
		checkEquals(12, rules.controller.strength(Channel.A), "strength after damage");
		checkEquals(List.of(
				List.of("clear-1", "clear-2"),
				List.of(damageA, damageB),
				nothing,
				// The hold period of 4 ticks is over: the recovery waveform replaces the damage waveform.
				List.of("clear-1", "clear-2"),
				List.of(recoveryA, recoveryB),
				// Decay starts one interval after the hold period and removes 5 every 2 ticks.
				List.of("strength-1+0+5", "strength-2+0+5"),
				nothing,
				List.of("strength-1+0+5", "strength-2+0+5"),
				nothing,
				// 2 is less than a step above zero, so the strength is set to zero and the queue emptied.
				List.of("strength-1+2+0", "clear-1", "strength-2+2+0", "clear-2"),
				nothing,
				nothing), rules.ticks(12), "timeline after damage");
		checkEquals(0, rules.controller.strength(Channel.A), "strength after the decay");

		// More damage during the hold period restarts it instead of extending it.
		rules.during(() -> rules.controller.onDamage(Source.LOCAL, 1.0F));
		rules.ticks(3);
		rules.during(() -> rules.controller.onDamage(Source.LOCAL, 1.0F));
		checkEquals(List.of(nothing, nothing, nothing, List.of("clear-1", "clear-2")), rules.ticks(4), "hold period restarted by more damage");
		rules.ticks(20);

		// A waveform shorter than the hold period is sent again when it ends: 1 frame lasts 2 ticks.
		rules.config.setWaveformPreset(WaveformSlot.A_DAMAGE, WaveformPreset.STEADY);
		rules.set(ChannelOption.HOLD_TICKS, 7);
		rules.during(() -> rules.controller.onDamage(Source.LOCAL, 4.0F));
		String steadyA = AppCommands.pulse(Channel.A, WaveformPreset.STEADY.waveform().frames());
		List<String> sentToA = rules.ticks(7).stream().flatMap(List::stream).filter(command -> command.endsWith("-1") || command.contains("-A:")).toList();
		checkEquals(List.of("clear-1", steadyA, steadyA, steadyA, "clear-1"), sentToA, "a short waveform looping during the hold period");
		rules.config.setWaveformPreset(WaveformSlot.A_DAMAGE, WaveformPreset.THUMP);
		rules.ticks(40);
		checkEquals(0, rules.controller.strength(Channel.A), "strength after the decay");

		// Small damage adds at least 1, a factor of 0 ignores damage, and the result is rounded down.
		rules.set(ChannelOption.STRENGTH_PER_DAMAGE, 0.1);
		checkEquals(List.of("strength-1+1+1", "strength-2+1+1"), rules.during(() -> rules.controller.onDamage(Source.LOCAL, 1.0F)), "small damage");
		rules.set(ChannelOption.STRENGTH_PER_DAMAGE, 0.0);
		checkEquals(nothing, rules.during(() -> rules.controller.onDamage(Source.LOCAL, 10.0F)), "damage with a factor of 0");
		rules.set(ChannelOption.STRENGTH_PER_DAMAGE, 1.5);
		checkEquals(List.of("strength-1+1+4", "strength-2+1+4"), rules.during(() -> rules.controller.onDamage(Source.LOCAL, 3.0F)), "damage rounded down");
		rules.set(ChannelOption.STRENGTH_PER_DAMAGE, ChannelOption.STRENGTH_PER_DAMAGE.defaultValue());
		rules.ticks(40);

		// The mod's own view of the strength never exceeds the app's limit.
		Rules limited = new Rules(directory.resolve("limited.json"));
		limited.bind(0, 30);
		checkEquals(List.of("strength-1+1+60", "strength-2+1+60"), limited.during(() -> limited.controller.onDamage(Source.LOCAL, 20.0F)), "damage beyond the limit");
		checkEquals(30, limited.controller.strength(Channel.A), "strength capped at the app's limit");
		checkEquals(30, limited.controller.limit(Channel.A), "limit taken from the report");

		// Death adds a fixed amount, up to the limit, and holds it.
		Rules death = new Rules(directory.resolve("death.json"));
		death.set(ChannelOption.DEATH_HOLD_TICKS, 3);
		death.set(ChannelOption.DECAY_INTERVAL, 1);
		death.bind(10, 100);
		checkEquals(List.of("strength-1+2+60", "strength-2+2+60"), death.during(() -> death.controller.onDeath(Source.LOCAL)), "death at strength 10");
		checkEquals(List.of("strength-1+2+100", "strength-2+2+100"), death.during(() -> death.controller.onDeath(Source.LOCAL)), "death near the limit");
		checkEquals(List.of(
				List.of("clear-1", "clear-2"),
				List.of(damageA, damageB),
				List.of("clear-1", "clear-2"),
				List.of("strength-1+0+1", recoveryA, "strength-2+0+1", recoveryB)), death.ticks(4), "timeline after death");

		// The floor scales with lost health: at half health it is half of 40.
		Rules floor = new Rules(directory.resolve("floor.json"));
		floor.set(ChannelOption.DECAY_INTERVAL, 1);
		floor.set(ChannelOption.DECAY_STEP, 5);
		floor.localLostHealth = 0.5F;
		floor.controller.onBound();
		floor.controller.onReport(new StrengthReport(32, 32, 100, 100));
		checkEquals(List.of("strength-1+0+5", "strength-1+0+5", "strength-1+2+20"), strengthCommands(floor.ticks(10), Channel.A), "decay to the floor at half health");
		checkEquals(20, floor.controller.strength(Channel.A), "strength resting on the floor");
		// A share that is not an exact fraction still gives the nearest whole number: a fifth of 40 is 8.
		floor.localLostHealth = 1.0F - 16.0F / 20.0F;
		checkEquals(List.of("strength-1+0+5", "strength-1+0+5", "strength-1+2+8"), strengthCommands(floor.ticks(10), Channel.A), "decay to an inexact floor");
		floor.controller.onReport(new StrengthReport(20, 20, 100, 100));
		floor.localLostHealth = 0.25F;
		checkEquals(List.of("strength-1+0+5", "strength-1+0+5"), strengthCommands(floor.ticks(10), Channel.A), "decay after regaining health");
		floor.config.set(ToggleSetting.HEALTH_FLOOR, false);
		checkEquals(List.of("strength-1+0+5", "strength-1+0+5"), strengthCommands(floor.ticks(10), Channel.A), "decay without a floor");
		checkEquals(0, floor.controller.strength(Channel.A), "strength without a floor");

		// The master switch brings both channels to zero once and makes the mod ignore events.
		Rules master = new Rules(directory.resolve("master.json"));
		master.bind(40, 100);
		master.config.set(ToggleSetting.ENABLED, false);
		checkEquals(silence, master.tick(), "switching the output off");
		checkEquals(nothing, master.tick(), "the tick after switching the output off");
		checkEquals(nothing, master.during(() -> master.controller.onDamage(Source.LOCAL, 4.0F)), "damage while the output is off");
		checkEquals(nothing, master.during(() -> master.controller.onDeath(Source.LOCAL)), "death while the output is off");
		master.config.set(ToggleSetting.ENABLED, true);
		checkEquals(nothing, master.tick(), "switching the output on again");
		checkEquals(List.of("strength-1+1+12", "strength-2+1+12"), master.during(() -> master.controller.onDamage(Source.LOCAL, 4.0F)), "damage after switching the output on");

		// Leaving the world does the same.
		checkEquals(silence, master.tick(false), "leaving the world");
		checkEquals(nothing, master.during(() -> master.controller.onDamage(Source.LOCAL, 4.0F)), "damage outside a world");
		master.tick();

		// A channel that is switched off is brought to zero and no longer reacts.
		master.config.set(ToggleSetting.CHANNEL_B, false);
		checkEquals(List.of("strength-2+2+0", "clear-2"), master.tick(), "switching channel B off");
		checkEquals(List.of("strength-1+1+12"), master.during(() -> master.controller.onDamage(Source.LOCAL, 4.0F)), "damage with channel B off");
		checkEquals(List.of("strength-1+2+62"), master.during(() -> master.controller.onDeath(Source.LOCAL)), "death with channel B off");
		master.config.set(ToggleSetting.CHANNEL_B, true);

		master.config.set(ToggleSetting.DAMAGE_RESPONSE, false);
		checkEquals(nothing, master.during(() -> master.controller.onDamage(Source.LOCAL, 4.0F)), "damage with the damage response off");
		master.config.set(ToggleSetting.DAMAGE_RESPONSE, true);
		master.config.set(ToggleSetting.DEATH_RESPONSE, false);
		checkEquals(nothing, master.during(() -> master.controller.onDeath(Source.LOCAL)), "death with the death response off");
		master.config.set(ToggleSetting.DEATH_RESPONSE, true);

		checkEquals(List.of("clear-1", damageA), master.during(() -> master.controller.preview(Channel.A, WaveformPreset.THUMP.waveform())), "previewing a waveform");
		checkEquals(silence, master.during(master.controller::silenceAll), "silencing both channels before the connection ends");

		master.controller.onUnbound();
		checkEquals(nothing, master.tick(), "a tick after the app disconnected");
		checkEquals(nothing, master.during(() -> master.controller.onDamage(Source.LOCAL, 4.0F)), "damage after the app disconnected");
	}

	private static void checkTwoPlayerRules(final Path directory) {
		Rules rules = new Rules(directory.resolve("two-player.json"));
		rules.set(ChannelOption.DECAY_INTERVAL, 1);
		rules.set(ChannelOption.DECAY_STEP, 5);
		rules.set(ChannelOption.HOLD_TICKS, 0);
		rules.set(ChannelOption.DEATH_HOLD_TICKS, 0);
		rules.twoPlayer = true;
		rules.bind(0, 100);
		check(rules.controller.isTwoPlayerActive(), "two-player mode must be active");

		// Each player drives one channel.
		checkEquals(List.of("strength-1+1+6"), rules.during(() -> rules.controller.onDamage(Source.LOCAL, 2.0F)), "damage to the local player");
		checkEquals(List.of("strength-2+1+9"), rules.during(() -> rules.controller.onDamage(Source.SECOND, 3.0F)), "damage to the second player");
		checkEquals(List.of("strength-2+2+59"), rules.during(() -> rules.controller.onDeath(Source.SECOND)), "death of the second player");
		checkEquals(List.of("strength-1+2+56"), rules.during(() -> rules.controller.onDeath(Source.LOCAL)), "death of the local player");

		// Each channel's floor follows its own player's health.
		rules.localLostHealth = 0.5F;
		rules.secondLostHealth = 1.0F;
		rules.ticks(20);
		checkEquals(20, rules.controller.strength(Channel.A), "floor of channel A from the local player's health");
		checkEquals(40, rules.controller.strength(Channel.B), "floor of channel B from the second player's health");

		// The strength added when the second player leaves is off by default.
		checkEquals(List.of(), rules.during(rules.controller::onSecondPlayerLeft), "the second player leaving, with nothing configured");
		rules.config.setTwoPlayer("Alex", 30);
		checkEquals(List.of("strength-2+2+70"), rules.during(rules.controller::onSecondPlayerLeft), "the second player leaving");

		// Without a second player, the local player drives both channels again.
		rules.twoPlayer = false;
		rules.tick();
		checkEquals(List.of("strength-1+1+6", "strength-2+1+6"), rules.during(() -> rules.controller.onDamage(Source.LOCAL, 2.0F)), "damage after two-player mode ended");
	}

	private static void checkPairingQr() throws FormatException, ChecksumException {
		String url = PairingUrl.of("192.168.100.200", 65535, UUID.randomUUID().toString());
		check(url.startsWith("https://www.dungeon-lab.com/app-download.php#DGLAB-SOCKET#ws://192.168.100.200:65535/"), "unexpected pairing text: " + url);
		checkEquals(2L, url.chars().filter(character -> character == '#').count(), "number of # in the pairing text");
		checkEquals("ws://[fe80::1]:9999/id", lastPart(PairingUrl.of("fe80::1", 9999, "id")), "pairing address for an IPv6 host");

		boolean[][] modules = PairingQr.encode(url);
		String decoded = new Decoder().decode(modules).getText();
		LOGGER.info("Pairing QR code: {} modules across, decoded to {}", modules.length, decoded);
		checkEquals(url, decoded, "text decoded from the pairing QR code");

		check(PairingUrl.isValidHost("192.168.1.2"), "an IPv4 address is a valid host");
		check(PairingUrl.isValidHost("my-pc.example.com"), "a domain name is a valid host");
		check(!PairingUrl.isValidHost("not a host"), "text with spaces is not a valid host");
		check(!PairingUrl.isValidHost(""), "an empty host is not valid");
	}

	private static void checkPairingServer() throws IOException {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		PairingServer server = new PairingServer(Runnable::run, new PairingServer.Listener() {
			@Override
			public void onBound() {
				events.add("bound");
			}

			@Override
			public void onUnbound() {
				events.add("unbound");
			}

			@Override
			public void onStrengthReport(final StrengthReport report) {
				events.add(report.toMessage());
			}
		});
		try {
			int port = freePort();
			checkEquals(PairingServer.State.STOPPED, server.state(), "state before the first start");
			server.start(port);
			await("the server to listen", () -> server.state() == PairingServer.State.LISTENING);
			check(!server.send("clear-1"), "a command without a bound app must not be sent");

			// An app that does not know the terminal ID cannot bind.
			try (FakeDgLabApp stranger = FakeDgLabApp.connect(PairingUrl.of(LOOPBACK, port, "someone-else"))) {
				checkEquals(SocketMessage.CODE_NO_SUCH_TARGET, stranger.requestBind("someone-else"), "bind with a wrong terminal ID");
				checkEquals(PairingServer.State.LISTENING, server.state(), "state after a refused bind");
				stranger.send(new SocketMessage(SocketMessage.TYPE_MSG, server.terminalId(), stranger.awaitAppId(), "strength-9+9+9+9"));
				stranger.await("the error for a message from an unbound app", message -> message.type().equals(SocketMessage.TYPE_ERROR)
						&& message.message().equals(SocketMessage.CODE_NOT_BOUND));
				check(events.isEmpty(), "an unbound app must not cause events, got " + events);
			}

			String pairingUrl = PairingUrl.of(LOOPBACK, port, server.terminalId());
			try (FakeDgLabApp app = FakeDgLabApp.connect(pairingUrl)) {
				app.bind(100, 80);
				await("the bind and the first report", () -> events.equals(List.of("bound", "strength-0+0+100+80")));
				checkEquals(PairingServer.State.BOUND, server.state(), "state after a bind");

				check(server.send("strength-2+1+95"), "a command to the bound app must be sent");
				SocketMessage command = app.await("the strength command", message -> message.message().equals("strength-2+1+95"));
				checkEquals(new SocketMessage(SocketMessage.TYPE_MSG, server.terminalId(), app.awaitAppId(), "strength-2+1+95"), command, "the command as the app received it");
				// The fake app raised channel B to its limit of 80 and reported that, as the real app does.
				await("the report after the command", () -> events.contains("strength-0+80+100+80"));

				check(!server.send("x".repeat(SocketMessage.MAX_LENGTH)), "a command that makes the message too long must be refused");
				app.sendRaw("this is not JSON");
				app.await("the error for malformed text", message -> message.type().equals(SocketMessage.TYPE_ERROR)
						&& message.message().equals(SocketMessage.CODE_NOT_JSON));
			}
			await("the unbind after the app left", () -> events.getLast().equals("unbound"));
			await("the server to listen again", () -> server.state() == PairingServer.State.LISTENING);

			// An app that scans the code while another is bound takes over, and the other's device is left silent.
			String report = "strength-0+0+100+100";
			events.clear();
			try (FakeDgLabApp first = FakeDgLabApp.connect(pairingUrl); FakeDgLabApp second = FakeDgLabApp.connect(pairingUrl)) {
				first.bind(100, 100);
				await("the first app to be bound", () -> events.equals(List.of("bound", report)));
				second.bind(100, 100);
				first.await("the notice that the first app was replaced", message -> message.type().equals(SocketMessage.TYPE_BREAK)
						&& message.message().equals(SocketMessage.CODE_PEER_DISCONNECTED));
				checkEquals(List.of("strength-1+2+0", "clear-1", "strength-2+2+0", "clear-2"), first.commands(), "commands to the replaced app");
				first.awaitClosed();
				await("the takeover", () -> events.equals(List.of("bound", report, "unbound", "bound", report)));
				check(server.send("clear-2"), "a command after a takeover must be sent");
				second.await("the command after the takeover", message -> message.message().equals("clear-2"));
				checkEquals(4, first.commands().size(), "commands the replaced app received in total");

				// A second bind request from the bound app changes nothing.
				checkEquals(SocketMessage.CODE_OK, second.requestBind(server.terminalId()), "a repeated bind request");
				checkEquals(5, events.size(), "events after a repeated bind request");

				// Stopping the server disconnects the app.
				server.stop();
				checkEquals(PairingServer.State.STOPPED, server.state(), "state after stopping");
				second.awaitClosed();
				checkEquals("unbound", events.getLast(), "last event when the server stops");
			}

			// A port that is taken is reported instead of retried.
			try (ServerSocket taken = new ServerSocket(0)) {
				server.start(taken.getLocalPort());
				await("the start on a taken port to fail", () -> server.state() == PairingServer.State.FAILED);
			}
			server.start(port);
			await("the server to listen after a failure", () -> server.state() == PairingServer.State.LISTENING);
			LOGGER.info("Pairing server checks passed on port {}", port);
		} finally {
			server.close();
		}
	}

	/**
	 * The pause menu names the state of a connection server that has no app, unless the server is switched off.
	 */
	private static void checkPauseMenuStatus(final Path directory) throws IOException {
		DgLabConfig config = DgLabConfig.load(directory.resolve("pause-menu.json"));
		DgLabController controller = new DgLabController(config, Runnable::run);
		try (ServerSocket taken = new ServerSocket(0)) {
			checkEquals(List.of(), PauseMenuStatus.lines(config, controller), "pause menu lines with a stopped server");
			controller.server().start(taken.getLocalPort());
			await("the start on a taken port to fail", () -> controller.server().state() == PairingServer.State.FAILED);
			checkEquals(List.of(PauseMenuStatus.SERVER_FAILED), PauseMenuStatus.lines(config, controller), "pause menu lines with a failed server");
			config.set(ToggleSetting.HUD, false);
			checkEquals(List.of(), PauseMenuStatus.lines(config, controller), "pause menu lines with the HUD off");
		} finally {
			controller.close();
		}
	}

	/** The strength commands for one channel, in order. */
	private static List<String> strengthCommands(final List<List<String>> timeline, final Channel channel) {
		return timeline.stream().flatMap(List::stream).filter(command -> command.startsWith("strength-" + channel.number() + "+")).toList();
	}

	/** The part of the pairing text after the last {@code #}: the WebSocket address. */
	private static String lastPart(final String pairingUrl) {
		return pairingUrl.substring(pairingUrl.lastIndexOf('#') + 1);
	}

	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static void await(final String description, final BooleanSupplier condition) {
		long deadline = System.nanoTime() + TIMEOUT_NANOS;
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("timed out waiting for " + description);
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("interrupted while waiting for " + description, e);
			}
		}
	}
}
