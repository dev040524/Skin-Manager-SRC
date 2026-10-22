package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.SkinEntry;
import dev.skinlibrary.SkinResolver;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/** Добавление скина по нику игрока (через API Mojang). */
public class NickScreen extends ThemedScreen {
	private final Screen parent;
	private EditBox nickBox;
	private Button findBtn, addBtn;
	private String nickText = "";
	private String status = "";
	private int statusColor = 0xFFAAAAAA;
	private boolean busy = false;
	private SkinEntry found;

	public NickScreen(Screen parent) {
		super(Component.literal(I18n.t("Скин по нику")));
		this.parent = parent;
		this.capesEnabled = true;
	}

	@Override
	protected void init() {
		int x0 = cx0() - 190;
		int px = cx0() + 66, py = 38;

		addBackground();
		addTitle(I18n.t("Скин по нику"));
		this.addRenderableOnly((g, mx, my, d) -> {
			Theme th = th();
			Ui.panel(g, x0 - 10, 30, 250, 160, th);
			Ui.panel(g, px - 8, 30, 116, 164, th);
			g.drawString(this.font, I18n.t("Ник игрока (лицензия Minecraft):"), x0, 37, th.sub());
			g.drawString(this.font, Ui.fit(this.font, status, 232), x0, 100, statusColor);
		});

		nickBox = new EditBox(this.font, x0, 48, 230, 20, Component.literal(I18n.t("Ник")));
		nickBox.setMaxLength(16);
		nickBox.setHint(Component.literal(I18n.t("Например: Notch")));
		nickBox.setValue(nickText);
		nickBox.setResponder(s -> {
			nickText = s;
			if (found != null) { // ник изменили после поиска: найденный скин больше не относится к тексту
				found = null;
				updateButtons();
			}
		});
		this.addRenderableWidget(nickBox);
		glow(nickBox);
		this.setInitialFocus(nickBox);

		findBtn = fancy(() -> I18n.t("Найти скин"), x0, 72, 230, 20, b -> find(), null, 1);
		addBtn = fancy(() -> I18n.t("Добавить в библиотеку"), x0, 136, 230, 20, b -> save(), null, 1);
		fancy(() -> I18n.t("Отмена"), x0, 160, 230, 20, b -> this.onClose(), null, 0);

		this.addRenderableOnly((g, mx, my, d) -> {
			if (found != null) {
				drawPreview(g, mx, my, found.url, found.isSlim(), px, py);
			} else {
				g.drawCenteredString(this.font, I18n.t("Превью"), px + 50, py + 71, th().sub());
			}
		});
		poseButton(px, 200);
		addCapes(px, 222, 100);
		updateButtons();
	}

	private void updateButtons() {
		if (addBtn != null) addBtn.active = found != null;
		if (findBtn != null) findBtn.active = !busy;
	}

	private void find() {
		String nick = nickText.trim();
		if (nick.isEmpty()) {
			setStatus(I18n.t("Введите ник"), 0xFFFF6B6B);
			return;
		}
		busy = true;
		found = null;
		setStatus(I18n.t("Поиск..."), 0xFFAAAAAA);
		updateButtons();
		CompletableFuture.supplyAsync(() -> {
			try {
				return SkinResolver.lookupNick(nick);
			} catch (Exception e) {
				throw new IllegalStateException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			}
		}).whenComplete((entry, ex) -> this.minecraft.execute(() -> {
			busy = false;
			if (ex != null) {
				Throwable t = ex.getCause() != null ? ex.getCause() : ex;
				setStatus(I18n.t("Ошибка: ") + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()), 0xFFFF6B6B);
			} else {
				found = entry;
				setStatus(I18n.t("Найден: ") + entry.name + " (" + entry.variant + ")", 0xFF6BFF8F);
			}
			updateButtons();
		}));
	}

	private void save() {
		if (found == null) return;
		if (SkinStore.hasSkin(found.url, found.variant)) {
			setStatus(I18n.t("Такой скин уже в библиотеке"), 0xFFFFC857);
			return;
		}
		SkinEntry copy = new SkinEntry(found.name, found.url, found.variant);
		copy.nickId = found.nickId == null ? "" : found.nickId; // скин будет следовать за аккаунтом игрока
		copy.capeId = capeId;
		copy.capeName = capeName;
		SkinStore.add(copy);
		this.minecraft.setScreen(parent);
	}

	private void setStatus(String s, int color) {
		status = s;
		statusColor = color;
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
