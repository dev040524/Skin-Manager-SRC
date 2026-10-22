package dev.skinlibrary.screen;

import dev.skinlibrary.SkinStore;

/** Стили анимации карточек в библиотеке. */
public enum CardStyle {
	SHELF("shelf", "Книги"),
	FAN("fan", "Веер"),
	WAVE("wave", "Волна"),
	FLOW("flow", "Карусель");

	public final String id;
	public final String label;

	CardStyle(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public static CardStyle current() {
		String id = SkinStore.animationId();
		for (CardStyle s : values()) if (s.id.equals(id)) return s;
		return SHELF;
	}

	public static CardStyle next() {
		CardStyle[] v = values();
		return v[(current().ordinal() + 1) % v.length];
	}
}
