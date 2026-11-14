package com.urntt.dglab.screen;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.config.ChannelOption;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.protocol.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * The strength rules: one section per {@link ChannelOption}, with a slider for each channel.
 */
public final class StrengthScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("options.dglab.strength.title");

	public StrengthScreen(final Screen parent) {
		super(parent, Minecraft.getInstance().options, TITLE);
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		for (ChannelOption option : ChannelOption.values()) {
			this.list.addHeader(option.caption());
			this.list.addSmall(slider(Channel.A, option), slider(Channel.B, option));
		}
	}

	/**
	 * Creates the slider of one option for one channel. The slider is labeled with the channel, because the section
	 * header already names the option.
	 */
	private static OptionInstance<Integer> slider(final Channel channel, final ChannelOption option) {
		DgLabConfig config = DgLabClient.config();
		return new OptionInstance<>(
				DgLabClient.channelNameKey(channel),
				OptionInstance.cachedConstantTooltip(option.tooltip()),
				(caption, step) -> Options.genericValueLabel(caption, option.format(option.fromStep(step))),
				new OptionInstance.IntRange(option.minStep(), option.maxStep()),
				option.toStep(config.value(channel, option)),
				step -> config.setValue(channel, option, option.fromStep(step)));
	}
}
