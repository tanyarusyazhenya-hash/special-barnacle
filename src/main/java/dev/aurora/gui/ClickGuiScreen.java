package dev.aurora.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.InterfaceModule;
import dev.aurora.setting.ActionSetting;
import dev.aurora.setting.BindSetting;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.setting.Setting;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One dark window with an orange glow: title bar, category sidebar, a card with the modules of the
 * selected category and a card with the settings of the selected module.
 *
 * Left click a module: toggle it and show its settings. Right click: show its settings only.
 * Drag the title bar to move the window. The "-" button collapses it. Scroll over a card to scroll.
 */
public class ClickGuiScreen extends Screen {
	private static final int TB = 28; // title bar height
	private static final int SB = 92; // sidebar width
	private static final int ROW = 22; // module row height
	private static final int TEXT = 0xD2D2D8;
	private static final int DIM = 0x7C7C86;

	private static Category selected = Category.RENDER;
	private static final Map<Category, Module> FOCUS = new EnumMap<>(Category.class);
	private static int winX = Integer.MIN_VALUE;
	private static int winY = Integer.MIN_VALUE;
	private static boolean minimized;
	private final StringBuilder search = new StringBuilder();
	private boolean searchFocused;

	private interface Drag {
		void on(double mx, double my);
	}

	private interface Click {
		void on(double mx, double my, int button);
	}

	private record Region(int x, int y, int w, int h, int clipTop, int clipBottom, Click click) {
		boolean hit(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipBottom;
		}
	}

	private final List<Region> regions = new ArrayList<>();
	private final Set<Setting> openModes = new HashSet<>();
	private final long openedAt = System.currentTimeMillis();

	private Drag drag;
	private boolean movingWindow;
	private int moveDx, moveDy;
	private BindSetting listening;

	private float alpha;
	private float minAnim = minimized ? 0f : 1f;
	private int clipTop, clipBottom;
	private int winW, winH, x0, y0;

	private float listScroll, settScroll;
	private int listContent, settContent;
	private int[] listRect = new int[4];
	private int[] settRect = new int[4];

	public ClickGuiScreen() {
		super(Component.literal("Aurora"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		if (listening != null) {
			listening.listening = false;
			listening = null;
		}
		ModuleManager.save();
		super.removed();
	}

	// ------------------------------------------------------------------ frame

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		regions.clear();
		clipTop = 0;
		clipBottom = height;

		float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 220f);
		alpha = 1f - (1f - t) * (1f - t) * (1f - t);

		Font font = Minecraft.getInstance().font;
		InterfaceModule ui = ModuleManager.ui;
		int accent = ui.accent();
		if (ui.dim.value) {
			g.fill(0, 0, width, height, ColorUtil.argb(0x000000, ui.dimOpacity.f() * alpha));
		}

		// greeting and credit at the top of the screen (drawn first, so the window covers them if it overlaps)
		if (ui.greeting.value) {
			int slide = Math.round((1f - alpha) * -10f);
			Component hello = Component.literal("Здравствуй").withStyle(ChatFormatting.BOLD);
			centeredScaled(g, font, hello, width / 2, 10 + slide, 2f, c(0xFFFFFF, 1f));
			g.fill(width / 2 - 36, 29 + slide, width / 2 + 36, 30 + slide, c(accent, 0.9f));
			Component credit = Component.literal("Создатель: ")
				.append(Component.literal("winidy").withStyle(Style.EMPTY.withColor(accent)));
			centeredScaled(g, font, credit, width / 2, 35 + slide, 1f, c(0x9A9AA4, 1f));
		}

		winW = Mth.clamp((int) (width * 0.8), 400, 620);
		winH = Mth.clamp((int) (height * 0.8), 240, 360);
		if (winX == Integer.MIN_VALUE) {
			winX = (width - winW) / 2;
			winY = (height - winH) / 2;
		}
		winX = Mth.clamp(winX, 80 - winW, width - 80);
		winY = Mth.clamp(winY, 0, Math.max(0, height - TB));
		x0 = winX;
		y0 = winY + Math.round((1f - alpha) * -12f);

		float goal = minimized ? 0f : 1f;
		minAnim += (goal - minAnim) * 0.25f;
		if (Math.abs(goal - minAnim) < 0.01f) {
			minAnim = goal;
		}
		int curH = TB + Math.round((winH - TB) * minAnim);

		// glow, border, body
		for (int i = 6; i >= 1; i--) {
			GuiUtil.rrect(g, x0 - i * 2, y0 - i * 2 + 4, winW + i * 4, curH + i * 4, 10 + i * 2, c(accent, 0.016f * (7 - i)));
		}
		GuiUtil.rrect(g, x0 - 1, y0 - 1, winW + 2, curH + 2, 11, c(accent, 0.42f));
		GuiUtil.rrect(g, x0, y0, winW, curH, 10, c(0x0A0A0D, 0.97f));
		if (curH > TB + 6) {
			g.fillGradient(x0 + 2, y0 + TB + 1, x0 + winW - 2, y0 + curH - 3, c(accent, 0f), c(accent, 0.07f));
		}

		renderTitleBar(g, font, mx, my, accent);

		if (minAnim > 0.02f) {
			int bottom = y0 + curH;
			int prevTop = clipTop, prevBottom = clipBottom;
			clipTop = Math.max(clipTop, y0 + TB + 1);
			clipBottom = Math.min(clipBottom, bottom - 1);
			g.enableScissor(x0, y0 + TB + 1, x0 + winW, bottom - 1);

			renderSidebar(g, font, mx, my, accent);
			renderCards(g, font, mx, my, accent);

			g.disableScissor();
			clipTop = prevTop;
			clipBottom = prevBottom;
		}
	}

