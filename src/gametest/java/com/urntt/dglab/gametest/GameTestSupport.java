package com.urntt.dglab.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.config.ChannelOption;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.server.PairingServer;
import com.urntt.dglab.server.PairingUrl;
import com.urntt.dglab.waveform.WaveformSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for the client game tests.
 */
@SuppressWarnings("UnstableApiUsage")
final class GameTestSupport {
	static final Logger LOGGER = LoggerFactory.getLogger("dglab-gametest");

	/** The address of this computer the fake app connects to. */
	static final String LOOPBACK = "127.0.0.1";
	/** The limits the fake app reports unless a test needs others. */
	static final int APP_LIMIT = 100;
	/** A key no vanilla key mapping uses, for pressing the mod's own mappings. */
	static final String TEST_KEY = "key.keyboard.j";
	/** Ticks to wait for something that must not happen. */
	static final int QUIET_TICKS = 20;
	/** Ticks after which an expected command counts as missing. */
	private static final int COMMAND_TIMEOUT_TICKS = 100;
	/** Ticks without a new command after which a burst of commands counts as complete. */
	private static final int SETTLE_TICKS = 4;
	private static final int RELOAD_TIMEOUT_TICKS = 1200;

	private GameTestSupport() {
	}

	/**
	 * Waits until the connection server listens, then connects and binds a fake app with the default limits.
	 */
	static FakeDgLabApp connectApp(final ClientGameTestContext context) {
		context.waitFor(client -> {
			PairingServer.State state = DgLabClient.controller().server().state();
			return state == PairingServer.State.LISTENING;
		});
		String url = context.computeOnClient(client -> PairingUrl.of(LOOPBACK,
				DgLabClient.controller().server().port(), DgLabClient.controller().server().terminalId()));
		FakeDgLabApp app = FakeDgLabApp.connect(url);
		app.bind(APP_LIMIT, APP_LIMIT);
		context.waitFor(client -> DgLabClient.controller().strength().isBound()
				&& DgLabClient.controller().strength().limit(Channel.A) == APP_LIMIT);
		return app;
	}

	/**
	 * Waits until the fake app has received commands beyond the first {@code skip}, and returns them once no further
	 * command arrived for a few ticks.
	 *
	 * @param expectation what the commands are expected for, used if none arrives
	 */
	static List<String> awaitCommands(final ClientGameTestContext context, final FakeDgLabApp app, final int skip,
			final String expectation) {
		for (int tick = 0; app.commands().size() <= skip; tick++) {
			check(tick < COMMAND_TIMEOUT_TICKS, "expected commands " + expectation + ", but the app received none");
			context.waitTick();
		}
		int seen;
		do {
			seen = app.commands().size();
			context.waitTicks(SETTLE_TICKS);
		} while (app.commands().size() != seen);
		return app.commandsAfter(skip);
	}

	/**
	 * Checks that the fake app receives no command for a while.
	 */
	static void checkNoCommands(final ClientGameTestContext context, final FakeDgLabApp app, final String situation) {
		int skip = app.commands().size();
		context.waitTicks(QUIET_TICKS);
		checkEquals(List.of(), app.commandsAfter(skip), "commands " + situation);
	}

	/**
	 * Restores every setting a test may have changed, so each test class starts from the defaults.
	 */
	static void resetConfig(final ClientGameTestContext context) {
		configure(context, config -> {
			for (ToggleSetting setting : ToggleSetting.values()) {
				config.set(setting, setting.defaultValue());
			}
			for (Channel channel : Channel.values()) {
				for (ChannelOption option : ChannelOption.values()) {
					config.setValue(channel, option, option.defaultValue());
				}
			}
			for (WaveformSlot slot : WaveformSlot.values()) {
				config.setWaveformPreset(slot, slot.defaultPreset());
			}
			config.setTwoPlayer("", 0);
			config.setOutputMultiplier(DgLabConfig.DEFAULT_OUTPUT_MULTIPLIER);
			config.setHudX(DgLabConfig.DEFAULT_HUD_PERCENT);
			config.setHudY(DgLabConfig.DEFAULT_HUD_PERCENT);
		});
	}

