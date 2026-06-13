package me.bombo.bomboaddons_final.eggfinder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.bombo.bomboaddons_final.Bomboaddons;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.world.entity.player.ProfileKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EggAuth {
    private static final Logger LOGGER = LoggerFactory.getLogger("bomboaddons-eggauth");
    private static final String AUTH_URL = "https://api.azureaaron.net/authenticate";
    private static final String ALGORITHM = "SHA256withRSA";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new Gson();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "EggAuth-Scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile String token = null;
    private static volatile boolean authenticating = false;

    public static String getToken() {
        if (token == null && !authenticating) {
            updateToken();
        }
        return token;
    }

    public static void forceUpdateToken() {
        authenticating = false;
        token = null;
        updateToken();
    }

    public static void updateToken() {
        if (authenticating) return;
        authenticating = true;

        Minecraft client = Minecraft.getInstance();
        if (client.getUser() == null || client.getUser().getProfileId() == null) {
            LOGGER.error("Cannot authenticate: Player user or ProfileId is null.");
            authenticating = false;
            return;
        }

        ProfileKeyPairManager profileKeys = client.getProfileKeyPairManager();
        if (profileKeys == null) {
            LOGGER.error("Cannot authenticate: ProfileKeyPairManager is null.");
            authenticating = false;
            return;
        }

        LOGGER.info("Preparing key pair for API authentication...");
        profileKeys.prepareKeyPair().thenAcceptAsync(playerKeypairOpt -> {
            try {
                boolean expired = playerKeypairOpt.map(keyPair -> keyPair.publicKey().data().hasExpired()).orElse(false);
                if (playerKeypairOpt.isEmpty() || expired) {
                    LOGGER.error("Failed to prepare key pair: Keypair option is empty or expired. Retrying in 5 minutes.");
                    SCHEDULER.schedule(EggAuth::updateToken, 5, TimeUnit.MINUTES);
                    authenticating = false;
                    return;
                }

                ProfileKeyPair playerKeyPair = playerKeypairOpt.get();
                String publicKey = Base64.getMimeEncoder().encodeToString(playerKeyPair.publicKey().data().key().getEncoded());
                byte[] publicKeySignature = playerKeyPair.publicKey().data().keySignature();
                long expiresAt = playerKeyPair.publicKey().data().expiresAt().toEpochMilli();

                JsonObject keyPairJson = new JsonObject();
                keyPairJson.addProperty("uuid", client.getUser().getProfileId().toString());
                keyPairJson.addProperty("publicKey", publicKey);
                keyPairJson.addProperty("publicKeySignature", Base64.getEncoder().encodeToString(publicKeySignature));
                keyPairJson.addProperty("expiresAt", expiresAt);

                SignedData signedData = getRandomSignedData(playerKeyPair.privateKey());
                if (signedData == null) {
                    LOGGER.error("Failed to sign random data. Retrying in 5 minutes.");
                    SCHEDULER.schedule(EggAuth::updateToken, 5, TimeUnit.MINUTES);
                    authenticating = false;
                    return;
                }

                JsonObject signedDataJson = new JsonObject();
                signedDataJson.addProperty("original", Base64.getEncoder().encodeToString(signedData.original));
                signedDataJson.addProperty("signed", Base64.getEncoder().encodeToString(signedData.signed));

                JsonObject root = new JsonObject();
                root.add("keyPair", keyPairJson);
                root.add("signedData", signedDataJson);
                root.addProperty("mod", "skyblocker");
                root.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name());
                root.addProperty("modVersion", "1.7.1");

                String requestJson = GSON.toJson(root);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(AUTH_URL))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Skyblocker/1.7.1 (" + SharedConstants.getCurrentVersion().name() + ")")
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            try {
                                if (response.statusCode() == 200) {
                                    JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
                                    token = responseJson.get("token").getAsString();
                                    long issuedAt = responseJson.get("issuedAt").getAsLong();
                                    long expires = responseJson.get("expiresAt").getAsLong();
                                    LOGGER.info("Successfully refreshed Skyblocker API Token.");

                                    // Refresh token 5 minutes before it expires
                                    long lifetimeSec = (expires - issuedAt) / 1000L;
                                    long delaySec = Math.max(60L, lifetimeSec - 300L);
                                    SCHEDULER.schedule(EggAuth::updateToken, delaySec, TimeUnit.SECONDS);
                                    
                                    // Notify WebSocket if it needs to connect or reconnect
                                    EggWebSocket.onTokenRefreshed();
                                } else {
                                    LOGGER.error("API Auth responded with HTTP status " + response.statusCode() + ": " + response.body());
                                    LOGGER.error("Retrying API Auth in 5 minutes.");
                                    SCHEDULER.schedule(EggAuth::updateToken, 5, TimeUnit.MINUTES);
                                }
                            } catch (Exception ex) {
                                LOGGER.error("Failed to parse API Auth response: " + ex.getMessage(), ex);
                                SCHEDULER.schedule(EggAuth::updateToken, 5, TimeUnit.MINUTES);
                            } finally {
                                authenticating = false;
                            }
                        }).exceptionally(t -> {
                            LOGGER.error("API Auth HTTP request failed: " + t.getMessage(), t);
                            SCHEDULER.schedule(EggAuth::updateToken, 5, TimeUnit.MINUTES);
                            authenticating = false;
                            return null;
                        });

            } catch (Exception e) {
                LOGGER.error("Unexpected error in prepareKeyPair: " + e.getMessage(), e);
                SCHEDULER.schedule(EggAuth::updateToken, 5, TimeUnit.MINUTES);
                authenticating = false;
            }
        }).exceptionally(t -> {
            LOGGER.error("Failed prepareKeyPair: " + t.getMessage(), t);
            SCHEDULER.schedule(EggAuth::updateToken, 5, TimeUnit.MINUTES);
            authenticating = false;
            return null;
        });
    }

    private static SignedData getRandomSignedData(PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            UUID uuid = UUID.randomUUID();
            ByteBuffer buf = ByteBuffer.allocate(16)
                    .putLong(uuid.getMostSignificantBits())
                    .putLong(uuid.getLeastSignificantBits());

            signature.initSign(privateKey);
            signature.update(buf.array());

            byte[] signedData = signature.sign();
            return new SignedData(buf.array(), signedData);
        } catch (Exception e) {
            LOGGER.error("Failed to sign random data: " + e.getMessage(), e);
            return null;
        }
    }

    private static class SignedData {
        final byte[] original;
        final byte[] signed;

        SignedData(byte[] original, byte[] signed) {
            this.original = original;
            this.signed = signed;
        }
    }
}
