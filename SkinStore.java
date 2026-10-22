package dev.skinlibrary;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Хранилище: config/skinlibrary/skins.json и config/skinlibrary/config.json */
public final class SkinStore {
	private static final Logger LOG = LoggerFactory.getLogger("skinlibrary");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("skinlibrary");
	private static final Path SKINS_FILE = DIR.resolve("skins.json");
	private static final Path CONFIG_FILE = DIR.resolve("config.json");

	private static final List<SkinEntry> SKINS = new ArrayList<>();
	private static String apiKey = "";
	private static String themeId = "ps";
	private static String animationId = "shelf";
	private static String poseId = "stand";
	private static boolean sound = true;
	private static boolean previewEnabled = true;
	private static String activeId = "";
	private static String soundPack = "classic";
	private static boolean animSounds = false;
	private static float volume = 1f;
	private static final Set<String> FOLDERS = new java.util.LinkedHashSet<>();
	private static final List<String> SCAN_FOLDERS = new ArrayList<>();
	private static final Map<String, int[]> POSITIONS = new HashMap<>();
	private static final Set<String> IGNORED = new HashSet<>();
	private static int casinoPoints = 100;
	private static String wearMode = "card";           // card | button | both
	private static boolean autoApply = true;
	private static int cooldownSec = 30;
	private static String chosenId = "";
	private static final Map<String, String> APPLIED = new HashMap<>();   // сервер -> id надетого скина
	private static final Map<String, Float> SOUND_VOL = new HashMap<>();
	private static String randomMode = "off";          // off | join | daily
	private static String randomFolder = "";           // "" — все скины, "★" — избранное, иначе имя папки
	private static String dailyDate = "", dailyId = "";
	private static boolean notifyCooldown = true;
	private static String language = "auto";           // auto | ru | en
	private static boolean guideSeen = false;
	private static final Set<String> SOUND_OFF = new HashSet<>();

	private SkinStore() {}

	// ------------------------------------------------------------------ загрузка и сохранение

