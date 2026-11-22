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

import java.util.Comparator;
import java.util.List;

/**
 * Attacks the best target in reach once the attack cooldown is ready. Friends are never attacked.
 * Rotation turns the real player (it is visible to the server and to other players).
 * Off by default every launch. Most servers ban this.
 */
public class KillAura extends Module {
	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final BoolSetting hostile = add(new BoolSetting("Hostile Mobs", false));
	public final BoolSetting passive = add(new BoolSetting("Passive Mobs", false));
	public final BoolSetting invisible = add(new BoolSetting("Invisible Players", true));
	public final NumberSetting range = add(new NumberSetting("Range", 3.0, 2.0, 6.0, 0.1));
	public final ModeSetting sort = add(new ModeSetting("Priority", "Distance", "Distance", "Health"));
	public final NumberSetting cooldown = add(new NumberSetting("Cooldown", 0.92, 0.5, 1.0, 0.01));
	public final ModeSetting rotation = add(new ModeSetting("Rotation", "Smooth", "None", "Snap", "Smooth"));
	public final NumberSetting rotationSpeed = add(new NumberSetting("Turn Speed", 40, 5, 180, 5));
	public final BoolSetting weaponOnly = add(new BoolSetting("Only With Weapon", true));
	public final BoolSetting throughWalls = add(new BoolSetting("Through Walls", false));
	public final BoolSetting whileUsing = add(new BoolSetting("Attack While Using Item", false));

	public KillAura() {
		super("KillAura", "Attacks targets in reach automatically", Category.COMBAT);
		rotationSpeed.visibleWhen(() -> rotation.is("Smooth"));
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	public String info() {
		return sort.get();
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

		List<LivingEntity> list = CombatUtil.targets(mc, range.value, players.value, hostile.value, passive.value,
			invisible.value);
		if (!throughWalls.value) {
			list.removeIf(e -> !p.hasLineOfSight(e));
		}
		if (list.isEmpty()) {
			return;
		}
		if (sort.is("Health")) {
			list.sort(Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount()));
		} else {
			list.sort(Comparator.comparingDouble(e -> CombatUtil.reach(p, e)));
		}
		LivingEntity target = list.get(0);

		if (!rotation.is("None")) {
			float[] rot = CombatUtil.rotationsTo(p.getEyePosition(), target.getBoundingBox().getCenter());
			CombatUtil.turn(p, rot, rotation.is("Snap") ? 360f : rotationSpeed.f());
			boolean facing = Math.abs(Mth.wrapDegrees(rot[0] - p.getYRot())) < 25f
				&& Math.abs(rot[1] - p.getXRot()) < 25f;
			if (!facing) {
				return; // still turning
			}
		}

		if (p.getAttackStrengthScale(0.5f) < cooldown.f()) {
			return;
		}
		mc.gameMode.attack(p, target);
		p.swing(InteractionHand.MAIN_HAND);
	}

	private static boolean isWeapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT);
	}
}
