package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.CombatUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mace smash attacks. The mace deals more damage the further you fall, so this module first gets you
 * up in the air, then holds the mace, turns toward the target while you come down and hits it once
 * you are falling and in reach.
 * <p>
 * Launch: Wind Charge (throws one at your feet, needs wind charges in the hotbar) or None (you get
 * the height yourself, for example by falling from a ledge). It presses your forward key while
 * airborne to drift toward the target. Friends and group members are never hit. Off by default every
 * launch. Many servers ban this; it is not tested against any anti-cheat.
 */
public class MaceAura extends Module {
	public final ModeSetting launch = add(new ModeSetting("Launch", "Wind Charge", "Wind Charge", "None"));
	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final BoolSetting hostile = add(new BoolSetting("Hostile Mobs", false));
	public final NumberSetting range = add(new NumberSetting("Attack Range", 3.0, 2.0, 5.0, 0.1));
	public final NumberSetting launchRange = add(new NumberSetting("Launch Range", 6, 3, 12, 1));
	public final NumberSetting minFall = add(new NumberSetting("Min Fall Distance", 2.5, 1.5, 20, 0.5));
	public final NumberSetting launchDelay = add(new NumberSetting("Launch Delay (ticks)", 20, 5, 60, 1));
	public final BoolSetting driftToTarget = add(new BoolSetting("Drift To Target", true));
	public final BoolSetting autoSwitch = add(new BoolSetting("Auto Switch Items", true));
	public final NumberSetting turnSpeed = add(new NumberSetting("Turn Speed", 45, 10, 180, 5));
	public final NumberSetting aimNoise = add(new NumberSetting("Aim Noise (deg)", 0.5, 0.0, 3.0, 0.1));
	public final BoolSetting requireCrosshair = add(new BoolSetting("Require Crosshair On Hitbox", true));
	public final BoolSetting randomDelay = add(new BoolSetting("Random Attack Delay", true));

	private int launchCooldown;
	private int attackDelay;
	private String status = "Idle";

	public MaceAura() {
		super("MaceAura", "Launches up and smashes targets with the mace", Category.COMBAT);
		launchRange.visibleWhen(() -> launch.is("Wind Charge"));
		launchDelay.visibleWhen(() -> launch.is("Wind Charge"));
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	public String info() {
		return status;
	}

	@Override
	protected void onDisable() {
		KeyMapping.setAll();
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null || mc.screen != null || !p.isAlive()
			|| ModuleManager.freecam.isEnabled()) {
			return;
		}
		if (launchCooldown > 0) {
			launchCooldown--;
		}
		if (attackDelay > 0) {
			attackDelay--;
		}
		double scan = Math.max(range.value, launch.is("Wind Charge") ? launchRange.value : range.value) + 2;
		List<LivingEntity> list = CombatUtil.targets(mc, scan, players.value, hostile.value, false, true);
		if (list.isEmpty()) {
			status = "No target";
			return;
		}
		list.sort(Comparator.comparingDouble(e -> CombatUtil.reach(p, e)));
		LivingEntity target = list.get(0);

		if (CombatUtil.findHotbar(p, Items.MACE) < 0 && !p.getMainHandItem().is(Items.MACE)) {
			status = "No mace in hotbar";
			return;
		}

		boolean grounded = p.onGround() && !p.isInWater();
		if (grounded) {
			if (!launch.is("Wind Charge")) {
				status = "Waiting to fall";
				return;
			}
			if (CombatUtil.reach(p, target) > launchRange.value) {
				status = "Target too far";
				return;
			}
			if (launchCooldown > 0) {
				status = "Launch cooldown";
				return;
			}
			if (CombatUtil.findHotbar(p, Items.WIND_CHARGE) < 0 && !p.getOffhandItem().is(Items.WIND_CHARGE)) {
				status = "No wind charges";
				return;
			}
			InteractionHand hand = CombatUtil.holdItem(p, Items.WIND_CHARGE, autoSwitch.value);
			if (hand == null) {
				status = "Hold wind charge";
				return;
			}
			p.setXRot(90f);
			mc.gameMode.useItem(p, hand);
			p.swing(hand);
			launchCooldown = launchDelay.i();
			status = "Launching";
			return;
		}

		// airborne
		if (!p.getMainHandItem().is(Items.MACE)) {
			int slot = CombatUtil.findHotbar(p, Items.MACE);
			if (slot < 0 || !autoSwitch.value) {
				status = "Hold the mace";
				return;
			}
			p.getInventory().setSelectedSlot(slot);
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		Vec3 eye = p.getEyePosition();
		AABB box = target.getBoundingBox();
		float[] rot = CombatUtil.rotationsTo(eye, box.getCenter());
		rot[0] += (float) ((rnd.nextDouble() - 0.5) * 2.0 * aimNoise.value);
		rot[1] += (float) ((rnd.nextDouble() - 0.5) * 2.0 * aimNoise.value);
		CombatUtil.turn(p, rot, turnSpeed.f());

		if (driftToTarget.value) {
			KeyMapping.setAll();
			double dx = target.getX() - p.getX();
			double dz = target.getZ() - p.getZ();
			if (Math.hypot(dx, dz) > 1.2) {
				mc.options.keyUp.setDown(true);
			}
		}

		boolean falling = p.getDeltaMovement().y < -0.05;
		if (!falling) {
			status = "Rising";
			return;
		}
		if (p.fallDistance < minFall.value) {
			status = "Falling (" + String.format("%.1f", p.fallDistance) + ")";
			return;
		}
		if (CombatUtil.reach(p, target) > range.value) {
			status = "Falling, out of reach";
			return;
		}
		if (requireCrosshair.value) {
			Optional<Vec3> hit = box.clip(eye, eye.add(p.getLookAngle().scale(range.value)));
			if (hit.isEmpty()) {
				status = "Aiming";
				return;
			}
		}
		if (attackDelay > 0) {
			return;
		}
		mc.gameMode.attack(p, target);
		p.swing(InteractionHand.MAIN_HAND);
		attackDelay = randomDelay.value ? 2 + rnd.nextInt(3) : 0;
		status = "Smash!";
	}
}
