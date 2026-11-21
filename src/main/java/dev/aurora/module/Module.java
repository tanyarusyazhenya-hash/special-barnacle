package dev.aurora.module;

import dev.aurora.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
	public final String name;
	public final String description;
	public final Category category;
	public final List<Setting> settings = new ArrayList<>();

	private boolean enabled;

	// GUI state
	public boolean expanded;
	public float expandAnim;
	public float enableAnim;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	public <T extends Setting> T add(T setting) {
		settings.add(setting);
		return setting;
	}

	/** Modules like Interface are always on and only expose settings. */
	public boolean hasToggle() {
		return true;
	}

	/** Whether this module appears in the HUD module list when enabled. */
	public boolean showInList() {
		return hasToggle();
	}

	/** Short extra text shown next to the name in the HUD module list (e.g. the current mode). */
	public String info() {
		return null;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean value) {
		if (enabled == value) {
			return;
		}
		enabled = value;
		enableAnim = value ? 1f : 0f;
		if (value) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected void onEnable() {}

	protected void onDisable() {}

	public void onTick(Minecraft mc) {}
}
