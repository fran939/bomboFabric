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
      BlockState diorite = FuckDiorite.checkAndReplace(pos, state);
      if (diorite != state) return diorite;
      return me.bombo.bomboaddons.DwarvenCarpetReplacer.checkAndReplace(state);
   }

   @ModifyVariable(
      method = {"setTimeFromServer"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private long modifyTimeFromServer(long time) {
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
            me.bombo.bomboaddons.ParticleTracker.onParticle(typeName, x, y, z);
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
            me.bombo.bomboaddons.ParticleTracker.onParticle(typeName, x, y, z);
         } catch (Throwable ignored) {}
      }
   }
}
