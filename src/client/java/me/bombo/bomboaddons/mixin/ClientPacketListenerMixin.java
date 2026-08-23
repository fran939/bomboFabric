package me.bombo.bomboaddons.mixin;

import com.mojang.brigadier.CommandDispatcher;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.GardenMacroDetector;
import me.bombo.bomboaddons.IRCClient;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public class ClientPacketListenerMixin {
   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendChat(String message, CallbackInfo ci) {
      String replaced = SkyblockUtils.replaceCoordPlaceholders(message);
      if (!replaced.equals(message)) {
         ci.cancel();
         ClientPacketListener listener = (ClientPacketListener)(Object)this;
         listener.sendChat(replaced);
      }
   }

   @Inject(
      method = {"sendCommand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendCommand(String command, CallbackInfo ci) {
      if (command != null) {
         String replaced = SkyblockUtils.replaceCoordPlaceholders(command);
         if (!replaced.equals(command)) {
            ci.cancel();
            ClientPacketListener listener = (ClientPacketListener)(Object)this;
            listener.sendCommand(replaced);
         } else {
            String clean = command.trim();
            if (clean.startsWith("/")) {
               clean = clean.substring(1).trim();
            }

            if (clean.toLowerCase().startsWith("cc ")) {
               clean = clean.substring(3).trim();
               if (clean.startsWith("/")) {
                  clean = clean.substring(1).trim();
               }
            } else if (clean.toLowerCase().startsWith("copychat ")) {
               clean = clean.substring(9).trim();
               if (clean.startsWith("/")) {
                  clean = clean.substring(1).trim();
               }
            }

            String lower = clean.toLowerCase();
            if (!lower.equals("b last") && !lower.equals("bombo last") && !lower.startsWith("b last ") && !lower.startsWith("bombo last ") && !lower.equals("locraw") && !lower.startsWith("locraw ")) {
               SkyblockUtils.lastExecutedCommand = clean;
            }

            if (lower.startsWith("chat ")) {
               String channelArg = lower.substring(5).trim();
               if (!channelArg.equals("b")) {
                  String normalizedTarget = BomboaddonsClient.normalizeChannel(channelArg);
                  String normalizedCurrent = BomboaddonsClient.normalizeChannel(BomboaddonsClient.currentHypixelChannel);
                  boolean wasIrc = BomboConfig.get().ircDefaultChat;
                  if (wasIrc) {
                     BomboConfig.get().ircDefaultChat = false;
                     BomboConfig.save();
                     Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7."));
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
                  Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7."));
               }
            } else if ((lower.startsWith("tell ") || lower.startsWith("w ") || lower.startsWith("msg ") || lower.startsWith("r ") || lower.startsWith("reply ") || lower.startsWith("whisper ") || lower.startsWith("message ")) && BomboConfig.get().ircDefaultChat) {
               BomboConfig.get().ircDefaultChat = false;
               BomboConfig.save();
               Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7."));
            }

         }
      }
   }

   @Inject(
      method = {"handleCommands"},
      at = {@At("TAIL")}
   )
   private void onHandleCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
      try {
         ClientPacketListener listener = (ClientPacketListener)(Object)this;
         CommandDispatcher<ClientSuggestionProvider> dispatcher = listener.getCommands();
         if (dispatcher != null) {
            BomboaddonsClient.registerMsgCommandsToDispatcher(dispatcher);
            BomboaddonsClient.registerBCommandsToDispatcher(dispatcher);

            for(String alias : BomboConfig.get().commandAliases.keySet()) {
               BomboaddonsClient.registerAliasToDispatcher(dispatcher, alias);
            }
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("HEAD")}
   )
   private void onHandleMovePlayerHead(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      try {
         GardenMacroDetector.onMovePlayerPacketHead();
      } catch (Throwable var4) {
      }

   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("RETURN")}
   )
   private void onHandleMovePlayerReturn(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      try {
         GardenMacroDetector.onMovePlayerPacketTail();
      } catch (Throwable var4) {
      }

   }

   @Inject(
      method = {"handleSetEntityPassengersPacket"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandlePassengers(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
      if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(packet.getVehicle()) == null) {
         ci.cancel();
      }

   }
}
