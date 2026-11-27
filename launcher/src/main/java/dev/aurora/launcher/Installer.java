package dev.aurora.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads Minecraft 1.21.11 from Mojang's servers, installs the Fabric loader and puts Aurora and
 * Fabric API into the mods folder. Everything already present and intact is skipped.
 */
public final class Installer {
	public static final String MC = "1.21.11";
	public static final String LOADER = "0.17.3";
	public static final String FABRIC_API = "0.141.6+1.21.11";
	private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
	private static final String ASSETS = "https://resources.download.minecraft.net/";

	public interface Progress {
		void update(String text, double fraction);
	}

	/** What the launcher needs to start the game. */
	public record Prepared(Map<String, Object> vanilla, Map<String, Object> fabric, List<Path> classpath, Path clientJar,
						   String assetIndexId) {}

	private record Task(String url, Path path, String sha1, long size) {}

	private final Path root;
	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

	public Installer(Path root) {
		this.root = root;
	}

	public Prepared prepare(Config cfg, Progress progress, boolean force) throws Exception {
		Files.createDirectories(root);
		progress.update("Reading version list", 0.0);
		Path vdir = root.resolve("versions").resolve(MC);
		Path vjson = vdir.resolve(MC + ".json");
		if (force || !Files.exists(vjson)) {
			Map<String, Object> manifest = Json.obj(Json.parse(getString(MANIFEST)));
			String url = null;
			for (Object o : Json.arr(manifest.get("versions"))) {
				Map<String, Object> v = Json.obj(o);
				if (MC.equals(Json.str(v.get("id")))) {
					url = Json.str(v.get("url"));
				}
			}
			if (url == null) {
				throw new IOException("Minecraft " + MC + " not found in Mojang's version list");
			}
			Files.createDirectories(vdir);
			Files.writeString(vjson, getString(url));
		}
		Map<String, Object> vanilla = Json.obj(Json.parse(Files.readString(vjson)));

		progress.update("Reading Fabric profile", 0.02);
		Path fdir = root.resolve("versions").resolve("fabric-loader-" + LOADER + "-" + MC);
		Path fjson = fdir.resolve("profile.json");
		if (force || !Files.exists(fjson)) {
			Files.createDirectories(fdir);
			Files.writeString(fjson, getString("https://meta.fabricmc.net/v2/versions/loader/" + MC + "/" + LOADER
				+ "/profile/json"));
		}
		Map<String, Object> fabric = Json.obj(Json.parse(Files.readString(fjson)));

		List<Task> tasks = new ArrayList<>();
		Map<String, Path> libs = new LinkedHashMap<>();

		for (Object o : Json.arr(vanilla.get("libraries"))) {
			Map<String, Object> lib = Json.obj(o);
			if (!Rules.allowed(lib.get("rules"), Map.of())) {
				continue;
			}
			Map<String, Object> art = Json.obj(Json.obj(lib.get("downloads")) == null ? null
				: Json.obj(lib.get("downloads")).get("artifact"));
			if (art == null) {
				continue;
			}
			Path p = root.resolve("libraries").resolve(Json.str(art.get("path")));
			tasks.add(new Task(Json.str(art.get("url")), p, Json.str(art.get("sha1")), Json.num(art.get("size"), -1)));
			libs.put(libKey(Json.str(lib.get("name"))), p);
		}
		for (Object o : Json.arr(fabric.get("libraries"))) {
			Map<String, Object> lib = Json.obj(o);
			String name = Json.str(lib.get("name"));
			String base = Json.str(lib.get("url"));
			String rel = mavenPath(name);
			Path p = root.resolve("libraries").resolve(rel);
			tasks.add(new Task((base == null ? "https://maven.fabricmc.net/" : base) + rel, p, Json.str(lib.get("sha1")),
				Json.num(lib.get("size"), -1)));
			libs.put(libKey(name), p);
		}

		Map<String, Object> dl = Json.obj(vanilla.get("downloads"));
		Map<String, Object> client = Json.obj(dl.get("client"));
		Path clientJar = vdir.resolve(MC + ".jar");
		tasks.add(new Task(Json.str(client.get("url")), clientJar, Json.str(client.get("sha1")), Json.num(client.get("size"), -1)));

		Map<String, Object> ai = Json.obj(vanilla.get("assetIndex"));
		String aiId = Json.str(ai.get("id"));
		Path aiFile = root.resolve("assets").resolve("indexes").resolve(aiId + ".json");
		progress.update("Downloading asset index", 0.04);
		download(new Task(Json.str(ai.get("url")), aiFile, Json.str(ai.get("sha1")), Json.num(ai.get("size"), -1)));
		Map<String, Object> objects = Json.obj(Json.obj(Json.parse(Files.readString(aiFile))).get("objects"));
		for (Map.Entry<String, Object> e : objects.entrySet()) {
			Map<String, Object> ob = Json.obj(e.getValue());
			String hash = Json.str(ob.get("hash"));
			String pre = hash.substring(0, 2);
			tasks.add(new Task(ASSETS + pre + "/" + hash, root.resolve("assets").resolve("objects").resolve(pre).resolve(hash),
				hash, Json.num(ob.get("size"), -1)));
		}

		if (cfg.installAurora) {
			Path mods = cfg.gameDirPath().resolve("mods");
			Files.createDirectories(mods);
			String apiName = "fabric-api-" + FABRIC_API + ".jar";
			try (var s = Files.list(mods)) {
				for (Path f : (Iterable<Path>) s::iterator) {
					String n = f.getFileName().toString();
					if (n.startsWith("fabric-api-") && n.endsWith(".jar") && !n.equals(apiName)) {
						Files.deleteIfExists(f);
					}
				}
			}
			tasks.add(new Task("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/" + FABRIC_API + "/" + apiName,
				mods.resolve(apiName), null, -1));
			installAurora(mods.resolve("aurora.jar"));
		}

		int total = tasks.size();
		AtomicInteger done = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool(12);
		List<Future<?>> futures = new ArrayList<>();
		for (Task t : tasks) {
			futures.add(pool.submit(() -> {
				download(t);
				int d = done.incrementAndGet();
				if (d % 25 == 0 || d == total) {
					progress.update("Downloading files " + d + "/" + total, 0.05 + 0.95 * d / total);
				}
				return null;
			}));
		}
		try {
			for (Future<?> f : futures) {
				f.get();
			}
		} finally {
			pool.shutdownNow();
		}
		return new Prepared(vanilla, fabric, new ArrayList<>(libs.values()), clientJar, aiId);
	}

