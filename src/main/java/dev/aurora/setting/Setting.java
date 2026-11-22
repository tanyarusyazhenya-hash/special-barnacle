package dev.aurora.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

public abstract class Setting {
	public final String name;
	private BooleanSupplier visible = () -> true;

	protected Setting(String name) {
		this.name = name;
	}

	/** Hide this setting in the GUI unless the condition holds. */
	public void visibleWhen(BooleanSupplier condition) {
		this.visible = condition;
	}

	public boolean isVisible() {
		return visible.getAsBoolean();
	}

	/** Buttons and similar settings have nothing to store in the config. */
	public boolean persistent() {
		return true;
	}

	public abstract JsonElement save();

	public abstract void load(JsonElement element);
}
