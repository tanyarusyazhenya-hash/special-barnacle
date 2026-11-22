package dev.aurora.util;

import dev.aurora.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers for the combat modules. Friends are never targeted. */
public final class CombatUtil {
	private CombatUtil() {}

	/** Distance from the viewer's eyes to the closest point of the target's hitbox. */
	public static double reach(Entity from, Entity to) {
		Vec3 eye = from.getEyePosition();
		AABB bb = to.getBoundingBox();
		double x = Mth.clamp(eye.x, bb.minX, bb.maxX);
		double y = Mth.clamp(eye.y, bb.minY, bb.maxY);
		double z = Mth.clamp(eye.z, bb.minZ, bb.maxZ);
		return eye.distanceTo(new Vec3(x, y, z));
	}

	public static List<LivingEntity> targets(Minecraft mc, double range, boolean players, boolean hostile,
											 boolean passive, boolean invisible) {
		List<LivingEntity> out = new ArrayList<>();
		LocalPlayer me = mc.player;
		if (me == null || mc.level == null) {
			return out;
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == me || !(e instanceof LivingEntity le) || !le.isAlive()) {
				continue;
			}
			if (le instanceof Player p) {
				if (!players || p.isSpectator() || ModuleManager.friends.isFriend(p)) {
					continue;
				}
				if (!invisible && p.isInvisible()) {
					continue;
				}
			} else if (le instanceof Enemy) {
				if (!hostile) {
					continue;
				}
			} else if (le instanceof Mob) {
				if (!passive) {
					continue;
				}
			} else {
				continue;
			}
			if (reach(me, le) <= range) {
				out.add(le);
			}
		}
		return out;
	}

	/** Closest non-friend player within range (by feet distance), or null. */
	public static AbstractClientPlayer nearestEnemyPlayer(Minecraft mc, double range) {
		if (mc.player == null || mc.level == null) {
			return null;
		}
		AbstractClientPlayer best = null;
		double bestDist = range * range;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player || p.isSpectator() || !p.isAlive() || ModuleManager.friends.isFriend(p)) {
				continue;
			}
			double d = mc.player.distanceToSqr(p);
			if (d <= bestDist) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	/** {yaw, pitch} that look from one point to another. */
	public static float[] rotationsTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f)};
	}

	/** Turn the real player toward a rotation, at most maxStep degrees per call. */
	public static void turn(LocalPlayer p, float[] target, float maxStep) {
		float dy = Mth.wrapDegrees(target[0] - p.getYRot());
		float dp = target[1] - p.getXRot();
		p.setYRot(p.getYRot() + Mth.clamp(dy, -maxStep, maxStep));
		p.setXRot(Mth.clamp(p.getXRot() + Mth.clamp(dp, -maxStep, maxStep), -90f, 90f));
	}

	/**
	 * Explosion damage estimate before armor and difficulty, ignoring blocks in the way, so it errs on
	 * the high side. A crystal has power 6, a respawn anchor power 5.
	 */
	public static double explosionDamage(Vec3 center, Entity e, double power) {
		double q = power * 2.0;
		double dist = Math.sqrt(e.distanceToSqr(center)) / q;
		if (dist > 1.0) {
			return 0.0;
		}
		double impact = 1.0 - dist;
		return (impact * impact + impact) / 2.0 * 7.0 * q + 1.0;
	}

	public static int findHotbar(LocalPlayer p, Item item) {
		for (int i = 0; i < 9; i++) {
			if (p.getInventory().getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	/** A hotbar slot that does not hold the item, preferring the selected one. */
	public static int findHotbarNot(LocalPlayer p, Item item) {
		int selected = p.getInventory().getSelectedSlot();
		if (!p.getInventory().getItem(selected).is(item)) {
			return selected;
		}
		for (int i = 0; i < 9; i++) {
			if (!p.getInventory().getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Makes sure the item can be used: offhand if it is there, otherwise the main hand (switching the
	 * hotbar slot if allowed). Returns the hand to use, or null if the item is not available.
	 */
	public static InteractionHand holdItem(LocalPlayer p, Item item, boolean allowSwitch) {
		if (p.getOffhandItem().is(item)) {
			return InteractionHand.OFF_HAND;
		}
		if (p.getMainHandItem().is(item)) {
			return InteractionHand.MAIN_HAND;
		}
		int slot = findHotbar(p, item);
		if (slot < 0 || !allowSwitch) {
			return null;
		}
		p.getInventory().setSelectedSlot(slot);
		return InteractionHand.MAIN_HAND;
	}
}
