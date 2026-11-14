package com.urntt.dglab.gametest;

import static com.urntt.dglab.gametest.GameTestSupport.LOGGER;
import static com.urntt.dglab.gametest.GameTestSupport.awaitCommands;
import static com.urntt.dglab.gametest.GameTestSupport.checkEquals;
import static com.urntt.dglab.gametest.GameTestSupport.connectApp;
import static com.urntt.dglab.gametest.GameTestSupport.prepareWorld;
import static com.urntt.dglab.gametest.GameTestSupport.resetConfig;

import com.urntt.dglab.protocol.AppCommands;
import com.urntt.dglab.protocol.Channel;
import com.urntt.dglab.waveform.WaveformPreset;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

/**
 * Checks on a local dedicated server that damage reaches the app in multiplayer too, where the client only learns
 * its health from the server's packets.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DgLabMultiplayerGameTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		resetConfig(context);
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			prepareWorld(context, server);

			try (FakeDgLabApp app = connectApp(context)) {
				int beforeDamage = app.commands().size();
				server.runCommand("damage @p 5");
				List<String> commands = awaitCommands(context, app, beforeDamage, "after 5 points of damage on a server");
				LOGGER.info("Commands after 5 points of damage on a dedicated server: {}", commands);
				checkEquals(List.of(
						"strength-1+1+15", "strength-2+1+15", "clear-1", "clear-2",
						AppCommands.pulse(Channel.A, WaveformPreset.THUMP.waveform().frames()),
						AppCommands.pulse(Channel.B, WaveformPreset.THUMP.waveform().frames())), commands.subList(0, 6),
						"commands after 5 points of damage on a server");
				checkEquals(15, app.strength(Channel.A), "strength of channel A in the app");
			}
		}
	}
}
