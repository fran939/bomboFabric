package me.bombo.bomboaddons;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class LeftClickEtherwarp {
   private static int state = 0;
   private static InputConstants.Key sneakKey = null;
   private static long lastHoldFallbackTime = 0L;
   private static long lastFastAotvTime = 0L;
   private static long lastFastHypTime = 0L;
   private static long lastHoldEtherwarpTime = 0L;
   /** True while a held left click is keeping sneak down for us. */
   private static boolean holdingSneak = false;
   /** True when the sneak currently down was pressed by us and has to be released again. */
   private static boolean sneakForcedByHold = false;
   private static boolean wasUseDown = false;
   private static long usePressStartTime = 0L;
   private static long leftClickAttackStartTime = 0L;

   public static boolean isBusy() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) return false;
      boolean anyEnabled = s.leftClickEtherwarp || s.fastAotv || s.fastHyp || s.fastUseEnabled;
      if (!anyEnabled) {
         if (state != 0 || holdingSneak || sneakForcedByHold) {
            reset();
         }
         return false;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.options == null) return false;
      if ((s.fastAotv || s.fastHyp || s.fastUseEnabled) && mc.options.keyUse.isDown()) {
         return true;
      }
      boolean attackDown = mc.options.keyAttack.isDown() && s.leftClickEtherwarp && isHoldingEtherwarp();
      return state != 0 || holdingSneak || sneakForcedByHold || attackDown;
   }

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         reset();
         return;
      }

      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) {
         reset();
         return;
      }

      boolean attackDown = mc.options != null && mc.options.keyAttack.isDown();
      if (!attackDown && (holdingSneak || sneakForcedByHold)) {
         releaseForcedSneak(mc);
      }

      long now = System.currentTimeMillis();

      // Handle Fast Use / Fast AOTV / Fast Hyp
      if (mc.options != null) {
         boolean isUseDown = mc.options.keyUse.isDown();
         if (isUseDown) {
            if (!wasUseDown) {
               // Initial press tick: Minecraft natively calls useItem() for single clicks!
               // Do NOT execute here so single clicks never double-fire.
               wasUseDown = true;
               usePressStartTime = now;
            } else {
               // Player is deliberately holding the use button down (>= 200ms)
               if (now - usePressStartTime >= 200L) {
                  ItemStack held = mc.player.getMainHandItem();
                  if (!held.isEmpty()) {
                     String name = held.getHoverName().getString();

                     if (s.fastUseEnabled && matchesFastUseTarget(held, s)) {
                        int speed = Math.max(1, Math.min(4, s.fastUseSpeed));
                        long delay = (5 - speed) * 50L;
                        if (now - lastFastHypTime >= delay) {
                           lastFastHypTime = now;
                           if (mc.gameMode != null) {
                              mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                              GardenMacroDetector.recordWeaponUse();
                           }
                        }
                     } else if (s.fastAotv && (name.contains("Aspect of the Void") || name.contains("Aspect of the End"))) {
                        int speed = Math.max(1, Math.min(4, s.fastAotvSpeed));
                        long delay = (5 - speed) * 50L;
                        if (now - lastFastAotvTime >= delay) {
                           lastFastAotvTime = now;
                           if (mc.gameMode != null) {
                              mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                              GardenMacroDetector.recordWeaponUse();
                           }
                        }
                     } else if (s.fastHyp && (name.contains("Hyperion") || name.contains("Valkyrie") || name.contains("Scylla") || name.contains("Astraea") || name.contains("Necron's Blade"))) {
                        int speed = Math.max(1, Math.min(4, s.fastHypSpeed));
                        long delay = (5 - speed) * 50L;
                        if (now - lastFastHypTime >= delay) {
                           lastFastHypTime = now;
                           if (mc.gameMode != null) {
                              mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                              GardenMacroDetector.recordWeaponUse();
                           }
                        }
                     }
                  }
               }
            }
         } else {
            wasUseDown = false;
            usePressStartTime = 0L;
         }
      }

      if (state != 0) {
         if (state == 1) {
            if (mc.gameMode != null) {
               mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
               GardenMacroDetector.recordWeaponUse();
               lastHoldEtherwarpTime = now;
            }
            state = 2;
            if (!attackDown) {
               releaseForcedSneak(mc);
            }
         } else if (state == 2) {
            if (!attackDown) {
               releaseForcedSneak(mc);
            }
         }
      }
   }

   private static boolean matchesFastUseTarget(ItemStack held, BomboConfig.Settings settings) {
      if (settings.fastUseBlocksOnly && !(held.getItem() instanceof BlockItem)) return false;
      if (settings.fastUseSkyblockId == null || settings.fastUseSkyblockId.isBlank()) return true;
      String heldId = SkyblockUtils.getSkyblockId(held);
      return settings.fastUseSkyblockId.equalsIgnoreCase(heldId);
   }

   public static void configureFastUse(int speed, boolean blocksOnly, String skyblockId) {
      BomboConfig.Settings settings = BomboConfig.get();
      settings.fastUseEnabled = true;
      settings.fastUseSpeed = Math.max(1, Math.min(4, speed));
      settings.fastUseBlocksOnly = blocksOnly;
      settings.fastUseSkyblockId = skyblockId == null ? "" : skyblockId.trim().toUpperCase();
      BomboConfig.save();
   }

   public static void disableFastUse() {
      BomboConfig.Settings settings = BomboConfig.get();
      settings.fastUseEnabled = false;
      settings.fastUseBlocksOnly = false;
      settings.fastUseSkyblockId = "";
      BomboConfig.save();
   }

   private static void reset() {
      state = 0;
      holdingSneak = false;
      sneakForcedByHold = false;
   }

   public static boolean isHoldingEtherwarp() {
      if (!BomboConfig.get().leftClickEtherwarp) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return false;
         } else {
            ItemStack heldItem = mc.player.getMainHandItem();
            if (heldItem.isEmpty()) {
               return false;
            } else {
               String itemName = heldItem.getHoverName().getString();
               return itemName.contains("Aspect of the Void") || itemName.contains("Aspect of the End");
            }
         }
      }
   }

   public static int getEtherwarpMaxDistance(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return 61;
      try {
         net.minecraft.world.item.component.ItemLore lore = (net.minecraft.world.item.component.ItemLore)stack.get(net.minecraft.core.component.DataComponents.LORE);
         if (lore != null) {
            for (Component line : lore.lines()) {
               String clean = line.getString().replaceAll("(?i)§.", "");
               // e.g. "to 61 blocks away." or "to 57 blocks away."
               if (clean.contains("blocks away") && clean.contains("to ")) {
                  int idx = clean.indexOf("to ");
                  String afterTo = clean.substring(idx + 3).trim();
                  int bIdx = afterTo.indexOf(" blocks");
                  if (bIdx != -1) {
                     String distStr = afterTo.substring(0, bIdx).trim();
                     return Integer.parseInt(distStr);
                  }
               }
            }
         }
      } catch (Throwable ignored) {}
      return 61;
   }

   public static boolean isValidEtherwarpTarget(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos targetPos, net.minecraft.core.Direction hitDirection) {
      if (level == null || targetPos == null) return false;
      net.minecraft.world.level.block.state.BlockState targetState = level.getBlockState(targetPos);
      if (targetState.isAir()) return false;

      // 1. Direct stand on clicked block:
      // [standPos.above(2)] (air/passable)
      // [standPos.above(1)] (air/passable)
      // [standPos] (clicked block / surface)
      net.minecraft.core.BlockPos feet1 = targetPos.above(1);
      net.minecraft.core.BlockPos head1 = targetPos.above(2);
      boolean feet1Passable = level.getBlockState(feet1).getCollisionShape(level, feet1).isEmpty();
      boolean head1Passable = level.getBlockState(head1).getCollisionShape(level, head1).isEmpty();
      if (feet1Passable && head1Passable) {
         return true;
      }

      // 2. Stand on block above target (e.g., player right-clicks fence with solid block on top):
      // [targetPos.above(3)] (air)
      // [targetPos.above(2)] (air)
      // [targetPos.above(1)] (solid block on fence)
      // [targetPos] (fence)
      net.minecraft.world.level.block.state.BlockState aboveState = level.getBlockState(targetPos.above(1));
      if (!aboveState.isAir() && !aboveState.getCollisionShape(level, targetPos.above(1)).isEmpty()) {
         net.minecraft.core.BlockPos feet2 = targetPos.above(2);
         net.minecraft.core.BlockPos head2 = targetPos.above(3);
         boolean feet2Passable = level.getBlockState(feet2).getCollisionShape(level, feet2).isEmpty();
         boolean head2Passable = level.getBlockState(head2).getCollisionShape(level, head2).isEmpty();
         if (feet2Passable && head2Passable) {
            return true;
         }
      }

      return false;
   }

   public static boolean onLeftClick() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.leftClickEtherwarp) return false;
      if (isHoldingEtherwarp()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null || mc.level == null) return false;

         long now = System.currentTimeMillis();
         leftClickAttackStartTime = now;

         ItemStack heldItem = mc.player.getMainHandItem();
         double maxDist = getEtherwarpMaxDistance(heldItem);

         // Raycast from eyes along look vector
         Vec3 eyePos = mc.player.getEyePosition();
         Vec3 lookVec = mc.player.getViewVector(1.0F);
         Vec3 endPos = eyePos.add(lookVec.scale(maxDist));
         BlockHitResult hit = mc.level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));

         boolean isLookingAtBlockInRange = (hit.getType() == HitResult.Type.BLOCK);
         boolean canEtherwarp = isLookingAtBlockInRange && isValidEtherwarpTarget(mc.level, hit.getBlockPos(), hit.getDirection());

         if (s.etherwarpBlockOnly) {
            if (!canEtherwarp) {
               if (s.etherwarpFallbackRightClick) {
                  // Perform normal right click without sneak (e.g. AOTV normal teleport / use item) without arm swing
                  if (mc.gameMode != null) {
                     mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                     GardenMacroDetector.recordWeaponUse();
                     lastHoldFallbackTime = now;
                  }
                  return true;
               } else {
                  // Do nothing, block the attack/swing
                  return true;
               }
            }
         }

         // Target block in range and valid -> perform etherwarp sneak + right click
         if (state == 0) {
            if (mc.options.keyShift.isDown()) {
               if (mc.gameMode != null) {
                  mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                  GardenMacroDetector.recordWeaponUse();
                  lastHoldEtherwarpTime = now;
                  state = 2; // completed for this click
               }
            } else {
               getSneakMapping(mc).setDown(true);
               sendSneakPacket(mc, true);
               sneakForcedByHold = true;
               state = 1;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static void onContinueAttack() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.leftClickEtherwarp) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.options == null || !mc.options.keyAttack.isDown()) return;
      if (isHoldingEtherwarp()) {
         if (mc.player == null || mc.level == null) return;

         long now = System.currentTimeMillis();
         // Do NOT fire onContinueAttack on the initial click frame! Only when held for >= 250ms!
         if (now - leftClickAttackStartTime < 250L) return;

         ItemStack heldItem = mc.player.getMainHandItem();
         double maxDist = getEtherwarpMaxDistance(heldItem);
         Vec3 eyePos = mc.player.getEyePosition();
         Vec3 lookVec = mc.player.getViewVector(1.0F);
         Vec3 endPos = eyePos.add(lookVec.scale(maxDist));
         BlockHitResult hit = mc.level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
         boolean canEtherwarp = (hit.getType() == HitResult.Type.BLOCK) && isValidEtherwarpTarget(mc.level, hit.getBlockPos(), hit.getDirection());

         if (!canEtherwarp) {
            holdingSneak = false;
            releaseForcedSneak(mc);
         }

         if (canEtherwarp) {
            holdingSneak = true;
            if (!mc.options.keyShift.isDown()) {
               getSneakMapping(mc).setDown(true);
               sendSneakPacket(mc, true);
               sneakForcedByHold = true;
            }
            if (now - lastHoldEtherwarpTime >= 200L) {
               lastHoldEtherwarpTime = now;
               if (mc.gameMode != null) {
                  mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                  GardenMacroDetector.recordWeaponUse();
               }
            }
            return;
         }

         if (s.etherwarpBlockOnly && s.etherwarpFallbackRightClick) {
            if (now - lastHoldFallbackTime >= 200L) {
               lastHoldFallbackTime = now;
               if (mc.gameMode != null) {
                  mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                  GardenMacroDetector.recordWeaponUse();
               }
            }
         }
      }
   }

   /** Releases a sneak that a held left click pressed, so the player never keeps crouching. */
   private static void releaseForcedSneak(Minecraft mc) {
      if (!sneakForcedByHold && !holdingSneak) return;
      sneakForcedByHold = false;
      holdingSneak = false;
      state = 0;
      if (mc.options != null && mc.options.keyShift != null) {
         boolean physicallyPressed = false;
         try {
            long handle = mc.getWindow().handle();
            if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
               physicallyPressed = true;
            }
         } catch (Throwable ignored) {}

         if (!physicallyPressed) {
            mc.options.keyShift.setDown(false);
            if (mc.player != null) {
               mc.player.setShiftKeyDown(false);
            }
            sendSneakPacket(mc, false);
         }
      }
   }

   private static KeyMapping getSneakMapping(Minecraft mc) {
      return mc.options.keyShift;
   }

   private static void sendSneakPacket(Minecraft mc, boolean start) {
      if (mc.getConnection() != null && mc.player != null) {
         ServerboundPlayerCommandPacket.Action action = null;

         try {
            action = Action.valueOf(start ? "PRESS_SHIFT_KEY" : "RELEASE_SHIFT_KEY");
         } catch (Exception var6) {
            try {
               action = Action.valueOf(start ? "START_SNEAKING" : "STOP_SNEAKING");
            } catch (Exception var5) {
            }
         }

         if (action != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, action));
         }

      }
   }
}
