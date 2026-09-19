package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.DebugUtils;
import me.bombo.bomboaddons.PlaytimeTracker;
import me.bombo.bomboaddons.auth.AccountManager;
import me.bombo.bomboaddons.auth.AccountManager.Account;
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

      // Check for Invalid session error in title, screen contents, or reason
      boolean isInvalidSession = false;
      try {
         String titleStr = this.getTitle() != null ? this.getTitle().getString().toLowerCase(java.util.Locale.ROOT) : "";
         if (titleStr.contains("invalid session")) {
            isInvalidSession = true;
         }
      } catch (Throwable ignored) {}

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
         try {
            if (renderable instanceof net.minecraft.client.gui.components.MultiLineTextWidget textWidget) {
               String text = textWidget.getMessage() != null ? textWidget.getMessage().getString().toLowerCase(java.util.Locale.ROOT) : "";
               if (text.contains("invalid session")) {
                  isInvalidSession = true;
               }
            }
         } catch (Throwable ignored) {}
      }

      // Fallback: check screen components / narration for invalid session
      if (!isInvalidSession) {
         try {
            for (Component c : this.children().stream().filter(e -> e instanceof Renderable).map(Object::toString).map(Component::literal).toList()) {
               if (c.getString().toLowerCase(java.util.Locale.ROOT).contains("invalid session")) {
                  isInvalidSession = true;
                  break;
               }
            }
         } catch (Throwable ignored) {}
      }

      final boolean finalInvalidSession = isInvalidSession;

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
         boolean auto = BomboConfig.get().autoReconnect || finalInvalidSession;
         boolean delayed = BomboaddonsClient.tempDisableReconnect;
         if (BomboConfig.get().reconnectButton || finalInvalidSession) {
            int reconnectY = targetY - originalHeight - 4;
            Button reconnectBtn = Button.builder(Component.literal(auto ? (finalInvalidSession ? "Refreshing Account..." : (delayed ? "Reconnect (300s)" : "Reconnect (5s)")) : "Reconnect"), (btn) -> {
               BomboaddonsClient.autoReconnectTicks = -1;
               if (BomboConfig.get().debugReconnect) {
                  DebugUtils.debug("reconnect", "Manual Reconnect button clicked.");
               }

               if (finalInvalidSession) {
                  triggerAccountRefreshAndReconnect();
               } else {
                  BomboaddonsClient.reconnect(this.parent, this.minecraft);
               }
            }).bounds(originalX, reconnectY, originalWidth, originalHeight).build();
            this.addRenderableWidget(reconnectBtn);
            BomboaddonsClient.activeReconnectBtn = reconnectBtn;
         } else {
            BomboaddonsClient.activeReconnectBtn = null;
         }

         BomboaddonsClient.activeParent = this.parent;
         if (finalInvalidSession) {
            System.out.println("[BomboAddons] 'Invalid session' disconnect detected. Auto refreshing account and reconnecting...");
            triggerAccountRefreshAndReconnect();
         } else if (auto) {
            BomboaddonsClient.autoReconnectTicks = delayed ? 6000 : 100;
            BomboaddonsClient.tempDisableReconnect = false;
         } else {
            BomboaddonsClient.autoReconnectTicks = -1;
         }

         if (BomboConfig.get().debugReconnect) {
            String serverIp = BomboaddonsClient.lastServerData != null ? BomboaddonsClient.lastServerData.ip : "null (fallback to hypixel.net)";
            DebugUtils.debug("reconnect", "DisconnectedScreen opened. Auto: " + auto + ", InvalidSession: " + finalInvalidSession + ", Server: " + serverIp + ", TargetTicks: " + BomboaddonsClient.autoReconnectTicks);
         }
      }
   }

   private void triggerAccountRefreshAndReconnect() {
      Account acc = AccountManager.currentAccount;
      if (acc == null && !AccountManager.accounts.isEmpty()) {
         acc = AccountManager.accounts.get(0);
      }
      if (acc != null && acc.refreshToken != null && !acc.refreshToken.isEmpty()) {
         if (BomboaddonsClient.activeReconnectBtn != null) {
            BomboaddonsClient.activeReconnectBtn.setMessage(Component.literal("Refreshing Account..."));
            BomboaddonsClient.activeReconnectBtn.active = false;
         }
         final Account targetAcc = acc;
         AccountManager.refreshAccount(targetAcc).thenAccept((refreshed) -> {
            if (refreshed != null) {
               if (this.minecraft != null) {
                  this.minecraft.execute(() -> {
                     AccountManager.setSession(refreshed);
                     System.out.println("[BomboAddons] Successfully refreshed session for " + refreshed.username + "! Reconnecting...");
                     BomboaddonsClient.reconnect(this.parent, this.minecraft);
                  });
               }
            } else {
               System.err.println("[BomboAddons] Failed to refresh account token.");
               if (this.minecraft != null) {
                  this.minecraft.execute(() -> {
                     if (BomboaddonsClient.activeReconnectBtn != null) {
                        BomboaddonsClient.activeReconnectBtn.setMessage(Component.literal("Refresh Failed - Reconnect"));
                        BomboaddonsClient.activeReconnectBtn.active = true;
                     }
                  });
               }
            }
         }).exceptionally(ex -> {
            ex.printStackTrace();
            if (this.minecraft != null) {
               this.minecraft.execute(() -> {
                  if (BomboaddonsClient.activeReconnectBtn != null) {
                     BomboaddonsClient.activeReconnectBtn.setMessage(Component.literal("Refresh Failed - Reconnect"));
                     BomboaddonsClient.activeReconnectBtn.active = true;
                  }
               });
            }
            return null;
         });
      } else {
         System.out.println("[BomboAddons] No refresh token available for account, standard reconnecting in 3s...");
         BomboaddonsClient.autoReconnectTicks = 60;
      }
   }
}
