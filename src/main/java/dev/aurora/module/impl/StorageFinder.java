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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class StorageFinder extends Module implements EspProvider {
	private record Target(AABB box, int rgb, double distSq) {}

	// Which containers to show
	public final BoolSetting chests = add(new BoolSetting("Chests", true));
	public final BoolSetting trapped = add(new BoolSetting("Trapped Chests", true));
	public final BoolSetting shulkers = add(new BoolSetting("Shulker Boxes", true));
	public final BoolSetting droppers = add(new BoolSetting("Droppers", true));
	public final BoolSetting ender = add(new BoolSetting("Ender Chests", false));
	public final BoolSetting barrels = add(new BoolSetting("Barrels", false));
	public final BoolSetting dispensers = add(new BoolSetting("Dispensers", false));
	public final BoolSetting merge = add(new BoolSetting("Merge Double Chests", true));

	// Colors per type
	public final ColorSetting chestColor = add(new ColorSetting("Chest Color", 0xF5A623));
	public final ColorSetting trappedColor = add(new ColorSetting("Trapped Color", 0xE5484D));
	public final ColorSetting shulkerColor = add(new ColorSetting("Shulker Color", 0xE879F9));
	public final ColorSetting dropperColor = add(new ColorSetting("Dropper Color", 0x38BDF8));
	public final ColorSetting enderColor = add(new ColorSetting("Ender Color", 0xA855F7));
	public final ColorSetting barrelColor = add(new ColorSetting("Barrel Color", 0xC08552));
	public final ColorSetting dispenserColor = add(new ColorSetting("Dispenser Color", 0x34D399));

	public final NumberSetting updateRate = add(new NumberSetting("Update Rate (ticks)", 10, 1, 40, 1));
	public final EspStyle style;

	private final List<Target> targets = new ArrayList<>();
	private int cooldown;

	public StorageFinder() {
		super("StorageFinder", "Highlights chests, shulker boxes and droppers", Category.RENDER);
		style = new EspStyle(this);

		chestColor.visibleWhen(() -> chests.value);
		trappedColor.visibleWhen(() -> trapped.value);
		shulkerColor.visibleWhen(() -> shulkers.value);
		dropperColor.visibleWhen(() -> droppers.value);
		enderColor.visibleWhen(() -> ender.value);
		barrelColor.visibleWhen(() -> barrels.value);
		dispenserColor.visibleWhen(() -> dispensers.value);
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
	}

	@Override
	protected void onDisable() {
		targets.clear();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			targets.clear();
			return;
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
					classify(be, player, r2, found);
				}
			}
		}
		targets.clear();
		targets.addAll(found);
	}

	private void classify(BlockEntity be, Vec3 player, double r2, List<Target> out) {
		BlockPos pos = be.getBlockPos();
		AABB box;
		int rgb;

		// Order matters: Dropper extends Dispenser, TrappedChest extends Chest.
		if (be instanceof DropperBlockEntity) {
			if (!droppers.value) return;
			box = full(pos);
			rgb = dropperColor.rgb();
		} else if (be instanceof DispenserBlockEntity) {
			if (!dispensers.value) return;
			box = full(pos);
			rgb = dispenserColor.rgb();
		} else if (be instanceof ShulkerBoxBlockEntity) {
			if (!shulkers.value) return;
			box = full(pos);
			rgb = shulkerColor.rgb();
		} else if (be instanceof BarrelBlockEntity) {
			if (!barrels.value) return;
			box = full(pos);
			rgb = barrelColor.rgb();
		} else if (be instanceof EnderChestBlockEntity) {
			if (!ender.value) return;
			box = chestBox(pos);
			rgb = enderColor.rgb();
		} else if (be instanceof ChestBlockEntity) {
			boolean isTrapped = be instanceof TrappedChestBlockEntity;
			if (isTrapped ? !trapped.value : !chests.value) return;
			rgb = isTrapped ? trappedColor.rgb() : chestColor.rgb();
			box = chestBox(pos);

			BlockState state = be.getBlockState();
			if (merge.value && state.hasProperty(ChestBlock.TYPE)) {
				ChestType type = state.getValue(ChestBlock.TYPE);
				if (type == ChestType.RIGHT) {
					return; // the LEFT half draws the merged box
				}
				if (type == ChestType.LEFT) {
					BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
					box = box.minmax(chestBox(other));
				}
			}
		} else {
			return;
		}

		double distSq = player.distanceToSqr(box.getCenter());
		if (distSq > r2) {
			return;
		}
		out.add(new Target(box, rgb, distSq));
	}

	private static AABB full(BlockPos p) {
		return new AABB(p).inflate(0.002);
	}

	private static AABB chestBox(BlockPos p) {
		double x = p.getX(), y = p.getY(), z = p.getZ();
		return new AABB(x + 0.0625, y, z + 0.0625, x + 0.9375, y + 0.875, z + 0.9375).inflate(0.002);
	}

	@Override
	public void collect(List<EspBox> out) {
		for (Target t : targets) {
			out.add(style.box(t.box(), t.rgb(), t.distSq()));
		}
	}
}
