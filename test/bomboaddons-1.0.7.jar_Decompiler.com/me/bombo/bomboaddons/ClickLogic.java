package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_437;
import net.minecraft.class_465;
import net.minecraft.class_746;
import net.minecraft.class_1792.class_9635;
import net.minecraft.class_1836.class_1837;

@Environment(EnvType.CLIENT)
public class ClickLogic {
   private static List<ClickLogic.ClickTarget> targets = new ArrayList();
   private static boolean debugMode = false;
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons_clicks.json").toFile();
   private static final Map<String, Integer> KEY_MAP = new HashMap();

   public static int getKeyCode(String keyName) {
      return (Integer)KEY_MAP.getOrDefault(keyName.toLowerCase(), -1);
   }

   public static void setTarget(String item, String gui, String keyName, String type, boolean auto) {
      int keyCode = (Integer)KEY_MAP.getOrDefault(keyName.toLowerCase(), -1);
      targets.add(new ClickLogic.ClickTarget(item, gui, keyName, keyCode, type, auto));
      saveTargets();
   }

   public static List<ClickLogic.ClickTarget> getTargets() {
      return targets;
   }

   public static void removeTarget(int index) {
      if (index >= 0 && index < targets.size()) {
         targets.remove(index);
         saveTargets();
      }

   }

