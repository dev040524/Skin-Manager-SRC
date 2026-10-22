package dev.skinlibrary.screen;

import dev.skinlibrary.I18n;
import dev.skinlibrary.FileDialogs;
import dev.skinlibrary.MineSkinApi;
import dev.skinlibrary.SkinEntry;
import dev.skinlibrary.SkinLibraryClient;
import dev.skinlibrary.SkinPreviewCache;
import dev.skinlibrary.SkinResolver;
import dev.skinlibrary.SkinStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Генератор команды /skin url ... (как на skinsrestorer.net/upload). */
public class GeneratorScreen extends ThemedScreen {
	private final Screen parent;
	private EditBox srcBox, nameBox, resultBox;
	private Button genBtn, copyBtn, libBtn, wearBtn;

	private String srcText = "", nameText = "", resultText = "";
	private String variant = "classic";
	private String status = "";
	private int statusColor = 0xFFAAAAAA;
	private boolean busy = false;
	private SkinEntry generated;
	private String previewSource = null;

	public GeneratorScreen(Screen parent) {
		super(Component.literal(I18n.t("Генератор команды")));
		this.parent = parent;
		this.capesEnabled = true;
	}

	@Override
	protected void init() {
		int x0 = cx0() - 190;
		int px = cx0() + 66, py = 38;

		addBackground();
		addTitle(I18n.t("Генератор команды"));
		this.addRenderableOnly((g, mx, my, d) -> {
			Theme th = th();
			Ui.panel(g, x0 - 10, 30, 250, 204, th);
			Ui.panel(g, px - 8, 30, 116, 164, th);
			g.drawString(this.font, I18n.t("Ссылка или PNG-файл:"), x0, 38, th.sub());
			g.drawString(this.font, I18n.t("Название:"), x0, 74, th.sub());
			g.drawString(this.font, status, x0, 160, statusColor);
			g.drawString(this.font, I18n.t("Загрузка через SkinsRestorer / MineSkin"), x0, 222, th.sub());
		});

		srcBox = new EditBox(this.font, x0, 48, 156, 20, Component.literal(I18n.t("Источник")));
		srcBox.setMaxLength(1024);
		srcBox.setHint(Component.literal(I18n.t("https://... или C:\\skin.png")));
		srcBox.setValue(srcText);
		srcBox.setResponder(this::onSourceChanged);
		this.addRenderableWidget(srcBox);
		glow(srcBox);
		this.setInitialFocus(srcBox);

		fancy(() -> I18n.t("Обзор..."), x0 + 160, 48, 70, 20, b -> browse(), I18n.t("Выбрать PNG в проводнике"), 0);

		nameBox = new EditBox(this.font, x0, 84, 230, 20, Component.literal(I18n.t("Название")));
		nameBox.setMaxLength(32);
		nameBox.setHint(Component.literal(I18n.t("Название (необязательно)")));
		nameBox.setValue(nameText);
		nameBox.setResponder(s -> nameText = s);
		this.addRenderableWidget(nameBox);
		glow(nameBox);

		fancy(() -> I18n.t("Модель: ") + variant, x0, 110, 230, 20,
				b -> variant = variant.equals("classic") ? "slim" : "classic", null, 0);
		genBtn = fancy(() -> I18n.t("Сгенерировать команду"), x0, 134, 230, 20, b -> generate(), null, 1);

		resultBox = new EditBox(this.font, x0, 172, 230, 20, Component.literal(I18n.t("Результат")));
		resultBox.setMaxLength(2048);
		resultBox.setValue(resultText);
		resultBox.setEditable(false);
		this.addRenderableWidget(resultBox);
		glow(resultBox);

		copyBtn = fancy(() -> I18n.t("Копировать"), x0, 198, 74, 20,
				b -> this.minecraft.keyboardHandler.setClipboard(resultText), null, 0);
		libBtn = fancy(() -> I18n.t("В библиотеку"), x0 + 78, 198, 80, 20, b -> saveToLibrary(), null, 0);
		wearBtn = fancy(() -> I18n.t("Надеть \u25B6"), x0 + 162, 198, 68, 20, b -> {
			if (generated != null) SkinLibraryClient.wear(generated);
		}, null, 1);

		fancy(() -> I18n.t("Назад"), this.width / 2 - 50, this.height - 26, 100, 20, b -> this.onClose(), null, 0);

		this.addRenderableOnly((g, mx, my, d) -> {
			if (previewSource != null) {
				drawPreview(g, mx, my, previewSource, variant.equals("slim"), px, py);
			} else {
				g.drawCenteredString(this.font, I18n.t("Превью"), px + 50, py + 71, th().sub());
			}
		});
		poseButton(px, 200);
		addCapes(px, 222, 100);

		updateButtons();
		onSourceChanged(srcText);
	}

