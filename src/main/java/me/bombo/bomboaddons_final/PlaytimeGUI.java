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

    private List<PlaytimeEntry> entries;
    private long totalPlaytime = 0;
    private long totalAfkTime = 0;
    private Map<String, Long> globalDaily = new HashMap<>();

    public PlaytimeGUI() {
        super(Component.literal("Detailed /playtime"));
        calculateEntries();
    }

    private void calculateEntries() {
        Map<String, PlaytimeTracker.AreaData> map = PlaytimeTracker.getAreaDataMap();
        entries = new ArrayList<>();
        totalPlaytime = 0;
        totalAfkTime = 0;
        globalDaily = new HashMap<>();

        for (Map.Entry<String, PlaytimeTracker.AreaData> entry : map.entrySet()) {
            PlaytimeEntry e = new PlaytimeEntry(entry.getKey(), entry.getValue());
            entries.add(e);
            totalPlaytime += e.data.totalTime;
            totalAfkTime += e.data.afkTime;
            
            // Aggregate global daily stats
            for (Map.Entry<String, Long> daily : e.data.dailyTime.entrySet()) {
                globalDaily.put(daily.getKey(), globalDaily.getOrDefault(daily.getKey(), 0L) + daily.getValue());
            }
        }

        // Sort by total playtime descending
        entries.sort((a, b) -> Long.compare(b.data.totalTime, a.data.totalTime));
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
        calculateEntries(); // Update real-time
        
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
        for (int i = 0; i < Math.min(entries.size(), 27); i++) {
            PlaytimeEntry entry = entries.get(i);
            int row = (i / 9) + 1;
            int col = i % 9;
            int slotX = startX + col * 18;
            int slotY = startY + row * 18;
            
            ItemStack icon = getIconForArea(entry.name);
            graphics.renderItem(icon, slotX, slotY);
            
            if (isMouseOver(slotX, slotY, mouseX, mouseY)) {
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                hovered = new HoveredTooltip("§a" + entry.name, mouseX, mouseY,
                    "§7Playtime: §a" + PlaytimeTracker.formatTime(entry.data.totalTime) + " §8(" + PlaytimeTracker.formatTime(entry.data.sessionTime) + ")",
                    "§7AFK Time: §c" + PlaytimeTracker.formatTime(entry.data.afkTime) + " §8(" + PlaytimeTracker.formatTime(entry.data.sessionAfkTime) + ")",
                    "§7Active: §b" + PlaytimeTracker.formatTime(entry.data.totalTime - entry.data.afkTime),
                    "",
                    "§e§lAverages:",
                    "§7Daily: §f" + getAverage(entry.data.dailyTime, 1),
                    "§7Weekly: §f" + getAverage(entry.data.dailyTime, 7),
                    "§7Monthly: §f" + getAverage(entry.data.dailyTime, 30)
                );
            }
        }
        
        // Current Session Info
        int sessionX = startX + 7 * 18;
        int sessionY = startY + 4 * 18;
        graphics.renderItem(Items.GOLD_NUGGET.getDefaultInstance(), sessionX, sessionY);
        if (isMouseOver(sessionX, sessionY, mouseX, mouseY)) {
            graphics.fill(sessionX, sessionY, sessionX + 16, sessionY + 16, 0x80FFFFFF);
            hovered = new HoveredTooltip("§dCurrent Session", mouseX, mouseY,
                "§7Elapsed: §e" + PlaytimeTracker.formatTime(PlaytimeTracker.getSessionTime()),
                "§7Current Area: §f" + BomboaddonsClient.currentArea,
                "§7Status: " + (PlaytimeTracker.isAfk() ? "§cAFK" : "§aActive")
            );
        }

        // Close Button
        int closeX = startX + 4 * 18;
        int closeY = startY + 5 * 18;
        graphics.renderItem(Items.BARRIER.getDefaultInstance(), closeX, closeY);
        if (isMouseOver(closeX, closeY, mouseX, mouseY)) {
            graphics.fill(closeX, closeY, closeX + 16, closeY + 16, 0x80FFFFFF);
            hovered = new HoveredTooltip("§cClose", mouseX, mouseY);
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
        
        // Close button check
        int closeX = startX + 4 * 18;
        int closeY = startY + 5 * 18;
        if (event.x() >= closeX && event.x() < closeX + 16 && event.y() >= closeY && event.y() < closeY + 16) {
            this.onClose();
            return true;
        }
        
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class PlaytimeEntry {
        public String name;
        public PlaytimeTracker.AreaData data;

        public PlaytimeEntry(String name, PlaytimeTracker.AreaData data) {
            this.name = name;
            this.data = data;
        }
    }
}
