package dev.skinlibrary.screen;

import dev.skinlibrary.DemoSkins;
import dev.skinlibrary.I18n;
import dev.skinlibrary.Input;
import dev.skinlibrary.Pose;
import dev.skinlibrary.SkinPreviewCache;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Гайд по моду: страницы с живыми анимированными макетами интерфейса. Плавно появляется при первом запуске.
 * Демо безопасны: никакие команды на сервер не отправляются, скины — встроенные (рисуются кодом).
 */
public class GuideScreen extends ThemedScreen {
	private static final int PAGES = 12;
	private static final int CW = 112, CH = 178;

	private final Screen parent;
	private int page = 0, target = 0, dir = 1;
	private float trans = 1f;
	private long pageStart = System.nanoTime();
	private long lastNano = System.nanoTime();
	private Button backBtn, nextBtn;
	private boolean down, wasDown;
	private int pw, ph, px, py;

	// состояние демо «карточки»
	private float gSel = 2f, gVel = 0f;
	private CardStyle gStyle = CardStyle.SHELF;
	private int chipPress = -1;

	public GuideScreen(Screen parent) {
		super(Component.literal(I18n.t("Гайд")));
		this.parent = parent;
	}

	// ------------------------------------------------------------------ тексты

	private static String title(int i) {
		return switch (i) {
			case 0 -> I18n.t("Добро пожаловать!");
			case 1 -> I18n.t("Библиотека и карточки");
			case 2 -> I18n.t("Как добавить скин");
			case 3 -> I18n.t("Другие способы");
			case 4 -> I18n.t("Надевание скина");
			case 5 -> I18n.t("На сервере");
			case 6 -> I18n.t("Папки и избранное");
			case 7 -> I18n.t("Плащи");
			case 8 -> I18n.t("Модель и позы");
			case 9 -> I18n.t("Превью в меню");
			case 10 -> I18n.t("Темы и звуки");
			default -> I18n.t("Настройки и язык");
		};
	}

	private static String body(int i) {
		return switch (i) {
			case 0 -> I18n.t("Skin Library — удобная библиотека скинов для серверов со SkinRestorer. Меняйте скин одним кликом, не выходя из игры. Листайте гайд кнопками внизу или кликайте по точкам.");
			case 1 -> I18n.t("Скины стоят на полке. Наведите на карточку — она выпрямится, поднимется и начнёт вращаться. Стиль анимации меняется кнопкой «Анимация» в библиотеке. Попробуйте справа: наведите мышь на карточки и нажмите на стили.");
			case 2 -> I18n.t("Нажмите «+ Добавить», вставьте команду с сайта SkinRestorer — справа сразу появится 3D-модель. Затем «Добавить в библиотеку», и карточка встанет на полку.");
			case 3 -> I18n.t("«Генератор» создаёт команду из PNG-файла или ссылки. «По нику» берёт скин любого лицензионного игрока. «Импорт...» находит скины аккаунта и лаунчеров: Prism, Modrinth App и других.");
			case 4 -> I18n.t("Кликните по выбранной карточке — команда уйдёт на сервер. После этого пауза 30 секунд (КД плагина), затем придёт уведомление. Способ надевания меняется в настройках.");
			case 5 -> I18n.t("Выберите скин заранее в главном меню — он применится при входе на сервер. Мод проверяет, есть ли там SkinRestorer, и не шлёт лишних команд. Есть режимы «случайный скин» и «скин дня».");
			case 6 -> I18n.t("Потяните карточку за нижнюю полосу на папку слева — скин переедет в неё. ★ добавляет в избранное, ✎ меняет название, папку и плащ. Папками можно управлять в «Папки...».");
			case 7 -> I18n.t("В окнах добавления слева стоят три полки с плащами SkinsRestorer. Выберите плащ — он будет добавлен к скину при надевании.");
			case 8 -> I18n.t("Зажмите левую кнопку на модели и вращайте её мышью. Кнопка «Поза» переключает позы: идёт, бежит, танцует, летит и другие.");
			case 9 -> I18n.t("В главном меню и меню паузы слева стоит превью скина. Тяните за пиксельную платформу, а размер меняйте за любой угол. Кнопка-иконка с головой открывает библиотеку и тоже двигается.");
			case 10 -> I18n.t("Темы с анимированным фоном и своей формой интерфейса (Тьма с жидкими кнопками, темы по мотивам инди-игр — Terraria со сменой дня и ночи, Stardew Valley, Undertale, Hollow Knight, Celeste, Hades, Cuphead, Лава, Кибер, Космос, Зима, Ретровейв, Океан, Таверна, Аркада, светлые Тетрадь и Облака), цветовые темы и «Классика» без анимаций — светлая и тёмная. Звуки: звуки анимаций, темы звуков, ползунки громкости каждого звука и импорт своих .ogg и .wav.");
			default -> I18n.t("В «Настройки...» выбирается способ надевания, автонадевание, случайный скин, уведомления и язык. Гайд можно открыть снова кнопкой «?». Приятной игры! В моде спрятан один секрет — поищите.");
		};
	}

	// ------------------------------------------------------------------ экран

	private void layout() {
		pw = Math.min(this.width - 24, 470);
		ph = Math.min(this.height - 24, 280);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
	}

	@Override
	protected void init() {
		layout();
		addBackground();
		this.addRenderableOnly((g, mx, my, d) -> drawAll(g, mx, my));

		fancy(() -> I18n.t("Пропустить"), px + 12, py + ph - 30, 96, 20, b -> this.onClose(), null, 0);
		backBtn = fancy(() -> I18n.t("Назад"), px + pw - 210, py + ph - 30, 96, 20, b -> go(page - 1), null, 0);
		nextBtn = fancy(() -> page == PAGES - 1 ? I18n.t("Готово") : I18n.t("Далее"),
				px + pw - 108, py + ph - 30, 96, 20, b -> {
					if (page >= PAGES - 1) this.onClose();
					else go(page + 1);
				}, null, 1);
		backBtn.visible = false;
		nextBtn.visible = false;
	}

	private void go(int p) {
		if (p < 0 || p >= PAGES || p == target) return;
		dir = p > target ? 1 : -1;
		target = p;
		trans = 0f;
	}

	@Override
	public void onClose() {
		SkinStore.setGuideSeen(true);
		this.minecraft.setScreen(parent);
	}

	// ------------------------------------------------------------------ утилиты

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static float seg(float t, float a, float b) {
		return clamp01((t - a) / (b - a));
	}

	private static float sm(float u) {
		u = clamp01(u);
		return u * u * (3f - 2f * u);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private List<String> wrap(String s, int maxW) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String test = line.length() == 0 ? word : line + " " + word;
			if (this.font.width(test) > maxW && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}

	private void mbtn(GuiGraphics g, int x, int y, int w, int h, String label, float hover, int kind) {
		Ui.button(g, this.font, x, y, w, h, label, hover, true, kind, th());
	}

	private void mfield(GuiGraphics g, int x, int y, int w, int h, String text, boolean focus, boolean caret, float t) {
		g.fill(x, y, x + w, y + h, focus ? 0xFFFFFFFF : 0xFFA0A0A0);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000);
		String s = text;
		while (this.font.width(s) > w - 8 && s.length() > 1) s = s.substring(1);
		g.drawString(this.font, s, x + 4, y + (h - 8) / 2, 0xFFE0E0E0);
		if (caret && ((int) (t * 2.5f)) % 2 == 0) {
			int cx = x + 4 + this.font.width(s);
			g.fill(cx, y + 3, cx + 1, y + h - 3, 0xFFFFFFFF);
		}
	}

