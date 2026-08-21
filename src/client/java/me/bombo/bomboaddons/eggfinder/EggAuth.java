package me.bombo.bomboaddons.eggfinder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.bombo.bomboaddons.AlphaTrackerHud;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.world.entity.player.ProfileKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EggAuth {
   private static final Logger LOGGER = LoggerFactory.getLogger("bomboaddons-eggauth");
   private static final String AUTH_URL = "https://hysky.de/api/aaron/authenticate";
   private static final String ALGORITHM = "SHA256withRSA";
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(10L)).build();
   private static final Gson GSON = new Gson();
   private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor((r) -> {
      Thread thread = new Thread(r, "EggAuth-Scheduler");
      thread.setDaemon(true);
      return thread;
   });
   private static volatile String token = null;
   private static volatile boolean authenticating = false;

   public static String getToken() {
      if (token == null && FabricLoader.getInstance().isModLoaded("skyblocker")) {
         try {
            Class<?> apiAuthClass = Class.forName("de.hysky.skyblocker.utils.ApiAuthentication");
            Method m = apiAuthClass.getMethod("getToken");
            Object tok = m.invoke(null);
            if (tok instanceof String && !((String) tok).isEmpty()) {
               token = (String) tok;
               return token;
            }
         } catch (Throwable ignored) {}
      }

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
      if (!AlphaTrackerHud.isHoppityActive()) {
         LOGGER.info("Hoppity's Hunt is inactive. Skipping EggAuth API authentication.");
         authenticating = false;
      } else if (!authenticating) {
         authenticating = true;
         Minecraft client = Minecraft.getInstance();
         if (FabricLoader.getInstance().isModLoaded("skyblocker")) {
            try {
               Class<?> apiAuthClass = Class.forName("de.hysky.skyblocker.utils.ApiAuthentication");
               Method m = apiAuthClass.getMethod("getToken");
               Object tok = m.invoke(null);
               if (tok instanceof String && !((String) tok).isEmpty()) {
                  token = (String) tok;
                  LOGGER.info("Successfully fetched API token directly from loaded Skyblocker mod.");
                  authenticating = false;
                  EggWebSocket.onTokenRefreshed();
                  return;
               }
            } catch (Throwable ignored) {}
         }

         if (client != null && client.getUser() != null) {
            ProfileKeyPairManager profileKeys = client.getProfileKeyPairManager();
            if (profileKeys == null) {
               LOGGER.error("Cannot authenticate: ProfileKeyPairManager is null.");
               authenticating = false;
            } else {
               LOGGER.info("Preparing key pair for API authentication...");
               profileKeys.prepareKeyPair().thenAcceptAsync((playerKeypairOpt) -> {
                  try {
                     boolean expired = (Boolean)playerKeypairOpt.map((keyPair) -> keyPair.publicKey().data().hasExpired()).orElse(false);
                     if (playerKeypairOpt.isEmpty() || expired) {
                        LOGGER.error("Failed to prepare key pair: Keypair option is empty or expired. Retrying in 5 minutes.");
                        SCHEDULER.schedule(EggAuth::updateToken, 5L, TimeUnit.MINUTES);
                        authenticating = false;
                        return;
                     }

                     ProfileKeyPair playerKeyPair = (ProfileKeyPair)playerKeypairOpt.get();
                     String publicKey = Base64.getEncoder().encodeToString(playerKeyPair.publicKey().data().key().getEncoded());
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
                        SCHEDULER.schedule(EggAuth::updateToken, 5L, TimeUnit.MINUTES);
                        authenticating = false;
                        return;
                     }

                     JsonObject signedDataJson = new JsonObject();
                     signedDataJson.addProperty("original", Base64.getEncoder().encodeToString(signedData.original));
                     signedDataJson.addProperty("signed", Base64.getEncoder().encodeToString(signedData.signed));
                     JsonObject root = new JsonObject();
                     root.add("keyPairInfo", keyPairJson);
                     root.add("signedData", signedDataJson);
                     root.addProperty("mod", "skyblocker");
                     root.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name());
                     root.addProperty("modVersion", "6.9.1");
                     String requestJson = GSON.toJson(root);
                     LOGGER.info("[EggAuth-Debug] Sending auth request for UUID: " + client.getUser().getProfileId() + " (User: " + client.getUser().getName() + "), KeyExpiresAt: " + expiresAt);
                     HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://hysky.de/api/aaron/authenticate")).header("Content-Type", "application/json").header("Accept", "application/json").header("User-Agent", "Skyblocker/6.9.1 (" + SharedConstants.getCurrentVersion().name() + ")").POST(BodyPublishers.ofString(requestJson)).build();
                     HTTP_CLIENT.sendAsync(request, BodyHandlers.ofString()).thenAccept((response) -> {
                        try {
                           if (response.statusCode() == 200) {
                              JsonObject responseJson = (JsonObject)GSON.fromJson((String)response.body(), JsonObject.class);
                              token = responseJson.get("token").getAsString();
                              long issuedAt = responseJson.get("issuedAt").getAsLong();
                              long expires = responseJson.get("expiresAt").getAsLong();
                              LOGGER.info("Successfully refreshed Skyblocker API Token.");
                              long lifetimeSec = (expires - issuedAt) / 1000L;
                              long delaySec = Math.max(60L, lifetimeSec - 300L);
                              SCHEDULER.schedule(EggAuth::updateToken, delaySec, TimeUnit.SECONDS);
                              EggWebSocket.onTokenRefreshed();
                           } else {
                              Logger var10000 = LOGGER;
                              int var10001 = response.statusCode();
                              var10000.error("[EggAuth-Debug] API Auth responded with HTTP " + var10001 + ": " + (String)response.body());
                              LOGGER.error("[EggAuth-Debug] Payload sent: " + requestJson);
                              LOGGER.error("Retrying API Auth in 5 minutes.");
                              SCHEDULER.schedule(EggAuth::updateToken, 5L, TimeUnit.MINUTES);
                           }
                        } catch (Exception ex) {
                           LOGGER.error("Failed to parse API Auth response: " + ex.getMessage(), ex);
                           SCHEDULER.schedule(EggAuth::updateToken, 5L, TimeUnit.MINUTES);
                        } finally {
                           authenticating = false;
                        }

                     }).exceptionally((t) -> {
                        LOGGER.error("API Auth HTTP request failed: " + t.getMessage(), t);
                        SCHEDULER.schedule(EggAuth::updateToken, 5L, TimeUnit.MINUTES);
                        authenticating = false;
                        return null;
                     });
                  } catch (Exception e) {
                     LOGGER.error("Unexpected error in prepareKeyPair: " + e.getMessage(), e);
                     SCHEDULER.schedule(EggAuth::updateToken, 5L, TimeUnit.MINUTES);
                     authenticating = false;
                  }

               }).exceptionally((t) -> {
                  LOGGER.error("Failed prepareKeyPair: " + t.getMessage(), t);
                  SCHEDULER.schedule(EggAuth::updateToken, 5L, TimeUnit.MINUTES);
                  authenticating = false;
                  return null;
               });
            }
         } else {
            LOGGER.error("Cannot authenticate: Player user or ProfileId is null.");
            authenticating = false;
         }
      }
   }

   private static SignedData getRandomSignedData(PrivateKey privateKey) {
      try {
         String keyAlgo = privateKey.getAlgorithm();
         String sigAlgo = "EC".equalsIgnoreCase(keyAlgo) || "ECDSA".equalsIgnoreCase(keyAlgo) ? "SHA256withECDSA" : "SHA256withRSA";
         Signature signature = Signature.getInstance(sigAlgo);
         UUID uuid = UUID.randomUUID();
         ByteBuffer buf = ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
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
