package dev.skinlibrary.screen;

import dev.skinlibrary.Input;

/**
 * Вращение модели мышью: зажмите левую кнопку на превью и двигайте мышь.
 * Состояние кнопки читается напрямую, поэтому не нужны переопределения событий мыши.
 */
final class PreviewDrag {
	float yaw = 25f;
	private float vel; // угловая скорость (град/с) для инерции после отпускания
	boolean dragging;
	private boolean wasDown, first = true;
	private double lastMx;
	private long last = System.nanoTime();

	/** @param blocked true — не начинать перетаскивание (например, мышь над кнопкой). */
	void update(int mx, int my, int rx, int ry, int rw, int rh, boolean autoSpin, boolean blocked) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;

		boolean down = Input.leftDown();
		if (first) { // нажатие, начатое на другом экране, не должно начинать вращение
			first = false;
			wasDown = down;
			lastMx = mx;
		}
		boolean inside = mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
		if (down && !wasDown && inside && !blocked) dragging = true;
		if (!down) dragging = false;

		if (dragging) {
			float d = (float) (mx - lastMx) * 1.3f;
			yaw += d;
			if (dt > 0f) vel = vel * 0.6f + (d / dt) * 0.4f;
		} else if (Math.abs(vel) > 20f) { // плавное вращение по инерции
			yaw += vel * dt;
			vel *= (float) Math.exp(-dt * 2.2f);
		} else if (autoSpin) {
			vel = 0f;
			yaw += dt * 50f;
		}
		yaw %= 360f;
		lastMx = mx;
		wasDown = down;
	}
}
