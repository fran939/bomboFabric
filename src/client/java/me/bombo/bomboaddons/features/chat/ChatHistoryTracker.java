package me.bombo.bomboaddons.features.chat;

import me.bombo.bomboaddons.BomboConfig;
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
        OUTGOING("OUTGOING", 0xFF38BDF8);

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
    }

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

    public static synchronized void clear() {
        entries.clear();
        recentMessageTimes.clear();
    }

    public static synchronized void recordIncoming(Component comp, boolean blocked, String category) {
        if (comp == null) return;
        String raw = comp.getString();
        if (raw.contains("DailyRewardDebug") || raw.contains("[BomboAddons]")) return;

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

        inspectCaller(entry, Thread.currentThread().getStackTrace());
        inspectComponentEvents(entry, comp);

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

        inspectCaller(entry, Thread.currentThread().getStackTrace());

        addEntry(entry);
    }

    private static void addEntry(Entry entry) {
        int max = BomboConfig.get().chatHistoryMaxMessages;
        if (max <= 0) max = 500;
        entries.add(entry);
        while (entries.size() > max) {
            entries.remove(0);
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

        StackTraceElement caller = null;
        for (StackTraceElement elem : stack) {
            String cls = elem.getClassName();
            String mth = elem.getMethodName();

            // 1. Skip java/jvm/reflection/mixin dispatchers and our own tracker
            if (cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.") || cls.startsWith("org.spongepowered.") || cls.startsWith("com.llamalad7.")) continue;
            if (cls.contains("ChatHistoryTracker") || cls.contains("bomboaddons.mixin") || cls.contains("bomboaddons.features.chat")) continue;

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
            if (cls.startsWith("me.bombo.bomboaddons")) {
                entry.isBombo = true;
                entry.isMod = true;
                entry.callerModId = "bomboaddons";
                entry.callerModName = "BomboAddons";
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
