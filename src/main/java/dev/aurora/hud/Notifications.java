package dev.aurora.hud;

import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.module.impl.InterfaceModule;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small slide-in toasts in a corner: module toggles, totem pops and anything else that pushes one. */
public final class Notifications {
	/** Set once the client has started, so loading the config does not spam toasts. */
	public static boolean ready;

	private record Note(String title, String text, int rgb, long created) {}

	private static final List<Note> NOTES = new ArrayList<>();

	private Notifications() {}

	public static void push(String title, String text, int rgb) {
		InterfaceModule ui = ModuleManager.ui;
		if (!ready || ui == null || !ui.notifications.value) {
			return;
		}
		NOTES.add(new Note(title, text, rgb, System.currentTimeMillis()));
		while (NOTES.size() > 6) {
			NOTES.remove(0);
		}
	}

	public static void moduleToggled(Module m, boolean on) {
		InterfaceModule ui = ModuleManager.ui;
		if (!ready || ui == null || !ui.notifications.value || !ui.notifyToggles.value || !m.showInList()
			|| !m.hasToggle() || m == ui) {
			return;
		}
		push(m.name, on ? "Enabled" : "Disabled", on ? ui.accent() : 0x9A9AA4);
	}

	public static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		InterfaceModule ui = ModuleManager.ui;
		if (NOTES.isEmpty() || ui == null || mc.options.hideGui) {
			return;
		}
		Font font = mc.font;
		long now = System.currentTimeMillis();
		long life = (long) (ui.notifyTime.value * 1000);
		boolean right = ui.notifySide.is("Right");
		boolean bottom = ui.notifyPos.is("Bottom");
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		int y = bottom ? gh - 48 : 30;

		Iterator<Note> it = NOTES.iterator();
		List<Note> shown = new ArrayList<>();
		while (it.hasNext()) {
			Note n = it.next();
			if (now - n.created() > life + 300) {
				it.remove();
			} else {
				shown.add(n);
			}
		}
		for (int i = shown.size() - 1; i >= 0; i--) {
			Note n = shown.get(i);
			long age = now - n.created();
			float in = Math.min(1f, age / 250f);
			float out = age > life ? 1f - (age - life) / 300f : 1f;
			float ease = 1f - (1f - in) * (1f - in);
			int w = Math.max(font.width(n.title()), font.width(n.text())) + 22;
			int h = 26;
			int slide = (int) ((1f - ease) * (w + 12));
			int x = right ? gw - w - 8 + slide : 8 - slide;
			float a = Math.max(0f, out);
			GuiUtil.rrect(g, x, y, w, h, 4, ColorUtil.argb(0x0B0B10, 0.85f * a));
			g.fillGradient(x + 1, y + 4, x + 4, y + h - 4, ColorUtil.argb(n.rgb(), a), ColorUtil.argb(ui.accent2(), a * 0.8f));
			g.drawString(font, n.title(), x + 10, y + 4, ColorUtil.argb(0xFFFFFF, a), false);
			g.drawString(font, n.text(), x + 10, y + 14, ColorUtil.argb(n.rgb(), a), false);
			y += bottom ? -(h + 4) : (h + 4);
		}
	}
}
