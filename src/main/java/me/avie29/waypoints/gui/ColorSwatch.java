package me.avie29.waypoints.gui;

import me.avie29.waypoints.waypoint.WaypointColor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** A small square in one of the 21 waypoint colors, outlined white while selected. */
public class ColorSwatch extends AbstractButton {
	private final int colorIndex;
	private final BooleanSupplier selected;
	private final Runnable onSelect;

	public ColorSwatch(int x, int y, int size, int colorIndex, BooleanSupplier selected, Runnable onSelect) {
		super(x, y, size, size, Component.translatable(WaypointColor.translationKey(colorIndex)));
		this.colorIndex = colorIndex;
		this.selected = selected;
		this.onSelect = onSelect;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.onSelect.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = this.getX();
		int y = this.getY();
		int border = this.selected.getAsBoolean() ? 0xFFFFFFFF : this.isHoveredOrFocused() ? 0xFFA0A0A0 : 0xFF303030;
		graphics.fill(x - 1, y - 1, x + this.width + 1, y + this.height + 1, border);
		graphics.fill(x, y, x + this.width, y + this.height, WaypointColor.argb(this.colorIndex));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
