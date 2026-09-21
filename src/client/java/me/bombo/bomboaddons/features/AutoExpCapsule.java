package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class AutoExpCapsule {

    public static boolean isToggled = false;
    private static long lastClickTime = 0L;
    private static boolean keyWasDown = false;

    private static final Pattern LEVEL_PROGRESS_PATTERN = Pattern.compile("Level\\s+(\\d+)\\s*(?:->|➜|➔)\\s*(\\d+)?\\s*(?:\\((.+?)\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERCENT_PATTERN = Pattern.compile("([\\d.]+)%");
    private static final Pattern MAX_LEVEL_PATTERN = Pattern.compile("Level\\s+(50|\\d+)\\s*\\(MAX\\)", Pattern.CASE_INSENSITIVE);

    public static class ToolExpInfo {
        public int currentLevel = -1;
        public int targetLevel = -1;
        public String progressStr = "";
        public String percentStr = "";
        public boolean isMax = false;
        public int toolSlot = -1;
        public int capsuleSlot = -1;
        public String toolName = "";
    }

    public static ToolExpInfo getHexToolInfo(AbstractContainerScreen<?> screen) {
        if (screen == null) return null;
        String title = ChatFormatting.stripFormatting(screen.getTitle().getString()).toLowerCase();
        if (!title.contains("hex") && !title.contains("modifier")) {
            return null;
        }

        ToolExpInfo info = new ToolExpInfo();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null) return null;

        for (Slot slot : mc.player.containerMenu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            // Only inspect container slots (exclude player hotbar/inventory for tool analysis)
            if (slot.index >= mc.player.containerMenu.slots.size() - 36) continue;

            ItemStack stack = slot.getItem();
            String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
            String sbId = SkyblockUtils.getSkyblockId(stack);
            if (sbId == null) sbId = "";

            if (name.toLowerCase().contains("tool exp capsule") || sbId.equalsIgnoreCase("TOOL_EXP_CAPSULE")) {
                info.capsuleSlot = slot.index;
            }

            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                List<Component> lines = lore.lines();
                for (int i = 0; i < lines.size(); i++) {
                    String clean = ChatFormatting.stripFormatting(lines.get(i).getString());

                    Matcher maxMatcher = MAX_LEVEL_PATTERN.matcher(clean);
                    if (maxMatcher.find()) {
                        info.currentLevel = 50;
                        info.isMax = true;
                        info.toolSlot = slot.index;
                        info.toolName = name;
                        break;
                    }

                    Matcher lvlMatcher = LEVEL_PROGRESS_PATTERN.matcher(clean);
                    if (lvlMatcher.find()) {
                        try {
                            info.currentLevel = Integer.parseInt(lvlMatcher.group(1));
                            if (lvlMatcher.group(2) != null) {
                                info.targetLevel = Integer.parseInt(lvlMatcher.group(2));
                            }
                            if (lvlMatcher.group(3) != null) {
                                info.progressStr = lvlMatcher.group(3);
                            }
                        } catch (Exception ignored) {}

                        // Next line often has the percentage e.g. "14.89%"
                        if (i + 1 < lines.size()) {
                            String nextClean = ChatFormatting.stripFormatting(lines.get(i + 1).getString());
                            Matcher pctMatcher = PERCENT_PATTERN.matcher(nextClean);
                            if (pctMatcher.find()) {
                                info.percentStr = pctMatcher.group(1) + "%";
                            }
                        }

                        info.toolSlot = slot.index;
                        info.toolName = name;
                        break;
                    }
                }
            }
        }

        return (info.toolSlot != -1 || info.capsuleSlot != -1) ? info : null;
    }

    private static int slotNotFoundGraceTicks = 0;

    public static void onTick(Minecraft mc) {
        onClientTick();
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            isToggled = false;
            slotNotFoundGraceTicks = 0;
            return;
        }

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.autoExpCapsuleEnabled || s.hideCheats) {
            isToggled = false;
            slotNotFoundGraceTicks = 0;
            return;
        }

        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> acs)) {
            isToggled = false;
            keyWasDown = false;
            slotNotFoundGraceTicks = 0;
            return;
        }

        ToolExpInfo info = getHexToolInfo(acs);
        if (info == null) {
            isToggled = false;
            keyWasDown = false;
            slotNotFoundGraceTicks = 0;
            return;
        }

        // Check keybind toggle (uses same key as auto anvil)
        String key = s.anvilAutoCombineKey;
        if (key != null && !key.isEmpty()) {
            int code = ClickLogic.getKeyCode(key);
            if (code != -1) {
                boolean down = ClickLogic.isCodeDown(mc.getWindow().handle(), mc.getWindow(), code);
                if (down && !keyWasDown) {
                    isToggled = !isToggled;
                    slotNotFoundGraceTicks = 0;
                    Bomboaddons.sendMessage("§8[§3Bombo§8] §7Auto Combine Exp Capsule: " + (isToggled ? "§aENABLED" : "§cDISABLED"));
                }
                keyWasDown = down;
            }
        }

        if (!isToggled) return;

        // Check if tool is level 50 or max
        if (info.isMax || info.currentLevel >= 50) {
            isToggled = false;
            slotNotFoundGraceTicks = 0;
            Bomboaddons.sendMessage("§8[§3Bombo§8] §aTool reached Level 50! Auto Exp Capsule stopped.");
            return;
        }

        if (info.capsuleSlot == -1) {
            slotNotFoundGraceTicks++;
            // Allow up to 10 ticks (0.5s) for the slot / cursor animation to settle before giving up
            if (slotNotFoundGraceTicks > 10) {
                isToggled = false;
                slotNotFoundGraceTicks = 0;
                Bomboaddons.sendMessage("§8[§3Bombo§8] §cTool Exp Capsule slot not found in Hex menu.");
            }
            return;
        } else {
            slotNotFoundGraceTicks = 0;
        }

        long now = System.currentTimeMillis();
        int delay = Math.max(100, s.anvilAutoCombineDelay);
        if (now - lastClickTime >= delay) {
            lastClickTime = now;
            if (mc.gameMode != null && mc.player.containerMenu != null) {
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, info.capsuleSlot, 0, ContainerInput.PICKUP, mc.player);
            }
        }
    }

    public static void renderHexOverlay(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.autoExpCapsuleEnabled) return;

        ToolExpInfo info = getHexToolInfo(screen);
        if (info == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int boxW = 240;
        int boxH = 34;
        int boxX = (screen.width - boxW) / 2;
        int boxY = 6;

        // Draw card background
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE0F172A);
        int borderCol = isToggled ? 0xFF22C55E : 0xFF38BDF8;
        g.outline(boxX, boxY, boxW, boxH, borderCol);

        // Status badge
        String status = isToggled ? "§a[AUTO-COMBINING]" : "§7[Key: " + (s.anvilAutoCombineKey.isEmpty() ? "None" : s.anvilAutoCombineKey) + "]";
        g.text(font, "§6The Hex ➜ Farming Tool Exp", boxX + 8, boxY + 6, 0xFFFFFFFF, true);
        g.text(font, status, boxX + boxW - 8 - font.width(status), boxY + 6, 0xFFFFFFFF, false);

        // Level & Progress info
        String lvlStr;
        if (info.isMax || info.currentLevel >= 50) {
            lvlStr = "§aLevel 50 §6(MAX LEVEL)";
        } else if (info.currentLevel > 0) {
            lvlStr = "§eLevel " + info.currentLevel + (info.targetLevel > 0 ? " ➔ " + info.targetLevel : "") + " §7(" + info.progressStr + ")" + (!info.percentStr.isEmpty() ? " §b" + info.percentStr : "");
        } else {
            lvlStr = "§7Place a farming tool in The Hex";
        }

        g.text(font, lvlStr, boxX + 8, boxY + 18, 0xFFE2E8F0, false);
    }
}
