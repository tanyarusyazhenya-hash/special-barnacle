package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;

/** Settings for the radar; drawn by RadarRenderer. */
public class Radar extends Module {
	public final NumberSetting size = add(new NumberSetting("Size", 90, 50, 200, 5));
	public final NumberSetting range = add(new NumberSetting("Range", 48, 10, 128, 2));
	public final ModeSetting corner = add(new ModeSetting("Corner", "Top Left", "Top Left", "Top Right",
		"Bottom Left", "Bottom Right"));
	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final BoolSetting hostile = add(new BoolSetting("Hostile Mobs", false));
	public final BoolSetting passive = add(new BoolSetting("Passive Mobs", false));
	public final NumberSetting opacity = add(new NumberSetting("Background Opacity", 0.55, 0.0, 1.0, 0.05));

	public Radar() {
		super("Radar", "Mini radar with nearby players", Category.RENDER);
	}
}
