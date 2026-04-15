package me.bombo.bomboaddons_final;

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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag.Default;

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

   public static boolean isCodeDown(long handle, com.mojang.blaze3d.platform.Window win, int code) {
      if (code == -1) return false;
      if (code < 8) {
         if (handle == 0) return false;
         return org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, code) == 1;
      } else {
         if (win == null) return false;
         return com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, code);
      }
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
         Minecraft mc = Minecraft.getInstance();
         Screen var3 = mc.screen;
         if (var3 instanceof AbstractContainerScreen) {
            AbstractContainerScreen screen = (AbstractContainerScreen)var3;
            String title = screen.getTitle().getString().toLowerCase();
            if (debugMode) {
               mc.player.displayClientMessage(Component.literal("§7[Debug] Pressed Key: " + key + " (" + getKeyName(key) + "), GUI: " + title), false);
            }

            for (ClickLogic.ClickTarget target : targets) {
               if (target.auto) continue;

               if (debugMode) {
                  mc.player.displayClientMessage(Component.literal("§7[Debug] Checking target: " + target.item + " (Key: " + target.keyCode + "/" + target.keyName + ", GUI: " + target.gui + ")"), false);
               }
               
               if (target.keyCode != -1 && key == target.keyCode) {
                  if (target.gui.equals("all") || title.contains(target.gui)) {
                     executeClick(target, mc, screen);
                  } else if (debugMode) {
                     mc.player.displayClientMessage(Component.literal("§7[Debug] GUI mismatch: '" + title + "' does not contain '" + target.gui + "'"), false);
                  }
               }
            }
         } else if (debugMode && mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§7[Debug] Key: " + key + " (" + getKeyName(key) + "), No container screen open"), false);
         }

      }
   }

   public static void listTargets(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
      source.sendFeedback(Component.literal("§8[§bBomboAddons§8]§r §6--- Click Targets ---"));
      if (targets.isEmpty()) {
         source.sendFeedback(Component.literal("§7  None"));
      } else {
         for (int i = 0; i < targets.size(); i++) {
            ClickLogic.ClickTarget t = targets.get(i);
            source.sendFeedback(Component.literal("§7" + i + ". §e" + t.item + " §7(GUI: §b" + t.gui + "§7, Key: §d" + t.keyName + "§7, Type: §a" + t.type + "§7, Auto: " + (t.auto ? "§aYes" : "§cNo") + "§7)"));
         }
      }
   }

   public static void onGuiOpen(AbstractContainerScreen screen) {
      if (BomboConfig.get().autoClicker) {
         Minecraft mc = Minecraft.getInstance();
         String title = screen.getTitle().getString().toLowerCase();
         if (debugMode && mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§7[Debug] GUI Opened: " + title), false);
         }

         (new Thread(() -> {
            try {
               Thread.sleep(100L);
            } catch (InterruptedException var4) {
               var4.printStackTrace();
            }

            mc.execute(() -> {
               if (mc.screen == screen) {
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

                     if (debugMode && mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("§7[Debug] Auto clicking: " + target.item), false);
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

   private static void executeClick(ClickLogic.ClickTarget target, Minecraft mc, AbstractContainerScreen screen) {
      if (target.item.startsWith("/") && !target.item.endsWith("/")) {
         mc.player.connection.sendCommand(target.item.substring(1));
         mc.player.displayClientMessage(Component.literal("§b[Bomboaddons] Executing command: " + target.item), true);
      } else {
         List<Slot> slots = screen.getMenu().slots;
         int totalSlots = slots.size();

         for(int i = 0; i < totalSlots; ++i) {
            Slot slot = (Slot)slots.get(i);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
               String itemName = stack.getHoverName().getString().toLowerCase();
               boolean match = false;
               String loreTarget;
               if (target.item.startsWith("l:")) {
                  loreTarget = target.item.substring(2).toLowerCase();
                  List<Component> tooltip = stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, Default.NORMAL);
                  Iterator var12 = tooltip.iterator();

                  while(var12.hasNext()) {
                     Component line = (Component)var12.next();
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
                  if (mc.gameMode != null && mc.player != null) {
                     int button = 0;
                     if (target.type.equalsIgnoreCase("right")) {
                        button = 1;
                     } else if (target.type.equalsIgnoreCase("middle")) {
                        button = 2;
                     }

                     mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, button, ClickType.PICKUP, mc.player);
                     LocalPlayer var10000 = mc.player;
                     String var10001 = stack.getHoverName().getString();
                     var10000.displayClientMessage(Component.literal("§b[Bomboaddons] Clicking " + var10001 + " (Slot " + slot.index + ") in " + screen.getTitle().getString()), true);
                  }

                  return;
               }
            }
         }

         if (debugMode && mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§7[Debug] Item '" + target.item + "' not found in GUI"), false);
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

      KEY_MAP.put("mouse1", 0);
      KEY_MAP.put("mouse2", 1);
      KEY_MAP.put("mouse3", 2);
      KEY_MAP.put("mouse4", 3);
      KEY_MAP.put("mouse5", 4);
      KEY_MAP.put("mouse6", 5);
      KEY_MAP.put("mouse7", 6);
      KEY_MAP.put("mouse8", 7);
      KEY_MAP.put("button1", 0);
      KEY_MAP.put("button2", 1);
      KEY_MAP.put("button3", 2);
      KEY_MAP.put("left_click", 0);
      KEY_MAP.put("right_click", 1);
      KEY_MAP.put("middle_click", 2);

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
