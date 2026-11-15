package com.urntt.dglab.screen;

import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.protocol.AppCommands;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Edits who the second player of two-player mode is. "Done" saves, "Cancel" or Escape discards the changes, and
 * "Reset Page" fills in the defaults.
 */
public final class TwoPlayerScreen extends Screen {
	private static final Component TITLE = Component.translatable("options.dglab.two_player.title");
	private static final Component DESCRIPTION = Component.translatable("options.dglab.two_player.description");
	private static final Component PLAYER = Component.translatable("options.dglab.two_player.player");
	private static final Component PLAYER_HINT = Component.translatable("options.dglab.two_player.player.hint")
			.withStyle(EditBox.SEARCH_HINT_STYLE);
	private static final Component QUIT_STRENGTH = Component.translatable("options.dglab.two_player.quit_strength");
	private static final int FIELD_WIDTH = 180;
	private static final int FIELD_HEIGHT = 20;
	private static final int SPACING = 8;
	private static final int DESCRIPTION_WIDTH = 320;
	private static final int FOOTER_BUTTON_WIDTH = 100;

	private final Screen parent;
	private final DgLabConfig config;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private @Nullable EditBox playerBox;
	private @Nullable IntSlider quitStrengthSlider;
	private @Nullable Button resetQuitStrengthButton;

	public TwoPlayerScreen(final Screen parent, final DgLabConfig config) {
		super(TITLE);
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);

		LinearLayout contents = this.layout.addToContents(LinearLayout.vertical().spacing(SPACING));
		contents.defaultCellSetting().alignHorizontallyCenter();
		contents.addChild(new MultiLineTextWidget(DESCRIPTION, this.font)
				.setMaxWidth(Math.min(DESCRIPTION_WIDTH, this.width - 2 * SPACING)).setCentered(true));

		GridLayout grid = contents.addChild(new GridLayout().spacing(SPACING));
		grid.defaultCellSetting().alignVerticallyMiddle();
		GridLayout.RowHelper rows = grid.createRowHelper(3);

		rows.addChild(new StringWidget(PLAYER, this.font));
		this.playerBox = rows.addChild(new EditBox(this.font, FIELD_WIDTH, FIELD_HEIGHT, PLAYER), 2);
		this.playerBox.setMaxLength(DgLabConfig.MAX_PLAYER_NAME_LENGTH);
		this.playerBox.setHint(PLAYER_HINT);
		this.playerBox.setValue(this.config.secondPlayer());

		rows.addChild(new StringWidget(QUIT_STRENGTH, this.font));
		Button resetQuitStrength = ResetButtons.forSetting(QUIT_STRENGTH, quitStrengthText(DgLabConfig.DEFAULT_QUIT_STRENGTH),
				() -> this.setQuitStrength(DgLabConfig.DEFAULT_QUIT_STRENGTH));
		this.resetQuitStrengthButton = resetQuitStrength;
		this.quitStrengthSlider = rows.addChild(new IntSlider(FIELD_WIDTH, 0, AppCommands.MAX_STRENGTH,
				this.config.secondPlayerQuitStrength(), TwoPlayerScreen::quitStrengthText,
				value -> resetQuitStrength.active = value != DgLabConfig.DEFAULT_QUIT_STRENGTH));
		this.quitStrengthSlider.setTooltip(Tooltip.create(Component.translatable("options.dglab.two_player.quit_strength.tooltip")));
		rows.addChild(resetQuitStrength);
		resetQuitStrength.active = this.config.secondPlayerQuitStrength() != DgLabConfig.DEFAULT_QUIT_STRENGTH;

		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(SPACING));
		footer.addChild(ResetButtons.forPage(this::fillInDefaults, false).width(FOOTER_BUTTON_WIDTH).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.saveAndClose()).width(FOOTER_BUTTON_WIDTH).build());
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(FOOTER_BUTTON_WIDTH).build());

		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	/** Puts the defaults into the fields. Like every other change here, they are saved by "Done". */
	private void fillInDefaults() {
		if (this.playerBox != null) {
			this.playerBox.setValue("");
		}
		this.setQuitStrength(DgLabConfig.DEFAULT_QUIT_STRENGTH);
	}

	private void setQuitStrength(final int strength) {
		if (this.quitStrengthSlider != null && this.resetQuitStrengthButton != null) {
			this.quitStrengthSlider.setIntValue(strength);
			this.resetQuitStrengthButton.active = strength != DgLabConfig.DEFAULT_QUIT_STRENGTH;
		}
	}

	private static Component quitStrengthText(final int strength) {
		return strength == 0 ? CommonComponents.OPTION_OFF : Component.literal(Integer.toString(strength));
	}

	private void saveAndClose() {
		if (this.playerBox != null && this.quitStrengthSlider != null) {
			this.config.setTwoPlayer(this.playerBox.getValue().trim(), this.quitStrengthSlider.intValue());
		}
		this.onClose();
	}
}
