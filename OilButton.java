package dev.skinlibrary.screen;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skinlibrary.Input;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Кнопка «нефтяная плёнка»: чёрная жидкость с радужной плёнкой. К курсору тянется гладкий мениск (пружина),
 * снизу набухают капли и отрываются. Картинка считается программно в {@link OilField} и кэшируется:
 * перерисовка только когда заметно изменилась форма (кончик, капля, наведение).
 */
final class OilButton {
	private static final class St {
		DynamicTexture tex;
		Identifier id;
		int tw, th, w, h;
		int[] pix;
		float[] d;
		int[] base;
		long baseSig = Long.MIN_VALUE;
		int[][] prev = new int[3][4];
		int prevN;
		long lastRender;
		float tipX, tipY, tipV, tipVy, reach;
		long last;
		long sig = Long.MIN_VALUE;
		int lastUse;
	}

	private static final Map<Long, St> MAP = new HashMap<>();
	private static int frame, ids;

	private OilButton() {}

	static void draw(GuiGraphics g, int x, int y, int w, int h, float hover, boolean active, int kind) {
		frame++;
		if (frame % 600 == 0) { // выбрасываем давно не рисуемые
			MAP.values().removeIf(s -> {
				if (frame - s.lastUse > 300) { if (s.tex != null) s.tex.close(); return true; }
				return false;
			});
		}
		long key = ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) w * 83492791L) ^ ((long) h << 40);
		St s = MAP.computeIfAbsent(key, k -> new St());
		s.lastUse = frame;
		if (s.tex == null || s.w != w || s.h != h) {
			if (s.tex != null) s.tex.close();
			s.w = w; s.h = h;
			s.tw = OilField.texW(w); s.th = OilField.texH(h);
			s.pix = new int[s.tw * s.th];
			s.d = new float[s.tw * s.th];
			s.base = new int[s.tw * s.th];
			s.baseSig = Long.MIN_VALUE;
			s.prevN = 0;
			s.tex = new DynamicTexture(() -> "skinlibrary_oil", new NativeImage(s.tw, s.th, true));
			s.id = Identifier.fromNamespaceAndPath("skinlibrary", "oil/" + (++ids));
			Minecraft.getInstance().getTextureManager().register(s.id, s.tex);
			s.sig = Long.MIN_VALUE;
			s.tipX = w / 2f; s.tipY = h / 2f; s.reach = 0f;
		}
		long now = System.nanoTime();
		float dt = s.last == 0 ? 0.016f : Math.min(0.05f, (now - s.last) / 1e9f);
		s.last = now;
		float t = (now / 1_000_000L % 1_000_000L) / 1000f;

		// курсор → цель мениска
		boolean tipOn = false;
		float rx = 0, ry = 0, tx = 0, ty = 0;
		double[] cur = active ? Input.cursorGui() : null;
		float want = 0f, cx = w / 2f, cy = h / 2f;
		if (cur != null) {
			float mx = (float) cur[0] - x, my = (float) cur[1] - y;
			float nx = Math.max(0, Math.min(w, mx)), ny = Math.max(0, Math.min(h, my));
			float dist = (float) Math.hypot(mx - nx, my - ny);
			if (dist < 56f) {
				want = Math.max(0f, Math.min(1f, (56f - dist) / 40f));
				if (dist < 1f) { // курсор над кнопкой: бугорок следует за ним по краю
					ny = my < h / 2f ? 0f : h;
					nx = mx;
				}
				rx = nx; ry = ny;
				float dx = mx - nx, dy = my - ny, len = (float) Math.hypot(dx, dy);
				float lim = Math.min(len, 8f + 34f * want);
				if (len < 1e-3f) { dx = 0; dy = -1; len = 1; }
				cx = nx + dx / len * lim;
				cy = ny + dy / len * lim;
			}
		}
		// пружина: кончик догоняет цель с инерцией
		float k = 120f, c = 11f;
		float ax = (cx - s.tipX) * k - s.tipV * c, ay = (cy - s.tipY) * k - s.tipVy * c;
		s.tipV += ax * dt; s.tipVy += ay * dt;
		s.tipX += s.tipV * dt; s.tipY += s.tipVy * dt;
		s.reach += (want - s.reach) * Math.min(1f, dt * 9f);
		if (s.reach > 0.04f && cur != null && want > 0f || s.reach > 0.08f) {
			tipOn = true;
			if (want <= 0f) { rx = s.tipX; ry = s.tipY; }
			float dxr = s.tipX - rx, dyr = s.tipY - ry;
			float len = (float) Math.hypot(dxr, dyr);
			if (len < 3f) { tipOn = s.reach > 0.15f; }
			tx = rx + dxr * Math.min(1f, s.reach * 1.2f);
			ty = ry + dyr * Math.min(1f, s.reach * 1.2f);
			if (len < 3f) { tx = rx + 3f * (len < 1e-3f ? 0 : dxr / len); ty = ry + 3f * (len < 1e-3f ? -1 : dyr / len); }
		}

		// капли: 1–2 на кнопку, разные фазы
		int nd = w >= 90 ? 2 : 1;
		float[] drip = new float[nd * 4];
		int seed = (int) (key & 0x7fffffff);
		for (int i = 0; i < nd; i++) {
			float ph = ((seed >> (i * 5)) & 1023) / 1023f;
			float per = 6.5f + ((seed >> (i * 3 + 2)) & 7) * 0.6f;
			float u = ((t + ph * per) % per) / per;
			float px = w * (0.2f + 0.6f * (((seed >> (i * 7 + 1)) & 255) / 255f));
			float len = -1, fall = -1, rad = 3f;
			if (u < 0.72f) {
				float q = u / 0.72f;
				len = 0.5f + 8f * q * q;
				rad = 2.2f + 1.5f * q;
			} else if (u < 0.78f) { // шейка тянется, капля отрывается
				float q = (u - 0.72f) / 0.06f;
				len = 8.5f + 3f * q; rad = 3.7f - 0.6f * q;
				if (q > 0.8f) len = 0f;
			} else {
				float q = (u - 0.78f) / 0.22f;
				fall = 14f * q * q + 6f * q;
				rad = 3.0f;
				if (q > 0.92f) fall = -1;
			}
			drip[i * 4] = px; drip[i * 4 + 1] = active ? len : -1; drip[i * 4 + 2] = active ? fall : -1; drip[i * 4 + 3] = rad;
		}

		// База (тело кнопки) зависит только от наведения/вида и считается редко; мениск и капли — локальные «заплатки» поверх неё.
		long bsig = Math.round(hover * 4f) * 31L + (active ? 1 : 0) + kind * 2L;
		long fsig = 17;
		fsig = fsig * 31 + (tipOn ? Math.round(tx * 2f) * 1000L + Math.round(ty * 2f) + Math.round(rx) * 7919L + Math.round(ry) * 104729L : -1);
		for (int i = 0; i < nd * 4; i++) fsig = fsig * 31 + Math.round(drip[i] * 2f);
		boolean baseChanged = bsig != s.baseSig;
		if (baseChanged || (fsig != s.sig && now - s.lastRender > 55_000_000L)) {
			s.lastRender = now;
			int tw = s.tw;
			int[] reg = new int[4];
			int[][] cur2 = new int[3][4];
			int n = 0;
			if (baseChanged) {
				s.baseSig = bsig;
				OilField.bodyRegion(w, h, reg);
				OilField.render(s.base, s.d, w, h, hover, kind, active, false, 0, 0, 0, 0, drip, 0, reg, true);
			}
			for (int i = 0; i < nd; i++) if (drip[i * 4 + 1] > 0f || drip[i * 4 + 2] >= 0f) OilField.dripRegion(h, drip[i * 4], cur2[n++]);
			if (tipOn) OilField.tipRegion(rx, ry, tx, ty, cur2[n++]);
			// восстановить прежние заплатки из базы
			int[] up = baseChanged ? null : new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0};
			if (baseChanged) {
				System.arraycopy(s.base, 0, s.pix, 0, s.base.length); // вне тела в базе нули — старые заплатки исчезают
			}
			if (!baseChanged) for (int i = 0; i < s.prevN; i++) { copyRect(s.base, s.pix, tw, s.th, s.prev[i][0], s.prev[i][1], s.prev[i][2], s.prev[i][3]); union(up, s.prev[i]); }
			for (int i = 0; i < n; i++) { union(up == null ? (up = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0}) : up, cur2[i]); }
			for (int i = 0; i < n; i++) OilField.render(s.pix, s.d, w, h, hover, kind, active, tipOn, rx, ry, tx, ty, drip, nd, cur2[i], false);
			for (int i = 0; i < n; i++) System.arraycopy(cur2[i], 0, s.prev[i], 0, 4);
			s.prevN = n;
			s.sig = fsig;
			NativeImage px = s.tex.getPixels();
			int u0, v0, u1, v1;
			if (baseChanged) { // полная выгрузка области тела + прежних заплаток
				u0 = 0; v0 = 0; u1 = tw; v1 = s.th;
			} else { u0 = Math.max(0, up[0]); v0 = Math.max(0, up[1]); u1 = Math.min(tw, up[2]); v1 = Math.min(s.th, up[3]); }
			for (int j = v0; j < v1; j++) {
				int row = j * tw;
				for (int i = u0; i < u1; i++) px.setPixel(i, j, s.pix[row + i]);
			}
			s.tex.upload();
		}
		g.pose().pushMatrix();
		g.pose().translate(x - OilField.MX, y - OilField.MY);
		g.pose().scale(1f / OilField.S, 1f / OilField.S);
		g.blit(RenderPipelines.GUI_TEXTURED, s.id, 0, 0, 0f, 0f, s.tw, s.th, s.tw, s.th);
		g.pose().popMatrix();
	}

	private static void union(int[] u, int[] r) {
		u[0] = Math.min(u[0], r[0]); u[1] = Math.min(u[1], r[1]); u[2] = Math.max(u[2], r[2]); u[3] = Math.max(u[3], r[3]);
	}

	private static void copyRect(int[] from, int[] to, int tw, int th, int i0, int j0, int i1, int j1) {
		i0 = Math.max(0, i0); j0 = Math.max(0, j0); i1 = Math.min(tw, i1); j1 = Math.min(th, j1);
		if (i1 <= i0) return;
		for (int j = j0; j < j1; j++) System.arraycopy(from, j * tw + i0, to, j * tw + i0, i1 - i0);
	}

	/** NativeImage.setPixel принимает ABGR; у CapeView ARGB уже используется как есть — здесь повторяем его соглашение. */
	private static int toAbgr(int argb) {
		return argb;
	}
}
