package me.bombo.bomboaddons.mixin;

import java.util.Objects;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PauseScreen.class})
public abstract class PauseScreenMixin extends Screen {
   private boolean confirmingDisconnect = false;

   protected PauseScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("RETURN")}
   )
   private void onInit(CallbackInfo ci) {
      if (this.minecraft != null && !this.minecraft.isLocalServer()) {
         Button serverLinksBtn = null;
         Button optionsBtn = null;

         for(Renderable renderable : ((ScreenAccessor)this).getRenderables()) {
            if (renderable instanceof Button) {
               Button button = (Button)renderable;
               if (this.isServerLinksButton(button)) {
                  serverLinksBtn = button;
               } else if (this.isOptionsButton(button)) {
                  ;
               }
            }
         }

         if (serverLinksBtn != null && BomboConfig.get().serverListButton) {
            int originalX = serverLinksBtn.getX();
            int originalY = serverLinksBtn.getY();
            int originalW = serverLinksBtn.getWidth();
            int originalH = serverLinksBtn.getHeight();
            Button newButton = Button.builder(Component.literal("Server List"), (btn) -> this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(this))).bounds(originalX, originalY, originalW, originalH).build();
            this.removeWidget(serverLinksBtn);
            this.addRenderableWidget(newButton);
         }

         Button disconnectBtn = null;

         for(Renderable renderable : ((ScreenAccessor)this).getRenderables()) {
            if (renderable instanceof Button) {
               Button button = (Button)renderable;
               if (this.isDisconnectButton(button)) {
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
            final Button finalDisconnectBtn = disconnectBtn;
            Button newDisconnectBtn = Button.builder(Component.translatable("menu.disconnect"), (btn) -> {
               if (!this.confirmingDisconnect) {
                  this.confirmingDisconnect = true;
                  btn.setMessage(Component.literal("§cConfirm Disconnect?"));
               } else {
                  finalDisconnectBtn.onPress(new InputWithModifiers() {
                     {
                        Objects.requireNonNull(PauseScreenMixin.this);
                     }

                     public int modifiers() {
                        return 0;
                     }

                     public int input() {
                        return 0;
                     }
                  });
               }

            }).bounds(originalX, originalY, originalW, originalH).build();
            this.removeWidget(disconnectBtn);
            this.addRenderableWidget(newDisconnectBtn);
         }

      }
   }

   private boolean isServerLinksButton(Button button) {
      Component message = button.getMessage();
      if (message != null) {
         ComponentContents var4 = message.getContents();
         if (var4 instanceof TranslatableContents) {
            TranslatableContents translatable = (TranslatableContents)var4;
            return "menu.server_links".equals(translatable.getKey());
         }
      }

      return false;
   }

   private boolean isOptionsButton(Button button) {
      Component message = button.getMessage();
      if (message != null) {
         ComponentContents var4 = message.getContents();
         if (var4 instanceof TranslatableContents) {
            TranslatableContents translatable = (TranslatableContents)var4;
            return "menu.options".equals(translatable.getKey());
         }
      }

      return false;
   }

   private boolean isDisconnectButton(Button button) {
      Component message = button.getMessage();
      if (message != null) {
         ComponentContents var4 = message.getContents();
         if (var4 instanceof TranslatableContents) {
            TranslatableContents translatable = (TranslatableContents)var4;
            return "menu.disconnect".equals(translatable.getKey());
         }
      }

      return false;
   }
}
