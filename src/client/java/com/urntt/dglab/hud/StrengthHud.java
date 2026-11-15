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
 * Shows both channels' strength while an app is bound. Without an app the HUD stays empty; the state of the
 * connection is shown on the pause menu instead, by {@link PauseMenuStatus}.
 */
public final class StrengthHud implements HudElement {
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		DgLabConfig config = DgLabClient.config();
		Minecraft client = Minecraft.getInstance();
		if (!config.get(ToggleSetting.HUD) || client.player == null) {
			return;
		}
		draw(graphics, client.font, lines(config, DgLabClient.controller(), client.getUser().getName()), TEXT_COLOR, config);
	}

	/**
	 * Returns the lines the HUD shows right now: one per channel while an app is bound, none otherwise.
	 *
	 * @param localPlayerName labels channel A in two-player mode
	 */
	public static List<Component> lines(final DgLabConfig config, final DgLabController controller, final String localPlayerName) {
		StrengthController strength = controller.strength();
		if (!strength.isBound()) {
			return List.of();
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

	/**
	 * Draws lines of text at the configured HUD position. The position is the share of the free space to the left
	 * of and above the text, so 100% touches the far edge.
	 */
	static void draw(final GuiGraphicsExtractor graphics, final Font font, final List<Component> lines, final int color,
			final DgLabConfig config) {
		int width = lines.stream().mapToInt(font::width).max().orElse(0);
		int height = lines.size() * font.lineHeight;
		int x = (graphics.guiWidth() - width) * config.hudX() / DgLabConfig.MAX_HUD_PERCENT;
		int y = (graphics.guiHeight() - height) * config.hudY() / DgLabConfig.MAX_HUD_PERCENT;
		for (Component line : lines) {
			graphics.text(font, line, x, y, color);
			y += font.lineHeight;
		}
	}
}
