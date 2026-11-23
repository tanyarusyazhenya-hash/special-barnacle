package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Client-side time of day. The server keeps its own time; only what you see changes. */
public class TimeChanger extends Module {
	public final ModeSetting preset = add(new ModeSetting("Time", "Day", "Day", "Noon", "Sunset", "Night", "Midnight",
		"Sunrise", "Custom"));
	public final NumberSetting custom = add(new NumberSetting("Custom Time", 6000, 0, 24000, 250));
	public final ModeSetting mode = add(new ModeSetting("Mode", "Fixed", "Fixed", "Cycle"));
	public final NumberSetting speed = add(new NumberSetting("Cycle Speed", 20, 1, 200, 1));

	private double cycle;

	public TimeChanger() {
		super("TimeChanger", "Changes the time of day you see", Category.RENDER);
		custom.visibleWhen(() -> preset.is("Custom") && mode.is("Fixed"));
		preset.visibleWhen(() -> mode.is("Fixed"));
		speed.visibleWhen(() -> mode.is("Cycle"));
	}

	@Override
	public String info() {
		return mode.is("Cycle") ? "Cycle" : preset.get();
	}

	private long fixedTime() {
		return switch (preset.get()) {
			case "Noon" -> 6000;
			case "Sunset" -> 12500;
			case "Night" -> 14000;
			case "Midnight" -> 18000;
			case "Sunrise" -> 23000;
			case "Custom" -> (long) custom.value;
			default -> 1000;
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null) {
			return;
		}
		long t;
		if (mode.is("Cycle")) {
			cycle = (cycle + speed.value) % 24000;
			t = (long) cycle;
		} else {
			t = fixedTime();
		}
		mc.level.setTimeFromServer(mc.level.getGameTime(), t, false);
	}
}
