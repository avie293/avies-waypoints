package me.avie29.waypoints.waypoint;

import net.minecraft.world.phys.Vec3;

/** One waypoint. Mutable, the edit screen changes the fields directly. */
public class Waypoint {
	public enum Type {
		NORMAL,
		DEATH
	}

	public String name;
	/** Short text inside the icon, 1-2 characters (Xaero calls this "initials"). */
	public String initials;
	public int x;
	public int y;
	public int z;
	/** False when the waypoint was shared without a height ("~" in Xaero's format). It is then drawn at the camera height. */
	public boolean yIncluded = true;
	/** Index into {@link WaypointColor} (0-15, same palette as Xaero). */
	public int color;
	public boolean visible = true;
	public Type type = Type.NORMAL;
	/** Only kept for Xaero compatibility. */
	public boolean rotateOnTp;
	public int yaw;

	public Waypoint(String name, String initials, int x, int y, int z, int color) {
		this.name = name;
		this.initials = initials;
		this.x = x;
		this.y = y;
		this.z = z;
		this.color = WaypointColor.clamp(color);
	}

	/** Center of the waypoint block, at the height the icon is drawn. */
	public Vec3 renderPos(double cameraY) {
		return new Vec3(this.x + 0.5, this.yIncluded ? this.y + 1.0 : cameraY, this.z + 0.5);
	}

	/** First character of every word, at most two, like Xaero does it. */
	public static String defaultInitials(String name) {
		StringBuilder builder = new StringBuilder();
		for (String word : name.trim().split("\\s+")) {
			if (!word.isEmpty()) {
				builder.appendCodePoint(word.codePointAt(0));
				if (builder.codePointCount(0, builder.length()) == 2) {
					break;
				}
			}
		}
		return builder.isEmpty() ? "X" : builder.toString().toUpperCase();
	}
}
