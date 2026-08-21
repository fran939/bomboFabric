package me.bombo.bomboaddons.util;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

public class ChatSearchHelper {
   public static String activeSearchQuery = "";

   public static boolean isChatSearchActive() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof ChatScreen) {
         return activeSearchQuery != null && !activeSearchQuery.isEmpty() && BomboConfig.get().chatSearchBar;
      }
      return false;
   }
}
