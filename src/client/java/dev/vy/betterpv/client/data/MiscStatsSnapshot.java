package dev.vy.betterpv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Home → Misc: lifetime player_stats, profile extras, community upgrades. */
public final class MiscStatsSnapshot {
	public record CountEntry(String id, String label, long count) {
	}

	public record CommunityUpgrade(String upgrade, String label, int tier, long startedMs) {
	}

	public record Section(String id, String title, List<CountEntry> entries) {
		public Section {
			entries = entries == null ? List.of() : List.copyOf(entries);
		}
	}

	/** Experimentation Table progress from {@code members.*.experimentation}. */
	public record ExperimentGame(String id, String label, long claims, long attempts, long bestScore) {
		public ExperimentGame {
			id = id == null ? "" : id;
			label = label == null || label.isBlank() ? id : label;
			claims = Math.max(0L, claims);
			attempts = Math.max(0L, attempts);
			bestScore = Math.max(0L, bestScore);
		}
	}

	public record ExperimentationStats(List<ExperimentGame> games, long serumsDrank, long claimsResets) {
		public ExperimentationStats {
			games = List.copyOf(games == null ? List.of() : games);
			serumsDrank = Math.max(0L, serumsDrank);
			claimsResets = Math.max(0L, claimsResets);
		}

		public static ExperimentationStats empty() {
			return new ExperimentationStats(List.of(), 0L, 0L);
		}

		public boolean present() {
			if (serumsDrank > 0L || claimsResets > 0L) {
				return true;
			}
			for (ExperimentGame g : games) {
				if (g.claims() > 0L || g.attempts() > 0L || g.bestScore() > 0L) {
					return true;
				}
			}
			return false;
		}
	}

	private final List<CountEntry> kills;
	private final long killsTotal;
	private final List<CountEntry> deaths;
	private final long deathsTotal;
	private final double highestDamage;
	private final double highestCriticalDamage;
	private final long giftsGiven;
	private final long giftsReceived;
	private final long petOresMined;
	private final long petSeaCreatures;
	private final long petXpTotal;
	private final long seaCreatureKills;
	private final long firstJoinMs;
	private final int fairyCollected;
	private final int fairyExchanges;
	private final int fairyUnspent;
	private final int personalBankUpgrade;
	private final boolean cookieBuffActive;
	private final long soulflow;
	private final int refinedJyrreUses;
	private final List<String> unlockedTemples;
	private final List<CommunityUpgrade> communityUpgrades;
	private final List<Section> extraSections;
	private final ExperimentationStats experimentation;

	private MiscStatsSnapshot(
		List<CountEntry> kills,
		long killsTotal,
		List<CountEntry> deaths,
		long deathsTotal,
		double highestDamage,
		double highestCriticalDamage,
		long giftsGiven,
		long giftsReceived,
		long petOresMined,
		long petSeaCreatures,
		long petXpTotal,
		long seaCreatureKills,
		long firstJoinMs,
		int fairyCollected,
		int fairyExchanges,
		int fairyUnspent,
		int personalBankUpgrade,
		boolean cookieBuffActive,
		long soulflow,
		int refinedJyrreUses,
		List<String> unlockedTemples,
		List<CommunityUpgrade> communityUpgrades,
		List<Section> extraSections,
		ExperimentationStats experimentation
	) {
		this.kills = List.copyOf(kills == null ? List.of() : kills);
		this.killsTotal = Math.max(0L, killsTotal);
		this.deaths = List.copyOf(deaths == null ? List.of() : deaths);
		this.deathsTotal = Math.max(0L, deathsTotal);
		this.highestDamage = Math.max(0D, highestDamage);
		this.highestCriticalDamage = Math.max(0D, highestCriticalDamage);
		this.giftsGiven = Math.max(0L, giftsGiven);
		this.giftsReceived = Math.max(0L, giftsReceived);
		this.petOresMined = Math.max(0L, petOresMined);
		this.petSeaCreatures = Math.max(0L, petSeaCreatures);
		this.petXpTotal = Math.max(0L, petXpTotal);
		this.seaCreatureKills = Math.max(0L, seaCreatureKills);
		this.firstJoinMs = Math.max(0L, firstJoinMs);
		this.fairyCollected = Math.max(0, fairyCollected);
		this.fairyExchanges = Math.max(0, fairyExchanges);
		this.fairyUnspent = Math.max(0, fairyUnspent);
		this.personalBankUpgrade = Math.max(0, personalBankUpgrade);
		this.cookieBuffActive = cookieBuffActive;
		this.soulflow = Math.max(0L, soulflow);
		this.refinedJyrreUses = Math.max(0, refinedJyrreUses);
		this.unlockedTemples = List.copyOf(unlockedTemples == null ? List.of() : unlockedTemples);
		this.communityUpgrades = List.copyOf(communityUpgrades == null ? List.of() : communityUpgrades);
		this.extraSections = List.copyOf(extraSections == null ? List.of() : extraSections);
		this.experimentation = experimentation == null ? ExperimentationStats.empty() : experimentation;
	}

