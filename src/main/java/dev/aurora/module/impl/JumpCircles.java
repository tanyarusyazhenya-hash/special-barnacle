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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A ring that spreads out on the ground where a player jumped. */
public class JumpCircles extends Module implements LineProvider {
	public final NumberSetting maxRadius = add(new NumberSetting("Max Radius", 1.4, 0.5, 3.0, 0.1));
	public final NumberSetting duration = add(new NumberSetting("Duration (ms)", 800, 300, 2000, 50));
	public final NumberSetting width = add(new NumberSetting("Width", 2.0, 0.5, 5.0, 0.25));
	public final ModeSetting colorMode = add(VisualColor.mode());
	public final ColorSetting staticColor = add(new ColorSetting("Static Color", 0xFFFFFF));
	public final BoolSetting self = add(new BoolSetting("Yourself", true));
	public final BoolSetting others = add(new BoolSetting("Other Players", true));

	private record Ring(Vec3 pos, long born) {}

	private final List<Ring> rings = new ArrayList<>();
	private final Set<UUID> airborne = new HashSet<>();

	public JumpCircles() {
		super("JumpCircles", "Rings on the ground where players jump", Category.RENDER);
		staticColor.visibleWhen(() -> colorMode.is("Static"));
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			rings.clear();
			return;
		}
		for (Player p : mc.level.players()) {
			if (p == mc.player ? !self.value : !others.value) {
				continue;
			}
			UUID id = p.getUUID();
			if (p.onGround()) {
				airborne.remove(id);
			} else if (airborne.add(id) && p.getDeltaMovement().y > 0.2) {
				rings.add(new Ring(new Vec3(p.getX(), p.getY() + 0.03, p.getZ()), System.currentTimeMillis()));
			}
		}
		while (rings.size() > 60) {
			rings.remove(0);
		}
	}

	@Override
	public void collectLines(List<EspLine> out, Vec3 start) {
		long now = System.currentTimeMillis();
		Iterator<Ring> it = rings.iterator();
		while (it.hasNext()) {
			Ring r = it.next();
			float k = (now - r.born()) / (float) duration.value;
			if (k >= 1f) {
				it.remove();
				continue;
			}
			double radius = maxRadius.value * (1.0 - Math.pow(1.0 - k, 2));
			float alpha = 1f - k;
			int seg = 36;
			for (int i = 0; i < seg; i++) {
				double a1 = Math.PI * 2 * i / seg;
				double a2 = Math.PI * 2 * (i + 1) / seg;
				Vec3 p1 = r.pos().add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
				Vec3 p2 = r.pos().add(Math.cos(a2) * radius, 0, Math.sin(a2) * radius);
				int rgb = VisualColor.pick(colorMode, staticColor, i / (float) seg);
				out.add(new EspLine(p1, p2, rgb, alpha, width.f(), true));
			}
		}
	}
}
