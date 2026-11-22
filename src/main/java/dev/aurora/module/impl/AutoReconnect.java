package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.setting.NumberSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Reconnects to the last multiplayer server after a disconnect. If the connection fails, the next
 * disconnect screen starts a new countdown, so it keeps trying up to the attempt limit.
 * Press Escape on the disconnect screen to cancel.
 */
public class AutoReconnect extends Module {
	public final NumberSetting delay = add(new NumberSetting("Delay (seconds)", 5, 1, 60, 1));
	public final NumberSetting maxAttempts = add(new NumberSetting("Max Attempts", 10, 1, 100, 1));

	private ServerData lastServer;
	private int attempts;

	public AutoReconnect() {
		super("AutoReconnect", "Reconnects after a disconnect (Esc cancels)", Category.UTILITY);
	}

	@Override
	public void onTick(Minecraft mc) {
		ServerData current = mc.getCurrentServer();
		if (current != null && mc.level != null) {
			lastServer = current;
			attempts = 0; // connected successfully
		}
	}

	/** Call once at startup. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof DisconnectedScreen)) {
				return;
			}
			AutoReconnect module = ModuleManager.autoReconnect;
			if (module == null || !module.isEnabled() || module.lastServer == null
				|| module.attempts >= module.maxAttempts.i()) {
				return;
			}
			module.attach(client, screen);
		});
	}

	private void attach(Minecraft mc, Screen screen) {
		final ServerData server = lastServer;
		final int[] ticksLeft = {delay.i() * 20};

		ScreenEvents.afterTick(screen).register(s -> {
			if (mc.screen != screen || !isEnabled()) {
				return; // user left the screen (Esc) or turned the module off
			}
			if (--ticksLeft[0] > 0) {
				return;
			}
			ticksLeft[0] = Integer.MAX_VALUE; // fire once
			attempts++;
			ConnectScreen.startConnecting(screen, mc, ServerAddress.parseString(server.ip), server, false, null);
		});

		ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
			if (ticksLeft[0] == Integer.MAX_VALUE) {
				return;
			}
			Font font = mc.font;
			String text = "Reconnecting in " + ((ticksLeft[0] + 19) / 20) + "s  (attempt " + (attempts + 1)
				+ "/" + maxAttempts.i() + ")  -  Esc to cancel";
			graphics.drawString(font, text, (s.width - font.width(text)) / 2, s.height - 24, 0xFFFFFFFF, true);
		});
	}
}
