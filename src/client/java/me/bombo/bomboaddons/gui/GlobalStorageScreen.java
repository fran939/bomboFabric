package me.bombo.bomboaddons.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.features.StorageTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;

import java.util.*;
import java.util.stream.Collectors;

public class GlobalStorageScreen extends Screen {
    private EditBox searchBox;
    private String currentCategory = "All";
    private List<AggregatedItem> filteredItems = new ArrayList<>();
    private List<AggregatedItem> allItems = new ArrayList<>();
    
    private static List<AggregatedItem> cachedAllItems = new ArrayList<>();
    private static long cachedStorageTime = -1;
    
    private int scrollOffset = 0;
    private int itemsPerRow = me.bombo.bomboaddons.BomboConfig.get().storageGuiCols;
    private final int itemSize = 18;
    private final int spacing = 2;
    private int maxVisibleRows = me.bombo.bomboaddons.BomboConfig.get().storageGuiRows;
    
    private boolean isResizing = false;
    private double resizeStartX = 0;
    private double resizeStartY = 0;
    private int resizeStartCols = 0;
    private int resizeStartRows = 0;

    public GlobalStorageScreen() {
        super(Component.literal("Global Storage"));
    }

    private static class TabInfo {
        String name;
        ItemStack icon;
        TabInfo(String name, ItemStack icon) {
            this.name = name;
            this.icon = icon;
        }
    }
    
    private List<TabInfo> tabs = new ArrayList<>();

    private static class AggregatedItem {
        ItemStack stack;
        int totalCount = 0;
        Map<String, Integer> locations = new HashMap<>(); // "Ender Chest Page 1" -> 64
        String rawNbt;

        AggregatedItem(ItemStack stack, String nbt) {
            this.stack = stack.copy();
            this.rawNbt = nbt;
        }

        void addLocation(String container, int count) {
            this.totalCount += count;
            locations.put(container, locations.getOrDefault(container, 0) + count);
        }
    }

    @Override
    protected void init() {
        // Layout constants — derived from the grid so there's no wasted space
        int panelW = 8 + (itemsPerRow * 18 + 2) + 2 + 12 + 4;
        int panelH = 22 + (maxVisibleRows * 18 + 2) + 6;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        // Search box: inside black fill at px+8, py+5
        searchBox = new EditBox(this.font, px + 9, py + 6, panelW - 18, 10, Component.literal("Search..."));
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setResponder(this::updateSearch);
        this.addRenderableWidget(searchBox);
        
        tabs.clear();
        tabs.add(new TabInfo("All", new ItemStack(net.minecraft.world.item.Items.COMPASS)));
        tabs.add(new TabInfo("Inventory", new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD)));
        tabs.add(new TabInfo("Chest", new ItemStack(net.minecraft.world.item.Items.CHEST)));
        tabs.add(new TabInfo("Ender Chest", new ItemStack(net.minecraft.world.item.Items.ENDER_CHEST)));
        
        ItemStack bp = me.bombo.bomboaddons.SkyblockItemManager.createSkyblockItem("GREATER_BACKPACK");
        tabs.add(new TabInfo("Backpack", bp != null ? bp : new ItemStack(net.minecraft.world.item.Items.LEATHER)));
        
