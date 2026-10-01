package me.avie29.waypoints.waypoint;

import net.minecraft.world.phys.Vec3;

public class Waypoint {
	public enum Type {
		NORMAL,
		DEATH,
		OLD_DEATH
	}

	public String name;
	public String initials;
	public int x;
	public int y;
	public int z;
	public boolean yIncluded = true;
	public int color;
	public boolean visible = true;
	public Type type = Type.NORMAL;
	public int deathNumber;
	public boolean autoName;
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

	public Vec3 renderPos(double cameraY) {
		return new Vec3(this.x + 0.5, this.yIncluded ? this.y + 1.0 : cameraY, this.z + 0.5);
	}

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
