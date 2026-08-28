package me.bombo.bomboaddons.util;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ChatImagePreview {
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
         .connectTimeout(Duration.ofSeconds(8L))
         .followRedirects(HttpClient.Redirect.ALWAYS)
         .build();
   private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
   private static final Pattern IMAGE_EXT_PATTERN = Pattern.compile("(?i)\\.(png|jpg|jpeg|webp|gif)(\\?.*)?$");
   
   private static final Map<String, LoadedImage> TEXTURE_CACHE = new ConcurrentHashMap<>();
   private static final Set<String> DOWNLOADING = ConcurrentHashMap.newKeySet();
   private static int idCounter = 0;

   public static class GifFrame {
      public final Identifier id;
      public final int delayMs;

      public GifFrame(Identifier id, int delayMs) {
         this.id = id;
         this.delayMs = delayMs;
      }
   }

   public static class LoadedImage {
      public final Identifier id;
      public final int width;
      public final int height;
      public final List<GifFrame> frames;
      public final int totalDurationMs;

      public LoadedImage(Identifier id, int width, int height) {
         this(id, width, height, null, 0);
      }

      public LoadedImage(Identifier id, int width, int height, List<GifFrame> frames, int totalDurationMs) {
         this.id = id;
         this.width = width;
         this.height = height;
         this.frames = frames;
         this.totalDurationMs = totalDurationMs;
      }

      public Identifier getActiveTextureId() {
         if (frames == null || frames.isEmpty() || totalDurationMs <= 0) {
            return id;
         }
         long now = System.currentTimeMillis() % totalDurationMs;
         long cumulative = 0;
         for (GifFrame frame : frames) {
            cumulative += frame.delayMs;
            if (now < cumulative) {
               return frame.id;
            }
         }
         return frames.get(0).id;
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
      if (u.contains("media.essential.gg/") || u.contains("essential.gg/")) {
         return true;
      }
      if (u.contains("tenor.com/") || u.contains("media.tenor.com/")) {
         return true;
      }
      if (u.contains("giphy.com/") || u.contains("media.giphy.com/")) {
         return true;
      }
      if (u.contains("klipy.com/gifs/") || u.contains("klipy.com/")) {
         return true;
      }
      return IMAGE_EXT_PATTERN.matcher(url).find();
   }

   public static String cleanUrl(String url) {
      if (url == null) return null;
      String cleaned = url.replaceAll("§[0-9a-fk-orxX]", "").replaceAll("§", "&");
      cleaned = cleaned.replaceAll("\\s+", "");
      cleaned = cleaned.replaceAll("[.,!?;:)\\]]+$", "").trim();
      return cleaned;
   }

   public static String extractImageUrl(String text) {
      if (text == null) return null;
      String cleanText = text.replaceAll("§[0-9a-fk-orxX]", "").replaceAll("§", "&");
      Matcher matcher = URL_PATTERN.matcher(cleanText);
      while (matcher.find()) {
         String url = cleanUrl(matcher.group());
         if (isImageUrl(url)) {
            return url;
         }
      }
      // Re-try after removing spaces in discord / cdn links
      if (cleanText.contains("cdn.discordapp.com") || cleanText.contains("media.discordapp.net") || cleanText.contains("klipy.com")) {
         int idx = cleanText.indexOf("https://");
         if (idx == -1) idx = cleanText.indexOf("http://");
         if (idx != -1) {
            String sub = cleanText.substring(idx).replaceAll("\\s+", "");
            Matcher m2 = URL_PATTERN.matcher(sub);
            if (m2.find()) {
               String url = cleanUrl(m2.group());
               if (isImageUrl(url)) {
                  return url;
               }
            }
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

      java.util.List<GuiMessage> allMsgs = chatAccessor.bombo$getAllMessages();
      if (allMsgs != null) {
         for (GuiMessage m : allMsgs) {
            if (m.addedTime() == line.addedTime()) {
               String extracted = extractImageUrl(m.content().getString());
               if (extracted != null) {
                  return extracted;
               }
            }
         }
      }

      String plain = IChatComponent.getLinePlainText(line.content());
      String extracted = extractImageUrl(plain);
      if (extracted != null) {
         return extracted;
      }

      java.util.List<GuiMessage.Line> fullLines = chatAccessor.bombo$getFullMessageLines(line);
      StringBuilder fullMsgWithSpace = new StringBuilder();
      StringBuilder fullMsgNoSpace = new StringBuilder();
      for (GuiMessage.Line l : fullLines) {
         String t = IChatComponent.getLinePlainText(l.content());
         if (fullMsgWithSpace.length() > 0) fullMsgWithSpace.append(" ");
         fullMsgWithSpace.append(t);
         fullMsgNoSpace.append(t);
      }
      String res = extractImageUrl(fullMsgWithSpace.toString());
      if (res != null) return res;
      return extractImageUrl(fullMsgNoSpace.toString());
   }

   public static void fetchImage(String url) {
      if (url == null || TEXTURE_CACHE.containsKey(url) || DOWNLOADING.contains(url)) {
         return;
      }

      DOWNLOADING.add(url);

      if (url.contains("klipy.com")) {
         // Direct slug extraction: https://klipy.com/gifs/caine-7 -> https://static.klipy.com/gifs/caine-7.gif
         int slugIdx = url.indexOf("klipy.com/gifs/");
         if (slugIdx != -1) {
            String slug = url.substring(slugIdx + 15).replaceAll("[?#].*", "").replaceAll("/$", "");
            if (!slug.isEmpty()) {
               downloadDirectImage(url, "https://static.klipy.com/gifs/" + slug + ".gif");
               return;
            }
         }
      }

      // If URL is a Tenor / Giphy / Klipy web page, fetch the page HTML first to extract the direct GIF/WebP URL
      if ((url.contains("tenor.com/view/") || url.contains("giphy.com/gifs/") || url.contains("klipy.com")) && !IMAGE_EXT_PATTERN.matcher(url).find()) {
         HttpRequest pageReq = HttpRequest.newBuilder()
               .uri(URI.create(url))
               .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
               .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
               .timeout(Duration.ofSeconds(10L))
               .GET()
               .build();

         HTTP_CLIENT.sendAsync(pageReq, HttpResponse.BodyHandlers.ofString()).thenAccept(pageResp -> {
            String directUrl = null;
            if (pageResp.statusCode() == 200 && pageResp.body() != null) {
               String html = pageResp.body();
               // Look for og:image, og:video, or twitter:image
               Matcher metaMatcher = Pattern.compile("<meta[^>]+(?:property|name)=[\"'](?:og:image|og:video|og:image:secure_url|twitter:image)[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
               if (metaMatcher.find()) {
                  directUrl = metaMatcher.group(1);
               }
               if (directUrl == null) {
                  Matcher m = Pattern.compile("https?://[^\"'\\s<>]+\\.(?:gif|png|webp|jpg|jpeg)(?:\\?[^\"'\\s<>]*)?", Pattern.CASE_INSENSITIVE).matcher(html);
                  while (m.find()) {
                     String found = m.group();
                     if (found.contains("media.tenor.com") || found.contains("media.giphy.com") || found.contains("static.klipy.com") || found.contains("klipy") || found.endsWith(".gif")) {
                        directUrl = found;
                        break;
                     }
                  }
               }
               if (directUrl == null && url.contains("klipy.com/gifs/")) {
                  String slug = url.substring(url.indexOf("klipy.com/gifs/") + 15).replaceAll("[?#].*", "").replaceAll("/$", "");
                  if (!slug.isEmpty()) {
                     directUrl = "https://static.klipy.com/gifs/" + slug + ".gif";
                  }
               }
            }
            if (directUrl != null) {
               downloadDirectImage(url, directUrl);
            } else {
               downloadDirectImage(url, url);
            }
         }).exceptionally(e -> {
            DOWNLOADING.remove(url);
            TEXTURE_CACHE.put(url, new LoadedImage(null, 0, 0));
            return null;
         });
         return;
      }

      downloadDirectImage(url, url);
   }

   private static void downloadDirectImage(String originalUrl, String fetchUrl) {
      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(fetchUrl))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .timeout(Duration.ofSeconds(10L))
            .GET()
            .build();

      HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
         DOWNLOADING.remove(originalUrl);
         if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
            byte[] bytes = response.body();
            Minecraft.getInstance().execute(() -> {
               try {
                  // Check if it's an animated GIF by attempting to read with GIF ImageReader
                  List<GifFrame> frames = new ArrayList<>();
                  int totalDur = 0;
                  int firstW = 0;
                  int firstH = 0;
                  Identifier firstId = null;

                  Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                  if (readers.hasNext()) {
                     ImageReader reader = readers.next();
                     try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                        reader.setInput(iis, false);
                        int numFrames = 0;
                        try {
                           numFrames = reader.getNumImages(true);
                        } catch (Throwable ignored) {
                           numFrames = 0;
                        }

                        if (numFrames > 1) {
                           for (int i = 0; i < Math.min(numFrames, 120); i++) { // cap at 120 frames
                              BufferedImage bi = reader.read(i);
                              if (bi != null) {
                                 int delay = 100; // default 100ms
                                 try {
                                    IIOMetadata metadata = reader.getImageMetadata(i);
                                    if (metadata != null) {
                                       String[] names = metadata.getMetadataFormatNames();
                                       for (String name : names) {
                                          Node root = metadata.getAsTree(name);
                                          if (root instanceof IIOMetadataNode metaNode) {
                                             NodeList gceList = metaNode.getElementsByTagName("GraphicControlExtension");
                                             if (gceList != null && gceList.getLength() > 0) {
                                                IIOMetadataNode gce = (IIOMetadataNode) gceList.item(0);
                                                String delayStr = gce.getAttribute("delayTime");
                                                if (delayStr != null && !delayStr.isEmpty()) {
                                                   int d = Integer.parseInt(delayStr) * 10; // delay in 1/100s -> ms
                                                   if (d > 0) delay = d;
                                                }
                                             }
                                          }
                                       }
                                    }
                                 } catch (Throwable ignored) {}

                                 ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                                 ImageIO.write(bi, "png", pngOut);
                                 try (InputStream pngIn = new ByteArrayInputStream(pngOut.toByteArray())) {
                                    NativeImage nImg = NativeImage.read(pngIn);
                                    if (nImg != null) {
                                       if (firstW == 0) {
                                          firstW = nImg.getWidth();
                                          firstH = nImg.getHeight();
                                       }
                                       int idNum = ++idCounter;
                                       DynamicTexture dynTex = new DynamicTexture(() -> "chat_preview_" + idNum, nImg);
                                       Identifier id = Identifier.fromNamespaceAndPath("bomboaddons", "chat_preview_" + idNum);
                                       Minecraft.getInstance().getTextureManager().register(id, dynTex);
                                       frames.add(new GifFrame(id, delay));
                                       totalDur += delay;
                                       if (firstId == null) {
                                          firstId = id;
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     } catch (Throwable ignored) {}
                     finally {
                        try { reader.dispose(); } catch (Throwable ignored) {}
                     }
                  }

                  if (frames.size() > 1 && firstId != null) {
                     TEXTURE_CACHE.put(originalUrl, new LoadedImage(firstId, firstW, firstH, frames, totalDur));
                     return;
                  }

                  // Fallback to static single frame decoding
                  NativeImage img = null;
                  try (InputStream in = new ByteArrayInputStream(bytes)) {
                     img = NativeImage.read(in);
                  } catch (Throwable t) {
                     // NativeImage failed, fallback to Java ImageIO
                     try (InputStream in2 = new ByteArrayInputStream(bytes)) {
                        BufferedImage bimg = ImageIO.read(in2);
                        if (bimg != null) {
                           ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                           ImageIO.write(bimg, "png", pngOut);
                           try (InputStream in3 = new ByteArrayInputStream(pngOut.toByteArray())) {
                              img = NativeImage.read(in3);
                           }
                        }
                     }
                  }

                  if (img != null) {
                     int w = img.getWidth();
                     int h = img.getHeight();
                     int idNum = ++idCounter;
                     DynamicTexture dynTex = new DynamicTexture(() -> "chat_preview_" + idNum, img);
                     Identifier id = Identifier.fromNamespaceAndPath("bomboaddons", "chat_preview_" + idNum);
                     Minecraft.getInstance().getTextureManager().register(id, dynTex);
                     TEXTURE_CACHE.put(originalUrl, new LoadedImage(id, w, h));
                  } else {
                     TEXTURE_CACHE.put(originalUrl, new LoadedImage(null, 0, 0));
                  }
               } catch (Throwable t) {
                  System.err.println("[BomboAddons] Failed to decode image from " + originalUrl + ": " + t.getMessage());
                  TEXTURE_CACHE.put(originalUrl, new LoadedImage(null, 0, 0));
               }
            });
         } else {
            System.err.println("[BomboAddons] Image download HTTP " + response.statusCode() + " for " + originalUrl);
            TEXTURE_CACHE.put(originalUrl, new LoadedImage(null, 0, 0));
         }
      }).exceptionally(e -> {
         DOWNLOADING.remove(originalUrl);
         System.err.println("[BomboAddons] Image download error for " + originalUrl + ": " + e.getMessage());
         TEXTURE_CACHE.put(originalUrl, new LoadedImage(null, 0, 0));
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

      int screenW = mc.getWindow().getGuiScaledWidth();
      int screenH = mc.getWindow().getGuiScaledHeight();
      boolean isShift = com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), 340) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), 344);

      int maxW = isShift ? Math.max(100, screenW - 40) : Math.min(480, screenW - 30);
      int maxH = isShift ? Math.max(100, screenH - 40) : Math.min(340, screenH - 30);
      int w = img.width;
      int h = img.height;

      if (w > maxW || h > maxH) {
         double ratio = Math.min((double) maxW / (double) w, (double) maxH / (double) h);
         w = Math.max(1, (int) Math.round(w * ratio));
         h = Math.max(1, (int) Math.round(h * ratio));
      }

      int previewX;
      int previewY;

      if (isShift) {
         previewX = (screenW - w) / 2;
         previewY = (screenH - h) / 2;
         g.fill(0, 0, screenW, screenH, 0xB0000000);
      } else {
         previewX = mouseX + 12;
         previewY = mouseY - h / 2;

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
      }

      // Draw shadow / background border
      g.fill(previewX - 3, previewY - 3, previewX + w + 3, previewY + h + 3, -16777216);
      g.fill(previewX - 2, previewY - 2, previewX + w + 2, previewY + h + 2, -13421773);
      g.fill(previewX - 1, previewY - 1, previewX + w + 1, previewY + h + 1, -14540254);

      // Render image texture (animated if frames are present)
      Identifier textureToRender = img.getActiveTextureId();
      g.blit(textureToRender, previewX, previewY, previewX + w, previewY + h, 0.0F, 1.0F, 0.0F, 1.0F);
   }
}
