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
   private static final Pattern progressPattern = Pattern.compile("\\[(.*)]\\s*(\\d+)%");
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

      for (PearlRenderData data : pearlSolutions) {
         boolean isMySupply = mySupply != null && vecEquals(mySupply, data.target);
         boolean isDouble = data.isDouble;
         boolean isReady = (data.time <= 0L || hasSupplies) && (isMySupply || isDouble);
         float r = isReady ? 0.0F : 1.0F;
         float g = isReady ? 1.0F : 0.0F;
         float b = 0.0F;
         float a = 1.0F;
         if (!isMySupply && !isDouble) {
            boolean isOtherReady = data.time <= 0L || hasSupplies;
            r = isOtherReady ? 0.0F : 1.0F;
            g = isOtherReady ? 1.0F : 0.0F;
         }

         Vec3 pos = data.solution;
         float waypointSize = data.isSky ? waypointSizeSky : waypointSizeFlat;
         double hs = (double) waypointSize / 2.0;
         double relX = pos.x - camPos.x;
         double relY = pos.y - camPos.y;
         double relZ = pos.z - camPos.z;
         float dist = (float) Math.sqrt(relX * relX + relY * relY + relZ * relZ);
         float scale = dist > 0.2F ? 0.2F / dist : 1.0F;

         double scaledX = relX * (double) scale;
         double scaledY = relY * (double) scale;
         double scaledZ = relZ * (double) scale;
         double scaledHs = hs * (double) scale;
         AABB box = new AABB(scaledX - scaledHs, scaledY - scaledHs, scaledZ - scaledHs, scaledX + scaledHs, scaledY + scaledHs, scaledZ + scaledHs);
         final AABB finalBox = box;
         final float finalR = r;
         final float finalG = g;
         collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, finalBox, finalR, finalG, b, a, 1.5F));

         if (s.showTimer || s.kuudraPearlTimer) {
            int textColor = isReady ? 0xFF00FF00 : 0xFFFF0000;
            Vector4f screenPos = new Vector4f((float) pos.x, (float) (pos.y + (double) (data.isSky ? 1.5F : 1.0F)), (float) pos.z, 1.0F);
            screenPos.mul(poseStack.last().pose());
            screenPos.mul(context.levelState().cameraRenderState.projectionMatrix);
            if (screenPos.w > 0.0F) {
               float ndcX = screenPos.x / screenPos.w;
               float ndcY = screenPos.y / screenPos.w;
               int screenWidth = mc.getWindow().getGuiScaledWidth();
               int screenHeight = mc.getWindow().getGuiScaledHeight();
               float screenX = (ndcX + 1.0F) * 0.5F * (float) screenWidth;
               float screenY = (1.0F - ndcY) * 0.5F * (float) screenHeight;
               HUD_TEXTS.add(new PearlHUDText(data.cachedDisplay, screenX, screenY, textColor));
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
            int percent = Integer.parseInt(matcher.group(2));
            long now = System.currentTimeMillis();
            lastTitleEvent = now;
            if (percent >= 0 && percent < 100) {
               trackingPickup = true;
               progressStartTime = now - (long) percent * 30L;
            } else if (percent == 100) {
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

      Vec3 fallback = noPreIsNothing ? noPre.getLocation() : vector;
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
         return supply != null && supply.getStatus() == SupplyStatus.NOTHING ? supply.getSpot().getLocation() : fallback;
      }
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
