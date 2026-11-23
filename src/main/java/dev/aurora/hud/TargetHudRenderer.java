package dev.aurora.hud;

import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.TargetHud;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Draws the TargetHUD in one of several styles. */
public final class TargetHudRenderer {
	private TargetHudRenderer() {}

	public static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		TargetHud t = ModuleManager.targetHud;
		if (t == null || !t.isEnabled() || mc.options.hideGui || mc.player == null) {
			return;
		}
		LivingEntity e = t.target;
		if (e == null) {
			return;
		}
		Font font = mc.font;
		int accent = ModuleManager.ui.accent();
		t.shownHealth += (e.getHealth() - t.shownHealth) * 0.15f;
		float frac = Mth.clamp(t.shownHealth / Math.max(1f, e.getMaxHealth()), 0f, 1f);
		int barColor = t.colorByHealth.value ? (0xFF000000 | ColorUtil.hsb(frac * 0.33f, 0.7f, 1f))
			: (0xFF000000 | accent);
		float hp = e.getHealth() + e.getAbsorptionAmount();
		String name = e.getName().getString();
		String dist = t.showDistance.value ? String.format(Locale.ROOT, " %.1fm", mc.player.distanceTo(e)) : "";
		String hpText = String.format(Locale.ROOT, "%.1f", hp);
		int cx = g.guiWidth() / 2 + (int) t.x.value;
		int y = g.guiHeight() / 2 + (int) t.y.value;
		int bg = ColorUtil.argb(0x0B0B10, t.opacity.f());

