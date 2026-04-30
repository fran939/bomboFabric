package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class GardenMovement {
    private static boolean forward = false;
    private static boolean backward = false;
    private static boolean left = false;
    private static boolean right = false;
    private static boolean breaking = false;
    private static boolean using = false;

    public static void onTick(Minecraft mc) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !SkyblockUtils.isInGarden()) {
            if (forward || backward || left || right || breaking) {
                if (forward) mc.options.keyUp.setDown(false);
                if (backward) mc.options.keyDown.setDown(false);
                if (left) mc.options.keyLeft.setDown(false);
                if (right) mc.options.keyRight.setDown(false);
                if (breaking) mc.options.keyAttack.setDown(false);
                if (using) mc.options.keyUse.setDown(false);
                reset();
            }
            return;
        }

        if (mc.screen != null) return; // Don't move while in a GUI

        // Apply held states to Minecraft's keys
        if (forward) mc.options.keyUp.setDown(true);
        if (backward) mc.options.keyDown.setDown(true);
        if (left) mc.options.keyLeft.setDown(true);
        if (right) mc.options.keyRight.setDown(true);
        if (breaking) mc.options.keyAttack.setDown(true);
        if (using) mc.options.keyUse.setDown(true);
    }

    public static void handleKey(int keyCode) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !SkyblockUtils.isInGarden()) return;
        
        // Don't trigger if in a GUI (unless it's a chat screen maybe, but usually better to avoid)
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;

        int fKey = ClickLogic.getKeyCode(s.gardenForwardKey);
        int bKey = ClickLogic.getKeyCode(s.gardenBackwardKey);
        int lKey = ClickLogic.getKeyCode(s.gardenLeftKey);
        int rKey = ClickLogic.getKeyCode(s.gardenRightKey);
        int brKey = ClickLogic.getKeyCode(s.gardenBreakKey);
        int uKey = ClickLogic.getKeyCode(s.gardenUseKey);

        if (keyCode == fKey && fKey != -1) {
            forward = !forward;
            if (!forward) mc.options.keyUp.setDown(false);
            if (forward) {
                backward = false;
                mc.options.keyDown.setDown(false);
            }
            sendToggleMsg("Forward", forward);
        } else if (keyCode == bKey && bKey != -1) {
            backward = !backward;
            if (!backward) mc.options.keyDown.setDown(false);
            if (backward) {
                forward = false;
                mc.options.keyUp.setDown(false);
            }
            sendToggleMsg("Backward", backward);
        } else if (keyCode == lKey && lKey != -1) {
            left = !left;
            if (!left) mc.options.keyLeft.setDown(false);
            if (left) {
                right = false;
                mc.options.keyRight.setDown(false);
            }
            sendToggleMsg("Left", left);
        } else if (keyCode == rKey && rKey != -1) {
            right = !right;
            if (!right) mc.options.keyRight.setDown(false);
            if (right) {
                left = false;
                mc.options.keyLeft.setDown(false);
            }
            sendToggleMsg("Right", right);
        } else if (keyCode == brKey && brKey != -1) {
            breaking = !breaking;
            if (breaking && mc.player != null) {
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            if (!breaking) mc.options.keyAttack.setDown(false);
            sendToggleMsg("Breaking", breaking);
        } else if (keyCode == uKey && uKey != -1) {
            using = !using;
            if (!using) mc.options.keyUse.setDown(false);
            sendToggleMsg("Using", using);
        }
    }

    private static void sendToggleMsg(String dir, boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§b[Garden] §f" + dir + ": " + (active ? "§aON" : "§cOFF")), true);
        }
    }

    public static void reset() {
        forward = false;
        backward = false;
        left = false;
        right = false;
        breaking = false;
        using = false;
    }
}
