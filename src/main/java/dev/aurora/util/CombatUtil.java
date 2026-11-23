package dev.aurora.util;

import dev.aurora.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers for the combat modules. Friends are never targeted. */
public final class CombatUtil {
	private static final EquipmentSlot[] ARMOR = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

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

	/** Closest non-friend player (and optionally hostile mob) within range, or null. */
	public static LivingEntity nearestTarget(Minecraft mc, double range, boolean players, boolean hostileMobs) {
		return nearestTarget(mc, range, players, hostileMobs, false);
	}

	public static LivingEntity nearestTarget(Minecraft mc, double range, boolean players, boolean hostileMobs,
											 boolean passiveMobs) {
		if (mc.player == null || mc.level == null) {
			return null;
		}
		LivingEntity best = null;
		double bestDist = range * range;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive()) {
				continue;
			}
			if (le instanceof Player p) {
				if (!players || p.isSpectator() || ModuleManager.friends.isFriend(p)) {
					continue;
				}
			} else if (le instanceof Enemy) {
				if (!hostileMobs) {
					continue;
				}
			} else if (le instanceof Mob) {
				if (!passiveMobs) {
					continue;
				}
			} else {
				continue;
			}
			double d = mc.player.distanceToSqr(le);
			if (d <= bestDist) {
				bestDist = d;
				best = le;
			}
		}
		return best;
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

	// ------------------------------------------------------------------ explosion damage

	/**
	 * Estimated damage an explosion (crystal power 6, respawn anchor power 5) deals to an entity. It
	 * follows the vanilla formula: distance falloff, how much of the hitbox is exposed (blocks in the
	 * way are traced), difficulty for players, armor, toughness, Protection / Blast Protection on the
	 * worn armor and the Resistance effect. Totems and absorption are not counted.
	 */
	public static double explosionDamage(Minecraft mc, Vec3 center, LivingEntity e, double power) {
		double q = power * 2.0;
		double dist = Math.sqrt(e.distanceToSqr(center)) / q;
		if (dist > 1.0 || mc.level == null) {
			return 0.0;
		}
		double exposure = exposure(mc, center, e);
		if (exposure <= 0.0) {
			return 0.0;
		}
		double impact = (1.0 - dist) * exposure;
		double damage = (impact * impact + impact) / 2.0 * 7.0 * q + 1.0;

		if (e instanceof Player) {
			damage = scaleForDifficulty(mc.level.getDifficulty(), damage);
		}

		// armor and toughness (same formula as the game)
		double armor = e.getArmorValue();
		double toughness = e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		double f = 2.0 + toughness / 4.0;
		double g = Mth.clamp(armor - damage / f, armor * 0.2, 20.0);
		damage *= 1.0 - g / 25.0;

		damage *= 1.0 - protectionPoints(mc, e) / 25.0;

		MobEffectInstance resistance = e.getEffect(MobEffects.RESISTANCE);
		if (resistance != null) {
			damage *= Math.max(0.0, 1.0 - (resistance.getAmplifier() + 1) * 0.2);
		}
		return Math.max(0.0, damage);
	}

	private static double scaleForDifficulty(Difficulty difficulty, double damage) {
		return switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> Math.min(damage / 2.0 + 1.0, damage);
			case NORMAL -> damage;
			case HARD -> damage * 1.5;
		};
	}

	/** Protection points from armor enchants, capped at 20: 2 per Blast Protection level, 1 per Protection level. */
	private static int protectionPoints(Minecraft mc, LivingEntity e) {
		var lookup = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> blast = lookup.getOrThrow(Enchantments.BLAST_PROTECTION);
		Holder<Enchantment> protection = lookup.getOrThrow(Enchantments.PROTECTION);
		int points = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = e.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			points += EnchantmentHelper.getItemEnchantmentLevel(blast, stack) * 2
				+ EnchantmentHelper.getItemEnchantmentLevel(protection, stack);
		}
		return Math.min(20, points);
	}

	/** Share of 9 sample points on the hitbox that the explosion can see without a block in the way. */
	private static double exposure(Minecraft mc, Vec3 center, LivingEntity e) {
		AABB bb = e.getBoundingBox();
		double w = bb.getXsize();
		double h = bb.getYsize();
		double cz = (bb.minZ + bb.maxZ) / 2.0;
		double[] xs = {bb.minX + w * 0.15, (bb.minX + bb.maxX) / 2.0, bb.maxX - w * 0.15};
		double[] ys = {bb.minY + h * 0.1, (bb.minY + bb.maxY) / 2.0, bb.maxY - h * 0.1};
		int clear = 0;
		for (double x : xs) {
			for (double y : ys) {
				BlockHitResult hit = mc.level.clip(
					new ClipContext(center, new Vec3(x, y, cz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
				if (hit.getType() == HitResult.Type.MISS) {
					clear++;
				}
			}
		}
		return clear / 9.0;
	}

	// ------------------------------------------------------------------ items

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
