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

    private boolean confirmingDisconnect = false;

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        // Only replace/add if we are on a remote server (multiplayer)
        if (this.minecraft == null || this.minecraft.isLocalServer()) return;

        Button serverLinksBtn = null;
        Button optionsBtn = null;
        for (Renderable renderable : ((ScreenAccessor) (Object) this).getRenderables()) {
            if (renderable instanceof Button button) {
                if (isServerLinksButton(button)) {
                    serverLinksBtn = button;
                } else if (isOptionsButton(button)) {
                    optionsBtn = button;
                }
            }
        }

        if (serverLinksBtn != null && BomboConfig.get().serverListButton) {
            int originalX = serverLinksBtn.getX();
            int originalY = serverLinksBtn.getY();
            int originalW = serverLinksBtn.getWidth();
            int originalH = serverLinksBtn.getHeight();

            Button newButton = Button.builder(Component.literal("Server List"), btn -> {
                this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(this));
            })
            .bounds(originalX, originalY, originalW, originalH)
            .build();

            removeWidget(serverLinksBtn);
            addRenderableWidget(newButton);
        }



        Button disconnectBtn = null;
        for (Renderable renderable : ((ScreenAccessor) (Object) this).getRenderables()) {
            if (renderable instanceof Button button) {
                if (isDisconnectButton(button)) {
                    disconnectBtn = button;
                    break;
                }
            }
        }

        if (disconnectBtn != null && BomboConfig.get().smartDisconnect) {
            int originalX = disconnectBtn.getX();
            int originalY = disconnectBtn.getY();
            int originalW = disconnectBtn.getWidth();
            int originalH = disconnectBtn.getHeight();

            Button origBtnRef = disconnectBtn;

            Button newDisconnectBtn = Button.builder(Component.translatable("menu.disconnect"), btn -> {
                if (!confirmingDisconnect) {
                    confirmingDisconnect = true;
                    btn.setMessage(Component.literal("§cConfirm Disconnect?"));
                } else {
                    origBtnRef.onPress(new net.minecraft.client.input.InputWithModifiers() {
                        @Override
                        public int modifiers() {
                            return 0;
                        }

                        @Override
                        public int input() {
                            return 0;
                        }
                    });
                }
            })
            .bounds(originalX, originalY, originalW, originalH)
            .build();

            removeWidget(disconnectBtn);
            addRenderableWidget(newDisconnectBtn);
        }
    }

    private boolean isServerLinksButton(Button button) {
        Component message = button.getMessage();
        if (message != null && message.getContents() instanceof TranslatableContents translatable) {
            return "menu.server_links".equals(translatable.getKey());
        }
        return false;
    }

    private boolean isOptionsButton(Button button) {
        Component message = button.getMessage();
        if (message != null && message.getContents() instanceof TranslatableContents translatable) {
            return "menu.options".equals(translatable.getKey());
        }
        return false;
    }

    private boolean isDisconnectButton(Button button) {
        Component message = button.getMessage();
        if (message != null && message.getContents() instanceof TranslatableContents translatable) {
            return "menu.disconnect".equals(translatable.getKey());
        }
        return false;
    }
}
