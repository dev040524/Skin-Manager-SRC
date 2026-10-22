package dev.skinlibrary;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Прямое чтение состояния мыши из GLFW: работает и на экранах меню. */
public final class Input {
	private Input() {}

	public static boolean shiftDown() {
		try {
			long window = GLFW.glfwGetCurrentContext();
			return window != 0L && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
					|| GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean leftDown() {
		try {
			long window = GLFW.glfwGetCurrentContext();
			return window != 0L && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Положение курсора в координатах интерфейса (GUI-пиксели) или null, если не удалось определить. */
	public static double[] cursorGui() {
		try {
			long window = GLFW.glfwGetCurrentContext();
			if (window == 0L) return null;
			double[] cx = new double[1], cy = new double[1];
			GLFW.glfwGetCursorPos(window, cx, cy);
			int[] ww = new int[1], wh = new int[1];
			GLFW.glfwGetWindowSize(window, ww, wh);
			if (ww[0] <= 0 || wh[0] <= 0) return null;
			Minecraft mc = Minecraft.getInstance();
			return new double[]{cx[0] * mc.getWindow().getGuiScaledWidth() / ww[0],
					cy[0] * mc.getWindow().getGuiScaledHeight() / wh[0]};
		} catch (Throwable t) {
			return null;
		}
	}
}
