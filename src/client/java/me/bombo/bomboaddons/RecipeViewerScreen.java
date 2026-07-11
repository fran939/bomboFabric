package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;

public class RecipeViewerScreen extends Screen {
    private final String itemId;
    private final Screen parentScreen;
    private final List<String> history = new ArrayList<>();

    private boolean loading = false;
    private String error = null;

    private List<JsonObject> recipesToDisplay = new ArrayList<>();
    private List<String> recipeOutputs = new ArrayList<>();
    private int currentRecipeIndex = 0;

    private ItemStack[] recipeGrid = new ItemStack[9];
    private ItemStack outputItem = ItemStack.EMPTY;
    private String recipeType = "crafting";
    private String crafttext = "";
    private int outputCount = 1;
    private boolean usageMode = false;

    private ItemStack hoveredStack = null;
    private String hoveredId = null;

    private Button nextBtn;
    private Button prevBtn;
    private Button viewRecipeBtn;

    public RecipeViewerScreen(String itemId, Screen parentScreen) {
        super(Component.literal("Recipe: " + itemId));
        this.itemId = itemId;
        this.parentScreen = parentScreen;
    }

    public RecipeViewerScreen(String itemId, net.minecraft.client.gui.screens.Screen parentScreen, List<String> history) {
        super(Component.literal("Recipe: " + itemId));
        this.itemId = itemId;
        this.parentScreen = parentScreen;
        this.history.addAll(history);
    }

    public void setUsageMode(boolean mode) {
        this.usageMode = mode;
    }

