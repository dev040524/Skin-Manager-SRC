package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.Input;
import dev.skinlibrary.Sfx;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Пасхалка: автомат с красным рычагом. Появляется, если очень быстро менять тему.
 * Только внутриигровые очки (без реальных денег). Символы: вишня, лимон, колокольчик, звезда, алмаз, семёрка.
 */
public class SlotMachineScreen extends ThemedScreen {
	/** Пиксельные символы 12x12 (вместо эмодзи, которых нет в шрифте игры). */
	private static final String[][] ART = {
		{
			"............",
			"............",
			"......bb....",
			".....b..b...",
			"....b....b..",
			"...b.....bb.",
			"..rrr...rrr.",
			".rwrrR.rwrrR",
			".rrrrR.rrrrR",
			".rrrrR.rrrrR",
			"..RRR...RRR.",
			"............"
		},
		{
			"............",
			"......gg....",
			"....yyyyyy..",
			"..yyyyyyyyy.",
			".ywyyyyyyyyY",
			"yywyyyyyyyyY",
			"yyyyyyyyyyYY",
			".yyyyyyyyyYY",
			"..yyyyyyyYY.",
			"....YYYYYY..",
			"............",
			"............"
		},
		{
			".....yy.....",
			"....yyyy....",
			"...yyyyyy...",
			"..yywyyyyy..",
			"..ywyyyyyy..",
			"..yyyyyyyy..",
			".yyyyyyyyyy.",
			".yyyyyyyyyY.",
			"yyyyyyyyyyYY",
			"YYYYYYYYYYYY",
			".....rr.....",
			"............"
		},
		{
			".....yy.....",
			".....yy.....",
			"....yyyy....",
			"yyyyyyyyyyyy",
			".yywyyyyyyY.",
			"..yyyyyyYY..",
			"...yyyyYY...",
			"..yyyyyyYY..",
			"..yyY..yyY..",
			".yyY....yyY.",
			"............",
			"............"
		},
		{
			"............",
			"..cccccccc..",
			".cwccccccccC",
			"ccccccccccCC",
			".cccccccccC.",
			"..ccccccCC..",
			"...cccccC...",
			"....ccCC....",
			".....cC.....",
			"............",
			"............",
			"............"
		},
		{
			"............",
			".rrrrrrrrrr.",
			".rrrrrrrrrr.",
			"........rrR.",
			".......rrR..",
			"......rrR...",
			".....rrR....",
			"....rrR.....",
			"...rrR......",
			"...rrR......",
			"...rR.......",
			"............"
		}
	};
	private static final String[] NAMES = {"Вишня", "Лимон", "Колокольчик", "Звезда", "Алмаз", "Семёрка"};
	private static final int[] WEIGHTS = {30, 25, 18, 14, 8, 5};
	private static final int[] MULT3 = {4, 6, 10, 15, 25, 50};   // выплата за три одинаковых (× ставка)
	private static final int[] STRIP = {0, 3, 1, 5, 2, 4};        // порядок символов на барабане
	private static final int[] BETS = {5, 10, 25, 50, 100};

	private static final int MW = 212, MH = 246;
	private static final int RX0 = 24, RW = 40, RGAP = 4, WY0 = 68, WY1 = 158, SH = 30;
	private static final float LX = 190f, LY = 130f, LL = 56f;

	private final Screen parent;
	private Button spinBtn, minusBtn, plusBtn;

	private int betIdx = 1;
	private boolean spinning;
	private final float[] pos = new float[3], startPos = new float[3], target = new float[3], dur = new float[3];
	private final boolean[] stopped = new boolean[3];
	private final boolean[] winner = new boolean[3];
	private int[] result = {0, 0, 0};
	private long spinStart, lastTick;
	private int stoppedCount;

	// рычаг
	private float lp, lv;
	private int lmode; // 0 покой, 1 тянут мышью, 2 автоопускание, 3 возврат
	private boolean leverFired;

	// эффекты
	private long winAt = -1L;
	private int winTier, lastPayout;
	private String msg = I18n.t("Тяните за рычаг!");
	private int msgColor = 0xFFA0FFA0;
	private final List<float[]> particles = new ArrayList<>();
	private boolean openFx;
	private boolean wasDown;
	private long lastNano = System.nanoTime();

