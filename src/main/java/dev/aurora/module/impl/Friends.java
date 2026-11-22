package dev.aurora.module.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.ActionSetting;
import dev.aurora.setting.BoolSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;

/**
 * Friend list. Friends can be recolored or hidden in PlayerESP, Nametags and Tracers.
 * Commands: /friend add <name>, /friend remove <name>, /friend list, /friend clear
 */
public class Friends extends Module {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public final ModeSetting treatment = add(new ModeSetting("Friends Are", "Recolored", "Recolored", "Hidden"));
	public final ColorSetting color = add(new ColorSetting("Friend Color", 0x3B82F6));
	public final BoolSetting tag = add(new BoolSetting("Mark In Nametags", true));
	public final ActionSetting addNearest = add(new ActionSetting("Add Nearest Player", this::addNearest));
	public final ActionSetting clearAll = add(new ActionSetting("Clear Friends", this::clear));

	public final ActionSetting openGroups = add(new ActionSetting("Open Groups Window",
		() -> Minecraft.getInstance().setScreen(new dev.aurora.gui.GroupsScreen(Minecraft.getInstance().screen))));
	public final ActionSetting addNearestToTeam = add(new ActionSetting("Add Nearest To Team", this::addNearestToTeam));

	private final Set<String> names = new TreeSet<>();
	/** Named groups (local only). Members count as friends everywhere: never attacked, recolored in ESP. */
	private final Map<String, Set<String>> groups = new TreeMap<>();

	public Friends() {
		super("Friends", "Friends are recolored or hidden in ESP, nametags and tracers", Category.UTILITY);
		color.visibleWhen(() -> treatment.is("Recolored"));
		tag.visibleWhen(() -> treatment.is("Recolored"));
		setEnabled(true);
		load();
		loadGroups();
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	public String info() {
		return Integer.toString(names.size());
	}

	/** True for friends while the module is on. */
	public boolean isFriend(Player p) {
		if (!isEnabled()) {
			return false;
		}
		String n = p.getName().getString().toLowerCase(Locale.ROOT);
		if (names.contains(n)) {
			return true;
		}
		for (Set<String> g : groups.values()) {
			if (g.contains(n)) {
				return true;
			}
		}
		return false;
	}

	public java.util.List<String> groupNames() {
		return new java.util.ArrayList<>(groups.keySet());
	}

	public java.util.List<String> groupMembers(String group) {
		Set<String> g = groups.get(group);
		return g == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(g);
	}

	public void groupCreate(String group) {
		groups.computeIfAbsent(group.toLowerCase(Locale.ROOT), k -> new TreeSet<>());
		saveGroups();
	}

	public void groupDelete(String group) {
		groups.remove(group.toLowerCase(Locale.ROOT));
		saveGroups();
	}

	public boolean groupAdd(String group, String player) {
		boolean added = groups.computeIfAbsent(group.toLowerCase(Locale.ROOT), k -> new TreeSet<>())
			.add(player.toLowerCase(Locale.ROOT));
		saveGroups();
		return added;
	}

	public boolean groupRemove(String group, String player) {
		Set<String> g = groups.get(group.toLowerCase(Locale.ROOT));
		boolean removed = g != null && g.remove(player.toLowerCase(Locale.ROOT));
		saveGroups();
		return removed;
	}

	private void addNearestToTeam() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		AbstractClientPlayer best = dev.aurora.util.CombatUtil.nearestEnemyPlayer(mc, 64);
		if (best == null) {
			say("No players nearby");
			return;
		}
		String n = best.getName().getString();
		say(groupAdd("team", n) ? "Invited " + n + " to group team" : n + " is already in group team");
	}

	/** True if friends should be left out of ESP, nametags and tracers completely. */
	public boolean hideFriends() {
		return treatment.is("Hidden");
	}

	// ------------------------------------------------------------------ editing

	public boolean add(String name) {
		boolean added = names.add(name.toLowerCase(Locale.ROOT));
		save();
		return added;
	}

	public boolean remove(String name) {
		boolean removed = names.remove(name.toLowerCase(Locale.ROOT));
		save();
		return removed;
	}

	private void clear() {
		names.clear();
		save();
		say("Friends cleared");
	}

