package dev.aurora.render;

import net.minecraft.world.phys.Vec3;

/** A point a tracer can point at. */
public record TracerTarget(Vec3 pos, int rgb) {
}