	private void cursor(GuiGraphics g, float x, float y) {
		String[] rows = {"X.......", "XX......", "XWX.....", "XWWX....", "XWWWX...", "XWWWWX..", "XWWWWWX.",
				"XWWWWWWX", "XWWWXXXX", "XWXWX...", "XX.XWX..", "X..XWX..", "....XX.."};
		int ix = Math.round(x), iy = Math.round(y);
		for (int r = 0; r < rows.length; r++)
			for (int c = 0; c < 8; c++) {
				char ch = rows[r].charAt(c);
				if (ch == '.') continue;
				g.fill(ix + c, iy + r, ix + c + 1, iy + r + 1, ch == 'X' ? 0xFF000000 : 0xFFFFFFFF);
			}
	}

	private void ripple(GuiGraphics g, float x, float y, float u) {
		if (u <= 0f || u >= 1f) return;
		int r = Math.round(3 + u * 12);
		Ui.outline(g, Math.round(x) - r, Math.round(y) - r, r * 2, r * 2, Ui.alpha(th().accent(), (int) (200 * (1f - u))));
	}

	/** Пиксельная голова-логотип (8x8 клеток). */
	private void drawLogo(GuiGraphics g, int cx, int cy, int size) {
		int cell = Math.max(1, size / 8), x0 = cx - cell * 4, y0 = cy - cell * 4;
		String[] grid = {"HHHHHHHH", "HHHHHHHH", "HSSSSSSH", "SSSSSSSS", "SWBSSWBS", "SSSSSSSS", "SSSMMSSS", "SSSSSSSS"};
		for (int r = 0; r < 8; r++)
			for (int c = 0; c < 8; c++) {
				int col = switch (grid[r].charAt(c)) {
					case 'H' -> 0xFF3F2A1A;
					case 'W' -> 0xFFFFFFFF;
					case 'B' -> 0xFF3A5FCD;
					case 'M' -> 0xFF8B5A3C;
					default -> 0xFFDEAA78;
				};
				g.fill(x0 + c * cell, y0 + r * cell, x0 + c * cell + cell, y0 + r * cell + cell, col);
			}
	}

