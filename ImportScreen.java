package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.FileDialogs;
import dev.skinlibrary.LicenseSync;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Импорт скинов из лицензионного аккаунта, лаунчеров (Prism, Modrinth App и др.) и папок. */
public class ImportScreen extends ThemedScreen {
	private static List<String> lastLines = new ArrayList<>();
	private static String lastSummary = "";

	private final Screen parent;
	private boolean busy;

	public ImportScreen(Screen parent) {
		super(Component.literal(I18n.t("Импорт скинов")));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x0 = this.width / 2 - 150;

		addBackground();
		addTitle(I18n.t("Импорт скинов"));
		this.addRenderableOnly((g, mx, my, d) -> {
			Theme th = th();
			Ui.panel(g, x0 - 10, 30, 320, 214, th);
			g.drawString(this.font, I18n.t("Аккаунт, официальный лаунчер, Prism, Modrinth App и др."), x0, 37, th.sub());

			g.drawString(this.font, busy ? I18n.t("Поиск...") : (lastSummary.isEmpty() ? I18n.t("Результат поиска:") : lastSummary),
					x0, 110, busy ? th.sub() : th.text());
			int y = 122;
			for (int i = 0; i < Math.min(5, lastLines.size()); i++) {
				String s = lastLines.get(i);
				while (this.font.width(s) > 296 && s.length() > 1) s = s.substring(0, s.length() - 1);
				g.drawString(this.font, s, x0 + 4, y, th.sub());
				y += 10;
			}
			g.drawString(this.font, I18n.t("Свои папки со скинами:"), x0, 178, th.sub());
		});

		fancy(() -> I18n.t("Найти новые скины"), x0, 48, 300, 20, b -> run(false), I18n.t("Аккаунт и все найденные лаунчеры"), 1);
		fancy(() -> I18n.t("Вернуть удалённые"), x0, 72, 148, 20, b -> run(true),
				I18n.t("Вернуть скины, которые вы удаляли"), 0);
		fancy(() -> I18n.t("Добавить папку..."), x0 + 152, 72, 148, 20, b -> addFolder(),
				I18n.t("Все PNG-скины из папки (и вложенных) попадут в библиотеку"), 0);

		List<String> folders = SkinStore.scanFolders();
		if (folders.size() > 3) { // остальные появятся после удаления верхних
			final int more = folders.size() - 3;
			this.addRenderableOnly((g, mx, my, d) -> g.drawString(this.font, "+" + more, x0 + 4, 238, th().sub()));
		}
		for (int i = 0; i < Math.min(3, folders.size()); i++) {
			String f = folders.get(i);
			int y = 190 + i * 16;
			fancy(() -> "x", x0 + 282, y, 18, 14, b -> {
				SkinStore.removeScanFolder(f);
				this.rebuildWidgets();
			}, I18n.t("Убрать папку из автопоиска"), 2);
			this.addRenderableOnly((g, mx, my, d) -> {
				String s = f;
				while (this.font.width(s) > 270 && s.length() > 4) s = "…" + s.substring(2);
				g.drawString(this.font, s, x0 + 4, y + 3, th().text());
			});
		}

		fancy(() -> I18n.t("Назад"), this.width / 2 - 50, this.height - 26, 100, 20, b -> this.onClose(), null, 0);
	}

	private void run(boolean force) {
		if (busy) return;
		busy = true;
		LicenseSync.sync(force).thenAccept(r -> {
			busy = false;
			lastLines = new ArrayList<>(r.lines());
			lastSummary = I18n.t("Добавлено новых скинов: ") + r.added();
		});
	}

	private void addFolder() {
		FileDialogs.pick(FileDialogs.Mode.PICK_FOLDER, p -> {
			if (p == null || p.isBlank()) return;
			SkinStore.addScanFolder(Path.of(p).toString());
			this.rebuildWidgets();
			run(false);
		});
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
