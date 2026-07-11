package me.bombo.bomboaddons.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@Mixin(EditBox.class)
public abstract class EditBoxMixin {
    @Shadow public abstract String getValue();
    @Shadow public abstract void setValue(String string);
    @Shadow public abstract int getCursorPosition();
    @Shadow public abstract void setCursorPosition(int i);



    @Shadow public abstract void insertText(String string);

    private boolean insertingCopied = false;

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void onRenderWidgetHead(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if ((Object)this == me.bombo.bomboaddons.ItemListOverlay.searchBox) {
            float scale = me.bombo.bomboaddons.BomboConfig.get().itemListSearchScale;
            if (scale != 1.0f) {
                graphics.pose().pushMatrix();
                int tx = ((net.minecraft.client.gui.components.AbstractWidget)(Object)this).getX();
                int ty = ((net.minecraft.client.gui.components.AbstractWidget)(Object)this).getY();
                graphics.pose().translate((float)tx, (float)ty);
                graphics.pose().scale(scale, scale);
                graphics.pose().translate((float)-tx, (float)-ty);
            }
        }
    }

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    private void onRenderWidgetReturn(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if ((Object)this == me.bombo.bomboaddons.ItemListOverlay.searchBox) {
            float scale = me.bombo.bomboaddons.BomboConfig.get().itemListSearchScale;
            if (scale != 1.0f) {
                graphics.pose().popMatrix();
            }
        }
    }

    @Inject(method = "insertText", at = @At("HEAD"), cancellable = true)
    private void onInsertText(String text, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (insertingCopied) return;

        if (text != null && me.bombo.bomboaddons.BomboConfig.get().ignoreCapsLock) {
            if (text.length() == 1) {
                boolean shift = Minecraft.getInstance().hasShiftDown();
                if (!shift && !text.equals(text.toLowerCase())) {
                    ci.cancel();
                    insertingCopied = true;
                    try {
                        this.insertText(text.toLowerCase().replace('§', '&'));
                    } finally {
                        insertingCopied = false;
                    }
                    return;
                }
            }
        }

        if (text != null && text.contains("§")) {
            ci.cancel();
            insertingCopied = true;
            try {
                this.insertText(text.replace('§', '&'));
            } finally {
                insertingCopied = false;
            }
        }
    }



    @Inject(method = "setValue", at = @At("HEAD"))
    private void onSetValue(String value, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (value != null) {
            String lower = value.toLowerCase();
            if (lower.startsWith("/lb") || lower.startsWith("/lfc")) {
                if (Minecraft.getInstance().screen instanceof ChatScreen) {
                    me.bombo.bomboaddons.LF.preFetchSelf();
                }
            }
        }
    }
}
