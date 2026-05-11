package me.bombo.bomboaddons_final;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

@Environment(EnvType.CLIENT)
public class LF {
   private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();
   private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
   private static final int MAX_RESULTS = 300;

   private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
           .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
           .build();

   public static void searchLocal(String query) {
      if (Minecraft.getInstance().player != null) {
         String name = Minecraft.getInstance().player.getName().getString();
         show(removeColors(name), query, false);
      }
   }

   public static void show(String username, String query, boolean coopMode) {
      sendMessage("&7Looking up &b" + username + (coopMode ? " &d(Coop Mode)" : "") + "&7...");
         getUuid(username).thenCompose((uuid) -> {
            if (uuid == null) {
               sendMessage("&cError: Could not find UUID for " + username);
               return CompletableFuture.completedFuture(null);
            } else {
               String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
               NAME_CACHE.put(cleanUuid, username);
               return getFeatureData(username, cleanUuid).thenApply((json) -> {
                  if (json == null || json.isEmpty()) {
                      sendMessage("&cError: Could not fetch data for " + username);
                      return null;
                  }
                  return new LF.SearchContext(json, cleanUuid, username, coopMode);
               });
            }
         }).thenAccept((ctx) -> {
            if (ctx != null && ctx.json != null) {
               Minecraft.getInstance().execute(() -> handleResponse(username, query, ctx));
            }
         }).exceptionally(e -> {
            sendMessage("&cError during search: " + e.getMessage());
            return null;
         });
   }


   private static void handleResponse(String username, String query, LF.SearchContext ctx) {
      try {
         JsonObject root = JsonParser.parseString(ctx.json).getAsJsonObject();
         String lowerQuery = query.toLowerCase();
         boolean searchLore = false;
         if (lowerQuery.startsWith("lore:") || lowerQuery.startsWith("l:")) {
            searchLore = true;
            lowerQuery = lowerQuery.substring(lowerQuery.indexOf(":") + 1).trim();
         }

         AtomicInteger matchCount = new AtomicInteger(0);
         sendMessage("&eSearch Results for '&f" + lowerQuery + "&e' in &b" + username + (ctx.coopMode ? " (Coop)" : "") + "&e:");
         searchJsonRecursive(root, "", lowerQuery, searchLore, matchCount, ctx, false, false, MAX_RESULTS, false);
         if (matchCount.get() == 0) sendMessage("&cCould find 0 results for '&f" + lowerQuery + "&c'.");
      } catch (Exception e) {
         sendMessage("&cError parsing data: " + e.getMessage());
      }
   }

   private static void searchJsonRecursive(JsonElement element, String path, String query, boolean searchLore,
         AtomicInteger matchCount, LF.SearchContext ctx, boolean isInsideMembersNode, boolean toolkitsOnly, int limit, boolean isBorrowed) {
      if (matchCount.get() >= limit) return;
      if (element.isJsonArray()) {
         JsonArray arr = element.getAsJsonArray();
         for (int i = 0; i < arr.size(); ++i) {
            searchJsonRecursive(arr.get(i), path + " > " + i, query, searchLore, matchCount, ctx, isInsideMembersNode, toolkitsOnly, limit, isBorrowed);
         }
      } else if (element.isJsonObject()) {
         JsonObject obj = element.getAsJsonObject();
         
         if (obj.has("borrowing") && obj.get("borrowing").isJsonPrimitive() && obj.get("borrowing").getAsBoolean()) {
             return; // Hide borrowed items entirely
         }

         if (obj.has("data") && obj.get("data").isJsonPrimitive()) {
            decodeAndSearch(path, obj.get("data").getAsString(), query, searchLore, matchCount, ctx, toolkitsOnly, limit, false);
         } else {
            boolean nextIsMember = false;
            for (Entry<String, JsonElement> entry : obj.entrySet()) {
               String key = entry.getKey();
               if (key.equalsIgnoreCase("members")) nextIsMember = true;
               
               if (isInsideMembersNode && !ctx.coopMode) {
                  String raw = key.replace("-", "").toLowerCase();
                  if (!raw.equals(ctx.targetUuid)) continue;
               }
               searchJsonRecursive(entry.getValue(), path.isEmpty() ? key : path + " > " + key, query, searchLore, matchCount, ctx, nextIsMember, toolkitsOnly, limit, false);
            }
         }
      }
   }

