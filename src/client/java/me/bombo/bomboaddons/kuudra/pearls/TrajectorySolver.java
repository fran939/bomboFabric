package me.bombo.bomboaddons.kuudra.pearls;

import java.util.Comparator;
import net.minecraft.world.phys.Vec3;

public class TrajectorySolver {
   private static final double GRAVITY = 0.03;
   private static final double SPEED = (double)1.5F;
   private static final double DRAG = 0.99;
   private static final double TICK_MS = (double)50.0F;
   private static final int MAX_TICKS = 100;
   private static final int REFINE_STEPS = 10;
   private static final int REFINE_ITERATIONS = 5;
   private static final int GRID_STEPS = 30;
   private static final double HIT_RADIUS_SQ = (double)1.0F;
   private static final double TOLERANCE_SQ = 0.010000000000000002;
   private static final int SKY_DISTANCE = 30;
   private static final int FLAT_DISTANCE = 15;
   private static final double MIN_THETA = 0.01;
   private static final double MAX_THETA = 1.5607963267948965;
   private static final double INITIAL_REFINE_DEG = (double)1.0F;
   private static final double FLAT_ANGLE_BIAS_DEG = 0.4;
   private static final double MIN_SKY_THETA = Math.toRadians((double)34.0F);
   private static final double MAX_FLAT_THETA = Math.toRadians((double)32.0F);

   public static PearlSolution solvePearl(boolean sky, Vec3 start, Vec3 target) {
      double dx = target.x - start.x;
      double dz = target.z - start.z;
      double horizontalDist = Math.hypot(dx, dz);
      if (horizontalDist < (double)1.0F) {
         return !sky ? null : new PearlSolution(new Vec3(start.x, start.y + (double)30.0F, start.z), 4500L, 0.0F, -90.0F);
      } else {
         double invLength = (double)1.0F / horizontalDist;
         double ux = dx * invLength;
         double uz = dz * invLength;
         SearchResult bestResult = searchInitialBestAngle(start, target, ux, uz, sky);
         if (bestResult == null) {
            return null;
         } else {
            double refineRange = Math.toRadians((double)1.0F);

            for(int iter = 0; iter < 5; ++iter) {
               double lower = Math.max(0.01, bestResult.theta - refineRange);
               double upper = Math.min(1.5607963267948965, bestResult.theta + refineRange);
               SearchResult refined = searchRefinedBestAngle(lower, upper, start, target, ux, uz, sky);
               if (refined == null || refined.errorSq >= bestResult.errorSq) {
                  break;
               }

               bestResult = refined;
               if (refined.errorSq < 0.010000000000000002) {
                  break;
               }

               refineRange *= (double)0.5F;
            }

            double finalTheta = bestResult.theta;
            int finalTick = bestResult.tick;
            if (!sky) {
               finalTheta = Math.min(MAX_FLAT_THETA, Math.max(0.01, finalTheta + Math.toRadians(0.4)));
            }

            Vec3 velocity = computeVelocity(finalTheta, ux, uz);
            if (!sky) {
               SimResult finalSim = simulateTrajectory(start, velocity, target);
               if (finalSim.hit && finalSim.hitTick >= 0) {
                  finalTick = finalSim.hitTick;
               }
            }

            Vec3 aimPoint = computeAimPoint(start, velocity, sky ? (double)30.0F : (double)15.0F);
            long flightTimeMs = Math.round((double)finalTick * (double)50.0F);
            double flatSpeed = Math.hypot(velocity.x, velocity.z);
            float yaw = (float)(Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - (double)90.0F);
            float pitch = (float)(-Math.toDegrees(Math.atan2(velocity.y, flatSpeed)));
            return new PearlSolution(aimPoint, flightTimeMs, yaw, pitch);
         }
      }
   }

   private static SearchResult searchRefinedBestAngle(double minTheta, double maxTheta, Vec3 start, Vec3 target, double ux, double uz, boolean sky) {
      Comparator<SearchResult> comparator = getResultComparator(sky);
      SearchResult best = null;
      double clampedMin = Math.max(minTheta, sky ? MIN_SKY_THETA : 0.01);
      double clampedMax = Math.min(maxTheta, sky ? 1.5607963267948965 : MAX_FLAT_THETA);

      for(int i = 0; i <= 10; ++i) {
         double theta = clampedMin + (clampedMax - clampedMin) * (double)i / (double)10.0F;
         Vec3 velocity = computeVelocity(theta, ux, uz);
         SimResult sim = simulateTrajectory(start, velocity, target);
         if (sim.hit) {
            SearchResult current = new SearchResult(theta, sim.errorSq, sim.hitTick);
            if (best == null || comparator.compare(current, best) < 0) {
               best = current;
            }
         }
      }

      return best;
   }

