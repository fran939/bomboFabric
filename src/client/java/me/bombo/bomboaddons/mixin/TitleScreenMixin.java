package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.mixin.ScreenAccessor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        Button multiplayerBtn = null;
        for (Renderable renderable : ((ScreenAccessor) (Object) this).getRenderables()) {
            if (renderable instanceof Button button) {
                Component msg = button.getMessage();
                if (msg != null && msg.getContents() instanceof TranslatableContents trans) {
                    if ("menu.multiplayer".equals(trans.getKey())) {
                        multiplayerBtn = button;
                        break;
                    }
                }
            }
        }

        if (multiplayerBtn != null && me.bombo.bomboaddons.BomboConfig.get().hypixelShortcutButton) {
            int mx = multiplayerBtn.getX();
            int my = multiplayerBtn.getY();
            int mw = multiplayerBtn.getWidth();

            // Place the small button to the right of the Multiplayer button
            int bx = mx + mw + 4;
            int by = my;

            Button hypixelBtn = Button.builder(Component.literal("H"), btn -> {
                net.minecraft.client.multiplayer.resolver.ServerAddress address = 
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("hypixel.net");
                net.minecraft.client.multiplayer.ServerData server = 
                    new net.minecraft.client.multiplayer.ServerData("Hypixel", "hypixel.net", net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                    new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this), 
                    this.minecraft, address, server, false, null
                );
            })
            .bounds(bx, by, 20, 20)
            .build();

            addRenderableWidget(hypixelBtn);
        }
    }
}
