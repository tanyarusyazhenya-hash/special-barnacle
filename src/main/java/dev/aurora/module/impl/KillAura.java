package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.CombatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Attacks the best target in reach. Friends and group members are never attacked.
 * <p>
 * The Preset makes it behave closer to a real player: a limited field of view, rotation toward a
 * slowly drifting point inside the hitbox with a little noise and mouse-sensitivity steps, attacks
 * only when the crosshair really is on the hitbox, a slightly random attack timing and occasional
 * missed swings. Presets are tuned guesses and are NOT tested against any particular anti-cheat; no
 * setting makes it undetectable, and staff can still see it. Off by default every launch.
 */
public class KillAura extends Module {
	/** Effective numbers after the preset is applied. */
	private record Profile(double range, double cdMin, double speed, double jitter, double fov,
						   double miss, int extraDelay, boolean lookAt, boolean walls) {}

	public final ModeSetting preset = add(new ModeSetting("Preset", "Legit", "Legit", "Strict", "Grim", "Custom"));
	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final BoolSetting hostile = add(new BoolSetting("Hostile Mobs", false));
	public final BoolSetting passive = add(new BoolSetting("Passive Mobs", false));
	public final BoolSetting invisible = add(new BoolSetting("Invisible Players", true));
	public final ModeSetting sort = add(new ModeSetting("Priority", "Distance", "Distance", "Health"));
	public final BoolSetting weaponOnly = add(new BoolSetting("Only With Weapon", true));
	public final BoolSetting whileUsing = add(new BoolSetting("Attack While Using Item", false));

	// Custom preset values
	public final NumberSetting range = add(new NumberSetting("Range", 3.0, 2.0, 6.0, 0.1));
	public final NumberSetting cooldown = add(new NumberSetting("Min Cooldown", 0.92, 0.5, 1.0, 0.01));
	public final NumberSetting rotationSpeed = add(new NumberSetting("Turn Speed", 40, 5, 180, 5));
	public final NumberSetting jitter = add(new NumberSetting("Aim Noise (deg)", 0.5, 0.0, 3.0, 0.1));
	public final NumberSetting fov = add(new NumberSetting("Field Of View", 120, 30, 360, 10));
	public final NumberSetting missChance = add(new NumberSetting("Miss Chance %", 3, 0, 30, 1));
	public final NumberSetting extraDelay = add(new NumberSetting("Random Delay (ticks)", 1, 0, 5, 1));
	public final BoolSetting lookAt = add(new BoolSetting("Require Crosshair On Hitbox", true));
	public final BoolSetting throughWalls = add(new BoolSetting("Through Walls", false));

	private LivingEntity lastTarget;
	private Vec3 aimOffset = Vec3.ZERO;
	private int offsetTicks;
	private int attackDelay;
	private double needed = 1.0;

