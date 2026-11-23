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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A fading colored ribbon behind players (you, and optionally others). */
public class Trail extends Module implements LineProvider {
	public final NumberSetting length = add(new NumberSetting("Length (ticks)", 40, 10, 200, 5));
	public final NumberSetting width = add(new NumberSetting("Width", 2.0, 0.5, 5.0, 0.25));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.8, 0.1, 1.0, 0.05));
	public final ModeSetting colorMode = add(VisualColor.mode());
	public final ColorSetting staticColor = add(new ColorSetting("Static Color", 0xFFFFFF));
	public final BoolSetting self = add(new BoolSetting("Yourself", true));
	public final BoolSetting others = add(new BoolSetting("Other Players", false));
	public final BoolSetting firstPerson = add(new BoolSetting("Show In First Person", false));
	public final BoolSetting fromFeet = add(new BoolSetting("From Feet", true));

	private final Map<UUID, Deque<Vec3>> trails = new HashMap<>();

	public Trail() {
		super("Trail", "Colored ribbon behind players", Category.RENDER);
		staticColor.visibleWhen(() -> colorMode.is("Static"));
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			trails.clear();
			return;
		}
		int max = length.i();
		for (Player p : mc.level.players()) {
			if (p == mc.player ? !self.value : !others.value) {
				continue;
			}
			Deque<Vec3> d = trails.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
			Vec3 pos = p.position().add(0, fromFeet.value ? 0.1 : p.getBbHeight() * 0.5, 0);
			Vec3 last = d.peekLast();
			if (last == null || last.distanceToSqr(pos) > 0.0004) {
				d.addLast(pos);
			} else if (!d.isEmpty()) {
				d.pollFirst(); // standing still: let the trail shrink
			}
			while (d.size() > max) {
				d.pollFirst();
			}
		}
		trails.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
	}

	@Override
	public void collectLines(List<EspLine> out, Vec3 start) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || (mc.options.getCameraType().isFirstPerson() && !firstPerson.value)) {
			return;
		}
		for (var e : trails.entrySet()) {
			boolean mine = e.getKey().equals(mc.player.getUUID());
			if (mine && mc.options.getCameraType().isFirstPerson() && !firstPerson.value) {
				continue;
			}
			Vec3[] pts = e.getValue().toArray(new Vec3[0]);
			for (int i = 1; i < pts.length; i++) {
				float t = i / (float) pts.length;
				int rgb = VisualColor.pick(colorMode, staticColor, t);
				out.add(new EspLine(pts[i - 1], pts[i], rgb, opacity.f() * t, width.f(), true));
			}
		}
	}
}
