package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.FuckDiorite;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({ClientLevel.class})
public class ClientLevelMixin {
   @ModifyVariable(
      method = {"setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private BlockState modifyBlockState(BlockState state, BlockPos pos) {
      return FuckDiorite.checkAndReplace(pos, state);
   }

   @org.spongepowered.asm.mixin.injection.Inject(
      method = {"addDestroyBlockEffect"},
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private void onAddDestroyBlockEffect(BlockPos pos, BlockState state, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderBreakParticles) {
         ci.cancel();
      }
   }

   @org.spongepowered.asm.mixin.injection.Inject(
      method = {"addBreakingBlockEffect"},
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private void onAddBreakingBlockEffect(BlockPos pos, net.minecraft.core.Direction direction, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderBreakParticles) {
         ci.cancel();
      }
   }

   @ModifyVariable(
      method = {"setTimeFromServer"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private long modifyTimeFromServer(long time) {
      me.bombo.bomboaddons.BomboaddonsClient.serverGameTimeTicks = time;
      if (BomboConfig.get().customTimeEnabled) {
         int hour = BomboConfig.get().customTimeHour;
         return (long)((hour - 6 + 24) % 24) * 1000L;
      } else {
         return time;
      }
   }

   @org.spongepowered.asm.mixin.injection.Inject(
      method = {"addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"},
      at = @At("HEAD"),
      require = 0
   )
   private void onAddParticleDirect(net.minecraft.core.particles.ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      if (options != null) {
         try {
            String typeName = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString();
            me.bombo.bomboaddons.cheat.esp.ParticleTracker.onParticle(typeName, x, y, z);
         } catch (Throwable ignored) {}
      }
   }

   @org.spongepowered.asm.mixin.injection.Inject(
      method = {"addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"},
      at = @At("HEAD"),
      require = 0
   )
   private void onAddParticleBool(net.minecraft.core.particles.ParticleOptions options, boolean overrideLimiter, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      if (options != null) {
         try {
            String typeName = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString();
            me.bombo.bomboaddons.cheat.esp.ParticleTracker.onParticle(typeName, x, y, z);
         } catch (Throwable ignored) {}
      }
   }

   @org.spongepowered.asm.mixin.injection.Inject(
      method = {"removeEntity(ILnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/world/entity/Entity;"},
      at = @At("HEAD"),
      require = 0
   )
   private void onRemoveEntity(int entityId, net.minecraft.world.entity.Entity.RemovalReason reason, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.entity.Entity> cir) {
      try {
         ClientLevel level = (ClientLevel)(Object)this;
         net.minecraft.world.entity.Entity entity = level.getEntity(entityId);
         if (entity != null) {
            me.bombo.bomboaddons.features.fishing.HotspotGoneAlert.onEntityRemoved(entity);
         }
      } catch (Throwable ignored) {}
   }
}
