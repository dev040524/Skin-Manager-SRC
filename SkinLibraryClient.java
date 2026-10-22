package dev.skinlibrary;

import dev.skinlibrary.screen.MenuOverlay;
import dev.skinlibrary.screen.Notify;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class SkinLibraryClient implements ClientModInitializer {
	private static volatile boolean busy;
	private static volatile long cooldownUntil;
	private static boolean cooling;
	private static volatile long lastSendNs;
	private static boolean guideRequested;
	private static int langTicks;

	@Override
	public void onInitializeClient() {
		SkinStore.load();
		CustomSounds.load();

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof PauseScreen) MenuOverlay.attach(screen, "pause");
			else if (screen instanceof TitleScreen) {
				MenuOverlay.attach(screen, "title");
				if (!SkinStore.guideSeen() && !guideRequested) { // первый запуск: плавно открываем гайд
					guideRequested = true;
					MenuOverlay.requestGuide(screen);
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(MenuOverlay::tick);
		ClientTickEvents.END_CLIENT_TICK.register(Sfx::pump);

		// автонадевание скина при входе на сервер / смене сервера
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> AutoWear.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AutoWear.onDisconnect());
		ClientTickEvents.END_CLIENT_TICK.register(AutoWear::tick);

		// ответ плагина на нашу команду показываем уведомлением: видно, почему скин не наделся
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (System.nanoTime() - lastSendNs > 6_000_000_000L) return;
			String text = message.getString();
			if (!text.toLowerCase().contains("skin")) return;
			String t = text.replaceAll("\\s+", " ").trim();
			Notify.show("SkinsRestorer", t.length() > 64 ? t.substring(0, 64) + "…" : t);
		});

		// уведомление, когда снова можно менять скин; обновление языка интерфейса
		ClientTickEvents.END_CLIENT_TICK.register(SkinLibraryClient::tickMisc);
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> Notify.render(graphics));
		I18n.refresh();

		// автоматический поиск скинов лицензионного аккаунта и лаунчеров
		LicenseSync.sync(false);
	}

	private static void tickMisc(Minecraft mc) {
		if (cooldownLeft() > 0) {
			cooling = true;
		} else if (cooling) {
			cooling = false;
			if (SkinStore.notifyCooldown() && mc.player != null) {
				Notify.show("Skin Library", I18n.t("Скин снова можно менять"));
			}
		}
		if (++langTicks >= 40) { // язык «Авто» следует за языком игры
			langTicks = 0;
			I18n.refresh();
		}
	}

	// ------------------------------------------------------------------ откат между надеваниями

	/** Сколько секунд осталось до следующего надевания (в SkinRestorer есть КД на команду /skin). */
	public static int cooldownLeft() {
		long ms = (cooldownUntil - System.nanoTime()) / 1_000_000L;
		return ms > 0 ? (int) Math.ceil(ms / 1000.0) : 0;
	}

	private static void startCooldown() {
		int sec = SkinStore.cooldownSec();
		if (sec > 0) cooldownUntil = System.nanoTime() + sec * 1_000_000_000L;
	}

	// ------------------------------------------------------------------ надевание

	public static void wear(SkinEntry entry) {
		wear(entry, null);
	}

	/**
	 * Надевает скин вручную. Вне сервера только выбирает скин (он применится при входе).
	 * Если нужна загрузка (файл из лаунчера, скин с плащом) — делает её сам.
	 */
	public static void wear(SkinEntry e, Consumer<String> status) {
		Minecraft mc = Minecraft.getInstance();
		SkinStore.setActive(e);
		if (mc.getConnection() == null) {
			if (status != null) status.accept(I18n.t("Скин выбран: применится при входе на сервер"));
			return;
		}
		int cd = cooldownLeft();
		if (cd > 0) {
			if (status != null) status.accept(I18n.t("Подождите ещё ") + cd + I18n.t(" с — КД SkinRestorer"));
			return;
		}
		if (busy) return;
		prepare(e, status, () -> {
			send(e);
			mc.setScreen(null);
			Sfx.wear();
		});
	}

	/** Тихое применение (автонадевание): без закрытия экранов. */
	static boolean isBusy() {
		return busy;
	}

	static void applyQuiet(SkinEntry e, String serverKey) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || busy) return;
		prepare(e, null, () -> {
			send(e);
			SkinStore.setApplied(serverKey, e.id);
			if (mc.player != null) {
				try {
					mc.player.displayClientMessage(Component.literal(I18n.t("Скин применён: ") + e.name), true);
				} catch (Throwable ignored) {
				}
			}
		});
	}

	/** Готовит ссылку (загрузка файла / создание скина с плащом) и вызывает onReady в потоке игры. */
	private static final long NICK_TTL_MS = 120_000L;
	private static final java.util.concurrent.ExecutorService NICK_POOL = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "skinlibrary-nick");
		t.setDaemon(true);
		return t;
	});
	private static volatile boolean nickBusy;

	/** Применяет свежий скин игрока к записи; true, если ссылка или модель изменились. Вызывать в главном потоке. */
	private static boolean applyFresh(SkinEntry e, SkinEntry fresh) {
		if (fresh == null || !fresh.hasUrl()) return false;
		if (fresh.url.equals(e.url) && fresh.variant.equals(e.variant)) return false;
		String old = e.url;
		e.url = fresh.url;
		e.variant = fresh.variant;
		e.capeUrl = ""; // скин с вшитым плащом устарел: создастся заново при надевании
		SkinPreviewCache.evict(old);
		SkinStore.save();
		return true;
	}

	/**
	 * Сверяет «живые» скины (добавленные по нику) с Mojang: если игрок сменил скин, запись обновляется.
	 * Не чаще одного запроса на запись раз в две минуты, последовательно и с паузой, чтобы не упереться в лимиты.
	 */
	public static void refreshNicks() {
		Minecraft mc = Minecraft.getInstance();
		if (nickBusy) return;
		java.util.List<SkinEntry> todo = new java.util.ArrayList<>();
		long now = System.currentTimeMillis();
		for (SkinEntry e : SkinStore.skins()) {
			if (e.isLive() && now - e.nickChecked > NICK_TTL_MS && todo.size() < 12) todo.add(e);
		}
		if (todo.isEmpty()) return;
		nickBusy = true;
		for (SkinEntry e : todo) e.nickChecked = now;
		NICK_POOL.execute(() -> {
			try {
				for (SkinEntry e : todo) {
					SkinEntry fresh = null;
					try {
						fresh = SkinResolver.lookupId(e.nickId, e.name);
					} catch (Exception ignored) { // нет сети или лимит — оставляем прежний скин
					}
					final SkinEntry f = fresh;
					mc.execute(() -> {
						if (applyFresh(e, f)) Notify.show(I18n.t("Скин обновлён"), e.name == null ? "" : e.name);
					});
					try {
						Thread.sleep(400);
					} catch (InterruptedException ie) {
						return;
					}
				}
			} finally {
				nickBusy = false;
			}
		});
	}

	private static void prepare(SkinEntry e, Consumer<String> status, Runnable onReady) {
		Minecraft mc = Minecraft.getInstance();
		if (e.isLive() && System.currentTimeMillis() - e.nickChecked > 60_000L) { // перед надеванием берём актуальный скин игрока
			e.nickChecked = System.currentTimeMillis();
			busy = true;
			if (status != null) status.accept(I18n.t("Проверяю текущий скин игрока..."));
			java.util.concurrent.CompletableFuture.supplyAsync(() -> {
				try {
					return SkinResolver.lookupId(e.nickId, e.name);
				} catch (Exception ex) {
					return null;
				}
			}, NICK_POOL).whenComplete((fresh, ex) -> mc.execute(() -> {
				busy = false;
				if (ex == null) applyFresh(e, fresh);
				prepare(e, status, onReady);
			}));
			return;
		}
		if (e.ready()) {
			onReady.run();
			return;
		}
		busy = true;
		if (status != null) status.accept(e.hasCape() ? I18n.t("Создаю скин с плащом...") : I18n.t("Загрузка скина для команды..."));
		java.util.concurrent.CompletableFuture<String> job = e.hasCape()
				? MineSkinApi.generateWithCape(e.source(), e.variant, e.name, e.capeId)
				: MineSkinApi.generate(e.file, e.variant, e.name);
		job.whenComplete((url, ex) -> mc.execute(() -> {
			busy = false;
			if (ex != null) {
				Throwable t = ex.getCause() != null ? ex.getCause() : ex;
				if (status != null) status.accept(I18n.t("Ошибка загрузки: ") + (t.getMessage() == null ? "?" : t.getMessage()));
				return;
			}
			if (e.hasCape()) e.capeUrl = url;
			else e.url = url;
			SkinStore.save();
			onReady.run();
		}));
	}

	private static void send(SkinEntry e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return;
		String cmd = e.commandNoSlash();
		if (cmd == null) return;
		mc.getConnection().sendCommand(cmd);
		lastSendNs = System.nanoTime();
		startCooldown();
		SkinStore.setApplied(AutoWear.serverKey(mc), e.id);
	}
}
