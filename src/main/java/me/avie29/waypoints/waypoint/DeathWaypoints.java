package me.avie29.waypoints.waypoint;

import me.avie29.waypoints.config.WaypointsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Death waypoints: the place of the last death is "Latest Death". When the player dies again, the previous one
 * becomes "Old Death #N", numbered over all dimensions of the world (#1 is the oldest).
 */
public final class DeathWaypoints {
	public static final String ICON = "☠";

	private DeathWaypoints() {
	}

	public static void onDeath(String dimension, BlockPos pos) {
		int nextNumber = WaypointStore.all().values().stream()
			.flatMap(List::stream)
			.filter(waypoint -> waypoint.type == Waypoint.Type.OLD_DEATH)
			.mapToInt(waypoint -> waypoint.deathNumber)
			.max().orElse(0) + 1;

		boolean keepOld = WaypointsConfig.KEEP_OLD_DEATHS.get();
		for (List<Waypoint> waypoints : WaypointStore.all().values()) {
			if (!keepOld) {
				waypoints.removeIf(waypoint -> waypoint.type == Waypoint.Type.DEATH);
				continue;
			}
			for (Waypoint waypoint : waypoints) {
				if (waypoint.type != Waypoint.Type.DEATH) {
					continue;
				}
				waypoint.type = Waypoint.Type.OLD_DEATH;
				waypoint.deathNumber = nextNumber++;
				if (waypoint.autoName) {
					waypoint.name = Component.translatable("avies-waypoints.death.old", waypoint.deathNumber).getString();
				}
			}
		}

		Waypoint death = new Waypoint(Component.translatable("avies-waypoints.death.latest").getString(), ICON,
			pos.getX(), pos.getY(), pos.getZ(), 0);
		death.type = Waypoint.Type.DEATH;
		death.autoName = true;
		WaypointStore.add(dimension, death);
	}
}
