/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.reflect.TypeToken
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.class_1657
 *  net.minecraft.class_1713
 *  net.minecraft.class_1735
 *  net.minecraft.class_1792$class_9635
 *  net.minecraft.class_1799
 *  net.minecraft.class_1836
 *  net.minecraft.class_1836$class_1837
 *  net.minecraft.class_1937
 *  net.minecraft.class_2371
 *  net.minecraft.class_2561
 *  net.minecraft.class_310
 *  net.minecraft.class_437
 *  net.minecraft.class_465
 */
package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.bombo.bomboaddons.BomboConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1657;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1836;
import net.minecraft.class_1937;
import net.minecraft.class_2371;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_437;
import net.minecraft.class_465;

@Environment(value=EnvType.CLIENT)
public class ClickLogic {
    private static List<ClickTarget> targets;
    private static boolean debugMode;
    private static final Gson GSON;
    private static final File CONFIG_FILE;
    private static final Map<String, Integer> KEY_MAP;

    public static void setTarget(String item, String gui, String keyName, String type, boolean auto) {
        int keyCode = KEY_MAP.getOrDefault(keyName.toLowerCase(), -1);
        targets.add(new ClickTarget(item, gui, keyName, keyCode, type, auto));
        ClickLogic.saveTargets();
    }

    public static List<ClickTarget> getTargets() {
        return targets;
    }

    public static void removeTarget(int index) {
        if (index >= 0 && index < targets.size()) {
            targets.remove(index);
            ClickLogic.saveTargets();
        }
    }

    public static void removeTargetById(String idString) {
        try {
            UUID id = UUID.fromString(idString);
            targets.removeIf(t -> t.id.equals(id));
            ClickLogic.saveTargets();
        }
        catch (IllegalArgumentException illegalArgumentException) {
            // empty catch block
        }
    }

    public static File getConfigFile() {
        return CONFIG_FILE;
    }

