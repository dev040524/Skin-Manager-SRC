package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.Capes;
import dev.skinlibrary.Pose;
import dev.skinlibrary.Sfx;
import dev.skinlibrary.SkinPreviewCache;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Базовый экран с темой. Кнопки — обычные Button (клики, подсказки, фокус работают как в игре),
 * поверх которых рисуется стилизованный слой с анимацией наведения.
 */
public abstract class ThemedScreen extends Screen {
	protected final long openTime = System.nanoTime();
	protected final PreviewDrag drag = new PreviewDrag();

	protected ThemedScreen(Component title) {
		super(title);
	}

	protected Theme th() {
		return Theme.current();
	}

	protected void addBackground() {
		this.addRenderableOnly((g, mx, my, d) -> Ui.background(g, this.width, this.height, openTime, th()));
	}

	protected void addTitle(String text) {
		this.addRenderableOnly((g, mx, my, d) -> Ui.title(g, this.font, text, this.width / 2, 8, th(), openTime));
	}

	/** kind: 0 — обычная, 1 — главная, 2 — опасная. */
	protected Button fancy(Supplier<String> label, int x, int y, int w, int h, Button.OnPress press,
						   String tooltip, int kind) {
		Button.Builder bb = Button.builder(Component.empty(), b0 -> {
			Sfx.click();
			press.onPress(b0);
		}).bounds(x, y, w, h);
		if (tooltip != null) bb.tooltip(Tooltip.create(Component.literal(tooltip)));
		Button b = bb.build();
		this.addRenderableWidget(b);

		float[] hover = {0f};
		boolean[] wasHover = {false};
		long[] last = {System.nanoTime()};
		this.addRenderableOnly((g, mx, my, d) -> {
			long now = System.nanoTime();
			float dt = Math.min(0.1f, (now - last[0]) / 1e9f);
			last[0] = now;
			boolean hov = b.visible && b.active && b.isHoveredOrFocused();
			if (hov && !wasHover[0]) Sfx.tick();
			wasHover[0] = hov;
			float target = hov ? 1f : 0f;
			if (th().classic()) hover[0] = target;
			else hover[0] += (target - hover[0]) * (1f - (float) Math.exp(-dt * 16f));
			if (!b.visible) return;
			Ui.button(g, this.font, b.getX(), b.getY(), b.getWidth(), b.getHeight(), label.get(),
					hover[0], b.active, kind, th());
		});
		return b;
	}

	protected Button fancy(String label, int x, int y, int w, int h, Button.OnPress press, int kind) {
		return fancy(() -> label, x, y, w, h, press, null, kind);
	}

	/** Анимированное свечение вокруг поля ввода при фокусе. */
	protected void glow(EditBox box) {
		float[] f = {0f};
		long[] last = {System.nanoTime()};
		this.addRenderableOnly((g, mx, my, d) -> {
			long now = System.nanoTime();
			float dt = Math.min(0.1f, (now - last[0]) / 1e9f);
			last[0] = now;
			f[0] += ((box.isFocused() ? 1f : 0f) - f[0]) * (1f - (float) Math.exp(-dt * 14f));
			Ui.fieldRing(g, box.getX(), box.getY(), box.getWidth(), box.getHeight(), f[0], th());
		});
	}

	/** Превью 3D-модели с рамкой, вращением мышью и текущей позой. */
	protected void drawPreview(GuiGraphics g, int mx, int my, String source, boolean slim, int px, int py) {
		Theme th = th();
		float t = (System.nanoTime() - openTime) / 1e9f;
		int a = th.classic() ? 20 : (int) (40 + 25 * Math.sin(t * 3));
		Ui.outline(g, px - 4, py - 4, 108, 158, Ui.alpha(th.accent(), a + 60));
		drag.update(mx, my, px, py, 100, 150, true, false);
		SkinPreviewCache.get(source).draw(g, px, py, slim, drag.yaw, Pose.current());
	}

	/** Кнопка смены позы под превью. */
	protected Button poseButton(int x, int y) {
		return fancy(() -> I18n.t("Поза: ") + I18n.t(Pose.current().label), x, y, 100, 18,
				b -> SkinStore.setPoseId(Pose.next().id),
				I18n.t("Стоит / идёт / машет. Модель можно вращать мышью"), 0);
	}

	// ------------------------------------------------------------------ плащи

	/** Включается экранами, где можно выбрать плащ. */
	protected boolean capesEnabled = false;
	protected String capeId = "";
	protected String capeName = "";
	CapePicker capePicker;

	/** Содержимое окон с плащами заканчивается примерно на 218-м пикселе: ниже должна поместиться полка с карточками. */
	private static final int CAPES_TOP = 226;

	private boolean inlineCapes() {
		return this.height - CAPES_TOP - 4 >= CapePicker.MIN_H;
	}

	/** Центр области содержимого (полка плащей внизу, по горизонтали ничего не сдвигается). */
	protected int cx0() {
		return this.width / 2;
	}

	/**
	 * Список плащей: при достаточной высоте окна — полка карточек внизу экрана (крупные, вращающиеся, как карточки скинов), иначе — кнопка (x, y, w), открывающая выбор.
	 */
	protected void addCapes(int x, int y, int w) {
		if (!capesEnabled) return;
		Consumer<Capes.Cape> pick = c -> {
			capeId = c == null ? "" : c.id;
			capeName = c == null ? "" : c.name;
		};
		if (inlineCapes()) {
			capePicker = new CapePicker(4, CAPES_TOP, this.width - 8, this.height - CAPES_TOP - 4, capeId, pick);
			this.addRenderableOnly((g, mx, my, d) -> capePicker.render(g, this.font, mx, my, th()));
		} else {
			fancy(() -> I18n.t("Плащ: ") + (capeName.isEmpty() ? (capeId.isEmpty() ? I18n.t("нет") : I18n.t("выбран")) : capeName), x, y, w, 18,
					b -> this.minecraft.setScreen(new CapeScreen(this, capeId, pick)), I18n.t("Список плащей SkinsRestorer"), 0);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (capePicker != null && capePicker.scroll(mouseX, mouseY, scrollY)) return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
