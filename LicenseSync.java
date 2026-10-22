package dev.skinlibrary;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Автоматический импорт скинов:
 *  - скин текущего лицензионного аккаунта (сервер сессий Mojang), название = ник, папка «Аккаунт»;
 *  - официальный лаунчер (launcher_custom_skins.json), название берётся из файла;
 *  - Prism Launcher / PolyMC / MultiMC (accounts.json со скинами аккаунтов, папка skins);
 *  - Modrinth App, TLauncher, GDLauncher и др. — поиск папок skins (формат баз данных не читается);
 *  - любые папки, которые вы добавите вручную.
 * Каждый лаунчер попадает в свою папку библиотеки. Для скинов из файлов ссылка для команды
 * создаётся при первом надевании.
 */
public final class LicenseSync {
	public record Report(int added, List<String> lines) {}

	private record Root(String label, Path dir) {}

	private static volatile boolean running;

	private LicenseSync() {}

	/** @param force true — вернуть и те скины, которые пользователь ранее удалил. */
	public static CompletableFuture<Report> sync(boolean force) {
		CompletableFuture<Report> result = new CompletableFuture<>();
		if (running) {
			result.complete(new Report(0, List.of(I18n.t("Поиск уже идёт..."))));
			return result;
		}
		running = true;
		Minecraft mc = Minecraft.getInstance();

		UUID id = null;
		String name = null;
		try {
			id = mc.getUser().getProfileId();
			name = mc.getUser().getName();
		} catch (Throwable ignored) {
		}
		final UUID fid = id;
		final String fname = name;

		CompletableFuture.runAsync(() -> {
			List<SkinEntry> found = new ArrayList<>();
			Map<String, Integer> counts = new LinkedHashMap<>();
			try {
				SkinEntry acc = accountSkin(fid, fname);
				if (acc != null) {
					found.add(acc);
					counts.merge(I18n.t("Аккаунт (") + acc.name + ")", 1, Integer::sum);
				}
			} catch (Throwable ignored) {
			}
			scanLaunchers(found, counts);

			mc.execute(() -> {
				Report rep;
				try {
					int n = 0;
					for (SkinEntry e : found) if (SkinStore.addSynced(e, force)) n++;
					List<String> lines = new ArrayList<>();
					if (counts.isEmpty()) lines.add(I18n.t("Ничего не найдено"));
					for (var en : counts.entrySet()) lines.add(en.getKey() + I18n.t(": найдено ") + en.getValue());
					rep = new Report(n, lines);
				} catch (Throwable t) {
					rep = new Report(0, List.of(I18n.t("Ошибка поиска")));
				} finally {
					running = false;
				}
				result.complete(rep);
			});
		}).exceptionally(ex -> {
			mc.execute(() -> {
				running = false;
				result.complete(new Report(0, List.of(I18n.t("Ошибка поиска"))));
			});
			return null;
		});
		return result;
	}

	// ------------------------------------------------------------------ аккаунт

	private static SkinEntry accountSkin(UUID id, String fallbackName) throws Exception {
		if (id == null) return null;
		SkinEntry e = SkinResolver.lookupUuid(id, fallbackName);
		e.origin = "account:" + textureHash(e.url);
		e.tag = I18n.t("Аккаунт");
		return e;
	}

	private static String textureHash(String url) {
		String h = url.substring(url.lastIndexOf('/') + 1);
		return h.length() > 16 ? h.substring(0, 16) : h;
	}

	// ------------------------------------------------------------------ лаунчеры

	private static void scanLaunchers(List<SkinEntry> out, Map<String, Integer> counts) {
		for (Root r : roots()) {
			List<SkinEntry> part = new ArrayList<>();
			try {
				scanRoot(r, part);
			} catch (Throwable ignored) {
			}
			if (!part.isEmpty()) {
				out.addAll(part);
				counts.merge(r.label(), part.size(), Integer::sum);
			}
		}
		for (String f : new ArrayList<>(SkinStore.scanFolders())) { // копия: список могут менять из интерфейса
			List<SkinEntry> part = new ArrayList<>();
			String label = I18n.t("Папка ") + f;
			try {
				label = I18n.t("Папка ") + Path.of(f).getFileName();
				scanPngFolder(Path.of(f), label, 2, part);
			} catch (Throwable ignored) {
			}
			if (!part.isEmpty()) {
				out.addAll(part);
				counts.merge(label, part.size(), Integer::sum);
			}
		}
	}

