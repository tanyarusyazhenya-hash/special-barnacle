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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dark, rounded, accent-colored ClickGUI.
 * Left click a module to toggle it, right click to expand its settings.
 * Drag a panel header to move it, right click a header to collapse it, scroll to scroll a panel.
 */
public class ClickGuiScreen extends Screen {
	private static final int W = 142;
	private static final int HEADER_H = 24;
	private static final int MOD_H = 20;
	private static final int PAD = 7;

	private static final Map<Category, int[]> POSITIONS = new EnumMap<>(Category.class);

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

	private static final class Panel {
		final Category category;
		final int[] pos;
		boolean collapsed;
		float collapseAnim = 1f;
		float scroll;
		int contentH = 400; // corrected after the first frame

		Panel(Category category, int[] pos) {
			this.category = category;
			this.pos = pos;
		}
	}

	private final List<Panel> panels = new ArrayList<>();
	private final List<Region> regions = new ArrayList<>();
	private Drag drag;
	private Panel moving;
	private int moveDx, moveDy;
	private final long openedAt = System.currentTimeMillis();
	private float alpha = 0f;
	private int clipTop, clipBottom;
	private Module hoveredModule;
	private BindSetting listening;

	public ClickGuiScreen() {
		super(Component.literal("Aurora"));
		int i = 0;
		for (Category c : Category.values()) {
			final int index = i++;
			// x < 0 means "not placed yet"; init() lays the panels out once the screen size is known
			int[] pos = POSITIONS.computeIfAbsent(c, k -> new int[]{-1, 24});
			panels.add(new Panel(c, pos));
		}
	}

	@Override
	protected void init() {
		int n = panels.size();
		int gap = n > 1 ? Math.max(2, Math.min(12, (width - 24 - n * W) / (n - 1))) : 12;
		for (int i = 0; i < n; i++) {
			int[] pos = panels.get(i).pos;
			if (pos[0] < 0) {
				pos[0] = 12 + i * (W + gap);
			}
		}
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

	// ------------------------------------------------------------------ rendering

	@Override
	public void render(GuiGraphics g, int mx, int my, float delta) {
		regions.clear();
		hoveredModule = null;
		clipTop = 0;
		clipBottom = height;

		float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 220f);
		alpha = 1f - (1f - t) * (1f - t) * (1f - t);

		Font font = Minecraft.getInstance().font;
		InterfaceModule ui = ModuleManager.ui;
		if (ui.dim.value) {
			g.fill(0, 0, width, height, ColorUtil.argb(0x000000, ui.dimOpacity.f() * alpha));
		}
		int accent = ui.accent();

		for (Panel p : panels) {
			renderPanel(g, font, p, mx, my, accent);
		}

		if (hoveredModule != null && !hoveredModule.description.isEmpty()) {
			String d = hoveredModule.description;
			int tw = font.width(d);
			int tx = Math.min(mx + 10, width - tw - 10);
			int ty = my + 12;
			rrect(g, tx - 4, ty - 3, tw + 8, 14, 3, c(0x000000, 0.88f));
			g.drawString(font, d, tx, ty, c(0xE0E0F0, 1f), false);
		}
	}

	private void renderPanel(GuiGraphics g, Font font, Panel p, int mx, int my, int accent) {
		int x = p.pos[0];
		int y = p.pos[1] + Math.round((1f - alpha) * -14f);

		p.collapseAnim += ((p.collapsed ? 0f : 1f) - p.collapseAnim) * 0.25f;
		int bodyY = y + HEADER_H;
		int avail = Math.max(40, height - bodyY - 10);
		int fullVisible = Math.min(p.contentH, avail);
		int bodyH = Math.round(fullVisible * p.collapseAnim);
		int totalH = HEADER_H + bodyH;

		rrect(g, x - 1, y - 1, W + 2, totalH + 2, 5, c(0x000000, 0.30f));
		rrect(g, x, y, W, totalH, 4, c(0x0E0E14, 0.95f));
		rrect(g, x, y, W, HEADER_H, 4, c(0x17171F, 0.98f));
		if (bodyH > 0) {
			g.fill(x, y + HEADER_H - 4, x + W, y + HEADER_H, c(0x17171F, 0.98f));
		}
		g.fill(x + 8, y + HEADER_H - 1, x + W - 8, y + HEADER_H, c(accent, 0.9f));

		String title = p.category.label.toUpperCase(Locale.ROOT);
		g.drawString(font, title, x + (W - font.width(title)) / 2, y + (HEADER_H - 8) / 2, c(0xFFFFFF, 1f), false);

		addRegion(x, y, W, HEADER_H, (cx, cy, button) -> {
			if (button == 0) {
				moving = p;
				moveDx = (int) cx - p.pos[0];
				moveDy = (int) cy - p.pos[1];
			} else if (button == 1) {
				p.collapsed = !p.collapsed;
			}
		});

		if (bodyH > 2) {
			int maxScroll = Math.max(0, p.contentH - fullVisible);
			p.scroll = Math.max(0, Math.min(maxScroll, p.scroll));

			int prevTop = clipTop, prevBottom = clipBottom;
			clipTop = Math.max(clipTop, bodyY);
			clipBottom = Math.min(clipBottom, bodyY + bodyH);
			g.enableScissor(x, bodyY, x + W, bodyY + bodyH);

			int cy = bodyY + 2 - Math.round(p.scroll);
			int startY = cy;
			for (Module m : ModuleManager.byCategory(p.category)) {
				cy = renderModule(g, font, m, x, cy, mx, my, accent);
			}
			p.contentH = cy - startY + 4;

			g.disableScissor();
			clipTop = prevTop;
			clipBottom = prevBottom;

			if (maxScroll > 0) {
				float frac = p.scroll / maxScroll;
				int barH = Math.max(12, fullVisible * fullVisible / Math.max(1, p.contentH));
				int barY = bodyY + Math.round((bodyH - barH) * frac);
				g.fill(x + W - 2, barY, x + W - 1, barY + barH, c(accent, 0.7f));
			}
		}
	}

