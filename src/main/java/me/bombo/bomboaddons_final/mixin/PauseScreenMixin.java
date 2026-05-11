package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.BomboConfig;
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
        if (!BomboConfig.get().serverListButton) return;
        // Only replace if we are on a remote server (multiplayer)
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
            Button newButton = Button.builder(Component.literal("Server List"), btn -> {
                this.minecraft.setScreen(new JoinMultiplayerScreen(this));
            })
            .bounds(serverLinksBtn.getX(), serverLinksBtn.getY(), serverLinksBtn.getWidth(), serverLinksBtn.getHeight())
            .build();

            removeWidget(serverLinksBtn);
            addRenderableWidget(newButton);
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
