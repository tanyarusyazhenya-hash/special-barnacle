package dev.aurora.util;

public final class ColorUtil {
	private ColorUtil() {}

	/** RGB + alpha (0..1) to packed ARGB. */
	public static int argb(int rgb, float alpha) {
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	public static int scale(int rgb, float k) {
		int r = clamp((int) (((rgb >> 16) & 0xFF) * k));
		int g = clamp((int) (((rgb >> 8) & 0xFF) * k));
		int b = clamp((int) ((rgb & 0xFF) * k));
		return (r << 16) | (g << 8) | b;
	}

	public static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
		int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
		int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
		return (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
	}

	public static int hsb(float h, float s, float b) {
		return java.awt.Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
