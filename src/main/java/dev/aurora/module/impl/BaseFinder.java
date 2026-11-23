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
import dev.aurora.util.CombatUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 * Looks for player bases and can dig toward them for you.
 * <p>
 * Finding: block entities (chests, barrels, shulker boxes, hoppers, furnaces, beds...) are sent to the
 * client even when the server hides ores with anti-xray, so clusters of them inside your loaded
 * chunks are reported as probable bases. Villages and other structures also have such blocks, so
 * expect some false alarms; raise the minimums to reduce them.
 * <p>
 * Tunnel mode digs a 1x2 tunnel in a straight line (loading new chunks as you go) and stops at
 * lava or water, gaps in the floor, containers, low health or when a player shows up. It only moves
 * and mines; it does not eat, place blocks or avoid mobs. Off by default every launch.
 */
public class BaseFinder extends Module implements EspProvider, LineProvider {
	private record Sign(BlockPos pos, boolean storage) {}

	private record Base(Vec3 center, int count, int storage) {}

	public final ModeSetting mode = add(new ModeSetting("Mode", "Scan", "Scan", "Tunnel"));
	public final ModeSetting direction = add(new ModeSetting("Tunnel Direction", "Facing", "Facing", "North", "South",
		"East", "West"));
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

	// tunnel safety
	public final BoolSetting autoPickaxe = add(new BoolSetting("Auto Pickaxe", true));
	public final BoolSetting stopOnBase = add(new BoolSetting("Stop When Base Found", true));
	public final BoolSetting stopOnPlayer = add(new BoolSetting("Stop When Player Near", true));
	public final NumberSetting playerDistance = add(new NumberSetting("Player Distance", 24, 8, 64, 2));
	public final NumberSetting minHealth = add(new NumberSetting("Stop Below Health", 8, 1, 20, 1));
	public final ActionSetting clearFound = add(new ActionSetting("Forget Found Bases", this::forget));
	public final EspStyle style;

	private final List<Sign> signs = new ArrayList<>();
	private final List<Base> bases = new ArrayList<>();
	private final Set<Long> announced = new HashSet<>();
	private int scanTimer;
	private Direction tunnelDir = Direction.NORTH;
	private String status = "Scanning";

