package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class KuudraSummaryOverlay {
   private static String kuudraPetTier = "?";
   private static int kuudraPetLevel = 0;
   private static int cooledForgesLevel = 5;
   private static long mageRep = -1L;
   private static long barbRep = -1L;
   private static long crimsonEssence = 0L;
   private static long kuudraShards = 0L;
   private static int attributeShardEssenceBonusPct = 0;
   public static ShardResult lastShardResult = new ShardResult();
   public static String selectedFaction = "Unknown";
   private static volatile boolean fetchInProgress = false;
   private static long lastFetchTime = 0L;
   private static final long FETCH_COOLDOWN_MS = 30000L;
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();

   public static String getKuudraPetTier() {
      return kuudraPetTier;
   }

   public static int getKuudraPetLevel() {
      return kuudraPetLevel;
   }

   public static int getCooledForgesLevel() {
      return cooledForgesLevel;
   }

   public static long getMageRep() {
      return mageRep;
   }

   public static long getBarbRep() {
      return barbRep;
   }

   public static long getKuudraShards() {
      return kuudraShards;
   }

   public static int getAttributeShardEssenceBonusPct() {
      return attributeShardEssenceBonusPct;
   }

   public static void ensureDataFetched() {
      long now = System.currentTimeMillis();
      if (lastFetchTime == 0L || now - lastFetchTime > 30000L) {
         fetchPlayerData();
      }

   }

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "kuudra_summary_overlay"), KuudraSummaryOverlay::render);
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         if (mc.screen != null) {
            String title = mc.screen.getTitle().getString();
            boolean isKuudraCheckGui = (title.contains("Kuudra") || title.contains("Chest")) && !title.contains("Croesus");
            if (isKuudraCheckGui) {
               long now = System.currentTimeMillis();
               if (lastFetchTime == 0L || now - lastFetchTime > 30000L) {
                  fetchPlayerData();
               }

               Font font = mc.font;
               int x = 2;
               int y = 2;
               String petLine;
               if (lastFetchTime == 0L) {
                  petLine = "§7Kuudra Pet: §8Loading...";
               } else if ("?".equals(kuudraPetTier)) {
                  petLine = "§7Kuudra Pet: §8None";
               } else {
                  petLine = "§7Kuudra Pet: §f" + kuudraPetTier + " §8(Lvl " + kuudraPetLevel + ")";
               }

               String repLine;
               if (lastFetchTime == 0L) {
                  repLine = "§7Reputation: §8Loading...";
               } else if (mageRep < 0L && barbRep < 0L) {
                  repLine = "§7Reputation: §8N/A";
               } else if (mageRep >= barbRep) {
                  Object[] var10001 = new Object[]{mageRep};
                  repLine = "§7Reputation: §bMage: §f" + String.format("%,d", var10001);
               } else {
                  Object[] var22 = new Object[]{barbRep};
                  repLine = "§7Reputation: §cBarbarian: §f" + String.format("%,d", var22);
               }

               Object[] var23 = new Object[]{crimsonEssence};
               String essenceLine = "§7Crimson Essence: §f" + String.format("%,d", var23);
               var23 = new Object[]{kuudraShards};
               String shardsLine = "§7Kuudra Shards: §f" + String.format("%,d", var23);
               int maxTextWidth = Math.max(Math.max(font.width(petLine), font.width(repLine)), Math.max(font.width(essenceLine), font.width(shardsLine)));
               int boxWidth = maxTextWidth + 12;
               int boxHeight = 60;
               g.fill(x, y, x + boxWidth, y + boxHeight, -872415232);
               int curY = y + 5;
               g.text(font, "§6§lKuudra Summary", x + 6, curY, -22016, true);
               curY += 12;
               g.text(font, petLine, x + 6, curY, -1, true);
               curY += 11;
               g.text(font, repLine, x + 6, curY, -1, true);
               curY += 11;
               g.text(font, essenceLine, x + 6, curY, -1, true);
               curY += 11;
               g.text(font, shardsLine, x + 6, curY, -1, true);
            }
         }
      }
   }

   private static void fetchPlayerData() {
      if (!fetchInProgress) {
         fetchInProgress = true;
         lastFetchTime = System.currentTimeMillis();
         Minecraft mc = Minecraft.getInstance();
         String username = mc.getUser().getName();
         if (username != null && !username.isEmpty() && !"Player".equalsIgnoreCase(username)) {
            try {
               String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
               String urlStr = "https://api.bombo.dpdns.org/data/" + encodedUser;
               HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlStr)).timeout(Duration.ofSeconds(10L)).header("User-Agent", "BomboAddons/1.0").GET().build();
               HTTP.sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
                  if (response.statusCode() == 200) {
                     parseResponse((String)response.body());
                  }

                  fetchInProgress = false;
               }).exceptionally((ex) -> {
                  ex.printStackTrace();
                  fetchInProgress = false;
                  return null;
               });
            } catch (Exception e) {
               e.printStackTrace();
               fetchInProgress = false;
            }

         } else {
            fetchInProgress = false;
         }
      }
   }

   private static void parseResponse(String json) {
      try {
         JsonObject root = JsonParser.parseString(json).getAsJsonObject();
         Minecraft mc = Minecraft.getInstance();
         String username = mc.getUser() != null ? mc.getUser().getName() : "";
         JsonObject member = getTargetMember(root, username);
         if (member == null) {
            return;
         }

         JsonObject petObj = findKuudraPetInMember(member);
         if (petObj != null) {
            kuudraPetTier = getStringOrNull(petObj, "tier");
            if (kuudraPetTier == null) {
               kuudraPetTier = "?";
            }

            if (petObj.has("exp")) {
               double exp = petObj.get("exp").getAsDouble();
               kuudraPetLevel = calculatePetLevel(exp, kuudraPetTier);
            } else if (petObj.has("level")) {
               kuudraPetLevel = petObj.get("level").getAsInt();
            }
         } else {
            kuudraPetTier = "?";
            kuudraPetLevel = 0;
         }

         JsonObject netherData = member.getAsJsonObject("nether_island_player_data");
         if (netherData != null) {
            if (netherData.has("mages_reputation")) {
               mageRep = netherData.get("mages_reputation").getAsLong();
            } else if (netherData.has("mage_reputation")) {
               mageRep = netherData.get("mage_reputation").getAsLong();
            }

            if (netherData.has("barbarians_reputation")) {
               barbRep = netherData.get("barbarians_reputation").getAsLong();
            } else if (netherData.has("barbarian_reputation")) {
               barbRep = netherData.get("barbarian_reputation").getAsLong();
            } else if (netherData.has("barbarianReputation")) {
               barbRep = netherData.get("barbarianReputation").getAsLong();
            }
         }

         JsonObject essences = member.getAsJsonObject("essences");
         if (essences != null && essences.has("CRIMSON")) {
            JsonObject crimsonData = essences.getAsJsonObject("CRIMSON");
            if (crimsonData.has("current")) {
               crimsonEssence = crimsonData.get("current").getAsLong();
            }
         }

         JsonObject playerData = member.getAsJsonObject("player_data");
         if (playerData != null && playerData.has("perks")) {
            JsonObject perks = playerData.getAsJsonObject("perks");
            if (perks != null && perks.has("cooled_forges")) {
               cooledForgesLevel = perks.get("cooled_forges").getAsInt();
            }
         }

         if (member.has("kuudra_shards")) {
            kuudraShards = member.get("kuudra_shards").getAsLong();
         } else if (member.has("kuudraShards")) {
            kuudraShards = member.get("kuudraShards").getAsLong();
         } else if (netherData != null && netherData.has("kuudra_shards")) {
            kuudraShards = netherData.get("kuudra_shards").getAsLong();
         } else if (netherData != null && netherData.has("shards") && netherData.get("shards").isJsonPrimitive()) {
            kuudraShards = netherData.get("shards").getAsLong();
         }

         JsonObject attrMenu = member.has("attributemenu") && member.get("attributemenu").isJsonObject() ? member.getAsJsonObject("attributemenu") : null;
         if (attrMenu == null && member.has("attribute_shards") && member.get("attribute_shards").isJsonObject()) {
            attrMenu = member.getAsJsonObject("attribute_shards");
         }

         if (attrMenu == null && member.has("attributes") && member.get("attributes").isJsonObject()) {
            attrMenu = member.getAsJsonObject("attributes");
         }

         if (attrMenu == null && netherData != null && netherData.has("attributemenu") && netherData.get("attributemenu").isJsonObject()) {
            attrMenu = netherData.getAsJsonObject("attributemenu");
         }

         if (attrMenu == null && netherData != null && netherData.has("shards") && netherData.get("shards").isJsonObject()) {
            attrMenu = netherData.getAsJsonObject("shards");
         }

         if (attrMenu == null && netherData != null && netherData.has("hunting_box") && netherData.get("hunting_box").isJsonObject()) {
            attrMenu = netherData.getAsJsonObject("hunting_box");
         }

         if (attrMenu == null && member.has("inventory") && member.get("inventory").isJsonObject()) {
            JsonObject invObj = member.getAsJsonObject("inventory");
            if (invObj.has("attribute_shards") && invObj.get("attribute_shards").isJsonObject()) {
               attrMenu = invObj.getAsJsonObject("attribute_shards");
            }
         }

         if (attrMenu != null) {
            if (attrMenu.has("stacks") && attrMenu.get("stacks").isJsonObject()) {
               JsonObject stacks = attrMenu.getAsJsonObject("stacks");
               if (stacks.has("crimson_essence")) {
                  attributeShardEssenceBonusPct = stacks.get("crimson_essence").getAsInt();
               } else if (stacks.has("CRIMSON_ESSENCE")) {
                  attributeShardEssenceBonusPct = stacks.get("CRIMSON_ESSENCE").getAsInt();
               }
            }

            if (attributeShardEssenceBonusPct == 0) {
               if (attrMenu.has("crimson_essence")) {
                  attributeShardEssenceBonusPct = attrMenu.get("crimson_essence").getAsInt();
               } else if (attrMenu.has("CRIMSON_ESSENCE")) {
                  attributeShardEssenceBonusPct = attrMenu.get("CRIMSON_ESSENCE").getAsInt();
               } else if (attrMenu.has("essence")) {
                  attributeShardEssenceBonusPct = attrMenu.get("essence").getAsInt();
               }
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   private static String getStringOrNull(JsonObject obj, String key) {
      if (obj != null && obj.has(key)) {
         JsonElement el = obj.get(key);
         return el.isJsonNull() ? null : el.getAsString();
      } else {
         return null;
      }
   }

   public static JsonObject getTargetMember(JsonObject root, String username) {
      if (root == null) {
         return null;
      } else if (!root.has("nether_island_player_data") && !root.has("player_data") && !root.has("essences") && !root.has("pets_data")) {
         JsonObject rawProfile = root.has("raw_profile") && root.get("raw_profile").isJsonObject() ? root.getAsJsonObject("raw_profile") : root;
         if (rawProfile.has("profile") && rawProfile.get("profile").isJsonObject()) {
            rawProfile = rawProfile.getAsJsonObject("profile");
         }

         if (!rawProfile.has("nether_island_player_data") && !rawProfile.has("player_data") && !rawProfile.has("essences")) {
            JsonObject members = rawProfile.has("members") && rawProfile.get("members").isJsonObject() ? rawProfile.getAsJsonObject("members") : null;
            if (members != null && !members.keySet().isEmpty()) {
               if (members.keySet().size() == 1) {
                  String singleUuid = (String)members.keySet().iterator().next();
                  return members.getAsJsonObject(singleUuid);
               } else {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.getUser() != null && username != null && username.equalsIgnoreCase(mc.getUser().getName())) {
                     String localUuid = mc.getUser().getProfileId().toString().replace("-", "");
                     if (members.has(localUuid)) {
                        return members.getAsJsonObject(localUuid);
                     }
                  }

                  try {
                     String mojangUrl = "https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(username, StandardCharsets.UTF_8);
                     HttpURLConnection conn = (HttpURLConnection)(new URL(mojangUrl)).openConnection();
                     conn.setRequestMethod("GET");
                     conn.setConnectTimeout(3000);
                     conn.setReadTimeout(3000);
                     if (conn.getResponseCode() == 200) {
                        InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                        JsonObject mJson = JsonParser.parseReader(reader).getAsJsonObject();
                        reader.close();
                        if (mJson.has("id")) {
                           String fetchedUuid = mJson.get("id").getAsString().replace("-", "");
                           if (members.has(fetchedUuid)) {
                              return members.getAsJsonObject(fetchedUuid);
                           }
                        }
                     }
                  } catch (Exception var10) {
                  }

                  for(String uuid : members.keySet()) {
                     JsonObject m = members.getAsJsonObject(uuid);
                     if (m != null) {
                        return m;
                     }
                  }

                  return null;
               }
            } else {
               return null;
            }
         } else {
            return rawProfile;
         }
      } else {
         return root;
      }
   }

   public static void fetchDataSilent(String targetPlayer) {
      if (targetPlayer == null || targetPlayer.trim().isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         targetPlayer = mc.getUser() != null ? mc.getUser().getName() : "user";
      }

      String name = targetPlayer.trim();
      CompletableFuture.runAsync(() -> {
         try {
            String encodedUser = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String urlData = "https://api.bombo.dpdns.org/data/" + encodedUser;
            String urlShards = "https://api.bombo.dpdns.org/shards/" + encodedUser;
            JsonObject rootData = null;
            JsonObject rootShards = null;

            try {
               HttpRequest req = HttpRequest.newBuilder().uri(URI.create(urlData)).timeout(Duration.ofSeconds(6L)).header("User-Agent", "BomboAddons/1.0").GET().build();
               HttpResponse<String> resp = HTTP.send(req, BodyHandlers.ofString());
               if (resp.statusCode() == 200) {
                  rootData = JsonParser.parseString((String)resp.body()).getAsJsonObject();
               }
            } catch (Exception var12) {
            }

            try {
               HttpRequest req = HttpRequest.newBuilder().uri(URI.create(urlShards)).timeout(Duration.ofSeconds(6L)).header("User-Agent", "BomboAddons/1.0").GET().build();
               HttpResponse<String> resp = HTTP.send(req, BodyHandlers.ofString());
               if (resp.statusCode() == 200) {
                  rootShards = JsonParser.parseString((String)resp.body()).getAsJsonObject();
               }
            } catch (Exception var11) {
            }

            JsonObject memberData = rootData != null ? getTargetMember(rootData, name) : null;
            JsonObject memberShards = rootShards != null ? getTargetMember(rootShards, name) : null;
            JsonObject member = memberData != null ? memberData : memberShards;
            if (memberData != null && memberShards != null) {
               for(Map.Entry<String, JsonElement> entry : memberShards.entrySet()) {
                  if (!memberData.has((String)entry.getKey())) {
                     memberData.add((String)entry.getKey(), (JsonElement)entry.getValue());
                  }
               }

               if (memberShards.has("shards")) {
                  memberData.add("shards", memberShards.get("shards"));
               }

               if (memberShards.has("attributes")) {
                  memberData.add("attributes", memberShards.get("attributes"));
               }

               if (memberShards.has("attributemenu")) {
                  memberData.add("attributemenu", memberShards.get("attributemenu"));
               }

               if (memberShards.has("kuudra_shards")) {
                  memberData.add("kuudra_shards", memberShards.get("kuudra_shards"));
               }
            }

            if (member != null) {
               lastShardResult = parseAttributeShards(member);
               JsonObject playerData = member.getAsJsonObject("player_data");
               if (playerData != null && playerData.has("perks")) {
                  JsonObject perks = playerData.getAsJsonObject("perks");
                  if (perks != null && perks.has("cooled_forges")) {
                     cooledForgesLevel = perks.get("cooled_forges").getAsInt();
                  } else {
                     cooledForgesLevel = 0;
                  }
               }

               JsonObject netherData = member.getAsJsonObject("nether_island_player_data");
               if (netherData != null && netherData.has("selected_faction")) {
                  selectedFaction = netherData.get("selected_faction").getAsString();
               }
            }
         } catch (Exception var13) {
         }

      });
   }

   public static void fetchAndPrintPlayerKuudraData(FabricClientCommandSource source, String targetPlayer) {
      if (targetPlayer == null || targetPlayer.trim().isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         targetPlayer = mc.getUser() != null ? mc.getUser().getName() : "user";
      }

      String name = targetPlayer.trim();
      source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §eFetching Kuudra data for §b" + name + "§e..."));
      CompletableFuture.runAsync(() -> {
         try {
            String encodedUser = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String urlData = "https://api.bombo.dpdns.org/data/" + encodedUser;
            String urlShards = "https://api.bombo.dpdns.org/shards/" + encodedUser;
            JsonObject rootData = null;
            JsonObject rootShards = null;

            try {
               HttpRequest req = HttpRequest.newBuilder().uri(URI.create(urlData)).timeout(Duration.ofSeconds(6L)).header("User-Agent", "BomboAddons/1.0").GET().build();
               HttpResponse<String> resp = HTTP.send(req, BodyHandlers.ofString());
               if (resp.statusCode() == 200) {
                  rootData = JsonParser.parseString((String)resp.body()).getAsJsonObject();
               }
            } catch (Exception var37) {
            }

            try {
               HttpRequest req = HttpRequest.newBuilder().uri(URI.create(urlShards)).timeout(Duration.ofSeconds(6L)).header("User-Agent", "BomboAddons/1.0").GET().build();
               HttpResponse<String> resp = HTTP.send(req, BodyHandlers.ofString());
               if (resp.statusCode() == 200) {
                  rootShards = JsonParser.parseString((String)resp.body()).getAsJsonObject();
               }
            } catch (Exception var36) {
            }

            if (rootData == null && rootShards == null) {
               source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §cFailed to fetch Kuudra API data for player §e" + name + "§c."));
               return;
            }

            JsonObject memberData = rootData != null ? getTargetMember(rootData, name) : null;
            JsonObject memberShards = rootShards != null ? getTargetMember(rootShards, name) : null;
            JsonObject member = memberData != null ? memberData : memberShards;
            if (memberData != null && memberShards != null) {
               for(Map.Entry<String, JsonElement> entry : memberShards.entrySet()) {
                  if (!memberData.has((String)entry.getKey())) {
                     memberData.add((String)entry.getKey(), (JsonElement)entry.getValue());
                  }
               }

               if (memberShards.has("shards")) {
                  memberData.add("shards", memberShards.get("shards"));
               }

               if (memberShards.has("attributes")) {
                  memberData.add("attributes", memberShards.get("attributes"));
               }

               if (memberShards.has("attributemenu")) {
                  memberData.add("attributemenu", memberShards.get("attributemenu"));
               }

               if (memberShards.has("kuudra_shards")) {
                  memberData.add("kuudra_shards", memberShards.get("kuudra_shards"));
               }
            }

            if (member == null) {
               source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §cNo active Skyblock profile found for §e" + name + "§c."));
               return;
            }

            String petTier = "None";
            int petLvl = 0;
            JsonObject petObj = findKuudraPetInMember(member);
            if (petObj != null) {
               petTier = getStringOrNull(petObj, "tier");
               if (petTier == null) {
                  petTier = "None";
               }

               if (petObj.has("exp")) {
                  petLvl = calculatePetLevel(petObj.get("exp").getAsDouble(), petTier);
               } else if (petObj.has("level")) {
                  petLvl = petObj.get("level").getAsInt();
               }
            }

            int petBonus = "LEGENDARY".equalsIgnoreCase(petTier) && petLvl >= 100 ? 20 : (int)((double)petLvl * 0.2);
            long mRep = 0L;
            long bRep = 0L;
            JsonObject netherData = member.getAsJsonObject("nether_island_player_data");
            if (netherData != null) {
               if (netherData.has("mages_reputation")) {
                  mRep = netherData.get("mages_reputation").getAsLong();
               } else if (netherData.has("mage_reputation")) {
                  mRep = netherData.get("mage_reputation").getAsLong();
               }

               if (netherData.has("barbarians_reputation")) {
                  bRep = netherData.get("barbarians_reputation").getAsLong();
               } else if (netherData.has("barbarian_reputation")) {
                  bRep = netherData.get("barbarian_reputation").getAsLong();
               }
            }

            int cooled = 0;
            JsonObject playerData = member.getAsJsonObject("player_data");
            if (playerData != null && playerData.has("perks")) {
               JsonObject perks = playerData.getAsJsonObject("perks");
               if (perks != null && perks.has("cooled_forges")) {
                  cooled = perks.get("cooled_forges").getAsInt();
               }
            }

            long kShards = 0L;
            if (member.has("kuudra_shards")) {
               kShards = member.get("kuudra_shards").getAsLong();
            } else if (member.has("kuudraShards")) {
               kShards = member.get("kuudraShards").getAsLong();
            } else if (netherData != null && netherData.has("kuudra_shards")) {
               kShards = netherData.get("kuudra_shards").getAsLong();
            } else if (netherData != null && netherData.has("shards") && netherData.get("shards").isJsonPrimitive()) {
               kShards = netherData.get("shards").getAsLong();
            }

            int attrShard = 0;
            ShardResult shardRes = parseAttributeShards(member);
            lastShardResult = shardRes;
            long finalMRep = Math.max(0L, mRep);
            long finalBRep = Math.max(0L, bRep);
            long finalKShards = Math.max(0L, kShards);
            final String finalPetTier = petTier;
            final int finalPetLvl = petLvl;
            final int finalPetBonus = petBonus;
            final int finalCooled = cooled;
            Minecraft.getInstance().execute(() -> {
               source.sendFeedback(Component.literal("§8---------------------------------------------------------"));
               source.sendFeedback(Component.literal("§8--- §b[Kuudra Info & Pet Statistics: §e" + name + "§b] §8---"));
               source.sendFeedback(Component.literal("§7Kuudra Pet: §e" + finalPetTier + " Level " + finalPetLvl + " §7(§a+" + finalPetBonus + "% Crimson Essence§7)"));
               source.sendFeedback(Component.literal("§7Cooled Forges Perk: §bLevel " + finalCooled + " §7(§a+" + finalCooled * 4 + "% Crimson Essence§7)"));
               source.sendFeedback(Component.literal("§7Crimson Essence Shard: §eLevel " + shardRes.crimsonLevel + " §7/ 10 (§a+" + shardRes.crimsonLevel + "% base§7)"));
               source.sendFeedback(Component.literal("§7Echo of Essence Shard: §eLevel " + shardRes.echoEssenceLevel + " §7/ 10 (§a+" + shardRes.echoEssenceLevel * 2 + "% boost§7)"));
               source.sendFeedback(Component.literal("§7Echo of Echoes Shard: §eLevel " + shardRes.echoEchoesLevel + " §7/ 10 (§a+" + shardRes.echoEchoesLevel * 5 + "% boost§7)"));
               Object[] var10002 = new Object[]{shardRes.getTotalBonusPct()};
               source.sendFeedback(Component.literal("§7Total Attribute Shards Bonus: §a+" + String.format("%.1f", var10002) + "% Crimson Essence"));
               String var10001 = String.format("%,d", finalMRep);
               source.sendFeedback(Component.literal("§7Mage Reputation: §b" + var10001 + " §7| Barbarian Reputation: §c" + String.format("%,d", finalBRep)));
               var10002 = new Object[]{finalKShards};
               source.sendFeedback(Component.literal("§7Kuudra Shards: §6" + String.format("%,d", var10002)));
               source.sendFeedback(Component.literal("§8---------------------------------------------------------"));
            });
         } catch (Exception e) {
            source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §cError parsing Kuudra data: " + e.getMessage()));
         }

      });
   }

   public static int getShardLevel(long count, String rarity) {
      if (count <= 0L) {
         return 0;
      } else {
         int[] thresholds;
         if ("RARE".equalsIgnoreCase(rarity)) {
            thresholds = new int[]{1, 3, 6, 9, 13, 17, 22, 28, 36, 48};
         } else if ("EPIC".equalsIgnoreCase(rarity)) {
            thresholds = new int[]{1, 2, 4, 6, 9, 12, 16, 20, 25, 32};
         } else if ("LEGENDARY".equalsIgnoreCase(rarity)) {
            thresholds = new int[]{1, 2, 3, 5, 7, 9, 12, 15, 19, 24};
         } else {
            thresholds = new int[]{1, 4, 9, 15, 22, 30, 40, 54, 72, 96};
         }

         if (count <= 10L && count <= (long)thresholds[0]) {
            return (int)count;
         } else {
            for(int lvl = 10; lvl >= 1; --lvl) {
               if (count >= (long)thresholds[lvl - 1]) {
                  return lvl;
               }
            }

            return 0;
         }
      }
   }

   public static ShardResult parseAttributeShards(JsonObject member) {
      ShardResult res = new ShardResult();
      if (member == null) {
         return res;
      } else {
         JsonObject stacks = null;
         if (member.has("attributes") && member.get("attributes").isJsonObject()) {
            JsonObject attrs = member.getAsJsonObject("attributes");
            if (attrs.has("stacks") && attrs.get("stacks").isJsonObject()) {
               stacks = attrs.getAsJsonObject("stacks");
            } else {
               stacks = attrs;
            }
         }

         if (stacks == null && member.has("attributemenu") && member.get("attributemenu").isJsonObject()) {
            JsonObject am = member.getAsJsonObject("attributemenu");
            if (am.has("stacks") && am.get("stacks").isJsonObject()) {
               stacks = am.getAsJsonObject("stacks");
            } else {
               stacks = am;
            }
         }

         if (stacks == null && member.has("attribute_shards") && member.get("attribute_shards").isJsonObject()) {
            JsonObject as = member.getAsJsonObject("attribute_shards");
            if (as.has("stacks") && as.get("stacks").isJsonObject()) {
               stacks = as.getAsJsonObject("stacks");
            } else {
               stacks = as;
            }
         }

         if (stacks != null) {
            long rawCrimson = getJsonLong(stacks, "crimson_essence");
            long rawEchoEssence = getJsonLong(stacks, "echo_of_essence");
            long rawEchoEchoes = getJsonLong(stacks, "echo_of_echoes");
            res.crimsonLevel = getShardLevel(rawCrimson, "RARE");
            res.echoEssenceLevel = getShardLevel(rawEchoEssence, "EPIC");
            res.echoEchoesLevel = getShardLevel(rawEchoEchoes, "LEGENDARY");
         }

         return res;
      }
   }

   private static long getJsonLong(JsonObject obj, String key) {
      if (obj == null) {
         return 0L;
      } else if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
         return obj.get(key).getAsLong();
      } else {
         String upper = key.toUpperCase();
         return obj.has(upper) && obj.get(upper).isJsonPrimitive() ? obj.get(upper).getAsLong() : 0L;
      }
   }

   public static JsonObject findKuudraPetInMember(JsonObject member) {
      if (member == null) {
         return null;
      } else {
         List<JsonObject> kuudraPets = new ArrayList();
         JsonArray petsArray = null;
         if (member.has("pets_data") && member.get("pets_data").isJsonObject()) {
            JsonObject petsData = member.getAsJsonObject("pets_data");
            if (petsData.has("pets") && petsData.get("pets").isJsonArray()) {
               petsArray = petsData.getAsJsonArray("pets");
            }
         }

         if (petsArray == null && member.has("pets") && member.get("pets").isJsonArray()) {
            petsArray = member.getAsJsonArray("pets");
         }

         if (petsArray != null) {
            for(JsonElement el : petsArray) {
               if (el.isJsonObject()) {
                  JsonObject pet = el.getAsJsonObject();
                  if (pet.has("type") && "KUUDRA".equalsIgnoreCase(pet.get("type").getAsString())) {
                     kuudraPets.add(pet);
                  }
               }
            }
         }

         if (kuudraPets.isEmpty()) {
            findKuudraPetsRecursive(member, kuudraPets);
         }

         if (kuudraPets.isEmpty()) {
            return null;
         } else {
            for(JsonObject pet : kuudraPets) {
               if (pet.has("active") && pet.get("active").getAsBoolean()) {
                  return pet;
               }
            }

            kuudraPets.sort((a, b) -> {
               int tierA = getTierScore(a.has("tier") ? a.get("tier").getAsString() : "");
               int tierB = getTierScore(b.has("tier") ? b.get("tier").getAsString() : "");
               if (tierA != tierB) {
                  return Integer.compare(tierB, tierA);
               } else {
                  double expA = a.has("exp") ? a.get("exp").getAsDouble() : (double)0.0F;
                  double expB = b.has("exp") ? b.get("exp").getAsDouble() : (double)0.0F;
                  return Double.compare(expB, expA);
               }
            });
            return (JsonObject)kuudraPets.get(0);
         }
      }
   }

   private static void findKuudraPetsRecursive(JsonElement element, List<JsonObject> list) {
      if (element != null && !element.isJsonNull()) {
         if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("type") && !obj.get("type").isJsonNull() && "KUUDRA".equalsIgnoreCase(obj.get("type").getAsString())) {
               list.add(obj);
               return;
            }

            for(Map.Entry<String, JsonElement> entry : obj.entrySet()) {
               findKuudraPetsRecursive((JsonElement)entry.getValue(), list);
            }
         } else if (element.isJsonArray()) {
            for(JsonElement item : element.getAsJsonArray()) {
               findKuudraPetsRecursive(item, list);
            }
         }

      }
   }

   private static int getTierScore(String tier) {
      if (!"MYTHIC".equalsIgnoreCase(tier) && !"LEGENDARY".equalsIgnoreCase(tier)) {
         if ("EPIC".equalsIgnoreCase(tier)) {
            return 4;
         } else if ("RARE".equalsIgnoreCase(tier)) {
            return 3;
         } else if ("UNCOMMON".equalsIgnoreCase(tier)) {
            return 2;
         } else {
            return "COMMON".equalsIgnoreCase(tier) ? 1 : 0;
         }
      } else {
         return 5;
      }
   }

   private static int calculatePetLevel(double exp, String tier) {
      double remaining = exp;
      int level = 1;
      int offset = 0;
      if ("UNCOMMON".equalsIgnoreCase(tier)) {
         offset = 6;
      } else if ("RARE".equalsIgnoreCase(tier)) {
         offset = 11;
      } else if ("EPIC".equalsIgnoreCase(tier)) {
         offset = 16;
      } else if ("LEGENDARY".equalsIgnoreCase(tier) || "MYTHIC".equalsIgnoreCase(tier)) {
         offset = 20;
      }

      for(double[] petLevels = new double[]{(double)100.0F, (double)110.0F, (double)120.0F, (double)130.0F, (double)145.0F, (double)160.0F, (double)175.0F, (double)190.0F, (double)210.0F, (double)230.0F, (double)250.0F, (double)275.0F, (double)300.0F, (double)330.0F, (double)360.0F, (double)400.0F, (double)440.0F, (double)490.0F, (double)540.0F, (double)600.0F, (double)660.0F, (double)730.0F, (double)800.0F, (double)880.0F, (double)960.0F, (double)1050.0F, (double)1150.0F, (double)1260.0F, (double)1380.0F, (double)1500.0F, (double)1640.0F, (double)1790.0F, (double)1960.0F, (double)2140.0F, (double)2340.0F, (double)2550.0F, (double)2780.0F, (double)3030.0F, (double)3300.0F, (double)3600.0F, (double)3930.0F, (double)4290.0F, (double)4680.0F, (double)5100.0F, (double)5560.0F, (double)6060.0F, (double)6600.0F, (double)7190.0F, (double)7830.0F, (double)8520.0F, (double)9270.0F, (double)10080.0F, (double)10960.0F, (double)11910.0F, (double)12940.0F, (double)14060.0F, (double)15270.0F, (double)16580.0F, (double)18000.0F, (double)19540.0F, (double)21210.0F, (double)23020.0F, (double)24980.0F, (double)27100.0F, (double)29390.0F, (double)31860.0F, (double)34530.0F, (double)37420.0F, (double)40540.0F, (double)43910.0F, (double)47550.0F, (double)51480.0F, (double)55720.0F, (double)60290.0F, (double)65210.0F, (double)70510.0F, (double)76220.0F, (double)82360.0F, (double)88960.0F, (double)96050.0F, (double)103660.0F, (double)111820.0F, (double)120560.0F, (double)129910.0F, (double)139900.0F, (double)150560.0F, (double)161920.0F, (double)174020.0F, (double)186890.0F, (double)200560.0F, (double)215070.0F, (double)230450.0F, (double)246740.0F, (double)263980.0F, (double)282210.0F, (double)301470.0F, (double)321800.0F, (double)343240.0F, (double)365830.0F, (double)389610.0F}; remaining > (double)0.0F && level <= 100; ++level) {
         int idx = offset + level - 1;
         double needed = idx < petLevels.length ? petLevels[idx] : petLevels[petLevels.length - 1];
         if (!(remaining >= needed)) {
            break;
         }

         remaining -= needed;
      }

      return Math.min(level, 100);
   }

   public static class ShardResult {
      public int crimsonLevel = 0;
      public int echoEssenceLevel = 0;
      public int echoEchoesLevel = 0;

      public double getEffectiveEchoPct() {
         double baseEcho = (double)this.echoEssenceLevel * (double)2.0F;
         double echoBoost = (double)1.0F + (double)this.echoEchoesLevel * 0.05;
         return baseEcho * echoBoost;
      }

      public double getTotalBonusPct() {
         double baseCrimson = (double)this.crimsonLevel * (double)1.0F;
         return baseCrimson * ((double)1.0F + this.getEffectiveEchoPct() / (double)100.0F);
      }
   }
}
