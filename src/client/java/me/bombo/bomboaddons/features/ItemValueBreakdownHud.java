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
      if (breakdown.reforge != null && (breakdown.reforgeStone == null || breakdown.reforge.price > 0)) rows++;
      if (breakdown.reforgeStone != null) rows++;
      if (breakdown.applyCost != null) rows++;
      if (breakdown.recombobulator != null) rows++;
      if (breakdown.etherwarp != null) rows++;
      if (breakdown.powerScroll != null) rows++;
      if (breakdown.hpb != null) rows++;
      if (breakdown.fuming != null) rows++;
      if (breakdown.tuners != null) rows++;
      rows += breakdown.otherUpgrades.size();
      rows += breakdown.gemstones.size();
      rows += breakdown.enchants.size();
      return Math.max(50, 16 + rows * 11);
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.keyToggleGui.isDown()) return;
      if (mc.gui.screen() instanceof HudMoveScreen) return;
      if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) return;

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
      if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) return;
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

      g.pose().pushMatrix();
      g.pose().translate((float) baseX, (float) baseY);
      g.pose().scale(scale, scale);

      // Clean typography with drop shadows, no dark box background
      g.text(font, "§6§lEstimated Value Breakdown", 0, 0, 0xFFFFAA00, true);
      int curY = 12;

      // 1. Base Item
      if (data.baseItem != null) {
         String bText = "§7Base: §e" + LowestBinManager.formatPrice(data.baseItem.price);
         g.text(font, bText, 0, curY, 0xFFE2E8F0, true);
         curY += 10;
      }

      // 2. Reforge
      if (data.reforge != null && (data.reforgeStone == null || data.reforge.price > 0)) {
         String rText = "§b" + data.reforge.label + (data.reforge.price > 0 ? ": §e" + LowestBinManager.formatPrice(data.reforge.price) : "");
         g.text(font, rText, 0, curY, 0xFF38BDF8, true);
         curY += 10;
      }

      // 3. Stone
      if (data.reforgeStone != null) {
         String sText = "§d" + font.plainSubstrByWidth(data.reforgeStone.label, w - 65) + ": §e" + LowestBinManager.formatPrice(data.reforgeStone.price);
         g.text(font, sText, 0, curY, 0xFFE879F9, true);
         curY += 10;
      }

      // 4. Apply Cost
      if (data.applyCost != null) {
         String acText = "§7Apply Cost: §e" + LowestBinManager.formatPrice(data.applyCost.price);
         g.text(font, acText, 0, curY, 0xFF94A3B8, true);
         curY += 10;
      }

      // 5. Recombobulator
      if (data.recombobulator != null) {
         String rcText = "§5Recombobulator: §e" + LowestBinManager.formatPrice(data.recombobulator.price);
         g.text(font, rcText, 0, curY, 0xFFA855F7, true);
         curY += 10;
      }

      // 6. Etherwarp
      if (data.etherwarp != null) {
         String ewText = "§3Etherwarp Conduit: §e" + LowestBinManager.formatPrice(data.etherwarp.price);
         g.text(font, ewText, 0, curY, 0xFF06B6D4, true);
         curY += 10;
      }

      // 7. Power Scroll
      if (data.powerScroll != null) {
         String psText = "§c" + font.plainSubstrByWidth(data.powerScroll.label, w - 65) + ": §e" + LowestBinManager.formatPrice(data.powerScroll.price);
         g.text(font, psText, 0, curY, 0xFFF87171, true);
         curY += 10;
      }

      // 8. HPB
      if (data.hpb != null) {
         String hpbText = "§e" + data.hpb.label + ": §e" + LowestBinManager.formatPrice(data.hpb.price);
         g.text(font, hpbText, 0, curY, 0xFFFACC15, true);
         curY += 10;
      }

      // 9. Fuming
      if (data.fuming != null) {
         String fpbText = "§6" + data.fuming.label + ": §e" + LowestBinManager.formatPrice(data.fuming.price);
         g.text(font, fpbText, 0, curY, 0xFFFB923C, true);
         curY += 10;
      }

      // 10. Tuners
      if (data.tuners != null) {
         String tText = "§2" + data.tuners.label + ": §e" + LowestBinManager.formatPrice(data.tuners.price);
         g.text(font, tText, 0, curY, 0xFF4ADE80, true);
         curY += 10;
      }

      // Other Upgrades (Wood singularity, Farming for dummies, Stars, Master stars, Attributes)
      for (LowestBinManager.ValueEntry upg : data.otherUpgrades) {
         String uText = "§d" + font.plainSubstrByWidth(upg.label, w - 65) + ": §e" + LowestBinManager.formatPrice(upg.price);
         g.text(font, uText, 0, curY, 0xFFE2E8F0, true);
         curY += 10;
      }

      // Gemstones
      for (LowestBinManager.ValueEntry gem : data.gemstones) {
         String gText = "§b" + font.plainSubstrByWidth(gem.label, w - 65) + ": §e" + LowestBinManager.formatPrice(gem.price);
         g.text(font, gText, 0, curY, 0xFF67E8F9, true);
         curY += 10;
      }

      // Enchants
      for (LowestBinManager.ValueEntry enc : data.enchants) {
         String eText = "§9" + font.plainSubstrByWidth(enc.label, w - 65) + ": §e" + LowestBinManager.formatPrice(enc.price);
         g.text(font, eText, 0, curY, 0xFF93C5FD, true);
         curY += 10;
      }

      // Total Line
      curY += 2;
      g.text(font, "§aTotal: §6§l" + LowestBinManager.formatPrice(data.totalPrice), 0, curY, 0xFFFFFFFF, true);

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
