package dev.skinlibrary;

import java.util.HashMap;
import java.util.Map;

/** Кэш превью по источнику (URL или путь к файлу). */
public final class SkinPreviewCache {
	private static final Map<String, SkinPreview> MAP = new HashMap<>();

	private SkinPreviewCache() {}

	public static String normalize(String s) {
		if (s == null) return "";
		int n = s.length();
		if (n > 0 && s.charAt(0) > ' ' && s.charAt(0) != '"' && s.charAt(n - 1) > ' ') return s; // быстрый путь: без лишних строк
		s = s.trim();
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1).trim();
		return s;
	}

	public static SkinPreview get(String source) {
		String key = normalize(source);
		SkinPreview p = MAP.computeIfAbsent(key, SkinPreview::new);
		if (p.state == SkinPreview.State.FAILED) { // временная ошибка сети не должна жить до перезапуска: повтор через 30 с
			long now = System.currentTimeMillis();
			Long at = FAIL_AT.putIfAbsent(key, now);
			if (at != null && now - at > 30_000L) {
				FAIL_AT.remove(key);
				MAP.remove(key);
				p.close();
				p = MAP.computeIfAbsent(key, SkinPreview::new);
			}
		} else if (!FAIL_AT.isEmpty()) {
			FAIL_AT.remove(key);
		}
		return p;
	}

	private static final Map<String, Long> FAIL_AT = new java.util.concurrent.ConcurrentHashMap<>();

	public static void evict(String source) {
		SkinPreview p = MAP.remove(normalize(source));
		if (p != null) p.close();
	}
}
