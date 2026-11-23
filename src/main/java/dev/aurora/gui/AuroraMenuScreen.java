package dev.aurora.gui;

import dev.aurora.account.AccountManager;
import dev.aurora.module.ModuleManager;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Replaces the vanilla title screen: dark animated background, big logo and an account switcher. */
public class AuroraMenuScreen extends Screen {
	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Hit> hits = new ArrayList<>();
	private final long start = System.currentTimeMillis();

	public AuroraMenuScreen() {
		super(Component.literal("Aurora"));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private int c(int rgb, float a) {
		return ColorUtil.argb(rgb, a);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		hits.clear();
		Font font = minecraft.font;
		int accent = ModuleManager.ui.accent();
		float t = (System.currentTimeMillis() - start) / 1000f;

		g.fillGradient(0, 0, width, height, 0xFF08080B, 0xFF000000 | blend(0x08080B, accent, 0.18f));
		// slow drifting glows
		for (int i = 0; i < 4; i++) {
			int cx = (int) (width * (0.2 + 0.6 * (0.5 + 0.5 * Math.sin(t * 0.25 + i * 1.7))));
			int cy = (int) (height * (0.25 + 0.5 * (0.5 + 0.5 * Math.cos(t * 0.2 + i * 2.3))));
			for (int k = 4; k >= 1; k--) {
				int r = 30 + k * 22 + i * 8;
				GuiUtil.rrect(g, cx - r, cy - r, r * 2, r * 2, r, c(accent, 0.012f));
			}
		}

		// logo
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f - 86);
		g.pose().scale(4f, 4f);
		Component logo = Component.literal("aurora").withStyle(ChatFormatting.BOLD);
		g.drawString(font, logo, -font.width(logo) / 2, 0, c(0xFFFFFF, 1f), true);
		g.pose().popMatrix();
		g.fill(width / 2 - 40, height / 2 - 48, width / 2 + 40, height / 2 - 47, c(accent, 0.9f));

		int bw = 200;
		int bx = (width - bw) / 2;
		int by = height / 2 - 34;
		by = button(g, font, bx, by, bw, "Singleplayer", mx, my, accent,
			() -> minecraft.setScreen(new SelectWorldScreen(this)));
		by = button(g, font, bx, by, bw, "Multiplayer", mx, my, accent,
			() -> minecraft.setScreen(new JoinMultiplayerScreen(this)));
		by = button(g, font, bx, by, bw, "Accounts", mx, my, accent,
			() -> minecraft.setScreen(new AccountsScreen(this)));
		int half = (bw - 4) / 2;
		button(g, font, bx, by, half, "Options", mx, my, accent,
			() -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
		by = button(g, font, bx + half + 4, by, half, "Quit", mx, my, accent, () -> minecraft.stop());

		g.drawString(font, "Account: ", 8, height - 16, c(0x9A9AA4, 1f), false);
		g.drawString(font, AccountManager.currentName(), 8 + font.width("Account: "), height - 16, c(accent, 1f), false);
		Component credit = Component.literal("Aurora · by winidy");
		g.drawString(font, credit, width - font.width(credit) - 8, height - 16, c(0x6A6A74, 1f), false);
		super.render(g, mx, my, delta);
	}

	private static int blend(int a, int b, float k) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
		int gr = (int) (((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
		int bl = (int) ((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
		return (r << 16) | (gr << 8) | bl;
	}

	private int button(GuiGraphics g, Font font, int x, int y, int w, String label, int mx, int my, int accent,
					   Runnable action) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + 24;
		GuiUtil.rrect(g, x - 1, y - 1, w + 2, 26, 6, c(accent, hov ? 0.8f : 0.3f));
		GuiUtil.rrect(g, x, y, w, 24, 5, c(hov ? 0x1C1C22 : 0x101014, 0.96f));
		g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + 8, c(0xFFFFFF, 1f), false);
		hits.add(new Hit(x, y, w, 24, action));
		return y + 30;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (Hit h : hits) {
				if (h.contains(event.x(), event.y())) {
					h.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
