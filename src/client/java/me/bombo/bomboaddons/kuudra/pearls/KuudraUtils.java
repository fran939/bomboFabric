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
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

public class KuudraUtils {
   private static ArmorStand elleEntity;
   private static MagmaCube kuudraEntity;
   private static final List<MagmaCube> magmaCubes = new ArrayList();
   private static final List<Vec3> crates = new ArrayList();
   private static final List<Zombie> zombies = new ArrayList<>();
   private static final List<CrateHitbox> crateHitboxes = new ArrayList<>();
   private static Vec3 myBobberPos = null;
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
      String area = BomboaddonsClient.currentArea;
      if (area != null && (area.toLowerCase().contains("kuudra") || area.matches("(?i)^T[1-5]$"))) {
         cachedInKuudra = true;
         return true;
      }
      String simArea = SkyblockUtils.getSimulatedArea();
      if (simArea != null && simArea.toLowerCase().contains("kuudra")) {
         cachedInKuudra = true;
         return true;
      }

      long now = System.currentTimeMillis();
      if (now - lastKuudraCheckTime < 1000L) {
         return cachedInKuudra;
      }
      lastKuudraCheckTime = now;

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
         zombies.clear();
         crateHitboxes.clear();
         myBobberPos = null;
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
               zombies.clear();
               myBobberPos = null;
               dirty = true;

               for (Entity entity : mc.level.entitiesForRendering()) {
                  if (entity != null) {
                     processEntity(entity, mc);
                  }
               }

