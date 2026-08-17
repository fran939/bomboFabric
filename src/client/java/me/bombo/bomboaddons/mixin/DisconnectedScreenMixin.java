package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.DebugUtils;
import me.bombo.bomboaddons.PlaytimeTracker;
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

@Mixin({DisconnectedScreen.class})
public abstract class DisconnectedScreenMixin extends Screen {
   @Shadow
   @Final
   private Screen parent;

   protected DisconnectedScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("RETURN")}
   )
   private void onInit(CallbackInfo ci) {
      try {
         PlaytimeTracker.sendPlaytimeDataToCloud(true);
      } catch (Throwable var14) {
      }

      Button targetBtn = null;
      Button backBtn = null;

      for(Renderable renderable : ((ScreenAccessor)this).getRenderables()) {
         if (renderable instanceof Button button) {
            String msg = button.getMessage().getString();
            if (!msg.contains("Report") && !msg.contains("Open") && !msg.contains("Log")) {
               if (msg.contains("Back") || msg.contains("Menu")) {
                  backBtn = button;
               }
            } else {
               targetBtn = button;
            }
         }
      }

      Button referenceBtn = targetBtn != null ? targetBtn : backBtn;
      if (referenceBtn != null) {
         int originalX = referenceBtn.getX();
         int originalY = referenceBtn.getY();
         int originalWidth = referenceBtn.getWidth();
         int originalHeight = referenceBtn.getHeight();
         if (targetBtn != null) {
            targetBtn.visible = false;
            targetBtn.active = false;
         }

         int targetY = this.height - 35;
         backBtn.setY(targetY);
         boolean auto = BomboConfig.get().autoReconnect;
         boolean delayed = BomboaddonsClient.tempDisableReconnect;
         if (BomboConfig.get().reconnectButton) {
            int reconnectY = targetY - originalHeight - 4;
            Button reconnectBtn = Button.builder(Component.literal(auto ? (delayed ? "Reconnect (300s)" : "Reconnect (5s)") : "Reconnect"), (btn) -> {
               BomboaddonsClient.autoReconnectTicks = -1;
               if (BomboConfig.get().debugReconnect) {
                  DebugUtils.debug("reconnect", "Manual Reconnect button clicked.");
               }

               BomboaddonsClient.reconnect(this.parent, this.minecraft);
            }).bounds(originalX, reconnectY, originalWidth, originalHeight).build();
            this.addRenderableWidget(reconnectBtn);
            BomboaddonsClient.activeReconnectBtn = reconnectBtn;
         } else {
            BomboaddonsClient.activeReconnectBtn = null;
         }

         BomboaddonsClient.activeParent = this.parent;
         if (auto) {
            BomboaddonsClient.autoReconnectTicks = delayed ? 6000 : 100;
            BomboaddonsClient.tempDisableReconnect = false;
         } else {
            BomboaddonsClient.autoReconnectTicks = -1;
         }

         if (BomboConfig.get().debugReconnect) {
            String serverIp = BomboaddonsClient.lastServerData != null ? BomboaddonsClient.lastServerData.ip : "null (fallback to hypixel.net)";
            DebugUtils.debug("reconnect", "DisconnectedScreen opened. Auto: " + auto + ", Server: " + serverIp + ", TargetTicks: " + BomboaddonsClient.autoReconnectTicks);
         }
      }

   }
}
