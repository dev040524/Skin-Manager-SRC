package dev.skinlibrary.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Растр {@link Pix}, загруженный в динамическую текстуру: фон рисуется одним blit вместо тысяч fill. */
final class Bake extends Pix {
	private static int ids;
	private final DynamicTexture tex;
	private final Identifier id;

	Bake(int w, int h) {
		super(w, h);
		tex = new DynamicTexture(() -> "skinlibrary_bake", new NativeImage(w, h, true));
		id = Identifier.fromNamespaceAndPath("skinlibrary", "bake/" + (++ids));
		Minecraft.getInstance().getTextureManager().register(id, tex);
	}

	void upload() {
		NativeImage px = tex.getPixels();
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px.setPixel(x, y, p[y * w + x]);
		tex.upload();
	}

	void close() {
		tex.close();
	}

	/** Рисует текстуру в прямоугольник dw x dh с левым верхним углом (x, y). */
	void draw(GuiGraphics g, float x, float y, float dw, float dh) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(dw / w, dh / h);
		g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, w, h, w, h);
		g.pose().popMatrix();
	}
}
