package dev.aurora.hud;

import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.Nametags;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Draws nametags by projecting each player's position onto the screen. Accurate in first person;
 * it hides itself in third person, and small drift is possible while the FOV changes (sprinting).
 */
public final class NametagRenderer {
	private NametagRenderer() {}

	public static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		Nametags nt = ModuleManager.nametags;
		if (nt == null || !nt.isEnabled() || mc.options.hideGui || mc.player == null || mc.level == null) {
			return;
		}
		if (!mc.options.getCameraType().isFirstPerson()) {
			return;
		}

		Font font = mc.font;
		Player me = mc.player;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int gw = g.guiWidth();
		int gh = g.guiHeight();

		Vec3 cam = me.getEyePosition(partial);
		double yaw = Math.toRadians(Mth.lerp(partial, me.yRotO, me.getYRot()));
		double pitch = Math.toRadians(Mth.lerp(partial, me.xRotO, me.getXRot()));
		double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
		Vec3 forward = new Vec3(-sy * cp, -sp, cy * cp);
		Vec3 right = new Vec3(-cy, 0, -sy);
		Vec3 up = right.cross(forward);

		double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		double aspect = (double) gw / gh;
		int accent = ModuleManager.ui.accent();

		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == me || p.isSpectator() || (!nt.showInvisible.value && p.isInvisible())) {
				continue;
			}
			double dist = me.distanceTo(p);
			if (dist > nt.maxDistance.value) {
				continue;
			}

			Vec3 pos = new Vec3(
				Mth.lerp(partial, p.xo, p.getX()),
				Mth.lerp(partial, p.yo, p.getY()) + p.getBbHeight() + nt.yOffset.value,
				Mth.lerp(partial, p.zo, p.getZ()));
			Vec3 rel = pos.subtract(cam);
			double depth = rel.dot(forward);
			if (depth < 0.1) {
				continue;
			}
			double ndcX = rel.dot(right) / (depth * tanHalf * aspect);
			double ndcY = rel.dot(up) / (depth * tanHalf);
			if (Math.abs(ndcX) > 1.3 || Math.abs(ndcY) > 1.3) {
				continue;
			}
			int sx = (int) ((ndcX * 0.5 + 0.5) * gw);
			int sYpos = (int) ((0.5 - ndcY * 0.5) * gh);

			float hp = p.getHealth() + p.getAbsorptionAmount();
			float frac = Mth.clamp(p.getHealth() / Math.max(1f, p.getMaxHealth()), 0f, 1f);

			int nameColor;
			if (nt.colorMode.is("Health")) {
				nameColor = ColorUtil.hsb(frac * 0.33f, 0.7f, 1f);
			} else if (nt.colorMode.is("Accent")) {
				nameColor = accent;
			} else {
				nameColor = nt.staticColor.rgb();
			}

			StringBuilder sb = new StringBuilder(p.getName().getString());
			if (nt.health.value) {
				String code = frac > 0.66f ? "§a" : frac > 0.33f ? "§e" : "§c";
				sb.append(' ').append(code).append(String.format(Locale.ROOT, "%.1f", hp));
			}
			if (nt.distance.value) {
				sb.append(" §7").append((int) dist).append('m');
			}

			int y = sYpos - 11;
			if (nt.heldItem.value && !p.getMainHandItem().isEmpty()) {
				tag(g, font, nt, "§7" + p.getMainHandItem().getHoverName().getString(), sx, y, 0xFFFFFF);
				y -= 11;
			}
			tag(g, font, nt, sb.toString(), sx, y, nameColor);
		}
	}

	private static void tag(GuiGraphics g, Font font, Nametags nt, String text, int cx, int y, int rgb) {
		int w = font.width(text) + 6;
		int x = cx - w / 2;
		if (nt.background.value) {
			GuiUtil.rrect(g, x, y, w, 11, 2, ColorUtil.argb(0x0B0B10, nt.opacity.f()));
		}
		g.drawString(font, text, x + 3, y + 2, 0xFF000000 | (rgb & 0xFFFFFF), true);
	}
}
