package dev.skinlibrary;

import java.util.UUID;

/** Одна запись в библиотеке скинов. */
public class SkinEntry {
	public String id = UUID.randomUUID().toString();
	public String name;
	public String url = "";            // ссылка для /skin url (может быть пустой у скинов из лаунчера до загрузки)
	public String variant = "classic"; // classic | slim
	public boolean favorite = false;
	public String tag = "";            // тег / папка
	public String file = "";           // локальный PNG (скины из лаунчера)
	public String origin = "";         // откуда импортирован: account:<хэш> / launcher:<хэш>
	public String capeId = "";         // выбранный плащ SkinsRestorer (пусто — без плаща)
	public String capeName = "";
	public String capeUrl = "";        // ссылка на скин с вшитым плащом (создаётся при первом надевании)

	public String nickId = "";         // UUID игрока (32 hex), если скин добавлен по нику: скин обновляется, когда игрок его меняет
	public transient long nickChecked; // когда в последний раз сверяли с Mojang (только в памяти)

	public SkinEntry() {}

	/** Скин «живой»: привязан к аккаунту игрока и обновляется вместе с ним. */
	public boolean isLive() {
		return nickId != null && nickId.matches("[0-9a-f]{32}");
	}

	public SkinEntry(String name, String url, String variant) {
		this.name = name;
		this.url = url == null ? "" : url;
		this.variant = "slim".equalsIgnoreCase(variant) ? "slim" : "classic";
	}

	public boolean isSlim() {
		return "slim".equalsIgnoreCase(variant);
	}

	public boolean hasUrl() {
		return url != null && !url.isBlank();
	}

	public boolean hasCape() {
		return capeId != null && !capeId.isBlank();
	}

	/** Ссылка, которая уйдёт в команду: скин с плащом, если он уже создан, иначе обычная. */
	public String commandUrl() {
		return hasCape() && capeUrl != null && !capeUrl.isBlank() ? capeUrl : url;
	}

	/** Можно ли отправлять команду сразу (не нужна загрузка файла или создание плаща). */
	public boolean ready() {
		return hasCape() ? (capeUrl != null && !capeUrl.isBlank()) : hasUrl();
	}

	/** Источник для превью: ссылка или локальный файл. */
	public String source() {
		if (hasUrl()) return url;
		return file == null ? "" : file;
	}

	/** Команда без слэша — в таком виде её принимает sendCommand. Null, если ссылки ещё нет. */
	public String commandNoSlash() {
		String u = commandUrl();
		return u != null && !u.isBlank() ? SkinCommand.build(u, variant) : null;
	}

	/** Команда для показа пользователю. */
	public String commandDisplay() {
		String c = commandNoSlash();
		return c != null ? "/" + c : I18n.t("(команда создастся при надевании)");
	}
}
