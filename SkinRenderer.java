package dev.skinlibrary;

import java.util.Arrays;

/**
 * Программный 3D-рендер модели игрока (ортографическая проекция, z-буфер).
 * Система координат: x вправо (для зрителя), y вверх, z к зрителю. Персонаж смотрит в +z.
 * Поддерживает позы: руки и ноги вращаются вокруг плеч и бёдер.
 */
final class SkinRenderer {
	private SkinRenderer() {}

	static void render(int[] tex, boolean slim, double yawDeg, double pitchDeg, double scale,
					   Pose pose, double time, int w, int h, int[] out, float[] zb) {
		Arrays.fill(out, 0);
		Arrays.fill(zb, -1e9f);
		double[] fit = fit(pose); // подгонка размера и положения под позу, чтобы модель не обрезалась
		Ctx c = new Ctx(tex, w, h, scale * fit[0], yawDeg, pitchDeg, out, zb);
		c.offY = fit[1];
		int aw = slim ? 3 : 4;
		double armX = 4 + aw / 2.0;

		// углы частей тела {наклон вперёд-назад (X), поворот (Y), наклон вбок (Z)} и общий наклон/смещение корпуса
		double[] head = new double[3], rArm = new double[3], lArm = new double[3], rLeg = new double[3], lLeg = new double[3];
		double[] body = new double[2]; // {наклон вперёд, смещение по высоте}
		poseAngles(pose, time, head, rArm, lArm, rLeg, lLeg, body);
		c.global(body[0], body[1]);

		for (int layer = 0; layer < 2; layer++) {
			boolean ov = layer == 1;
			double inf = ov ? 0.25 : 0;
			c.limb(0, 24, 0, head);
			c.box(ov ? 32 : 0, 0, 8, 8, 8, 0, 28, 0, ov ? 0.5 : 0, ov);                 // голова
			c.noLimb();
			c.box(16, ov ? 32 : 16, 8, 12, 4, 0, 18, 0, inf, ov);                         // тело

			c.limb(-armX, 22, 0, rArm);
			c.box(40, ov ? 32 : 16, aw, 12, 4, -armX, 18, 0, inf, ov);                    // правая рука
			c.limb(armX, 22, 0, lArm);
			c.box(ov ? 48 : 32, 48, aw, 12, 4, armX, 18, 0, inf, ov);                     // левая рука
			c.limb(-2, 12, 0, rLeg);
			c.box(0, ov ? 32 : 16, 4, 12, 4, -2, 6, 0, inf, ov);                          // правая нога
			c.limb(2, 12, 0, lLeg);
			c.box(ov ? 0 : 16, 48, 4, 12, 4, 2, 6, 0, inf, ov);                           // левая нога
			c.noLimb();
		}
	}

	/** Плащ как тонкая коробка 10x16x1: лицевая сторона (снаружи) — область (1,1) текстуры. tex — сетка 64x64. */
	static void renderCape(int[] tex, double yawDeg, double pitchDeg, double scale, int w, int h, int[] out, float[] zb) {
		Arrays.fill(out, 0);
		Arrays.fill(zb, -1e9f);
		Ctx c = new Ctx(tex, w, h, scale, yawDeg, pitchDeg, out, zb);
		c.box(0, 0, 10, 16, 1, 0, 16, 0, 0, true);
	}

	/** {множитель масштаба, сдвиг по вертикали в пикселях}: подобрано по измерению размеров каждой позы. */
	private static double[] fit(Pose p) {
		return switch (p) {
			case RUN -> new double[]{0.74, 1};
			case WAVE -> new double[]{0.75, 1};
			case DANCE -> new double[]{0.66, 4};
			case SIT -> new double[]{0.84, 16};
			case CROUCH -> new double[]{0.95, -3};
			case ZOMBIE -> new double[]{0.82, 2};
			case FLY -> new double[]{0.5, 5};
			default -> new double[]{0.95, 0};
		};
	}

	private static double rad(double deg) {
		return Math.toRadians(deg);
	}

