package dev.aurora.util;

import dev.aurora.module.ModuleManager;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;

/** Shared color picking for the visual modules: Theme (gradient), Static or Rainbow. */
public final class VisualColor {
	private VisualColor() {}

	public static ModeSetting mode() {
		return new ModeSetting("Color", "Theme", "Theme", "Static", "Rainbow");
	}

	/** t is a position 0..1 along the effect (for gradients). */
	public static int pick(ModeSetting mode, ColorSetting fixed, float t) {
		if (mode.is("Static")) {
			return fixed.rgb();
		}
		if (mode.is("Rainbow")) {
			return ColorUtil.hsb(((System.currentTimeMillis() % 4000L) / 4000f + t) % 1f, 0.7f, 1f);
		}
		return ModuleManager.ui.gradientAt(t);
	}
}
