package dev.skinlibrary;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Генерация ссылки для /skin url — так же, как страница skinsrestorer.net/upload:
 *  1) загрузка PNG через прокси SkinsRestorer (Axolotl, ключ не нужен);
 *  2) если не вышло — напрямую в MineSkin (очередь /v2/queue, ключ необязателен).
 * Результат — ссылка https://minesk.in/<uuid>.
 */
public final class MineSkinApi {
	private static final String AXOLOTL_UPLOAD = "https://axolotl.skinsrestorer.net/mineskin/skins";
	private static final String MINESKIN_QUEUE = "https://api.mineskin.org/v2/queue";
	private static final Pattern UUID_FULL = Pattern.compile(
			"[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}");

	private MineSkinApi() {}

	/** @return значение для /skin url "..." */
	public static CompletableFuture<String> generate(String source, String variant, String name) {
		if (SkinResolver.isRemote(source)) {
			// ссылка уже есть — плагин сам загрузит её в MineSkin
			return CompletableFuture.completedFuture(source);
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				byte[] png = Files.readAllBytes(Path.of(source));
				if (png.length > 5 * 1024 * 1024) throw new IllegalStateException(I18n.t("файл больше 5 МБ"));
				if (png.length < 8 || (png[1] != 'P' || png[2] != 'N' || png[3] != 'G'))
					throw new IllegalStateException(I18n.t("нужен PNG-файл"));
				png = SkinPng.normalize(png); // 64x32 и HD-скины приводим к 64x64
				try {
					return viaAxolotl(png, variant, name, "");
				} catch (Exception first) {
					try {
						return viaMineSkin(png, variant, name, "");
					} catch (Exception second) {
						throw new IllegalStateException(msg(first) + "; MineSkin: " + msg(second));
					}
				}
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new IllegalStateException(msg(e));
			}
		});
	}

	/** Загружает скин с выбранным плащом (cape — id из списка SkinsRestorer). Источник: ссылка или файл. */
	public static CompletableFuture<String> generateWithCape(String source, String variant, String name, String cape) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				byte[] png = SkinResolver.isRemote(source)
						? SkinResolver.fetchBytes(SkinResolver.resolve(source))
						: Files.readAllBytes(Path.of(source));
				if (png.length > 5 * 1024 * 1024) throw new IllegalStateException(I18n.t("файл больше 5 МБ"));
				png = SkinPng.normalize(png);
				try {
					return viaAxolotl(png, variant, name, cape);
				} catch (Exception first) {
					try {
						return viaMineSkin(png, variant, name, cape);
					} catch (Exception second) {
						throw new IllegalStateException(msg(first) + "; MineSkin: " + msg(second));
					}
				}
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new IllegalStateException(msg(e));
			}
		});
	}

	// ---------------- Axolotl (прокси SkinsRestorer) ----------------

	private static String viaAxolotl(byte[] png, String variant, String name, String cape) throws Exception {
		String boundary = "----SkinLibrary" + UUID.randomUUID().toString().replace("-", "");
		HttpRequest req = HttpRequest.newBuilder(URI.create(AXOLOTL_UPLOAD))
				.timeout(Duration.ofSeconds(330))
				.header("User-Agent", "SkinLibraryMod/1.0")
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, png, variant, name, cape)))
				.build();
		HttpResponse<String> r = SkinResolver.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() >= 300) throw new IllegalStateException("Axolotl " + r.statusCode() + snippet(r.body()));

		JsonObject j = JsonParser.parseString(r.body()).getAsJsonObject();
		String url = findLink(j);
		if (url == null) {
			String uuid = uuidFrom(j);
			if (uuid != null) url = "https://minesk.in/" + uuid.replace("-", "");
		}
		if (url == null) throw new IllegalStateException(I18n.t("Axolotl: неожиданный ответ"));
		if (url.toLowerCase().startsWith(SkinResolver.AXOLOTL_SCHEME)) url = SkinResolver.decrypt(url);
		return url;
	}

	// ---------------- MineSkin напрямую ----------------

	private static String viaMineSkin(byte[] png, String variant, String name, String cape) throws Exception {
		String key = SkinStore.apiKey();
		String boundary = "----SkinLibrary" + UUID.randomUUID().toString().replace("-", "");
		HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(MINESKIN_QUEUE))
				.timeout(Duration.ofSeconds(60))
				.header("User-Agent", "SkinLibraryMod/1.0")
				.header("Content-Type", "multipart/form-data; boundary=" + boundary);
		if (!key.isBlank()) rb.header("Authorization", "Bearer " + key);
		HttpResponse<String> r = SkinResolver.HTTP.send(
				rb.POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, png, variant, name, cape))).build(),
				HttpResponse.BodyHandlers.ofString());

		JsonObject j = parse(r.body(), r.statusCode());
		String uuid = uuidFrom(j);
		if (uuid != null) return "https://minesk.in/" + uuid.replace("-", "");

		String jobId = null;
		if (j.has("job") && j.get("job").isJsonObject() && j.getAsJsonObject("job").has("id"))
			jobId = j.getAsJsonObject("job").get("id").getAsString();
		if (jobId == null) throw new IllegalStateException(errorMessage(j, r.statusCode()));

		for (int i = 0; i < 70; i++) {
			Thread.sleep(1200);
			HttpRequest.Builder pb = HttpRequest.newBuilder(URI.create(MINESKIN_QUEUE + "/" + jobId))
					.timeout(Duration.ofSeconds(20))
					.header("User-Agent", "SkinLibraryMod/1.0").GET();
			if (!key.isBlank()) pb.header("Authorization", "Bearer " + key);
			HttpResponse<String> pr = SkinResolver.HTTP.send(pb.build(), HttpResponse.BodyHandlers.ofString());
			JsonObject pj = parse(pr.body(), pr.statusCode());
			uuid = uuidFrom(pj);
			if (uuid != null) return "https://minesk.in/" + uuid.replace("-", "");
			if (pj.has("job") && pj.get("job").isJsonObject()) {
				JsonObject job = pj.getAsJsonObject("job");
				if (job.has("status") && "failed".equalsIgnoreCase(job.get("status").getAsString()))
					throw new IllegalStateException(I18n.t("MineSkin отклонил скин"));
			}
		}
		throw new IllegalStateException(I18n.t("превышено время ожидания MineSkin"));
	}

	// ---------------- вспомогательное ----------------

	/** Имена полей формы загрузки. По умолчанию — предположение; при возможности уточняются по OpenAPI Axolotl. */
	private static final class Names {
		String file = "file", variant = "variant", cape = "cape", name = "name", visibility = "visibility";
		List<String> variantEnum = List.of();
	}

	private static Names names;

	private static synchronized Names names() {
		if (names != null) return names;
		Names n = new Names();
		boolean ok = false;
		try {
			HttpResponse<String> r = SkinResolver.HTTP.send(HttpRequest.newBuilder(URI.create(AXOLOTL_UPLOAD.replace("/mineskin/skins", "/openapi")))
					.timeout(Duration.ofSeconds(10)).header("User-Agent", "SkinLibraryMod/1.0").GET().build(),
					HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() == 200) {
				JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
				JsonObject post = root.getAsJsonObject("paths").getAsJsonObject("/mineskin/skins").getAsJsonObject("post");
				JsonObject content = post.getAsJsonObject("requestBody").getAsJsonObject("content");
				if (content.has("multipart/form-data")) {
					JsonObject schema = resolve(root, content.getAsJsonObject("multipart/form-data").getAsJsonObject("schema"));
					JsonObject props = schema.getAsJsonObject("properties");
					for (var en : props.entrySet()) {
						String k = en.getKey(), lk = k.toLowerCase();
						JsonObject ps = resolve(root, en.getValue().getAsJsonObject());
						boolean binary = (ps.has("format") && "binary".equals(ps.get("format").getAsString()))
								|| (ps.has("contentMediaType") && ps.get("contentMediaType").getAsString().startsWith("image"));
						if (binary || lk.equals("file") || lk.equals("skin") || lk.equals("image")) n.file = k;
						else if (lk.contains("cape")) n.cape = k;
						else if (lk.contains("variant") || lk.equals("model")) {
							n.variant = k;
							if (ps.has("enum")) {
								List<String> vals = new ArrayList<>();
								for (JsonElement e : ps.getAsJsonArray("enum")) vals.add(e.getAsString());
								n.variantEnum = vals;
							}
						} else if (lk.equals("name")) n.name = k;
						else if (lk.contains("visib")) n.visibility = k;
					}
					ok = true;
				}
			}
		} catch (Exception ignored) {
			// не удалось — остаются имена по умолчанию
		}
		if (ok) names = n; // при сбое сети не запоминаем догадки, повторим при следующей загрузке
		return n;
	}

	private static JsonObject resolve(JsonObject root, JsonObject o) {
		for (int i = 0; i < 5 && o.has("$ref"); i++) {
			String[] parts = o.get("$ref").getAsString().replace("#/", "").split("/");
			JsonObject cur = root;
			for (String part : parts) cur = cur.getAsJsonObject(part);
			o = cur;
		}
		return o;
	}

	private static byte[] multipart(String boundary, byte[] png, String variant, String name, String cape) throws Exception {
		Names nm = names();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		String v = variant;
		for (String e : nm.variantEnum) if (e.equalsIgnoreCase(variant)) v = e; // регистр как в схеме
		field(out, boundary, nm.variant, v);
		field(out, boundary, nm.visibility, "unlisted");
		if (cape != null && !cape.isBlank()) field(out, boundary, nm.cape, cape);
		if (name != null && !name.isBlank()) field(out, boundary, nm.name, name);
		out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + nm.file + "\"; filename=\"skin.png\"\r\n"
				+ "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		out.write(png);
		out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return out.toByteArray();
	}

	private static void field(ByteArrayOutputStream out, String boundary, String n, String v) throws Exception {
		out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + n + "\"\r\n\r\n" + v + "\r\n")
				.getBytes(StandardCharsets.UTF_8));
	}

	private static JsonObject parse(String body, int status) {
		try {
			return JsonParser.parseString(body).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException("HTTP " + status);
		}
	}

	/** Ищет в JSON строку с minesk.in или skinsrestorer-axolotl://. */
	private static String findLink(JsonElement e) {
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			String s = e.getAsString();
			String l = s.toLowerCase();
			if (l.startsWith(SkinResolver.AXOLOTL_SCHEME) || l.startsWith("https://minesk.in/")) return s;
		} else if (e.isJsonObject()) {
			for (var en : e.getAsJsonObject().entrySet()) {
				String r = findLink(en.getValue());
				if (r != null) return r;
			}
		} else if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				String r = findLink(x);
				if (r != null) return r;
			}
		}
		return null;
	}

	private static String uuidFrom(JsonObject j) {
		if (j.has("skin") && j.get("skin").isJsonObject()) {
			JsonObject s = j.getAsJsonObject("skin");
			if (s.has("uuid")) return s.get("uuid").getAsString();
		}
		if (j.has("job") && j.get("job").isJsonObject()) {
			JsonObject job = j.getAsJsonObject("job");
			if (job.has("result") && job.get("result").isJsonPrimitive()) {
				String r = job.get("result").getAsString();
				if (UUID_FULL.matcher(r).matches()) return r;
			}
		}
		return null;
	}

	private static String errorMessage(JsonObject json, int status) {
		try {
			if (json.has("errors") && json.get("errors").isJsonArray() && !json.getAsJsonArray("errors").isEmpty()) {
				JsonObject e = json.getAsJsonArray("errors").get(0).getAsJsonObject();
				if (e.has("message")) return e.get("message").getAsString();
				if (e.has("code")) return e.get("code").getAsString();
			}
			if (json.has("error")) return json.get("error").getAsString();
		} catch (Exception ignored) {
		}
		return "HTTP " + status;
	}

	/** Короткий текст ошибки из ответа сервера (поле message/error/detail или начало тела). */
	private static String snippet(String body) {
		if (body == null || body.isBlank()) return "";
		try {
			JsonObject o = JsonParser.parseString(body).getAsJsonObject();
			for (String k : new String[]{"message", "error", "detail", "title"}) {
				if (o.has(k) && o.get(k).isJsonPrimitive()) return ": " + cut(o.get(k).getAsString());
			}
		} catch (Exception ignored) {
		}
		return ": " + cut(body.trim().replaceAll("\\s+", " "));
	}

	private static String cut(String s) {
		return s.length() > 110 ? s.substring(0, 110) + "…" : s;
	}

	private static String msg(Throwable t) {
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}
}
