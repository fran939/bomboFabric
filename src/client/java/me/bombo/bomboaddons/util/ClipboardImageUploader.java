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
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
   private static final AtomicBoolean IS_UPLOADING = new AtomicBoolean(false);

   public static boolean hasClipboardImage() {
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
      } catch (Throwable t) {
         DebugUtils.debug("chat", "§cClipboard check error: " + t.getMessage());
      }
      return false;
   }

   public static boolean tryUploadClipboardImage(EditBox targetInput) {
      Minecraft mc = Minecraft.getInstance();
      if (IS_UPLOADING.get()) {
         if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bClipboardDebug§8] §eAlready uploading an image..."));
         }
         return true;
      }

      try {
         java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
         Transferable transferable = clipboard.getContents(null);
         if (transferable == null) {
            if (mc.gui != null && mc.gui.getChat() != null) {
               mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bClipboardDebug§8] §cClipboard contents is null"));
            }
            return false;
         }

         Image img = null;
         if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            try {
               Object data = transferable.getTransferData(DataFlavor.imageFlavor);
               if (data instanceof Image i) {
                  img = i;
               }
            } catch (Throwable t) {
               if (mc.gui != null && mc.gui.getChat() != null) {
                  mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bClipboardDebug§8] §cimageFlavor error: " + t.getMessage()));
               }
            }
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
            if (mc.gui != null && mc.gui.getChat() != null) {
               mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bClipboardDebug§8] §cPNG encoding produced 0 bytes"));
            }
            return false;
         }

         IS_UPLOADING.set(true);
         DebugUtils.debug("chat", "§bScreenshot on clipboard detected, uploading ss to Imgur...");
         if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §eUploading screenshot (" + (pngBytes.length / 1024) + " KB) to Imgur..."));
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

               DebugUtils.debug("chat", "§eContacting Imgur API...");
               HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
               if (response.statusCode() == 200 && response.body() != null) {
                  com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                  if (json.has("data") && json.getAsJsonObject("data").has("link")) {
                     String link = json.getAsJsonObject("data").get("link").getAsString();
                     DebugUtils.debug("chat", "§aGetting link from Imgur: " + link);
                     mc.execute(() -> {
                        if (targetInput != null) {
                           targetInput.insertText(link);
                        }
                        if (mc.gui != null && mc.gui.getChat() != null) {
                           mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §aScreenshot uploaded: §b" + link));
                        }
                     });
                     return;
                  }
               }

               DebugUtils.debug("chat", "§cImgur returned HTTP " + response.statusCode() + ", trying fallback...");
               if (mc.gui != null && mc.gui.getChat() != null) {
                  mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bClipboardDebug§8] §cImgur returned HTTP " + response.statusCode() + ", trying fallback..."));
               }
               // Fallback to catbox / 0x0 / tmpfiles if Imgur fails
               uploadToTmpHost(pngBytes, targetInput, mc);
            } catch (Throwable t) {
               DebugUtils.debug("chat", "§cImgur error: " + t.getMessage() + ", trying fallback...");
               if (mc.gui != null && mc.gui.getChat() != null) {
                  mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bClipboardDebug§8] §cImgur error: " + t.getMessage() + ", trying fallback..."));
               }
               uploadToTmpHost(pngBytes, targetInput, mc);
            } finally {
               IS_UPLOADING.set(false);
            }
         });

         return true;
      } catch (Throwable t) {
         IS_UPLOADING.set(false);
         DebugUtils.debug("chat", "§ctryUploadClipboardImage exception: " + t.getMessage());
         if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bClipboardDebug§8] §ctryUploadClipboardImage exception: " + t.getMessage()));
         }
         return false;
      }
   }

   private static void uploadToTmpHost(byte[] pngBytes, EditBox targetInput, Minecraft mc) {
      try {
         DebugUtils.debug("chat", "§eUploading screenshot to Litterbox fallback...");
         // Fallback to litterbox / catbox
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
            DebugUtils.debug("chat", "§aGetting link from Litterbox: " + link);
            mc.execute(() -> {
               if (targetInput != null) {
                  targetInput.insertText(link);
               }
               if (mc.gui != null && mc.gui.getChat() != null) {
                  mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §aScreenshot uploaded: §b" + link));
               }
            });
            return;
         }
      } catch (Throwable ignored) {
      }

      DebugUtils.debug("chat", "§cFailed to upload screenshot to fallback.");
      mc.execute(() -> {
         if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §cFailed to upload screenshot."));
         }
      });
   }
}
