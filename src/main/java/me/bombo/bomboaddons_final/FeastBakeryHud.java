package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public class FeastBakeryHud {
    private static final List<DetectedItem> DETECTED_ITEMS = new ArrayList<>();

    private static long lastRenderFrame = -1;
    private static boolean lastInMenu = false;
    private static long lastScanTime = 0;

    public static void onHudRender(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        long currentFrame = System.currentTimeMillis();
        // Prevent double rendering in the same frame
        if (currentFrame == lastRenderFrame && !(mc.screen instanceof HudMoveScreen)) return;
        lastRenderFrame = currentFrame;

        BomboConfig.Settings s = BomboConfig.get();
        if (!s.feastBakeryHud) return;

        LowestBinManager.ensureLoaded();

        boolean inMenu = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString();
            if (title.toLowerCase().contains("bakery")) {
                inMenu = true;
                if (!lastInMenu) {
                    if (s.debugMaster) Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Bakery GUI detected: §f" + title);
                }
                // Scan every 250ms to avoid constant clearing/refilling
                if (currentFrame - lastScanTime > 250) {
                    scanMenu(screen);
                    lastScanTime = currentFrame;
                }
            }
        }
        lastInMenu = inMenu;

        if (!inMenu && !(mc.screen instanceof HudMoveScreen)) {
            DETECTED_ITEMS.clear();
            return;
        }

        // If in HudMoveScreen and empty, show a dummy
        if (mc.screen instanceof HudMoveScreen && DETECTED_ITEMS.isEmpty()) {
            DETECTED_ITEMS.add(new DetectedItem("FRESHLY_BAKED_TALISMAN", "Baked Talisman", 25));
            DETECTED_ITEMS.add(new DetectedItem("POPCORN_RING", "Popcorn Ring", 125));
            DETECTED_ITEMS.add(new DetectedItem("ENCHANTMENT_FEAST_1", "Enchanted Book (Feast I)", 500));
        }

        if (DETECTED_ITEMS.isEmpty()) return;

        // Sort: Highest coins/kernel to lowest
        // We sort a copy to avoid concurrent modification if scanMenu runs
        List<DetectedItem> toDraw = new ArrayList<>(DETECTED_ITEMS);
        toDraw.sort((a, b) -> {
            long pA = LowestBinManager.getCachedPrice(a.id);
            long pB = LowestBinManager.getCachedPrice(b.id);
            double cpkA = a.kernelCost > 0 ? (double) pA / a.kernelCost : 0;
            double cpkB = b.kernelCost > 0 ? (double) pB / b.kernelCost : 0;
            return Double.compare(cpkB, cpkA);
        });

        drawBakeryInfo(g, s.feastBakeryHudX, s.feastBakeryHudY, toDraw);
    }

    private static void scanMenu(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
        List<DetectedItem> newList = new ArrayList<>();
        net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
        for (int i = 0; i < 54; i++) {
            if (i >= menu.slots.size()) break;
            net.minecraft.world.inventory.Slot slot = menu.getSlot(i);
            if (!slot.hasItem()) continue;
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            
            int kernels = getKernelCost(stack);
            if (kernels > 0) {
                String id = SkyblockUtils.getInternalId(stack);
                String name = stack.getHoverName().getString();
                
                // Bruteforce for Enchanted Books if the NBT parsing failed
                if (name.equals("Enchanted Book") || id.equals("ENCHANTED_BOOK")) {
                    List<Component> lore = SkyblockUtils.getLore(stack);
                    for (Component line : lore) {
                        String text = line.getString().replaceAll("(?i)§.", "").trim();
                        if (text.startsWith("Feast ")) {
                            id = "ENCHANTMENT_FEAST_" + text.replace("Feast ", "").trim();
                            name = text;
                            break;
                        }
                    }
                }

                if (BomboConfig.get().debugMaster) {
                    Bomboaddons.sendMessage("§7[Debug] Bakery Item: §f" + name + " §7(ID: §b" + id + "§7, Cost: §e" + kernels + "§7)");
                }
                newList.add(new DetectedItem(id, name, kernels));
            }
        }
        
        DETECTED_ITEMS.clear();
        DETECTED_ITEMS.addAll(newList);
    }

    private static int getKernelCost(net.minecraft.world.item.ItemStack stack) {
        try {
            List<Component> lore = SkyblockUtils.getLore(stack);
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i).getString().replaceAll("(?i)§.", "").trim();
                // Check if line starts with "Cost" (to handle "Cost:")
                if (line.toLowerCase().startsWith("cost")) {
                    if (i + 1 < lore.size()) {
                        String nextLine = lore.get(i + 1).getString().replaceAll("(?i)§.", "").trim();
                        if (nextLine.toLowerCase().contains("kernels")) {
                            try {
                                String costStr = nextLine.toLowerCase().replace("kernels", "").trim().replace(",", "");
                                return Integer.parseInt(costStr);
                            } catch (NumberFormatException e) {
                                String digits = nextLine.replaceAll("[^0-9]", "");
                                if (!digits.isEmpty()) return Integer.parseInt(digits);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return -1;
    }

    public static void drawBakeryInfo(GuiGraphics g, int x, int y, List<DetectedItem> items) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Kernels are untradeable, so we don't need a market price for them.
        int playerKernels = -1;

        int width = 185;
        int height = (items.size() + 2) * 10 + 10;

        // Premium background: High contrast for visibility
        g.fill(x - 5, y - 5, x + width + 5, y + height + 5, 0xCD000000); // 80% black
        g.renderOutline(x - 5, y - 5, width + 10, height + 10, 0xFFFFFFFF); // Pure white border

        // Header
        g.drawString(font, "§6§lFeast Bakery §r§7- §bScott", x, y, 0xFFFFFFFF, true);
        
        // Separator line
        g.fill(x, y + 11, x + width, y + 12, 0xAAFFFFFF);

        int curY = y + 18;
        for (DetectedItem item : items) {
            long itemPrice = LowestBinManager.getCachedPrice(item.id);
            // Bruteforce/Fallback prices if API fails
            if (itemPrice <= 0) {
                if (item.id.startsWith("ENCHANTMENT_FEAST_")) {
                    itemPrice = 4500000; // 4.5M fallback
                } else if (item.id.equals("BREAD_BOWL")) {
                    itemPrice = 1500000; // 1.5M fallback
                }
            }
            double coinsPerKernel = item.kernelCost > 0 ? (double) itemPrice / item.kernelCost : 0;

            String priceColor = "§a";
            if (itemPrice <= 0) priceColor = "§7"; // Gray if no price
            else if (coinsPerKernel < 5000) priceColor = "§c";
            else if (coinsPerKernel < 10000) priceColor = "§e";

            String displayName = item.name;
            if (item.id.startsWith("ENCHANTMENT_")) {
                String enchantName = item.id.replace("ENCHANTMENT_", "");
                // Convert FEAST_1 to Feast 1 or Feast I
                String[] parts = enchantName.split("_");
                if (parts.length >= 2) {
                    String prettyName = parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1).toLowerCase();
                    displayName = prettyName + " " + parts[1];
                }
            }
            
            String nameText = "§f" + (displayName.length() > 20 ? displayName.substring(0, 18) + ".." : displayName);
            String valueText = itemPrice <= 0 ? "§8N/A" : priceColor + LowestBinManager.formatPrice((long) coinsPerKernel) + "§7/k";
            
            g.drawString(font, nameText, x, curY, 0xFFFFFFFF, true);
            int valueWidth = font.width(valueText.replaceAll("(?i)§.", ""));
            g.drawString(font, valueText, x + width - valueWidth, curY, 0xFFFFFFFF, true);
            
            curY += 10;
        }

        // Show Scott's Bakery footer
        g.fill(x, curY + 2, x + width, curY + 3, 0x66FFFFFF);
        
        String bottomText = "§7Scott's Bakery";
        g.drawString(font, bottomText, x + (width / 2) - (font.width(bottomText.replaceAll("(?i)§.", "")) / 2), curY + 5, 0xFFFFFFFF, true);
    }

    public static class DetectedItem {
        public String id;
        public String name;
        public int kernelCost;

        public DetectedItem(String id, String name, int kernelCost) {
            this.id = id;
            this.name = name;
            this.kernelCost = kernelCost;
        }
    }
}
