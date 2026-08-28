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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class LeftClickEtherwarp {
   private static int state = 0;
   private static InputConstants.Key sneakKey = null;

   public static boolean isBusy() {
      return state != 0;
   }

   public static void onTick() {
      if (state != 0) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            reset();
         } else if (state == 1) {
            if (mc.gameMode != null) {
               mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
               GardenMacroDetector.recordWeaponUse();
            }

            state = 2;
         } else if (state == 2) {
            sendSneakPacket(mc, false);
            getSneakMapping(mc).setDown(false);
            reset();
         }
      }

   }

   private static void reset() {
      state = 0;
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

   public static boolean onLeftClick() {
      if (isHoldingEtherwarp()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null || mc.level == null) return false;

         BomboConfig.Settings s = BomboConfig.get();
         ItemStack heldItem = mc.player.getMainHandItem();
         double maxDist = getEtherwarpMaxDistance(heldItem);

         // Raycast from eyes along look vector
         Vec3 eyePos = mc.player.getEyePosition();
         Vec3 lookVec = mc.player.getViewVector(1.0F);
         Vec3 endPos = eyePos.add(lookVec.scale(maxDist));
         BlockHitResult hit = mc.level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));

         boolean isLookingAtBlockInRange = (hit.getType() == HitResult.Type.BLOCK);

         if (s.etherwarpBlockOnly) {
            if (!isLookingAtBlockInRange) {
               if (s.etherwarpFallbackRightClick) {
                  // Perform normal right click without sneak (e.g. AOTV normal teleport / use item) without arm swing
                  if (mc.gameMode != null) {
                     mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                     GardenMacroDetector.recordWeaponUse();
                  }
                  return true;
               } else {
                  // Do nothing, block the attack/swing
                  return true;
               }
            }
         }

         // Target block in range (or etherwarpBlockOnly is false) -> perform etherwarp sneak + right click
         if (state == 0) {
            if (mc.options.keyShift.isDown()) {
               if (mc.gameMode != null) {
                  mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                  GardenMacroDetector.recordWeaponUse();
               }
            } else {
               getSneakMapping(mc).setDown(true);
               sendSneakPacket(mc, true);
               state = 1;
            }
         }

         return true;
      } else {
         return false;
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
