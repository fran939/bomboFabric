package me.bombo.bomboaddons.features.critters;

import java.util.*;

public final class SafariSession {

    private final String selfName;
    private final long startedAtMillis;

    private final Map<Critter, Integer> ownCatches = new LinkedHashMap<>();
    private final Map<Critter, Integer> attempts = new LinkedHashMap<>();
    private final Map<Critter, Integer> failures = new LinkedHashMap<>();
    private final Map<Critter, Map<String, Integer>> sharedCatches = new LinkedHashMap<>();
    private final Set<Critter> sparklings = new LinkedHashSet<>();
    private final Map<Critter, Integer> shards = new LinkedHashMap<>();

    private int ownShards;
    private int sharedShards;
    private long lastEventMillis;

    public SafariSession(String selfName, long startedAtMillis) {
        this.selfName = selfName == null ? "You" : selfName;
        this.startedAtMillis = startedAtMillis;
        this.lastEventMillis = startedAtMillis;
    }

    public void record(CritterEvent event, long atMillis) {
        lastEventMillis = atMillis;
        Critter critter = event.critter();
        if (critter == null) return;

        switch (event.type()) {
            case OWN_CATCH -> {
                ownCatches.merge(critter, 1, Integer::sum);
                ownShards += event.shards();
                shards.merge(critter, event.shards(), Integer::sum);
            }
            case SHARED_CATCH -> {
                sharedCatches.computeIfAbsent(critter, c -> new TreeMap<>())
                    .merge(event.catcher(), 1, Integer::sum);
                sharedShards += event.shards();
                shards.merge(critter, event.shards(), Integer::sum);
            }
            case ATTEMPT -> attempts.merge(critter, 1, Integer::sum);
            case FAILED -> failures.merge(critter, 1, Integer::sum);
            case ENTERED_SAFARI -> {}
        }

        if (event.sparkling()) sparklings.add(critter);
    }

    public boolean caughtByYou(Critter critter) {
        return ownCatches.getOrDefault(critter, 0) > 0;
    }

    public int ownUnique() {
        return ownCatches.size();
    }

    public int ownUnique(SafariBiome biome) {
        return (int) ownCatches.keySet().stream().filter(c -> c.biome() == biome).count();
    }

    public int ownTotal() {
        return ownCatches.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int ownTotal(SafariBiome biome) {
        return ownCatches.entrySet().stream()
            .filter(e -> e.getKey().biome() == biome)
            .mapToInt(Map.Entry::getValue).sum();
    }

    public boolean caughtByParty(Critter critter) {
        return caughtByYou(critter) || sharedCatches.containsKey(critter);
    }

    public int required(Critter critter, boolean uniqueOnly) {
        if (uniqueOnly) return 1;
        return critter.hasQuota() ? critter.spawnQuota() : 1;
    }

    public boolean isComplete(Critter critter, boolean uniqueOnly) {
        return partyCatches(critter) >= required(critter, uniqueOnly);
    }

    public int remaining(Critter critter, boolean uniqueOnly) {
        return Math.max(0, required(critter, uniqueOnly) - partyCatches(critter));
    }

    public int partyUnique() {
        return (int) Critters.all().stream().filter(c -> isComplete(c, true)).count();
    }

    public int partyUnique(SafariBiome biome) {
        return (int) Critters.inBiome(biome).stream().filter(c -> isComplete(c, true)).count();
    }

    public int partyCompletedQuota(boolean uniqueOnly) {
        return (int) Critters.all().stream().filter(c -> isComplete(c, uniqueOnly)).count();
    }

    public int partyCompletedQuota(SafariBiome biome, boolean uniqueOnly) {
        return (int) Critters.inBiome(biome).stream().filter(c -> isComplete(c, uniqueOnly)).count();
    }

    public int partyTotal() {
        return ownTotal() + sharedTotal();
    }

    public int partyTotal(SafariBiome biome) {
        return ownTotal(biome) + sharedCatches.entrySet().stream()
            .filter(e -> e.getKey().biome() == biome)
            .flatMap(e -> e.getValue().values().stream())
            .mapToInt(Integer::intValue).sum();
    }

    private int sharedTotal() {
        return sharedCatches.values().stream()
            .flatMap(m -> m.values().stream())
            .mapToInt(Integer::intValue).sum();
    }

    public int partyCatches(Critter critter) {
        int shared = sharedCatches.getOrDefault(critter, Map.of()).values().stream()
            .mapToInt(Integer::intValue).sum();
        return ownCatches.getOrDefault(critter, 0) + shared;
    }

    public List<String> catchersOf(Critter critter) {
        List<String> names = new ArrayList<>();
        if (caughtByYou(critter)) names.add(selfName);
        names.addAll(sharedCatches.getOrDefault(critter, Map.of()).keySet());
        return names;
    }

    public boolean biomeComplete(SafariBiome biome, boolean uniqueOnly) {
        return partyCompletedQuota(biome, uniqueOnly) == Critters.totalIn(biome);
    }

    public boolean dexComplete(boolean uniqueOnly) {
        return partyCompletedQuota(uniqueOnly) == Critters.total();
    }

    public List<Critter> missing(SafariBiome biome, boolean uniqueOnly) {
        return Critters.inBiome(biome).stream().filter(c -> !isComplete(c, uniqueOnly)).toList();
    }

    public Map<String, Map<SafariBiome, Integer>> uniquePerPlayer() {
        Map<String, Map<SafariBiome, Integer>> result = new LinkedHashMap<>();

        Map<SafariBiome, Integer> mine = new EnumMap<>(SafariBiome.class);
        for (Critter critter : ownCatches.keySet()) {
            mine.merge(critter.biome(), 1, Integer::sum);
        }
        if (!mine.isEmpty()) result.put(selfName, mine);

        Map<String, Map<SafariBiome, Integer>> others = new TreeMap<>();
        for (Map.Entry<Critter, Map<String, Integer>> entry : sharedCatches.entrySet()) {
            SafariBiome biome = entry.getKey().biome();
            for (String player : entry.getValue().keySet()) {
                others.computeIfAbsent(player, p -> new EnumMap<>(SafariBiome.class))
                    .merge(biome, 1, Integer::sum);
            }
        }
        result.putAll(others);
        return result;
    }

    public Map<String, Integer> totalPerPlayer() {
        Map<String, Integer> result = new LinkedHashMap<>();
        int mineTotal = ownTotal();
        if (mineTotal > 0) result.put(selfName, mineTotal);

        Map<String, Integer> others = new TreeMap<>();
        for (Map.Entry<Critter, Map<String, Integer>> entry : sharedCatches.entrySet()) {
            for (Map.Entry<String, Integer> pEntry : entry.getValue().entrySet()) {
                others.merge(pEntry.getKey(), pEntry.getValue(), Integer::sum);
            }
        }
        result.putAll(others);
        return result;
    }

    public int totalShards() {
        return ownShards + sharedShards;
    }

    public String selfName() {
        return selfName;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public long elapsedMillis(long now) {
        return Math.max(0, now - startedAtMillis);
    }

    public boolean isEmpty() {
        return ownCatches.isEmpty() && sharedCatches.isEmpty() && attempts.isEmpty();
    }
}
