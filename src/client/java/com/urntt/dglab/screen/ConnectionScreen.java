package com.urntt.dglab.screen;

import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.server.LanAddresses;
import com.urntt.dglab.server.PairingUrl;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
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
 * Edits the connection server's port and what the pairing QR code points to. "Done" saves and is disabled while a
 * field is invalid, "Cancel" or Escape discards the changes, and "Reset Page" fills in the defaults.
 */
public final class ConnectionScreen extends Screen {
	private static final Component TITLE = Component.translatable("options.dglab.connection.title");
	private static final Component LISTEN_PORT = Component.translatable("options.dglab.connection.listen_port");
	private static final Component QR_ADDRESS = Component.translatable("options.dglab.connection.qr_address");
	private static final Component CUSTOM_ADDRESS = Component.translatable("options.dglab.connection.custom_address");
	private static final Component QR_PORT = Component.translatable("options.dglab.connection.qr_port");
	private static final Component QR_PORT_HINT = Component.translatable("options.dglab.connection.qr_port.hint")
			.withStyle(EditBox.SEARCH_HINT_STYLE);
	private static final int INVALID_TEXT_COLOR = 0xFFFF0000;
	private static final int FIELD_WIDTH = 180;
	private static final int FIELD_HEIGHT = 20;
	private static final int SPACING = 8;
	private static final int FOOTER_BUTTON_WIDTH = 100;
	private static final int MAX_PORT_LENGTH = 5;
	/** Longest host name the address field accepts. */
	private static final int MAX_ADDRESS_LENGTH = 253;

	/**
	 * A way to choose the address in the QR code.
	 *
	 * @param address the value stored in the configuration, or null for an address typed into the custom field
	 */
	private record AddressChoice(Component label, @Nullable String address) {
		private boolean isCustom() {
			return this.address == null;
		}
	}

	private static final AddressChoice AUTOMATIC = new AddressChoice(
			Component.translatable("options.dglab.connection.qr_address.automatic"), DgLabConfig.QR_ADDRESS_AUTOMATIC);
	private static final AddressChoice CUSTOM = new AddressChoice(
			Component.translatable("options.dglab.connection.qr_address.custom"), null);

	private final Screen parent;
	private final DgLabConfig config;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private @Nullable EditBox listenPortBox;
	private @Nullable CycleButton<AddressChoice> addressButton;
	private @Nullable EditBox customAddressBox;
	private @Nullable EditBox qrPortBox;
	private @Nullable Button doneButton;

	public ConnectionScreen(final Screen parent, final DgLabConfig config) {
		super(TITLE);
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);

		List<AddressChoice> choices = new ArrayList<>();
		choices.add(AUTOMATIC);
		for (LanAddresses.Candidate candidate : LanAddresses.list()) {
			choices.add(new AddressChoice(Component.translatable("options.dglab.connection.qr_address.interface",
					candidate.address(), candidate.interfaceName()), candidate.address()));
		}
		choices.add(CUSTOM);
		String storedAddress = this.config.qrAddress();
		AddressChoice initialChoice = choices.stream()
				.filter(choice -> storedAddress.equals(choice.address()))
				.findFirst()
				.orElse(CUSTOM);

		GridLayout grid = this.layout.addToContents(new GridLayout().spacing(SPACING));
		grid.defaultCellSetting().alignVerticallyMiddle();
		GridLayout.RowHelper rows = grid.createRowHelper(2);

		rows.addChild(new StringWidget(LISTEN_PORT, this.font));
		this.listenPortBox = rows.addChild(new EditBox(this.font, FIELD_WIDTH, FIELD_HEIGHT, LISTEN_PORT));
		this.listenPortBox.setMaxLength(MAX_PORT_LENGTH);
		this.listenPortBox.setValue(Integer.toString(this.config.listenPort()));
		this.listenPortBox.setTooltip(Tooltip.create(Component.translatable("options.dglab.connection.listen_port.tooltip")));
		this.listenPortBox.setResponder(text -> this.validate());

		rows.addChild(new StringWidget(QR_ADDRESS, this.font));
		this.addressButton = rows.addChild(CycleButton.<AddressChoice>builder(AddressChoice::label, initialChoice)
				.withValues(choices)
				.displayOnlyValue()
				.withTooltip(choice -> Tooltip.create(Component.translatable("options.dglab.connection.qr_address.tooltip")))
				.create(0, 0, FIELD_WIDTH, FIELD_HEIGHT, QR_ADDRESS, (button, choice) -> this.validate()));

