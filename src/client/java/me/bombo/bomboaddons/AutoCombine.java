package me.bombo.bomboaddons;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public class AutoCombine {
   private static long lastAction = 0L;
   private static boolean toggled = false;
   private static boolean wasDown = false;
   private static final String LOG_FILE = "bombo_autocombine.log";

   public static void log(String msg) {
      try {
         FileWriter fw = new FileWriter("bombo_autocombine.log", true);

         try {
            PrintWriter pw = new PrintWriter(fw);

            try {
               String var10001 = String.valueOf(new Date());
               pw.println("[" + var10001 + "] " + msg);
            } catch (Throwable var7) {
               try {
                  pw.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }

               throw var7;
            }

            pw.close();
         } catch (Throwable var8) {
            try {
               fw.close();
            } catch (Throwable var5) {
               var8.addSuppressed(var5);
            }

            throw var8;
         }

         fw.close();
      } catch (Exception var9) {
      }

   }

   public static void onTick() {
      try {
         onTickUnsafe();
      } catch (Throwable t) {
         log("CRASH: " + t.toString());
      }

   }

   private static void onTickUnsafe() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         BomboConfig.Settings config = BomboConfig.get();
         if (!config.hideCheats && config.anvilAutoCombineEnabled) {
            boolean isVanillaAnvil = mc.screen instanceof AnvilScreen;
            boolean isChestAnvil = false;
            Screen var5 = mc.screen;
            if (var5 instanceof AbstractContainerScreen) {
               AbstractContainerScreen<?> screen = (AbstractContainerScreen)var5;
               String title = screen.getTitle().getString();
               if (title.toLowerCase().contains("anvil") || title.toLowerCase().contains("combine")) {
                  isChestAnvil = true;
               }
            }

            if (!isVanillaAnvil && !isChestAnvil) {
               wasDown = false;
            } else {
               String bound = config.anvilAutoCombineKey;
               if (bound != null && !bound.isEmpty()) {
                  int code = ClickLogic.getKeyCode(bound);
                  if (code != -1) {
                     boolean down = ClickLogic.isCodeDown(mc.getWindow().handle(), mc.getWindow(), code);
                     if (down && !wasDown) {
                        toggled = !toggled;
                        Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Anvil Auto-Combine: " + (toggled ? "§aENABLED" : "§cDISABLED"));
                     }

                     wasDown = down;
                  }
               }

               if (!config.anvilAutoCombineRequireKey || toggled) {
                  try {
                     ItemStack carried = mc.player.containerMenu.getCarried();
                     if (carried != null && !carried.isEmpty()) {
                        return;
                     }
                  } catch (Throwable var23) {
                     try {
                        Method m = mc.player.containerMenu.getClass().getMethod("getCursorItem");
                        ItemStack carried = (ItemStack)m.invoke(mc.player.containerMenu);
                        if (carried != null && !carried.isEmpty()) {
                           return;
                        }
                     } catch (Throwable var22) {
                     }
                  }

                  if (System.currentTimeMillis() - lastAction >= (long)config.anvilAutoCombineDelay) {
                     int resultSlot;
                     int invStart;
                     AbstractContainerMenu menu;
                     int leftSlot;
                     int rightSlot;
                     if (isVanillaAnvil) {
                        menu = ((AnvilScreen)mc.screen).getMenu();
                        leftSlot = 0;
                        rightSlot = 1;
                        resultSlot = 2;
                        invStart = 3;
                     } else {
                        menu = ((AbstractContainerScreen)mc.screen).getMenu();
                        leftSlot = 29;
                        rightSlot = 33;
                        resultSlot = 22;
                        invStart = 54;
                     }

                     if (menu.slots.size() >= invStart) {
                        ItemStack result = menu.getSlot(resultSlot).getItem();
                        if (!result.isEmpty()) {
                           boolean isCombineAnvil = result.getItem() == Items.ANVIL;
                           boolean isSign = result.getItem() == Items.OAK_SIGN;
                           boolean hasGlint = result.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != null && (Boolean)result.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
                           if (isCombineAnvil && hasGlint || isSign) {
                              clickSlot(menu.containerId, resultSlot, 0, ContainerInput.PICKUP);
                              lastAction = System.currentTimeMillis();
                              return;
                           }

                           Map<String, Integer> enchants = getEnchantments(result);

                           for(Map.Entry<String, Integer> entry : enchants.entrySet()) {
                              Integer targetTier = (Integer)config.anvilAutoCombine.get(entry.getKey());
                              if (targetTier != null) {
                                 clickSlot(menu.containerId, resultSlot, 0, ContainerInput.QUICK_MOVE);
                                 lastAction = System.currentTimeMillis();
                                 return;
                              }
                           }
                        }

                        ItemStack left = menu.getSlot(leftSlot).getItem();
                        ItemStack right = menu.getSlot(rightSlot).getItem();
                        if (left.isEmpty() && right.isEmpty()) {
                           for(int i = invStart; i < menu.slots.size(); ++i) {
                              ItemStack stack = menu.getSlot(i).getItem();
                              if (!stack.isEmpty()) {
                                 Map<String, Integer> enchants = getEnchantments(stack);
                                 if (enchants.size() == 1) {
                                    for(Map.Entry<String, Integer> entry : enchants.entrySet()) {
                                       String enchantId = (String)entry.getKey();
                                       int tier = (Integer)entry.getValue();
                                       Integer targetTier = (Integer)config.anvilAutoCombine.get(enchantId);
                                       if (targetTier != null && tier < targetTier) {
                                          int otherSlot = findMatching(menu, enchantId, tier, i, invStart);
                                          if (otherSlot != -1) {
                                             clickSlot(menu.containerId, i, 0, ContainerInput.QUICK_MOVE);
                                             lastAction = System.currentTimeMillis();
                                             return;
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        } else if (left.isEmpty() || right.isEmpty()) {
                           ItemStack present = left.isEmpty() ? right : left;
                           Map<String, Integer> enchants = getEnchantments(present);
                           if (enchants.size() > 1) {
                              return;
                           }

                           if (enchants.size() == 1) {
                              Map.Entry<String, Integer> entry = (Map.Entry)enchants.entrySet().iterator().next();
                              String enchantId = (String)entry.getKey();
                              int tier = (Integer)entry.getValue();
                              Integer targetTier = (Integer)config.anvilAutoCombine.get(enchantId);
                              if (targetTier != null && tier < targetTier) {
                                 int otherSlot = findMatching(menu, enchantId, tier, -1, invStart);
                                 if (otherSlot != -1) {
                                    clickSlot(menu.containerId, otherSlot, 0, ContainerInput.QUICK_MOVE);
                                    lastAction = System.currentTimeMillis();
                                    return;
                                 }
                              }
                           }

                           int occupiedSlot = left.isEmpty() ? rightSlot : leftSlot;
                           clickSlot(menu.containerId, occupiedSlot, 0, ContainerInput.QUICK_MOVE);
                           lastAction = System.currentTimeMillis();
                        }

                     }
                  }
               }
            }
         }
      }
   }

   private static int findMatching(AbstractContainerMenu menu, String enchantId, int tier, int skipSlot, int invStart) {
      for(int i = invStart; i < menu.slots.size(); ++i) {
         if (i != skipSlot) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
               Map<String, Integer> enchants = getEnchantments(stack);
               if (enchants.size() == 1) {
                  Integer foundTier = (Integer)enchants.get(enchantId);
                  if (foundTier != null && foundTier == tier) {
                     return i;
                  }
               }
            }
         }
      }

      return -1;
   }

   public static Map<String, Integer> getEnchantments(ItemStack stack) {
      Map<String, Integer> enchants = new HashMap();
      CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         tag.getCompound("ExtraAttributes").ifPresent((ea) -> ea.getCompound("enchantments").ifPresent((encTag) -> {
               for(String key : encTag.keySet()) {
                  enchants.put(key, (Integer)encTag.getInt(key).orElse(0));
               }

            }));
         if (enchants.isEmpty()) {
            tag.getCompound("enchantments").ifPresent((encTag) -> {
               for(String key : encTag.keySet()) {
                  enchants.put(key, (Integer)encTag.getInt(key).orElse(0));
               }

            });
         }
      }

      return enchants;
   }

   private static void clickSlot(int syncId, int slotId, int button, ContainerInput clickType) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gameMode != null && mc.player != null) {
         mc.gameMode.handleContainerInput(syncId, slotId, button, clickType, mc.player);
      }

   }
}
