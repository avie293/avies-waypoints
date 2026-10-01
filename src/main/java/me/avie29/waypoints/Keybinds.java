package me.avie29.waypoints;

import com.mojang.blaze3d.platform.InputConstants;
import me.avie29.tabbylib.api.TabbyLibApi;
import me.avie29.waypoints.config.WaypointsConfig;
import me.avie29.waypoints.gui.WaypointEditScreen;
import me.avie29.waypoints.gui.WaypointListScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(AviesWaypoints.id("waypoints"));

	public static final KeyMapping ADD = new KeyMapping("key.avies-waypoints.add", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
	public static final KeyMapping LIST = new KeyMapping("key.avies-waypoints.list", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY);
	public static final KeyMapping TOGGLE = new KeyMapping("key.avies-waypoints.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
	public static final KeyMapping CONFIG = new KeyMapping("key.avies-waypoints.config", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

	private Keybinds() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(ADD);
		KeyMappingHelper.registerKeyMapping(LIST);
		KeyMappingHelper.registerKeyMapping(TOGGLE);
		KeyMappingHelper.registerKeyMapping(CONFIG);
	}

	public static void tick(Minecraft minecraft) {
		while (ADD.consumeClick()) {
			if (minecraft.player != null && minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(WaypointEditScreen.create(null));
			}
		}
		while (LIST.consumeClick()) {
			if (minecraft.player != null && minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new WaypointListScreen(null));
			}
		}
		while (TOGGLE.consumeClick()) {
			WaypointsConfig.ENABLED.set(!WaypointsConfig.ENABLED.get());
			WaypointsConfig.CONFIG.save();
		}
		while (CONFIG.consumeClick()) {
			TabbyLibApi.openScreen(AviesWaypoints.MOD_ID);
		}
	}
}