	public BaseFinder() {
		super("BaseFinder", "Finds probable bases from containers; can dig toward them", Category.WORLD);
		style = new EspStyle(this);
		style.range.set(300);
		direction.visibleWhen(() -> mode.is("Tunnel"));
		autoPickaxe.visibleWhen(() -> mode.is("Tunnel"));
		stopOnBase.visibleWhen(() -> mode.is("Tunnel"));
		stopOnPlayer.visibleWhen(() -> mode.is("Tunnel"));
		playerDistance.visibleWhen(() -> mode.is("Tunnel") && stopOnPlayer.value);
		minHealth.visibleWhen(() -> mode.is("Tunnel"));
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
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			tunnelDir = switch (direction.get()) {
				case "North" -> Direction.NORTH;
				case "South" -> Direction.SOUTH;
				case "East" -> Direction.EAST;
				case "West" -> Direction.WEST;
				default -> mc.player.getDirection();
			};
		}
	}

	@Override
	protected void onDisable() {
		KeyMapping.setAll();
		signs.clear();
		bases.clear();
	}

	private void forget() {
		announced.clear();
		bases.clear();
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
			scanTimer = 40;
			scan(mc);
		}
		if (mode.is("Tunnel")) {
			tunnel(mc);
		} else {
			status = "Scanning";
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
		int r = Math.min(chunkRadius.i(), mc.options.getEffectiveRenderDistance());
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
			mc.player.displayClientMessage(Component.literal("§d[Aurora]§r " + text), false);
		}
		if (toast.value) {
			Notifications.push("BaseFinder", "Base at " + where + "  " + dist + "m", color.rgb());
		}
		if (mode.is("Tunnel") && stopOnBase.value) {
			stopTunnel("Base found");
		}
	}

	// ------------------------------------------------------------------ tunnel

	private void stopTunnel(String reason) {
		status = reason;
		mode.set(0);
		KeyMapping.setAll();
		Notifications.push("BaseFinder", "Tunnel stopped: " + reason, 0xF87171);
	}

	private boolean liquidAt(Minecraft mc, BlockPos pos) {
		return !mc.level.getBlockState(pos).getFluidState().isEmpty();
	}

	private void tunnel(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (mc.screen != null || !p.isAlive() || ModuleManager.freecam.isEnabled()) {
			return;
		}
		KeyMapping.setAll();
		if (p.getHealth() + p.getAbsorptionAmount() < minHealth.value) {
			stopTunnel("Low health");
			return;
		}
		if (stopOnPlayer.value) {
			for (Player other : mc.level.players()) {
				if (other != p && p.distanceTo(other) < playerDistance.value
					&& !ModuleManager.friends.isFriend(other)) {
					stopTunnel("Player near: " + other.getName().getString());
					return;
				}
			}
		}
		Direction d = tunnelDir;
		Direction left = d.getCounterClockWise();
		Direction right = d.getClockWise();
		BlockPos feet = p.blockPosition();
		BlockPos a1 = feet.relative(d);
		BlockPos a2 = a1.above();

		BlockPos[] hazards = {a1.relative(d), a2.relative(d), a2.above(), a1.relative(left), a1.relative(right),
			a2.relative(left), a2.relative(right), a1.below(), a1, a2};
		for (BlockPos h : hazards) {
			if (liquidAt(mc, h)) {
				stopTunnel("Liquid ahead");
				return;
			}
		}
		BlockState floor = mc.level.getBlockState(a1.below());
		BlockState s1 = mc.level.getBlockState(a1);
		BlockState s2 = mc.level.getBlockState(a2);
		if (floor.getCollisionShape(mc.level, a1.below()).isEmpty() && s1.isAir()) {
			stopTunnel("Gap in the floor");
			return;
		}
		if (mc.level.getBlockEntity(a1) != null || mc.level.getBlockEntity(a2) != null) {
			stopTunnel("Container ahead");
			return;
		}

		// face the tunnel direction
		float yaw = d.toYRot();
		float dyaw = net.minecraft.util.Mth.wrapDegrees(yaw - p.getYRot());
		p.setYRot(p.getYRot() + net.minecraft.util.Mth.clamp(dyaw, -35f, 35f));

		BlockPos dig = null;
		BlockState digState = null;
		if (!s2.isAir() && s2.getFluidState().isEmpty()) {
			dig = a2;
			digState = s2;
		} else if (!s1.isAir() && s1.getFluidState().isEmpty()) {
			dig = a1;
			digState = s1;
		}
		if (dig == null) {
			status = "Walking " + d.getName();
			if (Math.abs(dyaw) < 20f) {
				mc.options.keyUp.setDown(true);
			}
			return;
		}
		if (digState.getDestroySpeed(mc.level, dig) < 0) {
			stopTunnel("Unbreakable block");
			return;
		}
		if (autoPickaxe.value) {
			int best = -1;
			float bestSpeed = 1f;
			for (int i = 0; i < 9; i++) {
				ItemStack st = p.getInventory().getItem(i);
				if (st.is(ItemTags.PICKAXES) && st.getDestroySpeed(digState) > bestSpeed) {
					bestSpeed = st.getDestroySpeed(digState);
					best = i;
				}
			}
			if (best >= 0 && p.getInventory().getSelectedSlot() != best) {
				p.getInventory().setSelectedSlot(best);
			}
		}
		CombatUtil.turn(p, CombatUtil.rotationsTo(p.getEyePosition(), Vec3.atCenterOf(dig)), 30f);
		mc.gameMode.continueDestroyBlock(dig, d.getOpposite());
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		status = "Digging";
		if (s2.getBlock() instanceof FallingBlock) {
			status = "Digging (falling block)";
		}
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
