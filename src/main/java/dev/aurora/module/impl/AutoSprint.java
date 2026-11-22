package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import net.minecraft.client.Minecraft;

/** Holds the sprint key for you while you walk forward. Normal forward sprint only. */
public class AutoSprint extends Module {
	public final BoolSetting stopWhenHungry = add(new BoolSetting("Stop When Hungry", true));
	public final BoolSetting stopWhenUsingItem = add(new BoolSetting("Stop While Using Item", true));

	private boolean forced;

	public AutoSprint() {
		super("AutoSprint", "Sprints automatically while walking forward", Category.UTILITY);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.screen != null) {
			release(mc);
			return;
		}
		boolean ok = mc.options.keyUp.isDown()
			&& !(stopWhenHungry.value && mc.player.getFoodData().getFoodLevel() <= 6)
			&& !(stopWhenUsingItem.value && mc.player.isUsingItem());
		if (ok) {
			mc.options.keySprint.setDown(true);
			forced = true;
		} else {
			release(mc);
		}
	}

	@Override
	protected void onDisable() {
		release(Minecraft.getInstance());
	}

	private void release(Minecraft mc) {
		if (forced && mc.options != null) {
			mc.options.keySprint.setDown(false);
		}
		forced = false;
	}
}
