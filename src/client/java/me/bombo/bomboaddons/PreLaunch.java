package me.bombo.bomboaddons;

import me.bombo.bomboaddons.util.WindowsDesktopHelper;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class PreLaunch implements PreLaunchEntrypoint {
   @Override
   public void onPreLaunch() {
      WindowsDesktopHelper.ensureDefaultDesktop();
   }
}
