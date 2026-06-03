package me.bombo.bomboaddons_final.kuudra.pearls;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.DisplaySlot;
import me.bombo.bomboaddons_final.BomboConfig;
import me.bombo.bomboaddons_final.BomboaddonsClient;
import me.bombo.bomboaddons_final.SkyblockUtils;

import java.util.ArrayList;
import java.util.List;

public class KuudraUtils {
    private static ArmorStand elleEntity;
    private static MagmaCube kuudraEntity;

    private static final List<MagmaCube> magmaCubes = new ArrayList<>();
    private static final List<Vec3> crates = new ArrayList<>();
    
    private static final Supply[] supplies = {
            new Supply(SupplySpot.SUPPLY1),
            new Supply(SupplySpot.SUPPLY2),
            new Supply(SupplySpot.SUPPLY3),
            new Supply(SupplySpot.SUPPLY4),
            new Supply(SupplySpot.SUPPLY5),
            new Supply(SupplySpot.SUPPLY6),
    };

    private static boolean dirty = false;
    private static int scanCounter = 0;

    public static boolean inKuudra() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        if (mc.isLocalServer()) return true;

        String area = BomboaddonsClient.currentArea;
        if ("Kuudra".equalsIgnoreCase(area)) return true;
        if (area != null && area.matches("^T[1-5]$")) return true;

        // Fallback: check scoreboard lines for "Kuudra"
        Objective sidebar = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            for (String line : SkyblockUtils.getSidebarLines(mc.level.getScoreboard(), sidebar)) {
                if (line.toLowerCase().contains("kuudra")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int getKuudraTier() {
        String area = BomboaddonsClient.currentArea;
        if (area != null && area.matches("^T[1-5]$")) {
            return Character.getNumericValue(area.charAt(1));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Objective sidebar = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null) {
                for (String line : SkyblockUtils.getSidebarLines(mc.level.getScoreboard(), sidebar)) {
                    String clean = line.replaceAll("(?i)§.", "").trim();
                    if (clean.contains("(T")) {
                        int idx = clean.indexOf("(T");
                        if (idx + 2 < clean.length()) {
                            char c = clean.charAt(idx + 2);
                            if (c >= '1' && c <= '5') {
                                return Character.getNumericValue(c);
                            }
                        }
                    }
                }
            }
        }
        return BomboConfig.get().kuudraTiers; // Fallback to config if not found
    }

    public static void onWorldUnload() {
        if (dirty) {
            dirty = false;
            elleEntity = null;
            kuudraEntity = null;
            magmaCubes.clear();
            crates.clear();
            resetSupplies();
        }
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (!inKuudra()) {
            if (dirty) {
                onWorldUnload();
            }
            return;
        }

        scanCounter++;
        if (scanCounter % 4 != 0 && dirty) {
            return;
        }

        magmaCubes.clear();
        crates.clear();
        dirty = true;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != null) {
                processEntity(entity);
            }
        }
    }

    private static void processEntity(Entity entity) {
        if (entity instanceof ArmorStand) {
            String name = entity.getName().getString();
            if (name == null || name.isEmpty()) return;
            name = name.replaceAll("§[0-9a-fk-or]", ""); // Strip formatting codes

            if ("Elle".equals(name)) {
                elleEntity = (ArmorStand) entity;
                return;
            }

            int matchedSpot = -1;
            double bestDistSq = 4.0; // Check within 2.0 blocks horizontally
            for (int idx = 0; idx < supplies.length; idx++) {
                SupplySpot spot = supplies[idx].getSpot();
                double dx = entity.getX() - spot.getLocation().x;
                double dz = entity.getZ() - spot.getLocation().z;
                double distSq = dx * dx + dz * dz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    matchedSpot = idx;
                }
            }
            if (matchedSpot != -1) {
                processSupply(matchedSpot, name);
            }
        } else if (entity instanceof MagmaCube magmaCube) {
            magmaCubes.add(magmaCube);
            if (isKuudraEntity(magmaCube)) kuudraEntity = magmaCube;
        } else if (entity instanceof Giant) {
            if (entity.getY() < 67) {
                final float yawRad = (float) ((entity.getYRot() + 130.0f) * (Math.PI / 180.0));
                final double offsetX = 3.7 * Math.cos(yawRad);
                final double offsetZ = 3.7 * Math.sin(yawRad);
                final double x = entity.getX() + 0.5 + offsetX;
                final double z = entity.getZ() + 0.5 + offsetZ;
                crates.add(new Vec3(x, 75, z));
            }
        }
    }

    private static void processSupply(int pos, String name) {
        SupplyStatus status = getStatusFromName(name);
        if (status == null) return;
        supplies[pos].setStatus(status);

        if (status == SupplyStatus.INPROGRESS && name.startsWith("PROGRESS:")) {
            try {
                String percentStr = name.substring("PROGRESS:".length()).replace("%", "").trim();
                float percent = Float.parseFloat(percentStr);
                supplies[pos].setProgressColor(getProgressColor(percent));
            } catch (NumberFormatException ignored) {}
        }
    }

    private static SupplyStatus getStatusFromName(String name) {
        if (name.contains("BRING SUPPLY CHEST HERE")) return SupplyStatus.NOTHING;
        if (name.contains("SUPPLIES RECEIVED")) return SupplyStatus.RECEIVED;
        if (name.contains("PROGRESS: ")) return name.contains("COMPLETE") ? SupplyStatus.COMPLETED : SupplyStatus.INPROGRESS;
        return null;
    }

    private static float[] getProgressColor(float percent) {
        percent = Math.max(0, Math.min(100, percent));
        float ratio = (float) Math.pow(percent / 100f, 1.5f);
        float red = 1.0f - ratio;
        return new float[] { red, ratio };
    }

    private static boolean isKuudraEntity(MagmaCube magmaCube) {
        return magmaCube.getBbWidth() > 14 && magmaCube.getHealth() <= 100000;
    }

    private static void resetSupplies() {
        for (Supply supply : supplies) supply.reset();
    }

    public static ArmorStand getElle() { return elleEntity; }
    public static MagmaCube getKuudra() { return kuudraEntity; }

    public static List<Vec3> getCrates() { return crates; }
    public static List<MagmaCube> getMagmaCubes() { return magmaCubes; }
    public static Supply[] getSupplies() { return supplies; }

    public static List<Vec3> getAllUncompletedSupplies() {
        List<Vec3> uncompleted = new ArrayList<>();
        for (Supply supply : supplies) {
            if (supply.getStatus() == SupplyStatus.NOTHING) {
                uncompleted.add(supply.getSpot().getLocation());
            }
        }
        return uncompleted;
    }
}
