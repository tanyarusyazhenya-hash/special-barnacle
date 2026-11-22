package dev.aurora.hud;

import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.TargetHud;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

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
		int w = 140;
		int h = t.armor.value ? 50 : 38;
		int x = g.guiWidth() / 2 - w / 2 + (int) t.x.value;
		int y = g.guiHeight() / 2 + (int) t.y.value;

		t.shownHealth += (e.getHealth() - t.shownHealth) * 0.15f;
		float max = Math.max(1f, e.getMaxHealth());
		float frac = Mth.clamp(t.shownHealth / max, 0f, 1f);

		GuiUtil.rrect(g, x, y, w, h, 4, ColorUtil.argb(0x0B0B10, t.opacity.f()));
		int tx = x + 6;
		if (t.face.value && e instanceof AbstractClientPlayer ap) {
			net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, ap.getSkin(), x + 5, y + 5, 28);
			tx = x + 38;
		}
		String name = e.getName().getString();
		g.drawString(font, name, tx, y + 6, 0xFFFFFFFF, true);
		float hp = e.getHealth() + e.getAbsorptionAmount();
		g.drawString(font, String.format(Locale.ROOT, "%.1f HP", hp), tx, y + 17, 0xFFB8B8C0, true);

		int bx = tx;
		int bw = x + w - 6 - bx;
		int by = y + 29;
		GuiUtil.rrect(g, bx, by, bw, 4, 2, 0x55FFFFFF & 0x55FFFFFF);
		int fill = Math.max(2, (int) (bw * frac));
		int color = ColorUtil.hsb(frac * 0.33f, 0.7f, 1f);
		GuiUtil.rrect(g, bx, by, fill, 4, 2, 0xFF000000 | (color & 0xFFFFFF));

		if (t.armor.value) {
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
			int ix = x + 6;
			for (EquipmentSlot s : slots) {
				ItemStack st = e.getItemBySlot(s);
				if (!st.isEmpty()) {
					g.renderItem(st, ix, y + 34);
					g.renderItemDecorations(font, st, ix, y + 34);
				}
				ix += 20;
			}
		}
	}
}
