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

    // Redirect submitArmWithItem calls to render the spectated player's skin, arm model, and held item
    @Redirect(
        method = "submitHandsWithItems",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
        )
    )
    private void bombo$renderSpectatedArmWithItem(
        ItemInHandRenderer instance,
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
    ) {
        if (SpectatorCamManager.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getCameraEntity() instanceof AbstractClientPlayer other) {
                // Use the spectated player's held item, arm swing progress, and pitch
                ItemStack otherStack = (hand == InteractionHand.MAIN_HAND) ? other.getMainHandItem() : other.getOffhandItem();
                float otherSwing = other.getAttackAnim(partialTicks);
                float otherPitch = other.getXRot(partialTicks);
                this.submitArmWithItem(other, partialTicks, otherPitch, hand, otherSwing, otherStack, equippedProgress, poseStack, buffer, light);
                return;
            }
        }
        this.submitArmWithItem(player, partialTicks, pitch, hand, swingProgress, stack, equippedProgress, poseStack, buffer, light);
    }
}
