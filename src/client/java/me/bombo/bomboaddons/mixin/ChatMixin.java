package me.bombo.bomboaddons.mixin;

import java.util.List;
import java.util.function.Predicate;
import me.bombo.bomboaddons.CarnivalAuto;
import me.bombo.bomboaddons.SphinxMacro;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ ChatComponent.class })
public abstract class ChatMixin implements me.bombo.bomboaddons.util.IChatComponent {
   @Shadow
   @Final
   private List<GuiMessage> allMessages;
   @Shadow
   @Final
   private List<GuiMessage.Line> trimmedMessages;

   @Shadow
   @Final
   private Minecraft minecraft;

   @Shadow
   protected abstract void refreshTrimmedMessages();

   @Shadow
   public abstract double getScale();

   @Shadow
   public abstract int getWidth();

   @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"))
   private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
      if (message != null) {
         String raw = message.getString();
         if (raw.contains("DailyRewardDebug") || raw.contains("[BomboAddons]")) return;
         SphinxMacro.onChatMessage(raw);
         CarnivalAuto.onChatMessage(raw);
         me.bombo.bomboaddons.KuudraPerkClicker.onChatMessage(raw);
         me.bombo.bomboaddons.kuudra.pearls.Pearls.onChatMessage(raw);
         // me.bombo.bomboaddons.DiscordBridge.onChatMessage(raw);
         if (me.bombo.bomboaddons.BomboConfig.get().debugDailyReward && minecraft != null && minecraft.player != null) {
             if (raw.contains("Reward") || raw.contains("rewards.hypixel.net") || raw.contains("Claim")) {
                 minecraft.player.sendSystemMessage(Component.literal("§8[§bDailyRewardDebug§8] §7Chat message: " + raw));
             }
         }
         if (me.bombo.bomboaddons.BomboConfig.get().dailyRewardHelper) {
             String url = findRewardUrl(message);
             if (url != null) {
                 String key = me.bombo.bomboaddons.DailyRewardHelper.extractRewardKey(url);
                 if (key != null) {
                     me.bombo.bomboaddons.DailyRewardHelper.fetchAndOpenRewardPage(key);
                 }
             }
         }
      }

   }

   @Unique
   private static String findRewardUrl(Component component) {
      if (component == null) return null;
      if (component.getStyle() != null && component.getStyle().getClickEvent() != null) {
          net.minecraft.network.chat.ClickEvent event = component.getStyle().getClickEvent();
          String val = extractClickEventValue(event);
          if (val != null && val.contains("rewards.hypixel.net")) {
              return val;
          }
      }
      String raw = component.getString();
      if (raw.contains("rewards.hypixel.net")) {
          return raw;
      }
      for (Component sibling : component.getSiblings()) {
          String found = findRewardUrl(sibling);
          if (found != null) return found;
      }
      return null;
   }

   @Unique
   private static String extractClickEventValue(net.minecraft.network.chat.ClickEvent clickEvent) {
       if (clickEvent == null) return null;
       try {
           for (java.lang.reflect.Field f : clickEvent.getClass().getDeclaredFields()) {
               f.setAccessible(true);
               Object v = f.get(clickEvent);
               if (v != null) {
                   String str = v.toString();
                   if (str.contains("rewards.hypixel.net")) return str;
               }
           }
       } catch (Throwable t) {}
       return clickEvent.toString();
   }

   @Unique
   public void bombo$removeMessages(Predicate<GuiMessage> predicate) {
      boolean removed = this.allMessages.removeIf(predicate);
      if (removed) {
         this.trimmedMessages.clear();
         this.refreshTrimmedMessages();
      }

   }

   @Shadow
   private int chatScrollbarPos;

   @Unique
   @Override
   public double bombo$getScale() {
      return this.getScale();
   }


   @Unique
   @Override
   public java.util.List<GuiMessage.Line> bombo$getFullMessageLines(GuiMessage.Line clickedLine) {
       java.util.List<GuiMessage.Line> result = new java.util.ArrayList<>();
       if (this.trimmedMessages == null) return result;
       int targetTime = clickedLine.addedTime();
       for (int i = this.trimmedMessages.size() - 1; i >= 0; i--) {
           GuiMessage.Line line = this.trimmedMessages.get(i);
           if (line.addedTime() == targetTime) {
               result.add(line);
           }
       }
       return result;
   }
   @Unique
   @Override
   public GuiMessage.Line bombo$getLineAt(double mouseX, double mouseY) {
      if (this.trimmedMessages == null || this.trimmedMessages.isEmpty()) return null;
      if (this.minecraft.screen == null) return null;
      
      double d = mouseX - 2.0;
      double e = (double)this.minecraft.getWindow().getGuiScaledHeight() - mouseY - 40.0;
      double scale = this.getScale();
      d = d / scale;
      e = e / scale;
      
      int width = this.getWidth();
      if (d >= 0.0 && d <= (double)width) {
         double chatLineSpacing = this.minecraft.options.chatLineSpacing().get();
         double chatLineHeight = 9.0 * (chatLineSpacing + 1.0);
         int lineIndex = net.minecraft.util.Mth.floor(e / chatLineHeight);
         int scrolledLineIndex = lineIndex + this.chatScrollbarPos;
         if (scrolledLineIndex >= 0 && scrolledLineIndex < this.trimmedMessages.size()) {
            return this.trimmedMessages.get(scrolledLineIndex);
          }
       }
       return null;
    }
}
