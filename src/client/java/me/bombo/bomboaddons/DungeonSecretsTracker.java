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
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public class DungeonSecretsTracker {
   public static final Map<String, Integer> startSecrets = new HashMap();
   public static final Map<String, Integer> endSecrets = new HashMap();
   private static boolean runActive = false;
   private static boolean printedFinal = false;
   private static final Object lock = new Object();

   public static void onChatMessage(String clean) {
      if (BomboConfig.get().dungeonSecretsTracker) {
         if (clean.contains("Starting in 3 seconds.")) {
            String area = BomboaddonsClient.currentArea;
            if (area != null && area.toLowerCase().contains("dungeon")) {
               runActive = true;
               printedFinal = false;
               startSecrets.clear();
               endSecrets.clear();
               Minecraft mc = Minecraft.getInstance();
               if (mc.getConnection() != null && mc.player != null) {
                  if (BomboConfig.get().dungeonSecretsDebug) {
                     mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §7Dungeon start detected! Fetching secrets..."));
                  }

                  for(PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                     String name = info.getProfile().name();
                     if (name != null && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                        UUID uuid = info.getProfile().id();
                        fetchSecrets(name, uuid, true);
                     }
                  }
               }
            }
         }

         if (runActive && (clean.contains("☠ Defeated ") || clean.contains("Defeated ") && clean.contains(" in "))) {
            runActive = false;
            Minecraft mc = Minecraft.getInstance();
            if (BomboConfig.get().dungeonSecretsDebug && mc.player != null) {
               mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §7Dungeon end detected! Scheduling final fetch in 2s..."));
            }

            (new Thread(() -> {
               try {
                  Thread.sleep(2000L);
               } catch (InterruptedException var5) {
               }

               Minecraft client = Minecraft.getInstance();
               if (client.getConnection() != null) {
                  for(PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                     String name = info.getProfile().name();
                     if (name != null && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                        UUID uuid = info.getProfile().id();
                        if (startSecrets.containsKey(name)) {
                           fetchSecrets(name, uuid, false);
                        }
                     }
                  }
               }

            })).start();
         }

      }
   }

   public static void fetchSecrets(String name, UUID uuid, boolean isStart) {
      String dashedUuid = uuid.toString();
      String undashedUuid = dashedUuid.replace("-", "");
      tryFetch(name, BomboApiUrl.getApiUrl("/" + dashedUuid), isStart, (failed1) -> {
         if (failed1) {
            tryFetch(name, "https://profile.snailify.workers.dev/?uuid=" + dashedUuid, isStart, (failed2) -> {
               if (failed2) {
                  tryFetch(name, BomboApiUrl.getApiUrl("/" + undashedUuid), isStart, (failed3) -> {
                     if (failed3) {
                        tryFetch(name, "https://profile.snailify.workers.dev/?uuid=" + undashedUuid, isStart, (failed4) -> {
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

   private static void tryFetch(String name, String urlStr, boolean isStart, Consumer<Boolean> onDone) {
      try {
         HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlStr)).timeout(Duration.ofSeconds(5L)).build();
         client.sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
            if (response.statusCode() == 200) {
               String body = (String)response.body();
               Pattern pattern = Pattern.compile("\"secrets\"\\s*:\\s*(\\d+)");
               Matcher matcher = pattern.matcher(body);
               if (matcher.find()) {
                  int secrets = Integer.parseInt(matcher.group(1));
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

                        checkAndPrintDiff();
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

   public static void checkAndPrintDiff() {
      synchronized(lock) {
         if (!printedFinal) {
            boolean allResolved = true;

            for(String name : startSecrets.keySet()) {
               if (!endSecrets.containsKey(name)) {
                  allResolved = false;
                  break;
               }
            }

            if (allResolved && !startSecrets.isEmpty()) {
               printedFinal = true;
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null) {
                  for(String name : startSecrets.keySet()) {
                     int start = (Integer)startSecrets.get(name);
                     int end = (Integer)endSecrets.get(name);
                     int diff = end - start;
                     if (diff < 0) {
                        diff = 0;
                     }

                     mc.player.sendSystemMessage(Component.literal("§9[Bombo] §e" + name + "§f: §b" + diff + " Secrets"));
                  }
               }
            }

         }
      }
   }

   public static void fetchAndPrintSecrets(String name, UUID uuid) {
      String dashedUuid = uuid.toString();
      String undashedUuid = dashedUuid.replace("-", "");
      tryFetchSingle(name, BomboApiUrl.getApiUrl("/" + dashedUuid), (failed1) -> {
         if (failed1) {
            tryFetchSingle(name, "https://profile.snailify.workers.dev/?uuid=" + dashedUuid, (failed2) -> {
               if (failed2) {
                  tryFetchSingle(name, BomboApiUrl.getApiUrl("/" + undashedUuid), (failed3) -> {
                     if (failed3) {
                        tryFetchSingle(name, "https://profile.snailify.workers.dev/?uuid=" + undashedUuid, (failed4) -> Minecraft.getInstance().execute(() -> {
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

   private static void tryFetchSingle(String name, String urlStr, Consumer<Boolean> onDone) {
      try {
         HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlStr)).timeout(Duration.ofSeconds(5L)).build();
         client.sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
            if (response.statusCode() == 200) {
               String body = (String)response.body();
               Pattern pattern = Pattern.compile("\"secrets\"\\s*:\\s*(\\d+)");
               Matcher matcher = pattern.matcher(body);
               if (matcher.find()) {
                  int secrets = Integer.parseInt(matcher.group(1));
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
}
