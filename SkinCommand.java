package dev.skinlibrary;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбор и сборка команд SkinRestorer вида: /skin url "https://minesk.in/..." classic */
public final class SkinCommand {
	private static final Pattern PATTERN = Pattern.compile(
			"^\\s*/?skin\\s+url\\s+(?:\"([^\"]+)\"|(\\S+))(?:\\s+(classic|slim))?\\s*$",
			Pattern.CASE_INSENSITIVE);

	private SkinCommand() {}

	/** @return запись без имени (имя заполняется позже) или null, если это не команда /skin url. */
	public static SkinEntry parse(String text) {
		if (text == null) return null;
		String t = text.replace('\u201C', '"').replace('\u201D', '"').trim();
		Matcher m = PATTERN.matcher(t);
		if (!m.matches()) return null;
		String url = m.group(1) != null ? m.group(1) : m.group(2);
		if (!SkinResolver.isRemote(url)) return null;
		String variant = m.group(3) == null ? "classic" : m.group(3).toLowerCase();
		return new SkinEntry(null, url, variant);
	}

	public static String build(String url, String variant) {
		return "skin url \"" + url + "\" " + ("slim".equalsIgnoreCase(variant) ? "slim" : "classic");
	}
}
