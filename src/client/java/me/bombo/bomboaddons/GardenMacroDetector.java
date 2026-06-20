package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public class GardenMacroDetector {
    public static long lastTeleportWeaponUseTime = 0;
    public static long lastWarpTpCommandTime = 0;
    public static boolean expectingTeleport = false;

    private static Vec3 recordedPos = null;
    private static float recordedYaw = 0.0f;
    private static float recordedPitch = 0.0f;
    private static boolean checkingPacket = false;

    // Called from mixin before packet is handled
    public static void onMovePlayerPacketHead() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            recordedPos = mc.player.position();
            recordedYaw = mc.player.getYRot();
            recordedPitch = mc.player.getXRot();
            checkingPacket = true;
        }
    }

    // Called from mixin after packet is handled
    public static void onMovePlayerPacketTail() {
        if (!checkingPacket) return;
        checkingPacket = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (!s.gardenMacroCheckDetector || !s.gardenMovement || !SkyblockUtils.isInGarden()) {
            return;
        }

        // Check if expecting a legitimate teleport
        long now = System.currentTimeMillis();
        boolean commandTp = expectingTeleport && (now - lastWarpTpCommandTime < 10000);
        boolean weaponTp = now - lastTeleportWeaponUseTime < 1500;

        if (commandTp || weaponTp) {
            // Legitimate teleport/rotation, ignore
            if (commandTp) {
                expectingTeleport = false; // Reset command tp flag since it has been fulfilled
            }
            return;
        }

        // Calculate changes
        double dist = mc.player.position().distanceTo(recordedPos);
        float yawDiff = Math.abs(mc.player.getYRot() - recordedYaw);
        float pitchDiff = Math.abs(mc.player.getXRot() - recordedPitch);

        if (dist > 8.0 || yawDiff > 0.1f || pitchDiff > 0.1f) {
            // Trigger Macro Check Alert!
            triggerMacroCheck(dist, yawDiff, pitchDiff);
        }
    }

    public static void recordWeaponUse() {
        lastTeleportWeaponUseTime = System.currentTimeMillis();
    }

    public static void recordCommandSend(String command) {
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        String lower = command.trim().toLowerCase();
        if (lower.startsWith("warp") || lower.startsWith("tp") || 
            lower.startsWith("tptoplot") || lower.startsWith("plottp") ||
            lower.startsWith("hub") || lower.startsWith("is") || lower.startsWith("island")) {
            lastWarpTpCommandTime = System.currentTimeMillis();
            expectingTeleport = true;
        }
    }

    private static void triggerMacroCheck(double dist, float yawDiff, float pitchDiff) {
        BomboConfig.Settings s = BomboConfig.get();
        Minecraft mc = Minecraft.getInstance();

        // 1. Stop movement (Only if Stop Movement on Check is enabled)
        if (s.gardenMacroCheckStop) {
            s.gardenMovement = false;
            BomboConfig.save();
            if (mc.player != null) {
                mc.options.keyUp.setDown(false);
                mc.options.keyDown.setDown(false);
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(false);
                mc.options.keyAttack.setDown(false);
                mc.options.keyUse.setDown(false);
            }
            GardenMovement.reset();
        }

        // 2. Alert in chat - spam 5 times!
        if (mc.player != null) {
            for (int i = 0; i < 5; i++) {
                mc.player.sendSystemMessage(Component.literal("§c§l[BomboAddons] WARNING: SUDDEN TELEPORT/ROTATION DETECTED!"));
            }
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Details: Distance: " + String.format("%.2f", dist) + "m, YawDiff: " + String.format("%.2f", yawDiff) + "°, PitchDiff: " + String.format("%.2f", pitchDiff) + "°"));
        }

        mc.execute(() -> {
            mc.gui.hud.setTimes(10, 100, 20);
            mc.gui.hud.setTitle(Component.literal("§c§lMACRO CHECKED!"));
            mc.gui.hud.setSubtitle(Component.literal("§eGarden Movement Alert"));
        });

        // 4. Play alarm sound repetitively
        net.minecraft.sounds.SoundEvent finalSound;
        switch (s.gardenMacroCheckSound != null ? s.gardenMacroCheckSound.toLowerCase() : "anvil") {
            case "pling" -> finalSound = SoundEvents.NOTE_BLOCK_PLING.value();
            case "wither" -> finalSound = SoundEvents.WITHER_DEATH;
            case "explode" -> finalSound = SoundEvents.GENERIC_EXPLODE.value();
            default -> finalSound = SoundEvents.ANVIL_LAND;
        }

        int soundCount = s.gardenMacroCheckSoundCount;
        int soundDelay = s.gardenMacroCheckSoundDelay;

        new Thread(() -> {
            for (int i = 0; i < soundCount; i++) {
                Minecraft.getInstance().execute(() -> {
                    try {
                        Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(
                                finalSound, 1.0F
                            )
                        );
                    } catch (Throwable ignored) {}
                });
                try {
                    Thread.sleep(soundDelay);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }
}
