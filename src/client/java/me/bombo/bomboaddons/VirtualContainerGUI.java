package me.bombo.bomboaddons;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class VirtualContainerGUI extends Screen {
   private static final Identifier CHEST_GUI_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
   private final List<ItemStack> items;
   private final String guiTitle;
   private final int highlightSlot;
   private final String username;
   private final String currentPath;
   private final int xSize = 176;
   private final int ySize = 222;

   public VirtualContainerGUI(String title, List<ItemStack> items, int highlightSlot, String username, String path) {
      super(Component.literal(title));
      this.items = items;
      this.guiTitle = title;
      this.highlightSlot = highlightSlot;
      this.username = username;
      this.currentPath = path;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      super.extractRenderState(graphics, mouseX, mouseY, delta);
      int x = (this.width - 176) / 2;
      int y = (this.height - 222) / 2;
      graphics.blit(CHEST_GUI_TEXTURE, x, y, x + 176, y + 222, 0.0F, 0.6875F, 0.0F, 0.8671875F);
      graphics.text(this.font, this.guiTitle, x + 8, y + 6, 4210752, false);
      if (System.currentTimeMillis() % 2000L < 50L) {
         String var10000 = this.guiTitle;
         LF.logDebug("GUI Rendering... Title: " + var10000 + " Items: " + this.items.size());
      }

      int startX = x + 8;
      int startY = y + 18;
      HoveredTooltip hovered = null;

      for(int i = 0; i < 54 && i < this.items.size(); ++i) {
         int row = i / 9;
         int col = i % 9;
         int slotX = startX + col * 18;
         int slotY = startY + row * 18;
         ItemStack stack = (ItemStack)this.items.get(i);
         if (i == this.highlightSlot) {
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, -2130706688);
         }

         if (stack != null && !stack.isEmpty()) {
            graphics.item(stack, slotX, slotY);
            graphics.itemDecorations(this.font, stack, slotX, slotY);
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
               graphics.fill(slotX, slotY, slotX + 16, slotY + 16, -2130706433);
               List<Component> tooltipLines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
               List<String> lines = new ArrayList();

               for(Component line : tooltipLines) {
                  lines.add(line.getString());
               }

               if (!lines.isEmpty()) {
                  hovered = new HoveredTooltip((String)lines.get(0), mouseX, mouseY, (String[])lines.subList(1, lines.size()).toArray(new String[0]));
               }
            }
         }
      }

      int buttonY = y - 20;
      this.drawButton(graphics, Items.BEACON.getDefaultInstance(), x, buttonY, "§eFirst Page", "§7Jump to the first storage slot.", mouseX, mouseY);
      if (this.isMouseOver(mouseX, mouseY, x, buttonY)) {
         hovered = this.getButtonTooltip("§eFirst Page", "§7Jump to the first storage slot.", mouseX, mouseY);
      }

      int buttonX = x + 20;
      this.drawButton(graphics, Items.ARROW.getDefaultInstance(), buttonX, buttonY, "§ePrevious", "§7View the previous slot.", mouseX, mouseY);
      if (this.isMouseOver(mouseX, mouseY, buttonX, buttonY)) {
         hovered = this.getButtonTooltip("§ePrevious", "§7View the previous slot.", mouseX, mouseY);
      }

      buttonX += 20;
      this.drawButton(graphics, Items.ARROW.getDefaultInstance(), buttonX, buttonY, "§eNext", "§7View the next slot.", mouseX, mouseY);
      if (this.isMouseOver(mouseX, mouseY, buttonX, buttonY)) {
         hovered = this.getButtonTooltip("§eNext", "§7View the next slot.", mouseX, mouseY);
      }

      buttonX += 20;
      this.drawButton(graphics, Items.NETHER_STAR.getDefaultInstance(), buttonX, buttonY, "§eLast Page", "§7Jump to the last storage slot.", mouseX, mouseY);
      if (this.isMouseOver(mouseX, mouseY, buttonX, buttonY)) {
         hovered = this.getButtonTooltip("§eLast Page", "§7Jump to the last storage slot.", mouseX, mouseY);
      }

      buttonX += 30;
      this.drawButton(graphics, Items.CHEST.getDefaultInstance(), buttonX, buttonY, "§6Back to Storage", "§7View all backpacks and ender chests.", mouseX, mouseY);
      if (this.isMouseOver(mouseX, mouseY, buttonX, buttonY)) {
         hovered = this.getButtonTooltip("§6Back to Storage", "§7View all backpacks and ender chests.", mouseX, mouseY);
      }

      buttonX = x + 176 - 18;
      this.drawButton(graphics, Items.BARRIER.getDefaultInstance(), buttonX, buttonY, "§cClose", "§7Exit the virtual container.", mouseX, mouseY);
      if (this.isMouseOver(mouseX, mouseY, buttonX, buttonY)) {
         hovered = this.getButtonTooltip("§cClose", "§7Exit the virtual container.", mouseX, mouseY);
      }

      if (hovered != null) {
         this.drawCustomTooltip(graphics, hovered, mouseX, mouseY);
      }

   }

   private void drawButton(GuiGraphicsExtractor graphics, ItemStack icon, int x, int y, String title, String desc, int mx, int my) {
      graphics.fill(x - 2, y - 2, x + 18, y + 18, Integer.MIN_VALUE);
      if (this.isMouseOver(mx, my, x, y)) {
         graphics.fill(x - 2, y - 2, x + 18, y + 18, -2130706433);
      }

      graphics.item(icon, x, y);
   }

   private boolean isMouseOver(int mx, int my, int x, int y) {
      return mx >= x && mx < x + 16 && my >= y && my < y + 16;
   }

   private HoveredTooltip getButtonTooltip(String title, String desc, int mx, int my) {
      return new HoveredTooltip(title, mx, my, new String[]{desc});
   }

   protected void init() {
      super.init();
      int x = (this.width - 176) / 2;
      int y = (this.height - 222) / 2;
      int buttonY = y - 20;
      this.addRenderableWidget(Button.builder(Component.empty(), (btn) -> this.navigateAbsolute(0)).bounds(x, buttonY, 18, 18).build());
      this.addRenderableWidget(Button.builder(Component.empty(), (btn) -> this.navigateRelative(-1)).bounds(x + 20, buttonY, 18, 18).build());
      this.addRenderableWidget(Button.builder(Component.empty(), (btn) -> this.navigateRelative(1)).bounds(x + 40, buttonY, 18, 18).build());
      this.addRenderableWidget(Button.builder(Component.empty(), (btn) -> this.navigateAbsolute(17)).bounds(x + 60, buttonY, 18, 18).build());
      this.addRenderableWidget(Button.builder(Component.empty(), (btn) -> this.backToStorage()).bounds(x + 85, buttonY, 18, 18).build());
      this.addRenderableWidget(Button.builder(Component.empty(), (btn) -> this.onClose()).bounds(x + 176 - 20, buttonY, 18, 18).build());
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      PrintStream var10000 = System.out;
      double var10001 = event.x();
      var10000.println("[Bombo] MOUSE CLICK: " + var10001 + "," + event.y() + " button=" + event.button());
      return super.mouseClicked(event, handled);
   }

   private void navigateRelative(int direction) {
      String[] parts = this.currentPath.split(" > ");
      if (parts.length > 0) {
         String lastPart = parts[parts.length - 1];
         int current = 0;
         boolean hasIndex = false;

         try {
            current = Integer.parseInt(lastPart);
            hasIndex = true;
         } catch (NumberFormatException var10) {
         }

         int next = Math.max(0, current + direction);
         StringBuilder nextPath = new StringBuilder();
         int limit = hasIndex ? parts.length - 1 : parts.length;

         for(int i = 0; i < limit; ++i) {
            if (i > 0) {
               nextPath.append(" > ");
            }

            nextPath.append(parts[i]);
         }

         nextPath.append(" > ").append(next);
         LF.openVirtualContainer(this.username, nextPath.toString(), -1);
      }

   }

   private void navigateAbsolute(int index) {
      String[] parts = this.currentPath.split(" > ");
      if (parts.length > 0) {
         String lastPart = parts[parts.length - 1];
         boolean hasIndex = false;

         try {
            Integer.parseInt(lastPart);
            hasIndex = true;
         } catch (NumberFormatException var8) {
         }

         StringBuilder nextPath = new StringBuilder();
         int limit = hasIndex ? parts.length - 1 : parts.length;

         for(int i = 0; i < limit; ++i) {
            if (i > 0) {
               nextPath.append(" > ");
            }

            nextPath.append(parts[i]);
         }

         nextPath.append(" > ").append(index);
         LF.openVirtualContainer(this.username, nextPath.toString(), -1);
      }

   }

   private void backToStorage() {
      try {
         String path = this.currentPath;
         String[] parts = path.split(" > ");
         if (parts.length >= 2) {
            StringBuilder nextPath = new StringBuilder();
            int targetParts = parts.length;
            if (!path.contains("backpack_contents") && !path.contains("ender_chest_contents")) {
               targetParts = parts.length - 1;
            } else {
               targetParts = parts.length - 2;
            }

            for(int i = 0; i < Math.max(1, targetParts); ++i) {
               if (i > 0) {
                  nextPath.append(" > ");
               }

               nextPath.append(parts[i]);
            }

            LF.openVirtualContainer(this.username, nextPath.toString(), -1);
         }
      } catch (Exception var6) {
      }

   }

   private void drawCustomTooltip(GuiGraphicsExtractor graphics, HoveredTooltip tooltip, int mouseX, int mouseY) {
      int maxWidth = this.font.width(tooltip.title);

      for(String line : tooltip.lore) {
         maxWidth = Math.max(maxWidth, this.font.width(line));
      }

      int tX = mouseX + 12;
      int tY = mouseY - 12;
      int width = maxWidth + 8;
      int height = (tooltip.lore.length + 1) * 10 + 4;
      if (tX + width > this.width) {
         tX = mouseX - width - 8;
      }

      if (tY + height > this.height) {
         tY = this.height - height - 8;
      }

      graphics.fill(tX - 2, tY - 2, tX + width + 2, tY + height + 2, -822083584);
      graphics.text(this.font, tooltip.title, tX, tY, -1, true);

      for(int i = 0; i < tooltip.lore.length; ++i) {
         graphics.text(this.font, tooltip.lore[i], tX, tY + (i + 1) * 10, -1, true);
      }

   }

   private static class HoveredTooltip {
      final String title;
      final int x;
      final int y;
      final String[] lore;

      HoveredTooltip(String title, int x, int y, String... lore) {
         this.title = title;
         this.x = x;
         this.y = y;
         this.lore = lore;
      }
   }
}
