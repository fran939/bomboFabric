package me.bombo.bomboaddons;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

public class TabCompletionManager {
    public static final Set<String> friends = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<String> guild = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<String> party = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final java.nio.file.Path FILE_PATH = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("bombo/autocomplete.json");
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    public static class AutocompleteData {
        public Set<String> friends = new HashSet<>();
        public Set<String> guild = new HashSet<>();
    }

    public static void load() {
        try {
            if (java.nio.file.Files.exists(FILE_PATH)) {
                try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(FILE_PATH)) {
                    AutocompleteData data = GSON.fromJson(reader, AutocompleteData.class);
                    if (data != null) {
                        if (data.friends != null) {
                            friends.addAll(data.friends);
                        }
                        if (data.guild != null) {
                            guild.addAll(data.guild);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void save() {
        try {
            if (!java.nio.file.Files.exists(FILE_PATH.getParent())) {
                java.nio.file.Files.createDirectories(FILE_PATH.getParent());
            }
            AutocompleteData data = new AutocompleteData();
            data.friends.addAll(friends);
            data.guild.addAll(guild);
            try (java.io.Writer writer = java.nio.file.Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void onChatMessage(String rawMessage) {
        if (rawMessage == null) return;
        String clean = rawMessage.replaceAll("§.", "").trim();
        if (clean.isEmpty()) return;

        // Split by newlines in case it's a multi-line message
        String[] lines = clean.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String trimmedLower = trimmed.toLowerCase();

            // 1. Parse Guild Join/Leave: "Guild > pavlor left." or "Guild > pavlor joined."
            // 2. Parse Friend Join/Leave: "Friends > pavlor left." or "Friends > pavlor joined."
            Matcher mJoinLeave = Pattern.compile("^(Friends|Guild)\\s*>\\s*(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})\\s+(joined|left)\\.", Pattern.CASE_INSENSITIVE).matcher(trimmed);
            if (mJoinLeave.find()) {
                String type = mJoinLeave.group(1).toLowerCase();
                String name = mJoinLeave.group(2);
                if (type.equals("friends")) {
                    if (friends.add(name)) {
                        save();
                    }
                } else if (type.equals("guild")) {
                    if (guild.add(name)) {
                        save();
                    }
                }
                continue;
            }

            // 3. Friend list entries (e.g. from `/f list` pages):
            // "Chimera_V is in SkyBlock - ..."
            // "tako* is currently offline"
            Matcher mFriendEntry = Pattern.compile("^([a-zA-Z0-9_]{3,16})\\*?\\s+is\\s+(?:in|currently\\s+offline)", Pattern.CASE_INSENSITIVE).matcher(trimmed);
            if (mFriendEntry.find()) {
                if (friends.add(mFriendEntry.group(1))) {
                    save();
                }
                continue;
            }

            // 4. Guild list entries (e.g. from `/g list` output with list bullet points '●'):
            // "Phoenix_12200 ●  [MVP+] ElSansESP ●  ..."
            if (trimmed.contains("●") && !trimmedLower.startsWith("party leader:") && !trimmedLower.startsWith("party moderators:") && !trimmedLower.startsWith("party members:")) {
                String[] parts = trimmed.split("●");
                boolean addedAny = false;
                for (String part : parts) {
                    String name = part.replaceAll("\\[[^\\]]+\\]", "").trim();
                    if (name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                        if (guild.add(name)) {
                            addedAny = true;
                        }
                    }
                }
                if (addedAny) {
                    save();
                }
                continue;
            }

            // 5. Party parsing:
            // "Party Leader: [MVP+] Player1"
            // "Party Moderators: [VIP] Player2"
            // "Party Members: Player3"
            if (trimmedLower.startsWith("party leader:") || trimmedLower.startsWith("party moderators:") || trimmedLower.startsWith("party members:")) {
                int colonIdx = trimmed.indexOf(":");
                if (colonIdx != -1) {
                    String content = trimmed.substring(colonIdx + 1);
                    content = content.replace("●", " ");
                    content = content.replaceAll("\\[[^\\]]+\\]", " ");
                    String[] words = content.split("[,\\s]+");
                    for (String word : words) {
                        String w = word.trim();
                        if (w.matches("^[a-zA-Z0-9_]{3,16}$")) {
                            party.add(w);
                        }
                    }
                }
                continue;
            }

            // "You have joined [MVP+] pavlor's party!"
            if (trimmedLower.contains("you have joined") && trimmedLower.contains("'s party!")) {
                party.clear();
                Matcher m = Pattern.compile("you\\s+have\\s+joined\\s*(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})'s\\s+party", Pattern.CASE_INSENSITIVE).matcher(trimmed);
                if (m.find()) {
                    party.add(m.group(1));
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    party.add(mc.player.getGameProfile().name());
                }
                continue;
            }

            // "[MVP+] pavlor has joined the party."
            if (trimmedLower.endsWith("has joined the party.")) {
                Matcher m = Pattern.compile("^(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})\\s+has\\s+joined\\s+the\\s+party\\.", Pattern.CASE_INSENSITIVE).matcher(trimmed);
                if (m.find()) {
                    party.add(m.group(1));
                }
                continue;
            }

            // "[MVP+] pavlor left the party." or "[MVP+] pavlor has been removed from the party."
            if (trimmedLower.endsWith("left the party.") || trimmedLower.endsWith("has been removed from the party.")) {
                Matcher m = Pattern.compile("^(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})\\s+(?:left\\s+the\\s+party|has\\s+been\\s+removed)", Pattern.CASE_INSENSITIVE).matcher(trimmed);
                if (m.find()) {
                    party.remove(m.group(1));
                }
                continue;
            }

            // "The party was disbanded." or "You left the party." or "You are not currently in a party."
            if (trimmedLower.contains("the party was disbanded") || trimmedLower.contains("you left the party") || trimmedLower.contains("you are not currently in a party")) {
                party.clear();
                continue;
            }
        }
    }

    public static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> getUsernameSuggestions(
            com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        
        String remaining = builder.getRemaining().toLowerCase();
        Set<String> suggestions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1. Current lobby players
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    String name = info.getProfile().name();
                    if (name != null && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                        suggestions.add(name);
                    }
                }
            }
            if (mc.player != null) {
                String selfName = mc.player.getGameProfile().name();
                if (selfName != null && selfName.matches("^[a-zA-Z0-9_]{3,16}$")) {
                    suggestions.add(selfName);
                }
            }
        } catch (Throwable t) {
            // Ignore
        }

        // 2. Friends
        suggestions.addAll(friends);

        // 3. Guild
        suggestions.addAll(guild);

        // 4. Party
        suggestions.addAll(party);

        // Filter and add suggestions
        for (String name : suggestions) {
            if (name.toLowerCase().startsWith(remaining) && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                builder.suggest(name);
            }
        }

        return builder.buildFuture();
    }
}
