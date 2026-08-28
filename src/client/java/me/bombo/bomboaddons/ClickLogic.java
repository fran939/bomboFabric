package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.bombo.bomboaddons.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ClickLogic {
   private static List<ClickTarget> targets = new ArrayList();
   private static boolean debugMode = false;
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final File OLD_CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons_clicks.json").toFile();
   private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/bomboaddons_clicks.json").toFile();
   private static final Map<String, Integer> KEY_MAP = new HashMap();

   public static int getKeyCode(String keyName) {
      if (keyName == null || keyName.trim().isEmpty()) {
         return -1;
      }
      String name = keyName.trim().toLowerCase();
      if (KEY_MAP.containsKey(name)) {
         return KEY_MAP.get(name);
      }
      int code = CustomBindsProcessor.getGlfwCodeForName(name);
      if (code != -1) {
         if (code >= 1000 && code <= 1007) return code - 1000;
         return code;
      }
      if (name.startsWith("key_")) {
         try {
            return Integer.parseInt(name.substring(4));
         } catch (NumberFormatException ignored) {
            return -1;
         }
      }
      return -1;
   }

   public static boolean isCodeDown(long handle, Window win, int code) {
      if (code == -1) {
         return false;
      } else if (code < 8) {
         if (handle == 0L) {
            return false;
         } else {
            return GLFW.glfwGetMouseButton(handle, code) == 1;
         }
      } else {
         return win == null ? false : InputConstants.isKeyDown(win, code);
      }
   }

   public static void setTarget(String item, String gui, String keyName, String type, boolean auto) {
      int keyCode = getKeyCode(keyName);
      targets.add(new ClickTarget(item, gui, keyName, keyCode, type, auto));
      saveTargets();
   }

   public static List<ClickTarget> getTargets() {
      return targets;
   }

   public static String getKeyDisplayName(String keyName) {
      if (keyName != null && !keyName.isEmpty()) {
         if (!keyName.contains("+")) {
            if (!keyName.equalsIgnoreCase("mouse1") && !keyName.equalsIgnoreCase("button1") && !keyName.equalsIgnoreCase("left_click")) {
               if (!keyName.equalsIgnoreCase("mouse2") && !keyName.equalsIgnoreCase("button2") && !keyName.equalsIgnoreCase("right_click")) {
                  if (!keyName.equalsIgnoreCase("mouse3") && !keyName.equalsIgnoreCase("button3") && !keyName.equalsIgnoreCase("middle_click")) {
                     if (!keyName.equalsIgnoreCase("mouse4") && !keyName.equalsIgnoreCase("button4")) {
                        if (!keyName.equalsIgnoreCase("mouse5") && !keyName.equalsIgnoreCase("button5")) {
                           if (!keyName.equalsIgnoreCase("mouse6") && !keyName.equalsIgnoreCase("button6")) {
                              if (!keyName.equalsIgnoreCase("mouse7") && !keyName.equalsIgnoreCase("button7")) {
                                 if (!keyName.equalsIgnoreCase("mouse8") && !keyName.equalsIgnoreCase("button8")) {
                                    if (keyName.equalsIgnoreCase("key_280")) {
                                       return "Caps Lock";
                                    } else if (keyName.equalsIgnoreCase("key_340")) {
                                       return "Left Shift";
                                    } else if (keyName.equalsIgnoreCase("key_344")) {
                                       return "Right Shift";
                                    } else if (keyName.equalsIgnoreCase("key_341")) {
                                       return "Left Ctrl";
                                    } else if (keyName.equalsIgnoreCase("key_345")) {
                                       return "Right Ctrl";
                                    } else if (keyName.equalsIgnoreCase("key_342")) {
                                       return "Left Alt";
                                    } else if (keyName.equalsIgnoreCase("key_346")) {
                                       return "Right Alt";
                                    } else if (keyName.equalsIgnoreCase("key_258")) {
                                       return "Tab";
                                    } else if (keyName.equalsIgnoreCase("key_262")) {
                                       return "Right Arrow";
                                    } else if (keyName.equalsIgnoreCase("key_263")) {
                                       return "Left Arrow";
                                    } else if (keyName.equalsIgnoreCase("key_264")) {
                                       return "Down Arrow";
                                    } else if (keyName.equalsIgnoreCase("key_265")) {
                                       return "Up Arrow";
                                    } else if (keyName.equalsIgnoreCase("key_261")) {
                                       return "Delete";
                                    } else if (keyName.equalsIgnoreCase("key_266")) {
                                       return "Page Up";
                                    } else if (keyName.equalsIgnoreCase("key_267")) {
                                       return "Page Down";
                                    } else if (keyName.equalsIgnoreCase("key_268")) {
                                       return "Home";
                                    } else if (keyName.equalsIgnoreCase("key_269")) {
                                       return "End";
                                    } else if (keyName.equalsIgnoreCase("key_257")) {
                                       return "Enter";
                                    } else if (keyName.equalsIgnoreCase("key_92")) {
                                       return "Backslash";
                                    } else if (keyName.equalsIgnoreCase("key_32")) {
                                       return "Space";
                                    } else if (keyName.equalsIgnoreCase("key_259")) {
                                       return "Backspace";
                                    } else if (keyName.equalsIgnoreCase("key_256")) {
                                       return "Escape";
                                    } else if (keyName.toLowerCase().matches("^kp_?[0-9]$|^keypad_?[0-9]$|^numpad_?[0-9]$")) {
                                       int num = Integer.parseInt(keyName.replaceAll("[^0-9]", ""));
                                       return "KP " + num;
                                    } else if (!keyName.equalsIgnoreCase("kp_enter") && !keyName.equalsIgnoreCase("kpenter")) {
                                       if (!keyName.equalsIgnoreCase("kp_add") && !keyName.equalsIgnoreCase("kpadd")) {
                                          if (!keyName.equalsIgnoreCase("kp_subtract") && !keyName.equalsIgnoreCase("kpsubtract")) {
                                             if (!keyName.equalsIgnoreCase("kp_multiply") && !keyName.equalsIgnoreCase("kpmultiply")) {
                                                if (!keyName.equalsIgnoreCase("kp_divide") && !keyName.equalsIgnoreCase("kpdivide")) {
                                                   if (!keyName.equalsIgnoreCase("kp_decimal") && !keyName.equalsIgnoreCase("kpdecimal")) {
                                                      if (keyName.toLowerCase().startsWith("key_")) {
                                                         try {
                                                            int code = Integer.parseInt(keyName.substring(4));
                                                            if (code >= 320 && code <= 329) {
                                                               return "KP " + (code - 320);
                                                            }

                                                            if (code == 330) {
                                                               return "KP .";
                                                            }

                                                            if (code == 331) {
                                                               return "KP /";
                                                            }

                                                            if (code == 332) {
                                                               return "KP *";
                                                            }

                                                            if (code == 333) {
                                                               return "KP -";
                                                            }

                                                            if (code == 334) {
                                                               return "KP +";
                                                            }

                                                            if (code == 335) {
                                                               return "KP Enter";
                                                            }

                                                            if (code == 336) {
                                                               return "KP =";
                                                            }

                                                            String glfwName = GLFW.glfwGetKeyName(code, 0);
                                                            if (glfwName != null && !glfwName.isEmpty()) {
                                                               String var11 = glfwName.substring(0, 1).toUpperCase();
                                                               return var11 + glfwName.substring(1);
                                                            }
                                                         } catch (Exception var7) {
                                                         }
                                                      }

                                                      if (keyName.length() > 0) {
                                                         String var10000 = keyName.substring(0, 1).toUpperCase();
                                                         return var10000 + keyName.substring(1);
                                                      } else {
                                                         return keyName;
                                                      }
                                                   } else {
                                                      return "KP .";
                                                   }
                                                } else {
                                                   return "KP /";
                                                }
                                             } else {
                                                return "KP *";
                                             }
                                          } else {
                                             return "KP -";
                                          }
                                       } else {
                                          return "KP +";
                                       }
                                    } else {
                                       return "KP Enter";
                                    }
                                 } else {
                                    return "Mouse 8";
                                 }
                              } else {
                                 return "Mouse 7";
                              }
                           } else {
                              return "Mouse 6";
                           }
                        } else {
                           return "Mouse 5";
                        }
                     } else {
                        return "Mouse 4";
                     }
                  } else {
                     return "Mouse 3";
                  }
               } else {
                  return "Mouse 2";
               }
            } else {
               return "Mouse 1";
            }
         } else {
            String[] parts = keyName.split("\\+");
            List<String> formatted = new ArrayList();

            for(String p : parts) {
               formatted.add(getKeyDisplayName(p.trim()));
            }

            return String.join(" + ", formatted);
         }
      } else {
         return "None";
      }
   }

   public static void updateTarget(int index, String item, String gui, String keyName, String type, boolean auto) {
      if (index >= 0 && index < targets.size()) {
         int keyCode = getKeyCode(keyName);
         targets.set(index, new ClickTarget(item, gui, keyName, keyCode, type, auto));
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
         targets.removeIf((t) -> t.id.equals(id));
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
      if (var3 == null) {
         return false;
      } else if (var3 instanceof BomboConfigGUI) {
         return false;
      } else if (!(var3 instanceof ChatScreen) && !(var3 instanceof AbstractSignEditScreen)) {
         AbstractContainerScreen screen;
         int slotIndex;
         label312: {
            if (var3 instanceof AbstractContainerScreen) {
               screen = (AbstractContainerScreen)var3;
               String title = screen.getTitle().getString().toLowerCase();
               if (title.contains("wardrobe") || title.contains("armor sets") || title.contains("equipment sets") || title.contains("loadouts")) {
                  List<String> wardrobeKeys = BomboConfig.get().wardrobeKeys;
                  if (wardrobeKeys != null) {
                     for(int i = 0; i < Math.min(12, wardrobeKeys.size()); ++i) {
                        String kName = (String)wardrobeKeys.get(i);
                        if (kName != null && !kName.isEmpty()) {
                           int code = getKeyCode(kName);
                           if (code != -1 && code == key) {
                              slotIndex = 36 + i;
                              if (title.contains("loadouts")) {
                                 int row = i / 3;
                                 int col = i % 3;
                                 slotIndex = 14 + row * 9 + col;
                                 break label312;
                              }

                              if (i < 9) {
                                 break label312;
                              }
                           }
                        }
                     }
                  }
               }

               if (title.contains("pets")) {
                  List<String> petKeys = BomboConfig.get().petKeys;
                  if (petKeys != null) {
                     for(int i = 0; i < Math.min(9, petKeys.size()); ++i) {
                        String kName = (String)petKeys.get(i);
                        if (kName != null && !kName.isEmpty()) {
                           int code = getKeyCode(kName);
                           if (code != -1 && code == key) {
                              String uuid = (String)BomboConfig.get().petKeybinds.get(String.valueOf(i + 1));
                              if (uuid != null && !uuid.isEmpty()) {
                                 for(int sIdx = 10; sIdx <= 43; ++sIdx) {
                                    if (sIdx < screen.getMenu().slots.size()) {
                                       Slot slot = (Slot)screen.getMenu().slots.get(sIdx);
                                       ItemStack stack = slot.getItem();
                                       String itemUuid = PetManager.getPetUuid(stack);
                                       if (itemUuid != null && itemUuid.equals(uuid)) {
                                          boolean isEquipped = false;
                                          if (BomboConfig.get().disableUnequipPet) {
                                             for(Component line : stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
                                                if (line.getString().contains("Click to despawn!")) {
                                                   isEquipped = true;
                                                   break;
                                                }
                                             }
                                          }

                                          if (!isEquipped && mc.gameMode != null && mc.player != null) {
                                             mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                                          }

                                          if (mc.player != null) {
                                             mc.player.closeContainer();
                                          }
                                          break;
                                       }
                                    }
                                 }
                              }

                              return true;
                           }
                        }
                     }
                  }

                  String saveKey = BomboConfig.get().savePetKey;
                  if (saveKey != null && !saveKey.isEmpty()) {
                     int code = getKeyCode(saveKey);
                     if (code != -1 && code == key) {
                        Slot hovered = ((AbstractContainerScreenAccessor)screen).getHoveredSlot();
                        if (hovered != null && hovered.hasItem()) {
                           String slotToSave = "1";

                           for(int i = 0; i < 9; ++i) {
                              String uuid = (String)BomboConfig.get().petKeybinds.get(String.valueOf(i + 1));
                              if (uuid == null || uuid.isEmpty()) {
                                 slotToSave = String.valueOf(i + 1);
                                 break;
                              }
                           }

                           PetManager.savePet((FabricClientCommandSource)null, slotToSave);
                           return true;
                        }
                     }
                  }
               }
            }

            if (targets != null && !targets.isEmpty()) {
               String title = var3.getTitle().getString().toLowerCase();
               boolean matched = false;

               for(ClickTarget t : targets) {
                  if (t != null && t.item != null && !t.item.trim().isEmpty()) {
                     if (t.keyCode == key || CustomBindsProcessor.matchesKey(t.keyName, key)) {
                        String guiTarget = t.gui != null ? t.gui.trim() : "";
                        if (guiTarget.isEmpty() || guiTarget.equalsIgnoreCase("all") || title.contains(guiTarget.toLowerCase())) {
                           executeClick(t, mc, var3 instanceof AbstractContainerScreen ? (AbstractContainerScreen)var3 : null);
                           matched = true;
                        }
                     }
                  }
               }

               if (matched) {
                  return true;
               }
            }

            if (var3 instanceof AbstractContainerScreen) {
               AbstractContainerScreen<?> contScreen = (AbstractContainerScreen)var3;
               if (key != -1) {
                  if (key == getKeyCode(BomboConfig.get().nextPageKey)) {
                     if (tryClickNavigation(mc, contScreen, "Next Page", "Levels ", "Next ")
                         || tryClickNavigation(mc, contScreen, "Scroll Right", "Scroll Up")) {
                        return true;
                     }
                  }

                  if (key == getKeyCode(BomboConfig.get().prevPageKey)) {
                     if (tryClickNavigation(mc, contScreen, "Previous Page", "Previous ")
                         || tryClickNavigation(mc, contScreen, "Scroll Left", "Scroll Down")) {
                        return true;
                     }
                  }

                  if (key == getKeyCode(BomboConfig.get().goBackKey) && tryClickNavigation(mc, contScreen, "Go Back", "Close", "Menu", "Back")) {
                     return true;
                  }

                  if (key == getKeyCode(BomboConfig.get().smartGoBackKey)) {
                     if (tryClickNavigation(mc, contScreen, "Previous Page", "Previous ")
                         || tryClickNavigation(mc, contScreen, "Scroll Left", "Scroll Down")
                         || tryClickNavigation(mc, contScreen, "Go Back", "Close", "Menu", "Back")) {
                        return true;
                     }
                  }
               }
            }

            return false;
         }

         if (slotIndex < screen.getMenu().slots.size()) {
            Slot slot = (Slot)screen.getMenu().slots.get(slotIndex);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
               boolean isEquipped = false;
               if (BomboConfig.get().disableUnequipWardrobe) {
                  for(Component line : stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
                     if (line.getString().contains(": Equipped")) {
                        isEquipped = true;
                        break;
                     }
                  }
               }

               if (!isEquipped && mc.gameMode != null && mc.player != null) {
                  mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                  if (!BomboConfig.get().hideCheats && BomboConfig.get().autoCloseWardrobe) {
                     mc.player.closeContainer();
                  }
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean tryClickNavigation(Minecraft mc, AbstractContainerScreen<?> screen, String... keywords) {
      AbstractContainerMenu menu = screen.getMenu();

      for(int i = 0; i < menu.slots.size(); ++i) {
         ItemStack stack = menu.getSlot(i).getItem();
         if (!stack.isEmpty()) {
            String name = stack.getHoverName().getString().toLowerCase().trim();
            if (!name.contains("backpack") && !name.contains("backwater") && !name.contains("background") && !name.contains("backbone")) {
               if (BomboConfig.get().apiDebug) {
                  DebugUtils.debug("nav", "Checking slot " + i + ": " + name);
               }

               for(String kw : keywords) {
                  String kwLower = kw.toLowerCase().trim();
                  boolean isMatch = false;
                  if (kwLower.equals("back")) {
                     isMatch = name.equals("back") || name.startsWith("back ") || name.endsWith(" back") || name.contains("go back") || name.contains("back to");
                  } else {
                     isMatch = name.contains(kwLower);
                  }

                  if (isMatch) {
                     if (BomboConfig.get().apiDebug) {
                        Bomboaddons.sendMessage("Â§a[Debug] Clicking navigation item: " + name + " in slot " + i);
                     }

                     mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.PICKUP, mc.player);
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   public static void listTargets(FabricClientCommandSource source) {
      source.sendFeedback(Component.literal("Â§8[Â§bBomboAddonsÂ§8]Â§r Â§6--- Click Targets ---"));
      if (targets.isEmpty()) {
         source.sendFeedback(Component.literal("Â§7  None"));
      } else {
         for(int i = 0; i < targets.size(); ++i) {
            ClickTarget t = (ClickTarget)targets.get(i);
            source.sendFeedback(Component.literal("Â§7" + i + ". Â§e" + t.item + " Â§7(GUI: Â§b" + t.gui + "Â§7, Key: Â§d" + t.keyName + "Â§7, Type: Â§a" + t.type + "Â§7, Auto: " + (t.auto ? "Â§aYes" : "Â§cNo") + "Â§7)"));
         }
      }

   }

   public static void onGuiOpen(AbstractContainerScreen screen) {
      WardrobeHelper.onGuiOpen(screen);
   }

   public static String getKeyName(int keyCode) {
      for(Map.Entry entry : KEY_MAP.entrySet()) {
         if ((Integer)entry.getValue() == keyCode) {
            return (String)entry.getKey();
         }
      }

      return "unknown";
   }

   private static void executeClick(ClickTarget target, Minecraft mc, AbstractContainerScreen screen) {
      DebugUtils.debug("clicker", "executeClick: item=" + target.item + ", gui=" + target.gui);
      if (target.item.startsWith("/") && !target.item.endsWith("/")) {
         String command = target.item.substring(1);
         DebugUtils.debug("clicker", "Sending command: " + command);

         try {
            mc.player.connection.sendCommand(command);
            mc.player.sendOverlayMessage(Component.literal("Â§b[Bomboaddons] Executing command: /" + command));
         } catch (Exception e) {
            DebugUtils.debug("clicker", "Failed to send command: " + e.getMessage());
            mc.player.sendOverlayMessage(Component.literal("Â§c[Bomboaddons] Failed to execute command: " + e.getMessage()));
         }

      } else if (screen != null) {
         List<Slot> slots = screen.getMenu().slots;
         int totalSlots = slots.size();

         for(int i = 0; i < totalSlots; ++i) {
            Slot slot = (Slot)slots.get(i);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
               String itemName = stack.getHoverName().getString().toLowerCase();
               boolean match = false;
               if (target.item.startsWith("l:")) {
                  String loreTarget = target.item.substring(2).toLowerCase();

                  for(Component line : stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
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
                  } catch (Exception var15) {
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

                     mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, button, ContainerInput.PICKUP, mc.player);
                     LocalPlayer var10000 = mc.player;
                     String var10001 = stack.getHoverName().getString();
                     var10000.sendOverlayMessage(Component.literal("Â§b[Bomboaddons] Clicking " + var10001 + " (Slot " + slot.index + ") in " + screen.getTitle().getString()));
                  }

                  return;
               }
            }
         }

         DebugUtils.debug("gui", "Item '" + target.item + "' not found in GUI");
      }
   }

   public static void saveTargets() {
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
            if (!CONFIG_FILE.getParentFile().exists()) {
               CONFIG_FILE.getParentFile().mkdirs();
            }

            Files.move(OLD_CONFIG_FILE.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      if (CONFIG_FILE.exists()) {
         try {
            FileReader reader = new FileReader(CONFIG_FILE);

            try {
               Type type = (new TypeToken<ArrayList<ClickTarget>>() {
               }).getType();
               List<ClickTarget> loadedTargets = (List)GSON.fromJson(reader, type);
               if (loadedTargets != null) {
                  for(ClickTarget t : loadedTargets) {
                     if (t.id == null) {
                        t.id = UUID.randomUUID();
                     }
                  }

                  targets = loadedTargets;
               }
            } catch (Throwable var71) {
               try {
                  reader.close();
               } catch (Throwable var5) {
                  var71.addSuppressed(var5);
               }

               throw var71;
            }

            reader.close();
         } catch (IOException var7) {
            var7.printStackTrace();
         }
      }

   }

   public static boolean shouldTriggerBind(BomboConfig.CommandBind bind) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return false;
      } else {
         if (!SkyblockUtils.matchesIslandRequirement(bind.requiredIsland)) {
            return false;
         }

         if (!SkyblockUtils.matchesArmorRequirement(bind.requiredArmor)) {
            return false;
         }

         return true;
      }
   }

   static {
      for(int i = 0; i <= 9; ++i) {
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

      for(int var2 = 1; var2 <= 12; ++var2) {
         KEY_MAP.put("f" + var2, 290 + var2 - 1);
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

      for(int var3 = 0; var3 <= 9; ++var3) {
         KEY_MAP.put("kp_" + var3, 320 + var3);
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
