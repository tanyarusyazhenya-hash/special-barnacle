package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Lowers the FOV option while enabled. Vanilla does not allow FOV below 30. */
public class Zoom extends Module {
	public final NumberSetting fov = add(new NumberSetting("Zoom FOV", 30, 30, 60, 1));

	private int original = -1;

	public Zoom() {
		super("Zoom", "Zoom in (bind it to a key and hold or toggle)", Category.WORLD);
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options != null) {
			original = mc.options.fov().get();
			mc.options.fov().set(fov.i());
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.options != null && mc.options.fov().get() != fov.i()) {
			mc.options.fov().set(fov.i());
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options != null && original > 0) {
			mc.options.fov().set(original);
		}
		original = -1;
	}
}
