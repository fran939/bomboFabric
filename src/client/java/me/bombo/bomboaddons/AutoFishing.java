package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.network.chat.Component;

import java.util.Random;

public class AutoFishing {

    private static long nextClickTime = 0;
    private static long lastReelTime = 0;
    private static int clickQueue = 0;
    private static final Random random = new Random();
    private static boolean waitingForRecast = false;
    private static int lastCalculatedDelay = 0;

    public static void onTick(Minecraft client) {
        if (!BomboConfig.get().autoFishingEnabled) return;
        if (client.player == null || client.level == null) return;
        if (!(client.player.getMainHandItem().getItem() instanceof FishingRodItem)) return;

        long now = System.currentTimeMillis();

        if (clickQueue > 0 && now >= nextClickTime) {
            clickQueue--;
            client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
            client.player.swing(InteractionHand.MAIN_HAND);
            
            if (BomboConfig.get().autoFishingDebug) {
                client.player.sendSystemMessage(Component.literal("§a[AutoFishing] Clicked! §7(" + lastCalculatedDelay + "ms)"));
            }

            if (waitingForRecast) {
                waitingForRecast = false;
            } else if (clickQueue == 0) {
                // We just reeled in. Schedule a recast
                waitingForRecast = true;
                clickQueue = 1;
                // Recast after 200-400ms
                lastCalculatedDelay = 200 + random.nextInt(200);
                nextClickTime = now + lastCalculatedDelay;
                if (BomboConfig.get().autoFishingDebug) {
                    client.player.sendSystemMessage(Component.literal("§e[AutoFishing] Scheduling recast... §7(" + lastCalculatedDelay + "ms)"));
                }
            }
            return;
        }

        // If we are waiting for a recast, we shouldn't scan for !!! yet
        if (waitingForRecast || clickQueue > 0) return;
        
        // Don't scan if we just reeled in (debounce)
        if (now - lastReelTime < 2000) return;

        // Scan for ArmorStand with !!!
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand) {
                if (entity.hasCustomName()) {
                    String name = entity.getCustomName().getString();
                    if (name.contains("!!!")) {
                        // Found it!
                        
                        int min = BomboConfig.get().autoFishingMinDelay;
                        int max = BomboConfig.get().autoFishingMaxDelay;
                        if (max < min) max = min;
                        
                        lastCalculatedDelay = min + (max > min ? random.nextInt(max - min) : 0);
                        
                        if (BomboConfig.get().autoFishingDebug) {
                            client.player.sendSystemMessage(Component.literal("§c[AutoFishing] Detected !!! §7(" + lastCalculatedDelay + "ms delay)"));
                        }
                        
                        nextClickTime = now + lastCalculatedDelay;
                        clickQueue = 1; // 1 click to reel in. The recast is scheduled after.
                        lastReelTime = now;
                        break;
                    }
                }
            }
        }
    }
}
