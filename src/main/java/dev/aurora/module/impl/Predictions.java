package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspLine;
import dev.aurora.render.EspProvider;
import dev.aurora.render.LineProvider;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Predicts where projectiles land: the one you are about to throw or shoot (pearls, snowballs, eggs,
 * potions, experience bottles, tridents, bow and crossbow arrows) and projectiles already flying.
 * Blocks are traced, entities are not, and wind or other mods are not counted.
 */
public class Predictions extends Module implements LineProvider, EspProvider {
	private static final Set<String> FLYING = Set.of("ender_pearl", "arrow", "spectral_arrow", "trident",
		"snowball", "egg", "splash_potion", "lingering_potion", "experience_bottle");

	public final BoolSetting held = add(new BoolSetting("Held Item", true));
	public final BoolSetting flying = add(new BoolSetting("Flying Projectiles", true));
	public final BoolSetting marker = add(new BoolSetting("Landing Marker", true));
	public final ColorSetting heldColor = add(new ColorSetting("Held Color", 0x4ADE80));
	public final ColorSetting flyingColor = add(new ColorSetting("Flying Color", 0xFF7A1A));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.85, 0.1, 1.0, 0.05));
	public final NumberSetting width = add(new NumberSetting("Width", 1.5, 0.5, 4.0, 0.25));
	public final NumberSetting steps = add(new NumberSetting("Max Steps", 120, 20, 300, 10));

	private final List<EspBox> boxes = new ArrayList<>();

	public Predictions() {
		super("Predictions", "Shows where pearls, arrows and potions will land", Category.RENDER);
	}

	@Override
	public void collectLines(List<EspLine> out, Vec3 start) {
		boxes.clear();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (mc.level == null || p == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (held.value) {
			ItemStack main = p.getMainHandItem();
			ItemStack off = p.getOffhandItem();
			double[] ph = physicsFor(p, main);
			if (ph == null) {
				ph = physicsFor(p, off);
			}
			if (ph != null) {
				Vec3 eye = p.getEyePosition(partial).add(0, -0.1, 0);
				Vec3 vel = p.getViewVector(partial).scale(ph[0]);
				trace(mc, out, eye, vel, ph[1], heldColor.rgb());
			}
		}
		if (flying.value) {
			for (Entity e : mc.level.entitiesForRendering()) {
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
				if (!FLYING.contains(id)) {
					continue;
				}
				Vec3 vel = e.getDeltaMovement();
				if (vel.lengthSqr() < 0.01) {
					continue;
				}
				trace(mc, out, e.position(), vel, gravityFor(id), flyingColor.rgb());
			}
		}
	}

	@Override
	public void collect(List<EspBox> out) {
		out.addAll(boxes);
		boxes.clear();
	}

	/** {speed, gravity} for an item that is thrown or shot, or null. */
	private static double[] physicsFor(LocalPlayer p, ItemStack s) {
		if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG)) {
			return new double[]{1.5, 0.03};
		}
		if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) {
			return new double[]{0.5, 0.05};
		}
		if (s.is(Items.EXPERIENCE_BOTTLE)) {
			return new double[]{0.7, 0.07};
		}
		if (s.is(Items.TRIDENT) && p.isUsingItem() && p.getUseItem() == s) {
			return new double[]{2.5, 0.05};
		}
		if (s.is(Items.BOW) && p.isUsingItem() && p.getUseItem() == s) {
			float f = p.getTicksUsingItem() / 20.0f;
			f = (f * f + f * 2.0f) / 3.0f;
			f = Math.min(f, 1.0f);
			return f < 0.1f ? null : new double[]{f * 3.0, 0.05};
		}
		if (s.is(Items.CROSSBOW) && CrossbowItem.isCharged(s)) {
			return new double[]{3.15, 0.05};
		}
		return null;
	}

	private static double gravityFor(String id) {
		if (id.contains("arrow") || id.equals("trident") || id.contains("potion")) {
			return 0.05;
		}
		return id.equals("experience_bottle") ? 0.07 : 0.03;
	}

	private void trace(Minecraft mc, List<EspLine> out, Vec3 start, Vec3 velocity, double gravity, int rgb) {
		Vec3 pos = start;
		Vec3 vel = velocity;
		for (int i = 0; i < (int) steps.value; i++) {
			Vec3 next = pos.add(vel);
			var hit = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, mc.player));
			boolean stop = hit.getType() != HitResult.Type.MISS;
			Vec3 end = stop ? hit.getLocation() : next;
			out.add(new EspLine(pos, end, rgb, opacity.f(), width.f(), true));
			if (stop) {
				if (marker.value) {
					boxes.add(new EspBox(new AABB(end.x - 0.25, end.y - 0.05, end.z - 0.25,
						end.x + 0.25, end.y + 0.45, end.z + 0.25), rgb, opacity.f(), 0.35f, width.f(), 2, true));
				}
				return;
			}
			boolean water = mc.level.getFluidState(net.minecraft.core.BlockPos.containing(next)).isSource()
				|| !mc.level.getFluidState(net.minecraft.core.BlockPos.containing(next)).isEmpty();
			vel = vel.scale(water ? 0.8 : 0.99).add(0, -gravity, 0);
			pos = next;
			if (pos.y < mc.level.getMinY() - 10) {
				return;
			}
		}
	}
}
