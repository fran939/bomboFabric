package me.bombo.bomboaddons;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.util.IChatComponent;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag.Default;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.EntityHitResult;

public class AutoCroesus {
   private static long lastOverviewScan = 0L;
   private static final long OVERVIEW_COOLDOWN = 2000L;
   private static boolean checkGuiReported = false;
   private static int lastContainerId = -1;
   private static KuudraTier storedTier;
   public static String lastRunTier;
   public static long lastRunKeyCost;
   public static long lastRunContentsValue;
   public static long lastRunProfit;
   public static long lastRunCrimsonEssence;
   public static long lastRunKuudraTeeth;
   public static long lastRunKrakenShards;
   public static long lastRunTimestamp;
   public static List<String> lastRunItems;
   public static int lastRunEssenceBonusPercent;
   private static final long KEY_PRICE_BASIC = 10000L;
   private static final long KEY_PRICE_HOT = 50000L;
   private static final long KEY_PRICE_BURNING = 200000L;
   private static final long KEY_PRICE_FIERY = 600000L;
   private static final long KEY_PRICE_INFERNAL = 2400000L;
   private static final String ENCHANTED_RED_SAND = "ENCHANTED_RED_SAND";
   private static final String ENCHANTED_MYCELIUM = "ENCHANTED_MYCELIUM";
   public static final int[] ARMOR_STAR_ESSENCE;
   private static final Map<String, Integer> BASE_SALVAGE_MAP;
   public static boolean active;
   public static boolean debugHighlightMode = false;
   public static boolean isProgrammaticClose;
   public static boolean needsNpcClick;
   private static AbstractContainerScreen<?> lastOverviewScreen;
   private static long overviewGuiOpenTime;
   private static AbstractContainerScreen<?> lastScreen;
   public static boolean boughtCurrentChest;
   public static List<ItemDetail> lastRunParsedDetails;
   private static boolean hasRerolledCurrentChest;
   private static boolean hasReportedSimulationClose = false;
   private static long lastActionTime;
   public static String hudActionStatus;
   private static Set<String> neverKismetBlacklist;
   private static long lastBlacklistFetch;
   private static final String PROFIT_FILE = "bombo_croesus_profit.json";

   // --- RandomStuff/AutoCroesus price-classification sets (logic port) -------------------
   // always_buy: items bought immediately regardless of computed profit, because the
   // lowest-BIN snapshot systematically under-prices them (handles, scrolls, dyes...).
   // worthless: items whose API price is manipulated or meaningless; forced to 0 so an
   // unopened run with only junk never trips the safety "0 price" abort.
   public static final Set<String> ALWAYS_BUY_ITEMS = Set.of(
           "NECRON_HANDLE", "DARK_CLAYMORE",
           "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR",
           "SHADOW_FURY", "SHADOW_WARP_SCROLL", "IMPLOSION_SCROLL", "WITHER_SHIELD_SCROLL", "DYE_LIVID");
   public static final Set<String> WORTHLESS_ITEMS = Set.of(
           "DUNGEON_DISC_5", "DUNGEON_DISC_4", "DUNGEON_DISC_3", "DUNGEON_DISC_2", "DUNGEON_DISC_1",
           "MAXOR_THE_FISH", "STORM_THE_FISH", "GOLDOR_THE_FISH",
           "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_1", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_2",
           "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_3", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_4",
           "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_5",
           "ENCHANTMENT_ULTIMATE_COMBO_1", "ENCHANTMENT_ULTIMATE_COMBO_2", "ENCHANTMENT_ULTIMATE_COMBO_3",
           "ENCHANTMENT_ULTIMATE_COMBO_4", "ENCHANTMENT_ULTIMATE_COMBO_5",
           "ENCHANTMENT_ULTIMATE_BANK_1", "ENCHANTMENT_ULTIMATE_BANK_2", "ENCHANTMENT_ULTIMATE_BANK_3",
           "ENCHANTMENT_ULTIMATE_BANK_4", "ENCHANTMENT_ULTIMATE_BANK_5",
           "ENCHANTMENT_ULTIMATE_JERRY_1", "ENCHANTMENT_ULTIMATE_JERRY_2", "ENCHANTMENT_ULTIMATE_JERRY_3",
           "ENCHANTMENT_ULTIMATE_JERRY_4", "ENCHANTMENT_ULTIMATE_JERRY_5",
           "ENCHANTMENT_FEATHER_FALLING_6", "ENCHANTMENT_FEATHER_FALLING_7", "ENCHANTMENT_FEATHER_FALLING_8",
           "ENCHANTMENT_FEATHER_FALLING_9", "ENCHANTMENT_FEATHER_FALLING_10",
           "ENCHANTMENT_INFINITE_QUIVER_6", "ENCHANTMENT_INFINITE_QUIVER_7", "ENCHANTMENT_INFINITE_QUIVER_8",
           "ENCHANTMENT_INFINITE_QUIVER_9", "ENCHANTMENT_INFINITE_QUIVER_10");

   /** true when this id must be bought even if the computed profit is negative. */
   public static boolean isAlwaysBuy(String itemId) {
      return itemId != null && ALWAYS_BUY_ITEMS.contains(sanitizeId(itemId));
   }

   /** true when this id's price must be treated as 0 regardless of what the API says. */
   public static boolean isWorthlessItem(String itemId) {
      return itemId != null && WORTHLESS_ITEMS.contains(sanitizeId(itemId));
   }

   public static int getArmorSalvageBase(int stars) {
      if (stars <= 0) {
         return 100;
      } else {
         return stars < ARMOR_STAR_ESSENCE.length ? ARMOR_STAR_ESSENCE[stars] : (int)Math.round((double)100.0F * Math.pow(1.15, (double)stars));
      }
   }

   public static int getBaseSalvageEssence(String itemId) {
      if (itemId == null) {
         return 0;
      } else {
         String cleanId = sanitizeId(itemId);
         if (BASE_SALVAGE_MAP.containsKey(cleanId)) {
            return (Integer)BASE_SALVAGE_MAP.get(cleanId);
         } else if (!cleanId.contains("HELMET") && !cleanId.contains("BOOTS") && !cleanId.contains("CHESTPLATE") && !cleanId.contains("LEGGINGS")) {
            return !cleanId.contains("BRACELET") && !cleanId.contains("BELT") && !cleanId.contains("CLOAK") && !cleanId.contains("NECKLACE") && !cleanId.contains("WAND") ? 0 : 500;
         } else {
            return 100;
         }
      }
   }

   public static void startDebugSimulation(FabricClientCommandSource source) {
      debugHighlightMode = true;
      active = true;
      needsNpcClick = true;
      isProgrammaticClose = false;
      BomboConfig.get().autoCroesus = true;
      BomboConfig.save();
      lastActionTime = 0L;
      if (source != null) {
         source.sendFeedback(Component.literal("§8[§bAutoCroesus Debug§8] §aAutoCroesus Simulation & Highlight Mode ACTIVATED! It will highlight the exact slot it would click. Press ESC to stop."));
      } else if (Minecraft.getInstance().player != null) {
         Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bAutoCroesus Debug§8] §aAutoCroesus Simulation & Highlight Mode ACTIVATED! It will highlight the exact slot it would click. Press ESC to stop."));
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.getUser() != null) {
         KuudraSummaryOverlay.fetchDataSilent(mc.getUser().getName());
      }