	public KillAura() {
		super("KillAura", "Attacks targets in reach, with human-like presets", Category.COMBAT);
		for (var s : new dev.aurora.setting.Setting[]{range, cooldown, rotationSpeed, jitter, fov, missChance,
			extraDelay, lookAt, throughWalls}) {
			s.visibleWhen(() -> preset.is("Custom"));
		}
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	public String info() {
		return preset.get();
	}

	private Profile profile() {
		return switch (preset.get()) {
			case "Strict" -> new Profile(2.9, 1.0, 22, 0.8, 70, 5, 2, true, false);
			case "Grim" -> new Profile(2.95, 1.0, 38, 0.4, 90, 2, 1, true, false);
			case "Custom" -> new Profile(range.value, cooldown.value, rotationSpeed.value, jitter.value, fov.value,
				missChance.value / 100.0, extraDelay.i(), lookAt.value, throughWalls.value);
			default -> new Profile(3.0, 0.95, 32, 0.6, 110, 3, 1, true, false);
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null || mc.screen != null || !p.isAlive()) {
			return;
		}
		if (ModuleManager.freecam.isEnabled()) {
			return;
		}
		if (p.isUsingItem() && !whileUsing.value) {
			return;
		}
		if (weaponOnly.value && !isWeapon(p.getMainHandItem())) {
			return;
		}
		Profile pr = profile();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double missProb = pr.miss() > 1.0 ? pr.miss() / 100.0 : pr.miss();

		List<LivingEntity> list = CombatUtil.targets(mc, pr.range(), players.value, hostile.value, passive.value,
			invisible.value);
		if (!pr.walls()) {
			list.removeIf(e -> !p.hasLineOfSight(e));
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		list.removeIf(e -> {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye).normalize();
			double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(to), -1.0, 1.0)));
			return angle > pr.fov() / 2.0;
		});
		if (list.isEmpty()) {
			lastTarget = null;
			return;
		}
		if (sort.is("Health")) {
			list.sort(Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount()));
		} else {
			list.sort(Comparator.comparingDouble(e -> CombatUtil.reach(p, e)));
		}
		LivingEntity target = list.get(0);
		AABB box = target.getBoundingBox();

		if (target != lastTarget || offsetTicks-- <= 0) {
			lastTarget = target;
			offsetTicks = 8 + rnd.nextInt(12);
			aimOffset = new Vec3(
				(rnd.nextDouble() - 0.5) * box.getXsize() * 0.6,
				(rnd.nextDouble() - 0.35) * box.getYsize() * 0.5,
				(rnd.nextDouble() - 0.5) * box.getZsize() * 0.6);
		}

		if (!preset.is("Custom") || pr.speed() < 360) {
			Vec3 aim = box.getCenter().add(aimOffset);
			aim = new Vec3(Mth.clamp(aim.x, box.minX, box.maxX), Mth.clamp(aim.y, box.minY, box.maxY),
				Mth.clamp(aim.z, box.minZ, box.maxZ));
			float[] rot = CombatUtil.rotationsTo(eye, aim);
			rot[0] += (float) ((rnd.nextDouble() - 0.5) * 2.0 * pr.jitter());
			rot[1] += (float) ((rnd.nextDouble() - 0.5) * 2.0 * pr.jitter());
			float step = (float) (pr.speed() * (0.7 + rnd.nextDouble() * 0.4));
			turnHuman(mc, p, rot, step);
		}

		if (attackDelay > 0) {
			attackDelay--;
			return;
		}
		if (pr.lookAt()) {
			Vec3 end = eye.add(p.getLookAngle().scale(pr.range()));
			Optional<Vec3> hit = box.clip(eye, end);
			if (hit.isEmpty()) {
				return;
			}
		}
		if (p.getAttackStrengthScale(0.5f) < needed) {
			return;
		}
		needed = Math.min(1.0, pr.cdMin() + rnd.nextDouble() * (1.0 - pr.cdMin()));
		attackDelay = pr.extraDelay() > 0 ? rnd.nextInt(pr.extraDelay() + 1) : 0;
		if (rnd.nextDouble() < missProb) {
			attackDelay += 1;
			return;
		}
		mc.gameMode.attack(p, target);
		p.swing(InteractionHand.MAIN_HAND);
	}

	/** Turns toward the target by at most step degrees, in steps a real mouse at your sensitivity could make. */
	private static void turnHuman(Minecraft mc, LocalPlayer p, float[] target, float step) {
		double s = mc.options.sensitivity().get() * 0.6 + 0.2;
		double gcd = s * s * s * 8.0 * 0.15;
		float dy = Mth.wrapDegrees(target[0] - p.getYRot());
		float dp = target[1] - p.getXRot();
		// ease out: slow down when close
		float ky = Mth.clamp(Math.abs(dy) / 30f, 0.25f, 1f);
		float kp = Mth.clamp(Math.abs(dp) / 30f, 0.25f, 1f);
		float my = Mth.clamp(dy, -step * ky, step * ky);
		float mp = Mth.clamp(dp, -step * kp, step * kp);
		my = (float) (Math.round(my / gcd) * gcd);
		mp = (float) (Math.round(mp / gcd) * gcd);
		p.setYRot(p.getYRot() + my);
		p.setXRot(Mth.clamp(p.getXRot() + mp, -90f, 90f));
	}

	private static boolean isWeapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT);
	}
}
