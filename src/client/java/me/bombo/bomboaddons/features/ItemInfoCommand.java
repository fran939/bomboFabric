package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.ItemCustomizeScreen;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

public class ItemInfoCommand {

    public static void execute() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            stack = player.getOffhandItem();
        }

        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cPlease hold an item in your hand to inspect its data!"));
            return;
        }

        String plainName = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        Component formattedName = stack.getHoverName();
        Identifier vanillaId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String sbId = SkyblockUtils.getSkyblockId(stack);
        String uuid = ItemCustomizeScreen.extractItemUuid(stack);
        CompoundTag extra = SkyblockUtils.getExtraAttributes(stack);

        String modifier = (extra != null && extra.contains("modifier")) ? extra.getString("modifier").orElse("None") : "None";
        String originTag = (extra != null && extra.contains("originTag")) ? extra.getString("originTag").orElse("None") : "None";

        // Determine Custom Model ID
        String customModelStr = "None (Vanilla)";
        if (sbId != null && !sbId.isEmpty()) {
            String lower = sbId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
            Identifier firmModel = Identifier.tryParse("firmskyblock:" + lower);
            if (firmModel != null) {
                Identifier override = CustomSkyblockModelRegistry.resolveOverride(firmModel, stack);
                if (override != null) {
                    customModelStr = override.toString() + " §a(Override matched)";
                } else if (!modifier.equals("None")) {
                    String modClean = modifier.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
                    Identifier modModel = Identifier.tryParse("firmskyblock:" + modClean + "_" + lower);
                    customModelStr = (modModel != null ? modModel.toString() : "None") + " §7(Reforge model)";
                } else {
                    customModelStr = firmModel.toString();
                }
            }
        }

        player.sendSystemMessage(Component.literal("§b§m--------------------------------------------------"));
        player.sendSystemMessage(Component.literal("§d§lITEM INSPECTION: ").append(formattedName));

        // Plain Name
        player.sendSystemMessage(createCopyableLine("  §7Display Name: ", "§f" + plainName, plainName));

        // Vanilla Item ID
        player.sendSystemMessage(createCopyableLine("  §7Vanilla ID: ", "§e" + vanillaId, vanillaId.toString()));

        // SkyBlock ID
        if (sbId != null && !sbId.isEmpty()) {
            player.sendSystemMessage(createCopyableLine("  §7SkyBlock ID: ", "§b" + sbId, sbId));
        } else {
            player.sendSystemMessage(Component.literal("  §7SkyBlock ID: §8None (Not a SkyBlock item)"));
        }

        // UUID
        if (uuid != null && !uuid.isEmpty()) {
            player.sendSystemMessage(createCopyableLine("  §7Item UUID: ", "§d" + uuid, uuid));
        } else {
            player.sendSystemMessage(Component.literal("  §7Item UUID: §8None"));
        }

        // Reforge / Modifier
        if (!modifier.equals("None")) {
            player.sendSystemMessage(createCopyableLine("  §7Reforge / Modifier: ", "§6" + modifier, modifier));
        }

        // Count / Damage
        int count = stack.getCount();
        int damage = stack.getDamageValue();
        int maxDamage = stack.getMaxDamage();
        String durInfo = maxDamage > 0 ? (" (Durability: " + (maxDamage - damage) + "/" + maxDamage + ")") : "";
        player.sendSystemMessage(Component.literal("  §7Count: §f" + count + durInfo));

        // Texture / Model Information
        player.sendSystemMessage(Component.literal("  §7Texture Information:"));
        player.sendSystemMessage(createCopyableLine("    §8• §7Vanilla Model: ", "§8" + vanillaId + "#inventory", vanillaId + "#inventory"));
        player.sendSystemMessage(createCopyableLine("    §8• §7SkyBlock Pack Model: ", "§a" + customModelStr, customModelStr));

        // Extra Attributes Summary
        if (extra != null && !extra.isEmpty()) {
            player.sendSystemMessage(Component.literal("  §7Extra Attributes: §8(" + extra.size() + " tags)"));
            for (String key : extra.keySet()) {
                if (key.equals("id") || key.equals("uuid") || key.equals("modifier")) continue;
                String val = extra.get(key) != null ? extra.get(key).toString() : "";
                if (val.length() > 60) val = val.substring(0, 57) + "...";
                player.sendSystemMessage(createCopyableLine("    §8• §7" + key + ": ", "§f" + val, val));
            }
        }

        // Copy Full NBT Button
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        String rawSnbt = customData != null ? customData.copyTag().toString() : "{}";
        MutableComponent copyNbtBtn = Component.literal("  §b§l[📋 COPY ALL ITEM NBT]§r")
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy raw SNBT to clipboard")))
                        .withClickEvent(new ClickEvent.CopyToClipboard(rawSnbt))
                );
        player.sendSystemMessage(copyNbtBtn);

        player.sendSystemMessage(Component.literal("§b§m--------------------------------------------------"));
    }

    private static Component createCopyableLine(String prefix, String displayValue, String copyValue) {
        MutableComponent line = Component.literal(prefix);
        MutableComponent valComp = Component.literal(displayValue);
        MutableComponent copyBadge = Component.literal(" §8[Copy]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.DARK_AQUA)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to copy §e" + copyValue)))
                        .withClickEvent(new ClickEvent.CopyToClipboard(copyValue))
                );
        line.append(valComp);
        line.append(copyBadge);
        return line;
    }
}
