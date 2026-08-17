package me.bombo.bomboaddons.mixin;

import java.io.File;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.util.ResourcePackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientCommonPacketListenerImpl.class})
public abstract class ClientCommonPacketListenerMixin {
   @Shadow
   @Final
   protected Connection connection;

   @Inject(
      method = {"handleResourcePackPush"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandleResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
      if (BomboConfig.get().noResourcePack || BomboConfig.get().bypassResourcePack) {
         try {
            if (packet.url() != null && !packet.url().isEmpty()) {
               File packsDir = new File(Minecraft.getInstance().gameDirectory, "resourcepacks");
               if (!packsDir.exists()) {
                  packsDir.mkdirs();
               }

               File packFile = new File(packsDir, "Hypixel_Skyblock.zip");
               if (!packFile.exists()) {
                  (new Thread(() -> {
                     try {
                        URL url = new URL(packet.url());
                        Files.copy(url.openStream(), packFile.toPath(), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
                        ResourcePackHelper.enableSkyblockPack();
                     } catch (Exception e) {
                        e.printStackTrace();
                     }
                  })).start();
               } else {
                  boolean isEnabled = false;
                  for (Pack p : Minecraft.getInstance().getResourcePackRepository().getSelectedPacks()) {
                     if (p.getId().toLowerCase().contains("hypixel_skyblock") || p.getId().toLowerCase().contains("skyblock")) {
                        isEnabled = true;
                        break;
                     }
                  }
                  if (!isEnabled) {
                     ResourcePackHelper.enableSkyblockPack();
                  }
               }
            }

            this.connection.send(new ServerboundResourcePackPacket(packet.id(), Action.ACCEPTED));
            this.connection.send(new ServerboundResourcePackPacket(packet.id(), Action.SUCCESSFULLY_LOADED));
            ci.cancel();
         } catch (Throwable t) {
            t.printStackTrace();
         }
      }
   }
}
