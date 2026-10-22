package dev.skinlibrary;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** 3D-превью плаща (вращающаяся пластина). Рисовать только из render-потока. Размер буфера W x H, как у превью скина. */
public final class CapeView {
	public static final int W = SkinPreview.W, H = SkinPreview.H;

	private static final AtomicInteger IDS = new AtomicInteger();
	private static final int[] SCRATCH_C = new int[W * H];
	private static final float[] SCRATCH_Z = new float[W * H];

	private static final class View {
		DynamicTexture texture;
		Identifier id;
		long lastKey = Long.MIN_VALUE;
		float lastYaw = Float.NaN;
	}

	private static final Map<String, View> VIEWS = new HashMap<>();

	private CapeView() {}

	/** Рисует плащ в точке (0,0) текущей матрицы (масштаб задаёт вызывающий). yaw — поворот вокруг вертикальной оси, градусы. */
	public static void draw(GuiGraphics g, Capes.Cape c, float yaw) {
		Minecraft mc = Minecraft.getInstance();
		if (c.tex == null) {
			Capes.loadTexture(c);
			boolean failed = c.url == null || c.url.isBlank();
			String msg = failed ? (c.name.isEmpty() ? "?" : c.name.substring(0, 1).toUpperCase()) : I18n.t("Загрузка...");
			g.drawCenteredString(mc.font, msg, W / 2, H / 2 - 4, 0xFFAAAAAA);
			return;
		}
		View v = VIEWS.computeIfAbsent(c.id, k -> new View());
		long key = System.nanoTime() / 30_000_000L; // не чаще ~33 раз в секунду
		if (v.texture == null || (key != v.lastKey && !(Math.abs(yaw - v.lastYaw) < 0.25f))) { // неподвижный плащ не перерисовываем
			ensureTexture(v);
			SkinRenderer.renderCape(c.tex, yaw, 8, 8.6, W, H, SCRATCH_C, SCRATCH_Z);
			NativeImage px = v.texture.getPixels();
			for (int y = 0; y < H; y++)
				for (int x = 0; x < W; x++)
					px.setPixel(x, y, SCRATCH_C[y * W + x]);
			v.texture.upload();
			v.lastKey = key;
			v.lastYaw = yaw;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, v.id, 0, 0, 0f, 0f, W, H, W, H);
	}

	private static void ensureTexture(View v) {
		if (v.texture != null) return;
		NativeImage img = new NativeImage(W, H, true);
		v.texture = new DynamicTexture(() -> "skinlibrary_cape", img);
		v.id = Identifier.fromNamespaceAndPath("skinlibrary", "cape/" + IDS.incrementAndGet());
		Minecraft.getInstance().getTextureManager().register(v.id, v.texture);
	}
}
