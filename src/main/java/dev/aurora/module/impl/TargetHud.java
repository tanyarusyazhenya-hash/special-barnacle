package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Card with the target's face, name, health bar and armor. Drawn by TargetHudRenderer. */
public class TargetHud extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Classic", "Classic", "Compact", "Minimal", "Modern",
		"Neon", "Ring", "Bubble"));
	public final ModeSetting barStyle = add(new ModeSetting("Bar Style", "Smooth", "Smooth", "Segmented", "Gradient"));
	public final BoolSetting colorByHealth = add(new BoolSetting("Color Bar By Health", true));
	public final BoolSetting showDistance = add(new BoolSetting("Show Distance", true));
	public final ModeSetting source = add(new ModeSetting("Target", "Crosshair", "Crosshair", "Nearest Player"));
	public final NumberSetting linger = add(new NumberSetting("Linger (sec)", 3, 0, 10, 0.5));
	public final BoolSetting playersOnly = add(new BoolSetting("Players Only", false));
	public final BoolSetting armor = add(new BoolSetting("Armor", true));
	public final BoolSetting face = add(new BoolSetting("Head", true));
	public final NumberSetting x = add(new NumberSetting("Offset X", 0, -400, 400, 5));
	public final NumberSetting y = add(new NumberSetting("Offset Y", 40, -300, 300, 5));
	public final NumberSetting opacity = add(new NumberSetting("Background Opacity", 0.6, 0.0, 1.0, 0.05));

	public LivingEntity target;
	public float shownHealth;
	private long lastSeen;

	public TargetHud() {
		super("TargetHUD", "Shows health and armor of the entity you are fighting", Category.HUD);
		face.visibleWhen(() -> !style.is("Minimal") && !style.is("Ring"));
		armor.visibleWhen(() -> style.is("Classic") || style.is("Modern") || style.is("Neon"));
		opacity.visibleWhen(() -> !style.is("Minimal"));
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			target = null;
			return;
		}
		LivingEntity found = null;
		if (source.is("Crosshair")) {
			Entity e = mc.crosshairPickEntity;
			if (e instanceof LivingEntity le && le.isAlive()
				&& (!playersOnly.value || le instanceof net.minecraft.world.entity.player.Player)) {
				found = le;
			}
		} else {
			found = dev.aurora.util.CombatUtil.nearestEnemyPlayer(mc, 12);
		}
		long now = System.currentTimeMillis();
		if (found != null) {
			if (found != target) {
				shownHealth = found.getHealth();
			}
			target = found;
			lastSeen = now;
		} else if (target != null && (!target.isAlive() || now - lastSeen > linger.value * 1000)) {
			target = null;
		}
	}
}
