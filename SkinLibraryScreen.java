package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.FileDialogs;
import dev.skinlibrary.Input;
import dev.skinlibrary.Pose;
import dev.skinlibrary.Sfx;
import dev.skinlibrary.SkinEntry;
import dev.skinlibrary.SkinLibraryClient;
import dev.skinlibrary.SkinPreview;
import dev.skinlibrary.SkinPreviewCache;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Библиотека скинов: поиск, папки, избранное, сортировка, четыре стиля анимации карточек,
 * вращение модели мышью, позы, звуки, импорт/экспорт.
 * Кнопки действий (избранное, правка, надеть, удалить) стоят на месте под полкой и не зависят от анимаций.
 */
public class SkinLibraryScreen extends ThemedScreen {
	private static final int CW = 112, CH = 178;
	private static final int STEP = 78;
	private static final float PUSH = 30f;
	private static final float LEAN_DEG = 8f;
	private static final float MIN_SCALE = 0.80f, MAX_SCALE = 1.07f;
	private static final float RISE = 20f;
	private static final String FAV = "\u2605";
	private static final String[] SORT_LABELS = {"Сорт: как есть", "Сорт: по имени", "Сорт: \u2605 выше"};
	private static final int ROW_W = 294;

	// панель папок слева
	private static final int PANEL_X = 4, PANEL_W = 92, PANEL_Y = 80;
	private static final int TW = 84, TH = 78, TGAP = 4;
	private static final String NEW_FOLDER = "\u0000new";

	private record Tile(String key, String label, int count, List<SkinEntry> sample, int kind) {}

	private static int lastSel = 0;
	private static SkinEntry lastEntry; // выбранный скин: после перестройки списка (сортировка, правка) выбор остаётся на нём
	private static String searchText = "";
	private static String filter = "";
	private static int sortMode = 0;

	private final Screen parent;
	private final List<SkinEntry> view = new ArrayList<>();
	private Button wearBtn, delBtn, starBtn, editBtn, diceBtn;
	private int rowX, rowY, rowW = 280;
	private boolean cardPress, cardWasDown;
	private int cardPressX, cardPressY;
	private static final java.util.ArrayDeque<Long> THEME_CLICKS = new java.util.ArrayDeque<>();
	private float shelfCx;

	private final List<Tile> tiles = new ArrayList<>();
	private float[] tileHover = new float[0];
	private float folderScroll = 0f, folderScrollTarget = 0f;
	private int folderPress = -1;
	private boolean folderWasDown;
	private Runnable pendingAction;
	// перетаскивание карточки на папку
	private boolean carryCandidate, carrying;
	private SkinEntry carryEntry;
	private int dropTile = -1;
	private Button folderBtn, sunBtn;

	private long lastNano = System.nanoTime();
	private int selTarget = 0, prevSel = -1;
	private float selPos = 0f, selVel = 0f;
	private float scrollTarget = 0f, scrollPos = 0f;

	private CardStyle style = CardStyle.SHELF;
	private float[] xs = new float[0], ys = new float[0], angs = new float[0];
	private float[] scXs = new float[0], scYs = new float[0], eases = new float[0];
	private float baseY, unit = 1f;

	private String toastText = "";
	private long toastAt = 0;

	public SkinLibraryScreen(Screen parent) {
		super(Component.literal(I18n.t("Библиотека скинов")));
		this.parent = parent;
	}

	/** Открыть библиотеку с фильтром по папке (используется экраном папок). */
	public static void openFilter(String folder) {
		filter = folder;
		lastSel = 0;
		lastEntry = null;
	}

	// ------------------------------------------------------------------ данные

	private SkinEntry cur() {
		return selTarget >= 0 && selTarget < view.size() ? view.get(selTarget) : null;
	}

	private void toast(String s) {
		toastText = s;
		toastAt = System.nanoTime();
	}

	private void refreshView() {
		if (!filter.isEmpty() && !filter.equals(FAV) && !SkinStore.folderExists(filter)) filter = ""; // папку переименовали или удалили
		rebuildTiles();
		SkinEntry keep = cur();
		if (keep == null) keep = lastEntry; // экран только что открыт: вернуть выбор на прежний скин
		view.clear();
		String q = searchText.trim().toLowerCase();
		for (SkinEntry e : SkinStore.skins()) {
			String nm = e.name == null ? "" : e.name.toLowerCase();
			String tg = e.tag == null ? "" : e.tag.toLowerCase();
			if (!q.isEmpty() && !nm.contains(q) && !tg.contains(q)) continue;
			if (filter.equals(FAV)) {
				if (!e.favorite) continue;
			} else if (!filter.isEmpty() && !filter.equalsIgnoreCase(e.tag == null ? "" : e.tag.trim())) {
				continue;
			}
			view.add(e);
		}
		if (sortMode == 1) view.sort(Comparator.comparing(e -> e.name == null ? "" : e.name.toLowerCase()));
		else if (sortMode == 2) view.sort(Comparator.comparing(e -> !e.favorite));

		int idx = keep == null ? -1 : view.indexOf(keep);
		selTarget = idx >= 0 ? idx : Math.max(0, Math.min(selTarget, view.size() - 1));
		lastSel = selTarget;
		lastEntry = cur();
		if (view.size() > 0) scrollTarget = Math.max(0, Math.min(view.size() - 1, scrollTarget));
	}

	private void computeBase() {
		float top = 82, bottom = this.height - 92;
		unit = Math.max(0.5f, Math.min(1f, (bottom - top) / (CH * MAX_SCALE + RISE + 8)));
		baseY = (top + bottom) / 2f + CH * unit * 0.5f + 6;
		shelfCx = (PANEL_X + PANEL_W + this.width) / 2f;
	}

	// ------------------------------------------------------------------ панель папок

