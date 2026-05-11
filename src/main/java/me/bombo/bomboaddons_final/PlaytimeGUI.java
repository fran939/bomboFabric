package me.bombo.bomboaddons_final;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

import net.minecraft.client.input.MouseButtonEvent;

public class PlaytimeGUI extends Screen {
    private static final Identifier CHEST_GUI_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private final int xSize = 176;
    private final int ySize = 222;
    private int guiLeft;
    private int guiTop;

    private enum ViewMode {
        AREAS, DAYS, DAY_DETAIL, SUB_AREAS
    }
    private ViewMode currentMode = ViewMode.AREAS;
    private String selectedDate = null;
    private String selectedAreaName = null;
    private List<ViewItem> viewItems = new ArrayList<>();

    private long totalPlaytime = 0;
    private long totalAfkTime = 0;
    private Map<String, Long> globalDaily = new HashMap<>();

    private final com.google.gson.JsonObject cloudData;

    public PlaytimeGUI() {
        this(null);
    }

    public PlaytimeGUI(com.google.gson.JsonObject cloudData) {
        super(Component.literal(cloudData != null ? "Playtime: " + (cloudData.has("username") ? cloudData.get("username").getAsString() : "Unknown") : "Detailed /playtime"));
        this.cloudData = cloudData;
        calculateEntries();
    }

    private void calculateEntries() {
        Map<String, PlaytimeTracker.AreaData> map;
        if (this.cloudData != null) {
            map = new HashMap<>();
            if (this.cloudData.has("areaDataMap")) {
                com.google.gson.JsonObject areas = this.cloudData.getAsJsonObject("areaDataMap");
                for (String key : areas.keySet()) {
                    PlaytimeTracker.AreaData data = new com.google.gson.Gson().fromJson(areas.get(key), PlaytimeTracker.AreaData.class);
                    map.put(key, data);
                }
            }
        } else {
            map = PlaytimeTracker.getAreaDataMap();
        }

        totalPlaytime = 0;
        totalAfkTime = 0;
        globalDaily = new HashMap<>();

        for (Map.Entry<String, PlaytimeTracker.AreaData> entry : map.entrySet()) {
            totalPlaytime += entry.getValue().totalTime;
            totalAfkTime += entry.getValue().afkTime;
            
            // Aggregate global daily stats
            for (Map.Entry<String, Long> daily : entry.getValue().dailyTime.entrySet()) {
                globalDaily.put(daily.getKey(), globalDaily.getOrDefault(daily.getKey(), 0L) + daily.getValue());
            }
        }

        this.viewItems.clear();

        if (currentMode == ViewMode.AREAS) {
            for (Map.Entry<String, PlaytimeTracker.AreaData> entry : map.entrySet()) {
                this.viewItems.add(new ViewItem(entry.getKey(), entry.getValue(), entry.getValue().totalTime));
            }
            this.viewItems.sort((a, b) -> Long.compare(b.sortValue, a.sortValue));
        } else if (currentMode == ViewMode.DAYS) {
            for (Map.Entry<String, Long> day : this.globalDaily.entrySet()) {
                long afk = 0;
                for (PlaytimeTracker.AreaData d : map.values()) {
                    afk += d.dailyAfk.getOrDefault(day.getKey(), 0L);
                }
                ViewItem item = new ViewItem(day.getKey(), null, day.getValue());
                item.dayAfkTime = afk;
                this.viewItems.add(item);
            }
            this.viewItems.sort((a, b) -> b.name.compareTo(a.name)); // Sort dates descending
        } else if (currentMode == ViewMode.DAY_DETAIL) {
            for (Map.Entry<String, PlaytimeTracker.AreaData> entry : map.entrySet()) {
                long time = entry.getValue().dailyTime.getOrDefault(selectedDate, 0L);
                if (time > 0) {
                    ViewItem item = new ViewItem(entry.getKey(), entry.getValue(), time);
                    item.dayAfkTime = entry.getValue().dailyAfk.getOrDefault(selectedDate, 0L);
                    this.viewItems.add(item);
                }
            }
            this.viewItems.sort((a, b) -> Long.compare(b.sortValue, a.sortValue));
        } else if (currentMode == ViewMode.SUB_AREAS) {
            PlaytimeTracker.AreaData parent = map.get(selectedAreaName);
            if (parent != null && parent.subAreas != null) {
                for (Map.Entry<String, PlaytimeTracker.AreaData> entry : parent.subAreas.entrySet()) {
                    this.viewItems.add(new ViewItem(entry.getKey(), entry.getValue(), entry.getValue().totalTime));
                }
                this.viewItems.sort((a, b) -> Long.compare(b.sortValue, a.sortValue));
            }
        }
    }

