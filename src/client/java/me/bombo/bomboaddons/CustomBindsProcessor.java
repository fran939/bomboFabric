package me.bombo.bomboaddons;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class CustomBindsProcessor {
   private static final Set<Integer> pressedKeys = new HashSet();
   private static long lastGuiKeybindHandledTime = 0L;
   private static int lastGuiKeybindHandledKey = -1;

   public static void onKeyInput(int key, int action) {
      if (action == 1) {
         pressedKeys.add(key);
         if (Minecraft.getInstance().screen == null) {
            checkKeybinds(key);
         } else {
            checkGuiKeybinds(key);
         }
      } else if (action == 0) {
         pressedKeys.remove(key);
      }

   }

   public static boolean isKeybindAllowed(String reqProfile, String reqIsland, String reqArmor) {
      BomboConfig.Settings s = BomboConfig.get();
      if (reqProfile != null && !reqProfile.trim().isEmpty()) {
         String p = reqProfile.trim().toLowerCase();
         if (!p.equals("all") && !p.equals("any") && !p.equals("general")) {
            String activeProf = s != null && s.activeProfile != null ? s.activeProfile.toLowerCase() : "default";
            if (!p.equals(activeProf)) {
               return false;
            }
         }
      }

      if (!SkyblockUtils.matchesIslandRequirement(reqIsland)) {
         return false;
      }

      if (!SkyblockUtils.matchesArmorRequirement(reqArmor)) {
         return false;
      }

      return true;
   }

   public static java.util.List<BomboConfig.CommandBind> getAllActiveBinds() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) return java.util.Collections.emptyList();
      java.util.List<BomboConfig.CommandBind> list = new java.util.ArrayList<>();
      String activeProf = s.activeProfile != null ? s.activeProfile : "default";

      if (s.profileBinds != null) {
         List<BomboConfig.CommandBind> pb = s.profileBinds.get(activeProf);
         if (pb != null) list.addAll(pb);
      }
      if (s.keybindBinds != null) {
         List<BomboConfig.CommandBind> kb = s.keybindBinds.get(activeProf);
         if (kb != null) list.addAll(kb);
      }
      if (s.commandBinds != null) {
         list.addAll(s.commandBinds);
      }
      return list;
   }

   private static void checkKeybinds(int keyCode) {
      List<BomboConfig.CommandBind> binds = getAllActiveBinds();
      for (BomboConfig.CommandBind cb : binds) {
         if (cb != null && cb.enabled && cb.command != null && !cb.command.trim().isEmpty()
               && isKeybindAllowed(cb.requiredProfile, cb.requiredIsland, cb.requiredArmor)
               && matchesKey(cb.keyName, keyCode)) {
            if (BomboConfig.get().debugKeys) {
               Bomboaddons.sendMessage("§a[KeyDebug] Matched In-Game Keybind: " + cb.keyName + " -> " + cb.command);
            }
            executeCommandOrChat(cb.command);
         }
      }
   }

   public static boolean hasHandledGuiKeyRecently(int keyCode) {
      return System.currentTimeMillis() - lastGuiKeybindHandledTime < 250L && lastGuiKeybindHandledKey == keyCode;
   }

   public static boolean matchesAnyRegisteredGuiBind(int keyCode) {
      List<BomboConfig.CommandBind> binds = getAllActiveBinds();
      for (BomboConfig.CommandBind cb : binds) {
         if (cb != null && cb.enabled && cb.command != null && !cb.command.trim().isEmpty()
               && isKeybindAllowed(cb.requiredProfile, cb.requiredIsland, cb.requiredArmor)
               && matchesKey(cb.keyName, keyCode)) {
            return true;
         }
      }
      return false;
   }

   public static boolean checkGuiKeybinds(int keyCode) {
      long now = System.currentTimeMillis();
      if (now - lastGuiKeybindHandledTime < 250L && lastGuiKeybindHandledKey == keyCode) {
         return true;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.screen == null) {
            return false;
         } else if (!(mc.screen instanceof ChatScreen) && !(mc.screen instanceof AbstractSignEditScreen)) {
            if (ItemListOverlay.searchBox != null && ItemListOverlay.searchBox.isFocused()) {
               return false;
            } else {
               boolean matchedAny = false;
               List<BomboConfig.CommandBind> binds = getAllActiveBinds();
               for (BomboConfig.CommandBind cb : binds) {
                  if (cb != null && cb.enabled && cb.command != null && !cb.command.trim().isEmpty()
                        && isKeybindAllowed(cb.requiredProfile, cb.requiredIsland, cb.requiredArmor)
                        && matchesKey(cb.keyName, keyCode)) {
                     if (BomboConfig.get().debugKeys) {
                        Bomboaddons.sendMessage("§a[KeyDebug] Matched GUI Keybind: " + cb.keyName + " -> " + cb.command);
                     }
                     executeCommandOrChat(cb.command);
                     matchedAny = true;
                  }
               }

               if (matchedAny) {
                  lastGuiKeybindHandledTime = now;
                  lastGuiKeybindHandledKey = keyCode;
               }

               return matchedAny;
            }
         } else {
            return false;
         }
      }
   }

   public static boolean isAnyGuiModifierHeld() {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.getWindow() == null) {
         return false;
      }
      long handle = mc.getWindow().handle();

      // Check standard extra mouse buttons (mouse3 - mouse8: GLFW buttons 2 - 7)
      for (int btn = 2; btn <= 7; btn++) {
         if (GLFW.glfwGetMouseButton(handle, btn) == 1) {
            return true;
         }
      }

      // Check if ANY modifier key is physically held down (Shift, Ctrl, Alt)
      boolean shiftDown = GLFW.glfwGetKey(handle, 340) == 1 || GLFW.glfwGetKey(handle, 344) == 1;
      boolean ctrlDown = GLFW.glfwGetKey(handle, 341) == 1 || GLFW.glfwGetKey(handle, 345) == 1;
      boolean altDown = GLFW.glfwGetKey(handle, 342) == 1 || GLFW.glfwGetKey(handle, 346) == 1;
      if (shiftDown || ctrlDown || altDown) {
         return true;
      }

      // Check modifier keys used across active profile binds
      List<BomboConfig.CommandBind> binds = getAllActiveBinds();
      for (BomboConfig.CommandBind cb : binds) {
         if (cb != null && cb.enabled && cb.keyName != null && cb.keyName.contains("+")) {
            String[] parts = cb.keyName.split("\\+");
            for (int i = 0; i < parts.length - 1; i++) {
               if (isSingleKeyCurrentlyPressed(parts[i])) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static void onMouseInput(int button, int action) {
      int mouseKeyCode = 1000 + button;
      if (action == 1) {
         pressedKeys.add(mouseKeyCode);
         if (Minecraft.getInstance().screen == null) {
            checkKeybinds(mouseKeyCode);
         } else {
            checkGuiKeybinds(mouseKeyCode);
         }
      } else if (action == 0) {
         pressedKeys.remove(mouseKeyCode);
      }
   }

   public static boolean isSingleKeyMatching(String singleKeyStr, int targetCode) {
      if (singleKeyStr == null || singleKeyStr.trim().isEmpty()) {
         return false;
      }
      String clean = singleKeyStr.trim().toLowerCase();
      if (clean.contains("+")) {
         return false;
      }
      int code = getGlfwCodeForName(clean);
      if (code != -1 && code == targetCode) {
         return true;
      }
      // Generic match for Shift
      if ((targetCode == 340 || targetCode == 344) && (clean.equals("shift") || clean.equals("lshift") || clean.equals("rshift") || clean.equals("left_shift") || clean.equals("right_shift") || clean.equals("left shift") || clean.equals("right shift"))) {
         return true;
      }
      // Generic match for Ctrl
      if ((targetCode == 341 || targetCode == 345) && (clean.equals("ctrl") || clean.equals("control") || clean.equals("lctrl") || clean.equals("rctrl") || clean.equals("left_control") || clean.equals("right_control") || clean.equals("left_ctrl") || clean.equals("right_ctrl") || clean.equals("left control") || clean.equals("right control"))) {
         return true;
      }
      // Generic match for Alt
      if ((targetCode == 342 || targetCode == 346) && (clean.equals("alt") || clean.equals("lalt") || clean.equals("ralt") || clean.equals("left_alt") || clean.equals("right_alt") || clean.equals("left alt") || clean.equals("right alt"))) {
         return true;
      }
      if (clean.equals(String.valueOf(targetCode)) || clean.equals("key_" + targetCode)) {
         return true;
      }
      return false;
   }

   public static String getKeyNameForGlfwCode(int code) {
      if (code >= 1000 && code <= 1007) {
         return "mouse" + (code - 1000 + 1);
      } else if (code >= 290 && code <= 301) {
         return "f" + (code - 289);
      } else if (code >= 320 && code <= 329) {
         return "kp_" + (code - 320);
      } else if (code == 330) {
         return "kp_decimal";
      } else if (code == 331) {
         return "kp_divide";
      } else if (code == 332) {
         return "kp_multiply";
      } else if (code == 333) {
         return "kp_subtract";
      } else if (code == 334) {
         return "kp_add";
      } else if (code == 335) {
         return "kp_enter";
      } else if (code == 336) {
         return "kp_equal";
      } else if (code == 340) {
         return "left_shift";
      } else if (code == 344) {
         return "right_shift";
      } else if (code == 341) {
         return "left_control";
      } else if (code == 345) {
         return "right_control";
      } else if (code == 342) {
         return "left_alt";
      } else if (code == 346) {
         return "right_alt";
      } else if (code == 32) {
         return "space";
      } else if (code == 257) {
         return "enter";
      } else {
         String glfwName = GLFW.glfwGetKeyName(code, 0);
         return glfwName != null && !glfwName.trim().isEmpty() ? glfwName.trim().toLowerCase() : "key_" + code;
      }
   }

   public static int getGlfwCodeForName(String name) {
      if (name == null || name.trim().isEmpty()) {
         return -1;
      }
      String clean = name.trim().toLowerCase();

      // Mouse mappings
      if (clean.startsWith("mouse") || clean.startsWith("button") || clean.equals("left_click") || clean.equals("right_click") || clean.equals("middle_click")) {
         if (clean.equals("mouse1") || clean.equals("button1") || clean.equals("left_click") || clean.equals("mouse_button_1") || clean.equals("mouse_1")) return 1000;
         if (clean.equals("mouse2") || clean.equals("button2") || clean.equals("right_click") || clean.equals("mouse_button_2") || clean.equals("mouse_2")) return 1001;
         if (clean.equals("mouse3") || clean.equals("button3") || clean.equals("middle_click") || clean.equals("mouse_button_3") || clean.equals("mouse_3")) return 1002;
         if (clean.equals("mouse4") || clean.equals("button4") || clean.equals("mouse_button_4") || clean.equals("mouse_4")) return 1003;
         if (clean.equals("mouse5") || clean.equals("button5") || clean.equals("mouse_button_5") || clean.equals("mouse_5")) return 1004;
         if (clean.equals("mouse6") || clean.equals("button6") || clean.equals("mouse_button_6") || clean.equals("mouse_6")) return 1005;
         if (clean.equals("mouse7") || clean.equals("button7") || clean.equals("mouse_button_7") || clean.equals("mouse_7")) return 1006;
         if (clean.equals("mouse8") || clean.equals("button8") || clean.equals("mouse_button_8") || clean.equals("mouse_8")) return 1007;

         try {
            int num = Integer.parseInt(clean.replaceAll("[^0-9]", ""));
            if (num >= 1 && num <= 8) {
               return 1000 + (num - 1);
            }
         } catch (Exception ignored) {}
      }

      // Key prefixes
      if (clean.startsWith("key_")) {
         try {
            return Integer.parseInt(clean.substring(4));
         } catch (Exception ignored) {}
      }

      // Modifiers
      if (clean.equals("shift") || clean.equals("lshift") || clean.equals("left_shift") || clean.equals("left shift")) return 340;
      if (clean.equals("rshift") || clean.equals("right_shift") || clean.equals("right shift")) return 344;
      if (clean.equals("ctrl") || clean.equals("control") || clean.equals("lctrl") || clean.equals("left_control") || clean.equals("left_ctrl") || clean.equals("left control") || clean.equals("left ctrl")) return 341;
      if (clean.equals("rctrl") || clean.equals("right_control") || clean.equals("right_ctrl") || clean.equals("right control") || clean.equals("right ctrl")) return 345;
      if (clean.equals("alt") || clean.equals("lalt") || clean.equals("left_alt") || clean.equals("left alt")) return 342;
      if (clean.equals("ralt") || clean.equals("right_alt") || clean.equals("right alt")) return 346;

      // Other special keys
      if (clean.equals("space")) return 32;
      if (clean.equals("enter") || clean.equals("return")) return 257;
      if (clean.equals("tab")) return 258;
      if (clean.equals("escape") || clean.equals("esc")) return 256;
      if (clean.equals("backspace")) return 259;
      if (clean.equals("caps_lock") || clean.equals("capslock") || clean.equals("caps")) return 280;

      // Numpad
      if (clean.matches("^kp_?[0-9]$|^keypad_?[0-9]$|^numpad_?[0-9]$")) {
         int num = Integer.parseInt(clean.replaceAll("[^0-9]", ""));
         return 320 + num;
      }
      if (clean.equals("kp_enter") || clean.equals("kpenter") || clean.equals("numpad_enter")) return 335;
      if (clean.equals("kp_add") || clean.equals("kpadd") || clean.equals("kp_plus")) return 334;
      if (clean.equals("kp_subtract") || clean.equals("kpsubtract") || clean.equals("kp_minus")) return 333;
      if (clean.equals("kp_multiply") || clean.equals("kpmultiply") || clean.equals("kp_star")) return 332;
      if (clean.equals("kp_divide") || clean.equals("kpdivide") || clean.equals("kp_slash")) return 331;
      if (clean.equals("kp_decimal") || clean.equals("kpdecimal") || clean.equals("kp_period")) return 330;

      // F keys (f1 - f12)
      if (clean.matches("^f_?[1-9]$|^f_?1[0-2]$|^key_?f_?[1-9]$|^key_?f_?1[0-2]$")) {
         int num = Integer.parseInt(clean.replaceAll("[^0-9]", ""));
         return 289 + num;
      }

      // Single characters 0-9, a-z, symbols
      if (clean.length() == 1) {
         char ch = clean.charAt(0);
         if (ch >= '0' && ch <= '9') return 48 + (ch - '0');
         if (ch >= 'a' && ch <= 'z') return 65 + (ch - 'a');
         if (ch == '.') return 46;
         if (ch == ',') return 44;
         if (ch == '/') return 47;
         if (ch == ';') return 59;
         if (ch == '\'') return 39;
         if (ch == '[') return 91;
         if (ch == ']') return 93;
         if (ch == '-') return 45;
         if (ch == '=') return 61;
         if (ch == '`') return 96;
         if (ch == '\\') return 92;
      }

      // Key prefixes
      if (clean.startsWith("key_")) {
         try {
            return Integer.parseInt(clean.substring(4));
         } catch (Exception ignored) {}
      }

      // Direct integer keycode
      try {
         int direct = Integer.parseInt(clean);
         if (direct >= 32) return direct;
      } catch (Exception ignored) {}

      return -1;
   }

   public static boolean isSingleKeyCurrentlyPressed(String singleKeyStr) {
      if (singleKeyStr == null || singleKeyStr.trim().isEmpty()) {
         return false;
      }
      String clean = singleKeyStr.trim().toLowerCase();
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.getWindow() == null) {
         return false;
      }
      long windowHandle = mc.getWindow().handle();

      // Check mouse
      if (clean.startsWith("mouse") || clean.startsWith("button") || clean.equals("left_click") || clean.equals("right_click") || clean.equals("middle_click")) {
         int mouseCode = getGlfwCodeForName(clean);
         if (mouseCode >= 1000 && mouseCode <= 1007) {
            int button = mouseCode - 1000;
            return GLFW.glfwGetMouseButton(windowHandle, button) == 1;
         }
      }

      int code = getGlfwCodeForName(clean);
      if (code != -1) {
         if (code >= 1000 && code <= 1007) {
            return GLFW.glfwGetMouseButton(windowHandle, code - 1000) == 1;
         }
         // Shift: either left or right shift if generic "shift", or specific
         if (code == 340 || code == 344 || clean.contains("shift")) {
            if (clean.equals("left_shift") || clean.equals("lshift") || clean.equals("left shift")) {
               return GLFW.glfwGetKey(windowHandle, 340) == 1 || pressedKeys.contains(340);
            } else if (clean.equals("right_shift") || clean.equals("rshift") || clean.equals("right shift")) {
               return GLFW.glfwGetKey(windowHandle, 344) == 1 || pressedKeys.contains(344);
            }
            return GLFW.glfwGetKey(windowHandle, 340) == 1 || GLFW.glfwGetKey(windowHandle, 344) == 1 || pressedKeys.contains(340) || pressedKeys.contains(344);
         }
         // Ctrl: either left or right ctrl if generic "ctrl"/"control", or specific
         if (code == 341 || code == 345 || clean.contains("ctrl") || clean.contains("control")) {
            if (clean.equals("left_control") || clean.equals("left_ctrl") || clean.equals("lctrl") || clean.equals("left control") || clean.equals("left ctrl")) {
               return GLFW.glfwGetKey(windowHandle, 341) == 1 || pressedKeys.contains(341);
            } else if (clean.equals("right_control") || clean.equals("right_ctrl") || clean.equals("rctrl") || clean.equals("right control") || clean.equals("right ctrl")) {
               return GLFW.glfwGetKey(windowHandle, 345) == 1 || pressedKeys.contains(345);
            }
            return GLFW.glfwGetKey(windowHandle, 341) == 1 || GLFW.glfwGetKey(windowHandle, 345) == 1 || pressedKeys.contains(341) || pressedKeys.contains(345);
         }
         // Alt: either left or right alt if generic "alt", or specific
         if (code == 342 || code == 346 || clean.contains("alt")) {
            if (clean.equals("left_alt") || clean.equals("lalt") || clean.equals("left alt")) {
               return GLFW.glfwGetKey(windowHandle, 342) == 1 || pressedKeys.contains(342);
            } else if (clean.equals("right_alt") || clean.equals("ralt") || clean.equals("right alt")) {
               return GLFW.glfwGetKey(windowHandle, 346) == 1 || pressedKeys.contains(346);
            }
            return GLFW.glfwGetKey(windowHandle, 342) == 1 || GLFW.glfwGetKey(windowHandle, 346) == 1 || pressedKeys.contains(342) || pressedKeys.contains(346);
         }
         return GLFW.glfwGetKey(windowHandle, code) == 1 || pressedKeys.contains(code);
      }
      return false;
   }

   public static boolean matchesKey(String keyName, int keyCode) {
      if (keyName != null && !keyName.trim().isEmpty()) {
         String raw = keyName.trim();
         if (!raw.contains("+")) {
            return isSingleKeyMatching(raw, keyCode);
         } else {
            String[] parts = raw.split("\\+");
            int triggeredIndex = -1;

            for(int i = 0; i < parts.length; ++i) {
               if (isSingleKeyMatching(parts[i], keyCode)) {
                  triggeredIndex = i;
                  break;
               }
            }

            if (triggeredIndex == -1) {
               return false;
            } else {
               for(int i = 0; i < parts.length; ++i) {
                  if (i != triggeredIndex && !isSingleKeyCurrentlyPressed(parts[i])) {
                     return false;
                  }
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static boolean processAlias(String commandText) {
      if (commandText != null && !commandText.trim().isEmpty()) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s != null && s.commandAliases != null) {
            String input = commandText.trim();
            if (input.startsWith("/")) {
               input = input.substring(1).trim();
            }

            String[] parts = input.split(" ", 2);
            String aliasKey = parts[0].toLowerCase();
            String extraArgs = parts.length > 1 ? parts[1] : "";

            for(Map.Entry<String, String> entry : s.commandAliases.entrySet()) {
               if (entry.getKey() != null && !entry.getKey().trim().isEmpty()) {
                  String targetKey = entry.getKey().trim().toLowerCase();
                  if (targetKey.startsWith("/")) {
                     targetKey = targetKey.substring(1).trim();
                  }

                  if (targetKey.equals(aliasKey)) {
                     String cmdToRun = entry.getValue();
                     if (!extraArgs.isEmpty() && cmdToRun != null) {
                        cmdToRun = cmdToRun + " " + extraArgs;
                     }

                     executeCommandOrChat(cmdToRun);
                     return true;
                  }
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void processChatTrigger(String chatMessage) {
      if (chatMessage != null && !chatMessage.trim().isEmpty()) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s != null) {
            List<BomboConfig.ChatTrigger> triggers = null;
            if (s.profileChatTriggers != null) {
               triggers = s.profileChatTriggers.get(s.activeProfile != null ? s.activeProfile : "default");
            }
            if (triggers == null && s.chatTriggers != null) {
               triggers = s.chatTriggers;
            }

            if (triggers != null) {
               for(BomboConfig.ChatTrigger cte : triggers) {
                  if (cte != null && cte.enabled && cte.triggerText != null && !cte.triggerText.trim().isEmpty() && chatMessage.contains(cte.triggerText.trim())) {
                     if (cte.commandToRun != null && !cte.commandToRun.trim().isEmpty()) {
                        executeCommandOrChat(cte.commandToRun.trim());
                     }

                     if (cte.titleToShow != null && !cte.titleToShow.trim().isEmpty()) {
                        showTitle(cte.titleToShow.trim());
                     }
                  }
               }
            }
         }
      }
   }

   public static void executeCommandOrChat(String text) {
      if (text != null && !text.trim().isEmpty()) {
         String clean;
         for(clean = text.trim(); clean.startsWith("/"); clean = clean.substring(1).trim()) {
         }

         BomboaddonsClient.executeTracked(clean);
      }
   }

   private static void showTitle(String titleText) {
      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> {
         if (mc.gui != null) {
            mc.gui.setTitle(Component.literal(titleText.replace("&", "Â§")));
         }

      });
   }
}
