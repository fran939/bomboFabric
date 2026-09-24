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
      if (trimmed.equalsIgnoreCase("b order") || trimmed.equalsIgnoreCase("bombo order") || trimmed.equalsIgnoreCase("bomboaddons order")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(new me.bombo.bomboaddons.gui.config.BomboOrderScreen(null)));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b buttons move") || trimmed.equalsIgnoreCase("bombo buttons move") || trimmed.equalsIgnoreCase("bomboaddons buttons move")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(new me.bombo.bomboaddons.features.buttons.InventoryButtonMoveScreen(null)));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b buttons") || trimmed.equalsIgnoreCase("bombo buttons") || trimmed.equalsIgnoreCase("bomboaddons buttons")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(new me.bombo.bomboaddons.features.buttons.InventoryButtonsScreen(null)));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b item") || trimmed.equalsIgnoreCase("bombo item") || trimmed.equalsIgnoreCase("bomboaddons item")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> me.bombo.bomboaddons.features.ItemInfoCommand.execute());
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b swap") || trimmed.equalsIgnoreCase("bombo swap") || trimmed.equalsIgnoreCase("bomboaddons swap")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(new me.bombo.bomboaddons.features.swapper.InventorySwapScreen(null)));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b pv") || trimmed.equalsIgnoreCase("bombo pv")
            || trimmed.toLowerCase().startsWith("b pv ") || trimmed.toLowerCase().startsWith("bombo pv ")) {
         String arg = "";
         int idx = trimmed.indexOf("pv");
         if (idx != -1 && idx + 2 < trimmed.length()) {
            arg = trimmed.substring(idx + 2).trim();
         }
         dev.vy.betterpv.client.ProfileViewerOpener.openSelfOr(arg.isEmpty() ? null : arg);
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b") || trimmed.equalsIgnoreCase("bombo") || trimmed.equalsIgnoreCase("bomboaddons")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(me.bombo.bomboaddons.gui.config.BomboConfigScreen.create()));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b totem") || trimmed.equalsIgnoreCase("bombo totem") || trimmed.equalsIgnoreCase("bomboaddons totem")
            || trimmed.toLowerCase().startsWith("b totem ") || trimmed.toLowerCase().startsWith("bombo totem ") || trimmed.toLowerCase().startsWith("bomboaddons totem ")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         String arg = "";
         int idx = trimmed.indexOf("totem");
         if (idx != -1 && idx + 5 < trimmed.length()) {
            arg = trimmed.substring(idx + 5).trim();
         }

         if (mc.player != null) {
            if (!arg.isEmpty()) {
               net.minecraft.world.item.ItemStack stack = me.bombo.bomboaddons.features.TotemAnimationManager.resolveItemStack(arg);
               if (stack != null && !stack.isEmpty()) {
                  me.bombo.bomboaddons.features.TotemAnimationManager.playTotemAnimation(stack);
               } else {
                  mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bBomboAddons§8] §cCould not find item for ID/Name: §e" + arg));
               }
            } else {
               net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
               if (!held.isEmpty()) {
                  me.bombo.bomboaddons.features.TotemAnimationManager.playTotemAnimation(held);
               } else {
                  mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bBomboAddons§8] §cYou must hold an item to test the totem animation or type §e/b totem <itemId>§c!"));
               }
            }
         }
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("freecam") || trimmed.equalsIgnoreCase("b freecam") || trimmed.equalsIgnoreCase("bombo freecam")) {
         me.bombo.bomboaddons.flavor.Flavor.get().freecamToggle();
         ci.cancel();
         return;
      }
      if (trimmed.matches("^(?i)f[1-7]$")) {
         int floor = Integer.parseInt(trimmed.substring(1));
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("joindungeon catacombs " + floor);
         }
         ci.cancel();
         return;
      }
      if (trimmed.matches("^(?i)m[1-7]$")) {
         int floor = Integer.parseInt(trimmed.substring(1));
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("joindungeon master_catacombs " + floor);
         }
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("e") || trimmed.equalsIgnoreCase("fe") || trimmed.equalsIgnoreCase("f0")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("joindungeon catacombs 0");
         }
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("gp") || trimmed.equalsIgnoreCase("greenhouse") || trimmed.equalsIgnoreCase("b gp") || trimmed.equalsIgnoreCase("bombo gp")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(new me.bombo.bomboaddons.features.garden.GreenhouseProfitScreen(null)));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b gui") || trimmed.equalsIgnoreCase("bombo gui")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(new me.bombo.bomboaddons.HudMoveScreen()));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("b sim") || trimmed.equalsIgnoreCase("bombo sim") || trimmed.equalsIgnoreCase("bomboaddons sim")
            || trimmed.toLowerCase().startsWith("b sim ") || trimmed.toLowerCase().startsWith("bombo sim ") || trimmed.toLowerCase().startsWith("bomboaddons sim ")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         String arg = "";
         int idx = trimmed.indexOf("sim");
         if (idx != -1 && idx + 3 < trimmed.length()) {
            arg = trimmed.substring(idx + 3).trim();
         }
         if (arg.isEmpty() || arg.equalsIgnoreCase("clear") || arg.equalsIgnoreCase("reset") || arg.equalsIgnoreCase("off")) {
            me.bombo.bomboaddons.SkyblockUtils.setSimulatedArea(null);
            if (mc.player != null) {
               mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§6BomboAddons§8] §7Area simulation §cdisabled§7. Current real area: §e" + me.bombo.bomboaddons.SkyblockUtils.getLocation()));
            }
         } else {
            me.bombo.bomboaddons.SkyblockUtils.setSimulatedArea(arg);
            if (mc.player != null) {
               mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§6BomboAddons§8] §7Area simulation set to: §a" + arg));
            }
         }
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
               mc.player.connection.sendCommand("tptoplot barn");
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
      if (trimmed.equalsIgnoreCase("/b pv") || trimmed.equalsIgnoreCase("/bombo pv")
            || trimmed.toLowerCase().startsWith("/b pv ") || trimmed.toLowerCase().startsWith("/bombo pv ")) {
         String arg = "";
         int idx = trimmed.indexOf("pv");
         if (idx != -1 && idx + 2 < trimmed.length()) {
            arg = trimmed.substring(idx + 2).trim();
         }
         dev.vy.betterpv.client.ProfileViewerOpener.openSelfOr(arg.isEmpty() ? null : arg);
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("/b") || trimmed.equalsIgnoreCase("/bombo") || trimmed.equalsIgnoreCase("/bomboaddons")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(me.bombo.bomboaddons.gui.config.BomboConfigScreen.create()));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("/freecam") || trimmed.equalsIgnoreCase("/b freecam") || trimmed.equalsIgnoreCase("/bombo freecam")) {
         me.bombo.bomboaddons.flavor.Flavor.get().freecamToggle();
         ci.cancel();
         return;
      }
      if (trimmed.matches("^/(?i)f[1-7]$")) {
         int floor = Integer.parseInt(trimmed.substring(2));
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("joindungeon catacombs " + floor);
         }
         ci.cancel();
         return;
      }
      if (trimmed.matches("^/(?i)m[1-7]$")) {
         int floor = Integer.parseInt(trimmed.substring(2));
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("joindungeon master_catacombs " + floor);
         }
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("/e") || trimmed.equalsIgnoreCase("/fe") || trimmed.equalsIgnoreCase("/f0")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("joindungeon catacombs 0");
         }
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("/gp") || trimmed.equalsIgnoreCase("/greenhouse") || trimmed.equalsIgnoreCase("/b gp") || trimmed.equalsIgnoreCase("/bombo gp")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(new me.bombo.bomboaddons.features.garden.GreenhouseProfitScreen(null)));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("/b gui") || trimmed.equalsIgnoreCase("/bombo gui")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         mc.execute(() -> mc.setScreenAndShow(new me.bombo.bomboaddons.HudMoveScreen()));
         ci.cancel();
         return;
      }
      if (trimmed.equalsIgnoreCase("/b sim") || trimmed.equalsIgnoreCase("/bombo sim") || trimmed.equalsIgnoreCase("/bomboaddons sim")
            || trimmed.toLowerCase().startsWith("/b sim ") || trimmed.toLowerCase().startsWith("/bombo sim ") || trimmed.toLowerCase().startsWith("/bomboaddons sim ")) {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         String arg = "";
         int idx = trimmed.indexOf("sim");
         if (idx != -1 && idx + 3 < trimmed.length()) {
            arg = trimmed.substring(idx + 3).trim();
         }
         if (arg.isEmpty() || arg.equalsIgnoreCase("clear") || arg.equalsIgnoreCase("reset") || arg.equalsIgnoreCase("off")) {
            me.bombo.bomboaddons.SkyblockUtils.setSimulatedArea(null);
            if (mc.player != null) {
               mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§6BomboAddons§8] §7Area simulation §cdisabled§7. Current real area: §e" + me.bombo.bomboaddons.SkyblockUtils.getLocation()));
            }
         } else {
            me.bombo.bomboaddons.SkyblockUtils.setSimulatedArea(arg);
            if (mc.player != null) {
               mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§6BomboAddons§8] §7Area simulation set to: §a" + arg));
            }
         }
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
               mc.player.connection.sendCommand("tptoplot barn");
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
