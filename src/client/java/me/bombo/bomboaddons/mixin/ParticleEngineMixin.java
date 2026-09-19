package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ParticleTracker;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ParticleEngine.class})
public class ParticleEngineMixin {
   @Inject(
      method = {"createParticle"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCreateParticle(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> cir) {
      if (options == null) return;
      try {
         String typeName = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString();
         BomboConfig.Settings s = BomboConfig.get();
         if (s != null) {
            if (s.noRenderExplosions && (typeName.contains("explosion") || typeName.contains("gust"))) {
               cir.setReturnValue(null);
               return;
            }
            if (s.noRenderDeadPoof && typeName.contains("poof")) {
               cir.setReturnValue(null);
               return;
            }
            if (s.noRenderBreakParticles && (typeName.contains("block") || typeName.contains("dust_pillar") || typeName.contains("falling_dust"))) {
               cir.setReturnValue(null);
               return;
            }
         }
         if (!ParticleTracker.isParticleTrackingNeeded()) return;
         ParticleTracker.onParticle(typeName, x, y, z);
      } catch (Throwable ignored) {
      }
   }

   @Inject(
      method = {"destroy"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onDestroy(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderBreakParticles) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"crack"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onCrack(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderBreakParticles) {
         ci.cancel();
      }
   }
}

