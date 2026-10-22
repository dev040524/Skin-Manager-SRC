package dev.skinlibrary.screen;

/**
 * Программная отрисовка «нефтяной» кнопки: поле расстояний (скруглённый прямоугольник + мениск-«рог» к курсору),
 * затем освещение: чёрное тело, яркий блик и радужная плёнка по краю (цвет зависит от наклона поверхности).
 * Чистая Java, без Minecraft: результат — массив ARGB. Координаты кнопки — в пикселях интерфейса,
 * текстура в S раз подробнее (суперсэмплинг).
 */
final class OilField {
	static final int S = 2;
	static final int MX = 70, MY = 36;

	private OilField() {}

	/** Область тела кнопки (без мениска и капель) в пикселях текстуры. */
	static void bodyRegion(int w, int h, int[] out) {
		out[0] = (-3 + MX) * S; out[1] = (-3 + MY) * S; out[2] = (w + 3 + MX) * S; out[3] = (h + 3 + MY) * S;
	}

	/** Область капли с корнем в px. */
	static void dripRegion(int h, float px, int[] out) {
		out[0] = (int) ((px - 14f + MX) * S); out[1] = (int) ((h - 12f + MY) * S);
		out[2] = (int) ((px + 14f + MX) * S); out[3] = (int) ((h + 32f + MY) * S);
	}

	/** Область мениска от корня (rx,ry) до кончика (tx,ty). */
	static void tipRegion(float rx, float ry, float tx, float ty, int[] out) {
		out[0] = (int) ((Math.min(rx, tx) - 20f + MX) * S); out[1] = (int) ((Math.min(ry, ty) - 20f + MY) * S);
		out[2] = (int) Math.ceil((Math.max(rx, tx) + 20f + MX) * S); out[3] = (int) Math.ceil((Math.max(ry, ty) + 20f + MY) * S);
	}

	static int texW(int w) {
		return (w + 2 * MX) * S;
	}

	static int texH(int h) {
		return (h + 2 * MY) * S;
	}

	private static float smin(float a, float b, float k) {
		float h = Math.max(k - Math.abs(a - b), 0f) / k;
		return Math.min(a, b) - h * h * k * 0.25f;
	}

	private static float sdBox(float px, float py, float hw, float hh, float r) {
		float qx = Math.abs(px) - hw + r, qy = Math.abs(py) - hh + r;
		float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
		return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
	}

	private static float sdSeg(float px, float py, float ax, float ay, float bx, float by, float ra, float rb) {
		float pax = px - ax, pay = py - ay, bax = bx - ax, bay = by - ay;
		float h = Math.max(0f, Math.min(1f, (pax * bax + pay * bay) / (bax * bax + bay * bay + 1e-6f)));
		float dx = pax - bax * h, dy = pay - bay * h;
		return (float) Math.sqrt(dx * dx + dy * dy) - (ra + (rb - ra) * h);
	}