      checkCroesusNpcClick();
   }

   public static void startActive(FabricClientCommandSource source) {
      debugHighlightMode = false;
      active = true;
      needsNpcClick = true;
      isProgrammaticClose = false;
      BomboConfig.get().autoCroesus = true;
      BomboConfig.save();
      lastActionTime = 0L;
      if (source != null) {
         source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §aAuto Croesus activated! Press ESC at any time to stop."));
      } else if (Minecraft.getInstance().player != null) {
         Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §aAuto Croesus activated! Press ESC at any time to stop."));
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.getUser() != null) {
         KuudraSummaryOverlay.fetchDataSilent(mc.getUser().getName());
      }

      checkCroesusNpcClick();
   }

   public static void stopActive(FabricClientCommandSource source) {
      if (active) {
         active = false;
         debugHighlightMode = false;
         needsNpcClick = false;
         isProgrammaticClose = false;
         SlotHighlight.clearTargetSlot();
         if (source != null) {
            source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §cAuto Croesus stopped (Kill Switch / ESC pressed)!"));
         } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §cAuto Croesus stopped (Kill Switch / ESC pressed)!"));
         }

      }
   }

   /**
    * (slot, action) pairs already announced for the currently open container. The highlight
    * itself is refreshed every tick (so the outline follows the real decision), but chat only
    * hears about a pair once - previously the decision flipped between two slots each tick and
    * flooded chat with the same lines.
    */
   private static final Set<String> announcedSimulation = new HashSet<>();

   private static void clickOrHighlightSlot(int syncId, int slotIndex, String actionName) {
      if (debugHighlightMode) {
         SlotHighlight.setTargetSlot(slotIndex, 0x8800FF00);
         String key = slotIndex + "|" + actionName;
         if (!announcedSimulation.add(key)) {
            return;
         }
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus Debug§8] §e[SIMULATION] Highlighted slot #" + slotIndex + " for: §a" + actionName));
         }
      } else {
         clickSlot(syncId, slotIndex);
      }
   }

   /** Called when a new container opens so the next simulation line is always printed. */
   private static void resetSimulationAnnounce() {
      announcedSimulation.clear();
   }

   public static void onScreenClosed() {
      if (active) {
         if (!isProgrammaticClose) {
            stopActive((FabricClientCommandSource)null);
         }

         isProgrammaticClose = false;
      }
   }

   public static void performStep(FabricClientCommandSource source) {
      Minecraft mc = Minecraft.getInstance();
      BomboConfig.Settings s = BomboConfig.get();
      if (!active) {
         active = true;
         s.autoCroesus = true;
      }

      Screen var4 = mc.gui.screen();
      if (var4 instanceof AbstractContainerScreen<?> containerScreen) {
         String rawTitle = containerScreen.getTitle().getString();
         String cleanTitle = rawTitle.replaceAll("§[0-9a-fk-or]", "").trim();
         if (cleanTitle.contains("Croesus")) {
            lastOverviewScan = 0L;
            lastActionTime = 0L;
            onContainerTick(containerScreen);
            if (source != null) {
               source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §aStep: Clicked next claimable chest in overview!"));
            }
         } else {
            boughtCurrentChest = false;
            lastActionTime = 0L;
            onCheckGuiTick(containerScreen);
            if (source != null) {
               source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §aStep: Clicked chest reward/reroll button!"));
            }
         }
      } else {
         checkCroesusNpcClick();
         if (source != null) {
            source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §aStep: Clicked Croesus NPC!"));
         }
      }

   }

   public static void onContainerTick(AbstractContainerScreen<?> screen) {
      if (screen != null) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s.autoCroesus && active) {
            String title = screen.getTitle().getString();
            if (title.contains("Croesus")) {
               long now = System.currentTimeMillis();
               if (screen != lastOverviewScreen) {
                  lastOverviewScreen = screen;
                  overviewGuiOpenTime = now;
               }

               if (now - lastOverviewScan >= s.autoCroesusDelay) {
                  lastOverviewScan = now;
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null) {
                     boolean foundClaimable = false;
                     boolean scannedAnyChest = false;

                     for(Slot slot : screen.getMenu().slots) {
                        if (slot.hasItem() && !(slot.container instanceof Inventory)) {
                           ItemStack stack = slot.getItem();
                           String name = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");
                           boolean isChest = name.contains("Kuudra") || name.contains("Chest") || name.contains("Paid") || name.contains("Free") || name.contains("Floor") || name.contains("Master") || name.contains("Tier") || name.contains("Dungeon");
                           if (isChest) {
                              scannedAnyChest = true;
                              List<Component> tooltip = stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, Default.NORMAL);
                              boolean hasClaimableIndicator = false;
                              boolean hasNoMoreChests = false;
                              KuudraTier tier = AutoCroesus.KuudraTier.UNKNOWN;

                              for(Component line : tooltip) {
                                 String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                                 String lower = clean.toLowerCase();
                                 if (lower.contains("chests expire in") || lower.contains("click to inspect") || lower.contains("click to view") || lower.contains("click to open") || lower.contains("click to claim") || lower.contains("unclaimed") || lower.contains("available") || lower.contains("open chest")) {
                                    hasClaimableIndicator = true;
                                 }

                                 if (lower.contains("no more chests") || lower.contains("no chests to open") || lower.contains("already claimed") || lower.contains("none remaining")) {
                                    hasNoMoreChests = true;
                                 }

                                 if (tier == AutoCroesus.KuudraTier.UNKNOWN) {
                                    tier = extractTier(clean);
                                 }
                              }

                              boolean canClaim = (hasClaimableIndicator || !hasNoMoreChests) && !hasNoMoreChests;
                              if (canClaim && tier != AutoCroesus.KuudraTier.UNKNOWN && (tier.ordinal() > storedTier.ordinal() || storedTier == AutoCroesus.KuudraTier.UNKNOWN)) {
                                 storedTier = tier;
                              }

                              if (canClaim) {
                                 foundClaimable = true;
                                 if (now - lastActionTime > s.autoCroesusDelay) {
                                    clickOrHighlightSlot(screen.getMenu().containerId, slot.index, "Open Run Slot " + slot.index);
                                    lastActionTime = now;
                                    break;
                                 }
                              }
                           }
                        }
                     }

                     if (scannedAnyChest && !foundClaimable && now - overviewGuiOpenTime > 1500L) {
                        isProgrammaticClose = true;
                        active = false;
                        needsNpcClick = false;
                        mc.execute(() -> {
                           if (mc.player != null) {
                              mc.player.closeContainer();
                              mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §aNo more unopened chests remaining! Auto Croesus completed."));
                           }

                        });
                     }

                  }
               }
            }
         }
      }
   }

   public static boolean isDungeonRunGui(AbstractContainerScreen<?> screen) {
      if (screen == null) {
         return false;
      } else {
         String rawTitle = screen.getTitle().getString();
         String cleanTitle = rawTitle.replaceAll("§[0-9a-fk-or]", "").trim();
         String lower = cleanTitle.toLowerCase();
         if (lower.contains("croesus") && !lower.contains("floor")) return false;
         return lower.contains("catacombs") || lower.matches("^(?:master )?catacombs - floor [ivxlcdm0-9]+$") || (lower.contains("floor") && !lower.contains("kuudra"));
      }
   }

   public static boolean isKuudraChestGui(AbstractContainerScreen<?> screen) {
      if (screen == null) {
         return false;
      } else {
         String rawTitle = screen.getTitle().getString();
         String cleanTitle = rawTitle.replaceAll("§[0-9a-fk-or]", "").trim();
         String lower = cleanTitle.toLowerCase();
         if (isDungeonRunGui(screen)) return false;
         return lower.startsWith("kuudra - ") || lower.contains("kuudra") || lower.equalsIgnoreCase("paid chest") || lower.equalsIgnoreCase("free chest") || (lower.contains("chest") && !lower.contains("inventory"));
      }
   }

   public static void onCheckGuiTick(AbstractContainerScreen<?> screen) {
      if (screen != null) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s.autoCroesus || s.autoCroesusHud) {
            int currentContainerId = screen.getMenu().containerId;
            if (currentContainerId != lastContainerId) {
               lastContainerId = currentContainerId;
               boughtCurrentChest = false;
               hasRerolledCurrentChest = false;
               checkGuiReported = false;
               hasReportedSimulationClose = false;
               lastActionTime = 0L;
               resetSimulationAnnounce();
            }

            if (isDungeonRunGui(screen)) {
               handleDungeonRunTick(screen, s);
            } else if (isKuudraChestGui(screen)) {
               handleKuudraChestTick(screen, s);
            }
         }
      }
   }

   public static class DungeonChestData {
      public int slotIndex;
      public String chestName;
      public long cost;
      public boolean isFree;
      public boolean alreadyOpened;
      public List<ItemDetail> parsedItems = new ArrayList<>();
      public long totalContentsValue = 0L;
      public long profit = 0L;
   }

   public static void handleDungeonRunTick(AbstractContainerScreen<?> screen, BomboConfig.Settings s) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      String cleanTitle = screen.getTitle().getString().replaceAll("§[0-9a-fk-or]", "").trim();
      long now = System.currentTimeMillis();

      List<DungeonChestData> chests = new ArrayList<>();
      Slot rerollSlot = null;
      boolean rerollAvailable = false;

      for (Slot slot : screen.getMenu().slots) {
         if (!slot.hasItem() || (slot.container instanceof Inventory)) continue;
         ItemStack stack = slot.getItem();
         String rawName = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
         String lowerName = rawName.toLowerCase();

         if (lowerName.contains("reroll") || lowerName.contains("modifier") || stack.getItem() == net.minecraft.world.item.Items.REDSTONE_TORCH) {
            rerollSlot = slot;
            for (Component line : stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, Default.NORMAL)) {
               String clean = line.getString().replaceAll("§[0-9a-fk-or]", "");
               String cl = clean.toLowerCase();
               if (cl.contains("kismet") || cl.contains("reroll") || cl.contains("modifiers") || cl.contains("consumable")) {
                  rerollAvailable = true;
                  break;
               }
            }
         } else if (isDungeonRewardChest(rawName, lowerName)) {
            DungeonChestData chest = new DungeonChestData();
            chest.slotIndex = slot.index;
            chest.chestName = rawName;
            List<Component> tooltip = stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, Default.NORMAL);

            boolean readingContents = false;
            boolean readingCost = false;
            for (Component line : tooltip) {
               String raw = line.getString();
               String clean = raw.replaceAll("§[0-9a-fk-or]", "").trim();
               if (clean.contains("Already opened") || clean.contains("Already claimed") || clean.contains("Purchased") || clean.contains("Opened!")) {
                  chest.alreadyOpened = true;
               }
               if (clean.equalsIgnoreCase("Free") || clean.startsWith("Free") || clean.equalsIgnoreCase("0 Coins")) {
                  chest.isFree = true;
                  chest.cost = 0L;
               }

               if (clean.equalsIgnoreCase("Cost")) {
                  readingCost = true;
                  readingContents = false;
                  continue;
               }
               if (readingCost) {
                  if (clean.equalsIgnoreCase("Free") || clean.startsWith("Free")) {
                     chest.isFree = true;
                     chest.cost = 0L;
                     readingCost = false;
                  } else if (clean.matches("(?i).*[0-9,]+.*coins?.*")) {
                     String digits = clean.replaceAll("(?i)[^0-9]", "");
                     if (!digits.isEmpty()) {
                        try {
                           chest.cost = Long.parseLong(digits);
                        } catch (Exception ignored) {}
                     }
                     readingCost = false;
                  }
               }

               if (clean.equalsIgnoreCase("Contents")) {
                  readingContents = true;
                  readingCost = false;
                  continue;
               } else if (clean.startsWith("Click to") || clean.contains("Already opened") || clean.contains("Already claimed") || clean.contains("Purchased") || clean.contains("NOTE:")) {
                  readingContents = false;
                  readingCost = false;
               }

               if (readingContents && !clean.isEmpty()) {
                  parseDungeonItemLine(line, clean, chest.parsedItems);
               }
            }

            for (ItemDetail item : chest.parsedItems) {
               chest.totalContentsValue += item.totalValue;
            }
            chest.profit = chest.totalContentsValue - chest.cost;
            chests.add(chest);
         }
      }

      me.bombo.bomboaddons.features.dungeons.DungeonChestProfitHud.currentChests = chests;

      if (chests.isEmpty()) return;

      boolean allOpened = true;
      for (DungeonChestData c : chests) {
         if (!c.alreadyOpened) {
            allOpened = false;
            break;
         }
      }

      if (allOpened) {
         hudActionStatus = debugHighlightMode ? "§e[SIMULATION: Run Chests Claimed]" : "§7[Run Chests Claimed]";
         if (s.autoCroesus && active && now - lastActionTime > s.autoCroesusDelay) {
            lastActionTime = now;
            if (!debugHighlightMode) {
               mc.player.closeContainer();
            } else {
               mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus Debug§8] §e[SIMULATION] Would close container (all chests claimed)."));
            }
         }
         return;
      }

      List<DungeonChestData> unopened = new ArrayList<>();
      for (DungeonChestData c : chests) {
         if (!c.alreadyOpened) unopened.add(c);
      }
      unopened.sort((a, b) -> Long.compare(b.profit, a.profit));
      if (unopened.isEmpty()) return;

      DungeonChestData bestChest = unopened.get(0);

      lastRunTier = cleanTitle;
      lastRunContentsValue = bestChest.totalContentsValue;
      lastRunKeyCost = bestChest.cost;
      lastRunProfit = bestChest.profit;
      lastRunParsedDetails = new ArrayList<>(bestChest.parsedItems);
      lastRunItems.clear();
      for (ItemDetail item : bestChest.parsedItems) {
         lastRunItems.add(item.name + (item.adjustedQuantity > 1 ? " x" + item.adjustedQuantity : "") + ": " + LowestBinManager.formatPrice(item.totalValue) + " coins");
      }
      lastRunTimestamp = now;

      // Debug summary: exactly one message per GUI visit listing EVERY chest with its
      // contents and values (item-by-item on hover). Detail lives in the on-screen panel
      // (DungeonChestProfitHud) so chat stays quiet.
      if (!checkGuiReported && mc.player != null && debugHighlightMode) {
         checkGuiReported = true;
         mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus Debug§8] §6" + cleanTitle
               + " §7- §e" + chests.size() + " chest" + (chests.size() == 1 ? "" : "s")
               + " §7(value panel shown on the right; hover the chat line below for item breakdown)"));

         MutableComponent summary = Component.literal("§8[§bAutoCroesus Debug§8] ");
         boolean first = true;
         StringBuilder allContents = new StringBuilder("§6--- All Chests Breakdown ---\n");
         for (DungeonChestData c : chests) {
            String pc = c.profit >= 0 ? "§a+" : "§c-";
            if (!first) summary.append(Component.literal("§8 | "));
            first = false;
            summary.append(Component.literal("§e" + c.chestName + " §7(" + pc + LowestBinManager.formatPrice(c.profit) + "§7)"));
            allContents.append("§6§l").append(c.chestName).append("§7 ").append(c.alreadyOpened ? "§8[claimed] " : "")
                  .append("§7cost §f").append(c.isFree ? "FREE" : LowestBinManager.formatPrice(c.cost))
                  .append(" §7profit §e").append(LowestBinManager.formatPrice(c.profit)).append("\n");
            for (ItemDetail item : c.parsedItems) {
               allContents.append("  §e").append(item.name)
                     .append(item.adjustedQuantity > 1 ? " §7x§a" + String.format("%,d", item.adjustedQuantity) : "")
                     .append(" §7= §6").append(LowestBinManager.formatPrice(item.totalValue)).append("\n");
            }
         }
         summary.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(allContents.toString().trim()))));
         mc.player.sendSystemMessage(summary);
      }

      if (s.autoCroesus && active && now - lastActionTime > s.autoCroesusDelay) {
         // Find Bedrock chest (or Obsidian chest) for Kismet reroll comparison
         DungeonChestData bedrockChest = null;
         for (DungeonChestData d : unopened) {
            if (d.chestName.equalsIgnoreCase("Bedrock") || d.chestName.equalsIgnoreCase("Obsidian")) {
               bedrockChest = d;
               break;
            }
         }
         long rerollChestProfit = (bedrockChest != null) ? bedrockChest.profit : bestChest.profit;
         String targetChestName = (bedrockChest != null) ? bedrockChest.chestName : bestChest.chestName;
         long activeThreshold = s.autoKismet ? s.kismetThreshold : s.autoCroesusRerollValue;

         boolean shouldReroll = !hasRerolledCurrentChest && rerollAvailable && (
            (s.autoCroesusReroll && rerollChestProfit < s.autoCroesusRerollValue) ||
            (s.autoKismet && rerollChestProfit < s.kismetThreshold)
         );

         if (shouldReroll && rerollSlot != null) {
            hudActionStatus = debugHighlightMode ? "§e[SIMULATION: Reroll Kismet (" + targetChestName + ")]" : "§d[Rerolling " + targetChestName + " Chest with Kismet...]";
            clickOrHighlightSlot(screen.getMenu().containerId, rerollSlot.index, "Reroll " + targetChestName + " Chest (Kismet Feather)");
            hasRerolledCurrentChest = true;
            checkGuiReported = false;
            lastActionTime = now;
            mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §e" + targetChestName + " chest profit (§c" + LowestBinManager.formatPrice(rerollChestProfit) + "§e) is below Kismet threshold (§b" + LowestBinManager.formatPrice(activeThreshold) + "§e). §dRerolling with Kismet Feather!"));
            return;
         }

         // RandomStuff always-buy: a chest containing one of these ids is claimed even at a
         // computed loss - the BIN snapshot under-prices them beyond what profit math sees.
         boolean bestHasAlwaysBuy = false;
         for (ItemDetail d : bestChest.parsedItems) {
            if (isAlwaysBuy(d.itemId)) { bestHasAlwaysBuy = true; break; }
         }

         if (!boughtCurrentChest && (bestHasAlwaysBuy || bestChest.profit >= s.autoCroesusDungeonProfitThreshold || bestChest.isFree)) {
            if (bestHasAlwaysBuy && bestChest.profit < s.autoCroesusDungeonProfitThreshold && !debugHighlightMode) {
               mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §dAlways-buy item in §e" + bestChest.chestName
                     + "§d - claiming despite computed profit (§e" + LowestBinManager.formatPrice(bestChest.profit) + "§d)."));
            }
            boughtCurrentChest = true;
            hudActionStatus = debugHighlightMode ? "§e[SIMULATION: Buy " + bestChest.chestName + "]" : "§a[Buying " + bestChest.chestName + "...]";
            clickOrHighlightSlot(screen.getMenu().containerId, bestChest.slotIndex, "Claim " + bestChest.chestName);
            lastActionTime = now;
            if (!debugHighlightMode) {
               mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §aClaiming §e" + bestChest.chestName + " §a(§6" + LowestBinManager.formatPrice(bestChest.totalContentsValue) + " value, " + (bestChest.profit >= 0 ? "§a+" : "§c") + LowestBinManager.formatPrice(bestChest.profit) + " profit§a)!"));
               recordChestPurchase(bestChest.profit, hasRerolledCurrentChest, bestChest.parsedItems);
            }

            if (s.autoCroesusUseDungeonKey && unopened.size() > 1) {
               DungeonChestData secondBest = unopened.get(1);
               if (secondBest.profit >= s.autoCroesusDungeonKeyProfit) {
                  clickOrHighlightSlot(screen.getMenu().containerId, secondBest.slotIndex, "Claim " + secondBest.chestName + " (Dungeon Key)");
                  if (!debugHighlightMode) {
                     mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §aClaiming 2nd profitable chest §e" + secondBest.chestName + " §ausing Dungeon Key (§a+" + LowestBinManager.formatPrice(secondBest.profit) + " profit)!"));
                     recordChestPurchase(secondBest.profit, false, secondBest.parsedItems);
                  }
               }
            }
            return;
         }

         hudActionStatus = debugHighlightMode ? "§e[SIMULATION: No Profitable Chests]" : "§7[No Profitable Chests]";
         lastActionTime = now;
         if (!debugHighlightMode) {
            mc.player.closeContainer();
         } else if (!hasReportedSimulationClose && !boughtCurrentChest) {
            hasReportedSimulationClose = true;
            mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus Debug§8] §e[SIMULATION] Would close container (no profitable chests)."));
         }
      }
   }

   public static boolean isDungeonRewardChest(String rawName, String cleanName) {
      if (rawName == null) return false;
      String lower = cleanName.toLowerCase().trim();
      if (lower.contains("close") || lower.contains("reroll") || lower.contains("croesus") || lower.contains("modifier") || lower.contains("feather") || lower.contains("dungeon chest key") || lower.contains("back") || lower.contains("barrier") || lower.contains("menu")) {
         return false;
      }
      return lower.contains("wood") || lower.contains("gold") || lower.contains("diamond") || lower.contains("emerald") || lower.contains("obsidian") || lower.contains("bedrock") || lower.contains("free") || lower.contains("chest");
   }

   public static void parseDungeonItemLine(Component line, String clean, List<ItemDetail> outList) {
      if (clean == null || clean.isEmpty()) return;

      // RandomStuff pet parsing: chest loot can contain a [Lvl 1] pet whose color codes map to
      // rarity (gold = Legendary, anything else = Epic). Prices live under "NAME;tier" ids.
      Matcher petM = Pattern.compile("(?i)^\\[Lvl 1\\]\\s*(\\S+)").matcher(clean);
      if (petM.find()) {
         String petName = petM.group(1).replaceAll("[^A-Za-z]", "").toUpperCase();
         String color = clean.contains("§6") || clean.toUpperCase().contains("§6") ? ";4" : ";3";
         String petId = petName + color;
         long unitPrice = LowestBinManager.getCachedPrice(petId);
         if (unitPrice <= 0) {
            unitPrice = LowestBinManager.getCachedPrice(petName);
         }
         ItemDetail detail = new ItemDetail();
         detail.name = petName + (color.endsWith("4") ? " (Legendary)" : " (Epic)");
         detail.originalLore = clean;
         detail.itemId = petId;
         detail.baseQuantity = 1;
         detail.adjustedQuantity = 1;
         detail.unitPrice = unitPrice;
         detail.totalValue = unitPrice;
         detail.priceSource = unitPrice > 0 ? "Lowest BIN" : "N/A";
         outList.add(detail);
         return;
      }

      Matcher essM = Pattern.compile("(?i)^(Wither|Undead|Dragon|Spider|Ice|Diamond|Gold|Crimson)\\s+Essence\\s+x(\\d+)").matcher(clean);
      if (essM.find()) {
         String type = essM.group(1).toUpperCase();
         long qty = Long.parseLong(essM.group(2));
         String essId = "ESSENCE_" + type;
         long unitPrice = LowestBinManager.getCachedPrice(essId);
         if (unitPrice <= 0) {
            unitPrice = switch (type) {
               case "WITHER" -> 4500L;
               case "UNDEAD" -> 1200L;
               case "CRIMSON" -> 962L;
               case "DRAGON" -> 3500L;
               default -> 1000L;
            };
         }
         ItemDetail detail = new ItemDetail();
         detail.name = toTitleCase(type) + " Essence";
         detail.originalLore = clean;
         detail.itemId = essId;
         detail.baseQuantity = qty;
         detail.adjustedQuantity = qty;
         detail.unitPrice = unitPrice;
         detail.totalValue = unitPrice * qty;
         detail.priceSource = "BZ";
         outList.add(detail);
         return;
      }

      Matcher bookM = Pattern.compile("(?i)^Enchanted Book\\s*\\((.*?)\\s*([IVXLCDM0-9]+)\\)").matcher(clean);
      if (bookM.find()) {
         String enchantName = bookM.group(1).trim();
         String lvlStr = bookM.group(2).trim();
         int lvl = romanToInt(lvlStr);
         String enchantId = "ENCHANTMENT_" + enchantName.toUpperCase().replace(" ", "_") + "_" + lvl;
         long unitPrice = LowestBinManager.getCachedPrice(enchantId);
         if (unitPrice <= 0) {
            unitPrice = LowestBinManager.getCachedPrice(enchantName.toUpperCase().replace(" ", "_"));
         }
         if (unitPrice <= 0) {
            // Tier based fallback estimation
            unitPrice = getFallbackBookPrice(enchantName, lvl);
         }
         // RandomStuff worthless-set: junk ultimates have manipulated/meaningless prices.
         if (isWorthlessItem(enchantId)) {
            unitPrice = 0L;
         }
         ItemDetail detail = new ItemDetail();
         detail.name = "Enchanted Book (" + enchantName + " " + lvlStr + ")";
         detail.originalLore = clean;
         detail.itemId = enchantId;
         detail.baseQuantity = 1;
         detail.adjustedQuantity = 1;
         detail.unitPrice = unitPrice;
         detail.totalValue = unitPrice;
         detail.priceSource = unitPrice > 0 ? (LowestBinManager.isBazaar(enchantId) ? "BZ" : "Lowest BIN")
                 : (isWorthlessItem(enchantId) ? "WORTHLESS" : "N/A");
         outList.add(detail);
         return;
      }

      String rawName = clean.replaceAll("(?i)\\s*x\\d+\\s*$", "").trim();
      long qty = parseQuantityFromName(clean);
      if (qty <= 0) qty = 1;
      String sanitized = sanitizeName(rawName);
      String itemId = resolveDungeonItemId(sanitized);
      long unitPrice = LowestBinManager.getCachedPrice(itemId);
      if (unitPrice <= 0) {
         unitPrice = LowestBinManager.getCachedPrice(sanitized.toUpperCase().replace(" ", "_"));
      }
      if (unitPrice <= 0) {
         unitPrice = getFallbackDungeonItemPrice(sanitized);
      }
      // RandomStuff price classification: known-manipulated ids are forced to 0 so they can
      // never inflate a chest value, and always-buy ids keep their price for the claim check.
      boolean worthless = isWorthlessItem(itemId) || isWorthlessItem(sanitized.toUpperCase().replace(" ", "_"));
      if (worthless) {
         unitPrice = 0L;
      }
      ItemDetail detail = new ItemDetail();
      detail.name = sanitized;
      detail.originalLore = clean;
      detail.itemId = itemId;
      detail.baseQuantity = qty;
      detail.adjustedQuantity = qty;
      detail.unitPrice = unitPrice;
      detail.totalValue = unitPrice * qty;
      detail.priceSource = unitPrice > 0 ? (LowestBinManager.isBazaar(itemId) ? "BZ" : "Lowest BIN")
              : (worthless ? "WORTHLESS" : "N/A");
      outList.add(detail);
   }

   public static long getFallbackBookPrice(String name, int lvl) {
      String upper = name.toUpperCase().trim();
      if (upper.equals("INFINITE QUIVER") || upper.equals("FEATHER FALLING")) {
         return switch (lvl) {
            case 10 -> 175000L;
            case 9 -> 88000L;
            case 8 -> 44000L;
            case 7 -> 22000L;
            case 6 -> 10000L;
            default -> 100L;
         };
      }
      long base = switch (upper) {
         case "SOUL EATER" -> 2100000L;
         case "ULTIMATE WISE" -> 350000L;
         case "OVERLOAD" -> 1500000L;
         case "LEGION" -> 8000000L;
         case "WISDOM" -> 40000L;
         case "LAST STAND" -> 20000L;
         case "REND" -> 15000L;
         case "REJUVENATE" -> 12000L;
         case "BANK" -> 10000L;
         case "COMBO", "NO PAIN NO GAIN" -> 5000L;
         case "ULTIMATE JERRY" -> 1000L;
         case "ONE FOR ALL" -> 1200000L;
         default -> 5000L;
      };
      if (upper.equals("ONE FOR ALL")) return base;
      return base * (long) Math.max(1, Math.pow(2, lvl - 1));
   }

   public static long getFallbackDungeonItemPrice(String name) {
      String upper = name.toUpperCase().replace("'", "").replace(" ", "_").trim();
      return switch (upper) {
         case "SHARD_APEX_DRAGON", "APEX_DRAGON_SHARD" -> 18500000L;
         case "SHARD_POWER_DRAGON", "POWER_DRAGON_SHARD" -> 12000000L;
         case "SHARD_WITHER", "WITHER_SHARD" -> 4500000L;
         case "SHARD_THORN", "THORN_SHARD" -> 1200000L;
         case "SHARD_SCARF", "SCARF_SHARD" -> 500000L;
         case "NECRONS_HANDLE", "NECRON_HANDLE" -> 945000000L;
         case "WITHER_SHIELD_SCROLL" -> 340000000L;
         case "IMPLOSION_SCROLL" -> 320000000L;
         case "SHADOW_WARP_SCROLL" -> 310000000L;
         case "DARK_CLAYMORE" -> 210000000L;
         case "GIANTS_SWORD" -> 165000000L;
         case "PRECURSOR_EYE" -> 28000000L;
         case "SHADOW_FURY" -> 42000000L;
         case "AUTO_RECOMBOBULATOR" -> 12000000L;
         case "RECOMBOBULATOR_3000" -> 9500000L;
         case "MASTER_SKULL_TIER_1" -> 400000L;
         case "MASTER_SKULL_TIER_2" -> 800000L;
         case "MASTER_SKULL_TIER_3" -> 1600000L;
         case "MASTER_SKULL_TIER_4" -> 4500000L;
         case "MASTER_SKULL_TIER_5" -> 18000000L;
         case "MASTER_SKULL_TIER_6" -> 45000000L;
         case "MASTER_SKULL_TIER_7" -> 250000000L;
         default -> 0L;
      };
   }

   public static String resolveDungeonItemId(String name) {
      if (name == null) return "";
      String upper = name.toUpperCase().replace("'", "").replace(" ", "_").trim();
      return switch (upper) {
         case "WITHER_SHARD" -> "SHARD_WITHER";
         case "THORN_SHARD" -> "SHARD_THORN";
         case "APEX_DRAGON_SHARD" -> "SHARD_APEX_DRAGON";
         case "POWER_DRAGON_SHARD" -> "SHARD_POWER_DRAGON";
         case "SCARF_SHARD" -> "SHARD_SCARF";
         case "NECROMANCERS_BROOCH", "NECROMANCER_BROOCH" -> "NECROMANCER_BROOCH";
         case "WITHER_SHIELD", "WITHER_SHIELD_SCROLL" -> "WITHER_SHIELD_SCROLL";
         case "IMPLOSION", "IMPLOSION_SCROLL" -> "IMPLOSION_SCROLL";
         case "SHADOW_WARP", "SHADOW_WARP_SCROLL" -> "SHADOW_WARP_SCROLL";
         case "WARPED_STONE" -> "AOTE_STONE";
         case "SPIRIT_STONE" -> "SPIRIT_DECOY";
         case "NECRONS_HANDLE", "NECRON_HANDLE" -> "NECRON_HANDLE";
         case "AUTO_RECOMBOBULATOR" -> "AUTO_RECOMBOBULATOR";
         case "RECOMBOBULATOR_3000" -> "RECOMBOBULATOR_3000";
         case "SHADOW_ASSASSIN_CHESTPLATE" -> "SHADOW_ASSASSIN_CHESTPLATE";
         case "SHADOW_ASSASSIN_LEGGINGS" -> "SHADOW_ASSASSIN_LEGGINGS";
         case "SHADOW_ASSASSIN_BOOTS" -> "SHADOW_ASSASSIN_BOOTS";
         case "SHADOW_ASSASSIN_HELMET" -> "SHADOW_ASSASSIN_HELMET";
         case "WITHER_CHESTPLATE" -> "WITHER_CHESTPLATE";
         case "STORM_CHESTPLATE" -> "STORM_CHESTPLATE";
         case "MAXOR_CHESTPLATE" -> "MAXOR_CHESTPLATE";
         case "GOLDOR_CHESTPLATE" -> "GOLDOR_CHESTPLATE";
         case "DARK_CLAYMORE" -> "DARK_CLAYMORE";
         case "GIANTS_SWORD" -> "GIANTS_SWORD";
         case "PRECURSOR_EYE" -> "PRECURSOR_EYE";
         case "WITHER_CLOAK_SWORD", "WITHER_CLOAK" -> "WITHER_CLOAK";
         case "SPIRIT_WING" -> "SPIRIT_WING";
         case "SPIRIT_BONE" -> "SPIRIT_BONE";
         case "SPIRIT_BOOTS" -> "SPIRIT_BOOTS";
         case "BONZOS_STAFF", "BONZO_STAFF" -> "BONZO_STAFF";
         case "FIRST_MASTER_STAR" -> "FIRST_MASTER_STAR";
         case "SECOND_MASTER_STAR" -> "SECOND_MASTER_STAR";
         case "THIRD_MASTER_STAR" -> "THIRD_MASTER_STAR";
         case "FOURTH_MASTER_STAR" -> "FOURTH_MASTER_STAR";
         case "FIFTH_MASTER_STAR" -> "FIFTH_MASTER_STAR";
         default -> {
            if (upper.startsWith("MASTER_SKULL")) {
               Matcher m = Pattern.compile("(?i)MASTER_SKULL.*?(\\d+)").matcher(upper);
               if (m.find()) {
                  yield "MASTER_SKULL_TIER_" + m.group(1);
               }
            }
            String found = LowestBinManager.findIdByName(name);
            yield (found != null && !found.isEmpty()) ? found : upper;
         }
      };
   }

   public static int romanToInt(String roman) {
      if (roman == null || roman.isEmpty()) return 1;
      try {
         return Integer.parseInt(roman);
      } catch (NumberFormatException ignored) {}
      int total = 0;
      int prev = 0;
      for (int i = roman.length() - 1; i >= 0; i--) {
         int val = switch (Character.toUpperCase(roman.charAt(i))) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            case 'C' -> 100;
            case 'D' -> 500;
            case 'M' -> 1000;
            default -> 0;
         };
         if (val < prev) total -= val;
         else { total += val; prev = val; }
      }
      return Math.max(1, total);
   }

   public static void handleKuudraChestTick(AbstractContainerScreen<?> screen, BomboConfig.Settings s) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         String cleanTitle = screen.getTitle().getString().replaceAll("§[0-9a-fk-or]", "").trim();
         KuudraTier tier = extractTier(cleanTitle);
         ItemStack paidChestStack = null;
         List<Component> paidChestTooltip = null;

         for(Slot slot : screen.getMenu().slots) {
            if (slot.hasItem() && !(slot.container instanceof Inventory)) {
               ItemStack stack = slot.getItem();
               String name = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "");
               String lowerName = name.toLowerCase();
               if ((lowerName.contains("paid") || lowerName.contains("open") || lowerName.contains("claim") || lowerName.contains("reward")) && !lowerName.contains("reroll") && !lowerName.contains("close") && !lowerName.contains("free")) {
                  paidChestTooltip = stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, Default.NORMAL);
                  break;
               }
            }
         }

         if (tier == AutoCroesus.KuudraTier.UNKNOWN && paidChestTooltip != null) {
            for(Component line : paidChestTooltip) {
               tier = extractTier(line.getString());
               if (tier != AutoCroesus.KuudraTier.UNKNOWN) {
                  break;
               }
            }
         }

         if (tier == AutoCroesus.KuudraTier.UNKNOWN) {
            tier = storedTier;
         }

         if (tier != AutoCroesus.KuudraTier.UNKNOWN) {
            List<String> paidChestLoreBlock = new ArrayList();
            if (paidChestTooltip != null) {
               boolean inBlock = false;

               for(Component line : paidChestTooltip) {
                  String raw = line.getString();
                  String clean = raw.replaceAll("§[0-9a-fk-or]", "").trim();
                  if (clean.equalsIgnoreCase("Contents")) {
                     inBlock = true;
                  }

                  if (inBlock) {
                     paidChestLoreBlock.add(raw);
                  }
               }

               if (paidChestLoreBlock.isEmpty()) {
                  for(int i = 1; i < paidChestTooltip.size(); ++i) {
                     paidChestLoreBlock.add(((Component)paidChestTooltip.get(i)).getString());
                  }
               }
            }

            String[] petData = getPlayerKuudraPetInfo();
            String petTier = petData[0];
            int petLevel = Integer.parseInt(petData[1]);
            boolean hasLegendaryPet100 = "LEGENDARY".equalsIgnoreCase(petTier) && petLevel >= 100;
            int petBonusPct = hasLegendaryPet100 ? 20 : ("LEGENDARY".equalsIgnoreCase(petTier) ? (int)((double)petLevel * 0.2) : 20);
            KuudraSummaryOverlay.ShardResult activeShardRes = KuudraSummaryOverlay.lastShardResult;
            if (activeShardRes == null || activeShardRes.crimsonLevel == 0 && activeShardRes.echoEssenceLevel == 0) {
               activeShardRes = new KuudraSummaryOverlay.ShardResult();
               activeShardRes.crimsonLevel = 10;
               activeShardRes.echoEssenceLevel = 10;
               activeShardRes.echoEchoesLevel = 10;
            }

            int cooledForgesLevel = KuudraSummaryOverlay.getCooledForgesLevel();
            int cooledForgesPct = cooledForgesLevel * 4;
            lastRunItems.clear();
            long totalContentsValue = 0L;
            long crimsonEssenceTotalQty = 0L;
            long kuudraTeethTotalQty = 0L;
            long krakenShardsTotalQty = 0L;
            boolean alreadyOpened = false;
            if (paidChestTooltip != null) {
               for(Component line : paidChestTooltip) {
                  String clean = line.getString().replaceAll("§[0-9a-fk-or]", "").trim();
                  if (clean.contains("Already opened") || clean.contains("Already claimed") || clean.contains("Purchased")) {
                     alreadyOpened = true;
                     break;
                  }
               }
            }

            if (alreadyOpened) {
               hudActionStatus = "§7[Already Opened]";
               long now = System.currentTimeMillis();
               if (s.autoCroesus && active && now - lastActionTime > s.autoCroesusDelay) {
                  lastActionTime = now;
                  if (mc.player != null) {
                     mc.player.closeContainer();
                  }
               }
            }

            List<ItemDetail> parsedItems = new ArrayList();
            if (paidChestTooltip != null) {
               boolean readingContents = false;

               for(Component line : paidChestTooltip) {
                  String rawLine = line.getString();
                  String clean = rawLine.replaceAll("§[0-9a-fk-or]", "").trim();
                  if (clean.equalsIgnoreCase("Contents")) {
                     readingContents = true;
                  } else {
                     if (clean.equalsIgnoreCase("Cost") || clean.startsWith("Click to") || clean.contains("Already opened") || clean.contains("Already claimed") || clean.contains("Purchased")) {
                        readingContents = false;
                     }

                     if (readingContents && !clean.isEmpty()) {
                        String rawName = clean.replaceAll("(?i)\\s*x\\d+\\s*$", "").trim();
                        long baseQty = parseQuantityFromName(clean);
                        if (baseQty <= 0L) {
                           baseQty = 1L;
                        }

                        String sanitizedName = sanitizeName(rawName);
                        String itemId = sanitizeId(resolveItemId(sanitizedName));
                        String rawFormattedLine = IChatComponent.getLineWithFormatting(line.getVisualOrderText(), '§');
                        int starCount = countStarsFromFormatted(rawFormattedLine);
                        boolean isCrimsonEssence = itemId.contains("ESSENCE_CRIMSON") || itemId.contains("CRIMSON_ESSENCE") || sanitizedName.equalsIgnoreCase("Crimson Essence");
                        boolean isKuudraTeeth = itemId.equals("KUUDRA_TEETH") || sanitizedName.equalsIgnoreCase("Kuudra Teeth");
                        boolean isShard = itemId.startsWith("SHARD_") || sanitizedName.toLowerCase().endsWith("shard");
                        long adjustedQty = baseQty;
                        if (isCrimsonEssence) {
                           double petMultiplier = (double)1.0F + (double)petBonusPct / (double)100.0F;
                           double shardMultiplier = (double)1.0F + activeShardRes.getTotalBonusPct() / (double)100.0F;
                           adjustedQty = (long)Math.ceil((double)baseQty * petMultiplier * shardMultiplier);
                        }

                        long crimsonUnitPrice = LowestBinManager.getCachedPrice("ESSENCE_CRIMSON");
                        if (crimsonUnitPrice <= 0L) {
                           crimsonUnitPrice = 962L;
                        }

                        long unitPrice = 0L;
                        String priceSource = "Lowest BIN";
                        int baseEssence = getBaseSalvageEssence(itemId);
                        long totalEssence = 0L;
                        if (baseEssence > 0) {
                           long rawEssence = (long)baseEssence;
                           if (baseEssence == 100) {
                              rawEssence = (long)getArmorSalvageBase(starCount);
                           }

                           double cooledMult = (double)1.0F + (double)cooledForgesPct / (double)100.0F;
                           totalEssence = Math.round((double)rawEssence * cooledMult) * baseQty;
                        }

                        if (baseEssence > 0 && !isCrimsonEssence && !isKuudraTeeth && !isShard && totalEssence > 0L) {
                           unitPrice = Math.round((double)totalEssence / (double)baseQty) * crimsonUnitPrice;
                           priceSource = "Salvage Conversion";
                        }

                        if (unitPrice <= 0L) {
                           unitPrice = LowestBinManager.getCachedPrice(itemId);
                           if (unitPrice > 0L) {
                              priceSource = LowestBinManager.isBazaar(itemId) ? "BZ" : "Lowest BIN";
                           } else if (isCrimsonEssence) {
                              unitPrice = crimsonUnitPrice;
                              priceSource = "BZ";
                           } else if (isKuudraTeeth) {
                              unitPrice = 7329L;
                              priceSource = "BZ";
                           } else if (isShard) {
                              unitPrice = LowestBinManager.getCachedPrice(itemId);
                              if (unitPrice <= 0L) {
                                 unitPrice = 256316L;
                              }

                              priceSource = "BZ";
                           } else {
                              unitPrice = 0L;
                              priceSource = "No price found in log";
                           }
                        }

                        ItemDetail detail = new ItemDetail();
                        detail.name = sanitizedName;
                        detail.originalLore = rawName;
                        detail.itemId = itemId;
                        detail.baseQuantity = baseQty;
                        detail.adjustedQuantity = adjustedQty;
                        detail.unitPrice = unitPrice;
                        detail.totalValue = unitPrice * adjustedQty;
                        detail.priceSource = priceSource;
                        detail.salvagedEssenceAmount = totalEssence;
                        detail.baseEssence = baseEssence;
                        detail.starCount = starCount;
                        detail.isCrimsonEssence = isCrimsonEssence;
                        detail.hasPetBonus = isCrimsonEssence && hasLegendaryPet100;
                        parsedItems.add(detail);
                     }
                  }
               }
            }

            if (!parsedItems.isEmpty()) {
               for(ItemDetail detail : parsedItems) {
                  totalContentsValue += detail.totalValue;
                  if (detail.isCrimsonEssence) {
                     crimsonEssenceTotalQty += detail.adjustedQuantity;
                  }

                  if (detail.itemId.equals("KUUDRA_TEETH")) {
                     kuudraTeethTotalQty += detail.baseQuantity;
                  }

                  if (detail.itemId.equals("SHARD_KRAKEN")) {
                     krakenShardsTotalQty += detail.baseQuantity;
                  }

                  String var10001 = detail.name;
                  lastRunItems.add(var10001 + (detail.adjustedQuantity > 1L ? " x" + String.format("%,d", detail.adjustedQuantity) : "") + ": ID: " + detail.itemId + ": Price: " + (detail.totalValue > 0L ? String.format("%,d", detail.totalValue) + " coins" : "N/A"));
               }

               long keyCost = calculateKeyCost(tier, s);
               long profit = totalContentsValue - keyCost;
               lastRunTier = tier.displayName;
               lastRunKeyCost = keyCost;
               lastRunContentsValue = totalContentsValue;
               lastRunProfit = profit;
               lastRunCrimsonEssence = crimsonEssenceTotalQty;
               lastRunKuudraTeeth = kuudraTeethTotalQty;
               lastRunKrakenShards = krakenShardsTotalQty;
               lastRunEssenceBonusPercent = petBonusPct;
               lastRunParsedDetails = new ArrayList(parsedItems);
               lastRunItems.clear();

               for(ItemDetail item : parsedItems) {
                  String var130 = item.name;
                  lastRunItems.add(var130 + (item.adjustedQuantity > 1L ? " x" + item.adjustedQuantity : "") + ": " + LowestBinManager.formatPrice(item.totalValue) + " coins");
               }

               lastRunTimestamp = System.currentTimeMillis();
               String profitColor = profit >= 0L ? "§a+" : "§c";
               String tierColor = getTierColor(tier);
               if (!checkGuiReported && mc.player != null) {
                  if (s.kuudraChestDebug) {
                     mc.player.sendSystemMessage(Component.literal("--- Kuudra Chest Analysis ---"));
                     String var131 = tier.displayName;
                     mc.player.sendSystemMessage(Component.literal("**Kuudra Tier:** " + var131));
                     mc.player.sendSystemMessage(Component.literal("**Analysis Settings:**"));
                     mc.player.sendSystemMessage(Component.literal("*   **Auto Croesus Enabled:** " + (s.autoCroesus ? "Yes" : "No")));
                     mc.player.sendSystemMessage(Component.literal("*   **Value Calculation Mode:** " + s.valueCalculationMode));
                     if ("Salvage Value".equalsIgnoreCase(s.valueCalculationMode)) {
                        mc.player.sendSystemMessage(Component.literal("*   **Cooled Forges Perk:** Level " + cooledForgesLevel + " (+" + cooledForgesPct + "% Essence)"));
                     }

                     mc.player.sendSystemMessage(Component.literal(""));
                     mc.player.sendSystemMessage(Component.literal("**--- Item Info: Paid Chest ---**"));

                     for(String line : paidChestLoreBlock) {
                        mc.player.sendSystemMessage(Component.literal(line));
                     }

                     mc.player.sendSystemMessage(Component.literal(""));
                     mc.player.sendSystemMessage(Component.literal("**Assumed Kuudra Pet Bonuses:**"));
                     mc.player.sendSystemMessage(Component.literal("*   **Pet:** Level " + petLevel + " " + petTier + " Kuudra Pet"));
                     mc.player.sendSystemMessage(Component.literal("*   **Bonus:** +" + petBonusPct + "% Crimson Essence quantity"));
                     mc.player.sendSystemMessage(Component.literal(""));
                     mc.player.sendSystemMessage(Component.literal("**Itemized Contents Values:**"));
                     mc.player.sendSystemMessage(Component.literal("*   **Paid Chest Contents:**"));

                     for(ItemDetail item : parsedItems) {
                        var131 = item.name;
                        mc.player.sendSystemMessage(Component.literal("    *   §e" + var131 + " §7(" + String.format("%,d", item.unitPrice) + ") §a" + String.format("%,d", item.adjustedQuantity) + " §7= §6" + String.format("%,d", item.totalValue)));
                        mc.player.sendSystemMessage(Component.literal("        *   Internal ID: `" + item.itemId + "`"));
                        mc.player.sendSystemMessage(Component.literal("        *   Base Quantity: " + item.baseQuantity));
                        if (item.hasPetBonus) {
                           mc.player.sendSystemMessage(Component.literal("        *   Adjusted Quantity: " + item.adjustedQuantity + " (" + item.baseQuantity + " * 1.20 due to pet bonus)"));
                        } else {
                           mc.player.sendSystemMessage(Component.literal("        *   Adjusted Quantity: N/A"));
                        }

                        if (item.salvagedEssenceAmount > 0L) {
                           String starBonusStr = item.starCount > 0 ? ", Stars: " + item.starCount + " (+" + item.starCount * 4 + "%)" : "";
                           mc.player.sendSystemMessage(Component.literal("        *   Salvage Essence: " + String.format("%,d", item.salvagedEssenceAmount) + " Crimson Essence (Base: " + item.baseEssence + starBonusStr + ", Cooled Forges: Level " + cooledForgesLevel + " (+" + cooledForgesPct + "%))"));
                        }

                        LocalPlayer var123 = mc.player;
                        var131 = item.unitPrice > 0L ? String.format("%,d", item.unitPrice) + " coins (" + item.priceSource + ")" : "0 coins (" + item.priceSource + ")";
                        var123.sendSystemMessage(Component.literal("        *   Market Value: " + var131));
                        var123 = mc.player;
                        Object[] var10002 = new Object[]{item.totalValue};
                        var123.sendSystemMessage(Component.literal("        *   Total Item Value: " + String.format("%,d", var10002) + " coins"));
                        mc.player.sendSystemMessage(Component.literal("        *   Lore:"));
                        mc.player.sendSystemMessage(Component.literal("            " + item.originalLore));
                     }

                     mc.player.sendSystemMessage(Component.literal(""));
                     mc.player.sendSystemMessage(Component.literal("**Summary:**"));
                     LocalPlayer var125 = mc.player;
                     Object[] var136 = new Object[]{totalContentsValue};
                     var125.sendSystemMessage(Component.literal("*   Total Estimated Contents Value (Adjusted): " + String.format("%,d", var136) + " coins"));
                     var125 = mc.player;
                     var136 = new Object[]{keyCost};
                     var125.sendSystemMessage(Component.literal("*   Kuudra Key Cost: " + String.format("%,d", var136) + " coins"));
                     var125 = mc.player;
                     var136 = new Object[]{profit};
                     var125.sendSystemMessage(Component.literal("*   Net Profit/Loss: " + String.format("%,d", var136) + " coins"));
                     mc.player.sendSystemMessage(Component.literal("---------------------------------------------------------"));
                  } else {
                     MutableComponent prefix = Component.literal("§8[§bAutoCroesus§8] " + tierColor + tier.displayName + " §7| ");
                     KeyCostDetail kd = getKeyCostDetail(tier);
                     StringBuilder keyTooltipSb = new StringBuilder("§c--- Kuudra Key Cost Breakdown ---\n");
                     keyTooltipSb.append("§7Tier: ").append(tierColor).append(tier.displayName).append("\n");
                     keyTooltipSb.append("§7Faction: §b").append(kd.faction).append(" Faction\n");
                     keyTooltipSb.append("§7Base Coins: §6").append(String.format("%,d", kd.baseCoins)).append(" coins\n");
                     if (kd.discountPercent > 0) {
                        keyTooltipSb.append("§7Ring Discount: §a").append(kd.discountName).append("\n");
                        keyTooltipSb.append("§7Discounted Coins: §6").append(String.format("%,d", kd.discountedCoins)).append(" coins\n");
                     }

                     if (kd.matQty > 0) {
                        keyTooltipSb.append("§7").append(kd.matName).append(" (").append(kd.matQty).append("x): §6").append(String.format("%,d", kd.matTotalPrice)).append(" coins\n");
                     }

                     if (kd.netherStarQty > 0) {
                        keyTooltipSb.append("§7Nether Star (").append(kd.netherStarQty).append("x): §6").append(String.format("%,d", kd.netherStarTotalPrice)).append(" coins\n");
                     }

                     keyTooltipSb.append("§7------------------------\n");
                     keyTooltipSb.append("§7Total Key Cost: §6").append(String.format("%,d", kd.totalKeyCost)).append(" coins (").append(LowestBinManager.formatPrice(kd.totalKeyCost)).append(")");
                     MutableComponent keyComp = Component.literal("§7Key: §6" + LowestBinManager.formatPrice(keyCost)).withStyle((style) -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(keyTooltipSb.toString()))));
                     StringBuilder contentsTooltip = new StringBuilder("§6--- Contents Breakdown ---\n");

                     for(ItemDetail item : parsedItems) {
                        contentsTooltip.append("§e").append(item.name).append(" §7x §a").append(String.format("%,d", item.adjustedQuantity)).append(" §7= §6").append(LowestBinManager.formatPrice(item.totalValue)).append(" §7(").append(String.format("%,d", item.totalValue)).append(" coins)");
                        if (item.salvagedEssenceAmount > 0L) {
                           contentsTooltip.append("\n  §7└─ Salvage Essence: §d+").append(String.format("%,d", item.salvagedEssenceAmount)).append(" Crimson Essence");
                        }

                        contentsTooltip.append("\n");
                     }

                     MutableComponent contentsComp = Component.literal("§7Contents: §6" + LowestBinManager.formatPrice(totalContentsValue)).withStyle((style) -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(contentsTooltip.toString().trim()))));
                     KuudraSummaryOverlay.ensureDataFetched();
                     KuudraSummaryOverlay.ShardResult sRes = KuudraSummaryOverlay.lastShardResult;
                     double totalBonus = sRes != null ? sRes.getTotalBonusPct() : (double)KuudraSummaryOverlay.getAttributeShardEssenceBonusPct();
                     String essenceTooltip = "§d--- Crimson Essence Calculation ---\n§7Pet Bonus: §a+" + petBonusPct + "%\n§7Attribute Shards Bonus: §a+" + String.format("%.1f", totalBonus) + "%\n§7Final Quantity: §d" + String.format("%,d", crimsonEssenceTotalQty);
                     String var10000 = String.format("%,d", crimsonEssenceTotalQty);
                     MutableComponent essenceComp = Component.literal("§7Essence: §d" + var10000).withStyle((style) -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(essenceTooltip))));
                     String profitTooltip = profitColor + "--- Net Profit Calculation ---\n§7Contents Value: §6+" + LowestBinManager.formatPrice(totalContentsValue) + " (" + String.format("%,d", totalContentsValue) + " coins)\n§7Kuudra Key Cost: §c-" + LowestBinManager.formatPrice(keyCost) + " (" + String.format("%,d", keyCost) + " coins)\n§7------------------------\n§7Net Profit: " + profitColor + (profit >= 0L ? "+" : "") + LowestBinManager.formatPrice(profit) + " (" + String.format("%,d", profit) + " coins)";
                     MutableComponent profitComp = Component.literal("§7Profit: " + profitColor + (profit >= 0L ? "+" : "") + LowestBinManager.formatPrice(profit) + " coins").withStyle((style) -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(profitTooltip))));
                     MutableComponent fullMessage = Component.empty().append(prefix).append(keyComp).append(Component.literal(" §7| ")).append(contentsComp).append(Component.literal(" §7| ")).append(essenceComp).append(Component.literal(" §7| ")).append(profitComp);
                     mc.player.sendSystemMessage(fullMessage);
                  }

                  checkGuiReported = true;
               }

               boolean hasZeroPriceItem = false;
               String zeroPriceItemName = "";
               boolean hasBlacklistedItem = false;
               String blacklistedItemName = "";

               for(ItemDetail detail : parsedItems) {
                  String lowerName = detail.name.toLowerCase();
                  boolean isShardOrBook = detail.itemId.startsWith("SHARD_") || detail.itemId.startsWith("ENCHANTMENT_") || lowerName.contains("shard") || lowerName.contains("book");
                  // RandomStuff classification: a WORTHLESS-tagged item legitimately prices at 0
                  // and must not trigger the missing-data safety abort.
                  boolean knownWorthless = isWorthlessItem(detail.itemId) || "WORTHLESS".equals(detail.priceSource);
                  if (detail.totalValue <= 0L && !detail.isCrimsonEssence && !detail.itemId.equals("KUUDRA_TEETH") && !isShardOrBook && !knownWorthless) {
                     hasZeroPriceItem = true;
                     zeroPriceItemName = detail.name;
                  }

                  if (isBlacklistedItem(detail.itemId)) {
                     hasBlacklistedItem = true;
                     blacklistedItemName = detail.name;
                  }
               }

               checkGuiReported = true;
               long now = System.currentTimeMillis();
               long requiredDelay = s.autoCroesusDelay;
               if (s.autoCroesus && active && !alreadyOpened && now - lastActionTime > requiredDelay) {
                  Slot rerollSlot = null;
                  Slot paidChestSlot = null;
                  boolean rerollAvailable = false;

                  for(Slot slot : screen.getMenu().slots) {
                     if (slot.hasItem() && !(slot.container instanceof Inventory)) {
                        ItemStack stack = slot.getItem();
                        String name = stack.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "").toLowerCase();
                        if (name.contains("reroll chest")) {
                           rerollSlot = slot;

                           for(Component line : stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, Default.NORMAL)) {
                              String clean = line.getString().replaceAll("§[0-9a-fk-or]", "");
                              if (clean.contains("Consume a Kismet Feather") || clean.contains("Click to reroll")) {
                                 rerollAvailable = true;
                                 break;
                              }
                           }
                        } else if (name.contains("paid chest") || name.contains("open reward") || name.contains("reward chest") || name.contains("paid") || name.contains("claim")) {
                           paidChestSlot = slot;
                        }
                     }
                  }

                  if (paidChestSlot == null && screen.getMenu().slots.size() > 20) {
                     Slot slot20 = (Slot)screen.getMenu().slots.get(20);
                     if (slot20.hasItem() && !(slot20.container instanceof Inventory)) {
                        paidChestSlot = slot20;
                     }
                  }

                  if (hasZeroPriceItem) {
                     hudActionStatus = "§c[Safety: " + zeroPriceItemName + " 0 price!]";
                     mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §cSafety Trigger! Item \"§e" + zeroPriceItemName + "§c\" has no price (0 coins). Auto-buy and auto-reroll cancelled!"));
                     return;
                  }

                  if (!boughtCurrentChest && hasBlacklistedItem && s.autoCroesusBuyPaid && paidChestSlot != null) {
                     boughtCurrentChest = true;
                     hudActionStatus = debugHighlightMode ? "§e[SIMULATION: RNG " + blacklistedItemName + "]" : "§d[RNG Drop: " + blacklistedItemName + "!]";
                     clickOrHighlightSlot(screen.getMenu().containerId, paidChestSlot.index, "Claim RNG Drop " + blacklistedItemName);
                     lastActionTime = now;
                     if (!debugHighlightMode) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §dValuable RNG drop detected (\"§e" + blacklistedItemName + "§d\")! Skipping Kismet Feather reroll and auto-buying!"));
                     }
                     return;
                  }

                  boolean shouldReroll = !hasRerolledCurrentChest && rerollAvailable && !hasBlacklistedItem && (s.autoCroesusReroll && profit < s.autoCroesusRerollValue || s.autoKismet && totalContentsValue < s.kismetThreshold);
                  if (shouldReroll && rerollSlot != null) {
                     hudActionStatus = debugHighlightMode ? "§e[SIMULATION: Reroll Kismet]" : "§d[Rerolling with Kismet...]";
                     clickOrHighlightSlot(screen.getMenu().containerId, rerollSlot.index, "Reroll Kuudra Chest (Kismet Feather)");
                     hasRerolledCurrentChest = true;
                     checkGuiReported = false;
                     lastActionTime = now;
                     long activeThreshold = s.autoKismet ? s.kismetThreshold : s.autoCroesusRerollValue;
                     LocalPlayer var129 = mc.player;
                     String var135 = LowestBinManager.formatPrice(totalContentsValue);
                     var129.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §eChest value (§c" + var135 + "§e) is below Kismet threshold (§b" + LowestBinManager.formatPrice(activeThreshold) + "§e). §dRerolling with Kismet Feather!"));
                     return;
                  }

                  if (!boughtCurrentChest && s.autoCroesusBuyPaid && paidChestSlot != null && (!shouldReroll || !rerollAvailable || hasRerolledCurrentChest || hasBlacklistedItem)) {
                     boughtCurrentChest = true;
                     hudActionStatus = debugHighlightMode ? "§e[SIMULATION: Buy Paid Chest]" : "§a[Buying Paid Chest...]";
                     clickOrHighlightSlot(screen.getMenu().containerId, paidChestSlot.index, "Open & Claim Paid Chest");
                     lastActionTime = now;
                     if (!debugHighlightMode) {
                        LocalPlayer var128 = mc.player;
                        String var134 = LowestBinManager.formatPrice(totalContentsValue);
                        var128.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §aOpening & Claiming Reward Chest (§b" + var134 + " value, " + (profit >= 0L ? "§a+" : "§c") + String.format("%,d", profit) + " profit§a)!"));
                     }
                  }
               }
            }
         }
      }
   }

   public static void onChatMessage(String message) {
      if (message != null) {
         String clean = message.replaceAll("§[0-9a-fk-or]", "").trim();
         if ((clean.contains("PAID CHEST REWARDS") || clean.contains("FREE CHEST REWARDS")) && !lastRunParsedDetails.isEmpty()) {
            recordChestPurchase(lastRunProfit, hasRerolledCurrentChest, lastRunParsedDetails);
         }

      }
   }

   public static void updateBlacklistFromApi() {
      long now = System.currentTimeMillis();
      if (now - lastBlacklistFetch >= 300000L) {
         lastBlacklistFetch = now;
         CompletableFuture.runAsync(() -> {
            try {
               URL url = new URL("https://api.bombo.dpdns.org/kuudra/blacklist");
               HttpURLConnection conn = (HttpURLConnection)url.openConnection();
               conn.setRequestMethod("GET");
               conn.setConnectTimeout(3000);
               conn.setReadTimeout(3000);
               conn.setRequestProperty("User-Agent", "BomboAddons/1.0");
               if (conn.getResponseCode() == 200) {
                  InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                  JsonElement element = JsonParser.parseReader(reader);
                  reader.close();
                  Set<String> newSet = new HashSet();
                  if (element.isJsonArray()) {
                     for(JsonElement item : element.getAsJsonArray()) {
                        newSet.add(sanitizeId(item.getAsString()));
                     }
                  } else if (element.isJsonObject() && element.getAsJsonObject().has("blacklist")) {
                     for(JsonElement item : element.getAsJsonObject().getAsJsonArray("blacklist")) {
                        newSet.add(sanitizeId(item.getAsString()));
                     }
                  }

                  if (!newSet.isEmpty()) {
                     neverKismetBlacklist = newSet;
                  }
               }
            } catch (Exception var7) {
            }

         });
      }
   }

   public static boolean isBlacklistedItem(String itemId) {
      if (itemId == null) {
         return false;
      } else {
         String cleanId = sanitizeId(itemId);
         return !cleanId.equals("HELLSTORM_STAFF") && !cleanId.equals("HELLSTORM_WAND") && !cleanId.equals("TORMENTOR") && !cleanId.equals("KUUDRA_TENTACLE") && !cleanId.equals("TENTACLE_DYE") && !cleanId.equals("ANANKE_SHARD") && !cleanId.equals("ANANKE_FEATHER") && !cleanId.equals("BURNING_KUUDRA_CORE") ? neverKismetBlacklist.contains(cleanId) : true;
      }
   }

   public static long parseShortPrice(String input) {
      if (input != null && !input.trim().isEmpty()) {
         String clean = input.trim().toLowerCase().replaceAll(",", "");

         try {
            if (clean.endsWith("b")) {
               double val = Double.parseDouble(clean.substring(0, clean.length() - 1));
               return (long)(val * (double)1.0E9F);
            } else if (clean.endsWith("m")) {
               double val = Double.parseDouble(clean.substring(0, clean.length() - 1));
               return (long)(val * (double)1000000.0F);
            } else if (clean.endsWith("k")) {
               double val = Double.parseDouble(clean.substring(0, clean.length() - 1));
               return (long)(val * (double)1000.0F);
            } else {
               return Long.parseLong(clean);
            }
         } catch (Exception var4) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   private static void clickSlot(int syncId, int slotId) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gameMode != null && mc.player != null) {
         mc.gameMode.handleContainerInput(syncId, slotId, 0, ContainerInput.PICKUP, mc.player);
      }

   }

   public static void onGuiClose() {
      storedTier = AutoCroesus.KuudraTier.UNKNOWN;
      checkGuiReported = false;
      hasRerolledCurrentChest = false;
   }

   public static String sanitizeName(String name) {
      return name == null ? "" : name.replaceAll("[✪⚚⭐]", "").replaceAll("\\s+", " ").trim();
   }

   public static String sanitizeId(String id) {
      return id == null ? "" : id.replaceAll("[✪⚚⭐]", "").replaceAll("_+$", "").replaceAll("^_+", "").trim();
   }

   public static int countStarsFromFormatted(String text) {
      if (text != null && !text.isEmpty()) {
         int totalGlyphs = 0;
         int masterStars = 0;
         char currentColor = 'f';
         char[] chars = text.toCharArray();

         for(int i = 0; i < chars.length; ++i) {
            char c = chars[i];
            if ((c == 167 || c == '&') && i + 1 < chars.length) {
               currentColor = Character.toLowerCase(chars[i + 1]);
               ++i;
            } else if (c == 10026 || c == 9882 || c == 11088) {
               ++totalGlyphs;
               if (currentColor == 'd' || currentColor == 'c' || currentColor == '5' || currentColor == '9' || currentColor == 'b' || currentColor == '4') {
                  ++masterStars;
               }
            }
         }

         if (totalGlyphs == 0) {
            return 0;
         } else {
            return totalGlyphs + masterStars;
         }
      } else {
         return 0;
      }
   }

   private static int countStars(String text) {
      if (text == null) {
         return 0;
      } else {
         int count = 0;

         for(char c : text.toCharArray()) {
            if (c == 10026 || c == 9882 || c == 11088) {
               ++count;
            }
         }

         return count;
      }
   }

   public static String resolveItemId(String baseName) {
      if (baseName.equalsIgnoreCase("Crimson Essence")) {
         return "ESSENCE_CRIMSON";
      } else if (baseName.equalsIgnoreCase("Kuudra Teeth")) {
         return "KUUDRA_TEETH";
      } else if (baseName.toLowerCase().contains("molten bracelet")) {
         return "MOLTEN_BRACELET";
      } else if (!baseName.toLowerCase().contains("hardened vitality") && !baseName.toLowerCase().contains("enchanted book")) {
         if (baseName.toLowerCase().endsWith(" shard")) {
            String core = baseName.replaceAll("(?i)\\s*shard$", "").trim();
            String var10000 = core.toUpperCase();
            return "SHARD_" + var10000.replace(" ", "_");
         } else {
            String found = LowestBinManager.findIdByName(baseName);
            return found != null && !found.isEmpty() ? found : baseName.toUpperCase().replace(" ", "_");
         }
      } else {
         return "ENCHANTMENT_HARDENED_MANA_5";
      }
   }

   private static KuudraTier extractTier(String text) {
      if (text == null) {
         return AutoCroesus.KuudraTier.UNKNOWN;
      } else {
         String lower = text.toLowerCase();
         if (lower.contains("infernal")) {
            return AutoCroesus.KuudraTier.INFERNAL;
         } else if (lower.contains("fiery")) {
            return AutoCroesus.KuudraTier.FIERY;
         } else if (lower.contains("burning")) {
            return AutoCroesus.KuudraTier.BURNING;
         } else if (!lower.contains("hot") || !lower.contains("key") && !lower.contains("tier") && !lower.contains("kuudra")) {
            return lower.contains("basic") ? AutoCroesus.KuudraTier.BASIC : AutoCroesus.KuudraTier.UNKNOWN;
         } else {
            return AutoCroesus.KuudraTier.HOT;
         }
      }
   }

   private static long parseQuantityFromName(String cleanName) {
      if (cleanName != null && !cleanName.isEmpty()) {
         Matcher m = Pattern.compile("x(\\d{1,10})\\s*$").matcher(cleanName);
         if (m.find()) {
            try {
               return Long.parseLong(m.group(1));
            } catch (NumberFormatException var3) {
            }
         }

         return 0L;
      } else {
         return 0L;
      }
   }

   public static int getRingDiscountPercent() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return 0;
      } else {
         int discount = 0;
         Inventory inv = mc.player.getInventory();
         int size = inv.getContainerSize();

         for(int i = 0; i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
               String name = stack.getHoverName().getString().toLowerCase();
               if (name.contains("seal of the family")) {
                  return 3;
               }

               if (name.contains("crooked artifact") && discount < 2) {
                  discount = 2;
               } else if (name.contains("shady ring") && discount < 1) {
                  discount = 1;
               }
            }
         }

         return discount;
      }
   }

   public static String getRingDiscountName(int discountPercent) {
      if (discountPercent >= 3) {
         return "Seal of the Family (-3%)";
      } else if (discountPercent == 2) {
         return "Crooked Artifact (-2%)";
      } else {
         return discountPercent == 1 ? "Shady Ring (-1%)" : "None (0%)";
      }
   }

   public static KeyCostDetail getKeyCostDetail(KuudraTier tier) {
      KeyCostDetail detail = new KeyCostDetail();
      detail.tier = tier;
      detail.baseCoins = tier.baseKeyCost;
      detail.discountPercent = getRingDiscountPercent();
      if (BomboConfig.get().sealOfFamilyLevel > 0 && detail.discountPercent < BomboConfig.get().sealOfFamilyLevel) {
         detail.discountPercent = BomboConfig.get().sealOfFamilyLevel;
      }

      detail.discountName = getRingDiscountName(detail.discountPercent);
      detail.discountedCoins = detail.baseCoins * (long)(100 - detail.discountPercent) / 100L;
      String factionStr = KuudraSummaryOverlay.selectedFaction;
      boolean isMage = "mages".equalsIgnoreCase(factionStr) || "mage".equalsIgnoreCase(factionStr);
      boolean isBarb = "barbarians".equalsIgnoreCase(factionStr) || "barbarian".equalsIgnoreCase(factionStr);
      long redSandPrice = LowestBinManager.getCachedPrice("ENCHANTED_RED_SAND");
      if (redSandPrice <= 0L) {
         redSandPrice = 1000L;
      }

      long mycPrice = LowestBinManager.getCachedPrice("ENCHANTED_MYCELIUM");
      if (mycPrice <= 0L) {
         mycPrice = 1000L;
      }

      long netherStarPrice = LowestBinManager.getCachedPrice("NETHER_STAR");
      if (netherStarPrice <= 0L) {
         netherStarPrice = 20000L;
      }

      if (isMage) {
         detail.faction = "Mage";
         detail.matName = "Enchanted Mycelium";
         detail.matUnitPrice = mycPrice;
      } else if (isBarb) {
         detail.faction = "Barbarian";
         detail.matName = "Enchanted Red Sand";
         detail.matUnitPrice = redSandPrice;
      } else {
         detail.faction = "Mage / Barbarian";
         if (mycPrice <= redSandPrice) {
            detail.matName = "Enchanted Mycelium";
            detail.matUnitPrice = mycPrice;
         } else {
            detail.matName = "Enchanted Red Sand";
            detail.matUnitPrice = redSandPrice;
         }
      }

      switch (tier.ordinal()) {
         case 0:
            detail.matQty = 10;
            break;
         case 1:
            detail.matQty = 20;
            break;
         case 2:
            detail.matQty = 40;
            break;
         case 3:
            detail.matQty = 60;
            break;
         case 4:
            detail.matQty = 80;
            detail.netherStarQty = 2;
            detail.netherStarUnitPrice = netherStarPrice;
            detail.netherStarTotalPrice = 2L * netherStarPrice;
            break;
         default:
            detail.matQty = 0;
      }

      detail.matTotalPrice = (long)detail.matQty * detail.matUnitPrice;
      detail.totalKeyCost = detail.discountedCoins + detail.matTotalPrice + detail.netherStarTotalPrice;
      return detail;
   }

   private static long calculateKeyCost(KuudraTier tier, BomboConfig.Settings s) {
      return getKeyCostDetail(tier).totalKeyCost;
   }

   public static String[] getPlayerKuudraPetInfo() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         Inventory inv = mc.player.getInventory();
         int invSize = inv.getContainerSize();

         for(int i = 0; i < invSize; ++i) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
               String id = SkyblockUtils.getInternalId(stack);
               if (id != null && id.contains("KUUDRA")) {
                  try {
                     CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
                     if (customData != null) {
                        CompoundTag tag = customData.copyTag();
                        CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                        if (ea != null) {
                           String petInfoStr = ea.getString("petInfo").orElse("");
                           if (!petInfoStr.isEmpty()) {
                              JsonObject petObj = JsonParser.parseString(petInfoStr).getAsJsonObject();
                              String petTier = petObj.has("tier") ? petObj.get("tier").getAsString() : "Legendary";
                              int petLevel = petObj.has("level") ? petObj.get("level").getAsInt() : 100;
                              int essBonus = "LEGENDARY".equalsIgnoreCase(petTier) && petLevel >= 100 ? 20 : (int)((double)petLevel * 0.2);
                              return new String[]{formatPetTier(petTier), String.valueOf(petLevel), String.valueOf(essBonus)};
                           }
                        }
                     }
                  } catch (Exception var14) {
                  }
               }
            }
         }
      }

      KuudraSummaryOverlay.ensureDataFetched();
      String apiTier = KuudraSummaryOverlay.getKuudraPetTier();
      int apiLevel = KuudraSummaryOverlay.getKuudraPetLevel();
      if (!"?".equals(apiTier) && !"None".equalsIgnoreCase(apiTier) && apiTier != null && !apiTier.isEmpty()) {
         int essBonus = "LEGENDARY".equalsIgnoreCase(apiTier) && apiLevel >= 100 ? 20 : (int)((double)apiLevel * 0.2);
         return new String[]{formatPetTier(apiTier), String.valueOf(apiLevel), String.valueOf(essBonus)};
      } else {
         return new String[]{"None", "0", "0"};
      }
   }

   private static String formatPetTier(String rawTier) {
      if (rawTier != null && !rawTier.isEmpty() && !"?".equals(rawTier) && !"None".equalsIgnoreCase(rawTier)) {
         String lower = rawTier.toLowerCase();
         char var10000 = Character.toUpperCase(lower.charAt(0));
         return var10000 + lower.substring(1);
      } else {
         return "None";
      }
   }

   public static void checkCroesusNpcClick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && mc.gui.screen() == null) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s.autoCroesus && active) {
            long now = System.currentTimeMillis();
            if (now - lastActionTime >= s.autoCroesusDelay) {
               Entity targetEntity = null;
               double closestDist = (double)64.0F;

               for(Entity entity : mc.level.entitiesForRendering()) {
                  if (entity != mc.player) {
                     String name = entity.getName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");
                     String customName = entity.getCustomName() != null ? entity.getCustomName().getString().replaceAll("(?i)§[0-9a-fk-or]", "") : "";
                     if (name.contains("Croesus") || customName.contains("Croesus")) {
                        double dist = mc.player.distanceToSqr(entity);
                        if (dist < closestDist) {
                           closestDist = dist;
                           targetEntity = entity;
                        }
                     }
                  }
               }

               if (targetEntity != null && mc.gameMode != null) {
                  EntityHitResult hitResult = new EntityHitResult(targetEntity);
                  mc.gameMode.interact(mc.player, targetEntity, hitResult, InteractionHand.MAIN_HAND);
                  lastActionTime = now;
               }

            }
         }
      }
   }

   public static ProfitRecord loadProfitRecord() {
      try {
         File file = new File("bombo_croesus_profit.json");
         if (file.exists()) {
            FileReader reader = new FileReader(file);
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();
            ProfitRecord rec = new ProfitRecord();
            rec.totalRuns = json.has("totalRuns") ? json.get("totalRuns").getAsInt() : 0;
            rec.kismetsUsed = json.has("kismetsUsed") ? json.get("kismetsUsed").getAsInt() : 0;
            rec.totalProfit = json.has("totalProfit") ? json.get("totalProfit").getAsLong() : 0L;
            if (json.has("items") && json.get("items").isJsonObject()) {
               JsonObject itemsObj = json.getAsJsonObject("items");

               for(String key : itemsObj.keySet()) {
                  JsonObject itemObj = itemsObj.getAsJsonObject(key);
                  ProfitItemData d = new ProfitItemData();
                  d.name = itemObj.has("name") ? itemObj.get("name").getAsString() : key;
                  d.quantity = itemObj.has("quantity") ? itemObj.get("quantity").getAsLong() : 0L;
                  d.totalValue = itemObj.has("totalValue") ? itemObj.get("totalValue").getAsLong() : 0L;
                  rec.items.put(key, d);
               }
            }

            if (json.has("floors") && json.get("floors").isJsonObject()) {
               JsonObject floorsObj = json.getAsJsonObject("floors");

               for(String key : floorsObj.keySet()) {
                  JsonObject fo = floorsObj.getAsJsonObject(key);
                  FloorStat fs = new FloorStat();
                  fs.runs = fo.has("runs") ? fo.get("runs").getAsInt() : 0;
                  fs.kismetsUsed = fo.has("kismetsUsed") ? fo.get("kismetsUsed").getAsInt() : 0;
                  fs.profit = fo.has("profit") ? fo.get("profit").getAsLong() : 0L;
                  rec.floors.put(key, fs);
               }
            }

            return rec;
         }
      } catch (Exception var9) {
      }

      return new ProfitRecord();
   }

   public static void saveProfitRecord(ProfitRecord rec) {
      try {
         JsonObject json = new JsonObject();
         json.addProperty("totalRuns", rec.totalRuns);
         json.addProperty("kismetsUsed", rec.kismetsUsed);
         json.addProperty("totalProfit", rec.totalProfit);
         JsonObject itemsObj = new JsonObject();

         for(Map.Entry<String, ProfitItemData> entry : rec.items.entrySet()) {
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("name", ((ProfitItemData)entry.getValue()).name);
            itemObj.addProperty("quantity", ((ProfitItemData)entry.getValue()).quantity);
            itemObj.addProperty("totalValue", ((ProfitItemData)entry.getValue()).totalValue);
            itemsObj.add((String)entry.getKey(), itemObj);
         }

         json.add("items", itemsObj);
         JsonObject floorsObj = new JsonObject();

         for(Map.Entry<String, FloorStat> entry : rec.floors.entrySet()) {
            JsonObject fo = new JsonObject();
            fo.addProperty("runs", ((FloorStat)entry.getValue()).runs);
            fo.addProperty("kismetsUsed", ((FloorStat)entry.getValue()).kismetsUsed);
            fo.addProperty("profit", ((FloorStat)entry.getValue()).profit);
            floorsObj.add((String)entry.getKey(), fo);
         }

         json.add("floors", floorsObj);
         FileWriter writer = new FileWriter("bombo_croesus_profit.json");
         writer.write((new GsonBuilder()).setPrettyPrinting().create().toJson(json));
         writer.close();
      } catch (Exception var6) {
      }

   }

   /**
    * Current dungeon floor / Kuudra tier as reported by the sidebar ("F7", "M4", "T5"), so
    * the profit tracker can break earnings down per floor. Falls back to "Unknown".
    */
   public static String getCurrentFloorTag() {
      try {
         String sub = SkyblockUtils.getSubArea();
         if (sub == null) return "Unknown";
         String clean = sub.replaceAll("§[0-9a-fk-or]", "").trim();
         Matcher m = Pattern.compile("(?i)\\b([FMT][1-7])\\b").matcher(clean);
         if (m.find()) {
            return m.group(1).toUpperCase();
         }
         if (clean.isEmpty() || clean.equalsIgnoreCase("unknown")) return "Unknown";
         return clean.length() > 6 ? clean.substring(0, 6) : clean;
      } catch (Throwable t) {
         return "Unknown";
      }
   }

   /** Ordering rank so F1..F7, M1..M7, T1..T5 render in a stable, readable order. */
   public static int floorSortKey(String floor) {
      if (floor == null) return 99;
      char c = floor.isEmpty() ? ' ' : Character.toUpperCase(floor.charAt(0));
      int n = 0;
      try {
         n = Integer.parseInt(floor.replaceAll("[^0-9]", ""));
      } catch (Exception ignored) {
      }
      return switch (c) {
         case 'F' -> n;
         case 'M' -> 10 + n;
         case 'T' -> 20 + n;
         default -> 99;
      };
   }

   public static void recordChestPurchase(long profit, boolean usedKismet, List<ItemDetail> items) {
      recordChestPurchase(profit, usedKismet, items, getCurrentFloorTag());
   }

   public static void recordChestPurchase(long profit, boolean usedKismet, List<ItemDetail> items, String floor) {
      ProfitRecord rec = loadProfitRecord();
      ++rec.totalRuns;
      if (usedKismet) {
         ++rec.kismetsUsed;
      }

      rec.totalProfit += profit;

      String tag = (floor == null || floor.isBlank()) ? "Unknown" : floor;
      FloorStat fs = rec.floors.computeIfAbsent(tag, k -> new FloorStat());
      ++fs.runs;
      fs.profit += profit;
      if (usedKismet) {
         ++fs.kismetsUsed;
      }

      for(ItemDetail item : items) {
         ProfitItemData data = (ProfitItemData)rec.items.computeIfAbsent(item.itemId, (k) -> {
            ProfitItemData d = new ProfitItemData();
            d.name = item.name;
            return d;
         });
         data.name = item.name;
         data.quantity += item.adjustedQuantity;
         data.totalValue += item.totalValue;
      }

      saveProfitRecord(rec);
      me.bombo.bomboaddons.features.dungeons.CroesusProfitTrackerHud.markDirty();
   }

   public static void resetProfitTracker() {
      try {
         File file = new File("bombo_croesus_profit.json");
         if (file.exists()) {
            file.delete();
         }
      } catch (Exception var1) {
      }

   }

   public static void syncProfitData(FabricClientCommandSource source) {
      Minecraft mc = Minecraft.getInstance();
      String ign = mc.getUser() != null ? mc.getUser().getName() : "user";
      ProfitRecord rec = loadProfitRecord();
      CompletableFuture.runAsync(() -> {
         try {
            JsonObject payload = new JsonObject();
            payload.addProperty("ign", ign);
            payload.addProperty("totalRuns", rec.totalRuns);
            payload.addProperty("kismetsUsed", rec.kismetsUsed);
            payload.addProperty("totalProfit", rec.totalProfit);
            JsonObject itemsObj = new JsonObject();

            for(Map.Entry<String, ProfitItemData> entry : rec.items.entrySet()) {
               JsonObject itemObj = new JsonObject();
               itemObj.addProperty("name", ((ProfitItemData)entry.getValue()).name);
               itemObj.addProperty("quantity", ((ProfitItemData)entry.getValue()).quantity);
               itemObj.addProperty("totalValue", ((ProfitItemData)entry.getValue()).totalValue);
               itemsObj.add((String)entry.getKey(), itemObj);
            }

            payload.add("items", itemsObj);
            URL url = new URL("https://api.bombo.dpdns.org/kuudra/profit/sync");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            OutputStream os = conn.getOutputStream();

            try {
               byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
               os.write(input, 0, input.length);
            } catch (Throwable var11) {
               if (os != null) {
                  try {
                     os.close();
                  } catch (Throwable x2) {
                     var11.addSuppressed(x2);
                  }
               }

               throw var11;
            }

            if (os != null) {
               os.close();
            }

            int code = conn.getResponseCode();
            if (code == 200) {
               if (source != null) {
                  source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §aProfit statistics synced to server successfully!"));
               }
            } else if (source != null) {
               source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §cFailed to sync profit statistics (HTTP " + code + ")"));
            }
         } catch (Exception e) {
            if (source != null) {
               source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §cSync error: " + e.getMessage()));
            }
         }

      });
   }

   public static void printProfitSummary(FabricClientCommandSource source) {
      ProfitRecord rec = loadProfitRecord();
      long avgProfit = rec.totalRuns > 0 ? rec.totalProfit / (long)rec.totalRuns : 0L;
      String profitColor = rec.totalProfit >= 0L ? "§a+" : "§c";
      String avgColor = avgProfit >= 0L ? "§a+" : "§c";
      source.sendFeedback(Component.literal("§8---------------------------------------------------------"));
      source.sendFeedback(Component.literal("§8--- §b[AutoCroesus Cumulative Profit Statistics] §8---"));
      source.sendFeedback(Component.literal("§7Total Runs Claimed: §e" + rec.totalRuns));
      source.sendFeedback(Component.literal("§7Kismet Feathers Used: §d" + rec.kismetsUsed));
      source.sendFeedback(Component.literal("§7Total Net Profit: " + profitColor + String.format("%,d", rec.totalProfit) + " coins"));
      source.sendFeedback(Component.literal("§7Average Profit / Run: " + avgColor + String.format("%,d", avgProfit) + " coins/run"));
      if (!rec.floors.isEmpty()) {
         source.sendFeedback(Component.literal("§8--- §6Per-Floor Breakdown §8---"));
         List<Map.Entry<String, FloorStat>> floors = new ArrayList<>(rec.floors.entrySet());
         floors.sort((a, b) -> Integer.compare(floorSortKey(a.getKey()), floorSortKey(b.getKey())));
         for (Map.Entry<String, FloorStat> e : floors) {
            FloorStat fs = e.getValue();
            long avg = fs.runs > 0 ? fs.profit / fs.runs : 0L;
            source.sendFeedback(Component.literal("§7" + e.getKey() + " §8| "
                  + (fs.profit >= 0 ? "§a+" : "§c-") + String.format("%,d", Math.abs(fs.profit))
                  + " §7in §e" + fs.runs + " run" + (fs.runs == 1 ? "" : "s")
                  + " §8(§7avg " + (avg >= 0 ? "§a+" : "§c-") + String.format("%,d", Math.abs(avg)) + "§8)"));
         }
      }
      if (rec.items.isEmpty()) {
         source.sendFeedback(Component.literal("§8[§eItem Profit Breakdown: §7No items logged yet.§8]"));
      } else {
         List<ProfitItemData> sorted = new ArrayList(rec.items.values());
         sorted.sort((a, b) -> Long.compare(b.totalValue, a.totalValue));
         StringBuilder hoverText = new StringBuilder("§6§lItem Profit Breakdown:\n");
         int rank = 1;

         for(ProfitItemData item : sorted) {
            hoverText.append(String.format("§e%d. §f%s §7x%,d §7-> §a%,d coins\n", rank, item.name, item.quantity, item.totalValue));
            ++rank;
         }

         if (hoverText.length() > 0) {
            hoverText.setLength(hoverText.length() - 1);
         }

         MutableComponent hoverComponent = Component.literal("§8[§eHover to View Item Breakdown (§b" + rec.items.size() + " items§e)§8]");
         hoverComponent.setStyle(hoverComponent.getStyle().withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText.toString()))));
         source.sendFeedback(hoverComponent);
      }

      source.sendFeedback(Component.literal("§8---------------------------------------------------------"));
   }

   private static String getTierColor(KuudraTier tier) {
      switch (tier.ordinal()) {
         case 0 -> {
            return "§f";
         }
         case 1 -> {
            return "§e";
         }
         case 2 -> {
            return "§6";
         }
         case 3 -> {
            return "§c";
         }
         case 4 -> {
            return "§4";
         }
         default -> {
            return "§7";
         }
      }
   }

   public static void showStarSalvage(FabricClientCommandSource source, String itemType, int stars) {
      if (stars < 0) {
         stars = 0;
      }

      if (stars > 10) {
         stars = 10;
      }

      String type = itemType != null && !itemType.isEmpty() ? itemType.toLowerCase().trim() : "helmet";
      int baseEssence = 100;
      if (type.contains("molten") || type.contains("belt") || type.contains("cloak") || type.contains("necklace") || type.contains("bracelet") || type.contains("wand")) {
         baseEssence = 500;
      }

      long rawEssence = (long)baseEssence;
      if (baseEssence == 100) {
         rawEssence = (long)getArmorSalvageBase(stars);
      }

      int cooledForgesLevel = KuudraSummaryOverlay.getCooledForgesLevel();
      int cooledForgesPct = cooledForgesLevel * 4;
      long cooledEssence = Math.round((double)rawEssence * ((double)1.0F + (double)cooledForgesPct / (double)100.0F));
      long crimsonUnitPrice = LowestBinManager.getCachedPrice("ESSENCE_CRIMSON");
      if (crimsonUnitPrice <= 0L) {
         crimsonUnitPrice = 962L;
      }

      long rawValue = rawEssence * crimsonUnitPrice;
      long cooledValue = cooledEssence * crimsonUnitPrice;
      String itemLabel = stars + "-Star " + toTitleCase(type);
      source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §6Star Salvage Breakdown for §e" + itemLabel + "§6:"));
      String var10001 = String.format("%,d", rawEssence);
      source.sendFeedback(Component.literal("§7  • Base Crimson Essence: §d" + var10001 + " Crimson Essence §7(§6" + LowestBinManager.formatPrice(rawValue) + " coins§7)"));
      source.sendFeedback(Component.literal("§7  • With Cooled Forge (Lvl " + cooledForgesLevel + ", +" + cooledForgesPct + "%): §d" + String.format("%,d", cooledEssence) + " Crimson Essence §7(§6" + LowestBinManager.formatPrice(cooledValue) + " coins§7)"));
   }

   private static String toTitleCase(String s) {
      if (s != null && !s.isEmpty()) {
         char var10000 = Character.toUpperCase(s.charAt(0));
         return var10000 + s.substring(1).toLowerCase();
      } else {
         return "";
      }
   }

   static {
      storedTier = AutoCroesus.KuudraTier.UNKNOWN;
      lastRunTier = "Unknown";
      lastRunKeyCost = 0L;
      lastRunContentsValue = 0L;
      lastRunProfit = 0L;
      lastRunCrimsonEssence = 0L;
      lastRunKuudraTeeth = 0L;
      lastRunKrakenShards = 0L;
      lastRunTimestamp = 0L;
      lastRunItems = new ArrayList();
      lastRunEssenceBonusPercent = 0;
      ARMOR_STAR_ESSENCE = new int[]{100, 115, 132, 152, 175, 200, 227, 257, 290, 326, 366};
      BASE_SALVAGE_MAP = new HashMap();
      BASE_SALVAGE_MAP.put("MOLTEN_BRACELET", 500);
      BASE_SALVAGE_MAP.put("MOLTEN_BELT", 500);
      BASE_SALVAGE_MAP.put("MOLTEN_CLOAK", 500);
      BASE_SALVAGE_MAP.put("MOLTEN_NECKLACE", 500);
      BASE_SALVAGE_MAP.put("AURORA_HELMET", 100);
      BASE_SALVAGE_MAP.put("AURORA_CHESTPLATE", 100);
      BASE_SALVAGE_MAP.put("AURORA_LEGGINGS", 100);
      BASE_SALVAGE_MAP.put("AURORA_BOOTS", 100);
      BASE_SALVAGE_MAP.put("CRIMSON_HELMET", 100);
      BASE_SALVAGE_MAP.put("CRIMSON_CHESTPLATE", 100);
      BASE_SALVAGE_MAP.put("CRIMSON_LEGGINGS", 100);
      BASE_SALVAGE_MAP.put("CRIMSON_BOOTS", 100);
      BASE_SALVAGE_MAP.put("TERROR_HELMET", 100);
      BASE_SALVAGE_MAP.put("TERROR_CHESTPLATE", 100);
      BASE_SALVAGE_MAP.put("TERROR_LEGGINGS", 100);
      BASE_SALVAGE_MAP.put("TERROR_BOOTS", 100);
      BASE_SALVAGE_MAP.put("HOLLOW_HELMET", 100);
      BASE_SALVAGE_MAP.put("HOLLOW_CHESTPLATE", 100);
      BASE_SALVAGE_MAP.put("HOLLOW_LEGGINGS", 100);
      BASE_SALVAGE_MAP.put("HOLLOW_BOOTS", 100);
      BASE_SALVAGE_MAP.put("FERVOR_HELMET", 100);
      BASE_SALVAGE_MAP.put("FERVOR_CHESTPLATE", 100);
      BASE_SALVAGE_MAP.put("FERVOR_LEGGINGS", 100);
      BASE_SALVAGE_MAP.put("FERVOR_BOOTS", 100);
      BASE_SALVAGE_MAP.put("ENCHANTMENT_HARDENED_MANA_5", 100);
      BASE_SALVAGE_MAP.put("ENCHANTMENT_HARDENED_VITALITY_5", 100);
      BASE_SALVAGE_MAP.put("HOLLOW_WAND", 500);
      BASE_SALVAGE_MAP.put("TORMENTOR", 500);
      BASE_SALVAGE_MAP.put("FLAMING_FIST", 50);
      BASE_SALVAGE_MAP.put("TAURUS_HELMET", 30);
      BASE_SALVAGE_MAP.put("FLAMING_CHESTPLATE", 25);
      BASE_SALVAGE_MAP.put("MOOGMA_LEGGINGS", 20);
      BASE_SALVAGE_MAP.put("SLUG_BOOTS", 15);
      BASE_SALVAGE_MAP.put("BLADE_OF_THE_VOLCANO", 10);
      BASE_SALVAGE_MAP.put("STAFF_OF_THE_VOLCANO", 10);
      BASE_SALVAGE_MAP.put("RAMPART_HELMET", 5);
      BASE_SALVAGE_MAP.put("RAMPART_CHESTPLATE", 5);
      BASE_SALVAGE_MAP.put("RAMPART_LEGGINGS", 5);
      BASE_SALVAGE_MAP.put("RAMPART_BOOTS", 5);
      active = false;
      isProgrammaticClose = false;
      needsNpcClick = false;
      lastOverviewScreen = null;
      overviewGuiOpenTime = 0L;
      lastScreen = null;
      boughtCurrentChest = false;
      lastRunParsedDetails = new ArrayList();
      hasRerolledCurrentChest = false;
      lastActionTime = 0L;
      hudActionStatus = "";
      neverKismetBlacklist = new HashSet();
      lastBlacklistFetch = 0L;
   }

   private static enum KuudraTier {
      BASIC("Basic", 10000L),
      HOT("Hot", 50000L),
      BURNING("Burning", 200000L),
      FIERY("Fiery", 600000L),
      INFERNAL("Infernal", 2400000L),
      UNKNOWN("Unknown", 0L);

      final String displayName;
      final long baseKeyCost;

      private KuudraTier(String displayName, long baseKeyCost) {
         this.displayName = displayName;
         this.baseKeyCost = baseKeyCost;
      }

      // $FF: synthetic method
      private static KuudraTier[] $values() {
         return new KuudraTier[]{BASIC, HOT, BURNING, FIERY, INFERNAL, UNKNOWN};
      }
   }

   public static class ItemDetail {
      public String name;
      public String originalLore;
      public String itemId;
      public long baseQuantity;
      public long adjustedQuantity;
      public long unitPrice;
      public long totalValue;
      public String priceSource;
      public long salvagedEssenceAmount;
      public int baseEssence;
      public int starCount;
      public boolean isCrimsonEssence;
      public boolean hasPetBonus;
   }

   public static class KeyCostDetail {
      public KuudraTier tier;
      public long baseCoins;
      public int discountPercent;
      public String discountName;
      public long discountedCoins;
      public String faction;
      public String matName;
      public int matQty;
      public long matUnitPrice;
      public long matTotalPrice;
      public int netherStarQty;
      public long netherStarUnitPrice;
      public long netherStarTotalPrice;
      public long totalKeyCost;

      public KeyCostDetail() {
         this.tier = AutoCroesus.KuudraTier.UNKNOWN;
         this.baseCoins = 0L;
         this.discountPercent = 0;
         this.discountName = "None";
         this.discountedCoins = 0L;
         this.faction = "Mage / Barbarian";
         this.matName = "Material";
         this.matQty = 0;
         this.matUnitPrice = 0L;
         this.matTotalPrice = 0L;
         this.netherStarQty = 0;
         this.netherStarUnitPrice = 0L;
         this.netherStarTotalPrice = 0L;
         this.totalKeyCost = 0L;
      }
   }

   public static class ProfitItemData {
      public String name = "";
      public long quantity = 0L;
      public long totalValue = 0L;
   }

   public static class ProfitRecord {
      public int totalRuns = 0;
      public int kismetsUsed = 0;
      public long totalProfit = 0L;
      public Map<String, ProfitItemData> items = new HashMap();
      /** Per-floor (F1..F7 / M1..M7 / T1..T5) breakdown, keyed by the sidebar floor tag. */
      public Map<String, FloorStat> floors = new java.util.LinkedHashMap();
   }

   /** Claimed runs and coins earned on one dungeon floor / Kuudra tier. */
   public static class FloorStat {
      public int runs = 0;
      public int kismetsUsed = 0;
      public long profit = 0L;
   }
}
