package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspProvider;
import dev.aurora.render.EspStyle;
import dev.aurora.render.TracerSource;
import dev.aurora.render.TracerTarget;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Hostile mobs, passive mobs, dropped items and item frames. */
public class EntityESP extends Module implements EspProvider, TracerSource {
	private record Hit(AABB box, int rgb, double distSq) {}

	public final BoolSetting hostile = add(new BoolSetting("Hostile Mobs", true));
	public final BoolSetting passive = add(new BoolSetting("Passive Mobs", false));
	public final BoolSetting items = add(new BoolSetting("Dropped Items", true));
	public final BoolSetting frames = add(new BoolSetting("Item Frames", false));

	public final ColorSetting hostileColor = add(new ColorSetting("Hostile Color", 0xEF4444));
	public final ColorSetting passiveColor = add(new ColorSetting("Passive Color", 0x22C55E));
	public final ColorSetting itemColor = add(new ColorSetting("Item Color", 0xFACC15));
	public final ColorSetting frameColor = add(new ColorSetting("Frame Color", 0x60A5FA));
	public final EspStyle style;

	public EntityESP() {
		super("EntityESP", "Highlights mobs, dropped items and item frames", Category.RENDER);
		style = new EspStyle(this);
		style.range.set(64);
		hostileColor.visibleWhen(() -> hostile.value);
		passiveColor.visibleWhen(() -> passive.value);
		itemColor.visibleWhen(() -> items.value);
		frameColor.visibleWhen(() -> frames.value);
	}

	@Override
	public String info() {
		return style.mode.get();
	}

	private List<Hit> gather() {
		List<Hit> hits = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return hits;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double r2 = style.range.value * style.range.value;

		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || e instanceof Player || e.isRemoved()) {
				continue;
			}
			int rgb;
			if (e instanceof Enemy) {
				if (!hostile.value) continue;
				rgb = hostileColor.rgb();
			} else if (e instanceof Mob) {
				if (!passive.value) continue;
				rgb = passiveColor.rgb();
			} else if (e instanceof ItemEntity) {
				if (!items.value) continue;
				rgb = itemColor.rgb();
			} else if (e instanceof ItemFrame) {
				if (!frames.value) continue;
				rgb = frameColor.rgb();
			} else {
				continue;
			}

			double distSq = mc.player.distanceToSqr(e);
			if (distSq > r2) {
				continue;
			}
			double dx = Mth.lerp(partial, e.xo, e.getX()) - e.getX();
			double dy = Mth.lerp(partial, e.yo, e.getY()) - e.getY();
			double dz = Mth.lerp(partial, e.zo, e.getZ()) - e.getZ();
			hits.add(new Hit(e.getBoundingBox().move(dx, dy, dz).inflate(0.02), rgb, distSq));
		}
		return hits;
	}

	@Override
	public void collect(List<EspBox> out) {
		for (Hit h : gather()) {
			out.add(style.box(h.box(), h.rgb(), h.distSq()));
		}
	}

	@Override
	public void tracerTargets(List<TracerTarget> out) {
		for (Hit h : gather()) {
			out.add(new TracerTarget(h.box().getCenter(), h.rgb()));
		}
	}
}
