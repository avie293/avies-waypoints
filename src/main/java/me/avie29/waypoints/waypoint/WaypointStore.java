package me.avie29.waypoints.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import me.avie29.waypoints.AviesWaypoints;
import me.avie29.waypoints.config.WaypointsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
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
import java.util.stream.Stream;

public final class WaypointStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type LIST_TYPE = new TypeToken<List<Waypoint>>() {
	}.getType();
	private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("awp").resolve("worlds");
	private static final Path OLD_DIR = FabricLoader.getInstance().getConfigDir().resolve(AviesWaypoints.MOD_ID).resolve("worlds");

	private static @Nullable String worldKey;
	private static final Map<String, List<Waypoint>> WAYPOINTS = new LinkedHashMap<>();

	private WaypointStore() {
	}

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
			load(minecraft);
			autoImportXaero(minecraft, key);
		}
	}

	private static void autoImportXaero(Minecraft minecraft, String key) {
		if (!WaypointsConfig.IMPORT_XAERO.get() || XaeroImporter.isImported(key)) {
			return;
		}
		XaeroImporter.markImported(key);
		int count = XaeroImporter.importCurrentWorld(minecraft);
		if (count > 0) {
			minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("avies-waypoints.import.done", count).withStyle(ChatFormatting.GRAY));
		}
	}

	public static List<Waypoint> get(String dimension) {
		return WAYPOINTS.computeIfAbsent(dimension, d -> new ArrayList<>());
	}

	public static List<Waypoint> current() {
		String dimension = currentDimension();
		return dimension == null ? List.of() : get(dimension);
	}

	public static Map<String, List<Waypoint>> all() {
		return WAYPOINTS;
	}

	public static List<String> dimensions() {
		List<String> dimensions = new ArrayList<>();
		WAYPOINTS.forEach((dimension, list) -> {
			if (!list.isEmpty()) {
				dimensions.add(dimension);
			}
		});
		String current = currentDimension();
		if (current != null && !dimensions.contains(current)) {
			dimensions.add(0, current);
		}
		return dimensions;
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

	public static void removeAll(String dimension, List<Waypoint> waypoints) {
		get(dimension).removeAll(waypoints);
		save();
	}

	private static String currentWorldKey(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			return folderName(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString());
		}
		ServerData serverData = minecraft.getCurrentServer();
		if (serverData != null) {
			return (serverData.isRealm() ? "realms_" : "") + folderName(serverData.ip);
		}
		return "unknown";
	}

	static String folderName(String name) {
		String folder = name.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_").strip();
		while (folder.endsWith(".")) {
			folder = folder.substring(0, folder.length() - 1);
		}
		return folder.isEmpty() ? "unknown" : folder;
	}

	static String fileName(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> "overworld";
			case "minecraft:the_nether" -> "nether";
			case "minecraft:the_end" -> "end";
			default -> {
				int colon = dimension.indexOf(':');
				yield dimension.substring(0, colon) + "$" + dimension.substring(colon + 1).replace('/', '%');
			}
		};
	}

	static @Nullable String dimensionOfFile(String fileName) {
		return switch (fileName) {
			case "overworld" -> "minecraft:overworld";
			case "nether" -> "minecraft:the_nether";
			case "end" -> "minecraft:the_end";
			default -> {
				int dollar = fileName.indexOf('$');
				yield dollar > 0 ? fileName.substring(0, dollar) + ":" + fileName.substring(dollar + 1).replace('%', '/') : null;
			}
		};
	}

	private static Path folder() {
		return ROOT.resolve(worldKey);
	}

	private static void load(Minecraft minecraft) {
		Path folder = folder();
		if (!Files.isDirectory(folder)) {
			migrateOldFile(minecraft);
			return;
		}
		try (Stream<Path> files = Files.list(folder)) {
			for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
				String name = file.getFileName().toString();
				String dimension = dimensionOfFile(name.substring(0, name.length() - ".json".length()));
				if (dimension != null) {
					loadFile(file, dimension);
				}
			}
		} catch (IOException e) {
			AviesWaypoints.LOGGER.error("Could not read waypoints in {}", folder, e);
		}
	}

	private static void loadFile(Path file, String dimension) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			List<Waypoint> list = GSON.fromJson(reader, LIST_TYPE);
			WAYPOINTS.put(dimension, fix(list));
		} catch (Exception e) {
			AviesWaypoints.LOGGER.error("Could not read waypoints {}, a backup is created", file, e);
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
		}
	}

	private static List<Waypoint> fix(@Nullable List<Waypoint> loaded) {
		List<Waypoint> list = loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
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
		return list;
	}

	private static void migrateOldFile(Minecraft minecraft) {
		Path oldFile = OLD_DIR.resolve(oldWorldKey(minecraft) + ".json");
		if (!Files.exists(oldFile)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(oldFile, StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				WAYPOINTS.put(entry.getKey(), fix(GSON.fromJson(entry.getValue(), LIST_TYPE)));
			}
		} catch (Exception e) {
			AviesWaypoints.LOGGER.error("Could not migrate waypoints {}", oldFile, e);
			return;
		}
		if (save()) {
			try {
				Files.delete(oldFile);
				for (Path folder : List.of(OLD_DIR, OLD_DIR.getParent())) {
					try (Stream<Path> rest = Files.list(folder)) {
						if (rest.findAny().isEmpty()) {
							Files.delete(folder);
						}
					}
				}
			} catch (IOException e) {
				AviesWaypoints.LOGGER.warn("Could not delete the migrated file {}", oldFile, e);
			}
			AviesWaypoints.LOGGER.info("Moved waypoints from {} to {}", oldFile, folder());
		}
	}

	private static String oldWorldKey(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			return "sp_" + oldSanitize(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString());
		}
		ServerData serverData = minecraft.getCurrentServer();
		if (serverData != null) {
			return (serverData.isRealm() ? "realms_" : "mp_") + oldSanitize(serverData.ip);
		}
		return "unknown";
	}

	private static String oldSanitize(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	public static boolean save() {
		if (worldKey == null) {
			return false;
		}
		Path folder = folder();
		boolean saved = true;
		for (Map.Entry<String, List<Waypoint>> entry : WAYPOINTS.entrySet()) {
			Path file = folder.resolve(fileName(entry.getKey()) + ".json");
			try {
				if (entry.getValue().isEmpty()) {
					Files.deleteIfExists(file);
					continue;
				}
				Files.createDirectories(folder);
				Path temp = file.resolveSibling(file.getFileName() + ".tmp");
				try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
					GSON.toJson(entry.getValue(), LIST_TYPE, writer);
				}
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				saved = false;
				AviesWaypoints.LOGGER.error("Could not save waypoints {}", file, e);
			}
		}
		return saved;
	}
}
