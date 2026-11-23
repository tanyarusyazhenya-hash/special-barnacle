package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Hits whatever your crosshair is on once the attack is charged. It never turns your head. The
 * presets add a human-like reaction delay after the crosshair lands on a new target, a slightly
 * random charge threshold and rare skipped hits. Not tested against any anti-cheat and not
 * undetectable. Friends and group members are never hit. Off by default every launch.
 */
public class TriggerBot extends Module {
	private record Profile(double cdMin, int reactMin, int reactMax, double miss) {}

	public final ModeSetting preset = add(new ModeSetting("Preset", "Legit", "Legit", "Strict", "Custom"));
	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final BoolSetting hostile = add(new BoolSetting("Hostile Mobs", true));
	public final BoolSetting passive = add(new BoolSetting("Passive Mobs", false));
	public final BoolSetting weaponOnly = add(new BoolSetting("Only With Weapon", true));
	public final BoolSetting onlyCrits = add(new BoolSetting("Only Crits", false));
	public final BoolSetting whileUsing = add(new BoolSetting("Attack While Using Item", false));
	public final NumberSetting cooldown = add(new NumberSetting("Min Cooldown", 0.92, 0.5, 1.0, 0.01));
	public final NumberSetting reactMin = add(new NumberSetting("Reaction Min (ticks)", 1, 0, 10, 1));
	public final NumberSetting reactMax = add(new NumberSetting("Reaction Max (ticks)", 3, 0, 12, 1));
	public final NumberSetting missChance = add(new NumberSetting("Miss Chance %", 4, 0, 30, 1));

	private Entity lastTarget;
	private int reaction;
	private double needed = 1.0;

	public TriggerBot() {
		super("TriggerBot", "Hits what your crosshair is on, with human-like timing", Category.COMBAT);
		for (var s : new dev.aurora.setting.Setting[]{cooldown, reactMin, reactMax, missChance}) {
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
			case "Strict" -> new Profile(1.0, 2, 5, 0.06);
			case "Custom" -> new Profile(cooldown.value, reactMin.i(), Math.max(reactMin.i(), reactMax.i()),
				missChance.value / 100.0);
			default -> new Profile(0.95, 1, 3, 0.04);
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null || mc.screen != null || !p.isAlive()
			|| ModuleManager.freecam.isEnabled()) {
			return;
		}
		if (p.isUsingItem() && !whileUsing.value) {
			return;
		}
		if (weaponOnly.value && !isWeapon(p.getMainHandItem())) {
			return;
		}
		Entity e = mc.crosshairPickEntity;
		if (!(e instanceof LivingEntity le) || !le.isAlive() || !valid(le)) {
			lastTarget = null;
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		Profile pr = profile();
		if (e != lastTarget) {
			lastTarget = e;
			reaction = pr.reactMin() + rnd.nextInt(pr.reactMax() - pr.reactMin() + 1);
		}
		if (reaction > 0) {
			reaction--;
			return;
		}
		if (onlyCrits.value && (p.onGround() || p.fallDistance <= 0 || p.isInWater() || p.onClimbable())) {
			return;
		}
		if (p.getAttackStrengthScale(0.5f) < needed) {
			return;
		}
		needed = Math.min(1.0, pr.cdMin() + rnd.nextDouble() * (1.0 - pr.cdMin()));
		if (rnd.nextDouble() < pr.miss()) {
			reaction = 1;
			return;
		}
		mc.gameMode.attack(p, e);
		p.swing(InteractionHand.MAIN_HAND);
	}

	private boolean valid(LivingEntity e) {
		if (e instanceof Player p) {
			return players.value && !p.isSpectator() && !ModuleManager.friends.isFriend(p);
		}
		if (e instanceof Enemy) {
			return hostile.value;
		}
		return e instanceof Mob && passive.value;
	}

	private static boolean isWeapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT);
	}
}
