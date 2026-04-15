package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2487;
import net.minecraft.class_2505;
import net.minecraft.class_2507;
import net.minecraft.class_2520;
import net.minecraft.class_2558;
import net.minecraft.class_2561;
import net.minecraft.class_2568;
import net.minecraft.class_2583;
import net.minecraft.class_310;
import net.minecraft.class_5250;

@Environment(EnvType.CLIENT)
public class LF {
   private static final HttpClient CLIENT;
   private static final Map<String, String> NAME_CACHE;
   private static final Pattern UUID_PATTERN;
   private static final int MAX_RESULTS = 15;
   private static Class<?> ClickEventClass;
   private static Class<?> HoverEventClass;
   private static Object ClickAction_RUN;
   private static Object ClickAction_SUGGEST;
   private static Object HoverAction_SHOW_TEXT;
   private static boolean reflectionInitDone;

   public static void searchLocal(String query) {
      if (class_310.method_1551().field_1724 != null) {
         String raw;
         try {
            Object profile = class_310.method_1551().field_1724.method_7334();
            raw = (String)profile.getClass().getMethod("getName").invoke(profile);
            show(raw, query, false);
         } catch (Exception var3) {
            raw = class_310.method_1551().field_1724.method_5477().getString();
            show(removeColors(raw), query, false);
         }
      }

   }

   private static void initReflection() {
      if (!reflectionInitDone) {
         reflectionInitDone = true;

         try {
            Method[] var0 = class_2583.class.getMethods();
            int var1 = var0.length;

            for(int var2 = 0; var2 < var1; ++var2) {
               Method m = var0[var2];
               if (m.getParameterCount() == 1) {
                  Class<?> paramType = m.getParameterTypes()[0];
                  if (ClickEventClass == null && isClickEventClass(paramType)) {
                     ClickEventClass = paramType;
                  }

                  if (HoverEventClass == null && isHoverEventClass(paramType)) {
                     HoverEventClass = paramType;
                  }
               }
            }

            if (ClickEventClass != null) {
               ClickAction_RUN = findEnumConstant(ClickEventClass, "RUN_COMMAND");
               ClickAction_SUGGEST = findEnumConstant(ClickEventClass, "SUGGEST_COMMAND");
            }

            if (HoverEventClass != null) {
               HoverAction_SHOW_TEXT = findHoverAction(HoverEventClass, "SHOW_TEXT");
            }
         } catch (Exception var5) {
         }

      }
   }

