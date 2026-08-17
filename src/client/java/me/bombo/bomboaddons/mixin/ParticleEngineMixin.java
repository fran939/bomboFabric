package me.bombo.bomboaddons.mixin;

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
      if (!ParticleTracker.isParticleTrackingNeeded()) return;
      try {
         String typeName = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString();
         ParticleTracker.onParticle(typeName, x, y, z);
      } catch (Throwable ignored) {
      }
   }
}
