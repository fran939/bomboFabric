package dev.vy.betterpv.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.AuctionSnapshot;
import dev.vy.betterpv.client.data.BestiarySnapshot;
import dev.vy.betterpv.client.data.ColeWeight;
import dev.vy.betterpv.client.data.CollectionSnapshot;
import dev.vy.betterpv.client.data.CrimsonSnapshot;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.EventsSnapshot;
import dev.vy.betterpv.client.data.FishingSnapshot;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.GardenData;
import dev.vy.betterpv.client.data.GardenSnapshot;
import dev.vy.betterpv.client.data.MiningHotmData;
import dev.vy.betterpv.client.data.MiningSnapshot;
import dev.vy.betterpv.client.data.MiscStatsSnapshot;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.data.Leveling;
import dev.vy.betterpv.client.data.PetSnapshot;
import dev.vy.betterpv.client.data.PlayerStatsCalculator;
import dev.vy.betterpv.client.data.PlayerStatsSnapshot;
import dev.vy.betterpv.client.data.ProfileSnapshot;
import dev.vy.betterpv.client.data.RepoData;
import dev.vy.betterpv.client.data.RiftSnapshot;
import dev.vy.betterpv.client.dungeons.CataXpMath;
import dev.vy.betterpv.client.dungeons.DungeonModifierScanner;
import dev.vy.betterpv.client.dungeons.DungeonXpData;
import dev.vy.betterpv.client.dungeons.EssenceShopData;
import dev.vy.betterpv.client.gui.ArmorStacks;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import dev.vy.betterpv.client.networth.NetworthBreakdown;
import dev.vy.betterpv.client.networth.NetworthCalculator;
import dev.vy.betterpv.client.networth.NetworthMode;
import dev.vy.betterpv.client.price.ItemPricer;
import dev.vy.betterpv.client.weight.WeightBreakdown;
import dev.vy.betterpv.client.weight.WeightCalculator;
import dev.vy.betterpv.BetterPV;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