    private String getAverage(Map<String, Long> daily, int days) {
        if (daily == null || daily.isEmpty()) return "0s";
        List<String> keys = new ArrayList<>(daily.keySet());
        Collections.sort(keys, Collections.reverseOrder());
        
        long total = 0;
        int count = Math.min(keys.size(), days);
        for (int i = 0; i < count; i++) {
            total += daily.get(keys.get(i));
        }
        return PlaytimeTracker.formatTime(total / count);
    }

    @Override
    protected void init() {
        this.guiLeft = (this.width - this.xSize) / 2;
        this.guiTop = (this.height - this.ySize) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (this.cloudData == null) {
            calculateEntries(); // Update real-time only if viewing local data
        }
        
        int x = (this.width - xSize) / 2;
        int y = (this.height - ySize) / 2;
        graphics.blit(CHEST_GUI_TEXTURE, x, y, xSize, ySize, 0f, 0f, (float) xSize, (float) ySize);
        
        graphics.drawString(this.font, this.title, x + 8, y + 6, 4210752, false);
        
        int startX = x + 8;
        int startY = y + 18;
        
        HoveredTooltip hovered = null;

        // Main Stats Icon
        int clockX = startX + 4 * 18;
        int clockY = startY;
        graphics.renderItem(Items.CLOCK.getDefaultInstance(), clockX, clockY);
        if (isMouseOver(clockX, clockY, mouseX, mouseY)) {
            graphics.fill(clockX, clockY, clockX + 16, clockY + 16, 0x80FFFFFF);
            hovered = new HoveredTooltip("§6Global Statistics", mouseX, mouseY,
                "§7Total Playtime: §a" + PlaytimeTracker.formatTime(totalPlaytime),
                "§7Total AFK Time: §c" + PlaytimeTracker.formatTime(totalAfkTime),
                "§7Active Playtime: §b" + PlaytimeTracker.formatTime(totalPlaytime - totalAfkTime),
                "",
                "§e§lAverages:",
                "§7Daily: §f" + getAverage(globalDaily, 1),
                "§7Weekly: §f" + getAverage(globalDaily, 7),
                "§7Monthly: §f" + getAverage(globalDaily, 30)
            );
        }

        // Area Icons
        for (int i = 0; i < Math.min(viewItems.size(), 27); i++) {
            ViewItem item = viewItems.get(i);
            int row = (i / 9) + 1;
            int col = i % 9;
            int slotX = startX + col * 18;
            int slotY = startY + row * 18;
            
            ItemStack icon = currentMode == ViewMode.DAYS ? Items.PAPER.getDefaultInstance() : getIconForArea(item.name);
            graphics.renderItem(icon, slotX, slotY);
            
            if (isMouseOver(slotX, slotY, mouseX, mouseY)) {
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                
                if (currentMode == ViewMode.AREAS) {
                    hovered = new HoveredTooltip("§a" + item.name, mouseX, mouseY,
                        "§7Playtime: §a" + PlaytimeTracker.formatTime(item.sortValue) + " §8(" + PlaytimeTracker.formatTime(item.data.sessionTime) + ")",
                        "§7AFK Time: §c" + PlaytimeTracker.formatTime(item.data.afkTime) + " §8(" + PlaytimeTracker.formatTime(item.data.sessionAfkTime) + ")",
                        "§7Active: §b" + PlaytimeTracker.formatTime(item.sortValue - item.data.afkTime),
                        "",
                        "§e§lAverages:",
                        "§7Daily: §f" + getAverage(item.data.dailyTime, 1),
                        "§7Weekly: §f" + getAverage(item.data.dailyTime, 7),
                        "§7Monthly: §f" + getAverage(item.data.dailyTime, 30),
                        "",
                        "§eClick to view subareas!"
                    );
                } else if (currentMode == ViewMode.DAYS) {
                    hovered = new HoveredTooltip("§b" + item.name, mouseX, mouseY,
                        "§7Total Playtime: §a" + PlaytimeTracker.formatTime(item.sortValue),
                        "§7AFK Time: §c" + PlaytimeTracker.formatTime(item.dayAfkTime),
                        "§7Active: §b" + PlaytimeTracker.formatTime(item.sortValue - item.dayAfkTime),
                        "",
                        "§eClick to view islands played on this day!"
                    );
                } else if (currentMode == ViewMode.DAY_DETAIL) {
                    hovered = new HoveredTooltip("§a" + item.name + " §7(" + selectedDate + ")", mouseX, mouseY,
                        "§7Playtime: §a" + PlaytimeTracker.formatTime(item.sortValue),
                        "§7AFK Time: §c" + PlaytimeTracker.formatTime(item.dayAfkTime),
                        "§7Active: §b" + PlaytimeTracker.formatTime(item.sortValue - item.dayAfkTime)
                    );
                } else if (currentMode == ViewMode.SUB_AREAS) {
                    hovered = new HoveredTooltip("§d" + item.name + " §7(Subarea)", mouseX, mouseY,
                        "§7Playtime: §a" + PlaytimeTracker.formatTime(item.sortValue) + " §8(" + PlaytimeTracker.formatTime(item.data.sessionTime) + ")",
                        "§7AFK Time: §c" + PlaytimeTracker.formatTime(item.data.afkTime) + " §8(" + PlaytimeTracker.formatTime(item.data.sessionAfkTime) + ")",
                        "§7Active: §b" + PlaytimeTracker.formatTime(item.sortValue - item.data.afkTime)
                    );
                }
            }
        }
        
        // Current Session Info
        int sessionX = startX + 7 * 18;
        int sessionY = startY + 4 * 18;
        graphics.renderItem(Items.GOLD_NUGGET.getDefaultInstance(), sessionX, sessionY);
        if (isMouseOver(sessionX, sessionY, mouseX, mouseY)) {
            graphics.fill(sessionX, sessionY, sessionX + 16, sessionY + 16, 0x80FFFFFF);

            long elapsed = PlaytimeTracker.getSessionTime();
            String area = BomboaddonsClient.currentArea;
            boolean afk = PlaytimeTracker.isAfk();
            boolean offline = false;

            if (this.cloudData != null) {
                if (this.cloudData.has("sessionTime")) {
                    elapsed = this.cloudData.get("sessionTime").getAsLong();
                } else {
                    elapsed = 0;
                }
                if (this.cloudData.has("currentArea")) {
                    area = this.cloudData.get("currentArea").getAsString();
                } else {
                    area = "Unknown";
                }
                if (this.cloudData.has("isAfk")) {
                    afk = this.cloudData.get("isAfk").getAsBoolean();
                }
                if (this.cloudData.has("lastUpdated")) {
                    long lastUpdated = this.cloudData.get("lastUpdated").getAsLong();
                    // Syncs every 5 minutes. If it's been more than 7 minutes, consider offline.
                    if (System.currentTimeMillis() - lastUpdated > 7 * 60 * 1000) {
                        offline = true;
                    }
                }
            }

            String statusStr;
            if (offline) {
                statusStr = "§8Offline";
            } else if (afk) {
                statusStr = "§cAFK";
            } else {
                statusStr = "§aActive";
            }
            
            if ("None".equals(area)) {
                area = "Main Screen";
            }

            hovered = new HoveredTooltip(offline ? "§dLast Session" : "§dCurrent Session", mouseX, mouseY,
                "§7Elapsed: §e" + PlaytimeTracker.formatTime(elapsed),
                "§7" + (offline ? "Last Area" : "Current Area") + ": §f" + area,
                "§7Status: " + statusStr
            );
        }

        // Bottom Navigation Buttons
        int closeX = startX + 4 * 18;
        int closeY = startY + 5 * 18;
        graphics.renderItem(Items.BARRIER.getDefaultInstance(), closeX, closeY);
        if (isMouseOver(closeX, closeY, mouseX, mouseY)) {
            graphics.fill(closeX, closeY, closeX + 16, closeY + 16, 0x80FFFFFF);
            hovered = new HoveredTooltip("§cClose", mouseX, mouseY);
        }
        
        if (currentMode != ViewMode.AREAS) {
            int backX = startX + 3 * 18;
            int backY = startY + 5 * 18;
            graphics.renderItem(Items.ARROW.getDefaultInstance(), backX, backY);
            if (isMouseOver(backX, backY, mouseX, mouseY)) {
                graphics.fill(backX, backY, backX + 16, backY + 16, 0x80FFFFFF);
                hovered = new HoveredTooltip("§eBack", mouseX, mouseY, "§7Return to previous view");
            }
        }
        
        if (currentMode == ViewMode.AREAS) {
            int dailyX = startX + 5 * 18;
            int dailyY = startY + 5 * 18;
            graphics.renderItem(Items.WRITABLE_BOOK.getDefaultInstance(), dailyX, dailyY);
            if (isMouseOver(dailyX, dailyY, mouseX, mouseY)) {
                graphics.fill(dailyX, dailyY, dailyX + 16, dailyY + 16, 0x80FFFFFF);
                hovered = new HoveredTooltip("§bDaily View", mouseX, mouseY, "§7View playtime broken down by day.");
            }
        }

        // Draw Tooltip last to be on top
        if (hovered != null) {
            drawCustomTooltip(graphics, hovered, mouseX, mouseY);
        }
    }

