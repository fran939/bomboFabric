package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class KuudraTimer {
   private static int lastDurationTicks = 0;
   private static long blindEndTime = 0L;
   private static long nowEndTime = 0L;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "kuudra_timer"), KuudraTimer::render);
   }

   public static void triggerTest(long durationMs) {
      long now = System.currentTimeMillis();
      blindEndTime = now + durationMs;
      nowEndTime = blindEndTime + 500L;
   }

   public static void onPlayerEaten() {
      long now = System.currentTimeMillis();
      if (now > nowEndTime) {
         blindEndTime = now;
         nowEndTime = now + 500L;
      }
   }

   public static void reset() {
      lastDurationTicks = 0;
      blindEndTime = 0L;
      nowEndTime = 0L;
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      try (me.bombo.bomboaddons.PerformanceProfiler.Scope p = me.bombo.bomboaddons.PerformanceProfiler.scope("HUD: Kuudra Timer")) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null || !s.kuudraBlindnessTimer) return;

         Minecraft client = Minecraft.getInstance();
         if (client.options.keyToggleGui.isDown()) return;
         if (client.gui.screen() != null && !(client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) return;

         boolean isKuudraOrLocal = SkyblockUtils.isInKuudra()
               || me.bombo.bomboaddons.kuudra.pearls.KuudraUtils.inKuudra()
               || "Kuudra".equalsIgnoreCase(BomboaddonsClient.currentArea)
               || "Kuudra's Hollow".equalsIgnoreCase(BomboaddonsClient.currentArea)
               || client.isLocalServer()
               || isActive();

         if (!isKuudraOrLocal) {
            lastDurationTicks = 0;
            return;
         }

         int durationTicks = 0;
         if (client.player != null) {
            // Read activeEffectsMap directly to bypass PlayerMixin disableBlindness suppression
            MobEffectInstance inst = client.player.getActiveEffectsMap().get(MobEffects.BLINDNESS);
            if (inst != null) {
               durationTicks = inst.getDuration();
            }
         }

         long now = System.currentTimeMillis();
         if (durationTicks > 0) {
            if (durationTicks > lastDurationTicks || now > nowEndTime) {
               long countdownMs = Math.max(500L, durationTicks * 50L);
               blindEndTime = now + countdownMs;
               nowEndTime = blindEndTime + 500L;
            }
         }
         lastDurationTicks = durationTicks;

         long remaining = blindEndTime - now;
         long remainingNow = nowEndTime - now;
         if (remaining > 0L) {
            drawTimerInfo(g, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, false, remaining, false);
         } else if (remainingNow > 0L) {
            drawTimerInfo(g, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, false, 0L, true);
         }
      }
   }

   public static void drawTimerInfo(GuiGraphicsExtractor g, int x, int y, boolean forceShow) {
      drawTimerInfo(g, x, y, forceShow, 500L, false);
   }

   public static void drawTimerInfo(GuiGraphicsExtractor g, int x, int y, boolean forceShow, long remaining, boolean showNow) {
      g.pose().pushMatrix();
      g.pose().translate((float)x, (float)y);
      float scale = BomboConfig.get().kuudraBlindnessTimerScale;
      if (scale <= 0.0F) scale = 1.0F;
      g.pose().scale(scale, scale);
      String timeStr;
      if (showNow) {
         timeStr = "§aNOW";
      } else {
         String colorCode;
         if (remaining >= 400L) {
            colorCode = "§a";
         } else if (remaining >= 200L) {
            colorCode = "§6";
         } else {
            colorCode = "§c";
         }

         timeStr = colorCode + remaining + "ms";
      }

      String text = "§9Eaten In §f✈ " + timeStr;
      Minecraft mc = Minecraft.getInstance();
      if (mc.font != null) {
         g.text(mc.font, text, 0, 0, -1, true);
      }
      g.pose().popMatrix();
   }

   public static boolean isActive() {
      return System.currentTimeMillis() < nowEndTime;
   }
}
