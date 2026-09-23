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

   @Unique
   public void bombo$refreshTrimmedMessages() {
      this.refreshTrimmedMessages();
   }

   @Inject(
      method = {"refreshTrimmedMessages"},
      at = {@At("TAIL")}
   )
   private void onRefreshTrimmedMessagesTail(CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.chatTabs && s.activeChatTab != null && !"All".equalsIgnoreCase(s.activeChatTab)) {
         this.trimmedMessages.removeIf(line -> {
            if (line == null || line.content() == null) return false;
            String text = IChatComponent.getLinePlainText(line.content());
            return !me.bombo.bomboaddons.features.chat.ChatTabsOverlay.matchesActiveTab(text);
         });
      }
   }

   @org.spongepowered.asm.mixin.injection.ModifyConstant(
      method = {"addMessage(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V", "addMessageToQueue"},
      constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 100),
      require = 0
   )
   private int expandChatHistoryLimit(int original) {
      return 5000;
   }

   @Inject(
      method = {"clearMessages"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onClearMessages(boolean clearSentHistory, CallbackInfo ci) {
      if (!clearSentHistory) {
         this.refreshTrimmedMessages();
         ci.cancel();
      }
   }

   @Shadow
   public abstract double getScale();

   @Shadow
   public abstract int getWidth();

   @Shadow
   protected abstract void addMessage(Component var1, MessageSignature var2, GuiMessageSource var3, GuiMessageTag var4);

   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onExtractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int tickCount, int mouseX, int mouseY, net.minecraft.client.gui.components.ChatComponent.DisplayMode displayMode, boolean chatOpen, CallbackInfo ci) {
      if (chatOpen && me.bombo.bomboaddons.util.ChatSearchHelper.isChatSearchActive()) {
         ci.cancel();
      }
   }

   /**
    * No Obfuscate: rewrites the message argument before anything else looks at it.
    *
    * <p>Deliberately an argument rewrite rather than a cancel-and-re-add like the other
    * transforms below. Those short-circuit the rest of the pipeline for the message they handle;
    * stripping \u00a7k must not silently disable chat triggers, tab filtering or message history.
    */
   @org.spongepowered.asm.mixin.injection.ModifyVariable(
      method = {"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Component bombo$noObfuscate(Component message) {
      if (message == null) return null;
      if (!me.bombo.bomboaddons.util.NoObfuscate.isEnabled()) return message;
      try {
         return me.bombo.bomboaddons.util.NoObfuscate.strip(message);
      } catch (Throwable ignored) {
         return message;
      }
   }

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

            me.bombo.bomboaddons.features.AutoRejoinManager.onChatMessage(raw);
            me.bombo.bomboaddons.GardenMovement.onChatMessage(raw);
            me.bombo.bomboaddons.features.profile.AutoProfileSwapper.onChatMessage(raw);
            me.bombo.bomboaddons.features.TotemAnimationManager.onChatMessage(raw);
            me.bombo.bomboaddons.features.party.PartyManager.onChatMessage(raw);

            if (me.bombo.bomboaddons.features.ring.RingManager.shouldSuppressMessage(raw)) {
               me.bombo.bomboaddons.features.chat.ChatHistoryTracker.recordIncoming(message, true, "RING_SUPPRESS");
               ci.cancel();
               return;
            }

            Component ringComp = me.bombo.bomboaddons.features.ring.RingManager.processIncomingChat(raw);
            if (ringComp != null) {
               me.bombo.bomboaddons.features.chat.ChatHistoryTracker.recordIncoming(message, false, "RING");
               ci.cancel();
               isFormattingMessage.set(true);
               try {
                  this.addMessage(ringComp, signature, source, tag);
               } finally {
                  isFormattingMessage.set(false);
               }
               return;
            }

            if (me.bombo.bomboaddons.ChatModifier.shouldHide(raw)) {
               me.bombo.bomboaddons.features.chat.ChatHistoryTracker.recordIncoming(message, true, me.bombo.bomboaddons.features.chat.ChatHistoryTracker.detectCategory(raw));
               ci.cancel();
               return;
            }

            me.bombo.bomboaddons.features.chat.ChatHistoryTracker.recordIncoming(message, false, me.bombo.bomboaddons.features.chat.ChatHistoryTracker.detectCategory(raw));

            if (raw.contains("[SHOW:")) {
               Component formatted = me.bombo.bomboaddons.IRCClient.formatWithLinks(raw);
               if (formatted != null && !formatted.getString().equals(raw)) {
                  ci.cancel();
                  isFormattingMessage.set(true);
                  try {
                     this.addMessage(formatted, signature, source, tag);
                  } finally {
                     isFormattingMessage.set(false);
                  }
                  return;
               }
            }

            me.bombo.bomboaddons.BomboaddonsClient.trackCommandFromChat(raw);

            String modifiedChat = me.bombo.bomboaddons.ChatModifier.modifyText(raw);
            if (!modifiedChat.equals(raw)) {
               ci.cancel();
               isFormattingMessage.set(true);
               try {
                  this.addMessage(ChromaTextHelper.parseFormattedText(modifiedChat), signature, source, tag);
               } finally {
                  isFormattingMessage.set(false);
               }
               return;
            }

            String chromaProcessed = ChromaTextHelper.processChroma(raw);
            if (!chromaProcessed.equals(raw)) {
               ci.cancel();
               isFormattingMessage.set(true);
               try {
                  this.addMessage(ChromaTextHelper.parseFormattedText(chromaProcessed), signature, source, tag);
               } finally {
                  isFormattingMessage.set(false);
               }
               return;
            }

            if (BomboConfig.get().customSbLevelColor != null && !BomboConfig.get().customSbLevelColor.isEmpty() && (raw.contains("[") && raw.contains("]"))) {
               Component sbRecolored = recolorSbLevelInComponent(message, BomboConfig.get().customSbLevelColor);
               if (sbRecolored != null && sbRecolored != message) {
                  ci.cancel();
                  isFormattingMessage.set(true);
                  try {
                     this.addMessage(sbRecolored, signature, source, tag);
                  } finally {
                     isFormattingMessage.set(false);
                  }
                  return;
               }
            }

            if (raw.contains(":")) {
               String emojiProcessed = me.bombo.bomboaddons.util.EmojiShortcodeHelper.replaceEmojis(raw);
               if (!emojiProcessed.equals(raw)) {
                  Component emojiComp = me.bombo.bomboaddons.util.EmojiShortcodeHelper.replaceEmojisInComponent(message);
                  if (emojiComp != null && !emojiComp.getString().equals(raw)) {
                     ci.cancel();
                     isFormattingMessage.set(true);
                     try {
                        this.addMessage(emojiComp, signature, source, tag);
                     } finally {
                        isFormattingMessage.set(false);
                     }
                     return;
                  }
               }
            }

            if (BomboConfig.get().clickableChatCommands) {
               // Only format if message does not already contain hover events (e.g. [SHOW:...] item tooltips)
               boolean hasHover = false;
               if (message.getStyle() != null && message.getStyle().getHoverEvent() != null) {
                  hasHover = true;
               } else {
                  for (Component sibling : message.getSiblings()) {
                     if (sibling.getStyle() != null && sibling.getStyle().getHoverEvent() != null) {
                        hasHover = true;
                        break;
                     }
                  }
               }
               if (!hasHover) {
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
            }

            if (raw.contains("&") && !raw.contains("[BomboAddons]") && !raw.contains("[SHOW:")) {
               String legacyFormatted = formatColorsSafe(raw);
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
            me.bombo.bomboaddons.features.critters.CritterSafariEngine.onChatMessage(raw);
            me.bombo.bomboaddons.features.garden.GreenhouseTracker.onChatMessage(message);
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

            // Strict chat tab filtering: if on a specific tab (like Guild), suppress message display if it doesn't match
            BomboConfig.Settings s = BomboConfig.get();
            if (s != null && s.chatTabs && s.activeChatTab != null && !"All".equalsIgnoreCase(s.activeChatTab)) {
               if (!me.bombo.bomboaddons.features.chat.ChatTabsOverlay.matchesActiveTab(raw)) {
                  ci.cancel();
                  return;
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
   public List<GuiMessage> bombo$getAllMessages() {
      return this.allMessages;
   }

   @Unique
   public double bombo$getScale() {
      return this.getScale();
   }

   @Unique
   private static String formatColorsSafe(String text) {
      if (text == null || !text.contains("&")) return text;
      StringBuilder sb = new StringBuilder();
      String[] parts = text.split("(?=https?://)");
      for (String part : parts) {
         if (part.startsWith("http://") || part.startsWith("https://")) {
            int spaceIdx = part.indexOf(' ');
            if (spaceIdx != -1) {
               sb.append(part, 0, spaceIdx);
               sb.append(part.substring(spaceIdx).replaceAll("&(?=[0-9a-fk-orA-FK-OR])", "§"));
            } else {
               sb.append(part);
            }
         } else {
            sb.append(part.replaceAll("&(?=[0-9a-fk-orA-FK-OR])", "§"));
         }
      }
      return sb.toString();
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
         if (this.minecraft.gui.screen() == null) {
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

   @Unique
   private static String getSbLevelColor(int level) {
      if (level < 40) return "§f";       // 0-39: White
      if (level < 80) return "§e";       // 40-79: Yellow
      if (level < 120) return "§a";      // 80-119: Green (Lime)
      if (level < 160) return "§2";      // 120-159: Dark Green
      if (level < 200) return "§b";      // 160-199: Aqua
      if (level < 240) return "§3";      // 200-239: Dark Aqua
      if (level < 280) return "§9";      // 240-279: Blue
      if (level < 320) return "§d";      // 280-319: Light Purple (Pink)
      if (level < 360) return "§5";      // 320-359: Dark Purple
      if (level < 400) return "§6";      // 360-399: Gold
      if (level < 440) return "§c";      // 400-439: Red
      if (level < 480) return "§4";      // 440-479: Dark Red
      return "§b";                       // 480+: Diamond / Cyan
   }

   @Unique
   private static String componentToFormattedString(Component comp) {
      if (comp == null) return "";
      StringBuilder sb = new StringBuilder();
      comp.visit((style, text) -> {
         if (text.isEmpty()) return Optional.empty();
         if (style.getColor() != null) {
            String colName = style.getColor().toString();
            net.minecraft.ChatFormatting cf = net.minecraft.ChatFormatting.valueOf(colName.toUpperCase(java.util.Locale.ROOT));
            if (cf != null) sb.append('§').append(cf.toString().charAt(1));
         }
         if (style.isBold()) sb.append("§l");
         if (style.isItalic()) sb.append("§o");
         if (style.isUnderlined()) sb.append("§n");
         if (style.isStrikethrough()) sb.append("§m");
         if (style.isObfuscated()) sb.append("§k");
         sb.append(text);
         return Optional.empty();
      }, Style.EMPTY);
      return sb.toString();
   }

   @Unique
   private static Component recolorSbLevelInComponent(Component original, String colorCode) {
      if (original == null) return original;
      String formatted = componentToFormattedString(original);
      Pattern p = Pattern.compile("(?:\u00a7[0-9a-fk-or]|&[0-9a-fk-or])*\\[(?:\u00a7[0-9a-fk-or]|&[0-9a-fk-or])*(\\d{1,5})(?:\u00a7[0-9a-fk-or]|&[0-9a-fk-or])*\\]");
      Matcher m = p.matcher(formatted);
      if (!m.find()) return original;

      StringBuffer sb = new StringBuffer();
      m.reset();
      while (m.find()) {
         String lvlNum = m.group(1);
         int lvlInt = 0;
         try { lvlInt = Integer.parseInt(lvlNum); } catch (Exception ignored) {}
         String colFmt;
         if (colorCode != null && !colorCode.isEmpty() && !colorCode.equalsIgnoreCase("Default") && !colorCode.equalsIgnoreCase("Auto")) {
            colFmt = colorCode.equalsIgnoreCase("chroma") ? "§z" : colorCode.replace('&', '§');
         } else {
            colFmt = getSbLevelColor(lvlInt);
         }
         m.appendReplacement(sb, Matcher.quoteReplacement(colFmt + "[" + lvlNum + "]§r"));
      }
      m.appendTail(sb);
      String replacedStr = sb.toString();
      return ChromaTextHelper.parseFormattedText(replacedStr);
   }
}
