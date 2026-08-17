package me.bombo.bomboaddons.mixin;

import java.lang.reflect.Field;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.DailyRewardHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BookViewScreen.class})
public class BookScreenMixin {
   @Inject(
      method = {"init"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInit(CallbackInfo ci) {
      if (BomboConfig.get().dailyRewardHelper) {
         try {
            BookViewScreen screen = (BookViewScreen)(Object)this;
            String key = null;
            if (BomboConfig.get().debugDailyReward && Minecraft.getInstance().player != null) {
               LocalPlayer var10000 = Minecraft.getInstance().player;
               String var10001 = screen.getTitle() != null ? screen.getTitle().getString() : "null";
               var10000.sendSystemMessage(Component.literal("§8[§bDailyRewardDebug§8] §7BookViewScreen opened: " + var10001));
            }

            if (screen.getTitle() != null) {
               key = DailyRewardHelper.extractRewardKey(screen.getTitle().getString());
            }

            if (key == null) {
               key = scanObjectForRewardKey(screen, 0);
            }

            if (key != null) {
               final String rewardKey = key;
               ci.cancel();
               Minecraft.getInstance().execute(() -> {
                  Minecraft.getInstance().setScreen((Screen)null);
                  DailyRewardHelper.fetchAndOpenRewardPage(rewardKey);
               });
            }
         } catch (Throwable t) {
            t.printStackTrace();
         }

      }
   }

   private static String scanObjectForRewardKey(Object obj, int depth) {
      if (obj != null && depth <= 3) {
         try {
            if (obj instanceof Component) {
               Component comp = (Component)obj;
               String url = findRewardUrl(comp);
               if (url != null) {
                  return DailyRewardHelper.extractRewardKey(url);
               }
            }

            if (obj instanceof String) {
               String str = (String)obj;
               if (str.contains("rewards.hypixel.net")) {
                  return DailyRewardHelper.extractRewardKey(str);
               }
            }

            if (obj instanceof BookViewScreen.BookAccess) {
               BookViewScreen.BookAccess bookAccess = (BookViewScreen.BookAccess)obj;

               for(int i = 0; i < bookAccess.getPageCount(); ++i) {
                  FormattedText page = bookAccess.getPage(i);
                  String k = scanObjectForRewardKey(page, depth + 1);
                  if (k != null) {
                     return k;
                  }
               }
            }

            for(Field field : obj.getClass().getDeclaredFields()) {
               field.setAccessible(true);
               Object val = field.get(obj);
               if (val != null && (val instanceof String || val instanceof Component || val instanceof BookViewScreen.BookAccess)) {
                  String k = scanObjectForRewardKey(val, depth + 1);
                  if (k != null) {
                     return k;
                  }
               }
            }
         } catch (Throwable var8) {
         }

         return null;
      } else {
         return null;
      }
   }

   private static String findRewardUrl(Component component) {
      if (component == null) {
         return null;
      } else {
         if (component.getStyle() != null && component.getStyle().getClickEvent() != null) {
            ClickEvent event = component.getStyle().getClickEvent();
            String val = extractClickEventValue(event);
            if (val != null && val.contains("rewards.hypixel.net")) {
               return val;
            }
         }

         String raw = component.getString();
         if (raw.contains("rewards.hypixel.net")) {
            return raw;
         } else {
            for(Component sibling : component.getSiblings()) {
               String found = findRewardUrl(sibling);
               if (found != null) {
                  return found;
               }
            }

            return null;
         }
      }
   }

   private static String extractClickEventValue(ClickEvent clickEvent) {
      if (clickEvent == null) {
         return null;
      } else {
         try {
            for(Field f : clickEvent.getClass().getDeclaredFields()) {
               f.setAccessible(true);
               Object v = f.get(clickEvent);
               if (v != null) {
                  String str = v.toString();
                  if (str.contains("rewards.hypixel.net")) {
                     return str;
                  }
               }
            }
         } catch (Throwable var7) {
         }

         return clickEvent.toString();
      }
   }
}
