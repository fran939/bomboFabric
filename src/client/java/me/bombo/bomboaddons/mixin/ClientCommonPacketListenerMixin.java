package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {
    @Shadow @Final protected Connection connection;

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void onHandleResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (BomboConfig.get().bypassResourcePack) {
            try {
                if (packet.url() != null && !packet.url().isEmpty()) {
                    java.io.File packsDir = new java.io.File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "resourcepacks");
                    if (!packsDir.exists()) packsDir.mkdirs();
                    java.io.File packFile = new java.io.File(packsDir, "Hypixel_Skyblock.zip");
                    if (!packFile.exists()) {
                        new Thread(() -> {
                            try {
                                me.bombo.bomboaddons.Bomboaddons.sendMessage("§e[Bombo] Downloading Hypixel Resource Pack to local folder...");
                                java.net.URL url = new java.net.URL(packet.url());
                                java.nio.file.Files.copy(url.openStream(), packFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                me.bombo.bomboaddons.Bomboaddons.sendMessage("§a[Bombo] Saved Hypixel pack! You can now enable 'Hypixel_Skyblock.zip' in your Resource Packs menu.");
                            } catch (Exception e) {
                                me.bombo.bomboaddons.Bomboaddons.sendMessage("§c[Bombo] Failed to download Resource Pack: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }).start();
                    } else {
                        boolean isEnabled = false;
                        for (net.minecraft.server.packs.repository.Pack p : net.minecraft.client.Minecraft.getInstance().getResourcePackRepository().getSelectedPacks()) {
                            if (p.getId().contains("Hypixel_Skyblock")) {
                                isEnabled = true;
                                break;
                            }
                        }
                        if (!isEnabled) {
                            me.bombo.bomboaddons.Bomboaddons.sendMessage("§c[Bombo] Please enable the 'Hypixel_Skyblock.zip' texture pack in your Resource Packs menu!");
                        }
                    }
                }
                
                this.connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
                this.connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DOWNLOADED));
                this.connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                ci.cancel();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
