package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public class RecipeViewerScreen extends Screen {
   private final String itemId;
   private final Screen parentScreen;
   private final List<String> history = new ArrayList();
   private boolean loading = false;
   private String error = null;
   private List<JsonObject> recipesToDisplay = new ArrayList();
   private List<String> recipeOutputs = new ArrayList();
   private int currentRecipeIndex = 0;
   private ItemStack[] recipeGrid = new ItemStack[9];
   private ItemStack outputItem;
   private String recipeType;
   private String crafttext;
   private int outputCount;
   private boolean usageMode;
   private ItemStack hoveredStack;
   private String hoveredId;
   private Button nextBtn;
   private Button prevBtn;
   private Button viewRecipeBtn;

   public RecipeViewerScreen(String itemId, Screen parentScreen) {
      super(Component.literal("Recipe: " + itemId));
      this.outputItem = ItemStack.EMPTY;
      this.recipeType = "crafting";
      this.crafttext = "";
      this.outputCount = 1;
      this.usageMode = false;
      this.hoveredStack = null;
      this.hoveredId = null;
      this.itemId = itemId;
      this.parentScreen = parentScreen;
   }

   public RecipeViewerScreen(String itemId, Screen parentScreen, List<String> history) {
      super(Component.literal("Recipe: " + itemId));
      this.outputItem = ItemStack.EMPTY;
      this.recipeType = "crafting";
      this.crafttext = "";
      this.outputCount = 1;
      this.usageMode = false;
      this.hoveredStack = null;
      this.hoveredId = null;
      this.itemId = itemId;
      this.parentScreen = parentScreen;
      this.history.addAll(history);
   }

   public void setUsageMode(boolean mode) {
      this.usageMode = mode;
   }

   protected void init() {
      super.init();
      this.loadRecipes();
      int w = 260;
      int h = 190;
      int x = (this.width - w) / 2;
      int y = (this.height - h) / 2;
      this.addRenderableWidget(Button.builder(Component.literal("§cBack"), (btn) -> {
         if (!this.history.isEmpty()) {
            String prevId = (String)this.history.remove(this.history.size() - 1);
            RecipeViewerScreen s = new RecipeViewerScreen(prevId, this.parentScreen, this.history);
            Minecraft.getInstance().setScreenAndShow(s);
         } else {
            Minecraft.getInstance().setScreenAndShow(this.parentScreen);
         }

      }).bounds(x + 20, y + 155, 50, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("§c✕"), (btn) -> Minecraft.getInstance().setScreenAndShow(this.parentScreen)).bounds(x + w - 24, y + 8, 16, 16).build());
      this.prevBtn = (Button)this.addRenderableWidget(Button.builder(Component.literal("<"), (btn) -> {
         if (this.currentRecipeIndex > 0) {
            --this.currentRecipeIndex;
            this.applyCurrentRecipe();
         }

      }).bounds(x + 90, y + 155, 20, 20).build());
      this.nextBtn = (Button)this.addRenderableWidget(Button.builder(Component.literal(">"), (btn) -> {
         if (this.currentRecipeIndex < this.recipesToDisplay.size() - 1) {
            ++this.currentRecipeIndex;
            this.applyCurrentRecipe();
         }

      }).bounds(x + 150, y + 155, 20, 20).build());
      this.viewRecipeBtn = (Button)this.addRenderableWidget(Button.builder(Component.literal("/viewrecipe"), (btn) -> {
         if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.connection.sendCommand("viewrecipe " + this.itemId);
            Minecraft.getInstance().setScreenAndShow((Screen)null);
         }

      }).bounds(x + 175, y + 155, 75, 20).build());
      this.updateButtons();
   }

   private void loadRecipes() {
      this.recipesToDisplay.clear();
      this.recipeOutputs.clear();
      this.currentRecipeIndex = 0;
      SkyblockItemManager.ensureLoaded();
      if (!this.usageMode) {
         String npcId = SkyblockItemManager.getNpcSeller(this.itemId);
         if (npcId != null) {
            SkyblockItemManager.SkyblockItemInfo npcInfo = SkyblockItemManager.getNpc(npcId);
            if (npcInfo != null) {
               Minecraft.getInstance().setScreenAndShow(new NpcShopScreen(npcInfo, this.parentScreen));
               return;
            }
         }
      }

      if (this.usageMode) {
         for(SkyblockItemManager.SkyblockItemInfo info : SkyblockItemManager.getUsages(this.itemId)) {
            if (info.recipes != null) {
               for(JsonElement el : info.recipes) {
                  JsonObject r = el.getAsJsonObject();
                  boolean uses = false;

                  for(String key : new String[]{"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"}) {
                     if (r.has(key) && r.get(key).getAsString().startsWith(this.itemId)) {
                        uses = true;
                        break;
                     }
                  }

                  if (r.has("inputs") && r.get("inputs").isJsonArray()) {
                     for(JsonElement inEl : r.getAsJsonArray("inputs")) {
                        if (inEl.getAsString().startsWith(this.itemId)) {
                           uses = true;
                        }
                     }
                  }

                  if (uses) {
                     this.recipesToDisplay.add(r);
                     this.recipeOutputs.add(info.id);
                  }
               }
            }
         }

         if (this.recipesToDisplay.isEmpty()) {
            this.error = "No usages found for " + this.itemId;
         }
      } else {
         SkyblockItemManager.SkyblockItemInfo info = SkyblockItemManager.getInfo(this.itemId);
         if (info == null) {
            this.error = "Item not found in local DB.";
         } else if (info.recipes != null && !info.recipes.isEmpty()) {
            for(JsonElement el : info.recipes) {
               this.recipesToDisplay.add(el.getAsJsonObject());
               this.recipeOutputs.add(this.itemId);
            }
         } else {
            this.error = "No recipes found for " + this.itemId;
         }
      }

      if (!this.recipesToDisplay.isEmpty()) {
         this.applyCurrentRecipe();
      }

   }

   private void applyCurrentRecipe() {
      if (!this.recipesToDisplay.isEmpty()) {
         JsonObject recipeObj = (JsonObject)this.recipesToDisplay.get(this.currentRecipeIndex);
         String outputId = (String)this.recipeOutputs.get(this.currentRecipeIndex);
         if (recipeObj.has("type")) {
            this.recipeType = recipeObj.get("type").getAsString();
         } else {
            this.recipeType = "crafting";
         }

         this.outputCount = recipeObj.has("count") ? recipeObj.get("count").getAsInt() : 1;

         for(int i = 0; i < 9; ++i) {
            this.recipeGrid[i] = ItemStack.EMPTY;
         }

         if (recipeObj.has("A1")) {
            String[] keys = new String[]{"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

            for(int i = 0; i < 9; ++i) {
               if (recipeObj.has(keys[i])) {
                  String inputStr = recipeObj.get(keys[i]).getAsString();
                  if (!inputStr.isEmpty()) {
                     String[] parts = inputStr.split(":");
                     ItemStack stack = SkyblockItemManager.createSkyblockItem(parts[0]);
                     if (stack != null && !stack.isEmpty()) {
                        stack.setCount(parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                        this.recipeGrid[i] = stack;
                     }
                  }
               }
            }
         } else if (recipeObj.has("inputs") && recipeObj.get("inputs").isJsonArray()) {
            JsonArray inputs = recipeObj.getAsJsonArray("inputs");

            for(int i = 0; i < 9 && i < inputs.size(); ++i) {
               String inputStr = inputs.get(i).getAsString();
               if (!inputStr.isEmpty()) {
                  String[] parts = inputStr.split(":");
                  ItemStack stack = SkyblockItemManager.createSkyblockItem(parts[0]);
                  if (stack != null && !stack.isEmpty()) {
                     stack.setCount(parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                     this.recipeGrid[i] = stack;
                  }
               }
            }
         }

         this.outputItem = SkyblockItemManager.createSkyblockItem(outputId);
         if (this.outputItem != null && !this.outputItem.isEmpty()) {
            this.outputItem.setCount(this.outputCount);
         }

         this.updateButtons();
      }
   }

   private void updateButtons() {
      if (this.prevBtn != null) {
         this.prevBtn.active = this.currentRecipeIndex > 0;
      }

      if (this.nextBtn != null) {
         this.nextBtn.active = this.currentRecipeIndex < this.recipesToDisplay.size() - 1;
      }

      if (this.viewRecipeBtn != null) {
         this.viewRecipeBtn.visible = this.error == null && !this.recipesToDisplay.isEmpty();
      }

   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int w = 260;
      int h = 190;
      int x = (this.width - w) / 2;
      int y = (this.height - h) / 2;
      graphics.fill(0, 0, this.width, this.height, -804648438);
      graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, -14013910);
      graphics.fill(x, y, x + w, y + h, -267251182);
      if (this.error != null) {
         graphics.centeredText(this.font, "§c" + this.error, x + w / 2, y + 80, -1);
      } else if (!this.recipesToDisplay.isEmpty()) {
         String nameColor = "§f";
         SkyblockItemManager.SkyblockItemInfo itemInfo = SkyblockItemManager.getInfo(this.itemId);
         if (itemInfo != null) {
            nameColor = SkyblockItemManager.getTierColor(itemInfo.tier);
         }

         String titleStr = this.usageMode ? "Usages: " + nameColor + (itemInfo != null ? itemInfo.name : this.itemId) : "Recipe: " + nameColor + (this.outputItem != null ? this.outputItem.getHoverName().getString() : this.itemId);
         graphics.text(this.font, titleStr, x + 20, y + 15, -1, true);
         if (!"mob_drop".equalsIgnoreCase(this.recipeType)) {
            String var10000 = this.usageMode ? "Item Usages" : "Crafting Recipe";
            String subtitle = "§7" + var10000 + " (" + this.recipeType + ")";
            graphics.text(this.font, subtitle, x + 20, y + 27, -1, true);
         }

         if (this.recipesToDisplay.size() > 1) {
            int cx = x + 130;
            int cy = y + 161;
            graphics.centeredText(this.font, "§e" + (this.currentRecipeIndex + 1) + " / " + this.recipesToDisplay.size(), cx, cy, -1);
         }

         this.hoveredStack = null;
         this.hoveredId = null;
         if ("forge".equalsIgnoreCase(this.recipeType)) {
            int fw = 162;
            int fh = 108;
            int fx = x + (w - fw) / 2;
            int fy = y + 45;
            graphics.fill(fx - 4, fy - 4, fx + fw + 4, fy + fh + 4, -13750738);
            graphics.fill(fx - 2, fy - 2, fx + fw + 2, fy + fh + 2, -15461356);

            for(int r = 0; r < 6; ++r) {
               for(int c = 0; c < 9; ++c) {
                  int slotX = fx + c * 18;
                  int slotY = fy + r * 18;
                  graphics.fill(slotX, slotY, slotX + 16, slotY + 16, -13750738);
                  ItemStack stack = ItemStack.EMPTY;
                  if (r == 1 && c == 4) {
                     stack = SkyblockItemManager.createSkyblockItem("STONE_BUTTON");
                  }

                  if (r == 2 && c >= 3 && c <= 5) {
                     int idx = c - 3;
                     if (this.recipeGrid[idx] != null && !this.recipeGrid[idx].isEmpty()) {
                        stack = this.recipeGrid[idx];
                     }
                  }

                  if (r == 3 && c == 4) {
                     stack = this.outputItem;
                  }

                  if (stack != null && !stack.isEmpty()) {
                     graphics.item(stack, slotX, slotY);
                     graphics.itemDecorations(this.font, stack, slotX, slotY);
                     if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 1090519039);
                        this.hoveredStack = stack;
                        String id = "";

                        try {
                           CompoundTag tag = ((CustomData)stack.get(DataComponents.CUSTOM_DATA)).copyTag();
                           if (tag != null && tag.contains("ExtraAttributes")) {
                              CompoundTag ea = (CompoundTag)tag.get("ExtraAttributes");
                              id = ea.getString("id").orElse("");
                           }
                        } catch (Exception var41) {
                        }

                        if (!id.isEmpty() && !id.equals("STONE_BUTTON")) {
                           this.hoveredId = id;
                        }
                     }
                  }
               }
            }
         } else if ("mob_drop".equalsIgnoreCase(this.recipeType)) {
            JsonObject recipeObj = (JsonObject)this.recipesToDisplay.get(this.currentRecipeIndex);
            String mobId = recipeObj.has("mob_id") ? recipeObj.get("mob_id").getAsString() : "";
            String mobName = recipeObj.has("mob_name") ? recipeObj.get("mob_name").getAsString() : "Unknown Mob";
            int panelY = y + 27;
            int panelH = 122;
            graphics.fill(x + 10, panelY, x + w - 10, panelY + panelH, -15198184);
            graphics.fill(x + 10, panelY, x + w - 10, panelY + 1, -12961222);
            graphics.fill(x + 10, panelY + panelH - 1, x + w - 10, panelY + panelH, -12961222);
            graphics.fill(x + 10, panelY, x + 11, panelY + panelH, -12961222);
            graphics.fill(x + w - 11, panelY, x + w - 10, panelY + panelH, -12961222);
            int cx = x + w / 2;
            int cy = y + 60;
            graphics.centeredText(this.font, "§6" + mobName, cx, cy - 25, -1);
            ItemStack mobItem = SkyblockItemManager.createSkyblockItem(mobId);
            if (mobItem != null && !mobItem.isEmpty()) {
               graphics.pose().pushMatrix();
               graphics.pose().translate((float)(cx - 16), (float)cy - 5.0F);
               graphics.pose().scale(2.0F, 2.0F);
               graphics.item(mobItem, 0, 0);
               graphics.pose().popMatrix();
            }

            if (recipeObj.has("all_drops") && recipeObj.get("all_drops").isJsonArray()) {
               JsonArray drops = recipeObj.getAsJsonArray("all_drops");
               int validDropsCount = 0;

               for(int i = 0; i < drops.size(); ++i) {
                  if (drops.get(i).getAsJsonObject().has("id")) {
                     ++validDropsCount;
                  }
               }

               int maxCols = 9;
               int cols = Math.min(validDropsCount, maxCols);
               int gridW = cols * 24;
               int startX = cx - gridW / 2;
               int startY = cy + 35;
               int col = 0;
               int row = 0;

               for(int i = 0; i < drops.size(); ++i) {
                  JsonObject drop = drops.get(i).getAsJsonObject();
                  if (drop.has("id")) {
                     String dropId = drop.get("id").getAsString().split(":")[0];
                     ItemStack dropStack = SkyblockItemManager.createSkyblockItem(dropId);
                     if (dropStack != null && !dropStack.isEmpty()) {
                        ItemStack renderStack = dropStack.copy();
                        ItemLore loreComp = (ItemLore)renderStack.get(DataComponents.LORE);
                        List<Component> lines = new ArrayList();
                        if (loreComp != null) {
                           lines.addAll(loreComp.lines());
                        }

                        lines.add(Component.literal(""));
                        String var10001 = drop.has("chance") ? drop.get("chance").getAsString() : "Unknown";
                        lines.add(Component.literal("§eDrop Chance: §a" + var10001));
                        if (drop.has("extra") && drop.get("extra").isJsonArray()) {
                           for(JsonElement extraEl : drop.getAsJsonArray("extra")) {
                              lines.add(Component.literal(extraEl.getAsString()));
                           }
                        }

                        renderStack.set(DataComponents.LORE, new ItemLore(lines));
                        int slotX = startX + col * 24 + 4;
                        int slotY = startY + row * 24 + 4;
                        graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, -13750738);
                        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, -15461356);
                        graphics.item(renderStack, slotX, slotY);
                        graphics.itemDecorations(this.font, renderStack, slotX, slotY);
                        if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                           graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 1090519039);
                           this.hoveredStack = renderStack;
                           this.hoveredId = dropId;
                        }

                        ++col;
                        if (col >= maxCols) {
                           col = 0;
                           ++row;
                        }
                     }
                  }
               }
            }
         } else {
            int gridX = x + 30;
            int gridY = y + 45;
            graphics.fill(gridX - 4, gridY - 4, gridX + 72 + 1, gridY + 72 + 1, -2146957304);

            for(int i = 0; i < 9; ++i) {
               int col = i % 3;
               int row = i / 3;
               int slotX = gridX + col * 24;
               int slotY = gridY + row * 24;
               graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, -13750738);
               graphics.fill(slotX, slotY, slotX + 16, slotY + 16, -15461356);
               ItemStack stack = this.recipeGrid[i];
               if (stack != null && !stack.isEmpty()) {
                  graphics.item(stack, slotX, slotY);
                  graphics.itemDecorations(this.font, stack, slotX, slotY);
                  if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                     graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 1090519039);
                     this.hoveredStack = stack;
                     String id = "";

                     try {
                        CompoundTag tag = ((CustomData)stack.get(DataComponents.CUSTOM_DATA)).copyTag();
                        if (tag != null && tag.contains("ExtraAttributes")) {
                           CompoundTag ea = (CompoundTag)tag.get("ExtraAttributes");
                           id = ea.getString("id").orElse("");
                        }
                     } catch (Exception var40) {
                     }

                     if (!id.isEmpty()) {
                        this.hoveredId = id;
                     }
                  }
               }
            }

            graphics.text(this.font, "➡", gridX + 85, gridY + 30, -10496, true);
            int outX = gridX + 120;
            int outY = gridY + 24;
            graphics.fill(outX - 2, outY - 2, outX + 26, outY + 26, -13750738);
            graphics.fill(outX - 1, outY - 1, outX + 25, outY + 25, -15461356);
            if (this.outputItem != null && !this.outputItem.isEmpty()) {
               graphics.item(this.outputItem, outX + 4, outY + 4);
               graphics.itemDecorations(this.font, this.outputItem, outX + 4, outY + 4);
               if (mouseX >= outX && mouseX < outX + 24 && mouseY >= outY && mouseY < outY + 24) {
                  graphics.fill(outX, outY, outX + 24, outY + 24, 1090519039);
                  this.hoveredStack = this.outputItem;
                  String id = "";

                  try {
                     CompoundTag tag = ((CustomData)this.outputItem.get(DataComponents.CUSTOM_DATA)).copyTag();
                     if (tag != null && tag.contains("ExtraAttributes")) {
                        CompoundTag ea = (CompoundTag)tag.get("ExtraAttributes");
                        id = ea.getString("id").orElse("");
                     }
                  } catch (Exception var39) {
                  }

                  if (!id.isEmpty()) {
                     this.hoveredId = id;
                  }
               }
            }
         }

         super.extractRenderState(graphics, mouseX, mouseY, delta);
         if (this.hoveredStack != null) {
            try {
               graphics.setTooltipForNextFrame(this.font, this.hoveredStack, mouseX, mouseY);
            } catch (Throwable var38) {
            }
         }

      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      double mouseX = event.x();
      double mouseY = event.y();
      int button = event.button();
      int w = 240;
      int h = 190;
      int x = (this.width - w) / 2;
      int y = (this.height - h) / 2;
      int cx = x + 120;
      int cy = y + 161;
      if (this.recipesToDisplay.size() > 1 && mouseY >= (double)cy && mouseY <= (double)(cy + 10)) {
         if (mouseX >= (double)(cx - 40) && mouseX <= (double)(cx - 10)) {
            if (this.currentRecipeIndex > 0) {
               --this.currentRecipeIndex;
               this.applyCurrentRecipe();
            }

            return true;
         }

         if (mouseX >= (double)(cx + 25) && mouseX <= (double)(cx + 55)) {
            if (this.currentRecipeIndex < this.recipesToDisplay.size() - 1) {
               ++this.currentRecipeIndex;
               this.applyCurrentRecipe();
            }

            return true;
         }
      }

      if (this.hoveredId != null) {
         this.history.add(this.itemId);
         RecipeViewerScreen s = new RecipeViewerScreen(this.hoveredId, this.parentScreen, this.history);
         if (button == 1) {
            s.setUsageMode(true);
         }

         Minecraft.getInstance().setScreenAndShow(s);
         return true;
      } else {
         return super.mouseClicked(event, handled);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      if (vertical > (double)0.0F) {
         if (this.currentRecipeIndex > 0) {
            --this.currentRecipeIndex;
            this.applyCurrentRecipe();
         }
      } else if (vertical < (double)0.0F && this.currentRecipeIndex < this.recipesToDisplay.size() - 1) {
         ++this.currentRecipeIndex;
         this.applyCurrentRecipe();
      }

      return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
   }

   public boolean keyPressed(KeyEvent event) {
      int keyCode = event.key();
      if (keyCode != 262 && keyCode != 266 && keyCode != 326) {
         if (keyCode != 263 && keyCode != 267 && keyCode != 324) {
            if (keyCode == 259 && !this.history.isEmpty()) {
               String prevId = (String)this.history.remove(this.history.size() - 1);
               Minecraft.getInstance().setScreenAndShow(new RecipeViewerScreen(prevId, this.parentScreen, this.history));
               return true;
            } else {
               return super.keyPressed(event);
            }
         } else {
            if (this.currentRecipeIndex > 0) {
               --this.currentRecipeIndex;
               this.applyCurrentRecipe();
            }

            return true;
         }
      } else {
         if (this.currentRecipeIndex < this.recipesToDisplay.size() - 1) {
            ++this.currentRecipeIndex;
            this.applyCurrentRecipe();
         }

         return true;
      }
   }
}
