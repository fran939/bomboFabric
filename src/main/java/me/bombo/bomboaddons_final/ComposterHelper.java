package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ComposterHelper {

    private static final Pattern VALUE_PATTERN = Pattern.compile("([\\d,]+(?:\\.\\d+)?)([kmb])?/([\\d,]+(?:\\.\\d+)?)([kmb])?");

    public static boolean onMouseClicked(AbstractContainerScreen<?> screen, Slot slot, int button) {
        if (slot == null || button != 0) return false;

        String title = screen.getTitle().getString();
        if (!title.contains("Composter")) return false;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return false;

        String name = stack.getHoverName().getString();
        if (name.contains("Organic Matter")) {
            handleComposterClick(stack, "BOX_OF_SEEDS", 25600);
            return true;
        } else if (name.contains("Fuel")) {
            handleComposterClick(stack, "SUNFLOWER_OIL", 20000);
            return true;
        }

        return false;
    }

    private static void handleComposterClick(ItemStack stack, String itemName, int perItem) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> tooltip = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        
        for (Component line : tooltip) {
            String text = line.getString().replaceAll("§.", "");
            Matcher matcher = VALUE_PATTERN.matcher(text);
            if (matcher.find()) {
                double current = parseValue(matcher.group(1), matcher.group(2));
                double max = parseValue(matcher.group(3), matcher.group(4));
                
                DebugUtils.debug("gui", "Composter: " + current + "/" + max + " (Missing: " + (max-current) + ")");

                double missing = max - current;
                if (missing > 0) {
                    int count = (int) Math.floor(missing / perItem);
                    DebugUtils.debug("gui", "Calculated " + count + " items needed (" + perItem + " per item)");
                    if (count > 0) {
                        DebugUtils.debug("command", "Runned: /gfs " + itemName + " " + count);
                        mc.player.connection.sendCommand("gfs " + itemName + " " + count);
                    }
                } else {
                    DebugUtils.debug("gui", "Composter is FULL, no action taken.");
                }
                break;
            }
        }
    }

    private static double parseValue(String valStr, String suffix) {
        double val = Double.parseDouble(valStr.replace(",", ""));
        if (suffix != null) {
            switch (suffix.toLowerCase()) {
                case "k" -> val *= 1000;
                case "m" -> val *= 1000000;
                case "b" -> val *= 1000000000;
            }
        }
        return val;
    }
}
