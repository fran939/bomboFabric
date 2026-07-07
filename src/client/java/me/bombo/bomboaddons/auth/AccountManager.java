package me.bombo.bomboaddons.auth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.minecraft.UserApiService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AccountManager {
    public static class Account {
        public String uuid;
        public String username;
        public String accessToken;
        public String refreshToken;
        public String skinUrl;
        public String clientId; // The OAuth client ID used to create this account's tokens

        public Account(String uuid, String username, String accessToken, String refreshToken, String skinUrl) {
            this.uuid = uuid;
            this.username = username;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.skinUrl = skinUrl;
            this.clientId = null; // will default to CLIENT_ID when refreshing
        }
    }

    public static final List<Account> accounts = new ArrayList<>();
    public static Account currentAccount = null;
    public static Account originalAccount = null;

    private static final String CLIENT_ID = "54fd49e4-2103-4044-9603-2b028c814ec3";
    private static final String SCOPE = "XboxLive.signin%20offline_access";
    private static final HttpClient client = HttpClient.newHttpClient();

    private static final File ACCOUNTS_FILE = new File(System.getProperty("user.home"), ".bomboaddons/accounts.json");
    private static final File KEY_FILE = new File(System.getProperty("user.home"), ".bomboaddons/auth_key.dat");

    public static void init() {
        CryptoUtils.init(KEY_FILE);
        User user = Minecraft.getInstance().getUser();
        originalAccount = new Account(user.getProfileId().toString(), user.getName(), user.getAccessToken(), "", "");
        loadAccounts();
    }

    public static void loadAccounts() {
        if (!ACCOUNTS_FILE.exists()) {
            if (currentAccount == null) {
                accounts.add(originalAccount);
                currentAccount = originalAccount;
            }
            return;
        }
        try (FileReader reader = new FileReader(ACCOUNTS_FILE)) {
            JsonElement rootElem = JsonParser.parseReader(reader);
            accounts.clear();
            if (rootElem.isJsonArray()) {
                JsonArray array = rootElem.getAsJsonArray();
                for (JsonElement element : array) {
                    JsonObject obj = element.getAsJsonObject();
                    String uuid = obj.get("uuid").getAsString();
                    String username = obj.get("username").getAsString();
                    String skinUrl = obj.has("skinUrl") ? obj.get("skinUrl").getAsString() : "";
                    String refreshEnc = obj.get("refreshToken").getAsString();
                    String accessEnc = obj.get("accessToken").getAsString();
                    Account acc = new Account(uuid, username, CryptoUtils.decrypt(accessEnc), CryptoUtils.decrypt(refreshEnc), skinUrl);
                    boolean exists = false;
                    for (Account a : accounts) {
                        if (a.uuid.replace("-", "").equalsIgnoreCase(acc.uuid.replace("-", "")) || a.username.equalsIgnoreCase(acc.username)) {
                            exists = true; break;
                        }
                    }
                    if (!exists) accounts.add(acc);
                }
                saveAccounts();
            } else if (rootElem.isJsonObject()) {
                JsonObject root = rootElem.getAsJsonObject();
                if (root.has("accounts")) {
                    JsonArray array = root.getAsJsonArray("accounts");
                    for (JsonElement element : array) {
                        JsonObject obj = element.getAsJsonObject();
                        if (!obj.has("profile") || !obj.has("msa")) continue;
                        JsonObject profile = obj.getAsJsonObject("profile");
                        JsonObject msa = obj.getAsJsonObject("msa");
                        String uuid = profile.get("id").getAsString();
                        String username = profile.get("name").getAsString();
                        String skinUrl = "";
                        if (profile.has("skin") && !profile.get("skin").isJsonNull()) {
                            JsonObject skin = profile.getAsJsonObject("skin");
                            if (skin.has("url")) skinUrl = skin.get("url").getAsString();
                        }
                        String accessEnc = "";
                        if (obj.has("ygg") && obj.getAsJsonObject("ygg").has("token")) {
                            accessEnc = obj.getAsJsonObject("ygg").get("token").getAsString();
                        } else if (msa.has("token")) {
                            accessEnc = msa.get("token").getAsString();
                        }
                        String refreshEnc = msa.has("refresh_token") ? msa.get("refresh_token").getAsString() : "";
                        // Read which OAuth client ID was used to create this token (crucial for refresh!)
                        String storedClientId = obj.has("msa-client-id") ? obj.get("msa-client-id").getAsString() : null;
                        Account acc = new Account(uuid, username, accessEnc, refreshEnc, skinUrl);
                        acc.clientId = storedClientId;
                        boolean exists = false;
                        for (Account a : accounts) {
                            if (a.uuid.replace("-", "").equalsIgnoreCase(acc.uuid.replace("-", "")) || a.username.equalsIgnoreCase(acc.username)) {
                                exists = true; break;
                            }
                        }
                        if (!exists) accounts.add(acc);
                    }
                }
            }
            
            // Set current account if it matches Minecraft
            if (currentAccount == null) {
                String mcUuid = Minecraft.getInstance().getUser().getProfileId().toString().replace("-", "");
                for (Account a : accounts) {
                    if (a.uuid.replace("-", "").equalsIgnoreCase(mcUuid)) {
                        currentAccount = a;
                        break;
                    }
                }
            }
            if (currentAccount == null) {
                boolean found = false;
                String originalUuid = originalAccount.uuid.replace("-", "");
                for (Account a : accounts) {
                    if (a.uuid.replace("-", "").equalsIgnoreCase(originalUuid) || a.username.equalsIgnoreCase(originalAccount.username)) {
                        currentAccount = a;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    accounts.add(originalAccount);
                    currentAccount = originalAccount;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        saveAccounts();
    }

    public static void saveAccounts() {
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            for (Account acc : accounts) {
                JsonObject obj = new JsonObject();
                
                JsonObject profile = new JsonObject();
                profile.addProperty("id", acc.uuid.replace("-", ""));
                profile.addProperty("name", acc.username);
                if (acc.skinUrl != null && !acc.skinUrl.isEmpty()) {
                    JsonObject skin = new JsonObject();
                    skin.addProperty("url", acc.skinUrl);
                    profile.add("skin", skin);
                }
                obj.add("profile", profile);
                
                JsonObject msa = new JsonObject();
                // To remain strictly compatible with Prism Launcher parsing, msa.token shouldn't be the MC token.
                // But if we don't have the original MSA token, we can just leave it empty or fallback.
                // Prism requires refresh_token to refresh.
                msa.addProperty("token", "");
                msa.addProperty("refresh_token", acc.refreshToken);
                // Store the client ID used for this account's MS tokens so we can refresh correctly
                if (acc.clientId != null && !acc.clientId.isEmpty()) {
                    obj.addProperty("msa-client-id", acc.clientId);
                }
                obj.add("msa", msa);
                
                JsonObject ygg = new JsonObject();
                ygg.addProperty("token", acc.accessToken);
                obj.add("ygg", ygg);
                
                array.add(obj);
            }
            root.add("accounts", array);
            
            ACCOUNTS_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(ACCOUNTS_FILE)) {
                writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static CompletableFuture<Account> refreshAccount(Account acc) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("[BOMBO-AUTH] Attempting to refresh token for " + acc.username);
                // Refresh Microsoft Token
                String reqBody = "client_id=" + (acc.clientId != null && !acc.clientId.isEmpty() ? acc.clientId : CLIENT_ID) + "&refresh_token=" + acc.refreshToken + "&grant_type=refresh_token";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.live.com/oauth20_token.srf"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(reqBody)).build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                System.out.println("[BOMBO-AUTH] MS Refresh response code: " + res.statusCode());
                if (res.statusCode() != 200) {
                    System.out.println("[BOMBO-AUTH] Failed to refresh MS token: " + res.body());
                    return null;
                }
                JsonObject tokenObj = JsonParser.parseString(res.body()).getAsJsonObject();
                String newAccess = tokenObj.get("access_token").getAsString();
                String newRefresh = tokenObj.get("refresh_token").getAsString();
                acc.refreshToken = newRefresh;

                System.out.println("[BOMBO-AUTH] Successfully refreshed MS token. Authenticating with Xbox...");
                Account updated = authenticateWithMicrosoftToken(newAccess, newRefresh);
                acc.accessToken = updated.accessToken;
                acc.username = updated.username;
                acc.skinUrl = updated.skinUrl;
                saveAccounts();
                System.out.println("[BOMBO-AUTH] Successfully completed Minecraft authentication for " + acc.username);
                return acc;
            } catch (Exception e) {
                System.out.println("[BOMBO-AUTH] Exception during refresh:");
                e.printStackTrace();
                return null;
            }
        });
    }
    
    // Step 1: Request Device Code
    public static CompletableFuture<JsonObject> requestDeviceCode() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String reqBody = "client_id=" + CLIENT_ID + "&scope=" + SCOPE;
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", "Mozilla/5.0")
                        .POST(HttpRequest.BodyPublishers.ofString(reqBody)).build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                return JsonParser.parseString(res.body()).getAsJsonObject();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    // Step 2: Poll for token
    public static void pollForToken(String deviceCode, Consumer<Account> onSuccess, Consumer<String> onError) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                String reqBody = "client_id=" + CLIENT_ID + "&device_code=" + deviceCode + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", "Mozilla/5.0")
                        .POST(HttpRequest.BodyPublishers.ofString(reqBody)).build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();

                if (obj.has("error")) {
                    String error = obj.get("error").getAsString();
                    if (!error.equals("authorization_pending") && !error.equals("slow_down")) {
                        if (error.equals("invalid_grant")) {
                            onError.accept("Code expired or used. Click 'Add Account' again.");
                        } else {
                            onError.accept(error);
                        }
                        executor.shutdown();
                    }
                } else if (obj.has("access_token")) {
                    String accessToken = obj.get("access_token").getAsString();
                    String refreshToken = obj.get("refresh_token").getAsString();
                    Account acc = authenticateWithMicrosoftToken(accessToken, refreshToken);
                    if (acc != null) {
                        // Remove existing
                        accounts.removeIf(a -> a.uuid.replace("-", "").equalsIgnoreCase(acc.uuid.replace("-", "")) || a.username.equalsIgnoreCase(acc.username));
                        accounts.add(acc);
                        currentAccount = acc;
                        saveAccounts();
                        onSuccess.accept(acc);
                    } else {
                        onError.accept("Authentication failed at Xbox/Minecraft step.");
                    }
                    executor.shutdown();
                }
            } catch (Exception e) {
                e.printStackTrace();
                onError.accept("Auth Error: " + e.getMessage() + ". Try again.");
                executor.shutdown();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private static Account authenticateWithMicrosoftToken(String msToken, String refreshToken) throws Exception {
        // XBL Auth
        JsonObject xblAuth = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msToken);
        xblAuth.add("Properties", xblProps);
        xblAuth.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblAuth.addProperty("TokenType", "JWT");

        HttpRequest xblReq = HttpRequest.newBuilder()
                .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(xblAuth))).build();
        HttpResponse<String> xblRes = client.send(xblReq, HttpResponse.BodyHandlers.ofString());
        if (xblRes.statusCode() >= 300) throw new Exception("XBL Error: " + xblRes.body());
        JsonObject xblObj = JsonParser.parseString(xblRes.body()).getAsJsonObject();
        String xblToken = xblObj.get("Token").getAsString();
        String uhs = xblObj.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        // XSTS Auth
        JsonObject xstsAuth = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xstsProps.add("UserTokens", userTokens);
        xstsAuth.add("Properties", xstsProps);
        xstsAuth.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsAuth.addProperty("TokenType", "JWT");

        HttpRequest xstsReq = HttpRequest.newBuilder()
                .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(xstsAuth))).build();
        HttpResponse<String> xstsRes = client.send(xstsReq, HttpResponse.BodyHandlers.ofString());
        if (xstsRes.statusCode() >= 300) throw new Exception("XSTS Error: " + xstsRes.body());
        JsonObject xstsObj = JsonParser.parseString(xstsRes.body()).getAsJsonObject();
        String xstsToken = xstsObj.get("Token").getAsString();

        // MC Auth
        JsonObject mcAuth = new JsonObject();
        mcAuth.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        HttpRequest mcReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(mcAuth))).build();
        HttpResponse<String> mcRes = client.send(mcReq, HttpResponse.BodyHandlers.ofString());
        JsonObject mcObj = JsonParser.parseString(mcRes.body()).getAsJsonObject();
        String mcToken = mcObj.get("access_token").getAsString();

        // Get Profile
        HttpRequest profileReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcToken)
                .GET().build();
        HttpResponse<String> profileRes = client.send(profileReq, HttpResponse.BodyHandlers.ofString());
        JsonObject profileObj = JsonParser.parseString(profileRes.body()).getAsJsonObject();
        String uuid = profileObj.get("id").getAsString();
        String name = profileObj.get("name").getAsString();
        
        String skinUrl = "";
        if (profileObj.has("skins") && profileObj.getAsJsonArray("skins").size() > 0) {
            skinUrl = profileObj.getAsJsonArray("skins").get(0).getAsJsonObject().get("url").getAsString();
        }

        return new Account(uuid, name, mcToken, refreshToken, skinUrl);
    }

    public static void setSession(Account acc) {
        currentAccount = acc;
        try {
            // Need to set Minecraft.getInstance().user
            User newUser = null;
                Object msaType = null;
                java.lang.reflect.Constructor<?>[] enumCtors = User.class.getDeclaredConstructors();
                for (java.lang.reflect.Constructor<?> c : enumCtors) {
                    for (Class<?> paramType : c.getParameterTypes()) {
                        if (paramType.isEnum()) {
                            Object[] constants = paramType.getEnumConstants();
                            if (constants.length > 0) msaType = constants[constants.length > 1 ? 1 : 0]; // Default to second (MSA) or first
                            for (Object enumConstant : constants) {
                                if (enumConstant.toString().equalsIgnoreCase("MSA")) {
                                    msaType = enumConstant;
                                    break;
                                }
                            }
                        }
                    }
                }
                
                String uuidStr = acc.uuid;
                if (!uuidStr.contains("-")) {
                    uuidStr = uuidStr.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                }
                java.util.UUID parsedUuid = java.util.UUID.fromString(uuidStr);
                
                java.lang.reflect.Constructor<?>[] ctors = User.class.getDeclaredConstructors();
                for (java.lang.reflect.Constructor<?> c : ctors) {
                    c.setAccessible(true);
                    try {
                        if (c.getParameterCount() == 6) {
                            if (c.getParameterTypes()[1] == java.util.UUID.class) {
                                newUser = (User) c.newInstance(acc.username, parsedUuid, acc.accessToken, Optional.empty(), Optional.empty(), msaType);
                            } else {
                                newUser = (User) c.newInstance(acc.username, acc.uuid, acc.accessToken, Optional.empty(), Optional.empty(), msaType);
                            }
                            break;
                        } else if (c.getParameterCount() == 4) {
                            if (c.getParameterTypes()[1] == java.util.UUID.class) {
                                newUser = (User) c.newInstance(acc.username, parsedUuid, acc.accessToken, msaType);
                            } else {
                                newUser = (User) c.newInstance(acc.username, acc.uuid, acc.accessToken, msaType);
                            }
                            break;
                        } else if (c.getParameterCount() == 5) {
                            newUser = (User) c.newInstance(acc.username, parsedUuid, acc.accessToken, Optional.empty(), msaType);
                            break;
                        }
                    } catch (Exception ex) {
                        System.out.println("Failed to instantiate with " + c.getParameterCount() + " args: " + ex.getMessage());
                    }
                }
            java.lang.reflect.Field userField = null;
            for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
                if (f.getType() == User.class) {
                    userField = f;
                    break;
                }
            }
            if (userField != null) {
                userField.setAccessible(true);
                userField.set(Minecraft.getInstance(), newUser);
            }
            
            try {
                java.lang.reflect.Field authServiceField = null;
                java.lang.reflect.Field userApiField = null;
                java.lang.reflect.Field profileKeyPairField = null;

                for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
                    String typeName = f.getType().getName();
                    if (typeName.contains("AuthenticationService")) {
                        authServiceField = f;
                    } else if (typeName.contains("UserApiService")) {
                        userApiField = f;
                    } else if (typeName.contains("ProfileKeyPairManager")) {
                        profileKeyPairField = f;
                    }
                }

                if (authServiceField != null && userApiField != null) {
                    authServiceField.setAccessible(true);
                    userApiField.setAccessible(true);
                    Object authService = authServiceField.get(Minecraft.getInstance());

                    if (authService != null) {
                        java.lang.reflect.Method createApiMethod = authService.getClass().getMethod("createUserApiService", String.class);
                        com.mojang.authlib.minecraft.UserApiService newApiService = (com.mojang.authlib.minecraft.UserApiService) createApiMethod.invoke(authService, newUser.getAccessToken());
                        userApiField.set(Minecraft.getInstance(), newApiService);

                        if (profileKeyPairField != null) {
                            profileKeyPairField.setAccessible(true);
                            net.minecraft.client.multiplayer.ProfileKeyPairManager newKeyPairManager = net.minecraft.client.multiplayer.ProfileKeyPairManager.create(
                                newApiService, newUser, Minecraft.getInstance().gameDirectory.toPath()
                            );
                            profileKeyPairField.set(Minecraft.getInstance(), newKeyPairManager);
                        }
                        System.out.println("[BOMBO-AUTH] Successfully updated internal Session services via reflection for fast swapping!");
                    }
                }
            } catch (Exception accessorEx) {
                System.out.println("[BOMBO-AUTH] Failed to update internal session services via reflection: " + accessorEx.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
