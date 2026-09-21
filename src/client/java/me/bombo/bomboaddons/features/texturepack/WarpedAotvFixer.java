package me.bombo.bomboaddons.features.texturepack;

import me.bombo.bomboaddons.BomboConfig;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;

public class WarpedAotvFixer {

    public static List<String> getCandidates() {
        Set<String> set = new LinkedHashSet<>();

        // Known common candidates from popular packs (cittofirm, firmskyblock, furfsky, hypixel packs, etc.)
        set.add("firmskyblock:warped_aspect_of_the_void");
        set.add("firmskyblock:item/warped_aspect_of_the_void");
        set.add("cittofirmgenerated:warped_aspect_of_the_void");
        set.add("cittofirmgenerated:item/warped_aspect_of_the_void");
        set.add("firmskyblock:aspect_of_the_void");
        set.add("firmskyblock:item/aspect_of_the_void");
        set.add("cittofirmgenerated:aspect_of_the_void");
        set.add("cittofirmgenerated:item/aspect_of_the_void");
        set.add("minecraft:item/warped_aspect_of_the_void");
        set.add("minecraft:item/aspect_of_the_void");
        set.add("minecraft:warped_aspect_of_the_void");
        set.add("minecraft:aspect_of_the_void");

        // Collect all models from CustomSkyblockModelRegistry that contain void, aotv, or shovel
        if (CustomSkyblockModelRegistry.SIMPLE_MODELS != null) {
            for (Map.Entry<String, Identifier> entry : CustomSkyblockModelRegistry.SIMPLE_MODELS.entrySet()) {
                String k = entry.getKey().toLowerCase(Locale.ROOT);
                if (k.contains("aspect_of_the_void") || k.contains("aotv") || k.contains("warped")) {
                    set.add(entry.getValue().toString());
                    if (entry.getValue().getPath().startsWith("item/")) {
                        set.add(entry.getValue().getNamespace() + ":" + entry.getValue().getPath().substring("item/".length()));
                    } else {
                        set.add(entry.getValue().getNamespace() + ":item/" + entry.getValue().getPath());
                    }
                }
            }
        }

        // Vanilla diamond shovel fallbacks
        set.add("minecraft:item/diamond_shovel");
        set.add("minecraft:diamond_shovel");

        return new ArrayList<>(set);
    }

    public static int handleCommand(FabricClientCommandSource source, String action) {
        List<String> candidates = getCandidates();
        if (candidates.isEmpty()) {
            source.sendError(Component.literal("§8[§bBomboAddons§8] §cNo AOTV model candidates found in active packs."));
            return 0;
        }

        String current = BomboConfig.get().warpedAotvCustomModelOverride;
        if (action == null || action.trim().isEmpty() || action.equalsIgnoreCase("next")) {
            int curIdx = candidates.indexOf(current);
            int nextIdx = (curIdx + 1) % candidates.size();
            String chosen = candidates.get(nextIdx);
            BomboConfig.get().warpedAotvCustomModelOverride = chosen;
            BomboConfig.save();
            source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §aAOTV Model set to §e[" + (nextIdx + 1) + "/" + candidates.size() + "] §b" + chosen));
            source.sendFeedback(Component.literal("§7Hold your AOTV in hand/inventory to check! Run §e/b fixaotv next §7or §e/b fixaotv prev §7to keep cycling."));
            return 1;
        } else if (action.equalsIgnoreCase("prev") || action.equalsIgnoreCase("back")) {
            int curIdx = candidates.indexOf(current);
            if (curIdx < 0) curIdx = 0;
            int prevIdx = (curIdx - 1 + candidates.size()) % candidates.size();
            String chosen = candidates.get(prevIdx);
            BomboConfig.get().warpedAotvCustomModelOverride = chosen;
            BomboConfig.save();
            source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §aAOTV Model set to §e[" + (prevIdx + 1) + "/" + candidates.size() + "] §b" + chosen));
            return 1;
        } else if (action.equalsIgnoreCase("reset") || action.equalsIgnoreCase("clear") || action.equalsIgnoreCase("auto")) {
            BomboConfig.get().warpedAotvCustomModelOverride = "";
            BomboConfig.save();
            source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §aReset AOTV Model to §eAutomatic Detection§a."));
            return 1;
        } else if (action.equalsIgnoreCase("list")) {
            source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §6Available AOTV Model Options (" + candidates.size() + " total):"));
            for (int i = 0; i < candidates.size(); i++) {
                String c = candidates.get(i);
                boolean isActive = c.equalsIgnoreCase(current);
                source.sendFeedback(Component.literal("§7 " + (i + 1) + ". " + (isActive ? "§a§l[ACTIVE] " : "§8") + "§b" + c));
            }
            source.sendFeedback(Component.literal("§7Use §e/b fixaotv <index> §7to select a specific model."));
            return 1;
        } else {
            try {
                int idx = Integer.parseInt(action.trim()) - 1;
                if (idx >= 0 && idx < candidates.size()) {
                    String chosen = candidates.get(idx);
                    BomboConfig.get().warpedAotvCustomModelOverride = chosen;
                    BomboConfig.save();
                    source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §aAOTV Model set to §e[" + (idx + 1) + "/" + candidates.size() + "] §b" + chosen));
                    return 1;
                } else {
                    source.sendError(Component.literal("§8[§bBomboAddons§8] §cIndex out of range (1.." + candidates.size() + ")"));
                    return 0;
                }
            } catch (NumberFormatException e) {
                BomboConfig.get().warpedAotvCustomModelOverride = action.trim();
                BomboConfig.save();
                source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §aAOTV Model manually set to: §b" + action.trim()));
                return 1;
            }
        }
    }
}
