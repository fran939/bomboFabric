package me.bombo.bomboaddons.kuudra.pearls;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.OrderedSubmitNodeCollector;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

public class Pearls {
   private static final int[][] PEARL_DELAY = new int[][]{{0, 3000, 4000, 5000, 6000, 6000}, {0, 2750, 3750, 4500, 5500, 5500}, {0, 2500, 3250, 4000, 5000, 5000}, {0, 2250, 3000, 3500, 4250, 4250}};
   public static int cachedInitialDelay = 100;
   public static int cachedDoubleDelay = 200;
   public static float textSizeSky = 1.5F;
   public static float textSizeFlat = 1.125F;
   public static float waypointSizeSky = 0.5F;
   public static float waypointSizeFlat = 0.375F;
   public static float timerOffset = 1.0F;
   private static final List<PearlRenderData> pearlSolutions = new ArrayList<>();
   private static final List<Vec3> currentSupplies = new ArrayList<>();
   private static final Pattern progressPattern = Pattern.compile("\\[[|\\s:]+\\]\\s*(\\d+)%");
   private static long progressStartTime = -1L;
   private static boolean trackingPickup = false;
   private static Vec3 mySupply = null;
   private static Vec3 myDouble = null;
   private static long lastTitleEvent = -1L;
   private static int tickCounter = 0;
   public static final List<PearlHUDText> HUD_TEXTS = new ArrayList<>();

   public static void onWorldUnload() {
      currentSupplies.clear();
      pearlSolutions.clear();
      HUD_TEXTS.clear();
      mySupply = null;
      myDouble = null;
      resetTracking();
   }

