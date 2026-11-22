package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import dev.aurora.util.CombatUtil;

/**
 * Throws an ender pearl the moment you press its bind, then switches back to the slot you were on.
 * Bind it to a key (the module turns itself off after the throw). Throws where you are looking.
 */
public class ClickPearl extends Module {
	public final BoolSetting switchBack = add(new BoolSetting("Switch Back", true));
	public final BoolSetting useOffhand = add(new BoolSetting("Use Offhand Pearl", true));

	public ClickPearl() {
		super("ClickPearl", "Throws an ender pearl instantly on key press", Category.COMBAT);
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null) {
			setEnabled(false);
			return;
		}
		setEnabled(false);
		if (mc.screen != null) {
			return;
		}
		if (useOffhand.value && p.getOffhandItem().is(Items.ENDER_PEARL)) {
			mc.gameMode.useItem(p, InteractionHand.OFF_HAND);
			p.swing(InteractionHand.OFF_HAND);
			return;
		}
		int slot = CombatUtil.findHotbar(p, Items.ENDER_PEARL);
		if (slot < 0) {
			return;
		}
		int prev = p.getInventory().getSelectedSlot();
		p.getInventory().setSelectedSlot(slot);
		mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
		p.swing(InteractionHand.MAIN_HAND);
		if (switchBack.value) {
			p.getInventory().setSelectedSlot(prev);
		}
	}
}
