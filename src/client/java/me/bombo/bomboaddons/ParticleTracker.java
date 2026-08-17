package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class ParticleTracker {
   private static final Collection<ParticleEntry> ENTRIES = new ConcurrentLinkedQueue();
   private static final long WINDOW_MS = 5000L;
   public static double espRadius = (double)32.0F;
   public static boolean espEnabled = false;

   public static boolean isParticleTrackingNeeded() {
      if (espEnabled) return true;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) return false;
      if (s.debugParticles) return true;
      if (s.particleHighlightsEnabled && s.particleHighlights != null) {
         for (BomboConfig.HighlightInfo hi : s.particleHighlights.values()) {
            if (hi != null && hi.enabled) return true;
         }
      }
      return false;
   }

   public static void onParticle(String typeName, double x, double y, double z) {
      if (!isParticleTrackingNeeded()) return;
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player != null) {
         double dx = x - player.getX();
         double dy = y - player.getY();
         double dz = z - player.getZ();
         double distSq = dx * dx + dy * dy + dz * dz;
         double maxR = Math.max(espRadius, (double)32.0F);
         if (!(distSq > maxR * maxR)) {
            ENTRIES.add(new ParticleEntry(cleanTypeName(typeName), typeName, x, y, z));
         }
      }
   }

   public static void onTick() {
      if (ENTRIES.isEmpty()) return;
      long cutoff = System.currentTimeMillis() - 5000L;
      ENTRIES.removeIf((e) -> e.timestamp < cutoff);
   }

   public static Map<String, Integer> getSummary(double radius) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player == null) {
         return Collections.emptyMap();
      } else {
         Map<String, Integer> counts = new LinkedHashMap();
         long cutoff = System.currentTimeMillis() - 5000L;
         double rSq = radius * radius;

         for(ParticleEntry e : ENTRIES) {
            if (e.timestamp >= cutoff) {
               double dx = e.x - player.getX();
               double dy = e.y - player.getY();
               double dz = e.z - player.getZ();
               if (!(dx * dx + dy * dy + dz * dz > rSq)) {
                  counts.merge(e.type, 1, Integer::sum);
               }
            }
         }

         List<Map.Entry<String, Integer>> sorted = new ArrayList(counts.entrySet());
         sorted.sort((a, b) -> (Integer)b.getValue() - (Integer)a.getValue());
         Map<String, Integer> result = new LinkedHashMap();

         for(Map.Entry<String, Integer> entry : sorted) {
            result.put((String)entry.getKey(), (Integer)entry.getValue());
         }

         return result;
      }
   }

   public static List<ParticleEntry> getEspPoints(String filterType) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player == null) {
         return Collections.emptyList();
      } else {
         List<ParticleEntry> result = new ArrayList();
         long cutoff = System.currentTimeMillis() - 5000L;
         double rSq = espRadius * espRadius;

         for(ParticleEntry e : ENTRIES) {
            if (e.timestamp >= cutoff) {
               double dx = e.x - player.getX();
               double dy = e.y - player.getY();
               double dz = e.z - player.getZ();
               if (!(dx * dx + dy * dy + dz * dz > rSq) && (filterType == null || e.type.toLowerCase().contains(filterType.toLowerCase()))) {
                  result.add(e);
               }
            }
         }

         return result;
      }
   }

   public static void clear() {
      ENTRIES.clear();
   }

   private static String cleanTypeName(String raw) {
      if (raw == null) {
         return "UNKNOWN";
      } else {
         int colonIdx = raw.lastIndexOf(58);
         if (colonIdx >= 0) {
            raw = raw.substring(colonIdx + 1);
         }

         int dotIdx = raw.lastIndexOf(46);
         if (dotIdx >= 0) {
            raw = raw.substring(dotIdx + 1);
         }

         int atIdx = raw.indexOf(64);
         if (atIdx >= 0) {
            raw = raw.substring(0, atIdx);
         }

         int dollarIdx = raw.indexOf(36);
         if (dollarIdx >= 0) {
            raw = raw.substring(0, dollarIdx);
         }

         return raw.toUpperCase(Locale.ROOT);
      }
   }

   public static int colorForType(String type) {
      if (type == null) {
         return 16777215;
      } else {
         switch (type.toLowerCase(Locale.ROOT)) {
            case "flame":
               return 16737792;
            case "smoke":
            case "large_smoke":
               return 8947848;
            case "witch":
               return 8913151;
            case "heart":
               return 16724889;
            case "crit":
            case "enchanted_hit":
               return 16768256;
            case "firework":
               return 16729292;
            case "explosion":
            case "explosion_emitter":
               return 16729088;
            case "end_rod":
               return 15658734;
            case "dragon_breath":
               return 8913066;
            case "totem_of_undying":
               return 65416;
            case "splash":
            case "rain":
            case "falling_water":
               return 4491519;
            default:
               return 65535;
         }
      }
   }

   public static class ParticleEntry {
      public final String type;
      public final String rawType;
      public final double x;
      public final double y;
      public final double z;
      public final long timestamp;

      public ParticleEntry(String type, String rawType, double x, double y, double z) {
         this.type = type;
         this.rawType = rawType;
         this.x = x;
         this.y = y;
         this.z = z;
         this.timestamp = System.currentTimeMillis();
      }
   }
}