	private void centeredScaled(GuiGraphics g, Font font, Component text, int cx, int y, float scale, int color) {
		int w = font.width(text);
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		g.drawString(font, text, -w / 2, 0, color, true);
		g.pose().popMatrix();
	}

	private void renderTitleBar(GuiGraphics g, Font font, int mx, int my, int accent) {
		GuiUtil.rrect(g, x0, y0, winW, TB, 10, c(0x101013, 1f));
		g.fill(x0, y0 + TB - 7, x0 + winW, y0 + TB, c(0x111114, 1f));
		if (ModuleManager.ui.gradient.value) {
			for (int i = 0; i < winW - 2; i++) {
				g.fill(x0 + 1 + i, y0 + TB, x0 + 2 + i, y0 + TB + 2, c(ModuleManager.ui.gradientAt(i / (float) (winW - 2)), 0.9f));
			}
		} else {
			g.fill(x0 + 1, y0 + TB, x0 + winW - 1, y0 + TB + 1, c(0xFFFFFF, 0.06f));
		}

		logo(g, font, x0 + 12, y0 + 7, accent);
		g.drawString(font, "aurora", x0 + 32, y0 + 10, c(0xFFFFFF, 1f), false);

		// minimize and close
		int bx = x0 + winW - 48;
		int by = y0 + 6;
		boolean hoverMin = hit(mx, my, bx, by, 16, 16);
		boolean hoverClose = hit(mx, my, bx + 22, by, 16, 16);
		if (hoverMin) {
			GuiUtil.rrect(g, bx, by, 16, 16, 4, c(0xFFFFFF, 0.08f));
		}
		if (hoverClose) {
			GuiUtil.rrect(g, bx + 22, by, 16, 16, 4, c(0xFFFFFF, 0.08f));
		}
		int minColor = c(hoverMin ? 0xFFFFFF : accent, 1f);
		g.fill(bx + 3, by + 8, bx + 13, by + 10, minColor);
		int cx = bx + 22 + 4;
		int cy = by + 4;
		int closeColor = c(hoverClose ? 0xFFFFFF : accent, 1f);
		for (int i = 0; i < 8; i++) {
			g.fill(cx + i, cy + i, cx + i + 2, cy + i + 1, closeColor);
			g.fill(cx + 7 - i, cy + i, cx + 9 - i, cy + i + 1, closeColor);
		}

		// regions: title bar drag first so the buttons win where they overlap
		addRegion(x0, y0, winW, TB, (clickX, clickY, button) -> {
			if (button == 0) {
				movingWindow = true;
				moveDx = (int) clickX - winX;
				moveDy = (int) clickY - winY;
			}
		});
		int sx = x0 + 84;
		int sw = Math.max(60, Math.min(150, bx - 72 - sx - 6));
		GuiUtil.rrect(g, sx, by, sw, 16, 4, c(searchFocused ? accent : 0xFFFFFF, searchFocused ? 0.6f : 0.12f));
		GuiUtil.rrect(g, sx + 1, by + 1, sw - 2, 14, 3, c(0x101013, 1f));
		if (search.length() == 0 && !searchFocused) {
			g.drawString(font, "Search modules...", sx + 6, by + 4, c(0x6A6A74, 1f), false);
		} else {
			boolean caret = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
			g.drawString(font, font.plainSubstrByWidth(search.toString(), sw - 14) + (caret ? "_" : ""), sx + 6,
				by + 4, c(0xFFFFFF, 1f), false);
		}
		addRegion(sx, by, sw, 16, (clickX, clickY, button) -> {
			if (button == 0) {
				searchFocused = true;
			} else if (button == 1) {
				search.setLength(0);
				listScroll = 0;
			}
		});
		int gx = bx - 66;
		boolean hoverGroups = hit(mx, my, gx, by, 60, 16);
		GuiUtil.rrect(g, gx, by, 60, 16, 4, c(accent, hoverGroups ? 0.35f : 0.18f));
		g.drawString(font, "Groups", gx + 30 - font.width("Groups") / 2, by + 4, c(0xFFFFFF, 1f), false);
		addRegion(gx, by, 60, 16, (clickX, clickY, button) -> {
			if (button == 0) {
				minecraft.setScreen(new GroupsScreen(this));
			}
		});
		addRegion(bx, by, 16, 16, (clickX, clickY, button) -> {
			if (button == 0) {
				minimized = !minimized;
			}
		});
		addRegion(bx + 22, by, 16, 16, (clickX, clickY, button) -> {
			if (button == 0) {
				onClose();
			}
		});
	}

