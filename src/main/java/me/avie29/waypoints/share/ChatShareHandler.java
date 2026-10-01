package me.avie29.waypoints.share;

import me.avie29.waypoints.config.WaypointsConfig;
import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointColor;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns shared waypoints in chat (Xaero's format) into a readable line with an "Add" button,
 * and sends own waypoints to the chat.
 */
public final class ChatShareHandler {
	private static final int MAX_PENDING = 100;

	/** Shared waypoints seen in chat, by the id used in the "Add" button's command. */
	private static final Map<Integer, XaeroShareFormat.Shared> PENDING = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, XaeroShareFormat.Shared> eldest) {
			return this.size() > MAX_PENDING;
		}
	};
	private static int nextId;
	/** With Xaero's Minimap installed, Xaero shows shared waypoints with its own "Add" button. */
	public static boolean deferToXaero = FabricLoader.getInstance().isModLoaded("xaerominimap");

	private ChatShareHandler() {
	}

	public static void register() {
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) ->
			handle(message, sender != null ? sender.name() : null));
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || handle(message, null));
	}

	/** Returns false (hides the raw message) when it contained a shared waypoint. */
	private static boolean handle(Component message, @Nullable String sender) {
		if (!WaypointsConfig.DETECT_SHARED.get() || deferToXaero) {
			return true;
		}
		String text = message.getString();
		XaeroShareFormat.Shared shared = XaeroShareFormat.find(text);
		if (shared == null) {
			return true;
		}

		int id = nextId++;
		PENDING.put(id, shared);
		Component name;
		if (sender != null) {
			name = Component.literal(sender);
		} else {
			// Like Xaero: system messages usually carry the player name as "<Name>"
			int open = text.indexOf('<');
			int close = open < 0 ? -1 : text.indexOf('>', open);
			name = close > open + 1 ? Component.literal(text.substring(open + 1, close)) : Component.translatable("avies-waypoints.chat.server");
		}
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(sharedMessage(name, shared, id));
		return false;
	}

	/** Same look as Xaero: 'Steve shared a waypoint called "Home" from overworld! [Add]', the whole line is clickable. */
	private static Component sharedMessage(Component sender, XaeroShareFormat.Shared shared, int id) {
		Waypoint waypoint = shared.waypoint();
		MutableComponent line = shared.dimension() != null
			? Component.translatable("avies-waypoints.chat.shared_dimension", sender, waypoint.name, dimensionPath(shared.dimension()))
			: Component.translatable("avies-waypoints.chat.shared", sender, waypoint.name);
		line.append(Component.translatable("avies-waypoints.chat.add").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.UNDERLINE));
		return line.withStyle(style -> style.applyFormat(ChatFormatting.GRAY)
			.withClickEvent(new ClickEvent.RunCommand("/avieswaypoints add_shared " + id))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(coordinates(waypoint)))));
	}

	private static String dimensionPath(String dimension) {
		return dimension.substring(dimension.indexOf(':') + 1);
	}

	/** "[Name]" in the waypoint color, hovering shows coordinates and dimension. */
	public static Component waypointName(Waypoint waypoint, @Nullable String dimension) {
		MutableComponent hover = Component.literal(coordinates(waypoint));
		if (dimension != null) {
			hover.append("\n").append(Component.literal(dimension).withStyle(ChatFormatting.GRAY));
		}
		int color = waypoint.color == 0 ? 0x555555 : WaypointColor.rgb(waypoint.color);
		return Component.literal("[" + waypoint.name + "]")
			.withStyle(style -> style.withColor(TextColor.fromRgb(color)).withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	public static String coordinates(Waypoint waypoint) {
		return waypoint.x + ", " + (waypoint.yIncluded ? waypoint.y : "~") + ", " + waypoint.z;
	}

	/** Called by the "Add" button in chat. */
	public static void addShared(int id) {
		Minecraft minecraft = Minecraft.getInstance();
		XaeroShareFormat.Shared shared = PENDING.get(id);
		String dimension = shared == null ? null : shared.dimension() != null ? shared.dimension() : WaypointStore.currentDimension();
		if (shared == null || dimension == null) {
			minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("avies-waypoints.chat.expired").withStyle(ChatFormatting.RED));
			return;
		}

		Waypoint waypoint = shared.waypoint();
		boolean exists = WaypointStore.get(dimension).stream().anyMatch(other ->
			other.x == waypoint.x && other.y == waypoint.y && other.z == waypoint.z && other.name.equals(waypoint.name));
		if (exists) {
			minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("avies-waypoints.chat.exists", waypointName(waypoint, dimension))
				.withStyle(ChatFormatting.GRAY));
			return;
		}

		// A copy, so adding twice from the same chat line creates two separate waypoints
		Waypoint copy = new Waypoint(waypoint.name, waypoint.initials, waypoint.x, waypoint.y, waypoint.z, waypoint.color);
		copy.yIncluded = waypoint.yIncluded;
		copy.rotateOnTp = waypoint.rotateOnTp;
		copy.yaw = waypoint.yaw;
		WaypointStore.add(dimension, copy);
		minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("avies-waypoints.chat.added", waypointName(copy, dimension))
			.withStyle(ChatFormatting.GRAY));
	}

	/** Sends a waypoint to the chat in Xaero's format, so players with Xaero's Minimap can add it too. */
	public static void share(Waypoint waypoint, String dimension) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.connection.sendChat(XaeroShareFormat.format(waypoint, dimension));
		}
	}
}
