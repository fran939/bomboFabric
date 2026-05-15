package me.bombo.bomboaddons_final;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class LF {
    private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
    private static final int MAX_RESULTS = 300;

    private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
            .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    public static void searchLocal(String query) {
        if (Minecraft.getInstance().player != null) {
            String name = Minecraft.getInstance().player.getName().getString();
            show(removeColors(name), query, false);
        }
    }

    public static void show(String username, String query, boolean coopMode) {
        sendMessage("&7Looking up &b" + username + (coopMode ? " &d(Coop Mode)" : "") + "&7...");
        getUuid(username).thenCompose((uuid) -> {
            if (uuid == null) {
                sendMessage("&cError: Could not find UUID for " + username);
                return CompletableFuture.completedFuture(null);
            } else {
                String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
                NAME_CACHE.put(cleanUuid, username);
                return getFeatureData(username, cleanUuid).thenApply((json) -> {
                    if (json == null || json.isEmpty()) {
                        sendMessage("&cError: Could not fetch data for " + username);
                        return null;
                    }
                    return new LF.SearchContext(json, cleanUuid, username, coopMode);
                });
            }
        }).thenAccept((ctx) -> {
            if (ctx != null && ctx.json != null) {
                Minecraft.getInstance().execute(() -> handleResponse(username, query, ctx));
            }
        }).exceptionally(e -> {
            sendMessage("&cError during search: " + e.getMessage());
            return null;
        });
    }

    private static void handleResponse(String username, String query, LF.SearchContext ctx) {
        try {
            JsonObject root = JsonParser.parseString(ctx.json).getAsJsonObject();
            String lowerQuery = query.toLowerCase();
            boolean searchLore = false;
            if (lowerQuery.startsWith("lore:") || lowerQuery.startsWith("l:")) {
                searchLore = true;
                lowerQuery = lowerQuery.substring(lowerQuery.indexOf(":") + 1).trim();
            }

            AtomicInteger matchCount = new AtomicInteger(0);
            sendMessage("&eSearch Results for '&f" + lowerQuery + "&e' in &b" + username
                    + (ctx.coopMode ? " (Coop)" : "") + "&e:");
            searchJsonRecursive(root, "", lowerQuery, searchLore, matchCount, ctx, false, false, MAX_RESULTS, false);
            if (matchCount.get() == 0)
                sendMessage("&cCould find 0 results for '&f" + lowerQuery + "&c'.");
        } catch (Exception e) {
            sendMessage("&cError parsing data: " + e.getMessage());
        }
    }

    private static void searchJsonRecursive(JsonElement element, String path, String query, boolean searchLore,
            AtomicInteger matchCount, LF.SearchContext ctx, boolean isInsideMembersNode, boolean toolkitsOnly,
            int limit, boolean isBorrowed) {
        if (matchCount.get() >= limit)
            return;
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (int i = 0; i < arr.size(); ++i) {
                searchJsonRecursive(arr.get(i), path + " > " + i, query, searchLore, matchCount, ctx,
                        isInsideMembersNode, toolkitsOnly, limit, isBorrowed);
            }
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            if (obj.has("borrowing") && obj.get("borrowing").isJsonPrimitive() && obj.get("borrowing").getAsBoolean()) {
                return; // Hide borrowed items entirely
            }

            if (obj.has("data") && obj.get("data").isJsonPrimitive()) {
                decodeAndSearch(path, obj.get("data").getAsString(), query, searchLore, matchCount, ctx, toolkitsOnly,
                        limit, false);
            } else {
                boolean nextIsMember = false;
                for (Entry<String, JsonElement> entry : obj.entrySet()) {
                    String key = entry.getKey();
                    if (key.equalsIgnoreCase("members"))
                        nextIsMember = true;

                    if (isInsideMembersNode && !ctx.coopMode) {
                        String raw = key.replace("-", "").toLowerCase();
                        if (!raw.equals(ctx.targetUuid))
                            continue;
                    }
                    searchJsonRecursive(entry.getValue(), path.isEmpty() ? key : path + " > " + key, query, searchLore,
                            matchCount, ctx, nextIsMember, toolkitsOnly, limit, false);
                }
            }
        }
    }

    private static void decodeAndSearch(String containerPath, String base64, String query, boolean searchLore,
            AtomicInteger matchCount, LF.SearchContext ctx, boolean toolkitsOnly, int limit, boolean isBorrowed) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            CompoundTag nbt;
            try {
                nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            } catch (Exception e) {
                nbt = NbtIo.read(new java.io.DataInputStream(new ByteArrayInputStream(bytes)));
            }
            if (nbt == null)
                return;

            if (nbt.contains("i")) {
                net.minecraft.nbt.ListTag list = nbt.get("i") instanceof net.minecraft.nbt.ListTag
                        ? (net.minecraft.nbt.ListTag) nbt.get("i")
                        : null;
                if (list != null) {
                    for (int i = 0; i < list.size(); ++i) {
                        CompoundTag item = list.getCompound(i).orElse(null);
                        if (item != null && !item.isEmpty()) {
                            processItemNbt(item, i, containerPath, query, searchLore, matchCount, ctx, limit,
                                    isBorrowed);
                        }
                    }
                }
            } else {
                if (nbt.contains("id") || nbt.contains("tag")) {
                    processItemNbt(nbt, 0, containerPath, query, searchLore, matchCount, ctx, limit, isBorrowed);
                }
            }
        } catch (Exception e) {
            if (BomboConfig.get().apiDebug) {
                sendMessage("&c[Debug] Decode failed for " + containerPath + ": " + e.getMessage());
            }
        }
    }

    private static void processItemNbt(CompoundTag item, int slotIndex, String containerPath, String query,
            boolean searchLore, AtomicInteger matchCount, LF.SearchContext ctx, int limit, boolean isBorrowed) {
        if (matchCount.get() >= limit)
            return;
        CompoundTag tag = item.getCompound("tag").orElse(null);
        if (tag == null)
            return;

        CompoundTag display = tag.getCompound("display").orElse(null);
        if (display == null)
            return;

        String fullName = display.getString("Name").orElse("");
        if (fullName.isEmpty())
            return;

        StringBuilder lore = new StringBuilder(fullName).append("\n");
        net.minecraft.nbt.ListTag loreList = display.get("Lore") instanceof net.minecraft.nbt.ListTag
                ? (net.minecraft.nbt.ListTag) display.get("Lore")
                : null;
        if (loreList != null) {
            for (int j = 0; j < loreList.size(); ++j)
                lore.append(loreList.getString(j).orElse("")).append("\n");
        }

        boolean match = removeColors(fullName).toLowerCase().contains(query);
        if (!match && searchLore && removeColors(lore.toString()).toLowerCase().contains(query))
            match = true;

        if (match) {
            int idx = matchCount.incrementAndGet();
            Object[] info = getContainerInfo(containerPath, slotIndex, removeColors(fullName));
            final int finalSlotIndex = slotIndex + (int) info[2];
            final String finalCmdBase = (String) info[1];
            final String finalContBase = (String) info[0];
            final int finalOffset = (int) info[2];

            resolveName(extractLastUuidFromPath(containerPath)).thenAccept(memberName -> {
                Minecraft.getInstance().execute(() -> {
                    String cont = finalContBase;
                    String fullCmd = finalCmdBase;
                    String mUser = memberName != null && !memberName.isEmpty() ? memberName : ctx.targetUsername;
                    String selfName = Minecraft.getInstance().getUser().getName();

                    boolean isOthers = !mUser.equalsIgnoreCase(selfName);

                    if (isOthers) {
                        cont += " (" + memberName + ")";
                        if (fullCmd.startsWith("/enderchest") || fullCmd.startsWith("/backpack")
                                || fullCmd.startsWith("/museum") || fullCmd.startsWith("/bank")
                                || fullCmd.startsWith("/ec")) {
                            if (!fullCmd.contains(mUser))
                                fullCmd += " " + mUser;
                        }
                    }

                    HoverEvent h = createHoverEventRobust(lore.toString());

                    String clickCmd = "/bombo_highlight_slot " + finalSlotIndex + " " + fullCmd;
                    if (fullCmd.startsWith("/museum")) {
                        clickCmd = "/bombo_museum_click " + mUser + " " + finalSlotIndex;
                    } else if (isOthers) {
                        // For the virtual GUI, we want the raw slot index (i), not the offset one
                        clickCmd = "/b view " + ctx.targetUsername + " \"" + containerPath + "\" " + slotIndex;
                    }

                    ClickEvent c = createClickEventRobust("RUN_COMMAND", clickCmd);

                    MutableComponent link = Component.literal(fullName);
                    Style style = Style.EMPTY;
                    if (h != null)
                        style = style.withHoverEvent(h);
                    if (c != null)
                        style = style.withClickEvent(c);
                    link.setStyle(style);

                    MutableComponent contComponent = translate(" &r&7(" + cont + ")");
                    if (c != null)
                        contComponent.setStyle(Style.EMPTY.withClickEvent(c));

                    final String finalContDisplay = cont;
                    Component msg = translate("&7#" + idx + " ").append(link).append(contComponent);
                    if (BomboConfig.get().apiDebug) {
                        sendMessage("&b[Debug] Found at: &7" + containerPath);
                    }
                    if (BomboConfig.get().debugMode) {
                        sendMessage("&b[Debug] " + finalContDisplay + " slot " + finalSlotIndex + " (i=" + slotIndex
                                + ", offset=" + finalOffset + ")");
                    }
                    sendMessage(msg);
                });
            });
        }
    }

    public static Object[] getContainerInfo(String raw, int itemIndex, String itemName) {
        String s = raw.toLowerCase();
        String name = "Inventory";
        String cmd = "/play sb";
        int offset = 0;
        if (s.contains("backpack")) {
            name = "Backpack";
            cmd = "/backpack";
            offset = 9;
            try {
                String[] parts = s.split(" > ");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].contains("backpack")) {
                        int num = Integer.parseInt(parts[i + 1]) + 1;
                        name = "Backpack " + num;
                        cmd = "/backpack " + num;
                        break;
                    }
                }
            } catch (Exception e) {
            }
        } else if (s.contains("enderchest") || s.contains("ender_chest")) {
            name = "Ender Chest";
            cmd = "/enderchest";
                // Use the itemIndex to calculate the page
                int page = (itemIndex / 45) + 1;
                name = "Ender Chest " + page;
                cmd = "/ec " + page;
        } else if (s.contains("wardrobe")) {
            name = "Wardrobe";
            cmd = "/wardrobe";
        } else if (s.contains("vault")) {
            name = "Personal Vault";
            cmd = "/bank";
        } else if (s.contains("museum")) {
            name = "Museum";
            cmd = "/museum";
        } else if (s.contains("sacks")) {
            name = "Sacks";
            cmd = "/sacks";
        } else if (s.contains("quiver")) {
            name = "Quiver";
            cmd = "/quiver";
        } else if (s.contains("potion_bag")) {
            name = "Potion Bag";
            cmd = "/potionbag";
        } else if (s.contains("candy_bag")) {
            name = "Candy Bag";
            cmd = "/candybag";
        } else if (s.contains("fishing_bag")) {
            name = "Fishing Bag";
            cmd = "/fishingbag";
        }

        return new Object[] { name, cmd, offset };
    }

    public static String removeColors(String s) {
        return s == null ? "" : s.replaceAll("§.", "").replaceAll("&.", "");
    }

    private static String extractLastUuidFromPath(String path) {
        String[] parts = path.split(" > ");
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i].replace("-", "").toLowerCase();
            if (p.length() == 32 && UUID_PATTERN.matcher(p).matches())
                return p;
        }
        return null;
    }

    private static CompletableFuture<UUID> getUuid(String username) {
        if (username == null || username.isEmpty())
            return CompletableFuture.completedFuture(null);
        return fetchString("https://api.ashcon.app/mojang/v2/user/" + username).thenCompose((response) -> {
            if (response != null && response.contains("\"uuid\"")) {
                try {
                    JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                    if (json.has("uuid")) {
                        return CompletableFuture.completedFuture(UUID.fromString(json.get("uuid").getAsString()));
                    }
                } catch (Exception e) {
                }
            }
            return fetchString("https://api.mojang.com/users/profiles/minecraft/" + username).thenApply((mojangRes) -> {
                if (mojangRes == null)
                    return null;
                try {
                    JsonObject json = JsonParser.parseString(mojangRes).getAsJsonObject();
                    if (json.has("id")) {
                        return UUID.fromString(json.get("id").getAsString()
                                .replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                    }
                } catch (Exception e) {
                }
                return null;
            });
        });
    }

    private static CompletableFuture<String> getFeatureData(String username, String cleanUuid) {
        String url = "https://bomboapi.frandl938.workers.dev/" + username;
        if (BomboConfig.get().apiDebug) {
            sendMessage("&b[Debug] API: " + url);
        }
        return fetchString(url).thenCompose((response) -> {
            if (response != null && !response.isEmpty() && response.startsWith("{")) {
                return CompletableFuture.completedFuture(response);
            }
            // Fallback to UUID
            String uuidUrl = "https://bomboapi.frandl938.workers.dev/" + cleanUuid;
            if (BomboConfig.get().apiDebug)
                sendMessage("&7[Debug] Username API failed, trying UUID: " + uuidUrl);
            return fetchString(uuidUrl).thenCompose(uuidRes -> {
                if (uuidRes != null && !uuidRes.isEmpty() && uuidRes.startsWith("{"))
                    return CompletableFuture.completedFuture(uuidRes);
                return fetchString("https://profile.snailify.workers.dev/?uuid=" + cleanUuid);
            });
        });
    }

    private static CompletableFuture<String> resolveName(String uuid) {
        if (uuid == null)
            return CompletableFuture.completedFuture(null);
        if (NAME_CACHE.containsKey(uuid.toLowerCase()))
            return CompletableFuture.completedFuture(NAME_CACHE.get(uuid.toLowerCase()));

        return fetchString("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)
                .thenCompose((response) -> {
                    if (response != null && response.contains("\"name\"")) {
                        try {
                            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                            if (json.has("name")) {
                                String name = json.get("name").getAsString();
                                NAME_CACHE.put(uuid.toLowerCase(), name);
                                return CompletableFuture.completedFuture(name);
                            }
                        } catch (Exception e) {
                        }
                    }
                    return fetchString("https://api.ashcon.app/mojang/v2/user/" + uuid).thenApply((fallbackRes) -> {
                        if (fallbackRes == null)
                            return null;
                        try {
                            JsonObject json = JsonParser.parseString(fallbackRes).getAsJsonObject();
                            if (json.has("username")) {
                                String name = json.get("username").getAsString();
                                NAME_CACHE.put(uuid.toLowerCase(), name);
                                return name;
                            }
                        } catch (Exception e) {
                        }
                        return null;
                    });
                });
    }

    private static CompletableFuture<String> fetchString(String url) {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .GET()
                .build();

        return CLIENT.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString()).thenApply((res) -> {
            return res.statusCode() == 200 ? res.body() : null;
        }).exceptionally((e) -> {
            return null;
        });
    }

    private static void sendMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.execute(() -> mc.player.displayClientMessage(translate(msg), false));
        }
    }

    private static void sendMessage(Component msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.execute(() -> mc.player.displayClientMessage(msg, false));
        }
    }

    public static HoverEvent createHoverEventRobust(String lore) {
        return SBECommands.createHoverEvent(lore);
    }

    public static ClickEvent createClickEventRobust(String action, String value) {
        try {
            if (action.equalsIgnoreCase("RUN_COMMAND"))
                return new ClickEvent.RunCommand(value);
            if (action.equalsIgnoreCase("SUGGEST_COMMAND"))
                return new ClickEvent.SuggestCommand(value);
            if (action.equalsIgnoreCase("OPEN_URL"))
                return new ClickEvent.OpenUrl(java.net.URI.create(value));
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static MutableComponent translate(String s) {
        return Component.literal(s.replace("&", "\u00A7"));
    }

    private record SearchContext(String json, String targetUuid, String targetUsername, boolean coopMode) {
    }

    public static void printContainerInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            sendMessage("&6--- Container Diagnostic ---");
            sendMessage("&eTitle: &f" + screen.getTitle().getString());

            net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
            List<net.minecraft.world.inventory.Slot> slots = menu.slots;
            sendMessage("&eTotal Slots: &f" + slots.size());

            if (slots.size() > 49) {
                net.minecraft.world.item.ItemStack timer = slots.get(49).getItem();
                sendMessage("&eSlot 49 (Timer): &f" + timer.getItem().toString() + " (Foil: " + timer.hasFoil() + ")");
            }

            sendMessage("&7(Diagnostic info displayed in-game)");
        } else {
            sendMessage("&cNot in a container!");
        }
    }

    public static void showToolkit(String username, int limit) {
        sendMessage("&7Fetching toolkit data for &b" + username + "&7...");
        getUuid(username).thenCompose((uuid) -> {
            if (uuid == null)
                return CompletableFuture.completedFuture(null);
            String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
            return getFeatureData(username, cleanUuid).thenApply(json -> new Object[] { cleanUuid, json });
        }).thenAccept((results) -> {
            if (results == null)
                return;
            String cleanUuid = (String) results[0];
            String json = (String) results[1];
            if (json == null) {
                Minecraft.getInstance().execute(() -> sendMessage("&cFailed to get data."));
                return;
            }
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                Minecraft.getInstance().execute(() -> {
                    sendMessage("&e--- Toolkit Contents for &b" + username + " &e---");
                    AtomicInteger count = new AtomicInteger(0);
                    LF.SearchContext ctx = new LF.SearchContext(json, cleanUuid, username, false);
                    searchJsonRecursive(root, "", "", false, count, ctx, false, true, limit, false);
                    if (count.get() == 0)
                        sendMessage("&cNo toolkit or sack data found.");
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> sendMessage("&cError: " + e.getMessage()));
            }
        });
    }

    private static String cachedUsername = "";
    private static String cachedJson = null;
    private static long cacheTime = 0;

    public static void openVirtualContainer(String username, String path, int highlightSlot) {
        if (username.equalsIgnoreCase(cachedUsername) && cachedJson != null
                && (System.currentTimeMillis() - cacheTime < 300000)) {
            processContainer(username, cachedJson, path, highlightSlot);
            return;
        }

        getUuid(username).thenCompose(uuid -> {
            if (uuid == null)
                return CompletableFuture.completedFuture(null);
            String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
            return getFeatureData(username, cleanUuid).thenApply(json -> new Object[] { json, cleanUuid });
        }).thenAccept(res -> {
            if (res == null)
                return;
            String json = (String) res[0];
            if (json == null)
                return;

            cachedUsername = username;
            cachedJson = json;
            cacheTime = System.currentTimeMillis();

            processContainer(username, json, path, highlightSlot);
        });
    }

    private static void processContainer(String username, String json, String path, int highlightSlot) {
        Minecraft.getInstance().execute(() -> {
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();

                // 1. Try direct path
                JsonElement target = drillDown(root, path);

                // 2. Fallback: Handle Elite/Simplified format by stripping Hypixel prefix
                if (target == null && path.contains("members > ")) {
                    String[] segments = path.split("members > ");
                    if (segments.length > 1) {
                        String localPath = segments[1];
                        if (localPath.contains(" > ")) {
                            localPath = localPath.split(" > ", 2)[1];
                            target = drillDown(root, localPath);
                            if (target == null && root.has("profile") && root.get("profile").isJsonObject()) 
                                target = drillDown(root.getAsJsonObject("profile"), localPath);
                            if (target == null && root.has("raw_profile") && root.get("raw_profile").isJsonObject()) 
                                target = drillDown(root.getAsJsonObject("raw_profile"), localPath);
                        }
                    }
                }

                // 3. Last Resort: Find the container key anywhere in the JSON
                if (target == null) {
                    String[] parts = path.split(" > ");
                    if (parts.length >= 2) {
                        String containerName = parts[parts.length - 2];
                        String indexOrLeaf = parts[parts.length - 1];
                        JsonElement container = findJsonRecursively(root, containerName);
                        if (container != null) {
                            if (container.isJsonObject() && container.getAsJsonObject().has(indexOrLeaf)) {
                                target = container.getAsJsonObject().get(indexOrLeaf);
                            } else if (container.isJsonArray()) {
                                try {
                                    int idx = Integer.parseInt(indexOrLeaf);
                                    JsonArray arr = container.getAsJsonArray();
                                    if (idx >= 0 && idx < arr.size())
                                        target = arr.get(idx);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }

                // 4. Double check for member UUID fallback if we still don't have it
                if (target == null && path.contains("members > ")) {
                    String uuidPart = path.split("members > ")[1].split(" > ")[0];
                    String targetKey = path.substring(path.lastIndexOf(">") + 1).trim();

                    JsonElement member = null;
                    if (root.has("profiles") && root.get("profiles").isJsonArray()) {
                        for (JsonElement p : root.getAsJsonArray("profiles")) {
                            JsonObject pObj = p.getAsJsonObject();
                            if (pObj.has("members") && pObj.getAsJsonObject("members").has(uuidPart)) {
                                member = pObj.getAsJsonObject("members").get(uuidPart);
                                break;
                            }
                        }
                    }

                    if (member != null) {
                        target = findJsonRecursively(member, targetKey);
                    }
                }

                String base64 = null;
                if (target != null) {
                    if (target.isJsonObject() && target.getAsJsonObject().has("data")) {
                        base64 = target.getAsJsonObject().get("data").getAsString();
                    } else if (target.isJsonPrimitive()) {
                        base64 = target.getAsString();
                    }
                }

                if (base64 != null) {
                    final String finalBase64 = base64;
                    if (BomboConfig.get().apiDebug)
                        sendMessage(
                                "&7[Debug] Container data found (length: " + finalBase64.length() + "). Decoding...");

                    CompletableFuture.supplyAsync(() -> decodeToItems(finalBase64), Minecraft.getInstance())
                            .thenAccept(items -> {
                                if (items == null || items.isEmpty()) {
                                    sendMessage("&cNo items found in container data.");
                                    return;
                                }

                                // Handle multi-page containers (like Ender Chests) by slicing the list
                                List<ItemStack> processedItems = items;
                                if (path.contains("ender_chest") || path.contains("enderchest")) {
                                    int page = highlightSlot / 45;
                                    int start = page * 45;
                                    int end = Math.min(start + 45, items.size());
                                    if (start < items.size()) {
                                        processedItems = new ArrayList<>(items.subList(start, end));
                                        if (BomboConfig.get().apiDebug)
                                            sendMessage("&7[Debug] Ender Chest slice: " + start + " to " + end
                                                    + " (Page " + (page + 1) + ")");
                                    }
                                }

                                final List<ItemStack> finalItems = processedItems;
                                Minecraft.getInstance().execute(() -> {
                                    try {
                                        String title = username + "'s "
                                                + path.substring(path.lastIndexOf(">") + 1).trim();
                                        VirtualContainerGUI gui = new VirtualContainerGUI(title, finalItems,
                                                highlightSlot % 45, username, path);
                                        Minecraft.getInstance().setScreen(gui);
                                    } catch (Exception e) {
                                        sendMessage("&cError opening GUI: " + e.getMessage());
                                    }
                                });
                            }).exceptionally(e -> {
                                sendMessage("&cError decoding container: " + e.getMessage());
                                return null;
                            });
                } else {
                    sendMessage("&cCould not find container data at: " + path);
                }
            } catch (Exception e) {
                sendMessage("&cFailed to open virtual container: " + e.getMessage());
            }
        });
    }

    private static JsonElement findJsonRecursively(JsonElement root, String targetKey) {
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            if (obj.has(targetKey))
                return obj.get(targetKey);
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                JsonElement res = findJsonRecursively(entry.getValue(), targetKey);
                if (res != null)
                    return res;
            }
        } else if (root.isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray()) {
                JsonElement res = findJsonRecursively(e, targetKey);
                if (res != null)
                    return res;
            }
        }
        return null;
    }

    private static JsonElement drillDown(JsonObject root, String path) {
        String[] parts = path.split(" > ");
        JsonElement current = root;
        for (int i = 0; i < parts.length; i++) {
            String key = parts[i].trim();
            if (current == null || current.isJsonNull())
                return null;

            JsonElement next = null;
            if (current.isJsonObject()) {
                JsonObject obj = current.getAsJsonObject();
                if (obj.has(key)) {
                    next = obj.get(key);
                } else {
                    // Try case-insensitive fallback
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(key)) {
                            next = entry.getValue();
                            break;
                        }
                    }

                    // If still not found and it's a common storage key, try deep search in siblings
                    if (next == null) {
                        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                            if (entry.getValue().isJsonObject()) {
                                JsonObject sub = entry.getValue().getAsJsonObject();
                                if (sub.has(key)) {
                                    next = sub.get(key);
                                    break;
                                }
                            }
                        }
                    }
                }
            } else if (current.isJsonArray()) {
                try {
                    int idx = Integer.parseInt(key);
                    JsonArray arr = current.getAsJsonArray();
                    if (idx >= 0 && idx < arr.size()) {
                        next = arr.get(idx);
                    }
                } catch (Exception e) {
                }
            }

            if (next == null) {
                if (BomboConfig.get().apiDebug) {
                    String keys = current.isJsonObject() ? String.join(", ", current.getAsJsonObject().keySet())
                            : "N/A";
                    sendMessage("&7[Debug] Failed at: " + key + ". Available: " + keys);
                }
                return null;
            }
            current = next;
        }
        return current;
    }

    private static List<ItemStack> decodeToItems(String base64) {
        List<ItemStack> result = new ArrayList<>();
        if (base64 == null || base64.trim().isEmpty()) return result;
        
        try {
            byte[] bytes = Base64.getDecoder().decode(base64.trim());
            CompoundTag nbt;
            try {
                nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            } catch (Exception e) {
                nbt = NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes)));
            }
            if (nbt == null) return result;

            if (nbt.contains("i")) {
                net.minecraft.nbt.ListTag list = (net.minecraft.nbt.ListTag) nbt.get("i");
                if (list != null) {
                    for (int i = 0; i < list.size(); ++i) {
                        result.add(convertNbtToStack(list.getCompound(i).orElse(null)));
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Illegal Base64 character in container data: " + e.getMessage());
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to decode container data", e);
        }
        return result;
    }

    private static ItemStack convertNbtToStack(CompoundTag itemNbt) {
        if (itemNbt == null || itemNbt.isEmpty()) return ItemStack.EMPTY;
        String idStr = "minecraft:air";
        try {
            // Pre-process legacy Hypixel NBT to match modern 1.21.1 schema
            CompoundTag modernNbt = new CompoundTag();
            
            // 1. Map ID (Prioritize SkyBlock ID from ExtraAttributes)
            String sbId = null;
            if (itemNbt.contains("tag")) {
                CompoundTag tag = itemNbt.getCompound("tag").orElse(null);
                if (tag != null) {
                    if (tag.contains("ExtraAttributes")) {
                        CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                        if (ea != null && ea.contains("id")) {
                            sbId = ea.getString("id").orElse(null);
                        }
                    }
                    // Check for Skull textures
                    if (tag.contains("SkullOwner")) {
                        idStr = "minecraft:player_head";
                        CompoundTag components = new CompoundTag();
                        components.put("minecraft:profile", tag.getCompound("SkullOwner").orElse(new CompoundTag()).copy());
                        modernNbt.put("components", components);
                    }
                }
            }

            boolean isHead = false;
            if (itemNbt.contains("tag")) {
                CompoundTag tag = itemNbt.getCompound("tag").orElse(null);
                if (tag != null && tag.contains("SkullOwner")) {
                    isHead = true;
                    idStr = "minecraft:player_head";
                }
            }

            if (!isHead && sbId != null) {
                idStr = guessItem(sbId);
            }
            
            if (idStr.equals("minecraft:chest") || idStr.equals("minecraft:air")) {
                if (itemNbt.contains("id")) {
                    if (itemNbt.get("id") instanceof net.minecraft.nbt.StringTag) {
                        String rawId = itemNbt.getString("id").orElse("minecraft:air");
                        if (!isHead) idStr = guessItem(rawId);
                        else idStr = "minecraft:player_head";
                    } else {
                        int numericId = itemNbt.getInt("id").orElse(0);
                        if (!isHead) idStr = mapNumericId(numericId);
                        else idStr = "minecraft:player_head";
                    }
                }
            }
            modernNbt.putString("id", idStr);

            // 2. Map Count
            int count = 1;
            if (itemNbt.contains("Count")) count = itemNbt.getByte("Count").orElse((byte)1);
            else if (itemNbt.contains("count")) count = itemNbt.getInt("count").orElse(1);
            modernNbt.putInt("count", count);
            
            // 3. Create the ItemStack
            ItemStack stack = ItemStack.EMPTY;
            if (Minecraft.getInstance().level != null) {
                RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
                stack = ItemStack.CODEC.parse(ops, modernNbt).result().orElse(ItemStack.EMPTY);
            }
            
            if (stack.isEmpty()) {
                net.minecraft.world.item.Item item = net.minecraft.world.item.Items.CHEST;
                try {
                    Object res = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Identifier.parse(idStr));
                    if (res instanceof java.util.Optional<?> o && o.isPresent()) {
                        Object inner = o.get();
                        if (inner instanceof net.minecraft.core.Holder<?> h) item = (net.minecraft.world.item.Item) h.value();
                        else if (inner instanceof net.minecraft.world.item.Item i) item = i;
                    } else if (res instanceof net.minecraft.world.item.Item i) {
                        item = i;
                    }
                } catch (Exception e) {}
                stack = item.getDefaultInstance().copyWithCount(count);
            }

            // 4. Apply Display Name, Lore, and Custom Head textures via DataComponents
            if (itemNbt.contains("tag")) {
                CompoundTag tag = itemNbt.getCompound("tag").orElse(null);
                if (tag != null) {
                    // Display Name and Lore
                    if (tag.contains("display")) {
                        CompoundTag display = tag.getCompound("display").orElse(null);
                        if (display != null) {
                            if (display.contains("Name")) {
                                String name = display.getString("Name").orElse("").replace("&", "§");
                                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
                            }
                            if (display.contains("Lore")) {
                                net.minecraft.nbt.ListTag loreList = (net.minecraft.nbt.ListTag) display.get("Lore");
                                List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
                                for (int i = 0; i < loreList.size(); i++) {
                                    lines.add(net.minecraft.network.chat.Component.literal(loreList.getString(i).orElse("").replace("&", "§")));
                                }
                                if (!lines.isEmpty()) {
                                    stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
                                }
                            }
                        }
                    }
                    
                    // Custom Head Texture
                    if (tag.contains("SkullOwner")) {
                        CompoundTag skullOwner = tag.getCompound("SkullOwner").orElse(null);
                        if (skullOwner != null) {
                            if (BomboConfig.get().apiDebug) logDebug("Raw SkullOwner: " + skullOwner);
                            try {
                                // Clean up the SkullOwner NBT to remove invalid signatures
                                if (skullOwner.contains("Properties")) {
                                    CompoundTag properties = skullOwner.getCompound("Properties").orElse(null);
                                    if (properties != null && properties.contains("textures")) {
                                        net.minecraft.nbt.ListTag textures = (net.minecraft.nbt.ListTag) properties.get("textures");
                                        for (int i = 0; i < textures.size(); i++) {
                                            CompoundTag tex = textures.getCompound(i).orElse(null);
                                            if (tex != null) {
                                                tex.remove("Signature");
                                                tex.remove("signature");
                                            }
                                        }
                                    }
                                }

                                String uuidStr = skullOwner.contains("Id") ? skullOwner.getString("Id").orElse(null) : null;
                                java.util.UUID uuid = null;
                                try { if (uuidStr != null) uuid = java.util.UUID.fromString(uuidStr); } catch (Exception ignored) {}
                                if (uuid == null || uuid.toString().startsWith("04049c90")) uuid = java.util.UUID.randomUUID();

                                String profileName = skullOwner.contains("Name") ? skullOwner.getString("Name").orElse("") : "";
                                com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, profileName);
                                if (BomboConfig.get().apiDebug) logDebug("Loading head: " + profileName + " UUID=" + uuid);
                                
                                // Add textures property manually to the profile using reflection for compatibility
                                com.mojang.authlib.properties.PropertyMap props = null;
                                try {
                                    java.lang.reflect.Method gp = profile.getClass().getMethod("getProperties");
                                    props = (com.mojang.authlib.properties.PropertyMap) gp.invoke(profile);
                                } catch (Exception e1) {
                                    try {
                                        java.lang.reflect.Method gp = profile.getClass().getMethod("properties");
                                        props = (com.mojang.authlib.properties.PropertyMap) gp.invoke(profile);
                                    } catch (Exception e2) {}
                                }

                                // If still null, try to instantiate via reflection
                                if (props == null) {
                                    try {
                                        props = (com.mojang.authlib.properties.PropertyMap) com.mojang.authlib.properties.PropertyMap.class.getDeclaredConstructor().newInstance();
                                    } catch (Exception e3) {}
                                }
                                if (props != null && (skullOwner.contains("Properties") || skullOwner.contains("properties"))) {
                                    CompoundTag properties = skullOwner.getCompound("Properties").orElse(skullOwner.getCompound("properties").orElse(null));
                                    if (properties != null && (properties.contains("textures") || properties.contains("Textures"))) {
                                        net.minecraft.nbt.ListTag textures = (net.minecraft.nbt.ListTag) properties.get(properties.contains("textures") ? "textures" : "Textures");
                                        for (int i = 0; i < textures.size(); i++) {
                                            CompoundTag tex = textures.getCompound(i).orElse(null);
                                            if (tex != null && (tex.contains("Value") || tex.contains("value"))) {
                                                try {
                                                    Object v = tex.get(tex.contains("Value") ? "Value" : "value");
                                                    Object s = tex.get(tex.contains("Signature") ? "Signature" : "signature");
                                                    
                                                    String val = "";
                                                    if (v != null) {
                                                        if (v instanceof String) val = (String)v;
                                                        else {
                                                            try {
                                                                java.lang.reflect.Method m = v.getClass().getMethod("getAsString");
                                                                val = (String) m.invoke(v);
                                                            } catch (Exception e) {
                                                                val = v.toString().replace("\"", "");
                                                            }
                                                        }
                                                    }
                                                    
                                                    // Signature stripping to bypass Authlib verification
                                                    props.put("textures", new com.mojang.authlib.properties.Property("textures", val));
                                                    if (BomboConfig.get().apiDebug) logDebug("  Added Texture (len=" + val.length() + ", no sig)");
                                                } catch (Exception e_tex) {
                                                    System.out.println("[Bombo]   Failed to get texture string: " + e_tex.getMessage());
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    System.out.println("[Bombo]   NO Properties found in SkullOwner!");
                                }
                                final com.mojang.authlib.properties.PropertyMap finalProps = props;

                                // Create ResolvableProfile via reflection to handle record component differences
                                try {
                                    java.lang.reflect.Constructor<?>[] constructors = net.minecraft.world.item.component.ResolvableProfile.class.getConstructors();
                                    Object rp = null;
                                    for (java.lang.reflect.Constructor<?> constructor : constructors) {
                                        Class<?>[] params = constructor.getParameterTypes();
                                        System.out.println("[Bombo]   Constructor params: " + java.util.Arrays.toString(params));
                                        if (params.length == 1 && params[0] == com.mojang.authlib.GameProfile.class) {
                                            rp = constructor.newInstance(profile);
                                            break;
                                        } else if (params.length == 3) {
                                            // Record constructor: Optional<String>, Optional<UUID>, PropertyMap
                                            try {
                                                java.util.Optional<String> nameOpt = java.util.Optional.ofNullable(profile.name());
                                                java.util.Optional<java.util.UUID> idOpt = java.util.Optional.ofNullable(profile.id());
                                                System.out.println("[Bombo]   Applying (name=" + nameOpt + ", id=" + idOpt + ", props=" + (finalProps != null) + ")");
                                                rp = constructor.newInstance(nameOpt, idOpt, finalProps);
                                                break;
                                            } catch (Exception e_inner) {
                                                System.out.println("[Bombo]   Inner reflection failed: " + e_inner.getMessage());
                                                e_inner.printStackTrace();
                                            }
                                        }
                                    }
                                    
                                    if (rp != null) {
                                        stack.set(net.minecraft.core.component.DataComponents.PROFILE, (net.minecraft.world.item.component.ResolvableProfile)rp);
                                        if (BomboConfig.get().apiDebug) logDebug("  Applied ResolvableProfile (UUID: " + profile.id() + ")");
                                    } else {
                                        if (BomboConfig.get().apiDebug) logDebug("  FAILED to create ResolvableProfile via reflection");
                                    }
                                    // Final fallback: use codec with a manually constructed, "clean" NBT
                                    try {
                                        CompoundTag cleanProfileNbt = new CompoundTag();
                                        cleanProfileNbt.putString("name", profileName);
                                        
                                        // UUID as int-array (Required by modern Codecs)
                                        long most = uuid.getMostSignificantBits();
                                        long least = uuid.getLeastSignificantBits();
                                        int[] uuidInts = new int[]{(int)(most >> 32), (int)most, (int)(least >> 32), (int)least};
                                        cleanProfileNbt.putIntArray("id", uuidInts);
                                        
                                        if (finalProps != null && !finalProps.isEmpty()) {
                                            net.minecraft.nbt.ListTag propsList = new net.minecraft.nbt.ListTag();
                                            for (com.mojang.authlib.properties.Property p : finalProps.get("textures")) {
                                                CompoundTag pTag = new CompoundTag();
                                                pTag.putString("name", "textures");
                                                pTag.putString("value", p.value());
                                                // Ensure signature is ABSENT or empty to bypass verification
                                                propsList.add(pTag);
                                            }
                                            cleanProfileNbt.put("properties", propsList);
                                        }
                                        
                                        RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
                                        if (BomboConfig.get().apiDebug) logDebug("  Bulletproof NBT: " + cleanProfileNbt);
                                        
                                        net.minecraft.world.item.component.ResolvableProfile parsed = net.minecraft.world.item.component.ResolvableProfile.CODEC.parse(ops, cleanProfileNbt).result().orElse(null);
                                        if (parsed != null) {
                                            stack.set(net.minecraft.core.component.DataComponents.PROFILE, parsed);
                                            if (BomboConfig.get().apiDebug) logDebug("  Applied ResolvableProfile via CODEC (Bulletproof)");
                                        } else {
                                            if (BomboConfig.get().apiDebug) logDebug("  CODEC parse returned null");
                                        }
                                    } catch (Exception e_codec) {
                                        if (BomboConfig.get().apiDebug) logDebug("  CODEC fallback failed: " + e_codec.getMessage());
                                    }
                                } catch (Exception e) {
                                    if (BomboConfig.get().apiDebug) sendMessage("&cError applying head texture: " + e.getMessage());
                                }
                            } catch (Exception e_outer) {
                                System.out.println("[Bombo] Outer head error: " + e_outer.getMessage());
                            }
                        }
                    }

                    // Enchantment Glint
                    if (tag.contains("ench") || tag.contains("ExtraAttributes") && tag.getCompound("ExtraAttributes").orElse(new CompoundTag()).contains("enchantments")) {
                        stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                    }
                }
            }

            if (BomboConfig.get().apiDebug && stack != null) {
                logDebug("Final Stack: " + stack.getItem() + " components: " + stack.getComponents());
            }
            return stack;
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("Error converting NBT to Stack", e);
        }
        if (BomboConfig.get().apiDebug) logDebug("Guessing item for ID: " + idStr);
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Identifier.parse(guessItem(idStr)))
            .map(net.minecraft.core.Holder::value)
            .orElse(net.minecraft.world.item.Items.CHEST);
        return item.getDefaultInstance();
    }

    private static String guessItem(String id) {
        String clean = id.toUpperCase().replace("MINECRAFT:", "");

        // 1. Common legacy ID mappings
        String legacyMapped = switch (clean) {
            case "SKULL_ITEM" -> "minecraft:player_head";
            case "IRON_SPADE" -> "minecraft:iron_shovel";
            case "DIAMOND_SPADE" -> "minecraft:diamond_shovel";
            case "GOLD_SPADE" -> "minecraft:gold_shovel";
            case "WOOD_SPADE" -> "minecraft:wooden_shovel";
            case "STONE_SPADE" -> "minecraft:stone_shovel";
            case "IRON_PICKAXE" -> "minecraft:iron_pickaxe";
            case "DIAMOND_PICKAXE" -> "minecraft:diamond_pickaxe";
            case "GOLD_PICKAXE" -> "minecraft:gold_pickaxe";
            case "WOOD_PICKAXE" -> "minecraft:wooden_pickaxe";
            case "STONE_PICKAXE" -> "minecraft:stone_pickaxe";
            case "IRON_AXE" -> "minecraft:iron_axe";
            case "DIAMOND_AXE" -> "minecraft:diamond_axe";
            case "GOLD_AXE" -> "minecraft:gold_axe";
            case "WOOD_AXE" -> "minecraft:wooden_axe";
            case "STONE_AXE" -> "minecraft:stone_axe";
            case "IRON_HOE" -> "minecraft:iron_hoe";
            case "DIAMOND_HOE" -> "minecraft:diamond_hoe";
            case "GOLD_HOE" -> "minecraft:gold_hoe";
            case "WOOD_HOE" -> "minecraft:wooden_hoe";
            case "STONE_HOE" -> "minecraft:stone_hoe";
            case "IRON_SWORD" -> "minecraft:iron_sword";
            case "DIAMOND_SWORD" -> "minecraft:diamond_sword";
            case "GOLD_SWORD" -> "minecraft:gold_sword";
            case "WOOD_SWORD" -> "minecraft:wooden_sword";
            case "STONE_SWORD" -> "minecraft:stone_sword";
            case "RAW_FISH" -> "minecraft:cod";
            case "COOKED_FISH" -> "minecraft:cooked_cod";
            case "INK_SACK" -> "minecraft:ink_sac";
            case "SULPHUR" -> "minecraft:gunpowder";
            case "NETHER_STALK" -> "minecraft:nether_wart";
            case "POTION" -> "minecraft:potion";
            case "EXP_BOTTLE" -> "minecraft:experience_bottle";
            case "BOOK_AND_QUILL" -> "minecraft:writable_book";
            case "WATCH" -> "minecraft:clock";
            case "STEP" -> "minecraft:stone_slab";
            case "WOOD_STEP" -> "minecraft:oak_slab";
            case "STAINED_GLASS" -> "minecraft:white_stained_glass";
            case "STAINED_GLASS_PANE" -> "minecraft:white_stained_glass_pane";
            case "THIN_GLASS" -> "minecraft:glass_pane";
            case "RED_ROSE" -> "minecraft:poppy";
            case "YELLOW_FLOWER" -> "minecraft:dandelion";
            case "LONG_GRASS" -> "minecraft:short_grass";
            case "LEASH" -> "minecraft:lead";
            case "PRISMARINE_SHARD" -> "minecraft:prismarine_shard";
            case "PRISMARINE_CRYSTALS" -> "minecraft:prismarine_crystals";
            default -> null;
        };
        if (legacyMapped != null)
            return legacyMapped;

        // 2. Check if it's already a valid Minecraft item
        String namespaced = "minecraft:" + clean.toLowerCase();
        if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(Identifier.parse(namespaced))) {
            return namespaced;
        }

        // 3. Handle common SkyBlock item name patterns
        String lower = clean.toLowerCase();

        // High Priority Mappings
        if (lower.contains("terminator"))
            return "minecraft:bow";
        if (lower.contains("hyperion") || lower.contains("astraea") || lower.contains("scylla")
                || lower.contains("valkyrie"))
            return "minecraft:iron_sword";
        if (lower.contains("juju"))
            return "minecraft:bow";
        if (lower.contains("drill") || lower.contains("divan"))
            return "minecraft:prismarine_shard";
        if (lower.contains("mithril") || lower.contains("refined"))
            return "minecraft:prismarine_crystals";
        if (lower.contains("titanium") || lower.contains("gauntlet"))
            return "minecraft:iron_ingot";
        if (lower.contains("lasso") || lower.contains("lead") || lower.contains("leash"))
            return "minecraft:lead";
        if (lower.contains("pickonimbus") || lower.contains("pioneer") || lower.contains("flux"))
            return "minecraft:iron_pickaxe";
        if (lower.contains("sinker"))
            return "minecraft:fishing_rod";
        if (lower.contains("fishing"))
            return "minecraft:cod";
        if (lower.contains("prismarine") || lower.contains("shard"))
            return "minecraft:prismarine_shard";

        if (lower.contains("dandelion") || lower.contains("flower") || lower.contains("exp_share"))
            return "minecraft:dandelion";
        if (lower.contains("exp"))
            return "minecraft:experience_bottle";
        if (lower.contains("dicer"))
            return "minecraft:diamond_axe";
        if (lower.contains("zapper"))
            return "minecraft:flint";
        if (lower.contains("hoe"))
            return "minecraft:diamond_hoe";

        if (lower.contains("belt") || lower.contains("cloak") || lower.contains("bracelet")
                || lower.contains("necklace") || lower.contains("gloves"))
            return "minecraft:leather_boots";

        if (lower.contains("sword"))
            return "minecraft:iron_sword";
        if (lower.contains("bow"))
            return "minecraft:bow";
        if (lower.contains("pickaxe"))
            return "minecraft:diamond_pickaxe";
        if (lower.contains("axe"))
            return "minecraft:iron_axe";
        if (lower.contains("shovel") || lower.contains("spade"))
            return "minecraft:iron_shovel";
        if (lower.contains("hoe"))
            return "minecraft:iron_hoe";
        if (lower.contains("artifact") || lower.contains("relic") || lower.contains("talisman")
                || lower.contains("ring"))
            return "minecraft:player_head";

        // Materials
        if (lower.contains("cobblestone"))
            return "minecraft:cobblestone";
        if (lower.contains("diamond"))
            return "minecraft:diamond";
        if (lower.contains("iron"))
            return "minecraft:iron_ingot";
        if (lower.contains("gold"))
            return "minecraft:gold_ingot";
        if (lower.contains("coal"))
            return "minecraft:coal";
        if (lower.contains("emerald"))
            return "minecraft:emerald";
        if (lower.contains("lapis"))
            return "minecraft:lapis_lazuli";
        if (lower.contains("redstone"))
            return "minecraft:redstone";
        if (lower.contains("slime"))
            return "minecraft:slime_ball";
        if (lower.contains("flesh"))
            return "minecraft:rotten_flesh";
        if (lower.contains("bone"))
            return "minecraft:bone";
        if (lower.contains("eye"))
            return "minecraft:spider_eye";
        if (lower.contains("string"))
            return "minecraft:string";
        if (lower.contains("feather"))
            return "minecraft:feather";
        if (lower.contains("leather"))
            return "minecraft:leather";
        if (lower.contains("blaze"))
            return "minecraft:blaze_rod";
        if (lower.contains("ghast"))
            return "minecraft:ghast_tear";
        if (lower.contains("magma"))
            return "minecraft:magma_cream";
        if (lower.contains("glowstone"))
            return "minecraft:glowstone_dust";
        if (lower.contains("sugar"))
            return "minecraft:sugar_cane";
        if (lower.contains("wheat"))
            return "minecraft:wheat";
        if (lower.contains("carrot"))
            return "minecraft:carrot";
        if (lower.contains("potato"))
            return "minecraft:potato";
        if (lower.contains("pumpkin"))
            return "minecraft:pumpkin";
        if (lower.contains("melon"))
            return "minecraft:melon_slice";
        if (lower.contains("mushroom"))
            return "minecraft:red_mushroom";
        if (lower.contains("cactus"))
            return "minecraft:cactus";
        if (lower.contains("beef"))
            return "minecraft:beef";
        if (lower.contains("pork"))
            return "minecraft:porkchop";
        if (lower.contains("chicken"))
            return "minecraft:chicken";
        if (lower.contains("mutton"))
            return "minecraft:mutton";
        if (lower.contains("rabbit"))
            return "minecraft:rabbit";
        if (lower.contains("spider"))
            return "minecraft:spider_eye";
        if (lower.contains("string"))
            return "minecraft:string";
        if (lower.contains("wool"))
            return "minecraft:white_wool";
        if (lower.contains("pearl"))
            return "minecraft:ender_pearl";
        if (lower.contains("snow"))
            return "minecraft:snowball";
        if (lower.contains("ice"))
            return "minecraft:ice";
        if (lower.contains("glass"))
            return "minecraft:glass";
        if (lower.contains("clay"))
            return "minecraft:clay_ball";
        if (lower.contains("brick"))
            return "minecraft:brick";
        if (lower.contains("paper"))
            return "minecraft:paper";
        if (lower.contains("book"))
            return "minecraft:book";
        if (lower.contains("map"))
            return "minecraft:map";
        if (lower.contains("firework"))
            return "minecraft:firework_rocket";
        if (lower.contains("potion"))
            return "minecraft:potion";
        if (lower.contains("experience") || lower.contains("exp"))
            return "minecraft:experience_bottle";
        if (lower.contains("enchanted_book"))
            return "minecraft:enchanted_book";

        // Final Fallbacks with Material Detection
        String material = "iron";
        if (lower.contains("leather"))
            material = "leather";
        else if (lower.contains("gold"))
            material = "golden";
        else if (lower.contains("diamond"))
            material = "diamond";
        else if (lower.contains("netherite"))
            material = "netherite";
        else if (lower.contains("chain"))
            material = "chainmail";
        else if (lower.contains("stone"))
            material = "stone";
        else if (lower.contains("wood"))
            material = "wooden";

        if (lower.contains("helmet"))
            return "minecraft:" + material + "_helmet";
        if (lower.contains("chestplate"))
            return "minecraft:" + material + "_chestplate";
        if (lower.contains("leggings"))
            return "minecraft:" + material + "_leggings";
        if (lower.contains("boots"))
            return "minecraft:" + material + "_boots";
        if (lower.contains("sword"))
            return "minecraft:" + material + "_sword";
        if (lower.contains("pickaxe"))
            return "minecraft:" + material + "_pickaxe";
        if (lower.contains("axe") && !lower.contains("pickaxe"))
            return "minecraft:" + material + "_axe";
        if (lower.contains("shovel") || lower.contains("spade"))
            return "minecraft:" + material + "_shovel";
        if (lower.contains("hoe"))
            return "minecraft:" + material + "_hoe";
        if (lower.contains("rod"))
            return "minecraft:fishing_rod";
        if (lower.contains("bow"))
            return "minecraft:bow";

        if (lower.contains("egg"))
            return "minecraft:egg";

        if (BomboConfig.get().apiDebug)
            Bomboaddons.LOGGER.info("[Bombo] Unknown SkyBlock ID: " + id);
        return "minecraft:chest";
    }

    private static String mapNumericId(int id) {
        return switch (id) {
            case 0 -> "minecraft:air";
            case 1 -> "minecraft:stone";
            case 2 -> "minecraft:grass_block";
            case 3 -> "minecraft:dirt";
            case 4 -> "minecraft:cobblestone";
            case 5 -> "minecraft:oak_planks";
            case 6 -> "minecraft:oak_sapling";
            case 7 -> "minecraft:bedrock";
            case 12 -> "minecraft:sand";
            case 13 -> "minecraft:gravel";
            case 14 -> "minecraft:gold_ore";
            case 15 -> "minecraft:iron_ore";
            case 16 -> "minecraft:coal_ore";
            case 17 -> "minecraft:oak_log";
            case 18 -> "minecraft:oak_leaves";

            case 20 -> "minecraft:glass";
            case 22 -> "minecraft:lapis_block";
            case 33 -> "minecraft:piston";
            case 35 -> "minecraft:white_wool";

            case 41 -> "minecraft:gold_block";
            case 42 -> "minecraft:iron_block";
            case 44 -> "minecraft:stone_slab";
            case 46 -> "minecraft:tnt";
            case 54 -> "minecraft:chest";
            case 57 -> "minecraft:diamond_block";
            case 58 -> "minecraft:crafting_table";
            case 61 -> "minecraft:furnace";
            case 89 -> "minecraft:glowstone";
            case 116 -> "minecraft:enchanting_table";
            case 130 -> "minecraft:ender_chest";
            case 145 -> "minecraft:anvil";
            case 152 -> "minecraft:redstone_block";
            case 256 -> "minecraft:iron_shovel";
            case 257 -> "minecraft:iron_pickaxe";
            case 258 -> "minecraft:iron_axe";
            case 261 -> "minecraft:bow";
            case 263 -> "minecraft:coal";
            case 264 -> "minecraft:diamond";
            case 265 -> "minecraft:iron_ingot";
            case 266 -> "minecraft:gold_ingot";
            case 267 -> "minecraft:iron_sword";
            case 276 -> "minecraft:diamond_sword";
            case 277 -> "minecraft:diamond_shovel";
            case 278 -> "minecraft:diamond_pickaxe";
            case 279 -> "minecraft:diamond_axe";
            case 280 -> "minecraft:stick";
            case 283 -> "minecraft:gold_sword";
            case 284 -> "minecraft:gold_shovel";
            case 285 -> "minecraft:gold_pickaxe";
            case 286 -> "minecraft:gold_axe";
            case 287 -> "minecraft:string";
            case 288 -> "minecraft:feather";
            case 289 -> "minecraft:gunpowder";
            case 298 -> "minecraft:leather_helmet";
            case 299 -> "minecraft:leather_chestplate";
            case 300 -> "minecraft:leather_leggings";
            case 301 -> "minecraft:leather_boots";
            case 302 -> "minecraft:chainmail_helmet";
            case 303 -> "minecraft:chainmail_chestplate";
            case 304 -> "minecraft:chainmail_leggings";
            case 305 -> "minecraft:chainmail_boots";
            case 306 -> "minecraft:iron_helmet";
            case 307 -> "minecraft:iron_chestplate";
            case 308 -> "minecraft:iron_leggings";
            case 309 -> "minecraft:iron_boots";
            case 310 -> "minecraft:diamond_helmet";
            case 311 -> "minecraft:diamond_chestplate";
            case 312 -> "minecraft:diamond_leggings";
            case 313 -> "minecraft:diamond_boots";
            case 322 -> "minecraft:golden_apple";
            case 331 -> "minecraft:redstone";
            case 339 -> "minecraft:paper";
            case 340 -> "minecraft:book";
            case 341 -> "minecraft:slime_ball";
            case 345 -> "minecraft:compass";
            case 347 -> "minecraft:clock";
            case 348 -> "minecraft:glowstone_dust";
            case 351 -> "minecraft:ink_sac";
            case 358 -> "minecraft:map";
            case 368 -> "minecraft:ender_pearl";
            case 369 -> "minecraft:blaze_rod";
            case 370 -> "minecraft:ghast_tear";
            case 372 -> "minecraft:nether_wart";
            case 373 -> "minecraft:potion";
            case 381 -> "minecraft:ender_eye";
            case 384 -> "minecraft:experience_bottle";
            case 386 -> "minecraft:writable_book";
            case 387 -> "minecraft:written_book";
            case 388 -> "minecraft:emerald";
            case 395 -> "minecraft:empty_map";
            case 399 -> "minecraft:nether_star";
            default -> "minecraft:chest";
        };
    }
    public static void logDebug(String message) {
        System.out.println("[Bombo] " + message);
        try (java.io.FileWriter fw = new java.io.FileWriter("bombo_debug.log", true)) {
            fw.write("[" + new java.util.Date() + "] " + message + "\n");
        } catch (Exception ignored) {
        }
    }
}
