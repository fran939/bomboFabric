package me.bombo.bomboaddons;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ChatModifierScreen extends Screen {
   private final Screen parent;
   private EditBox patternBox;
   private EditBox replacementBox;
   private Checkbox hideCheckbox;
   private Checkbox regexCheckbox;
   private int selectedIndex = -1;
   private double scrollAmount = 0.0;

   private String initialPattern = "";
   private String initialReplacement = "";
   private boolean hideState = false;
   private boolean regexState = false;

   public ChatModifierScreen(Screen parent) {
      super(Component.literal("Chat Modifier Settings"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      this.clearWidgets();

      int leftX = 20;
      int topY = 40;

      // Rule Inputs
      this.patternBox = new EditBox(this.font, leftX, topY + 15, 180, 18, Component.literal("Pattern"));
      this.patternBox.setMaxLength(256);
      this.patternBox.setValue(this.initialPattern);
      this.addRenderableWidget(this.patternBox);

      this.replacementBox = new EditBox(this.font, leftX + 190, topY + 15, 180, 18, Component.literal("Replacement"));
      this.replacementBox.setMaxLength(256);
      this.replacementBox.setValue(this.initialReplacement);
      this.addRenderableWidget(this.replacementBox);

      this.hideCheckbox = Checkbox.builder(Component.literal("Hide Message"), this.font)
            .pos(leftX + 380, topY + 14)
            .selected(this.hideState)
            .build();
      this.addRenderableWidget(this.hideCheckbox);

      this.regexCheckbox = Checkbox.builder(Component.literal("Regex"), this.font)
            .pos(leftX + 480, topY + 14)
            .selected(this.regexState)
            .build();
      this.addRenderableWidget(this.regexCheckbox);

      // Add / Update Button
      this.addRenderableWidget(Button.builder(Component.literal(this.selectedIndex >= 0 ? "Save Rule" : "Add Rule"), (btn) -> {
         String pat = this.patternBox.getValue().trim();
         if (!pat.isEmpty()) {
            String rep = this.replacementBox.getValue();
            boolean hide = this.hideCheckbox.selected();
            boolean regex = this.regexCheckbox.selected();

            if (this.selectedIndex >= 0 && this.selectedIndex < ChatModifier.rules.size()) {
               ChatModifier.ChatRule rule = ChatModifier.rules.get(this.selectedIndex);
               rule.pattern = pat;
               rule.replacement = rep;
               rule.hideMessage = hide;
               rule.isRegex = regex;
            } else {
               ChatModifier.rules.add(new ChatModifier.ChatRule(pat, rep, hide, regex, true));
            }
            ChatModifier.save();
            this.clearInputs();
         }
      }).bounds(leftX + 550, topY + 13, 110, 20).build());

      // Back Button
      this.addRenderableWidget(Button.builder(Component.literal("Back"), (btn) -> {
         if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
         }
      }).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
   }

   private void clearInputs() {
      this.selectedIndex = -1;
      this.initialPattern = "";
      this.initialReplacement = "";
      this.hideState = false;
      this.regexState = false;
      this.init();
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
      g.fill(0, 0, this.width, this.height, 0xD0101015);

      g.text(this.font, "§b§lChat Modifier Rules", 20, 15, -1, true);
      g.text(this.font, "§7Match Pattern / Text:", 20, 42, 0xFFAAAAAA, false);
      g.text(this.font, "§7Replacement (Color codes supported with &):", 210, 42, 0xFFAAAAAA, false);

      int listTop = 85;
      int listBottom = this.height - 35;
      g.fill(15, listTop - 5, this.width - 15, listBottom, 0x50000000);
      g.outline(15, listTop - 5, this.width - 30, listBottom - listTop + 5, 0xFF444444);

      int y = listTop - (int) this.scrollAmount;
      List<ChatModifier.ChatRule> rules = ChatModifier.rules;

      if (rules.isEmpty()) {
         g.text(this.font, "§7No chat modification rules created yet. Enter a pattern above and click Add Rule.", 25, listTop + 10, 0xFF888888, false);
      }

      for (int i = 0; i < rules.size(); i++) {
         ChatModifier.ChatRule rule = rules.get(i);
         if (y + 24 >= listTop && y <= listBottom - 10) {
            boolean hovered = mouseX >= 20 && mouseX <= this.width - 20 && mouseY >= y && mouseY <= y + 22;
            int bg = (i == this.selectedIndex) ? 0x6000AAFF : (hovered ? 0x40FFFFFF : 0x20000000);
            g.fill(20, y, this.width - 20, y + 22, bg);
            g.outline(20, y, this.width - 40, 22, (i == this.selectedIndex) ? 0xFF00AAFF : 0xFF333333);

            int maxTextW = this.width - 150 - 30;
            String statusStr = rule.enabled ? "§a[ON]" : "§c[OFF]";
            String typeStr = rule.hideMessage ? "§c[HIDE]" : "§e[REPLACE]";
            String regexStr = rule.isRegex ? " §b(Regex)" : (rule.pattern != null && rule.pattern.contains("${") ? " §d(Template)" : "");

            String fullDisplay;
            if (rule.hideMessage) {
               fullDisplay = statusStr + " " + typeStr + regexStr + " §f\"§e" + rule.pattern + "§f\"";
            } else {
               String repl = rule.replacement != null ? rule.replacement.replace('&', '§') : "";
               fullDisplay = statusStr + " " + typeStr + regexStr + " §f\"§e" + rule.pattern + "§f\" §7-> §r\"§b" + repl + "§r\"";
            }

            if (this.font.width(fullDisplay) > maxTextW) {
               while (fullDisplay.length() > 4 && this.font.width(fullDisplay + "...") > maxTextW) {
                  fullDisplay = fullDisplay.substring(0, fullDisplay.length() - 1);
               }
               fullDisplay += "...";
            }

            g.text(this.font, fullDisplay, 26, y + 6, -1, false);

            // Buttons previewed on right
            int btnX = this.width - 140;
            g.fill(btnX, y + 2, btnX + 35, y + 20, 0xFF333344);
            g.text(this.font, "§eEdit", btnX + 8, y + 6, -1, false);

            g.fill(btnX + 40, y + 2, btnX + 75, y + 20, rule.enabled ? 0xFF225522 : 0xFF552222);
            g.text(this.font, rule.enabled ? "§aToggle" : "§cToggle", btnX + 44, y + 6, -1, false);

            g.fill(btnX + 80, y + 2, btnX + 115, y + 20, 0xFF552222);
            g.text(this.font, "§cDel", btnX + 90, y + 6, -1, false);
         }
         y += 26;
      }

      super.extractRenderState(g, mouseX, mouseY, delta);
   }

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
      double mouseX = event.x();
      double mouseY = event.y();
      int listTop = 85;
      int listBottom = this.height - 35;

      if (mouseX >= 20 && mouseX <= this.width - 20 && mouseY >= listTop && mouseY <= listBottom) {
         int y = listTop - (int) this.scrollAmount;
         for (int i = 0; i < ChatModifier.rules.size(); i++) {
            if (mouseY >= y && mouseY <= y + 22) {
               int btnX = this.width - 140;
               if (mouseX >= btnX && mouseX <= btnX + 35) { // Edit
                  this.selectedIndex = i;
                  ChatModifier.ChatRule rule = ChatModifier.rules.get(i);
                  this.initialPattern = rule.pattern;
                  this.initialReplacement = rule.replacement != null ? rule.replacement : "";
                  this.hideState = rule.hideMessage;
                  this.regexState = rule.isRegex;
                  this.init();
                  return true;
               } else if (mouseX >= btnX + 40 && mouseX <= btnX + 75) { // Toggle
                  ChatModifier.ChatRule rule = ChatModifier.rules.get(i);
                  rule.enabled = !rule.enabled;
                  ChatModifier.save();
                  return true;
               } else if (mouseX >= btnX + 80 && mouseX <= btnX + 115) { // Delete
                  ChatModifier.rules.remove(i);
                  if (this.selectedIndex == i) this.selectedIndex = -1;
                  ChatModifier.save();
                  return true;
               }
            }
            y += 26;
         }
      }

      return super.mouseClicked(event, handled);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      double maxScroll = Math.max(0, ChatModifier.rules.size() * 26 - (this.height - 120));
      this.scrollAmount = Math.max(0.0, Math.min(maxScroll, this.scrollAmount - vertical * 18.0));
      return true;
   }
}
