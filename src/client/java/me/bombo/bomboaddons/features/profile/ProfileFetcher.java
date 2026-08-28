package me.bombo.bomboaddons.features.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.LF;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ProfileFetcher {
   private static final HttpClient CLIENT = HttpClient.newBuilder().build();
   private static final double[] SKILL_XP = new double[]{(double)0.0F, (double)50.0F, (double)175.0F, (double)375.0F, (double)675.0F, (double)1175.0F, (double)1925.0F, (double)2925.0F, (double)4425.0F, (double)6425.0F, (double)9925.0F, (double)14925.0F, (double)22425.0F, (double)32425.0F, (double)47425.0F, (double)67425.0F, (double)97425.0F, (double)147425.0F, (double)222425.0F, (double)322425.0F, (double)522425.0F, (double)822425.0F, (double)1222425.0F, (double)1722425.0F, (double)2322425.0F, (double)3022425.0F, (double)3822425.0F, (double)4722425.0F, (double)5722425.0F, (double)6822425.0F, (double)8022425.0F, (double)9322425.0F, (double)1.0722425E7F, (double)1.2222425E7F, (double)1.3822425E7F, (double)1.5522425E7F, 1.7322425E7, 1.9222425E7, 2.1222425E7, 2.3322425E7, 2.5522425E7, 2.7822425E7, 3.0222425E7, 3.2722425E7, 3.5322425E7, 3.8072425E7, 4.0972425E7, 4.4072425E7, 4.7472425E7, 5.1172425E7, 5.5172425E7, 5.9472425E7, 6.4072425E7, 6.8972425E7, 7.4172425E7, 7.9672425E7, 8.5472425E7, 9.1572425E7, 9.7972425E7, 1.04672425E8, 1.11672425E8};
   private static final double[] SLAYER_XP = new double[]{(double)0.0F, (double)5.0F, (double)15.0F, (double)200.0F, (double)1000.0F, (double)5000.0F, (double)20000.0F, (double)100000.0F, (double)400000.0F, (double)1000000.0F};
   private static final double[] CATA_XP = new double[]{(double)0.0F, (double)50.0F, (double)125.0F, (double)235.0F, (double)395.0F, (double)625.0F, (double)955.0F, (double)1425.0F, (double)2095.0F, (double)3045.0F, (double)4385.0F, (double)6275.0F, (double)8940.0F, (double)12700.0F, (double)17960.0F, (double)25340.0F, (double)35640.0F, (double)50040.0F, (double)70040.0F, (double)98040.0F, (double)137040.0F, (double)191040.0F, (double)265040.0F, (double)365040.0F, (double)500040.0F, (double)680040.0F, (double)910040.0F, (double)1200040.0F, (double)1550040.0F, (double)1970040.0F, (double)2470040.0F, (double)3070040.0F, (double)3800040.0F, (double)4700040.0F, (double)5800040.0F, (double)7150040.0F, (double)8800040.0F, (double)1.080004E7F, (double)1.320004E7F, (double)1.610004E7F, (double)1.960004E7F, (double)2.390004E7F, (double)2.920004E7F, (double)3.570004E7F, (double)4.360004E7F, (double)5.320004E7F, (double)6.480004E7F, (double)7.880004E7F, (double)9.560004E7F, (double)1.1560004E8F, 1.3960004E8};
   private static final Map<String, CachedProfile> CACHE = new ConcurrentHashMap();
   private static final Map<String, String> UUID_CACHE = new ConcurrentHashMap();
   private static final File DISK_CACHE_FILE = new File(Minecraft.getInstance().gameDirectory, "config/bomboaddons/profile_cache.json");
   private static String pvApiKey = null;

   static {
      loadDiskCache();
   }

   private static void loadDiskCache() {
      try {
         if (DISK_CACHE_FILE.exists()) {
            String content = Files.readString(DISK_CACHE_FILE.toPath());
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
               if (entry.getValue().isJsonObject()) {
                  JsonObject p = entry.getValue().getAsJsonObject();
                  ProfileData data = new ProfileData();
                  data.username = entry.getKey();
                  if (p.has("sbLevel")) data.skyblockLevel = p.get("sbLevel").getAsDouble();
                  if (p.has("networth")) data.networth = p.get("networth").getAsDouble();
                  long ts = p.has("ts") ? p.get("ts").getAsLong() : System.currentTimeMillis();
                  CACHE.put(entry.getKey().toLowerCase(), new CachedProfile(data));
               }
            }
         }
      } catch (Throwable ignored) {}
   }

   private static void saveDiskCache() {
      try {
         if (!DISK_CACHE_FILE.getParentFile().exists()) {
            DISK_CACHE_FILE.getParentFile().mkdirs();
         }
         JsonObject root = new JsonObject();
         for (Map.Entry<String, CachedProfile> entry : CACHE.entrySet()) {
            if (entry.getValue() != null && entry.getValue().data != null) {
               JsonObject p = new JsonObject();
               p.addProperty("sbLevel", entry.getValue().data.skyblockLevel);
               p.addProperty("networth", entry.getValue().data.networth);
               p.addProperty("ts", entry.getValue().timestamp);
               root.add(entry.getKey(), p);
            }
         }
         Files.writeString(DISK_CACHE_FILE.toPath(), root.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (Throwable ignored) {}
   }

   private static int xpToLevel(double xp, double[] table) {
      for(int i = table.length - 1; i >= 0; --i) {
         if (xp >= table[i]) {
            return i;
         }
      }

      return 0;
   }

   public static ProfileData getCachedProfile(String username) {
      if (username == null) return null;
      CachedProfile cached = CACHE.get(username.toLowerCase());
      return cached != null ? cached.data : null;
   }

   public static String getTabSkyBlockLevel(String username) {
      if (username == null || username.isEmpty()) return null;
      try {
         for (Component comp : SkyblockUtils.getTabListLines()) {
            String raw = comp.getString();
            String clean = raw.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
            if (clean.contains(username) && clean.contains("[") && clean.contains("]")) {
               int start = clean.indexOf("[");
               int end = clean.indexOf("]", start);
               if (start != -1 && end != -1) {
                  String lvlNum = clean.substring(start + 1, end).trim();
                  if (lvlNum.matches("\\d+")) {
                     String formatted = SkyblockUtils.getFormattedComponentText(comp);
                     int fStart = formatted.indexOf("[");
                     int fEnd = formatted.indexOf("]", fStart);
                     if (fStart != -1 && fEnd != -1) {
                        return formatted.substring(fStart, fEnd + 1);
                     }
                     return "§3[§b" + lvlNum + "§3]";
                  }
               }
            }
         }
      } catch (Throwable ignored) {}
      return null;
   }

   public static CompletableFuture<ProfileData> fetchProfile(String username) {
      String lowerName = username.toLowerCase();
      if (CACHE.containsKey(lowerName)) {
         CachedProfile cached = (CachedProfile)CACHE.get(lowerName);
         if (System.currentTimeMillis() - cached.timestamp < 300000L) {
            return CompletableFuture.completedFuture(cached.data);
         }
      }

      if (UUID_CACHE.containsKey(lowerName)) {
         String uuid = (String)UUID_CACHE.get(lowerName);
         return fetchFromPvApi(uuid, username).thenCompose((pvData) -> fetchFromBomboAPI(uuid, username).thenCompose((bomboData) -> bomboData == null ? fetchFromSnailifyAPI(uuid, username) : CompletableFuture.completedFuture(bomboData)).thenApply((hypixelData) -> {
               ProfileData merged = hypixelData;
               if (hypixelData == null) {
                  merged = pvData;
               } else if (pvData != null) {
                  hypixelData.networth = pvData.networth;
                  if (pvData.purse > (double)0.0F) {
                     hypixelData.purse = pvData.purse;
                  }

                  if (pvData.bank > (double)0.0F) {
                     hypixelData.bank = pvData.bank;
                  }

                  hypixelData.foragingWhispers = pvData.foragingWhispers;
                  hypixelData.foragingSpentWhispers = pvData.foragingSpentWhispers;
                  hypixelData.foragingFig = pvData.foragingFig;
                  hypixelData.foragingMangrove = pvData.foragingMangrove;
                  hypixelData.riftVisits = pvData.riftVisits;
                  hypixelData.riftMotes = pvData.riftMotes;
                  hypixelData.riftGrubber = pvData.riftGrubber;
               }

               if (merged != null) {
                  CACHE.put(username.toLowerCase(), new CachedProfile(merged));
                  saveDiskCache();
               }

               return merged;
            }));
      } else {
         String mojangUrl = "https://api.mojang.com/users/profiles/minecraft/" + username;
         Bomboaddons.logApiRequest(mojangUrl);
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(mojangUrl)).timeout(Duration.ofSeconds(30L)).GET().build();
         return CLIENT.sendAsync(request, BodyHandlers.ofString()).thenCompose((res) -> {
            if (res.statusCode() != 200) {
               return CompletableFuture.<ProfileData>completedFuture(null);
            } else {
               try {
                  JsonObject json = JsonParser.parseString((String)res.body()).getAsJsonObject();
                  if (!json.has("id")) {
                     return CompletableFuture.<ProfileData>completedFuture(null);
                  } else {
                     String uuid = json.get("id").getAsString();
                     UUID_CACHE.put(lowerName, uuid);
                     return fetchFromPvApi(uuid, username).thenCompose((pvData) -> fetchFromBomboAPI(uuid, username).thenCompose((bomboData) -> bomboData == null ? fetchFromSnailifyAPI(uuid, username) : CompletableFuture.completedFuture(bomboData)).thenApply((hypixelData) -> {
                           ProfileData merged = hypixelData;
                           if (hypixelData == null) {
                              merged = pvData;
                           } else if (pvData != null) {
                              hypixelData.networth = pvData.networth;
                              if (pvData.purse > (double)0.0F) {
                                 hypixelData.purse = pvData.purse;
                              }

                              if (pvData.bank > (double)0.0F) {
                                 hypixelData.bank = pvData.bank;
                              }

                              hypixelData.foragingWhispers = pvData.foragingWhispers;
                              hypixelData.foragingSpentWhispers = pvData.foragingSpentWhispers;
                              hypixelData.foragingFig = pvData.foragingFig;
                              hypixelData.foragingMangrove = pvData.foragingMangrove;
                              hypixelData.riftVisits = pvData.riftVisits;
                              hypixelData.riftMotes = pvData.riftMotes;
                              hypixelData.riftLifetimeMotes = pvData.riftLifetimeMotes;
                              hypixelData.riftGrubber = pvData.riftGrubber;
                              hypixelData.riftEnigmaSouls = pvData.riftEnigmaSouls;
                              hypixelData.riftDeadCats = pvData.riftDeadCats;
                              hypixelData.riftUnlockedEyes = pvData.riftUnlockedEyes;
                              hypixelData.riftSecondsSitting = pvData.riftSecondsSitting;
                              hypixelData.riftTrophies = pvData.riftTrophies;
                           }

                           if (merged != null) {
                              CACHE.put(username.toLowerCase(), new CachedProfile(merged));
                              saveDiskCache();
                           }

                           return merged;
                        }).thenCompose((merged) -> {
                           if (merged == null) {
                              return CompletableFuture.<ProfileData>completedFuture(null);
                           } else if (merged.museumWeapons.isEmpty() && merged.museumArmor.isEmpty() && merged.museumRarities.isEmpty() && merged.museumSpecial.isEmpty()) {
                              HttpRequest mReq = HttpRequest.newBuilder().uri(URI.create("https://skyblock-pv.thatgravyboat.tech/api/v1/museum/" + merged.profileId)).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)").timeout(Duration.ofSeconds(15L)).GET().build();
                              return CLIENT.sendAsync(mReq, BodyHandlers.ofString()).thenApply((mRes) -> {
                                 if (mRes.statusCode() == 200) {
                                    try {
                                       JsonObject mJson = JsonParser.parseString((String)mRes.body()).getAsJsonObject();
                                       merged.museumWeapons.clear();
                                       merged.museumArmor.clear();
                                       merged.museumRarities.clear();
                                       merged.museumSpecial.clear();
                                       if (mJson.has("members")) {
                                          JsonObject members = mJson.getAsJsonObject("members");
                                          String u = uuid != null ? uuid.replace("-", "") : "";
                                          if (members.has(u)) {
                                             JsonObject memberMuseum = members.getAsJsonObject(u);
                                             parseMuseumData(memberMuseum, merged);
                                          }
                                       }
                                    } catch (Exception e) {
                                       e.printStackTrace();
                                    }
                                 }

                                 dumpProfileDebug(merged);
                                 return merged;
                              });
                           } else {
                              return CompletableFuture.completedFuture(merged);
                           }
                        }));
                  }
               } catch (Exception e) {
                  e.printStackTrace();
                  return CompletableFuture.<ProfileData>completedFuture(null);
               }
            }
         }).exceptionally((e) -> {
            e.printStackTrace();
            return null;
         });
      }
   }

   private static void dumpProfileDebug(ProfileData data) {
      if (data != null) {
         try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== PROFILE DEBUG LOG ===\n");
            sb.append("Timestamp: ").append(new Date()).append("\n");
            sb.append("Username: ").append(data.username).append("\n");
            sb.append("Profile Name: ").append(data.profileName).append("\n");
            sb.append("Profile ID: ").append(data.profileId).append("\n");
            sb.append("Purse: ").append(data.purse).append("\n");
            sb.append("Bank: ").append(data.bank).append("\n");
            sb.append("Networth: ").append(data.networth).append("\n");
            sb.append("SkyBlock Level: ").append(data.skyblockLevel).append("\n");
            sb.append("--- SKILLS ---\n");
            sb.append("Farming: ").append(data.farming).append("\n");
            sb.append("Mining: ").append(data.mining).append(" (HotM Exp: ").append(data.hotmExp).append(")\n");
            sb.append("Combat: ").append(data.combat).append("\n");
            sb.append("Foraging: ").append(data.foraging).append(" (Whispers: ").append(data.foragingWhispers).append(", Spent: ").append(data.foragingSpentWhispers).append(", Fig: ").append(data.foragingFig).append(", Mangrove: ").append(data.foragingMangrove).append(")\n");
            sb.append("Fishing: ").append(data.fishing).append("\n");
            sb.append("Enchanting: ").append(data.enchanting).append("\n");
            sb.append("Alchemy: ").append(data.alchemy).append("\n");
            sb.append("Taming: ").append(data.taming).append("\n");
            sb.append("--- RIFT ---\n");
            sb.append("Visits: ").append(data.riftVisits).append(", Motes: ").append(data.riftMotes).append(", Lifetime: ").append(data.riftLifetimeMotes).append("\n");
            sb.append("Grubber: ").append(data.riftGrubber).append(", Enigma: ").append(data.riftEnigmaSouls).append(", DeadCats: ").append(data.riftDeadCats).append(", Eyes: ").append(data.riftUnlockedEyes).append("\n");
            sb.append("Trophies count: ").append(data.riftTrophies.size()).append("\n");
            sb.append("--- MUSEUM ---\n");
            sb.append("Weapons: ").append(data.museumWeapons.size()).append(", Armor: ").append(data.museumArmor.size()).append(", Rarities: ").append(data.museumRarities.size()).append(", Special: ").append(data.museumSpecial.size()).append("\n");
            sb.append("--- CHOCOLATE FACTORY ---\n");
            sb.append("Chocolate: ").append(data.cfChocolate).append(", Total: ").append(data.cfTotalChocolate).append(", Prestige: ").append(data.cfPrestigeLevel).append("\n");
            sb.append("=== END DEBUG LOG ===\n");
            Files.writeString(Paths.get("bomboaddons_profile_debug.txt"), sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
         } catch (Exception var2) {
         }

      }
   }

   public static void parseMuseumData(JsonObject memberMuseum, ProfileData merged) {
      if (memberMuseum.has("items")) {
         JsonObject itemsObj = memberMuseum.getAsJsonObject("items");

         for(Map.Entry<String, JsonElement> entry : itemsObj.entrySet()) {
            JsonObject itemData = ((JsonElement)entry.getValue()).getAsJsonObject();
            if (itemData.has("items")) {
               JsonObject innerItems = itemData.getAsJsonObject("items");
               if (innerItems.has("data")) {
                  for(ItemStack stack : LF.decodeToItems(innerItems.get("data").getAsString())) {
                     if (stack != null && !stack.isEmpty()) {
                        String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
                        if (!itemName.contains("helmet") && !itemName.contains("chestplate") && !itemName.contains("leggings") && !itemName.contains("boots") && !itemName.contains("armor")) {
                           if (!itemName.contains("sword") && !itemName.contains("bow") && !itemName.contains("axe") && !itemName.contains("weapon")) {
                              merged.museumRarities.add(stack);
                           } else {
                              merged.museumWeapons.add(stack);
                           }
                        } else {
                           merged.museumArmor.add(stack);
                        }
                     }
                  }
               }
            }
         }
      }

      if (memberMuseum.has("special")) {
         for(JsonElement el : memberMuseum.getAsJsonArray("special")) {
            if (el.isJsonObject() && el.getAsJsonObject().has("items")) {
               JsonObject innerItems = el.getAsJsonObject().getAsJsonObject("items");
               if (innerItems.has("data")) {
                  for(ItemStack stack : LF.decodeToItems(innerItems.get("data").getAsString())) {
                     if (stack != null && !stack.isEmpty()) {
                        merged.museumSpecial.add(stack);
                     }
                  }
               }
            }
         }
      }

   }

   private static CompletableFuture<String> authenticatePV() {
      return pvApiKey != null ? CompletableFuture.completedFuture(pvApiKey) : CompletableFuture.<String>supplyAsync(() -> {
         try {
            User user = Minecraft.getInstance().getUser();
            String server = UUID.randomUUID().toString();
            MinecraftSessionService sessionService = null;

            for(Method m : Minecraft.class.getMethods()) {
               if (m.getReturnType().getName().contains("SessionService")) {
                  sessionService = (MinecraftSessionService)m.invoke(Minecraft.getInstance());
                  break;
               }
            }

            if (sessionService == null) {
               for(Field f : Minecraft.class.getDeclaredFields()) {
                  if (f.getType().getName().contains("SessionService")) {
                     f.setAccessible(true);
                     sessionService = (MinecraftSessionService)f.get(Minecraft.getInstance());
                     break;
                  }
               }
            }

            if (sessionService != null) {
               sessionService.joinServer(user.getProfileId(), user.getAccessToken(), server);
            } else {
               System.out.println("Could not find MinecraftSessionService!");
            }

            HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://skyblock-pv.thatgravyboat.tech/api/v1/authenticate")).header("User-Agent", "SkyBlockPV/1.0.0/1.21.1").header("x-minecraft-username", user.getName()).header("x-minecraft-server", server).GET().build();
            HttpResponse<String> res = CLIENT.send(req, BodyHandlers.ofString());
            if (res.statusCode() == 200) {
               pvApiKey = ((String)res.body()).replace("\"", "").trim();
               return pvApiKey;
            }
         } catch (Exception e) {
            e.printStackTrace();
         }

         return (String)null;
      });
   }

   private static CompletableFuture<ProfileData> fetchFromPvApi(String uuid, String username) {
      return authenticatePV().thenCompose((key) -> {
         if (key == null) {
            return CompletableFuture.<ProfileData>completedFuture(null);
         } else {
            String url = "https://skyblock-pv.thatgravyboat.tech/api/v1/profiles/" + uuid;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", key).timeout(Duration.ofSeconds(30L)).GET().build();
            return CLIENT.sendAsync(req, BodyHandlers.ofString()).thenApply((res) -> {
               if (res.statusCode() != 200) {
                  return null;
               } else {
                  try {
                     Files.writeString(Paths.get("pv_response.json"), (CharSequence)res.body(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                     JsonObject root = JsonParser.parseString((String)res.body()).getAsJsonObject();
                     if (!root.has("selected")) {
                        return null;
                     } else {
                        String selectedId = root.get("selected").getAsString();
                        JsonObject profile = root.getAsJsonObject("profiles").getAsJsonObject(selectedId);
                        ProfileData data = new ProfileData();
                        data.username = username;
                        data.profileId = selectedId;
                        data.profileName = profile.has("cuteName") ? profile.get("cuteName").getAsString() : "Unknown";
                        data.skyblockLevel = profile.has("skyblockLevel") ? profile.get("skyblockLevel").getAsDouble() : (double)0.0F;
                        if (profile.has("netWorth")) {
                           JsonObject nw = profile.getAsJsonObject("netWorth");
                           if (nw.has("total")) {
                              data.networth = nw.get("total").getAsDouble();
                           }
                        }

                        if (profile.has("currency")) {
                           JsonObject cur = profile.getAsJsonObject("currency");
                           if (cur.has("purse")) {
                              data.purse = cur.get("purse").getAsDouble();
                           }
                        }

                        if (profile.has("bank")) {
                           JsonObject bank = profile.getAsJsonObject("bank");
                           if (bank.has("soloBank")) {
                              data.bank = bank.get("soloBank").getAsDouble();
                           }

                           if (bank.has("profileBank") && data.bank == (double)0.0F) {
                              data.bank = bank.get("profileBank").getAsDouble();
                           }
                        }

                        try {
                           if (profile.has("foragingCore") && !profile.get("foragingCore").isJsonNull()) {
                              JsonObject fc = profile.getAsJsonObject("foragingCore");
                              if (fc.has("forests_whispers")) {
                                 data.foragingWhispers = fc.get("forests_whispers").getAsInt();
                              }

                              if (fc.has("forests_whispers_spent")) {
                                 data.foragingSpentWhispers = fc.get("forests_whispers_spent").getAsInt();
                              }
                           }

                           if (profile.has("foraging") && !profile.get("foraging").isJsonNull()) {
                              JsonObject f = profile.getAsJsonObject("foraging");
                              if (f.has("tree_gifts") && !f.get("tree_gifts").isJsonNull()) {
                                 JsonObject tg = f.getAsJsonObject("tree_gifts");
                                 if (tg.has("FIG")) {
                                    data.foragingFig = tg.get("FIG").getAsInt();
                                 }

                                 if (tg.has("MANGROVE")) {
                                    data.foragingMangrove = tg.get("MANGROVE").getAsInt();
                                 }
                              }
                           }

                           if (profile.has("rift") && !profile.get("rift").isJsonNull()) {
                              JsonObject rift = profile.getAsJsonObject("rift");
                              if (rift.has("member") && !rift.get("member").isJsonNull()) {
                                 JsonObject member = rift.getAsJsonObject("member");
                                 if (member.has("castle") && !member.get("castle").isJsonNull()) {
                                    JsonObject castle = member.getAsJsonObject("castle");
                                    if (castle.has("grubber_stacks")) {
                                       data.riftGrubber = castle.get("grubber_stacks").getAsInt();
                                    }
                                 }
                              }

                              if (rift.has("playerStats") && !rift.get("playerStats").isJsonNull()) {
                                 JsonObject stats = rift.getAsJsonObject("playerStats");
                                 if (stats.has("visits")) {
                                    data.riftVisits = stats.get("visits").getAsInt();
                                 }

                                 if (stats.has("lifetime_motes_earned")) {
                                    data.riftLifetimeMotes = stats.get("lifetime_motes_earned").getAsInt();
                                 }
                              }

                              if (rift.has("currency") && !rift.get("currency").isJsonNull()) {
                                 JsonObject currency = rift.getAsJsonObject("currency");
                                 if (currency.has("motes_purse")) {
                                    data.riftMotes = currency.get("motes_purse").getAsInt();
                                 }
                              }

                              if (rift.has("dead_cat") && !rift.get("dead_cat").isJsonNull()) {
                                 JsonObject dc = rift.getAsJsonObject("dead_cat");
                                 if (dc.has("found_cats") && !dc.get("found_cats").isJsonNull()) {
                                    data.riftDeadCats = dc.getAsJsonArray("found_cats").size();
                                 }
                              }

                              if (rift.has("enigma") && !rift.get("enigma").isJsonNull()) {
                                 JsonObject enigma = rift.getAsJsonObject("enigma");
                                 if (enigma.has("found_souls") && !enigma.get("found_souls").isJsonNull()) {
                                    data.riftEnigmaSouls = enigma.getAsJsonArray("found_souls").size();
                                 }
                              }

                              if (rift.has("eyes_unlocked") && !rift.get("eyes_unlocked").isJsonNull()) {
                                 data.riftUnlockedEyes = rift.getAsJsonArray("eyes_unlocked").size();
                              }

                              if (rift.has("village_plaza") && !rift.get("village_plaza").isJsonNull()) {
                                 JsonObject vp = rift.getAsJsonObject("village_plaza");
                                 if (vp.has("got_supreme_timecharm") && !vp.get("got_supreme_timecharm").isJsonNull() && vp.get("got_supreme_timecharm").getAsBoolean()) {
                                 }

                                 if (vp.has("timecharms") && !vp.get("timecharms").isJsonNull()) {
                                    JsonObject tc = vp.getAsJsonObject("timecharms");
                                    if (tc.has("supreme") && !tc.get("supreme").isJsonNull()) {
                                       JsonObject sup = tc.getAsJsonObject("supreme");
                                       if (sup.has("seconds_sitting")) {
                                          data.riftSecondsSitting = sup.get("seconds_sitting").getAsInt();
                                       }
                                    }
                                 }
                              }

                              if (rift.has("timecharms") && !rift.get("timecharms").isJsonNull()) {
                                 JsonObject timecharms = rift.getAsJsonObject("timecharms");
                                 if (timecharms.has("unlocked") && !timecharms.get("unlocked").isJsonNull()) {
                                    for(JsonElement el : timecharms.getAsJsonArray("unlocked")) {
                                       JsonObject tc = el.getAsJsonObject();
                                       Trophy t = new Trophy();
                                       t.type = tc.has("type") ? tc.get("type").getAsString() : "unknown";
                                       t.visits = tc.has("visits") ? tc.get("visits").getAsInt() : 0;
                                       t.timestamp = tc.has("timestamp") ? tc.get("timestamp").getAsLong() : 0L;
                                       data.riftTrophies.add(t);
                                    }
                                 }
                              }
                           }
                        } catch (Exception var12) {
                        }

                        return data;
                     }
                  } catch (Exception e) {
                     e.printStackTrace();
                     return null;
                  }
               }
            }).thenCompose((data) -> {
               if (data != null && data.profileId != null) {
                  HttpRequest mReq = HttpRequest.newBuilder().uri(URI.create("https://skyblock-pv.thatgravyboat.tech/api/v1/museum/" + data.profileId)).header("Authorization", key).timeout(Duration.ofSeconds(10L)).GET().build();
                  return CLIENT.sendAsync(mReq, BodyHandlers.ofString()).thenApply((mRes) -> {
                     if (mRes.statusCode() == 200) {
                        try {
                           JsonObject mRoot = JsonParser.parseString((String)mRes.body()).getAsJsonObject();
                           if (mRoot.has("members")) {
                              JsonObject members = mRoot.getAsJsonObject("members");
                              String u = uuid.replace("-", "");
                              if (members.has(u)) {
                                 JsonObject member = members.getAsJsonObject(u);
                                 if (member.has("items")) {
                                    JsonObject itemsObj = member.getAsJsonObject("items");

                                    for(Map.Entry<String, JsonElement> entry : itemsObj.entrySet()) {
                                       JsonObject itemData = ((JsonElement)entry.getValue()).getAsJsonObject();
                                       if (itemData.has("items")) {
                                          JsonObject innerItems = itemData.getAsJsonObject("items");
                                          if (innerItems.has("data")) {
                                             for(ItemStack stack : LF.decodeToItems(innerItems.get("data").getAsString())) {
                                                if (stack != null && !stack.isEmpty()) {
                                                   String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
                                                   if (!itemName.contains("helmet") && !itemName.contains("chestplate") && !itemName.contains("leggings") && !itemName.contains("boots") && !itemName.contains("armor")) {
                                                      if (!itemName.contains("sword") && !itemName.contains("bow") && !itemName.contains("axe") && !itemName.contains("weapon")) {
                                                         data.museumRarities.add(stack);
                                                      } else {
                                                         data.museumWeapons.add(stack);
                                                      }
                                                   } else {
                                                      data.museumArmor.add(stack);
                                                   }
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }

                                 if (member.has("special")) {
                                    for(JsonElement el : member.getAsJsonArray("special")) {
                                       if (el.isJsonObject() && el.getAsJsonObject().has("items")) {
                                          JsonObject innerItems = el.getAsJsonObject().getAsJsonObject("items");
                                          if (innerItems.has("data")) {
                                             for(ItemStack stack : LF.decodeToItems(innerItems.get("data").getAsString())) {
                                                if (stack != null && !stack.isEmpty()) {
                                                   data.museumSpecial.add(stack);
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        } catch (Exception var16) {
                        }
                     }

                     return data;
                  });
               } else {
                  return CompletableFuture.<ProfileData>completedFuture(null);
               }
            });
         }
      });
   }

   private static CompletableFuture<Double> fetchNetworthFromBomboAPI(String usernameOrUuid) {
      String url = BomboApiUrl.getApiUrl("/nw/" + usernameOrUuid);
      Bomboaddons.logApiRequest(url);
      HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15L)).GET().build();
      return CLIENT.sendAsync(req, BodyHandlers.ofString()).thenApply((res) -> {
         if (res.statusCode() != 200) return 0.0;
         try {
            JsonObject json = JsonParser.parseString((String)res.body()).getAsJsonObject();
            if (json.has("data") && json.get("data").isJsonObject()) {
               JsonObject d = json.getAsJsonObject("data");
               if (d.has("networth")) {
                  if (d.get("networth").isJsonObject()) {
                     JsonObject nwObj = d.getAsJsonObject("networth");
                     if (nwObj.has("networth")) {
                        return nwObj.get("networth").getAsDouble();
                     }
                  } else if (d.get("networth").isJsonPrimitive()) {
                     return d.get("networth").getAsDouble();
                  }
               }
            } else if (json.has("networth")) {
               if (json.get("networth").isJsonObject()) {
                  JsonObject nwObj = json.getAsJsonObject("networth");
                  if (nwObj.has("networth")) {
                     return nwObj.get("networth").getAsDouble();
                  }
               } else if (json.get("networth").isJsonPrimitive()) {
                  return json.get("networth").getAsDouble();
               }
            }
         } catch (Exception ignored) {}
         return 0.0;
      }).exceptionally((e) -> 0.0);
   }

   private static CompletableFuture<ProfileData> fetchFromBomboAPI(String uuid, String username) {
      String url = BomboApiUrl.getApiUrl("/data/" + uuid);
      Bomboaddons.logApiRequest(url);
      HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30L)).GET().build();
      return CLIENT.sendAsync(req, BodyHandlers.ofString()).thenCompose((res) -> {
         if (res.statusCode() != 200) {
            return CompletableFuture.completedFuture(null);
         } else {
            try {
               JsonObject json = JsonParser.parseString((String)res.body()).getAsJsonObject();
               ProfileData data = null;
               if (json.has("raw_profile")) {
                  JsonObject rawProfile = json.getAsJsonObject("raw_profile");
                  String profileName = json.has("profile") ? json.get("profile").getAsString() : "Unknown";
                  data = parseProfile(rawProfile, uuid, username, profileName);
                  if (data != null && rawProfile.has("museum")) {
                     JsonObject museumData = rawProfile.getAsJsonObject("museum");
                     String u = uuid != null ? uuid.replace("-", "") : "";
                     if (museumData.has(u)) {
                        parseMuseumData(museumData.getAsJsonObject(u), data);
                     }
                  }
               } else if (json.has("members")) {
                  // Direct profile object returned from /data/:uuid
                  String profileName = json.has("cute_name") ? json.get("cute_name").getAsString() : "Unknown";
                  data = parseProfile(json, uuid, username, profileName);
               }

               if (data != null) {
                  final ProfileData finalData = data;
                  return fetchNetworthFromBomboAPI(username != null ? username : uuid).thenApply((nw) -> {
                     if (nw > 0.0) {
                        finalData.networth = nw;
                     }
                     return finalData;
                  });
               }
               return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
               e.printStackTrace();
               return CompletableFuture.completedFuture(null);
            }
         }
      }).exceptionally((e) -> null);
   }

   private static CompletableFuture<ProfileData> fetchFromSnailifyAPI(String uuid, String username) {
      String url = "https://profile.snailify.workers.dev/?uuid=" + uuid;
      Bomboaddons.logApiRequest(url);
      HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30L)).GET().build();
      return CLIENT.sendAsync(req, BodyHandlers.ofString()).thenApply((res) -> {
         if (res.statusCode() != 200) {
            return null;
         } else {
            try {
               JsonObject json = JsonParser.parseString((String)res.body()).getAsJsonObject();
               if (!json.has("profiles")) {
                  return null;
               } else {
                  JsonArray profiles = json.getAsJsonArray("profiles");
                  if (profiles.size() == 0) {
                     return null;
                  } else {
                     JsonObject currentProfile = null;

                     for(JsonElement el : profiles) {
                        JsonObject p = el.getAsJsonObject();
                        if (p.has("selected") && p.get("selected").getAsBoolean()) {
                           currentProfile = p;
                           break;
                        }
                     }

                     if (currentProfile == null) {
                        currentProfile = profiles.get(0).getAsJsonObject();
                     }

                     String profileName = currentProfile.has("cute_name") ? currentProfile.get("cute_name").getAsString() : "Unknown";
                     return parseProfile(currentProfile, uuid, username, profileName);
                  }
               }
            } catch (Exception e) {
               e.printStackTrace();
               return null;
            }
         }
      }).exceptionally((e) -> null);
   }

   public static void populateFromMemberData(JsonObject memberData, ProfileData data) {
      if (memberData.has("leveling")) {
         JsonObject leveling = memberData.getAsJsonObject("leveling");
         if (leveling.has("experience")) {
            data.skyblockLevel = leveling.get("experience").getAsDouble() / (double)100.0F;
         }
      }

      if (memberData.has("player_data")) {
         JsonObject pData = memberData.getAsJsonObject("player_data");
         if (pData.has("experience")) {
            JsonObject exp = pData.getAsJsonObject("experience");
            data.farming = xpToLevel(getRawXp(exp, "SKILL_FARMING"), SKILL_XP);
            data.mining = xpToLevel(getRawXp(exp, "SKILL_MINING"), SKILL_XP);
            data.combat = xpToLevel(getRawXp(exp, "SKILL_COMBAT"), SKILL_XP);
            data.foraging = xpToLevel(getRawXp(exp, "SKILL_FORAGING"), SKILL_XP);
            data.fishing = xpToLevel(getRawXp(exp, "SKILL_FISHING"), SKILL_XP);
            data.enchanting = xpToLevel(getRawXp(exp, "SKILL_ENCHANTING"), SKILL_XP);
            data.alchemy = xpToLevel(getRawXp(exp, "SKILL_ALCHEMY"), SKILL_XP);
            data.taming = (double)xpToLevel(getRawXp(exp, "SKILL_TAMING"), SKILL_XP);
         }

         if (pData.has("rift")) {
            JsonObject pRift = pData.getAsJsonObject("rift");
            if (pRift.has("visits")) {
               data.riftVisits = pRift.get("visits").getAsInt();
            }

            if (pRift.has("lifetime_motes_earned")) {
               data.riftLifetimeMotes = pRift.get("lifetime_motes_earned").getAsInt();
            }
         }
      }

      if (memberData.has("currencies")) {
         JsonObject currencies = memberData.getAsJsonObject("currencies");
         if (currencies.has("motes_purse")) {
            data.riftMotes = currencies.get("motes_purse").getAsInt();
         }
      }

      if (memberData.has("slayer") && memberData.getAsJsonObject("slayer").has("slayer_bosses")) {
         JsonObject bosses = memberData.getAsJsonObject("slayer").getAsJsonObject("slayer_bosses");
         data.zombieSlayerInfo = parseSlayerInfo(bosses, "zombie");
         data.zombieSlayer = data.zombieSlayerInfo.level;
         data.spiderSlayerInfo = parseSlayerInfo(bosses, "spider");
         data.spiderSlayer = data.spiderSlayerInfo.level;
         data.wolfSlayerInfo = parseSlayerInfo(bosses, "wolf");
         data.wolfSlayer = data.wolfSlayerInfo.level;
         data.endermanSlayerInfo = parseSlayerInfo(bosses, "enderman");
         data.endermanSlayer = data.endermanSlayerInfo.level;
         data.blazeSlayerInfo = parseSlayerInfo(bosses, "blaze");
         data.blazeSlayer = data.blazeSlayerInfo.level;
         data.vampireSlayerInfo = parseSlayerInfo(bosses, "vampire");
         data.vampireSlayer = data.vampireSlayerInfo.level;
      }

      if (memberData.has("events")) {
         JsonObject events = memberData.getAsJsonObject("events");
         if (events.has("easter")) {
            JsonObject easter = events.getAsJsonObject("easter");
            if (easter.has("chocolate")) {
               data.cfChocolate = easter.get("chocolate").getAsLong();
            }

            if (easter.has("total_chocolate")) {
               data.cfTotalChocolate = easter.get("total_chocolate").getAsLong();
            }

            if (easter.has("chocolate_since_prestige")) {
               data.cfChocolateSincePrestige = easter.get("chocolate_since_prestige").getAsLong();
            }

            if (easter.has("chocolate_level")) {
               data.cfPrestigeLevel = easter.get("chocolate_level").getAsInt();
            }

            if (easter.has("chocolate_multiplier_upgrades")) {
               data.cfMultiplierUpgrades = easter.get("chocolate_multiplier_upgrades").getAsInt();
            }
         }
      }

      if (memberData.has("rift") && !memberData.get("rift").isJsonNull()) {
         JsonObject rift = memberData.getAsJsonObject("rift");
         if (rift.has("village_plaza") && !rift.get("village_plaza").isJsonNull()) {
            JsonObject vp = rift.getAsJsonObject("village_plaza");
            if (vp.has("lonely") && !vp.get("lonely").isJsonNull()) {
               JsonObject lonely = vp.getAsJsonObject("lonely");
               if (lonely.has("seconds_sitting")) {
                  data.riftSecondsSitting = lonely.get("seconds_sitting").getAsInt();
               }
            }
         }

         if (rift.has("dead_cats") && !rift.get("dead_cats").isJsonNull()) {
            JsonObject deadCats = rift.getAsJsonObject("dead_cats");
            if (deadCats.has("found_cats")) {
               data.riftDeadCats = deadCats.getAsJsonArray("found_cats").size();
            }
         }

         if (rift.has("enigma") && !rift.get("enigma").isJsonNull()) {
            JsonObject enigma = rift.getAsJsonObject("enigma");
            if (enigma.has("found_souls")) {
               data.riftEnigmaSouls = enigma.getAsJsonArray("found_souls").size();
            }
         }

         if (rift.has("wither_cage") && !rift.get("wither_cage").isJsonNull()) {
            JsonObject witherCage = rift.getAsJsonObject("wither_cage");
            if (witherCage.has("killed_eyes")) {
               data.riftUnlockedEyes = witherCage.getAsJsonArray("killed_eyes").size();
            }
         }

         if (rift.has("castle") && !rift.get("castle").isJsonNull()) {
            JsonObject castle = rift.getAsJsonObject("castle");
            if (castle.has("grubber_stacks")) {
               data.riftGrubber = castle.get("grubber_stacks").getAsInt();
            }
         }

         if (rift.has("gallery") && !rift.get("gallery").isJsonNull()) {
            JsonObject gallery = rift.getAsJsonObject("gallery");
            if (gallery.has("secured_trophies")) {
               for(JsonElement el : gallery.getAsJsonArray("secured_trophies")) {
                  JsonObject obj = el.getAsJsonObject();
                  Trophy t = new Trophy();
                  if (obj.has("type")) {
                     t.type = obj.get("type").getAsString();
                  }

                  if (obj.has("timestamp")) {
                     t.timestamp = obj.get("timestamp").getAsLong();
                  }

                  if (obj.has("visits")) {
                     t.visits = obj.get("visits").getAsInt();
                  }

                  data.riftTrophies.add(t);
               }
            }
         }
      }

      if (data.riftLifetimeMotes == 0 && data.riftMotes > 0) {
         data.riftLifetimeMotes = data.riftMotes;
      }

      if (data.riftVisits == 0 && !data.riftTrophies.isEmpty()) {
         int maxV = 0;

         for(Trophy t : data.riftTrophies) {
            if (t.visits > maxV) {
               maxV = t.visits;
            }
         }

         data.riftVisits = maxV;
      }

      if (memberData.has("dungeons")) {
         JsonObject dungeons = memberData.getAsJsonObject("dungeons");
         if (dungeons.has("secrets")) {
            data.totalSecrets = dungeons.get("secrets").getAsLong();
         }

         if (dungeons.has("selected_dungeon_class")) {
            data.selectedDungeonClass = dungeons.get("selected_dungeon_class").getAsString();
         }

         if (dungeons.has("player_classes")) {
            JsonObject pClasses = dungeons.getAsJsonObject("player_classes");

            for(Map.Entry<String, JsonElement> entry : pClasses.entrySet()) {
               if (((JsonElement)entry.getValue()).isJsonObject() && ((JsonElement)entry.getValue()).getAsJsonObject().has("experience")) {
                  double exp = ((JsonElement)entry.getValue()).getAsJsonObject().get("experience").getAsDouble();
                  data.classXpMap.put((String)entry.getKey(), exp);
                  data.classLevelMap.put((String)entry.getKey(), xpToLevel(exp, CATA_XP));
               }
            }
         }

         if (dungeons.has("dungeon_types")) {
            JsonObject dTypes = dungeons.getAsJsonObject("dungeon_types");
            if (dTypes.has("catacombs") && dTypes.get("catacombs").isJsonObject()) {
               JsonObject cata = dTypes.getAsJsonObject("catacombs");
               if (cata.has("experience")) {
                  data.catacombsXp = cata.get("experience").getAsDouble();
                  data.catacombs = xpToLevel(data.catacombsXp, CATA_XP);
               }

               if (cata.has("tier_completions") && cata.get("tier_completions").isJsonObject()) {
                  JsonObject comps = cata.getAsJsonObject("tier_completions");

                  for(Map.Entry<String, JsonElement> e : comps.entrySet()) {
                     try {
                        int floor = Integer.parseInt((String)e.getKey());
                        data.normalFloorCompletions.put(floor, ((JsonElement)e.getValue()).getAsInt());
                     } catch (Exception var12) {
                     }
                  }
               }
            }

            if (dTypes.has("master_catacombs") && dTypes.get("master_catacombs").isJsonObject()) {
               JsonObject mcata = dTypes.getAsJsonObject("master_catacombs");
               if (mcata.has("tier_completions") && mcata.get("tier_completions").isJsonObject()) {
                  JsonObject comps = mcata.getAsJsonObject("tier_completions");

                  for(Map.Entry<String, JsonElement> e : comps.entrySet()) {
                     try {
                        int floor = Integer.parseInt((String)e.getKey());
                        data.masterFloorCompletions.put(floor, ((JsonElement)e.getValue()).getAsInt());
                     } catch (Exception var11) {
                     }
                  }
               }
            }
         }
      }

      if (memberData.has("mining_core")) {
         JsonObject mc = memberData.getAsJsonObject("mining_core");
         if (mc.has("experience")) {
            data.hotmExp = mc.get("experience").getAsDouble();
         }

         if (mc.has("nodes")) {
            JsonObject nodesObj = mc.getAsJsonObject("nodes");
            if (nodesObj.has("mining_core") && nodesObj.get("mining_core").isJsonObject()) {
               nodesObj = nodesObj.getAsJsonObject("mining_core");
            }

            for(Map.Entry<String, JsonElement> entry : nodesObj.entrySet()) {
               try {
                  if (((JsonElement)entry.getValue()).isJsonPrimitive()) {
                     data.hotmNodes.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsInt());
                  }
               } catch (Exception var10) {
               }
            }
         }
      }

      if (memberData.has("collection")) {
         data.totalCollections = memberData.getAsJsonObject("collection").size();
      }

      JsonObject inventory = memberData.has("inventory") ? memberData.getAsJsonObject("inventory") : new JsonObject();
      JsonObject petsData = memberData.has("pets_data") ? memberData.getAsJsonObject("pets_data") : new JsonObject();
      if (inventory.has("inv_contents")) {
         JsonObject inv = inventory.getAsJsonObject("inv_contents");
         if (inv.has("data")) {
            data.inventory = LF.decodeToItems(inv.get("data").getAsString());
         }
      }

      if (inventory.has("ender_chest_contents")) {
         JsonObject ec = inventory.getAsJsonObject("ender_chest_contents");
         if (ec.has("data")) {
            data.enderChest = LF.decodeToItems(ec.get("data").getAsString());
         }
      }

      if (inventory.has("wardrobe_contents")) {
         JsonObject wd = inventory.getAsJsonObject("wardrobe_contents");
         if (wd.has("data")) {
            data.wardrobe = LF.decodeToItems(wd.get("data").getAsString());
         }
      } else {
         JsonObject sharedInv = memberData.has("shared_inventory") ? memberData.getAsJsonObject("shared_inventory") : new JsonObject();
         if (sharedInv.has("wardrobe_contents")) {
            JsonObject wd = sharedInv.getAsJsonObject("wardrobe_contents");
            if (wd.has("data")) {
               data.wardrobe = LF.decodeToItems(wd.get("data").getAsString());
            }
         } else if (memberData.has("wardrobe_contents")) {
            JsonObject wd = memberData.getAsJsonObject("wardrobe_contents");
            if (wd.has("data")) {
               data.wardrobe = LF.decodeToItems(wd.get("data").getAsString());
            }
         }
      }

      if (inventory.has("personal_vault_contents")) {
         JsonObject pv = inventory.getAsJsonObject("personal_vault_contents");
         if (pv.has("data")) {
            data.personalVault = LF.decodeToItems(pv.get("data").getAsString());
         }
      }

      if (inventory.has("inv_armor")) {
         JsonObject inv = inventory.getAsJsonObject("inv_armor");
         if (inv.has("data")) {
            data.armor = LF.decodeToItems(inv.get("data").getAsString());
         }
      }

      if (inventory.has("equipment_contents")) {
         JsonObject eq = inventory.getAsJsonObject("equipment_contents");
         if (eq.has("data")) {
            data.equipment = LF.decodeToItems(eq.get("data").getAsString());
         }
      }

      if (inventory.has("bag_contents")) {
         JsonObject bag = inventory.getAsJsonObject("bag_contents");
         if (bag.has("talisman_bag")) {
            JsonObject acc = bag.getAsJsonObject("talisman_bag");
            if (acc.has("data")) {
               data.accessories = LF.decodeToItems(acc.get("data").getAsString());
            }
         }

         if (bag.has("potion_bag")) {
            JsonObject obj = bag.getAsJsonObject("potion_bag");
            if (obj.has("data")) {
               data.potionBag = LF.decodeToItems(obj.get("data").getAsString());
            }
         }

         if (bag.has("fishing_bag")) {
            JsonObject obj = bag.getAsJsonObject("fishing_bag");
            if (obj.has("data")) {
               data.fishingBag = LF.decodeToItems(obj.get("data").getAsString());
            }
         }

         if (bag.has("quiver")) {
            JsonObject obj = bag.getAsJsonObject("quiver");
            if (obj.has("data")) {
               data.quiver = LF.decodeToItems(obj.get("data").getAsString());
            }
         }
      }

      if (inventory.has("candy_inventory_contents")) {
         JsonObject candy = inventory.getAsJsonObject("candy_inventory_contents");
         if (candy.has("data")) {
            data.candyBag = LF.decodeToItems(candy.get("data").getAsString());
         }
      }

      if (inventory.has("backpack_contents")) {
         JsonObject bp = inventory.getAsJsonObject("backpack_contents");

         for(Map.Entry<String, JsonElement> entry : bp.entrySet()) {
            if (((JsonElement)entry.getValue()).isJsonObject()) {
               JsonObject page = ((JsonElement)entry.getValue()).getAsJsonObject();
               if (page.has("data")) {
                  try {
                     int idx = Integer.parseInt((String)entry.getKey());
                     data.backpacks.put(idx, LF.decodeToItems(page.get("data").getAsString()));
                  } catch (Exception var9) {
                  }
               }
            }
         }
      }

      if (petsData.has("pets") && petsData.get("pets").isJsonArray()) {
         for(JsonElement el : petsData.getAsJsonArray("pets")) {
            if (el.isJsonObject()) {
               JsonObject petObj = el.getAsJsonObject();
               Pet pet = new Pet();
               pet.type = petObj.has("type") ? petObj.get("type").getAsString() : "Unknown";
               pet.tier = petObj.has("tier") ? petObj.get("tier").getAsString() : "COMMON";
               pet.active = petObj.has("active") && petObj.get("active").getAsBoolean();
               pet.exp = petObj.has("exp") ? petObj.get("exp").getAsDouble() : (double)0.0F;
               pet.heldItem = petObj.has("heldItem") && !petObj.get("heldItem").isJsonNull() ? petObj.get("heldItem").getAsString() : null;
               data.pets.add(pet);
            }
         }
      }

   }

   private static ProfileData parseProfile(JsonObject profileObject, String uuid, String username, String profileName) {
      if (!profileObject.has("members")) {
         return null;
      } else {
         JsonObject members = profileObject.getAsJsonObject("members");
         JsonObject memberData = null;

         for(Map.Entry<String, JsonElement> entry : members.entrySet()) {
            if (((String)entry.getKey()).replace("-", "").equalsIgnoreCase(uuid.replace("-", ""))) {
               memberData = ((JsonElement)entry.getValue()).getAsJsonObject();
               break;
            }
         }

         if (memberData == null) {
            return null;
         } else {
            ProfileData data = new ProfileData();
            data.username = username;
            data.profileName = profileName;
            data.profileId = profileObject.has("profile_id") ? profileObject.get("profile_id").getAsString() : null;
            if (profileObject.has("currencies")) {
               JsonObject currencies = profileObject.getAsJsonObject("currencies");
               if (currencies.has("coin_purse")) {
                  data.purse = currencies.get("coin_purse").getAsDouble();
               }
            }

            if (profileObject.has("banking")) {
               JsonObject banking = profileObject.getAsJsonObject("banking");
               if (banking.has("balance")) {
                  data.bank = banking.get("balance").getAsDouble();
               }
            }

            populateFromMemberData(memberData, data);
            data.networth = (double)0.0F;
            return data;
         }
      }
   }

   private static double getRawXp(JsonObject exp, String skillName) {
      return exp.has(skillName) ? exp.get(skillName).getAsDouble() : (double)0.0F;
   }

   private static SlayerInfo parseSlayerInfo(JsonObject bosses, String bossName) {
      SlayerInfo info = new SlayerInfo();
      if (bosses.has(bossName)) {
         JsonObject boss = bosses.getAsJsonObject(bossName);
         if (boss.has("xp")) {
            info.xp = boss.get("xp").getAsDouble();
            info.level = xpToLevel(info.xp, SLAYER_XP);
         }

         for(Map.Entry<String, JsonElement> entry : boss.entrySet()) {
            if (((String)entry.getKey()).startsWith("boss_kills_tier_")) {
               try {
                  int tier = Integer.parseInt(((String)entry.getKey()).replace("boss_kills_tier_", "")) + 1;
                  info.kills.put(tier, ((JsonElement)entry.getValue()).getAsInt());
               } catch (Exception var7) {
               }
            }
         }
      }

      return info;
   }

   private static double getSlayerXp(JsonObject bosses, String bossName) {
      if (bosses.has(bossName)) {
         JsonObject boss = bosses.getAsJsonObject(bossName);
         if (boss.has("xp")) {
            return boss.get("xp").getAsDouble();
         }
      }

      return (double)0.0F;
   }

   private static class CachedProfile {
      ProfileData data;
      long timestamp;

      CachedProfile(ProfileData data) {
         this.data = data;
         this.timestamp = System.currentTimeMillis();
      }
   }

   public static class SlayerInfo {
      public double xp = (double)0.0F;
      public int level = 0;
      public Map<Integer, Integer> kills = new HashMap();
   }

   public static class ProfileData {
      public String username;
      public String profileName;
      public String profileId;
      public double purse;
      public double bank;
      public double skyblockLevel;
      public double taming = (double)0.0F;
      public Map<String, Integer> hotmNodes = new HashMap();
      public int foragingWhispers = 0;
      public int foragingSpentWhispers = 0;
      public int foragingFig = 0;
      public int foragingMangrove = 0;
      public int riftVisits = 0;
      public int riftMotes = 0;
      public int riftLifetimeMotes = 0;
      public int riftGrubber = 0;
      public int riftEnigmaSouls = 0;
      public int riftDeadCats = 0;
      public int riftUnlockedEyes = 0;
      public int riftSecondsSitting = 0;
      public List<Trophy> riftTrophies = new ArrayList();
      public long cfChocolate = 0L;
      public long cfTotalChocolate = 0L;
      public long cfChocolateSincePrestige = 0L;
      public int cfPrestigeLevel = 0;
      public int cfMultiplierUpgrades = 0;
      public double networth;
      public SlayerInfo zombieSlayerInfo = new SlayerInfo();
      public SlayerInfo spiderSlayerInfo = new SlayerInfo();
      public SlayerInfo wolfSlayerInfo = new SlayerInfo();
      public SlayerInfo endermanSlayerInfo = new SlayerInfo();
      public SlayerInfo blazeSlayerInfo = new SlayerInfo();
      public SlayerInfo vampireSlayerInfo = new SlayerInfo();
      public int farming;
      public int mining;
      public int combat;
      public int foraging;
      public int fishing;
      public int enchanting;
      public int alchemy;
      public int zombieSlayer;
      public int spiderSlayer;
      public int wolfSlayer;
      public int endermanSlayer;
      public int blazeSlayer;
      public int vampireSlayer;
      public int catacombs;
      public double catacombsXp;
      public long totalSecrets;
      public String selectedDungeonClass = "None";
      public Map<String, Double> classXpMap = new HashMap();
      public Map<String, Integer> classLevelMap = new HashMap();
      public Map<Integer, Integer> normalFloorCompletions = new TreeMap();
      public Map<Integer, Integer> masterFloorCompletions = new TreeMap();
      public double hotmExp;
      public int totalCollections;
      public List<ItemStack> inventory = new ArrayList();
      public List<ItemStack> enderChest = new ArrayList();
      public List<ItemStack> wardrobe = new ArrayList();
      public List<ItemStack> personalVault = new ArrayList();
      public List<ItemStack> armor = new ArrayList();
      public List<ItemStack> equipment = new ArrayList();
      public List<ItemStack> accessories = new ArrayList();
      public Map<Integer, List<ItemStack>> backpacks = new TreeMap();
      public List<Pet> pets = new ArrayList();
      public List<ItemStack> potionBag = new ArrayList();
      public List<ItemStack> fishingBag = new ArrayList();
      public List<ItemStack> quiver = new ArrayList();
      public List<ItemStack> candyBag = new ArrayList();
      public List<ItemStack> museumWeapons = new ArrayList();
      public Map<String, Integer> bestiaryKills = new HashMap();
      public List<ItemStack> museumArmor = new ArrayList();
      public List<ItemStack> museumRarities = new ArrayList();
      public List<ItemStack> museumSpecial = new ArrayList();
   }

   public static class Pet {
      public String type;
      public String tier;
      public boolean active;
      public double exp;
      public String heldItem;
   }

   public static class Trophy {
      public String type;
      public int visits;
      public long timestamp;
   }
}
