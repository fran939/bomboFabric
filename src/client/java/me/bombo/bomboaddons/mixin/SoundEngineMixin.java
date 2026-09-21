package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SoundEngine.class})
public class SoundEngineMixin {
   private static long lastDragonSoundAlert = 0L;

   private static final ThreadLocal<Boolean> IS_PLAYING_REPLACEMENT = ThreadLocal.withInitial(() -> false);

   @Inject(
      method = {"play"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPlayHead(SoundInstance sound, CallbackInfoReturnable<?> ci) {
      if (sound == null || Boolean.TRUE.equals(IS_PLAYING_REPLACEMENT.get())) return;
      try {
         String loc = sound.getSound() != null && sound.getSound().getLocation() != null ? sound.getSound().getLocation().toString() : "";
         String ident = sound.getIdentifier() != null ? sound.getIdentifier().toString() : "";

         String repl = me.bombo.bomboaddons.features.sounds.CustomSoundManager.getReplacement(loc);
         if (repl == null || repl.isEmpty()) {
            repl = me.bombo.bomboaddons.features.sounds.CustomSoundManager.getReplacement(ident);
         }

         if (repl != null && !repl.trim().isEmpty()) {
            ci.cancel();
            float vol = sound.getVolume();
            float pitch = sound.getPitch();
            if (BomboConfig.get().debugSounds) {
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null) {
                  mc.player.sendSystemMessage(Component.literal("§8[§bSound Debug§8] §cBlocked: §e" + loc + " §7(ident: " + ident + ") ➔ §aReplacing with: §e" + repl));
               }
            }
            IS_PLAYING_REPLACEMENT.set(true);
            try {
               me.bombo.bomboaddons.features.sounds.CustomSoundManager.playCustomOrVanillaSound(repl, vol, pitch);
            } finally {
               IS_PLAYING_REPLACEMENT.set(false);
            }
         } else if (BomboConfig.get().debugSounds) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               mc.player.sendSystemMessage(Component.literal("§8[§bSound Debug§8] §7[Played] §e" + loc + " §8| §7Ident: §e" + ident + " §7(v:" + sound.getVolume() + " p:" + sound.getPitch() + ")"));
            }
         }
      } catch (Throwable ignored) {}
   }
   @Inject(
      method = {"play"},
      at = {@At("TAIL")}
   )
   private void onPlay(SoundInstance sound, CallbackInfoReturnable<?> ci) {
      if (sound == null) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;

      try {
         String id = sound.getIdentifier().toString();
         float vol = sound.getVolume();
         float pitch = sound.getPitch();

         String area = me.bombo.bomboaddons.BomboaddonsClient.currentArea;
         if (area != null && (area.toLowerCase().contains("crystal") || area.toLowerCase().contains("hollows"))) {
            if (id.contains("entity.ender_dragon.growl") || id.contains("entity.ender_dragon.flap") ||
                id.contains("ender_dragon.growl") || id.contains("ender_dragon.flap")) {
               long now = System.currentTimeMillis();
               if (now - lastDragonSoundAlert > 5000L) {
                  lastDragonSoundAlert = now;
                  mc.execute(() -> {
                     if (mc.gui != null) {
                        mc.gui.hud.setTitle(Component.literal("§6§lGolden Dragon Nest"));
                        mc.gui.hud.setSubtitle(Component.literal("§eDragon sound detected nearby!"));
                     }
                     if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §6★ Golden Dragon Nest sound detected nearby!"));
                     }
                  });
               }
            }
         }

         BomboConfig.Settings s = BomboConfig.get();
         if (s != null && s.debugSounds) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §e[Sound] §a" + id + " §7(v:" + vol + " p:" + pitch + ")"));
         }
      } catch (Exception ignored) {
      }
   }
}
