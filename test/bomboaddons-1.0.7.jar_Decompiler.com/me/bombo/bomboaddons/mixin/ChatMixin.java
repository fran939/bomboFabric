package me.bombo.bomboaddons.mixin;

import java.util.List;
import java.util.function.Predicate;
import me.bombo.bomboaddons.SphinxMacro;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2561;
import net.minecraft.class_303;
import net.minecraft.class_338;
import net.minecraft.class_7469;
import net.minecraft.class_7591;
import net.minecraft.class_303.class_7590;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({class_338.class})
public abstract class ChatMixin {
   @Shadow
   @Final
   private List<class_303> field_2061;
   @Shadow
   @Final
   private List<class_7590> field_2064;

   @Shadow
   protected abstract void method_44813();

   @Inject(
      method = {"method_44811"},
      at = {@At("HEAD")}
   )
   private void onAddMessage(class_2561 message, class_7469 signature, class_7591 tag, CallbackInfo ci) {
      if (message != null) {
         SphinxMacro.onChatMessage(message.getString());
      }

   }

   @Unique
   public void bombo$removeMessages(Predicate<class_303> predicate) {
      boolean removed = this.field_2061.removeIf(predicate);
      if (removed) {
         this.field_2064.clear();
         this.method_44813();
      }

   }
}
