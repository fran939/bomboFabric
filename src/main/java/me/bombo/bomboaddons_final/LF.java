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
      sendMessage("§7Looking up §b" + username + (coopMode ? " §d(Coop Mode)" : "") + "§7...");
      getUuid(username).thenCompose((uuid) -> {
         if (uuid == null) {
            sendMessage("§cError: Could not find UUID for " + username);
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
            sendMessage("§cFailed to get data for " + username);
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
         sendMessage("§eSearch Results for '§f" + lowerQuery + "§e' in §b" + username + (ctx.coopMode ? " (Coop)" : "") + "§e:");
         searchJsonRecursive(root, "", lowerQuery, searchLore, matchCount, ctx, false);
         if (matchCount.get() == 0) sendMessage("§cCould not find '§f" + lowerQuery + "§c' in any container.");
      } catch (Exception e) {
         sendMessage("§cError parsing data: " + e.getMessage());
      }
   }

   private static void searchJsonRecursive(JsonElement element, String path, String query, boolean searchLore,
         AtomicInteger matchCount, LF.SearchContext ctx, boolean isInsideMembersNode) {
      if (matchCount.get() >= MAX_RESULTS) return;
      if (element.isJsonArray()) {
         JsonArray arr = element.getAsJsonArray();
         for (int i = 0; i < arr.size(); ++i) {
            searchJsonRecursive(arr.get(i), path + " > " + i, query, searchLore, matchCount, ctx, isInsideMembersNode);
         }
      } else if (element.isJsonObject()) {
         JsonObject obj = element.getAsJsonObject();
         if (obj.has("data") && obj.get("data").isJsonPrimitive()) {
            decodeAndSearch(path, obj.get("data").getAsString(), query, searchLore, matchCount, ctx);
         } else {
            boolean nextIsMember = false;
            for (Entry<String, JsonElement> entry : obj.entrySet()) {
               String key = entry.getKey();
               if (key.equalsIgnoreCase("members")) nextIsMember = true;
               if (isInsideMembersNode && !ctx.coopMode) {
                  String raw = key.replace("-", "").toLowerCase();
                  if (raw.length() == 32 && UUID_PATTERN.matcher(raw).matches() && !raw.equals(ctx.targetUuid)) continue;
               }
               searchJsonRecursive(entry.getValue(), path.isEmpty() ? key : path + " > " + key, query, searchLore, matchCount, ctx, nextIsMember);
            }
         }
      }
   }

   private static void decodeAndSearch(String containerPath, String base64Data, String query, boolean searchLore, AtomicInteger matchCount, LF.SearchContext ctx) {
      try {
         byte[] data = Base64.getDecoder().decode(base64Data);
         CompoundTag nbt = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(9223372036854775807L));
         if (!nbt.contains("i")) return;
         
         net.minecraft.nbt.ListTag items = (net.minecraft.nbt.ListTag) nbt.get("i");
         if (items == null) return;

         for (int i = 0; i < items.size(); ++i) {
            CompoundTag item = (CompoundTag) items.get(i);
            CompoundTag tag = item.getCompound("tag").orElse(null);
            if (tag == null) continue;
            
            CompoundTag display = tag.getCompound("display").orElse(null);
            if (display == null) continue;
            
            String fullName = display.getString("Name").orElse("");
            if (fullName.isEmpty()) continue;
            
            StringBuilder lore = new StringBuilder(fullName).append("\n");
            net.minecraft.nbt.ListTag loreList = (net.minecraft.nbt.ListTag) display.get("Lore");
            if (loreList != null) {
               for (int j = 0; j < loreList.size(); ++j) lore.append(loreList.getString(j).orElse("")).append("\n");
            }
            
            boolean match = removeColors(fullName).toLowerCase().contains(query);
            if (!match && searchLore && removeColors(lore.toString()).toLowerCase().contains(query)) match = true;
            
            if (match) {
               int idx = matchCount.incrementAndGet();
               Object[] info = getContainerInfo(containerPath, i, removeColors(fullName));
               final int slotIndex = i + (int) info[2];
               resolveName(extractLastUuidFromPath(containerPath)).thenAccept(memberName -> {
                  String cont = (String) info[0];
                  if (memberName != null && !memberName.isEmpty() && (ctx.coopMode || !memberName.equalsIgnoreCase(ctx.targetUsername))) cont += " (" + memberName + ")";
                  
                  HoverEvent h = createHoverEventRobust(lore.toString());
                  ClickEvent c = createClickEventRobust("RUN_COMMAND", "/bombo_highlight_slot " + slotIndex + " " + info[1]);
                  
                  MutableComponent link = Component.literal(fullName);
                  Style style = Style.EMPTY;
                  if (h != null) style = style.withHoverEvent(h);
                  if (c != null) style = style.withClickEvent(c);
                  link.setStyle(style);
                  
                  MutableComponent contComponent = Component.literal(" §r§7(" + cont + ")");
                  if (c != null) contComponent.setStyle(Style.EMPTY.withClickEvent(c));
                  
                  Component msg = Component.literal("§7#" + idx + " ").append(link).append(contComponent);
                  Minecraft.getInstance().execute(() -> sendMessage(msg));
               });
            }
         }
      } catch (Exception e) {}
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
          } catch (Exception e) {}
      }
      else if (s.contains("museum")) { name = "Museum"; cmd = "/museum"; }
      else if (s.contains("vault")) { name = "Personal Vault"; cmd = "/bank"; }
      else if (s.contains("wardrobe")) { name = "Wardrobe"; cmd = "/wardrobe"; }
      else if (s.contains("sacks")) { name = "Sacks"; cmd = "/sacks"; }
      else if (s.contains("accessory") || s.contains("talisman")) { name = "Accessory Bag"; cmd = "/accessories"; }
      else if (s.contains("pets")) { name = "Pets"; cmd = "/pets"; }
      
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

   public static String removeColors(String text) { return text.replaceAll("(?i)§[0-9a-fk-or]", ""); }

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

   private static void sendMessage(String msg) { if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), false); }
   private static void sendMessage(Component msg) { if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(msg, false); }

    private record SearchContext(String json, String targetUuid, String targetUsername, boolean coopMode) {}

    public static void printContainerInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            sendMessage("§6--- Container Diagnostic ---");
            sendMessage("§eTitle: §f" + screen.getTitle().getString());
            
            net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
            List<net.minecraft.world.inventory.Slot> slots = menu.slots;
            sendMessage("§eTotal Slots: §f" + slots.size());
            
            if (slots.size() > 49) {
                net.minecraft.world.item.ItemStack timer = slots.get(49).getItem();
                sendMessage("§eSlot 49 (Timer): §f" + timer.getItem().toString() + " (Foil: " + timer.hasFoil() + ")");
            }
            
            sendMessage("§7(Diagnostic info displayed in-game)");
        } else {
            sendMessage("§cNot in a container!");
        }
    }
}
