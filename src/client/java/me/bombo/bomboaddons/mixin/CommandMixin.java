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
      if (trimmed.equalsIgnoreCase("b history") || trimmed.equalsIgnoreCase("bombo history") || trimmed.equalsIgnoreCase("bomboaddons history")) {
         me.bombo.bomboaddons.BomboaddonsClient.showCommandHistory((net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)null, 25, null);
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("b history ") || trimmed.toLowerCase().startsWith("bombo history ") || trimmed.toLowerCase().startsWith("bomboaddons history ")) {
         String q = trimmed.substring(trimmed.indexOf("history ") + 8).trim();
         if (q.equalsIgnoreCase("all") || q.equalsIgnoreCase("*")) {
            me.bombo.bomboaddons.BomboaddonsClient.showCommandHistory((net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)null, Integer.MAX_VALUE, null);
         } else {
            try {
               int count = Integer.parseInt(q);
               me.bombo.bomboaddons.BomboaddonsClient.showCommandHistory((net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)null, count, null);
            } catch (NumberFormatException ignored) {
               me.bombo.bomboaddons.BomboaddonsClient.showCommandHistory((net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)null, 20, q);
            }
         }
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
      if (trimmed.equalsIgnoreCase("b stat") || trimmed.equalsIgnoreCase("b stats") || trimmed.equalsIgnoreCase("b stat mf") || trimmed.equalsIgnoreCase("b stats mf") || trimmed.equalsIgnoreCase("bombo stat mf") || trimmed.equalsIgnoreCase("bombo stats mf")) {
         me.bombo.bomboaddons.BomboaddonsClient.openMagicFindOptimizer(null, null);
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b stat mf diana") || trimmed.equalsIgnoreCase("b stats mf diana") || trimmed.equalsIgnoreCase("bombo stat mf diana") || trimmed.equalsIgnoreCase("bombo stats mf diana")) {
         me.bombo.bomboaddons.BomboaddonsClient.openMagicFindOptimizer(null, null, me.bombo.bomboaddons.features.magicfind.MagicFindOptimizer.Mode.DIANA);
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("b stat mf ") || trimmed.toLowerCase().startsWith("b stats mf ") || trimmed.toLowerCase().startsWith("bombo stat mf ") || trimmed.toLowerCase().startsWith("bombo stats mf ")) {
         String targetUser = trimmed.substring(trimmed.lastIndexOf(" ") + 1).trim();
         if (targetUser.equalsIgnoreCase("diana")) {
            me.bombo.bomboaddons.BomboaddonsClient.openMagicFindOptimizer(null, null, me.bombo.bomboaddons.features.magicfind.MagicFindOptimizer.Mode.DIANA);
         } else {
            me.bombo.bomboaddons.BomboaddonsClient.openMagicFindOptimizer(null, targetUser);
         }
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("bombochromatest") || trimmed.toLowerCase().startsWith("/bombochromatest")) {
         String text = trimmed.contains(" ") ? trimmed.substring(trimmed.indexOf(" ") + 1).trim() : "&w(This is a test of animated chroma text! 12345)";
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc.player != null) {
            String processed = me.bombo.bomboaddons.ChromaTextHelper.processChroma(text);
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bChromaDebug§8] Raw input: §7" + text));
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bChromaDebug§8] Output: §r" + processed));
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
      if (trimmed.equalsIgnoreCase("/b history") || trimmed.equalsIgnoreCase("/bombo history") || trimmed.equalsIgnoreCase("/bomboaddons history")) {
         me.bombo.bomboaddons.BomboaddonsClient.showCommandHistory((net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)null, 25, null);
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("/b history ") || trimmed.toLowerCase().startsWith("/bombo history ") || trimmed.toLowerCase().startsWith("/bomboaddons history ")) {
         String q = trimmed.substring(trimmed.indexOf("history ") + 8).trim();
         if (q.equalsIgnoreCase("all") || q.equalsIgnoreCase("*")) {
            me.bombo.bomboaddons.BomboaddonsClient.showCommandHistory((net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)null, Integer.MAX_VALUE, null);
         } else {
            try {
               int count = Integer.parseInt(q);
               me.bombo.bomboaddons.BomboaddonsClient.showCommandHistory((net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)null, count, null);
            } catch (NumberFormatException ignored) {
               me.bombo.bomboaddons.BomboaddonsClient.showCommandHistory((net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)(Object)null, 20, q);
            }
         }
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
      if (trimmed.equalsIgnoreCase("/stat") || trimmed.equalsIgnoreCase("/stats") || trimmed.equalsIgnoreCase("/stat mf") || trimmed.equalsIgnoreCase("/stats mf") || trimmed.equalsIgnoreCase("/stat magicfind") || trimmed.equalsIgnoreCase("/stats magicfind") || trimmed.equalsIgnoreCase("/b stat") || trimmed.equalsIgnoreCase("/b stats") || trimmed.equalsIgnoreCase("/b stat mf") || trimmed.equalsIgnoreCase("/b stats mf") || trimmed.equalsIgnoreCase("/bombo stat mf") || trimmed.equalsIgnoreCase("/bombo stats mf")) {
         me.bombo.bomboaddons.BomboaddonsClient.openMagicFindOptimizer(null, null);
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("/stat mf diana") || trimmed.equalsIgnoreCase("/stats mf diana") || trimmed.equalsIgnoreCase("/b stat mf diana") || trimmed.equalsIgnoreCase("/b stats mf diana") || trimmed.equalsIgnoreCase("/bombo stat mf diana") || trimmed.equalsIgnoreCase("/bombo stats mf diana")) {
         me.bombo.bomboaddons.BomboaddonsClient.openMagicFindOptimizer(null, null, me.bombo.bomboaddons.features.magicfind.MagicFindOptimizer.Mode.DIANA);
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("/stat mf ") || trimmed.toLowerCase().startsWith("/stats mf ") || trimmed.toLowerCase().startsWith("/stat magicfind ") || trimmed.toLowerCase().startsWith("/stats magicfind ") || trimmed.toLowerCase().startsWith("/b stat mf ") || trimmed.toLowerCase().startsWith("/b stats mf ")) {
         String targetUser = trimmed.substring(trimmed.lastIndexOf(" ") + 1).trim();
         if (targetUser.equalsIgnoreCase("diana")) {
            me.bombo.bomboaddons.BomboaddonsClient.openMagicFindOptimizer(null, null, me.bombo.bomboaddons.features.magicfind.MagicFindOptimizer.Mode.DIANA);
         } else {
            me.bombo.bomboaddons.BomboaddonsClient.openMagicFindOptimizer(null, targetUser);
         }
         ci.cancel();
         return;
      }
      if (trimmed.toLowerCase().startsWith("/bombochromatest")) {
         String text = trimmed.contains(" ") ? trimmed.substring(trimmed.indexOf(" ") + 1).trim() : "&w(This is a test of animated chroma text! 12345)";
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc.player != null) {
            String processed = me.bombo.bomboaddons.ChromaTextHelper.processChroma(text);
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bChromaDebug§8] Raw input: §7" + text));
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bChromaDebug§8] Output: §r" + processed));
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
