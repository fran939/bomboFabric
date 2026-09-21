package me.bombo.bomboaddons.features;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ItemValueBreakdownHud {
   public static Slot currentHoveredSlot = null;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "item_value_breakdown_hud"), ItemValueBreakdownHud::render);
   }

   public static int getHudWidth() {
      return 190;
   }

   public static int getHudHeight(LowestBinManager.ItemValueBreakdown breakdown) {
      if (breakdown == null) return 80;
      int rows = 2; // Title & Total
      if (breakdown.baseItem != null) rows++;
      rows += breakdown.upgrades.size();
      rows += breakdown.gemstones.size();
      rows += breakdown.enchants.size();
      return Math.max(60, 16 + rows * 11);
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.keyToggleGui.isDown()) return;
      if (mc.gui.screen() instanceof HudMoveScreen) return;

      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.itemValueBreakdownHud) return;

      if (mc.gui.screen() instanceof AbstractContainerScreen<?>) {
         Slot hovered = currentHoveredSlot;
         if (hovered != null && hovered.hasItem()) {
            ItemStack stack = hovered.getItem();
            String sbId = SkyblockUtils.getInternalId(stack);
            LowestBinManager.ItemValueBreakdown breakdown = LowestBinManager.calculateDetailedEstimatedValue(stack, sbId);
            if (breakdown != null && breakdown.totalPrice > 0L) {
               int posX = s.itemValueBreakdownHudX >= 0 ? s.itemValueBreakdownHudX : 10;
               int posY = s.itemValueBreakdownHudY >= 0 ? s.itemValueBreakdownHudY : (mc.getWindow().getGuiScaledHeight() / 2 - 40);
               drawBreakdown(g, posX, posY, s.itemValueBreakdownHudScale, breakdown, false);
            }
         }
      }
   }

   public static void renderDirect(GuiGraphicsExtractor g) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.itemValueBreakdownHud) return;

      Slot hovered = currentHoveredSlot;
      if (hovered != null && hovered.hasItem()) {
         ItemStack stack = hovered.getItem();
         String sbId = SkyblockUtils.getInternalId(stack);
         LowestBinManager.ItemValueBreakdown breakdown = LowestBinManager.calculateDetailedEstimatedValue(stack, sbId);
         if (breakdown != null && breakdown.totalPrice > 0L) {
            int posX = s.itemValueBreakdownHudX >= 0 ? s.itemValueBreakdownHudX : 10;
            int posY = s.itemValueBreakdownHudY >= 0 ? s.itemValueBreakdownHudY : (mc.getWindow().getGuiScaledHeight() / 2 - 40);
            drawBreakdown(g, posX, posY, s.itemValueBreakdownHudScale, breakdown, false);
         }
      }
   }

   public static void drawBreakdown(GuiGraphicsExtractor g, int baseX, int baseY, float scale, LowestBinManager.ItemValueBreakdown breakdown, boolean forceDummy) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      if (scale <= 0.0F) scale = 1.0F;

      LowestBinManager.ItemValueBreakdown data = breakdown;
      if (forceDummy || data == null) {
         data = getDummyBreakdown();
      }

      int w = getHudWidth();
      int h = getHudHeight(data);

      g.pose().pushMatrix();
      g.pose().translate((float) baseX, (float) baseY);
      g.pose().scale(scale, scale);

      // Card Background
      g.fill(0, 0, w, h, 0xEE0F172A);
      g.outline(0, 0, w, h, 0x4400E5FF);

      // Header
      g.text(font, "§6§lEstimated Value Breakdown", 6, 5, 0xFFFFFFFF, true);
      int curY = 18;

      if (data.baseItem != null) {
         String bText = "§7Base: §e" + LowestBinManager.formatPrice(data.baseItem.price);
         g.text(font, bText, 8, curY, 0xFFE2E8F0, false);
         curY += 11;
      }

      for (LowestBinManager.ValueEntry upg : data.upgrades) {
         String uText = "§d" + font.plainSubstrByWidth(upg.label, w - 75) + ": §e" + LowestBinManager.formatPrice(upg.price);
         g.text(font, uText, 8, curY, 0xFFE2E8F0, false);
         curY += 11;
      }

      for (LowestBinManager.ValueEntry gem : data.gemstones) {
         String gText = "§b" + font.plainSubstrByWidth(gem.label, w - 75) + ": §e" + LowestBinManager.formatPrice(gem.price);
         g.text(font, gText, 8, curY, 0xFFE2E8F0, false);
         curY += 11;
      }

      for (LowestBinManager.ValueEntry enc : data.enchants) {
         String eText = "§9" + font.plainSubstrByWidth(enc.label, w - 75) + ": §e" + LowestBinManager.formatPrice(enc.price);
         g.text(font, eText, 8, curY, 0xFFE2E8F0, false);
         curY += 11;
      }

      // Total Line
      g.fill(6, curY + 1, w - 6, curY + 2, 0x33FFFFFF);
      curY += 4;
      g.text(font, "§aTotal: §6§l" + LowestBinManager.formatPrice(data.totalPrice), 8, curY, 0xFFFFFFFF, true);

      g.pose().popMatrix();
   }

   private static LowestBinManager.ItemValueBreakdown getDummyBreakdown() {
      LowestBinManager.ItemValueBreakdown dummy = new LowestBinManager.ItemValueBreakdown();
      dummy.baseItem = new LowestBinManager.ValueEntry("Hyperion", 850_000_000L, "Item");
      dummy.upgrades.add(new LowestBinManager.ValueEntry("Wither Shield", 180_000_000L, "Scroll"));
      dummy.upgrades.add(new LowestBinManager.ValueEntry("Shadow Warp", 175_000_000L, "Scroll"));
      dummy.gemstones.add(new LowestBinManager.ValueEntry("Flawless Sapphire", 12_500_000L, "Gem"));
      dummy.enchants.add(new LowestBinManager.ValueEntry("Smite VII", 25_000_000L, "Enchant"));
      dummy.totalPrice = 1_242_500_000L;
      return dummy;
   }
}
