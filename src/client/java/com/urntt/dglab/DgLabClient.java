package com.urntt.dglab;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.dglab.command.DgLabCommand;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.hud.PauseMenuStatus;
import com.urntt.dglab.hud.StrengthHud;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.screen.DgLabConfigScreen;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;

public final class DgLabClient implements ClientModInitializer {
	public static final String MOD_ID = "dglab";
	public static final String OPEN_SETTINGS_KEY_NAME = "key.dglab.open_settings";

	private static DgLabConfig config;
	private static DgLabController controller;

	@Override
	public void onInitializeClient() {
		Minecraft client = Minecraft.getInstance();
		config = DgLabConfig.load(DgLabConfig.defaultPath());
		// schedule() always queues, so the controller sees the server's events in the order they happened.
		controller = new DgLabController(config, client::schedule);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		KeyMapping openSettingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(OPEN_SETTINGS_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));
		Map<ToggleSetting, KeyMapping> toggleKeys = new EnumMap<>(ToggleSetting.class);
		for (ToggleSetting setting : ToggleSetting.values()) {
			toggleKeys.put(setting, KeyMappingHelper.registerKeyMapping(
					new KeyMapping(setting.keyName(), InputConstants.UNKNOWN.getValue(), category)));
		}

		ClientTickEvents.END_CLIENT_TICK.register(tickingClient -> {
			while (openSettingsKey.consumeClick()) {
				tickingClient.gui.setScreen(new DgLabConfigScreen(tickingClient.gui.screen()));
			}
			toggleKeys.forEach((setting, key) -> {
				while (key.consumeClick()) {
					toggle(tickingClient, setting);
				}
			});
			controller.tick(tickingClient);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(stoppingClient -> controller.close());

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "strength"), new StrengthHud());
		PauseMenuStatus.register();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> DgLabCommand.register(dispatcher));
	}

	/** Translation key of a channel's name. */
	public static String channelNameKey(final Channel channel) {
		return "options.dglab.channel." + channel.id();
	}

	public static DgLabConfig config() {
		return config;
	}

	public static DgLabController controller() {
		return controller;
	}

	/**
	 * Flips a setting, saves it, and shows the new state on the action bar.
	 */
	public static void toggle(final Minecraft client, final ToggleSetting setting) {
		boolean value = config.toggle(setting);
		if (client.player != null) {
			client.player.sendOverlayMessage(CommonComponents.optionStatus(setting.caption(), value));
		}
	}
}
