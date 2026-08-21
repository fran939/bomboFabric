package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class TabWidgetHud {
   public static final List<String> COMMON_WIDGETS = List.of(
      "Bestiary", "Visitors", "Stats", "Jacob's Contest", "Pests", "Crop Milestones", "Skills", "Area", "Profile", "Bank", "Election", "Events"
   );

   public static void init() {
   }

   public static void onHudRender(GuiGraphicsExtractor g) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.tabWidgets != null && !s.tabWidgets.isEmpty()) {
         Minecraft client = Minecraft.getInstance();
         if (!client.options.hideGui) {
            if (client.screen == null || client.screen instanceof HudMoveScreen) {
               String currentLocation = SkyblockUtils.getLocation();

               for(BomboConfig.TabWidgetInfo widget : s.tabWidgets) {
                  if (widget.enabled && (widget.island.equalsIgnoreCase("All") || widget.island.equalsIgnoreCase("Any") || currentLocation.equalsIgnoreCase(widget.island) || currentLocation.toLowerCase().contains(widget.island.toLowerCase()))) {
                     List<Component> lines = getMatchedWidgetComponents(widget.name, false);
                     if (!lines.isEmpty()) {
                        drawWidgetComponents(g, widget.x, widget.y, widget.scale, lines);
                     }
                  }
               }

            }
         }
      }
   }

   public static List<String> getAvailableTabWidgets() {
      List<String> headers = new ArrayList(COMMON_WIDGETS);

      for(Component comp : SkyblockUtils.getTabListLines()) {
         String raw = comp.getString();
         String clean = raw.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
         if (!clean.isEmpty() && clean.endsWith(":") && !clean.startsWith("Next ") && !clean.startsWith("Plots:") && !clean.startsWith("Alive:") && !clean.startsWith("Spray:") && !clean.startsWith("Repellent:") && !clean.startsWith("Bonus:") && !clean.startsWith("Cooldown:") && !clean.startsWith("Full Traps:") && !clean.startsWith("No Bait:") && !raw.startsWith("  ") && !raw.startsWith("   ") && !raw.startsWith("\t")) {
            String title = clean.substring(0, clean.length() - 1).trim();
            if (!headers.contains(title)) {
               headers.add(title);
            }
         }
      }

      return headers;
   }

   public static List<Component> getMatchedWidgetComponents(String query, boolean isDummy) {
      if (isDummy) {
         List<Component> dummy = new ArrayList();
         String name = query == null ? "Widget" : query;
         String lower = name.toLowerCase();
         if (lower.contains("visitor")) {
            dummy.add(Component.literal("§b§lVisitors: §7(3)"));
            dummy.add(Component.literal(" §cSpaceman §dNEW!"));
            dummy.add(Component.literal(" §aLynn"));
            dummy.add(Component.literal(" §aOdawa"));
            dummy.add(Component.literal(" §fNext Visitor: §b9s"));
         } else if (lower.contains("stat")) {
            dummy.add(Component.literal("§e§lStats:"));
            dummy.add(Component.literal(" §fSpeed: §f400"));
            dummy.add(Component.literal(" §fFarming Fortune: §6526"));
            dummy.add(Component.literal(" §fStrength: §c481"));
            dummy.add(Component.literal(" §fBonus Pest Chance: §2190"));
         } else if (lower.contains("bestiary")) {
            dummy.add(Component.literal("§6§lBestiary:"));
            dummy.add(Component.literal(" §fLava Flame 16: §b408/425"));
            dummy.add(Component.literal(" §fAnt 0: §b0/1"));
            dummy.add(Component.literal(" §fBeeheemoth 0: §b0/1"));
            dummy.add(Component.literal(" §fBlue Jay 0: §b0/1"));
            dummy.add(Component.literal(" §fBrineling 0: §b0/2"));
            dummy.add(Component.literal(" §fBunbun 0: §b0/1"));
            dummy.add(Component.literal(" §fDrybark 0: §b0/1"));
            dummy.add(Component.literal(" §fDustybit 0: §b0/1"));
            dummy.add(Component.literal(" §fEmber 0: §b0/1"));
            dummy.add(Component.literal(" §fFirefox 0: §b0/1"));
            dummy.add(Component.literal(" §fGiant Isopod 0: §b0/1"));
            dummy.add(Component.literal(" §fGrizzly Bear 0: §b0/1"));
            dummy.add(Component.literal(" §fGroundhog 0: §b0/1"));
            dummy.add(Component.literal(" §fHaggard 0: §b0/2"));
            dummy.add(Component.literal(" §fHideonsun 0: §b0/1"));
            dummy.add(Component.literal(" §fHivethief 0: §b0/1"));
            dummy.add(Component.literal(" §fHoneybuzz 0: §b0/1"));
            dummy.add(Component.literal(" §fMountain Goat 0: §b0/1"));
            dummy.add(Component.literal(" §fPangolin 0: §b0/1"));
            dummy.add(Component.literal(" §fParched 0: §b0/1"));
            dummy.add(Component.literal(" §fPollendart 0: §b0/1"));
            dummy.add(Component.literal(" §fPuck 0: §b0/1"));
            dummy.add(Component.literal(" §fQueen Ant 0: §b0/5"));
            dummy.add(Component.literal(" §fSepialot 0: §b0/1"));
            dummy.add(Component.literal(" §fSilkbreeze 0: §b0/1"));
            dummy.add(Component.literal(" §fSolar 0: §b0/1"));
            dummy.add(Component.literal(" §fSprawl 0: §b0/1"));
            dummy.add(Component.literal(" §fTiki 0: §b0/1"));
         } else if (!lower.contains("crop") && !lower.contains("milestone")) {
            if (lower.contains("jacob")) {
               dummy.add(Component.literal("§e§lJacob's Contest:"));
               dummy.add(Component.literal(" §fStarts In: §e4m 51s"));
               dummy.add(Component.literal(" §e○ §fSugar Cane"));
               dummy.add(Component.literal(" §6\ue051 §fSunflower"));
            } else if (lower.contains("pest")) {
               dummy.add(Component.literal("§4§lPests:"));
               dummy.add(Component.literal(" §fAlive: §46"));
               dummy.add(Component.literal(" §fPlots: §b10"));
            } else {
               dummy.add(Component.literal("§b§l" + name + ":"));
               dummy.add(Component.literal(" §7Sample Item 1"));
               dummy.add(Component.literal(" §7Sample Item 2"));
            }
         } else {
            dummy.add(Component.literal("§b§lCrop Milestones:"));
            dummy.add(Component.literal(" §fMushroom 46: §c§lMAX"));
            dummy.add(Component.literal(" §fCarrot 46: §c§lMAX"));
         }

         return dummy;
      } else {
         List<Component> result = new ArrayList();
         List<Component> tabLines = SkyblockUtils.getTabListLines();
         if (!tabLines.isEmpty() && query != null && !query.trim().isEmpty()) {
            String search = query.trim().toLowerCase();
            int targetStartIndex = -1;

            for(int i = 0; i < tabLines.size(); ++i) {
               String cleanLine = ((Component)tabLines.get(i)).getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
               if (!cleanLine.isEmpty()) {
                  String cleanLower = cleanLine.toLowerCase();
                  if (cleanLower.equals(search + ":") || cleanLower.equals(search) || cleanLower.startsWith(search + ":") || cleanLower.startsWith(search + " (")) {
                     targetStartIndex = i;
                     break;
                  }
               }
            }

            if (targetStartIndex == -1) {
               return result;
            } else {
               result.add((Component)tabLines.get(targetStartIndex));

               for(int i = targetStartIndex + 1; i < tabLines.size(); ++i) {
                  Component comp = (Component)tabLines.get(i);
                  String rawLine = comp.getString();
                  String unformattedLine = rawLine.replaceAll("(?i)§[0-9a-fk-or]", "");
                  String clean = unformattedLine.trim();
                  if (!clean.isEmpty()) {
                     if (clean.equalsIgnoreCase("Info") || clean.startsWith("Info ") || clean.startsWith("Info:") || clean.endsWith("Info")) {
                        continue;
                     }
                     if (!unformattedLine.startsWith(" ") && !unformattedLine.startsWith("\t") && !clean.contains(":") && !clean.contains("/")) {
                        break;
                     }

                     result.add(comp);
                  }
               }

               return result;
            }
         } else {
            return result;
         }
      }
   }

   public static List<String> getMatchedWidgetLines(String query, boolean isDummy) {
      List<Component> comps = getMatchedWidgetComponents(query, isDummy);
      List<String> lines = new ArrayList();

      for(Component c : comps) {
         lines.add(SkyblockUtils.getFormattedComponentText(c));
      }

      return lines;
   }

   public static void drawWidgetComponents(GuiGraphicsExtractor g, int x, int y, float scale, List<Component> lines) {
      g.pose().pushMatrix();
      g.pose().translate((float)x, (float)y);
      g.pose().scale(scale, scale);
      int curY = 0;

      for(Component line : lines) {
         g.text(Minecraft.getInstance().font, line, 0, curY, -1, true);
         curY += 10;
      }

      g.pose().popMatrix();
   }

   public static void drawWidgetInfo(GuiGraphicsExtractor g, int x, int y, float scale, List<String> lines) {
      g.pose().pushMatrix();
      g.pose().translate((float)x, (float)y);
      g.pose().scale(scale, scale);
      int curY = 0;

      for(String line : lines) {
         g.text(Minecraft.getInstance().font, line, 0, curY, -1, true);
         curY += 10;
      }

      g.pose().popMatrix();
   }

   public static int getWidth() {
      return 140;
   }

   public static int getHeight(int lineCount) {
      return Math.max(20, lineCount * 10);
   }
}
