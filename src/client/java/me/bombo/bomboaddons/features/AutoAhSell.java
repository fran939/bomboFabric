package me.bombo.bomboaddons.features;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class AutoAhSell {
   private static boolean running = false;
   private static long targetPrice = 0L;
   private static String targetPriceFormatted = "";
   private static String targetSkyblockId = null;
   private static String targetDisplayName = null;
   private static boolean sessionWarnBypassed = false;
   private static boolean awaitingConfirmBypass = false;

   private static Step currentStep = Step.IDLE;
   private static long lastActionTime = 0L;
   private static int actionAttempts = 0;

   private enum Step {
      IDLE,
      OPEN_AH,
      WAIT_AH_OPEN,
      CLICK_CREATE_AUCTION,
      WAIT_MANAGE_AUCTIONS_GUI,
      WAIT_AUCTION_CREATE_GUI,
      CLICK_ITEM_SLOT,
      WAIT_AFTER_ITEM_PUT,
      CLICK_DURATION_CLOCK_1,
      WAIT_DURATION_GUI,
      CLICK_DURATION_CUSTOM_CLOCK_2,
      WAIT_SIGN_DURATION,
      SUBMIT_SIGN_DURATION,
      WAIT_AFTER_DURATION_SIGN,
      CLICK_ITEM_PRICE_OR_BIN,
      WAIT_AFTER_PRICE_OR_BIN_CLICK,
      WAIT_SIGN_PRICE,
      SUBMIT_SIGN_PRICE,
      WAIT_AFTER_PRICE_SIGN,
      CLICK_CREATE_BIN_AUCTION,
      WAIT_CONFIRM_GUI,
      CHECK_SAFETY_AND_CONFIRM,
      WAIT_AFTER_CONFIRM,
      CHECK_NEXT_ITEM
   }

   public static void start(String priceStr) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;

      long parsed = parsePrice(priceStr);
      if (parsed <= 0L) {
         mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §cInvalid price: §e" + priceStr + "§c. Use formats like §e4m§c, §e500k§c, §e1.5m§c, or numbers."));
         return;
      }

      // Check hovered item if an inventory/container is open
      ItemStack targetStack = null;
      if (mc.screen instanceof AbstractContainerScreen) {
         AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
         Slot hovered = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();
         if (hovered != null && hovered.hasItem()) {
            targetStack = hovered.getItem();
         }
      }

      // If no hovered item, check main hand item
      if (targetStack == null || targetStack.isEmpty()) {
         targetStack = mc.player.getMainHandItem();
      }

      if (targetStack == null || targetStack.isEmpty()) {
         mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §cHover over the item you want to sell in your inventory or hold it in your hand!"));
         return;
      }

      targetSkyblockId = SkyblockUtils.getSkyblockId(targetStack);
      if (targetSkyblockId == null || targetSkyblockId.isEmpty()) {
         targetSkyblockId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetStack.getItem()).getPath().toUpperCase();
      }
      targetDisplayName = targetStack.getHoverName().getString();

      targetPrice = parsed;
      targetPriceFormatted = LowestBinManager.formatPrice(parsed);
      running = true;
      currentStep = Step.OPEN_AH;
      lastActionTime = 0L;
      actionAttempts = 0;

      mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §aStarting Auto Sell for §f" + targetDisplayName + " §7(ID: §e" + targetSkyblockId + "§7) at §6" + targetPriceFormatted + " coins§a..."));
   }

   public static void onConfirmWarning() {
      sessionWarnBypassed = true;
      awaitingConfirmBypass = false;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §aPrice warning bypassed for this session. Continuing sale..."));
      }
      if (running && currentStep == Step.CHECK_SAFETY_AND_CONFIRM) {
         lastActionTime = 0L;
      }
   }

   public static void stop() {
      running = false;
      currentStep = Step.IDLE;
      awaitingConfirmBypass = false;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §cAuto Sell cancelled."));
      }
   }

   public static boolean isRunning() {
      return running;
   }

   public static boolean isAwaitingBypass() {
      return awaitingConfirmBypass;
   }

   public static long parsePrice(String input) {
      if (input == null) return 0L;
      String clean = input.trim().toLowerCase().replace(",", "").replace("_", "");
      try {
         if (clean.endsWith("b")) {
            double val = Double.parseDouble(clean.substring(0, clean.length() - 1));
            return (long) (val * 1_000_000_000L);
         } else if (clean.endsWith("m")) {
            double val = Double.parseDouble(clean.substring(0, clean.length() - 1));
            return (long) (val * 1_000_000L);
         } else if (clean.endsWith("k")) {
            double val = Double.parseDouble(clean.substring(0, clean.length() - 1));
            return (long) (val * 1_000L);
         }
         return Long.parseLong(clean);
      } catch (Exception e) {
         return 0L;
      }
   }

   public static void onClientTick() {
      if (!running) return;

      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         running = false;
         return;
      }

      int delay = Math.max(80, BomboConfig.get().autoAhSellDelayMs);
      long now = System.currentTimeMillis();
      if (now - lastActionTime < delay) return;

      Screen screen = mc.screen;

      switch (currentStep) {
         case OPEN_AH -> {
            mc.player.connection.sendCommand("ah");
            currentStep = Step.WAIT_AH_OPEN;
            lastActionTime = now;
            actionAttempts = 0;
         }
         case WAIT_AH_OPEN -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               String title = containerScreen.getTitle().getString();
               if (title.contains("Co-op Auction House") || title.contains("Auction House")) {
                  currentStep = Step.CLICK_CREATE_AUCTION;
                  lastActionTime = now;
                  return;
               }
            }
            if (++actionAttempts > 40) { // 40 * delay timeout
               mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §cTimed out waiting for Auction House to open."));
               stop();
            }
         }
         case CLICK_CREATE_AUCTION -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               // First check for Create Auction
               int createSlot = findSlotByItemName(containerScreen, "Create Auction");
               if (createSlot != -1) {
                  clickSlot(containerScreen, createSlot);
                  currentStep = Step.WAIT_AUCTION_CREATE_GUI;
                  lastActionTime = now;
                  actionAttempts = 0;
                  return;
               }

               // If user already has active auctions, it shows "Manage Auctions" (golden horse armor)
               int manageSlot = findSlotByItemName(containerScreen, "Manage Auctions");
               if (manageSlot == -1) {
                  manageSlot = findSlotByItemName(containerScreen, "Manage");
               }
               if (manageSlot != -1) {
                  clickSlot(containerScreen, manageSlot);
                  currentStep = Step.WAIT_MANAGE_AUCTIONS_GUI;
                  lastActionTime = now;
                  actionAttempts = 0;
                  return;
               }
            }
         }
         case WAIT_MANAGE_AUCTIONS_GUI -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               String title = containerScreen.getTitle().getString();
               if (title.contains("Manage Auctions") || title.contains("Your Auctions") || title.contains("Auction House")) {
                  // Inside Manage Auctions, click Create Auction (golden horse armor)
                  int createSlot = findSlotByItemName(containerScreen, "Create Auction");
                  if (createSlot == -1) {
                     createSlot = findSlotByItemName(containerScreen, "Create");
                  }
                  if (createSlot != -1) {
                     clickSlot(containerScreen, createSlot);
                     currentStep = Step.WAIT_AUCTION_CREATE_GUI;
                     lastActionTime = now;
                     actionAttempts = 0;
                     return;
                  }
               }
            }
            if (++actionAttempts > 40) {
               stop();
            }
         }
         case WAIT_AUCTION_CREATE_GUI -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               String title = containerScreen.getTitle().getString();
               if (title.contains("Create Auction") || title.contains("Create BIN Auction")) {
                  currentStep = Step.CLICK_ITEM_SLOT;
                  lastActionTime = now;
                  return;
               }
            }
            if (++actionAttempts > 40) {
               stop();
            }
         }
         case CLICK_ITEM_SLOT -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               // Find item in player inventory portion of container matching targetSkyblockId
               int matchingPlayerSlot = findMatchingItemInInventory(containerScreen, targetSkyblockId);
               if (matchingPlayerSlot == -1) {
                  mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §aNo more matching §f" + targetDisplayName + " §afound in inventory. Finished!"));
                  running = false;
                  currentStep = Step.IDLE;
                  return;
               }
               // Click item in player inventory into auction slot (or click item slot directly)
               clickSlot(containerScreen, matchingPlayerSlot);
               currentStep = Step.WAIT_AFTER_ITEM_PUT;
               lastActionTime = now;
            }
         }
         case WAIT_AFTER_ITEM_PUT -> {
            currentStep = Step.CLICK_DURATION_CLOCK_1;
            lastActionTime = now;
         }
         case CLICK_DURATION_CLOCK_1 -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               int clockSlot = findSlotByItemName(containerScreen, "Duration");
               if (clockSlot != -1) {
                  clickSlot(containerScreen, clockSlot);
                  currentStep = Step.WAIT_DURATION_GUI;
                  lastActionTime = now;
                  actionAttempts = 0;
               }
            }
         }
         case WAIT_DURATION_GUI -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               String title = containerScreen.getTitle().getString();
               if (title.contains("Auction Duration") || title.contains("Duration")) {
                  currentStep = Step.CLICK_DURATION_CUSTOM_CLOCK_2;
                  lastActionTime = now;
                  return;
               }
            }
            if (++actionAttempts > 40) {
               stop();
            }
         }
         case CLICK_DURATION_CUSTOM_CLOCK_2 -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               int customSlot = findSlotByItemName(containerScreen, "Custom Duration");
               if (customSlot == -1) {
                  customSlot = findSlotByItemName(containerScreen, "Custom");
               }
               if (customSlot != -1) {
                  clickSlot(containerScreen, customSlot);
                  currentStep = Step.WAIT_SIGN_DURATION;
                  lastActionTime = now;
                  actionAttempts = 0;
               }
            }
         }
         case WAIT_SIGN_DURATION -> {
            if (screen instanceof AbstractSignEditScreen) {
               currentStep = Step.SUBMIT_SIGN_DURATION;
               lastActionTime = now;
               return;
            }
            if (++actionAttempts > 40) {
               stop();
            }
         }
         case SUBMIT_SIGN_DURATION -> {
            if (screen instanceof AbstractSignEditScreen signScreen) {
               // Fill "9999" (minutes / hours) for max duration and close
               setSignTextAndClose(signScreen, "9999");
               currentStep = Step.WAIT_AFTER_DURATION_SIGN;
               lastActionTime = now;
            }
         }
         case WAIT_AFTER_DURATION_SIGN -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               String title = containerScreen.getTitle().getString();
               if (title.contains("Create Auction") || title.contains("Create BIN Auction")) {
                  currentStep = Step.CLICK_ITEM_PRICE_OR_BIN;
                  lastActionTime = now;
               }
            }
         }
         case CLICK_ITEM_PRICE_OR_BIN -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               int priceSlot = findSlotByItemName(containerScreen, "Item price");
               if (priceSlot == -1) {
                  priceSlot = findSlotByItemName(containerScreen, "Starting bid");
               }

               // If BIN switch exists and we are not in BIN mode
               int switchBinSlot = findSlotByItemName(containerScreen, "Switch to BIN");
               if (switchBinSlot != -1) {
                  clickSlot(containerScreen, switchBinSlot);
                  currentStep = Step.WAIT_AFTER_PRICE_OR_BIN_CLICK;
                  lastActionTime = now;
                  return;
               }

               if (priceSlot != -1) {
                  clickSlot(containerScreen, priceSlot);
                  currentStep = Step.WAIT_SIGN_PRICE;
                  lastActionTime = now;
                  actionAttempts = 0;
               }
            }
         }
         case WAIT_AFTER_PRICE_OR_BIN_CLICK -> {
            currentStep = Step.CLICK_ITEM_PRICE_OR_BIN;
            lastActionTime = now;
         }
         case WAIT_SIGN_PRICE -> {
            if (screen instanceof AbstractSignEditScreen) {
               currentStep = Step.SUBMIT_SIGN_PRICE;
               lastActionTime = now;
               return;
            }
            if (++actionAttempts > 40) {
               stop();
            }
         }
         case SUBMIT_SIGN_PRICE -> {
            if (screen instanceof AbstractSignEditScreen signScreen) {
               setSignTextAndClose(signScreen, String.valueOf(targetPrice));
               currentStep = Step.WAIT_AFTER_PRICE_SIGN;
               lastActionTime = now;
            }
         }
         case WAIT_AFTER_PRICE_SIGN -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               String title = containerScreen.getTitle().getString();
               if (title.contains("Create Auction") || title.contains("Create BIN Auction")) {
                  currentStep = Step.CLICK_CREATE_BIN_AUCTION;
                  lastActionTime = now;
               }
            }
         }
         case CLICK_CREATE_BIN_AUCTION -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               int createBinSlot = findSlotByItemName(containerScreen, "Create BIN Auction");
               if (createBinSlot == -1) {
                  createBinSlot = findSlotByItemName(containerScreen, "Create Auction");
               }
               if (createBinSlot != -1) {
                  clickSlot(containerScreen, createBinSlot);
                  currentStep = Step.WAIT_CONFIRM_GUI;
                  lastActionTime = now;
                  actionAttempts = 0;
               }
            }
         }
         case WAIT_CONFIRM_GUI -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               String title = containerScreen.getTitle().getString();
               if (title.contains("Confirm BIN Auction") || title.contains("Confirm Auction")) {
                  currentStep = Step.CHECK_SAFETY_AND_CONFIRM;
                  lastActionTime = now;
                  return;
               }
            }
            if (++actionAttempts > 40) {
               stop();
            }
         }
         case CHECK_SAFETY_AND_CONFIRM -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               long marketPrice = LowestBinManager.getCachedPrice(targetSkyblockId);

               // Price safeguard check: If price is much lower (e.g. < 40% of market value) and not bypassed
               if (!sessionWarnBypassed && marketPrice > 0L && targetPrice < (marketPrice * 40L / 100L)) {
                  if (!awaitingConfirmBypass) {
                     awaitingConfirmBypass = true;
                     mc.gui.setTitle(Component.literal("§c§lPRICE TOO LOW!"));
                     mc.gui.setSubtitle(Component.literal("§eSelling for " + targetPriceFormatted + " §7(Market: §a" + LowestBinManager.formatPrice(marketPrice) + "§7)"));
                     mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §c§lWARNING: §eYour price of §6" + targetPriceFormatted + " §eis significantly lower than the estimated market price (§a" + LowestBinManager.formatPrice(marketPrice) + "§e)!"));
                     mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §7Type §b/b sell bypass §7or manually click Confirm to proceed anyway and disable this warning for this session."));
                  }
                  return;
               }

               // Click Confirm
               int confirmSlot = findSlotByItemName(containerScreen, "Confirm");
               if (confirmSlot != -1) {
                  clickSlot(containerScreen, confirmSlot);
                  currentStep = Step.WAIT_AFTER_CONFIRM;
                  lastActionTime = now;
                  actionAttempts = 0;
               }
            }
         }
         case WAIT_AFTER_CONFIRM -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               int backSlot = findSlotByItemName(containerScreen, "Go Back");
               if (backSlot == -1) {
                  backSlot = findSlotByItemName(containerScreen, "Back");
               }
               if (backSlot == -1) {
                  backSlot = findSlotByItemName(containerScreen, "arrow");
               }
               if (backSlot != -1) {
                  clickSlot(containerScreen, backSlot);
                  currentStep = Step.CHECK_NEXT_ITEM;
                  lastActionTime = now;
                  actionAttempts = 0;
                  return;
               }
            } else if (screen == null) {
               // If screen closed, reopen /ah
               currentStep = Step.OPEN_AH;
               lastActionTime = now;
               return;
            }
            if (++actionAttempts > 25) {
               // Fallback: reopen /ah directly
               mc.player.connection.sendCommand("ah");
               currentStep = Step.WAIT_AH_OPEN;
               lastActionTime = now;
               actionAttempts = 0;
            }
         }
         case CHECK_NEXT_ITEM -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
               int remaining = findMatchingItemInInventory(containerScreen, targetSkyblockId);
               if (remaining != -1) {
                  // Loop sale: click Create Auction
                  int createSlot = findSlotByItemName(containerScreen, "Create Auction");
                  if (createSlot != -1) {
                     clickSlot(containerScreen, createSlot);
                     currentStep = Step.WAIT_AUCTION_CREATE_GUI;
                     lastActionTime = now;
                     actionAttempts = 0;
                  } else {
                     currentStep = Step.OPEN_AH;
                     lastActionTime = now;
                  }
               } else {
                  mc.player.sendSystemMessage(Component.literal("§8[§6AutoSell§8] §aAll matching items sold successfully!"));
                  running = false;
                  currentStep = Step.IDLE;
               }
            } else {
               mc.player.connection.sendCommand("ah");
               currentStep = Step.WAIT_AH_OPEN;
               lastActionTime = now;
            }
         }
      }
   }

   private static void clickSlot(AbstractContainerScreen<?> screen, int slotId) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gameMode != null && mc.player != null) {
         mc.gameMode.handleContainerInput(screen.getMenu().containerId, slotId, 0, ContainerInput.PICKUP, mc.player);
      }
   }

   private static int findSlotByItemName(AbstractContainerScreen<?> screen, String keyword) {
      String lower = keyword.toLowerCase();
      for (Slot slot : screen.getMenu().slots) {
         if (slot.hasItem()) {
            String name = slot.getItem().getHoverName().getString().toLowerCase();
            if (name.contains(lower)) {
               return slot.index;
            }
         }
      }
      return -1;
   }

   private static int findMatchingItemInInventory(AbstractContainerScreen<?> screen, String expectedSbId) {
      // In chest container menus, slots 0..X are the chest, and the remaining slots are the player's 36 inventory slots
      int totalSlots = screen.getMenu().slots.size();
      int playerInvStart = totalSlots - 36;
      for (int i = playerInvStart; i < totalSlots; i++) {
         Slot slot = screen.getMenu().slots.get(i);
         if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            String sbId = SkyblockUtils.getSkyblockId(stack);
            if (sbId == null || sbId.isEmpty()) {
               sbId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase();
            }
            if (expectedSbId.equalsIgnoreCase(sbId)) {
               return slot.index;
            }
         }
      }
      return -1;
   }

   private static void setSignTextAndClose(AbstractSignEditScreen signScreen, String text) {
      try {
         for (java.lang.reflect.Field f : AbstractSignEditScreen.class.getDeclaredFields()) {
            if (f.getType().isArray() && f.getType().getComponentType() == String.class) {
               f.setAccessible(true);
               String[] msgs = (String[]) f.get(signScreen);
               if (msgs != null && msgs.length > 0) {
                  msgs[0] = text;
                  for (int i = 1; i < msgs.length; i++) {
                     msgs[i] = "";
                  }
               }
               break;
            }
         }
         signScreen.onClose();
      } catch (Throwable t) {
         Minecraft.getInstance().setScreen(null);
      }
   }
}