	private void logo(GuiGraphics g, Font font, int x, int y, int accent) {
		GuiUtil.rrect(g, x, y, 14, 14, 3, c(accent, 1f));
		g.drawString(font, Component.literal("A").withStyle(ChatFormatting.BOLD), x + 4, y + 3, c(0x0C0C0E, 1f), false);
	}

	// ------------------------------------------------------------------ sidebar

	private void renderSidebar(GuiGraphics g, Font font, int mx, int my, int accent) {
		g.fill(x0 + SB, y0 + TB + 1, x0 + SB + 1, y0 + winH - 4, c(0xFFFFFF, 0.05f));

		int y = y0 + TB + 10;
		for (Category cat : Category.values()) {
			if (cat == Category.CLIENT) {
				continue;
			}
			sidebarItem(g, font, cat, y, mx, my, accent);
			y += 26;
		}

		y += 4;
		g.fill(x0 + 10, y, x0 + SB - 10, y + 1, c(0xFFFFFF, 0.08f));
		g.fill(x0 + 10, y, x0 + 38, y + 1, c(accent, 0.9f));
		y += 9;
		sidebarItem(g, font, Category.CLIENT, y, mx, my, accent);

		// footer
		int fx = x0 + 8;
		int fy = y0 + winH - 38;
		int fw = SB - 16;
		GuiUtil.rrect(g, fx, fy, fw, 30, 5, c(0xFFFFFF, 0.04f));
		logo(g, font, fx + 6, fy + 8, accent);
		g.drawString(font, "Aurora", fx + 26, fy + 7, c(0xE8E8EE, 1f), false);
		g.drawString(font, "v0.3.0 ♥", fx + 26, fy + 17, c(DIM, 1f), false);
	}

	private void sidebarItem(GuiGraphics g, Font font, Category cat, int y, int mx, int my, int accent) {
		int x = x0 + 6;
		int w = SB - 12;
		int h = 24;
		boolean sel = cat == selected;
		boolean hover = hit(mx, my, x, y, w, h);

		if (sel) {
			for (int i = 0; i < w; i++) {
				g.fill(x + i, y, x + i + 1, y + h, c(accent, 0.34f * (1f - i / (float) w)));
			}
			g.fill(x, y + 2, x + 2, y + h - 2, c(accent, 1f));
		} else if (hover) {
			GuiUtil.rrect(g, x, y, w, h, 4, c(0xFFFFFF, 0.05f));
		}

		int tint = sel ? accent : (hover ? 0xB4B4BE : 0x8A8A94);
		icon(g, cat, x + 12, y + 7, tint);
		String label = cat == Category.CLIENT ? "Settings" : cat.label;
		g.drawString(font, label, x + 30, y + 8, c(sel ? 0xFFFFFF : (hover ? 0xD0D0D8 : 0x9A9AA4), 1f), false);

		addRegion(x, y, w, h, (clickX, clickY, button) -> {
			if (button == 0) {
				selected = cat;
				listScroll = 0;
				settScroll = 0;
			}
		});
	}

