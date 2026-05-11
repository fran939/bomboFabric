package me.bombo.bomboaddons_final.mixin;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import me.bombo.bomboaddons_final.BomboConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({ Entity.class })
public abstract class EntityMixin {
   @Inject(method = { "isInvisibleTo" }, at = { @At("HEAD") }, cancellable = true)
   private void onIsInvisibleTo(Player player, CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().debugMode || BomboConfig.get().debugEntities) {
         cir.setReturnValue(false);
      }

   }

   @Inject(method = { "isInvisible" }, at = { @At("HEAD") }, cancellable = true)
   private void onIsInvisible(CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().debugMode || BomboConfig.get().debugEntities) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = { "isCurrentlyGlowing" }, at = { @At("HEAD") }, cancellable = true)
   private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      Entity self = (Entity) (Object) this;

      if (BomboConfig.get().pestEsp) {
         if (self instanceof net.minecraft.world.entity.decoration.ArmorStand stand && me.bombo.bomboaddons_final.TargetPests.getPestName(stand) != null) { cir.setReturnValue(true); return; }
         for (Entity p : self.getPassengers()) { if (p instanceof net.minecraft.world.entity.decoration.ArmorStand stand && me.bombo.bomboaddons_final.TargetPests.getPestName(stand) != null) { cir.setReturnValue(true); return; } }
         if (self.getVehicle() instanceof net.minecraft.world.entity.decoration.ArmorStand stand && me.bombo.bomboaddons_final.TargetPests.getPestName(stand) != null) { cir.setReturnValue(true); return; }
      }

      if (!BomboConfig.get().highlightsEnabled) {
         return;
      }
      String name = ChatFormatting.stripFormatting(self.getDisplayName().getString());
      StringBuilder combinedName = new StringBuilder(name != null ? name.toLowerCase() : "");

      if (self instanceof net.minecraft.world.entity.decoration.ArmorStand) {
         String pestName = me.bombo.bomboaddons_final.TargetPests
               .getPestName((net.minecraft.world.entity.decoration.ArmorStand) self);
         if (pestName != null) {
            combinedName.append(" | ").append(pestName);
         }
      }

      for (Entity passenger : self.getPassengers()) {
         String pName = ChatFormatting.stripFormatting(passenger.getDisplayName().getString());
         if (pName != null) {
            combinedName.append(" | ").append(pName.toLowerCase());
         }
         if (passenger instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            String pestName = me.bombo.bomboaddons_final.TargetPests
                  .getPestName((net.minecraft.world.entity.decoration.ArmorStand) passenger);
            if (pestName != null) {
               combinedName.append(" | ").append(pestName);
            }
         }
      }

      Entity vehicle = self.getVehicle();
      if (vehicle != null) {
         String vName = ChatFormatting.stripFormatting(vehicle.getDisplayName().getString());
         if (vName != null) {
            combinedName.append(" | ").append(vName.toLowerCase());
         }
         if (vehicle instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            String pestName = me.bombo.bomboaddons_final.TargetPests
                  .getPestName((net.minecraft.world.entity.decoration.ArmorStand) vehicle);
            if (pestName != null) {
               combinedName.append(" | ").append(pestName);
            }
         }
      }

      name = combinedName.toString();
      String nametagName = getNearbyNametagName(self);

      if (!name.isEmpty() || nametagName != null) {
         Iterator var4 = BomboConfig.get().highlights.entrySet().iterator();

         while (var4.hasNext()) {
            Entry<String, BomboConfig.HighlightInfo> entry = (Entry) var4.next();
            String key = (String) entry.getKey();
            if ((!name.isEmpty() && name.contains(key)) || (nametagName != null && nametagName.contains(key))) {
               if (self.isInvisible() && !((BomboConfig.HighlightInfo) entry.getValue()).showInvisible) {
                  continue;
               }


               cir.setReturnValue(true);
               return;
            }
         }
      }

   }

   @Inject(method = { "getTeamColor" }, at = { @At("HEAD") }, cancellable = true)
   private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
      Entity self = (Entity) (Object) this;

      if (BomboConfig.get().pestEsp) {
         boolean isPest = false;
         if (self instanceof net.minecraft.world.entity.decoration.ArmorStand stand && me.bombo.bomboaddons_final.TargetPests.getPestName(stand) != null) isPest = true;
         for (Entity p : self.getPassengers()) { if (p instanceof net.minecraft.world.entity.decoration.ArmorStand stand && me.bombo.bomboaddons_final.TargetPests.getPestName(stand) != null) isPest = true; }
         if (self.getVehicle() instanceof net.minecraft.world.entity.decoration.ArmorStand stand && me.bombo.bomboaddons_final.TargetPests.getPestName(stand) != null) isPest = true;
         
         if (isPest) {
            String colorStr = BomboConfig.get().pestEspColor.toUpperCase().replace("#", "");
            try {
               ChatFormatting format = ChatFormatting.valueOf(colorStr);
               if (format != null && format.getColor() != null) {
                  cir.setReturnValue(format.getColor());
                  return;
               }
            } catch (Exception ignored) {
               try {
                  int hex = Integer.parseInt(colorStr, 16);
                  cir.setReturnValue(hex);
                  return;
               } catch (NumberFormatException ignored2) {}
            }
         }
      }

      String name = ChatFormatting.stripFormatting(self.getDisplayName().getString());
      StringBuilder combinedName = new StringBuilder(name != null ? name.toLowerCase() : "");

      if (self instanceof net.minecraft.world.entity.decoration.ArmorStand) {
         String pestName = me.bombo.bomboaddons_final.TargetPests
               .getPestName((net.minecraft.world.entity.decoration.ArmorStand) self);
         if (pestName != null) {
            combinedName.append(" | ").append(pestName);
         }
      }

      for (Entity passenger : self.getPassengers()) {
         String pName = ChatFormatting.stripFormatting(passenger.getDisplayName().getString());
         if (pName != null) {
            combinedName.append(" | ").append(pName.toLowerCase());
         }
         if (passenger instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            String pestName = me.bombo.bomboaddons_final.TargetPests
                  .getPestName((net.minecraft.world.entity.decoration.ArmorStand) passenger);
            if (pestName != null) {
               combinedName.append(" | ").append(pestName);
            }
         }
      }

      Entity vehicle = self.getVehicle();
      if (vehicle != null) {
         String vName = ChatFormatting.stripFormatting(vehicle.getDisplayName().getString());
         if (vName != null) {
            combinedName.append(" | ").append(vName.toLowerCase());
         }
         if (vehicle instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            String pestName = me.bombo.bomboaddons_final.TargetPests
                  .getPestName((net.minecraft.world.entity.decoration.ArmorStand) vehicle);
            if (pestName != null) {
               combinedName.append(" | ").append(pestName);
            }
         }
      }

      name = combinedName.toString();
      String nametagName = getNearbyNametagName(self);

      if (!name.isEmpty() || nametagName != null) {
         Iterator var4 = BomboConfig.get().highlights.entrySet().iterator();

         while (var4.hasNext()) {
            Entry<String, BomboConfig.HighlightInfo> entry = (Entry) var4.next();
            String key = (String) entry.getKey();
            if ((!name.isEmpty() && name.contains(key)) || (nametagName != null && nametagName.contains(key))) {
               String colorStr = ((BomboConfig.HighlightInfo) entry.getValue()).color.replace("#", "");
               try {
                  ChatFormatting format = ChatFormatting.valueOf(colorStr);
                  if (format != null && format.getColor() != null) {

                     cir.setReturnValue(format.getColor());
                     return;
                  }
               } catch (Exception var7) {
                  try {
                     int hex = Integer.parseInt(colorStr, 16);

                     cir.setReturnValue(hex);
                     return;
                  } catch (NumberFormatException ignored) {

                  }
               }
            }
         }
      }

   }

   @Inject(method = { "isCustomNameVisible" }, at = { @At("HEAD") }, cancellable = true)
   private void onIsCustomNameVisible(CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().debugMode || BomboConfig.get().debugEntities) {
         cir.setReturnValue(true);
      }

   }

   private String getNearbyNametagName(Entity self) {
      if (self instanceof ArmorStand || self.level() == null) {
         return null;
      }
      List<Entity> nearby = self.level().getEntities(self, self.getBoundingBox().inflate(0.5D, 3.0D, 0.5D),
            (e) -> e instanceof ArmorStand && e.hasCustomName());
      for (Entity e : nearby) {
         String name = ChatFormatting.stripFormatting(e.getCustomName().getString());
         if (name != null && !name.isEmpty()) {
            return name.toLowerCase();
         }
      }
      return null;
   }
}
