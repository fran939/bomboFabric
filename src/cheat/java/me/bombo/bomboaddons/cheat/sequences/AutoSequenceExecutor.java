package me.bombo.bomboaddons.cheat.sequences;

import me.bombo.bomboaddons.features.auto.*;

import me.bombo.bomboaddons.*;

import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.CustomBindsProcessor;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction;
import me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence;
import me.bombo.bomboaddons.features.chat.ChatHistoryTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Runs user defined {@link AutoSequence}s.
 *
 * <p><b>Shared between flavors.</b> The user authors a sequence in their own config GUI and
 * runs it on their own client; both the legit and the cheat jar ship this executor so the
 * Sequences category actually does what its editor promises. Only flavor-specific extras
 * (the {@code /b hide} family) remain flavor-only.
 *
 * <p>Every start, stop, retry and safety halt is recorded through
 * {@link ChatHistoryTracker#recordEvent}, so {@code /b chathistory} can explain after the
 * fact exactly what the client did and what triggered it.
 */
public final class AutoSequenceExecutor {

    public static final String FEATURE = "Auto Sequence";
    public static final String ORIGIN = "Auto Sequences Manager";

    /** A step may fail this many times in a row before the sequence is halted. */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    // Only one sequence can run at a time, so the progress state is global.
    private static int actionIndex = 0;
    private static long nextActionTime = 0L;
    private static int consecutiveFailures = 0;
    /** Completed runs of the current step, for {@code repeatCount}. */
    private static int repeatsDone = 0;

    private AutoSequenceExecutor() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoSequenceExecutor::tick);
    }

    // ------------------------------------------------------------------
    // Input triggers
    // ------------------------------------------------------------------

    /**
     * @param kind  "Keybind" or "Mouse Button"
     * @param label human readable trigger for the history tooltip
     * @return true when a sequence consumed the input
     */
    public static boolean onInputTrigger(int code, String kind, String label) {
        if (code == -1) return false;

        for (AutoSequence seq : AutoSequenceManager.getSequences()) {
            if (seq == null || !seq.enabled) continue;
            if (seq.triggerKey == null || seq.triggerKey.trim().isEmpty()) continue;
            if (!AutoSequenceManager.hasRuntime()) continue;

            int mapped = ClickLogic.getKeyCode(seq.triggerKey);
            if (mapped == code || CustomBindsProcessor.matchesKey(seq.triggerKey, code)) {
                toggle(seq, triggerText(kind, label));
                return true;
            }
        }
        return false;
    }

    public static void toggleByIndex(int index, String kind, String label) {
        List<AutoSequence> list = AutoSequenceManager.getSequences();
        if (index < 0 || index >= list.size()) return;
        toggle(list.get(index), triggerText(kind, label));
    }

    public static void toggle(AutoSequence seq, String trigger) {
        if (seq == null) return;

        if (AutoSequenceManager.isRunning(seq)) {
            halt("stopped by user", trigger);
            return;
        }
        if (seq.actions == null || seq.actions.isEmpty()) {
            AutoSequenceManager.sendMessage("§cSequence §e" + seq.name + " §chas no steps.");
            record("Could not start auto sequence: " + seq.name + " (no steps)", trigger);
            return;
        }
        boolean anyEnabled = false;
        for (AutoAction step : seq.actions) {
            if (step != null && step.enabled) {
                anyEnabled = true;
                break;
            }
        }
        if (!anyEnabled) {
            AutoSequenceManager.sendMessage("§cSequence §e" + seq.name + " §chas every step switched OFF.");
            record("Could not start auto sequence: " + seq.name + " (all steps disabled)", trigger);
            return;
        }
        if (AutoSequenceManager.isAnyRunning()) {
            AutoSequence current = AutoSequenceManager.getRunningSequence();
            halt("replaced by another sequence", trigger);
            if (current != null) {
                AutoSequenceManager.sendMessage("§7Stopped §e" + current.name + " §7to start §e" + seq.name + "§7.");
            }
        }

        actionIndex = 0;
        consecutiveFailures = 0;
        repeatsDone = 0;
        nextActionTime = 0L;
        AutoSequenceManager.setRunningSequenceId(seq.id);

        String details = "Started auto sequence: " + seq.name
                + (seq.loop
                ? " (loop ON, cooldown " + AutoSequenceManager.describeJitter(Math.max(50, seq.loopDelayMs), seq.jitterPercent) + ")"
                : " (" + seq.actions.size() + " step" + (seq.actions.size() == 1 ? "" : "s") + ")");
        record(details, trigger);
        AutoSequenceManager.sendMessage("§aStarted auto sequence: §e" + seq.name);
    }

    /** Stops whatever is running. Used by the safety guards and {@code /b auto stop}. */
    public static void halt(String reason, String trigger) {
        AutoSequence seq = AutoSequenceManager.getRunningSequence();
        AutoSequenceManager.setRunningSequenceId(null);
        actionIndex = 0;
        consecutiveFailures = 0;
        repeatsDone = 0;
        nextActionTime = 0L;

        String name = seq != null ? seq.name : "sequence";
        if ("stopped by user".equals(reason)) {
            record("Stopped auto sequence: " + name, trigger);
            AutoSequenceManager.sendMessage("§cStopped auto sequence: §e" + name);
        } else {
            record("Halted auto sequence: " + name + " \u2014 " + reason, trigger);
            AutoSequenceManager.sendMessage("§cHalted auto sequence: §e" + name + " §7(" + reason + ")");
        }
    }

    public static void stopAll(String trigger) {
        if (!AutoSequenceManager.isAnyRunning()) return;
        halt("stopped by user", trigger);
    }

    // ------------------------------------------------------------------
    // Tick loop
    // ------------------------------------------------------------------

    private static void tick(Minecraft mc) {
        if (!AutoSequenceManager.isAnyRunning()) return;

        // World / player guards: these are the "something unexpected happened" cases that
        // used to leave a sequence silently spinning forever.
        if (mc.player == null || mc.level == null) {
            halt("player left the world", "Safety guard");
            return;
        }

        AutoSequence seq = AutoSequenceManager.getRunningSequence();
        if (seq == null) {
            halt("sequence no longer exists", "Safety guard");
            return;
        }

        List<AutoAction> actions = seq.actions;
        if (actions == null || actions.isEmpty()) {
            halt("sequence has no steps", "Safety guard");
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionTime) return;

        if (actionIndex >= actions.size()) {
            if (seq.loop) {
                actionIndex = 0;
                repeatsDone = 0;
                // Loop cooldown is randomised too, so a repeating macro is not a metronome.
                nextActionTime = now + Math.max(50, AutoSequenceManager.jitter(seq.loopDelayMs, seq.jitterPercent));
                return;
            }
            AutoSequenceManager.setRunningSequenceId(null);
            record("Completed auto sequence: " + seq.name + " (" + actions.size() + " steps)",
                    "Loop OFF");
            AutoSequenceManager.sendMessage("§aCompleted auto sequence: §e" + seq.name);
            return;
        }

        AutoAction action = actions.get(actionIndex);
        // A step the user switched off is skipped entirely - it must not consume its delay, or a
        // toggled-off step would silently slow the whole sequence down.
        if (action != null && !action.enabled) {
            actionIndex++;
            repeatsDone = 0;
            consecutiveFailures = 0;
            return;
        }
        boolean ok = executeAction(mc, action);

        if (!ok) {
            consecutiveFailures++;
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                halt("step " + (actionIndex + 1) + " failed " + MAX_CONSECUTIVE_FAILURES
                        + "x (" + describeFailure(mc, action) + ")", "Safety guard");
                return;
            }
            // Retry the same step rather than silently skipping it.
            nextActionTime = now + Math.max(50, AutoSequenceManager.jitter(action.delayMs, seq.jitterPercent));
            return;
        }

        consecutiveFailures = 0;
        long wait = Math.max(20, AutoSequenceManager.jitter(action.delayMs, seq.jitterPercent));

        // repeatCount: run the same step again before advancing.
        int repeat = Math.max(1, action.repeatCount);
        if (repeatsDone + 1 < repeat) {
            repeatsDone++;
            nextActionTime = System.currentTimeMillis() + wait;
            return;
        }

        repeatsDone = 0;
        actionIndex++;
        nextActionTime = System.currentTimeMillis() + wait;
    }

    private static String describeFailure(Minecraft mc, AutoAction action) {
        if (action.type == AutoSequenceManager.ActionType.CLICK_SLOT
                && !(mc.gui.screen() instanceof AbstractContainerScreen<?>)) {
            return "container closed";
        }
        if (action.type == AutoSequenceManager.ActionType.INTERACT_ENTITY) {
            return action.entityMatcher == null || action.entityMatcher.isBlank()
                    ? "no NPC in range"
                    : "no NPC matching \"" + action.entityMatcher + "\" in range";
        }
        return action.type == AutoSequenceManager.ActionType.CLICK_SLOT ? "slot not found" : "step failed";
    }

    private static boolean executeAction(Minecraft mc, AutoAction action) {
        if (action == null || action.type == null) return false;
        try {
            // Check GUI condition across all actions:
            String guiCond = (action.guiCondition != null && !action.guiCondition.isBlank())
                    ? action.guiCondition
                    : action.guiMatcher;

            if (guiCond != null && !guiCond.isBlank()) {
                String trimmedCond = guiCond.trim();
                if (trimmedCond.equalsIgnoreCase("NONE") || trimmedCond.equalsIgnoreCase("NO_GUI")) {
                    if (mc.gui.screen() != null) {
                        return false; // must NOT be in a GUI
                    }
                } else if (action.type != AutoSequenceManager.ActionType.CLICK_SLOT) {
                    // Non-slot-click actions can also require a specific GUI screen
                    if (mc.gui.screen() == null) {
                        return false;
                    }
                    String title = ChatFormatting.stripFormatting(mc.gui.screen().getTitle().getString()).trim().toLowerCase(Locale.ROOT);
                    if (!title.contains(trimmedCond.toLowerCase(Locale.ROOT))) {
                        return false;
                    }
                }
            }

            switch (action.type) {
                case CLICK_SLOT -> {
                    if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> containerScreen)) {
                        return false;
                    }
                    // Optional GUI title guard: a step that names a GUI only clicks inside it
                    if (guiCond != null && !guiCond.isBlank()) {
                        String trimmedCond = guiCond.trim();
                        if (trimmedCond.equalsIgnoreCase("NONE") || trimmedCond.equalsIgnoreCase("NO_GUI")) {
                            return false;
                        }
                        String title = ChatFormatting.stripFormatting(
                                containerScreen.getTitle().getString()).trim().toLowerCase(Locale.ROOT);
                        String want = trimmedCond.toLowerCase(Locale.ROOT);
                        if (!title.contains(want)) {
                            return false;
                        }
                    }
                    int targetSlot = -1;
                    if (action.slotIndex >= 0 && action.slotIndex < containerScreen.getMenu().slots.size()) {
                        targetSlot = action.slotIndex;
                    } else {
                        targetSlot = findSlotMatching(mc, containerScreen, action);
                    }
                    if (targetSlot < 0) return false;

                    int containerId = containerScreen.getMenu().containerId;
                    ContainerInput input = ContainerInput.PICKUP;
                    int button = 0;

                    String cType = action.clickType != null ? action.clickType.toUpperCase(Locale.ROOT) : "LEFT";
                    if (cType.equals("RIGHT")) {
                        button = 1;
                    } else if (cType.equals("SHIFT_LEFT")) {
                        input = ContainerInput.QUICK_MOVE;
                    } else if (cType.equals("DROP")) {
                        input = ContainerInput.THROW;
                    }
                    if (mc.gameMode == null) return false;
                    mc.gameMode.handleContainerInput(containerId, targetSlot, button, input, mc.player);
                    return true;
                }

                case CLOSE_GUI -> {
                    if (mc.player != null) {
                        mc.player.closeContainer();
                    }
                    if (mc.gui.screen() != null) {
                        mc.setScreenAndShow(null);
                    }
                    return true;
                }

                case RUN_COMMAND -> {
                    if (mc.player == null || mc.player.connection == null) return false;
                    if (action.command == null || action.command.trim().isEmpty()) return false;
                    String cmd = action.command.trim();
                    if (cmd.startsWith("/")) {
                        mc.player.connection.sendCommand(cmd.substring(1));
                    } else {
                        mc.player.connection.sendChat(cmd);
                    }
                    return true;
                }

                case CLICK_WORLD -> {
                    if (mc.options == null) return false;
                    if (action.rightClick) {
                        mc.options.keyUse.setDown(true);
                        releaseLater(mc, false);
                    } else {
                        mc.options.keyAttack.setDown(true);
                        releaseLater(mc, true);
                    }
                    return true;
                }

                case INTERACT_ENTITY -> {
                    if (mc.player == null || mc.gameMode == null) return false;
                    Entity target = findEntity(mc, action);
                    if (target == null) return false;

                    Vec3 hit = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
                    if (action.rightClick) {
                        mc.gameMode.interact(mc.player, target, new EntityHitResult(target, hit), InteractionHand.MAIN_HAND);
                    } else {
                        mc.gameMode.attack(mc.player, target);
                    }
                    return true;
                }

                case WAIT -> {
                    // Delay only - handled by delayMs.
                    return true;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return false;
    }

    /** Releases a synthetic key press shortly after, on the client thread. */
    private static void releaseLater(Minecraft mc, boolean attack) {
        new Thread(() -> {
            try {
                Thread.sleep(50L);
            } catch (Throwable ignored) {
            }
            mc.execute(() -> {
                if (mc.options == null) return;
                if (attack) {
                    mc.options.keyAttack.setDown(false);
                } else {
                    mc.options.keyUse.setDown(false);
                }
            });
        }).start();
    }

    /**
     * Nearest living entity whose name matches the step's matcher, inside its radius.
     * An empty matcher simply means "the closest entity".
     */
    private static Entity findEntity(Minecraft mc, AutoAction action) {
        if (mc.level == null || mc.player == null) return null;

        double radius = action.searchRadius > 0 ? action.searchRadius : 5.0D;
        String query = action.entityMatcher == null ? "" : action.entityMatcher.trim().toLowerCase(Locale.ROOT);
        net.minecraft.world.phys.AABB area = mc.player.getBoundingBox().inflate(radius, Math.max(2.0D, radius / 2.0D), radius);

        Entity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : mc.level.getEntities(mc.player, area)) {
            if (entity == mc.player) continue;
            if (!entity.isAlive()) continue;

            if (!query.isEmpty()) {
                String name = ChatFormatting.stripFormatting(entity.getName().getString()).toLowerCase(Locale.ROOT);
                String type = entity.getType().toString().toLowerCase(Locale.ROOT);
                if (!name.contains(query) && !type.contains(query)) continue;
            }

            double dist = mc.player.distanceToSqr(entity);
            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    private static int findSlotMatching(Minecraft mc, AbstractContainerScreen<?> screen, AutoAction action) {
        String matcher = action.itemMatcher != null ? action.itemMatcher.trim() : "";
        String loweredAll = matcher.toLowerCase(Locale.ROOT);

        String locationCond = (action.locationCondition != null && !action.locationCondition.isBlank())
                ? action.locationCondition.trim().toUpperCase(Locale.ROOT) : "ANY";
        String slotCond = (action.slotCondition != null) ? action.slotCondition.trim() : "";

        String targetMatcher = loweredAll;
        boolean isLore = false;

        // Check location prefixes in matcher string
        if (targetMatcher.startsWith("inv:") || targetMatcher.startsWith("inventory:") || targetMatcher.startsWith("i:")) {
            locationCond = "INVENTORY";
            targetMatcher = targetMatcher.substring(targetMatcher.indexOf(':') + 1).trim();
        } else if (targetMatcher.startsWith("container:") || targetMatcher.startsWith("c:") || targetMatcher.startsWith("chest:")) {
            locationCond = "CONTAINER";
            targetMatcher = targetMatcher.substring(targetMatcher.indexOf(':') + 1).trim();
        }

        // Check slot condition prefix in matcher string: e.g. "slot:<9", "slot:<=8", "slot:>9"
        if (targetMatcher.startsWith("slot:")) {
            int nextSpace = targetMatcher.indexOf(' ');
            if (nextSpace != -1) {
                slotCond = targetMatcher.substring(5, nextSpace).trim();
                targetMatcher = targetMatcher.substring(nextSpace + 1).trim();
            } else {
                slotCond = targetMatcher.substring(5).trim();
                targetMatcher = "";
            }
        }

        // Check lore prefix: "l:" or "lore:"
        if (targetMatcher.startsWith("l:") || targetMatcher.startsWith("lore:")) {
            isLore = true;
            targetMatcher = targetMatcher.substring(targetMatcher.indexOf(':') + 1).trim();
        }

        if (targetMatcher.matches("^slot\\s*#?\\s*\\d+$")) {
            String digits = targetMatcher.replaceAll("[^\\d]", "");
            try {
                int idx = Integer.parseInt(digits);
                if (idx >= 0 && idx < screen.getMenu().slots.size()) return idx;
            } catch (Throwable ignored) {
            }
            return -1;
        }

        List<Slot> slots = screen.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);

            // Check slot condition
            if (!slotCond.isEmpty() && !matchesSlotCondition(i, slotCond)) {
                continue;
            }

            // Check location condition
            boolean isInventory = (slot.container instanceof net.minecraft.world.entity.player.Inventory)
                    || (mc.player != null && slot.container == mc.player.getInventory());
            if ("INVENTORY".equals(locationCond) && !isInventory) {
                continue;
            }
            if ("CONTAINER".equals(locationCond) && isInventory) {
                continue;
            }

            ItemStack stack = slot.getItem();
            if (targetMatcher.isEmpty() && !stack.isEmpty()) {
                return i;
            }

            if (!stack.isEmpty()) {
                if (isLore) {
                    if (matchesLore(mc, stack, targetMatcher)) {
                        return i;
                    }
                } else {
                    String hover = ChatFormatting.stripFormatting(stack.getHoverName().getString())
                            .toLowerCase(Locale.ROOT).trim();
                    String sbId = SkyblockUtils.getSkyblockId(stack).toLowerCase(Locale.ROOT).trim();
                    if (hover.contains(targetMatcher) || sbId.contains(targetMatcher) || sbId.replace("_", " ").contains(targetMatcher)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean matchesSlotCondition(int slotIndex, String cond) {
        if (cond == null || cond.isBlank()) return true;
        try {
            String c = cond.trim();
            if (c.startsWith("<=")) {
                return slotIndex <= Integer.parseInt(c.substring(2).trim());
            } else if (c.startsWith("<")) {
                return slotIndex < Integer.parseInt(c.substring(1).trim());
            } else if (c.startsWith(">=")) {
                return slotIndex >= Integer.parseInt(c.substring(2).trim());
            } else if (c.startsWith(">")) {
                return slotIndex > Integer.parseInt(c.substring(1).trim());
            } else if (c.startsWith("==") || c.startsWith("=")) {
                String val = c.startsWith("==") ? c.substring(2).trim() : c.substring(1).trim();
                return slotIndex == Integer.parseInt(val);
            } else if (c.contains("-")) {
                String[] parts = c.split("-");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                return slotIndex >= min && slotIndex <= max;
            } else if (c.matches("^\\d+$")) {
                return slotIndex == Integer.parseInt(c);
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static boolean matchesLore(Minecraft mc, ItemStack stack, String targetLore) {
        if (targetLore == null || targetLore.isEmpty()) return true;
        String query = targetLore.toLowerCase(Locale.ROOT).trim();

        // 1. Check DataComponents.LORE
        net.minecraft.world.item.component.ItemLore itemLore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (itemLore != null) {
            for (net.minecraft.network.chat.Component line : itemLore.lines()) {
                String text = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT);
                if (text.contains(query)) {
                    return true;
                }
            }
        }

        // 2. Check full tooltip lines
        if (mc.level != null && mc.player != null) {
            try {
                List<net.minecraft.network.chat.Component> tooltip = stack.getTooltipLines(
                        net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                        mc.player,
                        net.minecraft.world.item.TooltipFlag.NORMAL);
                for (net.minecraft.network.chat.Component line : tooltip) {
                    String text = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT);
                    if (text.contains(query)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static int findSlotMatching(AbstractContainerScreen<?> screen, String matcher) {
        return findSlotMatching(Minecraft.getInstance(), screen, new AutoAction(AutoSequenceManager.ActionType.CLICK_SLOT, -1, matcher, "LEFT", 0));
    }

    private static String triggerText(String kind, String label) {
        if (kind == null || kind.isEmpty()) {
            return label != null && !label.isEmpty() ? label : "Unknown trigger";
        }
        return label != null && !label.isEmpty() ? kind + " " + label : kind;
    }

    private static void record(String message, String trigger) {
        ChatHistoryTracker.recordEvent("AUTO", message, FEATURE, ORIGIN, trigger);
    }
}
