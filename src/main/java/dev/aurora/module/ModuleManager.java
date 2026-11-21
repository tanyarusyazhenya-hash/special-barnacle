package dev.aurora.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aurora.module.impl.HudModule;
import dev.aurora.module.impl.InterfaceModule;
import dev.aurora.module.impl.PlayerESP;
import dev.aurora.module.impl.SpawnerFinder;
import dev.aurora.module.impl.StorageFinder;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspProvider;
import dev.aurora.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<Module> MODULES = new ArrayList<>();

	public static StorageFinder storageFinder;
	public static SpawnerFinder spawnerFinder;
	public static PlayerESP playerEsp;
	public static HudModule hud;
	public static InterfaceModule ui;

	private ModuleManager() {}

	public static void init() {
		storageFinder = new StorageFinder();
		spawnerFinder = new SpawnerFinder();
		playerEsp = new PlayerESP();
		hud = new HudModule();
		ui = new InterfaceModule();
		MODULES.add(storageFinder);
		MODULES.add(spawnerFinder);
		MODULES.add(playerEsp);
		MODULES.add(hud);
		MODULES.add(ui);
	}

	public static List<Module> all() {
		return MODULES;
	}

	public static List<Module> byCategory(Category category) {
		List<Module> out = new ArrayList<>();
		for (Module m : MODULES) {
			if (m.category == category) {
				out.add(m);
			}
		}
		return out;
	}

	public static void tick(Minecraft mc) {
		for (Module m : MODULES) {
			if (m.isEnabled()) {
				m.onTick(mc);
			}
		}
	}

	public static void collectEsp(List<EspBox> out) {
		for (Module m : MODULES) {
			if (m.isEnabled() && m instanceof EspProvider provider) {
				provider.collect(out);
			}
		}
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("aurora.json");
	}

	public static void save() {
		JsonObject root = new JsonObject();
		for (Module m : MODULES) {
			JsonObject mo = new JsonObject();
			mo.addProperty("enabled", m.isEnabled());
			JsonObject so = new JsonObject();
			for (Setting s : m.settings) {
				so.add(s.name, s.save());
			}
			mo.add("settings", so);
			root.add(m.name, mo);
		}
		try {
			Files.writeString(configPath(), GSON.toJson(root));
		} catch (IOException e) {
			System.err.println("[Aurora] Failed to save config: " + e.getMessage());
		}
	}

	public static void load() {
		Path path = configPath();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path));
			if (!parsed.isJsonObject()) {
				return;
			}
			JsonObject root = parsed.getAsJsonObject();
			for (Module m : MODULES) {
				if (!root.has(m.name)) {
					continue;
				}
				JsonObject mo = root.getAsJsonObject(m.name);
				if (mo.has("enabled")) {
					m.setEnabled(mo.get("enabled").getAsBoolean());
				}
				if (mo.has("settings")) {
					JsonObject so = mo.getAsJsonObject("settings");
					for (Setting s : m.settings) {
						if (so.has(s.name)) {
							s.load(so.get(s.name));
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Aurora] Failed to load config: " + e.getMessage());
		}
	}
}
