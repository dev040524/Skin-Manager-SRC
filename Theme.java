package dev.skinlibrary.screen;

import dev.skinlibrary.SkinStore;

import java.util.List;

/** Цветовая тема интерфейса. glowA/glowB — RGB без альфы, остальные цвета — ARGB. */
public record Theme(String id, String name,
					int bgTop, int bgBottom, int glowA, int glowB, int accent,
					int cardTop, int cardBottom, int border, int text, int sub,
					int shelfTop, int shelfBottom, int btnTop, int btnBottom, boolean classic, Look look) {

	/** Фон с эффектом. */
	public enum Bg { PARTICLES, RAIN, STARS, SNOW, GRID, WAVES, EMBERS, PIXELS, VOID, LAVA, NOTEBOOK, SKY,
		TERRARIA, STARDEW, UNDERTALE, HOLLOW, CELESTE, HADES, CUPHEAD,
		DONTSTARVE, HOTLINE, DEADCELLS, CULT, BALATRO, SLAY, AMONGUS }

	/** Форма панелей, карточек и кнопок. */
	public enum Shape { ROUND, SQUARE, CHAMFER, THICK, ORNATE, SKETCH, TERRA, WOOD, BOXED, GILT, TOON }

	/** Украшения панелей и карточек. */
	public enum Deco { NONE, FROST, BRACKETS, STUDS, SYNTH, WAVE, DRIPS, CRACKS, TAPE, GLOSS, ORNAMENT, KEY, NAILS, SPARKLE }

	/** Характер темы: анимированный фон, форма элементов, украшения, линии развёртки, жидкие кнопки. */
	public record Look(Bg bg, Shape shape, Deco deco, boolean scan, boolean liquid) {
		public Look(Bg bg, Shape shape, Deco deco, boolean scan) {
			this(bg, shape, deco, scan, false);
		}

		public static final Look DEFAULT = new Look(Bg.PARTICLES, Shape.ROUND, Deco.NONE, false, false);
	}

	/** Светлая ли тема (по яркости карточки): у светлых тем мягче подсветка кнопок. */
	public boolean light() {
		int c = cardTop;
		double l = (0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF)) / 255.0;
		return l > 0.55;
	}

	/** Классическая тема (без анимаций) или обычная цветовая. */
	public Theme(String id, String name, int bgTop, int bgBottom, int glowA, int glowB, int accent,
				 int cardTop, int cardBottom, int border, int text, int sub,
				 int shelfTop, int shelfBottom, int btnTop, int btnBottom, boolean classic) {
		this(id, name, bgTop, bgBottom, glowA, glowB, accent, cardTop, cardBottom, border, text, sub,
				shelfTop, shelfBottom, btnTop, btnBottom, classic, Look.DEFAULT);
	}

	/** Полноценная тема с характером (фон, форма, украшения). */
	public Theme(String id, String name, Look look, int bgTop, int bgBottom, int glowA, int glowB, int accent,
				 int cardTop, int cardBottom, int border, int text, int sub,
				 int shelfTop, int shelfBottom, int btnTop, int btnBottom) {
		this(id, name, bgTop, bgBottom, glowA, glowB, accent, cardTop, cardBottom, border, text, sub,
				shelfTop, shelfBottom, btnTop, btnBottom, false, look);
	}

	/** Обычный конструктор: тема с анимациями. */
	public Theme(String id, String name, int bgTop, int bgBottom, int glowA, int glowB, int accent,
				 int cardTop, int cardBottom, int border, int text, int sub,
				 int shelfTop, int shelfBottom, int btnTop, int btnBottom) {
		this(id, name, bgTop, bgBottom, glowA, glowB, accent, cardTop, cardBottom, border, text, sub,
				shelfTop, shelfBottom, btnTop, btnBottom, false);
	}

	public static final List<Theme> ALL = List.of(
			new Theme("ps", "PlayStation", 0xC00A1530, 0xE002030A, 0x2A5FFF, 0x7A3CFF, 0xFF4F8CFF,
					0xFF1E2338, 0xFF0B0C16, 0xFF3A3F58, 0xFFFFFFFF, 0xFF8890B0,
					0xFF2B2F45, 0xFF11131F, 0xFF2A3355, 0xFF171C33),
			new Theme("neon", "Неон", 0xC0160020, 0xE0060009, 0xFF2BD6, 0x00E5FF, 0xFFFF3DDC,
					0xFF2A1033, 0xFF0E0414, 0xFF5A2A66, 0xFFFFFFFF, 0xFFC08AD0,
					0xFF3A1A48, 0xFF150820, 0xFF48206A, 0xFF240F38),
			new Theme("sunset", "Закат", 0xC02A0F1E, 0xE0100508, 0xFF7A2A, 0xFF2A6A, 0xFFFF8A3D,
					0xFF3A1E26, 0xFF150A0E, 0xFF6A3A3A, 0xFFFFF4EC, 0xFFD0A08A,
					0xFF4A2A2A, 0xFF1A0E0E, 0xFF5A2E2E, 0xFF2A1414),
			new Theme("forest", "Лес", 0xC00A1E14, 0xE0030A06, 0x2AFF8A, 0x2AA8FF, 0xFF3DDC84,
					0xFF16301F, 0xFF08140C, 0xFF2E5A3A, 0xFFF0FFF4, 0xFF8AC09A,
					0xFF244A30, 0xFF0C1A10, 0xFF24503A, 0xFF123020),
			new Theme("sakura", "Сакура", 0xC02A1020, 0xE0100610, 0xFF8AC8, 0xC88AFF, 0xFFFF8AC8,
					0xFF3A1F33, 0xFF150A14, 0xFF6A3A5A, 0xFFFFF0F8, 0xFFD0A0C0,
					0xFF4A2A44, 0xFF1A0E18, 0xFF5A2E4E, 0xFF2A1426),
			new Theme("classic", "Классика", 0xC0101010, 0xC0101010, 0x808080, 0x808080, 0xFFFFFF55,
					0xFF2A2A2A, 0xFF2A2A2A, 0xFF555555, 0xFFFFFFFF, 0xFFA0A0A0,
					0xFF555555, 0xFF3A3A3A, 0xFF707070, 0xFF707070, true),
			new Theme("mono", "Монохром", 0xC0141414, 0xE0050505, 0xFFFFFF, 0xAAAAAA, 0xFFE0E0E0,
					0xFF2A2A2A, 0xFF0E0E0E, 0xFF505050, 0xFFFFFFFF, 0xFF9A9A9A,
					0xFF3A3A3A, 0xFF161616, 0xFF3A3A3A, 0xFF1C1C1C),

			// ---- «Классика тёмная»: та же ванильная простота, но тёмная палитра
			new Theme("classic_dark", "Классика тёмная", 0xE0060606, 0xE0060606, 0x606060, 0x606060, 0xFFE8C547,
					0xFF1A1A1A, 0xFF1A1A1A, 0xFF3C3C3C, 0xFFEDEDED, 0xFF8C8C8C,
					0xFF3A3A3A, 0xFF222222, 0xFF404040, 0xFF404040, true),

			// ---- анимированные версии классики: те же простые квадратные элементы и палитра, но с живым фоном
			new Theme("classic_anim", "Классика анимированная", new Look(Bg.PARTICLES, Shape.SQUARE, Deco.NONE, false),
					0xFF101010, 0xFF1E1E1E, 0x909090, 0x606060, 0xFFDADADA,
					0xFF2A2A2A, 0xFF2A2A2A, 0xFF555555, 0xFFFFFFFF, 0xFFA0A0A0,
					0xFF555555, 0xFF3A3A3A, 0xFF707070, 0xFF5A5A5A),
			new Theme("classic_dark_anim", "Классика тёмная анимированная", new Look(Bg.STARS, Shape.SQUARE, Deco.NONE, false),
					0xFF060606, 0xFF121212, 0xE8C547, 0x606060, 0xFFE8C547,
					0xFF1A1A1A, 0xFF1A1A1A, 0xFF3C3C3C, 0xFFEDEDED, 0xFF8C8C8C,
					0xFF3A3A3A, 0xFF222222, 0xFF404040, 0xFF303030),

			// ---- полноценные темы: у каждой свой анимированный фон, форма элементов и украшения
			// Кибер: «цифровой дождь», срезанные углы, неоновые уголки, линии развёртки
			new Theme("cyber", "Кибер", new Look(Bg.RAIN, Shape.CHAMFER, Deco.BRACKETS, true),
					0xD0010806, 0xF0000302, 0x00FFB0, 0x00B8FF, 0xFF00FFB0,
					0xFF08201C, 0xFF020807, 0xFF0E5A4A, 0xFFD8FFF4, 0xFF5FB89E,
					0xFF0E3A30, 0xFF03100D, 0xFF0B3A30, 0xFF04140F),
			// Космос: звёздное небо с параллаксом и падающими звёздами
			new Theme("space", "Космос", new Look(Bg.STARS, Shape.ROUND, Deco.NONE, false),
					0xD0050818, 0xF0010208, 0x6A5BFF, 0xB45BFF, 0xFFA98BFF,
					0xFF161433, 0xFF07061A, 0xFF3A3470, 0xFFF2F0FF, 0xFF9A96CC,
					0xFF26224F, 0xFF0A0920, 0xFF2A2660, 0xFF14123A),
			// Зима: снегопад, иней и сосульки на панелях
			new Theme("winter", "Зима", new Look(Bg.SNOW, Shape.ROUND, Deco.FROST, false),
					0xC8102236, 0xE0050B14, 0x9ADCFF, 0xFFFFFF, 0xFF8FE3FF,
					0xFF1D3550, 0xFF0B1826, 0xFF4E7FA8, 0xFFF4FBFF, 0xFF9CC4DE,
					0xFF34587A, 0xFF10202F, 0xFF2E5578, 0xFF152C44),
			// Ретровейв: закат над сеткой, прямые углы, двойная неоновая линия, развёртка
			new Theme("synthwave", "Ретровейв", new Look(Bg.GRID, Shape.SQUARE, Deco.SYNTH, true),
					0xD0180830, 0xF0080214, 0xFF3FA0, 0xFF9A2B, 0xFF35E0FF,
					0xFF2A0F46, 0xFF0F0420, 0xFF8A2BE2, 0xFFFFF0FF, 0xFFC48AE8,
					0xFF43186A, 0xFF150628, 0xFF4A1A7A, 0xFF230A3C),
			// Океан: бегущие волны, пузырьки, лучи света, волнистая подсветка панелей
			new Theme("ocean", "Океан", new Look(Bg.WAVES, Shape.ROUND, Deco.WAVE, false),
					0xC0062A3A, 0xE0020A12, 0x1FD0C0, 0x2A8AFF, 0xFF2FE0D0,
					0xFF0F3A4C, 0xFF06141C, 0xFF2A7088, 0xFFEFFFFF, 0xFF8CC8D8,
					0xFF1A5870, 0xFF08202C, 0xFF1E6078, 0xFF0C2C3C),
			// Таверна: тёмное дерево, золотая рамка с заклёпками, искры от очага
			new Theme("tavern", "Таверна", new Look(Bg.EMBERS, Shape.ORNATE, Deco.STUDS, false),
					0xC8201208, 0xE0080402, 0xFF9A2A, 0xFFC857, 0xFFFFC857,
					0xFF3A2414, 0xFF160C06, 0xFF8A6A32, 0xFFFFF3D8, 0xFFC8A878,
					0xFF5A3A1E, 0xFF22140A, 0xFF5A381C, 0xFF2A1809),
			// Аркада: падающие пиксельные блоки, толстые рамки и жёсткие тени
			new Theme("arcade", "Аркада", new Look(Bg.PIXELS, Shape.THICK, Deco.NONE, false),
					0xD0101030, 0xF0040410, 0x4A7BFF, 0xFF4A6A, 0xFFFFE14A,
					0xFF1C1C48, 0xFF0A0A24, 0xFF5A6AFF, 0xFFFFFFFF, 0xFF9AA4FF,
					0xFF2A2A68, 0xFF101030, 0xFF3A3ACC, 0xFF1E1E80),

			// Тьма: жидкие кнопки-сгустки тянутся к курсору, как феррожидкость к магниту, и капают; из темноты смотрят глаза
			new Theme("void", "Тьма", new Look(Bg.VOID, Shape.ROUND, Deco.DRIPS, false, true),
					0xE0030206, 0xF0000000, 0x5A1FD0, 0xA01030, 0xFF9B5CFF,
					0xFF0E0A16, 0xFF030206, 0xFF2E2250, 0xFFE8E0FF, 0xFF7C6FA0,
					0xFF1A1228, 0xFF07050C, 0xFF21163C, 0xFF0B0716),
			// Лава: каменные панели со светящимися трещинами, лавовая река с пузырями, искры
			new Theme("lava", "Лава", new Look(Bg.LAVA, Shape.CHAMFER, Deco.CRACKS, false),
					0xD0200400, 0xF0060100, 0xFF4A00, 0xFFB000, 0xFFFF6A1A,
					0xFF2A100A, 0xFF0E0403, 0xFF6A2A14, 0xFFFFEDE0, 0xFFC08A70,
					0xFF4A1E10, 0xFF1A0804, 0xFF5A1E0E, 0xFF240A04),
			// Тетрадь (светлая): клетчатый лист с полями и дырками, каракули, липкие листки со скотчем, карандашные рамки
			new Theme("notebook", "Тетрадь", new Look(Bg.NOTEBOOK, Shape.SKETCH, Deco.TAPE, false),
					0xF4F3EFDC, 0xF4E7E1C9, 0xFFD27A, 0x9AD8FF, 0xFFE0443A,
					0xFFFFFDF2, 0xFFF3EDD2, 0xFF7A7F8C, 0xFF22304A, 0xFF6B7690,
					0xFFD8CFA8, 0xFFB5AA82, 0xFFFFF4B8, 0xFFFFE98A),
			// Облака (светлая): утреннее небо, плывущие облака, солнце с лучами, птицы, глянцевые панели
			new Theme("sky", "Облака", new Look(Bg.SKY, Shape.ROUND, Deco.GLOSS, false),
					0xF08CCBFF, 0xF0E6F4FF, 0xFFD27A, 0x9AD8FF, 0xFFFF9A3D,
					0xFFFFFFFF, 0xFFE2F0FF, 0xFFA8CFEF, 0xFF1D3B5C, 0xFF5F82A3,
					0xFFBFDDF5, 0xFF8FBDE0, 0xFFFFFFFF, 0xFFD6EAFB),

			// ---- темы по мотивам инди-игр
			new Theme("terraria", "Terraria", new Look(Bg.TERRARIA, Shape.TERRA, Deco.NONE, false),
					0xFF2D5AD0, 0xFF86B8E8, 0x3F52A0, 0x2A3A82, 0xFFFFE04A,
					0xE04658AE, 0xE02A3A82, 0xFF0E1650, 0xFFFFFFFF, 0xFFB8C4F0,
					0xFF3A4C9E, 0xFF22306E, 0xFF4A5DB5, 0xFF2B3C8A),
			new Theme("stardew", "Stardew Valley", new Look(Bg.STARDEW, Shape.WOOD, Deco.NAILS, false),
					0xFF0A64C0, 0xFF68E0E0, 0xFFD060, 0x8AD06A, 0xFFE8892B,
					0xFFF2B866, 0xFFE8A24C, 0xFF5B2B06, 0xFF5B2B06, 0xFF8A5220,
					0xFFE0A860, 0xFFC4883A, 0xFFF0B25A, 0xFFDC9238),
			new Theme("undertale", "Undertale", new Look(Bg.UNDERTALE, Shape.BOXED, Deco.NONE, false),
					0xFF000000, 0xFF000000, 0x202020, 0x101010, 0xFFFFE600,
					0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFA0A0A0,
					0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000),
			new Theme("hollow", "Hollow Knight", new Look(Bg.HOLLOW, Shape.ORNATE, Deco.ORNAMENT, false),
					0xFF1A1C1E, 0xFF0A0B0C, 0x808890, 0x404448, 0xFFFFFFFF,
					0xE02A2C2E, 0xE0141516, 0xFFC8CACC, 0xFFFFFFFF, 0xFF9A9EA2,
					0xFF2A2C2E, 0xFF121314, 0xFF303234, 0xFF161718),
			new Theme("celeste", "Celeste", new Look(Bg.CELESTE, Shape.ROUND, Deco.SPARKLE, false),
					0xFF14305E, 0xFF4878A0, 0x7571AC, 0x4F8CB8, 0xFF8AF07A,
					0xD0243070, 0xD0141C48, 0xFF9AA8E8, 0xFFFFFFFF, 0xFFB0B8E8,
					0xFF2B3A78, 0xFF1A2454, 0xFF2E3C80, 0xFF1C2658),
			new Theme("hades", "Hades", new Look(Bg.HADES, Shape.SQUARE, Deco.NONE, false),
					0xFF14111C, 0xFF050407, 0x4A3A7A, 0x7A1A10, 0xFFE8B84A,
					0xE0141018, 0xE00A080C, 0xFF7A6A52, 0xFFEDE6D6, 0xFF9A8E78,
					0xFF1C1822, 0xFF0C0A10, 0xFF241E2C, 0xFF100C14),
			new Theme("cuphead", "Cuphead", new Look(Bg.CUPHEAD, Shape.SQUARE, Deco.NONE, false),
					0xFF060605, 0xFF060605, 0x8CA8BA, 0x404850, 0xFFFFFFFF,
					0xE0141413, 0xE00A0A09, 0xFF6A665C, 0xFFF0EEE6, 0xFF8C8272,
					0xFF1A1A18, 0xFF0C0C0B, 0xFF1E1E1C, 0xFF101010),

			// ---- ещё темы по мотивам игр
			new Theme("dontstarve", "Don't Starve", new Look(Bg.DONTSTARVE, Shape.ORNATE, Deco.NONE, false),
					0xFF0E0B08, 0xFF2B2016, 0x8A6A3A, 0x4A3A22, 0xFFE8C878,
					0xE02A2118, 0xE0150F0A, 0xFF8A6E48, 0xFFEAD8B8, 0xFFA89878,
					0xFF2E241A, 0xFF150F0A, 0xFF3A2E20, 0xFF1E1610),
			new Theme("hotline", "Hotline Miami", new Look(Bg.HOTLINE, Shape.THICK, Deco.NONE, true),
					0xFF1A0B33, 0xFF8A1A6A, 0xFF3EA5, 0x00F0FF, 0xFFFF3EA5,
					0xE0240F44, 0xE0120622, 0xFF00F0FF, 0xFFFFFFFF, 0xFFC8A0F0,
					0xFF381A60, 0xFF180A30, 0xFF4A1A7A, 0xFF260C44),
			new Theme("deadcells", "Dead Cells", new Look(Bg.DEADCELLS, Shape.CHAMFER, Deco.NONE, false),
					0xFF0C0E16, 0xFF181C2A, 0xFF6A1A, 0x2A4A8A, 0xFFFF8A2A,
					0xE01C2030, 0xE00C0E16, 0xFF4A5470, 0xFFE8E4DC, 0xFF8A90A8,
					0xFF242A3C, 0xFF10131C, 0xFF2A3248, 0xFF141824),
			new Theme("cult", "Cult of the Lamb", new Look(Bg.CULT, Shape.ROUND, Deco.NONE, false),
					0xFF2A0A0E, 0xFF080204, 0xC02020, 0x601020, 0xFFE83A3A,
					0xE0240C10, 0xE0120508, 0xFF8A2A30, 0xFFF4E4D0, 0xFFB08A82,
					0xFF301014, 0xFF14060A, 0xFF4A1418, 0xFF260A0E),
			new Theme("balatro", "Balatro", new Look(Bg.BALATRO, Shape.TOON, Deco.NONE, false),
					0xFF0E4A44, 0xFF2A0A0A, 0xE84A3A, 0x2A9A8A, 0xFFFFC83A,
					0xF0143A38, 0xF00A2220, 0xFFF4F0E8, 0xFFFFFFFF, 0xFFB8D8D0,
					0xFF1A5A54, 0xFF0A2A28, 0xFFE84A3A, 0xFFB82E22),
			new Theme("slay", "Slay the Spire", new Look(Bg.SLAY, Shape.GILT, Deco.NONE, false),
					0xFF14161E, 0xFF05050A, 0xB04A20, 0x3A3A5A, 0xFFFFD060,
					0xE01C1E26, 0xE00C0D12, 0xFF8A7448, 0xFFEAE4D4, 0xFF9A9486,
					0xFF262830, 0xFF101116, 0xFF30323C, 0xFF181A20),
			new Theme("amongus", "Among Us", new Look(Bg.AMONGUS, Shape.ROUND, Deco.NONE, false),
					0xFF04060C, 0xFF0C1020, 0x2A3A8A, 0x6A2A5A, 0xFFC51111,
					0xE02A3040, 0xE0181C28, 0xFF9AA4B8, 0xFFFFFFFF, 0xFF9AA4B8,
					0xFF2E3446, 0xFF161A24, 0xFF3A4258, 0xFF222838),

			// ---- цветовые темы
			new Theme("mint", "Мята", 0xC00A2420, 0xE0030A09, 0x2AFFC8, 0x2AB8FF, 0xFF3DFFC4,
					0xFF14342F, 0xFF08140F, 0xFF2E6A5E, 0xFFEFFFFA, 0xFF8AC8BA,
					0xFF1F4A42, 0xFF0C1C18, 0xFF1F5A4E, 0xFF103028),
			new Theme("lavender", "Лаванда", 0xC0201A3A, 0xE0080614, 0xB28AFF, 0x8AA8FF, 0xFFB69CFF,
					0xFF2A2250, 0xFF100C26, 0xFF54489A, 0xFFF4F0FF, 0xFFA89CD8,
					0xFF3A3070, 0xFF161034, 0xFF40368A, 0xFF221A52),
			new Theme("burgundy", "Бордо", 0xC02A0A14, 0xE0100308, 0xC0204A, 0xFF4A6A, 0xFFFF4A6E,
					0xFF3A1420, 0xFF16060C, 0xFF7A2A3E, 0xFFFFEFF2, 0xFFD09AA6,
					0xFF4A1C2A, 0xFF1E0A10, 0xFF5A1E30, 0xFF2E0E18),
			new Theme("amber", "Янтарь", 0xC02A1A06, 0xE0100904, 0xFFB02A, 0xFF8A2A, 0xFFFFB32A,
					0xFF3A2A10, 0xFF161006, 0xFF7A5A20, 0xFFFFF6E0, 0xFFD0B078,
					0xFF4A3814, 0xFF1E1608, 0xFF5A4216, 0xFF2E200A),
			new Theme("graphite", "Графит", 0xC0161618, 0xE0060607, 0x8A8A9A, 0x5A5A6A, 0xFFE0E0EC,
					0xFF26262A, 0xFF0E0E10, 0xFF48484E, 0xFFF2F2F6, 0xFF9A9AA4,
					0xFF34343A, 0xFF121214, 0xFF3A3A42, 0xFF1E1E22),
			new Theme("indigo", "Индиго", 0xC0101040, 0xE0040414, 0x3A3AFF, 0x2A8AFF, 0xFF6A7AFF,
					0xFF1A1E5A, 0xFF080A28, 0xFF3A44A8, 0xFFF0F2FF, 0xFF9AA4E0,
					0xFF262C7A, 0xFF0C1030, 0xFF2C34A0, 0xFF161C5A),
			new Theme("turquoise", "Бирюза", 0xC00A2630, 0xE0030B0F, 0x2AE0FF, 0x2AFFD0, 0xFF2AD8F0,
					0xFF10343E, 0xFF061418, 0xFF2A7A8A, 0xFFEFFEFF, 0xFF8AC8D4,
					0xFF1A4A58, 0xFF0A1E24, 0xFF1A6076, 0xFF0E3444),
			new Theme("olive", "Оливка", 0xC0181A08, 0xE0070803, 0xA8C02A, 0x6A8A2A, 0xFFB8D44A,
					0xFF2A3010, 0xFF101404, 0xFF5A6A20, 0xFFF6FAE0, 0xFFB0BC80,
					0xFF3A4416, 0xFF161A08, 0xFF48561A, 0xFF262E0C),
			new Theme("coffee", "Кофе", 0xC0241608, 0xE0100804, 0xB07A3A, 0x8A5A2A, 0xFFD8A060,
					0xFF3A2616, 0xFF180E06, 0xFF6A4A2E, 0xFFFFF2E4, 0xFFC0A088,
					0xFF4A301C, 0xFF1E1208, 0xFF5A3A22, 0xFF2E1C0E),
			new Theme("rose", "Розовый кварц", 0xE0FFE6EE, 0xF0F8C6D6, 0xFF8AB4, 0xFFB8D0, 0xFFE0508A,
					0xFFFFF0F4, 0xFFFAD6E0, 0xFFE0A0B4, 0xFF4A1A2C, 0xFF9A5A70,
					0xFFF6C8D6, 0xFFE8A6BC, 0xFFFFFFFF, 0xFFFAD4E0),
			new Theme("ice", "Лёд", 0xE0E8F6FF, 0xF0B8D8F0, 0x6ACBFF, 0xA8E0FF, 0xFF2A9AE0,
					0xFFF2FAFF, 0xFFD6ECFA, 0xFFA0C8E0, 0xFF123048, 0xFF587A94,
					0xFFCCE4F4, 0xFF9CC4E0, 0xFFFFFFFF, 0xFFD0E8F8),
			new Theme("vanilla", "Ваниль", 0xE0FFF6DC, 0xF0EAD8A8, 0xFFD060, 0xFFE8A0, 0xFFD89A20,
					0xFFFFFAEA, 0xFFF4E6C0, 0xFFD8C08A, 0xFF3A2C10, 0xFF8A7448,
					0xFFF0E0B0, 0xFFD8C080, 0xFFFFFFF0, 0xFFF2E2B8)
	);

	private static Theme cached;
	private static String cachedId;

	/** Текущая тема (кэшируется: вызывается сотни раз за кадр). */
	public static Theme current() {
		String id = SkinStore.themeId();
		if (cached != null && id.equals(cachedId)) return cached;
		Theme found = ALL.get(0);
		for (Theme t : ALL) {
			if (t.id().equals(id)) {
				found = t;
				break;
			}
		}
		cached = found;
		cachedId = id;
		return found;
	}

	/** Вкладка галереи: 0 — полные темы (свой фон и форма), 1 — цветовые, 2 — классика, 3 — по мотивам игр. */
	public int group() {
		if (classic || id.startsWith("classic")) return 2; // вкладка «Классика» (в т.ч. анимированные версии)
		if (look.bg().ordinal() >= Bg.TERRARIA.ordinal()) return 3;
		return look.equals(Look.DEFAULT) ? 1 : 0;
	}

	public static Theme next() {
		int i = ALL.indexOf(current());
		return ALL.get((i + 1) % ALL.size());
	}
}
