package com.urntt.dglab.hud;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.DgLabController;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tells the player on the pause menu why the HUD shows no strength: no app is connected, or the connection server
 * could not start. It is drawn at the HUD's position and follows the HUD's on/off setting.
 */
public final class PauseMenuStatus {
	public static final Component NOT_CONNECTED = Component.translatable("hud.dglab.not_connected");
	public static final Component SERVER_FAILED = Component.translatable("hud.dglab.server_failed");

	private static final int WARNING_COLOR = 0xFFFF5555;

	private PauseMenuStatus() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (isPauseMenu(screen)) {
				ScreenEvents.afterExtract(screen).register((pauseMenu, graphics, mouseX, mouseY, a) -> StrengthHud.draw(
						graphics, Screens.getFont(pauseMenu), lines(DgLabClient.config(), DgLabClient.controller()), WARNING_COLOR,
						DgLabClient.config()));
			}
		});
	}

	/**
	 * Returns whether {@code screen} is the pause menu, as opposed to the bare "game paused" screen without buttons.
	 */
	public static boolean isPauseMenu(final Screen screen) {
		return screen instanceof PauseScreen pauseScreen && pauseScreen.showsPauseMenu();
	}

	/**
	 * Returns the lines the pause menu shows right now. Nothing is shown while an app is bound or the connection
	 * server is switched off.
	 */
	public static List<Component> lines(final DgLabConfig config, final DgLabController controller) {
		if (!config.get(ToggleSetting.HUD) || controller.strength().isBound()) {
			return List.of();
		}
		return switch (controller.server().state()) {
			case STOPPED -> List.of();
			case FAILED -> List.of(SERVER_FAILED);
			default -> List.of(NOT_CONNECTED);
		};
	}
}
