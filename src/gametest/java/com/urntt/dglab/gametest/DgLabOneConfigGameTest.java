package com.urntt.dglab.gametest;

import static com.urntt.dglab.gametest.GameTestSupport.LOGGER;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Checks the OneConfig integration when OneConfig is installed. Without it, the test only confirms that the mod
 * loaded without touching OneConfig. The checks live in {@link OneConfigChecks}, which is only loaded when
 * OneConfig is present, because it refers to OneConfig's classes.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DgLabOneConfigGameTest implements FabricClientGameTest {
	private static final String ONECONFIG_MOD_ID = "oneconfig";

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded(ONECONFIG_MOD_ID)) {
			LOGGER.info("OneConfig is not installed; skipping the OneConfig checks");
			return;
		}
		OneConfigChecks.run(context);
	}
}
