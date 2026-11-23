package dev.aurora.gui;

import dev.aurora.macro.MacroManager;
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
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Window to edit macros: click the key box and press a key, click the text box and type a command. */
public class MacrosScreen extends Screen {
	private static final int W = 400;
	private static final int H = 250;

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private int editText = -1;
	private int editKey = -1;
	private int scroll;

	public MacrosScreen(Screen parent) {
		super(Component.literal("Macros"));
		this.parent = parent;
		MacroManager.ensureLoaded();
	}

	@Override
	public void onClose() {
		MacroManager.save();
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
		g.drawString(font, "Macros", x0 + 12, y0 + 10, c(0xFFFFFF, 1f), false);
		g.drawString(font, "key sends a command (/home) or a chat message", x0 + 12 + font.width("Macros") + 8,
			y0 + 10, c(0x7A7A84, 1f), false);
		button(g, font, x0 + W - 58, y0 + 6, 52, 16, "Back", mx, my, accent, this::onClose);

		int x = x0 + 12;
		int y = y0 + 32;
		List<MacroManager.Macro> list = MacroManager.MACROS;
		int rows = (H - 32 - 34) / 24;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
		for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
			final int idx = i;
			MacroManager.Macro m = list.get(i);
			String keyLabel = editKey == i ? "press..." : m.key > 0 ? InputConstants.Type.KEYSYM.getOrCreate(m.key)
				.getDisplayName().getString() : "none";
			boolean keyHover = mx >= x && mx < x + 70 && my >= y && my < y + 20;
			GuiUtil.rrect(g, x, y, 70, 20, 4, c(editKey == i ? accent : 0xFFFFFF, editKey == i ? 0.4f : keyHover ? 0.15f : 0.08f));
			g.drawString(font, font.plainSubstrByWidth(keyLabel, 62), x + 35 - font.width(font.plainSubstrByWidth(keyLabel, 62)) / 2,
				y + 6, c(0xFFFFFF, 1f), false);
			hits.add(new Hit(x, y, 70, 20, () -> {
				editKey = idx;
				editText = -1;
			}));

			int tx = x + 76;
			int tw = W - 24 - 76 - 24;
			boolean focused = editText == i;
			GuiUtil.rrect(g, tx, y, tw, 20, 4, c(focused ? accent : 0xFFFFFF, focused ? 0.55f : 0.12f));
			GuiUtil.rrect(g, tx + 1, y + 1, tw - 2, 18, 3, c(0x101013, 1f));
			boolean caret = focused && (System.currentTimeMillis() / 500) % 2 == 0;
			if (m.text.isEmpty() && !focused) {
				g.drawString(font, "/command or message", tx + 6, y + 6, c(0x6A6A74, 1f), false);
			} else {
				String shown = m.text;
				while (font.width(shown) > tw - 14 && shown.length() > 1) {
					shown = shown.substring(1);
				}
				g.drawString(font, shown + (caret ? "_" : ""), tx + 6, y + 6, c(0xFFFFFF, 1f), false);
			}
			hits.add(new Hit(tx, y, tw, 20, () -> {
				editText = idx;
				editKey = -1;
			}));
			boolean hovX = mx >= x + W - 24 - 18 && mx < x + W - 24 && my >= y && my < y + 20;
			g.drawString(font, "x", x + W - 24 - 12, y + 6, hovX ? 0xFFFF5555 : c(0x9A9AA4, 1f), false);
			hits.add(new Hit(x + W - 24 - 18, y, 18, 20, () -> {
				MacroManager.MACROS.remove(idx);
				editText = -1;
				editKey = -1;
				MacroManager.save();
			}));
			y += 24;
		}
		if (list.isEmpty()) {
			g.drawString(font, "No macros yet. Press + Add.", x, y + 4, c(0x7A7A84, 1f), false);
		}
		button(g, font, x0 + 12, y0 + H - 30, 80, 20, "+ Add", mx, my, accent, () -> {
			MacroManager.MACROS.add(new MacroManager.Macro());
			editText = MacroManager.MACROS.size() - 1;
			editKey = -1;
			scroll = Integer.MAX_VALUE;
		});
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
			editText = -1;
			editKey = -1;
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
		if (editText >= 0 && editText < MacroManager.MACROS.size() && event.codepoint() >= 32) {
			MacroManager.Macro m = MacroManager.MACROS.get(editText);
			if (m.text.length() < 100) {
				m.text += new String(Character.toChars(event.codepoint()));
			}
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (editKey >= 0 && editKey < MacroManager.MACROS.size()) {
			MacroManager.MACROS.get(editKey).key = key == GLFW.GLFW_KEY_ESCAPE ? -1 : key;
			editKey = -1;
			MacroManager.save();
			return true;
		}
		if (editText >= 0 && editText < MacroManager.MACROS.size()) {
			MacroManager.Macro m = MacroManager.MACROS.get(editText);
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!m.text.isEmpty()) {
					m.text = m.text.substring(0, m.text.length() - 1);
				}
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
				editText = -1;
				MacroManager.save();
				return true;
			}
		}
		return super.keyPressed(event);
	}
}
