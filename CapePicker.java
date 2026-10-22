package dev.skinlibrary.screen;

import dev.skinlibrary.CapeView;
import dev.skinlibrary.Capes;
import dev.skinlibrary.I18n;
import dev.skinlibrary.Input;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Плащи SkinsRestorer в виде карточек на полке, как карточки скинов в библиотеке: крупные, каждый плащ
 * вращается вокруг своей оси. Выбранная карточка подсвечена и приподнята. Листание: стрелки, колёсико мыши.
 * Рисуется и обрабатывается вручную (опрос мыши). Первая карточка — «без плаща».
 */
final class CapePicker {
	/** Минимальная высота области, при которой карточки ещё читаются. */
	static final int MIN_H = 105;
	private static final int TITLE_H = 14, RISE = 3, PLANK_H = 8, ARROW_W = 16, GAP = 6, MAX_CARD_H = 104;

	private final int x, y, w, h;
	private final Consumer<Capes.Cape> onPick; // null — без плаща
	private String selectedId;
	private float scrollPos, scrollTarget;
	private boolean reveal = true;
	private long lastNano = System.nanoTime();
	private int pressKind = -1, pressIndex = -1; // kind: 0 карточка, 1 «<», 2 «>», 3 «повторить»
	private boolean wasDown;

	CapePicker(int x, int y, int w, int h, String selectedId, Consumer<Capes.Cape> onPick) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.selectedId = selectedId == null ? "" : selectedId;
		this.onPick = onPick;
		Capes.ensureLoaded();
		wasDown = Input.leftDown(); // нажатие, начатое на прошлом экране, не считается кликом здесь
		if (wasDown) pressKind = -2;
	}

	private List<Capes.Cape> itemsCache;
	private int itemsSize = -1;
	private Object itemsRef;

	/** Ячейки: null — «без плаща», остальные — плащи. Список пересобирается только при изменении плащей. */
	private List<Capes.Cape> items() {
		if (itemsCache == null || itemsSize != Capes.LIST.size() || itemsRef != (Object) Capes.LIST) {
			List<Capes.Cape> l = new ArrayList<>(Capes.LIST.size() + 1);
			l.add(null);
			l.addAll(Capes.LIST);
			itemsCache = l;
			itemsSize = Capes.LIST.size();
			itemsRef = Capes.LIST;
		}
		return itemsCache;
	}

	private int cardH() {
		return Math.max(60, Math.min(MAX_CARD_H, h - TITLE_H - RISE - PLANK_H - 2));
	}

	private int cardW() {
		return Math.round(cardH() * 0.66f);
	}

	private int rowX() {
		return x + ARROW_W + 6;
	}

	private int rowW() {
		return w - 2 * (ARROW_W + 6);
	}

	private int visible() {
		return Math.max(1, (rowW() + GAP) / (cardW() + GAP));
	}

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Колёсико мыши над полкой; true — событие обработано. */
	boolean scroll(double mx, double my, double dy) {
		if (!inside((int) mx, (int) my, x, y, w, h) || dy == 0) return false;
		scrollTarget -= Math.signum(dy) * 2;
		return true;
	}

	void render(GuiGraphics g, Font font, int mx, int my, Theme th) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNano) / 1e9f);
		lastNano = now;
		float t = now / 1e9f;
		boolean classic = th.classic();

		List<Capes.Cape> items = items();
		int n = items.size();
		int cw = cardW(), ch = cardH(), step = cw + GAP;
		int vis = visible();
		float maxScroll = Math.max(0, n - vis);
		int selIdx = 0;
		for (int i = 1; i < n; i++) if (items.get(i).id.equals(selectedId)) selIdx = i;

		if (reveal) { // при открытии показываем выбранный плащ
			scrollTarget = Math.max(0, Math.min(maxScroll, selIdx - vis / 2f));
			scrollPos = scrollTarget;
			reveal = false;
		}
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scrollPos += (scrollTarget - scrollPos) * (1f - (float) Math.exp(-dt * 12f));
		if (Math.abs(scrollTarget - scrollPos) < 0.003f) scrollPos = scrollTarget;

		int rowX = rowX(), rowW = rowW();
		int bottom = y + h;
		int plank = bottom - PLANK_H - 2;      // верх полки
		int cardTop = plank - ch;               // верх карточки без подъёма
		boolean fewer = n <= vis;
		float off = fewer ? (rowW - (n * step - GAP)) / 2f : 0f;

		g.drawString(font, I18n.t("Плащи"), x + 8, y + 2, th.sub());

		// полка
		g.fillGradient(x, plank, x + w, plank + PLANK_H, th.shelfTop(), th.shelfBottom());
		g.fill(x, plank, x + w, plank + 1, Ui.mix(th.border(), th.accent(), 0.5f));

		int hover = -1, hoverKind = -1;
		boolean ready = Capes.state.equals("ok");
		if (!ready) {
			String msg = Capes.state.equals("loading") ? I18n.t("Загрузка...") : I18n.t("Не удалось загрузить");
			g.drawCenteredString(font, msg, x + w / 2, cardTop + ch / 2 - 10, th.sub());
			if (Capes.state.equals("error")) {
				int bx = x + w / 2 - 40, by = cardTop + ch / 2 + 2;
				boolean over = inside(mx, my, bx, by, 80, 16);
				g.fill(bx, by, bx + 80, by + 16, over ? 0xFF4A4A60 : 0xFF33334A);
				g.drawCenteredString(font, I18n.t("Повторить"), x + w / 2, by + 4, th.text());
				if (over) hoverKind = 3;
			}
		} else {
			g.enableScissor(rowX - 3, y + TITLE_H - 4, rowX + rowW + 3, bottom);
			for (int i = 0; i < n; i++) {
				int cx = Math.round(rowX + off + (i - scrollPos) * step);
				if (cx + cw < rowX - 3 || cx > rowX + rowW + 3) continue;
				boolean sel = i == selIdx;
				boolean over = inside(mx, my, Math.max(cx, rowX), cardTop - RISE, Math.min(cw, rowX + rowW - Math.max(cx, rowX)), ch + RISE)
						&& inside(mx, my, rowX, y, rowW, h);
				if (over) hover = i;
				drawCard(g, font, items.get(i), i, cx, cardTop, cw, ch, sel, over, t, th, classic);
			}
			g.disableScissor();
		}

		// стрелки
		int ay = cardTop + ch / 2 - 12;
		boolean canL = ready && scrollTarget > 0.01f, canR = ready && scrollTarget < maxScroll - 0.01f;
		boolean overL = canL && inside(mx, my, x + 3, ay, ARROW_W, 24);
		boolean overR = canR && inside(mx, my, x + w - 3 - ARROW_W, ay, ARROW_W, 24);
		if (ready && n > vis) {
			g.fill(x + 3, ay, x + 3 + ARROW_W, ay + 24, canL ? (overL ? 0xFF4A4A60 : 0xFF2A2A3C) : 0x802A2A3C);
			g.fill(x + w - 3 - ARROW_W, ay, x + w - 3, ay + 24, canR ? (overR ? 0xFF4A4A60 : 0xFF2A2A3C) : 0x802A2A3C);
			g.drawCenteredString(font, "<", x + 3 + ARROW_W / 2, ay + 8, canL ? th.text() : th.sub());
			g.drawCenteredString(font, ">", x + w - 3 - ARROW_W / 2, ay + 8, canR ? th.text() : th.sub());
		}
		if (overL) hoverKind = 1;
		if (overR) hoverKind = 2;

		// клики: нажатие и отпускание на одном и том же элементе
		boolean down = Input.leftDown();
		if (down && !wasDown) {
			pressKind = hoverKind >= 1 ? hoverKind : (hover >= 0 ? 0 : -1);
			pressIndex = hover;
		}
		if (!down && wasDown) {
			if (pressKind == 0 && hover == pressIndex && hover >= 0) {
				Capes.Cape c = items.get(hover);
				selectedId = c == null ? "" : c.id;
				onPick.accept(c);
			} else if (pressKind == 1 && hoverKind == 1) scrollTarget -= vis;
			else if (pressKind == 2 && hoverKind == 2) scrollTarget += vis;
			else if (pressKind == 3 && hoverKind == 3) Capes.reload();
			pressKind = -1;
		}
		wasDown = down;
	}

	private void drawCard(GuiGraphics g, Font font, Capes.Cape c, int i, int cx, int baseTop, int cw, int ch,
						  boolean sel, boolean over, float t, Theme th, boolean classic) {
		float ease = sel ? 1f : over ? 0.55f : 0f;
		int lift = classic ? 0 : (sel ? -RISE : over ? -2 : 0);
		int cy = baseTop + lift;

		if (sel && !classic) { // пульсирующее свечение, как у выбранной карточки скина
			float pulse = 0.75f + 0.25f * (float) Math.sin(t * 3.2f);
			for (int k = 1; k <= 4; k++) {
				int a = (int) (pulse * (46 - k * 8));
				if (a > 0) Ui.outline(g, cx - k, cy - k, cw + 2 * k, ch + 2 * k, Ui.alpha(th.accent(), a));
			}
		}
		Ui.roundPanel(g, cx, cy, cw, ch, th.cardTop(), th.cardBottom());
		Ui.roundOutline(g, cx, cy, cw, ch, Ui.mix(th.border(), th.accent(), ease));
		Ui.deco(g, cx, cy, cw, ch, th, ease);
		g.fill(cx + 2, cy + 1, cx + 5, cy + ch - 21, Ui.mix(Ui.alpha(th.border(), 255), th.accent(), ease));
		if (ease > 0.05f) g.fill(cx + 6, cy + 1, cx + cw - 3, cy + 2, Ui.alpha(th.accent(), (int) (200 * ease)));

		// превью плаща: буфер 100x150 вписывается в карточку над подписью
		float sp = Math.min((cw - 8) / (float) CapeView.W, (ch - 26) / (float) CapeView.H);
		float px = cx + (cw - CapeView.W * sp) / 2f, py = cy + 3;
		if (c == null) { // «без плаща»
			g.pose().pushMatrix();
			g.pose().translate(cx + cw / 2f, py + (CapeView.H * sp) / 2f - 8f);
			g.pose().scale(3f, 3f);
			g.drawCenteredString(font, "∅", 0, 0, th.sub());
			g.pose().popMatrix();
		} else {
			g.pose().pushMatrix();
			g.pose().translate(px, py);
			g.pose().scale(sp, sp);
			float yaw = (t * 45f + i * 40f) % 360f; // каждый плащ крутится вокруг своей оси
			CapeView.draw(g, c, yaw);
			g.pose().popMatrix();
		}

		String nm = c == null ? I18n.t("Без плаща") : c.name;
		while (font.width(nm) > cw - 12 && nm.length() > 1) nm = nm.substring(0, nm.length() - 1);
		g.drawString(font, nm, cx + 6, cy + ch - 20, th.text());
		if (c != null) g.drawString(font, I18n.t("Плащ"), cx + 6, cy + ch - 10, th.sub());
		else if (sel) g.drawString(font, "✓", cx + 6, cy + ch - 10, Ui.alpha(th.accent(), 230));

		int dim = (int) ((1f - ease) * 0x60);
		if (dim > 0) g.fill(cx, cy, cx + cw, cy + ch, dim << 24);
	}
}
