package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboaddonsClient;
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
        Button targetBtn = null;
        Button backBtn = null;
        
        for (Renderable renderable : ((ScreenAccessor) (Object) this).getRenderables()) {
            if (renderable instanceof Button button) {
                String msg = button.getMessage().getString();
                if (msg.contains("Report") || msg.contains("Open") || msg.contains("Log")) {
                    targetBtn = button;
                } else if (msg.contains("Back") || msg.contains("Menu")) {
                    backBtn = button;
                }
            }
        }

        // Default to backBtn if targetBtn wasn't found
        Button referenceBtn = targetBtn != null ? targetBtn : backBtn;

        if (referenceBtn != null) {
            int originalX = referenceBtn.getX();
            int originalY = referenceBtn.getY();
            int originalWidth = referenceBtn.getWidth();
            int originalHeight = referenceBtn.getHeight();
            
            // Hide the report button if we are replacing it
            if (targetBtn != null) {
                targetBtn.visible = false;
                targetBtn.active = false;
            }

            boolean auto = me.bombo.bomboaddons.BomboConfig.get().autoReconnect;
            boolean delayed = BomboaddonsClient.tempDisableReconnect;

            if (me.bombo.bomboaddons.BomboConfig.get().reconnectButton) {
                // If we didn't find the target button, we place it above the back button
                int newY = targetBtn != null ? originalY : originalY - originalHeight - 4;
                
                Button reconnectBtn = Button.builder(Component.literal(auto ? (delayed ? "Reconnect (300s)" : "Reconnect (5s)") : "Reconnect"), btn -> {
                    BomboaddonsClient.autoReconnectTicks = -1;
                    BomboaddonsClient.reconnect(this.parent, this.minecraft);
                })
                .bounds(originalX, newY, originalWidth, originalHeight)
                .build();

                addRenderableWidget(reconnectBtn);
                BomboaddonsClient.activeReconnectBtn = reconnectBtn;
            } else {
                BomboaddonsClient.activeReconnectBtn = null;
            }

            BomboaddonsClient.activeParent = this.parent;
            if (auto) {
                BomboaddonsClient.autoReconnectTicks = delayed ? 6000 : 100; // 5 minutes if delayed, otherwise 5 seconds
                BomboaddonsClient.tempDisableReconnect = false; // Reset the flag
            } else {
                BomboaddonsClient.autoReconnectTicks = -1;
            }
        }
    }
}
