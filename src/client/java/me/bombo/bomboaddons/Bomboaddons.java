package me.bombo.bomboaddons;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class Bomboaddons implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("bomboaddons");
    public static Consumer<String> sendMessageConsumer = null;

    public static void sendMessage(String message) {
        if (sendMessageConsumer != null) {
            sendMessageConsumer.accept(message);
        } else {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
                    return;
                }
            } catch (Throwable ignored) {}
            LOGGER.info(message);
        }
    }

    public static void logApiRequest(String url) {
        LOGGER.info("[API Request] " + url);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[BomboAddons] Initialized common/server mod features.");

        // Aspect of the Void (AOTV) & Etherwarp handler for Diamond Shovel (Server side only)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (stack != null && stack.is(Items.DIAMOND_SHOVEL)) {
                if (player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverLevel) {
                    handleAOTV(serverPlayer, serverLevel);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        // Register /physics and /nophysics commands
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.commands.Commands.literal("physics")
                .executes(context -> {
                    me.bombo.bomboaddons.features.HypixelWorldPhysics.noPhysics = !me.bombo.bomboaddons.features.HypixelWorldPhysics.noPhysics;
                    boolean active = me.bombo.bomboaddons.features.HypixelWorldPhysics.noPhysics;
                    String status = active ? "§aDISABLED (Hypixel Floating Blocks Enabled)" : "§cENABLED (Vanilla Physics)";
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§8[§bBombo§8] §7Block Physics: " + status), true);
                    return 1;
                })
                .then(net.minecraft.commands.Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                    .executes(context -> {
                        boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
                        me.bombo.bomboaddons.features.HypixelWorldPhysics.noPhysics = !enabled;
                        String status = !enabled ? "§aDISABLED (Hypixel Floating Blocks Enabled)" : "§cENABLED (Vanilla Physics)";
                        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§8[§bBombo§8] §7Block Physics: " + status), true);
                        return 1;
                    })
                )
            );

            dispatcher.register(net.minecraft.commands.Commands.literal("nophysics")
                .executes(context -> {
                    me.bombo.bomboaddons.features.HypixelWorldPhysics.noPhysics = true;
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§8[§bBombo§8] §7Block Physics: §aDISABLED (Hypixel Floating Blocks & Leaves Protection ON)"), true);
                    return 1;
                })
            );
        });
    }

    public static void handleAOTV(ServerPlayer player, ServerLevel level) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);

        if (player.isShiftKeyDown()) {
            // Etherwarp: Sneak + Right Click (up to 61 blocks)
            double maxDist = 61.0;
            Vec3 endPos = eyePos.add(lookVec.scale(maxDist));
            BlockHitResult hit = level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos targetBlock = hit.getBlockPos();
                BlockPos targetStand = targetBlock.above();
                BlockPos targetHead = targetStand.above();

                double destX = targetBlock.getX() + 0.5;
                double destY = targetBlock.getY() + 1.0;
                double destZ = targetBlock.getZ() + 0.5;

                // Particle & sound at start
                level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.3, 0.5, 0.3, 0.1);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.3F);

                // Teleport
                player.teleportTo(destX, destY, destZ);
                player.fallDistance = 0.0F;

                // Particle & sound at destination
                level.sendParticles(ParticleTypes.PORTAL, destX, destY + 1.0, destZ, 24, 0.3, 0.5, 0.3, 0.1);
                level.playSound(null, destX, destY, destZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.3F);
            }
        } else {
            // Instant Transmission: Normal Right Click (12 blocks forward)
            double maxDist = 12.0;
            Vec3 endPos = eyePos.add(lookVec.scale(maxDist));
            BlockHitResult hit = level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

            double destX;
            double destY;
            double destZ;

            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 hitPos = hit.getLocation();
                Vec3 backOff = lookVec.scale(0.5);
                Vec3 adjusted = hitPos.subtract(backOff);
                destX = adjusted.x;
                destY = adjusted.y;
                destZ = adjusted.z;
            } else {
                destX = endPos.x;
                destY = endPos.y - player.getEyeHeight();
                destZ = endPos.z;
            }

            // Particle & sound at start
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.3, 0.5, 0.3, 0.1);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

            // Teleport
            player.teleportTo(destX, destY, destZ);
            player.fallDistance = 0.0F;

            // Particle & sound at destination
            level.sendParticles(ParticleTypes.PORTAL, destX, destY + 1.0, destZ, 24, 0.3, 0.5, 0.3, 0.1);
            level.playSound(null, destX, destY, destZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
