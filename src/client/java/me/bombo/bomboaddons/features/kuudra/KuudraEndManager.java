package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kuudra End of Run Manager:
 * - Real-time chest value & profit evaluator on open
 * - Session Profit Tracker HUD matching authentic KuudraIsCool (KIC) styling
 * - Auto Requeue with party validation
 */
public class KuudraEndManager {

    private static final Pattern ESSENCE_PATTERN = Pattern.compile("(?i)(\\w+) Essence x(\\d+)");
    private static final Pattern SHARD_PATTERN = Pattern.compile("(?i)(.+?) Shard x(\\d+)");

    private static int sessionRuns = 0;
    private static int sessionChests = 0;
    private static long sessionProfitCoins = 0L;
    private static long sessionChestCosts = 0L;
    private static long sessionEssence = 0L;
    private static long sessionStartTime = System.currentTimeMillis();

    private static final Set<String> initialPartyMembers = new HashSet<>();
    private static final Set<String> recordedChestsThisRun = new HashSet<>();
    private static boolean runActive = false;
    private static String lastScannedChestTitle = "";
    private static long lastChestScanTime = 0L;

    public static void onRunStart(Set<String> partyMembers) {
        runActive = true;
        initialPartyMembers.clear();
        recordedChestsThisRun.clear();
        if (partyMembers != null) {
            initialPartyMembers.addAll(partyMembers);
        }
    }

    public static void onRunEnd(long profitCoins) {
        if (!runActive) return;
        runActive = false;
        sessionRuns++;
        if (profitCoins > 0) {
            sessionProfitCoins += profitCoins;
        }

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        // Auto Requeue
        if (s.kuudraAutoRequeue) {
            handleAutoRequeue(s);
        }
    }

    public static void onContainerTick(AbstractContainerScreen<?> screen) {
        if (screen == null) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraProfitTracker) return;

        String title = screen.getTitle().getString();
        if (!title.contains("Paid Chest") && !title.contains("Free Chest") && !title.contains("Kuudra Chest")) {
            return;
        }

        long now = System.currentTimeMillis();
        if (title.equals(lastScannedChestTitle) && (now - lastChestScanTime < 3000L)) {
            return;
        }

