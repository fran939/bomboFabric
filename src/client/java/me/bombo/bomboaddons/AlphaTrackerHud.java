package me.bombo.bomboaddons;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class AlphaTrackerHud {
   private static int players = 0;
   private static int maxPlayers = 0;
   private static boolean isOpen = false;
   private static long lastCheckTime = 0L;
   private static boolean checking = false;
   private static boolean initialized = false;
   private static boolean wasOpen = false;
   private static int prevMaxPlayers = 0;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "alpha_tracker_hud"), AlphaTrackerHud::render);
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         long now = System.currentTimeMillis();
         if (now - lastCheckTime > 30000L && !checking) {
            checkServerStatusAsync();
         }
      });
      checkServerStatusAsync();
   }

   public static void checkServerStatusAsync() {
      if (!checking) {
         checking = true;
         CompletableFuture.runAsync(() -> {
            HttpURLConnection conn = null;
            boolean detectedOpen = false;
            int detectedPlayers = 0;
            int detectedMax = 0;
            boolean querySuccess = false;

            try {
               URL url = new URL("https://api.bombo.dpdns.org/mc/alpha.hypixel.net");
               conn = (HttpURLConnection)url.openConnection();
               conn.setRequestMethod("GET");
               conn.setConnectTimeout(5000);
               conn.setReadTimeout(5000);
               conn.setRequestProperty("User-Agent", "BomboAddons/1.0");
               if (conn.getResponseCode() == 200) {
                  InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                  JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                  reader.close();
                  boolean online = json.has("online") && json.get("online").getAsBoolean();
                  if (json.has("players") && json.get("players").isJsonObject()) {
                     JsonObject playersObj = json.getAsJsonObject("players");
                     detectedPlayers = playersObj.has("online") ? playersObj.get("online").getAsInt() : 0;
                     detectedMax = playersObj.has("max") ? playersObj.get("max").getAsInt() : 0;
                     detectedOpen = online;
                  } else if (json.has("open")) {
                     detectedOpen = json.get("open").getAsBoolean();
                     detectedPlayers = json.has("players") ? json.get("players").getAsInt() : 0;
                     detectedMax = json.has("max") ? json.get("max").getAsInt() : 0;
                  } else {
                     detectedOpen = online;
                     detectedPlayers = 0;
                     detectedMax = 0;
                  }

                  querySuccess = true;
               }
            } catch (Exception ignored) {
            } finally {
               if (conn != null) {
                  conn.disconnect();
               }
            }

            if (!querySuccess) {
               try {
                  URL fallbackUrl = new URL("https://bombo.dpdns.org/alpha");
                  conn = (HttpURLConnection)fallbackUrl.openConnection();
                  conn.setRequestMethod("GET");
                  conn.setConnectTimeout(5000);
                  conn.setReadTimeout(5000);
                  conn.setRequestProperty("User-Agent", "BomboAddons/1.0");
                  if (conn.getResponseCode() == 200) {
                     InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                     JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                     reader.close();
                     detectedOpen = json.has("open") && json.get("open").getAsBoolean();
                     detectedPlayers = json.has("players") ? json.get("players").getAsInt() : 0;
                     detectedMax = json.has("max") ? json.get("max").getAsInt() : 0;
                     querySuccess = true;
                  }
               } catch (Exception ignored) {
               } finally {
                  if (conn != null) {
                     conn.disconnect();
                  }
               }
            }

            lastCheckTime = System.currentTimeMillis();
            checking = false;

            if (querySuccess) {
               handleStatusUpdate(detectedOpen, detectedPlayers, detectedMax);
            }
         });
      }
   }

   private static void handleStatusUpdate(boolean newOpen, int newPlayers, int newMax) {
      boolean prevOpenState = wasOpen;
      int prevMax = prevMaxPlayers;
      isOpen = newOpen;
      players = newPlayers;
      maxPlayers = newMax;
      wasOpen = newOpen;
      prevMaxPlayers = newMax;

      if (!initialized) {
         initialized = true;
         return;
      }

      boolean newlyOpened = isOpen && (!prevOpenState || prevMax == 0) && maxPlayers > 0;
      boolean capacityIncreased = isOpen && prevOpenState && maxPlayers > prevMax && prevMax > 0;

      if (newlyOpened || capacityIncreased) {
         Minecraft mc = Minecraft.getInstance();
         mc.execute(() -> {
            if (mc.player == null) return;
            BomboConfig.Settings s = BomboConfig.get();
            if (!s.alphaOpenAlert) return;

            if (newlyOpened) {
               MutableComponent msg = Component.literal("§8[§3Bombo§8] §a§l🚨 HYPIXEL ALPHA IS OPEN! §e(Cap: §b" + maxPlayers + "§e, Online: §b" + players + "§e) ")
                  .append(Component.literal("§8[§a§lJOIN§8]")
                     .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/server alpha"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run §b/server alpha")))));
               mc.player.sendSystemMessage(msg);

               if (s.alphaOpenAlertTitle && mc.gui != null && mc.gui.hud != null) {
                  mc.gui.hud.setTitle(Component.literal("§a§lALPHA IS OPEN!"));
                  mc.gui.hud.setSubtitle(Component.literal("§eCap: §b" + maxPlayers + " §8| §eOnline: §b" + players + " §7(alpha.hypixel.net)"));
               }

               if (s.alphaOpenAlertSound) {
                  try {
                     mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
                  } catch (Throwable ignored) {}
               }
            } else if (capacityIncreased) {
               MutableComponent msg = Component.literal("§8[§3Bombo§8] §e§l📈 ALPHA CAPACITY INCREASED! §7(" + prevMax + " ➔ §a§l" + maxPlayers + "§7, Online: §b" + players + "§7) ")
                  .append(Component.literal("§8[§a§lJOIN§8]")
                     .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/server alpha"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run §b/server alpha")))));
               mc.player.sendSystemMessage(msg);

               if (s.alphaOpenAlertSound) {
                  try {
                     mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5F));
                  } catch (Throwable ignored) {}
               }
            }
         });
      }
   }

   public static void sendCurrentStatusToChat() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      if (isOpen) {
         MutableComponent msg = Component.literal("§8[§3Bombo§8] §bAlpha Status: §a§lOPEN §7(" + players + "/" + maxPlayers + ") ")
            .append(Component.literal("§8[§a§lJOIN§8]")
               .withStyle(style -> style
                  .withColor(ChatFormatting.GREEN)
                  .withUnderlined(true)
                  .withClickEvent(new ClickEvent.RunCommand("/server alpha"))
                  .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run §b/server alpha")))));
         mc.player.sendSystemMessage(msg);
      } else {
         mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §bAlpha Status: §c§lCLOSED §7(" + players + "/0)"));
      }
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("HUD: Alpha Tracker")) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s.alphaTrackerHud) {
            Minecraft mc = Minecraft.getInstance();
            if (!s.alphaTrackerHideWhenClosed && !s.alphaTrackerOnlyWhenOpen || isOpen || mc.gui.screen() instanceof HudMoveScreen) {
               if (!mc.options.keyToggleGui.isDown()) {
                  if (mc.gui.screen() == null || mc.gui.screen() instanceof HudMoveScreen || mc.gui.screen() instanceof ChatScreen || mc.gui.screen() instanceof AbstractContainerScreen) {
                     long now = System.currentTimeMillis();
                     if (now - lastCheckTime > 60000L && !checking) {
                        checkServerStatusAsync();
                     }

                     Font font = mc.font;
                     float scale = s.alphaTrackerHudScale;
                     int baseX = s.alphaTrackerHudX;
                     int baseY = s.alphaTrackerHudY;
                     g.pose().pushMatrix();
                     g.pose().translate((float)baseX, (float)baseY);
                     g.pose().scale(scale, scale);
                     drawAlphaInfo(g, font, 0, 0);
                     g.pose().popMatrix();
                  }
               }
            }
         }
      }
   }

   public static void drawAlphaInfo(GuiGraphicsExtractor g, Font font, int x, int y) {
      BomboConfig.Settings s = BomboConfig.get();
      String statusText;
      if (isOpen) {
         statusText = "§a§lOPEN";
         if (s.alphaTrackerShowPlayers) {
            statusText = statusText + " §7(" + players + "/" + maxPlayers + ")";
         }
      } else {
         statusText = "§c§lCLOSED";
         if (s.alphaTrackerShowPlayers) {
            statusText = statusText + " §7(" + players + "/0)";
         }
      }

      g.text(font, "§bAlpha: " + statusText, x, y, -1, true);
   }

   public static int getHudWidth() {
      return 140;
   }

   public static int getHudHeight() {
      return 12;
   }

   public static boolean isOpen() {
      return isOpen;
   }

   public static int getPlayers() {
      return players;
   }

   public static int getMaxPlayers() {
      return maxPlayers;
   }

   public static boolean isHoppityActive() {
      long now = System.currentTimeMillis() / 1000L;
      long skyblockEpoch = 1560275700L;
      long yearLength = 446400L;
      long seasonLength = 111600L;
      long elapsed = now - skyblockEpoch;
      long yearCycleTime = (elapsed % yearLength + yearLength) % yearLength;
      return yearCycleTime < seasonLength;
   }
}
