package dev.aurora.module.impl;

import dev.aurora.hud.Notifications;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspLine;
import dev.aurora.render.EspProvider;
import dev.aurora.render.EspStyle;
import dev.aurora.render.LineProvider;
import dev.aurora.setting.ActionSetting;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.path.PathFollower;
import dev.aurora.path.Pathfinder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Looks for player bases and walks / digs to search for them (Aurora's own A* path finder, no Baritone).
 * <p>
 * Finding: block entities (chests, barrels, shulker boxes, hoppers, furnaces, beds...) are sent to the
 * client even when the server hides ores with anti-xray, so clusters of them inside your loaded
 * chunks are reported as probable bases. Villages also have such blocks, so expect some false alarms.
 * <p>
 * Explore: travels along long lanes (Straight or Serpentine), plans a path every few seconds toward a
 * point ahead, walks, jumps, drops down and mines through whatever is in the way. New chunks load as
 * you move, so they get scanned. Go To Base: the same, toward the nearest base found. It stops on
 * low health, nearby players, liquids or lava and when it cannot find a way. It does not eat, place
 * blocks or fight mobs. Off by default every launch.
 */
public class BaseFinder extends Module implements EspProvider, LineProvider {
	private record Sign(BlockPos pos, boolean storage) {}

	private record Base(Vec3 center, int count, int storage) {}

	public final ModeSetting mode = add(new ModeSetting("Mode", "Scan", "Scan", "Explore", "Go To Base"));
	public final ModeSetting direction = add(new ModeSetting("Explore Direction", "Facing", "Facing", "North", "South",
		"East", "West"));
	public final ModeSetting pattern = add(new ModeSetting("Explore Pattern", "Serpentine", "Straight", "Serpentine"));
	public final ModeSetting level = add(new ModeSetting("Explore Level", "Keep Y", "Keep Y", "Surface"));
	public final NumberSetting laneLength = add(new NumberSetting("Lane Length", 1500, 200, 10000, 100));
	public final NumberSetting chunkRadius = add(new NumberSetting("Scan Radius (chunks)", 10, 2, 32, 1));
	public final NumberSetting minBlocks = add(new NumberSetting("Min Blocks For Base", 6, 2, 40, 1));
	public final NumberSetting minStorage = add(new NumberSetting("Min Storage Blocks", 3, 0, 20, 1));
	public final BoolSetting storageBlocks = add(new BoolSetting("Chests / Barrels / Shulkers / Hoppers", true));
	public final BoolSetting utilityBlocks = add(new BoolSetting("Furnaces / Beds / Brewing / Beacons", true));
	public final BoolSetting enderChests = add(new BoolSetting("Ender Chests", false));
	public final BoolSetting spawners = add(new BoolSetting("Spawners", false));
	public final BoolSetting chat = add(new BoolSetting("Chat Message On Find", true));
	public final BoolSetting toast = add(new BoolSetting("Notification On Find", true));
	public final BoolSetting tracer = add(new BoolSetting("Line To Nearest Base", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF7A1A));

	// travel
	public final BoolSetting allowDig = add(new BoolSetting("Allow Digging", true));
	public final BoolSetting autoTool = add(new BoolSetting("Auto Tool", true));
	public final BoolSetting sprint = add(new BoolSetting("Sprint", true));
	public final BoolSetting stopOnBase = add(new BoolSetting("Stop When Base Found", true));
	public final BoolSetting walkToFound = add(new BoolSetting("Then Walk To It", true));
	public final BoolSetting stopOnPlayer = add(new BoolSetting("Stop When Player Near", true));
	public final NumberSetting playerDistance = add(new NumberSetting("Player Distance", 24, 8, 64, 2));
	public final NumberSetting minHealth = add(new NumberSetting("Stop Below Health", 8, 1, 20, 1));
	public final ActionSetting clearFound = add(new ActionSetting("Forget Found Bases", this::forget));
	public final EspStyle style;

	private final List<Sign> signs = new ArrayList<>();
	private final List<Base> bases = new ArrayList<>();
	private final List<Vec3> known = new ArrayList<>();
	private final Set<Long> announced = new HashSet<>();
	private final PathFollower follower = new PathFollower();
	private int scanTimer;
	private String status = "Scanning";
	private int lastMode = -1;

	// explore state
	private Direction laneDir = Direction.NORTH;
	private Direction heading = Direction.NORTH;
	private Direction shiftSide = Direction.EAST;
	private boolean shifting;
	private Vec3 segStart = Vec3.ZERO;
	private double segLen;
	private int fails;
	private int sideStep;

	public BaseFinder() {
		super("BaseFinder", "Finds probable bases from containers; walks and digs to search for them", Category.WORLD);
		style = new EspStyle(this);
		style.range.set(300);
		for (var s : new dev.aurora.setting.Setting[]{direction, pattern, level, laneLength}) {
			s.visibleWhen(() -> mode.is("Explore"));
		}
		laneLength.visibleWhen(() -> mode.is("Explore") && pattern.is("Serpentine"));
		autoTool.visibleWhen(() -> !mode.is("Scan") && allowDig.value);
		allowDig.visibleWhen(() -> !mode.is("Scan"));
		sprint.visibleWhen(() -> !mode.is("Scan"));
		stopOnBase.visibleWhen(() -> mode.is("Explore"));
		walkToFound.visibleWhen(() -> mode.is("Explore") && stopOnBase.value);
		stopOnPlayer.visibleWhen(() -> !mode.is("Scan"));
		playerDistance.visibleWhen(() -> !mode.is("Scan") && stopOnPlayer.value);
		minHealth.visibleWhen(() -> !mode.is("Scan"));
	}

	@Override
	public boolean persistEnabled() {
		return false;
	}

	@Override
	public String info() {
		return bases.isEmpty() ? status : status + " (" + bases.size() + ")";
	}

	@Override
	protected void onEnable() {
		scanTimer = 0;
		lastMode = -1;
	}

	@Override
	protected void onDisable() {
		KeyMapping.setAll();
		follower.clear();
		signs.clear();
		bases.clear();
	}

	private void forget() {
		announced.clear();
		bases.clear();
		known.clear();
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			signs.clear();
			bases.clear();
			return;
		}
		if (scanTimer-- <= 0) {
			scanTimer = 20;
			scan(mc);
		}
		if (mode.index() != lastMode) {
			lastMode = mode.index();
			follower.clear();
			fails = 0;
			sideStep = 0;
			KeyMapping.setAll();
			if (mode.is("Explore")) {
				startExplore(mc);
			}
		}
		if (mode.is("Scan")) {
			status = "Scanning";
			return;
		}
		if (mc.screen != null || !mc.player.isAlive() || ModuleManager.freecam.isEnabled()) {
			return;
		}
		if (safetyStop(mc)) {
			return;
		}
		if (mode.is("Explore")) {
			explore(mc);
		} else {
			goToBase(mc);
		}
	}

	private boolean wanted(Block block) {
		String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (id.equals("ender_chest")) {
			return enderChests.value;
		}
		if (id.contains("spawner")) {
			return spawners.value;
		}
		if (isStorage(id)) {
			return storageBlocks.value;
		}
		return utilityBlocks.value && (id.contains("furnace") || id.equals("smoker") || id.endsWith("_bed")
			|| id.equals("brewing_stand") || id.equals("enchanting_table") || id.equals("beacon")
			|| id.equals("lectern") || id.equals("jukebox"));
	}

	private static boolean isStorage(String id) {
		return id.contains("chest") && !id.equals("ender_chest") || id.equals("barrel") || id.contains("shulker_box")
			|| id.equals("hopper") || id.equals("dropper") || id.equals("dispenser");
	}

	private void scan(Minecraft mc) {
		signs.clear();
		int r = Math.min(chunkRadius.i(), mc.options.getEffectiveRenderDistance() + 2);
		int pcx = mc.player.blockPosition().getX() >> 4;
		int pcz = mc.player.blockPosition().getZ() >> 4;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				ChunkAccess access = mc.level.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
				if (!(access instanceof LevelChunk chunk)) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					Block block = be.getBlockState().getBlock();
					if (wanted(block)) {
						String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
						signs.add(new Sign(be.getBlockPos(), isStorage(id)));
					}
				}
				if (signs.size() > 2500) {
					break;
				}
			}
		}
		findBases(mc);
	}

	private void findBases(Minecraft mc) {
		bases.clear();
		Set<Long> cells = new HashSet<>();
		for (Sign a : signs) {
			int count = 0;
			int storage = 0;
			double sx = 0, sy = 0, sz = 0;
			for (Sign b : signs) {
				int dx = a.pos().getX() - b.pos().getX();
				int dz = a.pos().getZ() - b.pos().getZ();
				int dy = a.pos().getY() - b.pos().getY();
				if (dx * dx + dz * dz <= 576 && Math.abs(dy) <= 24) {
					count++;
					if (b.storage()) {
						storage++;
					}
					sx += b.pos().getX();
					sy += b.pos().getY();
					sz += b.pos().getZ();
				}
			}
			if (count < minBlocks.i() || storage < minStorage.i()) {
				continue;
			}
			long cell = ((long) Math.floorDiv(a.pos().getX(), 40) << 32) ^ (Math.floorDiv(a.pos().getZ(), 40) & 0xFFFFFFFFL);
			if (!cells.add(cell)) {
				continue;
			}
			Vec3 center = new Vec3(sx / count, sy / count, sz / count);
			bases.add(new Base(center, count, storage));
			if (announced.add(cell)) {
				known.add(center);
				announce(mc, center, count, storage);
			}
		}
	}

	private void announce(Minecraft mc, Vec3 c, int count, int storage) {
		String where = (int) c.x + " " + (int) c.y + " " + (int) c.z;
		int dist = (int) mc.player.position().distanceTo(c);
		String text = "Probable base at " + where + " (" + dist + " blocks, " + count + " blocks, " + storage
			+ " storage)";
		if (chat.value) {
			mc.player.displayClientMessage(Component.literal("\u00a7d[Aurora]\u00a7r " + text), false);
		}
		if (toast.value) {
			Notifications.push("BaseFinder", "Base at " + where + "  " + dist + "m", color.rgb());
		}
		if (mode.is("Explore") && stopOnBase.value) {
			follower.clear();
			KeyMapping.setAll();
			if (walkToFound.value) {
				mode.set(2);
				lastMode = 2;
				fails = 0;
			} else {
				stop("Base found");
			}
		}
	}

	// ------------------------------------------------------------------ travel

	private void stop(String reason) {
		status = reason;
		mode.set(0);
		lastMode = 0;
		follower.clear();
		KeyMapping.setAll();
		Notifications.push("BaseFinder", "Stopped: " + reason, 0xF87171);
	}

	private boolean safetyStop(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p.getHealth() + p.getAbsorptionAmount() < minHealth.value) {
			stop("Low health");
			return true;
		}
		if (stopOnPlayer.value) {
			for (Player other : mc.level.players()) {
				if (other != p && p.distanceTo(other) < playerDistance.value && !ModuleManager.friends.isFriend(other)) {
					stop("Player near: " + other.getName().getString());
					return true;
				}
			}
		}
		return false;
	}

	private void startExplore(Minecraft mc) {
		laneDir = switch (direction.get()) {
			case "North" -> Direction.NORTH;
			case "South" -> Direction.SOUTH;
			case "East" -> Direction.EAST;
			case "West" -> Direction.WEST;
			default -> mc.player.getDirection();
		};
		heading = laneDir;
		shiftSide = laneDir.getClockWise();
		shifting = false;
		segStart = mc.player.position();
		segLen = pattern.is("Serpentine") ? laneLength.value : 1.0e9;
	}

	private double progress(Minecraft mc) {
		Vec3 d = mc.player.position().subtract(segStart);
		return d.x * heading.getStepX() + d.z * heading.getStepZ();
	}

	private void nextSegment(Minecraft mc) {
		segStart = mc.player.position();
		if (!shifting) {
			shifting = true;
			heading = shiftSide;
			double r = Math.min(chunkRadius.i(), mc.options.getEffectiveRenderDistance());
			segLen = Math.max(24, r * 32 * 0.9);
		} else {
			shifting = false;
			laneDir = laneDir.getOpposite();
			heading = laneDir;
			segLen = laneLength.value;
		}
	}

	private void explore(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (progress(mc) >= segLen) {
			nextSegment(mc);
			follower.clear();
		}
		if (follower.hasPath()) {
			PathFollower.State st = follower.tick(mc, allowDig.value, autoTool.value, sprint.value);
			status = follower.mining ? "Digging" : "Exploring " + heading.getName();
			if (st == PathFollower.State.RUNNING) {
				return;
			}
			if (st == PathFollower.State.FAILED) {
				fails++;
				sideStep = (sideStep + 1) % 4;
				follower.clear();
				if (fails > 5) {
					stop("Cannot find a way (" + follower.failReason + ")");
				}
				return;
			}
			fails = 0;
			follower.clear();
		}
		double ahead = Math.min(40, Math.max(8, segLen - progress(mc)));
		int off = switch (sideStep) {
			case 1 -> 6;
			case 2 -> -6;
			case 3 -> 12;
			default -> 0;
		};
		Direction side = heading.getClockWise();
		int gx = p.blockPosition().getX() + (int) (heading.getStepX() * ahead) + side.getStepX() * off;
		int gz = p.blockPosition().getZ() + (int) (heading.getStepZ() * ahead) + side.getStepZ() * off;
		int gy = p.blockPosition().getY();
		if (level.is("Surface") && mc.level.dimension() != Level.NETHER
			&& mc.level.getChunkSource().hasChunk(gx >> 4, gz >> 4)) {
			gy = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
		}
		plan(mc, new BlockPos(gx, gy, gz));
	}

	private void plan(Minecraft mc, BlockPos goal) {
		List<BlockPos> path = Pathfinder.find(mc.level, mc.player.blockPosition(), goal, allowDig.value, 4000);
		if (path.size() < 2) {
			fails++;
			sideStep = (sideStep + 1) % 4;
			status = "No path, retrying";
			if (fails > 5) {
				stop("Cannot find a way");
			}
			return;
		}
		follower.setPath(path);
	}

	private void goToBase(Minecraft mc) {
		LocalPlayer p = mc.player;
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (Vec3 c : known) {
			double d = p.position().distanceToSqr(c);
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		if (best == null) {
			stop("No base found yet");
			return;
		}
		double hd = Math.hypot(best.x - p.getX(), best.z - p.getZ());
		if (hd <= 12) {
			stop("Arrived at the base");
			return;
		}
		if (follower.hasPath()) {
			PathFollower.State st = follower.tick(mc, allowDig.value, autoTool.value, sprint.value);
			status = follower.mining ? "Digging" : "Going to base " + (int) hd + "m";
			if (st == PathFollower.State.RUNNING) {
				return;
			}
			if (st == PathFollower.State.FAILED) {
				fails++;
				sideStep = (sideStep + 1) % 4;
				follower.clear();
				if (fails > 5) {
					stop("Cannot find a way (" + follower.failReason + ")");
				}
				return;
			}
			fails = 0;
			follower.clear();
		}
		double step = Math.min(40, hd - 8);
		double nx = (best.x - p.getX()) / hd;
		double nz = (best.z - p.getZ()) / hd;
		double off = switch (sideStep) {
			case 1 -> 6;
			case 2 -> -6;
			case 3 -> 12;
			default -> 0;
		};
		int gx = (int) Math.floor(p.getX() + nx * step - nz * off);
		int gz = (int) Math.floor(p.getZ() + nz * step + nx * off);
		int gy = hd <= 48 ? (int) best.y : p.blockPosition().getY();
		plan(mc, new BlockPos(gx, gy, gz));
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void collect(List<EspBox> out) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		int rgb = color.rgb();
		for (Sign s : signs) {
			BlockPos p = s.pos();
			AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1).inflate(0.002);
			out.add(style.box(box, rgb, mc.player.distanceToSqr(Vec3.atCenterOf(p))));
		}
		for (Base b : bases) {
			Vec3 c = b.center();
			AABB pillar = new AABB(c.x - 1.5, c.y - 30, c.z - 1.5, c.x + 1.5, c.y + 60, c.z + 1.5);
			out.add(new EspBox(pillar, rgb, 0.5f, 0.12f, 1.5f, 2, true));
		}
	}

	@Override
	public void collectLines(List<EspLine> out, Vec3 start) {
		if (!tracer.value || bases.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Base nearest = null;
		double bestD = Double.MAX_VALUE;
		for (Base b : bases) {
			double d = mc.player.position().distanceToSqr(b.center());
			if (d < bestD) {
				bestD = d;
				nearest = b;
			}
		}
		if (nearest != null) {
			out.add(new EspLine(start, nearest.center(), color.rgb(), 0.9f, 2f, true));
		}
	}
}