	// раскладка кадра
	private float sc = 1f, ox, oy;

	public SlotMachineScreen(Screen parent) {
		super(Component.literal(I18n.t("Автомат")));
		this.parent = parent;
	}

	private static int color(char c) {
		return switch (c) {
			case 'g' -> 0xFF43A047;
			case 'r' -> 0xFFE53935;
			case 'R' -> 0xFF9E1B17;
			case 'w' -> 0xFFFFFFFF;
			case 'y' -> 0xFFFFD83B;
			case 'Y' -> 0xFFC79A1B;
			case 'c' -> 0xFF4DD0E1;
			case 'C' -> 0xFF1E8FA3;
			case 'b' -> 0xFF8B5A2B;
			default -> 0xFFFF00FF;
		};
	}

	private int bet() {
		return BETS[betIdx];
	}

	// ------------------------------------------------------------------ интерфейс

	@Override
	protected void init() {
		addBackground();
		this.addRenderableOnly((g, mx, my, d) -> drawAll(g, mx, my));

		int x0 = (this.width - 320) / 2, y = this.height - 26;
		minusBtn = fancy(() -> "-", x0, y, 30, 20, b -> {
			if (!spinning && betIdx > 0) betIdx--;
		}, I18n.t("Меньше ставка"), 0);
		fancy(() -> I18n.t("Ставка: ") + bet(), x0 + 34, y, 84, 20, b -> {
			if (!spinning) betIdx = (betIdx + 1) % BETS.length;
		}, I18n.t("Сменить ставку"), 0);
		plusBtn = fancy(() -> "+", x0 + 122, y, 30, 20, b -> {
			if (!spinning && betIdx < BETS.length - 1) betIdx++;
		}, I18n.t("Больше ставка"), 0);
		spinBtn = fancy(() -> SkinStore.points() < bet() ? I18n.t("Взять 100 очков") : (spinning ? "..." : I18n.t("Крутить")),
				x0 + 156, y, 90, 20, b -> pressSpin(), I18n.t("Или потяните за красный рычаг"), 1);
		fancy(() -> I18n.t("Выход"), x0 + 250, y, 70, 20, b -> this.onClose(), null, 0);
	}

	private void pressSpin() {
		if (spinning) return;
		if (SkinStore.points() < bet()) { // очки бесплатные: можно взять ещё
			SkinStore.setPoints(SkinStore.points() + 100);
			msg = I18n.t("+100 очков! Играйте на здоровье");
			msgColor = 0xFFFFE082;
			Sfx.casino(3);
			return;
		}
		lmode = 2; // рычаг опускается, затем запускает барабаны
		leverFired = false;
	}

	// ------------------------------------------------------------------ логика

	private int roll() {
		int total = 0;
		for (int w : WEIGHTS) total += w;
		int r = ThreadLocalRandom.current().nextInt(total);
		for (int i = 0; i < WEIGHTS.length; i++) {
			r -= WEIGHTS[i];
			if (r < 0) return i;
		}
		return 0;
	}

	private void startSpin() {
		if (spinning || SkinStore.points() < bet()) return;
		SkinStore.setPoints(SkinStore.points() - bet());
		spinning = true;
		stoppedCount = 0;
		winAt = -1L;
		winTier = 0;
		spinStart = System.nanoTime();
		result = new int[]{roll(), roll(), roll()};
		for (int i = 0; i < 3; i++) {
			stopped[i] = false;
			winner[i] = false;
			startPos[i] = pos[i];
			int k = (int) Math.floor(pos[i]) + 16 + i * 8;
			while (STRIP[Math.floorMod(k, 6)] != result[i]) k++;
			target[i] = k;
			dur[i] = 1.4f + 0.75f * i;
		}
		msg = I18n.t("Крутим...");
		msgColor = 0xFFFFFFFF;
		Sfx.casino(0);
	}

