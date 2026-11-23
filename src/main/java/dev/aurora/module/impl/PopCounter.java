package dev.aurora.module.impl;

import dev.aurora.hud.Notifications;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Counts how many Totems of Undying other players use. A pop is recognised by the effects a totem
 * gives (Regeneration II for 45 seconds together with Absorption), so it is a good guess, not a
 * packet-exact count; a rare potion or a lagging server can fool it.
 */
public class PopCounter extends Module {
	public final BoolSetting chat = add(new BoolSetting("Chat Message", true));
	public final BoolSetting toast = add(new BoolSetting("Notification", true));
	public final BoolSetting nametag = add(new BoolSetting("Show In Nametags", true));
	public final BoolSetting self = add(new BoolSetting("Count Yourself", false));
	public final BoolSetting friendsToo = add(new BoolSetting("Count Friends", false));

	private final Map<UUID, Integer> pops = new HashMap<>();
	private final Set<UUID> fresh = new HashSet<>();
	private final Set<UUID> seen = new HashSet<>();

	public PopCounter() {
		super("PopCounter", "Counts totem pops of other players", Category.COMBAT);
		setEnabled(true);
	}

	@Override
	public boolean persistEnabled() {
		return true;
	}

	@Override
	public String info() {
		int total = 0;
		for (int v : pops.values()) {
			total += v;
		}
		return Integer.toString(total);
	}

	public int popsOf(Player p) {
		return pops.getOrDefault(p.getUUID(), 0);
	}

	@Override
	protected void onDisable() {
		pops.clear();
		fresh.clear();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			pops.clear();
			fresh.clear();
			return;
		}
		seen.clear();
		for (Player p : mc.level.players()) {
			UUID id = p.getUUID();
			seen.add(id);
			if (p == mc.player && !self.value) {
				continue;
			}
			if (!friendsToo.value && ModuleManager.friends.isFriend(p)) {
				continue;
			}
			if (!p.isAlive() || p.getHealth() <= 0f) {
				Integer n = pops.remove(id);
				fresh.remove(id);
				if (n != null && n > 0) {
					say(p.getName().getString() + " died after " + n + " pop" + (n == 1 ? "" : "s"), 0xFF5555,
						"Death");
				}
				continue;
			}
			MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
			boolean totemLike = regen != null && regen.getAmplifier() == 1 && regen.getDuration() >= 860
				&& p.getEffect(MobEffects.ABSORPTION) != null;
			if (totemLike && fresh.add(id)) {
				int n = pops.merge(id, 1, Integer::sum);
				say(p.getName().getString() + " popped a totem (" + n + ")", 0xFFB020, "Totem pop");
			} else if (!totemLike && (regen == null || regen.getDuration() < 800)) {
				fresh.remove(id);
			}
		}
		pops.keySet().removeIf(id -> !seen.contains(id));
		fresh.removeIf(id -> !seen.contains(id));
	}

	private void say(String text, int rgb, String title) {
		Minecraft mc = Minecraft.getInstance();
		if (chat.value && mc.player != null) {
			mc.player.displayClientMessage(Component.literal("§d[Aurora]§r " + text), false);
		}
		if (toast.value) {
			Notifications.push(title, text, rgb);
		}
	}
}
