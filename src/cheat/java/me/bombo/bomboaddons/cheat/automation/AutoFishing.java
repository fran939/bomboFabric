package me.bombo.bomboaddons.cheat.automation;

import me.bombo.bomboaddons.*;

import java.lang.reflect.Field;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

public class AutoFishing {
   private static State state;
   private static long nextActionTime;
   private static long lastReelTime;
   private static final Random random;
   private static int lastCalculatedDelay;
   private static boolean debuggedSlugSkip;
   private static int lastBobberId;
   private static int originalRodSlot;
   private static int remainingWeaponClicks;
   private static long lastBobberNullTime = 0L;
   public static long lastFireVeilUseTime = 0L;

   public static boolean isFishingActive(Minecraft client) {
      if (client == null || client.player == null) return false;
      return state != AutoFishing.State.IDLE || (client.player.getMainHandItem().getItem() instanceof FishingRodItem && client.player.fishing != null);
   }

   public static void onTick(Minecraft client) {
      if (!BomboConfig.get().autoFishingEnabled) {
         state = AutoFishing.State.IDLE;
         lastBobberNullTime = 0L;
         return;
      }
      if (client.player == null || client.level == null) {
         return;
      }

      long now = System.currentTimeMillis();

      if (client.options != null && client.options.keyUse != null && client.options.keyUse.isDown()) {
         if (client.player != null && isFireVeil(client.player.getMainHandItem())) {
            lastFireVeilUseTime = now;
         }
      }

      // Auto-recast watchdog: if holding rod, in IDLE, not in GUI, and no active bobber exists
      if (state == AutoFishing.State.IDLE) {
         if (client.player.getMainHandItem().getItem() instanceof FishingRodItem) {
            if (client.player.fishing == null) {
               if (client.gui.screen() == null) {
                  if (lastBobberNullTime == 0L) {
                     lastBobberNullTime = now;
                  } else if (now - lastBobberNullTime >= 1500L && now - lastReelTime >= 1000L) {
                     if (BomboConfig.get().autoFishingDebug) {
                        client.player.sendSystemMessage(Component.literal("§e[AutoFishing] No active bobber detected - casting rod..."));
                     }
                     client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                     client.player.swing(InteractionHand.MAIN_HAND);
                     lastBobberNullTime = now;
                     lastReelTime = now;
                  }
               }
               return;
            } else {
               lastBobberNullTime = 0L;
            }
         } else {
            lastBobberNullTime = 0L;
            return;
         }
      }

      if (client.player.fishing != null && client.player.fishing.getId() != lastBobberId) {
         lastBobberId = client.player.fishing.getId();
         debuggedSlugSkip = false;
      }

      switch (state.ordinal()) {
         case 0:
         default:
            if (!(client.player.getMainHandItem().getItem() instanceof FishingRodItem) || client.player.fishing == null) {
               return;
            }

            // Stuck bobber timeout watchdog: if immersed for >30 seconds with no bite, reel in and recast
            if (client.player.fishing.tickCount > 600) {
               if (BomboConfig.get().autoFishingDebug) {
                  client.player.sendSystemMessage(Component.literal("§e[AutoFishing] Bobber stuck (>30s) - reeling in & recasting..."));
               }
               client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
               lastReelTime = now;
               state = AutoFishing.State.RECASTING;
               nextActionTime = now + 400L;
               return;
            }

            if (now - lastReelTime < 2000L) {
               return;
            }

            for (Entity entity : client.level.entitiesForRendering()) {
               if (entity instanceof ArmorStand && entity.hasCustomName()) {
                  String name = entity.getCustomName().getString();
                  if (name.contains("!!!")) {
                     // Proximity check: ensure the exclamation mark ArmorStand belongs to our bobber!
                     if (client.player.fishing != null && entity.distanceToSqr(client.player.fishing) > 16.0) {
                        continue;
                     }

                     if (BomboConfig.get().autoFishingSlugMode && BomboConfig.get().autoFishingSlugDelay > 0.0F) {
                        int requiredTicks = (int)(BomboConfig.get().autoFishingSlugDelay * 20.0F);
                        if (client.player.fishing.tickCount < requiredTicks) {
                           if (BomboConfig.get().autoFishingDebug && !debuggedSlugSkip) {
                              LocalPlayer var10000 = client.player;
                              String var10001 = String.format("%.1f", (float)client.player.fishing.tickCount / 20.0F);
                              var10000.sendSystemMessage(Component.literal("§c[AutoFishing] Slug mode: Skipping bite, bobber time " + var10001 + "s < " + BomboConfig.get().autoFishingSlugDelay + "s"));
                              debuggedSlugSkip = true;
                           }
                           continue;
                        }
                     }

                     int min = BomboConfig.get().autoFishingMinDelay;
                     int max = BomboConfig.get().autoFishingMaxDelay;
                     if (max < min) {
                        max = min;
                     }

                     lastCalculatedDelay = min + (max > min ? random.nextInt(max - min + 1) : 0);
                     if (BomboConfig.get().autoFishingDebug) {
                        client.player.sendSystemMessage(Component.literal("§c[AutoFishing] Detected !!! §7(" + lastCalculatedDelay + "ms delay)"));
                     }

                     nextActionTime = now + (long)lastCalculatedDelay;
                     state = AutoFishing.State.REELING;
                     lastReelTime = now;
                     break;
                  }
               }
            }
            return;
            case 1:
               if (now >= nextActionTime) {
                  client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                  if (BomboConfig.get().autoFishingDebug) {
                     client.player.sendSystemMessage(Component.literal("§a[AutoFishing] Reeled in!"));
                  }

                  if (BomboConfig.get().autoFishingSwapEnabled) {
                     state = AutoFishing.State.SWAPPING_TO_WEAPON;
                     nextActionTime = now + 150L + (long)random.nextInt(100);
                  } else {
                     state = AutoFishing.State.RECASTING;
                     int min = BomboConfig.get().autoFishingMinDelay;
                     int max = BomboConfig.get().autoFishingMaxDelay;
                     if (max < min) {
                        max = min;
                     }

                     int recastDelay = min + (max > min ? random.nextInt(max - min + 1) : 0);
                     nextActionTime = now + (long)recastDelay;
                  }
               }

               return;
            case 2:
               if (now >= nextActionTime) {
                  int weaponSlot = findWeaponSlot(client);
                  if (weaponSlot != -1) {
                     ItemStack weaponStack = client.player.getInventory().getItem(weaponSlot);
                     if (isFireVeil(weaponStack)) {
                        long elapsed = now - lastFireVeilUseTime;
                        if (elapsed < 5000L) {
                           if (BomboConfig.get().autoFishingDebug) {
                              client.player.sendSystemMessage(Component.literal("§e[AutoFishing] Fire Veil active (" + (5000L - elapsed) + "ms remaining), skipping swap."));
                           }
                           state = AutoFishing.State.RECASTING;
                           int min = BomboConfig.get().autoFishingMinDelay;
                           int max = BomboConfig.get().autoFishingMaxDelay;
                           if (max < min) max = min;
                           int recastDelay = min + (max > min ? random.nextInt(max - min + 1) : 0);
                           nextActionTime = now + (long)recastDelay;
                           return;
                        }
                     }

                     originalRodSlot = getSelectedSlot(client);
                     setSelectedSlot(client, weaponSlot);
                     remainingWeaponClicks = Math.max(1, BomboConfig.get().autoFishingClickCount);
                     state = AutoFishing.State.ATTACKING_WEAPON;
                     nextActionTime = now + 100L;
                     if (BomboConfig.get().autoFishingDebug) {
                        client.player.sendSystemMessage(Component.literal("§e[AutoFishing] Swapped to weapon slot " + (weaponSlot + 1)));
                     }
                  } else {
                     if (BomboConfig.get().autoFishingDebug) {
                        client.player.sendSystemMessage(Component.literal("§c[AutoFishing] Weapon not found in hotbar! Skipping swap."));
                     }

                     state = AutoFishing.State.RECASTING;
                     nextActionTime = now + 200L;
                  }
               }

               return;
            case 3:
               if (now >= nextActionTime) {
                  if (remainingWeaponClicks > 0) {
                     --remainingWeaponClicks;
                     ItemStack heldWeapon = client.player.getMainHandItem();
                     if (isFireVeil(heldWeapon)) {
                        lastFireVeilUseTime = now;
                        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                        client.player.swing(InteractionHand.MAIN_HAND);
                     } else {
                        int clickType = BomboConfig.get().autoFishingClickType;
                        if (clickType == 1) { // Left Click
                           if (client.crosshairPickEntity != null) {
                              client.gameMode.attack(client.player, client.crosshairPickEntity);
                           }
                           client.player.swing(InteractionHand.MAIN_HAND);
                        } else { // Right Click
                           client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                        }
                     }

                     int min = BomboConfig.get().autoFishingClickDelayMin;
                     int max = BomboConfig.get().autoFishingClickDelayMax;
                     if (max < min) {
                        max = min;
                     }

                     int clickDelay = min + (max > min ? random.nextInt(max - min + 1) : 0);
                     nextActionTime = now + (long)clickDelay;
                     if (BomboConfig.get().autoFishingDebug) {
                        client.player.sendSystemMessage(Component.literal("§a[AutoFishing] Weapon click! Remaining: " + remainingWeaponClicks + " (" + clickDelay + "ms delay)"));
                     }
                  } else {
                     state = AutoFishing.State.SWAPPING_TO_ROD;
                     nextActionTime = now + 100L;
                  }
               }

               return;
            case 4:
               if (now >= nextActionTime) {
                  if (originalRodSlot >= 0 && originalRodSlot < 9) {
                     setSelectedSlot(client, originalRodSlot);
                     if (BomboConfig.get().autoFishingDebug) {
                        client.player.sendSystemMessage(Component.literal("§e[AutoFishing] Swapped back to rod slot " + (originalRodSlot + 1)));
                     }
                  }

                  state = AutoFishing.State.RECASTING;
                  int min = BomboConfig.get().autoFishingMinDelay;
                  int max = BomboConfig.get().autoFishingMaxDelay;
                  if (max < min) {
                     max = min;
                  }

                  int recastDelay = min + (max > min ? random.nextInt(max - min + 1) : 0);
                  nextActionTime = now + (long)recastDelay;
               }

               return;
            case 5:
               if (now >= nextActionTime) {
                  if (client.player.getMainHandItem().getItem() instanceof FishingRodItem) {
                     client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                     client.player.swing(InteractionHand.MAIN_HAND);
                     if (BomboConfig.get().autoFishingDebug) {
                        client.player.sendSystemMessage(Component.literal("§a[AutoFishing] Recast rod!"));
                     }
                  }

                  state = AutoFishing.State.IDLE;
               }

         }
   }