	/** Tiny pixel icons, 10x10. */
	private void icon(GuiGraphics g, Category cat, int ix, int iy, int rgb) {
		int col = c(rgb, 1f);
		switch (cat) {
			case COMBAT -> { // crossed swords
				for (int i = 0; i < 10; i++) {
					g.fill(ix + i, iy + i, ix + i + 2, iy + i + 1, col);
					g.fill(ix + 8 - i, iy + i, ix + 10 - i, iy + i + 1, col);
				}
			}
			case RENDER -> { // monitor
				outline(g, ix, iy, 10, 7, col);
				g.fill(ix + 4, iy + 7, ix + 6, iy + 9, col);
				g.fill(ix + 2, iy + 9, ix + 8, iy + 10, col);
			}
			case WORLD -> { // cube
				outline(g, ix, iy + 2, 8, 8, col);
				outline(g, ix + 2, iy, 8, 8, col);
			}
			case UTILITY -> { // plus / tool
				g.fill(ix + 4, iy, ix + 6, iy + 10, col);
				g.fill(ix, iy + 4, ix + 10, iy + 6, col);
				g.fill(ix + 2, iy + 2, ix + 8, iy + 8, c(rgb, 0.35f));
			}
			case HUD -> { // list
				g.fill(ix, iy + 1, ix + 10, iy + 3, col);
				g.fill(ix, iy + 4, ix + 7, iy + 6, col);
				g.fill(ix, iy + 7, ix + 10, iy + 9, col);
			}
			default -> { // settings ring
				outline(g, ix + 1, iy + 1, 8, 8, col);
				g.fill(ix + 4, iy + 4, ix + 6, iy + 6, col);
				g.fill(ix + 4, iy, ix + 6, iy + 2, col);
				g.fill(ix + 4, iy + 8, ix + 6, iy + 10, col);
				g.fill(ix, iy + 4, ix + 2, iy + 6, col);
				g.fill(ix + 8, iy + 4, ix + 10, iy + 6, col);
			}
		}
	}

	private static void outline(GuiGraphics g, int x, int y, int w, int h, int col) {
		g.fill(x, y, x + w, y + 1, col);
		g.fill(x, y + h - 1, x + w, y + h, col);
		g.fill(x, y, x + 1, y + h, col);
		g.fill(x + w - 1, y, x + w, y + h, col);
	}

	// ------------------------------------------------------------------ cards

