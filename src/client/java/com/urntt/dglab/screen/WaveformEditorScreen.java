package com.urntt.dglab.screen;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.waveform.Waveform;
import com.urntt.dglab.waveform.WaveformSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Edits a slot's custom waveform sample by sample. A sample is either set by hand or interpolated between the
 * nearest hand-set samples before and after it; the first and the last sample are always set by hand. "Done" saves
 * the waveform and makes the slot use it, "Cancel" or Escape discards the changes, and "Reset Page" loads the slot's
 * default preset.
 */
public final class WaveformEditorScreen extends Screen {
	private static final Component INTENSITY = Component.translatable("options.dglab.waveform.editor.intensity");
	private static final Component FREQUENCY = Component.translatable("options.dglab.waveform.editor.frequency");
	private static final Component MANUAL = Component.translatable("options.dglab.waveform.editor.manual");
	private static final Component AUTOMATIC = Component.translatable("options.dglab.waveform.editor.automatic");
	private static final Component MODE = Component.translatable("options.dglab.waveform.editor.mode");
	private static final Component ADD = Component.translatable("options.dglab.waveform.editor.add");
	private static final Component REMOVE = Component.translatable("options.dglab.waveform.editor.remove");
	private static final Component COPY = Component.translatable("options.dglab.waveform.editor.copy");
	private static final Component PASTE = Component.translatable("options.dglab.waveform.editor.paste");
	private static final Component PASTE_FAILED = Component.translatable("options.dglab.waveform.editor.paste_failed")
			.withStyle(ChatFormatting.RED);
	private static final Component COPIED = Component.translatable("options.dglab.waveform.editor.copied");
	private static final int ROW_WIDTH = 310;
	private static final int ROW_HEIGHT = 24;
	private static final int SPACING = 4;
	private static final int TIME_WIDTH = 44;
	private static final int MODE_WIDTH = 50;
	private static final int SLIDER_WIDTH = (ROW_WIDTH - TIME_WIDTH - MODE_WIDTH - 3 * SPACING) / 2;
	private static final int PREVIEW_HEIGHT = 24;
	/** Four buttons and three gaps fill a row. */
	private static final int EDIT_BUTTON_WIDTH = (ROW_WIDTH - 3 * SPACING) / 4;
	/** Three buttons and two gaps fill a row. */
	private static final int CLOSE_BUTTON_WIDTH = (ROW_WIDTH - 2 * SPACING) / 3;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int PREVIEW_BACKGROUND = 0x80000000;
	private static final int PREVIEW_BAR = 0xFFFFDD33;
	private static final double MILLIS_PER_SECOND = 1000.0;

	/** A sample being edited. */
	private static final class EditableSample {
		private int frequency;
		private int intensity;
		/** Whether the values are set by hand instead of interpolated. */
		private boolean manual;

		private EditableSample(final int frequency, final int intensity, final boolean manual) {
			this.frequency = frequency;
			this.intensity = intensity;
			this.manual = manual;
		}
	}

	private final WaveformScreen parent;
	private final WaveformSlot slot;
	private final DgLabConfig config = DgLabClient.config();
	private final List<EditableSample> samples = new ArrayList<>();
	private final HeaderAndFooterLayout layout;
	private @Nullable SampleList sampleList;
	private @Nullable StringWidget status;
	private @Nullable Button addButton;
	private @Nullable Button removeButton;

	WaveformEditorScreen(final WaveformScreen parent, final WaveformSlot slot) {
		super(Component.translatable("options.dglab.waveform.editor.title", slot.label()));
		this.parent = parent;
		this.slot = slot;
		this.load(this.config.customWaveform(slot));
		this.layout = new HeaderAndFooterLayout(this, 12 + 9 + SPACING + 9 + SPACING + PREVIEW_HEIGHT, 2 * 20 + 3 * SPACING + 4);
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(SPACING));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		this.status = header.addChild(new StringWidget(ROW_WIDTH, this.font.lineHeight, Component.empty(), this.font));
		header.addChild(new Preview());

		this.sampleList = this.layout.addToContents(new SampleList());

