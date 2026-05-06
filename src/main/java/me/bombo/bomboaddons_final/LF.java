package me.bombo.bomboaddons_final;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;

@Environment(EnvType.CLIENT)
public class LF {
   private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();
   private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
   private static final int MAX_RESULTS = 300;

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
            return getFeatureData(cleanUuid).thenApply((json) -> {
               return new LF.SearchContext(json, cleanUuid, username, coopMode);
            });
         }
      }).thenAccept((ctx) -> {
         if (ctx != null && ctx.json != null) {
            Minecraft.getInstance().execute(() -> handleResponse(username, query, ctx));
         } else if (ctx != null) {
            sendMessage("&cFailed to get data for " + username);
         }
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
         searchJsonRecursive(root, "", lowerQuery, searchLore, matchCount, ctx, false, false, MAX_RESULTS);
         if (matchCount.get() == 0) sendMessage("&cCould not find '&f" + lowerQuery + "&c' in any container.");
      } catch (Exception e) {
         sendMessage("&cError parsing data: " + e.getMessage());
      }
   }

   private static void searchJsonRecursive(JsonElement element, String path, String query, boolean searchLore,
         AtomicInteger matchCount, LF.SearchContext ctx, boolean isInsideMembersNode, boolean toolkitsOnly, int limit) {
      if (matchCount.get() >= limit) return;
      if (element.isJsonArray()) {
         JsonArray arr = element.getAsJsonArray();
         for (int i = 0; i < arr.size(); ++i) {
            searchJsonRecursive(arr.get(i), path + " > " + i, query, searchLore, matchCount, ctx, isInsideMembersNode, toolkitsOnly, limit);
         }
      } else if (element.isJsonObject()) {
         JsonObject obj = element.getAsJsonObject();
         if (obj.has("data") && obj.get("data").isJsonPrimitive()) {
            decodeAndSearch(path, obj.get("data").getAsString(), query, searchLore, matchCount, ctx, toolkitsOnly, limit);
         } else {
            boolean nextIsMember = false;
            for (Entry<String, JsonElement> entry : obj.entrySet()) {
               String key = entry.getKey();
               if (key.equalsIgnoreCase("members")) nextIsMember = true;
               if (isInsideMembersNode && !ctx.coopMode) {
                  String raw = key.replace("-", "").toLowerCase();
                  if (raw.length() == 32 && UUID_PATTERN.matcher(raw).matches() && !raw.equals(ctx.targetUuid)) continue;
               }
               searchJsonRecursive(entry.getValue(), path.isEmpty() ? key : path + " > " + key, query, searchLore, matchCount, ctx, nextIsMember, toolkitsOnly, limit);
            }
         }
      } else if (element.isJsonPrimitive() && !path.contains(" > data")) {
          // Check if this is a sack item (primitive count)
          if (path.contains("sacks_counts") && !toolkitsOnly) {
              String[] parts = path.split(" > ");
              String id = parts[parts.length - 1];
              int count = element.getAsInt();
          if (count > 0) {
                  String nameId = id;
                  if (nameId.equals("NETHER_STALK")) nameId = "NETHER_WART";
                  else if (nameId.equals("ENCHANTED_NETHER_STALK")) nameId = "ENCHANTED_NETHER_WART";
                  else if (nameId.equals("MUTANT_NETHER_STALK")) nameId = "MUTANT_NETHER_WART";
                  
                  String name = nameId.replace("_", " ").toLowerCase();
                  // Simple capitalization
                  String capitalized = "";
                  for (String word : name.split(" ")) {
                      if (word.length() > 0) capitalized += word.substring(0, 1).toUpperCase() + word.substring(1) + " ";
                  }
                  capitalized = capitalized.trim();
                  
                  if (capitalized.toLowerCase().contains(query)) {
                      int idx = matchCount.incrementAndGet();
                      ClickEvent c = new ClickEvent.SuggestCommand("/gfs " + id + " ");
                      MutableComponent link = Component.literal(capitalized).withStyle(s -> s.withClickEvent(c));
                      Component msg = translate("&7#" + idx + " ").append(link).append(translate(" &e(x" + count + ") &r&7(Sacks)"));
                      sendMessage(msg);
                  }
              }
          }
      }
   }

   private static void decodeAndSearch(String containerPath, String base64Data, String query, boolean searchLore, AtomicInteger matchCount, LF.SearchContext ctx, boolean toolkitsOnly, int limit) {
      if (matchCount.get() >= limit) return;
      if (toolkitsOnly && !containerPath.contains("farming_toolkit")) return;
      try {
         byte[] data = Base64.getDecoder().decode(base64Data);
         CompoundTag nbt = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(9223372036854775807L));
         
         if (nbt.contains("i")) {
            net.minecraft.nbt.ListTag items = (net.minecraft.nbt.ListTag) nbt.get("i");
            if (items == null) return;
            for (int i = 0; i < items.size(); ++i) {
               processItemNbt((CompoundTag) items.get(i), i, containerPath, query, searchLore, matchCount, ctx, limit);
            }
         } else {
            // Might be a single item (like in some toolkits)
            processItemNbt(nbt, 0, containerPath, query, searchLore, matchCount, ctx, limit);
         }
      } catch (Exception e) {
         try {
            // Fallback for uncompressed NBT
            byte[] data = Base64.getDecoder().decode(base64Data);
            CompoundTag nbt = NbtIo.read(new java.io.DataInputStream(new ByteArrayInputStream(data)), NbtAccounter.create(9223372036854775807L));
            if (nbt.contains("i")) {
               net.minecraft.nbt.ListTag items = (net.minecraft.nbt.ListTag) nbt.get("i");
               if (items != null) {
                  for (int i = 0; i < items.size(); ++i) {
                     processItemNbt((CompoundTag) items.get(i), i, containerPath, query, searchLore, matchCount, ctx, limit);
                  }
               }
            } else {
               processItemNbt(nbt, 0, containerPath, query, searchLore, matchCount, ctx, limit);
            }
         } catch (Exception e2) {}
      }
   }

   private static void processItemNbt(CompoundTag item, int slotIndex, String containerPath, String query, boolean searchLore, AtomicInteger matchCount, LF.SearchContext ctx, int limit) {
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
            ClickEvent c = createClickEventRobust("RUN_COMMAND", "/bombo_highlight_slot " + finalSlotIndex + " " + fullCmd);
            
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
                      cmd = "/backpack " + num;
                      break;
                  }
              }
          } catch (Exception e) {}
      }
      else if (s.contains("ender")) {
          name = "Ender Chest"; cmd = "/enderchest";
          offset = 9;
          try {
              String[] parts = s.split(" > ");
              for (int i = 0; i < parts.length - 1; i++) {
                  if (parts[i].contains("ender")) {
                      int num = Integer.parseInt(parts[i+1]) + 1;
                      name = "Ender Chest " + num;
                      cmd = "/enderchest " + num;
                      break;
                  }
              }
              
              int pageFromIndex = (itemIndex / 45) + 1;
              if (itemIndex >= 45) {
                  if (!cmd.contains(" ")) {
                      name = "Ender Chest " + pageFromIndex;
                      cmd = "/enderchest " + pageFromIndex;
                  }
                  offset = 9 - (pageFromIndex - 1) * 45;
              } else {
                  offset = 9;
              }
          } catch (Exception e) {}
      }
      else if (s.contains("museum")) { name = "Museum"; cmd = "/museum"; }
      else if (s.contains("vault")) { name = "Personal Vault"; cmd = "/bank"; }
      else if (s.contains("wardrobe")) { name = "Wardrobe"; cmd = "/wardrobe"; }
      else if (s.contains("sacks")) { name = "Sacks"; cmd = "/sacks"; }
      else if (s.contains("accessory") || s.contains("talisman")) { name = "Accessory Bag"; cmd = "/accessories"; }
      else if (s.contains("pets")) { name = "Pets"; cmd = "/pets"; }
      else if (s.contains("toolkit")) {
          if (s.contains("hunting")) { name = "Hunting Toolkit"; cmd = "/play sb"; }
          else { 
              name = "Farming Toolkit"; 
              String[] parts = raw.split(" > ");
              for (int i = 0; i < parts.length; i++) {
                  if (parts[i].equalsIgnoreCase("farming_toolkit") && i + 1 < parts.length) {
                      name += " (" + parts[i+1] + ")";
                      break;
                  }
              }
              cmd = "/play sb"; 
          }
      }
      
      return new Object[] { name, cmd, offset };
   }

   public static ClickEvent createClickEventRobust(String actionName, String value) {
      if ("RUN_COMMAND".equals(actionName)) return new ClickEvent.RunCommand(value);
      if ("SUGGEST_COMMAND".equals(actionName)) return new ClickEvent.SuggestCommand(value);
      return null;
   }

   public static HoverEvent createHoverEventRobust(String text) {
      return new HoverEvent.ShowText(Component.literal(text));
   }

   public static String removeColors(String text) { 
       return text.replaceAll("(?i)\\u00A7[0-9a-fk-or]", "").replaceAll("(?i)&[0-9a-fk-or]", ""); 
   }

   private static String extractLastUuidFromPath(String path) {
      String[] parts = path.split(" > ");
      for (int i = parts.length - 1; i >= 0; i--) {
         String p = parts[i].replace("-", "").toLowerCase();
         if (p.length() == 32 && UUID_PATTERN.matcher(p).matches()) return p;
      }
      return null;
   }

   private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
           .version(java.net.http.HttpClient.Version.HTTP_2)
           .connectTimeout(java.time.Duration.ofSeconds(10L))
           .build();

   private static CompletableFuture<String> fetchString(String url) {
      java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url)).header("User-Agent", "Minecraft-Mod-1.21").GET().build();
      return CLIENT.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString()).thenApply((res) -> {
         return res.statusCode() == 200 ? res.body() : null;
      }).exceptionally((e) -> {
         return null;
      });
   }

   private static CompletableFuture<UUID> getUuid(String username) {
      return fetchString("https://api.ashcon.app/mojang/v2/uuid/" + username).thenCompose((response) -> {
         if (response != null) {
            try {
               if (response.contains("-") && response.length() == 36) {
                  return CompletableFuture.completedFuture(UUID.fromString(response.trim()));
               }
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

   private static CompletableFuture<String> getFeatureData(String cleanUuid) {
      return fetchString("https://bomboapi.frandl938.workers.dev/" + cleanUuid).thenCompose((response) -> {
         if (response != null && !response.isEmpty() && response.startsWith("{")) {
            return CompletableFuture.completedFuture(response);
         }
         return fetchString("https://profile.snailify.workers.dev/?uuid=" + cleanUuid);
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

   private static void sendMessage(String msg) { 
       if (Minecraft.getInstance().player != null) {
           String formatted = msg.replace("&", "\u00A7");
           Minecraft.getInstance().player.displayClientMessage(Component.literal(formatted), false); 
       }
   }
   
   private static void sendMessage(Component msg) { 
       if (Minecraft.getInstance().player != null) {
           Minecraft.getInstance().player.displayClientMessage(msg, false); 
       }
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
            return getFeatureData(cleanUuid).thenApply(json -> new Object[]{cleanUuid, json});
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
                    searchJsonRecursive(root, "", "", false, count, ctx, false, true, limit);
                    if (count.get() == 0) sendMessage("&cNo toolkit or sack data found.");
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> sendMessage("&cError: " + e.getMessage()));
            }
        });
    }

    private static void findAndPrintToolkit(JsonElement el, String target, String uuid, java.util.Set<JsonElement> visited) {
        // Legacy debug method, replaced by recursive search with empty query in showToolkit
    }
}
