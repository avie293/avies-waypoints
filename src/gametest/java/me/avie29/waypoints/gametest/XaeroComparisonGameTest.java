package me.avie29.waypoints.gametest;

import me.avie29.waypoints.config.WaypointsConfig;
import me.avie29.waypoints.gui.WaypointEditScreen;
import me.avie29.waypoints.gui.WaypointListScreen;
import me.avie29.waypoints.share.ChatShareHandler;
import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Iterator;

/**
 * Compares with Xaero's Minimap (only runs when it is in run/mods). Screenshots in build/run/clientGameTest/screenshots:
 * <ul>
 *     <li>a waypoint created the way Xaero does it, shared with Xaero's share function</li>
 *     <li>the same waypoint every 100 blocks, once drawn only by Xaero and once only by this mod</li>
 *     <li>this mod's share message read by Xaero, and Xaero's share message read by this mod</li>
 * </ul>
 */
public class XaeroComparisonGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("avies-waypoints-gametest");
	private static final int[] DISTANCES = {10, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 9900, 10000, 12000};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded("xaerominimap")) {
			LOGGER.warn("Xaero's Minimap is not installed, skipping the comparison");
			return;
		}

		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, type, time) -> {
			LOGGER.info("[chat] raw player chat: {}", message.getString());
			return true;
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			LOGGER.info("[chat] raw system chat: {}", message.getString());
			return true;
		});
		context.runOnClient(minecraft -> WaypointsConfig.ENABLED.set(false));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getServer().runCommand("gamemode spectator @a");
			singleplayer.getServer().runCommand("time set day");
			singleplayer.getServer().runCommand("gamerule advance_time false");
			singleplayer.getServer().runCommand("weather clear");
			context.waitTicks(60);

			BlockPos pos = context.computeOnClient(minecraft -> minecraft.player.blockPosition());
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();
			LOGGER.info("[test] waypoint position {} {} {}", x, y, z);

			// Same path as Xaero's "Add" button in chat: opens Xaero's add waypoint screen
			String add = "xaero_waypoint_add:Test:T:" + x + ":" + y + ":" + z + ":12:false:0:Internal-overworld";
			context.runOnClient(minecraft -> minecraft.player.connection.sendUnattendedCommand(add, null));
			context.waitTicks(10);
			screenshot(context, "01_xaero_add_screen");
			context.clickScreenButton("gui.xaero_confirm");
			context.waitTicks(10);

			context.runOnClient(minecraft -> xaero(XaeroComparisonGameTest::shareFirstXaeroWaypoint));
			context.waitTicks(5);
			context.clickScreenButton("gui.yes");
			context.waitTicks(20);
			screenshot(context, "02_xaero_share_chat");

			// Same waypoint in this mod
			context.runOnClient(minecraft -> WaypointStore.add(WaypointStore.currentDimension(), new Waypoint("Test", "T", x, y, z, 12)));
			context.setScreen(() -> WaypointEditScreen.create(null));
			context.waitTicks(5);
			screenshot(context, "03_avie_edit_screen");
			context.setScreen(() -> new WaypointListScreen(null));
			context.waitTicks(5);
			screenshot(context, "04_avie_list_screen");
			context.setScreen(() -> null);

			for (int distance : DISTANCES) {
				teleport(singleplayer, context, x, y, z, distance, 0);
				showOnly(context, true);
				screenshot(context, "10_" + distance + "_xaero");
				showOnly(context, false);
				screenshot(context, "10_" + distance + "_avie");
			}
			// Looking 30° to the side: not pointed at, so no distance
			teleport(singleplayer, context, x, y, z, 100, 30);
			showOnly(context, true);
			screenshot(context, "11_side_xaero");
			showOnly(context, false);
			screenshot(context, "11_side_avie");
			showOnly(context, true);

			// This mod shares, Xaero reads it
			context.runOnClient(minecraft -> ChatShareHandler.share(WaypointStore.current().getFirst(), WaypointStore.currentDimension()));
			context.waitTicks(20);
			screenshot(context, "20_avie_share_read_by_xaero");

			// Xaero shares, this mod reads it
			context.runOnClient(minecraft -> ChatShareHandler.deferToXaero = false);
			context.runOnClient(minecraft -> xaero(XaeroComparisonGameTest::shareFirstXaeroWaypoint));
			context.waitTicks(5);
			context.clickScreenButton("gui.yes");
			context.waitTicks(20);
			screenshot(context, "21_xaero_share_read_by_avie");
		}
	}

	private static void teleport(TestSingleplayerContext singleplayer, ClientGameTestContext context, int x, int y, int z, int distance, int yawOffset) {
		// Facing the waypoint means facing -x, which is yaw 90
		singleplayer.getServer().runCommand("tp @a " + (x + distance + 0.5) + " " + y + " " + (z + 0.5) + " " + (90 + yawOffset) + " 0");
		context.waitTicks(20);
	}

	/** True: only Xaero draws the waypoint, false: only this mod. */
	private static void showOnly(ClientGameTestContext context, boolean xaero) {
		context.runOnClient(minecraft -> {
			WaypointsConfig.ENABLED.set(!xaero);
			xaero(() -> setXaeroWaypointDisabled(!xaero));
		});
		context.waitTicks(3);
	}

	private static void screenshot(ClientGameTestContext context, String name) {
		LOGGER.info("[test] screenshot {}", context.takeScreenshot(name));
	}

	private interface Reflective {
		void run() throws ReflectiveOperationException;
	}

	private static void xaero(Reflective action) {
		try {
			action.run();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Xaero's Minimap call failed", e);
		}
	}

	// ------------------------------------------------ Xaero via reflection (no compile dependency on Xaero)

	private static Object xaeroSession() throws ReflectiveOperationException {
		Object minimap = Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
		return call(minimap, "getCurrentSession");
	}

	private static Object xaeroWorld() throws ReflectiveOperationException {
		return call(call(xaeroSession(), "getWorldManager"), "getCurrentWorld");
	}

	private static Object firstXaeroWaypoint() throws ReflectiveOperationException {
		Iterator<?> waypoints = ((Iterable<?>) call(call(xaeroWorld(), "getCurrentWaypointSet"), "getWaypoints")).iterator();
		if (!waypoints.hasNext()) {
			throw new IllegalStateException("Xaero has no waypoint");
		}
		return waypoints.next();
	}

	private static void setXaeroWaypointDisabled(boolean disabled) throws ReflectiveOperationException {
		Object waypoint = firstXaeroWaypoint();
		waypoint.getClass().getMethod("setDisabled", boolean.class).invoke(waypoint, disabled);
	}

	/** getSharing().shareWaypoint(screen, waypoint, world), opens Xaero's confirm screen. */
	private static void shareFirstXaeroWaypoint() throws ReflectiveOperationException {
		Object sharing = call(call(xaeroSession(), "getWaypointSession"), "getSharing");
		for (Method method : sharing.getClass().getMethods()) {
			if (method.getName().equals("shareWaypoint")) {
				method.invoke(sharing, (Screen) null, firstXaeroWaypoint(), xaeroWorld());
				return;
			}
		}
		throw new NoSuchMethodException("shareWaypoint");
	}

	private static Object call(Object target, String method) throws ReflectiveOperationException {
		return target.getClass().getMethod(method).invoke(target);
	}
}
