package dev.aurora.render;

import net.minecraft.world.phys.Vec3;

/**
 * A straight line in world space, drawn as a camera-facing ribbon.
 *
 * @param thickness approximate width in screen pixels
 */
public record EspLine(Vec3 from, Vec3 to, int rgb, float alpha, float thickness, boolean throughWalls) {
}
