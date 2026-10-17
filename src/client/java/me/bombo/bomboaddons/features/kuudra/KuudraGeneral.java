package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/**
 * Kuudra General quality of life features:
 * - Block Useless Perks: Prevents clicking dead perks in perk menu unless Ctrl is held.
 * - Block SkyBlock Menu: Prevents accidentally opening SkyBlock menu in hotbar slot 8 during Kuudra runs.
 * - Hide Mob Nametags: Suppresses [Lv...] tags above Kuudra mobs to reduce visual clutter.
 * - Hide Useless Armor Stands: Cleans up empty/dead/nameless armor stands in Kuudra.
 * - Hide Death Animation: Instantly suppresses rendering dead or dying entities.
 * - Scale Kuudra Mobs: Configurable scale multiplier for Kuudra combat mobs.
 */
public class KuudraGeneral {

    private static final Set<String> BLOCKED_PERKS = Set.of(
            "steady hands", "bomberman", "mining frenzy", "elle's lava rod", "elle's pickaxe", "auto revive"
    );

    /**
     * Checks if clicking a slot in a container screen should be blocked (Block Useless Perks).
     */
    public static boolean shouldBlockSlotClick(Screen screen, Slot slot) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.blockUselessPerks) return false;
        if (!SkyblockUtils.isInKuudra()) return false;
        if (isCtrlKeyDown()) return false; // Hold Ctrl to bypass

        if (screen == null || slot == null) return false;
        String title = screen.getTitle().getString().toLowerCase();
        if (!title.contains("perk") && !title.contains("upgrade")) return false;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return false;

        String name = stack.getHoverName().getString().trim().toLowerCase();
        // Strip roman numerals (e.g. "Steady Hands II" -> "steady hands")
        name = stripRomanSuffix(name);

        return BLOCKED_PERKS.contains(name);
    }

    /**
     * Checks if interacting with slot 8 (SkyBlock Menu) should be blocked during Kuudra.
     */
    public static boolean shouldBlockSkyblockMenu() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.blockSkyblockMenu) return false;
        if (!SkyblockUtils.isInKuudra()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        if (mc.player.getInventory().getSelectedSlot() == 8) {
            ItemStack stack = mc.player.getInventory().getItem(8);
            if (stack.isEmpty()) return false;

            String sbId = SkyblockUtils.getSkyblockId(stack);
            if ("SKYBLOCK_MENU".equalsIgnoreCase(sbId)) return true;
            if (stack.is(Items.NETHER_STAR) || stack.is(Items.COMPASS)) return true;
        }

        return false;
    }

    /**
     * Checks if a mob nametag should be hidden (Hide Mob Nametags).
     */
    public static boolean shouldHideMobNametag(String name) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.hideMobNametags) return false;
        if (!SkyblockUtils.isInKuudra()) return false;

        if (name == null) return false;
        String clean = name.trim();
        return clean.startsWith("[Lv") || clean.startsWith("§8[§7Lv");
    }

    /**
     * Checks if an armor stand should be hidden (Hide Useless Armor Stands).
     */
    public static boolean shouldHideArmorStand(ArmorStand stand) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.hideUselessArmorStands) return false;
        if (!SkyblockUtils.isInKuudra()) return false;
        if (stand == null) return false;

        if (stand.isDeadOrDying()) return true;

        if (stand.isInvisible()) {
            if (!stand.hasCustomName()) return true;
            String name = stand.getName().getString().trim();
            if (name.isEmpty() || name.equalsIgnoreCase("Armor Stand")) return true;
            if (name.startsWith("[Lv")) return true;
        }

        return false;
    }

    /**
     * Checks if an entity's death animation should be suppressed (Hide Death Animation).
     */
    public static boolean shouldHideDeathAnimation(LivingEntity living) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.hideDeathAnimation) return false;
        if (!SkyblockUtils.isInKuudra()) return false;
        return living != null && living.isDeadOrDying();
    }

    /**
     * Checks if an entity qualifies for Kuudra mob scaling.
     */
    public static boolean shouldScaleKuudraMob(Entity entity) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.scaleKuudraMobs) return false;
        if (!SkyblockUtils.isInKuudra()) return false;
        String typeStr = entity.getType().toString().toLowerCase(java.util.Locale.ROOT);
        if (typeStr.contains("zombie") && !typeStr.contains("piglin") && entity instanceof LivingEntity living) {
            return !living.getItemBySlot(EquipmentSlot.FEET).isEmpty()
                    || !living.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                    || !living.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        }

        return typeStr.contains("zombified_piglin")
                || typeStr.contains("wither_skeleton")
                || typeStr.contains("iron_golem");
    }

    public static float getKuudraMobScale() {
        BomboConfig.Settings s = BomboConfig.get();
        return (s != null && s.kuudraMobsSize > 0.05F) ? s.kuudraMobsSize : 0.6F;
    }

    private static boolean isCtrlKeyDown() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.hasControlDown();
    }

    private static String stripRomanSuffix(String s) {
        if (s == null || s.length() < 3) return s != null ? s : "";
        int i = s.length() - 1;
        while (i >= 0 && isRoman(s.charAt(i))) {
            i--;
        }
        if (i >= 0 && s.charAt(i) == ' ') {
            return s.substring(0, i).trim();
        }
        return s;
    }

    private static boolean isRoman(char c) {
        char upper = Character.toUpperCase(c);
        return upper == 'I' || upper == 'V' || upper == 'X';
    }
}
