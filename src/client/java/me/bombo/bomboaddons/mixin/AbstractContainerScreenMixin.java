package me.bombo.bomboaddons.mixin;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.AutoCroesus;
import me.bombo.bomboaddons.AutoCroesusHud;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboConfigGUI;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.ComposterHelper;
import me.bombo.bomboaddons.CroesusHelper;
import me.bombo.bomboaddons.CustomBindsProcessor;
import me.bombo.bomboaddons.ItemListOverlay;
import me.bombo.bomboaddons.SkyblockCalculator;
import me.bombo.bomboaddons.SlotHighlight;
import me.bombo.bomboaddons.StopwatchManager;
import me.bombo.bomboaddons.util.CustomSlotManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag.Default;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({AbstractContainerScreen.class})
public abstract class AbstractContainerScreenMixin extends Screen {
   @Shadow
   protected @Nullable Slot hoveredSlot;
   @Shadow
   protected int leftPos;
   @Shadow
   protected int topPos;
   @Shadow
   protected int imageWidth;
   @Shadow
   protected int imageHeight;

   protected AbstractContainerScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("HEAD")}
   )
   private void onInit(CallbackInfo ci) {
      ClickLogic.onGuiOpen((AbstractContainerScreen)(Object)this);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInitTail(CallbackInfo ci) {
      CroesusHelper.onContainerTick((AbstractContainerScreen)(Object)this);
      me.bombo.bomboaddons.features.garden.GreenhouseTracker.onContainerTick((AbstractContainerScreen)(Object)this);
      ItemListOverlay.updateLayout(this.leftPos, this.imageWidth, this.topPos, this.width, this.height);
      if (BomboConfig.get().itemListEnabled && ItemListOverlay.sidebarW >= 120) {
         int searchX = ItemListOverlay.sidebarX + 5;
         int searchY = ItemListOverlay.sidebarY + ItemListOverlay.sidebarH - 52;
         int searchW = ItemListOverlay.sidebarW - 10;
         if (BomboConfig.get().itemListSeparateSearch) {
            BomboConfig.Settings s = BomboConfig.get();
            searchW = s.itemListSearchW;
            searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
            searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
         }

         if (ItemListOverlay.searchBox != null) {
            this.removeWidget(ItemListOverlay.searchBox);
         }
         EditBox box = new EditBox(Minecraft.getInstance().font, searchX, searchY, searchW, 16, Component.literal("Search..."));
         box.setMaxLength(1024);
         box.setValue(ItemListOverlay.query);
         box.setResponder((val) -> ItemListOverlay.setQuery(val));
         box.setBordered(true);
         ItemListOverlay.isHiddenState = BomboConfig.get().autoHideItemList;
         boolean isVisible = !ItemListOverlay.isHiddenState;
         if (BomboConfig.get().itemListSearchAlwaysVisible) {
            isVisible = true;
         }

         box.setVisible(isVisible);
         this.addRenderableWidget(box);
         ItemListOverlay.searchBox = box;
      } else {
         ItemListOverlay.searchBox = null;
      }

      if (BomboConfig.get().autoHoppityBuyRabbit) {
         String screenTitle = net.minecraft.ChatFormatting.stripFormatting(this.getTitle().getString()).toLowerCase(java.util.Locale.ROOT);
         if (screenTitle.contains("hoppity") || screenTitle.contains("chocolate factory") || screenTitle.contains("rabbit shop") || screenTitle.contains("rabbits")) {
            long baseDelay = BomboConfig.get().autoHoppityDelayMs;
            long jitter = (long)(baseDelay * (0.8 + Math.random() * 0.4));
            java.util.concurrent.CompletableFuture.delayedExecutor(jitter, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> {
               Minecraft mc = Minecraft.getInstance();
               mc.execute(() -> {
                  if (mc.gui.screen() == (Object)this && ((AbstractContainerScreen)(Object)this).getMenu() != null) {
                     for (Slot s : ((AbstractContainerScreen)(Object)this).getMenu().slots) {
                        if (s != null && s.hasItem()) {
                           ItemStack item = s.getItem();
                           ItemLore lore = (ItemLore)item.get(DataComponents.LORE);
                           if (lore != null) {
                              boolean canTrade = false;
                              for (Component line : lore.lines()) {
                                 String str = net.minecraft.ChatFormatting.stripFormatting(line.getString());
                                 if (str.contains("Click to trade!") || str.contains("Click to buy!")) {
                                    canTrade = true;
                                    break;
                                 }
                              }
                              if (canTrade) {
                                 if (mc.gameMode != null && mc.player != null) {
                                    mc.gameMode.handleContainerInput(((AbstractContainerScreen)(Object)this).getMenu().containerId, s.index, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                                 }
                                 break;
                              }
                           }
                        }
                     }
                  }
               });
            });
         }
      }

      if (BomboConfig.get().storageOverlay) {
         me.bombo.bomboaddons.gui.StorageOverlayManager.onContainerInit((AbstractContainerScreen)(Object)this, this.width, this.height, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
      }

      if (StopwatchManager.isActive()) {
         int swX = 10;
         int swY = 215;
         String pauseLabel = StopwatchManager.isPaused() ? "§a▶" : "§e❚❚";
         this.addRenderableWidget(Button.builder(Component.literal(pauseLabel), (btn) -> {
            StopwatchManager.togglePause();
            btn.setMessage(Component.literal(StopwatchManager.isPaused() ? "§a▶" : "§e❚❚"));
         }).bounds(swX, swY, 25, 16).build());
         this.addRenderableWidget(Button.builder(Component.literal("§c⬛"), (btn) -> {
            StopwatchManager.stop();
            this.clearWidgets();
            this.init();
         }).bounds(swX + 28, swY, 25, 16).build());
      }

   }

   @Inject(
      method = {"extractSlot"},
      at = {@At("HEAD")}
   )
   private void onRenderSlotBg(GuiGraphicsExtractor guiGraphics, Slot slot, int x, int y, CallbackInfo ci) {
      if (BomboConfig.get().trophyHighlight && slot.hasItem()) {
         AbstractContainerScreen<?> screen = (AbstractContainerScreen)(Object)this;
         String title = screen.getTitle().getString();
         if (title.contains("Trophy Fish") || title.contains("Trophy Frogs")) {
            ItemStack stack = slot.getItem();
            ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
            if (lore != null) {
               boolean hasDiamond = false;
               boolean hasGold = false;
               boolean hasSilver = false;
               boolean hasBronze = false;

               for(Component line : lore.lines()) {
                  String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
                  if (clean.contains("Diamond") && (clean.contains("✔") || clean.contains("✓"))) {
                     hasDiamond = true;
                  }

                  if (clean.contains("Gold") && (clean.contains("✔") || clean.contains("✓"))) {
                     hasGold = true;
                  }

                  if (clean.contains("Silver") && (clean.contains("✔") || clean.contains("✓"))) {
                     hasSilver = true;
                  }

                  if (clean.contains("Bronze") && (clean.contains("✔") || clean.contains("✓"))) {
                     hasBronze = true;
                  }
               }

               int color = 0;
               if (hasDiamond) {
                  color = -2141847553;
               } else if (hasGold) {
                  color = -2130716928;
               } else if (hasSilver) {
                  color = -2133601325;
               } else if (hasBronze) {
                  color = -2134016206;
               }

               if (color != 0) {
                  guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
               }
            }
         }
      }

      if (slot.hasItem()) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s != null && s.inventoryItemRarityBg) {
            String code = me.bombo.bomboaddons.SkyblockUtils.getItemRarityColor(slot.getItem());
            int rCol = switch (code) {
               case "§a" -> 0x55FF55;
               case "§9" -> 0x5555FF;
               case "§5" -> 0xAA00AA;
               case "§6" -> 0xFFAA00;
               case "§d" -> 0xFF55FF;
               case "§b" -> 0x55FFFF;
               case "§c" -> 0xFF5555;
               default -> 0;
            };
            if (rCol != 0) {
               guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x44000000 | rCol);
            }
         }
      }

   }

   @Inject(
      method = {"extractSlot"},
      at = {@At("TAIL")}
   )
   private void onRenderSlot(GuiGraphicsExtractor guiGraphics, Slot slot, int x, int y, CallbackInfo ci) {
      int color = 0;
      if (SlotHighlight.isTargetSlot(slot.index)) {
         color = SlotHighlight.getCurrentColor();
      } else if (slot.hasItem()) {
         int nameColor = SlotHighlight.getHighlightColor(slot.getItem().getHoverName().getString());
         if (nameColor != 0) {
            color = nameColor;
         }
      }

      if (color != 0) {
         if ((color & -16777216) == 0) {
            color |= Integer.MIN_VALUE;
         }

         guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
      }

       if (slot.hasItem()) {
          BomboConfig.Settings s = BomboConfig.get();
          boolean isGardenMoving = s != null && s.gardenBlockSlotsWhileFarming && me.bombo.bomboaddons.GardenMovement.isActive() && me.bombo.bomboaddons.SkyblockUtils.isInGarden();
          if (isGardenMoving) {
             guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x771E293B);
             guiGraphics.outline(slot.x, slot.y, 16, 16, 0xAA64748B);
          } else if (s != null && s.blockedSlots != null && !s.blockedSlots.isEmpty()) {
             String currentGui = net.minecraft.ChatFormatting.stripFormatting(this.getTitle().getString()).toLowerCase();
             String currentArea = me.bombo.bomboaddons.BomboaddonsClient.currentArea != null ? me.bombo.bomboaddons.BomboaddonsClient.currentArea.toLowerCase() : "";
             ItemStack stack = slot.getItem();
             String itemName = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()).toLowerCase();
             String sbId = me.bombo.bomboaddons.SkyblockUtils.getSkyblockId(stack);
             if (sbId == null) sbId = "";
             sbId = sbId.toLowerCase();

             for (BomboConfig.BlockedSlotDef def : s.blockedSlots) {
                if (def == null || !def.enabled) continue;
                String im = def.itemMatcher != null ? def.itemMatcher.trim().toLowerCase() : "";
                String gm = def.guiMatcher != null ? def.guiMatcher.trim().toLowerCase() : "";
                String ism = def.islandMatcher != null ? def.islandMatcher.trim().toLowerCase() : "";

                boolean itemMatch = im.isEmpty() || itemName.contains(im) || sbId.equalsIgnoreCase(im);
                boolean guiMatch = gm.isEmpty() || currentGui.contains(gm);
                boolean islandMatch = ism.isEmpty() || currentArea.contains(ism);

                if (itemMatch && guiMatch && islandMatch && (!im.isEmpty() || !gm.isEmpty() || !ism.isEmpty())) {
                   guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x66FF2222);
                   guiGraphics.outline(slot.x, slot.y, 16, 16, 0xFFFF4444);
                   break;
                }
             }
          }
       }

      if (ItemListOverlay.inventorySearchMode && ItemListOverlay.query != null && !ItemListOverlay.query.trim().isEmpty() && slot.hasItem()) {
         ItemStack stack = slot.getItem();
         String rawQuery = ItemListOverlay.query.toLowerCase(java.util.Locale.ROOT);
         String[] tokens = rawQuery.split(",");
         for (int i = 0; i < tokens.length; i++) tokens[i] = tokens[i].trim();

         String name = stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
         boolean nameMatch = false;
         for (String t : tokens) {
            if (!t.isEmpty() && name.contains(t)) {
               nameMatch = true;
               break;
            }
         }

         int highlightColor = 0;
         if (nameMatch) {
            highlightColor = -1442775296;
         } else {
            boolean loreMatch = false;
            ItemLore lore = (ItemLore) stack.get(DataComponents.LORE);
            if (lore != null) {
               for (Component c : lore.lines()) {
                  if (c == null) continue;
                  String lineStr = c.getString().toLowerCase(java.util.Locale.ROOT);
                  for (String t : tokens) {
                     if (!t.isEmpty() && lineStr.contains(t)) {
                        loreMatch = true;
                        break;
                     }
                  }
                  if (loreMatch) break;
               }
            }

            if (loreMatch) {
               highlightColor = -1426063616;
            } else {
               highlightColor = -1442840576;
            }
         }

         if (highlightColor != 0) {
            guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, highlightColor);
         }

         String title = this.getTitle() != null ? this.getTitle().getString() : "";
         if (me.bombo.bomboaddons.features.garden.GreenhouseTracker.shouldHighlightConfigurePlotsSlot(slot.getItem(), title)) {
            guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x8822C55E);
            guiGraphics.outline(slot.x, slot.y, 16, 16, 0xFF10B981);
         }
      }

   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseClicked(MouseButtonEvent event, boolean handled, CallbackInfoReturnable<Boolean> cir) {
      if (me.bombo.bomboaddons.features.buttons.InventoryButtonManager.mouseClicked((AbstractContainerScreen<?>)(Object)this, event.x(), event.y(), event.button(), this.leftPos, this.topPos, this.imageWidth, this.imageHeight)) {
         cir.setReturnValue(true);
         return;
      }
      if (me.bombo.bomboaddons.features.swapper.InventorySlotSwapManager.handleSlotClick((AbstractContainerScreen<?>)(Object)this, this.hoveredSlot, event.button(), event.hasShiftDown() || (Minecraft.getInstance() != null && Minecraft.getInstance().hasShiftDown()))) {
         cir.setReturnValue(true);
         return;
      }
      if (me.bombo.bomboaddons.gui.StorageOverlayManager.mouseClicked(event, this.width, this.height)) {
         cir.setReturnValue(true);
         return;
      }

      if (me.bombo.bomboaddons.features.hud.EquipmentHud.onMouseClick(event.x(), event.y(), event.button())) {
         cir.setReturnValue(true);
         return;
      }
      if (me.bombo.bomboaddons.features.hud.ArmorHud.onMouseClick(event.x(), event.y(), event.button())) {
         cir.setReturnValue(true);
         return;
      }

      if (ItemListOverlay.searchBox != null) {
         float scale = BomboConfig.get().itemListSearchScale;
         double unscaledX = event.x();
         double unscaledY = event.y();
         if (scale != 1.0F) {
            int tx = ItemListOverlay.searchBox.getX();
            int ty = ItemListOverlay.searchBox.getY();
            unscaledX = (double)tx + (event.x() - (double)tx) / (double)scale;
            unscaledY = (double)ty + (event.y() - (double)ty) / (double)scale;
         }

         if (event.button() == 0 && ItemListOverlay.searchBox.isMouseOver(unscaledX, unscaledY)) {
            ItemListOverlay.searchBox.setFocused(true);
            ((AbstractContainerScreen)(Object)this).setFocused(ItemListOverlay.searchBox);
         } else if (event.button() == 0 && ItemListOverlay.searchBox.isFocused()) {
            ItemListOverlay.searchBox.setFocused(false);
         }
      }

      if (ItemListOverlay.mouseClicked(event.x(), event.y(), event.button())) {
         cir.setReturnValue(true);
      } else {
         BomboConfig.Settings s = BomboConfig.get();
         boolean isGardenMoving = s != null && s.gardenBlockSlotsWhileFarming && me.bombo.bomboaddons.GardenMovement.isActive() && me.bombo.bomboaddons.SkyblockUtils.isInGarden();
         if (isGardenMoving && this.hoveredSlot != null) {
            cir.setReturnValue(true);
            return;
         }

         if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (s != null && s.blockedSlots != null && !s.blockedSlots.isEmpty()) {
               boolean isBypassed = false;
               int bypassKey = ClickLogic.getKeyCode(s.blockedSlotsBypassKey);
               if (bypassKey != -1 && org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), bypassKey) == 1) {
                  isBypassed = true;
               }

               if (!isBypassed) {
                  String currentGui = net.minecraft.ChatFormatting.stripFormatting(this.getTitle().getString()).toLowerCase();
                  String currentArea = me.bombo.bomboaddons.BomboaddonsClient.currentArea != null ? me.bombo.bomboaddons.BomboaddonsClient.currentArea.toLowerCase() : "";
                  ItemStack stack = this.hoveredSlot.getItem();
                  String itemName = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()).toLowerCase();
                  String sbId = me.bombo.bomboaddons.SkyblockUtils.getSkyblockId(stack);
                  if (sbId == null) sbId = "";
                  sbId = sbId.toLowerCase();

                  for (BomboConfig.BlockedSlotDef def : s.blockedSlots) {
                     if (def == null || !def.enabled) continue;
                     String im = def.itemMatcher != null ? def.itemMatcher.trim().toLowerCase() : "";
                     String gm = def.guiMatcher != null ? def.guiMatcher.trim().toLowerCase() : "";
                     String ism = def.islandMatcher != null ? def.islandMatcher.trim().toLowerCase() : "";

                     boolean itemMatch = im.isEmpty() || itemName.contains(im) || sbId.equalsIgnoreCase(im);
                     boolean guiMatch = gm.isEmpty() || currentGui.contains(gm);
                     boolean islandMatch = ism.isEmpty() || currentArea.contains(ism);

                     if (itemMatch && guiMatch && islandMatch && (!im.isEmpty() || !gm.isEmpty() || !ism.isEmpty())) {
                        if (Minecraft.getInstance().player != null) {
                           Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§cBlocked Slot§8] §7Click blocked! Hold §e" + s.blockedSlotsBypassKey + " §7to bypass."));
                        }
                        cir.setReturnValue(true);
                        return;
                     }
                  }
               }
            }
         }

         if (this.hoveredSlot != null) {
            if (Minecraft.getInstance().hasControlDown() && me.bombo.bomboaddons.features.SupercraftHelper.handleCtrlClick((AbstractContainerScreen)(Object)this, this.hoveredSlot)) {
               cir.setReturnValue(true);
               return;
            }
            ItemStack override = CustomSlotManager.getOverride(this.hoveredSlot);
            if (override != null) {
               if (Minecraft.getInstance().hasControlDown()) {
                  AbstractContainerScreen<?> screen = (AbstractContainerScreen)(Object)this;
                  String title = screen.getTitle().getString();
                  Minecraft.getInstance().setScreenAndShow(new BomboConfigGUI((Screen)null));
                  BomboConfigGUI.selectedCategory = 25;
                  BomboConfigGUI.autofillCustomSlot(title, this.hoveredSlot.index);
               } else {
                  String cmd = CustomSlotManager.getCommand(this.hoveredSlot);
                  if (cmd != null && !cmd.isEmpty() && Minecraft.getInstance().player != null) {
                     Minecraft.getInstance().player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                  }
               }

               cir.setReturnValue(true);
               return;
            }
         }

         if (BomboConfig.get().hoppityWarp && this.hoveredSlot != null && this.hoveredSlot.hasItem() && event.button() == 0) {
            String screenTitle = net.minecraft.ChatFormatting.stripFormatting(this.getTitle().getString());
            if (screenTitle != null && screenTitle.toLowerCase(java.util.Locale.ROOT).contains("hoppity's collection")) {
               ItemStack stack = this.hoveredSlot.getItem();
               String targetWarp = null;
               List<String> tooltipLines = new ArrayList<>();
               for (Component c : stack.getTooltipLines(TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, Default.NORMAL)) {
                  tooltipLines.add(net.minecraft.ChatFormatting.stripFormatting(c.getString()).toLowerCase(java.util.Locale.ROOT));
               }

               // Only warp for lines that explicitly declare "current hotspot:" or "(spot) resident" / "resident"
               for (String line : tooltipLines) {
                  if (line.contains("current hotspot:") || line.contains("hotspot:")) {
                     targetWarp = resolveRabbitWarp(line);
                     if (targetWarp != null) break;
                  }
               }

               if (targetWarp == null) {
                  for (String line : tooltipLines) {
                     if (line.contains("resident")) {
                        targetWarp = resolveRabbitWarp(line);
                        if (targetWarp != null) break;
                     }
                  }
               }

               if (targetWarp != null) {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null) {
                     mc.player.connection.sendCommand("warp " + targetWarp);
                     mc.player.closeContainer();
                     cir.setReturnValue(true);
                     return;
                  }
               }
            }
         }

         if (ComposterHelper.onMouseClicked((AbstractContainerScreen)(Object)this, this.hoveredSlot, event.button())) {
            cir.setReturnValue(true);
         } else if (this.hoveredSlot == null || !SlotHighlight.isTargetSlot(this.hoveredSlot.index) && this.hoveredSlot.index != 45 && this.hoveredSlot.index != 53) {
            SlotHighlight.clearTargetSlot();
         }
      }
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (me.bombo.bomboaddons.gui.StorageOverlayManager.keyPressed(event)) {
         cir.setReturnValue(true);
         return;
      }

      int focusKey = ClickLogic.getKeyCode(BomboConfig.get().itemListFocusKey);
      if (focusKey != -1 && event.key() == focusKey) {
         if (ItemListOverlay.searchBox != null) {
            boolean focused = !ItemListOverlay.searchBox.isFocused();
            ItemListOverlay.searchBox.setFocused(focused);
            if (focused) {
               ((AbstractContainerScreen<?>)(Object)this).setFocused(ItemListOverlay.searchBox);
               ItemListOverlay.isHiddenState = false;
               ItemListOverlay.searchBox.setVisible(true);
            }
            cir.setReturnValue(true);
            return;
         }
      }

       int k = event.key();
       if ((k == 258 || k == 262 || k == 263 || k == 264 || k == 265) && focusKey != k) {
          if (ItemListOverlay.searchBox != null && !ItemListOverlay.searchBox.isFocused()) {
             cir.setReturnValue(true);
             return;
          }
       }

      if (BomboConfig.get().preventSlotSwapOnGuiKeybind) {
         if (CustomBindsProcessor.checkGuiKeybinds(event.key())) {
            if (BomboConfig.get().debugKeys) {
               Bomboaddons.sendMessage("§e[KeyDebug] AbstractContainerScreen.keyPressed consumed by GUI keybind: " + event.key());
            }
            cir.setReturnValue(true);
            return;
         }
      }

      BomboConfig.Settings s = BomboConfig.get();
      boolean isGardenMoving = s != null && s.gardenBlockSlotsWhileFarming && me.bombo.bomboaddons.GardenMovement.isActive() && me.bombo.bomboaddons.SkyblockUtils.isInGarden();
      if (isGardenMoving && this.hoveredSlot != null) {
         Minecraft mc = Minecraft.getInstance();
         boolean isDrop = mc.options.keyDrop.matches(event);
         boolean isHotbarKey = false;
         for (net.minecraft.client.KeyMapping km : mc.options.keyHotbarSlots) {
            if (km.matches(event)) {
               isHotbarKey = true;
               break;
            }
         }
         if (isDrop || isHotbarKey) {
            cir.setReturnValue(true);
            return;
         }
      }

      if (ItemListOverlay.searchBox != null && ItemListOverlay.searchBox.isFocused()) {
         if (event.key() == 257 || event.key() == 335) {
            String val = ItemListOverlay.searchBox.getValue();
            SkyblockCalculator.EvaluationResult res = SkyblockCalculator.evaluate(val);
            if (res.error == null) {
               String resStr = SkyblockCalculator.formatCompactNumber(res.value);
               ItemListOverlay.searchBox.setValue(resStr);
               Minecraft.getInstance().keyboardHandler.setClipboard(resStr);
            }
         }

         if (ItemListOverlay.searchBox.keyPressed(event)) {
            cir.setReturnValue(true);
         } else if (event.key() == 256) {
            ItemListOverlay.searchBox.setFocused(false);
            cir.setReturnValue(true);
         } else {
            cir.setReturnValue(true);
         }

      } else {
         int prefillBoundKey = ClickLogic.getKeyCode(BomboConfig.get().customSlotPrefillKey);
         if (this.hoveredSlot != null && event.key() == prefillBoundKey) {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen)(Object)this;
            String title = screen.getTitle().getString();
            Minecraft.getInstance().setScreenAndShow(new BomboConfigGUI((Screen)null));
            BomboConfigGUI.selectedCategory = 25;
            BomboConfigGUI.autofillCustomSlot(title, this.hoveredSlot.index);
            cir.setReturnValue(true);
         }

      }
   }

   @Inject(
      method = {"onClose"},
      at = {@At("HEAD")}
   )
   private void onGuiClose(CallbackInfo ci) {
      AutoCroesus.onScreenClosed();
      if (System.currentTimeMillis() - SlotHighlight.highlightStartTime > 500L) {
         SlotHighlight.clearTargetSlot();
      }

   }

   @org.spongepowered.asm.mixin.Unique
   private Slot lastStoragePreviewSlot = null;

   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")}
   )
   private void onExtractRenderStateHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (BomboConfig.get().storagePreview && me.bombo.bomboaddons.StoragePreviewManager.isPreviewActive(this.hoveredSlot)) {
         this.lastStoragePreviewSlot = this.hoveredSlot;
         this.hoveredSlot = null;
      } else {
         this.lastStoragePreviewSlot = null;
      }
   }

   @org.spongepowered.asm.mixin.Unique
   private long lastCroesusTickTime = 0L;
   @org.spongepowered.asm.mixin.Unique
   private long lastStoragePreviewTickTime = 0L;
   @org.spongepowered.asm.mixin.Unique
   private long lastEquipmentCheckTime = 0L;

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      long now = System.currentTimeMillis();
      me.bombo.bomboaddons.features.ItemValueBreakdownHud.currentHoveredSlot = this.hoveredSlot;
      me.bombo.bomboaddons.features.AutoExpCapsule.renderHexOverlay(graphics, (AbstractContainerScreen<?>)(Object)this);
      if (now - lastEquipmentCheckTime >= 200L) {
         lastEquipmentCheckTime = now;
         try {
            String title = this.getTitle() != null ? net.minecraft.ChatFormatting.stripFormatting(this.getTitle().getString()).toLowerCase(java.util.Locale.ROOT) : "";
            net.minecraft.world.inventory.AbstractContainerMenu menu = ((AbstractContainerScreen<?>)(Object)this).getMenu();
            if (menu != null) {
               if (title.contains("equipment sets")) {
               // In Equipment Sets GUI, row 4 has indicator slots (slots 36..44 representing Slot 1 .. Slot 9)
               int equippedCol = -1;
               for (int col = 0; col < 9; col++) {
                  int indicatorSlot = 36 + col;
                  if (indicatorSlot < menu.slots.size()) {
                     Slot s = menu.getSlot(indicatorSlot);
                     if (s != null && s.hasItem()) {
                        ItemStack stack = s.getItem();
                        String name = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()).toLowerCase(java.util.Locale.ROOT);
                        boolean isEquipped = name.contains("equipped") && !name.contains("click to equip");
                        if (!isEquipped) {
                           List<Component> lore = me.bombo.bomboaddons.SkyblockUtils.getLore(stack);
                           for (Component line : lore) {
                              String lStr = net.minecraft.ChatFormatting.stripFormatting(line.getString()).toLowerCase(java.util.Locale.ROOT);
                              if (lStr.contains("currently equipped") || lStr.contains("current set")) {
                                 isEquipped = true;
                                 break;
                              }
                           }
                        }
                        if (isEquipped) {
                           equippedCol = col;
                           break;
                        }
                     }
                  }
               }
               if (equippedCol != -1) {
                  for (int row = 0; row < 4; row++) {
                     int slotIdx = row * 9 + equippedCol;
                     if (slotIdx < menu.slots.size()) {
                        Slot s = menu.getSlot(slotIdx);
                        if (s != null && s.hasItem()) {
                           ItemStack item = s.getItem();
                           String hoverName = net.minecraft.ChatFormatting.stripFormatting(item.getHoverName().getString()).toLowerCase(java.util.Locale.ROOT);
                           boolean isPlaceholder = hoverName.contains("empty equipment slot") || hoverName.contains("empty slot")
                                 || hoverName.startsWith("slot 1 ") || hoverName.startsWith("slot 2 ") || hoverName.startsWith("slot 3 ") || hoverName.startsWith("slot 4 ")
                                 || hoverName.equals("necklace") || hoverName.equals("cloak") || hoverName.equals("belt") || hoverName.equals("gloves") || hoverName.equals("gauntlet") || hoverName.equals("bracelet")
                                 || hoverName.contains("empty") || item.is(net.minecraft.world.item.Items.AIR) || item.getItem().getDescriptionId().contains("glass_pane");
                           if (!isPlaceholder) {
                              List<Component> lore = me.bombo.bomboaddons.SkyblockUtils.getLore(item);
                              for (Component l : lore) {
                                 String lStr = net.minecraft.ChatFormatting.stripFormatting(l.getString()).toLowerCase(java.util.Locale.ROOT);
                                 if (lStr.contains("to add it to this set") || lStr.contains("place a necklace") || lStr.contains("place a cloak") || lStr.contains("place a belt") || lStr.contains("place gloves") || lStr.contains("place a gauntlet")) {
                                    isPlaceholder = true;
                                    break;
                                 }
                              }
                           }
                           if (isPlaceholder) {
                              me.bombo.bomboaddons.features.hud.EquipmentHud.updateEquipmentSlot(row, ItemStack.EMPTY);
                           } else {
                              me.bombo.bomboaddons.features.hud.EquipmentHud.updateEquipmentSlot(row, item);
                           }
                        } else {
                           me.bombo.bomboaddons.features.hud.EquipmentHud.updateEquipmentSlot(row, ItemStack.EMPTY);
                        }
                     }
                  }
               } else {
                  // No set currently equipped
                  for (int row = 0; row < 4; row++) {
                     me.bombo.bomboaddons.features.hud.EquipmentHud.updateEquipmentSlot(row, ItemStack.EMPTY);
                  }
               }
            } else if (title.contains("stats & equipment") || title.contains("your equipment") || title.contains("equipment")) {
               if (menu.slots.size() > 37) {
                  int[] eqSlots = new int[]{10, 19, 28, 37};
                  for (int i = 0; i < 4; i++) {
                     Slot s = menu.getSlot(eqSlots[i]);
                     if (s != null && s.hasItem()) {
                        ItemStack item = s.getItem();
                        String hoverName = net.minecraft.ChatFormatting.stripFormatting(item.getHoverName().getString()).toLowerCase(java.util.Locale.ROOT);
                        boolean isPlaceholder = hoverName.contains("empty equipment slot") || hoverName.contains("empty slot")
                               || hoverName.startsWith("slot 1 ") || hoverName.startsWith("slot 2 ") || hoverName.startsWith("slot 3 ") || hoverName.startsWith("slot 4 ")
                               || hoverName.equals("necklace") || hoverName.equals("cloak") || hoverName.equals("belt") || hoverName.equals("gloves") || hoverName.equals("gauntlet") || hoverName.equals("bracelet")
                               || hoverName.contains("empty") || item.is(net.minecraft.world.item.Items.AIR) || item.getItem().getDescriptionId().contains("glass_pane");
                        if (!isPlaceholder) {
                           List<Component> lore = me.bombo.bomboaddons.SkyblockUtils.getLore(item);
                           for (Component l : lore) {
                              String lStr = net.minecraft.ChatFormatting.stripFormatting(l.getString()).toLowerCase(java.util.Locale.ROOT);
                              if (lStr.contains("to add it to this set") || lStr.contains("place a necklace") || lStr.contains("place a cloak") || lStr.contains("place a belt") || lStr.contains("place gloves") || lStr.contains("place a gauntlet")) {
                                 isPlaceholder = true;
                                 break;
                              }
                           }
                        }
                        if (isPlaceholder) {
                           me.bombo.bomboaddons.features.hud.EquipmentHud.updateEquipmentSlot(i, ItemStack.EMPTY);
                        } else {
                           me.bombo.bomboaddons.features.hud.EquipmentHud.updateEquipmentSlot(i, item);
                        }
                     } else {
                        me.bombo.bomboaddons.features.hud.EquipmentHud.updateEquipmentSlot(i, ItemStack.EMPTY);
                     }
                  }
               }
            }
            }
         } catch (Throwable ignored) {}
      }
      if (BomboConfig.get().storageOverlay && me.bombo.bomboaddons.gui.StorageOverlayManager.isOverlayOpen) {
         graphics.fill(0, 0, this.width, this.height, 0xD0101218);
      } else {
         if (!(((Object)this) instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
            ItemListOverlay.render(graphics, Minecraft.getInstance().font, mouseX, mouseY);
         }

         if (StopwatchManager.isActive()) {
            StopwatchManager.drawStopwatch(graphics, 10, 200);
         }
      }

      now = System.currentTimeMillis();
      if (now - lastCroesusTickTime >= 100L) {
         lastCroesusTickTime = now;
         AutoCroesus.onContainerTick((AbstractContainerScreen)(Object)this);
         AutoCroesus.onCheckGuiTick((AbstractContainerScreen)(Object)this);
      }
      AutoCroesusHud.renderInContainer(graphics);

      if (BomboConfig.get().storageOverlay) {
         me.bombo.bomboaddons.gui.StorageOverlayManager.renderOverlay(graphics, Minecraft.getInstance().font, mouseX, mouseY, this.width, this.height);
      }

      if (BomboConfig.get().storagePreview) {
         if (now - lastStoragePreviewTickTime >= 100L) {
            lastStoragePreviewTickTime = now;
            me.bombo.bomboaddons.StoragePreviewManager.onContainerTick((AbstractContainerScreen)(Object)this);
         }
         Slot previewSlot = this.lastStoragePreviewSlot != null ? this.lastStoragePreviewSlot : this.hoveredSlot;
         me.bombo.bomboaddons.StoragePreviewManager.renderHoverPreview(graphics, previewSlot, mouseX, mouseY);
      }

      me.bombo.bomboaddons.features.ItemValueBreakdownHud.renderDirect(graphics);
      me.bombo.bomboaddons.features.hud.EquipmentHud.renderHoverTooltipDirect(graphics);
      me.bombo.bomboaddons.features.hud.ArmorHud.renderHoverTooltipDirect(graphics);
      me.bombo.bomboaddons.features.hud.InventoryHud.renderHoverTooltipDirect(graphics);
      if (!(((Object)this) instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
         me.bombo.bomboaddons.features.swapper.InventorySlotSwapManager.renderSlotOverlays(graphics, (AbstractContainerScreen<?>)(Object)this, this.leftPos, this.topPos, Minecraft.getInstance().font, mouseX, mouseY);
         me.bombo.bomboaddons.features.buttons.InventoryButtonManager.renderButtons(graphics, (AbstractContainerScreen<?>)(Object)this, mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
      BomboConfig.Settings s = BomboConfig.get();
      boolean isGardenMoving = s != null && s.gardenBlockSlotsWhileFarming && me.bombo.bomboaddons.GardenMovement.isActive() && me.bombo.bomboaddons.SkyblockUtils.isInGarden();
      if (isGardenMoving) {
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"mouseScrolled"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseScrolled(double mouseX, double mouseY, double amountX, double amountY, CallbackInfoReturnable<Boolean> cir) {
      if (ItemListOverlay.mouseScrolled(mouseX, mouseY, amountY)) {
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"checkHotbarKeyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCheckHotbarKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().preventSlotSwapOnGuiKeybind) {
         if (CustomBindsProcessor.hasHandledGuiKeyRecently(event.key()) || CustomBindsProcessor.matchesAnyRegisteredGuiBind(event.key())) {
            if (BomboConfig.get().debugKeys) {
               Bomboaddons.sendMessage("§e[KeyDebug] Blocked checkHotbarKeyPressed for key: " + event.key() + " (matching bind active)");
            }
            cir.setReturnValue(true);
         }
      }
   }

   private static String resolveRabbitWarp(String line) {
      if (line == null || line.isEmpty()) return null;
      String l = line.toLowerCase(java.util.Locale.ROOT);
      if (l.contains("spider")) {
         return "spider";
      } else if (l.contains("crimson") || l.contains("isle") || l.contains("kuudra") || l.contains("dragontail") || l.contains("scarleton") || l.contains("smoldering") || l.contains("wasteland") || l.contains("ashfang") || l.contains("volcano") || l.contains("bastion")) {
         return "crimson";
      } else if (l.contains("moonglade")) {
         return "moonglade";
      } else if (l.contains("lotus") || l.contains("atoll") || l.contains("croak")) {
         return "lotus";
      } else if (l.contains("the park") || l.contains("park") || l.contains("birch") || l.contains("spruce") || l.contains("dark thicket") || l.contains("savanna woodland") || l.contains("jungle") || l.contains("howling cave") || l.contains("melancholy")) {
         return "park";
      } else if (l.contains("farming island") || l.contains("the barn") || l.contains("barn") || l.contains("windmill") || l.contains("mushroom gorge") || l.contains("desert settlement") || l.contains("desert") || l.contains("oasis") || l.contains("trapper")) {
         return "farming";
      } else if (l.contains("the end") || l.contains("end resident") || l.contains("dragon's nest") || l.contains("dragons nest") || l.contains("void sepulture") || l.contains("zealot") || l.endsWith("end")) {
         return "end";
      } else if (l.contains("garden")) {
         return "garden";
      } else if (l.contains("gold mine") || l.contains("gold")) {
         return "gold";
      } else if (l.contains("deep caverns") || l.contains("deep") || l.contains("gunpowder") || l.contains("lapis quarry") || l.contains("pigman") || l.contains("slimehill") || l.contains("diamond reserve") || l.contains("obsidian sanctuary")) {
         return "deep";
      } else if (l.contains("dragon's lair") || l.contains("goblin holdout") || l.contains("precursor") || l.contains("crystal hollows") || l.contains("mineshaft") || l.contains("glacite") || l.contains("crystal")) {
         return "ch";
      } else if (l.contains("dwarven") || l.contains("royal mines") || l.contains("cliffside") || l.contains("rampart") || l.contains("the mist") || l.contains("lava springs") || l.contains("mithril deposits")) {
         return "mines";
      } else if (l.contains("rift") || l.contains("wyld woods") || l.contains("dreadfarm") || l.contains("black lagoon") || l.contains("west village") || l.contains("plaza") || l.contains("wizard")) {
         return "wizard";
      } else if (l.contains("dungeon hub") || l.contains("dungeon_hub")) {
         return "dungeon_hub";
      } else if (l.contains("jerry") || l.contains("winter island") || l.contains("winter")) {
         return "jerry";
      } else if (l.contains("hub") || l.contains("village") || l.contains("farm") || l.contains("forest") || l.contains("mountain") || l.contains("ruins") || l.contains("graveyard") || l.contains("coal mine") || l.contains("wilderness") || l.contains("colosseum") || l.contains("highland") || l.contains("castle") || l.contains("canvas")) {
         return "hub";
      }
      return null;
   }
}