	private void renderCards(GuiGraphics g, Font font, int mx, int my, int accent) {
		int cardY = y0 + TB + 8;
		int cardH = winH - TB - 16;
		int contentW = winW - SB - 16;
		int leftW = (int) (contentW * 0.36f);
		int lx = x0 + SB + 8;
		int rx = lx + leftW + 8;
		int rightW = contentW - leftW - 8;
		listRect = new int[]{lx, cardY, leftW, cardH};
		settRect = new int[]{rx, cardY, rightW, cardH};

		// ---- module list card
		card(g, lx, cardY, leftW, cardH);
		icon(g, selected, lx + 10, cardY + 8, accent);
		boolean searching = search.length() > 0;
		String title = searching ? "Search" : selected == Category.CLIENT ? "Settings" : selected.label;
		g.drawString(font, title, lx + 26, cardY + 9, c(0xFFFFFF, 1f), false);
		g.fill(lx + 8, cardY + 24, lx + leftW - 8, cardY + 25, c(0xFFFFFF, 0.07f));

		List<Module> mods;
		if (searching) {
			mods = new ArrayList<>();
			String q = search.toString().toLowerCase();
			for (Module m : ModuleManager.all()) {
				if (m.name.toLowerCase().contains(q) || m.description.toLowerCase().contains(q)) {
					mods.add(m);
				}
			}
		} else {
			mods = ModuleManager.byCategory(selected);
		}
		Module focus = FOCUS.get(selected);
		if (focus == null || !mods.contains(focus)) {
			focus = mods.isEmpty() ? null : mods.get(0);
			if (focus != null) {
				FOCUS.put(selected, focus);
			}
		}

		int top = cardY + 28;
		int bottom = cardY + cardH - 16;
		listContent = mods.size() * ROW + 4;
		listScroll = Mth.clamp(listScroll, 0f, Math.max(0, listContent - (bottom - top)));

		int prevTop = clipTop, prevBottom = clipBottom;
		clipTop = Math.max(clipTop, top);
		clipBottom = Math.min(clipBottom, bottom);
		g.enableScissor(lx, top, lx + leftW, bottom);
		int y = top - Math.round(listScroll);
		for (Module m : mods) {
			moduleRow(g, font, m, m == focus, lx, leftW, y, mx, my, accent, selected);
			y += ROW;
		}
		g.disableScissor();
		clipTop = prevTop;
		clipBottom = prevBottom;
		scrollbar(g, lx + leftW - 4, top, bottom - top, listContent, listScroll, accent);
		g.drawString(font, "L: toggle  R: options", lx + 10, cardY + cardH - 12, c(0x5A5A64, 1f), false);

		// ---- settings card
		card(g, rx, cardY, rightW, cardH);
		final Module fm = focus;
		if (fm == null) {
			g.drawString(font, "Nothing here yet", rx + 12, cardY + 12, c(DIM, 1f), false);
			return;
		}
		g.drawString(font, fm.name, rx + 12, cardY + 8, c(0xFFFFFF, 1f), false);
		int descW = rightW - 24 - (fm.hasToggle() ? 36 : 0);
		g.drawString(font, trim(font, fm.description, descW), rx + 12, cardY + 20, c(DIM, 1f), false);
		if (fm.hasToggle()) {
			toggle(g, rx + rightW - 12 - 26, cardY + 10, fm.enableAnim, accent);
			addRegion(rx + rightW - 16 - 26, cardY + 6, 34, 20, (clickX, clickY, button) -> {
				if (button == 0) {
					fm.toggle();
				}
			});
		}
		g.fill(rx + 8, cardY + 34, rx + rightW - 8, cardY + 35, c(0xFFFFFF, 0.07f));

		int sTop = cardY + 38;
		int sBottom = cardY + cardH - 4;
		int total = 0;
		for (Setting s : fm.settings) {
			if (s.isVisible()) {
				total += rowHeight(s);
			}
		}
		settContent = total + 4;
		settScroll = Mth.clamp(settScroll, 0f, Math.max(0, settContent - (sBottom - sTop)));

		if (fm.settings.isEmpty()) {
			g.drawString(font, "No settings", rx + 12, sTop + 4, c(DIM, 1f), false);
			return;
		}

		prevTop = clipTop;
		prevBottom = clipBottom;
		clipTop = Math.max(clipTop, sTop);
		clipBottom = Math.min(clipBottom, sBottom);
		g.enableScissor(rx, sTop, rx + rightW, sBottom);
		int sy = sTop - Math.round(settScroll);
		for (Setting s : fm.settings) {
			if (!s.isVisible()) {
				continue;
			}
			renderSetting(g, font, s, rx, rightW, sy, mx, my, accent);
			sy += rowHeight(s);
		}
		g.disableScissor();
		clipTop = prevTop;
		clipBottom = prevBottom;
		scrollbar(g, rx + rightW - 4, sTop, sBottom - sTop, settContent, settScroll, accent);
	}

	private void card(GuiGraphics g, int x, int y, int w, int h) {
		GuiUtil.rrect(g, x - 1, y - 1, w + 2, h + 2, 9, c(0xFFFFFF, 0.06f));
		GuiUtil.rrect(g, x, y, w, h, 8, c(0x121215, 0.96f));
		g.fill(x + 8, y, x + w - 8, y + 1, c(0xFFFFFF, 0.06f));
	}

	private void scrollbar(GuiGraphics g, int x, int top, int view, int content, float scroll, int accent) {
		if (content <= view) {
			return;
		}
		float frac = scroll / (content - view);
		int barH = Math.max(12, view * view / content);
		int barY = top + Math.round((view - barH) * frac);
		g.fill(x, barY, x + 2, barY + barH, c(accent, 0.75f));
	}

	private void moduleRow(GuiGraphics g, Font font, Module m, boolean isFocus, int lx, int leftW, int y, int mx, int my,
						   int accent, Category cat) {
		m.enableAnim += ((m.isEnabled() ? 1f : 0f) - m.enableAnim) * 0.2f;
		boolean hover = hit(mx, my, lx + 4, y, leftW - 8, ROW - 2);

		if (m.enableAnim > 0.02f) {
			GuiUtil.rrect(g, lx + 4, y, leftW - 8, ROW - 2, 6, c(accent, 0.16f * m.enableAnim));
		}
		if (isFocus) {
			GuiUtil.rrect(g, lx + 4, y, leftW - 8, ROW - 2, 6, c(0xFFFFFF, 0.06f));
			GuiUtil.rrect(g, lx + 5, y + 4, 2, ROW - 10, 1, c(accent, 1f));
		} else if (hover) {
			GuiUtil.rrect(g, lx + 4, y, leftW - 8, ROW - 2, 6, c(0xFFFFFF, 0.05f));
		}

		int tc = m.hasToggle() ? ColorUtil.lerp(0x8E8E98, 0xFFFFFF, m.enableAnim) : 0xFFFFFF;
		g.drawString(font, m.name, lx + 14, y + 6, c(tc, 1f), false);
		if (m.hasToggle()) {
			toggle(g, lx + leftW - 10 - 28, y + 3, m.enableAnim, accent);
		} else {
			g.drawString(font, ">", lx + leftW - 16, y + 6, c(DIM, 1f), false);
		}

		addRegion(lx + 4, y, leftW - 8, ROW - 2, (clickX, clickY, button) -> {
			if (FOCUS.get(cat) != m) {
				settScroll = 0;
			}
			FOCUS.put(cat, m);
			if (button == 0 && m.hasToggle()) {
				m.toggle();
			}
		});
	}

