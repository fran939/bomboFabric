package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import me.bombo.bomboaddons_final.mixin.PlayerTabOverlayAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import java.util.Optional;

public class SkyblockUtils {

    public static String getLocation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "Menu";

        String loc = "Unknown";

        // 1. Try Scoreboard Sidebar
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            List<String> sbLines = getSidebarLines(scoreboard, sidebar);
            loc = parseAreaFromLines(sbLines);
            if (loc.equals("Unknown")) {
                // Check for Lobby marker in scoreboard
                for (String line : sbLines) {
                    if (line.toLowerCase().contains("hypixel.net")) {
                        loc = "Lobby";
                        break;
                    }
                }
            }
        } else {
            // No scoreboard sidebar - usually Limbo
            if (mc.getConnection() != null) {
                return "Limbo";
            }
        }

        // 2. Try Tab List Header/Footer
        if (loc.equals("Unknown") && mc.getConnection() != null) {
            List<Component> tabLines = getTabListLines();
            List<String> plainTabLines = new ArrayList<>();
            for (Component c : tabLines) plainTabLines.add(c.getString());
            
            loc = parseAreaFromLines(plainTabLines);
            if (loc.equals("Unknown")) {
                PlayerTabOverlayAccessor tabAccessor = (PlayerTabOverlayAccessor) mc.gui.getTabList();
                Component header = tabAccessor.getHeader();
                Component footer = tabAccessor.getFooter();
                if (header != null) {
                    loc = parseAreaFromLines(List.of(header.getString()));
                }
                if (loc.equals("Unknown") && footer != null) {
                    loc = parseAreaFromLines(List.of(footer.getString()));
                }
            }
        }

        // 3. Try fallback to mapping from Subarea
        if (loc.equals("Unknown")) {
            String sub = getSubArea();
            if (!sub.equals("None")) {
                String mapped = mapSubAreaToMainArea(sub);
                if (!mapped.equals("Unknown")) {
                    loc = mapped;
                }
            }
        }

        return loc;
    }
    
    public static String getSubArea() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (mc.screen != null) {
                String name = mc.screen.getClass().getSimpleName();
                if (name.equals("JoinMultiplayerScreen") || name.equals("MultiplayerScreen")) return "Multiplayer Menu";
                if (name.equals("TitleScreen")) return "Main Menu";
            }
            return "None";
        }

        // 1. Try Scoreboard Sidebar
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            List<String> sbLines = getSidebarLines(scoreboard, sidebar);
            String sub = parseSubAreaFromLines(sbLines);
            if (!sub.equals("None")) return sub;
        }

        // 2. Try Tab List Header/Footer
        if (mc.getConnection() != null) {
            List<Component> tabLines = getTabListLines();
            List<String> plainTabLines = new ArrayList<>();
            for (Component c : tabLines) plainTabLines.add(c.getString());
            
            String sub = parseSubAreaFromLines(plainTabLines);
            if (!sub.equals("None")) return sub;
            
            PlayerTabOverlayAccessor tabAccessor = (PlayerTabOverlayAccessor) mc.gui.getTabList();
            Component header = tabAccessor.getHeader();
            Component footer = tabAccessor.getFooter();
            if (header != null) {
                String s = parseSubAreaFromLines(List.of(header.getString()));
                if (!s.equals("None")) return s;
            }
            if (footer != null) {
                String s = parseSubAreaFromLines(List.of(footer.getString()));
                if (!s.equals("None")) return s;
            }
        }

        return "None";
    }

    private static String parseSubAreaFromLines(List<String> lines) {
        for (String line : lines) {
            String clean = line.replaceAll("(?i)§.", "").trim();
            
            // Dungeon Floor Detection (e.g. ⏣ The Catacombs (F7))
            if (clean.contains("The Catacombs (")) {
                int start = clean.indexOf("(") + 1;
                int end = clean.indexOf(")");
                if (start > 0 && end > start) {
                    return clean.substring(start, end);
                }
            }

            // Hypixel subareas usually start with these symbols
            if (clean.startsWith("⏣") || clean.startsWith("ф")) {
                return clean.substring(1).trim();
            }
        }
        return "None";
    }

    private static String parseAreaFromLines(List<String> lines) {
        for (String line : lines) {
            String clean = line.replaceAll("(?i)§.", "").trim();
            // Fuzzy search for common location markers
            if (clean.contains("Area:") || clean.contains("Zone:")) {
                return clean.substring(clean.indexOf(":") + 1).trim();
            }
            // Direct matches for common SkyBlock areas
            String lower = clean.toLowerCase();
            if (lower.contains("the garden") || lower.contains("garden")) return "The Garden";
            if (lower.contains("the hub") || lower.contains("hub")) return "The Hub";
            if (lower.contains("private island") || lower.contains("island")) return "Private Island";
            if (lower.contains("catacombs") || lower.contains("dungeon")) return "Dungeons";
            if (lower.contains("dwarven mines")) return "Dwarven Mines";
            if (lower.contains("crystal hollows")) return "Crystal Hollows";
            if (lower.contains("crimson isle")) return "Crimson Isle";
            if (lower.contains("spider's den")) return "Spider's Den";
            if (lower.contains("the end")) return "The End";
            if (lower.contains("the park")) return "The Park";
            if (lower.contains("deep caverns")) return "Deep Caverns";
            if (lower.contains("gold mine")) return "Gold Mine";
            if (lower.contains("the barn")) return "The Barn";
            if (lower.contains("mushroom desert")) return "Mushroom Desert";
            if (lower.contains("the rift") || lower.contains("rift")) return "The Rift";
            if (lower.contains("jerry's workshop") || lower.contains("jerry")) return "Jerry's Workshop";
            if (lower.contains("dark auction") || lower.contains("auction")) return "Dark Auction";
            if (lower.contains("limbo")) return "Limbo";
            if (lower.contains("lobby")) return "Lobby";
        }
        return "Unknown";
    }

    public static String mapSubAreaToMainArea(String sub) {
        if (sub == null || sub.isEmpty() || sub.equalsIgnoreCase("None")) return "Unknown";
        String lower = sub.toLowerCase();
        
        if (lower.contains("garden") || lower.contains("plot")) return "The Garden";
        if (lower.contains("island")) return "Private Island";
        if (lower.contains("catacombs") || lower.contains("dungeon") || 
            lower.matches("^(f[1-7]|m[1-7])$")) return "Dungeons";
        if (lower.contains("mines") || lower.contains("dwarven")) return "Dwarven Mines";
        if (lower.contains("hollows") || lower.contains("crystal")) return "Crystal Hollows";
        if (lower.contains("crimson") || lower.contains("isle")) return "Crimson Isle";
        if (lower.contains("spider") || lower.contains("den")) return "Spider's Den";
        if (lower.contains("end")) return "The End";
        if (lower.contains("park")) return "The Park";
        if (lower.contains("cavern") || lower.contains("deep")) return "Deep Caverns";
        if (lower.contains("gold") || lower.contains("mine")) return "Gold Mine";
        if (lower.contains("barn")) return "The Barn";
        if (lower.contains("desert") || lower.contains("mushroom")) return "Mushroom Desert";
        if (lower.contains("rift")) return "The Rift";
        if (lower.contains("jerry") || lower.contains("workshop")) return "Jerry's Workshop";
        if (lower.contains("auction") || lower.contains("dark")) return "Dark Auction";
        
        // Hub subareas
        if (lower.contains("village") || lower.contains("ruins") || lower.contains("high level") || 
            lower.contains("forest") || lower.contains("mountain") || lower.contains("wilderness") || 
            lower.contains("graveyard") || lower.contains("coal mine") || lower.contains("bazaar") || 
            lower.contains("community center") || lower.contains("farm") || lower.contains("hut")) {
            return "The Hub";
        }
        
        return "Unknown";
    }

    public static boolean isInGarden() {
        String loc = getLocation();
        return loc.equalsIgnoreCase("The Garden") || loc.toLowerCase().contains("garden");
    }

    public static List<String> getSidebarLines(Scoreboard scoreboard, Objective objective) {
        List<String> lines = new ArrayList<>();
        scoreboard.listPlayerScores(objective).forEach(score -> {
            String ownerName = score.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(ownerName);
            if (team != null) {
                Component fullName = Component.empty()
                    .append(team.getPlayerPrefix())
                    .append(Component.literal(ownerName))
                    .append(team.getPlayerSuffix());
                lines.add(fullName.getString());
            } else {
                lines.add(ownerName);
            }
        });
        Collections.reverse(lines);
        return lines;
    }

    public static List<Component> getTabListLines() {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        if (mc.getConnection() == null) return lines;

        Collection<PlayerInfo> players = mc.getConnection().getOnlinePlayers();
        for (PlayerInfo info : players) {
            Component displayName = info.getTabListDisplayName();
            if (displayName != null) {
                lines.add(displayName);
            } else if (info.getProfile() != null && info.getProfile().name() != null) {
                lines.add(Component.literal(info.getProfile().name()));
            }
        }
        return lines;
    }

    public static String getInternalId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            // Try ExtraAttributes.id
            CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (ea != null) {
                String id = ea.getString("id").orElse("");
                // Special handling for enchanted books to get bazaar price
                if (id.equals("ENCHANTED_BOOK")) {
                    // Skyblock uses "enchantments" tag inside ExtraAttributes
                    CompoundTag enchants = ea.getCompound("enchantments").orElse(null);
                    if (enchants == null) enchants = ea.getCompound("enchantment").orElse(null); // fallback
                    
                    if (enchants != null && !enchants.keySet().isEmpty()) {
                        String name = enchants.keySet().iterator().next();
                        int level = enchants.getInt(name).orElse(1);
                        return "ENCHANTMENT_" + name.toUpperCase() + "_" + level;
                    }
                }
                return id;
            }
            // Fallback to direct id in tag
            return tag.getString("id").orElse("");
        }
        return "";
    }

    public static List<Component> getLore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        net.minecraft.world.item.component.ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            return lore.lines();
        }
        return Collections.emptyList();
    }
}
