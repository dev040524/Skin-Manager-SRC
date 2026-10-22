package dev.skinlibrary;

import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Автонадевание: выбранный в меню скин применяется при входе на сервер или смене сервера,
 * но только если на сервере есть SkinRestorer (команда /skin в дереве команд) и этот скин ещё не надет на этом сервере.
 */
public final class AutoWear {
	private static boolean waiting;
	private static long joinedAt;
	private static SkinEntry pending;
	private static String pendingKey = "";

	private AutoWear() {}

	public static void onJoin(Minecraft mc) {
		waiting = true;
		joinedAt = System.nanoTime();
		pending = null;
	}

	public static void onDisconnect() {
		waiting = false;
		pending = null;
	}

	public static String serverKey(Minecraft mc) {
		try {
			ServerData sd = mc.getCurrentServer();
			if (sd != null && sd.ip != null) return sd.ip.toLowerCase();
		} catch (Throwable ignored) {
		}
		return "local";
	}

	/** Есть ли на сервере SkinRestorer: смотрим команды /skin, /skinrestorer или /sr в дереве команд. */
	private static boolean hasSkinRestorer(RootCommandNode<?> root) {
		return root.getChild("skin") != null || root.getChild("skinrestorer") != null || root.getChild("sr") != null;
	}

	/**
	 * Случайный скин по настройке: при каждом входе или «скин дня» (один и тот же в течение суток).
	 * Берётся из выбранной папки (или из всех/избранных). Выбранный скин становится активным.
	 */
	/** Скин дня из пула: один и тот же в течение суток (запоминается в настройках). */
	public static SkinEntry dailyPick(java.util.List<SkinEntry> pool) {
		if (pool.isEmpty()) return null;
		String today = java.time.LocalDate.now().toString();
		SkinEntry pick = null;
		if (today.equals(SkinStore.dailyDate())) {
			for (SkinEntry e : pool) if (e.id.equals(SkinStore.dailyId())) pick = e;
		}
		if (pick == null) {
			java.util.Random r = new java.util.Random(today.hashCode() * 31L + pool.size());
			pick = pool.get(r.nextInt(pool.size()));
			SkinStore.setDaily(today, pick.id);
		}
		return pick;
	}

	public static SkinEntry pickRandom() {
		String mode = SkinStore.randomMode();
		if (mode.equals("off")) return null;
		java.util.List<SkinEntry> pool = SkinStore.randomPool();
		if (pool.isEmpty()) return null;
		SkinEntry pick;
		if (mode.equals("daily")) {
			pick = dailyPick(pool);
		} else {
			SkinEntry cur = SkinStore.chosenEntry();
			do {
				pick = pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
			} while (pool.size() > 1 && pick == cur);
		}
		SkinStore.setActive(pick);
		return pick;
	}

	public static void tick(Minecraft mc) {
		if (mc.getConnection() == null) {
			waiting = false;
			pending = null;
			return;
		}
		if (waiting) {
			RootCommandNode<?> root = mc.getConnection().getCommands().getRoot();
			if (root.getChildren().isEmpty()) { // дерево команд ещё не пришло
				if (System.nanoTime() - joinedAt > 15_000_000_000L) waiting = false;
				return;
			}
			waiting = false;
			if (!SkinStore.autoApply() || mc.hasSingleplayerServer()) return;
			if (!hasSkinRestorer(root)) return;           // плагина нет — команду не отправляем
			SkinEntry e = pickRandom();
			if (e == null) e = SkinStore.chosenEntry();
			if (e == null) return;
			String key = serverKey(mc);
			if (e.id.equals(SkinStore.appliedOn(key))) return; // этот скин уже надет на этом сервере
			pending = e;
			pendingKey = key;
		}
		if (pending != null) {
			if (SkinLibraryClient.cooldownLeft() > 0 || SkinLibraryClient.isBusy()) return; // подождём, не теряя запрос
			SkinEntry e = pending;
			pending = null;
			SkinLibraryClient.applyQuiet(e, pendingKey);
		}
	}
}
