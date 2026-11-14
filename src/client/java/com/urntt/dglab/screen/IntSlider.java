package com.urntt.dglab.screen;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A vanilla slider over a range of whole numbers. Unlike the base class, the arrow keys move it by exactly one.
 */
final class IntSlider extends AbstractSliderButton {
	private final int min;
	private final int max;
	private final IntFunction<Component> label;
	private final IntConsumer onChange;

	/**
	 * @param label turns a value into the text on the slider
	 * @param onChange called when the player changes the value, but not by {@link #setIntValue(int)}
	 */
	IntSlider(final int width, final int min, final int max, final int value, final IntFunction<Component> label,
			final IntConsumer onChange) {
		super(0, 0, width, DEFAULT_HEIGHT, Component.empty(), 0.0);
		this.min = min;
		this.max = max;
		this.label = label;
		this.onChange = onChange;
		this.setIntValue(value);
	}

	int intValue() {
		return this.min + (int) Math.round(this.value * (this.max - this.min));
	}

	/**
	 * Moves the slider without reporting a change.
	 */
	void setIntValue(final int value) {
		this.value = (double) (Math.clamp(value, this.min, this.max) - this.min) / (this.max - this.min);
		this.updateMessage();
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (this.canChangeValue && (event.isLeft() || event.isRight())) {
			int value = Math.clamp(this.intValue() + (event.isLeft() ? -1 : 1), this.min, this.max);
			if (value != this.intValue()) {
				this.setIntValue(value);
				this.onChange.accept(value);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void updateMessage() {
		this.setMessage(this.label.apply(this.intValue()));
	}

	@Override
	protected void applyValue() {
		// Snap to the whole number under the handle, so the handle and the label agree.
		int value = this.intValue();
		this.value = (double) (value - this.min) / (this.max - this.min);
		this.onChange.accept(value);
	}
}
