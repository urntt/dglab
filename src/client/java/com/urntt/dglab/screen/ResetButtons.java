package com.urntt.dglab.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Creates the buttons that reset settings to their defaults, the way vanilla's key binds screen has them: one next
 * to each setting and one for the whole page.
 */
final class ResetButtons {
	/** Width of the button next to a setting, as on vanilla's key binds screen. */
	static final int SETTING_BUTTON_WIDTH = 50;

	/** Vanilla's label of the button that resets one key binding. */
	private static final Component RESET = Component.translatable("controls.reset");
	private static final Component RESET_PAGE = Component.translatable("options.dglab.reset_page");
	private static final String RESET_TOOLTIP_KEY = "options.dglab.reset.tooltip";
	private static final String RESET_NARRATION_KEY = "narrator.controls.reset";
	private static final String RESET_PAGE_TOOLTIP_KEY = "options.dglab.reset_page.tooltip";
	private static final String RESET_PAGE_UNSAVED_TOOLTIP_KEY = "options.dglab.reset_page.tooltip.unsaved";

	private ResetButtons() {
	}

	/**
	 * Creates the button that resets one setting.
	 *
	 * @param name the setting's name, for narration
	 * @param defaultValue the default as the setting shows it, for the tooltip
	 */
	static Button forSetting(final Component name, final Component defaultValue, final Runnable action) {
		return Button.builder(RESET, button -> action.run())
				.width(SETTING_BUTTON_WIDTH)
				.tooltip(Tooltip.create(Component.translatable(RESET_TOOLTIP_KEY, defaultValue)))
				.createNarration(defaultNarration -> Component.translatable(RESET_NARRATION_KEY, name))
				.build();
	}

	/**
	 * Starts the button that resets every setting on a page.
	 *
	 * @param savedImmediately whether the reset is saved right away, or only when the page's "Done" is pressed
	 */
	static Button.Builder forPage(final Runnable action, final boolean savedImmediately) {
		return Button.builder(RESET_PAGE, button -> action.run())
				.tooltip(Tooltip.create(Component.translatable(savedImmediately ? RESET_PAGE_TOOLTIP_KEY : RESET_PAGE_UNSAVED_TOOLTIP_KEY)));
	}
}
