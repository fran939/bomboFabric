package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class AutoRejoinHud {

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "auto_rejoin_hud"), AutoRejoinHud::render);
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.keyToggleGui.isDown()) return;
      if (mc.gui.screen() instanceof HudMoveScreen) return;

      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.autoRejoinHud && AutoRejoinManager.isCountingDown()) {
         drawHud(g, s.autoRejoinHudX, s.autoRejoinHudY, s.autoRejoinHudScale, false);
      }
   }

   public static int getHudWidth() {
      return 140;
   }

   public static int getHudHeight() {
      return 26;
   }

   public static void drawHud(GuiGraphicsExtractor g, int baseX, int baseY, float scale, boolean forcePreview) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      if (scale <= 0.0F) scale = 1.0F;

      int remaining = forcePreview ? 63 : AutoRejoinManager.getRemainingSeconds();
      int ticks = forcePreview ? 1260 : AutoRejoinManager.getCountdownTicks();
      float progress = Math.max(0.0f, Math.min(1.0f, (float) ticks / 1260.0f));

      int w = getHudWidth();
      int h = getHudHeight();

      g.pose().pushMatrix();
      g.pose().translate((float) baseX, (float) baseY);
      g.pose().scale(scale, scale);

      // Background Card
      g.fill(0, 0, w, h, 0xDD0B0F19);
      g.outline(0, 0, w, h, 0x4438BDF8);

      // Label & Timer
      String timerColor = remaining <= 10 ? "§c" : (remaining <= 30 ? "§e" : "§a");
      String title = "§6§lAuto Rejoin: " + timerColor + remaining + "s";
      g.text(font, title, 6, 5, 0xFFFFFFFF, true);

      // Progress bar
      int barX = 6;
      int barY = 17;
      int barW = w - 12;
      int barH = 4;
      g.fill(barX, barY, barX + barW, barY + barH, 0xFF1E293B);
      int fillW = (int) (barW * progress);
      if (fillW > 0) {
         int barColor = remaining <= 10 ? 0xFFEF4444 : (remaining <= 30 ? 0xFFF59E0B : 0xFF10B981);
         g.fill(barX, barY, barX + fillW, barY + barH, barColor);
      }

      g.pose().popMatrix();
   }
}
