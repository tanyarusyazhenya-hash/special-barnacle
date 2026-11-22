package dev.aurora.render;

import net.minecraft.world.phys.Vec3;

import java.util.List;

public interface LineProvider {
	/** Called once per frame. start is a point just in front of the camera (the crosshair). */
	void collectLines(List<EspLine> out, Vec3 start);
}
