package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspProvider;
import dev.aurora.render.EspStyle;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Highlights chosen ore blocks in loaded chunks. Chunks are scanned a few per tick and refreshed
 * periodically, so it stays cheap. Off by default: many servers do not allow ore finders.
 */
public class BlockFinder extends Module implements EspProvider {
	private record Ore(BoolSetting on, ColorSetting color, Block[] blocks) {}

	private record Found(AABB box, Ore ore, int y) {}

	private static final int REFRESH_TICKS = 100;

	public final BoolSetting diamond = add(new BoolSetting("Diamond", true));
	public final ColorSetting diamondColor = add(new ColorSetting("Diamond Color", 0x38E1F5));
	public final BoolSetting debris = add(new BoolSetting("Ancient Debris", true));
	public final ColorSetting debrisColor = add(new ColorSetting("Debris Color", 0xC2703D));
	public final BoolSetting emerald = add(new BoolSetting("Emerald", false));
	public final ColorSetting emeraldColor = add(new ColorSetting("Emerald Color", 0x22C55E));
	public final BoolSetting gold = add(new BoolSetting("Gold", false));
	public final ColorSetting goldColor = add(new ColorSetting("Gold Color", 0xFACC15));
	public final BoolSetting iron = add(new BoolSetting("Iron", false));
	public final ColorSetting ironColor = add(new ColorSetting("Iron Color", 0xD6B8A0));
	public final BoolSetting lapis = add(new BoolSetting("Lapis", false));
	public final ColorSetting lapisColor = add(new ColorSetting("Lapis Color", 0x3B5BDB));
	public final BoolSetting redstone = add(new BoolSetting("Redstone", false));
	public final ColorSetting redstoneColor = add(new ColorSetting("Redstone Color", 0xEF4444));
	public final BoolSetting coal = add(new BoolSetting("Coal", false));
	public final ColorSetting coalColor = add(new ColorSetting("Coal Color", 0x6B7280));
	public final BoolSetting copper = add(new BoolSetting("Copper", false));
	public final ColorSetting copperColor = add(new ColorSetting("Copper Color", 0xE8875A));
	public final BoolSetting quartz = add(new BoolSetting("Nether Quartz", false));
	public final ColorSetting quartzColor = add(new ColorSetting("Quartz Color", 0xF3F4F6));

	public final NumberSetting chunkRadius = add(new NumberSetting("Chunk Radius", 5, 1, 12, 1));
	public final NumberSetting scanSpeed = add(new NumberSetting("Scan Speed (chunks/tick)", 2, 1, 6, 1));
	public final NumberSetting minY = add(new NumberSetting("Min Y", -64, -64, 320, 1));
	public final NumberSetting maxY = add(new NumberSetting("Max Y", 320, -64, 320, 1));
	public final NumberSetting maxBlocks = add(new NumberSetting("Max Blocks", 400, 50, 3000, 50));
	public final EspStyle style;

	private final List<Ore> ores = new ArrayList<>();
	private final Map<Long, List<Found>> cache = new HashMap<>();
	private final Map<Long, Integer> scanned = new HashMap<>();
	private List<Found> merged = new ArrayList<>();
	private int tick;
	private int lastSignature = -1;
	private int[][] offsets;
	private int offsetRadius = -1;

	public BlockFinder() {
		super("BlockFinder", "Highlights selected ores (off by default)", Category.RENDER);
		style = new EspStyle(this);
		style.range.set(96);

		ores.add(new Ore(diamond, diamondColor, new Block[]{Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE}));
		ores.add(new Ore(debris, debrisColor, new Block[]{Blocks.ANCIENT_DEBRIS}));
		ores.add(new Ore(emerald, emeraldColor, new Block[]{Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE}));
		ores.add(new Ore(gold, goldColor, new Block[]{Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE}));
		ores.add(new Ore(iron, ironColor, new Block[]{Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE}));
		ores.add(new Ore(lapis, lapisColor, new Block[]{Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE}));
		ores.add(new Ore(redstone, redstoneColor, new Block[]{Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE}));
		ores.add(new Ore(coal, coalColor, new Block[]{Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE}));
		ores.add(new Ore(copper, copperColor, new Block[]{Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE}));
		ores.add(new Ore(quartz, quartzColor, new Block[]{Blocks.NETHER_QUARTZ_ORE}));
		for (Ore o : ores) {
			o.color().visibleWhen(() -> o.on().value);
		}
	}

