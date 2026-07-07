package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class CrosshairRenderer {

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Player player = mc.player;
        if (player == null) return;
        
        BomboConfig.CrosshairSettings settings = BomboConfig.get().customCrosshair;
        if (settings == null || !settings.enabled || settings.grid == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        float scale = settings.scale;
        if (scale <= 0) scale = 1.0f;
        
        int centerX = (screenWidth - 15) / 2 + 7;
        int centerY = (screenHeight - 15) / 2 + 7;
        
        int mainColor = getColorValue(settings.color, settings.chroma);
        int outlineColor = getColorValue(settings.outlineColor, false);
        
        int time = (int) mc.level.getGameTime();
        
        // Outline pass
        for (int row = 0; row < 15; row++) {
            for (int col = 0; col < 15; col++) {
                int index = row * 15 + col;
                if (index < settings.grid.length && settings.grid[index]) {
                    float px = (col - 7) * scale;
                    float py = (row - 7) * scale;
                    
                    int x1 = (int) (centerX + px);
                    int y1 = (int) (centerY + py);
                    int x2 = (int) (centerX + px + scale);
                    int y2 = (int) (centerY + py + scale);
                    
                    if (settings.outline) {
                        int ox = Math.max(1, (int)(scale * 0.2f));
                        int pixelOutline = outlineColor;
                        if (row == 7 && col == 7) {
                            int r = (outlineColor >> 16) & 0xFF;
                            int g = (outlineColor >> 8) & 0xFF;
                            int b = outlineColor & 0xFF;
                            pixelOutline = 0xFF000000 | ((255-r) << 16) | ((255-g) << 8) | (255-b);
                        }
                        graphics.fill(x1 - ox, y1 - ox, x2 + ox, y2 + ox, pixelOutline);
                    }
                }
            }
        }
        
        // Main color pass
        for (int row = 0; row < 15; row++) {
            for (int col = 0; col < 15; col++) {
                int index = row * 15 + col;
                if (index < settings.grid.length && settings.grid[index]) {
                    float px = (col - 7) * scale;
                    float py = (row - 7) * scale;
                    
                    int x1 = (int) (centerX + px);
                    int y1 = (int) (centerY + py);
                    int x2 = (int) (centerX + px + scale);
                    int y2 = (int) (centerY + py + scale);
                    
                                        int pixelColor = mainColor;
                    if (row == 7 && col == 7) {
                        int r = (mainColor >> 16) & 0xFF;
                        int g = (mainColor >> 8) & 0xFF;
                        int b = mainColor & 0xFF;
                        pixelColor = 0xFF000000 | ((255-r) << 16) | ((255-g) << 8) | (255-b);
                    }
                    graphics.fill(x1, y1, x2, y2, pixelColor);
                }
            }
        }
    }
    
    public static int getColorValue(String colorName, boolean chroma) {
        if (chroma) {
            long time = System.currentTimeMillis();
            return java.awt.Color.HSBtoRGB((time % 2000L) / 2000.0f, 0.8f, 1.0f) | 0xFF000000;
        }
        if (colorName == null) return 0xFFFFFFFF;
        
        switch (colorName.toUpperCase()) {
            case "WHITE": return 0xFFFFFFFF;
            case "BLACK": return 0xFF000000;
            case "RED": return 0xFFFF5555;
            case "GREEN": return 0xFF55FF55;
            case "BLUE": return 0xFF5555FF;
            case "YELLOW": return 0xFFFFFF55;
            case "AQUA": return 0xFF55FFFF;
            case "PURPLE": return 0xFFFF55FF;
            case "GOLD": return 0xFFFFAA00;
            case "GRAY": return 0xFFAAAAAA;
            case "DARK_GRAY": return 0xFF555555;
            default: return 0xFFFFFFFF;
        }
    }
}
