package me.bombo.bomboaddons.mixin;

import com.mojang.authlib.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Property.class, remap = false)
public class PropertyMixin {
    @Inject(method = "hasSignature", at = @At("HEAD"), cancellable = true)
    private void onHasSignature(CallbackInfoReturnable<Boolean> cir) {
        Property prop = (Property) (Object) this;
        if (prop.signature() == null || prop.signature().isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