        evaluateChestLoot(screen, title);
    }

    private static void evaluateChestLoot(AbstractContainerScreen<?> screen, String title) {
        lastScannedChestTitle = title;
        lastChestScanTime = System.currentTimeMillis();

        long lootValue = 0L;
        int essenceCount = 0;
        long keyCost = 0L;
        boolean isFree = title.contains("Free Chest");
        boolean alreadyOpened = false;

        // Scan reward slots 9..17
        for (Slot slot : screen.getMenu().slots) {
            if (slot.index >= 9 && slot.index <= 17 && slot.hasItem()) {
                ItemStack stack = slot.getItem();
                String itemKey = stack.getItem().toString().toLowerCase(Locale.ROOT);
                if (itemKey.contains("glass") || stack.getItem() == Items.BARRIER || stack.getItem() == Items.CHEST) {
                    continue;
                }

                String name = stack.getHoverName().getString();
                Matcher essM = ESSENCE_PATTERN.matcher(name);
                if (essM.find()) {
                    int count = Integer.parseInt(essM.group(2));
                    essenceCount += count;
                    long price = LowestBinManager.getCachedPrice("ESSENCE_CRIMSON");
                    if (price <= 0) price = 7500L;
                    lootValue += (price * count);
                    continue;
                }

                Matcher shardM = SHARD_PATTERN.matcher(name);
                if (shardM.find()) {
                    String type = shardM.group(1).trim().toUpperCase(Locale.ROOT).replace(" ", "_");
                    int count = Integer.parseInt(shardM.group(2));
                    long price = LowestBinManager.getCachedPrice("SHARD_" + type);
                    if (price <= 0) price = LowestBinManager.getCachedPrice(type + "_SHARD");
                    if (price <= 0) price = 50000L;
                    lootValue += (price * count);
                    continue;
                }

                String sbId = SkyblockUtils.getSkyblockId(stack);
                if (sbId != null && !sbId.isEmpty()) {
                    long est = LowestBinManager.calculateEstimatedValue(stack, sbId);
                    if (est <= 0) {
                        est = LowestBinManager.getCachedPrice(sbId);
                    }
                    if (est > 0) {
                        lootValue += est * Math.max(1, stack.getCount());
                    }
                }
            } else if (slot.index == 31 && slot.hasItem()) {
                // Buy slot
                ItemStack buyStack = slot.getItem();
                ItemLore lore = buyStack.get(DataComponents.LORE);
                if (lore != null) {
                    for (Component line : lore.lines()) {
                        String str = line.getString();
                        if (str.contains("Already opened")) {
                            alreadyOpened = true;
                        } else if (str.contains("Infernal Kuudra Key")) {
                            long p = LowestBinManager.getCachedPrice("KUUDRA_INFERNAL_TIER_KEY");
                            keyCost = p > 0 ? p : 8500000L;
                        } else if (str.contains("Fiery Kuudra Key")) {
                            long p = LowestBinManager.getCachedPrice("KUUDRA_FIERY_TIER_KEY");
                            keyCost = p > 0 ? p : 3200000L;
                        } else if (str.contains("Burning Kuudra Key")) {
                            long p = LowestBinManager.getCachedPrice("KUUDRA_BURNING_TIER_KEY");
                            keyCost = p > 0 ? p : 1400000L;
                        } else if (str.contains("Hot Kuudra Key")) {
                            long p = LowestBinManager.getCachedPrice("KUUDRA_HOT_TIER_KEY");
                            keyCost = p > 0 ? p : 550000L;
                        } else if (str.contains("Kuudra Key")) {
                            long p = LowestBinManager.getCachedPrice("KUUDRA_TIER_KEY");
                            keyCost = p > 0 ? p : 200000L;
                        }
                    }
                }
            }
        }

        if (isFree) keyCost = 0L;
        long netProfit = lootValue - keyCost;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String profitColor = netProfit >= 0 ? "§a+" : "§c";
            String msg = "§8[§bBombo§8] §6" + title + ": §7Loot: §e" + formatCoins(lootValue)
                    + (keyCost > 0 ? " §7(Key: §c-" + formatCoins(keyCost) + "§7)" : "")
                    + " §8| §7Profit: " + profitColor + formatCoins(netProfit);
            mc.player.sendSystemMessage(Component.literal(msg));
        }

        // Record to session if not yet recorded for this run
        if (!recordedChestsThisRun.contains(title)) {
            recordedChestsThisRun.add(title);
            sessionChests++;
            sessionProfitCoins += netProfit;
            sessionChestCosts += keyCost;
            sessionEssence += essenceCount;
        }
    }

    private static void handleAutoRequeue(BomboConfig.Settings s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int tier = KuudraUtils.getKuudraTier();
        if (tier < 1 || tier > 5) tier = 5;

        final int finalTier = tier;
        new Thread(() -> {
            try {
                Thread.sleep(1500L);
                mc.execute(() -> {
                    if (mc.player != null) {
                        String tierName = switch (finalTier) {
                            case 1 -> "basic";
                            case 2 -> "hot";
                            case 3 -> "burning";
                            case 4 -> "fiery";
                            default -> "infernal";
                        };
                        mc.player.connection.sendCommand("joininstance kuudra_" + tierName);
                    }
                });
            } catch (InterruptedException ignored) {}
        }).start();
    }

    public static void renderHUD(GuiGraphicsExtractor g, Font font, int screenW, int screenH) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraProfitTracker) return;

        if (s.kuudraProfitHideInRun && runActive) return;

        int boxW = 160;
        int boxH = 92;
        int boxX = 16;
        int boxY = 80;

        boolean noBg = s.hudNoBackground;
        if (!noBg) {
            g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xD0090D16);
            g.outline(boxX, boxY, boxW, boxH, 0x6038BDF8);
        }

        long elapsedSec = Math.max(1, (System.currentTimeMillis() - sessionStartTime) / 1000);
        long hourlyProfit = (long) ((sessionProfitCoins / (double) elapsedSec) * 3600);
        long avgProfitPerChest = sessionChests > 0 ? (sessionProfitCoins / sessionChests) : sessionProfitCoins;

        int y = boxY + 5;
        // KIC authentic lines
        g.text(font, "§4§lKuudra Tracker §r§7[§3§lCURRENT§r§7]", boxX + 8, y, 0xFFFFFFFF, false); y += 11;
        g.text(font, "§3§lProfit: §r§3" + (sessionProfitCoins >= 0 ? "+" : "") + formatCoins(sessionProfitCoins), boxX + 8, y, 0xFFCBD5E1, false); y += 10;
        g.text(font, "§3§lRuns: §r§3" + sessionRuns, boxX + 8, y, 0xFFCBD5E1, false); y += 10;
        g.text(font, "§3§lChests: §r§3" + sessionChests + (sessionChestCosts > 0 ? " §7(§c-" + formatCoins(sessionChestCosts) + "§7)" : ""), boxX + 8, y, 0xFFCBD5E1, false); y += 10;
        g.text(font, "§3§lAverage: §r§3" + formatCoins(avgProfitPerChest) + "/chest", boxX + 8, y, 0xFFCBD5E1, false); y += 10;
        g.text(font, "§3§lRate: §r§3" + formatCoins(hourlyProfit) + "/hr", boxX + 8, y, 0xFFCBD5E1, false); y += 10;
        g.text(font, "§3§lEssence: §r§3+" + formatCoins(sessionEssence), boxX + 8, y, 0xFFCBD5E1, false); y += 10;
        g.text(font, "§3§lTime: §r§3" + formatTime(elapsedSec), boxX + 8, y, 0xFF94A3B8, false);
    }

    private static String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        if (m >= 60) {
            long h = m / 60;
            return String.format(Locale.ROOT, "%dh %dm", h, m % 60);
        }
        return String.format(Locale.ROOT, "%dm %ds", m, s);
    }

    public static String formatCoins(long c) {
        if (c >= 1_000_000_000L || c <= -1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2fB", c / 1_000_000_000.0);
        } else if (c >= 1_000_000L || c <= -1_000_000L) {
            return String.format(Locale.ROOT, "%.2fM", c / 1_000_000.0);
        } else if (c >= 1_000L || c <= -1_000L) {
            return String.format(Locale.ROOT, "%.1fk", c / 1_000.0);
        }
        return String.valueOf(c);
    }

    public static int getSessionRuns() {
        return sessionRuns;
    }

    public static int getSessionChests() {
        return sessionChests;
    }

    public static long getSessionProfitCoins() {
        return sessionProfitCoins;
    }

    public static long getSessionEssence() {
        return sessionEssence;
    }
}