    private boolean isMouseOver(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    private void drawCustomTooltip(GuiGraphics graphics, HoveredTooltip tooltip, int mouseX, int mouseY) {
        int tX = mouseX + 12;
        int tY = mouseY - 12;
        int width = 170;
        int height = (tooltip.lore.length + 1) * 10 + 4;
        
        // Ensure tooltip stays on screen
        if (tX + width > this.width) tX = mouseX - width - 12;
        if (tY + height > this.height) tY = this.height - height - 4;

        graphics.fill(tX - 4, tY - 4, tX + width, tY + height, 0xFF181818);
        graphics.fill(tX - 5, tY - 5, tX - 4, tY + height + 1, 0xFF555555);
        graphics.fill(tX + width, tY - 5, tX + width + 1, tY + height + 1, 0xFF555555);
        graphics.fill(tX - 5, tY - 5, tX + width + 1, tY - 4, 0xFF555555);
        graphics.fill(tX - 5, tY + height, tX + width + 1, tY + height + 1, 0xFF555555);
        
        graphics.drawString(this.font, tooltip.title, tX, tY, 0xFFFFFFFF, true);
        for (int i = 0; i < tooltip.lore.length; i++) {
            graphics.drawString(this.font, tooltip.lore[i], tX, tY + (i + 1) * 10, 0xFFFFFFFF, true);
        }
    }

    private static class HoveredTooltip {
        String title;
        String[] lore;
        int x, y;

        HoveredTooltip(String title, int x, int y, String... lore) {
            this.title = title;
            this.lore = lore;
            this.x = x;
            this.y = y;
        }
    }

    private ItemStack getIconForArea(String area) {
        String name = area.toLowerCase();
        if (name.contains("island")) return Items.GRASS_BLOCK.getDefaultInstance();
        if (name.contains("hub")) return Items.FILLED_MAP.getDefaultInstance();
        if (name.contains("garden")) return Items.SUNFLOWER.getDefaultInstance();
        if (name.contains("crimson") || name.contains("isle")) return Items.NETHERRACK.getDefaultInstance();
        if (name.contains("end")) return Items.END_STONE.getDefaultInstance();
        if (name.contains("spider")) return Items.SPIDER_EYE.getDefaultInstance();
        if (name.contains("park")) return Items.OAK_SAPLING.getDefaultInstance();
        if (name.contains("gold")) return Items.GOLD_ORE.getDefaultInstance();
        if (name.contains("deep") || name.contains("cavern")) return Items.IRON_ORE.getDefaultInstance();
        if (name.contains("dwarven")) return Items.PRISMARINE_CRYSTALS.getDefaultInstance();
        if (name.contains("crystal")) return Items.AMETHYST_CLUSTER.getDefaultInstance();
        if (name.contains("barn")) return Items.WHEAT.getDefaultInstance();
        if (name.contains("mushroom")) return Items.RED_MUSHROOM.getDefaultInstance();
        if (name.contains("dungeon")) return Items.WITHER_SKELETON_SKULL.getDefaultInstance();
        if (name.contains("lobby")) return Items.BEACON.getDefaultInstance();
        if (name.contains("limbo")) return Items.FIREWORK_STAR.getDefaultInstance();
        
        return Items.PAPER.getDefaultInstance();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        int x = (this.width - xSize) / 2;
        int y = (this.height - ySize) / 2;
        int startX = x + 8;
        int startY = y + 18;
        
        double mouseX = event.x();
        double mouseY = event.y();
        
        // Navigation Buttons
        int closeX = startX + 4 * 18;
        int closeY = startY + 5 * 18;
        if (mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16) {
            this.onClose();
            return true;
        }
        
        if (currentMode != ViewMode.AREAS) {
            int backX = startX + 3 * 18;
            int backY = startY + 5 * 18;
            if (mouseX >= backX && mouseX < backX + 16 && mouseY >= backY && mouseY < backY + 16) {
                if (currentMode == ViewMode.DAY_DETAIL) {
                    currentMode = ViewMode.DAYS;
                    calculateEntries();
                } else if (currentMode == ViewMode.DAYS || currentMode == ViewMode.SUB_AREAS) {
                    currentMode = ViewMode.AREAS;
                    calculateEntries();
                }
                return true;
            }
        }
        
        if (currentMode == ViewMode.AREAS) {
            int dailyX = startX + 5 * 18;
            int dailyY = startY + 5 * 18;
            if (mouseX >= dailyX && mouseX < dailyX + 16 && mouseY >= dailyY && mouseY < dailyY + 16) {
                currentMode = ViewMode.DAYS;
                calculateEntries();
                return true;
            }

            // Click an area to see subareas
            for (int i = 0; i < Math.min(viewItems.size(), 27); i++) {
                int row = (i / 9) + 1;
                int col = i % 9;
                int slotX = startX + col * 18;
                int slotY = startY + row * 18;
                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    selectedAreaName = viewItems.get(i).name;
                    currentMode = ViewMode.SUB_AREAS;
                    calculateEntries();
                    return true;
                }
            }
        }
        
        // Item clicks
        if (currentMode == ViewMode.DAYS) {
            for (int i = 0; i < Math.min(viewItems.size(), 27); i++) {
                int row = (i / 9) + 1;
                int col = i % 9;
                int slotX = startX + col * 18;
                int slotY = startY + row * 18;
                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    selectedDate = viewItems.get(i).name;
                    currentMode = ViewMode.DAY_DETAIL;
                    calculateEntries();
                    return true;
                }
            }
        }
        
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class ViewItem {
        String name;
        PlaytimeTracker.AreaData data;
        long sortValue;
        long dayAfkTime;

        ViewItem(String name, PlaytimeTracker.AreaData data, long sortValue) {
            this.name = name;
            this.data = data;
            this.sortValue = sortValue;
        }
    }
}