	private void addNearest() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		AbstractClientPlayer best = null;
		double bestDist = Double.MAX_VALUE;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player) {
				continue;
			}
			double d = mc.player.distanceToSqr(p);
			if (d < bestDist) {
				bestDist = d;
				best = p;
			}
		}
		if (best == null) {
			say("No players nearby");
			return;
		}
		String n = best.getName().getString();
		say(add(n) ? "Added " + n + " as a friend" : n + " is already a friend");
	}

	private static void say(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal("§d[Aurora]§r " + text), false);
		}
	}

	// ------------------------------------------------------------------ commands

	public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommandManager.literal("group")
			.then(ClientCommandManager.literal("invite")
				.then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(ctx -> {
					String n = StringArgumentType.getString(ctx, "player");
					say(groupAdd("team", n) ? "Invited " + n + " to group team" : n + " is already in group team");
					return 1;
				})))
			.then(ClientCommandManager.literal("add")
				.then(ClientCommandManager.argument("group", StringArgumentType.word())
					.then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(ctx -> {
						String g = StringArgumentType.getString(ctx, "group");
						String n = StringArgumentType.getString(ctx, "player");
						say(groupAdd(g, n) ? "Added " + n + " to " + g : n + " is already in " + g);
						return 1;
					}))))
			.then(ClientCommandManager.literal("remove")
				.then(ClientCommandManager.argument("group", StringArgumentType.word())
					.then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(ctx -> {
						String g = StringArgumentType.getString(ctx, "group");
						String n = StringArgumentType.getString(ctx, "player");
						say(groupRemove(g, n) ? "Removed " + n + " from " + g : n + " is not in " + g);
						return 1;
					}))))
			.then(ClientCommandManager.literal("delete")
				.then(ClientCommandManager.argument("group", StringArgumentType.word()).executes(ctx -> {
					String g = StringArgumentType.getString(ctx, "group").toLowerCase(Locale.ROOT);
					say(groups.remove(g) != null ? "Deleted group " + g : "No group " + g);
					saveGroups();
					return 1;
				})))
			.then(ClientCommandManager.literal("list").executes(ctx -> {
				if (groups.isEmpty()) {
					say("No groups yet. /group invite <player> or /group add <group> <player>");
				}
				groups.forEach((k, v) -> say(k + ": " + String.join(", ", v)));
				return 1;
			})));
		dispatcher.register(ClientCommandManager.literal("friend")
			.then(ClientCommandManager.literal("add")
				.then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
					String n = StringArgumentType.getString(ctx, "name");
					say(add(n) ? "Added " + n + " as a friend" : n + " is already a friend");
					return 1;
				})))
			.then(ClientCommandManager.literal("remove")
				.then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
					String n = StringArgumentType.getString(ctx, "name");
					say(remove(n) ? "Removed " + n : n + " is not a friend");
					return 1;
				})))
			.then(ClientCommandManager.literal("list").executes(ctx -> {
				say(names.isEmpty() ? "No friends yet" : "Friends: " + String.join(", ", names));
				return 1;
			}))
			.then(ClientCommandManager.literal("clear").executes(ctx -> {
				clear();
				return 1;
			})));
	}

	private static Path groupsFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("aurora_groups.json");
	}

	private void saveGroups() {
		JsonObject obj = new JsonObject();
		for (Map.Entry<String, Set<String>> e : groups.entrySet()) {
			JsonArray arr = new JsonArray();
			e.getValue().forEach(arr::add);
			obj.add(e.getKey(), arr);
		}
		try {
			Files.writeString(groupsFile(), GSON.toJson(obj));
		} catch (IOException ex) {
			System.err.println("[Aurora] Failed to save groups: " + ex.getMessage());
		}
	}

	private void loadGroups() {
		Path path = groupsFile();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path));
			if (parsed.isJsonObject()) {
				for (Map.Entry<String, JsonElement> e : parsed.getAsJsonObject().entrySet()) {
					Set<String> set = new TreeSet<>();
					for (JsonElement m : e.getValue().getAsJsonArray()) {
						set.add(m.getAsString().toLowerCase(Locale.ROOT));
					}
					groups.put(e.getKey(), set);
				}
			}
		} catch (Exception ex) {
			System.err.println("[Aurora] Failed to load groups: " + ex.getMessage());
		}
	}

	// ------------------------------------------------------------------ persistence

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("aurora_friends.json");
	}

	private void save() {
		JsonArray array = new JsonArray();
		for (String n : names) {
			array.add(n);
		}
		try {
			Files.writeString(file(), GSON.toJson(array));
		} catch (IOException e) {
			System.err.println("[Aurora] Failed to save friends: " + e.getMessage());
		}
	}

	private void load() {
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path));
			if (parsed.isJsonArray()) {
				for (JsonElement e : parsed.getAsJsonArray()) {
					names.add(e.getAsString().toLowerCase(Locale.ROOT));
				}
			}
		} catch (Exception e) {
			System.err.println("[Aurora] Failed to load friends: " + e.getMessage());
		}
	}
}
