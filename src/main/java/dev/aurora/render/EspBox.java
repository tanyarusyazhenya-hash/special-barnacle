package dev.aurora.render;

import net.minecraft.world.phys.AABB;

/**
 * One box to draw this frame, with its style already resolved.
 *
 * @param mode 0 = Outline, 1 = Filled, 2 = Both, 3 = Corners
 */
public record EspBox(AABB aabb, int rgb, float outlineAlpha, float fillAlpha, float thickness, int mode,
					 boolean throughWalls) {
}
