package me.bombo.bomboaddons.features.chat;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Constants;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.text.SimpleDateFormat;
import java.util.*;

public class ChatHistoryTracker {

    public enum Status {
        ALLOWED("ALLOWED", 0xFF22C55E),
        BLOCKED("BLOCKED", 0xFFEF4444),
        OUTGOING("OUTGOING", 0xFF38BDF8),
        /** Raised by a mod feature itself (e.g. an auto sequence starting), not by chat. */
        EVENT("EVENT", 0xFFA855F7),
        /** A feature event whose own chat line was suppressed by another mod. */
        BLOCKED_EVENT("EVENT", 0xFFA855F7);

        public final String label;
        public final int color;

        Status(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public static class Entry {
        public long id = System.nanoTime();
        public long timestamp = System.currentTimeMillis();
        public String timeFormatted;
        public Status status;
        public String category = "CHAT";
        public Component component;
        public String rawText = "";
        public String callerModId = null;
        public String callerModName = null;
        public String callerFrame = null;
        public String featureName = null;
        public String clickAction = null;
        public String hoverText = null;
        public boolean isBombo = false;
        public boolean isMod = false;

        // --- Feature event metadata (only populated for Status.EVENT rows) ---
        /** Which part of the mod raised the event, e.g. "Auto Sequences Manager". */
        public String originFeature = null;
        /** What triggered it, e.g. "Keybind TAB" or "Mouse Button 5". */
        public String triggerDesc = null;
        /** True when this row came from a mod feature rather than chat. */
        public boolean isEvent = false;
    }

    /** Minimum gap between two identical feature events, in milliseconds. */
    private static final long EVENT_DEDUP_MS = 250L;

    /**
     * Trigger context for an outgoing message, set by whatever feature is about to send one
     * (a keybind handler, the clicker, a sequence executor...) and consumed by the next
     * {@link #recordOutgoing} call on the same thread.
     *
     * <p>Without this, an outgoing command fired by a keybind stack-walks through our own
     * dispatch plumbing and gets mis-attributed ("created by null") because no frame in the
     * stack can honestly claim authorship - the real origin was a key press, not a frame.
     */
    public static final ThreadLocal<String> OUTGOING_TRIGGER = new ThreadLocal<>();
    /** Set when the outgoing send was initiated by the player typing in a chat screen. */
    public static final ThreadLocal<Boolean> OUTGOING_PLAYER_INPUT = ThreadLocal.withInitial(() -> false);

    /**
     * How long a recorded feature event stays eligible to "adopt" the chat line that
     * reports the same thing. Without this, every feature event showed up twice: once as the
     * event row (with origin/trigger) and once as the raw {@code [BomboAddons]} chat line
     * (with none of that context).
     */
    private static final long EVENT_CHAT_ADOPT_MS = 2000L;

    /**
     * Frames that belong to the chat plumbing rather than to the code that produced a
     * message. Walking past these is what stops every server message being attributed to
     * BomboAddons just because our own mixin/hook sits in the call path.
     */
    private static boolean isInfrastructure(String cls, String method) {
        if (cls.contains("ChatHistoryTracker")) return true;
        if (cls.contains("ChatHistoryScreen")) return true;
        if (cls.contains("bomboaddons.mixin")) return true;
        if (cls.contains("ChatMessageTracker")) return true;
        if (cls.equals("me.bombo.bomboaddons.BomboaddonsClient")) return true;
        if (method.equals("recordIncoming") || method.equals("recordOutgoing") || method.equals("recordEvent")) return true;
        if (method.startsWith("onChatMessage") || method.startsWith("routeChat") || method.startsWith("handleChat")) return true;
        return false;
    }

    /** Methods that prove a message was delivered over the network rather than fabricated. */
    private static final List<String> NETWORK_HANDLERS = List.of(
            "handleSystemChat", "handleDisguisedChat", "handlePlayerChat", "handleChatMessage",
            "handleCustomPayload", "handlePlayerInfoUpdate", "handleTabList");

    private static final List<Entry> entries = new ArrayList<>();
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");
    private static final Map<String, Long> recentMessageTimes = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 200;
        }
    };

