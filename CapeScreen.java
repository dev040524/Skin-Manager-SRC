package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.Capes;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Выбор плаща на весь экран (для низких окон, где не помещается полка под формой): те же крупные вращающиеся карточки. */
public class CapeScreen extends ThemedScreen {
	private final Screen parent;
	private final String selectedId;
	private final Consumer<Capes.Cape> onPick;

	public CapeScreen(Screen parent, String selectedId, Consumer<Capes.Cape> onPick) {
		super(Component.literal(I18n.t("Плащ")));
		this.parent = parent;
		this.selectedId = selectedId;
		this.onPick = onPick;
	}

	@Override
	protected void init() {
		addBackground();
		addTitle(I18n.t("Плащ"));
		capePicker = new CapePicker(4, 28, this.width - 8, this.height - 28 - 34, selectedId, onPick);
		this.addRenderableOnly((g, mx, my, d) -> capePicker.render(g, this.font, mx, my, th()));
		fancy(() -> I18n.t("Готово"), this.width / 2 - 50, this.height - 26, 100, 20, b -> this.onClose(), null, 1);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
