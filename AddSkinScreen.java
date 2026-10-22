package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.SkinCommand;
import dev.skinlibrary.SkinEntry;
import dev.skinlibrary.SkinPreviewCache;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Вставка команды /skin url ... из сайта SkinRestorer и предпросмотр. */
public class AddSkinScreen extends ThemedScreen {
	private final Screen parent;
	private EditBox cmdBox, nameBox;
	private Button addBtn;
	private SkinEntry parsed;
	private String cmdText = "", nameText = "";
	private boolean dup;

	public AddSkinScreen(Screen parent) {
		super(Component.literal(I18n.t("Добавить скин")));
		this.parent = parent;
		this.capesEnabled = true;
	}

	@Override
	protected void init() {
		int x0 = cx0() - 190;
		int px = cx0() + 66, py = 38;

		addBackground();
		addTitle(I18n.t("Добавить скин"));
		this.addRenderableOnly((g, mx, my, d) -> {
			Theme th = th();
			Ui.panel(g, x0 - 10, 30, 250, 160, th);
			Ui.panel(g, px - 8, 30, 116, 164, th);
			g.drawString(this.font, I18n.t("Команда из сайта SkinRestorer:"), x0, 37, th.sub());
		});

		cmdBox = new EditBox(this.font, x0, 48, 230, 20, Component.literal(I18n.t("Команда")));
		cmdBox.setMaxLength(1024);
		cmdBox.setHint(Component.literal("/skin url \"https://minesk.in/...\" classic"));
		cmdBox.setValue(cmdText);
		cmdBox.setResponder(this::onCommandChanged);
		this.addRenderableWidget(cmdBox);
		glow(cmdBox);
		this.setInitialFocus(cmdBox);

		fancy(() -> I18n.t("Вставить из буфера"), x0, 72, 230, 20,
				b -> cmdBox.setValue(this.minecraft.keyboardHandler.getClipboard()), null, 0);

		nameBox = new EditBox(this.font, x0, 108, 230, 20, Component.literal(I18n.t("Название")));
		nameBox.setMaxLength(32);
		nameBox.setHint(Component.literal(I18n.t("Название (необязательно)")));
		nameBox.setValue(nameText);
		nameBox.setResponder(s -> nameText = s);
		this.addRenderableWidget(nameBox);
		glow(nameBox);

		addBtn = fancy(() -> I18n.t("Добавить в библиотеку"), x0, 136, 230, 20, b -> save(), null, 1);
		fancy(() -> I18n.t("Отмена"), x0, 160, 230, 20, b -> this.onClose(), null, 0);

		this.addRenderableOnly((g, mx, my, d) -> {
			if (parsed != null) {
				drawPreview(g, mx, my, parsed.url, parsed.isSlim(), px, py);
			} else {
				g.drawCenteredString(this.font, I18n.t("Превью"), px + 50, py + 71, th().sub());
			}
		});
		poseButton(px, 200);
		addCapes(px, 222, 100);

		onCommandChanged(cmdText);
	}

	private void onCommandChanged(String text) {
		cmdText = text;
		dup = false;
		parsed = SkinCommand.parse(text);
		if (addBtn != null) addBtn.active = parsed != null;
	}

	private void save() {
		if (parsed == null) return;
		if (SkinStore.hasSkin(parsed.url, parsed.variant)) {
			dup = true;
			return;
		}
		String n = nameText.trim();
		parsed.name = n.isEmpty() ? SkinStore.defaultName() : n;
		parsed.capeId = capeId;
		parsed.capeName = capeName;
		SkinStore.add(parsed);
		this.minecraft.setScreen(parent);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		int x0 = cx0() - 190;
		if (dup) {
			g.drawString(this.font, I18n.t("Такой скин уже в библиотеке"), x0, 96, 0xFFFFC857);
		} else if (!cmdText.isBlank() && parsed == null) {
			g.drawString(this.font, I18n.t("Неверная команда"), x0, 96, 0xFFFF6B6B);
		} else if (parsed != null) {
			g.drawString(this.font, I18n.t("Модель: ") + parsed.variant, x0, 96, 0xFF6BFF8F);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
