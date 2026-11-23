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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Walks after the nearest enemy player by itself and jumps. It presses your movement keys for you
 * (releasing them as soon as it is off or there is no target) and turns you toward the target.
 * Follow = run straight at them, Orbit = circle around them. Friends and group members are ignored.
 * It only moves you; use KillAura to hit. Off by default every launch.
 */
public class TargetAura extends Module {
	public final ModeSetting mode = add(new ModeSetting("Mode", "Follow", "Follow", "Orbit"));
	public final NumberSetting range = add(new NumberSetting("Search Range", 16, 4, 48, 1));
	public final NumberSetting distance = add(new NumberSetting("Keep Distance", 2.5, 1.0, 6.0, 0.25));
	public final ModeSetting jump = add(new ModeSetting("Jump", "Always", "Always", "On Obstacle", "Off"));
	public final BoolSetting sprint = add(new BoolSetting("Sprint", true));
	public final BoolSetting rotate = add(new BoolSetting("Face Target", true));
	public final NumberSetting turnSpeed = add(new NumberSetting("Turn Speed", 35, 5, 180, 5));
	public final BoolSetting flipOrbit = add(new BoolSetting("Switch Orbit Side", false));

	private int orbitTicks;
	private boolean orbitRight = true;
	private String status = "No target";

	public TargetAura() {
		super("TargetAura", "Runs after the nearest player and jumps", Category.COMBAT);
		flipOrbit.visibleWhen(() -> mode.is("Orbit"));
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
		if (p == null || mc.level == null || !p.isAlive() || mc.screen != null
			|| ModuleManager.freecam.isEnabled()) {
			return;
		}
		// start from the real keyboard state, then press what we need
		KeyMapping.setAll();
		AbstractClientPlayer target = CombatUtil.nearestEnemyPlayer(mc, range.value);
		if (target == null) {
			status = "No target";
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 aim = target.getBoundingBox().getCenter();
		if (rotate.value) {
			CombatUtil.turn(p, CombatUtil.rotationsTo(eye, aim), turnSpeed.f());
		}
		double dx = target.getX() - p.getX();
		double dz = target.getZ() - p.getZ();
		double dist = Math.hypot(dx, dz);
		boolean close = dist <= distance.value;

		var o = mc.options;
		if (mode.is("Orbit")) {
			status = "Orbiting";
			o.keyUp.setDown(!close || dist > distance.value + 0.5);
			if (orbitTicks-- <= 0) {
				orbitTicks = 25 + (int) (Math.random() * 20);
				if (flipOrbit.value) {
					orbitRight = !orbitRight;
				}
			}
			if (orbitRight) {
				o.keyRight.setDown(true);
			} else {
				o.keyLeft.setDown(true);
			}
		} else {
			status = close ? "Close" : "Following";
			o.keyUp.setDown(!close);
		}
		if (sprint.value && !close) {
			o.keySprint.setDown(true);
		}
		if (!jump.is("Off") && p.onGround()) {
			if (jump.is("Always") ? !close : p.horizontalCollision) {
				o.keyJump.setDown(true);
			}
		}
	}
}
