package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PlaytimeGUI extends Screen {
   private static final Identifier CHEST_GUI_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
   private final int xSize;
   private final int ySize;
   private int guiLeft;
   private int guiTop;
   private ViewMode currentMode;
   private String selectedDate;
   private String selectedAreaName;
   private List<ViewItem> viewItems;
   private long totalPlaytime;
   private long totalAfkTime;
   private Map<String, Long> globalDaily;
   private final JsonObject cloudData;

   public PlaytimeGUI() {
      this((JsonObject)null);
   }

   public PlaytimeGUI(JsonObject cloudData) {
      super(Component.literal(cloudData != null ? "Playtime: " + (cloudData.has("username") ? cloudData.get("username").getAsString() : "Unknown") : "Detailed /playtime"));
      this.xSize = 176;
      this.ySize = 126;
      this.currentMode = PlaytimeGUI.ViewMode.AREAS;
      this.selectedDate = null;
      this.selectedAreaName = null;
      this.viewItems = new ArrayList();
      this.totalPlaytime = 0L;
      this.totalAfkTime = 0L;
      this.globalDaily = new HashMap();
      this.cloudData = cloudData;
      this.calculateEntries();
   }

   private void calculateEntries() {
      Map<String, PlaytimeTracker.AreaData> map;
      if (this.cloudData != null) {
         map = new HashMap();
         if (this.cloudData.has("areaDataMap")) {
            JsonObject areas = this.cloudData.getAsJsonObject("areaDataMap");

            for(String key : areas.keySet()) {
               PlaytimeTracker.AreaData data = (PlaytimeTracker.AreaData)(new Gson()).fromJson(areas.get(key), PlaytimeTracker.AreaData.class);
               map.put(key, data);
            }
         }
      } else {
         map = PlaytimeTracker.getAreaDataMap();
      }

      this.totalPlaytime = 0L;
      this.totalAfkTime = 0L;
      this.globalDaily = new HashMap();

      for(Map.Entry<String, PlaytimeTracker.AreaData> entry : map.entrySet()) {
         this.totalPlaytime += ((PlaytimeTracker.AreaData)entry.getValue()).totalTime;
         this.totalAfkTime += ((PlaytimeTracker.AreaData)entry.getValue()).afkTime;

         for(Map.Entry<String, Long> daily : ((PlaytimeTracker.AreaData)entry.getValue()).dailyTime.entrySet()) {
            this.globalDaily.put((String)daily.getKey(), (Long)this.globalDaily.getOrDefault(daily.getKey(), 0L) + (Long)daily.getValue());
         }
      }

      this.viewItems.clear();
      if (this.currentMode == PlaytimeGUI.ViewMode.AREAS) {
         for(Map.Entry<String, PlaytimeTracker.AreaData> entry : map.entrySet()) {
            this.viewItems.add(new ViewItem((String)entry.getKey(), (PlaytimeTracker.AreaData)entry.getValue(), ((PlaytimeTracker.AreaData)entry.getValue()).totalTime));
         }

         this.viewItems.sort((a, b) -> Long.compare(b.sortValue, a.sortValue));
      } else if (this.currentMode == PlaytimeGUI.ViewMode.DAYS) {
         for(Map.Entry<String, Long> day : this.globalDaily.entrySet()) {
            long afk = 0L;

            for(PlaytimeTracker.AreaData d : map.values()) {
               afk += (Long)d.dailyAfk.getOrDefault(day.getKey(), 0L);
            }

            ViewItem item = new ViewItem((String)day.getKey(), (PlaytimeTracker.AreaData)null, (Long)day.getValue());
            item.dayAfkTime = afk;
            this.viewItems.add(item);
         }

         this.viewItems.sort((a, b) -> b.name.compareTo(a.name));
      } else if (this.currentMode == PlaytimeGUI.ViewMode.DAY_DETAIL) {
         for(Map.Entry<String, PlaytimeTracker.AreaData> entry : map.entrySet()) {
            long time = (Long)((PlaytimeTracker.AreaData)entry.getValue()).dailyTime.getOrDefault(this.selectedDate, 0L);
            if (time > 0L) {
               ViewItem item = new ViewItem((String)entry.getKey(), (PlaytimeTracker.AreaData)entry.getValue(), time);
               item.dayAfkTime = (Long)((PlaytimeTracker.AreaData)entry.getValue()).dailyAfk.getOrDefault(this.selectedDate, 0L);
               this.viewItems.add(item);
            }
         }

         this.viewItems.sort((a, b) -> Long.compare(b.sortValue, a.sortValue));
      } else if (this.currentMode == PlaytimeGUI.ViewMode.SUB_AREAS) {
         PlaytimeTracker.AreaData parent = (PlaytimeTracker.AreaData)map.get(this.selectedAreaName);
         if (parent != null && parent.subAreas != null) {
            for(Map.Entry<String, PlaytimeTracker.AreaData> entry : parent.subAreas.entrySet()) {
               this.viewItems.add(new ViewItem((String)entry.getKey(), (PlaytimeTracker.AreaData)entry.getValue(), ((PlaytimeTracker.AreaData)entry.getValue()).totalTime));
            }

            this.viewItems.sort((a, b) -> Long.compare(b.sortValue, a.sortValue));
         }
      } else if (this.currentMode == PlaytimeGUI.ViewMode.DAY_SUB_AREAS) {
         PlaytimeTracker.AreaData parent = (PlaytimeTracker.AreaData)map.get(this.selectedAreaName);
         if (parent != null && parent.subAreas != null) {
            for(Map.Entry<String, PlaytimeTracker.AreaData> entry : parent.subAreas.entrySet()) {
               long time = (Long)((PlaytimeTracker.AreaData)entry.getValue()).dailyTime.getOrDefault(this.selectedDate, 0L);
               if (time > 0L) {
                  ViewItem item = new ViewItem((String)entry.getKey(), (PlaytimeTracker.AreaData)entry.getValue(), time);
                  item.dayAfkTime = (Long)((PlaytimeTracker.AreaData)entry.getValue()).dailyAfk.getOrDefault(this.selectedDate, 0L);
                  this.viewItems.add(item);
               }
            }

            this.viewItems.sort((a, b) -> Long.compare(b.sortValue, a.sortValue));
         }
      }

   }

   private String getAverage(Map<String, Long> daily, int days) {
      if (daily != null && !daily.isEmpty()) {
         List<String> keys = new ArrayList(daily.keySet());
         Collections.sort(keys, Collections.reverseOrder());
         long total = 0L;
         int count = Math.min(keys.size(), days);

         for(int i = 0; i < count; ++i) {
            total += (Long)daily.get(keys.get(i));
         }

         return PlaytimeTracker.formatTime(total / (long)count);
      } else {
         return "0s";
      }
   }

   private String formatSeconds(long seconds) {
      if (seconds < 60L) {
         return seconds + "s";
      } else {
         long minutes = seconds / 60L;
         long secs = seconds % 60L;
         if (minutes < 60L) {
            return minutes + "m " + secs + "s";
         } else {
            long hours = minutes / 60L;
            long mins = minutes % 60L;
            if (hours < 24L) {
               return hours + "h " + mins + "m";
            } else {
               long days = hours / 24L;
               long hrs = hours % 24L;
               return days + "d " + hrs + "h";
            }
         }
      }
   }

   private String getAverageWithActive(Map<String, Long> dailyTime, Map<String, Long> dailyAfk, int days) {
      if (dailyTime != null && !dailyTime.isEmpty()) {
         List<String> keys = new ArrayList(dailyTime.keySet());
         Collections.sort(keys, Collections.reverseOrder());
         long totalPlay = 0L;
         long totalAfk = 0L;
         int count = Math.min(keys.size(), days);

         for(int i = 0; i < count; ++i) {
            String date = (String)keys.get(i);
            totalPlay += (Long)dailyTime.get(date);
            if (dailyAfk != null) {
               totalAfk += (Long)dailyAfk.getOrDefault(date, 0L);
            }
         }

         long avgPlay = totalPlay / (long)count;
         long avgAfk = totalAfk / (long)count;
         long avgActive = Math.max(0L, avgPlay - avgAfk);
         String var10000 = PlaytimeTracker.formatTime(avgPlay);
         return "§a" + var10000 + " §7(§b" + PlaytimeTracker.formatTime(avgActive) + "§7)";
      } else {
         return "§a0s §7(§b0s§7)";
      }
   }

   protected void init() {
      int var10001 = this.width;
      Objects.requireNonNull(this);
      this.guiLeft = (var10001 - 176) / 2;
      var10001 = this.height;
      Objects.requireNonNull(this);
      this.guiTop = (var10001 - 126) / 2;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
      if (this.cloudData == null) {
         this.calculateEntries();
      }

      int x = (this.width - 176) / 2;
      int y = (this.height - 126) / 2;
      graphics.blit(CHEST_GUI_TEXTURE, x, y, x + 176, y + 126, 0.0F, 0.6875F, 0.0F, 0.4921875F);
      graphics.text(this.font, this.title, x + 8, y + 6, 4210752, false);
      int startX = x + 8;
      int startY = y + 18;
      HoveredTooltip hovered = null;
      int clockX = startX + 72;
      graphics.item(Items.CLOCK.getDefaultInstance(), clockX, startY);
      if (this.isMouseOver(clockX, startY, mouseX, mouseY)) {
         graphics.fill(clockX, startY, clockX + 16, startY + 16, -2130706433);
         Map<String, Long> globalDailyAfk = new HashMap();
         Map<String, PlaytimeTracker.AreaData> map;
         if (this.cloudData != null) {
            map = new HashMap();
            if (this.cloudData.has("areaDataMap")) {
               JsonObject areas = this.cloudData.getAsJsonObject("areaDataMap");

               for(String key : areas.keySet()) {
                  PlaytimeTracker.AreaData data = (PlaytimeTracker.AreaData)(new Gson()).fromJson(areas.get(key), PlaytimeTracker.AreaData.class);
                  map.put(key, data);
               }
            }
         } else {
            map = PlaytimeTracker.getAreaDataMap();
         }

         for(PlaytimeTracker.AreaData d : map.values()) {
            for(Map.Entry<String, Long> dailyAfk : d.dailyAfk.entrySet()) {
               globalDailyAfk.put((String)dailyAfk.getKey(), (Long)globalDailyAfk.getOrDefault(dailyAfk.getKey(), 0L) + (Long)dailyAfk.getValue());
            }
         }

         String[] var10005 = new String[]{"§7Total Playtime: §a" + PlaytimeTracker.formatTime(this.totalPlaytime), "§7Total AFK Time: §c" + PlaytimeTracker.formatTime(this.totalAfkTime), null, null, null, null, null, null};
         long var10008 = this.totalPlaytime - this.totalAfkTime;
         var10005[2] = "§7Active Playtime: §b" + PlaytimeTracker.formatTime(var10008);
         var10005[3] = "";
         var10005[4] = "§e§lAverages:";
         String var59 = this.getAverageWithActive(this.globalDaily, globalDailyAfk, 1);
         var10005[5] = "§7Daily: §f" + var59;
         var59 = this.getAverageWithActive(this.globalDaily, globalDailyAfk, 7);
         var10005[6] = "§7Weekly: §f" + var59;
         var59 = this.getAverageWithActive(this.globalDaily, globalDailyAfk, 30);
         var10005[7] = "§7Monthly: §f" + var59;
         hovered = new HoveredTooltip("§6Global Statistics", mouseX, mouseY, var10005);
      }

      for(int i = 0; i < Math.min(this.viewItems.size(), 27); ++i) {
         ViewItem item = (ViewItem)this.viewItems.get(i);
         int row = i / 9 + 1;
         int col = i % 9;
         int slotX = startX + col * 18;
         int slotY = startY + row * 18;
         ItemStack icon = this.currentMode == PlaytimeGUI.ViewMode.DAYS ? Items.PAPER.getDefaultInstance() : this.getIconForArea(item.name);
         graphics.item(icon, slotX, slotY);
         if (this.isMouseOver(slotX, slotY, mouseX, mouseY)) {
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, -2130706433);
            if (this.currentMode == PlaytimeGUI.ViewMode.AREAS) {
               String var10002 = "§a" + item.name;
               String[] var54 = new String[10];
               String var62 = PlaytimeTracker.formatTime(item.sortValue);
               var54[0] = "§7Playtime: §a" + var62 + " §8(" + PlaytimeTracker.formatTime(item.data.sessionTime) + ")";
               var62 = PlaytimeTracker.formatTime(item.data.afkTime);
               var54[1] = "§7AFK Time: §c" + var62 + " §8(" + PlaytimeTracker.formatTime(item.data.sessionAfkTime) + ")";
               long var64 = item.sortValue - item.data.afkTime;
               var54[2] = "§7Active: §b" + PlaytimeTracker.formatTime(var64);
               var54[3] = "";
               var54[4] = "§e§lAverages:";
               String var65 = this.getAverageWithActive(item.data.dailyTime, item.data.dailyAfk, 1);
               var54[5] = "§7Daily: §f" + var65;
               var65 = this.getAverageWithActive(item.data.dailyTime, item.data.dailyAfk, 7);
               var54[6] = "§7Weekly: §f" + var65;
               var65 = this.getAverageWithActive(item.data.dailyTime, item.data.dailyAfk, 30);
               var54[7] = "§7Monthly: §f" + var65;
               var54[8] = "";
               var54[9] = "§eClick to view subareas!";
               hovered = new HoveredTooltip(var10002, mouseX, mouseY, var54);
            } else if (this.currentMode == PlaytimeGUI.ViewMode.DAYS) {
               String var50 = "§b" + item.name;
               String[] var55 = new String[]{"§7Total Playtime: §a" + PlaytimeTracker.formatTime(item.sortValue), "§7AFK Time: §c" + PlaytimeTracker.formatTime(item.dayAfkTime), null, null, null};
               long var68 = item.sortValue - item.dayAfkTime;
               var55[2] = "§7Active: §b" + PlaytimeTracker.formatTime(var68);
               var55[3] = "";
               var55[4] = "§eClick to view islands played on this day!";
               hovered = new HoveredTooltip(var50, mouseX, mouseY, var55);
            } else if (this.currentMode == PlaytimeGUI.ViewMode.DAY_DETAIL) {
               String var51 = "§a" + item.name + " §7(" + this.selectedDate + ")";
               String[] var56 = new String[]{"§7Playtime: §a" + PlaytimeTracker.formatTime(item.sortValue), "§7AFK Time: §c" + PlaytimeTracker.formatTime(item.dayAfkTime), null, null, null};
               long var69 = item.sortValue - item.dayAfkTime;
               var56[2] = "§7Active: §b" + PlaytimeTracker.formatTime(var69);
               var56[3] = "";
               var56[4] = "§eClick to view subareas for this day!";
               hovered = new HoveredTooltip(var51, mouseX, mouseY, var56);
            } else if (this.currentMode == PlaytimeGUI.ViewMode.SUB_AREAS) {
               String var52 = "§d" + item.name + " §7(Subarea)";
               String[] var57 = new String[3];
               String var70 = PlaytimeTracker.formatTime(item.sortValue);
               var57[0] = "§7Playtime: §a" + var70 + " §8(" + PlaytimeTracker.formatTime(item.data.sessionTime) + ")";
               var70 = PlaytimeTracker.formatTime(item.data.afkTime);
               var57[1] = "§7AFK Time: §c" + var70 + " §8(" + PlaytimeTracker.formatTime(item.data.sessionAfkTime) + ")";
               long var72 = item.sortValue - item.data.afkTime;
               var57[2] = "§7Active: §b" + PlaytimeTracker.formatTime(var72);
               hovered = new HoveredTooltip(var52, mouseX, mouseY, var57);
            } else if (this.currentMode == PlaytimeGUI.ViewMode.DAY_SUB_AREAS) {
               String var53 = "§d" + item.name + " §7(" + this.selectedDate + ")";
               String[] var58 = new String[]{"§7Playtime: §a" + PlaytimeTracker.formatTime(item.sortValue), "§7AFK Time: §c" + PlaytimeTracker.formatTime(item.dayAfkTime), null};
               long var73 = item.sortValue - item.dayAfkTime;
               var58[2] = "§7Active: §b" + PlaytimeTracker.formatTime(var73);
               hovered = new HoveredTooltip(var53, mouseX, mouseY, var58);
            }
         }
      }

      int sessionX = startX + 126;
      int sessionY = startY + 72;
      graphics.item(Items.GOLD_NUGGET.getDefaultInstance(), sessionX, sessionY);
      if (this.isMouseOver(sessionX, sessionY, mouseX, mouseY)) {
         graphics.fill(sessionX, sessionY, sessionX + 16, sessionY + 16, -2130706433);
         long elapsed = PlaytimeTracker.getSessionTime();
         String area = BomboaddonsClient.currentArea;
         boolean afk = PlaytimeTracker.isAfk();
         boolean offline = false;
         long lastSyncAgo = -1L;
         if (this.cloudData != null) {
            if (this.cloudData.has("sessionTime")) {
               elapsed = this.cloudData.get("sessionTime").getAsLong();
            } else {
               elapsed = 0L;
            }

            if (this.cloudData.has("currentArea")) {
               area = this.cloudData.get("currentArea").getAsString();
            } else {
               area = "Unknown";
            }

            if (this.cloudData.has("isAfk")) {
               afk = this.cloudData.get("isAfk").getAsBoolean();
            }

            if (this.cloudData.has("lastUpdated")) {
               long lastUpdated = this.cloudData.get("lastUpdated").getAsLong();
               lastSyncAgo = (System.currentTimeMillis() - lastUpdated) / 1000L;
               if (lastSyncAgo > 3900L) {
                  offline = true;
               } else if (!offline) {
                  elapsed += System.currentTimeMillis() - lastUpdated;
               }
            }
         } else {
            lastSyncAgo = (System.currentTimeMillis() - PlaytimeTracker.lastCloudSyncTime) / 1000L;
         }

         String statusStr;
         if (offline) {
            statusStr = "§8Offline";
         } else if (afk) {
            statusStr = "§cAFK";
         } else {
            statusStr = "§aActive";
         }

         if ("None".equals(area)) {
            area = "Main Screen";
         }

         List<String> lore = new ArrayList();
         String var10001 = PlaytimeTracker.formatTime(elapsed);
         lore.add("§7Elapsed: §e" + var10001);
         lore.add("§7" + (offline ? "Last Area" : "Current Area") + ": §f" + area);
         lore.add("§7Status: " + statusStr);
         lore.add("");
         if (lastSyncAgo >= 0L) {
            var10001 = this.formatSeconds(lastSyncAgo);
            lore.add("§8Last Sync: " + var10001 + " ago");
            if (this.cloudData == null) {
               long next = Math.max(0L, 300L - lastSyncAgo);
               var10001 = this.formatSeconds(next);
               lore.add("§8Next Sync: " + var10001);
            }
         }

         hovered = new HoveredTooltip(offline ? "§dLast Session" : "§dCurrent Session", mouseX, mouseY, (String[])lore.toArray(new String[0]));
      }

      int closeX = startX + 72;
      int closeY = startY + 90;
      graphics.item(Items.BARRIER.getDefaultInstance(), closeX, closeY);
      if (this.isMouseOver(closeX, closeY, mouseX, mouseY)) {
         graphics.fill(closeX, closeY, closeX + 16, closeY + 16, -2130706433);
         hovered = new HoveredTooltip("§cClose", mouseX, mouseY, new String[0]);
      }

      if (this.currentMode != PlaytimeGUI.ViewMode.AREAS) {
         int backX = startX + 54;
         int backY = startY + 90;
         graphics.item(Items.ARROW.getDefaultInstance(), backX, backY);
         if (this.isMouseOver(backX, backY, mouseX, mouseY)) {
            graphics.fill(backX, backY, backX + 16, backY + 16, -2130706433);
            hovered = new HoveredTooltip("§eBack", mouseX, mouseY, new String[]{"§7Return to previous view"});
         }
      }

      if (this.currentMode == PlaytimeGUI.ViewMode.AREAS) {
         int dailyX = startX + 90;
         int dailyY = startY + 90;
         graphics.item(Items.WRITABLE_BOOK.getDefaultInstance(), dailyX, dailyY);
         if (this.isMouseOver(dailyX, dailyY, mouseX, mouseY)) {
            graphics.fill(dailyX, dailyY, dailyX + 16, dailyY + 16, -2130706433);
            hovered = new HoveredTooltip("§bDaily View", mouseX, mouseY, new String[]{"§7View playtime broken down by day."});
         }
      }

      if (hovered != null) {
         this.drawCustomTooltip(graphics, hovered, mouseX, mouseY);
      }

   }

   private boolean isMouseOver(int x, int y, int mouseX, int mouseY) {
      return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
   }

   private void drawCustomTooltip(GuiGraphicsExtractor graphics, HoveredTooltip tooltip, int mouseX, int mouseY) {
      int tX = mouseX + 12;
      int tY = mouseY - 12;
      int width = 170;
      int height = (tooltip.lore.length + 1) * 10 + 4;
      if (tX + width > this.width) {
         tX = mouseX - width - 12;
      }

      if (tY + height > this.height) {
         tY = this.height - height - 4;
      }

      graphics.fill(tX - 4, tY - 4, tX + width, tY + height, -15198184);
      graphics.fill(tX - 5, tY - 5, tX - 4, tY + height + 1, -11184811);
      graphics.fill(tX + width, tY - 5, tX + width + 1, tY + height + 1, -11184811);
      graphics.fill(tX - 5, tY - 5, tX + width + 1, tY - 4, -11184811);
      graphics.fill(tX - 5, tY + height, tX + width + 1, tY + height + 1, -11184811);
      graphics.text(this.font, tooltip.title, tX, tY, -1, true);

      for(int i = 0; i < tooltip.lore.length; ++i) {
         graphics.text(this.font, tooltip.lore[i], tX, tY + (i + 1) * 10, -1, true);
      }

   }

   private ItemStack getIconForArea(String area) {
      String name = area.toLowerCase();
      if (name.contains("island")) {
         return Items.GRASS_BLOCK.getDefaultInstance();
      } else if (name.contains("hub")) {
         return Items.FILLED_MAP.getDefaultInstance();
      } else if (name.contains("garden")) {
         return Items.SUNFLOWER.getDefaultInstance();
      } else if (!name.contains("crimson") && !name.contains("isle")) {
         if (name.contains("end")) {
            return Items.END_STONE.getDefaultInstance();
         } else if (name.contains("spider")) {
            return Items.SPIDER_EYE.getDefaultInstance();
         } else if (name.contains("park")) {
            return Items.OAK_SAPLING.getDefaultInstance();
         } else if (name.contains("gold")) {
            return Items.GOLD_ORE.getDefaultInstance();
         } else if (!name.contains("deep") && !name.contains("cavern")) {
            if (name.contains("dwarven")) {
               return Items.PRISMARINE_CRYSTALS.getDefaultInstance();
            } else if (name.contains("crystal")) {
               return Items.AMETHYST_CLUSTER.getDefaultInstance();
            } else if (name.contains("barn")) {
               return Items.WHEAT.getDefaultInstance();
            } else if (name.contains("mushroom")) {
               return Items.RED_MUSHROOM.getDefaultInstance();
            } else if (name.contains("dungeon")) {
               return Items.WITHER_SKELETON_SKULL.getDefaultInstance();
            } else if (name.contains("lobby")) {
               return Items.BEACON.getDefaultInstance();
            } else if (name.contains("limbo")) {
               return Items.FIREWORK_STAR.getDefaultInstance();
            } else if (name.contains("jerry")) {
               return Items.SNOWBALL.getDefaultInstance();
            } else {
               return name.contains("dark auction") ? Items.GOLD_INGOT.getDefaultInstance() : Items.PAPER.getDefaultInstance();
            }
         } else {
            return Items.IRON_ORE.getDefaultInstance();
         }
      } else {
         return Items.NETHERRACK.getDefaultInstance();
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      int x = (this.width - 176) / 2;
      int y = (this.height - 126) / 2;
      int startX = x + 8;
      int startY = y + 18;
      double mouseX = event.x();
      double mouseY = event.y();
      int closeX = startX + 72;
      int closeY = startY + 90;
      if (mouseX >= (double)closeX && mouseX < (double)(closeX + 16) && mouseY >= (double)closeY && mouseY < (double)(closeY + 16)) {
         this.onClose();
         return true;
      } else {
         if (this.currentMode != PlaytimeGUI.ViewMode.AREAS) {
            int backX = startX + 54;
            int backY = startY + 90;
            if (mouseX >= (double)backX && mouseX < (double)(backX + 16) && mouseY >= (double)backY && mouseY < (double)(backY + 16)) {
               if (this.currentMode == PlaytimeGUI.ViewMode.DAY_SUB_AREAS) {
                  this.currentMode = PlaytimeGUI.ViewMode.DAY_DETAIL;
                  this.calculateEntries();
               } else if (this.currentMode == PlaytimeGUI.ViewMode.DAY_DETAIL) {
                  this.currentMode = PlaytimeGUI.ViewMode.DAYS;
                  this.calculateEntries();
               } else if (this.currentMode == PlaytimeGUI.ViewMode.DAYS || this.currentMode == PlaytimeGUI.ViewMode.SUB_AREAS) {
                  this.currentMode = PlaytimeGUI.ViewMode.AREAS;
                  this.calculateEntries();
               }

               return true;
            }
         }

         if (this.currentMode == PlaytimeGUI.ViewMode.AREAS) {
            int dailyX = startX + 90;
            int dailyY = startY + 90;
            if (mouseX >= (double)dailyX && mouseX < (double)(dailyX + 16) && mouseY >= (double)dailyY && mouseY < (double)(dailyY + 16)) {
               this.currentMode = PlaytimeGUI.ViewMode.DAYS;
               this.calculateEntries();
               return true;
            }

            for(int i = 0; i < Math.min(this.viewItems.size(), 27); ++i) {
               int row = i / 9 + 1;
               int col = i % 9;
               int slotX = startX + col * 18;
               int slotY = startY + row * 18;
               if (mouseX >= (double)slotX && mouseX < (double)(slotX + 16) && mouseY >= (double)slotY && mouseY < (double)(slotY + 16)) {
                  this.selectedAreaName = ((ViewItem)this.viewItems.get(i)).name;
                  this.currentMode = PlaytimeGUI.ViewMode.SUB_AREAS;
                  this.calculateEntries();
                  return true;
               }
            }
         }

         if (this.currentMode == PlaytimeGUI.ViewMode.DAYS) {
            for(int i = 0; i < Math.min(this.viewItems.size(), 27); ++i) {
               int row = i / 9 + 1;
               int col = i % 9;
               int slotX = startX + col * 18;
               int slotY = startY + row * 18;
               if (mouseX >= (double)slotX && mouseX < (double)(slotX + 16) && mouseY >= (double)slotY && mouseY < (double)(slotY + 16)) {
                  this.selectedDate = ((ViewItem)this.viewItems.get(i)).name;
                  this.currentMode = PlaytimeGUI.ViewMode.DAY_DETAIL;
                  this.calculateEntries();
                  return true;
               }
            }
         } else if (this.currentMode == PlaytimeGUI.ViewMode.DAY_DETAIL) {
            for(int i = 0; i < Math.min(this.viewItems.size(), 27); ++i) {
               int row = i / 9 + 1;
               int col = i % 9;
               int slotX = startX + col * 18;
               int slotY = startY + row * 18;
               if (mouseX >= (double)slotX && mouseX < (double)(slotX + 16) && mouseY >= (double)slotY && mouseY < (double)(slotY + 16)) {
                  this.selectedAreaName = ((ViewItem)this.viewItems.get(i)).name;
                  this.currentMode = PlaytimeGUI.ViewMode.DAY_SUB_AREAS;
                  this.calculateEntries();
                  return true;
               }
            }
         }

         return super.mouseClicked(event, handled);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static enum ViewMode {
      AREAS,
      DAYS,
      DAY_DETAIL,
      SUB_AREAS,
      DAY_SUB_AREAS;

      // $FF: synthetic method
      private static ViewMode[] $values() {
         return new ViewMode[]{AREAS, DAYS, DAY_DETAIL, SUB_AREAS, DAY_SUB_AREAS};
      }
   }

   private static class HoveredTooltip {
      String title;
      String[] lore;
      int x;
      int y;

      HoveredTooltip(String title, int x, int y, String... lore) {
         this.title = title;
         this.lore = lore;
         this.x = x;
         this.y = y;
      }
   }

   private static class ViewItem {
      String name;
      PlaytimeTracker.AreaData data;
      long sortValue;
      long dayAfkTime;

      ViewItem(String name, PlaytimeTracker.AreaData data, long sortValue) {
         this.name = name;
         this.data = data;
         this.sortValue = sortValue;
      }
   }
}
