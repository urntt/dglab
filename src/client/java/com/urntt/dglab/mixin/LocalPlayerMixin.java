package com.urntt.dglab.mixin;

import com.urntt.dglab.DgLabClient;
import com.urntt.dglab.feedback.HealthTracker;
import com.urntt.dglab.feedback.StrengthController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports the local player's damage and death to the mod. {@code hurtTo} receives every health value the server
 * sends for this player; this mixin only observes it and leaves the game's behavior untouched.
 *
 * <p>The previous health is remembered here instead of read from the player, because the player's health may
 * already have been updated through its synced entity data when {@code hurtTo} runs. A new player object, as after
 * joining, respawning, or changing dimension, starts without a previous health, so its first value only sets the
 * baseline.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
	@Unique
	private final HealthTracker dglab$health = new HealthTracker();

	@Inject(method = "hurtTo", at = @At("HEAD"))
	private void dglab$reportHealthChange(final float newHealth, final CallbackInfo callback) {
		DgLabClient.controller().onHealthChange(StrengthController.Source.LOCAL, this.dglab$health.sample(newHealth));
	}
}