	/**
	 * Switches the game's language and waits for the resource reload to finish.
	 */
	static void setLanguage(final ClientGameTestContext context, final String code) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			client.getLanguageManager().setSelected(code);
			client.options.languageCode = code;
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, RELOAD_TIMEOUT_TICKS);
	}

	/**
	 * Makes the test world predictable: survival mode, no regeneration that would change the health under the test,
	 * and no death screen to click through.
	 */
	static void prepareWorld(final ClientGameTestContext context, final TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("gamerule natural_health_regeneration false");
		server.runCommand("gamerule immediate_respawn true");
		context.waitFor(client -> client.player != null && client.player.onGround());
	}

	/**
	 * Returns the mod's key mapping registered under {@code name}, bound to {@link #TEST_KEY} for the test.
	 */
	static KeyMapping bindKey(final ClientGameTestContext context, final String name) {
		KeyMapping mapping = Objects.requireNonNull(KeyMapping.get(name), name);
		context.runOnClient(client -> {
			mapping.setKey(InputConstants.getKey(TEST_KEY));
			KeyMapping.resetMapping();
		});
		return mapping;
	}

	static void unbindKey(final ClientGameTestContext context, final KeyMapping mapping) {
		context.runOnClient(client -> {
			mapping.setKey(mapping.getDefaultKey());
			KeyMapping.resetMapping();
		});
	}

	/**
	 * Presses the first active button labeled with the translation of {@code translationKey} on the current screen.
	 * Unlike the test API's own method, it also finds buttons inside lists.
	 */
	static void clickButton(final ClientGameTestContext context, final String translationKey) {
		context.runOnClient(client -> {
			List<AbstractButton> buttons = findButtons(client.gui.screen(), translationKey);
			check(!buttons.isEmpty(), "no button labeled \"" + label(translationKey) + "\" on " + client.gui.screen());
			AbstractButton button = buttons.stream().filter(candidate -> candidate.active).findFirst().orElse(null);
			check(button != null, "no button labeled \"" + label(translationKey) + "\" is active");
			button.onPress(new MouseButtonInfo(0, 0));
		});
	}

	/**
	 * Counts the active buttons labeled with the translation of {@code translationKey} on the current screen.
	 */
	static int countActiveButtons(final ClientGameTestContext context, final String translationKey) {
		return context.computeOnClient(client -> (int) findButtons(client.gui.screen(), translationKey).stream()
				.filter(button -> button.active).count());
	}

	private static String label(final String translationKey) {
		return Component.translatable(translationKey).getString();
	}

	private static List<AbstractButton> findButtons(final ContainerEventHandler container, final String translationKey) {
		List<AbstractButton> buttons = new ArrayList<>();
		if (container != null) {
			collectButtons(container, label(translationKey), buttons);
		}
		return buttons;
	}

	private static void collectButtons(final ContainerEventHandler container, final String label, final List<AbstractButton> buttons) {
		for (GuiEventListener child : container.children()) {
			if (child instanceof AbstractButton button && button.getMessage().getString().equals(label)) {
				buttons.add(button);
			}
			if (child instanceof ContainerEventHandler nested) {
				collectButtons(nested, label, buttons);
			}
		}
	}

	/**
	 * Changes the mod's configuration on the client thread.
	 */
	static void configure(final ClientGameTestContext context, final Consumer<DgLabConfig> change) {
		context.runOnClient(client -> change.accept(DgLabClient.config()));
	}

	static boolean get(final ClientGameTestContext context, final ToggleSetting setting) {
		return context.computeOnClient(client -> DgLabClient.config().get(setting));
	}

	static int strength(final ClientGameTestContext context, final Channel channel) {
		return context.computeOnClient(client -> DgLabClient.controller().strength().strength(channel));
	}

	static DgLabConfig loadSavedConfig() {
		return DgLabConfig.load(DgLabConfig.defaultPath());
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	static void checkEquals(final Object expected, final Object actual, final String what) {
		if (!Objects.equals(expected, actual)) {
			throw new AssertionError(what + ": expected " + expected + ", got " + actual);
		}
	}
}
