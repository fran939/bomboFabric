package me.bombo.bomboaddons;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

public class AlphaTrackerHud {
   private static int players = 0;
   private static int maxPlayers = 0;
   private static boolean isOpen = false;
   private static long lastCheckTime = 0L;
   private static boolean checking = false;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "alpha_tracker_hud"), AlphaTrackerHud::render);
      checkServerStatusAsync();
   }

   public static void checkServerStatusAsync() {
      if (!checking) {
         checking = true;
         CompletableFuture.runAsync(() -> {
            HttpURLConnection conn = null;

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
                     players = playersObj.has("online") ? playersObj.get("online").getAsInt() : 0;
                     maxPlayers = playersObj.has("max") ? playersObj.get("max").getAsInt() : 0;
                     isOpen = online;
                  } else if (json.has("open")) {
                     isOpen = json.get("open").getAsBoolean();
                     players = json.has("players") ? json.get("players").getAsInt() : 0;
                     maxPlayers = json.has("max") ? json.get("max").getAsInt() : 0;
                  } else {
                     isOpen = online;
                     players = 0;
                     maxPlayers = 0;
                  }

                  lastCheckTime = System.currentTimeMillis();
                  checking = false;
                  return;
               }
            } catch (Exception var18) {
            } finally {
               if (conn != null) {
                  conn.disconnect();
               }

            }

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
                  isOpen = json.has("open") && json.get("open").getAsBoolean();
                  players = json.has("players") ? json.get("players").getAsInt() : 0;
                  maxPlayers = json.has("max") ? json.get("max").getAsInt() : 0;
               } else {
                  isOpen = false;
                  players = 0;
                  maxPlayers = 0;
               }
            } catch (Exception var16) {
               isOpen = false;
               players = 0;
               maxPlayers = 0;
            } finally {
               if (conn != null) {
                  conn.disconnect();
               }

               lastCheckTime = System.currentTimeMillis();
               checking = false;
            }

         });
      }
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.alphaTrackerHud) {
         Minecraft mc = Minecraft.getInstance();
         if (!s.alphaTrackerHideWhenClosed && !s.alphaTrackerOnlyWhenOpen || isOpen || mc.screen instanceof HudMoveScreen) {
            if (!mc.options.hideGui) {
               if (mc.screen == null || mc.screen instanceof HudMoveScreen || mc.screen instanceof ChatScreen || mc.screen instanceof AbstractContainerScreen) {
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
