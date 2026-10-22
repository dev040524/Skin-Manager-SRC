package dev.skinlibrary;

/** Поза модели в превью. */
public enum Pose {
	STAND("stand", "Стоит"),
	WALK("walk", "Идёт"),
	RUN("run", "Бежит"),
	WAVE("wave", "Машет"),
	DANCE("dance", "Танцует"),
	SIT("sit", "Сидит"),
	CROUCH("crouch", "Крадётся"),
	ZOMBIE("zombie", "Зомби"),
	FLY("fly", "Полёт");

	public final String id;
	public final String label;

	Pose(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public static Pose current() {
		String id = SkinStore.poseId();
		for (Pose p : values()) if (p.id.equals(id)) return p;
		return STAND;
	}

	public static Pose next() {
		Pose[] v = values();
		return v[(current().ordinal() + 1) % v.length];
	}
}
