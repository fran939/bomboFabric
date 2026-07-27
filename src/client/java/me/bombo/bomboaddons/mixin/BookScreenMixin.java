package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.DailyRewardHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BookViewScreen.class)
public class BookScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if (!BomboConfig.get().dailyRewardHelper) return;

        try {
            BookViewScreen screen = (BookViewScreen) (Object) this;
            String key = null;

            if (BomboConfig.get().debugDailyReward && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bDailyRewardDebug§8] §7BookViewScreen opened: " + (screen.getTitle() != null ? screen.getTitle().getString() : "null")));
            }

            // 1. Check title
            if (screen.getTitle() != null) {
                key = DailyRewardHelper.extractRewardKey(screen.getTitle().getString());
            }

            // 2. Scan screen fields recursively for reward URL or key
            if (key == null) {
                key = scanObjectForRewardKey(screen, 0);
            }

            if (key != null) {
                ci.cancel();
                final String finalKey = key;
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().setScreen(null);
                    DailyRewardHelper.fetchAndOpenRewardPage(finalKey);
                });
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static String scanObjectForRewardKey(Object obj, int depth) {
        if (obj == null || depth > 3) return null;
        try {
            if (obj instanceof Component comp) {
                String url = findRewardUrl(comp);
                if (url != null) return DailyRewardHelper.extractRewardKey(url);
            }
            if (obj instanceof String str) {
                if (str.contains("rewards.hypixel.net")) {
                    return DailyRewardHelper.extractRewardKey(str);
                }
            }
            if (obj instanceof BookViewScreen.BookAccess bookAccess) {
                for (int i = 0; i < bookAccess.getPageCount(); i++) {
                    net.minecraft.network.chat.FormattedText page = bookAccess.getPage(i);
                    String k = scanObjectForRewardKey(page, depth + 1);
                    if (k != null) return k;
                }
            }
            for (java.lang.reflect.Field field : obj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(obj);
                if (val != null) {
                    if (val instanceof String || val instanceof Component || val instanceof BookViewScreen.BookAccess) {
                        String k = scanObjectForRewardKey(val, depth + 1);
                        if (k != null) return k;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String findRewardUrl(Component component) {
        if (component == null) return null;
        if (component.getStyle() != null && component.getStyle().getClickEvent() != null) {
            net.minecraft.network.chat.ClickEvent event = component.getStyle().getClickEvent();
            String val = extractClickEventValue(event);
            if (val != null && val.contains("rewards.hypixel.net")) {
                return val;
            }
        }
        String raw = component.getString();
        if (raw.contains("rewards.hypixel.net")) {
            return raw;
        }
        for (Component sibling : component.getSiblings()) {
            String found = findRewardUrl(sibling);
            if (found != null) return found;
        }
        return null;
    }

    private static String extractClickEventValue(net.minecraft.network.chat.ClickEvent clickEvent) {
        if (clickEvent == null) return null;
        try {
            for (java.lang.reflect.Field f : clickEvent.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(clickEvent);
                if (v != null) {
                    String str = v.toString();
                    if (str.contains("rewards.hypixel.net")) return str;
                }
            }
        } catch (Throwable t) {}
        return clickEvent.toString();
    }
}
