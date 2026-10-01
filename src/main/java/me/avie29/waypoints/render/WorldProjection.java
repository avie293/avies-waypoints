package me.avie29.waypoints.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

final class WorldProjection {
	private final Matrix4f matrix;
	private final Vec3 cameraPos;

	private WorldProjection(Matrix4f matrix, Vec3 cameraPos) {
		this.matrix = matrix;
		this.cameraPos = cameraPos;
	}

	record Point(double x, double y, double depth) {
	}

	static WorldProjection current(Minecraft minecraft) {
		GameRenderState state = minecraft.gameRenderer.gameRenderState();
		CameraRenderState camera = state.levelRenderState.cameraRenderState;
		OptionsRenderState options = state.optionsRenderState;
		PoseStack bob = new PoseStack();
		bobHurt(camera.entityRenderState, options, bob);
		if (options.bobView) {
			bobView(camera.entityRenderState, bob);
		}
		Matrix4f matrix = new Matrix4f(camera.projectionMatrix).mul(bob.last().pose()).mul(camera.viewRotationMatrix);
		return new WorldProjection(matrix, camera.pos);
	}

	Vec3 cameraPos() {
		return this.cameraPos;
	}

	@Nullable Point project(Vec3 point, int width, int height) {
		Vector4f clip = this.matrix.transform(new Vector4f(
			(float) (point.x - this.cameraPos.x), (float) (point.y - this.cameraPos.y), (float) (point.z - this.cameraPos.z), 1.0F));
		if (clip.w <= 0.05F) {
			return null;
		}
		return new Point((clip.x / clip.w + 1.0) * 0.5 * width, (1.0 - clip.y / clip.w) * 0.5 * height, clip.w);
	}

	private static void bobHurt(CameraEntityRenderState entity, OptionsRenderState options, PoseStack poseStack) {
		if (!entity.isLiving) {
			return;
		}
		if (entity.isDeadOrDying) {
			float duration = Math.min(entity.deathTime, 20.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (duration + 200.0F)));
		}
		float hurt = entity.hurtTime;
		if (hurt < 0.0F) {
			return;
		}
		hurt /= entity.hurtDuration;
		hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
		poseStack.mulPose(Axis.YP.rotationDegrees(-entity.hurtDir));
		poseStack.mulPose(Axis.ZP.rotationDegrees((float) (-hurt * 14.0 * options.damageTiltStrength)));
		poseStack.mulPose(Axis.YP.rotationDegrees(entity.hurtDir));
	}

	private static void bobView(CameraEntityRenderState entity, PoseStack poseStack) {
		if (!entity.isPlayer) {
			return;
		}
		float walk = entity.backwardsInterpolatedWalkDistance;
		float bob = entity.bob;
		poseStack.translate(Mth.sin(walk * (float) Math.PI) * bob * 0.5F, -Math.abs(Mth.cos(walk * (float) Math.PI) * bob), 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(walk * (float) Math.PI) * bob * 3.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(walk * (float) Math.PI - 0.2F) * bob) * 5.0F));
	}
}
