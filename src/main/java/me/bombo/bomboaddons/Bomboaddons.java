package me.bombo.bomboaddons;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bomboaddons implements ModInitializer {
   public static final String MOD_ID = "bomboaddons";
   public static final Logger LOGGER = LoggerFactory.getLogger("bomboaddons");

   public void onInitialize() {
      BomboConfig.load();
      InventoryConfig.load();
   }

   public static java.util.function.Consumer<String> sendMessageConsumer = null;

   public static void sendMessage(String message) {
      if (sendMessageConsumer != null) {
         sendMessageConsumer.accept(message);
      }
   }

   public static void logApiRequest(String url) {
      if (BomboConfig.get() != null && BomboConfig.get().apiChatMessages) {
         sendMessage("§8[§bBomboAddons API§8] §7Requesting: §f" + url);
      }
   }
}
