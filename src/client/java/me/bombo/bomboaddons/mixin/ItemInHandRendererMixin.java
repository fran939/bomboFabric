package me.bombo.bomboaddons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.bombo.bomboaddons.util.SpectatorCamManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Synchronizes first-person arm and item-in-hand rendering to the spectated player
 * when spectating via /b cam or spectator camera.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;

    @Shadow
    protected abstract void submitArmWithItem(
        AbstractClientPlayer player,
        float partialTicks,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equippedProgress,
        PoseStack poseStack,
        SubmitNodeCollector buffer,
        int light
    );

    // Keep item held states synchronized with the spectated player on tick
    @Inject(method = "tick", at = @At("TAIL"))
    private void bombo$syncSpectatedHandItems(CallbackInfo ci) {
        if (SpectatorCamManager.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getCameraEntity() instanceof AbstractClientPlayer other) {
                this.mainHandItem = other.getMainHandItem();
                this.offHandItem = other.getOffhandItem();
            }
        }
    }

    // Isolate hands and held items rendering to the spectated player
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void bombo$submitSpectatedHandsWithItems(
        float partialTicks,
        PoseStack poseStack,
        SubmitNodeCollector buffer,
        net.minecraft.client.player.LocalPlayer player,
        int light,
        CallbackInfo ci
    ) {
        if (SpectatorCamManager.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getCameraEntity() instanceof AbstractClientPlayer other) {
                ItemStack mainStack = other.getMainHandItem();
                ItemStack offStack = other.getOffhandItem();
                float otherSwing = other.getAttackAnim(partialTicks);
                float otherPitch = other.getXRot(partialTicks);
                InteractionHand hand = other.swingingArm != null ? other.swingingArm : InteractionHand.MAIN_HAND;

                this.submitArmWithItem(other, partialTicks, otherPitch, InteractionHand.MAIN_HAND, hand == InteractionHand.MAIN_HAND ? otherSwing : 0.0f, mainStack, 0.0f, poseStack, buffer, light);
                if (!offStack.isEmpty()) {
                    this.submitArmWithItem(other, partialTicks, otherPitch, InteractionHand.OFF_HAND, hand == InteractionHand.OFF_HAND ? otherSwing : 0.0f, offStack, 0.0f, poseStack, buffer, light);
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void bombo$renderSpectatedPlayerArm(
        PoseStack poseStack,
        SubmitNodeCollector buffer,
        int light,
        float equippedProgress,
        float swingProgress,
        net.minecraft.world.entity.HumanoidArm arm,
        CallbackInfo ci
    ) {
        if (SpectatorCamManager.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getCameraEntity() instanceof AbstractClientPlayer other) {
                boolean right = (arm != net.minecraft.world.entity.HumanoidArm.LEFT);
                float sign = right ? 1.0F : -1.0F;
                float sqrtSwing = net.minecraft.util.Mth.sqrt(swingProgress);
                float sin1 = net.minecraft.util.Mth.sin((float)(sqrtSwing * Math.PI));
                float sin2 = net.minecraft.util.Mth.sin((float)(sqrtSwing * Math.PI * 2.0));
                float sin3 = net.minecraft.util.Mth.sin((float)(swingProgress * Math.PI));

                poseStack.pushPose();
                poseStack.translate(sign * (sin1 * 0.64F), sin2 * -0.6F + equippedProgress * -0.6F, sin3 * -0.72F);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign * 45.0F));
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(sign * sin1 * 70.0F));
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sign * -20.0F));
                poseStack.translate(sign * -1.0F, 3.6F, 3.5F);
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sign * 120.0F));
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(200.0F));
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign * -135.0F));
                poseStack.translate(sign * 5.6F, 0.0F, 0.0F);

                net.minecraft.client.renderer.entity.player.AvatarRenderer avatarRenderer = mc.getEntityRenderDispatcher().getPlayerRenderer(other);
                net.minecraft.resources.Identifier skin = other.getSkin().body().texturePath();
                boolean sleeve = other.isModelPartShown(right ? net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE : net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE);
                if (right) {
                    avatarRenderer.renderRightHand(poseStack, buffer, light, skin, sleeve);
                } else {
                    avatarRenderer.renderLeftHand(poseStack, buffer, light, skin, sleeve);
                }
                poseStack.popPose();
            }
            ci.cancel();
        }
    }
}