    public static synchronized List<Entry> getEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * Messages dropped from the front of the buffer by the ring-buffer trim. Shown in the
     * history header so "why is the oldest message different now" has a visible answer.
     */
    private static int trimmedCount = 0;

    public static synchronized int getTrimmedCount() {
        return trimmedCount;
    }

    public static synchronized void clear() {
        entries.clear();
        recentMessageTimes.clear();
        trimmedCount = 0;
    }

    /**
     * Records a session boundary (world switch, server change, disconnect) as a visible row
     * instead of wiping the buffer.
     *
     * <p>History is <b>never</b> cleared implicitly: switching lobbies or servers used to make the
     * user lose everything they were reading. When {@code persistHistoryAcrossServers} is on
     * (the default) a boundary row is the only trace; when it is off the same row is written but
     * the buffer is still kept, so nothing is destroyed behind the user's back.
     */
    public static synchronized void markSessionBoundary(String reason) {
        Entry entry = new Entry();
        entry.timeFormatted = TIME_FMT.format(new Date());
        entry.status = Status.EVENT;
        entry.category = "SESSION";
        entry.isEvent = true;
        entry.featureName = "Session";
        entry.originFeature = "Chat History";
        entry.triggerDesc = reason;
        entry.rawText = "--- session boundary: " + reason + " ---";
        entry.component = Component.literal("§8--- session boundary: §7" + reason + " §8---");
        addEntry(entry);
    }

    public static synchronized void recordIncoming(Component comp, boolean blocked, String category) {
        if (comp == null) return;
        String raw = comp.getString();
        if (raw.contains("DailyRewardDebug")) return;

        // A message the mod itself printed ("[BomboAddons] Started auto sequence: X"). Rather
        // than dropping it, attach it to the event that caused it so the row carries the real
        // origin and trigger ("Keybind TAB", "Config GUI RUN button", ...).
        //
        // The check runs on the *unformatted* text: our own prefix is written as
        // "§8[§bBomboAddons§8]", so a plain substring test would never match it.
        boolean fromBombo = ChatFormatting.stripFormatting(raw).contains("[BomboAddons]");
        if (fromBombo) {
            Entry owner = findEventForChatLine(raw, blocked);
            if (owner != null) {
                if (blocked && owner.status == Status.EVENT) {
                    owner.status = Status.BLOCKED_EVENT;
                }
                return;
            }
        }

        long now = System.currentTimeMillis();
        String dedupKey = (blocked ? "B:" : "A:") + raw;
        Long lastSeen = recentMessageTimes.get(dedupKey);
        if (lastSeen != null && (now - lastSeen) < 150L) {
            return;
        }
        recentMessageTimes.put(dedupKey, now);

        Entry entry = new Entry();
        entry.timeFormatted = TIME_FMT.format(new Date());
        entry.status = blocked ? Status.BLOCKED : Status.ALLOWED;
        entry.category = (category != null && !category.isEmpty()) ? category : detectCategory(raw);
        entry.component = comp;
        entry.rawText = raw;
        entry.isBombo = fromBombo;

        inspectCaller(entry, Thread.currentThread().getStackTrace());
        if (fromBombo) {
            // Our own feature message: attribute it to us regardless of what the stack says.
            entry.isMod = true;
            entry.isBombo = true;
            entry.callerModId = Constants.MOD_ID;
            entry.callerModName = Constants.MOD_NAME;
            entry.callerFrame = "knot//" + Constants.MOD_ID + ".feature";
            if (entry.featureName == null) entry.featureName = "Core Client";
        }
        inspectComponentEvents(entry, comp);

        addEntry(entry);
    }

