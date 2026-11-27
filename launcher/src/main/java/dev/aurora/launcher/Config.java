package dev.aurora.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Launcher settings, saved as launcher.json in the launcher folder. */
public final class Config {
	public List<String> nicks = new ArrayList<>(List.of("Player"));
	public String nick = "Player";
	public int ramMb = 4096;
	public String jvmArgs = "";
	public String javaPath = "";
	public String gameDir = "";
	public int width = 1280;
	public int height = 720;
	public boolean fullscreen;
	public String afterLaunch = "Hide launcher";
	public String accent = "FF7A1A";
	public boolean installAurora = true;

	public static Path home() {
		String appData = System.getenv("APPDATA");
		if (appData != null && !appData.isEmpty()) {
			return Path.of(appData, ".aurora-launcher");
		}
		return Path.of(System.getProperty("user.home"), ".aurora-launcher");
	}

	public Path gameDirPath() {
		return gameDir == null || gameDir.isBlank() ? home().resolve("game") : Path.of(gameDir);
	}

	public static Config load() {
		Config c = new Config();
		try {
			Path f = home().resolve("launcher.json");
			if (Files.exists(f)) {
				Map<String, Object> m = Json.obj(Json.parse(Files.readString(f)));
				if (m != null) {
					List<Object> nl = Json.arr(m.get("nicks"));
					if (nl != null) {
						c.nicks = new ArrayList<>();
						for (Object o : nl) {
							if (o instanceof String s) {
								c.nicks.add(s);
							}
						}
					}
					c.nick = orDef(Json.str(m.get("nick")), c.nick);
					c.ramMb = (int) Json.num(m.get("ramMb"), c.ramMb);
					c.jvmArgs = orDef(Json.str(m.get("jvmArgs")), "");
					c.javaPath = orDef(Json.str(m.get("javaPath")), "");
					c.gameDir = orDef(Json.str(m.get("gameDir")), "");
					c.width = (int) Json.num(m.get("width"), c.width);
					c.height = (int) Json.num(m.get("height"), c.height);
					c.fullscreen = Boolean.TRUE.equals(m.get("fullscreen"));
					c.afterLaunch = orDef(Json.str(m.get("afterLaunch")), c.afterLaunch);
					c.accent = orDef(Json.str(m.get("accent")), c.accent);
					c.installAurora = !Boolean.FALSE.equals(m.get("installAurora"));
				}
			}
		} catch (Exception ignored) {
			// broken file: fall back to defaults
		}
		if (c.nicks.isEmpty()) {
			c.nicks.add("Player");
		}
		if (!c.nicks.contains(c.nick)) {
			c.nicks.add(c.nick);
		}
		return c;
	}

	private static String orDef(String v, String d) {
		return v == null ? d : v;
	}

	public void save() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("nicks", new ArrayList<Object>(nicks));
		m.put("nick", nick);
		m.put("ramMb", (long) ramMb);
		m.put("jvmArgs", jvmArgs);
		m.put("javaPath", javaPath);
		m.put("gameDir", gameDir);
		m.put("width", (long) width);
		m.put("height", (long) height);
		m.put("fullscreen", fullscreen);
		m.put("afterLaunch", afterLaunch);
		m.put("accent", accent);
		m.put("installAurora", installAurora);
		try {
			Files.createDirectories(home());
			Files.writeString(home().resolve("launcher.json"), Json.write(m));
		} catch (IOException ignored) {
			// cannot save: settings stay in memory
		}
	}
}