   private static void decodeAndSearch(String containerPath, String base64, String query, boolean searchLore,
         AtomicInteger matchCount, LF.SearchContext ctx, boolean toolkitsOnly, int limit, boolean isBorrowed) {
      try {
         byte[] bytes = Base64.getDecoder().decode(base64);
         CompoundTag nbt;
         try {
             nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
         } catch (Exception e) {
             nbt = NbtIo.read(new java.io.DataInputStream(new ByteArrayInputStream(bytes)));
         }
         if (nbt == null) return;

         if (nbt.contains("i")) {
             net.minecraft.nbt.ListTag list = (net.minecraft.nbt.ListTag) nbt.get("i");
             if (list != null) {
                 for (int i = 0; i < list.size(); ++i) {
                    CompoundTag item = list.getCompound(i).orElse(null);
                    if (item != null && !item.isEmpty()) {
                       processItemNbt(item, i, containerPath, query, searchLore, matchCount, ctx, limit, isBorrowed);
                    }
                 }
             }
         } else {
             if (nbt.contains("id") || nbt.contains("tag")) {
                 processItemNbt(nbt, 0, containerPath, query, searchLore, matchCount, ctx, limit, isBorrowed);
             }
         }
      } catch (Exception e) {
          if (BomboConfig.get().apiDebug) {
              sendMessage("&c[Debug] Decode failed for " + containerPath + ": " + e.getMessage());
          }
      }
   }

   private static void processItemNbt(CompoundTag item, int slotIndex, String containerPath, String query, boolean searchLore, AtomicInteger matchCount, LF.SearchContext ctx, int limit, boolean isBorrowed) {
      if (matchCount.get() >= limit) return;
      CompoundTag tag = item.getCompound("tag").orElse(null);
      if (tag == null) return;
      
      CompoundTag display = tag.getCompound("display").orElse(null);
      if (display == null) return;
      
      String fullName = display.getString("Name").orElse("");
      if (fullName.isEmpty()) return;
      
      StringBuilder lore = new StringBuilder(fullName).append("\n");
      net.minecraft.nbt.ListTag loreList = (net.minecraft.nbt.ListTag) display.get("Lore");
      if (loreList != null) {
         for (int j = 0; j < loreList.size(); ++j) lore.append(loreList.getString(j).orElse("")).append("\n");
      }
      
      boolean match = removeColors(fullName).toLowerCase().contains(query);
      if (!match && searchLore && removeColors(lore.toString()).toLowerCase().contains(query)) match = true;
      
      if (match) {
         int idx = matchCount.incrementAndGet();
         Object[] info = getContainerInfo(containerPath, slotIndex, removeColors(fullName));
         final int finalSlotIndex = slotIndex + (int) info[2];
         final String finalCmdBase = (String) info[1];
         final String finalContBase = (String) info[0];
         final int finalOffset = (int) info[2];

         resolveName(extractLastUuidFromPath(containerPath)).thenAccept(memberName -> {
            String cont = finalContBase;
            String fullCmd = finalCmdBase;
            
            boolean isOthers = memberName != null && !memberName.isEmpty() && (ctx.coopMode || !memberName.equalsIgnoreCase(ctx.targetUsername));
            if (isOthers) {
                cont += " (" + memberName + ")";
                if (fullCmd.startsWith("/enderchest") || fullCmd.startsWith("/backpack") || fullCmd.startsWith("/museum") || fullCmd.startsWith("/bank")) {
                    fullCmd += " " + memberName;
                }
            }
            
            HoverEvent h = createHoverEventRobust(lore.toString());
            
            String clickCmd = "/bombo_highlight_slot " + finalSlotIndex + " " + fullCmd;
            if (fullCmd.startsWith("/museum")) {
                String mUser = memberName != null && !memberName.isEmpty() ? memberName : ctx.targetUsername;
                clickCmd = "/bombo_museum_click " + mUser + " " + finalSlotIndex;
            }
            
            ClickEvent c = createClickEventRobust("RUN_COMMAND", clickCmd);
            
            MutableComponent link = Component.literal(fullName);
            Style style = Style.EMPTY;
            if (h != null) style = style.withHoverEvent(h);
            if (c != null) style = style.withClickEvent(c);
            link.setStyle(style);
            
            MutableComponent contComponent = translate(" &r&7(" + cont + ")");
            if (c != null) contComponent.setStyle(Style.EMPTY.withClickEvent(c));
            
            final String finalContDisplay = cont;
            Component msg = translate("&7#" + idx + " ").append(link).append(contComponent);
            Minecraft.getInstance().execute(() -> {
                if (BomboConfig.get().debugMode) {
                    sendMessage("&b[Debug] " + finalContDisplay + " slot " + finalSlotIndex + " (i=" + slotIndex + ", offset=" + finalOffset + ")");
                }
                sendMessage(msg);
            });
         });
      }
   }