    /**
     * Finds the feature event that a {@code [BomboAddons]} chat line is reporting, so the same
     * thing never occupies two rows. Matching is prefix based because the printed line is
     * usually a trimmed version of the recorded event ("Started auto sequence: X" vs
     * "Started auto sequence: X (loop ON, 500ms)").
     */
    private static Entry findEventForChatLine(String raw, boolean blocked) {
        String incoming = normalizeForMatch(raw);
        if (incoming.isEmpty()) return null;

        long now = System.currentTimeMillis();
        for (Entry e : entries) {
            if (e.status != Status.EVENT && e.status != Status.BLOCKED_EVENT) continue;
            if (e.rawText == null) continue;
            if (now - e.timestamp > EVENT_CHAT_ADOPT_MS) continue;
            String eventText = normalizeForMatch(e.rawText);
            if (eventText.isEmpty()) continue;
            if (eventText.startsWith(incoming) || incoming.startsWith(eventText)) {
                return e;
            }
        }
        return null;
    }

    /** Strips formatting and the mod prefix so two renderings of the same line compare equal. */
    private static String normalizeForMatch(String text) {
        if (text == null) return "";
        String clean = ChatFormatting.stripFormatting(text).trim();
        clean = clean.replaceFirst("^\\[?BomboAddons\\]?\\s*", "");
        return clean.trim();
    }

    /**
     * Records an event raised by one of the mod's own features (auto sequences, safety
     * guards, migrations...).
     *
     * <p>This deliberately bypasses the {@code [BomboAddons]} filter used by
     * {@link #recordIncoming}, because feature events are exactly the messages that filter
     * would otherwise throw away. Identical events inside {@link #EVENT_DEDUP_MS} are
     * collapsed so looping sequences cannot flood the buffer.
     */
    public static synchronized void recordEvent(String category, String message,
                                                String featureName, String originFeature, String triggerDesc) {
        if (message == null || message.trim().isEmpty()) return;

        String dedupKey = "E:" + category + ":" + message + ":" + triggerDesc;
        long now = System.currentTimeMillis();
        Long lastSeen = recentMessageTimes.get(dedupKey);
        if (lastSeen != null && (now - lastSeen) < EVENT_DEDUP_MS) {
            return;
        }
        recentMessageTimes.put(dedupKey, now);

        Entry entry = new Entry();
        entry.timeFormatted = TIME_FMT.format(new Date());
        entry.status = Status.EVENT;
        entry.category = (category != null && !category.isEmpty()) ? category : "EVENT";
        entry.rawText = message;
        entry.component = Component.literal(message);
        entry.isEvent = true;
        entry.isBombo = true;
        entry.isMod = true;
        entry.callerModId = Constants.MOD_ID;
        entry.callerModName = Constants.MOD_NAME;
        entry.featureName = featureName != null ? featureName : entry.category;
        entry.originFeature = originFeature;
        entry.triggerDesc = triggerDesc;
        entry.callerFrame = "knot//" + Constants.MOD_ID + "." + entry.featureName;
        addEntry(entry);
    }

    public static synchronized void recordOutgoing(String commandOrMessage, boolean isCommand) {
        if (commandOrMessage == null) return;

        long now = System.currentTimeMillis();
        String dedupKey = "OUT:" + commandOrMessage;
        Long lastSeen = recentMessageTimes.get(dedupKey);
        if (lastSeen != null && (now - lastSeen) < 150L) {
            return;
        }
        recentMessageTimes.put(dedupKey, now);

        Entry entry = new Entry();
        entry.timeFormatted = TIME_FMT.format(new Date());
        entry.status = Status.OUTGOING;
        entry.category = isCommand ? "COMMAND" : "OUTGOING";
        entry.rawText = commandOrMessage;
        entry.component = Component.literal(commandOrMessage);

        // Trigger provenance set by the feature that initiated the send beats stack-walking:
        // the stack below us is our own dispatch plumbing and cannot "create" the message.
        String trigger = OUTGOING_TRIGGER.get();
        boolean playerTyped = OUTGOING_PLAYER_INPUT.get();
        if (trigger != null && !trigger.trim().isEmpty()) {
            entry.isBombo = true;
            entry.isMod = true;
            entry.callerModId = Constants.MOD_ID;
            entry.callerModName = Constants.MOD_NAME;
            entry.featureName = "Custom Keybind";
            entry.originFeature = "Custom Keybinds";
            entry.triggerDesc = trigger;
            entry.callerFrame = "knot//" + Constants.MOD_ID + ".keybind-exec";
        } else {
            inspectCaller(entry, Thread.currentThread().getStackTrace());
            if (playerTyped) {
                // Typed in a chat screen: the player created it, no matter what the stack says.
                entry.isBombo = false;
                entry.isMod = false;
                entry.callerModId = null;
                entry.callerModName = "Player Input";
                if (entry.featureName == null || "Server Packet".equals(entry.featureName)) entry.featureName = "Chat Screen";
            }
        }
        OUTGOING_TRIGGER.remove();
        OUTGOING_PLAYER_INPUT.remove();

        addEntry(entry);
    }

