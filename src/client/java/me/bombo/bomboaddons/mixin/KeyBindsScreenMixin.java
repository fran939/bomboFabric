package me.bombo.bomboaddons.mixin;

import java.lang.reflect.Field;
import me.bombo.bomboaddons.IBomboKeyBindsList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Screen.class})
public abstract class KeyBindsScreenMixin {
   @Unique
   private EditBox bombo$searchBox;

   @Shadow
   protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T var1);

   @Inject(
      method = {"init"},
      at = {@At("RETURN")}
   )
   private void onInit(CallbackInfo ci) {
      if (((Object)this) instanceof KeyBindsScreen kbs) {
         int boxWidth = 200;
         int x = kbs.width / 2 - boxWidth / 2;
         int y = 22;
         this.bombo$searchBox = new EditBox(Minecraft.getInstance().font, x, y, boxWidth, 18, Component.literal("Search..."));
         this.bombo$searchBox.setResponder((query) -> {
            try {
               Field listField = null;

               for(Field f : KeyBindsScreen.class.getDeclaredFields()) {
                  if (KeyBindsList.class.isAssignableFrom(f.getType())) {
                     listField = f;
                     break;
                  }
               }

               if (listField != null) {
                  listField.setAccessible(true);
                  Object list = listField.get(kbs);
                  if (list instanceof IBomboKeyBindsList) {
                     ((IBomboKeyBindsList)list).bombo$filter(query);
                  }
               }
            } catch (Exception e) {
               e.printStackTrace();
            }

         });
         this.addRenderableWidget(this.bombo$searchBox);
      }

   }
}
