package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Cosmetic particle effects around you (and optionally other players). They are spawned on your own
 * screen only, nobody else sees them. A real cape would need changes to the game's player renderer,
 * which this client does not do.
 */
public class Particles extends Module {
	public final ModeSetting shape = add(new ModeSetting("Shape", "Halo", "Halo", "Spiral", "Orbit", "Wings", "Trail",
		"Cloud"));
	public final ModeSetting particle = add(new ModeSetting("Particle", "End Rod", "End Rod", "Flame", "Soul Fire",
		"Heart", "Snow", "Enchant", "Note", "Happy"));
	public final NumberSetting amount = add(new NumberSetting("Amount", 2, 1, 8, 1));
	public final NumberSetting radius = add(new NumberSetting("Radius", 0.6, 0.2, 2.0, 0.1));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.2, 4.0, 0.1));
	public final BoolSetting firstPerson = add(new BoolSetting("Show In First Person", false));
	public final BoolSetting others = add(new BoolSetting("Other Players Too", false));

	private double angle;

	public Particles() {
		super("Particles", "Particle effects around you (visible only to you)", Category.RENDER);
	}

	@Override
	public String info() {
		return shape.get();
	}

	private ParticleOptions type() {
		return switch (particle.get()) {
			case "Flame" -> ParticleTypes.FLAME;
			case "Soul Fire" -> ParticleTypes.SOUL_FIRE_FLAME;
			case "Heart" -> ParticleTypes.HEART;
			case "Snow" -> ParticleTypes.SNOWFLAKE;
			case "Enchant" -> ParticleTypes.ENCHANT;
			case "Note" -> ParticleTypes.NOTE;
			case "Happy" -> ParticleTypes.HAPPY_VILLAGER;
			default -> ParticleTypes.END_ROD;
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		angle += 0.18 * speed.value;
		boolean fp = mc.options.getCameraType().isFirstPerson();
		if (!fp || firstPerson.value) {
			spawn(mc, mc.player);
		}
		if (others.value) {
			for (Player p : mc.level.players()) {
				if (p != mc.player && mc.player.distanceToSqr(p) < 48 * 48) {
					spawn(mc, p);
				}
			}
		}
	}

	private void spawn(Minecraft mc, Player p) {
		ParticleOptions type = type();
		double r = radius.value;
		Vec3 pos = p.position();
		int n = amount.i();
		for (int i = 0; i < n; i++) {
			double a = angle + i * (Math.PI * 2 / n);
			double x = pos.x;
			double y = pos.y;
			double z = pos.z;
			switch (shape.get()) {
				case "Spiral" -> {
					double h = ((angle * 0.3 + i * 0.7) % 2.0);
					x += Math.cos(a * 2) * r;
					z += Math.sin(a * 2) * r;
					y += h;
				}
				case "Orbit" -> {
					x += Math.cos(a) * r * 1.4;
					z += Math.sin(a) * r * 1.4;
					y += 0.9 + Math.sin(a * 2) * 0.3;
				}
				case "Wings" -> {
					double yaw = Math.toRadians(p.yBodyRot);
					double side = i % 2 == 0 ? 1 : -1;
					double flap = Math.sin(angle * 2) * 0.25;
					double w = (0.3 + Math.random() * r * 1.5);
					double back = 0.25 + Math.random() * 0.2;
					x += Math.cos(yaw) * side * w + Math.sin(yaw) * back;
					z += Math.sin(yaw) * side * w - Math.cos(yaw) * back;
					y += 1.0 + Math.random() * 0.7 + flap * w;
				}
				case "Trail" -> {
					x += (Math.random() - 0.5) * r * 0.6;
					z += (Math.random() - 0.5) * r * 0.6;
					y += 0.1 + Math.random() * 0.2;
				}
				case "Cloud" -> {
					x += (Math.random() - 0.5) * r * 3;
					z += (Math.random() - 0.5) * r * 3;
					y += p.getBbHeight() + 0.5 + Math.random() * 0.3;
				}
				default -> { // Halo
					x += Math.cos(a) * r;
					z += Math.sin(a) * r;
					y += p.getBbHeight() + 0.25;
				}
			}
			mc.level.addParticle(type, x, y, z, 0, 0, 0);
		}
	}
}
