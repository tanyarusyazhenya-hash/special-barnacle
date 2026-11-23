package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.render.EspLine;
import dev.aurora.render.LineProvider;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.VisualColor;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** A glowing cone hat above heads, drawn with lines. Visible only to you. */
public class ChinaHat extends Module implements LineProvider {
	public final NumberSetting radius = add(new NumberSetting("Radius", 0.5, 0.2, 1.0, 0.05));
	public final NumberSetting hatHeight = add(new NumberSetting("Height", 0.3, 0.1, 0.8, 0.05));
	public final NumberSetting spokes = add(new NumberSetting("Spokes", 16, 4, 32, 2));
	public final NumberSetting width = add(new NumberSetting("Width", 1.5, 0.5, 4.0, 0.25));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.9, 0.1, 1.0, 0.05));
	public final ModeSetting colorMode = add(VisualColor.mode());
	public final ColorSetting staticColor = add(new ColorSetting("Static Color", 0xFFFFFF));
	public final BoolSetting self = add(new BoolSetting("Yourself", true));
	public final BoolSetting others = add(new BoolSetting("Other Players", false));

	public ChinaHat() {
		super("ChinaHat", "Cone hat above heads", Category.RENDER);
		staticColor.visibleWhen(() -> colorMode.is("Static"));
	}

	@Override
	public void collectLines(List<EspLine> out, Vec3 start) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		for (Player p : mc.level.players()) {
			boolean mine = p == mc.player;
			if (mine ? (!self.value || firstPerson) : !others.value) {
				continue;
			}
			double x = Mth.lerp(partial, p.xo, p.getX());
			double y = Mth.lerp(partial, p.yo, p.getY()) + p.getBbHeight() + 0.02 - (p.isCrouching() ? 0.15 : 0);
			double z = Mth.lerp(partial, p.zo, p.getZ());
			Vec3 apex = new Vec3(x, y + hatHeight.value, z);
			int seg = 36;
			for (int i = 0; i < seg; i++) {
				double a1 = Math.PI * 2 * i / seg;
				double a2 = Math.PI * 2 * (i + 1) / seg;
				Vec3 p1 = new Vec3(x + Math.cos(a1) * radius.value, y, z + Math.sin(a1) * radius.value);
				Vec3 p2 = new Vec3(x + Math.cos(a2) * radius.value, y, z + Math.sin(a2) * radius.value);
				out.add(new EspLine(p1, p2, VisualColor.pick(colorMode, staticColor, i / (float) seg), opacity.f(),
					width.f(), true));
			}
			int n = spokes.i();
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n;
				Vec3 base = new Vec3(x + Math.cos(a) * radius.value, y, z + Math.sin(a) * radius.value);
				out.add(new EspLine(base, apex, VisualColor.pick(colorMode, staticColor, i / (float) n),
					opacity.f() * 0.7f, width.f() * 0.8f, true));
			}
		}
	}
}
