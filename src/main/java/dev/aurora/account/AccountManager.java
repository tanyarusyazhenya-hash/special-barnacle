package dev.aurora.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Offline account switcher: changes the name (and the matching offline UUID) the game uses. This only
 * works on offline-mode servers and singleplayer. Premium servers check your real login and will
 * refuse these names.
 */
public final class AccountManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<String> NAMES = new ArrayList<>();
	private static String current;
	private static String original;

	private AccountManager() {}

	public static void init() {
		Minecraft mc = Minecraft.getInstance();
		original = mc.getUser().getName();
		load();
		if (current != null && !current.equals(original)) {
			use(current);
		}
	}

	public static List<String> names() {
		return NAMES;
	}

	public static String currentName() {
		return Minecraft.getInstance().getUser().getName();
	}

	public static String originalName() {
		return original;
	}

	public static void add(String name) {
		name = clean(name);
		if (name.length() >= 3 && !NAMES.contains(name)) {
			NAMES.add(name);
			save();
		}
	}

	public static void remove(String name) {
		NAMES.remove(name);
		save();
	}

	public static String randomName() {
		String[] a = {"Swift", "Dark", "Frost", "Neon", "Pixel", "Shadow", "Iron", "Nova", "Ghost", "Lunar"};
		String[] b = {"Fox", "Wolf", "Hawk", "Byte", "Blade", "Moth", "Raven", "Orbit", "Cat", "Drake"};
		return a[(int) (Math.random() * a.length)] + b[(int) (Math.random() * b.length)]
			+ (int) (Math.random() * 90 + 10);
	}

	/** Switches the session to an offline account with this name. Returns false if it failed. */
	public static boolean use(String name) {
		name = clean(name);
		if (name.length() < 3) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		try {
			UUID uuid = UUIDUtil.createOfflinePlayerUUID(name);
			User user = new User(name, uuid, "0", Optional.empty(), Optional.empty());
			for (Field f : Minecraft.class.getDeclaredFields()) {
				if (f.getType() == User.class) {
					f.setAccessible(true);
					f.set(mc, user);
					current = name;
					save();
					return true;
				}
			}
		} catch (Throwable t) {
			System.err.println("[Aurora] Account switch failed: " + t);
		}
		return false;
	}

	private static String clean(String name) {
		StringBuilder sb = new StringBuilder();
		for (char c : name.trim().toCharArray()) {
			if (Character.isLetterOrDigit(c) || c == '_') {
				sb.append(c);
			}
		}
		return sb.length() > 16 ? sb.substring(0, 16) : sb.toString();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("aurora_accounts.json");
	}

	private static void save() {
		JsonObject obj = new JsonObject();
		JsonArray arr = new JsonArray();
		NAMES.forEach(arr::add);
		obj.add("accounts", arr);
		if (current != null) {
			obj.addProperty("current", current);
		}
		try {
			Files.writeString(file(), GSON.toJson(obj));
		} catch (IOException e) {
			System.err.println("[Aurora] Failed to save accounts: " + e.getMessage());
		}
	}

	private static void load() {
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
			for (JsonElement e : obj.getAsJsonArray("accounts")) {
				NAMES.add(e.getAsString());
			}
			if (obj.has("current")) {
				current = obj.get("current").getAsString();
			}
		} catch (Exception e) {
			System.err.println("[Aurora] Failed to load accounts: " + e.getMessage());
		}
	}
}
