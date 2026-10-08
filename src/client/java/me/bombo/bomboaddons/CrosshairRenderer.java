package me.bombo.bomboaddons;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Renders the user-designed crosshair.
 *
 * <p>Pixels are laid out on an integer cell grid so that neighbouring lit cells share an edge
 * exactly — no sub-pixel seams from float truncation. A PNG/JPG can be used instead of the
 * pixel grid; it keeps its original resolution and is drawn at an integer scale (no stretching,
 * no blur).
 */
public class CrosshairRenderer {
   private static final Identifier IMAGE_TEXTURE_ID =
         Identifier.fromNamespaceAndPath("bomboaddons", "custom_crosshair_image");

   private static DynamicTexture imageTexture = null;
   private static String loadedImagePath = "";
   private static int imageWidth = 0;
   private static int imageHeight = 0;

   public static void render(GuiGraphicsExtractor graphics) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.options.keyToggleGui.isDown()) {
         return;
      }
      Player player = mc.player;
      if (player == null || mc.level == null) {
         return;
      }
      BomboConfig.CrosshairSettings settings = BomboConfig.get().customCrosshair;
      if (settings == null || !settings.enabled) {
         return;
      }

      int screenWidth = mc.getWindow().getGuiScaledWidth();
      int screenHeight = mc.getWindow().getGuiScaledHeight();

      if (settings.useImage) {
         if (ensureImageLoaded(settings)) {
            renderImage(graphics, screenWidth, screenHeight, settings);
         }
         return;
      }
      renderGrid(graphics, screenWidth, screenHeight, settings);
   }

   // ---------------------------------------------------------------------------------------------
   // Pixel-grid crosshair
   // ---------------------------------------------------------------------------------------------
   private static void renderGrid(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight,
         BomboConfig.CrosshairSettings settings) {
      boolean[] grid = settings.grid;
      if (grid == null || grid.length != 225) {
         return;
      }

      float scale = settings.scale > 0.0F ? settings.scale : 1.0F;
      int cell = Math.max(1, Math.round(scale));

      int originX = (screenWidth - 15 * cell) / 2;
      int originY = (screenHeight - 15 * cell) / 2;

      int mainColor = getColorValue(settings.color, settings.chroma);
      int outlineColor = getColorValue(settings.outlineColor, false);
      int ox = settings.outline ? Math.max(1, Math.round(cell * 0.28F)) : 0;

      // Outline pass (drawn first so neighbouring fills cover the internal borders, leaving only
      // a clean outer border around the merged shape).
      if (ox > 0) {
         for (int row = 0; row < 15; row++) {
            for (int col = 0; col < 15; col++) {
               if (!grid[row * 15 + col]) continue;
               int x = originX + col * cell;
               int y = originY + row * cell;
               graphics.fill(x - ox, y - ox, x + cell + ox, y + cell + ox, outlineColor);
            }
         }
      }

      // Fill pass — adjacent cells share the exact edge x+cell == next cell's x.
      for (int row = 0; row < 15; row++) {
         for (int col = 0; col < 15; col++) {
            if (!grid[row * 15 + col]) continue;
            int x = originX + col * cell;
            int y = originY + row * cell;
            graphics.fill(x, y, x + cell, y + cell, mainColor);
         }
      }
   }

   // ---------------------------------------------------------------------------------------------
   // Image crosshair
   // ---------------------------------------------------------------------------------------------
   private static void renderImage(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight,
         BomboConfig.CrosshairSettings settings) {
      float scale = settings.imageScale > 0.0F ? settings.imageScale : 1.0F;
      int factor = Math.max(1, Math.round(scale));
      int drawW = Math.max(1, imageWidth * factor);
      int drawH = Math.max(1, imageHeight * factor);
      int x = (screenWidth - drawW) / 2;
      int y = (screenHeight - drawH) / 2;
      int tint = settings.imageTint ? getColorValue(settings.color, settings.chroma) : 0xFFFFFFFF;

      graphics.blit(RenderPipelines.GUI_TEXTURED, IMAGE_TEXTURE_ID,
            x, y, 0.0F, 0.0F, drawW, drawH, drawW, drawH, tint);
   }

   private static boolean ensureImageLoaded(BomboConfig.CrosshairSettings settings) {
      String path = settings.imagePath != null ? settings.imagePath.trim() : "";
      if (path.isEmpty()) {
         return false;
      }
      if (!path.equals(loadedImagePath)) {
         loadedImagePath = path;
         unloadImage();
         try {
            Path resolved = resolveImagePath(path);
            if (resolved != null && Files.exists(resolved)) {
               byte[] bytes = Files.readAllBytes(resolved);
               try (InputStream in = new ByteArrayInputStream(bytes)) {
                  NativeImage img = NativeImage.read(in);
                  if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                     imageWidth = img.getWidth();
                     imageHeight = img.getHeight();
                     imageTexture = new DynamicTexture(() -> "bombo_crosshair_image", img);
                     Minecraft.getInstance().getTextureManager().register(IMAGE_TEXTURE_ID, imageTexture);
                  }
               }
            }
         } catch (Throwable ignored) {
            unloadImage();
         }
      }
      return imageTexture != null;
   }

   private static void unloadImage() {
      if (imageTexture != null) {
         try {
            imageTexture.close();
         } catch (Throwable ignored) {
         }
         imageTexture = null;
      }
      imageWidth = 0;
      imageHeight = 0;
   }

   /** Force a reload on the next render (e.g. after the path or file changed). */
   public static void invalidateImageCache() {
      loadedImagePath = "";
      unloadImage();
   }

   public static boolean hasLoadedImage() {
      return imageTexture != null && imageWidth > 0 && imageHeight > 0;
   }

   public static Identifier getImageTextureId() {
      return IMAGE_TEXTURE_ID;
   }

   /** Integer-scaled fit of the loaded image inside {@code maxPx}, preserving aspect ratio. */
   public static int[] getImagePreviewSize(int maxPx) {
      if (imageWidth <= 0 || imageHeight <= 0) {
         return new int[]{1, 1};
      }
      int factor = Math.max(1, Math.min(maxPx / Math.max(1, imageWidth), maxPx / Math.max(1, imageHeight)));
      return new int[]{imageWidth * factor, imageHeight * factor};
   }

   public static Path getCrosshairImageDirectory() {
      return FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("crosshairs");
   }

   public static Path resolveImagePath(String path) {
      if (path == null || path.trim().isEmpty()) {
         return null;
      }
      String trimmed = path.trim();
      java.io.File direct = new java.io.File(trimmed);
      if (direct.isAbsolute()) {
         return direct.toPath();
      }
      Path inConfig = getCrosshairImageDirectory().resolve(trimmed);
      if (Files.exists(inConfig)) {
         return inConfig;
      }
      try {
         Path inGameDir = Minecraft.getInstance().gameDirectory.toPath().resolve(trimmed);
         if (Files.exists(inGameDir)) {
            return inGameDir;
         }
      } catch (Throwable ignored) {
      }
      return inConfig;
   }

   /**
    * Opens the OS file chooser on a worker thread, copies the chosen image into
    * {@code config/bomboaddons/crosshairs/} and hands the resulting path back on the render thread.
    */
   public static void browseForCrosshairImage(java.util.function.Consumer<String> onChosen) {
      Thread worker = new Thread(() -> {
         try {
            java.awt.Frame owner = new java.awt.Frame();
            owner.setAlwaysOnTop(true);
            java.awt.FileDialog dialog = new java.awt.FileDialog(owner, "Select Crosshair Image", java.awt.FileDialog.LOAD);
            dialog.setFilenameFilter((dir, name) -> {
               String n = name.toLowerCase();
               return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                     || n.endsWith(".gif") || n.endsWith(".bmp");
            });
            dialog.setVisible(true);
            String chosen = dialog.getFile();
            String dir = dialog.getDirectory();
            owner.dispose();
            if (chosen == null || dir == null) {
               return;
            }
            Path source = new java.io.File(dir, chosen).toPath();
            Path destDir = getCrosshairImageDirectory();
            Files.createDirectories(destDir);
            Path dest = destDir.resolve(chosen);
            Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Minecraft.getInstance().execute(() -> onChosen.accept(dest.toString()));
         } catch (Throwable ignored) {
         }
      }, "bombo-crosshair-file-picker");
      worker.setDaemon(true);
      worker.start();
   }

   public static int getColorValue(String colorName, boolean chroma) {
      if (chroma) {
         long time = System.currentTimeMillis();
         return Color.HSBtoRGB((float)(time % 2000L) / 2000.0F, 0.8F, 1.0F) | -16777216;
      } else if (colorName == null) {
         return -1;
      } else {
         switch (colorName.toUpperCase()) {
            case "WHITE" -> {
               return -1;
            }
            case "BLACK" -> {
               return -16777216;
            }
            case "RED" -> {
               return -43691;
            }
            case "GREEN" -> {
               return -11141291;
            }
            case "BLUE" -> {
               return -11184641;
            }
            case "YELLOW" -> {
               return -171;
            }
            case "AQUA" -> {
               return -11141121;
            }
            case "PURPLE" -> {
               return -43521;
            }
            case "GOLD" -> {
               return -22016;
            }
            case "GRAY" -> {
               return -5592406;
            }
            case "DARK_GRAY" -> {
               return -11184811;
            }
            default -> {
               return -1;
            }
         }
      }
   }
}
