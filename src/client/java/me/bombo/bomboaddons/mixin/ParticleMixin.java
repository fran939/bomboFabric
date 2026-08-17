package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ParticleTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public class ParticleMixin {
   @Inject(
      method = {"handleParticleEvent"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandleLevelParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
      if (!ParticleTracker.isParticleTrackingNeeded()) return;
      try {
         String typeName;
         try {
            typeName = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString();
         } catch (Throwable var5) {
            typeName = packet.getParticle().getType().toString();
         }

         ParticleTracker.onParticle(typeName, packet.getX(), packet.getY(), packet.getZ());
      } catch (Throwable ignored) {
      }
   }
}
