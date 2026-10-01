package me.avie29.waypoints.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import me.avie29.waypoints.AviesWaypoints;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Waypoints of the world / server the player is in, grouped by dimension id
 * (e.g. "minecraft:overworld"). Saved in {@code config/avies-waypoints/worlds/<world>.json}.
 */
public final class WaypointStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type LIST_TYPE = new TypeToken<List<Waypoint>>() {
	}.getType();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(AviesWaypoints.MOD_ID).resolve("worlds");

	private static @Nullable String worldKey;
	private static final Map<String, List<Waypoint>> WAYPOINTS = new LinkedHashMap<>();

	private WaypointStore() {
	}

	/** Called every client tick: loads the waypoints when joining a world and saves them when leaving. */
	public static void tick(Minecraft minecraft) {
		String key = minecraft.level == null ? null : currentWorldKey(minecraft);
		if (key == null ? worldKey == null : key.equals(worldKey)) {
			return;
		}
		if (worldKey != null) {
			save();
		}
		WAYPOINTS.clear();
		worldKey = key;
		if (key != null) {
			load();
		}
	}

	/** Waypoints of a dimension. The list is live, call {@link #save()} after changing it. */
	public static List<Waypoint> get(String dimension) {
		return WAYPOINTS.computeIfAbsent(dimension, d -> new ArrayList<>());
	}

	/** Waypoints of the dimension the player is in, empty when not in a world. */
	public static List<Waypoint> current() {
		String dimension = currentDimension();
		return dimension == null ? List.of() : get(dimension);
	}

	public static @Nullable String currentDimension() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? null : minecraft.level.dimension().identifier().toString();
	}

	public static void add(String dimension, Waypoint waypoint) {
		get(dimension).add(waypoint);
		save();
	}

	public static void remove(String dimension, Waypoint waypoint) {
		get(dimension).remove(waypoint);
		save();
	}

	private static @Nullable String currentWorldKey(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			return "sp_" + sanitize(root.getFileName().toString());
		}
		ServerData serverData = minecraft.getCurrentServer();
		if (serverData != null) {
			return (serverData.isRealm() ? "realms_" : "mp_") + sanitize(serverData.ip);
		}
		return "unknown";
	}

	private static String sanitize(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	private static Path file() {
		return DIR.resolve(worldKey + ".json");
	}

	private static void load() {
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
				List<Waypoint> list = GSON.fromJson(entry.getValue(), LIST_TYPE);
				list.removeIf(waypoint -> waypoint == null || waypoint.name == null);
				for (Waypoint waypoint : list) {
					if (waypoint.initials == null || waypoint.initials.isEmpty()) {
						waypoint.initials = Waypoint.defaultInitials(waypoint.name);
					}
					if (waypoint.type == null) {
						waypoint.type = Waypoint.Type.NORMAL;
					}
					waypoint.color = WaypointColor.clamp(waypoint.color);
				}
				WAYPOINTS.put(entry.getKey(), new ArrayList<>(list));
			}
		} catch (Exception e) {
			AviesWaypoints.LOGGER.error("Could not read waypoints {}, a backup is created", file, e);
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// Nothing more we can do
			}
		}
	}

	public static void save() {
		if (worldKey == null) {
			return;
		}
		JsonObject json = new JsonObject();
		WAYPOINTS.forEach((dimension, list) -> {
			if (!list.isEmpty()) {
				json.add(dimension, GSON.toJsonTree(list, LIST_TYPE));
			}
		});
		Path file = file();
		try {
			Files.createDirectories(DIR);
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			AviesWaypoints.LOGGER.error("Could not save waypoints {}", file, e);
		}
	}
}
