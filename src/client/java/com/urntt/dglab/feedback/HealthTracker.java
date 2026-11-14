package com.urntt.dglab.feedback;

/**
 * Derives damage and death from a player's health sampled once per tick.
 */
public final class HealthTracker {
	/**
	 * What changed since the previous sample.
	 *
	 * @param damage the health lost, or 0
	 * @param died whether the player went from alive to dead
	 */
	public record Change(float damage, boolean died) {
		public static final Change NONE = new Change(0.0F, false);
	}

	private static final float UNKNOWN = Float.NaN;

	private float lastHealth = UNKNOWN;

	/**
	 * Records a sample and returns the change since the previous one. The first sample after {@link #reset()} only
	 * sets the baseline.
	 */
	public Change sample(final float health) {
		float previous = this.lastHealth;
		this.lastHealth = health;
		if (Float.isNaN(previous) || health >= previous) {
			return Change.NONE;
		}
		return new Change(previous - health, previous > 0.0F && health <= 0.0F);
	}

	/** Whether the last sample showed the player alive. */
	public boolean isAlive() {
		return !Float.isNaN(this.lastHealth) && this.lastHealth > 0.0F;
	}

	public void reset() {
		this.lastHealth = UNKNOWN;
	}

	/**
	 * Returns the share of its health a player has lost, from 0 to 1.
	 */
	public static float lostShare(final float health, final float maxHealth) {
		return maxHealth > 0.0F ? Math.clamp((maxHealth - health) / maxHealth, 0.0F, 1.0F) : 0.0F;
	}
}
