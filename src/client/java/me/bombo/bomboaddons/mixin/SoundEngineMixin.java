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
                        mc.gui.setTitle(Component.literal("§6§lGolden Dragon Nest"));
                        mc.gui.setSubtitle(Component.literal("§eDragon sound detected nearby!"));
                        mc.gui.setTimes(10, 50, 10);
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
