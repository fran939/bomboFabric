package me.bombo.bomboaddons_final.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import me.bombo.bomboaddons_final.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;

@Mixin(Minecraft.class)
public class HollowWandMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.player != null) {
            boolean hollowClick = BomboConfig.get().hollowWandClickThrough;
            boolean hollowCombine = BomboConfig.get().hollowWandAutoCombine;

            if (!hollowClick && !hollowCombine)
                return;

            ItemStack stack = client.player.getMainHandItem();
            String name = stack.getHoverName().getString().replaceAll("§.", "").toLowerCase();

            if (name.contains("hollow wand")) {
                if (client.getConnection() != null) {
                    client.getConnection().send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                            client.player.blockPosition(), Direction.DOWN));
                }

                client.player.swing(InteractionHand.MAIN_HAND);

                if (hollowCombine) {
                    new Thread(() -> {
                        try {
                            long randomDelay = 40 + (long) (Math.random() * 31);
                            Thread.sleep(randomDelay);
                            client.execute(() -> {
                                if (client.player != null && client.gameMode != null) {
                                    client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                                    client.player.swing(InteractionHand.MAIN_HAND);
                                }
                            });
                        } catch (InterruptedException e) {
                        }
                    }).start();
                }

                cir.setReturnValue(false);
            }
        }
    }
}