	public static MiscStatsSnapshot empty() {
		return new MiscStatsSnapshot(
			List.of(), 0L, List.of(), 0L,
			0D, 0D, 0L, 0L, 0L, 0L, 0L, 0L,
			0L, 0, 0, 0, 0, false, 0L, 0, List.of(),
			List.of(), List.of(), ExperimentationStats.empty()
		);
	}

	public static MiscStatsSnapshot from(JsonObject profileRoot, JsonObject member) {
		if (member == null) {
			return empty();
		}
		JsonObject stats = Leveling.obj(member.get("player_stats"));
		JsonObject killsObj = Leveling.obj(stats == null ? null : stats.get("kills"));
		JsonObject deathsObj = Leveling.obj(stats == null ? null : stats.get("deaths"));
		List<CountEntry> kills = countMap(killsObj, true);
		List<CountEntry> deaths = countMap(deathsObj, true);
		long killsTotal = longOf(killsObj, "total");
		if (killsTotal <= 0L) {
			killsTotal = sum(kills);
		}
		long deathsTotal = longOf(deathsObj, "total");
		if (deathsTotal <= 0L) {
			deathsTotal = sum(deaths);
		}

		JsonObject gifts = Leveling.obj(stats == null ? null : stats.get("gifts"));
		JsonObject pets = Leveling.obj(stats == null ? null : stats.get("pets"));
		JsonObject milestones = Leveling.obj(pets == null ? null : pets.get("milestone"));

		JsonObject profile = Leveling.obj(member.get("profile"));
		JsonObject fairy = Leveling.obj(member.get("fairy_soul"));
		JsonObject itemData = Leveling.obj(member.get("item_data"));
		JsonObject winter = Leveling.obj(member.get("winter_player_data"));
		JsonObject temples = Leveling.obj(member.get("temples"));

		List<CommunityUpgrade> upgrades = parseCommunity(profileRoot);
		List<Section> extras = new ArrayList<>();
		addSection(extras, "mythos", "Mythological", Leveling.obj(stats == null ? null : stats.get("mythos")));
		addSection(extras, "end_island", "End Island", Leveling.obj(stats == null ? null : stats.get("end_island")));
		addSection(extras, "races", "Races", Leveling.obj(stats == null ? null : stats.get("races")));
		addSection(extras, "winter", "Winter", Leveling.obj(stats == null ? null : stats.get("winter")));
		addSection(extras, "spooky", "Spooky Festival", Leveling.obj(stats == null ? null : stats.get("spooky")));
		addSection(extras, "candy", "Candy", Leveling.obj(stats == null ? null : stats.get("candy_collected")));
		addSection(extras, "rift_combat", "Rift Combat", Leveling.obj(stats == null ? null : stats.get("rift")));

		return new MiscStatsSnapshot(
			kills,
			killsTotal,
			deaths,
			deathsTotal,
			doubleOf(stats, "highest_damage"),
			doubleOf(stats, "highest_critical_damage"),
			longOf(gifts, "total_given"),
			longOf(gifts, "total_received"),
			longOf(milestones, "ores_mined"),
			longOf(milestones, "sea_creatures_killed"),
			longOf(pets, "total_exp_gained"),
			longOf(stats, "sea_creature_kills"),
			longOf(profile, "first_join"),
			(int) longOf(fairy, "total_collected"),
			(int) longOf(fairy, "fairy_exchanges"),
			(int) longOf(fairy, "unspent_souls"),
			(int) longOf(profile, "personal_bank_upgrade"),
			boolOf(profile, "cookie_buff_active"),
			longOf(itemData, "soulflow"),
			(int) longOf(winter, "refined_jyrre_uses"),
			stringList(temples == null ? null : temples.get("unlocked_temples")),
			upgrades,
			extras,
			parseExperimentation(Leveling.obj(member.get("experimentation")))
		);
	}

