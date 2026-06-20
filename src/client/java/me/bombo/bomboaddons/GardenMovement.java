package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class GardenMovement {
    private static boolean forward = false;
    private static boolean backward = false;
    private static boolean left = false;
    private static boolean right = false;
    private static boolean breaking = false;
    private static boolean using = false;

    // Direction Helper Tracking
    private static boolean wasGoingForward = false;
    private static boolean wasGoingBackward = false;
    private static boolean wasGoingLeft = false;
    private static boolean wasGoingRight = false;
    private static boolean warped = false;
    private static long warningStartTime = 0;
    private static String correctArrow = "";

    public static void onTick(Minecraft mc) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !SkyblockUtils.isInGarden()) {
            clearWasGoing();
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

        if (mc.gui.screen() != null) return; // Don't move while in a GUI

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
        
        // Don't trigger if in a GUI
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return;

        int fKey = ClickLogic.getKeyCode(s.gardenForwardKey);
        int bKey = ClickLogic.getKeyCode(s.gardenBackwardKey);
        int lKey = ClickLogic.getKeyCode(s.gardenLeftKey);
        int rKey = ClickLogic.getKeyCode(s.gardenRightKey);
        int brKey = ClickLogic.getKeyCode(s.gardenBreakKey);
        int uKey = ClickLogic.getKeyCode(s.gardenUseKey);

        if (keyCode == fKey && fKey != -1) {
            toggleForward();
        } else if (keyCode == bKey && bKey != -1) {
            toggleBackward();
        } else if (keyCode == lKey && lKey != -1) {
            toggleLeft();
        } else if (keyCode == rKey && rKey != -1) {
            toggleRight();
        } else if (keyCode == brKey && brKey != -1) {
            toggleBreak();
        } else if (keyCode == uKey && uKey != -1) {
            toggleUse();
        }
    }

    public static void toggleForward() {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();

        forward = !forward;
        mc.options.keyUp.setDown(forward);
        if (forward) {
            if (s.gardenDirectionHelper && warped) {
                if (s.gardenSugarCane) {
                    if (wasGoingBackward || wasGoingRight) {
                        warningStartTime = System.currentTimeMillis();
                        correctArrow = "→";
                        warped = false;
                    } else {
                        warped = false;
                    }
                } else {
                    if (!wasGoingForward) {
                        warningStartTime = System.currentTimeMillis();
                        if (wasGoingBackward) correctArrow = "↓";
                        else if (wasGoingLeft) correctArrow = "←";
                        else if (wasGoingRight) correctArrow = "→";
                        else correctArrow = "↓";
                        warped = false;
                    } else {
                        warped = false;
                    }
                }
            }

            if (s.gardenSugarCane) {
                right = false;
                mc.options.keyRight.setDown(false);
            } else {
                backward = false;
                mc.options.keyDown.setDown(false);
            }
        }
        sendToggleMsg("Forward", forward);
    }

    public static void toggleBackward() {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();

        backward = !backward;
        mc.options.keyDown.setDown(backward);
        if (backward) {
            if (s.gardenDirectionHelper && warped) {
                if (s.gardenSugarCane) {
                    if (wasGoingForward || wasGoingLeft) {
                        warningStartTime = System.currentTimeMillis();
                        correctArrow = "←";
                        warped = false;
                    } else {
                        warped = false;
                    }
                } else {
                    if (!wasGoingBackward) {
                        warningStartTime = System.currentTimeMillis();
                        if (wasGoingForward) correctArrow = "↑";
                        else if (wasGoingLeft) correctArrow = "←";
                        else if (wasGoingRight) correctArrow = "→";
                        else correctArrow = "↑";
                        warped = false;
                    } else {
                        warped = false;
                    }
                }
            }
            
            if (s.gardenSugarCane) {
                left = false;
                mc.options.keyLeft.setDown(false);
            } else {
                forward = false;
                mc.options.keyUp.setDown(false);
            }
        }
        sendToggleMsg("Backward", backward);
    }

    public static void toggleLeft() {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();

        left = !left;
        mc.options.keyLeft.setDown(left);
        if (left) {
            if (s.gardenDirectionHelper && warped) {
                if (s.gardenSugarCane) {
                    if (wasGoingBackward || wasGoingRight) {
                        warningStartTime = System.currentTimeMillis();
                        correctArrow = "↓";
                        warped = false;
                    } else {
                        warped = false;
                    }
                } else {
                    if (!wasGoingLeft) {
                        warningStartTime = System.currentTimeMillis();
                        if (wasGoingRight) correctArrow = "→";
                        else if (wasGoingForward) correctArrow = "↑";
                        else if (wasGoingBackward) correctArrow = "↓";
                        else correctArrow = "→";
                        warped = false;
                    } else {
                        warped = false;
                    }
                }
            }
            
            if (s.gardenSugarCane) {
                backward = false;
                mc.options.keyDown.setDown(false);
            } else {
                right = false;
                mc.options.keyRight.setDown(false);
            }
        }
        sendToggleMsg("Left", left);
    }

    public static void toggleRight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();

        right = !right;
        mc.options.keyRight.setDown(right);
        if (right) {
            if (s.gardenDirectionHelper && warped) {
                if (s.gardenSugarCane) {
                    if (wasGoingForward || wasGoingLeft) {
                        warningStartTime = System.currentTimeMillis();
                        correctArrow = "↑";
                        warped = false;
                    } else {
                        warped = false;
                    }
                } else {
                    if (!wasGoingRight) {
                        warningStartTime = System.currentTimeMillis();
                        if (wasGoingLeft) correctArrow = "←";
                        else if (wasGoingForward) correctArrow = "↑";
                        else if (wasGoingBackward) correctArrow = "↓";
                        else correctArrow = "←";
                        warped = false;
                    } else {
                        warped = false;
                    }
                }
            }
            
            if (s.gardenSugarCane) {
                forward = false;
                mc.options.keyUp.setDown(false);
            } else {
                left = false;
                mc.options.keyLeft.setDown(false);
            }
        }
        sendToggleMsg("Right", right);
    }

    public static void toggleBreak() {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();

        breaking = !breaking;
        mc.options.keyAttack.setDown(breaking);
        if (breaking && mc.player != null) {
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        sendToggleMsg("Breaking", breaking);
    }

    public static void toggleUse() {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();

        using = !using;
        mc.options.keyUse.setDown(using);
        sendToggleMsg("Using", using);
    }

    private static void sendToggleMsg(String dir, boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal("§b[Garden] §f" + dir + ": " + (active ? "§aON" : "§cOFF")));
        }
    }

    public static void onWarpTriggered() {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement) return;
        
        Minecraft mc = Minecraft.getInstance();
        
        boolean isMovingNow = forward || backward || left || right ||
                             mc.options.keyUp.isDown() || mc.options.keyDown.isDown() ||
                             mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        if (warped && !isMovingNow) {
            return;
        }
        
        wasGoingForward = forward || mc.options.keyUp.isDown();
        wasGoingBackward = backward || mc.options.keyDown.isDown();
        wasGoingLeft = left || mc.options.keyLeft.isDown();
        wasGoingRight = right || mc.options.keyRight.isDown();
        
        if (wasGoingForward || wasGoingBackward || wasGoingLeft || wasGoingRight) {
            warped = true;
        }
        
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        reset();
    }

    public static void drawDirectionWarning(net.minecraft.client.gui.GuiGraphicsExtractor g) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMovement || !s.gardenDirectionHelper) return;

        long now = System.currentTimeMillis();
        if (now - warningStartTime > 3000) return;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        net.minecraft.client.gui.Font font = mc.font;

        int x = screenWidth / 2;
        int y = screenHeight / 2 - 50;

        String warningText = "§c§lWrong lane brochacho";
        String arrowText = "§6§l" + correctArrow;

        int textW = Math.max(font.width(warningText.replaceAll("(?i)§.", "")), font.width(arrowText.replaceAll("(?i)§.", "")));
        int padX = 10;
        int padY = 8;
        g.fill(x - textW / 2 - padX, y - padY, x + textW / 2 + padX, y + 25 + padY, 0xAA000000);
        g.outline(x - textW / 2 - padX, y - padY, textW + padX * 2, 25 + padY * 2, 0xFFFF0000);

        g.centeredText(font, warningText, x, y, 0xFFFFFFFF);
        g.centeredText(font, arrowText, x, y + 12, 0xFFFFFFFF);
    }

    public static boolean shouldLockMouse() {
        return BomboConfig.get().lockMouseOnGarden && SkyblockUtils.isInGarden() && (forward || backward || left || right);
    }

    public static void clearWasGoing() {
        wasGoingForward = false;
        wasGoingBackward = false;
        wasGoingLeft = false;
        wasGoingRight = false;
        warped = false;
    }

    public static void onManualReset() {
        clearWasGoing();
        reset();
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
