package dev.aurora.render;

import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.world.phys.AABB;

/** The shared customization block used by every ESP-style module. */
public final class EspStyle {
	public final ModeSetting mode;
	public final NumberSetting outlineOpacity;
	public final NumberSetting fillOpacity;
	public final NumberSetting thickness;
	public final NumberSetting range;
	public final BoolSetting throughWalls;
	public final ModeSetting colorMode;

	public EspStyle(Module owner) {
		mode = owner.add(new ModeSetting("Mode", "Outline", "Outline", "Filled", "Both", "Corners"));
		outlineOpacity = owner.add(new NumberSetting("Outline Opacity", 1.0, 0.0, 1.0, 0.05));
		fillOpacity = owner.add(new NumberSetting("Fill Opacity", 0.25, 0.0, 1.0, 0.05));
		thickness = owner.add(new NumberSetting("Thickness", 0.03, 0.01, 0.12, 0.005));
		colorMode = owner.add(new ModeSetting("Color Mode", "Static", "Static", "Rainbow", "Pulse", "Distance"));
		throughWalls = owner.add(new BoolSetting("Through Walls", true));
		range = owner.add(new NumberSetting("Range", 128, 16, 512, 16));

		outlineOpacity.visibleWhen(() -> !mode.is("Filled"));
		thickness.visibleWhen(() -> !mode.is("Filled"));
		fillOpacity.visibleWhen(() -> mode.is("Filled") || mode.is("Both"));
	}

	public EspBox box(AABB aabb, int baseRgb, double distSq) {
		return new EspBox(aabb, resolveColor(aabb, baseRgb, distSq), outlineOpacity.f(), fillOpacity.f(),
			thickness.f(), mode.index(), throughWalls.value);
	}

	private int resolveColor(AABB aabb, int base, double distSq) {
		long t = System.currentTimeMillis();
		switch (colorMode.index()) {
			case 1: { // Rainbow, offset by position so neighbours differ
				float h = ((t % 4000L) / 4000f + (float) (aabb.minX + aabb.minZ) * 0.01f) % 1f;
				if (h < 0) {
					h += 1f;
				}
				return ColorUtil.hsb(h, 0.75f, 1f);
			}
			case 2: { // Pulse
				float k = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(t / 300.0));
				return ColorUtil.scale(base, k);
			}
			case 3: { // Distance: fades toward a dim version of the base color
				float f = (float) (Math.sqrt(distSq) / Math.max(1.0, range.value));
				return ColorUtil.lerp(base, ColorUtil.scale(base, 0.3f), f);
			}
			default:
				return base;
		}
	}
}
