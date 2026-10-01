package me.avie29.waypoints.gametest;

import me.avie29.waypoints.gui.WaypointListScreen;
import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Ticks waypoints in the list with the mouse, hides and deletes them, with screenshots of every step. */
public class WaypointListGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("avies-waypoints-gametest");
	private static final List<String> NAMES = List.of("Alpha", "Bravo", "Charlie", "Delta", "Echo");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
			context.waitTicks(40);
			context.runOnClient(minecraft -> {
				String dimension = WaypointStore.currentDimension();
				for (int i = 0; i < NAMES.size(); i++) {
					WaypointStore.add(dimension, new Waypoint(NAMES.get(i), NAMES.get(i).substring(0, 1), i * 50, 64, 0, 9 + i));
				}
			});

			context.setScreen(() -> new WaypointListScreen(null));
			context.waitTicks(5);
			screenshot(context, "30_list");

			clickCheckbox(context, 1, false);
			clickCheckbox(context, 3, false);
			screenshot(context, "31_list_two_ticked");
			clickButton(context, "Hide (2)");
			screenshot(context, "32_list_two_hidden");
			assertVisible(context, true, false, true, false, true);

			// Tick Alpha, then shift click Echo: everything from Alpha to Echo
			clickCheckbox(context, 0, false);
			clickCheckbox(context, 4, true);
			screenshot(context, "33_list_all_ticked");
			assertButton(context, "Select None");

			clickButton(context, "Delete (5)");
			screenshot(context, "34_delete_confirm");
			context.clickScreenButton("gui.yes");
			context.waitTicks(5);
			screenshot(context, "35_list_after_delete");
			int left = context.computeOnClient(minecraft -> WaypointStore.current().size());
			if (left != 0) {
				throw new AssertionError("Expected all waypoints to be deleted, " + left + " are left");
			}
			LOGGER.info("[test] waypoint list test passed");
		}
	}

	private static AbstractSelectionList<?> list(Minecraft minecraft) {
		for (GuiEventListener child : minecraft.gui.screen().children()) {
			if (child instanceof AbstractSelectionList<?> list) {
				return list;
			}
		}
		throw new AssertionError("No list on the screen");
	}

	/** Moves the real cursor to the checkbox of a row and clicks. */
	private static void clickCheckbox(ClientGameTestContext context, int row, boolean shift) {
		double[] pos = context.computeOnClient(minecraft -> {
			AbstractSelectionList<?> list = list(minecraft);
			int scale = minecraft.getWindow().getGuiScale();
			return new double[]{(list.getRowLeft() + 7) * scale, (list.getRowTop(row) + 11) * scale};
		});
		click(context, pos, shift);
	}

	private static void clickButton(ClientGameTestContext context, String text) {
		double[] pos = context.computeOnClient(minecraft -> {
			int scale = minecraft.getWindow().getGuiScale();
			for (GuiEventListener child : minecraft.gui.screen().children()) {
				if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(text)) {
					return new double[]{(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("No button \"" + text + "\"");
		});
		click(context, pos, false);
	}

	private static void click(ClientGameTestContext context, double[] pos, boolean shift) {
		context.getInput().setCursorPos(pos[0], pos[1]);
		if (shift) {
			context.getInput().holdShift();
		}
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		if (shift) {
			context.getInput().releaseShift();
		}
		context.waitTicks(2);
	}

	private static void assertButton(ClientGameTestContext context, String text) {
		boolean found = context.computeOnClient(minecraft -> minecraft.gui.screen().children().stream()
			.anyMatch(child -> child instanceof AbstractWidget widget && widget.getMessage().getString().equals(text)));
		if (!found) {
			throw new AssertionError("No button \"" + text + "\"");
		}
	}

	private static void assertVisible(ClientGameTestContext context, boolean... expected) {
		List<Boolean> actual = context.computeOnClient(minecraft -> WaypointStore.current().stream().map(waypoint -> waypoint.visible).toList());
		for (int i = 0; i < expected.length; i++) {
			if (actual.get(i) != expected[i]) {
				throw new AssertionError("Visibility of " + NAMES.get(i) + " should be " + expected[i] + ", all: " + actual);
			}
		}
	}

	private static void screenshot(ClientGameTestContext context, String name) {
		LOGGER.info("[test] screenshot {}", context.takeScreenshot(name));
	}
}
