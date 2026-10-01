package me.avie29.waypoints.gametest;

import me.avie29.waypoints.gui.WaypointListScreen;
import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Dies three times: the previous "Latest Death" becomes "Old Death #N", a renamed one keeps its name. */
public class DeathWaypointGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("avies-waypoints-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getServer().runCommand("gamemode survival @a");
			context.waitTicks(40);

			die(context, singleplayer, 0);
			expect(context, "Latest Death@0");
			die(context, singleplayer, 20);
			expect(context, "Old Death #1@0", "Latest Death@20");

			// The player renames the latest death, it keeps that name when it gets old
			context.runOnClient(minecraft -> {
				Waypoint latest = WaypointStore.current().getLast();
				latest.name = "My Grave";
				latest.autoName = false;
			});
			die(context, singleplayer, 40);
			expect(context, "Old Death #1@0", "My Grave@20", "Latest Death@40");

			context.setScreen(() -> new WaypointListScreen(null));
			context.waitTicks(5);
			LOGGER.info("[test] screenshot {}", context.takeScreenshot("40_death_waypoints"));
			LOGGER.info("[test] death waypoint test passed");
		}
	}

	private static void die(ClientGameTestContext context, TestSingleplayerContext singleplayer, int x) {
		singleplayer.getServer().runCommand("tp @a " + x + " -60 0");
		context.waitTicks(10);
		singleplayer.getServer().runCommand("kill @a");
		context.waitForScreen(DeathScreen.class);
		// The respawn button is enabled after a short delay
		context.waitTicks(30);
		context.clickScreenButton("deathScreen.respawn");
		context.waitForScreen(null);
		context.waitTicks(10);
	}

	/** Waypoints of the current dimension as "name@x", in list order. */
	private static void expect(ClientGameTestContext context, String... expected) {
		List<String> actual = context.computeOnClient(minecraft -> WaypointStore.current().stream()
			.map(waypoint -> waypoint.name + "@" + waypoint.x)
			.toList());
		if (!actual.equals(List.of(expected))) {
			throw new AssertionError("Expected " + List.of(expected) + " but got " + actual);
		}
		LOGGER.info("[test] waypoints: {}", actual);
	}
}
