package me.bombo.bomboaddons_final;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;

@Environment(EnvType.CLIENT)
public class ChatPeek {
   public static void init() {
   }

   public static boolean isPeeking() {
      int keyCode = ClickLogic.getKeyCode(BomboConfig.get().chatPeekKey);
      return keyCode == -1 ? false : InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode);
   }
}
