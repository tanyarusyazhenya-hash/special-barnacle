package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspProvider;
import dev.aurora.render.EspStyle;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import dev.aurora.util.ColorUtil;

import java.util.List;

/** Highlights other players. Boxes are interpolated every frame, so they move smoothly. */
public class PlayerESP extends Module implements EspProvider {
	public final ModeSetting colorSource = add(new ModeSetting("Color Source", "Static", "Static", "Health"));
	public final ColorSetting color = add(new ColorSetting("Color", 0x4ADE80));
	public final BoolSetting invisible = add(new BoolSetting("Show Invisible", true));
	public final BoolSetting spectators = add(new BoolSetting("Show Spectators", false));
	public final EspStyle style;

	public PlayerESP() {
		super("PlayerESP", "Highlights other players", Category.RENDER);
		style = new EspStyle(this);
		style.range.set(256);
		color.visibleWhen(() -> colorSource.is("Static"));
	}

	@Override
	public String info() {
		return style.mode.get();
	}

	@Override
	public void collect(List<EspBox> out) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double r2 = style.range.value * style.range.value;

		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player) {
				continue;
			}
			if (!invisible.value && p.isInvisible()) {
				continue;
			}
			if (!spectators.value && p.isSpectator()) {
				continue;
			}
			double distSq = mc.player.distanceToSqr(p);
			if (distSq > r2) {
				continue;
			}
			Friends friends = ModuleManager.friends;
			boolean friend = friends.isFriend(p);
			if (friend && friends.hideFriends()) {
				continue;
			}

			double dx = Mth.lerp(partial, p.xo, p.getX()) - p.getX();
			double dy = Mth.lerp(partial, p.yo, p.getY()) - p.getY();
			double dz = Mth.lerp(partial, p.zo, p.getZ()) - p.getZ();
			AABB box = p.getBoundingBox().move(dx, dy, dz).inflate(0.02);

			int rgb;
			if (friend) {
				rgb = friends.color.rgb();
			} else if (colorSource.is("Health")) {
				float frac = Mth.clamp(p.getHealth() / Math.max(1f, p.getMaxHealth()), 0f, 1f);
				rgb = ColorUtil.hsb(frac * 0.33f, 0.8f, 1f); // red -> green
			} else {
				rgb = color.rgb();
			}
			out.add(style.box(box, rgb, distSq));
		}
	}
}
