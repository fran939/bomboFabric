package me.bombo.bomboaddons;

import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

public class CrosshairRenderer {
   public static void render(GuiGraphicsExtractor graphics) {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.options.hideGui) {
         Player player = mc.player;
         if (player != null) {
            BomboConfig.CrosshairSettings settings = BomboConfig.get().customCrosshair;
            if (settings != null && settings.enabled && settings.grid != null) {
               int screenWidth = mc.getWindow().getGuiScaledWidth();
               int screenHeight = mc.getWindow().getGuiScaledHeight();
               float scale = settings.scale;
               if (scale <= 0.0F) {
                  scale = 1.0F;
               }

               int centerX = (screenWidth - 15) / 2 + 7;
               int centerY = (screenHeight - 15) / 2 + 7;
               int mainColor = getColorValue(settings.color, settings.chroma);
               int outlineColor = getColorValue(settings.outlineColor, false);
               int time = (int)mc.level.getGameTime();

               for(int row = 0; row < 15; ++row) {
                  for(int col = 0; col < 15; ++col) {
                     int index = row * 15 + col;
                     if (index < settings.grid.length && settings.grid[index]) {
                        float px = (float)(col - 7) * scale;
                        float py = (float)(row - 7) * scale;
                        int x1 = (int)((float)centerX + px);
                        int y1 = (int)((float)centerY + py);
                        int x2 = (int)((float)centerX + px + scale);
                        int y2 = (int)((float)centerY + py + scale);
                        if (settings.outline) {
                           int ox = Math.max(1, (int)(scale * 0.2F));
                           int pixelOutline = outlineColor;
                           if (row == 7 && col == 7) {
                              int r = outlineColor >> 16 & 255;
                              int g = outlineColor >> 8 & 255;
                              int b = outlineColor & 255;
                              pixelOutline = -16777216 | 255 - r << 16 | 255 - g << 8 | 255 - b;
                           }

                           graphics.fill(x1 - ox, y1 - ox, x2 + ox, y2 + ox, pixelOutline);
                        }
                     }
                  }
               }

               for(int row = 0; row < 15; ++row) {
                  for(int col = 0; col < 15; ++col) {
                     int index = row * 15 + col;
                     if (index < settings.grid.length && settings.grid[index]) {
                        float px = (float)(col - 7) * scale;
                        float py = (float)(row - 7) * scale;
                        int x1 = (int)((float)centerX + px);
                        int y1 = (int)((float)centerY + py);
                        int x2 = (int)((float)centerX + px + scale);
                        int y2 = (int)((float)centerY + py + scale);
                        int pixelColor = mainColor;
                        if (row == 7 && col == 7) {
                           int r = mainColor >> 16 & 255;
                           int g = mainColor >> 8 & 255;
                           int b = mainColor & 255;
                           pixelColor = -16777216 | 255 - r << 16 | 255 - g << 8 | 255 - b;
                        }

                        graphics.fill(x1, y1, x2, y2, pixelColor);
                     }
                  }
               }

            }
         }
      }
   }

   public static int getColorValue(String colorName, boolean chroma) {
      if (chroma) {
         long time = System.currentTimeMillis();
         return Color.HSBtoRGB((float)(time % 2000L) / 2000.0F, 0.8F, 1.0F) | -16777216;
      } else if (colorName == null) {
         return -1;
      } else {
         switch (colorName.toUpperCase()) {
            case "WHITE" -> {
               return -1;
            }
            case "BLACK" -> {
               return -16777216;
            }
            case "RED" -> {
               return -43691;
            }
            case "GREEN" -> {
               return -11141291;
            }
            case "BLUE" -> {
               return -11184641;
            }
            case "YELLOW" -> {
               return -171;
            }
            case "AQUA" -> {
               return -11141121;
            }
            case "PURPLE" -> {
               return -43521;
            }
            case "GOLD" -> {
               return -22016;
            }
            case "GRAY" -> {
               return -5592406;
            }
            case "DARK_GRAY" -> {
               return -11184811;
            }
            default -> {
               return -1;
            }
         }
      }
   }
}
