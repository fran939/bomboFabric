package me.bombo.bomboaddons;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public class DungeonSecretsTracker {
   public static final Map<String, Integer> startSecrets = new HashMap<>();
   public static final Map<String, Integer> endSecrets = new HashMap<>();
   private static boolean runActive = false;
   private static boolean printedFinal = false;
   private static boolean fetchedAtNecron = false;
   private static final Object lock = new Object();

   public static void onChatMessage(String clean) {
      if (BomboConfig.get().dungeonSecretsTracker) {
         if (clean.contains("Starting in 3 seconds.")) {
            String area = BomboaddonsClient.currentArea;
            if (area != null && area.toLowerCase().contains("dungeon")) {
               runActive = true;
               printedFinal = false;
               fetchedAtNecron = false;
               startSecrets.clear();
               endSecrets.clear();
               Minecraft mc = Minecraft.getInstance();
               if (mc.getConnection() != null && mc.player != null) {
                  if (BomboConfig.get().dungeonSecretsDebug) {
                     mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §7Dungeon start detected! Fetching secrets..."));
                  }

                  for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                     String name = info.getProfile().name();
                     UUID uuid = info.getProfile().id();
                     if (name != null && isValidPlayer(name, uuid)) {
                        fetchSecrets(name, uuid, true);
                     }
                  }
               }
            }
         } else if (clean.contains("[BOSS] Necron:") || clean.contains("You went further than any human before")) {
            if (runActive && !fetchedAtNecron) {
               fetchedAtNecron = true;
               Minecraft mc = Minecraft.getInstance();
               if (BomboConfig.get().dungeonSecretsDebug && mc.player != null) {
                  mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §7Necron phase detected! Pre-fetching end secrets in background..."));
               }
               new Thread(() -> {
                  Minecraft client = Minecraft.getInstance();
                  if (client.getConnection() != null) {
                     for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                        String name = info.getProfile().name();
                        if (name != null && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                           UUID uuid = info.getProfile().id();
                           if (startSecrets.containsKey(name)) {
                              fetchSecrets(name, uuid, false);
                           }
                        }
                     }
                  }
               }).start();
            }
         } else if ((clean.contains("Team Score:") && clean.contains("(")) || clean.contains("EXTRA STATS") || clean.contains("☠ Defeated ") || (clean.contains("Defeated ") && clean.contains(" in "))) {
            if (runActive) {
               runActive = false;
               Minecraft mc = Minecraft.getInstance();
               BomboConfig.Settings s = BomboConfig.get();
               long delayMs = 0L;
               if (s != null && "DELAYED".equalsIgnoreCase(s.dungeonSecretsTriggerMode)) {
                  delayMs = Math.max(0, s.dungeonSecretsDelay) * 1000L;
               }

               final long waitTime = delayMs;
               if (fetchedAtNecron && !endSecrets.isEmpty()) {
                  if (BomboConfig.get().dungeonSecretsDebug && mc.player != null) {
                     mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §7Dungeon end detected! Waiting " + (waitTime / 1000.0) + "s to display pre-calculated secrets..."));
                  }
                  new Thread(() -> {
                     if (waitTime > 0) {
                        try {
                           Thread.sleep(waitTime);
                        } catch (InterruptedException ignored) {}
                     }
                     checkAndPrintDiff();
                  }).start();
               } else {
                  if (BomboConfig.get().dungeonSecretsDebug && mc.player != null) {
                     mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §7Dungeon end detected! Waiting " + (waitTime / 1000.0) + "s before fetching secrets..."));
                  }

                  new Thread(() -> {
                     if (waitTime > 0) {
                        try {
                           Thread.sleep(waitTime);
                        } catch (InterruptedException ignored) {}
                     }

                     Minecraft client = Minecraft.getInstance();
                     if (client.getConnection() != null) {
                        if (BomboConfig.get().dungeonSecretsDebug && client.player != null) {
                           client.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §7Fetching end secrets..."));
                        }

                        for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                           String name = info.getProfile().name();
                           if (name != null && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                              UUID uuid = info.getProfile().id();
                              if (startSecrets.containsKey(name)) {
                                 fetchSecrets(name, uuid, false);
                              }
                           }
                        }
                     }
                  }).start();
               }
            }
         }
      }
   }

   public static void fetchSecrets(String name, UUID uuid, boolean isStart) {
      String dashedUuid = uuid.toString();
      String undashedUuid = dashedUuid.replace("-", "");
      tryFetch(name, uuid, BomboApiUrl.getApiUrl("/data/" + dashedUuid), isStart, (failed1) -> {
         if (failed1) {
            tryFetch(name, uuid, "https://profile.snailify.workers.dev/?uuid=" + dashedUuid, isStart, (failed2) -> {
               if (failed2) {
                  tryFetch(name, uuid, BomboApiUrl.getApiUrl("/data/" + undashedUuid), isStart, (failed3) -> {
                     if (failed3) {
                        tryFetch(name, uuid, "https://profile.snailify.workers.dev/?uuid=" + undashedUuid, isStart, (failed4) -> {
                           if (BomboConfig.get().dungeonSecretsDebug) {
                              Minecraft.getInstance().execute(() -> {
                                 Minecraft mc = Minecraft.getInstance();
                                 if (mc.player != null) {
                                    mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §cFailed to fetch secrets for §e" + name));
                                 }
                              });
                           }
                        });
                     }
                  });
               }
            });
         }
      });
   }

   private static Integer extractSecretsFromJson(String body, UUID uuid) {
      if (body == null || body.trim().isEmpty()) return null;
      try {
         JsonElement parsed = JsonParser.parseString(body);
         if (!parsed.isJsonObject()) return null;
         JsonObject root = parsed.getAsJsonObject();

         String dashed = uuid != null ? uuid.toString() : "";
         String undashed = dashed.replace("-", "");

         // Snailify format: { "profiles": [ { "selected": true, "members": { ... } } ] }
         if (root.has("profiles") && root.get("profiles").isJsonArray()) {
            JsonArray profiles = root.getAsJsonArray("profiles");
            JsonObject selectedProfile = null;
            for (JsonElement el : profiles) {
               if (el.isJsonObject()) {
                  JsonObject p = el.getAsJsonObject();
                  if (p.has("selected") && p.get("selected").getAsBoolean()) {
                     selectedProfile = p;
                     break;
                  }
               }
            }
            if (selectedProfile == null && profiles.size() > 0 && profiles.get(0).isJsonObject()) {
               selectedProfile = profiles.get(0).getAsJsonObject();
            }
            if (selectedProfile != null) {
               root = selectedProfile;
            }
         }

         JsonObject members = null;
         if (root.has("raw_profile") && root.get("raw_profile").isJsonObject()) {
            JsonObject rawProfile = root.getAsJsonObject("raw_profile");
            if (rawProfile.has("members") && rawProfile.get("members").isJsonObject()) {
               members = rawProfile.getAsJsonObject("members");
            }
         } else if (root.has("members") && root.get("members").isJsonObject()) {
            members = root.getAsJsonObject("members");
         }

         if (members != null) {
            JsonObject targetMember = null;
            if (members.has(undashed) && members.get(undashed).isJsonObject()) {
               targetMember = members.getAsJsonObject(undashed);
            } else if (members.has(dashed) && members.get(dashed).isJsonObject()) {
               targetMember = members.getAsJsonObject(dashed);
            } else {
               for (Map.Entry<String, JsonElement> entry : members.entrySet()) {
                  if (entry.getKey().replace("-", "").equalsIgnoreCase(undashed)) {
                     if (entry.getValue().isJsonObject()) {
                        targetMember = entry.getValue().getAsJsonObject();
                        break;
                     }
                  }
               }
            }

            if (targetMember != null && targetMember.has("dungeons") && targetMember.get("dungeons").isJsonObject()) {
               JsonObject dungeons = targetMember.getAsJsonObject("dungeons");
               if (dungeons.has("secrets")) {
                  return dungeons.get("secrets").getAsInt();
               }
            }
         }

         if (root.has("dungeons") && root.get("dungeons").isJsonObject()) {
            JsonObject dungeons = root.getAsJsonObject("dungeons");
            if (dungeons.has("secrets")) {
               return dungeons.get("secrets").getAsInt();
            }
         }

         if (root.has("secrets") && root.get("secrets").isJsonPrimitive()) {
            return root.get("secrets").getAsInt();
         }
      } catch (Throwable ignored) {}

      return null;
   }

   private static void tryFetch(String name, UUID uuid, String urlStr, boolean isStart, Consumer<Boolean> onDone) {
      try {
         HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlStr)).timeout(Duration.ofSeconds(5L)).build();
         client.sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
            if (response.statusCode() == 200) {
               String body = response.body();
               Integer secretsVal = extractSecretsFromJson(body, uuid);
               if (secretsVal != null) {
                  int secrets = secretsVal;
                  Minecraft.getInstance().execute(() -> {
                     if (isStart) {
                        startSecrets.put(name, secrets);
                        if (BomboConfig.get().dungeonSecretsDebug) {
                           Minecraft mc = Minecraft.getInstance();
                           if (mc.player != null) {
                              mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §aStart secrets for §e" + name + "§a: §d" + secrets));
                           }
                        }
                     } else {
                        endSecrets.put(name, secrets);
                        if (BomboConfig.get().dungeonSecretsDebug) {
                           Minecraft mc = Minecraft.getInstance();
                           if (mc.player != null) {
                              mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §aEnd secrets for §e" + name + "§a: §d" + secrets));
                           }
                        }

                        // Only print immediately if the run has already finished
                        if (!runActive) {
                           checkAndPrintDiff();
                        }
                     }
                  });
                  onDone.accept(false);
               } else {
                  onDone.accept(true);
               }
            } else {
               onDone.accept(true);
            }
         }).exceptionally((ex) -> {
            onDone.accept(true);
            return null;
         });
      } catch (Throwable var6) {
         onDone.accept(true);
      }
   }

   private static void checkAndPrintDiff() {
      synchronized (lock) {
         if (!printedFinal) {
            boolean allResolved = true;

            for (String name : startSecrets.keySet()) {
               if (!endSecrets.containsKey(name)) {
                  allResolved = false;
                  break;
               }
            }

            if (allResolved && !startSecrets.isEmpty()) {
               printedFinal = true;
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null) {
                  StringBuilder sb = new StringBuilder("§8[§3Bombo§8] §bSecret found: ");
                  boolean first = true;

                  for (String name : startSecrets.keySet()) {
                     int start = startSecrets.get(name);
                     int end = endSecrets.get(name);
                     int diff = end - start;
                     if (diff < 0) {
                        diff = 0;
                     }

                     if (!first) {
                        sb.append(" §7| ");
                     }
                     sb.append("§e").append(name).append(" §f").append(diff);
                     first = false;
                  }

                  mc.player.sendSystemMessage(Component.literal(sb.toString()));
               }
            }
         }
      }
   }

   public static void fetchAndPrintSecrets(String name, UUID uuid) {
      String dashedUuid = uuid.toString();
      String undashedUuid = dashedUuid.replace("-", "");
      tryFetchSingle(name, uuid, BomboApiUrl.getApiUrl("/data/" + dashedUuid), (failed1) -> {
         if (failed1) {
            tryFetchSingle(name, uuid, "https://profile.snailify.workers.dev/?uuid=" + dashedUuid, (failed2) -> {
               if (failed2) {
                  tryFetchSingle(name, uuid, BomboApiUrl.getApiUrl("/data/" + undashedUuid), (failed3) -> {
                     if (failed3) {
                        tryFetchSingle(name, uuid, "https://profile.snailify.workers.dev/?uuid=" + undashedUuid, (failed4) -> Minecraft.getInstance().execute(() -> {
                           Minecraft mc = Minecraft.getInstance();
                           if (mc.player != null) {
                              mc.player.sendSystemMessage(Component.literal("  §e" + name + "§f: §cFailed to fetch"));
                           }
                        }));
                     }
                  });
               }
            });
         }
      });
   }

   private static void tryFetchSingle(String name, UUID uuid, String urlStr, Consumer<Boolean> onDone) {
      try {
         HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlStr)).timeout(Duration.ofSeconds(5L)).build();
         client.sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
            if (response.statusCode() == 200) {
               String body = response.body();
               Integer secretsVal = extractSecretsFromJson(body, uuid);
               if (secretsVal != null) {
                  int secrets = secretsVal;
                  Minecraft.getInstance().execute(() -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("  §e" + name + "§f: §d" + secrets));
                     }
                  });
                  onDone.accept(false);
               } else {
                  onDone.accept(true);
               }
            } else {
               onDone.accept(true);
            }
         }).exceptionally((ex) -> {
            onDone.accept(true);
            return null;
         });
      } catch (Throwable var5) {
         onDone.accept(true);
      }
   }

   private static boolean isValidPlayer(String name, UUID uuid) {
      if (name == null || name.isEmpty() || name.startsWith("!") || name.startsWith("[NPC]")) return false;
      if (!name.matches("^[a-zA-Z0-9_]{3,16}$")) return false;
      if (uuid == null || uuid.version() != 4) return false;
      // Filter out random alphanumeric NPC dummy strings without vowels or typical hypixel bots
      if (name.length() == 10 && name.matches("^[a-z0-9]+$") && !name.matches(".*[aeiou].*")) return false;
      return true;
   }
}
