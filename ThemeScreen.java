package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.Input;
import dev.skinlibrary.Sfx;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Галерея тем: вкладки «Полные / Цветные / Классика», плитки с мини-образцом; под курсором плитка оживает (настоящий фон темы). */
public class ThemeScreen extends ThemedScreen {
	private static final int TW = 104, TH = 66, GAP = 8, TOP = 52;
	private static final String[] TABS = {"Полные", "Игры", "Цветные", "Классика"};
	private static final int[] GROUPS = {0, 3, 1, 2}; // какая вкладка какую группу тем показывает
	private static final String NOTICE = "Важно: темы-игры — неофициальные фан-оформления, нарисованные кодом. Логотипы, спрайты, музыка и звуки оригинальных игр защищены авторским правом и не копируются в мод, поэтому оформление лишь напоминает игры, а не повторяет их 1:1. Названия игр принадлежат их правообладателям.";
	private static int tab = -1;
	private final Screen parent;
	private float scroll, scrollTarget;
	private boolean wasDown = true;
	private long lastNano = System.nanoTime();

	public ThemeScreen(Screen parent) {
		super(Component.literal(I18n.t("Темы")));
		this.parent = parent;
		if (tab < 0) tab = Theme.current().group();
	}

	@Override
	protected void init() {
		addBackground();
		addTitle(I18n.t("Темы"));
		int bw = Math.min(78, (this.width - 20) / 4 - 4);
		int x0 = this.width / 2 - (4 * bw + 12) / 2;
		for (int i = 0; i < 4; i++) {
			final int k = i;
			fancy(() -> (tab == GROUPS[k] ? "• " : "") + I18n.t(TABS[k]), x0 + i * (bw + 4), 28, bw, 20,
					b -> { tab = GROUPS[k]; scroll = scrollTarget = 0; }, null, tab == GROUPS[i] ? 1 : 0);
		}
		fancy(() -> I18n.t("Назад"), this.width / 2 - 44, this.height - 26, 88, 20, b -> this.onClose(), null, 0);
		this.addRenderableOnly((g, mx, my, d) -> renderGrid(g, mx, my));
	}

	private List<Theme> shown() {
		List<Theme> out = new ArrayList<>();
		for (Theme t : Theme.ALL) if (t.group() == tab) out.add(t);
		return out;
	}

	private int cols() {
		return Math.max(1, (this.width - 16 + GAP) / (TW + GAP));
	}

