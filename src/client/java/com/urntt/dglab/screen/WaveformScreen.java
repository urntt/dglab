package com.urntt.dglab.screen;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.feedback.StrengthController;
import com.urntt.dglab.waveform.WaveformPreset;
import com.urntt.dglab.waveform.WaveformSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Chooses the waveform of each slot: a preset or the slot's custom waveform, which the editor changes.
 */
public final class WaveformScreen extends DgLabOptionsScreen {
	private static final Component TITLE = Component.translatable("options.dglab.waveforms.title");
	private static final Component PRESET = Component.translatable("options.dglab.waveform.preset");
	private static final Component CUSTOM = Component.translatable("options.dglab.waveform.preset.custom");
	private static final Component TEST = Component.translatable("options.dglab.waveform.test");
	private static final Component EDIT = Component.translatable("options.dglab.waveform.edit");

	public WaveformScreen(final Screen parent) {
		super(parent, TITLE);
	}

	/**
	 * Makes every slot play its default preset again. The slots' custom waveforms are kept; the editor resets those.
	 */
	@Override
	protected void resetPage() {
		DgLabConfig config = DgLabClient.config();
		for (WaveformSlot slot : WaveformSlot.values()) {
			config.setWaveformPreset(slot, slot.defaultPreset());
		}
	}

	/** Also used by the editor to return here, so that this page shows the slot's new choice. */
	@Override
	protected Screen recreate() {
		return new WaveformScreen(this.lastScreen);
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		DgLabConfig config = DgLabClient.config();
		StrengthController strength = DgLabClient.controller().strength();

		// An empty value stands for the slot's custom waveform.
		List<Optional<WaveformPreset>> choices = new ArrayList<>();
		for (WaveformPreset preset : WaveformPreset.values()) {
			choices.add(Optional.of(preset));
		}
		choices.add(Optional.empty());

		for (WaveformSlot slot : WaveformSlot.values()) {
			this.list.addHeader(slot.label());
			CycleButton<Optional<WaveformPreset>> presetButton = CycleButton
					.<Optional<WaveformPreset>>builder(choice -> choice.map(WaveformPreset::label).orElse(CUSTOM), config.waveformPreset(slot))
					.withValues(choices)
					.withTooltip(choice -> Tooltip.create(Component.translatable("options.dglab.waveform.preset.tooltip")))
					.create(PRESET, (button, choice) -> choice.ifPresentOrElse(
							preset -> config.setWaveformPreset(slot, preset), () -> config.useCustomWaveform(slot)));
			Button testButton = Button.builder(TEST, button -> strength.preview(slot.channel(), config.waveform(slot)))
					.tooltip(Tooltip.create(Component.translatable("options.dglab.waveform.test.tooltip")))
					.build();
			testButton.active = strength.isBound();
			this.list.addSmall(presetButton, testButton);
			this.list.addBig(Button.builder(EDIT, button -> this.minecraft.gui.setScreen(new WaveformEditorScreen(this, slot)))
					.tooltip(Tooltip.create(Component.translatable("options.dglab.waveform.edit.tooltip")))
					.build());
		}
	}
}
