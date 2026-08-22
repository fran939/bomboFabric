package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

public class CustomTimerManager {
   public static final List<CustomTimer> activeTimers = new CopyOnWriteArrayList();
   private static final Path TIMERS_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("active_timers.json");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();

   public static boolean hasTimers() {
      return !activeTimers.isEmpty();
   }

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "custom_timer_manager"), CustomTimerManager::render);
      loadTimers();
   }

   public static void saveTimers() {
      java.util.concurrent.CompletableFuture.runAsync(() -> {
         try {
            Files.createDirectories(TIMERS_FILE.getParent());
            JsonArray array = new JsonArray();

            for(CustomTimer timer : activeTimers) {
               JsonObject obj = new JsonObject();
               obj.addProperty("name", timer.name);
               obj.addProperty("endTime", timer.endTime);
               obj.addProperty("isPartyTimer", timer.isPartyTimer);
               if (timer.logoItemId != null) {
                  obj.addProperty("logoItemId", timer.logoItemId);
               }

               obj.addProperty("showOnlyWhenReady", timer.showOnlyWhenReady);
               obj.addProperty("keepReadyState", timer.keepReadyState);
               obj.addProperty("ready", timer.ready);
               obj.addProperty("readyExpiration", timer.readyExpiration);
               array.add(obj);
            }

            Files.writeString(TIMERS_FILE, GSON.toJson(array));
         } catch (Throwable t) {
            t.printStackTrace();
         }
      });
   }

   public static void loadTimers() {
      try {
         if (!Files.exists(TIMERS_FILE, new LinkOption[0])) {
            return;
         }

         String json = Files.readString(TIMERS_FILE);
         if (json == null || json.trim().isEmpty()) {
            return;
         }

         JsonArray array = JsonParser.parseString(json).getAsJsonArray();
         activeTimers.clear();
         long now = System.currentTimeMillis();

         for(JsonElement elem : array) {
            JsonObject obj = elem.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "Timer";
            long endTime = obj.has("endTime") ? obj.get("endTime").getAsLong() : now;
            boolean isPartyTimer = obj.has("isPartyTimer") && obj.get("isPartyTimer").getAsBoolean();
            String logoItemId = obj.has("logoItemId") && !obj.get("logoItemId").isJsonNull() ? obj.get("logoItemId").getAsString() : null;
            boolean showOnlyWhenReady = obj.has("showOnlyWhenReady") && obj.get("showOnlyWhenReady").getAsBoolean();
            boolean keepReadyState = obj.has("keepReadyState") && obj.get("keepReadyState").getAsBoolean();
            boolean ready = obj.has("ready") && obj.get("ready").getAsBoolean();
            long readyExpiration = obj.has("readyExpiration") ? obj.get("readyExpiration").getAsLong() : -1L;
            long remainingMs = endTime - now;
            CustomTimer timer = new CustomTimer(name, remainingMs > 0L ? remainingMs : 0L, isPartyTimer, logoItemId, showOnlyWhenReady, keepReadyState);

            try {
               Field field = CustomTimer.class.getDeclaredField("endTime");
               field.setAccessible(true);
               field.setLong(timer, endTime);
            } catch (Throwable var21) {
            }

            timer.ready = ready;
            timer.readyExpiration = readyExpiration;
            if (!ready && now >= endTime) {
               timer.ready = true;
               timer.readyExpiration = keepReadyState ? -1L : now + 10000L;
            }

            if (!timer.ready || keepReadyState || timer.readyExpiration == -1L || now <= timer.readyExpiration) {
               activeTimers.add(timer);
            }
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.customTimerHudEnabled) {
         Minecraft client = Minecraft.getInstance();
         if (!client.options.hideGui) {
            if (client.screen == null || client.screen instanceof HudMoveScreen) {
               drawTimers(g, s.customTimerHudX, s.customTimerHudY, false);
            }
         }
      }
   }

   public static void drawTimers(GuiGraphicsExtractor g, int x, int y, boolean isHovered) {
      BomboConfig.Settings s = BomboConfig.get();
      if (!activeTimers.isEmpty() || isHovered) {
         g.pose().pushMatrix();
         g.pose().translate((float)x, (float)y);
         float scale = s.customTimerHudScale;
         g.pose().scale(scale, scale);
         int curY = 0;
         long now = System.currentTimeMillis();
         if (activeTimers.isEmpty()) {
            g.text(Minecraft.getInstance().font, "§6§lTimers §7(Example)", 0, curY, -1, true);
            curY += 14;
            g.text(Minecraft.getInstance().font, "§fTimer: §a02:45", 0, curY, -1, true);
            curY += 14;
            g.text(Minecraft.getInstance().font, "§fbomboclas: §e04:59", 0, curY, -1, true);
         } else {
            for(CustomTimer timer : activeTimers) {
               if (!timer.showOnlyWhenReady || timer.ready) {
                  int startX = 0;
                  if (timer.logoItemId != null && !timer.logoItemId.isEmpty()) {
                     ItemStack item = SkyblockItemManager.createSkyblockItem(timer.logoItemId);
                     if (item != null && !item.isEmpty()) {
                        g.pose().pushMatrix();
                        g.pose().translate(0.0F, (float)(curY - 3));
                        g.pose().scale(0.8F, 0.8F);
                        g.item(item, 0, 0);
                        g.pose().popMatrix();
                        startX += 14;
                     }
                  }

                  if (timer.ready) {
                     g.text(Minecraft.getInstance().font, "§f" + timer.name + ": §a§lREADY", startX, curY, -1, true);
                  } else {
                     long remaining = timer.endTime - now;
                     if (remaining < 0L) {
                        remaining = 0L;
                     }

                     String timeColor = remaining < 10000L ? "§c" : (remaining < 30000L ? "§e" : "§a");
                     g.text(Minecraft.getInstance().font, "§f" + timer.name + ": " + timeColor + formatTimeRemaining(remaining), startX, curY, -1, true);
                  }

                  curY += 14;
               }
            }
         }

         g.pose().popMatrix();
      }
   }

   public static int getWidth() {
      return 120;
   }

   public static int getHeight() {
      int count = activeTimers.isEmpty() ? 3 : 1 + activeTimers.size();
      return count * 14;
   }

   public static String formatTimeRemaining(long remainingMs) {
      if (remainingMs <= 0L) {
         return "00:00";
      } else {
         long totalSecs = remainingMs / 1000L;
         long hours = totalSecs / 3600L;
         long mins = totalSecs % 3600L / 60L;
         long secs = totalSecs % 60L;
         return hours > 0L ? String.format("%d:%02d:%02d", hours, mins, secs) : String.format("%02d:%02d", mins, secs);
      }
   }

   public static long parseTimeMs(String input) {
      if (input != null && !input.isEmpty()) {
         Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)(h|m|s)", 2).matcher(input);
         long totalMs = 0L;
         boolean found = false;

         while(matcher.find()) {
            found = true;

            try {
               double value = Double.parseDouble(matcher.group(1));
               switch (matcher.group(2).toLowerCase()) {
                  case "h":
                     totalMs += (long)(value * (double)3600.0F * (double)1000.0F);
                     break;
                  case "m":
                     totalMs += (long)(value * (double)60.0F * (double)1000.0F);
                     break;
                  case "s":
                     totalMs += (long)(value * (double)1000.0F);
               }
            } catch (NumberFormatException var11) {
            }
         }

         if (!found) {
            try {
               double value = Double.parseDouble(input);
               return (long)(value * (double)60.0F * (double)1000.0F);
            } catch (NumberFormatException var10) {
               return -1L;
            }
         } else {
            return totalMs;
         }
      } else {
         return -1L;
      }
   }

   public static void startTimer(String name, long durationMs) {
      startTimer(name, durationMs, false, (String)null, false, false);
   }

   public static void startTimer(String name, long durationMs, boolean isPartyTimer, String logoItemId, boolean showOnlyWhenReady) {
      startTimer(name, durationMs, isPartyTimer, logoItemId, showOnlyWhenReady, false);
   }

   public static void clearTimers() {
      activeTimers.clear();
      saveTimers();
   }

   public static void startTimer(String name, long durationMs, boolean isPartyTimer, String logoItemId, boolean showOnlyWhenReady, boolean keepReadyState) {
      if (name == null || name.isEmpty()) {
         name = "Timer";
      }

      for(CustomTimer timer : activeTimers) {
         if (timer.name.equalsIgnoreCase(name)) {
            activeTimers.remove(timer);
         }
      }

      activeTimers.add(new CustomTimer(name, durationMs, isPartyTimer, logoItemId, showOnlyWhenReady, keepReadyState));
      saveTimers();
   }

   public static void tick() {
      if (activeTimers.isEmpty()) {
         return;
      }
      long now = System.currentTimeMillis();
      boolean stateChanged = false;

      for(CustomTimer timer : activeTimers) {
         if (timer.ready) {
            if (!timer.keepReadyState && timer.readyExpiration != -1L && now > timer.readyExpiration) {
               activeTimers.remove(timer);
               stateChanged = true;
            }
         } else if (now >= timer.endTime) {
            timer.ready = true;
            timer.readyExpiration = timer.keepReadyState ? -1L : now + 10000L;
            stateChanged = true;
            Minecraft.getInstance().execute(() -> {
               Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.5F));
               String nameLabel = timer.name.equalsIgnoreCase("Timer") ? "" : " '" + timer.name + "'";
               Bomboaddons.sendMessage("&8[&bBomboAddons&8] &cTimer" + nameLabel + " has expired!");
               if (timer.isPartyTimer) {
                  BomboaddonsClient.executeTracked("pc Timer for " + timer.name + " has expired!");
               }

            });
         }
      }

      if (stateChanged) {
         saveTimers();
      }

   }

   public static class CustomTimer {
      public final String name;
      public final long endTime;
      public final boolean isPartyTimer;
      public final String logoItemId;
      public final boolean showOnlyWhenReady;
      public final boolean keepReadyState;
      public boolean ready = false;
      public long readyExpiration = -1L;

      public CustomTimer(String name, long durationMs, boolean isPartyTimer, String logoItemId, boolean showOnlyWhenReady, boolean keepReadyState) {
         this.name = name;
         this.endTime = System.currentTimeMillis() + durationMs;
         this.isPartyTimer = isPartyTimer;
         this.logoItemId = logoItemId;
         this.showOnlyWhenReady = showOnlyWhenReady;
         this.keepReadyState = keepReadyState;
      }
   }
}
