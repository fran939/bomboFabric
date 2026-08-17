package me.bombo.bomboaddons.mixin;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ConfirmScreen.class})
public abstract class PackConfirmScreenMixin extends Screen {
   protected PackConfirmScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo ci) {
      if (this.getClass().getName().contains("PackConfirmScreen")) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s.bypassResourcePack) {
            try {
               Field outerField = this.getClass().getDeclaredField("this$0");
               outerField.setAccessible(true);
               Object outerListener = outerField.get(this);
               Field connField = outerListener.getClass().getDeclaredField("connection");
               connField.setAccessible(true);
               Connection connection = (Connection)connField.get(outerListener);
               Field reqField = this.getClass().getDeclaredField("requests");
               reqField.setAccessible(true);

               for(Object req : (List)reqField.get(this)) {
                  UUID uuid = (UUID)req.getClass().getMethod("id").invoke(req);
                  connection.send(new ServerboundResourcePackPacket(uuid, Action.ACCEPTED));
                  connection.send(new ServerboundResourcePackPacket(uuid, Action.DOWNLOADED));
                  connection.send(new ServerboundResourcePackPacket(uuid, Action.SUCCESSFULLY_LOADED));
               }

               boolean hasPack = false;

               for(Pack pack : this.minecraft.getResourcePackRepository().getSelectedPacks()) {
                  String id = pack.getId().toLowerCase();
                  String title = pack.getTitle().getString().toLowerCase();
                  if (id.contains("hypixel") || id.contains("world_specific_resources") || title.contains("hypixel")) {
                     hasPack = true;
                     break;
                  }
               }

               if (!hasPack) {
                  me.bombo.bomboaddons.util.ResourcePackHelper.enableSkyblockPack();
               }

               Field parentField = this.getClass().getDeclaredField("parentScreen");
               parentField.setAccessible(true);
               Screen parent = (Screen)parentField.get(this);
               Minecraft.getInstance().execute(() -> this.minecraft.setScreen(parent));
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }

   }
}
