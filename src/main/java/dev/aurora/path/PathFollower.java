package dev.aurora.path;

import dev.aurora.util.CombatUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks a path from Pathfinder by pressing your movement keys, jumping when the next block is higher
 * and mining any solid block that is in the way. Call tick() once per game tick.
 */
public final class PathFollower {
	public enum State { RUNNING, DONE, FAILED }

	private List<BlockPos> path = List.of();
	private int index;
	private int stillTicks;
	private Vec3 lastPos = Vec3.ZERO;
	private int checkTimer;
	public String failReason = "";
	public boolean mining;

	public void setPath(List<BlockPos> newPath) {
		path = newPath;
		index = 0;
		stillTicks = 0;
		checkTimer = 0;
		failReason = "";
	}

	public boolean hasPath() {
		return !path.isEmpty() && index < path.size();
	}

	public void clear() {
		path = List.of();
		index = 0;
		mining = false;
	}

	private boolean reached(LocalPlayer p, BlockPos n, double tolerance) {
		double dx = p.getX() - (n.getX() + 0.5);
		double dz = p.getZ() - (n.getZ() + 0.5);
		return dx * dx + dz * dz < tolerance && Math.abs(p.getY() - n.getY()) < 0.75;
	}

	/** Cells that must be free to go from a to b. */
	private List<BlockPos> cells(BlockPos a, BlockPos b) {
		List<BlockPos> out = new ArrayList<>();
		if (b.getY() < a.getY() && b.getX() == a.getX() && b.getZ() == a.getZ()) {
			out.add(b); // digging down: only the block we drop into
			return out;
		}
		if (b.getY() > a.getY()) {
			out.add(a.above(2)); // headroom for the jump
		}
		if (b.getY() < a.getY()) {
			for (int y = a.getY() + 1; y >= b.getY(); y--) {
				out.add(new BlockPos(b.getX(), y, b.getZ()));
			}
			return out;
		}
		out.add(b.above());
		out.add(b);
		return out;
	}

	public State tick(Minecraft mc, boolean allowDig, boolean autoTool, boolean sprint) {
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		mining = false;
		if (p == null || level == null || !hasPath()) {
			return State.DONE;
		}
		KeyMapping.setAll();

		while (index < path.size() && reached(p, path.get(index), 0.16)) {
			index++;
		}
		if (index + 1 < path.size() && reached(p, path.get(index + 1), 0.36)) {
			index++;
		}
		if (index >= path.size()) {
			return State.DONE;
		}
		BlockPos target = path.get(index);
		BlockPos prev = index > 0 ? path.get(index - 1) : p.blockPosition();

		for (BlockPos c : cells(prev, target)) {
			BlockState s = level.getBlockState(c);
			if (!s.getFluidState().isEmpty()) {
				return fail("Liquid in the way");
			}
			if (!s.getCollisionShape(level, c).isEmpty()) {
				if (!allowDig || s.getDestroySpeed(level, c) < 0 || level.getBlockEntity(c) != null) {
					return fail("Blocked");
				}
				for (Direction d : Direction.values()) {
					if (level.getBlockState(c.relative(d)).getFluidState().is(FluidTags.LAVA)) {
						return fail("Lava behind the block");
					}
				}
				mine(mc, p, c, s, autoTool);
				return State.RUNNING;
			}
		}

		// walk to the node
		double dx = target.getX() + 0.5 - p.getX();
		double dz = target.getZ() + 0.5 - p.getZ();
		double hd = Math.hypot(dx, dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float dyaw = Mth.wrapDegrees(yaw - p.getYRot());
		p.setYRot(p.getYRot() + Mth.clamp(dyaw, -40f, 40f));
		p.setXRot(Mth.lerp(0.3f, p.getXRot(), 0f));

		var o = mc.options;
		if (hd > 0.12 && Math.abs(dyaw) < 50f) {
			o.keyUp.setDown(true);
			if (sprint && Math.abs(target.getY() - p.getY()) < 0.5 && hd > 1.5) {
				o.keySprint.setDown(true);
			}
		}
		if (p.onGround() && (target.getY() > p.getY() + 0.1 || (p.horizontalCollision && hd > 0.5))) {
			o.keyJump.setDown(true);
		}

		// stuck check
		if (++checkTimer >= 40) {
			if (lastPos.distanceTo(p.position()) < 0.3) {
				return fail("Stuck");
			}
			lastPos = p.position();
			checkTimer = 0;
		}
		return State.RUNNING;
	}

	private void mine(Minecraft mc, LocalPlayer p, BlockPos pos, BlockState state, boolean autoTool) {
		mining = true;
		checkTimer = 0;
		lastPos = p.position();
		if (autoTool) {
			int best = -1;
			float bestSpeed = 1f;
			for (int i = 0; i < 9; i++) {
				ItemStack st = p.getInventory().getItem(i);
				float speed = st.getDestroySpeed(state);
				if (speed > bestSpeed) {
					bestSpeed = speed;
					best = i;
				}
			}
			if (best >= 0 && p.getInventory().getSelectedSlot() != best) {
				p.getInventory().setSelectedSlot(best);
			}
		}
		Vec3 center = Vec3.atCenterOf(pos);
		CombatUtil.turn(p, CombatUtil.rotationsTo(p.getEyePosition(), center), 40f);
		Direction face = Direction.getApproximateNearest(p.getEyePosition().subtract(center));
		mc.gameMode.continueDestroyBlock(pos, face);
		p.swing(InteractionHand.MAIN_HAND);
	}

	private State fail(String reason) {
		failReason = reason;
		KeyMapping.setAll();
		return State.FAILED;
	}
}