	static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float i = (float) Math.floor(h * 6f), f = h * 6f - i;
		float p = v * (1f - s), q = v * (1f - f * s), t = v * (1f - (1f - f) * s);
		float r, g, b;
		switch (((int) i) % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (Math.min(1f, r) * 255) << 16) | ((int) (Math.min(1f, g) * 255) << 8) | (int) (Math.min(1f, b) * 255);
	}

	private static float pow16(float x) {
		x *= x; x *= x; x *= x; return x * x;
	}

	/**
	 * @param tipOn   есть ли мениск; (rx,ry) — корень на краю кнопки, (tx,ty) — кончик; координаты от левого верхнего угла кнопки
	 * @param kind    0 обычная, 1 главная (светлее), 2 опасная (красный отлив)
	 */
	static void render(int[] pix, float[] d, int w, int h, float hover, int kind, boolean active,
					   boolean tipOn, float rx, float ry, float tx, float ty, float[] drip, int nd, int[] reg, boolean clear) {
		int tw = texW(w), th = texH(h);
		float rr = Math.min(h * 0.45f, 9.5f), hw = w / 2f, hh = h / 2f;

		// --- мениск: цепочка конусов от корня к кончику по дуге (лёгкий изгиб вверх), плавно сливается с телом
		final int N = 9;
		float[] hx = new float[N + 1], hy = new float[N + 1], hr = new float[N + 1];
		float hbx0 = 1e9f, hby0 = 1e9f, hbx1 = -1e9f, hby1 = -1e9f;
		if (tipOn) {
			float dx = tx - rx, dy = ty - ry, len = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
			float nx = -dy / len, ny = dx / len;
			float cx = rx + dx * 0.5f + nx * len * 0.16f, cy = ry + dy * 0.5f + ny * len * 0.16f;
			for (int i = 0; i <= N; i++) {
				float u = i / (float) N, v = 1f - u;
				hx[i] = v * v * rx + 2 * v * u * cx + u * u * tx;
				hy[i] = v * v * ry + 2 * v * u * cy + u * u * ty;
				hr[i] = 7.5f * (1f - u) + 2.0f * u;
				hbx0 = Math.min(hbx0, hx[i] - hr[i] - 10f);
				hby0 = Math.min(hby0, hy[i] - hr[i] - 10f);
				hbx1 = Math.max(hbx1, hx[i] + hr[i] + 10f);
				hby1 = Math.max(hby1, hy[i] + hr[i] + 10f);
			}
		}

		// рабочая область (в пикселях текстуры) задана вызывающим
		int ci0 = Math.max(1, reg[0]), cj0 = Math.max(1, reg[1]), ci1 = Math.min(tw - 1, reg[2]), cj1 = Math.min(th - 1, reg[3]);
		if (clear) for (int j = cj0; j < cj1; j++) java.util.Arrays.fill(pix, j * tw + ci0, j * tw + ci1, 0);

		// --- проход 1: поле расстояний (в пикселях интерфейса); вдали от тела и мениска пропускаем
		for (int j = cj0; j < cj1; j++) {
			float gy = (j + 0.5f) / S - MY;
			for (int i = ci0; i < ci1; i++) {
				float gx = (i + 0.5f) / S - MX;
				float v = sdBox(gx - hw, gy - hh, hw, hh, rr);
				if (tipOn && gx > hbx0 && gx < hbx1 && gy > hby0 && gy < hby1) {
					float dh = 1e9f;
					for (int s = 0; s < N; s++) dh = Math.min(dh, sdSeg(gx, gy, hx[s], hy[s], hx[s + 1], hy[s + 1], hr[s], hr[s + 1]));
					float ex = gx - tx, ey = gy - ty;
					dh = Math.min(dh, (float) Math.sqrt(ex * ex + ey * ey) - 2.3f);
					v = smin(v, dh, 8.5f);
				}
				if (gy > h - 8f) {
					for (int q = 0; q < nd; q++) {
						float px = drip[q * 4], len = drip[q * 4 + 1], fall = drip[q * 4 + 2], rad = drip[q * 4 + 3];
						if (len > 0f) {
							float neck = Math.max(1.3f, 3.6f - len * 0.12f);
							float dd = sdSeg(gx, gy, px, h - 5f, px, h - 2f + len, 4.6f, neck);
							float ex = gx - px, ey = gy - (h - 2f + len);
							dd = smin(dd, (float) Math.sqrt(ex * ex + ey * ey) - rad, 3.5f);
							v = smin(v, dd, 6f);
						}
						if (fall >= 0f) {
							float ex = gx - px, ey = (gy - (h + 6f + fall)) * 0.8f;
							v = Math.min(v, (float) Math.sqrt(ex * ex + ey * ey) - rad);
						}
					}
				}
				d[j * tw + i] = v - hover * 0.9f;
			}
		}

		// --- проход 2: освещение
		float lx = -0.4f, ly = -0.7f, lz = 0.6f;
		float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
		lx /= ll; ly /= ll; lz /= ll;
		float hxv = lx, hyv = ly, hzv = lz + 1f;
		float hl = (float) Math.sqrt(hxv * hxv + hyv * hyv + hzv * hzv);
		hxv /= hl; hyv /= hl; hzv /= hl;

		float vmul = (kind == 1 ? 1.35f : 1f) * (1f + hover * 0.35f);
		float sat = 0.85f;
		float hueShift = kind == 2 ? -0.5f : 0f;
		if (!active) { vmul = 0.45f; sat = 0.25f; }
		float satIn = kind == 2 ? 0.9f : 0.6f;

		for (int j = cj0 + 1; j < cj1 - 1; j++) {
			float vrow = Math.max(0f, Math.min(1f, ((j + 0.5f) / S - MY) / h));
			// цвет «глубины» тела: почти чёрный, лёгкий подъём вверху; при наведении и у главной кнопки — холодное свечение
			float glow = (kind == 1 ? 0.07f : 0f) + hover * 0.10f * (1f - Math.abs(vrow - 0.5f) * 1.7f);
			glow = Math.max(0f, glow);
			int in = hsv(kind == 2 ? 0.98f : 0.62f, satIn, 0.035f + 0.02f * (1f - vrow) + glow);
			int inR = (in >> 16) & 255, inG = (in >> 8) & 255, inB = in & 255;
			if (!active) { inR = (int) (inR * 0.6f); inG = (int) (inG * 0.6f); inB = (int) (inB * 0.6f); }
			int inside = 0xFF000000 | (inR << 16) | (inG << 8) | inB;
			for (int i = ci0 + 1; i < ci1 - 1; i++) {
				int idx = j * tw + i;
				float v = d[idx];
				if (v > 1.2f) continue;
				if (v < -9f) { pix[idx] = inside; continue; }
				// градиент поля по соседям (единицы: пиксели интерфейса на пиксель интерфейса)
				float gxv = (d[idx + 1] - d[idx - 1]) * (S * 0.5f);
				float gyv = (d[idx + tw] - d[idx - tw]) * (S * 0.5f);
				float depth = (float) Math.sqrt(Math.max(0f, Math.min(1f, -v / 8f)));
				float nz0 = 0.10f + depth;
				float nxv = gxv * (1f - depth), nyv = gyv * (1f - depth);
				float nl = (float) Math.sqrt(nxv * nxv + nyv * nyv + nz0 * nz0) + 1e-6f;
				nxv /= nl; nyv /= nl; float nzv = nz0 / nl;
				float sp = Math.max(0f, nxv * hxv + nyv * hyv + nzv * hzv);
				float spec = pow16(pow16(sp)) * 0.38f * 2.6f; // ~ sp^256 заменён на более мягкий пик
				spec = pow16(sp) * pow16(sp) * pow16(sp) * sp; // sp^49
				float fres = 1f - nzv;
				fres = fres * (float) Math.sqrt(fres + 1e-4f);
				float hue = fres * 2.2f + nxv * 0.35f - nyv * 0.25f + 0.55f + hueShift;
				float val = Math.max(0f, Math.min(1f, (0.02f + fres * 2.1f) * vmul));
				int rgb = hsv(hue, sat, val);
				int r = (int) (((rgb >> 16) & 255) * 0.95f + spec * 255f), g = (int) (((rgb >> 8) & 255) * 0.95f + spec * 250f),
						b = (int) ((rgb & 255) * 0.95f + spec * 255f);
				// у самого края тело темнее радужной плёнки: смешиваем с «внутренним» цветом по глубине
				float mixIn = depth * depth * depth * 0.7f;
				r = (int) (r * (1f - mixIn) + inR * mixIn);
				g = (int) (g * (1f - mixIn) + inG * mixIn);
				b = (int) (b * (1f - mixIn) + inB * mixIn);
				int a = v > 0f ? (int) Math.max(0f, 255f * (0.5f - v * S) + 0f) : 255;
				if (a > 255) a = 255;
				if (a <= 0) continue;
				pix[idx] = (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
			}
		}
	}
}
