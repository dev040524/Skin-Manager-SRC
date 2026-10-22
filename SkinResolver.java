package dev.skinlibrary;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Превращает «ссылку из команды» в прямую ссылку на PNG:
 *  - skinsrestorer-axolotl://v2/...  -> расшифровка через axolotl.skinsrestorer.net -> https://minesk.in/<uuid>
 *  - https://minesk.in/<uuid>        -> это HTML-страница MineSkin, поэтому берём URL текстуры через API MineSkin
 *  - всё остальное считается прямой ссылкой на картинку
 */
public final class SkinResolver {
	public static final String AXOLOTL_SCHEME = "skinsrestorer-axolotl://";
	private static final Pattern UUID_RE = Pattern.compile(
			"([0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12})");

	static final HttpClient HTTP = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private SkinResolver() {}

	public static boolean isRemote(String s) {
		if (s == null || s.length() > 400) return false;
		for (int i = 0; i < s.length(); i++) { // управляющие символы, пробелы, § и кавычки ломают команду /skin
			char c = s.charAt(i);
			if (c <= ' ' || c == 0x7F || c == '\u00A7' || c == '"') return false;
		}
		String l = s.toLowerCase();
		return l.startsWith("http://") || l.startsWith("https://") || l.startsWith(AXOLOTL_SCHEME);
	}

	public static String resolve(String source) throws Exception {
		String s = source.trim();
		if (s.toLowerCase().startsWith(AXOLOTL_SCHEME)) s = decrypt(s);
		String l = s.toLowerCase();
		if (l.contains("minesk.in/") || l.contains("mineskin.org/")) {
			Matcher m = UUID_RE.matcher(s);
			if (m.find()) return textureUrl(dash(m.group(1)));
			throw new IOException(I18n.t("не удалось найти id скина в ссылке"));
		}
		return s;
	}

	/** skinsrestorer-axolotl://... -> https://minesk.in/<uuid> */
	public static String decrypt(String axolotlUrl) throws Exception {
		String url = "https://axolotl.skinsrestorer.net/mineskin/decrypt-url?url="
				+ URLEncoder.encode(axolotlUrl, StandardCharsets.UTF_8);
		JsonObject j = getJson(url, false);
		if (!j.has("url")) throw new IOException(I18n.t("Axolotl не вернул ссылку"));
		return j.get("url").getAsString();
	}

