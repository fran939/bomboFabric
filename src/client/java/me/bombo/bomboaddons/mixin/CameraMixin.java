package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.util.FreelookManager;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float modifyYaw(float yRot) {
        if (FreelookManager.isFreelookActive()) {
            return FreelookManager.getFreelookYaw();
        }
        return yRot;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float modifyPitch(float xRot) {
        if (FreelookManager.isFreelookActive()) {
            return FreelookManager.getFreelookPitch();
        }
        return xRot;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true, require = 0)
    private void onGetFluidInCamera(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.level.material.FogType> cir) {
        if (me.bombo.bomboaddons.BomboConfig.get().clearWaterAndLava) {
            cir.setReturnValue(net.minecraft.world.level.material.FogType.NONE);
        }
    }
}
