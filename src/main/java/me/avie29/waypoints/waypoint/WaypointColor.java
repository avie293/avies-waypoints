package me.avie29.waypoints.waypoint;

import net.minecraft.ChatFormatting;

import java.util.concurrent.ThreadLocalRandom;

/** The 16 chat colors. Xaero's minimap uses the same palette and stores the index in shared waypoints. */
public final class WaypointColor {
	private static final ChatFormatting[] FORMATS = {
		ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
		ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
		ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
		ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE
	};
	private static final int[] RGB = {
		0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
		0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
		0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
	};

	public static final int COUNT = RGB.length;

	private WaypointColor() {
	}

	public static int clamp(int index) {
		return index < 0 || index >= COUNT ? 0 : index;
	}

	/** Opaque ARGB color of the palette entry. */
	public static int argb(int index) {
		return 0xFF000000 | RGB[clamp(index)];
	}

	public static ChatFormatting format(int index) {
		return FORMATS[clamp(index)];
	}

	/** Random color for new waypoints, skips black and the grays like Xaero. */
	public static int random() {
		int[] nice = {1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14};
		return nice[ThreadLocalRandom.current().nextInt(nice.length)];
	}
}
