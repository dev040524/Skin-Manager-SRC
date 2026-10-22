package dev.skinlibrary;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Загруженный скин + динамическая текстура с 3D-превью. Рисовать только из render-потока. */
public final class SkinPreview {
	public enum State { LOADING, READY, FAILED }

	public static final int W = 100, H = 150;

	private static final AtomicInteger IDS = new AtomicInteger();

	public volatile State state = State.LOADING;
	public volatile String error = "";
	private volatile int[] skin;

	// общие рабочие буфера (рисуем всегда по очереди в одном потоке)
	private static final int[] SCRATCH_C = new int[W * H];
	private static final float[] SCRATCH_Z = new float[W * H];

	/** Отдельная текстура для каждого «места» показа (карточка, мини-папка, меню...), чтобы они не затирали друг друга. */
	private static final class View {
		DynamicTexture texture;
		Identifier id;
		int lastKey = Integer.MIN_VALUE;
		boolean lastSlim;
		Pose lastPose = Pose.STAND;
	}

	private final java.util.Map<String, View> views = new java.util.HashMap<>();

	/** Ограниченный пул загрузки: не забиваем общий пул, когда в библиотеке много скинов. */
	private static final java.util.concurrent.ExecutorService POOL = java.util.concurrent.Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "skinlibrary-loader");
		t.setDaemon(true);
		return t;
	});

	SkinPreview(String source) {
		CompletableFuture.runAsync(() -> load(source), POOL);
	}

	private static String sha1(String s) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (byte b : java.security.MessageDigest.getInstance("SHA-1").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
			sb.append(String.format("%02x", b));
		return sb.toString();
	}

	private void load(String source) {
		try {
			if (DemoSkins.isDemo(source)) { // встроенные демо-скины для гайда
				skin = DemoSkins.make(source);
				state = State.READY;
				return;
			}
			BufferedImage img;
			if (SkinResolver.isRemote(source)) {
				// кэш на диске: не качаем и не дёргаем MineSkin при каждом запуске
				Path cache = SkinStore.DIR.resolve("cache").resolve(sha1(source) + ".png");
				img = null;
				if (Files.isRegularFile(cache)) {
					try {
						img = SkinPng.readSafe(cache.toFile());
					} catch (Exception ignored) {
					}
					if (img == null) Files.deleteIfExists(cache); // битый файл — скачаем заново
				}
				if (img == null) {
					byte[] bytes = SkinResolver.fetchBytes(SkinResolver.resolve(source));
					img = SkinPng.readSafe(new ByteArrayInputStream(bytes));
					if (img != null) {
						try {
							Files.createDirectories(cache.getParent());
							Files.write(cache, bytes);
						} catch (Exception ignored) {
						}
					}
				}
			} else {
				img = SkinPng.readSafe(Path.of(source).toFile());
			}
			if (img == null) throw new IOException(I18n.t("это не изображение"));
			skin = convert(img);
			state = State.READY;
		} catch (Exception e) {
			error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			state = State.FAILED;
		}
	}

	/** Приводит любой скин (64x64, 64x32, HD) к 64x64 ARGB; базовые слои делает непрозрачными. */
	static int[] convert(BufferedImage img) throws IOException {
		int w = img.getWidth(), h = img.getHeight();
		if (w < 64 || h < 32) throw new IOException(I18n.t("слишком маленькое изображение ") + w + "x" + h);
		boolean legacy = h * 2 == w;
		double scale = w / 64.0;
		int[] t = new int[64 * 64];
		int rows = legacy ? 32 : 64;
		for (int y = 0; y < rows; y++) {
			for (int x = 0; x < 64; x++) {
				int sx = Math.min(w - 1, (int) (x * scale));
				int sy = Math.min(h - 1, (int) (y * scale));
				t[y * 64 + x] = img.getRGB(sx, sy);
			}
		}
		if (legacy) {
			// ноги
			copyFlip(t, 4, 16, 20, 48, 4, 4);
			copyFlip(t, 8, 16, 24, 48, 4, 4);
			copyFlip(t, 0, 20, 24, 52, 4, 12);
			copyFlip(t, 4, 20, 20, 52, 4, 12);
			copyFlip(t, 8, 20, 16, 52, 4, 12);
			copyFlip(t, 12, 20, 28, 52, 4, 12);
			// руки
			copyFlip(t, 44, 16, 36, 48, 4, 4);
			copyFlip(t, 48, 16, 40, 48, 4, 4);
			copyFlip(t, 40, 20, 40, 52, 4, 12);
			copyFlip(t, 44, 20, 36, 52, 4, 12);
			copyFlip(t, 48, 20, 32, 52, 4, 12);
			copyFlip(t, 52, 20, 44, 52, 4, 12);
		}
		// базовые слои — всегда непрозрачные (как в игре)
		opaque(t, 0, 0, 32, 16);
		opaque(t, 0, 16, 64, 16);
		opaque(t, 16, 48, 32, 16);
		return t;
	}

	private static void copyFlip(int[] t, int sx, int sy, int dx, int dy, int w, int h) {
		for (int j = 0; j < h; j++)
			for (int i = 0; i < w; i++)
				t[(dy + j) * 64 + dx + i] = t[(sy + j) * 64 + sx + (w - 1 - i)];
	}

	private static void opaque(int[] t, int x0, int y0, int w, int h) {
		for (int y = y0; y < y0 + h; y++)
			for (int x = x0; x < x0 + w; x++)
				t[y * 64 + x] |= 0xFF000000;
	}

	/** Старый вариант: spin=true — модель вращается сама. */
	public void draw(GuiGraphics g, int x, int y, boolean slim, boolean spin) {
		draw(g, x, y, slim, spin ? (System.currentTimeMillis() % 7200L) / 20f : 25f, Pose.STAND, "main");
	}

	public void draw(GuiGraphics g, int x, int y, boolean slim, float yaw, Pose pose) {
		draw(g, x, y, slim, yaw, pose, "main");
	}

	/**
	 * Рисует превью в точке (x, y), размер W x H, с заданным поворотом и позой.
	 * slot — название «места показа»: у каждого места своя текстура.
	 */
	public void draw(GuiGraphics g, int x, int y, boolean slim, float yaw, Pose pose, String slot) {
		Minecraft mc = Minecraft.getInstance();
		if (state != State.READY) {
			String msg = state == State.LOADING ? I18n.t("Загрузка...") : I18n.t("Ошибка");
			g.drawCenteredString(mc.font, msg, x + W / 2, y + H / 2 - 4, state == State.LOADING ? 0xFFAAAAAA : 0xFFFF5555);
			if (state == State.FAILED && !error.isEmpty()) {
				String e = error.length() > 16 ? error.substring(0, 16) + "…" : error;
				g.drawCenteredString(mc.font, e, x + W / 2, y + H / 2 + 6, 0xFF999999);
			}
			return;
		}
		View v = views.computeIfAbsent(slot, k -> new View());
		boolean anim = pose != Pose.STAND;
		int key = anim ? (int) (System.nanoTime() / 33_000_000L) : Math.round(yaw * 2f); // анимация ~30 кадров/с
		if (v.texture == null || key != v.lastKey || slim != v.lastSlim || pose != v.lastPose) {
			ensureTexture(v);
			double time = System.nanoTime() / 1e9;
			SkinRenderer.render(skin, slim, yaw, 12, 4.3, pose, time, W, H, SCRATCH_C, SCRATCH_Z);
			NativeImage px = v.texture.getPixels();
			for (int yy = 0; yy < H; yy++)
				for (int xx = 0; xx < W; xx++)
					px.setPixel(xx, yy, SCRATCH_C[yy * W + xx]);
			v.texture.upload();
			v.lastKey = key;
			v.lastSlim = slim;
			v.lastPose = pose;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, v.id, x, y, 0f, 0f, W, H, W, H);
	}

	private void ensureTexture(View v) {
		if (v.texture != null) return;
		NativeImage img = new NativeImage(W, H, true);
		v.texture = new DynamicTexture(() -> "skinlibrary_preview", img);
		v.id = Identifier.fromNamespaceAndPath("skinlibrary", "preview/" + IDS.incrementAndGet());
		Minecraft.getInstance().getTextureManager().register(v.id, v.texture);
	}

	public void close() {
		for (View v : views.values()) {
			if (v.texture != null) Minecraft.getInstance().getTextureManager().release(v.id);
		}
		views.clear();
	}
}
