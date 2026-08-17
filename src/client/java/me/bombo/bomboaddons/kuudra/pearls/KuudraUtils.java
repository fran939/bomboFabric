package me.bombo.bomboaddons.kuudra.pearls;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

public class KuudraUtils {
   private static ArmorStand elleEntity;
   private static MagmaCube kuudraEntity;
   private static final List<MagmaCube> magmaCubes = new ArrayList();
   private static final List<Vec3> crates = new ArrayList();
   private static final Supply[] supplies;
   private static boolean dirty;
   private static int scanCounter;

   private static long lastKuudraCheckTime = 0L;
   private static boolean cachedInKuudra = false;

   public static boolean inKuudra() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return false;
      }
      long now = System.currentTimeMillis();
      if (now - lastKuudraCheckTime < 1000L) {
         return cachedInKuudra;
      }
      lastKuudraCheckTime = now;

      String area = BomboaddonsClient.currentArea;
      if ("Kuudra".equalsIgnoreCase(area) || "Kuudra's Hollow".equalsIgnoreCase(area) || (area != null && area.matches("^T[1-5]$"))) {
         cachedInKuudra = true;
         return true;
      }

      Objective sidebar = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
      if (sidebar != null) {
         for(String line : SkyblockUtils.getSidebarLines(mc.level.getScoreboard(), sidebar)) {
            if (line.toLowerCase().contains("kuudra")) {
               cachedInKuudra = true;
               return true;
            }
         }
      }

      cachedInKuudra = false;
      return false;
   }

   public static int getKuudraTier() {
      String area = BomboaddonsClient.currentArea;
      if (area != null && area.matches("^T[1-5]$")) {
         return Character.getNumericValue(area.charAt(1));
      } else {
         String sub = BomboaddonsClient.currentSubArea;
         if (sub != null && sub.matches("^T[1-5]$")) {
            return Character.getNumericValue(sub.charAt(1));
         } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
               Objective sidebar = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
               if (sidebar != null) {
                  for(String line : SkyblockUtils.getSidebarLines(mc.level.getScoreboard(), sidebar)) {
                     String clean = line.replaceAll("(?i)§.", "").trim();
                     if (clean.contains("(T")) {
                        int idx = clean.indexOf("(T");
                        if (idx + 2 < clean.length()) {
                           char c = clean.charAt(idx + 2);
                           if (c >= '1' && c <= '5') {
                              return Character.getNumericValue(c);
                           }
                        }
                     }
                  }
               }
            }

            return BomboConfig.get().kuudraTiers;
         }
      }
   }

   public static void onWorldUnload() {
      if (dirty) {
         dirty = false;
         elleEntity = null;
         kuudraEntity = null;
         magmaCubes.clear();
         crates.clear();
         resetSupplies();
      }

   }

   public static void onClientTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (!inKuudra()) {
            if (dirty) {
               onWorldUnload();
            }

         } else {
            ++scanCounter;
            if (scanCounter % 4 == 0 || !dirty) {
               magmaCubes.clear();
               crates.clear();
               dirty = true;

               for(Entity entity : mc.level.entitiesForRendering()) {
                  if (entity != null) {
                     processEntity(entity);
                  }
               }

            }
         }
      }
   }

   private static void processEntity(Entity entity) {
      if (entity instanceof ArmorStand) {
         String name = entity.getName().getString();
         if (name == null || name.isEmpty()) {
            return;
         }

         name = name.replaceAll("§[0-9a-fk-or]", "");
         if ("Elle".equals(name)) {
            elleEntity = (ArmorStand)entity;
            return;
         }

         int matchedSpot = -1;
         double bestDistSq = (double)4.0F;

         for(int idx = 0; idx < supplies.length; ++idx) {
            SupplySpot spot = supplies[idx].getSpot();
            double dx = entity.getX() - spot.getLocation().x;
            double dz = entity.getZ() - spot.getLocation().z;
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
               bestDistSq = distSq;
               matchedSpot = idx;
            }
         }

         if (matchedSpot != -1) {
            processSupply(matchedSpot, name);
         }
      } else if (entity instanceof MagmaCube) {
         MagmaCube magmaCube = (MagmaCube)entity;
         magmaCubes.add(magmaCube);
         if (isKuudraEntity(magmaCube)) {
            kuudraEntity = magmaCube;
         }
      } else if (entity instanceof Giant && entity.getY() < (double)67.0F) {
         float yawRad = (float)((double)(entity.getYRot() + 130.0F) * (Math.PI / 180D));
         double offsetX = 3.7 * Math.cos((double)yawRad);
         double offsetZ = 3.7 * Math.sin((double)yawRad);
         double x = entity.getX() + (double)0.5F + offsetX;
         double z = entity.getZ() + (double)0.5F + offsetZ;
         crates.add(new Vec3(x, (double)75.0F, z));
      }

   }

   private static void processSupply(int pos, String name) {
      SupplyStatus status = getStatusFromName(name);
      if (status != null) {
         supplies[pos].setStatus(status);
         if (status == SupplyStatus.INPROGRESS && name.startsWith("PROGRESS:")) {
            try {
               String percentStr = name.substring("PROGRESS:".length()).replace("%", "").trim();
               float percent = Float.parseFloat(percentStr);
               supplies[pos].setProgressColor(getProgressColor(percent));
            } catch (NumberFormatException var5) {
            }
         }

      }
   }

   private static SupplyStatus getStatusFromName(String name) {
      if (name.contains("BRING SUPPLY CHEST HERE")) {
         return SupplyStatus.NOTHING;
      } else if (name.contains("SUPPLIES RECEIVED")) {
         return SupplyStatus.RECEIVED;
      } else if (name.contains("PROGRESS: ")) {
         return name.contains("COMPLETE") ? SupplyStatus.COMPLETED : SupplyStatus.INPROGRESS;
      } else {
         return null;
      }
   }

   private static float[] getProgressColor(float percent) {
      percent = Math.max(0.0F, Math.min(100.0F, percent));
      float ratio = (float)Math.pow((double)(percent / 100.0F), (double)1.5F);
      float red = 1.0F - ratio;
      return new float[]{red, ratio};
   }

   private static boolean isKuudraEntity(MagmaCube magmaCube) {
      return magmaCube.getBbWidth() > 14.0F && magmaCube.getHealth() <= 100000.0F;
   }

   private static void resetSupplies() {
      for(Supply supply : supplies) {
         supply.reset();
      }

   }

   public static ArmorStand getElle() {
      return elleEntity;
   }

   public static MagmaCube getKuudra() {
      return kuudraEntity;
   }

   public static List<Vec3> getCrates() {
      return crates;
   }

   public static List<MagmaCube> getMagmaCubes() {
      return magmaCubes;
   }

   public static Supply[] getSupplies() {
      return supplies;
   }

   public static List<Vec3> getAllUncompletedSupplies() {
      List<Vec3> uncompleted = new ArrayList();

      for(Supply supply : supplies) {
         if (supply.getStatus() == SupplyStatus.NOTHING) {
            uncompleted.add(supply.getSpot().getLocation());
         }
      }

      return uncompleted;
   }

   static {
      supplies = new Supply[]{new Supply(SupplySpot.SUPPLY1), new Supply(SupplySpot.SUPPLY2), new Supply(SupplySpot.SUPPLY3), new Supply(SupplySpot.SUPPLY4), new Supply(SupplySpot.SUPPLY5), new Supply(SupplySpot.SUPPLY6)};
      dirty = false;
      scanCounter = 0;
   }
}
