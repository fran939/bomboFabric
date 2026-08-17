package me.bombo.bomboaddons.features;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class StorageTracker {
   private static final File STORAGE_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "bomboaddons_storage.json");
   public static final Map<String, Map<Integer, String>> storageData = new ConcurrentHashMap();
   private static final Map<String, Map<Integer, String>> uncompressedCache = new ConcurrentHashMap();
   public static long lastUpdateTime = 0L;
   private static long lastSave = 0L;
   public static BlockPos lastClickedBlockPos = null;
   private static long lastSaveAttempt = 0L;
   private static long lastPlayerInvUpdate = 0L;

   public static void init() {
      if (STORAGE_FILE.exists()) {
         try {
            FileReader reader = new FileReader(STORAGE_FILE);

            try {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
               if (root.has("containers")) {
                  JsonObject containers = root.getAsJsonObject("containers");

                  for(String containerName : containers.keySet()) {
                     JsonObject container = containers.getAsJsonObject(containerName);
                     Map<Integer, String> slots = new ConcurrentHashMap();
                     if (container.has("items")) {
                        for(JsonElement elem : container.getAsJsonArray("items")) {
                           JsonObject itemObj = elem.getAsJsonObject();
                           int slot = itemObj.get("slot").getAsInt();
                           String nbt = itemObj.get("nbt").getAsString();
                           slots.put(slot, nbt);
                        }
                     }

                     storageData.put(containerName, slots);
                  }
               }

               List<String> toRemove = new ArrayList();
               Map<String, Map<Integer, String>> toAdd = new HashMap();

               for(String key : storageData.keySet()) {
                  if (key.contains(" @ ") && !key.startsWith("Island Chest @ ")) {
                     String var10000 = key.substring(key.indexOf(" @ ") + 3);
                     String newKey = "Island Chest @ " + var10000;
                     toRemove.add(key);
                     toAdd.put(newKey, (Map)storageData.get(key));
                  }
               }

               for(String key : toRemove) {
                  storageData.remove(key);
               }

               storageData.putAll(toAdd);
               List<String> duplicates = new ArrayList();

               for(String key : storageData.keySet()) {
                  if (!duplicates.contains(key) && key.startsWith("Island Chest @ ")) {
                     try {
                        String[] parts = key.substring(15).split(",");
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int z = Integer.parseInt(parts[2].trim());
                        BlockPos pos = new BlockPos(x, y, z);

                        for(Direction d : Plane.HORIZONTAL) {
                           BlockPos adj = pos.relative(d);
                           int var43 = adj.getX();
                           String adjKey = "Island Chest @ " + var43 + ", " + adj.getY() + ", " + adj.getZ();
                           if (storageData.containsKey(adjKey) && !duplicates.contains(adjKey)) {
                              if (adj.compareTo(pos) < 0) {
                                 duplicates.add(key);
                              } else {
                                 duplicates.add(adjKey);
                              }
                           }
                        }
                     } catch (Exception var17) {
                     }
                  }
               }

               for(String key : duplicates) {
                  storageData.remove(key);
               }

               boolean cleaned = false;

               for(Map<Integer, String> slotsMap : storageData.values()) {
                  Iterator<Map.Entry<Integer, String>> it = slotsMap.entrySet().iterator();

                  while(it.hasNext()) {
                     String nbt = (String)((Map.Entry)it.next()).getValue();
                     if (nbt.contains("\"minecraft:black_stained_glass_pane\"") && nbt.contains("hide_tooltip:1b") && nbt.contains("text:\"\"")) {
                        it.remove();
                        cleaned = true;
                     }
                  }
               }

               if (!toRemove.isEmpty() || !duplicates.isEmpty() || cleaned) {
                  save();
               }

               CompletableFuture.runAsync(() -> {
                  boolean migrated = false;

                  for(Map<Integer, String> slotsMap : storageData.values()) {
                     for(Map.Entry<Integer, String> entry : slotsMap.entrySet()) {
                        String nbt = (String)entry.getValue();
                        if (nbt != null && !nbt.startsWith("B64:") && !nbt.isEmpty()) {
                           try {
                              CompoundTag tag = TagParser.parseCompoundFully(nbt);
                              ByteArrayOutputStream baos = new ByteArrayOutputStream();
                              NbtIo.writeCompressed(tag, baos);
                              entry.setValue("B64:" + Base64.getEncoder().encodeToString(baos.toByteArray()));
                              migrated = true;
                           } catch (Exception var8) {
                           }
                        }
                     }
                  }

                  if (migrated) {
                     save();
                  }

               });
               lastUpdateTime = System.currentTimeMillis();
            } catch (Throwable var18) {
               try {
                  reader.close();
               } catch (Throwable var16) {
                  var18.addSuppressed(var16);
               }

               throw var18;
            }

            reader.close();
         } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to load storage cache", e);
         }

      }
   }

   public static synchronized void save() {
      if (System.currentTimeMillis() - lastSaveAttempt >= 2000L) {
         lastSaveAttempt = System.currentTimeMillis();
         lastUpdateTime = System.currentTimeMillis();

         try {
            JsonObject root = new JsonObject();
            JsonObject containers = new JsonObject();

            for(Map.Entry<String, Map<Integer, String>> entry : storageData.entrySet()) {
               JsonObject containerObj = new JsonObject();
               JsonArray items = new JsonArray();

               for(Map.Entry<Integer, String> slotEntry : ((Map<Integer, String>)(Map<?, ?>)entry.getValue()).entrySet()) {
                  JsonObject itemObj = new JsonObject();
                  itemObj.addProperty("slot", (Number)slotEntry.getKey());
                  itemObj.addProperty("nbt", (String)slotEntry.getValue());
                  items.add(itemObj);
               }

               containerObj.add("items", items);
               containers.add((String)entry.getKey(), containerObj);
            }

            root.add("containers", containers);
            CompletableFuture.runAsync(() -> {
               try {
                  STORAGE_FILE.getParentFile().mkdirs();
                  FileWriter writer = new FileWriter(STORAGE_FILE);

                  try {
                     (new GsonBuilder()).setPrettyPrinting().create().toJson(root, writer);
                  } catch (Throwable var5) {
                     try {
                        writer.close();
                     } catch (Throwable x2) {
                        var5.addSuppressed(x2);
                     }

                     throw var5;
                  }

                  writer.close();
               } catch (Exception e) {
                  Bomboaddons.LOGGER.error("[BomboAddons] Failed to save storage cache", e);
               }

            });
         } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to build storage cache JSON", e);
         }

      }
   }

   public static void onGuiTick() {
      Minecraft mc = Minecraft.getInstance();
      Screen var2 = mc.screen;
      if (var2 instanceof AbstractContainerScreen<?> screen) {
         String title = screen.getTitle().getString().replaceAll("§.", "").trim();
         if (isTrackableContainer(title)) {
            boolean changed = false;
            if (SkyblockUtils.getLocation().equals("Private Island") && lastClickedBlockPos != null && (title.startsWith("Chest") || title.startsWith("Large Chest") || title.startsWith("Small Chest"))) {
               int var10000 = lastClickedBlockPos.getX();
               title = "Island Chest @ " + var10000 + ", " + lastClickedBlockPos.getY() + ", " + lastClickedBlockPos.getZ();

               for(Direction d : Plane.HORIZONTAL) {
                  BlockPos adj = lastClickedBlockPos.relative(d);
                  var10000 = adj.getX();
                  String oldKey = "Island Chest @ " + var10000 + ", " + adj.getY() + ", " + adj.getZ();
                  if (storageData.containsKey(oldKey)) {
                     storageData.remove(oldKey);
                     changed = true;
                  }
               }
            }

            if (title.startsWith("Museum ➜")) {
               title = "Museum";
            }

            Map<Integer, String> slots = (Map)storageData.computeIfAbsent(title, (k) -> new ConcurrentHashMap());
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
            Map<Integer, String> uncompressedSlots = (Map)uncompressedCache.computeIfAbsent(title, (k) -> new ConcurrentHashMap());

            for(Slot slot : screen.getMenu().slots) {
               if (slot.container != mc.player.getInventory()) {
                  ItemStack stack = slot.getItem();
                  String uncompressedNbtStr = "";
                  if (!stack.isEmpty()) {
                     String name = ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
                     if (!name.isEmpty() && !name.contains("Empty ") && !name.contains("Locked") && !name.equals("Back") && !name.equals("Close") && !name.contains(" Page") && !name.equals("Go Back")) {
                        try {
                           Tag tag = (Tag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                           uncompressedNbtStr = tag.toString();
                        } catch (Exception var17) {
                           uncompressedNbtStr = "";
                        }
                     } else {
                        uncompressedNbtStr = "";
                     }
                  }

                  String existingUncompressed = (String)uncompressedSlots.get(slot.index);
                  if ((existingUncompressed != null || !uncompressedNbtStr.isEmpty()) && (existingUncompressed == null || !existingUncompressed.equals(uncompressedNbtStr))) {
                     if (uncompressedNbtStr.isEmpty()) {
                        uncompressedSlots.remove(slot.index);
                        slots.remove(slot.index);
                     } else {
                        uncompressedSlots.put(slot.index, uncompressedNbtStr);

                        try {
                           Tag tag = (Tag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                           if (tag instanceof CompoundTag) {
                              CompoundTag ct = (CompoundTag)tag;
                              ByteArrayOutputStream baos = new ByteArrayOutputStream();
                              NbtIo.writeCompressed(ct, baos);
                              String nbtStr = "B64:" + Base64.getEncoder().encodeToString(baos.toByteArray());
                              slots.put(slot.index, nbtStr);
                           } else {
                              slots.put(slot.index, tag.toString());
                           }
                        } catch (Exception var16) {
                           slots.put(slot.index, uncompressedNbtStr);
                        }
                     }

                     if (!changed) {
                        Bomboaddons.LOGGER.info("[BomboAddons] Storage changed in GUI at slot " + slot.index + " in " + title);
                     }

                     changed = true;
                  }
               }
            }

            if (changed) {
               save();
            }
         }
      }

   }

   private static boolean isTrackableContainer(String title) {
      if (title.startsWith("Ender Chest (")) {
         return true;
      } else if (title.contains("Backpack (Slot #")) {
         return true;
      } else if (!title.contains("Pets") && !title.equals("Pets")) {
         if (title.contains("Accessory Bag")) {
            return true;
         } else if (title.endsWith(" Sack") && !title.equals("Sacks")) {
            return true;
         } else if (title.equals("Fishing Bag")) {
            return true;
         } else if (title.equals("Potion Bag")) {
            return true;
         } else if (title.equals("Quiver")) {
            return true;
         } else if (title.equals("Time Pocket")) {
            return true;
         } else if (title.contains("Armor Sets")) {
            return true;
         } else if (title.contains("Equipment Sets")) {
            return true;
         } else if (title.equals("Personal Vault")) {
            return true;
         } else if (title.startsWith("Museum ➜")) {
            return true;
         } else {
            return title.equals("Chest") || title.equals("Large Chest") || title.equals("Small Chest");
         }
      } else {
         return true;
      }
   }

   public static String[] getDisplayLocAndCommand(String containerName) {
      String displayLoc = containerName;
      String cmd = "";
      if (containerName.startsWith("Ender Chest (")) {
         String num = containerName.replaceAll("[^0-9]", "");
         if (num.length() > 0) {
            displayLoc = "Ender Chest " + num.charAt(0);
            cmd = "/enderchest " + num.charAt(0);
         }
      } else if (containerName.contains("Backpack (Slot #")) {
         String num = containerName.replaceAll("[^0-9]", "");
         displayLoc = "Backpack " + num;
         cmd = "/backpack " + num;
      } else if (!containerName.contains("Pets") && !containerName.equals("Pets")) {
         if (containerName.contains("Accessory Bag")) {
            Matcher m = Pattern.compile("Accessory Bag \\((\\d+)/\\d+\\)").matcher(containerName);
            if (m.find()) {
               displayLoc = "Accessory Bag " + m.group(1);
            }

            cmd = "/accessorybag";
         } else if (containerName.endsWith(" Sack")) {
            cmd = "/sax";
         } else if (containerName.equals("Fishing Bag")) {
            cmd = "/fishingbag";
         } else if (containerName.equals("Potion Bag")) {
            cmd = "/potionbag";
         } else if (containerName.equals("Quiver")) {
            cmd = "/quiver";
         } else if (containerName.equals("Time Pocket")) {
            cmd = "/timepocket";
         } else if (containerName.contains("Armor Sets")) {
            cmd = "/armor";
         } else if (containerName.contains("Equipment Sets")) {
            cmd = "/equipment";
         } else if (containerName.equals("Personal Vault")) {
            cmd = "/bank";
         } else if (containerName.equals("Museum")) {
            cmd = "/warp museum";
         } else if (containerName.contains(" @ ")) {
            String var10000 = containerName.substring(containerName.indexOf(" @ ") + 3);
            displayLoc = "Island Chest @ " + var10000;
         }
      } else {
         cmd = "/pets";
      }

      return new String[]{displayLoc, cmd};
   }

   public static void updatePlayerInventory(Minecraft mc) {
      if (mc.player != null) {
         if (System.currentTimeMillis() - lastPlayerInvUpdate >= 10000L) {
            lastPlayerInvUpdate = System.currentTimeMillis();
            boolean changed = false;
            Map<Integer, String> slots = (Map)storageData.computeIfAbsent("Inventory", (k) -> new ConcurrentHashMap());
            Map<Integer, String> uncompressedSlots = (Map)uncompressedCache.computeIfAbsent("Inventory", (k) -> new ConcurrentHashMap());
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());

            for(int i = 0; i < mc.player.getInventory().getContainerSize(); ++i) {
               ItemStack stack = mc.player.getInventory().getItem(i);
               String uncompressedNbtStr = "";
               if (!stack.isEmpty()) {
                  String name = ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
                  if (!name.contains("Empty ") && !name.contains("Locked") && !name.equals("Back") && !name.equals("Close") && !name.contains(" Page") && !name.equals("Go Back")) {
                     try {
                        Tag tag = (Tag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                        uncompressedNbtStr = tag.toString();
                     } catch (Exception var13) {
                        uncompressedNbtStr = "";
                     }
                  }
               }

               String existingUncompressed = (String)uncompressedSlots.get(i);
               if ((existingUncompressed != null || !uncompressedNbtStr.isEmpty()) && (existingUncompressed == null || !existingUncompressed.equals(uncompressedNbtStr))) {
                  if (uncompressedNbtStr.isEmpty()) {
                     uncompressedSlots.remove(i);
                     slots.remove(i);
                  } else {
                     uncompressedSlots.put(i, uncompressedNbtStr);

                     try {
                        Tag tag = (Tag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                        if (tag instanceof CompoundTag) {
                           CompoundTag ct = (CompoundTag)tag;
                           ByteArrayOutputStream baos = new ByteArrayOutputStream();
                           NbtIo.writeCompressed(ct, baos);
                           String nbtStr = "B64:" + Base64.getEncoder().encodeToString(baos.toByteArray());
                           slots.put(i, nbtStr);
                        } else {
                           slots.put(i, tag.toString());
                        }
                     } catch (Exception var14) {
                        slots.put(i, uncompressedNbtStr);
                     }
                  }

                  changed = true;
               }
            }

            if (changed) {
               save();
            }

         }
      }
   }
}
