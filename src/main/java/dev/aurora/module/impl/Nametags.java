package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;

/** Settings for player nametags. Drawing happens in NametagRenderer (HUD layer). */
public class Nametags extends Module {
	public final BoolSetting health = add(new BoolSetting("Health", true));
	public final BoolSetting distance = add(new BoolSetting("Distance", true));
	public final BoolSetting heldItem = add(new BoolSetting("Held Item", false));
	public final BoolSetting background = add(new BoolSetting("Background", true));
	public final NumberSetting opacity = add(new NumberSetting("Background Opacity", 0.55, 0.0, 1.0, 0.05));
	public final ModeSetting colorMode = add(new ModeSetting("Name Color", "Health", "Health", "Static", "Accent"));
	public final ColorSetting staticColor = add(new ColorSetting("Static Color", 0xFFFFFF));
	public final NumberSetting maxDistance = add(new NumberSetting("Max Distance", 128, 16, 512, 16));
	public final NumberSetting yOffset = add(new NumberSetting("Height Offset", 0.7, 0.0, 2.0, 0.1));
	public final BoolSetting showInvisible = add(new BoolSetting("Show Invisible", true));

	public Nametags() {
		super("Nametags", "Name, health and distance above players", Category.RENDER);
		opacity.visibleWhen(() -> background.value);
		staticColor.visibleWhen(() -> colorMode.is("Static"));
	}
}
