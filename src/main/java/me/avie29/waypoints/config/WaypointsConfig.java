package me.avie29.waypoints.config;

import me.avie29.tabbylib.api.TabbyConfig;
import me.avie29.tabbylib.api.option.BooleanOption;
import me.avie29.tabbylib.api.option.DoubleOption;
import me.avie29.tabbylib.api.option.EnumOption;
import me.avie29.tabbylib.api.option.IntOption;
import me.avie29.tabbylib.api.option.KeyBindOption;
import me.avie29.waypoints.AviesWaypoints;
import me.avie29.waypoints.Keybinds;
import net.minecraft.network.chat.Component;

public final class WaypointsConfig {
	/** When a text next to the waypoint icon is shown. */
	public enum ShowMode {
		ALWAYS,
		LOOKING,
		NEVER
	}

	// ------------------------------------------------ display
	public static final BooleanOption ENABLED = BooleanOption.builder("enabled", true).build();
	public static final EnumOption<ShowMode> SHOW_DISTANCE = EnumOption.builder("showDistance", ShowMode.LOOKING).dependsOn(ENABLED).build();
	public static final BooleanOption NAME_WITH_DISTANCE = BooleanOption.builder("nameWithDistance", true).dependsOn(ENABLED).build();
	public static final DoubleOption SCALE = DoubleOption.builder("scale", 1.0).slider(0.5, 3.0, 0.1).dependsOn(ENABLED).build();
	public static final IntOption MAX_DISTANCE = IntOption.builder("maxDistance", 0)
		.range(0, 1_000_000)
		.formatter(value -> value == 0 ? Component.translatable("config.avies-waypoints.maxDistance.unlimited") : Component.literal(value + " m"))
		.dependsOn(ENABLED)
		.build();
	public static final IntOption OPACITY = IntOption.builder("opacity", 80)
		.slider(10, 100, 5)
		.formatter(value -> Component.literal(value + "%"))
		.dependsOn(ENABLED)
		.build();

	// ------------------------------------------------ waypoints
	public static final BooleanOption DEATH_WAYPOINTS = BooleanOption.builder("deathWaypoints", true).build();
	public static final BooleanOption KEEP_OLD_DEATHS = BooleanOption.builder("keepOldDeaths", true).dependsOn(DEATH_WAYPOINTS).build();
	public static final BooleanOption DETECT_SHARED = BooleanOption.builder("detectShared", true).build();

	// ------------------------------------------------ locator bar (the vanilla bar above the hotbar)
	public static final BooleanOption PLAYER_HEADS = BooleanOption.builder("playerHeads", false).build();
	public static final BooleanOption WAYPOINTS_ON_BAR = BooleanOption.builder("waypointsOnBar", false).dependsOn(PLAYER_HEADS).build();

	public static TabbyConfig CONFIG;

	private WaypointsConfig() {
	}

	/** Call after the key mappings are registered. */
	public static void init() {
		CONFIG = TabbyConfig.builder(AviesWaypoints.MOD_ID)
			.category("general", category -> category
				.group("display", group -> group.add(ENABLED, SHOW_DISTANCE, NAME_WITH_DISTANCE, SCALE, OPACITY, MAX_DISTANCE))
				.group("waypoints", group -> group.add(DEATH_WAYPOINTS, KEEP_OLD_DEATHS, DETECT_SHARED))
				.group("locatorBar", group -> group.add(PLAYER_HEADS, WAYPOINTS_ON_BAR))
				.group("keys", group -> group.add(
					KeyBindOption.builder("keyAdd", Keybinds.ADD).build(),
					KeyBindOption.builder("keyList", Keybinds.LIST).build(),
					KeyBindOption.builder("keyToggle", Keybinds.TOGGLE).build(),
					KeyBindOption.builder("keyConfig", Keybinds.CONFIG).build())))
			.build();
	}
}
