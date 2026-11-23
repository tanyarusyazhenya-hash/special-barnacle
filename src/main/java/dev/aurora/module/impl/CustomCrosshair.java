package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;

/** Settings for the custom crosshair; drawn by CrosshairRenderer in place of the vanilla one. */
public class CustomCrosshair extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Cross", "Cross", "Plus", "Dot", "Circle", "Square",
		"Cross + Dot"));
	public final NumberSetting size = add(new NumberSetting("Size", 5, 1, 20, 1));
	public final NumberSetting gap = add(new NumberSetting("Gap", 3, 0, 12, 1));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", 1, 1, 4, 1));
	public final BoolSetting outline = add(new BoolSetting("Outline", true));
	public final ModeSetting colorMode = add(new ModeSetting("Color", "Accent", "Accent", "Static", "Rainbow", "Health"));
	public final ColorSetting staticColor = add(new ColorSetting("Static Color", 0xFFFFFF));
	public final BoolSetting targetColor = add(new BoolSetting("Change Color On Target", true));
	public final ColorSetting enemyColor = add(new ColorSetting("Target Color", 0xFF4D4D));
	public final BoolSetting dynamic = add(new BoolSetting("Dynamic Gap (Moving)", true));
	public final BoolSetting attackIndicator = add(new BoolSetting("Attack Cooldown Ring", false));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 1.0, 0.1, 1.0, 0.05));

	public CustomCrosshair() {
		super("CustomCrosshair", "Replaces the crosshair with a customizable one", Category.RENDER);
		staticColor.visibleWhen(() -> colorMode.is("Static"));
		enemyColor.visibleWhen(() -> targetColor.value);
		gap.visibleWhen(() -> style.is("Cross") || style.is("Plus") || style.is("Cross + Dot"));
	}
}
