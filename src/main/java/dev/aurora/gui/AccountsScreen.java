package dev.aurora.gui;

import dev.aurora.account.AccountManager;
import dev.aurora.module.ModuleManager;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Account switcher window: saved offline names, click to use. */
public class AccountsScreen extends Screen {
	private static final int W = 300;
	private static final int H = 240;

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final StringBuilder input = new StringBuilder();
	private String message = "";
	private int scroll;

	public AccountsScreen(Screen parent) {
		super(Component.literal("Accounts"));
		this.parent = parent;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private int c(int rgb, float a) {
		return ColorUtil.argb(rgb, a);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		hits.clear();
		Font font = minecraft.font;
		int accent = ModuleManager.ui.accent();
		g.fill(0, 0, width, height, c(0x000000, 0.55f));
		int x0 = (width - W) / 2;
		int y0 = (height - H) / 2;
		for (int i = 3; i >= 1; i--) {
			GuiUtil.rrect(g, x0 - i * 2, y0 - i * 2, W + i * 4, H + i * 4, 8 + i * 2, c(accent, 0.035f * (4 - i)));
		}
		GuiUtil.rrect(g, x0 - 1, y0 - 1, W + 2, H + 2, 8, c(accent, 0.55f));
		GuiUtil.rrect(g, x0, y0, W, H, 7, c(0x0C0C0E, 0.97f));
		g.drawString(font, "Accounts", x0 + 12, y0 + 10, c(0xFFFFFF, 1f), false);
		button(g, font, x0 + W - 58, y0 + 6, 52, 16, "Back", mx, my, accent, this::onClose);

		int x = x0 + 12;
		int y = y0 + 32;
		g.drawString(font, "Current: ", x, y, c(0x9A9AA4, 1f), false);
		g.drawString(font, AccountManager.currentName(), x + font.width("Current: "), y, c(accent, 1f), false);
		y += 14;
		g.drawString(font, "Offline names only: works on cracked servers and singleplayer.", x, y,
			c(0x6A6A74, 1f), false);
		y += 16;

		int iw = W - 24 - 84;
		GuiUtil.rrect(g, x, y, iw, 18, 4, c(accent, 0.55f));
		GuiUtil.rrect(g, x + 1, y + 1, iw - 2, 16, 3, c(0x101013, 1f));
		boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
		g.drawString(font, input + (caret ? "_" : ""), x + 6, y + 5, c(0xFFFFFF, 1f), false);
		button(g, font, x + iw + 4, y, 36, 18, "Add", mx, my, accent, () -> {
			AccountManager.add(input.toString());
			input.setLength(0);
		});
		button(g, font, x + iw + 44, y, 36, 18, "Rnd", mx, my, accent, () -> input.replace(0, input.length(),
			AccountManager.randomName()));
		y += 26;

		List<String> names = new ArrayList<>(AccountManager.names());
		if (AccountManager.originalName() != null && !names.contains(AccountManager.originalName())) {
			names.add(0, AccountManager.originalName());
		}
		int rows = (y0 + H - 30 - y) / 22;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, names.size() - rows)));
		for (int i = scroll; i < Math.min(names.size(), scroll + rows); i++) {
			String n = names.get(i);
			boolean cur = n.equals(AccountManager.currentName());
			boolean hov = mx >= x && mx < x + W - 24 && my >= y && my < y + 20;
			GuiUtil.rrect(g, x, y, W - 24, 20, 4, c(cur ? accent : 0xFFFFFF, cur ? 0.3f : hov ? 0.12f : 0.05f));
			g.drawString(font, n + (n.equals(AccountManager.originalName()) ? "  (original)" : ""), x + 8, y + 6,
				c(0xFFFFFF, 1f), false);
			hits.add(new Hit(x, y, W - 24 - 20, 20, () -> {
				message = AccountManager.use(n) ? "Using " + n : "Could not switch";
			}));
			if (!n.equals(AccountManager.originalName())) {
				boolean hovX = mx >= x + W - 24 - 18 && mx < x + W - 24 - 4 && my >= y + 3 && my < y + 17;
				g.drawString(font, "x", x + W - 24 - 14, y + 6, hovX ? 0xFFFF5555 : c(0x9A9AA4, 1f), false);
				hits.add(new Hit(x + W - 24 - 18, y + 3, 14, 14, () -> AccountManager.remove(n)));
			}
			y += 22;
		}
		g.drawString(font, message, x, y0 + H - 18, c(0x9A9AA4, 1f), false);
		super.render(g, mx, my, delta);
	}

	private void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int mx, int my,
						int accent, Runnable action) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		GuiUtil.rrect(g, x, y, w, h, 4, c(accent, hov ? 0.55f : 0.35f));
		g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, c(0xFFFFFF, 1f), false);
		hits.add(new Hit(x, y, w, h, action));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				if (hits.get(i).contains(event.x(), event.y())) {
					hits.get(i).action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll -= (int) Math.signum(sy);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		int cp = event.codepoint();
		if (input.length() < 16 && (Character.isLetterOrDigit(cp) || cp == '_')) {
			input.appendCodePoint(cp);
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
			if (input.length() > 0) {
				input.setLength(input.length() - 1);
			}
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			AccountManager.add(input.toString());
			input.setLength(0);
			return true;
		}
		return super.keyPressed(event);
	}
}
