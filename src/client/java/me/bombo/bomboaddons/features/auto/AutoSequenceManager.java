package me.bombo.bomboaddons.features.auto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.CustomBindsProcessor;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AutoSequenceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/bomboaddons_auto_sequences.json").toFile();

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

        public AutoAction() {}

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

        public AutoSequence() {}

        public AutoSequence(String name, String triggerKey) {
            this.name = name;
            this.triggerKey = triggerKey;
        }
    }

    private static final List<AutoSequence> sequences = new ArrayList<>();

    // Runtime execution state
    private static AutoSequence activeRunningSequence = null;
    private static int currentActionIndex = 0;
    private static long nextActionTime = 0;

    public static void init() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick(client));
    }

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
            sequences.remove(index);
            save();
        }
    }

    public static synchronized void load() {
        sequences.clear();
        try {
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE, StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<List<AutoSequence>>() {}.getType();
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

    public static boolean onKeyPressed(int keyCode) {
        if (keyCode == -1) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return false;

        for (AutoSequence seq : sequences) {
            if (seq.enabled && seq.triggerKey != null && !seq.triggerKey.trim().isEmpty()) {
                int code = ClickLogic.getKeyCode(seq.triggerKey);
                if (code == keyCode || CustomBindsProcessor.matchesKey(seq.triggerKey, keyCode)) {
                    toggleSequence(seq);
                    return true;
                }
            }
        }
        return false;
    }

    public static void toggleSequence(AutoSequence seq) {
        if (activeRunningSequence == seq) {
            stopRunning();
            sendMessage("§cStopped auto sequence: §e" + seq.name);
        } else {
            startSequence(seq);
            sendMessage("§aStarted auto sequence: §e" + seq.name);
        }
    }

    public static void startSequence(AutoSequence seq) {
        if (seq == null || seq.actions.isEmpty()) return;
        activeRunningSequence = seq;
        currentActionIndex = 0;
        nextActionTime = System.currentTimeMillis();
    }

    public static void stopRunning() {
        activeRunningSequence = null;
        currentActionIndex = 0;
        nextActionTime = 0;
    }

    public static boolean isRunning(AutoSequence seq) {
        return activeRunningSequence == seq;
    }

    public static AutoSequence getActiveRunningSequence() {
        return activeRunningSequence;
    }

    private static void onClientTick(Minecraft mc) {
        if (activeRunningSequence == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now < nextActionTime) return;

        List<AutoAction> actions = activeRunningSequence.actions;
        if (actions == null || actions.isEmpty()) {
            stopRunning();
            return;
        }

        if (currentActionIndex >= actions.size()) {
            if (activeRunningSequence.loop) {
                currentActionIndex = 0;
                nextActionTime = now + Math.max(50, activeRunningSequence.loopDelayMs);
                return;
            } else {
                sendMessage("§aCompleted auto sequence: §e" + activeRunningSequence.name);
                stopRunning();
                return;
            }
        }

        AutoAction action = actions.get(currentActionIndex);
        executeAction(mc, action);
        currentActionIndex++;
        nextActionTime = System.currentTimeMillis() + Math.max(20, action.delayMs);
    }

    private static void executeAction(Minecraft mc, AutoAction action) {
        try {
            switch (action.type) {
                case CLICK_SLOT -> {
                    if (mc.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) {
                        int targetSlot = -1;
                        if (action.slotIndex >= 0 && action.slotIndex < containerScreen.getMenu().slots.size()) {
                            targetSlot = action.slotIndex;
                        } else if (action.itemMatcher != null && !action.itemMatcher.trim().isEmpty()) {
                            targetSlot = findSlotMatching(containerScreen, action.itemMatcher.trim());
                        }

                        if (targetSlot >= 0) {
                            int containerId = containerScreen.getMenu().containerId;
                            ContainerInput input = ContainerInput.PICKUP;
                            int button = 0;

                            String cType = action.clickType != null ? action.clickType.toUpperCase(Locale.ROOT) : "LEFT";
                            if (cType.equals("RIGHT")) {
                                button = 1;
                            } else if (cType.equals("SHIFT_LEFT")) {
                                input = ContainerInput.QUICK_MOVE;
                                button = 0;
                            } else if (cType.equals("DROP")) {
                                input = ContainerInput.THROW;
                                button = 0;
                            }

                            mc.gameMode.handleContainerInput(containerId, targetSlot, button, input, mc.player);
                        }
                    }
                }

                case CLOSE_GUI -> {
                    if (mc.player != null) {
                        mc.player.closeContainer();
                    }
                    if (mc.gui.screen() != null) {
                        mc.setScreenAndShow(null);
                    }
                }

                case RUN_COMMAND -> {
                    if (mc.player != null && action.command != null && !action.command.trim().isEmpty()) {
                        String cmd = action.command.trim();
                        if (cmd.startsWith("/")) {
                            mc.player.connection.sendCommand(cmd.substring(1));
                        } else {
                            mc.player.connection.sendChat(cmd);
                        }
                    }
                }

                case CLICK_WORLD -> {
                    if (mc.options != null) {
                        if (action.rightClick) {
                            mc.options.keyUse.setDown(true);
                            new Thread(() -> {
                                try { Thread.sleep(50); } catch (Throwable ignored) {}
                                mc.execute(() -> {
                                    if (mc.options != null) mc.options.keyUse.setDown(false);
                                });
                            }).start();
                        } else {
                            mc.options.keyAttack.setDown(true);
                            new Thread(() -> {
                                try { Thread.sleep(50); } catch (Throwable ignored) {}
                                mc.execute(() -> {
                                    if (mc.options != null) mc.options.keyAttack.setDown(false);
                                });
                            }).start();
                        }
                    }
                }

                case WAIT -> {
                    // Handled via delayMs
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static int findSlotMatching(AbstractContainerScreen<?> screen, String matcher) {
        String query = matcher.toLowerCase(Locale.ROOT).trim();
        List<Slot> slots = screen.getMenu().slots;

        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            if (!stack.isEmpty()) {
                String hover = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()).toLowerCase(Locale.ROOT).trim();
                String sbId = SkyblockUtils.getSkyblockId(stack).toLowerCase(Locale.ROOT).trim();
                if (hover.contains(query) || sbId.contains(query) || sbId.replace("_", " ").contains(query)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static void sendMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] " + msg));
        }
    }
}
