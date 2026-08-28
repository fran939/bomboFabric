package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class StoragePreviewManager {
   private static final Map<String, InventorySnapshot> storageCache = new ConcurrentHashMap();
   private static final Map<String, ParsedTooltipCache> parsedItemCache = new ConcurrentHashMap();
   private static boolean initialApiCheckDone = false;
   private static boolean initGuard = false;
   private static final File CACHE_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("bombo_storage.json").toFile();

   public static void init() {
      if (!initGuard) {
         initGuard = true;
         loadCacheFromDisk();
         initialApiCheckDone = true;
      }
   }

   private static void loadCacheFromDisk() {
      if (CACHE_FILE.exists()) {
         try {
            FileReader reader = new FileReader(CACHE_FILE);

            try {
               Map<String, InventorySnapshot> loaded = (Map)(new Gson()).fromJson(reader, (new TypeToken<Map<String, InventorySnapshot>>() {
               }).getType());
               if (loaded != null) {
                  storageCache.putAll(loaded);
               }
            } catch (Throwable var4) {
               try {
                  reader.close();
               } catch (Throwable var3) {
                  var4.addSuppressed(var3);
               }

               throw var4;
            }

            reader.close();
         } catch (Exception var5) {
         }

      }
   }

   private static void saveCacheToDisk() {
      try {
         CACHE_FILE.getParentFile().mkdirs();
         FileWriter writer = new FileWriter(CACHE_FILE);

         try {
            (new GsonBuilder()).setPrettyPrinting().create().toJson(storageCache, writer);
         } catch (Throwable var4) {
            try {
               writer.close();
            } catch (Throwable var3) {
               var4.addSuppressed(var3);
            }

            throw var4;
         }

         writer.close();
      } catch (Exception var5) {
      }

   }

   private static void logDebugToFile(String msg) {
      try {
         File logFile = FabricLoader.getInstance().getConfigDir().resolve("storage_preview_debug.log").toFile();
         FileWriter fw = new FileWriter(logFile, true);

         try {
            String var10001 = String.valueOf(new Date());
            fw.write("[" + var10001 + "] " + msg + "\n");
         } catch (Throwable var6) {
            try {
               fw.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         fw.close();
      } catch (Exception var7) {
      }

   }

   public static void fetchStorageFromApiAsync() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         String ign = mc.player.getName().getString();
         CompletableFuture.runAsync(() -> {
            try {
               if (BomboConfig.get().storagePreviewDebug && mc.player != null) {
                  String msg = "[StorageDebug] Fetching API storage data for: " + ign + " (UUID: " + String.valueOf(mc.player.getUUID()) + ", Prefix: " + getProfilePrefix() + ")";
                  mc.player.sendSystemMessage(Component.literal("§8[§bStorageDebug§8] §7Fetching API storage data for: §e" + ign));
                  logDebugToFile(msg);
               }

               URL url = new URL("https://api.bombo.dpdns.org/data/" + ign);
               HttpURLConnection conn = (HttpURLConnection)url.openConnection();
               conn.setRequestMethod("GET");
               conn.setConnectTimeout(10000);
               conn.setReadTimeout(10000);
               conn.setRequestProperty("User-Agent", "BomboAddons/1.0");
               int code = conn.getResponseCode();
               if (BomboConfig.get().storagePreviewDebug && mc.player != null) {
                  mc.player.sendSystemMessage(Component.literal("§8[§bStorageDebug§8] §7API Response code: §a" + code));
                  logDebugToFile("[StorageDebug] API Response code: " + code);
               }

               if (code == 200) {
                  InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                  JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                  reader.close();
                  JsonObject profile = null;
                  if (json.has("raw_profile") && json.getAsJsonObject("raw_profile").has("members")) {
                     profile = json.getAsJsonObject("raw_profile");
                  } else if (json.has("profile") && json.getAsJsonObject("profile").has("members")) {
                     profile = json.getAsJsonObject("profile");
                  }

                  if (profile != null) {
                     if (profile.has("profile_id")) {
                        SkyblockUtils.currentProfileId = profile.get("profile_id").getAsString();
                     } else if (profile.has("id")) {
                        SkyblockUtils.currentProfileId = profile.get("id").getAsString();
                     }

                     logDebugToFile("[StorageDebug] Active Profile ID resolved to: " + SkyblockUtils.currentProfileId);
                     JsonObject members = profile.getAsJsonObject("members");
                     String uuidClean = mc.player.getUUID().toString().replace("-", "");
                     JsonObject memberData = null;

                     for(String key : members.keySet()) {
                        if (key.replace("-", "").equalsIgnoreCase(uuidClean)) {
                           memberData = members.getAsJsonObject(key);
                           break;
                        }
                     }

                     if (memberData == null && !members.keySet().isEmpty()) {
                        memberData = members.getAsJsonObject((String)members.keySet().iterator().next());
                     }

                     if (memberData != null && memberData.has("inventory")) {
                        JsonObject inv = memberData.getAsJsonObject("inventory");
                        if (inv.has("ender_chest_contents")) {
                           parseAndCacheApiStorage("enderchest1", inv.getAsJsonObject("ender_chest_contents"), "Ender Chest (Page 1)");
                        }

                        if (inv.has("backpack_contents")) {
                           JsonObject bpObj = inv.getAsJsonObject("backpack_contents");

                           for(String bpKey : bpObj.keySet()) {
                              try {
                                 int slotNum = Integer.parseInt(bpKey) + 1;
                                 parseAndCacheApiStorage("backpackslot" + slotNum, bpObj.getAsJsonObject(bpKey), "Backpack (Slot #" + slotNum + ")");
                                 parseAndCacheApiStorage("backpack" + slotNum, bpObj.getAsJsonObject(bpKey), "Backpack (Slot #" + slotNum + ")");
                              } catch (NumberFormatException var20) {
                              }
                           }
                        }

                        saveCacheToDisk();
                        if (BomboConfig.get().storagePreviewDebug && mc.player != null) {
                           int var10000 = storageCache.size();
                           String msg = "[StorageDebug] Successfully parsed & cached API storage! (Cached " + var10000 + " storage keys, Active Prefix: " + getProfilePrefix() + ")";
                           mc.player.sendSystemMessage(Component.literal("§8[§bStorageDebug§8] §aSuccessfully parsed & cached API storage! (Cached " + storageCache.size() + " storage keys)"));
                           logDebugToFile(msg);
                        }
                     }
                  }
               }

               conn.disconnect();
            } catch (Exception e) {
               logDebugToFile("[StorageDebug] Error: " + e.getMessage());
            } finally {
               initialApiCheckDone = true;
            }

         });
      }
   }

   private static void parseAndCacheApiStorage(String storageKey, JsonObject nbtDataObj, String displayName) {
      if (nbtDataObj.has("data")) {
         try {
            String b64Data = nbtDataObj.get("data").getAsString();
            byte[] bytes = Base64.getDecoder().decode(b64Data);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            InputStream is = bais;
            if (bytes.length > 2 && bytes[0] == 31 && bytes[1] == -117) {
               is = new GZIPInputStream(bais);
            }

            CompoundTag rootTag;
            try {
               rootTag = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            } catch (Exception var16) {
               DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
               rootTag = NbtIo.read(dis, NbtAccounter.unlimitedHeap());
            }

            if (rootTag != null && rootTag.contains("i")) {
               ListTag list = (ListTag)rootTag.getList("i").orElse(new ListTag());
               List<String> itemData = new ArrayList();
               Minecraft mc = Minecraft.getInstance();
               RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());

               for(int i = 0; i < list.size(); ++i) {
                  if (i >= 9) {
                     CompoundTag itemCompound = list.getCompound(i).orElse(new CompoundTag());
                     if (!itemCompound.isEmpty() && itemCompound.contains("id")) {
                        ItemStack stack = (ItemStack)ItemStack.CODEC.parse(ops, itemCompound).result().orElse(ItemStack.EMPTY);
                        if (!stack.isEmpty() && !isNavigationItem(stack)) {
                           Tag tag = (Tag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                           itemData.add(i - 9 + "|" + tag.toString());
                        }
                     }
                  }
               }

               String cleanK = cleanKey(storageKey);
               String prefix = getProfilePrefix();
               storageCache.put(prefix + cleanK, new InventorySnapshot(displayName, "API Sync", itemData));
               parsedItemCache.remove(prefix + cleanK);
               parsedItemCache.remove(cleanK);
            }
         } catch (Exception var17) {
         }

      }
   }

   private static boolean isNavigationItem(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         String name = stack.getHoverName().getString().replaceAll("§.", "").toLowerCase();
         return name.contains("next page") || name.contains("previous page") || name.contains("close") || name.contains("go back");
      }
   }

   public static void onContainerTick(AbstractContainerScreen<?> screen) {
      if (!initialApiCheckDone) {
         init();
      }

      String title = screen.getTitle().getString().replaceAll("§.", "").trim();
      String storageKey = identifyStorageKey(title);
      if (storageKey != null) {
         Minecraft mc = Minecraft.getInstance();
         List<String> itemData = new ArrayList();
         RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
         boolean isBackpack = title.toLowerCase().contains("backpack");
         boolean isEnderChest = title.toLowerCase().contains("ender chest");
         int maxContainerSlot = -1;

         for(Slot slot : screen.getMenu().slots) {
            if (slot.container != mc.player.getInventory()) {
               int slotIndex = slot.index;
               if (!isBackpack && !isEnderChest || slotIndex >= 9) {
                  int targetIdx = !isBackpack && !isEnderChest ? slotIndex : slotIndex - 9;
                  if (targetIdx > maxContainerSlot) {
                     maxContainerSlot = targetIdx;
                  }

                  ItemStack stack = slot.getItem();
                  if (!stack.isEmpty() && !isNavigationItem(stack)) {
                     try {
                        Tag tag = (Tag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                        itemData.add(targetIdx + "|" + tag.toString());
                     } catch (Exception var15) {
                     }
                  }
               }
            }
         }

         if (maxContainerSlot != -1) {
            itemData.add(maxContainerSlot + "|DUMMY");
         }

         int slotNum = extractSlotNumber(title);
         String formattedTitle = title;
         if (isBackpack && slotNum != -1) {
            formattedTitle = "Backpack (Slot #" + slotNum + ")";
         } else if (isEnderChest) {
            formattedTitle = title;
         }

         String cleanK = cleanKey(storageKey);
         String prefix = getProfilePrefix();
         InventorySnapshot snapshot = new InventorySnapshot(formattedTitle, "Live", itemData);
         storageCache.put(prefix + cleanK, snapshot);
         parsedItemCache.remove(prefix + cleanK);
         parsedItemCache.remove(cleanK);
         saveCacheToDisk();
      }
   }

   public static boolean isPreviewActive(ItemStack stack) {
      if (!BomboConfig.get().storagePreview || stack == null || stack.isEmpty()) {
         return false;
      }
      String hoverName = stack.getHoverName().getString().replaceAll("§.", "").trim();
      String storageKey = identifyStorageKeyFromItem(hoverName, stack);
      if (storageKey == null) {
         return false;
      }
      InventorySnapshot snapshot = findMatchingSnapshot(storageKey);
      return snapshot != null && !snapshot.itemData.isEmpty();
   }

   public static boolean isPreviewActive(Slot hoveredSlot) {
      if (!BomboConfig.get().storagePreview) {
         return false;
      } else if (hoveredSlot != null && hoveredSlot.hasItem()) {
         return isPreviewActive(hoveredSlot.getItem());
      } else {
         return false;
      }
   }

   public static void renderHoverPreview(GuiGraphicsExtractor graphics, Slot hoveredSlot, int mouseX, int mouseY) {
      if (isPreviewActive(hoveredSlot)) {
         ItemStack stack = hoveredSlot.getItem();
         String hoverName = stack.getHoverName().getString().replaceAll("§.", "").trim();
         String storageKey = identifyStorageKeyFromItem(hoverName, stack);
         InventorySnapshot snapshot = findMatchingSnapshot(storageKey);
         drawStoragePreviewTooltip(graphics, snapshot, mouseX, mouseY);
      }
   }

   private static String identifyStorageKey(String title) {
      String lower = title.toLowerCase();
      if (lower.contains("ender chest")) {
         int pageNum = extractPageNumber(title);
         return pageNum != -1 ? "enderchest" + pageNum : "enderchest1";
      } else if (lower.contains("backpack")) {
         int slotNum = extractSlotNumber(title);
         return slotNum != -1 ? "backpackslot" + slotNum : title;
      } else {
         return null;
      }
   }

   private static String identifyStorageKeyFromItem(String hoverName, ItemStack stack) {
      String lower = hoverName.toLowerCase();
      if (lower.contains("ender chest")) {
         int pageNum = extractPageNumber(hoverName);
         return pageNum != -1 ? "enderchest" + pageNum : "enderchest1";
      } else if (lower.contains("backpack") || lower.contains("greater backpack") || lower.contains("jumbo backpack") || lower.contains("small backpack") || lower.contains("medium backpack") || lower.contains("large backpack")) {
         int slotNum = extractSlotNumber(hoverName);
         return slotNum != -1 ? "backpackslot" + slotNum : hoverName;
      } else {
         return null;
      }
   }

   private static int extractSlotNumber(String text) {
      Matcher m = Pattern.compile("(?:slot\\s*#?\\s*|backpack\\s+(?:slot\\s*#?\\s*)?)(\\d+)", 2).matcher(text);
      if (m.find()) {
         try {
            return Integer.parseInt(m.group(1));
         } catch (NumberFormatException var3) {
         }
      }
      Matcher m2 = Pattern.compile("#(\\d+)").matcher(text);
      if (m2.find()) {
         try {
            return Integer.parseInt(m2.group(1));
         } catch (NumberFormatException var4) {
         }
      }

      return -1;
   }

   private static int extractPageNumber(String text) {
      Matcher m = Pattern.compile("(?:\\(|page\\s*)(\\d+)(?:/|\\s*\\)|$)", 2).matcher(text);
      if (m.find()) {
         try {
            return Integer.parseInt(m.group(1));
         } catch (NumberFormatException var3) {
         }
      }

      return -1;
   }

   private static String getProfilePrefix() {
      Minecraft mc = Minecraft.getInstance();
      String userUuid = mc.player != null ? mc.player.getUUID().toString().replace("-", "").toLowerCase() : "unknown";
      String profileId = SkyblockUtils.currentProfileId;
      return profileId != null && !profileId.isEmpty() ? userUuid + "_" + profileId.replace("-", "").toLowerCase() + "_" : userUuid + "_";
   }

   private static InventorySnapshot findMatchingSnapshot(String searchKey) {
      String prefix = getProfilePrefix();
      String cleanSearch = cleanKey(searchKey);
      InventorySnapshot direct = (InventorySnapshot)storageCache.get(prefix + cleanSearch);
      if (direct != null) {
         return direct;
      } else if (cleanSearch.contains("enderchest")) {
         int pageNum = extractPageNumber(searchKey);
         if (pageNum != -1) {
            InventorySnapshot pageMatch = (InventorySnapshot)storageCache.get(prefix + "enderchest" + pageNum);
            if (pageMatch != null) {
               return pageMatch;
            }
         }

         return (InventorySnapshot)storageCache.get(prefix + "enderchest1");
      } else {
         int slotNum = extractSlotNumber(searchKey);
         if (slotNum != -1) {
            InventorySnapshot slotMatch = (InventorySnapshot)storageCache.get(prefix + "backpackslot" + slotNum);
            if (slotMatch != null) {
               return slotMatch;
            }

            slotMatch = (InventorySnapshot)storageCache.get(prefix + "backpack" + slotNum);
            if (slotMatch != null) {
               return slotMatch;
            }

            for(Map.Entry<String, InventorySnapshot> entry : storageCache.entrySet()) {
               if (((String)entry.getKey()).startsWith(prefix) && ((String)entry.getKey()).contains("backpack")) {
                  int entrySlot = extractSlotNumber((String)entry.getKey());
                  if (entrySlot == slotNum) {
                     return (InventorySnapshot)entry.getValue();
                  }
               }
            }
         }

         return null;
      }
   }

   private static String cleanKey(String key) {
      return key.toLowerCase().replaceAll("[^a-z0-9]", "");
   }

   private static void drawStoragePreviewTooltip(GuiGraphicsExtractor g, InventorySnapshot snapshot, int mouseX, int mouseY) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      String cacheKey = cleanKey(snapshot.guiName);
      ParsedTooltipCache cachedParsed = (ParsedTooltipCache)parsedItemCache.get(cacheKey);
      if (cachedParsed == null) {
         Map<Integer, ItemStack> items = new HashMap();
         int maxIndex = -1;
         RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());

         for(String itemStr : snapshot.itemData) {
            String[] parts = itemStr.split("\\|", 2);
            if (parts.length == 2) {
               try {
                  int idx = Integer.parseInt(parts[0]);
                  if ("DUMMY".equals(parts[1])) {
                     if (idx > maxIndex) {
                        maxIndex = idx;
                     }
                  } else {
                     CompoundTag tag = TagParser.parseCompoundFully(parts[1]);
                     ItemStack stack = (ItemStack)ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
                     if (!stack.isEmpty()) {
                        items.put(idx, stack);
                        if (idx > maxIndex) {
                           maxIndex = idx;
                        }
                     }
                  }
               } catch (Exception var26) {
               }
            }
         }

         cachedParsed = new ParsedTooltipCache(items, maxIndex);
         parsedItemCache.put(cacheKey, cachedParsed);
      }

      Map<Integer, ItemStack> items = cachedParsed.items;
      int maxIndex = cachedParsed.maxIndex;
      int totalSlots = maxIndex < 0 ? 18 : (int)Math.ceil((double)(maxIndex + 1) / (double)9.0F) * 9;
      int cols = 9;
      int rows = Math.max(1, totalSlots / cols);
      int pad = 6;
      int width = cols * 18 + pad * 2;
      int headerHeight = 16;
      int height = rows * 18 + headerHeight + pad * 2;
      int x = mouseX + 12;
      int y = mouseY - 12;
      if (x + width > mc.getWindow().getGuiScaledWidth()) {
         x = mouseX - width - 12;
      }

      if (y + height > mc.getWindow().getGuiScaledHeight()) {
         y = mc.getWindow().getGuiScaledHeight() - height - 4;
      }

      if (y < 4) {
         y = 4;
      }

      g.fill(x, y, x + width, y + height, -267382768);
      g.fill(x + 1, y + 1, x + width - 1, y + height - 1, -266001115);
      g.fill(x + 2, y + 2, x + width - 2, y + height - 2, -266856424);
      g.text(font, "§7" + snapshot.guiName, x + pad, y + pad, -1, true);
      int gridY = y + pad + headerHeight;

      for(int r = 0; r < rows; ++r) {
         for(int c = 0; c < cols; ++c) {
            int slotIdx = r * 9 + c;
            int slotX = x + pad + c * 18;
            int slotY = gridY + r * 18;
            g.fill(slotX, slotY, slotX + 16, slotY + 16, -15921907);
            g.fill(slotX + 1, slotY + 1, slotX + 16, slotY + 16, -13290187);
            g.fill(slotX + 1, slotY + 1, slotX + 15, slotY + 15, -14935012);
            ItemStack stack = (ItemStack)items.get(slotIdx);
            if (stack != null && !stack.isEmpty()) {
               g.item(stack, slotX, slotY);
               g.itemDecorations(font, stack, slotX, slotY);
            }
         }
      }

   }

   private static class ParsedTooltipCache {
      final Map<Integer, ItemStack> items;
      final int maxIndex;

      ParsedTooltipCache(Map<Integer, ItemStack> items, int maxIndex) {
         this.items = items;
         this.maxIndex = maxIndex;
      }
   }
}
