package com.urntt.dglab.hud;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.DgLabController;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.feedback.StrengthController;
import com.urntt.dglab.protocol.Channel;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shows both channels' strength, or the state of the connection while no app is bound.
 */
public final class StrengthHud implements HudElement {
	public static final Component NOT_CONNECTED = Component.translatable("hud.dglab.not_connected");
	public static final Component SERVER_FAILED = Component.translatable("hud.dglab.server_failed");

	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int WARNING_COLOR = 0xFFFF5555;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		DgLabConfig config = DgLabClient.config();
		Minecraft client = Minecraft.getInstance();
		if (!config.get(ToggleSetting.HUD) || client.player == null) {
			return;
		}

		DgLabController controller = DgLabClient.controller();
		List<Component> lines = lines(config, controller, client.getUser().getName());
		if (lines.isEmpty()) {
			return;
		}
		int color = controller.strength().isBound() ? TEXT_COLOR : WARNING_COLOR;

		Font font = client.font;
		int width = lines.stream().mapToInt(font::width).max().orElse(0);
		int height = lines.size() * font.lineHeight;
		// The position is the share of the free space to the left of and above the text, so 100% touches the far edge.
		int x = (graphics.guiWidth() - width) * config.hudX() / DgLabConfig.MAX_HUD_PERCENT;
		int y = (graphics.guiHeight() - height) * config.hudY() / DgLabConfig.MAX_HUD_PERCENT;
		for (Component line : lines) {
			graphics.text(font, line, x, y, color);
			y += font.lineHeight;
		}
	}

	/**
	 * Returns the lines the HUD shows right now. Nothing is shown while the connection server is switched off.
	 *
	 * @param localPlayerName labels channel A in two-player mode
	 */
	public static List<Component> lines(final DgLabConfig config, final DgLabController controller, final String localPlayerName) {
		StrengthController strength = controller.strength();
		if (!strength.isBound()) {
			return switch (controller.server().state()) {
				case STOPPED -> List.of();
				case FAILED -> List.of(SERVER_FAILED);
				default -> List.of(NOT_CONNECTED);
			};
		}

		List<Component> lines = new ArrayList<>();
		for (Channel channel : Channel.values()) {
			// In two-player mode each channel belongs to a player, so it is labeled with that player's name.
			Component name = controller.secondPlayerName().isEmpty()
					? Component.translatable(DgLabClient.channelNameKey(channel))
					: Component.literal(channel == Channel.A ? localPlayerName : controller.secondPlayerName());
			lines.add(config.get(ToggleSetting.HUD_LIMIT)
					? Component.translatable("hud.dglab.strength_with_limit", name, strength.strength(channel), strength.limit(channel))
					: Component.translatable("hud.dglab.strength", name, strength.strength(channel)));
		}
		return lines;
	}
}
