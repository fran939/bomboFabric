package me.bombo.bomboaddons_final;

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
}