	public static void load() {
		try {
			Files.createDirectories(DIR);
			try {
			if (Files.exists(SKINS_FILE)) {
				try (Reader r = Files.newBufferedReader(SKINS_FILE)) {
					SkinEntry[] arr = GSON.fromJson(r, SkinEntry[].class);
					if (arr != null) {
						for (SkinEntry e : arr) {
							if (e == null) continue;
							if (e.url == null) e.url = "";
							if (e.nickId == null) e.nickId = "";
							if (e.variant == null || e.variant.isBlank()) e.variant = "classic";
							if (e.name == null) e.name = "";
							if (e.file == null) e.file = "";
							if (e.origin == null) e.origin = "";
							if (e.tag == null) e.tag = "";
							if (e.capeId == null) e.capeId = "";
							if (e.capeName == null) e.capeName = "";
							if (e.capeUrl == null) e.capeUrl = "";
							if (e.id == null || e.id.isBlank()) e.id = java.util.UUID.randomUUID().toString();
							SKINS.add(e);
						}
					}
				}
			}
			} catch (Exception ex) {
				LOG.error("skins.json повреждён, копия сохранена в skins.json.broken", ex);
				backupBroken(SKINS_FILE);
				SKINS.clear();
			}
			boolean cfgOk = true;
			try {
			if (Files.exists(CONFIG_FILE)) {
				try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
					JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
					if (o.has("mineskinApiKey")) apiKey = o.get("mineskinApiKey").getAsString();
					if (o.has("theme")) themeId = o.get("theme").getAsString();
					if (o.has("animation")) animationId = o.get("animation").getAsString();
					if (o.has("pose")) poseId = o.get("pose").getAsString();
					if (o.has("sound")) sound = o.get("sound").getAsBoolean();
					if (o.has("menuPreview")) previewEnabled = o.get("menuPreview").getAsBoolean();
					if (o.has("activeSkin")) activeId = o.get("activeSkin").getAsString();
					if (o.has("soundPack")) soundPack = o.get("soundPack").getAsString();
					if (o.has("animSounds")) animSounds = o.get("animSounds").getAsBoolean();
					if (o.has("volume")) volume = o.get("volume").getAsFloat();
					if (o.has("folders")) for (JsonElement el : o.getAsJsonArray("folders")) FOLDERS.add(el.getAsString());
					if (o.has("scanFolders")) for (JsonElement el : o.getAsJsonArray("scanFolders")) SCAN_FOLDERS.add(el.getAsString());
					if (o.has("positions") && o.get("positions").isJsonObject()) {
						for (var en : o.getAsJsonObject("positions").entrySet()) {
							JsonArray a = en.getValue().getAsJsonArray();
							POSITIONS.put(en.getKey(), new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
						}
					}
					if (o.has("buttonX") && o.has("buttonY") && !POSITIONS.containsKey("btn_pause")) { // старый формат
						POSITIONS.put("btn_pause", new int[]{o.get("buttonX").getAsInt(), o.get("buttonY").getAsInt()});
					}
					if (o.has("casinoPoints")) casinoPoints = o.get("casinoPoints").getAsInt();
					if (o.has("wearMode")) wearMode = o.get("wearMode").getAsString();
					if (o.has("autoApply")) autoApply = o.get("autoApply").getAsBoolean();
					if (o.has("wearCooldown")) cooldownSec = Math.max(0, o.get("wearCooldown").getAsInt());
					if (o.has("chosenSkin")) chosenId = o.get("chosenSkin").getAsString();
					if (o.has("applied") && o.get("applied").isJsonObject())
						for (var en : o.getAsJsonObject("applied").entrySet()) APPLIED.put(en.getKey(), en.getValue().getAsString());
					if (o.has("soundVol") && o.get("soundVol").isJsonObject())
						for (var en : o.getAsJsonObject("soundVol").entrySet()) SOUND_VOL.put(en.getKey(), en.getValue().getAsFloat());
					if (o.has("soundOff")) for (JsonElement el : o.getAsJsonArray("soundOff")) SOUND_OFF.add(el.getAsString());
					if (o.has("randomMode")) randomMode = o.get("randomMode").getAsString();
					if (o.has("randomFolder")) randomFolder = o.get("randomFolder").getAsString();
					if (o.has("dailyDate")) dailyDate = o.get("dailyDate").getAsString();
					if (o.has("dailyId")) dailyId = o.get("dailyId").getAsString();
					if (o.has("notifyCooldown")) notifyCooldown = o.get("notifyCooldown").getAsBoolean();
					if (o.has("language")) language = o.get("language").getAsString();
					if (o.has("guideSeen")) guideSeen = o.get("guideSeen").getAsBoolean();
					if (o.has("ignored")) {
						for (JsonElement el : o.getAsJsonArray("ignored")) IGNORED.add(el.getAsString());
					}
				}
			}
			} catch (Exception ex) {
				LOG.error("config.json повреждён, копия сохранена в config.json.broken", ex);
				backupBroken(CONFIG_FILE);
				cfgOk = false;
			}
			if (cfgOk) saveConfig(); // при ошибке чтения не затираем файл частично прочитанным состоянием
			if (!SKINS.isEmpty()) saveSkins();
		} catch (Exception e) {
			LOG.error("Не удалось загрузить библиотеку скинов", e);
		}
	}