	/** Copies the Aurora jar that is bundled inside the launcher into the mods folder (if it differs). */
	private void installAurora(Path target) throws IOException {
		try (InputStream in = Installer.class.getResourceAsStream("/aurora-mod.jar")) {
			if (in == null) {
				return; // launcher built without the mod: keep whatever is in the folder
			}
			byte[] data = in.readAllBytes();
			if (Files.exists(target) && Files.size(target) == data.length && java.util.Arrays.equals(Files.readAllBytes(target), data)) {
				return;
			}
			Files.write(target, data);
		}
	}

	private static String libKey(String name) {
		String[] p = name.split(":");
		return p[0] + ":" + p[1] + (p.length > 3 ? ":" + p[3] : "");
	}

	static String mavenPath(String name) {
		String[] p = name.split(":");
		String group = p[0].replace('.', '/');
		String file = p[1] + "-" + p[2] + (p.length > 3 ? "-" + p[3] : "") + ".jar";
		return group + "/" + p[1] + "/" + p[2] + "/" + file;
	}

	private String getString(String url) throws IOException, InterruptedException {
		HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
			HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) {
			throw new IOException("HTTP " + r.statusCode() + " for " + url);
		}
		return r.body();
	}

	private void download(Task t) throws Exception {
		if (Files.exists(t.path)) {
			if (t.sha1 != null && t.size > 0 ? Files.size(t.path) == t.size && sha1(t.path).equalsIgnoreCase(t.sha1)
				: Files.size(t.path) > 0) {
				return;
			}
		}
		Files.createDirectories(t.path.getParent());
		Path tmp = t.path.resolveSibling(t.path.getFileName() + ".part");
		IOException last = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				HttpResponse<Path> r = http.send(HttpRequest.newBuilder(URI.create(t.url)).build(),
					HttpResponse.BodyHandlers.ofFile(tmp));
				if (r.statusCode() != 200) {
					throw new IOException("HTTP " + r.statusCode() + " for " + t.url);
				}
				if (t.sha1 != null && !sha1(tmp).equalsIgnoreCase(t.sha1)) {
					throw new IOException("Checksum mismatch for " + t.url);
				}
				Files.move(tmp, t.path, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (IOException e) {
				last = e;
			}
		}
		throw last;
	}

	private static String sha1(Path p) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		try (InputStream in = Files.newInputStream(p)) {
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) {
				md.update(buf, 0, n);
			}
		}
		StringBuilder b = new StringBuilder();
		for (byte x : md.digest()) {
			b.append(String.format("%02x", x));
		}
		return b.toString();
	}
}
