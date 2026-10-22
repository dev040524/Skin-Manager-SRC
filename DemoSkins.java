package dev.skinlibrary;

/** Встроенные демо-скины для гайда: рисуются кодом, внешних файлов не нужно. Источник «demo:N». */
public final class DemoSkins {
	public static final String[] NAMES = {"Sand Golem", "Scarecrow", "Iron Golem", "Hay Golem", "Ancient Golem", "Golem"};
	/** Номер встроенного скина-героя гайда (из ресурсов мода). */
	public static final int HERO = 5;
	// {волосы, кожа, рубашка, штаны, обувь}
	private static final int[][] PALETTE = {
			{0xFF4A3220, 0xFFD9A877, 0xFF3A6FD0, 0xFF2B2F6B, 0xFF4B4B4B},
			{0xFF8A4B1F, 0xFFE8B896, 0xFF3FA05A, 0xFF6B4A2B, 0xFF3A2A1A},
			{0xFF222222, 0xFFD9A877, 0xFF9AA0B0, 0xFF4A4F5E, 0xFF2A2A2A},
			{0xFF1B1B2B, 0xFFE0B48C, 0xFF7B3FD0, 0xFF22223A, 0xFFEFEFEF},
			{0xFFE8F4FF, 0xFFF1D2B8, 0xFF4DD0E1, 0xFF2C5F8A, 0xFFDDEEFF},
			{0xFFFF8AC8, 0xFFF1CFB5, 0xFFFFD83B, 0xFF1FA2B3, 0xFFFFFFFF},
	};

	private DemoSkins() {}

	public static boolean isDemo(String source) {
		return source != null && source.startsWith("demo:");
	}

	private static void fill(int[] t, int x0, int y0, int x1, int y1, int c) {
		for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) t[y * 64 + x] = c;
	}

	/** Скин-герой гайда: PNG из ресурсов мода (assets/skinlibrary/demo_skin.png). */
	private static int[] loadHero() {
		return load("demo_skin.png");
	}

	private static int[] load(String res) {
		try (java.io.InputStream in = DemoSkins.class.getResourceAsStream("/assets/skinlibrary/" + res)) {
			if (in == null) return null;
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
			if (img == null || img.getWidth() != 64 || img.getHeight() != 64) return null;
			int[] t = new int[64 * 64];
			img.getRGB(0, 0, 64, 64, t, 0, 64);
			opaque(t, 0, 0, 32, 16);   // базовые слои — непрозрачные, как в игре
			opaque(t, 0, 16, 64, 16);
			opaque(t, 16, 48, 32, 16);
			return t;
		} catch (Exception e) {
			return null;
		}
	}

	private static void opaque(int[] t, int x0, int y0, int w, int h) {
		for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) t[y * 64 + x] |= 0xFF000000;
	}

	/** Текстура 64x64 (ARGB) для демо-скина с номером из строки «demo:N». */
	public static int[] make(String source) {
		int n;
		try {
			n = Math.floorMod(Integer.parseInt(source.substring(5)), NAMES.length);
		} catch (Exception e) {
			n = 0;
		}
		if (n == HERO) {
			int[] hero = loadHero();
			if (hero != null) return hero;
			n = 0;
		}
		if (n < HERO) {
			int[] file = load("demo_skin_" + n + ".png");
			if (file != null) return file;
		}
		int[] p = PALETTE[Math.min(n, PALETTE.length - 1)];
		int hair = p[0], skin = p[1], shirt = p[2], pants = p[3], shoes = p[4];
		int[] t = new int[64 * 64];

		fill(t, 0, 0, 32, 16, skin);                       // голова: все грани
		fill(t, 8, 0, 24, 8, hair);                        // верх и низ головы
		fill(t, 0, 8, 32, 10, hair);                       // волосы по бокам
		if (n == 1 || n == 5) fill(t, 0, 10, 8, 14, hair); // длинные волосы
		fill(t, 10, 12, 12, 14, 0xFFFFFFFF);               // глаза (лицевая грань 8..16 x 8..16)
		fill(t, 13, 12, 15, 14, 0xFFFFFFFF);
		fill(t, 11, 12, 12, 14, 0xFF3A5FCD);
		fill(t, 14, 12, 15, 14, 0xFF3A5FCD);
		fill(t, 11, 14, 13, 15, 0xFF8B5A3C);               // рот

		fill(t, 16, 16, 40, 32, shirt);                    // тело
		fill(t, 40, 16, 56, 32, shirt);                    // правая рука
		fill(t, 40, 28, 56, 32, skin);                     // кисть
		fill(t, 32, 48, 48, 64, shirt);                    // левая рука
		fill(t, 32, 60, 48, 64, skin);
		fill(t, 0, 16, 16, 32, pants);                     // правая нога
		fill(t, 0, 28, 16, 32, shoes);
		fill(t, 16, 48, 32, 64, pants);                    // левая нога
		fill(t, 16, 60, 32, 64, shoes);

		if (n == 2) fill(t, 20, 18, 36, 20, 0xFFD8B04A);   // ремень рыцаря
		if (n == 3) fill(t, 20, 26, 36, 32, 0xFF5A2FA8);   // карман толстовки
		return t;
	}
}
