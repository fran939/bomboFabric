package me.bombo.bomboaddons.features.misc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReminderManager {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private static final Gson GSON = new Gson();

    public static class ActiveReminder {
        public long expireTimeMs;
        public String reminder;
        public String durationStr;

        public ActiveReminder(long expireTimeMs, String reminder, String durationStr) {
            this.expireTimeMs = expireTimeMs;
            this.reminder = reminder;
            this.durationStr = durationStr;
        }
    }

    private static final List<ActiveReminder> localReminders = new CopyOnWriteArrayList<>();

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        for (Iterator<ActiveReminder> it = localReminders.iterator(); it.hasNext(); ) {
            ActiveReminder r = it.next();
            if (now >= r.expireTimeMs) {
                localReminders.remove(r);
                mc.execute(() -> {
                    try {
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f));
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 1.0f));
                        mc.gui.hud.setTitle(Component.literal("§6§lTimer Reminder!"));
                        String sub = r.reminder != null && !r.reminder.isEmpty() ? "§e\"" + r.reminder + "\"" : "§eYour timer is up!";
                        mc.gui.hud.setSubtitle(Component.literal(sub));
                        mc.player.sendSystemMessage(Component.literal(
                                "§8[§bBomboAddons§8] §6⏰ [Timer Reminder] §f" + (r.reminder.isEmpty() ? "Your timer is up!" : "\"" + r.reminder + "\" (" + r.durationStr + ")")));
                    } catch (Throwable ignored) {}
                });
            }
        }
    }

    public static void handleCommand(String rawArgs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String args = (rawArgs != null) ? rawArgs.trim() : "";
        if (args.isEmpty() || args.equalsIgnoreCase("help")) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §e§lTimer & Reminder Commands:"));
            mc.player.sendSystemMessage(Component.literal("§7/reminder <duration> <text> §8- §fSet a reminder with in-game title & Discord DM (e.g. §b/reminder 10s awa§f or §b/timer 2h 30m forge§f)"));
            mc.player.sendSystemMessage(Component.literal("§7/reminder list §8- §fView active timers and countdowns"));
            mc.player.sendSystemMessage(Component.literal("§7/reminder clear §8- §fCancel all active timers"));
            return;
        }

        String username = mc.getUser().getName();

        if (args.equalsIgnoreCase("clear") || args.equalsIgnoreCase("cancel")) {
            localReminders.clear();
            sendPostAsync("clear", username, null, null, (success, resp) -> {
                mc.execute(() -> {
                    mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aCleared all active reminders and timers."));
                });
            });
            return;
        }

        if (args.equalsIgnoreCase("list")) {
            sendPostAsync("list", username, null, null, (success, resp) -> {
                mc.execute(() -> {
                    if (resp != null && resp.has("timers")) {
                        JsonArray arr = resp.getAsJsonArray("timers");
                        if (arr.size() == 0 && localReminders.isEmpty()) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7No active timers."));
                            return;
                        }
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §6Active Timers (" + (arr.size() + localReminders.size()) + "):"));
                        for (JsonElement el : arr) {
                            if (el.isJsonObject()) {
                                JsonObject obj = el.getAsJsonObject();
                                String rem = obj.has("reminder") ? obj.get("reminder").getAsString() : "";
                                String formatted = obj.has("formattedRemaining") ? obj.get("formattedRemaining").getAsString() : "";
                                mc.player.sendSystemMessage(Component.literal("§8- §e\"§f" + rem + "§e\" §7in §a" + formatted + " §7(Discord DM)"));
                            }
                        }
                        for (ActiveReminder r : localReminders) {
                            long remSec = Math.max(0, (r.expireTimeMs - System.currentTimeMillis()) / 1000);
                            String str = formatSeconds(remSec);
                            mc.player.sendSystemMessage(Component.literal("§8- §b\"§f" + r.reminder + "§b\" §7in §a" + str + " §7(In-Game)"));
                        }
                    } else if (!localReminders.isEmpty()) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §6Active Local Timers (" + localReminders.size() + "):"));
                        for (ActiveReminder r : localReminders) {
                            long remSec = Math.max(0, (r.expireTimeMs - System.currentTimeMillis()) / 1000);
                            mc.player.sendSystemMessage(Component.literal("§8- §b\"§f" + r.reminder + "§b\" §7in §a" + formatSeconds(remSec)));
                        }
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7No active timers found."));
                    }
                });
            });
            return;
        }

        // Setting a reminder: parse duration & text
        ParsedDuration parsed = parseDuration(args);
        if (parsed == null || parsed.totalSeconds <= 0) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cInvalid duration! Examples: §e10s§c, §e5m§c, §e1h 30m§c. Usage: §b/reminder <time> <text>"));
            return;
        }

        String durationStr = parsed.durationStr;
        String reminderText = parsed.reminderText;
        long durationSec = parsed.totalSeconds;

        // Register local reminder for instant in-game title & sound
        localReminders.add(new ActiveReminder(System.currentTimeMillis() + (durationSec * 1000L), reminderText, durationStr));

        // Register with server bot for Discord DM notifications
        sendPostAsync("set", username, durationStr, reminderText, (success, resp) -> {
            mc.execute(() -> {
                String label = reminderText.isEmpty() ? "" : " for \"" + reminderText + "\"";
                if (success) {
                    mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §a⏱️ Reminder" + label + " set for ~" + durationStr + "! §7(In-game alert + Discord DM)"));
                } else {
                    mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §a⏱️ Reminder" + label + " set for ~" + durationStr + "! §7(In-game alert active)"));
                }
            });
        });
    }

    private static class ParsedDuration {
        long totalSeconds;
        String durationStr;
        String reminderText;
    }

    private static ParsedDuration parseDuration(String input) {
        // Matches tokens like "10s", "5m", "2h", "1d", "30min", "45sec", etc. at beginning or end
        Pattern tokenPattern = Pattern.compile("(?i)^((?:\\d+\\s*(?:d|h|m|s|min|sec|minute|hour|day)s?\\s*)+)(.*)$");
        Matcher m = tokenPattern.matcher(input.trim());
        if (m.find()) {
            String durPart = m.group(1).trim();
            String remPart = m.group(2).trim();
            long sec = parseDurationTokens(durPart);
            if (sec > 0) {
                ParsedDuration p = new ParsedDuration();
                p.totalSeconds = sec;
                p.durationStr = formatSeconds(sec);
                p.reminderText = remPart;
                return p;
            }
        }

        // Try duration at end: e.g. "awa 10s"
        Pattern endPattern = Pattern.compile("(?i)^(.*?)\\s+((?:\\d+\\s*(?:d|h|m|s|min|sec|minute|hour|day)s?\\s*)+)$");
        Matcher em = endPattern.matcher(input.trim());
        if (em.find()) {
            String remPart = em.group(1).trim();
            String durPart = em.group(2).trim();
            long sec = parseDurationTokens(durPart);
            if (sec > 0) {
                ParsedDuration p = new ParsedDuration();
                p.totalSeconds = sec;
                p.durationStr = formatSeconds(sec);
                p.reminderText = remPart;
                return p;
            }
        }

        return null;
    }

    private static long parseDurationTokens(String s) {
        long total = 0;
        Pattern p = Pattern.compile("(?i)(\\d+)\\s*(d|h|m|s|min|sec|minute|hour|day)");
        Matcher m = p.matcher(s);
        while (m.find()) {
            long val = Long.parseLong(m.group(1));
            String unit = m.group(2).toLowerCase();
            if (unit.startsWith("d")) total += val * 86400L;
            else if (unit.startsWith("h")) total += val * 3600L;
            else if (unit.startsWith("m")) total += val * 60L;
            else if (unit.startsWith("s")) total += val;
        }
        return total;
    }

    private static String formatSeconds(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long d = totalSeconds / 86400;
        long h = (totalSeconds % 86400) / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || (d == 0 && h == 0)) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    private interface ResponseCallback {
        void onResponse(boolean success, JsonObject json);
    }

    private static void sendPostAsync(String action, String user, String duration, String text, ResponseCallback callback) {
        new Thread(() -> {
            try {
                Map<String, Object> map = new HashMap<>();
                map.put("action", action);
                map.put("user", user);
                if (duration != null) map.put("duration", duration);
                if (text != null) map.put("text", text);
                String body = GSON.toJson(map);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.bombo.dpdns.org/api/bot/timer"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(4))
                        .build();

                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (callback != null) callback.onResponse(true, json);
                    return;
                }
            } catch (Throwable ignored) {
                // Fallback to direct port
                try {
                    Map<String, Object> map = new HashMap<>();
                    map.put("action", action);
                    map.put("user", user);
                    if (duration != null) map.put("duration", duration);
                    if (text != null) map.put("text", text);
                    String body = GSON.toJson(map);

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://ssh.bombo.dpdns.org:6668/api/bot/timer"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(4))
                            .build();

                    HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                        if (callback != null) callback.onResponse(true, json);
                        return;
                    }
                } catch (Throwable ignored2) {}
            }
            if (callback != null) callback.onResponse(false, null);
        }, "ReminderPostThread").start();
    }
}