    /** Hard ceiling for unlimited history so one very long session cannot exhaust the heap. */
    private static final int UNLIMITED_HARD_CAP = 50000;

    private static void addEntry(Entry entry) {
        BomboConfig.Settings cfg = BomboConfig.get();
        boolean unlimited = cfg != null && cfg.unlimitedChatHistory;
        int max = cfg != null ? cfg.chatHistoryMaxMessages : 500;
        if (max <= 0) max = 500;
        int limit = unlimited ? UNLIMITED_HARD_CAP : max;
        entries.add(entry);
        while (entries.size() > limit) {
            entries.remove(0);
            trimmedCount++;
        }
    }

    public static String detectCategory(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "EMPTY";
        String clean = ChatFormatting.stripFormatting(raw).trim();
        if (clean.isEmpty()) return "EMPTY";

        if (clean.contains("stashed away") || clean.contains("CLICK HERE to pick them up")) return "STASH_COMPACT";
        if (clean.contains("GEXP") || clean.contains("Guild >")) return "GUILD_EVENT_EXP";
        if (clean.startsWith("{") && clean.endsWith("}")) return "OTHER_MOD";
        if (clean.contains("»") || clean.contains("[Odin]") || clean.contains("Odin »") || clean.contains("SkyHanni »") || clean.contains("NEU »")) return "OTHER_MOD";
        if (clean.startsWith("/")) return "COMMAND";
        if (clean.startsWith("Party >") || clean.contains("joined the party") || clean.contains("left the party")) return "PARTY";
        if (clean.startsWith("Co-op >") || clean.startsWith("Officer >")) return "GUILD";
        if (clean.startsWith("From ") || clean.startsWith("To ")) return "PRIVATE";
        return "CHAT";
    }

