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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
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
	public final ModeSetting launch = add(new ModeSetting("Launch", "Wind Charge", "Wind Charge", "Elytra", "None"));
	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final BoolSetting hostile = add(new BoolSetting("Hostile Mobs", false));
	public final NumberSetting range = add(new NumberSetting("Attack Range", 3.0, 2.0, 5.0, 0.1));
	public final NumberSetting launchRange = add(new NumberSetting("Launch Range", 6, 3, 12, 1));
	public final NumberSetting climbHeight = add(new NumberSetting("Elytra Climb Height", 30, 10, 100, 5));
	public final BoolSetting stunSlam = add(new BoolSetting("Stun Slam (axe vs shield)", true));
	public final BoolSetting swapChest = add(new BoolSetting("Swap Chestplate For Hit", true));
	public final NumberSetting minFall = add(new NumberSetting("Min Fall Distance", 2.5, 1.5, 20, 0.5));
	public final NumberSetting launchDelay = add(new NumberSetting("Launch Delay (ticks)", 20, 5, 60, 1));
	public final BoolSetting driftToTarget = add(new BoolSetting("Drift To Target", true));
	public final BoolSetting autoSwitch = add(new BoolSetting("Auto Switch Items", true));
	public final NumberSetting turnSpeed = add(new NumberSetting("Turn Speed", 30, 10, 180, 5));
	public final NumberSetting aimNoise = add(new NumberSetting("Aim Noise (deg)", 0.5, 0.0, 3.0, 0.1));
	public final BoolSetting groundAttack = add(new BoolSetting("Attack On Ground", true));
	public final BoolSetting requireCrosshair = add(new BoolSetting("Require Crosshair On Hitbox", true));
	public final BoolSetting randomDelay = add(new BoolSetting("Random Attack Delay", true));

	private int launchCooldown;
	private int attackDelay;
	private String status = "Idle";

	public MaceAura() {
		super("MaceAura", "Launches up and smashes targets with the mace", Category.COMBAT);
		launchRange.visibleWhen(() -> launch.is("Wind Charge"));
		launchDelay.visibleWhen(() -> launch.is("Wind Charge"));
		climbHeight.visibleWhen(() -> launch.is("Elytra"));
		swapChest.visibleWhen(() -> launch.is("Elytra"));
		minFall.visibleWhen(() -> !launch.is("Elytra"));
		driftToTarget.visibleWhen(() -> !launch.is("Elytra"));
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
		if (swapped) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && mc.gameMode != null) {
				swapArmor(mc, mc.player, elytraSlot);
			}
			swapped = false;
		}
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
		double scan = launch.is("Elytra") ? 48 : Math.max(range.value, launch.is("Wind Charge") ? launchRange.value : range.value) + 2;
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

		if (launch.is("Elytra")) {
			elytra(mc, p, target);
			return;
		}
		boolean grounded = p.onGround() && !p.isInWater();
		if (grounded) {
			if (!launch.is("Wind Charge")) {
				status = "Waiting to fall";
				groundHit(mc, p, target);
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
				groundHit(mc, p, target);
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
		aim(p, rot);

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
		if (stunFirst(mc, p, target)) {
			return;
		}
		mc.gameMode.attack(p, target);
		p.swing(InteractionHand.MAIN_HAND);
		attackDelay = randomDelay.value ? 2 + rnd.nextInt(3) : 0;
		status = "Smash!";
	}

	private int rocketCd;
	private boolean jumpPulse;
	private boolean diving;

	/**
	 * Elytra mode: jump, open the elytra, climb with fireworks until Elytra Climb Height above the
	 * target, then dive onto it with the mace. Needs an elytra worn, fireworks and a mace in the hotbar.
	 * The smash bonus depends on how the server counts fall distance while gliding.
	 */
	private void elytra(Minecraft mc, LocalPlayer p, LivingEntity target) {
		KeyMapping.setAll();
		if (swapped) {
			swappedHit(mc, p, target);
			return;
		}
		if (!p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
			status = "Wear an elytra";
			return;
		}
		if (rocketCd > 0) {
			rocketCd--;
		}
		if (!p.isFallFlying()) {
			diving = false;
			if (p.onGround()) {
				mc.options.keyJump.setDown(true);
				status = "Taking off";
			} else if (p.getDeltaMovement().y < 0 && !p.isInWater()) {
				jumpPulse = !jumpPulse;
				if (jumpPulse) {
					mc.options.keyJump.setDown(true);
				}
				status = "Opening elytra";
			}
			return;
		}
		Vec3 eye = p.getEyePosition();
		AABB box = target.getBoundingBox();
		double dist = CombatUtil.reach(p, target);
		if (p.getY() >= target.getY() + climbHeight.value) {
			diving = true;
		}
		if (!diving) {
			float[] rot = CombatUtil.rotationsTo(eye, box.getCenter());
			rot[1] = -55f;
			aim(p, rot);
			if (rocketCd <= 0) {
				InteractionHand hand = CombatUtil.holdItem(p, Items.FIREWORK_ROCKET, autoSwitch.value);
				if (hand == null) {
					status = "No fireworks";
					return;
				}
				mc.gameMode.useItem(p, hand);
				p.swing(hand);
				rocketCd = 22;
			}
			status = "Climbing";
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		float[] rot = CombatUtil.rotationsTo(eye, box.getCenter());
		rot[0] += (float) ((rnd.nextDouble() - 0.5) * 2.0 * aimNoise.value);
		rot[1] += (float) ((rnd.nextDouble() - 0.5) * 2.0 * aimNoise.value);
		aim(p, rot);
		status = "Diving";
		if (dist > 14) {
			return;
		}
		if (swapChest.value) {
			int cs = findChestplate(p);
			if (cs >= 0) {
				if (dist <= range.value + 2.5) {
					swapArmor(mc, p, cs);
					swapped = true;
					elytraSlot = cs;
					swapTicks = 0;
					status = "Elytra off";
				}
				return;
			}
			status = "No chestplate in hotbar";
		}
		if (!p.getMainHandItem().is(Items.MACE)) {
			int slot = CombatUtil.findHotbar(p, Items.MACE);
			if (slot < 0 || !autoSwitch.value) {
				status = "Hold the mace";
				return;
			}
			p.getInventory().setSelectedSlot(slot);
		}
		if (dist > range.value || attackDelay > 0) {
			return;
		}
		if (requireCrosshair.value && box.clip(eye, eye.add(p.getLookAngle().scale(range.value))).isEmpty()) {
			return;
		}
		if (stunFirst(mc, p, target)) {
			return;
		}
		mc.gameMode.attack(p, target);
		p.swing(InteractionHand.MAIN_HAND);
		attackDelay = randomDelay.value ? 2 + rnd.nextInt(3) : 0;
		diving = false;
		status = "Smash!";
	}

	/** Eased turning: covers part of the remaining angle each tick, capped by Turn Speed, so aim never snaps. */
	private void aim(LocalPlayer p, float[] rot) {
		float cap = turnSpeed.f();
		float dy = net.minecraft.util.Mth.wrapDegrees(rot[0] - p.getYRot());
		float dp = rot[1] - p.getXRot();
		p.setYRot(p.getYRot() + net.minecraft.util.Mth.clamp(dy * 0.4f, -cap, cap));
		p.setXRot(net.minecraft.util.Mth.clamp(p.getXRot() + net.minecraft.util.Mth.clamp(dp * 0.4f, -cap, cap), -90f, 90f));
	}

	/** Stun slam: a target that is blocking with a shield gets an axe hit first (it disables the shield), the mace follows. */
	private boolean stunFirst(Minecraft mc, LocalPlayer p, LivingEntity target) {
		if (!stunSlam.value || !(target instanceof net.minecraft.world.entity.player.Player pl) || !pl.isBlocking()) {
			return false;
		}
		for (int i = 0; i < 9; i++) {
			if (p.getInventory().getItem(i).is(ItemTags.AXES)) {
				p.getInventory().setSelectedSlot(i);
				mc.gameMode.attack(p, target);
				p.swing(InteractionHand.MAIN_HAND);
				attackDelay = 1;
				status = "Stun!";
				return true;
			}
		}
		return false;
	}

	private boolean swapped;
	private int elytraSlot;
	private int swapTicks;

	private static int findChestplate(LocalPlayer p) {
		for (int i = 0; i < 9; i++) {
			ItemStack st = p.getInventory().getItem(i);
			if (st.is(ItemTags.CHEST_ARMOR) && !st.is(Items.ELYTRA)) {
				return i;
			}
		}
		return -1;
	}

	/** Swaps the worn chest item with a hotbar slot (the hotkey swap of the inventory screen). */
	private void swapArmor(Minecraft mc, LocalPlayer p, int hotbar) {
		mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, 6, hotbar, ClickType.SWAP, p);
	}

	/** The elytra is off, so gliding has ended and the mace can smash: hit, then put the elytra back on. */
	private void swappedHit(Minecraft mc, LocalPlayer p, LivingEntity target) {
		swapTicks++;
		Vec3 eye = p.getEyePosition();
		AABB box = target.getBoundingBox();
		double dist = CombatUtil.reach(p, target);
		boolean done = false;
		if (swapTicks >= 2 && dist <= range.value) {
			int slot = CombatUtil.findHotbar(p, Items.MACE);
			if (slot >= 0) {
				p.getInventory().setSelectedSlot(slot);
			}
			float[] rot = CombatUtil.rotationsTo(eye, box.getCenter());
			aim(p, rot);
			if (p.getMainHandItem().is(Items.MACE)) {
				if (stunFirst(mc, p, target)) {
					return;
				}
				mc.gameMode.attack(p, target);
				p.swing(InteractionHand.MAIN_HAND);
				status = "Smash!";
				done = true;
			}
		} else {
			aim(p, CombatUtil.rotationsTo(eye, box.getCenter()));
		}
		if (done || swapTicks > 8 || dist > range.value + 4 || p.onGround()) {
			swapArmor(mc, p, elytraSlot);
			swapped = false;
			diving = false;
			rocketCd = 6;
			if (!done) {
				status = "Missed, elytra back on";
			}
		}
	}

	/** Plain mace hit when you are on the ground (no smash bonus, but it still hits). */
	private void groundHit(Minecraft mc, LocalPlayer p, LivingEntity target) {
		if (!groundAttack.value || CombatUtil.reach(p, target) > range.value) {
			return;
		}
		if (!p.getMainHandItem().is(Items.MACE)) {
			int slot = CombatUtil.findHotbar(p, Items.MACE);
			if (slot < 0 || !autoSwitch.value) {
				return;
			}
			p.getInventory().setSelectedSlot(slot);
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		float[] rot = CombatUtil.rotationsTo(p.getEyePosition(), target.getBoundingBox().getCenter());
		rot[0] += (float) ((rnd.nextDouble() - 0.5) * 2.0 * aimNoise.value);
		rot[1] += (float) ((rnd.nextDouble() - 0.5) * 2.0 * aimNoise.value);
		aim(p, rot);
		if (requireCrosshair.value) {
			Vec3 eye = p.getEyePosition();
			if (target.getBoundingBox().clip(eye, eye.add(p.getLookAngle().scale(range.value))).isEmpty()) {
				return;
			}
		}
		if (attackDelay > 0 || p.getAttackStrengthScale(0.5f) < 0.92f) {
			return;
		}
		if (stunFirst(mc, p, target)) {
			return;
		}
		mc.gameMode.attack(p, target);
		p.swing(InteractionHand.MAIN_HAND);
		attackDelay = randomDelay.value ? 2 + rnd.nextInt(3) : 0;
		status = "Hit";
	}
}
