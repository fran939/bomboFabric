package me.bombo.bomboaddons.features.mining;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.features.StorageTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag.Default;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForgeTimerManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(Minecraft.getInstance().gameDirectory, "config/bomboaddons/forge_timers.json");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)(?:(\\d+)\\s*d)?\\s*(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?");
    private static final Pattern SLOT_NUM_PATTERN = Pattern.compile("(?i)(?:slot\\s*#?\\s*(\\d+)|#(\\d+))");

    public static class ForgeSlot {
        public int slotIndex = 1;
        public String itemName = "";
        public long finishTimestamp = 0L;
        public boolean finished = false;
        public boolean empty = true;
        public boolean locked = false;
        public boolean notified = false;

        public long getRemainingSeconds() {
            if (finished) return 0L;
            if (empty || locked || finishTimestamp <= 0L) return -1L;
            long delta = (finishTimestamp - System.currentTimeMillis()) / 1000L;
            return Math.max(0L, delta);
        }

        public String getFormattedRemaining() {
            if (finished) return "§aReady!";
            if (empty) return "§8Empty";
            if (locked) return "§cLocked";
            long s = getRemainingSeconds();
            if (s <= 0) return "§aReady!";
            long d = s / 86400;
            long h = (s % 86400) / 3600;
            long m = (s % 3600) / 60;
            long sec = s % 60;
            if (d > 0) return d + "d " + h + "h " + m + "m";
            if (h > 0) return h + "h " + m + "m " + sec + "s";
            if (m > 0) return m + "m " + sec + "s";
            return sec + "s";
        }
    }

    // Key format: "username:profileId" (e.g. "fran938:coconut", "bomboclas:apple")
    public static final Map<String, List<ForgeSlot>> accountSlots = new ConcurrentHashMap<>();
    private static long lastTickCheck = 0L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void init() {
        load();
    }

    public static String getCurrentAccountKey() {
        Minecraft mc = Minecraft.getInstance();
        String user = "default";
        if (mc != null && mc.getUser() != null) {
            String u = mc.getUser().getName();
            if (u != null && !u.isEmpty()) user = u.toLowerCase(Locale.ROOT);
        }
        String prof = StorageTracker.getActiveProfileId();
        if (prof == null || prof.isEmpty()) prof = "default";
        return user + ":" + prof;
    }

    public static String getCurrentUsername() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            String u = mc.getUser().getName();
            if (u != null && !u.isEmpty()) return u;
        }
        return "player";
    }

    public static synchronized List<ForgeSlot> getCurrentSlots() {
        String key = getCurrentAccountKey();
        return accountSlots.computeIfAbsent(key, k -> new ArrayList<>());
    }

    public static synchronized List<ForgeSlot> getUnlockedSlots() {
        List<ForgeSlot> current = getCurrentSlots();
        List<ForgeSlot> list = new ArrayList<>();
        for (ForgeSlot slot : current) {
            if (!slot.locked) {
                list.add(slot);
            }
        }
        return list;
    }

    public static synchronized ForgeSlot getClosestSlot() {
        List<ForgeSlot> current = getCurrentSlots();
        ForgeSlot closest = null;
        long lowestRemaining = Long.MAX_VALUE;
        for (ForgeSlot slot : current) {
            if (!slot.locked && !slot.empty) {
                long rem = slot.getRemainingSeconds();
                if (rem >= 0 && rem < lowestRemaining) {
                    lowestRemaining = rem;
                    closest = slot;
                }
            }
        }
        return closest;
    }

    public static void onContainerTick(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        String rawTitle = screen.getTitle().getString();
        String title = ChatFormatting.stripFormatting(rawTitle);
        if (!title.equalsIgnoreCase("The Forge") && !title.contains("The Forge")) return;

        boolean changed = false;
        synchronized (ForgeTimerManager.class) {
            List<ForgeSlot> slots = getCurrentSlots();
            int detectedSlotNumber = 1;
            for (Slot s : screen.getMenu().slots) {
                if (s == null || s.index >= 54 || !s.hasItem()) continue;
                ItemStack stack = s.getItem();
                String hoverName = ChatFormatting.stripFormatting(stack.getHoverName().getString());
                List<Component> tooltipLines = stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, Default.NORMAL);

                boolean isForgeSlotItem = false;
                boolean isLocked = false;
                boolean isEmpty = false;
                boolean isFinished = false;
                String makingItem = null;
                long secondsRemaining = -1;

                if (hoverName.contains("Forge Slot") || hoverName.contains("Slot #") || hoverName.contains("The Forge")) {
                    isForgeSlotItem = true;
                }

                for (Component lineComp : tooltipLines) {
                    String line = ChatFormatting.stripFormatting(lineComp.getString()).trim();
                    if (line.isEmpty()) continue;

                    if (line.contains("Requires Heart of the Mountain") || line.contains("Requires HotM")
                            || line.contains("Requires Tier") || hoverName.contains("Locked")) {
                        isLocked = true;
                        isForgeSlotItem = true;
                    }

                    if (line.contains("Nothing is being forged") || line.contains("Click an item from your inventory to forge it")
                            || hoverName.contains("Empty")) {
                        isEmpty = true;
                        isForgeSlotItem = true;
                    }

                    if (line.contains("Ready to collect!") || line.contains("Click to collect!") || line.contains("Item Finished!")) {
                        isFinished = true;
                        isForgeSlotItem = true;
                    }

                    if (line.startsWith("Currently making:") || line.startsWith("Currently Crafting:")) {
                        isForgeSlotItem = true;
                        makingItem = line.substring(line.indexOf(':') + 1).trim();
                    }

                    if (line.startsWith("Time Remaining:") || line.startsWith("Remaining:")) {
                        isForgeSlotItem = true;
                        String timePart = line.substring(line.indexOf(':') + 1).trim();
                        secondsRemaining = parseRemainingSeconds(timePart);
                    }
                }

                if (isFinished && (makingItem == null || makingItem.isEmpty())) {
                    for (Component lineComp : tooltipLines) {
                        String line = ChatFormatting.stripFormatting(lineComp.getString()).trim();
                        if (line.isEmpty() || line.startsWith("Slot #") || line.contains("Click to") || line.contains("Finished") || line.contains("Ready") || line.contains("Closing the menu")) continue;
                        makingItem = line;
                        break;
                    }
                }

                if (isForgeSlotItem) {
                    int slotIdx = extractSlotNumber(hoverName, detectedSlotNumber);
                    detectedSlotNumber++;

                    ForgeSlot existing = findSlot(slots, slotIdx);
                    if (existing == null) {
                        existing = new ForgeSlot();
                        existing.slotIndex = slotIdx;
                        slots.add(existing);
                    }

                    existing.locked = isLocked;
                    existing.empty = isEmpty;

                    if (isLocked) {
                        existing.itemName = "";
                        existing.finishTimestamp = 0L;
                        existing.finished = false;
                    } else if (isEmpty) {
                        existing.itemName = "";
                        existing.finishTimestamp = 0L;
                        existing.finished = false;
                        existing.notified = false;
                    } else if (isFinished || secondsRemaining == 0) {
                        if (makingItem != null && !makingItem.isEmpty()) {
                            existing.itemName = makingItem;
                        } else if (existing.itemName.isEmpty() && !hoverName.contains("Slot")) {
                            existing.itemName = hoverName;
                        }
                        existing.finished = true;
                        existing.finishTimestamp = System.currentTimeMillis();
                    } else if (secondsRemaining > 0) {
                        if (makingItem != null && !makingItem.isEmpty()) {
                            existing.itemName = makingItem;
                        } else if (existing.itemName.isEmpty() && !hoverName.contains("Slot")) {
                            existing.itemName = hoverName;
                        }
                        existing.finished = false;
                        existing.notified = false;
                        existing.finishTimestamp = System.currentTimeMillis() + (secondsRemaining * 1000L);
                    }
                    changed = true;
                }
            }

            if (changed) {
                slots.sort(Comparator.comparingInt(a -> a.slotIndex));
                save();
            }
        }
    }

    public static void onClientTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastTickCheck < 1000L) return;
        lastTickCheck = now;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        String currentKey = getCurrentAccountKey();

        synchronized (ForgeTimerManager.class) {
            boolean changed = false;
            for (Map.Entry<String, List<ForgeSlot>> entry : accountSlots.entrySet()) {
                String accKey = entry.getKey();
                List<ForgeSlot> slots = entry.getValue();
                boolean isCurrent = accKey.equalsIgnoreCase(currentKey);

                String[] keyParts = accKey.split(":");
                String accUser = keyParts.length > 0 ? keyParts[0] : "player";
                String accProf = keyParts.length > 1 ? keyParts[1] : "default";

                for (ForgeSlot slot : slots) {
                    if (slot.locked || slot.empty) continue;

                    if (!slot.finished && slot.finishTimestamp > 0L && now >= slot.finishTimestamp) {
                        slot.finished = true;
                        changed = true;
                    }

                    if (slot.finished && !slot.notified) {
                        slot.notified = true;
                        changed = true;

                        // Check for bundling / debounce (items finishing within 3 minutes)
                        String bundleSuffix = "";
                        if (s.forgeConsolidateNotifications) {
                            long windowEnd = now + 180_000L; // 3 minutes
                            for (ForgeSlot other : slots) {
                                if (other != slot && !other.locked && !other.empty && !other.finished) {
                                    if (other.finishTimestamp > now && other.finishTimestamp <= windowEnd) {
                                        long deltaSec = (other.finishTimestamp - now) / 1000L;
                                        long m = deltaSec / 60;
                                        long sec = deltaSec % 60;
                                        String deltaStr = m > 0 ? (m + "m " + sec + "s") : (sec + "s");
                                        bundleSuffix = " §7(§e" + other.itemName + " §7done in §b" + deltaStr + "§7)";
                                        break;
                                    }
                                }
                            }
                        }

                        triggerAlerts(mc, s, slot, bundleSuffix, accUser, accProf, isCurrent);
                    }
                }
            }

            if (changed) {
                save();
            }
        }
    }

    private static void triggerAlerts(Minecraft mc, BomboConfig.Settings s, ForgeSlot slot, String bundleSuffix, String accUser, String accProf, boolean isCurrent) {
        String itemName = slot.itemName.isEmpty() ? ("Slot #" + slot.slotIndex) : slot.itemName;
        String accPrefix = isCurrent ? "" : ("§8[§b" + accUser + " - " + accProf + "§8] ");

        // 1. Chat Warning
        if (s.forgeChatWarning && mc.player != null) {
            Component warpButton = Component.literal("§b§l[Click to Warp]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.RunCommand("/warp forge"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run /warp forge"))));

            Component msg = Component.literal("§8[§6🔨 Forge§8] " + accPrefix + "§aYour §e" + itemName + " §ais ready at the Forge!" + bundleSuffix + " ")
                    .append(warpButton);
            mc.player.sendSystemMessage(msg);
        }

        // 2. Screen Title Warning
        if (s.forgeScreenWarning) {
            mc.gui.hud.setTitle(Component.literal("§6§lForge Item Ready!"));
            String subtitle = isCurrent ? ("§e" + itemName + " §ais ready to collect!") : ("§b[" + accUser + "] §e" + itemName + " §ais ready!");
            mc.gui.hud.setSubtitle(Component.literal(subtitle));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f));
        }

        // 3. Discord Bot Alert
        if (s.forgeDiscordAlert) {
            String cleanBundle = ChatFormatting.stripFormatting(bundleSuffix);
            String userTag = isCurrent ? "" : (" (" + accUser + " - " + accProf + ")");
            String discordMsg = "🔨 **[Forge Reminder]** Your **" + itemName + "**" + userTag + " is ready at the Forge!" + cleanBundle;
            sendDiscordAlertAsync(discordMsg, accUser);
        }
    }

    private static void sendDiscordAlertAsync(String message, String username) {
        new Thread(() -> {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("message", message);
                payload.put("user", username);
                String json = GSON.toJson(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.bombo.dpdns.org/api/bot/forge-alert"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(4))
                        .build();
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
                // Fallback to direct port 6668 if domain proxy has transient issue
                try {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("message", message);
                    payload.put("user", username);
                    String json = GSON.toJson(payload);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://ssh.bombo.dpdns.org:6668/api/bot/forge-alert"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .timeout(Duration.ofSeconds(4))
                            .build();
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                } catch (Throwable ignored2) {}
            }
        }, "ForgeDiscordAlertThread").start();
    }

    public static void printForgeTimersToChat(Minecraft mc) {
        if (mc.player == null) return;
        String curKey = getCurrentAccountKey();

        synchronized (ForgeTimerManager.class) {
            if (accountSlots.isEmpty() || (accountSlots.size() == 1 && getCurrentSlots().isEmpty())) {
                mc.player.sendSystemMessage(Component.literal("§8[§6🔨 Forge§8] §7No active Forge slots recorded yet. Open §eThe Forge §7GUI in Dwarven Mines to sync!"));
                return;
            }

            mc.player.sendSystemMessage(Component.literal("§6§l--------- 🔨 Dwarven Mines Forge Timers ---------"));
            for (Map.Entry<String, List<ForgeSlot>> entry : accountSlots.entrySet()) {
                String accKey = entry.getKey();
                List<ForgeSlot> slots = entry.getValue();
                if (slots.isEmpty()) continue;

                boolean isCurrent = accKey.equalsIgnoreCase(curKey);
                String[] parts = accKey.split(":");
                String accUser = parts.length > 0 ? parts[0] : "player";
                String accProf = parts.length > 1 ? parts[1] : "default";

                String header = "§e" + accUser + " §7(Profile: §b" + accProf + "§7)" + (isCurrent ? " §a[Active]" : "");
                mc.player.sendSystemMessage(Component.literal(header));

                for (ForgeSlot slot : slots) {
                    if (slot.locked) continue;
                    String status = slot.getFormattedRemaining();
                    String name = slot.itemName.isEmpty() ? "Empty Slot" : slot.itemName;
                    mc.player.sendSystemMessage(Component.literal("  §7• §eSlot #" + slot.slotIndex + ": §f" + name + " §7- " + status));
                }
            }

            Component warpButton = Component.literal("§b§l[Click to Warp to Forge]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.RunCommand("/warp forge"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to warp to Forge"))));
            mc.player.sendSystemMessage(Component.literal(" ").append(warpButton));
        }
    }

    private static ForgeSlot findSlot(List<ForgeSlot> list, int index) {
        for (ForgeSlot slot : list) {
            if (slot.slotIndex == index) return slot;
        }
        return null;
    }

    private static int extractSlotNumber(String text, int fallback) {
        if (text == null) return fallback;
        Matcher m = SLOT_NUM_PATTERN.matcher(text);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            String numStr = g1 != null ? g1 : g2;
            try {
                return Integer.parseInt(numStr);
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static long parseRemainingSeconds(String text) {
        if (text == null || text.isEmpty()) return -1L;
        if (text.equalsIgnoreCase("0s") || text.contains("Ready") || text.contains("Finished")) return 0L;

        Matcher m = DURATION_PATTERN.matcher(text);
        if (m.find()) {
            long total = 0L;
            String d = m.group(1);
            String h = m.group(2);
            String min = m.group(3);
            String s = m.group(4);

            if (d != null) total += Long.parseLong(d) * 86400L;
            if (h != null) total += Long.parseLong(h) * 3600L;
            if (min != null) total += Long.parseLong(min) * 60L;
            if (s != null) total += Long.parseLong(s);
            if (total > 0) return total;
        }
        return -1L;
    }

    private static synchronized void save() {
        try {
            FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(accountSlots, writer);
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void load() {
        if (!FILE.exists()) return;
        try (FileReader reader = new FileReader(FILE)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                Type mapType = new TypeToken<Map<String, List<ForgeSlot>>>() {}.getType();
                Map<String, List<ForgeSlot>> loadedMap = GSON.fromJson(root, mapType);
                if (loadedMap != null) {
                    accountSlots.clear();
                    accountSlots.putAll(loadedMap);
                }
            } else if (root.isJsonArray()) {
                // Backward compatibility: load array into current account
                Type listType = new TypeToken<List<ForgeSlot>>() {}.getType();
                List<ForgeSlot> loadedList = GSON.fromJson(root, listType);
                if (loadedList != null) {
                    accountSlots.clear();
                    accountSlots.put(getCurrentAccountKey(), loadedList);
                }
            }
        } catch (Throwable ignored) {}
    }
}
