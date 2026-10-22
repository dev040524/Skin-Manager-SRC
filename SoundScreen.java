package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.CustomSounds;
import dev.skinlibrary.FileDialogs;
import dev.skinlibrary.Input;
import dev.skinlibrary.Sfx;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Звуки: общие настройки, отдельная громкость и выключатели каждого звука, импорт своих звуков. */
public class SoundScreen extends ThemedScreen {
	private static final String[] KEYS = {"HOVER", "SELECT", "CLICK", "WEAR", "ANIM", "CASINO"};
	private static final String[] LABELS = {"Наведение", "Выбор карточки", "Нажатие", "Надевание", "Звуки анимаций", "Казино"};
	private static int tab = 0;

	private final Screen parent;
	private String status = "";
	private int statusColor = 0xFFAAAAAA;

	private final List<Slider> sliders = new ArrayList<>();
	private boolean wasDown;

	/** Ползунок громкости: тянется мышью (состояние кнопки читается напрямую). */
	private static final class Slider {
		final int x, y, w;
		final Supplier<Float> get;
		final Consumer<Float> live;
		boolean drag;

		Slider(int x, int y, int w, Supplier<Float> get, Consumer<Float> live) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.get = get;
			this.live = live;
		}
	}

	public SoundScreen(Screen parent) {
		super(Component.literal(I18n.t("Звуки")));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x0 = this.width / 2 - 150;
		sliders.clear();

		addBackground();
		addTitle(I18n.t("Звуки"));
		this.addRenderableOnly((g, mx, my, d) -> {
			Theme th = th();
			Ui.panel(g, x0 - 10, 30, 320, 218, th);
			g.drawString(this.font, status, x0, 234, statusColor);
			drawSliders(g, mx, my, th);
		});

		String[] tabs = {I18n.t("Общие"), I18n.t("Отдельные"), I18n.t("Свои")};
		for (int i = 0; i < 3; i++) {
			final int ti = i;
			fancy(() -> (tab == ti ? "\u25B8 " : "") + tabs[ti], x0 + i * 102, 36, 96, 18, b -> {
				tab = ti;
				this.rebuildWidgets();
			}, null, tab == i ? 1 : 0);
		}

		if (tab == 0) buildGeneral(x0);
		else if (tab == 1) buildPerSound(x0);
		else buildCustom(x0);

		fancy(() -> I18n.t("Назад"), this.width / 2 - 50, this.height - 26, 100, 20, b -> this.onClose(), null, 0);
	}

	private void buildGeneral(int x0) {
		fancy(() -> I18n.t("Звук: ") + (SkinStore.sound() ? I18n.t("вкл") : I18n.t("выкл")), x0, 62, 148, 20,
				b -> SkinStore.setSound(!SkinStore.sound()), null, 0);
		sliders.add(new Slider(x0 + 160, 68, 110, SkinStore::volume, SkinStore::setVolumeLive));
		this.addRenderableOnly((g, mx, my, d) ->
				g.drawString(this.font, Math.round(SkinStore.volume() * 100) + "%", x0 + 276, 66, th().text()));

		fancy(() -> I18n.t("Звуки анимаций: ") + (SkinStore.animSounds() ? I18n.t("вкл") : I18n.t("выкл")), x0, 88, 300, 20, b -> {
			SkinStore.setAnimSounds(!SkinStore.animSounds());
			Sfx.previewAnimation();
		}, I18n.t("Книги — страницы, Веер — стук планок, Волна — переливы, Карусель — щелчки"), 0);

		fancy(() -> I18n.t("Тема звуков: ") + I18n.t(Sfx.Pack.current().label), x0, 114, 220, 20,
				b -> SkinStore.setSoundPackId(Sfx.Pack.next().id), I18n.t("Независима от стиля анимации карточек"), 0);
		fancy(() -> I18n.t("Тест"), x0 + 224, 114, 76, 20, b -> {
			Sfx.preview(Sfx.Event.HOVER);
			Sfx.preview(Sfx.Event.SELECT);
		}, null, 0);

		this.addRenderableOnly((g, mx, my, d) -> {
			Theme th = th();
			g.drawString(this.font, I18n.t("Звуки анимаций зависят от стиля карточек,"), x0, 146, th.sub());
			g.drawString(this.font, I18n.t("а в темах-играх — от темы. Громкость и выключатели"), x0, 158, th.sub());
			g.drawString(this.font, I18n.t("каждого звука — на вкладке «Отдельные»."), x0, 170, th.sub());
		});
	}

	private void buildPerSound(int x0) {
		for (int i = 0; i < KEYS.length; i++) {
			final String key = KEYS[i];
			int y = 62 + i * 26;
			this.addRenderableOnly((g, mx, my, d) -> g.drawString(this.font, I18n.t(LABELS[indexOf(key)]), x0 + 2, y + 6, th().text()));
			fancy(() -> SkinStore.soundEnabled(key) ? I18n.t("вкл") : I18n.t("выкл"), x0 + 100, y, 46, 20,
					b -> SkinStore.setSoundEnabled(key, !SkinStore.soundEnabled(key)), I18n.t("Включить или выключить этот звук"), 0);
			sliders.add(new Slider(x0 + 154, y + 5, 100, () -> SkinStore.soundVolume(key), v -> SkinStore.setSoundVolumeLive(key, v)));
			fancy(() -> I18n.t("Тест"), x0 + 262, y, 38, 20, b -> Sfx.previewKey(key), null, 0);
		}
	}

	private static int indexOf(String key) {
		for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
		return 0;
	}

	private void buildCustom(int x0) {
		this.addRenderableOnly((g, mx, my, d) ->
				g.drawString(this.font, I18n.t("Свои звуки (.ogg или .wav, до 3 с):"), x0, 62, th().sub()));
		for (int i = 0; i < Sfx.Event.values().length; i++) {
			Sfx.Event e = Sfx.Event.values()[i];
			int y = 78 + i * 30;
			this.addRenderableOnly((g, mx, my, d) -> {
				Theme th = th();
				boolean has = CustomSounds.has(e);
				g.drawString(this.font, I18n.t(e.label), x0 + 2, y + 2, th.text());
				g.drawString(this.font, has ? I18n.t("\u2713 свой") : I18n.t("по умолчанию"), x0 + 2, y + 12, has ? 0xFF6BFF8F : th.sub());
			});
			fancy(() -> I18n.t("Импорт..."), x0 + 120, y, 76, 20, b -> importSound(e), I18n.t("Выбрать .ogg или .wav"), 0);
			fancy(() -> I18n.t("Тест"), x0 + 200, y, 44, 20, b -> Sfx.preview(e), null, 0);
			fancy(() -> I18n.t("Сброс"), x0 + 248, y, 52, 20, b -> {
				CustomSounds.clear(e);
				setStatus(I18n.t("Звук «") + I18n.t(e.label) + I18n.t("» сброшен"), 0xFFAAAAAA);
			}, null, 2);
		}
	}

	private void importSound(Sfx.Event e) {
		FileDialogs.pick(FileDialogs.Mode.OPEN_AUDIO, p -> {
			if (p == null) return;
			try {
				CustomSounds.importFile(e, Path.of(p));
				if (Sfx.Pack.current() != Sfx.Pack.CUSTOM) SkinStore.setSoundPackId(Sfx.Pack.CUSTOM.id);
				setStatus(I18n.t("Загружено: «") + I18n.t(e.label) + I18n.t("». Тема звуков: Свои"), 0xFF6BFF8F);
				Sfx.preview(e);
			} catch (Exception ex) {
				setStatus(I18n.t("Ошибка: ") + (ex.getMessage() == null ? I18n.t("файл не читается") : ex.getMessage()), 0xFFFF6B6B);
			}
		});
	}

	private void drawSliders(GuiGraphics g, int mx, int my, Theme th) {
		boolean down = Input.leftDown();
		for (Slider s : sliders) {
			boolean over = mx >= s.x - 3 && mx < s.x + s.w + 3 && my >= s.y - 3 && my < s.y + 13;
			if (down && !wasDown && over) s.drag = true;
			if (s.drag) {
				if (down) s.live.accept(Math.max(0f, Math.min(1f, (mx - s.x) / (float) s.w)));
				else {
					s.drag = false;
					SkinStore.saveSettings();
				}
			}
			float v = s.get.get();
			int hx = s.x + Math.round(s.w * v);
			g.fill(s.x, s.y + 3, s.x + s.w, s.y + 7, 0xFF16161F);
			g.fill(s.x, s.y + 3, hx, s.y + 7, th.accent());
			Ui.outline(g, s.x - 1, s.y + 2, s.w + 2, 6, 0xFF000000);
			g.fill(hx - 2, s.y, hx + 2, s.y + 10, s.drag || over ? 0xFFFFFFFF : 0xFFC8C8D8);
			Ui.outline(g, hx - 2, s.y, 4, 10, 0xFF000000);
		}
		if (!down) for (Slider s : sliders) s.drag = false;
		wasDown = down;
	}

	private void setStatus(String s, int c) {
		status = s;
		statusColor = c;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
