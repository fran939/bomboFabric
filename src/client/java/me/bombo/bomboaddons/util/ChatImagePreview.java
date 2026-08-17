package me.bombo.bomboaddons.util;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public class ChatImagePreview {
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
   private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
   private static final Pattern IMAGE_EXT_PATTERN = Pattern.compile("(?i)\\.(png|jpg|jpeg|webp|gif)(\\?.*)?$");
   
   private static final Map<String, LoadedImage> TEXTURE_CACHE = new ConcurrentHashMap<>();
   private static final Set<String> DOWNLOADING = ConcurrentHashMap.newKeySet();
   private static int idCounter = 0;

   public static class LoadedImage {
      public final Identifier id;
      public final int width;
      public final int height;

      public LoadedImage(Identifier id, int width, int height) {
         this.id = id;
         this.width = width;
         this.height = height;
      }
   }

   public static boolean isImageUrl(String url) {
      if (url == null || url.trim().isEmpty()) return false;
      String u = url.toLowerCase();
      if (u.contains("cdn.discordapp.com/attachments/") || u.contains("media.discordapp.net/attachments/")) {
         return true;
      }
      if (u.contains("i.imgur.com/") || u.contains("imgur.com/")) {
         return true;
      }
      return IMAGE_EXT_PATTERN.matcher(url).find();
   }

   public static String cleanUrl(String url) {
      if (url == null) return null;
      // Strip formatting codes (e.g. §r, §x§f..., §is=... corrupted by section signs)
      String cleaned = url.replaceAll("§[0-9a-fk-orxX]", "").replaceAll("§", "&");
      cleaned = cleaned.replaceAll("[.,!?;:)]+$", "").trim();
      return cleaned;
   }

   public static String extractImageUrl(String text) {
      if (text == null) return null;
      // Clean section signs from text
      String cleanText = text.replaceAll("§[0-9a-fk-orxX]", "").replaceAll("§", "&");
      Matcher matcher = URL_PATTERN.matcher(cleanText);
      while (matcher.find()) {
         String url = cleanUrl(matcher.group());
         if (isImageUrl(url)) {
            return url;
         }
      }
      return null;
   }

   public static String getHoveredImageUrl(Minecraft mc, double mouseX, double mouseY) {
      if (mc.gui == null || mc.gui.getChat() == null || !(mc.gui.getChat() instanceof IChatComponent chatAccessor)) {
         return null;
      }

      GuiMessage.Line line = chatAccessor.bombo$getLineAt(mouseX, mouseY);
      if (line == null) {
         return null;
      }

      double scale = chatAccessor.bombo$getScale();
      Style style = IChatComponent.getStyleAt(mc, line, mouseX, scale);
      if (style != null && style.getClickEvent() != null) {
         ClickEvent click = style.getClickEvent();
         if (click instanceof ClickEvent.OpenUrl openUrl) {
            String url = cleanUrl(openUrl.uri().toString());
            if (isImageUrl(url)) {
               return url;
            }
         } else {
            String clickStr = click.toString();
            String extracted = extractImageUrl(clickStr);
            if (extracted != null) {
               return extracted;
            }
         }
      }

      String plain = IChatComponent.getLinePlainText(line.content());
      String extracted = extractImageUrl(plain);
      if (extracted != null) {
         return extracted;
      }

      java.util.List<GuiMessage.Line> fullLines = chatAccessor.bombo$getFullMessageLines(line);
      StringBuilder fullMsg = new StringBuilder();
      for (GuiMessage.Line l : fullLines) {
         if (fullMsg.length() > 0) fullMsg.append(" ");
         fullMsg.append(IChatComponent.getLinePlainText(l.content()));
      }
      return extractImageUrl(fullMsg.toString());
   }

   public static void fetchImage(String url) {
      if (url == null || TEXTURE_CACHE.containsKey(url) || DOWNLOADING.contains(url)) {
         return;
      }

      DOWNLOADING.add(url);
      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .timeout(Duration.ofSeconds(10L))
            .GET()
            .build();

      HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
         DOWNLOADING.remove(url);
         if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
            byte[] bytes = response.body();
            Minecraft.getInstance().execute(() -> {
               try (InputStream in = new ByteArrayInputStream(bytes)) {
                  NativeImage img = NativeImage.read(in);
                  int w = img.getWidth();
                  int h = img.getHeight();
                  int idNum = ++idCounter;
                  DynamicTexture dynTex = new DynamicTexture(() -> "chat_preview_" + idNum, img);
                  Identifier id = Identifier.fromNamespaceAndPath("bomboaddons", "chat_preview_" + idNum);
                  Minecraft.getInstance().getTextureManager().register(id, dynTex);
                  TEXTURE_CACHE.put(url, new LoadedImage(id, w, h));
               } catch (Throwable t) {
                  System.err.println("[BomboAddons] Failed to decode image from " + url + ": " + t.getMessage());
                  TEXTURE_CACHE.put(url, new LoadedImage(null, 0, 0));
               }
            });
         } else {
            System.err.println("[BomboAddons] Image download HTTP " + response.statusCode() + " for " + url);
            TEXTURE_CACHE.put(url, new LoadedImage(null, 0, 0));
         }
      }).exceptionally(e -> {
         DOWNLOADING.remove(url);
         System.err.println("[BomboAddons] Image download error for " + url + ": " + e.getMessage());
         TEXTURE_CACHE.put(url, new LoadedImage(null, 0, 0));
         return null;
      });
   }

   public static void renderPreview(GuiGraphicsExtractor g, Minecraft mc, int mouseX, int mouseY) {
      String url = getHoveredImageUrl(mc, (double) mouseX, (double) mouseY);
      if (url == null) {
         return;
      }

      if (!TEXTURE_CACHE.containsKey(url)) {
         fetchImage(url);
         // Show small loading tag next to cursor
         String loading = "§7[Loading image preview...]";
         int tw = mc.font.width(loading);
         int renderX = Math.min(mouseX + 12, mc.getWindow().getGuiScaledWidth() - tw - 8);
         int renderY = Math.max(mouseY - 14, 8);
         g.fill(renderX - 2, renderY - 2, renderX + tw + 2, renderY + 11, -1879048192);
         g.text(mc.font, loading, renderX, renderY, -1, true);
         return;
      }

      LoadedImage img = TEXTURE_CACHE.get(url);
      if (img == null || img.id == null) return;

      int maxW = 200;
      int maxH = 150;
      int w = img.width;
      int h = img.height;

      if (w > maxW || h > maxH) {
         double ratio = Math.min((double) maxW / (double) w, (double) maxH / (double) h);
         w = Math.max(1, (int) Math.round(w * ratio));
         h = Math.max(1, (int) Math.round(h * ratio));
      }

      int screenW = mc.getWindow().getGuiScaledWidth();
      int screenH = mc.getWindow().getGuiScaledHeight();

      int previewX = mouseX + 12;
      int previewY = mouseY - h / 2;

      if (previewX + w + 8 > screenW) {
         previewX = mouseX - w - 12;
      }
      if (previewX < 4) {
         previewX = 4;
      }
      if (previewY + h + 8 > screenH) {
         previewY = screenH - h - 8;
      }
      if (previewY < 4) {
         previewY = 4;
      }

      // Draw shadow / background border
      g.fill(previewX - 3, previewY - 3, previewX + w + 3, previewY + h + 3, -16777216);
      g.fill(previewX - 2, previewY - 2, previewX + w + 2, previewY + h + 2, -13421773);
      g.fill(previewX - 1, previewY - 1, previewX + w + 1, previewY + h + 1, -14540254);

      // Render image texture
      g.blit(img.id, previewX, previewY, previewX + w, previewY + h, 0.0F, 1.0F, 0.0F, 1.0F);
   }
}