		switch (t.style.get()) {
			case "Compact" -> {
				int w = 118, h = 26, x = cx - w / 2;
				GuiUtil.rrect(g, x, y, w, h, 4, bg);
				int tx = x + 6;
				if (t.face.value && e instanceof AbstractClientPlayer ap) {
					PlayerFaceRenderer.draw(g, ap.getSkin(), x + 4, y + 4, 18);
					tx = x + 26;
				}
				g.drawString(font, font.plainSubstrByWidth(name, x + w - tx - 28), tx, y + 4, 0xFFFFFFFF, true);
				g.drawString(font, hpText, x + w - 6 - font.width(hpText), y + 4, barColor, true);
				bar(g, t, tx, y + 17, x + w - 6 - tx, 3, frac, barColor, accent);
			}
			case "Minimal" -> {
				int w = 90;
				int x = cx - w / 2;
				String line = name + " §7" + hpText + dist;
				g.drawString(font, line, cx - font.width(line) / 2, y, 0xFFFFFFFF, true);
				bar(g, t, x, y + 12, w, 2, frac, barColor, accent);
			}
			case "Modern" -> {
				int w = 168;
				int h = t.armor.value ? 58 : 42;
				int x = cx - w / 2;
				GuiUtil.rrect(g, x, y, w, h, 5, bg);
				g.fill(x + 1, y + 6, x + 4, y + h - 6, 0xFF000000 | accent);
				int tx = x + 10;
				if (t.face.value && e instanceof AbstractClientPlayer ap) {
					PlayerFaceRenderer.draw(g, ap.getSkin(), x + 9, y + 6, 30);
					tx = x + 44;
				}
				g.drawString(font, font.plainSubstrByWidth(name, w - (tx - x) - 40), tx, y + 7, 0xFFFFFFFF, true);
				g.pose().pushMatrix();
				g.pose().translate(x + w - 8 - font.width(hpText) * 1.5f, y + 6);
				g.pose().scale(1.5f, 1.5f);
				g.drawString(font, hpText, 0, 0, barColor, true);
				g.pose().popMatrix();
				if (!dist.isEmpty()) {
					g.drawString(font, dist.trim(), tx, y + 18, 0xFF9A9AA4, true);
				}
				bar(g, t, tx, y + 30, x + w - 8 - tx, 5, frac, barColor, accent);
				if (t.armor.value) {
					items(g, font, e, x + 8, y + 40, 1f);
				}
			}
			case "Neon" -> {
				int w = 150, h = t.armor.value ? 52 : 36, x = cx - w / 2;
				for (int i = 3; i >= 1; i--) {
					GuiUtil.rrect(g, x - i * 2, y - i * 2, w + i * 4, h + i * 4, 6 + i * 2,
						ColorUtil.argb(accent, 0.06f * (4 - i)));
				}
				GuiUtil.rrect(g, x - 1, y - 1, w + 2, h + 2, 5, ColorUtil.argb(accent, 0.9f));
				GuiUtil.rrect(g, x, y, w, h, 4, ColorUtil.argb(0x08080B, 0.92f));
				int tx = x + 8;
				if (t.face.value && e instanceof AbstractClientPlayer ap) {
					PlayerFaceRenderer.draw(g, ap.getSkin(), x + 6, y + 5, 26);
					tx = x + 38;
				}
				g.drawString(font, font.plainSubstrByWidth(name, w - (tx - x) - 8), tx, y + 6, 0xFF000000 | accent, true);
				g.drawString(font, hpText + " HP" + dist, tx, y + 17, 0xFFFFFFFF, true);
				bar(g, t, tx, y + 28, x + w - 8 - tx, 3, frac, barColor, accent);
				if (t.armor.value) {
					items(g, font, e, x + 6, y + 34, 0.8f);
				}
			}
			case "Ring" -> {
				int r = 24;
				int ccx = cx;
				int ccy = y + r + 2;
				GuiUtil.rrect(g, ccx - r - 6, ccy - r - 6, (r + 6) * 2, (r + 6) * 2 + 16, r + 6, bg);
				int dots = 72;
				for (int i = 0; i < dots; i++) {
					double a = Math.toRadians(-90 + i * 360.0 / dots);
					int px = ccx + (int) Math.round(Math.cos(a) * r);
					int py = ccy + (int) Math.round(Math.sin(a) * r);
					boolean on = i < dots * frac;
					g.fill(px - 1, py - 1, px + 1, py + 1, on ? barColor : 0x44FFFFFF);
				}
				if (e instanceof AbstractClientPlayer ap) {
					PlayerFaceRenderer.draw(g, ap.getSkin(), ccx - 14, ccy - 14, 28);
				} else {
					g.drawString(font, hpText, ccx - font.width(hpText) / 2, ccy - 4, barColor, true);
				}
				String label = font.plainSubstrByWidth(name, 70);
				g.drawString(font, label, ccx - font.width(label) / 2, ccy + r + 8, 0xFFFFFFFF, true);
			}
			case "Bubble" -> {
				int w = 128, h = 30, x = cx - w / 2;
				GuiUtil.rrect(g, x, y, w, h, 15, bg);
				int tx = x + 12;
				if (t.face.value && e instanceof AbstractClientPlayer ap) {
					PlayerFaceRenderer.draw(g, ap.getSkin(), x + 4, y + 4, 22);
					tx = x + 32;
				}
				g.drawString(font, font.plainSubstrByWidth(name, x + w - tx - 10), tx, y + 5, 0xFFFFFFFF, true);
				bar(g, t, tx, y + 18, x + w - 12 - tx, 4, frac, barColor, accent);
			}
			default -> classic(g, font, t, e, cx, y, frac, barColor, accent, name, hp, dist, bg);
		}
	}

	private static void classic(GuiGraphics g, Font font, TargetHud t, LivingEntity e, int cx, int y, float frac,
								int barColor, int accent, String name, float hp, String dist, int bg) {
		int w = 140;
		int h = t.armor.value ? 50 : 38;
		int x = cx - w / 2;
		GuiUtil.rrect(g, x, y, w, h, 4, bg);
		int tx = x + 6;
		if (t.face.value && e instanceof AbstractClientPlayer ap) {
			PlayerFaceRenderer.draw(g, ap.getSkin(), x + 5, y + 5, 28);
			tx = x + 38;
		}
		g.drawString(font, name, tx, y + 6, 0xFFFFFFFF, true);
		g.drawString(font, String.format(Locale.ROOT, "%.1f HP", hp) + dist, tx, y + 17, 0xFFB8B8C0, true);
		bar(g, t, tx, y + 29, x + w - 6 - tx, 4, frac, barColor, accent);
		if (t.armor.value) {
			items(g, font, e, x + 6, y + 34, 1f);
		}
	}

	private static void bar(GuiGraphics g, TargetHud t, int x, int y, int w, int h, float frac, int color, int accent) {
		if (w <= 0) {
			return;
		}
		switch (t.barStyle.get()) {
			case "Segmented" -> {
				int seg = 10;
				int gap = 1;
				int sw = Math.max(1, (w - gap * (seg - 1)) / seg);
				for (int i = 0; i < seg; i++) {
					int sx = x + i * (sw + gap);
					g.fill(sx, y, sx + sw, y + h, i < Math.ceil(frac * seg) ? color : 0x44FFFFFF);
				}
			}
			case "Gradient" -> {
				g.fill(x, y, x + w, y + h, 0x44FFFFFF);
				int fill = Math.max(1, (int) (w * frac));
				int c1 = 0xFF000000 | accent;
				for (int i = 0; i < fill; i++) {
					float k = i / (float) Math.max(1, w - 1);
					g.fill(x + i, y, x + i + 1, y + h, lerp(c1, 0xFFFFFFFF, k * 0.6f));
				}
			}
			default -> {
				GuiUtil.rrect(g, x, y, w, h, h / 2, 0x44FFFFFF);
				GuiUtil.rrect(g, x, y, Math.max(h, (int) (w * frac)), h, h / 2, color);
			}
		}
	}

	private static int lerp(int a, int b, float k) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
		int gr = (int) (((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
		int bl = (int) ((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
		return 0xFF000000 | (r << 16) | (gr << 8) | bl;
	}

	private static void items(GuiGraphics g, Font font, LivingEntity e, int x, int y, float scale) {
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
		int step = Math.round(20 * scale);
		for (EquipmentSlot s : slots) {
			ItemStack st = e.getItemBySlot(s);
			if (!st.isEmpty()) {
				g.pose().pushMatrix();
				g.pose().translate(x, y);
				g.pose().scale(scale, scale);
				g.renderItem(st, 0, 0);
				g.renderItemDecorations(font, st, 0, 0);
				g.pose().popMatrix();
			}
			x += step;
		}
	}
}