    public static void toggleDebug() {
        debugMode = !debugMode;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void onKeyPressed(int key) {
        if (!BomboConfig.get().chestClicker) {
            return;
        }
        class_310 mc = class_310.method_1551();
        class_437 class_4372 = mc.field_1755;
        if (class_4372 instanceof class_465) {
            class_465 screen = (class_465)class_4372;
            String title = screen.method_25440().getString().toLowerCase();
            if (debugMode) {
                mc.field_1724.method_7353((class_2561)class_2561.method_43470((String)("\u00a77[Debug] Pressed Key: " + key + " (" + ClickLogic.getKeyName(key) + "), GUI: " + title)), false);
            }
            for (ClickTarget target : targets) {
                if (target.auto) continue;
                if (debugMode) {
                    mc.field_1724.method_7353((class_2561)class_2561.method_43470((String)("\u00a77[Debug] Checking target: " + target.item + " (Key: " + target.keyCode + "/" + target.keyName + ", GUI: " + target.gui + ")")), false);
                }
                if (target.keyCode == -1 || key != target.keyCode) continue;
                if (target.gui.equals("all") || title.contains(target.gui)) {
                    ClickLogic.executeClick(target, mc, screen);
                    continue;
                }
                if (!debugMode) continue;
                mc.field_1724.method_7353((class_2561)class_2561.method_43470((String)("\u00a77[Debug] GUI mismatch: '" + title + "' does not contain '" + target.gui + "'")), false);
            }
        } else if (debugMode && mc.field_1724 != null) {
            mc.field_1724.method_7353((class_2561)class_2561.method_43470((String)("\u00a77[Debug] Key: " + key + " (" + ClickLogic.getKeyName(key) + "), No container screen open")), false);
        }
    }

    public static void onGuiOpen(class_465 screen) {
        if (!BomboConfig.get().autoClicker) {
            return;
        }
        class_310 mc = class_310.method_1551();
        String title = screen.method_25440().getString().toLowerCase();
        if (debugMode && mc.field_1724 != null) {
            mc.field_1724.method_7353((class_2561)class_2561.method_43470((String)("\u00a77[Debug] GUI Opened: " + title)), false);
        }
        new Thread(() -> {
            try {
                Thread.sleep(100L);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            mc.execute(() -> {
                if (mc.field_1755 != screen) {
                    return;
                }
                for (ClickTarget target : targets) {
                    if (!target.auto || !target.gui.equals("all") && !title.contains(target.gui)) continue;
                    if (debugMode && mc.field_1724 != null) {
                        mc.field_1724.method_7353((class_2561)class_2561.method_43470((String)("\u00a77[Debug] Auto clicking: " + target.item)), false);
                    }
                    ClickLogic.executeClick(target, mc, screen);
                }
            });
        }).start();
    }

    private static String getKeyName(int keyCode) {
        for (Map.Entry<String, Integer> entry : KEY_MAP.entrySet()) {
            if (entry.getValue() != keyCode) continue;
            return entry.getKey();
        }
        return "unknown";
    }

    private static void executeClick(ClickTarget target, class_310 mc, class_465 screen) {
        if (target.item.startsWith("/")) {
            mc.field_1724.field_3944.method_45730(target.item.substring(1));
            mc.field_1724.method_7353((class_2561)class_2561.method_43470((String)("\u00a7b[Bomboaddons] Executing command: " + target.item)), true);
            return;
        }
        class_2371 slots = screen.method_17577().field_7761;
        int totalSlots = slots.size();
        for (int i = 0; i < totalSlots; ++i) {
            class_1735 slot = (class_1735)slots.get(i);
            class_1799 stack = slot.method_7677();
            if (stack.method_7960()) continue;
            String itemName = stack.method_7964().getString().toLowerCase();
            boolean match = false;
            if (target.item.startsWith("l:")) {
                String loreTarget = target.item.substring(2).toLowerCase();
                List tooltip = stack.method_7950(class_1792.class_9635.method_59528((class_1937)mc.field_1687), (class_1657)mc.field_1724, (class_1836)class_1836.class_1837.field_41070);
                for (class_2561 line : tooltip) {
                    if (!line.getString().toLowerCase().contains(loreTarget)) continue;
                    match = true;
                    break;
                }
            } else {
                try {
                    if (target.item.startsWith("/") && target.item.endsWith("/")) {
                        String regex = target.item.substring(1, target.item.length() - 1);
                        match = itemName.matches(".*" + regex + ".*");
                    }
                    match = itemName.contains(target.item.toLowerCase());
                }
                catch (Exception e) {
                    match = itemName.contains(target.item.toLowerCase());
                }
            }
            if (!match) continue;
            if (mc.field_1761 != null && mc.field_1724 != null) {
                int button = 0;
                if (target.type.equalsIgnoreCase("right")) {
                    button = 1;
                } else if (target.type.equalsIgnoreCase("middle")) {
                    button = 2;
                }
                mc.field_1761.method_2906(screen.method_17577().field_7763, slot.field_7874, button, class_1713.field_7790, (class_1657)mc.field_1724);
                mc.field_1724.method_7353((class_2561)class_2561.method_43470((String)("\u00a7b[Bomboaddons] Clicking " + stack.method_7964().getString() + " (Slot " + slot.field_7874 + ") in " + screen.method_25440().getString())), true);
            }
            return;
        }
        if (debugMode && mc.field_1724 != null) {
            mc.field_1724.method_7353((class_2561)class_2561.method_43470((String)("\u00a77[Debug] Item '" + target.item + "' not found in GUI")), false);
        }
    }

    private static void saveTargets() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE);){
            GSON.toJson(targets, (Appendable)writer);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadTargets() {
        if (!CONFIG_FILE.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE);){
            Type type = new TypeToken<ArrayList<ClickTarget>>(){}.getType();
            List loadedTargets = (List)GSON.fromJson((Reader)reader, type);
            if (loadedTargets != null) {
                for (ClickTarget t : loadedTargets) {
                    if (t.id != null) continue;
                    t.id = UUID.randomUUID();
                }
                targets = loadedTargets;
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    static {
        int i;
        targets = new ArrayList<ClickTarget>();
        debugMode = false;
        GSON = new GsonBuilder().setPrettyPrinting().create();
        CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons_clicks.json").toFile();
        KEY_MAP = new HashMap<String, Integer>();
        for (i = 0; i <= 9; ++i) {
            KEY_MAP.put(String.valueOf(i), 48 + i);
        }
        for (char c = 'a'; c <= 'z'; c = (char)(c + '\u0001')) {
            KEY_MAP.put(String.valueOf(c), 65 + (c - 97));
        }
        KEY_MAP.put("tab", 258);
        KEY_MAP.put("space", 32);
        KEY_MAP.put("enter", 257);
        KEY_MAP.put("escape", 256);
        KEY_MAP.put("backspace", 259);
        KEY_MAP.put("left_shift", 340);
        KEY_MAP.put("right_shift", 344);
        KEY_MAP.put("left_control", 341);
        KEY_MAP.put("right_control", 345);
        KEY_MAP.put("left_alt", 342);
        KEY_MAP.put("right_alt", 346);
        KEY_MAP.put("insert", 260);
        KEY_MAP.put("delete", 261);
        KEY_MAP.put("right", 262);
        KEY_MAP.put("left", 263);
        KEY_MAP.put("down", 264);
        KEY_MAP.put("up", 265);
        KEY_MAP.put("page_up", 266);
        KEY_MAP.put("page_down", 267);
        KEY_MAP.put("home", 268);
        KEY_MAP.put("end", 269);
        KEY_MAP.put("caps_lock", 280);
        KEY_MAP.put("scroll_lock", 281);
        KEY_MAP.put("num_lock", 282);
        KEY_MAP.put("print_screen", 283);
        KEY_MAP.put("pause", 284);
        for (i = 1; i <= 12; ++i) {
            KEY_MAP.put("f" + i, 290 + i - 1);
        }
        for (i = 0; i <= 9; ++i) {
            KEY_MAP.put("kp_" + i, 320 + i);
        }
        ClickLogic.loadTargets();
    }

    @Environment(value=EnvType.CLIENT)
    public static class ClickTarget {
        public UUID id = UUID.randomUUID();
        public String item;
        public String gui;
        public String keyName;
        public int keyCode;
        public String type;
        public boolean auto;

        public ClickTarget(String item, String gui, String keyName, int keyCode, String type, boolean auto) {
            this.item = item.toLowerCase();
            this.gui = gui.toLowerCase();
            this.keyName = keyName.toLowerCase();
            this.keyCode = keyCode;
            this.type = type.toLowerCase();
            this.auto = auto;
        }
    }
}
