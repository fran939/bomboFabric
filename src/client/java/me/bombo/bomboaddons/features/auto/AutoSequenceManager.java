package me.bombo.bomboaddons.features.auto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sequence definitions and persistence.
 *
 * <p><b>Shared between flavors.</b> This class intentionally contains no automation
 * runtime: it only stores the user's sequences and exposes the running state so the config
 * GUI can render its ON/OFF indicator. The code that actually clicks slots, closes
 * containers, sends commands or presses keys lives in the cheat source set
 * ({@code src/cheat/java}, see {@code CheatAutoExecutor}) and is therefore absent from the
 * {@code bomboaddons} jar.
 *
 * <p>The JSON layout is unchanged from earlier versions, so existing
 * {@code bomboaddons_auto_sequences.json} files keep loading.
 */
public class AutoSequenceManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("bomboaddons/bomboaddons_auto_sequences.json").toFile();

    public enum ActionType {
        CLICK_SLOT("Click Slot / Item"),
        CLOSE_GUI("Close Current GUI"),
        RUN_COMMAND("Run Chat / Command"),
        CLICK_WORLD("Swing in World"),
        INTERACT_ENTITY("Right/Left Click NPC"),
        WAIT("Wait Delay");

        public final String displayName;

        ActionType(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class AutoAction {
        public ActionType type = ActionType.CLICK_SLOT;
        public int slotIndex = -1; // -1 means use itemMatcher
        public String itemMatcher = ""; // Match item display name or skyblock ID
        public String clickType = "LEFT"; // LEFT, RIGHT, SHIFT_LEFT, DROP
        public String command = ""; // for RUN_COMMAND
        public boolean rightClick = true; // for CLICK_WORLD / INTERACT_ENTITY
        /** Entity name matcher for INTERACT_ENTITY (e.g. "Plushie", "Jerry"). */
        public String entityMatcher = "";
        /** Search radius in blocks for INTERACT_ENTITY. */
        public double searchRadius = 5.0D;
        /** How many times this step runs before moving on. */
        public int repeatCount = 1;
        public int delayMs = 200; // delay after this action before next

        public AutoAction() {
        }

        public AutoAction(ActionType type, int slotIndex, String itemMatcher, String clickType, int delayMs) {
            this.type = type;
            this.slotIndex = slotIndex;
            this.itemMatcher = itemMatcher;
            this.clickType = clickType;
            this.delayMs = delayMs;
        }

        public static AutoAction clickSlot(int slotIndex, String itemMatcher, String clickType, int delayMs) {
            return new AutoAction(ActionType.CLICK_SLOT, slotIndex, itemMatcher, clickType, delayMs);
        }

        public static AutoAction closeGui(int delayMs) {
            AutoAction a = new AutoAction();
            a.type = ActionType.CLOSE_GUI;
            a.delayMs = delayMs;
            return a;
        }

        public static AutoAction runCommand(String cmd, int delayMs) {
            AutoAction a = new AutoAction();
            a.type = ActionType.RUN_COMMAND;
            a.command = cmd;
            a.delayMs = delayMs;
            return a;
        }

        public static AutoAction clickWorld(boolean rightClick, int delayMs) {
            AutoAction a = new AutoAction();
            a.type = ActionType.CLICK_WORLD;
            a.rightClick = rightClick;
            a.delayMs = delayMs;
            return a;
        }

        public static AutoAction interactEntity(String matcher, double radius, boolean rightClick, int delayMs) {
            AutoAction a = new AutoAction();
            a.type = ActionType.INTERACT_ENTITY;
            a.entityMatcher = matcher;
            a.searchRadius = radius;
            a.rightClick = rightClick;
            a.delayMs = delayMs;
            return a;
        }

        public static AutoAction waitDelay(int delayMs) {
            AutoAction a = new AutoAction();
            a.type = ActionType.WAIT;
            a.delayMs = delayMs;
            return a;
        }

        public String getSummary() {
            String repeat = repeatCount > 1 ? (" x" + repeatCount) : "";
            return switch (type) {
                case CLICK_SLOT -> {
                    String target = slotIndex >= 0 ? ("Slot #" + slotIndex) : ("\"" + itemMatcher + "\"");
                    yield "Click " + target + " (" + clickType + ", " + delayMs + "ms)" + repeat;
                }
                case CLOSE_GUI -> "Close GUI (" + delayMs + "ms)" + repeat;
                case RUN_COMMAND -> "Run \"" + command + "\" (" + delayMs + "ms)" + repeat;
                case CLICK_WORLD -> (rightClick ? "Right-Click World" : "Left-Click World") + " (" + delayMs + "ms)" + repeat;
                case INTERACT_ENTITY -> (rightClick ? "Right-Click" : "Left-Click")
                        + (entityMatcher == null || entityMatcher.isBlank() ? " NPC" : " \"" + entityMatcher + "\"")
                        + " /" + (int) searchRadius + "m (" + delayMs + "ms)" + repeat;
                case WAIT -> "Wait " + delayMs + "ms" + repeat;
            };
        }
    }

    public static class AutoSequence {
        public String id = UUID.randomUUID().toString().substring(0, 8);
        public String name = "New Sequence";
        public boolean enabled = true;
        public String triggerKey = ""; // Keybind to trigger
        public String triggerGui = ""; // Optional GUI title matcher
        public boolean loop = false;
        public int loopDelayMs = 400;
        /**
         * Randomisation applied to every wait in this sequence, as a percentage.
         * 30 means a 3000ms delay fires somewhere in 2100-3900ms, so runs are never
         * perfectly periodic.
         */
        public int jitterPercent = 30;
        public List<AutoAction> actions = new ArrayList<>();

        public AutoSequence() {
        }

        public AutoSequence(String name, String triggerKey) {
            this.name = name;
            this.triggerKey = triggerKey;
        }
    }

    private static final List<AutoSequence> sequences = new ArrayList<>();

    /**
     * Id of the sequence the flavor runtime is currently executing, or null.
     * Owned by the cheat executor; read here so the shared config GUI can show ON/OFF.
     */
    private static volatile String runningSequenceId = null;

    public static synchronized List<AutoSequence> getSequences() {
        return sequences;
    }

    public static synchronized void addSequence(AutoSequence seq) {
        if (seq != null) {
            sequences.add(seq);
            save();
        }
    }

    public static synchronized void removeSequence(int index) {
        if (index >= 0 && index < sequences.size()) {
            AutoSequence removed = sequences.remove(index);
            if (removed != null && removed.id != null && removed.id.equals(runningSequenceId)) {
                setRunningSequenceId(null);
            }
            save();
        }
    }

    public static synchronized AutoSequence findById(String id) {
        if (id == null) return null;
        for (AutoSequence seq : sequences) {
            if (id.equals(seq.id)) return seq;
        }
        return null;
    }

    public static synchronized AutoSequence findByName(String name) {
        if (name == null) return null;
        String needle = name.trim();
        for (AutoSequence seq : sequences) {
            if (seq.name != null && seq.name.equalsIgnoreCase(needle)) return seq;
        }
        // Fall back to a loose contains match so /b auto run plushie works.
        for (AutoSequence seq : sequences) {
            if (seq.name != null && seq.name.toLowerCase().contains(needle.toLowerCase())) return seq;
        }
        return null;
    }

    public static synchronized void load() {
        sequences.clear();
        try {
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE, StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<List<AutoSequence>>() {
                    }.getType();
                    List<AutoSequence> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) {
                        sequences.addAll(loaded);
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        // Add default demonstration sequence if empty
        if (sequences.isEmpty()) {
            AutoSequence sample = new AutoSequence("Plushie Transfer Macro", "TAB");
            sample.actions.add(AutoAction.clickSlot(-1, "marketable plushie", "LEFT", 250));
            sample.actions.add(AutoAction.closeGui(200));
            sample.actions.add(AutoAction.clickWorld(true, 300));
            sequences.add(sample);
            save();
        }
    }

    public static synchronized void save() {
        try {
            if (!FILE.getParentFile().exists()) {
                FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(sequences, writer);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Shared running state (written by the flavor runtime, read by the GUI)
    // ------------------------------------------------------------------

    public static boolean isRunning(AutoSequence seq) {
        return seq != null && seq.id != null && seq.id.equals(runningSequenceId);
    }

    public static boolean isAnyRunning() {
        return runningSequenceId != null;
    }

    public static AutoSequence getRunningSequence() {
        return findById(runningSequenceId);
    }

    public static String getRunningSequenceId() {
        return runningSequenceId;
    }

    public static void setRunningSequenceId(String id) {
        runningSequenceId = id;
    }

    /** True when this build ships a sequence runtime that can actually execute. */
    public static boolean hasRuntime() {
        return me.bombo.bomboaddons.flavor.Flavor.get().isCheat();
    }

    public static void sendMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] " + msg));
        }
    }

    // ------------------------------------------------------------------
    // Randomised timing
    // ------------------------------------------------------------------

    private static final java.util.Random JITTER_RANDOM = new java.util.Random();

    /**
     * Applies a sequence's jitter to a delay.
     *
     * <p>{@code jitter(base, 0)} is exact; {@code jitter(base, 30)} spreads the result evenly
     * across {@code base +/- 30%} (2100-3900ms for a 3000ms base).
     */
    public static long jitter(long baseMs, int jitterPercent) {
        long base = Math.max(0L, baseMs);
        int pct = Math.max(0, Math.min(90, jitterPercent));
        if (pct == 0 || base == 0L) return base;
        long spread = Math.max(1L, base * pct / 100L);
        long offset = (long) (JITTER_RANDOM.nextDouble() * (spread * 2L + 1L)) - spread;
        return Math.max(1L, base + offset);
    }

    /** Human readable jitter range, e.g. {@code 3000ms -> 2100-3900ms}. */
    public static String describeJitter(long baseMs, int jitterPercent) {
        int pct = Math.max(0, Math.min(90, jitterPercent));
        if (pct == 0) return baseMs + "ms (fixed)";
        long spread = Math.max(1L, baseMs * pct / 100L);
        return "~" + Math.max(1L, baseMs - spread) + "-" + (baseMs + spread) + "ms (\u00b1" + pct + "%)";
    }

    /**
     * Best effort human readable name for a raw GLFW key code, used for the
     * {@code Trigger:} line in the chat history tooltip.
     */
    public static String describeKeyCode(int code) {
        if (code < 0) return "None";
        try {
            String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(code, 0);
            if (name != null && !name.isBlank()) {
                return name.toUpperCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
        return "Key " + code;
    }
}
