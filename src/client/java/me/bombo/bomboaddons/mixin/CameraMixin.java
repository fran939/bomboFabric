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
}
