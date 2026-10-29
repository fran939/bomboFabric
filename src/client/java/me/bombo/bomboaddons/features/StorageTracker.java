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
import java.util.Locale;
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
   public static final File UNIFIED_STORAGE_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "bomboaddons/storage.json");
   private static final File LEGACY_STORAGE_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "bomboaddons_storage.json");
   private static String loadedProfileId = "";
   public static final Map<String, Map<String, Map<Integer, String>>> profilesData = new ConcurrentHashMap<>();
   public static final Map<String, Map<Integer, String>> storageData = new ConcurrentHashMap<>();
   private static final Map<String, Map<Integer, String>> uncompressedCache = new ConcurrentHashMap<>();
   public static long lastUpdateTime = 0L;
   private static long lastSave = 0L;
   public static BlockPos lastClickedBlockPos = null;
   private static long lastSaveAttempt = 0L;
   private static long lastPlayerInvUpdate = 0L;

   public static String getActiveProfileId() {
      if (SkyblockUtils.currentProfileId != null && !SkyblockUtils.currentProfileId.trim().isEmpty()) {
         return SkyblockUtils.currentProfileId.trim().toLowerCase(Locale.ROOT);
      }
      if (loadedProfileId != null && !loadedProfileId.trim().isEmpty() && !loadedProfileId.equalsIgnoreCase("default")) {
         return loadedProfileId;
      }
      try {
         me.bombo.bomboaddons.BomboConfig.Settings s = me.bombo.bomboaddons.BomboConfig.get();
         if (s != null && s.lastProfileId != null && !s.lastProfileId.trim().isEmpty()) {
            return s.lastProfileId.trim().toLowerCase(Locale.ROOT);
         }
      } catch (Throwable ignored) {}
      return "default";
   }

   public static File getStorageFile() {
      File dir = UNIFIED_STORAGE_FILE.getParentFile();
      if (!dir.exists()) {
         dir.mkdirs();
      }
      return UNIFIED_STORAGE_FILE;
   }

   public static File getStorageFile(String profileId) {
      return getStorageFile();
   }

   public static synchronized void onProfileChanged(String newProfileId) {
      if (newProfileId == null || newProfileId.trim().isEmpty()) return;
      String pid = newProfileId.trim().toLowerCase(Locale.ROOT);
      if (pid.equalsIgnoreCase(loadedProfileId)) return;
      syncStorageDataToProfilesMap();
      saveImmediate();
      storageData.clear();
      uncompressedCache.clear();
      slotHashCache.clear();
      loadedProfileId = pid;
      loadActiveProfileFromMap(pid);
      lastUpdateTime = System.currentTimeMillis();
      me.bombo.bomboaddons.gui.GlobalStorageScreen.invalidateCache();
      StorageChestWaypoints.clear();
      me.bombo.bomboaddons.BlockHighlight.targetChestPosList.clear();
      me.bombo.bomboaddons.SlotHighlight.clearTargetSlot();
   }

   public static void init() {
      loadAll();
   }

   private static Map<String, Map<Integer, String>> parseContainers(JsonObject containers) {
      Map<String, Map<Integer, String>> result = new ConcurrentHashMap<>();
      if (containers == null) return result;
      for (String containerName : containers.keySet()) {
         JsonObject container = containers.getAsJsonObject(containerName);
         Map<Integer, String> slots = new ConcurrentHashMap<>();
         if (container.has("items")) {
            for (JsonElement elem : container.getAsJsonArray("items")) {
               JsonObject itemObj = elem.getAsJsonObject();
               int slot = itemObj.get("slot").getAsInt();
               String nbt = itemObj.get("nbt").getAsString();
               slots.put(slot, nbt);
            }
         }
         result.put(containerName, slots);
      }
      return result;
   }

   public static synchronized void loadAll() {
      profilesData.clear();
      File storageFile = getStorageFile();
      boolean loaded = false;

      if (storageFile.exists()) {
         try (FileReader reader = new FileReader(storageFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("profiles")) {
               JsonObject profilesObj = root.getAsJsonObject("profiles");
               for (String pId : profilesObj.keySet()) {
                  JsonObject pObj = profilesObj.getAsJsonObject(pId);
                  if (pObj.has("containers")) {
                     profilesData.put(pId.toLowerCase(Locale.ROOT), parseContainers(pObj.getAsJsonObject("containers")));
                  }
               }
               loaded = true;
            } else if (root.has("containers")) {
               profilesData.put("default", parseContainers(root.getAsJsonObject("containers")));
               loaded = true;
            }
         } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to load unified storage.json", e);
         }
      }

      if (!loaded && LEGACY_STORAGE_FILE.exists()) {
         try (FileReader reader = new FileReader(LEGACY_STORAGE_FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("profiles")) {
               JsonObject profilesObj = root.getAsJsonObject("profiles");
               for (String pId : profilesObj.keySet()) {
                  JsonObject pObj = profilesObj.getAsJsonObject(pId);
                  if (pObj.has("containers")) {
                     profilesData.put(pId.toLowerCase(Locale.ROOT), parseContainers(pObj.getAsJsonObject("containers")));
                  }
               }
            } else if (root.has("containers")) {
               profilesData.put("default", parseContainers(root.getAsJsonObject("containers")));
            }
         } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to load legacy storage file", e);
         }
      }

      File storageDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "bomboaddons/storage");
      if (storageDir.exists() && storageDir.isDirectory()) {
         File[] pFiles = storageDir.listFiles((dir, name) -> name.endsWith(".json"));
         if (pFiles != null) {
            for (File pf : pFiles) {
               String fname = pf.getName();
               String pId = fname.substring(0, fname.length() - 5).toLowerCase(Locale.ROOT);
               if (!profilesData.containsKey(pId)) {
                  try (FileReader reader = new FileReader(pf)) {
                     JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                     if (root.has("containers")) {
                        profilesData.put(pId, parseContainers(root.getAsJsonObject("containers")));
                     }
                  } catch (Exception ignored) {}
               }
            }
         }
      }

      String activePid = getActiveProfileId();
      loadedProfileId = activePid;
      loadActiveProfileFromMap(activePid);
   }

   public static synchronized void loadActiveProfileFromMap(String pid) {
      storageData.clear();
      uncompressedCache.clear();
      slotHashCache.clear();
      if (pid != null && profilesData.containsKey(pid)) {
         Map<String, Map<Integer, String>> containers = profilesData.get(pid);
         for (Map.Entry<String, Map<Integer, String>> entry : containers.entrySet()) {
            storageData.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
         }
      }

      cleanStorageData();

      CompletableFuture.runAsync(() -> {
         boolean migrated = false;
         for (Map<Integer, String> slotsMap : storageData.values()) {
            for (Map.Entry<Integer, String> entry : slotsMap.entrySet()) {
               String nbt = entry.getValue();
               if (nbt != null && !nbt.startsWith("B64:") && !nbt.isEmpty()) {
                  try {
                     CompoundTag tag = TagParser.parseCompoundFully(nbt);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     NbtIo.writeCompressed(tag, baos);
                     entry.setValue("B64:" + Base64.getEncoder().encodeToString(baos.toByteArray()));
                     migrated = true;
                  } catch (Exception ignored) {}
               }
            }
         }
         if (migrated) {
            save();
         }
      });
      lastUpdateTime = System.currentTimeMillis();
   }

   private static synchronized void cleanStorageData() {
      List<String> toRemove = new ArrayList<>();
      Map<String, Map<Integer, String>> toAdd = new HashMap<>();

      for (String key : storageData.keySet()) {
         if (key.contains(" @ ") && !key.startsWith("Island Chest @ ") && !key.startsWith("Island Double Chest @ ")) {
            String coords = key.substring(key.indexOf(" @ ") + 3);
            String newKey = "Island Chest @ " + coords;
            toRemove.add(key);
            toAdd.put(newKey, storageData.get(key));
         }
      }

      for (String key : toRemove) {
         storageData.remove(key);
      }
      storageData.putAll(toAdd);

      List<String> duplicates = new ArrayList<>();
      for (String key : storageData.keySet()) {
         if (!duplicates.contains(key) && key.startsWith("Island Chest @ ")) {
            try {
               String[] parts = key.substring(15).split(",");
               int x = Integer.parseInt(parts[0].trim());
               int y = Integer.parseInt(parts[1].trim());
               int z = Integer.parseInt(parts[2].trim());
               BlockPos pos = new BlockPos(x, y, z);
               for (Direction d : Plane.HORIZONTAL) {
                  BlockPos adj = pos.relative(d);
                  String adjKey = "Island Chest @ " + adj.getX() + ", " + adj.getY() + ", " + adj.getZ();
                  if (storageData.containsKey(adjKey) && !duplicates.contains(adjKey)) {
                     if (adj.compareTo(pos) < 0) {
                        duplicates.add(key);
                     } else {
                        duplicates.add(adjKey);
                     }
                  }
               }
            } catch (Exception ignored) {}
         }
      }

      for (String key : duplicates) {
         storageData.remove(key);
      }

      for (Map<Integer, String> slotsMap : storageData.values()) {
         Iterator<Map.Entry<Integer, String>> it = slotsMap.entrySet().iterator();
         while (it.hasNext()) {
            String nbt = it.next().getValue();
            if (nbt.contains("\"minecraft:black_stained_glass_pane\"") && nbt.contains("hide_tooltip:1b") && nbt.contains("text:\"\"")) {
               it.remove();
            }
         }
      }
   }

   private static synchronized void syncStorageDataToProfilesMap() {
      if (loadedProfileId == null || loadedProfileId.trim().isEmpty()) return;
      Map<String, Map<Integer, String>> currentMap = new ConcurrentHashMap<>();
      for (Map.Entry<String, Map<Integer, String>> entry : storageData.entrySet()) {
         currentMap.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
      }
      profilesData.put(loadedProfileId, currentMap);
   }

   private static JsonObject buildRootJson() {
      syncStorageDataToProfilesMap();
      JsonObject root = new JsonObject();
      JsonObject profilesObj = new JsonObject();

      for (Map.Entry<String, Map<String, Map<Integer, String>>> pEntry : profilesData.entrySet()) {
         JsonObject profileObj = new JsonObject();
         JsonObject containersObj = new JsonObject();

         for (Map.Entry<String, Map<Integer, String>> cEntry : pEntry.getValue().entrySet()) {
            JsonObject containerObj = new JsonObject();
            JsonArray itemsArray = new JsonArray();

            for (Map.Entry<Integer, String> sEntry : cEntry.getValue().entrySet()) {
               JsonObject itemObj = new JsonObject();
               itemObj.addProperty("slot", sEntry.getKey());
               itemObj.addProperty("nbt", sEntry.getValue());
               itemsArray.add(itemObj);
            }

            containerObj.add("items", itemsArray);
            containersObj.add(cEntry.getKey(), containerObj);
         }

         profileObj.add("containers", containersObj);
         profilesObj.add(pEntry.getKey(), profileObj);
      }

      root.add("profiles", profilesObj);
      return root;
   }

   public static synchronized void saveImmediate() {
      try {
         File storageFile = getStorageFile();
         storageFile.getParentFile().mkdirs();
         JsonObject root = buildRootJson();
         try (FileWriter writer = new FileWriter(storageFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
         }
      } catch (Exception e) {
         Bomboaddons.LOGGER.error("[BomboAddons] Failed to save storage cache immediately", e);
      }
   }

   public static synchronized void save() {
      if (System.currentTimeMillis() - lastSaveAttempt >= 2000L) {
         lastSaveAttempt = System.currentTimeMillis();
         lastUpdateTime = System.currentTimeMillis();

         try {
            JsonObject root = buildRootJson();
            File storageFile = getStorageFile();
            CompletableFuture.runAsync(() -> {
               try {
                  storageFile.getParentFile().mkdirs();
                  try (FileWriter writer = new FileWriter(storageFile)) {
                     new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
                  }
               } catch (Exception e) {
                  Bomboaddons.LOGGER.error("[BomboAddons] Failed to save storage cache", e);
               }
            });
         } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to build storage cache JSON", e);
         }
      }
   }

   private static long lastGuiScanTime = 0L;
   private static final Map<String, Map<Integer, Integer>> slotHashCache = new ConcurrentHashMap<>();

   public static void onGuiTick() {
      long now = System.currentTimeMillis();
      if (now - lastGuiScanTime < 500L) {
         return;
      }
      lastGuiScanTime = now;

      Minecraft mc = Minecraft.getInstance();
      Screen var2 = mc.gui.screen();
      if (var2 instanceof AbstractContainerScreen<?> screen) {
         String title = screen.getTitle().getString().replaceAll("§.", "").trim();
         if (isTrackableContainer(title)) {
            boolean changed = false;
            if (("Private Island".equals(SkyblockUtils.getLocation()) || "Garden".equals(SkyblockUtils.getLocation())) && lastClickedBlockPos != null && isChestContainerTitle(title)) {
               boolean isDouble = false;
               BlockPos partnerPos = null;
               try {
                  if (mc.level != null) {
                     net.minecraft.world.level.block.state.BlockState bs = mc.level.getBlockState(lastClickedBlockPos);
                     if (bs.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                        net.minecraft.world.level.block.state.properties.ChestType cType = bs.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.CHEST_TYPE)
                           ? bs.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.CHEST_TYPE)
                           : net.minecraft.world.level.block.state.properties.ChestType.SINGLE;
                        if (cType != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                           Direction connectedDir = net.minecraft.world.level.block.ChestBlock.getConnectedDirection(bs);
                           partnerPos = lastClickedBlockPos.relative(connectedDir);
                           isDouble = true;
                        }
                     }
                  }
               } catch (Throwable ignored) {}

               if (isDouble && partnerPos != null) {
                  int minX = Math.min(lastClickedBlockPos.getX(), partnerPos.getX());
                  int maxX = Math.max(lastClickedBlockPos.getX(), partnerPos.getX());
                  int minY = Math.min(lastClickedBlockPos.getY(), partnerPos.getY());
                  int maxY = Math.max(lastClickedBlockPos.getY(), partnerPos.getY());
                  int minZ = Math.min(lastClickedBlockPos.getZ(), partnerPos.getZ());
                  int maxZ = Math.max(lastClickedBlockPos.getZ(), partnerPos.getZ());
                  title = "Island Double Chest @ " + minX + ", " + minY + ", " + minZ + " & " + maxX + ", " + maxY + ", " + maxZ;

                  List<String> toClean = new ArrayList<>();
                  for (String k : storageData.keySet()) {
                     if ((k.startsWith("Island Chest @ ") || k.startsWith("Island Double Chest @ ")) && !k.equals(title)) {
                        if (k.contains(String.valueOf(lastClickedBlockPos.getX())) || k.contains(String.valueOf(partnerPos.getX()))) {
                           if (k.contains(String.valueOf(lastClickedBlockPos.getZ())) || k.contains(String.valueOf(partnerPos.getZ()))) {
                              toClean.add(k);
                           }
                        }
                     }
                  }
                  for (String oldKey : toClean) {
                     storageData.remove(oldKey);
                     slotHashCache.remove(oldKey);
                     uncompressedCache.remove(oldKey);
                     changed = true;
                  }
               } else {
                  int cx = lastClickedBlockPos.getX();
                  title = "Island Chest @ " + cx + ", " + lastClickedBlockPos.getY() + ", " + lastClickedBlockPos.getZ();

                  for (Direction d : Plane.HORIZONTAL) {
                     BlockPos adj = lastClickedBlockPos.relative(d);
                     String oldKey = "Island Chest @ " + adj.getX() + ", " + adj.getY() + ", " + adj.getZ();
                     if (storageData.containsKey(oldKey)) {
                        storageData.remove(oldKey);
                        slotHashCache.remove(oldKey);
                        uncompressedCache.remove(oldKey);
                        changed = true;
                     }
                  }
               }
            }

            if (title.startsWith("Museum ➜")) {
               title = "Museum";
            }

            Map<Integer, String> slots = (Map)storageData.computeIfAbsent(title, (k) -> new ConcurrentHashMap());
            Map<Integer, Integer> hashes = (Map)slotHashCache.computeIfAbsent(title, (k) -> new ConcurrentHashMap());
            RegistryOps<Tag> ops = null;
            Map<Integer, String> uncompressedSlots = (Map)uncompressedCache.computeIfAbsent(title, (k) -> new ConcurrentHashMap());

            for(Slot slot : screen.getMenu().slots) {
               if (slot.container != mc.player.getInventory()) {
                  ItemStack stack = slot.getItem();
                  int stackFingerprint = stack.isEmpty() ? 0 : (stack.getItem().hashCode() * 31 + stack.getCount()) * 31 + stack.getComponentsPatch().hashCode();
                  Integer cachedHash = hashes.get(slot.index);
                  if (cachedHash != null && cachedHash.intValue() == stackFingerprint) {
                     continue;
                  }
                  hashes.put(slot.index, stackFingerprint);

                  String uncompressedNbtStr = "";
                  if (!stack.isEmpty()) {
                     String name = ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
                     if (!name.isEmpty() && !name.contains("Empty ") && !name.contains("Locked") && !name.equals("Back") && !name.equals("Close") && !name.contains(" Page") && !name.equals("Go Back")) {
                        boolean isEmptySack = false;
                        if (title.contains("Sack") && mc.level != null && mc.player != null) {
                           try {
                              List<net.minecraft.network.chat.Component> tooltip = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                              for (net.minecraft.network.chat.Component c : tooltip) {
                                 String line = ChatFormatting.stripFormatting(c.getString()).trim().toLowerCase(Locale.ROOT);
                                 if (line.contains("empty sack") || line.startsWith("stored: 0/")) {
                                    isEmptySack = true;
                                    break;
                                 }
                              }
                           } catch (Throwable ignored) {}
                        }
                        if (isEmptySack) {
                           uncompressedNbtStr = "";
                        } else {
                           try {
                              if (ops == null && mc.level != null) {
                                 ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
                              }
                              if (ops != null) {
                                 Tag tag = (Tag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                                 uncompressedNbtStr = tag.toString();
                              }
                           } catch (Exception var17) {
                              uncompressedNbtStr = "";
                           }
                        }
                     } else {
                        uncompressedNbtStr = "";
                     }
                  }

                  boolean hasStored = slots.containsKey(slot.index) || uncompressedSlots.containsKey(slot.index);
                  if (uncompressedNbtStr.isEmpty()) {
                     if (hasStored) {
                        uncompressedSlots.remove(slot.index);
                        slots.remove(slot.index);
                        if (!changed) {
                           Bomboaddons.LOGGER.info("[BomboAddons] Storage removed empty item in GUI at slot " + slot.index + " in " + title);
                        }
                        changed = true;
                     }
                  } else {
                     String existingUncompressed = (String)uncompressedSlots.get(slot.index);
                     if (existingUncompressed == null || !existingUncompressed.equals(uncompressedNbtStr)) {
                        uncompressedSlots.put(slot.index, uncompressedNbtStr);

                        try {
                           if (ops == null && mc.level != null) {
                              ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
                           }
                           Tag tag = ops != null ? (Tag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow() : null;
                           if (tag instanceof CompoundTag) {
                              CompoundTag ct = (CompoundTag)tag;
                              ByteArrayOutputStream baos = new ByteArrayOutputStream();
                              NbtIo.writeCompressed(ct, baos);
                              String nbtStr = "B64:" + Base64.getEncoder().encodeToString(baos.toByteArray());
                              slots.put(slot.index, nbtStr);
                           } else if (tag != null) {
                              slots.put(slot.index, tag.toString());
                           } else {
                              slots.put(slot.index, uncompressedNbtStr);
                           }
                        } catch (Exception var16) {
                           slots.put(slot.index, uncompressedNbtStr);
                        }

                        if (!changed) {
                           Bomboaddons.LOGGER.info("[BomboAddons] Storage changed in GUI at slot " + slot.index + " in " + title);
                        }
                        changed = true;
                     }
                  }
               }
            }

            if (changed) {
               save();
            }
         }
      }

   }

   public static boolean isChestContainerTitle(String title) {
      if (title == null) return false;
      String t = title.toLowerCase(Locale.ROOT).trim();
      if (t.startsWith("chest") || t.startsWith("large chest") || t.startsWith("small chest")
            || t.startsWith("cofre") || t.startsWith("cofre grande") || t.startsWith("cofre pequeño")
            || t.startsWith("island chest")) {
         return true;
      }
      try {
         String transChest = net.minecraft.network.chat.Component.translatable("container.chest").getString().toLowerCase(Locale.ROOT);
         String transDouble = net.minecraft.network.chat.Component.translatable("container.chestDouble").getString().toLowerCase(Locale.ROOT);
         if (t.startsWith(transChest) || t.startsWith(transDouble)) return true;
      } catch (Throwable ignored) {}
      return false;
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
            return isChestContainerTitle(title);
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

   public static synchronized void clearCurrentProfileStorage() {
      storageData.clear();
      uncompressedCache.clear();
      slotHashCache.clear();
      if (loadedProfileId != null && !loadedProfileId.isEmpty()) {
         profilesData.remove(loadedProfileId);
      }
      StorageChestWaypoints.clear();
      me.bombo.bomboaddons.BlockHighlight.targetChestPosList.clear();
      me.bombo.bomboaddons.SlotHighlight.clearTargetSlot();
      saveImmediate();
      me.bombo.bomboaddons.gui.GlobalStorageScreen.invalidateCache();
   }

   public static synchronized int[] scanAndCleanStaleChests() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return new int[]{0, 0, storageData.size()};

      List<String> staleKeys = new ArrayList<>();
      int scanned = 0;

      for (String key : storageData.keySet()) {
         if (key.startsWith("Island Chest @ ")) {
            BlockPos pos = StorageChestWaypoints.parsePosition(key);
            if (pos != null && mc.level.isLoaded(pos)) {
               scanned++;
               net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
               if (!StorageChestWaypoints.isContainer(state)) {
                  staleKeys.add(key);
               }
            }
         } else if (key.startsWith("Island Double Chest @ ")) {
            BlockPos[] pair = StorageChestWaypoints.parseDoublePosition(key);
            if (pair != null && pair.length == 2) {
               boolean p0Loaded = mc.level.isLoaded(pair[0]);
               boolean p1Loaded = mc.level.isLoaded(pair[1]);
               if (p0Loaded && p1Loaded) {
                  scanned++;
                  net.minecraft.world.level.block.state.BlockState s0 = mc.level.getBlockState(pair[0]);
                  net.minecraft.world.level.block.state.BlockState s1 = mc.level.getBlockState(pair[1]);
                  if (!StorageChestWaypoints.isContainer(s0) || !StorageChestWaypoints.isContainer(s1)) {
                     staleKeys.add(key);
                  }
               }
            }
         }
      }

      for (String k : staleKeys) {
         storageData.remove(k);
         slotHashCache.remove(k);
         uncompressedCache.remove(k);
      }

      if (!staleKeys.isEmpty()) {
         saveImmediate();
         me.bombo.bomboaddons.gui.GlobalStorageScreen.invalidateCache();
         StorageChestWaypoints.verify(mc);
      }

      return new int[]{scanned, staleKeys.size(), storageData.size()};
   }
}
