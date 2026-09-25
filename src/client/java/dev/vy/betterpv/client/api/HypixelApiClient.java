package dev.vy.betterpv.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.BetterPV;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HypixelApiClient {
	private static final String WORKER_BASE = "https://api.vyriv.dev";
	private static final URI MOJANG_PROFILE = URI.create("https://api.mojang.com/users/profiles/minecraft/");
	private static final URI MOJANG_SESSION = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");
	private static final Duration TIMEOUT = Duration.ofSeconds(12);
	private static final long SPACING_MS = 80L;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ConcurrentHashMap<String, JsonObject> PLAYER_CACHE = new ConcurrentHashMap<>();
	/** Mojang names do not change during this client session often enough to justify repeat lookups. */
	private static final ConcurrentHashMap<String, UuidName> UUID_NAME_CACHE = new ConcurrentHashMap<>();
	/** Small pool so museum + election (and other GETs) can overlap; rate limit still serializes spacing. */
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "BetterPV-HypixelApi");
		t.setDaemon(true);
		return t;
	});
	/** Heavy profile parse (NBT / networth) stays off the HTTP pool. */
	private static final ExecutorService PARSE_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "BetterPV-ProfileParse");
		t.setDaemon(true);
		return t;
	});

	public static ExecutorService parseExecutor() {
		return PARSE_EXECUTOR;
	}

	public static ExecutorService networkExecutor() {
		return EXECUTOR;
	}

	private static long nextRequestAtMillis;

	private HypixelApiClient() {
	}

	public static boolean canFetch() {
		return true;
	}

	public static CompletableFuture<Optional<UuidName>> resolveUuid(String name) {
		String cleaned = name == null ? "" : name.trim();
		if (cleaned.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		UuidName cached = UUID_NAME_CACHE.get(cleaned.toLowerCase(Locale.ROOT));
		if (cached != null) {
			return CompletableFuture.completedFuture(Optional.of(cached));
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(
					MOJANG_PROFILE.resolve(URLEncoder.encode(cleaned, StandardCharsets.UTF_8))
				).timeout(TIMEOUT).GET().build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
					return Optional.<UuidName>empty();
				}
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				UUID uuid = parseUndashedUuid(root.has("id") ? root.get("id").getAsString() : null);
				String resolved = root.has("name") ? root.get("name").getAsString() : cleaned;
				if (uuid == null) {
					return Optional.empty();
				}
				UuidName uuidName = new UuidName(uuid, resolved);
				UUID_NAME_CACHE.put(cleaned.toLowerCase(Locale.ROOT), uuidName);
				UUID_NAME_CACHE.put(resolved.toLowerCase(Locale.ROOT), uuidName);
				return Optional.of(uuidName);
			} catch (Exception exception) {
				BetterPV.LOGGER.warn("Mojang UUID lookup failed for {}", cleaned, exception);
				return Optional.empty();
			}
		}, EXECUTOR);
	}

	public static CompletableFuture<Optional<UuidName>> resolveName(UUID uuid) {
		if (uuid == null) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(MOJANG_SESSION.resolve(id))
					.timeout(TIMEOUT)
					.GET()
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
					return Optional.<UuidName>empty();
				}
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				String resolved = root.has("name") ? root.get("name").getAsString() : null;
				if (resolved == null || resolved.isBlank()) {
					return Optional.empty();
				}
				UuidName uuidName = new UuidName(uuid, resolved);
				UUID_NAME_CACHE.put(resolved.toLowerCase(Locale.ROOT), uuidName);
				return Optional.of(uuidName);
			} catch (Exception exception) {
				BetterPV.LOGGER.debug("Mojang name lookup failed for {}", id, exception);
				return Optional.empty();
			}
		}, EXECUTOR);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockProfiles(UUID uuid) {
		return skyblockProfiles(uuid, null);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockProfiles(UUID uuid, String name) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(
			() -> fetchBomboProfiles(id, name),
			EXECUTOR
		);
	}

	private static Optional<JsonObject> fetchBomboProfiles(String undashedUuid, String username) {
		// 1. Try https://api.bombo.dpdns.org/data/<username or uuid>
		String target = (username != null && !username.isBlank()) ? username.trim() : undashedUuid;
		if (target != null && !target.isBlank()) {
			String url = "https://api.bombo.dpdns.org/data/" + URLEncoder.encode(target, StandardCharsets.UTF_8);
			long started = System.currentTimeMillis();
			try {
				HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "BomboAddons/" + me.bombo.bomboaddons.Constants.myVersion())
					.timeout(TIMEOUT)
					.GET();
				me.bombo.bomboaddons.util.BomboApiUrl.attachApiKey(b);
				HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
				me.bombo.bomboaddons.util.ApiHistory.http("GET", url, resp.statusCode(), System.currentTimeMillis() - started);
				if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
					JsonElement el = JsonParser.parseString(resp.body());
					if (el.isJsonObject()) {
						Optional<JsonObject> normalized = normalizeBomboProfile(el.getAsJsonObject());
						if (normalized.isPresent()) return normalized;
					}
				}
			} catch (Throwable t) {
				me.bombo.bomboaddons.util.ApiHistory.http("GET", url, 0, System.currentTimeMillis() - started);
			}
		}

		// 2. Try https://api.bombo.dpdns.org/data/<undashedUuid> if target was username
		if (undashedUuid != null && !undashedUuid.isBlank() && !undashedUuid.equalsIgnoreCase(target)) {
			String url = "https://api.bombo.dpdns.org/data/" + undashedUuid;
			long started = System.currentTimeMillis();
			try {
				HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "BomboAddons/" + me.bombo.bomboaddons.Constants.myVersion())
					.timeout(TIMEOUT)
					.GET();
				me.bombo.bomboaddons.util.BomboApiUrl.attachApiKey(b);
				HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
				me.bombo.bomboaddons.util.ApiHistory.http("GET", url, resp.statusCode(), System.currentTimeMillis() - started);
				if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
					JsonElement el = JsonParser.parseString(resp.body());
					if (el.isJsonObject()) {
						Optional<JsonObject> normalized = normalizeBomboProfile(el.getAsJsonObject());
						if (normalized.isPresent()) return normalized;
					}
				}
			} catch (Throwable t) {
				me.bombo.bomboaddons.util.ApiHistory.http("GET", url, 0, System.currentTimeMillis() - started);
			}
		}

		// 3. Fallback: official Hypixel /v2/skyblock/profiles if API key is configured
		String hypixelKey = me.bombo.bomboaddons.features.auth.BomboApiKeyManager.getApiKey();
		if (!hypixelKey.isEmpty() && undashedUuid != null && !undashedUuid.isBlank()) {
			String url = "https://api.hypixel.net/v2/skyblock/profiles?uuid=" + undashedUuid;
			long started = System.currentTimeMillis();
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create(url))
					.header("API-Key", hypixelKey)
					.header("User-Agent", "BomboAddons/" + me.bombo.bomboaddons.Constants.myVersion())
					.timeout(TIMEOUT)
					.GET()
					.build();
				HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				me.bombo.bomboaddons.util.ApiHistory.http("GET", url, resp.statusCode(), System.currentTimeMillis() - started);
				if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
					JsonElement el = JsonParser.parseString(resp.body());
					if (el.isJsonObject()) {
						return Optional.of(el.getAsJsonObject());
					}
				}
			} catch (Throwable t) {
				me.bombo.bomboaddons.util.ApiHistory.http("GET", url, 0, System.currentTimeMillis() - started);
			}
		}

		return Optional.empty();
	}

	private static Optional<JsonObject> normalizeBomboProfile(JsonObject json) {
		if (json.has("profiles") && json.get("profiles").isJsonArray()) {
			json.addProperty("success", true);
			return Optional.of(json);
		}
		if (json.has("raw_profile") && json.get("raw_profile").isJsonObject()) {
			JsonObject root = new JsonObject();
			root.addProperty("success", true);
			JsonArray arr = new JsonArray();
			JsonObject raw = json.getAsJsonObject("raw_profile");
			if (json.has("profile") && !raw.has("cute_name")) {
				raw.addProperty("cute_name", json.get("profile").getAsString());
			}
			arr.add(raw);
			root.add("profiles", arr);
			return Optional.of(root);
		}
		if (json.has("members") && json.get("members").isJsonObject()) {
			JsonObject root = new JsonObject();
			root.addProperty("success", true);
			JsonArray arr = new JsonArray();
			arr.add(json);
			root.add("profiles", arr);
			return Optional.of(root);
		}
		return Optional.empty();
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockMuseum(UUID uuid) {
		return skyblockMuseum(uuid, null);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockMuseum(UUID uuid, String profileId) {
		String id = undashed(uuid);
		String workerUrl = WORKER_BASE + "/hypixel/skyblock/museum/" + id;
		if (profileId != null && !profileId.isBlank()) {
			String profile = profileId.trim().replace("-", "").toLowerCase(Locale.ROOT);
			String encoded = URLEncoder.encode(profile, StandardCharsets.UTF_8);
			workerUrl += "?profile=" + encoded;
		}
		String finalWorkerUrl = workerUrl;
		return CompletableFuture.supplyAsync(
			() -> fetchVyrivApi(finalWorkerUrl, "skyblock/museum"),
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockAuction(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(
			() -> fetchVyrivApi(
				WORKER_BASE + "/hypixel/skyblock/auction/" + id,
				"skyblock/auction"
			),
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockGarden(String profileId) {
		if (profileId == null || profileId.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String id = profileId.trim().replace("-", "").toLowerCase(Locale.ROOT);
		String encoded = URLEncoder.encode(id, StandardCharsets.UTF_8);
		return CompletableFuture.supplyAsync(
			() -> {
				Optional<JsonObject> root = fetchVyrivApi(
					WORKER_BASE + "/hypixel/skyblock/garden/" + encoded,
					"skyblock/garden"
				);
				if (root.isEmpty()) {
					return Optional.empty();
				}
				JsonObject body = root.get();
				if (body.has("garden") && body.get("garden").isJsonObject()) {
					return Optional.of(body.getAsJsonObject("garden"));
				}
				return Optional.of(body);
			},
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> player(UUID uuid) {
		String id = undashed(uuid);
		JsonObject cached = PLAYER_CACHE.get(id);
		if (cached != null) {
			return CompletableFuture.completedFuture(Optional.of(cached));
		}
		return CompletableFuture.supplyAsync(
			() -> {
				Optional<JsonObject> root = fetchVyrivApi(
					WORKER_BASE + "/hypixel/player/" + id,
					"player"
				);
				if (root.isEmpty()) {
					return Optional.empty();
				}
				JsonObject body = root.get();
				JsonObject player = body.has("player") && body.get("player").isJsonObject()
					? body.getAsJsonObject("player")
					: body;
				PLAYER_CACHE.put(id, player);
				return Optional.of(player);
			},
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> guild(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(
			() -> fetchVyrivApi(
				WORKER_BASE + "/hypixel/guild/" + id,
				"guild"
			),
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockElection() {
		return CompletableFuture.supplyAsync(() -> {
			waitForSlot();
			return getJson("https://api.hypixel.net/v2/resources/skyblock/election");
		}, EXECUTOR);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockBingoResources() {
		return CompletableFuture.supplyAsync(() -> {
			waitForSlot();
			return getJson("https://api.hypixel.net/v2/resources/skyblock/bingo");
		}, EXECUTOR);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockBingo(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(
			() -> fetchVyrivApi(
				WORKER_BASE + "/hypixel/skyblock/bingo/" + id,
				"skyblock/bingo"
			),
			EXECUTOR
		);
	}

	private static Optional<JsonObject> fetchVyrivApi(String workerUrl, String routeName) {
		waitForSlot();
		Optional<JsonObject> viaWorker = getJson(workerUrl);
		if (viaWorker.isPresent()) {
			return viaWorker;
		}
		BetterPV.LOGGER.warn("Vyriv Hypixel API unavailable for {}", routeName);
		return Optional.empty();
	}

	private static Optional<JsonObject> getJson(String url) {
		return getJson(url, true);
	}

	private static Optional<JsonObject> getJson(String url, boolean allowReauth) {
		long started = System.currentTimeMillis();
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", "BomboAddons/" + me.bombo.bomboaddons.Constants.myVersion())
				.timeout(TIMEOUT)
				.GET();
			boolean needsProxyAuth = url != null && url.startsWith(WORKER_BASE) && url.contains("/hypixel/");
			if (needsProxyAuth && !BetterPvSessionAuth.applyAuthHeaders(builder)) {
				BetterPV.LOGGER.warn(
					"Hypixel GET {} skipped: {}",
					url,
					BetterPvSessionAuth.userFacingFailure().orElse("missing BetterPV credentials")
				);
				BetterPvSessionAuth.notifyPlayerIfNeeded();
				return Optional.empty();
			}
			HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			me.bombo.bomboaddons.util.ApiHistory.http("GET", url, response.statusCode(), System.currentTimeMillis() - started);
			if (response.statusCode() == 401 && needsProxyAuth) {
				BetterPvSessionAuth.invalidate();
				if (allowReauth) {
					return getJson(url, false);
				}
				BetterPV.LOGGER.warn("Hypixel GET {} unauthorized after re-auth", url);
				return Optional.empty();
			}
			if (response.statusCode() == 503 && needsProxyAuth) {
				BetterPV.LOGGER.warn("Hypixel GET {} session auth unavailable (503)", url);
				return Optional.empty();
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
				BetterPV.LOGGER.warn("Hypixel GET {} failed status={}", url, response.statusCode());
				return Optional.empty();
			}
			JsonElement element = JsonParser.parseString(response.body());
			if (!element.isJsonObject()) {
				return Optional.empty();
			}
			JsonObject root = element.getAsJsonObject();
			if (root.has("success") && root.get("success").isJsonPrimitive() && !root.get("success").getAsBoolean()) {
				return Optional.empty();
			}
			return Optional.of(root);
		} catch (IOException | InterruptedException exception) {
			me.bombo.bomboaddons.util.ApiHistory.http("GET", url, 0, System.currentTimeMillis() - started);
			BetterPV.LOGGER.warn("Hypixel GET {} failed", url, exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		}
	}

	private static synchronized void waitForSlot() {
		long now = System.currentTimeMillis();
		long wait = nextRequestAtMillis - now;
		if (wait > 0L) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}
		nextRequestAtMillis = System.currentTimeMillis() + SPACING_MS;
	}

	public static String undashed(UUID uuid) {
		return uuid == null ? "" : uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	public static UUID parseUndashedUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String hex = raw.trim().replace("-", "").toLowerCase(Locale.ROOT);
		if (hex.length() != 32) {
			return null;
		}
		try {
			String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
				+ "-" + hex.substring(16, 20) + "-" + hex.substring(20);
			return UUID.fromString(dashed);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	
	public static CompletableFuture<Optional<JsonObject>> status(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(() -> {
			waitForSlot();
			return getJson(WORKER_BASE + "/hypixel/status/" + id);
		}, EXECUTOR);
	}


	private static final Duration NAME_HISTORY_TIMEOUT = Duration.ofSeconds(8);
	private static final String BROWSER_UA =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

	/**
	 * Public username history (oldest to newest).
	 * Parallel Laby + Crafty + NameMC; Ashcon only if all miss.
	 */
	public static CompletableFuture<Optional<com.google.gson.JsonArray>> usernameHistory(UUID uuid) {
		String id = undashed(uuid);
		String dashed = uuid == null ? "" : uuid.toString();
		String labyId = dashed.isBlank() ? id : dashed;
		CompletableFuture<Optional<com.google.gson.JsonArray>> laby =
			CompletableFuture.supplyAsync(() -> fetchLabyNameHistory(labyId), EXECUTOR);
		CompletableFuture<Optional<com.google.gson.JsonArray>> crafty =
			CompletableFuture.supplyAsync(() -> fetchCraftyNameHistory(labyId), EXECUTOR);
		CompletableFuture<Optional<com.google.gson.JsonArray>> namemc =
			CompletableFuture.supplyAsync(() -> fetchNameMcNameHistory(labyId), EXECUTOR);
		return CompletableFuture.allOf(laby, crafty, namemc).thenApply(ignored -> {
			com.google.gson.JsonArray merged = new com.google.gson.JsonArray();
			mergeNameHistory(merged, laby.join());
			mergeNameHistory(merged, crafty.join());
			mergeNameHistory(merged, namemc.join());
			if (merged.size() == 0) {
				mergeNameHistory(merged, fetchAshconNameHistory(id));
			}
			if (merged.size() == 0) {
				return Optional.empty();
			}
			com.google.gson.JsonArray cleaned = collapseConsecutiveNameDupes(sortNameHistoryOldestFirst(merged));
			BetterPV.LOGGER.info("Username history for {}: {} name(s)", id, cleaned.size());
			return Optional.of(cleaned);
		});
	}

	private static Optional<com.google.gson.JsonArray> fetchLabyNameHistory(String uuidPath) {
		if (uuidPath == null || uuidPath.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create("https://laby.net/api/v3/user/" + uuidPath + "/names");
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(NAME_HISTORY_TIMEOUT)
				.header("User-Agent", BROWSER_UA)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()) {
				return Optional.empty();
			}
			JsonElement element = JsonParser.parseString(response.body());
			if (element.isJsonArray()) {
				return Optional.of(normalizeNameHistoryArray(element.getAsJsonArray()));
			}
			if (element.isJsonObject()) {
				JsonObject root = element.getAsJsonObject();
				if (root.has("username_history") && root.get("username_history").isJsonArray()) {
					return Optional.of(normalizeNameHistoryArray(root.getAsJsonArray("username_history")));
				}
				if (root.has("name_history") && root.get("name_history").isJsonArray()) {
					return Optional.of(normalizeNameHistoryArray(root.getAsJsonArray("name_history")));
				}
			}
			return Optional.empty();
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Laby name history failed for {}", uuidPath, exception);
			return Optional.empty();
		}
	}

	private static Optional<com.google.gson.JsonArray> fetchCraftyNameHistory(String uuidPath) {
		if (uuidPath == null || uuidPath.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create("https://api.crafty.gg/api/v2/players/" + uuidPath);
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(NAME_HISTORY_TIMEOUT)
				.header("User-Agent", BROWSER_UA)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()
				|| response.body().trim().startsWith("<")) {
				return Optional.empty();
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject data = root.has("data") && root.get("data").isJsonObject()
				? root.getAsJsonObject("data") : root;
			if (!data.has("usernames") || !data.get("usernames").isJsonArray()) {
				return Optional.empty();
			}
			com.google.gson.JsonArray raw = data.getAsJsonArray("usernames");
			com.google.gson.JsonArray out = new com.google.gson.JsonArray();
			for (JsonElement el : raw) {
				if (el == null || !el.isJsonObject()) {
					continue;
				}
				JsonObject obj = el.getAsJsonObject();
				if (obj.has("hidden") && obj.get("hidden").isJsonPrimitive() && obj.get("hidden").getAsBoolean()) {
					continue;
				}
				String name = obj.has("username") && obj.get("username").isJsonPrimitive()
					? obj.get("username").getAsString() : "";
				if (name == null || name.isBlank()) {
					continue;
				}
				JsonObject normalized = new JsonObject();
				normalized.addProperty("username", name);
				if (obj.has("changed_at") && !obj.get("changed_at").isJsonNull()
					&& obj.get("changed_at").isJsonPrimitive()) {
					normalized.addProperty("changed_at", obj.get("changed_at").getAsString());
				}
				out.add(normalized);
			}
			return out.size() == 0 ? Optional.empty() : Optional.of(out);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Crafty name history failed for {}", uuidPath, exception);
			return Optional.empty();
		}
	}

	/**
	 * NameMC has no documented JSON name-history API - only the public profile HTML.
	 * Fetched once on user click; redacted rows ({@code &mdash;}) are skipped.
	 */
	private static Optional<com.google.gson.JsonArray> fetchNameMcNameHistory(String uuidPath) {
		if (uuidPath == null || uuidPath.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create("https://namemc.com/profile/" + uuidPath);
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(NAME_HISTORY_TIMEOUT)
				.header("User-Agent", BROWSER_UA)
				.header("Accept", "text/html,application/xhtml+xml")
				.header("Accept-Language", "en-US,en;q=0.9")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()) {
				return Optional.empty();
			}
			return parseNameMcHistoryHtml(response.body());
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("NameMC name history failed for {}", uuidPath, exception);
			return Optional.empty();
		}
	}

	private static Optional<com.google.gson.JsonArray> parseNameMcHistoryHtml(String html) {
		if (html == null || html.isBlank()) {
			return Optional.empty();
		}
		int start = html.indexOf("<strong>Name History</strong>");
		if (start < 0) {
			return Optional.empty();
		}
		int end = html.indexOf("</table>", start);
		if (end < 0) {
			end = Math.min(html.length(), start + 80_000);
		}
		String chunk = html.substring(start, end);
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		// Desktop rows only (skip mobile duplicate rows).
		java.util.regex.Pattern row = java.util.regex.Pattern.compile(
			"<tr>(?!\\s*class=\"d-lg-none)[\\s\\S]*?</tr>",
			java.util.regex.Pattern.CASE_INSENSITIVE
		);
		java.util.regex.Pattern nameLink = java.util.regex.Pattern.compile(
			"href=\"/search\\?q=([^\"]+)\"[^>]*>([^<]+)</a>"
		);
		java.util.regex.Pattern timePat = java.util.regex.Pattern.compile(
			"datetime=\"([^\"]+)\""
		);
		java.util.regex.Matcher rows = row.matcher(chunk);
		while (rows.find()) {
			String tr = rows.group();
			if (!tr.contains("fw-bold")) {
				continue;
			}
			java.util.regex.Matcher nm = nameLink.matcher(tr);
			if (!nm.find()) {
				continue; // redacted (&mdash;) or empty
			}
			String name = nm.group(2).trim();
			if (name.isBlank() || "-".equals(name) || "&mdash;".equalsIgnoreCase(name)) {
				continue;
			}
			JsonObject normalized = new JsonObject();
			normalized.addProperty("username", name);
			java.util.regex.Matcher tm = timePat.matcher(tr);
			if (tm.find()) {
				normalized.addProperty("changed_at", tm.group(1));
			}
			out.add(normalized);
		}
		return out.size() == 0 ? Optional.empty() : Optional.of(out);
	}

	private static void mergeNameHistory(com.google.gson.JsonArray into, Optional<com.google.gson.JsonArray> source) {
		if (into == null || source == null || source.isEmpty()) {
			return;
		}
		java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
		for (JsonElement el : into) {
			if (el != null && el.isJsonObject()) {
				String key = nameHistoryKey(el.getAsJsonObject());
				if (!key.isBlank()) {
					seen.add(key);
				}
			}
		}
		for (JsonElement el : source.get()) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject obj = el.getAsJsonObject();
			String key = nameHistoryKey(obj);
			if (key.isBlank() || seen.contains(key)) {
				continue;
			}
			seen.add(key);
			into.add(obj);
		}
	}

	private static String nameHistoryKey(JsonObject obj) {
		if (obj == null) {
			return "";
		}
		String name = "";
		if (obj.has("username") && obj.get("username").isJsonPrimitive()) {
			name = obj.get("username").getAsString();
		}
		if (name == null || name.isBlank()) {
			return "";
		}
		String changed = "";
		if (obj.has("changed_at") && obj.get("changed_at").isJsonPrimitive()) {
			changed = obj.get("changed_at").getAsString();
		}
		String day = "";
		if (changed != null && changed.length() >= 10) {
			day = changed.substring(0, 10);
		}
		return name.toLowerCase(Locale.ROOT) + "|" + day;
	}

	private static com.google.gson.JsonArray sortNameHistoryOldestFirst(com.google.gson.JsonArray raw) {
		java.util.ArrayList<JsonObject> list = new java.util.ArrayList<>();
		for (JsonElement el : raw) {
			if (el != null && el.isJsonObject()) {
				list.add(el.getAsJsonObject());
			}
		}
		list.sort((a, b) -> {
			String ca = a.has("changed_at") && a.get("changed_at").isJsonPrimitive()
				? a.get("changed_at").getAsString() : "";
			String cb = b.has("changed_at") && b.get("changed_at").isJsonPrimitive()
				? b.get("changed_at").getAsString() : "";
			if (ca.isBlank() && cb.isBlank()) {
				return 0;
			}
			if (ca.isBlank()) {
				return -1;
			}
			if (cb.isBlank()) {
				return 1;
			}
			return ca.compareTo(cb);
		});
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		for (JsonObject obj : list) {
			out.add(obj);
		}
		return out;
	}

	private static com.google.gson.JsonArray collapseConsecutiveNameDupes(com.google.gson.JsonArray sorted) {
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		String prev = null;
		for (JsonElement el : sorted) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject obj = el.getAsJsonObject();
			String name = obj.has("username") && obj.get("username").isJsonPrimitive()
				? obj.get("username").getAsString() : "";
			if (name == null || name.isBlank()) {
				continue;
			}
			String key = name.toLowerCase(Locale.ROOT);
			if (prev != null && prev.equals(key)) {
				continue;
			}
			prev = key;
			out.add(obj);
		}
		return out;
	}

	private static Optional<com.google.gson.JsonArray> fetchAshconNameHistory(String undashedUuid) {
		try {
			URI uri = URI.create("https://api.ashcon.app/mojang/v2/user/" + undashedUuid);
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(NAME_HISTORY_TIMEOUT)
				.header("User-Agent", BROWSER_UA)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()) {
				return Optional.empty();
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (root.has("username_history") && root.get("username_history").isJsonArray()) {
				return Optional.of(normalizeNameHistoryArray(root.getAsJsonArray("username_history")));
			}
			return Optional.empty();
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Ashcon name history failed for {}", undashedUuid, exception);
			return Optional.empty();
		}
	}

	private static com.google.gson.JsonArray normalizeNameHistoryArray(com.google.gson.JsonArray raw) {
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		if (raw == null) {
			return out;
		}
		for (JsonElement el : raw) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject obj = el.getAsJsonObject();
			String name = "";
			if (obj.has("username") && obj.get("username").isJsonPrimitive()) {
				name = obj.get("username").getAsString();
			} else if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
				name = obj.get("name").getAsString();
			}
			if (name == null || name.isBlank()) {
				continue;
			}
			String changed = "";
			if (obj.has("changed_at") && obj.get("changed_at").isJsonPrimitive()
				&& !obj.get("changed_at").isJsonNull()) {
				try {
					changed = obj.get("changed_at").getAsString();
				} catch (Exception ignored) {
					changed = "";
				}
			} else if (obj.has("changedAt") && obj.get("changedAt").isJsonPrimitive()) {
				changed = obj.get("changedAt").getAsString();
			}
			JsonObject normalized = new JsonObject();
			normalized.addProperty("username", name);
			if (changed != null && !changed.isBlank()) {
				normalized.addProperty("changed_at", changed);
			}
			out.add(normalized);
		}
		return out;
	}

	public record UuidName(UUID uuid, String name) {
	}
}
