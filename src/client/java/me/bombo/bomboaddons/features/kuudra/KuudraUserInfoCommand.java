package me.bombo.bomboaddons.features.kuudra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KuudraUserInfoCommand {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static final String[] TALISMANS = new String[]{"§4No Talisman", "§cKuudra's Kidney", "§aKuudra's Lung", "§2Kuudra's Heart"};

    public static void showKuudraInfo(String playerInput) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String target = (playerInput != null && !playerInput.trim().isEmpty())
                ? playerInput.trim()
                : mc.player.getName().getString();

        mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §7Fetching Kuudra overview for §e" + target + "§7..."));

        new Thread(() -> {
            try {
                String url = BomboApiUrl.getApiUrl("/api/pv/" + target);
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(25))
                        .header("User-Agent", "bomboaddons")
                        .GET();
                BomboApiUrl.attachApiKey(builder);
                HttpRequest req = builder.build();

                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200 || resp.body() == null || resp.body().isEmpty()) {
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cCould not fetch profile data for §e" + target + "§c (Status: " + resp.statusCode() + ")."));
                        }
                    });
                    return;
                }

                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("error")) {
                    String err = root.get("error").getAsString();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cProfile error for §e" + target + "§c: §7" + err));
                        }
                    });
                    return;
                }

                mc.execute(() -> {
                    if (mc.player != null) {
                        Component overview = buildKuudraOverview(root, target);
                        mc.player.sendSystemMessage(overview);
                    }
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cError fetching Kuudra info: §7" + e.getMessage()));
                    }
                });
            }
        }).start();
    }

    private static Component buildKuudraOverview(JsonObject root, String fallbackName) {
        String username = root.has("username") ? root.get("username").getAsString() : fallbackName;
        double sbLevel = root.has("level") ? root.get("level").getAsDouble() : 0.0;
        int mp = root.has("magical_power") ? root.get("magical_power").getAsInt() : 0;
        String power = root.has("power") && !root.get("power").isJsonNull() ? root.get("power").getAsString() : "None";
        String tuning = root.has("tuning") && !root.get("tuning").isJsonNull() ? root.get("tuning").getAsString() : "None";
        String enrichment = root.has("enrichment") && !root.get("enrichment").isJsonNull() ? root.get("enrichment").getAsString() : "None";

        JsonObject crimson = root.has("crimson") && root.get("crimson").isJsonObject() ? root.getAsJsonObject("crimson") : null;
        int basicRuns = crimson != null && crimson.has("basic_runs") ? crimson.get("basic_runs").getAsInt() : 0;
        int hotRuns = crimson != null && crimson.has("hot_runs") ? crimson.get("hot_runs").getAsInt() : 0;
        int burningRuns = crimson != null && crimson.has("burning_runs") ? crimson.get("burning_runs").getAsInt() : 0;
        int fieryRuns = crimson != null && crimson.has("fiery_runs") ? crimson.get("fiery_runs").getAsInt() : 0;
        int infernalRuns = crimson != null && crimson.has("infernal_runs") ? crimson.get("infernal_runs").getAsInt() : 0;
        int totalRuns = basicRuns + hotRuns + burningRuns + fieryRuns + infernalRuns;
        double soreComps = (double)basicRuns * 0.5 + (double)hotRuns + (double)burningRuns * 2.0 + (double)fieryRuns * 4.0 + (double)infernalRuns * 8.0;

        int mageRep = crimson != null && crimson.has("mage_rep") ? crimson.get("mage_rep").getAsInt() : 0;
        int barbRep = crimson != null && crimson.has("barb_rep") ? crimson.get("barb_rep").getAsInt() : 0;

        double cataLevel = 0.0;
        if (root.has("dungeons") && root.get("dungeons").isJsonObject()) {
            JsonObject dung = root.getAsJsonObject("dungeons");
            if (dung.has("cataLevel")) cataLevel = dung.get("cataLevel").getAsDouble();
        }

        long purse = root.has("purse") ? root.get("purse").getAsLong() : 0L;
        long bank = root.has("bank") ? root.get("bank").getAsLong() : 0L;

        // Weapons scan
        boolean hasHyp = false;
        String hypLore = "";
        boolean hasTerm = false;
        String termLore = "";
        int termDuplex = 0;
        boolean termP7 = false;
        boolean termC6 = false;
        boolean termSmold5 = false;
        boolean hasRag = false;
        String ragLore = "";
        int ragChim = 0;
        boolean hasBone = false;
        int boneRend = 0;
        boolean hasBlade = false;
        boolean bladeGk7 = false;
        boolean bladePros6 = false;

        if (root.has("weapons") && root.get("weapons").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("weapons")) {
                if (!el.isJsonObject()) continue;
                JsonObject w = el.getAsJsonObject();
                String name = w.has("name") ? w.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
                String lore = w.has("lore") ? w.get("lore").getAsString() : "";
                if (name.contains("hyperion") || name.contains("scylla") || name.contains("astraea") || name.contains("valkyrie")) {
                    hasHyp = true;
                    hypLore = lore;
                }
                if (name.contains("terminator")) {
                    hasTerm = true;
                    termLore = lore;
                    if (lore.contains("Duplex")) {
                        try {
                            int idx = lore.indexOf("Duplex ");
                            termDuplex = Integer.parseInt(lore.substring(idx + 7, idx + 8));
                        } catch (Exception ignored) {}
                    }
                    if (lore.contains("Power VII") || lore.contains("Power 7")) termP7 = true;
                    if (lore.contains("Cubism VI") || lore.contains("Cubism 6")) termC6 = true;
                    if (lore.contains("Smoldering V") || lore.contains("Smoldering 5")) termSmold5 = true;
                }
                if (name.contains("ragnarock")) {
                    hasRag = true;
                    ragLore = lore;
                    if (lore.contains("Chimera")) {
                        try {
                            int idx = lore.indexOf("Chimera ");
                            ragChim = Integer.parseInt(lore.substring(idx + 8, idx + 9));
                        } catch (Exception ignored) {}
                    }
                }
                if (name.contains("bonemerang")) {
                    hasBone = true;
                    if (lore.contains("Rend")) {
                        try {
                            int idx = lore.indexOf("Rend ");
                            boneRend = Integer.parseInt(lore.substring(idx + 5, idx + 6));
                        } catch (Exception ignored) {}
                    }
                }
                if (name.contains("blade") || name.contains("katana") || name.contains("claymore")) {
                    hasBlade = true;
                    if (lore.contains("Giant Killer VII") || lore.contains("Giant Killer 7")) bladeGk7 = true;
                    if (lore.contains("Prosecute VI") || lore.contains("Prosecute 6")) bladePros6 = true;
                }
            }
        }

        // Armor scan
        boolean hasReaper = false;
        boolean hasHollow = false;
        boolean hasTux = false;
        int totalLegion = 0;
        int totalStrongMana = 0;
        int totalFeroMana = 0;

        if (root.has("armor") && root.get("armor").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("armor")) {
                if (!el.isJsonObject()) continue;
                JsonObject a = el.getAsJsonObject();
                String name = a.has("name") ? a.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
                String lore = a.has("lore") ? a.get("lore").getAsString() : "";
                if (name.contains("reaper")) hasReaper = true;
                if (name.contains("hollow")) hasHollow = true;
                if (name.contains("tuxedo")) hasTux = true;
                if (lore.contains("Legion V") || lore.contains("Legion 5")) totalLegion += 5;
                else if (lore.contains("Legion")) totalLegion += 1;
                if (lore.contains("Strong Mana")) totalStrongMana += 1;
                if (lore.contains("Ferocious Mana")) totalFeroMana += 1;
            }
        }

        // Talisman tier
        int talismanTier = 0;
        if (root.has("accessories") && root.get("accessories").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("accessories")) {
                if (!el.isJsonObject()) continue;
                String name = el.getAsJsonObject().has("name") ? el.getAsJsonObject().get("name").getAsString().toLowerCase(Locale.ROOT) : "";
                if (name.contains("kuudra's heart") || name.contains("kuudra heart")) talismanTier = Math.max(talismanTier, 3);
                else if (name.contains("kuudra's lung") || name.contains("kuudra lung")) talismanTier = Math.max(talismanTier, 2);
                else if (name.contains("kuudra's kidney") || name.contains("kuudra kidney")) talismanTier = Math.max(talismanTier, 1);
            }
        }

        // Pets scan
        List<String> petsList = new ArrayList<>();
        petsList.add("§7* §aBank: §f" + formatCoins(bank));
        petsList.add("§7* §aPurse: §f" + formatCoins(purse));
        if (root.has("pets") && root.get("pets").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("pets")) {
                if (!el.isJsonObject()) continue;
                JsonObject pet = el.getAsJsonObject();
                int lvl = pet.has("level") ? pet.get("level").getAsInt() : 100;
                String petName = pet.has("name") ? pet.get("name").getAsString() : "Pet";
                String petItem = pet.has("pet_item") && !pet.get("pet_item").isJsonNull() ? pet.get("pet_item").getAsString() : "No pet item";
                petsList.add(String.format(Locale.ROOT, "\n§7[Lvl %d] §6%s", lvl, petName));
                petsList.add("§7* §aItem: §f" + petItem);
            }
        }

        // Build Hover Tooltips
        List<String> userList = new ArrayList<>();
        userList.add("§7* §aUsername: §2" + username);
        userList.add(String.format(Locale.ROOT, "§7* §aSB level: §b%.2f", sbLevel));
        userList.add(String.format(Locale.ROOT, "§7* §aKuudra level: §3%.2f", soreComps / 100.0));
        userList.add("§7* §aPurse: §e" + formatCoins(purse));
        String userLore = "§a§lUser Info:\n\n" + String.join("\n", userList);

        List<String> runsList = new ArrayList<>();
        runsList.add("§aBasic: §f" + basicRuns);
        runsList.add("§aHot: §f" + hotRuns);
        runsList.add("§aBurning: §f" + burningRuns);
        runsList.add("§aFiery: §f" + fieryRuns);
        runsList.add("§aInfernal: §f" + infernalRuns);
        runsList.add("\n§a§lTotal: §f" + totalRuns);
        String runsLore = "§a§lRuns Breakdown:\n\n" + String.join("\n", runsList);

        List<String> mpList = new ArrayList<>();
        mpList.add("§7* §aPower: §f§l" + power);
        mpList.add("§7* §aTotal MP: §b" + mp);
        mpList.add("§7* §aTuning: §f" + tuning);
        mpList.add("§7* §aEnrich: §f" + enrichment);
        String mpLore = "§a§lMP Breakdown:\n\n" + String.join("\n", mpList);

        List<String> extraList = new ArrayList<>();
        extraList.add("§7* §aReaper: " + (hasReaper ? "§a✔" : "§4X"));
        extraList.add("§7* §aHollow: " + (hasHollow ? "§a✔" : "§4X"));
        extraList.add("§7* §aLegion: §b" + totalLegion);
        extraList.add("§7* §aReputation: §b" + Math.max(mageRep, barbRep));
        extraList.add(String.format(Locale.ROOT, "§7* §aMana Enchants: §bStrong: %d, Fero: %d", totalStrongMana, totalFeroMana));
        String extraLore = "§a§lExtra Info:\n\n" + String.join("\n", extraList);

        List<String> rendList = new ArrayList<>();
        rendList.add("§7* §aTerm: " + (hasTerm ? ("§a✔ §7(P7: " + (termP7 ? "✔" : "X") + ", C6: " + (termC6 ? "✔" : "X") + ")") : "§4X"));
        rendList.add("§7* §aBone: " + (hasBone ? ("§a✔ §7(Rend " + boneRend + ")") : "§4X"));
        rendList.add("§7* §aBlade: " + (hasBlade ? ("§a✔ §7(GK7: " + (bladeGk7 ? "✔" : "X") + ", Pros6: " + (bladePros6 ? "✔" : "X") + ")") : "§4X"));
        rendList.add("§7* §aTuxedo: " + (hasTux ? "§a✔" : "§4X"));
        String rendLore = "§a§lRend Info:\n\n" + String.join("\n", rendList);

        String petsLore = "§a§lPets Info:\n\n" + String.join("\n", petsList);

        // Build interactive Chat Component
        MutableComponent main = Component.empty();

        String startLine = String.format(Locale.ROOT, "§2§m-----§f[- §7[§e%.0f§7] §2%s §f-]§2§m-----\n", sbLevel, username);
        MutableComponent headerComp = Component.literal(startLine)
                .withStyle(s -> s
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(userLore + "\n\n§eClick to view full profile: /pv " + username)))
                        .withClickEvent(new ClickEvent.RunCommand("/pv " + username))
                );
        main.append(headerComp);

        MutableComponent runsComp = Component.literal(String.format(Locale.ROOT, "§aRuns: §c%d §7(§f%d§7)\n", infernalRuns, totalRuns))
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(runsLore))));
        main.append(runsComp);

        MutableComponent mpComp = Component.literal("§aMagical Power: §6" + mp + " §7(§e" + power + "§7)\n")
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(mpLore))));
        main.append(mpComp);

        main.append(Component.literal(String.format(Locale.ROOT, "§aCata: §c%.2f\n", cataLevel)));

        // Weapons
        MutableComponent hypComp = Component.literal("§aHyperion: " + (hasHyp ? "§a✔" : "§4X") + "\n");
        final String finalHypLore = hypLore;
        if (hasHyp && !finalHypLore.isEmpty()) hypComp.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(finalHypLore))));
        main.append(hypComp);

        String termStatus = hasTerm ? (termDuplex > 0 ? "§a✔ §7(Duplex " + termDuplex + ")" : "§a✔") : "§4X";
        MutableComponent termComp = Component.literal("§aTerminator: " + termStatus + "\n");
        final String finalTermLore = termLore;
        if (hasTerm && !finalTermLore.isEmpty()) termComp.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(finalTermLore))));
        main.append(termComp);

        String ragStatus = hasRag ? (ragChim > 0 ? "§a✔ §7(Chimera " + ragChim + ")" : "§a✔") : "§4X";
        MutableComponent ragComp = Component.literal("§aRagnarock Axe: " + ragStatus + "\n");
        final String finalRagLore = ragLore;
        if (hasRag && !finalRagLore.isEmpty()) ragComp.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(finalRagLore))));
        main.append(ragComp);

        main.append(Component.literal("§aTalisman: " + TALISMANS[Math.min(3, Math.max(0, talismanTier))] + "\n"));

        // Interactive Lore Hovers
        MutableComponent rendComp = Component.literal("§aRend Info §7[HOVER]  ")
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(rendLore))));
        MutableComponent extraComp = Component.literal("§aExtra Info §7[HOVER]  ")
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(extraLore))));
        MutableComponent petComp = Component.literal("§aPets Info §7[HOVER]\n")
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(petsLore))));
        main.append(rendComp);
        main.append(extraComp);
        main.append(petComp);

        main.append(Component.literal("§2§m-----------------------------"));

        return main;
    }

    private static String formatCoins(long coins) {
        if (coins >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2fB", coins / 1_000_000_000.0);
        } else if (coins >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.2fM", coins / 1_000_000.0);
        } else if (coins >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", coins / 1_000.0);
        }
        return String.valueOf(coins);
    }
}
