package me.bombo.bomboaddons.features.hider;

import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EntityBlockHiderScreen extends Screen {
   private final Screen parent;
   private int activeTab = 0; // 0 = Entities, 1 = Blocks
   private double scrollAmount = 0.0;

   // Entity fields
   private EditBox entityMatcherBox;
   private EditBox entityIslandBox;
   private EditBox entitySubareaBox;
   private int selectedEntityIndex = -1;

   // Block fields
   private EditBox blockFromBox;
   private EditBox blockToBox;
   private EditBox blockIslandBox;
   private EditBox blockSubareaBox;
   private Checkbox preservePropsCheckbox;
   private boolean preservePropsState = true;
   private int selectedBlockIndex = -1;

   public EntityBlockHiderScreen(Screen parent) {
      super(Component.literal("Entity & Block Hider"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      this.clearWidgets();
      int winW = Math.min(740, this.width - 20);
      int winH = Math.min(460, this.height - 20);
      int winX = (this.width - winW) / 2;
      int winY = (this.height - winH) / 2;

      // Master Toggle Button
      boolean enabled = BomboConfig.get().hiderEnabled;
      this.addRenderableWidget(Button.builder(Component.literal(enabled ? "§aHider: ON" : "§cHider: OFF"), (btn) -> {
         BomboConfig.get().hiderEnabled = !BomboConfig.get().hiderEnabled;
         BomboConfig.save();
         this.init();
      }).bounds(winX + winW - 110, winY + 8, 95, 20).build());

      // Tabs
      this.addRenderableWidget(Button.builder(Component.literal(this.activeTab == 0 ? "§6§l[Entities]" : "§7Entities"), (btn) -> {
         this.activeTab = 0;
         this.scrollAmount = 0.0;
         this.init();
      }).bounds(winX + 16, winY + 38, 120, 20).build());

      this.addRenderableWidget(Button.builder(Component.literal(this.activeTab == 1 ? "§6§l[Blocks]" : "§7Blocks"), (btn) -> {
         this.activeTab = 1;
         this.scrollAmount = 0.0;
         this.init();
      }).bounds(winX + 142, winY + 38, 120, 20).build());

      int formY = winY + 66;

      if (this.activeTab == 0) {
         // Entities Inputs
         this.entityMatcherBox = new EditBox(this.font, winX + 16, formY + 12, 220, 18, Component.literal("Texture Hash / Name / Type"));
         this.entityMatcherBox.setMaxLength(256);
         this.addRenderableWidget(this.entityMatcherBox);

         this.entityIslandBox = new EditBox(this.font, winX + 244, formY + 12, 120, 18, Component.literal("Island (e.g. Garden)"));
         this.entityIslandBox.setMaxLength(128);
         this.addRenderableWidget(this.entityIslandBox);

         this.entitySubareaBox = new EditBox(this.font, winX + 372, formY + 12, 120, 18, Component.literal("Subarea (e.g. Plot - 16)"));
         this.entitySubareaBox.setMaxLength(128);
         this.addRenderableWidget(this.entitySubareaBox);

         this.addRenderableWidget(Button.builder(Component.literal(this.selectedEntityIndex >= 0 ? "§aUpdate" : "§a+ Add"), (btn) -> {
            String matcher = this.entityMatcherBox.getValue().trim();
            if (!matcher.isEmpty()) {
               String island = this.entityIslandBox.getValue().trim();
               String sub = this.entitySubareaBox.getValue().trim();
               if (this.selectedEntityIndex >= 0 && this.selectedEntityIndex < BomboConfig.get().hiddenEntities.size()) {
                  BomboConfig.EntityHideRule rule = BomboConfig.get().hiddenEntities.get(this.selectedEntityIndex);
                  rule.matcher = matcher;
                  rule.island = island;
                  rule.subarea = sub;
               } else {
                  BomboConfig.get().hiddenEntities.add(new BomboConfig.EntityHideRule(matcher, island, sub));
               }
               BomboConfig.save();
               this.selectedEntityIndex = -1;
               this.init();
            }
         }).bounds(winX + 500, formY + 11, 75, 20).build());

         // Preset add for user's requested hash
         this.addRenderableWidget(Button.builder(Component.literal("§ePreset Hash"), (btn) -> {
            this.entityMatcherBox.setValue("c526a56b80f56a6870f891d1d46fa7f8c71494cad24e94326da84b3829417b81");
            this.entityIslandBox.setValue("Garden");
         }).bounds(winX + 582, formY + 11, 110, 20).build());

      } else {
         // Blocks Inputs
         this.blockFromBox = new EditBox(this.font, winX + 16, formY + 12, 110, 18, Component.literal("From Block"));
         this.blockFromBox.setMaxLength(128);
         this.addRenderableWidget(this.blockFromBox);

         this.blockToBox = new EditBox(this.font, winX + 132, formY + 12, 110, 18, Component.literal("To Block"));
         this.blockToBox.setMaxLength(128);
         this.addRenderableWidget(this.blockToBox);

         this.blockIslandBox = new EditBox(this.font, winX + 248, formY + 12, 110, 18, Component.literal("Island"));
         this.blockIslandBox.setMaxLength(128);
         this.addRenderableWidget(this.blockIslandBox);

         this.blockSubareaBox = new EditBox(this.font, winX + 364, formY + 12, 110, 18, Component.literal("Subarea"));
         this.blockSubareaBox.setMaxLength(128);
         this.addRenderableWidget(this.blockSubareaBox);

         this.preservePropsCheckbox = Checkbox.builder(Component.literal("Keep Props"), this.font)
            .pos(winX + 480, formY + 12)
            .selected(this.preservePropsState)
            .onValueChange((cb, val) -> this.preservePropsState = val)
            .build();
         this.addRenderableWidget(this.preservePropsCheckbox);

         this.addRenderableWidget(Button.builder(Component.literal(this.selectedBlockIndex >= 0 ? "§aUpdate" : "§a+ Add"), (btn) -> {
            String from = this.blockFromBox.getValue().trim();
            String to = this.blockToBox.getValue().trim();
            if (!from.isEmpty()) {
               String island = this.blockIslandBox.getValue().trim();
               String sub = this.blockSubareaBox.getValue().trim();
               boolean keepProps = this.preservePropsCheckbox.selected();
               if (this.selectedBlockIndex >= 0 && this.selectedBlockIndex < BomboConfig.get().blockReplacements.size()) {
                  BomboConfig.BlockReplaceRule rule = BomboConfig.get().blockReplacements.get(this.selectedBlockIndex);
                  rule.fromBlock = from;
                  rule.toBlock = to;
                  rule.island = island;
                  rule.subarea = sub;
                  rule.preserveProperties = keepProps;
               } else {
                  BomboConfig.get().blockReplacements.add(new BomboConfig.BlockReplaceRule(from, to, island, sub, keepProps));
               }
               BomboConfig.save();
               EntityBlockHider.rebuildCache();
               // Force chunk re-render so replacement takes effect immediately
               if (Minecraft.getInstance().levelRenderer != null) {
                  Minecraft.getInstance().levelRenderer.clearVisibleSections();
               }
               this.selectedBlockIndex = -1;
               this.init();
            }
         }).bounds(winX + 575, formY + 11, 65, 20).build());

         // Preset Sugar cane -> Glass pane
         this.addRenderableWidget(Button.builder(Component.literal("§eCane->Pane"), (btn) -> {
            this.blockFromBox.setValue("sugar_cane");
            this.blockToBox.setValue("glass_pane");
            this.blockIslandBox.setValue("Garden");
         }).bounds(winX + 645, formY + 11, 80, 20).build());
      }

      // Back Button
      this.addRenderableWidget(Button.builder(Component.literal("Back"), (btn) -> {
         if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
         }
      }).bounds(winX + winW / 2 - 50, winY + winH - 28, 100, 20).build());
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.scrollAmount = Math.max(0.0, this.scrollAmount - verticalAmount * 20.0);
      return true;
   }

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
      int winW = Math.min(740, this.width - 20);
      int winH = Math.min(460, this.height - 20);
      int winX = (this.width - winW) / 2;
      int winY = (this.height - winH) / 2;

      int listX = winX + 16;
      int listY = winY + 115;
      int listW = winW - 32;
      int listH = winH - 150;

      double mouseX = event.x();
      double mouseY = event.y();

      if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
         int rowH = 26;
         int clickedIndex = (int) ((mouseY - listY + this.scrollAmount) / rowH);

         if (this.activeTab == 0) {
            List<BomboConfig.EntityHideRule> rules = BomboConfig.get().hiddenEntities;
            if (clickedIndex >= 0 && clickedIndex < rules.size()) {
               BomboConfig.EntityHideRule rule = rules.get(clickedIndex);
               // Check toggle button / delete button bounds
               int itemY = listY + clickedIndex * rowH - (int) this.scrollAmount;
               if (mouseX >= listX + listW - 40 && mouseX <= listX + listW - 10 && mouseY >= itemY + 3 && mouseY <= itemY + 21) {
                  rules.remove(clickedIndex);
                  BomboConfig.save();
                  this.init();
                  return true;
               } else if (mouseX >= listX + listW - 85 && mouseX <= listX + listW - 45 && mouseY >= itemY + 3 && mouseY <= itemY + 21) {
                  rule.enabled = !rule.enabled;
                  BomboConfig.save();
                  this.init();
                  return true;
               } else {
                  this.selectedEntityIndex = clickedIndex;
                  if (this.entityMatcherBox != null) this.entityMatcherBox.setValue(rule.matcher);
                  if (this.entityIslandBox != null) this.entityIslandBox.setValue(rule.island);
                  if (this.entitySubareaBox != null) this.entitySubareaBox.setValue(rule.subarea);
                  return true;
               }
            }
         } else {
            List<BomboConfig.BlockReplaceRule> rules = BomboConfig.get().blockReplacements;
            if (clickedIndex >= 0 && clickedIndex < rules.size()) {
               BomboConfig.BlockReplaceRule rule = rules.get(clickedIndex);
               int itemY = listY + clickedIndex * rowH - (int) this.scrollAmount;
               if (mouseX >= listX + listW - 40 && mouseX <= listX + listW - 10 && mouseY >= itemY + 3 && mouseY <= itemY + 21) {
                  rules.remove(clickedIndex);
                  BomboConfig.save();
                  EntityBlockHider.rebuildCache();
                  if (Minecraft.getInstance().levelRenderer != null) Minecraft.getInstance().levelRenderer.clearVisibleSections();
                  this.init();
                  return true;
               } else if (mouseX >= listX + listW - 85 && mouseX <= listX + listW - 45 && mouseY >= itemY + 3 && mouseY <= itemY + 21) {
                  rule.enabled = !rule.enabled;
                  BomboConfig.save();
                  EntityBlockHider.rebuildCache();
                  if (Minecraft.getInstance().levelRenderer != null) Minecraft.getInstance().levelRenderer.clearVisibleSections();
                  this.init();
                  return true;
               } else {
                  this.selectedBlockIndex = clickedIndex;
                  this.preservePropsState = rule.preserveProperties;
                  this.init();
                  if (this.blockFromBox != null) this.blockFromBox.setValue(rule.fromBlock);
                  if (this.blockToBox != null) this.blockToBox.setValue(rule.toBlock);
                  if (this.blockIslandBox != null) this.blockIslandBox.setValue(rule.island);
                  if (this.blockSubareaBox != null) this.blockSubareaBox.setValue(rule.subarea);
                  return true;
               }
            }
         }
      }

      return super.mouseClicked(event, handled);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
      g.fill(0, 0, this.width, this.height, 0xDD0A0C10);

      int winW = Math.min(740, this.width - 20);
      int winH = Math.min(460, this.height - 20);
      int winX = (this.width - winW) / 2;
      int winY = (this.height - winH) / 2;

      g.fill(winX, winY, winX + winW, winY + winH, ConfigUITheme.getMainWindowBg());
      g.outline(winX, winY, winW, winH, ConfigUITheme.getBorderColor());

      // Header
      g.fill(winX, winY, winX + winW, winY + 32, ConfigUITheme.getSidebarBg());
      g.text(this.font, "§6§lEntity & Block Hider §8(Anti-Lag & Custom Render)", winX + 16, winY + 11, 0xFFFFFFFF, true);

      // Form Header Labels
      int formY = winY + 66;
      if (this.activeTab == 0) {
         g.text(this.font, "§7Matcher (Hash / Name / ID):", winX + 16, formY + 2, 0xFFAAAAAA, false);
         g.text(this.font, "§7Island:", winX + 244, formY + 2, 0xFFAAAAAA, false);
         g.text(this.font, "§7Subarea:", winX + 372, formY + 2, 0xFFAAAAAA, false);
      } else {
         g.text(this.font, "§7From Block:", winX + 16, formY + 2, 0xFFAAAAAA, false);
         g.text(this.font, "§7To Block:", winX + 132, formY + 2, 0xFFAAAAAA, false);
         g.text(this.font, "§7Island:", winX + 248, formY + 2, 0xFFAAAAAA, false);
         g.text(this.font, "§7Subarea:", winX + 364, formY + 2, 0xFFAAAAAA, false);
      }

      super.extractRenderState(g, mouseX, mouseY, delta);

      // Rules List Area
      int listX = winX + 16;
      int listY = winY + 115;
      int listW = winW - 32;
      int listH = winH - 150;

      g.fill(listX, listY, listX + listW, listY + listH, 0x33000000);
      g.outline(listX, listY, listW, listH, 0x444B5563);

      g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
      int rowH = 26;

      if (this.activeTab == 0) {
         List<BomboConfig.EntityHideRule> rules = BomboConfig.get().hiddenEntities;
         int startY = listY + 3 - (int) this.scrollAmount;
         for (int i = 0; i < rules.size(); i++) {
            BomboConfig.EntityHideRule rule = rules.get(i);
            int itemY = startY + i * rowH;
            if (itemY + rowH >= listY && itemY <= listY + listH) {
               boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= itemY && mouseY < itemY + rowH;
               g.fill(listX + 2, itemY, listX + listW - 2, itemY + rowH - 2, hovered ? 0x44374151 : 0x221F2937);

               String label = (rule.enabled ? "§a✔ " : "§c✖ ") + "§e" + rule.matcher;
               if (rule.island != null && !rule.island.isEmpty()) label += " §7[Island: §b" + rule.island + "§7]";
               if (rule.subarea != null && !rule.subarea.isEmpty()) label += " §7[Subarea: §d" + rule.subarea + "§7]";
               g.text(this.font, label, listX + 8, itemY + 7, 0xFFFFFFFF, false);

               // Toggle & Delete button badges
               g.fill(listX + listW - 85, itemY + 3, listX + listW - 45, itemY + 21, rule.enabled ? 0x88059669 : 0x88DC2626);
               g.text(this.font, rule.enabled ? "§aON" : "§cOFF", listX + listW - 74, itemY + 7, 0xFFFFFFFF, false);

               g.fill(listX + listW - 40, itemY + 3, listX + listW - 10, itemY + 21, 0x88991B1B);
               g.text(this.font, "§cDEL", listX + listW - 34, itemY + 7, 0xFFFFFFFF, false);
            }
         }
      } else {
         List<BomboConfig.BlockReplaceRule> rules = BomboConfig.get().blockReplacements;
         int startY = listY + 3 - (int) this.scrollAmount;
         for (int i = 0; i < rules.size(); i++) {
            BomboConfig.BlockReplaceRule rule = rules.get(i);
            int itemY = startY + i * rowH;
            if (itemY + rowH >= listY && itemY <= listY + listH) {
               boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= itemY && mouseY < itemY + rowH;
               g.fill(listX + 2, itemY, listX + listW - 2, itemY + rowH - 2, hovered ? 0x44374151 : 0x221F2937);

               String label = (rule.enabled ? "§a✔ " : "§c✖ ") + "§e" + rule.fromBlock + " §7➡ §a" + (rule.toBlock.isEmpty() ? "AIR" : rule.toBlock);
               if (rule.preserveProperties) label += " §8(KeepProps)";
               if (rule.island != null && !rule.island.isEmpty()) label += " §7[Island: §b" + rule.island + "§7]";
               if (rule.subarea != null && !rule.subarea.isEmpty()) label += " §7[Subarea: §d" + rule.subarea + "§7]";
               g.text(this.font, label, listX + 8, itemY + 7, 0xFFFFFFFF, false);

               // Toggle & Delete button badges
               g.fill(listX + listW - 85, itemY + 3, listX + listW - 45, itemY + 21, rule.enabled ? 0x88059669 : 0x88DC2626);
               g.text(this.font, rule.enabled ? "§aON" : "§cOFF", listX + listW - 74, itemY + 7, 0xFFFFFFFF, false);

               g.fill(listX + listW - 40, itemY + 3, listX + listW - 10, itemY + 21, 0x88991B1B);
               g.text(this.font, "§cDEL", listX + listW - 34, itemY + 7, 0xFFFFFFFF, false);
            }
         }
      }

      g.disableScissor();
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
