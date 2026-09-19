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
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(30L)).build();
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

      // Do NOT send any HTTP requests to Skyblocker API
      authenticating = false;
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