	private List<SkinEntry> first3(List<SkinEntry> src) {
		return new ArrayList<>(src.subList(0, Math.min(3, src.size())));
	}

	private void rebuildTiles() {
		tiles.clear();
		List<SkinEntry> all = SkinStore.skins();
		tiles.add(new Tile("", I18n.t("Все скины"), all.size(), first3(all), 0));
		List<SkinEntry> fav = new ArrayList<>();
		for (SkinEntry e : all) if (e.favorite) fav.add(e);
		tiles.add(new Tile(FAV, I18n.t("Избранное"), fav.size(), first3(fav), 1));
		for (String f : SkinStore.folders()) {
			List<SkinEntry> in = new ArrayList<>();
			for (SkinEntry e : all) if (e.tag != null && e.tag.trim().equalsIgnoreCase(f)) in.add(e);
			tiles.add(new Tile(f, f, in.size(), first3(in), 2));
		}
		tiles.add(new Tile(NEW_FOLDER, I18n.t("Новая папка"), 0, List.of(), 3));
		if (tileHover.length != tiles.size()) tileHover = new float[tiles.size()];
	}

	private int panelH() {
		return Math.max(60, this.height - 80 - PANEL_Y);
	}

	private int tileTop(int i) {
		return PANEL_Y + 14 + i * (TH + TGAP) - Math.round(folderScroll);
	}

	private int tileAt(int mx, int my) {
		if (mx < PANEL_X || mx >= PANEL_X + PANEL_W || my < PANEL_Y + 12 || my >= PANEL_Y + panelH()) return -1;
		for (int i = 0; i < tiles.size(); i++) {
			int ty = tileTop(i);
			if (my >= ty && my < ty + TH) return i;
		}
		return -1;
	}

	private void folderInput(int mx, int my, float dt) {
		int content = tiles.size() * (TH + TGAP);
		float maxScroll = Math.max(0, content - (panelH() - 18));
		folderScrollTarget = clamp(folderScrollTarget, 0, maxScroll);
		if (th().classic()) folderScroll = folderScrollTarget;
		else folderScroll += (folderScrollTarget - folderScroll) * (1f - (float) Math.exp(-dt * 12f));

		boolean down = Input.leftDown();
		int over = tileAt(mx, my);
		dropTile = carrying ? over : -1;
		if (carrying) folderPress = -1;
		for (int i = 0; i < tileHover.length; i++) {
			if (th().classic()) tileHover[i] = i == over ? 1f : 0f;
			else tileHover[i] += ((i == over ? 1f : 0f) - tileHover[i]) * (1f - (float) Math.exp(-dt * 14f));
		}
		if (down && !folderWasDown && over >= 0) folderPress = over;
		if (!down) {
			if (folderWasDown && folderPress >= 0 && over == folderPress) activateTile(folderPress);
			folderPress = -1;
		}
		folderWasDown = down;
	}

	private void activateTile(int i) {
		Tile t = tiles.get(i);
		Sfx.click();
		if (t.kind() == 3) {
			pendingAction = () -> this.minecraft.setScreen(new FoldersScreen(this));
			return;
		}
		filter = t.key();
		lastSel = 0;
		lastEntry = null;
		selTarget = 0;
		scrollTarget = 0;
		refreshView();
	}

	@Override
	public void tick() {
		super.tick();
		if (pendingAction != null) {
			Runnable r = pendingAction;
			pendingAction = null;
			r.run();
		}
	}

	// ------------------------------------------------------------------ интерфейс

