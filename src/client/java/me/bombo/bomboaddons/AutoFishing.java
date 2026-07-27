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
    private static boolean debuggedSlugSkip = false;
    private static int lastBobberId = -1;

    public static void onTick(Minecraft client) {
        if (!BomboConfig.get().autoFishingEnabled) return;
        if (client.player == null || client.level == null) return;
        if (!(client.player.getMainHandItem().getItem() instanceof FishingRodItem)) return;

        if (client.player.fishing != null && client.player.fishing.getId() != lastBobberId) {
            lastBobberId = client.player.fishing.getId();
            debuggedSlugSkip = false;
        }

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
                        
                        // Check Slug Delay
                        if (BomboConfig.get().autoFishingSlugMode && BomboConfig.get().autoFishingSlugDelay > 0) {
                            if (client.player.fishing == null) continue; // No bobber exists
                            int requiredTicks = (int) (BomboConfig.get().autoFishingSlugDelay * 20.0f);
                            if (client.player.fishing.tickCount < requiredTicks) {
                                if (BomboConfig.get().autoFishingDebug && !debuggedSlugSkip) {
                                    client.player.sendSystemMessage(Component.literal("§c[AutoFishing] Slug mode: Skipping bite, bobber time " + String.format("%.1f", client.player.fishing.tickCount/20.0f) + "s < " + BomboConfig.get().autoFishingSlugDelay + "s"));
                                    debuggedSlugSkip = true;
                                }
                                continue; // Wait until bobber has been out long enough
                            }
                        }
                        
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

    public static void renderTimer(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        
        BomboConfig.Settings s = BomboConfig.get();
        if (s.showBobberTime) {
            if (client.player.fishing != null) {
                int tickCount = client.player.fishing.tickCount;
                float seconds = tickCount / 20.0f;
                String text = String.format("§bBobber Time: %.1fs", seconds);
                
                int screenWidth = client.getWindow().getGuiScaledWidth();
                int screenHeight = client.getWindow().getGuiScaledHeight();
                
                int width = client.font.width(text);
                graphics.text(client.font, text, screenWidth / 2 - width / 2, screenHeight / 2 + 15, 0xFFFFFFFF, true);
            }
        }
    }
}
