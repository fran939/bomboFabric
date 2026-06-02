package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.ParticleTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ParticleMixin {

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void onHandleLevelParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        try {
            String typeName;
            try {
                typeName = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString();
            } catch (Throwable e) {
                typeName = packet.getParticle().getType().toString();
            }
            ParticleTracker.onParticle(typeName, packet.getX(), packet.getY(), packet.getZ());
        } catch (Throwable t) {
            // Never crash the client from a particle packet
        }
    }
}
