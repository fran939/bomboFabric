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
            boolean hasAny = !localReminders.isEmpty() || me.bombo.bomboaddons.CustomTimerManager.hasTimers();
            if (hasAny) {
                printActiveTimers(mc, false);
            }
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §e§lTimer & Reminder Commands:"));
            mc.player.sendSystemMessage(Component.literal("§7• Set: §b/b timer <duration> [name] §7(e.g. §b/b timer 10s§7, §b/b timer 100§7, §b/b timer 5m boss§7)"));
            mc.player.sendSystemMessage(Component.literal("§7• List: §b/b timer list §7(view active countdowns)"));
            mc.player.sendSystemMessage(Component.literal("§7• Clear: §b/b timer clear §7(cancel active timers)"));
            mc.player.sendSystemMessage(Component.literal("§7• Stopwatch: §b/b timer sw §7(toggle stopwatch HUD)"));
            return;
        }

        String username = mc.getUser().getName();

        // Stopwatch toggle: /b timer sw or /b timer stopwatch
        if (args.equalsIgnoreCase("sw") || args.equalsIgnoreCase("stopwatch")) {
            if (me.bombo.bomboaddons.StopwatchManager.isActive()) {
                if (me.bombo.bomboaddons.StopwatchManager.isPaused()) {
                    me.bombo.bomboaddons.StopwatchManager.togglePause();
                    mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aStopwatch resumed!"));
                } else {
                    me.bombo.bomboaddons.StopwatchManager.stop();
                    mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cStopwatch stopped!"));
                }
            } else {
                me.bombo.bomboaddons.StopwatchManager.start();
                mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aStopwatch started! Open inventory to Pause or Stop."));
            }
            return;
        }

        // Clear: /b timer clear or /reminder clear
        if (args.equalsIgnoreCase("clear") || args.equalsIgnoreCase("reset")) {
            localReminders.clear();
            me.bombo.bomboaddons.CustomTimerManager.clearTimers();
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aCleared all active reminders and on-screen timers."));
            sendPostAsync("clear", username, null, null, null);
            return;
        }

        // List: /b timer list or /reminder list
        if (args.equalsIgnoreCase("list")) {
            printActiveTimers(mc, true);
            sendPostAsync("list", username, null, null, (success, resp) -> {
                mc.execute(() -> {
                    if (resp != null && resp.has("timers")) {
                        JsonArray arr = resp.getAsJsonArray("timers");
                        if (arr.size() > 0) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §6Discord Bot Timers (" + arr.size() + "):"));
                            for (JsonElement el : arr) {
                                if (el.isJsonObject()) {
                                    JsonObject obj = el.getAsJsonObject();
                                    String rem = obj.has("reminder") ? obj.get("reminder").getAsString() : "";
                                    String formatted = obj.has("formattedRemaining") ? obj.get("formattedRemaining").getAsString() : "";
                                    mc.player.sendSystemMessage(Component.literal("§8- §e\"§f" + rem + "§e\" §7in §a" + formatted + " §7(Discord DM)"));
                                }
                            }
                        }
                    }
                });
            });
            return;
        }

        // Setting a reminder / timer
        ParsedDuration parsed = parseDuration(args);
        if (parsed == null || parsed.totalSeconds <= 0) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cInvalid duration! Examples: §e10s§c, §e100§c, §e5m§c, §e1h 30m§c. Usage: §b/b timer <duration> [name]"));
            return;
        }

        String durationStr = parsed.durationStr;
        String reminderText = (parsed.reminderText != null && !parsed.reminderText.trim().isEmpty())
                ? parsed.reminderText.trim()
                : "Timer";
        long durationSec = parsed.totalSeconds;
        long durationMs = durationSec * 1000L;

        // Register local reminder for title banner, bell & sound alert
        localReminders.add(new ActiveReminder(System.currentTimeMillis() + durationMs, reminderText, durationStr));

        // Start in CustomTimerManager so it displays ON SCREEN on the HUD!
        me.bombo.bomboaddons.CustomTimerManager.startTimer(reminderText, durationMs);

        // Immediate chat feedback
        mc.player.sendSystemMessage(Component.literal(
                "§8[§bBomboAddons§8] §a⏱️ Timer for \"§e" + reminderText + "§a\" set for ~§e" + durationStr + "§a! §7(HUD + Title + Discord)"));

        // Register with server bot for Discord DM notifications
        sendPostAsync("set", username, durationStr, reminderText, null);
    }

    private static void printActiveTimers(Minecraft mc, boolean showEmptyMessage) {
        long now = System.currentTimeMillis();
        List<String> lines = new java.util.ArrayList<>();

        List<me.bombo.bomboaddons.CustomTimerManager.CustomTimer> customTimers = me.bombo.bomboaddons.CustomTimerManager.activeTimers;
        for (me.bombo.bomboaddons.CustomTimerManager.CustomTimer t : customTimers) {
            if (t.ready) {
                lines.add("§8- §e\"§f" + t.name + "§e\" §a§l[READY] §7(HUD)");
            } else if (t.endTime > now) {
                long remSec = (t.endTime - now + 999L) / 1000L;
                lines.add("§8- §e\"§f" + t.name + "§e\" §7in §a" + formatSeconds(remSec) + " §7(HUD)");
            }
        }
        for (ActiveReminder r : localReminders) {
            long remSec = (r.expireTimeMs - now + 999L) / 1000L;
            if (remSec > 0) {
                boolean alreadyListed = false;
                for (me.bombo.bomboaddons.CustomTimerManager.CustomTimer t : customTimers) {
                    if (t.name.equalsIgnoreCase(r.reminder)) {
                        alreadyListed = true;
                        break;
                    }
                }
                if (!alreadyListed) {
                    lines.add("§8- §b\"§f" + r.reminder + "§b\" §7in §a" + formatSeconds(remSec) + " §7(Alert)");
                }
            }
        }

        if (!lines.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §6Active Timers (" + lines.size() + "):"));
            for (String line : lines) {
                mc.player.sendSystemMessage(Component.literal(line));
            }
        } else if (showEmptyMessage) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7No active timers."));
        }
    }

    private static class ParsedDuration {
        long totalSeconds;
        String durationStr;
        String reminderText;
    }

    private static ParsedDuration parseDuration(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String trimmed = input.trim();

        // 1. Pure number of seconds: e.g. "100" or "60"
        if (trimmed.matches("^\\d+$")) {
            try {
                long sec = Long.parseLong(trimmed);
                if (sec > 0) {
                    ParsedDuration p = new ParsedDuration();
                    p.totalSeconds = sec;
                    p.durationStr = formatSeconds(sec);
                    p.reminderText = "Timer";
                    return p;
                }
            } catch (Exception ignored) {}
        }

        // 2. Number of seconds followed by name: e.g. "100 boss" or "60 awa"
        if (trimmed.matches("^\\d+\\s+.*$")) {
            int sp = trimmed.indexOf(' ');
            try {
                long sec = Long.parseLong(trimmed.substring(0, sp).trim());
                if (sec > 0) {
                    ParsedDuration p = new ParsedDuration();
                    p.totalSeconds = sec;
                    p.durationStr = formatSeconds(sec);
                    p.reminderText = trimmed.substring(sp + 1).trim();
                    return p;
                }
            } catch (Exception ignored) {}
        }

        // 3. Name followed by number of seconds: e.g. "boss 100" or "awa 60"
        if (trimmed.matches("^.*\\s+\\d+$")) {
            int sp = trimmed.lastIndexOf(' ');
            try {
                long sec = Long.parseLong(trimmed.substring(sp + 1).trim());
                if (sec > 0) {
                    ParsedDuration p = new ParsedDuration();
                    p.totalSeconds = sec;
                    p.durationStr = formatSeconds(sec);
                    p.reminderText = trimmed.substring(0, sp).trim();
                    return p;
                }
            } catch (Exception ignored) {}
        }

        // 4. Matches tokens like "10s", "5m", "2h", "1d", "30min", "45sec" at beginning: e.g. "10s", "5m boss", "1h 30m forge"
        Pattern tokenPattern = Pattern.compile("(?i)^((?:\\d+\\s*(?:d|h|m|s|min|sec|minute|hour|day)s?\\s*)+)(.*)$");
        Matcher m = tokenPattern.matcher(trimmed);
        if (m.find()) {
            String durPart = m.group(1).trim();
            String remPart = m.group(2).trim();
            long sec = parseDurationTokens(durPart);
            if (sec > 0) {
                ParsedDuration p = new ParsedDuration();
                p.totalSeconds = sec;
                p.durationStr = formatSeconds(sec);
                p.reminderText = remPart.isEmpty() ? "Timer" : remPart;
                return p;
            }
        }

        // 5. Matches tokens at end: e.g. "boss 5m" or "awa 10s"
        Pattern endPattern = Pattern.compile("(?i)^(.*?)\\s+((?:\\d+\\s*(?:d|h|m|s|min|sec|minute|hour|day)s?\\s*)+)$");
        Matcher em = endPattern.matcher(trimmed);
        if (em.find()) {
            String remPart = em.group(1).trim();
            String durPart = em.group(2).trim();
            long sec = parseDurationTokens(durPart);
            if (sec > 0) {
                ParsedDuration p = new ParsedDuration();
                p.totalSeconds = sec;
                p.durationStr = formatSeconds(sec);
                p.reminderText = remPart.isEmpty() ? "Timer" : remPart;
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
