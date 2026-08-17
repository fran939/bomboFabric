package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.features.StorageTracker;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

@Environment(EnvType.CLIENT)
public class LF {
   private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap();
   private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
   private static final Set<String> pendingPreFetches = ConcurrentHashMap.newKeySet();
   private static final int MAX_RESULTS = 300;
   private static final HttpClient CLIENT;
   private static volatile String cachedUsername;
   private static volatile String cachedUuid;
   private static volatile String cachedJson;
   private static volatile long cacheTime;

   public static void searchLocal(String query) {
      if (Minecraft.getInstance().player != null) {
         String name = Minecraft.getInstance().player.getName().getString();
         show(removeColors(name), query, false);
      }

   }

   public static void show(String username, String query, boolean coopMode) {
      if (username.equalsIgnoreCase(cachedUsername) && cachedJson != null && cachedUuid != null && !cachedUuid.isEmpty() && System.currentTimeMillis() - cacheTime < 60000L) {
         sendMessage("&7Looking up &b" + username + (coopMode ? " &d(Coop Mode)" : "") + "&7 (Cached)...");
         SearchContext ctx = new SearchContext(cachedJson, cachedUuid, username, coopMode);
         Minecraft.getInstance().execute(() -> handleResponse(username, query, ctx));
      } else {
         sendMessage("&7Looking up &b" + username + (coopMode ? " &d(Coop Mode)" : "") + "&7...");
         getUuid(username).thenCompose((uuid) -> {
            if (uuid == null) {
               sendMessage("&cError: Could not find UUID for " + username);
               return CompletableFuture.completedFuture(null);
            } else {
               String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
               NAME_CACHE.put(cleanUuid, username);
               return getFeatureData(username, cleanUuid).thenApply((json) -> {
                  if (json != null && !json.isEmpty()) {
                     cachedUsername = username;
                     cachedUuid = cleanUuid;
                     cachedJson = json;
                     cacheTime = System.currentTimeMillis();
                     return new SearchContext(json, cleanUuid, username, coopMode);
                  } else {
                     sendMessage("&cError: Could not fetch data for " + username);
                     return null;
                  }
               });
            }
         }).thenAccept(ctxx -> {
            SearchContext ctx = (SearchContext)ctxx;
            if (ctxx != null && ctxx.json != null) {
               Minecraft.getInstance().execute(() -> handleResponse(username, query, ctx));
            }

         }).exceptionally((e) -> {
            sendMessage("&cError during search: " + e.getMessage());
            return null;
         });
      }
   }

