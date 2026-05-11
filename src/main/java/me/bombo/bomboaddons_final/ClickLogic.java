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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;

@Environment(EnvType.CLIENT)
public class ClickLogic {
   private static List<ClickLogic.ClickTarget> targets = new ArrayList();
   private static boolean debugMode = false;
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final File OLD_CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons_clicks.json").toFile();
   private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bombo/bomboaddons_clicks.json").toFile();
   private static final Map<String, Integer> KEY_MAP = new HashMap();

    public static int getKeyCode(String keyName) {
        if (keyName == null) return -1;
        String name = keyName.toLowerCase();
        if (name.startsWith("key_")) {
            try {
                return Integer.parseInt(name.substring(4));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return KEY_MAP.getOrDefault(name, -1);
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
      int keyCode = getKeyCode(keyName);
      targets.add(new ClickLogic.ClickTarget(item, gui, keyName, keyCode, type, auto));
      saveTargets();
   }

   public static List<ClickLogic.ClickTarget> getTargets() {
      return targets;
   }

   public static String getKeyDisplayName(String keyName) {
      if (keyName == null || keyName.isEmpty()) return "None";
      if (keyName.equalsIgnoreCase("key_280")) return "Caps Lock";
      if (keyName.equalsIgnoreCase("key_340")) return "Left Shift";
      if (keyName.equalsIgnoreCase("key_344")) return "Right Shift";
      if (keyName.equalsIgnoreCase("key_341")) return "Left Ctrl";
      if (keyName.equalsIgnoreCase("key_345")) return "Right Ctrl";
      if (keyName.equalsIgnoreCase("key_342")) return "Left Alt";
      if (keyName.equalsIgnoreCase("key_346")) return "Right Alt";
      if (keyName.equalsIgnoreCase("key_258")) return "Tab";
      if (keyName.equalsIgnoreCase("key_262")) return "Right Arrow";
      if (keyName.equalsIgnoreCase("key_263")) return "Left Arrow";
      if (keyName.equalsIgnoreCase("key_264")) return "Down Arrow";
      if (keyName.equalsIgnoreCase("key_265")) return "Up Arrow";
      if (keyName.equalsIgnoreCase("key_261")) return "Delete";
      if (keyName.equalsIgnoreCase("key_266")) return "Page Up";
      if (keyName.equalsIgnoreCase("key_267")) return "Page Down";
      if (keyName.equalsIgnoreCase("key_268")) return "Home";
      if (keyName.equalsIgnoreCase("key_269")) return "End";
      if (keyName.equalsIgnoreCase("key_257")) return "Enter";
      if (keyName.equalsIgnoreCase("key_92")) return "Backslash";
      if (keyName.equalsIgnoreCase("key_32")) return "Space";
      if (keyName.equalsIgnoreCase("key_259")) return "Backspace";
      if (keyName.equalsIgnoreCase("key_256")) return "Escape";
      
      if (keyName.length() > 0) {
         return keyName.substring(0, 1).toUpperCase() + keyName.substring(1);
      }
      return keyName;
   }

   public static void updateTarget(int index, String item, String gui, String keyName, String type, boolean auto) {
      if (index >= 0 && index < targets.size()) {
         int keyCode = getKeyCode(keyName);
         targets.set(index, new ClickLogic.ClickTarget(item, gui, keyName, keyCode, type, auto));
         saveTargets();
      }
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

   public static boolean onKeyPressed(int key) {
      Minecraft mc = Minecraft.getInstance();
      Screen var3 = mc.screen;
      if (var3 == null) return false;
      if (var3 instanceof me.bombo.bomboaddons_final.BomboConfigGUI) return false;
      if (var3 instanceof net.minecraft.client.gui.screens.ChatScreen || var3 instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen) return false;

      // Handle Wardrobe Keybinds (Legacy logic, usually in AbstractContainerScreen)
      if (var3 instanceof AbstractContainerScreen) {
         AbstractContainerScreen screen = (AbstractContainerScreen)var3;
         String title = screen.getTitle().getString().toLowerCase();

         if (title.contains("wardrobe")) {
            List<String> wardrobeKeys = BomboConfig.get().wardrobeKeys;
            if (wardrobeKeys != null) {
               for (int i = 0; i < Math.min(9, wardrobeKeys.size()); i++) {
                  String kName = wardrobeKeys.get(i);
                  if (kName != null && !kName.isEmpty()) {
                     int code = getKeyCode(kName);
                     if (code != -1 && code == key) {
                        int slotIndex = 36 + i;
                        if (slotIndex < screen.getMenu().slots.size()) {
                           Slot slot = screen.getMenu().slots.get(slotIndex);
                           ItemStack stack = slot.getItem();
                           if (!stack.isEmpty()) {
                              boolean isEquipped = false;
                              if (BomboConfig.get().disableUnequipWardrobe) {
                                 List<Component> tooltip = stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
                                 for (Component line : tooltip) {
                                    if (line.getString().contains(": Equipped")) {
                                       isEquipped = true;
                                       break;
                                    }
                                 }
                              }
                              if (!isEquipped) {
                                 if (mc.gameMode != null && mc.player != null) {
                                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0, ClickType.PICKUP, mc.player);
                                    if (BomboConfig.get().autoCloseWardrobe) {
                                       mc.player.closeContainer();
                                    }
                                 }
                              }
                           }
                        }
                        return true;
                     }
                  }
               }
            }
         }
      }

      if (BomboConfig.get().chestClicker) {
         String title = var3.getTitle().getString().toLowerCase();
         DebugUtils.debug("clicker", "Key pressed: " + key + " in GUI: " + title);
         for (ClickLogic.ClickTarget target : targets) {
            if (target.keyCode != -1 && key == target.keyCode) {
               DebugUtils.debug("clicker", "Match! Target gui=" + target.gui + " item=" + target.item);
               if (target.gui.trim().equalsIgnoreCase("all") || title.contains(target.gui.trim().toLowerCase())) {
                  executeClick(target, mc, (var3 instanceof AbstractContainerScreen ? (AbstractContainerScreen)var3 : null));
                  return true;
               }
            }
         }
      }
      return false;
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
                     } while(!target.gui.trim().equalsIgnoreCase("all") && !title.contains(target.gui.trim().toLowerCase()));

                     DebugUtils.debug("gui", "Auto clicking: " + target.item);

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
      DebugUtils.debug("clicker", "executeClick: item=" + target.item + ", gui=" + target.gui);
      if (target.item.startsWith("/") && !target.item.endsWith("/")) {
         String command = target.item.substring(1);
         DebugUtils.debug("clicker", "Sending command: " + command);
         try {
            mc.player.connection.sendCommand(command);
            mc.player.displayClientMessage(Component.literal("§b[Bomboaddons] Executing command: /" + command), true);
         } catch (Exception e) {
            DebugUtils.debug("clicker", "Failed to send command: " + e.getMessage());
            mc.player.displayClientMessage(Component.literal("§c[Bomboaddons] Failed to execute command: " + e.getMessage()), true);
         }
         return;
      }
      if (screen == null) return;
      List<Slot> slots = screen.getMenu().slots;
      int totalSlots = slots.size();

      for (int i = 0; i < totalSlots; ++i) {
         Slot slot = (Slot) slots.get(i);
         ItemStack stack = slot.getItem();
         if (!stack.isEmpty()) {
            String itemName = stack.getHoverName().getString().toLowerCase();
            boolean match = false;
            if (target.item.startsWith("l:")) {
               String loreTarget = target.item.substring(2).toLowerCase();
               List<Component> tooltip = stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
               for (Component line : tooltip) {
                  if (line.getString().toLowerCase().contains(loreTarget)) {
                     match = true;
                     break;
                  }
               }
            } else {
               try {
                  if (target.item.startsWith("/") && target.item.endsWith("/")) {
                     String regex = target.item.substring(1, target.item.length() - 1);
                     match = itemName.matches(".*" + regex + ".*");
                  } else {
                     match = itemName.contains(target.item.toLowerCase());
                  }
               } catch (Exception e) {
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
                  mc.player.displayClientMessage(Component.literal("§b[Bomboaddons] Clicking " + stack.getHoverName().getString() + " (Slot " + slot.index + ") in " + screen.getTitle().getString()), true);
               }
               return;
            }
         }
      }
      DebugUtils.debug("gui", "Item '" + target.item + "' not found in GUI");
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
      if (OLD_CONFIG_FILE.exists()) {
         try {
            if (!CONFIG_FILE.getParentFile().exists()) CONFIG_FILE.getParentFile().mkdirs();
            java.nio.file.Files.move(OLD_CONFIG_FILE.toPath(), CONFIG_FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
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
         this.item = item.trim().toLowerCase();
         this.gui = gui.trim().toLowerCase();
         this.keyName = keyName.trim().toLowerCase();
         this.keyCode = keyCode;
         this.type = type.trim().toLowerCase();
         this.auto = auto;
      }
   }
}
