package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        // Only replace/add if we are on a remote server (multiplayer)
        if (this.minecraft == null || this.minecraft.isLocalServer()) return;

        Button serverLinksBtn = null;
        for (Renderable renderable : ((ScreenAccessor) (Object) this).getRenderables()) {
            if (renderable instanceof Button button) {
                if (isServerLinksButton(button)) {
                    serverLinksBtn = button;
                    break;
                }
            }
        }

        if (serverLinksBtn != null) {
            int originalX = serverLinksBtn.getX();
            int originalY = serverLinksBtn.getY();
            int originalW = serverLinksBtn.getWidth();
            int originalH = serverLinksBtn.getHeight();

            if (BomboConfig.get().serverListButton) {
                Button newButton = Button.builder(Component.literal("Server List"), btn -> {
                    this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(this));
                })
                .bounds(originalX, originalY, originalW, originalH)
                .build();

                removeWidget(serverLinksBtn);
                addRenderableWidget(newButton);
            }

            if (BomboConfig.get().reconnectButton) {
                Button reconnectBtn = Button.builder(Component.literal("Reconnect"), btn -> {
                    net.minecraft.client.multiplayer.ServerData server = this.minecraft.getCurrentServer();
                    if (server == null) {
                        server = me.bombo.bomboaddons.BomboaddonsClient.lastServerData;
                    }
                    if (server != null) {
                        if (this.minecraft.getConnection() != null) {
                            this.minecraft.getConnection().getConnection().disconnect(Component.literal("Reconnecting..."));
                        }
                        net.minecraft.client.multiplayer.resolver.ServerAddress address = 
                            net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(server.ip);
                        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                            new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new net.minecraft.client.gui.screens.TitleScreen()), 
                            this.minecraft, address, server, false, null
                        );
                    }
                })
                .bounds(originalX, originalY + originalH + 4, originalW, originalH)
                .build();

                addRenderableWidget(reconnectBtn);
            }
        }
    }

    private boolean isServerLinksButton(Button button) {
        Component message = button.getMessage();
        if (message != null && message.getContents() instanceof TranslatableContents translatable) {
            return "menu.server_links".equals(translatable.getKey());
        }
        return false;
    }
}
