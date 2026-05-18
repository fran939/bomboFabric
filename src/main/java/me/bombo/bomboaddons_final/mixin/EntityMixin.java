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

   private me.bombo.bomboaddons_final.TargetPests.EntityInfoCache getCachedInfo(Entity self) {
      if (me.bombo.bomboaddons_final.TargetPests.infoCache.size() > 5000) {
         me.bombo.bomboaddons_final.TargetPests.infoCache.clear();
      }
      int id = self.getId();
      long now = System.currentTimeMillis();
      me.bombo.bomboaddons_final.TargetPests.EntityInfoCache cached = me.bombo.bomboaddons_final.TargetPests.infoCache.get(id);
      
      if (cached != null && (now - cached.lastCheckMs) < 500) {
         return cached;
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

      String finalCombined = combinedName.toString();
      String nametag = getNearbyNametagName(self);

      me.bombo.bomboaddons_final.TargetPests.EntityInfoCache newValue = new me.bombo.bomboaddons_final.TargetPests.EntityInfoCache(now, finalCombined, nametag);
      me.bombo.bomboaddons_final.TargetPests.infoCache.put(id, newValue);
      return newValue;
   }

   @Inject(method = { "isCurrentlyGlowing" }, at = { @At("HEAD") }, cancellable = true)
   private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      BomboConfig.Settings config = BomboConfig.get();
      Entity self = (Entity) (Object) this;

      boolean shouldGlow = false;

      // 1. Or if highlights are enabled and matches config highlights
      if (config.highlightsEnabled) {
         me.bombo.bomboaddons_final.TargetPests.EntityInfoCache cache = getCachedInfo(self);
         String name = cache.combinedName;
         String nametagName = cache.nametagName;

         if (!name.isEmpty() || nametagName != null) {
            for (Entry<String, BomboConfig.HighlightInfo> entry : config.highlights.entrySet()) {
               String key = entry.getKey();
               if ((!name.isEmpty() && name.contains(key)) || (nametagName != null && nametagName.contains(key))) {
                  if (self.isInvisible() && !entry.getValue().showInvisible) {
                     continue;
                  }
                  shouldGlow = true;
                  break;
               }
            }
         }
      }

      if (shouldGlow) {
         // If hideCheats is enabled, enforce line of sight (no seeing through walls!)
         if (config.hideCheats) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && !mc.player.hasLineOfSight(self)) {
               return;
            }
         }
         cir.setReturnValue(true);
      }
   }

   @Inject(method = { "getTeamColor" }, at = { @At("HEAD") }, cancellable = true)
   private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
      BomboConfig.Settings config = BomboConfig.get();
      Entity self = (Entity) (Object) this;

      if (config.highlightsEnabled) {
         me.bombo.bomboaddons_final.TargetPests.EntityInfoCache cache = getCachedInfo(self);
         String name = cache.combinedName;
         String nametagName = cache.nametagName;

         if (!name.isEmpty() || nametagName != null) {
            for (Entry<String, BomboConfig.HighlightInfo> entry : config.highlights.entrySet()) {
               String key = entry.getKey();
               if ((!name.isEmpty() && name.contains(key)) || (nametagName != null && nametagName.contains(key))) {
                  String colorStr = entry.getValue().color.replace("#", "");
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