	/** Движения для каждой позы. Положительный X у руки/ноги — назад, отрицательный — вперёд. */
	private static void poseAngles(Pose pose, double t, double[] head, double[] rArm, double[] lArm,
								   double[] rLeg, double[] lLeg, double[] body) {
		switch (pose) {
			case WALK -> {
				double s = Math.sin(t * 5.0) * rad(32);
				rArm[0] = -s; lArm[0] = s; rLeg[0] = s; lLeg[0] = -s;
			}
			case RUN -> {
				double s = Math.sin(t * 9.0) * rad(55);
				rArm[0] = -s - rad(25); lArm[0] = s - rad(25);
				rLeg[0] = s; lLeg[0] = -s;
				body[0] = rad(14);
				body[1] = Math.abs(Math.sin(t * 9.0)) * 0.9 - 0.5;
				head[0] = -rad(10);
			}
			case WAVE -> {
				rArm[2] = -(rad(150) + Math.sin(t * 9.0) * rad(16));
				head[2] = Math.sin(t * 3.0) * rad(5);
			}
			case DANCE -> {
				double b = t * 6.0;
				rArm[2] = -(rad(110) + Math.sin(b) * rad(35));
				lArm[2] = rad(110) + Math.sin(b + Math.PI) * rad(35);
				rLeg[0] = Math.sin(b) * rad(18);
				lLeg[0] = -Math.sin(b) * rad(18);
				rLeg[2] = -rad(6);
				lLeg[2] = rad(6);
				body[0] = Math.sin(b) * rad(4);
				body[1] = Math.abs(Math.sin(b)) * 1.5;
				head[2] = Math.sin(b * 0.5) * rad(12);
			}
			case SIT -> {
				rLeg[0] = -rad(90); lLeg[0] = -rad(90);
				rArm[0] = -rad(35); lArm[0] = -rad(35);
				body[1] = 0;
			}
			case CROUCH -> {
				body[0] = rad(22);
				body[1] = -1.5;
				head[0] = -rad(22);
				rArm[0] = -rad(15); lArm[0] = -rad(15);
				rLeg[0] = rad(14); lLeg[0] = rad(14);
			}
			case ZOMBIE -> {
				double s = Math.sin(t * 3.0) * rad(20);
				rArm[0] = -rad(90) + Math.sin(t * 2.5) * rad(6);
				lArm[0] = -rad(90) - Math.sin(t * 2.5) * rad(6);
				rLeg[0] = s; lLeg[0] = -s;
				head[0] = rad(8);
			}
			case FLY -> {
				body[0] = rad(82);
				body[1] = 4;
				head[0] = -rad(70);
				rArm[0] = rad(180) + Math.sin(t * 2.0) * rad(4);
				lArm[0] = rad(180) - Math.sin(t * 2.0) * rad(4);
				rLeg[0] = Math.sin(t * 8.0) * rad(12);
				lLeg[0] = -Math.sin(t * 8.0) * rad(12);
			}
			default -> { }
		}
	}

	private static final class Ctx {
		final int[] tex, out;
		final float[] zb;
		final int w, h;
		final double scale, cy, sy, cp, sp;

		boolean limb;
		double lpx, lpy, lpz, cax = 1, sax, cay = 1, say, caz = 1, saz;
		double gLean, gCos = 1, gSin, gDy, offY;

		Ctx(int[] tex, int w, int h, double scale, double yawDeg, double pitchDeg, int[] out, float[] zb) {
			this.tex = tex;
			this.w = w;
			this.h = h;
			this.scale = scale;
			this.out = out;
			this.zb = zb;
			double yaw = Math.toRadians(yawDeg), pit = Math.toRadians(pitchDeg);
			this.cy = Math.cos(yaw);
			this.sy = Math.sin(yaw);
			this.cp = Math.cos(pit);
			this.sp = Math.sin(pit);
		}

		void limb(double px, double py, double pz, double[] ang) {
			limb = ang[0] != 0 || ang[1] != 0 || ang[2] != 0;
			lpx = px;
			lpy = py;
			lpz = pz;
			cax = Math.cos(ang[0]);
			sax = Math.sin(ang[0]);
			cay = Math.cos(ang[1]);
			say = Math.sin(ang[1]);
			caz = Math.cos(ang[2]);
			saz = Math.sin(ang[2]);
		}

		void noLimb() {
			limb = false;
		}

		/** Общий наклон корпуса вперёд вокруг бёдер и смещение по высоте. */
		void global(double lean, double dy) {
			gLean = lean;
			gCos = Math.cos(lean);
			gSin = Math.sin(lean);
			gDy = dy;
		}

		final double[] t0 = new double[3], t1 = new double[3], t2 = new double[3];

		/** Модель -> экран: out = {sx, sy, depth}. Большая глубина = ближе к зрителю. */
		void tp(double x, double y, double z, double[] out) {
			if (limb) {
				double dx = x - lpx, dy = y - lpy, dz = z - lpz;
				double y1 = dy * cax - dz * sax;
				double z1 = dy * sax + dz * cax;
				dy = y1;
				dz = z1;
				double x1 = dx * cay + dz * say;
				double z2 = -dx * say + dz * cay;
				dx = x1;
				dz = z2;
				double x2 = dx * caz - dy * saz;
				double y2 = dx * saz + dy * caz;
				x = lpx + x2;
				y = lpy + y2;
				z = lpz + dz;
			}
			if (gLean != 0 || gDy != 0) {
				double ry = y - 12;
				double ny = 12 + ry * gCos - z * gSin;
				double nz = ry * gSin + z * gCos;
				y = ny + gDy;
				z = nz;
			}
			y -= 16; // центр модели
			double x1 = x * cy + z * sy;
			double z1 = -x * sy + z * cy;
			double y2 = y * cp - z1 * sp;
			double z2 = y * sp + z1 * cp;
			out[0] = w / 2.0 + x1 * scale;
			out[1] = h / 2.0 - y2 * scale + offY;
			out[2] = z2;
		}

