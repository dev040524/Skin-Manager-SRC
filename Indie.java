package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.Input;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Темы по мотивам инди-игр: фоны, логотипы вместо заголовка и текстовые меню в духе главных меню этих игр.
 * Всё нарисовано кодом (свои рисунки, без файлов и ассетов игр).
 */
final class Indie {
	private Indie() {}

	// ------------------------------------------------------------------ мелочи

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	private static float rnd(float seed, float mul) {
		return frac((float) Math.sin(seed) * mul);
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static float smooth(float v) {
		v = clamp(v);
		return v * v * (3f - 2f * v);
	}

	private static int mix(int a, int b, float t) {
		return Ui.mix(a, b, t);
	}

	private static int al(int rgb, float a) {
		return Ui.alpha(rgb, (int) a);
	}

	/** Пиксельный диск; step — высота полосы (больше — быстрее и грубее). */
	static void disc(GuiGraphics g, int cx, int cy, int r, int col) {
		discS(g, cx, cy, r, col, 1);
	}

	static void discS(GuiGraphics g, int cx, int cy, int r, int col, int step) {
		for (int dy = -r; dy <= r; dy += step) {
			int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r + r * 0.6f - dy * dy)));
			g.fill(cx - hw, cy + dy, cx + hw + 1, cy + Math.min(dy + step, r + 1), col);
		}
	}

	static float worldTime() {
		return (System.nanoTime() / 1_000_000L % 100_000_000L) / 1000f;
	}

	/** Темы-игры, у которых главное меню — просто список слов без кнопок-плиток. */
	static boolean textMenu(Theme th) {
		switch (th.id()) {
			case "terraria", "hollow", "celeste", "hades", "cuphead", "dontstarve", "hotline", "deadcells", "cult", "slay" -> { return true; }
			default -> { return false; }
		}
	}

	static void draw(Theme.Bg bg, GuiGraphics g, int w, int h, float t, float fade, Theme th) {
		switch (bg) {
			case TERRARIA -> terraria(g, w, h, t);
			case STARDEW -> stardew(g, w, h, t);
			case UNDERTALE -> undertale(g, w, h, t);
			case HOLLOW -> hollow(g, w, h, t);
			case CELESTE -> celeste(g, w, h, t);
			case HADES -> hades(g, w, h, t);
			case CUPHEAD -> cuphead(g, w, h, t);
			case DONTSTARVE -> dontstarve(g, w, h, t);
			case HOTLINE -> hotline(g, w, h, t);
			case DEADCELLS -> deadcells(g, w, h, t);
			case CULT -> cult(g, w, h, t);
			case BALATRO -> balatro(g, w, h, t);
			case SLAY -> slay(g, w, h, t);
			case AMONGUS -> amongus(g, w, h, t);
			default -> { }
		}
	}

	private static final int[][] CLOUD_PARTS = {{-30, 6, 9}, {-16, -1, 13}, {2, -6, 16}, {22, -1, 13}, {36, 6, 9}};

	/** Пушистое пиксельное облако из дисков: тень снизу, светлая верхушка. */
	private static void cloud(GuiGraphics g, int cx, int cy, float s, int col, int shade, int a) {
		final int[][] parts = CLOUD_PARTS;
		for (int[] p : parts) discS(g, cx + (int) (p[0] * s), cy + (int) (p[1] * s) + (int) (3 * s), (int) (p[2] * s), al(shade, a), 2);
		g.fill(cx - (int) (36 * s), cy + (int) (8 * s), cx + (int) (42 * s), cy + (int) (16 * s), al(shade, a));
		for (int[] p : parts) discS(g, cx + (int) (p[0] * s), cy + (int) (p[1] * s), (int) (p[2] * s), al(col, a), 2);
		g.fill(cx - (int) (34 * s), cy + (int) (7 * s), cx + (int) (40 * s), cy + (int) (13 * s), al(col, a));
	}

	// ------------------------------------------------------------------ Terraria: главное меню — горы, сосны, облака; сутки идут по кругу

	static final float DAY_LEN = 120f;

	/** Фаза суток 0..1: [0; 0.6) — день (солнце), [0.6; 1) — ночь (луна). */
	static float dayPhase() {
		return (((worldTime() + timeShift) % DAY_LEN) + DAY_LEN) % DAY_LEN / DAY_LEN;
	}

	/** Сдвиг времени суток (секунды) — меняется, когда солнце/луну тащат мышью. */
	static float timeShift = 0f;
	/** Выключается галереей тем, чтобы миниатюра не реагировала на мышь. */
	static boolean noDrag = false;
	private static boolean sunDrag = false, wasDown = false;
	private static float dragX, dragY;

	private static void terraria(GuiGraphics g, int w, int h, float t) {
		float p = dayPhase();
		boolean sun = p < 0.6f;
		float u = sun ? p / 0.6f : (p - 0.6f) / 0.4f;
		float e = (float) Math.sin(Math.PI * u) * (sun ? 1f : -1f);
		float day = smooth((e + 0.12f) / 0.45f);
		float tw = clamp(1f - Math.abs(e) / 0.3f);
		int top = mix(0xFF040916, 0xFF2D5AD0, day), bot = mix(0xFF0E1A42, 0xFF86B8E8, day);
		bot = mix(bot, 0xFFFF9A58, tw * 0.85f);
		top = mix(top, 0xFF7A62C0, tw * 0.3f);
		g.fillGradient(0, 0, w, h, top, bot);

		float night = 1f - day;
		if (night > 0.05f) {
			for (int i = 0; i < 46; i++) {
				float r1 = rnd(i * 5.17f + 1f, 43758.5453f), r2 = rnd(i * 2.91f + 7f, 24634.6345f);
				int sx = (int) (r1 * w), sy = (int) (r2 * h * 0.6f);
				float tw2 = 0.5f + 0.5f * (float) Math.sin(t * (1f + r1 * 2f) + i);
				g.fill(sx, sy, sx + 1 + (i % 7 == 0 ? 1 : 0), sy + 1 + (i % 7 == 0 ? 1 : 0), al(0xFFFFFF, 230 * night * (0.45f + 0.55f * tw2)));
			}
		}
		int horizon = (int) (h * 0.62f);
		int bx = (int) (w * (0.06f + 0.88f * u)), by = (int) (horizon + 10 - (horizon - 10) * Math.abs((float) Math.sin(Math.PI * u)));
		if (!noDrag) {
			boolean down = Input.leftDown();
			double[] c = Input.cursorGui();
			if (c != null) {
				if (down && !wasDown && Math.hypot(c[0] - bx, c[1] - by) < 26) sunDrag = true;
				if (sunDrag && down) { // тащим тело куда угодно; время суток следует за x
					dragX = (float) c[0]; dragY = (float) c[1];
					float nu = clamp((dragX / w - 0.06f) / 0.88f);
					float np = sun ? nu * 0.6f : 0.6f + nu * 0.4f;
					timeShift = np * DAY_LEN - worldTime();
					bx = (int) dragX; by = (int) dragY;
				}
			}
			if (!down) sunDrag = false;
			wasDown = down;
		}
		if (sun) {
			disc(g, bx, by, 24, al(0xFFF0A0, 18));
			disc(g, bx, by, 19, al(0xFFF0A0, 28));
			disc(g, bx, by, 14, al(0xFFF4B0, 70));
			disc(g, bx, by, 10, 0xFFFFF4C0);
			disc(g, bx, by, 7, 0xFFFFFFE8);
		} else {
			disc(g, bx, by, 16, al(0xB4C8FF, 24));
			disc(g, bx, by, 12, al(0xB4C8FF, 40));
			disc(g, bx, by, 9, 0xFFE4EAFA);
			disc(g, bx - 3, by - 2, 2, 0xFFC4CCE4);
			disc(g, bx + 3, by + 3, 2, 0xFFC4CCE4);
		}
		float lit = 0.18f + 0.82f * day;
		int cloudC = mix(0xFF2A3562, 0xFFFFFFFF, day), cloudS = mix(0xFF1A2244, 0xFFBCD0EC, day);
		for (int i = 0; i < 7; i++) {
			int cx = (int) (((i * 107f + worldTime() * (3f + i % 3 * 2f)) % (w + 120)) - 60);
			int cy = 10 + (int) (rnd(i * 3.3f + 2f, 9631.5f) * (horizon * 0.5f));
			cloud(g, cx, cy, 0.6f + 0.25f * (i % 3), cloudC, cloudS, 240);
		}
		if (night > 0.6f) { // падающая звезда
			float per = 8f, ph = ((worldTime() + 3f) % per) / per;
			if (ph < 0.14f) {
				float k = ph / 0.14f, seed = (float) Math.floor((worldTime() + 3f) / per);
				int sx = (int) (w * (0.15f + 0.6f * rnd(seed * 4.1f, 43758.5453f)) + k * 80), sy = (int) (10 + k * 60);
				for (int s = 0; s < 8; s++) g.fill(sx - s * 2, sy - s, sx - s * 2 + 2, sy - s + 1, al(0xFFF0A0, 230 - s * 28));
				g.fill(sx, sy, sx + 2, sy + 2, 0xFFFFFFFF);
			}
		}
		// ландшафт (горы, сосны) запекается в текстуру и пересобирается только при заметной смене освещения
		int q = Math.round(lit * 32f);
		if (landBake == null || landW != w || landH != h) {
			if (landBake != null) landBake.close();
			landBake = new Bake(Math.max(1, w), Math.max(1, h));
			landW = w; landH = h; landQ = -1;
		}
		if (q != landQ) {
			landQ = q;
			landBake.clear();
			landscape(landBake, w, h, q / 32f);
			landBake.upload();
		}
		landBake.draw(g, 0, 0, w, h);
	}

	private static Bake landBake;
	private static int landW, landH, landQ;

	private static void landscape(Pix p, int w, int h, float lit) {
		// четыре слоя гор от дальнего (бледный, синий) к ближнему (зелёно-бирюзовый) с дымкой между ними
		int[][] cols = {{0xFF2A4678, 0xFF86B6D6, 0xFFA8D2E4}, {0xFF2A4670, 0xFF5EA4BC, 0xFF86CCD0},
				{0xFF1E3C60, 0xFF3F9A92, 0xFF5AB4A8}, {0xFF14283C, 0xFF2A7568, 0xFF3C9684}};
		int[] hazeC = {0xFFB4DCF0, 0xFF9CD0DC, 0xFF86C4C4, 0xFF70B4A8};
		for (int l = 0; l < 4; l++) {
			int dark = mix(cols[l][0], cols[l][1], lit), light = mix(cols[l][0], cols[l][2], lit);
			float base = h * (0.62f + 0.055f * l);
			float amp = 52f - 8f * l;
			for (int x = 0; x < w; x += 3) {
				float k = x * (0.009f + 0.0035f * l) + l * 1.7f;
				float tri = Math.abs((k % 2f) - 1f);
				float y = base - tri * amp - 7f * (float) Math.sin(x * 0.045f + l * 2f) - 3f * (float) Math.sin(x * 0.13f + l);
				boolean left = ((k % 2f) < 1f); // левый склон светлее
				p.rect(x, (int) y, x + 3, h, left ? light : dark);
				if (tri > 0.85f && l < 3) p.rect(x, (int) y, x + 3, (int) y + 2, mix(light, 0xFFFFFFFF, 0.5f * lit)); // заснеженные пики
			}
			p.grad(0, (int) (base - 4), w, (int) (base + 40), al(mix(0xFF0A1020, hazeC[l], lit) & 0xFFFFFF, 0), al(mix(0xFF0A1020, hazeC[l], lit) & 0xFFFFFF, 70));
			if (l == 1) { // дальние маленькие сосенки на втором слое
				int c2 = mix(0xFF0A1C18, 0xFF2A8068, lit);
				for (int i = 0; i < w / 14; i++) {
					int tx = i * 14 + (int) (rnd(i * 2.3f, 4711.3f) * 8f), ty = (int) (base + 8 + 4 * rnd(i * 1.7f, 9631.5f));
					for (int tier = 0; tier < 3; tier++) p.rect(tx - 1 - tier, ty - 10 + tier * 3, tx + 2 + tier, ty - 7 + tier * 3, c2);
				}
			}
		}
		// сосны переднего плана и зелёные холмы
		int green = mix(0xFF0A1C14, 0xFF2A7A4C, lit), green2 = mix(0xFF06120C, 0xFF1C5A3A, lit), green3 = mix(0xFF081810, 0xFF246848, lit);
		p.grad(0, (int) (h * 0.88f), w, h, green, green2);
		int trees = Math.max(7, w / 26);
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < trees; i++) {
				if ((i + pass) % 2 == 0 && pass == 1) continue;
				int tx = (int) ((i + 0.3f + 0.4f * rnd(i * 2.9f + pass, 4711.3f)) * (w / (float) trees));
				int th2 = (pass == 0 ? 22 : 30) + (int) (rnd(i * 5.7f + 1f + pass, 9631.5f) * (i % 3 == 0 ? 46f : 26f));
				int base = h - (i % 2) * 4 + (pass == 0 ? -10 : 0);
				int c = pass == 0 ? green3 : (i % 2 == 0 ? green2 : mix(green2, green, 0.5f));
				for (int tier = 0; tier < 6; tier++) {
					int ty = base - th2 + tier * (th2 / 6);
					int half = 2 + tier * (th2 / 12);
					p.rect(tx - half, ty + th2 / 12, tx + half + 1, ty + th2 / 6 + 1, c);
					p.rect(tx - half + 2, ty + th2 / 24, tx + half - 1, ty + th2 / 12, c);
				}
				p.rect(tx - 1, base - 3, tx + 2, base, mix(0xFF0A0604, 0xFF4A2E18, lit));
			}
		}
		for (int x = 0; x < w; x += 7) p.rect(x, h - 3 - (x / 7 % 3), x + 5, h, green2); // кусты у земли
	}


	// ------------------------------------------------------------------ Stardew	// ------------------------------------------------------------------ Stardew Valley: синее небо и огромные облака (меню создания персонажа)

	private static Bake[] cloudSpr;
	private static Bake floorBake;
	private static int floorW = -1;
	private static final int CSW = 200, CSH = 110, COX = 100, COY = 64;

	private static void stardew(GuiGraphics g, int w, int h, float t) {
		g.fillGradient(0, 0, w, h * 45 / 100, 0xFF00305E, 0xFF0A64C0);
		g.fillGradient(0, h * 45 / 100, w, h * 80 / 100, 0xFF0A64C0, 0xFF3AACEE);
		g.fillGradient(0, h * 80 / 100, w, h, 0xFF3AACEE, 0xFF68E0E0);
		float wt = worldTime();
		if (cloudSpr == null) { // спрайты облаков запекаются один раз
			cloudSpr = new Bake[4];
			for (int l = 0; l < 4; l++) {
				Bake b = new Bake(CSW, CSH);
				b.cloud(COX, COY, 0.7f + 0.45f * l, l == 0 ? 0xFF9CC8F8 : 0xFFFFFFFF, l == 0 ? 0xFF6EA0E0 : 0xFFA8C8EC, l == 0 ? 170 : 245);
				b.upload();
				cloudSpr[l] = b;
			}
		}
		for (int l = 0; l < 4; l++) {
			int n = 3 + l;
			for (int i = 0; i < n; i++) {
				float sp = 1.6f + l * 2.2f;
				int cx = (int) (((i * (w / (float) n) + l * 60f + wt * sp) % (w + 180)) - 90);
				int cy = (int) (h * (0.1f + 0.2f * l) + 22f * rnd(i * 3.1f + l * 7f, 9631.5f));
				if (cx + CSW - COX < 0 || cx - COX > w) continue;
				cloudSpr[l].draw(g, cx - COX, cy - COY, CSW, CSH);
			}
		}
		// облачный «пол»: запечённая полоса, сдвигается по модулю 46
		int fw = (w / 46 + 5) * 46 + 160;
		if (floorBake == null || floorW != w) {
			if (floorBake != null) floorBake.close();
			floorBake = new Bake(fw, 64);
			floorW = w;
			for (int i = -1; i < w / 46 + 2; i++) {
				int cx = i * 46 + 80, r = 20 + (int) (rnd(i * 2.7f + 1f, 4711.3f) * 12f);
				floorBake.discS(cx, 64 - 6 + (int) (rnd(i * 1.3f, 9631.5f) * 8f) + 3, r, 0xFFB4D2F0, 3);
			}
			for (int i = -1; i < w / 46 + 2; i++) {
				int cx = i * 46 + 22 + 80, r = 18 + (int) (rnd(i * 4.1f + 3f, 4711.3f) * 12f);
				floorBake.discS(cx, 64 - 2 + (int) (rnd(i * 5.3f, 9631.5f) * 8f), r, 0xFFFFFFFF, 3);
			}
			floorBake.upload();
		}
		floorBake.draw(g, (int) ((wt * 3f) % 46f) - 126, h - 64, fw, 64);
		for (int i = 0; i < 9; i++) { // птички
			float bx = ((wt * 10f + i * 77f) % (w + 40)) - 20, byy = h * 0.1f + 12f * (float) Math.sin(wt * 0.7f + i * 2f) + i * 6f;
			int wing = (int) (wt * 5f + i) % 2;
			g.fill((int) bx, (int) byy + wing, (int) bx + 2, (int) byy + wing + 1, 0xCC12305A);
			g.fill((int) bx + 2, (int) byy, (int) bx + 4, (int) byy + 1, 0xCC12305A);
		}
	}

	// ------------------------------------------------------------------ Undertale	// ------------------------------------------------------------------ Undertale

	private static void undertale(GuiGraphics g, int w, int h, float t) {
		g.fill(0, 0, w, h, 0xFF000000);
		for (int i = 0; i < 56; i++) {
			float r1 = rnd(i * 4.7f + 2f, 43758.5453f), r2 = rnd(i * 8.3f + 1f, 24634.6345f);
			int sx = (int) (((r1 * w) - worldTime() * (1.5f + r2 * 3f)) % w);
			if (sx < 0) sx += w;
			int sy = (int) (r2 * h);
			float tw = 0.5f + 0.5f * (float) Math.sin(worldTime() * (1.2f + r1 * 2f) + i);
			g.fill(sx, sy, sx + 1 + (i % 9 == 0 ? 1 : 0), sy + 1 + (i % 9 == 0 ? 1 : 0), al(0xFFFFFF, 60 + 150 * tw));
		}
		float pulse = 0.5f + 0.5f * (float) Math.sin(worldTime() * 2.4f);
		int cx = w - 30, cy = h - 34, r = 4 + (int) (pulse * 2f);
		for (int i = -r; i <= r; i++) {
			int a = (int) (255 * (1f - Math.abs(i) / (float) (r + 1)));
			g.fill(cx + i, cy, cx + i + 1, cy + 1, al(0xFFE600, a));
			g.fill(cx, cy + i, cx + 1, cy + i + 1, al(0xFFE600, a));
		}
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFA0);
	}

	// ------------------------------------------------------------------ Hollow Knight: серый туман, занавесы света, тёмные пятна

	private static void hollow(GuiGraphics g, int w, int h, float t) {
		g.fillGradient(0, 0, w, h / 2, 0xFF1A1C1E, 0xFF52575A);
		g.fillGradient(0, h / 2, w, h, 0xFF52575A, 0xFF0A0B0C);
		for (int i = 0; i < 5; i++) { // вертикальные занавесы света
			float cx = w * (0.14f + 0.18f * i) + 16f * (float) Math.sin(t * 0.18f + i * 1.9f);
			int bw = 26 + (i % 3) * 20;
			g.fillGradient((int) cx - bw, 0, (int) cx + bw, h * 3 / 4, al(0xD8D0CC, 0), al(0xD8D0CC, 26));
			g.fillGradient((int) cx - bw, h * 3 / 4, (int) cx + bw, h, al(0xD8D0CC, 26), al(0xD8D0CC, 0));
			g.fillGradient((int) cx - bw / 3, 0, (int) cx + bw / 3, h / 2, al(0xFFF0E8, 18), al(0xFFF0E8, 0));
		}
		for (int i = 0; i < 12; i++) { // тёмные пятна и пылинки, медленно дрейфуют
			float r1 = rnd(i * 6.1f + 1f, 43758.5453f), r2 = rnd(i * 3.3f + 5f, 24634.6345f);
			float px = (r1 * w + t * (2f + r2 * 3f)) % w, py = r2 * h + 8f * (float) Math.sin(t * 0.4f + i);
			discS(g, (int) px, (int) py, 2 + i % 3, al(0x000000, 200), 1);
		}
		for (int i = 0; i < 20; i++) {
			float r1 = rnd(i * 2.1f + 8f, 43758.5453f), r2 = rnd(i * 7.7f + 2f, 24634.6345f);
			g.fill((int) ((r1 * w + t * 2f) % w), (int) (h - ((t * (3f + r2 * 4f) + r2 * h) % h)), (int) ((r1 * w + t * 2f) % w) + 1, (int) (h - ((t * (3f + r2 * 4f) + r2 * h) % h)) + 1, al(0xE8E4E0, 90));
		}
		g.fillGradient(0, 0, 70, h, 0xB0000000, 0);
		g.fillGradient(w - 70, 0, w, h, 0, 0xB0000000);
		g.fillGradient(0, 0, w, h / 5, 0x90000000, 0);
		g.fillGradient(0, h - h / 5, w, h, 0, 0xA0000000);
	}

	// ------------------------------------------------------------------ Celeste: ночная гора, фиолетовые облака, снег

	private static void celeste(GuiGraphics g, int w, int h, float t) {
		g.fillGradient(0, 0, w, h * 6 / 10, 0xFF14305E, 0xFF3E4A88);
		g.fillGradient(0, h * 6 / 10, w, h, 0xFF3E4A88, 0xFF4878A0);
		g.fillGradient(w / 2, 0, w, h, al(0x7571AC, 0), al(0x7571AC, 110));
		g.fillGradient(w * 6 / 10, h / 3, w, h, al(0xB868A8, 0), al(0xB868A8, 50)); // розовая дымка справа
		for (int i = 0; i < 44; i++) {
			float r1 = rnd(i * 5.9f + 1f, 43758.5453f), r2 = rnd(i * 3.1f + 5f, 24634.6345f);
			float tw = 0.4f + 0.6f * (float) Math.sin(t * (0.8f + r1 * 1.6f) + i);
			g.fill((int) (r1 * w), (int) (r2 * h * 0.55f), (int) (r1 * w) + 1, (int) (r2 * h * 0.55f) + 1, al(0xFFFFFF, 200 * Math.max(0f, tw)));
		}
		for (int i = 0; i < 5; i++) { // мягкие облака
			int cx = (int) (((i * 130f + t * (2f + i % 2)) % (w + 160)) - 80), cy = (int) (h * (0.38f + 0.1f * (i % 3)));
			cloud(g, cx, cy, 1.2f + 0.3f * (i % 2), 0xFF6A68B0, 0xFF5A58A0, 130);
		}
		if (celBake == null || celW != w || celH != h) { // горы статичны: рисуем один раз в текстуру
			if (celBake != null) celBake.close();
			celBake = new Bake(Math.max(1, w), Math.max(1, h));
			celW = w; celH = h;
			celesteMountains(celBake, w, h);
			celBake.upload();
		}
		celBake.draw(g, 0, 0, w, h);
		float gust = 0.5f + 0.5f * (float) Math.sin(t * 0.4f);
		for (int i = 0; i < 44; i++) { // снег
			float r1 = rnd(i * 4.3f + 2f, 43758.5453f), r2 = rnd(i * 7.9f + 1f, 24634.6345f);
			float py = (t * (10f + r2 * 14f) + r1 * h) % (h + 6) - 3;
			float sx = (r1 * w + t * (6f + 14f * gust) * (0.5f + r2)) % w;
			int s2 = i % 5 == 0 ? 2 : 1;
			g.fill((int) sx, (int) py, (int) sx + s2, (int) py + s2, al(0xFFFFFF, 120 + 110 * r2));
		}
	}

	private static Bake celBake;
	private static int celW, celH;

	private static void celesteMountains(Pix p, int w, int h) {
		// дальний хребет
		for (int x = 0; x < w; x += 3) {
			float y = h * 0.55f - Math.abs(((x * 0.011f + 0.3f) % 2f) - 1f) * 34f - 5f * (float) Math.sin(x * 0.09f);
			p.rect(x, (int) y, x + 3, h, 0xFF3A4A86);
		}
		// главная вершина: низкополигональные грани (цвет зависит от «ячейки» в ряду и столбце)
		int px = (int) (w * 0.52f), peakY = (int) (h * 0.1f);
		int[] facetsL = {0xFF4F8CB8, 0xFF6FB0D4, 0xFF5C9CC6, 0xFF86C4E4};
		int[] facetsR = {0xFF8A9CC8, 0xFF9EB4D8, 0xFF7A88B8, 0xFFB4C4E4};
		for (int x = 0; x < w; x += 4) {
			float dx = x - px;
			float y = peakY + Math.abs(dx) * (dx < 0 ? 1.05f : 0.9f) - (float) Math.sin(x * 0.2f) * 2f;
			if (y > h) continue;
			boolean left = dx < 0;
			int[] f = left ? facetsL : facetsR;
			int cell = (int) Math.floor(x / 14f);
			int c1 = f[(cell * 7 + 1) % 4], c2 = f[(cell * 5 + 2) % 4], c3 = f[(cell * 3 + 3) % 4];
			int ya = (int) y, ymid = ya + (int) ((h - ya) * 0.45f), ymid2 = ya + (int) ((h - ya) * 0.75f);
			p.grad(x, ya, x + 4, ymid, mix(c1, 0xFFFFFFFF, 0.35f), c1);
			p.grad(x, ymid, x + 4, ymid2, c2, c2);
			p.grad(x, ymid2, x + 4, h, c3, mix(c3, 0xFF4878A0, 0.5f));
			if (Math.abs(dx) < 10) p.rect(x, ya, x + 4, ya + 3, 0xFFFFFFFF); // снежная шапка
		}
		int p2 = (int) (w * 0.8f), peak2 = (int) (h * 0.4f); // вторая вершина
		for (int x = p2 - 70; x < p2 + 70 && x < w; x += 3) {
			float y = peak2 + Math.abs(x - p2) * 0.95f;
			int col = x < p2 ? 0xFF5C6CA8 : 0xFF7280B8;
			p.grad(x, (int) y, x + 3, h, mix(col, 0xFFB8C0F0, 0.2f), col);
			if (Math.abs(x - p2) < 7) p.rect(x, (int) y, x + 3, (int) y + 2, 0xFFE8ECFF);
		}
		for (int x = 0; x < w * 0.3f; x += 3) { // тёмные предгорья слева
			float y = h * 0.62f - Math.abs(((x * 0.02f) % 2f) - 1f) * 40f + 20f;
			p.rect(x, (int) y, x + 3, h, 0xFF28366A);
		}
		p.grad(0, h * 7 / 10, w, h, al(0x9AB8D8, 0), al(0x9AB8D8, 130));
	}

	// ------------------------------------------------------------------ Hades	// ------------------------------------------------------------------ Hades: тёмный экран, фиолетовое и красное свечение, искры

	private static void hades(GuiGraphics g, int w, int h, float t) {
		g.fillGradient(0, 0, w, h, 0xFF14111C, 0xFF050407);
		g.fillGradient(0, 0, w * 6 / 10, h, al(0x4A3A7A, 70), al(0x4A3A7A, 0)); // фиолетовая фигура слева
		g.fillGradient(w * 4 / 10, 0, w, h, al(0x7A1A10, 0), al(0x7A1A10, 120)); // красное свечение справа
		float pulse = 0.7f + 0.3f * (float) Math.sin(t * 1.1f);
		g.fillGradient(w * 7 / 10, 0, w, h * 6 / 10, al(0xC8A058, 0), al(0xC8A058, 22 * pulse));
		for (int i = 0; i < 36; i++) {
			float r1 = rnd(i * 4.1f + 2f, 43758.5453f), r2 = rnd(i * 6.3f + 4f, 24634.6345f);
			float py = h - ((t * (8f + r2 * 18f) + r1 * h) % h);
			float px = r1 * w + 8f * (float) Math.sin(t * 0.7f + i);
			float fl = 0.5f + 0.5f * (float) Math.sin(t * 5f + i * 2f);
			int c = r2 > 0.7f ? 0xFFD060 : r2 > 0.35f ? 0xFF7A20 : 0xFF3A18;
			g.fill((int) px, (int) py, (int) px + 1, (int) py + 1, al(c, 60 + 140 * fl));
		}
		for (int i = 0; i < 4; i++) { // значки внизу слева: Discord и прочее
			int ix = 12 + i * 20, iy = h - 28;
			g.fill(ix, iy, ix + 13, iy + 13, al(0x1C1824, 235));
			Ui.outline(g, ix, iy, 13, 13, al(0x6A6070, 150));
			g.fill(ix + 4, iy + 4, ix + 9, iy + 9, al(0xB8B0C0, 150));
		}
		g.fillGradient(0, 0, w, h / 6, 0x90000000, 0);
		g.fillGradient(0, h - h / 4, w, h, 0, 0xB0000000);
	}

	// ------------------------------------------------------------------ Cuphead: чёрный экран, плёнка, пятно света

	private static void cuphead(GuiGraphics g, int w, int h, float t) {
		g.fill(0, 0, w, h, 0xFF060605);
		long fr = (long) (t * 12f);
		float seed = fr * 7.31f;
		// пятно света снизу справа и тень в нём
		int lx = (int) (w * 0.68f), ly = (int) (h * 0.84f), a = (int) (w * 0.3f), b = (int) (h * 0.13f);
		for (int dy = -b; dy <= b; dy += 2) {
			float k = 1f - (float) dy * dy / (b * b);
			int hw = (int) (a * Math.sqrt(Math.max(0f, k)));
			g.fill(lx - hw, ly + dy, lx + hw, ly + dy + 2, al(0x8CA8BA, 90 * k));
		}
		for (int dy = -b / 2; dy <= b / 2; dy += 2) {
			float k = 1f - (float) dy * dy / (b * b / 4f);
			int hw = (int) (a * 0.55f * Math.sqrt(Math.max(0f, k)));
			g.fill(lx + a / 6 - hw, ly + dy + b / 3, lx + a / 6 + hw, ly + dy + b / 3 + 2, al(0x000000, 190 * k));
		}
		// зерно, царапины, мерцание
		for (int i = 0; i < 120; i++) {
			float r1 = rnd(seed + i * 3.7f, 43758.5453f), r2 = rnd(seed * 0.7f + i * 9.1f, 24634.6345f);
			g.fill((int) (r1 * w), (int) (r2 * h), (int) (r1 * w) + 1, (int) (r2 * h) + 1, i % 3 == 0 ? al(0xFFFFFF, 60) : al(0x909088, 40));
		}
		g.fill((int) (w * 0.07f), 0, (int) (w * 0.07f) + 1, h, al(0xFFFFFF, 38)); // постоянная царапина
		for (int i = 0; i < 2; i++) {
			float r1 = rnd(seed + i * 11.3f, 4711.3f), r2 = rnd(seed + i * 5.7f, 9631.5f);
			if (r2 > 0.6f) g.fill((int) (r1 * w), 0, (int) (r1 * w) + 1, (int) (h * (0.3f + 0.7f * r2)), al(0xFFFFFF, 40));
		}
		if (rnd(seed, 43758.5453f) > 0.85f) g.fill(0, 0, w, h, al(0xFFFFFF, 8));
		g.fillGradient(0, 0, w, h / 5, 0x80000000, 0);
		g.fillGradient(0, h - h / 6, w, h, 0, 0x60000000);
	}


	// ------------------------------------------------------------------ Don't Starve: сепия, туман, чёрные кривые деревья, светлячки

	private static void dontstarve(GuiGraphics g, int w, int h, float t) {
		g.fillGradient(0, 0, w, h, 0xFF16110C, 0xFF3A2C1C);
		for (int l = 0; l < 3; l++) { // слои тумана
			for (int x = 0; x < w; x += 6) {
				float y = h * (0.4f + l * 0.17f) + 12f * (float) Math.sin(x * 0.011f + t * (0.1f + l * 0.05f) + l * 2f);
				g.fillGradient(x, (int) y, x + 6, h, al(0xB8A078, 0), al(0xB8A078, 28 + l * 10));
			}
		}
		for (int i = 0; i < 7; i++) { // силуэты кривых деревьев
			int tx = (int) (w * (0.04f + 0.15f * i + 0.04f * rnd(i * 2.3f, 4711.3f)));
			int th2 = 50 + (int) (rnd(i * 5.1f + 3f, 9631.5f) * 60f), bw = 3 + i % 3;
			int top = h - th2;
			for (int y = top; y < h; y += 2) {
				float sway = 2f * (float) Math.sin(y * 0.09f + i);
				g.fill(tx + (int) sway, y, tx + (int) sway + bw + (y - top) / 40, y + 2, 0xFF050403);
			}
			for (int b = 0; b < 4; b++) { // ветви
				int by = top + 6 + b * th2 / 6, dir = (b + i) % 2 == 0 ? 1 : -1, len = 10 + (int) (rnd(i * 7f + b, 4711.3f) * 14f);
				for (int k = 0; k < len; k++) g.fill(tx + dir * k, by - k / 2, tx + dir * k + 2, by - k / 2 + 2, 0xFF050403);
			}
		}
		for (int i = 0; i < 16; i++) { // светлячки
			float fx = w * rnd(i * 3.3f + 1f, 43758.5453f) + 14f * (float) Math.sin(t * 0.5f + i);
			float fy = h * (0.35f + 0.5f * rnd(i * 6.1f, 24634.6345f)) + 10f * (float) Math.cos(t * 0.6f + i * 2f);
			float tw = 0.5f + 0.5f * (float) Math.sin(t * 2f + i * 1.7f);
			disc(g, (int) fx, (int) fy, 3, al(0xE8D068, 26 * tw));
			g.fill((int) fx, (int) fy, (int) fx + 2, (int) fy + 2, al(0xFFF0A0, 230 * tw));
		}
		g.fillGradient(0, 0, 60, h, 0xA0000000, 0);
		g.fillGradient(w - 60, 0, w, h, 0, 0xA0000000);
		g.fillGradient(0, 0, w, h / 5, 0x90000000, 0);
	}

	// ------------------------------------------------------------------ Hotline Miami: неоновый закат, VHS

	private static void hotline(GuiGraphics g, int w, int h, float t) {
		float ph = t * 0.15f;
		int top = OilField.hsv(0.76f + 0.04f * (float) Math.sin(ph), 0.8f, 0.22f) | 0xFF000000;
		int bot = OilField.hsv(0.88f + 0.06f * (float) Math.sin(ph + 1f), 0.85f, 0.75f) | 0xFF000000;
		g.fillGradient(0, 0, w, h, top, bot);
		int sunY = (int) (h * 0.55f);
		disc(g, w / 2, sunY, 38, al(0xFFC83A, 40));
		for (int dy = -34; dy <= 34; dy += 2) { // солнце полосами
			if (((dy + 34) / 6) % 2 == 1 && dy > -6) continue;
			int hw = (int) Math.sqrt(34 * 34 - dy * dy);
			g.fill(w / 2 - hw, sunY + dy, w / 2 + hw, sunY + dy + 2, mix(0xFFFFE04A, 0xFFFF3E8A, (dy + 34) / 68f));
		}
		for (int x = 0; x < w; x += 9) { // силуэты города с окнами
			int bh = 14 + (int) (rnd(x * 0.37f, 4711.3f) * 46f);
			g.fill(x, h - bh, x + 8, h, 0xFF08020E);
			for (int wy = h - bh + 3; wy < h - 3; wy += 5)
				if (rnd(x * 1.7f + wy, 9631.5f) > 0.6f) g.fill(x + 2, wy, x + 4, wy + 2, al(rnd(x + wy, 311f) > 0.5f ? 0x00F0FF : 0xFF3EA5, 200));
		}
		float seed = (float) Math.floor(t * 8f);
		for (int i = 0; i < 2; i++) { // VHS-полосы
			float r = rnd(seed * 3.1f + i, 43758.5453f);
			if (r > 0.55f) {
				int gy = (int) (rnd(seed + i * 9f, 24634.6345f) * h);
				g.fill(0, gy, w, gy + 2 + (int) (r * 4f), al(0xFFFFFF, 22));
				g.fill(3, gy, w, gy + 1, al(0x00F0FF, 60));
				g.fill(0, gy + 1, w - 3, gy + 2, al(0xFF3EA5, 60));
			}
		}
		g.fillGradient(0, 0, w, h / 6, 0x90000000, 0);
	}

	// ------------------------------------------------------------------ Dead Cells: тёмная каменная кладка, факелы, цепи

	private static void deadcells(GuiGraphics g, int w, int h, float t) {
		g.fillGradient(0, 0, w, h, 0xFF10131E, 0xFF1C2132);
		for (int y = 0; y < h; y += 12) { // кирпичная кладка
			g.fill(0, y, w, y + 1, al(0x000000, 90));
			int off = (y / 12) % 2 * 12;
			for (int x = -off; x < w; x += 24) g.fill(x, y, x + 1, y + 12, al(0x000000, 70));
			if ((y / 12) % 3 == 0) for (int x = 6 - off; x < w; x += 48) g.fill(x, y + 2, x + 10, y + 3, al(0x4A5470, 40));
		}
		for (int i = 0; i < 5; i++) { // цепи
			int cx = (int) (w * (0.08f + 0.2f * i));
			int len = 40 + (int) (rnd(i * 4.3f, 4711.3f) * 50f);
			float sw = 2f * (float) Math.sin(t * 0.6f + i);
			for (int y = 0; y < len; y += 4) g.fill(cx + (int) (sw * y / len), y, cx + (int) (sw * y / len) + 2, y + 3, y / 4 % 2 == 0 ? 0xFF4A5068 : 0xFF2A2E40);
		}
		for (int i = 0; i < 3; i++) { // факелы с мерцанием
			int fx = (int) (w * (0.2f + 0.3f * i)), fy = (int) (h * 0.4f);
			float fl = 0.75f + 0.25f * (float) Math.sin(t * 9f + i * 2.1f) * (0.6f + 0.4f * (float) Math.sin(t * 4.3f + i));
			disc(g, fx, fy, 46, al(0xFF7A20, 14 * fl));
			disc(g, fx, fy, 30, al(0xFF8A30, 22 * fl));
			g.fill(fx - 1, fy + 4, fx + 2, fy + 14, 0xFF3A3040);
			g.fill(fx - 3, fy, fx + 4, fy + 4, 0xFFFF8A2A);
			g.fill(fx - 1, fy - 4 - (int) (3 * fl), fx + 2, fy, 0xFFFFD060);
		}
		for (int i = 0; i < 24; i++) {
			float r1 = rnd(i * 3.7f + 1f, 43758.5453f), r2 = rnd(i * 6.1f + 2f, 24634.6345f);
			float py = h - ((t * (8f + r2 * 14f) + r1 * h) % h);
			g.fill((int) (r1 * w + 6f * Math.sin(t + i)), (int) py, (int) (r1 * w) + 1, (int) py + 1, al(0xFF9A3A, 80 + 120 * r2));
		}
		g.fillGradient(0, 0, w, h / 5, 0xA0000000, 0);
		g.fillGradient(0, h - h / 5, w, h, 0, 0xA0000000);
		g.fillGradient(0, 0, 50, h, 0x80000000, 0);
		g.fillGradient(w - 50, 0, w, h, 0, 0x80000000);
	}

	// ------------------------------------------------------------------ Cult of the Lamb: багровое небо, чёрный лес, ритуальные знаки

	private static void cult(GuiGraphics g, int w, int h, float t) {
		g.fillGradient(0, 0, w, h, 0xFF1A0508, 0xFF6A1418);
		int mx = (int) (w * 0.5f), my = (int) (h * 0.4f);
		disc(g, mx, my, 54, al(0xE83A3A, 18));
		disc(g, mx, my, 42, al(0xE85A4A, 40));
		disc(g, mx, my, 32, 0xFFD8584A);
		disc(g, mx - 8, my - 6, 8, 0xFFC04438);
		for (int i = 0; i < 4; i++) { // кружащиеся знаки
			float a = t * 0.2f + i * 1.57f;
			int sx = mx + (int) (Math.cos(a) * 62), sy = my + (int) (Math.sin(a) * 30);
			g.fill(sx - 3, sy + 2, sx + 4, sy + 3, al(0xF4E4D0, 140));
			g.fill(sx - 2, sy, sx + 3, sy + 1, al(0xF4E4D0, 140));
			g.fill(sx - 1, sy - 2, sx + 2, sy - 1, al(0xF4E4D0, 140));
			g.fill(sx, sy - 3, sx + 1, sy - 2, al(0xF4E4D0, 140));
		}
		for (int i = 0; i < 18; i++) { // чёрные ёлки
			int tx = (int) (w * (i / 17f)) + (int) (rnd(i * 2.9f, 4711.3f) * 10f);
			int th2 = 36 + (int) (rnd(i * 5.7f + 1f, 9631.5f) * 40f);
			for (int tier = 0; tier < 5; tier++) {
				int ty = h - th2 + tier * (th2 / 5);
				int half = 2 + tier * (th2 / 14);
				g.fill(tx - half, ty, tx + half + 1, ty + th2 / 5 + 1, 0xFF040102);
			}
		}
		for (int i = 0; i < 30; i++) { // красные частицы
			float r1 = rnd(i * 4.7f + 1f, 43758.5453f), r2 = rnd(i * 2.3f + 5f, 24634.6345f);
			float py = h - ((t * (6f + r2 * 12f) + r1 * h) % h);
			g.fill((int) (r1 * w + 5f * Math.sin(t * 0.8f + i)), (int) py, (int) (r1 * w) + 2, (int) py + 2, al(0xFF5A4A, 90 + 100 * r2));
		}
		g.fillGradient(0, h - 50, w, h, al(0x000000, 0), al(0x000000, 200));
	}

	// ------------------------------------------------------------------ Balatro: зелёно-красный вихрь, CRT

	private static Bake balBake;
	private static int balW, balH;
	private static long balLast;

	private static void balatro(GuiGraphics g, int w, int h, float t) {
		final int C = 5; // размер ячейки вихря в GUI-пикселях
		int tw = Math.max(8, w / 2), th = Math.max(8, h / 2);
		if (balBake == null || balW != w || balH != h) {
			if (balBake != null) balBake.close();
			balBake = new Bake(tw, th);
			balW = w; balH = h; balLast = 0;
		}
		long now = System.nanoTime();
		if (now - balLast > 50_000_000L) { // обновляем ~20 раз в секунду
			balLast = now;
			float tt = now / 1e9f % 100000f;
			for (int j = 0; j < th; j++) {
				int y = (j * 2) / C * C;
				float v = y * 0.02f;
				boolean scan = j % 2 == 1;
				float fy = j / (float) th;
				for (int i = 0; i < tw; i++) {
					int x = (i * 2) / C * C;
					float u = x * 0.014f;
					float wx = u + 1.1f * (float) Math.sin(v * 1.6f + tt * 0.35f), wy = v + 1.1f * (float) Math.sin(u * 1.2f - tt * 0.3f);
					float n = (float) (Math.sin(wx * 2.4f + tt * 0.2f) + Math.sin(wy * 2.9f - tt * 0.25f) + Math.sin((wx + wy) * 1.8f)) / 3f;
					int col = n > 0.15f ? mix(0xFF1A5A52, 0xFFC8382E, clamp((n - 0.15f) * 3f)) : mix(0xFF0A2A28, 0xFF1A5A52, clamp((n + 0.6f) * 1.6f));
					float fx = i / (float) tw;
					float edge = Math.min(Math.min(fx, 1f - fx) * 5f, Math.min(fy, 1f - fy) * 8f);
					float dark = (1f - clamp(edge)) * 0.6f + (scan ? 0.14f : 0f);
					balBake.p[j * tw + i] = mix(col, 0xFF000000, clamp(dark));
				}
			}
			balBake.upload();
		}
		balBake.draw(g, 0, 0, w, h);
	}

	// ------------------------------------------------------------------ Slay the Spire: башня в облаках, огонь внизу

	private static void slay(GuiGraphics g, int w, int h, float t) {
		g.fillGradient(0, 0, w, h, 0xFF14161E, 0xFF2A1A18);
		for (int i = 0; i < 4; i++) {
			int cx = (int) (((i * 150f + t * (2f + i % 2)) % (w + 200)) - 100), cy = (int) (h * (0.15f + 0.12f * i));
			cloud(g, cx, cy, 1.6f, 0xFF3A3C4C, 0xFF2A2C3A, 150);
		}
		int tx = (int) (w * 0.72f); // башня
		for (int y = (int) (h * 0.18f); y < h; y += 2) {
			float k = (y - h * 0.18f) / (h * 0.82f);
			int half = 5 + (int) (k * 20f);
			g.fill(tx - half, y, tx + half, y + 2, y % 8 < 2 ? 0xFF0C0C12 : 0xFF08080C);
		}
		g.fill(tx - 9, (int) (h * 0.18f) - 8, tx + 9, (int) (h * 0.18f), 0xFF0A0A10);
		g.fill(tx - 1, (int) (h * 0.18f) - 18, tx + 2, (int) (h * 0.18f) - 8, 0xFF0A0A10);
		for (int i = 0; i < 9; i++) {
			int wy = (int) (h * 0.28f) + i * (int) (h * 0.07f);
			if (rnd(i * 3.1f, 4711.3f) > 0.35f) g.fill(tx - 2 + (i % 2) * 4, wy, tx + 1 + (i % 2) * 4, wy + 3, al(0xFFC060, 160 + 80 * (float) Math.sin(t * 2f + i)));
		}
		float fl = 0.7f + 0.3f * (float) Math.sin(t * 3f);
		g.fillGradient(0, h - 90, w, h, al(0xFF6A20, 0), al(0xFF6A20, 90 * fl));
		for (int i = 0; i < 34; i++) {
			float r1 = rnd(i * 4.1f + 2f, 43758.5453f), r2 = rnd(i * 6.3f + 4f, 24634.6345f);
			float py = h - ((t * (10f + r2 * 18f) + r1 * h) % h);
			g.fill((int) (r1 * w + 8f * Math.sin(t * 0.7f + i)), (int) py, (int) (r1 * w) + 1, (int) py + 1, al(r2 > 0.5f ? 0xFFB040 : 0xFF5A20, 80 + 140 * r2));
		}
		g.fillGradient(0, 0, w, h / 6, 0x90000000, 0);
	}

	// ------------------------------------------------------------------ Among Us: космос с параллаксом и плывущими «планетами»

	private static void amongus(GuiGraphics g, int w, int h, float t) {
		g.fillGradient(0, 0, w, h, 0xFF03040A, 0xFF0C1022);
		for (int l = 0; l < 3; l++) { // три слоя звёзд
			int n = 26 + l * 12;
			for (int i = 0; i < n; i++) {
				float r1 = rnd(i * 4.7f + l * 9f, 43758.5453f), r2 = rnd(i * 8.3f + l, 24634.6345f);
				int sx = (int) (((r1 * w) - t * (3f + l * 5f)) % w);
				if (sx < 0) sx += w;
				int sz = l == 2 ? 2 : 1;
				g.fill(sx, (int) (r2 * h), sx + sz, (int) (r2 * h) + sz, al(0xFFFFFF, 90 + l * 50));
			}
		}
		int[] cols = {0xFF5A7AD8, 0xFFD85A8A, 0xFF4AB8A0, 0xFFE0A040, 0xFF8A5AD8};
		for (int i = 0; i < 5; i++) { // плывущие планеты-шарики с тенью
			float sp = 4f + i * 2f;
			int cx = (int) (((i * 97f + w * 0.3f - t * sp) % (w + 80)) + (w + 80)) % (w + 80) - 40;
			int cy = (int) (h * (0.15f + 0.17f * i)) + (int) (6f * Math.sin(t * 0.5f + i));
			int r = 6 + (i % 3) * 4;
			disc(g, cx, cy, r + 2, al(cols[i], 22));
			disc(g, cx, cy, r, cols[i]);
			discS(g, cx + r / 3, cy + r / 3, r - 1, al(0x000000, 80), 1);
			disc(g, cx - r / 3, cy - r / 3, Math.max(1, r / 4), al(0xFFFFFF, 170));
		}
		g.fillGradient(0, h - 40, w, h, 0, 0x80000000);
	}

	// ------------------------------------------------------------------ логотип вместо заголовка

	/** Рисует текст с масштабом и контуром; (x, y) — левый верхний угол в «экранных» пикселях. */
	private static void big(GuiGraphics g, Font font, String s, float x, float y, float sc, int fill, int outline, float ow) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(sc, sc);
		if (outline != 0) {
			float[][] o = {{-ow, 0}, {ow, 0}, {0, -ow}, {0, ow}, {-ow, -ow}, {ow, ow}, {-ow, ow}, {ow, -ow}};
			for (float[] d : o) {
				g.pose().pushMatrix();
				g.pose().translate(d[0], d[1]);
				g.drawString(font, s, 0, 0, outline, false);
				g.pose().popMatrix();
			}
		}
		g.drawString(font, s, 0, 0, fill, false);
		g.pose().popMatrix();
	}

	private static void diamondF(GuiGraphics g, int cx, int cy, int r, int col) {
		for (int i = -r; i <= r; i++) {
			int half = r - Math.abs(i);
			g.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, col);
		}
	}

	/** Логотип игры вместо заголовка главного экрана. Возвращает true, если для темы он есть. */
	static boolean logo(GuiGraphics g, Font font, Theme th, int cx, int y, float t) {
		String name;
		switch (th.id()) {
			case "terraria" -> name = I18n.isEnglish() ? "Terraria" : "Террария";
			case "stardew" -> name = "Stardew Valley";
			case "undertale" -> name = "UNDERTALE";
			case "hollow" -> name = "HOLLOW KNIGHT";
			case "celeste" -> name = "CELESTE";
			case "hades" -> name = "HADES";
			case "cuphead" -> name = "CUPHEAD";
			case "dontstarve" -> name = "Don't Starve";
			case "hotline" -> name = "HOTLINE MIAMI";
			case "deadcells" -> name = "DEAD CELLS";
			case "cult" -> name = "CULT OF THE LAMB";
			case "balatro" -> name = "BALATRO";
			case "slay" -> name = "Slay the Spire";
			case "amongus" -> name = "AMONG US";
			default -> { return false; }
		}
		float sc = 2f;
		int tw = (int) (font.width(name) * sc);
		float x0 = cx - tw / 2f;
		switch (th.id()) {
			case "terraria" -> {
				big(g, font, name, x0 + 1, y + 3, sc, 0xFF5A3A18, 0xFF2A1808, 1f); // земляная тень
				big(g, font, name, x0, y + 1, sc, 0xFF3F8A1E, 0xFF10240A, 1f);
				big(g, font, name, x0, y, sc, 0xFF8EDC3E, 0, 0);
				g.fill((int) x0 + 1, y + 2, (int) x0 + tw - 1, y + 3, 0x66FFFFFF);
				for (int i = 0; i < tw; i += 3) { // трава и листья по верху букв
					int hgt = 1 + (int) (rnd(i * 1.7f, 4711.3f) * 3f);
					g.fill((int) x0 + i, y - hgt, (int) x0 + i + 2, y + 1, i % 2 == 0 ? 0xFF5AB02C : 0xFF3F8A1E);
				}
				int tx = (int) x0 - 22; // дерево слева
				g.fill(tx + 8, y + 10, tx + 12, y + 24, 0xFF6B4424);
				g.fill(tx + 8, y + 10, tx + 9, y + 24, 0xFF8A5E34);
				disc(g, tx + 10, y + 6, 8, 0xFF2E8A2A);
				disc(g, tx + 5, y + 9, 5, 0xFF2E8A2A);
				disc(g, tx + 15, y + 9, 5, 0xFF2E8A2A);
				disc(g, tx + 8, y + 3, 4, 0xFF58B83C);
			}
			case "stardew" -> {
				big(g, font, name, x0 + 1, y + 3, sc, 0xFF3A1A04, 0, 0);
				big(g, font, name, x0, y, sc, 0xFFFFC83A, 0xFF6A2E06, 1f);
				g.fillGradient((int) x0, y + 2, (int) x0 + tw, y + 9, 0x66FFFFFF, 0x00FFFFFF);
				for (int sgn = -1; sgn <= 1; sgn += 2) { // цветочки по бокам
					int fx = cx + sgn * (tw / 2 + 12);
					g.fill(fx - 1, y + 4, fx + 2, y + 7, 0xFFFFE860);
					g.fill(fx - 3, y + 5, fx - 1, y + 6, 0xFFFF8AB0);
					g.fill(fx + 2, y + 5, fx + 4, y + 6, 0xFFFF8AB0);
					g.fill(fx, y + 2, fx + 1, y + 4, 0xFFFF8AB0);
					g.fill(fx, y + 7, fx + 1, y + 9, 0xFFFF8AB0);
					g.fill(fx, y + 9, fx + 1, y + 15, 0xFF3E9A2A);
				}
			}
			case "undertale" -> {
				big(g, font, name, x0, y + 1, sc, 0xFFFFFFFF, 0, 0);
				Indie.heart(g, (int) x0 - 14, y + 5, 0xFFFF0000);
			}
			case "hollow" -> {
				big(g, font, name, x0, y - 1, sc, 0xFFFFFFFF, al(0xFFFFFF, 50), 0.6f);
				int half = tw / 2 + 6, yy = y + 18, c = 0xDDFFFFFF;
				g.fill(cx - half, yy, cx - 14, yy + 1, c);
				g.fill(cx + 14, yy, cx + half, yy + 1, c);
				g.fill(cx - half + 4, yy + 2, cx - 18, yy + 3, al(0xFFFFFF, 120));
				g.fill(cx + 18, yy + 2, cx + half - 4, yy + 3, al(0xFFFFFF, 120));
				for (int sgn = -1; sgn <= 1; sgn += 2) { // завитки на концах
					int ex = cx + sgn * (half + 2);
					g.fill(ex - 1, yy - 2, ex + 1, yy + 4, c);
					g.fill(ex + sgn * 2, yy - 3, ex + sgn * 4, yy - 2, c);
					g.fill(ex + sgn * 2, yy + 4, ex + sgn * 4, yy + 5, c);
					diamondF(g, ex + sgn * 6, yy, 2, c);
				}
				diamondF(g, cx, yy, 5, 0xFFFFFFFF); // жук по центру: ромб с чертами
				g.fill(cx - 1, yy - 3, cx + 1, yy + 4, 0xFF303234);
				g.fill(cx - 3, yy, cx + 4, yy + 1, 0xFF303234);
				diamondF(g, cx - 12, yy, 2, c);
				diamondF(g, cx + 12, yy, 2, c);
			}
			case "celeste" -> {
				int sp = 2; // разрядка букв
				int x = (int) (cx - (tw + (name.length() - 1) * sp) / 2f) + 10;
				for (int i = 0; i < name.length(); i++) {
					String ch = String.valueOf(name.charAt(i));
					big(g, font, ch, x + 0.5f, y + 1, sc, 0xFF1E2A68, 0xFF1E2A68, 0.5f);
					big(g, font, ch, x, y, sc, 0xFFFFFFFF, al(0x9AB8FF, 150), 0.8f);
					x += (int) (font.width(ch) * sc) + sp;
				}
				int mx = (int) (cx - (tw + (name.length() - 1) * sp) / 2f) - 14;
				for (int r = 0; r < 20; r++) { // ледяная вершина с гранями
					int half = r * 2 / 3;
					g.fill(mx + 9 - half, y + 1 + r, mx + 9, y + 2 + r, mix(0xFF8AD0F8, 0xFFB8A8FF, r / 20f));
					g.fill(mx + 9, y + 1 + r, mx + 9 + half + 1, y + 2 + r, mix(0xFFFFFFFF, 0xFFD8C8FF, r / 20f));
				}
				for (int r = 8; r < 20; r++) g.fill(mx + 15, y + 2 + r, mx + 17 + (r - 8) / 3, y + 3 + r, mix(0xFF9ED0F4, 0xFFC0B0FF, r / 20f));
			}
			case "hades" -> {
				big(g, font, name, x0 + 2, y + 2, sc, 0xFF4A0C0C, 0, 0);
				big(g, font, name, x0 + 1, y + 1, sc, 0xFFB02A22, 0, 0);
				big(g, font, name, x0, y, sc, 0xFFF4E8D0, 0xFF1A0A0A, 0.5f);
				int yy = y + 19;
				g.fill(cx - tw / 2 - 8, yy, cx + tw / 2 + 8, yy + 1, al(0xD8A83A, 200));
				diamondF(g, cx, yy, 3, 0xFFD8A83A);
				for (int sgn = -1; sgn <= 1; sgn += 2) { // лавровая ветвь по бокам
					for (int i = 0; i < 6; i++) {
						int lx = cx + sgn * (tw / 2 + 12 + i * 3), ly = y + 14 - i * 2;
						g.fill(lx, ly, lx + 3, ly + 2, 0xFFD8A83A);
						g.fill(lx + 1, ly + 2, lx + 3, ly + 4, 0xFFB8862A);
					}
				}
			}
			case "cuphead" -> {
				int x = (int) x0;
				for (int i = 0; i < 12; i++) { // лучи за буквами
					int lx = cx + (int) (Math.cos(i * 0.52f + t * 0.4f) * (tw / 2 + 8)), ly = y + 8 + (int) (Math.sin(i * 0.52f + t * 0.4f) * 12);
					g.fill(lx, ly, lx + 2, ly + 2, al(0xF5E6C0, 90));
				}
				for (int i = 0; i < name.length(); i++) {
					String ch = String.valueOf(name.charAt(i));
					float bob = (float) Math.sin(t * 5f + i * 0.9f) * 1.2f;
					big(g, font, ch, x + 2f, y + bob + 2f, sc, 0xFFB02A1A, 0xFFB02A1A, 0.5f);
					big(g, font, ch, x, y + bob, sc, 0xFFF5E6C0, 0xFF000000, 1f);
					x += (int) (font.width(ch) * sc);
				}
			}
			case "dontstarve" -> {
				big(g, font, name, x0 + 1, y + 2, sc, 0xFF0A0604, 0, 0);
				big(g, font, name, x0, y, sc, 0xFFEAD8B0, 0xFF2A1A0A, 1f);
				g.fill((int) x0, y + 18, (int) x0 + tw, y + 19, al(0xEAD8B0, 160));
				for (int i = 0; i < 5; i++) g.fill((int) x0 + 6 + i * (tw - 12) / 4, y + 17, (int) x0 + 8 + i * (tw - 12) / 4, y + 21, al(0xEAD8B0, 200));
			}
			case "hotline" -> {
				float jx = (System.nanoTime() / 90_000_000L % 7 == 0) ? 2f : 0f;
				big(g, font, name, x0 - 2 + jx, y + 1, sc, 0xFF00F0FF, 0, 0);
				big(g, font, name, x0 + 2 - jx, y - 1, sc, 0xFFFFE04A, 0, 0);
				big(g, font, name, x0, y, sc, 0xFFFF3EA5, 0xFF1A0630, 1f);
			}
			case "deadcells" -> {
				big(g, font, name, x0, y + 2, sc, 0xFF0A0C14, 0, 0);
				big(g, font, name, x0, y, sc, 0xFFE8E4DC, 0xFF1A1E2E, 1f);
				g.fill((int) x0, y + 18, (int) x0 + tw / 3, y + 20, 0xFFFF8A2A);
				g.fill((int) x0 + tw - tw / 3, y + 18, (int) x0 + tw, y + 20, 0xFFFF8A2A);
				for (int i = 0; i < 4; i++) g.fill((int) x0 + tw / 3 + 4 + i * 5, y + 17 + i % 2, (int) x0 + tw / 3 + 6 + i * 5, y + 20, 0xFF4A5470);
			}
			case "cult" -> {
				big(g, font, name, x0 + 1, y + 2, sc, 0xFF1A0408, 0, 0);
				big(g, font, name, x0, y, sc, 0xFFF4E4D0, 0xFFB02A30, 1f);
				int sx = cx; // знак над названием
				for (int i = 0; i < 4; i++) g.fill(sx - 2 + i, y - 4 + i, sx + 3 - i, y - 3 + i, 0xFFE83A3A);
				g.fill((int) x0 - 8, y + 8, (int) x0 - 3, y + 9, 0xFFE83A3A);
				g.fill((int) x0 + tw + 3, y + 8, (int) x0 + tw + 8, y + 9, 0xFFE83A3A);
			}
			case "balatro" -> {
				int x = (int) x0;
				for (int i = 0; i < name.length(); i++) {
					String ch = String.valueOf(name.charAt(i));
					float bob = (float) Math.sin(t * 3.2f + i * 0.8f) * 1.6f;
					big(g, font, ch, x + 1.5f, y + bob + 2.5f, sc, 0xFF1A0A0A, 0xFF1A0A0A, 0.5f);
					big(g, font, ch, x, y + bob, sc, i % 2 == 0 ? 0xFFE84A3A : 0xFF3AC8B0, 0xFFF4F0E8, 1f);
					x += (int) (font.width(ch) * sc);
				}
			}
			case "slay" -> {
				big(g, font, name, x0 + 1, y + 2, sc, 0xFF0A0A0E, 0, 0);
				big(g, font, name, x0, y, sc, 0xFFF0D890, 0xFF3A1A0A, 1f);
				int yy = y + 19;
				g.fill((int) x0 + 4, yy, (int) x0 + tw - 4, yy + 1, al(0xB02A2A, 220));
				diamondF(g, cx, yy, 3, 0xFFB02A2A);
				diamondF(g, cx, yy, 1, 0xFFF0D890);
			}
			case "amongus" -> {
				big(g, font, name, x0 + 1, y + 3, sc, 0xFF1A0404, 0, 0);
				big(g, font, name, x0, y, sc, 0xFFC51111, 0xFFF4F4F4, 1f);
				for (int i = 0; i < 6; i++) g.fill(cx - tw / 2 - 14 + (i % 2) * 3, y + 2 + i * 3, cx - tw / 2 - 12 + (i % 2) * 3, y + 4 + i * 3, al(0xFFFFFF, 180));
			}
			default -> { }
		}
		return true;
	}

	// ------------------------------------------------------------------ текстовые «кнопки» главного меню

	private static void textOut(GuiGraphics g, Font font, String s, int x, int y, int fill, int outline) {
		if (outline != 0) {
			g.drawString(font, s, x - 1, y, outline, false);
			g.drawString(font, s, x + 1, y, outline, false);
			g.drawString(font, s, x, y - 1, outline, false);
			g.drawString(font, s, x, y + 1, outline, false);
			g.drawString(font, s, x - 1, y + 1, outline, false);
			g.drawString(font, s, x + 1, y + 1, outline, false);
		}
		g.drawString(font, s, x, y, fill, false);
	}

	private static void gear(GuiGraphics g, int x, int y, int col) {
		g.fill(x + 2, y, x + 6, y + 8, col);
		g.fill(x, y + 2, x + 8, y + 6, col);
		g.fill(x + 1, y + 1, x + 7, y + 7, col);
		g.fill(x + 3, y + 3, x + 5, y + 5, 0xFF1C2250);
	}

	private static void back(GuiGraphics g, int x, int y, int col) {
		g.fill(x + 1, y + 2, x + 6, y + 3, col);
		g.fill(x, y + 1, x + 1, y + 4, col);
		g.fill(x + 5, y + 3, x + 7, y + 7, col);
		g.fill(x + 2, y + 6, x + 6, y + 8, col);
	}

	/** Кнопка в виде слова (как в главных меню игр): без плитки, с эффектом наведения этой игры. */
	static void menuText(GuiGraphics g, Font font, int x, int y, int w, int h, String label, float hover, boolean active, int kind, Theme th) {
		int tw = font.width(label), cx = x + w / 2, ty = y + (h - 8) / 2, tx = cx - tw / 2;
		switch (th.id()) {
			case "terraria" -> {
				int c = !active ? 0xFF7A8490 : mix(0xFFCFD8E4, 0xFFFFE04A, hover);
				if (hover > 0.05f && active) {
					float sc = 1f + 0.15f * hover;
					g.pose().pushMatrix();
					g.pose().translate(cx, y + h / 2f);
					g.pose().scale(sc, sc);
					textOut(g, font, label, -tw / 2, -4, c, 0xFF08181C);
					g.pose().popMatrix();
				} else textOut(g, font, label, tx, ty, c, 0xFF08181C);
			}
			case "hollow" -> {
				int c = !active ? 0xFF5A5E62 : mix(0xFFBFC2C4, 0xFFFFFFFF, hover);
				g.drawString(font, label, tx, ty, c, false);
				if (active && hover > 0.15f) { // «◆ ◀ Текст ▶ ◆»
					int a = (int) (255 * hover), pad = 8 + (int) (3 * (1f - hover));
					int lx = tx - pad, rx = tx + tw + pad;
					for (int i = 0; i < 4; i++) {
						g.fill(lx - i, ty + 2 + i / 2, lx - i + 1, ty + 6 - i / 2, al(0xFFFFFF, a));
						g.fill(rx + i, ty + 2 + i / 2, rx + i + 1, ty + 6 - i / 2, al(0xFFFFFF, a));
					}
					diamondF(g, lx - 9, ty + 4, 2, al(0xFFFFFF, a));
					diamondF(g, rx + 9, ty + 4, 2, al(0xFFFFFF, a));
				}
			}
			case "celeste" -> {
				int c = !active ? 0xFF8890B8 : mix(0xFFFFFFFF, 0xFF8AF07A, hover);
				int ix = tx - 14;
				if (kind == 1) Indie.heart(g, ix, ty, 0xFFE8384A);
				else if (kind == 2) back(g, ix, ty, 0xFFB8C8FF);
				else gear(g, ix, ty, 0xFF9AA8E8);
				textOut(g, font, label, tx, ty, c, 0xFF1A2260);
				g.drawString(font, label, tx + 1, ty, c, false); // «жирность» шрифта
			}
			case "hades" -> {
				int c = !active ? 0xFF5A5448 : mix(0xFFE6DECE, 0xFFFFFFFF, hover);
				g.drawString(font, label, tx, ty, c, false);
				if (active && hover > 0.05f) {
					g.fill(tx, ty + 10, tx + (int) (tw * hover), ty + 11, al(0xE8B84A, 220));
					diamondF(g, tx - 7, ty + 4, 2, al(0xE8B84A, 255 * hover));
				}
			}
			case "cuphead" -> {
				int c = !active ? 0xFF4A463E : mix(0xFF8C8272, 0xFFFFFFFF, hover);
				int jit = hover > 0.5f ? (int) (System.nanoTime() / 83_000_000L % 2) : 0; // дрожание плёнки
				g.drawString(font, label, tx + jit, ty, c, false);
				if (hover > 0.5f) g.drawString(font, label, tx + jit + 1, ty, al(0xFFFFFF, 130), false);
			}
			case "dontstarve" -> {
				int c = !active ? 0xFF5A5040 : mix(0xFFB8A888, 0xFFFFF0D0, hover);
				textOut(g, font, label, tx, ty, c, 0xFF0A0604);
				if (active && hover > 0.1f) {
					g.fill(tx - 4, ty + 10, tx + (int) ((tw + 8) * hover), ty + 11, al(0xEAD8B0, 190));
					g.fill(tx - 4, ty + 12, tx + (int) ((tw + 8) * hover * 0.7f), ty + 13, al(0xEAD8B0, 100));
				}
			}
			case "hotline" -> {
				int jit = hover > 0.5f ? (int) (System.nanoTime() / 70_000_000L % 3) - 1 : 0;
				int c = !active ? 0xFF6A4A8A : mix(0xFFFF3EA5, 0xFF00F0FF, hover);
				g.drawString(font, label, tx + 1 + jit, ty + 1, 0xFF1A0630, false);
				g.drawString(font, label, tx + jit, ty, c, false);
				if (hover > 0.5f) g.drawString(font, label, tx - 1, ty, al(0xFF3EA5, 140), false);
			}
			case "deadcells" -> {
				int c = !active ? 0xFF50546A : mix(0xFFE8E4DC, 0xFFFF8A2A, hover);
				textOut(g, font, label, tx, ty, c, 0xFF0A0C14);
				if (active && hover > 0.1f) for (int i = 0; i < 4; i++) g.fill(tx - 10 + i, ty + 1 + i, tx - 9 + i, ty + 8 - i, al(0xFF8A2A, 255 * hover));
			}
			case "cult" -> {
				int c = !active ? 0xFF5A3A38 : mix(0xFFF4E4D0, 0xFFE83A3A, hover);
				textOut(g, font, label, tx, ty, c, 0xFF1A0408);
				if (active && hover > 0.1f) {
					int a = (int) (255 * hover);
					for (int sgn = -1; sgn <= 1; sgn += 2) {
						int sx = sgn < 0 ? tx - 9 : tx + tw + 8;
						for (int i = 0; i < 3; i++) g.fill(sx - 3 + i, ty + 6 - i * 2, sx + 4 - i, ty + 7 - i * 2, al(0xE83A3A, a));
					}
				}
			}
			case "slay" -> {
				int c = !active ? 0xFF4A463E : mix(0xFFC8C0B0, 0xFFFFD060, hover);
				textOut(g, font, label, tx, ty, c, 0xFF0A0A0E);
				if (active && hover > 0.1f) {
					int a = (int) (255 * hover);
					diamondF(g, tx - 8, ty + 4, 2, al(0xB02A2A, a));
					diamondF(g, tx + tw + 8, ty + 4, 2, al(0xB02A2A, a));
				}
			}
			default -> g.drawString(font, label, tx, ty, th.text(), false);
		}
	}

	// ------------------------------------------------------------------ мелкие детали форм

	/** Сердечко Undertale 7x6. */
	static void heart(GuiGraphics g, int x, int y, int col) {
		g.fill(x + 1, y, x + 3, y + 1, col);
		g.fill(x + 4, y, x + 6, y + 1, col);
		g.fill(x, y + 1, x + 7, y + 3, col);
		g.fill(x + 1, y + 3, x + 6, y + 4, col);
		g.fill(x + 2, y + 4, x + 5, y + 5, col);
		g.fill(x + 3, y + 5, x + 4, y + 6, col);
	}

	/** Полоска греческого меандра (используется в украшениях Hades). */
	static void meander(GuiGraphics g, int x0, int y, int w, int col) {
		for (int x = x0; x < x0 + w - 11; x += 12) {
			g.fill(x, y, x + 10, y + 2, col);
			g.fill(x, y, x + 2, y + 8, col);
			g.fill(x + 2, y + 6, x + 8, y + 8, col);
			g.fill(x + 6, y + 3, x + 8, y + 6, col);
			g.fill(x + 10, y + 4, x + 12, y + 8, col);
		}
	}
}
