package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Keeps a Totem of Undying in your offhand. When the offhand is empty (for example right after a
 * pop) it swaps one in from the inventory using the normal inventory click, after a short delay.
 * Automation like this is blocked by many servers, so it is off by default.
 */
public class AutoTotem extends Module {
	public final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 1, 0, 10, 1));
	public final NumberSetting health = add(new NumberSetting("Swap At Health", 20, 1, 20, 1));
	public final BoolSetting hotbarOnly = add(new BoolSetting("Hotbar Only", false));

	private int wait;

	public AutoTotem() {
		super("AutoTotem", "Keeps a totem in your offhand (off by default)", Category.UTILITY);
	}

	@Override
	public String info() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : Integer.toString(countTotems(mc.player.getInventory()));
	}

	@Override
	protected void onEnable() {
		wait = 0;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null) {
			return;
		}
		if (p.containerMenu != p.inventoryMenu) {
			return; // a chest, furnace, etc. is open
		}
		if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			wait = delay.i();
			return;
		}
		if (p.getHealth() + p.getAbsorptionAmount() > health.value) {
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		int slot = findTotemSlot(p.getInventory());
		if (slot < 0) {
			return;
		}
		// Button 40 swaps the clicked slot with the offhand.
		mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, slot, 40, ClickType.SWAP, p);
		wait = delay.i();
	}

	/** Slot index in the player inventory menu (hotbar 36-44, main inventory 9-35), or -1. */
	private int findTotemSlot(Inventory inv) {
		for (int i = 0; i < 9; i++) {
			if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
				return 36 + i;
			}
		}
		if (!hotbarOnly.value) {
			for (int i = 9; i < 36; i++) {
				if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
					return i;
				}
			}
		}
		return -1;
	}

	private static int countTotems(Inventory inv) {
		int count = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.TOTEM_OF_UNDYING)) {
				count += s.getCount();
			}
		}
		return count;
	}
}
