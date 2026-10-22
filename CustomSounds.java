package dev.skinlibrary;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Свои звуки (.ogg и .wav) для событий интерфейса. Файлы копируются в config/skinlibrary/sounds.
 * .ogg декодируется встроенным в Minecraft stb_vorbis, воспроизведение — через javax.sound.
 */
public final class CustomSounds {
	private static final Path DIR = SkinStore.DIR.resolve("sounds");
	private static final Map<Sfx.Event, Decoded> CACHE = new EnumMap<>(Sfx.Event.class);
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "skinlibrary-audio");
		t.setDaemon(true);
		return t;
	});

	private record Decoded(byte[] pcm, int channels, float rate) {}

	private CustomSounds() {}

	public static void load() {
		for (Sfx.Event e : Sfx.Event.values()) {
			for (String ext : new String[]{"ogg", "wav"}) {
				Path p = DIR.resolve(e.name().toLowerCase() + "." + ext);
				if (Files.isRegularFile(p)) {
					try {
						CACHE.put(e, decode(p));
					} catch (Exception ignored) {
					}
				}
			}
		}
	}

	public static boolean has(Sfx.Event e) {
		return CACHE.containsKey(e);
	}

	/** Импортирует звук для события. Бросает исключение, если файл не читается. */
	public static void importFile(Sfx.Event e, Path src) throws Exception {
		Decoded d = decode(src);
		String name = src.getFileName().toString().toLowerCase();
		String ext = name.endsWith(".ogg") ? "ogg" : "wav";
		Files.createDirectories(DIR);
		Path target = DIR.resolve(e.name().toLowerCase() + "." + ext);
		Path tmp = DIR.resolve(e.name().toLowerCase() + ".tmp");
		Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING); // сначала копия, чтобы повторный выбор того же файла не удалял источник
		clearFiles(e);
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		CACHE.put(e, d);
	}

	public static void clear(Sfx.Event e) {
		clearFiles(e);
		CACHE.remove(e);
	}

	private static void clearFiles(Sfx.Event e) {
		for (String ext : new String[]{"ogg", "wav"}) {
			try {
				Files.deleteIfExists(DIR.resolve(e.name().toLowerCase() + "." + ext));
			} catch (Exception ignored) {
			}
		}
	}

	public static void play(Sfx.Event e, float volume) {
		Decoded d = CACHE.get(e);
		if (d == null) return;
		EXEC.execute(() -> {
			Clip clip = null;
			try {
				AudioFormat f = new AudioFormat(d.rate(), 16, d.channels(), true, false);
				clip = AudioSystem.getClip();
				final Clip cl = clip;
				cl.addLineListener(ev -> {
					if (ev.getType() == LineEvent.Type.STOP) cl.close();
				});
				clip.open(f, d.pcm(), 0, d.pcm().length);
				if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
					FloatControl c = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
					float db = (float) (20.0 * Math.log10(Math.max(0.0001, volume)));
					c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), db)));
				}
				clip.start();
			} catch (Throwable ignored) {
				if (clip != null) try { clip.close(); } catch (Throwable ignored2) { }
			}
		});
	}

	// ------------------------------------------------------------------ декодирование

	private static Decoded decode(Path p) throws Exception {
		String n = p.getFileName().toString().toLowerCase();
		if (n.endsWith(".ogg")) return decodeOgg(p);
		if (n.endsWith(".wav")) return decodeWav(p);
		throw new IOException(I18n.t("поддерживаются только .ogg и .wav"));
	}

	private static Decoded decodeOgg(Path p) throws Exception {
		byte[] bytes = Files.readAllBytes(p);
		if (bytes.length > 8_000_000) throw new IOException(I18n.t("файл слишком большой"));
		ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
		try {
			buf.put(bytes).flip();
			try (MemoryStack st = MemoryStack.stackPush()) {
				IntBuffer ch = st.mallocInt(1), sr = st.mallocInt(1);
				ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(buf, ch, sr);
				if (pcm == null) throw new IOException(I18n.t("не удалось прочитать .ogg"));
				int channels = ch.get(0), rate = sr.get(0);
				int samples = Math.min(pcm.remaining(), rate * channels * 3); // не длиннее 3 секунд
				byte[] out = new byte[samples * 2];
				for (int i = 0; i < samples; i++) {
					short v = pcm.get(i);
					out[2 * i] = (byte) v;
					out[2 * i + 1] = (byte) (v >> 8);
				}
				try {
					LibCStdlib.nfree(MemoryUtil.memAddress(pcm));
				} catch (Throwable ignored) {
				}
				return new Decoded(out, channels, rate);
			}
		} finally {
			MemoryUtil.memFree(buf);
		}
	}

	private static Decoded decodeWav(Path p) throws Exception {
		try (AudioInputStream ais = AudioSystem.getAudioInputStream(p.toFile())) {
			AudioFormat src = ais.getFormat();
			AudioFormat tgt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
					src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
			try (AudioInputStream conv = AudioSystem.getAudioInputStream(tgt, ais)) {
				byte[] all = conv.readAllBytes();
				int max = (int) (src.getSampleRate() * src.getChannels() * 2 * 3);
				if (all.length > max) all = java.util.Arrays.copyOf(all, max);
				return new Decoded(all, src.getChannels(), src.getSampleRate());
			}
		}
	}
}