public final class ProfileFetcher {
	/** Match worker profiles cache TTL (5 minutes). */
	private static final long CACHE_TTL_MS = 5L * 60L * 1000L;
	private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
	/** Post-core enrichment / profile-switch parsing (bounded; avoids parse-pool deadlock). */
	private static final ExecutorService ENRICH_EXECUTOR = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "BetterPV-Enrich");
		t.setDaemon(true);
		return t;
	});
	private static final Object ACTIVE_ENRICH_LOCK = new Object();
	private static volatile ProfileEnrichmentSession activeEnrichment;

	private static final String[] HOME_SKILLS = {
		"combat", "foraging", "farming", "enchanting", "mining", "alchemy", "fishing", "carpentry", "taming", "hunting"
	};
	private static final String[][] HOME_SLAYERS = {
		{"zombie", "Revenant"},
		{"enderman", "Enderman"},
		{"spider", "Tarantula"},
		{"blaze", "Blaze"},
		{"wolf", "Sven"},
		{"vampire", "Vampire"}
	};

	private static final String[] DUNGEON_CLASSES = {
		"healer", "mage", "berserk", "archer", "tank"
	};

	private static final ConcurrentHashMap<String, String> COOP_MEMBER_NAMES = new ConcurrentHashMap<>();

	private ProfileFetcher() {
	}

	public record CoopMemberRef(String uuid, String fallbackName) {
		public CoopMemberRef {
			uuid = uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
			fallbackName = fallbackName == null || fallbackName.isBlank() ? shortCoopUuid(uuid) : fallbackName;
		}
	}

	public record CoopSummary(
		int currentOthers,
		int formerCount,
		List<CoopMemberRef> currentMembers,
		List<CoopMemberRef> formerMembers
	) {
		public CoopSummary {
			currentMembers = currentMembers == null ? List.of() : List.copyOf(currentMembers);
			formerMembers = formerMembers == null ? List.of() : List.copyOf(formerMembers);
			currentOthers = Math.max(0, currentOthers);
			formerCount = Math.max(0, formerCount);
		}

		public static CoopSummary solo() {
			return new CoopSummary(0, 0, List.of(), List.of());
		}

		public boolean soloProfile() {
			return currentOthers == 0 && formerCount == 0;
		}
	}

	public record ProfileChoice(
		String cuteName,
		String profileId,
		boolean selected,
		String gameMode,
		long createdAtMs,
		CoopSummary coop
	) {
		public ProfileChoice {
			cuteName = cuteName == null || cuteName.isBlank() ? "Unknown" : cuteName;
			profileId = profileId == null ? "" : profileId;
			gameMode = gameMode == null ? "" : gameMode.trim();
			createdAtMs = Math.max(0L, createdAtMs);
			coop = coop == null ? CoopSummary.solo() : coop;
		}

		public ProfileChoice(
			String cuteName,
			String profileId,
			boolean selected,
			String gameMode,
			long createdAtMs
		) {
			this(cuteName, profileId, selected, gameMode, createdAtMs, CoopSummary.solo());
		}

		public String gameModeLabel() {
			if (gameMode.isBlank()) {
				return "";
			}
			return switch (gameMode.toLowerCase(java.util.Locale.ROOT)) {
				case "ironman" -> "Ironman";
				case "stranded" -> "Stranded";
				case "bingo" -> "Bingo";
				default -> {
					String lower = gameMode.toLowerCase(java.util.Locale.ROOT);
					yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
				}
			};
		}
	}

	public record LoadedProfile(
		ProfileSnapshot snapshot,
		DungeonSnapshot dungeons,
		InventorySnapshot inventories,
		PetSnapshot pets,
		AuctionSnapshot auctions,
		CollectionSnapshot collections,
		GardenSnapshot garden,
		MiningSnapshot mining,
		ForagingSnapshot foraging,
		FishingSnapshot fishing,
		CrimsonSnapshot crimson,
		RiftSnapshot rift,
		BestiarySnapshot bestiary,
		EventsSnapshot events,
		MiscStatsSnapshot misc,
		JsonObject museumMember,
		String profileId,
		JsonObject profilesRoot,
		List<ProfileChoice> profiles,
		WeightBreakdown senither,
		WeightBreakdown lily,
		NetworthBreakdown networthNormal,
		NetworthBreakdown networthNonCosmetic,
		NetworthBreakdown networthUnsoulbound,
		NetworthBreakdown networthUnsoulboundNonCosmetic,
		ItemStack[] armor,
		PlayerStatsSnapshot playerStats,
		String error
	) {
		public LoadedProfile {
			profiles = profiles == null ? List.of() : List.copyOf(profiles);
		}

		public boolean ok() {
			return error == null || error.isBlank();
		}

		public NetworthBreakdown networth(NetworthMode mode) {
			if (mode == null) {
				return networthNormal;
			}
			return switch (mode) {
				case NORMAL -> networthNormal;
				case NON_COSMETIC -> networthNonCosmetic;
				case UNSOULBOUND -> networthUnsoulbound;
				case UNSOULBOUND_NON_COSMETIC -> networthUnsoulboundNonCosmetic;
			};
		}
	}

	public static CompletableFuture<LoadedProfile> fetch(String playerName) {
		return fetch(playerName, null);
	}

	/**
	 * Loads the home/core profile first (enough for Home + dismiss loading egg), then publishes
	 * a fully enriched profile through {@code onUpdate}. Fatal failures return {@code !ok()} and
	 * never become a successful core load.
	 */
	public static CompletableFuture<LoadedProfile> fetch(String playerName, Consumer<LoadedProfile> onUpdate) {
		RepoData.ensureLoaded();
		DungeonXpData.ensureLoaded();
		GardenData.ensureLoaded();
		MiningHotmData.ensureLoaded();
		if (!HypixelApiClient.canFetch()) {
			return CompletableFuture.completedFuture(new LoadedProfile(
				ProfileSnapshot.loading(playerName),
				DungeonSnapshot.empty(),
				InventorySnapshot.empty(),
				PetSnapshot.empty(),
				AuctionSnapshot.empty(),
				CollectionSnapshot.empty(),
				GardenSnapshot.empty(),
				MiningSnapshot.empty(),
				ForagingSnapshot.empty(),
				FishingSnapshot.empty(),
				CrimsonSnapshot.empty(),
				RiftSnapshot.empty(),
				BestiarySnapshot.empty(),
				EventsSnapshot.empty(),
				MiscStatsSnapshot.empty(),
				null,
				null,
				null,
				List.of(),
				WeightBreakdown.empty(dev.vy.betterpv.client.weight.WeightSystem.SENITHER),
				WeightBreakdown.empty(dev.vy.betterpv.client.weight.WeightSystem.LILY),
				NetworthBreakdown.empty("API unavailable"),
				NetworthBreakdown.empty("API unavailable"),
				NetworthBreakdown.empty("API unavailable"),
				NetworthBreakdown.empty("API unavailable"),
				emptyArmor(),
				PlayerStatsSnapshot.empty(),
				"API unavailable"
			));
		}
		String cleaned = playerName == null ? "" : playerName.trim();
		LoadedProfile cached = getCached(nameKey(cleaned));
		if (cached != null) {
			BetterPV.LOGGER.info("Profile cache hit for {}", cleaned);
			return CompletableFuture.completedFuture(cached);
		}
		return HypixelApiClient.resolveUuid(cleaned).thenCompose(uuidOpt -> {
			if (uuidOpt.isEmpty()) {
				return CompletableFuture.completedFuture(fail(cleaned, "Player not found"));
			}
			HypixelApiClient.UuidName id = uuidOpt.get();
			LoadedProfile byUuid = getCached(uuidKey(id.uuid()));
			if (byUuid != null) {
				BetterPV.LOGGER.info("Profile cache hit for {} ({})", id.name(), id.uuid());
				putCache(nameKey(cleaned), byUuid);
				putCache(nameKey(id.name()), byUuid);
				return CompletableFuture.completedFuture(byUuid);
			}

			return HypixelApiClient.skyblockProfiles(id.uuid(), id.name()).thenCompose(profilesOpt -> {
					if (profilesOpt.isEmpty()) {
						return CompletableFuture.completedFuture(fail(id.name(), "Could not load SkyBlock profiles for " + id.name()));
					}
					JsonObject root = profilesOpt.get();
						JsonObject best = selectedProfile(root);
						String profileId = best != null && best.has("profile_id")
							? best.get("profile_id").getAsString()
							: null;
						CompletableFuture<Optional<JsonObject>> museumFut =
							HypixelApiClient.skyblockMuseum(id.uuid(), profileId);
						CompletableFuture<Optional<JsonObject>> electionFut =
							HypixelApiClient.skyblockElection();
						CompletableFuture<Optional<JsonObject>> auctionFut =
							HypixelApiClient.skyblockAuction(id.uuid());
						CompletableFuture<Optional<JsonArray>> soldFut =
							CoflnetApiClient.playerAuctions(id.uuid(), 0);
						CompletableFuture<Optional<JsonArray>> bidsFut =
							CoflnetApiClient.playerBids(id.uuid(), 0);

						CompletableFuture<LoadedProfile> coreFuture = CompletableFuture.supplyAsync(
							() -> parseHomeCore(id.name(), id.uuid(), root),
							HypixelApiClient.parseExecutor()
						);

						coreFuture.thenAccept(core -> {
							if (core != null && core.ok()) {
								// Cache core immediately so a quick second /pv is warm for first paint.
								putCache(uuidKey(id.uuid()), core);
								putCache(nameKey(id.name()), core);
								putCache(nameKey(cleaned), core);
								startProgressiveEnrichment(
									core,
									nameKey(cleaned),
									root,
									museumFut,
									electionFut,
									auctionFut,
									soldFut,
									bidsFut,
									onUpdate
								);
							}
						});
						return coreFuture;
					});
		});
	}

	/**
	 * Prefer loading the clicked tab next while background enrichment is still running.
	 */
	public static void prioritizeTab(dev.vy.betterpv.client.gui.nav.PvTab tab) {
		ProfileEnrichmentSession session = activeEnrichment;
		if (session != null) {
			session.prioritize(tab);
		}
	}

	static void clearActiveEnrichment(ProfileEnrichmentSession session) {
		synchronized (ACTIVE_ENRICH_LOCK) {
			if (activeEnrichment == session) {
				activeEnrichment = null;
			}
		}
	}

	static void cacheEnriched(UUID uuid, String name, String cleanedNameKey, LoadedProfile loaded) {
		if (loaded == null || !loaded.ok()) {
			return;
		}
		putCache(uuidKey(uuid), loaded);
		putCache(nameKey(name), loaded);
		if (cleanedNameKey != null && !cleanedNameKey.isBlank()) {
			CACHE.put(cleanedNameKey, new CacheEntry(loaded, System.currentTimeMillis() + CACHE_TTL_MS));
		}
	}

	static DungeonSnapshot parseDungeonsPublic(
		JsonObject member,
		JsonObject museumMember,
		JsonObject electionRoot,
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories
	) {
		return parseDungeons(member, museumMember, electionRoot, inventoryCategories);
	}

	static JsonObject findMuseumMemberPublic(JsonObject museumRoot, String profileId, String undashed) {
		return findMuseumMember(museumRoot, profileId, undashed);
	}

	static void scheduleNetworthRefreshPublic(
		LoadedProfile base,
		JsonObject member,
		JsonObject profile,
		JsonObject museumMember,
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories,
		Consumer<LoadedProfile> onUpdate
	) {
		scheduleNetworthRefresh(base, member, profile, museumMember, inventoryCategories, onUpdate);
	}

	private static void startProgressiveEnrichment(
		LoadedProfile core,
		String cleanedNameKey,
		JsonObject root,
		CompletableFuture<Optional<JsonObject>> museumFut,
		CompletableFuture<Optional<JsonObject>> electionFut,
		CompletableFuture<Optional<JsonObject>> auctionFut,
		CompletableFuture<Optional<JsonArray>> soldFut,
		CompletableFuture<Optional<JsonArray>> bidsFut,
		Consumer<LoadedProfile> onUpdate
	) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		JsonObject best = pickProfile(profiles, core.profileId());
		if (best == null || core.snapshot() == null || core.snapshot().playerUuid() == null) {
			return;
		}
		String undashed = HypixelApiClient.undashed(core.snapshot().playerUuid());
		JsonObject members = best.has("members") && best.get("members").isJsonObject()
			? best.getAsJsonObject("members")
			: null;
		JsonObject member = findMember(members, undashed);
		if (member == null) {
			return;
		}
		ProfileEnrichmentSession session = new ProfileEnrichmentSession(
			core,
			cleanedNameKey,
			root,
			best,
			members,
			member,
			museumFut,
			electionFut,
			auctionFut,
			soldFut,
			bidsFut,
			onUpdate,
			ENRICH_EXECUTOR
		);
		synchronized (ACTIVE_ENRICH_LOCK) {
			if (activeEnrichment != null) {
				activeEnrichment.cancel();
			}
			activeEnrichment = session;
		}
		session.start();
	}

	/**
	 * Async re-parse for profile switching. Publishes core first when {@code onUpdate} is set,
	 * then enrichment. Caller must ignore stale results via its own generation counter.
	 */
	public static CompletableFuture<LoadedProfile> switchToProfile(
		String name,
		UUID uuid,
		JsonObject root,
		String profileId,
		Consumer<LoadedProfile> onUpdate
	) {
		CompletableFuture<LoadedProfile> coreFuture = CompletableFuture.supplyAsync(
			() -> parseHomeCore(name, uuid, root, profileId),
			HypixelApiClient.parseExecutor()
		);
		coreFuture.thenAccept(core -> {
			if (core == null || !core.ok()) {
				return;
			}
			String pid = core.profileId();
			CompletableFuture<Optional<JsonObject>> museumFut = HypixelApiClient.skyblockMuseum(uuid, pid);
			CompletableFuture<Optional<JsonObject>> electionFut = HypixelApiClient.skyblockElection();
			CompletableFuture<Optional<JsonObject>> auctionFut = HypixelApiClient.skyblockAuction(uuid);
			CompletableFuture<Optional<JsonArray>> soldFut = CoflnetApiClient.playerAuctions(uuid, 0);
			CompletableFuture<Optional<JsonArray>> bidsFut = CoflnetApiClient.playerBids(uuid, 0);
			startProgressiveEnrichment(
				core,
				nameKey(name),
				root,
				museumFut,
				electionFut,
				auctionFut,
				soldFut,
				bidsFut,
				onUpdate
			);
		});
		return coreFuture;
	}

	private static String nameKey(String name) {
		return "n:" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
	}

	private static String uuidKey(UUID uuid) {
		return "u:" + HypixelApiClient.undashed(uuid);
	}

	private static LoadedProfile getCached(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		CacheEntry entry = CACHE.get(key);
		if (entry == null) {
			return null;
		}
		if (!entry.fresh()) {
			CACHE.remove(key, entry);
			return null;
		}
		return entry.profile();
	}

	private static void putCache(String key, LoadedProfile profile) {
		if (key == null || key.isBlank() || profile == null || !profile.ok()) {
			return;
		}
		CACHE.put(key, new CacheEntry(profile, System.currentTimeMillis() + CACHE_TTL_MS));
	}

	private record CacheEntry(LoadedProfile profile, long expiresAtMs) {
		boolean fresh() {
			return System.currentTimeMillis() < this.expiresAtMs;
		}
	}

	private static JsonObject selectedProfile(JsonObject root) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		if (profiles == null) {
			return null;
		}
		JsonObject best = null;
		for (JsonElement element : profiles) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			boolean selected = profile.has("selected") && profile.get("selected").getAsBoolean();
			if (selected || best == null) {
				best = profile;
				if (selected) {
					break;
				}
			}
		}
		return best;
	}

	public static LoadedProfile failed(String name, String error) {
		return new LoadedProfile(
			ProfileSnapshot.loading(name),
			DungeonSnapshot.empty(),
			InventorySnapshot.empty(),
			PetSnapshot.empty(),
			AuctionSnapshot.empty(),
			CollectionSnapshot.empty(),
			GardenSnapshot.empty(),
			MiningSnapshot.empty(),
			ForagingSnapshot.empty(),
			FishingSnapshot.empty(),
			CrimsonSnapshot.empty(),
			RiftSnapshot.empty(),
			BestiarySnapshot.empty(),
			EventsSnapshot.empty(),
			MiscStatsSnapshot.empty(),
			null,
			null,
			null,
			List.of(),
			WeightBreakdown.empty(dev.vy.betterpv.client.weight.WeightSystem.SENITHER),
			WeightBreakdown.empty(dev.vy.betterpv.client.weight.WeightSystem.LILY),
			NetworthBreakdown.empty(error),
			NetworthBreakdown.empty(error),
			NetworthBreakdown.empty(error),
			NetworthBreakdown.empty(error),
			emptyArmor(),
			PlayerStatsSnapshot.empty(),
			error
		);
	}

	private static LoadedProfile fail(String name, String error) {
		return failed(name, error);
	}

	private static ItemStack[] emptyArmor() {
		return new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
	}

	private static LoadedProfile parse(
		String name,
		UUID uuid,
		JsonObject root,
		JsonObject museumRoot,
		JsonObject electionRoot,
		AuctionSnapshot auctions
	) {
		return parse(name, uuid, root, museumRoot, electionRoot, auctions, null);
	}

	private static LoadedProfile parse(
		String name,
		UUID uuid,
		JsonObject root,
		JsonObject museumRoot,
		JsonObject electionRoot,
		AuctionSnapshot auctions,
		String preferredProfileId
	) {
		try {
			return parseUnsafe(name, uuid, root, museumRoot, electionRoot, auctions, true, preferredProfileId);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Profile parse failed for {}", name, exception);
			return fail(name, exception.getMessage() == null ? "Parse failed" : exception.getMessage());
		}
	}

	public static LoadedProfile parseByProfileId(String name, UUID uuid, JsonObject root, String profileId) {
		if (root == null || profileId == null || profileId.isBlank()) {
			return fail(name, "Missing profile");
		}
		try {
			return InventoryDecoder.withSharedDecode(() -> parseUnsafe(
				name, uuid, root, null, null, AuctionSnapshot.empty(), true, profileId
			));
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Profile re-parse failed for {} ({})", name, profileId, exception);
			return fail(name, exception.getMessage() == null ? "Parse failed" : exception.getMessage());
		}
	}

	private static LoadedProfile parseHomeCore(String name, UUID uuid, JsonObject root) {
		return parseHomeCore(name, uuid, root, null);
	}

	/**
	 * Minimum valid Home snapshot only. No inventory UI, tab snapshots, museum, or networth.
	 * Fatal validation failures return {@code !ok()} so the loading egg stays up.
	 */
	private static LoadedProfile parseHomeCore(String name, UUID uuid, JsonObject root, String preferredProfileId) {
		try {
			return parseHomeCoreUnsafe(name, uuid, root, preferredProfileId);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Core profile parse failed for {}", name, exception);
			return fail(name, exception.getMessage() == null ? "Parse failed" : exception.getMessage());
		}
	}

	private static LoadedProfile parseHomeCoreUnsafe(
		String name,
		UUID uuid,
		JsonObject root,
		String preferredProfileId
	) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		if (profiles == null || profiles.isEmpty()) {
			return fail(name, "No SkyBlock profiles");
		}
		JsonObject best = pickProfile(profiles, preferredProfileId);
		String cuteName = "Unknown";
		String undashed = HypixelApiClient.undashed(uuid);
		String profileId = null;
		if (best != null) {
			cuteName = best.has("cute_name") ? best.get("cute_name").getAsString() : cuteName;
			profileId = best.has("profile_id") ? best.get("profile_id").getAsString() : profileId;
		}
		List<ProfileChoice> choices = listProfileChoices(profiles, profileId, undashed);
		if (best == null) {
			return fail(name, "No usable profile");
		}
		JsonObject members = best.has("members") && best.get("members").isJsonObject()
			? best.getAsJsonObject("members")
			: null;
		JsonObject member = findMember(members, undashed);
		if (member == null) {
			return fail(name, "Member data missing");
		}

		warmCoopMemberNames(choices, undashed, name, null);

		BetterPV.LOGGER.info("Parsing home core for {} ({})", name, cuteName);

		Map<String, Leveling.Progress> weightLevels = WeightCalculator.buildLevels(member);
		WeightBreakdown senither = WeightCalculator.senither(member, weightLevels);
		WeightBreakdown lily = WeightCalculator.lily(member, weightLevels);

		List<ProfileSnapshot.SkillEntry> skills = buildHomeSkills(member);
		ProfileSnapshot.SkillEntry social = buildSocial(member);
		ProfileSnapshot.SkillEntry runecrafting = buildRunecrafting(member);
		List<ProfileSnapshot.SlayerEntry> slayers = buildHomeSlayers(member);
		ProfileSnapshot.ActiveSlayerQuest activeSlayer = parseActiveSlayerQuest(member);

		int sbLevel = 0;
		int sbXp = 0;
		JsonObject leveling = Leveling.obj(member.get("leveling"));
		if (leveling != null) {
			Float experience = Leveling.num(leveling.get("experience"));
			if (experience != null) {
				sbLevel = (int) Math.floor(experience / 100F);
				sbXp = Math.round(experience % 100F);
			}
		}
		ProfileSnapshot.EmblemInfo emblems = parseEmblems(leveling);

		double purseCoins = NetworthCalculator.purse(member);
		double bankCoins = NetworthCalculator.bank(best, member);
		List<ProfileSnapshot.BankTransaction> bankTransactions = parseBankTransactions(best);

		ProfileSnapshot snapshot = new ProfileSnapshot(
			name,
			uuid,
			cuteName,
			sbLevel,
			sbXp,
			FormatUtil.weight(senither.total()),
			"…",
			purseCoins,
			bankCoins,
			bankTransactions,
			skills,
			slayers,
			social,
			runecrafting,
			activeSlayer,
			emblems
		);

		Map<String, List<InventoryDecoder.Stack>> homeGear = InventoryDecoder.parseHomeGear(member);
		PlayerStatsSnapshot playerStats = PlayerStatsCalculator.fromMember(member, homeGear);
		ItemStack[] armor = ArmorStacks.fromMember(member);

		MiscStatsSnapshot misc;
		try {
			misc = MiscStatsSnapshot.from(best, member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Misc stats parse failed for {}", name, exception);
			misc = MiscStatsSnapshot.empty();
		}

		return new LoadedProfile(
			snapshot,
			DungeonSnapshot.empty(),
			InventorySnapshot.empty(),
			PetSnapshot.empty(),
			AuctionSnapshot.empty(),
			CollectionSnapshot.empty(),
			GardenSnapshot.empty(),
			MiningSnapshot.empty(),
			ForagingSnapshot.empty(),
			FishingSnapshot.empty(),
			CrimsonSnapshot.empty().withPlayerStats(playerStats),
			RiftSnapshot.empty(),
			BestiarySnapshot.empty(),
			EventsSnapshot.empty(),
			misc,
			null,
			profileId,
			root,
			choices,
			senither,
			lily,
			NetworthBreakdown.empty("Loading networth"),
			NetworthBreakdown.empty("Loading networth"),
			NetworthBreakdown.empty("Loading networth"),
			NetworthBreakdown.empty("Loading networth"),
			armor,
			playerStats,
			null
		);
	}

	private static List<ProfileSnapshot.SkillEntry> buildHomeSkills(JsonObject member) {
		List<ProfileSnapshot.SkillEntry> skills = new ArrayList<>();
		for (String skill : HOME_SKILLS) {
			int cap = Leveling.skillCap(skill, member);
			Leveling.Progress progress = Leveling.getLevel(
				Leveling.skillTable(skill), Leveling.readSkillXpDouble(member, skill), cap, false
			);
			skills.add(new ProfileSnapshot.SkillEntry(
				skill,
				title(skill),
				progress.cappedLevel(),
				progress.fill(),
				progress.maxed(),
				progress.skillHover(title(skill)),
				progress.skillHoverLines(title(skill))
			));
		}
		return skills;
	}

	private static ProfileSnapshot.SkillEntry buildSocial(JsonObject member) {
		float socialXp = (float) Leveling.readSkillXpDouble(member, "social");
		int socialCap = Leveling.skillCap("social", member);
		Leveling.Progress socialProgress = Leveling.getLevel(Leveling.skillTable("social"), socialXp, socialCap, false);
		return new ProfileSnapshot.SkillEntry(
			"social",
			"Social",
			socialProgress.cappedLevel(),
			socialProgress.fill(),
			socialProgress.maxed(),
			socialProgress.skillHover("Social"),
			socialProgress.skillHoverLines("Social")
		);
	}

	private static ProfileSnapshot.SkillEntry buildRunecrafting(JsonObject member) {
		float xp = (float) Leveling.readSkillXpDouble(member, "runecrafting");
		int cap = Leveling.skillCap("runecrafting", member);
		Leveling.Progress progress = Leveling.getLevel(Leveling.skillTable("runecrafting"), xp, cap, false);
		return new ProfileSnapshot.SkillEntry(
			"runecrafting",
			"Runecrafting",
			progress.cappedLevel(),
			progress.fill(),
			progress.maxed(),
			progress.skillHover("Runecrafting"),
			progress.skillHoverLines("Runecrafting")
		);
	}

	private static ProfileSnapshot.ActiveSlayerQuest parseActiveSlayerQuest(JsonObject member) {
		JsonObject slayer = Leveling.obj(member == null ? null : member.get("slayer"));
		JsonObject quest = slayer == null ? null : Leveling.obj(slayer.get("slayer_quest"));
		if (quest == null) {
			return null;
		}
		String type = str(quest.get("type"));
		if (type.isBlank()) {
			return null;
		}
		Float tierRaw = Leveling.num(quest.get("tier"));
		if (tierRaw == null) {
			return null;
		}
		int tier = Math.max(0, Math.round(tierRaw)) + 1;
		boolean spawned = Leveling.num(quest.get("spawn_timestamp")) != null;
		boolean solo = bool(quest, "solo");
		Float combat = Leveling.num(quest.get("combat_xp"));
		String island = InventoryDecoder.prettyWords(str(quest.get("last_killed_mob_island")));
		return new ProfileSnapshot.ActiveSlayerQuest(
			type.toLowerCase(Locale.ROOT),
			slayerDisplayName(type),
			tier,
			spawned,
			solo,
			combat == null ? 0F : Math.max(0F, combat),
			island
		);
	}

	private static String slayerDisplayName(String type) {
		if (type == null || type.isBlank()) {
			return "";
		}
		String key = type.trim().toLowerCase(Locale.ROOT);
		for (String[] pair : HOME_SLAYERS) {
			if (pair[0].equals(key)) {
				return pair[1];
			}
		}
		return title(key);
	}

	private static ProfileSnapshot.EmblemInfo parseEmblems(JsonObject leveling) {
		if (leveling == null) {
			return ProfileSnapshot.EmblemInfo.empty();
		}
		java.util.LinkedHashSet<String> unlocked = new java.util.LinkedHashSet<>();
		addEmblemIds(unlocked, leveling.get("emblem_unlocks"));
		addEmblemIds(unlocked, leveling.get("unlocked_emblems"));
		addEmblemIds(unlocked, leveling.get("emblems"));
		if (leveling.has("completed_tasks") && leveling.get("completed_tasks").isJsonArray()) {
			for (JsonElement el : leveling.getAsJsonArray("completed_tasks")) {
				String id = emblemId(el);
				if (id.isBlank()) {
					continue;
				}
				String upper = id.toUpperCase(Locale.ROOT);
				if (upper.contains("EMBLEM") || upper.contains("SYMBOL")) {
					unlocked.add(id);
				}
			}
		}
		String selected = str(leveling.get("selected_symbol"));
		if (selected.isBlank()) {
			selected = str(leveling.get("selected_emblem"));
		}
		if (!selected.isBlank()) {
			unlocked.add(selected);
		}
		if (unlocked.isEmpty() && selected.isBlank()) {
			return ProfileSnapshot.EmblemInfo.empty();
		}
		return new ProfileSnapshot.EmblemInfo(selected, List.copyOf(unlocked));
	}

	private static void addEmblemIds(java.util.Set<String> out, JsonElement raw) {
		if (raw == null || raw.isJsonNull()) {
			return;
		}
		if (raw.isJsonArray()) {
			for (JsonElement el : raw.getAsJsonArray()) {
				String id = emblemId(el);
				if (!id.isBlank()) {
					out.add(id);
				}
			}
			return;
		}
		if (raw.isJsonObject()) {
			for (var entry : raw.getAsJsonObject().entrySet()) {
				String id = emblemId(entry.getValue());
				if (id.isBlank()) {
					id = entry.getKey() == null ? "" : entry.getKey().trim();
				}
				if (!id.isBlank()) {
					out.add(id);
				}
			}
		}
	}

	private static String emblemId(JsonElement el) {
		if (el == null || el.isJsonNull()) {
			return "";
		}
		if (el.isJsonPrimitive()) {
			return str(el);
		}
		if (!el.isJsonObject()) {
			return "";
		}
		JsonObject obj = el.getAsJsonObject();
		String id = str(obj.get("id"));
		if (id.isBlank()) {
			id = str(obj.get("emblem"));
		}
		if (id.isBlank()) {
			id = str(obj.get("symbol"));
		}
		if (id.isBlank()) {
			id = str(obj.get("name"));
		}
		return id;
	}

	private static String str(JsonElement element) {
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return "";
		}
		try {
			return element.getAsString();
		} catch (Exception ignored) {
			return "";
		}
	}

	private static boolean bool(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) {
			return false;
		}
		try {
			return obj.get(key).getAsBoolean();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static List<ProfileSnapshot.SlayerEntry> buildHomeSlayers(JsonObject member) {
		List<ProfileSnapshot.SlayerEntry> slayers = new ArrayList<>();
		for (String[] pair : HOME_SLAYERS) {
			float xp = Leveling.readSlayerXp(member, pair[0]);
			JsonArray slayerTable = RepoData.slayerXp(pair[0]);
			int slayerCap = slayerTable == null || slayerTable.isEmpty() ? 9 : slayerTable.size();
			Leveling.Progress progress = Leveling.getLevel(slayerTable, xp, slayerCap, true);
			int[] kills = Leveling.readSlayerBossKills(member, pair[0]);
			List<Integer> killList = new ArrayList<>(kills.length);
			for (int kill : kills) {
				killList.add(kill);
			}
			slayers.add(new ProfileSnapshot.SlayerEntry(
				pair[0],
				pair[1],
				(int) Math.floor(progress.level()),
				progress.fill(),
				progress.maxed(),
				progress.totalXp(),
				progress.slayerHoverWithKills(pair[1], pair[0], kills),
				killList,
				progress.slayerHoverLinesWithKills(pair[1], pair[0], kills)
			));
		}
		return slayers;
	}

	private static LoadedProfile parseUnsafe(
		String name,
		UUID uuid,
		JsonObject root,
		JsonObject museumRoot,
		JsonObject electionRoot,
		AuctionSnapshot auctions,
		boolean includeNetworth,
		String preferredProfileId
	) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		if (profiles == null || profiles.isEmpty()) {
			return fail(name, "No SkyBlock profiles");
		}
		List<ProfileChoice> choices;
		JsonObject best = pickProfile(profiles, preferredProfileId);
		String cuteName = "Unknown";
		String undashed = HypixelApiClient.undashed(uuid);
		String profileId = null;
		if (best != null) {
			cuteName = best.has("cute_name") ? best.get("cute_name").getAsString() : cuteName;
			profileId = best.has("profile_id") ? best.get("profile_id").getAsString() : profileId;
		}
		choices = listProfileChoices(profiles, profileId, undashed);
		if (best == null) {
			return fail(name, "No usable profile");
		}
		JsonObject members = best.has("members") && best.get("members").isJsonObject()
			? best.getAsJsonObject("members")
			: null;
		JsonObject member = findMember(members, undashed);
		if (member == null) {
			return fail(name, "Member data missing");
		}

		warmCoopMemberNames(choices, undashed, name, null);

		Map<String, Leveling.Progress> weightLevels = WeightCalculator.buildLevels(member);
		WeightBreakdown senither = WeightCalculator.senither(member, weightLevels);
		WeightBreakdown lily = WeightCalculator.lily(member, weightLevels);

		// Never block first paint on prices. Brief wait only during enrichment.
		if (includeNetworth && !ItemPricer.isReady()) {
			ItemPricer.awaitReady(1_500L);
		}
		JsonObject museumMember = includeNetworth ? findMuseumMember(museumRoot, profileId, undashed) : null;
		BetterPV.LOGGER.info("Parsing profile {} ({})", name, cuteName);

		Map<String, List<InventoryDecoder.Stack>> inventoryCategories =
			InventoryDecoder.parseCategories(member, museumMember);

		boolean pricesReady = includeNetworth && ItemPricer.isReady();
		NetworthBreakdown networthNormal = pricesReady
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.NORMAL)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown networthNonCosmetic = pricesReady
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.NON_COSMETIC)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown networthUnsoulbound = pricesReady
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown networthUnsoulboundNonCosmetic = pricesReady
			? NetworthCalculator.calculate(
				member, best, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND_NON_COSMETIC
			)
			: NetworthBreakdown.empty("Loading networth");

		List<ProfileSnapshot.SkillEntry> skills = buildHomeSkills(member);
		ProfileSnapshot.SkillEntry social = buildSocial(member);
		ProfileSnapshot.SkillEntry runecrafting = buildRunecrafting(member);
		List<ProfileSnapshot.SlayerEntry> slayers = buildHomeSlayers(member);
		ProfileSnapshot.ActiveSlayerQuest activeSlayer = parseActiveSlayerQuest(member);

		int sbLevel = 0;
		int sbXp = 0;
		JsonObject leveling = Leveling.obj(member.get("leveling"));
		if (leveling != null) {
			Float experience = Leveling.num(leveling.get("experience"));
			if (experience != null) {
				sbLevel = (int) Math.floor(experience / 100F);
				sbXp = Math.round(experience % 100F);
			}
		}
		ProfileSnapshot.EmblemInfo emblems = parseEmblems(leveling);

		String nwText = !includeNetworth || !pricesReady
			? "…"
			: networthNormal.total() > 0
			? FormatUtil.shortCoins(networthNormal.total())
			: "-";

		double purseCoins = NetworthCalculator.purse(member);
		double bankCoins = NetworthCalculator.bank(best, member);
		List<ProfileSnapshot.BankTransaction> bankTransactions = parseBankTransactions(best);

		ProfileSnapshot snapshot = new ProfileSnapshot(
			name,
			uuid,
			cuteName,
			sbLevel,
			sbXp,
			FormatUtil.weight(senither.total()),
			nwText,
			purseCoins,
			bankCoins,
			bankTransactions,
			skills,
			slayers,
			social,
			runecrafting,
			activeSlayer,
			emblems,
			dev.vy.betterpv.client.slayer.SlayerMayorMods.from(electionRoot)
		);

		DungeonSnapshot dungeons = parseDungeons(member, museumMember, electionRoot, inventoryCategories);

		InventorySnapshot inventories;
		try {
			inventories = InventoryDecoder.parseUi(member);
			dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory.warmAsync(inventories);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Inventory decode failed for {}", name, exception);
			inventories = InventorySnapshot.empty();
		}

		PetSnapshot pets;
		try {
			pets = PetSnapshot.fromMember(member);
			dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory.warmPetsAsync(pets);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Pets decode failed for {}", name, exception);
			pets = PetSnapshot.empty();
		}

		CollectionSnapshot collections;
		try {
			collections = CollectionSnapshot.fromProfile(members, uuid, name);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Collections decode failed for {}", name, exception);
			collections = CollectionSnapshot.empty();
		}

		GardenSnapshot garden;
		try {
			garden = GardenSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Garden member parse failed for {}", name, exception);
			garden = GardenSnapshot.empty();
		}

		MiningSnapshot mining;
		try {
			mining = MiningSnapshot.fromMember(member);
			mining = mining.withColeWeight(ColeWeight.calculate(mining, collections, member));
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Mining member parse failed for {}", name, exception);
			mining = MiningSnapshot.empty();
		}
		ForagingSnapshot foraging;
		try {
			foraging = ForagingSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Foraging member parse failed for {}", name, exception);
			foraging = ForagingSnapshot.empty();
		}
		FishingSnapshot fishing;
		try {
			fishing = FishingSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Fishing member parse failed for {}", name, exception);
			fishing = FishingSnapshot.empty();
		}
		CrimsonSnapshot crimson;
		try {
			crimson = CrimsonSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Crimson member parse failed for {}", name, exception);
			crimson = CrimsonSnapshot.empty();
		}
		RiftSnapshot rift;
		try {
			rift = RiftSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Rift member parse failed for {}", name, exception);
			rift = RiftSnapshot.empty();
		}
		BestiarySnapshot bestiary;
		try {
			bestiary = BestiarySnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Bestiary member parse failed for {}", name, exception);
			bestiary = BestiarySnapshot.empty();
		}
		EventsSnapshot events;
		try {
			events = EventsSnapshot.fromMember(member, root, uuid);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Events member parse failed for {}", name, exception);
			events = EventsSnapshot.empty();
		}
		MiscStatsSnapshot misc;
		try {
			misc = MiscStatsSnapshot.from(best, member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Misc stats parse failed for {}", name, exception);
			misc = MiscStatsSnapshot.empty();
		}
		BetterPV.LOGGER.info("Profile ready for {} (nw={})", name, nwText);
		AuctionSnapshot auctionSnapshot = auctions == null ? AuctionSnapshot.empty() : auctions;
		auctionSnapshot = auctionSnapshot.withStats(AuctionSnapshot.Stats.fromMember(member));
		PlayerStatsSnapshot playerStats = PlayerStatsCalculator.fromMember(member, inventoryCategories);
		crimson = crimson.withPlayerStats(playerStats);

		LoadedProfile loaded = new LoadedProfile(
			snapshot,
			dungeons,
			inventories,
			pets,
			auctionSnapshot,
			collections,
			garden,
			mining,
			foraging,
			fishing,
			crimson,
			rift,
			bestiary,
			events,
			misc,
			museumMember,
			profileId,
			root,
			choices,
			senither,
			lily,
			networthNormal,
			networthNonCosmetic,
			networthUnsoulbound,
			networthUnsoulboundNonCosmetic,
			ArmorStacks.fromMember(member),
			playerStats,
			null
		);

		if (includeNetworth && !pricesReady) {
			scheduleNetworthRefresh(loaded, member, best, museumMember, inventoryCategories, null);
		}
		return loaded;
	}

	private static void scheduleNetworthRefresh(
		LoadedProfile base,
		JsonObject member,
		JsonObject profile,
		JsonObject museumMember,
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories,
		Consumer<LoadedProfile> onUpdate
	) {
		if (base == null || !base.ok() || base.snapshot() == null || base.snapshot().playerUuid() == null) {
			return;
		}
		UUID uuid = base.snapshot().playerUuid();
		String name = base.snapshot().playerName();
		CompletableFuture.runAsync(() -> {
			ItemPricer.awaitReady(30_000L);
			if (!ItemPricer.isReady()) {
				return;
			}
			try {
				NetworthBreakdown normal = NetworthCalculator.calculate(
					member, profile, museumMember, inventoryCategories, NetworthMode.NORMAL
				);
				NetworthBreakdown nonCosmetic = NetworthCalculator.calculate(
					member, profile, museumMember, inventoryCategories, NetworthMode.NON_COSMETIC
				);
				NetworthBreakdown unsoulbound = NetworthCalculator.calculate(
					member, profile, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND
				);
				NetworthBreakdown unsoulboundNonCosmetic = NetworthCalculator.calculate(
					member, profile, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND_NON_COSMETIC
				);
				String nwText = normal.total() > 0 ? FormatUtil.shortCoins(normal.total()) : "-";
				LoadedProfile refreshed = new LoadedProfile(
					base.snapshot().withNetworthText(nwText),
					base.dungeons(),
					base.inventories(),
					base.pets(),
					base.auctions(),
					base.collections(),
					base.garden(),
					base.mining(),
					base.foraging(),
					base.fishing(),
					base.crimson(),
					base.rift(),
					base.bestiary(),
					base.events(),
					base.misc(),
					base.museumMember(),
					base.profileId(),
					base.profilesRoot(),
					base.profiles(),
					base.senither(),
					base.lily(),
					normal,
					nonCosmetic,
					unsoulbound,
					unsoulboundNonCosmetic,
					base.armor(),
					base.playerStats(),
					null
				);
				putCache(uuidKey(uuid), refreshed);
				putCache(nameKey(name), refreshed);
				if (onUpdate != null) {
					onUpdate.accept(refreshed);
				}
				for (Consumer<LoadedProfile> listener : NETWORTH_LISTENERS) {
					try {
						listener.accept(refreshed);
					} catch (Exception ignored) {
					}
				}
			} catch (Exception exception) {
				BetterPV.LOGGER.warn("Deferred networth refresh failed for {}", name, exception);
			}
		}, ENRICH_EXECUTOR);
	}

	private static final java.util.concurrent.CopyOnWriteArrayList<Consumer<LoadedProfile>> NETWORTH_LISTENERS =
		new java.util.concurrent.CopyOnWriteArrayList<>();

	/** Registers a listener for deferred networth refreshes (screen generation must filter stale). */
	public static void addNetworthListener(Consumer<LoadedProfile> listener) {
		if (listener != null) {
			NETWORTH_LISTENERS.add(listener);
		}
	}

	public static void removeNetworthListener(Consumer<LoadedProfile> listener) {
		if (listener != null) {
			NETWORTH_LISTENERS.remove(listener);
		}
	}

	/** Prefer {@code preferredProfileId}, else Hypixel-selected, else first usable profile. */
	private static JsonObject pickProfile(JsonArray profiles, String preferredProfileId) {
		if (profiles == null) {
			return null;
		}
		JsonObject preferred = null;
		JsonObject selected = null;
		JsonObject first = null;
		for (JsonElement element : profiles) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			if (first == null) {
				first = profile;
			}
			String id = profile.has("profile_id") ? profile.get("profile_id").getAsString() : null;
			if (preferredProfileId != null && preferredProfileId.equals(id)) {
				preferred = profile;
			}
			if (profile.has("selected") && profile.get("selected").getAsBoolean()) {
				selected = profile;
			}
		}
		if (preferred != null) {
			return preferred;
		}
		return selected != null ? selected : first;
	}

	private static List<ProfileChoice> listProfileChoices(
		JsonArray profiles,
		String activeProfileId,
		String viewedUuidUndashed
	) {
		if (profiles == null || profiles.isEmpty()) {
			return List.of();
		}
		String viewed = viewedUuidUndashed == null ? "" : viewedUuidUndashed.replace("-", "").toLowerCase(Locale.ROOT);
		List<ProfileChoice> out = new ArrayList<>(profiles.size());
		for (JsonElement element : profiles) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			String id = profile.has("profile_id") ? profile.get("profile_id").getAsString() : "";
			String cute = profile.has("cute_name") ? profile.get("cute_name").getAsString() : "Unknown";
			boolean hypixelSelected = profile.has("selected") && profile.get("selected").getAsBoolean();
			boolean selected = activeProfileId != null && !activeProfileId.isBlank()
				? activeProfileId.equals(id)
				: hypixelSelected;
			String mode = "";
			if (profile.has("game_mode") && profile.get("game_mode").isJsonPrimitive()
				&& !profile.get("game_mode").isJsonNull()) {
				mode = profile.get("game_mode").getAsString();
			}
			long created = 0L;
			if (profile.has("created_at") && profile.get("created_at").isJsonPrimitive()) {
				try {
					created = profile.get("created_at").getAsLong();
				} catch (Exception ignored) {
					created = 0L;
				}
			}
			out.add(new ProfileChoice(cute, id, selected, mode, created, parseCoopSummary(profile, viewed)));
		}
		if (activeProfileId == null || activeProfileId.isBlank()) {
			boolean any = false;
			for (ProfileChoice choice : out) {
				if (choice.selected()) {
					any = true;
					break;
				}
			}
			if (!any && !out.isEmpty()) {
				ProfileChoice first = out.get(0);
				out.set(0, new ProfileChoice(
					first.cuteName(),
					first.profileId(),
					true,
					first.gameMode(),
					first.createdAtMs(),
					first.coop()
				));
			}
		}
		return out;
	}

	private static CoopSummary parseCoopSummary(JsonObject profile, String viewedUuidUndashed) {
		JsonObject members = Leveling.obj(profile == null ? null : profile.get("members"));
		if (members == null || members.isEmpty()) {
			return CoopSummary.solo();
		}
		List<CoopMemberRef> current = new ArrayList<>();
		List<CoopMemberRef> former = new ArrayList<>();
		for (var entry : members.entrySet()) {
			if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
				continue;
			}
			String uuid = entry.getKey() == null ? "" : entry.getKey().replace("-", "").toLowerCase(Locale.ROOT);
			if (uuid.isBlank()) {
				continue;
			}
			JsonObject memberObj = entry.getValue().getAsJsonObject();
			CoopMemberRef ref = new CoopMemberRef(uuid, shortCoopUuid(uuid));
			if (isDeletedCoopMember(memberObj)) {
				former.add(ref);
			} else if (!uuid.equals(viewedUuidUndashed)) {
				current.add(ref);
			}
		}
		current.sort(Comparator.comparing(CoopMemberRef::fallbackName, String.CASE_INSENSITIVE_ORDER));
		former.sort(Comparator.comparing(CoopMemberRef::fallbackName, String.CASE_INSENSITIVE_ORDER));
		return new CoopSummary(current.size(), former.size(), List.copyOf(current), List.copyOf(former));
	}

	private static boolean isDeletedCoopMember(JsonObject memberObj) {
		JsonObject profileNode = Leveling.obj(memberObj.get("profile"));
		if (profileNode == null) {
			return false;
		}
		JsonElement notice = profileNode.get("deletion_notice");
		return notice != null && !notice.isJsonNull();
	}

	private static String shortCoopUuid(String uuid) {
		if (uuid == null || uuid.length() < 8) {
			return uuid == null ? "?" : uuid;
		}
		return uuid.substring(0, 8);
	}

	public static String coopMemberDisplayName(CoopMemberRef member) {
		if (member == null) {
			return "?";
		}
		String resolved = COOP_MEMBER_NAMES.get(member.uuid());
		return resolved != null && !resolved.isBlank() ? resolved : member.fallbackName();
	}

	public static boolean coopNameResolved(CoopMemberRef member) {
		if (member == null || member.uuid().isBlank()) {
			return false;
		}
		String name = coopMemberDisplayName(member);
		if (name == null || name.isBlank() || "?".equals(name)) {
			return false;
		}
		return !name.equals(shortCoopUuid(member.uuid()));
	}

	public static void warmCoopMemberNames(
		List<ProfileChoice> choices,
		String viewedUuidUndashed,
		String viewedName,
		Runnable onResolved
	) {
		if (choices == null || choices.isEmpty()) {
			return;
		}
		String viewed = viewedUuidUndashed == null ? "" : viewedUuidUndashed.replace("-", "").toLowerCase(Locale.ROOT);
		if (!viewed.isBlank() && viewedName != null && !viewedName.isBlank()) {
			COOP_MEMBER_NAMES.put(viewed, viewedName);
		}
		Set<String> pending = new HashSet<>();
		for (ProfileChoice choice : choices) {
			collectCoopNameLookups(choice.coop().currentMembers(), pending);
			collectCoopNameLookups(choice.coop().formerMembers(), pending);
		}
		for (String uuidKey : pending) {
			UUID uuid = HypixelApiClient.parseUndashedUuid(uuidKey);
			if (uuid == null) {
				continue;
			}
			HypixelApiClient.resolveName(uuid).thenAccept(opt -> {
				opt.ifPresent(id -> {
					COOP_MEMBER_NAMES.put(uuidKey, id.name());
					if (onResolved != null) {
						onResolved.run();
					}
				});
			});
		}
	}

	private static void collectCoopNameLookups(List<CoopMemberRef> members, Set<String> pending) {
		for (CoopMemberRef member : members) {
			if (member.uuid().isBlank()) {
				continue;
			}
			if (COOP_MEMBER_NAMES.containsKey(member.uuid())) {
				continue;
			}
			if (!member.fallbackName().equals(shortCoopUuid(member.uuid()))) {
				COOP_MEMBER_NAMES.put(member.uuid(), member.fallbackName());
				continue;
			}
			pending.add(member.uuid());
		}
	}

	private static List<ProfileSnapshot.BankTransaction> parseBankTransactions(JsonObject profileRoot) {
		if (profileRoot == null) {
			return List.of();
		}
		JsonObject banking = Leveling.obj(profileRoot.get("banking"));
		if (banking == null || !(banking.get("transactions") instanceof JsonArray arr) || arr.isEmpty()) {
			return List.of();
		}
		List<ProfileSnapshot.BankTransaction> out = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject tx = el.getAsJsonObject();
			String action = tx.has("action") && tx.get("action").isJsonPrimitive()
				? tx.get("action").getAsString()
				: "";
			double amount = 0D;
			if (tx.has("amount") && tx.get("amount").isJsonPrimitive()) {
				try {
					amount = tx.get("amount").getAsDouble();
				} catch (Exception ignored) {
					amount = 0D;
				}
			}
			String initiator = "";
			if (tx.has("initiator_name") && tx.get("initiator_name").isJsonPrimitive()) {
				initiator = tx.get("initiator_name").getAsString();
				if (initiator != null) {
					initiator = initiator.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "").trim();
				} else {
					initiator = "";
				}
			}
			long ts = 0L;
			if (tx.has("timestamp") && tx.get("timestamp").isJsonPrimitive()) {
				try {
					ts = tx.get("timestamp").getAsLong();
				} catch (Exception ignored) {
					ts = 0L;
				}
			}
			if (action.isBlank() && amount <= 0D && ts <= 0L) {
				continue;
			}
			out.add(new ProfileSnapshot.BankTransaction(action, amount, initiator, ts));
		}
		out.sort((a, b) -> Long.compare(b.timestampMs(), a.timestampMs()));
		if (out.size() > 24) {
			return List.copyOf(out.subList(0, 24));
		}
		return List.copyOf(out);
	}

	private static DungeonSnapshot parseDungeons(
		JsonObject member,
		JsonObject museumMember,
		JsonObject electionRoot,
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories
	) {
		float cataXp = Leveling.readCatacombsXp(member);
		Leveling.Progress cata = Leveling.getLevel(RepoData.catacombsXp(), cataXp, 50, false);

		JsonObject dungeons = Leveling.obj(member.get("dungeons"));
		String selected = "";
		long secrets = 0L;
		if (dungeons != null) {
			if (dungeons.has("selected_dungeon_class") && dungeons.get("selected_dungeon_class").isJsonPrimitive()) {
				selected = dungeons.get("selected_dungeon_class").getAsString();
			}
			Float secretsF = Leveling.num(dungeons.get("secrets"));
			if (secretsF != null) {
				secrets = Math.round(secretsF);
			}
		}

		List<DungeonSnapshot.ClassEntry> classes = new ArrayList<>();
		float classSumCapped = 0F;
		float classSumOverflow = 0F;
		int maxedCount = 0;
		for (String classId : DUNGEON_CLASSES) {
			float classXp = Leveling.readClassXp(member, classId);
			Leveling.Progress progress = Leveling.getLevel(
				RepoData.catacombsXp(),
				classXp,
				50,
				false
			);
			int level = (int) Math.floor(progress.level());
			boolean selectedClass = classId.equalsIgnoreCase(selected);
			if (progress.maxed()) {
				maxedCount++;
				classSumCapped += 50F;
				classSumOverflow += progress.level();
			} else {
				classSumCapped += progress.level();
			}
			classes.add(new DungeonSnapshot.ClassEntry(
				classId,
				title(classId),
				level,
				classXp,
				progress.fill(),
				progress.maxed(),
				selectedClass,
				progress.skillHover(title(classId))
			));
		}

		float classAverage;
		float classAvgProgress;
		boolean classAvgMaxed;
		String classAvgHover;
		if (maxedCount == DUNGEON_CLASSES.length) {
			classAverage = classSumOverflow / DUNGEON_CLASSES.length;
			classAvgProgress = 1F;
			classAvgMaxed = true;
			classAvgHover = "Class Average " + FormatUtil.oneDecimal(classAverage) + " - MAX";
		} else {
			classAverage = classSumCapped / DUNGEON_CLASSES.length;
			classAvgProgress = Math.max(0F, Math.min(1F, classAverage / 50F));
			classAvgMaxed = false;
			classAvgHover = "Class Average " + FormatUtil.oneDecimal(classAverage) + " / 50";
		}

		DungeonSnapshot.ModeStats normal = parseMode(member, "catacombs", false);
		DungeonSnapshot.ModeStats master = parseMode(member, "master_catacombs", true);
		long allRuns = Math.max(1L, normal.totalRuns() + master.totalRuns());
		double secretsPerRun = secrets / (double) allRuns;

		DungeonModifierScanner.Mods mods = DungeonModifierScanner.Mods.none();
		try {
			Map<String, List<InventoryDecoder.Stack>> categories = inventoryCategories != null
				? inventoryCategories
				: InventoryDecoder.parseCategories(member, museumMember);
			mods = DungeonModifierScanner.scan(categories);
		} catch (Exception ignored) {
		}

		double mayorFactor = CataXpMath.mayorXpFactor(electionRoot);
		String mayorName = CataXpMath.mayorName(electionRoot);

		java.util.Map<String, Double> essenceBonuses = DungeonModifierScanner.readEssenceClassBonuses(member);
		double graduateBonus = DungeonModifierScanner.readCatacombsGraduateBonus(member);

		JsonObject dailyRuns = Leveling.obj(dungeons == null ? null : dungeons.get("daily_runs"));
		int dailyCount = 0;
		if (dailyRuns != null) {
			Float n = Leveling.num(dailyRuns.get("completed_runs_count"));
			if (n != null) {
				dailyCount = Math.max(0, Math.round(n));
			}
		}
		JsonObject journal = Leveling.obj(dungeons == null ? null : dungeons.get("dungeon_journal"));
		int journals = 0;
		if (journal != null && journal.has("unlocked_journals") && journal.get("unlocked_journals").isJsonArray()) {
			journals = journal.getAsJsonArray("unlocked_journals").size();
		}
		DungeonSnapshot.HubRace race = DungeonSnapshot.parseHubRace(
			dungeons == null ? null : dungeons.get("dungeon_hub_race_settings")
		);

		return new DungeonSnapshot(
			(int) Math.floor(cata.level()),
			cataXp,
			cata.fill(),
			cata.maxed(),
			cata.skillHover("Catacombs"),
			secrets,
			secretsPerRun,
			classAverage,
			classAvgProgress,
			classAvgMaxed,
			classAvgHover,
			classes,
			normal,
			master,
			mods.expertRing(),
			mods.hecatombLevel(),
			mods.scarfBonus(),
			graduateBonus,
			essenceBonuses,
			mayorFactor,
			mayorName,
			EssenceShopData.wither(member),
			EssenceShopData.undead(member),
			EssenceShopData.ice(member),
			EssenceShopData.spider(member),
			EssenceShopData.dragon(member),
			dailyCount,
			journals,
			race
		);
	}

	private static DungeonSnapshot.ModeStats parseMode(JsonObject member, String typeKey, boolean master) {
		JsonObject dungeons = Leveling.obj(member.get("dungeons"));
		JsonObject types = dungeons == null ? null : Leveling.obj(dungeons.get("dungeon_types"));
		JsonObject type = types == null ? null : Leveling.obj(types.get(typeKey));
		JsonObject completions = type == null ? null : Leveling.obj(type.get("tier_completions"));
		JsonObject milestones = type == null ? null : Leveling.obj(type.get("milestone_completions"));
		JsonObject mobsKilled = type == null ? null : Leveling.obj(type.get("mobs_killed"));
		JsonObject bestScore = type == null ? null : Leveling.obj(type.get("best_score"));
		JsonObject mostMobs = type == null ? null : Leveling.obj(type.get("most_mobs_killed"));
		JsonObject fastest = type == null ? null : Leveling.obj(type.get("fastest_time"));
		JsonObject fastestS = type == null ? null : Leveling.obj(type.get("fastest_time_s"));
		JsonObject sPlus = type == null ? null : Leveling.obj(type.get("fastest_time_s_plus"));
		JsonObject mostHealing = type == null ? null : Leveling.obj(type.get("most_healing"));

		List<DungeonSnapshot.FloorEntry> floors = new ArrayList<>();
		long total = 0L;
		int start = master ? 1 : 0;
		for (int floor = start; floor <= 7; floor++) {
			String key = String.valueOf(floor);
			long runs = readLong(completions, key);
			total += runs;
			String label = master ? "M" + floor : (floor == 0 ? "E" : "F" + floor);
			DamagePeak damage = bestDamage(type, key);
			floors.add(new DungeonSnapshot.FloorEntry(
				key,
				label,
				runs,
				readLong(milestones, key),
				readLong(mobsKilled, key),
				readLong(bestScore, key),
				readLong(mostMobs, key),
				readLong(fastest, key),
				readLong(fastestS, key),
				readLong(sPlus, key),
				readDouble(mostHealing, key),
				damage.classId(),
				damage.value()
			));
		}
		return new DungeonSnapshot.ModeStats(total, floors);
	}

	private record DamagePeak(String classId, double value) {
		static DamagePeak none() {
			return new DamagePeak(null, 0D);
		}
	}

	private static DamagePeak bestDamage(JsonObject type, String floorKey) {
		if (type == null) {
			return DamagePeak.none();
		}
		String bestClass = null;
		double best = 0D;
		for (String classId : new String[] {"mage", "berserk", "archer", "healer", "tank"}) {
			JsonObject map = Leveling.obj(type.get("most_damage_" + classId));
			double value = readDouble(map, floorKey);
			if (value > best) {
				best = value;
				bestClass = classId;
			}
		}
		return bestClass == null ? DamagePeak.none() : new DamagePeak(bestClass, best);
	}

	private static long readLong(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return 0L;
		}
		Float num = Leveling.num(object.get(key));
		return num == null ? 0L : Math.round(num);
	}

	private static double readDouble(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return 0D;
		}
		Float num = Leveling.num(object.get(key));
		return num == null ? 0D : num.doubleValue();
	}

	public static JsonObject findMuseumMember(JsonObject museumRoot, String profileId, String undashed) {
		if (museumRoot == null) {
			return null;
		}
		JsonObject members = Leveling.obj(museumRoot.get("members"));
		if (members != null) {
			JsonObject direct = findMember(members, undashed);
			if (direct != null) {
				return direct;
			}
		}
		if (profileId != null && museumRoot.has(profileId) && museumRoot.get(profileId).isJsonObject()) {
			JsonObject profileMuseum = museumRoot.getAsJsonObject(profileId);
			JsonObject nestedMembers = Leveling.obj(profileMuseum.get("members"));
			if (nestedMembers != null) {
				return findMember(nestedMembers, undashed);
			}
			return profileMuseum;
		}
		return findMember(museumRoot, undashed);
	}

	private static JsonObject findMember(JsonObject members, String undashed) {
		if (members == null) {
			return null;
		}
		if (members.has(undashed) && members.get(undashed).isJsonObject()) {
			return members.getAsJsonObject(undashed);
		}
		for (var entry : members.entrySet()) {
			if (entry.getKey() != null && entry.getKey().replace("-", "").equalsIgnoreCase(undashed)
				&& entry.getValue().isJsonObject()) {
				return entry.getValue().getAsJsonObject();
			}
		}
		return null;
	}

	private static String title(String id) {
		return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
	}
}