	private void updateButtons() {
		boolean has = generated != null;
		if (copyBtn != null) copyBtn.active = has;
		if (libBtn != null) libBtn.active = has;
		if (wearBtn != null) wearBtn.active = has;
		if (genBtn != null) genBtn.active = !busy;
	}

	private void onSourceChanged(String s) {
		srcText = s;
		String n = SkinPreviewCache.normalize(s);
		boolean remote = SkinResolver.isRemote(n);
		boolean loadable;
		if (remote) {
			String l = n.toLowerCase();
			loadable = n.length() > 12 && (l.endsWith(".png") || l.contains("minesk.in") || l.contains("mineskin.org")
					|| l.contains("textures.minecraft.net") || l.startsWith(SkinResolver.AXOLOTL_SCHEME));
		} else {
			loadable = n.toLowerCase().endsWith(".png") && Files.isRegularFile(Path.of(safePath(n)));
		}
		previewSource = loadable ? n : (generated != null ? generated.url : null);
	}

	private static String safePath(String p) {
		try {
			Path.of(p);
			return p;
		} catch (Exception e) {
			return ".";
		}
	}

	private void generate() {
		String src = SkinPreviewCache.normalize(srcText);
		if (src.isEmpty()) {
			setStatus(I18n.t("Укажите ссылку на PNG или путь к файлу"), 0xFFFF6B6B);
			return;
		}
		boolean remote = SkinResolver.isRemote(src);
		if (!remote && !Files.isRegularFile(Path.of(safePath(src)))) {
			setStatus(I18n.t("Файл не найден"), 0xFFFF6B6B);
			return;
		}
		String name = nameText.trim();
		String var = variant;
		busy = true;
		generated = null;
		resultText = "";
		resultBox.setValue("");
		setStatus(I18n.t("Генерация..."), 0xFFAAAAAA);
		updateButtons();

		final String cid = capeId, cname = capeName;
		java.util.concurrent.CompletableFuture<String> job = cid.isEmpty()
				? MineSkinApi.generate(src, var, name)
				: MineSkinApi.generateWithCape(src, var, name, cid);
		job.whenComplete((url, ex) -> this.minecraft.execute(() -> {
			busy = false;
			if (ex != null) {
				Throwable t = ex.getCause() != null ? ex.getCause() : ex;
				String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
				setStatus(I18n.t("Ошибка: ") + msg, 0xFFFF6B6B);
			} else {
				generated = new SkinEntry(name.isEmpty() ? SkinStore.defaultName() : name, url, var);
				if (!cid.isEmpty()) {
					generated.capeId = cid;
					generated.capeName = cname;
					generated.capeUrl = url;
				}
				resultText = generated.commandDisplay();
				resultBox.setValue(resultText);
				previewSource = url;
				setStatus(I18n.t("Готово"), 0xFF6BFF8F);
			}
			updateButtons();
		}));
	}

	/** Открывает системный проводник для выбора PNG. */
	private void browse() {
		setStatus(I18n.t("Выберите файл в открывшемся окне..."), 0xFFAAAAAA);
		FileDialogs.pick(FileDialogs.Mode.OPEN_PNG, p -> {
			if (p != null) {
				srcBox.setValue(p);
				setStatus("", 0xFFAAAAAA);
			} else {
				setStatus(I18n.t("Файл не выбран"), 0xFFAAAAAA);
			}
		});
	}

	private void saveToLibrary() {
		if (generated == null) return;
		SkinEntry copy = new SkinEntry(generated.name, generated.url, generated.variant);
		copy.capeId = generated.capeId;
		copy.capeName = generated.capeName;
		copy.capeUrl = generated.capeUrl;
		SkinStore.add(copy);
		setStatus(I18n.t("Добавлено в библиотеку"), 0xFF6BFF8F);
	}

	private void setStatus(String s, int color) {
		status = s;
		statusColor = color;
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		if (!paths.isEmpty() && srcBox != null) srcBox.setValue(paths.get(0).toString());
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
