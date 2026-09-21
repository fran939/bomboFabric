package me.bombo.bomboaddons.features.critters;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class CritterMapHud {

    public static boolean debugAerialScanning = false;

    public static void render(GuiGraphicsExtractor g, int x, int y, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null || mc.level == null) return;
        if (!SafariLocation.inSafari()) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.critterMapHud) return;

        Font font = mc.font;

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        if (scale != 1.0f && scale > 0.0f) {
            g.pose().scale(scale, scale);
        }

        int mapW = 120;
        int mapH = 120;

        // Custom HUD Style
        BomboConfig.HudStyle style = s.getHudStyle("CRITTER_MAP");
        int bgColor = style.getBackgroundColorInt();
        int borderColor = style.getBorderColorInt();

        g.fill(0, 0, mapW, mapH, bgColor);
        g.outline(0, 0, mapW, mapH, borderColor);

        // Total map bounds
        double minMapX = -150, maxMapX = 60;
        double minMapZ = -100, maxMapZ = 100;

        boolean is3D = s.critterMap3D;

        for (SafariBiome b : SafariBiome.values()) {
            int bx1 = (int) mapCoord(b.minX, minMapX, maxMapX, mapW);
            int bz1 = (int) mapCoord(b.minZ, minMapZ, maxMapZ, mapH);
            int bx2 = (int) mapCoord(b.maxX, minMapX, maxMapX, mapW);
            int bz2 = (int) mapCoord(b.maxZ, minMapZ, maxMapZ, mapH);

            int bCol = (0x33000000) | (b.colour() & 0x00FFFFFF);

            if (is3D) {
                // 3D Isometric Projection
                int isoX1 = mapW / 2 + (bx1 - bz1) / 2;
                int isoY1 = 20 + (bx1 + bz1) / 4;
                int isoX2 = mapW / 2 + (bx2 - bz2) / 2;
                int isoY2 = 20 + (bx2 + bz2) / 4;
                g.fill(Math.min(isoX1, isoX2), Math.min(isoY1, isoY2), Math.max(isoX1, isoX2), Math.max(isoY1, isoY2), bCol);
                g.outline(Math.min(isoX1, isoX2), Math.min(isoY1, isoY2), Math.abs(isoX2 - isoX1), Math.abs(isoY2 - isoY1), 0x55FFFFFF);
            } else {
                // 2D Aerial Projection
                g.fill(Math.min(bx1, bx2), Math.min(bz1, bz2), Math.max(bx1, bx2), Math.max(bz1, bz2), bCol);
                g.outline(Math.min(bx1, bx2), Math.min(bz1, bz2), Math.abs(bx2 - bx1), Math.abs(bz2 - bz1), 0x55FFFFFF);
            }
        }

        // Title
        boolean isUnderground = mc.player.getY() < 55 && (SafariLocation.biome() == SafariBiome.CAVERN || SafariLocation.biome() == SafariBiome.FOREST);
        String title = "§6§lSafari Map " + (is3D ? "§d(3D)" : isUnderground ? "§7(Cave)" : "§7(2D)");
        g.text(font, title, 4, 4, style.getTitleColorInt(), true);

        String playerStyle = s.critterMapPlayerStyle != null ? s.critterMapPlayerStyle : "Small Icon";

        // Render other players (filter out Tab bots/fake NPCs)
        if (mc.getConnection() != null) {
            for (PlayerInfo pInfo : mc.getConnection().getOnlinePlayers()) {
                String pName = pInfo.getProfile().name();
                if (pName.equalsIgnoreCase(mc.getUser().getName())) continue;
                if (!isValidPlayer(pName, pInfo.getProfile().id())) continue;

                net.minecraft.world.entity.player.Player pEntity = mc.level.getPlayerByUUID(pInfo.getProfile().id());
                if (pEntity != null) {
                    double px = pEntity.getX();
                    double pz = pEntity.getZ();
                    int mx = (int) mapCoord(px, minMapX, maxMapX, mapW);
                    int mz = (int) mapCoord(pz, minMapZ, maxMapZ, mapH);

                    if (is3D) {
                        int isoX = mapW / 2 + (mx - mz) / 2;
                        int isoY = 20 + (mx + mz) / 4;
                        mx = isoX;
                        mz = isoY;
                    }

                    if (mx >= 4 && mx <= mapW - 4 && mz >= 4 && mz <= mapH - 4) {
                        renderPlayerOnMap(g, font, pInfo, pName, mx, mz, playerStyle, 0xFF00FFFF);
                    }
                }
            }
        }

        // Render Local Player
        double myX = mc.player.getX();
        double myZ = mc.player.getZ();
        int myScreenX = (int) mapCoord(myX, minMapX, maxMapX, mapW);
        int myScreenZ = (int) mapCoord(myZ, minMapZ, maxMapZ, mapH);

        if (is3D) {
            int isoX = mapW / 2 + (myScreenX - myScreenZ) / 2;
            int isoY = 20 + (myScreenX + myScreenZ) / 4;
            myScreenX = isoX;
            myScreenZ = isoY;
        }

        myScreenX = Math.max(4, Math.min(mapW - 4, myScreenX));
        myScreenZ = Math.max(4, Math.min(mapH - 4, myScreenZ));

        PlayerInfo localInfo = mc.getConnection() != null && mc.player != null ? mc.getConnection().getPlayerInfo(mc.player.getUUID()) : null;
        renderPlayerOnMap(g, font, localInfo, mc.getUser().getName(), myScreenX, myScreenZ, playerStyle, 0xFFFFFF00);

        g.pose().popMatrix();

        // Mouse hover tooltip over minimap
        if (s.critterShowMissing && mc.gui != null && mc.gui.screen() != null) {
            double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();

            double relX = (mouseX - x) / (scale > 0 ? scale : 1.0f);
            double relY = (mouseY - y) / (scale > 0 ? scale : 1.0f);

            if (relX >= 0 && relX <= mapW && relY >= 0 && relY <= mapH) {
                // Determine hovered biome
                SafariBiome hoveredBiome = null;
                for (SafariBiome b : SafariBiome.values()) {
                    int bx1 = (int) mapCoord(b.minX, minMapX, maxMapX, mapW);
                    int bz1 = (int) mapCoord(b.minZ, minMapZ, maxMapZ, mapH);
                    int bx2 = (int) mapCoord(b.maxX, minMapX, maxMapX, mapW);
                    int bz2 = (int) mapCoord(b.maxZ, minMapZ, maxMapZ, mapH);

                    int minXBound = Math.min(bx1, bx2);
                    int maxXBound = Math.max(bx1, bx2);
                    int minZBound = Math.min(bz1, bz2);
                    int maxZBound = Math.max(bz1, bz2);

                    if (relX >= minXBound && relX <= maxXBound && relY >= minZBound && relY <= maxZBound) {
                        hoveredBiome = b;
                        break;
                    }
                }

                if (hoveredBiome != null) {
                    SafariSession session = CritterSessionManager.currentOrLast();
                    boolean uniqueOnly = !"All".equalsIgnoreCase(s.critterTrackingMode);
                    if (session != null) {
                        int caught = session.partyUnique(hoveredBiome);
                        int total = Critters.totalIn(hoveredBiome);
                        List<String> tip = new ArrayList<>();
                        tip.add("§6§l" + hoveredBiome.displayName() + " Biome");
                        tip.add("§7Uniques Captured: §a" + caught + "§7/§e" + total);
                        List<Critter> missing = Critters.inBiome(hoveredBiome).stream().filter(c -> !session.isComplete(c, uniqueOnly)).toList();
                        if (!missing.isEmpty()) {
                            tip.add("§cMissing: " + String.join(", ", missing.stream().map(Critter::name).toList()));
                        } else {
                            tip.add("§a✔ Biome Completed!");
                        }

                        int tipW = 0;
                        for (String t : tip) {
                            int w = font.width(t);
                            if (w > tipW) tipW = w;
                        }
                        int tipH = tip.size() * 10 + 6;
                        int tx = (int) mouseX + 10;
                        int ty = (int) mouseY + 10;

                        g.pose().pushMatrix();
                        g.fill(tx, ty, tx + tipW + 8, ty + tipH, 0xF0101015);
                        g.outline(tx, ty, tipW + 8, tipH, 0xFFFFAA00);
                        int lineY = ty + 4;
                        for (String t : tip) {
                            g.text(font, t, tx + 4, lineY, 0xFFFFFFFF, true);
                            lineY += 10;
                        }
                        g.pose().popMatrix();
                    }
                }
            }
        }
    }

    private static boolean isValidPlayer(String name, java.util.UUID uuid) {
        if (name == null || name.isEmpty() || name.startsWith("!") || name.startsWith("[NPC]")) return false;
        if (!name.matches("^[a-zA-Z0-9_]{3,16}$")) return false;
        if (uuid == null || uuid.version() != 4) return false;
        if (name.length() == 10 && name.matches("^[a-z0-9]+$") && !name.matches(".*[aeiou].*")) return false;
        return true;
    }

    private static void renderPlayerOnMap(GuiGraphicsExtractor g, Font font, PlayerInfo pInfo, String name, int px, int pz, String style, int color) {
        if ("Player Head".equalsIgnoreCase(style) || "Head + Name".equalsIgnoreCase(style)) {
            net.minecraft.resources.Identifier skinTex = null;
            if (pInfo != null) {
                try {
                    Object skinObj = pInfo.getSkin();
                    if (skinObj != null) {
                        for (java.lang.reflect.Method m : skinObj.getClass().getMethods()) {
                            if (m.getParameterCount() == 0 && net.minecraft.resources.Identifier.class.isAssignableFrom(m.getReturnType())) {
                                skinTex = (net.minecraft.resources.Identifier) m.invoke(skinObj);
                                if (skinTex != null) break;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (skinTex != null) {
                // Render face base layer (8x8 at 8,8 on 64x64 texture)
                g.blit(skinTex, px - 4, pz - 4, px + 4, pz + 4, 8.0f / 64.0f, 16.0f / 64.0f, 8.0f / 64.0f, 16.0f / 64.0f);
                // Render outer hat layer (8x8 at 40,8 on 64x64 texture)
                g.blit(skinTex, px - 4, pz - 4, px + 4, pz + 4, 40.0f / 64.0f, 48.0f / 64.0f, 8.0f / 64.0f, 16.0f / 64.0f);
                g.outline(px - 4, pz - 4, 8, 8, 0xFF000000);
            } else {
                g.fill(px - 4, pz - 4, px + 4, pz + 4, color);
                g.outline(px - 4, pz - 4, 8, 8, 0xFF000000);
            }

            if ("Head + Name".equalsIgnoreCase(style)) {
                String label = formatPlayerName(name);
                g.text(font, "§f" + label, px + 6, pz - 4, 0xFFFFFFFF, true);
            }
        } else {
            // Small Icon (Default 4x4 diamond/box icon)
            g.fill(px - 3, pz - 3, px + 3, pz + 3, color);
            g.outline(px - 3, pz - 3, 6, 6, 0xFF000000);
        }
    }

    public static String formatPlayerName(String name) {
        if (name == null || name.isEmpty()) return "";
        // If name ends in digits (e.g. zamasu12045), strip trailing digits if prefix has length >= 3
        if (name.matches("^[a-zA-Z_]{3,}\\d+$")) {
            return name.replaceAll("\\d+$", "");
        }
        // If name has interior digits/leetspeak (e.g. m4kia, PANT4LL4), keep intact!
        return name;
    }

    private static double mapCoord(double val, double min, double max, int screenDim) {
        return ((val - min) / (max - min)) * screenDim;
    }

    public static int getWidth() {
        return 120;
    }

    public static int getHeight() {
        return 120;
    }
}
