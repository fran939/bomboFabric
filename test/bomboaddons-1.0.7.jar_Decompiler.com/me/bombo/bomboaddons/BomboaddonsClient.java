package me.bombo.bomboaddons;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_124;
import net.minecraft.class_1799;
import net.minecraft.class_2172;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_2558.class_10609;
import net.minecraft.class_2558.class_10610;

@Environment(EnvType.CLIENT)
public class BomboaddonsClient implements ClientModInitializer {
   public void onInitializeClient() {
      Bomboaddons.LOGGER.info("Bomboaddons client initialized!");
      ChatPeek.init();
      BazaarUtils.init();
      ItemHotkeys.init();
      ClientTickEvents.START_CLIENT_TICK.register((client) -> {
         LeftClickEtherwarp.onTick();
         AutoExperiments.onTick();
         if (client.field_1724 != null && BomboConfig.get().leftClickEtherwarp) {
            class_1799 stack = client.field_1724.method_6047();
            String name = stack.method_7964().getString();
            if ((name.contains("Aspect of the Void") || name.contains("Aspect of the End")) && client.field_1690.field_1886.method_1436()) {
               while(true) {
                  if (!client.field_1690.field_1886.method_1436()) {
                     client.field_1690.field_1886.method_23481(false);
                     LeftClickEtherwarp.onLeftClick();
                     break;
                  }
               }
            }
         }

      });
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("test").executes((context) -> {
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aTest command works!"));
            return 1;
         }));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("clickdebug").executes((context) -> {
            ClickLogic.toggleDebug();
            boolean debug = ClickLogic.isDebugMode();
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aClick debug " + (debug ? "enabled" : "disabled")));
            return 1;
         }));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("clickfolder").executes((context) -> {
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aConfig stored at: §e" + ClickLogic.getConfigFile().getAbsolutePath()));
            return 1;
         }));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("clicks").executes((context) -> {
            List<ClickLogic.ClickTarget> targets = ClickLogic.getTargets();
            if (targets.isEmpty()) {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§cNo click targets stored."));
            } else {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§6Stored click targets (Click index to remove):"));

               for(int i = 0; i < targets.size(); ++i) {
                  ClickLogic.ClickTarget t = (ClickLogic.ClickTarget)targets.get(i);
                  String idStr = t.id != null ? t.id.toString() : "null";
                  String var10000 = t.item;
                  String info = "§a" + var10000 + " §7in §a" + t.gui + (t.auto ? " §7(AUTO)" : " §7using §a" + t.keyName) + " §7(" + t.type + ")";
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§e" + i + ": ").method_27694((s) -> {
                     return s.method_10958(new class_10609("/clickremoveid " + idStr));
                  }).method_10852(class_2561.method_43470(info)));
               }
            }

            return 1;
         }));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("clickremoveid").then(ClientCommandManager.argument("id", StringArgumentType.string()).executes((context) -> {
            String id = StringArgumentType.getString(context, "id");
            ClickLogic.removeTargetById(id);
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aAttempted to remove target by ID."));
            return 1;
         })));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("clickremove").then(ClientCommandManager.argument("index", IntegerArgumentType.integer(0)).executes((context) -> {
            int index = IntegerArgumentType.getInteger(context, "index");
            List<ClickLogic.ClickTarget> targets = ClickLogic.getTargets();
            if (index >= 0 && index < targets.size()) {
               ClickLogic.removeTarget(index);
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aRemoved click target at index " + index));
            } else {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§cInvalid index. Use /clicks to see available targets."));
            }

            return 1;
         })));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("autoclick").then(ClientCommandManager.argument("guiName", StringArgumentType.string()).then(ClientCommandManager.argument("itemAndType", StringArgumentType.greedyString()).executes((context) -> {
            String itemAndType = StringArgumentType.getString(context, "itemAndType");
            String guiName = StringArgumentType.getString(context, "guiName");
            String itemName = itemAndType;
            String clickType = "left";
            String[] parts = itemAndType.split(" ");
            if (parts.length > 1) {
               String lastPart = parts[parts.length - 1].toLowerCase();
               if (lastPart.equals("left") || lastPart.equals("right") || lastPart.equals("middle")) {
                  clickType = lastPart;
                  StringBuilder sb = new StringBuilder();

                  for(int i = 0; i < parts.length - 1; ++i) {
                     if (i > 0) {
                        sb.append(" ");
                     }

                     sb.append(parts[i]);
                  }

                  itemName = sb.toString();
               }
            }

            ClickLogic.setTarget(itemName, guiName, "none", clickType, true);
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aSet auto click target to " + itemName + " in " + guiName + " (" + clickType + ")"));
            return 1;
         }))));
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("click").executes((context) -> {
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§cUsage: /click <guiName> <key> <itemName> [clickType]"));
            return 1;
         })).then(ClientCommandManager.argument("guiName", StringArgumentType.string()).then(ClientCommandManager.argument("key", StringArgumentType.string()).then(ClientCommandManager.argument("itemAndType", StringArgumentType.greedyString()).executes((context) -> {
            String itemAndType = StringArgumentType.getString(context, "itemAndType");
            String guiName = StringArgumentType.getString(context, "guiName");
            String key = StringArgumentType.getString(context, "key");
            String itemName = itemAndType;
            String clickType = "left";
            String[] parts = itemAndType.split(" ");
            if (parts.length > 1) {
               String lastPart = parts[parts.length - 1].toLowerCase();
               if (lastPart.equals("left") || lastPart.equals("right") || lastPart.equals("middle")) {
                  clickType = lastPart;
                  StringBuilder sb = new StringBuilder();

                  for(int i = 0; i < parts.length - 1; ++i) {
                     if (i > 0) {
                        sb.append(" ");
                     }

                     sb.append(parts[i]);
                  }

                  itemName = sb.toString();
               }
            }

            ClickLogic.setTarget(itemName, guiName, key, clickType, false);
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aSet click target to " + itemName + " in " + guiName + " using " + key + " (" + clickType + ")"));
            return 1;
         })))));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("bombo").executes((context) -> {
            class_2561 header = class_2561.method_43470("§6§lBomboaddons Settings:");
            ((FabricClientCommandSource)context.getSource()).sendFeedback(header);
            BomboConfig.Settings s = BomboConfig.get();
            ((FabricClientCommandSource)context.getSource()).sendFeedback(this.createToggle("Sign Calculator:", s.signCalculator));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(this.createToggle("Chest Clicker:", s.chestClicker));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(this.createToggle("Auto Clicker:", s.autoClicker));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(this.createToggle("SBE Commands:", s.sbeCommands));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(this.createToggle("Left Click Etherwarp:", s.leftClickEtherwarp));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(this.createToggle("Auto Experiments:", s.autoExperiments));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(this.createToggle("Sphinx Macro:", s.sphinxMacro));
            FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
            String var10001 = s.debugMode ? "§a[ON]" : "§c[OFF]";
            var10000.sendFeedback(class_2561.method_43470("§eDebug Mode: " + var10001).method_27694((style) -> {
               return style.method_10958(new class_10609("/bombotoggle debug"));
            }));
            var10000 = (FabricClientCommandSource)context.getSource();
            int var4 = s.experimentClickDelay;
            var10000.sendFeedback(class_2561.method_43470("§eExperiment Click Delay: §b" + var4 + "ms §7(Click to change)").method_27694((style) -> {
               return style.method_10958(new class_10610("/bomboset delay "));
            }));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§eExperiment Auto Close: " + (s.experimentAutoClose ? "§a[ON]" : "§c[OFF]")).method_27694((style) -> {
               return style.method_10958(new class_10609("/bombotoggle autoclose"));
            }));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§eExperiment Serum Count: §b" + s.experimentSerumCount + " §7(Click to change)").method_27694((style) -> {
               return style.method_10958(new class_10610("/bomboset serum "));
            }));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§6--- Keybinds ---"));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§eChat Peek Key: §b[" + s.chatPeekKey.toUpperCase() + "]").method_27694((style) -> {
               return style.method_10958(new class_10610("/bombosetkey chatpeek "));
            }));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§eTrade Key: §b[" + s.tradeKey.toUpperCase() + "]").method_27694((style) -> {
               return style.method_10958(new class_10610("/bombosetkey trade "));
            }));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§eRecipe Key: §b[" + s.recipeKey.toUpperCase() + "]").method_27694((style) -> {
               return style.method_10958(new class_10610("/bombosetkey recipe "));
            }));
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§eUsage Key: §b[" + s.usageKey.toUpperCase() + "]").method_27694((style) -> {
               return style.method_10958(new class_10610("/bombosetkey usage "));
            }));
            return 1;
         }));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("bombo").then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("highlight").then(ClientCommandManager.argument("name", StringArgumentType.string()).then(((RequiredArgumentBuilder)ClientCommandManager.argument("color", StringArgumentType.string()).suggests((context, builder) -> {
            List<String> colors = new ArrayList();
            class_124[] var3 = class_124.values();
            int var4 = var3.length;

            for(int var5 = 0; var5 < var4; ++var5) {
               class_124 cf = var3[var5];
               if (cf.method_543()) {
                  colors.add(cf.name());
               }
            }

            return class_2172.method_9265(colors, builder);
         }).then(ClientCommandManager.argument("showInvisible", IntegerArgumentType.integer(0, 1)).executes((context) -> {
            String name = StringArgumentType.getString(context, "name").toLowerCase();
            String colorString = StringArgumentType.getString(context, "color").toUpperCase();
            boolean showInvisible = IntegerArgumentType.getInteger(context, "showInvisible") == 1;

            try {
               class_124.valueOf(colorString);
            } catch (IllegalArgumentException var5) {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§c[Bomboaddons] Invalid color: " + colorString));
               return 1;
            }

            BomboConfig.get().highlights.put(name, new BomboConfig.HighlightInfo(colorString, showInvisible));
            BomboConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§a[Bomboaddons] Now highlighting " + name + " in " + colorString + (showInvisible ? " (even if invisible)" : "")));
            return 1;
         }))).executes((context) -> {
            String name = StringArgumentType.getString(context, "name").toLowerCase();
            String colorString = StringArgumentType.getString(context, "color").toUpperCase();

            try {
               class_124.valueOf(colorString);
            } catch (IllegalArgumentException var4) {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§c[Bomboaddons] Invalid color: " + colorString));
               return 1;
            }

            BomboConfig.get().highlights.put(name, new BomboConfig.HighlightInfo(colorString, false));
            BomboConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§a[Bomboaddons] Now highlighting " + name + " in " + colorString));
            return 1;
         })))).then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes((context) -> {
            String name = StringArgumentType.getString(context, "name").toLowerCase();
            if (BomboConfig.get().highlights.remove(name) != null) {
               BomboConfig.save();
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§a[Bomboaddons] Removed highlight for " + name));
            } else {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§c[Bomboaddons] No highlight found for " + name));
            }

            return 1;
         })))).then(ClientCommandManager.literal("list").executes((context) -> {
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§6--- Entity Highlights ---"));
            if (BomboConfig.get().highlights.isEmpty()) {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§7None"));
            } else {
               Iterator var1 = BomboConfig.get().highlights.entrySet().iterator();

               while(var1.hasNext()) {
                  Entry<String, BomboConfig.HighlightInfo> entry = (Entry)var1.next();
                  FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
                  String var10001 = (String)entry.getKey();
                  var10000.sendFeedback(class_2561.method_43470("§e" + var10001 + " §7- §b" + ((BomboConfig.HighlightInfo)entry.getValue()).color + " " + (((BomboConfig.HighlightInfo)entry.getValue()).showInvisible ? "§d[Inv]" : "")).method_10852(class_2561.method_43470(" §c[Remove]").method_27694((style) -> {
                     return style.method_10958(new class_10609("/bombo highlight remove " + (String)entry.getKey()));
                  })));
               }
            }

            return 1;
         }))).then(ClientCommandManager.literal("clear").executes((context) -> {
            BomboConfig.get().highlights.clear();
            BomboConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§a[Bomboaddons] Cleared all highlights"));
            return 1;
         }))));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("bombosetkey").then(ClientCommandManager.argument("action", StringArgumentType.string()).then(ClientCommandManager.argument("key", StringArgumentType.greedyString()).executes((context) -> {
            String action = StringArgumentType.getString(context, "action");
            String key = StringArgumentType.getString(context, "key").toLowerCase();
            if (ClickLogic.getKeyCode(key) == -1 && !key.equals("none")) {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§c[Bomboaddons] Invalid key name: " + key));
               return 1;
            } else {
               BomboConfig.Settings s = BomboConfig.get();
               String var4 = action.toLowerCase();
               byte var5 = -1;
               switch(var4.hashCode()) {
               case -934914674:
                  if (var4.equals("recipe")) {
                     var5 = 2;
                  }
                  break;
               case 110621028:
                  if (var4.equals("trade")) {
                     var5 = 1;
                  }
                  break;
               case 111574433:
                  if (var4.equals("usage")) {
                     var5 = 3;
                  }
                  break;
               case 1438226611:
                  if (var4.equals("chatpeek")) {
                     var5 = 0;
                  }
               }

               switch(var5) {
               case 0:
                  s.chatPeekKey = key;
                  break;
               case 1:
                  s.tradeKey = key;
                  break;
               case 2:
                  s.recipeKey = key;
                  break;
               case 3:
                  s.usageKey = key;
                  break;
               default:
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§cInvalid action."));
                  return 1;
               }

               BomboConfig.save();
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§a[Bomboaddons] Set " + action + " key to " + key.toUpperCase()));
               return 1;
            }
         }))));
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("bomboset").then(ClientCommandManager.literal("delay").then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(0)).executes((context) -> {
            int ms = IntegerArgumentType.getInteger(context, "ms");
            BomboConfig.get().experimentClickDelay = ms;
            BomboConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aSet experiment click delay to " + ms + "ms"));
            return 1;
         })))).then(ClientCommandManager.literal("serum").then(ClientCommandManager.argument("count", IntegerArgumentType.integer(0, 3)).executes((context) -> {
            int count = IntegerArgumentType.getInteger(context, "count");
            BomboConfig.get().experimentSerumCount = count;
            BomboConfig.save();
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aSet experiment serum count to " + count));
            return 1;
         }))));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("bombotoggle").then(ClientCommandManager.argument("feature", StringArgumentType.string()).executes((context) -> {
            String feature = StringArgumentType.getString(context, "feature");
            BomboConfig.Settings s = BomboConfig.get();
            String msg = "";
            if (feature.equals("signcalc")) {
               s.signCalculator = !s.signCalculator;
               msg = "Sign Calculator is now " + (s.signCalculator ? "§aON" : "§cOFF");
            } else if (feature.equals("chestclicker")) {
               s.chestClicker = !s.chestClicker;
               msg = "Chest Clicker is now " + (s.chestClicker ? "§aON" : "§cOFF");
            } else if (feature.equals("autoclicker")) {
               s.autoClicker = !s.autoClicker;
               msg = "Auto Clicker is now " + (s.autoClicker ? "§aON" : "§cOFF");
            } else if (feature.equals("sbecommands")) {
               s.sbeCommands = !s.sbeCommands;
               msg = "SBE Commands are now " + (s.sbeCommands ? "§aON" : "§cOFF");
            } else if (feature.equals("leftclicketherwarp")) {
               s.leftClickEtherwarp = !s.leftClickEtherwarp;
               msg = "Left Click Etherwarp is now " + (s.leftClickEtherwarp ? "§aON" : "§cOFF");
            } else if (feature.equals("autoexperiments")) {
               s.autoExperiments = !s.autoExperiments;
               msg = "Auto Experiments is now " + (s.autoExperiments ? "§aON" : "§cOFF");
               if (!s.autoExperiments) {
                  AutoExperiments.reset();
               }
            } else if (feature.equals("sphinxmacro")) {
               s.sphinxMacro = !s.sphinxMacro;
               msg = "Sphinx Macro is now " + (s.sphinxMacro ? "§aON" : "§cOFF");
            } else if (feature.equals("debug")) {
               s.debugMode = !s.debugMode;
               msg = "Debug Mode is now " + (s.debugMode ? "§aON" : "§cOFF");
            } else if (feature.equals("autoclose")) {
               s.experimentAutoClose = !s.experimentAutoClose;
               msg = "Experiment Auto Close is now " + (s.experimentAutoClose ? "§aON" : "§cOFF");
            }

            if (!msg.isEmpty()) {
               BomboConfig.save();
               class_310.method_1551().execute(() -> {
                  if (class_310.method_1551().field_1724 != null) {
                     try {
                        Method method = class_310.method_1551().field_1705.method_1743().getClass().getDeclaredMethod("bombo$removeMessages", Predicate.class);
                        method.setAccessible(true);
                        method.invoke(class_310.method_1551().field_1705.method_1743(), (guiMessage) -> {
                           try {
                              Method getContent = guiMessage.getClass().getDeclaredMethod("content");
                              getContent.setAccessible(true);
                              class_2561 c = (class_2561)getContent.invoke(guiMessage);
                              String text = c.getString();
                              return text.contains("Bomboaddons Settings") || text.contains("Sign Calculator:") || text.contains("Chest Clicker:") || text.contains("Auto Clicker:") || text.contains("SBE Commands:") || text.contains("Left Click Etherwarp:") || text.contains("Auto Experiments:") || text.contains("Sphinx Macro:") || text.contains("Debug Mode:") || text.contains("Experiment Click Delay:") || text.contains("Experiment Auto Close:") || text.contains("Experiment Serum Count:");
                           } catch (Exception var4) {
                              return false;
                           }
                        });
                     } catch (Exception var1) {
                     }

                     class_310.method_1551().field_1724.field_3944.method_45730("bombo");
                  }

               });
            }

            return 1;
         })));
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("bombosphinx").then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("question", StringArgumentType.greedyString()).executes((context) -> {
            String input = StringArgumentType.getString(context, "question");
            int qm = input.indexOf(63);
            if (qm < 0) {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§cUsage: /bombosphinx add <question?> <answer>"));
               return 1;
            } else {
               String q = input.substring(0, qm + 1).trim();
               String a = input.substring(qm + 1).trim();
               if (a.isEmpty()) {
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§cMissing answer. Usage: /bombosphinx add <question?> <answer>"));
                  return 1;
               } else {
                  SphinxMacro.addQuestion(q, a);
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aAdded Sphinx question: §b" + q + " §7-> §f" + a));
                  return 1;
               }
            }
         })))).then(ClientCommandManager.literal("delete").then(ClientCommandManager.argument("question", StringArgumentType.greedyString()).executes((context) -> {
            String q = StringArgumentType.getString(context, "question");
            if (SphinxMacro.removeQuestion(q)) {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aDeleted Sphinx question: §b" + q));
            } else {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§cQuestion not found: §b" + q));
            }

            return 1;
         })))).then(ClientCommandManager.literal("list").executes((context) -> {
            Map<String, String> map = SphinxMacro.getQuestions();
            if (map.isEmpty()) {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§eNo Sphinx questions stored."));
            } else {
               ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§6Stored Sphinx questions (" + map.size() + "):"));
               int count = 0;
               Iterator var3 = map.entrySet().iterator();

               while(var3.hasNext()) {
                  Entry<String, String> e = (Entry)var3.next();
                  FabricClientCommandSource var5;
                  if (count++ >= 20) {
                     var5 = (FabricClientCommandSource)context.getSource();
                     int var6 = map.size();
                     var5.sendFeedback(class_2561.method_43470("§7... and " + (var6 - 20) + " more."));
                     break;
                  }

                  var5 = (FabricClientCommandSource)context.getSource();
                  String var10001 = (String)e.getKey();
                  var5.sendFeedback(class_2561.method_43470("§7- §b" + var10001 + " §7-> §f" + (String)e.getValue()));
               }
            }

            return 1;
         }))).then(ClientCommandManager.literal("defaults").executes((context) -> {
            int added = SphinxMacro.addDefaultQuestions();
            ((FabricClientCommandSource)context.getSource()).sendFeedback(class_2561.method_43470("§aAdded " + added + " default questions."));
            return 1;
         })));
         String[] sbeCmds = new String[]{"nw", "cata", "skills", "slayer", "crimson", "trophyfish"};
         String[] var4 = sbeCmds;
         int var5 = sbeCmds.length;

         for(int var6 = 0; var6 < var5; ++var6) {
            String cmdName = var4[var6];
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal(cmdName).executes((context) -> {
               SBECommands.handleCommand(cmdName, class_310.method_1551().method_1548().method_1676(), "selected");
               return 1;
            })).then(((RequiredArgumentBuilder)ClientCommandManager.argument("name", StringArgumentType.string()).executes((context) -> {
               String name = StringArgumentType.getString(context, "name");
               SBECommands.handleCommand(cmdName, name, "selected");
               return 1;
            })).then(ClientCommandManager.argument("profile", StringArgumentType.string()).executes((context) -> {
               String name = StringArgumentType.getString(context, "name");
               String profile = StringArgumentType.getString(context, "profile");
               SBECommands.handleCommand(cmdName, name, profile);
               return 1;
            }))));
         }

         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("lf").then(ClientCommandManager.argument("username", StringArgumentType.string()).then(ClientCommandManager.argument("query", StringArgumentType.greedyString()).executes((context) -> {
            String username = StringArgumentType.getString(context, "username");
            String query = StringArgumentType.getString(context, "query");
            LF.show(username, query, false);
            return 1;
         }))));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("lfc").then(ClientCommandManager.argument("username", StringArgumentType.string()).then(ClientCommandManager.argument("query", StringArgumentType.greedyString()).executes((context) -> {
            String username = StringArgumentType.getString(context, "username");
            String query = StringArgumentType.getString(context, "query");
            LF.show(username, query, true);
            return 1;
         }))));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("lb").then(ClientCommandManager.argument("query", StringArgumentType.greedyString()).executes((context) -> {
            String query = StringArgumentType.getString(context, "query");
            LF.searchLocal(query);
            return 1;
         })));
      });
   }

   private class_2561 createToggle(String name, boolean enabled) {
      String var10000 = name.toLowerCase().replace(" ", "");
      String cmd = "/bombotoggle " + var10000.replace(":", "");
      if (name.contains("Sign")) {
         cmd = "/bombotoggle signcalc";
      } else if (name.contains("Chest")) {
         cmd = "/bombotoggle chestclicker";
      } else if (name.contains("Auto") && !name.contains("Experiments")) {
         cmd = "/bombotoggle autoclicker";
      } else if (name.contains("SBE")) {
         cmd = "/bombotoggle sbecommands";
      } else if (name.contains("Left")) {
         cmd = "/bombotoggle leftclicketherwarp";
      } else if (name.contains("Experiments")) {
         cmd = "/bombotoggle autoexperiments";
      } else if (name.contains("Sphinx")) {
         cmd = "/bombotoggle sphinxmacro";
      }

      return class_2561.method_43470("§e" + name + " " + (enabled ? "§a[ON]" : "§c[OFF]")).method_27694((style) -> {
         return style.method_10958(new class_10609(cmd));
      });
   }
}
