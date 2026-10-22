package dev.skinlibrary;

import dev.skinlibrary.screen.CardStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Звуки интерфейса. Два независимых типа:
 *  1) звуки анимаций — зависят от стиля карточек (Книги — страницы, Веер — стук планок и т.д.);
 *  2) звуковые темы (Классика, Кристалл, Мелодия, Ретро, Свои) — не зависят от анимации.
 * Если включены звуки анимаций, при смене карточки играет звук анимации, остальное — звуки темы.
 */
public final class Sfx {
	public enum Event {
		HOVER("Наведение"), SELECT("Выбор карточки"), CLICK("Нажатие"), WEAR("Надевание");

		public final String label;

		Event(String label) {
			this.label = label;
		}
	}

	public enum Pack {
		CLASSIC("classic", "Классика"), CRYSTAL("crystal", "Кристалл"), MELODY("melody", "Мелодия"),
		RETRO("retro", "Ретро"), CUSTOM("custom", "Свои");

		public final String id, label;

		Pack(String id, String label) {
			this.id = id;
			this.label = label;
		}

		public static Pack current() {
			for (Pack p : values()) if (p.id.equals(SkinStore.soundPackId())) return p;
			return CLASSIC;
		}

		public static Pack next() {
			Pack[] v = values();
			return v[(current().ordinal() + 1) % v.length];
		}
	}

	private record Task(long at, Runnable run) {}

	private static final List<Task> QUEUE = new ArrayList<>();
	private static String K = "CLICK"; // ключ звука для отдельной громкости/выключателя
	private static final float[] SCALE = {0.84f, 0.94f, 1.06f, 1.26f, 1.41f, 1.68f, 1.88f};

	private Sfx() {}

	/** Вызывать каждый клиентский тик: проигрывает отложенные звуки. */
	public static void pump(Minecraft mc) {
		if (QUEUE.isEmpty()) return;
		long now = System.nanoTime();
		Iterator<Task> it = QUEUE.iterator();
		List<Runnable> due = new ArrayList<>();
		while (it.hasNext()) {
			Task t = it.next();
			if (t.at() <= now) {
				due.add(t.run());
				it.remove();
			}
		}
		for (Runnable r : due) r.run();
	}

	private static void later(int ms, Runnable r) {
		final String key = K;
		QUEUE.add(new Task(System.nanoTime() + ms * 1_000_000L, () -> {
			String old = K;
			K = key;
			try {
				r.run();
			} finally {
				K = old;
			}
		}));
	}

