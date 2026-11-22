package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Client-side night vision. The server is not told about the effect. */
public class Fullbright extends Module {
	public Fullbright() {
		super("Fullbright", "See in the dark (client-side night vision)", Category.RENDER);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		MobEffectInstance current = mc.player.getEffect(MobEffects.NIGHT_VISION);
		if (current == null || current.getDuration() < 400) {
			mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100000, 0, false, false));
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}
}