   public static void preFetchSelf() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         String username = removeColors(mc.player.getName().getString());
         if (username != null && !username.isEmpty()) {
            if (!username.equalsIgnoreCase(cachedUsername) || cachedJson == null || System.currentTimeMillis() - cacheTime >= 60000L) {
               String lowerName = username.toLowerCase();
               if (pendingPreFetches.add(lowerName)) {
                  getUuid(username).thenCompose((uuid) -> {
                     if (uuid == null) {
                        return CompletableFuture.completedFuture(null);
                     } else {
                        String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
                        return getFeatureData(username, cleanUuid).thenApply((json) -> {
                           if (json != null && !json.isEmpty()) {
                              cachedUsername = username;
                              cachedUuid = cleanUuid;
                              cachedJson = json;
                              cacheTime = System.currentTimeMillis();
                           }

                           return null;
                        });
                     }
                  }).whenComplete((res, ex) -> pendingPreFetches.remove(lowerName));
               }
            }
         }
      }
   }

   private static void handleResponse(String username, String query, SearchContext ctx) {
      try {
         JsonObject root = JsonParser.parseString(ctx.json).getAsJsonObject();
         String lowerQuery = query.toLowerCase();
         boolean searchLore = false;
         if (lowerQuery.startsWith("lore:") || lowerQuery.startsWith("l:")) {
            searchLore = true;
            lowerQuery = lowerQuery.substring(lowerQuery.indexOf(":") + 1).trim();
         }

         JsonElement searchTarget = root;
         String pathPrefix = "";
         if (root.has("profiles") && root.get("profiles").isJsonArray()) {
            JsonArray profiles = root.getAsJsonArray("profiles");
            int activeIndex = -1;
            if (BomboConfig.get().lbDebug) {
               sendMessage("&b[Debug] Profiles found in API JSON:");
            }

            for(int i = 0; i < profiles.size(); ++i) {
               JsonElement p = profiles.get(i);
               if (p.isJsonObject()) {
                  JsonObject pObj = p.getAsJsonObject();
                  String cuteName = pObj.has("cute_name") ? pObj.get("cute_name").getAsString() : "Unknown";
                  String profileId = pObj.has("profile_id") ? pObj.get("profile_id").getAsString() : "Unknown";
                  boolean selected = pObj.has("selected") && pObj.get("selected").isJsonPrimitive() && pObj.get("selected").getAsBoolean();
                  long lastSave = pObj.has("last_save") ? pObj.get("last_save").getAsLong() : -1L;
                  if (BomboConfig.get().lbDebug) {
                     sendMessage("  &7- &e" + cuteName + " &7(" + profileId + ") - Selected: " + (selected ? "&aYes" : "&cNo") + " &7- Last Save: &d" + lastSave);
                  }

                  if (selected) {
                     activeIndex = i;
                  }
               }
            }

            if (activeIndex == -1) {
               long maxSave = -1L;

               for(int i = 0; i < profiles.size(); ++i) {
                  JsonElement p = profiles.get(i);
                  if (p.isJsonObject()) {
                     JsonObject pObj = p.getAsJsonObject();
                     long save = pObj.has("last_save") ? pObj.get("last_save").getAsLong() : -1L;
                     if (save > maxSave) {
                        maxSave = save;
                        activeIndex = i;
                     }
                  }
               }

               if (activeIndex != -1 && BomboConfig.get().lbDebug) {
                  sendMessage("&b[Debug] No profile was explicitly marked as selected. Falling back to most recently saved profile index: " + activeIndex);
               }
            }

            if (activeIndex == -1 && profiles.size() > 0) {
               activeIndex = 0;
               if (BomboConfig.get().lbDebug) {
                  sendMessage("&b[Debug] No profile marked selected and no timestamps. Defaulting to index 0.");
               }
            }

            if (activeIndex != -1) {
               searchTarget = profiles.get(activeIndex);
               pathPrefix = "profiles > " + activeIndex;
               JsonObject activeObj = searchTarget.getAsJsonObject();
               String activeName = activeObj.has("cute_name") ? activeObj.get("cute_name").getAsString() : "Unknown";
               if (BomboConfig.get().lbDebug) {
                  sendMessage("&b[Debug] Choosing profile: &6" + activeName + " &7(index " + activeIndex + ") for search.");
               }
            }
         } else if (BomboConfig.get().lbDebug) {
            sendMessage("&b[Debug] API JSON did not contain 'profiles' array. Searching root JSON directly.");
         }

         AtomicInteger matchCount = new AtomicInteger(0);
         sendMessage("&eSearch Results for '&f" + lowerQuery + "&e' in &b" + username + (ctx.coopMode ? " (Coop)" : "") + "&e:");
         searchJsonRecursive(searchTarget, pathPrefix, lowerQuery, searchLore, matchCount, ctx, false, false, 300, false);
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && removeColors(mc.player.getName().getString()).equalsIgnoreCase(username)) {
            searchStorageTracker(lowerQuery, searchLore, matchCount);
         }

         if (matchCount.get() == 0) {
            sendMessage("&cCould find 0 results for '&f" + lowerQuery + "&c'.");
         }
      } catch (Exception e) {
         sendMessage("&cError parsing data: " + e.getMessage());
      }

   }

   private static void searchStorageTracker(String query, boolean searchLore, AtomicInteger matchCount) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         RegistryAccess registryAccess = mc.level.registryAccess();
         RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registryAccess);

         for(Map.Entry<String, Map<Integer, String>> containerEntry : StorageTracker.storageData.entrySet()) {
            String containerName = (String)containerEntry.getKey();

            for(Map.Entry<Integer, String> slotEntry : ((Map<Integer, String>)(Map<?, ?>)containerEntry.getValue()).entrySet()) {
               if (matchCount.get() >= 300) {
                  return;
               }

               String nbtStr = (String)slotEntry.getValue();
               if (nbtStr != null && !nbtStr.isEmpty()) {
                  try {
                     CompoundTag tag = TagParser.parseCompoundFully(nbtStr);
                     ItemStack stack = (ItemStack)ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
                     if (!stack.isEmpty()) {
                        String friendlyName = stack.getHoverName().getString();
                        String cleanName = removeColors(friendlyName).toLowerCase();
                        boolean matched = cleanName.contains(query);
                        if (!matched && searchLore) {
                           matched = nbtStr.toLowerCase().contains(query);
                        }

                        if (matched) {
                           int count = stack.getCount();
                           matchCount.incrementAndGet();
                           String fullName = "§a" + friendlyName + (count > 1 ? " §7x" + count : "");
                           String[] locCmd = StorageTracker.getDisplayLocAndCommand(containerName);
                           String displayLoc = locCmd[0];
                           String cmd = locCmd[1];
                           MutableComponent var10000 = Component.literal(fullName + " §8- §e" + displayLoc);
                           Style var10001 = Style.EMPTY.withHoverEvent(SBECommands.createHoverEvent("§a" + friendlyName + "\n§7Click to highlight in storage!"));
                           String var10004 = String.valueOf(slotEntry.getKey());
                           Component msg = var10000.withStyle(var10001.withClickEvent(new ClickEvent.RunCommand("/bombo_highlight_slot " + var10004 + (cmd.isEmpty() ? "" : " " + cmd))));
                           mc.player.sendSystemMessage(msg);
                        }
                     }
                  } catch (Exception var23) {
                  }
               }
            }
         }

      }
   }

   private static void searchJsonRecursive(JsonElement element, String path, String query, boolean searchLore, AtomicInteger matchCount, SearchContext ctx, boolean isInsideMembersNode, boolean toolkitsOnly, int limit, boolean isBorrowed) {
      if (matchCount.get() < limit) {
         if (!element.isJsonObject() || !path.endsWith("sacks_counts") && !path.contains("sacks_counts")) {
            if (element.isJsonArray() && path.endsWith("pets")) {
               String finalPath = path;

               for(JsonElement itemEl : element.getAsJsonArray()) {
                  if (itemEl.isJsonObject()) {
                     JsonObject pet = itemEl.getAsJsonObject();
                     if (pet.has("type") && pet.has("tier")) {
                        String type = pet.get("type").getAsString();
                        String tier = pet.get("tier").getAsString();
                        double exp = pet.has("exp") ? pet.get("exp").getAsDouble() : (double)0.0F;
                        boolean active = pet.has("active") && pet.get("active").getAsBoolean();
                        String friendlyType = toTitleCase(type.replace("_", " "));
                        String friendlyTier = toTitleCase(tier.replace("_", " "));
                        String displayName = friendlyTier + " " + friendlyType;
                        if (active) {
                           displayName = displayName + " §a[Active]";
                        }

                        if (displayName.toLowerCase().contains(query) || friendlyType.toLowerCase().contains(query)) {
                           int idx = matchCount.incrementAndGet();
                           String expText = "";
                           if (exp >= (double)1000000.0F) {
                              expText = String.format("%.1fM exp", exp / (double)1000000.0F);
                           } else if (exp >= (double)1000.0F) {
                              expText = String.format("%.1fK exp", exp / (double)1000.0F);
                           } else {
                              expText = String.format("%.0f exp", exp);
                           }

                           String color = getPetColor(tier);
                           String fullName = color + displayName + " §7(" + expText + ")";
                           resolveName(extractLastUuidFromPath(finalPath)).thenAccept((memberName) -> Minecraft.getInstance().execute(() -> {
                                 String mUser = memberName != null && !memberName.isEmpty() ? memberName : ctx.targetUsername;
                                 String selfName = Minecraft.getInstance().getUser().getName();
                                 String labelSuffix = "Pets";
                                 if (!mUser.equalsIgnoreCase(selfName)) {
                                    labelSuffix = labelSuffix + ") (" + mUser;
                                 }

                                 MutableComponent link = Component.literal(fullName);
                                 ClickEvent c = createClickEventRobust("RUN_COMMAND", "/pets");
                                 Style style = Style.EMPTY.withClickEvent(c);
                                 link.setStyle(style);
                                 MutableComponent contComponent = translate(" &r&7(" + labelSuffix + ")");
                                 Component msg = translate("&7#" + idx + " ").append(link).append(contComponent);
                                 sendMessage(msg);
                              }));
                        }
                     }
                  }
               }

            } else {
               if (element.isJsonArray()) {
                  JsonArray arr = element.getAsJsonArray();

                  for(int i = 0; i < arr.size(); ++i) {
                     searchJsonRecursive(arr.get(i), path + " > " + i, query, searchLore, matchCount, ctx, isInsideMembersNode, toolkitsOnly, limit, isBorrowed);
                  }
               } else if (element.isJsonObject()) {
                  JsonObject obj = element.getAsJsonObject();
                  if (obj.has("borrowing") && obj.get("borrowing").isJsonPrimitive() && obj.get("borrowing").getAsBoolean()) {
                     return;
                  }

                  if (obj.has("data") && obj.get("data").isJsonPrimitive()) {
                     decodeAndSearch(path, obj.get("data").getAsString(), query, searchLore, matchCount, ctx, toolkitsOnly, limit, false);
                  } else {
                     for(Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        String key = (String)entry.getKey();
                        boolean nextIsMember = key.equalsIgnoreCase("members") && !path.contains("museum");
                        if (isInsideMembersNode && !ctx.coopMode) {
                           String raw = key.replace("-", "").toLowerCase();
                           if (!raw.equals(ctx.targetUuid)) {
                              continue;
                           }
                        }

                        searchJsonRecursive((JsonElement)entry.getValue(), path.isEmpty() ? key : path + " > " + key, query, searchLore, matchCount, ctx, nextIsMember, toolkitsOnly, limit, false);
                     }
                  }
               }

            }
         } else {
            String finalPath = path;
            JsonObject obj = element.getAsJsonObject();

            for(Map.Entry<String, JsonElement> entry : obj.entrySet()) {
               String itemId = (String)entry.getKey();
               int count = 0;

               try {
                  count = ((JsonElement)entry.getValue()).getAsInt();
               } catch (Exception var27) {
               }

               if (count > 0) {
                  String friendlyName = itemId.replace("_", " ").toLowerCase();
                  friendlyName = toTitleCase(friendlyName);
                  if (friendlyName.toLowerCase().contains(query)) {
                     int idx = matchCount.incrementAndGet();
                     String fullName = "§a" + friendlyName + " §7x" + count;
                     resolveName(extractLastUuidFromPath(finalPath)).thenAccept((memberName) -> Minecraft.getInstance().execute(() -> {
                           String mUser = memberName != null && !memberName.isEmpty() ? memberName : ctx.targetUsername;
                           String selfName = Minecraft.getInstance().getUser().getName();
                           String labelSuffix = "Sacks";
                           if (!mUser.equalsIgnoreCase(selfName)) {
                              labelSuffix = labelSuffix + ") (" + mUser;
                           }

                           MutableComponent link = Component.literal(fullName);
                           ClickEvent c = createClickEventRobust("RUN_COMMAND", "/sacks");
                           Style style = Style.EMPTY.withClickEvent(c);
                           link.setStyle(style);
                           MutableComponent contComponent = translate(" &r&7(" + labelSuffix + ")");
                           Component msg = translate("&7#" + idx + " ").append(link).append(contComponent);
                           sendMessage(msg);
                        }));
                  }
               }
            }

         }
      }
   }

   private static void decodeAndSearch(String containerPath, String base64, String query, boolean searchLore, AtomicInteger matchCount, SearchContext ctx, boolean toolkitsOnly, int limit, boolean isBorrowed) {
      try {
         byte[] bytes = Base64.getDecoder().decode(base64);

         CompoundTag nbt;
         try {
            nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
         } catch (Exception var14) {
            nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
         }

         if (nbt == null) {
            return;
         }

         if (nbt.contains("i")) {
            ListTag list = nbt.get("i") instanceof ListTag ? (ListTag)nbt.get("i") : null;
            if (list != null) {
               for(int i = 0; i < list.size(); ++i) {
                  CompoundTag item = list.getCompound(i).orElse(null);
                  if (item != null && !item.isEmpty()) {
                     processItemNbt(item, i, containerPath, query, searchLore, matchCount, ctx, limit, isBorrowed);
                  }
               }
            }
         } else if (nbt.contains("id") || nbt.contains("tag")) {
            processItemNbt(nbt, 0, containerPath, query, searchLore, matchCount, ctx, limit, isBorrowed);
         }
      } catch (Exception e) {
         if (BomboConfig.get().apiDebug) {
            sendMessage("&c[Debug] Decode failed for " + containerPath + ": " + e.getMessage());
         }
      }

   }

   private static void processItemNbt(CompoundTag item, int slotIndex, String containerPath, String query, boolean searchLore, AtomicInteger matchCount, SearchContext ctx, int limit, boolean isBorrowed) {
      if (matchCount.get() < limit) {
         CompoundTag tag = item.getCompound("tag").orElse(null);
         if (tag != null) {
            CompoundTag display = tag.getCompound("display").orElse(null);
            if (display != null) {
               String fullName = display.getString("Name").orElse("");
               if (!fullName.isEmpty()) {
                  StringBuilder lore = (new StringBuilder(fullName)).append("\n");
                  ListTag loreList = display.get("Lore") instanceof ListTag ? (ListTag)display.get("Lore") : null;
                  if (loreList != null) {
                     for(int j = 0; j < loreList.size(); ++j) {
                        lore.append(loreList.getString(j).orElse("")).append("\n");
                     }
                  }

                  boolean match = removeColors(fullName).toLowerCase().contains(query);
                  if (!match && searchLore && removeColors(lore.toString()).toLowerCase().contains(query)) {
                     match = true;
                  }

                  if (match) {
                     int idx = matchCount.incrementAndGet();
                     Object[] info = getContainerInfo(containerPath, slotIndex, removeColors(fullName));
                     int finalSlotIndex = slotIndex + (Integer)info[2];
                     String finalCmdBase = (String)info[1];
                     String finalContBase = (String)info[0];
                     int finalOffset = (Integer)info[2];
                     resolveName(extractLastUuidFromPath(containerPath)).thenAccept((memberName) -> Minecraft.getInstance().execute(() -> {
                           String cont = finalContBase;
                           String fullCmd = finalCmdBase;
                           String mUser = memberName != null && !memberName.isEmpty() ? memberName : ctx.targetUsername;
                           String selfName = Minecraft.getInstance().getUser().getName();
                           boolean isOthers = !mUser.equalsIgnoreCase(selfName);
                           if (isOthers) {
                              cont = finalContBase + " (" + memberName + ")";
                              if ((finalCmdBase.startsWith("/enderchest") || finalCmdBase.startsWith("/backpack") || finalCmdBase.startsWith("/museum") || finalCmdBase.startsWith("/bank") || finalCmdBase.startsWith("/ec")) && !finalCmdBase.contains(mUser)) {
                                 fullCmd = finalCmdBase + " " + mUser;
                              }
                           }

                           HoverEvent h = createHoverEventRobust(lore.toString());
                           int highlightSlotTemp = finalSlotIndex;
                           if (finalCmdBase.startsWith("/ec ") || finalCmdBase.startsWith("/ec") || finalCmdBase.startsWith("/enderchest")) {
                              highlightSlotTemp = slotIndex % 45 + 9;
                           }

                           String clickCmd = "/bombo_highlight_slot " + highlightSlotTemp + " " + fullCmd;
                           if (fullCmd.startsWith("/museum")) {
                              clickCmd = "/bombo_museum_click " + mUser + " " + finalSlotIndex;
                           } else if (isOthers) {
                              clickCmd = "/b view " + ctx.targetUsername + " \"" + containerPath + "\" " + slotIndex;
                           }

                           ClickEvent c = createClickEventRobust("RUN_COMMAND", clickCmd);
                           MutableComponent link = Component.literal(fullName);
                           Style style = Style.EMPTY;
                           if (h != null) {
                              style = style.withHoverEvent(h);
                           }

                           if (c != null) {
                              style = style.withClickEvent(c);
                           }

                           link.setStyle(style);
                           MutableComponent contComponent = translate(" &r&7(" + cont + ")");
                           if (c != null) {
                              contComponent.setStyle(Style.EMPTY.withClickEvent(c));
                           }

                           Component msg = translate("&7#" + idx + " ").append(link).append(contComponent);
                           if (BomboConfig.get().apiDebug || BomboConfig.get().lbDebug) {
                              sendMessage("&b[Debug] Found at: &7" + containerPath);
                           }

                           if (BomboConfig.get().debugMode) {
                              sendMessage("&b[Debug] " + cont + " slot " + finalSlotIndex + " (i=" + slotIndex + ", offset=" + finalOffset + ")");
                           }

                           sendMessage(msg);
                        }));
                  }

               }
            }
         }
      }
   }

   public static Object[] getContainerInfo(String raw, int itemIndex, String itemName) {
      String s = raw.toLowerCase();
      String name = "Inventory";
      String cmd = "/play sb";
      int offset = 0;
      if (s.contains("backpack")) {
         name = "Backpack";
         cmd = "/backpack";
         offset = 9;

         try {
            String[] parts = s.split(" > ");

            for(int i = 0; i < parts.length - 1; ++i) {
               if (parts[i].contains("backpack")) {
                  int num = Integer.parseInt(parts[i + 1]) + 1;
                  name = "Backpack " + num;
                  cmd = "/backpack " + num;
                  break;
               }
            }
         } catch (Exception var10) {
         }
      } else if (!s.contains("enderchest") && !s.contains("ender_chest")) {
         if (!s.contains("accessory_bag") && !s.contains("accessory") && !s.contains("talisman_bag") && !s.contains("talisman")) {
            if (s.contains("wardrobe")) {
               name = "Wardrobe";
               cmd = "/wardrobe";
            } else if (s.contains("vault")) {
               name = "Personal Vault";
               cmd = "/bank";
            } else if (s.contains("museum")) {
               name = "Museum";
               cmd = "/museum";
            } else if (s.contains("sacks")) {
               name = "Sacks";
               cmd = "/sacks";
            } else if (s.contains("quiver")) {
               name = "Quiver";
               cmd = "/quiver";
            } else if (s.contains("potion_bag")) {
               name = "Potion Bag";
               cmd = "/potionbag";
            } else if (s.contains("candy_bag")) {
               name = "Candy Bag";
               cmd = "/candybag";
            } else if (s.contains("fishing_bag")) {
               name = "Fishing Bag";
               cmd = "/fishingbag";
            }
         } else {
            name = "Accessory Bag";
            cmd = "/ab";
         }
      } else {
         name = "Ender Chest";
         cmd = "/enderchest";
         int page = itemIndex / 45 + 1;
         name = "Ender Chest " + page;
         cmd = "/ec " + page;
      }

      return new Object[]{name, cmd, offset};
   }

   public static String removeColors(String s) {
      return s == null ? "" : s.replaceAll("§.", "").replaceAll("&.", "");
   }

   private static String getPetColor(String tier) {
      if (tier == null) {
         return "§7";
      } else {
         switch (tier.toUpperCase()) {
            case "COMMON":
               return "§f";
            case "UNCOMMON":
               return "§a";
            case "RARE":
               return "§9";
            case "EPIC":
               return "§5";
            case "LEGENDARY":
               return "§6";
            case "MYTHIC":
               return "§d";
            case "SPECIAL":
            case "VERY_SPECIAL":
               return "§c";
            default:
               return "§7";
         }
      }
   }

   private static String extractLastUuidFromPath(String path) {
      String[] parts = path.split(" > ");

      for(int i = parts.length - 1; i >= 0; --i) {
         String p = parts[i].replace("-", "").toLowerCase();
         if (p.length() == 32 && UUID_PATTERN.matcher(p).matches()) {
            return p;
         }
      }

      return null;
   }

   private static CompletableFuture<UUID> getUuid(String username) {
      return username != null && !username.isEmpty() ? fetchString("https://api.ashcon.app/mojang/v2/user/" + username).thenCompose((response) -> {
         if (response != null && response.contains("\"uuid\"")) {
            try {
               JsonObject json = JsonParser.parseString(response).getAsJsonObject();
               if (json.has("uuid")) {
                  return CompletableFuture.completedFuture(UUID.fromString(json.get("uuid").getAsString()));
               }
            } catch (Exception var3) {
            }
         }

         return fetchString("https://api.mojang.com/users/profiles/minecraft/" + username).thenApply((mojangRes) -> {
            if (mojangRes == null) {
               return null;
            } else {
               try {
                  JsonObject json = JsonParser.parseString(mojangRes).getAsJsonObject();
                  if (json.has("id")) {
                     return UUID.fromString(json.get("id").getAsString().replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                  }
               } catch (Exception var2) {
               }

               return null;
            }
         });
      }) : CompletableFuture.completedFuture(null);
   }

   private static CompletableFuture<String> getFeatureData(String username, String cleanUuid) {
      String url = BomboApiUrl.getApiUrl("/data/" + username);
      if (BomboConfig.get().apiDebug) {
         sendMessage("&b[Debug] API: " + url);
      }

      return fetchString(url).thenCompose((response) -> {
         if (response != null && !response.isEmpty() && response.startsWith("{")) {
            return CompletableFuture.completedFuture(response);
         } else {
            String uuidUrl = BomboApiUrl.getApiUrl("/data/" + cleanUuid);
            if (BomboConfig.get().apiDebug) {
               sendMessage("&7[Debug] Username API failed, trying UUID: " + uuidUrl);
            }

            return fetchString(uuidUrl).thenCompose((uuidRes) -> uuidRes != null && !uuidRes.isEmpty() && uuidRes.startsWith("{") ? CompletableFuture.completedFuture(uuidRes) : fetchString("https://profile.snailify.workers.dev/?uuid=" + cleanUuid));
         }
      });
   }

   private static CompletableFuture<String> resolveName(String uuid) {
      if (uuid == null) {
         return CompletableFuture.completedFuture(null);
      } else {
         return NAME_CACHE.containsKey(uuid.toLowerCase()) ? CompletableFuture.completedFuture((String)NAME_CACHE.get(uuid.toLowerCase())) : fetchString("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid).thenCompose((response) -> {
            if (response != null && response.contains("\"name\"")) {
               try {
                  JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                  if (json.has("name")) {
                     String name = json.get("name").getAsString();
                     NAME_CACHE.put(uuid.toLowerCase(), name);
                     return CompletableFuture.completedFuture(name);
                  }
               } catch (Exception var4) {
               }
            }

            return fetchString("https://api.ashcon.app/mojang/v2/user/" + uuid).thenApply((fallbackRes) -> {
               if (fallbackRes == null) {
                  return null;
               } else {
                  try {
                     JsonObject json = JsonParser.parseString(fallbackRes).getAsJsonObject();
                     if (json.has("username")) {
                        String name = json.get("username").getAsString();
                        NAME_CACHE.put(uuid.toLowerCase(), name);
                        return name;
                     }
                  } catch (Exception var4) {
                  }

                  return null;
               }
            });
         });
      }
   }

   private static CompletableFuture<String> fetchString(String url) {
      Bomboaddons.logApiRequest(url);
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30L)).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36").GET().build();
      return CLIENT.sendAsync(request, BodyHandlers.ofString()).thenApply((res) -> res.statusCode() == 200 ? (String)res.body() : null).exceptionally((e) -> null);
   }

   private static void sendMessage(String msg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.execute(() -> mc.player.sendSystemMessage(translate(msg)));
      }

   }

   private static void sendMessage(Component msg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.execute(() -> mc.player.sendSystemMessage(msg));
      }

   }

   public static HoverEvent createHoverEventRobust(String lore) {
      return SBECommands.createHoverEvent(lore);
   }

   public static ClickEvent createClickEventRobust(String action, String value) {
      try {
         if (action.equalsIgnoreCase("RUN_COMMAND")) {
            return new ClickEvent.RunCommand(value);
         } else if (action.equalsIgnoreCase("SUGGEST_COMMAND")) {
            return new ClickEvent.SuggestCommand(value);
         } else {
            return action.equalsIgnoreCase("OPEN_URL") ? new ClickEvent.OpenUrl(URI.create(value)) : null;
         }
      } catch (Throwable var3) {
         return null;
      }
   }

   private static MutableComponent translate(String s) {
      return Component.literal(s.replace("&", "§"));
   }

   public static void printContainerInfo() {
      Minecraft mc = Minecraft.getInstance();
      Screen var2 = mc.screen;
      if (var2 instanceof AbstractContainerScreen<?> screen) {
         sendMessage("&6--- Container Diagnostic ---");
         sendMessage("&eTitle: &f" + screen.getTitle().getString());
         AbstractContainerMenu menu = screen.getMenu();
         List<Slot> slots = menu.slots;
         sendMessage("&eTotal Slots: &f" + slots.size());
         if (slots.size() > 49) {
            ItemStack timer = ((Slot)slots.get(49)).getItem();
            String var10000 = timer.getItem().toString();
            sendMessage("&eSlot 49 (Timer): &f" + var10000 + " (Foil: " + timer.hasFoil() + ")");
         }

         sendMessage("&7(Diagnostic info displayed in-game)");
      } else {
         sendMessage("&cNot in a container!");
      }

   }

   public static void showToolkit(String username, int limit) {
      sendMessage("&7Fetching toolkit data for &b" + username + "&7...");
      getUuid(username).thenCompose((uuid) -> {
         if (uuid == null) {
            return CompletableFuture.completedFuture(null);
         } else {
            String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
            return getFeatureData(username, cleanUuid).thenApply((json) -> new Object[]{cleanUuid, json});
         }
      }).thenAccept((results) -> {
         if (results != null) {
            String cleanUuid = (String)results[0];
            String json = (String)results[1];
            if (json == null) {
               Minecraft.getInstance().execute(() -> sendMessage("&cFailed to get data."));
            } else {
               try {
                  JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                  Minecraft.getInstance().execute(() -> {
                     sendMessage("&e--- Toolkit Contents for &b" + username + " &e---");
                     AtomicInteger count = new AtomicInteger(0);
                     SearchContext ctx = new SearchContext(json, cleanUuid, username, false);
                     searchJsonRecursive(root, "", "", false, count, ctx, false, true, limit, false);
                     if (count.get() == 0) {
                        sendMessage("&cNo toolkit or sack data found.");
                     }

                  });
               } catch (Exception e) {
                  Minecraft.getInstance().execute(() -> sendMessage("&cError: " + e.getMessage()));
               }

            }
         }
      });
   }

   public static void openVirtualContainer(String username, String path, int highlightSlot) {
      if (username.equalsIgnoreCase(cachedUsername) && cachedJson != null && System.currentTimeMillis() - cacheTime < 300000L) {
         processContainer(username, cachedJson, path, highlightSlot);
      } else {
         getUuid(username).thenCompose((uuid) -> {
            if (uuid == null) {
               return CompletableFuture.completedFuture(null);
            } else {
               String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
               return getFeatureData(username, cleanUuid).thenApply((json) -> new Object[]{json, cleanUuid});
            }
         }).thenAccept((res) -> {
            if (res != null) {
               String json = (String)res[0];
               String cleanUuid = (String)res[1];
               if (json != null) {
                  cachedUsername = username;
                  cachedUuid = cleanUuid;
                  cachedJson = json;
                  cacheTime = System.currentTimeMillis();
                  processContainer(username, json, path, highlightSlot);
               }
            }
         });
      }
   }

   private static void processContainer(String username, String json, String path, int highlightSlot) {
      Minecraft.getInstance().execute(() -> {
         try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement target = drillDown(root, path);
            if (target == null && path.contains("members > ")) {
               String[] segments = path.split("members > ");
               if (segments.length > 1) {
                  String localPath = segments[1];
                  if (localPath.contains(" > ")) {
                     localPath = localPath.split(" > ", 2)[1];
                     target = drillDown(root, localPath);
                     if (target == null && root.has("profile") && root.get("profile").isJsonObject()) {
                        target = drillDown(root.getAsJsonObject("profile"), localPath);
                     }

                     if (target == null && root.has("raw_profile") && root.get("raw_profile").isJsonObject()) {
                        target = drillDown(root.getAsJsonObject("raw_profile"), localPath);
                     }
                  }
               }
            }

            if (target == null) {
               String[] parts = path.split(" > ");
               if (parts.length >= 2) {
                  String containerName = parts[parts.length - 2];
                  String indexOrLeaf = parts[parts.length - 1];
                  JsonElement container = findJsonRecursively(root, containerName);
                  if (container != null) {
                     if (container.isJsonObject() && container.getAsJsonObject().has(indexOrLeaf)) {
                        target = container.getAsJsonObject().get(indexOrLeaf);
                     } else if (container.isJsonArray()) {
                        try {
                           int idx = Integer.parseInt(indexOrLeaf);
                           JsonArray arr = container.getAsJsonArray();
                           if (idx >= 0 && idx < arr.size()) {
                              target = arr.get(idx);
                           }
                        } catch (Exception var12) {
                        }
                     }
                  }
               }
            }

            if (target == null && path.contains("members > ")) {
               String uuidPart = path.split("members > ")[1].split(" > ")[0];
               String targetKey = path.substring(path.lastIndexOf(">") + 1).trim();
               JsonElement member = null;
               if (root.has("profiles") && root.get("profiles").isJsonArray()) {
                  for(JsonElement p : root.getAsJsonArray("profiles")) {
                     JsonObject pObj = p.getAsJsonObject();
                     if (pObj.has("members") && pObj.getAsJsonObject("members").has(uuidPart)) {
                        member = pObj.getAsJsonObject("members").get(uuidPart);
                        break;
                     }
                  }
               }

               if (member != null) {
                  target = findJsonRecursively(member, targetKey);
               }
            }

            String base64 = null;
            if (target != null) {
               if (target.isJsonObject() && target.getAsJsonObject().has("data")) {
                  base64 = target.getAsJsonObject().get("data").getAsString();
               } else if (target.isJsonPrimitive()) {
                  base64 = target.getAsString();
               }
            }

            if (base64 != null) {
               if (BomboConfig.get().apiDebug) {
                  sendMessage("&7[Debug] Container data found (length: " + base64.length() + "). Decoding...");
               }

               final String finalBase64 = base64;
               final int finalHighlightSlot = highlightSlot;
               CompletableFuture.supplyAsync(() -> decodeToItems(finalBase64), Minecraft.getInstance()).thenAccept((items) -> {
                  if (items != null && !items.isEmpty()) {
                     List<ItemStack> processedItems = items;
                     if (path.contains("ender_chest") || path.contains("enderchest")) {
                        int page = finalHighlightSlot / 45;
                        int start = page * 45;
                        int end = Math.min(start + 45, items.size());
                        if (start < items.size()) {
                           processedItems = new ArrayList(items.subList(start, end));
                           if (BomboConfig.get().apiDebug) {
                              sendMessage("&7[Debug] Ender Chest slice: " + start + " to " + end + " (Page " + (page + 1) + ")");
                           }
                        }
                     }
                     final List<ItemStack> finalProcessedItems = processedItems;
                     Minecraft.getInstance().execute(() -> {
                        try {
                           String title = username + "'s " + path.substring(path.lastIndexOf(">") + 1).trim();
                           VirtualContainerGUI gui = new VirtualContainerGUI(title, finalProcessedItems, finalHighlightSlot % 45, username, path);
                           Minecraft.getInstance().setScreenAndShow(gui);
                        } catch (Exception e) {
                           sendMessage("&cError opening GUI: " + e.getMessage());
                        }

                     });
                  } else {
                     sendMessage("&cNo items found in container data.");
                  }
               }).exceptionally((ex) -> {
                  sendMessage("&cError decoding container: " + ex.getMessage());
                  return null;
               });
            } else {
               sendMessage("&cCould not find container data at: " + path);
            }
         } catch (Exception e) {
            sendMessage("&cFailed to open virtual container: " + e.getMessage());
         }

      });
   }

   private static JsonElement findJsonRecursively(JsonElement root, String targetKey) {
      if (root.isJsonObject()) {
         JsonObject obj = root.getAsJsonObject();
         if (obj.has(targetKey)) {
            return obj.get(targetKey);
         }

         for(Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement res = findJsonRecursively((JsonElement)entry.getValue(), targetKey);
            if (res != null) {
               return res;
            }
         }
      } else if (root.isJsonArray()) {
         for(JsonElement e : root.getAsJsonArray()) {
            JsonElement res = findJsonRecursively(e, targetKey);
            if (res != null) {
               return res;
            }
         }
      }

      return null;
   }

   private static JsonElement drillDown(JsonObject root, String path) {
      String[] parts = path.split(" > ");
      JsonElement current = root;

      for(int i = 0; i < parts.length; ++i) {
         String key = parts[i].trim();
         if (current == null || current.isJsonNull()) {
            return null;
         }

         JsonElement next = null;
         if (current.isJsonObject()) {
            JsonObject obj = current.getAsJsonObject();
            if (obj.has(key)) {
               next = obj.get(key);
            } else {
               for(Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                  if (((String)entry.getKey()).equalsIgnoreCase(key)) {
                     next = (JsonElement)entry.getValue();
                     break;
                  }
               }

               if (next == null) {
                  for(Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                     if (((JsonElement)entry.getValue()).isJsonObject()) {
                        JsonObject sub = ((JsonElement)entry.getValue()).getAsJsonObject();
                        if (sub.has(key)) {
                           next = sub.get(key);
                           break;
                        }
                     }
                  }
               }
            }
         } else if (current.isJsonArray()) {
            try {
               int idx = Integer.parseInt(key);
               JsonArray arr = current.getAsJsonArray();
               if (idx >= 0 && idx < arr.size()) {
                  next = arr.get(idx);
               }
            } catch (Exception var11) {
            }
         }

         if (next == null) {
            if (BomboConfig.get().apiDebug) {
               String keys = current.isJsonObject() ? String.join(", ", current.getAsJsonObject().keySet()) : "N/A";
               sendMessage("&7[Debug] Failed at: " + key + ". Available: " + keys);
            }

            return null;
         }

         current = next;
      }

      return current;
   }

   public static List<ItemStack> decodeToItems(String base64) {
      List<ItemStack> result = new ArrayList();
      if (base64 != null && !base64.trim().isEmpty()) {
         try {
            byte[] bytes = Base64.getDecoder().decode(base64.trim());

            CompoundTag nbt;
            try {
               nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            } catch (Exception var6) {
               nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
            }

            if (nbt == null) {
               return result;
            }

            if (nbt.contains("i")) {
               ListTag list = (ListTag)nbt.get("i");
               if (list != null) {
                  for(int i = 0; i < list.size(); ++i) {
                     result.add(convertNbtToStack(list.getCompound(i).orElse(null)));
                  }
               }
            }
         } catch (IllegalArgumentException e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Illegal Base64 character in container data: " + e.getMessage());
         } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to decode container data", e);
         }

         return result;
      } else {
         return result;
      }
   }

   public static ItemStack convertNbtToStack(CompoundTag itemNbt) {
      if (itemNbt != null && !itemNbt.isEmpty()) {
         String idStr = "minecraft:air";

         try {
            CompoundTag modernNbt = new CompoundTag();
            String sbId = null;
            if (itemNbt.contains("tag")) {
               CompoundTag tag = itemNbt.getCompound("tag").orElse(null);
               if (tag != null) {
                  if (tag.contains("ExtraAttributes")) {
                     CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                     if (ea != null && ea.contains("id")) {
                        sbId = ea.getString("id").orElse(null);
                     }
                  }

                  if (tag.contains("SkullOwner")) {
                     idStr = "minecraft:player_head";
                     CompoundTag components = new CompoundTag();
                     components.put("minecraft:profile", (tag.getCompound("SkullOwner").orElse(new CompoundTag())).copy());
                     modernNbt.put("components", components);
                  }
               }
            }

            boolean isHead = false;
            if (itemNbt.contains("tag")) {
               CompoundTag tag = itemNbt.getCompound("tag").orElse(null);
               if (tag != null && tag.contains("SkullOwner")) {
                  isHead = true;
                  idStr = "minecraft:player_head";
               }
            }

            if (!isHead && sbId != null) {
               idStr = guessItem(sbId);
            }

            if ((idStr.equals("minecraft:chest") || idStr.equals("minecraft:air")) && itemNbt.contains("id")) {
               if (itemNbt.get("id") instanceof StringTag) {
                  String rawId = itemNbt.getString("id").orElse("minecraft:air");
                  if (!isHead) {
                     idStr = guessItem(rawId);
                  } else {
                     idStr = "minecraft:player_head";
                  }
               } else {
                  int numericId = (Integer)itemNbt.getInt("id").orElse(0);
                  if (!isHead) {
                     idStr = mapNumericId(numericId);
                  } else {
                     idStr = "minecraft:player_head";
                  }
               }
            }

            modernNbt.putString("id", idStr);
            int count = 1;
            if (itemNbt.contains("Count")) {
               count = (Byte)itemNbt.getByte("Count").orElse((byte)1);
            } else if (itemNbt.contains("count")) {
               count = (Integer)itemNbt.getInt("count").orElse(1);
            }

            modernNbt.putInt("count", count);
            ItemStack stack = ItemStack.EMPTY;
            if (Minecraft.getInstance().level != null) {
               RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
               stack = (ItemStack)ItemStack.CODEC.parse(ops, modernNbt).result().orElse(ItemStack.EMPTY);
            }

            if (stack.isEmpty()) {
               Item item = Items.CHEST;

               try {
                  label239: {
                     Object res = BuiltInRegistries.ITEM.get(Identifier.parse(idStr));
                     if (res instanceof Optional) {
                        Optional<?> o = (Optional)res;
                        if (o.isPresent()) {
                           Object inner = o.get();
                           if (inner instanceof Holder) {
                              Holder<?> h = (Holder)inner;
                              item = (Item)h.value();
                           } else if (inner instanceof Item) {
                              Item i = (Item)inner;
                              item = i;
                           }
                           break label239;
                        }
                     }

                     if (res instanceof Item) {
                        Item i = (Item)res;
                        item = i;
                     }
                  }
               } catch (Exception var18) {
               }

               stack = item.getDefaultInstance().copyWithCount(count);
            }

            if (itemNbt.contains("tag")) {
               CompoundTag tag = itemNbt.getCompound("tag").orElse(null);
               if (tag != null) {
                  if (tag.contains("display")) {
                     CompoundTag display = tag.getCompound("display").orElse(null);
                     if (display != null) {
                        if (display.contains("Name")) {
                           String name = (display.getString("Name").orElse("")).replace("&", "§");
                           stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                        }

                        if (display.contains("Lore")) {
                           ListTag loreList = (ListTag)display.get("Lore");
                           List<Component> lines = new ArrayList();

                           for(int i = 0; i < loreList.size(); ++i) {
                              lines.add(Component.literal((loreList.getString(i).orElse("")).replace("&", "§")));
                           }

                           if (!lines.isEmpty()) {
                              stack.set(DataComponents.LORE, new ItemLore(lines));
                           }
                        }
                     }
                  }

                  if (tag.contains("SkullOwner")) {
                     CompoundTag skullOwner = tag.getCompound("SkullOwner").orElse(null);
                     if (skullOwner != null) {
                        if (BomboConfig.get().apiDebug) {
                           logDebug("Raw SkullOwner: " + String.valueOf(skullOwner));
                        }

                        try {
                           if (skullOwner.contains("Properties")) {
                              CompoundTag properties = skullOwner.getCompound("Properties").orElse(null);
                              if (properties != null && properties.contains("textures")) {
                                 ListTag textures = (ListTag)properties.get("textures");

                                 for(int i = 0; i < textures.size(); ++i) {
                                    CompoundTag tex = textures.getCompound(i).orElse(null);
                                    if (tex != null) {
                                       tex.remove("Signature");
                                       tex.remove("signature");
                                    }
                                 }
                              }
                           }

                           String val = null;
                           if (skullOwner.contains("Properties") || skullOwner.contains("properties")) {
                              CompoundTag properties = skullOwner.getCompound("Properties").orElse((CompoundTag)skullOwner.getCompound("properties").orElse(null));
                              if (properties != null && (properties.contains("textures") || properties.contains("Textures"))) {
                                 ListTag textures = (ListTag)properties.get(properties.contains("textures") ? "textures" : "Textures");

                                 for(int i = 0; i < textures.size(); ++i) {
                                    CompoundTag tex = textures.getCompound(i).orElse(null);
                                    if (tex != null && (tex.contains("Value") || tex.contains("value"))) {
                                       Object v = tex.get(tex.contains("Value") ? "Value" : "value");
                                       if (v != null) {
                                          if (v instanceof String) {
                                             val = (String)v;
                                          } else {
                                             try {
                                                Method m = v.getClass().getMethod("getAsString");
                                                val = (String)m.invoke(v);
                                             } catch (Exception var16) {
                                                val = v.toString().replace("\"", "");
                                             }
                                          }
                                       }
                                       break;
                                    }
                                 }
                              }
                           }

                           if (val != null && !val.isEmpty()) {
                              ResolvableProfile rp = SkyblockItemManager.createProfile(val, (String)null);
                              if (rp != null) {
                                 stack.set(DataComponents.PROFILE, rp);
                                 if (BomboConfig.get().apiDebug) {
                                    logDebug("  Applied ResolvableProfile (Cached)");
                                 }
                              }
                           }
                        } catch (Exception e) {
                           if (BomboConfig.get().apiDebug) {
                              sendMessage("&cError applying head texture: " + e.getMessage());
                           }
                        }
                     }
                  }

                  if (tag.contains("ench") || tag.contains("ExtraAttributes") && (tag.getCompound("ExtraAttributes").orElse(new CompoundTag())).contains("enchantments")) {
                     stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                  }
               }
            }

            if (BomboConfig.get().apiDebug && stack != null) {
               String var10000 = String.valueOf(stack.getItem());
               logDebug("Final Stack: " + var10000 + " components: " + String.valueOf(stack.getComponents()));
            }

            return stack;
         } catch (Exception e) {
            Bomboaddons.LOGGER.error("Error converting NBT to Stack", e);
            if (BomboConfig.get().apiDebug) {
               logDebug("Guessing item for ID: " + idStr);
            }

            Item item = (Item)BuiltInRegistries.ITEM.get(Identifier.parse(guessItem(idStr))).map(Holder::value).orElse(Items.CHEST);
            return item.getDefaultInstance();
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static String guessItem(String id) {
      String var10000;
      switch (id.toUpperCase().replace("MINECRAFT:", "")) {
         case "EXPLOSIVE_MINECART" -> var10000 = "minecraft:tnt_minecart";
         case "SKULL_ITEM" -> var10000 = "minecraft:player_head";
         case "IRON_SPADE" -> var10000 = "minecraft:iron_shovel";
         case "DIAMOND_SPADE" -> var10000 = "minecraft:diamond_shovel";
         case "GOLD_SPADE" -> var10000 = "minecraft:gold_shovel";
         case "WOOD_SPADE" -> var10000 = "minecraft:wooden_shovel";
         case "STONE_SPADE" -> var10000 = "minecraft:stone_shovel";
         case "IRON_PICKAXE" -> var10000 = "minecraft:iron_pickaxe";
         case "DIAMOND_PICKAXE" -> var10000 = "minecraft:diamond_pickaxe";
         case "GOLD_PICKAXE" -> var10000 = "minecraft:gold_pickaxe";
         case "WOOD_PICKAXE" -> var10000 = "minecraft:wooden_pickaxe";
         case "STONE_PICKAXE" -> var10000 = "minecraft:stone_pickaxe";
         case "IRON_AXE" -> var10000 = "minecraft:iron_axe";
         case "DIAMOND_AXE" -> var10000 = "minecraft:diamond_axe";
         case "GOLD_AXE" -> var10000 = "minecraft:gold_axe";
         case "WOOD_AXE" -> var10000 = "minecraft:wooden_axe";
         case "STONE_AXE" -> var10000 = "minecraft:stone_axe";
         case "IRON_HOE" -> var10000 = "minecraft:iron_hoe";
         case "DIAMOND_HOE" -> var10000 = "minecraft:diamond_hoe";
         case "GOLD_HOE" -> var10000 = "minecraft:gold_hoe";
         case "WOOD_HOE" -> var10000 = "minecraft:wooden_hoe";
         case "STONE_HOE" -> var10000 = "minecraft:stone_hoe";
         case "IRON_SWORD" -> var10000 = "minecraft:iron_sword";
         case "DIAMOND_SWORD" -> var10000 = "minecraft:diamond_sword";
         case "GOLD_SWORD" -> var10000 = "minecraft:gold_sword";
         case "WOOD_SWORD" -> var10000 = "minecraft:wooden_sword";
         case "STONE_SWORD" -> var10000 = "minecraft:stone_sword";
         case "RAW_FISH" -> var10000 = "minecraft:cod";
         case "COOKED_FISH" -> var10000 = "minecraft:cooked_cod";
         case "INK_SACK" -> var10000 = "minecraft:ink_sac";
         case "SULPHUR" -> var10000 = "minecraft:gunpowder";
         case "NETHER_STALK" -> var10000 = "minecraft:nether_wart";
         case "POTION" -> var10000 = "minecraft:potion";
         case "EXP_BOTTLE" -> var10000 = "minecraft:experience_bottle";
         case "BOOK_AND_QUILL" -> var10000 = "minecraft:writable_book";
         case "LOG" -> var10000 = "minecraft:oak_log";
         case "LOG_2" -> var10000 = "minecraft:acacia_log";
         case "LEAVES" -> var10000 = "minecraft:oak_leaves";
         case "LEAVES_2" -> var10000 = "minecraft:acacia_leaves";
         case "WOOD" -> var10000 = "minecraft:oak_wood";
         case "WATCH" -> var10000 = "minecraft:clock";
         case "STEP" -> var10000 = "minecraft:stone_slab";
         case "WOOD_STEP" -> var10000 = "minecraft:oak_slab";
         case "STAINED_GLASS" -> var10000 = "minecraft:white_stained_glass";
         case "STAINED_GLASS_PANE" -> var10000 = "minecraft:white_stained_glass_pane";
         case "THIN_GLASS" -> var10000 = "minecraft:glass_pane";
         case "RED_ROSE" -> var10000 = "minecraft:poppy";
         case "YELLOW_FLOWER" -> var10000 = "minecraft:dandelion";
         case "LONG_GRASS" -> var10000 = "minecraft:short_grass";
         case "LEASH" -> var10000 = "minecraft:lead";
         case "PRISMARINE_SHARD" -> var10000 = "minecraft:prismarine_shard";
         case "PRISMARINE_CRYSTALS" -> var10000 = "minecraft:prismarine_crystals";
         case "CARPET" -> var10000 = "minecraft:white_carpet";
         default -> var10000 = null;
      }

      String legacyMapped = var10000;
      if (legacyMapped != null) {
         return legacyMapped;
      } else {
         String clean = id.toUpperCase().replace("MINECRAFT:", "");
         String namespaced = "minecraft:" + clean.toLowerCase();
         if (BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(namespaced))) {
            return namespaced;
         } else {
            String lower = clean.toLowerCase();
            if (lower.contains("terminator")) {
               return "minecraft:bow";
            } else if (!lower.contains("hyperion") && !lower.contains("astraea") && !lower.contains("scylla") && !lower.contains("valkyrie")) {
               if (lower.contains("juju")) {
                  return "minecraft:bow";
               } else if (!lower.contains("drill") && !lower.contains("divan")) {
                  if (!lower.contains("mithril") && !lower.contains("refined")) {
                     if (!lower.contains("titanium") && !lower.contains("gauntlet")) {
                        if (!lower.contains("lasso") && !lower.contains("lead") && !lower.contains("leash")) {
                           if (!lower.contains("pickonimbus") && !lower.contains("pioneer") && !lower.contains("flux")) {
                              if (lower.contains("sinker")) {
                                 return "minecraft:fishing_rod";
                              } else if (lower.contains("fishing")) {
                                 return "minecraft:cod";
                              } else if (!lower.contains("prismarine") && !lower.contains("shard")) {
                                 if (!lower.contains("dandelion") && !lower.contains("flower") && !lower.contains("exp_share")) {
                                    if (lower.contains("exp")) {
                                       return "minecraft:experience_bottle";
                                    } else if (lower.contains("dicer")) {
                                       return "minecraft:diamond_axe";
                                    } else if (lower.contains("zapper")) {
                                       return "minecraft:flint";
                                    } else if (lower.contains("hoe")) {
                                       return "minecraft:diamond_hoe";
                                    } else if (!lower.contains("belt") && !lower.contains("cloak") && !lower.contains("bracelet") && !lower.contains("necklace") && !lower.contains("gloves")) {
                                       if (lower.contains("sword")) {
                                          return "minecraft:iron_sword";
                                       } else if (lower.contains("bow")) {
                                          return "minecraft:bow";
                                       } else if (lower.contains("pickaxe")) {
                                          return "minecraft:diamond_pickaxe";
                                       } else if (lower.contains("axe")) {
                                          return "minecraft:iron_axe";
                                       } else if (!lower.contains("shovel") && !lower.contains("spade")) {
                                          if (lower.contains("hoe")) {
                                             return "minecraft:iron_hoe";
                                          } else if (!lower.contains("artifact") && !lower.contains("relic") && !lower.contains("talisman") && !lower.contains("ring")) {
                                             if (lower.contains("cobblestone")) {
                                                return "minecraft:cobblestone";
                                             } else if (lower.contains("diamond")) {
                                                return "minecraft:diamond";
                                             } else if (lower.contains("iron")) {
                                                return "minecraft:iron_ingot";
                                             } else if (lower.contains("gold")) {
                                                return "minecraft:gold_ingot";
                                             } else if (lower.contains("coal")) {
                                                return "minecraft:coal";
                                             } else if (lower.contains("emerald")) {
                                                return "minecraft:emerald";
                                             } else if (lower.contains("lapis")) {
                                                return "minecraft:lapis_lazuli";
                                             } else if (lower.contains("redstone")) {
                                                return "minecraft:redstone";
                                             } else if (lower.contains("slime")) {
                                                return "minecraft:slime_ball";
                                             } else if (lower.contains("flesh")) {
                                                return "minecraft:rotten_flesh";
                                             } else if (lower.contains("bone")) {
                                                return "minecraft:bone";
                                             } else if (lower.contains("eye")) {
                                                return "minecraft:spider_eye";
                                             } else if (lower.contains("string")) {
                                                return "minecraft:string";
                                             } else if (lower.contains("feather")) {
                                                return "minecraft:feather";
                                             } else if (lower.contains("leather")) {
                                                return "minecraft:leather";
                                             } else if (lower.contains("blaze")) {
                                                return "minecraft:blaze_rod";
                                             } else if (lower.contains("ghast")) {
                                                return "minecraft:ghast_tear";
                                             } else if (lower.contains("magma")) {
                                                return "minecraft:magma_cream";
                                             } else if (lower.contains("glowstone")) {
                                                return "minecraft:glowstone_dust";
                                             } else if (lower.contains("sugar")) {
                                                return "minecraft:sugar_cane";
                                             } else if (lower.contains("wheat")) {
                                                return "minecraft:wheat";
                                             } else if (lower.contains("carrot")) {
                                                return "minecraft:carrot";
                                             } else if (lower.contains("potato")) {
                                                return "minecraft:potato";
                                             } else if (lower.contains("pumpkin")) {
                                                return "minecraft:pumpkin";
                                             } else if (lower.contains("melon")) {
                                                return "minecraft:melon_slice";
                                             } else if (lower.contains("mushroom")) {
                                                return "minecraft:red_mushroom";
                                             } else if (lower.contains("cactus")) {
                                                return "minecraft:cactus";
                                             } else if (lower.contains("beef")) {
                                                return "minecraft:beef";
                                             } else if (lower.contains("pork")) {
                                                return "minecraft:porkchop";
                                             } else if (lower.contains("chicken")) {
                                                return "minecraft:chicken";
                                             } else if (lower.contains("mutton")) {
                                                return "minecraft:mutton";
                                             } else if (lower.contains("rabbit")) {
                                                return "minecraft:rabbit";
                                             } else if (lower.contains("spider")) {
                                                return "minecraft:spider_eye";
                                             } else if (lower.contains("string")) {
                                                return "minecraft:string";
                                             } else if (lower.contains("wool")) {
                                                return "minecraft:white_wool";
                                             } else if (lower.contains("pearl")) {
                                                return "minecraft:ender_pearl";
                                             } else if (lower.contains("snow")) {
                                                return "minecraft:snowball";
                                             } else if (lower.contains("ice")) {
                                                return "minecraft:ice";
                                             } else if (lower.contains("glass")) {
                                                return "minecraft:glass";
                                             } else if (lower.contains("clay")) {
                                                return "minecraft:clay_ball";
                                             } else if (lower.contains("brick")) {
                                                return "minecraft:brick";
                                             } else if (lower.contains("paper")) {
                                                return "minecraft:paper";
                                             } else if (lower.contains("book")) {
                                                return "minecraft:book";
                                             } else if (lower.contains("map")) {
                                                return "minecraft:map";
                                             } else if (lower.contains("firework")) {
                                                return "minecraft:firework_rocket";
                                             } else if (lower.contains("potion")) {
                                                return "minecraft:potion";
                                             } else if (!lower.contains("experience") && !lower.contains("exp")) {
                                                if (lower.contains("enchanted_book")) {
                                                   return "minecraft:enchanted_book";
                                                } else {
                                                   String material = "iron";
                                                   if (lower.contains("leather")) {
                                                      material = "leather";
                                                   } else if (lower.contains("gold")) {
                                                      material = "golden";
                                                   } else if (lower.contains("diamond")) {
                                                      material = "diamond";
                                                   } else if (lower.contains("netherite")) {
                                                      material = "netherite";
                                                   } else if (lower.contains("chain")) {
                                                      material = "chainmail";
                                                   } else if (lower.contains("stone")) {
                                                      material = "stone";
                                                   } else if (lower.contains("wood")) {
                                                      material = "wooden";
                                                   }

                                                   if (lower.contains("helmet")) {
                                                      return "minecraft:" + material + "_helmet";
                                                   } else if (lower.contains("chestplate")) {
                                                      return "minecraft:" + material + "_chestplate";
                                                   } else if (lower.contains("leggings")) {
                                                      return "minecraft:" + material + "_leggings";
                                                   } else if (lower.contains("boots")) {
                                                      return "minecraft:" + material + "_boots";
                                                   } else if (lower.contains("sword")) {
                                                      return "minecraft:" + material + "_sword";
                                                   } else if (lower.contains("pickaxe")) {
                                                      return "minecraft:" + material + "_pickaxe";
                                                   } else if (lower.contains("axe") && !lower.contains("pickaxe")) {
                                                      return "minecraft:" + material + "_axe";
                                                   } else if (!lower.contains("shovel") && !lower.contains("spade")) {
                                                      if (lower.contains("hoe")) {
                                                         return "minecraft:" + material + "_hoe";
                                                      } else if (lower.contains("rod")) {
                                                         return "minecraft:fishing_rod";
                                                      } else if (lower.contains("bow")) {
                                                         return "minecraft:bow";
                                                      } else if (lower.contains("egg")) {
                                                         return "minecraft:egg";
                                                      } else {
                                                         if (BomboConfig.get().apiDebug) {
                                                            Bomboaddons.LOGGER.info("[Bombo] Unknown SkyBlock ID: " + id);
                                                         }

                                                         return "minecraft:chest";
                                                      }
                                                   } else {
                                                      return "minecraft:" + material + "_shovel";
                                                   }
                                                }
                                             } else {
                                                return "minecraft:experience_bottle";
                                             }
                                          } else {
                                             return "minecraft:player_head";
                                          }
                                       } else {
                                          return "minecraft:iron_shovel";
                                       }
                                    } else {
                                       return "minecraft:leather_boots";
                                    }
                                 } else {
                                    return "minecraft:dandelion";
                                 }
                              } else {
                                 return "minecraft:prismarine_shard";
                              }
                           } else {
                              return "minecraft:iron_pickaxe";
                           }
                        } else {
                           return "minecraft:lead";
                        }
                     } else {
                        return "minecraft:iron_ingot";
                     }
                  } else {
                     return "minecraft:prismarine_crystals";
                  }
               } else {
                  return "minecraft:prismarine_shard";
               }
            } else {
               return "minecraft:iron_sword";
            }
         }
      }
   }

   private static String mapNumericId(int id) {
      String var10000;
      switch (id) {
         case 0:
            var10000 = "minecraft:air";
            break;
         case 1:
            var10000 = "minecraft:stone";
            break;
         case 2:
            var10000 = "minecraft:grass_block";
            break;
         case 3:
            var10000 = "minecraft:dirt";
            break;
         case 4:
            var10000 = "minecraft:cobblestone";
            break;
         case 5:
            var10000 = "minecraft:oak_planks";
            break;
         case 6:
            var10000 = "minecraft:oak_sapling";
            break;
         case 7:
            var10000 = "minecraft:bedrock";
            break;
         case 8:
         case 9:
         case 10:
         case 11:
         case 19:
         case 21:
         case 23:
         case 24:
         case 25:
         case 26:
         case 27:
         case 28:
         case 29:
         case 30:
         case 31:
         case 32:
         case 34:
         case 36:
         case 37:
         case 38:
         case 39:
         case 40:
         case 43:
         case 45:
         case 47:
         case 48:
         case 49:
         case 50:
         case 51:
         case 52:
         case 53:
         case 55:
         case 56:
         case 59:
         case 60:
         case 62:
         case 63:
         case 64:
         case 65:
         case 66:
         case 67:
         case 68:
         case 69:
         case 70:
         case 71:
         case 72:
         case 73:
         case 74:
         case 75:
         case 76:
         case 77:
         case 78:
         case 79:
         case 80:
         case 81:
         case 82:
         case 83:
         case 84:
         case 85:
         case 86:
         case 87:
         case 88:
         case 90:
         case 91:
         case 92:
         case 93:
         case 94:
         case 95:
         case 96:
         case 97:
         case 98:
         case 99:
         case 100:
         case 101:
         case 102:
         case 103:
         case 104:
         case 105:
         case 106:
         case 107:
         case 108:
         case 109:
         case 110:
         case 111:
         case 112:
         case 113:
         case 114:
         case 115:
         case 117:
         case 118:
         case 119:
         case 120:
         case 121:
         case 122:
         case 123:
         case 124:
         case 125:
         case 126:
         case 127:
         case 128:
         case 129:
         case 131:
         case 132:
         case 133:
         case 134:
         case 135:
         case 136:
         case 137:
         case 138:
         case 139:
         case 140:
         case 141:
         case 142:
         case 143:
         case 144:
         case 146:
         case 147:
         case 148:
         case 149:
         case 150:
         case 151:
         case 153:
         case 154:
         case 155:
         case 156:
         case 157:
         case 158:
         case 159:
         case 160:
         case 161:
         case 162:
         case 163:
         case 164:
         case 165:
         case 166:
         case 167:
         case 168:
         case 169:
         case 170:
         case 171:
         case 172:
         case 173:
         case 174:
         case 175:
         case 176:
         case 177:
         case 178:
         case 179:
         case 180:
         case 181:
         case 182:
         case 183:
         case 184:
         case 185:
         case 186:
         case 187:
         case 188:
         case 189:
         case 190:
         case 191:
         case 192:
         case 193:
         case 194:
         case 195:
         case 196:
         case 197:
         case 198:
         case 199:
         case 200:
         case 201:
         case 202:
         case 203:
         case 204:
         case 205:
         case 206:
         case 207:
         case 208:
         case 209:
         case 210:
         case 211:
         case 212:
         case 213:
         case 214:
         case 215:
         case 216:
         case 217:
         case 218:
         case 219:
         case 220:
         case 221:
         case 222:
         case 223:
         case 224:
         case 225:
         case 226:
         case 227:
         case 228:
         case 229:
         case 230:
         case 231:
         case 232:
         case 233:
         case 234:
         case 235:
         case 236:
         case 237:
         case 238:
         case 239:
         case 240:
         case 241:
         case 242:
         case 243:
         case 244:
         case 245:
         case 246:
         case 247:
         case 248:
         case 249:
         case 250:
         case 251:
         case 252:
         case 253:
         case 254:
         case 255:
         case 259:
         case 260:
         case 262:
         case 268:
         case 269:
         case 270:
         case 271:
         case 272:
         case 273:
         case 274:
         case 275:
         case 281:
         case 282:
         case 290:
         case 291:
         case 292:
         case 293:
         case 294:
         case 295:
         case 296:
         case 297:
         case 314:
         case 315:
         case 316:
         case 317:
         case 318:
         case 319:
         case 320:
         case 321:
         case 323:
         case 324:
         case 325:
         case 326:
         case 327:
         case 328:
         case 329:
         case 330:
         case 332:
         case 333:
         case 334:
         case 335:
         case 336:
         case 337:
         case 338:
         case 342:
         case 343:
         case 344:
         case 346:
         case 349:
         case 350:
         case 352:
         case 353:
         case 354:
         case 355:
         case 356:
         case 357:
         case 359:
         case 360:
         case 361:
         case 362:
         case 363:
         case 364:
         case 365:
         case 366:
         case 367:
         case 371:
         case 374:
         case 375:
         case 376:
         case 377:
         case 378:
         case 379:
         case 380:
         case 382:
         case 383:
         case 385:
         case 389:
         case 390:
         case 391:
         case 392:
         case 393:
         case 394:
         case 396:
         case 397:
         case 398:
         default:
            var10000 = "minecraft:chest";
            break;
         case 12:
            var10000 = "minecraft:sand";
            break;
         case 13:
            var10000 = "minecraft:gravel";
            break;
         case 14:
            var10000 = "minecraft:gold_ore";
            break;
         case 15:
            var10000 = "minecraft:iron_ore";
            break;
         case 16:
            var10000 = "minecraft:coal_ore";
            break;
         case 17:
            var10000 = "minecraft:oak_log";
            break;
         case 18:
            var10000 = "minecraft:oak_leaves";
            break;
         case 20:
            var10000 = "minecraft:glass";
            break;
         case 22:
            var10000 = "minecraft:lapis_block";
            break;
         case 33:
            var10000 = "minecraft:piston";
            break;
         case 35:
            var10000 = "minecraft:white_wool";
            break;
         case 41:
            var10000 = "minecraft:gold_block";
            break;
         case 42:
            var10000 = "minecraft:iron_block";
            break;
         case 44:
            var10000 = "minecraft:stone_slab";
            break;
         case 46:
            var10000 = "minecraft:tnt";
            break;
         case 54:
            var10000 = "minecraft:chest";
            break;
         case 57:
            var10000 = "minecraft:diamond_block";
            break;
         case 58:
            var10000 = "minecraft:crafting_table";
            break;
         case 61:
            var10000 = "minecraft:furnace";
            break;
         case 89:
            var10000 = "minecraft:glowstone";
            break;
         case 116:
            var10000 = "minecraft:enchanting_table";
            break;
         case 130:
            var10000 = "minecraft:ender_chest";
            break;
         case 145:
            var10000 = "minecraft:anvil";
            break;
         case 152:
            var10000 = "minecraft:redstone_block";
            break;
         case 256:
            var10000 = "minecraft:iron_shovel";
            break;
         case 257:
            var10000 = "minecraft:iron_pickaxe";
            break;
         case 258:
            var10000 = "minecraft:iron_axe";
            break;
         case 261:
            var10000 = "minecraft:bow";
            break;
         case 263:
            var10000 = "minecraft:coal";
            break;
         case 264:
            var10000 = "minecraft:diamond";
            break;
         case 265:
            var10000 = "minecraft:iron_ingot";
            break;
         case 266:
            var10000 = "minecraft:gold_ingot";
            break;
         case 267:
            var10000 = "minecraft:iron_sword";
            break;
         case 276:
            var10000 = "minecraft:diamond_sword";
            break;
         case 277:
            var10000 = "minecraft:diamond_shovel";
            break;
         case 278:
            var10000 = "minecraft:diamond_pickaxe";
            break;
         case 279:
            var10000 = "minecraft:diamond_axe";
            break;
         case 280:
            var10000 = "minecraft:stick";
            break;
         case 283:
            var10000 = "minecraft:gold_sword";
            break;
         case 284:
            var10000 = "minecraft:gold_shovel";
            break;
         case 285:
            var10000 = "minecraft:gold_pickaxe";
            break;
         case 286:
            var10000 = "minecraft:gold_axe";
            break;
         case 287:
            var10000 = "minecraft:string";
            break;
         case 288:
            var10000 = "minecraft:feather";
            break;
         case 289:
            var10000 = "minecraft:gunpowder";
            break;
         case 298:
            var10000 = "minecraft:leather_helmet";
            break;
         case 299:
            var10000 = "minecraft:leather_chestplate";
            break;
         case 300:
            var10000 = "minecraft:leather_leggings";
            break;
         case 301:
            var10000 = "minecraft:leather_boots";
            break;
         case 302:
            var10000 = "minecraft:chainmail_helmet";
            break;
         case 303:
            var10000 = "minecraft:chainmail_chestplate";
            break;
         case 304:
            var10000 = "minecraft:chainmail_leggings";
            break;
         case 305:
            var10000 = "minecraft:chainmail_boots";
            break;
         case 306:
            var10000 = "minecraft:iron_helmet";
            break;
         case 307:
            var10000 = "minecraft:iron_chestplate";
            break;
         case 308:
            var10000 = "minecraft:iron_leggings";
            break;
         case 309:
            var10000 = "minecraft:iron_boots";
            break;
         case 310:
            var10000 = "minecraft:diamond_helmet";
            break;
         case 311:
            var10000 = "minecraft:diamond_chestplate";
            break;
         case 312:
            var10000 = "minecraft:diamond_leggings";
            break;
         case 313:
            var10000 = "minecraft:diamond_boots";
            break;
         case 322:
            var10000 = "minecraft:golden_apple";
            break;
         case 331:
            var10000 = "minecraft:redstone";
            break;
         case 339:
            var10000 = "minecraft:paper";
            break;
         case 340:
            var10000 = "minecraft:book";
            break;
         case 341:
            var10000 = "minecraft:slime_ball";
            break;
         case 345:
            var10000 = "minecraft:compass";
            break;
         case 347:
            var10000 = "minecraft:clock";
            break;
         case 348:
            var10000 = "minecraft:glowstone_dust";
            break;
         case 351:
            var10000 = "minecraft:ink_sac";
            break;
         case 358:
            var10000 = "minecraft:map";
            break;
         case 368:
            var10000 = "minecraft:ender_pearl";
            break;
         case 369:
            var10000 = "minecraft:blaze_rod";
            break;
         case 370:
            var10000 = "minecraft:ghast_tear";
            break;
         case 372:
            var10000 = "minecraft:nether_wart";
            break;
         case 373:
            var10000 = "minecraft:potion";
            break;
         case 381:
            var10000 = "minecraft:ender_eye";
            break;
         case 384:
            var10000 = "minecraft:experience_bottle";
            break;
         case 386:
            var10000 = "minecraft:writable_book";
            break;
         case 387:
            var10000 = "minecraft:written_book";
            break;
         case 388:
            var10000 = "minecraft:emerald";
            break;
         case 395:
            var10000 = "minecraft:empty_map";
            break;
         case 399:
            var10000 = "minecraft:nether_star";
      }

      return var10000;
   }

   public static void logDebug(String message) {
      System.out.println("[Bombo] " + message);

      try {
         FileWriter fw = new FileWriter("bombo_debug.log", true);

         try {
            String var10001 = String.valueOf(new Date());
            fw.write("[" + var10001 + "] " + message + "\n");
         } catch (Throwable var5) {
            try {
               fw.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }

            throw var5;
         }

         fw.close();
      } catch (Exception var6) {
      }

   }

   private static String toTitleCase(String input) {
      if (input != null && !input.isEmpty()) {
         StringBuilder titleCase = new StringBuilder();
         boolean nextTitleCase = true;

         for(char c : input.toCharArray()) {
            if (!Character.isSpaceChar(c) && c != '_') {
               if (nextTitleCase) {
                  titleCase.append(Character.toTitleCase(c));
                  nextTitleCase = false;
               } else {
                  titleCase.append(Character.toLowerCase(c));
               }
            } else {
               nextTitleCase = true;
               titleCase.append(' ');
            }
         }

         return titleCase.toString().trim();
      } else {
         return "";
      }
   }

   static {
      CLIENT = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(10L)).build();
      cachedUsername = "";
      cachedUuid = "";
      cachedJson = null;
      cacheTime = 0L;
   }

   private static record SearchContext(String json, String targetUuid, String targetUsername, boolean coopMode) {
   }
}