	/** ev — SoundEvent или Holder<SoundEvent> (тип констант зависит от версии, поэтому Object). */
	@SuppressWarnings("unchecked")
	private static void snd(Object ev, float pitch, float vol) {
		if (!SkinStore.sound() || !SkinStore.soundEnabled(K)) return;
		float v = Math.min(1f, vol * SkinStore.volume() * SkinStore.soundVolume(K));
		if (v <= 0.001f) return;
		try {
			Holder<SoundEvent> h = ev instanceof Holder<?> hh
					? (Holder<SoundEvent>) hh
					: BuiltInRegistries.SOUND_EVENT.wrapAsHolder((SoundEvent) ev);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(h, pitch, v));
		} catch (Throwable ignored) {
		}
	}

	private static float rnd(float base, float spread) {
		return base + (ThreadLocalRandom.current().nextFloat() - 0.5f) * spread;
	}

	// ------------------------------------------------------------------ публичный API

	public static void hover() {
		themed(Event.HOVER, 0);
	}

	/** Совместимость со старым названием. */
	public static void tick() {
		hover();
	}

	public static void click() {
		themed(Event.CLICK, 0);
	}

	public static void wear() {
		themed(Event.WEAR, 0);
	}

	public static void select() {
		select(0);
	}

	/** Смена выбранной карточки; index — номер карточки (для мелодичных звуков). */
	public static void select(int index) {
		if (!SkinStore.sound()) return;
		if (SkinStore.animSounds()) animation(CardStyle.current(), index);
		else themed(Event.SELECT, index);
	}

	/** Пробный проигрыш события (для экрана настроек). */
	public static void preview(Event e) {
		if (e == Event.SELECT) themed(Event.SELECT, 3);
		else themed(e, 0);
	}

	public static void previewAnimation() {
		animation(CardStyle.current(), 2);
	}

	/** Уведомление (например, закончился КД): два мягких колокольчика. */
	public static void notice() {
		if (!SkinStore.sound()) return;
		K = "WEAR";
		snd(SoundEvents.NOTE_BLOCK_BELL, 1.4f, 0.7f);
		later(120, () -> snd(SoundEvents.NOTE_BLOCK_BELL, 1.88f, 0.7f));
	}

	/** Звуки автомата (пасхалка): 0 рычаг, 1 тик барабана, 2 остановка, 3 выигрыш, 4 джекпот, 5 проигрыш. */
	public static void casino(int kind) {
		if (!SkinStore.sound()) return;
		K = "CASINO";
		switch (kind) {
			case 0 -> snd(SoundEvents.LEVER_CLICK, 0.7f, 0.9f);
			case 1 -> snd(SoundEvents.UI_BUTTON_CLICK, rnd(1.7f, 0.2f), 0.35f);
			case 2 -> snd(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, rnd(1.0f, 0.1f), 0.7f);
			case 3 -> {
				snd(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
				later(100, () -> snd(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f, 0.8f));
				later(200, () -> snd(SoundEvents.NOTE_BLOCK_BELL, 1.5f, 0.8f));
			}
			case 4 -> {
				for (int i = 0; i < 7; i++) {
					final float p = SCALE[i];
					later(i * 100, () -> snd(SoundEvents.NOTE_BLOCK_BELL, p, 0.9f));
				}
				later(750, () -> snd(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.9f));
			}
			default -> snd(SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.6f);
		}
	}

	/** Проба звука по ключу (экран настроек). */
	public static void previewKey(String key) {
		switch (key) {
			case "HOVER" -> preview(Event.HOVER);
			case "SELECT" -> preview(Event.SELECT);
			case "CLICK" -> preview(Event.CLICK);
			case "WEAR" -> preview(Event.WEAR);
			case "ANIM" -> previewAnimation();
			default -> casino(3);
		}
	}

	// ------------------------------------------------------------------ 1) звуки анимаций

	private static void animation(CardStyle style, int index) {
		K = "ANIM";
		if (gameAnimation(dev.skinlibrary.screen.Theme.current().id(), index)) return; // темы по мотивам игр звучат по-своему
		switch (style) {
			case SHELF: // перелистывание страниц
				snd(SoundEvents.BOOK_PAGE_TURN, rnd(1.0f, 0.25f), 0.8f);
				break;
			case FAN: // стук деревянных планок веера
				snd(SoundEvents.WOODEN_BUTTON_CLICK_ON, rnd(1.5f, 0.2f), 0.7f);
				later(50, () -> snd(SoundEvents.WOODEN_BUTTON_CLICK_ON, rnd(1.7f, 0.2f), 0.6f));
				later(100, () -> snd(SoundEvents.WOODEN_BUTTON_CLICK_ON, rnd(1.35f, 0.2f), 0.5f));
				break;
			case WAVE: // перелив: нота по номеру карточки
				snd(SoundEvents.AMETHYST_BLOCK_CHIME, SCALE[Math.floorMod(index, SCALE.length)], 0.6f);
				break;
			default: // карусель: щелчок механизма
				snd(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, rnd(1.15f, 0.1f), 0.7f);
				later(50, () -> snd(SoundEvents.LEVER_CLICK, 1.5f, 0.35f));
		}
	}


	/** Звуки смены карточки для тем (игры, полные и цветные) (из стандартных звуков игры, без внешних файлов). */
	private static boolean gameAnimation(String id, int index) {
		float n = SCALE[Math.floorMod(index, SCALE.length)];
		switch (id) {
			case "terraria": // копание: земля и подбор предмета
				snd(SoundEvents.GRASS_STEP, rnd(1.1f, 0.3f), 0.9f);
				later(60, () -> snd(SoundEvents.ITEM_PICKUP, rnd(1.4f, 0.2f), 0.35f));
				return true;
			case "stardew": // бодрый перезвон
				snd(SoundEvents.NOTE_BLOCK_CHIME, n, 0.6f);
				later(70, () -> snd(SoundEvents.NOTE_BLOCK_XYLOPHONE, n * 1.26f > 2f ? 2f : n * 1.26f, 0.35f));
				return true;
			case "undertale": // «печатающие» блипы
				snd(SoundEvents.NOTE_BLOCK_BIT, 1.19f, 0.4f);
				later(55, () -> snd(SoundEvents.NOTE_BLOCK_BIT, 1.59f, 0.4f));
				return true;
			case "hollow": // тихий хрустальный отзвук пещер
				snd(SoundEvents.AMETHYST_BLOCK_RESONATE, rnd(0.7f, 0.1f), 0.7f);
				return true;
			case "celeste": // восходящая нота и рывок
				snd(SoundEvents.AMETHYST_BLOCK_CHIME, n, 0.6f);
				later(80, () -> snd(SoundEvents.NOTE_BLOCK_CHIME, Math.min(2f, n * 1.5f), 0.4f));
				return true;
			case "hades": // лира и тяжёлый удар
				snd(SoundEvents.NOTE_BLOCK_HARP, 0.84f, 0.6f);
				later(90, () -> snd(SoundEvents.NOTE_BLOCK_BASS, 0.7f, 0.5f));
				return true;
			case "cuphead": // джазовое банжо
				snd(SoundEvents.NOTE_BLOCK_BANJO, n, 0.6f);
				later(70, () -> snd(SoundEvents.NOTE_BLOCK_SNARE, 1.4f, 0.3f));
				return true;
			case "dontstarve": // нервная гитара
				snd(SoundEvents.NOTE_BLOCK_GUITAR, n * 0.75f, 0.7f);
				later(80, () -> snd(SoundEvents.NOTE_BLOCK_DIDGERIDOO, 1.2f, 0.25f));
				return true;
			case "hotline": // синтвейв: бас и удар
				snd(SoundEvents.NOTE_BLOCK_BASEDRUM, 1.0f, 0.8f);
				later(60, () -> snd(SoundEvents.NOTE_BLOCK_PLING, n, 0.45f));
				return true;
			case "deadcells": // кованое железо и цепь
				snd(SoundEvents.CHAIN_PLACE, rnd(1.0f, 0.2f), 0.9f);
				later(60, () -> snd(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE, 0.8f, 0.35f));
				return true;
			case "cult": // низкий колокол ритуала
				snd(SoundEvents.NOTE_BLOCK_BELL, 0.6f, 0.7f);
				later(100, () -> snd(SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.45f));
				return true;
			case "balatro": // карта: шорох и звонкая нота
				snd(SoundEvents.BOOK_PAGE_TURN, rnd(1.3f, 0.2f), 0.8f);
				later(70, () -> snd(SoundEvents.NOTE_BLOCK_XYLOPHONE, n, 0.4f));
				return true;
			case "slay": // карта и блок щитом
				snd(SoundEvents.BOOK_PAGE_TURN, rnd(0.9f, 0.15f), 0.7f);
				later(70, () -> snd(SoundEvents.SHIELD_BLOCK, rnd(1.2f, 0.2f), 0.45f));
				return true;
			case "amongus": // щелчок панели и сигнал задания
				snd(SoundEvents.UI_BUTTON_CLICK, 1.2f, 0.6f);
				later(60, () -> snd(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE, n, 0.4f));
				return true;
			// ---- полные темы
			case "cyber": return duo(SoundEvents.NOTE_BLOCK_BIT, 1.5f, 0.4f, SoundEvents.NOTE_BLOCK_PLING, n, 0.35f, 50);
			case "space": return duo(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 0.6f, SoundEvents.NOTE_BLOCK_CHIME, n * 0.8f, 0.3f, 90);
			case "winter": return duo(SoundEvents.AMETHYST_BLOCK_CHIME, 1.7f, 0.5f, SoundEvents.SNOW_STEP, rnd(1.2f, 0.2f), 0.5f, 60);
			case "synthwave": return duo(SoundEvents.NOTE_BLOCK_PLING, n, 0.5f, SoundEvents.NOTE_BLOCK_BASS, 0.7f, 0.5f, 60);
			case "ocean": return duo(SoundEvents.NOTE_BLOCK_FLUTE, n, 0.5f, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.3f, 80);
			case "tavern": return duo(SoundEvents.WOODEN_BUTTON_CLICK_ON, rnd(1.0f, 0.2f), 0.7f, SoundEvents.NOTE_BLOCK_BANJO, n * 0.8f, 0.4f, 60);
			case "arcade": return duo(SoundEvents.NOTE_BLOCK_BIT, n, 0.45f, SoundEvents.NOTE_BLOCK_BIT, Math.min(2f, n * 1.5f), 0.4f, 50);
			case "void": return duo(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 0.7f, SoundEvents.NOTE_BLOCK_BASS, 0.5f, 0.4f, 100);
			case "lava": return duo(SoundEvents.LAVA_POP, rnd(1.0f, 0.3f), 0.8f, SoundEvents.NOTE_BLOCK_BASEDRUM, 0.8f, 0.5f, 50);
			case "notebook": return duo(SoundEvents.BOOK_PAGE_TURN, rnd(1.1f, 0.2f), 0.7f, SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.8f, 0.3f, 60);
			case "sky": return duo(SoundEvents.NOTE_BLOCK_CHIME, n, 0.5f, SoundEvents.NOTE_BLOCK_HARP, Math.min(2f, n * 1.26f), 0.35f, 80);
			// ---- цветные темы
			case "ps": return duo(SoundEvents.NOTE_BLOCK_BELL, n, 0.5f, SoundEvents.NOTE_BLOCK_CHIME, Math.min(2f, n * 1.5f), 0.3f, 70);
			case "neon": return duo(SoundEvents.NOTE_BLOCK_PLING, n, 0.5f, SoundEvents.NOTE_BLOCK_BIT, 1.6f, 0.25f, 50);
			case "sunset": return duo(SoundEvents.NOTE_BLOCK_HARP, n * 0.9f, 0.55f, SoundEvents.NOTE_BLOCK_FLUTE, 0.9f, 0.3f, 90);
			case "forest": return duo(SoundEvents.GRASS_STEP, rnd(1.2f, 0.2f), 0.7f, SoundEvents.NOTE_BLOCK_FLUTE, n, 0.35f, 70);
			case "sakura": return duo(SoundEvents.NOTE_BLOCK_CHIME, Math.min(2f, n * 1.26f), 0.5f, SoundEvents.NOTE_BLOCK_HARP, 1.8f, 0.3f, 80);
			case "mono": return duo(SoundEvents.UI_BUTTON_CLICK, 1.0f, 0.6f, SoundEvents.NOTE_BLOCK_HAT, 1.2f, 0.3f, 50);
			case "mint": return duo(SoundEvents.NOTE_BLOCK_CHIME, 1.6f, 0.45f, SoundEvents.AMETHYST_BLOCK_CHIME, n, 0.3f, 70);
			case "lavender": return duo(SoundEvents.NOTE_BLOCK_HARP, 1.5f, 0.45f, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.3f, 90);
			case "burgundy": return duo(SoundEvents.NOTE_BLOCK_BASS, 0.8f, 0.6f, SoundEvents.NOTE_BLOCK_BELL, 0.7f, 0.35f, 80);
			case "amber": return duo(SoundEvents.NOTE_BLOCK_BANJO, n * 0.85f, 0.55f, SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.3f, 0.3f, 60);
			case "graphite": return duo(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE, n * 0.8f, 0.5f, SoundEvents.UI_BUTTON_CLICK, 1.4f, 0.3f, 50);
			case "indigo": return duo(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f, SoundEvents.NOTE_BLOCK_HARP, n * 0.8f, 0.3f, 90);
			case "turquoise": return duo(SoundEvents.NOTE_BLOCK_BELL, n, 0.45f, SoundEvents.NOTE_BLOCK_FLUTE, 1.2f, 0.3f, 80);
			case "olive": return duo(SoundEvents.NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.4f, SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.1f, 0.5f, 60);
			case "coffee": return duo(SoundEvents.NOTE_BLOCK_GUITAR, n * 0.7f, 0.6f, SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.9f, 0.4f, 70);
			case "rose": return duo(SoundEvents.NOTE_BLOCK_CHIME, n, 0.5f, SoundEvents.NOTE_BLOCK_HARP, 1.6f, 0.3f, 80);
			case "ice": return duo(SoundEvents.AMETHYST_BLOCK_CHIME, 1.9f, 0.45f, SoundEvents.SNOW_STEP, rnd(1.4f, 0.2f), 0.4f, 60);
			case "vanilla": return duo(SoundEvents.NOTE_BLOCK_XYLOPHONE, n, 0.45f, SoundEvents.NOTE_BLOCK_CHIME, 1.3f, 0.3f, 70);
			default:
				return themeAnimation(id, n);
		}
	}

	/** Две ноты подряд: быстрый способ описать звук темы. */
	private static void pair(Object a, float pa, float va, Object b, float pb, float vb) {
		snd(a, pa, va);
		later(70, () -> snd(b, pb, vb));
	}

	/** Звуки смены карточки для обычных (полных и цветных) тем. */
	private static boolean themeAnimation(String id, float n) {
		float hi = Math.min(2f, n * 1.26f);
		switch (id) {
			// полные темы
			case "cyber" -> pair(SoundEvents.NOTE_BLOCK_PLING, n, 0.45f, SoundEvents.NOTE_BLOCK_HAT, 1.6f, 0.35f);
			case "space" -> pair(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 0.6f, SoundEvents.NOTE_BLOCK_CHIME, n, 0.35f);
			case "winter" -> pair(SoundEvents.SNOW_STEP, rnd(1.1f, 0.2f), 0.8f, SoundEvents.AMETHYST_BLOCK_CHIME, hi, 0.4f);
			case "synthwave" -> pair(SoundEvents.NOTE_BLOCK_BASS, 0.8f, 0.6f, SoundEvents.NOTE_BLOCK_PLING, n, 0.4f);
			case "ocean" -> pair(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, rnd(1.2f, 0.4f), 0.7f, SoundEvents.NOTE_BLOCK_FLUTE, n, 0.35f);
			case "tavern" -> pair(SoundEvents.BARREL_OPEN, rnd(1.2f, 0.2f), 0.5f, SoundEvents.NOTE_BLOCK_GUITAR, n * 0.8f, 0.45f);
			case "arcade" -> pair(SoundEvents.NOTE_BLOCK_BIT, n, 0.4f, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f, 0.4f);
			case "void" -> pair(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 0.7f, SoundEvents.NOTE_BLOCK_BASS, 0.55f, 0.5f);
			case "lava" -> pair(SoundEvents.LAVA_POP, rnd(1.0f, 0.3f), 0.8f, SoundEvents.NOTE_BLOCK_BASS, 0.7f, 0.4f);
			case "notebook" -> pair(SoundEvents.BOOK_PAGE_TURN, rnd(1.2f, 0.2f), 0.7f, SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.9f, 0.35f);
			case "sky" -> pair(SoundEvents.NOTE_BLOCK_FLUTE, n, 0.45f, SoundEvents.NOTE_BLOCK_CHIME, hi, 0.35f);
			// цветные темы
			case "ps" -> pair(SoundEvents.NOTE_BLOCK_PLING, n, 0.4f, SoundEvents.NOTE_BLOCK_BELL, hi, 0.3f);
			case "neon" -> pair(SoundEvents.NOTE_BLOCK_PLING, hi, 0.4f, SoundEvents.NOTE_BLOCK_BIT, n, 0.3f);
			case "sunset" -> pair(SoundEvents.NOTE_BLOCK_HARP, n * 0.8f, 0.6f, SoundEvents.NOTE_BLOCK_BASS, 0.9f, 0.3f);
			case "forest" -> pair(SoundEvents.GRASS_STEP, rnd(1.0f, 0.3f), 0.8f, SoundEvents.NOTE_BLOCK_FLUTE, n, 0.3f);
			case "sakura" -> pair(SoundEvents.NOTE_BLOCK_CHIME, hi, 0.5f, SoundEvents.NOTE_BLOCK_HARP, n, 0.35f);
			case "mono" -> pair(SoundEvents.UI_BUTTON_CLICK, 1.1f, 0.6f, SoundEvents.NOTE_BLOCK_HAT, 1.2f, 0.3f);
			case "mint" -> pair(SoundEvents.NOTE_BLOCK_XYLOPHONE, n, 0.4f, SoundEvents.NOTE_BLOCK_CHIME, hi, 0.3f);
			case "lavender" -> pair(SoundEvents.NOTE_BLOCK_HARP, n, 0.5f, SoundEvents.AMETHYST_BLOCK_CHIME, hi, 0.35f);
			case "burgundy" -> pair(SoundEvents.NOTE_BLOCK_BASS, 0.75f, 0.6f, SoundEvents.NOTE_BLOCK_BELL, 0.8f, 0.35f);
			case "amber" -> pair(SoundEvents.NOTE_BLOCK_GUITAR, n, 0.55f, SoundEvents.NOTE_BLOCK_HARP, hi, 0.3f);
			case "graphite" -> pair(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE, n * 0.7f, 0.45f, SoundEvents.UI_BUTTON_CLICK, 0.8f, 0.4f);
			case "indigo" -> pair(SoundEvents.NOTE_BLOCK_BELL, n * 0.8f, 0.5f, SoundEvents.NOTE_BLOCK_FLUTE, n, 0.3f);
			case "turquoise" -> pair(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, rnd(1.3f, 0.3f), 0.7f, SoundEvents.NOTE_BLOCK_XYLOPHONE, n, 0.35f);
			case "olive" -> pair(SoundEvents.NOTE_BLOCK_BANJO, n * 0.8f, 0.5f, SoundEvents.GRASS_STEP, 1.0f, 0.5f);
			case "coffee" -> pair(SoundEvents.WOODEN_BUTTON_CLICK_ON, rnd(1.1f, 0.2f), 0.6f, SoundEvents.NOTE_BLOCK_GUITAR, n * 0.8f, 0.4f);
			case "rose" -> pair(SoundEvents.NOTE_BLOCK_CHIME, n, 0.5f, SoundEvents.AMETHYST_BLOCK_CHIME, hi, 0.35f);
			case "ice" -> pair(SoundEvents.AMETHYST_BLOCK_CHIME, hi, 0.5f, SoundEvents.SNOW_STEP, rnd(1.2f, 0.2f), 0.5f);
			case "vanilla" -> pair(SoundEvents.NOTE_BLOCK_HARP, n, 0.45f, SoundEvents.NOTE_BLOCK_CHIME, hi, 0.3f);
			// анимированная классика
			case "classic_anim" -> pair(SoundEvents.UI_BUTTON_CLICK, rnd(1.2f, 0.2f), 0.6f, SoundEvents.NOTE_BLOCK_HAT, 1.5f, 0.3f);
			case "classic_dark_anim" -> pair(SoundEvents.UI_BUTTON_CLICK, rnd(0.9f, 0.2f), 0.6f, SoundEvents.NOTE_BLOCK_BASEDRUM, 1.2f, 0.3f);
			default -> { return false; }
		}
		return true;
	}

	/** Два звука подряд (второй через delay мс) — основа «характера» темы. */
	private static boolean duo(Object a, float pa, float va, Object b, float pb, float vb, int delay) {
		snd(a, pa, va);
		later(delay, () -> snd(b, pb, vb));
		return true;
	}

	// ------------------------------------------------------------------ 2) звуковые темы

	private static void themed(Event e, int index) {
		if (!SkinStore.sound()) return;
		K = e.name();
		switch (Pack.current()) {
			case CRYSTAL:
				switch (e) {
					case HOVER -> snd(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.22f);
					case SELECT -> snd(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.5f);
					case CLICK -> snd(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
					default -> {
						snd(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.7f);
						later(100, () -> snd(SoundEvents.AMETHYST_BLOCK_CHIME, 1.7f, 0.6f));
					}
				}
				break;
			case MELODY:
				switch (e) {
					case HOVER -> snd(SoundEvents.NOTE_BLOCK_HARP, 1.68f, 0.2f);
					case SELECT -> snd(SoundEvents.NOTE_BLOCK_HARP, SCALE[Math.floorMod(index, SCALE.length)], 0.5f);
					case CLICK -> snd(SoundEvents.NOTE_BLOCK_BELL, 1.2f, 0.5f);
					default -> {
						snd(SoundEvents.NOTE_BLOCK_HARP, 1.0f, 0.6f);
						later(100, () -> snd(SoundEvents.NOTE_BLOCK_HARP, 1.26f, 0.6f));
						later(200, () -> snd(SoundEvents.NOTE_BLOCK_HARP, 1.5f, 0.7f));
					}
				}
				break;
			case RETRO:
				switch (e) {
					case HOVER -> snd(SoundEvents.NOTE_BLOCK_BIT, 1.8f, 0.2f);
					case SELECT -> snd(SoundEvents.NOTE_BLOCK_BIT, 1.5f, 0.35f);
					case CLICK -> snd(SoundEvents.NOTE_BLOCK_BIT, 1.0f, 0.45f);
					default -> {
						snd(SoundEvents.NOTE_BLOCK_BIT, 1.0f, 0.5f);
						later(100, () -> snd(SoundEvents.NOTE_BLOCK_BIT, 1.26f, 0.5f));
						later(200, () -> snd(SoundEvents.NOTE_BLOCK_BIT, 1.68f, 0.55f));
					}
				}
				break;
			case CUSTOM:
				if (CustomSounds.has(e)) {
					if (SkinStore.soundEnabled(K)) CustomSounds.play(e, SkinStore.volume() * SkinStore.soundVolume(K));
					break;
				}
				classic(e);
				break;
			default:
				classic(e);
		}
	}

	private static void classic(Event e) {
		switch (e) {
			case HOVER -> snd(SoundEvents.UI_BUTTON_CLICK, 1.9f, 0.30f);
			case SELECT -> snd(SoundEvents.UI_BUTTON_CLICK, 1.35f, 0.55f);
			case CLICK -> snd(SoundEvents.UI_BUTTON_CLICK, 1.0f, 0.8f);
			default -> snd(SoundEvents.UI_BUTTON_CLICK, 0.75f, 1.0f);
		}
	}
}
