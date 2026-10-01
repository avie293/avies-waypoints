package me.avie29.waypoints.waypoint;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.avie29.waypoints.AviesWaypoints;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class XaeroImporter {
	private static final Path GAME_DIR = FabricLoader.getInstance().getGameDir();
	private static final Path IMPORTED_FILE = FabricLoader.getInstance().getGameDir().resolve("awp").resolve("xaero-imported.json");
	private static final Gson GSON = new Gson();
	private static final int TYPE_DEATH = 1;
	private static final int TYPE_OLD_DEATH = 2;

	private XaeroImporter() {
	}

	public static boolean isImported(String worldKey) {
		return readImported().contains(worldKey);
	}

	public static void markImported(String worldKey) {
		Set<String> imported = readImported();
		if (imported.add(worldKey)) {
			try {
				Files.createDirectories(IMPORTED_FILE.getParent());
				try (Writer writer = Files.newBufferedWriter(IMPORTED_FILE, StandardCharsets.UTF_8)) {
					GSON.toJson(imported, writer);
				}
			} catch (IOException e) {
				AviesWaypoints.LOGGER.error("Could not save {}", IMPORTED_FILE, e);
			}
		}
	}

	private static Set<String> readImported() {
		if (!Files.exists(IMPORTED_FILE)) {
			return new LinkedHashSet<>();
		}
		try (Reader reader = Files.newBufferedReader(IMPORTED_FILE, StandardCharsets.UTF_8)) {
			Set<String> imported = GSON.fromJson(reader, new TypeToken<LinkedHashSet<String>>() {
			}.getType());
			return imported == null ? new LinkedHashSet<>() : imported;
		} catch (Exception e) {
			AviesWaypoints.LOGGER.error("Could not read {}", IMPORTED_FILE, e);
			return new LinkedHashSet<>();
		}
	}

	public static int importCurrentWorld(Minecraft minecraft) {
		Map<String, List<Waypoint>> found = new LinkedHashMap<>();
		for (Path container : containerFolders(minecraft)) {
			readContainer(container, found);
		}

		boolean hasLatestDeath = WaypointStore.all().values().stream()
			.flatMap(List::stream)
			.anyMatch(waypoint -> waypoint.type == Waypoint.Type.DEATH);
		List<Waypoint> formerLatest = new ArrayList<>();
		int added = 0;
		for (Map.Entry<String, List<Waypoint>> entry : found.entrySet()) {
			List<Waypoint> existing = WaypointStore.get(entry.getKey());
			for (Waypoint waypoint : entry.getValue()) {
				if (existing.stream().noneMatch(other -> same(other, waypoint))) {
					if (waypoint.type == Waypoint.Type.DEATH) {
						if (hasLatestDeath) {
							waypoint.type = Waypoint.Type.OLD_DEATH;
							formerLatest.add(waypoint);
						}
						hasLatestDeath = true;
					}
					existing.add(waypoint);
					added++;
				}
			}
		}
		if (added > 0) {
			numberOldDeaths(formerLatest);
			WaypointStore.save();
		}
		AviesWaypoints.LOGGER.info("Imported {} waypoints from Xaero's Minimap", added);
		return added;
	}

	private static boolean same(Waypoint a, Waypoint b) {
		return a.x == b.x && a.y == b.y && a.z == b.z && a.name.equals(b.name);
	}

	private static void numberOldDeaths(List<Waypoint> formerLatest) {
		int next = WaypointStore.all().values().stream()
			.flatMap(List::stream)
			.mapToInt(waypoint -> waypoint.deathNumber)
			.max().orElse(0) + 1;
		List<Waypoint> unnumbered = new ArrayList<>(WaypointStore.all().values().stream()
			.flatMap(List::stream)
			.filter(waypoint -> waypoint.type == Waypoint.Type.OLD_DEATH && waypoint.deathNumber == 0)
			.toList());
		unnumbered.sort((a, b) -> Boolean.compare(formerLatest.contains(a), formerLatest.contains(b)));
		for (Waypoint waypoint : unnumbered) {
			waypoint.deathNumber = next++;
			if (waypoint.autoName) {
				waypoint.name = Component.translatable("avies-waypoints.death.old", waypoint.deathNumber).getString();
			}
		}
	}

	private static List<Path> containerFolders(Minecraft minecraft) {
		List<String> names = new ArrayList<>();
		IntegratedServer server = minecraft.getSingleplayerServer();
		ServerData serverData = minecraft.getCurrentServer();
		if (server != null) {
			names.addAll(singleplayerContainerNames(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString()));
		} else if (serverData != null && !serverData.isRealm()) {
			names.addAll(multiplayerContainerNames(serverData.ip));
		}

		List<Path> folders = new ArrayList<>();
		for (Path root : List.of(GAME_DIR.resolve("xaero").resolve("minimap"), GAME_DIR.resolve("XaeroWaypoints"))) {
			if (!Files.isDirectory(root)) {
				continue;
			}
			try (Stream<Path> children = Files.list(root)) {
				children.filter(Files::isDirectory)
					.filter(folder -> names.stream().anyMatch(name -> name.equalsIgnoreCase(folder.getFileName().toString())))
					.forEach(folders::add);
			} catch (IOException e) {
				AviesWaypoints.LOGGER.error("Could not list {}", root, e);
			}
		}
		return folders;
	}

	static List<String> singleplayerContainerNames(String worldFolder) {
		String base = worldFolder.replace("_", "%us%").replace("/", "%fs%").replace("\\", "%bs%");
		return List.of(base.replace("[", "%lb%").replace("]", "%rb%"), base);
	}

	static List<String> multiplayerContainerNames(String address) {
		Set<String> names = new LinkedHashSet<>();
		for (int version = 0; version <= 4; version++) {
			String ip = address;
			int portDivider = version >= 1 && ip.indexOf(':') != ip.lastIndexOf(':') ? ip.lastIndexOf("]:") + 1 : ip.indexOf(':');
			if (portDivider > 0) {
				ip = ip.substring(0, portDivider);
			}
			while (ip.endsWith(".")) {
				ip = ip.substring(0, ip.length() - 1);
			}
			if (version >= 2) {
				ip = ip.replace("[", "").replace("]", "");
			}
			ip = ip.replace(":", version < 3 ? "§" : ".").replace("_", "%us%").replace("/", "%fs%").replace("\\", "%bs%");
			if (version >= 4) {
				ip = ip.trim();
			}
			names.add("Multiplayer_" + (ip.isEmpty() ? "Empty Address" : ip));
		}
		names.add("Multiplayer_Any Address");
		return List.copyOf(names);
	}

	static @Nullable String dimensionOf(String folder) {
		switch (folder) {
			case "dim%0":
				return "minecraft:overworld";
			case "dim%-1":
				return "minecraft:the_nether";
			case "dim%1":
				return "minecraft:the_end";
			default:
				break;
		}
		if (!folder.startsWith("dim%")) {
			return null;
		}
		String id = folder.substring("dim%".length());
		int dollar = id.indexOf('$');
		if (dollar <= 0) {
			return null;
		}
		String path = id.substring(dollar + 1).replace('%', '/');
		while (path.endsWith(",")) {
			path = path.substring(0, path.length() - 1) + ".";
		}
		return id.substring(0, dollar) + ":" + path;
	}

	private static void readContainer(Path container, Map<String, List<Waypoint>> found) {
		try (Stream<Path> children = Files.list(container)) {
			for (Path dimensionFolder : children.filter(Files::isDirectory).toList()) {
				String dimension = dimensionOf(dimensionFolder.getFileName().toString());
				if (dimension == null) {
					continue;
				}
				try (Stream<Path> files = Files.list(dimensionFolder)) {
					for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".txt")).toList()) {
						for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
							Waypoint waypoint = parseLine(line);
							if (waypoint != null) {
								List<Waypoint> list = found.computeIfAbsent(dimension, d -> new ArrayList<>());
								if (list.stream().noneMatch(other -> same(other, waypoint))) {
									list.add(waypoint);
								}
							}
						}
					}
				}
			}
		} catch (IOException e) {
			AviesWaypoints.LOGGER.error("Could not read Xaero's waypoints in {}", container, e);
		}
	}

	static @Nullable Waypoint parseLine(String line) {
		if (!line.startsWith("waypoint:")) {
			return null;
		}
		String[] args = line.split(":");
		if (args.length < 10) {
			return null;
		}
		try {
			boolean yIncluded = !args[4].equals("~");
			int type = Integer.parseInt(args[8]);
			if (type > TYPE_OLD_DEATH) {
				return null;
			}
			String name = args[1].replace("§§", ":");
			String initials = args[2].replace("§§", ":");
			Waypoint waypoint = new Waypoint(name, initials.isEmpty() ? Waypoint.defaultInitials(name) : initials,
				Integer.parseInt(args[3]), yIncluded ? Integer.parseInt(args[4]) : 64, Integer.parseInt(args[5]),
				Integer.parseInt(args[6]));
			waypoint.yIncluded = yIncluded;
			waypoint.visible = !args[7].equals("true");
			if (args.length > 11) {
				waypoint.rotateOnTp = args[10].equals("true");
				waypoint.yaw = Integer.parseInt(args[11]);
			}
			if (type == TYPE_DEATH || type == TYPE_OLD_DEATH) {
				waypoint.type = type == TYPE_DEATH ? Waypoint.Type.DEATH : Waypoint.Type.OLD_DEATH;
				waypoint.initials = DeathWaypoints.ICON;
				waypoint.autoName = name.startsWith("gui.xaero_");
				if (type == TYPE_DEATH && waypoint.autoName) {
					waypoint.name = Component.translatable("avies-waypoints.death.latest").getString();
				}
			} else if (name.startsWith("gui.xaero_")) {
				waypoint.name = Component.translatable(name).getString();
			}
			return waypoint;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