	private static ExperimentationStats parseExperimentation(JsonObject root) {
		if (root == null || root.entrySet().isEmpty()) {
			return ExperimentationStats.empty();
		}
		List<ExperimentGame> games = new ArrayList<>(3);
		addExperimentGame(games, root, "pairings", "Pairings");
		addExperimentGame(games, root, "simon", "Simon");
		addExperimentGame(games, root, "numbers", "Numbers");
		return new ExperimentationStats(
			games,
			longOf(root, "serums_drank"),
			longOf(root, "claims_resets")
		);
	}

	private static void addExperimentGame(List<ExperimentGame> out, JsonObject root, String key, String label) {
		JsonObject game = Leveling.obj(root.get(key));
		if (game == null || game.entrySet().isEmpty()) {
			return;
		}
		long claims = 0L;
		long attempts = 0L;
		long best = 0L;
		for (Map.Entry<String, JsonElement> e : game.entrySet()) {
			String id = e.getKey();
			if (id == null || !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) {
				continue;
			}
			long n = Math.max(0L, (long) e.getValue().getAsDouble());
			if (id.startsWith("claims_")) {
				claims += n;
			} else if (id.startsWith("attempts_")) {
				attempts += n;
			} else if (id.startsWith("best_score_")) {
				best = Math.max(best, n);
			}
		}
		if (claims <= 0L && attempts <= 0L && best <= 0L) {
			return;
		}
		out.add(new ExperimentGame(key, label, claims, attempts, best));
	}

	private static void addSection(List<Section> out, String id, String title, JsonObject obj) {
		if (obj == null || obj.entrySet().isEmpty()) {
			return;
		}
		List<CountEntry> entries = flattenCounts(obj, "");
		if (entries.isEmpty()) {
			return;
		}
		entries.sort(Comparator
			.comparingLong(CountEntry::count).reversed()
			.thenComparing(e -> e.label().toLowerCase(Locale.ROOT)));
		out.add(new Section(id, title, entries));
	}

