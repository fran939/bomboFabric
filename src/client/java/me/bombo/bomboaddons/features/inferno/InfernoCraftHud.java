package me.bombo.bomboaddons.features.inferno;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.PerformanceProfiler;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * On-screen HUD displaying live Inferno Fuel craft progress,
 * remaining materials to buy on Bazaar, and next recipe step.
 * Renderable in-game and inside Bazaar screens with interactive clicks.
 */
public class InfernoCraftHud {

    public static class ClickableRegion {
        public int x1, y1, x2, y2;
        public Runnable action;

        public ClickableRegion(int x1, int y1, int x2, int y2, Runnable action) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.action = action;
        }
    }

    private static final List<ClickableRegion> CLICK_REGIONS = new ArrayList<>();
    private static int lastRenderedHeight = 110;

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "inferno_craft_hud"), InfernoCraftHud::render);
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.infernoCraftHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyToggleGui.isDown()) return;

        // When inside AbstractContainerScreen, renderDirect is called from AbstractContainerScreenMixin instead
        if (mc.gui.screen() instanceof AbstractContainerScreen) {
            return;
        }

        boolean isMoving = mc.gui.screen() instanceof HudMoveScreen;
        if (!isMoving) {
            if (s.infernoCraftHudOnlyIsland && !SkyblockUtils.isInPrivateIsland()) return;
            if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
                return;
            }
        }

        try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("HUD: Inferno Fuel Craft")) {
            Font font = mc.font;
            float scale = s.infernoCraftHudScale > 0 ? s.infernoCraftHudScale : 1.0f;
            int x = s.infernoCraftHudX;
            int y = s.infernoCraftHudY;

            g.pose().pushMatrix();
            g.pose().translate((float) x, (float) y);
            g.pose().scale(scale, scale);

            drawHud(g, font, 0, 0, -1, -1, false);

            g.pose().popMatrix();
        }
    }

    /**
     * Called directly from AbstractContainerScreenMixin to render overlay inside Bazaar.
     */
    public static void renderDirect(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.infernoCraftHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyToggleGui.isDown()) return;

        Font font = mc.font;
        float scale = s.infernoCraftHudScale > 0 ? s.infernoCraftHudScale : 1.0f;
        int x = s.infernoCraftHudX;
        int y = s.infernoCraftHudY;

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);

        // Transform mouse coordinates into HUD local space
        double localMouseX = (mouseX - x) / (double) scale;
        double localMouseY = (mouseY - y) / (double) scale;

        drawHud(g, font, 0, 0, (int) localMouseX, (int) localMouseY, true);

        g.pose().popMatrix();
    }

    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.infernoCraftHudEnabled) return false;

        float scale = s.infernoCraftHudScale > 0 ? s.infernoCraftHudScale : 1.0f;
        int hudX = s.infernoCraftHudX;
        int hudY = s.infernoCraftHudY;

        double localX = (mouseX - hudX) / (double) scale;
        double localY = (mouseY - hudY) / (double) scale;

        synchronized (CLICK_REGIONS) {
            for (ClickableRegion r : CLICK_REGIONS) {
                if (localX >= r.x1 && localX <= r.x2 && localY >= r.y1 && localY <= r.y2) {
                    if (r.action != null) {
                        r.action.run();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void drawHud(GuiGraphicsExtractor g, Font font, int x, int y) {
        drawHud(g, font, x, y, -1, -1, false);
    }

    public static void drawHud(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, int mouseY, boolean trackClicks) {
        if (trackClicks) {
            synchronized (CLICK_REGIONS) {
                CLICK_REGIONS.clear();
            }
        }

        InfernoFuelCraftHelper.CraftPlan plan = InfernoFuelCraftHelper.computePlan();
        int w = getHudWidth();

        int curY = y;

        // Top Title Card (e.g. Fuel Craft Planner)
        int headH = 24;
        boolean headHov = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + headH;
        g.fill(x, curY, x + w, curY + headH, headHov ? 0xDD1E293B : 0xCC0F172A);
        g.outline(x, curY, w, headH, headHov ? 0xFF60A5FA : 0xFF334155);

        g.text(font, "§6§l✦ Fuel Craft Helper §8(" + plan.targetFuels + " Fuels)", x + 7, curY + 5, 0xFFFFFFFF, true);
        String buyStatus = plan.totalMissingRawTypes == 0 ? "§a✔ Ready" : "§e" + plan.totalMissingRawTypes + " to buy";
        g.text(font, buyStatus, x + w - font.width(buyStatus) - 7, curY + 5, 0xFFFFFFFF, false);

        if (trackClicks) {
            addClickRegion(x, curY, x + w, curY + headH, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.connection.sendCommand("b inferno craft");
                }
            });
        }

        curY += headH + 4;

        // Draw Stages matching screenshot pattern:
        // Craft <ItemName> (<amount needed>)
        //   <count>x <Ingredient> <cost> [icon]
        //   Add to craft material collector! / [Click for /viewrecipe]

        // Stage 1: Sulphuric Coal
        curY = drawCraftStage(g, font, x, curY, w, mouseX, mouseY, trackClicks,
                "SULPHURIC_COAL", "Sulphuric Coal", "🔥",
                getIntermediateCount(plan, "SULPHURIC_COAL"),
                List.of(
                        new IngredientLine("ENCHANTED_COAL", "Enchanted Coal", "⚫", "enchanted coal", 16, getRawCount(plan, "ENCHANTED_COAL")),
                        new IngredientLine("ENCHANTED_SULPHUR", "Enchanted Sulphur", "🟡", "enchanted sulphur", 1, getRawCount(plan, "ENCHANTED_SULPHUR"))
                ));

        // Stage 2: Fuel Gabagool
        curY = drawCraftStage(g, font, x, curY, w, mouseX, mouseY, trackClicks,
                "FUEL_GABAGOOL", "Fuel Gabagool", "⛽",
                getIntermediateCount(plan, "FUEL_GABAGOOL"),
                List.of(
                        new IngredientLine("VERY_CRUDE_GABAGOOL", "Very Crude Gabagool", "🍖", "very crude gabagool", 1, getRawCount(plan, "VERY_CRUDE_GABAGOOL")),
                        new IngredientLine("SULPHURIC_COAL", "Sulphuric Coal", "🔥", "sulphuric coal", 8, getIntermediateHave(plan, "SULPHURIC_COAL"))
                ));

        // Stage 3: Heavy Gabagool
        curY = drawCraftStage(g, font, x, curY, w, mouseX, mouseY, trackClicks,
                "HEAVY_GABAGOOL", "Heavy Gabagool", "📦",
                getIntermediateCount(plan, "HEAVY_GABAGOOL"),
                List.of(
                        new IngredientLine("FUEL_GABAGOOL", "Fuel Gabagool", "⛽", "fuel gabagool", 24, getIntermediateHave(plan, "FUEL_GABAGOOL")),
                        new IngredientLine("SULPHURIC_COAL", "Sulphuric Coal", "🔥", "sulphuric coal", 1, getIntermediateHave(plan, "SULPHURIC_COAL"))
                ));

        // Stage 4: Hypergolic Gabagool
        curY = drawCraftStage(g, font, x, curY, w, mouseX, mouseY, trackClicks,
                "HYPERGOLIC_GABAGOOL", "Hypergolic Gabagool", "⚡",
                getIntermediateCount(plan, "HYPERGOLIC_GABAGOOL"),
                List.of(
                        new IngredientLine("HEAVY_GABAGOOL", "Heavy Gabagool", "📦", "heavy gabagool", 12, getIntermediateHave(plan, "HEAVY_GABAGOOL")),
                        new IngredientLine("SULPHURIC_COAL", "Sulphuric Coal", "🔥", "sulphuric coal", 1, getIntermediateHave(plan, "SULPHURIC_COAL"))
                ));

        // Stage 5: Hypergolic Inferno Fuel
        curY = drawCraftStage(g, font, x, curY, w, mouseX, mouseY, trackClicks,
                "INFERNO_HYPERGOLIC_CRUDE_GABAGOOL", "Hypergolic Fuel", "🔥",
                new StageAmount(plan.targetFuels, 0),
                List.of(
                        new IngredientLine("HYPERGOLIC_GABAGOOL", "Hypergolic Gabagool", "⚡", "hypergolic gabagool", 1, getIntermediateHave(plan, "HYPERGOLIC_GABAGOOL")),
                        new IngredientLine("INFERNO_FUEL_BLOCK", "Inferno Fuel Block", "🧱", "inferno fuel block", 2, getRawCount(plan, "INFERNO_FUEL_BLOCK")),
                        new IngredientLine("CRUDE_GABAGOOL_DISTILLATE", "Crude Distillate", "🧪", "crude gabagool distillate", 6, getRawCount(plan, "CRUDE_GABAGOOL_DISTILLATE"))
                ));

        lastRenderedHeight = Math.max(50, curY - y);
    }

    private static class StageAmount {
        public int totalNeeded;
        public int toCraft;
        public StageAmount(int totalNeeded, int toCraft) {
            this.totalNeeded = totalNeeded;
            this.toCraft = toCraft;
        }
    }

    private static class IngredientLine {
        public String id;
        public String name;
        public String icon;
        public String bzCommand;
        public int recipeRatio;
        public InfernoFuelCraftHelper.CraftItem item;

        public IngredientLine(String id, String name, String icon, String bzCmd, int ratio, InfernoFuelCraftHelper.CraftItem item) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.bzCommand = bzCmd;
            this.recipeRatio = ratio;
            this.item = item;
        }
    }

    private static int drawCraftStage(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY, boolean trackClicks,
                                      String stageId, String stageName, String stageIcon, StageAmount amount, List<IngredientLine> ingredients) {
        boolean isFinished = amount.toCraft <= 0 && !"INFERNO_HYPERGOLIC_CRUDE_GABAGOOL".equals(stageId);

        // If stage is already completed, render as a sleek single-line checkmark
        if (isFinished) {
            int rowH = 18;
            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + rowH;
            g.fill(x, y, x + w, y + rowH, hov ? 0x2510B981 : 0x1510B981);
            g.outline(x, y, w, rowH, hov ? 0xFF10B981 : 0x4010B981);
            g.text(font, "§a✔ " + stageName + " §7(Ready)", x + 7, y + 5, 0xFF10B981, false);

            String vr = "§b/viewrecipe";
            g.text(font, vr, x + w - font.width(vr) - 7, y + 5, 0xFF60A5FA, false);

            if (trackClicks) {
                addClickRegion(x, y, x + w, y + rowH, () -> executeViewRecipe(stageId));
            }
            return y + rowH + 3;
        }

        // Active stage card (pattern from screenshot)
        int cardH = 22 + ingredients.size() * 16 + 18;
        boolean cardHov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + cardH;

        g.fill(x, y, x + w, y + cardH, 0xD00F172A);
        g.outline(x, y, w, cardH, cardHov ? 0xFFFBBF24 : 0xFF334155);

        // Header: Craft <Item> (<Amount Needed>)
        String header = "§fCraft §9" + stageName + " §8(§e" + InfernoFuelCraftHelper.formatNum(amount.totalNeeded) + "§8)";
        g.text(font, header, x + 7, y + 6, 0xFFFFFFFF, true);

        // Top right: /viewrecipe button
        int vrW = 68;
        int vrH = 14;
        int vrX = x + w - vrW - 6;
        int vrY = y + 4;
        boolean vrHov = mouseX >= vrX && mouseX <= vrX + vrW && mouseY >= vrY && mouseY <= vrY + vrH;
        g.fill(vrX, vrY, vrX + vrW, vrY + vrH, vrHov ? 0xFF2563EB : 0xFF1E293B);
        g.outline(vrX, vrY, vrW, vrH, vrHov ? 0xFF60A5FA : 0xFF475569);
        g.centeredText(font, "§bView Recipe", vrX + vrW / 2, vrY + 3, 0xFFFFFFFF);

        if (trackClicks) {
            addClickRegion(vrX, vrY, vrX + vrW, vrY + vrH, () -> executeViewRecipe(stageId));
        }

        // Ingredient lines
        int curY = y + 22;
        boolean allIngredientsReady = true;

        for (IngredientLine ing : ingredients) {
            int neededForStage = ing.item != null ? ing.item.needed : ing.recipeRatio;
            int have = ing.item != null ? ing.item.getTotalHave() : 0;
            int toBuy = ing.item != null ? ing.item.toBuy : Math.max(0, neededForStage - have);
            if (toBuy > 0) allIngredientsReady = false;

            boolean rowHov = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + 15;
            if (rowHov) {
                g.fill(x + 4, curY - 1, x + w - 4, curY + 14, 0x30374151);
            }

            // Line text: 16x Enchanted Coal 19k [icon]
            String countPrefix = "§7" + InfernoFuelCraftHelper.formatNum(neededForStage) + "x ";
            String nameText = (toBuy == 0 ? "§a" : "§f") + ing.name;
            long cost = (long) neededForStage * InfernoFuelCraftHelper.getBazaarPrice(ing.id);
            String costText = " §6" + InfernoMinionTracker.formatCoins(cost);

            g.text(font, countPrefix + nameText + costText, x + 10, curY + 2, 0xFFFFFFFF, false);

            // Icon / status at right
            String status = toBuy == 0 ? "§a✔ " + ing.icon : "§c" + InfernoFuelCraftHelper.formatNum(toBuy) + "x " + ing.icon;
            g.text(font, status, x + w - font.width(status) - 8, curY + 2, 0xFFFFFFFF, false);

            if (trackClicks) {
                final IngredientLine targetIng = ing;
                addClickRegion(x + 4, curY - 1, x + w - 4, curY + 14, () -> {
                    if (targetIng.item != null) {
                        InfernoFuelCraftHelper.executeBazaarAction(targetIng.item);
                    } else {
                        executeBazaarSearch(targetIng.bzCommand, toBuy);
                    }
                });
            }

            curY += 15;
        }

        // Bottom Action prompt
        if (allIngredientsReady) {
            g.text(font, "§a✔ Add to craft material collector! §b(Click to craft)", x + 7, curY + 3, 0xFF10B981, false);
            if (trackClicks) {
                addClickRegion(x, curY, x + w, curY + 16, () -> executeViewRecipe(stageId));
            }
        } else {
            g.text(font, "§eClick any material to /bz & copy buy amount", x + 7, curY + 3, 0xFFFCD34D, false);
        }

        return y + cardH + 4;
    }

    private static void executeViewRecipe(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand("viewrecipe " + itemId);
            mc.player.sendSystemMessage(Component.literal("§8[§6🔥 Craft Helper§8] §aOpened §e/viewrecipe " + itemId + "§a!"));
        }
    }

    private static void executeBazaarSearch(String searchWords, int toBuy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String clean = searchWords != null ? searchWords.replace("_", " ").trim() : "";
        if (toBuy > 0) {
            int toCopy = Math.min(toBuy, InfernoFuelCraftHelper.BAZAAR_ORDER_LIMIT);
            mc.keyboardHandler.setClipboard(String.valueOf(toCopy));
            mc.player.connection.sendCommand("bz " + clean);
            mc.player.sendSystemMessage(Component.literal("§8[§6🔥 Craft Helper§8] §aOpened §e/bz " + clean + " §aand copied §b" + InfernoFuelCraftHelper.formatNum(toCopy) + " §ato clipboard!"));
        } else {
            mc.player.connection.sendCommand("bz " + clean);
        }
    }

    private static void addClickRegion(int x1, int y1, int x2, int y2, Runnable action) {
        synchronized (CLICK_REGIONS) {
            CLICK_REGIONS.add(new ClickableRegion(x1, y1, x2, y2, action));
        }
    }

    private static StageAmount getIntermediateCount(InfernoFuelCraftHelper.CraftPlan plan, String id) {
        for (InfernoFuelCraftHelper.CraftItem it : plan.intermediates) {
            if (it.id.equalsIgnoreCase(id)) {
                return new StageAmount(it.needed, it.toCraft);
            }
        }
        return new StageAmount(1, 1);
    }

    private static InfernoFuelCraftHelper.CraftItem getIntermediateHave(InfernoFuelCraftHelper.CraftPlan plan, String id) {
        for (InfernoFuelCraftHelper.CraftItem it : plan.intermediates) {
            if (it.id.equalsIgnoreCase(id)) return it;
        }
        return null;
    }

    private static InfernoFuelCraftHelper.CraftItem getRawCount(InfernoFuelCraftHelper.CraftPlan plan, String id) {
        for (InfernoFuelCraftHelper.CraftItem it : plan.rawMaterials) {
            if (it.id.equalsIgnoreCase(id)) return it;
        }
        return null;
    }

    public static int getHudWidth() {
        return 225;
    }

    public static int getHudHeight() {
        return lastRenderedHeight;
    }
}
