package me.bombo.bomboaddons.mixin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.AutoFishing;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.CarnivalAuto;
import me.bombo.bomboaddons.ChromaTextHelper;
import me.bombo.bomboaddons.DailyRewardHelper;
import me.bombo.bomboaddons.KuudraPerkClicker;
import me.bombo.bomboaddons.SphinxMacro;
import me.bombo.bomboaddons.kuudra.pearls.Pearls;
import me.bombo.bomboaddons.util.ChatMessageTracker;
import me.bombo.bomboaddons.util.IChatComponent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ChatComponent.class})
public abstract class ChatMixin implements IChatComponent {
   @Shadow
   @Final
   private List<GuiMessage> allMessages;
   @Shadow
   @Final
   private List<GuiMessage.Line> trimmedMessages;
   @Shadow
   @Final
   private Minecraft minecraft;
   @Unique
   private static final ThreadLocal<Boolean> isFormattingMessage = ThreadLocal.withInitial(() -> false);
   @Shadow
   private int chatScrollbarPos;

   @Shadow
   protected abstract void refreshTrimmedMessages();

   @Shadow
   public abstract double getScale();

   @Shadow
   public abstract int getWidth();

   @Shadow
   protected abstract void addMessage(Component var1, MessageSignature var2, GuiMessageSource var3, GuiMessageTag var4);