	@Override
	protected void init() {
		dev.skinlibrary.SkinLibraryClient.refreshNicks(); // скины, добавленные по нику, подтягивают смену скина игрока
		view.clear();
		int cx = this.width / 2;
		computeBase();

		addBackground();
		addTitle(I18n.t("Библиотека скинов"));
		this.addRenderableOnly((g, mx, my, d) -> titleSecret(mx, my));
		fancy(() -> "?", this.width - 28, 6, 22, 20, b -> this.minecraft.setScreen(new GuideScreen(this)), I18n.t("Гайд по моду"), 1);
		this.addRenderableOnly((g, mx, my, d) -> drawShelf(g));
		this.addRenderableOnly((g, mx, my, d) -> drawFolderPanel(g));

		// ряд 1
		int bw = Math.min(98, (this.width - 24) / 4), gap = 4;
		int x1 = (this.width - (4 * bw + 3 * gap)) / 2;
		fancy(() -> I18n.t("+ Добавить"), x1, 30, bw, 20,
				b -> this.minecraft.setScreen(new AddSkinScreen(this)), I18n.t("Вставить команду SkinRestorer"), 1);
		fancy(() -> I18n.t("Генератор"), x1 + (bw + gap), 30, bw, 20,
				b -> this.minecraft.setScreen(new GeneratorScreen(this)), I18n.t("Создать команду из PNG или ссылки"), 0);
		fancy(() -> I18n.t("По нику"), x1 + 2 * (bw + gap), 30, bw, 20,
				b -> this.minecraft.setScreen(new NickScreen(this)), I18n.t("Взять скин игрока по нику"), 0);
		fancy(() -> I18n.t("Импорт..."), x1 + 3 * (bw + gap), 30, bw, 20,
				b -> this.minecraft.setScreen(new ImportScreen(this)),
				I18n.t("Аккаунт, лаунчеры (Prism, Modrinth App...) и папки"), 0);

		// ряд 2: поиск, сортировка, поза (папки выбираются на панели слева)
		int[] ws = {150, 100, 90};
		int total = ws[0] + ws[1] + ws[2] + 8;
		float f = Math.min(1f, (this.width - 12f) / total);
		for (int i = 0; i < 3; i++) ws[i] = Math.round(ws[i] * f);
		int g2 = Math.round(4 * f);
		int x2 = (this.width - (ws[0] + ws[1] + ws[2] + 2 * g2)) / 2;

		EditBox search = new EditBox(this.font, x2, 54, ws[0], 20, Component.literal(I18n.t("Поиск")));
		search.setMaxLength(40);
		search.setHint(Component.literal(I18n.t("Поиск...")));
		search.setValue(searchText);
		search.setResponder(s -> {
			searchText = s;
			refreshView();
		});
		this.addRenderableWidget(search);
		glow(search);

		int xx = x2 + ws[0] + g2;
		fancy(() -> I18n.t(SORT_LABELS[sortMode]), xx, 54, ws[1], 20, b -> {
			sortMode = (sortMode + 1) % SORT_LABELS.length;
			refreshView();
		}, I18n.t("Порядок скинов на полке"), 0);
		xx += ws[1] + g2;
		fancy(() -> I18n.t("Поза: ") + I18n.t(Pose.current().label), xx, 54, ws[2], 20,
				b -> SkinStore.setPoseId(Pose.next().id), I18n.t("Поза модели. Модель можно вращать мышью"), 0);

		// низ
		fancy(() -> "<", cx - 84, this.height - 26, 36, 20,
				b -> scrollTarget = Math.max(0, scrollTarget - 2), null, 0);
		fancy(() -> I18n.t("Назад"), cx - 44, this.height - 26, 88, 20, b -> this.onClose(), null, 0);
		fancy(() -> ">", cx + 48, this.height - 26, 36, 20,
				b -> scrollTarget = Math.min(Math.max(0, view.size() - 1), scrollTarget + 2), null, 0);

		fancy(() -> { // длинное название: без слова «Тема», чтобы влезло; полное — в подсказке
			String nm = I18n.t(Theme.current().name()), full = I18n.t("Тема: ") + nm;
			return this.font.width(full) <= 104 ? full : nm;
		}, 6, this.height - 26, 112, 20,
				b -> this.minecraft.setScreen(new ThemeScreen(this)), I18n.t("Галерея тем интерфейса"), 0);
		fancy(() -> I18n.t("Из файла"), 6, this.height - 50, 54, 20, b -> doImport(), I18n.t("Загрузить библиотеку из JSON"), 0);
		fancy(() -> I18n.t("В файл"), 64, this.height - 50, 54, 20, b -> doExport(), I18n.t("Сохранить библиотеку в JSON"), 0);
		fancy(() -> I18n.t("Папки..."), 6, this.height - 74, 112, 20,
				b -> this.minecraft.setScreen(new FoldersScreen(this)), I18n.t("Создать, переименовать, удалить папки"), 0);

		fancy(() -> I18n.t("Анимация: ") + I18n.t(CardStyle.current().label), this.width - 118, this.height - 26, 112, 20,
				b -> SkinStore.setAnimationId(CardStyle.next().id), I18n.t("Сменить стиль анимации карточек"), 0);
		fancy(() -> I18n.t("Звуки..."), this.width - 118, this.height - 50, 112, 20,
				b -> this.minecraft.setScreen(new SoundScreen(this)), I18n.t("Звуки анимаций, темы звуков, свои звуки"), 0);
		fancy(() -> I18n.t("Настройки..."), this.width - 118, this.height - 74, 112, 20,
				b -> this.minecraft.setScreen(new SettingsScreen(this)),
				I18n.t("Способ надевания, автонадевание, превью в меню"), 0);

		// кнопки действий: фиксированный ряд под полкой, не двигается с анимацией
		boolean showWear = !SkinStore.wearMode().equals("card");
		rowW = showWear ? ROW_W : 152;
		rowX = Math.round(shelfCx - rowW / 2f);
		rowY = Math.min(Math.round(baseY) + 38, this.height - 52);
		starBtn = fancy(() -> {
			SkinEntry e = cur();
			return e != null && e.favorite ? FAV : "\u2606";
		}, rowX, rowY, 22, 20, b -> {
			SkinEntry e = cur();
			if (e == null) return;
			e.favorite = !e.favorite;
			SkinStore.save();
			refreshView();
		}, I18n.t("Избранное"), 0);
		editBtn = fancy(() -> "\u270E", rowX + 26, rowY, 22, 20, b -> {
			SkinEntry e = cur();
			if (e != null) this.minecraft.setScreen(new EditSkinScreen(this, e));
		}, I18n.t("Название, папка и плащ"), 0);
		folderBtn = fancy(() -> "\u25A4", rowX + 52, rowY, 22, 20, b -> moveToNextFolder(),
				I18n.t("Переложить скин в следующую папку"), 0);
		diceBtn = fancy(() -> "\u2684", rowX + 78, rowY, 22, 20, b -> wearRandom(),
				I18n.t("Случайный скин из текущего списка"), 0);
		sunBtn = fancy(() -> "\u2600", rowX + 104, rowY, 22, 20, b -> wearDaily(),
				I18n.t("Скин дня: один и тот же в течение суток"), 0);
		int nx = rowX + 130;
		if (showWear) {
			wearBtn = fancy(() -> {
				int cd = SkinLibraryClient.cooldownLeft();
				if (cd > 0) return I18n.t("Подожди ") + cd + I18n.t(" с");
				SkinEntry e = cur();
				if (e == null || e.name == null) return I18n.t("\u25B6 Надеть");
				String n = e.name.length() > 11 ? e.name.substring(0, 11) + "…" : e.name;
				return I18n.t("\u25B6 Надеть: ") + n;
			}, nx, rowY, 138, 20, b -> wearSelected(), I18n.t("Надеть скин (отправит команду на сервер)"), 1);
			nx += 142;
		} else {
			wearBtn = null;
		}
		delBtn = fancy(() -> "x", nx, rowY, 22, 20, b -> {
			SkinEntry e = cur();
			if (e == null) return;
			SkinStore.remove(e);
			boolean used = SkinStore.skins().stream().anyMatch(o -> java.util.Objects.equals(o.source(), e.source()));
			if (!used) SkinPreviewCache.evict(e.source());
			refreshView();
		}, I18n.t("Удалить из библиотеки"), 2);
		cardWasDown = Input.leftDown();
		folderWasDown = cardWasDown;

		selTarget = Math.max(0, lastSel);
		refreshView();
		selPos = selTarget;
		selVel = 0f;
		scrollTarget = selTarget;
		scrollPos = selTarget;
		prevSel = selTarget;
	}

