package me.avie29.waypoints.share;

import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointColor;
import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XaeroShareFormat {
	public static final String PREFIX = "xaero-waypoint:";
	private static final String INTERNAL = "Internal-";

	private static final Pattern PATTERN = Pattern.compile(
		"(xaero[-_]waypoint):([^:]*):([^:]*):(-?\\d+):(~|-?\\d+):(-?\\d+):(\\d+):(true|false):(-?\\d+)(?::(\\S+))?");

	private XaeroShareFormat() {
	}

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
			+ INTERNAL + removeFormatting(dimensionKey(dimension).replace(":", "^col^"));
	}

	public static @Nullable Shared find(String text) {
		Matcher matcher = PATTERN.matcher(text.replaceAll("§.", ""));
		if (!matcher.find()) {
			return null;
		}
		try {
			boolean newFormat = matcher.group(1).equals("xaero-waypoint");
			String name = newFormat ? unescape(matcher.group(2)) : matcher.group(2);
			String initials = newFormat ? unescape(matcher.group(3)) : matcher.group(3);
			boolean yIncluded = !matcher.group(5).equals("~");
			Waypoint waypoint = new Waypoint(
				name.isEmpty() ? "Waypoint" : name,
				initials.isEmpty() ? Waypoint.defaultInitials(name) : initials,
				Integer.parseInt(matcher.group(4)),
				yIncluded ? Integer.parseInt(matcher.group(5)) : 64,
				Integer.parseInt(matcher.group(6)),
				Integer.parseInt(matcher.group(7)) % WaypointColor.COUNT);
			waypoint.yIncluded = yIncluded;
			waypoint.rotateOnTp = Boolean.parseBoolean(matcher.group(8));
			waypoint.yaw = Integer.parseInt(matcher.group(9));
			String destination = matcher.group(10);
			return new Shared(waypoint, destination == null ? null : dimensionOf(destination), matcher.group());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String escape(String text) {
		return removeFormatting(text.replace(":", "^col^"));
	}

	private static String unescape(String text) {
		return restoreFormatting(text).replace("^col^", ":");
	}

	private static String removeFormatting(String text) {
		return text.replace("-", "^min^").replace("_", "-").replace("*", "^ast^");
	}

	private static String restoreFormatting(String text) {
		return text.replace("^ast^", "*").replace("-", "_").replace("^min^", "-");
	}

	static String dimensionKey(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> "overworld";
			case "minecraft:the_nether" -> "the_nether";
			case "minecraft:the_end" -> "the_end";
			default -> {
				int colon = dimension.indexOf(':');
				String namespace = colon < 0 ? "minecraft" : dimension.substring(0, colon);
				yield "dim%" + namespace + "$" + dimension.substring(colon + 1).replace('/', '%');
			}
		};
	}

	static @Nullable String dimensionOf(String destination) {
		if (!destination.startsWith(INTERNAL)) {
			return null;
		}
		String key = unescape(destination.substring(INTERNAL.length()));
		int slash = key.indexOf('/');
		if (slash >= 0) {
			key = key.substring(0, slash);
		}
		if (key.endsWith("_waypoints")) {
			key = key.substring(0, key.length() - "_waypoints".length());
		}
		switch (key) {
			case "overworld", "dim%0":
				return "minecraft:overworld";
			case "the_nether", "nether", "dim%-1":
				return "minecraft:the_nether";
			case "the_end", "end", "dim%1":
				return "minecraft:the_end";
			default:
				break;
		}
		if (key.startsWith("dim%")) {
			String id = key.substring("dim%".length());
			int dollar = id.indexOf('$');
			if (dollar > 0) {
				return id.substring(0, dollar) + ":" + id.substring(dollar + 1).replace('%', '/');
			}
		}
		return null;
	}
}