               buildCrateHitboxes(mc);
            }
         }
      }
   }

   private static void processEntity(Entity entity, Minecraft mc) {
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

         for (int idx = 0; idx < supplies.length; ++idx) {
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
      } else if (entity instanceof MagmaCube magmaCube) {
         magmaCubes.add(magmaCube);
         if (isKuudraEntity(magmaCube)) {
            if (kuudraEntity == null || magmaCube.getId() < kuudraEntity.getId()) {
               kuudraEntity = magmaCube;
            }
         }
      } else if (entity instanceof Giant && entity.getY() < 67.0) {
         float yawRad = (float)((double)(entity.getYRot() + 130.0F) * (Math.PI / 180D));
         double offsetX = 3.7 * Math.cos((double)yawRad);
         double offsetZ = 3.7 * Math.sin((double)yawRad);
         double x = entity.getX() + 0.5 + offsetX;
         double z = entity.getZ() + 0.5 + offsetZ;
         crates.add(new Vec3(x, 75.0, z));
      } else if (entity instanceof FishingHook hook) {
         if (mc.player != null && hook.getPlayerOwner() != null && hook.getPlayerOwner().getUUID().equals(mc.player.getUUID())) {
            myBobberPos = hook.position();
         }
      } else if (entity instanceof Zombie zombie) {
         if (zombie.isAlive()) {
            zombies.add(zombie);
         }
      }
   }

   private static void buildCrateHitboxes(Minecraft mc) {
      crateHitboxes.clear();
      if (crates.isEmpty()) return;

      int n = crates.size();
      List<List<AABB>> perCrate = new ArrayList<>(n);
      double[] minX = new double[n];
      double[] minY = new double[n];
      double[] minZ = new double[n];
      double[] maxX = new double[n];
      double[] maxY = new double[n];
      double[] maxZ = new double[n];
      boolean[] any = new boolean[n];
      double[] aimX = new double[n];
      double[] aimY = new double[n];
      double[] aimZ = new double[n];
      double[] aimDistSq = new double[n];

      for (int i = 0; i < n; i++) {
         perCrate.add(new ArrayList<>(8));
         minX[i] = minY[i] = minZ[i] = Double.POSITIVE_INFINITY;
         maxX[i] = maxY[i] = maxZ[i] = Double.NEGATIVE_INFINITY;
         aimDistSq[i] = Double.POSITIVE_INFINITY;
      }

      Vec3 eyePos = mc.player != null ? mc.player.getEyePosition() : null;

      for (Zombie zombie : zombies) {
         if (!zombie.isAlive()) continue;
         AABB box = zombie.getBoundingBox();
         double cx = (box.minX + box.maxX) * 0.5;
         double cy = (box.minY + box.maxY) * 0.5;
         double cz = (box.minZ + box.maxZ) * 0.5;
         int best = -1;
         double bestD2 = 9.0;

         for (int i = 0; i < n; i++) {
            Vec3 crate = crates.get(i);
            double dx = cx - crate.x;
            double dy = cy - crate.y;
            double dz = cz - crate.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= bestD2) {
               bestD2 = d2;
               best = i;
            }
         }

         if (best >= 0) {
            perCrate.get(best).add(box);
            if (eyePos != null) {
               double x = Math.max(box.minX, Math.min(eyePos.x, box.maxX));
               double y = Math.max(box.minY, Math.min(eyePos.y, box.maxY));
               double z = Math.max(box.minZ, Math.min(eyePos.z, box.maxZ));
               double dx = eyePos.x - x;
               double dy = eyePos.y - y;
               double dz = eyePos.z - z;
               double distSq = dx * dx + dy * dy + dz * dz;
               if (distSq <= 9.0 && distSq < aimDistSq[best]) {
                  aimDistSq[best] = distSq;
                  aimX[best] = x;
                  aimY[best] = y;
                  aimZ[best] = z;
               }
            }

            any[best] = true;
            minX[best] = Math.min(minX[best], box.minX);
            minY[best] = Math.min(minY[best], box.minY);
            minZ[best] = Math.min(minZ[best], box.minZ);
            maxX[best] = Math.max(maxX[best], box.maxX);
            maxY[best] = Math.max(maxY[best], box.maxY);
            maxZ[best] = Math.max(maxZ[best], box.maxZ);
         }
      }

      for (int i = 0; i < n; i++) {
         Vec3 crateCenter = crates.get(i);
         if (any[i]) {
            AABB union = new AABB(minX[i], minY[i], minZ[i], maxX[i], maxY[i], maxZ[i]);
            double centerX = (union.minX + union.maxX) * 0.5;
            double centerY = union.minY;
            double centerZ = (union.minZ + union.maxZ) * 0.5;
            float height = (float)(union.maxY - union.minY);
            double rx = (union.maxX - union.minX) * 0.5;
            double rz = (union.maxZ - union.minZ) * 0.5;
            float radius = (float)Math.sqrt(rx * rx + rz * rz) + 0.05F;
            Vec3 aimPoint = aimDistSq[i] != Double.POSITIVE_INFINITY ? new Vec3(aimX[i], aimY[i], aimZ[i]) : null;
            boolean draggable = myBobberPos != null && myBobberPos.distanceToSqr(crateCenter) <= 33.0625;
            crateHitboxes.add(new CrateHitbox(crateCenter, perCrate.get(i), union, new Vec3(centerX, centerY, centerZ), radius, height, aimPoint, draggable));
         } else {
            boolean draggable = myBobberPos != null && myBobberPos.distanceToSqr(crateCenter) <= 33.0625;
            crateHitboxes.add(new CrateHitbox(crateCenter, List.of(), null, crateCenter, 1.0F, 1.8F, null, draggable));
         }
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
               supplies[pos].setProgress((int)percent);
               supplies[pos].setProgressColor(getProgressColor(percent));
            } catch (NumberFormatException ignored) {}
         } else if (status == SupplyStatus.COMPLETED || status == SupplyStatus.RECEIVED) {
            supplies[pos].setProgress(100);
            supplies[pos].setProgressColor(new float[]{0.0F, 1.0F});
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
      float ratio = (float)Math.pow((double)(percent / 100.0F), 1.5);
      float red = 1.0F - ratio;
      return new float[]{red, ratio};
   }

   private static boolean isKuudraEntity(MagmaCube magmaCube) {
      if (magmaCube == null || magmaCube.getSize() != 30) return false;
      try {
         double maxHp = magmaCube.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
         if (Math.abs(maxHp - 100000.0) < 1.0) return true;
      } catch (Exception ignored) {}
      return Math.abs(magmaCube.getMaxHealth() - 100000.0F) < 1.0F;
   }

   private static void resetSupplies() {
      for (Supply supply : supplies) {
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

   public static List<CrateHitbox> getCrateHitboxes() {
      return crateHitboxes;
   }

   public static List<MagmaCube> getMagmaCubes() {
      return magmaCubes;
   }

   public static Supply[] getSupplies() {
      return supplies;
   }

   public static List<Vec3> getAllUncompletedSupplies() {
      List<Vec3> uncompleted = new ArrayList();

      for (Supply supply : supplies) {
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