	/** Скин дня: выбирает на полке сегодняшний скин (из выбранной в настройках папки) и надевает его. */
	private void wearDaily() {
		SkinEntry e = dev.skinlibrary.AutoWear.dailyPick(SkinStore.randomPool());
		if (e == null) {
			toast(I18n.t("Библиотека пуста"));
			return;
		}
		int idx = view.indexOf(e);
		if (idx < 0) { // скина нет в текущем фильтре: сбрасываем поиск и папку
			searchText = "";
			filter = "";
			lastEntry = e;
			refreshView();
			idx = view.indexOf(e);
		}
		if (idx >= 0) {
			selTarget = idx;
			lastSel = idx;
			lastEntry = e;
			scrollTarget = idx;
		}
		toast(I18n.t("Скин дня: ") + (e.name == null ? "" : e.name));
		SkinLibraryClient.wear(e, this::toast);
	}

	/** Выбирает случайную карточку из текущего списка и надевает её. */
	private void wearRandom() {
		int n = view.size();
		if (n == 0) return;
		int idx = selTarget;
		if (n > 1) {
			do {
				idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(n);
			} while (idx == selTarget);
		}
		selTarget = idx;
		lastSel = idx;
		lastEntry = idx >= 0 && idx < view.size() ? view.get(idx) : null;
		scrollTarget = idx;
		SkinEntry e = cur();
		if (e == null) return;
		toast(I18n.t("Случайный скин: ") + (e.name == null ? "" : e.name));
		SkinLibraryClient.wear(e, this::toast);
	}

	private void wearSelected() {
		SkinEntry e = cur();
		if (e != null) SkinLibraryClient.wear(e, this::toast);
	}

	private boolean titleWasDown = true;

	/** Пасхалка: пять быстрых кликов по заголовку «Библиотека скинов» открывают автомат. */
	private void titleSecret(int mx, int my) {
		boolean down = Input.leftDown();
		boolean click = down && !titleWasDown;
		titleWasDown = down;
		if (!click) return;
		int half = (int) (this.font.width(I18n.t("Библиотека скинов")) * 0.75f) + 6;
		if (Math.abs(mx - this.width / 2) > half || my < 4 || my > 24) return;
		long now = System.nanoTime();
		THEME_CLICKS.addLast(now);
		while (!THEME_CLICKS.isEmpty() && now - THEME_CLICKS.peekFirst() > 2_500_000_000L) THEME_CLICKS.removeFirst();
		if (THEME_CLICKS.size() >= 5) {
			THEME_CLICKS.clear();
			this.minecraft.setScreen(new SlotMachineScreen(this));
		}
	}

	/**
	 * Клик по выбранной карточке надевает скин (режимы «по карточке» и «оба»). Перетаскивание модели — не клик.
	 * Если потянуть карточку за нижнюю полосу с названием (или за любое место с зажатым Shift),
	 * её можно перенести на папку слева.
	 */
	private void cardClick(int mx, int my, boolean overUi) {
		boolean enabled = !SkinStore.wearMode().equals("button");
		boolean down = Input.leftDown();
		boolean inCard = false, inStrip = false;
		if (cur() != null && selTarget < xs.length && !overUi) {
			float sx = scXs[selTarget], sy = scYs[selTarget];
			int left = Math.round(xs[selTarget] - CW * sx / 2f), top = Math.round(ys[selTarget] - CH * sy);
			inCard = eases[selTarget] > 0.7f && mx >= left && mx < left + Math.round(CW * sx)
					&& my >= top && my < top + Math.round(CH * sy);
			inStrip = inCard && my >= top + Math.round(156 * sy);
		}
		if (down && !cardWasDown) {
			cardPress = inCard;
			cardPressX = mx;
			cardPressY = my;
			carryCandidate = inCard && (inStrip || Input.shiftDown());
		}
		if (down && carryCandidate && !carrying
				&& (Math.abs(mx - cardPressX) > 6 || Math.abs(my - cardPressY) > 6)) {
			carrying = true;
			carryEntry = cur();
			cardPress = false;
		}
		if (!down && cardWasDown) {
			if (carrying) {
				int t = tileAt(mx, my);
				if (t >= 0 && carryEntry != null) dropOnTile(tiles.get(t), carryEntry);
				carrying = false;
				carryEntry = null;
			} else if (cardPress && enabled && inCard && Math.abs(mx - cardPressX) <= 4 && Math.abs(my - cardPressY) <= 4) {
				wearSelected();
			}
			cardPress = false;
			carryCandidate = false;
		}
		cardWasDown = down;
	}

	/** Карточку отпустили над плиткой папки: переносим скин. */
	private void dropOnTile(Tile t, SkinEntry e) {
		switch (t.kind()) {
			case 0 -> {
				e.tag = "";
				toast(I18n.t("Скин вне папок"));
			}
			case 1 -> {
				e.favorite = true;
				toast(I18n.t("Добавлено в избранное"));
			}
			case 2 -> {
				e.tag = t.key();
				toast(I18n.t("В папку: ") + t.key());
			}
			default -> { // «Новая папка»: создаём папку и кладём скин в неё
				int n = SkinStore.folders().size() + 1;
				String name = I18n.t("Папка ") + n;
				while (SkinStore.folderExists(name)) name = I18n.t("Папка ") + (++n);
				SkinStore.createFolder(name);
				e.tag = name;
				toast(I18n.t("Создана папка: ") + name);
			}
		}
		SkinStore.save();
		Sfx.click();
		refreshView();
	}