		void box(int u, int v, int bw, int bh, int bd, double cx, double cy_, double cz,
				 double inf, boolean overlay) {
			double x0 = cx - bw / 2.0 - inf, x1 = cx + bw / 2.0 + inf;
			double y0 = cy_ - bh / 2.0 - inf, y1 = cy_ + bh / 2.0 + inf;
			double z0 = cz - bd / 2.0 - inf, z1 = cz + bd / 2.0 + inf;
			double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;

			face(new double[]{x0, y1, z1}, new double[]{dx, 0, 0}, new double[]{0, -dy, 0},
					u + bd, v + bd, bw, bh, 0.92, overlay);
			face(new double[]{x1, y1, z0}, new double[]{-dx, 0, 0}, new double[]{0, -dy, 0},
					u + 2 * bd + bw, v + bd, bw, bh, 0.72, overlay);
			face(new double[]{x0, y1, z0}, new double[]{0, 0, dz}, new double[]{0, -dy, 0},
					u, v + bd, bd, bh, 0.82, overlay);
			face(new double[]{x1, y1, z1}, new double[]{0, 0, -dz}, new double[]{0, -dy, 0},
					u + bd + bw, v + bd, bd, bh, 0.82, overlay);
			face(new double[]{x0, y1, z0}, new double[]{dx, 0, 0}, new double[]{0, 0, dz},
					u + bd, v, bw, bd, 1.0, overlay);
			face(new double[]{x0, y0, z1}, new double[]{dx, 0, 0}, new double[]{0, 0, -dz},
					u + bd + bw, v, bw, bd, 0.6, overlay);
		}

		void face(double[] p0, double[] a, double[] b, int u0, int v0, int fw, int fh,
				  double shade, boolean overlay) {
			double[] o = t0, pa = t1, pb = t2;
			tp(p0[0], p0[1], p0[2], o);
			tp(p0[0] + a[0], p0[1] + a[1], p0[2] + a[2], pa);
			tp(p0[0] + b[0], p0[1] + b[1], p0[2] + b[2], pb);
			double ax = pa[0] - o[0], ay = pa[1] - o[1], az = pa[2] - o[2];
			double bx = pb[0] - o[0], by = pb[1] - o[1], bz = pb[2] - o[2];
			double det = ax * by - ay * bx;
			if (det <= 1e-6) return;

			double minX = Math.min(Math.min(o[0], o[0] + ax), Math.min(o[0] + bx, o[0] + ax + bx));
			double maxX = Math.max(Math.max(o[0], o[0] + ax), Math.max(o[0] + bx, o[0] + ax + bx));
			double minY = Math.min(Math.min(o[1], o[1] + ay), Math.min(o[1] + by, o[1] + ay + by));
			double maxY = Math.max(Math.max(o[1], o[1] + ay), Math.max(o[1] + by, o[1] + ay + by));
			int pxMin = Math.max(0, (int) Math.floor(minX)), pxMax = Math.min(w - 1, (int) Math.ceil(maxX));
			int pyMin = Math.max(0, (int) Math.floor(minY)), pyMax = Math.min(h - 1, (int) Math.ceil(maxY));

			for (int py = pyMin; py <= pyMax; py++) {
				double dy = py + 0.5 - o[1];
				for (int px = pxMin; px <= pxMax; px++) {
					double dx = px + 0.5 - o[0];
					double s = (dx * by - dy * bx) / det;
					double t = (ax * dy - ay * dx) / det;
					if (s < 0 || s >= 1 || t < 0 || t >= 1) continue;
					int idx = py * w + px;
					float z = (float) (o[2] + s * az + t * bz);
					if (z <= zb[idx]) continue;

					int tx = u0 + (int) (s * fw);
					int ty = v0 + (int) (t * fh);
					int c = tex[ty * 64 + tx];
					if (overlay && (c >>> 24) < 128) continue;
					int r = (int) (((c >> 16) & 0xFF) * shade);
					int g = (int) (((c >> 8) & 0xFF) * shade);
					int bl = (int) ((c & 0xFF) * shade);
					out[idx] = 0xFF000000 | (r << 16) | (g << 8) | bl;
					zb[idx] = z;
				}
			}
		}
	}
}
