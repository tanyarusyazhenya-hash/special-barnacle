package dev.aurora.gui;

import dev.aurora.module.ModuleManager;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * Aurora look for the loading screens (connecting, joining a world, saving) and the disconnect
 * screen. The vanilla screens keep working underneath; this only paints over them.
 */
public final class ScreenOverlay {
	private static final Set<String> LOADING = Set.of("ReceivingLevelScreen", "LevelLoadingScreen", "ProgressScreen",
		"GenericMessageScreen", "GenericDirtMessageScreen");

	private ScreenOverlay() {}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (ModuleManager.ui == null || !ModuleManager.ui.customMenu.value) {
				return;
			}
			if (screen instanceof DisconnectedScreen) {
				ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> disconnected(client, s, g, mx, my, dt));
			} else if (screen instanceof ConnectScreen || LOADING.contains(screen.getClass().getSimpleName())) {
				ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> loading(client, s, g));
			}
		});
	}

	private static int c(int rgb, float a) {
		return ColorUtil.argb(rgb, a);
	}

	private static void background(GuiGraphics g, Screen s, int accent) {
		g.fillGradient(0, 0, s.width, s.height, 0xFF08080B, 0xFF000000 | blend(0x08080B, accent, 0.16f));
		float t = (System.currentTimeMillis() % 20000) / 20000f * (float) (Math.PI * 2);
		for (int i = 0; i < 3; i++) {
			int cx = (int) (s.width * (0.25 + 0.5 * (0.5 + 0.5 * Math.sin(t + i * 2.1))));
			int cy = (int) (s.height * (0.3 + 0.4 * (0.5 + 0.5 * Math.cos(t * 1.3 + i * 1.7))));
			for (int k = 4; k >= 1; k--) {
				int r = 26 + k * 20;
				GuiUtil.rrect(g, cx - r, cy - r, r * 2, r * 2, r, c(accent, 0.012f));
			}
		}
	}

	private static int blend(int a, int b, float k) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
		int gr = (int) (((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
		int bl = (int) ((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
		return (r << 16) | (gr << 8) | bl;
	}

	private static void logo(GuiGraphics g, Font font, Screen s, int y, float scale) {
		Component logo = Component.literal("aurora");
		g.pose().pushMatrix();
		g.pose().translate(s.width / 2f, y);
		g.pose().scale(scale, scale);
		g.drawString(font, logo, -font.width(logo) / 2, 0, 0xFFFFFFFF, true);
		g.pose().popMatrix();
	}

	private static void loading(Minecraft mc, Screen s, GuiGraphics g) {
		Font font = mc.font;
		int accent = ModuleManager.ui.accent();
		background(g, s, accent);
		int cy = s.height / 2;
		logo(g, font, s, cy - 40, 3f);
		String title = s.getTitle().getString();
		if (title.isEmpty()) {
			title = "Loading";
		}
		g.drawString(font, title, (s.width - font.width(title)) / 2, cy, 0xFFE0E0E8, true);

		// bouncing bar
		int bw = 140;
		int bx = (s.width - bw) / 2;
		int by = cy + 18;
		GuiUtil.rrect(g, bx, by, bw, 3, 1, c(0xFFFFFF, 0.12f));
		float pos = (System.currentTimeMillis() % 1400) / 1400f;
		float p = pos < 0.5f ? pos * 2f : (1f - pos) * 2f;
		int segW = 40;
		int segX = bx + (int) ((bw - segW) * p);
		for (int i = 0; i < segW; i++) {
			g.fill(segX + i, by, segX + i + 1, by + 3, c(ModuleManager.ui.gradientAt(i / (float) segW), 1f));
		}
		String hint = "Esc to cancel";
		g.drawString(font, hint, (s.width - font.width(hint)) / 2, by + 14, 0xFF6A6A74, false);
	}

	private static void disconnected(Minecraft mc, Screen s, GuiGraphics g, int mx, int my, float dt) {
		Font font = mc.font;
		int accent = ModuleManager.ui.accent();
		background(g, s, accent);
		List<AbstractWidget> buttons = Screens.getButtons(s).stream()
			.filter(w -> w instanceof AbstractWidget).map(w -> (AbstractWidget) w).toList();
		int firstButtonY = s.height / 2 + 20;
		for (AbstractWidget w : buttons) {
			firstButtonY = Math.min(firstButtonY == s.height / 2 + 20 ? w.getY() : firstButtonY, w.getY());
		}
		logo(g, font, s, 24, 2f);
		g.fill(s.width / 2 - 30, 46, s.width / 2 + 30, 47, c(accent, 0.9f));

		String head = "Disconnected";
		g.pose().pushMatrix();
		g.pose().translate(s.width / 2f, Math.max(56, firstButtonY - 90));
		g.pose().scale(1.6f, 1.6f);
		g.drawString(font, head, -font.width(head) / 2, 0, 0xFFFF6666, true);
		g.pose().popMatrix();

		Component reason = reason(s);
		if (reason != null) {
			int y = Math.max(56, firstButtonY - 90) + 22;
			for (var line : font.split(reason, Math.min(320, s.width - 40))) {
				g.drawString(font, line, (s.width - font.width(line)) / 2, y, 0xFFD0D0D8, true);
				y += 11;
				if (y > firstButtonY - 8) {
					break;
				}
			}
		}
		for (AbstractWidget w : buttons) {
			w.render(g, mx, my, dt);
		}
	}

	private static Component reason(Screen s) {
		Component longest = null;
		try {
			for (Field f : DisconnectedScreen.class.getDeclaredFields()) {
				f.setAccessible(true);
				Object v = f.get(s);
				if (v instanceof DisconnectionDetails d) {
					return d.reason();
				}
				if (v instanceof Component comp) {
					if (longest == null || comp.getString().length() > longest.getString().length()) {
						longest = comp;
					}
				}
			}
		} catch (Throwable ignored) {
			// fall through
		}
		return longest;
	}
}
