package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BazaarUtils;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ClickLogic;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_11908;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_2487;
import net.minecraft.class_310;
import net.minecraft.class_465;
import net.minecraft.class_9279;
import net.minecraft.class_9334;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({class_465.class})
public abstract class ItemHotkeysMixin {
   @Shadow
   protected class_1735 field_2787;

   @Inject(
      method = {"method_25404"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPressed(class_11908 event, CallbackInfoReturnable<Boolean> cir) {
      if (this.field_2787 != null && this.field_2787.method_7681()) {
         int pressedKey = event.comp_4795();
         int tradeBoundKey = ClickLogic.getKeyCode(BomboConfig.get().tradeKey);
         int recipeBoundKey = ClickLogic.getKeyCode(BomboConfig.get().recipeKey);
         int usageBoundKey = ClickLogic.getKeyCode(BomboConfig.get().usageKey);
         if (pressedKey != -1 && pressedKey == tradeBoundKey) {
            if (this.handleKey(this.field_2787.method_7677(), "TRADE")) {
               cir.setReturnValue(true);
            }
         } else if (pressedKey != -1 && pressedKey == recipeBoundKey) {
            if (this.handleKey(this.field_2787.method_7677(), "RECIPE")) {
               cir.setReturnValue(true);
            }
         } else if (pressedKey != -1 && pressedKey == usageBoundKey && this.handleKey(this.field_2787.method_7677(), "USAGE")) {
            cir.setReturnValue(true);
         }

      }
   }

   private boolean handleKey(class_1799 itemStack, String action) {
      String skyblockId = null;
      class_9279 customData = (class_9279)itemStack.method_58694(class_9334.field_49628);
      if (customData != null) {
         class_2487 tag = customData.method_57461();
         if (tag.method_10545("id")) {
            skyblockId = (String)tag.method_10558("id").orElse((Object)null);
         } else if (tag.method_10545("ExtraAttributes")) {
            class_2487 extraAttributes = (class_2487)tag.method_10562("ExtraAttributes").orElse((Object)null);
            if (extraAttributes != null && extraAttributes.method_10545("id")) {
               skyblockId = (String)extraAttributes.method_10558("id").orElse((Object)null);
            }
         }
      }

      if (skyblockId == null) {
         skyblockId = itemStack.method_7909().toString().toUpperCase();
      }

      System.out.println("[ItemHotkeys] Action: " + action + ", Extracted ID: " + skyblockId);
      if (skyblockId != null) {
         if (action.equals("RECIPE") || action.equals("USAGE")) {
            class_310.method_1551().field_1724.field_3944.method_45730("viewrecipe " + skyblockId);
            return true;
         }

         if (action.equals("TRADE")) {
            boolean isBazaar = BazaarUtils.isBazaarItem(skyblockId);
            String cleanName = this.cleanName(itemStack.method_7964().getString());
            if (isBazaar) {
               class_310.method_1551().field_1724.field_3944.method_45730("bz " + cleanName.toLowerCase());
            } else {
               class_310.method_1551().field_1724.field_3944.method_45730("ahs " + cleanName);
            }

            return true;
         }
      }

      return false;
   }

   private String cleanName(String originalName) {
      String name = originalName.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
      if (name.startsWith("[Lvl")) {
         int closingBracket = name.indexOf(93);
         if (closingBracket != -1 && closingBracket + 2 < name.length()) {
            name = name.substring(closingBracket + 2);
         }
      }

      String[] reforges = new String[]{"Gentle ", "Odd ", "Fast ", "Fair ", "Epic ", "Sharp ", "Heroic ", "Spicy ", "Legendary ", "Dirty ", "Fabled ", "Suspicious ", "Gilded ", "Warped ", "Withered ", "Bulky ", "Stellar ", "Heated ", "Ambered ", "Fruitful ", "Magnetic ", "Fleet ", "Mithraic ", "Auspicious ", "Refined ", "Headstrong ", "Precise ", "Spiritual ", "Renowned ", "Giant ", "Submerged ", "Jaded ", "Loving ", "Necrotic ", "Ancient ", "Undead ", "Red ", "Snaded ", "Pitchin ", "Beady ", "Ridiculous ", "Unreal ", "Awkward ", "Rich ", "Fine ", "Neat ", "Hasty ", "Grand ", "Rapid ", "Deadly "};
      String[] var4 = reforges;
      int var5 = reforges.length;

      for(int var6 = 0; var6 < var5; ++var6) {
         String reforge = var4[var6];
         if (name.startsWith(reforge)) {
            name = name.substring(reforge.length());
            break;
         }
      }

      return name.trim();
   }
}
