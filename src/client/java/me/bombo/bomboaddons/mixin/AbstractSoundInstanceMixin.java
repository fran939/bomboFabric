package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSoundInstance.class)
public class AbstractSoundInstanceMixin {

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void onGetVolume(CallbackInfoReturnable<Float> cir) {
        AbstractSoundInstance instance = (AbstractSoundInstance) (Object) this;
        String id = instance.getIdentifier().toString();
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.customSoundVolumes != null && s.customSoundVolumes.containsKey(id)) {
            float customVol = s.customSoundVolumes.get(id);
            cir.setReturnValue(cir.getReturnValue() * customVol);
        }
    }
}
