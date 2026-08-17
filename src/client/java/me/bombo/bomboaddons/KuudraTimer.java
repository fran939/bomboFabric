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

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.kuudraBlindnessTimer) {
         Minecraft client = Minecraft.getInstance();
         if (!client.options.hideGui) {
            if (client.screen == null || client.screen instanceof HudMoveScreen) {
               boolean isKuudraOrLocal = "Kuudra".equalsIgnoreCase(BomboaddonsClient.currentArea) || "Kuudra's Hollow".equalsIgnoreCase(BomboaddonsClient.currentArea) || client.isLocalServer();
               if (!isKuudraOrLocal) {
                  lastDurationTicks = 0;
               } else {
                  int durationTicks = 0;
                  if (client.player != null) {
                     MobEffectInstance inst = client.player.getEffect(MobEffects.BLINDNESS);
                     if (inst != null) {
                        durationTicks = inst.getDuration();
                     }
                  }

                  long now = System.currentTimeMillis();
                  if (durationTicks > lastDurationTicks) {
                     blindEndTime = now + 500L;
                     nowEndTime = blindEndTime + 500L;
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
      g.text(Minecraft.getInstance().font, text, 0, 0, -1, true);
      g.pose().popMatrix();
   }

   public static boolean isActive() {
      return System.currentTimeMillis() < nowEndTime;
   }
}