    private static void inspectComponentEvents(Entry entry, Component comp) {
        if (comp == null) return;

        comp.visit((style, text) -> {
            if (style != null) {
                if (entry.clickAction == null && style.getClickEvent() != null) {
                    ClickEvent ce = style.getClickEvent();
                    if (ce instanceof ClickEvent.RunCommand runCmd) {
                        entry.clickAction = "RUN_COMMAND: " + runCmd.command();
                    } else if (ce instanceof ClickEvent.SuggestCommand suggestCmd) {
                        entry.clickAction = "SUGGEST_COMMAND: " + suggestCmd.command();
                    } else if (ce instanceof ClickEvent.OpenUrl openUrl) {
                        entry.clickAction = "OPEN_URL: " + openUrl.uri();
                    } else {
                        entry.clickAction = ce.toString();
                    }
                }
                if (entry.hoverText == null && style.getHoverEvent() != null) {
                    HoverEvent he = style.getHoverEvent();
                    if (he instanceof HoverEvent.ShowText showText) {
                        entry.hoverText = showText.value().getString();
                    } else {
                        entry.hoverText = he.toString();
                    }
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static void inspectCaller(Entry entry, StackTraceElement[] stack) {
        if (stack == null) return;

        // Was this message actually delivered by the server? If a packet handler is anywhere in
        // the path the message came over the network, and only a mod frame *above* it can claim
        // authorship. Checking this first is what fixes server chat being credited to us.
        boolean fromServer = false;
        String serverMethod = null;
        for (StackTraceElement elem : stack) {
            if (NETWORK_HANDLERS.contains(elem.getMethodName())) {
                fromServer = true;
                serverMethod = elem.getMethodName();
                break;
            }
        }

        StackTraceElement caller = null;
        for (StackTraceElement elem : stack) {
            String cls = elem.getClassName();
            String mth = elem.getMethodName();

            // 1. Skip java/jvm/reflection/mixin dispatchers, Fabric's event plumbing, and our
            //    own chat routing/history code - none of those "created" the message.
            if (cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.") || cls.startsWith("org.spongepowered.") || cls.startsWith("com.llamalad7.")) continue;
            if (cls.startsWith("net.fabricmc.")) continue;
            if (isInfrastructure(cls, mth)) continue;

            // 2. Skip any Mixin-injected handler method in any class (crucial: avoids knot//...ChatComponent.handler$...)
            if (mth.startsWith("handler$") || mth.startsWith("wrapOperation$") || mth.startsWith("invoke$")
                    || mth.startsWith("redirect$") || mth.startsWith("modifyArg$") || mth.startsWith("modifyVariable$") || mth.startsWith("modifyConstant$")) continue;

            // 3. Skip Minecraft chat & command forwarding boilerplate
            if (cls.equals("net.minecraft.client.gui.components.ChatComponent")) continue;
            if (cls.equals("net.minecraft.client.multiplayer.ClientPacketListener")) {
                if (mth.equals("sendChat") || mth.equals("sendCommand") || mth.equals("send") || mth.equals("sendUnsignedCommand")) continue;
            }
            if (cls.equals("net.minecraft.client.player.LocalPlayer")) {
                if (mth.equals("chat") || mth.equals("sendChat") || mth.equals("sendCommand")) continue;
            }
            if (cls.equals("net.minecraft.network.Connection")) {
                if (mth.equals("send") || mth.equals("sendPacket") || mth.equals("doSendPacket")) continue;
            }
            if (cls.equals("net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl")) {
                if (mth.equals("send") || mth.equals("sendWhen")) continue;
            }

            caller = elem;

            // If it is NOT a net.minecraft / mojang class, we found the true mod caller!
            if (!cls.startsWith("net.minecraft.") && !cls.startsWith("com.mojang.")) {
                break;
            }

            // If it is a known server packet handler or screen input, we also found the root
            if (mth.equals("handleSystemChat") || mth.equals("handleDisguisedChat") || mth.equals("handlePlayerChat") || mth.equals("handleCustomPayload")) {
                break;
            }
            if (cls.contains("ChatScreen") || cls.contains("CommandSuggestions") || cls.contains("KeyboardHandler")) {
                break;
            }
        }

        if (caller == null && stack.length > 0) {
            caller = stack[Math.min(2, stack.length - 1)];
        }

        if (caller != null) {
            entry.callerFrame = "knot//" + caller.getClassName() + "." + caller.getMethodName() + "(" + caller.getFileName() + ":" + caller.getLineNumber() + ")";

            String cls = caller.getClassName();
            boolean modFrame = !cls.startsWith("net.minecraft.") && !cls.startsWith("com.mojang.")
                    && !cls.startsWith("net.fabricmc.");

            if (fromServer && !modFrame) {
                // Plain server chat (or a Fabric event with no mod in the path): nothing in
                // this mod produced it, so do not claim it.
                entry.isMod = false;
                entry.isBombo = false;
                entry.callerModId = null;
                entry.callerModName = "Hypixel / Server";
                entry.featureName = "Server Chat";
                entry.callerFrame = "net.minecraft // " + (serverMethod != null ? serverMethod : "system chat");
                return;
            }

            if (cls.startsWith("me.bombo.bomboaddons")) {
                entry.isBombo = true;
                entry.isMod = true;
                entry.callerModId = Constants.MOD_ID;
                entry.callerModName = Constants.MOD_NAME;
                entry.featureName = resolveFeatureName(cls, caller.getMethodName());
            } else if (cls.startsWith("com.github.synnerz.devonian") || cls.contains("devonian")) {
                entry.isMod = true;
                entry.callerModId = "devonian";
                entry.callerModName = "Devonian";
                entry.featureName = caller.getMethodName();
            } else if (cls.startsWith("at.hannibal2.skyhanni") || cls.contains("skyhanni")) {
                entry.isMod = true;
                entry.callerModId = "skyhanni";
                entry.callerModName = "SkyHanni";
                entry.featureName = caller.getMethodName();
            } else if (cls.startsWith("com.odindev.odin") || cls.startsWith("me.odin") || cls.contains("odin")) {
                entry.isMod = true;
                entry.callerModId = "odin";
                entry.callerModName = "Odin";
                entry.featureName = caller.getMethodName();
            } else if (cls.contains("notenoughupdates") || cls.contains("moulberry")) {
                entry.isMod = true;
                entry.callerModId = "notenoughupdates";
                entry.callerModName = "NotEnoughUpdates";
                entry.featureName = caller.getMethodName();
            } else if (cls.contains("betterpv")) {
                entry.isMod = true;
                entry.callerModId = "betterpv";
                entry.callerModName = "BetterPV";
                entry.featureName = caller.getMethodName();
            } else if (cls.contains("skytils")) {
                entry.isMod = true;
                entry.callerModId = "skytils";
                entry.callerModName = "Skytils";
                entry.featureName = caller.getMethodName();
            } else if (cls.contains("bobby")) {
                entry.isMod = true;
                entry.callerModId = "bobby";
                entry.callerModName = "Bobby";
                entry.featureName = caller.getMethodName();
            } else if (cls.startsWith("net.minecraft.")) {
                entry.isMod = false;
                entry.isBombo = false;
                entry.callerModId = null;
                if (cls.contains("ChatScreen") || cls.contains("CommandSuggestions") || cls.contains("KeyboardHandler")) {
                    entry.callerModName = "Player Input";
                    entry.featureName = "Chat Screen";
                } else {
                    entry.callerModName = "Hypixel / Server";
                    entry.featureName = "Server Packet";
                }
            } else {
                // Dynamically check FabricLoader
                entry.isMod = true;
                entry.callerModId = resolveModIdFromClass(cls);
                entry.callerModName = resolveModName(entry.callerModId);
                entry.featureName = caller.getMethodName();
            }
        }
    }

    private static String resolveFeatureName(String cls, String method) {
        String simple = cls.substring(cls.lastIndexOf('.') + 1);
        if (simple.equals("BomboaddonsClient")) {
            if (method.contains("Locraw") || method.contains("locraw")) return "Locraw Tracker";
            if (method.contains("checkHoppity")) return "Hoppity Hunt";
            if (method.contains("trackCommand")) return "Command Tracker";
            return "Core Client";
        }
        if (simple.contains("AutoFishing")) return "Auto Fishing";
        if (simple.contains("Swap")) return "Slot Swapper";
        if (simple.contains("Party")) return "Party Manager";
        if (simple.contains("Reward")) return "Daily Rewards";
        if (simple.contains("Macro")) return "Macro Feature";
        if (simple.contains("Ring")) return "Ring Suppression";
        if (simple.contains("ChatModifier")) return "Chat Filter/Modifier";
        return simple;
    }

    private static String resolveModIdFromClass(String className) {
        try {
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId();
                String cleanId = id.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                if (className.toLowerCase(Locale.ROOT).contains(cleanId)) {
                    return id;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String resolveModName(String modId) {
        if (modId == null) return null;
        try {
            Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
            if (mod.isPresent()) {
                return mod.get().getMetadata().getName();
            }
        } catch (Throwable ignored) {}
        return modId;
    }
}
