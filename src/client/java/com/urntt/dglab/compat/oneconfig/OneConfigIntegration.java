package com.urntt.dglab.compat.oneconfig;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.screen.DgLabConfigScreen;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.config.v1.ConfigEntrypoint;

/**
 * OneConfig entrypoint. OneConfig is an optional dependency, so only the classes in this package may reference it;
 * they are only loaded when OneConfig calls this entrypoint.
 */
public final class OneConfigIntegration implements ConfigEntrypoint {
	@Override
	public void registerConfigs(final Context context) {
		context.register(new DgLabOneConfig(DgLabClient.config()));
		// OneConfig's button for a mod's own screen opens the mod's main configuration screen.
		context.originalScreen(() -> {
			Minecraft client = Minecraft.getInstance();
			client.gui.setScreen(new DgLabConfigScreen(client.gui.screen()));
		});
	}
}
