package me.bombo.bomboaddons_final;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.phys.EntityHitResult;

@Environment(EnvType.CLIENT)
public class BomboaddonsClient implements ClientModInitializer {
    private static final String PREFIX = "§8[§bBomboAddons§8]§r ";
    private static boolean openGuiNextTick = false;

    public void onInitializeClient() {
        Bomboaddons.LOGGER.info("[BomboAddons] onInitializeClient start");
        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                Bomboaddons.LOGGER.info("[BomboAddons] Command registration lambda reached!");

                // --- PRIORITY 1: /click and /clicks ---
                try {
                    dispatcher.register(ClientCommandManager.literal("clicks")
                            .executes(context -> {
                                ClickLogic.listTargets(context.getSource());
                                return 1;
                            }));
                    dispatcher.register(ClientCommandManager.literal("click")
                            .then(ClientCommandManager.literal("add")
                                    .then(ClientCommandManager.argument("item", StringArgumentType.string())
                                            .then(ClientCommandManager.argument("gui", StringArgumentType.string())
                                                    .then(ClientCommandManager.argument("key", StringArgumentType.string())
                                                            .then(ClientCommandManager.argument("type", StringArgumentType.string())
                                                                    .executes(context -> {
                                                                        String item = StringArgumentType.getString(context, "item");
                                                                        String gui = StringArgumentType.getString(context, "gui");
                                                                        String key = StringArgumentType.getString(context, "key");
                                                                        String type = StringArgumentType.getString(context, "type");
                                                                        ClickLogic.setTarget(item, gui, key, type, false);
                                                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§aAdded click target for §e" + item));
                                                                        return 1;
                                                                    })
                                                                    .then(ClientCommandManager.argument("auto", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                                            .executes(context -> {
                                                                                String item = StringArgumentType.getString(context, "item");
                                                                                String gui = StringArgumentType.getString(context, "gui");
                                                                                String key = StringArgumentType.getString(context, "key");
                                                                                String type = StringArgumentType.getString(context, "type");
                                                                                boolean auto = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "auto");
                                                                                ClickLogic.setTarget(item, gui, key, type, auto);
                                                                                context.getSource().sendFeedback(Component.literal(PREFIX + "§aAdded " + (auto ? "auto " : "") + "click target for §e" + item));
                                                                                return 1;
                                                                            })))))))
                            .then(ClientCommandManager.literal("remove")
                                    .then(ClientCommandManager.argument("id", StringArgumentType.string())
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "id");
                                                try {
                                                    int index = Integer.parseInt(id);
                                                    ClickLogic.removeTarget(index);
                                                } catch (NumberFormatException e) {
                                                    ClickLogic.removeTargetById(id);
                                                }
                                                context.getSource().sendFeedback(Component.literal(PREFIX + "§aRemoved click target."));
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("list")
                                    .executes(context -> {
                                        ClickLogic.listTargets(context.getSource());
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("debug")
                                    .executes(context -> {
                                        ClickLogic.toggleDebug();
                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§7Click Debug: " + (ClickLogic.isDebugMode() ? "§aON" : "§cOFF")));
                                        return 1;
                                    })));
                    Bomboaddons.LOGGER.info("[BomboAddons] Priority commands (/click, /clicks) registered successfully!");
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register priority /click commands!", t);
                }

                // --- PRIORITY 2: /b, /ba, /bombo and subcommands ---
                try {
                    LiteralArgumentBuilder<FabricClientCommandSource> bBuilder = ClientCommandManager.literal("b");
                    LiteralArgumentBuilder<FabricClientCommandSource> baBuilder = ClientCommandManager.literal("ba");
                    LiteralArgumentBuilder<FabricClientCommandSource> bomboBuilder = ClientCommandManager.literal("bombo");

                    java.util.function.Consumer<LiteralArgumentBuilder<FabricClientCommandSource>> setupCommands = builder -> {
                        builder.executes(context -> {
                            openGuiNextTick = true;
                            return 1;
                        });

                        // --- Diagnostics ---
                        builder.then(ClientCommandManager.literal("area").executes(context -> {
                            String loc = SkyblockUtils.getLocation();
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§7Current Area: §a" + loc));
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("sb").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§6Scoreboard Lines:"));
                            List<String> lines = SkyblockUtils.getSidebarLines(Minecraft.getInstance().level.getScoreboard(),
                                    Minecraft.getInstance().level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR));
                            for (String line : lines)
                                context.getSource().sendFeedback(Component.literal("§7- §r" + line));
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("tab").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§bTab List Lines:"));
                            for (Component line : SkyblockUtils.getTabListLines()) {
                                context.getSource().sendFeedback(Component.empty().append("§7- ").append(line));
                            }
                            return 1;
                        }));

                        builder.then(ClientCommandManager.literal("api").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§eChecking and Reloading APIs..."));
                            LowestBinManager.reload();
                            context.getSource().sendFeedback(Component.literal(LowestBinManager.getStatus()));
                            return 1;
                        }));

                        // --- SBE Commands (Translation to /b) ---
                        String[] sbeSubs = { "nw", "cata", "skills", "slayer", "trophyfish", "crimson" };
                        for (String s : sbeSubs) {
                            builder.then(ClientCommandManager.literal(s)
                                    .executes(context -> {
                                        SBECommands.handleCommand(s, Minecraft.getInstance().player.getName().getString(), null);
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                            .executes(context -> {
                                                SBECommands.handleCommand(s, StringArgumentType.getString(context, "name"),
                                                        null);
                                                return 1;
                                            })
                                            .then(ClientCommandManager.argument("profile", StringArgumentType.word())
                                                    .executes(context -> {
                                                        SBECommands.handleCommand(s,
                                                                StringArgumentType.getString(context, "name"),
                                                                StringArgumentType.getString(context, "profile"));
                                                        return 1;
                                                    }))));
                        }

                        // --- Utilities ---
                        builder.then(ClientCommandManager.literal("ec")
                                .executes(context -> {
                                    executeTracked(CommandTracker.getLastEc());
                                    return 1;
                                }));
                        builder.then(ClientCommandManager.literal("bp")
                                .executes(context -> {
                                    executeTracked(CommandTracker.getLastBp());
                                    return 1;
                                }));
                        builder.then(ClientCommandManager.literal("sh")
                                .executes(context -> {
                                    executeTracked(CommandTracker.getLastSh());
                                    return 1;
                                }));

                        // --- Highlight Command ---
                        builder.then(ClientCommandManager.literal("highlight")
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name").toLowerCase();
                                                    if (BomboConfig.get().highlights.remove(name) != null) {
                                                        BomboConfig.save();
                                                        context.getSource()
                                                                .sendFeedback(Component.literal(
                                                                        PREFIX + "§aRemoved highlight for: §e"
                                                                                + name));
                                                    } else {
                                                        context.getSource()
                                                                .sendFeedback(Component.literal(
                                                                        PREFIX + "§cNo highlight found for: §e"
                                                                                + name));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> {
                                            context.getSource().sendFeedback(
                                                    Component.literal(PREFIX + "§6--- Persistent Entity Highlights ---"));
                                            if (BomboConfig.get().highlights.isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal("  §7None"));
                                            } else {
                                                for (Map.Entry<String, BomboConfig.HighlightInfo> entry : BomboConfig.get().highlights
                                                        .entrySet()) {
                                                    String targetName = entry.getKey();
                                                    String color = entry.getValue().color;
                                                    
                                                    ClickEvent click = LF.createClickEventRobust("RUN_COMMAND", "/bombo highlight remove " + targetName);
                                                    Component removeBtn = Component.literal(" §c[Remove]");
                                                    if (click != null) {
                                                        removeBtn = Component.literal(" §c[Remove]").withStyle(style -> style.withClickEvent(click));
                                                    }
                                                            
                                                    context.getSource().sendFeedback(Component.literal("  §e" + targetName + " §7- §b" + color)
                                                            .append(removeBtn));
                                                }
                                            }
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            BomboConfig.get().highlights.clear();
                                            BomboConfig.save();
                                            SlotHighlight.clearTargetSlot();
                                            context.getSource()
                                                    .sendFeedback(Component.literal(PREFIX + "§aCleared all highlights."));
                                            return 1;
                                        }))
                                .executes(context -> {
                                    SlotHighlight.clearTargetSlot();
                                    context.getSource()
                                            .sendFeedback(Component.literal(PREFIX + "§7Temporary highlights cleared."));
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String args = StringArgumentType.getString(context, "args");
                                            String[] parts = args.split(" ");
                                            if (parts.length < 2) {
                                                context.getSource().sendFeedback(Component.literal(
                                                        PREFIX + "§cUsage: /bombo highlight <name> <color> [showInvisible (0/1)]"));
                                                return 1;
                                            }

                                            int showInvisible = -1;
                                            String colorString;
                                            int nameEndIndex;
                                            String lastPart = parts[parts.length - 1];

                                            if (lastPart.equals("0") || lastPart.equals("1")) {
                                                showInvisible = Integer.parseInt(lastPart);
                                                if (parts.length < 3) {
                                                    context.getSource().sendFeedback(Component.literal(
                                                            PREFIX + "§cUsage: /bombo highlight <name> <color> [showInvisible (0/1)]"));
                                                    return 1;
                                                }
                                                colorString = parts[parts.length - 2].toUpperCase();
                                                nameEndIndex = parts.length - 3;
                                            } else {
                                                colorString = lastPart.toUpperCase();
                                                nameEndIndex = parts.length - 2;
                                            }

                                            try {
                                                ChatFormatting.valueOf(colorString);
                                            } catch (IllegalArgumentException e) {
                                                context.getSource().sendFeedback(
                                                        Component.literal(PREFIX + "§cInvalid color: " + colorString));
                                                return 1;
                                            }

                                            StringBuilder nameBuilder = new StringBuilder();
                                            for (int i = 0; i <= nameEndIndex; ++i) {
                                                if (i > 0)
                                                    nameBuilder.append(" ");
                                                nameBuilder.append(parts[i]);
                                            }
                                            String name = nameBuilder.toString().toLowerCase();

                                            boolean si = showInvisible == 1;
                                            BomboConfig.get().highlights.put(name,
                                                    new BomboConfig.HighlightInfo(colorString, si));
                                            BomboConfig.save();

                                            context.getSource().sendFeedback(Component.literal(PREFIX + "§aNow highlighting §e"
                                                    + name + " §7in §b" + colorString + (si ? " §d(even if invisible)" : "")));
                                            return 1;
                                        })));

