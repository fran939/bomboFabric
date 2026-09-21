package me.bombo.bomboaddons.features.sounds;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class CustomSoundManager {
    private static final Path SOUNDS_DIR = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("sounds");
    public static final List<String> SUPPORTED_EXTENSIONS = List.of(".wav", ".mp3", ".ogg", ".mp4", ".aiff", ".aif");

    static {
        ensureDirectory();
    }

    public static void ensureDirectory() {
        try {
            if (!Files.exists(SOUNDS_DIR)) {
                Files.createDirectories(SOUNDS_DIR);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static File getSoundsDirectory() {
        ensureDirectory();
        return SOUNDS_DIR.toFile();
    }

    public static List<File> getCustomSoundFiles() {
        ensureDirectory();
        File dir = SOUNDS_DIR.toFile();
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String ext : SUPPORTED_EXTENSIONS) {
                if (lower.endsWith(ext)) return true;
            }
            return false;
        });
        if (files == null) return Collections.emptyList();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return list;
    }

    public static boolean importSoundFile(Path sourcePath) {
        try {
            ensureDirectory();
            String fileName = sourcePath.getFileName().toString();
            String lower = fileName.toLowerCase(Locale.ROOT);
            boolean supported = false;
            for (String ext : SUPPORTED_EXTENSIONS) {
                if (lower.endsWith(ext)) {
                    supported = true;
                    break;
                }
            }
            if (!supported) return false;

            Path target = SOUNDS_DIR.resolve(fileName);
            Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[CustomSoundManager] Failed to import file: " + sourcePath, e);
            return false;
        }
    }

    public static void playCustomOrVanillaSound(String soundName, float volume, float pitch) {
        if (soundName == null || soundName.trim().isEmpty()) return;
        String clean = soundName.trim();

        // 1. Check if sound corresponds to a local custom file in config/bomboaddons/sounds
        File customFile = findCustomFile(clean);
        if (customFile != null && customFile.exists()) {
            playAudioFileAsync(customFile, volume, pitch);
            return;
        }

        // 2. Play vanilla sound event
        Minecraft mc = Minecraft.getInstance();
        try {
            String path = clean.contains(":") ? clean.substring(clean.indexOf(':') + 1) : clean;
            Identifier id = null;
            SoundEvent event = null;

            // Try standard parse
            try {
                id = clean.contains(":") ? Identifier.parse(clean) : Identifier.fromNamespaceAndPath("minecraft", path);
                event = BuiltInRegistries.SOUND_EVENT.getValue(id);
            } catch (Throwable ignored) {}

            // Try converting dots to underscores or underscores to dots if not found
            if (event == null) {
                try {
                    Identifier idUnderscore = Identifier.fromNamespaceAndPath("minecraft", path.replace('.', '_'));
                    SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(idUnderscore);
                    if (ev != null) {
                        event = ev;
                        id = idUnderscore;
                    }
                } catch (Throwable ignored) {}
            }

            if (event == null) {
                try {
                    Identifier idDot = Identifier.fromNamespaceAndPath("minecraft", path.replace('_', '.'));
                    SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(idDot);
                    if (ev != null) {
                        event = ev;
                        id = idDot;
                    }
                } catch (Throwable ignored) {}
            }

            if (id == null) {
                id = Identifier.fromNamespaceAndPath("minecraft", path);
            }

            final SoundEvent finalEvent = event;
            final Identifier finalId = id;
            mc.execute(() -> {
                try {
                    if (finalEvent != null) {
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(finalEvent, pitch, Math.max(0.1f, volume)));
                    } else {
                        SoundEvent customEvent = SoundEvent.createVariableRangeEvent(finalId);
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(customEvent, pitch, Math.max(0.1f, volume)));
                    }
                    if (BomboConfig.get().debugSounds && mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bSound Debug§8] §aPlaying replacement vanilla sound: §e" + clean + " §7(v:" + volume + " p:" + pitch + ")"));
                    }
                } catch (Exception ex) {
                    Bomboaddons.LOGGER.error("[CustomSoundManager] Play failed for " + clean, ex);
                }
            });
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[CustomSoundManager] Could not play sound: " + clean, e);
        }
    }

    private static File findCustomFile(String name) {
        File dir = SOUNDS_DIR.toFile();
        if (!dir.exists()) return null;

        File exact = new File(dir, name);
        if (exact.exists()) return exact;

        for (String ext : SUPPORTED_EXTENSIONS) {
            File withExt = new File(dir, name + ext);
            if (withExt.exists()) return withExt;
        }

        File[] list = dir.listFiles();
        if (list != null) {
            for (File f : list) {
                String fName = f.getName();
                int dotIdx = fName.lastIndexOf('.');
                String base = (dotIdx != -1) ? fName.substring(0, dotIdx) : fName;
                if (base.equalsIgnoreCase(name) || fName.equalsIgnoreCase(name)) {
                    return f;
                }
            }
        }
        return null;
    }

    private static java.lang.invoke.MethodHandle mciSendStringHandle = null;
    private static boolean mciInitAttempted = false;

    private static synchronized java.lang.invoke.MethodHandle getMciHandle() {
        if (!mciInitAttempted) {
            mciInitAttempted = true;
            try {
                java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                java.lang.foreign.SymbolLookup lookup = java.lang.foreign.SymbolLookup.libraryLookup("winmm.dll", java.lang.foreign.Arena.global());
                var sym = lookup.find("mciSendStringA");
                if (sym.isPresent()) {
                    mciSendStringHandle = linker.downcallHandle(
                        sym.get(),
                        java.lang.foreign.FunctionDescriptor.of(
                            java.lang.foreign.ValueLayout.JAVA_INT,
                            java.lang.foreign.ValueLayout.ADDRESS,
                            java.lang.foreign.ValueLayout.ADDRESS,
                            java.lang.foreign.ValueLayout.JAVA_INT,
                            java.lang.foreign.ValueLayout.ADDRESS
                        )
                    );
                }
            } catch (Throwable t) {
                Bomboaddons.LOGGER.warn("[CustomSoundManager] Failed to bind winmm.dll: " + t.getMessage());
            }
        }
        return mciSendStringHandle;
    }

    private static int sendMci(java.lang.invoke.MethodHandle mci, java.lang.foreign.Arena arena, String cmd) {
        try {
            java.lang.foreign.MemorySegment cmdSeg = arena.allocateFrom(cmd);
            return (int) mci.invoke(cmdSeg, java.lang.foreign.MemorySegment.NULL, 0, java.lang.foreign.MemorySegment.NULL);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean playWithWinmm(File file, float volume) {
        java.lang.invoke.MethodHandle mci = getMciHandle();
        if (mci == null) return false;
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            String alias = "bombo_snd_" + (System.nanoTime() % 1000000);
            String path = file.getAbsolutePath().replace('\\', '/');

            sendMci(mci, arena, "close " + alias);
            int openRes = sendMci(mci, arena, "open \"" + path + "\" type mpegvideo alias " + alias);
            if (openRes != 0) {
                openRes = sendMci(mci, arena, "open \"" + path + "\" alias " + alias);
            }
            if (openRes == 0) {
                int mciVol = Math.max(0, Math.min(1000, (int)(volume * 1000)));
                sendMci(mci, arena, "setaudio " + alias + " volume to " + mciVol);
                sendMci(mci, arena, "play " + alias + " from 0");

                new Thread(() -> {
                    try {
                        Thread.sleep(8000);
                    } catch (InterruptedException ignored) {}
                    try (java.lang.foreign.Arena closeArena = java.lang.foreign.Arena.ofConfined()) {
                        sendMci(mci, closeArena, "close " + alias);
                    } catch (Throwable ignored) {}
                }, "Winmm-Cleanup").start();
                return true;
            }
        } catch (Throwable t) {
            Bomboaddons.LOGGER.warn("[CustomSoundManager] winmm error: " + t.getMessage());
        }
        return false;
    }

    public static void playAudioFileAsync(File file, float volume, float pitch) {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            String nameLower = file.getName().toLowerCase(Locale.ROOT);
            boolean played = false;

            // 1. Try Java standard AudioSystem (works for .wav, .aiff, .aif, .au)
            if (nameLower.endsWith(".wav") || nameLower.endsWith(".aiff") || nameLower.endsWith(".aif") || nameLower.endsWith(".au")) {
                try {
                    AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioStream);
                    try {
                        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                        float dB = (float) (Math.log10(Math.max(0.0001f, volume)) * 20.0);
                        dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
                        gainControl.setValue(dB);
                    } catch (Exception ignored) {}
                    clip.start();
                    played = true;
                } catch (Exception e) {
                    Bomboaddons.LOGGER.warn("[CustomSoundManager] AudioSystem failed for " + file.getName() + ": " + e.getMessage());
                }
            }

            // 2. Instant native Windows MCI playback for .mp3, etc.
            if (!played) {
                String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
                if (os.contains("win")) {
                    played = playWithWinmm(file, volume);
                    if (!played) {
                        // Fallback to powershell only if winmm fails
                        try {
                            String escapedPath = file.getAbsolutePath().replace("'", "''");
                            float safeVol = Math.min(1.0f, Math.max(0.01f, volume));
                            String psCmd = "$p = New-Object System.Windows.Media.MediaPlayer; $p.Volume = " + safeVol + "; $p.Open([System.Uri]'" + escapedPath + "'); $p.Play(); Start-Sleep -Seconds 6";
                            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", "Add-Type -AssemblyName PresentationCore; " + psCmd).start();
                            played = true;
                        } catch (Exception ignored) {}
                    }
                } else if (os.contains("mac")) {
                    try {
                        new ProcessBuilder("afplay", "-v", String.valueOf(volume), file.getAbsolutePath()).start();
                        played = true;
                    } catch (Exception ignored) {}
                } else {
                    try {
                        new ProcessBuilder("ffplay", "-nodisp", "-autoexit", "-volume", String.valueOf((int)(volume * 100)), file.getAbsolutePath()).start();
                        played = true;
                    } catch (Exception ignored) {}
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            if (BomboConfig.get().debugSounds) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§8[§bSound Debug§8] §aSound §e" + file.getName() + " §aplayed in §b" + elapsed + "ms§a."));
                }
            }
        }, "CustomSound-Player").start();
    }

    public static String getReplacement(String soundId) {
        if (soundId == null || soundId.trim().isEmpty()) return null;
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.customSoundReplacements != null && !s.customSoundReplacements.isEmpty()) {
            String clean = soundId.trim();
            String rawId = clean.startsWith("minecraft:") ? clean.substring(10) : clean;
            String dotId = rawId.replace('_', '.');
            String underId = rawId.replace('.', '_');

            String[] candidates = new String[]{clean, rawId, "minecraft:" + rawId, dotId, "minecraft:" + dotId, underId, "minecraft:" + underId};
            for (String cand : candidates) {
                if (s.customSoundReplacements.containsKey(cand)) {
                    if (s.disabledCustomSoundReplacements != null && s.disabledCustomSoundReplacements.contains(cand)) {
                        return null;
                    }
                    return s.customSoundReplacements.get(cand);
                }
            }

            // Check case-insensitive and partial key aliases
            for (var entry : s.customSoundReplacements.entrySet()) {
                String k = entry.getKey().trim();
                if (s.disabledCustomSoundReplacements != null && s.disabledCustomSoundReplacements.contains(k)) {
                    continue;
                }
                String kRaw = k.startsWith("minecraft:") ? k.substring(10) : k;
                if (kRaw.equalsIgnoreCase(rawId) || kRaw.equalsIgnoreCase(dotId) || kRaw.equalsIgnoreCase(underId)) {
                    return entry.getValue();
                }
                // Handle dragon hurt aliases e.g. entity.ender_dragon.hurt vs mob.enderdragon.hit
                if ((kRaw.contains("ender_dragon.hurt") || kRaw.contains("enderdragon.hurt") || kRaw.contains("dragon.hurt"))
                        && (rawId.contains("ender_dragon.hurt") || rawId.contains("enderdragon.hit") || rawId.contains("enderdragon/hit") || rawId.contains("ender_dragon/hit"))) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}
