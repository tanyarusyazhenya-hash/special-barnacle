package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;

/**
 * Lowers a few video options that cost frames (particles, clouds, entity shadows, biome blending,
 * entity draw distance) and puts your own values back when you turn it off.
 */
public class FpsBoost extends Module {
	public final BoolSetting particles = add(new BoolSetting("Minimal Particles", true));
	public final BoolSetting shadows = add(new BoolSetting("Entity Shadows Off", true));
	public final BoolSetting blend = add(new BoolSetting("Biome Blend Off", true));
	public final NumberSetting entityDistance = add(new NumberSetting("Entity Distance %", 60, 50, 100, 10));

	private boolean saved;
	private ParticleStatus oldParticles;
	private boolean oldShadows;
	private int oldBlend;
	private double oldEntity;

	public FpsBoost() {
		super("FpsBoost", "Lowers video options that cost FPS; restores them when off", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		var o = mc.options;
		oldParticles = o.particles().get();
		oldShadows = o.entityShadows().get();
		oldBlend = o.biomeBlendRadius().get();
		oldEntity = o.entityDistanceScaling().get();
		saved = true;
	}

	@Override
	public void onTick(Minecraft mc) {
		var o = mc.options;
		if (particles.value && o.particles().get() != ParticleStatus.MINIMAL) {
			o.particles().set(ParticleStatus.MINIMAL);
		}
		if (shadows.value && o.entityShadows().get()) {
			o.entityShadows().set(false);
		}
		if (blend.value && o.biomeBlendRadius().get() != 0) {
			o.biomeBlendRadius().set(0);
		}
		double scale = entityDistance.value / 100.0;
		if (Math.abs(o.entityDistanceScaling().get() - scale) > 0.01) {
			o.entityDistanceScaling().set(scale);
		}
	}

	@Override
	protected void onDisable() {
		if (!saved) {
			return;
		}
		saved = false;
		var o = Minecraft.getInstance().options;
		o.particles().set(oldParticles);
		o.entityShadows().set(oldShadows);
		o.biomeBlendRadius().set(oldBlend);
		o.entityDistanceScaling().set(oldEntity);
	}
}
