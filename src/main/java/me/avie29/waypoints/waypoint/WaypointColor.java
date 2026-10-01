package me.avie29.waypoints.waypoint;

import net.minecraft.world.item.DyeColor;

import java.util.concurrent.ThreadLocalRandom;

public final class WaypointColor {
	private static final String[] NAMES = {
		"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
		"dark_gray", "blue", "green", "aqua", "red", "purple", "yellow", "white",
		"magenta", "light_blue", "lime", "pink", "brown"
	};
	private static final int[] RGB = {
		0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF0000, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
		DyeColor.MAGENTA.getTextColor(), DyeColor.LIGHT_BLUE.getTextColor(), DyeColor.LIME.getTextColor(),
		DyeColor.PINK.getTextColor(), DyeColor.BROWN.getTextColor()
	};

	public static final int COUNT = RGB.length;

	private WaypointColor() {
	}

	public static int clamp(int index) {
		return Math.clamp(index, 0, COUNT - 1);
	}

	public static int argb(int index) {
		return 0xFF000000 | rgb(index);
	}

	public static int rgb(int index) {
		return RGB[clamp(index)] & 0xFFFFFF;
	}

	public static String translationKey(int index) {
		return "avies-waypoints.color." + NAMES[clamp(index)];
	}

	public static int random() {
		int[] nice = {1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14, 16, 17, 18, 19};
		return nice[ThreadLocalRandom.current().nextInt(nice.length)];
	}
}