	private static List<Root> roots() {
		List<Root> roots = new ArrayList<>();
		Set<Path> seen = new LinkedHashSet<>();
		String home = System.getProperty("user.home", "");
		String appdata = System.getenv("APPDATA");
		Path game = FabricLoader.getInstance().getGameDir();

		java.util.function.BiConsumer<String, Path> add = (label, dir) -> {
			if (dir == null) return;
			Path d = dir.toAbsolutePath().normalize();
			if (Files.isDirectory(d) && seen.add(d)) roots.add(new Root(label, d));
		};

		// лаунчер, рядом с которым лежит игра (в т.ч. portable Prism: instances/.../minecraft)
		Path p = game.toAbsolutePath().normalize();
		for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
			if (Files.exists(p.resolve("accounts.json")) || Files.exists(p.resolve("launcher_custom_skins.json"))) {
				add.accept(i == 0 ? I18n.t("Лаунчер") : I18n.t("Лаунчер (") + p.getFileName() + ")", p);
			}
		}
		add.accept(I18n.t("Официальный лаунчер"), game);

		if (appdata != null) {
			Path a = Path.of(appdata);
			add.accept(I18n.t("Официальный лаунчер"), a.resolve(".minecraft"));
			add.accept("Prism Launcher", a.resolve("PrismLauncher"));
			add.accept("PolyMC", a.resolve("PolyMC"));
			add.accept("Modrinth App", a.resolve("ModrinthApp"));
			add.accept("TLauncher", a.resolve(".tlauncher"));
			add.accept("GDLauncher", a.resolve("gdlauncher_next"));
		}
		if (!home.isEmpty()) {
			Path h = Path.of(home);
			add.accept(I18n.t("Официальный лаунчер"), h.resolve(".minecraft"));
			add.accept("Prism Launcher", h.resolve(".local/share/PrismLauncher"));
			add.accept("Prism Launcher", h.resolve(".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"));
			add.accept("PolyMC", h.resolve(".local/share/PolyMC"));
			add.accept("Modrinth App", h.resolve(".local/share/ModrinthApp"));
			add.accept("TLauncher", h.resolve(".tlauncher"));
			Path mac = h.resolve("Library/Application Support");
			add.accept(I18n.t("Официальный лаунчер"), mac.resolve("minecraft"));
			add.accept("Prism Launcher", mac.resolve("PrismLauncher"));
			add.accept("PolyMC", mac.resolve("PolyMC"));
			add.accept("Modrinth App", mac.resolve("ModrinthApp"));
		}
		return roots;
	}

	private static void scanRoot(Root r, List<SkinEntry> out) throws Exception {
		Path official = r.dir().resolve("launcher_custom_skins.json");
		if (Files.isRegularFile(official)) {
			List<JsonObject> objs = new ArrayList<>();
			collectSkinImages(JsonParser.parseString(Files.readString(official)), objs);
			for (JsonObject o : objs) {
				try {
					SkinEntry e = fromLauncherObject(o, r.label());
					if (e != null) addUnique(out, e);
				} catch (Exception ignored) {
				}
			}
		}

		Path accounts = r.dir().resolve("accounts.json"); // формат Prism / PolyMC / MultiMC
		if (Files.isRegularFile(accounts)) {
			List<JsonObject> profiles = new ArrayList<>();
			collectProfiles(JsonParser.parseString(Files.readString(accounts)), profiles);
			for (JsonObject prof : profiles) {
				try {
					JsonObject skin = prof.getAsJsonObject("skin");
					String url = skin.get("url").getAsString();
					if (url.startsWith("http://")) url = "https://" + url.substring(7);
					String variant = skin.has("variant") ? skin.get("variant").getAsString() : "classic";
					String nick = prof.has("name") ? prof.get("name").getAsString() : I18n.t("Игрок");
					SkinEntry e = new SkinEntry(nick, url, variant.equalsIgnoreCase("slim") ? "slim" : "classic");
					e.origin = "account:" + textureHash(url);
					e.tag = r.label();
					addUnique(out, e);
				} catch (Exception ignored) {
				}
			}
		}

		scanPngFolder(r.dir().resolve("skins"), r.label(), 1, out);
		scanPngFolder(r.dir().resolve("caches").resolve("skins"), r.label(), 1, out);
	}

	private static void addUnique(List<SkinEntry> out, SkinEntry e) {
		for (SkinEntry x : out) if (x.origin.equals(e.origin)) return;
		out.add(e);
	}

	// ------------------------------------------------------------------ разбор JSON

	private static void collectSkinImages(JsonElement el, List<JsonObject> out) {
		if (el.isJsonObject()) {
			JsonObject o = el.getAsJsonObject();
			if (o.has("skinImage") && o.get("skinImage").isJsonPrimitive()) {
				out.add(o);
				return;
			}
			for (var en : o.entrySet()) collectSkinImages(en.getValue(), out);
		} else if (el.isJsonArray()) {
			for (JsonElement x : el.getAsJsonArray()) collectSkinImages(x, out);
		}
	}

	private static void collectProfiles(JsonElement el, List<JsonObject> out) {
		if (el.isJsonObject()) {
			JsonObject o = el.getAsJsonObject();
			if (o.has("skin") && o.get("skin").isJsonObject() && o.getAsJsonObject("skin").has("url")) {
				out.add(o);
				return;
			}
			for (var en : o.entrySet()) collectProfiles(en.getValue(), out);
		} else if (el.isJsonArray()) {
			for (JsonElement x : el.getAsJsonArray()) collectProfiles(x, out);
		}
	}

	private static SkinEntry fromLauncherObject(JsonObject o, String label) throws Exception {
		byte[] png = decodeImage(o.get("skinImage").getAsString());
		if (png == null) return null;
		String sha = sha1(png).substring(0, 8);
		String name = o.has("name") && o.get("name").isJsonPrimitive() ? o.get("name").getAsString().trim() : "";
		if (name.isEmpty()) name = I18n.t("Скин лаунчера");
		if (name.length() > 32) name = name.substring(0, 32);
		boolean slim = (o.has("slim") && o.get("slim").isJsonPrimitive() && o.get("slim").getAsBoolean())
				|| (o.has("model") && o.get("model").isJsonPrimitive() && "slim".equalsIgnoreCase(o.get("model").getAsString()))
				|| (o.has("variant") && o.get("variant").isJsonPrimitive() && "slim".equalsIgnoreCase(o.get("variant").getAsString()));
		SkinEntry e = new SkinEntry(name, "", slim ? "slim" : "classic");
		e.file = storePng(png, name, sha);
		e.origin = "launcher:" + sha;
		e.tag = label;
		return e;
	}

	// ------------------------------------------------------------------ PNG-папки

	private static void scanPngFolder(Path dir, String label, int depth, List<SkinEntry> out) throws Exception {
		if (!Files.isDirectory(dir)) return;
		List<Path> pngs = new ArrayList<>();
		try (Stream<Path> st = Files.walk(dir, depth)) {
			st.filter(Files::isRegularFile)
					.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".png"))
					.limit(200).forEach(pngs::add);
		}
		for (Path f : pngs) {
			try {
				if (Files.size(f) > 1_000_000) continue;
				byte[] bytes = Files.readAllBytes(f);
				BufferedImage img = SkinPng.readSafe(new ByteArrayInputStream(bytes));
				if (img == null) continue;
				int w = img.getWidth(), h = img.getHeight();
				boolean skinShape = (w == 64 && (h == 64 || h == 32)) || (w == 128 && (h == 128 || h == 64));
				if (!skinShape) continue;
				String sha = sha1(bytes).substring(0, 8);
				String base = f.getFileName().toString();
				base = base.substring(0, base.length() - 4);
				if (base.length() > 32) base = base.substring(0, 32);
				SkinEntry e = new SkinEntry(base, "", "classic");
				e.file = storePng(bytes, base, sha);
				e.origin = "file:" + sha;
				e.tag = label;
				addUnique(out, e);
			} catch (Exception ignored) {
			}
		}
	}

	private static String storePng(byte[] png, String name, String sha) throws Exception {
		Path dir = SkinStore.DIR.resolve("imported");
		Files.createDirectories(dir);
		Path f = dir.resolve(safe(name) + "-" + sha + ".png");
		if (!Files.exists(f)) {
			Path tmp = dir.resolve(f.getFileName() + ".tmp");
			Files.write(tmp, png);
			Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING); // не оставляем обрезанный PNG
		}
		return f.toString();
	}

	private static byte[] decodeImage(String s) {
		int comma = s.indexOf(',');
		if (s.startsWith("data:") && comma >= 0) s = s.substring(comma + 1);
		byte[] b = Base64.getMimeDecoder().decode(s.getBytes(StandardCharsets.UTF_8));
		boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
		return png && b.length < 1_000_000 ? b : null;
	}

	private static String sha1(byte[] data) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (byte x : MessageDigest.getInstance("SHA-1").digest(data)) sb.append(String.format("%02x", x));
		return sb.toString();
	}

	private static String safe(String name) {
		String s = name.replaceAll("[^\\p{L}\\p{N}_-]+", "_");
		return s.isBlank() ? "skin" : s;
	}
}
