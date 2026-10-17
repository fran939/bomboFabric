package me.bombo.bomboaddons.features.misc;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks SkyBlock damage indicator armor stands and logs damage numbers to chat.
 * Supports:
 * - Private Island filtering
 * - Crits only filtering
 * - Minimum damage value threshold
 */
public class DamageTracker {

    private static final Pattern DAMAGE_PATTERN = Pattern.compile("^(?:[✧✯](\\d{1,3}(?:,\\d{3})*)[✧✯].?\\uFE0F?|(\\d{1,3}(?:,\\d{3})*).?\\uFE0F?)$");
    private static final Map<Integer, Integer> pendingStands = new HashMap<>();

    public static void onEntityLoad(int entityId) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.damageTrackerEnabled) return;
        if (s.damageTrackerOnlyIsland && !SkyblockUtils.isInPrivateIsland()) return;

        pendingStands.put(entityId, 0);
    }

    public static void onEntityUnload(int entityId) {
        pendingStands.remove(entityId);
    }

    public static void onClientTick() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.damageTrackerEnabled) {
            pendingStands.clear();
            return;
        }

        if (s.damageTrackerOnlyIsland && !SkyblockUtils.isInPrivateIsland()) {
            pendingStands.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            pendingStands.clear();
            return;
        }

        Iterator<Map.Entry<Integer, Integer>> it = pendingStands.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int age = entry.getValue();
            if (age >= 5) {
                it.remove();
                continue;
            }

            entry.setValue(age + 1);
            var entity = mc.level.getEntity(entry.getKey());
            if (entity instanceof ArmorStand stand) {
                if (processDamageStand(stand, s, mc)) {
                    it.remove();
                }
            } else {
                it.remove();
            }
        }
    }

    private static boolean processDamageStand(ArmorStand stand, BomboConfig.Settings s, Minecraft mc) {
        Component customName = stand.getCustomName();
        if (customName == null) return false;

        String raw = customName.getString();
        Matcher matcher = DAMAGE_PATTERN.matcher(raw);
        if (!matcher.matches()) return true;

        boolean isCrit = matcher.group(1) != null;
        if (s.damageTrackerOnlyCrits && !isCrit) return true;

        String numStr = isCrit ? matcher.group(1) : matcher.group(2);
        long damageVal;
        try {
            damageVal = Long.parseLong(numStr.replace(",", ""));
        } catch (NumberFormatException e) {
            return true;
        }

        if (damageVal >= s.damageTrackerThreshold) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§8[§cDamage§8] " + customName.getString()));
            }
        }

        return true;
    }
}
