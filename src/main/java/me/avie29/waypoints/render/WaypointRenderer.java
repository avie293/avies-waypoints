package me.avie29.waypoints.render;

import com.mojang.blaze3d.platform.Window;
import me.avie29.waypoints.config.WaypointsConfig;
import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointColor;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WaypointRenderer implements HudElement {
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int LABEL_BACKGROUND = 0x5A000000;
	private static final double POINTING_ANGLE = Math.toRadians(10);
	private static final double NEARBY_DISTANCE = 20;
	private static final int KM_THRESHOLD = 10000;
	private static final double MAX_CLOSE_SCALE = 8.0;

	private record Visible(Waypoint waypoint, double screenX, double screenY, double distance, double depth) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (!WaypointsConfig.ENABLED.get() || player == null || minecraft.level == null) {
			return;
		}
		List<Waypoint> waypoints = WaypointStore.current();
		if (waypoints.isEmpty()) {
			return;
		}

		Window window = minecraft.getWindow();
		int width = window.getWidth();
		int height = window.getHeight();
		WorldProjection projection = WorldProjection.current(minecraft);
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		Vec3 playerPos = player.getPosition(partialTick);
		int maxDistance = WaypointsConfig.MAX_DISTANCE.get();

		List<Visible> visible = new ArrayList<>();
		for (Waypoint waypoint : waypoints) {
			if (!waypoint.visible) {
				continue;
			}
			WorldProjection.Point point = projection.project(waypoint.renderPos(playerPos.y + 1.0), width, height);
			if (point == null) {
				continue;
			}
			double distance = distance(waypoint, playerPos);
			if (maxDistance > 0 && waypoint.type != Waypoint.Type.DEATH && horizontalDistance(waypoint, playerPos) > maxDistance) {
				continue;
			}
			visible.add(new Visible(waypoint, point.x(), point.y(), distance, point.depth()));
		}
		if (visible.isEmpty()) {
			return;
		}
		visible.sort(Comparator.comparingDouble(Visible::distance).reversed());

		double fov = Math.toRadians(minecraft.options.fov().get());
		double pointingHalfWidth = width / 2.0 * Math.tan(POINTING_ANGLE) / (Math.tan(fov / 2.0) * width / height);
		boolean all = player.isShiftKeyDown();
		Visible main = null;
		for (Visible entry : visible) {
			double offsetX = Math.abs(entry.screenX() - width / 2.0);
			if (offsetX <= pointingHalfWidth && (main == null || offsetX < Math.abs(main.screenX() - width / 2.0))) {
				main = entry;
			}
		}

		float scale = WaypointsConfig.SCALE.get().floatValue();
		int autoScale = Math.min(width, height) >= 1500 ? Math.min(width, height) / 500 : 2;
		float iconScale = autoScale * scale;
		int nameScale = Math.max(1, (int) Math.ceil(autoScale * 0.5 * scale));
		int distanceScale = Math.max(1, (int) Math.ceil(autoScale * scale));
		float alpha = 0.52274513F * WaypointsConfig.OPACITY.get() / 100.0F;
		double closeDepth = 0.021333335 * height / autoScale / (2.0 * Math.tan(fov / 2.0));

		float guiScale = window.getGuiScale();
		graphics.pose().pushMatrix();
		graphics.pose().scale(1.0F / guiScale, 1.0F / guiScale);
		for (Visible entry : visible) {
			boolean highlighted = all
				? Math.abs(entry.screenX() - width / 2.0) <= pointingHalfWidth
				: entry == main;
			float closeScale = entry.depth() < closeDepth ? (float) Math.min(closeDepth / entry.depth(), MAX_CLOSE_SCALE) : 1.0F;
			this.drawWaypoint(graphics, minecraft.font, entry, highlighted, !all && highlighted, closeScale, iconScale, nameScale, distanceScale, alpha);
		}
		graphics.pose().popMatrix();
	}

	private void drawWaypoint(GuiGraphicsExtractor graphics, Font font, Visible entry, boolean highlighted, boolean brighter,
							  float closeScale, float iconScale, int nameScale, int distanceScale, float alpha) {
		Waypoint waypoint = entry.waypoint();
		boolean nearby = entry.distance() <= NEARBY_DISTANCE;
		boolean showDistance = !nearby && switch (WaypointsConfig.SHOW_DISTANCE.get()) {
			case ALWAYS -> true;
			case LOOKING -> highlighted;
			case NEVER -> false;
		};
		boolean showName = nearby
			|| showDistance && WaypointsConfig.NAME_WITH_DISTANCE.get()
			|| !showDistance && waypoint.type == Waypoint.Type.DEATH;

		graphics.pose().pushMatrix();
		graphics.pose().translate((float) Math.floor(entry.screenX()), (float) Math.floor(entry.screenY()));
		graphics.pose().scale(closeScale, closeScale);

		int halfIconPixel = (int) iconScale / 2;
		graphics.pose().pushMatrix();
		graphics.pose().translate(halfIconPixel, 0);
		graphics.pose().scale(iconScale, iconScale);
		int initialsWidth = font.width(waypoint.initials);
		int addedFrame = initialsWidth > 8 ? (initialsWidth - 8) - (initialsWidth - 8) / 2 : 0;
		float iconAlpha = brighter ? Math.min(1.0F, alpha * 1.5F) : alpha;
		int color = (int) (iconAlpha * 255) << 24 | WaypointColor.argb(waypoint.color) & 0xFFFFFF;
		graphics.fill(-5 - addedFrame, -9, 4 + addedFrame, 0, color);
		graphics.text(font, waypoint.initials, -initialsWidth / 2, -8, TEXT_COLOR, false);
		graphics.pose().popMatrix();

		int y = 2;
		if (showName) {
			label(graphics, font, waypoint.name, y, nameScale);
			y += 9 * nameScale;
		}
		y += 2;
		if (showDistance) {
			label(graphics, font, formatDistance(entry.distance()), y, distanceScale);
		}
		graphics.pose().popMatrix();
	}

	private static void label(GuiGraphicsExtractor graphics, Font font, String text, int y, int scale) {
		int backgroundWidth = font.width(text) + 3;
		int halfBackgroundWidth = backgroundWidth / 2;
		graphics.pose().pushMatrix();
		if ((backgroundWidth & 1) != 0) {
			graphics.pose().translate(-(scale - scale / 2), 0);
		}
		graphics.pose().translate(0, y);
		graphics.pose().scale(scale, scale);
		graphics.fill(-halfBackgroundWidth, 0, -halfBackgroundWidth + backgroundWidth, 9, LABEL_BACKGROUND);
		graphics.text(font, text, -halfBackgroundWidth + 2, 1, TEXT_COLOR, false);
		graphics.pose().popMatrix();
	}

	public static double distance(Waypoint waypoint, Vec3 from) {
		double x = waypoint.x + 0.5 - from.x;
		double y = waypoint.yIncluded ? waypoint.y - from.y : 0;
		double z = waypoint.z + 0.5 - from.z;
		return Math.sqrt(x * x + y * y + z * z);
	}

	private static double horizontalDistance(Waypoint waypoint, Vec3 from) {
		double x = waypoint.x + 0.5 - from.x;
		double z = waypoint.z + 0.5 - from.z;
		return Math.sqrt(x * x + z * z);
	}

	public static String formatDistance(double distance) {
		return distance >= KM_THRESHOLD
			? String.format(Locale.ROOT, "%.1fkm", distance / 1000.0)
			: String.format(Locale.ROOT, "%.1fm", distance);
	}
}
