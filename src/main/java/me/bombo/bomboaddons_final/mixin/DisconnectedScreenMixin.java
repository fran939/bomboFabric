package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.BomboaddonsClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow @Final private Screen parent;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        Button backBtn = null;
        for (Renderable renderable : ((ScreenAccessor) (Object) this).getRenderables()) {
            if (renderable instanceof Button button) {
                backBtn = button;
                break;
            }
        }

        if (backBtn != null) {
            int originalX = backBtn.getX();
            int originalY = backBtn.getY();
            int originalWidth = backBtn.getWidth();
            int originalHeight = backBtn.getHeight();

            Button reconnectBtn = Button.builder(Component.literal("Reconnect (5s)"), btn -> {
                BomboaddonsClient.autoReconnectTicks = -1;
                BomboaddonsClient.reconnect(this.parent, this.minecraft);
            })
            .bounds(originalX, originalY + originalHeight + 4, originalWidth, originalHeight)
            .build();

            addRenderableWidget(reconnectBtn);
            BomboaddonsClient.activeReconnectBtn = reconnectBtn;
            BomboaddonsClient.activeParent = this.parent;
            BomboaddonsClient.autoReconnectTicks = 100; // 5 seconds (20 ticks/sec)
        }
    }
}
