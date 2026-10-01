package me.avie29.waypoints.gui;

import me.avie29.waypoints.waypoint.Waypoint;
import me.avie29.waypoints.waypoint.WaypointColor;
import me.avie29.waypoints.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class WaypointEditScreen extends Screen {
	private static final int VALID_COLOR = 0xFFE0E0E0;
	private static final int INVALID_COLOR = 0xFFFF5555;

	private final @Nullable Screen parent;
	private final @Nullable Waypoint editing;
	private final String dimension;

	private String name;
	private String initials;
	private String x;
	private String y;
	private String z;
	private int color;
	private boolean customInitials;

	private EditBox nameBox;
	private EditBox initialsBox;
	private EditBox xBox;
	private EditBox yBox;
	private EditBox zBox;
	private Button saveButton;

	private WaypointEditScreen(@Nullable Screen parent, @Nullable Waypoint editing, String dimension, Waypoint values) {
		super(Component.translatable(editing == null ? "avies-waypoints.edit.title.new" : "avies-waypoints.edit.title.edit"));
		this.parent = parent;
		this.editing = editing;
		this.dimension = dimension;
		this.name = values.name;
		this.initials = values.initials;
		this.x = String.valueOf(values.x);
		this.y = values.yIncluded ? String.valueOf(values.y) : "~";
		this.z = String.valueOf(values.z);
		this.color = values.color;
		this.customInitials = editing != null && !values.initials.equals(Waypoint.defaultInitials(values.name));
	}

	public static WaypointEditScreen create(@Nullable Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		BlockPos pos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
		String name = Component.translatable("avies-waypoints.edit.default_name").getString();
		Waypoint values = new Waypoint(name, Waypoint.defaultInitials(name), pos.getX(), pos.getY(), pos.getZ(), WaypointColor.random());
		String dimension = WaypointStore.currentDimension();
		return new WaypointEditScreen(parent, null, dimension == null ? "minecraft:overworld" : dimension, values);
	}

	public static WaypointEditScreen edit(@Nullable Screen parent, Waypoint waypoint, String dimension) {
		return new WaypointEditScreen(parent, waypoint, dimension, waypoint);
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 100;
		int top = this.height / 2 - 90;

		this.nameBox = this.addRenderableWidget(new EditBox(this.font, left, top + 12, 154, 20, Component.translatable("avies-waypoints.edit.name")));
		this.nameBox.setMaxLength(64);
		this.nameBox.setValue(this.name);
		this.nameBox.setResponder(value -> {
			this.name = value;
			if (!this.customInitials) {
				this.initials = Waypoint.defaultInitials(value);
				this.initialsBox.setValue(this.initials);
			}
			this.validate();
		});

		this.initialsBox = this.addRenderableWidget(new EditBox(this.font, left + 160, top + 12, 40, 20, Component.translatable("avies-waypoints.edit.initials")));
		this.initialsBox.setMaxLength(3);
		this.initialsBox.setValue(this.initials);
		this.initialsBox.setResponder(value -> {
			if (!value.equals(this.initials)) {
				this.customInitials = true;
			}
			this.initials = value;
			this.validate();
		});

		this.xBox = this.coordinateBox(left, top + 50, this.x, "x");
		this.xBox.setResponder(value -> {
			this.x = value;
			this.validate();
		});
		this.yBox = this.coordinateBox(left + 68, top + 50, this.y, "y");
		this.yBox.setResponder(value -> {
			this.y = value;
			this.validate();
		});
		this.zBox = this.coordinateBox(left + 136, top + 50, this.z, "z");
		this.zBox.setResponder(value -> {
			this.z = value;
			this.validate();
		});

		for (int i = 0; i < WaypointColor.COUNT; i++) {
			int index = i;
			int row = i < 11 ? 0 : 1;
			int column = i - row * 11;
			this.addRenderableWidget(new ColorSwatch(left + column * 14, top + 88 + row * 14, 12, index,
				() -> this.color == index, () -> this.color = index));
		}

		this.saveButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.save())
			.bounds(left, top + 128, 98, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
			.bounds(left + 102, top + 128, 98, 20).build());
		this.validate();
	}

	private EditBox coordinateBox(int x, int y, String value, String axis) {
		EditBox box = this.addRenderableWidget(new EditBox(this.font, x, y, 64, 20, Component.literal(axis.toUpperCase())));
		box.setMaxLength(12);
		box.setValue(value);
		return box;
	}

	@Override
	protected void setInitialFocus() {
		this.setInitialFocus(this.nameBox);
	}

	private void validate() {
		boolean xValid = parse(this.x) != null;
		boolean yValid = this.y.trim().equals("~") || parse(this.y) != null;
		boolean zValid = parse(this.z) != null;
		this.xBox.setTextColor(xValid ? VALID_COLOR : INVALID_COLOR);
		this.yBox.setTextColor(yValid ? VALID_COLOR : INVALID_COLOR);
		this.zBox.setTextColor(zValid ? VALID_COLOR : INVALID_COLOR);
		this.saveButton.active = xValid && yValid && zValid && !this.name.isBlank();
	}

	private static @Nullable Integer parse(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void save() {
		if (!this.saveButton.active) {
			return;
		}
		boolean yIncluded = !this.y.trim().equals("~");
		String initials = this.initials.isBlank() ? Waypoint.defaultInitials(this.name) : this.initials.trim();
		Waypoint waypoint = this.editing != null ? this.editing : new Waypoint(this.name, initials, 0, 0, 0, this.color);
		if (!waypoint.name.equals(this.name.trim())) {
			waypoint.autoName = false;
		}
		waypoint.name = this.name.trim();
		waypoint.initials = initials;
		waypoint.x = parse(this.x);
		waypoint.yIncluded = yIncluded;
		if (yIncluded) {
			waypoint.y = parse(this.y);
		}
		waypoint.z = parse(this.z);
		waypoint.color = this.color;

		if (this.editing == null) {
			WaypointStore.add(this.dimension, waypoint);
		} else {
			WaypointStore.save();
		}
		this.onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && this.saveButton.active) {
			this.save();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int left = this.width / 2 - 100;
		int top = this.height / 2 - 90;
		int labelColor = 0xFFA0A0A0;
		graphics.centeredText(this.font, this.title, this.width / 2, top - 20, 0xFFFFFFFF);
		graphics.text(this.font, Component.translatable("avies-waypoints.edit.name"), left, top, labelColor);
		graphics.text(this.font, Component.translatable("avies-waypoints.edit.initials"), left + 160, top, labelColor);
		graphics.text(this.font, "X", left, top + 38, labelColor);
		graphics.text(this.font, "Y", left + 68, top + 38, labelColor);
		graphics.text(this.font, "Z", left + 136, top + 38, labelColor);
		graphics.text(this.font, Component.translatable("avies-waypoints.edit.color"), left, top + 76, labelColor);
	}
}
