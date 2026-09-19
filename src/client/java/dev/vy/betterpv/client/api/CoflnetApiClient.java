package dev.vy.betterpv.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.BetterPV;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SkyCofl public auction history ({@code https://sky.coflnet.com/api}).
 * No API key for player auctions/bids pages. Respect ~1 req/s pacing.
 */
public final class CoflnetApiClient {
	private static final String BASE = "https://sky.coflnet.com/api";
	private static final Duration TIMEOUT = Duration.ofSeconds(12);
	private static final long SPACING_MS = 350L;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "BetterPV-Coflnet");
		t.setDaemon(true);
		return t;
	});
	private static long nextRequestAtMillis;

	private CoflnetApiClient() {
	}

	/** Auctions the player created. */
	public static CompletableFuture<Optional<JsonArray>> playerAuctions(UUID uuid, int page) {
		String id = HypixelApiClient.undashed(uuid);
		int p = Math.max(0, page);
		return CompletableFuture.supplyAsync(
			() -> getArray(BASE + "/player/" + id + "/auctions?page=" + p),
			EXECUTOR
		);
	}

	/** Bids the player made. */
	public static CompletableFuture<Optional<JsonArray>> playerBids(UUID uuid, int page) {
		String id = HypixelApiClient.undashed(uuid);
		int p = Math.max(0, page);
		return CompletableFuture.supplyAsync(
			() -> getArray(BASE + "/player/" + id + "/bids?page=" + p),
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> auction(String auctionId) {
		if (auctionId == null || auctionId.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String id = auctionId.replace("-", "").toLowerCase();
		return CompletableFuture.supplyAsync(
			() -> getObject(BASE + "/auction/" + id),
			EXECUTOR
		);
	}

	private static Optional<JsonArray> getArray(String url) {
		Optional<JsonElement> root = getJson(url);
		if (root.isEmpty()) {
			return Optional.empty();
		}
		JsonElement el = root.get();
		if (el.isJsonArray()) {
			return Optional.of(el.getAsJsonArray());
		}
		return Optional.empty();
	}

	private static Optional<JsonObject> getObject(String url) {
		Optional<JsonElement> root = getJson(url);
		if (root.isEmpty() || !root.get().isJsonObject()) {
			return Optional.empty();
		}
		return Optional.of(root.get().getAsJsonObject());
	}

	private static Optional<JsonElement> getJson(String url) {
		waitForSlot();
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()) {
				BetterPV.LOGGER.warn("Coflnet {} → HTTP {}", url, response.statusCode());
				return Optional.empty();
			}
			return Optional.of(JsonParser.parseString(response.body()));
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Coflnet request failed: {}", url, exception);
			return Optional.empty();
		}
	}

	private static synchronized void waitForSlot() {
		long now = System.currentTimeMillis();
		long wait = nextRequestAtMillis - now;
		if (wait > 0L) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			now = System.currentTimeMillis();
		}
		nextRequestAtMillis = now + SPACING_MS;
	}

	public static List<JsonObject> objects(JsonArray array) {
		List<JsonObject> out = new ArrayList<>();
		if (array == null) {
			return out;
		}
		for (JsonElement el : array) {
			if (el != null && el.isJsonObject()) {
				out.add(el.getAsJsonObject());
			}
		}
		return out;
	}
}
