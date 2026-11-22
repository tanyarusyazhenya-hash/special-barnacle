package dev.aurora.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.module.impl.AutoReconnect;
import dev.aurora.module.impl.AutoSprint;
import dev.aurora.module.impl.AutoTotem;
import dev.aurora.module.impl.BlockFinder;
import dev.aurora.module.impl.EntityESP;
import dev.aurora.module.impl.Freecam;
import dev.aurora.module.impl.Friends;
import dev.aurora.module.impl.Fullbright;
import dev.aurora.module.impl.HudModule;
import dev.aurora.module.impl.InterfaceModule;
import dev.aurora.module.impl.Nametags;
import dev.aurora.module.impl.PlayerESP;
import dev.aurora.module.impl.SpawnerFinder;
import dev.aurora.module.impl.StorageFinder;
import dev.aurora.module.impl.Tracers;
import dev.aurora.module.impl.Waypoints;
import dev.aurora.module.impl.Zoom;
import dev.aurora.render.EspBox;
import dev.aurora.render.EspLine;
import dev.aurora.render.EspProvider;
import dev.aurora.render.LineProvider;
import dev.aurora.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

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
	public static EntityESP entityEsp;
	public static BlockFinder blockFinder;
	public static Tracers tracers;
	public static Nametags nametags;
	public static Waypoints waypoints;
	public static Fullbright fullbright;
	public static Zoom zoom;
	public static Freecam freecam;
	public static AutoSprint autoSprint;
	public static AutoTotem autoTotem;
	public static AutoReconnect autoReconnect;
	public static Friends friends;
	public static HudModule hud;
	public static InterfaceModule ui;

	private ModuleManager() {}

	public static void init() {
		storageFinder = new StorageFinder();
		spawnerFinder = new SpawnerFinder();
		playerEsp = new PlayerESP();
		entityEsp = new EntityESP();
		blockFinder = new BlockFinder();
		tracers = new Tracers();
		nametags = new Nametags();
		waypoints = new Waypoints();
		fullbright = new Fullbright();
		zoom = new Zoom();
		freecam = new Freecam();
		autoSprint = new AutoSprint();
		autoTotem = new AutoTotem();
		autoReconnect = new AutoReconnect();
		friends = new Friends();
		hud = new HudModule();
		ui = new InterfaceModule();

		MODULES.add(storageFinder);
		MODULES.add(spawnerFinder);
		MODULES.add(playerEsp);
		MODULES.add(entityEsp);
		MODULES.add(blockFinder);
		MODULES.add(tracers);
		MODULES.add(nametags);
		MODULES.add(waypoints);
		MODULES.add(fullbright);
		MODULES.add(zoom);
		MODULES.add(freecam);
		MODULES.add(autoSprint);
		MODULES.add(autoTotem);
		MODULES.add(autoReconnect);
		MODULES.add(friends);
		MODULES.add(hud);
		MODULES.add(ui);

		// Every toggleable module gets a keybind row at the bottom of its settings.
		for (Module m : MODULES) {
			if (m.hasToggle()) {
				m.settings.add(m.bind);
			}
		}
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

	/** Runs every rendered frame, before the world is drawn. */
	public static void frame(Minecraft mc) {
		zoom.frame(mc);
		freecam.frame(mc);
	}

	public static void tick(Minecraft mc) {
		zoom.installScrollHook();
		zoom.safetyCheck(mc);

		if (mc.screen == null) {
			for (Module m : MODULES) {
				if (!m.hasToggle() || m.bind.key < 0) {
					continue;
				}
				boolean down = InputConstants.isKeyDown(mc.getWindow(), m.bind.key);
				if (m.holdToActivate()) {
					if (down != m.isEnabled()) {
						m.setEnabled(down);
					}
				} else if (down && !m.keyWasDown) {
					m.toggle();
				}
				m.keyWasDown = down;
			}
		} else {
			// opening a screen while a hold-bind is down releases it
			for (Module m : MODULES) {
				if (m.hasToggle() && m.holdToActivate() && m.isEnabled()) {
					m.setEnabled(false);
				}
				m.keyWasDown = false;
			}
		}
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

	public static void collectLines(List<EspLine> out, Vec3 start) {
		for (Module m : MODULES) {
			if (m.isEnabled() && m instanceof LineProvider provider) {
				provider.collectLines(out, start);
			}
		}
	}

	/** Undo temporary side effects (Zoom changes the FOV option) before the game closes. */
	public static void shutdown() {
		save();
		if (freecam != null) {
			freecam.setEnabled(false);
		}
		if (zoom != null) {
			zoom.setEnabled(false);
			zoom.restoreNow(Minecraft.getInstance());
		}
	}

	// ------------------------------------------------------------------ config + profiles

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("aurora.json");
	}

	private static Path profilePath(String id) {
		return FabricLoader.getInstance().getConfigDir().resolve("aurora_profile_" + id + ".json");
	}

	public static void save() {
		saveTo(configPath());
	}

	public static void load() {
		loadFrom(configPath());
	}

	public static void saveProfile(String id) {
		saveTo(profilePath(id));
		notice("Profile " + id + " saved");
	}

	public static void loadProfile(String id) {
		if (!Files.exists(profilePath(id))) {
			notice("Profile " + id + " is empty");
			return;
		}
		loadFrom(profilePath(id));
		save();
		notice("Profile " + id + " loaded");
	}

	private static void notice(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal("§d[Aurora]§r " + text), true);
		}
	}

	private static void saveTo(Path path) {
		JsonObject root = new JsonObject();
		for (Module m : MODULES) {
			JsonObject mo = new JsonObject();
			mo.addProperty("enabled", m.persistEnabled() && m.isEnabled());
			JsonObject so = new JsonObject();
			for (Setting s : m.settings) {
				if (s.persistent()) {
					so.add(s.name, s.save());
				}
			}
			mo.add("settings", so);
			root.add(m.name, mo);
		}
		try {
			Files.writeString(path, GSON.toJson(root));
		} catch (IOException e) {
			System.err.println("[Aurora] Failed to save " + path + ": " + e.getMessage());
		}
	}

	private static void loadFrom(Path path) {
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
						if (s.persistent() && so.has(s.name)) {
							s.load(so.get(s.name));
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Aurora] Failed to load " + path + ": " + e.getMessage());
		}
	}
}
