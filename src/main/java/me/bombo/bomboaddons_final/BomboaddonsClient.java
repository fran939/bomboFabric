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
    public static com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> clientDispatcher;
    public static String currentArea = "None";


    public void onInitializeClient() {
        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                clientDispatcher = dispatcher;

                // --- PRIORITY 1: /click and /clicks ---
                try {
                    dispatcher.register(ClientCommandManager.literal("clicks")
                            .executes(context -> {
                                ClickLogic.listTargets(context.getSource());
                                return 1;
                            }));
                    dispatcher.register(ClientCommandManager.literal("click")
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
                                    }))
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
                                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§aRemoved click target §e#" + index));
                                                } catch (Exception e) {
                                                    ClickLogic.removeTargetById(id);
                                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§aRemoved click target for §e" + id));
                                                }
                                                return 1;
                                            }))));
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register click commands!", t);
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
                        builder.then(ClientCommandManager.literal("container").executes(context -> {
                            LF.printContainerInfo();
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

                        // --- Garden ---
                        builder.then(ClientCommandManager.literal("ks")
                                .executes(context -> {
                                    GardenMovement.reset();
                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§cGarden Movement Reset! §7(States cleared)"));
                                    return 1;
                                }));
                        builder.then(ClientCommandManager.literal("sc")
                                .executes(context -> {
                                    BomboConfig.Settings s = BomboConfig.get();
                                    s.gardenSugarCane = !s.gardenSugarCane;
                                    BomboConfig.save();
                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§7Sugar Cane Mode: " + (s.gardenSugarCane ? "§aON" : "§cOFF")));
                                    return 1;
                                }));
                        builder.then(ClientCommandManager.literal("sugarcane")
                                .executes(context -> {
                                    BomboConfig.Settings s = BomboConfig.get();
                                    s.gardenSugarCane = !s.gardenSugarCane;
                                    BomboConfig.save();
                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§7Sugar Cane Mode: " + (s.gardenSugarCane ? "§aON" : "§cOFF")));
                                    return 1;
                                }));
                        builder.then(ClientCommandManager.literal("test")
                                .executes(context -> {
                                    String version = FabricLoader.getInstance().getModContainer("bomboaddons").get().getMetadata().getVersion().getFriendlyString();
                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§aCurrent Version: §e" + version));
                                    return 1;
                                }));
                        // --- Playtime ---
                        builder.then(ClientCommandManager.literal("pt")
                                .executes(context -> {
                                    Minecraft.getInstance().execute(() -> {
                                        Minecraft.getInstance().setScreen(new PlaytimeGUI());
                                    });
                                    return 1;
                                }));

                        // --- Update ---
                        builder.then(ClientCommandManager.literal("update")
                                .executes(context -> {
                                    ModUpdater.checkAndUpdate();
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
                                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§aRemoved highlight for: §e" + name));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§cNo highlight found for: §e" + name));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(PREFIX + "§6--- Persistent Entity Highlights ---"));
                                            if (BomboConfig.get().highlights.isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal("  §7None"));
                                            } else {
                                                for (Map.Entry<String, BomboConfig.HighlightInfo> entry : BomboConfig.get().highlights.entrySet()) {
                                                    String targetName = entry.getKey();
                                                    String color = entry.getValue().color;
                                                    ClickEvent click = LF.createClickEventRobust("RUN_COMMAND", "/b highlight remove " + targetName);
                                                    Component removeBtn = Component.literal(" §c[Remove]");
                                                    if (click != null) removeBtn = Component.literal(" §c[Remove]").withStyle(style -> style.withClickEvent(click));
                                                    context.getSource().sendFeedback(Component.literal("  §e" + targetName + " §7- §b" + color).append(removeBtn));
                                                }
                                            }
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            BomboConfig.get().highlights.clear();
                                            BomboConfig.save();
                                            context.getSource().sendFeedback(Component.literal(PREFIX + "§aCleared all highlights."));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("mob", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                                        .suggests((context, builder2) -> {
                                                            for (String c : SlotHighlight.COLORS) builder2.suggest(c);
                                                            return builder2.buildFuture();
                                                        })
                                                        .executes(context -> {
                                                            String mob = StringArgumentType.getString(context, "mob");
                                                            String color = StringArgumentType.getString(context, "color").toUpperCase();
                                                            BomboConfig.get().highlights.put(mob.toLowerCase(), new BomboConfig.HighlightInfo(color, false));
                                                            BomboConfig.save();
                                                            context.getSource().sendFeedback(Component.literal(PREFIX + "§aHighlight added for §e" + mob + " §awith color §b" + color));
                                                            return 1;
                                                        })
                                                        .then(ClientCommandManager.argument("showInvisible", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1))
                                                                .executes(context -> {
                                                                    String mob = StringArgumentType.getString(context, "mob");
                                                                    String color = StringArgumentType.getString(context, "color").toUpperCase();
                                                                    int siInt = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "showInvisible");
                                                                    boolean si = (siInt == 1);
                                                                    BomboConfig.get().highlights.put(mob.toLowerCase(), new BomboConfig.HighlightInfo(color, si));
                                                                    BomboConfig.save();
                                                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§aHighlight added for §e" + mob + " §7(Invis: " + si + ")"));
                                                                    return 1;
                                                                })))))
                                .then(ClientCommandManager.argument("mob", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                                .suggests((context, builder2) -> {
                                                    for (String c : SlotHighlight.COLORS) builder2.suggest(c);
                                                    return builder2.buildFuture();
                                                })
                                                .executes(context -> {
                                                    String mob = StringArgumentType.getString(context, "mob");
                                                    String color = StringArgumentType.getString(context, "color").toUpperCase();
                                                    BomboConfig.get().highlights.put(mob.toLowerCase(), new BomboConfig.HighlightInfo(color, false));
                                                    BomboConfig.save();
                                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§aHighlight added for §e" + mob + " §awith color §b" + color));
                                                    return 1;
                                                })
                                                .then(ClientCommandManager.argument("showInvisible", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1))
                                                        .executes(context -> {
                                                            String mob = StringArgumentType.getString(context, "mob");
                                                            String color = StringArgumentType.getString(context, "color").toUpperCase();
                                                            int siInt = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "showInvisible");
                                                            boolean si = (siInt == 1);
                                                            BomboConfig.get().highlights.put(mob.toLowerCase(), new BomboConfig.HighlightInfo(color, si));
                                                            BomboConfig.save();
                                                            context.getSource().sendFeedback(Component.literal(PREFIX + "§aHighlight added for §e" + mob + " §7(Invis: " + si + ")"));
                                                            return 1;
                                                        }))))
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§7Usage: /b highlight <mob> <color> [showInvis: true/false]"));
                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§7Subcommands: list, remove <name>, clear"));
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
                                    String query = StringArgumentType.getString(context, "query");
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        String name = mc.player.getGameProfile().name();
                                        LF.show(name, query, false);
                                    }
                                    return 1;
                                })));
                    dispatcher.register(ClientCommandManager.literal("tk")
                            .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                    .executes(context -> {
                                        LF.showToolkit(StringArgumentType.getString(context, "username"), 50);
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("limit", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                LF.showToolkit(StringArgumentType.getString(context, "username"), IntegerArgumentType.getInteger(context, "limit"));
                                                return 1;
                                            }))));
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
                                BitsManager.fetchTopBits(5).thenAccept(lines -> { for (String line : lines) context.getSource().sendFeedback(Component.literal(line)); });
                                return 1;
                            })
                            .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1, 100))
                                    .executes(context -> {
                                        int amount = IntegerArgumentType.getInteger(context, "amount");
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

                // --- PRIORITY 5: Inventory Snapshots ---
                try {
                    dispatcher.register(ClientCommandManager.literal("checki")
                                    .then(ClientCommandManager.literal("list")
                                            .executes(context -> {
                                                InventoryManager.listSnapshots(context.getSource());
                                                return 1;
                                            }))
                                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                            .executes(context -> {
                                                InventoryManager.openSnapshot(StringArgumentType.getString(context, "name"), 1);
                                                return 1;
                                            })
                                            .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                                    .executes(context -> {
                                                        InventoryManager.openSnapshot(StringArgumentType.getString(context, "name"),
                                                                IntegerArgumentType.getInteger(context, "index"));
                                                        return 1;
                                                    }))));
                    dispatcher.register(ClientCommandManager.literal("savei")
                            .executes(context -> {
                                InventoryManager.captureCurrentGUI();
                                return 1;
                            }));
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register inventory commands!", t);
                }

            });

            BomboConfig.load();
            PlaytimeTracker.load();
            ChatPeek.init();
            BazaarUtils.init();
            LowestBinManager.ensureLoaded();
            ItemHotkeys.init();
            
            ModUpdater.init();
            registerTickEvents();

            WorldRenderEvents.AFTER_ENTITIES.register(context -> {
                HighlightESP.render(context);
                PestESP.render(context);
            });

            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                LowestBinManager.reload();
                AutoExperiments.reset();
            });

            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                DebugUtils.debug("chat", message.getString().replaceAll("§.", ""));
            });

        } catch (Throwable t) {
            Bomboaddons.LOGGER.error("[BomboAddons] CRITICAL ERROR in onInitializeClient!", t);
        }
    }

    private void registerTickEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.player.tickCount % 20 == 0) {
                currentArea = SkyblockUtils.getLocation();
            }
            PlaytimeTracker.tick();
            
            // Safe execution of Config GUI logic
            try {
                if (openGuiNextTick && client.player != null) {
                    openGuiNextTick = false;
                    client.setScreen(new BomboConfigGUI(client.screen));
                }
            } catch (Throwable t) {
                // Silently ignore or use logger
            }

            // Independent Safe Box for Etherwarp
            try {
                LeftClickEtherwarp.onTick();
            } catch (Throwable t) {
                // Silently ignore
            }

            // Independent Safe Box for Experiments
            try {
                AutoExperiments.onTick();
            } catch (Throwable t) {
                // Silently ignore
            }

            try {
                GardenMovement.onTick(client);
            } catch (Throwable t) {
                // Silently ignore
            }

            try {
                if (client.player != null && client.player.tickCount % 100 == 0) {
                    if (client.level != null) {
                        int count = 0;
                        StringBuilder info = new StringBuilder("Entities near you: ");
                        for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
                            count++;
                            if (e.distanceTo(client.player) < 10) {
                                info.append(e.getName().getString()).append(" (").append(e.getId()).append("), ");
                            }
                        }
                        DebugUtils.debug("entity", "Total: " + count + " | Nearby: " + info.toString());
                    }
                }
            } catch (Throwable t) {}
        });
    }



    private void executeTracked(String cmd) {
        if (cmd == null || cmd.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                String cleanCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                try {
                    DebugUtils.debug("command", "Runned: /" + cleanCmd);
                    // Check if it's a client-side command first using our stored dispatcher
                    if (clientDispatcher != null && clientDispatcher.getRoot().getChild(cleanCmd.split(" ")[0]) != null) {
                        clientDispatcher.execute(cleanCmd, (FabricClientCommandSource) mc.player);
                    } else if (mc.player.connection != null) {
                        // Fallback to server
                        mc.player.connection.sendCommand(cleanCmd);
                    } else {
                        mc.player.displayClientMessage(Component.literal("§c[Bombo] Failed to execute: /" + cleanCmd), false);
                    }
                } catch (Exception e) {
                    if (mc.player.connection != null) mc.player.connection.sendCommand(cleanCmd);
                }
            }
        });
    }



    private static String cleanName(String name) {
        return name.trim().replaceAll("(?i)§[0-9a-fk-or]", "");
    }
}
