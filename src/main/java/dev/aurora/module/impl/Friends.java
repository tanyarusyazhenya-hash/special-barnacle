package dev.aurora.module.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

	private final Set<String> names = new TreeSet<>();

	public Friends() {
		super("Friends", "Friends are recolored or hidden in ESP, nametags and tracers", Category.UTILITY);
		color.visibleWhen(() -> treatment.is("Recolored"));
		tag.visibleWhen(() -> treatment.is("Recolored"));
		setEnabled(true);
		load();
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
		return isEnabled() && names.contains(p.getName().getString().toLowerCase(Locale.ROOT));
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