   private static SearchResult searchInitialBestAngle(Vec3 start, Vec3 target, double ux, double uz, boolean sky) {
      Comparator<SearchResult> comparator = getResultComparator(sky);
      double minTheta = sky ? MIN_SKY_THETA : 0.01;
      double maxTheta = sky ? 1.5607963267948965 : MAX_FLAT_THETA;
      SearchResult best = null;

      for(int i = 0; i <= 30; ++i) {
         double theta = minTheta + (maxTheta - minTheta) * (double)i / (double)30.0F;
         Vec3 velocity = computeVelocity(theta, ux, uz);
         SimResult sim = simulateTrajectory(start, velocity, target);
         if (sim.hit) {
            SearchResult candidate = new SearchResult(theta, sim.errorSq, sim.hitTick);
            if (best == null || comparator.compare(candidate, best) < 0) {
               best = candidate;
            }
         }
      }

      return best;
   }

   private static Comparator<SearchResult> getResultComparator(boolean sky) {
      return Comparator.comparingDouble((SearchResult r) -> r.errorSq).thenComparingDouble((SearchResult r) -> sky ? -r.theta : r.theta);
   }

   private static SimResult simulateTrajectory(Vec3 start, Vec3 initialVelocity, Vec3 target) {
      double x = start.x;
      double y = start.y;
      double z = start.z;
      double vx = initialVelocity.x;
      double vy = initialVelocity.y;
      double vz = initialVelocity.z;
      double sx = start.x;
      double sy = start.y;
      double sz = start.z;
      double tx = target.x;
      double ty = target.y;
      double tz = target.z;
      double dx = tx - x;
      double dy = ty - y;
      double dz = tz - z;
      double bestErrorSq = dx * dx + dy * dy + dz * dz;
      int bestTick = -1;
      double maxDistanceSq = bestErrorSq * (double)4.0F;
      double minY = ty - (double)5.0F;

      for(int tick = 0; tick < 100; ++tick) {
         double lastX = x;
         double lastY = y;
         double lastZ = z;
         x += vx;
         y += vy;
         z += vz;
         double abx = x - lastX;
         double aby = y - lastY;
         double abz = z - lastZ;
         double apx = tx - lastX;
         double apy = ty - lastY;
         double apz = tz - lastZ;
         double abLenSq = abx * abx + aby * aby + abz * abz;
         double t = (double)0.0F;
         if (abLenSq > 1.0E-4) {
            t = (apx * abx + apy * aby + apz * abz) / abLenSq;
            if (t < (double)0.0F) {
               t = (double)0.0F;
            } else if (t > (double)1.0F) {
               t = (double)1.0F;
            }
         }

         double closestX = lastX + t * abx;
         double closestY = lastY + t * aby;
         double closestZ = lastZ + t * abz;
         dx = tx - closestX;
         dy = ty - closestY;
         double loopDz = tz - closestZ;
         double errorSq = dx * dx + dy * dy + loopDz * loopDz;
         if (errorSq < bestErrorSq) {
            bestErrorSq = errorSq;
            bestTick = tick;
         }

         if (errorSq < (double)1.0F) {
            return new SimResult(true, errorSq, tick);
         }

         double sxDiff = x - sx;
         double syDiff = y - sy;
         double szDiff = z - sz;
         double distSqFromStart = sxDiff * sxDiff + syDiff * syDiff + szDiff * szDiff;
         if (y < minY || distSqFromStart > maxDistanceSq) {
            break;
         }

         vx *= 0.99;
         vy = (vy - 0.03) * 0.99;
         vz *= 0.99;
      }

      return new SimResult(false, bestErrorSq, bestTick);
   }

   private static Vec3 computeVelocity(double theta, double ux, double uz) {
      double cos = Math.cos(theta);
      double sin = Math.sin(theta);
      return new Vec3((double)1.5F * cos * ux, (double)1.5F * sin, (double)1.5F * cos * uz);
   }

   private static Vec3 computeAimPoint(Vec3 start, Vec3 velocity, double distance) {
      double invNorm = (double)1.0F / velocity.length();
      return new Vec3(start.x + velocity.x * invNorm * distance, start.y + velocity.y * invNorm * distance, start.z + velocity.z * invNorm * distance);
   }

   private static class SimResult {
      final boolean hit;
      final double errorSq;
      final int hitTick;

      SimResult(boolean hit, double errorSq, int hitTick) {
         this.hit = hit;
         this.errorSq = errorSq;
         this.hitTick = hitTick;
      }
   }

   private static class SearchResult {
      final double theta;
      final double errorSq;
      final int tick;

      SearchResult(double theta, double errorSq, int tick) {
         this.theta = theta;
         this.errorSq = errorSq;
         this.tick = tick;
      }
   }
}
