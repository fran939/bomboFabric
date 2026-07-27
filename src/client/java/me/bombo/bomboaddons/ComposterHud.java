package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ComposterHud {

    private static final Pattern VALUE_PATTERN = Pattern.compile("([\\d,]+(?:\\.\\d+)?)([kmb])?/([\\d,]+(?:\\.\\d+)?)([kmb])?");
    private static final Pattern SPEED_PERCENT_PATTERN = Pattern.compile("speed by (\\d+)%");
    private static final Pattern COST_PERCENT_PATTERN = Pattern.compile("by (\\d+)%");

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "composter_hud"), ComposterHud::render);
    }

    public static void onContainerTick(AbstractContainerScreen<?> screen) {
        if (screen == null) return;
        String title = screen.getTitle().getString();
        if (!title.contains("Composter")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        boolean isUpgradesMenu = title.contains("Upgrades");
        BomboConfig.Settings s = BomboConfig.get();
        boolean configChanged = false;

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            String name = stack.getHoverName().getString();

            List<Component> tooltip = stack.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                    mc.player,
                    TooltipFlag.Default.NORMAL
            );

            if (name.contains("Organic Matter")) {
                if (parseValueSlot(tooltip, true)) configChanged = true;
            } else if (name.contains("Fuel")) {
                if (parseValueSlot(tooltip, false)) configChanged = true;
            }

            if (isUpgradesMenu) {
                if (name.contains("Composter Speed")) {
                    int lvl = parseLevelFromNameOrTooltip(name, tooltip, SPEED_PERCENT_PATTERN, 20);
                    if (lvl >= 0 && s.composterSpeedLevel != lvl) {
                        s.composterSpeedLevel = lvl;
                        configChanged = true;
                    }
                } else if (name.contains("Cost Reduction")) {
                    int lvl = parseLevelFromNameOrTooltip(name, tooltip, COST_PERCENT_PATTERN, 1);
                    if (lvl >= 0 && s.composterCostReductionLevel != lvl) {
                        s.composterCostReductionLevel = lvl;
                        configChanged = true;
                    }
                }
            }
        }

        if (configChanged) {
            s.composterLastSavedTime = System.currentTimeMillis();
            BomboConfig.save();
        }
    }

    private static int parseLevelFromNameOrTooltip(String name, List<Component> tooltip, Pattern percentPattern, int percentPerLevel) {
        String[] parts = name.split(" ");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            try {
                int romanVal = RomanNumber.romanToDecimal(lastPart);
                if (romanVal > 0) return romanVal;
            } catch (Exception ignored) {}
        }

        for (Component line : tooltip) {
            String text = line.getString().replaceAll("§.", "");
            Matcher matcher = percentPattern.matcher(text);
            if (matcher.find()) {
                int pct = Integer.parseInt(matcher.group(1));
                return pct / percentPerLevel;
            }
        }
        return -1;
    }

    private static boolean parseValueSlot(List<Component> tooltip, boolean isOrganic) {
        BomboConfig.Settings s = BomboConfig.get();
        for (Component line : tooltip) {
            String text = line.getString().replaceAll("§.", "");
            Matcher matcher = VALUE_PATTERN.matcher(text);
            if (matcher.find()) {
                double current = parseValue(matcher.group(1), matcher.group(2));
                double max = parseValue(matcher.group(3), matcher.group(4));
                if (isOrganic) {
                    s.composterLastOrganic = current;
                    s.composterLastMaxOrganic = max;
                } else {
                    s.composterLastFuel = current;
                    s.composterLastMaxFuel = max;
                }
                return true;
            }
        }
        return false;
    }

    private static double parseValue(String valStr, String suffix) {
        double val = Double.parseDouble(valStr.replace(",", ""));
        if (suffix != null) {
            switch (suffix.toLowerCase()) {
                case "k" -> val *= 1000;
                case "m" -> val *= 1000000;
                case "b" -> val *= 1000000000;
            }
        }
        return val;
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.composterHud && !s.composterTimerHud) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;
        if (client.screen != null && !(client.screen instanceof HudMoveScreen) && !(client.screen instanceof AbstractContainerScreen<?>)) return;

        if (client.screen instanceof AbstractContainerScreen<?> containerScreen) {
            onContainerTick(containerScreen);
        }

        if (s.composterHud && (s.composterLastOrganic >= 0 || s.composterLastFuel >= 0)) {
            drawComposterInfo(g, s.composterHudX, s.composterHudY, false);
        }

        if (s.composterTimerHud && (s.composterLastOrganic >= 0 || s.composterLastFuel >= 0)) {
            drawComposterTimerInfo(g, s.composterTimerHudX, s.composterTimerHudY, false);
        }
    }

    public static void drawComposterTimerInfo(GuiGraphicsExtractor g, int x, int y, boolean isDummy) {
        BomboConfig.Settings s = BomboConfig.get();

        double baseOrgCur = isDummy ? 1200 : Math.max(0, s.composterLastOrganic);
        double baseFuelCur = isDummy ? 464937.5 : Math.max(0, s.composterLastFuel);

        int speedLvl = s.composterSpeedLevel >= 0 ? s.composterSpeedLevel : (isDummy ? 25 : 0);
        int costLvl = s.composterCostReductionLevel >= 0 ? s.composterCostReductionLevel : (isDummy ? 25 : 0);

        boolean missingUpgrades = (s.composterSpeedLevel < 0 || s.composterCostReductionLevel < 0) && !isDummy;

        double speedMultiplier = 1.0 + (speedLvl * 0.20);
        double timePerCompostSec = 600.0 / speedMultiplier;

        double costMultiplier = 1.0 - (costLvl * 0.01);
        double orgPerCompost = 4000.0 * costMultiplier;
        double fuelPerCompost = 2000.0 * costMultiplier;

        double orgRatePerSec = orgPerCompost / timePerCompostSec;
        double fuelRatePerSec = fuelPerCompost / timePerCompostSec;

        double elapsedSec = (!isDummy && s.composterLastSavedTime > 0) ? (System.currentTimeMillis() - s.composterLastSavedTime) / 1000.0 : 0;

        double usableBaseOrg = Math.max(0, baseOrgCur - orgPerCompost);
        double usableBaseFuel = Math.max(0, baseFuelCur - fuelPerCompost);

        double maxRunningSec = Math.min(
            orgRatePerSec > 0 ? usableBaseOrg / orgRatePerSec : 0,
            fuelRatePerSec > 0 ? usableBaseFuel / fuelRatePerSec : 0
        );

        double actualElapsedSec = Math.min(elapsedSec, maxRunningSec);

        double orgCur = Math.max(0, baseOrgCur - (orgRatePerSec * actualElapsedSec));
        double fuelCur = Math.max(0, baseFuelCur - (fuelRatePerSec * actualElapsedSec));

        // Composter requires at least orgPerCompost and fuelPerCompost to make 1 compost!
        double usableOrg = Math.max(0, orgCur - orgPerCompost);
        double usableFuel = Math.max(0, fuelCur - fuelPerCompost);

        double secondsOrg = (usableOrg > 0 && orgRatePerSec > 0) ? (usableOrg / orgRatePerSec) : 0;
        double secondsFuel = (usableFuel > 0 && fuelRatePerSec > 0) ? (usableFuel / fuelRatePerSec) : 0;

        double timeUntilEmptySec = Math.min(secondsOrg, secondsFuel);

        String line;
        if (missingUpgrades) {
            line = "§eOpen Upgrades Menu!";
        } else if (timeUntilEmptySec <= 0) {
            line = "§cEmpty!";
        } else {
            line = "§c" + formatTime((long) timeUntilEmptySec);
        }

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        float scale = s.composterTimerHudScale;
        g.pose().scale(scale, scale);

        // Draw compost texture item icon
        net.minecraft.world.item.ItemStack compostStack = SkyblockItemManager.createSkyblockItem("COMPOST");
        if (compostStack.isEmpty()) {
            compostStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPOSTER);
        }

        g.pose().pushMatrix();
        g.pose().translate(0f, -2f);
        g.pose().scale(0.75f, 0.75f);
        g.item(compostStack, 0, 0);
        g.pose().popMatrix();

        g.text(Minecraft.getInstance().font, line, 15, 0, 0xFFFFFFFF, true);

        g.pose().popMatrix();
    }

    public static void drawComposterInfo(GuiGraphicsExtractor g, int x, int y, boolean isDummy) {
        BomboConfig.Settings s = BomboConfig.get();

        double baseOrgCur = isDummy ? 1200 : Math.max(0, s.composterLastOrganic);
        double orgMax = isDummy ? 790000 : Math.max(0, s.composterLastMaxOrganic);
        double baseFuelCur = isDummy ? 464937.5 : Math.max(0, s.composterLastFuel);
        double fuelMax = isDummy ? 850000 : Math.max(0, s.composterLastMaxFuel);

        int speedLvl = s.composterSpeedLevel >= 0 ? s.composterSpeedLevel : (isDummy ? 25 : 0);
        int costLvl = s.composterCostReductionLevel >= 0 ? s.composterCostReductionLevel : (isDummy ? 25 : 0);

        boolean missingUpgrades = (s.composterSpeedLevel < 0 || s.composterCostReductionLevel < 0) && !isDummy;

        double speedMultiplier = 1.0 + (speedLvl * 0.20);
        double timePerCompostSec = 600.0 / speedMultiplier;

        double costMultiplier = 1.0 - (costLvl * 0.01);
        double orgPerCompost = 4000.0 * costMultiplier;
        double fuelPerCompost = 2000.0 * costMultiplier;

        double orgRatePerSec = orgPerCompost / timePerCompostSec;
        double fuelRatePerSec = fuelPerCompost / timePerCompostSec;

        double elapsedSec = (!isDummy && s.composterLastSavedTime > 0) ? (System.currentTimeMillis() - s.composterLastSavedTime) / 1000.0 : 0;

        double usableBaseOrg = Math.max(0, baseOrgCur - orgPerCompost);
        double usableBaseFuel = Math.max(0, baseFuelCur - fuelPerCompost);

        double maxRunningSec = Math.min(
            orgRatePerSec > 0 ? usableBaseOrg / orgRatePerSec : 0,
            fuelRatePerSec > 0 ? usableBaseFuel / fuelRatePerSec : 0
        );

        double actualElapsedSec = Math.min(elapsedSec, maxRunningSec);

        double orgCur = Math.max(0, baseOrgCur - (orgRatePerSec * actualElapsedSec));
        double fuelCur = Math.max(0, baseFuelCur - (fuelRatePerSec * actualElapsedSec));

        double usableOrg = Math.max(0, orgCur - orgPerCompost);
        double usableFuel = Math.max(0, fuelCur - fuelPerCompost);

        double secondsOrg = (usableOrg > 0 && orgRatePerSec > 0) ? (usableOrg / orgRatePerSec) : 0;
        double secondsFuel = (usableFuel > 0 && fuelRatePerSec > 0) ? (usableFuel / fuelRatePerSec) : 0;

        double timeUntilEmptySec = Math.min(secondsOrg, secondsFuel);
        boolean orgLimiting = secondsOrg <= secondsFuel;

        List<String> lines = new ArrayList<>();
        lines.add("§a§lComposter Status");
        lines.add("§fOrganic: §e" + formatNum(orgCur) + "§7/§e" + formatNum(orgMax) + " §7(" + String.format("%.1f", orgCur > 0 && orgMax > 0 ? (orgCur/orgMax*100) : 0) + "%)");
        lines.add("§fFuel: §b" + formatNum(fuelCur) + "§7/§b" + formatNum(fuelMax) + " §7(" + String.format("%.1f", fuelCur > 0 && fuelMax > 0 ? (fuelCur/fuelMax*100) : 0) + "%)");

        if (missingUpgrades) {
            lines.add("§cTime Left: §eOpen Upgrades Menu!");
        } else if (timeUntilEmptySec <= 0) {
            lines.add("§cComposter is empty!");
        } else {
            lines.add("§fTime Left: §c" + formatTime((long) timeUntilEmptySec) + (orgCur > 0 || fuelCur > 0 ? " §7(" + (orgLimiting ? "Organic" : "Fuel") + ")" : ""));
        }

        if (s.composterDebug) {
            lines.add("§8[Debug] Speed Lvl: " + speedLvl + " (+" + (int)(speedLvl*20) + "%) | Cost Lvl: " + costLvl + " (-" + costLvl + "%)");
            lines.add("§8[Debug] Elapsed: " + String.format("%.1fs", actualElapsedSec) + " | Rate: " + String.format("%.2f", orgRatePerSec) + " Org/s, " + String.format("%.2f", fuelRatePerSec) + " Fuel/s");
        }

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        float scale = s.composterHudScale;
        g.pose().scale(scale, scale);

        // Draw compost texture item icon for header
        net.minecraft.world.item.ItemStack compostStack = SkyblockItemManager.createSkyblockItem("COMPOST");
        if (compostStack.isEmpty()) {
            compostStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPOSTER);
        }

        int curY = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                g.pose().pushMatrix();
                g.pose().translate(0f, -2f);
                g.pose().scale(0.75f, 0.75f);
                g.item(compostStack, 0, 0);
                g.pose().popMatrix();
                g.text(Minecraft.getInstance().font, lines.get(0), 15, curY, 0xFFFFFFFF, true);
            } else {
                g.text(Minecraft.getInstance().font, lines.get(i), 0, curY, 0xFFFFFFFF, true);
            }
            curY += 10;
        }

        g.pose().popMatrix();
    }

    public static int getWidth() {
        return 180;
    }

    public static int getHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        return s.composterDebug ? 70 : 50;
    }

    private static String formatNum(double num) {
        if (num >= 1_000_000) return String.format("%.1fm", num / 1_000_000);
        if (num >= 1_000) return String.format("%.1fk", num / 1_000);
        return String.format("%.0f", num);
    }

    private static String formatTime(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
