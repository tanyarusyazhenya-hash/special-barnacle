package dev.aurora.macro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Key to command/chat-message macros, saved to config/aurora_macros.json. */
public final class MacroManager {
	public static final class Macro {
		public int key = -1;
		public String text = "";
		public boolean wasDown;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final List<Macro> MACROS = new ArrayList<>();
	private static boolean loaded;

	private MacroManager() {}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonArray arr = JsonParser.parseString(Files.readString(path)).getAsJsonArray();
			for (JsonElement e : arr) {
				JsonObject o = e.getAsJsonObject();
				Macro m = new Macro();
				m.key = o.get("key").getAsInt();
				m.text = o.get("text").getAsString();
				MACROS.add(m);
			}
		} catch (Exception e) {
			System.err.println("[Aurora] Failed to load macros: " + e.getMessage());
		}
	}

	public static void save() {
		JsonArray arr = new JsonArray();
		for (Macro m : MACROS) {
			JsonObject o = new JsonObject();
			o.addProperty("key", m.key);
			o.addProperty("text", m.text);
			arr.add(o);
		}
		try {
			Files.writeString(file(), GSON.toJson(arr));
		} catch (IOException e) {
			System.err.println("[Aurora] Failed to save macros: " + e.getMessage());
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("aurora_macros.json");
	}
}
