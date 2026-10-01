package me.avie29.waypoints.share;

import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointColor;
import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The chat format Xaero's Minimap uses to share waypoints:
 * <pre>xaero-waypoint:Name:N:x:y:z:color:rotateOnTp:yaw:Internal-overworld-waypoints</pre>
 * Colons in name and initials can come as "§§", y is "~" when the height is unknown and color is
 * an index into the 16 chat colors.
 */
public final class XaeroShareFormat {
	public static final String PREFIX = "xaero-waypoint:";

	/** Finds a shared waypoint anywhere in a chat line (players' names etc. come before it). */
	private static final Pattern PATTERN = Pattern.compile(
		"xaero[-_]waypoint:([^:]*):([^:]*):(-?\\d+):(~|-?\\d+):(-?\\d+):(\\d+):(true|false):(-?\\d+)(?::([^\\s:]+))?");

	private XaeroShareFormat() {
	}

	/** A waypoint read from chat, with the dimension it belongs to (null when it can not be told). */
	public record Shared(Waypoint waypoint, @Nullable String dimension, String raw) {
	}

	public static String format(Waypoint waypoint, String dimension) {
		return PREFIX
			+ escape(waypoint.name) + ":"
			+ escape(waypoint.initials) + ":"
			+ waypoint.x + ":"
			+ (waypoint.yIncluded ? String.valueOf(waypoint.y) : "~") + ":"
			+ waypoint.z + ":"
			+ waypoint.color + ":"
			+ waypoint.rotateOnTp + ":"
			+ waypoint.yaw + ":"
			+ worldPart(dimension);
	}

	/** Finds the first shared waypoint in a text, or null. */
	public static @Nullable Shared find(String text) {
		Matcher matcher = PATTERN.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		try {
			String name = unescape(matcher.group(1));
			String initials = unescape(matcher.group(2));
			boolean yIncluded = !matcher.group(4).equals("~");
			Waypoint waypoint = new Waypoint(
				name.isEmpty() ? "Waypoint" : name,
				initials.isEmpty() ? Waypoint.defaultInitials(name) : initials,
				Integer.parseInt(matcher.group(3)),
				yIncluded ? Integer.parseInt(matcher.group(4)) : 64,
				Integer.parseInt(matcher.group(5)),
				Integer.parseInt(matcher.group(6)) % WaypointColor.COUNT);
			waypoint.yIncluded = yIncluded;
			waypoint.rotateOnTp = Boolean.parseBoolean(matcher.group(7));
			waypoint.yaw = Integer.parseInt(matcher.group(8));
			String world = matcher.group(9);
			return new Shared(waypoint, world == null ? null : dimensionOf(world), matcher.group());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Servers kick players that send "§", so colons are replaced instead of escaped like in Xaero's files. */
	private static String escape(String text) {
		return text.replace(':', '-').replace("§", "");
	}

	private static String unescape(String text) {
		return text.replace("§§", ":");
	}

	/** Dimension id to Xaero's container name, e.g. "minecraft:the_nether" to "Internal-the-nether-waypoints". */
	static String worldPart(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> "Internal-overworld-waypoints";
			case "minecraft:the_nether" -> "Internal-the-nether-waypoints";
			case "minecraft:the_end" -> "Internal-the-end-waypoints";
			default -> {
				int colon = dimension.indexOf(':');
				String namespace = colon < 0 ? "minecraft" : dimension.substring(0, colon);
				String path = dimension.substring(colon + 1).replace('/', '%');
				yield "Internal-dim%" + namespace + "$" + path + "-waypoints";
			}
		};
	}

	/** Xaero's container name back to a dimension id. Knows the old number ids (dim%-1) too. */
	static @Nullable String dimensionOf(String world) {
		String name = world;
		if (name.startsWith("Internal-") || name.startsWith("Internal_")) {
			name = name.substring("Internal-".length());
		}
		if (name.endsWith("-waypoints") || name.endsWith("_waypoints")) {
			name = name.substring(0, name.length() - "-waypoints".length());
		}
		switch (name) {
			case "overworld", "dim%0":
				return "minecraft:overworld";
			case "the-nether", "the_nether", "nether", "dim%-1":
				return "minecraft:the_nether";
			case "the-end", "the_end", "end", "dim%1":
				return "minecraft:the_end";
			default:
				break;
		}
		if (name.startsWith("dim%")) {
			String id = name.substring("dim%".length());
			int dollar = id.indexOf('$');
			if (dollar > 0) {
				return id.substring(0, dollar) + ":" + id.substring(dollar + 1).replace('%', '/');
			}
		}
		return null;
	}
}
