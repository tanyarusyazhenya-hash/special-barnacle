package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspProvider;
import dev.aurora.render.EspStyle;
import dev.aurora.render.TracerSource;
import dev.aurora.render.TracerTarget;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpawnerFinder extends Module implements EspProvider, TracerSource {
	private record Target(AABB box, int rgb, double distSq) {}

	public final BoolSetting spawners = add(new BoolSetting("Spawners", true));
	public final BoolSetting trialSpawners = add(new BoolSetting("Trial Spawners", false));
	public final ColorSetting spawnerColor = add(new ColorSetting("Spawner Color", 0xEF4444));
	public final ColorSetting trialColor = add(new ColorSetting("Trial Color", 0x22D3EE));
	public final NumberSetting minY = add(new NumberSetting("Min Y", -64, -64, 320, 1));
	public final NumberSetting maxY = add(new NumberSetting("Max Y", 320, -64, 320, 1));
	public final BoolSetting notify = add(new BoolSetting("Chat Notify", true));
	public final NumberSetting updateRate = add(new NumberSetting("Update Rate (ticks)", 10, 1, 40, 1));
	public final EspStyle style;

	private final List<Target> targets = new ArrayList<>();
	private final Set<BlockPos> known = new HashSet<>();
	private Object lastLevel;
	private int cooldown;

	public SpawnerFinder() {
		super("SpawnerFinder", "Highlights mob spawners", Category.RENDER);
		style = new EspStyle(this);
		spawnerColor.visibleWhen(() -> spawners.value);
		trialColor.visibleWhen(() -> trialSpawners.value);
	}

	@Override
	public String info() {
		return style.mode.get();
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
	}

	@Override
	protected void onDisable() {
		targets.clear();
		known.clear();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			targets.clear();
			known.clear();
			lastLevel = null;
			return;
		}
		if (lastLevel != mc.level) {
			lastLevel = mc.level;
			known.clear();
		}
		if (--cooldown > 0) {
			return;
		}
		cooldown = updateRate.i();
		scan(mc);
	}

	private void scan(Minecraft mc) {
		List<Target> found = new ArrayList<>();
		double range = style.range.value;
		double r2 = range * range;
		Vec3 player = mc.player.position();
		int chunkRadius = (int) Math.ceil(range / 16.0) + 1;
		int pcx = mc.player.blockPosition().getX() >> 4;
		int pcz = mc.player.blockPosition().getZ() >> 4;

		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				ChunkAccess access = mc.level.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
				if (!(access instanceof LevelChunk chunk)) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					int rgb;
					if (be instanceof SpawnerBlockEntity) {
						if (!spawners.value) continue;
						rgb = spawnerColor.rgb();
					} else if (be instanceof TrialSpawnerBlockEntity) {
						if (!trialSpawners.value) continue;
						rgb = trialColor.rgb();
					} else {
						continue;
					}

					BlockPos pos = be.getBlockPos();
					if (pos.getY() < minY.value || pos.getY() > maxY.value) {
						continue;
					}
					AABB box = new AABB(pos).inflate(0.002);
					double distSq = player.distanceToSqr(box.getCenter());
					if (distSq > r2) {
						continue;
					}
					found.add(new Target(box, rgb, distSq));

					if (known.add(pos.immutable()) && notify.value) {
						mc.player.displayClientMessage(Component.literal(
							"§d[Aurora]§r Spawner found at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
					}
				}
			}
		}
		targets.clear();
		targets.addAll(found);
	}

	@Override
	public void tracerTargets(List<TracerTarget> out) {
		for (Target t : targets) {
			out.add(new TracerTarget(t.box().getCenter(), t.rgb()));
		}
	}

	@Override
	public void collect(List<EspBox> out) {
		for (Target t : targets) {
			out.add(style.box(t.box(), t.rgb(), t.distSq()));
		}
	}
}
