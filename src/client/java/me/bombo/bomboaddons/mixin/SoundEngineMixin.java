package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "play", at = @At("TAIL"))
    private void onPlay(SoundInstance sound, CallbackInfoReturnable<?> ci) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.debugSounds && sound != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                try {
                    String id = sound.getIdentifier().toString();
                    float vol = sound.getVolume();
                    float pitch = sound.getPitch();
                    mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §e[Sound] §a" + id + " §7(v:" + vol + " p:" + pitch + ")"));
                } catch (Exception e) {
                    try {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §e[Sound] §a" + sound.getIdentifier().toString() + " §7(error getting details)"));
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