	private void settle() {
		spinning = false;
		int a = result[0], b = result[1], c = result[2];
		int mult = 0;
		if (a == b && b == c) {
			mult = MULT3[a];
			winner[0] = winner[1] = winner[2] = true;
		} else {
			int ch = 0;
			for (int r : result) if (r == 0) ch++;
			if (ch >= 2) {
				mult = 2;
				for (int i = 0; i < 3; i++) winner[i] = result[i] == 0;
			}
		}
		int payout = bet() * mult;
		lastPayout = payout;
		if (payout > 0) {
			SkinStore.setPoints(SkinStore.points() + payout);
			winTier = (a == b && b == c && a == 5) || mult >= 25 ? 2 : 1;
			winAt = System.nanoTime();
			msg = (winTier == 2 ? I18n.t("ДЖЕКПОТ! +") : I18n.t("ВЫИГРЫШ! +")) + payout;
			msgColor = 0xFFFFE082;
			burst(winTier == 2 ? 110 : 36, winTier == 2 ? 420f : 280f);
			Sfx.casino(winTier == 2 ? 4 : 3);
		} else {
			msg = I18n.t("Не повезло... Ещё раз?");
			msgColor = 0xFFFF9A9A;
			Sfx.casino(5);
		}
	}

	private void burst(int n, float speed) {
		float cx = ox + MW * sc / 2f, cy = oy + 113 * sc;
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < n; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			float sp = (float) (speed * (0.35 + r.nextDouble() * 0.65));
			particles.add(new float[]{cx, cy, (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp - 120f,
					1.4f + r.nextFloat() * 1.2f, r.nextInt(3)});
		}
	}

	private void update(float dt, int mx, int my) {
		boolean down = Input.leftDown();
		float lx = (mx - ox) / sc, ly = (my - oy - yOff()) / sc;

		// рычаг
		float kx = LX + 18f * (float) Math.sin(lp * Math.toRadians(160));
		float ky = LY - LL * (float) Math.cos(lp * Math.toRadians(160));
		boolean overKnob = Math.hypot(lx - kx, ly - ky) < 12;
		if (down && !wasDown && overKnob && lmode == 0) {
			lmode = 1;
			leverFired = false;
		}
		switch (lmode) {
			case 1 -> {
				if (down) {
					float cos = Math.max(-1f, Math.min(1f, (LY - ly) / LL));
					lp = Math.max(0f, Math.min(1f, (float) (Math.acos(cos) / Math.toRadians(160))));
					if (lp > 0.85f && !leverFired) {
						leverFired = true;
						startSpin();
						lmode = 3;
					}
				} else { // отпустили: короткий клик — опускаем сами
					if (lp < 0.12f) lmode = 2;
					else lmode = 3;
					leverFired = false;
				}
			}
			case 2 -> {
				lp += dt / 0.18f;
				if (lp >= 1f) {
					lp = 1f;
					if (!leverFired) {
						leverFired = true;
						if (!spinning && SkinStore.points() >= bet()) startSpin();
						else if (SkinStore.points() < bet()) {
							msg = I18n.t("Мало очков: нажмите «Взять 100 очков»");
							msgColor = 0xFFFF9A9A;
						}
					}
					lmode = 3;
					lv = 0f;
				}
			}
			case 3 -> {
				float acc = 140f * (0f - lp) - 12f * lv; // пружина: возвращается с небольшим «отскоком»
				lv += acc * dt;
				lp += lv * dt;
				if (Math.abs(lp) < 0.003f && Math.abs(lv) < 0.03f) {
					lp = 0f;
					lv = 0f;
					lmode = 0;
				}
				lp = Math.max(lp, -0.06f);
			}
			default -> { }
		}

		// барабаны
		if (spinning) {
			float e = (System.nanoTime() - spinStart) / 1e9f;
			int prevFloor = (int) Math.floor(pos[0]);
			for (int i = 0; i < 3; i++) {
				if (stopped[i]) continue;
				float u = Math.min(1f, e / dur[i]);
				float eased = 1f - (1f - u) * (1f - u) * (1f - u);
				pos[i] = startPos[i] + (target[i] - startPos[i]) * eased;
				if (u >= 1f) {
					pos[i] = target[i];
					stopped[i] = true;
					stoppedCount++;
					Sfx.casino(2);
				}
			}
			long now = System.nanoTime();
			if ((int) Math.floor(pos[0]) != prevFloor && now - lastTick > 70_000_000L) {
				lastTick = now;
				Sfx.casino(1);
			}
			if (stoppedCount == 3) settle();
		}

		// частицы
		for (int i = particles.size() - 1; i >= 0; i--) {
			float[] p = particles.get(i);
			p[3] += 700f * dt;
			p[0] += p[2] * dt;
			p[1] += p[3] * dt;
			p[4] -= dt;
			if (p[4] <= 0 || p[1] > this.height + 10) particles.remove(i);
		}
		wasDown = down;
	}

