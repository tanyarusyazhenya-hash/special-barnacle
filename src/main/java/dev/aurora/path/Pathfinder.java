package dev.aurora.path;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A small A* path finder over the blocks the client has loaded (a compact stand-in for what Baritone
 * does). The path is a list of feet positions. Moves: walk, step up one block, drop up to three blocks,
 * dig straight down one block, and, if digging is allowed, mine through solid blocks. It avoids
 * liquids, fire, cactus and similar, never mines containers or unbreakable blocks, and never mines
 * next to lava. Cells in chunks that are not loaded count as impassable, so for far goals the caller
 * asks for a path toward a nearer point and asks again as new chunks arrive.
 */
public final class Pathfinder {
	private static final double MINE_COST = 6.0;

	private final ClientLevel level;
	private final boolean dig;

	private static final class Node {
		final BlockPos pos;
		final double g;
		final double f;
		final Node parent;

		Node(BlockPos pos, double g, double f, Node parent) {
			this.pos = pos;
			this.g = g;
			this.f = f;
			this.parent = parent;
		}
	}

	private Pathfinder(ClientLevel level, boolean dig) {
		this.level = level;
		this.dig = dig;
	}

	/** Returns the path (including the start), a partial path toward the goal, or an empty list. */
	public static List<BlockPos> find(ClientLevel level, BlockPos start, BlockPos goal, boolean dig, int maxNodes) {
		return new Pathfinder(level, dig).run(start, goal, maxNodes);
	}

	// ------------------------------------------------------------------ search

	private double h(BlockPos p, BlockPos goal) {
		double dx = p.getX() - goal.getX();
		double dz = p.getZ() - goal.getZ();
		return Math.sqrt(dx * dx + dz * dz) + 0.4 * Math.abs(p.getY() - goal.getY());
	}

	private boolean reachedGoal(BlockPos p, BlockPos goal) {
		return Math.abs(p.getX() - goal.getX()) <= 1 && Math.abs(p.getZ() - goal.getZ()) <= 1;
	}

	private List<BlockPos> run(BlockPos start, BlockPos goal, int maxNodes) {
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
		Map<Long, Node> best = new HashMap<>();
		Node s = new Node(start, 0, h(start, goal), null);
		open.add(s);
		best.put(start.asLong(), s);
		Node closest = s;
		double closestH = h(start, goal);
		int expanded = 0;

		while (!open.isEmpty() && expanded < maxNodes) {
			Node n = open.poll();
			if (best.get(n.pos.asLong()) != n) {
				continue; // a cheaper way to this block was found later
			}
			expanded++;
			if (reachedGoal(n.pos, goal)) {
				return build(n);
			}
			double hn = h(n.pos, goal);
			if (hn < closestH) {
				closestH = hn;
				closest = n;
			}
			expand(n, goal, open, best);
		}
		if (closest != s && closestH < h(start, goal) - 3.0) {
			return build(closest);
		}
		return List.of();
	}

	private void expand(Node n, BlockPos goal, PriorityQueue<Node> open, Map<Long, Node> best) {
		BlockPos pos = n.pos;
		int y = pos.getY();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos nf = pos.relative(d);
			BlockPos nh = nf.above();
			double cf = clear(nf);
			double ch = clear(nh);
			if (cf >= 0 && ch >= 0) {
				BlockPos below = nf.below();
				if (solidFloor(below)) {
					push(n, nf, 1.0 + cf + ch, goal, open, best);
				} else if (clear(below) == 0) {
					for (int drop = 1; drop <= 3; drop++) {
						BlockPos land = nf.below(drop);
						if (clear(land) != 0) {
							break;
						}
						if (solidFloor(land.below())) {
							push(n, land, 1.5 + 0.5 * drop + cf + ch, goal, open, best);
							break;
						}
					}
				}
			}
			// step up one block onto nf
			BlockPos uf = nf.above();
			BlockPos uh = uf.above();
			double cu = clear(uf);
			double cuh = clear(uh);
			double head = clear(pos.above(2));
			if (cu >= 0 && cuh >= 0 && head >= 0 && solidFloor(nf)) {
				push(n, uf, 1.7 + cu + cuh + head, goal, open, best);
			}
		}
		// dig one block straight down
		BlockPos df = pos.below();
		double cd = clear(df);
		if (cd >= 0 && solidFloor(df.below())) {
			push(n, df, 2.0 + cd, goal, open, best);
		}
	}

	private void push(Node from, BlockPos to, double cost, BlockPos goal, PriorityQueue<Node> open,
					  Map<Long, Node> best) {
		double g = from.g + cost;
		Node existing = best.get(to.asLong());
		if (existing != null && existing.g <= g) {
			return;
		}
		Node nn = new Node(to, g, g + h(to, goal), from);
		best.put(to.asLong(), nn);
		open.add(nn);
	}

	private List<BlockPos> build(Node end) {
		List<BlockPos> out = new ArrayList<>();
		for (Node n = end; n != null; n = n.parent) {
			out.add(n.pos);
		}
		Collections.reverse(out);
		return out;
	}

	// ------------------------------------------------------------------ cells

	private boolean loaded(BlockPos p) {
		return level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4);
	}

	/** 0 = open space, MINE_COST = solid but can be mined, -1 = cannot be used. */
	private double clear(BlockPos p) {
		if (!loaded(p) || p.getY() < level.getMinY() || p.getY() >= level.getMinY() + level.getHeight()) {
			return -1;
		}
		BlockState s = level.getBlockState(p);
		if (!s.getFluidState().isEmpty()) {
			return -1;
		}
		if (s.getCollisionShape(level, p).isEmpty()) {
			return hazard(s) ? -1 : 0;
		}
		if (!dig || s.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null) {
			return -1;
		}
		if (lavaNear(p)) {
			return -1;
		}
		return MINE_COST;
	}

	public static boolean hazard(BlockState s) {
		String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return id.contains("fire") || id.contains("cactus") || id.contains("berry_bush") || id.contains("web")
			|| id.contains("magma") || id.contains("campfire") || id.contains("powder_snow")
			|| id.contains("wither_rose") || id.contains("lava");
	}

	private boolean solidFloor(BlockPos p) {
		if (!loaded(p)) {
			return false;
		}
		BlockState s = level.getBlockState(p);
		return s.getFluidState().isEmpty() && !s.getCollisionShape(level, p).isEmpty() && !hazard(s);
	}

	private boolean lavaNear(BlockPos p) {
		for (Direction d : Direction.values()) {
			BlockPos n = p.relative(d);
			if (loaded(n) && level.getBlockState(n).getFluidState().is(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}
}