	private int renderModule(GuiGraphics g, Font font, Module m, int x, int cy, int mx, int my, int accent) {
		m.enableAnim += ((m.isEnabled() ? 1f : 0f) - m.enableAnim) * 0.2f;
		m.expandAnim += ((m.expanded ? 1f : 0f) - m.expandAnim) * 0.25f;

		boolean hover = hit(mx, my, x, cy, W, MOD_H);
		if (hover) {
			hoveredModule = m;
		}
		int base = ColorUtil.lerp(0x13131A, ColorUtil.scale(accent, 0.30f), m.hasToggle() ? m.enableAnim : 0f);
		if (hover) {
			base = ColorUtil.lerp(base, 0xFFFFFF, 0.05f);
		}
		g.fill(x, cy, x + W, cy + MOD_H, c(base, 1f));
		if (m.hasToggle() && m.enableAnim > 0.02f) {
			g.fill(x, cy + 3, x + 2, cy + MOD_H - 3, c(accent, m.enableAnim));
		}

		int textColor = m.hasToggle() ? ColorUtil.lerp(0xA0A0B4, 0xFFFFFF, m.enableAnim) : 0xFFFFFF;
		g.drawString(font, m.name, x + PAD + 2, cy + (MOD_H - 8) / 2, c(textColor, 1f), false);
		if (!m.settings.isEmpty()) {
			String indicator = m.expanded ? "-" : "+";
			g.drawString(font, indicator, x + W - PAD - font.width(indicator), cy + (MOD_H - 8) / 2, c(0x6B6B80, 1f), false);
		}

		addRegion(x, cy, W, MOD_H, (cx, cyy, button) -> {
			if (button == 0 && m.hasToggle()) {
				m.toggle();
			} else if (button == 1 || (button == 0 && !m.hasToggle())) {
				m.expanded = !m.expanded;
			}
		});
		cy += MOD_H;

		if (m.expandAnim > 0.01f) {
			int full = 0;
			for (Setting s : m.settings) {
				if (s.isVisible()) {
					full += rowHeight(s);
				}
			}
			int visible = Math.round(full * m.expandAnim);

			int prevTop = clipTop, prevBottom = clipBottom;
			clipTop = Math.max(clipTop, cy);
			clipBottom = Math.min(clipBottom, cy + visible);
			g.enableScissor(x, cy, x + W, cy + visible);

			int sy = cy;
			for (Setting s : m.settings) {
				if (s.isVisible()) {
					sy = renderSetting(g, font, s, x, sy, mx, my, accent);
				}
			}
			g.fill(x + 3, cy, x + 4, cy + visible, c(accent, 0.35f));

			g.disableScissor();
			clipTop = prevTop;
			clipBottom = prevBottom;
			cy += visible;
		}
		return cy;
	}

	private static int rowHeight(Setting s) {
		if (s instanceof NumberSetting) {
			return 26;
		}
		if (s instanceof ColorSetting cs) {
			return 17 + Math.round(62 * cs.anim);
		}
		return 17;
	}

