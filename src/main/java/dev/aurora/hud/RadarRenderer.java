package dev.aurora.hud;

import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.Radar;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

public final class RadarRenderer {
	private RadarRenderer() {}

	public static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		Radar r = ModuleManager.radar;
		if (r == null || !r.isEnabled() || mc.options.hideGui || mc.player == null || mc.level == null) {
			return;
		}
		int size = r.size.i();
		int margin = 8;
		int x = r.corner.get().contains("Right") ? g.guiWidth() - size - margin : margin;
		int y = r.corner.get().startsWith("Bottom") ? g.guiHeight() - size - margin - 20 : margin + 20;
		int accent = ModuleManager.ui.accent();
		GuiUtil.rrect(g, x - 1, y - 1, size + 2, size + 2, size / 2 + 1, ColorUtil.argb(accent, 0.5f));
		GuiUtil.rrect(g, x, y, size, size, size / 2, ColorUtil.argb(0x0B0B10, r.opacity.f()));
		int cx = x + size / 2;
		int cy = y + size / 2;
		g.fill(cx - size / 2 + 6, cy, cx + size / 2 - 6, cy + 1, ColorUtil.argb(0xFFFFFF, 0.08f));
		g.fill(cx, cy - size / 2 + 6, cx + 1, cy + size / 2 - 6, ColorUtil.argb(0xFFFFFF, 0.08f));

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double yaw = Math.toRadians(Mth.lerp(partial, mc.player.yRotO, mc.player.getYRot()));
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		double scale = (size / 2.0 - 4) / r.range.value;

		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || !(e instanceof LivingEntity)) {
				continue;
			}
			int rgb;
			if (e instanceof Player p) {
				if (!r.players.value) {
					continue;
				}
				rgb = ModuleManager.friends.isFriend(p) ? ModuleManager.friends.color.rgb() : 0xFF4D4D;
			} else if (e instanceof Enemy) {
				if (!r.hostile.value) {
					continue;
				}
				rgb = 0xFF9F43;
			} else if (e instanceof Mob) {
				if (!r.passive.value) {
					continue;
				}
				rgb = 0x4ADE80;
			} else {
				continue;
			}
			double dx = e.getX() - mc.player.getX();
			double dz = e.getZ() - mc.player.getZ();
			double right = dx * -cos + dz * -sin;
			double fwd = dx * -sin + dz * cos;
			double px = right * scale;
			double py = -fwd * scale;
			double d = Math.hypot(px, py);
			double max = size / 2.0 - 4;
			if (d > max) {
				px *= max / d;
				py *= max / d;
			}
			int ex = cx + (int) Math.round(px);
			int ey = cy + (int) Math.round(py);
			g.fill(ex - 2, ey - 2, ex + 2, ey + 2, 0xFF000000 | rgb);
		}
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF000000 | accent);
	}
}