   private static boolean isClickEventClass(Class<?> clazz) {
      Class[] var1 = clazz.getDeclaredClasses();
      int var2 = var1.length;

      for(int var3 = 0; var3 < var2; ++var3) {
         Class<?> inner = var1[var3];
         if (inner.isEnum()) {
            Object[] var5 = inner.getEnumConstants();
            int var6 = var5.length;

            for(int var7 = 0; var7 < var6; ++var7) {
               Object constant = var5[var7];
               if (constant.toString().equals("RUN_COMMAND")) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean isHoverEventClass(Class<?> clazz) {
      Field[] var1 = clazz.getDeclaredFields();
      int var2 = var1.length;

      int var3;
      for(var3 = 0; var3 < var2; ++var3) {
         Field f = var1[var3];
         if (f.getName().contains("SHOW_TEXT")) {
            return true;
         }
      }

      Class[] var9 = clazz.getDeclaredClasses();
      var2 = var9.length;

      for(var3 = 0; var3 < var2; ++var3) {
         Class<?> inner = var9[var3];
         Field[] var5 = inner.getDeclaredFields();
         int var6 = var5.length;

         for(int var7 = 0; var7 < var6; ++var7) {
            Field f = var5[var7];
            if (f.getName().contains("SHOW_TEXT")) {
               return true;
            }
         }
      }

      return false;
   }

   private static Object findEnumConstant(Class<?> parent, String name) {
      Class[] var2 = parent.getDeclaredClasses();
      int var3 = var2.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         Class<?> inner = var2[var4];
         if (inner.isEnum()) {
            Object[] var6 = inner.getEnumConstants();
            int var7 = var6.length;

            for(int var8 = 0; var8 < var7; ++var8) {
               Object constant = var6[var8];
               if (constant.toString().equals(name)) {
                  return constant;
               }
            }
         }
      }

      return null;
   }

   private static Object findHoverAction(Class<?> parent, String name) {
      try {
         Field[] var2 = parent.getDeclaredFields();
         int var3 = var2.length;

         int var4;
         for(var4 = 0; var4 < var3; ++var4) {
            Field f = var2[var4];
            if (Modifier.isStatic(f.getModifiers()) && f.getName().contains(name)) {
               f.setAccessible(true);
               return f.get((Object)null);
            }
         }

         Class[] var11 = parent.getDeclaredClasses();
         var3 = var11.length;

         for(var4 = 0; var4 < var3; ++var4) {
            Class<?> inner = var11[var4];
            Field[] var6 = inner.getDeclaredFields();
            int var7 = var6.length;

            for(int var8 = 0; var8 < var7; ++var8) {
               Field f = var6[var8];
               if (Modifier.isStatic(f.getModifiers()) && f.getName().contains(name)) {
                  f.setAccessible(true);
                  return f.get((Object)null);
               }
            }
         }
      } catch (Exception var10) {
      }

      return null;
   }

   public static void show(String username, String query, boolean coopMode) {
      initReflection();
      String modeText = coopMode ? " §d(Coop Mode)" : "";
      sendMessage("§7Looking up §b" + username + modeText + "§7...");
      getUuid(username).thenCompose((uuid) -> {
         if (uuid == null) {
            sendMessage("§cError: Could not find UUID for " + username);
            return CompletableFuture.completedFuture((Object)null);
         } else {
            String cleanUuid = uuid.toString().replace("-", "").toLowerCase();
            NAME_CACHE.put(cleanUuid, username);
            return getFeatureData(cleanUuid).thenApply((json) -> {
               return new LF.SearchContext(json, cleanUuid, username, coopMode);
            });
         }
      }).thenAccept((ctx) -> {
         if (ctx != null && ctx.json != null) {
            class_310.method_1551().execute(() -> {
               handleResponse(username, query, ctx);
            });
         } else {
            sendMessage("§cFailed to get data for " + username);
         }

      });
   }

   private static void handleResponse(String username, String query, LF.SearchContext ctx) {
      try {
         JsonObject root = JsonParser.parseString(ctx.json).getAsJsonObject();
         if (query == null || query.trim().isEmpty()) {
            sendMessage("§cPlease provide an item to search for.");
            return;
         }

         String lowerQuery = query.toLowerCase();
         boolean searchLore = false;
         if (lowerQuery.startsWith("lore:")) {
            searchLore = true;
            lowerQuery = lowerQuery.substring(5).trim();
         } else if (lowerQuery.startsWith("l:")) {
            searchLore = true;
            lowerQuery = lowerQuery.substring(2).trim();
         }

         AtomicInteger matchCount = new AtomicInteger(0);
         String searchTypeMsg = searchLore ? "lore '§f" + lowerQuery + "§e'" : "'§f" + lowerQuery + "§e'";
         sendMessage("§eSearch Results for " + searchTypeMsg + " in §b" + username + (ctx.coopMode ? " (Coop)" : "") + "§e:");
         searchJsonRecursive(root, "", lowerQuery, searchLore, matchCount, ctx, false);
         if (matchCount.get() == 0) {
            sendMessage("§cCould not find " + searchTypeMsg + "§c in any container.");
         }
      } catch (Exception var8) {
         sendMessage("§cError parsing data: " + var8.getMessage());
      }

   }

   private static void searchJsonRecursive(JsonElement element, String path, String query, boolean searchLore, AtomicInteger matchCount, LF.SearchContext ctx, boolean isInsideMembersNode) {
      if (matchCount.get() < 15) {
         if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();

            for(int i = 0; i < arr.size(); ++i) {
               searchJsonRecursive(arr.get(i), path + " > " + i, query, searchLore, matchCount, ctx, isInsideMembersNode);
            }

         } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String pathUuid;
            if (obj.has("data") && obj.get("data").isJsonPrimitive()) {
               pathUuid = obj.get("data").getAsString();
               decodeAndSearch(path, pathUuid, query, searchLore, matchCount, ctx);
            } else {
               String rawName;
               if (obj.has("sacks_counts")) {
                  if (isInsideMembersNode && !ctx.coopMode) {
                     pathUuid = extractLastUuidFromPath(path);
                     if (pathUuid != null && !pathUuid.equals(ctx.targetUuid)) {
                        return;
                     }
                  }

                  JsonObject sacks = obj.getAsJsonObject("sacks_counts");
                  Iterator var9 = sacks.entrySet().iterator();

                  label119:
                  while(true) {
                     int count;
                     String prettyName;
                     do {
                        do {
                           Entry sackEntry;
                           do {
                              if (!var9.hasNext()) {
                                 break label119;
                              }

                              sackEntry = (Entry)var9.next();
                              if (matchCount.get() >= 15) {
                                 return;
                              }

                              count = ((JsonElement)sackEntry.getValue()).getAsInt();
                           } while(count <= 0);

                           rawName = (String)sackEntry.getKey();
                           prettyName = formatSackName(rawName);
                        } while(searchLore);
                     } while(!prettyName.toLowerCase().contains(query) && !rawName.toLowerCase().contains(query));

                     int index = matchCount.incrementAndGet();
                     if (index > 15) {
                        return;
                     }

                     String memberUuid = extractLastUuidFromPath(path);
                     resolveName(memberUuid).thenAccept((memberName) -> {
                        String containerStr = "Sacks";
                        boolean shouldShowName = false;
                        if (memberName != null && !memberName.isEmpty()) {
                           if (ctx.coopMode) {
                              shouldShowName = true;
                           } else if (!memberName.equalsIgnoreCase(ctx.targetUsername)) {
                              shouldShowName = true;
                           }
                        }

                        if (shouldShowName) {
                           containerStr = containerStr + " (" + memberName + ")";
                        }

                        class_2568 itemHover = createHoverEventRobust("§eSack Item\n§7ID: §8" + rawName + "\n§7Amount: §6" + count);
                        class_2558 itemClick = createClickEventRobust("SUGGEST_COMMAND", "/gfs " + rawName + " ");
                        class_5250 itemLink = class_2561.method_43470("§a" + prettyName);
                        class_2583 itemStyle = class_2583.field_24360;
                        if (itemHover != null) {
                           itemStyle = itemStyle.method_10949(itemHover);
                        }

                        if (itemClick != null) {
                           itemStyle = itemStyle.method_10958(itemClick);
                        }

                        itemLink.method_10862(itemStyle);
                        class_5250 containerLink = class_2561.method_43470(" §r§7(" + containerStr + ")");
                        class_2561 finalMsg = class_2561.method_43470("§7#" + index + " §6" + count + "x ").method_10852(itemLink).method_10852(containerLink);
                        class_310.method_1551().execute(() -> {
                           sendMessage(finalMsg);
                        });
                     });
                  }
               }

               Iterator var18 = obj.entrySet().iterator();

               while(true) {
                  Entry entry;
                  String key;
                  boolean nextIsMemberNode;
                  do {
                     do {
                        do {
                           do {
                              if (!var18.hasNext()) {
                                 return;
                              }

                              entry = (Entry)var18.next();
                              key = (String)entry.getKey();
                           } while(key.equals("id"));
                        } while(key.equals("timestamp"));
                     } while(key.equals("sacks_counts"));

                     nextIsMemberNode = key.equalsIgnoreCase("members");
                     if (!isInsideMembersNode || ctx.coopMode) {
                        break;
                     }

                     rawName = key.replace("-", "").toLowerCase();
                  } while(rawName.length() == 32 && UUID_PATTERN.matcher(rawName).matches() && !rawName.equals(ctx.targetUuid));

                  rawName = path.isEmpty() ? key : path + " > " + key;
                  searchJsonRecursive((JsonElement)entry.getValue(), rawName, query, searchLore, matchCount, ctx, nextIsMemberNode);
               }
            }
         }
      }
   }

   private static void decodeAndSearch(String containerPath, String base64Data, String query, boolean searchLore, AtomicInteger matchCount, LF.SearchContext ctx) {
      try {
         byte[] compressed = Base64.getDecoder().decode(base64Data);
         ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
         class_2487 nbt = class_2507.method_10629(bis, class_2505.method_53898());
         nbt.method_10554("i").ifPresent((items) -> {
            for(int i = 0; i < items.size(); ++i) {
               if (matchCount.get() >= 15) {
                  return;
               }

               class_2487 itemTag = null;

               try {
                  class_2520 rawTag = items.method_10534(i);
                  if (rawTag instanceof class_2487) {
                     itemTag = (class_2487)rawTag;
                  }
               } catch (Exception var10) {
                  itemTag = (class_2487)items.method_10602(i).orElse((Object)null);
               }

               if (itemTag != null && !itemTag.method_33133() && itemTag.method_10545("tag")) {
                  itemTag.method_10562("tag").ifPresent((tag) -> {
                     tag.method_10562("display").ifPresent((display) -> {
                        display.method_10558("Name").ifPresent((fullName) -> {
                           StringBuilder tooltipBuilder = new StringBuilder();
                           tooltipBuilder.append(fullName).append("\n");
                           display.method_10554("Lore").ifPresent((loreList) -> {
                              for(int j = 0; j < loreList.size(); ++j) {
                                 try {
                                    tooltipBuilder.append((String)loreList.method_10608(j).orElse("")).append("\n");
                                 } catch (Exception var4) {
                                 }
                              }

                           });
                           boolean isMatch = false;
                           String cleanLore;
                           if (searchLore) {
                              cleanLore = removeColors(tooltipBuilder.toString()).toLowerCase();
                              if (cleanLore.contains(query)) {
                                 isMatch = true;
                              }
                           } else {
                              cleanLore = removeColors(fullName).toLowerCase();
                              if (cleanLore.contains(query)) {
                                 isMatch = true;
                              }
                           }

                           if (isMatch) {
                              int index = matchCount.incrementAndGet();
                              if (index > 15) {
                                 return;
                              }

                              int count = (Integer)itemTag.method_10571("Count").map(Byte::intValue).orElse(1);
                              String[] containerInfo = getContainerInfo(containerPath, i);
                              String prettyContainer = containerInfo[0];
                              String commandToRun = containerInfo[1];
                              String memberUuid = extractLastUuidFromPath(containerPath);
                              resolveName(memberUuid).thenAccept((memberName) -> {
                                 String containerString = prettyContainer;
                                 boolean shouldShowName = false;
                                 if (memberName != null && !memberName.isEmpty()) {
                                    if (ctx.coopMode) {
                                       shouldShowName = true;
                                    } else if (!memberName.equalsIgnoreCase(ctx.targetUsername)) {
                                       shouldShowName = true;
                                    }
                                 }

                                 if (shouldShowName) {
                                    containerString = prettyContainer + " (" + memberName + ")";
                                 }

                                 class_2568 itemHover = createHoverEventRobust(tooltipBuilder.toString());
                                 class_2558 itemClick = createClickEventRobust("SUGGEST_COMMAND", fullName);
                                 class_5250 itemLink = class_2561.method_43470(fullName);
                                 class_2583 itemStyle = class_2583.field_24360;
                                 if (itemHover != null) {
                                    itemStyle = itemStyle.method_10949(itemHover);
                                 }

                                 if (itemClick != null) {
                                    itemStyle = itemStyle.method_10958(itemClick);
                                 }

                                 itemLink.method_10862(itemStyle);
                                 class_5250 containerLink = class_2561.method_43470(" §r§7(" + containerString + ")");
                                 if (commandToRun != null) {
                                    class_2558 runClick = createClickEventRobust("RUN_COMMAND", commandToRun);
                                    class_2568 runHover = createHoverEventRobust("§eClick to run: " + commandToRun);
                                    class_2583 runStyle = class_2583.field_24360;
                                    if (runClick != null) {
                                       runStyle = runStyle.method_10958(runClick);
                                    }

                                    if (runHover != null) {
                                       runStyle = runStyle.method_10949(runHover);
                                    }

                                    containerLink.method_10862(runStyle);
                                 }

                                 class_2561 finalMsg = class_2561.method_43470("§7#" + index + " §6" + count + "x ").method_10852(itemLink).method_10852(containerLink);
                                 class_310.method_1551().execute(() -> {
                                    sendMessage(finalMsg);
                                 });
                              });
                           }

                        });
                     });
                  });
               }
            }

         });
      } catch (Exception var9) {
      }

   }

   private static String formatSackName(String raw) {
      String[] words = raw.split("_");
      StringBuilder sb = new StringBuilder();
      String[] var3 = words;
      int var4 = words.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         String w = var3[var5];
         if (!w.isEmpty()) {
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(" ");
         }
      }

      return sb.toString().trim();
   }

