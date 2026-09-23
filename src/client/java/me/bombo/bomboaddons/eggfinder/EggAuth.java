package me.bombo.bomboaddons.eggfinder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
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
import me.bombo.bomboaddons.BomboConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.ProfileKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EggAuth {
   private static final Logger LOGGER = LoggerFactory.getLogger("bomboaddons-eggauth");
   private static final String AUTH_URL = "https://hysky.de/api/aaron/authenticate";
   private static final String ALGORITHM = "SHA256withRSA";
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10L)).build();
   private static final Gson GSON = new Gson();
   private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor((r) -> {
      Thread thread = new Thread(r, "EggAuth-Scheduler");
      thread.setDaemon(true);
      return thread;
   });
   private static volatile String token = null;
   private static volatile boolean authenticating = false;

   public static String getToken() {
      if (FabricLoader.getInstance().isModLoaded("skyblocker")) {
         try {
            Class<?> apiAuthClass = Class.forName("de.hysky.skyblocker.utils.ApiAuthentication");
            Field tokenInfoField = apiAuthClass.getDeclaredField("tokenInfo");
            tokenInfoField.setAccessible(true);
            Object tokenInfoObj = tokenInfoField.get(null);
            if (tokenInfoObj != null) {
               Method tokenMethod = tokenInfoObj.getClass().getMethod("token");
               Method expiresMethod = tokenInfoObj.getClass().getMethod("expiresAt");
               String tok = (String) tokenMethod.invoke(tokenInfoObj);
               long expiresAt = (Long) expiresMethod.invoke(tokenInfoObj);
               if (tok != null && !tok.isEmpty() && expiresAt > System.currentTimeMillis()) {
                  token = tok;
                  return token;
               }
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
         authenticating = false;
         return;
      }
      
      // If Skyblocker mod is loaded, we can sync its existing token directly
      if (FabricLoader.getInstance().isModLoaded("skyblocker")) {
         try {
            Class<?> apiAuthClass = Class.forName("de.hysky.skyblocker.utils.ApiAuthentication");
            Field tokenInfoField = apiAuthClass.getDeclaredField("tokenInfo");
            tokenInfoField.setAccessible(true);
            Object tokenInfoObj = tokenInfoField.get(null);
            if (tokenInfoObj != null) {
               Method tokenMethod = tokenInfoObj.getClass().getMethod("token");
               Method expiresMethod = tokenInfoObj.getClass().getMethod("expiresAt");
               String tok = (String) tokenMethod.invoke(tokenInfoObj);
               long expiresAt = (Long) expiresMethod.invoke(tokenInfoObj);
               if (tok != null && !tok.isEmpty() && expiresAt > System.currentTimeMillis()) {
                  token = tok;
                  LOGGER.info("Using active Skyblocker API token directly.");
                  if (BomboConfig.get().eggFinderDebug && Minecraft.getInstance().player != null) {
                     Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§eEggFinder Debug§8] §aUsing active Skyblocker API token directly."));
                  }
                  authenticating = false;
                  EggWebSocket.onTokenRefreshed();
                  return;
               }
            }
         } catch (Throwable ignored) {}
      }

      // No Skyblocker installed to borrow a token from: run the real aaron handshake,
      // byte-for-byte what de.hysky.skyblocker.utils.ApiAuthentication does - fetch the
      // player's Mojang profile key pair, sign random data with the private key, and POST
      // the signed proof to hysky.de. The server validates it against Mojang's key
      // signature, so nothing short of the real flow produces a working token.
      authenticateWithAaron();
      authenticating = false;
   }

   /**
    * Full port of Skyblocker's {@code ApiAuthentication.updateToken}: profile key pair,
    * {@code SHA256withRSA}-signed random data, {@code POST} to the aaron endpoint with a
    * {@code mod} envelope that matches Skyblocker's own (their server checks the mod id,
    * so we must claim to be Skyblocker - the whole point of the handshake spoof).
    */
   private static void authenticateWithAaron() {
      if (authenticating) return;
      authenticating = true;
      SCHEDULER.execute(() -> {
         try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getUser() == null) {
               authenticating = false;
               return;
            }

            ProfileKeyPairManager profileKeys = ((me.bombo.bomboaddons.mixin.MinecraftAccessor) mc).getProfileKeyPairManagerField();
            java.util.concurrent.CompletableFuture<java.util.Optional<ProfileKeyPair>> future = profileKeys.prepareKeyPair();
            java.util.Optional<ProfileKeyPair> opt = future != null ? future.join() : null;

            if (opt == null || opt.isEmpty()) {
               LOGGER.warn("[EggAuth] No profile key pair available (offline account or MC services down).");
               debugChat("§cNo profile key pair - aaron auth impossible (offline account?).");
               authenticating = false;
               return;
            }

            ProfileKeyPair keyPair = opt.get();
            if (keyPair.publicKey().data().hasExpired()) {
               LOGGER.warn("[EggAuth] Profile key pair expired (game open >24h?). Restart the game.");
               debugChat("§cProfile key pair expired - restart the game.");
               authenticating = false;
               return;
            }

            String publicKey = Base64.getMimeEncoder().encodeToString(keyPair.publicKey().data().key().getEncoded());
            byte[] publicKeySignature = keyPair.publicKey().data().keySignature();
            long expiresAt = keyPair.publicKey().data().expiresAt().toEpochMilli();
            java.util.UUID uuid = mc.getUser().getProfileId();

            SignedData signedData = getRandomSignedData(keyPair.privateKey());
            if (signedData == null) {
               authenticating = false;
               return;
            }

            JsonObject keyPairInfo = new JsonObject();
            keyPairInfo.addProperty("uuid", uuid.toString());
            keyPairInfo.addProperty("publicKey", publicKey);
            keyPairInfo.addProperty("publicKeySignature", Base64.getEncoder().encodeToString(publicKeySignature));
            keyPairInfo.addProperty("expiresAt", expiresAt);

            JsonObject signedDataJson = new JsonObject();
            signedDataJson.addProperty("original", Base64.getEncoder().encodeToString(signedData.original));
            signedDataJson.addProperty("signed", Base64.getEncoder().encodeToString(signedData.signed));

            JsonObject request = new JsonObject();
            request.add("keyPair", keyPairInfo);
            request.add("signedData", signedDataJson);
            // Their server records/validates the mod id + versions; sending "skyblocker"
            // with the matching UA is what makes the token indistinguishable from a real one.
            request.addProperty("mod", "skyblocker");
            request.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name());
            request.addProperty("modVersion", "6.10.4");

            HttpRequest req = HttpRequest.newBuilder()
                  .uri(URI.create(AUTH_URL))
                  .timeout(Duration.ofSeconds(30L))
                  .header("Accept", "application/json")
                  .header("Content-Type", "application/json")
                  .header("User-Agent", "Skyblocker/6.10.4 (" + SharedConstants.getCurrentVersion().name() + ")")
                  .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request)))
                  .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
               JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
               if (json != null && json.has("token")) {
                  token = json.get("token").getAsString();
                  long issuedAt = json.has("issuedAt") ? json.get("issuedAt").getAsLong() : System.currentTimeMillis();
                  long exp = json.has("expiresAt") ? json.get("expiresAt").getAsLong() : System.currentTimeMillis() + 3600_000L;
                  LOGGER.info("[EggAuth] aaron auth succeeded; token refresh scheduled.");
                  debugChat("§aaaron auth OK - EggFinder token acquired.");

                  // Refresh 5 minutes before expiry like Skyblocker does.
                  long refreshInMs = Math.max(60_000L, (exp - issuedAt) - 300_000L);
                  SCHEDULER.schedule(EggAuth::forceUpdateToken, refreshInMs, TimeUnit.MILLISECONDS);

                  EggWebSocket.onTokenRefreshed();
               } else {
                  LOGGER.error("[EggAuth] aaron response missing token: HTTP " + resp.statusCode() + " body: " + resp.body());
                  debugChat("§caaron auth: response missing token (HTTP " + resp.statusCode() + ").");
               }
            } else {
               LOGGER.error("[EggAuth] aaron auth failed: HTTP " + resp.statusCode() + " body: " + resp.body());
               debugChat("§caaron auth failed: HTTP " + resp.statusCode() + ".");
               // Retry in 15 minutes like Skyblocker.
               SCHEDULER.schedule(EggAuth::forceUpdateToken, 900_000L, TimeUnit.MILLISECONDS);
            }
         } catch (Throwable t) {
            LOGGER.error("[EggAuth] aaron auth exception: " + t.getMessage(), t);
            debugChat("§caaron auth exception: " + t.getClass().getSimpleName());
            SCHEDULER.schedule(EggAuth::forceUpdateToken, 300_000L, TimeUnit.MILLISECONDS);
         } finally {
            authenticating = false;
         }
      });
   }

   private static void debugChat(String msg) {
      if (BomboConfig.get().eggFinderDebug && Minecraft.getInstance().player != null) {
         Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§eEggFinder Debug§8] " + msg));
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
