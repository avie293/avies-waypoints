package me.avie29.waypoints.mixin;

import me.avie29.waypoints.render.LocatorBarRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocatorBar.class)
public abstract class LocatorBarMixin implements ContextualBar {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void avieswaypoints$render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo info) {
		if (LocatorBarRenderer.active()) {
			LocatorBarRenderer.render(graphics, deltaTracker, this.top(Minecraft.getInstance().getWindow()));
			info.cancel();
		}
	}
}
