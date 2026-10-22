package dev.skinlibrary.screen;

import dev.skinlibrary.Sfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Всплывающее уведомление в углу экрана (например, «КД закончился») — рисуется поверх игры. */
public final class Notify {
	private static String title = "", text = "";
	private static long startNs = -1L;

	private Notify() {}

	public static void show(String t, String msg) {
		title = t;
		text = msg;
		startNs = System.nanoTime();
		Sfx.notice();
	}

	public static void render(GuiGraphics g) {
		if (startNs < 0) return;
		float age = (System.nanoTime() - startNs) / 1e9f;
		if (age > 4.2f) {
			startNs = -1L;
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Theme th = Theme.current();
		int w = Math.max(mc.font.width(title), mc.font.width(text)) + 40, h = 30;
		int sw = mc.getWindow().getGuiScaledWidth();

		float slide = 1f; // 0 — спрятано справа, 1 — на месте
		if (!th.classic()) {
			if (age < 0.35f) {
				float u = age / 0.35f;
				slide = 1f - (1f - u) * (1f - u);
			} else if (age > 3.7f) {
				slide = 1f - (age - 3.7f) / 0.5f;
			}
		}
		slide = Math.max(0f, Math.min(1f, slide));
		int x = sw - Math.round((w + 6) * slide), y = 6;

		Ui.panel(g, x, y, w, h, th);
		g.fill(x + 3, y + 3, x + 6, y + h - 3, th.accent());
		g.pose().pushMatrix();
		g.pose().translate((float) (x + 12), (float) (y + 8));
		g.pose().scale(2f, 2f);
		g.drawString(mc.font, "\u2713", 0, 0, th.accent());
		g.pose().popMatrix();
		g.drawString(mc.font, title, x + 32, y + 6, th.text());
		g.drawString(mc.font, text, x + 32, y + 17, th.sub());
	}
}