   public static boolean isFireVeil(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return false;
      String sbId = SkyblockUtils.getSkyblockId(stack);
      if (sbId != null && !sbId.isEmpty()) {
         if (sbId.toUpperCase(java.util.Locale.ROOT).contains("FIRE_VEIL")) return true;
      }
      String hover = stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
      if (hover.contains("fire veil") || hover.contains("fireveil") || hover.contains("veil")) return true;
      String cfg = BomboConfig.get().autoFishingWeaponName.toLowerCase(java.util.Locale.ROOT).trim();
      if (!cfg.isEmpty() && (cfg.contains("veil") || cfg.contains("fire veil"))) {
         if (hover.contains(cfg)) return true;
      }
      return false;
   }

   private static int findWeaponSlot(Minecraft client) {
      if (client.player == null) {
         return -1;
      } else {
         String query = BomboConfig.get().autoFishingWeaponName.toLowerCase().trim();
         if (query.isEmpty()) {
            return -1;
         } else {
            try {
               int slotNum = Integer.parseInt(query);
               if (slotNum >= 1 && slotNum <= 9) {
                  return slotNum - 1;
               }
            } catch (NumberFormatException var6) {
            }

            for(int i = 0; i < 9; ++i) {
               ItemStack stack = client.player.getInventory().getItem(i);
               if (!stack.isEmpty()) {
                  String hoverName = stack.getHoverName().getString().toLowerCase();
                  String itemId = stack.getItem().toString().toLowerCase();
                  if (hoverName.contains(query) || itemId.contains(query)) {
                     return i;
                  }
               }
            }

            return -1;
         }
      }
   }

