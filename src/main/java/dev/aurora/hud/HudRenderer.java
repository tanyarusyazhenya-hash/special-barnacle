package dev.aurora.hud;

import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.HudModule;
import dev.aurora.module.impl.Waypoints;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Draws the watermark, module list, info stack, armor row and the left-side panels. */
public final class HudRenderer {
	private static final int ROW_H = 12;
	private static final java.util.Map<String, Float> SLIDE = new java.util.HashMap<>();
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
			String name = "aurora";
			String version = " §71.21.11";
			int bw = font.width(name) + font.width(version) + 30;
			if (h.background.value) {
				GuiUtil.rrect(g, 3, 3, bw + 2, 16, 8, ColorUtil.argb(colorFor(h, accent, 0), 0.22f));
				GuiUtil.rrect(g, 4, 4, bw, 14, 7, ColorUtil.argb(BG, Math.max(0.55f, h.bgOpacity.f())));
			}
			if (h.accentBar.value) {
				// logo badge: accent circle with a bold dark "A"
				GuiUtil.rrect(g, 7, 6, 12, 12, 6, ColorUtil.argb(colorFor(h, accent, 0), 1f));
				g.drawString(font, Component.literal("A").withStyle(ChatFormatting.BOLD), 11, 8, 0xFF0C0C0E, false);
			}
			text(g, font, h, name + version, h.accentBar.value ? 24 : 10, 8, colorFor(h, accent, 0));
			topLeftY = 24;
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
			java.util.Set<String> keys = new java.util.HashSet<>();
			for (String label : labels) {
				keys.add(label.split(" §7")[0]);
			}
			SLIDE.keySet().removeIf(k -> !keys.contains(k));
			for (String label : labels) {
				String key = label.split(" §7")[0];
				float a = SLIDE.getOrDefault(key, 0f);
				a += (1f - a) * 0.18f;
				SLIDE.put(key, a);
				int off = Math.round((1f - a) * (font.width(label) + 14)) * (right ? 1 : -1);
				drawRow(g, font, h, label, right, gw, y, colorFor(h, accent, i++), off);
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
		if (h.session.value) {
			lines.add("Session §7" + clock((System.currentTimeMillis() - h.sessionStart) / 1000L));
		}
		if (h.totems.value) {
			lines.add("Totems §7" + countItem(mc, Items.TOTEM_OF_UNDYING));
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

		// Armor row (top-center)
		if (h.armor.value) {
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			int x0 = gw / 2 - 36;
			for (int i = 0; i < slots.length; i++) {
				ItemStack stack = mc.player.getItemBySlot(slots[i]);
				if (stack.isEmpty()) {
					continue;
				}
				int ix = x0 + i * 18;
				g.renderItem(stack, ix, 4);
				g.renderItemDecorations(font, stack, ix, 4);
				if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
					int pct = 100 - stack.getDamageValue() * 100 / stack.getMaxDamage();
					String s = pct + "%";
					int color = ColorUtil.hsb(Mth.clamp(pct / 100f, 0f, 1f) * 0.33f, 0.8f, 1f);
					g.drawString(font, s, ix + 8 - font.width(s) / 2, 22, 0xFF000000 | color, h.shadow.value);
				}
			}
		}

		// Left-middle panels: potion effects, nearby players, waypoints
		List<String> left = new ArrayList<>();
		if (h.effects.value) {
			for (MobEffectInstance e : mc.player.getActiveEffects()) {
				String name = e.getEffect().value().getDisplayName().getString();
				int amp = e.getAmplifier() + 1;
				String time = e.isInfiniteDuration() ? "inf" : clock(e.getDuration() / 20L);
				left.add(name + (amp > 1 ? " " + amp : "") + " §7" + time);
			}
		}
		if (h.nearby.value) {
			List<AbstractClientPlayer> others = new ArrayList<>();
			for (AbstractClientPlayer p : mc.level.players()) {
				if (p != mc.player && !p.isSpectator()) {
					others.add(p);
				}
			}
			others.sort(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)));
			for (int i = 0; i < Math.min(6, others.size()); i++) {
				AbstractClientPlayer p = others.get(i);
				String prefix = ModuleManager.friends.isFriend(p) ? "§b" : "";
				left.add(prefix + p.getName().getString() + " §7" + (int) mc.player.distanceTo(p) + "m");
			}
		}
		if (h.waypointList.value && ModuleManager.waypoints.isEnabled()) {
			double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
			for (Waypoints.Waypoint w : ModuleManager.waypoints.nearest(mc, h.waypointCount.i())) {
				int dist = (int) Math.sqrt(Waypoints.distSq(w, px, py, pz));
				left.add(w.type() + " §7" + dist + "m  " + w.x() + " " + w.y() + " " + w.z());
			}
		}
		int ly = Math.max(topLeftY + 4, gh / 2 - left.size() * (ROW_H + 1) / 2);
		int li = 0;
		for (String line : left) {
			drawRow(g, font, h, line, false, gw, ly, colorFor(h, accent, li++));
			ly += ROW_H + 1;
		}
	}

	private static int countItem(Minecraft mc, net.minecraft.world.item.Item item) {
		Inventory inv = mc.player.getInventory();
		int count = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				count += s.getCount();
			}
		}
		return count;
	}

	private static String clock(long totalSeconds) {
		long h = totalSeconds / 3600, m = (totalSeconds / 60) % 60, s = totalSeconds % 60;
		return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
	}

	private static void drawRow(GuiGraphics g, Font font, HudModule h, String label, boolean right, int gw, int y, int rgb) {
		drawRow(g, font, h, label, right, gw, y, rgb, 0);
	}

	private static void drawRow(GuiGraphics g, Font font, HudModule h, String label, boolean right, int gw, int y, int rgb,
								int dx) {
		int bw = font.width(label) + 10;
		int x = (right ? gw - 4 - bw : 4) + dx;
		if (h.background.value) {
			GuiUtil.rrect(g, x - 1, y - 1, bw + 2, ROW_H + 1, 5, ColorUtil.argb(rgb, 0.16f));
			GuiUtil.rrect(g, x, y, bw, ROW_H - 1, 4, ColorUtil.argb(BG, h.bgOpacity.f()));
		}
		if (h.accentBar.value) {
			int bx = right ? x + bw - 3 : x + 1;
			GuiUtil.rrect(g, bx, y + 2, 2, ROW_H - 5, 1, ColorUtil.argb(rgb, 1f));
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
