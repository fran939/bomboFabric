package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonSecretsTracker {
    public static final Map<String, Integer> startSecrets = new HashMap<>();
    public static final Map<String, Integer> endSecrets = new HashMap<>();
    private static boolean runActive = false;
    private static boolean printedFinal = false;
    private static final Object lock = new Object();

    public static void onChatMessage(String clean) {
        if (!BomboConfig.get().dungeonSecretsTracker) return;

        // Start detection: Starting in 3 seconds.
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
                    for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                        String name = info.getProfile().name();
                        if (name != null && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                            UUID uuid = info.getProfile().id();
                            fetchSecrets(name, uuid, true);
                        }
                    }
                }
            }
        }

        // End detection: ☠ Defeated (boss) in (time)
        if (runActive && (clean.contains("☠ Defeated ") || (clean.contains("Defeated ") && clean.contains(" in ")))) {
            runActive = false;
            
            Minecraft mc = Minecraft.getInstance();
            if (BomboConfig.get().dungeonSecretsDebug) {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§8[§bBombo Debug§8] §7Dungeon end detected! Scheduling final fetch in 2s..."));
                }
            }

            // Fetch final secrets after 2 seconds to let Hypixel API sync
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}

                Minecraft client = Minecraft.getInstance();
                if (client.getConnection() != null) {
                    for (net.minecraft.client.multiplayer.PlayerInfo info : client.getConnection().getOnlinePlayers()) {
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

    public static void fetchSecrets(String name, UUID uuid, boolean isStart) {
        String dashedUuid = uuid.toString();
        String undashedUuid = dashedUuid.replace("-", "");

        // Try main bomboapi URL with dashed UUID
        tryFetch(name, me.bombo.bomboaddons.util.BomboApiUrl.getApiUrl("/" + dashedUuid), isStart, failed1 -> {
            if (failed1) {
                // Try workers url 2 (snailify) with dashed UUID
                tryFetch(name, "https://profile.snailify.workers.dev/?uuid=" + dashedUuid, isStart, failed2 -> {
                    if (failed2) {
                        // Try main bomboapi URL with undashed UUID
                        tryFetch(name, me.bombo.bomboaddons.util.BomboApiUrl.getApiUrl("/" + undashedUuid), isStart, failed3 -> {
                            if (failed3) {
                                // Try workers url 2 with undashed UUID
                                tryFetch(name, "https://profile.snailify.workers.dev/?uuid=" + undashedUuid, isStart, failed4 -> {
                                    if (BomboConfig.get().dungeonSecretsDebug) {
                                        Minecraft.getInstance().execute(() -> {
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.player != null) {
                                                mc.player.sendSystemMessage(Component.literal(
                                                    "§8[§bBombo Debug§8] §cFailed to fetch secrets for §e" + name
                                                ));
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

    private static void tryFetch(String name, String urlStr, boolean isStart, java.util.function.Consumer<Boolean> onDone) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(Duration.ofSeconds(5))
                .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        String body = response.body();
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
                                            mc.player.sendSystemMessage(Component.literal(
                                                "§8[§bBombo Debug§8] §aStart secrets for §e" + name + "§a: §d" + secrets
                                            ));
                                        }
                                    }
                                } else {
                                    endSecrets.put(name, secrets);
                                    if (BomboConfig.get().dungeonSecretsDebug) {
                                        Minecraft mc = Minecraft.getInstance();
                                        if (mc.player != null) {
                                            mc.player.sendSystemMessage(Component.literal(
                                                "§8[§bBombo Debug§8] §aEnd secrets for §e" + name + "§a: §d" + secrets
                                            ));
                                        }
                                    }
                                    checkAndPrintDiff();
                                }
                            });
                            onDone.accept(false); // Success!
                        } else {
                            onDone.accept(true);
                        }
                    } else {
                        onDone.accept(true);
                    }
                })
                .exceptionally(ex -> {
                    onDone.accept(true);
                    return null;
                });
        } catch (Throwable t) {
            onDone.accept(true);
        }
    }

    public static void checkAndPrintDiff() {
        synchronized (lock) {
            if (printedFinal) return;
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
                    for (String name : startSecrets.keySet()) {
                        int start = startSecrets.get(name);
                        int end = endSecrets.get(name);
                        int diff = end - start;
                        if (diff < 0) diff = 0;
                        mc.player.sendSystemMessage(Component.literal(
                            "§9[Bombo] §e" + name + "§f: §b" + diff + " Secrets"
                        ));
                    }
                }
            }
        }
    }

    public static void fetchAndPrintSecrets(String name, UUID uuid) {
        String dashedUuid = uuid.toString();
        String undashedUuid = dashedUuid.replace("-", "");

        tryFetchSingle(name, me.bombo.bomboaddons.util.BomboApiUrl.getApiUrl("/" + dashedUuid), failed1 -> {
            if (failed1) {
                tryFetchSingle(name, "https://profile.snailify.workers.dev/?uuid=" + dashedUuid, failed2 -> {
                    if (failed2) {
                        tryFetchSingle(name, me.bombo.bomboaddons.util.BomboApiUrl.getApiUrl("/" + undashedUuid), failed3 -> {
                            if (failed3) {
                                tryFetchSingle(name, "https://profile.snailify.workers.dev/?uuid=" + undashedUuid, failed4 -> {
                                    Minecraft.getInstance().execute(() -> {
                                        Minecraft mc = Minecraft.getInstance();
                                        if (mc.player != null) {
                                            mc.player.sendSystemMessage(Component.literal("  §e" + name + "§f: §cFailed to fetch"));
                                        }
                                    });
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private static void tryFetchSingle(String name, String urlStr, java.util.function.Consumer<Boolean> onDone) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(Duration.ofSeconds(5))
                .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        String body = response.body();
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
                })
                .exceptionally(ex -> {
                    onDone.accept(true);
                    return null;
                });
        } catch (Throwable t) {
            onDone.accept(true);
        }
    }
}