   public static Object[] getContainerInfo(String raw, int itemIndex, String itemName) {
      String s = raw.toLowerCase();
      String name = "Inventory";
      String cmd = "/play sb";
      int offset = 0;
      if (s.contains("backpack")) {
          name = "Backpack"; cmd = "/backpack";
          offset = 9;
          try {
              String[] parts = s.split(" > ");
              for (int i = 0; i < parts.length - 1; i++) {
                  if (parts[i].contains("backpack")) {
                      int num = Integer.parseInt(parts[i+1]) + 1;
                      name = "Backpack " + num;
                      break;
                  }
              }
          } catch (Exception e) {}
      }
      else if (s.contains("enderchest") || s.contains("ender_chest")) { name = "Ender Chest"; cmd = "/enderchest"; }
      else if (s.contains("wardrobe")) { name = "Wardrobe"; cmd = "/wardrobe"; }
      else if (s.contains("vault")) { name = "Personal Vault"; cmd = "/pv"; }
      else if (s.contains("museum")) { name = "Museum"; cmd = "/museum"; }
      else if (s.contains("sacks")) { name = "Sacks"; cmd = "/sacks"; }
      else if (s.contains("quiver")) { name = "Quiver"; cmd = "/quiver"; }
      else if (s.contains("potion_bag")) { name = "Potion Bag"; cmd = "/potionbag"; }
      else if (s.contains("candy_bag")) { name = "Candy Bag"; cmd = "/candybag"; }
      else if (s.contains("fishing_bag")) { name = "Fishing Bag"; cmd = "/fishingbag"; }
      
      return new Object[] { name, cmd, offset };
   }

   public static String removeColors(String s) {
      return s == null ? "" : s.replaceAll("§.", "").replaceAll("&.", "");
   }

   private static String extractLastUuidFromPath(String path) {
      String[] parts = path.split(" > ");
      for (int i = parts.length - 1; i >= 0; i--) {
         String p = parts[i].replace("-", "").toLowerCase();
         if (p.length() == 32 && UUID_PATTERN.matcher(p).matches()) return p;
      }
      return null;
   }

   private static CompletableFuture<UUID> getUuid(String username) {
      if (username == null || username.isEmpty()) return CompletableFuture.completedFuture(null);
      return fetchString("https://api.ashcon.app/mojang/v2/user/" + username).thenCompose((response) -> {
         if (response != null && response.contains("\"uuid\"")) {
            try {
               JsonObject json = JsonParser.parseString(response).getAsJsonObject();
               if (json.has("uuid")) {
                  return CompletableFuture.completedFuture(UUID.fromString(json.get("uuid").getAsString()));
               }
            } catch (Exception e) {}
         }
         return fetchString("https://api.mojang.com/users/profiles/minecraft/" + username).thenApply((mojangRes) -> {
            if (mojangRes == null) return null;
            try {
               JsonObject json = JsonParser.parseString(mojangRes).getAsJsonObject();
               if (json.has("id")) {
                  return UUID.fromString(json.get("id").getAsString().replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
               }
            } catch (Exception e) {}
            return null;
         });
      });
   }

   private static CompletableFuture<String> getFeatureData(String username, String cleanUuid) {
      String url = "https://bomboapi.frandl938.workers.dev/" + username;
      if (BomboConfig.get().apiDebug) {
          sendMessage("&b[Debug] API: " + url);
      }
      return fetchString(url).thenCompose((response) -> {
         if (response != null && !response.isEmpty() && response.startsWith("{")) {
            return CompletableFuture.completedFuture(response);
         }
         // Fallback to UUID
         String uuidUrl = "https://bomboapi.frandl938.workers.dev/" + cleanUuid;
         if (BomboConfig.get().apiDebug) sendMessage("&7[Debug] Username API failed, trying UUID: " + uuidUrl);
         return fetchString(uuidUrl).thenCompose(uuidRes -> {
             if (uuidRes != null && !uuidRes.isEmpty() && uuidRes.startsWith("{")) return CompletableFuture.completedFuture(uuidRes);
             return fetchString("https://profile.snailify.workers.dev/?uuid=" + cleanUuid);
         });
      });
   }

   private static CompletableFuture<String> resolveName(String uuid) {
      if (uuid == null) return CompletableFuture.completedFuture(null);
      if (NAME_CACHE.containsKey(uuid.toLowerCase())) return CompletableFuture.completedFuture(NAME_CACHE.get(uuid.toLowerCase()));
      
      return fetchString("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid).thenCompose((response) -> {
         if (response != null && response.contains("\"name\"")) {
            try {
               JsonObject json = JsonParser.parseString(response).getAsJsonObject();
               if (json.has("name")) {
                  String name = json.get("name").getAsString();
                  NAME_CACHE.put(uuid.toLowerCase(), name);
                  return CompletableFuture.completedFuture(name);
               }
            } catch (Exception e) {}
         }
         return fetchString("https://api.ashcon.app/mojang/v2/user/" + uuid).thenApply((fallbackRes) -> {
            if (fallbackRes == null) return null;
            try {
               JsonObject json = JsonParser.parseString(fallbackRes).getAsJsonObject();
               if (json.has("username")) {
                  String name = json.get("username").getAsString();
                  NAME_CACHE.put(uuid.toLowerCase(), name);
                  return name;
               }
            } catch (Exception e) {}
            return null;
         });
      });
   }

