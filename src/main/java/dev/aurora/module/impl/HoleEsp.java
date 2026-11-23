package dev.aurora.module.impl;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspProvider;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Highlights one-block holes ringed by bedrock and obsidian (safe spots in crystal PvP). */
public class HoleEsp extends Module implements EspProvider {
	public final NumberSetting range = add(new NumberSetting("Range", 8, 3, 16, 1));
	public final NumberSetting vertical = add(new NumberSetting("Vertical Range", 4, 1, 8, 1));
	public final BoolSetting bedrock = add(new BoolSetting("Bedrock Holes", true));
	public final BoolSetting obsidian = add(new BoolSetting("Obsidian Holes", true));
	public final BoolSetting mixed = add(new BoolSetting("Mixed Holes", true));
	public final ColorSetting bedrockColor = add(new ColorSetting("Bedrock Color", 0x4ADE80));
	public final ColorSetting obsidianColor = add(new ColorSetting("Obsidian Color", 0xF87171));
	public final ColorSetting mixedColor = add(new ColorSetting("Mixed Color", 0xFACC15));
	public final NumberSetting outline = add(new NumberSetting("Outline Opacity", 0.9, 0.05, 1.0, 0.05));
	public final NumberSetting fill = add(new NumberSetting("Fill Opacity", 0.25, 0.0, 1.0, 0.05));
	public final NumberSetting height = add(new NumberSetting("Height", 0.1, 0.02, 1.0, 0.02));
	public final BoolSetting throughWalls = add(new BoolSetting("Through Walls", true));

	private record Hole(BlockPos pos, int kind) {}

	private final List<Hole> holes = new ArrayList<>();
	private int timer;

	public HoleEsp() {
		super("HoleESP", "Highlights bedrock and obsidian holes", Category.RENDER);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			holes.clear();
			return;
		}
		if (timer-- > 0) {
			return;
		}
		timer = 8;
		holes.clear();
		BlockPos o = mc.player.blockPosition();
		int r = range.i();
		int v = vertical.i();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -v; dy <= v; dy++) {
					BlockPos p = o.offset(dx, dy, dz);
					if (!mc.level.getBlockState(p).isAir() || !mc.level.getBlockState(p.above()).isAir()
						|| !mc.level.getBlockState(p.above(2)).isAir()) {
						continue;
					}
					int kind = kindOf(mc, p);
					if (kind >= 0) {
						holes.add(new Hole(p, kind));
					}
				}
			}
		}
	}

	/** 0 = bedrock, 1 = obsidian, 2 = mixed, -1 = not a hole. */
	private int kindOf(Minecraft mc, BlockPos p) {
		int bed = 0;
		int obs = 0;
		for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST,
			Direction.WEST}) {
			BlockState s = mc.level.getBlockState(p.relative(d));
			if (s.is(Blocks.BEDROCK)) {
				bed++;
			} else if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)) {
				obs++;
			} else {
				return -1;
			}
		}
		if (obs == 0 && bedrock.value) {
			return 0;
		}
		if (bed == 0 && obsidian.value) {
			return 1;
		}
		return bed > 0 && obs > 0 && mixed.value ? 2 : -1;
	}

	@Override
	public void collect(List<EspBox> out) {
		for (Hole h : holes) {
			int rgb = h.kind() == 0 ? bedrockColor.rgb() : h.kind() == 1 ? obsidianColor.rgb() : mixedColor.rgb();
			BlockPos p = h.pos();
			AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + height.value, p.getZ() + 1);
			out.add(new EspBox(box, rgb, outline.f(), fill.f(), 1.5f, 2, throughWalls.value));
		}
	}
}
