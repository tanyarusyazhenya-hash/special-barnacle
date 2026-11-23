package dev.aurora.module.impl;

import dev.aurora.hud.Notifications;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashSet;
import java.util.Set;

/**
 * Tells you when a friend or a group member joins or leaves the server (read from the tab list, so it
 * works at any distance). Nothing is announced for players who were already online when you joined.
 */
public class FriendAlert extends Module {
	public final BoolSetting joins = add(new BoolSetting("Joined", true));
	public final BoolSetting leaves = add(new BoolSetting("Left", true));
	public final BoolSetting toast = add(new BoolSetting("Notification", true));
	public final BoolSetting chat = add(new BoolSetting("Chat Message", false));
	public final BoolSetting sound = add(new BoolSetting("Sound", true));

	private final Set<String> online = new HashSet<>();
	private int settle;

	public FriendAlert() {
		super("FriendAlert", "Alerts when a friend or group member joins or leaves", Category.UTILITY);
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void onEnable() {
		online.clear();
		settle = 40;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null) {
			online.clear();
			settle = 40;
			return;
		}
		Set<String> now = new HashSet<>();
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			now.add(info.getProfile().name());
		}
		if (settle > 0) {
			settle--;
			online.clear();
			online.addAll(now);
			return;
		}
		for (String n : now) {
			if (!online.contains(n) && joins.value && ModuleManager.friends.isFriendName(n)) {
				alert(mc, n, "joined the server", 0x4ADE80);
			}
		}
		for (String n : online) {
			if (!now.contains(n) && leaves.value && ModuleManager.friends.isFriendName(n)) {
				alert(mc, n, "left the server", 0xF87171);
			}
		}
		online.clear();
		online.addAll(now);
	}

	private void alert(Minecraft mc, String name, String text, int rgb) {
		if (toast.value) {
			Notifications.push(name, text, rgb);
		}
		if (chat.value) {
			mc.player.displayClientMessage(Component.literal("§d[Aurora]§r §b" + name + "§r " + text), false);
		}
		if (sound.value) {
			mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
		}
	}
}