	/** Миниатюра карточки (как в библиотеке) с настоящим 3D-превью демо-скина. */
	private void miniCard(GuiGraphics g, float cx, float by, float sx, float sy, float ang, float ease, int idx,
						  float yaw, Pose pose, String slot) {
		Theme th = th();
		int x0 = -CW / 2, y0 = -CH;
		g.pose().pushMatrix();
		g.pose().translate(cx, by);
		g.pose().rotate(ang);
		g.pose().scale(sx, sy);
		if (ease > 0.03f && !th.classic()) {
			for (int k = 1; k <= 4; k++) {
				int a = (int) (ease * (40 - k * 8));
				if (a > 0) g.fill(x0 - k * 2, y0 - k * 2, x0 + CW + k * 2, k * 2, Ui.alpha(th.accent(), a));
			}
		}
		Ui.roundPanel(g, x0, y0, CW, CH, th.cardTop(), th.cardBottom());
		Ui.roundOutline(g, x0, y0, CW, CH, Ui.mix(th.border(), th.accent(), ease));
		g.fill(x0 + 2, y0 + 1, x0 + 5, -2, Ui.mix(Ui.alpha(th.border(), 255), th.accent(), ease));
		SkinPreviewCache.get("demo:" + idx).draw(g, x0 + 8, y0 + 4, false, yaw, pose, slot);
		g.drawString(this.font, DemoSkins.NAMES[Math.floorMod(idx, DemoSkins.NAMES.length)], x0 + 8, -20, th.text());
		g.drawString(this.font, "classic", x0 + 8, -10, th.sub());
		int dim = (int) ((1f - ease) * 0x78);
		if (dim > 0) g.fill(x0, y0, x0 + CW, 0, dim << 24);
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ кадр

	private void drawAll(GuiGraphics g, int mx, int my) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNano) / 1e9f);
		lastNano = now;
		layout();
		wasDown = down;
		down = Input.leftDown();

		// плавное появление: панель вырастает с лёгким «отскоком», фон затемняется
		float t0 = (now - openTime) / 1e9f;
		float u = clamp01(t0 / 0.7f);
		float c1 = 1.70158f, c3 = c1 + 1f, v = u - 1f;
		float oe = 1f + c3 * v * v * v + c1 * v * v;
		float fade = clamp01(t0 / 0.45f);
		boolean opened = t0 >= 0.75f;

		g.fill(0, 0, this.width, this.height, Ui.alpha(0x000000, (int) (150 * fade)));

		if (backBtn != null) {
			backBtn.visible = opened;
			nextBtn.visible = opened;
			backBtn.active = page > 0 && target > 0;
		}

		// переход между страницами
		if (target != page || trans < 1f) {
			trans = Math.min(1f, trans + dt / 0.42f);
			if (trans >= 0.5f && page != target) {
				page = target;
				pageStart = now;
			}
		}

		float cxp = px + pw / 2f, cyp = py + ph / 2f;
		float sc = 0.86f + 0.14f * oe;
		g.pose().pushMatrix();
		g.pose().translate(cxp, cyp + (1f - clamp01(oe)) * 18f);
		g.pose().scale(sc, sc);
		g.pose().translate(-cxp, -cyp);

		Theme th = th();
		Ui.panel(g, px, py, pw, ph, th);
		g.fill(px + 6, py + 4, px + 6 + Math.round((pw - 12) * seg(t0, 0.2f, 0.9f)), py + 6, Ui.alpha(th.accent(), 200)); // полоска-«загрузка»

		// шапка: логотип и счётчик страниц
		drawLogo(g, px + 20, py + 18, 16);
		g.drawString(this.font, "Skin Library", px + 34, py + 11, th.text());
		String cnt = (target + 1) + " / " + PAGES;
		g.drawString(this.font, cnt, px + pw - 14 - this.font.width(cnt), py + 11, th.sub());

		// содержимое страницы с плавной сменой (сдвиг + затухание)
		float shift = 0f, cover = 0f;
		if (trans < 1f) {
			if (trans < 0.5f) {
				float a = sm(trans * 2f);
				cover = a;
				shift = -dir * 22f * a;
			} else {
				float a = sm((trans - 0.5f) * 2f);
				cover = 1f - a;
				shift = dir * 22f * (1f - a);
			}
		}
		float pt = (now - pageStart) / 1e9f;
		drawContent(g, page, Math.round(shift), pt, mx, my, dt);
		if (cover > 0.01f) {
			int a = (int) (255 * cover);
			g.fill(px + 8, py + 28, px + pw - 8, py + ph - 50, Ui.alpha(th.classic() ? 0x202020 : mixBase(th), a));
		}

		// точки-индикаторы: клик переходит на страницу
		int dotsW = PAGES * 12, dx0 = px + (pw - dotsW) / 2, dy0 = py + ph - 44;
		for (int i = 0; i < PAGES; i++) {
			boolean cur = i == target;
			boolean over = mx >= dx0 + i * 12 - 1 && mx < dx0 + i * 12 + 10 && my >= dy0 - 3 && my < dy0 + 9;
			int w = cur ? 10 : 6;
			g.fill(dx0 + i * 12 + (10 - w) / 2, dy0 + 2, dx0 + i * 12 + (10 - w) / 2 + w, dy0 + 6,
					cur ? th.accent() : (over ? th.text() : Ui.alpha(th.sub(), 150)));
			if (opened && over && wasDown && !down) go(i);
		}
		g.pose().popMatrix();

		// белая вспышка при появлении
		if (t0 < 0.25f) g.fill(0, 0, this.width, this.height, Ui.alpha(0xFFFFFF, (int) (40 * (1f - t0 / 0.25f))));
	}

	private static int mixBase(Theme th) {
		return th.cardTop() & 0xFFFFFF;
	}

	private void drawContent(GuiGraphics g, int p, int shift, float t, int mx, int my, float dt) {
		Theme th = th();
		int textW = Math.max(110, Math.min(160, pw * 36 / 100));
		int tx = px + 16 + shift, ty = py + 34;

		g.pose().pushMatrix();
		g.pose().translate((float) tx, (float) ty);
		g.pose().scale(1.25f, 1.25f);
		g.drawString(this.font, title(p), 0, 0, th.text());
		g.pose().popMatrix();
		int yy = ty + 18;
		for (String line : wrap(body(p), textW)) {
			if (yy > py + ph - 56) break;
			g.drawString(this.font, line, tx, yy, th.sub());
			yy += 10;
		}

		int dx = px + 16 + textW + 12 + shift, dy = py + 28;
		int dw = px + pw - 12 - (px + 16 + textW + 12), dh = ph - 28 - 50;
		g.fill(dx, dy, dx + dw, dy + dh, 0x66000000);
		Ui.outline(g, dx, dy, dw, dh, Ui.alpha(th.border(), 255));

		float tt = Math.max(0f, t);
		switch (p) {
			case 0 -> demoWelcome(g, dx, dy, dw, dh, tt);
			case 1 -> demoCards(g, dx, dy, dw, dh, tt, mx, my, dt);
			case 2 -> demoAdd(g, dx, dy, dw, dh, tt);
			case 3 -> demoOther(g, dx, dy, dw, dh, tt);
			case 4 -> demoWear(g, dx, dy, dw, dh, tt);
			case 5 -> demoServer(g, dx, dy, dw, dh, tt);
			case 6 -> demoFolders(g, dx, dy, dw, dh, tt);
			case 7 -> demoCapes(g, dx, dy, dw, dh, tt);
			case 8 -> demoPoses(g, dx, dy, dw, dh, tt);
			case 9 -> demoMenu(g, dx, dy, dw, dh, tt);
			case 10 -> demoThemes(g, dx, dy, dw, dh, tt);
			default -> demoSettings(g, dx, dy, dw, dh, tt);
		}
	}

	// ------------------------------------------------------------------ демо 0: приветствие

	private void demoWelcome(GuiGraphics g, int x, int y, int w, int h, float t) {
		Theme th = th();
		float cx = x + w / 2f, cy = y + h / 2f - 12;
		for (int i = 0; i < 5; i++) { // карточки за логотипом покачиваются
			float a = (i - 2) * 0.22f;
			float bob = (float) Math.sin(t * 1.6f + i) * 3f;
			miniCard(g, cx + (i - 2) * 36f, cy + 44 + bob, 0.28f, 0.28f, a * 0.5f, i == 2 ? 1f : 0f, i == 2 ? DemoSkins.HERO : i, 25f, Pose.STAND, "gw" + i);
		}
		float pop = sm(t / 0.6f);
		drawLogo(g, Math.round(cx), Math.round(cy - 18 + (float) Math.sin(t * 2f) * 3f), Math.round(48 * pop));
		g.pose().pushMatrix();
		g.pose().translate(cx, cy + 56f);
		g.pose().scale(1.6f, 1.6f);
		g.drawCenteredString(this.font, "Skin Library", 0, 0, th.text());
		g.pose().popMatrix();
		for (int i = 0; i < 10; i++) { // искры
			float s = i * 5.3f;
			float sx = x + 10 + ((float) Math.abs(Math.sin(s)) * (w - 20));
			float sy = y + h - ((t * (14 + i * 3) + i * 17) % h);
			float life = (float) Math.sin(Math.PI * clamp01((y + h - sy) / h));
			g.fill((int) sx, (int) sy, (int) sx + 1, (int) sy + 1, Ui.alpha(i % 2 == 0 ? th.glowA() : th.glowB(), (int) (170 * life)));
		}
	}

	// ------------------------------------------------------------------ демо 1: карточки и стили

	private void demoCards(GuiGraphics g, int x, int y, int w, int h, float t, int mx, int my, float dt) {
		Theme th = th();
		int n = 5;
		float u = Math.min(0.6f, (h - 62f) / (CH * 1.07f + 24f));
		float cx = x + w / 2f, baseY = y + h - 36;

		boolean inside = mx >= x && mx < x + w && my >= y && my < y + h - 28;
		int tgt = (int) ((t * 0.5f) % n);
		if (inside) {
			float best = 1e9f;
			for (int i = 0; i < n; i++) {
				float sxp = staticX(i, cx, u);
				if (Math.abs(mx - sxp) < best) {
					best = Math.abs(mx - sxp);
					tgt = i;
				}
			}
		}
		for (int s = 0; s < 8; s++) { // пружина выбора
			float acc = 190f * (tgt - gSel) - 19f * gVel;
			gVel += acc * dt / 8f;
			gSel += gVel * dt / 8f;
		}

		Integer[] order = {0, 1, 2, 3, 4};
		java.util.Arrays.sort(order, (a, b) -> Float.compare(Math.abs(b - gSel), Math.abs(a - gSel)));
		if (gStyle == CardStyle.SHELF || gStyle == CardStyle.WAVE) {
			g.fillGradient(x + 2, (int) baseY, x + w - 2, (int) baseY + 6, th.shelfTop(), th.shelfBottom());
			g.fill(x + 2, (int) baseY, x + w - 2, (int) baseY + 1, Ui.mix(th.border(), th.accent(), 0.5f));
		}
		for (int idx : order) {
			float d = idx - gSel, dc = Math.max(-1f, Math.min(1f, d));
			float near = 1f - Math.min(1f, Math.abs(d));
			float ease = near * near * (3f - 2f * near);
			float gg = idx - 2f;
			float xx, yy, ang, sx, sy;
			switch (gStyle) {
				case FAN -> {
					float a = Math.max(-0.95f, Math.min(0.95f, gg * 0.17f));
					xx = cx + (float) Math.sin(a) * 300f * u + dc * 15f * u;
					yy = baseY + (1f - (float) Math.cos(a)) * 180f * u - 24f * u * ease;
					ang = a * (1f - 0.92f * ease);
					sx = sy = u * (0.9f + 0.2f * ease);
				}
				case WAVE -> {
					float wv = (float) Math.sin(gg * 0.9f + t * 1.8f);
					xx = cx + gg * 92f * u + dc * 15f * u;
					yy = baseY + wv * 7f * u * (1f - ease) - 20f * u * ease;
					ang = wv * 0.07f * (1f - ease);
					sx = sy = u * (0.86f + 0.22f * ease);
				}
				case FLOW -> {
					float gc = Math.max(-1f, Math.min(1f, gg));
					float sq = Math.min(1f, Math.abs(gg)) * (1f - ease);
					xx = cx + gc * CW * 0.7f * u + (gg - gc) * 30f * u;
					yy = baseY - 20f * u * ease;
					ang = 0f;
					sx = u * (1f - 0.5f * sq) * (1f + 0.07f * ease);
					sy = u * (1f - 0.12f * sq) * (1f + 0.07f * ease);
				}
				default -> {
					xx = cx + gg * 78f * u + dc * 30f * u;
					yy = baseY - 20f * u * ease;
					ang = (float) Math.toRadians(dc * 8f);
					sx = sy = u * (0.8f + 0.27f * ease);
				}
			}
			float yaw = ease > 0.5f ? 25f + t * 50f : 25f;
			miniCard(g, xx, yy, sx, sy, ang, ease, idx, yaw, ease > 0.5f ? Pose.WAVE : Pose.STAND, "g1_" + idx);
		}

		// переключатель стилей: можно кликать
		CardStyle[] st = CardStyle.values();
		int cw = (w - 8 - 3 * 4) / 4;
		boolean pressEdge = down && !wasDown, releaseEdge = !down && wasDown;
		for (int i = 0; i < st.length; i++) {
			int bx = x + 4 + i * (cw + 4), by = y + h - 22;
			boolean over = mx >= bx && mx < bx + cw && my >= by && my < by + 18;
			if (pressEdge && over) chipPress = i;
			if (releaseEdge && over && chipPress == i) gStyle = st[i];
			mbtn(g, bx, by, cw, 18, I18n.t(st[i].label), (over ? 0.8f : 0f), st[i] == gStyle ? 1 : 0);
		}
		if (releaseEdge) chipPress = -1;
		if (t < 4f && !inside) cursor(g, x + w * 0.5f + (float) Math.sin(t * 0.8f) * 60f, y + h * 0.45f); // подсказка-курсор
	}

	private float staticX(int i, float cx, float u) {
		float gg = i - 2f;
		return switch (gStyle) {
			case FAN -> cx + (float) Math.sin(Math.max(-0.95f, Math.min(0.95f, gg * 0.17f))) * 300f * u;
			case WAVE -> cx + gg * 92f * u;
			case FLOW -> {
				float gc = Math.max(-1f, Math.min(1f, gg));
				yield cx + gc * CW * 0.7f * u + (gg - gc) * 30f * u;
			}
			default -> cx + gg * 78f * u;
		};
	}

	// ------------------------------------------------------------------ демо 2: добавление скина

	private void topRow(GuiGraphics g, int x, int y, int w, int hl, float hv) {
		String[] labels = {"+ Добавить", "Генератор", "По нику", "Импорт..."};
		int bw = (w - 8 - 12) / 4;
		for (int i = 0; i < 4; i++) mbtn(g, x + 4 + i * (bw + 4), y + 6, bw, 18, I18n.t(labels[i]), i == hl ? hv : 0f, i == 0 ? 1 : 0);
	}

	private void demoAdd(GuiGraphics g, int x, int y, int w, int h, float t) {
		Theme th = th();
		t %= 10.5f;
		String cmd = "/skin url \"https://minesk.in/6c644a26cfab40cc892e7163822661ae\" classic";
		int bw = (w - 8 - 12) / 4;
		boolean libPhase = t < 1.9f || t >= 7.2f;
		if (libPhase) {
			float hv = t > 0.9f && t < 1.9f ? 1f : 0f;
			topRow(g, x, y, w, 0, hv);
			int base = y + h - 30;
			g.fillGradient(x + 4, base, x + w - 4, base + 6, th.shelfTop(), th.shelfBottom());
			if (t >= 7.2f) {
				float dropU = seg(t, 7.2f, 8.0f);
				float yy = lerp(y - 140f, base, dropU * dropU);
				if (dropU >= 1f) yy = base - (float) Math.abs(Math.sin((t - 8.0f) * 9f)) * 8f * (float) Math.exp(-(t - 8.0f) * 4f);
				miniCard(g, x + w / 2f, yy, 0.5f, 0.5f, 0f, 1f, DemoSkins.HERO, 25f + t * 40f, Pose.STAND, "g2");
				if (t > 8.2f) {
					String msg = "\u2713 " + I18n.t("Добавлено в библиотеку");
					int tw = this.font.width(msg) + 12;
					g.fill(x + w - tw - 6, y + 30, x + w - 6, y + 46, 0xE0102010);
					Ui.outline(g, x + w - tw - 6, y + 30, tw, 16, 0xFF3FA05A);
					g.drawString(this.font, msg, x + w - tw, y + 34, 0xFF6BFF8F);
				}
			}
			if (t < 2f) { // курсор идёт к кнопке «+ Добавить»
				float k = sm(seg(t, 0f, 0.9f));
				cursor(g, lerp(x + w - 24, x + 4 + bw / 2f, k), lerp(y + h - 24, y + 16, k));
				ripple(g, x + 4 + bw / 2f, y + 16, seg(t, 1.2f, 1.8f));
			}
		} else {
			float ph0 = t - 1.9f;
			g.drawString(this.font, I18n.t("Команда из сайта SkinRestorer:"), x + 8, y + 8, th.sub());
			int fw = Math.min(w - 120, 190);
			int typed = (int) Math.max(0, Math.min(cmd.length(), (t - 2.4f) * 22f));
			mfield(g, x + 8, y + 20, fw, 18, cmd.substring(0, typed), t < 5.5f, true, t);
			mbtn(g, x + 8, y + 42, fw, 18, I18n.t("Вставить из буфера"), 0f, 0);
			mbtn(g, x + 8, y + h - 30, fw, 18, I18n.t("Добавить в библиотеку"), seg(t, 6.2f, 6.4f) * (t < 7f ? 1f : 0f), 1);
			// превью справа
			float pv = sm(seg(t, 4.8f, 5.4f));
			if (pv > 0.01f) {
				float s = Math.min(1f, (h - 14f) / 150f) * pv;
				g.pose().pushMatrix();
				g.pose().translate((float) (x + w - 6 - 100 * s), (float) (y + 8));
				g.pose().scale(s, s);
				SkinPreviewCache.get("demo:" + DemoSkins.HERO).draw(g, 0, 0, false, 25f + t * 60f, Pose.STAND, "g2m");
				g.pose().popMatrix();
			}
			if (t > 4.8f) g.drawString(this.font, I18n.t("Модель: ") + "classic", x + 8, y + 66, 0xFF6BFF8F);
			// курсор к кнопке «Добавить»
			float k = sm(seg(t, 5.6f, 6.2f));
			if (t >= 5.5f) {
				cursor(g, lerp(x + w * 0.65f, x + 8 + fw / 2f, k), lerp(y + 20, y + h - 22, k));
				ripple(g, x + 8 + fw / 2f, y + h - 22, seg(t, 6.3f, 6.9f));
			}
		}
		// короткое затухание на стыках фаз
		float cover = Math.max(1f - Math.abs(t - 1.9f) / 0.18f, 1f - Math.abs(t - 7.2f) / 0.18f);
		if (cover > 0f) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Ui.alpha(0x000000, (int) (200 * clamp01(cover))));
	}

	// ------------------------------------------------------------------ демо 3: другие способы

	private void demoOther(GuiGraphics g, int x, int y, int w, int h, float t) {
		Theme th = th();
		float per = 3.6f;
		int tab = (int) (t / per) % 3;
		float tt = t % per;
		String[] names = {"Генератор", "По нику", "Импорт..."};
		int cw = (w - 8 - 8) / 3;
		for (int i = 0; i < 3; i++) mbtn(g, x + 4 + i * (cw + 4), y + 5, cw, 16, I18n.t(names[i]), 0f, i == tab ? 1 : 0);
		int cy = y + 30;
		int fw = Math.min(w - 20, 210);
		if (tab == 0) {
			String path = "C:\\skins\\hero.png";
			mfield(g, x + 8, cy, fw - 70, 18, path.substring(0, (int) Math.min(path.length(), tt * 14f)), true, true, tt);
			mbtn(g, x + 8 + fw - 66, cy, 66, 18, I18n.t("Обзор..."), tt > 1.3f && tt < 1.7f ? 1f : 0f, 0);
			mbtn(g, x + 8, cy + 24, fw, 18, I18n.t("Сгенерировать команду"), tt > 1.9f && tt < 2.3f ? 1f : 0f, 1);
			String res = "/skin url \"https://minesk.in/9f3c...\" classic";
			if (tt > 2.4f) mfield(g, x + 8, cy + 50, fw, 18, res.substring(0, (int) Math.min(res.length(), (tt - 2.4f) * 30f)), false, false, tt);
			g.drawString(this.font, I18n.t("Ссылка или PNG-файл:"), x + 8, cy - 10, th.sub());
		} else if (tab == 1) {
			String nick = "Notch";
			mfield(g, x + 8, cy, fw, 18, nick.substring(0, (int) Math.min(nick.length(), tt * 5f)), true, true, tt);
			mbtn(g, x + 8, cy + 24, fw, 18, I18n.t("Найти скин"), tt > 1.5f && tt < 1.9f ? 1f : 0f, 1);
			if (tt > 2.0f) {
				g.drawString(this.font, I18n.t("Найден: ") + "Notch (classic)", x + 8, cy + 48, 0xFF6BFF8F);
				float s = Math.min(0.55f, (h - 80f) / 150f);
				g.pose().pushMatrix();
				g.pose().translate((float) (x + w - 14 - 100 * s), (float) (y + 30));
				g.pose().scale(s, s);
				SkinPreviewCache.get("demo:0").draw(g, 0, 0, false, 25f + t * 60f, Pose.STAND, "g3n");
				g.pose().popMatrix();
			}
		} else {
			String[] lines = {"Prism Launcher" + I18n.t(": найдено ") + "3", "Modrinth App" + I18n.t(": найдено ") + "2",
					I18n.t("Аккаунт") + " (Notch)" + I18n.t(": найдено ") + "1"};
			mbtn(g, x + 8, cy, fw, 18, I18n.t("Найти новые скины"), tt < 0.6f ? 1f : 0f, 1);
			g.drawString(this.font, tt < 0.7f ? I18n.t("Поиск...") : I18n.t("Результат поиска:"), x + 8, cy + 26, th.text());
			for (int i = 0; i < 3; i++) {
				float a = sm(seg(tt, 0.9f + i * 0.5f, 1.3f + i * 0.5f));
				if (a > 0f) {
					g.drawString(this.font, "\u2713 " + lines[i], x + 12 + Math.round((1f - a) * 16f), cy + 40 + i * 12, Ui.alpha(0x6BFF8F, (int) (255 * a)));
				}
			}
		}
	}

	// ------------------------------------------------------------------ демо 4: надевание, КД, уведомление

	private void demoWear(GuiGraphics g, int x, int y, int w, int h, float t) {
		Theme th = th();
		t %= 10.5f;
		float u = Math.min(0.5f, (h - 70f) / (CH * 1.07f + 24f));
		float cx = x + w / 2f - 30, baseY = y + h - 52;
		g.fillGradient(x + 4, (int) baseY, x + w - 4, (int) baseY + 5, th.shelfTop(), th.shelfBottom());
		float click = seg(t, 1.5f, 2.0f);
		for (int i = 0; i < 3; i++) {
			float ease = i == 1 ? 1f : 0f;
			float bump = i == 1 ? (float) Math.sin(click * Math.PI) * 0.04f : 0f;
			miniCard(g, cx + (i - 1) * 56f * (u / 0.5f), baseY - 8f * u * ease, u * (0.8f + 0.27f * ease) + bump, u * (0.8f + 0.27f * ease) + bump,
					(i - 1) * 0.1f * (1f - ease), ease, i == 1 ? DemoSkins.HERO : (i == 0 ? 3 : 4), 25f + (i == 1 ? t * 60f : 0f), Pose.STAND, "g4_" + i);
		}
		if (t < 2.1f) {
			float k = sm(seg(t, 0f, 1.3f));
			cursor(g, lerp(x + w - 20, cx, k), lerp(y + 20, baseY - 50, k));
			ripple(g, cx, baseY - 50, seg(t, 1.5f, 2.1f));
		}
		if (t > 1.5f && t < 2.0f) g.fill((int) (cx - 28), (int) (baseY - 90), (int) (cx + 28), (int) baseY, Ui.alpha(0xFFFFFF, (int) (80 * (1f - (t - 1.5f) / 0.5f))));

		// чат
		int cy = y + h - 40;
		g.fill(x + 4, cy, x + w - 4, y + h - 4, 0xA0000000);
		String cmd = "/skin url \"https://minesk.in/6c64...\" classic";
		if (t > 2.0f) g.drawString(this.font, "> " + cmd.substring(0, (int) Math.max(0, Math.min(cmd.length(), (t - 2.0f) * 22f))), x + 8, cy + 5, 0xFFFFFFFF);
		if (t > 3.8f) g.drawString(this.font, "\u2713 " + I18n.t("Скин применён: ") + DemoSkins.NAMES[DemoSkins.HERO], x + 8, cy + 18, 0xFF6BFF8F);

		// кнопка «Надеть» с отсчётом КД
		int left = t > 4.0f && t < 8.2f ? (int) Math.ceil(30f * (1f - seg(t, 4.2f, 8.2f))) : 0;
		String lbl = t > 4.0f && t < 8.2f ? I18n.t("Подожди ") + Math.max(left, 1) + I18n.t(" с") : "\u25B6 " + I18n.t("Надеть");
		int bw2 = Math.min(110, w - 16);
		Ui.button(g, this.font, x + w - bw2 - 8, y + 8, bw2, 18, lbl, 0f, !(t > 4.0f && t < 8.2f), 1, th);
		if (t > 4.0f && t < 8.2f) g.fill(x + w - bw2 - 8, y + 26, x + w - bw2 - 8 + Math.round(bw2 * seg(t, 4.2f, 8.2f)), y + 28, th.accent());

		// уведомление о конце КД
		if (t > 8.4f) {
			float s = sm(seg(t, 8.4f, 8.8f));
			String msg = I18n.t("Скин снова можно менять");
			int tw = this.font.width(msg) + 36;
			int nx = x + w - Math.round((tw + 6) * s), ny = y + 34;
			Ui.panel(g, nx, ny, tw, 24, th);
			g.fill(nx + 3, ny + 3, nx + 6, ny + 21, th.accent());
			g.drawString(this.font, "\u2713", nx + 12, ny + 8, th.accent());
			g.drawString(this.font, msg, nx + 24, ny + 8, th.text());
		}
	}

	// ------------------------------------------------------------------ демо 5: сервер

	private void demoServer(GuiGraphics g, int x, int y, int w, int h, float t) {
		Theme th = th();
		t %= 10.5f;
		// мини-меню: три «кнопки» и превью слева
		int bw = Math.min(80, w / 3);
		for (int i = 0; i < 3; i++) g.fill(x + w / 2 - bw / 2 + 20, y + 10 + i * 18, x + w / 2 + bw / 2 + 20, y + 24 + i * 18, 0xFF5A5A5A);
		g.fill(x + w / 2 - bw / 2 + 20, y + 10 + 18, x + w / 2 + bw / 2 + 20, y + 24 + 18, t > 1.6f ? 0xFF7A7A95 : 0xFF5A5A5A);
		Ui.panel(g, x + 8, y + 8, 50, 74, th);
		g.pose().pushMatrix();
		g.pose().translate((float) (x + 12), (float) (y + 11));
		g.pose().scale(0.3f, 0.3f);
		SkinPreviewCache.get("demo:" + DemoSkins.HERO).draw(g, 0, 0, false, 25f + t * 50f, Pose.WAVE, "g5");
		g.pose().popMatrix();
		g.drawString(this.font, DemoSkins.NAMES[DemoSkins.HERO], x + 12, y + 70, th.text());
		if (t < 1.8f) {
			float k = sm(seg(t, 0f, 1.2f));
			cursor(g, lerp(x + w - 30, x + w / 2f + 20, k), lerp(y + 60, y + 36, k));
			ripple(g, x + w / 2f + 20, y + 36, seg(t, 1.3f, 1.8f));
		}
		int ly = y + 92;
		if (t > 2.0f && t < 4.4f) {
			g.drawString(this.font, I18n.t("Вход на сервер..."), x + 8, ly, th.text());
			g.fill(x + 8, ly + 12, x + w - 8, ly + 16, 0xFF202020);
			g.fill(x + 8, ly + 12, x + 8 + Math.round((w - 16) * seg(t, 2.0f, 4.2f)), ly + 16, th.accent());
		}
		if (t >= 4.4f) {
			float a = sm(seg(t, 4.4f, 4.9f));
			g.drawString(this.font, "\u2713 " + I18n.t("Команда /skin найдена на сервере"), x + 8, ly, Ui.alpha(0x6BFF8F, (int) (255 * a)));
		}
		if (t > 5.8f) {
			String cmd = "> /skin url \"https://minesk.in/6c64...\"";
			g.drawString(this.font, cmd.substring(0, (int) Math.min(cmd.length(), (t - 5.8f) * 24f)), x + 8, ly + 12, 0xFFFFFFFF);
		}
		if (t > 7.6f) g.drawString(this.font, "\u2713 " + I18n.t("Скин применён: ") + DemoSkins.NAMES[DemoSkins.HERO], x + 8, ly + 24, 0xFF6BFF8F);
		if (t > 7.6f) { // искры радости
			for (int i = 0; i < 8; i++) {
				float k = (t - 7.6f) * 1.2f + i * 0.1f;
				g.fill(x + 30 + (int) (Math.sin(i * 2.1) * 22 * k), y + 40 - (int) (k * 20), x + 32 + (int) (Math.sin(i * 2.1) * 22 * k), y + 42 - (int) (k * 20),
						Ui.alpha(0xFFD83B, (int) (200 * clamp01(1f - k))));
			}
		}
	}

	// ------------------------------------------------------------------ демо 6: папки и избранное

	private void miniFolder(GuiGraphics g, int x, int y, String label, int count, float pulse, int c1, int c2) {
		Theme th = th();
		int back = 0xB0D9A92E, front = 0xA8F7D56A;
		g.fill(x + 1, y, x + 17, y + 4, back);
		g.fill(x, y + 4, x + 40, y + 28, back);
		int[] skinCols = {0xFFD9A877, 0xFF3A6FD0};
		for (int k = 0; k < Math.min(2, c2 > 0 ? 2 : 0); k++) {
			int cx2 = x + 8 + k * 14 - Math.round(pulse * 3);
			g.fill(cx2, y - 4 - Math.round(pulse * 3), cx2 + 12, y + 14, th.cardTop() | 0xFF000000);
			g.fill(cx2 + 1, y - 3 - Math.round(pulse * 3), cx2 + 11, y + 4, skinCols[0]);
			g.fill(cx2 + 1, y + 4, cx2 + 11, y + 12, skinCols[1]);
		}
		g.fill(x, y + 10, x + 40, y + 28, front);
		g.fill(x + 2, y + 11, x + 38, y + 12, 0x55FFFFFF);
		if (pulse > 0f) Ui.outline(g, x - 1, y - 6, 42, 36, Ui.alpha(th.accent(), (int) (220 * pulse)));
		g.drawCenteredString(this.font, label, x + 20, y + 31, pulse > 0f ? th.accent() : th.text());
		g.drawCenteredString(this.font, String.valueOf(count), x + 20, y + 41, th.sub());
	}

	private void demoFolders(GuiGraphics g, int x, int y, int w, int h, float t) {
		Theme th = th();
		t %= 9.5f;
		Ui.panel(g, x + 4, y + 4, 52, h - 8, th);
		boolean dropped = t > 3.5f;
		float pulse = dropped ? clamp01(1f - (t - 3.5f) / 0.9f) : (t > 2.6f ? 0.6f : 0f);
		miniFolder(g, x + 10, y + 12, I18n.t("Все скины"), dropped ? 6 : 6, 0f, 0, 2);
		miniFolder(g, x + 10, y + 12 + 60, I18n.t("Избранное"), t > 5.6f ? 2 : 1, t > 5.6f ? clamp01(1f - (t - 5.6f) / 0.8f) : 0f, 0, 1);
		miniFolder(g, x + 10, y + 12 + 120 > y + h - 50 ? y + h - 56 : y + 12 + 120, "Halloween", dropped ? 4 : 3, pulse, 0, 2);

		// полка справа
		int base = y + h - 34;
		g.fillGradient(x + 62, base, x + w - 4, base + 5, th.shelfTop(), th.shelfBottom());
		float u = Math.min(0.42f, (h - 60f) / (CH * 1.07f + 24f));
		float cx = x + 62 + (w - 66) / 2f;
		for (int i = 0; i < 3; i++) {
			if (i == 1 && t > 1.0f && t < 3.5f) continue; // эту карточку несут
			if (i == 1 && dropped) continue;
			float ease = i == 1 ? 1f : 0f;
			int idx = i == 0 ? 0 : (i == 1 ? 2 : 4);
			float off = dropped && i == 2 ? -52f * u / 0.42f * sm(seg(t, 3.6f, 4.2f)) : 0f;
			miniCard(g, cx + (i - 1) * 54f + off, base - 6f * u * ease, u * (0.8f + 0.27f * ease), u * (0.8f + 0.27f * ease), 0f, ease, idx, 25f, Pose.STAND, "g6_" + i);
		}
		// звёздочка у первой карточки
		if (t > 4.8f && t < 6.4f) {
			float s = sm(seg(t, 4.8f, 5.2f));
			String star = "\u2605";
			g.pose().pushMatrix();
			g.pose().translate(cx - 54f + 6, base - 90f * u / 0.42f * 0.75f);
			g.pose().scale(1f + s, 1f + s);
			g.drawString(this.font, star, 0, 0, Ui.alpha(th.accent(), 255));
			g.pose().popMatrix();
		}
		// курсор переносит карточку за нижнюю полосу
		float stripX = cx, stripY = base - 8f;
		float folderX = x + 30, folderY = y + 12 + 120 > y + h - 50 ? y + h - 40 : y + 12 + 132;
		if (t < 4.2f) {
			float k1 = sm(seg(t, 0f, 0.9f)), k2 = sm(seg(t, 1.2f, 3.0f));
			float curX = t < 1.0f ? lerp(x + w - 20, stripX, k1) : lerp(stripX, folderX, k2);
			float curY = t < 1.0f ? lerp(y + 20, stripY, k1) : lerp(stripY, folderY, k2);
			if (t > 1.0f && t < 3.5f) { // «призрак» карточки следует за курсором
				miniCard(g, curX + 6, curY + 30, 0.2f, 0.2f, 0.15f, 1f, 2, 25f, Pose.STAND, "g6g");
			}
			cursor(g, curX, curY);
			ripple(g, folderX, folderY, seg(t, 3.2f, 3.9f));
			if (t > 1.0f && t < 1.3f) g.drawString(this.font, I18n.t("Отпустите на папку"), x + 62, y + 6, th.accent());
			if (t > 1.3f && t < 3.5f) g.drawString(this.font, I18n.t("Отпустите на папку"), x + 62, y + 6, th.accent());
		}
	}

	// ------------------------------------------------------------------ демо 7: плащи

	private static final int[] CAPE_COLORS = {0xFFB71C1C, 0xFF1565C0, 0xFF2E7D32, 0xFFF9A825, 0xFF6A1B9A, 0xFF00838F, 0xFFEF6C00, 0xFF455A64, 0xFFAD1457};

	private void demoCapes(GuiGraphics g, int x, int y, int w, int h, float t) {
		Theme th = th();
		int sel = (int) (t / 2.2f) % 9;
		float tt = t % 2.2f;
		int pw2 = 90, rowH = Math.min(52, (h - 22) / 3);
		Ui.panel(g, x + 4, y + 4, pw2, h - 8, th);
		g.drawString(this.font, I18n.t("Плащи"), x + 10, y + 8, th.sub());
		for (int r = 0; r < 3; r++) {
			int top = y + 20 + r * rowH, plank = top + rowH - 8;
			g.fillGradient(x + 6, plank, x + pw2 + 2, plank + 4, 0xFF7A5634, 0xFF3E2A18);
			g.fill(x + 6, plank, x + pw2 + 2, plank + 1, 0xFFB08050);
			for (int c = 0; c < 3; c++) {
				int idx = r * 3 + c, cx2 = x + 12 + c * 27;
				if (idx == sel) {
					g.fill(cx2 - 2, top + 2, cx2 + 22, plank, Ui.alpha(th.accent(), 40));
					Ui.outline(g, cx2 - 2, top + 2, 24, plank - top - 2, Ui.alpha(th.accent(), 220));
				}
				int ch = Math.min(rowH - 14, 28);
				g.fill(cx2 - 1, top + 5, cx2 + 21, top + 6, 0xFF9AA0B0);
				g.fill(cx2, top + 6, cx2 + 20, top + 6 + ch, CAPE_COLORS[idx]);
				g.fill(cx2, top + 6, cx2 + 20, top + 8, 0x40FFFFFF);
			}
		}
		// модель с плащом
		float s = Math.min(1f, (h - 10f) / 150f);
		float mxx = x + pw2 + 14 + (w - pw2 - 18 - 100 * s) / 2f, myy = y + 6;
		g.fill((int) (mxx + 28 * s), (int) (myy + 36 * s), (int) (mxx + 72 * s), (int) (myy + 116 * s), CAPE_COLORS[sel]); // плащ за спиной
		g.fill((int) (mxx + 28 * s), (int) (myy + 36 * s), (int) (mxx + 72 * s), (int) (myy + 40 * s), 0x40FFFFFF);
		g.pose().pushMatrix();
		g.pose().translate(mxx, myy);
		g.pose().scale(s, s);
		SkinPreviewCache.get("demo:0").draw(g, 0, 0, false, 25f, Pose.STAND, "g7");
		g.pose().popMatrix();
		int ci = sel;
		float curX = x + 12 + (ci % 3) * 27 + 12, curY = y + 20 + (ci / 3) * rowH + 22;
		float prevX = x + 12 + (((sel + 8) % 9) % 3) * 27 + 12, prevY = y + 20 + (((sel + 8) % 9) / 3) * rowH + 22;
		float k = sm(seg(tt, 0f, 0.8f));
		cursor(g, lerp(prevX, curX, k), lerp(prevY, curY, k));
		ripple(g, curX, curY, seg(tt, 0.85f, 1.5f));
	}

	// ------------------------------------------------------------------ демо 8: модель и позы

	private void demoPoses(GuiGraphics g, int x, int y, int w, int h, float t) {
		Theme th = th();
		Pose[] ps = Pose.values();
		int pi = (int) (t / 1.8f) % ps.length;
		float s = Math.min(1f, (h - 10f) / 150f);
		float yaw = 25f + (float) Math.sin(t * 1.2f) * 60f;
		g.pose().pushMatrix();
		g.pose().translate(x + 14f, (float) (y + 5));
		g.pose().scale(s, s);
		SkinPreviewCache.get("demo:3").draw(g, 0, 0, false, yaw, ps[pi], "g8");
		g.pose().popMatrix();
		// «рука» вращает модель
		float mxp = x + 14 + 50 * s;
		cursor(g, mxp + (float) Math.sin(t * 1.2f) * 24f, y + h * 0.5f);
		g.drawString(this.font, "\u21C4", (int) mxp - 3, y + h - 18, th.sub());
		// список поз
		int lx = x + w - 84, rowH = Math.min(14, (h - 8) / ps.length);
		for (int i = 0; i < ps.length; i++) {
			boolean cur = i == pi;
			int yy = y + 4 + i * rowH;
			g.fill(lx, yy, lx + 80, yy + rowH - 2, cur ? Ui.alpha(th.accent(), 70) : 0x33000000);
			if (cur) Ui.outline(g, lx, yy, 80, rowH - 2, Ui.alpha(th.accent(), 200));
			g.drawString(this.font, I18n.t(ps[i].label), lx + 4, yy + (rowH - 2 - 8) / 2, cur ? th.text() : th.sub());
		}
	}

	// ------------------------------------------------------------------ демо 9: превью в меню

	private void demoMenu(GuiGraphics g, int x, int y, int w, int h, float t) {
		Theme th = th();
		t %= 10.5f;
		int bw = Math.min(90, w / 3);
		for (int i = 0; i < 3; i++) g.fill(x + w / 2 - bw / 2 + 30, y + 14 + i * 20, x + w / 2 + bw / 2 + 30, y + 30 + i * 20, 0xFF5A5A5A);
		// иконка-кнопка с головой в углу
		drawLogo(g, x + w - 16, y + 14, 12);
		Ui.outline(g, x + w - 24, y + 6, 16, 16, Ui.alpha(th.accent(), 160));

		float move = t > 1.6f && t < 3.6f ? sm(seg(t, 1.6f, 3.4f)) : (t >= 3.6f && t < 5.0f ? 1f : (t >= 5.0f && t < 6.4f ? 1f - sm(seg(t, 5.0f, 6.2f)) : 0f));
		float grow = t > 6.8f && t < 9.4f ? sm(seg(t, 6.8f, 8.0f)) * (1f - sm(seg(t, 8.8f, 9.4f))) : 0f;
		float sc = 0.36f * (1f + 0.45f * grow);
		float wx = x + 10 + move * (w * 0.2f), wy = y + 6;
		g.pose().pushMatrix();
		g.pose().translate(wx, wy);
		g.pose().scale(sc, sc);
		Ui.panel(g, 0, 0, 116, 186, th);
		g.fillGradient(4, 5, 112, 150, Ui.alpha(th.glowA(), 0), Ui.alpha(th.glowA(), 50));
		SkinPreviewCache.get("demo:1").draw(g, 8, 6, false, 25f + t * 40f, Pose.STAND, "g9");
		int py2 = 186 - 27;
		g.fill(4, py2, 112, py2 + 5, Ui.mix(0xFF6E7590, th.accent(), (move > 0f && move < 1f) ? 0.6f : 0f));
		g.fill(4, py2 + 5, 112, py2 + 23, 0xFF14161F);
		for (int row = 0; row < 3; row++)
			for (int bx0 = row % 2 == 0 ? 0 : -6; bx0 < 108; bx0 += 12)
				g.fill(4 + Math.max(0, bx0), py2 + 5 + row * 6, 4 + Math.min(108, bx0 + 11), py2 + 10 + row * 6, 0xFF3A3F55);
		if (grow > 0f || t < 1f) { // угловые «ручки» изменения размера
			int col = Ui.alpha(th.accent(), 255);
			g.fill(110, 176, 116, 178, col);
			g.fill(114, 170, 116, 186, col);
		}
		g.pose().popMatrix();
		// курсор: берёт платформу, затем угол
		float platX = wx + 58 * sc, platY = wy + (186 - 18) * sc;
		float cornerX = wx + 114 * sc, cornerY = wy + 184 * sc;
		float curX, curY;
		if (t < 1.6f) {
			curX = lerp(x + w - 30, platX, sm(seg(t, 0f, 1.2f)));
			curY = lerp(y + h - 20, platY, sm(seg(t, 0f, 1.2f)));
		} else if (t < 6.4f) {
			curX = platX;
			curY = platY;
		} else if (t < 6.8f) {
			curX = lerp(platX, cornerX, sm(seg(t, 6.4f, 6.8f)));
			curY = lerp(platY, cornerY, sm(seg(t, 6.4f, 6.8f)));
		} else {
			curX = cornerX;
			curY = cornerY;
		}
		cursor(g, curX, curY);
		if (t > 1.6f && t < 3.6f) g.drawString(this.font, I18n.t("☰ тяните"), x + 10, y + h - 14, th.accent());
	}

	// ------------------------------------------------------------------ демо 10: темы и звуки

	private void demoThemes(GuiGraphics g, int x, int y, int w, int h, float t) {
		List<Theme> all = Theme.ALL;
		int sel = (int) (t / 1.5f) % all.size();
		float tt = t % 1.5f;
		Theme th = all.get(sel);
		// плитки тем
		int cw = 11, perRow = Math.max(1, (w - 12) / (cw + 2));
		for (int i = 0; i < all.size(); i++) {
			Theme ti = all.get(i);
			int cx2 = x + 6 + (i % perRow) * (cw + 2), cy2 = y + 5 + (i / perRow) * 9;
			g.fillGradient(cx2, cy2, cx2 + cw, cy2 + 7, ti.btnTop() | 0xFF000000, ti.cardBottom() | 0xFF000000);
			g.fill(cx2, cy2 + 5, cx2 + cw, cy2 + 7, ti.accent());
			Ui.outline(g, cx2, cy2, cw, 7, i == sel ? 0xFFFFFFFF : Ui.alpha(0xFFFFFF, 60));
		}
		// образец интерфейса в выбранной теме
		int sx = x + 6, sy = y + 52, sw = Math.min(w - 12, 220), shh = h - 66 - 30;
		g.fill(sx, sy, sx + sw, sy + shh, th.bgBottom() | 0xFF000000);
		Ui.panel(g, sx + 6, sy + 6, sw - 12, shh - 12, th);
		g.drawString(this.font, Ui.fit(this.font, I18n.t(th.name()), 120), sx + 12, sy + 11, th.text());
		Ui.button(g, this.font, sx + 12, sy + 24, 70, 16, I18n.t("Надевание"), (float) Math.abs(Math.sin(tt * 4.2f)), true, 1, th);
		Ui.button(g, this.font, sx + 88, sy + 24, 60, 16, I18n.t("Тест"), 0f, true, 0, th);
		g.fill(sx + 12, sy + 46, sx + 12 + (int) ((sw - 24) * (0.3f + 0.7f * clamp01(tt))), sy + 49, th.accent());
		// эквалайзер звуков
		int ex = x + w - 70, ey = y + 8;
		String[] names = {"Наведение", "Нажатие", "Надевание"};
		for (int i = 0; i < 3; i++) {
			g.drawString(this.font, I18n.t(names[i]), ex - 10, ey + i * 24 + 14, Ui.alpha(0xFFFFFF, 180));
			for (int b = 0; b < 6; b++) {
				float hgt = 3f + (float) Math.abs(Math.sin(t * (3f + i) + b * 0.9f + i)) * 12f;
				g.fill(ex - 10 + b * 5 + 40, ey + i * 24 + 12 - (int) hgt + 6, ex - 10 + b * 5 + 43 + 40, ey + i * 24 + 18 + 0, Ui.alpha(th.accent(), 200));
			}
		}
	}

	// ------------------------------------------------------------------ демо 11: настройки и язык

	private void demoSettings(GuiGraphics g, int x, int y, int w, int h, float t) {
		float tick = t / 1.6f;
		int step = (int) tick;
		float f = tick - step;
		String on = I18n.t("вкл"), off = I18n.t("выкл");
		String[] modes = {I18n.t("По карточке"), I18n.t("По кнопке"), I18n.t("Карточка и кнопка")};
		String[] langs = {I18n.t("Авто"), I18n.t("Русский"), "English"};
		String[] rnd = {off, I18n.t("при каждом входе"), I18n.t("скин дня")};
		String[] labels = {
				I18n.t("Надевание: ") + modes[step % 3],
				I18n.t("Автонадевание при входе: ") + (step % 2 == 0 ? on : off),
				I18n.t("Случайный скин: ") + rnd[step % 3],
				I18n.t("Уведомление о конце КД: ") + (step % 2 == 1 ? on : off),
				I18n.t("Язык / Language: ") + langs[step % 3],
		};
		int rowH = Math.min(24, (h - 8) / 5);
		for (int i = 0; i < 5; i++) {
			boolean active = i == step % 5;
			mbtn(g, x + 6, y + 4 + i * rowH, w - 12, rowH - 4, labels[i], active ? 1f - f : 0f, i == 0 ? 1 : 0);
			if (active && f < 0.35f) ripple(g, x + w / 2f, y + 4 + i * rowH + (rowH - 4) / 2f, f / 0.35f);
		}
		cursor(g, x + w / 2f + 30f, y + 4 + (step % 5) * rowH + (rowH - 4) / 2f + 3f);
	}
}
