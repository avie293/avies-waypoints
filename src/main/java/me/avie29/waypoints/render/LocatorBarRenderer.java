package me.avie29.waypoints.render;

import me.avie29.waypoints.config.WaypointsConfig;
import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointColor;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LocatorBarRenderer {
	private static final Identifier ARROW_UP = Identifier.withDefaultNamespace("hud/locator_bar_arrow_up");
	private static final Identifier ARROW_DOWN = Identifier.withDefaultNamespace("hud/locator_bar_arrow_down");
	private static final UUID WAYPOINT_ID = new UUID(0, 0);
	private static final int DOT_SIZE = 9;
	private static final int HEAD_SIZE = 8;
	private static final double VISIBLE_DEGREES = 60.0;
	private static final int POINTING_RANGE = 6;
	private static final float FAR_HEAD_ALPHA = 0.45F;

	private LocatorBarRenderer() {
	}

	private record Marker(int position, float distance, TrackedWaypoint.PitchDirection pitch, int color, Identifier dot,
						  @Nullable PlayerInfo player, float alpha, String name) {
	}

	public static boolean active() {
		return WaypointsConfig.PLAYER_HEADS.get();
	}

	public static boolean showsWaypoints() {
		return active() && WaypointsConfig.WAYPOINTS_ON_BAR.get() && WaypointStore.current().stream().anyMatch(waypoint -> waypoint.visible);
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int top) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity cameraEntity = minecraft.getCameraEntity();
		if (cameraEntity == null || minecraft.player == null) {
			return;
		}
		Level level = cameraEntity.level();
		PartialTickSupplier partialTick = entity -> deltaTracker.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
		List<Marker> markers = new ArrayList<>();

		if (WaypointsConfig.WAYPOINTS_ON_BAR.get()) {
			WaypointStyle style = minecraft.gui.hud.getWaypointStyles().get(WaypointStyleAssets.DEFAULT);
			int maxDistance = WaypointsConfig.MAX_DISTANCE.get();
			for (Waypoint waypoint : WaypointStore.current()) {
				if (!waypoint.visible) {
					continue;
				}
				int y = waypoint.yIncluded ? waypoint.y : Mth.floor(cameraEntity.getY());
				TrackedWaypoint tracked = TrackedWaypoint.setPosition(WAYPOINT_ID, new net.minecraft.world.waypoints.Waypoint.Icon(), new Vec3i(waypoint.x, y, waypoint.z));
				float distance = (float) Math.sqrt(tracked.distanceSquared(cameraEntity));
				if (maxDistance > 0 && waypoint.type != Waypoint.Type.DEATH && distance > maxDistance) {
					continue;
				}
				add(markers, minecraft, level, tracked, partialTick, distance, WaypointColor.argb(waypoint.color), style.sprite(distance), null, 1.0F, waypoint.name);
			}
		}

		minecraft.player.connection.getWaypointManager().forEachWaypoint(cameraEntity, tracked -> {
			if (tracked.id().left().map(uuid -> uuid.equals(cameraEntity.getUUID())).orElse(false)) {
				return;
			}
			net.minecraft.world.waypoints.Waypoint.Icon icon = tracked.icon();
			WaypointStyle style = minecraft.gui.hud.getWaypointStyles().get(icon.style);
			float distance = (float) Math.sqrt(tracked.distanceSquared(cameraEntity));
			int color = icon.color.orElseGet(() -> tracked.id().map(
				uuid -> ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F),
				name -> ARGB.setBrightness(ARGB.color(255, name.hashCode()), 0.9F)));
			PlayerInfo player = tracked.id().left().map(uuid -> minecraft.player.connection.getPlayerInfo(uuid)).orElse(null);
			float alpha = 1.0F - (1.0F - FAR_HEAD_ALPHA) * Mth.clamp((distance - style.nearDistance()) / (float) (style.farDistance() - style.nearDistance()), 0.0F, 1.0F);
			String name = player != null ? player.getProfile().name() : "";
			add(markers, minecraft, level, tracked, partialTick, distance, color, style.sprite(distance), player, alpha, name);
		});

		markers.sort(Comparator.comparingDouble(Marker::distance).reversed());
		int middle = Mth.ceil((graphics.guiWidth() - DOT_SIZE) / 2.0F);
		Marker pointed = null;
		for (Marker marker : markers) {
			draw(graphics, marker, middle + marker.position(), top);
			if (Math.abs(marker.position()) <= POINTING_RANGE && (pointed == null || Math.abs(marker.position()) < Math.abs(pointed.position()))) {
				pointed = marker;
			}
		}
		if (pointed != null) {
			label(graphics, minecraft, pointed, middle + pointed.position(), top);
		}
	}

	private static void add(List<Marker> markers, Minecraft minecraft, Level level, TrackedWaypoint tracked, PartialTickSupplier partialTick,
							float distance, int color, Identifier dot, @Nullable PlayerInfo player, float alpha, String name) {
		double angle = tracked.yawAngleToCamera(level, minecraft.gameRenderer.mainCamera(), partialTick);
		if (angle <= -VISIBLE_DEGREES || angle > VISIBLE_DEGREES) {
			return;
		}
		int position = Mth.floor(angle * 173.0 / 2.0 / VISIBLE_DEGREES);
		TrackedWaypoint.PitchDirection pitch = tracked.pitchDirectionToCamera(level, minecraft.gameRenderer, partialTick);
		markers.add(new Marker(position, distance, pitch, color, dot, player, alpha, name));
	}

	private static void draw(GuiGraphicsExtractor graphics, Marker marker, int x, int top) {
		boolean head = marker.player() != null;
		if (head) {
			int alpha = Math.round(marker.alpha() * 255);
			int headX = x;
			int headY = top - 2;
			graphics.fill(headX - 1, headY - 1, headX + HEAD_SIZE + 1, headY + HEAD_SIZE + 1, ARGB.color(alpha, marker.color()));
			PlayerFaceExtractor.extractRenderState(graphics, marker.player().getSkin(), headX, headY, HEAD_SIZE, ARGB.color(alpha, 0xFFFFFF));
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, marker.dot(), x, top - 2, DOT_SIZE, DOT_SIZE, marker.color());
		}

		if (marker.pitch() == TrackedWaypoint.PitchDirection.UP) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_UP, x + 1, top - (head ? 8 : 6), 7, 5);
		} else if (marker.pitch() == TrackedWaypoint.PitchDirection.DOWN) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_DOWN, x + 1, top + (head ? 8 : 6), 7, 5);
		}
	}

	private static void label(GuiGraphicsExtractor graphics, Minecraft minecraft, Marker marker, int x, int top) {
		String distance = marker.distance() >= 10000
			? String.format(Locale.ROOT, "%.1fkm", marker.distance() / 1000.0F)
			: Math.round(marker.distance()) + "m";
		String text = marker.name().isEmpty() ? distance : marker.name() + " " + distance;
		int bottom = top - 3 - (marker.pitch() == TrackedWaypoint.PitchDirection.UP ? 6 : 0);
		int width = minecraft.font.width(text);

		graphics.pose().pushMatrix();
		graphics.pose().translate(x + DOT_SIZE / 2.0F, bottom);
		graphics.pose().scale(0.5F, 0.5F);
		graphics.fill(-width / 2 - 2, -minecraft.font.lineHeight - 2, width / 2 + 2, 0, 0x80000000);
		graphics.text(minecraft.font, text, -width / 2, -minecraft.font.lineHeight, 0xFFFFFFFF, false);
		graphics.pose().popMatrix();
	}
}
