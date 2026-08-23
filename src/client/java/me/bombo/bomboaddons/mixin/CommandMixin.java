package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.CommandTracker;
import me.bombo.bomboaddons.CustomBindsProcessor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public class CommandMixin {
   @Inject(
      method = {"sendCommand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendCommand(String command, CallbackInfo ci) {
      CommandTracker.onCommandSent(command);
      me.bombo.bomboaddons.BomboaddonsClient.recordCommand(command);
      String trimmed = command.trim();
      if (trimmed.equalsIgnoreCase("b") || trimmed.equalsIgnoreCase("bombo") || trimmed.equalsIgnoreCase("bomboaddons")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreen(me.bombo.bomboaddons.BomboConfigGUI.create()));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b gui") || trimmed.equalsIgnoreCase("bombo gui")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreen(new me.bombo.bomboaddons.HudMoveScreen()));
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("b title ") || trimmed.toLowerCase().startsWith("bombo title ")) {
         String rest = trimmed.substring(trimmed.indexOf("title ") + 6).trim();
         String[] parts = rest.split(" ", 2);
         if (parts.length == 1) {
            me.bombo.bomboaddons.BomboaddonsClient.handleTitleCommand("self", parts[0]);
         } else {
            me.bombo.bomboaddons.BomboaddonsClient.handleTitleCommand(parts[0], parts[1]);
         }
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("b sound ") || trimmed.toLowerCase().startsWith("bombo sound ")) {
         String rest = trimmed.substring(trimmed.indexOf("sound ") + 6).trim();
         String[] parts = rest.split(" ", 2);
         if (parts.length == 1) {
            me.bombo.bomboaddons.BomboaddonsClient.handleSoundCommand("self", parts[0]);
         } else {
            me.bombo.bomboaddons.BomboaddonsClient.handleSoundCommand(parts[0], parts[1]);
         }
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("tp ") && me.bombo.bomboaddons.SkyblockUtils.isInGarden()) {
         String arg = trimmed.substring(3).trim();
         if (arg.equalsIgnoreCase("barn")) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
               mc.player.connection.sendCommand("tpbarn");
            }
            ci.cancel();
            return;
         }
         try {
            int p = Integer.parseInt(arg);
            if (p >= 1 && p <= 24) {
               net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
               if (mc.player != null && mc.player.connection != null) {
                  mc.player.connection.sendCommand("tptoplot " + p);
               }
               ci.cancel();
               return;
            }
         } catch (Exception ignored) {}
      }
      if (CustomBindsProcessor.processAlias(command)) {
         ci.cancel();
      }

   }

   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendChat(String message, CallbackInfo ci) {
      if (message.startsWith("/")) me.bombo.bomboaddons.BomboaddonsClient.recordCommand(message.substring(1));
      String trimmed = message.trim();
      if (trimmed.equalsIgnoreCase("/b") || trimmed.equalsIgnoreCase("/bombo") || trimmed.equalsIgnoreCase("/bomboaddons")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreen(me.bombo.bomboaddons.BomboConfigGUI.create()));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("/b gui") || trimmed.equalsIgnoreCase("/bombo gui")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreen(new me.bombo.bomboaddons.HudMoveScreen()));
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("/b title ") || trimmed.toLowerCase().startsWith("/bombo title ")) {
         String rest = trimmed.substring(trimmed.indexOf("title ") + 6).trim();
         String[] parts = rest.split(" ", 2);
         if (parts.length == 1) {
            me.bombo.bomboaddons.BomboaddonsClient.handleTitleCommand("self", parts[0]);
         } else {
            me.bombo.bomboaddons.BomboaddonsClient.handleTitleCommand(parts[0], parts[1]);
         }
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("/b sound ") || trimmed.toLowerCase().startsWith("/bombo sound ")) {
         String rest = trimmed.substring(trimmed.indexOf("sound ") + 6).trim();
         String[] parts = rest.split(" ", 2);
         if (parts.length == 1) {
            me.bombo.bomboaddons.BomboaddonsClient.handleSoundCommand("self", parts[0]);
         } else {
            me.bombo.bomboaddons.BomboaddonsClient.handleSoundCommand(parts[0], parts[1]);
         }
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("/tp ") && me.bombo.bomboaddons.SkyblockUtils.isInGarden()) {
         String arg = trimmed.substring(4).trim();
         if (arg.equalsIgnoreCase("barn")) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
               mc.player.connection.sendCommand("tpbarn");
            }
            ci.cancel();
            return;
         }
         try {
            int p = Integer.parseInt(arg);
            if (p >= 1 && p <= 24) {
               net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
               if (mc.player != null && mc.player.connection != null) {
                  mc.player.connection.sendCommand("tptoplot " + p);
               }
               ci.cancel();
               return;
            }
         } catch (Exception ignored) {}
      }
      if (CustomBindsProcessor.processAlias(message)) {
         ci.cancel();
      }

   }
}
