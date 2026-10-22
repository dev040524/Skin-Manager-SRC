package dev.skinlibrary;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Список плащей, которые поддерживает SkinsRestorer (прокси Axolotl): GET /mineskin/capes. */
public final class Capes {
	public static final class Cape {
		public final String id, name, url;
		public volatile int[] front; // лицевая сторона 10x16, ARGB
		public volatile int[] tex;   // вся текстура плаща в сетке 64x64, ARGB (для 3D-превью)
		volatile boolean loading;

		Cape(String id, String name, String url) {
			this.id = id;
			this.name = name;
			this.url = url;
		}
	}

	private static final java.util.concurrent.ExecutorService POOL = java.util.concurrent.Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "skinlibrary-capes");
		t.setDaemon(true);
		return t;
	});

	private static final String ENDPOINT = "https://axolotl.skinsrestorer.net/mineskin/capes";

	public static volatile List<Cape> LIST = List.of();
	public static volatile String state = "idle"; // idle | loading | ok | error
	public static volatile String error = "";

	private Capes() {}

	public static void ensureLoaded() {
		if (state.equals("idle") || state.equals("error")) reload();
	}

	public static void reload() {
		state = "loading";
		CompletableFuture.runAsync(() -> {
			try {
				HttpResponse<String> r = SkinResolver.HTTP.send(HttpRequest.newBuilder(URI.create(ENDPOINT))
						.timeout(Duration.ofSeconds(20)).header("User-Agent", "SkinLibraryMod/1.0")
						.header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
				if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
				JsonArray arr = findArray(JsonParser.parseString(r.body()));
				if (arr == null) throw new IllegalStateException(I18n.t("неожиданный ответ"));
				List<Cape> out = new ArrayList<>();
				for (JsonElement el : arr) {
					Cape c = parse(el);
					if (c != null) out.add(c);
				}
				LIST = out;
				state = "ok";
			} catch (Exception e) {
				error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				state = "error";
			}
		});
	}

	public static Cape byId(String id) {
		for (Cape c : LIST) if (c.id.equals(id)) return c;
		return null;
	}

	/** Запускает загрузку картинки плаща (один раз). */
	public static void loadTexture(Cape c) {
		if (c.tex != null || c.loading || c.url == null || c.url.isBlank()) return;
		c.loading = true;
		CompletableFuture.runAsync(() -> {
			try {
				BufferedImage img = SkinPng.readSafe(new ByteArrayInputStream(SkinResolver.fetchBytes(c.url)));
				if (img == null) return;
				int w = img.getWidth(), ih = img.getHeight();
				int sc = Math.max(1, w / (w >= 64 ? 64 : 22));
				int[] f = new int[10 * 16];
				for (int y = 0; y < 16; y++)
					for (int x = 0; x < 10; x++)
						f[y * 10 + x] = img.getRGB(Math.min(w - 1, (1 + x) * sc), Math.min(ih - 1, (1 + y) * sc)) | 0xFF000000;
				// вся текстура: 64x32 (или 22x17) приводим к сетке 64x64
				double k = w >= 64 ? w / 64.0 : w / 22.0;
				int[] t = new int[64 * 64];
				for (int y = 0; y < 32; y++) {
					for (int x = 0; x < 64; x++) {
						int sx = (int) (x * k), sy = (int) (y * k);
						if (sx < w && sy < ih) t[y * 64 + x] = img.getRGB(sx, sy);
					}
				}
				c.tex = t;
				c.front = f;
			} catch (Exception ignored) {
			}
		}, POOL);
	}

	private static JsonArray findArray(JsonElement e) {
		if (e.isJsonArray()) return e.getAsJsonArray();
		if (e.isJsonObject()) {
			for (var en : e.getAsJsonObject().entrySet()) {
				if (en.getValue().isJsonArray()) return en.getValue().getAsJsonArray();
			}
			for (var en : e.getAsJsonObject().entrySet()) {
				if (en.getValue().isJsonObject()) {
					JsonArray a = findArray(en.getValue());
					if (a != null) return a;
				}
			}
		}
		return null;
	}

	private static String str(JsonObject o, String... keys) {
		for (String k : keys) {
			if (o.has(k) && o.get(k).isJsonPrimitive()) {
				String v = o.get(k).getAsString();
				if (!v.isBlank()) return v;
			}
		}
		return null;
	}

	private static Cape parse(JsonElement el) {
		if (el.isJsonPrimitive()) {
			String s = el.getAsString();
			return new Cape(s, s, null);
		}
		if (!el.isJsonObject()) return null;
		JsonObject o = el.getAsJsonObject();
		if (o.has("supported") && o.get("supported").isJsonPrimitive() && !o.get("supported").getAsBoolean()) return null;
		String id = str(o, "id", "uuid", "capeId");
		if (id == null) return null;
		String name = str(o, "alias", "name", "label", "title");
		String url = str(o, "url", "texture", "image", "imageUrl");
		if (url == null) {
			for (var en : o.entrySet()) {
				if (en.getValue().isJsonPrimitive()) {
					String v = en.getValue().getAsString();
					if (v.startsWith("http") && (v.contains("texture") || v.endsWith(".png"))) url = v;
				}
			}
		}
		if (url != null && url.startsWith("http://")) url = "https://" + url.substring(7);
		return new Cape(id, name == null ? id : name, url);
	}
}
