package dev.aurora.gui;

import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.Friends;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Separate window opened from the ClickGUI: create groups, pick one, add online players with a click
 * (or type a name), remove members. Group members are treated as friends everywhere.
 */
public class GroupsScreen extends Screen {
	private static final int W = 380;
	private static final int H = 230;
	private static final int TB = 26;

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private String selected;
	private final StringBuilder groupName = new StringBuilder();
	private final StringBuilder playerName = new StringBuilder();
	private int focus; // 0 = group name, 1 = player name
	private int scrollGroups;
	private int scrollPlayers;

	public GroupsScreen(Screen parent) {
		super(Component.literal("Groups"));
		this.parent = parent;
		List<String> names = ModuleManager.friends.groupNames();
		if (!names.isEmpty()) {
			selected = names.get(0);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
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
		Friends friends = ModuleManager.friends;
		int accent = ModuleManager.ui.accent();
		g.fill(0, 0, width, height, c(0x000000, 0.5f));

		int x0 = (width - W) / 2;
		int y0 = (height - H) / 2;
		for (int i = 3; i >= 1; i--) {
			GuiUtil.rrect(g, x0 - i * 2, y0 - i * 2, W + i * 4, H + i * 4, 8 + i * 2, c(accent, 0.035f * (4 - i)));
		}
		GuiUtil.rrect(g, x0 - 1, y0 - 1, W + 2, H + 2, 8, c(accent, 0.55f));
		GuiUtil.rrect(g, x0, y0, W, H, 7, c(0x0C0C0E, 0.97f));
		GuiUtil.rrect(g, x0, y0, W, TB, 7, c(0x111114, 1f));
		g.fill(x0, y0 + TB - 7, x0 + W, y0 + TB, c(0x111114, 1f));
		g.drawString(font, "Groups", x0 + 12, y0 + 9, c(0xFFFFFF, 1f), false);
		g.drawString(font, "members count as friends", x0 + 12 + font.width("Groups") + 8, y0 + 9,
			c(0x7A7A84, 1f), false);

		boolean hoverBack = mx >= x0 + W - 58 && mx < x0 + W - 6 && my >= y0 + 5 && my < y0 + 21;
		GuiUtil.rrect(g, x0 + W - 58, y0 + 5, 52, 16, 4, c(accent, hoverBack ? 0.35f : 0.18f));
		g.drawString(font, "Back", x0 + W - 32 - font.width("Back") / 2, y0 + 9, c(0xFFFFFF, 1f), false);
		hits.add(new Hit(x0 + W - 58, y0 + 5, 52, 16, this::onClose));

		// ---- left: groups
		int lx = x0 + 10;
		int ly = y0 + TB + 8;
		int lw = 140;
		g.drawString(font, "GROUPS", lx, ly, c(accent, 1f), false);
		ly += 12;
		inputBox(g, font, lx, ly, lw - 34, groupName, "new group...", focus == 0, accent);
		hits.add(new Hit(lx, ly, lw - 34, 18, () -> focus = 0));
		button(g, font, lx + lw - 30, ly, 30, 18, "+", mx, my, accent, this::createGroup);
		ly += 24;

		List<String> names = friends.groupNames();
		int rows = (y0 + H - 10 - ly) / 22;
		scrollGroups = Math.max(0, Math.min(scrollGroups, Math.max(0, names.size() - rows)));
		for (int i = scrollGroups; i < Math.min(names.size(), scrollGroups + rows); i++) {
			String n = names.get(i);
			boolean sel = n.equals(selected);
			boolean hov = mx >= lx && mx < lx + lw && my >= ly && my < ly + 20;
			GuiUtil.rrect(g, lx, ly, lw, 20, 4, c(sel ? accent : 0xFFFFFF, sel ? 0.30f : hov ? 0.10f : 0.05f));
			g.drawString(font, font.plainSubstrByWidth(n, lw - 40), lx + 7, ly + 6, c(0xFFFFFF, 1f), false);
			g.drawString(font, Integer.toString(friends.groupMembers(n).size()), lx + lw - 30, ly + 6,
				c(0x9A9AA4, 1f), false);
			hits.add(new Hit(lx, ly, lw - 18, 20, () -> selected = n));
			boolean hovX = mx >= lx + lw - 16 && mx < lx + lw - 2 && my >= ly + 3 && my < ly + 17;
			g.drawString(font, "x", lx + lw - 12, ly + 6, hovX ? 0xFFFF5555 : c(0x9A9AA4, 1f), false);
			hits.add(new Hit(lx + lw - 16, ly + 3, 14, 14, () -> {
				friends.groupDelete(n);
				if (n.equals(selected)) {
					selected = null;
				}
			}));
			ly += 22;
		}
		if (names.isEmpty()) {
			g.drawString(font, "No groups yet", lx + 4, ly + 4, c(0x7A7A84, 1f), false);
		}

		// ---- right: members and online players
		int rx = x0 + 160;
		int rw = W - 170;
		int ry = y0 + TB + 8;
		g.fill(rx - 6, y0 + TB + 6, rx - 5, y0 + H - 8, c(0xFFFFFF, 0.07f));
		if (selected == null || !names.contains(selected)) {
			selected = names.isEmpty() ? null : names.get(0);
		}
		if (selected == null) {
			g.drawString(font, "Create a group on the left", rx + 4, ry + 4, c(0x9A9AA4, 1f), false);
			super.render(g, mx, my, delta);
			return;
		}
		g.drawString(font, "MEMBERS OF " + selected.toUpperCase(), rx, ry, c(accent, 1f), false);
		ry += 12;
		List<String> members = friends.groupMembers(selected);
		int shown = 0;
		int mxPos = rx;
		for (String m : members) {
			if (shown >= 6) {
				break;
			}
			boolean hovX = mx >= rx + rw - 16 && mx < rx + rw - 2 && my >= ry + 2 && my < ry + 16;
			GuiUtil.rrect(g, rx, ry, rw, 18, 4, c(0xFFFFFF, 0.06f));
			g.drawString(font, m, rx + 7, ry + 5, c(0xFFFFFF, 1f), false);
			g.drawString(font, "x", rx + rw - 12, ry + 5, hovX ? 0xFFFF5555 : c(0x9A9AA4, 1f), false);
			String sel = selected;
			hits.add(new Hit(rx + rw - 16, ry + 2, 14, 14, () -> friends.groupRemove(sel, m)));
			ry += 20;
			shown++;
		}
		if (members.isEmpty()) {
			g.drawString(font, "Empty. Add players below", rx + 4, ry + 4, c(0x7A7A84, 1f), false);
			ry += 20;
		} else if (members.size() > 6) {
			g.drawString(font, "+" + (members.size() - 6) + " more", rx + 4, ry + 2, c(0x7A7A84, 1f), false);
			ry += 12;
		}

		ry = Math.max(ry + 4, y0 + TB + 8 + 12 + 6 * 20 + 4);
		inputBox(g, font, rx, ry, rw - 54, playerName, "player name...", focus == 1, accent);
		hits.add(new Hit(rx, ry, rw - 54, 18, () -> focus = 1));
		button(g, font, rx + rw - 50, ry, 50, 18, "Invite", mx, my, accent, this::addTyped);
		ry += 24;

		g.drawString(font, "ONLINE (click to invite)", rx, ry, c(accent, 1f), false);
		ry += 12;
		List<String> online = new ArrayList<>();
		if (minecraft.level != null) {
			for (AbstractClientPlayer p : minecraft.level.players()) {
				if (p != minecraft.player) {
					online.add(p.getName().getString());
				}
			}
		}
		int cols = 2;
		int cw = (rw - 4) / cols;
		int idx = 0;
		for (String name : online) {
			int cx = rx + (idx % cols) * (cw + 4);
			int cy = ry + (idx / cols) * 20;
			if (cy + 18 > y0 + H - 8) {
				break;
			}
			boolean in = members.contains(name.toLowerCase());
			boolean hov = mx >= cx && mx < cx + cw && my >= cy && my < cy + 18;
			GuiUtil.rrect(g, cx, cy, cw, 18, 4, c(in ? accent : 0xFFFFFF, in ? 0.25f : hov ? 0.12f : 0.05f));
			g.drawString(font, font.plainSubstrByWidth(name, cw - 10), cx + 6, cy + 5, c(0xFFFFFF, 1f), false);
			String sel = selected;
			hits.add(new Hit(cx, cy, cw, 18, () -> {
				if (in) {
					friends.groupRemove(sel, name);
				} else {
					friends.groupAdd(sel, name);
				}
			}));
			idx++;
		}
		if (online.isEmpty()) {
			g.drawString(font, "No other players around", rx + 4, ry + 4, c(0x7A7A84, 1f), false);
		}
		super.render(g, mx, my, delta);
	}

	private void inputBox(GuiGraphics g, Font font, int x, int y, int w, StringBuilder text, String hint,
						  boolean focused, int accent) {
		GuiUtil.rrect(g, x, y, w, 18, 4, c(focused ? accent : 0xFFFFFF, focused ? 0.55f : 0.12f));
		GuiUtil.rrect(g, x + 1, y + 1, w - 2, 16, 3, c(0x101013, 1f));
		boolean caret = focused && (System.currentTimeMillis() / 500) % 2 == 0;
		if (text.length() == 0 && !focused) {
			g.drawString(font, hint, x + 6, y + 5, c(0x6A6A74, 1f), false);
		} else {
			String shown = font.plainSubstrByWidth(text.toString(), w - 16);
			g.drawString(font, shown + (caret ? "_" : ""), x + 6, y + 5, c(0xFFFFFF, 1f), false);
		}
	}

	private void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int mx, int my,
						int accent, Runnable action) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		GuiUtil.rrect(g, x, y, w, h, 4, c(accent, hov ? 0.55f : 0.35f));
		g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + 5, c(0xFFFFFF, 1f), false);
		hits.add(new Hit(x, y, w, h, action));
	}

	private void createGroup() {
		String n = groupName.toString().trim().replace(' ', '_');
		if (n.isEmpty()) {
			return;
		}
		ModuleManager.friends.groupCreate(n);
		selected = n.toLowerCase();
		groupName.setLength(0);
	}

	private void addTyped() {
		String n = playerName.toString().trim();
		if (n.isEmpty() || selected == null) {
			return;
		}
		ModuleManager.friends.groupAdd(selected, n);
		playerName.setLength(0);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (h.contains(event.x(), event.y())) {
					h.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		StringBuilder sb = focus == 0 ? groupName : playerName;
		int cp = event.codepoint();
		if (sb.length() < 24 && cp >= 32 && cp != 127) {
			sb.appendCodePoint(cp);
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		StringBuilder sb = focus == 0 ? groupName : playerName;
		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			if (sb.length() > 0) {
				sb.setLength(sb.length() - 1);
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			if (focus == 0) {
				createGroup();
			} else {
				addTyped();
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_TAB) {
			focus = 1 - focus;
			return true;
		}
		return super.keyPressed(event);
	}
}