   public static void render(LevelRenderContext context) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || (!s.pearlCalculator && !s.kuudraDynamicPearls)) return;

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) return;
      if (!KuudraUtils.inKuudra()) return;

      HUD_TEXTS.clear();
      Vec3 camPos = mc.gameRenderer.mainCamera().position();
      PoseStack poseStack = context.poseStack();
      OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());

      boolean hasSupplies = isHoldingSupplies();

      float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
      Vec3 eyePos = mc.player.getEyePosition(pt);

      org.joml.Matrix4f modelView = new org.joml.Matrix4f(poseStack.last().pose());
      org.joml.Matrix4f projection = new org.joml.Matrix4f(context.levelState().cameraRenderState.projectionMatrix);

      for (PearlRenderData data : pearlSolutions) {
         boolean isMySupply = mySupply != null && vecEquals(mySupply, data.target);
         boolean isDouble = data.isDouble;
         boolean isReady = (data.time <= 0L || hasSupplies) && (isMySupply || isDouble);
         float r = (isMySupply || isDouble) ? 0.0F : 1.0F;
         float g = (isMySupply || isDouble) ? 1.0F : 0.0F;
         float b = 0.0F;
         float a = 1.0F;

         double dist = data.isSky ? 25.0 : 12.0;
         Vec3 dir = VecUtil.directionFromYawPitch(data.yaw, data.pitch);
         double relX, relY, relZ;
         if (mc.options.getCameraType().isFirstPerson()) {
            relX = dir.x * dist;
            relY = dir.y * dist;
            relZ = dir.z * dist;
         } else {
            Vec3 point = eyePos.add(dir.scale(dist));
            relX = point.x - camPos.x;
            relY = point.y - camPos.y;
            relZ = point.z - camPos.z;
         }
         float hs = 0.50F; // Full block size (1.0 x 1.0 x 1.0)
         AABB box = new AABB(relX - hs, relY - hs, relZ - hs, relX + hs, relY + hs, relZ + hs);
         final AABB finalBox = box;
         final float finalR = r;
         final float finalG = g;
         collector.submitCustomGeometry(poseStack, RenderTypes.secondaryBlockOutline(), (pose, vertexConsumer) -> {
            BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, finalBox, finalR, finalG, b, a, 1.5F);
         });
         collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, vc) -> {
            BomboRenderUtils.drawFilledBox(pose.pose(), vc, (float)finalBox.minX, (float)finalBox.minY, (float)finalBox.minZ, (float)finalBox.maxX, (float)finalBox.maxY, (float)finalBox.maxZ, finalR, finalG, b, 0.20F);
         });

         String disp = data.cachedDisplay != null ? data.cachedDisplay : (isReady ? "§a§lTHROW" : "§c" + data.time + "ms");
         int textColor = isReady ? 0xFF00FF00 : 0xFFFF5555;

         // Also render full block highlight and countdown timer directly on top of target destination block
         if (data.target != null) {
            double tx = data.target.x - camPos.x;
            double ty = data.target.y - camPos.y;
            double tz = data.target.z - camPos.z;

            AABB targetBox = new AABB(tx - 0.5, ty, tz - 0.5, tx + 0.5, ty + 1.0, tz + 0.5);
            collector.submitCustomGeometry(poseStack, RenderTypes.secondaryBlockOutline(), (pose, vc) -> {
               BomboRenderUtils.drawBox(pose.pose(), vc, targetBox, finalR, finalG, b, a, 1.5F);
            });
            collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, vc) -> {
               BomboRenderUtils.drawFilledBox(pose.pose(), vc, (float)targetBox.minX, (float)targetBox.minY, (float)targetBox.minZ, (float)targetBox.maxX, (float)targetBox.maxY, (float)targetBox.maxZ, finalR, finalG, b, 0.20F);
            });

            if (s.showTimer || s.kuudraPearlTimer) {
               float targetTextY = (float) (ty + 1.25);
               BomboRenderUtils.drawText(poseStack, collector, disp, (float) tx, targetTextY, (float) tz, textColor, 0.035F, true, true);
            }
         }

         if (s.showTimer || s.kuudraPearlTimer) {
            float textY = (float) (relY + hs + 0.15F);
            BomboRenderUtils.drawText(poseStack, collector, disp, (float) relX, textY, (float) relZ, textColor, 0.035F, true, true);

            Vector4f clip = new Vector4f((float) relX, textY, (float) relZ, 1.0F);
            modelView.transform(clip);
            projection.transform(clip);
            if (clip.w > 0.05F) {
               float ndcX = clip.x / clip.w;
               float ndcY = clip.y / clip.w;
               if (ndcX >= -1.2F && ndcX <= 1.2F && ndcY >= -1.2F && ndcY <= 1.2F) {
                  int screenWidth = mc.getWindow().getGuiScaledWidth();
                  int screenHeight = mc.getWindow().getGuiScaledHeight();
                  float screenX = (ndcX + 1.0F) * 0.5F * (float) screenWidth;
                  float screenY = (1.0F - ndcY) * 0.5F * (float) screenHeight;
                  HUD_TEXTS.add(new PearlHUDText(disp, screenX, screenY, textColor));
               }
            }
         }
      }

      // Render Supply drop spots (all 6 spots with KIC cyan color + textured beacon beam through walls)
      if (s.kuudraSupplyWaypoints) {
         Supply[] allSupplies = KuudraUtils.getSupplies();
         if (allSupplies != null) {
            for (Supply supply : allSupplies) {
               if (supply == null || supply.getStatus() == SupplyStatus.COMPLETED) continue;
               Vec3 loc = supply.getSpot().getLocation();
               double rx = loc.x - camPos.x;
               double ry = loc.y - camPos.y;
               double rz = loc.z - camPos.z;

               float sr = 0.16F;
               float sg = 0.98F;
               float sb = 0.99F;
               if (supply.getStatus() == SupplyStatus.INPROGRESS) {
                  float[] pc = supply.getProgressColor();
                  if (pc != null && pc.length > 0) sr = pc[0];
                  if (pc != null && pc.length > 1) sg = pc[1];
                  sb = 0.0F;
               }

               final float bR = sr;
               final float bG = sg;
               final float bB = sb;

               // Real textured beacon beam through walls
               int dropSpotColor = 0xFF000000 | ((int)(sr * 255) << 16) | ((int)(sg * 255) << 8) | (int)(sb * 255);
               BomboRenderUtils.drawBeaconBeam(poseStack, collector, rx, ry, rz, dropSpotColor, mc.level.getGameTime(), true);

               // Wireframe half-box highlight through walls
               AABB dropBox = new AABB(rx - 0.5, ry, rz - 0.5, rx + 0.5, ry + 0.5, rz + 0.5);
               collector.submitCustomGeometry(poseStack, RenderTypes.secondaryBlockOutline(), (pose, vc) ->
                  BomboRenderUtils.drawBox(pose.pose(), vc, dropBox, bR, bG, bB, 1.0F, 1.5F));

               // Filled half-box through walls
               collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, vc) ->
                  BomboRenderUtils.drawFilledBox(pose.pose(), vc, (float)dropBox.minX, (float)dropBox.minY, (float)dropBox.minZ, (float)dropBox.maxX, (float)dropBox.maxY, (float)dropBox.maxZ, bR, bG, bB, 0.18F));

               // Progress text above drop spot (only if in progress)
               if (supply.getStatus() == SupplyStatus.INPROGRESS && supply.getProgress() > 0) {
                  int percent = supply.getProgress();
                  String label = "§e" + percent + "%";
                  int textColor = 0xFFFFAA00;
                  BomboRenderUtils.drawText(poseStack, collector, label, (float) rx, (float) (ry + 0.8), (float) rz, textColor, 0.035F, true, true);
               }
            }
         }
      }

      // Render Supply crates with individual zombie hitboxes, 5.75m drag circle, and aim point (all through walls)
      if (s.kuudraSupplyWaypoints || s.kuudraSupplyHitbox || s.kuudraDragHitbox || s.kuudraSupplyBestAim) {
         List<CrateHitbox> crateHitboxes = KuudraUtils.getCrateHitboxes();

         for (CrateHitbox hitbox : crateHitboxes) {
            Vec3 crate = hitbox.crateCenter();
            double rx = crate.x - camPos.x;
            double ry = crate.y - camPos.y;
            double rz = crate.z - camPos.z;

            // Beacon beam to sky for crate through walls
            if (s.kuudraSupplyWaypoints) {
               BomboRenderUtils.drawBeaconBeam(poseStack, collector, rx, ry, rz, 0xFF28F9FE, mc.level.getGameTime(), true);
            }

            // Individual zombie hitboxes (wireframe, strictly around actual zombies, through walls)
            if (s.kuudraSupplyHitbox && !hitbox.zombies().isEmpty()) {
               for (AABB zBox : hitbox.zombies()) {
                  boolean inReach = eyePos != null && eyePos.distanceToSqr(
                     Math.max(zBox.minX, Math.min(eyePos.x, zBox.maxX)),
                     Math.max(zBox.minY, Math.min(eyePos.y, zBox.maxY)),
                     Math.max(zBox.minZ, Math.min(eyePos.z, zBox.maxZ))
                  ) <= 9.0;
                  float zr = (inReach && s.kuudraSupplyPickupRange) ? 0.0F : 0.16F;
                  float zg = (inReach && s.kuudraSupplyPickupRange) ? 1.0F : 0.98F;
                  float zb = (inReach && s.kuudraSupplyPickupRange) ? 0.0F : 0.99F;
                  AABB relZBox = new AABB(zBox.minX - camPos.x, zBox.minY - camPos.y, zBox.minZ - camPos.z,
                                          zBox.maxX - camPos.x, zBox.maxY - camPos.y, zBox.maxZ - camPos.z);
                  collector.submitCustomGeometry(poseStack, RenderTypes.secondaryBlockOutline(), (pose, vc) ->
                     BomboRenderUtils.drawBox(pose.pose(), vc, relZBox, zr, zg, zb, 1.0F, 1.5F));
               }
            }

            // Drag hitbox circle: Green if hooked/draggable (bobber <= 5.75m), Red/Pink if not, through walls
            if (s.kuudraDragHitbox) {
               float dr = hitbox.draggable() ? 0.0F : 0.98F;
               float dg = hitbox.draggable() ? 1.0F : 0.20F;
               float db = hitbox.draggable() ? 0.0F : 0.70F;
               collector.submitCustomGeometry(poseStack, RenderTypes.secondaryBlockOutline(), (pose, vc) ->
                  BomboRenderUtils.drawCircle(poseStack, vc, rx, ry + 0.05, rz, 5.75F, 32, dr, dg, db, 0.8F, 2.0F));
            }

            // Best aim point: Green small box at optimal click point through walls
            if (s.kuudraSupplyBestAim && hitbox.aimPoint() != null) {
               Vec3 ap = hitbox.aimPoint();
               double ax = ap.x - camPos.x;
               double ay = ap.y - camPos.y;
               double az = ap.z - camPos.z;
               float hs = 0.1F;
               AABB aimBox = new AABB(ax - hs, ay - hs, az - hs, ax + hs, ay + hs, az + hs);
               collector.submitCustomGeometry(poseStack, RenderTypes.secondaryBlockOutline(), (pose, vc) ->
                  BomboRenderUtils.drawBox(pose.pose(), vc, aimBox, 0.0F, 1.0F, 0.0F, 1.0F, 1.5F));
            }
         }
      }
   }

   public static void onClientTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) return;

      if (!KuudraUtils.inKuudra()) {
         if (!pearlSolutions.isEmpty() || !currentSupplies.isEmpty() || !HUD_TEXTS.isEmpty()) {
            onWorldUnload();
         }
         return;
      }

      if (lastTitleEvent != -1L && System.currentTimeMillis() - lastTitleEvent > 750L) {
         resetTracking();
      }

      ++tickCounter;
      if (tickCounter % 2 != 0) {
         boolean hasSupplies = isHoldingSupplies();
         for (PearlRenderData data : pearlSolutions) {
            data.updateDisplay(hasSupplies, progressStartTime, trackingPickup);
         }
      } else {
         currentSupplies.clear();
         currentSupplies.addAll(KuudraUtils.getAllUncompletedSupplies());
         BomboConfig.Settings s = BomboConfig.get();
         if (s != null && (s.pearlCalculator || s.kuudraDynamicPearls)) {
            Vec3 eyePos = mc.player.getEyePosition();
            if (eyePos != null) {
               PickupSpot closestSpot = PickupSpot.getClosestSpot(eyePos);
               mySupply = getMyDropSpot(eyePos, closestSpot);
               pearlSolutions.clear();
               boolean hasSupplies = isHoldingSupplies();
               boolean showSky = s.showSkyPearls || s.kuudraPearlShowSky;
               boolean showFlat = s.showFlatPearls || s.kuudraPearlShowFlat;
               boolean showDouble = s.showDoublePearls || s.kuudraPearlShowDouble;

               if (s.showAll) {
                  for (Vec3 targetSupply : currentSupplies) {
                     if (showSky) {
                        PearlSolution sky = TrajectorySolver.solvePearl(true, eyePos, targetSupply);
                        tryAddPearl(sky, targetSupply, false, true, hasSupplies);
                     }
                     if (showFlat) {
                        PearlSolution flat = TrajectorySolver.solvePearl(false, eyePos, targetSupply);
                        tryAddPearl(flat, targetSupply, false, false, hasSupplies);
                     }
                  }
               } else if (mySupply != null) {
                  if (showSky) {
                     PearlSolution sky = TrajectorySolver.solvePearl(true, eyePos, mySupply);
                     tryAddPearl(sky, mySupply, false, true, hasSupplies);
                  }
                  if (showFlat) {
                     PearlSolution flat = TrajectorySolver.solvePearl(false, eyePos, mySupply);
                     tryAddPearl(flat, mySupply, false, false, hasSupplies);
                  }
               }

               myDouble = null;
               if (showDouble && closestSpot != PickupSpot.NONE) {
                  for (DoublePearl pearlSpot : DoublePearlRegistry.getRoutesFrom(closestSpot)) {
                     Vec3 targetSpot = pearlSpot.getLocation();
                     if (myDouble == null) {
                        myDouble = targetSpot;
                     }
                     PearlSolution doubleSol = TrajectorySolver.solvePearl(true, eyePos, targetSpot);
                     tryAddPearl(doubleSol, targetSpot, true, true, hasSupplies);
                  }
               }
            }
         }
      }
   }

   private static void tryAddPearl(PearlSolution sol, Vec3 target, boolean isDouble, boolean isSky, boolean hasSupplies) {
      if (sol != null && sol.solution != null) {
         PearlRenderData data = new PearlRenderData(sol, target, isDouble, isSky, hasSupplies, progressStartTime, trackingPickup);
         pearlSolutions.add(data);
      }
   }

   public static void onTitleReceived(String raw) {
      if (raw == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || (!s.pearlCalculator && !s.kuudraDynamicPearls)) return;

      String clean = raw.replaceAll("(?i)§.", "");
      Matcher matcher = progressPattern.matcher(clean);
      if (matcher.find()) {
         try {
            int percent = Integer.parseInt(matcher.group(1));
            long now = System.currentTimeMillis();
            lastTitleEvent = now;
            if (percent == 0) {
               trackingPickup = true;
               progressStartTime = now;
            } else if (percent > 0 && percent < 100) {
               trackingPickup = true;
               if (progressStartTime == -1L) {
                  progressStartTime = now - (long) percent * 30L;
               }
            } else if (percent >= 100) {
               resetTracking();
            }
         } catch (NumberFormatException ignored) {}
      }
   }

   public static void onChatMessage(String message) {
      if (message == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || (!s.pearlCalculator && !s.kuudraDynamicPearls)) return;

      if (message.contains("moved and the Chest slipped out") || message.contains("You were killed by Kuudra") || message.contains("retrieved some of Elle's supplies")) {
         resetTracking();
      }
   }

   private static void resetTracking() {
      trackingPickup = false;
      progressStartTime = -1L;
      lastTitleEvent = -1L;
   }

   private static Vec3 getMyDropSpot(Vec3 vector, PickupSpot pickUpSpot) {
      Supply[] supplies = KuudraUtils.getSupplies();
      SupplySpot noPre = NoPre.getNoPreSpot();
      boolean noPreIsNothing = false;
      if (noPre != null) {
         Supply noPreSupply = getSupply(supplies, noPre.ordinal());
         noPreIsNothing = noPreSupply != null && noPreSupply.getStatus() == SupplyStatus.NOTHING;
      }

      Vec3 fallback = noPreIsNothing ? noPre.getLocation() : new Vec3(-101.5, 79.0, -106.0);
      if (pickUpSpot == PickupSpot.SQUARE && noPreIsNothing) {
         return noPre.getLocation();
      } else {
         Supply supply = null;
         switch (pickUpSpot) {
            case SHOP -> supply = getSupply(supplies, 0);
            case X -> supply = getSupply(supplies, 1);
            case X_CANNON -> supply = getSupply(supplies, 2);
            case EQUALS -> supply = getSupply(supplies, 3);
            case SLASH -> supply = getSupply(supplies, 4);
            case TRIANGLE -> supply = getSupply(supplies, 5);
            default -> {}
         }
         return supply != null && supply.getStatus() == SupplyStatus.NOTHING ? supply.getSpot().getLocation() : getClosestSupply(vector).orElse(fallback);
      }
   }

   private static Optional<Vec3> getClosestSupply(Vec3 vector) {
      double minDistSq = Double.MAX_VALUE;
      Vec3 closest = null;
      Supply[] supplies = KuudraUtils.getSupplies();
      if (supplies != null) {
         for (Supply supply : supplies) {
            if (supply != null && supply.getStatus() == SupplyStatus.NOTHING) {
               Vec3 loc = supply.getSpot().getLocation();
               double distSq = vector.distanceToSqr(loc);
               if (distSq < minDistSq) {
                  minDistSq = distSq;
                  closest = loc;
               }
            }
         }
      }
      return Optional.ofNullable(closest);
   }

   private static Supply getSupply(Supply[] supplies, int index) {
      return supplies != null && index >= 0 && index < supplies.length ? supplies[index] : null;
   }

   public static Optional<DoublePearl> getClosestDoublePearl(Vec3 eyePos, List<DoublePearl> doublePearls) {
      if (doublePearls.isEmpty()) return Optional.empty();
      DoublePearl closest = null;
      double closestDistance = Double.MAX_VALUE;
      for (DoublePearl dp : doublePearls) {
         double distance = eyePos.distanceToSqr(dp.getLocation());
         if (distance < closestDistance) {
            closestDistance = distance;
            closest = dp;
         }
      }
      return Optional.ofNullable(closest);
   }

   public static boolean isHoldingSupplies() {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.player == null) return false;

      // In Hypixel Kuudra, supplies or fuel cells are placed in slot 8
      ItemStack slot8 = mc.player.getInventory().getItem(8);
      if (!slot8.isEmpty()) {
         String id = SkyblockUtils.getSkyblockId(slot8);
         if ("ELLE_SUPPLIES".equalsIgnoreCase(id) || "ELLE_FUEL_CELL".equalsIgnoreCase(id)) return true;
         String name = slot8.getHoverName().getString();
         if (name.contains("Supply") || name.contains("Supplies") || name.contains("Fuel Cell") || name.contains("Chest") || name.contains("Crate")) return true;
      }

      // Check held item as fallback
      ItemStack held = mc.player.getMainHandItem();
      if (!held.isEmpty()) {
         String id = SkyblockUtils.getSkyblockId(held);
         if ("ELLE_SUPPLIES".equalsIgnoreCase(id) || "ELLE_FUEL_CELL".equalsIgnoreCase(id)) return true;
         String name = held.getHoverName().getString();
         if (name.contains("Supply") || name.contains("Supplies") || name.contains("Fuel Cell") || name.contains("Chest") || name.contains("Crate")) return true;
      }

      return false;
   }

   public static int getPearlDelay(int talismanTier, int kuudraTier) {
      if (talismanTier < 0) talismanTier = 0;
      if (talismanTier > 3) talismanTier = 3;
      if (kuudraTier < 1) kuudraTier = 1;
      if (kuudraTier > 5) kuudraTier = 5;
      return PEARL_DELAY[talismanTier][kuudraTier];
   }

   private static boolean vecEquals(Vec3 a, Vec3 b) {
      if (a != null && b != null) {
         return Math.abs(a.x - b.x) < 0.1 && Math.abs(a.y - b.y) < 0.1 && Math.abs(a.z - b.z) < 0.1;
      }
      return false;
   }

   public static class PearlHUDText {
      public String text;
      public float x;
      public float y;
      public int color;

      public PearlHUDText(String text, float x, float y, int color) {
         this.text = text;
         this.x = x;
         this.y = y;
         this.color = color;
      }
   }
}