	private void moveToNextFolder() {
		SkinEntry e = cur();
		if (e == null) return;
		List<String> opts = new ArrayList<>();
		opts.add("");
		opts.addAll(SkinStore.folders());
		int i = 0;
		for (int k = 0; k < opts.size(); k++) if (opts.get(k).equalsIgnoreCase(e.tag == null ? "" : e.tag.trim())) i = k;
		String next = opts.get((i + 1) % opts.size());
		e.tag = next;
		SkinStore.save();
		refreshView();
		toast(next.isEmpty() ? I18n.t("Скин вне папок") : I18n.t("В папку: ") + next);
	}

	private void doExport() {
		FileDialogs.pick(FileDialogs.Mode.SAVE_JSON, p -> {
			if (p == null) return;
			try {
				int n = SkinStore.exportTo(Path.of(p));
				toast(I18n.t("Экспортировано скинов: ") + n);
			} catch (Exception e) {
				toast(I18n.t("Ошибка экспорта"));
			}
		});
	}

	private void doImport() {
		FileDialogs.pick(FileDialogs.Mode.OPEN_JSON, p -> {
			if (p == null) return;
			try {
				int n = SkinStore.importFrom(Path.of(p));
				refreshView();
				toast(n > 0 ? I18n.t("Импортировано скинов: ") + n : I18n.t("Новых скинов нет"));
			} catch (Exception e) {
				toast(I18n.t("Ошибка импорта: неверный файл"));
			}
		});
	}

	// ------------------------------------------------------------------ раскладка

	private float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private float staticX(int i, float cx, float u) {
		float g = i - scrollPos;
		switch (style) {
			case FAN: {
				float t = clamp(g * 0.17f, -0.95f, 0.95f);
				return cx + (float) Math.sin(t) * 300f * u;
			}
			case WAVE:
				return cx + g * STEP * 1.18f * u;
			case FLOW: {
				float gc = clamp(g, -1f, 1f);
				return cx + gc * CW * 0.7f * u + (g - gc) * 30f * u;
			}
			default:
				return cx + g * STEP * u;
		}
	}

