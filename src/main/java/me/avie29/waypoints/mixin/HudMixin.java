package me.avie29.waypoints.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.avie29.waypoints.render.LocatorBarRenderer;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class HudMixin {
	@ModifyExpressionValue(
		method = "nextContextualInfoState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/waypoints/ClientWaypointManager;hasWaypoints()Z"))
	private boolean avieswaypoints$showLocatorBar(boolean hasWaypoints) {
		return hasWaypoints || LocatorBarRenderer.showsWaypoints();
	}
}
