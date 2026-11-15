package com.urntt.dglab.screen;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Base of the settings screens whose changes are saved immediately. It adds what vanilla's options screens lack: a
 * button in the footer that resets the page, and sliders that come with their own reset button.
 */
abstract class DgLabOptionsScreen extends OptionsSubScreen {
	private static final int FOOTER_SPACING = 8;

	DgLabOptionsScreen(final @Nullable Screen parent, final Component title) {
		super(parent, Minecraft.getInstance().options, title);
	}

	/**
	 * Restores every setting on this page to its default.
	 */
	protected abstract void resetPage();

	/**
	 * Returns a new screen of this kind with the same parent. It replaces this one whenever settings changed behind
	 * the widgets' backs, because a screen keeps the widgets it was built with.
	 */
	protected abstract Screen recreate();

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(FOOTER_SPACING));
		footer.addChild(ResetButtons.forPage(() -> {
			this.resetPage();
			this.minecraft.gui.setScreen(this.recreate());
		}, true).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
	}

	/**
	 * Adds a row with a slider over whole numbers and the button that resets it to its default.
	 *
	 * @param valueText turns a value into the text shown after the caption
	 * @param onChange saves a value the player chose
	 */
	protected void addSlider(final String captionKey, final Component tooltip, final IntFunction<Component> valueText,
			final int min, final int max, final int value, final int defaultValue, final IntConsumer onChange) {
		if (this.list == null) {
			return;
		}
		Button[] resetButton = new Button[1];
		OptionInstance<Integer> option = new OptionInstance<>(
				captionKey,
				OptionInstance.cachedConstantTooltip(tooltip),
				(caption, current) -> Options.genericValueLabel(caption, valueText.apply(current)),
				new OptionInstance.IntRange(min, max),
				value,
				current -> {
					onChange.accept(current);
					resetButton[0].active = current != defaultValue;
				});
		resetButton[0] = ResetButtons.forSetting(Component.translatable(captionKey), valueText.apply(defaultValue), () -> {
			// Setting the option saves the default; the list then moves the slider to it.
			option.set(defaultValue);
			if (this.list != null) {
				this.list.resetOption(option);
			}
		});
		resetButton[0].active = value != defaultValue;
		this.list.addSmall(option.createButton(this.options), option, resetButton[0]);
	}

	/** Shows a whole number as a percentage. */
	protected static Component percent(final int value) {
		return Component.translatable("options.dglab.value.percent", value);
	}
}
