package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.Input;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Общие приёмы отрисовки: фон с частицами, панели, кнопки, заголовок. */
final class Ui {
	private Ui() {}

	static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
				| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	static int alpha(int rgb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	// ---------------------------------------------------------------- фон

	static void background(GuiGraphics g, int w, int h, long openNano, Theme th) {
		if (th.classic()) { // «Классика»: простой тёмный фон без свечения и частиц
			g.fillGradient(0, 0, w, h, th.bgTop(), th.bgBottom());
			return;
		}
		float t = (System.nanoTime() - openNano) / 1e9f;
		float fade = Math.min(1f, t / 0.35f);

		g.fillGradient(0, 0, w, h, th.bgTop(), th.bgBottom());

		int pulse = (int) ((44 + 24 * Math.sin(t * 1.4)) * fade);
		g.fillGradient(0, h - 110, w, h, th.glowA(), alpha(th.glowA(), pulse));
		int top = (int) ((26 + 14 * Math.sin(t * 1.1 + 1.5)) * fade);
		g.fillGradient(0, 0, w, 80, alpha(th.glowB(), top), th.glowB());

		switch (th.look().bg()) {
			case RAIN -> rain(g, w, h, t, fade, th);
			case STARS -> stars(g, w, h, t, fade, th);
			case SNOW -> snow(g, w, h, t, fade, th);
			case GRID -> grid(g, w, h, t, fade, th);
			case WAVES -> waves(g, w, h, t, fade, th);
			case EMBERS -> embers(g, w, h, t, fade, th);
			case PIXELS -> pixels(g, w, h, t, fade, th);
			case VOID -> voidBg(g, w, h, t, fade, th);
			case LAVA -> lava(g, w, h, t, fade, th);
			case NOTEBOOK -> notebook(g, w, h, t, fade, th);
			case SKY -> sky(g, w, h, t, fade, th);
			case TERRARIA, STARDEW, UNDERTALE, HOLLOW, CELESTE, HADES, CUPHEAD, DONTSTARVE, HOTLINE, DEADCELLS, CULT, BALATRO, SLAY, AMONGUS -> Indie.draw(th.look().bg(), g, w, h, t, fade, th);
			default -> particles(g, w, h, t, fade, th);
		}
		if (th.look().scan()) scanlines(g, w, h, t, th);
	}

	private static float rnd(float seed, float mul) {
		return frac((float) Math.sin(seed) * mul);
	}

	/** Обычные плавающие частицы (базовый фон). */
	private static void particles(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		for (int i = 0; i < 30; i++) {
			float seed = i * 12.9898f;
			float rx = frac((float) Math.sin(seed) * 43758.5453f);
			float ry = frac((float) Math.sin(seed * 1.7f + 3.1f) * 24634.6345f);
			float spd = 0.012f + 0.03f * frac(rx * 7.3f);
			float yy = h - ((t * spd + ry) % 1f) * h;
			float xx = rx * w + (float) Math.sin(t * 0.4f + i) * 10f;
			float life = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, 1f - yy / h)));
			int a = (int) (95 * life * fade);
			int s = i % 4 == 0 ? 2 : 1;
			if (a > 3) g.fill((int) xx, (int) yy, (int) xx + s, (int) yy + s, alpha(i % 2 == 0 ? th.glowA() : th.glowB(), a));
		}
	}

	/** Кибер: «цифровой дождь» — колонки падающих блоков со светящейся головой и затухающим хвостом. */
	private static void rain(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		int colW = 10, cols = w / colW + 1;
		for (int c = 0; c < cols; c++) {
			float r1 = rnd(c * 12.9898f, 43758.5453f);
			float spd = 0.16f + 0.34f * frac(r1 * 7.3f);
			int len = 7 + (int) (9 * frac(r1 * 3.1f));
			float period = h + len * 8f;
			float head = (t * spd * h * 0.8f + r1 * period) % period;
			int x = c * colW + 3;
			for (int k = 0; k < len; k++) {
				int yy = Math.round(head - k * 8);
				if (yy < -8 || yy > h) continue;
				boolean flick = ((int) (t * 9f) + c * 3 + k) % 6 == 0;
				if (k == 0) {
					g.fill(x, yy, x + 3, yy + 6, alpha(0xFFFFFF, (int) (230 * fade)));
					g.fill(x - 1, yy - 1, x + 4, yy + 7, alpha(th.accent(), (int) (50 * fade)));
				} else {
					int a = (int) (150 * (1f - k / (float) len) * fade * (flick ? 0.45f : 1f));
					if (a > 4) g.fill(x, yy, x + 3, yy + 6, alpha(th.accent(), a));
				}
			}
		}
	}

	/** Космос: звёзды трёх слоёв с мерцанием и параллаксом, редкие падающие звёзды. */
	private static void stars(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		for (int i = 0; i < 90; i++) {
			float seed = i * 9.137f + 1f;
			int layer = i % 3;
			float x = (rnd(seed, 43758.5453f) * w + t * (1.5f + layer * 3f)) % w;
			float y = rnd(seed * 1.7f + 3.1f, 24634.6345f) * h;
			float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.8f + layer * 0.7f) + i * 1.3f);
			int a = (int) ((50 + 170 * tw) * fade * (layer == 0 ? 0.6f : 1f));
			int col = i % 5 == 0 ? th.accent() : 0xFFFFFF;
			int px = (int) x, py = (int) y;
			if (layer == 2 && i % 6 == 2) { // крупная звезда-крестик
				g.fill(px - 2, py, px + 3, py + 1, alpha(col, a));
				g.fill(px, py - 2, px + 1, py + 3, alpha(col, a));
				g.fill(px, py, px + 1, py + 1, alpha(0xFFFFFF, Math.min(255, a + 60)));
			} else {
				g.fill(px, py, px + (layer == 2 ? 2 : 1), py + (layer == 2 ? 2 : 1), alpha(col, a));
			}
		}
		// падающая звезда раз в ~7 секунд
		float cyc = t % 7f;
		float u = cyc / 1.1f;
		if (u < 1f) {
			int n = (int) (t / 7f);
			float x0 = w * (0.15f + 0.55f * frac(n * 0.618f)), y0 = h * (0.08f + 0.25f * frac(n * 0.331f));
			float life = (float) Math.sin(Math.PI * u);
			for (int k = 0; k < 16; k++) {
				int px = (int) (x0 + u * w * 0.38f - k * 3.2f), py = (int) (y0 + u * h * 0.26f - k * 1.7f);
				g.fill(px, py, px + 2, py + 1, alpha(k == 0 ? 0xFFFFFF : th.accent(), (int) (230 * (1f - k / 16f) * life * fade)));
			}
		}
	}

	/** Зима: снегопад с покачиванием, сугробы внизу. */
	private static void snow(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		g.fillGradient(0, h - 40, w, h, alpha(0xFFFFFF, 0), alpha(0xBFE8FF, (int) (46 * fade)));
		for (int x = 0; x < w; x += 6) { // сугробы
			int hh = 3 + (int) (2.2 * Math.sin(x * 0.07) + 1.6 * Math.sin(x * 0.19 + 1.3));
			g.fill(x, h - hh, x + 6, h, alpha(0xFFFFFF, (int) (70 * fade)));
		}
		for (int i = 0; i < 80; i++) {
			float seed = i * 5.71f + 2f;
			float r1 = rnd(seed, 43758.5453f), r2 = rnd(seed * 1.3f, 24634.6345f), r3 = rnd(seed * 2.1f, 9631.123f);
			float x = r1 * w + (float) Math.sin(t * 0.7f + i) * 12f;
			float y = ((t * (10f + 24f * r2) + r3 * h) % (h + 8)) - 4;
			int size = r2 < 0.15f ? 3 : r2 < 0.55f ? 2 : 1;
			int a = (int) ((110 + 110 * r3) * fade);
			int px = (int) x, py = (int) y;
			if (size == 3) { // крупная снежинка-крестик
				g.fill(px - 1, py, px + 2, py + 1, alpha(0xFFFFFF, a));
				g.fill(px, py - 1, px + 1, py + 2, alpha(0xFFFFFF, a));
			} else {
				g.fill(px, py, px + size, py + size, alpha(0xFFFFFF, a));
			}
		}
	}

	/** Ретровейв: полосатое солнце на горизонте и бегущая перспективная сетка. */
	private static void grid(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		int hy = (int) (h * 0.6f);
		int cx = w / 2;
		int r = (int) (Math.min(w, h) * 0.24f);
		int cy = hy - (int) (r * 0.1f);
		// солнце: градиент жёлтый -> розовый, нижняя часть с растущими полосами
		for (int yy = cy - r; yy < hy; yy++) {
			float dy = yy - cy;
			int half = (int) Math.sqrt(Math.max(0, (double) r * r - dy * dy));
			if (half <= 0) continue;
			if (dy > r * 0.05f && ((yy - cy) % 7) < (dy / r) * 5.2f) continue; // полосы
			int col = mix(0xFFFFD84A, 0xFFFF2BA0, (yy - (cy - r)) / (2f * r));
			g.fill(cx - half, yy, cx + half, yy + 1, alpha(col, (int) (215 * fade)));
		}
		g.fillGradient(0, hy - 30, w, hy, alpha(th.glowA(), 0), alpha(th.glowA(), (int) (80 * fade)));
		g.fillGradient(0, hy, w, h, alpha(0x0A0214, (int) (235 * fade)), alpha(0x2A0A40, (int) (235 * fade)));
		g.fill(0, hy, w, hy + 1, alpha(th.accent(), (int) (200 * fade)));
		// горизонтальные линии бегут к зрителю
		float ph = (t * 0.35f) % 1f;
		for (int k = 0; k < 14; k++) {
			float u = (k + ph) / 14f;
			int y = hy + Math.round((h - hy) * u * u);
			g.fill(0, y, w, y + 1, alpha(th.accent(), (int) ((40 + 130 * u) * fade)));
		}
		// вертикальные линии сходятся в точке горизонта
		int steps = Math.max(1, (h - hy) / 4);
		for (int i = -9; i <= 9; i++) {
			for (int s = 0; s < steps; s++) {
				float v = s / (float) steps;
				int x = cx + Math.round(i * (w / 7f) * v);
				g.fill(x, hy + Math.round((h - hy) * v), x + 1, hy + Math.round((h - hy) * v) + 4,
						alpha(th.glowA() | 0, (int) ((30 + 110 * v) * fade)));
			}
		}
	}

	/** Океан: три слоя бегущих волн, пузырьки и мерцающие лучи света. */
	private static void waves(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		for (int i = 0; i < 5; i++) { // лучи света сверху
			float x = (i * 0.23f + 0.08f) * w + (float) Math.sin(t * 0.25f + i * 2f) * 24f;
			int a = (int) ((16 + 10 * Math.sin(t * 0.8f + i)) * fade);
			g.fillGradient((int) x, 0, (int) x + 16 + i * 4, (int) (h * 0.7f), alpha(th.glowA(), a), alpha(th.glowA(), 0));
		}
		for (int i = 0; i < 16; i++) { // пузырьки
			float seed = i * 6.23f + 1f;
			float r1 = rnd(seed, 43758.5453f), r2 = rnd(seed * 1.9f, 24634.6345f);
			float y = h - ((t * (0.03f + 0.05f * r2) + r1) % 1f) * h;
			float x = r1 * w + (float) Math.sin(t * 1.2f + i) * 6f;
			int a = (int) (120 * fade * (y / h));
			int s = r2 < 0.3f ? 3 : 2;
			Ui.outline(g, (int) x, (int) y, s + 1, s + 1, alpha(0xFFFFFF, a));
		}
		for (int l = 0; l < 3; l++) { // волны
			float amp = 4f + l * 2f, k1 = 0.028f + l * 0.008f, sp = 0.8f + l * 0.3f;
			float y0 = h * (0.74f + l * 0.07f);
			int col = l == 1 ? th.glowB() : th.glowA();
			for (int x = 0; x < w; x += 3) {
				float y = y0 + amp * (float) Math.sin(x * k1 + t * sp) + amp * 0.5f * (float) Math.sin(x * k1 * 2.3f - t * sp * 1.4f);
				int iy = (int) y;
				g.fill(x, iy, x + 3, h, alpha(col, (int) ((26 + l * 18) * fade)));
				g.fill(x, iy, x + 3, iy + 1, alpha(0xFFFFFF, (int) ((46 - l * 8) * fade)));
			}
		}
	}

	/** Таверна: искры от очага — тёплые, мерцающие, с покачиванием; по краям тёмная виньетка. */
	private static void embers(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		g.fillGradient(0, 0, w, 60, alpha(0x000000, (int) (90 * fade)), alpha(0x000000, 0));
		g.fillGradient(0, h - 60, w, h, alpha(0x000000, 0), alpha(0x000000, (int) (110 * fade)));
		for (int i = 0; i < 46; i++) {
			float seed = i * 8.71f + 0.5f;
			float r1 = rnd(seed, 43758.5453f), r2 = rnd(seed * 1.7f, 24634.6345f);
			float spd = 0.03f + 0.07f * r2;
			float prog = (t * spd + r1) % 1f;
			float y = h - prog * (h + 10);
			float x = r1 * w + (float) Math.sin(t * (0.8f + r2) + i) * (8f + 14f * prog);
			float flick = 0.6f + 0.4f * (float) Math.sin(t * 9f + i * 3.7f);
			int a = (int) (200 * Math.sin(Math.PI * prog) * flick * fade);
			if (a < 4) continue;
			int col = prog < 0.5f ? th.glowB() : th.glowA();
			int s = i % 5 == 0 ? 3 : i % 2 == 0 ? 2 : 1;
			g.fill((int) x, (int) y, (int) x + s, (int) y + s, alpha(col, a));
			if (s == 3) g.fill((int) x - 1, (int) y - 1, (int) x + 4, (int) y + 4, alpha(col, a / 5));
		}
	}

	/** Аркада: падающие пиксельные блоки с шагом в 2 px и бледная пиксельная сетка. */
	private static void pixels(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		int gc = alpha(0xFFFFFF, (int) (9 * fade));
		for (int x = 0; x < w; x += 16) g.fill(x, 0, x + 1, h, gc);
		for (int y = 0; y < h; y += 16) g.fill(0, y, w, y + 1, gc);
		int[] pal = {th.accent(), th.glowA() | 0xFF000000, th.glowB() | 0xFF000000, 0xFF4AFF8A};
		for (int i = 0; i < 28; i++) {
			float seed = i * 7.77f + 3f;
			float r1 = rnd(seed, 43758.5453f), r2 = rnd(seed * 1.3f, 24634.6345f), r3 = rnd(seed * 2.9f, 9631.123f);
			int s = r2 < 0.4f ? 4 : r2 < 0.8f ? 6 : 8;
			int x = ((int) (r1 * w) / 8) * 8;
			float y = ((t * (9f + 22f * r3) + r2 * h) % (h + 12)) - 10;
			int py = ((int) y / 2) * 2;
			int a = (int) ((((int) (t * 3f) + i) % 3 == 0 ? 90 : 160) * fade);
			int col = pal[i % pal.length];
			g.fill(x, py, x + s, py + s, alpha(col, a));
			g.fill(x, py, x + s, py + 1, alpha(0xFFFFFF, a / 2));
			g.fill(x, py + s - 1, x + s, py + s, alpha(0x000000, a / 2));
		}
	}

	/** Тьма (нефтяная плёнка): почти чёрная поверхность, по которой медленно ползут радужные разводы; виньетка. */
	private static void voidBg(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		final int C = 8;
		g.fill(0, 0, w, h, 0xFF040308);
		for (int y = 0; y < h; y += C) {
			for (int x = 0; x < w; x += C) {
				float u = x * 0.012f, v = y * 0.016f;
				float wx = u + 0.9f * (float) Math.sin(v * 1.7f + t * 0.11f), wy = v + 0.9f * (float) Math.sin(u * 1.3f - t * 0.09f);
				float n = (float) (Math.sin(wx * 2.1f + t * 0.05f) + Math.sin(wy * 2.7f - t * 0.07f) + Math.sin((wx + wy) * 1.6f)) / 3f;
				float band = Math.max(0f, n - 0.12f);
				if (band <= 0.01f) continue;
				float hue = 0.52f + n * 0.55f + wx * 0.06f;
				int col = OilField.hsv(hue, 0.85f, Math.min(0.11f, 0.015f + band * 0.12f) * fade);
				g.fill(x, y, x + C, y + C, 0xFF000000 | col);
			}
		}
		int vig = (int) (150 * fade);
		g.fillGradient(0, 0, w, h / 5, alpha(0x000000, vig), 0);
		g.fillGradient(0, h - h / 4, w, h, 0, alpha(0x000000, vig));
	}

	/** Лава: лавовая река внизу с коркой и пузырями, жар от неё и летящие искры. */
	private static void lava(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		g.fillGradient(0, (int) (h * 0.55f), w, h, alpha(0xFF3A00, 0), alpha(0xFF3A00, (int) (60 * fade)));
		float base = h * 0.84f;
		for (int x = 0; x < w; x += 3) {
			float y = base + 3f * (float) Math.sin(x * 0.045f + t * 0.7f) + 1.6f * (float) Math.sin(x * 0.13f - t * 1.3f);
			int iy = (int) y;
			g.fillGradient(x, iy, x + 3, h, alpha(0xFFC030, (int) (235 * fade)), alpha(0xE03A08, (int) (240 * fade)));
			float crust = (float) (Math.sin(x * 0.21f + t * 0.25f) * Math.sin(x * 0.067f - t * 0.1f));
			if (crust > 0.35f) { // тёмная корка на поверхности
				int ch = 2 + (int) ((crust - 0.35f) * 7f);
				g.fill(x, iy, x + 3, iy + ch, alpha(0x3A0E04, (int) (230 * fade)));
				g.fill(x, iy + ch, x + 3, iy + ch + 1, alpha(0xFF8A20, (int) (200 * fade)));
			}
		}
		for (int i = 0; i < 9; i++) { // пузыри лопаются на поверхности
			float r1 = rnd(i * 5.17f + 1f, 43758.5453f), r2 = rnd(i * 8.3f + 2f, 24634.6345f);
			float period = 2.2f + 2.6f * r2;
			float u = ((t + period * r1) % period) / period;
			int bx = (int) (r1 * w), by = (int) (base + 6 + 10 * r2);
			int rad = (int) (1 + 5 * u);
			int a = (int) (200 * (1f - u) * fade);
			if (a > 6) outline(g, bx - rad, by - rad / 2, rad * 2, rad, alpha(0xFFE070, a));
		}
		embers(g, w, h, t, fade, th);
	}

	/** Тетрадь: клетчатый лист — синие линии, красное поле, дырки от дырокола и каракули, плавающие в воздухе. */
	private static void notebook(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		for (int y = 40; y < h; y += 14) g.fill(0, y, w, y + 1, alpha(0x5C8AD0, (int) (120 * fade)));
		g.fill(44, 0, 45, h, alpha(0xE0443A, (int) (150 * fade)));
		g.fill(47, 0, 48, h, alpha(0xE0443A, (int) (100 * fade)));
		for (int k = 0; k < 3; k++) { // дырки
			int cy = (int) (h * (0.2f + 0.3f * k)), cx = 16;
			for (int dy = -5; dy <= 5; dy++) {
				int half = (int) Math.round(Math.sqrt(Math.max(0, 30 - dy * dy)));
				g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, alpha(0x7A7355, (int) (220 * fade)));
			}
			g.fill(cx - 3, cy - 3, cx + 4, cy + 4, alpha(0x2A2618, (int) (170 * fade)));
		}
		for (int i = 0; i < 10; i++) { // каракули: звёздочки, квадраты, сердечки, треугольники
			float r1 = rnd(i * 7.13f + 1f, 43758.5453f), r2 = rnd(i * 3.7f + 4f, 24634.6345f);
			int x = (int) (70 + (w - 90) * r1 + 4 * Math.sin(t * 0.6f + i));
			int y = (int) (30 + (h - 50) * r2 + 5 * Math.sin(t * 0.8f + i * 1.7f));
			int a = (int) (125 * fade);
			int col = i % 3 == 0 ? 0x2D5BFF : i % 3 == 1 ? 0x3A3A44 : 0xE0443A;
			switch (i % 4) {
				case 0 -> { // звёздочка
					g.fill(x - 3, y, x + 4, y + 1, alpha(col, a));
					g.fill(x, y - 3, x + 1, y + 4, alpha(col, a));
					g.fill(x - 2, y - 2, x - 1, y - 1, alpha(col, a));
					g.fill(x + 2, y + 2, x + 3, y + 3, alpha(col, a));
					g.fill(x + 2, y - 2, x + 3, y - 1, alpha(col, a));
					g.fill(x - 2, y + 2, x - 1, y + 3, alpha(col, a));
				}
				case 1 -> outline(g, x - 3, y - 3, 7, 7, alpha(col, a));
				case 2 -> { // сердечко
					g.fill(x - 3, y - 2, x - 1, y, alpha(col, a));
					g.fill(x + 2, y - 2, x + 4, y, alpha(col, a));
					g.fill(x - 3, y, x + 4, y + 1, alpha(col, a));
					g.fill(x - 2, y + 1, x + 3, y + 2, alpha(col, a));
					g.fill(x - 1, y + 2, x + 2, y + 3, alpha(col, a));
					g.fill(x, y + 3, x + 1, y + 4, alpha(col, a));
				}
				default -> { // треугольник
					g.fill(x, y - 3, x + 1, y - 2, alpha(col, a));
					g.fill(x - 1, y - 2, x + 2, y - 1, alpha(col, a));
					g.fill(x - 2, y - 1, x + 3, y, alpha(col, a));
					g.fill(x - 3, y, x + 4, y + 1, alpha(col, a));
				}
			}
		}
	}

	private static void cloud(GuiGraphics g, int cx, int cy, float sc, int a) {
		int[][] c = {{-22, 4, 11}, {-8, -4, 15}, {10, -2, 14}, {24, 4, 10}, {0, 6, 13}};
		int top = Integer.MAX_VALUE, bot = Integer.MIN_VALUE;
		for (int[] k : c) {
			top = Math.min(top, (int) (cy + k[1] * sc - k[2] * sc));
			bot = Math.max(bot, (int) (cy + k[1] * sc + k[2] * sc));
		}
		for (int y = top; y <= bot; y++) {
			int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
			for (int[] k : c) {
				float dy = y - (cy + k[1] * sc), r = k[2] * sc;
				if (Math.abs(dy) > r) continue;
				int half = (int) Math.sqrt(r * r - dy * dy);
				mn = Math.min(mn, (int) (cx + k[0] * sc) - half);
				mx = Math.max(mx, (int) (cx + k[0] * sc) + half);
			}
			if (mn >= mx) continue;
			float v = (y - top) / (float) Math.max(1, bot - top);
			g.fill(mn, y, mx, y + 1, alpha(mix(0xFFFFFF, 0xBFD6EE, v * v), a));
		}
	}

	/** Облака: утреннее солнце с лучами, плывущие облака двух слоёв и птицы. */
	private static void sky(GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		int sx = (int) (w * 0.82f), sy = (int) (h * 0.2f), r = 20;
		for (int k = 4; k >= 1; k--) { // гало
			int rr = r + k * 9;
			for (int dy = -rr; dy <= rr; dy += 2) {
				int half = (int) Math.sqrt(rr * rr - dy * dy);
				g.fill(sx - half, sy + dy, sx + half, sy + dy + 2, alpha(0xFFE9A0, (int) (14 * fade)));
			}
		}
		for (int i = 0; i < 14; i++) { // лучи медленно вращаются
			double ang = t * 0.08 + i * Math.PI * 2 / 14;
			for (int k = 0; k < 16; k++) {
				int px = sx + (int) (Math.cos(ang) * (r + 6 + k)), py = sy + (int) (Math.sin(ang) * (r + 6 + k));
				g.fill(px, py, px + 2, py + 2, alpha(0xFFE070, (int) ((150 - k * 8) * fade)));
			}
		}
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.sqrt(r * r - dy * dy);
			g.fill(sx - half, sy + dy, sx + half, sy + dy + 1, alpha(mix(0xFFF3A0, 0xFFB03A, (dy + r) / (2f * r)), (int) (240 * fade)));
		}
		for (int i = 0; i < 7; i++) { // облака разной скорости и размера
			float r1 = rnd(i * 6.3f + 1f, 43758.5453f), r2 = rnd(i * 2.9f + 3f, 24634.6345f);
			float sc = 0.7f + 0.9f * r2;
			float x = ((r1 * (w + 160) + t * (4f + 8f * r2)) % (w + 160)) - 80;
			int cy = (int) (h * (0.14f + 0.62f * r1 * r2 + 0.1f * i / 7f));
			cloud(g, (int) x, cy, sc, (int) ((120 + 90 * r2) * fade));
		}
		for (int i = 0; i < 3; i++) { // птицы
			float cyc = 11f + i * 3f;
			float u = ((t + i * 4.3f) % cyc) / cyc;
			float bx = -20 + u * (w + 40), by = h * (0.25f + 0.15f * i) + 6f * (float) Math.sin(u * 9f + i);
			int flap = (int) Math.round(2.2 * Math.sin(t * 7f + i * 2f));
			int col = alpha(0x35506E, (int) (210 * fade));
			g.fill((int) bx - 4, (int) by - flap, (int) bx - 1, (int) by - flap + 1, col);
			g.fill((int) bx - 1, (int) by, (int) bx + 1, (int) by + 1, col);
			g.fill((int) bx + 1, (int) by - flap, (int) bx + 4, (int) by - flap + 1, col);
		}
	}

	/** Линии развёртки (ЭЛТ): тонкие тёмные строки и медленная светлая полоса. */
	private static void scanlines(GuiGraphics g, int w, int h, float t, Theme th) {
		for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x16000000);
		int by = (int) ((t * 38f) % (h + 40)) - 30;
		g.fillGradient(0, by, w, by + 26, alpha(th.accent(), 0), alpha(th.accent(), 14));
	}

	private static void titleText(GuiGraphics g, Font font, String text, float x, float y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(1.5f, 1.5f);
		g.drawCenteredString(font, text, 0, 0, color);
		g.pose().popMatrix();
	}

	private static void diamond(GuiGraphics g, int cx, int cy, int r, int col) {
		for (int i = -r; i <= r; i++) {
			int half = r - Math.abs(i);
			g.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, col);
		}
	}

	static void title(GuiGraphics g, Font font, String text, int cx, int y, Theme th, long openNano) {
		if (th.classic()) {
			g.drawCenteredString(font, text, cx, y + 4, th.text());
			return;
		}
		float t = (System.nanoTime() - openNano) / 1e9f;
		float k = Math.min(1f, t / 0.5f);
		k = 1f - (1f - k) * (1f - k);
		Theme.Look lk = th.look();
		if (th.group() == 3 && text.equals(I18n.t("Библиотека скинов")) && Indie.logo(g, font, th, cx, y, t)) return; // вместо заголовка — логотип игры

		// тень/глитч под основным текстом — свой характер у каждой темы
		if (lk.deco() == Theme.Deco.BRACKETS) {
			float ph = (t * 0.6f) % 1f;
			if (ph < 0.08f) { // редкий глитч
				int o = 1 + (int) (ph * 30) % 3;
				titleText(g, font, text, cx - o, y, alpha(0xFF2BD6, 170));
				titleText(g, font, text, cx + o, y, alpha(0x00FFFF, 170));
			}
		} else if (lk.deco() == Theme.Deco.SYNTH) {
			titleText(g, font, text, cx + 2, y + 2, alpha(th.glowA(), 200));
		} else if (lk.deco() == Theme.Deco.STUDS) {
			titleText(g, font, text, cx + 1, y + 1, 0xFF1A0C04);
		} else if (lk.shape() == Theme.Shape.THICK) {
			titleText(g, font, text, cx + 2, y + 2, 0xFF000000);
		} else if (lk.shape() == Theme.Shape.TERRA) {
			titleText(g, font, text, cx + 1, y + 1, 0xFF000000);
			titleText(g, font, text, cx - 1, y - 1, 0xFF0E1650);
		} else if (lk.shape() == Theme.Shape.WOOD) {
			titleText(g, font, text, cx + 1, y + 1, alpha(0xFFFFFF, 170));
		} else if (lk.shape() == Theme.Shape.GILT) {
			titleText(g, font, text, cx + 1, y + 1, alpha(0xD84A1A, 200));
		} else if (lk.deco() == Theme.Deco.DRIPS) { // тёмная аура вокруг текста
			for (int i = 0; i < 4; i++) {
				int ox = i < 2 ? (i == 0 ? -1 : 1) : 0, oy = i < 2 ? 0 : (i == 2 ? -1 : 1);
				titleText(g, font, text, cx + ox * 2, y + oy * 2, alpha(th.accent(), 70));
			}
		} else if (lk.deco() == Theme.Deco.CRACKS) { // жар
			titleText(g, font, text, cx + 1, y + 2, alpha(0xFF6A1A, 150));
		} else if (lk.deco() == Theme.Deco.TAPE) { // чернильная тень
			titleText(g, font, text, cx + 1, y + 1, alpha(0x000000, 40));
		}
		titleText(g, font, text, cx, y, th.text());

		int half = (int) ((font.width(text) * 1.5f / 2f + 14) * k);
		switch (lk.deco()) {
			case WAVE -> { // волнистая линия
				for (int dx = -half; dx < half; dx += 2) {
					int yy = y + 15 + (int) Math.round(1.6 * Math.sin(dx * 0.16 + t * 3.0));
					float fall = 1f - Math.abs(dx) / (float) Math.max(1, half);
					g.fill(cx + dx, yy, cx + dx + 2, yy + 2, alpha(th.accent(), (int) (230 * fall)));
				}
			}
			case STUDS -> { // золотая линия с ромбами на концах
				g.fillGradient(cx - half, y + 14, cx, y + 16, alpha(th.accent(), 0), th.accent());
				g.fillGradient(cx, y + 14, cx + half, y + 16, th.accent(), alpha(th.accent(), 0));
				diamond(g, cx - half - 4, y + 15, 3, th.accent());
				diamond(g, cx + half + 4, y + 15, 3, th.accent());
				diamond(g, cx, y + 15, 2, 0xFFFFF0B0);
			}
			case FROST -> { // ледяная линия со снежинками на концах
				g.fillGradient(cx - half, y + 14, cx, y + 16, alpha(th.accent(), 0), th.accent());
				g.fillGradient(cx, y + 14, cx + half, y + 16, th.accent(), alpha(th.accent(), 0));
				for (int sgn = -1; sgn <= 1; sgn += 2) {
					int fx = cx + sgn * (half + 5);
					g.fill(fx - 3, y + 15, fx + 4, y + 16, 0xFFFFFFFF);
					g.fill(fx, y + 12, fx + 1, y + 19, 0xFFFFFFFF);
					g.fill(fx - 2, y + 13, fx - 1, y + 14, 0xCCFFFFFF);
					g.fill(fx + 2, y + 17, fx + 3, y + 18, 0xCCFFFFFF);
				}
			}
			case ORNAMENT -> {
				g.fill(cx - half, y + 15, cx - 6, y + 16, alpha(0xE6EEF8, 200));
				g.fill(cx + 6, y + 15, cx + half, y + 16, alpha(0xE6EEF8, 200));
				diamond(g, cx, y + 15, 3, 0xFFE6EEF8);
			}
			case KEY -> Indie.meander(g, cx - half, y + 13, half * 2, alpha(0xD8A83A, 190));
			case TAPE -> { // подчёркивание красной ручкой, дважды
				for (int dx = -half; dx < half; dx += 2) {
					g.fill(cx + dx, y + 15 + (int) Math.round(1.2 * Math.sin(dx * 0.45)), cx + dx + 2, y + 16 + (int) Math.round(1.2 * Math.sin(dx * 0.45)), alpha(th.accent(), 220));
					if (Math.abs(dx) < half * 0.8f)
						g.fill(cx + dx, y + 19 + (int) Math.round(1.2 * Math.sin(dx * 0.4 + 1.3)), cx + dx + 2, y + 20 + (int) Math.round(1.2 * Math.sin(dx * 0.4 + 1.3)), alpha(th.accent(), 160));
				}
			}
			case DRIPS -> { // линия с каплями
				g.fillGradient(cx - half, y + 14, cx, y + 16, alpha(th.accent(), 0), th.accent());
				g.fillGradient(cx, y + 14, cx + half, y + 16, th.accent(), alpha(th.accent(), 0));
				float tt = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f;
				for (int i = 0; i < 3; i++) panelDrip(g, cx + (i - 1) * (half / 2), y + 16, tt, 77 + i, i, alpha(th.accent(), 230), 5f);
			}
			case CRACKS -> {
				g.fillGradient(cx - half, y + 14, cx, y + 16, alpha(0xFF6A1A, 0), 0xFFFFB030);
				g.fillGradient(cx, y + 14, cx + half, y + 16, 0xFFFFB030, alpha(0xFF6A1A, 0));
			}
			case SYNTH -> { // двойная неоновая линия
				g.fillGradient(cx - half, y + 14, cx, y + 15, alpha(th.glowA(), 0), th.glowA() | 0xFF000000);
				g.fillGradient(cx, y + 14, cx + half, y + 15, th.glowA() | 0xFF000000, alpha(th.glowA(), 0));
				g.fillGradient(cx - half, y + 17, cx, y + 18, alpha(th.accent(), 0), th.accent());
				g.fillGradient(cx, y + 17, cx + half, y + 18, th.accent(), alpha(th.accent(), 0));
			}
			case BRACKETS -> { // линия со скобками
				g.fillGradient(cx - half, y + 14, cx, y + 15, alpha(th.accent(), 0), th.accent());
				g.fillGradient(cx, y + 14, cx + half, y + 15, th.accent(), alpha(th.accent(), 0));
				for (int sgn = -1; sgn <= 1; sgn += 2) {
					int bx = cx + sgn * (half + 2);
					g.fill(bx, y + 9, bx + 1, y + 21, th.accent());
					g.fill(sgn < 0 ? bx : bx - 3, y + 9, sgn < 0 ? bx + 4 : bx + 1, y + 10, th.accent());
					g.fill(sgn < 0 ? bx : bx - 3, y + 20, sgn < 0 ? bx + 4 : bx + 1, y + 21, th.accent());
				}
			}
			default -> {
				if (lk.shape() == Theme.Shape.THICK) { // пиксельная полоса
					for (int dx = -half; dx < half; dx += 6)
						g.fill(cx + dx, y + 14, cx + dx + 4, y + 17, alpha(th.accent(), 230));
				} else {
					g.fillGradient(cx - half, y + 14, cx, y + 16, alpha(th.accent(), 0), th.accent());
					g.fillGradient(cx, y + 14, cx + half, y + 16, th.accent(), alpha(th.accent(), 0));
				}
			}
		}
	}

	// ---------------------------------------------------------------- панели

	static void rect(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
		g.fillGradient(x, y, x + w, y + h, top, bottom);
	}

	static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
		g.fill(x, y, x + w, y + 1, c);
		g.fill(x, y + h - 1, x + w, y + h, c);
		g.fill(x, y, x + 1, y + h, c);
		g.fill(x + w - 1, y, x + w, y + h, c);
	}

	private static Theme.Shape shapeOf(Theme th) {
		return th.classic() ? Theme.Shape.SQUARE : th.look().shape();
	}

	/** Панель со скруглёнными (на 2 px) углами и вертикальным градиентом; форму задаёт текущая тема. */
	static void roundPanel(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
		shapePanel(Theme.current(), g, x, y, w, h, top, bottom);
	}

	static void roundOutline(GuiGraphics g, int x, int y, int w, int h, int c) {
		shapeOutline(Theme.current(), g, x, y, w, h, c);
	}

	static void shapePanel(Theme th, GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
		if (th.classic()) {
			g.fill(x, y, x + w, y + h, top);
			return;
		}
		switch (shapeOf(th)) {
			case SQUARE, THICK, ORNATE, SKETCH, WOOD, BOXED, GILT -> g.fillGradient(x, y, x + w, y + h, top, bottom);
			case CHAMFER -> {
				int c = Math.max(2, Math.min(6, Math.min(w, h) / 3));
				g.fillGradient(x + c, y, x + w - c, y + h, top, bottom);
				for (int i = 0; i < c; i++) { // срезанные углы лесенкой
					int inset = c - i;
					int ya = y + inset, yb = y + h - inset;
					if (yb <= ya) continue;
					int lt = mix(top, bottom, inset / (float) h), lb = mix(top, bottom, (h - inset) / (float) h);
					g.fillGradient(x + i, ya, x + i + 1, yb, lt, lb);
					g.fillGradient(x + w - 1 - i, ya, x + w - i, yb, lt, lb);
				}
			}
			default -> {
				g.fillGradient(x + 2, y, x + w - 2, y + h, top, bottom);
				g.fillGradient(x + 1, y + 1, x + 2, y + h - 1, mix(top, bottom, 1f / h), mix(top, bottom, (h - 1f) / h));
				g.fillGradient(x + w - 2, y + 1, x + w - 1, y + h - 1, mix(top, bottom, 1f / h), mix(top, bottom, (h - 1f) / h));
				g.fillGradient(x, y + 2, x + 1, y + h - 2, mix(top, bottom, 2f / h), mix(top, bottom, (h - 2f) / h));
				g.fillGradient(x + w - 1, y + 2, x + w, y + h - 2, mix(top, bottom, 2f / h), mix(top, bottom, (h - 2f) / h));
			}
		}
	}

	static void shapeOutline(Theme th, GuiGraphics g, int x, int y, int w, int h, int c) {
		if (th.classic()) {
			outline(g, x, y, w, h, c);
			return;
		}
		switch (shapeOf(th)) {
			case SQUARE -> outline(g, x, y, w, h, c);
			case TERRA -> { // голубая панель Terraria: тёмная кайма и светлая внутренняя линия
				g.fill(x + 2, y, x + w - 2, y + 1, c);
				g.fill(x + 2, y + h - 1, x + w - 2, y + h, c);
				g.fill(x, y + 2, x + 1, y + h - 2, c);
				g.fill(x + w - 1, y + 2, x + w, y + h - 2, c);
				g.fill(x + 1, y + 1, x + 2, y + 2, c);
				g.fill(x + w - 2, y + 1, x + w - 1, y + 2, c);
				g.fill(x + 1, y + h - 2, x + 2, y + h - 1, c);
				g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, c);
				outline(g, x + 2, y + 2, w - 4, h - 4, alpha(0xFFFFFF, 30));
			}
			case WOOD -> { // деревянная рамка Stardew: тёмный кант, рыжая и светлая полосы
				int f = Math.min(w, h) < 34 ? 1 : 2;
				int mid = 0xFFC4742A, lit = 0xFFEFA855;
				for (int i = 0; i < f; i++) outline(g, x + i, y + i, w - 2 * i, h - 2 * i, c);
				for (int i = f; i < 2 * f; i++) outline(g, x + i, y + i, w - 2 * i, h - 2 * i, mid);
				outline(g, x + 2 * f, y + 2 * f, w - 4 * f, h - 4 * f, lit);
				if (w - 6 * f > 6 && h - 6 * f > 6) { // скос: светлее сверху-слева, темнее снизу-справа
					g.fill(x + 2 * f + 1, y + 2 * f + 1, x + w - 2 * f - 1, y + 2 * f + 2, 0x55FFFFFF);
					g.fill(x + 2 * f + 1, y + h - 2 * f - 2, x + w - 2 * f - 1, y + h - 2 * f - 1, 0x33000000);
				}
				g.fill(x, y, x + 1, y + 1, 0x00000000);
			}
			case BOXED -> { // Undertale: чёрный прямоугольник с белой рамкой в 3 (2) пикселя
				int f = Math.min(w, h) < 34 ? 2 : 3;
				for (int i = 0; i < f; i++) outline(g, x + i, y + i, w - 2 * i, h - 2 * i, c);
			}
			case GILT -> { // Hades: золотая рамка, тёмная вставка, уголки
				outline(g, x, y, w, h, c);
				outline(g, x + 2, y + 2, w - 4, h - 4, mix(c, 0xFF000000, 0.55f));
				g.fill(x, y, x + 4, y + 4, c);
				g.fill(x + w - 4, y, x + w, y + 4, c);
				g.fill(x, y + h - 4, x + 4, y + h, c);
				g.fill(x + w - 4, y + h - 4, x + w, y + h, c);
				g.fill(x + 1, y + 1, x + 3, y + 3, mix(c, 0xFF000000, 0.6f));
				g.fill(x + w - 3, y + 1, x + w - 1, y + 3, mix(c, 0xFF000000, 0.6f));
				g.fill(x + 1, y + h - 3, x + 3, y + h - 1, mix(c, 0xFF000000, 0.6f));
				g.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, mix(c, 0xFF000000, 0.6f));
			}
			case TOON -> { // Cuphead: толстый чёрный контур мультфильма
				for (int i = 0; i < 3; i++) {
					g.fill(x + 2 + i, y + i, x + w - 2 - i, y + i + 1, c);
					g.fill(x + 2 + i, y + h - 1 - i, x + w - 2 - i, y + h - i, c);
					g.fill(x + i, y + 2 + i, x + i + 1, y + h - 2 - i, c);
					g.fill(x + w - 1 - i, y + 2 + i, x + w - i, y + h - 2 - i, c);
				}
				g.fill(x + 1, y + 1, x + 3, y + 3, c);
				g.fill(x + w - 3, y + 1, x + w - 1, y + 3, c);
				g.fill(x + 1, y + h - 3, x + 3, y + h - 1, c);
				g.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, c);
			}
			case THICK -> {
				outline(g, x, y, w, h, c);
				outline(g, x + 1, y + 1, w - 2, h - 2, c);
			}
			case ORNATE -> {
				outline(g, x, y, w, h, c);
				outline(g, x + 3, y + 3, w - 6, h - 6, alpha(c, 90)); // внутренняя тонкая рамка
			}
			case SKETCH -> { // карандашная рамка: линия чуть «гуляет», поверх — вторая, бледнее
				sketchLine(g, x, y, x + w, y, c, 0);
				sketchLine(g, x, y + h - 1, x + w, y + h - 1, c, 1);
				sketchLine(g, x, y, x, y + h, c, 2);
				sketchLine(g, x + w - 1, y, x + w - 1, y + h, c, 3);
				if (w >= 160) { // вторая бледная линия — только у больших панелей (дёшево для карточек)
					int c2 = alpha(c, 70);
					sketchLine(g, x - 1, y + 1, x + w + 1, y + 1, c2, 5);
					sketchLine(g, x + 1, y + h - 2, x + w - 1, y + h - 2, c2, 6);
				}
			}
			case CHAMFER -> {
				int k = Math.max(2, Math.min(6, Math.min(w, h) / 3));
				g.fill(x + k, y, x + w - k, y + 1, c);
				g.fill(x + k, y + h - 1, x + w - k, y + h, c);
				g.fill(x, y + k, x + 1, y + h - k, c);
				g.fill(x + w - 1, y + k, x + w, y + h - k, c);
				for (int i = 0; i < k; i++) { // косые срезы
					g.fill(x + i, y + k - i, x + i + 1, y + k - i + 1, c);
					g.fill(x + w - 1 - i, y + k - i, x + w - i, y + k - i + 1, c);
					g.fill(x + i, y + h - k + i - 1, x + i + 1, y + h - k + i, c);
					g.fill(x + w - 1 - i, y + h - k + i - 1, x + w - i, y + h - k + i, c);
				}
			}
			default -> {
				g.fill(x + 2, y, x + w - 2, y + 1, c);
				g.fill(x + 2, y + h - 1, x + w - 2, y + h, c);
				g.fill(x, y + 2, x + 1, y + h - 2, c);
				g.fill(x + w - 1, y + 2, x + w, y + h - 2, c);
				g.fill(x + 1, y + 1, x + 2, y + 2, c);
				g.fill(x + w - 2, y + 1, x + w - 1, y + 2, c);
				g.fill(x + 1, y + h - 2, x + 2, y + h - 1, c);
				g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, c);
			}
		}
	}

	private static int jitter(int a, int b, int salt) {
		int hsh = a * 73856093 ^ b * 19349663 ^ salt * 83492791;
		return ((hsh >>> 9) % 3) - 1;
	}

	/** Линия «от руки»: отрезками по 20 px, каждый чуть смещён поперёк. */
	private static void sketchLine(GuiGraphics g, int x0, int y0, int x1, int y1, int c, int salt) {
		if (y0 == y1) {
			for (int sx = x0; sx < x1; sx += 20) g.fill(sx, y0 + jitter(sx, y0, salt), Math.min(sx + 20, x1), y0 + jitter(sx, y0, salt) + 1, c);
		} else {
			for (int sy = y0; sy < y1; sy += 20) g.fill(x0 + jitter(x0, sy, salt), sy, x0 + jitter(x0, sy, salt) + 1, Math.min(sy + 20, y1), c);
		}
	}

	private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
		int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
		for (int n = 0; n < 64; n++) {
			g.fill(x0, y0, x0 + 1, y0 + 1, c);
			if (x0 == x1 && y0 == y1) break;
			int e2 = 2 * err;
			if (e2 > -dy) { err -= dy; x0 += sx; }
			if (e2 < dx) { err += dx; y0 += sy; }
		}
	}

	/** Капля, висящая на нижнем крае (px, top): шейка и круглая головка. */
	private static void dripHang(GuiGraphics g, int px, int top, float len, float rad, int col) {
		int L = Math.max(0, (int) len), R = Math.max(1, (int) Math.round(rad));
		for (int k = 0; k < L; k++) g.fill(px - 1, top + k, px + 2, top + k + 1, col);
		for (int dy = -R; dy <= R; dy++) {
			int half = (int) Math.round(Math.sqrt(Math.max(0, R * R - dy * dy)));
			g.fill(px - half, top + L + dy, px + half + 1, top + L + dy + 1, col);
		}
	}

	/** Падающая капля. */
	private static void dripFall(GuiGraphics g, int px, int py, int col) {
		g.fill(px, py - 2, px + 1, py - 1, col);
		g.fill(px - 1, py - 1, px + 2, py + 1, col);
		g.fill(px, py + 1, px + 1, py + 2, col);
	}

	/** Капающая капля на панели/карточке: растёт на нижнем крае, отрывается и падает. */
	private static void panelDrip(GuiGraphics g, int px, int bottom, float t, int seed, int s, int col, float maxLen) {
		float r = rnd(seed * 12.9898f + s * 78.233f, 43758.5453f);
		float period = 5f + 3.5f * r;
		float u = ((t + period * frac(r * 7.31f)) % period) / period;
		if (u < 0.6f) {
			float k = u / 0.6f;
			dripHang(g, px, bottom - 1, 1 + maxLen * k * k, 1.2f + 1.2f * k, col);
		} else if (u < 0.68f) {
			dripHang(g, px, bottom - 1, maxLen * (1f - (u - 0.6f) / 0.08f), 2.4f, col);
		}
		if (u >= 0.6f) {
			float f = (u - 0.6f) / 0.4f;
			int a = (int) ((col >>> 24) * (1f - f * f));
			if (a > 5) dripFall(g, px, bottom + (int) (maxLen + 36 * f * f), alpha(col, a));
		}
	}

	/** Украшения темы поверх панели или карточки: уголки, иней, заклёпки, волна, неоновая линия. ease 0..1 — «активность». */
	static void deco(GuiGraphics g, int x, int y, int w, int h, Theme th, float ease) {
		if (th.classic()) return;
		int acc = th.accent();
		switch (th.look().deco()) {
			case BRACKETS -> {
				int a = (int) (120 + 135 * ease);
				int c = alpha(acc, a);
				g.fill(x + 4, y, x + 14, y + 2, c);
				g.fill(x, y + 4, x + 2, y + 14, c);
				g.fill(x + w - 14, y + h - 2, x + w - 4, y + h, c);
				g.fill(x + w - 2, y + h - 14, x + w, y + h - 4, c);
			}
			case SYNTH -> {
				int a = (int) (150 + 100 * ease);
				g.fill(x + 1, y, x + w / 2, y + 1, alpha(th.glowA(), a));
				g.fill(x + w / 2, y, x + w - 1, y + 1, alpha(acc, a));
				g.fill(x + 1, y + h - 1, x + w / 2, y + h, alpha(acc, a / 2));
				g.fill(x + w / 2, y + h - 1, x + w - 1, y + h, alpha(th.glowA(), a / 2));
			}
			case STUDS -> {
				int gold = alpha(acc, 235), hi = 0xFFFFF4C0, dk = 0xFF6A4A14;
				int[][] pts = {{x + 1, y + 1}, {x + w - 4, y + 1}, {x + 1, y + h - 4}, {x + w - 4, y + h - 4}};
				for (int[] p : pts) {
					g.fill(p[0], p[1], p[0] + 3, p[1] + 3, gold);
					g.fill(p[0], p[1], p[0] + 1, p[1] + 1, hi);
					g.fill(p[0] + 2, p[1] + 2, p[0] + 3, p[1] + 3, dk);
				}
			}
			case FROST -> {
				int fa = (int) (80 + 60 * ease);
				g.fill(x + 3, y + 1, x + w - 3, y + 3, alpha(0xFFFFFF, fa));
				for (int ix = x + 6; ix < x + w - 8; ix += 9) { // сосульки
					int len = 2 + ((ix * 7) % 5);
					g.fill(ix, y + 3, ix + 3, y + 4, alpha(0xFFFFFF, fa + 20));
					g.fill(ix + 1, y + 4, ix + 2, y + 3 + len, alpha(0xFFFFFF, fa + 30));
				}
			}
			case DRIPS -> { // капли тьмы висят на нижнем крае и отрываются
				float tt = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f;
				int n = Math.max(2, Math.min(6, w / 40));
				int col = alpha(mix(th.cardBottom(), acc, 0.18f), 245);
				g.fill(x + 2, y + h - 1, x + w - 2, y + h, alpha(acc, (int) (60 + 90 * ease))); // влажный блик на краю
				for (int i = 0; i < n; i++) {
					int px = x + 8 + (int) ((w - 16) * (n == 1 ? 0.5f : (i + 0.5f * rnd(x * 0.37f + i * 5.1f, 4375.5f)) / n));
					panelDrip(g, px, y + h, tt, x * 31 + y * 17 + w, i, col, 7f);
				}
			}
			case CRACKS -> { // светящиеся трещины в камне
				float tt = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f;
				int pul = (int) (120 + 90 * (0.5 + 0.5 * Math.sin(tt * 2.1 + x * 0.1)) + 45 * ease);
				int c1 = alpha(0xFF7A20, Math.min(255, pul)), c2 = alpha(0xFFD060, Math.min(255, pul / 2 + 40));
				int[][] a1 = {{0, 7}, {5, 9}, {8, 15}, {14, 17}, {17, 24}};
				for (int i = 0; i + 1 < a1.length; i++) {
					line(g, x + a1[i][0] + 1, y + a1[i][1] - 4, x + a1[i + 1][0] + 1, y + a1[i + 1][1] - 4, c1);
					line(g, x + w - 2 - a1[i][0], y + h - 1 - a1[i][1] + 4, x + w - 2 - a1[i + 1][0], y + h - 1 - a1[i + 1][1] + 4, c1);
				}
				g.fill(x + 9, y + 10, x + 10, y + 11, c2);
				g.fill(x + w - 11, y + h - 12, x + w - 10, y + h - 11, c2);
			}
			case TAPE -> { // скотч сверху (по краям) — как у приклеенного листка
				int tc = alpha(0xFFE88A, 190), tb = alpha(0xB89A3A, 150);
				g.fill(x + 5, y - 3, x + 21, y + 4, tc);
				outline(g, x + 5, y - 3, 16, 7, tb);
				g.fill(x + w - 21, y - 3, x + w - 5, y + 4, tc);
				outline(g, x + w - 21, y - 3, 16, 7, tb);
			}
			case ORNAMENT -> { // Hollow Knight: ромбы-узоры по центру верха и низа и точки по углам
				int c = alpha(0xE6EEF8, (int) (150 + 100 * ease));
				diamond(g, x + w / 2, y, 3, c);
				diamond(g, x + w / 2, y + h - 1, 3, c);
				g.fill(x + w / 2 - 16, y, x + w / 2 - 5, y + 1, c);
				g.fill(x + w / 2 + 6, y, x + w / 2 + 17, y + 1, c);
				g.fill(x + 5, y + 5, x + 7, y + 7, alpha(0xE6EEF8, 90));
				g.fill(x + w - 7, y + 5, x + w - 5, y + 7, alpha(0xE6EEF8, 90));
			}
			case KEY -> { // Hades: полоска меандра сверху внутри панели
				if (w > 40 && w <= 110 && h > 60) Indie.meander(g, x + 6, y + 5, w - 12, alpha(0xD8A83A, (int) (60 + 70 * ease)));
			}
			case NAILS -> { // Stardew: шляпки гвоздей по углам
				int c = 0xFF6A3A12;
				if (Math.min(w, h) >= 34) {
					g.fill(x + 8, y + 8, x + 10, y + 10, c);
					g.fill(x + w - 10, y + 8, x + w - 8, y + 10, c);
					g.fill(x + 8, y + h - 10, x + 10, y + h - 8, c);
					g.fill(x + w - 10, y + h - 10, x + w - 8, y + h - 8, c);
				}
			}
			case SPARKLE -> { // Celeste: мерцающие искры на гранях
				float tt = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f;
				for (int i = 0; i < 2; i++) {
					int sx = x + 5 + (int) ((w - 10) * rnd(x * 0.17f + i * 4.1f + y * 0.05f, 9631.5f));
					int sy = y + 4 + (int) ((h - 8) * rnd(y * 0.13f + i * 6.7f + x * 0.03f, 7919.3f));
					int a = (int) (230 * Math.max(0, Math.sin(tt * 1.8f + i * 2.4f + sx)));
					if (a > 10) {
						g.fill(sx - 2, sy, sx + 3, sy + 1, alpha(0xFFE0F0, a));
						g.fill(sx, sy - 2, sx + 1, sy + 3, alpha(0xFFE0F0, a));
					}
				}
			}
			case GLOSS -> { // глянец и искры
				float tt = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f;
				g.fillGradient(x + 2, y + 1, x + w - 2, y + h / 2, alpha(0xFFFFFF, 90), alpha(0xFFFFFF, 0));
				for (int i = 0; i < 2; i++) {
					int sx = x + 6 + (int) ((w - 12) * rnd(x * 0.13f + i * 3.3f + y * 0.07f, 9631.5f));
					int sy = y + 5 + (int) ((h - 10) * rnd(y * 0.11f + i * 7.7f + x * 0.05f, 7919.3f));
					float tw = (float) Math.max(0, Math.sin(tt * 2f + i * 2.4f + sx));
					int a = (int) (220 * tw);
					if (a > 8) {
						g.fill(sx - 2, sy, sx + 3, sy + 1, alpha(0xFFFFFF, a));
						g.fill(sx, sy - 2, sx + 1, sy + 3, alpha(0xFFFFFF, a));
					}
				}
			}
			case WAVE -> {
				float tt = (System.nanoTime() % 100_000_000_000L) / 1e9f;
				int a = (int) (70 + 100 * ease);
				for (int dx = 3; dx < w - 3; dx += 2) {
					int yy = y + 1 + (int) Math.round(1.3 * Math.sin(dx * 0.2 + tt * 2.4));
					g.fill(x + dx, yy, x + dx + 2, yy + 1, alpha(acc, a));
				}
			}
			default -> { }
		}
	}

	/** Стеклянная панель для групп элементов. */
	static void panel(GuiGraphics g, int x, int y, int w, int h, Theme th) {
		if (th.classic()) {
			g.fill(x, y, x + w, y + h, 0xC0000000);
			outline(g, x, y, w, h, th.border());
			return;
		}
		if (th.look().shape() == Theme.Shape.THICK || th.look().shape() == Theme.Shape.TOON) { // жёсткая тень без размытия
			g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0xAA000000);
		} else if (th.look().shape() == Theme.Shape.SKETCH) { // тень листка бумаги
			g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x38000000);
		} else {
			g.fill(x + 1, y + h, x + w - 1, y + h + 2, 0x44000000);
		}
		shapePanel(th, g, x, y, w, h, alpha(th.cardTop(), 0xE0), alpha(th.cardBottom(), 0xE8));
		shapeOutline(th, g, x, y, w, h, alpha(th.border(), 0xFF));
		g.fill(x + 3, y + 1, x + w - 3, y + 2, 0x18FFFFFF);
		deco(g, x, y, w, h, th, 0f);
	}

	// ---------------------------------------------------------------- жидкие кнопки (тема «Тьма»)

	/** Кнопка «нефтяная плёнка»: см. {@link OilButton}. */
	private static void liquidButton(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
									 float hover, boolean active, int kind, Theme th) {
		OilButton.draw(g, x, y, w, h, hover, active, kind);
		int tc = !active ? th.sub() : kind == 2 ? 0xFFFFC0C8 : 0xFFEDE6FF;
		g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, tc);
	}

	/** Свечение вокруг поля ввода. */
	static void fieldRing(GuiGraphics g, int x, int y, int w, int h, float focus, Theme th) {
		if (focus <= 0.01f || th.classic()) return;
		int a1 = (int) (150 * focus), a2 = (int) (55 * focus);
		outline(g, x - 1, y - 1, w + 2, h + 2, alpha(th.accent(), a1));
		outline(g, x - 2, y - 2, w + 4, h + 4, alpha(th.accent(), a2));
	}

	// ---------------------------------------------------------------- кнопка
	// kind: 0 — обычная, 1 — главная (акцентная), 2 — опасная (красная)

	/** Обрезает строку с «…», если она шире maxW (длинные названия не вылезают за кнопку/плитку). */
	static String fit(Font font, String s, int maxW) {
		if (s == null || maxW <= 0 || font.width(s) <= maxW) return s;
		int ell = font.width("\u2026");
		String t = s;
		while (t.length() > 1 && font.width(t) + ell > maxW) t = t.substring(0, t.length() - 1);
		return t.stripTrailing() + "\u2026";
	}

	static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
					   float hover, boolean active, int kind, Theme th) {
		label = fit(font, label, w - 8);
		if (th.classic()) { // кнопка как в ванильном Minecraft: без анимаций и свечения (цвет берётся из темы: есть светлая и тёмная классика)
			boolean hov = hover > 0.5f && active;
			int base = th.btnTop() | 0xFF000000;
			int face = !active ? mix(base, 0xFF000000, 0.45f)
					: kind == 2 ? mix(base, 0xFFB04040, hov ? 0.5f : 0.35f)
					: (hov ? mix(base, 0xFF9090C0, 0.35f) : base);
			g.fill(x, y, x + w, y + h, 0xFF000000);
			g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
			if (active) {
				int lit = mix(face, 0xFFFFFFFF, 0.42f), mid = mix(face, 0xFFFFFFFF, 0.3f), shade = mix(face, 0xFF000000, 0.45f);
				g.fill(x + 1, y + 1, x + w - 1, y + 2, lit);
				g.fill(x + 1, y + 1, x + 2, y + h - 1, mid);
				g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shade);
				g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, shade);
			}
			if (hov) outline(g, x, y, w, h, 0xFFFFFFFF);
			g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2,
					!active ? 0xFFA0A0A0 : hov ? 0xFFFFFFA0 : 0xFFFFFFFF);
			return;
		}
		if (Indie.textMenu(th)) {
			Indie.menuText(g, font, x, y, w, h, label, hover, active, kind, th);
			return;
		}
		if (th.look().liquid()) {
			liquidButton(g, font, x, y, w, h, label, hover, active, kind, th);
			return;
		}
		boolean light = th.light();
		int topC, botC, border, textC;
		if (!active) {
			topC = light ? 0xFFD2D2CA : 0xFF2A2A2E;
			botC = light ? 0xFFBEBEB6 : 0xFF1A1A1E;
			border = light ? 0xFFA4A49C : 0xFF3A3A40;
			textC = light ? 0xFF8A8A88 : 0xFF777780;
		} else {
			int baseTop = th.btnTop(), baseBot = th.btnBottom(), accent = th.accent();
			if (kind == 1) {
				Theme.Shape ks = th.look().shape();
				float kk = ks == Theme.Shape.TERRA ? 0.2f : ks == Theme.Shape.BOXED ? 0.1f : ks == Theme.Shape.GILT ? 0.3f : 0.55f;
				baseTop = mix(th.btnTop(), accent, light ? 0.16f : kk);
				baseBot = mix(th.btnBottom(), accent, light ? 0.10f : kk * 0.5f);
			} else if (kind == 2) {
				baseTop = light ? 0xFFF2B8B8 : 0xFF5A2428;
				baseBot = light ? 0xFFE49A9A : 0xFF2C1114;
				accent = light ? 0xFFD03030 : 0xFFFF5A5A;
			}
			topC = mix(baseTop, accent, hover * (light ? 0.22f : 0.45f));
			botC = mix(baseBot, accent, hover * (light ? 0.12f : 0.18f));
			border = mix(th.border(), accent, 0.35f + hover * 0.65f);
			textC = th.text();
			if (hover > 0.02f && !light) {
				g.fill(x - 1, y - 1, x + w + 1, y + h + 1, alpha(accent, (int) (50 * hover)));
			}
		}
		Theme.Shape sh = th.look().shape();
		if (sh == Theme.Shape.ROUND) {
			g.fill(x + 1, y + h, x + w - 1, y + h + 2, 0x55000000);
			rect(g, x, y, w, h, topC, botC);
			outline(g, x, y, w, h, border);
			g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
		} else {
			if (sh == Theme.Shape.THICK) g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0xCC000000); // жёсткая тень
			else if (sh == Theme.Shape.SKETCH) g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x40000000);
			else g.fill(x + 1, y + h, x + w - 1, y + h + 2, 0x55000000);
			shapePanel(th, g, x, y, w, h, topC, botC);
			shapeOutline(th, g, x, y, w, h, border);
			if (sh != Theme.Shape.ORNATE) g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x22FFFFFF);
			if (active && hover > 0.03f) deco(g, x, y, w, h, th, hover);
		}

		if (sh == Theme.Shape.SKETCH && active && hover > 0.03f) // маркер под текстом
			g.fill(x + 5, y + h / 2 + 1, x + 5 + (int) ((w - 10) * hover), y + h / 2 + 7, alpha(0xFFE25A, 140));
		int ty = y + (h - 8) / 2;
		if (active) { // контраст текста к цвету кнопки: тёмный на светлой, светлый на тёмной
			int face = mix(topC, botC, 0.5f);
			float lum = (((face >> 16) & 255) * 0.299f + ((face >> 8) & 255) * 0.587f + (face & 255) * 0.114f) / 255f;
			if (lum > 0.55f) textC = mix(th.text(), 0xFF000000, 0.55f) | 0xFF000000;
			else if (lum < 0.4f && ((th.text() >> 8) & 255) < 120) textC = 0xFFF2F2F2;
		}
		if (active && (sh == Theme.Shape.TERRA || sh == Theme.Shape.BOXED || sh == Theme.Shape.GILT)) // жёлтый текст при наведении, как в этих играх
			textC = mix(textC, sh == Theme.Shape.GILT ? 0xFFFFC94A : 0xFFFFE600, hover);
		if (sh == Theme.Shape.BOXED && active && hover > 0.35f) Indie.heart(g, x + 6, y + (h - 6) / 2, 0xFFFF0000); // красное сердечко-курсор
		if (light) g.drawString(font, label, x + (w - font.width(label)) / 2, ty, textC, false); // без тени: на светлом она мылит текст
		else g.drawCenteredString(font, label, x + w / 2, ty, textC);
		if (active && hover > 0.03f) {
			int half = (int) ((w / 2f - 4) * hover);
			g.fill(x + w / 2 - half, y + h - 2, x + w / 2 + half, y + h - 1, alpha(kind == 2 ? 0xFF5A5A : th.accent(), 220));
		}
	}
}
