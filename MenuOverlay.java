package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.Input;
import dev.skinlibrary.Pose;
import dev.skinlibrary.SkinEntry;
import dev.skinlibrary.SkinPreview;
import dev.skinlibrary.SkinPreviewCache;
import dev.skinlibrary.SkinStore;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Плавающие элементы в главном меню и меню паузы:
 *  - компактная кнопка-иконка «Библиотека скинов»;
 *  - превью скина на красивом фоне с пиксельной платформой. За платформу превью переносится,
 *    за любой из четырёх углов — меняет размер (как окно), модель вращается мышью.
 * Элементы никогда не заходят на родные кнопки меню и друг на друга.
 */
public final class MenuOverlay {
	private static final int BTN = 20, BTN_EXP = 124;
	private static final int PW0 = 116, PH0 = 186;
	private static final float DEFAULT_SCALE = 0.85f, MIN_SCALE = 0.55f, MAX_SCALE = 1.5f;
	private static final int HS = 9; // размер угловой «ручки» изменения размера
	private static final int MARGIN = 3;

	private static final Map<Screen, Integer> GENERATION = new WeakHashMap<>();
	private static Screen pendingOpen;
	private static Screen pendingGuide;
	private static String key = "pause";

	// кнопка
	private static int bx = 6, by = 6, bOffX, bOffY, bStartX, bStartY;
	private static boolean bPress, bMoved;
	private static float bHover;
	private static int bDir;

	// превью
	private static int px, py, pOffX, pOffY;
	private static float pScale = DEFAULT_SCALE;
	private static boolean pPress, pVisible;
	private static float pHover;
	private static int rCorner = -1, rAx, rAy;
	private static final PreviewDrag model = new PreviewDrag();
	// «пружинное» смещение отображения относительно логической позиции (плавное следование, отскок при отпускании)
	private static float oX, oY, vX, vY, oS, vS;

	private static boolean wasDown;
	private static long lastNano = System.nanoTime();
	private static final long START = System.nanoTime();

	private MenuOverlay() {}

