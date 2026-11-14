package com.urntt.dglab.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.DgLabController;
import com.urntt.dglab.config.ChannelOption;
import com.urntt.dglab.config.DgLabConfig;
import com.urntt.dglab.config.ToggleSetting;
import com.urntt.dglab.feedback.StrengthController;
import com.urntt.dglab.protocol.AppCommands;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.screen.DgLabConfigScreen;
import com.urntt.dglab.screen.PairingScreen;
import com.urntt.dglab.server.PairingServer;
import com.urntt.dglab.waveform.WaveformSlot;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The {@code /dglab} client command. It offers the configuration screen's functions from the chat.
 */
public final class DgLabCommand {
	public static final String NAME = "dglab";

	private static final String SETTING = "setting";
	private static final String OPTION = "option";
	private static final String SLOT = "slot";
	private static final String VALUE_A = "a";
	private static final String VALUE_B = "b";
	private static final int SUCCESS = 1;
	private static final int FAILURE = 0;

	private DgLabCommand() {
	}

	public static void register(final CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal(NAME)
				.executes(DgLabCommand::status)
				.then(ClientCommands.literal("status").executes(DgLabCommand::status))
				.then(ClientCommands.literal("settings").executes(context -> openScreen(context, DgLabConfigScreen::new)))
				.then(ClientCommands.literal("pair").executes(context -> openScreen(context, PairingScreen::new)))
				.then(ClientCommands.literal("server")
						.then(ClientCommands.literal("start").executes(context -> setServer(context, true)))
						.then(ClientCommands.literal("stop").executes(context -> setServer(context, false))))
				.then(ClientCommands.literal("strength")
						.then(ClientCommands.literal("get").executes(DgLabCommand::getStrength))
						.then(ClientCommands.literal("set")
								.then(ClientCommands.argument(VALUE_A, IntegerArgumentType.integer(0, AppCommands.MAX_STRENGTH))
										.then(ClientCommands.argument(VALUE_B, IntegerArgumentType.integer(0, AppCommands.MAX_STRENGTH))
												.executes(DgLabCommand::setStrength)))))
				.then(ClientCommands.literal("toggle")
						.then(ClientCommands.argument(SETTING, StringArgumentType.word())
								.suggests(suggest(() -> Arrays.stream(ToggleSetting.values()).map(ToggleSetting::id).toArray(String[]::new)))
								.executes(DgLabCommand::toggle)))
				.then(ClientCommands.literal("config")
						.then(ClientCommands.literal("get").executes(DgLabCommand::getConfig))
						.then(ClientCommands.literal("set")
								.then(ClientCommands.argument(OPTION, StringArgumentType.word())
										.suggests(suggest(() -> Arrays.stream(ChannelOption.values()).map(ChannelOption::id).toArray(String[]::new)))
										.then(ClientCommands.argument(VALUE_A, DoubleArgumentType.doubleArg(0))
												.then(ClientCommands.argument(VALUE_B, DoubleArgumentType.doubleArg(0))
														.executes(DgLabCommand::setConfig))))))
				.then(ClientCommands.literal("test")
						.then(ClientCommands.argument(SLOT, StringArgumentType.word())
								.suggests(suggest(() -> Arrays.stream(WaveformSlot.values()).map(WaveformSlot::id).toArray(String[]::new)))
								.executes(DgLabCommand::test))));
	}

	private static int status(final CommandContext<FabricClientCommandSource> context) {
		DgLabController controller = DgLabClient.controller();
		context.getSource().sendFeedback(Component.translatable("commands.dglab.status.server",
				Component.translatable(serverStateKey(controller.server().state())), controller.server().port()));
		return getStrength(context);
	}

	/** Translation key of a state of the connection server, as the status shows it. */
	public static String serverStateKey(final PairingServer.State state) {
		return "commands.dglab.server_state." + state.name().toLowerCase(Locale.ROOT);
	}

	private static int openScreen(final CommandContext<FabricClientCommandSource> context, final Function<Screen, Screen> screen) {
		Minecraft client = context.getSource().getClient();
		// The chat screen closes itself after the command ran, so the new screen is opened once that is done.
		client.schedule(() -> client.gui.setScreen(screen.apply(null)));
		return SUCCESS;
	}

	private static int setServer(final CommandContext<FabricClientCommandSource> context, final boolean enabled) {
		DgLabController controller = DgLabClient.controller();
		DgLabClient.config().set(ToggleSetting.SERVER_ENABLED, enabled);
		if (enabled && controller.server().state() == PairingServer.State.FAILED) {
			// The controller leaves a server alone whose port could not be opened; asking again retries it.
			controller.restartServer();
		}
		context.getSource().sendFeedback(CommonComponents.optionStatus(ToggleSetting.SERVER_ENABLED.caption(), enabled));
		return SUCCESS;
	}

	private static int getStrength(final CommandContext<FabricClientCommandSource> context) {
		StrengthController strength = DgLabClient.controller().strength();
		if (!strength.isBound()) {
			context.getSource().sendFeedback(Component.translatable("commands.dglab.not_bound"));
			return FAILURE;
		}
		context.getSource().sendFeedback(Component.translatable("commands.dglab.strength",
				strength.strength(Channel.A), strength.limit(Channel.A), strength.strength(Channel.B), strength.limit(Channel.B)));
		return SUCCESS;
	}

	private static int setStrength(final CommandContext<FabricClientCommandSource> context) {
		StrengthController strength = DgLabClient.controller().strength();
		if (!strength.isBound()) {
			context.getSource().sendError(Component.translatable("commands.dglab.not_bound"));
			return FAILURE;
		}
		strength.setStrength(Channel.A, IntegerArgumentType.getInteger(context, VALUE_A));
		strength.setStrength(Channel.B, IntegerArgumentType.getInteger(context, VALUE_B));
		return getStrength(context);
	}

	private static int toggle(final CommandContext<FabricClientCommandSource> context) {
		String id = StringArgumentType.getString(context, SETTING);
		Optional<ToggleSetting> setting = ToggleSetting.byId(id);
		if (setting.isEmpty()) {
			context.getSource().sendError(Component.translatable("commands.dglab.unknown_setting", id));
			return FAILURE;
		}
		boolean value = DgLabClient.config().toggle(setting.get());
		context.getSource().sendFeedback(CommonComponents.optionStatus(setting.get().caption(), value));
		return SUCCESS;
	}

	private static int getConfig(final CommandContext<FabricClientCommandSource> context) {
		for (ChannelOption option : ChannelOption.values()) {
			context.getSource().sendFeedback(describe(option));
		}
		return SUCCESS;
	}

	private static int setConfig(final CommandContext<FabricClientCommandSource> context) {
		String id = StringArgumentType.getString(context, OPTION);
		Optional<ChannelOption> option = ChannelOption.byId(id);
		if (option.isEmpty()) {
			context.getSource().sendError(Component.translatable("commands.dglab.unknown_option", id));
			return FAILURE;
		}
		DgLabConfig config = DgLabClient.config();
		config.setValue(Channel.A, option.get(), DoubleArgumentType.getDouble(context, VALUE_A));
		config.setValue(Channel.B, option.get(), DoubleArgumentType.getDouble(context, VALUE_B));
		context.getSource().sendFeedback(describe(option.get()));
		return SUCCESS;
	}

	private static int test(final CommandContext<FabricClientCommandSource> context) {
		String id = StringArgumentType.getString(context, SLOT);
		Optional<WaveformSlot> slot = WaveformSlot.byId(id);
		if (slot.isEmpty()) {
			context.getSource().sendError(Component.translatable("commands.dglab.unknown_slot", id));
			return FAILURE;
		}
		StrengthController strength = DgLabClient.controller().strength();
		if (!strength.isBound()) {
			context.getSource().sendError(Component.translatable("commands.dglab.not_bound"));
			return FAILURE;
		}
		strength.preview(slot.get().channel(), DgLabClient.config().waveform(slot.get()));
		context.getSource().sendFeedback(Component.translatable("commands.dglab.test", slot.get().label()));
		return SUCCESS;
	}

	private static Component describe(final ChannelOption option) {
		DgLabConfig config = DgLabClient.config();
		return Component.translatable("commands.dglab.config.value", option.caption(),
				option.format(config.value(Channel.A, option)), option.format(config.value(Channel.B, option)));
	}

	private static SuggestionProvider<FabricClientCommandSource> suggest(final Supplier<String[]> names) {
		return (context, builder) -> SharedSuggestionProvider.suggest(names.get(), builder);
	}
}