   private static String[] getContainerInfo(String raw, int itemIndex) {
      String s = raw.toLowerCase();
      String cmd = null;
      int num = -1;
      Matcher m = Pattern.compile("(\\d+)$").matcher(s);
      if (m.find()) {
         num = Integer.parseInt(m.group(1));
         if (s.contains(">")) {
            ++num;
         }
      }

      String name;
      if (s.contains("backpack_contents")) {
         if (num == -1) {
            num = itemIndex / 54 + 1;
         }

         name = "Backpack " + num;
         cmd = "/backpack " + num;
      } else if (!s.contains("ender_chest_contents") && !s.contains("ender")) {
         if (!s.contains("wardrobe_contents") && !s.contains("wardrobe")) {
            if (!s.contains("personal_vault_contents") && !s.contains("vault")) {
               if (s.contains("quiver")) {
                  name = "Quiver";
                  cmd = "/quiver";
               } else if (!s.contains("potion_bag") && !s.contains("potion")) {
                  if (!s.contains("talisman_bag") && !s.contains("accessory")) {
                     if (s.contains("equipment")) {
                        name = "Equipment";
                     } else if (s.contains("museum")) {
                        name = "Museum";
                        cmd = "/warp museum";
                     } else if (s.contains("pets")) {
                        name = "Pets";
                        cmd = "/pets";
                     } else if (!s.contains("inv_contents") && !s.contains("inv_armor") && !s.equals("inventory")) {
                        String[] parts = raw.split(" > ");
                        name = capitalize(parts[parts.length - 1].replace("_", " ").trim());
                     } else {
                        name = "Inventory";
                     }
                  } else {
                     name = "Accessory Bag";
                     cmd = "/accessories";
                  }
               } else {
                  name = "Potion Bag";
                  cmd = "/potionbag";
               }
            } else {
               name = "Vault";
               cmd = "/bank";
            }
         } else {
            if (num == -1) {
               num = itemIndex / 36 + 1;
            }

            name = "Wardrobe " + num;
            cmd = "/wardrobe " + num;
         }
      } else {
         if (num == -1) {
            num = itemIndex / 45 + 1;
         }

         name = "Ender Chest " + num;
         cmd = "/ec " + num;
      }

      return new String[]{name, cmd};
   }

