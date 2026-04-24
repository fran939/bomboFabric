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
               for (int j = 0; j < loreList.size(); ++j) lore.append(loreList.getString(j)).append("\n");
            }
            
            boolean match = removeColors(fullName).toLowerCase().contains(query);
            if (!match && searchLore && removeColors(lore.toString()).toLowerCase().contains(query)) match = true;
            
            if (match) {
               int idx = matchCount.incrementAndGet();
               Object[] info = getContainerInfo(containerPath, i, removeColors(fullName));
               resolveName(extractLastUuidFromPath(containerPath)).thenAccept(memberName -> {
                  String cont = (String) info[0];
                  if (memberName != null && !memberName.isEmpty() && (ctx.coopMode || !memberName.equalsIgnoreCase(ctx.targetUsername))) cont += " (" + memberName + ")";
                  
                  HoverEvent h = createHoverEventRobust(lore.toString());
                  ClickEvent c = createClickEventRobust("RUN_COMMAND", (String) info[1]);
                  
                  MutableComponent link = Component.literal(fullName);
                  Style style = Style.EMPTY;
                  if (h != null) style = style.withHoverEvent(h);
                  if (c != null) style = style.withClickEvent(c);
                  link.setStyle(style);
                  
                  Component msg = Component.literal("§7#" + idx + " ").append(link).append(Component.literal(" §r§7(" + cont + ")"));
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
      if (s.contains("backpack")) { name = "Backpack"; cmd = "/backpack"; }
      else if (s.contains("ender")) { name = "Ender Chest"; cmd = "/enderchest"; }
      return new Object[] { name, cmd };
   }

   public static ClickEvent createClickEventRobust(String actionName, String value) {
      return null;
   }

   public static HoverEvent createHoverEventRobust(String text) {
      return null;
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

   private static CompletableFuture<UUID> getUuid(String name) { return CompletableFuture.completedFuture(UUID.randomUUID()); }
   private static CompletableFuture<String> getFeatureData(String uuid) { return CompletableFuture.completedFuture("{}"); }
   private static CompletableFuture<String> resolveName(String uuid) { return CompletableFuture.completedFuture(NAME_CACHE.getOrDefault(uuid, "")); }
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
