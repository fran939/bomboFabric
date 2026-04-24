package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class AutoExperiments {
    private static String lastDetectedTitle = "";
    private static final HashMap<Integer, Integer> ultrasequencerOrder = new HashMap<>();
    private static final List<Integer> chronomatronOrder = new ArrayList<>(28);
    private static long lastClickTime = 0L;
    private static int lastGlowingSlot = -1;
    private static boolean noteInProgress = false;
    private static boolean hasAdded = false;
    private static boolean wasInInputPhase = false;
    private static boolean wasInShowingPhase = false;
    private static int lastAdded = -1;
    private static String debugStr = "None";
    private static int clicks = 0;
    private static final Random random = new Random();

    public static String getDebugStr() {
        return debugStr;
    }

    public static String getDetectedTitle() {
        return lastDetectedTitle;
    }

    public static void reset() {
        ultrasequencerOrder.clear();
        chronomatronOrder.clear();
        hasAdded = false;
        wasInShowingPhase = false;
        wasInInputPhase = false;
        lastAdded = -1;
        lastGlowingSlot = -1;
        noteInProgress = false;
        clicks = 0;
    }

    private static long tickCount = 0;
    private static long lastDebugTime = 0;
    private static String lastInventoryHash = "";

    public static void onTick() {
        tickCount++;
        Minecraft mc = Minecraft.getInstance();
        BomboConfig.Settings config = BomboConfig.get();

        if (mc.player != null && config.autoExperiments) {
            String title = mc.screen != null ? mc.screen.getTitle().getString().trim() : "";
            
            if (mc.screen == null) {
                if (!lastDetectedTitle.isEmpty()) {
                    lastDetectedTitle = "";
                    reset();
                }
                debugStr = "Waiting for GUI... " + (tickCount % 20 < 10 ? "|" : "-");
            } else {
                String cleanTitle = title.replaceAll("(?i)§[0-9a-fk-or]", "");
                if (!cleanTitle.equals(lastDetectedTitle)) {
                    
                    if (lastDetectedTitle.contains("Chronomatron") != cleanTitle.contains("Chronomatron") ||
                        lastDetectedTitle.contains("Ultrasequencer") != cleanTitle.contains("Ultrasequencer") ||
                        cleanTitle.contains("Stakes") || lastDetectedTitle.contains("Stakes") ||
                        cleanTitle.contains("Experiment Over")) {
                        reset();
                    }
                    
                    lastDetectedTitle = cleanTitle;
                }
                
                Screen screen = mc.screen;
                if (screen instanceof AbstractContainerScreen && !cleanTitle.contains("Stakes") && !cleanTitle.contains("Table")) {
                    AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) screen;
                    

                    int timerSlot = findTimerSlot(acs.getMenu().slots);
                    if (System.currentTimeMillis() - lastDebugTime > 1000) {
                        String timerInfo = (timerSlot != -1) ? acs.getMenu().slots.get(timerSlot).getItem().getItem().toString() : "NONE";
                        debugStr = String.format("Timer: %s | Mode: %s", timerInfo, cleanTitle.contains("Chron") ? "Chrono" : "Ultra");
                        lastDebugTime = System.currentTimeMillis();
                    }

                    if (cleanTitle.contains("Chronomatron")) {
                        solveChronomatron(mc, acs);
                    } else if (cleanTitle.contains("Ultrasequencer")) {
                        solveUltraSequencer(mc, acs);
                    }
                } else if (cleanTitle.contains("Stakes") || cleanTitle.contains("Table")) {
                    debugStr = "In Menu (Ignored)";
                }
            }
        }
    }

    private static String getInventoryHash(List<Slot> slots) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 54 && i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            sb.append(stack.getItem().toString()).append(stack.getCount()).append(hasGlint(stack));
        }
        return sb.toString();
    }


    private static boolean hasGlint(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.hasFoil() || 
               stack.getComponents().has(net.minecraft.core.component.DataComponents.ENCHANTMENTS) ||
               stack.getComponents().has(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
    }

    private static void solveChronomatron(Minecraft mc, AbstractContainerScreen<?> screen) {
        BomboConfig.Settings config = BomboConfig.get();
        List<Slot> slots = screen.getMenu().slots;
        
        int timerSlot = findTimerSlot(slots);
        if (timerSlot != -1) {
            ItemStack timerStack = slots.get(timerSlot).getItem();
            // Phase Detection
            boolean isInputPhase = isClock(timerStack);
            boolean isShowingPhase = isGlowstone(timerStack) || isGlowItem(timerStack);

            // Recording Phase: Track flashes (e.g. terracotta or glowing items)
            boolean terracottaFound = false;
            int currentFlashedSlot = -1;
            for (int i = 0; i < slots.size() && i < 54; i++) {
                Slot slot = slots.get(i);
                if (slot.container == mc.player.getInventory()) continue;
                if (i == timerSlot) continue;
                ItemStack stack = slot.getItem();
                String currentItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                // ONLY detect the flash materials (terracotta, wool, concrete, glowstone)
                // stained_glass is the default button state, so we ignore it here.
                boolean isFlashMaterial = currentItem.contains("terracotta") || currentItem.contains("wool") || currentItem.contains("concrete") || currentItem.contains("glowstone");
                
                if (isFlashMaterial && isNoteItem(stack)) {
                    terracottaFound = true;
                    currentFlashedSlot = i;
                    break;
                }
            }

            if (terracottaFound) {
                if (!noteInProgress) {
                    noteInProgress = true;
                    lastGlowingSlot = currentFlashedSlot;
                    if (config.debugMode) mc.player.displayClientMessage(Component.literal("§7[Bombo] Chrono: Flash detected at slot " + currentFlashedSlot), false);
                }
            } else {
                noteInProgress = false;
            }

            // Phase Reset / Input Start
            if (isInputPhase && !wasInInputPhase) {
                if (lastGlowingSlot != -1) {
                    chronomatronOrder.add(lastGlowingSlot);
                    if (config.debugMode) mc.player.displayClientMessage(Component.literal("§a[Bombo] Chrono: Note #" + chronomatronOrder.size() + " captured: " + lastGlowingSlot), false);
                    lastGlowingSlot = -1;
                } else if (config.debugMode) {
                    mc.player.displayClientMessage(Component.literal("§c[Bombo] Chrono: No note captured this round!"), false);
                }
                clicks = 0;
                hasAdded = true; 
                noteInProgress = false;
            }
            wasInInputPhase = isInputPhase;

            debugStr = String.format("Chrono | Phase: %s | Clicks: %d/%d | Glow: %d", 
                isShowingPhase ? "SHOWING" : (isInputPhase ? "INPUT" : "WAIT"), 
                clicks, chronomatronOrder.size(), lastGlowingSlot);

            // Clicking Phase
            if (isInputPhase && hasAdded && chronomatronOrder.size() > clicks) {
                long delay = (long) (config.experimentClickDelay + (random.nextInt(41) - 20));
                if (delay < 0L) delay = 0L;

                if (System.currentTimeMillis() - lastClickTime > delay) {
                    int slotIndex = chronomatronOrder.get(clicks);
                    clickSlot(mc, screen, slotIndex);
                    // Using false for chat instead of true for action bar for better log visibility
                    if (config.debugMode) mc.player.displayClientMessage(Component.literal("§b[Bombo] Chrono Click #" + (clicks + 1) + " (Slot " + slotIndex + ")"), false);
                    lastClickTime = System.currentTimeMillis();
                    clicks++;
                    
                    if (clicks >= chronomatronOrder.size()) {
                        hasAdded = false;
                        if (config.debugMode) mc.player.displayClientMessage(Component.literal("§7[Bombo] Chrono: Round finished, waiting for next note..."), false);
                        
                        // Auto-Close logic: 6 + serums (9 at max serums)
                        int targetRound = config.experimentGetMaxXp ? 20 : (6 + config.experimentSerumCount);
                        // Safety: don't close if we haven't even started (round 0 or 1)
                        if (config.experimentAutoClose && chronomatronOrder.size() >= targetRound && chronomatronOrder.size() > 2) {
                            mc.player.displayClientMessage(Component.literal("§c[Bombo] Target round reached (" + targetRound + ")! Closing."), false);
                            mc.player.closeContainer();
                        }
                    }
                }
            }
        }
    }

    private static void solveUltraSequencer(Minecraft mc, AbstractContainerScreen<?> screen) {
        BomboConfig.Settings config = BomboConfig.get();
        int maxRound = config.experimentGetMaxXp ? 20 : 9 - config.experimentSerumCount;
        List<Slot> slots = screen.getMenu().slots;
        
        int timerSlot = findTimerSlot(slots);
        if (timerSlot == -1) return;

        ItemStack timerStack = slots.get(timerSlot).getItem();
        boolean isInputPhase = isClock(timerStack);
        boolean isShowingPhase = isGlowstone(timerStack) || isGlowItem(timerStack);

        debugStr = String.format("Ultra | Added: %s | Clicks: %d/%d | Timer: %s", 
            hasAdded, clicks, ultrasequencerOrder.size(), timerStack.getItem().toString());

        // Memorization Phase (Glowstone is visible)
        if (isShowingPhase) {
            if (!wasInShowingPhase) {
                ultrasequencerOrder.clear();
                if (config.debugMode) mc.player.displayClientMessage(Component.literal("§7[Bombo] Ultra: Showing phase started, clearing map."), false);
            }
            
            for (int i = 0; i < slots.size() && i < 54; i++) {
                Slot slot = slots.get(i);
                if (slot.container == mc.player.getInventory()) continue;
                if (i == timerSlot) continue;
                
                ItemStack stack = slot.getItem();
                if (isNoteItem(stack)) {
                    int order = stack.getCount();
                    if (order > 0) {
                        if (!ultrasequencerOrder.containsKey(order - 1)) {
                            ultrasequencerOrder.put(order - 1, i);
                            if (config.debugMode) mc.player.displayClientMessage(Component.literal("§a[Bombo] Ultra: Captured dye #" + order + " at slot " + i), false);
                        }
                    }
                }
            }

            if (!ultrasequencerOrder.isEmpty()) {
                hasAdded = true;
                clicks = 0;
            }
        }
        wasInShowingPhase = isShowingPhase;

        // Clicking Phase (Clock is visible)
        if (isInputPhase && hasAdded && !ultrasequencerOrder.isEmpty() && ultrasequencerOrder.size() > clicks) {
            long delay = (long) (config.experimentClickDelay + (random.nextInt(41) - 20));
            if (delay < 0L) delay = 0L;

            if (System.currentTimeMillis() - lastClickTime > delay) {
                if (ultrasequencerOrder.containsKey(clicks)) {
                    int slotIndex = ultrasequencerOrder.get(clicks);
                    clickSlot(mc, screen, slotIndex);
                    if (config.debugMode) mc.player.displayClientMessage(Component.literal("§b[Bombo] Ultra Click #" + (clicks + 1) + " (Slot " + slotIndex + ")"), false);
                    lastClickTime = System.currentTimeMillis();
                    clicks++;
                    
                    if (clicks >= ultrasequencerOrder.size()) {
                        // Auto-Close logic: 2 + serums (5 at max serums)
                        int targetRound = config.experimentGetMaxXp ? 20 : (2 + config.experimentSerumCount);
                        if (config.experimentAutoClose && ultrasequencerOrder.size() >= targetRound && ultrasequencerOrder.size() > 1) {
                            mc.player.displayClientMessage(Component.literal("§c[Bombo] Target round reached (" + targetRound + ")! Closing."), false);
                            mc.player.closeContainer();
                        }
                    }
                } else {
                    clicks++;
                }
            }
        }
    }

    private static int findTimerSlot(List<Slot> slots) {
        Minecraft mc = Minecraft.getInstance();
        if (slots.size() > 49) {
            Slot s49 = slots.get(49);
            if (s49.container != mc.player.getInventory()) {
                ItemStack item = s49.getItem();
                if (isClock(item) || isGlowstone(item) || isGlowItem(item)) return 49;
            }
        }
        for (int i = 0; i < slots.size() && i < 54; i++) {
            Slot slot = slots.get(i);
            if (slot.container == mc.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (isClock(stack) || isGlowstone(stack) || isGlowItem(stack)) return i;
        }
        return -1;
    }

    private static void clickSlot(Minecraft mc, AbstractContainerScreen<?> screen, int slotIndex) {
        if (mc.gameMode != null && mc.player != null) {
            BomboConfig.Settings s = BomboConfig.get();
            int button = 0; // Left
            ClickType type = ClickType.PICKUP;
            
            switch (s.experimentClickType) {
                case 1 -> { button = 2; type = ClickType.CLONE; } // Middle
                case 2 -> { button = 0; type = ClickType.QUICK_MOVE; } // Shift
            }
            
            mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotIndex, button, type, mc.player);
        }
    }

    private static boolean isGlowstone(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.GLOWSTONE_DUST) || stack.is(Items.GLOWSTONE);
    }

    private static boolean isGlowItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.SEA_LANTERN) || stack.is(Items.GLOW_INK_SAC) || stack.is(Items.GLOW_ITEM_FRAME) || 
               stack.is(Items.BEACON) || stack.is(Items.MAGMA_BLOCK) || 
               stack.is(Items.COMMAND_BLOCK) || stack.is(Items.REPEATING_COMMAND_BLOCK);
    }

    private static boolean isClock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.CLOCK) || stack.is(Items.PLAYER_HEAD) || stack.is(Items.MAP) || stack.is(Items.FILLED_MAP);
    }

    private static boolean isNoteItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        // Background panes
        if (path.contains("glass_pane")) return false;
        // Known note items
        return path.contains("dye") || path.contains("stained_glass") || path.contains("skull") || 
               path.contains("head") || path.contains("star") || path.contains("dust") ||
               path.contains("terracotta") || path.contains("wool") || path.contains("concrete") ||
               path.equals("bone_meal") || path.equals("ink_sac") || path.equals("lapis_lazuli") ||
               path.equals("cocoa_beans") || path.equals("player_head");
    }

    private static boolean isDye(ItemStack stack) {
        return isNoteItem(stack);
    }
}
