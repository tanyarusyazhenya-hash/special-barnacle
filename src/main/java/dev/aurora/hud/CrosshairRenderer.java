package dev.aurora.hud;

import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.CustomCrosshair;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class CrosshairRenderer {
	private static float spread;

	private CrosshairRenderer() {}

	public static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		CustomCrosshair c = ModuleManager.crosshair;
		if (c == null || mc.options.hideGui || mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		float target = c.dynamic.value ? (float) Math.min(6.0, ModuleManager.hud.speedBps * 0.6) : 0f;
		spread += (target - spread) * 0.2f;

		int rgb;
		boolean onEnemy = c.targetColor.value && mc.crosshairPickEntity instanceof LivingEntity;
		if (onEnemy) {
			rgb = c.enemyColor.rgb();
		} else if (c.colorMode.is("Static")) {
			rgb = c.staticColor.rgb();
		} else if (c.colorMode.is("Rainbow")) {
			rgb = ColorUtil.hsb((System.currentTimeMillis() % 3000) / 3000f, 0.7f, 1f);
		} else if (c.colorMode.is("Health")) {
			float f = Mth.clamp(mc.player.getHealth() / Math.max(1f, mc.player.getMaxHealth()), 0f, 1f);
			rgb = ColorUtil.hsb(f * 0.33f, 0.7f, 1f);
		} else {
			rgb = ModuleManager.ui.accent();
		}
		int col = ColorUtil.argb(rgb, c.opacity.f());
		int line = ColorUtil.argb(0x000000, c.opacity.f() * 0.85f);
		int t = c.thickness.i();
		int size = c.size.i();
		int gap = c.gap.i() + Math.round(spread);

		switch (c.style.get()) {
			case "Dot" -> rect(g, cx - t, cy - t, t * 2, t * 2, col, line, c.outline.value);
			case "Circle" -> {
				int r = size + 2;
				for (int i = 0; i < 48; i++) {
					double a = Math.toRadians(i * 7.5);
					int px = cx + (int) Math.round(Math.cos(a) * r);
					int py = cy + (int) Math.round(Math.sin(a) * r);
					rect(g, px, py, t, t, col, line, c.outline.value);
				}
			}
			case "Square" -> {
				int r = size + 2;
				rect(g, cx - r, cy - r, r * 2 + t, t, col, line, c.outline.value);
				rect(g, cx - r, cy + r, r * 2 + t, t, col, line, c.outline.value);
				rect(g, cx - r, cy - r, t, r * 2 + t, col, line, c.outline.value);
				rect(g, cx + r, cy - r, t, r * 2 + t, col, line, c.outline.value);
			}
			default -> {
				boolean plus = c.style.is("Plus");
				// four arms; "Cross" leaves out the top arm like a classic gaming crosshair
				rect(g, cx - gap - size, cy - t / 2, size, t, col, line, c.outline.value);
				rect(g, cx + gap + 1, cy - t / 2, size, t, col, line, c.outline.value);
				rect(g, cx - t / 2, cy + gap + 1, t, size, col, line, c.outline.value);
				if (plus) {
					rect(g, cx - t / 2, cy - gap - size, t, size, col, line, c.outline.value);
				}
				if (c.style.is("Cross + Dot")) {
					rect(g, cx - t / 2, cy - t / 2, t, t, col, line, c.outline.value);
					rect(g, cx - t / 2, cy - gap - size, t, size, col, line, c.outline.value);
				}
			}
		}

		if (c.attackIndicator.value) {
			float s = mc.player.getAttackStrengthScale(0f);
			if (s < 1f) {
				int r = size + 7;
				int dots = (int) (40 * s);
				for (int i = 0; i < dots; i++) {
					double a = Math.toRadians(-90 + i * 9.0);
					int px = cx + (int) Math.round(Math.cos(a) * r);
					int py = cy + (int) Math.round(Math.sin(a) * r);
					g.fill(px, py, px + 1, py + 1, col);
				}
			}
		}
	}

	private static void rect(GuiGraphics g, int x, int y, int w, int h, int color, int outline, boolean withOutline) {
		if (withOutline) {
			g.fill(x - 1, y - 1, x + w + 1, y + h + 1, outline);
		}
		g.fill(x, y, x + w, y + h, color);
	}
}
