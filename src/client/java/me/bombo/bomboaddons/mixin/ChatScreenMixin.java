package me.bombo.bomboaddons.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.DiceHud;
import me.bombo.bomboaddons.DiceTracker;
import me.bombo.bomboaddons.util.IChatComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ChatScreen.class})
public abstract class ChatScreenMixin extends Screen {
   @Shadow
   protected EditBox input;
   @Shadow
   public abstract void moveInHistory(int msgAmount);
   @org.spongepowered.asm.mixin.Unique
   private EditBox searchBox;
   @org.spongepowered.asm.mixin.Unique
   private int searchScrollOffset = 0;

   protected ChatScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo ci) {
      this.searchScrollOffset = 0;
      if (BomboConfig.get().chatSearchBar) {
         BomboConfig.Settings s = BomboConfig.get();
         int defaultSearchY = this.input != null ? this.input.getY() - 18 : this.height - 32;
         int searchX = s.chatSearchX >= 0 ? s.chatSearchX : 4;
         int searchY = s.chatSearchY >= 0 ? s.chatSearchY : defaultSearchY;
         this.searchBox = new EditBox(this.font, searchX, searchY, 150, 12, Component.literal(""));
         this.searchBox.setValue(me.bombo.bomboaddons.util.ChatSearchHelper.activeSearchQuery);
         this.searchBox.setResponder((val) -> {
            this.searchScrollOffset = 0;
            me.bombo.bomboaddons.util.ChatSearchHelper.activeSearchQuery = val != null ? val.trim() : "";
         });
         if (!s.chatSearchBackground) {
            this.searchBox.setBordered(false);
         }
         this.addRenderableWidget(this.searchBox);
      } else {
         me.bombo.bomboaddons.util.ChatSearchHelper.activeSearchQuery = "";
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void onRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (this.searchBox != null && BomboConfig.get().chatSearchBar && !this.searchBox.getValue().trim().isEmpty()) {
         String rawQuery = this.searchBox.getValue().trim();
         String query = rawQuery.toLowerCase();
         int contextCount = 0;
         if (rawQuery.contains(",")) {
            String[] parts = rawQuery.split(",", 2);
            query = parts[0].trim().toLowerCase();
            try {
               contextCount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {}
         }

         Minecraft mc = Minecraft.getInstance();
         if (mc.gui != null && mc.gui.getChat() instanceof IChatComponent chatAccessor) {
            List<GuiMessage> all = chatAccessor.bombo$getAllMessages();
            if (all != null) {
               java.util.TreeSet<Integer> matchedIndices = new java.util.TreeSet<>();
               for (int i = 0; i < all.size(); i++) {
                  GuiMessage msg = all.get(i);
                  String raw = msg.content().getString();
                  String clean = raw.replaceAll("§[0-9a-fk-orxX]", "");
                  if (clean.toLowerCase().contains(query) || raw.toLowerCase().contains(query)) {
                     for (int c = Math.max(0, i - contextCount); c <= Math.min(all.size() - 1, i + contextCount); c++) {
                        matchedIndices.add(c);
                     }
                  }
               }

               int searchX = this.searchBox.getX();
               int searchY = this.searchBox.getY();
               int drawY = searchY - 12;
               int boxH = Math.min(220, Math.max(40, matchedIndices.size() * 10 + 20));
               int topY = searchY - 2 - boxH;
               int boxW = Math.min(400, Math.max(260, this.width - searchX - 10));

               // Background box for search results
               g.fill(searchX - 2, topY, searchX + boxW, searchY - 2, 0xF0101015);
               g.outline(searchX - 2, topY, boxW + 2, searchY - 2 - topY, 0xFF555555);
               String ctxInfo = contextCount > 0 ? " (Context: ±" + contextCount + ")" : "";
               g.text(this.font, "§6§lFiltered Chat: §e\"" + query + "\"§7" + ctxInfo, searchX + 4, topY + 4, -1, true);

               // Draw matching messages from bottom up
               java.util.List<Integer> list = new java.util.ArrayList<>(matchedIndices);
               int maxOffset = Math.max(0, list.size() - 5);
               if (this.searchScrollOffset > maxOffset) this.searchScrollOffset = maxOffset;
               int startK = Math.min(list.size() - 1, list.size() - 1 - this.searchScrollOffset);

               int rendered = 0;
               for (int k = startK; k >= 0; k--) {
                  int idx = list.get(k);
                  GuiMessage msg = all.get(idx);
                  if (drawY >= topY + 16) {
                     g.text(this.font, msg.content(), searchX + 4, drawY, -1, true);
                     drawY -= 10;
                     rendered++;
                  }
               }

               if (rendered == 0) {
                  g.text(this.font, "§7No matching messages found.", searchX + 4, searchY - 20, 0xFFAAAAAA, false);
               }
            }
         }
      }
      if (this.input != null) {
         String text = this.input.getValue();
         if (text != null && text.toLowerCase().startsWith("/bc ")) {
            String message = text.substring(4);
            if (!message.isEmpty() && message.contains("&")) {
               String previewText = "§7Preview: " + message.replace('&', '§');
               int x = this.input.getX() + 2;
               int y = this.input.getY() - 14;
               int textWidth = this.font.width(previewText);
               g.fill(x - 2, y - 2, x + textWidth + 2, y + 10, -1442840576);
               g.text(this.font, previewText, x, y, -1, true);
            }
         }
      }

      me.bombo.bomboaddons.util.ChatImagePreview.renderPreview(g, this.minecraft, mouseX, mouseY);

      if (this.searchBox != null && !this.searchBox.getValue().trim().isEmpty()) {
         GuiMessage hovered = bombo$getSearchResultAt(mouseX, mouseY);
         if (hovered != null) {
            Style s0 = hovered.content().getStyle();
            if (s0 == null || s0.getHoverEvent() == null) {
               for (Component sib : hovered.content().getSiblings()) {
                  if (sib.getStyle() != null && sib.getStyle().getHoverEvent() != null) {
                     s0 = sib.getStyle();
                     break;
                  }
               }
            }
            if (s0 != null && s0.getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText st) {
               g.setTooltipForNextFrame(this.font, st.value(), (int)mouseX, (int)mouseY);
            }
         }
      }
   }

   @Inject(
      method = {"mouseScrolled"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseScrolled(double mouseX, double mouseY, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
      if (this.searchBox != null && !this.searchBox.getValue().trim().isEmpty()) {
         if (vertical > 0) {
            this.searchScrollOffset = Math.max(0, this.searchScrollOffset - 1);
            cir.setReturnValue(true);
         } else if (vertical < 0) {
            this.searchScrollOffset += 1;
            cir.setReturnValue(true);
         }
      }
   }

   @Unique
   private GuiMessage bombo$getSearchResultAt(double mouseX, double mouseY) {
      if (this.searchBox == null || this.searchBox.getValue().trim().isEmpty()) return null;
      Minecraft mc = Minecraft.getInstance();
      if (mc.gui == null || !(mc.gui.getChat() instanceof IChatComponent chatAccessor)) return null;
      List<GuiMessage> all = chatAccessor.bombo$getAllMessages();
      if (all == null) return null;
      String rawQuery = this.searchBox.getValue().trim();
      String query = rawQuery.toLowerCase();
      int contextCount = 0;
      if (rawQuery.contains(",")) {
         String[] parts = rawQuery.split(",", 2);
         query = parts[0].trim().toLowerCase();
         try { contextCount = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
      }
      java.util.TreeSet<Integer> matchedIndices = new java.util.TreeSet<>();
      for (int i = 0; i < all.size(); i++) {
         GuiMessage msg = all.get(i);
         String raw = msg.content().getString();
         String clean = raw.replaceAll("§[0-9a-fk-orxX]", "");
         if (clean.toLowerCase().contains(query) || raw.toLowerCase().contains(query)) {
            for (int c = Math.max(0, i - contextCount); c <= Math.min(all.size() - 1, i + contextCount); c++) {
               matchedIndices.add(c);
            }
         }
      }
      int searchX = this.searchBox.getX();
      int searchY = this.searchBox.getY();
      int drawY = searchY - 12;
      int boxH = Math.min(220, Math.max(40, matchedIndices.size() * 10 + 20));
      int topY = searchY - 2 - boxH;
      int boxW = Math.min(400, Math.max(260, this.width - searchX - 10));

      if (mouseX >= searchX - 2 && mouseX <= searchX + boxW && mouseY >= topY && mouseY <= searchY - 2) {
         java.util.List<Integer> list = new java.util.ArrayList<>(matchedIndices);
         int startK = Math.min(list.size() - 1, list.size() - 1 - this.searchScrollOffset);
         for (int k = startK; k >= 0; k--) {
            int idx = list.get(k);
            if (drawY >= topY + 16) {
               if (mouseY >= drawY - 1 && mouseY <= drawY + 9) {
                  return all.get(idx);
               }
               drawY -= 10;
            }
         }
      }
      return null;
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseClicked(MouseButtonEvent event, boolean handled, CallbackInfoReturnable<Boolean> cir) {
      BomboConfig.Settings s = BomboConfig.get();
      double mouseX = event.x();
      double mouseY = event.y();

      if (this.searchBox != null) {
         if (mouseX >= (double)this.searchBox.getX() && mouseX <= (double)(this.searchBox.getX() + this.searchBox.getWidth()) &&
             mouseY >= (double)this.searchBox.getY() && mouseY <= (double)(this.searchBox.getY() + this.searchBox.getHeight())) {
            this.searchBox.setFocused(true);
            if (this.input != null) this.input.setFocused(false);
            this.setFocused(this.searchBox);
            cir.setReturnValue(true);
            return;
         } else if (this.input != null && mouseX >= (double)this.input.getX() && mouseX <= (double)(this.input.getX() + this.input.getWidth()) &&
                    mouseY >= (double)this.input.getY() && mouseY <= (double)(this.input.getY() + this.input.getHeight())) {
            this.searchBox.setFocused(false);
            this.input.setFocused(true);
            this.setFocused(this.input);
         }
      }

      if (this.searchBox != null && !this.searchBox.getValue().trim().isEmpty()) {
         GuiMessage clickedMsg = bombo$getSearchResultAt(mouseX, mouseY);
         if (clickedMsg != null) {
            int button = event.button();
            if (button == 0) {
               Style s0 = clickedMsg.content().getStyle();
               if (s0 == null || s0.getClickEvent() == null) {
                  for (Component sib : clickedMsg.content().getSiblings()) {
                     if (sib.getStyle() != null && sib.getStyle().getClickEvent() != null) {
                        s0 = sib.getStyle();
                        break;
                     }
                  }
               }
                if (s0 != null && s0.getClickEvent() != null) {
                   defaultHandleClickEvent(s0.getClickEvent(), Minecraft.getInstance(), this);
                   cir.setReturnValue(true);
                   return;
                }
            } else if (button == 1) {
               String raw = clickedMsg.content().getString().replaceAll("§[0-9a-fk-orxX]", "");
               Minecraft.getInstance().keyboardHandler.setClipboard(raw);
               Minecraft.getInstance().gui.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §aCopied message: §r" + raw));
               cir.setReturnValue(true);
               return;
            }
         }
      }

      if (s.diceTracker && DiceTracker.shouldShowHud()) {
         float scale = s.diceHudScale;
         int w = (int)(260.0F * scale);
         int h = (int)(52.0F * scale);
         if (mouseX >= (double)s.diceHudX && mouseX <= (double)(s.diceHudX + w) && mouseY >= (double)s.diceHudY && mouseY <= (double)(s.diceHudY + h)) {
            int button = event.button();
            if (button == 0) {
               if ("Current".equalsIgnoreCase(s.diceDisplayMode)) {
                  s.diceDisplayMode = "Lifetime";
               } else {
                  s.diceDisplayMode = "Current";
               }

               BomboConfig.save();
            } else if (button == 1) {
               DiceHud.showStatsInChat();
            }

            cir.setReturnValue(true);
            return;
         }
      }

      if (BomboConfig.get().copyChat) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gui != null && mc.gui.getChat() != null) {
            if (mc.gui.getChat() instanceof IChatComponent chatAccessor) {
               int button = event.button();
               if (button == 1) {
                  GuiMessage.Line line = chatAccessor.bombo$getLineAt(mouseX, mouseY);
                  if (line != null) {
                     boolean isControl = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 341) || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 345);
                     boolean isAlt = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 342) || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 346);
                     String copiedText = "";
                     if (isControl) {
                        if (isAlt) {
                           copiedText = IChatComponent.getLineWithFormatting(line.content(), '&');
                        } else {
                           copiedText = IChatComponent.getLinePlainText(line.content());
                        }
                     } else {
                        String originalFull = null;
                        List<GuiMessage> allMsgs = chatAccessor.bombo$getAllMessages();
                        if (allMsgs != null) {
                           for (GuiMessage m : allMsgs) {
                              if (m.addedTime() == line.addedTime()) {
                                 originalFull = isAlt ? m.content().getString() : m.content().getString().replaceAll("§[0-9a-fk-orxX]", "");
                                 break;
                              }
                           }
                        }

                        if (originalFull != null && !originalFull.isEmpty()) {
                           copiedText = originalFull;
                        } else {
                           List<GuiMessage.Line> allLines = chatAccessor.bombo$getFullMessageLines(line);
                           StringBuilder sb = new StringBuilder();
                           for (GuiMessage.Line l : allLines) {
                              if (isAlt) {
                                 sb.append(IChatComponent.getLineWithFormatting(l.content(), '&'));
                              } else {
                                 sb.append(IChatComponent.getLinePlainText(l.content()));
                              }
                           }
                           copiedText = sb.toString();
                        }
                     }

                     if (copiedText != null && !copiedText.isEmpty()) {
                        mc.keyboardHandler.setClipboard(copiedText);
                        mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §aCopied chat: §r" + copiedText.replace('&', '§')));
                        cir.setReturnValue(true);
                     }
                  }
               }
            }

         }
      }
   }

   @Inject(
      method = {"removed"},
      at = {@At("HEAD")}
   )
   private void onRemoved(CallbackInfo ci) {
      if (this.searchBox != null) {
         this.searchBox.setValue("");
      }
      me.bombo.bomboaddons.util.ChatSearchHelper.activeSearchQuery = "";
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      // Intercept UP/DOWN arrow on input box to directly move in chat history without Screen focus navigation!
      if (this.input != null) {
         boolean isCtrl = event.hasControlDown() || Minecraft.getInstance().hasControlDown() || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 341) || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 345);
         if (event.key() == 86 && isCtrl) { // 86 is GLFW_KEY_V
            if (me.bombo.bomboaddons.util.ClipboardImageUploader.hasClipboardImage()) {
               if (me.bombo.bomboaddons.util.ClipboardImageUploader.tryUploadClipboardImage(this.input)) {
                  cir.setReturnValue(true);
                  return;
               }
            }
         }
         if (this.input.isFocused()) {
            if (event.key() == 265) {
               this.moveInHistory(-1);
               cir.setReturnValue(true);
               return;
            } else if (event.key() == 264) {
               this.moveInHistory(1);
               cir.setReturnValue(true);
               return;
            }
         }
      }
      if (this.searchBox != null && this.searchBox.isFocused()) {
         if (event.key() == 256) {
            this.searchBox.setFocused(false);
            if (this.input != null) {
               this.input.setFocused(true);
               this.setFocused(this.input);
            }
            cir.setReturnValue(true);
            return;
         }
         if (this.searchBox.keyPressed(event)) {
            cir.setReturnValue(true);
            return;
         }
      }
   }

   public EditBox bombo$getInput() {
      return this.input;
   }
}