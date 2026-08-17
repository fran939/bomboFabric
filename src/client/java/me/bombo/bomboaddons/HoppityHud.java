package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.eggfinder.EggFinder;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class HoppityHud {
   private static final List<EggClickArea> clickAreas = new ArrayList();

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "hoppity_hud"), HoppityHud::render);
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.hoppityHud) {
         Minecraft mc = Minecraft.getInstance();
         if (!s.hoppityHideWhenInactive || AlphaTrackerHud.isHoppityActive() || mc.screen instanceof HudMoveScreen) {
            if (!mc.options.hideGui) {
               if (mc.screen == null || mc.screen instanceof HudMoveScreen || mc.screen instanceof ChatScreen || mc.screen instanceof AbstractContainerScreen) {
                  synchronized(clickAreas) {
                     clickAreas.clear();
                  }

                  int baseX = s.hoppityHudX;
                  int baseY = s.hoppityHudY;
                  Font font = mc.font;
                  g.text(font, "§6§lHoppity Eggs", baseX, baseY, -22016, true);
                  EggFinder.EggType[] activeTypes = EggFinder.EggType.values();
                  List<EggFinder.EggWaypoint> waypoints = EggFinder.getActiveWaypoints();
                  int curY = baseY + 12;

                  for(EggFinder.EggType type : activeTypes) {
                     EggFinder.EggWaypoint matchingWp = null;

                     for(EggFinder.EggWaypoint wp : waypoints) {
                        if (wp.type == type) {
                           matchingWp = wp;
                           break;
                        }
                     }

                     boolean isCollected = type.collected || matchingWp != null && matchingWp.collected;
                     BlockPos targetPos = matchingWp != null ? matchingWp.pos : null;
                     String label = type.name + " Egg";
                     int textWidth = font.width(label);
                     int color = -16777216 | type.hexColor;
                     g.text(font, label, baseX, curY, color, true);
                     if (isCollected) {
                        int lineY = curY + 4;
                        g.fill(baseX - 1, lineY - 1, baseX + textWidth + 1, lineY + 3, -16777216);
                        g.fill(baseX - 1, lineY, baseX + textWidth + 1, lineY + 2, -2039584);
                     }

                     synchronized(clickAreas) {
                        clickAreas.add(new EggClickArea(baseX, curY, textWidth, 9, type, targetPos));
                     }

                     curY += 11;
                  }

               }
            }
         }
      }
   }

   public static boolean onMouseClick(double mouseX, double mouseY, int button) {
      BomboConfig.Settings s = BomboConfig.get();
      if (!s.hoppityHud) {
         return false;
      } else if (button != 0) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.screen != null) {
            return false;
         } else {
            List<EggClickArea> areas;
            synchronized(clickAreas) {
               areas = new ArrayList(clickAreas);
            }

            for(EggClickArea area : areas) {
               if (mouseX >= (double)area.x && mouseX <= (double)(area.x + area.width) && mouseY >= (double)area.y && mouseY <= (double)(area.y + area.height)) {
                  if (area.pos != null) {
                     if (mc.player != null && mc.player.connection != null) {
                        ClientPacketListener var10000 = mc.player.connection;
                        int var10001 = area.pos.getX();
                        var10000.sendCommand("shnav " + var10001 + " " + area.pos.getY() + " " + area.pos.getZ());
                        String var11 = area.type.name;
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aNavigating to §e" + var11 + " Egg §aat §e" + area.pos.getX() + ", " + area.pos.getY() + ", " + area.pos.getZ() + "!"));
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                     }
                  } else if (mc.player != null) {
                     mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cNo waypoint position discovered for " + area.type.name + " Egg yet!"));
                     mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.5F));
                  }

                  return true;
               }
            }

            return false;
         }
      }
   }

   public static class EggClickArea {
      public final int x;
      public final int y;
      public final int width;
      public final int height;
      public final EggFinder.EggType type;
      public final BlockPos pos;

      public EggClickArea(int x, int y, int width, int height, EggFinder.EggType type, BlockPos pos) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.type = type;
         this.pos = pos;
      }
   }
}