   public static void removeTargetById(String idString) {
      try {
         UUID id = UUID.fromString(idString);
         targets.removeIf((t) -> {
            return t.id.equals(id);
         });
         saveTargets();
      } catch (IllegalArgumentException var2) {
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
      if (BomboConfig.get().chestClicker) {
         class_310 mc = class_310.method_1551();
         class_437 var3 = mc.field_1755;
         if (var3 instanceof class_465) {
            class_465 screen = (class_465)var3;
            String title = screen.method_25440().getString().toLowerCase();
            if (debugMode) {
               mc.field_1724.method_7353(class_2561.method_43470("§7[Debug] Pressed Key: " + key + " (" + getKeyName(key) + "), GUI: " + title), false);
            }

            Iterator var4 = targets.iterator();

            while(true) {
               while(true) {
                  ClickLogic.ClickTarget target;
                  do {
                     do {
                        do {
                           if (!var4.hasNext()) {
                              return;
                           }

                           target = (ClickLogic.ClickTarget)var4.next();
                        } while(target.auto);

                        if (debugMode) {
                           mc.field_1724.method_7353(class_2561.method_43470("§7[Debug] Checking target: " + target.item + " (Key: " + target.keyCode + "/" + target.keyName + ", GUI: " + target.gui + ")"), false);
                        }
                     } while(target.keyCode == -1);
                  } while(key != target.keyCode);

                  if (!target.gui.equals("all") && !title.contains(target.gui)) {
                     if (debugMode) {
                        mc.field_1724.method_7353(class_2561.method_43470("§7[Debug] GUI mismatch: '" + title + "' does not contain '" + target.gui + "'"), false);
                     }
                  } else {
                     executeClick(target, mc, screen);
                  }
               }
            }
         } else if (debugMode && mc.field_1724 != null) {
            mc.field_1724.method_7353(class_2561.method_43470("§7[Debug] Key: " + key + " (" + getKeyName(key) + "), No container screen open"), false);
         }

      }
   }

   public static void onGuiOpen(class_465 screen) {
      if (BomboConfig.get().autoClicker) {
         class_310 mc = class_310.method_1551();
         String title = screen.method_25440().getString().toLowerCase();
         if (debugMode && mc.field_1724 != null) {
            mc.field_1724.method_7353(class_2561.method_43470("§7[Debug] GUI Opened: " + title), false);
         }

         (new Thread(() -> {
            try {
               Thread.sleep(100L);
            } catch (InterruptedException var4) {
               var4.printStackTrace();
            }

            mc.execute(() -> {
               if (mc.field_1755 == screen) {
                  Iterator var3 = targets.iterator();

                  while(true) {
                     ClickLogic.ClickTarget target;
                     do {
                        do {
                           if (!var3.hasNext()) {
                              return;
                           }

                           target = (ClickLogic.ClickTarget)var3.next();
                        } while(!target.auto);
                     } while(!target.gui.equals("all") && !title.contains(target.gui));

                     if (debugMode && mc.field_1724 != null) {
                        mc.field_1724.method_7353(class_2561.method_43470("§7[Debug] Auto clicking: " + target.item), false);
                     }

                     executeClick(target, mc, screen);
                  }
               }
            });
         })).start();
      }
   }

   private static String getKeyName(int keyCode) {
      Iterator var1 = KEY_MAP.entrySet().iterator();

      Entry entry;
      do {
         if (!var1.hasNext()) {
            return "unknown";
         }

         entry = (Entry)var1.next();
      } while((Integer)entry.getValue() != keyCode);

      return (String)entry.getKey();
   }

   private static void executeClick(ClickLogic.ClickTarget target, class_310 mc, class_465 screen) {
      if (target.item.startsWith("/") && !target.item.endsWith("/")) {
         mc.field_1724.field_3944.method_45730(target.item.substring(1));
         mc.field_1724.method_7353(class_2561.method_43470("§b[Bomboaddons] Executing command: " + target.item), true);
      } else {
         List<class_1735> slots = screen.method_17577().field_7761;
         int totalSlots = slots.size();

         for(int i = 0; i < totalSlots; ++i) {
            class_1735 slot = (class_1735)slots.get(i);
            class_1799 stack = slot.method_7677();
            if (!stack.method_7960()) {
               String itemName = stack.method_7964().getString().toLowerCase();
               boolean match = false;
               String loreTarget;
               if (target.item.startsWith("l:")) {
                  loreTarget = target.item.substring(2).toLowerCase();
                  List<class_2561> tooltip = stack.method_7950(class_9635.method_59528(mc.field_1687), mc.field_1724, class_1837.field_41070);
                  Iterator var12 = tooltip.iterator();

                  while(var12.hasNext()) {
                     class_2561 line = (class_2561)var12.next();
                     if (line.getString().toLowerCase().contains(loreTarget)) {
                        match = true;
                        break;
                     }
                  }
               } else {
                  try {
                     if (target.item.startsWith("/") && target.item.endsWith("/")) {
                        loreTarget = target.item.substring(1, target.item.length() - 1);
                        match = itemName.matches(".*" + loreTarget + ".*");
                     } else {
                        match = itemName.contains(target.item.toLowerCase());
                     }
                  } catch (Exception var14) {
                     match = itemName.contains(target.item.toLowerCase());
                  }
               }

               if (match) {
                  if (mc.field_1761 != null && mc.field_1724 != null) {
                     int button = 0;
                     if (target.type.equalsIgnoreCase("right")) {
                        button = 1;
                     } else if (target.type.equalsIgnoreCase("middle")) {
                        button = 2;
                     }

                     mc.field_1761.method_2906(screen.method_17577().field_7763, slot.field_7874, button, class_1713.field_7790, mc.field_1724);
                     class_746 var10000 = mc.field_1724;
                     String var10001 = stack.method_7964().getString();
                     var10000.method_7353(class_2561.method_43470("§b[Bomboaddons] Clicking " + var10001 + " (Slot " + slot.field_7874 + ") in " + screen.method_25440().getString()), true);
                  }

                  return;
               }
            }
         }

         if (debugMode && mc.field_1724 != null) {
            mc.field_1724.method_7353(class_2561.method_43470("§7[Debug] Item '" + target.item + "' not found in GUI"), false);
         }

      }
   }

   private static void saveTargets() {
      try {
         FileWriter writer = new FileWriter(CONFIG_FILE);

         try {
            GSON.toJson(targets, writer);
         } catch (Throwable var4) {
            try {
               writer.close();
            } catch (Throwable var3) {
               var4.addSuppressed(var3);
            }

            throw var4;
         }

         writer.close();
      } catch (IOException var5) {
         var5.printStackTrace();
      }

   }

   private static void loadTargets() {
      if (CONFIG_FILE.exists()) {
         try {
            FileReader reader = new FileReader(CONFIG_FILE);

            try {
               Type type = (new TypeToken<ArrayList<ClickLogic.ClickTarget>>() {
               }).getType();
               List<ClickLogic.ClickTarget> loadedTargets = (List)GSON.fromJson(reader, type);
               if (loadedTargets != null) {
                  Iterator var3 = loadedTargets.iterator();

                  while(var3.hasNext()) {
                     ClickLogic.ClickTarget t = (ClickLogic.ClickTarget)var3.next();
                     if (t.id == null) {
                        t.id = UUID.randomUUID();
                     }
                  }

                  targets = loadedTargets;
               }
            } catch (Throwable var6) {
               try {
                  reader.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }

               throw var6;
            }

            reader.close();
         } catch (IOException var7) {
            var7.printStackTrace();
         }

      }
   }

   static {
      int i;
      for(i = 0; i <= 9; ++i) {
         KEY_MAP.put(String.valueOf(i), 48 + i);
      }

      for(char c = 'a'; c <= 'z'; ++c) {
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
      KEY_MAP.put(",", 44);
      KEY_MAP.put(".", 46);
      KEY_MAP.put("/", 47);
      KEY_MAP.put(";", 59);
      KEY_MAP.put("'", 39);
      KEY_MAP.put("[", 91);
      KEY_MAP.put("]", 93);
      KEY_MAP.put("\\", 92);
      KEY_MAP.put("-", 45);
      KEY_MAP.put("=", 61);
      KEY_MAP.put("`", 96);
      KEY_MAP.put("<", 162);

      for(i = 1; i <= 12; ++i) {
         KEY_MAP.put("f" + i, 290 + i - 1);
      }

      for(i = 0; i <= 9; ++i) {
         KEY_MAP.put("kp_" + i, 320 + i);
      }

      loadTargets();
   }

   @Environment(EnvType.CLIENT)
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
