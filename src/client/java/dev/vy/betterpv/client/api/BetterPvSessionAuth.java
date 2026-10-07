package dev.vy.betterpv.client.api;

import java.net.http.HttpRequest;
import java.util.Optional;

/**
 * Session auth adapter.
 * Decoupled from third-party api.vyriv.dev; requests are now routed
 * through BomboAddons's own API infrastructure and official Hypixel endpoints.
 */
public final class BetterPvSessionAuth {

	public enum Failure {
		NONE(""),
		MISSING_SESSION(""),
		OFFLINE_SESSION(""),
		JOIN_SERVER_FAILED(""),
		SERVER_AUTH_UNAVAILABLE(""),
		AUTH_REJECTED(""),
		AUTH_HTTP(""),
		MISSING_JWT("");

		private final String userMessage;

		Failure(String userMessage) {
			this.userMessage = userMessage;
		}

		public String userMessage() {
			return userMessage;
		}
	}

	private BetterPvSessionAuth() {
	}

	public static void invalidate() {
	}

	public static Failure lastFailure() {
		return Failure.NONE;
	}

	public static Optional<String> userFacingFailure() {
		return Optional.empty();
	}

	/**
	 * Attaches available Hypixel or Bombo API keys to request builder.
	 */
	public static boolean applyAuthHeaders(HttpRequest.Builder builder) {
		String hypixelKey = me.bombo.bomboaddons.features.auth.BomboApiKeyManager.getApiKey();
		if (hypixelKey != null && !hypixelKey.isBlank()) {
			builder.header("API-Key", hypixelKey);
		}
		me.bombo.bomboaddons.util.BomboApiUrl.attachApiKey(builder);
		return true;
	}

	public static void notifyPlayerIfNeeded() {
	}

	public static void prefetchAsync() {
	}

	public static Optional<String> ensureBearerToken() {
		return Optional.empty();
	}
}