	private static List<CountEntry> countMap(JsonObject obj, boolean skipTotal) {
		Map<String, CountEntry> merged = new LinkedHashMap<>();
		if (obj == null) {
			return List.of();
		}
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			String key = e.getKey();
			if (key == null || (skipTotal && "total".equalsIgnoreCase(key))) {
				continue;
			}
			if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) {
				continue;
			}
			long n = Math.max(0L, (long) e.getValue().getAsDouble());
			if (n <= 0L) {
				continue;
			}
			String label = InventoryDecoder.prettyWords(key);
			CountEntry existing = merged.get(label);
			if (existing == null) {
				merged.put(label, new CountEntry(key, label, n));
			} else {
				merged.put(label, new CountEntry(existing.id() + "+" + key, label, existing.count() + n));
			}
		}
		List<CountEntry> out = new ArrayList<>(merged.values());
		out.sort(Comparator
			.comparingLong(CountEntry::count).reversed()
			.thenComparing(e -> e.label().toLowerCase(Locale.ROOT)));
		return out;
	}

	private static List<CountEntry> flattenCounts(JsonObject obj, String prefix) {
		List<CountEntry> out = new ArrayList<>();
		if (obj == null) {
			return out;
		}
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			String key = e.getKey();
			if (key == null) {
				continue;
			}
			String path = prefix.isEmpty() ? key : prefix + "_" + key;
			JsonElement val = e.getValue();
			if (val.isJsonPrimitive() && val.getAsJsonPrimitive().isNumber()) {
				long n = Math.max(0L, (long) val.getAsDouble());
				if (n > 0L) {
					out.add(new CountEntry(path, InventoryDecoder.prettyWords(path), n));
				}
			} else if (val.isJsonObject()) {
				out.addAll(flattenCounts(val.getAsJsonObject(), path));
			}
		}
		return out;
	}

	private static List<CommunityUpgrade> parseCommunity(JsonObject profileRoot) {
		JsonObject root = Leveling.obj(profileRoot == null ? null : profileRoot.get("community_upgrades"));
		JsonArray states = root == null || !root.has("upgrade_states") || !root.get("upgrade_states").isJsonArray()
			? null
			: root.getAsJsonArray("upgrade_states");
		if (states == null) {
			return List.of();
		}
		Map<String, CommunityUpgrade> byId = new LinkedHashMap<>();
		for (JsonElement el : states) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject u = el.getAsJsonObject();
			String upgrade = str(u, "upgrade");
			if (upgrade.isBlank()) {
				continue;
			}
			CommunityUpgrade next = new CommunityUpgrade(
				upgrade,
				InventoryDecoder.prettyWords(upgrade),
				(int) longOf(u, "tier"),
				longOf(u, "started_ms")
			);
			String key = upgrade.toLowerCase(Locale.ROOT);
			CommunityUpgrade prev = byId.get(key);
			if (prev == null || next.tier() >= prev.tier()) {
				byId.put(key, next);
			}
		}
		List<CommunityUpgrade> out = new ArrayList<>(byId.values());
		out.sort(Comparator
			.comparing((CommunityUpgrade c) -> c.upgrade().toLowerCase(Locale.ROOT))
			.thenComparingInt(CommunityUpgrade::tier));
		return out;
	}

	private static long sum(List<CountEntry> entries) {
		long total = 0L;
		for (CountEntry e : entries) {
			total += e.count();
		}
		return total;
	}

	private static long longOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return 0L;
		}
		try {
			return Math.max(0L, (long) obj.get(key).getAsDouble());
		} catch (Exception ignored) {
			return 0L;
		}
	}

	private static double doubleOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return 0D;
		}
		try {
			return Math.max(0D, obj.get(key).getAsDouble());
		} catch (Exception ignored) {
			return 0D;
		}
	}

	private static boolean boolOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return false;
		}
		try {
			return obj.get(key).getAsBoolean();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			String s = obj.get(key).getAsString();
			return s == null ? "" : s;
		} catch (Exception ignored) {
			return "";
		}
	}

	private static List<String> stringList(JsonElement el) {
		if (el == null || !el.isJsonArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement item : el.getAsJsonArray()) {
			if (item == null || !item.isJsonPrimitive()) {
				continue;
			}
			try {
				String s = item.getAsString();
				if (s != null && !s.isBlank()) {
					out.add(InventoryDecoder.prettyWords(s));
				}
			} catch (Exception ignored) {
			}
		}
		return out;
	}

	public List<CountEntry> kills() { return kills; }
	public long killsTotal() { return killsTotal; }
	public List<CountEntry> deaths() { return deaths; }
	public long deathsTotal() { return deathsTotal; }
	public double highestDamage() { return highestDamage; }
	public double highestCriticalDamage() { return highestCriticalDamage; }
	public long giftsGiven() { return giftsGiven; }
	public long giftsReceived() { return giftsReceived; }
	public long petOresMined() { return petOresMined; }
	public long petSeaCreatures() { return petSeaCreatures; }
	public long petXpTotal() { return petXpTotal; }
	public long seaCreatureKills() { return seaCreatureKills; }
	public long firstJoinMs() { return firstJoinMs; }
	public int fairyCollected() { return fairyCollected; }
	public int fairyExchanges() { return fairyExchanges; }
	public int fairyUnspent() { return fairyUnspent; }
	public int personalBankUpgrade() { return personalBankUpgrade; }
	public boolean cookieBuffActive() { return cookieBuffActive; }
	public long soulflow() { return soulflow; }
	public int refinedJyrreUses() { return refinedJyrreUses; }
	public List<String> unlockedTemples() { return unlockedTemples; }
	public List<CommunityUpgrade> communityUpgrades() { return communityUpgrades; }
	public List<Section> extraSections() { return extraSections; }
	public ExperimentationStats experimentation() { return experimentation; }
}