	private void layout(int mx, int my, float dt) {
		style = CardStyle.current();
		int n = view.size();
		if (xs.length != n) {
			xs = new float[n];
			ys = new float[n];
			angs = new float[n];
			scXs = new float[n];
			scYs = new float[n];
			eases = new float[n];
		}
		computeBase();
		float cx = shelfCx;
		folderInput(mx, my, dt);

		// вращение модели мышью (по геометрии прошлого кадра)
		if (n > 0 && selTarget < n) {
			float sx = scXs[selTarget], sy = scYs[selTarget];
			int rx = Math.round(xs[selTarget] - CW * sx / 2f + 8 * sx);
			int ry = Math.round(ys[selTarget] - CH * sy + 4 * sy);
			drag.update(mx, my, rx, ry, Math.round(100 * sx), Math.round(150 * sy), eases[selTarget] > 0.5f, false);
		} else {
			drag.update(mx, my, -1, -1, 0, 0, true, true);
		}

		// наведение выбирает карточку — но не над рядом кнопок действий и не во время вращения
		boolean overUi = (mx >= rowX - 8 && mx < rowX + rowW + 8 && my >= rowY - 8 && my < rowY + 28)
				|| mx < PANEL_X + PANEL_W + 2
				|| this.getChildAt(mx, my).isPresent(); // кнопки и поля не должны одновременно выбирать/надевать карточку
		float lowBand = baseY + 14 + (style == CardStyle.FAN ? 90 * unit : 0);
		if (!overUi && !drag.dragging && !carrying && n > 0
				&& my > baseY - (CH * MAX_SCALE + RISE) * unit - 10 && my < Math.min(lowBand, rowY - 10)) {
			int best = -1;
			float bestD = 60f * unit;
			for (int i = 0; i < n; i++) {
				float dd = Math.abs(mx - staticX(i, cx, unit));
				if (dd < bestD) {
					bestD = dd;
					best = i;
				}
			}
			if (best >= 0) {
				selTarget = best;
				lastSel = best;
				lastEntry = view.get(best);
			}
		}
		cardClick(mx, my, overUi);
		if (selTarget != prevSel) {
			if (prevSel >= 0 && n > 0) Sfx.select(selTarget);
			prevSel = selTarget;
		}

		if (n > 0 && n * STEP * unit <= this.width - PANEL_W - 70) scrollTarget = (n - 1) / 2f;
		scrollTarget = clamp(scrollTarget, 0, Math.max(0, n - 1));

		int steps = Math.max(1, (int) Math.ceil(dt / 0.012f));
		float h = dt / steps;
		for (int s = 0; s < steps; s++) {
			float acc = 190f * (selTarget - selPos) - 19f * selVel;
			selVel += acc * h;
			selPos += selVel * h;
		}
		scrollPos += (scrollTarget - scrollPos) * (1f - (float) Math.exp(-dt * 9f));
		if (th().classic()) { // тема «Классика»: без плавных переходов
			selPos = selTarget;
			selVel = 0f;
			scrollPos = scrollTarget;
		}

		float t = (System.nanoTime() - openTime) / 1e9f;
		for (int i = 0; i < n; i++) {
			float d = i - selPos;
			float dc = clamp(d, -1f, 1f);
			float near = 1f - Math.min(1f, Math.abs(d));
			float ease = near * near * (3f - 2f * near);
			float g = i - scrollPos;

			float ap = th().classic() ? 1f : clamp((t - i * 0.05f) / 0.45f, 0f, 1f);
			float e = 1f - (1f - ap) * (1f - ap) * (1f - ap);

			float x, y, ang, sx, sy;
			switch (style) {
				case FAN: {
					float th = clamp(g * 0.17f, -0.95f, 0.95f);
					x = cx + (float) Math.sin(th) * 300f * unit + dc * PUSH * 0.5f * unit;
					y = baseY + (1f - (float) Math.cos(th)) * 300f * unit * 0.6f - RISE * unit * 1.2f * ease;
					ang = th * (1f - 0.92f * ease);
					sx = sy = unit * (0.90f + 0.20f * ease);
					break;
				}
				case WAVE: {
					float w = (float) Math.sin(g * 0.9f + t * 1.8f);
					x = cx + g * STEP * 1.18f * unit + dc * PUSH * 0.5f * unit;
					y = baseY + w * 7f * unit * (1f - ease) - RISE * unit * ease;
					ang = w * 0.07f * (1f - ease);
					sx = sy = unit * (0.86f + 0.22f * ease);
					break;
				}
				case FLOW: {
					float gc = clamp(g, -1f, 1f);
					float sq = Math.min(1f, Math.abs(g)) * (1f - ease);
					x = cx + gc * CW * 0.7f * unit + (g - gc) * 30f * unit;
					y = baseY - RISE * unit * ease;
					ang = 0f;
					sx = unit * (1f - 0.5f * sq) * (1f + 0.07f * ease);
					sy = unit * (1f - 0.12f * sq) * (1f + 0.07f * ease);
					break;
				}
				default: {
					x = cx + g * STEP * unit + dc * PUSH * unit;
					y = baseY - RISE * unit * ease;
					ang = (float) Math.toRadians(dc * LEAN_DEG);
					sx = sy = unit * (MIN_SCALE + (MAX_SCALE - MIN_SCALE) * ease);
				}
			}
			xs[i] = x;
			ys[i] = y + (1f - e) * 110f * unit;
			angs[i] = ang * (0.4f + 0.6f * e);
			float pop = 0.88f + 0.12f * e;
			scXs[i] = sx * pop;
			scYs[i] = sy * pop;
			eases[i] = ease * e;
		}

		boolean has = cur() != null;
		for (Button b : new Button[]{wearBtn, delBtn, starBtn, editBtn, folderBtn, diceBtn}) {
			if (b != null) b.active = has;
		}
		if (sunBtn != null) sunBtn.active = !SkinStore.skins().isEmpty();
		if (wearBtn != null) wearBtn.active = has && SkinLibraryClient.cooldownLeft() == 0;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNano) / 1e9f);
		lastNano = now;
		layout(mouseX, mouseY, dt);

		super.render(g, mouseX, mouseY, delta);

		if (view.isEmpty()) {
			String msg = SkinStore.skins().isEmpty()
					? I18n.t("Библиотека пуста. Нажмите «+ Добавить» или «Импорт...»") : I18n.t("Ничего не найдено");
			g.drawCenteredString(this.font, msg, Math.round(shelfCx), this.height / 2, th().sub());
		}

		if (carrying && carryEntry != null) { // «призрак» переносимой карточки
			Theme gt = th();
			g.pose().pushMatrix();
			g.pose().translate((float) (mouseX - 20), (float) (mouseY - 30));
			g.pose().scale(0.55f, 0.55f);
			Ui.roundPanel(g, 0, 0, CW, CH, Ui.alpha(gt.cardTop(), 0xE0), Ui.alpha(gt.cardBottom(), 0xE0));
			Ui.roundOutline(g, 0, 0, CW, CH, gt.accent());
			SkinPreviewCache.get(carryEntry.source()).draw(g, 8, 4, carryEntry.isSlim(), 25f, Pose.STAND, "mini");
			g.pose().popMatrix();
		}

		float age = (now - toastAt) / 1e9f;
		if (!toastText.isEmpty() && age < 3.2f) {
			float a = th().classic() || age < 2.6f ? 1f : 1f - (age - 2.6f) / 0.6f;
			String ts = Ui.fit(this.font, toastText, this.width - 24);
			int w = this.font.width(ts) + 16;
			int x = (this.width - w) / 2, y = rowY - 22;
			Theme th = th();
			g.fill(x, y, x + w, y + 16, Ui.alpha(th.cardBottom(), (int) (230 * a)));
			Ui.outline(g, x, y, w, 16, Ui.alpha(th.accent(), (int) (200 * a)));
			g.drawCenteredString(this.font, ts, this.width / 2, y + 4, Ui.alpha(th.text(), (int) (255 * a)));
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= PANEL_X && mouseX < PANEL_X + PANEL_W && mouseY >= PANEL_Y && mouseY < PANEL_Y + panelH()) {
			folderScrollTarget -= (float) scrollY * 40f;
			return true;
		}
		int n = view.size();
		if (n > 0) {
			scrollTarget = clamp(scrollTarget - (float) scrollY * 1.5f, 0, n - 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ------------------------------------------------------------------ отрисовка

	private void drawShelf(GuiGraphics g) {
		int n = view.size();
		if (n == 0) return;
		Theme th = th();
		int sy = Math.round(baseY);

		if (style == CardStyle.SHELF || style == CardStyle.WAVE) {
			g.fillGradient(0, sy, this.width, sy + 10, th.shelfTop(), th.shelfBottom());
			g.fill(0, sy, this.width, sy + 1, Ui.mix(th.border(), th.accent(), 0.5f));
			g.fillGradient(0, sy + 10, this.width, sy + 34, 0x40000000, 0x00000000);
		} else {
			g.fillGradient(0, sy - 2, this.width, sy + 40, Ui.alpha(th.glowA(), 0), 0x00000000);
			g.fillGradient(this.width / 4, sy, this.width * 3 / 4, sy + 1,
					Ui.alpha(th.accent(), 0), Ui.alpha(th.accent(), 90));
		}

		// от дальних карточек к ближним без сортировки и выделения памяти: сравниваем крайние индексы
		final float sp = selPos;
		int lo = 0, hi = n - 1;
		while (lo <= hi) {
			int idx;
			if (Math.abs(lo - sp) >= Math.abs(hi - sp)) idx = lo++;
			else idx = hi--;
			if (idx >= xs.length) continue;
			if (xs[idx] < -CW || xs[idx] > this.width + CW) continue;
			drawCard(g, idx, th);
		}

		SkinEntry e = cur();
		if (e == null) return;
		g.pose().pushMatrix();
		g.pose().translate(shelfCx, baseY + 8f);
		g.pose().scale(1.3f, 1.3f);
		g.drawCenteredString(this.font, (e.favorite ? FAV + " " : "") + (e.name == null ? "" : e.name), 0, 0, th.text());
		g.pose().popMatrix();
		String sub = e.variant + (e.tag != null && !e.tag.isBlank() ? "  \u25A4 " + e.tag : "")
				+ "  " + (selTarget + 1) + "/" + n;
		g.drawCenteredString(this.font, sub, Math.round(shelfCx), sy + 24, Ui.mix(th.sub(), th.accent(), 0.5f));
	}

	// ------------------------------------------------------------------ папки слева

	private void drawFolderPanel(GuiGraphics g) {
		Theme th = th();
		int ph = panelH();
		Ui.panel(g, PANEL_X, PANEL_Y, PANEL_W, ph, th);
		g.drawString(this.font, carrying ? I18n.t("Отпустите на папку") : I18n.t("Папки"), PANEL_X + 8, PANEL_Y + 4, carrying ? th.accent() : th.sub());

		g.enableScissor(PANEL_X + 1, PANEL_Y + 13, PANEL_X + PANEL_W - 1, PANEL_Y + ph - 2);
		for (int i = 0; i < tiles.size(); i++) {
			int ty = tileTop(i);
			if (ty > PANEL_Y + ph || ty + TH < PANEL_Y + 12) continue;
			Tile t = tiles.get(i);
			boolean selected = (t.kind() != 3 && t.key().equalsIgnoreCase(filter) && !carrying) || (carrying && i == dropTile);
			drawFolderTile(g, t, PANEL_X + (PANEL_W - TW) / 2, ty, i < tileHover.length ? tileHover[i] : 0f, selected, th);
		}
		g.disableScissor();

		int content = tiles.size() * (TH + TGAP), view = ph - 18;
		if (content > view) {
			float frac = view / (float) content;
			int barH = Math.max(12, Math.round(view * frac));
			float maxScroll = content - view;
			int barY = PANEL_Y + 14 + Math.round((view - barH) * (maxScroll <= 0 ? 0 : folderScroll / maxScroll));
			g.fill(PANEL_X + PANEL_W - 4, barY, PANEL_X + PANEL_W - 2, barY + barH, Ui.alpha(th.accent(), 150));
		}
	}

	/** Папка в стиле проводника Windows: полупрозрачная, из неё торчат три карточки скинов, имя снизу. */
	private void drawFolderTile(GuiGraphics g, Tile t, int tx, int ty, float hover, boolean selected, Theme th) {
		int lift = th.classic() ? 0 : Math.round(-3 * hover);
		int ix = tx + (TW - 64) / 2, iy = ty + 12 + lift;

		if (selected) {
			g.fill(tx, ty + 2, tx + TW, ty + TH - 2, Ui.alpha(th.accent(), 34));
			Ui.roundOutline(g, tx, ty + 2, TW, TH - 4, Ui.alpha(th.accent(), 170));
		} else if (hover > 0.02f) {
			g.fill(tx, ty + 2, tx + TW, ty + TH - 2, Ui.alpha(0xFFFFFF, (int) (18 * hover)));
		}

		int back, front;
		switch (t.kind()) {
			case 0 -> { back = 0xB04F7FD6; front = 0xA88FB4FF; }   // «Все скины» — голубая
			case 1 -> { back = 0xB0E08A2E; front = 0xA8FFC857; }   // «Избранное» — золотая
			default -> { back = 0xB0D9A92E; front = 0xA8F7D56A; }  // обычная жёлтая папка
		}

		if (t.kind() == 3) { // «Новая папка»: пунктирный контур и плюс
			int col = Ui.alpha(th.accent(), (int) (120 + 100 * hover));
			for (int x = ix; x < ix + 64; x += 6) {
				g.fill(x, iy + 5, Math.min(x + 3, ix + 64), iy + 6, col);
				g.fill(x, iy + 43, Math.min(x + 3, ix + 64), iy + 44, col);
			}
			for (int y = iy + 5; y < iy + 44; y += 6) {
				g.fill(ix, y, ix + 1, Math.min(y + 3, iy + 44), col);
				g.fill(ix + 63, y, ix + 64, Math.min(y + 3, iy + 44), col);
			}
			g.pose().pushMatrix();
			g.pose().translate((float) (ix + 32), (float) (iy + 14));
			g.pose().scale(2f, 2f);
			g.drawCenteredString(this.font, "+", 0, 0, col);
			g.pose().popMatrix();
		} else {
			g.fill(ix + 1, iy, ix + 25, iy + 5, back);           // язычок
			Ui.roundPanel(g, ix, iy + 5, 64, 39, back, back);     // задняя стенка

			// три карточки, торчащие из папки
			int c = t.sample().size();
			int[][] plan = c == 1 ? new int[][]{{0, 1}} : c == 2 ? new int[][]{{0, 0}, {1, 2}} : new int[][]{{0, 0}, {2, 2}, {1, 1}};
			float[] px = {ix + 12f, ix + 32f, ix + 52f};
			float[] ang = {-0.22f, 0f, 0.22f};
			if (c > 0) {
				for (int[] pl : plan) {
					SkinEntry e = t.sample().get(pl[0]);
					g.pose().pushMatrix();
					g.pose().translate(px[pl[1]], (float) (iy + 26));
					g.pose().rotate(ang[pl[1]]);
					Ui.roundPanel(g, -11, -34, 22, 34, Ui.alpha(th.cardTop(), 0xF2), Ui.alpha(th.cardBottom(), 0xF2));
					Ui.roundOutline(g, -11, -34, 22, 34, Ui.alpha(th.border(), 0xFF));
					g.pose().pushMatrix();
					g.pose().translate(-10f, -33f);
					g.pose().scale(0.2f, 0.2f);
					SkinPreviewCache.get(e.source()).draw(g, 0, 0, e.isSlim(), 25f, Pose.STAND, "mini");
					g.pose().popMatrix();
					g.pose().popMatrix();
				}
			}

			Ui.roundPanel(g, ix, iy + 14, 64, 30, front, Ui.mix(front, back, 0.6f));  // передняя панель
			g.fill(ix + 2, iy + 15, ix + 62, iy + 16, 0x55FFFFFF);
			Ui.roundOutline(g, ix, iy + 14, 64, 30, 0x40000000);
			if (t.kind() == 1) g.drawCenteredString(this.font, FAV, ix + 32, iy + 25, 0xFFFFF4B0);
			if (c == 0 && t.kind() == 2) g.drawCenteredString(this.font, I18n.t("пусто"), ix + 32, iy + 25, 0x99FFFFFF);
		}

		String name = t.label();
		while (this.font.width(name) > TW - 2 && name.length() > 1) name = name.substring(0, name.length() - 1);
		g.drawCenteredString(this.font, name, tx + TW / 2, ty + 60, selected ? th.accent() : th.text());
		if (t.kind() != 3) g.drawCenteredString(this.font, String.valueOf(t.count()), tx + TW / 2, ty + 69, th.sub());
	}

	private void drawCard(GuiGraphics g, int i, Theme th) {
		SkinEntry e = view.get(i);
		float ease = eases[i];
		int x0 = -CW / 2, y0 = -CH;
		float t = (System.nanoTime() - openTime) / 1e9f;
		boolean selected = i == selTarget && ease > 0.5f;

		g.pose().pushMatrix();
		g.pose().translate(xs[i], ys[i]);
		g.pose().rotate(angs[i]);
		g.pose().scale(scXs[i], scYs[i]);

		if (ease > 0.03f && !th.classic()) {
			float pulse = 0.75f + 0.25f * (float) Math.sin(t * 3.2f);
			for (int k = 1; k <= 5; k++) {
				int a = (int) (ease * pulse * (46 - k * 7));
				if (a <= 0) continue;
				g.fill(x0 - k * 2, y0 - k * 2, x0 + CW + k * 2, k * 2, Ui.alpha(th.accent(), a));
			}
		}

		Ui.roundPanel(g, x0, y0, CW, CH, th.cardTop(), th.cardBottom());
		Ui.roundOutline(g, x0, y0, CW, CH, Ui.mix(th.border(), th.accent(), ease));
		Ui.deco(g, x0, y0, CW, CH, th, ease);
		g.fill(x0 + 2, y0 + 1, x0 + 5, -2, Ui.mix(Ui.alpha(th.border(), 255), th.accent(), ease));
		if (ease > 0.05f) g.fill(x0 + 6, y0 + 1, x0 + CW - 3, y0 + 2, Ui.alpha(th.accent(), (int) (200 * ease)));

		SkinPreview p = SkinPreviewCache.get(e.source());
		if (selected) p.draw(g, x0 + 8, y0 + 4, e.isSlim(), drag.yaw, Pose.current(), "card");
		else p.draw(g, x0 + 8, y0 + 4, e.isSlim(), 25f, Pose.STAND, "card");

		if (e.favorite) g.drawString(this.font, FAV, x0 + 10, y0 + 5, Ui.alpha(th.accent(), 255));

		if (ease > 0.3f && !th.classic()) {
			float k = (t * 0.55f) % 2.2f - 0.4f;
			int bx = x0 + (int) (k * CW);
			int l = Math.max(x0 + 1, bx), r = Math.min(x0 + CW - 1, bx + 12);
			if (r > l) g.fill(l, y0 + 2, r, -2, (int) (26 * ease) << 24 | 0xFFFFFF);
		}

		if (selected && !SkinStore.wearMode().equals("button"))
			g.drawString(this.font, "\u25B6", x0 + CW - 14, -11, Ui.alpha(th.accent(), 230));
		String nm = e.name == null ? "" : e.name;
		while (this.font.width(nm) > CW - 16 && nm.length() > 1) nm = nm.substring(0, nm.length() - 1);
		g.drawString(this.font, nm, x0 + 8, -20, th.text());
		String sub = e.variant + (e.tag != null && !e.tag.isBlank() ? " \u25A4 " + e.tag : "");
		while (this.font.width(sub) > CW - 16 && sub.length() > 1) sub = sub.substring(0, sub.length() - 1);
		g.drawString(this.font, sub, x0 + 8, -10, th.sub());

		int dim = (int) ((1f - ease) * 0x78);
		if (dim > 0) g.fill(x0, y0, x0 + CW, 0, dim << 24);

		g.pose().popMatrix();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
