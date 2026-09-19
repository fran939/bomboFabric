package me.bombo.bomboaddons;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class ChatPeek {
   public static void init() {
   }

   public static boolean isPeeking() {
      int keyCode = ClickLogic.getKeyCode(BomboConfig.get().chatPeekKey);
      return keyCode == -1 ? false : InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode);
   }
}
