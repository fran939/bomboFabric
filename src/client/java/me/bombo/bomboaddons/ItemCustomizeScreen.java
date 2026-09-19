package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public class ItemCustomizeScreen extends Screen {
   private final Screen parent;
   private final ItemStack heldStack;
   private final String itemKey;
   private final String itemUuid;
   private final String originalDisplayName;
   private final String initialMaterial;
   private final String initialName;
   private final String initialLore;
   private final String initialArmorColor;
   private EditBox armorColorBox;
   private EditBox materialBox;
   private EditBox nameBox;
   private List<EditBox> loreLineBoxes = new ArrayList<>();
   private List<String> currentLoreLines = new ArrayList<>();
   private Boolean customEnchanted = null;
   private Button glintBtn;
   private double previewScrollY = 0.0;
   private double loreEditScrollY = 0.0;
   private boolean rawTextMode = false;
   private EditBox rawLoreBox;

   public static int parseColorHex(String hex) {
      if (hex == null || hex.trim().isEmpty()) return -1;
      String clean = hex.trim();
      if (clean.startsWith("#")) clean = clean.substring(1);
      try {
         return Integer.parseInt(clean, 16) & 0xFFFFFF;
      } catch (Exception e) {
         return switch (clean.toLowerCase(java.util.Locale.ROOT)) {
            case "cyan" -> 0x00E5FF;
            case "gold", "yellow" -> 0xFFAA00;
            case "emerald", "green" -> 0x10B981;
            case "purple" -> 0xA855F7;
            case "red" -> 0xEF4444;
            case "blue" -> 0x3B82F6;
            case "pink" -> 0xEC4899;
            case "white" -> 0xFFFFFF;
            case "aquamarine" -> 0x7FFFD4;
            case "ruby" -> 0xE0115F;
            case "amethyst" -> 0x9966CC;
            case "coral" -> 0xFF7F50;
            case "pure black", "black" -> 0x000000;
            default -> -1;
         };
      }
   }

   public ItemCustomizeScreen(Screen parent) {
      super(Component.literal("Customize Item"));
      this.parent = parent;
      Minecraft mc = Minecraft.getInstance();
      this.heldStack = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;
      String skyblockId = SkyblockUtils.getInternalIdRaw(this.heldStack);
      if (skyblockId != null && !skyblockId.isEmpty()) {
         this.itemKey = skyblockId;
      } else if (!this.heldStack.isEmpty()) {
         this.itemKey = BuiltInRegistries.ITEM.getKey(this.heldStack.getItem()).toString();
      } else {
         this.itemKey = "";
      }

      this.itemUuid = extractItemUuid(this.heldStack);
      this.originalDisplayName = this.heldStack.isEmpty() ? "None" : this.heldStack.getHoverName().getString();

      BomboConfig.CustomItemOverride tempOverride = null;
      if (this.itemUuid != null && BomboConfig.get().customItemOverrides.containsKey(this.itemUuid)) {
         tempOverride = BomboConfig.get().customItemOverrides.get(this.itemUuid);
      } else if (BomboConfig.get().customItemOverrides.containsKey(this.itemKey)) {
         tempOverride = BomboConfig.get().customItemOverrides.get(this.itemKey);
      }

      if (tempOverride != null) {
         this.initialMaterial = tempOverride.material != null ? tempOverride.material : "";
         this.initialName = tempOverride.name != null ? tempOverride.name : "";
         this.initialLore = tempOverride.lore != null ? tempOverride.lore : "";
         this.customEnchanted = tempOverride.enchanted;
         this.initialArmorColor = tempOverride.armorColor != null ? tempOverride.armorColor : "";
      } else if (!this.heldStack.isEmpty()) {
         this.initialMaterial = BuiltInRegistries.ITEM.getKey(this.heldStack.getItem()).getPath().toUpperCase();
         this.initialName = componentToAmpersandString(this.heldStack.getHoverName());
         this.initialLore = extractInitialLore(this.heldStack);
         this.customEnchanted = null;
         net.minecraft.world.item.component.DyedItemColor dyedColor = this.heldStack.get(DataComponents.DYED_COLOR);
         if (dyedColor != null) {
            this.initialArmorColor = String.format(java.util.Locale.ROOT, "#%06X", dyedColor.rgb());
         } else {
            this.initialArmorColor = "";
         }
      } else {
         this.initialMaterial = "";
         this.initialName = "";
         this.initialLore = "";
         this.customEnchanted = null;
         this.initialArmorColor = "";
      }

      if (this.initialLore != null && !this.initialLore.isEmpty()) {
         String[] split = this.initialLore.split("\\\\n|\\r?\\n");
         for (String s : split) {
            this.currentLoreLines.add(s);
         }
      }
      if (this.currentLoreLines.isEmpty()) {
         this.currentLoreLines.add("");
      }
   }

   public static String extractItemUuid(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return null;
      try {
         CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
         if (cd != null) {
            CompoundTag tag = cd.copyTag();
            if (tag != null) {
               if (tag.contains("ExtraAttributes")) {
                  CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                  if (ea != null && ea.contains("uuid")) {
                     String u = ea.getString("uuid").orElse(null);
                     if (u != null && !u.isEmpty()) return u;
                  }
               }
               if (tag.contains("uuid")) {
                  String u = tag.getString("uuid").orElse(null);
                  if (u != null && !u.isEmpty()) return u;
               }
            }
         }
      } catch (Throwable ignored) {}
      return null;
   }

   private static String componentToAmpersandString(Component comp) {
      if (comp == null) return "";
      StringBuilder sb = new StringBuilder();
      comp.visit((style, text) -> {
         if (text.isEmpty()) return java.util.Optional.empty();
         if (style.getColor() != null) {
            String colName = style.getColor().toString();
            net.minecraft.ChatFormatting cf = net.minecraft.ChatFormatting.valueOf(colName.toUpperCase(java.util.Locale.ROOT));
            if (cf != null) {
               sb.append('&').append(cf.toString().charAt(1));
            }
         }
         if (style.isBold()) sb.append("&l");
         if (style.isItalic()) sb.append("&o");
         if (style.isUnderlined()) sb.append("&n");
         if (style.isStrikethrough()) sb.append("&m");
         if (style.isObfuscated()) sb.append("&k");
         sb.append(text.replace('§', '&'));
         return java.util.Optional.empty();
      }, Style.EMPTY);
      return sb.toString();
   }

   private static String extractInitialLore(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return "";
      try {
         net.minecraft.world.item.component.ItemLore itemLore = stack.get(DataComponents.LORE);
         if (itemLore != null && !itemLore.lines().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < itemLore.lines().size(); i++) {
               if (i > 0) sb.append("\\n");
               sb.append(componentToAmpersandString(itemLore.lines().get(i)));
            }
            return sb.toString();
         }

         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            List<Component> tooltipLines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
            if (tooltipLines != null && tooltipLines.size() > 1) {
               StringBuilder sb = new StringBuilder();
               boolean first = true;
               for (int i = 1; i < tooltipLines.size(); i++) {
                  String str = componentToAmpersandString(tooltipLines.get(i));
                  String clean = net.minecraft.ChatFormatting.stripFormatting(tooltipLines.get(i).getString()).toLowerCase();
                  if (clean.contains("craft cost:") || clean.contains("estimated value:") || clean.contains("lowest bin:")) {
                     continue;
                  }
                  if (!first) sb.append("\\n");
                  sb.append(str);
                  first = false;
               }
               return sb.toString();
            }
         }
      } catch (Throwable ignored) {}
      return "";
   }

   private void syncCurrentLinesFromBoxes() {
      if (this.rawTextMode) {
         if (this.rawLoreBox != null) {
            this.currentLoreLines.clear();
            String[] parts = this.rawLoreBox.getValue().split("\\\\n|\\r?\\n");
            for (String p : parts) {
               this.currentLoreLines.add(p);
            }
         }
      }
   }

   private String getCompiledLore() {
      syncCurrentLinesFromBoxes();
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < this.currentLoreLines.size(); i++) {
         if (i > 0) sb.append("\\n");
         sb.append(this.currentLoreLines.get(i));
      }
      return sb.toString();
   }

   @Override
   protected void init() {
      syncCurrentLinesFromBoxes();
      this.clearWidgets();

      int winW = Math.min(680, this.width - 30);
      int winH = Math.min(420, this.height - 24);
      int winX = (this.width - winW) / 2;
      int winY = (this.height - winH) / 2;

      int leftW = winW / 2 - 10;
      int fieldX = winX + 16;
      int fieldW = leftW - 32;

      String currentMaterial = this.materialBox != null ? this.materialBox.getValue() : this.initialMaterial;
      String currentName = this.nameBox != null ? this.nameBox.getValue() : this.initialName;
      String currentArmorCol = this.armorColorBox != null ? this.armorColorBox.getValue() : this.initialArmorColor;

      // Material input
      this.materialBox = new EditBox(this.font, fieldX, winY + 54, fieldW - 24, 16, Component.literal("Material"));
      this.materialBox.setMaxLength(128);
      this.materialBox.setValue(currentMaterial);
      this.addRenderableWidget(this.materialBox);

      // Display Name input
      this.nameBox = new EditBox(this.font, fieldX, winY + 84, fieldW, 16, Component.literal("Display Name"));
      this.nameBox.setMaxLength(256);
      this.nameBox.setValue(currentName);
      this.addRenderableWidget(this.nameBox);

      // Quick palette buttons
      String[] colors = new String[]{"&c", "&6", "&e", "&a", "&b", "&d", "&f", "&l", "&o", "&r"};
      int palX = fieldX;
      int palY = winY + 104;
      for (String code : colors) {
         int finalX = palX;
         this.addRenderableWidget(Button.builder(Component.literal(code.replace('&', '§') + code), (btn) -> {
            EditBox target = getFocusedEditBox();
            if (target != null) {
               target.insertText(code);
            }
         }).bounds(finalX, palY, 26, 14).build());
         palX += 28;
      }

      // Armor Color Input (Hex / Dye)
      int armorY = winY + 122;
      this.armorColorBox = new EditBox(this.font, fieldX, armorY + 10, fieldW - 60, 16, Component.literal("Armor Color Hex"));
      this.armorColorBox.setMaxLength(16);
      this.armorColorBox.setValue(currentArmorCol);
      this.addRenderableWidget(this.armorColorBox);

      // Enchanted Glint Toggle Button
      String glintText = this.customEnchanted == null ? "§7Glint: Auto" : (this.customEnchanted ? "§aGlint: YES" : "§cGlint: NO");
      this.glintBtn = Button.builder(Component.literal(glintText), (btn) -> {
         if (this.customEnchanted == null) {
            this.customEnchanted = Boolean.TRUE;
         } else if (this.customEnchanted == Boolean.TRUE) {
            this.customEnchanted = Boolean.FALSE;
         } else {
            this.customEnchanted = null;
         }
         String newText = this.customEnchanted == null ? "§7Glint: Auto" : (this.customEnchanted ? "§aGlint: YES" : "§cGlint: NO");
         btn.setMessage(Component.literal(newText));
      }).bounds(fieldX + fieldW - 55, armorY + 10, 55, 16).build();
      this.addRenderableWidget(this.glintBtn);

      // Lore Editor Header Buttons (+ Add Line, Mode Toggle)
      int loreTopY = winY + 152;
      this.addRenderableWidget(Button.builder(Component.literal("§a+ Add Line"), (btn) -> {
         syncCurrentLinesFromBoxes();
         this.currentLoreLines.add("");
         this.init();
      }).bounds(fieldX, loreTopY, 70, 16).build());

      this.addRenderableWidget(Button.builder(Component.literal(this.rawTextMode ? "§eMode: Raw \\n" : "§bMode: Lines"), (btn) -> {
         syncCurrentLinesFromBoxes();
         this.rawTextMode = !this.rawTextMode;
         this.init();
      }).bounds(fieldX + 74, loreTopY, 80, 16).build());

      int loreAreaY = loreTopY + 20;
      int loreAreaH = winY + winH - 36 - loreAreaY;

      this.loreLineBoxes.clear();
      if (this.rawTextMode) {
         this.rawLoreBox = new EditBox(this.font, fieldX, loreAreaY, fieldW, loreAreaH, Component.literal("Raw Lore"));
         this.rawLoreBox.setMaxLength(32768);
         StringBuilder sb = new StringBuilder();
         for (int i = 0; i < this.currentLoreLines.size(); i++) {
            if (i > 0) sb.append("\\n");
            sb.append(this.currentLoreLines.get(i));
         }
         this.rawLoreBox.setValue(sb.toString());
         this.addRenderableWidget(this.rawLoreBox);
      } else {
         int rowH = 22;
         double maxLoreScroll = Math.max(0.0, this.currentLoreLines.size() * rowH - loreAreaH + 4);
         if (this.loreEditScrollY > maxLoreScroll) {
            this.loreEditScrollY = maxLoreScroll;
         }

         for (int i = 0; i < this.currentLoreLines.size(); i++) {
            int lineY = loreAreaY + i * rowH - (int) this.loreEditScrollY;
            String text = this.currentLoreLines.get(i);
            final int lineIdx = i;

            if (lineY >= loreAreaY - 4 && lineY + 18 <= loreAreaY + loreAreaH + 4) {
               EditBox lineBox = new EditBox(this.font, fieldX + 18, lineY, fieldW - 40, 18, Component.literal("Line " + (i + 1)));
               lineBox.setMaxLength(512);
               lineBox.setValue(text);
               lineBox.setResponder((val) -> {
                  if (lineIdx < this.currentLoreLines.size()) {
                     this.currentLoreLines.set(lineIdx, val);
                  }
               });
               this.loreLineBoxes.add(lineBox);
               this.addRenderableWidget(lineBox);

               this.addRenderableWidget(Button.builder(Component.literal("§c-"), (btn) -> {
                  if (this.currentLoreLines.size() > 1) {
                     this.currentLoreLines.remove(lineIdx);
                  } else {
                     this.currentLoreLines.set(0, "");
                  }
                  this.init();
               }).bounds(fieldX + fieldW - 20, lineY + 1, 18, 16).build());
            }
         }
      }

      // Action Buttons
      int btnW = 90;
      int btnH = 22;
      int btnY = winY + winH - 30;

      this.addRenderableWidget(Button.builder(Component.literal("§aSave & Sync"), (btn) -> {
         saveAndClose();
      }).bounds(fieldX, btnY, btnW + 10, btnH).build());

      this.addRenderableWidget(Button.builder(Component.literal("§cReset"), (btn) -> {
         if (this.itemUuid != null && !this.itemUuid.isEmpty()) {
            BomboConfig.get().customItemOverrides.remove(this.itemUuid);
         }
         BomboConfig.get().customItemOverrides.remove(this.itemKey);
         BomboConfig.save();
         if (Minecraft.getInstance().player != null) {
            Bomboaddons.sendMessage("§8[§bBomboAddons§8] §cReset custom item override for " + (this.itemUuid != null ? this.itemUuid : this.itemKey));
         }
         this.onClose();
      }).bounds(fieldX + btnW + 18, btnY, btnW, btnH).build());

      this.addRenderableWidget(Button.builder(Component.literal("Cancel"), (btn) -> this.onClose())
              .bounds(fieldX + btnW * 2 + 26, btnY, btnW, btnH).build());
   }

   private EditBox getFocusedEditBox() {
      if (this.nameBox != null && this.nameBox.isFocused()) return this.nameBox;
      if (this.materialBox != null && this.materialBox.isFocused()) return this.materialBox;
      if (this.armorColorBox != null && this.armorColorBox.isFocused()) return this.armorColorBox;
      if (this.rawTextMode && this.rawLoreBox != null && this.rawLoreBox.isFocused()) return this.rawLoreBox;
      for (EditBox eb : this.loreLineBoxes) {
         if (eb != null && eb.isFocused()) return eb;
      }
      return this.nameBox;
   }

   private void saveAndClose() {
      String mat = this.materialBox != null ? this.materialBox.getValue().trim().toUpperCase().replace(' ', '_') : "";
      String nm = this.nameBox != null ? this.nameBox.getValue().trim() : "";
      String lr = getCompiledLore().trim();
      String armorCol = this.armorColorBox != null ? this.armorColorBox.getValue().trim() : "";

      if (mat.isEmpty() && nm.isEmpty() && lr.isEmpty() && armorCol.isEmpty() && this.customEnchanted == null) {
         if (this.itemUuid != null && !this.itemUuid.isEmpty()) {
            BomboConfig.get().customItemOverrides.remove(this.itemUuid);
         } else {
            BomboConfig.get().customItemOverrides.remove(this.itemKey);
         }
      } else {
         BomboConfig.CustomItemOverride customOverride = new BomboConfig.CustomItemOverride(mat, nm, lr, this.customEnchanted, armorCol);
         if (this.itemUuid != null && !this.itemUuid.isEmpty()) {
            BomboConfig.get().customItemOverrides.put(this.itemUuid, customOverride);
            BomboConfig.get().customItemOverrides.remove(this.itemKey);
         } else {
            BomboConfig.get().customItemOverrides.put(this.itemKey, customOverride);
         }

         if (IRCClient.isConnected()) {
            try {
               String b64Lore = !lr.isEmpty() ? java.util.Base64.getEncoder().encodeToString(lr.getBytes(java.nio.charset.StandardCharsets.UTF_8)) : "";
               String uuidStr = this.itemUuid != null ? this.itemUuid : "";
               String payload = "[META_CUSTOM_ITEM]\u0002" + this.itemKey + "\u0002" + uuidStr + "\u0002" + mat + "\u0002" + nm + "\u0002" + b64Lore + "\u0002" + armorCol;
               IRCClient.sendRaw("NOTICE #bomboaddons_chat :" + payload);
            } catch (Throwable ignored) {}
         }
      }

      BomboConfig.save();
      if (Minecraft.getInstance().player != null) {
         String targetDesc = (this.itemUuid != null && !this.itemUuid.isEmpty()) ? ("item UUID " + this.itemUuid) : this.itemKey;
         Bomboaddons.sendMessage("§8[§bBomboAddons§8] §aSaved custom item override for §e" + targetDesc);
      }
      this.onClose();
   }

   private List<String> autocompleteSuggestions = new ArrayList<>();
   private int selectedSuggestionIndex = -1;

   private void updateAutocomplete() {
      if (this.materialBox == null || !this.materialBox.isFocused()) {
         autocompleteSuggestions.clear();
         selectedSuggestionIndex = -1;
         return;
      }
      String input = this.materialBox.getValue().trim().toUpperCase().replace("MINECRAFT:", "");
      if (input.isEmpty()) {
         autocompleteSuggestions.clear();
         selectedSuggestionIndex = -1;
         return;
      }
      List<String> matches = new ArrayList<>();
      for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
         String path = id.getPath().toUpperCase();
         if (path.startsWith(input) || path.contains(input)) {
            matches.add(path);
            if (matches.size() >= 8) break;
         }
      }
      this.autocompleteSuggestions = matches;
      if (selectedSuggestionIndex >= matches.size()) {
         selectedSuggestionIndex = matches.isEmpty() ? -1 : 0;
      }
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (this.materialBox != null && this.materialBox.isFocused() && !autocompleteSuggestions.isEmpty()) {
         if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            selectedSuggestionIndex = (selectedSuggestionIndex + 1) % autocompleteSuggestions.size();
            return true;
         } else if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            selectedSuggestionIndex = (selectedSuggestionIndex - 1 + autocompleteSuggestions.size()) % autocompleteSuggestions.size();
            return true;
         } else if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            if (selectedSuggestionIndex >= 0 && selectedSuggestionIndex < autocompleteSuggestions.size()) {
               this.materialBox.setValue(autocompleteSuggestions.get(selectedSuggestionIndex));
               autocompleteSuggestions.clear();
               selectedSuggestionIndex = -1;
               return true;
            } else if (!autocompleteSuggestions.isEmpty()) {
               this.materialBox.setValue(autocompleteSuggestions.get(0));
               autocompleteSuggestions.clear();
               selectedSuggestionIndex = -1;
               return true;
            }
         }
      }

      if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
         saveAndClose();
         return true;
      }

      return super.keyPressed(event);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int winW = Math.min(680, this.width - 30);
      int winH = Math.min(420, this.height - 24);
      int winX = (this.width - winW) / 2;
      int winY = (this.height - winH) / 2;

      int leftW = winW / 2 - 10;
      int fieldX = winX + 16;
      int fieldW = leftW - 32;

      int rightX = winX + winW / 2 + 10;
      int rightY = winY + 40;
      int rightW = winW / 2 - 26;
      int rightH = winH - 52;

      if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= rightY && mouseY <= rightY + rightH) {
         this.previewScrollY = Math.max(0.0, this.previewScrollY - verticalAmount * 16.0);
         return true;
      }

      int loreTopY = winY + 166;
      int loreAreaH = winY + winH - 36 - loreTopY;
      if (mouseX >= fieldX && mouseX <= fieldX + fieldW && mouseY >= loreTopY && mouseY <= loreTopY + loreAreaH) {
         this.loreEditScrollY = Math.max(0.0, this.loreEditScrollY - verticalAmount * 22.0);
         this.init();
         return true;
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
      if (!autocompleteSuggestions.isEmpty() && this.materialBox != null) {
         int winW = Math.min(680, this.width - 30);
         int winH = Math.min(420, this.height - 24);
         int winX = (this.width - winW) / 2;
         int winY = (this.height - winH) / 2;
         int leftW = winW / 2 - 10;
         int fieldX = winX + 16;
         int fieldW = leftW - 32;

         int dropX = fieldX;
         int dropY = winY + 72;
         int dropW = fieldW - 24;
         int rowH = 14;
         int totalH = autocompleteSuggestions.size() * rowH + 4;

         if (event.x() >= dropX && event.x() <= dropX + dropW && event.y() >= dropY && event.y() <= dropY + totalH) {
            int idx = (int) (event.y() - dropY - 2) / rowH;
            if (idx >= 0 && idx < autocompleteSuggestions.size()) {
               this.materialBox.setValue(autocompleteSuggestions.get(idx));
               autocompleteSuggestions.clear();
               selectedSuggestionIndex = -1;
               return true;
            }
         }
      }
      return super.mouseClicked(event, handled);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
      updateAutocomplete();

      // Backdrop
      g.fill(0, 0, this.width, this.height, 0xDD0A0C10);

      int winW = Math.min(680, this.width - 30);
      int winH = Math.min(420, this.height - 24);
      int winX = (this.width - winW) / 2;
      int winY = (this.height - winH) / 2;

      // Window base
      g.fill(winX, winY, winX + winW, winY + winH, ConfigUITheme.getMainWindowBg());
      g.outline(winX, winY, winW, winH, ConfigUITheme.getBorderColor());

      // Header bar
      int headerH = 34;
      g.fill(winX, winY, winX + winW, winY + headerH, ConfigUITheme.getSidebarBg());
      g.fill(winX, winY + headerH - 1, winX + winW, winY + headerH, ConfigUITheme.getBorderColor());

      Font font = this.font;
      g.text(font, "§6§lCustomize Item §8| §f" + this.originalDisplayName, winX + 16, winY + 11, 0xFFFFFFFF, true);

      // Left column labels
      int fieldX = winX + 16;
      int leftW = winW / 2 - 10;
      int fieldW = leftW - 32;

      String targetLabel = (this.itemUuid != null && !this.itemUuid.isEmpty())
              ? "§7Target: §bUUID (§e" + this.itemUuid + "§b)"
              : "§7Target: §e" + this.itemKey;
      g.text(font, targetLabel, fieldX, winY + 36, 0xFFAAAAAA, false);
      g.text(font, "§fMaterial ID:", fieldX, winY + 44, 0xFFFFFFFF, false);
      g.text(font, "§fDisplay Name:", fieldX, winY + 74, 0xFFFFFFFF, false);
      g.text(font, "§fArmor Color Hex:", fieldX, winY + 122, 0xFFFFFFFF, false);

      // Line numbers in non-raw mode
      if (!this.rawTextMode) {
         int loreTopY = winY + 172;
         int rowH = 22;
         int loreAreaH = winY + winH - 36 - loreTopY;
         for (int i = 0; i < this.currentLoreLines.size(); i++) {
            int lineY = loreTopY + i * rowH - (int) this.loreEditScrollY;
            if (lineY >= loreTopY - 4 && lineY + 18 <= loreTopY + loreAreaH + 4) {
               g.text(font, "§8" + (i + 1), fieldX + 2, lineY + 5, 0xFF888888, false);
            }
         }
      }

      // Material Icon Preview next to material box
      String currentMat = this.materialBox != null ? this.materialBox.getValue().trim() : this.initialMaterial;
      Item previewItem = Items.IRON_SWORD;
      if (!currentMat.isEmpty()) {
         String matName = currentMat.toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "");
         Item found = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", matName));
         if (found != null && found != Items.AIR) {
            previewItem = found;
         }
      }
      int iconX = fieldX + fieldW - 20;
      int iconY = winY + 53;
      g.fill(iconX - 2, iconY - 1, iconX + 18, iconY + 19, 0x33000000);
      g.outline(iconX - 2, iconY - 1, 20, 20, 0x44FFFFFF);
      g.item(new ItemStack(previewItem), iconX, iconY + 1);

      // Render standard widgets
      super.extractRenderState(g, mouseX, mouseY, delta);

      // Right column: Live Tooltip / Item Preview Card
      int rightX = winX + winW / 2 + 10;
      int rightY = winY + 40;
      int rightW = winW / 2 - 26;
      int rightH = winH - 52;

      g.fill(rightX, rightY, rightX + rightW, rightY + rightH, 0x22111827);
      g.outline(rightX, rightY, rightW, rightH, 0x444B5563);
      g.text(font, "§b§lLive Item Preview §7(Scrollable)", rightX + 10, rightY + 8, 0xFF00E5FF, true);

      // Live Tooltip Box inside Preview Area
      int ttX = rightX + 8;
      int ttY = rightY + 26;
      int ttW = rightW - 16;
      int ttH = rightH - 34;

      g.fill(ttX, ttY, ttX + ttW, ttY + ttH, 0xEE100010);
      g.outline(ttX, ttY, ttW, ttH, 0xAA5000FF);

      String rawName = this.nameBox != null ? this.nameBox.getValue().trim() : this.initialName;
      String formattedName = rawName.isEmpty() ? "§f" + this.originalDisplayName : rawName.replace('&', '§');

      String rawLore = getCompiledLore();
      List<String> previewLines = new ArrayList<>();
      if (!rawLore.isEmpty()) {
         String[] split = rawLore.split("\\\\n|\\r?\\n");
         for (String s : split) {
            previewLines.add(s.replace('&', '§'));
         }
      }

      int totalContentH = 18 + previewLines.size() * 11 + 10;
      double maxScroll = Math.max(0.0, totalContentH - ttH);
      if (this.previewScrollY > maxScroll) {
         this.previewScrollY = maxScroll;
      }

      g.enableScissor(ttX + 2, ttY + 2, ttX + ttW - 2, ttY + ttH - 2);
      int startLineY = ttY + 8 - (int) this.previewScrollY;

      // Item Title
      g.text(font, formattedName, ttX + 8, startLineY, 0xFFFFFFFF, true);
      startLineY += 14;

      // Lore Lines
      for (String l : previewLines) {
         if (startLineY + 10 >= ttY && startLineY <= ttY + ttH + 10) {
            g.text(font, l, ttX + 8, startLineY, 0xFFAAAAAA, false);
         }
         startLineY += 11;
      }
      g.disableScissor();

      // Autocomplete Suggestions Dropdown (rendered on top)
      if (!autocompleteSuggestions.isEmpty() && this.materialBox != null && this.materialBox.isFocused()) {
         int dropX = fieldX;
         int dropY = winY + 72;
         int dropW = fieldW - 24;
         int rowH = 14;
         int totalH = autocompleteSuggestions.size() * rowH + 4;

         g.fill(dropX, dropY, dropX + dropW, dropY + totalH, 0xFA151821);
         g.outline(dropX, dropY, dropW, totalH, 0xFF4F46E5);

         for (int i = 0; i < autocompleteSuggestions.size(); i++) {
            String sug = autocompleteSuggestions.get(i);
            int itemY = dropY + 2 + i * rowH;
            boolean isSel = (i == selectedSuggestionIndex);
            boolean isHover = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY < itemY + rowH;
            if (isSel || isHover) {
               g.fill(dropX + 2, itemY, dropX + dropW - 2, itemY + rowH, 0x446366F1);
            }
            g.text(font, (isSel ? "§e▶ " : "  §7") + sug, dropX + 4, itemY + 3, isSel ? 0xFFFFAA00 : 0xFFDDDDDD, false);
         }
      }
   }

   @Override
   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreenAndShow(this.parent);
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
