package me.bombo.bomboaddons.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Minecraft's {@link ProfileKeyPairManager} so the EggFinder handshake can run
 * Skyblocker's aaron authentication (profile key pair + signed data) exactly like the
 * real mod does. Named the same as Skyblocker's own accessor
 * ({@code de.hysky.skyblocker.mixins.accessors.MinecraftAccessor}) for parity.
 */
@Mixin({Minecraft.class})
public interface MinecraftAccessor {
   @Accessor("profileKeyPairManager")
   ProfileKeyPairManager getProfileKeyPairManagerField();
}