	// ------------------------------------------------------------------ settings

	private int rowHeight(Setting s) {
		if (s instanceof NumberSetting) {
			return 28;
		}
		if (s instanceof ColorSetting cs) {
			return 20 + Math.round(64 * cs.anim);
		}
		if (s instanceof ModeSetting ms) {
			return 22 + (openModes.contains(ms) ? ms.modes.length * 14 + 6 : 0);
		}
		if (s instanceof ActionSetting) {
			return 24;
		}
		return 20;
	}

	private void renderSetting(GuiGraphics g, Font font, Setting s, int sx, int cw, int y, int mx, int my, int accent) {
		int h = rowHeight(s);
		int lx = sx + 12;
		int rr = sx + cw - 12;

		if (s instanceof BoolSetting b) {
			b.anim += ((b.value ? 1f : 0f) - b.anim) * 0.3f;
			if (hit(mx, my, sx + 4, y, cw - 8, h)) {
				GuiUtil.rrect(g, sx + 4, y, cw - 8, h, 4, c(0xFFFFFF, 0.04f));
			}
			g.drawString(font, b.name, lx, y + 6, c(TEXT, 1f), false);
			toggle(g, rr - 26, y + 3, b.anim, accent);
			addRegion(sx + 4, y, cw - 8, h, (clickX, clickY, button) -> {
				if (button == 0) {
					b.value = !b.value;
				}
			});
		} else if (s instanceof NumberSetting n) {
			g.drawString(font, n.name, lx, y + 4, c(TEXT, 1f), false);
			String v = n.display();
			g.drawString(font, v, rr - font.width(v), y + 4, c(accent, 1f), false);

			int tx = lx;
			int tw = rr - lx;
			int ty = y + 18;
			GuiUtil.rrect(g, tx, ty, tw, 3, 1, c(0x2A2A30, 1f));
			float frac = (float) ((n.value - n.min) / (n.max - n.min));
			int fw = Math.max(2, Math.round(tw * frac));
			GuiUtil.rrect(g, tx, ty, fw, 3, 1, c(accent, 1f));
			GuiUtil.rrect(g, tx + fw - 4, ty - 2, 8, 8, 4, c(0xFFE9D6, 1f));

			addRegion(tx - 3, y + 12, tw + 6, 14, (clickX, clickY, button) -> {
				if (button == 0) {
					drag = (dx, dy) -> n.set(n.min + (dx - tx) / (double) tw * (n.max - n.min));
					drag.on(clickX, clickY);
				}
			});
		} else if (s instanceof ModeSetting ms) {
			boolean open = openModes.contains(ms);
			int bw = Math.min(100, cw / 2 - 8);
			int bx = rr - bw;
			g.drawString(font, ms.name, lx, y + 7, c(TEXT, 1f), false);
			GuiUtil.rrect(g, bx, y + 3, bw, 16, 4, c(open ? accent : 0x2A2A30, open ? 0.5f : 1f));
			GuiUtil.rrect(g, bx + 1, y + 4, bw - 2, 14, 3, c(0x1C1C20, 1f));
			g.drawString(font, trim(font, ms.get(), bw - 22), bx + 7, y + 7, c(0xFFFFFF, 1f), false);
			chevron(g, bx + bw - 9, y + 9, 0xA0A0AA, open);
			addRegion(sx + 4, y, cw - 8, 22, (clickX, clickY, button) -> {
				if (button == 0) {
					if (!openModes.remove(ms)) {
						openModes.add(ms);
					}
				} else if (button == 1) {
					ms.next();
				}
			});

			if (open) {
				int n = ms.modes.length;
				GuiUtil.rrect(g, bx, y + 21, bw, n * 14 + 4, 4, c(accent, 0.5f));
				GuiUtil.rrect(g, bx + 1, y + 22, bw - 2, n * 14 + 2, 3, c(0x1C1C20, 1f));
				for (int i = 0; i < n; i++) {
					int oy = y + 23 + i * 14;
					boolean sel = i == ms.index();
					if (hit(mx, my, bx + 2, oy, bw - 4, 14)) {
						GuiUtil.rrect(g, bx + 2, oy, bw - 4, 14, 3, c(0xFFFFFF, 0.07f));
					}
					g.drawString(font, ms.modes[i], bx + 8, oy + 3, c(sel ? accent : 0xC8C8D0, 1f), false);
					final int index = i;
					addRegion(bx + 2, oy, bw - 4, 14, (clickX, clickY, button) -> {
						if (button == 0) {
							ms.set(index);
							openModes.remove(ms);
						}
					});
				}
			}
		} else if (s instanceof BindSetting bs) {
			g.drawString(font, "Bind", lx, y + 6, c(TEXT, 1f), false);
			String v = bs.listening ? "..." : keyName(bs.key);
			int vw = font.width(v) + 10;
			GuiUtil.rrect(g, rr - vw, y + 2, vw, 16, 4, c(bs.listening ? ColorUtil.scale(accent, 0.55f) : 0x1C1C20, 1f));
			g.drawString(font, v, rr - vw + 5, y + 6, c(bs.listening ? 0xFFFFFF : accent, 1f), false);
			addRegion(sx + 4, y, cw - 8, h, (clickX, clickY, button) -> {
				if (button == 0) {
					boolean now = !bs.listening;
					if (listening != null) {
						listening.listening = false;
					}
					bs.listening = now;
					listening = now ? bs : null;
				} else if (button == 1) {
					bs.key = -1;
					bs.listening = false;
					listening = null;
				}
			});
		} else if (s instanceof ActionSetting act) {
			boolean hover = hit(mx, my, sx + 10, y + 3, cw - 20, 18);
			GuiUtil.rrect(g, sx + 10, y + 3, cw - 20, 18, 4, c(accent, hover ? 0.32f : 0.20f));
			g.drawString(font, act.name, sx + (cw - font.width(act.name)) / 2, y + 8, c(accent, 1f), false);
			addRegion(sx + 10, y + 3, cw - 20, 18, (clickX, clickY, button) -> {
				if (button == 0) {
					act.run();
				}
			});
		} else if (s instanceof ColorSetting cs) {
			cs.anim += ((cs.open ? 1f : 0f) - cs.anim) * 0.25f;
			g.drawString(font, cs.name, lx, y + 6, c(TEXT, 1f), false);
			GuiUtil.rrect(g, rr - 26, y + 3, 26, 14, 4, c(0x3A3A42, 1f));
			GuiUtil.rrect(g, rr - 25, y + 4, 24, 12, 3, c(cs.rgb(), 1f));
			addRegion(sx + 4, y, cw - 8, 20, (clickX, clickY, button) -> cs.open = !cs.open);

			if (cs.anim > 0.02f) {
				int prevTop = clipTop, prevBottom = clipBottom;
				clipTop = Math.max(clipTop, y + 20);
				clipBottom = Math.min(clipBottom, y + h);
				g.enableScissor(sx, y + 20, sx + cw, y + h);

				int px = lx;
				int py = y + 23;
				int sqW = (rr - lx) - 14;
				int sqH = 56;
				int hx = px + sqW + 4;
				for (int i = 0; i < sqW; i++) {
					float sat = i / (float) (sqW - 1);
					g.fillGradient(px + i, py, px + i + 1, py + sqH, c(ColorUtil.hsb(cs.hue, sat, 1f), 1f), c(0x000000, 1f));
				}
				for (int j = 0; j < sqH; j++) {
					g.fill(hx, py + j, hx + 10, py + j + 1, c(ColorUtil.hsb(j / (float) sqH, 1f, 1f), 1f));
				}
				int mxp = px + Math.round(cs.sat * (sqW - 1));
				int myp = py + Math.round((1f - cs.bri) * (sqH - 1));
				g.fill(mxp - 2, myp - 2, mxp + 3, myp + 3, c(0xFFFFFF, 1f));
				g.fill(mxp - 1, myp - 1, mxp + 2, myp + 2, c(cs.rgb(), 1f));
				int hy = py + Math.round(cs.hue * (sqH - 1));
				g.fill(hx - 1, hy - 1, hx + 11, hy + 1, c(0xFFFFFF, 1f));

				g.disableScissor();
				clipTop = prevTop;
				clipBottom = prevBottom;

				final int fpx = px, fpy = py, fsqW = sqW, fsqH = sqH;
				addRegion(fpx, fpy, fsqW, fsqH, (clickX, clickY, button) -> {
					if (button == 0) {
						drag = (dx, dy) -> {
							cs.sat = clamp01((float) ((dx - fpx) / (fsqW - 1)));
							cs.bri = 1f - clamp01((float) ((dy - fpy) / (fsqH - 1)));
						};
						drag.on(clickX, clickY);
					}
				});
				addRegion(hx, fpy, 10, fsqH, (clickX, clickY, button) -> {
					if (button == 0) {
						drag = (dx, dy) -> cs.hue = clamp01((float) ((dy - fpy) / (fsqH - 1)));
						drag.on(clickX, clickY);
					}
				});
			}
		}
	}

