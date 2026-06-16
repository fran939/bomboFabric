package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.BomboConfig;
import me.bombo.bomboaddons_final.IRCClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, CallbackInfo ci) {
        if (BomboConfig.get().ircChatEnabled && BomboConfig.get().ircDefaultChat) {
            if (Minecraft.getInstance().screen instanceof ChatScreen) {
                IRCClient.sendMessage(message);
                ci.cancel();
            }
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onHandleMovePlayerHead(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        try {
            me.bombo.bomboaddons_final.GardenMacroDetector.onMovePlayerPacketHead();
        } catch (Throwable t) {}
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void onHandleMovePlayerReturn(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        try {
            me.bombo.bomboaddons_final.GardenMacroDetector.onMovePlayerPacketTail();
        } catch (Throwable t) {}
    }
}
