package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.AutoFishing;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class DojoUtilities {

   public static boolean isInDojo() {
      String area = SkyblockUtils.getLocation();
      String sub = SkyblockUtils.getSubArea();
      String areaLower = area != null ? area.toLowerCase() : "";
      String subLower = sub != null ? sub.toLowerCase() : "";

      boolean isCrimson = areaLower.contains("crimson") || subLower.contains("crimson");
      boolean isDojoSub = subLower.contains("dojo");

      return isCrimson || isDojoSub;
   }

   public static void onPreAttack(Minecraft mc) {
      if (mc.player == null || mc.level == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.dojoUtilities) return;

      if (!isInDojo()) return;

      Entity target = null;
      if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY && mc.hitResult instanceof EntityHitResult ehr) {
         target = ehr.getEntity();
      }

      if (target instanceof LivingEntity living) {
         handleZombieAttack(mc, living);
      }
   }

   private static void handleZombieAttack(Minecraft mc, LivingEntity entity) {
      ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
      if (head.isEmpty()) return;

      int requiredSwordSlot = -1;

      if (head.is(Items.LEATHER_HELMET)) {
         requiredSwordSlot = findSwordSlot(mc, "wooden_sword");
      } else if (head.is(Items.IRON_HELMET)) {
         requiredSwordSlot = findSwordSlot(mc, "iron_sword");
      } else if (head.is(Items.GOLDEN_HELMET)) {
         requiredSwordSlot = findSwordSlot(mc, "golden_sword");
      } else if (head.is(Items.DIAMOND_HELMET)) {
         requiredSwordSlot = findSwordSlot(mc, "diamond_sword");
      }

      if (requiredSwordSlot != -1) {
         int currentSlot = AutoFishing.getSelectedSlot(mc);
         if (currentSlot != requiredSwordSlot) {
            AutoFishing.setSelectedSlot(mc, requiredSwordSlot);
         }
      }
   }

   private static int findSwordSlot(Minecraft mc, String targetItemPath) {
      if (mc.player == null) return -1;
      for (int i = 0; i < 9; i++) {
         ItemStack stack = mc.player.getInventory().getItem(i);
         if (!stack.isEmpty()) {
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
            if (path.equals(targetItemPath)) {
               return i;
            }
         }
      }
      return -1;
   }
}
