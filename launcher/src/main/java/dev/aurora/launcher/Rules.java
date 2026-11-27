package dev.aurora.launcher;

import java.util.List;
import java.util.Map;

/** Evaluates the "rules" lists used by Mojang's version files (operating system and feature flags). */
public final class Rules {
	private Rules() {}

	public static String osName() {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win")) {
			return "windows";
		}
		if (os.contains("mac")) {
			return "osx";
		}
		return "linux";
	}

	public static boolean allowed(Object rules, Map<String, Boolean> features) {
		List<Object> list = Json.arr(rules);
		if (list == null || list.isEmpty()) {
			return true;
		}
		boolean allow = false;
		for (Object o : list) {
			Map<String, Object> r = Json.obj(o);
			if (!matches(r, features)) {
				continue;
			}
			allow = "allow".equals(Json.str(r.get("action")));
		}
		return allow;
	}

	private static boolean matches(Map<String, Object> r, Map<String, Boolean> features) {
		Map<String, Object> os = Json.obj(r.get("os"));
		if (os != null) {
			String name = Json.str(os.get("name"));
			if (name != null && !name.equals(osName())) {
				return false;
			}
			String arch = Json.str(os.get("arch"));
			String real = System.getProperty("os.arch", "").toLowerCase();
			boolean x86 = real.equals("x86") || real.equals("i386");
			if (arch != null && arch.equals("x86") != x86) {
				return false;
			}
		}
		Map<String, Object> feat = Json.obj(r.get("features"));
		if (feat != null) {
			for (Map.Entry<String, Object> e : feat.entrySet()) {
				if (features.getOrDefault(e.getKey(), false) != Boolean.TRUE.equals(e.getValue())) {
					return false;
				}
			}
		}
		return true;
	}
}