   private static class_2558 createClickEventRobust(String actionType, String value) {
      try {
         Class[] var2 = class_2558.class.getDeclaredClasses();
         int var3 = var2.length;

         int var4;
         for(var4 = 0; var4 < var3; ++var4) {
            Class<?> inner = var2[var4];
            if (class_2558.class.isAssignableFrom(inner)) {
               Constructor[] var6 = inner.getDeclaredConstructors();
               int var7 = var6.length;

               for(int var8 = 0; var8 < var7; ++var8) {
                  Constructor<?> c = var6[var8];
                  if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == String.class) {
                     c.setAccessible(true);
                     class_2558 instance = (class_2558)c.newInstance(value);
                     Method[] var11 = class_2558.class.getMethods();
                     int var12 = var11.length;

                     for(int var13 = 0; var13 < var12; ++var13) {
                        Method m = var11[var13];
                        if (m.getParameterCount() == 0 && m.getReturnType().isEnum()) {
                           m.setAccessible(true);
                           Enum<?> actionEnum = (Enum)m.invoke(instance);
                           if (actionEnum != null && actionEnum.name().equalsIgnoreCase(actionType)) {
                              return instance;
                           }
                        }
                     }
                  }
               }
            }
         }

         Class<?> actionClass = null;
         Class[] var18 = class_2558.class.getDeclaredClasses();
         var4 = var18.length;

         int var21;
         for(var21 = 0; var21 < var4; ++var21) {
            Class<?> inner = var18[var21];
            if (inner.isEnum()) {
               actionClass = inner;
               break;
            }
         }

         if (actionClass != null) {
            Object targetEnum = null;
            Object[] var20 = actionClass.getEnumConstants();
            var21 = var20.length;

            int var24;
            for(var24 = 0; var24 < var21; ++var24) {
               Object constant = var20[var24];
               if (((Enum)constant).name().equalsIgnoreCase(actionType)) {
                  targetEnum = constant;
                  break;
               }
            }

            if (targetEnum != null) {
               Constructor[] var22 = class_2558.class.getDeclaredConstructors();
               var21 = var22.length;

               for(var24 = 0; var24 < var21; ++var24) {
                  Constructor<?> c = var22[var24];
                  if (c.getParameterCount() == 2) {
                     c.setAccessible(true);
                     return (class_2558)c.newInstance(targetEnum, value);
                  }
               }
            }
         }
      } catch (Throwable var16) {
      }

