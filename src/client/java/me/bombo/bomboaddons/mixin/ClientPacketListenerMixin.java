package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.IRCClient;
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

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void onSendCommand(String command, CallbackInfo ci) {
        if (command == null) return;
        String clean = command;
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        String lower = clean.toLowerCase().trim();
        if (lower.startsWith("chat ")) {
            String channelArg = lower.substring(5).trim();
            if (!channelArg.equals("b")) {
                String normalizedTarget = me.bombo.bomboaddons.BomboaddonsClient.normalizeChannel(channelArg);
                String normalizedCurrent = me.bombo.bomboaddons.BomboaddonsClient.normalizeChannel(me.bombo.bomboaddons.BomboaddonsClient.currentHypixelChannel);
                
                boolean wasIrc = BomboConfig.get().ircDefaultChat;
                if (wasIrc) {
                    BomboConfig.get().ircDefaultChat = false;
                    BomboConfig.save();
                    Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7."));
                }
                
                if (normalizedTarget.equals(normalizedCurrent)) {
                    ci.cancel();
                    return;
                }
            }
        } else if (lower.equals("chat")) {
            if (BomboConfig.get().ircDefaultChat) {
                BomboConfig.get().ircDefaultChat = false;
                BomboConfig.save();
                Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7."));
            }
        } else if (lower.startsWith("tell ") || lower.startsWith("w ") || lower.startsWith("msg ") || 
                   lower.startsWith("r ") || lower.startsWith("reply ") || lower.startsWith("whisper ") || 
                   lower.startsWith("message ")) {
            if (BomboConfig.get().ircDefaultChat) {
                BomboConfig.get().ircDefaultChat = false;
                BomboConfig.save();
                Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7."));
            }
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onHandleMovePlayerHead(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        try {
            me.bombo.bomboaddons.GardenMacroDetector.onMovePlayerPacketHead();
        } catch (Throwable t) {}
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void onHandleMovePlayerReturn(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        try {
            me.bombo.bomboaddons.GardenMacroDetector.onMovePlayerPacketTail();
        } catch (Throwable t) {}
    }
}
