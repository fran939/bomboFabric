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
        CLICK_WORLD("Click in World (NPC)"),
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
        public boolean rightClick = true; // for CLICK_WORLD
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

        public static AutoAction waitDelay(int delayMs) {
            AutoAction a = new AutoAction();
            a.type = ActionType.WAIT;
            a.delayMs = delayMs;
            return a;
        }

        public String getSummary() {
            return switch (type) {
                case CLICK_SLOT -> {
                    String target = slotIndex >= 0 ? ("Slot #" + slotIndex) : ("\"" + itemMatcher + "\"");
                    yield "Click " + target + " (" + clickType + ", " + delayMs + "ms)";
                }
                case CLOSE_GUI -> "Close GUI (" + delayMs + "ms)";
                case RUN_COMMAND -> "Run \"" + command + "\" (" + delayMs + "ms)";
                case CLICK_WORLD -> (rightClick ? "Right-Click World" : "Left-Click World") + " (" + delayMs + "ms)";
                case WAIT -> "Wait " + delayMs + "ms";
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