	private float openEase() {
		float t = Math.min(1f, (System.nanoTime() - openTime) / 1e9f / 0.85f);
		float c1 = 1.70158f, c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	private float yOff() {
		return (1f - openEase()) * this.height;
	}

	// ------------------------------------------------------------------ отрисовка

	private void drawSymbol(GuiGraphics g, int idx, int x, int y, int cell, int clipTop, int clipBottom, int alpha) {
		String[] rows = ART[idx];
		for (int r = 0; r < 12; r++) {
			for (int c = 0; c < 12; c++) {
				char ch = rows[r].charAt(c);
				if (ch == '.') continue;
				int y1 = y + r * cell, y2 = y1 + cell;
				if (y2 <= clipTop || y1 >= clipBottom) continue;
				y1 = Math.max(y1, clipTop);
				y2 = Math.min(y2, clipBottom);
				g.fill(x + c * cell, y1, x + c * cell + cell, y2, (alpha << 24) | (color(ch) & 0xFFFFFF));
			}
		}
	}

	private void drawAll(GuiGraphics g, int mx, int my) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNano) / 1e9f);
		lastNano = now;
		float t = (now - openTime) / 1e9f;

		sc = Math.min(1f, (this.height - 40f) / MH);
		boolean pay = this.width >= 380;
		ox = (this.width - MW * sc) / 2f + (pay ? 40 : 0);
		oy = 8 + (this.height - 40 - MH * sc) / 2f;
		update(dt, mx, my);

		if (!openFx && t >= 0.8f) {
			openFx = true;
			burst(28, 220f);
			Sfx.casino(3);
		}

		float since = winAt < 0 ? 99f : (now - winAt) / 1e9f;
		float shakeX = 0, shakeY = 0;
		if (winTier == 2 && since < 0.9f) {
			float a = 3f * (1f - since / 0.9f);
			shakeX = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 2 * a;
			shakeY = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 2 * a;
		}

		if (pay) drawPaytable(g, (int) (ox - 118 * 1f), (int) oy + 22);

		g.pose().pushMatrix();
		g.pose().translate(ox + shakeX, oy + yOff() + shakeY);
		g.pose().scale(sc, sc);
		drawMachine(g, t, since);
		g.pose().popMatrix();

		// частицы-монеты
		for (float[] p : particles) {
			float a = Math.min(1f, p[4] / 0.5f);
			int col = p[5] == 0 ? 0xFFFFD83B : p[5] == 1 ? 0xFFFFFFFF : 0xFFFF9800;
			int size = p[5] == 1 ? 2 : 3;
			g.fill((int) p[0], (int) p[1], (int) p[0] + size, (int) p[1] + size, Ui.alpha(col, (int) (255 * a)));
		}

		// баннер выигрыша и вспышка джекпота
		if (winTier > 0 && since < 3.2f) {
			float pulse = 1f + 0.12f * (float) Math.sin(since * 10f);
			int col = winTier == 2
					? 0xFF000000 | java.awt.Color.HSBtoRGB((since * 1.5f) % 1f, 0.8f, 1f)
					: 0xFFFFE082;
			String txt = (winTier == 2 ? I18n.t("ДЖЕКПОТ!  ") : I18n.t("ВЫИГРЫШ!  ")) + "+" + lastPayout;
			g.pose().pushMatrix();
			g.pose().translate(this.width / 2f, 14f);
			g.pose().scale(2f * pulse, 2f * pulse);
			g.drawCenteredString(this.font, txt, 0, 0, col);
			g.pose().popMatrix();
			if (winTier == 2 && since < 0.5f) g.fill(0, 0, this.width, this.height, Ui.alpha(0xFFFFFF, (int) (150 * (1f - since / 0.5f))));
		}

		// кнопки: недоступны во время вращения
		boolean idle = !spinning && lmode == 0;
		if (spinBtn != null) spinBtn.active = !spinning;
		if (minusBtn != null) minusBtn.active = idle && betIdx > 0;
		if (plusBtn != null) plusBtn.active = idle && betIdx < BETS.length - 1;
	}

	private void drawPaytable(GuiGraphics g, int x, int y) {
		Theme th = th();
		Ui.panel(g, x - 6, y - 8, 112, 118, th);
		g.drawString(this.font, I18n.t("Выплаты"), x, y - 4, th.sub());
		for (int i = 5; i >= 0; i--) {
			int row = 5 - i;
			int yy = y + 8 + row * 15;
			for (int k = 0; k < 3; k++) drawSymbol(g, i, x + k * 11, yy - 1, 1, 0, 9999, 255);
			g.drawString(this.font, "x" + MULT3[i], x + 40, yy + 2, 0xFFFFE082);
		}
		drawSymbol(g, 0, x, y + 98, 1, 0, 9999, 255);
		drawSymbol(g, 0, x + 11, y + 98, 1, 0, 9999, 255);
		g.drawString(this.font, "x2", x + 40, y + 100, 0xFFFFE082);
	}

	private void drawMachine(GuiGraphics g, float t, float since) {
		boolean fast = spinning;
		// корпус
		g.fill(8, MH - 4, 184, MH + 2, 0x66000000);
		Ui.roundPanel(g, 0, 0, 176, MH - 2, 0xFFC62828, 0xFF7F1010);
		Ui.roundOutline(g, 0, 0, 176, MH - 2, 0xFFFFC107);
		Ui.outline(g, 5, 5, 166, MH - 12, 0x66FFE082);

		// табло с названием и лампочками
		Ui.roundPanel(g, 14, 10, 148, 30, 0xFF2A0A0A, 0xFF120404);
		g.pose().pushMatrix();
		g.pose().translate(88f, 20f);
		g.pose().scale(1.4f, 1.4f);
		g.drawCenteredString(this.font, "SKIN SLOTS", 0, 0, 0xFFFFD83B);
		g.pose().popMatrix();
		for (int i = 0; i < 15; i++) {
			float speed = winTier > 0 && since < 3f ? 12f : fast ? 7f : 1.5f;
			boolean on = ((int) (t * speed) + i) % 2 == 0;
			int col = on ? (i % 2 == 0 ? 0xFFFFE082 : 0xFFFF5252) : 0xFF4A2A10;
			g.fill(14 + i * 10, 46, 14 + i * 10 + 6, 52, col);
			if (on) g.fill(13 + i * 10, 45, 14 + i * 10 + 7, 53, Ui.alpha(col, 50));
		}

		// окно барабанов
		Ui.roundPanel(g, 18, 62, 140, 104, 0xFF1A1A1A, 0xFF080808);
		for (int i = 0; i < 3; i++) {
			int rx = RX0 + i * (RW + RGAP);
			g.fillGradient(rx, WY0, rx + RW, WY1, 0xFFE8E2D0, 0xFFC9C0A4);
			float p = pos[i];
			int base = (int) Math.floor(p);
			for (int k = base - 2; k <= base + 2; k++) {
				int idx = STRIP[Math.floorMod(k, 6)];
				int yc = Math.round(113 + (p - k) * SH);
				int sy = yc - 12;
				if (fast && !stopped[i]) {
					drawSymbol(g, idx, rx + 8, sy - 5, 2, WY0, WY1, 70);
					drawSymbol(g, idx, rx + 8, sy + 5, 2, WY0, WY1, 70);
				}
				drawSymbol(g, idx, rx + 8, sy, 2, WY0, WY1, fast && !stopped[i] ? 190 : 255);
			}
			g.fillGradient(rx, WY0, rx + RW, WY0 + 30, 0xB0000000, 0x00000000);
			g.fillGradient(rx, WY1 - 30, rx + RW, WY1, 0x00000000, 0xB0000000);
			if (winTier > 0 && winner[i] && since < 3f) {
				int a = (int) (120 + 120 * Math.sin(since * 14f));
				Ui.outline(g, rx - 1, WY0 - 1, RW + 2, WY1 - WY0 + 2, Ui.alpha(0xFFD83B, Math.max(40, a)));
				Ui.outline(g, rx, WY0, RW, WY1 - WY0, Ui.alpha(0xFFFFFF, Math.max(20, a / 2)));
			}
		}
		// линия выплат
		int lineA = winTier > 0 && since < 3f ? (int) (170 + 85 * Math.sin(since * 16f)) : 150;
		g.fill(20, 112, 156, 114, Ui.alpha(0xFF3030, lineA));
		g.fill(16, 109, 20, 117, 0xFFFFD83B);
		g.fill(156, 109, 160, 117, 0xFFFFD83B);

		// табло очков
		Ui.roundPanel(g, 18, 174, 140, 58, 0xFF0D2A0D, 0xFF061406);
		Ui.roundOutline(g, 18, 174, 140, 58, 0xFF2E7D32);
		g.drawString(this.font, I18n.t("ОЧКИ: ") + SkinStore.points(), 24, 180, 0xFF7CFF7C);
		g.drawString(this.font, I18n.t("СТАВКА: ") + bet(), 24, 192, 0xFFB9F6CA);
		String m = msg;
		while (this.font.width(m) > 128 && m.length() > 1) m = m.substring(0, m.length() - 1);
		g.drawString(this.font, m, 24, 210, msgColor);
		g.fill(30, MH - 10, 146, MH - 5, 0xFF1A0505); // лоток для монет

		drawLever(g);
	}

	private void drawLever(GuiGraphics g) {
		// основание рычага на боковой стенке
		Ui.roundPanel(g, 170, 112, 16, 40, 0xFFB0B4C0, 0xFF6A6E7C);
		Ui.roundOutline(g, 170, 112, 16, 40, 0xFF2A2A34);
		double phi = Math.toRadians(160) * lp;
		float kx = LX + 18f * (float) Math.sin(phi);
		float ky = LY - LL * (float) Math.cos(phi);
		// стержень
		int steps = 28;
		for (int i = 0; i <= steps; i++) {
			float f = i / (float) steps;
			int x = Math.round(LX + (kx - LX) * f), y = Math.round(LY + (ky - LY) * f);
			g.fill(x - 2, y - 2, x + 2, y + 2, i % 2 == 0 ? 0xFFD8DCE8 : 0xFF9AA0B2);
		}
		g.fill(Math.round(LX) - 4, Math.round(LY) - 4, Math.round(LX) + 4, Math.round(LY) + 4, 0xFF4A4E5C);
		// красный шар
		int r = 9;
		for (int yy = -r; yy <= r; yy++) {
			int half = (int) Math.sqrt(r * r - yy * yy);
			float shade = 1f - (yy + r) / (2f * r) * 0.5f;
			int red = (int) (0xE5 * shade), gr = (int) (0x30 * shade), bl = (int) (0x30 * shade);
			g.fill(Math.round(kx) - half, Math.round(ky) + yy, Math.round(kx) + half + 1, Math.round(ky) + yy + 1,
					0xFF000000 | (red << 16) | (gr << 8) | bl);
		}
		g.fill(Math.round(kx) - 4, Math.round(ky) - 5, Math.round(kx) - 1, Math.round(ky) - 3, 0xB0FFFFFF);
	}

	@Override
	public void onClose() {
		if (spinning) { // закрыли во время вращения — результат засчитывается сразу
			for (int i = 0; i < 3; i++) {
				pos[i] = target[i];
				stopped[i] = true;
			}
			settle();
		}
		this.minecraft.setScreen(parent);
	}
}