	/** Запись через временный файл: при сбое посреди записи старый файл не портится. */
	private static void writeAtomic(Path file, String content) throws java.io.IOException {
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, content);
		try {
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Повреждённый файл не затираем: сохраняем копию рядом (*.broken). */
	private static void backupBroken(Path file) {
		try {
			Path bak = file.resolveSibling(file.getFileName() + ".broken");
			if (Files.exists(bak)) bak = file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis() + ".broken");
			Files.copy(file, bak, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ignored) {
		}
	}

	private static void saveSkins() {
		try {
			Files.createDirectories(DIR);
			writeAtomic(SKINS_FILE, GSON.toJson(SKINS));
		} catch (Exception e) {
			LOG.error("Не удалось сохранить библиотеку скинов", e);
		}
	}

	private static void saveConfig() {
		try {
			Files.createDirectories(DIR);
			JsonObject o = new JsonObject();
			o.addProperty("mineskinApiKey", apiKey == null ? "" : apiKey);
			o.addProperty("theme", themeId);
			o.addProperty("animation", animationId);
			o.addProperty("pose", poseId);
			o.addProperty("sound", sound);
			o.addProperty("menuPreview", previewEnabled);
			o.addProperty("activeSkin", activeId);
			o.addProperty("soundPack", soundPack);
			o.addProperty("animSounds", animSounds);
			o.addProperty("volume", volume);
			JsonArray fl = new JsonArray();
			for (String f : FOLDERS) fl.add(f);
			o.add("folders", fl);
			JsonArray sf = new JsonArray();
			for (String f : SCAN_FOLDERS) sf.add(f);
			o.add("scanFolders", sf);
			JsonObject pos = new JsonObject();
			for (var en : POSITIONS.entrySet()) {
				JsonArray a = new JsonArray();
				a.add(en.getValue()[0]);
				a.add(en.getValue()[1]);
				pos.add(en.getKey(), a);
			}
			o.add("positions", pos);
			o.addProperty("casinoPoints", casinoPoints);
			o.addProperty("wearMode", wearMode);
			o.addProperty("autoApply", autoApply);
			o.addProperty("wearCooldown", cooldownSec);
			o.addProperty("chosenSkin", chosenId);
			JsonObject ap = new JsonObject();
			for (var en : APPLIED.entrySet()) ap.addProperty(en.getKey(), en.getValue());
			o.add("applied", ap);
			JsonObject sv = new JsonObject();
			for (var en : SOUND_VOL.entrySet()) sv.addProperty(en.getKey(), en.getValue());
			o.add("soundVol", sv);
			JsonArray so = new JsonArray();
			for (String k : SOUND_OFF) so.add(k);
			o.add("soundOff", so);
			o.addProperty("randomMode", randomMode);
			o.addProperty("randomFolder", randomFolder);
			o.addProperty("dailyDate", dailyDate);
			o.addProperty("dailyId", dailyId);
			o.addProperty("notifyCooldown", notifyCooldown);
			o.addProperty("language", language);
			o.addProperty("guideSeen", guideSeen);
			JsonArray ign = new JsonArray();
			for (String s : IGNORED) ign.add(s);
			o.add("ignored", ign);
			writeAtomic(CONFIG_FILE, GSON.toJson(o));
		} catch (Exception e) {
			LOG.error("Не удалось сохранить настройки", e);
		}
	}

	// ------------------------------------------------------------------ настройки

	public static String apiKey() {
		return apiKey == null ? "" : apiKey.trim();
	}

	public static String themeId() {
		return themeId;
	}

	public static void setThemeId(String id) {
		themeId = id;
		saveConfig();
	}

	public static String animationId() {
		return animationId;
	}

	public static void setAnimationId(String id) {
		animationId = id;
		saveConfig();
	}

	public static String poseId() {
		return poseId;
	}

	public static void setPoseId(String id) {
		poseId = id;
		saveConfig();
	}

	public static boolean sound() {
		return sound;
	}

	public static void setSound(boolean on) {
		sound = on;
		saveConfig();
	}

	public static boolean previewEnabled() {
		return previewEnabled;
	}

	public static void setPreviewEnabled(boolean on) {
		previewEnabled = on;
		saveConfig();
	}

	public static String soundPackId() {
		return soundPack;
	}

	public static void setSoundPackId(String id) {
		soundPack = id;
		saveConfig();
	}

	public static boolean animSounds() {
		return animSounds;
	}

	public static void setAnimSounds(boolean on) {
		animSounds = on;
		saveConfig();
	}

	public static float volume() {
		return volume;
	}

	public static void setVolume(float v) {
		volume = Math.max(0f, Math.min(1f, v));
		saveConfig();
	}

	// ------------------------------------------------------------------ папки (тег записи = имя папки)

	/** Все папки: созданные вручную и те, что используются в тегах скинов. */
	public static List<String> folders() {
		TreeMap<String, String> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (String f : FOLDERS) if (f != null && !f.isBlank()) m.putIfAbsent(f.trim(), f.trim());
		for (SkinEntry e : SKINS) if (e.tag != null && !e.tag.isBlank()) m.putIfAbsent(e.tag.trim(), e.tag.trim());
		return new ArrayList<>(m.values());
	}

	public static int folderCount(String name) {
		int n = 0;
		for (SkinEntry e : SKINS) if (e.tag != null && e.tag.trim().equalsIgnoreCase(name)) n++;
		return n;
	}

	public static boolean folderExists(String name) {
		for (String f : folders()) if (f.equalsIgnoreCase(name.trim())) return true;
		return false;
	}

	public static boolean createFolder(String name) {
		String n = name == null ? "" : name.trim();
		if (n.isEmpty() || n.length() > 24 || folderExists(n)) return false;
		FOLDERS.add(n);
		saveConfig();
		return true;
	}

	public static boolean renameFolder(String old, String neu) {
		String n = neu == null ? "" : neu.trim();
		if (n.isEmpty() || n.length() > 24) return false;
		if (!old.equalsIgnoreCase(n) && folderExists(n)) return false;
		FOLDERS.removeIf(f -> f.equalsIgnoreCase(old));
		FOLDERS.add(n);
		for (SkinEntry e : SKINS) if (e.tag != null && e.tag.trim().equalsIgnoreCase(old)) e.tag = n;
		saveConfig();
		saveSkins();
		return true;
	}

	/** Удаляет папку; скины из неё остаются в библиотеке без папки. */
	public static void deleteFolder(String name) {
		FOLDERS.removeIf(f -> f.equalsIgnoreCase(name));
		for (SkinEntry e : SKINS) if (e.tag != null && e.tag.trim().equalsIgnoreCase(name)) e.tag = "";
		saveConfig();
		saveSkins();
	}

	public static List<String> scanFolders() {
		return Collections.unmodifiableList(SCAN_FOLDERS);
	}

	public static void addScanFolder(String path) {
		if (!SCAN_FOLDERS.contains(path)) {
			SCAN_FOLDERS.add(path);
			saveConfig();
		}
	}

	public static void removeScanFolder(String path) {
		SCAN_FOLDERS.remove(path);
		saveConfig();
	}

	/** Сохранённая позиция плавающего элемента меню (ключи вида btn_pause, prev_title) или null. */
	public static int[] pos(String key) {
		return POSITIONS.get(key);
	}

	public static void setPos(String key, int x, int y) {
		POSITIONS.put(key, new int[]{x, y});
		saveConfig();
	}

	// ------------------------------------------------------------------ записи

	public static List<SkinEntry> skins() {
		return Collections.unmodifiableList(SKINS);
	}

	public static void save() {
		saveSkins();
	}

	public static void add(SkinEntry e) {
		SKINS.add(e);
		saveSkins();
	}

	public static void remove(SkinEntry e) {
		SKINS.remove(e);
		if (e.origin != null && !e.origin.isBlank()) {
			IGNORED.add(e.origin); // чтобы автосинхронизация не вернула удалённый скин
			saveConfig();
		}
		saveSkins();
	}

	/** Есть ли в библиотеке такой же скин (та же ссылка и модель). */
	public static boolean hasSkin(String url, String variant) {
		String v = "slim".equalsIgnoreCase(variant) ? "slim" : "classic";
		for (SkinEntry e : SKINS) if (e.hasUrl() && e.url.equals(url) && v.equals(e.variant)) return true;
		return false;
	}

	public static String defaultName() {
		return I18n.t("Скин ") + (SKINS.size() + 1);
	}

	private static String uniqueName(String base) {
		String name = base;
		int n = 2;
		while (true) {
			boolean dup = false;
			for (SkinEntry e : SKINS) {
				if (e.name != null && e.name.equalsIgnoreCase(name)) {
					dup = true;
					break;
				}
			}
			if (!dup) return name;
			name = base + " (" + n++ + ")";
		}
	}

	/** Добавляет импортированный из аккаунта/лаунчера скин. @return true, если добавлен */
	public static boolean addSynced(SkinEntry e, boolean force) {
		if (e.origin == null || e.origin.isBlank()) return false;
		if (!force && IGNORED.contains(e.origin)) return false;
		for (SkinEntry o : SKINS) if (e.origin.equals(o.origin)) return false;
		if (force && IGNORED.remove(e.origin)) saveConfig();
		e.name = uniqueName(e.name == null || e.name.isBlank() ? I18n.t("Скин") : e.name);
		SKINS.add(e);
		if (activeId.isBlank() && e.origin.startsWith("account:")) {
			activeId = e.id;
			saveConfig();
		}
		saveSkins();
		return true;
	}

	/** Пользователь выбрал/надел скин: он показывается в превью и применяется при входе на сервер. */
	public static void setActive(SkinEntry e) {
		activeId = e.id;
		chosenId = e.id;
		saveConfig();
	}

	/** Скин, который игрок выбрал сам (для автоприменения на сервере). */
	public static SkinEntry chosenEntry() {
		if (chosenId.isBlank()) return null;
		for (SkinEntry e : SKINS) if (chosenId.equals(e.id)) return e;
		return null;
	}

	public static String appliedOn(String server) {
		return APPLIED.getOrDefault(server, "");
	}

	public static void setApplied(String server, String skinId) {
		APPLIED.put(server, skinId);
		saveConfig();
	}

	public static void clearApplied(String skinId) {
		if (APPLIED.values().removeIf(v -> v.equals(skinId))) saveConfig();
	}

	public static int points() {
		return casinoPoints;
	}

	public static void setPoints(int p) {
		casinoPoints = Math.max(0, p);
		saveConfig();
	}

	public static String wearMode() {
		return wearMode;
	}

	public static void setWearMode(String m) {
		wearMode = m;
		saveConfig();
	}

	public static boolean autoApply() {
		return autoApply;
	}

	public static void setAutoApply(boolean on) {
		autoApply = on;
		saveConfig();
	}

	public static int cooldownSec() {
		return cooldownSec;
	}

	public static String randomMode() {
		return randomMode;
	}

	public static void setRandomMode(String m) {
		randomMode = m;
		saveConfig();
	}

	public static String randomFolder() {
		return randomFolder;
	}

	public static void setRandomFolder(String f) {
		randomFolder = f == null ? "" : f;
		saveConfig();
	}

	public static String dailyDate() {
		return dailyDate;
	}

	public static String dailyId() {
		return dailyId;
	}

	public static void setDaily(String date, String id) {
		dailyDate = date;
		dailyId = id;
		saveConfig();
	}

	public static boolean notifyCooldown() {
		return notifyCooldown;
	}

	public static void setNotifyCooldown(boolean on) {
		notifyCooldown = on;
		saveConfig();
	}

	public static boolean guideSeen() {
		return guideSeen;
	}

	public static void setGuideSeen(boolean v) {
		guideSeen = v;
		saveConfig();
	}

	public static String language() {
		return language;
	}

	public static void setLanguage(String l) {
		language = l;
		saveConfig();
	}

	/** Скины для случайного выбора: все, избранные или из выбранной папки. */
	public static List<SkinEntry> randomPool() {
		List<SkinEntry> pool = new ArrayList<>();
		for (SkinEntry e : SKINS) {
			if (randomFolder.isEmpty()
					|| (randomFolder.equals("\u2605") && e.favorite)
					|| (e.tag != null && e.tag.trim().equalsIgnoreCase(randomFolder))) pool.add(e);
		}
		return pool;
	}

	/** Отдельные звуки: ключи HOVER, SELECT, CLICK, WEAR, ANIM, CASINO. */
	public static boolean soundEnabled(String key) {
		return !SOUND_OFF.contains(key);
	}

	public static void setSoundEnabled(String key, boolean on) {
		if (on) SOUND_OFF.remove(key);
		else SOUND_OFF.add(key);
		saveConfig();
	}

	/** Изменение без записи на диск (для ползунков); сохранить — saveSettings(). */
	public static void setSoundVolumeLive(String key, float v) {
		SOUND_VOL.put(key, Math.max(0f, Math.min(1f, v)));
	}

	public static void setVolumeLive(float v) {
		volume = Math.max(0f, Math.min(1f, v));
	}

	public static void saveSettings() {
		saveConfig();
	}

	public static float soundVolume(String key) {
		return SOUND_VOL.getOrDefault(key, 1f);
	}

	public static void setSoundVolume(String key, float v) {
		SOUND_VOL.put(key, Math.max(0f, Math.min(1f, v)));
		saveConfig();
	}

	/** Скин для превью в меню: последний надетый, иначе скин аккаунта, иначе первый в библиотеке. */
	public static SkinEntry activeEntry() {
		if (!activeId.isBlank()) for (SkinEntry e : SKINS) if (activeId.equals(e.id)) return e;
		for (SkinEntry e : SKINS) if (e.origin != null && e.origin.startsWith("account:")) return e;
		return SKINS.isEmpty() ? null : SKINS.get(0);
	}

	/** Все используемые теги (по алфавиту, без пустых). */
	public static List<String> tags() {
		TreeMap<String, String> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (SkinEntry e : SKINS) {
			if (e.tag != null && !e.tag.isBlank()) m.putIfAbsent(e.tag.trim(), e.tag.trim());
		}
		return new ArrayList<>(m.values());
	}

	// ------------------------------------------------------------------ экспорт / импорт

	/** Экспорт в JSON (только скины со ссылкой). @return число записей */
	public static int exportTo(Path path) throws Exception {
		Path p = path;
		if (!p.getFileName().toString().toLowerCase().endsWith(".json")) {
			p = p.resolveSibling(p.getFileName() + ".json");
		}
		List<SkinEntry> out = new ArrayList<>();
		for (SkinEntry e : SKINS) if (e.hasUrl()) out.add(e);
		Files.writeString(p, GSON.toJson(out));
		return out.size();
	}

	/** Импорт из JSON (дубликаты по ссылке и модели пропускаются). @return сколько добавлено */
	public static int importFrom(Path path) throws Exception {
		int added = 0;
		try (Reader r = Files.newBufferedReader(path)) {
			SkinEntry[] arr = GSON.fromJson(r, SkinEntry[].class);
			if (arr == null) return 0;
			for (SkinEntry e : arr) {
				if (SKINS.size() >= 500) break;
				if (e == null || e.url == null || !SkinResolver.isRemote(e.url)) continue;
				String variant = "slim".equalsIgnoreCase(e.variant) ? "slim" : "classic";
				boolean dup = false;
				for (SkinEntry o : SKINS) {
					if (e.url.equals(o.url) && variant.equals(o.variant)) {
						dup = true;
						break;
					}
				}
				if (dup) continue;
				String name = e.name == null || e.name.isBlank() ? defaultName() : e.name.trim();
				SkinEntry n = new SkinEntry(name.length() > 32 ? name.substring(0, 32) : name, e.url, variant);
				n.favorite = e.favorite;
				if (e.nickId != null && e.nickId.matches("[0-9a-f]{32}")) n.nickId = e.nickId;
				String tag = e.tag == null ? "" : e.tag.trim();
				n.tag = tag.length() > 24 ? tag.substring(0, 24) : tag;
				SKINS.add(n);
				added++;
			}
		}
		if (added > 0) saveSkins();
		return added;
	}
}
