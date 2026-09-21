package me.bombo.bomboaddons.util;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.bombo.bomboaddons.Bomboaddons;
import net.minecraft.client.Minecraft;

public class ResourcePackHelper {

   public static void cleanTempFiles(File packsDir) {
      if (packsDir == null || !packsDir.isDirectory()) return;
      File[] files = packsDir.listFiles((dir, name) -> name.startsWith("zipfstmp") && name.endsWith(".tmp"));
      if (files != null) {
         for (File f : files) {
            try {
               f.delete();
            } catch (Throwable ignored) {}
         }
      }
   }

   public static void patchPackFormat(File packFile) {
      if (packFile == null || !packFile.exists() || !packFile.getName().endsWith(".zip")) return;
      cleanTempFiles(packFile.getParentFile());

      File tempOut = null;
      try {
         tempOut = File.createTempFile("bombo_pack_patch_", ".zip");
         boolean patched = false;

         try (java.util.zip.ZipFile zipIn = new java.util.zip.ZipFile(packFile);
              java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(tempOut))) {
            
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipIn.entries();
            while (entries.hasMoreElements()) {
               java.util.zip.ZipEntry entry = entries.nextElement();
               java.util.zip.ZipEntry newEntry = new java.util.zip.ZipEntry(entry.getName());
               zos.putNextEntry(newEntry);

               if ("pack.mcmeta".equalsIgnoreCase(entry.getName())) {
                  try (java.io.InputStream is = zipIn.getInputStream(entry)) {
                     String original = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                     String patchedJson = "{\n  \"pack\": {\n    \"pack_format\": 46,\n    \"supported_formats\": {\n      \"min_inclusive\": 1,\n      \"max_inclusive\": 999\n    },\n    \"min_format\": 1,\n    \"max_format\": 999,\n    \"description\": \"Hypixel SkyBlock\"\n  }\n}";
                     zos.write(patchedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                     patched = true;
                  }
               } else {
                  try (java.io.InputStream is = zipIn.getInputStream(entry)) {
                     is.transferTo(zos);
                  }
               }
               zos.closeEntry();
            }
         }

         if (patched && tempOut.length() > 0) {
            Files.move(tempOut.toPath(), packFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[Bombo] Successfully patched " + packFile.getName() + " with universal 26.2 compatibility.");
         }
      } catch (Throwable t) {
         System.err.println("[Bombo] Failed to patch pack.mcmeta for " + packFile.getName() + ": " + t.getMessage());
      } finally {
         if (tempOut != null && tempOut.exists()) {
            tempOut.delete();
         }
         cleanTempFiles(packFile.getParentFile());
      }
   }

   public static void enableSkyblockPack() {
      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> {
         try {
            File packsDir = new File(mc.gameDirectory, "resourcepacks");
            cleanTempFiles(packsDir);
            File packFile = new File(packsDir, "Hypixel_Skyblock.zip");
            if (packFile.exists()) {
               patchPackFormat(packFile);
            }
            File sbPack = new File(packsDir, "SbTexturePack_v2.3.zip");
            if (sbPack.exists()) {
               patchPackFormat(sbPack);
            }

            var repo = mc.getResourcePackRepository();
            repo.reload();
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            boolean added = false;
            for (String id : repo.getAvailableIds()) {
               if (id.toLowerCase().contains("hypixel_skyblock") || id.toLowerCase().contains("skyblock")) {
                  if (!selected.contains(id)) {
                     selected.add(id);
                     added = true;
                  }
               }
            }
            if (added) {
               repo.setSelected(selected);
               mc.options.updateResourcePacks(repo);
               mc.reloadResourcePacks();
               Bomboaddons.sendMessage("§a[Bombo] Hypixel Skyblock resource pack automatically enabled!");
            }
         } catch (Throwable t) {
            t.printStackTrace();
         }
      });
   }
}
