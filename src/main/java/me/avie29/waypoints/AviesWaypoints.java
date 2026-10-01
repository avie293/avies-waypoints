package me.avie29.waypoints;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.avie29.tabbylib.api.TabbyLibApi;
import me.avie29.waypoints.config.WaypointsConfig;
import me.avie29.waypoints.gui.WaypointListScreen;
import me.avie29.waypoints.render.WaypointRenderer;
import me.avie29.waypoints.share.ChatShareHandler;
import me.avie29.waypoints.waypoint.DeathWaypoints;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AviesWaypoints implements ClientModInitializer {
	public static final String MOD_ID = "avies-waypoints";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static boolean wasDead;

	@Override
	public void onInitializeClient() {
		Keybinds.register();
		WaypointsConfig.init();
		ChatShareHandler.register();

		// Below the chat, so chat and screens stay readable
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("waypoints"), new WaypointRenderer());

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			WaypointStore.tick(minecraft);
			Keybinds.tick(minecraft);
			checkDeath(minecraft);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> WaypointStore.save());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommands.literal("avieswaypoints")
				.executes(context -> {
					Minecraft minecraft = Minecraft.getInstance();
					// Commands run while the chat is still open, so the screen is set afterwards
					minecraft.execute(() -> minecraft.gui.setScreen(new WaypointListScreen(null)));
					return 1;
				})
				.then(ClientCommands.literal("config").executes(context -> {
					TabbyLibApi.openScreen(MOD_ID);
					return 1;
				}))
				.then(ClientCommands.literal("add_shared")
					.then(ClientCommands.argument("id", IntegerArgumentType.integer(0)).executes(context -> {
						ChatShareHandler.addShared(IntegerArgumentType.getInteger(context, "id"));
						return 1;
					})))));
	}

	/** A waypoint where the player died, see {@link DeathWaypoints}. */
	private static void checkDeath(Minecraft minecraft) {
		boolean dead = minecraft.player != null && minecraft.player.isDeadOrDying();
		if (dead && !wasDead && WaypointsConfig.DEATH_WAYPOINTS.get()) {
			String dimension = WaypointStore.currentDimension();
			if (dimension != null) {
				DeathWaypoints.onDeath(dimension, minecraft.player.blockPosition());
			}
		}
		wasDead = dead;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
