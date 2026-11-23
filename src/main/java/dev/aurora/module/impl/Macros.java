package dev.aurora.module.impl;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.macro.MacroManager;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.ActionSetting;
import net.minecraft.client.Minecraft;

/** Press a key to send a command or chat message. Edit them in the Macros window. */
public class Macros extends Module {
	public final ActionSetting open = add(new ActionSetting("Open Macros Window",
		() -> Minecraft.getInstance().setScreen(new dev.aurora.gui.MacrosScreen(Minecraft.getInstance().screen))));

	public Macros() {
		super("Macros", "Keys that send commands or chat messages", Category.UTILITY);
		MacroManager.ensureLoaded();
		setEnabled(true);
	}

	@Override
	public boolean persistEnabled() {
		return true;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.screen != null) {
			for (var m : MacroManager.MACROS) {
				m.wasDown = m.key > 0 && mc.player != null && InputConstants.isKeyDown(mc.getWindow(), m.key);
			}
			return;
		}
		for (var m : MacroManager.MACROS) {
			if (m.key <= 0 || m.text.isBlank()) {
				continue;
			}
			boolean down = InputConstants.isKeyDown(mc.getWindow(), m.key);
			if (down && !m.wasDown) {
				String t = m.text.trim();
				if (t.startsWith("/")) {
					mc.player.connection.sendCommand(t.substring(1));
				} else {
					mc.player.connection.sendChat(t);
				}
			}
			m.wasDown = down;
		}
	}
}
