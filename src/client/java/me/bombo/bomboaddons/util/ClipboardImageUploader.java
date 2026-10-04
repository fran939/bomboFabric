package me.bombo.bomboaddons.util;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import me.bombo.bomboaddons.DebugUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class ClipboardImageUploader {
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
         .connectTimeout(Duration.ofSeconds(10))
         .build();
   private static final String IMGUR_CLIENT_ID = "546c25a59c58ad7"; // Standard public client ID

   // Cache and in-flight deduplication
   private static final Map<String, String> COMPLETED_LINKS = new ConcurrentHashMap<>();
   private static final Map<String, CompletableFuture<String>> IN_FLIGHT_UPLOADS = new ConcurrentHashMap<>();
   private static final Map<String, String> TAG_TO_HASH = new ConcurrentHashMap<>();
   private static final Map<String, String> HASH_TO_TAG = new ConcurrentHashMap<>();
   private static final java.util.regex.Pattern IMGUR_PATTERN = java.util.regex.Pattern.compile("\\$imgur\\d*");
   private static volatile String latestImageHash = null;

   public static boolean hasClipboardImage() {
      for (int attempt = 0; attempt < 3; attempt++) {
         try {
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable transferable = clipboard.getContents(null);
            if (transferable != null) {
               if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                  return true;
               }
               if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                  try {
                     Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
                     if (data instanceof java.util.List<?> list && !list.isEmpty()) {
                        for (Object obj : list) {
                           if (obj instanceof java.io.File file) {
                              String name = file.getName().toLowerCase();
                              if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp") || name.endsWith(".gif")) {
                                 return true;
                              }
                           }
                        }
                     }
                  } catch (Throwable ignored) {}
               }
               for (DataFlavor flavor : transferable.getTransferDataFlavors()) {
                  if (flavor.isMimeTypeEqual("image/png") || flavor.isMimeTypeEqual("image/jpeg") || flavor.isMimeTypeEqual("image/x-java-image") || flavor.getMimeType().startsWith("image/")) {
                     return true;
                  }
               }
            }
            return false;
         } catch (IllegalStateException e) {
            try { Thread.sleep(20L); } catch (InterruptedException ignored) {}
         } catch (Throwable t) {
            DebugUtils.debug("chat", "§cClipboard check error: " + t.getMessage());
            break;
         }
      }
      return false;
   }

   private static long lastPasteTime = 0L;

   public static boolean tryUploadClipboardImage(EditBox targetInput) {
      if (System.currentTimeMillis() - lastPasteTime < 250L) {
         return true;
      }
      lastPasteTime = System.currentTimeMillis();
      Minecraft mc = Minecraft.getInstance();

      Transferable transferable = null;
      for (int attempt = 0; attempt < 3; attempt++) {
         try {
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            transferable = clipboard.getContents(null);
            if (transferable != null) break;
         } catch (IllegalStateException e) {
            try { Thread.sleep(20L); } catch (InterruptedException ignored) {}
         } catch (Throwable t) {
            break;
         }
      }
      if (transferable == null) {
         return false;
      }

      try {
         Image img = null;
         if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            try {
               Object data = transferable.getTransferData(DataFlavor.imageFlavor);
               if (data instanceof Image i) {
                  img = i;
               }
            } catch (Throwable ignored) {}
         }

         if (img == null && transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
               Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
               if (data instanceof java.util.List<?> list && !list.isEmpty()) {
                  for (Object obj : list) {
                     if (obj instanceof java.io.File file && file.exists()) {
                        BufferedImage bi = ImageIO.read(file);
                        if (bi != null) {
                           img = bi;
                           break;
                        }
                     }
                  }
               }
            } catch (Throwable ignored) {}
         }

         if (img == null) {
            for (DataFlavor flavor : transferable.getTransferDataFlavors()) {
               try {
                  if (flavor.isMimeTypeEqual("image/png") || flavor.isMimeTypeEqual("image/jpeg") || flavor.isMimeTypeEqual("image/x-java-image") || flavor.getMimeType().startsWith("image/")) {
                     Object data = transferable.getTransferData(flavor);
                     if (data instanceof Image i) {
                        img = i;
                        break;
                     } else if (data instanceof InputStream is) {
                        BufferedImage bi = ImageIO.read(is);
                        if (bi != null) {
                           img = bi;
                           break;
                        }
                     }
                  }
               } catch (Throwable ignored) {}
            }
         }

         if (img == null) {
            return false;
         }

         BufferedImage bImg;
         if (img instanceof BufferedImage bi) {
            bImg = bi;
         } else {
            int iw = Math.max(1, img.getWidth(null));
            int ih = Math.max(1, img.getHeight(null));
            bImg = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = bImg.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
         }

         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ImageIO.write(bImg, "png", baos);
         byte[] pngBytes = baos.toByteArray();

         if (pngBytes.length == 0) {
            return false;
         }

         // Compute image hash for deduplication
         String hash = computeHash(pngBytes);
         latestImageHash = hash;

         // Determine which placeholder tag to assign:
         // If this exact image already has a tag in targetInput, reuse it; otherwise pick lowest unused tag
         String currentVal = (targetInput != null) ? targetInput.getValue() : "";
         String chosenTag = HASH_TO_TAG.get(hash);
         if (chosenTag == null || (!currentVal.contains(chosenTag) && TAG_TO_HASH.containsKey(chosenTag) && !hash.equals(TAG_TO_HASH.get(chosenTag)))) {
            int idx = 1;
            while (true) {
               String candidate = (idx == 1) ? "$imgur" : ("$imgur" + idx);
               if (!currentVal.contains(candidate)) {
                  chosenTag = candidate;
                  break;
               }
               idx++;
            }
            HASH_TO_TAG.put(hash, chosenTag);
            TAG_TO_HASH.put(chosenTag, hash);
         } else {
            TAG_TO_HASH.put(chosenTag, hash);
         }

         // Insert placeholder into the input box
         if (targetInput != null) {
            targetInput.insertText(chosenTag);
         }

         // Check if already uploaded
         if (COMPLETED_LINKS.containsKey(hash)) {
            String existingLink = COMPLETED_LINKS.get(hash);
            if (mc.gui != null && mc.gui.hud.getChat() != null) {
               mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §aUsing cached image link for " + chosenTag + ": §b" + existingLink));
            }
            return true;
         }

         // Check if already in-flight
         if (IN_FLIGHT_UPLOADS.containsKey(hash)) {
            if (mc.gui != null && mc.gui.hud.getChat() != null) {
               mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §eImage upload already in progress... (" + chosenTag + " queued)"));
            }
            return true;
         }

         // Start new upload
         CompletableFuture<String> future = new CompletableFuture<>();
         IN_FLIGHT_UPLOADS.put(hash, future);

         if (mc.gui != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §eUploading screenshot (" + (pngBytes.length / 1024) + " KB) to Imgur... (" + chosenTag + " placed)"));
         }

         CompletableFuture.runAsync(() -> {
            try {
               String base64Image = Base64.getEncoder().encodeToString(pngBytes);
               String formBody = "image=" + java.net.URLEncoder.encode(base64Image, java.nio.charset.StandardCharsets.UTF_8) + "&type=base64";

               HttpRequest request = HttpRequest.newBuilder()
                     .uri(URI.create("https://api.imgur.com/3/image"))
                     .header("Authorization", "Client-ID " + IMGUR_CLIENT_ID)
                     .header("Content-Type", "application/x-www-form-urlencoded")
                     .timeout(Duration.ofSeconds(15))
                     .POST(HttpRequest.BodyPublishers.ofString(formBody))
                     .build();

               HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
               if (response.statusCode() == 200 && response.body() != null) {
                  com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                  if (json.has("data") && json.getAsJsonObject("data").has("link")) {
                     String link = json.getAsJsonObject("data").get("link").getAsString();
                     COMPLETED_LINKS.put(hash, link);
                     IN_FLIGHT_UPLOADS.remove(hash);
                     future.complete(link);
                     mc.execute(() -> {
                        if (mc.gui != null && mc.gui.hud.getChat() != null) {
                           mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §aScreenshot uploaded: §b" + link));
                        }
                     });
                     return;
                  }
               }

               // Fallback
               uploadToTmpHost(pngBytes, hash, future, mc);
            } catch (Throwable t) {
               uploadToTmpHost(pngBytes, hash, future, mc);
            }
         });

         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   private static void uploadToTmpHost(byte[] pngBytes, String hash, CompletableFuture<String> future, Minecraft mc) {
      try {
         String boundary = "----WebKitFormBoundary" + Long.toHexString(System.currentTimeMillis());
         ByteArrayOutputStream postData = new ByteArrayOutputStream();
         postData.write(("--" + boundary + "\r\n").getBytes());
         postData.write("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n".getBytes());
         postData.write("fileupload\r\n".getBytes());
         postData.write(("--" + boundary + "\r\n").getBytes());
         postData.write("Content-Disposition: form-data; name=\"time\"\r\n\r\n".getBytes());
         postData.write("72h\r\n".getBytes());
         postData.write(("--" + boundary + "\r\n").getBytes());
         postData.write("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"screenshot.png\"\r\n".getBytes());
         postData.write("Content-Type: image/png\r\n\r\n".getBytes());
         postData.write(pngBytes);
         postData.write(("\r\n--" + boundary + "--\r\n").getBytes());

         HttpRequest request = HttpRequest.newBuilder()
               .uri(URI.create("https://litterbox.catbox.moe/resources/internals/api.php"))
               .header("Content-Type", "multipart/form-data; boundary=" + boundary)
               .timeout(Duration.ofSeconds(15))
               .POST(HttpRequest.BodyPublishers.ofByteArray(postData.toByteArray()))
               .build();

         HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
         if (resp.statusCode() == 200 && resp.body() != null && resp.body().startsWith("http")) {
            String link = resp.body().trim();
            COMPLETED_LINKS.put(hash, link);
            IN_FLIGHT_UPLOADS.remove(hash);
            future.complete(link);
            mc.execute(() -> {
               if (mc.gui != null && mc.gui.hud.getChat() != null) {
                  mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §aScreenshot uploaded (fallback): §b" + link));
               }
            });
            return;
         }
      } catch (Throwable ignored) {}

      IN_FLIGHT_UPLOADS.remove(hash);
      future.completeExceptionally(new RuntimeException("Upload failed"));
      mc.execute(() -> {
         if (mc.gui != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §cFailed to upload screenshot."));
         }
      });
   }

   public static void processOutgoingMessage(String rawMessage, Consumer<String> onSend) {
      if (rawMessage == null || !rawMessage.contains("$imgur")) {
         onSend.accept(rawMessage);
         return;
      }

      java.util.regex.Matcher matcher = IMGUR_PATTERN.matcher(rawMessage);
      java.util.Set<String> tagsFound = new java.util.LinkedHashSet<>();
      while (matcher.find()) {
         tagsFound.add(matcher.group());
      }

      if (tagsFound.isEmpty()) {
         onSend.accept(rawMessage);
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      Map<String, CompletableFuture<String>> futuresToWait = new java.util.HashMap<>();
      Map<String, String> resolvedLinks = new java.util.HashMap<>();

      for (String tag : tagsFound) {
         String hash = TAG_TO_HASH.get(tag);
         if (hash == null) hash = latestImageHash;

         if (hash != null && COMPLETED_LINKS.containsKey(hash)) {
            resolvedLinks.put(tag, COMPLETED_LINKS.get(hash));
         } else if (hash != null && IN_FLIGHT_UPLOADS.containsKey(hash)) {
            futuresToWait.put(tag, IN_FLIGHT_UPLOADS.get(hash));
         }
      }

      if (futuresToWait.isEmpty()) {
         // All already resolved or none pending
         String result = rawMessage;
         for (Map.Entry<String, String> entry : resolvedLinks.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
         }
         onSend.accept(result);
         return;
      }

      if (mc.gui != null && mc.gui.hud.getChat() != null) {
         mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §eWaiting for " + futuresToWait.size() + " image upload(s) to complete... Message queued!"));
      }

      CompletableFuture<?>[] array = futuresToWait.values().toArray(new CompletableFuture[0]);
      CompletableFuture.allOf(array).whenComplete((res, err) -> {
         mc.execute(() -> {
            String result = rawMessage;
            for (Map.Entry<String, String> entry : resolvedLinks.entrySet()) {
               result = result.replace(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, CompletableFuture<String>> entry : futuresToWait.entrySet()) {
               try {
                  String link = entry.getValue().getNow(null);
                  if (link != null) {
                     result = result.replace(entry.getKey(), link);
                  }
               } catch (Throwable ignored) {}
            }
            onSend.accept(result);
         });
      });
   }

   private static String computeHash(byte[] data) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         byte[] digest = md.digest(data);
         StringBuilder sb = new StringBuilder();
         for (byte b : digest) {
            sb.append(String.format("%02x", b));
         }
         return sb.toString();
      } catch (Exception e) {
         return String.valueOf(java.util.Arrays.hashCode(data));
      }
   }
}
