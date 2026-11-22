package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.CombatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Respawn anchor PvP: places an anchor next to the nearest enemy player, charges it with glowstone
 * and detonates it. Anchors only explode outside the Nether, so the module does nothing there.
 * Damage is estimated without armor and without blocks in the way. Friends are never targeted.
 * Off by default every launch. Most servers ban this.
 */
public class AutoAnchor extends Module {
	public final NumberSetting targetRange = add(new NumberSetting("Target Range", 8, 3, 12, 1));
	public final NumberSetting placeRange = add(new NumberSetting("Place Range", 4.5, 2.0, 6.0, 0.5));
	public final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 2, 0, 10, 1));
	public final NumberSetting minDamage = add(new NumberSetting("Min Target Damage", 4, 1, 20, 1));
	public final NumberSetting maxSelf = add(new NumberSetting("Max Self Damage", 6, 1, 20, 1));
	public final BoolSetting antiSuicide = add(new BoolSetting("Anti Suicide", true));
	public final BoolSetting autoSwitch = add(new BoolSetting("Auto Switch", true));
	public final BoolSetting rotate = add(new BoolSetting("Rotate", false));

	private static final double POWER = 5.0;

	private int timer = 100;

	public AutoAnchor() {
		super("AutoAnchor", "Places, charges and detonates respawn anchors", Category.COMBAT);
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	protected void onEnable() {
		timer = 100;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null || mc.screen != null || !p.isAlive()) {
			return;
		}
		if (ModuleManager.freecam.isEnabled() || mc.level.dimension() == Level.NETHER) {
			return;
		}
		timer = Math.min(timer + 1, 100);
		if (timer < delay.i()) {
			return;
		}
		AbstractClientPlayer target = CombatUtil.nearestEnemyPlayer(mc, targetRange.value);
		if (target == null) {
			return;
		}
		double selfHp = p.getHealth() + p.getAbsorptionAmount();
		Vec3 eye = p.getEyePosition();

		// 1. an anchor is already next to the target: charge it, then detonate it
		BlockPos t = target.blockPosition();
		BlockPos anchor = null;
		boolean anyAnchor = false;
		double bestScore = -1e9;
		for (int dx = -4; dx <= 4; dx++) {
			for (int dy = -3; dy <= 4; dy++) {
				for (int dz = -4; dz <= 4; dz++) {
					BlockPos pos = t.offset(dx, dy, dz);
					if (!mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
						continue;
					}
					Vec3 center = Vec3.atCenterOf(pos);
					if (eye.distanceTo(center) > placeRange.value) {
						continue;
					}
					anyAnchor = true;
					double dmgTarget = CombatUtil.explosionDamage(center, target, POWER);
					double dmgSelf = CombatUtil.explosionDamage(center, p, POWER);
					if (dmgTarget < minDamage.value || !selfSafe(dmgSelf, selfHp)) {
						continue;
					}
					if (dmgTarget - dmgSelf > bestScore) {
						bestScore = dmgTarget - dmgSelf;
						anchor = pos;
					}
				}
			}
		}
		if (anchor != null) {
			if (useAnchor(mc, p, anchor)) {
				timer = 0;
			}
			return;
		}
		if (anyAnchor) {
			return; // an anchor is there but not safe to use right now: do not stack more
		}

		// 2. nothing placed yet: put an anchor next to the target
		if (placeAnchor(mc, p, target, selfHp, eye)) {
			timer = 0;
		}
	}

	private boolean selfSafe(double dmgSelf, double selfHp) {
		return dmgSelf <= maxSelf.value && !(antiSuicide.value && dmgSelf >= selfHp - 1.0);
	}

	private boolean useAnchor(Minecraft mc, LocalPlayer p, BlockPos pos) {
		BlockState state = mc.level.getBlockState(pos);
		int charge = state.getValue(RespawnAnchorBlock.CHARGE);
		Vec3 hitVec = Vec3.atCenterOf(pos).add(0, 0.5, 0);
		BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
		InteractionHand hand;

		if (charge == 0) {
			hand = CombatUtil.holdItem(p, Items.GLOWSTONE, autoSwitch.value);
			if (hand == null) {
				return false;
			}
		} else {
			// detonate with anything that is not glowstone
			if (p.getMainHandItem().is(Items.GLOWSTONE)) {
				int slot = CombatUtil.findHotbarNot(p, Items.GLOWSTONE);
				if (slot < 0 || !autoSwitch.value) {
					return false;
				}
				p.getInventory().setSelectedSlot(slot);
			}
			hand = InteractionHand.MAIN_HAND;
		}
		look(p, hitVec);
		mc.gameMode.useItemOn(p, hand, hit);
		p.swing(hand);
		return true;
	}

	private boolean placeAnchor(Minecraft mc, LocalPlayer p, AbstractClientPlayer target, double selfHp, Vec3 eye) {
		BlockPos t = target.blockPosition();
		BlockPos bestSupport = null;
		Direction bestFace = null;
		Vec3 bestHit = null;
		double bestScore = -1e9;

		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 0; dy <= 3; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos pos = t.offset(dx, dy, dz);
					if (!mc.level.getBlockState(pos).isAir()) {
						continue;
					}
					if (!mc.level.getEntities(null, new AABB(pos)).isEmpty()) {
						continue;
					}
					BlockPos support = null;
					Direction face = null;
					for (Direction d : Direction.values()) {
						BlockPos n = pos.relative(d);
						BlockState ns = mc.level.getBlockState(n);
						if (!ns.isAir() && ns.getFluidState().isEmpty()) {
							support = n;
							face = d.getOpposite();
							break;
						}
					}
					if (support == null) {
						continue;
					}
					Vec3 hitVec = Vec3.atCenterOf(support)
						.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
					if (eye.distanceTo(hitVec) > placeRange.value) {
						continue;
					}
					Vec3 center = Vec3.atCenterOf(pos);
					double dmgTarget = CombatUtil.explosionDamage(center, target, POWER);
					if (dmgTarget < minDamage.value) {
						continue;
					}
					double dmgSelf = CombatUtil.explosionDamage(center, p, POWER);
					if (!selfSafe(dmgSelf, selfHp)) {
						continue;
					}
					double score = dmgTarget - dmgSelf * 1.2;
					if (score > bestScore) {
						bestScore = score;
						bestSupport = support;
						bestFace = face;
						bestHit = hitVec;
					}
				}
			}
		}
		if (bestSupport == null) {
			return false;
		}
		InteractionHand hand = CombatUtil.holdItem(p, Items.RESPAWN_ANCHOR, autoSwitch.value);
		if (hand == null) {
			return false;
		}
		look(p, bestHit);
		mc.gameMode.useItemOn(p, hand, new BlockHitResult(bestHit, bestFace, bestSupport, false));
		p.swing(hand);
		return true;
	}

	private void look(LocalPlayer p, Vec3 at) {
		if (rotate.value && !ModuleManager.freecam.isEnabled()) {
			CombatUtil.turn(p, CombatUtil.rotationsTo(p.getEyePosition(), at), 360f);
		}
	}
}
