package dev.aurora.hud;

import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.HudModule;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Draws the watermark, the enabled-module list and the info stack. */
public final class HudRenderer {
	private static final int ROW_H = 11;
	private static final int BG = 0x0B0B10;

	private HudRenderer() {}

	public static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		HudModule h = ModuleManager.hud;
		if (h == null || !h.isEnabled() || mc.options.hideGui || mc.player == null || mc.level == null) {
			return;
		}
		Font font = mc.font;
		int accent = ModuleManager.ui.accent();
		int gw = g.guiWidth();
		int gh = g.guiHeight();

		// Watermark (top-left)
		int topLeftY = 4;
		if (h.watermark.value) {
			String name = "Aurora";
			String version = " §71.21.11";
			int bw = font.width(name) + font.width(version) + 10;
			if (h.background.value) {
				GuiUtil.rrect(g, 4, 4, bw, 14, 3, ColorUtil.argb(BG, h.bgOpacity.f()));
			}
			if (h.accentBar.value) {
				g.fill(4, 6, 6, 16, ColorUtil.argb(colorFor(h, accent, 0), 1f));
			}
			text(g, font, h, name + version, 10, 8, colorFor(h, accent, 0));
			topLeftY = 22;
		}

		// Module list
		if (h.moduleList.value) {
			List<String> labels = new ArrayList<>();
			for (Module m : ModuleManager.all()) {
				if (!m.isEnabled() || !m.showInList()) {
					continue;
				}
				String info = m.info();
				labels.add(h.listInfo.value && info != null ? m.name + " §7" + info : m.name);
			}
			labels.sort(Comparator.comparingInt((String s) -> font.width(s)).reversed());

			boolean right = h.listSide.is("Right");
			int y = right ? 4 : topLeftY;
			int i = 0;
			for (String label : labels) {
				drawRow(g, font, h, label, right, gw, y, colorFor(h, accent, i++));
				y += ROW_H + 1;
			}
		}

		// Info stack (bottom)
		List<String> lines = new ArrayList<>();
		if (h.fps.value) {
			lines.add("FPS §7" + mc.getFps());
		}
		if (h.ping.value) {
			ClientPacketListener connection = mc.getConnection();
			PlayerInfo info = connection == null ? null : connection.getPlayerInfo(mc.player.getUUID());
			lines.add("Ping §7" + (info == null ? 0 : info.getLatency()) + "ms");
		}
		if (h.coords.value) {
			lines.add(String.format(Locale.ROOT, "XYZ §7%.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
		}
		if (h.speed.value) {
			lines.add(String.format(Locale.ROOT, "Speed §7%.1f b/s", h.speedBps));
		}
		if (h.direction.value) {
			Direction d = mc.player.getDirection();
			String n = d.getName();
			String facing = Character.toUpperCase(n.charAt(0)) + n.substring(1);
			lines.add(String.format(Locale.ROOT, "Facing §7%s (%.0f)", facing, Mth.wrapDegrees(mc.player.getYRot())));
		}
		if (!lines.isEmpty()) {
			boolean right = h.infoSide.is("Right");
			int y = gh - 4 - lines.size() * (ROW_H + 1) + 1;
			int i = 0;
			for (String line : lines) {
				drawRow(g, font, h, line, right, gw, y, colorFor(h, accent, i++));
				y += ROW_H + 1;
			}
		}
	}

	private static void drawRow(GuiGraphics g, Font font, HudModule h, String label, boolean right, int gw, int y, int rgb) {
		int bw = font.width(label) + 8;
		int x = right ? gw - 4 - bw : 4;
		if (h.background.value) {
			GuiUtil.rrect(g, x, y, bw, ROW_H, 2, ColorUtil.argb(BG, h.bgOpacity.f()));
		}
		if (h.accentBar.value) {
			int bx = right ? x + bw - 2 : x;
			g.fill(bx, y + 1, bx + 2, y + ROW_H - 1, ColorUtil.argb(rgb, 1f));
		}
		text(g, font, h, label, x + (right ? 4 : 6), y + 2, rgb);
	}

	private static void text(GuiGraphics g, Font font, HudModule h, String s, int x, int y, int rgb) {
		g.drawString(font, s, x, y, 0xFF000000 | (rgb & 0xFFFFFF), h.shadow.value);
	}

	private static int colorFor(HudModule h, int accent, int index) {
		if (h.colorMode.is("Rainbow")) {
			float hue = ((System.currentTimeMillis() % 4000L) / 4000f - index * 0.05f + 1f) % 1f;
			return ColorUtil.hsb(hue, 0.65f, 1f);
		}
		if (h.colorMode.is("White")) {
			return 0xFFFFFF;
		}
		return accent;
	}
}