	@Override
	public String info() {
		return style.mode.get();
	}

	@Override
	protected void onDisable() {
		cache.clear();
		scanned.clear();
		merged = new ArrayList<>();
		lastSignature = -1;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			cache.clear();
			scanned.clear();
			merged = new ArrayList<>();
			return;
		}
		tick++;

		Map<Block, Ore> lookup = new HashMap<>();
		int signature = 0;
		for (int i = 0; i < ores.size(); i++) {
			Ore ore = ores.get(i);
			if (ore.on().value) {
				signature |= 1 << i;
				for (Block b : ore.blocks()) {
					lookup.put(b, ore);
				}
			}
		}
		if (signature != lastSignature) {
			// the set of ores changed: forget everything and rescan
			lastSignature = signature;
			cache.clear();
			scanned.clear();
		}
		if (lookup.isEmpty()) {
			merged = new ArrayList<>();
			return;
		}

		int r = chunkRadius.i();
		int pcx = mc.player.blockPosition().getX() >> 4;
		int pcz = mc.player.blockPosition().getZ() >> 4;
		int budget = scanSpeed.i();
		boolean changed = false;

		for (int[] off : offsets(r)) {
			if (budget <= 0) {
				break;
			}
			int cx = pcx + off[0];
			int cz = pcz + off[1];
			long key = ChunkPos.asLong(cx, cz);
			Integer last = scanned.get(key);
			if (last != null && tick - last < REFRESH_TICKS) {
				continue;
			}
			ChunkAccess access = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false);
			if (!(access instanceof LevelChunk chunk)) {
				continue;
			}
			cache.put(key, scanChunk(mc, chunk, cx, cz, lookup));
			scanned.put(key, tick);
			budget--;
			changed = true;
		}

		if (tick % 40 == 0) {
			Iterator<Long> it = cache.keySet().iterator();
			while (it.hasNext()) {
				long key = it.next();
				if (Math.abs(ChunkPos.getX(key) - pcx) > r + 2 || Math.abs(ChunkPos.getZ(key) - pcz) > r + 2) {
					it.remove();
					scanned.remove(key);
				}
			}
		}

		if (changed || tick % 20 == 0) {
			List<Found> all = new ArrayList<>();
			for (List<Found> list : cache.values()) {
				all.addAll(list);
			}
			double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
			all.sort(Comparator.comparingDouble(f -> f.box().getCenter().distanceToSqr(px, py, pz)));
			int limit = Math.min(all.size(), maxBlocks.i());
			merged = new ArrayList<>(all.subList(0, limit));
		}
	}

	private List<Found> scanChunk(Minecraft mc, LevelChunk chunk, int cx, int cz, Map<Block, Ore> lookup) {
		List<Found> result = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();
		int minY = mc.level.getMinY();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir()) {
				continue;
			}
			if (!section.maybeHas(state -> lookup.containsKey(state.getBlock()))) {
				continue;
			}
			int baseY = minY + i * 16;
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						BlockState state = section.getBlockState(x, y, z);
						Ore ore = lookup.get(state.getBlock());
						if (ore == null) {
							continue;
						}
						int wx = (cx << 4) + x, wy = baseY + y, wz = (cz << 4) + z;
						result.add(new Found(new AABB(wx, wy, wz, wx + 1, wy + 1, wz + 1).inflate(0.002), ore, wy));
					}
				}
			}
		}
		return result;
	}

	/** Chunk offsets inside the radius, nearest first. */
	private int[][] offsets(int r) {
		if (offsets == null || offsetRadius != r) {
			List<int[]> list = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz <= r * r) {
						list.add(new int[]{dx, dz});
					}
				}
			}
			list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
			offsets = list.toArray(new int[0][]);
			offsetRadius = r;
		}
		return offsets;
	}

	@Override
	public void collect(List<EspBox> out) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		double r2 = style.range.value * style.range.value;
		for (Found f : merged) {
			if (f.y() < minY.value || f.y() > maxY.value) {
				continue;
			}
			double d2 = mc.player.distanceToSqr(f.box().getCenter());
			if (d2 > r2) {
				continue;
			}
			out.add(style.box(f.box(), f.ore().color().rgb(), d2));
		}
	}
}
