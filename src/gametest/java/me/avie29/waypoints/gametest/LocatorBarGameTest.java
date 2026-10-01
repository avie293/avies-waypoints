package me.avie29.waypoints.gametest;

import com.mojang.authlib.GameProfile;
import me.avie29.waypoints.config.WaypointsConfig;
import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * Locator bar with player heads and waypoints. Singleplayer has no second player, so husks that transmit a
 * waypoint stand in for players and get a tab list entry; the bar then treats them exactly like players.
 */
public class LocatorBarGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("avies-waypoints-gametest");

	/** name, distance, angle to the right of the view direction. */
	private record Fake(String name, int number, int distance, int angle) {
		UUID uuid() {
			return new UUID(0, this.number);
		}
	}

	private static final Fake[] PLAYERS = {
		new Fake("Steve", 1, 30, 2),
		new Fake("Alex", 2, 200, 22),
		new Fake("Notch", 3, 400, -30)
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getServer().runCommand("gamemode creative @a");
			singleplayer.getServer().runCommand("time set day");
			singleplayer.getServer().runCommand("gamerule advance_time false");
			singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 -90 0");
			for (Fake fake : PLAYERS) {
				int x = (int) Math.round(fake.distance() * Math.cos(Math.toRadians(fake.angle())));
				int z = (int) Math.round(fake.distance() * Math.sin(Math.toRadians(fake.angle())));
				singleplayer.getServer().runCommand("forceload add " + x + " " + z);
				singleplayer.getServer().runCommand("summon husk " + x + " -60 " + z + " {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,"
					+ "UUID:[I;0,0,0," + fake.number() + "],attributes:[{id:\"minecraft:waypoint_transmit_range\",base:1000.0}]}");
			}
			context.waitTicks(40);
			context.runOnClient(minecraft -> {
				Map<UUID, PlayerInfo> players = playerInfoMap(minecraft.player.connection);
				for (Fake fake : PLAYERS) {
					players.put(fake.uuid(), new PlayerInfo(new GameProfile(fake.uuid(), fake.name()), false));
				}
				String dimension = WaypointStore.currentDimension();
				WaypointStore.add(dimension, new Waypoint("Base", "B", 60, -60, -12, 10));
				WaypointStore.add(dimension, new Waypoint("Mine", "M", 250, -60, 160, 6));
				WaypointsConfig.ENABLED.set(false);
			});

			setBar(context, false, false);
			screenshot(context, "50_bar_vanilla");
			setBar(context, true, false);
			screenshot(context, "51_bar_heads");
			setBar(context, true, true);
			screenshot(context, "52_bar_heads_waypoints");
			// Looking at the "Base" waypoint: its name and distance are shown
			singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 -101 0");
			context.waitTicks(10);
			screenshot(context, "53_bar_looking_at_waypoint");

			// Without any player the bar is still shown for the waypoints
			singleplayer.getServer().runCommand("kill @e[type=husk]");
			context.waitTicks(20);
			screenshot(context, "54_bar_only_waypoints");
			setBar(context, true, false);
			screenshot(context, "55_bar_no_waypoints_option");
		}
	}

	private static void setBar(ClientGameTestContext context, boolean heads, boolean waypoints) {
		context.runOnClient(minecraft -> {
			WaypointsConfig.PLAYER_HEADS.set(heads);
			WaypointsConfig.WAYPOINTS_ON_BAR.set(waypoints);
		});
		context.waitTicks(5);
	}

	@SuppressWarnings("unchecked")
	private static Map<UUID, PlayerInfo> playerInfoMap(ClientPacketListener connection) {
		try {
			Field field = ClientPacketListener.class.getDeclaredField("playerInfoMap");
			field.setAccessible(true);
			return (Map<UUID, PlayerInfo>) field.get(connection);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void screenshot(ClientGameTestContext context, String name) {
		LOGGER.info("[test] screenshot {}", context.takeScreenshot(name));
	}
}
