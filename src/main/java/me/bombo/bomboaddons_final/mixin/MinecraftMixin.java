package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.LeftClickEtherwarp;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        LeftClickEtherwarp.onLeftClick();
    }
}
