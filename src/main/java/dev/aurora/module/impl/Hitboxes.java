package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspProvider;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws the hitboxes of entities (like F3+B, but colored and see-through). This is only a display: it
 * shows the real hitbox, it does not make hitboxes bigger on the server.
 */
public class Hitboxes extends Module implements EspProvider {
	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final BoolSetting hostile = add(new BoolSetting("Hostile Mobs", true));
	public final BoolSetting passive = add(new BoolSetting("Passive Mobs", false));
	public final BoolSetting items = add(new BoolSetting("Dropped Items", false));
	public final BoolSetting projectiles = add(new BoolSetting("Projectiles", true));
	public final ColorSetting playerColor = add(new ColorSetting("Player Color", 0xFF7A1A));
	public final ColorSetting hostileColor = add(new ColorSetting("Hostile Color", 0xFF4D4D));
	public final ColorSetting passiveColor = add(new ColorSetting("Passive Color", 0x4ADE80));
	public final ColorSetting otherColor = add(new ColorSetting("Other Color", 0xFFFFFF));
	public final ModeSetting style = add(new ModeSetting("Style", "Outline", "Outline", "Filled", "Both", "Corners"));
	public final NumberSetting outline = add(new NumberSetting("Outline Opacity", 0.9, 0.05, 1.0, 0.05));
	public final NumberSetting fill = add(new NumberSetting("Fill Opacity", 0.2, 0.0, 1.0, 0.05));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", 1.5, 0.5, 4.0, 0.25));
	public final NumberSetting expand = add(new NumberSetting("Expand", 0.0, 0.0, 0.5, 0.05));
	public final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256, 8));
	public final BoolSetting throughWalls = add(new BoolSetting("Through Walls", true));

	public Hitboxes() {
		super("Hitboxes", "Shows entity hitboxes", Category.RENDER);
		fill.visibleWhen(() -> style.is("Filled") || style.is("Both"));
	}

	@Override
	public void collect(List<EspBox> out) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double r2 = range.value * range.value;
		int mode = switch (style.get()) {
			case "Filled" -> 1;
			case "Both" -> 2;
			case "Corners" -> 3;
			default -> 0;
		};
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || e.isSpectator() || mc.player.distanceToSqr(e) > r2) {
				continue;
			}
			int rgb;
			if (e instanceof Player p) {
				if (!players.value || (e.isInvisible() && false)) {
					continue;
				}
				rgb = ModuleManager.friends.isFriend(p) ? ModuleManager.friends.color.rgb() : playerColor.rgb();
			} else if (e instanceof Enemy) {
				if (!hostile.value) {
					continue;
				}
				rgb = hostileColor.rgb();
			} else if (e instanceof Mob || e instanceof LivingEntity) {
				if (!passive.value) {
					continue;
				}
				rgb = passiveColor.rgb();
			} else if (e instanceof ItemEntity) {
				if (!items.value) {
					continue;
				}
				rgb = otherColor.rgb();
			} else {
				if (!projectiles.value || !(e instanceof net.minecraft.world.entity.projectile.Projectile)) {
					continue;
				}
				rgb = otherColor.rgb();
			}
			Vec3 off = new Vec3(
				Mth.lerp(partial, e.xo, e.getX()) - e.getX(),
				Mth.lerp(partial, e.yo, e.getY()) - e.getY(),
				Mth.lerp(partial, e.zo, e.getZ()) - e.getZ());
			AABB box = e.getBoundingBox().move(off).inflate(expand.value);
			out.add(new EspBox(box, rgb, outline.f(), fill.f(), thickness.f(), mode, throughWalls.value));
		}
	}
}