    @Override
    protected void init() {
        super.init();
        loadRecipes();
        
        int w = 260;
        int h = 190;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("§cBack"), btn -> {
            if (!history.isEmpty()) {
                String prevId = history.remove(history.size() - 1);
                RecipeViewerScreen s = new RecipeViewerScreen(prevId, parentScreen, history);
                Minecraft.getInstance().setScreenAndShow(s);
            } else {
                Minecraft.getInstance().setScreenAndShow(parentScreen);
            }
        }).bounds(x + 20, y + 155, 50, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§c✕"), btn -> {
            Minecraft.getInstance().setScreenAndShow(parentScreen);
        }).bounds(x + w - 24, y + 8, 16, 16).build());

        prevBtn = this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
            if (currentRecipeIndex > 0) {
                currentRecipeIndex--;
                applyCurrentRecipe();
            }
        }).bounds(x + 90, y + 155, 20, 20).build());

        nextBtn = this.addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
            if (currentRecipeIndex < recipesToDisplay.size() - 1) {
                currentRecipeIndex++;
                applyCurrentRecipe();
            }
        }).bounds(x + 150, y + 155, 20, 20).build());

        viewRecipeBtn = this.addRenderableWidget(Button.builder(Component.literal("/viewrecipe"), btn -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.connection.sendCommand("viewrecipe " + this.itemId);
                Minecraft.getInstance().setScreenAndShow(null);
            }
        }).bounds(x + 175, y + 155, 75, 20).build());

        updateButtons();
    }

    private void loadRecipes() {
        recipesToDisplay.clear();
        recipeOutputs.clear();
        currentRecipeIndex = 0;

        SkyblockItemManager.ensureLoaded();
        
        if (usageMode) {
            List<SkyblockItemManager.SkyblockItemInfo> usages = SkyblockItemManager.getUsages(itemId);
            for (SkyblockItemManager.SkyblockItemInfo info : usages) {
                if (info.recipes != null) {
                    for (JsonElement el : info.recipes) {
                        JsonObject r = el.getAsJsonObject();
                        // Verify this specific recipe actually uses our item
                        boolean uses = false;
                        for (String key : new String[]{"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"}) {
                            if (r.has(key) && r.get(key).getAsString().startsWith(itemId)) {
                                uses = true;
                                break;
                            }
                        }
                        if (r.has("inputs") && r.get("inputs").isJsonArray()) {
                            for (JsonElement inEl : r.getAsJsonArray("inputs")) {
                                if (inEl.getAsString().startsWith(itemId)) uses = true;
                            }
                        }
                        if (uses) {
                            recipesToDisplay.add(r);
                            recipeOutputs.add(info.id);
                        }
                    }
                }
            }
            if (recipesToDisplay.isEmpty()) {
                error = "No usages found for " + itemId;
            }
        } else {
            SkyblockItemManager.SkyblockItemInfo info = SkyblockItemManager.getInfo(itemId);
            if (info == null) {
                error = "Item not found in local DB.";
            } else if (info.recipes == null || info.recipes.isEmpty()) {
                error = "No recipes found for " + itemId;
            } else {
                for (JsonElement el : info.recipes) {
                    recipesToDisplay.add(el.getAsJsonObject());
                    recipeOutputs.add(itemId);
                }
            }
        }

        if (!recipesToDisplay.isEmpty()) {
            applyCurrentRecipe();
        }
    }

    private void applyCurrentRecipe() {
        if (recipesToDisplay.isEmpty()) return;
        
        JsonObject recipeObj = recipesToDisplay.get(currentRecipeIndex);
        String outputId = recipeOutputs.get(currentRecipeIndex);
        
        if (recipeObj.has("type")) {
            recipeType = recipeObj.get("type").getAsString();
        } else {
            recipeType = "crafting";
        }

        outputCount = recipeObj.has("count") ? recipeObj.get("count").getAsInt() : 1;
        
        for (int i = 0; i < 9; i++) recipeGrid[i] = ItemStack.EMPTY;
        
        if (recipeObj.has("A1")) {
            String[] keys = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};
            for (int i = 0; i < 9; i++) {
                if (recipeObj.has(keys[i])) {
                    String inputStr = recipeObj.get(keys[i]).getAsString();
                    if (!inputStr.isEmpty()) {
                        String[] parts = inputStr.split(":");
                        ItemStack stack = SkyblockItemManager.createSkyblockItem(parts[0]);
                        if (stack != null && !stack.isEmpty()) {
                            stack.setCount(parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                            recipeGrid[i] = stack;
                        }
                    }
                }
            }
        } else if (recipeObj.has("inputs") && recipeObj.get("inputs").isJsonArray()) {
            JsonArray inputs = recipeObj.getAsJsonArray("inputs");
            for (int i = 0; i < 9 && i < inputs.size(); i++) {
                String inputStr = inputs.get(i).getAsString();
                if (!inputStr.isEmpty()) {
                    String[] parts = inputStr.split(":");
                    ItemStack stack = SkyblockItemManager.createSkyblockItem(parts[0]);
                    if (stack != null && !stack.isEmpty()) {
                        stack.setCount(parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                        recipeGrid[i] = stack;
                    }
                }
            }
        }

        outputItem = SkyblockItemManager.createSkyblockItem(outputId);
        if (outputItem != null && !outputItem.isEmpty()) {
            outputItem.setCount(outputCount);
        }

        updateButtons();
    }

    private void updateButtons() {
        if (prevBtn != null) prevBtn.active = currentRecipeIndex > 0;
        if (nextBtn != null) nextBtn.active = currentRecipeIndex < recipesToDisplay.size() - 1;
        if (viewRecipeBtn != null) {
            viewRecipeBtn.visible = error == null && !recipesToDisplay.isEmpty();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int w = 260;
        int h = 190;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        graphics.fill(0, 0, this.width, this.height, 0xD00A0A0A);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2A2A2A);
        graphics.fill(x, y, x + w, y + h, 0xF0121212);

        if (error != null) {
            graphics.centeredText(font, "§c" + error, x + w / 2, y + 80, 0xFFFFFFFF);
            return;
        }
        if (recipesToDisplay.isEmpty()) {
            return;
        }

        String nameColor = "§f";
        SkyblockItemManager.SkyblockItemInfo itemInfo = SkyblockItemManager.getInfo(itemId);
        if (itemInfo != null) nameColor = SkyblockItemManager.getTierColor(itemInfo.tier);
        
        String titleStr = usageMode ? "Usages: " + nameColor + (itemInfo != null ? itemInfo.name : itemId)
                                    : "Recipe: " + nameColor + (outputItem != null ? outputItem.getHoverName().getString() : itemId);
        graphics.text(font, titleStr, x + 20, y + 15, 0xFFFFFFFF, true);
        
        if (!"mob_drop".equalsIgnoreCase(recipeType)) {
            String subtitle = "§7" + (usageMode ? "Item Usages" : "Crafting Recipe") + " (" + recipeType + ")";
            graphics.text(font, subtitle, x + 20, y + 27, 0xFFFFFFFF, true);
        }

        if (recipesToDisplay.size() > 1) {
            int cx = x + 130;
            int cy = y + 161;
            graphics.centeredText(font, "§e" + (currentRecipeIndex + 1) + " / " + recipesToDisplay.size(), cx, cy, 0xFFFFFFFF);
        }

        hoveredStack = null;
        hoveredId = null;

        if ("forge".equalsIgnoreCase(recipeType)) {
            // Draw forge UI (54 slots)
            int fw = 9 * 18;
            int fh = 6 * 18;
            int fx = x + (w - fw) / 2;
            int fy = y + 45;
            
            // Draw dark background
            graphics.fill(fx - 4, fy - 4, fx + fw + 4, fy + fh + 4, 0xFF2E2E2E);
            graphics.fill(fx - 2, fy - 2, fx + fw + 2, fy + fh + 2, 0xFF141414);

            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 9; c++) {
                    int slotX = fx + c * 18;
                    int slotY = fy + r * 18;
                    
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF2E2E2E);
                    
                    ItemStack stack = ItemStack.EMPTY;
                    if (r == 1 && c == 4) stack = SkyblockItemManager.createSkyblockItem("STONE_BUTTON");
                    if (r == 2 && c >= 3 && c <= 5) {
                        int idx = c - 3;
                        if (recipeGrid[idx] != null && !recipeGrid[idx].isEmpty()) {
                            stack = recipeGrid[idx];
                        }
                    }
                    if (r == 3 && c == 4) {
                        stack = outputItem;
                    }

                    if (stack != null && !stack.isEmpty()) {
                        graphics.item(stack, slotX, slotY);
                        graphics.itemDecorations(font, stack, slotX, slotY);
                        if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x40FFFFFF);
                            hoveredStack = stack;
                            String id = "";
                            try {
                                net.minecraft.nbt.CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
                                if (tag != null && tag.contains("ExtraAttributes")) {
                                    net.minecraft.nbt.CompoundTag ea = (net.minecraft.nbt.CompoundTag) tag.get("ExtraAttributes");
                                    id = ea.getString("id").orElse("");
                                }
                            } catch (Exception e) {}
                            if (!id.isEmpty() && !id.equals("STONE_BUTTON")) hoveredId = id;
                        }
                    }
                }
            }
        } else if ("mob_drop".equalsIgnoreCase(recipeType)) {
            JsonObject recipeObj = recipesToDisplay.get(currentRecipeIndex);
            String mobId = recipeObj.has("mob_id") ? recipeObj.get("mob_id").getAsString() : "";
            String mobName = recipeObj.has("mob_name") ? recipeObj.get("mob_name").getAsString() : "Unknown Mob";
            
            int panelY = y + 27;
            int panelH = 122;
            graphics.fill(x + 10, panelY, x + w - 10, panelY + panelH, 0xFF181818);
            graphics.fill(x + 10, panelY, x + w - 10, panelY + 1, 0xFF3A3A3A);
            graphics.fill(x + 10, panelY + panelH - 1, x + w - 10, panelY + panelH, 0xFF3A3A3A);
            graphics.fill(x + 10, panelY, x + 11, panelY + panelH, 0xFF3A3A3A);
            graphics.fill(x + w - 11, panelY, x + w - 10, panelY + panelH, 0xFF3A3A3A);

            int cx = x + w / 2;
            int cy = y + 60;
            
            graphics.centeredText(font, "§6" + mobName, cx, cy - 25, 0xFFFFFFFF);
            
            ItemStack mobItem = SkyblockItemManager.createSkyblockItem(mobId);
            if (mobItem != null && !mobItem.isEmpty()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate((float) (cx - 16), (float) cy - 5);
                graphics.pose().scale(2.0f, 2.0f);
                graphics.item(mobItem, 0, 0);
                graphics.pose().popMatrix();
            }
            
            if (recipeObj.has("all_drops") && recipeObj.get("all_drops").isJsonArray()) {
                com.google.gson.JsonArray drops = recipeObj.getAsJsonArray("all_drops");
                
                int validDropsCount = 0;
                for (int i = 0; i < drops.size(); i++) {
                    if (drops.get(i).getAsJsonObject().has("id")) validDropsCount++;
                }
                
                int maxCols = 9;
                int cols = Math.min(validDropsCount, maxCols);
                int gridW = cols * 24;
                
                int startX = cx - (gridW / 2);
                int startY = cy + 35;
                int col = 0;
                int row = 0;
                
                for (int i = 0; i < drops.size(); i++) {
                    JsonObject drop = drops.get(i).getAsJsonObject();
                    if (!drop.has("id")) continue;
                    String dropId = drop.get("id").getAsString().split(":")[0];
                    ItemStack dropStack = SkyblockItemManager.createSkyblockItem(dropId);
                    
                    if (dropStack != null && !dropStack.isEmpty()) {
                        ItemStack renderStack = dropStack.copy();
                        ItemLore loreComp = renderStack.get(DataComponents.LORE);
                        List<Component> lines = new ArrayList<>();
                        if (loreComp != null) lines.addAll(loreComp.lines());
                        lines.add(Component.literal(""));
                        lines.add(Component.literal("§eDrop Chance: §a" + (drop.has("chance") ? drop.get("chance").getAsString() : "Unknown")));
                        if (drop.has("extra") && drop.get("extra").isJsonArray()) {
                            for (JsonElement extraEl : drop.getAsJsonArray("extra")) {
                                lines.add(Component.literal(extraEl.getAsString()));
                            }
                        }
                        renderStack.set(DataComponents.LORE, new ItemLore(lines));
                        
                        int slotX = startX + col * 24 + 4;
                        int slotY = startY + row * 24 + 4;
                        
                        graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF2E2E2E);
                        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF141414);
                        
                        graphics.item(renderStack, slotX, slotY);
                        graphics.itemDecorations(font, renderStack, slotX, slotY);
                        
                        if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x40FFFFFF);
                            hoveredStack = renderStack;
                            hoveredId = dropId;
                        }
                        
                        col++;
                        if (col >= maxCols) {
                            col = 0;
                            row++;
                        }
                    }
                }
            }
        } else {
            // Standard 3x3 grid
            int gridX = x + 30;
            int gridY = y + 45;
            
            graphics.fill(gridX - 4, gridY - 4, gridX + 3 * 24 + 1, gridY + 3 * 24 + 1, 0x80080808);

            for (int i = 0; i < 9; i++) {
                int col = i % 3;
                int row = i / 3;
                int slotX = gridX + col * 24;
                int slotY = gridY + row * 24;

                graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF2E2E2E);
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF141414);

                ItemStack stack = recipeGrid[i];
                if (stack != null && !stack.isEmpty()) {
                    graphics.item(stack, slotX, slotY);
                    graphics.itemDecorations(font, stack, slotX, slotY);

                    if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x40FFFFFF);
                        hoveredStack = stack;
                        String id = "";
                        try {
                            net.minecraft.nbt.CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
                            if (tag != null && tag.contains("ExtraAttributes")) {
                                net.minecraft.nbt.CompoundTag ea = (net.minecraft.nbt.CompoundTag) tag.get("ExtraAttributes");
                                id = ea.getString("id").orElse("");
                            }
                        } catch (Exception e) {}
                        if (!id.isEmpty()) hoveredId = id;
                    }
                }
            }

            // Arrow
            graphics.text(font, "➡", gridX + 85, gridY + 30, 0xFFFFD700, true);

            // Output slot
            int outX = gridX + 120;
            int outY = gridY + 24;

            graphics.fill(outX - 2, outY - 2, outX + 26, outY + 26, 0xFF2E2E2E);
            graphics.fill(outX - 1, outY - 1, outX + 25, outY + 25, 0xFF141414);

            if (outputItem != null && !outputItem.isEmpty()) {
                graphics.item(outputItem, outX + 4, outY + 4);
                graphics.itemDecorations(font, outputItem, outX + 4, outY + 4);

                if (mouseX >= outX && mouseX < outX + 24 && mouseY >= outY && mouseY < outY + 24) {
                    graphics.fill(outX, outY, outX + 24, outY + 24, 0x40FFFFFF);
                    hoveredStack = outputItem;
                    String id = "";
                    try {
                        net.minecraft.nbt.CompoundTag tag = outputItem.get(DataComponents.CUSTOM_DATA).copyTag();
                        if (tag != null && tag.contains("ExtraAttributes")) {
                            net.minecraft.nbt.CompoundTag ea = (net.minecraft.nbt.CompoundTag) tag.get("ExtraAttributes");
                            id = ea.getString("id").orElse("");
                        }
                    } catch (Exception e) {}
                    if (!id.isEmpty()) hoveredId = id;
                }
            }
        }
        
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (hoveredStack != null) {
            try {
                graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
            } catch (Throwable t) {}
        }
    }

    @Override
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
        
        if (recipesToDisplay.size() > 1 && mouseY >= cy && mouseY <= cy + 10) {
            if (mouseX >= cx - 40 && mouseX <= cx - 10) {
                if (currentRecipeIndex > 0) {
                    currentRecipeIndex--;
                    applyCurrentRecipe();
                }
                return true;
            } else if (mouseX >= cx + 25 && mouseX <= cx + 55) {
                if (currentRecipeIndex < recipesToDisplay.size() - 1) {
                    currentRecipeIndex++;
                    applyCurrentRecipe();
                }
                return true;
            }
        }
        
        if (hoveredId != null) {
            history.add(itemId);
            RecipeViewerScreen s = new RecipeViewerScreen(hoveredId, parentScreen, history);
            if (button == 1) {
                s.setUsageMode(true);
            }
            Minecraft.getInstance().setScreenAndShow(s);
            return true;
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical > 0) {
            if (currentRecipeIndex > 0) {
                currentRecipeIndex--;
                applyCurrentRecipe();
            }
        } else if (vertical < 0) {
            if (currentRecipeIndex < recipesToDisplay.size() - 1) {
                currentRecipeIndex++;
                applyCurrentRecipe();
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 262 || keyCode == 266 || keyCode == 326) { // RIGHT, PAGE UP, or NUMPAD 6
            if (currentRecipeIndex < recipesToDisplay.size() - 1) {
                currentRecipeIndex++;
                applyCurrentRecipe();
            }
            return true;
        } else if (keyCode == 263 || keyCode == 267 || keyCode == 324) { // LEFT, PAGE DOWN, or NUMPAD 4
            if (currentRecipeIndex > 0) {
                currentRecipeIndex--;
                applyCurrentRecipe();
            }
            return true;
        } else if (keyCode == 259 && !history.isEmpty()) { // BACKSPACE
            String prevId = history.remove(history.size() - 1);
            Minecraft.getInstance().setScreenAndShow(new RecipeViewerScreen(prevId, parentScreen, history));
            return true;
        }
        return super.keyPressed(event);
    }
}
