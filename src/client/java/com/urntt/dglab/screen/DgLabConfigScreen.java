package com.urntt.dglab.screen;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The mod's main configuration screen, built from vanilla widgets. Every change is saved immediately.
 */
public final class DgLabConfigScreen extends DgLabOptionsScreen {
	private static final Component TITLE = Component.translatable("options.dglab.title");

	public DgLabConfigScreen(final @Nullable Screen parent) {
		super(parent, TITLE);
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		DgLabConfig config = DgLabClient.config();

		this.list.addHeader(Component.translatable("options.dglab.section.connection"));
		this.list.addBig(this.screenButton("options.dglab.pair", () -> new PairingScreen(this)));
		this.list.addSmall(this.toggle(ToggleSetting.SERVER_ENABLED),
				this.screenButton("options.dglab.connection", () -> new ConnectionScreen(this, config)));

		this.list.addHeader(Component.translatable("options.dglab.section.output"));
		this.list.addBig(toggleOption(ToggleSetting.ENABLED));
		this.addPercentSlider("options.dglab.output_multiplier", DgLabConfig.MAX_OUTPUT_MULTIPLIER, config.outputMultiplier(),
				DgLabConfig.DEFAULT_OUTPUT_MULTIPLIER, config::setOutputMultiplier);
		this.list.addSmall(toggleOption(ToggleSetting.CHANNEL_A), toggleOption(ToggleSetting.CHANNEL_B));
		this.list.addSmall(toggleOption(ToggleSetting.DAMAGE_RESPONSE), toggleOption(ToggleSetting.DEATH_RESPONSE));
		this.list.addSmall(this.toggle(ToggleSetting.HEALTH_FLOOR),
				this.screenButton("options.dglab.strength", () -> new StrengthScreen(this)));
		this.list.addBig(this.screenButton("options.dglab.waveforms", () -> new WaveformScreen(this)));

		this.list.addHeader(Component.translatable("options.dglab.section.hud"));
		this.list.addSmall(toggleOption(ToggleSetting.HUD), toggleOption(ToggleSetting.HUD_LIMIT));
		this.addPercentSlider("options.dglab.hud_x", DgLabConfig.MAX_HUD_PERCENT, config.hudX(), DgLabConfig.DEFAULT_HUD_PERCENT,
				config::setHudX);
		this.addPercentSlider("options.dglab.hud_y", DgLabConfig.MAX_HUD_PERCENT, config.hudY(), DgLabConfig.DEFAULT_HUD_PERCENT,
				config::setHudY);

		this.list.addHeader(Component.translatable("options.dglab.section.two_player"));
		this.list.addSmall(this.toggle(ToggleSetting.TWO_PLAYER),
				this.screenButton("options.dglab.two_player_settings", () -> new TwoPlayerScreen(this, config)));
	}

	/**
	 * Resets the settings this page shows: the switches, the output multiplier, and the HUD's position. The pages
	 * it leads to reset their own settings.
	 */
	@Override
	protected void resetPage() {
		DgLabConfig config = DgLabClient.config();
		for (ToggleSetting setting : ToggleSetting.values()) {
			config.set(setting, setting.defaultValue());
		}
		config.setOutputMultiplier(DgLabConfig.DEFAULT_OUTPUT_MULTIPLIER);
		config.setHudX(DgLabConfig.DEFAULT_HUD_PERCENT);
		config.setHudY(DgLabConfig.DEFAULT_HUD_PERCENT);
	}

	@Override
	protected Screen recreate() {
		return new DgLabConfigScreen(this.lastScreen);
	}

	/**
	 * Creates the on/off option of a setting. Its tooltip is the translation of the setting's tooltip key.
	 */
	static OptionInstance<Boolean> toggleOption(final ToggleSetting setting) {
		DgLabConfig config = DgLabClient.config();
		return OptionInstance.createBoolean(
				setting.captionKey(),
				OptionInstance.cachedConstantTooltip(Component.translatable(setting.tooltipKey())),
				config.get(setting),
				value -> config.set(setting, value));
	}

	private AbstractWidget toggle(final ToggleSetting setting) {
		return toggleOption(setting).createButton(this.options);
	}

	/**
	 * Creates a button that opens another screen. Its tooltip is the translation of {@code captionKey + ".tooltip"}.
	 */
	private Button screenButton(final String captionKey, final Supplier<Screen> screen) {
		return Button.builder(Component.translatable(captionKey), button -> this.minecraft.gui.setScreen(screen.get()))
				.tooltip(Tooltip.create(Component.translatable(captionKey + ".tooltip")))
				.build();
	}

	/**
	 * Adds a slider from 0% to {@code max}%. Its tooltip is the translation of {@code captionKey + ".tooltip"}.
	 */
	private void addPercentSlider(final String captionKey, final int max, final int value, final int defaultValue,
			final IntConsumer onChange) {
		this.addSlider(captionKey, Component.translatable(captionKey + ".tooltip"), DgLabOptionsScreen::percent, 0, max, value,
				defaultValue, onChange);
	}
}
