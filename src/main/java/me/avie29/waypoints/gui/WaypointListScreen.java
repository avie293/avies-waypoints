package me.avie29.waypoints.gui;

import me.avie29.tabbylib.api.TabbyLibApi;
import me.avie29.waypoints.AviesWaypoints;
import me.avie29.waypoints.render.WaypointRenderer;
import me.avie29.waypoints.share.ChatShareHandler;
import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointColor;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * List of the waypoints of one dimension with buttons to add, edit, delete, hide, share and teleport.
 * Waypoints can be ticked with the checkbox in front of them (shift click ticks a range, Ctrl+A all of them);
 * delete and hide/show then work on all ticked waypoints, otherwise on the selected one.
 */
public class WaypointListScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int CHECKBOX_SIZE = 12;
	private static final Identifier CHECKBOX = Identifier.withDefaultNamespace("widget/checkbox");
	private static final Identifier CHECKBOX_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_highlighted");
	private static final Identifier CHECKBOX_SELECTED = Identifier.withDefaultNamespace("widget/checkbox_selected");
	private static final Identifier CHECKBOX_SELECTED_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");

	private final @Nullable Screen parent;
	private String dimension;
	private WaypointList list;
	private Button selectAllButton;
	private Button editButton;
	private Button deleteButton;
	private Button visibilityButton;
	private Button shareButton;
	private Button teleportButton;
	private Button dimensionButton;
	/** Ticked waypoints. By identity, two waypoints can have the same values. */
	private final Set<Waypoint> checked = Collections.newSetFromMap(new IdentityHashMap<>());
	/** The waypoint whose checkbox was clicked last, start of a shift click range. */
	private @Nullable Waypoint lastChecked;

	public WaypointListScreen(@Nullable Screen parent) {
		super(Component.translatable("avies-waypoints.list.title"));
		this.parent = parent;
		String current = WaypointStore.currentDimension();
		this.dimension = current == null ? "minecraft:overworld" : current;
	}

	@Override
	protected void init() {
		this.list = this.addRenderableWidget(new WaypointList(this.minecraft, this.width, this.height - 32 - 60, 32));
		this.list.refresh();

		int center = this.width / 2;
		this.selectAllButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.toggleAll())
			.bounds(6, 6, 90, 20).build());
		this.dimensionButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.nextDimension())
			.bounds(center - 100, 6, 200, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("avies-waypoints.list.config"), button -> TabbyLibApi.openScreen(AviesWaypoints.MOD_ID))
			.bounds(this.width - 66, 6, 60, 20).build());

		int bottom = this.height - 54;
		this.addRenderableWidget(Button.builder(Component.translatable("avies-waypoints.list.new"), button ->
				this.minecraft.gui.setScreen(WaypointEditScreen.create(this)))
			.bounds(center - 154, bottom, 100, 20).build());
		this.editButton = this.addRenderableWidget(Button.builder(Component.translatable("avies-waypoints.list.edit"), button -> this.editSelected())
			.bounds(center - 50, bottom, 100, 20).build());
		this.deleteButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.deleteSelected())
			.bounds(center + 54, bottom, 100, 20).build());

		this.visibilityButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.toggleVisibility())
			.bounds(center - 154, bottom + 24, 75, 20).build());
		this.shareButton = this.addRenderableWidget(Button.builder(Component.translatable("avies-waypoints.list.share"), button -> this.shareSelected())
			.bounds(center - 75, bottom + 24, 75, 20).build());
		this.teleportButton = this.addRenderableWidget(Button.builder(Component.translatable("avies-waypoints.list.teleport"), button -> this.teleportSelected())
			.bounds(center + 4, bottom + 24, 75, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
			.bounds(center + 83, bottom + 24, 71, 20).build());

		this.updateButtons();
	}

	/** The ticked waypoints in list order, or the selected one when none is ticked. */
	private List<Waypoint> targets() {
		if (this.checked.isEmpty()) {
			WaypointList.Entry entry = this.list.getSelected();
			return entry == null ? List.of() : List.of(entry.waypoint);
		}
		return WaypointStore.get(this.dimension).stream().filter(this.checked::contains).toList();
	}

	/** The waypoint edit, share and teleport work on, null when none or several are targeted. */
	private @Nullable Waypoint single() {
		List<Waypoint> targets = this.targets();
		return targets.size() == 1 ? targets.getFirst() : null;
	}

	void updateButtons() {
		List<Waypoint> targets = this.targets();
		boolean single = targets.size() == 1;
		boolean inDimension = this.dimension.equals(WaypointStore.currentDimension());
		this.editButton.active = single;
		this.shareButton.active = single;
		this.teleportButton.active = single && inDimension;
		this.deleteButton.active = !targets.isEmpty();
		this.deleteButton.setMessage(withCount("avies-waypoints.list.delete", targets.size()));
		this.visibilityButton.active = !targets.isEmpty();
		boolean anyVisible = targets.isEmpty() || targets.stream().anyMatch(waypoint -> waypoint.visible);
		this.visibilityButton.setMessage(withCount(anyVisible ? "avies-waypoints.list.hide" : "avies-waypoints.list.show", targets.size()));

		boolean empty = this.list.children().isEmpty();
		boolean allChecked = !empty && this.checked.size() == this.list.children().size();
		this.selectAllButton.active = !empty;
		this.selectAllButton.setMessage(Component.translatable(allChecked ? "avies-waypoints.list.select_none" : "avies-waypoints.list.select_all"));
		this.dimensionButton.setMessage(Component.translatable("avies-waypoints.list.dimension", dimensionName(this.dimension)));
		this.dimensionButton.active = WaypointStore.dimensions().size() > 1;
	}

	/** "Delete" for one waypoint, "Delete (3)" for several. */
	private static Component withCount(String key, int count) {
		Component text = Component.translatable(key);
		return count > 1 ? Component.translatable("avies-waypoints.list.count", text, count) : text;
	}

	private static Component dimensionName(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> Component.translatable("avies-waypoints.dimension.overworld");
			case "minecraft:the_nether" -> Component.translatable("avies-waypoints.dimension.the_nether");
			case "minecraft:the_end" -> Component.translatable("avies-waypoints.dimension.the_end");
			default -> Component.literal(dimension);
		};
	}

	private void nextDimension() {
		List<String> dimensions = WaypointStore.dimensions();
		int index = dimensions.indexOf(this.dimension);
		this.dimension = dimensions.get((index + 1) % dimensions.size());
		this.checked.clear();
		this.lastChecked = null;
		this.list.refresh();
		this.updateButtons();
	}

	private void checkAll() {
		this.list.children().forEach(entry -> this.checked.add(entry.waypoint));
		this.updateButtons();
	}

	private void toggleAll() {
		if (this.checked.size() == this.list.children().size()) {
			this.checked.clear();
			this.updateButtons();
		} else {
			this.checkAll();
		}
	}

	/** Ticks or unticks a waypoint, with shift also everything between it and the last clicked checkbox. */
	private void toggleChecked(Waypoint waypoint, boolean range) {
		boolean check = !this.checked.contains(waypoint);
		List<Waypoint> all = WaypointStore.get(this.dimension);
		int from = this.lastChecked == null ? -1 : all.indexOf(this.lastChecked);
		int to = all.indexOf(waypoint);
		if (range && from >= 0 && to >= 0) {
			for (Waypoint other : all.subList(Math.min(from, to), Math.max(from, to) + 1)) {
				if (check) {
					this.checked.add(other);
				} else {
					this.checked.remove(other);
				}
			}
		} else if (check) {
			this.checked.add(waypoint);
		} else {
			this.checked.remove(waypoint);
		}
		this.lastChecked = waypoint;
		this.updateButtons();
	}

	private void editSelected() {
		Waypoint selected = this.single();
		if (selected != null) {
			this.minecraft.gui.setScreen(WaypointEditScreen.edit(this, selected, this.dimension));
		}
	}

	private void deleteSelected() {
		List<Waypoint> targets = this.targets();
		if (targets.isEmpty()) {
			return;
		}
		Component title = targets.size() == 1
			? Component.translatable("avies-waypoints.list.delete.title")
			: Component.translatable("avies-waypoints.list.delete.title.multiple", targets.size());
		Component message = targets.size() == 1
			? Component.translatable("avies-waypoints.list.delete.message", targets.getFirst().name)
			: Component.translatable("avies-waypoints.list.delete.message.multiple");
		this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				WaypointStore.removeAll(this.dimension, targets);
				targets.forEach(this.checked::remove);
			}
			this.minecraft.gui.setScreen(this);
		}, title, message));
	}

	/** Hides all targets when one of them is visible, otherwise shows them all. */
	private void toggleVisibility() {
		List<Waypoint> targets = this.targets();
		boolean visible = targets.stream().noneMatch(waypoint -> waypoint.visible);
		targets.forEach(waypoint -> waypoint.visible = visible);
		WaypointStore.save();
		this.updateButtons();
	}

	private void shareSelected() {
		Waypoint selected = this.single();
		if (selected != null) {
			ChatShareHandler.share(selected, this.dimension);
			this.onClose();
		}
	}

	private void teleportSelected() {
		Waypoint selected = this.single();
		if (selected != null && this.minecraft.player != null) {
			String y = selected.yIncluded ? String.valueOf(selected.y) : "~";
			this.minecraft.player.connection.sendCommand("tp @s " + selected.x + " " + y + " " + selected.z);
			this.onClose();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isSelectAll() && !this.list.children().isEmpty()) {
			this.checkAll();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void added() {
		// Coming back from the edit or confirm screen: init() is not called again, so the list is refreshed here
		if (this.list != null) {
			List<Waypoint> existing = WaypointStore.get(this.dimension);
			this.checked.removeIf(waypoint -> existing.stream().noneMatch(other -> other == waypoint));
			this.list.refresh();
			this.updateButtons();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.list.children().isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("avies-waypoints.list.empty"), this.width / 2, this.height / 2 - 20, 0xFFA0A0A0);
		}
	}

	private class WaypointList extends ObjectSelectionList<WaypointList.Entry> {
		WaypointList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		void refresh() {
			Waypoint previous = WaypointListScreen.this.list != null && this.getSelected() != null ? this.getSelected().waypoint : null;
			this.clearEntries();
			for (Waypoint waypoint : WaypointStore.get(WaypointListScreen.this.dimension)) {
				Entry entry = new Entry(waypoint);
				this.addEntry(entry);
				if (waypoint == previous) {
					this.setSelected(entry);
				}
			}
			this.setScrollAmount(0);
		}

		@Override
		public void setSelected(@Nullable Entry selected) {
			super.setSelected(selected);
			if (WaypointListScreen.this.editButton != null) {
				WaypointListScreen.this.updateButtons();
			}
		}

		@Override
		public int getRowWidth() {
			return 320;
		}

		class Entry extends ObjectSelectionList.Entry<Entry> {
			final Waypoint waypoint;

			Entry(Waypoint waypoint) {
				this.waypoint = waypoint;
			}

			@Override
			public Component getNarration() {
				return Component.literal(this.waypoint.name);
			}

			private boolean isOverCheckbox(double mouseX, double mouseY) {
				int x = this.getContentX();
				int y = this.getContentY() + 4;
				return mouseX >= x - 2 && mouseX < x + CHECKBOX_SIZE + 2 && mouseY >= y - 2 && mouseY < y + CHECKBOX_SIZE + 2;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				var font = WaypointListScreen.this.font;
				int x = this.getContentX();
				int y = this.getContentY();
				int alpha = this.waypoint.visible ? 0xFF000000 : 0x80000000;
				int textColor = this.waypoint.visible ? 0xFFFFFFFF : 0xFF808080;

				boolean checked = WaypointListScreen.this.checked.contains(this.waypoint);
				boolean checkboxHovered = this.isOverCheckbox(mouseX, mouseY);
				Identifier sprite = checked
					? checkboxHovered ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED
					: checkboxHovered ? CHECKBOX_HIGHLIGHTED : CHECKBOX;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y + 4, CHECKBOX_SIZE, CHECKBOX_SIZE);
				x += CHECKBOX_SIZE + 6;

				// Same icon as in the world
				int iconSize = 18;
				graphics.fill(x, y + 1, x + iconSize, y + 1 + iconSize, alpha | (WaypointColor.argb(this.waypoint.color) & 0xFFFFFF));
				graphics.centeredText(font, this.waypoint.initials, x + iconSize / 2, y + 6, textColor);

				graphics.text(font, this.waypoint.name, x + iconSize + 6, y + 1, textColor);
				graphics.text(font, ChatShareHandler.coordinates(this.waypoint), x + iconSize + 6, y + 11, 0xFF909090);

				Minecraft minecraft = WaypointListScreen.this.minecraft;
				if (minecraft.player != null && WaypointListScreen.this.dimension.equals(WaypointStore.currentDimension())) {
					String distance = WaypointRenderer.formatDistance(WaypointRenderer.distance(this.waypoint, minecraft.player.position()));
					graphics.text(font, distance, this.getContentRight() - font.width(distance) - 2, y + 6, 0xFF909090);
				}
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				if (this.isOverCheckbox(event.x(), event.y())) {
					WaypointListScreen.this.toggleChecked(this.waypoint, event.hasShiftDown() || WaypointListScreen.this.minecraft.hasShiftDown());
					return true;
				}
				WaypointList.this.setSelected(this);
				if (doubleClick) {
					WaypointListScreen.this.editSelected();
				}
				return true;
			}
		}
	}
}
