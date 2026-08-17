package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class AutoCroesusHud {
   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "auto_croesus_hud"), AutoCroesusHud::render);
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.options.hideGui) {
         if (!(mc.screen instanceof HudMoveScreen)) {
            BomboConfig.Settings s = BomboConfig.get();
            if (s.autoCroesusHud) {
               drawCroesusInfo(g, s.autoCroesusHudX, s.autoCroesusHudY, s.autoCroesusHudScale, false);
            }
         }
      }
   }

   public static void renderInContainer(GuiGraphicsExtractor g) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.options.hideGui) {
         if (!(mc.screen instanceof HudMoveScreen)) {
            BomboConfig.Settings s = BomboConfig.get();
            if (s.autoCroesusHud) {
               drawCroesusInfo(g, s.autoCroesusHudX, s.autoCroesusHudY, s.autoCroesusHudScale, false);
            }
         }
      }
   }

   public static void drawCroesusInfo(GuiGraphicsExtractor g, int baseX, int baseY, float scale, boolean forceDummy) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      BomboConfig.Settings s = BomboConfig.get();
      if (scale <= 0.0F) {
         scale = 1.0F;
      }

      boolean isDummy = forceDummy || AutoCroesus.lastRunItems.isEmpty();
      String tier = isDummy ? "Infernal" : AutoCroesus.lastRunTier;
      long keyCost = isDummy ? 2400000L : AutoCroesus.lastRunKeyCost;
      long contentsValue = isDummy ? 2480000L : AutoCroesus.lastRunContentsValue;
      long profit = isDummy ? 80000L : AutoCroesus.lastRunProfit;
      List<String> items = isDummy ? getDummyItems() : AutoCroesus.lastRunItems;
      String status = isDummy ? "§a[Ready / Testing]" : AutoCroesus.hudActionStatus;
      g.pose().pushMatrix();
      g.pose().translate((float)baseX, (float)baseY);
      g.pose().scale(scale, scale);
      int padding = 0;
      int lineHeight = 10;
      int width = 220;
      g.text(font, "§b§lCroesus Analysis §7(" + tier + ")", padding, padding, 16777215, true);
      int curY = padding + lineHeight;
      int displayed = 0;

      for(String itemLine : items) {
         if (displayed >= 6) {
            break;
         }

         String shortLine = font.plainSubstrByWidth(itemLine, width);
         g.text(font, "§f" + shortLine, padding, curY, 16777215, true);
         curY += lineHeight;
         ++displayed;
      }

      String var10002 = LowestBinManager.formatPrice(contentsValue);
      g.text(font, "§7Contents: §6" + var10002 + " §7| Key: §c" + LowestBinManager.formatPrice(keyCost), padding, curY, 16777215, true);
      curY += lineHeight;
      String profitColor = profit >= 0L ? "§a+" : "§c";
      g.text(font, "§7Net Profit: " + profitColor + LowestBinManager.formatPrice(profit) + " coins", padding, curY, 16777215, true);
      curY += lineHeight;
      if (status != null && !status.isEmpty()) {
         g.text(font, status, padding, curY, 16777215, true);
      }

      g.pose().popMatrix();
   }

   private static List<String> getDummyItems() {
      List<String> list = new ArrayList();
      list.add("Kada Knight Shard: Price: 250,000 coins");
      list.add("Enchanted Book (Vampiric Vitality V): Price: 100,000 coins");
      list.add("Crimson Essence x2,000: Price: 1,924,000 coins");
      list.add("Kuudra Teeth x4: Price: 29,316 coins");
      list.add("Kraken Shard: Price: 250,000 coins");
      return list;
   }
}
