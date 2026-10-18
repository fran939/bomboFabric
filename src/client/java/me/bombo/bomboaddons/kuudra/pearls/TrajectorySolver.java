package me.bombo.bomboaddons.kuudra.pearls;

import net.minecraft.world.phys.Vec3;

public class TrajectorySolver {

   public static PearlSolution solvePearl(boolean sky, Vec3 start, Vec3 target) {
      return solvePearl(sky, start, target, 0.0F);
   }

   public static PearlSolution solvePearl(boolean sky, Vec3 start, Vec3 target, float offset) {
      if (start == null || target == null) return null;
      double dx = target.x - start.x;
      double dy = (target.y + (double) offset) - start.y;
      double dz = target.z - start.z;
      double horiz = Math.hypot(dx, dz);
      if (horiz < 1.0E-12) {
         return null;
      }

      double angle = Math.atan2(-dx, dz);
      float yaw = wrapDegrees((float) Math.toDegrees(angle));

      int tick = sky ? solveHighestAngleTick(horiz, dy) : solveBestIntegerTick(horiz, dy);
      if (tick <= 0) {
         return null;
      }

      double hFactor = horizontalFactor(tick);
      double gCorrection = gravityCorrection(tick);
      double cosTheta = clamp(horiz / (1.5 * hFactor), -1.0, 1.0);
      double sinTheta = clamp(-(dy + gCorrection) / (1.5 * hFactor), -1.0, 1.0);
      double pitchRad = Math.atan2(sinTheta, cosTheta);
      float pitch = (float) Math.toDegrees(pitchRad);

      double distance = sky ? 30.0 : 15.0;
      Vec3 aimPoint = VecUtil.pointFromYawPitch(start, yaw, pitch, distance);
      long flightTimeMs = (long) tick * 50L;
      return new PearlSolution(aimPoint, flightTimeMs, yaw, pitch);
   }

   private static int solveBestIntegerTick(double horiz, double dy) {
      double tSeed = Math.max(1.0, seedTicksFromHorizontal(horiz));
      double t = tSeed;
      for (int iter = 0; iter < 20; ++iter) {
         double f = flightEquation(t, horiz, dy);
         double fPrime = flightEquationDerivative(t, dy);
         if (Math.abs(fPrime) < 1.0E-12) break;
         double nextT = t - f / fPrime;
         if (!Double.isFinite(nextT)) break;
         if (nextT < 1.0) nextT = 1.0;
         if (Math.abs(nextT - t) < 1.0E-10) {
            t = nextT;
            break;
         }
         t = nextT;
      }

      int centerTick = Math.max(1, (int) Math.round(t));
      int bestTick = -1;
      double minError = Double.POSITIVE_INFINITY;
      int minT = Math.max(1, centerTick - 4);
      int maxT = centerTick + 4;

      for (int candidate = minT; candidate <= maxT; ++candidate) {
         double hFactor = horizontalFactor(candidate);
         if (hFactor <= 0.0) continue;
         double cosCandidate = horiz / (1.5 * hFactor);
         if (Math.abs(cosCandidate) > 1.000000001) continue;
         double sinCandidate = -(dy + gravityCorrection(candidate)) / (1.5 * hFactor);
         if (Math.abs(sinCandidate) > 1.000000001) continue;
         double error = Math.abs(flightEquation(candidate, horiz, dy));
         if (error < minError) {
            minError = error;
            bestTick = candidate;
         }
      }
      return bestTick;
   }

   private static int solveHighestAngleTick(double horiz, double dy) {
      int bestTick = -1;
      double minError = Double.POSITIVE_INFINITY;
      for (int tick = 1; tick <= 125; ++tick) {
         double hFactor = horizontalFactor(tick);
         if (hFactor <= 1.0E-12) continue;
         double cosCandidate = horiz / (1.5 * hFactor);
         double sinCandidate = -(dy + gravityCorrection(tick)) / (1.5 * hFactor);
         if (Math.abs(cosCandidate) > 1.05 || Math.abs(sinCandidate) > 1.05) continue;
         double cClamp = clamp(cosCandidate, -1.0, 1.0);
         double sClamp = clamp(sinCandidate, -1.0, 1.0);
         double trigError = Math.abs(cClamp * cClamp + sClamp * sClamp - 1.0);
         if (trigError > 0.02) continue;
         if (tick > bestTick || (tick == bestTick && trigError < minError)) {
            bestTick = tick;
            minError = trigError;
         }
      }
      return bestTick;
   }

   private static double seedTicksFromHorizontal(double horiz) {
      double ratio = horiz / 1.5;
      double clamped = clamp(ratio / 98.99999999999991, 0.0, 0.999999999999);
      double t = Math.log(1.0 - clamped) / Math.log(0.99);
      return Math.max(1.0, t);
   }

   private static double flightEquation(double t, double horiz, double dy) {
      Terms terms = computeTerms(t);
      double vH = 1.5 * terms.a;
      double vY = dy + terms.c;
      return (horiz * horiz + vY * vY) - (vH * vH);
   }

   private static double flightEquationDerivative(double t, double dy) {
      Terms terms = computeTerms(t);
      return 2.0 * (dy + terms.c) * terms.cp - 4.5 * terms.a * terms.ap;
   }

   private static double horizontalFactor(double t) {
      double pow = Math.pow(0.99, t);
      double factor = (1.0 - pow) / 0.010000000000000009;
      return 0.99 * factor;
   }

   private static double gravityCorrection(double t) {
      double pow = Math.pow(0.99, t);
      double factor = (1.0 - pow) / 0.010000000000000009;
      return 2.969999999999997 * (t - factor);
   }

   private static Terms computeTerms(double t) {
      double invRate = 1.0 / 0.010000000000000009;
      double lnRate = Math.log(0.99);
      double pow = Math.pow(0.99, t);
      double series = (1.0 - pow) * invRate;
      double a = 0.99 * series;
      double ap = 0.99 * (-pow * lnRate * invRate);
      double cCoeff = 0.029699999999999997 * invRate;
      double c = cCoeff * (t - series);
      double cp = cCoeff * (1.0 - (-pow * lnRate * invRate));
      return new Terms(a, ap, c, cp);
   }

   private static double clamp(double val, double min, double max) {
      if (val < min) return min;
      return Math.min(val, max);
   }

   private static float wrapDegrees(float degrees) {
      float f = degrees % 360.0F;
      if (f >= 180.0F) f -= 360.0F;
      if (f < -180.0F) f += 360.0F;
      return f;
   }

   private static class Terms {
      final double a;
      final double ap;
      final double c;
      final double cp;

      Terms(double a, double ap, double c, double cp) {
         this.a = a;
         this.ap = ap;
         this.c = c;
         this.cp = cp;
      }
   }
}
