package dev.skinlibrary;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Приводит PNG скина к стандартному виду 64x64 (старые 64x32 и HD-скины конвертируются) перед загрузкой в MineSkin:
 * так «не все скины надеваются» из-за нестандартного размера больше не случается.
 */
public final class SkinPng {
	/** Чтение PNG с проверкой размеров до декодирования: маленький файл не должен раскрываться в гигабайты. */
	public static BufferedImage readSafe(Object src) throws java.io.IOException {
		try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(src)) {
			if (in == null) return null;
			java.util.Iterator<javax.imageio.ImageReader> it = ImageIO.getImageReaders(in);
			if (!it.hasNext()) return null;
			javax.imageio.ImageReader rd = it.next();
			try {
				rd.setInput(in, true, true);
				if (rd.getWidth(0) > 2048 || rd.getHeight(0) > 2048) throw new java.io.IOException("image too large");
				return rd.read(0);
			} finally {
				rd.dispose();
			}
		}
	}

	private SkinPng() {}

	public static byte[] normalize(byte[] png) throws IOException {
		BufferedImage img = SkinPng.readSafe(new ByteArrayInputStream(png));
		if (img == null) throw new IOException(I18n.t("это не изображение"));
		if (img.getWidth() == 64 && img.getHeight() == 64) return png;
		int[] t = SkinPreview.convert(img); // 64x64 ARGB (внутри проверяется допустимый размер)
		BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		out.setRGB(0, 0, 64, 64, t, 0, 64);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(out, "png", bos);
		return bos.toByteArray();
	}
}
