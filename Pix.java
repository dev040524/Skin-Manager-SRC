package dev.skinlibrary.screen;

/** Программный растр ARGB: смешивание «поверх», прямоугольники и диски — для запекания фонов в текстуры. */
class Pix {
	final int w, h;
	final int[] p;

	Pix(int w, int h) {
		this.w = w;
		this.h = h;
		this.p = new int[w * h];
	}

	void clear() {
		java.util.Arrays.fill(p, 0);
	}

	static int over(int dst, int src) {
		int sa = src >>> 24;
		if (sa == 255) return src;
		if (sa == 0) return dst;
		int da = dst >>> 24;
		int k = da * (255 - sa) / 255;
		int oa = sa + k;
		if (oa == 0) return 0;
		int r = (((src >> 16) & 255) * sa + ((dst >> 16) & 255) * k) / oa;
		int g = (((src >> 8) & 255) * sa + ((dst >> 8) & 255) * k) / oa;
		int b = ((src & 255) * sa + (dst & 255) * k) / oa;
		return oa << 24 | r << 16 | g << 8 | b;
	}

	void rect(int x0, int y0, int x1, int y1, int col) {
		x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(w, x1); y1 = Math.min(h, y1);
		for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) p[y * w + x] = over(p[y * w + x], col);
	}

	/** Вертикальный градиент с интерполяцией ARGB (в т.ч. прозрачности). */
	void grad(int x0, int y0, int x1, int y1, int c0, int c1) {
		int hh = y1 - y0;
		if (hh <= 0) return;
		for (int y = Math.max(0, y0); y < Math.min(h, y1); y++) {
			float t = hh <= 1 ? 0f : (y - y0) / (float) (hh - 1);
			int c = 0;
			for (int sft = 24; sft >= 0; sft -= 8) {
				int a = (c0 >>> sft) & 255, b = (c1 >>> sft) & 255;
				c |= (int) (a + (b - a) * t) << sft;
			}
			rect(x0, y, x1, y + 1, c);
		}
	}

	/** Тот же диск, что Indie.discS, но в растр. */
	void discS(int cx, int cy, int r, int col, int step) {
		for (int dy = -r; dy <= r; dy += step) {
			int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r + r * 0.6f - dy * dy)));
			rect(cx - hw, cy + dy, cx + hw + 1, cy + Math.min(dy + step, r + 1), col);
		}
	}

	/** Облако как Indie.cloud, но в растр. */
	void cloud(int cx, int cy, float s, int col, int shade, int a) {
		int[][] parts = {{-30, 6, 9}, {-16, -1, 13}, {2, -6, 16}, {22, -1, 13}, {36, 6, 9}};
		int sh = (a << 24) | (shade & 0xFFFFFF), cc = (a << 24) | (col & 0xFFFFFF);
		for (int[] q : parts) discS(cx + (int) (q[0] * s), cy + (int) (q[1] * s) + (int) (3 * s), (int) (q[2] * s), sh, 2);
		rect(cx - (int) (36 * s), cy + (int) (8 * s), cx + (int) (42 * s), cy + (int) (16 * s), sh);
		for (int[] q : parts) discS(cx + (int) (q[0] * s), cy + (int) (q[1] * s), (int) (q[2] * s), cc, 2);
		rect(cx - (int) (34 * s), cy + (int) (7 * s), cx + (int) (40 * s), cy + (int) (13 * s), cc);
	}
}
