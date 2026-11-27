package dev.aurora.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Builds the java command line from the version files and starts Minecraft. */
public final class Game {
	private Game() {}

	public static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	public static String javaExecutable(Config cfg) {
		if (cfg.javaPath != null && !cfg.javaPath.isBlank()) {
			return cfg.javaPath;
		}
		String exe = Rules.osName().equals("windows") ? "java.exe" : "java";
		return Path.of(System.getProperty("java.home"), "bin", exe).toString();
	}

	public static List<String> command(Config cfg, Installer.Prepared p, Path root) throws IOException {
		Path gameDir = cfg.gameDirPath();
		Files.createDirectories(gameDir);
		Path natives = root.resolve("natives");
		Files.createDirectories(natives);

		StringBuilder cp = new StringBuilder();
		for (Path lib : p.classpath()) {
			cp.append(lib).append(File.pathSeparator);
		}
		cp.append(p.clientJar());

		Map<String, String> vars = new HashMap<>();
		vars.put("auth_player_name", cfg.nick);
		vars.put("version_name", Installer.MC);
		vars.put("game_directory", gameDir.toString());
		vars.put("assets_root", root.resolve("assets").toString());
		vars.put("assets_index_name", p.assetIndexId());
		vars.put("auth_uuid", offlineUuid(cfg.nick).toString().replace("-", ""));
		vars.put("auth_access_token", "0");
		vars.put("clientid", "0");
		vars.put("auth_xuid", "0");
		vars.put("user_type", "legacy");
		vars.put("version_type", "Aurora");
		vars.put("natives_directory", natives.toString());
		vars.put("launcher_name", "AuroraLauncher");
		vars.put("launcher_version", "1.0");
		vars.put("classpath", cp.toString());
		vars.put("classpath_separator", File.pathSeparator);
		vars.put("library_directory", root.resolve("libraries").toString());
		vars.put("resolution_width", Integer.toString(cfg.width));
		vars.put("resolution_height", Integer.toString(cfg.height));

		Map<String, Boolean> features = new HashMap<>();
		features.put("has_custom_resolution", !cfg.fullscreen);

		List<String> cmd = new ArrayList<>();
		cmd.add(javaExecutable(cfg));
		cmd.add("-Xmx" + cfg.ramMb + "M");
		cmd.add("-Xms" + Math.min(512, cfg.ramMb) + "M");
		if (cfg.jvmArgs != null && !cfg.jvmArgs.isBlank()) {
			for (String a : cfg.jvmArgs.trim().split("\\s+")) {
				cmd.add(a);
			}
		}
		boolean hasCp = false;
		for (Map<String, Object> src : List.of(p.vanilla(), p.fabric())) {
			Map<String, Object> args = Json.obj(src.get("arguments"));
			if (args != null) {
				hasCp |= expand(Json.arr(args.get("jvm")), vars, features, cmd, true);
			}
		}
		if (!hasCp) {
			cmd.add("-cp");
			cmd.add(cp.toString());
		}
		cmd.add(Json.str(p.fabric().get("mainClass")));
		for (Map<String, Object> src : List.of(p.vanilla(), p.fabric())) {
			Map<String, Object> args = Json.obj(src.get("arguments"));
			if (args != null) {
				expand(Json.arr(args.get("game")), vars, features, cmd, false);
			}
		}
		if (cfg.fullscreen) {
			cmd.add("--fullscreen");
		}
		return cmd;
	}

	/** Adds the allowed arguments with ${placeholders} filled in. Returns true if a "-cp" was added. */
	private static boolean expand(List<Object> list, Map<String, String> vars, Map<String, Boolean> features,
								  List<String> out, boolean jvm) {
		boolean cp = false;
		if (list == null) {
			return false;
		}
		for (Object o : list) {
			List<String> values = new ArrayList<>();
			if (o instanceof String s) {
				values.add(s);
			} else if (Json.obj(o) != null) {
				Map<String, Object> m = Json.obj(o);
				if (!Rules.allowed(m.get("rules"), features)) {
					continue;
				}
				Object v = m.get("value");
				if (v instanceof String s) {
					values.add(s);
				} else if (Json.arr(v) != null) {
					for (Object x : Json.arr(v)) {
						values.add(String.valueOf(x));
					}
				}
			}
			for (String v : values) {
				String r = fill(v, vars);
				if (jvm && (r.equals("-cp") || r.equals("-classpath"))) {
					cp = true;
				}
				out.add(r);
			}
		}
		return cp;
	}

	private static String fill(String s, Map<String, String> vars) {
		StringBuilder b = new StringBuilder();
		int i = 0;
		while (i < s.length()) {
			int st = s.indexOf("${", i);
			if (st < 0) {
				b.append(s, i, s.length());
				break;
			}
			int en = s.indexOf('}', st);
			if (en < 0) {
				b.append(s, i, s.length());
				break;
			}
			b.append(s, i, st);
			String key = s.substring(st + 2, en);
			b.append(vars.getOrDefault(key, ""));
			i = en + 1;
		}
		return b.toString();
	}

	public static Process start(List<String> cmd, Path gameDir, Consumer<String> log) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(gameDir.toFile());
		pb.redirectErrorStream(true);
		Process pr = pb.start();
		Thread t = new Thread(() -> {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					log.accept(line);
				}
			} catch (IOException ignored) {
				// process ended
			}
		}, "game-log");
		t.setDaemon(true);
		t.start();
		return pr;
	}
}