	// ------------------------------------------------------------------ widgets and helpers

	/** Pill switch like the reference: accent when on, grey when off. */
	private void toggle(GuiGraphics g, int x, int y, float anim, int accent) {
		int w = 28;
		int h = 14;
		if (anim > 0.02f) {
			GuiUtil.rrect(g, x - 2, y - 2, w + 4, h + 4, 8, c(accent, 0.22f * anim));
		}
		GuiUtil.rrect(g, x, y, w, h, 7, c(ColorUtil.lerp(0x26262C, accent, anim), 1f));
		int kx = x + 2 + Math.round((w - h) * anim);
		GuiUtil.rrect(g, kx, y + 2, 10, 10, 5, c(ColorUtil.lerp(0x8A8A92, 0xFFFFFF, anim), 1f));
	}

	private void chevron(GuiGraphics g, int cx, int cy, int rgb, boolean up) {
		for (int i = 0; i < 3; i++) {
			int yy = up ? cy + 2 - i : cy + i;
			g.fill(cx - 2 + i, yy, cx - 1 + i, yy + 1, c(rgb, 1f));
			g.fill(cx + 2 - i, yy, cx + 3 - i, yy + 1, c(rgb, 1f));
		}
	}

	private static String trim(Font font, String s, int maxW) {
		if (font.width(s) <= maxW) {
			return s;
		}
		String dots = "...";
		int end = s.length();
		while (end > 0 && font.width(s.substring(0, end) + dots) > maxW) {
			end--;
		}
		return s.substring(0, end) + dots;
	}

