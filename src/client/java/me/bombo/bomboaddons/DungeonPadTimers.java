package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class DungeonPadTimers {
   private static boolean active = false;
   private static int serverTicks = 0;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "dungeon_pad_timers"), DungeonPadTimers::render);
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.padTimersPurple || s.padTimersGreen) {
         Minecraft client = Minecraft.getInstance();
         if (!client.options.hideGui) {
            if (client.screen == null || client.screen instanceof HudMoveScreen) {
               drawTimerInfo(g, s.padTimersX, s.padTimersY, false);
            }
         }
      }
   }

   public static void onBossMessage() {
      active = true;
      double purpleTime = BomboConfig.get().padTimerPurpleTime;
      int purpleCountdownTicks = (int)Math.round(purpleTime * (double)20.0F);
      serverTicks = 100 + purpleCountdownTicks;
      if (BomboConfig.get().debugCommands || BomboConfig.get().debugMaster) {
         Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Dungeon Pad Timers started!");
      }

   }

   public static void onPingPacket(int packetId) {
      if (active) {
         if (packetId <= 0) {
            --serverTicks;
            if (serverTicks < 15) {
               active = false;
            }
         }

      }
   }

   public static void drawTimerInfo(GuiGraphicsExtractor g, int x, int y, boolean forceShow) {
      g.pose().pushMatrix();
      g.pose().translate((float)x, (float)y);
      float scale = BomboConfig.get().padTimersScale;
      g.pose().scale(scale, scale);
      String text = "";
      BomboConfig.Settings config = BomboConfig.get();
      if (active) {
         double purpleTime = config.padTimerPurpleTime;
         int purpleCountdownTicks = (int)Math.round(purpleTime * (double)20.0F);
         int tGreenNow = 15;
         int tGreenCountdown = 25;
         int tGap = 75;
         int tPurpleNow = 90;
         int tPurpleCountdown = 100;
         if (serverTicks > tPurpleCountdown) {
            if (config.padTimersPurple) {
               double sec = (double)(serverTicks - tPurpleCountdown) / (double)20.0F;
               text = String.format("Pad §dpurple§r in §b%.1fs", sec);
            }
         } else if (serverTicks > tPurpleNow) {
            if (config.padTimersPurple) {
               text = "Pad §dpurple §eNOW!";
            }
         } else if (serverTicks > tGreenCountdown) {
            if (config.padTimersGreen && serverTicks <= tGap) {
               double sec = (double)(serverTicks - tGreenCountdown) / (double)20.0F;
               text = String.format("Pad §agreen§r in §b%.1fs!", sec);
            }
         } else if (serverTicks >= tGreenNow && config.padTimersGreen) {
            text = "Pad §agreen §eNOW!";
         }
      } else if (forceShow) {
         text = String.format("Pad §dpurple§r in §b%.1fs", config.padTimerPurpleTime);
      }

      if (!text.isEmpty()) {
         g.text(Minecraft.getInstance().font, text, 0, 0, -1, true);
      }

      g.pose().popMatrix();
   }

   public static boolean isActive() {
      return active;
   }
}
