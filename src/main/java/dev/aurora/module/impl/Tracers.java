package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.render.EspLine;
import dev.aurora.render.LineProvider;
import dev.aurora.render.TracerSource;
import dev.aurora.render.TracerTarget;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Lines from the crosshair to players, storage, spawners, entities and waypoints. */
public class Tracers extends Module implements LineProvider {
	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final BoolSetting storage = add(new BoolSetting("Storage", true));
	public final BoolSetting spawners = add(new BoolSetting("Spawners", true));
	public final BoolSetting entities = add(new BoolSetting("Entities", false));
	public final BoolSetting waypoints = add(new BoolSetting("Waypoints", false));
	public final ModeSetting colorMode = add(new ModeSetting("Color", "Target", "Target", "Static"));
	public final ColorSetting staticColor = add(new ColorSetting("Static Color", 0xFFFFFF));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.8, 0.05, 1.0, 0.05));
	public final NumberSetting width = add(new NumberSetting("Width", 1.5, 0.5, 4.0, 0.25));
	public final NumberSetting playerRange = add(new NumberSetting("Player Range", 256, 16, 512, 16));
	public final BoolSetting throughWalls = add(new BoolSetting("Through Walls", true));

	public Tracers() {
		super("Tracers", "Lines from your crosshair to targets", Category.RENDER);
		staticColor.visibleWhen(() -> colorMode.is("Static"));
		playerRange.visibleWhen(() -> players.value);
	}

	@Override
	public void collectLines(List<EspLine> out, Vec3 start) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		List<TracerTarget> targets = new ArrayList<>();

		if (players.value) {
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			double r2 = playerRange.value * playerRange.value;
			int rgb = ModuleManager.playerEsp.color.rgb();
			for (AbstractClientPlayer p : mc.level.players()) {
				if (p == mc.player || p.isSpectator() || mc.player.distanceToSqr(p) > r2) {
					continue;
				}
				Vec3 pos = new Vec3(
					Mth.lerp(partial, p.xo, p.getX()),
					Mth.lerp(partial, p.yo, p.getY()) + p.getBbHeight() * 0.5,
					Mth.lerp(partial, p.zo, p.getZ()));
				targets.add(new TracerTarget(pos, rgb));
			}
		}
		addFrom(storage.value, ModuleManager.storageFinder, ModuleManager.storageFinder.isEnabled(), targets);
		addFrom(spawners.value, ModuleManager.spawnerFinder, ModuleManager.spawnerFinder.isEnabled(), targets);
		addFrom(entities.value, ModuleManager.entityEsp, ModuleManager.entityEsp.isEnabled(), targets);
		addFrom(waypoints.value, ModuleManager.waypoints, ModuleManager.waypoints.isEnabled(), targets);

		for (TracerTarget t : targets) {
			int rgb = colorMode.is("Static") ? staticColor.rgb() : t.rgb();
			out.add(new EspLine(start, t.pos(), rgb, opacity.f(), width.f(), throughWalls.value));
		}
	}

	private static void addFrom(boolean wanted, TracerSource source, boolean sourceEnabled, List<TracerTarget> out) {
		if (wanted && sourceEnabled) {
			source.tracerTargets(out);
		}
	}
}