                        // Default
                        builder.then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                                .executes(context -> {
                                    openGuiNextTick = true;
                                    return 1;
                                }));
                    };

                    setupCommands.accept(bBuilder);
                    setupCommands.accept(baBuilder);
                    setupCommands.accept(bomboBuilder);

                    dispatcher.register(bBuilder);
                    dispatcher.register(baBuilder);
                    dispatcher.register(bomboBuilder);
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register /bombo commands!", t);
                }

                // --- PRIORITY 3: SBE Roots ---
                try {
                    String[] sbeRoots = { "nw", "cata", "skills", "slayer", "trophyfish", "crimson" };
                    for (String s : sbeRoots) {
                        dispatcher.register(ClientCommandManager.literal(s)
                                .executes(context -> {
                                    SBECommands.handleCommand(s, Minecraft.getInstance().player.getName().getString(), null);
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            SBECommands.handleCommand(s, StringArgumentType.getString(context, "name"), null);
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("profile", StringArgumentType.word())
                                                .executes(context -> {
                                                    SBECommands.handleCommand(s,
                                                            StringArgumentType.getString(context, "name"),
                                                            StringArgumentType.getString(context, "profile"));
                                                    return 1;
                                                }))));
                    }
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register SBE root commands!", t);
                }

                // --- PRIORITY 4: API and Utils ---
                try {
                    dispatcher.register(ClientCommandManager.literal("bombo_highlight_slot")
                            .then(ClientCommandManager.argument("slots", StringArgumentType.string())
                                    .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String slots = StringArgumentType.getString(context, "slots");
                                                String cmd = StringArgumentType.getString(context, "command");
                                                for (String s : slots.replace("\"", "").split(",")) {
                                                    try {
                                                        SlotHighlight.addTargetSlot(Integer.parseInt(s), 0x8000FF00); 
                                                    } catch (NumberFormatException e) {
                                                        SlotHighlight.addTargetName(s, 0x8000FF00);
                                                    }
                                                }
                                                executeTracked(cmd);
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommandManager.literal("lf")
                            .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                    .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                LF.show(StringArgumentType.getString(context, "username"),
                                                        StringArgumentType.getString(context, "query"), false);
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommandManager.literal("lfc")
                            .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                    .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                LF.show(StringArgumentType.getString(context, "username"),
                                                        StringArgumentType.getString(context, "query"), true);
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommandManager.literal("lb")
                            .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        LF.searchLocal(StringArgumentType.getString(context, "query"));
                                        return 1;
                                    })));
                    dispatcher.register(ClientCommandManager.literal("deal")
                            .executes(context -> {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc.player == null || mc.level == null) return 0;
                                Set<String> tabPlayerNames = new HashSet<>();
                                if (mc.getConnection() != null) {
                                    for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                                        String name = info.getProfile().name();
                                        if (name != null) tabPlayerNames.add(cleanName(name));
                                        if (info.getTabListDisplayName() != null) tabPlayerNames.add(cleanName(info.getTabListDisplayName().getString()));
                                    }
                                }
                                List<Player> nearbyPlayers = new ArrayList<>();
                                for (Player p : mc.level.players()) {
                                    if (p == mc.player) continue;
                                    String pName = p.getGameProfile().name();
                                    if (!tabPlayerNames.contains(cleanName(pName))) continue;
                                    double distSq = p.distanceToSqr(mc.player);
                                    if (distSq <= 100.0) nearbyPlayers.add(p);
                                }
                                String targetName = null;
                                if (nearbyPlayers.isEmpty()) {
                                    context.getSource().sendFeedback(Component.literal("§cNo players nearby within 10 blocks!"));
                                    return 0;
                                } else if (nearbyPlayers.size() == 1) {
                                    targetName = nearbyPlayers.get(0).getGameProfile().name();
                                } else {
                                    if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof Player p) {
                                        String pName = p.getGameProfile().name();
                                        if (!tabPlayerNames.contains(cleanName(pName))) {
                                            context.getSource().sendFeedback(Component.literal("§cLooking at an NPC, not a real player!"));
                                            return 0;
                                        }
                                        if (p.distanceToSqr(mc.player) <= 100.0) targetName = pName;
                                        else {
                                            context.getSource().sendFeedback(Component.literal("§cPlayer too far away!"));
                                            return 0;
                                        }
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("§cMultiple players nearby. Look at one!"));
                                        return 0;
                                    }
                                }
                                if (targetName != null) {
                                    final String finalTarget = targetName;
                                    mc.execute(() -> { if (mc.player != null) mc.player.connection.sendCommand("trade " + finalTarget); });
                                }
                                return 1;
                            }));
                    dispatcher.register(ClientCommandManager.literal("bits")
                            .executes(context -> {
                                context.getSource().sendFeedback(Component.literal(PREFIX + "§7[BUILD_VERIFY_B] §eFetching top bits..."));
                                BitsManager.fetchTopBits(5).thenAccept(lines -> { for (String line : lines) context.getSource().sendFeedback(Component.literal(line)); });
                                return 1;
                            })
                            .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1, 100))
                                    .executes(context -> {
                                        int amount = IntegerArgumentType.getInteger(context, "amount");
                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§7[BUILD_VERIFY_B] §eFetching top " + amount + " bits..."));
                                        BitsManager.fetchTopBits(amount).thenAccept(lines -> { for (String line : lines) context.getSource().sendFeedback(Component.literal(line)); });
                                        return 1;
                                    })));
                    dispatcher.register(ClientCommandManager.literal("bclick")
                            .executes(context -> {
                                ClickLogic.listTargets(context.getSource());
                                return 1;
                            }));
                    dispatcher.register(ClientCommandManager.literal("bc")
                            .executes(context -> {
                                ClickLogic.listTargets(context.getSource());
                                return 1;
                            }));
                    dispatcher.register(ClientCommandManager.literal("c")
                            .then(ClientCommandManager.argument("expression", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        SkyblockCalculator.EvaluationResult res = SkyblockCalculator.evaluate(StringArgumentType.getString(context, "expression"));
                                        context.getSource().sendFeedback(res.error != null ? Component.literal(res.error) : res.breakdown);
                                        return 1;
                                    })));
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register util commands!", t);
                }

                Bomboaddons.LOGGER.info("[BomboAddons] Command registration lambda FINISHED!");
            });

            HotbarConfig.load();
            ChatPeek.init();
            BazaarUtils.init();
            LowestBinManager.ensureLoaded();
            ItemHotkeys.init();
        } catch (Throwable t) {
            Bomboaddons.LOGGER.error("[BomboAddons] CRITICAL ERROR in onInitializeClient!", t);
        }
        Bomboaddons.LOGGER.info("[BomboAddons] onInitializeClient finished");
    }



    private void executeTracked(String cmd) {
        if (cmd == null || cmd.isEmpty())
            return;
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
        });
    }

    private static String cleanName(String name) {
        return name.trim().replaceAll("(?i)§[0-9a-fk-or]", "");
    }
}
