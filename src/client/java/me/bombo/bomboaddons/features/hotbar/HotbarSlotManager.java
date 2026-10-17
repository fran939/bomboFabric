package me.bombo.bomboaddons.features.hotbar;

import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Handles active hotbar slot switching via /b hotbar & /hotbar:
 * - /b hotbar <1-9>: selects specific hotbar slot.
 * - /b hotbar <name>: finds item containing <name> on hotbar (0..8) and selects it.
 * - /b hotbar l:<lore> or lore:<lore>: finds item whose tooltip lore contains text and selects it.
 * - /b hotbar back: swaps back to the previous hotbar slot (toggles back and forth).
 */
public class HotbarSlotManager {

    private static int previousHotbarSlot = -1;

    public static void execute(String rawArg, Consumer<Component> feedback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String arg = rawArg != null ? rawArg.trim() : "";
        if (arg.startsWith("\"") && arg.endsWith("\"") && arg.length() >= 2) {
            arg = arg.substring(1, arg.length() - 1).trim();
        }

        int currentSlot = mc.player.getInventory().getSelectedSlot();

        // 1. No arguments: display current held slot + usage tip
        if (arg.isEmpty()) {
            ItemStack held = mc.player.getInventory().getItem(currentSlot);
            String itemDesc = !held.isEmpty() ? " §7(" + held.getHoverName().getString() + ")" : " §7(Empty)";
            feedback.accept(Component.literal("§8[§3Bombo§8] §aCurrent hotbar slot: §e" + (currentSlot + 1) + itemDesc
                    + " §8| §7Usage: §b/b hotbar <1-9|name|l:lore|back> §7(or §e/b swap§7 for swapper GUI)"));
            return;
        }

        // 2. /b hotbar back
        if (arg.equalsIgnoreCase("back")) {
            if (previousHotbarSlot < 0 || previousHotbarSlot > 8) {
                feedback.accept(Component.literal("§8[§3Bombo§8] §cNo previous hotbar slot recorded yet."));
                return;
            }
            int target = previousHotbarSlot;
            previousHotbarSlot = currentSlot;
            mc.player.getInventory().setSelectedSlot(target);
            ItemStack st = mc.player.getInventory().getItem(target);
            String itemDesc = !st.isEmpty() ? " §7(" + st.getHoverName().getString() + ")" : "";
            feedback.accept(Component.literal("§8[§3Bombo§8] §aSwitched back to hotbar slot §e" + (target + 1) + itemDesc));
            return;
        }

        // 3. /b hotbar <1-9>
        try {
            int num = Integer.parseInt(arg);
            if (num >= 1 && num <= 9) {
                int target = num - 1;
                if (target != currentSlot) {
                    previousHotbarSlot = currentSlot;
                }
                mc.player.getInventory().setSelectedSlot(target);
                ItemStack st = mc.player.getInventory().getItem(target);
                String itemDesc = !st.isEmpty() ? " §7(" + st.getHoverName().getString() + ")" : "";
                feedback.accept(Component.literal("§8[§3Bombo§8] §aSelected hotbar slot §e" + num + itemDesc));
                return;
            }
        } catch (NumberFormatException ignored) {}

        // 4. /b hotbar l:<lore> or /b hotbar lore:<lore>
        boolean isLoreQuery = arg.toLowerCase(Locale.ROOT).startsWith("l:") || arg.toLowerCase(Locale.ROOT).startsWith("lore:");
        if (isLoreQuery) {
            String query = arg.toLowerCase(Locale.ROOT).startsWith("lore:")
                    ? arg.substring(5).trim().toLowerCase(Locale.ROOT)
                    : arg.substring(2).trim().toLowerCase(Locale.ROOT);

            for (int i = 0; i < 9; i++) {
                ItemStack st = mc.player.getInventory().getItem(i);
                if (st.isEmpty()) continue;

                if (itemLoreContains(st, query)) {
                    if (i != currentSlot) {
                        previousHotbarSlot = currentSlot;
                    }
                    mc.player.getInventory().setSelectedSlot(i);
                    feedback.accept(Component.literal("§8[§3Bombo§8] §aSelected slot §e" + (i + 1)
                            + " §7(" + st.getHoverName().getString() + ") matching lore '§e" + query + "§7'"));
                    return;
                }
            }

            feedback.accept(Component.literal("§8[§3Bombo§8] §cNo item with lore matching '§e" + query + "§c' found on hotbar."));
            return;
        }

        // 5. /b hotbar <name> (Name or SkyBlock ID match)
        String query = arg.toLowerCase(Locale.ROOT);
        int matchedSlot = -1;
        ItemStack matchedStack = ItemStack.EMPTY;

        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (st.isEmpty()) continue;

            String name = st.getHoverName().getString().toLowerCase(Locale.ROOT);
            String sbId = SkyblockUtils.getSkyblockId(st);
            String sbIdLower = sbId != null ? sbId.toLowerCase(Locale.ROOT) : "";

            if (name.contains(query) || (!sbIdLower.isEmpty() && sbIdLower.contains(query))) {
                matchedSlot = i;
                matchedStack = st;
                break;
            }
        }

        if (matchedSlot != -1) {
            if (matchedSlot != currentSlot) {
                previousHotbarSlot = currentSlot;
            }
            mc.player.getInventory().setSelectedSlot(matchedSlot);
            feedback.accept(Component.literal("§8[§3Bombo§8] §aSelected slot §e" + (matchedSlot + 1)
                    + " §7(" + matchedStack.getHoverName().getString() + ")"));
            return;
        }

        feedback.accept(Component.literal("§8[§3Bombo§8] §cNo item matching '§e" + arg + "§c' found on hotbar."));
    }

    private static boolean itemLoreContains(ItemStack st, String query) {
        if (st == null || st.isEmpty()) return false;

        ItemLore loreComp = st.getComponents().get(DataComponents.LORE);
        if (loreComp != null) {
            for (Component line : loreComp.lines()) {
                if (line.getString().toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
        }

        CustomData cd = st.getComponents().get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            String tagStr = cd.copyTag().toString().toLowerCase(Locale.ROOT);
            if (tagStr.contains(query)) {
                return true;
            }
        }

        return false;
    }

    public static int getPreviousSlot() {
        return previousHotbarSlot;
    }
}
