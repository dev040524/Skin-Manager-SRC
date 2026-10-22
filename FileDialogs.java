package dev.skinlibrary;

import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Системные диалоги выбора файла (из LWJGL, который уже есть в Minecraft). */
public final class FileDialogs {
	public enum Mode { OPEN_PNG, OPEN_JSON, SAVE_JSON, OPEN_AUDIO, PICK_FOLDER }

	private FileDialogs() {}

	/** Колбэк вызывается в потоке рендера; null — выбор отменён. */
	public static void pick(Mode mode, Consumer<String> onDone) {
		Minecraft mc = Minecraft.getInstance();
		Supplier<String> job = () -> run(mode);
		if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
			onDone.accept(job.get()); // на macOS диалоги нужно открывать из главного потока
			return;
		}
		CompletableFuture.supplyAsync(job).thenAccept(p -> mc.execute(() -> onDone.accept(p)));
	}

	private static String run(Mode mode) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			if (mode == Mode.PICK_FOLDER) {
				return TinyFileDialogs.tinyfd_selectFolderDialog("Select skins folder", "");
			}
			PointerBuffer filters;
			if (mode == Mode.OPEN_AUDIO) {
				filters = stack.mallocPointer(2);
				filters.put(stack.UTF8("*.ogg")).put(stack.UTF8("*.wav")).flip();
			} else {
				filters = stack.mallocPointer(1);
				filters.put(stack.UTF8(mode == Mode.OPEN_PNG ? "*.png" : "*.json")).flip();
			}
			switch (mode) {
				case OPEN_PNG:
					return TinyFileDialogs.tinyfd_openFileDialog("Select skin PNG", "", filters, "PNG skin", false);
				case OPEN_JSON:
					return TinyFileDialogs.tinyfd_openFileDialog("Import library", "", filters, "JSON", false);
				case OPEN_AUDIO:
					return TinyFileDialogs.tinyfd_openFileDialog("Select sound (.ogg / .wav)", "", filters, "Sound", false);
				default:
					String def = System.getProperty("user.home", "") + File.separator + "skinlibrary-export.json";
					return TinyFileDialogs.tinyfd_saveFileDialog("Export library", def, filters, "JSON");
			}
		} catch (Throwable t) {
			return null;
		}
	}
}
