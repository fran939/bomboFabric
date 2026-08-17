package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag.Default;

public class ComposterHud {
   private static final Pattern VALUE_PATTERN = Pattern.compile("([\\d,]+(?:\\.\\d+)?)([kmb])?/([\\d,]+(?:\\.\\d+)?)([kmb])?");
   private static final Pattern SPEED_PERCENT_PATTERN = Pattern.compile("speed by (\\d+)%");
   private static final Pattern COST_PERCENT_PATTERN = Pattern.compile("by (\\d+)%");

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "composter_hud"), ComposterHud::render);
   }

   public static void onContainerTick(AbstractContainerScreen<?> screen) {
      if (screen != null) {
         String title = screen.getTitle().getString();
         if (title.contains("Composter")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
               boolean isUpgradesMenu = title.contains("Upgrades");
               BomboConfig.Settings s = BomboConfig.get();
               boolean configChanged = false;

               for(Slot slot : screen.getMenu().slots) {
                  if (slot.hasItem()) {
                     ItemStack stack = slot.getItem();
                     String name = stack.getHoverName().getString();
                     List<Component> tooltip = stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, Default.NORMAL);
                     if (name.contains("Organic Matter")) {
                        if (parseValueSlot(tooltip, true)) {
                           configChanged = true;
                        }
                     } else if (name.contains("Fuel") && parseValueSlot(tooltip, false)) {
                        configChanged = true;
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
               }

               if (configChanged) {
                  s.composterLastSavedTime = System.currentTimeMillis();
                  BomboConfig.save();
               }

            }
         }
      }
   }

   private static int parseLevelFromNameOrTooltip(String name, List<Component> tooltip, Pattern percentPattern, int percentPerLevel) {
      String[] parts = name.split(" ");
      if (parts.length > 0) {
         String lastPart = parts[parts.length - 1];

         try {
            int romanVal = RomanNumber.romanToDecimal(lastPart);
            if (romanVal > 0) {
               return romanVal;
            }
         } catch (Exception var10) {
         }
      }

      for(Component line : tooltip) {
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

      for(Component line : tooltip) {
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
            case "k" -> val *= (double)1000.0F;
            case "m" -> val *= (double)1000000.0F;
            case "b" -> val *= (double)1.0E9F;
         }
      }

      return val;
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.composterHud || s.composterTimerHud) {
         Minecraft client = Minecraft.getInstance();
         if (!client.options.hideGui) {
            if (client.screen == null || client.screen instanceof HudMoveScreen || client.screen instanceof AbstractContainerScreen) {
               Screen var5 = client.screen;
               if (var5 instanceof AbstractContainerScreen) {
                  AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen)var5;
                  onContainerTick(containerScreen);
               }

               if (s.composterHud && (s.composterLastOrganic >= (double)0.0F || s.composterLastFuel >= (double)0.0F)) {
                  drawComposterInfo(g, s.composterHudX, s.composterHudY, false);
               }

               if (s.composterTimerHud && (s.composterLastOrganic >= (double)0.0F || s.composterLastFuel >= (double)0.0F)) {
                  drawComposterTimerInfo(g, s.composterTimerHudX, s.composterTimerHudY, false);
               }

            }
         }
      }
   }

   public static void drawComposterTimerInfo(GuiGraphicsExtractor g, int x, int y, boolean isDummy) {
      BomboConfig.Settings s = BomboConfig.get();
      double baseOrgCur = isDummy ? (double)1200.0F : Math.max((double)0.0F, s.composterLastOrganic);
      double baseFuelCur = isDummy ? (double)464937.5F : Math.max((double)0.0F, s.composterLastFuel);
      int speedLvl = s.composterSpeedLevel >= 0 ? s.composterSpeedLevel : (isDummy ? 25 : 0);
      int costLvl = s.composterCostReductionLevel >= 0 ? s.composterCostReductionLevel : (isDummy ? 25 : 0);
      boolean missingUpgrades = (s.composterSpeedLevel < 0 || s.composterCostReductionLevel < 0) && !isDummy;
      double speedMultiplier = (double)1.0F + (double)speedLvl * 0.2;
      double timePerCompostSec = (double)600.0F / speedMultiplier;
      double costMultiplier = (double)1.0F - (double)costLvl * 0.01;
      double orgPerCompost = (double)4000.0F * costMultiplier;
      double fuelPerCompost = (double)2000.0F * costMultiplier;
      double orgRatePerSec = orgPerCompost / timePerCompostSec;
      double fuelRatePerSec = fuelPerCompost / timePerCompostSec;
      double elapsedSec = !isDummy && s.composterLastSavedTime > 0L ? (double)(System.currentTimeMillis() - s.composterLastSavedTime) / (double)1000.0F : (double)0.0F;
      double usableBaseOrg = Math.max((double)0.0F, baseOrgCur - orgPerCompost);
      double usableBaseFuel = Math.max((double)0.0F, baseFuelCur - fuelPerCompost);
      double maxRunningSec = Math.min(orgRatePerSec > (double)0.0F ? usableBaseOrg / orgRatePerSec : (double)0.0F, fuelRatePerSec > (double)0.0F ? usableBaseFuel / fuelRatePerSec : (double)0.0F);
      double actualElapsedSec = Math.min(elapsedSec, maxRunningSec);
      double orgCur = Math.max((double)0.0F, baseOrgCur - orgRatePerSec * actualElapsedSec);
      double fuelCur = Math.max((double)0.0F, baseFuelCur - fuelRatePerSec * actualElapsedSec);
      double usableOrg = Math.max((double)0.0F, orgCur - orgPerCompost);
      double usableFuel = Math.max((double)0.0F, fuelCur - fuelPerCompost);
      double secondsOrg = usableOrg > (double)0.0F && orgRatePerSec > (double)0.0F ? usableOrg / orgRatePerSec : (double)0.0F;
      double secondsFuel = usableFuel > (double)0.0F && fuelRatePerSec > (double)0.0F ? usableFuel / fuelRatePerSec : (double)0.0F;
      double timeUntilEmptySec = Math.min(secondsOrg, secondsFuel);
      String line;
      if (missingUpgrades) {
         line = "§eOpen Upgrades Menu!";
      } else if (timeUntilEmptySec <= (double)0.0F) {
         line = "§cEmpty!";
      } else {
         line = "§c" + formatTime((long)timeUntilEmptySec);
      }

      g.pose().pushMatrix();
      g.pose().translate((float)x, (float)y);
      float scale = s.composterTimerHudScale;
      g.pose().scale(scale, scale);
      ItemStack compostStack = SkyblockItemManager.createSkyblockItem("COMPOST");
      if (compostStack.isEmpty()) {
         compostStack = new ItemStack(Items.COMPOSTER);
      }

      g.pose().pushMatrix();
      g.pose().translate(0.0F, -2.0F);
      g.pose().scale(0.75F, 0.75F);
      g.item(compostStack, 0, 0);
      g.pose().popMatrix();
      g.text(Minecraft.getInstance().font, line, 15, 0, -1, true);
      g.pose().popMatrix();
   }

   public static void drawComposterInfo(GuiGraphicsExtractor g, int x, int y, boolean isDummy) {
      BomboConfig.Settings s = BomboConfig.get();
      double baseOrgCur = isDummy ? (double)1200.0F : Math.max((double)0.0F, s.composterLastOrganic);
      double orgMax = isDummy ? (double)790000.0F : Math.max((double)0.0F, s.composterLastMaxOrganic);
      double baseFuelCur = isDummy ? (double)464937.5F : Math.max((double)0.0F, s.composterLastFuel);
      double fuelMax = isDummy ? (double)850000.0F : Math.max((double)0.0F, s.composterLastMaxFuel);
      int speedLvl = s.composterSpeedLevel >= 0 ? s.composterSpeedLevel : (isDummy ? 25 : 0);
      int costLvl = s.composterCostReductionLevel >= 0 ? s.composterCostReductionLevel : (isDummy ? 25 : 0);
      boolean missingUpgrades = (s.composterSpeedLevel < 0 || s.composterCostReductionLevel < 0) && !isDummy;
      double speedMultiplier = (double)1.0F + (double)speedLvl * 0.2;
      double timePerCompostSec = (double)600.0F / speedMultiplier;
      double costMultiplier = (double)1.0F - (double)costLvl * 0.01;
      double orgPerCompost = (double)4000.0F * costMultiplier;
      double fuelPerCompost = (double)2000.0F * costMultiplier;
      double orgRatePerSec = orgPerCompost / timePerCompostSec;
      double fuelRatePerSec = fuelPerCompost / timePerCompostSec;
      double elapsedSec = !isDummy && s.composterLastSavedTime > 0L ? (double)(System.currentTimeMillis() - s.composterLastSavedTime) / (double)1000.0F : (double)0.0F;
      double usableBaseOrg = Math.max((double)0.0F, baseOrgCur - orgPerCompost);
      double usableBaseFuel = Math.max((double)0.0F, baseFuelCur - fuelPerCompost);
      double maxRunningSec = Math.min(orgRatePerSec > (double)0.0F ? usableBaseOrg / orgRatePerSec : (double)0.0F, fuelRatePerSec > (double)0.0F ? usableBaseFuel / fuelRatePerSec : (double)0.0F);
      double actualElapsedSec = Math.min(elapsedSec, maxRunningSec);
      double orgCur = Math.max((double)0.0F, baseOrgCur - orgRatePerSec * actualElapsedSec);
      double fuelCur = Math.max((double)0.0F, baseFuelCur - fuelRatePerSec * actualElapsedSec);
      double usableOrg = Math.max((double)0.0F, orgCur - orgPerCompost);
      double usableFuel = Math.max((double)0.0F, fuelCur - fuelPerCompost);
      double secondsOrg = usableOrg > (double)0.0F && orgRatePerSec > (double)0.0F ? usableOrg / orgRatePerSec : (double)0.0F;
      double secondsFuel = usableFuel > (double)0.0F && fuelRatePerSec > (double)0.0F ? usableFuel / fuelRatePerSec : (double)0.0F;
      double timeUntilEmptySec = Math.min(secondsOrg, secondsFuel);
      boolean orgLimiting = secondsOrg <= secondsFuel;
      List<String> lines = new ArrayList();
      lines.add("§a§lComposter Status");
      String var10001 = formatNum(orgCur);
      lines.add("§fOrganic: §e" + var10001 + "§7/§e" + formatNum(orgMax) + " §7(" + String.format("%.1f", orgCur > (double)0.0F && orgMax > (double)0.0F ? orgCur / orgMax * (double)100.0F : (double)0.0F) + "%)");
      var10001 = formatNum(fuelCur);
      lines.add("§fFuel: §b" + var10001 + "§7/§b" + formatNum(fuelMax) + " §7(" + String.format("%.1f", fuelCur > (double)0.0F && fuelMax > (double)0.0F ? fuelCur / fuelMax * (double)100.0F : (double)0.0F) + "%)");
      if (missingUpgrades) {
         lines.add("§cTime Left: §eOpen Upgrades Menu!");
      } else if (timeUntilEmptySec <= (double)0.0F) {
         lines.add("§cComposter is empty!");
      } else {
         var10001 = formatTime((long)timeUntilEmptySec);
         lines.add("§fTime Left: §c" + var10001 + (!(orgCur > (double)0.0F) && !(fuelCur > (double)0.0F) ? "" : " §7(" + (orgLimiting ? "Organic" : "Fuel") + ")"));
      }

      if (s.composterDebug) {
         lines.add("§8[Debug] Speed Lvl: " + speedLvl + " (+" + speedLvl * 20 + "%) | Cost Lvl: " + costLvl + " (-" + costLvl + "%)");
         var10001 = String.format("%.1fs", actualElapsedSec);
         lines.add("§8[Debug] Elapsed: " + var10001 + " | Rate: " + String.format("%.2f", orgRatePerSec) + " Org/s, " + String.format("%.2f", fuelRatePerSec) + " Fuel/s");
      }

      g.pose().pushMatrix();
      g.pose().translate((float)x, (float)y);
      float scale = s.composterHudScale;
      g.pose().scale(scale, scale);
      ItemStack compostStack = SkyblockItemManager.createSkyblockItem("COMPOST");
      if (compostStack.isEmpty()) {
         compostStack = new ItemStack(Items.COMPOSTER);
      }

      int curY = 0;

      for(int i = 0; i < lines.size(); ++i) {
         if (i == 0) {
            g.pose().pushMatrix();
            g.pose().translate(0.0F, -2.0F);
            g.pose().scale(0.75F, 0.75F);
            g.item(compostStack, 0, 0);
            g.pose().popMatrix();
            g.text(Minecraft.getInstance().font, (String)lines.get(0), 15, curY, -1, true);
         } else {
            g.text(Minecraft.getInstance().font, (String)lines.get(i), 0, curY, -1, true);
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
      if (num >= (double)1000000.0F) {
         return String.format("%.1fm", num / (double)1000000.0F);
      } else {
         return num >= (double)1000.0F ? String.format("%.1fk", num / (double)1000.0F) : String.format("%.0f", num);
      }
   }

   private static String formatTime(long totalSeconds) {
      if (totalSeconds <= 0L) {
         return "0s";
      } else {
         long hours = totalSeconds / 3600L;
         long minutes = totalSeconds % 3600L / 60L;
         long seconds = totalSeconds % 60L;
         if (hours > 0L) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
         } else {
            return minutes > 0L ? String.format("%dm %ds", minutes, seconds) : String.format("%ds", seconds);
         }
      }
   }
}
