package com.urntt.dglab.screen;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.config.ChannelOption;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.protocol.Channel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The strength rules: one section per {@link ChannelOption}, with a slider for each channel.
 */
public final class StrengthScreen extends DgLabOptionsScreen {
	private static final Component TITLE = Component.translatable("options.dglab.strength.title");

	public StrengthScreen(final Screen parent) {
		super(parent, TITLE);
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		DgLabConfig config = DgLabClient.config();
		for (ChannelOption option : ChannelOption.values()) {
			this.list.addHeader(option.caption());
			for (Channel channel : Channel.values()) {
				// The slider is labeled with the channel, because the section header already names the option.
				this.addSlider(DgLabClient.channelNameKey(channel), option.tooltip(), step -> option.format(option.fromStep(step)),
						option.minStep(), option.maxStep(), option.toStep(config.value(channel, option)),
						option.toStep(option.defaultValue()), step -> config.setValue(channel, option, option.fromStep(step)));
			}
		}
	}

	@Override
	protected void resetPage() {
		DgLabConfig config = DgLabClient.config();
		for (Channel channel : Channel.values()) {
			for (ChannelOption option : ChannelOption.values()) {
				config.setValue(channel, option, option.defaultValue());
			}
		}
	}

	@Override
	protected Screen recreate() {
		return new StrengthScreen(this.lastScreen);
	}
}