        tabs.add(new TabInfo("Vault", new ItemStack(net.minecraft.world.item.Items.GOLD_BLOCK)));
        tabs.add(new TabInfo("Sack", new ItemStack(net.minecraft.world.item.Items.GUNPOWDER)));
        tabs.add(new TabInfo("Armor", new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)));

        aggregateItems();
        updateSearch(searchBox.getValue());
    }

    private void aggregateItems() {
        if (me.bombo.bomboaddons.features.StorageTracker.lastUpdateTime == cachedStorageTime && !cachedAllItems.isEmpty()) {
            allItems = new ArrayList<>(cachedAllItems);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
        Map<String, AggregatedItem> map = new HashMap<>();

        for (Map.Entry<String, Map<Integer, String>> containerEntry : StorageTracker.storageData.entrySet()) {
            String containerName = containerEntry.getKey();
            for (String nbtStr : containerEntry.getValue().values()) {
                if (nbtStr == null || nbtStr.isEmpty()) continue;
                
                try {
                    CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(nbtStr);
                    ItemStack stack = ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
                    
                    if (!stack.isEmpty()) {
                        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) continue;
                        String cleanName = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
                        if (cleanName.contains("Empty ") || cleanName.contains("Locked") || cleanName.equals("Back") || cleanName.equals("Close") || cleanName.contains(" Page") || cleanName.equals("Go Back")) {
                            continue;
                        }
                        
                        // If this is a player head with no PROFILE, try to extract skin from legacy NBT
                        if (stack.getItem() == net.minecraft.world.item.Items.PLAYER_HEAD && !stack.has(net.minecraft.core.component.DataComponents.PROFILE)) {
                            try {
                                net.minecraft.world.item.component.CustomData cd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                                if (cd != null) {
                                    CompoundTag ct = cd.copyTag();
                                    // Check SkullOwner legacy NBT
                                    if (ct.contains("SkullOwner")) {
                                        CompoundTag so = ct.getCompoundOrEmpty("SkullOwner");
                                        if (so.contains("Properties")) {
                                            CompoundTag props = so.getCompoundOrEmpty("Properties");
                                            if (props.contains("textures")) {
                                                net.minecraft.nbt.ListTag textures = props.getListOrEmpty("textures");
                                                if (!textures.isEmpty()) {
                                                    CompoundTag t0 = ((CompoundTag) textures.get(0));
                                                    if (t0.contains("Value")) {
                                                        String skinVal = t0.getString("Value").orElse("");
                                                        if (!skinVal.isEmpty()) {
                                                            net.minecraft.world.item.component.ResolvableProfile rp = me.bombo.bomboaddons.SkyblockItemManager.createProfile(skinVal, null);
                                                            if (rp != null) stack.set(net.minecraft.core.component.DataComponents.PROFILE, rp);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        
                        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        String name = stack.getHoverName().getString();
                        String groupKey = id + ":" + name; 
                        
                        AggregatedItem agg = map.computeIfAbsent(groupKey, k -> new AggregatedItem(stack, nbtStr));
                        agg.addLocation(containerName, stack.getCount());
                    }
                } catch (Exception e) {}
            }
        }
        
        allItems = new ArrayList<>(map.values());
        allItems.sort((a, b) -> Integer.compare(b.totalCount, a.totalCount));
        
        cachedAllItems = new ArrayList<>(allItems);
        cachedStorageTime = me.bombo.bomboaddons.features.StorageTracker.lastUpdateTime;
    }

    private void updateSearch(String query) {
        String lowerQuery = query.toLowerCase();
        filteredItems = allItems.stream().filter(agg -> {
            boolean categoryMatch = currentCategory.equals("All") || 
                agg.locations.keySet().stream().anyMatch(loc -> loc.toLowerCase().contains(currentCategory.toLowerCase()));
            
            boolean textMatch = lowerQuery.isEmpty() || agg.stack.getHoverName().getString().toLowerCase().contains(lowerQuery);
            return categoryMatch && textMatch;
        }).collect(Collectors.toList());
        scrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
        } else if (scrollY < 0) {
            int maxScroll = Math.max(0, (int)Math.ceil((double)filteredItems.size() / itemsPerRow) - maxVisibleRows);
            if (scrollOffset < maxScroll) {
                scrollOffset++;
            }
        }
        return true;
    }

    private AggregatedItem hoveredItem = null;

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
        int panelW = 8 + (itemsPerRow * 18 + 2) + 2 + 12 + 4;
        int panelH = 22 + (maxVisibleRows * 18 + 2) + 6;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        if (event.x() >= px + panelW - 12 && event.x() <= px + panelW && event.y() >= py + panelH - 12 && event.y() <= py + panelH) {
            isResizing = true;
            resizeStartX = event.x();
            resizeStartY = event.y();
            resizeStartCols = itemsPerRow;
            resizeStartRows = maxVisibleRows;
            return true;
        }

        int tabWidth = 28;
        for (int i = 0; i < tabs.size(); i++) {
            int tx = px + 28 * i;
            int ty = py - 28;
            boolean selected = currentCategory.equals(tabs.get(i).name);
            if (selected) ty = py - 32;
            
            int th = selected ? 32 : 28;
            if (event.x() >= tx && event.x() < tx + tabWidth && event.y() >= ty && event.y() < ty + th) {
                currentCategory = tabs.get(i).name;
                updateSearch(searchBox.getValue());
                return true;
            }
        }

        if (hoveredItem != null && !hoveredItem.locations.isEmpty()) {
            String targetLoc = hoveredItem.locations.keySet().iterator().next();
            String[] locCmd = me.bombo.bomboaddons.features.StorageTracker.getDisplayLocAndCommand(targetLoc);
            String cmd = locCmd[1];
            
            if (!cmd.isEmpty()) {
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                minecraft.player.connection.sendCommand(cmd);
                me.bombo.bomboaddons.SlotHighlight.addTargetName(hoveredItem.stack.getHoverName().getString(), 0xAA00FF00);
            } else if (targetLoc.contains(" @ ")) {
                try {
                    String coords = targetLoc.substring(targetLoc.indexOf(" @ ") + 3);
                    String[] parts = coords.split(",");
                    if (parts.length == 3) {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int z = Integer.parseInt(parts[2].trim());
                        me.bombo.bomboaddons.BlockHighlight.targetChestPos = new net.minecraft.core.BlockPos(x, y, z);
                        me.bombo.bomboaddons.BlockHighlight.targetChestTime = System.currentTimeMillis();
                        me.bombo.bomboaddons.SlotHighlight.addTargetName(hoveredItem.stack.getHoverName().getString(), 0xAA00FF00);
                    }
                } catch (Exception e) {}
            }
            this.minecraft.setScreen(null);
            return true;
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (isResizing) {
            int dCols = (int) Math.round((event.x() - resizeStartX) / 9.0);
            int dRows = (int) Math.round((event.y() - resizeStartY) / 9.0);
            
            itemsPerRow = Math.max(5, Math.min(24, resizeStartCols + dCols));
            maxVisibleRows = Math.max(3, Math.min(16, resizeStartRows + dRows));
            
            int panelW = 8 + (itemsPerRow * 18 + 2) + 2 + 12 + 4;
            int panelH = 22 + (maxVisibleRows * 18 + 2) + 6;
            int px = (this.width - panelW) / 2;
            int py = (this.height - panelH) / 2;
            searchBox.setX(px + 9);
            searchBox.setY(py + 6);
            searchBox.setWidth(panelW - 18);
            
            // Adjust scroll if out of bounds
            int maxScroll = Math.max(0, (int)Math.ceil((double)filteredItems.size() / itemsPerRow) - maxVisibleRows);
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (isResizing) {
            isResizing = false;
            me.bombo.bomboaddons.BomboConfig.get().storageGuiCols = itemsPerRow;
            me.bombo.bomboaddons.BomboConfig.get().storageGuiRows = maxVisibleRows;
            me.bombo.bomboaddons.BomboConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelW = 8 + (itemsPerRow * 18 + 2) + 2 + 12 + 4;
        int panelH = 22 + (maxVisibleRows * 18 + 2) + 6;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        graphics.fill(0, 0, this.width, this.height, 0x60000000);

        // Tabs at the top
        int tabWidth = 28;
        for (int i = 0; i < tabs.size(); i++) {
            TabInfo tab = tabs.get(i);
            boolean selected = currentCategory.equals(tab.name);
            
            int tx = px + 28 * i;
            int ty = py - 28;
            int th = selected ? 32 : 28;
            if (selected) ty = py - 32;

            // Draw tab button
            graphics.fill(tx, ty, tx + tabWidth, ty + th, 0xFF555555);
            graphics.fill(tx + 1, ty + 1, tx + tabWidth - 1, ty + th, selected ? 0xFFC8C8C8 : 0xFF999999);
            // Light edge for selected tab
            if (selected) {
                graphics.fill(tx + 1, ty + 1, tx + tabWidth - 1, ty + 2, 0xFFEEEEEE);
                graphics.fill(tx + 1, ty + 1, tx + 2, ty + th, 0xFFEEEEEE);
            }
            graphics.item(tab.icon, tx + 6, ty + 7);
        }

        graphics.fill(px, py, px + panelW, py + panelH, 0xFF8B8B8B);
        graphics.fill(px + 7, py + 4, px + panelW - 4, py + 18, 0xFF373737);
        graphics.fill(px + 8, py + 5, px + panelW - 5, py + 17, 0xFF000000);

        // Item grid inner panel
        int gx = px + 8;
        int gy = py + 22;
        int gw = itemsPerRow * 18 + 2;
        int gh = maxVisibleRows * 18 + 2;
        graphics.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, 0xFF373737);
        graphics.fill(gx, gy, gx + gw, gy + gh, 0xFF8B8B8B);

        // Scrollbar — immediately right of the item grid
        int scrollX = gx + gw + 2;
        int scrollY = gy;
        int scrollH = maxVisibleRows * itemSize;
        graphics.fill(scrollX, scrollY, scrollX + 12, scrollY + scrollH, 0xFF373737);
        graphics.fill(scrollX + 1, scrollY + 1, scrollX + 11, scrollY + scrollH - 1, 0xFF8B8B8B);

        int startX = gx + 1;
        int startY = gy + 1;
        
        int row = 0;
        int col = 0;
        int startIndex = scrollOffset * itemsPerRow;
        
        this.hoveredItem = null;
        int hx = 0;
        int hy = 0;

        for (int i = startIndex; i < filteredItems.size() && row < maxVisibleRows; i++) {
            AggregatedItem agg = filteredItems.get(i);
            int x = startX + col * itemSize;
            int y = startY + row * itemSize;
            
            ItemStack renderStack = agg.stack.copy();
            renderStack.setCount(1);
            graphics.item(renderStack, x + 1, y + 1);
            graphics.itemDecorations(this.font, renderStack, x + 1, y + 1);
            
            if (agg.totalCount > 1) {
                String amt = formatAmount(agg.totalCount);
                graphics.text(this.font, amt, x + 17 - this.font.width(amt), y + 9, 0xFFFFFF, true);
            }

            if (mouseX >= x && mouseX <= x + itemSize && mouseY >= y && mouseY <= y + itemSize) {
                graphics.fill(x + 1, y + 1, x + itemSize - 1, y + itemSize - 1, 0x80FFFFFF);
                this.hoveredItem = agg;
                hx = x;
                hy = y;
            }

            col++;
            if (col >= itemsPerRow) {
                col = 0;
                row++;
            }
        }
        
        // Scrollbar knob
        int totalRows = Math.max(1, (int)Math.ceil((double)filteredItems.size() / itemsPerRow));
        int maxScroll = Math.max(0, totalRows - maxVisibleRows);
        int knobH = Math.max(12, (int)((double)maxVisibleRows / totalRows * scrollH));
        if (knobH > scrollH - 2) knobH = scrollH - 2;
        
        int knobY = scrollY + 1;
        if (maxScroll > 0) {
            knobY += (int)(((double)scrollOffset / maxScroll) * (scrollH - 2 - knobH));
        }
        graphics.fill(scrollX + 1, knobY, scrollX + 11, knobY + knobH, 0xFFC6C6C6);

        if (this.hoveredItem != null) {
            List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
            tooltip.add(this.hoveredItem.stack.getHoverName());
            tooltip.add(net.minecraft.network.chat.Component.literal("§7Total Amount: §e" + this.hoveredItem.totalCount));
            tooltip.add(net.minecraft.network.chat.Component.literal("§8----------------"));
            for (Map.Entry<String, Integer> loc : this.hoveredItem.locations.entrySet()) {
                String[] locCmd = me.bombo.bomboaddons.features.StorageTracker.getDisplayLocAndCommand(loc.getKey());
                String displayLoc = locCmd[0];
                tooltip.add(net.minecraft.network.chat.Component.literal("§7" + displayLoc + ": §a" + loc.getValue()));
            }
            graphics.setTooltipForNextFrame(this.font, tooltip, java.util.Optional.empty(), hx, hy);
        }

        // Draw resize handle
        int rhx = px + panelW - 8;
        int rhy = py + panelH - 8;
        graphics.fill(rhx + 4, rhy + 4, rhx + 6, rhy + 6, 0xFFFFFFFF);
        graphics.fill(rhx + 2, rhy + 6, rhx + 4, rhy + 8, 0xFFFFFFFF);
        graphics.fill(rhx + 6, rhy + 2, rhx + 8, rhy + 4, 0xFFFFFFFF);
        
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    
    private String formatAmount(int amount) {
        if (amount >= 1000000) return String.format("%.1fM", amount / 1000000.0);
        if (amount >= 1000) return String.format("%.1fk", amount / 1000.0);
        return String.valueOf(amount);
    }
}
