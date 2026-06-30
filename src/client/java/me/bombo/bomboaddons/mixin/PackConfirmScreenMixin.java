package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConfirmScreen.class)
public abstract class PackConfirmScreenMixin extends Screen {

    protected PackConfirmScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // Only run if we are inside the PackConfirmScreen inner class
        if (this.getClass().getName().contains("PackConfirmScreen")) {
            BomboConfig.Settings s = BomboConfig.get();
            if (s.bypassResourcePack) {
                this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                    Component.literal("§lNuh uh"),
                    btn -> {
                        try {
                            // Extract synthetic outer listener field 'this$0'
                            java.lang.reflect.Field outerField = this.getClass().getDeclaredField("this$0");
                            outerField.setAccessible(true);
                            Object outerListener = outerField.get(this);

                            // Extract network connection field 'connection' from outerListener
                            java.lang.reflect.Field connField = outerListener.getClass().getDeclaredField("connection");
                            connField.setAccessible(true);
                            net.minecraft.network.Connection connection = (net.minecraft.network.Connection) connField.get(outerListener);

                            // Extract 'requests' list from this screen
                            java.lang.reflect.Field reqField = this.getClass().getDeclaredField("requests");
                            reqField.setAccessible(true);
                            java.util.List<?> requestsList = (java.util.List<?>) reqField.get(this);

                            // Extract 'parentScreen' field to go back to
                            java.lang.reflect.Field parentField = this.getClass().getDeclaredField("parentScreen");
                            parentField.setAccessible(true);
                            Screen parent = (Screen) parentField.get(this);

                            // Send ACCEPTED and SUCCESSFULLY_LOADED packets for each pack request
                            for (Object req : requestsList) {
                                java.util.UUID uuid = (java.util.UUID) req.getClass().getMethod("id").invoke(req);
                                connection.send(new net.minecraft.network.protocol.common.ServerboundResourcePackPacket(
                                    uuid, net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.ACCEPTED
                                ));
                                connection.send(new net.minecraft.network.protocol.common.ServerboundResourcePackPacket(
                                    uuid, net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED
                                ));
                            }

                            // Close screen and return to parent
                            this.minecraft.setScreenAndShow(parent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                ).bounds(this.width / 2 - 75, this.height - 35, 150, 20).build());
            }
        }
    }
}