	public static void attach(Screen screen, String screenKey) {
		key = screenKey;
		int gen = GENERATION.merge(screen, 1, Integer::sum);
		bPress = false;
		bMoved = false;
		pPress = false;
		rCorner = -1;
		place(screen);
		ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
			if (GENERATION.getOrDefault(s, 0) != gen) return;
			frame(s, g, mx, my);
		});
	}

	/** Показать гайд (первый запуск) вне цикла отрисовки. */
	public static void requestGuide(Screen from) {
		pendingGuide = from;
	}

	/** Открывает экран вне цикла отрисовки. */
	public static void tick(Minecraft mc) {
		if (pendingGuide != null) {
			Screen p = pendingGuide;
			pendingGuide = null;
			if (mc.screen == p) mc.setScreen(new GuideScreen(p));
		}
		if (pendingOpen != null) {
			Screen p = pendingOpen;
			pendingOpen = null;
			if (mc.screen == p) mc.setScreen(new SkinLibraryScreen(p));
		}
	}

	// ------------------------------------------------------------------ геометрия

	private static int pw() {
		return Math.round(PW0 * pScale);
	}

	private static int ph() {
		return Math.round(PH0 * pScale);
	}

	private static boolean overlap(int ax, int ay, int aw, int ah, int bx2, int by2, int bw, int bh) {
		return ax < bx2 + bw && ax + aw > bx2 && ay < by2 + bh && ay + ah > by2;
	}

	/** Свободен ли прямоугольник: внутри экрана, не на родных кнопках, не на логотипе, не на другом элементе. */
	private static boolean free(Screen s, int x, int y, int w, int h, int[] other) {
		if (x < 0 || y < 0 || x + w > s.width || y + h > s.height) return false;
		int mx = x - MARGIN, my = y - MARGIN, mw = w + 2 * MARGIN, mh = h + 2 * MARGIN;
		for (GuiEventListener c : s.children()) {
			if (c instanceof AbstractWidget a && a.visible
					&& overlap(mx, my, mw, mh, a.getX(), a.getY(), a.getWidth(), a.getHeight())) return false;
		}
		if (other != null && overlap(mx, my, mw, mh, other[0], other[1], other[2], other[3])) return false;
		if (s instanceof TitleScreen && overlap(x, y, w, h, s.width / 2 - 132, 18, 264, 84)) return false;
		return true;
	}

	private static int[] nearestFree(Screen s, int x, int y, int w, int h, int[] other) {
		x = Math.max(0, Math.min(s.width - w, x));
		y = Math.max(0, Math.min(s.height - h, y));
		if (free(s, x, y, w, h, other)) return new int[]{x, y};
		int max = Math.max(s.width, s.height);
		for (int r = 4; r <= max; r += 4) {
			for (int dx = -r; dx <= r; dx += 4) {
				if (free(s, x + dx, y - r, w, h, other)) return new int[]{x + dx, y - r};
				if (free(s, x + dx, y + r, w, h, other)) return new int[]{x + dx, y + r};
			}
			for (int dy = -r + 4; dy <= r - 4; dy += 4) {
				if (free(s, x - r, y + dy, w, h, other)) return new int[]{x - r, y + dy};
				if (free(s, x + r, y + dy, w, h, other)) return new int[]{x + r, y + dy};
			}
		}
		return null;
	}

	/** Перемещение «со скольжением» вдоль препятствий. */
	private static int[] slide(Screen s, int ox, int oy, int nx, int ny, int w, int h, int[] other) {
		nx = Math.max(0, Math.min(s.width - w, nx));
		ny = Math.max(0, Math.min(s.height - h, ny));
		if (free(s, nx, ny, w, h, other)) return new int[]{nx, ny};
		if (free(s, nx, oy, w, h, other)) return new int[]{nx, oy};
		if (free(s, ox, ny, w, h, other)) return new int[]{ox, ny};
		return new int[]{ox, oy};
	}

	private static int[] previewRect() {
		return SkinStore.previewEnabled() && pVisible ? new int[]{px, py, pw(), ph()} : null;
	}

	private static int[] buttonRect() {
		return new int[]{bx, by, BTN, BTN};
	}

	private static void place(Screen s) {
		int[] sp = SkinStore.pos("prevs_" + key);
		pScale = sp != null ? Math.max(MIN_SCALE, Math.min(MAX_SCALE, sp[0] / 100f)) : DEFAULT_SCALE;

		int[] pp = SkinStore.pos("prev_" + key);
		pVisible = false;
		// если на экране не хватает места — пробуем уменьшить превью
		for (float sc = pScale; sc >= MIN_SCALE - 0.001f; sc -= 0.05f) {
			int w = Math.round(PW0 * sc), h = Math.round(PH0 * sc);
			int dx0 = pp != null ? pp[0] : s.width / 2 - 100 - 10 - w;
			int dy0 = pp != null ? pp[1] : (s.height - h) / 2;
			int[] r = nearestFree(s, dx0, dy0, w, h, null);
			if (r != null) {
				pScale = sc;
				px = r[0];
				py = r[1];
				pVisible = true;
				break;
			}
		}

		int[] bp = SkinStore.pos("btn_" + key);
		int bx0 = bp != null ? bp[0] : 6;
		int by0 = bp != null ? bp[1] : 6;
		int[] rb = nearestFree(s, bx0, by0, BTN, BTN, previewRect());
		if (rb != null) {
			bx = rb[0];
			by = rb[1];
		} else {
			bx = 0;
			by = 0;
		}
	}

	// ------------------------------------------------------------------ кадр

	private static void frame(Screen screen, GuiGraphics g, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		Theme th = Theme.current();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNano) / 1e9f);
		lastNano = now;
		float t = (now - START) / 1e9f;

		boolean down = Input.leftDown();
		boolean previewOn = previewRect() != null;
		int w = pw(), h = ph();
		final int oldPx = px, oldPy = py;
		final float oldScale = pScale;

		// ---- превью: углы (размер), платформа (перенос), модель (вращение)
		int hoverCorner = -1;
		boolean overPanel = false, overPlat = false;
		if (previewOn) {
			overPanel = mx >= px && mx < px + w && my >= py && my < py + h;
			if (rCorner < 0 && !pPress) {
				for (int c = 0; c < 4; c++) {
					int cx = (c % 2 == 0) ? px : px + w - HS;
					int cy = c < 2 ? py : py + h - HS;
					if (mx >= cx && mx < cx + HS && my >= cy && my < cy + HS) hoverCorner = c;
				}
			}
			int platX = px + Math.round(4 * pScale), platY = py + Math.round((PH0 - 27) * pScale);
			int platW = Math.round((PW0 - 8) * pScale), platH = Math.round(23 * pScale);
			overPlat = hoverCorner < 0 && mx >= platX && mx < platX + platW && my >= platY && my < platY + platH;

			if (down && !wasDown) {
				if (hoverCorner >= 0) {
					rCorner = hoverCorner;
					rAx = (hoverCorner % 2 == 0) ? px + w : px; // якорь — противоположный угол
					rAy = hoverCorner < 2 ? py + h : py;
				} else if (overPlat) {
					pPress = true;
					pOffX = mx - px;
					pOffY = my - py;
				}
			}
			if (rCorner >= 0) {
				if (down) {
					float dx = (rCorner % 2 == 0) ? rAx - mx : mx - rAx;
					float dy = rCorner < 2 ? rAy - my : my - rAy;
					float ns = (Math.max(0, dx) * PW0 + Math.max(0, dy) * PH0) / (float) (PW0 * PW0 + PH0 * PH0);
					ns = Math.max(MIN_SCALE, Math.min(MAX_SCALE, ns));
					int nw = Math.round(PW0 * ns), nh = Math.round(PH0 * ns);
					int nx = (rCorner % 2 == 0) ? rAx - nw : rAx;
					int ny = rCorner < 2 ? rAy - nh : rAy;
					if (free(screen, nx, ny, nw, nh, buttonRect())) {
						pScale = ns;
						px = nx;
						py = ny;
					}
				} else {
					rCorner = -1;
					SkinStore.setPos("prev_" + key, px, py);
					SkinStore.setPos("prevs_" + key, Math.round(pScale * 100), 0);
				}
			} else if (pPress) {
				if (down) {
					int[] r = slide(screen, px, py, mx - pOffX, my - pOffY, w, h, buttonRect());
					px = r[0];
					py = r[1];
				} else {
					pPress = false;
					SkinStore.setPos("prev_" + key, px, py);
				}
			}
			w = pw();
			h = ph();
			model.update(mx, my, px + Math.round(8 * pScale), py + Math.round(6 * pScale),
					Math.round(100 * pScale), Math.round(150 * pScale), true, pPress || rCorner >= 0 || hoverCorner >= 0);
		}

		// ---- плавное следование: логическая позиция меняется сразу, а отображение догоняет её пружиной
		if (previewOn) {
			oX -= (px - oldPx);
			oY -= (py - oldPy);
			oS -= (pScale - oldScale);
			if (th.classic()) {
				oX = oY = oS = vX = vY = vS = 0f;
			} else {
				int steps = 4;
				float hh = dt / steps;
				for (int i = 0; i < steps; i++) {
					vX += (-260f * oX - 24f * vX) * hh;
					vY += (-260f * oY - 24f * vY) * hh;
					vS += (-300f * oS - 26f * vS) * hh;
					oX += vX * hh;
					oY += vY * hh;
					oS += vS * hh;
				}
			}
		}

		// ---- кнопка: направление раскрытия без наезда на кнопки меню
		int[] pr = previewRect();
		if (free(screen, bx, by, BTN_EXP, BTN, pr)) bDir = 1;
		else if (free(screen, bx + BTN - BTN_EXP, by, BTN_EXP, BTN, pr)) bDir = -1;
		else bDir = 0;

		int wNow = BTN + Math.round((BTN_EXP - BTN) * bHover);
		int dx = bDir < 0 ? bx + BTN - wNow : bx;
		boolean inside = mx >= dx && mx < dx + wNow && my >= by && my < by + BTN;

		if (down && !wasDown && inside) {
			bPress = true;
			bMoved = false;
			bStartX = mx;
			bStartY = my;
			bOffX = mx - bx;
			bOffY = my - by;
		}
		if (bPress) {
			if (down) {
				if (!bMoved && (Math.abs(mx - bStartX) > 3 || Math.abs(my - bStartY) > 3)) bMoved = true;
				if (bMoved) {
					int[] r = slide(screen, bx, by, mx - bOffX, my - bOffY, BTN, BTN, pr);
					bx = r[0];
					by = r[1];
				}
			} else {
				bPress = false;
				if (bMoved) SkinStore.setPos("btn_" + key, bx, by);
				else pendingOpen = screen;
			}
		}
		wasDown = down;

		boolean expand = (inside || bPress) && !bMoved && bDir != 0;
		if (th.classic()) bHover = expand ? 1f : 0f;
		else bHover += ((expand ? 1f : 0f) - bHover) * (1f - (float) Math.exp(-dt * 16f));
		if (th.classic()) pHover = (overPlat || pPress) ? 1f : 0f;
		else pHover += (((overPlat || pPress) ? 1f : 0f) - pHover) * (1f - (float) Math.exp(-dt * 14f));

		// ---- отрисовка
		if (previewOn) {
			float vx = px + oX, vy = py + oY, vs = Math.max(MIN_SCALE * 0.8f, pScale + oS);
			float tilt = Math.max(-0.3f, Math.min(0.3f, (oX + vX * 0.02f) * 0.014f)); // инерционный наклон: движение вправо — верх уходит влево
			drawPreview(g, mc, th, t, vx, vy, vs, tilt);
			drawHandles(g, th, hoverCorner, overPanel, vx, vy, vs, tilt);
		}
		drawButton(g, mc, th, inside);
	}

	// ------------------------------------------------------------------ кнопка

	private static void drawButton(GuiGraphics g, Minecraft mc, Theme th, boolean inside) {
		int wNow = BTN + Math.round((BTN_EXP - BTN) * bHover);
		int dx = bDir < 0 ? bx + BTN - wNow : bx;
		Ui.button(g, mc.font, dx, by, wNow, BTN, "", Math.max(bHover, inside ? 0.6f : 0f), true, 1, th);
		drawIcon(g, bDir < 0 ? dx + wNow - BTN : dx, by);
		if (bHover > 0.45f) {
			int a = (int) (255 * Math.min(1f, (bHover - 0.45f) / 0.55f));
			int tx = bDir < 0 ? dx + 6 : dx + BTN + 2;
			g.drawString(mc.font, I18n.t("Скины"), tx, by + 6, Ui.alpha(th.text(), a));
		}
		if (bMoved && bPress) Ui.outline(g, bx - 1, by - 1, BTN + 2, BTN + 2, Ui.alpha(th.accent(), 200));
	}

	/** Иконка: пиксельная голова игрока 12x12. */
	private static void drawIcon(GuiGraphics g, int ix, int iy) {
		final int hair = 0xFF3B2A1A, skin = 0xFFD9A877, eye = 0xFF3A5FCD, mouth = 0xFF8B5A3C;
		int[][] grid = {
				{1, 1, 1, 1, 1, 1},
				{1, 2, 2, 2, 2, 1},
				{2, 3, 2, 2, 3, 2},
				{2, 2, 2, 2, 2, 2},
				{2, 2, 4, 4, 2, 2},
				{0, 2, 2, 2, 2, 0},
		};
		int ox = ix + 4, oy = iy + 4;
		for (int r = 0; r < 6; r++) {
			for (int c = 0; c < 6; c++) {
				int v = grid[r][c];
				if (v == 0) continue;
				int col = v == 1 ? hair : v == 2 ? skin : v == 3 ? eye : mouth;
				g.fill(ox + c * 2, oy + r * 2, ox + c * 2 + 2, oy + r * 2 + 2, col);
			}
		}
	}

	// ------------------------------------------------------------------ превью (рисуется в локальных координатах со масштабом)

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	private static void drawPreview(GuiGraphics g, Minecraft mc, Theme th, float t, float vx, float vy, float vs, float tilt) {
		g.pose().pushMatrix();
		g.pose().translate(vx + PW0 * vs / 2f, vy + PH0 * vs);   // поворачиваем вокруг основания платформы
		g.pose().rotate(tilt);
		g.pose().translate(-PW0 * vs / 2f, -PH0 * vs);
		g.pose().scale(vs, vs);

		Ui.panel(g, 0, 0, PW0, PH0, th);
		if (!th.classic()) {
		int pulse = (int) (40 + 16 * Math.sin(t * 1.6));
		g.fillGradient(4, 5, PW0 - 4, PH0 - 30, Ui.alpha(th.glowA(), 0), Ui.alpha(th.glowA(), pulse));
		g.fillGradient(4, 5, PW0 - 4, 40, Ui.alpha(th.glowB(), 38), Ui.alpha(th.glowB(), 0));
		// луч света сверху: расширяется книзу, слегка «дышит»
		float breathe = 0.85f + 0.15f * (float) Math.sin(t * 1.3f);
		for (int yy = 6; yy < PH0 - 30; yy += 4) {
			float k = (yy - 6) / (float) (PH0 - 36);
			int half = 12 + (int) (36 * k);
			int a1 = (int) ((1f - k * 0.6f) * 20 * breathe);
			g.fill(PW0 / 2 - half, yy, PW0 / 2 + half, yy + 4, Ui.alpha(th.accent(), a1));
			g.fill(PW0 / 2 - half / 2, yy, PW0 / 2 + half / 2, yy + 4, Ui.alpha(0xFFFFFF, a1 / 2));
		}
		Ui.outline(g, 6, 7, PW0 - 12, PH0 - 42, Ui.alpha(th.accent(), 26)); // тонкая внутренняя рамка «витрины»
		for (int i = 0; i < 12; i++) {
			float seed = i * 7.31f + 1.7f;
			float rx = frac((float) Math.sin(seed) * 4375.5453f);
			float ry = frac((float) Math.sin(seed * 1.9f) * 2463.6345f);
			float yy = 1f - ((t * (0.05f + 0.06f * rx) + ry) % 1f);
			float life = (float) Math.sin(Math.PI * yy);
			int sx = 8 + (int) (rx * (PW0 - 18)), sy = 8 + (int) (yy * (PH0 - 46));
			int pc = i % 2 == 0 ? th.glowA() : th.glowB();
			g.fill(sx, sy, sx + 1, sy + 1, Ui.alpha(pc, (int) (130 * life)));
			if (i % 4 == 0) { // искорка-крестик
				int ca = (int) (90 * life);
				g.fill(sx - 1, sy, sx + 2, sy + 1, Ui.alpha(0xFFFFFF, ca));
				g.fill(sx, sy - 1, sx + 1, sy + 2, Ui.alpha(0xFFFFFF, ca));
			}
		}

		}

		SkinEntry e = SkinStore.activeEntry();
		if (e == null) {
			g.drawCenteredString(mc.font, I18n.t("Нет скина"), PW0 / 2, 62, th.sub());
			g.drawCenteredString(mc.font, I18n.t("Добавьте в"), PW0 / 2, 76, th.sub());
			g.drawCenteredString(mc.font, I18n.t("библиотеке"), PW0 / 2, 86, th.sub());
		} else {
			SkinPreview p = SkinPreviewCache.get(e.source());
			p.draw(g, 8, 6, e.isSlim(), model.yaw, Pose.current(), "menu");
		}

		drawPlatform(g, mc, th, e);
		g.pose().popMatrix();
	}

	/** Плоский эллипс (для теней и свечения на платформе). */
	private static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int col) {
		for (int dy = -ry; dy <= ry; dy++) {
			int hw = (int) Math.round(rx * Math.sqrt(1.0 - (dy * dy) / (double) ((ry + 1) * (ry + 1))));
			g.fill(cx - hw, cy + dy, cx + hw, cy + dy + 1, col);
		}
	}

	private static void drawPlatform(GuiGraphics g, Minecraft mc, Theme th, SkinEntry e) {
		int x = 4, y = PH0 - 27, w = PW0 - 8;
		float h = pHover;

		g.fill(x + 16, y - 2, x + w - 16, y, 0x55000000);
		g.fill(x + 24, y - 3, x + w - 24, y - 2, 0x33000000);

		int topC = Ui.mix(0xFF6E7590, th.accent(), h * 0.55f);
		g.fill(x, y, x + w, y + 5, topC);
		g.fill(x, y, x + w, y + 1, Ui.mix(topC, 0xFFFFFFFF, 0.25f));
		g.fill(x, y + 4, x + w, y + 5, Ui.mix(topC, 0xFF000000, 0.35f));

		int mortar = 0xFF14161F;
		g.fill(x, y + 5, x + w, y + 23, mortar);
		int base = Ui.mix(0xFF3A3F55, th.accent(), h * 0.25f);
		for (int row = 0; row < 3; row++) {
			int yy = y + 5 + row * 6;
			int off = row % 2 == 0 ? 0 : 6;
			for (int bx0 = -off, k = 0; bx0 < w; bx0 += 12, k++) {
				int x1 = Math.max(0, bx0), x2 = Math.min(w, bx0 + 12);
				float shade = (((k + row * 2) % 3) - 1) * 0.07f;
				int c = shade >= 0 ? Ui.mix(base, 0xFFFFFFFF, shade) : Ui.mix(base, 0xFF000000, -shade);
				g.fill(x + x1, yy, x + x2 - 1, yy + 5, c);
			}
		}
		// контактная тень модели и подсветка пьедестала под ней
		ellipse(g, x + w / 2, y + 1, 26, 3, 0x66000000);
		ellipse(g, x + w / 2, y + 1, 16, 2, 0x55000000);
		ellipse(g, x + w / 2, y, 34, 3, Ui.alpha(th.accent(), (int) (34 + 30 * h)));
		int edge = Ui.mix(0xFF0B0C12, th.accent(), h);
		g.fill(x, y + 5, x + 1, y + 23, edge);
		g.fill(x + w - 1, y + 5, x + w, y + 23, edge);
		g.fill(x, y + 22, x + w, y + 23, edge);
		if (h > 0.02f) Ui.outline(g, x - 1, y - 1, w + 2, 25, Ui.alpha(th.accent(), (int) (150 * h)));

		String label = e == null ? "" : (e.name == null ? "" : e.name);
		if (h > 0.5f) label = I18n.t("\u2630 тяните");
		while (mc.font.width(label) > w - 14 && label.length() > 1) label = label.substring(0, label.length() - 1);
		g.fill(x + 6, y + 8, x + w - 6, y + 18, 0xAA000000);
		g.drawCenteredString(mc.font, label, x + w / 2, y + 9, th.text());
	}

	/** Угловые «ручки» изменения размера в экранных координатах. */
	private static void drawHandles(GuiGraphics g, Theme th, int hoverCorner, boolean overPanel, float dispX, float dispY, float dispS, float tilt) {
		int w = Math.round(PW0 * dispS), h = Math.round(PH0 * dispS);
		int px = 0, py = 0;
		g.pose().pushMatrix(); // та же трансформация, что у панели, — рамка не отлипает при наклоне
		g.pose().translate(dispX + PW0 * dispS / 2f, dispY + PH0 * dispS);
		g.pose().rotate(tilt);
		g.pose().translate(-PW0 * dispS / 2f, -PH0 * dispS);
		for (int c = 0; c < 4; c++) {
			boolean active = c == hoverCorner || c == rCorner;
			int a = active ? 255 : (overPanel || rCorner >= 0 ? 140 : 55);
			int col = Ui.alpha(th.accent(), a);
			int x = (c % 2 == 0) ? px : px + w - HS;
			int y = c < 2 ? py : py + h - HS;
			int len = active ? HS : HS - 2;
			boolean left = c % 2 == 0, top = c < 2;
			int hx = left ? x : x + HS - len, vy = top ? y : y + HS - len;
			g.fill(hx, top ? y : y + HS - 2, hx + len, top ? y + 2 : y + HS, col); // горизонтальная черта
			g.fill(left ? x : x + HS - 2, vy, left ? x + 2 : x + HS, vy + len, col); // вертикальная черта
		}
		g.pose().popMatrix();
	}
}
