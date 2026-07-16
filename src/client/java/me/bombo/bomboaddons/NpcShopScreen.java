package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;

public class NpcShopScreen extends Screen {
    private static final Identifier CHEST_GUI_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private final SkyblockItemManager.SkyblockItemInfo npcInfo;
    private final Screen parentScreen;
    private final List<ItemStack> shopItems = new ArrayList<>();
    private final List<String> resultIds = new ArrayList<>();
    private final int xSize = 176;
    private final int ySize = 222;

    public NpcShopScreen(SkyblockItemManager.SkyblockItemInfo npcInfo, Screen parentScreen) {
        super(Component.literal(npcInfo.name));
        this.npcInfo = npcInfo;
        this.parentScreen = parentScreen;
        loadShopItems();
    }

    private void loadShopItems() {
        if (npcInfo.recipes == null) return;

        for (JsonElement el : npcInfo.recipes) {
            if (!el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            if (r.has("type") && "npc_shop".equals(r.get("type").getAsString()) && r.has("result")) {
                String resultStr = r.get("result").getAsString();
                String[] parts = resultStr.split(":");
                String resultId = parts[0];
                int count = parts.length > 1 ? (int) Double.parseDouble(parts[1]) : 1;

                ItemStack stack = SkyblockItemManager.createSkyblockItem(resultId);
                if (stack != null && !stack.isEmpty()) {
                    stack.setCount(count);
                    
                    // Add Cost to Lore
                    if (r.has("cost")) {
                        List<Component> extraLore = new ArrayList<>();
                        extraLore.add(Component.literal(""));
                        extraLore.add(Component.literal("§7Cost"));
                        
                        List<String> costs = new ArrayList<>();
                        if (r.get("cost").isJsonArray()) {
                            for (JsonElement costEl : r.getAsJsonArray("cost")) {
                                costs.add(costEl.getAsString());
                            }
                        } else if (r.get("cost").isJsonPrimitive()) {
                            costs.add(r.get("cost").getAsString());
                        }

                        for (String costStr : costs) {
                            String[] costParts = costStr.split(":");
                            String costId = costParts[0];
                            String costAmt = costParts.length > 1 ? costParts[1] : "1";
                            String costAmtFormatted = costAmt;
                            try {
                                long amt = Long.parseLong(costAmt);
                                costAmtFormatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(amt);
                            } catch (Exception e) {}
                            
                            if (costId.equals("SKYBLOCK_COIN")) {
                                extraLore.add(Component.literal("§6" + costAmtFormatted + " Coins"));
                            } else {
                                SkyblockItemManager.SkyblockItemInfo costInfo = SkyblockItemManager.getInfo(costId);
                                String colorPrefix = costInfo != null ? SkyblockItemManager.getTierColor(costInfo.tier) : "§a";
                                String costName = costInfo != null && costInfo.name != null ? costInfo.name.replaceAll("(?i)§[0-9A-FK-OR]", "") : costId;
                                extraLore.add(Component.literal(colorPrefix + costName + " §8x" + costAmtFormatted));
                            }
                        }
                        
                        extraLore.add(Component.literal(""));
                        extraLore.add(Component.literal("§eClick to trade!"));

                        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
                        List<Component> combined = new ArrayList<>(lore.lines());
                        combined.addAll(extraLore);
                        stack.set(DataComponents.LORE, new ItemLore(combined, combined));
                    }

                    shopItems.add(stack);
                    resultIds.add(resultId);
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - xSize) / 2;
        int y = (this.height - ySize) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("§cBack"), btn -> {
            Minecraft.getInstance().setScreenAndShow(parentScreen);
        }).bounds(x + xSize - 50, y + ySize - 25, 40, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§aGo To NPC"), btn -> {
            if (Minecraft.getInstance().player != null) {
                String targetName = npcInfo.name != null ? npcInfo.name.replaceAll("(?i)§[0-9a-fk-or]", "") : npcInfo.id;
                
                boolean needsWarp = false;
                if (npcInfo.island != null && !npcInfo.island.isEmpty()) {
                    String currentLoc = me.bombo.bomboaddons.SkyblockUtils.getLocation();
                    if (currentLoc == null) currentLoc = "";
                    String formattedLoc = currentLoc.replace(" ", "_");
                    if (!formattedLoc.equalsIgnoreCase(npcInfo.island) && !currentLoc.equalsIgnoreCase(npcInfo.island)) {
                        needsWarp = true;
                    }
                }
                
                if (needsWarp) {
                    me.bombo.bomboaddons.WaypointManager.pendingNavTarget = targetName;
                    Minecraft.getInstance().player.connection.sendCommand("warp " + npcInfo.island);
                } else {
                    Minecraft.getInstance().player.connection.sendCommand("bnav " + targetName);
                }
                
                Minecraft.getInstance().setScreenAndShow(null);
            }
        }).bounds(x + 10, y + ySize - 25, 80, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = (this.width - xSize) / 2;
        int y = (this.height - ySize) / 2;
        graphics.blit(CHEST_GUI_TEXTURE, x, y, x + xSize, y + ySize, 0f, 176f/256f, 0f, 222f/256f);
        
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String nameStr = npcInfo.name != null ? npcInfo.name.replaceAll("(?i)§[0-9A-FK-OR]", "") : npcInfo.id;
        graphics.text(this.font, nameStr, x + 8, y + 6, 4210752, false);
        
        int startX = x + 8;
        int startY = y + 18;
        
        ItemStack hoveredStack = null;
        for (int i = 0; i < 54 && i < shopItems.size(); i++) {
            int row = i / 9;
            int col = i % 9;
            int slotX = startX + col * 18;
            int slotY = startY + row * 18;

            ItemStack stack = shopItems.get(i);
            
            if (stack != null && !stack.isEmpty()) {
                graphics.item(stack, slotX, slotY);
                graphics.itemDecorations(this.font, stack, slotX, slotY);

                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                    hoveredStack = stack;
                }
            }
        }
        
        if (hoveredStack != null) {
            try {
                graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
            } catch (Throwable t) {}
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (handled) return super.mouseClicked(event, handled);

        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        
        int x = (this.width - xSize) / 2;
        int y = (this.height - ySize) / 2;
        int startX = x + 8;
        int startY = y + 18;
        
        for (int i = 0; i < 54 && i < shopItems.size(); i++) {
            int row = i / 9;
            int col = i % 9;
            int slotX = startX + col * 18;
            int slotY = startY + row * 18;

            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                if (event.button() == 0) {
                    String clickedId = resultIds.get(i);
                    RecipeViewerScreen newScreen = new RecipeViewerScreen(clickedId, this);
                    Minecraft.getInstance().setScreenAndShow(newScreen);
                    return true;
                }
            }
        }
        
        return super.mouseClicked(event, handled);
    }
}