   private static CompletableFuture<String> fetchString(String url) {
      java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
              .uri(java.net.URI.create(url))
              .timeout(java.time.Duration.ofSeconds(10))
              .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
              .GET()
              .build();
      
      return CLIENT.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString()).thenApply((res) -> {
         return res.statusCode() == 200 ? res.body() : null;
      }).exceptionally((e) -> {
         return null;
      });
   }

   private static void sendMessage(String msg) { 
       if (Minecraft.getInstance().player != null) {
           Minecraft.getInstance().player.displayClientMessage(translate(msg), false); 
       }
   }
   
   private static void sendMessage(Component msg) { 
       if (Minecraft.getInstance().player != null) {
           Minecraft.getInstance().player.displayClientMessage(msg, false); 
       }
   }

   public static HoverEvent createHoverEventRobust(String lore) {
       return SBECommands.createHoverEvent(lore);
   }

   public static ClickEvent createClickEventRobust(String action, String value) {
       try {
           if (action.equalsIgnoreCase("RUN_COMMAND")) return new ClickEvent.RunCommand(value);
           if (action.equalsIgnoreCase("SUGGEST_COMMAND")) return new ClickEvent.SuggestCommand(value);
           if (action.equalsIgnoreCase("OPEN_URL")) return new ClickEvent.OpenUrl(java.net.URI.create(value));
           return null;
       } catch (Throwable t) { return null; }
   }

   private static MutableComponent translate(String s) {
       return Component.literal(s.replace("&", "\u00A7"));
   }

    private record SearchContext(String json, String targetUuid, String targetUsername, boolean coopMode) {}

    public static void printContainerInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            sendMessage("&6--- Container Diagnostic ---");
            sendMessage("&eTitle: &f" + screen.getTitle().getString());
            
            net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
            List<net.minecraft.world.inventory.Slot> slots = menu.slots;
            sendMessage("&eTotal Slots: &f" + slots.size());
            
            if (slots.size() > 49) {
                net.minecraft.world.item.ItemStack timer = slots.get(49).getItem();
                sendMessage("&eSlot 49 (Timer): &f" + timer.getItem().toString() + " (Foil: " + timer.hasFoil() + ")");
            }
            
            sendMessage("&7(Diagnostic info displayed in-game)");
        } else {
            sendMessage("&cNot in a container!");
        }
    }

    public static void showToolkit(String username, int limit) {
        sendMessage("&7Fetching toolkit data for &b" + username + "&7...");
        getUuid(username).thenCompose((uuid) -> {
            if (uuid == null) return CompletableFuture.completedFuture(null);
            String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
            return getFeatureData(username, cleanUuid).thenApply(json -> new Object[]{cleanUuid, json});
        }).thenAccept((results) -> {
            if (results == null) return;
            String cleanUuid = (String) results[0];
            String json = (String) results[1];
            if (json == null) {
                Minecraft.getInstance().execute(() -> sendMessage("&cFailed to get data."));
                return;
            }
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                Minecraft.getInstance().execute(() -> {
                    sendMessage("&e--- Toolkit Contents for &b" + username + " &e---");
                    AtomicInteger count = new AtomicInteger(0);
                    LF.SearchContext ctx = new LF.SearchContext(json, cleanUuid, username, false);
                    searchJsonRecursive(root, "", "", false, count, ctx, false, true, limit, false);
                    if (count.get() == 0) sendMessage("&cNo toolkit or sack data found.");
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> sendMessage("&cError: " + e.getMessage()));
            }
        });
    }
}
