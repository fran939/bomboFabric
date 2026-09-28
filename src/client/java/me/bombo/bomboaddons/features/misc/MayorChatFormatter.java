package me.bombo.bomboaddons.features.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.mixin.ChatComponentAccessor;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class MayorChatFormatter {

   private static final long CACHE_TTL_MS = 300_000L; // 5 minutes
   private static final int TIMEOUT_MS = 6000;
   private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

   private static volatile ElectionData cachedData = null;
   private static volatile long lastFetchTime = 0;

   public record PerkInfo(String name, String description) {}
   public record MinisterInfo(String name, String key, PerkInfo perk) {}
   public record ElectionData(
      String mayorName,
      String mayorKey,
      List<PerkInfo> perks,
      MinisterInfo minister,
      String timeRemainingFormatted
   ) {}

   private MayorChatFormatter() {}

   public static boolean isMayorMessage(String raw) {
      if (raw == null) return false;
      String stripped = raw.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
      return stripped.contains("[Mayor]") || (stripped.contains("Current:") && stripped.contains("Minister:"));
   }

   public static Component processIncomingChat(String raw) {
      if (!isMayorMessage(raw)) return null;

      String prefix = "";
      int mayorIdx = raw.indexOf("[Mayor]");
      if (mayorIdx > 0) {
         prefix = raw.substring(0, mayorIdx);
      } else {
         int curIdx = raw.indexOf("Current:");
         if (curIdx > 0) {
            prefix = raw.substring(0, curIdx);
         }
      }

      Component inner;
      long now = System.currentTimeMillis();
      if (cachedData != null && (now - lastFetchTime < CACHE_TTL_MS)) {
         inner = buildInteractive(cachedData);
      } else {
         if (IN_FLIGHT.compareAndSet(false, true)) {
            fetchAsync(false);
         }
         if (cachedData != null) {
            inner = buildInteractive(cachedData);
         } else {
            inner = Component.literal("§8[§6Mayor§8] §7Loading active mayor & election details...");
         }
      }

      if (!prefix.isEmpty()) {
         return Component.literal(prefix).append(inner);
      }
      return inner;
   }

   public static void executeCommand() {
      long now = System.currentTimeMillis();
      if (cachedData != null && (now - lastFetchTime < CACHE_TTL_MS)) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            mc.player.sendSystemMessage(buildInteractive(cachedData));
         }
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.sendSystemMessage(Component.literal("§8[§6Mayor§8] §7Fetching active election data..."));
      }

      if (IN_FLIGHT.compareAndSet(false, true)) {
         fetchAsync(true);
      }
   }

   private static void fetchAsync(boolean sendDirect) {
      Thread thread = new Thread(() -> {
         ElectionData data = null;
         try {
            data = fetch();
         } catch (Exception ignored) {
         } finally {
            IN_FLIGHT.set(false);
         }

         if (data != null) {
            cachedData = data;
            lastFetchTime = System.currentTimeMillis();
            updateChatOnMainThread(data, sendDirect);
         }
      }, "Bombo-MayorFetch");
      thread.setDaemon(true);
      thread.start();
   }

   private static ElectionData fetch() {
      HttpURLConnection conn = null;
      try {
         String targetUrl = BomboApiUrl.getApiUrl("/election");
         URL url = URI.create(targetUrl).toURL();
         conn = (HttpURLConnection) url.openConnection();
         conn.setRequestMethod("GET");
         conn.setConnectTimeout(TIMEOUT_MS);
         conn.setReadTimeout(TIMEOUT_MS);
         conn.setRequestProperty("User-Agent", "BomboAddons/26.2");
         conn.connect();

         if (conn.getResponseCode() != 200) return null;

         try (InputStream in = conn.getInputStream();
              InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (!json.has("mayor") || !json.get("mayor").isJsonObject()) return null;

            JsonObject mayorObj = json.getAsJsonObject("mayor");
            String mayorName = mayorObj.has("name") ? mayorObj.get("name").getAsString() : "Unknown";
            String mayorKey = mayorObj.has("key") ? mayorObj.get("key").getAsString() : "";

            List<PerkInfo> perks = new ArrayList<>();
            if (mayorObj.has("perks") && mayorObj.get("perks").isJsonArray()) {
               JsonArray perksArr = mayorObj.getAsJsonArray("perks");
               for (JsonElement el : perksArr) {
                  if (el.isJsonObject()) {
                     JsonObject p = el.getAsJsonObject();
                     String pName = p.has("name") ? p.get("name").getAsString() : "";
                     String pDesc = p.has("description") ? p.get("description").getAsString() : "";
                     perks.add(new PerkInfo(pName, pDesc));
                  }
               }
            }

            MinisterInfo minister = null;
            if (mayorObj.has("minister") && mayorObj.get("minister").isJsonObject()) {
               JsonObject minObj = mayorObj.getAsJsonObject("minister");
               String mName = minObj.has("name") ? minObj.get("name").getAsString() : "";
               String mKey = minObj.has("key") ? minObj.get("key").getAsString() : "";
               PerkInfo minPerk = null;
               if (minObj.has("perk") && minObj.get("perk").isJsonObject()) {
                  JsonObject mp = minObj.getAsJsonObject("perk");
                  minPerk = new PerkInfo(
                     mp.has("name") ? mp.get("name").getAsString() : "",
                     mp.has("description") ? mp.get("description").getAsString() : ""
                  );
               }
               minister = new MinisterInfo(mName, mKey, minPerk);
            }

            String timeFormatted = null;
            if (json.has("term") && json.get("term").isJsonObject()) {
               JsonObject termObj = json.getAsJsonObject("term");
               if (termObj.has("timeRemainingFormatted")) {
                  timeFormatted = termObj.get("timeRemainingFormatted").getAsString();
               }
            }

            return new ElectionData(mayorName, mayorKey, perks, minister, timeFormatted);
         }
      } catch (Exception e) {
         return null;
      } finally {
         if (conn != null) conn.disconnect();
      }
   }

   private static void updateChatOnMainThread(ElectionData data, boolean sendDirect) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null) return;
      mc.execute(() -> {
         Component comp = buildInteractive(data);
         if (sendDirect) {
            if (mc.player != null) mc.player.sendSystemMessage(comp);
            return;
         }

         try {
            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
               net.minecraft.client.gui.components.ChatComponent chat = mc.gui.hud.getChat();
               if (chat instanceof ChatComponentAccessor acc) {
                  List<GuiMessage> messages = acc.getAllMessages();
                  if (messages != null) {
                     for (int i = 0; i < messages.size(); i++) {
                        GuiMessage msg = messages.get(i);
                        if (msg != null && msg.content() != null) {
                           String text = msg.content().getString();
                           if (text.contains("[Mayor]") || text.contains("Loading active mayor")) {
                              messages.set(i, new GuiMessage(
                                 msg.addedTime(), comp, msg.signature(), msg.source(), msg.tag()));
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
            mc.player.sendSystemMessage(comp);
         }
      });
   }

   public static Component buildInteractive(ElectionData data) {
      MutableComponent root = Component.empty();
      root.append(Component.literal("§8[§6Mayor§8] §7Current: "));

      // Mayor Name with Full Hypixel Tooltip on Hover (colored without underline)
      Component mayorTooltip = buildFullMayorTooltip(data);
      MutableComponent mayorChip = Component.literal("§e" + data.mayorName() + "§r")
         .withStyle(Style.EMPTY
            .withColor(ChatFormatting.YELLOW)
            .withHoverEvent(new HoverEvent.ShowText(mayorTooltip)));
      root.append(mayorChip);

      // Perks list (each individually hoverable, colored without underline)
      if (data.perks() != null && !data.perks().isEmpty()) {
         root.append(Component.literal(" §7("));
         for (int i = 0; i < data.perks().size(); i++) {
            if (i > 0) root.append(Component.literal("§7, "));
            PerkInfo p = data.perks().get(i);
            Component perkTooltip = buildPerkTooltip(p);
            MutableComponent perkChip = Component.literal("§b" + p.name() + "§r")
               .withStyle(Style.EMPTY
                  .withColor(ChatFormatting.AQUA)
                  .withHoverEvent(new HoverEvent.ShowText(perkTooltip)));
            root.append(perkChip);
         }
         root.append(Component.literal("§7)"));
      }

      // Minister (colored without underline)
      if (data.minister() != null && data.minister().name() != null && !data.minister().name().isEmpty()) {
         root.append(Component.literal(" §7| Minister: "));
         Component minTooltip = buildMinisterTooltip(data.minister());
         MutableComponent minChip = Component.literal("§6" + data.minister().name() + "§r")
            .withStyle(Style.EMPTY
               .withColor(ChatFormatting.GOLD)
               .withHoverEvent(new HoverEvent.ShowText(minTooltip)));
         root.append(minChip);
      }

      // Handover time
      if (data.timeRemainingFormatted() != null && !data.timeRemainingFormatted().isEmpty()) {
         root.append(Component.literal(" §8(Handover in " + data.timeRemainingFormatted() + ")"));
      }

      return root;
   }

   /**
    * Hypixel Calendar GUI Mayor Tooltip format (exact match to user screenshot):
    *
    * Mayor Diaz
    * Perks List
    * -----------------------------
    * Volume Trading
    * The available item quantity per
    * Shen's Auction has been doubled,
    * and two additional Shen's Special
    * auctions will be available if Diaz
    * is Mayor.
    *
    * Long Term Investment
    * The elected minister will appear as
    * a candidate with all of their perks
    * in the next election cycle.
    *
    * The listed perks are available to
    * all players until the closing of
    * the next elections.
    */
   private static Component buildFullMayorTooltip(ElectionData data) {
      MutableComponent tip = Component.empty();
      tip.append(Component.literal("§eMayor " + data.mayorName() + "\n"));
      tip.append(Component.literal("§7Perks List\n"));
      tip.append(Component.literal("§8§m-----------------------------\n"));

      if (data.perks() != null) {
         for (int i = 0; i < data.perks().size(); i++) {
            PerkInfo p = data.perks().get(i);
            tip.append(Component.literal("§e" + p.name() + "\n"));
            tip.append(Component.literal(wrapDescription(p.description(), 36) + "\n\n"));
         }
      }

      tip.append(Component.literal("§8§m-----------------------------\n"));
      tip.append(Component.literal("§7The listed perks are available to\nall players until the closing of\nthe next elections."));
      return tip;
   }

   private static Component buildPerkTooltip(PerkInfo perk) {
      MutableComponent tip = Component.empty();
      tip.append(Component.literal("§e" + perk.name() + "\n"));
      tip.append(Component.literal("§8§m-----------------------------\n"));
      tip.append(Component.literal(wrapDescription(perk.description(), 36)));
      return tip;
   }

   private static Component buildMinisterTooltip(MinisterInfo min) {
      MutableComponent tip = Component.empty();
      tip.append(Component.literal("§6Minister " + min.name() + "\n"));
      tip.append(Component.literal("§7Minister Perk\n"));
      tip.append(Component.literal("§8§m-----------------------------\n"));
      if (min.perk() != null) {
         tip.append(Component.literal("§e" + min.perk().name() + "\n"));
         tip.append(Component.literal(wrapDescription(min.perk().description(), 36)));
      } else {
         tip.append(Component.literal("§7No active minister perk."));
      }
      return tip;
   }

   /**
    * Wraps a Minecraft-formatted string at word boundaries around maxChars per line,
    * preserving color codes across newlines.
    */
   private static String wrapDescription(String raw, int maxChars) {
      if (raw == null || raw.isEmpty()) return "";
      String[] lines = raw.split("\n");
      StringBuilder sb = new StringBuilder();
      String activeFormat = "§7";

      for (int l = 0; l < lines.length; l++) {
         if (l > 0) sb.append("\n");
         String line = lines[l];
         String[] words = line.split(" ");
         StringBuilder curLine = new StringBuilder();
         int visibleLen = 0;

         for (String word : words) {
            int wordVisLen = word.replaceAll("(?i)§[0-9a-fk-or]", "").length();
            if (visibleLen > 0 && visibleLen + 1 + wordVisLen > maxChars) {
               sb.append(activeFormat).append(curLine).append("\n");
               curLine.setLength(0);
               visibleLen = 0;
            }

            if (curLine.length() > 0) {
               curLine.append(" ");
               visibleLen += 1;
            }
            curLine.append(word);
            visibleLen += wordVisLen;

            // Track last format code in this word
            int lastCodeIdx = word.lastIndexOf('§');
            if (lastCodeIdx != -1 && lastCodeIdx + 1 < word.length()) {
               activeFormat = "§" + word.charAt(lastCodeIdx + 1);
            }
         }

         if (curLine.length() > 0) {
            sb.append(activeFormat).append(curLine);
         }
      }
      return sb.toString();
   }
}