   @Inject(
      method = {"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
      if (!(Boolean)isFormattingMessage.get()) {
         if (message != null) {
            ChatMessageTracker.addMessage(message);
            String raw = message.getString();
            if (raw.contains("DailyRewardDebug") || raw.contains("[BomboAddons]")) {
               return;
            }

            String chromaProcessed = ChromaTextHelper.processChroma(raw);
            if (!chromaProcessed.equals(raw)) {
               raw = chromaProcessed;
            }

            if (BomboConfig.get().clickableChatCommands) {
               Component clickable = makeChatCommandsClickable(message);
               if (clickable != message) {
                  ci.cancel();
                  isFormattingMessage.set(true);

                  try {
                     this.addMessage(clickable, signature, source, tag);
                  } finally {
                     isFormattingMessage.set(false);
                  }

                  return;
               }
            }

            if (raw.contains("&") && !raw.contains("[BomboAddons]")) {
               String legacyFormatted = raw.replace('&', '§');
               if (!legacyFormatted.equals(raw)) {
                  ci.cancel();
                  isFormattingMessage.set(true);

                  try {
                     this.addMessage(Component.literal(legacyFormatted), signature, source, tag);
                  } finally {
                     isFormattingMessage.set(false);
                  }

                  return;
               }
            }

            SphinxMacro.onChatMessage(raw);
            CarnivalAuto.onChatMessage(raw);
            AutoFishing.onChatMessage(raw);
            KuudraPerkClicker.onChatMessage(raw);
            Pearls.onChatMessage(raw);
            if (BomboConfig.get().debugDailyReward && this.minecraft != null && this.minecraft.player != null && (raw.contains("Reward") || raw.contains("rewards.hypixel.net") || raw.contains("Claim"))) {
               this.minecraft.player.sendSystemMessage(Component.literal("§8[§bDailyRewardDebug§8] §7Chat message: " + raw));
            }

            if (BomboConfig.get().dailyRewardHelper) {
               String url = findRewardUrl(message);
               if (url != null) {
                  String key = DailyRewardHelper.extractRewardKey(url);
                  if (key != null) {
                     DailyRewardHelper.fetchAndOpenRewardPage(key);
                  }
               }
            }
         }

      }
   }

   @Unique
   private static String findRewardUrl(Component component) {
      if (component == null) {
         return null;
      } else {
         if (component.getStyle() != null && component.getStyle().getClickEvent() != null) {
            ClickEvent event = component.getStyle().getClickEvent();
            String val = extractClickEventValue(event);
            if (val != null && val.contains("rewards.hypixel.net")) {
               return val;
            }
         }

         String raw = component.getString();
         if (raw.contains("rewards.hypixel.net")) {
            return raw;
         } else {
            for(Component sibling : component.getSiblings()) {
               String found = findRewardUrl(sibling);
               if (found != null) {
                  return found;
               }
            }

            return null;
         }
      }
   }

   @Unique
   private static String extractClickEventValue(ClickEvent clickEvent) {
      if (clickEvent == null) {
         return null;
      } else {
         try {
            for(Field f : clickEvent.getClass().getDeclaredFields()) {
               f.setAccessible(true);
               Object v = f.get(clickEvent);
               if (v != null) {
                  String str = v.toString();
                  if (str.contains("rewards.hypixel.net")) {
                     return str;
                  }
               }
            }
         } catch (Throwable var7) {
         }

         return clickEvent.toString();
      }
   }

   @Unique
   public void bombo$removeMessages(Predicate<GuiMessage> predicate) {
      boolean removed = this.allMessages.removeIf(predicate);
      if (removed) {
         this.trimmedMessages.clear();
         this.refreshTrimmedMessages();
      }

   }

   @Unique
   public double bombo$getScale() {
      return this.getScale();
   }

   @Unique
   public List<GuiMessage.Line> bombo$getFullMessageLines(GuiMessage.Line clickedLine) {
      List<GuiMessage.Line> result = new ArrayList();
      if (this.trimmedMessages == null) {
         return result;
      } else {
         int targetTime = clickedLine.addedTime();

         for(int i = this.trimmedMessages.size() - 1; i >= 0; --i) {
            GuiMessage.Line line = (GuiMessage.Line)this.trimmedMessages.get(i);
            if (line.addedTime() == targetTime) {
               result.add(line);
            }
         }

         return result;
      }
   }

   @Unique
   public GuiMessage.Line bombo$getLineAt(double mouseX, double mouseY) {
      if (this.trimmedMessages != null && !this.trimmedMessages.isEmpty()) {
         if (this.minecraft.screen == null) {
            return null;
         } else {
            double d = mouseX - (double)2.0F;
            double e = (double)this.minecraft.getWindow().getGuiScaledHeight() - mouseY - (double)40.0F;
            double scale = this.getScale();
            d /= scale;
            e /= scale;
            int width = this.getWidth();
            if (d >= (double)0.0F && d <= (double)width) {
               double chatLineSpacing = (Double)this.minecraft.options.chatLineSpacing().get();
               double chatLineHeight = (double)9.0F * (chatLineSpacing + (double)1.0F);
               int lineIndex = Mth.floor(e / chatLineHeight);
               int scrolledLineIndex = lineIndex + this.chatScrollbarPos;
               if (scrolledLineIndex >= 0 && scrolledLineIndex < this.trimmedMessages.size()) {
                  return (GuiMessage.Line)this.trimmedMessages.get(scrolledLineIndex);
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   @Unique
   private static Component makeChatCommandsClickable(Component message) {
      if (message == null) {
         return message;
      } else {
         String raw = message.getString();
         if (!raw.contains("/")) {
            return message;
         } else if (raw.startsWith("[DailyRewardDebug")) {
            return message;
         } else {
            List<int[]> urlRanges = new ArrayList();
            Matcher urlMatcher = Pattern.compile("https?://\\S+|www\\.\\S+").matcher(raw);

            while(urlMatcher.find()) {
               urlRanges.add(new int[]{urlMatcher.start(), urlMatcher.end()});
            }

            Pattern cmdPattern = Pattern.compile("(?<=[^a-zA-Z0-9_]|^)(/(?:[a-zA-Z0-9_]{1,32})(?:\\s+[a-zA-Z0-9_\\-]+)*)");
            Matcher matcher = cmdPattern.matcher(raw);
            List<Object[]> commandList = new ArrayList();

            while(matcher.find()) {
               int start = matcher.start();
               int end = matcher.end();
               String cmdStr = matcher.group(1).trim();
               boolean insideUrl = false;

               for(int[] urlRange : urlRanges) {
                  if (start >= urlRange[0] && start < urlRange[1]) {
                     insideUrl = true;
                     break;
                  }
               }

               if (!insideUrl) {
                  commandList.add(new Object[]{start, end, cmdStr});
               }
            }

            if (commandList.isEmpty()) {
               return message;
            } else {
               MutableComponent result = Component.empty();
               int[] globalIndex = new int[]{0};
               message.visit((style, text) -> {
                  if (text != null && !text.isEmpty()) {
                     int segStart = globalIndex[0];
                     int segEnd = segStart + text.length();
                     globalIndex[0] = segEnd;
                     int localOffset = 0;

                     while(localOffset < text.length()) {
                        int currentGlobal = segStart + localOffset;
                        Object[] activeCmd = null;

                        for(Object[] cmdObj : commandList) {
                           int cStart = (Integer)cmdObj[0];
                           int cEnd = (Integer)cmdObj[1];
                           if (currentGlobal >= cStart && currentGlobal < cEnd) {
                              activeCmd = cmdObj;
                              break;
                           }
                        }

                        if (activeCmd != null) {
                           int cEnd = (Integer)activeCmd[1];
                           String fullCmd = (String)activeCmd[2];
                           int subEndGlobal = Math.min(segEnd, cEnd);
                           int length = subEndGlobal - currentGlobal;
                           String sub = text.substring(localOffset, localOffset + length);
                           MutableComponent cmdComp = Component.literal(sub);
                           cmdComp.setStyle(style.withClickEvent(new ClickEvent.RunCommand(fullCmd)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run §b" + fullCmd))));
                           result.append(cmdComp);
                           localOffset += length;
                        } else {
                           int nextCmdStart = segEnd;

                           for(Object[] cmdObj : commandList) {
                              int cStart = (Integer)cmdObj[0];
                              if (cStart > currentGlobal && cStart < nextCmdStart) {
                                 nextCmdStart = cStart;
                              }
                           }

                           int length = nextCmdStart - currentGlobal;
                           String sub = text.substring(localOffset, localOffset + length);
                           MutableComponent plainComp = Component.literal(sub);
                           plainComp.setStyle(style);
                           result.append(plainComp);
                           localOffset += length;
                        }
                     }

                     return Optional.empty();
                  } else {
                     return Optional.empty();
                  }
               }, Style.EMPTY);
               return result;
            }
         }
      }
   }

   public void bombo$removeProfileListMessages() {
      if (this.allMessages != null) {
         this.allMessages.removeIf((m) -> {
            if (m != null && m.content() != null) {
               String text = m.content().getString();
               if (text == null) {
                  return false;
               } else {
                  String clean = text.replaceAll("(?i)[§§].", "").trim();
                  if (clean.contains("Your Config Profiles:")) {
                     return true;
                  } else if (clean.contains("Switched to config profile:")) {
                     return true;
                  } else if (clean.contains("Created new config profile:")) {
                     return true;
                  } else if (clean.contains("▶") && clean.contains("(Active)")) {
                     return true;
                  } else {
                     return clean.startsWith("- ");
                  }
               }
            } else {
               return false;
            }
         });
      }

      if (this.trimmedMessages != null) {
         this.trimmedMessages.removeIf((l) -> {
            if (l != null && l.content() != null) {
               String plain = IChatComponent.getLinePlainText(l.content());
               if (plain == null) {
                  return false;
               } else {
                  String clean = plain.replaceAll("(?i)[§§].", "").trim();
                  if (clean.contains("Your Config Profiles:")) {
                     return true;
                  } else if (clean.contains("Switched to config profile:")) {
                     return true;
                  } else if (clean.contains("Created new config profile:")) {
                     return true;
                  } else if (clean.contains("▶") && clean.contains("(Active)")) {
                     return true;
                  } else {
                     return clean.startsWith("- ");
                  }
               }
            } else {
               return false;
            }
         });
      }

      this.refreshTrimmedMessages();
   }
}