	private static String keyName(int key) {
		if (key < 0) {
			return "None";
		}
		return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
	}

	private void addRegion(int x, int y, int w, int h, Click click) {
		regions.add(new Region(x, y, w, h, clipTop, clipBottom, click));
	}

	private boolean hit(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipBottom;
	}

	private int c(int rgb, float a) {
		return ColorUtil.argb(rgb, a * alpha);
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static boolean inRect(int[] r, double mx, double my) {
		return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		searchFocused = false;
		for (int i = regions.size() - 1; i >= 0; i--) {
			Region r = regions.get(i);
			if (r.hit(mx, my)) {
				r.click().on(mx, my, button);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (drag != null) {
			drag.on(event.x(), event.y());
			return true;
		}
		if (movingWindow) {
			winX = (int) event.x() - moveDx;
			winY = (int) event.y() - moveDy;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (drag != null || movingWindow) {
			ModuleManager.save();
		}
		drag = null;
		movingWindow = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (inRect(listRect, mx, my)) {
			listScroll -= (float) (scrollY * 16.0);
			return true;
		}
		if (inRect(settRect, mx, my)) {
			settScroll -= (float) (scrollY * 18.0);
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (searchFocused && search.length() < 24 && event.codepoint() >= 32) {
			search.appendCodePoint(event.codepoint());
			listScroll = 0;
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening != null) {
			listening.key = event.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : event.key();
			listening.listening = false;
			listening = null;
			ModuleManager.save();
			return true;
		}
		if (searchFocused) {
			int k = event.key();
			if (k == GLFW.GLFW_KEY_BACKSPACE) {
				if (search.length() > 0) {
					search.setLength(search.length() - 1);
					listScroll = 0;
				}
				return true;
			}
			if (k == GLFW.GLFW_KEY_ESCAPE) {
				if (search.length() > 0) {
					search.setLength(0);
					listScroll = 0;
				}
				searchFocused = false;
				return true;
			}
			if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
				searchFocused = false;
				return true;
			}
			if (k == GLFW.GLFW_KEY_RIGHT_SHIFT || k == GLFW.GLFW_KEY_LEFT_SHIFT) {
				return true;
			}
		}
		if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
