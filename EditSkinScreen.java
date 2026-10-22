package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.SkinEntry;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Редактирование записи: название, тег/папка, избранное. */
public class EditSkinScreen extends ThemedScreen {
	private final Screen parent;
	private final SkinEntry entry;
	private EditBox nameBox, tagBox;
	private String nameText, tagText;
	private boolean fav;

	public EditSkinScreen(Screen parent, SkinEntry entry) {
		super(Component.literal(I18n.t("Редактировать скин")));
		this.parent = parent;
		this.entry = entry;
		this.nameText = entry.name == null ? "" : entry.name;
		this.tagText = entry.tag == null ? "" : entry.tag;
		this.fav = entry.favorite;
		this.capesEnabled = true;
		this.capeId = entry.capeId == null ? "" : entry.capeId;
		this.capeName = entry.capeName == null ? "" : entry.capeName;
	}

	@Override
	protected void init() {
		int x0 = cx0() - 190;
		int px = cx0() + 66, py = 38;

		addBackground();
		addTitle(I18n.t("Редактировать скин"));
		this.addRenderableOnly((g, mx, my, d) -> {
			Theme th = th();
			Ui.panel(g, x0 - 10, 30, 250, 160, th);
			Ui.panel(g, px - 8, 30, 116, 164, th);
			g.drawString(this.font, I18n.t("Название:"), x0, 37, th.sub());
			g.drawString(this.font, I18n.t("Папка:"), x0, 73, th.sub());
		});

		nameBox = new EditBox(this.font, x0, 48, 230, 20, Component.literal(I18n.t("Название")));
		nameBox.setMaxLength(32);
		nameBox.setValue(nameText);
		nameBox.setResponder(s -> nameText = s);
		this.addRenderableWidget(nameBox);
		glow(nameBox);
		this.setInitialFocus(nameBox);

		tagBox = new EditBox(this.font, x0, 84, 170, 20, Component.literal(I18n.t("Папка")));
		tagBox.setMaxLength(24);
		tagBox.setHint(Component.literal(I18n.t("Например: Аниме, Друзья, Работа")));
		tagBox.setValue(tagText);
		tagBox.setResponder(s -> tagText = s);
		this.addRenderableWidget(tagBox);
		glow(tagBox);
		fancy(() -> I18n.t("Из списка"), x0 + 174, 84, 56, 20, b -> {
			java.util.List<String> fs = dev.skinlibrary.SkinStore.folders();
			if (fs.isEmpty()) return;
			int i = -1;
			for (int k = 0; k < fs.size(); k++) if (fs.get(k).equalsIgnoreCase(tagText.trim())) i = k;
			tagBox.setValue(fs.get((i + 1) % fs.size()));
		}, I18n.t("Выбрать из существующих папок"), 0);

		fancy(() -> fav ? I18n.t("\u2605 В избранном") : I18n.t("\u2606 Добавить в избранное"), x0, 110, 230, 20,
				b -> fav = !fav, null, 0);
		fancy(() -> I18n.t("Сохранить"), x0, 136, 230, 20, b -> save(), null, 1);
		fancy(() -> I18n.t("Отмена"), x0, 160, 230, 20, b -> this.onClose(), null, 0);

		this.addRenderableOnly((g, mx, my, d) -> drawPreview(g, mx, my, entry.source(), entry.isSlim(), px, py));
		poseButton(px, 200);
		addCapes(px, 222, 100);
	}

	private void save() {
		String n = nameText.trim();
		if (!n.isEmpty()) entry.name = n;
		entry.tag = tagText.trim();
		entry.favorite = fav;
		if (!capeId.equals(entry.capeId == null ? "" : entry.capeId)) {
			entry.capeId = capeId;
			entry.capeName = capeName;
			entry.capeUrl = ""; // скин с новым плащом создастся при надевании
			SkinStore.clearApplied(entry.id);
		}
		SkinStore.save();
		this.minecraft.setScreen(parent);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
