package me.bombo.bomboaddons;

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
import me.bombo.bomboaddons.mixin.PlayerTabOverlayAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import java.util.Optional;

public class SkyblockUtils {

    public static boolean isConnectedToHypixel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            String ip = mc.getCurrentServer().ip.toLowerCase();
            return ip.contains("hypixel.net") || ip.contains("hypixel.io");
        }
        return false;
    }

    public static String mapLocrawToArea(String mode, String map) {
        if (mode == null) mode = "";
        if (map == null) map = "";
        
        String lowerMode = mode.toLowerCase();
        String lowerMap = map.toLowerCase();
        
        if (lowerMode.contains("dungeon_hub") || lowerMap.contains("dungeon hub")) return "Dungeon Hub";
        if (lowerMode.contains("kuudra") || lowerMap.contains("kuudra")) return "Kuudra's Hollow";
        if (lowerMode.contains("garden") || lowerMap.contains("garden")) return "The Garden";
        if (lowerMode.contains("hub") || lowerMap.contains("hub")) return "The Hub";
        if (lowerMap.contains("private island") || lowerMode.contains("island")) return "Private Island";
        if (lowerMode.contains("dungeon") || lowerMap.contains("dungeon") || lowerMap.contains("catacombs")) return "Dungeons";
        if (lowerMode.contains("mines") || lowerMap.contains("dwarven mines")) return "Dwarven Mines";
        if (lowerMode.contains("crystal_hollows") || lowerMap.contains("crystal hollows")) return "Crystal Hollows";
        if (lowerMode.contains("crimson_isle") || lowerMap.contains("crimson isle")) return "Crimson Isle";
        if (lowerMode.contains("spider") || lowerMap.contains("spider's den")) return "Spider's Den";
        if (lowerMode.contains("end") || lowerMap.contains("the end")) return "The End";
        if (lowerMode.contains("park") || lowerMap.contains("the park")) return "The Park";
        if (lowerMode.contains("caverns") || lowerMap.contains("deep caverns")) return "Deep Caverns";
        if (lowerMode.contains("gold") || lowerMap.contains("gold mine")) return "Gold Mine";
        if (lowerMode.contains("farming") || lowerMap.contains("farming") ||
            lowerMode.contains("barn") || lowerMap.contains("barn") || 
            lowerMode.contains("desert") || lowerMap.contains("desert") || lowerMap.contains("mushroom")) return "Farming Islands";
        if (lowerMode.contains("rift") || lowerMap.contains("the rift")) return "The Rift";
        if (lowerMode.contains("jerry") || lowerMap.contains("jerry's workshop")) return "Jerry's Workshop";
        if (lowerMode.contains("auction") || lowerMap.contains("dark auction")) return "Dark Auction";
        
        // Fallback
        List<String> list = new ArrayList<>();
        list.add(map);
        list.add(mode);
        String parsed = parseAreaFromLines(list);
        if (!parsed.equals("Unknown")) return parsed;
        
        return "Unknown";
    }

    public static String getLocation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "Menu";

        // Prioritize locraw if on Hypixel SkyBlock
        if (isConnectedToHypixel() && "SKYBLOCK".equals(BomboaddonsClient.locrawGametype)) {
            String area = mapLocrawToArea(BomboaddonsClient.locrawMode, BomboaddonsClient.locrawMap);
            if (!area.equals("Unknown")) {
                return area;
            }
        }

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

            // Hypixel subareas usually start with these symbols (⏏ U+23CF, ⏣ U+23E3, or o U+0444)
            if (clean.startsWith("\u23CF") || clean.startsWith("\u23E3") || clean.startsWith("⏣") || clean.startsWith("\u0444")) {
                String sub = clean.substring(1).trim();
                if (sub.toLowerCase().contains("kuudra")) {
                    if (sub.contains("(T")) {
                        int idx = sub.indexOf("(T");
                        if (idx + 2 < sub.length()) {
                            char c = sub.charAt(idx + 2);
                            if (c >= '1' && c <= '5') {
                                return "T" + c;
                            }
                        }
                    }
                }
                return sub;
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
            if (lower.contains("dungeon hub")) return "Dungeon Hub";
            if (lower.contains("kuudra's hollow") || lower.contains("kuudra")) {
                return "Kuudra's Hollow";
            }
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
            if (lower.contains("farming") || lower.contains("the barn") || lower.contains("barn") || 
                lower.contains("mushroom desert") || lower.contains("desert")) return "Farming Islands";
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
        
        // 1. Dungeons
        if (lower.contains("catacombs") || lower.contains("dungeon") || 
            lower.matches("^(f[1-7]|m[1-7])$")) return "Dungeons";
            
        // 2. Kuudra
        if (lower.matches("^(t[1-5])$") || lower.contains("kuudra")) return "Kuudra's Hollow";
        
        // 3. Farming Islands (The Barn & Mushroom Desert & their sub-locations)
        if (lower.contains("barn") || lower.contains("desert") || lower.contains("mushroom") || 
            lower.contains("oasis") || lower.contains("windmill") || lower.contains("trevor") || 
            lower.contains("shepherd") || lower.contains("jake")) return "Farming Islands";
            
        // 4. Dwarven Mines (make sure it's checked before Gold Mine because of "mines")
        if (lower.contains("dwarven") || lower.contains("forge") || lower.contains("goblin") || 
            lower.contains("royal") || lower.contains("palace") || lower.contains("cliffside") ||
            lower.contains("copcoils") || lower.contains("rampart") || lower.contains("aristocrat") ||
            lower.contains("hanging court")) return "Dwarven Mines";
            
        // 5. Crystal Hollows (check before other general ones)
        if (lower.contains("hollows") || lower.contains("nucleus") || lower.contains("precursor") || 
            lower.contains("khazad") || lower.contains("grotto") || lower.contains("mithril deposits")) return "Crystal Hollows";
            
        // 6. Deep Caverns
        if (lower.contains("cavern") || lower.contains("deep") || lower.contains("gunpowder") || 
            lower.contains("lapis") || lower.contains("pigman") || lower.contains("slimehill") || 
            lower.contains("obsidian") || lower.contains("diamond reserve")) return "Deep Caverns";
            
        // 7. Gold Mine (check after Coal Mine/Dwarven Mines/Mithril/etc)
        if (lower.contains("gold")) return "Gold Mine";
        
        // 8. The Park
        if (lower.contains("park") || lower.contains("spruce") || lower.contains("birch") || 
            lower.contains("savanna") || lower.contains("howling") || lower.contains("melancholy") || 
            lower.contains("dark thicket")) return "The Park";
            
        // 9. Spider's Den
        if (lower.contains("spider") || lower.contains("den") || lower.contains("arachne") || 
            lower.contains("archaeologist")) return "Spider's Den";
            
        // 10. The End
        if (lower.contains("end") || lower.contains("nest") || lower.contains("sepulture") || 
            lower.contains("zealot")) return "The End";
            
        // 11. Crimson Isle
        if (lower.contains("crimson") || lower.contains("isle") || lower.contains("scarleton") || 
            lower.contains("dragontail") || lower.contains("ashfang") || lower.contains("lest") || 
            lower.contains("marsh") || lower.contains("bastion") || lower.contains("smoldering") || 
            lower.contains("aurea")) return "Crimson Isle";
            
        // 12. The Rift
        if (lower.contains("rift") || lower.contains("wyld") || lower.contains("lagoon") || 
            lower.contains("colosseum reborn") || lower.contains("dreadfarm") || 
            lower.contains("westbridge") || lower.contains("otherside") || 
            lower.contains("mirror") || lower.contains("gallery") || lower.contains("village plaza")) return "The Rift";
            
        // 13. Jerry's Workshop
        if (lower.contains("jerry")) return "Jerry's Workshop";
        
        // 14. Private Island
        if (lower.contains("island") || lower.contains("plot")) return "Private Island";
        
        // 15. The Garden
        if (lower.contains("garden")) return "The Garden";
        
        // 16. The Hub
        if (lower.contains("village") || lower.contains("ruins") || lower.contains("high level") || 
            lower.contains("forest") || lower.contains("mountain") || lower.contains("wilderness") || 
            lower.contains("graveyard") || lower.contains("coal") || lower.contains("bazaar") || 
            lower.contains("community center") || lower.contains("farm") || lower.contains("hut") || 
            lower.contains("canvas") || lower.contains("carnival") || lower.contains("colosseum") || 
            lower.contains("election") || lower.contains("blacksmith") || lower.contains("auction") || 
            lower.contains("bank") || lower.contains("abiphone") || lower.contains("library") || 
            lower.contains("thaumaturgist") || lower.contains("sewer") || lower.contains("museum") || 
            lower.contains("taylor") || lower.contains("seymour") || lower.contains("shen") || 
            lower.contains("elise") || lower.contains("wizard") || lower.contains("flower house")) {
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
        
        // Try vanilla STORED_ENCHANTMENTS (or ENCHANTMENTS) fallback for enchanted books
        if (stack.getItem().toString().contains("enchanted_book")) {
            net.minecraft.world.item.enchantment.ItemEnchantments enchants = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (enchants == null) {
                enchants = stack.get(DataComponents.ENCHANTMENTS);
            }
            if (enchants != null && !enchants.isEmpty()) {
                for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder : enchants.keySet()) {
                    int level = enchants.getLevel(holder);
                    String path = holder.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
                    if (!path.isEmpty()) {
                        return "ENCHANTMENT_" + path.toUpperCase() + "_" + level;
                    }
                }
            }
        }
        
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            CompoundTag searchTag = tag;
            
            // Try ExtraAttributes.id
            CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (ea != null) {
                searchTag = ea;
            }
            
            String id = searchTag.getString("id").orElse("");
            if (id.equals("ENCHANTED_BOOK")) {
                CompoundTag enchants = searchTag.getCompound("enchantments").orElse(null);
                if (enchants == null) enchants = searchTag.getCompound("enchantment").orElse(null);
                
                if (enchants != null && !enchants.keySet().isEmpty()) {
                    String name = enchants.keySet().iterator().next();
                    int level = enchants.getInt(name).orElse(1);
                    return "ENCHANTMENT_" + name.toUpperCase() + "_" + level;
                }
            }
            if (id.equals("PET")) {
                String petInfoStr = searchTag.getString("petInfo").orElse("");
                if (!petInfoStr.isEmpty()) {
                    try {
                        com.google.gson.JsonObject petObj = com.google.gson.JsonParser.parseString(petInfoStr).getAsJsonObject();
                        if (petObj.has("type") && petObj.has("tier")) {
                            String type = petObj.get("type").getAsString();
                            String tier = petObj.get("tier").getAsString();
                            return "PET-" + type + "-" + tier;
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (!id.isEmpty()) {
                return id;
            }
            return tag.getString("id").orElse("");
        }
        return "";
    }

    public static String getInternalIdRaw(ItemStack stack) {
        if (stack == null) return "";
        CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (ea != null) {
                return ea.getString("id").orElse("");
            }
            return tag.getString("id").orElse("");
        }
        return "";
    }

    public static String getInternalIdRaw(net.minecraft.core.component.DataComponentMap map) {
        if (map == null) return "";
        net.minecraft.world.item.component.CustomData customData = map.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (ea != null) {
                return ea.getString("id").orElse("");
            }
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
