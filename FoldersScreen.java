package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Папки скинов: создать, переименовать, удалить, открыть в библиотеке. */
public class FoldersScreen extends ThemedScreen {
	private static final int ROW = 24;

	private final Screen parent;
	private EditBox nameBox;
	private String nameText = "";
	private String status = "";
	private int statusColor = 0xFFAAAAAA;
	private int page = 0;

	public FoldersScreen(Screen parent) {
		super(Component.literal(I18n.t("Папки скинов")));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x0 = this.width / 2 - 160;
		List<String> folders = SkinStore.folders();
		int perPage = Math.max(1, (this.height - 112 - 34) / ROW);
		int pages = Math.max(1, (folders.size() + perPage - 1) / perPage);
		page = Math.max(0, Math.min(page, pages - 1));

		addBackground();
		addTitle(I18n.t("Папки скинов"));
		this.addRenderableOnly((g, mx, my, d) -> {
			Theme th = th();
			int ph = Math.min(this.height - 62, 84 + perPage * ROW + 8);
			Ui.panel(g, x0 - 8, 30, 336, ph, th);
			g.drawString(this.font, I18n.t("Новая папка или новое имя для «Переим.»:"), x0, 36, th.sub());
			g.drawString(this.font, status, x0, 84, statusColor);
			if (folders.isEmpty()) g.drawString(this.font, I18n.t("Папок пока нет"), x0 + 4, 104, th.sub());
		});

		nameBox = new EditBox(this.font, x0, 48, 200, 20, Component.literal(I18n.t("Папка")));
		nameBox.setMaxLength(24);
		nameBox.setHint(Component.literal(I18n.t("Название папки")));
		nameBox.setValue(nameText);
		nameBox.setResponder(s -> nameText = s);
		this.addRenderableWidget(nameBox);
		glow(nameBox);
		this.setInitialFocus(nameBox);

		fancy(() -> I18n.t("Создать"), x0 + 204, 48, 112, 20, b -> {
			if (SkinStore.createFolder(nameText)) {
				setStatus(I18n.t("Папка создана"), 0xFF6BFF8F);
				nameText = "";
				this.rebuildWidgets();
			} else {
				setStatus(I18n.t("Пустое или уже существующее имя"), 0xFFFF6B6B);
			}
		}, null, 1);

		int from = page * perPage, to = Math.min(folders.size(), from + perPage);
		for (int i = from; i < to; i++) {
			String f = folders.get(i);
			int y = 98 + (i - from) * ROW;
			final String s = Ui.fit(this.font, f, 118); // строки считаем один раз при построении, а не каждый кадр
			final String cnt = I18n.t("скинов: ") + SkinStore.folderCount(f);
			this.addRenderableOnly((g, mx, my, d) -> {
				Theme th = th();
				g.drawString(this.font, s, x0 + 4, y + 2, th.text());
				g.drawString(this.font, cnt, x0 + 4, y + 12, th.sub());
			});
			fancy(() -> I18n.t("Открыть"), x0 + 124, y, 60, 20, b -> {
				SkinLibraryScreen.openFilter(f);
				this.minecraft.setScreen(parent);
			}, I18n.t("Показать скины папки в библиотеке"), 1);
			fancy(() -> I18n.t("Переим."), x0 + 188, y, 60, 20, b -> {
				if (SkinStore.renameFolder(f, nameText)) {
					setStatus(I18n.t("Переименовано"), 0xFF6BFF8F);
					nameText = "";
					this.rebuildWidgets();
				} else {
					setStatus(I18n.t("Введите новое имя (уникальное)"), 0xFFFF6B6B);
				}
			}, I18n.t("Новое имя берётся из поля сверху"), 0);
			fancy(() -> I18n.t("Удалить"), x0 + 252, y, 64, 20, b -> {
				SkinStore.deleteFolder(f);
				setStatus(I18n.t("Папка удалена, скины остались"), 0xFFAAAAAA);
				this.rebuildWidgets();
			}, I18n.t("Скины из папки не удаляются"), 2);
		}

		int cx = this.width / 2;
		fancy(() -> "<", cx - 84, this.height - 26, 36, 20, b -> {
			page--;
			this.rebuildWidgets();
		}, null, 0).active = page > 0;
		fancy(() -> I18n.t("Назад"), cx - 44, this.height - 26, 88, 20, b -> this.onClose(), null, 0);
		fancy(() -> ">", cx + 48, this.height - 26, 36, 20, b -> {
			page++;
			this.rebuildWidgets();
		}, null, 0).active = page < pages - 1;
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
