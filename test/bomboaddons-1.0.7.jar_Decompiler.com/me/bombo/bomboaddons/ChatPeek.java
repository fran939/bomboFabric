package me.bombo.bomboaddons;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;
import net.minecraft.class_3675;

@Environment(EnvType.CLIENT)
public class ChatPeek {
   public static void init() {
   }

   public static boolean isPeeking() {
      int keyCode = ClickLogic.getKeyCode(BomboConfig.get().chatPeekKey);
      return keyCode == -1 ? false : class_3675.method_15987(class_310.method_1551().method_22683(), keyCode);
   }
}
