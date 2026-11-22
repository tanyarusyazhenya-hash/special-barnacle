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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places end crystals on obsidian or bedrock next to the nearest enemy player and breaks them.
 * Damage is estimated without armor and without blocks in the way, so it errs on the high side.
 * Friends are never targeted. Off by default every launch. Most servers ban this.
 */
public class AutoCrystal extends Module {
	public final BoolSetting place = add(new BoolSetting("Place", true));
	public final BoolSetting breakCrystals = add(new BoolSetting("Break", true));
	public final NumberSetting targetRange = add(new NumberSetting("Target Range", 10, 4, 16, 1));
	public final NumberSetting placeRange = add(new NumberSetting("Place Range", 4.5, 2.0, 6.0, 0.5));
	public final NumberSetting breakRange = add(new NumberSetting("Break Range", 4.0, 2.0, 6.0, 0.5));
	public final NumberSetting placeDelay = add(new NumberSetting("Place Delay (ticks)", 2, 0, 10, 1));
	public final NumberSetting breakDelay = add(new NumberSetting("Break Delay (ticks)", 1, 0, 10, 1));
	public final NumberSetting minDamage = add(new NumberSetting("Min Target Damage", 4, 1, 20, 1));
	public final NumberSetting maxSelf = add(new NumberSetting("Max Self Damage", 8, 1, 20, 1));
	public final BoolSetting antiSuicide = add(new BoolSetting("Anti Suicide", true));
	public final BoolSetting autoSwitch = add(new BoolSetting("Auto Switch", true));
	public final BoolSetting rotate = add(new BoolSetting("Rotate", false));
	public final BoolSetting pauseWhileUsing = add(new BoolSetting("Pause While Eating", true));

	private int placeTimer = 100;
	private int breakTimer = 100;

	public AutoCrystal() {
		super("AutoCrystal", "Places and breaks end crystals near enemies", Category.COMBAT);
		placeRange.visibleWhen(() -> place.value);
		placeDelay.visibleWhen(() -> place.value);
		breakRange.visibleWhen(() -> breakCrystals.value);
		breakDelay.visibleWhen(() -> breakCrystals.value);
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	public String info() {
		return place.value && breakCrystals.value ? "Place+Break" : place.value ? "Place" : "Break";
	}

	@Override
	protected void onEnable() {
		placeTimer = 100;
		breakTimer = 100;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null || mc.screen != null || !p.isAlive()) {
			return;
		}
		if (ModuleManager.freecam.isEnabled() || (pauseWhileUsing.value && p.isUsingItem())) {
			return;
		}
		placeTimer = Math.min(placeTimer + 1, 100);
		breakTimer = Math.min(breakTimer + 1, 100);

		AbstractClientPlayer target = CombatUtil.nearestEnemyPlayer(mc, targetRange.value);
		if (target == null) {
			return;
		}
		double selfHp = p.getHealth() + p.getAbsorptionAmount();

		// 1. break the best crystal in reach
		if (breakCrystals.value && breakTimer >= breakDelay.i()) {
			EndCrystal best = null;
			double bestScore = -1e9;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!(e instanceof EndCrystal crystal) || !crystal.isAlive()) {
					continue;
				}
				if (CombatUtil.reach(p, crystal) > breakRange.value) {
					continue;
				}
				Vec3 center = crystal.position();
				double dmgTarget = CombatUtil.explosionDamage(center, target, 6.0);
				double dmgSelf = CombatUtil.explosionDamage(center, p, 6.0);
				if (dmgTarget < minDamage.value || !selfSafe(dmgSelf, selfHp)) {
					continue;
				}
				if (dmgTarget - dmgSelf > bestScore) {
					bestScore = dmgTarget - dmgSelf;
					best = crystal;
				}
			}
			if (best != null) {
				if (rotate.value) {
					look(p, best.position());
				}
				mc.gameMode.attack(p, best);
				p.swing(InteractionHand.MAIN_HAND);
				breakTimer = 0;
				return;
			}
		}

		// 2. place a new crystal
		if (place.value && placeTimer >= placeDelay.i()) {
			BlockPos base = findPlacement(mc, p, target, selfHp);
			if (base == null) {
				return;
			}
			InteractionHand hand = CombatUtil.holdItem(p, Items.END_CRYSTAL, autoSwitch.value);
			if (hand == null) {
				return;
			}
			Vec3 top = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
			if (rotate.value) {
				look(p, top);
			}
			BlockHitResult hit = new BlockHitResult(top, Direction.UP, base, false);
			InteractionResult result = mc.gameMode.useItemOn(p, hand, hit);
			if (result.consumesAction()) {
				p.swing(hand);
			}
			placeTimer = 0;
		}
	}

	private boolean selfSafe(double dmgSelf, double selfHp) {
		return dmgSelf <= maxSelf.value && !(antiSuicide.value && dmgSelf >= selfHp - 1.0);
	}

	private void look(LocalPlayer p, Vec3 at) {
		if (!ModuleManager.freecam.isEnabled()) {
			CombatUtil.turn(p, CombatUtil.rotationsTo(p.getEyePosition(), at), 360f);
		}
	}

	private BlockPos findPlacement(Minecraft mc, LocalPlayer p, AbstractClientPlayer target, double selfHp) {
		Vec3 eye = p.getEyePosition();
		int r = (int) Math.ceil(placeRange.value);
		BlockPos origin = p.blockPosition();
		BlockPos best = null;
		double bestScore = -1e9;

		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos b = origin.offset(dx, dy, dz);
					BlockState state = mc.level.getBlockState(b);
					if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
						continue;
					}
					if (!mc.level.getBlockState(b.above()).isAir()) {
						continue;
					}
					Vec3 top = new Vec3(b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5);
					if (eye.distanceTo(top) > placeRange.value) {
						continue;
					}
					AABB space = new AABB(b.getX(), b.getY() + 1, b.getZ(), b.getX() + 1, b.getY() + 3, b.getZ() + 1);
					if (!mc.level.getEntities(null, space).isEmpty()) {
						continue;
					}
					double dmgTarget = CombatUtil.explosionDamage(top, target, 6.0);
					if (dmgTarget < minDamage.value) {
						continue;
					}
					double dmgSelf = CombatUtil.explosionDamage(top, p, 6.0);
					if (!selfSafe(dmgSelf, selfHp)) {
						continue;
					}
					if (dmgTarget - dmgSelf > bestScore) {
						bestScore = dmgTarget - dmgSelf;
						best = b;
					}
				}
			}
		}
		return best;
	}
}
