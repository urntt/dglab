package com.urntt.dglab.screen;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.DgLabController;
import com.urntt.dglab.server.PairingQr;
import com.urntt.dglab.server.PairingServer;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Shows the QR code the DG-LAB app scans to connect, the state of the connection, and the safety notice.
 */
public final class PairingScreen extends Screen {
	private static final Component TITLE = Component.translatable("options.dglab.pair.title");
	private static final int SPACING = 4;
	private static final int TEXT_WIDTH = 320;
	/** Rows of text below the code: the state, the address, and the safety notice. */
	private static final int TEXT_ROWS = 5;
	private static final int DARK = 0xFF000000;
	private static final int LIGHT = 0xFFFFFFFF;

	private final @Nullable Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private PairingServer.@Nullable State shownState;

	public PairingScreen(final @Nullable Screen parent) {
		super(TITLE);
		this.parent = parent;
	}

	@Override
	protected void init() {
		DgLabController controller = DgLabClient.controller();
		PairingServer.State state = controller.server().state();
		this.shownState = state;

		// init() runs again whenever the window is resized or the state changes, so it starts from an empty layout.
		this.layout.removeChildren();
		this.layout.addTitleHeader(this.title, this.font);
		LinearLayout contents = this.layout.addToContents(LinearLayout.vertical().spacing(SPACING));
		contents.defaultCellSetting().alignHorizontallyCenter();

		int textWidth = Math.min(TEXT_WIDTH, this.width - 2 * SPACING);
		if (state == PairingServer.State.LISTENING || state == PairingServer.State.BOUND) {
			String url = controller.pairingUrl();
			int available = this.height - this.layout.getHeaderHeight() - this.layout.getFooterHeight()
					- TEXT_ROWS * (this.font.lineHeight + SPACING);
			contents.addChild(new QrWidget(PairingQr.encode(url), Math.min(available, this.width - 2 * SPACING)));
			contents.addChild(new MultiLineTextWidget(Component.literal(url.substring(url.lastIndexOf('#') + 1))
					.withStyle(ChatFormatting.GRAY), this.font).setMaxWidth(textWidth).setCentered(true));
		}
		contents.addChild(new MultiLineTextWidget(stateMessage(state, controller.server().port()), this.font)
				.setMaxWidth(textWidth).setCentered(true));
		contents.addChild(new MultiLineTextWidget(DgLabController.SAFETY_NOTICE, this.font)
				.setMaxWidth(textWidth).setCentered(true));

		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.layout.arrangeElements();
	}

	@Override
	public void tick() {
		// The state changes while the screen is open, most importantly when the app binds.
		if (DgLabClient.controller().server().state() != this.shownState) {
			this.rebuildWidgets();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	/**
	 * Describes a state of the connection server to the player.
	 */
	public static Component stateMessage(final PairingServer.State state, final int port) {
		MutableComponent message = Component.translatable(stateKey(state), port);
		return switch (state) {
			case BOUND -> message.withStyle(ChatFormatting.GREEN);
			case FAILED -> message.withStyle(ChatFormatting.RED);
			default -> message;
		};
	}

	/** Translation key of the description of a state. Only the one for a failed start uses the port. */
	public static String stateKey(final PairingServer.State state) {
		return "options.dglab.pair.state." + state.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Draws a QR code with its quiet zone, at a whole number of pixels per module.
	 */
	private static final class QrWidget extends AbstractWidget {
		private final boolean[][] modules;
		private final int moduleSize;

		/**
		 * @param maxSize the largest width and height the widget may take
		 */
		private QrWidget(final boolean[][] modules, final int maxSize) {
			super(0, 0, 0, 0, TITLE);
			this.modules = modules;
			int modulesAcross = modules.length + 2 * PairingQr.QUIET_ZONE;
			this.moduleSize = Math.max(1, maxSize / modulesAcross);
			this.setSize(modulesAcross * this.moduleSize, modulesAcross * this.moduleSize);
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
			graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), LIGHT);
			int left = this.getX() + PairingQr.QUIET_ZONE * this.moduleSize;
			int top = this.getY() + PairingQr.QUIET_ZONE * this.moduleSize;
			for (int row = 0; row < this.modules.length; row++) {
				// Each run of dark modules in a row is drawn as one rectangle.
				int runStart = -1;
				for (int column = 0; column <= this.modules.length; column++) {
					boolean dark = column < this.modules.length && this.modules[row][column];
					if (dark && runStart < 0) {
						runStart = column;
					} else if (!dark && runStart >= 0) {
						graphics.fill(left + runStart * this.moduleSize, top + row * this.moduleSize,
								left + column * this.moduleSize, top + (row + 1) * this.moduleSize, DARK);
						runStart = -1;
					}
				}
			}
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
		}
	}
}
