package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TabWidgetHud {

    public static final List<String> COMMON_WIDGETS = List.of(
        "Area", "Profile", "Pet", "Stats", "Composter", "Pests", "Crop Milestones", "Visitors", "Jacob's Contest", "Pest Traps", "Active Effects", "Skills", "Daily Quests", "Coop", "Minions", "Event"
    );

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "tab_widget_hud"), TabWidgetHud::render);
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s.tabWidgets == null || s.tabWidgets.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;
        if (client.screen != null && !(client.screen instanceof HudMoveScreen)) return;

        String currentLocation = SkyblockUtils.getLocation();

        for (BomboConfig.TabWidgetInfo widget : s.tabWidgets) {
            if (!widget.enabled) continue;

            // Island filtering
            if (!widget.island.equalsIgnoreCase("All") && !widget.island.equalsIgnoreCase("Any")) {
                if (!currentLocation.equalsIgnoreCase(widget.island) && !currentLocation.toLowerCase().contains(widget.island.toLowerCase())) {
                    continue;
                }
            }

            List<Component> lines = getMatchedWidgetComponents(widget.name, false);
            if (!lines.isEmpty()) {
                drawWidgetComponents(g, widget.x, widget.y, widget.scale, lines);
            }
        }
    }

    public static List<String> getAvailableTabWidgets() {
        List<String> headers = new ArrayList<>();
        List<Component> tabLines = SkyblockUtils.getTabListLines();
        for (Component comp : tabLines) {
            String raw = comp.getString();
            String clean = raw.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
            if (clean.isEmpty()) continue;

            if (clean.endsWith(":") && !clean.startsWith("Next ") && !clean.startsWith("Plots:") && !clean.startsWith("Alive:") && !clean.startsWith("Spray:") && !clean.startsWith("Repellent:") && !clean.startsWith("Bonus:") && !clean.startsWith("Cooldown:") && !clean.startsWith("Full Traps:") && !clean.startsWith("No Bait:")) {
                if (!raw.startsWith("  ") && !raw.startsWith("   ") && !raw.startsWith("\t")) {
                    String title = clean.substring(0, clean.length() - 1).trim();
                    if (!headers.contains(title)) {
                        headers.add(title);
                    }
                }
            }
        }
        return headers;
    }

    public static List<Component> getMatchedWidgetComponents(String query, boolean isDummy) {
        if (isDummy) {
            List<Component> dummy = new ArrayList<>();
            String name = query == null ? "Widget" : query;
            String lower = name.toLowerCase();

            if (lower.contains("visitor")) {
                dummy.add(Component.literal("§b§lVisitors: §7(3)"));
                dummy.add(Component.literal(" §cSpaceman §dNEW!"));
                dummy.add(Component.literal(" §aLynn"));
                dummy.add(Component.literal(" §aOdawa"));
                dummy.add(Component.literal(" §fNext Visitor: §b9s"));
            } else if (lower.contains("stat")) {
                dummy.add(Component.literal("§e§lStats:"));
                dummy.add(Component.literal(" §fSpeed: §f400"));
                dummy.add(Component.literal(" §fFarming Fortune: §6526"));
                dummy.add(Component.literal(" §fStrength: §c481"));
                dummy.add(Component.literal(" §fBonus Pest Chance: §2190"));
            } else if (lower.contains("crop") || lower.contains("milestone")) {
                dummy.add(Component.literal("§b§lCrop Milestones:"));
                dummy.add(Component.literal(" §fMushroom 46: §c§lMAX"));
                dummy.add(Component.literal(" §fCarrot 46: §c§lMAX"));
            } else if (lower.contains("jacob")) {
                dummy.add(Component.literal("§e§lJacob's Contest:"));
                dummy.add(Component.literal(" §fStarts In: §e4m 51s"));
                dummy.add(Component.literal(" §e○ §fSugar Cane"));
                dummy.add(Component.literal(" §6 §fSunflower"));
            } else if (lower.contains("pest")) {
                dummy.add(Component.literal("§4§lPests:"));
                dummy.add(Component.literal(" §fAlive: §46"));
                dummy.add(Component.literal(" §fPlots: §b10"));
            } else {
                dummy.add(Component.literal("§b§l" + name + ":"));
                dummy.add(Component.literal(" §7Sample Item 1"));
                dummy.add(Component.literal(" §7Sample Item 2"));
            }
            return dummy;
        }

        List<Component> result = new ArrayList<>();
        List<Component> tabLines = SkyblockUtils.getTabListLines();
        if (tabLines.isEmpty() || query == null || query.trim().isEmpty()) return result;

        String search = query.trim().toLowerCase();
        int targetStartIndex = -1;

        // Search linearly through sorted tab lines for matching section title
        for (int i = 0; i < tabLines.size(); i++) {
            String cleanLine = tabLines.get(i).getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
            if (cleanLine.isEmpty()) continue;

            String cleanLower = cleanLine.toLowerCase();
            if (cleanLower.equals(search + ":") || cleanLower.equals(search) || cleanLower.startsWith(search + ":") || cleanLower.startsWith(search + " (")) {
                targetStartIndex = i;
                break;
            }
        }

        if (targetStartIndex == -1) return result;

        // Add header
        result.add(tabLines.get(targetStartIndex));

        // Collect all inner body lines until next section header or unindented line (player list/new section)
        for (int i = targetStartIndex + 1; i < tabLines.size(); i++) {
            Component comp = tabLines.get(i);
            String rawLine = comp.getString();
            String unformattedLine = rawLine.replaceAll("(?i)§[0-9a-fk-or]", "");

            if (unformattedLine.trim().isEmpty()) continue;

            // Sub-entries of tab widgets are indented by Skyblock with leading spaces/tabs.
            // Unindented lines indicate the widget section has ended (e.g. new section header or player list).
            if (!unformattedLine.startsWith(" ") && !unformattedLine.startsWith("\t")) {
                break;
            }

            result.add(comp);
        }

        return result;
    }

    public static List<String> getMatchedWidgetLines(String query, boolean isDummy) {
        List<Component> comps = getMatchedWidgetComponents(query, isDummy);
        List<String> lines = new ArrayList<>();
        for (Component c : comps) {
            lines.add(SkyblockUtils.getFormattedComponentText(c));
        }
        return lines;
    }

    public static void drawWidgetComponents(GuiGraphicsExtractor g, int x, int y, float scale, List<Component> lines) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);

        int curY = 0;
        for (Component line : lines) {
            g.text(Minecraft.getInstance().font, line, 0, curY, 0xFFFFFFFF, true);
            curY += 10;
        }

        g.pose().popMatrix();
    }

    public static void drawWidgetInfo(GuiGraphicsExtractor g, int x, int y, float scale, List<String> lines) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);

        int curY = 0;
        for (String line : lines) {
            g.text(Minecraft.getInstance().font, line, 0, curY, 0xFFFFFFFF, true);
            curY += 10;
        }

        g.pose().popMatrix();
    }

    public static int getWidth() {
        return 140;
    }

    public static int getHeight(int lineCount) {
        return Math.max(20, lineCount * 10);
    }
}
