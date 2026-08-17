package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.gui.AccountSwitcherWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TitleScreen.class})
public abstract class TitleScreenMixin extends Screen {
   protected TitleScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("RETURN")}
   )
   private void onInit(CallbackInfo ci) {
      Button multiplayerBtn = null;

      for(Renderable renderable : ((ScreenAccessor)this).getRenderables()) {
         if (renderable instanceof Button button) {
            Component msg = button.getMessage();
            if (msg != null) {
               ComponentContents var8 = msg.getContents();
               if (var8 instanceof TranslatableContents) {
                  TranslatableContents trans = (TranslatableContents)var8;
                  if ("menu.multiplayer".equals(trans.getKey())) {
                     multiplayerBtn = button;
                     break;
                  }
               }
            }
         }
      }

      if (multiplayerBtn != null && BomboConfig.get().hypixelShortcutButton) {
         int mx = multiplayerBtn.getX();
         int my = multiplayerBtn.getY();
         int mw = multiplayerBtn.getWidth();
         int bx = mx + mw + 4;
         Button hypixelBtn = Button.builder(Component.literal("H"), (btn) -> {
            ServerAddress address = ServerAddress.parseString("hypixel.net");
            ServerData server = new ServerData("Hypixel", "hypixel.net", Type.OTHER);
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(this), this.minecraft, address, server, false, (TransferState)null);
         }).bounds(bx, my, 20, 20).build();
         this.addRenderableWidget(hypixelBtn);
      }

      AccountSwitcherWidget accountSwitcher = new AccountSwitcherWidget(5, 5, this);
      this.addRenderableWidget(accountSwitcher);
   }
}
