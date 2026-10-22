package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Настройки: надевание, автонадевание, случайный скин, превью в меню, уведомление о КД, язык. */
public class SettingsScreen extends ThemedScreen {
	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.literal(I18n.t("Настройки")));
		this.parent = parent;
	}

	private static String modeLabel() {
		return switch (SkinStore.wearMode()) {
			case "button" -> I18n.t("По кнопке");
			case "both" -> I18n.t("Карточка и кнопка");
			default -> I18n.t("По карточке");
		};
	}

	private static void nextMode() {
		String m = SkinStore.wearMode();
		SkinStore.setWearMode(m.equals("card") ? "button" : m.equals("button") ? "both" : "card");
	}

	private static String randomLabel() {
		return switch (SkinStore.randomMode()) {
			case "join" -> I18n.t("при каждом входе");
			case "daily" -> I18n.t("скин дня");
			default -> I18n.t("выкл");
		};
	}

	private static void nextRandom() {
		String m = SkinStore.randomMode();
		SkinStore.setRandomMode(m.equals("off") ? "join" : m.equals("join") ? "daily" : "off");
	}

	private static String randomFolderLabel() {
		String f = SkinStore.randomFolder();
		if (f.isEmpty()) return I18n.t("все скины");
		if (f.equals("\u2605")) return I18n.t("избранное");
		return f.length() > 14 ? f.substring(0, 14) + "…" : f;
	}

	private static void nextRandomFolder() {
		List<String> opts = new ArrayList<>();
		opts.add("");
		opts.add("\u2605");
		opts.addAll(SkinStore.folders());
		int i = opts.indexOf(SkinStore.randomFolder());
		SkinStore.setRandomFolder(opts.get((i + 1) % opts.size()));
	}

	private static String languageLabel() {
		return switch (SkinStore.language()) {
			case "ru" -> I18n.t("Русский");
			case "en" -> "English";
			default -> I18n.t("Авто");
		};
	}

	private static void nextLanguage() {
		String l = SkinStore.language();
		SkinStore.setLanguage(l.equals("auto") ? "ru" : l.equals("ru") ? "en" : "auto");
		I18n.refresh();
	}

	@Override
	protected void init() {
		int x0 = this.width / 2 - 150;
		addBackground();
		addTitle(I18n.t("Настройки"));
		this.addRenderableOnly((g, mx, my, d) -> Ui.panel(g, x0 - 10, 30, 320, 206, th()));

		int y = 40;
		fancy(() -> I18n.t("Надевание: ") + modeLabel(), x0, y, 300, 22, b -> nextMode(),
				I18n.t("По карточке — клик по выбранной карточке; по кнопке — кнопка «Надеть»; или оба способа"), 1);
		y += 26;
		fancy(() -> I18n.t("Автонадевание при входе: ") + (SkinStore.autoApply() ? I18n.t("вкл") : I18n.t("выкл")), x0, y, 300, 22,
				b -> SkinStore.setAutoApply(!SkinStore.autoApply()),
				I18n.t("Выбранный скин применяется на сервере, если там есть SkinRestorer и скин ещё не надет"), 0);
		y += 26;
		fancy(() -> I18n.t("Случайный скин: ") + randomLabel(), x0, y, 300, 22, b -> nextRandom(),
				I18n.t("При входе на сервер надевать случайный скин или «скин дня» (один на сутки)"), 0);
		y += 26;
		fancy(() -> I18n.t("Брать из: ") + randomFolderLabel(), x0, y, 300, 22, b -> nextRandomFolder(),
				I18n.t("Из каких скинов выбирать случайный: все, избранные или папка"), 0);
		y += 26;
		fancy(() -> I18n.t("Превью в меню: ") + (SkinStore.previewEnabled() ? I18n.t("вкл") : I18n.t("выкл")), x0, y, 300, 22,
				b -> SkinStore.setPreviewEnabled(!SkinStore.previewEnabled()),
				I18n.t("Превью скина слева в главном меню и меню паузы"), 0);
		y += 26;
		fancy(() -> I18n.t("Уведомление о конце КД: ") + (SkinStore.notifyCooldown() ? I18n.t("вкл") : I18n.t("выкл")), x0, y, 300, 22,
				b -> SkinStore.setNotifyCooldown(!SkinStore.notifyCooldown()),
				I18n.t("Показать уведомление, когда снова можно менять скин (КД ") + SkinStore.cooldownSec() + I18n.t(" с)"), 0);
		y += 26;
		fancy(() -> I18n.t("Язык / Language: ") + languageLabel(), x0, y, 300, 22, b -> {
			nextLanguage();
			this.rebuildWidgets();
		}, I18n.t("Авто — по языку игры: русский или английский"), 0);

		fancy(() -> I18n.t("Открыть гайд"), this.width / 2 + 56, this.height - 26, 100, 20,
				b -> this.minecraft.setScreen(new GuideScreen(this)), I18n.t("Показать обучение по всем функциям мода"), 1);
		fancy(() -> I18n.t("Назад"), this.width / 2 - 156, this.height - 26, 100, 20, b -> this.onClose(), null, 0);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
