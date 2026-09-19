package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

public class GardenMovement {
   private static boolean forward = false;
   private static boolean backward = false;
   private static boolean left = false;
   private static boolean right = false;
   private static boolean breaking = false;
   private static boolean using = false;
   private static boolean wasGoingForward = false;
   private static boolean wasGoingBackward = false;
   private static boolean wasGoingLeft = false;
   private static boolean wasGoingRight = false;
   private static boolean warped = false;
   private static long warningStartTime = 0L;
   private static String correctArrow = "";

   private static long lastBlockBrokenTime = System.currentTimeMillis();
   private static long attackStartedTime = 0L;
   private static long lastCropWarningTime = 0L;

   public static void notifyBlockBroken() {
      lastBlockBrokenTime = System.currentTimeMillis();
   }

   public static boolean isActive() {
      return forward || backward || left || right || breaking || using;
   }

   public static boolean isAllowedScreen(net.minecraft.client.gui.screens.Screen screen) {
      if (screen == null) return true;
      if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) return true;
      if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
         return false; // Server-side container (chest, menu)
      }
      return true; // Client GUI (Chat, Bombo config, Mod config, Pause, etc.)
   }

   public static void onTick(Minecraft mc) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement) {
         if (isAllowedScreen(mc.gui.screen())) {
            if (forward) {
               mc.options.keyUp.setDown(true);
            }

            if (backward) {
               mc.options.keyDown.setDown(true);
            }

            if (left) {
               mc.options.keyLeft.setDown(true);
            }

            if (right) {
               mc.options.keyRight.setDown(true);
            }

            if (breaking) {
               mc.options.keyAttack.setDown(true);
               if (mc.gameMode != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                  net.minecraft.core.BlockPos pos = bhr.getBlockPos();
                  net.minecraft.core.Direction dir = bhr.getDirection();
                  if (mc.level != null && !mc.level.getBlockState(pos).isAir()) {
                     mc.gameMode.continueDestroyBlock(pos, dir);
                     if (mc.player != null) {
                        mc.player.swing(InteractionHand.MAIN_HAND);
                     }
                  }
               }
            }

            if (using) {
               mc.options.keyUse.setDown(true);
            }
         }
      }

      if (s.cropBreakWarning && SkyblockUtils.isInGarden() && mc.player != null && mc.level != null) {
         boolean isAttacking = breaking || mc.options.keyAttack.isDown();
         if (isAttacking) {
            if (attackStartedTime == 0L) {
               attackStartedTime = System.currentTimeMillis();
            }
            long now = System.currentTimeMillis();
            if (now - attackStartedTime > 1500L && now - lastBlockBrokenTime > 1500L) {
               net.minecraft.world.phys.HitResult hit = mc.hitResult;
               if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                  net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(bhr.getBlockPos());
                  if (!state.isAir()) {
                     if (now - lastCropWarningTime > 2500L) {
                        lastCropWarningTime = now;
                        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 1.0f));
                        mc.gui.hud.setTitle(Component.literal("§c§lNot Breaking Crops!"));
                        mc.player.sendSystemMessage(Component.literal("§8[§cGarden Alert§8] §cWarning: Attempting to break crops but nothing is breaking!"));
                     }
                  }
               }
            }
         } else {
            attackStartedTime = 0L;
            lastBlockBrokenTime = System.currentTimeMillis();
         }
      }

      if (!s.gardenMovement) {
         clearWasGoing();
         if (forward || backward || left || right || breaking) {
            if (forward) {
               mc.options.keyUp.setDown(false);
            }

            if (backward) {
               mc.options.keyDown.setDown(false);
            }

            if (left) {
               mc.options.keyLeft.setDown(false);
            }

            if (right) {
               mc.options.keyRight.setDown(false);
            }

            if (breaking) {
               mc.options.keyAttack.setDown(false);
            }

            if (using) {
               mc.options.keyUse.setDown(false);
            }

            reset();
         }

      }
   }

   public static void handleKey(int keyCode) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement && SkyblockUtils.isInGarden()) {
         Minecraft mc = Minecraft.getInstance();
         // Allow toggling on no-screen or allowed GUI (player inventory, chat, etc.)
         // but NOT on server-side containers (chest, menus) — isAllowedScreen handles that.
         if (isAllowedScreen(mc.gui.screen())) {
            int fKey = ClickLogic.getKeyCode(s.gardenForwardKey);
            int bKey = ClickLogic.getKeyCode(s.gardenBackwardKey);
            int lKey = ClickLogic.getKeyCode(s.gardenLeftKey);
            int rKey = ClickLogic.getKeyCode(s.gardenRightKey);
            int brKey = ClickLogic.getKeyCode(s.gardenBreakKey);
            int uKey = ClickLogic.getKeyCode(s.gardenUseKey);
            if (keyCode == fKey && fKey != -1) {
               toggleForward();
            } else if (keyCode == bKey && bKey != -1) {
               toggleBackward();
            } else if (keyCode == lKey && lKey != -1) {
               toggleLeft();
            } else if (keyCode == rKey && rKey != -1) {
               toggleRight();
            } else if (keyCode == brKey && brKey != -1) {
               toggleBreak();
            } else if (keyCode == uKey && uKey != -1) {
               toggleUse();
            }
         }
      }
   }

   public static void toggleForward() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement) {
         Minecraft mc = Minecraft.getInstance();
         forward = !forward;
         mc.options.keyUp.setDown(forward);
         if (forward) {
            if (s.gardenDirectionHelper && warped) {
               if (s.gardenSugarCane) {
                  if (!wasGoingBackward && !wasGoingRight) {
                     warped = false;
                  } else {
                     warningStartTime = System.currentTimeMillis();
                     correctArrow = "→";
                     warped = false;
                  }
               } else if (!wasGoingForward) {
                  warningStartTime = System.currentTimeMillis();
                  if (wasGoingBackward) {
                     correctArrow = "↓";
                  } else if (wasGoingLeft) {
                     correctArrow = "←";
                  } else if (wasGoingRight) {
                     correctArrow = "→";
                  } else {
                     correctArrow = "↓";
                  }

                  warped = false;
               } else {
                  warped = false;
               }
            }

            if (s.gardenSugarCane) {
               right = false;
               mc.options.keyRight.setDown(false);
            } else {
               backward = false;
               mc.options.keyDown.setDown(false);
            }
         }

         sendToggleMsg("Forward", forward);
      }
   }

   public static void toggleBackward() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement) {
         Minecraft mc = Minecraft.getInstance();
         backward = !backward;
         mc.options.keyDown.setDown(backward);
         if (backward) {
            if (s.gardenDirectionHelper && warped) {
               if (s.gardenSugarCane) {
                  if (!wasGoingForward && !wasGoingLeft) {
                     warped = false;
                  } else {
                     warningStartTime = System.currentTimeMillis();
                     correctArrow = "←";
                     warped = false;
                  }
               } else if (!wasGoingBackward) {
                  warningStartTime = System.currentTimeMillis();
                  if (wasGoingForward) {
                     correctArrow = "↑";
                  } else if (wasGoingLeft) {
                     correctArrow = "←";
                  } else if (wasGoingRight) {
                     correctArrow = "→";
                  } else {
                     correctArrow = "↑";
                  }

                  warped = false;
               } else {
                  warped = false;
               }
            }

            if (s.gardenSugarCane) {
               left = false;
               mc.options.keyLeft.setDown(false);
            } else {
               forward = false;
               mc.options.keyUp.setDown(false);
            }
         }

         sendToggleMsg("Backward", backward);
      }
   }

   public static void toggleLeft() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement) {
         Minecraft mc = Minecraft.getInstance();
         left = !left;
         mc.options.keyLeft.setDown(left);
         if (left) {
            if (s.gardenDirectionHelper && warped) {
               if (s.gardenSugarCane) {
                  if (!wasGoingBackward && !wasGoingRight) {
                     warped = false;
                  } else {
                     warningStartTime = System.currentTimeMillis();
                     correctArrow = "↓";
                     warped = false;
                  }
               } else if (!wasGoingLeft) {
                  warningStartTime = System.currentTimeMillis();
                  if (wasGoingRight) {
                     correctArrow = "→";
                  } else if (wasGoingForward) {
                     correctArrow = "↑";
                  } else if (wasGoingBackward) {
                     correctArrow = "↓";
                  } else {
                     correctArrow = "→";
                  }

                  warped = false;
               } else {
                  warped = false;
               }
            }

            if (s.gardenSugarCane) {
               backward = false;
               mc.options.keyDown.setDown(false);
            } else {
               right = false;
               mc.options.keyRight.setDown(false);
            }
         }

         sendToggleMsg("Left", left);
      }
   }

   public static void toggleRight() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement) {
         Minecraft mc = Minecraft.getInstance();
         right = !right;
         mc.options.keyRight.setDown(right);
         if (right) {
            if (s.gardenDirectionHelper && warped) {
               if (s.gardenSugarCane) {
                  if (!wasGoingForward && !wasGoingLeft) {
                     warped = false;
                  } else {
                     warningStartTime = System.currentTimeMillis();
                     correctArrow = "↑";
                     warped = false;
                  }
               } else if (!wasGoingRight) {
                  warningStartTime = System.currentTimeMillis();
                  if (wasGoingLeft) {
                     correctArrow = "←";
                  } else if (wasGoingForward) {
                     correctArrow = "↑";
                  } else if (wasGoingBackward) {
                     correctArrow = "↓";
                  } else {
                     correctArrow = "←";
                  }

                  warped = false;
               } else {
                  warped = false;
               }
            }

            if (s.gardenSugarCane) {
               forward = false;
               mc.options.keyUp.setDown(false);
            } else {
               left = false;
               mc.options.keyLeft.setDown(false);
            }
         }

         sendToggleMsg("Right", right);
      }
   }

   public static void toggleBreak() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement) {
         Minecraft mc = Minecraft.getInstance();
         breaking = !breaking;
         mc.options.keyAttack.setDown(breaking);
         if (breaking && mc.player != null) {
            mc.player.swing(InteractionHand.MAIN_HAND);
         }

         sendToggleMsg("Breaking", breaking);
      }
   }

   public static void toggleUse() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement) {
         Minecraft mc = Minecraft.getInstance();
         using = !using;
         mc.options.keyUse.setDown(using);
         sendToggleMsg("Using", using);
      }
   }

   private static void sendToggleMsg(String dir, boolean active) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.sendOverlayMessage(Component.literal("§b[Garden] §f" + dir + ": " + (active ? "§aON" : "§cOFF")));
      }

   }

   public static void onWarpTriggered() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement) {
         Minecraft mc = Minecraft.getInstance();
         boolean isMovingNow = forward || backward || left || right || mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
         if (!warped || isMovingNow) {
            wasGoingForward = forward || mc.options.keyUp.isDown();
            wasGoingBackward = backward || mc.options.keyDown.isDown();
            wasGoingLeft = left || mc.options.keyLeft.isDown();
            wasGoingRight = right || mc.options.keyRight.isDown();
            if (wasGoingForward || wasGoingBackward || wasGoingLeft || wasGoingRight) {
               warped = true;
            }

            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            reset();
         }
      }
   }

   public static void drawDirectionWarning(GuiGraphicsExtractor g) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.gardenMovement && s.gardenDirectionHelper) {
         long now = System.currentTimeMillis();
         if (now - warningStartTime <= 3000L) {
            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            Font font = mc.font;
            int x = screenWidth / 2;
            int y = screenHeight / 2 - 50;
            String warningText = "§c§lWrong lane brochacho";
            String arrowText = "§6§l" + correctArrow;
            int textW = Math.max(font.width(warningText.replaceAll("(?i)§.", "")), font.width(arrowText.replaceAll("(?i)§.", "")));
            int padX = 10;
            int padY = 8;
            g.fill(x - textW / 2 - padX, y - padY, x + textW / 2 + padX, y + 25 + padY, -1442840576);
            g.outline(x - textW / 2 - padX, y - padY, textW + padX * 2, 25 + padY * 2, -65536);
            g.centeredText(font, warningText, x, y, -1);
            g.centeredText(font, arrowText, x, y + 12, -1);
         }
      }
   }

   public static boolean shouldLockMouse() {
      if (!BomboConfig.get().lockMouseOnGarden || !(forward || backward || left || right)) {
         return false;
      }
      return "The Garden".equalsIgnoreCase(BomboaddonsClient.currentArea) || SkyblockUtils.isInGarden();
   }

   public static void clearWasGoing() {
      wasGoingForward = false;
      wasGoingBackward = false;
      wasGoingLeft = false;
      wasGoingRight = false;
      warped = false;
   }

   public static void onManualReset() {
      clearWasGoing();
      reset();
   }

   public static void stopGardenMovement() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.options != null) {
         mc.options.keyUp.setDown(false);
         mc.options.keyDown.setDown(false);
         mc.options.keyLeft.setDown(false);
         mc.options.keyRight.setDown(false);
         mc.options.keyAttack.setDown(false);
         mc.options.keyUse.setDown(false);
      }
      clearWasGoing();
      reset();
   }

   public static boolean isForward() { return forward; }
   public static boolean isBackward() { return backward; }
   public static boolean isLeft() { return left; }
   public static boolean isRight() { return right; }
   public static boolean isBreaking() { return breaking; }
   public static boolean isUsing() { return using; }

   private static boolean isProcessingChat = false;

   public static void onChatMessage(String clean) {
      if (isProcessingChat) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) return;

      if (clean.contains("You can't break crops with an item while wearing Sun's Grasp!")) {
         isProcessingChat = true;
         try {
            if (s.sunsGraspWarning) {
               mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 1.0f));
               mc.gui.hud.setSubtitle(Component.literal("§eSwitch to bare hands or empty slot!"));
               mc.gui.hud.setTitle(Component.literal("§c§lSun's Grasp Warning!"));
               mc.player.sendSystemMessage(Component.literal("§8[§cGarden Alert§8] §cYou can't break crops with an item while wearing Sun's Grasp!"));
            }

            if (s.sunsGraspAutoSwap) {
               for (int i = 0; i < 9; i++) {
                  net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);
                  if (stack.isEmpty()) {
                     mc.player.getInventory().setSelectedSlot(i);
                     mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aAuto-swapped to empty hotbar slot #" + (i + 1) + " for Sun's Grasp."));
                     break;
                  }
               }
            }
         } finally {
            isProcessingChat = false;
         }
      }
   }

   public static void reset() {
      forward = false;
      backward = false;
      left = false;
      right = false;
      breaking = false;
      using = false;
   }
}