   public static void stopAllAutomation(String reason) {
      Minecraft client = Minecraft.getInstance();
      if (client != null) {
         client.execute(() -> {
            GardenMovement.onManualReset();
            if (client.options != null) {
               client.options.keyUp.setDown(false);
               client.options.keyDown.setDown(false);
               client.options.keyLeft.setDown(false);
               client.options.keyRight.setDown(false);
               client.options.keyAttack.setDown(false);
               client.options.keyUse.setDown(false);
            }
            if (client.player != null && originalRodSlot >= 0 && originalRodSlot < 9) {
               setSelectedSlot(client, originalRodSlot);
            }

            state = AutoFishing.State.IDLE;
            originalRodSlot = -1;
            remainingWeaponClicks = 0;
            nextActionTime = 0L;
            if (client.player != null) {
               if (reason != null && !reason.trim().isEmpty() && !reason.equalsIgnoreCase("manual")) {
                  client.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §c§lKILL SWITCH ACTIVATED! §7Matched trigger: §e\"" + reason + "\"§7. Aborted current sequence & stopped Garden Movement. Recast manually to resume."));
               } else {
                  client.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §c§lKILL SWITCH ACTIVATED! §7Aborted current sequence & stopped Garden Movement. Recast manually to resume."));
               }
            }

         });
      }
   }

   public static void onChatMessage(String text) {
      if (text != null && !text.trim().isEmpty()) {
         String triggersStr = BomboConfig.get().autoFishingStopChatMessage;
         if (triggersStr != null && !triggersStr.trim().isEmpty()) {
            String[] triggers = triggersStr.split(",");
            String lowerText = text.toLowerCase();

            for(String trigger : triggers) {
               String cleanTrigger = trigger.trim();
               if (!cleanTrigger.isEmpty() && lowerText.contains(cleanTrigger.toLowerCase())) {
                  stopAllAutomation(cleanTrigger);
                  break;
               }
            }

         }
      }
   }

   public static void renderTimer(GuiGraphicsExtractor graphics) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s.showBobberTime && client.player.fishing != null) {
            int tickCount = client.player.fishing.tickCount;
            float seconds = (float)tickCount / 20.0F;
            String text = String.format("§bBobber Time: %.1fs", seconds);
            drawBobberHud(graphics, client, text, s.bobberTimeHudX, s.bobberTimeHudY, s.bobberTimeHudScale);
         }
      }
   }

   public static void drawBobberHud(GuiGraphicsExtractor graphics, Minecraft client, String text, int customX, int customY, float scale) {
      int screenWidth = client.getWindow().getGuiScaledWidth();
      int screenHeight = client.getWindow().getGuiScaledHeight();
      int textW = client.font.width(text);
      int x = customX >= 0 ? customX : (screenWidth / 2 - textW / 2);
      int y = customY >= 0 ? customY : (screenHeight / 2 + 15);
      if (scale != 1.0F && scale > 0.0F) {
         graphics.pose().pushMatrix();
         graphics.pose().translate((float)x, (float)y);
         graphics.pose().scale(scale, scale);
         graphics.text(client.font, text, 0, 0, -1, true);
         graphics.pose().popMatrix();
      } else {
         graphics.text(client.font, text, x, y, -1, true);
      }
   }

   public static int getSelectedSlot(Minecraft client) {
      if (client == null || client.player == null) {
         return 0;
      }
      try {
         return client.player.getInventory().getSelectedSlot();
      } catch (Throwable t) {
         return 0;
      }
   }

   public static void setSelectedSlot(Minecraft client, int slot) {
      if (client != null && client.player != null && slot >= 0 && slot < 9) {
         try {
            client.player.getInventory().setSelectedSlot(slot);
         } catch (Throwable ignored) {}
      }
   }

   static {
      state = AutoFishing.State.IDLE;
      nextActionTime = 0L;
      lastReelTime = 0L;
      random = new Random();
      lastCalculatedDelay = 0;
      debuggedSlugSkip = false;
      lastBobberId = -1;
      originalRodSlot = -1;
      remainingWeaponClicks = 0;
   }

   public static enum State {
      IDLE,
      REELING,
      SWAPPING_TO_WEAPON,
      ATTACKING_WEAPON,
      SWAPPING_TO_ROD,
      RECASTING;

      // $FF: synthetic method
      private static State[] $values() {
         return new State[]{IDLE, REELING, SWAPPING_TO_WEAPON, ATTACKING_WEAPON, SWAPPING_TO_ROD, RECASTING};
      }
   }
}