		LinearLayout footer = this.layout.addToFooter(LinearLayout.vertical().spacing(SPACING));
		LinearLayout editing = footer.addChild(LinearLayout.horizontal().spacing(SPACING));
		this.addButton = editing.addChild(Button.builder(ADD, button -> this.addFrame()).width(EDIT_BUTTON_WIDTH).build());
		this.removeButton = editing.addChild(Button.builder(REMOVE, button -> this.removeFrame()).width(EDIT_BUTTON_WIDTH).build());
		editing.addChild(Button.builder(COPY, button -> this.copy()).width(EDIT_BUTTON_WIDTH)
				.tooltip(Tooltip.create(Component.translatable("options.dglab.waveform.editor.copy.tooltip"))).build());
		editing.addChild(Button.builder(PASTE, button -> this.paste()).width(EDIT_BUTTON_WIDTH)
				.tooltip(Tooltip.create(Component.translatable("options.dglab.waveform.editor.paste.tooltip"))).build());
		LinearLayout closing = footer.addChild(LinearLayout.horizontal().spacing(SPACING));
		closing.addChild(ResetButtons.forPage(this::loadDefault, false).width(CLOSE_BUTTON_WIDTH).build());
		closing.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.saveAndClose()).width(CLOSE_BUTTON_WIDTH).build());
		closing.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(CLOSE_BUTTON_WIDTH).build());

		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.refresh();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.sampleList != null) {
			this.sampleList.updateSize(this.width, this.layout);
		}
	}

	@Override
	public void onClose() {
		// The parent is recreated so that it shows the slot's new choice and waveform.
		this.minecraft.gui.setScreen(this.parent.recreate());
	}

	/** The waveform as currently edited. */
	Waveform waveform() {
		List<Waveform.Sample> result = new ArrayList<>(this.samples.size());
		for (EditableSample sample : this.samples) {
			result.add(new Waveform.Sample(sample.frequency, sample.intensity));
		}
		return Waveform.ofSamples(result);
	}

	private void saveAndClose() {
		this.config.setCustomWaveform(this.slot, this.waveform());
		this.config.useCustomWaveform(this.slot);
		this.onClose();
	}

	/** Replaces the edited waveform with the slot's default preset. Like every other change here, it is saved by "Done". */
	private void loadDefault() {
		this.load(this.slot.defaultPreset().waveform());
		this.rebuildList();
	}

	private void load(final Waveform waveform) {
		this.samples.clear();
		for (Waveform.Sample sample : waveform.samples()) {
			this.samples.add(new EditableSample(sample.frequency(), sample.intensity(), true));
		}
	}

	/** Appends one frame of interpolated samples that ends in a hand-set copy of the previous last sample. */
	private void addFrame() {
		if (this.samples.size() >= Waveform.MAX_FRAMES * Waveform.SAMPLES_PER_FRAME) {
			return;
		}
		EditableSample last = this.samples.getLast();
		for (int i = 0; i < Waveform.SAMPLES_PER_FRAME; i++) {
			this.samples.add(new EditableSample(last.frequency, last.intensity, i == Waveform.SAMPLES_PER_FRAME - 1));
		}
		this.rebuildList();
	}

	private void removeFrame() {
		if (this.samples.size() <= Waveform.SAMPLES_PER_FRAME) {
			return;
		}
		for (int i = 0; i < Waveform.SAMPLES_PER_FRAME; i++) {
			this.samples.removeLast();
		}
		this.rebuildList();
	}

	private void copy() {
		this.minecraft.keyboardHandler.setClipboard(this.waveform().code());
		this.setStatus(COPIED);
	}

	private void paste() {
		Optional<Waveform> pasted = Waveform.parseCode(this.minecraft.keyboardHandler.getClipboard());
		if (pasted.isEmpty()) {
			this.setStatus(PASTE_FAILED);
			return;
		}
		this.load(pasted.get());
		this.rebuildList();
	}

	private void rebuildList() {
		if (this.sampleList != null) {
			this.sampleList.rebuild();
		}
		this.refresh();
	}

	/**
	 * Recomputes the interpolated samples and brings every widget in line with the samples.
	 */
	private void refresh() {
		this.samples.getFirst().manual = true;
		this.samples.getLast().manual = true;
		int previous = 0;
		for (int index = 1; index < this.samples.size(); index++) {
			if (!this.samples.get(index).manual) {
				continue;
			}
			EditableSample from = this.samples.get(previous);
			EditableSample to = this.samples.get(index);
			for (int between = previous + 1; between < index; between++) {
				double share = (double) (between - previous) / (index - previous);
				EditableSample sample = this.samples.get(between);
				sample.frequency = (int) Math.round(from.frequency + (to.frequency - from.frequency) * share);
				sample.intensity = (int) Math.round(from.intensity + (to.intensity - from.intensity) * share);
			}
			previous = index;
		}

		if (this.sampleList != null) {
			this.sampleList.children().forEach(SampleEntry::refresh);
		}
		int frames = this.samples.size() / Waveform.SAMPLES_PER_FRAME;
		if (this.addButton != null) {
			this.addButton.active = frames < Waveform.MAX_FRAMES;
		}
		if (this.removeButton != null) {
			this.removeButton.active = frames > 1;
		}
		this.setStatus(Component.translatable("options.dglab.waveform.editor.duration", seconds(frames * Waveform.FRAME_MILLIS)));
	}

	private void setStatus(final Component message) {
		if (this.status != null) {
			this.status.setMessage(message);
			this.repositionElements();
		}
	}

	private static Component seconds(final int millis) {
		return Component.translatable("options.dglab.value.seconds", String.format(Locale.ROOT, "%.3f", millis / MILLIS_PER_SECOND));
	}

	/**
	 * Draws the waveform's intensity over time as one bar per sample.
	 */
	private final class Preview extends AbstractWidget {
		private Preview() {
			super(0, 0, ROW_WIDTH, PREVIEW_HEIGHT, Component.empty());
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
			graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), PREVIEW_BACKGROUND);
			int count = WaveformEditorScreen.this.samples.size();
			for (int index = 0; index < count; index++) {
				int left = this.getX() + this.getWidth() * index / count;
				int right = this.getX() + this.getWidth() * (index + 1) / count;
				int barHeight = this.getHeight() * WaveformEditorScreen.this.samples.get(index).intensity / Waveform.MAX_INTENSITY;
				graphics.fill(left, this.getBottom() - barHeight, right, this.getBottom(), PREVIEW_BAR);
			}
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
		}
	}

	private final class SampleList extends ContainerObjectSelectionList<SampleEntry> {
		private SampleList() {
			super(Minecraft.getInstance(), WaveformEditorScreen.this.width, WaveformEditorScreen.this.layout.getContentHeight(),
					WaveformEditorScreen.this.layout.getHeaderHeight(), ROW_HEIGHT);
			this.rebuild();
		}

		/** Replaces the rows after samples were added, removed, or pasted. */
		private void rebuild() {
			List<SampleEntry> entries = new ArrayList<>(WaveformEditorScreen.this.samples.size());
			for (int index = 0; index < WaveformEditorScreen.this.samples.size(); index++) {
				entries.add(new SampleEntry(index));
			}
			this.replaceEntries(entries);
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}
	}

	/**
	 * The row of one sample: when it starts, its two sliders, and whether it is set by hand.
	 */
	private final class SampleEntry extends ContainerObjectSelectionList.Entry<SampleEntry> {
		private final EditableSample sample;
		private final Component time;
		private final IntSlider intensitySlider;
		private final IntSlider frequencySlider;
		private final CycleButton<Boolean> modeButton;

		private SampleEntry(final int index) {
			this.sample = WaveformEditorScreen.this.samples.get(index);
			this.time = seconds(index * Waveform.SAMPLE_MILLIS);
			this.intensitySlider = new IntSlider(SLIDER_WIDTH, 0, Waveform.MAX_INTENSITY, this.sample.intensity,
					value -> Options.genericValueLabel(INTENSITY, value), value -> {
						this.sample.intensity = value;
						WaveformEditorScreen.this.refresh();
					});
			this.frequencySlider = new IntSlider(SLIDER_WIDTH, Waveform.MIN_FREQUENCY, Waveform.MAX_FREQUENCY, this.sample.frequency,
					value -> Options.genericValueLabel(FREQUENCY, value), value -> {
						this.sample.frequency = value;
						WaveformEditorScreen.this.refresh();
					});
			this.frequencySlider.setTooltip(Tooltip.create(Component.translatable("options.dglab.waveform.editor.frequency.tooltip")));
			this.modeButton = CycleButton.booleanBuilder(MANUAL, AUTOMATIC, this.sample.manual)
					.displayOnlyValue()
					.withTooltip(manual -> Tooltip.create(Component.translatable("options.dglab.waveform.editor.mode.tooltip")))
					.create(0, 0, MODE_WIDTH, 20, MODE, (button, manual) -> {
						this.sample.manual = manual;
						WaveformEditorScreen.this.refresh();
					});
		}

		/** Shows the sample's current values. Interpolated samples cannot be dragged. */
		private void refresh() {
			boolean endpoint = this.sample == WaveformEditorScreen.this.samples.getFirst()
					|| this.sample == WaveformEditorScreen.this.samples.getLast();
			this.intensitySlider.setIntValue(this.sample.intensity);
			this.frequencySlider.setIntValue(this.sample.frequency);
			this.intensitySlider.active = this.sample.manual;
			this.frequencySlider.active = this.sample.manual;
			this.modeButton.setValue(this.sample.manual);
			this.modeButton.active = !endpoint;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.intensitySlider, this.frequencySlider, this.modeButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.intensitySlider, this.frequencySlider, this.modeButton);
		}

		@Override
		public void extractContent(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY,
				final boolean hovered, final float a) {
			int x = this.getContentX();
			int y = this.getContentY();
			graphics.text(WaveformEditorScreen.this.font, this.time, x, y + (20 - WaveformEditorScreen.this.font.lineHeight) / 2 + 1, TEXT_COLOR);
			this.intensitySlider.setPosition(x + TIME_WIDTH + SPACING, y);
			this.frequencySlider.setPosition(x + TIME_WIDTH + SLIDER_WIDTH + 2 * SPACING, y);
			this.modeButton.setPosition(this.getContentRight() - MODE_WIDTH, y);
			this.intensitySlider.extractRenderState(graphics, mouseX, mouseY, a);
			this.frequencySlider.extractRenderState(graphics, mouseX, mouseY, a);
			this.modeButton.extractRenderState(graphics, mouseX, mouseY, a);
		}
	}
}