	private int renderSetting(GuiGraphics g, Font font, Setting s, int x, int y, int mx, int my, int accent) {
		int h = rowHeight(s);
		g.fill(x, y, x + W, y + h, c(0x0B0B10, 1f));
		int lx = x + PAD + 2;
		int rx = x + W - PAD;
		int labelColor = 0xC8C8D8;

		if (s instanceof BoolSetting b) {
			b.anim += ((b.value ? 1f : 0f) - b.anim) * 0.3f;
			g.drawString(font, b.name, lx, y + 5, c(labelColor, 1f), false);
			int sw = 20, sh = 9, sx = rx - sw, sy = y + 4;
			rrect(g, sx, sy, sw, sh, 4, c(ColorUtil.lerp(0x2A2A36, accent, b.anim), 1f));
			int kx = sx + 1 + Math.round((sw - sh) * b.anim);
			rrect(g, kx, sy + 1, sh - 2, sh - 2, 3, c(0xFFFFFF, 1f));
			addRegion(x, y, W, h, (cx, cy, button) -> {
				if (button == 0) {
					b.value = !b.value;
				}
			});
		} else if (s instanceof NumberSetting n) {
			g.drawString(font, n.name, lx, y + 4, c(labelColor, 1f), false);
			String v = n.display();
			g.drawString(font, v, rx - font.width(v), y + 4, c(accent, 1f), false);

			int tx = lx, tw = rx - lx, ty = y + 17;
			rrect(g, tx, ty, tw, 3, 1, c(0x2A2A36, 1f));
			float frac = (float) ((n.value - n.min) / (n.max - n.min));
			int fw = Math.max(2, Math.round(tw * frac));
			rrect(g, tx, ty, fw, 3, 1, c(accent, 1f));
			rrect(g, tx + fw - 3, ty - 1, 5, 5, 2, c(0xFFFFFF, 1f));

			addRegion(tx - 2, y + 12, tw + 4, 12, (cx, cy, button) -> {
				if (button == 0) {
					drag = (dx, dy) -> n.set(n.min + (dx - tx) / (double) tw * (n.max - n.min));
					drag.on(cx, cy);
				}
			});
		} else if (s instanceof ModeSetting ms) {
			g.drawString(font, ms.name, lx, y + 5, c(labelColor, 1f), false);
			String v = ms.get();
			int vw = font.width(v);
			rrect(g, rx - vw - 6, y + 2, vw + 6, 13, 3, c(0x1E1E2A, 1f));
			g.drawString(font, v, rx - vw - 3, y + 5, c(accent, 1f), false);
			addRegion(x, y, W, h, (cx, cy, button) -> {
				if (button == 0) {
					ms.next();
				} else if (button == 1) {
					ms.prev();
				}
			});
		} else if (s instanceof BindSetting bs) {
			g.drawString(font, "Bind", lx, y + 5, c(labelColor, 1f), false);
			String v = bs.listening ? "..." : keyName(bs.key);
			int vw = font.width(v);
			rrect(g, rx - vw - 6, y + 2, vw + 6, 13, 3, c(bs.listening ? ColorUtil.scale(accent, 0.5f) : 0x1E1E2A, 1f));
			g.drawString(font, v, rx - vw - 3, y + 5, c(bs.listening ? 0xFFFFFF : accent, 1f), false);
			addRegion(x, y, W, h, (cx, cy, button) -> {
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
			rrect(g, lx - 2, y + 2, rx - lx + 4, 13, 3, c(0x1E1E2A, 1f));
			g.drawString(font, act.name, x + (W - font.width(act.name)) / 2, y + 5, c(accent, 1f), false);
			addRegion(x, y, W, h, (cx, cy, button) -> {
				if (button == 0) {
					act.run();
				}
			});
		} else if (s instanceof ColorSetting cs) {
			cs.anim += ((cs.open ? 1f : 0f) - cs.anim) * 0.25f;
			g.drawString(font, cs.name, lx, y + 5, c(labelColor, 1f), false);
			rrect(g, rx - 23, y + 2, 24, 13, 3, c(0x3A3A48, 1f));
			rrect(g, rx - 22, y + 3, 22, 11, 2, c(cs.rgb(), 1f));
			addRegion(x, y, W, 17, (cx, cy, button) -> cs.open = !cs.open);

			if (cs.anim > 0.02f) {
				int prevTop = clipTop, prevBottom = clipBottom;
				clipTop = Math.max(clipTop, y + 17);
				clipBottom = Math.min(clipBottom, y + h);
				g.enableScissor(x, y + 17, x + W, y + h);

				int px = lx, py = y + 19, sqW = (rx - lx) - 14, sqH = 56;
				int hx = px + sqW + 4;
				for (int i = 0; i < sqW; i++) {
					float sat = i / (float) (sqW - 1);
					g.fillGradient(px + i, py, px + i + 1, py + sqH,
						c(ColorUtil.hsb(cs.hue, sat, 1f), 1f), c(0x000000, 1f));
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
				addRegion(fpx, fpy, fsqW, fsqH, (cx, cy, button) -> {
					if (button == 0) {
						drag = (dx, dy) -> {
							cs.sat = clamp01((float) ((dx - fpx) / (fsqW - 1)));
							cs.bri = 1f - clamp01((float) ((dy - fpy) / (fsqH - 1)));
						};
						drag.on(cx, cy);
					}
				});
				addRegion(hx, fpy, 10, fsqH, (cx, cy, button) -> {
					if (button == 0) {
						drag = (dx, dy) -> cs.hue = clamp01((float) ((dy - fpy) / (fsqH - 1)));
						drag.on(cx, cy);
					}
				});
			}
		}
		return y + h;
	}

	// ------------------------------------------------------------------ helpers

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

	private static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
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
		if (moving != null) {
			moving.pos[0] = (int) event.x() - moveDx;
			moving.pos[1] = (int) event.y() - moveDy;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (drag != null || moving != null) {
			ModuleManager.save();
		}
		drag = null;
		moving = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		for (Panel p : panels) {
			int x = p.pos[0];
			int y = p.pos[1];
			if (mx >= x && mx < x + W && my >= y + HEADER_H) {
				p.scroll -= (float) (scrollY * 18.0);
				return true;
			}
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
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
		if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
