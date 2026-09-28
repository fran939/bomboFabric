package me.bombo.bomboaddons.features.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the flat {@code [RTCA50]} line BomboBot prints into Minecraft chat into interactive,
 * hoverable components.
 *
 * <p>The Discord bot broadcasts a single dense string to game chat, e.g.
 * <pre>
 * [Bombo] [DC] BomboBot: &#127919; [RTCA50] bomboclas on Cucumber (CA 48.00): 1,070 M7 runs to CA 50 | Archer 48 ...
 * </pre>
 * That string carries the exact class levels but none of the per-class detail people actually
 * hover for (runs to max, XP to the next level, and the boost breakdown that produced the run
 * count). This class intercepts the line in {@code ChatMixin}, cancels the flat version and
 * re-emits it built from {@code /command/rtca/<player>}, so every class name becomes a hoverable
 * element.
 *
 * <p>The API call is cached per player for {@link #CACHE_TTL_MS}; the bot can print the same line
 * several times in a row and only the first print pays for the request.
 */
public final class RtcaChatFormatter {

   /** Matches the bot's RTCA line and captures the player and profile it was printed for. */
   private static final Pattern RTCA_PATTERN = Pattern.compile(
      "\\[RTCA50]\\s+(\\S+)\\s+on\\s+(\\S+?)\\s*\\(CA\\s*([\\d.]+)\\)?:",
      Pattern.CASE_INSENSITIVE);

   /** Fallback that just pulls the player out of any "[RTCA50] <name>" occurrence. */
   private static final Pattern PLAYER_PATTERN = Pattern.compile("\\[RTCA50]\\s+(\\S+)");

   private static final String[] CLASS_ORDER = {"archer", "berserk", "healer", "mage", "tank"};

   private static final ChatFormatting[] CLASS_COLORS = {
      ChatFormatting.GREEN,   // archer
      ChatFormatting.RED,     // berserk
      ChatFormatting.LIGHT_PURPLE, // healer
      ChatFormatting.AQUA,    // mage
      ChatFormatting.DARK_GREEN // tank
   };

   /** Legacy section-sign codes matching {@link #CLASS_COLORS}, used for the literal chat text. */
   private static final String[] CLASS_COLOR_CODES = {"a", "c", "d", "b", "2"};

   private static final long CACHE_TTL_MS = 60_000L;
   private static final int CONNECT_TIMEOUT_MS = 6000;
   private static final int READ_TIMEOUT_MS = 8000;

   private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
   private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

   private RtcaChatFormatter() {
   }

   private record CacheEntry(RtcaData data, long fetchedAt) {
      boolean fresh(long now) {
         return now - fetchedAt < CACHE_TTL_MS;
      }
   }

   /** The slice of {@code /command/rtca/<player>} this feature needs. */
   private record RtcaData(
      String player,
      String profile,
      double classAverage,
      int totalRuns,
      String floorLabel,
      int targetLevel,
      Map<String, ClassInfo> classes
   ) {
   }

   private record ClassInfo(
      String label,
      double exactLevel,
      boolean maxed,
      long xpPerRun,
      int runsToMax,
      long xpForNextLevel,
      double boostTotal,
      String boostLabel,
      List<String> assumed
   ) {
   }

   /**
    * @return true when {@code raw} is a BomboBot RTCA line we own and should replace.
    */
   public static boolean isRtcaMessage(String raw) {
      return raw != null && raw.contains("[RTCA50]");
   }

   /**
    * Hook for {@code ChatMixin}. Returns {@code null} when the line is not ours (so the caller
    * leaves it alone); otherwise a placeholder component is returned immediately and the enriched
    * version is sent from the network callback once the API answers.
    */
   public static Component processIncomingChat(String raw) {
      if (!isRtcaMessage(raw)) return null;

      Matcher matcher = PLAYER_PATTERN.matcher(stripped(raw));
      if (!matcher.find()) return null;
      String player = matcher.group(1);
      if (player == null || player.isEmpty()) return null;

      String profileFromLine = null;
      Matcher full = RTCA_PATTERN.matcher(stripped(raw));
      if (full.find() && full.groupCount() >= 3) {
         profileFromLine = full.group(2);
      }

      String key = player.toLowerCase(Locale.ROOT);
      long now = System.currentTimeMillis();
      CacheEntry cached = CACHE.get(key);
      if (cached != null && cached.fresh(now)) {
         return build(cached.data(), profileFromLine);
      }

      // Kick off (or join) the fetch and show a compact "loading" line straight away.
      if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) == null) {
         fetchAsync(key, player, profileFromLine);
      }
      return Component.literal("§6[Bombo] §e[RTCA50] §b" + player + " §8loading class details...");
   }

   /** Message sent back to the player when the API could not be reached. */
   private static Component buildFallback(String player, String profile) {
      return Component.literal("§6[Bombo] §e[RTCA50] §b" + player
         + (profile != null ? " §7on §a" + profile : "")
         + " §8(class details unavailable)");
   }

   private static void fetchAsync(String key, String player, String profileFromLine) {
      Thread thread = new Thread(() -> {
         RtcaData data = null;
         try {
            data = fetch(player);
         } catch (Exception ignored) {
            data = null;
         } finally {
            IN_FLIGHT.remove(key);
         }

         if (data == null) {
            updateChatOnMainThread(player, buildFallback(player, profileFromLine));
            return;
         }

         CACHE.put(key, new CacheEntry(data, System.currentTimeMillis()));
         updateChatOnMainThread(player, build(data, profileFromLine));
      }, "Bombo-RtcaFetch");
      thread.setDaemon(true);
      thread.start();
   }

   private static void updateChatOnMainThread(String player, Component component) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null) return;
      mc.execute(() -> {
         try {
            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
               net.minecraft.client.gui.components.ChatComponent chat = mc.gui.hud.getChat();
               if (chat instanceof me.bombo.bomboaddons.mixin.ChatComponentAccessor acc) {
                  java.util.List<net.minecraft.client.multiplayer.chat.GuiMessage> messages = acc.getAllMessages();
                  if (messages != null) {
                     String lowerPlayer = player.toLowerCase(Locale.ROOT);
                     for (int i = 0; i < messages.size(); i++) {
                        net.minecraft.client.multiplayer.chat.GuiMessage msg = messages.get(i);
                        if (msg != null && msg.content() != null) {
                           String text = msg.content().getString();
                           if (text.contains("[RTCA50]") && text.toLowerCase(Locale.ROOT).contains(lowerPlayer)) {
                              messages.set(i, new net.minecraft.client.multiplayer.chat.GuiMessage(
                                 msg.addedTime(), component, msg.signature(), msg.source(), msg.tag()));
                              acc.invokeRefreshTrimmedMessages();
                              return;
                           }
                        }
                     }
                  }
               }
            }
         } catch (Exception ignored) {
         }

         if (mc.player != null) {
            mc.player.sendSystemMessage(component);
         }
      });
   }

   // --------------------------------------------------------------------------
   // Component construction
   // --------------------------------------------------------------------------

   private static Component build(RtcaData data, String profileFromLine) {
      String profile = data.profile() != null ? data.profile() : profileFromLine;

      MutableComponent root = Component.empty();
      root.append(Component.literal("§6[Bombo] §e[RTCA50] §b" + (data.player() == null ? "" : data.player()) + " "));
      if (profile != null && !profile.isEmpty()) {
         root.append(Component.literal("§7on §a" + profile + " "));
      }
      root.append(Component.literal("§7(CA §e" + formatTwoDecimals(data.classAverage()) + "§7): "));

      if (data.targetLevel() > 0 && data.classAverage() >= data.targetLevel()) {
         root.append(Component.literal("§aalready at Class Average " + data.targetLevel()));
         return root;
      }

      root.append(Component.literal("§f" + formatThousands(data.totalRuns()) + " "
         + (data.floorLabel() == null ? "M7" : data.floorLabel())
         + " runs to CA " + data.targetLevel() + " "));

      // Hoverable class chips, each carrying its own breakdown tooltip.
      for (int i = 0; i < CLASS_ORDER.length; i++) {
         String id = CLASS_ORDER[i];
         ClassInfo info = data.classes().get(id);
         if (info == null) continue;
         if (i > 0) root.append(Component.literal("§8| "));
         root.append(buildClassChip(id, info, i));
      }

      return root;
   }

   private static MutableComponent buildClassChip(String id, ClassInfo info, int colorIndex) {
      int index = Math.floorMod(colorIndex, CLASS_COLORS.length);
      ChatFormatting color = CLASS_COLORS[index];
      String text = info.label() + " " + formatTwoDecimals(info.exactLevel()) + (info.maxed() ? "*" : "");

      MutableComponent chip = Component.literal("§" + CLASS_COLOR_CODES[index] + text);
      return chip.withStyle(Style.EMPTY
         .withColor(color)
         .withBold(info.maxed())
         .withHoverEvent(new HoverEvent.ShowText(buildTooltip(info, id))));
   }

   private static Component buildTooltip(ClassInfo info, String id) {
      MutableComponent tip = Component.empty();
      tip.append(Component.literal("§6§l" + info.label() + " Level " + formatTwoDecimals(info.exactLevel()))
         .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)));

      tip.append(Component.literal("\n§7• Runs to max class: ")
         .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
      if (info.maxed()) {
         tip.append(Component.literal("§aMAXED").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
      } else {
         tip.append(Component.literal("§b" + formatThousands(info.runsToMax()) + " runs")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)));
      }

      tip.append(Component.literal("\n§7• XP to next level: ")
         .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
      if (info.maxed()) {
         tip.append(Component.literal("§aMAXED").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
      } else {
         tip.append(Component.literal("§e" + formatThousands(info.xpForNextLevel()) + " XP")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
      }

      tip.append(Component.literal("\n§7• XP per run: ")
         .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
      tip.append(Component.literal("§a" + formatThousands(info.xpPerRun()) + " XP")
         .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));

      tip.append(Component.literal("\n§7• Boost Breakdown: ").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
      if (info.boostLabel() == null || info.boostLabel().isEmpty()) {
         tip.append(Component.literal("§8none from your profile").withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)));
      } else {
         tip.append(Component.literal("§d+" + Math.round(info.boostTotal() * 100) + "% total")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE)));
         for (String part : info.boostLabel().split("\\|")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            tip.append(Component.literal("\n   §8- §7" + trimmed)
               .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
         }
      }

      // Boosts the API cannot see. They are deliberately counted as zero rather than guessed, so
      // say so instead of letting the tooltip look complete when it is not.
      if (info.assumed() != null && !info.assumed().isEmpty()) {
         StringBuilder names = new StringBuilder();
         for (String key : info.assumed()) {
            if (names.length() > 0) names.append(", ");
            names.append(friendlyAssumed(key));
         }
         tip.append(Component.literal("\n§8• Not counted: " + names + "\n   §8(unknown from the API - not assumed)")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)));
      }

      return tip;
   }

   // --------------------------------------------------------------------------
   // API
   // --------------------------------------------------------------------------

   private static RtcaData fetch(String player) throws Exception {
      String encoded = URLEncoder.encode(player, StandardCharsets.UTF_8);
      String url = BomboApiUrl.getApiUrl("/command/rtca/" + encoded + "?format=json");

      HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("User-Agent",
         "BomboAddons/" + me.bombo.bomboaddons.BomboaddonsClient.MOD_VERSION);
      String apiKey = me.bombo.bomboaddons.features.auth.BomboApiKeyManager.getApiKey();
      if (apiKey != null && !apiKey.isEmpty()) {
         conn.setRequestProperty("Api-Key", apiKey);
         conn.setRequestProperty("X-Api-Key", apiKey);
      }
      conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
      conn.setReadTimeout(READ_TIMEOUT_MS);

      if (conn.getResponseCode() != 200) {
         conn.disconnect();
         return null;
      }

      JsonObject root;
      try (InputStream in = conn.getInputStream();
           InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
         JsonElement parsed = JsonParser.parseReader(reader);
         if (!parsed.isJsonObject()) return null;
         root = parsed.getAsJsonObject();
      } finally {
         conn.disconnect();
      }

      return parse(root, player);
   }

   private static RtcaData parse(JsonObject root, String fallbackPlayer) {
      if (root.has("error")) return null;

      double classAverage = readDouble(root, "classAverageExact");
      if (classAverage <= 0) classAverage = readDouble(root, "classAverage");

      JsonObject rtca = root.has("rtca") && root.get("rtca").isJsonObject()
         ? root.getAsJsonObject("rtca")
         : new JsonObject();

      int totalRuns = (int) readDouble(rtca, "totalRuns");
      int targetLevel = (int) readDouble(rtca, "targetLevel");
      if (targetLevel <= 0) targetLevel = 50;

      String floorLabel = "M7";
      if (rtca.has("floor") && rtca.get("floor").isJsonObject()) {
         JsonObject floor = rtca.getAsJsonObject("floor");
         if (floor.has("label") && !floor.get("label").isJsonNull()) {
            floorLabel = floor.get("label").getAsString();
         }
      }

      String profile = root.has("profile") && !root.get("profile").isJsonNull()
         ? root.get("profile").getAsString()
         : null;

      Map<String, ClassInfo> classes = new LinkedHashMap<>();
      if (root.has("classes") && root.get("classes").isJsonObject()) {
         JsonObject classesNode = root.getAsJsonObject("classes");
         for (String id : CLASS_ORDER) {
            if (!classesNode.has(id) || !classesNode.get(id).isJsonObject()) continue;
            JsonObject node = classesNode.getAsJsonObject(id);
            String label = node.has("label") && !node.get("label").isJsonNull()
               ? node.get("label").getAsString()
               : capitalize(id);
            double exactLevel = readDouble(node, "exactLevel");
            if (exactLevel <= 0) exactLevel = readDouble(node, "level");
            boolean maxed = node.has("maxed") && node.get("maxed").getAsBoolean();
            long xpPerRun = (long) readDouble(node, "xpPerRun");
            int runsToMax = (int) readDouble(node, "runsToMax");
            long xpForNext = (long) readDouble(node, "xpToNextLevel");
            if (xpForNext <= 0) xpForNext = (long) readDouble(node, "xpForNextLevel");
            double boostTotal = 0;
            String boostLabel = "";
            List<String> assumed = new ArrayList<>();
            if (node.has("boost") && node.get("boost").isJsonObject()) {
               JsonObject boost = node.getAsJsonObject("boost");
               boostTotal = readDouble(boost, "total");
               if (boost.has("label") && !boost.get("label").isJsonNull()) {
                  boostLabel = boost.get("label").getAsString();
               }
               if (boost.has("assumed") && boost.get("assumed").isJsonArray()) {
                  for (JsonElement element : boost.getAsJsonArray("assumed")) {
                     assumed.add(element.getAsString());
                  }
               }
            }
            classes.put(id, new ClassInfo(label, exactLevel, maxed, xpPerRun, runsToMax, xpForNext, boostTotal, boostLabel, assumed));
         }
      }

      if (classes.isEmpty()) return null;

      return new RtcaData(resolvePlayerName(root, fallbackPlayer), profile, classAverage, totalRuns, floorLabel, targetLevel, classes);

   }

   private static String resolvePlayerName(JsonObject root, String fallback) {
      if (root.has("ign") && !root.get("ign").isJsonNull()) {
         return root.get("ign").getAsString();
      }
      return fallback;
   }

   private static double readDouble(JsonObject node, String key) {
      if (node == null || !node.has(key) || node.get(key).isJsonNull()) return 0;
      try {
         JsonElement element = node.get(key);
         if (!element.isJsonPrimitive()) return 0;
         return element.getAsDouble();
      } catch (Exception ignored) {
         return 0;
      }
   }

   // --------------------------------------------------------------------------
   // Small helpers
   // --------------------------------------------------------------------------

   private static String friendlyAssumed(String key) {
      if (key == null) return "";
      switch (key.toLowerCase(Locale.ROOT)) {
         case "hecatomb":
            return "Hecatomb";
         case "scarf":
            return "Scarf accessory";
         case "graduate":
            return "Catacombs Graduate";
         case "mayor":
            return "Mayor";
         case "global":
            return "Global multiplier";
         default:
            return key;
      }
   }

   private static String stripped(String raw) {
      return raw.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("[\\u200B-\\u200D\\uFEFF]", "").trim();
   }

   private static String capitalize(String value) {
      if (value == null || value.isEmpty()) return value;
      return Character.toUpperCase(value.charAt(0)) + value.substring(1);
   }

   private static String formatTwoDecimals(double value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private static String formatThousands(long value) {
      return String.format(Locale.ROOT, "%,d", value);
   }
}