	/** Скин игрока по нику через API Mojang. */
	public static SkinEntry lookupNick(String nick) throws Exception {
		if (!nick.matches("[A-Za-z0-9_]{1,16}")) throw new IOException(I18n.t("некорректный ник"));

		HttpResponse<String> r1 = HTTP.send(HttpRequest.newBuilder(
						URI.create("https://api.mojang.com/users/profiles/minecraft/" + nick))
				.timeout(Duration.ofSeconds(15)).header("User-Agent", "SkinLibraryMod/1.0").GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (r1.statusCode() == 204 || r1.statusCode() == 404) throw new IOException(I18n.t("игрок не найден"));
		if (r1.statusCode() == 429) throw new IOException(I18n.t("слишком много запросов, подождите"));
		if (r1.statusCode() != 200) throw new IOException("Mojang HTTP " + r1.statusCode());
		JsonObject prof = JsonParser.parseString(r1.body()).getAsJsonObject();
		String id = prof.get("id").getAsString();
		String realName = prof.has("name") ? prof.get("name").getAsString() : nick;

		return sessionSkin(id, realName);
	}

	/** Скин аккаунта по UUID (лицензионный аккаунт). */
	public static SkinEntry lookupUuid(java.util.UUID uuid, String fallbackName) throws Exception {
		return sessionSkin(uuid.toString().replace("-", ""), fallbackName);
	}

	/** Текущий скин игрока по UUID (32 hex) — для обновления скинов, добавленных по нику. */
	public static SkinEntry lookupId(String id, String fallbackName) throws Exception {
		return sessionSkin(id, fallbackName);
	}

	private static SkinEntry sessionSkin(String id, String fallbackName) throws Exception {
		HttpResponse<String> r2 = HTTP.send(HttpRequest.newBuilder(
						URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id))
				.timeout(Duration.ofSeconds(15)).header("User-Agent", "SkinLibraryMod/1.0").GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (r2.statusCode() == 429) throw new IOException(I18n.t("слишком много запросов, подождите"));
		if (r2.statusCode() != 200) throw new IOException("Mojang HTTP " + r2.statusCode());
		JsonObject sess = JsonParser.parseString(r2.body()).getAsJsonObject();
		String realName = sess.has("name") ? sess.get("name").getAsString() : fallbackName;
		for (var el : sess.getAsJsonArray("properties")) {
			JsonObject p = el.getAsJsonObject();
			if (!"textures".equals(p.get("name").getAsString())) continue;
			String json = new String(Base64.getDecoder().decode(p.get("value").getAsString()), StandardCharsets.UTF_8);
			JsonObject tex = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
			if (tex == null || !tex.has("SKIN")) break;
			JsonObject skin = tex.getAsJsonObject("SKIN");
			String url = fixTexture(skin.get("url").getAsString());
			boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
					&& "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
			SkinEntry se = new SkinEntry(realName, url, slim ? "slim" : "classic");
			se.nickId = id.toLowerCase();
			return se;
		}
		throw new IOException(I18n.t("у игрока нет скина"));
	}

	private static String textureUrl(String uuid) throws Exception {
		JsonObject root = getJson("https://api.mineskin.org/v2/skins/" + uuid, true);
		JsonObject skin = root.has("skin") && root.get("skin").isJsonObject() ? root.getAsJsonObject("skin") : root;
		if (!skin.has("texture") || !skin.get("texture").isJsonObject())
			throw new IOException(I18n.t("MineSkin не вернул текстуру"));
		JsonObject tex = skin.getAsJsonObject("texture");

		if (tex.has("url") && tex.get("url").isJsonObject()) {
			JsonObject u = tex.getAsJsonObject("url");
			if (u.has("skin")) return fixTexture(u.get("skin").getAsString());
		}
		if (tex.has("data") && tex.get("data").isJsonObject()) {
			JsonObject d = tex.getAsJsonObject("data");
			if (d.has("value")) {
				String json = new String(Base64.getDecoder().decode(d.get("value").getAsString()), StandardCharsets.UTF_8);
				JsonObject v = JsonParser.parseString(json).getAsJsonObject();
				return fixTexture(v.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString());
			}
		}
		throw new IOException(I18n.t("в ответе MineSkin нет ссылки на текстуру"));
	}

	private static String fixTexture(String url) {
		return url.startsWith("http://textures.minecraft.net") ? "https://" + url.substring(7) : url;
	}

	private static String dash(String u) {
		String h = u.replace("-", "").toLowerCase();
		if (h.length() != 32) return u;
		return h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16) + "-"
				+ h.substring(16, 20) + "-" + h.substring(20);
	}

	private static JsonObject getJson(String url, boolean mineskin) throws Exception {
		HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "SkinLibraryMod/1.0")
				.header("Accept", "application/json")
				.GET();
		String key = SkinStore.apiKey();
		if (mineskin && !key.isBlank()) rb.header("Authorization", "Bearer " + key);
		HttpResponse<String> r = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() == 404) throw new IOException(I18n.t("скин не найден (404)"));
		if (r.statusCode() >= 300) throw new IOException("HTTP " + r.statusCode());
		return JsonParser.parseString(r.body()).getAsJsonObject();
	}

	public static byte[] fetchBytes(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(fixTexture(url)))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "SkinLibraryMod/1.0")
				.GET().build();
		HttpResponse<java.io.InputStream> r = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
		try (java.io.InputStream in = r.body()) {
			if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
			byte[] data = in.readNBytes(4_000_001); // читаем не больше лимита, а не всё тело
			if (data.length > 4_000_000) throw new IOException(I18n.t("файл слишком большой"));
			return data;
		}
	}
}