		rows.addChild(new StringWidget(CUSTOM_ADDRESS, this.font));
		this.customAddressBox = rows.addChild(new EditBox(this.font, FIELD_WIDTH, FIELD_HEIGHT, CUSTOM_ADDRESS));
		this.customAddressBox.setMaxLength(MAX_ADDRESS_LENGTH);
		this.customAddressBox.setValue(initialChoice.isCustom() ? storedAddress : "");
		this.customAddressBox.setResponder(text -> this.validate());

		rows.addChild(new StringWidget(QR_PORT, this.font));
		this.qrPortBox = rows.addChild(new EditBox(this.font, FIELD_WIDTH, FIELD_HEIGHT, QR_PORT));
		this.qrPortBox.setMaxLength(MAX_PORT_LENGTH);
		this.qrPortBox.setHint(QR_PORT_HINT);
		this.qrPortBox.setValue(this.config.qrPort() == DgLabConfig.QR_PORT_SAME_AS_LISTEN ? "" : Integer.toString(this.config.qrPort()));
		this.qrPortBox.setTooltip(Tooltip.create(Component.translatable("options.dglab.connection.qr_port.tooltip")));
		this.qrPortBox.setResponder(text -> this.validate());

		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(SPACING));
		footer.addChild(ResetButtons.forPage(this::fillInDefaults, false).width(FOOTER_BUTTON_WIDTH).build());
		this.doneButton = footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.saveAndClose())
				.width(FOOTER_BUTTON_WIDTH).build());
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(FOOTER_BUTTON_WIDTH).build());

		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.validate();
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
		if (this.listenPortBox == null || this.addressButton == null || this.customAddressBox == null || this.qrPortBox == null) {
			return;
		}
		this.listenPortBox.setValue(Integer.toString(DgLabConfig.DEFAULT_PORT));
		this.addressButton.setValue(AUTOMATIC);
		this.customAddressBox.setValue("");
		this.qrPortBox.setValue("");
		this.validate();
	}

	private void saveAndClose() {
		if (this.listenPortBox == null || this.addressButton == null || this.customAddressBox == null || this.qrPortBox == null) {
			return;
		}
		AddressChoice choice = this.addressButton.getValue();
		String address = choice.isCustom() ? this.customAddressBox.getValue().trim() : choice.address();
		this.config.setConnection(
				parsePort(this.listenPortBox.getValue()).orElse(DgLabConfig.DEFAULT_PORT),
				address,
				parsePort(this.qrPortBox.getValue()).orElse(DgLabConfig.QR_PORT_SAME_AS_LISTEN));
		this.onClose();
	}

	/**
	 * Marks invalid fields in red, enables the custom address field only for the custom choice, and enables "Done"
	 * only while everything is valid.
	 */
	private void validate() {
		if (this.listenPortBox == null || this.addressButton == null || this.customAddressBox == null || this.qrPortBox == null
				|| this.doneButton == null) {
			return;
		}
		boolean custom = this.addressButton.getValue().isCustom();
		this.customAddressBox.setEditable(custom);

		boolean listenPortValid = parsePort(this.listenPortBox.getValue()).isPresent();
		boolean addressValid = !custom || PairingUrl.isValidHost(this.customAddressBox.getValue().trim());
		boolean qrPortValid = this.qrPortBox.getValue().isBlank() || parsePort(this.qrPortBox.getValue()).isPresent();
		mark(this.listenPortBox, listenPortValid);
		mark(this.customAddressBox, addressValid);
		mark(this.qrPortBox, qrPortValid);
		this.doneButton.active = listenPortValid && addressValid && qrPortValid;
	}

	private static void mark(final EditBox box, final boolean valid) {
		box.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : INVALID_TEXT_COLOR);
	}

	private static OptionalInt parsePort(final String text) {
		try {
			int port = Integer.parseInt(text.trim());
			return port >= DgLabConfig.MIN_PORT && port <= DgLabConfig.MAX_PORT ? OptionalInt.of(port) : OptionalInt.empty();
		} catch (NumberFormatException e) {
			return OptionalInt.empty();
		}
	}
}
