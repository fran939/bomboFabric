package me.bombo.bomboaddons.features;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class SupercraftHelper {
   private static final Pattern MISSING_ITEMS_PATTERN = Pattern.compile("([0-9,]+)x\\s+missing\\s+items", Pattern.CASE_INSENSITIVE);
   private static final Pattern CRAFTING_AMOUNT_PATTERN = Pattern.compile("Crafting\\s+(\\d+)\\s+items", Pattern.CASE_INSENSITIVE);
   private static final Pattern STARTS_IN_PATTERN = Pattern.compile("Starts\\s+in:\\s*(?:(\\d+)d\\s*)?(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(?:(\\d+)s)?", Pattern.CASE_INSENSITIVE);

   public static String getCanceledOrderAmount(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return null;
      String name = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");
      if (!name.contains("Cancel Order")) return null;

      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) return null;

      for (Component line : lore.lines()) {
         String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
         Matcher m = MISSING_ITEMS_PATTERN.matcher(clean);
         if (m.find()) {
            return m.group(1).replace(",", "").trim();
         }
      }
      return null;
   }

   public static int getCraftAmountPerCraft(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return 1;
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) return 1;

      for (Component line : lore.lines()) {
         String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
         Matcher m = CRAFTING_AMOUNT_PATTERN.matcher(clean);
         if (m.find()) {
            try {
               return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
         }
      }
      return 1;
   }

   public static int calculateMaxCrafts(AbstractContainerScreen<?> screen, ItemStack supercraftStack) {
      if (screen == null || supercraftStack == null) return 0;
      int craftAmount = getCraftAmountPerCraft(supercraftStack);
      if (craftAmount <= 0) craftAmount = 1;

      int maxStackSize = 64;
      for (Slot slot : screen.getMenu().slots) {
         if (!(slot.container instanceof Inventory) && slot.hasItem()) {
            ItemStack s = slot.getItem();
            String sName = s.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");
            if (!sName.contains("Supercraft") && !sName.contains("Quick Craft") && !sName.contains("Close") && !sName.contains("Back") && !sName.contains("Recipe")) {
               maxStackSize = s.getMaxStackSize();
               break;
            }
         }
      }

      int emptySlots = 0;
      for (Slot slot : screen.getMenu().slots) {
         if (slot.container instanceof Inventory) {
            if (!slot.hasItem()) {
               emptySlots++;
            }
         }
      }

      int totalItemsCanHold = emptySlots * maxStackSize;
      return totalItemsCanHold / craftAmount;
   }

   public static void appendTooltip(ItemStack stack, List<Component> lines) {
      if (!BomboConfig.get().loreAdditionsEnabled || stack == null || stack.isEmpty()) return;
      String name = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");

      // 1. Cancel Order Tooltip
      if (BomboConfig.get().copyCanceledOrderAmount && name.contains("Cancel Order")) {
         String amount = getCanceledOrderAmount(stack);
         if (amount != null) {
            lines.add(Component.literal("§eHold Ctrl + Click to copy " + amount));
         }
      }

      // 2. Supercraft Max Calculator Tooltip
      if (BomboConfig.get().supercraftMaxCalculator && name.contains("Supercraft")) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.screen instanceof AbstractContainerScreen) {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
            int maxCrafts = calculateMaxCrafts(screen, stack);
            lines.add(Component.literal("§bMax craftable: §a" + maxCrafts));
            lines.add(Component.literal("§eHold Ctrl + Click to copy " + maxCrafts));
         }
      }

      // 3. Starts In Absolute Time Tooltip
      if (BomboConfig.get().startsInAbsoluteTime) {
         ItemLore lore = stack.get(DataComponents.LORE);
         if (lore != null) {
            for (Component line : lore.lines()) {
               String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
               Matcher m = STARTS_IN_PATTERN.matcher(clean);
               if (m.find()) {
                  long days = m.group(1) != null ? Long.parseLong(m.group(1)) : 0L;
                  long hours = m.group(2) != null ? Long.parseLong(m.group(2)) : 0L;
                  long mins = m.group(3) != null ? Long.parseLong(m.group(3)) : 0L;
                  long secs = m.group(4) != null ? Long.parseLong(m.group(4)) : 0L;
                  long totalMillis = (days * 86400L + hours * 3600L + mins * 60L + secs) * 1000L;
                  if (totalMillis > 0L) {
                     long targetEpoch = System.currentTimeMillis() + totalMillis;
                     Locale loc = Locale.getDefault();
                     SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", loc);
                     String dayName = dayFmt.format(new Date(targetEpoch));
                     java.text.DateFormat dateFmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, loc);
                     String dateStr = dateFmt.format(new Date(targetEpoch));
                     java.text.DateFormat timeFmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, loc);
                     String timeStr = timeFmt.format(new Date(targetEpoch));
                     String formatted = dayName + " " + dateStr + " " + timeStr;
                     lines.add(Component.literal("§7Starts at: §e" + formatted));
                     break;
                  }
               }
            }
         }
      }
   }

   public static boolean handleCtrlClick(AbstractContainerScreen<?> screen, Slot slot) {
      if (screen == null || slot == null || !slot.hasItem()) return false;
      ItemStack stack = slot.getItem();
      String name = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");

      if (BomboConfig.get().copyCanceledOrderAmount && name.contains("Cancel Order")) {
         String amount = getCanceledOrderAmount(stack);
         if (amount != null) {
            Minecraft.getInstance().keyboardHandler.setClipboard(amount);
            Bomboaddons.sendMessage("§a[BomboAddons] Copied " + amount + " to clipboard!");
            return false; // Return false so vanilla container slot click continues!
         }
      }

      if (BomboConfig.get().supercraftMaxCalculator && name.contains("Supercraft")) {
         int maxCrafts = calculateMaxCrafts(screen, stack);
         Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(maxCrafts));
         Bomboaddons.sendMessage("§a[BomboAddons] Copied " + maxCrafts + " to clipboard!");
         return true;
      }

      return false;
   }
}
