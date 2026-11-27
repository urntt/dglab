package com.urntt.dglab.compat.oneconfig;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.config.ToggleSetting;
import org.polyfrost.oneconfig.api.toggle.v1.ModToggle;

/**
 * The switch on the mod's card in OneConfig. It is the master switch, {@link ToggleSetting#ENABLED}.
 */
public final class OneConfigModToggle implements ModToggle {
	@Override
	public boolean isEnabled() {
		return DgLabClient.config().get(ToggleSetting.ENABLED);
	}

	@Override
	public void setEnabled(final boolean enabled) {
		DgLabClient.config().set(ToggleSetting.ENABLED, enabled);
	}
}
