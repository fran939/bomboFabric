package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.gui.CustomSoundsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Screen.class})
public abstract class SoundOptionsScreenMixin {
   @Shadow
   public int width;
   @Shadow
   public int height;

   @Shadow
   protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T var1);

   @Inject(
      method = {"init()V"},
      at = {@At("RETURN")}
   )
   private void onInit(CallbackInfo ci) {
      if (((Object)this) instanceof SoundOptionsScreen) {
         this.addRenderableWidget(Button.builder(Component.literal("BomboAddons Sounds"), (btn) -> Minecraft.getInstance().setScreen(new CustomSoundsScreen((Screen)(Object)this))).bounds(this.width / 2 - 155, this.height / 6 + 120, 150, 20).build());
      }

   }
}
