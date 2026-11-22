package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera from your character. A client-side camera entity flies around while the real
 * player stands still: its input is swapped for an empty one, and the mouse movement is moved from
 * the player onto the camera every frame, so no rotation reaches the server.
 */
public class Freecam extends Module {
	public final NumberSetting speed = add(new NumberSetting("Speed (blocks/s)", 12, 2, 60, 1));
	public final BoolSetting boost = add(new BoolSetting("Sprint Boost (x2)", true));
	public final BoolSetting smooth = add(new BoolSetting("Smooth Movement", true));
	public final BoolSetting disableOnDamage = add(new BoolSetting("Disable On Damage", true));

	private RemotePlayer cam;
	private ClientInput savedInput;
	private final ClientInput blankInput = new ClientInput();
	private Object level;
	private float savedYaw, savedPitch, camYaw, camPitch;
	private Vec3 velocity = Vec3.ZERO;

	public Freecam() {
		super("Freecam", "Fly the camera around while your character stays put", Category.UTILITY);
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			setEnabled(false);
			return;
		}
		level = mc.level;
		savedYaw = p.getYRot();
		savedPitch = p.getXRot();
		camYaw = savedYaw;
		camPitch = savedPitch;
		velocity = Vec3.ZERO;

		cam = new RemotePlayer(mc.level, p.getGameProfile());
		cam.noPhysics = true;
		cam.setPos(p.getX(), p.getY(), p.getZ());
		cam.xo = cam.getX();
		cam.yo = cam.getY();
		cam.zo = cam.getZ();
		cam.setYRot(camYaw);
		cam.setXRot(camPitch);
		cam.yRotO = camYaw;
		cam.xRotO = camPitch;

		savedInput = p.input;
		p.input = blankInput;
		mc.setCameraEntity(cam);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p != null) {
			if (savedInput != null) {
				p.input = savedInput;
			}
			p.setYRot(savedYaw);
			p.setXRot(savedPitch);
			mc.setCameraEntity(p);
		}
		savedInput = null;
		cam = null;
		level = null;
	}

	/** Every rendered frame: move the mouse look from the player to the camera. */
	public void frame(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (!isEnabled() || cam == null || p == null) {
			return;
		}
		float dy = p.getYRot() - savedYaw;
		float dx = p.getXRot() - savedPitch;
		if (dy != 0f || dx != 0f) {
			camYaw += dy;
			camPitch = Mth.clamp(camPitch + dx, -90f, 90f);
			p.setYRot(savedYaw);
			p.setXRot(savedPitch);
			p.yRotO = savedYaw;
			p.xRotO = savedPitch;
		}
		cam.setYRot(camYaw);
		cam.setXRot(camPitch);
		cam.yRotO = camYaw;
		cam.xRotO = camPitch;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.level != level || cam == null) {
			setEnabled(false);
			return;
		}
		if (!p.isAlive() || (disableOnDamage.value && p.hurtTime > 0)) {
			setEnabled(false);
			return;
		}
		if (mc.getCameraEntity() != cam) {
			mc.setCameraEntity(cam);
		}
		if (p.input != blankInput) {
			savedInput = p.input;
			p.input = blankInput;
		}

		Options o = mc.options;
		double forward = 0, side = 0, vertical = 0;
		if (mc.screen == null) {
			if (o.keyUp.isDown()) forward += 1;
			if (o.keyDown.isDown()) forward -= 1;
			if (o.keyRight.isDown()) side += 1;
			if (o.keyLeft.isDown()) side -= 1;
			if (o.keyJump.isDown()) vertical += 1;
			if (o.keyShift.isDown()) vertical -= 1;
		}

		double yaw = Math.toRadians(camYaw);
		double pitch = Math.toRadians(camPitch);
		double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
		Vec3 look = new Vec3(-sy * cp, -sp, cy * cp);
		Vec3 right = new Vec3(-cy, 0, -sy);

		Vec3 wish = look.scale(forward).add(right.scale(side)).add(0, vertical, 0);
		if (wish.lengthSqr() > 1.0E-6) {
			wish = wish.normalize();
		}
		double perTick = speed.value / 20.0 * (boost.value && o.keySprint.isDown() ? 2.0 : 1.0);
		Vec3 goal = wish.scale(perTick);
		velocity = smooth.value ? velocity.lerp(goal, 0.4) : goal;

		cam.xo = cam.getX();
		cam.yo = cam.getY();
		cam.zo = cam.getZ();
		cam.setPos(cam.getX() + velocity.x, cam.getY() + velocity.y, cam.getZ() + velocity.z);
	}
}
