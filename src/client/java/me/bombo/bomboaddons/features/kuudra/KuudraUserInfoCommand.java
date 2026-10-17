package me.bombo.bomboaddons.features.kuudra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.util.ApiHistory;
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
            long startTime = System.currentTimeMillis();
            String url = BomboApiUrl.getApiUrl("/hypixel/info/" + target + "?type=KUUDRA");
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(25))
                        .header("User-Agent", "bomboaddons")
                        .GET();
                BomboApiUrl.attachApiKey(builder);
                HttpRequest req = builder.build();

                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                long duration = System.currentTimeMillis() - startTime;
                ApiHistory.http("GET", url, resp.statusCode(), duration);

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
                long duration = System.currentTimeMillis() - startTime;
                ApiHistory.http("GET", url, 0, duration);
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cError fetching Kuudra info: §7" + e.getMessage()));
                    }
                });
            }
        }).start();
    }

    private static Component buildKuudraOverview(JsonObject root, String fallbackName) {
        JsonObject playerInfo = root.has("playerInfo") && root.get("playerInfo").isJsonObject() ? root.getAsJsonObject("playerInfo") : null;
        JsonObject stats = root.has("stats") && root.get("stats").isJsonObject() ? root.getAsJsonObject("stats") : null;
        JsonObject items = root.has("items") && root.get("items").isJsonObject() ? root.getAsJsonObject("items") : null;
        JsonObject extra = root.has("extra") && root.get("extra").isJsonObject() ? root.getAsJsonObject("extra") : null;
        JsonObject rend = root.has("rend") && root.get("rend").isJsonObject() ? root.getAsJsonObject("rend") : null;
        JsonObject armor = root.has("armor") && root.get("armor").isJsonObject() ? root.getAsJsonObject("armor") : null;
        JsonObject equipment = root.has("equipment") && root.get("equipment").isJsonObject() ? root.getAsJsonObject("equipment") : null;
        JsonObject pets = root.has("pets") && root.get("pets").isJsonObject() ? root.getAsJsonObject("pets") : null;
        JsonObject apiStatus = root.has("apiStatus") && root.get("apiStatus").isJsonObject() ? root.getAsJsonObject("apiStatus") : null;

        String username = playerInfo != null && playerInfo.has("username") ? playerInfo.get("username").getAsString() : fallbackName;
        double sbLevel = stats != null && stats.has("skyblock_level") ? stats.get("skyblock_level").getAsDouble() : 0.0;
        double cataLevel = stats != null && stats.has("cata_level") ? stats.get("cata_level").getAsDouble() : 0.0;
        double foragingLevel = stats != null && stats.has("foraging_level") ? stats.get("foraging_level").getAsDouble() : 0.0;
        int mp = stats != null && stats.has("magical_power") ? stats.get("magical_power").getAsInt() : 0;
        String power = stats != null && stats.has("power") && !stats.get("power").isJsonNull() ? stats.get("power").getAsString() : "None";
        String tuning = stats != null && stats.has("tuning") && !stats.get("tuning").isJsonNull() ? stats.get("tuning").getAsString() : "None";
        String enrichment = stats != null && stats.has("enrichments") && !stats.get("enrichments").isJsonNull() ? stats.get("enrichments").getAsString() : "None";
        int talismanTier = stats != null && stats.has("talisman_tier") ? stats.get("talisman_tier").getAsInt() : 0;

        int basicRuns = stats != null && stats.has("basic_runs") ? stats.get("basic_runs").getAsInt() : 0;
        int hotRuns = stats != null && stats.has("hot_runs") ? stats.get("hot_runs").getAsInt() : 0;
        int burningRuns = stats != null && stats.has("burning_runs") ? stats.get("burning_runs").getAsInt() : 0;
        int fieryRuns = stats != null && stats.has("fiery_runs") ? stats.get("fiery_runs").getAsInt() : 0;
        int infernalRuns = stats != null && stats.has("infernal_runs") ? stats.get("infernal_runs").getAsInt() : 0;
        int totalRuns = basicRuns + hotRuns + burningRuns + fieryRuns + infernalRuns;
        double soreComps = (double)basicRuns * 0.5 + (double)hotRuns + (double)burningRuns * 2.0 + (double)fieryRuns * 4.0 + (double)infernalRuns * 8.0;

        int mageRep = stats != null && stats.has("mage_rep") ? stats.get("mage_rep").getAsInt() : 0;
        int barbRep = stats != null && stats.has("barb_rep") ? stats.get("barb_rep").getAsInt() : 0;

        long purse = pets != null && pets.has("purse_balance") ? pets.get("purse_balance").getAsLong() : 0L;
        long bank = pets != null && pets.has("bank_balance") ? pets.get("bank_balance").getAsLong() : 0L;
        long goldCol = pets != null && pets.has("gold_collection") ? pets.get("gold_collection").getAsLong() : 0L;

        // Weapons
        boolean hasHyp = items != null && items.has("hyperion") && items.get("hyperion").getAsBoolean();
        String hypLore = items != null && items.has("hyperion_lore") ? items.get("hyperion_lore").getAsString() : "";
        boolean hasDuplexTerm = items != null && items.has("duplex_term") && items.get("duplex_term").getAsBoolean();
        String duplexTermLore = items != null && items.has("duplex_term_lore") ? items.get("duplex_term_lore").getAsString() : "";
        int duplexLvl = items != null && items.has("duplex_term_duplex_lvl") ? items.get("duplex_term_duplex_lvl").getAsInt() : 0;
        boolean duplexP7 = items != null && items.has("duplex_term_power7") && items.get("duplex_term_power7").getAsBoolean();
        boolean duplexC6 = items != null && items.has("duplex_term_cubism6") && items.get("duplex_term_cubism6").getAsBoolean();
        boolean duplexSmold5 = items != null && items.has("duplex_term_smold5") && items.get("duplex_term_smold5").getAsBoolean();

        boolean hasExtraTerm = extra != null && extra.has("term") && extra.get("term").getAsBoolean();
        String extraTermLore = extra != null && extra.has("term_lore") ? extra.get("term_lore").getAsString() : "";
        boolean extraP7 = extra != null && extra.has("term_power7") && extra.get("term_power7").getAsBoolean();
        boolean extraC6 = extra != null && extra.has("term_cubism6") && extra.get("term_cubism6").getAsBoolean();
        boolean extraSmold5 = extra != null && extra.has("term_smold5") && extra.get("term_smold5").getAsBoolean();

        boolean hasRag = items != null && items.has("rag") && items.get("rag").getAsBoolean();
        String ragLore = items != null && items.has("rag_lore") ? items.get("rag_lore").getAsString() : "";
        int ragChim = items != null && items.has("rag_chimera") ? items.get("rag_chimera").getAsInt() : 0;
        String ragGem = items != null && items.has("rag_gemstone") ? items.get("rag_gemstone").getAsString() : "";

        // Rend
        boolean rendTerm = rend != null && rend.has("term") && rend.get("term").getAsBoolean();
        String rendTermName = rend != null && rend.has("term_name") ? rend.get("term_name").getAsString() : "";
        int rendTermLvl = rend != null && rend.has("term_rend_lvl") ? rend.get("term_rend_lvl").getAsInt() : 0;
        boolean rendTermP7 = rend != null && rend.has("term_power7") && rend.get("term_power7").getAsBoolean();
        boolean rendTermC6 = rend != null && rend.has("term_cubism6") && rend.get("term_cubism6").getAsBoolean();
        boolean rendTermSmold5 = rend != null && rend.has("term_smold5") && rend.get("term_smold5").getAsBoolean();

        boolean rendBone = rend != null && rend.has("bone") && rend.get("bone").getAsBoolean();
        String rendBoneName = rend != null && rend.has("bone_name") ? rend.get("bone_name").getAsString() : "";
        int rendBoneLvl = rend != null && rend.has("bone_rend_lvl") ? rend.get("bone_rend_lvl").getAsInt() : 0;
        boolean rendBoneP7 = rend != null && rend.has("bone_power7") && rend.get("bone_power7").getAsBoolean();
        boolean rendBoneC6 = rend != null && rend.has("bone_cubism6") && rend.get("bone_cubism6").getAsBoolean();
        boolean rendBoneSmold5 = rend != null && rend.has("bone_smold5") && rend.get("bone_smold5").getAsBoolean();

        boolean rendBlade = rend != null && rend.has("blade") && rend.get("blade").getAsBoolean();
        String rendBladeName = rend != null && rend.has("blade_name") ? rend.get("blade_name").getAsString() : "";
        boolean rendBladeGk7 = rend != null && rend.has("blade_gk7") && rend.get("blade_gk7").getAsBoolean();
        boolean rendBladePros6 = rend != null && rend.has("blade_pros6") && rend.get("blade_pros6").getAsBoolean();
        boolean rendTux = rend != null && rend.has("tux") && rend.get("tux").getAsBoolean();
        int rendTuxHab = rend != null && rend.has("tux_hab_lvl") ? rend.get("tux_hab_lvl").getAsInt() : 0;

        // Extra
        boolean reaper = extra != null && extra.has("reaper") && extra.get("reaper").getAsBoolean();
        boolean hollow = extra != null && extra.has("hollow") && extra.get("hollow").getAsBoolean();
        boolean deployable = extra != null && extra.has("deployable") && extra.get("deployable").getAsBoolean();
        String deployableName = extra != null && extra.has("deployable_name") ? extra.get("deployable_name").getAsString() : "";
        boolean fireVeil = extra != null && extra.has("fire_veil") && extra.get("fire_veil").getAsBoolean();
        String fireVeilName = extra != null && extra.has("fire_veil_name") ? extra.get("fire_veil_name").getAsString() : "";
        boolean radio = extra != null && extra.has("radio") && extra.get("radio").getAsBoolean();
        String radioName = extra != null && extra.has("radio_name") ? extra.get("radio_name").getAsString() : "";

        // Armor
        int totalLegion = 0;
        int totalStrongMana = 0;
        int totalFeroMana = 0;
        if (armor != null) {
            totalLegion = getInt(armor, "helmet_legion") + getInt(armor, "chestplate_legion") + getInt(armor, "leggings_legion") + getInt(armor, "boots_legion");
            totalStrongMana = getInt(armor, "helmet_strong_mana") + getInt(armor, "chestplate_strong_mana") + getInt(armor, "leggings_strong_mana") + getInt(armor, "boots_strong_mana");
            totalFeroMana = getInt(armor, "helmet_fero_mana") + getInt(armor, "chestplate_fero_mana") + getInt(armor, "leggings_fero_mana") + getInt(armor, "boots_fero_mana");
        }

        // Pets
        List<String> petsList = new ArrayList<>();
        petsList.add("§7* §aBank: §f" + formatCoins(bank));
        petsList.add("§7* §aPurse: §f" + formatCoins(purse));
        petsList.add("§7* §aGold: §f" + formatCoins(goldCol));
        boolean hasGdrag = false;
        String gdragSummary = "§c* No golden dragon";
        if (pets != null && pets.has("pets") && pets.get("pets").isJsonArray()) {
            for (JsonElement el : pets.getAsJsonArray("pets")) {
                if (!el.isJsonObject()) continue;
                JsonObject pet = el.getAsJsonObject();
                int lvl = pet.has("level") ? pet.get("level").getAsInt() : 100;
                String petName = pet.has("name") ? pet.get("name").getAsString() : "Pet";
                String petItem = pet.has("pet_item") && !pet.get("pet_item").isJsonNull() ? pet.get("pet_item").getAsString() : "No pet item";
                petsList.add(String.format(Locale.ROOT, "\n§7[Lvl %d] §6%s", lvl, petName));
                petsList.add("§7* §aItem: §f" + petItem);
                if (petName.toLowerCase(Locale.ROOT).contains("golden dragon") && !hasGdrag) {
                    hasGdrag = true;
                    String bankColor = bank >= 1_000_000_000L ? "§a" : "§c";
                    gdragSummary = String.format(Locale.ROOT, "%s* §7[Lvl %d] §6Golden Dragon §7(§f%s§7)", bankColor, lvl, petItem);
                }
            }
        }

        // Hover tooltips
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
        extraList.add("§7* §aReaper: " + (reaper ? "§a✔" : "§4X"));
        extraList.add("§7* §aHollow: " + (hollow ? "§a✔" : "§4X"));
        extraList.add("§7* §aDeployable: " + (deployable ? deployableName : "§4X"));
        extraList.add("§7* §aFire Veil: " + (fireVeil ? fireVeilName : "§4X"));
        extraList.add("§7* §aRadio: " + (radio ? radioName : "§4X"));
        extraList.add("§7* §aLegion: §b" + totalLegion);
        extraList.add("§7* §aReputation: §b" + Math.max(mageRep, barbRep));
        extraList.add(String.format(Locale.ROOT, "§7* §aForaging Level: §e%.2f", foragingLevel));
        extraList.add(String.format(Locale.ROOT, "§7* §aMana Enchants: §bStrong: %d, Fero: %d", totalStrongMana, totalFeroMana));
        String extraLore = "§a§lExtra Info:\n\n" + String.join("\n", extraList);

        List<String> rendList = new ArrayList<>();
        if (rendTerm) {
            rendList.add(String.format(Locale.ROOT, "§7* §aTerm: %s §7(Rend %d) §7(P7: %s, C6: %s, Smold: %s)", rendTermName, rendTermLvl, rendTermP7 ? "§a✔" : "§cX", rendTermC6 ? "§a✔" : "§cX", rendTermSmold5 ? "§a✔" : "§cX"));
        } else {
            rendList.add("§7* §aTerm: §4X");
        }
        if (rendBone) {
            rendList.add(String.format(Locale.ROOT, "§7* §aBone: %s §7(Rend %d) §7(P7: %s, C6: %s)", rendBoneName, rendBoneLvl, rendBoneP7 ? "§a✔" : "§cX", rendBoneC6 ? "§a✔" : "§cX"));
        } else {
            rendList.add("§7* §aBone: §4X");
        }
        if (rendBlade) {
            rendList.add(String.format(Locale.ROOT, "§7* §aBlade: %s §7(GK7: %s, Pros6: %s)", rendBladeName, rendBladeGk7 ? "§a✔" : "§cX", rendBladePros6 ? "§a✔" : "§cX"));
        } else {
            rendList.add("§7* §aBlade: §4X");
        }
        if (rendTux) {
            rendList.add(String.format(Locale.ROOT, "§7* §aTuxedo: §a✔ §7(Hab %d)", rendTuxHab));
        } else {
            rendList.add("§7* §aTuxedo: §4X");
        }
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

        // Hyperion
        MutableComponent hypComp = Component.literal("§aHyperion: " + (hasHyp ? "§a✔" : "§4X") + "\n");
        final String finalHypLore = hypLore;
        if (hasHyp && !finalHypLore.isEmpty()) hypComp.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(finalHypLore))));
        main.append(hypComp);

        // Terminator
        String termStatus = hasDuplexTerm ? ("§a✔ §7(Duplex " + duplexLvl + ") §7(P7: " + (duplexP7 ? "✔" : "X") + ")") : (hasExtraTerm ? "§a✔" : "§4X");
        String finalTermLore = hasDuplexTerm ? duplexTermLore : extraTermLore;
        MutableComponent termComp = Component.literal("§aTerminator: " + termStatus + "\n");
        if ((hasDuplexTerm || hasExtraTerm) && !finalTermLore.isEmpty()) termComp.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(finalTermLore))));
        main.append(termComp);

        // Ragnarock
        String ragStatus = hasRag ? ("§a✔ §7(Chim " + ragChim + ") §7(" + ragGem + ")") : "§4X";
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

        main.append(Component.literal(gdragSummary + "\n"));
        main.append(Component.literal("§2§m-----------------------------"));

        return main;
    }

    private static int getInt(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : 0;
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