      return null;
   }

   private static class_2568 createHoverEventRobust(String text) {
      try {
         class_2561 content = class_2561.method_43470(text);
         Class[] var2 = class_2568.class.getDeclaredClasses();
         int var3 = var2.length;

         int var4;
         for(var4 = 0; var4 < var3; ++var4) {
            Class<?> inner = var2[var4];
            Constructor[] var6 = inner.getDeclaredConstructors();
            int var7 = var6.length;

            for(int var8 = 0; var8 < var7; ++var8) {
               Constructor<?> c = var6[var8];
               c.setAccessible(true);
               if (c.getParameterCount() == 1 && c.getParameterTypes()[0].isAssignableFrom(class_2561.class)) {
                  Object showTextInstance = c.newInstance(content);
                  if (showTextInstance instanceof class_2568) {
                     return (class_2568)showTextInstance;
                  }
               }
            }
         }

         if (!class_2568.class.isInterface()) {
            Class<?> actionClass = null;
            Class[] var13 = class_2568.class.getDeclaredClasses();
            var4 = var13.length;

            int var16;
            for(var16 = 0; var16 < var4; ++var16) {
               Class<?> inner = var13[var16];
               if (inner.isEnum() || inner.getSimpleName().contains("Action")) {
                  actionClass = inner;
                  break;
               }
            }

            if (actionClass != null) {
               Object showTextAction = null;
               Field[] var15 = actionClass.getDeclaredFields();
               var16 = var15.length;

               int var19;
               for(var19 = 0; var19 < var16; ++var19) {
                  Field f = var15[var19];
                  if (f.getName().contains("SHOW_TEXT") || f.getName().contains("TEXT")) {
                     f.setAccessible(true);
                     showTextAction = f.get((Object)null);
                     break;
                  }
               }

               if (showTextAction != null) {
                  Constructor[] var17 = class_2568.class.getDeclaredConstructors();
                  var16 = var17.length;

                  for(var19 = 0; var19 < var16; ++var19) {
                     Constructor<?> c = var17[var19];
                     c.setAccessible(true);
                     if (c.getParameterCount() == 2) {
                        return (class_2568)c.newInstance(showTextAction, content);
                     }
                  }
               }
            }
         }
      } catch (Throwable var11) {
      }

      return null;
   }

   private static String extractLastUuidFromPath(String path) {
      Matcher matcher = UUID_PATTERN.matcher(path.replace("-", ""));

      String lastMatch;
      for(lastMatch = null; matcher.find(); lastMatch = matcher.group(0).toLowerCase()) {
      }

      return lastMatch;
   }

   private static CompletableFuture<String> resolveName(String uuid) {
      if (uuid == null) {
         return CompletableFuture.completedFuture((Object)null);
      } else {
         return NAME_CACHE.containsKey(uuid.toLowerCase()) ? CompletableFuture.completedFuture((String)NAME_CACHE.get(uuid.toLowerCase())) : fetchString("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid).thenCompose((response) -> {
            if (response != null && response.contains("\"name\"")) {
               try {
                  JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                  if (json.has("name")) {
                     String name = json.get("name").getAsString();
                     NAME_CACHE.put(uuid.toLowerCase(), name);
                     return CompletableFuture.completedFuture(name);
                  }
               } catch (Exception var4) {
               }
            }

            return fetchString("https://api.ashcon.app/mojang/v2/user/" + uuid).thenApply((fallbackRes) -> {
               if (fallbackRes == null) {
                  return null;
               } else {
                  try {
                     JsonObject json = JsonParser.parseString(fallbackRes).getAsJsonObject();
                     if (json.has("username")) {
                        String name = json.get("username").getAsString();
                        NAME_CACHE.put(uuid.toLowerCase(), name);
                        return name;
                     }
                  } catch (Exception var4) {
                  }

                  return null;
               }
            });
         });
      }
   }

   private static String capitalize(String str) {
      if (str != null && !str.isEmpty()) {
         char var10000 = Character.toUpperCase(str.charAt(0));
         return var10000 + str.substring(1);
      } else {
         return str;
      }
   }

   private static String removeColors(String text) {
      return text.replaceAll("(?i)§[0-9a-fk-or]", "");
   }

   private static void sendMessage(String text) {
      if (class_310.method_1551().field_1724 != null) {
         class_310.method_1551().field_1724.method_7353(class_2561.method_43470(text), false);
      }

   }

   private static void sendMessage(class_2561 component) {
      if (class_310.method_1551().field_1724 != null) {
         class_310.method_1551().field_1724.method_7353(component, false);
      }

   }

   private static CompletableFuture<UUID> getUuid(String username) {
      return fetchString("https://api.ashcon.app/mojang/v2/uuid/" + username).thenCompose((response) -> {
         if (response != null) {
            try {
               if (response.contains("-") && response.length() == 36) {
                  return CompletableFuture.completedFuture(UUID.fromString(response.trim()));
               }

               JsonObject json = JsonParser.parseString(response).getAsJsonObject();
               if (json.has("uuid")) {
                  return CompletableFuture.completedFuture(UUID.fromString(json.get("uuid").getAsString()));
               }
            } catch (Exception var3) {
            }
         }

         return fetchString("https://api.mojang.com/users/profiles/minecraft/" + username).thenApply((mojangRes) -> {
            if (mojangRes == null) {
               return null;
            } else {
               try {
                  JsonObject json = JsonParser.parseString(mojangRes).getAsJsonObject();
                  if (json.has("id")) {
                     return makeUuid(json.get("id").getAsString());
                  }
               } catch (Exception var2) {
               }

               return null;
            }
         });
      });
   }

   private static CompletableFuture<String> getFeatureData(String cleanUuid) {
      return fetchString("https://bomboapi.frandl938.workers.dev/" + cleanUuid).thenCompose((response) -> {
         return isValidJson(response) ? CompletableFuture.completedFuture(response) : fetchString("https://profile.snailify.workers.dev/?uuid=" + cleanUuid);
      });
   }

   private static CompletableFuture<String> fetchString(String url) {
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Minecraft-Mod-1.21").GET().build();
      return CLIENT.sendAsync(request, BodyHandlers.ofString()).thenApply((res) -> {
         return res.statusCode() == 200 ? (String)res.body() : null;
      }).exceptionally((e) -> {
         return null;
      });
   }

   private static UUID makeUuid(String undashed) {
      return UUID.fromString(undashed.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
   }

   private static boolean isValidJson(String test) {
      try {
         JsonParser.parseString(test);
         return true;
      } catch (Exception var2) {
         return false;
      }
   }

   static {
      CLIENT = HttpClient.newBuilder().version(Version.HTTP_2).connectTimeout(Duration.ofSeconds(10L)).build();
      NAME_CACHE = new ConcurrentHashMap();
      UUID_PATTERN = Pattern.compile("[0-9a-f]{32}", 2);
      ClickEventClass = null;
      HoverEventClass = null;
      ClickAction_RUN = null;
      ClickAction_SUGGEST = null;
      HoverAction_SHOW_TEXT = null;
      reflectionInitDone = false;
   }

   @Environment(EnvType.CLIENT)
   private static record SearchContext(String json, String targetUuid, String targetUsername, boolean coopMode) {
      private SearchContext(String json, String targetUuid, String targetUsername, boolean coopMode) {
         this.json = json;
         this.targetUuid = targetUuid;
         this.targetUsername = targetUsername;
         this.coopMode = coopMode;
      }

      public String json() {
         return this.json;
      }

      public String targetUuid() {
         return this.targetUuid;
      }

      public String targetUsername() {
         return this.targetUsername;
      }

      public boolean coopMode() {
         return this.coopMode;
      }
   }
}
