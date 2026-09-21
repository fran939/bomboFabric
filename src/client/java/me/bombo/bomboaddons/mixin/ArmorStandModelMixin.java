package me.bombo.bomboaddons.mixin;

import net.minecraft.client.model.object.armorstand.ArmorStandModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandModel.class)
public abstract class ArmorStandModelMixin {
    @Shadow @Final private ModelPart rightBodyStick;
    @Shadow @Final private ModelPart leftBodyStick;
    @Shadow @Final private ModelPart shoulderStick;
    @Shadow @Final private ModelPart basePlate;
    @Shadow @Final private ModelPart body;
    @Shadow @Final private ModelPart rightArm;
    @Shadow @Final private ModelPart leftArm;
    @Shadow @Final private ModelPart rightLeg;
    @Shadow @Final private ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;)V", at = @At("TAIL"))
    private void onSetupAnimTail(ArmorStandRenderState state, CallbackInfo ci) {
        if (state != null && ArmorStandRendererMixin.isHeadOnly(state)) {
            // Hide everything except the head for dungeon key armor stands so only the skull silhouette is rendered
            this.body.visible = false;
            this.rightArm.visible = false;
            this.leftArm.visible = false;
            this.rightLeg.visible = false;
            this.leftLeg.visible = false;
            this.rightBodyStick.visible = false;
            this.leftBodyStick.visible = false;
            this.shoulderStick.visible = false;
            this.basePlate.visible = false;
        }
    }
}