	private void renderGrid(GuiGraphics g, int mx, int my) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNano) / 1e9f);
		lastNano = now;
		List<Theme> list = shown();
		int cols = cols(), rows = (list.size() + cols - 1) / cols;
		int gw = cols * TW + (cols - 1) * GAP;
		int gx = (this.width - gw) / 2;
		int bottom = this.height - 32;
		int viewH = bottom - TOP;
		List<net.minecraft.util.FormattedCharSequence> note = tab == 3
				? this.font.split(Component.literal(I18n.t(NOTICE)), Math.max(60, this.width - 28)) : List.of();
		int noteH = note.isEmpty() ? 0 : note.size() * 10 + 14;
		float maxScroll = Math.max(0, noteH + rows * (TH + GAP) - GAP - viewH);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 14f));
		boolean down = Input.leftDown();
		boolean click = down && !wasDown;
		wasDown = down;
		Theme cur = Theme.current();
		g.enableScissor(0, TOP - 2, this.width, bottom);
		if (noteH > 0) { // предупреждение об авторских правах на вкладке игр
			int ny = TOP - Math.round(scroll);
			g.fill(gx, ny, gx + gw, ny + noteH - 6, 0xB0000000);
			Ui.outline(g, gx, ny, gw, noteH - 6, Ui.alpha(cur.accent(), 180));
			g.drawString(this.font, "\u24D8", gx + 5, ny + 4, cur.accent() | 0xFF000000);
			for (int k = 0; k < note.size(); k++) g.drawString(this.font, note.get(k), gx + 16, ny + 4 + k * 10, 0xFFE8E8E8);
		}
		for (int i = 0; i < list.size(); i++) {
			Theme t = list.get(i);
			int x = gx + (i % cols) * (TW + GAP), y = TOP + noteH + (i / cols) * (TH + GAP) - Math.round(scroll);
			if (y + TH < TOP - 2 || y > bottom) continue;
			boolean hov = mx >= x && mx < x + TW && my >= Math.max(y, TOP) && my < Math.min(y + TH, bottom);
			drawTile(g, t, x, y, hov, t == cur);
			if (hov && click) {
				Sfx.click();
				SkinStore.setThemeId(t.id());
			}
		}
		g.disableScissor();
		if (maxScroll > 0) { // полоса прокрутки
			int bh = Math.max(14, (int) (viewH * viewH / (viewH + maxScroll)));
			int by = TOP + (int) ((viewH - bh) * (scroll / maxScroll));
			g.fill(this.width - 5, TOP, this.width - 3, bottom, 0x30FFFFFF);
			g.fill(this.width - 5, by, this.width - 3, by + bh, Ui.alpha(cur.accent(), 200));
		}
	}

	/** Плитка: фон темы (живой при наведении), образец панели в её форме, кнопка, название. */
	private void drawTile(GuiGraphics g, Theme t, int x, int y, boolean hov, boolean selected) {
		g.fill(x - 1, y - 1, x + TW + 1, y + TH + 1, 0xFF000000);
		g.enableScissor(x, Math.max(y, TOP - 2), x + TW, Math.min(y + TH, this.height - 32));
		if (hov) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(TW / (float) this.width, TH / (float) this.height); // настоящий фон темы, уменьшенный
			Indie.noDrag = true;
			Ui.background(g, this.width, this.height, openTime, t);
			Indie.noDrag = false;
			g.pose().popMatrix();
		} else {
			g.fillGradient(x, y, x + TW, y + TH, t.bgTop() | 0xFF000000, t.bgBottom() | 0xFF000000);
			g.fillGradient(x, y + TH - 22, x + TW, y + TH, Ui.alpha(t.glowA(), 0), Ui.alpha(t.glowA(), 70));
		}
		// образец карточки в форме темы
		int px = x + 10, py = y + 8, pw = 34, ph = TH - 22;
		if (t.classic()) {
			g.fill(px, py, px + pw, py + ph, t.cardTop() | 0xFF000000);
			Ui.outline(g, px, py, pw, ph, t.border() | 0xFF000000);
		} else {
			Ui.shapePanel(t, g, px, py, pw, ph, t.cardTop(), t.cardBottom());
			Ui.shapeOutline(t, g, px, py, pw, ph, hov ? t.accent() : t.border());
			Ui.deco(g, px, py, pw, ph, t, hov ? 1f : 0f);
		}
		// образец кнопки
		int bx = x + 50, by = y + 14, bw = 44, bh = 12;
		if (Indie.textMenu(t)) { // в этих играх меню — просто слова
			g.fill(bx + 6, by + 1, bx + bw - 6, by + 3, Ui.alpha(t.text(), 230));
			g.fill(bx + 10, by + 7, bx + bw - 10, by + 9, Ui.alpha(t.text(), 120));
		} else if (t.look().liquid()) {
			g.fill(bx, by, bx + bw, by + bh, 0xFF05040A);
			Ui.outline(g, bx, by, bw, bh, Ui.alpha(OilField.hsv(0.55f + (System.nanoTime() / 4e9f % 1f), 0.8f, 0.8f), 220));
		} else {
			g.fillGradient(bx, by, bx + bw, by + bh, t.btnTop() | 0xFF000000, t.btnBottom() | 0xFF000000);
			Ui.outline(g, bx, by, bw, bh, t.border() | 0xFF000000);
		}
		g.fill(bx, by + 18, bx + (int) (bw * 0.7f), by + 20, t.accent() | 0xFF000000);
		g.disableScissor();
		// подпись
		List<net.minecraft.util.FormattedCharSequence> nm = this.font.split(Component.literal(I18n.t(t.name())), TW - (selected ? 22 : 10));
		int lines = Math.max(1, Math.min(2, nm.size())); // длинные названия переносятся на 2 строки
		int ly = y + TH - 3 - lines * 9;
		g.fill(x, ly - 2, x + TW, y + TH, 0xB0000000);
		for (int k = 0; k < lines; k++) g.drawString(this.font, nm.get(k), x + 5, ly + k * 9, 0xFFFFFFFF);
		Ui.outline(g, x - 1, y - 1, TW + 2, TH + 2, selected ? t.accent() | 0xFF000000 : hov ? 0xFFFFFFFF : Ui.alpha(0xFFFFFF, 50));
		if (selected) g.drawString(this.font, "✔", x + TW - 12, y + TH - 12, t.accent() | 0xFF000000);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget -= (float) scrollY * 36f;
		return true;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
