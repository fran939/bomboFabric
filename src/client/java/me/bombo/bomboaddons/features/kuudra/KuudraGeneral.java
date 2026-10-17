package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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

    /**
     * Handles keypress or mouse click in Kuudra Perk Menu or Confirm menu.
     */
    public static boolean handlePerkKeybind(AbstractContainerScreen<?> screen, int keyOrButton, boolean isMouse) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.perkMenuClicker) return false;
        if (!SkyblockUtils.isInKuudra()) return false;
        if (screen == null) return false;

        String title = screen.getTitle().getString().trim();
        String lowerTitle = title.toLowerCase(java.util.Locale.ROOT);
        boolean isPerkMenu = lowerTitle.contains("perk") || lowerTitle.contains("upgrade");
        boolean isConfirmMenu = lowerTitle.contains("are you sure") || lowerTitle.contains("confirm") || lowerTitle.contains("purchase") || lowerTitle.contains("buy");

        if (!isPerkMenu && !isConfirmMenu) return false;

        boolean matchesBallista = isKeyOrButtonMatch(s.perkBallistaKey, keyOrButton, isMouse);
        boolean matchesCannonball = isKeyOrButtonMatch(s.perkCannonballKey, keyOrButton, isMouse);

        if (!matchesBallista && !matchesCannonball) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;

        if (isConfirmMenu && (matchesBallista || matchesCannonball)) {
            int confirmSlot = findSlotWithTextOrItem(screen, "confirm", 11);
            if (confirmSlot != -1) {
                mc.gameMode.handleContainerInput(screen.getMenu().containerId, confirmSlot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                return true;
            }
        }

        if (isPerkMenu) {
            if (matchesBallista) {
                int ballistaSlot = findSlotWithTextOrItem(screen, "ballista", 13);
                if (ballistaSlot == -1) ballistaSlot = findSlotWithTextOrItem(screen, "specialist", 13);
                if (ballistaSlot != -1) {
                    mc.gameMode.handleContainerInput(screen.getMenu().containerId, ballistaSlot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                    return true;
                }
            } else if (matchesCannonball) {
                int cannonSlot = findSlotWithTextOrItem(screen, "cannonball", 25);
                if (cannonSlot == -1) cannonSlot = findSlotWithTextOrItem(screen, "human", 25);
                if (cannonSlot != -1) {
                    mc.gameMode.handleContainerInput(screen.getMenu().containerId, cannonSlot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                    return true;
                }
            }
        }

        return false;
    }

    private static int findSlotWithTextOrItem(AbstractContainerScreen<?> screen, String keyword, int defaultSlot) {
        String kw = keyword.toLowerCase(java.util.Locale.ROOT);
        for (Slot slot : screen.getMenu().slots) {
            if (slot.hasItem()) {
                String name = slot.getItem().getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
                if (name.contains(kw)) {
                    return slot.index;
                }
                String itemKey = slot.getItem().getItem().toString().toLowerCase(java.util.Locale.ROOT);
                if (kw.equals("confirm") && (name.contains("confirm") || name.contains("yes") || itemKey.contains("lime") || itemKey.contains("emerald") || itemKey.contains("green_stained"))) {
                    return slot.index;
                }
            }
        }
        if (defaultSlot >= 0 && defaultSlot < screen.getMenu().slots.size() && screen.getMenu().slots.get(defaultSlot).hasItem()) {
            return defaultSlot;
        }
        return -1;
    }

    public static boolean isKeyOrButtonMatch(String keyDef, int keyOrButton, boolean isMouse) {
        if (keyDef == null || keyDef.trim().isEmpty() || keyDef.equalsIgnoreCase("none")) return false;
        String clean = keyDef.trim().toLowerCase(java.util.Locale.ROOT);
        if (isMouse) {
            if (clean.equals("mouse1") || clean.equals("button1") || clean.equals("left_click")) return keyOrButton == 0;
            if (clean.equals("mouse2") || clean.equals("button2") || clean.equals("right_click")) return keyOrButton == 1;
            if (clean.equals("mouse3") || clean.equals("button3") || clean.equals("middle_click")) return keyOrButton == 2;
            if (clean.equals("mouse4") || clean.equals("button4")) return keyOrButton == 3;
            if (clean.equals("mouse5") || clean.equals("button5")) return keyOrButton == 4;
            if (clean.startsWith("mouse") || clean.startsWith("button")) {
                try {
                    int num = Integer.parseInt(clean.replaceAll("[^0-9]", ""));
                    if (num >= 1 && num <= 8) return (num - 1) == keyOrButton;
                } catch (Exception ignored) {}
            }
            return false;
        } else {
            if (clean.startsWith("mouse") || clean.startsWith("button")) return false;
            int code = me.bombo.bomboaddons.CustomBindsProcessor.getGlfwCodeForName(clean);
            if (code == -1) code = me.bombo.bomboaddons.ClickLogic.getKeyCode(clean);
            return code == keyOrButton;
        }
    }
}
