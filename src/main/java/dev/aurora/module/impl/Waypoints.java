package dev.aurora.module.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.render.TracerSource;
import dev.aurora.render.TracerTarget;
import dev.aurora.setting.ActionSetting;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.NumberSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers spawners and stashes you found and keeps them across restarts.
 * Auto-detection needs SpawnerFinder / StorageFinder to be enabled, since they supply the targets.
 */
public class Waypoints extends Module implements TracerSource {
	public record Waypoint(String type, String server, int x, int y, int z) {
		public int color() {
			return type.equals("Spawner") ? 0xEF4444 : 0xF5B83D;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public final BoolSetting autoSpawners = add(new BoolSetting("Auto Spawners", true));
	public final BoolSetting autoStashes = add(new BoolSetting("Auto Stashes", true));
	public final NumberSetting stashMin = add(new NumberSetting("Stash Min Containers", 4, 2, 20, 1));
	public final BoolSetting notify = add(new BoolSetting("Chat Notify", true));
	public final ActionSetting clear = add(new ActionSetting("Clear Waypoints", this::clearAll));

	private final List<Waypoint> list = new ArrayList<>();
	private int cooldown;

	public Waypoints() {
		super("Waypoints", "Remembers spawners and stashes you find", Category.WORLD);
		stashMin.visibleWhen(() -> autoStashes.value);
		load();
	}

	@Override
	public String info() {
		return Integer.toString(list.size());
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null || --cooldown > 0) {
			return;
		}
		cooldown = 40;
		String server = serverKey(mc);

		if (autoSpawners.value && ModuleManager.spawnerFinder.isEnabled()) {
			List<TracerTarget> found = new ArrayList<>();
			ModuleManager.spawnerFinder.tracerTargets(found);
			for (TracerTarget t : found) {
				addIfNew(mc, "Spawner", server, (int) Math.floor(t.pos().x), (int) Math.floor(t.pos().y),
					(int) Math.floor(t.pos().z), 2.0);
			}
		}

		if (autoStashes.value && ModuleManager.storageFinder.isEnabled()) {
			List<TracerTarget> found = new ArrayList<>();
			ModuleManager.storageFinder.tracerTargets(found);
			Map<Long, List<Vec3>> byChunk = new HashMap<>();
			for (TracerTarget t : found) {
				long key = ChunkPos.asLong((int) Math.floor(t.pos().x) >> 4, (int) Math.floor(t.pos().z) >> 4);
				byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(t.pos());
			}
			for (List<Vec3> group : byChunk.values()) {
				if (group.size() < stashMin.i()) {
					continue;
				}
				double sx = 0, sy = 0, sz = 0;
				for (Vec3 p : group) {
					sx += p.x;
					sy += p.y;
					sz += p.z;
				}
				int n = group.size();
				addIfNew(mc, "Stash", server, (int) Math.floor(sx / n), (int) Math.floor(sy / n),
					(int) Math.floor(sz / n), 32.0);
			}
		}
	}

	private void addIfNew(Minecraft mc, String type, String server, int x, int y, int z, double radius) {
		for (Waypoint w : list) {
			if (!w.type().equals(type) || !w.server().equals(server)) {
				continue;
			}
			double dx = w.x() - x, dy = w.y() - y, dz = w.z() - z;
			if (dx * dx + dy * dy + dz * dz <= radius * radius) {
				return;
			}
		}
		list.add(new Waypoint(type, server, x, y, z));
		save();
		if (notify.value && mc.player != null) {
			mc.player.displayClientMessage(Component.literal(
				"§d[Aurora]§r Waypoint saved: " + type + " at " + x + " " + y + " " + z), false);
		}
	}

	/** Nearest waypoints on the current server, closest first. */
	public List<Waypoint> nearest(Minecraft mc, int count) {
		if (mc.player == null) {
			return List.of();
		}
		String server = serverKey(mc);
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : list) {
			if (w.server().equals(server)) {
				out.add(w);
			}
		}
		out.sort(Comparator.comparingDouble(w -> distSq(w, px, py, pz)));
		return out.size() > count ? new ArrayList<>(out.subList(0, count)) : out;
	}

	public static double distSq(Waypoint w, double px, double py, double pz) {
		double dx = w.x() + 0.5 - px, dy = w.y() + 0.5 - py, dz = w.z() + 0.5 - pz;
		return dx * dx + dy * dy + dz * dz;
	}

	@Override
	public void tracerTargets(List<TracerTarget> out) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		String server = serverKey(mc);
		for (Waypoint w : list) {
			if (w.server().equals(server)) {
				out.add(new TracerTarget(new Vec3(w.x() + 0.5, w.y() + 0.5, w.z() + 0.5), w.color()));
			}
		}
	}

	private void clearAll() {
		Minecraft mc = Minecraft.getInstance();
		String server = mc.player == null ? null : serverKey(mc);
		list.removeIf(w -> server == null || w.server().equals(server));
		save();
	}

	private static String serverKey(Minecraft mc) {
		ServerData data = mc.getCurrentServer();
		return data != null ? data.ip : "singleplayer";
	}

	// ------------------------------------------------------------------ persistence

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("aurora_waypoints.json");
	}

	private void save() {
		JsonArray array = new JsonArray();
		for (Waypoint w : list) {
			JsonObject o = new JsonObject();
			o.addProperty("type", w.type());
			o.addProperty("server", w.server());
			o.addProperty("x", w.x());
			o.addProperty("y", w.y());
			o.addProperty("z", w.z());
			array.add(o);
		}
		try {
			Files.writeString(file(), GSON.toJson(array));
		} catch (IOException e) {
			System.err.println("[Aurora] Failed to save waypoints: " + e.getMessage());
		}
	}

	private void load() {
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path));
			if (!parsed.isJsonArray()) {
				return;
			}
			for (JsonElement e : parsed.getAsJsonArray()) {
				JsonObject o = e.getAsJsonObject();
				list.add(new Waypoint(o.get("type").getAsString(), o.get("server").getAsString(),
					o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
			}
		} catch (Exception e) {
			System.err.println("[Aurora] Failed to load waypoints: " + e.getMessage());
		}
	}
}
