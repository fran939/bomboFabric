package me.bombo.bomboaddons.util;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.Bomboaddons;
import net.minecraft.client.Minecraft;

public class ResourcePackHelper {
   public static void enableSkyblockPack() {
      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> {
         try {
            var repo = mc.getResourcePackRepository();
            repo.reload();
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            boolean added = false;
            for (String id : repo.getAvailableIds()) {
               if (id.toLowerCase().contains("hypixel_skyblock") || id.toLowerCase().contains("skyblock")) {
                  if (!selected.contains(id)) {
                     selected.add(id);
                     added = true;
                  }
               }
            }
            if (added) {
               repo.setSelected(selected);
               mc.options.updateResourcePacks(repo);
               mc.reloadResourcePacks();
               Bomboaddons.sendMessage("§a[Bombo] Hypixel Skyblock resource pack automatically enabled!");
            }
         } catch (Throwable t) {
            t.printStackTrace();
         }
      });
   }
}
