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
   public static boolean isProgrammaticClose;
   public static boolean needsNpcClick;
   private static AbstractContainerScreen<?> lastOverviewScreen;
   private static long overviewGuiOpenTime;
   private static AbstractContainerScreen<?> lastScreen;
   public static boolean boughtCurrentChest;
   public static List<ItemDetail> lastRunParsedDetails;
   private static boolean hasRerolledCurrentChest;
   private static long lastActionTime;
   public static String hudActionStatus;
   private static Set<String> neverKismetBlacklist;
   private static long lastBlacklistFetch;
   private static final String PROFIT_FILE = "bombo_croesus_profit.json";

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

   public static void startActive(FabricClientCommandSource source) {
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
         needsNpcClick = false;
         isProgrammaticClose = false;
         if (source != null) {
            source.sendFeedback(Component.literal("§8[§bAutoCroesus§8] §cAuto Croesus stopped (Kill Switch / ESC pressed)!"));
         } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §cAuto Croesus stopped (Kill Switch / ESC pressed)!"));
         }

      }
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

      Screen var4 = mc.screen;
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
                                    clickSlot(screen.getMenu().containerId, slot.index);
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

   public static boolean isKuudraChestGui(AbstractContainerScreen<?> screen) {
      if (screen == null) {
         return false;
      } else {
         String rawTitle = screen.getTitle().getString();
         String cleanTitle = rawTitle.replaceAll("§[0-9a-fk-or]", "").trim();
         String lower = cleanTitle.toLowerCase();
         return lower.startsWith("kuudra - ") || lower.contains("croesus") || lower.equalsIgnoreCase("paid chest") || lower.equalsIgnoreCase("free chest") || lower.contains("chest") && !lower.contains("inventory");
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
               lastActionTime = 0L;
            }

            if (isKuudraChestGui(screen)) {
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
                           if (detail.totalValue <= 0L && !detail.isCrimsonEssence && !detail.itemId.equals("KUUDRA_TEETH") && !isShardOrBook) {
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
                              hudActionStatus = "§d[RNG Drop: " + blacklistedItemName + "!]";
                              clickSlot(screen.getMenu().containerId, paidChestSlot.index);
                              lastActionTime = now;
                              mc.player.sendSystemMessage(Component.literal("§8[§bAutoCroesus§8] §dValuable RNG drop detected (\"§e" + blacklistedItemName + "§d\")! Skipping Kismet Feather reroll and auto-buying!"));
                              return;
                           }

                           boolean shouldReroll = !hasRerolledCurrentChest && rerollAvailable && !hasBlacklistedItem && (s.autoCroesusReroll && profit < s.autoCroesusRerollValue || s.autoKismet && totalContentsValue < s.kismetThreshold);
                           if (shouldReroll && rerollSlot != null) {
                              hudActionStatus = "§d[Rerolling with Kismet...]";
                              clickSlot(screen.getMenu().containerId, rerollSlot.index);
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
                              hudActionStatus = "§a[Buying Paid Chest...]";
                              clickSlot(screen.getMenu().containerId, paidChestSlot.index);
                              lastActionTime = now;
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
      if (mc.player != null && mc.level != null && mc.screen == null) {
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
         FileWriter writer = new FileWriter("bombo_croesus_profit.json");
         writer.write((new GsonBuilder()).setPrettyPrinting().create().toJson(json));
         writer.close();
      } catch (Exception var6) {
      }

   }

   public static void recordChestPurchase(long profit, boolean usedKismet, List<ItemDetail> items) {
      ProfitRecord rec = loadProfitRecord();
      ++rec.totalRuns;
      if (usedKismet) {
         ++rec.kismetsUsed;
      }

      rec.totalProfit += profit;

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

   private static class ItemDetail {
      String name;
      String originalLore;
      String itemId;
      long baseQuantity;
      long adjustedQuantity;
      long unitPrice;
      long totalValue;
      String priceSource;
      long salvagedEssenceAmount;
      int baseEssence;
      int starCount;
      boolean isCrimsonEssence;
      boolean hasPetBonus;
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
   }
}
