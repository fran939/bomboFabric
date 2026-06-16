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
import java.nio.file.Path;
import net.minecraft.util.Util;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

@Environment(EnvType.CLIENT)
public class BomboaddonsClient implements ClientModInitializer {
    public static class PendingCommand {
        public final String command;
        public final long triggerTime;

        public PendingCommand(String command, long triggerTime) {
            this.command = command;
            this.triggerTime = triggerTime;
        }
    }
    public static final java.util.List<PendingCommand> pendingCommands = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final String PREFIX = "§8[§bBomboAddons§8]§r ";
    private static boolean openGuiNextTick = false;
    private static boolean openHudMoveNextTick = false;
    public static com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> clientDispatcher;
    public static String currentArea = "None";
    public static String currentSubArea = "None";
    private static int menuTickCount = 0;
    public static net.minecraft.client.multiplayer.ServerData lastServerData = null;
    public static net.minecraft.client.gui.components.Button activeReconnectBtn = null;
    public static net.minecraft.client.gui.screens.Screen activeParent = null;
    public static int autoReconnectTicks = -1;
    public static boolean tempDisableReconnect = false;
    public static String locrawServer = "";
    public static String locrawGametype = "";
    public static String locrawMode = "";
    public static String locrawMap = "";
    public static long lastLocrawTime = 0;
    public static int locrawDelayTicks = -1;
    public static int expectingLocrawCount = 0;

    public void onInitializeClient() {
        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                clientDispatcher = dispatcher;
                
                // --- Hoppity Egg Finder commands ---
                try {
                    dispatcher.register(ClientCommandManager.literal("skyblocker")
                            .then(ClientCommandManager.literal("eggFinder")
                                    .then(ClientCommandManager.literal("shareLocation")
                                            .then(ClientCommandManager.argument("eggType", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String typeStr = StringArgumentType.getString(context, "eggType");
                                                        me.bombo.bomboaddons_final.eggfinder.EggFinder.EggType type = me.bombo.bomboaddons_final.eggfinder.EggFinder.EggType.getTypeByName(typeStr);
                                                        if (type == null) {
                                                            context.getSource().sendError(Component.literal("§8[§bBomboAddons§8] §cInvalid egg type: " + typeStr));
                                                            return 1;
                                                        }
                                                        List<me.bombo.bomboaddons_final.eggfinder.EggFinder.EggWaypoint> wps = me.bombo.bomboaddons_final.eggfinder.EggFinder.getActiveWaypoints();
                                                        me.bombo.bomboaddons_final.eggfinder.EggFinder.EggWaypoint targetWp = null;
                                                        for (var wp : wps) {
                                                            if (wp.type == type) {
                                                                targetWp = wp;
                                                                break;
                                                            }
                                                        }
                                                        if (targetWp == null) {
                                                            context.getSource().sendError(Component.literal("§8[§bBomboAddons§8] §cUnable to share egg location - not found."));
                                                            return 1;
                                                        }
                                                        String chatMsg = "[Skyblocker] Chocolate " + type.name + " Egg found at " + targetWp.pos.getX() + ", " + targetWp.pos.getY() + ", " + targetWp.pos.getZ();
                                                        Minecraft.getInstance().player.connection.sendChat(chatMsg);
                                                        return 1;
                                                    })))
                                    .then(ClientCommandManager.literal("sharelocation")
                                            .then(ClientCommandManager.argument("eggType", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String typeStr = StringArgumentType.getString(context, "eggType");
                                                        me.bombo.bomboaddons_final.eggfinder.EggFinder.EggType type = me.bombo.bomboaddons_final.eggfinder.EggFinder.EggType.getTypeByName(typeStr);
                                                        if (type == null) {
                                                            context.getSource().sendError(Component.literal("§8[§bBomboAddons§8] §cInvalid egg type: " + typeStr));
                                                            return 1;
                                                        }
                                                        List<me.bombo.bomboaddons_final.eggfinder.EggFinder.EggWaypoint> wps = me.bombo.bomboaddons_final.eggfinder.EggFinder.getActiveWaypoints();
                                                        me.bombo.bomboaddons_final.eggfinder.EggFinder.EggWaypoint targetWp = null;
                                                        for (var wp : wps) {
                                                            if (wp.type == type) {
                                                                targetWp = wp;
                                                                break;
                                                            }
                                                        }
                                                        if (targetWp == null) {
                                                            context.getSource().sendError(Component.literal("§8[§bBomboAddons§8] §cUnable to share egg location - not found."));
                                                            return 1;
                                                        }
                                                        String chatMsg = "[Skyblocker] Chocolate " + type.name + " Egg found at " + targetWp.pos.getX() + ", " + targetWp.pos.getY() + ", " + targetWp.pos.getZ();
                                                        Minecraft.getInstance().player.connection.sendChat(chatMsg);
                                                        return 1;
                                                    })))
                                    .then(ClientCommandManager.literal("status")
                                            .executes(context -> {
                                                boolean connected = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.isConnected();
                                                boolean connecting = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.isConnecting();
                                                String sub = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.getActiveSubscription();
                                                String statusColor = connected ? "§aConnected" : (connecting ? "§eConnecting..." : "§cDisconnected");
                                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: " + statusColor));
                                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Active Subscription Area: §e" + (sub != null ? sub : "None")));
                                                return 1;
                                            }))
                                    .then(ClientCommandManager.literal("reconnect")
                                            .executes(context -> {
                                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §eRe-authenticating and reconnecting to Egg Finder WebSocket..."));
                                                me.bombo.bomboaddons_final.eggfinder.EggAuth.forceUpdateToken();
                                                me.bombo.bomboaddons_final.eggfinder.EggWebSocket.forceReconnect();
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("eggfinder")
                                    .then(ClientCommandManager.literal("shareLocation")
                                            .then(ClientCommandManager.argument("eggType", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String typeStr = StringArgumentType.getString(context, "eggType");
                                                        me.bombo.bomboaddons_final.eggfinder.EggFinder.EggType type = me.bombo.bomboaddons_final.eggfinder.EggFinder.EggType.getTypeByName(typeStr);
                                                        if (type == null) {
                                                            context.getSource().sendError(Component.literal("§8[§bBomboAddons§8] §cInvalid egg type: " + typeStr));
                                                            return 1;
                                                        }
                                                        List<me.bombo.bomboaddons_final.eggfinder.EggFinder.EggWaypoint> wps = me.bombo.bomboaddons_final.eggfinder.EggFinder.getActiveWaypoints();
                                                        me.bombo.bomboaddons_final.eggfinder.EggFinder.EggWaypoint targetWp = null;
                                                        for (var wp : wps) {
                                                            if (wp.type == type) {
                                                                targetWp = wp;
                                                                break;
                                                            }
                                                        }
                                                        if (targetWp == null) {
                                                            context.getSource().sendError(Component.literal("§8[§bBomboAddons§8] §cUnable to share egg location - not found."));
                                                            return 1;
                                                        }
                                                        String chatMsg = "[Skyblocker] Chocolate " + type.name + " Egg found at " + targetWp.pos.getX() + ", " + targetWp.pos.getY() + ", " + targetWp.pos.getZ();
                                                        Minecraft.getInstance().player.connection.sendChat(chatMsg);
                                                        return 1;
                                                    })))
                                    .then(ClientCommandManager.literal("sharelocation")
                                            .then(ClientCommandManager.argument("eggType", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String typeStr = StringArgumentType.getString(context, "eggType");
                                                        me.bombo.bomboaddons_final.eggfinder.EggFinder.EggType type = me.bombo.bomboaddons_final.eggfinder.EggFinder.EggType.getTypeByName(typeStr);
                                                        if (type == null) {
                                                            context.getSource().sendError(Component.literal("§8[§bBomboAddons§8] §cInvalid egg type: " + typeStr));
                                                            return 1;
                                                        }
                                                        List<me.bombo.bomboaddons_final.eggfinder.EggFinder.EggWaypoint> wps = me.bombo.bomboaddons_final.eggfinder.EggFinder.getActiveWaypoints();
                                                        me.bombo.bomboaddons_final.eggfinder.EggFinder.EggWaypoint targetWp = null;
                                                        for (var wp : wps) {
                                                            if (wp.type == type) {
                                                                targetWp = wp;
                                                                break;
                                                            }
                                                        }
                                                        if (targetWp == null) {
                                                            context.getSource().sendError(Component.literal("§8[§bBomboAddons§8] §cUnable to share egg location - not found."));
                                                            return 1;
                                                        }
                                                        String chatMsg = "[Skyblocker] Chocolate " + type.name + " Egg found at " + targetWp.pos.getX() + ", " + targetWp.pos.getY() + ", " + targetWp.pos.getZ();
                                                        Minecraft.getInstance().player.connection.sendChat(chatMsg);
                                                        return 1;
                                                    })))
                                    .then(ClientCommandManager.literal("status")
                                            .executes(context -> {
                                                boolean connected = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.isConnected();
                                                boolean connecting = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.isConnecting();
                                                String sub = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.getActiveSubscription();
                                                String statusColor = connected ? "§aConnected" : (connecting ? "§eConnecting..." : "§cDisconnected");
                                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: " + statusColor));
                                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Active Subscription Area: §e" + (sub != null ? sub : "None")));
                                                return 1;
                                            }))
                                    .then(ClientCommandManager.literal("reconnect")
                                            .executes(context -> {
                                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §eRe-authenticating and reconnecting to Egg Finder WebSocket..."));
                                                me.bombo.bomboaddons_final.eggfinder.EggAuth.forceUpdateToken();
                                                me.bombo.bomboaddons_final.eggfinder.EggWebSocket.forceReconnect();
                                                return 1;
                                            }))));

                    // Register direct /eggfinder command
                    dispatcher.register(ClientCommandManager.literal("eggfinder")
                            .then(ClientCommandManager.literal("status")
                                    .executes(context -> {
                                        boolean connected = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.isConnected();
                                        boolean connecting = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.isConnecting();
                                        String sub = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.getActiveSubscription();
                                        String statusColor = connected ? "§aConnected" : (connecting ? "§eConnecting..." : "§cDisconnected");
                                        context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: " + statusColor));
                                        context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Active Subscription Area: §e" + (sub != null ? sub : "None")));
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("reconnect")
                                    .executes(context -> {
                                        context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §eRe-authenticating and reconnecting to Egg Finder WebSocket..."));
                                        me.bombo.bomboaddons_final.eggfinder.EggAuth.forceUpdateToken();
                                        me.bombo.bomboaddons_final.eggfinder.EggWebSocket.forceReconnect();
                                        return 1;
                                    })));

                    // Register direct /eggFinder command
                    dispatcher.register(ClientCommandManager.literal("eggFinder")
                            .then(ClientCommandManager.literal("status")
                                    .executes(context -> {
                                        boolean connected = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.isConnected();
                                        boolean connecting = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.isConnecting();
                                        String sub = me.bombo.bomboaddons_final.eggfinder.EggWebSocket.getActiveSubscription();
                                        String statusColor = connected ? "§aConnected" : (connecting ? "§eConnecting..." : "§cDisconnected");
                                        context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: " + statusColor));
                                        context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Active Subscription Area: §e" + (sub != null ? sub : "None")));
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("reconnect")
                                    .executes(context -> {
                                        context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §eRe-authenticating and reconnecting to Egg Finder WebSocket..."));
                                        me.bombo.bomboaddons_final.eggfinder.EggAuth.forceUpdateToken();
                                        me.bombo.bomboaddons_final.eggfinder.EggWebSocket.forceReconnect();
                                        return 1;
                                    })));
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] Failed to register skyblocker eggFinder commands!", t);
                }

                registerAllAliases();

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
                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§7Click Debug: "
                                                + (ClickLogic.isDebugMode() ? "§aON" : "§cOFF")));
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("add")
                                    .then(ClientCommandManager.argument("item", StringArgumentType.string())
                                            .then(ClientCommandManager.argument("gui", StringArgumentType.string())
                                                    .then(ClientCommandManager
                                                            .argument("key", StringArgumentType.string())
                                                            .then(ClientCommandManager
                                                                    .argument("type", StringArgumentType.string())
                                                                    .executes(context -> {
                                                                        String item = StringArgumentType
                                                                                .getString(context, "item");
                                                                        String gui = StringArgumentType
                                                                                .getString(context, "gui");
                                                                        String key = StringArgumentType
                                                                                .getString(context, "key");
                                                                        String type = StringArgumentType
                                                                                .getString(context, "type");
                                                                        ClickLogic.setTarget(item, gui, key, type,
                                                                                false);
                                                                        context.getSource()
                                                                                .sendFeedback(Component.literal(PREFIX
                                                                                        + "§aAdded click target for §e"
                                                                                        + item));
                                                                        return 1;
                                                                    })
                                                                    .then(ClientCommandManager.argument("auto",
                                                                            com.mojang.brigadier.arguments.BoolArgumentType
                                                                                    .bool())
                                                                            .executes(context -> {
                                                                                String item = StringArgumentType
                                                                                        .getString(context, "item");
                                                                                String gui = StringArgumentType
                                                                                        .getString(context, "gui");
                                                                                String key = StringArgumentType
                                                                                        .getString(context, "key");
                                                                                String type = StringArgumentType
                                                                                        .getString(context, "type");
                                                                                boolean auto = com.mojang.brigadier.arguments.BoolArgumentType
                                                                                        .getBool(context, "auto");
                                                                                ClickLogic.setTarget(item, gui, key,
                                                                                        type, auto);
                                                                                context.getSource().sendFeedback(
                                                                                        Component.literal(PREFIX
                                                                                                + "§aAdded "
                                                                                                + (auto ? "auto " : "")
                                                                                                + "click target for §e"
                                                                                                + item));
                                                                                return 1;
                                                                            })))))))
                            .then(ClientCommandManager.literal("remove")
                                    .then(ClientCommandManager.argument("id", StringArgumentType.string())
                                            .executes(context -> {
                                                String id = StringArgumentType.getString(context, "id");
                                                try {
                                                    int index = Integer.parseInt(id);
                                                    ClickLogic.removeTarget(index);
                                                    context.getSource().sendFeedback(Component
                                                            .literal(PREFIX + "§aRemoved click target §e#" + index));
                                                } catch (Exception e) {
                                                    ClickLogic.removeTargetById(id);
                                                    context.getSource().sendFeedback(Component
                                                            .literal(PREFIX + "§aRemoved click target for §e" + id));
                                                }
                                                return 1;
                                            }))));
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register click commands!", t);
                }

                // --- PRIORITY 1: Core Search ---
                try {
                    dispatcher.register(ClientCommandManager.literal("lf")
                            .executes(context -> {
                                System.out.println("[Bombo] Executing /lf (help)");
                                context.getSource()
                                        .sendFeedback(Component.literal(PREFIX + "§7Usage: /lf <username> [query]"));
                                return 1;
                            })
                            .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                    .executes(context -> {
                                        String user = StringArgumentType.getString(context, "username");
                                        System.out.println("[Bombo] Executing /lf for user: " + user);
                                        LF.show(user, "", false);
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String user = StringArgumentType.getString(context, "username");
                                                String query = StringArgumentType.getString(context, "query");
                                                System.out.println("[Bombo] Executing /lf for user: " + user
                                                        + " with query: " + query);
                                                LF.show(user, query, false);
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommandManager.literal("lfc")
                            .executes(context -> {
                                System.out.println("[Bombo] Executing /lfc (help)");
                                context.getSource()
                                        .sendFeedback(Component.literal(PREFIX + "§7Usage: /lfc <username> [query]"));
                                return 1;
                            })
                            .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                    .executes(context -> {
                                        String user = StringArgumentType.getString(context, "username");
                                        System.out.println("[Bombo] Executing /lfc for user: " + user);
                                        LF.show(user, "", true);
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String user = StringArgumentType.getString(context, "username");
                                                String query = StringArgumentType.getString(context, "query");
                                                System.out.println("[Bombo] Executing /lfc for user: " + user
                                                        + " with query: " + query);
                                                LF.show(user, query, true);
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommandManager.literal("lb")
                            .executes(context -> {
                                System.out.println("[Bombo] Executing /lb");
                                Minecraft mc = Minecraft.getInstance();
                                if (mc.player != null) {
                                    String name = mc.player.getName().getString();
                                    System.out.println("[Bombo] /lb for self: " + name);
                                    LF.show(name, "", false);
                                }
                                return 1;
                            })
                            .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String query = StringArgumentType.getString(context, "query");
                                        Minecraft mc = Minecraft.getInstance();
                                        if (mc.player != null) {
                                            String name = mc.player.getName().getString();
                                            System.out
                                                    .println("[Bombo] /lb for self: " + name + " with query: " + query);
                                            LF.show(name, query, false);
                                        }
                                        return 1;
                                    })));
                } catch (Throwable t) {
                    System.err.println("[Bombo] FAILED to register core search commands!");
                    t.printStackTrace();
                }

                // --- PRIORITY 2: /b, /ba, /bombo and subcommands ---
                try {
                    LiteralArgumentBuilder<FabricClientCommandSource> bBuilder = ClientCommandManager.literal("b");
                    LiteralArgumentBuilder<FabricClientCommandSource> baBuilder = ClientCommandManager
                            .literal("bomboaddons");
                    LiteralArgumentBuilder<FabricClientCommandSource> bomboBuilder = ClientCommandManager
                            .literal("bombo");

                    java.util.function.Consumer<LiteralArgumentBuilder<FabricClientCommandSource>> setupCommands = builder -> {
                        builder.executes(context -> {
                            openGuiNextTick = true;
                            return 1;
                        });

                        builder.then(ClientCommandManager.literal("help").executes(context -> {
                            context.getSource().sendFeedback(
                                    Component.literal("§8----------------- §b[BomboAddons Help] §8-----------------"));
                            context.getSource().sendFeedback(Component
                                    .literal("§7Hover over any command to see what it does! Click to suggest it.\n"));

                            context.getSource().sendFeedback(createHelpLine("/b", "/b", "Opens the main config GUI.")
                                    .append(Component.literal(" §7- Opens the main config GUI")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b help", "/b help", "Shows this help menu.")
                                            .append(Component.literal(" §7- Shows this help menu")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b prof", "/b prof", "Opens config GUI directly to Profile Binds.")
                                            .append(Component.literal(" §7- Opens Profile Binds config")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b gui", "/b gui", "Opens the HUD Editor to reposition overlays.")
                                            .append(Component.literal(" §7- Opens the HUD Editor")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b api", "/b api", "Reloads lowest BIN prices and checks status.")
                                            .append(Component.literal(" §7- Reloads and checks APIs")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b ks", "/b ks", "Resets active Garden Movement states.")
                                            .append(Component.literal(" §7- Resets Garden Movement states")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b sugarcane", "/b sugarcane",
                                            "Toggles Sugar Cane mode for lane warnings.")
                                            .append(Component.literal(" §7- Toggles Sugar Cane mode")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b highlight", "/b highlight",
                                            "Configures persistent entity highlights.")
                                            .append(Component.literal(" §7- Persistent Highlights config")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b anvil", "/b anvil", "Configures persistent auto-combine goals.")
                                            .append(Component.literal(" §7- Anvil Auto-Combine config")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b pt", "/b pt", "Opens Playtime statistics GUI.")
                                            .append(Component.literal(" §7- Opens Playtime GUI")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b update", "/b update", "Manually checks for mod updates.")
                                            .append(Component.literal(" §7- Checks for mod updates")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b hide", "/b hide", "Toggles visibility of cheats in the GUI.")
                                            .append(Component.literal(" §7- Toggles GUI cheat visibility")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b area", "/b area", "Shows the current SkyBlock area.")
                                            .append(Component.literal(" §7- Shows current Area")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b subarea", "/b subarea", "Shows the current SkyBlock subarea.")
                                            .append(Component.literal(" §7- Shows current Subarea")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b container", "/b container",
                                            "Logs active virtual container structures.")
                                            .append(Component.literal(" §7- Logs container info")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b sb", "/b sb", "Logs current scoreboard lines to chat.")
                                            .append(Component.literal(" §7- Logs scoreboard lines")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b tab", "/b tab", "Logs current tab list lines to chat.")
                                            .append(Component.literal(" §7- Logs tab list lines")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b kick", "/b kick", "Safely disconnects you from the server.")
                                            .append(Component.literal(" §7- Safely disconnects from server")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b resetdice", "/b resetdice",
                                            "Resets High Class Archfiend Dice stats.")
                                            .append(Component.literal(" §7- Resets Dice statistics")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b lf <name>", "/b lf ", "Searches a player's inventory.")
                                            .append(Component.literal(" §7- Searches player's inventory")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b lfc <name>", "/b lfc ",
                                            "Searches a player's inventory with NBT components.")
                                            .append(Component.literal(" §7- Searches inventory with NBT components")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b lb", "/b lb", "Searches your own inventory.")
                                            .append(Component.literal(" §7- Searches your own inventory")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b view <name> <p>", "/b view ", "Opens virtual container paths.")
                                            .append(Component.literal(" §7- Opens virtual container paths")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b msg <message>", "/b msg ",
                                            "Simulates a chat message with §-color code support.")
                                            .append(Component.literal(" §7- Simulates a chat message")));
                            context.getSource()
                                    .sendFeedback(createHelpLine("/b get <alias>", "/b get ",
                                            "Checks inventory and runs /gfs to refill item up to target.")
                                            .append(Component.literal(" §7- Refills items from sack")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b rank [name]", "/b rank", "Fetches and displays the Hypixel rank of a player.")
                                            .append(Component.literal(" §7- Fetches and displays player rank")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b chat", "/b chat", "Toggles the global IRC mod chat.")
                                            .append(Component.literal(" §7- Toggles IRC chat")));

                            context.getSource().sendFeedback(
                                    Component.literal("§8---------------------------------------------------------"));
                            return 1;
                        }));

                        builder.then(ClientCommandManager.literal("prof").executes(context -> {
                            BomboConfigGUI.selectedCategory = 5;
                            openGuiNextTick = true;
                            return 1;
                        }));

                        // --- Diagnostics ---
                        builder.then(ClientCommandManager.literal("kick").executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.getConnection() != null) {
                                mc.getConnection().getConnection().disconnect(Component.literal("Kicked via /b kick"));
                            } else {
                                context.getSource().sendFeedback(
                                        Component.literal(PREFIX + "§cNot currently connected to any server!"));
                            }
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("area").executes(context -> {
                            String loc = SkyblockUtils.getLocation();
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§7Current Area: §a" + loc));
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("subarea").executes(context -> {
                            String sub = SkyblockUtils.getSubArea();
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§7Current Subarea: §d" + sub));
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("container").executes(context -> {
                            LF.printContainerInfo();
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("sb").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§6Scoreboard Lines:"));
                            List<String> lines = SkyblockUtils.getSidebarLines(
                                    Minecraft.getInstance().level.getScoreboard(),
                                    Minecraft.getInstance().level.getScoreboard()
                                            .getDisplayObjective(DisplaySlot.SIDEBAR));
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
                            context.getSource()
                                    .sendFeedback(Component.literal(PREFIX + "§eChecking and Reloading APIs..."));
                            LowestBinManager.reload();
                            context.getSource().sendFeedback(Component.literal(LowestBinManager.getStatus()));
                            return 1;
                        }));

                        builder.then(ClientCommandManager.literal("wd")
                                .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int slot = IntegerArgumentType.getInteger(context, "slot");
                                            me.bombo.bomboaddons_final.WardrobeHelper.equip(slot);
                                            return 1;
                                        })));

                        builder.then(ClientCommandManager.literal("hide").executes(context -> {
                            BomboConfig.Settings s = BomboConfig.get();
                            s.hideCheats = !s.hideCheats;
                            if (s.hideCheats) {
                                if (BomboConfigGUI.selectedCategory == 2 || BomboConfigGUI.selectedCategory == 9) {
                                    BomboConfigGUI.selectedCategory = 0;
                                }
                                context.getSource().sendFeedback(
                                        Component.literal(PREFIX + "§aCheats are now §chidden §afrom the GUI!"));
                            } else {
                                context.getSource().sendFeedback(
                                        Component.literal(PREFIX + "§aCheats are now §avisible §ain the GUI!"));
                            }
                            BomboConfig.save();
                            return 1;
                        }));

                        // --- SBE Commands (Translation to /b) ---
                        String[] sbeSubs = { "nw", "cata", "skills", "slayer", "trophyfish", "crimson" };
                        for (String s : sbeSubs) {
                            builder.then(ClientCommandManager.literal(s)
                                    .executes(context -> {
                                        SBECommands.handleCommand(s,
                                                Minecraft.getInstance().player.getName().getString(), null);
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                            .executes(context -> {
                                                SBECommands.handleCommand(s,
                                                        StringArgumentType.getString(context, "name"),
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

                        builder.then(ClientCommandManager.literal("rank")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        String name = mc.player.getName().getString();
                                        showRankCommand(context.getSource(), name);
                                    }
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            showRankCommand(context.getSource(), name);
                                            return 1;
                                        })));

                        builder.then(ClientCommandManager.literal("chat")
                                .executes(context -> {
                                    try {
                                        BomboConfig.get().ircChatEnabled = !BomboConfig.get().ircChatEnabled;
                                        BomboConfig.save();
                                        IRCClient.onEnabledToggled();
                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§7IRC Chat: " 
                                                + (BomboConfig.get().ircChatEnabled ? "§aON" : "§cOFF")));
                                    } catch (Throwable t) {
                                        Bomboaddons.LOGGER.error("[BomboAddons] Error toggling IRC Chat via command", t);
                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§cError toggling IRC Chat: " + t.getMessage()));
                                    }
                                    return 1;
                                }));

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
                                    GardenMovement.onWarpTriggered();
                                    context.getSource().sendFeedback(
                                            Component.literal(PREFIX + "§cGarden Movement Reset! §7(States cleared)"));
                                    return 1;
                                }));
                        builder.then(ClientCommandManager.literal("sc")
                                .executes(context -> {
                                    BomboConfig.Settings s = BomboConfig.get();
                                    s.gardenSugarCane = !s.gardenSugarCane;
                                    BomboConfig.save();
                                    context.getSource().sendFeedback(Component.literal(
                                            PREFIX + "§7Sugar Cane Mode: " + (s.gardenSugarCane ? "§aON" : "§cOFF")));
                                    return 1;
                                }));
                        builder.then(ClientCommandManager.literal("sugarcane")
                                .executes(context -> {
                                    BomboConfig.Settings s = BomboConfig.get();
                                    s.gardenSugarCane = !s.gardenSugarCane;
                                    BomboConfig.save();
                                    context.getSource().sendFeedback(Component.literal(
                                            PREFIX + "§7Sugar Cane Mode: " + (s.gardenSugarCane ? "§aON" : "§cOFF")));
                                    return 1;
                                }));
                        builder.then(ClientCommandManager.literal("test")
                                .executes(context -> {
                                    String version = FabricLoader.getInstance().getModContainer("bomboaddons").get()
                                            .getMetadata().getVersion().getFriendlyString();
                                    context.getSource().sendFeedback(
                                            Component.literal(PREFIX + "§aCurrent Version: §e" + version));
                                    return 1;
                                }));

                        builder.then(ClientCommandManager.literal("wp")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component
                                            .literal(PREFIX + "§cUsage: /b wp <x> <y> <z> <name> OR /b wp clear"));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            GardenWaypoints.clear();
                                            context.getSource().sendFeedback(
                                                    Component.literal(PREFIX + "§aAll waypoints cleared!"));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.argument("x", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("y", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("z", StringArgumentType.word())
                                                        .then(ClientCommandManager
                                                                .argument("name", StringArgumentType.greedyString())
                                                                .executes(context -> {
                                                                    try {
                                                                        String xStr = StringArgumentType
                                                                                .getString(context, "x");
                                                                        String yStr = StringArgumentType
                                                                                .getString(context, "y");
                                                                        String zStr = StringArgumentType
                                                                                .getString(context, "z");
                                                                        String name = StringArgumentType
                                                                                .getString(context, "name");

                                                                        Minecraft mc = Minecraft.getInstance();
                                                                        if (mc.player == null) {
                                                                            context.getSource().sendFeedback(
                                                                                    Component.literal(PREFIX
                                                                                            + "§cPlayer not found!"));
                                                                            return 0;
                                                                        }
                                                                        Vec3 playerPos = mc.player.position();
                                                                        double x, y, z;
                                                                        if (xStr.startsWith("~")) {
                                                                            x = playerPos.x
                                                                                    + (xStr.length() > 1
                                                                                            ? Double.parseDouble(
                                                                                                    xStr.substring(1))
                                                                                            : 0);
                                                                        } else {
                                                                            x = Double.parseDouble(xStr);
                                                                        }
                                                                        if (yStr.startsWith("~")) {
                                                                            y = playerPos.y
                                                                                    + (yStr.length() > 1
                                                                                            ? Double.parseDouble(
                                                                                                    yStr.substring(1))
                                                                                            : 0);
                                                                        } else {
                                                                            y = Double.parseDouble(yStr);
                                                                        }
                                                                        if (zStr.startsWith("~")) {
                                                                            z = playerPos.z
                                                                                    + (zStr.length() > 1
                                                                                            ? Double.parseDouble(
                                                                                                    zStr.substring(1))
                                                                                            : 0);
                                                                        } else {
                                                                            z = Double.parseDouble(zStr);
                                                                        }

                                                                        Vec3 targetPos = new Vec3(x, y, z);
                                                                        GardenWaypoints.addWaypoint(targetPos, name);
                                                                        context.getSource()
                                                                                .sendFeedback(Component.literal(PREFIX
                                                                                        + "§aAdded waypoint '§e" + name
                                                                                        + "§a' at "
                                                                                        + String.format(
                                                                                                "%.1f, %.1f, %.1f", x,
                                                                                                y, z)));
                                                                        return 1;
                                                                    } catch (NumberFormatException e) {
                                                                        context.getSource()
                                                                                .sendFeedback(Component.literal(PREFIX
                                                                                        + "§cInvalid coordinates format!"));
                                                                        return 0;
                                                                    }
                                                                }))))));

                        builder.then(ClientCommandManager.literal("cycle")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(PREFIX
                                            + "§cUsage: /b cycle add <name> <commands...>, /b cycle apply <name>, /b cycle remove <name>, or /b cycle list"));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .then(ClientCommandManager
                                                        .argument("commands", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            String name = StringArgumentType.getString(context, "name")
                                                                    .toLowerCase();
                                                            String commandsStr = StringArgumentType.getString(context,
                                                                    "commands");
                                                            List<String> cmds = splitCommands(commandsStr);
                                                            if (cmds.isEmpty()) {
                                                                context.getSource().sendFeedback(Component
                                                                        .literal(PREFIX + "§cNo commands specified!"));
                                                                return 0;
                                                            }
                                                            BomboConfig.get().commandCycles.put(name, cmds);
                                                            BomboConfig.get().commandCycleIndices.put(name, 0);
                                                            BomboConfig.save();
                                                            context.getSource()
                                                                    .sendFeedback(Component.literal(PREFIX
                                                                            + "§aAdded cycle §e" + name + " §awith §6"
                                                                            + cmds.size() + "§a commands: §7"
                                                                            + String.join(", ", cmds)));
                                                            return 1;
                                                        }))))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name")
                                                            .toLowerCase();
                                                    if (BomboConfig.get().commandCycles.remove(name) != null) {
                                                        BomboConfig.get().commandCycleIndices.remove(name);
                                                        BomboConfig.save();
                                                        context.getSource().sendFeedback(Component
                                                                .literal(PREFIX + "§aRemoved cycle §e" + name));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§cCycle §e" + name + " §cdoes not exist!"));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> {
                                            context.getSource().sendFeedback(
                                                    Component.literal(PREFIX + "§6--- Command Cycles ---"));
                                            if (BomboConfig.get().commandCycles.isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal("  §7None"));
                                            } else {
                                                for (Map.Entry<String, List<String>> entry : BomboConfig
                                                        .get().commandCycles.entrySet()) {
                                                    String name = entry.getKey();
                                                    List<String> cmds = entry.getValue();
                                                    int index = BomboConfig.get().commandCycleIndices.getOrDefault(name,
                                                            0);
                                                    context.getSource().sendFeedback(Component.literal("  §e" + name
                                                            + " §7(Next index: §b" + index + "§7/§b" + cmds.size()
                                                            + "§7) -> §7" + String.join(", ", cmds)));
                                                }
                                            }
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("apply")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name")
                                                            .toLowerCase();
                                                    List<String> cmds = BomboConfig.get().commandCycles.get(name);
                                                    if (cmds == null || cmds.isEmpty()) {
                                                        context.getSource()
                                                                .sendFeedback(Component.literal(PREFIX + "§cCycle §e"
                                                                        + name + " §cdoes not exist or is empty!"));
                                                        return 0;
                                                    }
                                                    int index = BomboConfig.get().commandCycleIndices.getOrDefault(name,
                                                            0);
                                                    if (index >= cmds.size() || index < 0) {
                                                        index = 0;
                                                    }
                                                    String cmd = cmds.get(index);
                                                    int nextIndex = (index + 1) % cmds.size();
                                                    BomboConfig.get().commandCycleIndices.put(name, nextIndex);
                                                    BomboConfig.save();

                                                    context.getSource()
                                                            .sendFeedback(Component.literal(PREFIX + "§aRunning: §b"
                                                                    + cmd + " §7(Next: " + cmds.get(nextIndex) + ")"));
                                                    executeTracked(cmd);
                                                    return 1;
                                                }))));

                        // --- Playtime ---
                        builder.then(ClientCommandManager.literal("pt")
                                .executes(context -> {
                                    Minecraft.getInstance().execute(() -> {
                                        Minecraft.getInstance().setScreen(new PlaytimeGUI(null));
                                    });
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("sync")
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    PREFIX + "§aManually syncing your playtime data to the cloud..."));
                                            PlaytimeTracker.sendPlaytimeDataToCloud();
                                            return 1;
                                        }))
                                .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                        .executes(context -> {
                                            String username = StringArgumentType.getString(context, "username");
                                            context.getSource().sendFeedback(Component.literal(
                                                    PREFIX + "§aFetching playtime data for §e" + username + "§a..."));
                                            new Thread(() -> {
                                                try {
                                                    java.net.URL url = new java.net.URI(
                                                            "https://bomboapi.frandl938.workers.dev/playtime/"
                                                                    + username)
                                                            .toURL();
                                                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url
                                                            .openConnection();
                                                    conn.setRequestMethod("GET");
                                                    int responseCode = conn.getResponseCode();
                                                    if (responseCode == 200) {
                                                        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                                                                conn.getInputStream())) {
                                                            com.google.gson.JsonObject data = com.google.gson.JsonParser
                                                                    .parseReader(reader).getAsJsonObject();
                                                            Minecraft.getInstance().execute(() -> {
                                                                Minecraft.getInstance()
                                                                        .setScreen(new PlaytimeGUI(data));
                                                            });
                                                        }
                                                    } else {
                                                        context.getSource()
                                                                .sendFeedback(Component.literal(
                                                                        PREFIX + "§cNo playtime data found for §e"
                                                                                + username + "§c."));
                                                    }
                                                } catch (Exception e) {
                                                    context.getSource().sendFeedback(Component.literal(PREFIX
                                                            + "§cError fetching playtime data: " + e.getMessage()));
                                                }
                                            }).start();
                                            return 1;
                                        })));

                        // --- Update ---
                        builder.then(ClientCommandManager.literal("update")
                                .executes(context -> {
                                    ModUpdater.checkAndUpdate(false);
                                    return 1;
                                }));

                        // --- Highlight Command ---
                        builder.then(ClientCommandManager.literal("highlight")
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name")
                                                            .toLowerCase();
                                                    if (BomboConfig.get().highlights.remove(name) != null) {
                                                        BomboConfig.save();
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§aRemoved highlight for: §e" + name));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§cNo highlight found for: §e" + name));
                                                    }
                                                    return 1;
                                                })))

                                .then(ClientCommandManager.literal("toggle")
                                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name")
                                                            .toLowerCase();
                                                    BomboConfig.HighlightInfo info = BomboConfig.get().highlights.get(name);
                                                    if (info != null) {
                                                        info.enabled = !info.enabled;
                                                        BomboConfig.save();
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§aToggled highlight for: §e" + name + " §a(" + (info.enabled ? "Enabled" : "Disabled") + ")"));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§cNo highlight found for: §e" + name));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component
                                                    .literal(PREFIX + "§6--- Persistent Entity Highlights ---"));
                                            if (BomboConfig.get().highlights.isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal("  §7None"));
                                            } else {
                                                for (Map.Entry<String, BomboConfig.HighlightInfo> entry : BomboConfig
                                                        .get().highlights.entrySet()) {
                                                    String targetName = entry.getKey();
                                                    String color = entry.getValue().color;
                                                    boolean enabled = entry.getValue().enabled;

                                                    ClickEvent toggleClick = LF.createClickEventRobust("RUN_COMMAND",
                                                            "/b highlight toggle " + targetName);
                                                    Component toggleBtn = enabled ? Component.literal(" §a[Enabled]") : Component.literal(" §c[Disabled]");
                                                    if (toggleClick != null) {
                                                        toggleBtn = (enabled ? Component.literal(" §a[Enabled]") : Component.literal(" §c[Disabled]"))
                                                                .withStyle(style -> style.withClickEvent(toggleClick));
                                                    }

                                                    ClickEvent click = LF.createClickEventRobust("RUN_COMMAND",
                                                            "/b highlight remove " + targetName);
                                                    Component removeBtn = Component.literal(" §c[Remove]");
                                                    if (click != null)
                                                        removeBtn = Component.literal(" §c[Remove]")
                                                                .withStyle(style -> style.withClickEvent(click));
                                                    context.getSource()
                                                            .sendFeedback(Component
                                                                    .literal("  §e" + targetName + " §7- §b" + color)
                                                                    .append(toggleBtn)
                                                                    .append(removeBtn));
                                                }
                                            }
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            BomboConfig.get().highlights.clear();
                                            BomboConfig.save();
                                            context.getSource().sendFeedback(
                                                    Component.literal(PREFIX + "§aCleared all highlights."));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("mob", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                                        .suggests((context, builder2) -> {
                                                            for (String c : SlotHighlight.COLORS)
                                                                builder2.suggest(c);
                                                            return builder2.buildFuture();
                                                        })
                                                        .executes(context -> {
                                                            String mob = StringArgumentType.getString(context, "mob");
                                                            String color = StringArgumentType
                                                                    .getString(context, "color").toUpperCase();
                                                            BomboConfig.get().highlights.put(mob.toLowerCase(),
                                                                    new BomboConfig.HighlightInfo(color, false));
                                                            BomboConfig.save();
                                                            context.getSource()
                                                                    .sendFeedback(Component.literal(
                                                                            PREFIX + "§aHighlight added for §e" + mob
                                                                                    + " §awith color §b" + color));
                                                            return 1;
                                                        })
                                                        .then(ClientCommandManager.argument("showInvisible",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                                        .integer(0, 1))
                                                                .executes(context -> {
                                                                    String mob = StringArgumentType.getString(context,
                                                                            "mob");
                                                                    String color = StringArgumentType
                                                                            .getString(context, "color").toUpperCase();
                                                                    int siInt = com.mojang.brigadier.arguments.IntegerArgumentType
                                                                            .getInteger(context, "showInvisible");
                                                                    boolean si = (siInt == 1);
                                                                    BomboConfig.get().highlights.put(mob.toLowerCase(),
                                                                            new BomboConfig.HighlightInfo(color, si));
                                                                    BomboConfig.save();
                                                                    context.getSource()
                                                                            .sendFeedback(Component.literal(PREFIX
                                                                                    + "§aHighlight added for §e" + mob
                                                                                    + " §7(Invis: " + si + ")"));
                                                                    return 1;
                                                                })))))
                                .then(ClientCommandManager.argument("mob", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                                .suggests((context, builder2) -> {
                                                    for (String c : SlotHighlight.COLORS)
                                                        builder2.suggest(c);
                                                    return builder2.buildFuture();
                                                })
                                                .executes(context -> {
                                                    String mob = StringArgumentType.getString(context, "mob");
                                                    String color = StringArgumentType.getString(context, "color")
                                                            .toUpperCase();
                                                    BomboConfig.get().highlights.put(mob.toLowerCase(),
                                                            new BomboConfig.HighlightInfo(color, false));
                                                    BomboConfig.save();
                                                    context.getSource().sendFeedback(
                                                            Component.literal(PREFIX + "§aHighlight added for §e" + mob
                                                                    + " §awith color §b" + color));
                                                    return 1;
                                                })
                                                .then(ClientCommandManager
                                                        .argument("showInvisible",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                                        .integer(0, 1))
                                                        .executes(context -> {
                                                            String mob = StringArgumentType.getString(context, "mob");
                                                            String color = StringArgumentType
                                                                    .getString(context, "color").toUpperCase();
                                                            int siInt = com.mojang.brigadier.arguments.IntegerArgumentType
                                                                    .getInteger(context, "showInvisible");
                                                            boolean si = (siInt == 1);
                                                            BomboConfig.get().highlights.put(mob.toLowerCase(),
                                                                    new BomboConfig.HighlightInfo(color, si));
                                                            BomboConfig.save();
                                                            context.getSource()
                                                                    .sendFeedback(Component
                                                                            .literal(PREFIX + "§aHighlight added for §e"
                                                                                    + mob + " §7(Invis: " + si + ")"));
                                                            return 1;
                                                        }))))
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(
                                            PREFIX + "§7Usage: /b highlight <mob> <color> [showInvis: true/false]"));
                                    context.getSource().sendFeedback(
                                            Component.literal(PREFIX + "§7Subcommands: list, remove <name>, clear, toggle <name>"));
                                    return 1;
                                }));

                        builder.then(ClientCommandManager.literal("left").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleLeft();
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("right").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleRight();
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("back").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleBackward();
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("forw").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleForward();
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("break").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleBreak();
                            return 1;
                        }));
                        builder.then(ClientCommandManager.literal("use").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleUse();
                            return 1;
                        }));

                        builder.then(ClientCommandManager.literal("gui").executes(context -> {
                            openHudMoveNextTick = true;
                            return 1;
                        }));

                        builder.then(ClientCommandManager.literal("corpsedebug").executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.level == null) {
                                context.getSource().sendFeedback(Component.literal("§cWorld is null!"));
                                return 1;
                            }
                            int totalCount = 0;
                            int standCount = 0;
                            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                                totalCount++;
                                if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
                                    standCount++;
                                    net.minecraft.world.item.ItemStack helmet = stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                                    String itemStr = "None";
                                    if (helmet != null && !helmet.isEmpty()) {
                                        itemStr = helmet.getHoverName().getString();
                                    }
                                    context.getSource().sendFeedback(Component.literal(
                                            "§eStand ID: " + stand.getId() + " §7- Pos: " + stand.blockPosition() + " §7- Head: §b" + itemStr
                                    ));
                                }
                            }
                            context.getSource().sendFeedback(Component.literal(
                                    "§aScan complete. Checked " + totalCount + " entities. Found " + standCount + " armor stands."
                            ));
                            return 1;
                        }));

                        builder.then(ClientCommandManager.literal("resetdice").executes(context -> {
                            DiceTracker.reset();
                            context.getSource().sendFeedback(
                                    Component.literal(PREFIX + "§aDice Tracker statistics have been reset!"));
                            return 1;
                        }));

                        builder.then(ClientCommandManager.literal("msg")
                                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String msg = StringArgumentType.getString(context, "message").replace('&',
                                                    '§');
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.gui != null && mc.gui.getChat() != null) {
                                                mc.gui.getChat().addMessage(Component.literal(msg));
                                            }
                                            processChatMessage(msg);
                                            return 1;
                                        })));

                        // --- Particle List + ESP ---
                        builder.then(ClientCommandManager.literal("particles")
                                .executes(context -> {
                                    // List nearby particles
                                    java.util.Map<String, Integer> summary = ParticleTracker
                                            .getSummary(ParticleTracker.espRadius);
                                    context.getSource().sendFeedback(
                                            Component.literal(PREFIX + "§6Nearby Particles (last 5s, radius §e"
                                                    + (int) ParticleTracker.espRadius + "§6 blocks):"));
                                    if (summary.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("  §7None detected."));
                                    } else {
                                        for (java.util.Map.Entry<String, Integer> entry : summary.entrySet()) {
                                            int col = ParticleTracker.colorForType(entry.getKey());
                                            String hexStr = String.format("#%06X", col);
                                            context.getSource().sendFeedback(Component.literal(
                                                    "  §7» §r" + entry.getKey() + " §8x" + entry.getValue()));
                                        }
                                    }
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("esp")
                                        .executes(context -> {
                                            ParticleTracker.espEnabled = !ParticleTracker.espEnabled;
                                            ParticleESP.typeFilter = null;
                                            context.getSource()
                                                    .sendFeedback(Component.literal(PREFIX + "§7Particle ESP: "
                                                            + (ParticleTracker.espEnabled ? "§aON" : "§cOFF")));
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("type", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String filter = StringArgumentType.getString(context, "type");
                                                    if (filter.equals("off") || filter.equals("none")) {
                                                        ParticleESP.typeFilter = null;
                                                        ParticleTracker.espEnabled = false;
                                                        context.getSource().sendFeedback(
                                                                Component.literal(PREFIX + "§cParticle ESP disabled."));
                                                    } else {
                                                        ParticleESP.typeFilter = filter;
                                                        ParticleTracker.espEnabled = true;
                                                        context.getSource().sendFeedback(Component.literal(PREFIX
                                                                + "§aParticle ESP §aON §7— filtering: §e" + filter));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("radius")
                                        .then(ClientCommandManager
                                                .argument("r",
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,
                                                                128))
                                                .executes(context -> {
                                                    int r = com.mojang.brigadier.arguments.IntegerArgumentType
                                                            .getInteger(context, "r");
                                                    ParticleTracker.espRadius = r;
                                                    context.getSource().sendFeedback(Component.literal(PREFIX
                                                            + "§7Particle ESP radius set to §e" + r + "§7 blocks."));
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            ParticleTracker.clear();
                                            context.getSource().sendFeedback(
                                                    Component.literal(PREFIX + "§aParticle history cleared."));
                                            return 1;
                                        })));

                        builder.then(ClientCommandManager.literal("anvil")
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager
                                                .argument("tier",
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,
                                                                100))
                                                .executes(context -> {
                                                    Minecraft mc = Minecraft.getInstance();
                                                    if (mc.player != null) {
                                                        ItemStack hand = mc.player.getMainHandItem();
                                                        if (!hand.isEmpty()) {
                                                            Map<String, Integer> enchants = getEnchantments(hand);
                                                            if (!enchants.isEmpty()) {
                                                                int tier = com.mojang.brigadier.arguments.IntegerArgumentType
                                                                        .getInteger(context, "tier");
                                                                for (String enc : enchants.keySet()) {
                                                                    BomboConfig.get().anvilAutoCombine.put(enc, tier);
                                                                    context.getSource()
                                                                            .sendFeedback(Component.literal(PREFIX
                                                                                    + "§aAdded anvil auto-combine: §e"
                                                                                    + enc + " §7(Target Tier: " + tier
                                                                                    + ")"));
                                                                }
                                                                BomboConfig.save();
                                                                return 1;
                                                            } else {
                                                                context.getSource().sendFeedback(Component.literal(
                                                                        PREFIX + "§cNo enchantments found on this item! §8(NBT may be flat or missing ExtraAttributes)"));
                                                                return 0;
                                                            }
                                                        } else {
                                                            context.getSource().sendFeedback(Component.literal(PREFIX
                                                                    + "§cPlease hold an enchanted book in your main hand!"));
                                                            return 0;
                                                        }
                                                    }
                                                    return 0;
                                                }))
                                        .then(ClientCommandManager.argument("enchant", StringArgumentType.word())
                                                .then(ClientCommandManager
                                                        .argument("tier",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                                        .integer(1, 100))
                                                        .executes(context -> {
                                                            String enc = StringArgumentType
                                                                    .getString(context, "enchant").toLowerCase();
                                                            int tier = com.mojang.brigadier.arguments.IntegerArgumentType
                                                                    .getInteger(context, "tier");
                                                            BomboConfig.get().anvilAutoCombine.put(enc, tier);
                                                            BomboConfig.save();
                                                            context.getSource()
                                                                    .sendFeedback(Component.literal(PREFIX
                                                                            + "§aAdded anvil auto-combine: §e" + enc
                                                                            + " §7(Target Tier: " + tier + ")"));
                                                            return 1;
                                                        }))))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("enchant", StringArgumentType.word())
                                                .executes(context -> {
                                                    String enc = StringArgumentType.getString(context, "enchant")
                                                            .toLowerCase();
                                                    if (BomboConfig.get().anvilAutoCombine.remove(enc) != null) {
                                                        BomboConfig.save();
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§aRemoved anvil auto-combine for: §e" + enc));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(PREFIX
                                                                + "§cNo anvil auto-combine found for: §e" + enc));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> {
                                            context.getSource().sendFeedback(
                                                    Component.literal(PREFIX + "§6--- Anvil Auto-Combine ---"));
                                            if (BomboConfig.get().anvilAutoCombine.isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal("  §7None"));
                                            } else {
                                                for (Map.Entry<String, Integer> entry : BomboConfig
                                                        .get().anvilAutoCombine.entrySet()) {
                                                    context.getSource().sendFeedback(Component.literal("  §e"
                                                            + entry.getKey() + " §7- §bTier " + entry.getValue()));
                                                }
                                            }
                                            return 1;
                                        })));

                        builder.then(ClientCommandManager.literal("view")
                                .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                        .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String user = StringArgumentType.getString(context, "username");
                                                    String pathWithHighlight = StringArgumentType.getString(context,
                                                            "path");
                                                    int highlight = -1;
                                                    String path = pathWithHighlight;
                                                    if (pathWithHighlight.contains(" ")) {
                                                        try {
                                                            int lastSpace = pathWithHighlight.lastIndexOf(" ");
                                                            highlight = Integer.parseInt(
                                                                    pathWithHighlight.substring(lastSpace + 1));
                                                            path = pathWithHighlight.substring(0, lastSpace);
                                                        } catch (Exception e) {
                                                        }
                                                    }
                                                    LF.openVirtualContainer(user, path.replace("\"", ""), highlight);
                                                    return 1;
                                                }))));
                        builder.then(ClientCommandManager.literal("lf")
                                .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                        .executes(context -> {
                                            LF.show(StringArgumentType.getString(context, "username"), "", false);
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    LF.show(StringArgumentType.getString(context, "username"),
                                                            StringArgumentType.getString(context, "query"), false);
                                                    return 1;
                                                }))));
                        builder.then(ClientCommandManager.literal("lfc")
                                .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                        .executes(context -> {
                                            LF.show(StringArgumentType.getString(context, "username"), "", true);
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    LF.show(StringArgumentType.getString(context, "username"),
                                                            StringArgumentType.getString(context, "query"), true);
                                                    return 1;
                                                }))));
                        builder.then(ClientCommandManager.literal("lb")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null)
                                        LF.show(mc.getUser().getName(), "", false);
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String query = StringArgumentType.getString(context, "query");
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.player != null) {
                                                String name = mc.getUser().getName();
                                                LF.show(name, query, false);
                                            }
                                            return 1;
                                        })));

                        builder.then(ClientCommandManager.literal("pet")
                                .then(ClientCommandManager.literal("save")
                                        .then(ClientCommandManager.argument("slot", StringArgumentType.word())
                                                .executes(context -> {
                                                    String slot = StringArgumentType.getString(context, "slot");
                                                    PetManager.savePet(context.getSource(), slot);
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("apply")
                                        .then(ClientCommandManager.argument("slot", StringArgumentType.word())
                                                .executes(context -> {
                                                    String slot = StringArgumentType.getString(context, "slot");
                                                    PetManager.applyPet(context.getSource(), slot);
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.argument("slot", StringArgumentType.word())
                                        .executes(context -> {
                                            String slot = StringArgumentType.getString(context, "slot");
                                            PetManager.applyPet(context.getSource(), slot);
                                            return 1;
                                        })));

                        builder.then(ClientCommandManager.literal("ep")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        int pearlCount = 0;
                                        for (int j = 0; j < mc.player.getInventory().getContainerSize(); j++) {
                                            ItemStack stack = mc.player.getInventory().getItem(j);
                                            if (!stack.isEmpty()) {
                                                String internalId = SkyblockUtils.getInternalId(stack);
                                                if ("ENDER_PEARL".equals(internalId)
                                                        || stack.is(net.minecraft.world.item.Items.ENDER_PEARL)) {
                                                    pearlCount += stack.getCount();
                                                }
                                            }
                                        }
                                        if (pearlCount < 16) {
                                            int toGet = 16 - pearlCount;
                                            mc.player.connection.sendCommand("gfs ENDER_PEARL " + toGet);
                                            if (BomboConfig.get().debugCommands || BomboConfig.get().debugMaster) {
                                                context.getSource()
                                                        .sendFeedback(Component.literal("§7[Bombo] Found §e"
                                                                + pearlCount + "§7 pearls. Requesting §e" + toGet
                                                                + "§7 more from sack!"));
                                            }
                                        } else {
                                            if (BomboConfig.get().debugCommands || BomboConfig.get().debugMaster) {
                                                context.getSource()
                                                        .sendFeedback(Component.literal("§7[Bombo] Already have §e"
                                                                + pearlCount + "§7 pearls (>= 16)."));
                                            }
                                        }
                                    }
                                    return 1;
                                }));

                        builder.then(ClientCommandManager.literal("get")
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.argument("number", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("alias", StringArgumentType.word())
                                                        .executes(context -> {
                                                            Minecraft mc = Minecraft.getInstance();
                                                            if (mc.player == null)
                                                                return 0;
                                                            ItemStack hand = mc.player.getMainHandItem();
                                                            if (hand.isEmpty()) {
                                                                context.getSource().sendFeedback(Component.literal(
                                                                        PREFIX + "§cPlease hold the item you want to add in your main hand!"));
                                                                return 0;
                                                            }
                                                            String itemId = SkyblockUtils.getInternalId(hand);
                                                            if (itemId == null || itemId.isEmpty()) {
                                                                itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                                                        .getKey(hand.getItem()).getPath().toUpperCase();
                                                            }
                                                            int number = IntegerArgumentType.getInteger(context,
                                                                    "number");
                                                            String alias = StringArgumentType
                                                                    .getString(context, "alias").toLowerCase();

                                                            BomboConfig.get().getTargets.put(alias,
                                                                    new BomboConfig.GetTarget(itemId, number));
                                                            BomboConfig.save();

                                                            context.getSource().sendFeedback(Component.literal(PREFIX
                                                                    + "§aAdded get target: §e" + itemId + " §7(Target: "
                                                                    + number + ") under alias §b" + alias));
                                                            return 1;
                                                        }))))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("alias", StringArgumentType.word())
                                                .executes(context -> {
                                                    String alias = StringArgumentType.getString(context, "alias")
                                                            .toLowerCase();
                                                    if (BomboConfig.get().getTargets.remove(alias) != null) {
                                                        BomboConfig.save();
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§aRemoved get target for alias §e" + alias));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§cNo get target found for alias §e" + alias));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> {
                                            context.getSource()
                                                    .sendFeedback(Component.literal(PREFIX + "§6--- Get Targets ---"));
                                            if (BomboConfig.get().getTargets.isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal("  §7None"));
                                            } else {
                                                for (Map.Entry<String, BomboConfig.GetTarget> entry : BomboConfig
                                                        .get().getTargets.entrySet()) {
                                                    context.getSource().sendFeedback(Component.literal("  §eb" + " get "
                                                            + entry.getKey() + " §7-> §b" + entry.getValue().itemId
                                                            + " §7(Target: " + entry.getValue().targetAmount + ")"));
                                                }
                                            }
                                            return 1;
                                        }))
                                .then(ClientCommandManager.argument("alias_or_id", StringArgumentType.word())
                                        .executes(context -> {
                                            String alias = StringArgumentType.getString(context, "alias_or_id")
                                                    .toLowerCase();
                                            BomboConfig.GetTarget target = BomboConfig.get().getTargets.get(alias);
                                            if (target == null) {
                                                context.getSource().sendFeedback(Component.literal(
                                                        PREFIX + "§cNo get target found for alias §e" + alias));
                                                return 0;
                                            }

                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.player != null) {
                                                int currentCount = 0;
                                                for (int j = 0; j < mc.player.getInventory().getContainerSize(); j++) {
                                                    ItemStack stack = mc.player.getInventory().getItem(j);
                                                    if (!stack.isEmpty()) {
                                                        String itemId = SkyblockUtils.getInternalId(stack);
                                                        if (itemId == null || itemId.isEmpty()) {
                                                            itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                                                    .getKey(stack.getItem()).getPath().toUpperCase();
                                                        }
                                                        if (itemId.equalsIgnoreCase(target.itemId)) {
                                                            currentCount += stack.getCount();
                                                        }
                                                    }
                                                }
                                                if (currentCount < target.targetAmount) {
                                                    int missing = target.targetAmount - currentCount;
                                                    mc.player.connection
                                                            .sendCommand("gfs " + target.itemId + " " + missing);
                                                    context.getSource().sendFeedback(
                                                            Component.literal(PREFIX + "§7Requesting §e" + missing
                                                                    + " §7more §e" + target.itemId + " §7(missing §e"
                                                                    + missing + "/" + target.targetAmount + "§7)."));
                                                } else {
                                                    context.getSource()
                                                            .sendFeedback(Component.literal(PREFIX + "§7Already have §e"
                                                                    + currentCount + "/" + target.targetAmount + " §e"
                                                                    + target.itemId + "§7."));
                                                }
                                            }
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("number", IntegerArgumentType.integer(1))
                                                .then(ClientCommandManager.argument("alias", StringArgumentType.word())
                                                        .executes(context -> {
                                                            String itemId = StringArgumentType
                                                                    .getString(context, "alias_or_id").toUpperCase();
                                                            int number = IntegerArgumentType.getInteger(context,
                                                                    "number");
                                                            String alias = StringArgumentType
                                                                    .getString(context, "alias").toLowerCase();

                                                            BomboConfig.get().getTargets.put(alias,
                                                                    new BomboConfig.GetTarget(itemId, number));
                                                            BomboConfig.save();

                                                            context.getSource().sendFeedback(Component.literal(PREFIX
                                                                    + "§aAdded get target: §e" + itemId + " §7(Target: "
                                                                    + number + ") under alias §b" + alias));
                                                            return 1;
                                                        })))));
                    };

                    setupCommands.accept(bBuilder);
                    setupCommands.accept(baBuilder);
                    setupCommands.accept(bomboBuilder);

                    dispatcher.register(bBuilder);
                    dispatcher.register(baBuilder);
                    dispatcher.register(bomboBuilder);

                    dispatcher.register(ClientCommandManager.literal("bomboprof")
                            .executes(context -> {
                                BomboConfigGUI.selectedCategory = 5;
                                openGuiNextTick = true;
                                return 1;
                            }));
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register /bombo commands!", t);
                }

                // --- PRIORITY 3: SBE Roots ---
                try {
                    String[] sbeRoots = { "nw", "cata", "skills", "slayer", "trophyfish", "crimson" };
                    for (String s : sbeRoots) {
                        dispatcher.register(ClientCommandManager.literal(s)
                                .executes(context -> {
                                    SBECommands.handleCommand(s, Minecraft.getInstance().player.getName().getString(),
                                            null);
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

                    final long[] lastMuseumClick = { 0L };
                    final String[] lastMuseumTarget = { "" };
                    dispatcher.register(ClientCommandManager.literal("bombo_museum_click")
                            .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                    .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer())
                                            .executes(context -> {
                                                String user = StringArgumentType.getString(context, "username");
                                                int slot = IntegerArgumentType.getInteger(context, "slot");
                                                long now = System.currentTimeMillis();
                                                String target = user + ":" + slot;

                                                if (now - lastMuseumClick[0] < 2000
                                                        && target.equals(lastMuseumTarget[0])) {
                                                    executeTracked("/warp museum");
                                                    lastMuseumClick[0] = 0;
                                                    lastMuseumTarget[0] = "";
                                                } else {
                                                    lastMuseumClick[0] = now;
                                                    lastMuseumTarget[0] = target;
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "§7[Bombo] Click again within 2s to §b/warp museum§7!"));
                                                }
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommandManager.literal("tk")
                            .then(ClientCommandManager.argument("username", StringArgumentType.string())
                                    .executes(context -> {
                                        LF.showToolkit(StringArgumentType.getString(context, "username"), 50);
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("limit", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                LF.showToolkit(StringArgumentType.getString(context, "username"),
                                                        IntegerArgumentType.getInteger(context, "limit"));
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommandManager.literal("deal")
                            .executes(context -> {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc.player == null || mc.level == null)
                                    return 0;
                                Set<String> tabPlayerNames = new HashSet<>();
                                if (mc.getConnection() != null) {
                                    for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                                        String name = info.getProfile().name();
                                        if (name != null)
                                            tabPlayerNames.add(cleanName(name));
                                        if (info.getTabListDisplayName() != null)
                                            tabPlayerNames.add(cleanName(info.getTabListDisplayName().getString()));
                                    }
                                }
                                List<Player> nearbyPlayers = new ArrayList<>();
                                for (Player p : mc.level.players()) {
                                    if (p == mc.player)
                                        continue;
                                    String pName = p.getGameProfile().name();
                                    if (!tabPlayerNames.contains(cleanName(pName)))
                                        continue;
                                    double distSq = p.distanceToSqr(mc.player);
                                    if (distSq <= 100.0)
                                        nearbyPlayers.add(p);
                                }
                                String targetName = null;
                                if (nearbyPlayers.isEmpty()) {
                                    context.getSource()
                                            .sendFeedback(Component.literal("§cNo players nearby within 10 blocks!"));
                                    return 0;
                                } else if (nearbyPlayers.size() == 1) {
                                    targetName = nearbyPlayers.get(0).getGameProfile().name();
                                } else {
                                    if (mc.hitResult instanceof EntityHitResult ehr
                                            && ehr.getEntity() instanceof Player p) {
                                        String pName = p.getGameProfile().name();
                                        if (!tabPlayerNames.contains(cleanName(pName))) {
                                            context.getSource().sendFeedback(
                                                    Component.literal("§cLooking at an NPC, not a real player!"));
                                            return 0;
                                        }
                                        if (p.distanceToSqr(mc.player) <= 100.0)
                                            targetName = pName;
                                        else {
                                            context.getSource()
                                                    .sendFeedback(Component.literal("§cPlayer too far away!"));
                                            return 0;
                                        }
                                    } else {
                                        context.getSource().sendFeedback(
                                                Component.literal("§cMultiple players nearby. Look at one!"));
                                        return 0;
                                    }
                                }
                                if (targetName != null) {
                                    final String finalTarget = targetName;
                                    mc.execute(() -> {
                                        if (mc.player != null)
                                            mc.player.connection.sendCommand("trade " + finalTarget);
                                    });
                                }
                                return 1;
                            }));
                    dispatcher.register(ClientCommandManager.literal("bits")
                            .executes(context -> {
                                BitsManager.fetchTopBits(5).thenAccept(lines -> {
                                    for (String line : lines)
                                        context.getSource().sendFeedback(Component.literal(line));
                                });
                                return 1;
                            })
                            .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1, 100))
                                    .executes(context -> {
                                        int amount = IntegerArgumentType.getInteger(context, "amount");
                                        BitsManager.fetchTopBits(amount).thenAccept(lines -> {
                                            for (String line : lines)
                                                context.getSource().sendFeedback(Component.literal(line));
                                        });
                                        return 1;
                                    })));
                    dispatcher.register(ClientCommandManager.literal("bclick")
                            .executes(context -> {
                                ClickLogic.listTargets(context.getSource());
                                return 1;
                            }));
                    dispatcher.register(ClientCommandManager.literal("bc")
                            .executes(context -> {
                                context.getSource()
                                        .sendFeedback(Component.literal("§8[§bBomboAddons§8] §cUsage: /bc <message>"));
                                return 1;
                            })
                            .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        if (!BomboConfig.get().ircChatEnabled) {
                                            context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §cIRC Chat is currently disabled! Toggle it on with §e/b chat§c."));
                                            return 1;
                                        }
                                        String message = StringArgumentType.getString(context, "message");
                                        IRCClient.sendMessage(message);
                                        return 1;
                                    })));

                    dispatcher.register(ClientCommandManager.literal("chat")
                            .then(ClientCommandManager.literal("b")
                                    .executes(context -> {
                                        BomboConfig.get().ircDefaultChat = true;
                                        BomboConfig.save();
                                        context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Default chat set to §eIRC§7. Messages will be sent to IRC chat."));
                                        return 1;
                                    }))
                            .then(ClientCommandManager.argument("channel", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String channel = StringArgumentType.getString(context, "channel");
                                        BomboConfig.get().ircDefaultChat = false;
                                        BomboConfig.save();
                                        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null) {
                                            Minecraft.getInstance().player.connection.sendCommand("chat " + channel);
                                        }
                                        return 1;
                                    }))
                            .executes(context -> {
                                if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null) {
                                    Minecraft.getInstance().player.connection.sendCommand("chat");
                                }
                                return 1;
                            }));

                    dispatcher.register(ClientCommandManager.literal("c")
                            .then(ClientCommandManager.argument("expression", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        SkyblockCalculator.EvaluationResult res = SkyblockCalculator
                                                .evaluate(StringArgumentType.getString(context, "expression"));
                                        context.getSource().sendFeedback(
                                                res.error != null ? Component.literal(res.error) : res.breakdown);
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
                                                InventoryManager.openSnapshot(
                                                        StringArgumentType.getString(context, "name"),
                                                        IntegerArgumentType.getInteger(context, "index"));
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommandManager.literal("savei")
                            .executes(context -> {
                                InventoryManager.captureCurrentGUI();
                                return 1;
                            }));

                    java.util.function.Consumer<String> registerHotbarCommand = nameLiteral -> {
                        dispatcher.register(ClientCommandManager.literal(nameLiteral)
                            .then(ClientCommandManager.literal("save")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                if (HotbarSwapper.saveSnapshot(name)) {
                                                    context.getSource().sendFeedback(
                                                            Component.literal("§aSaved hotbar snapshot: §e" + name));
                                                } else {
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "§cFailed to save hotbar snapshot (player is null)."));
                                                }
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("s")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                if (HotbarSwapper.saveSnapshot(name)) {
                                                    context.getSource().sendFeedback(
                                                            Component.literal("§aSaved hotbar snapshot: §e" + name));
                                                } else {
                                                    context.getSource().sendFeedback(Component.literal(
                                                            "§cFailed to save hotbar snapshot (player is null)."));
                                                }
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("delete")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                if (HotbarSwapper.deleteSnapshot(name)) {
                                                    context.getSource().sendFeedback(
                                                            Component.literal("§aDeleted hotbar snapshot: §e" + name));
                                                } else {
                                                    context.getSource().sendFeedback(
                                                            Component.literal("§cSnapshot not found: §e" + name));
                                                }
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("list")
                                    .executes(context -> {
                                        context.getSource()
                                                .sendFeedback(Component.literal("§6--- Hotbar Snapshots ---"));
                                        for (String id : HotbarSwapper.list()) {
                                            context.getSource().sendFeedback(Component.literal("§7- §e" + id));
                                        }
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("apply")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                if (HotbarSwapper.exists(name)) {
                                                    HotbarSwapper.apply(name);
                                                    context.getSource().sendFeedback(
                                                            Component.literal("§aApplied hotbar snapshot: §e" + name));
                                                } else {
                                                    context.getSource().sendFeedback(
                                                            Component.literal("§cSnapshot not found: §e" + name));
                                                }
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("a")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                if (HotbarSwapper.exists(name)) {
                                                    HotbarSwapper.apply(name);
                                                    context.getSource().sendFeedback(
                                                            Component.literal("§aApplied hotbar snapshot: §e" + name));
                                                } else {
                                                    context.getSource().sendFeedback(
                                                            Component.literal("§cSnapshot not found: §e" + name));
                                                }
                                                return 1;
                                            })))
                        );
                    };

                    registerHotbarCommand.accept("bombohb");
                    registerHotbarCommand.accept("bhb");
                    dispatcher.register(ClientCommandManager.literal("mod")
                            .executes(context -> {
                                Path modsFolder = FabricLoader.getInstance().getGameDir().resolve("mods");
                                Util.getPlatform().openUri(modsFolder.toUri());
                                context.getSource().sendFeedback(Component.literal("§aOpening mods folder..."));
                                return 1;
                            }));

                    // --- PRIORITY 6: Quick Join Commands ---
                    String[] floorNames = { "one", "two", "three", "four", "five", "six", "seven" };
                    for (int i = 1; i <= 7; i++) {
                        final int f = i;
                        dispatcher.register(ClientCommandManager.literal("f" + i).executes(c -> {
                            if (BomboConfig.get().quickJoinCommands) {
                                Minecraft.getInstance().execute(() -> {
                                    if (Minecraft.getInstance().player != null)
                                        Minecraft.getInstance().player.connection
                                                .sendCommand("joininstance catacombs_floor_" + floorNames[f - 1]);
                                });
                                return 1;
                            }
                            return 0;
                        }));
                        dispatcher.register(ClientCommandManager.literal("m" + i).executes(c -> {
                            if (BomboConfig.get().quickJoinCommands) {
                                Minecraft.getInstance().execute(() -> {
                                    if (Minecraft.getInstance().player != null)
                                        Minecraft.getInstance().player.connection.sendCommand(
                                                "joininstance master_catacombs_floor_" + floorNames[f - 1]);
                                });
                                return 1;
                            }
                            return 0;
                        }));
                    }
                    String[] kuudraTiers = { "normal", "hot", "burning", "fiery", "infernal" };
                    for (int i = 1; i <= 5; i++) {
                        final int t = i;
                        dispatcher.register(ClientCommandManager.literal("t" + i).executes(c -> {
                            if (BomboConfig.get().quickJoinCommands) {
                                Minecraft.getInstance().execute(() -> {
                                    if (Minecraft.getInstance().player != null)
                                        Minecraft.getInstance().player.connection
                                                .sendCommand("joininstance kuudra_" + kuudraTiers[t - 1]);
                                });
                                return 1;
                            }
                            return 0;
                        }));
                    }
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register inventory commands!", t);
                }

            });

            BomboConfig.load();
            RankCache.load();
            PlaytimeTracker.load();
            DiceTracker.load();
            ChatPeek.init();
            BazaarUtils.init();
            LowestBinManager.ensureLoaded();
            ItemHotkeys.init();

            ModUpdater.init();
            registerTickEvents();
            DiceHud.init();
            KuudraTimer.init();
            DungeonPadTimers.init();
            CorpseHighlight.init();
            IRCClient.start();

            WorldRenderEvents.AFTER_ENTITIES.register(context -> {
                if (BomboConfig.get().debugEntities)
                    System.out.println("DEBUG: AFTER_ENTITIES Fired!");
                HighlightESP.render(context);
                PestESP.render(context);
                ParticleESP.render(context);
                try {
                    me.bombo.bomboaddons_final.kuudra.pearls.Pearls.render(context);
                } catch (Throwable t) {
                }
                try {
                    GardenWaypoints.render(context);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                try {
                    me.bombo.bomboaddons_final.eggfinder.EggFinder.render(context);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                try {
                    CorpseHighlight.render(context);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });

            HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
                if (BomboConfig.get().pestEspTracer) {
                    for (PestESP.PestTracer tracer : PestESP.TRACERS) {
                        BomboRenderUtils.draw2DLine(graphics, tracer.start.x, tracer.start.y, tracer.end.x,
                                tracer.end.y, tracer.color, tracer.thickness);
                    }
                }

                try {
                    if (BomboConfig.get().kuudraDebug) {
                        graphics.drawString(Minecraft.getInstance().font, "§d§lHUD RENDER TEST ACTIVE", 10, 50,
                                0xFFFF00FF, true);
                    }
                    for (me.bombo.bomboaddons_final.kuudra.pearls.Pearls.PearlHUDText t : me.bombo.bomboaddons_final.kuudra.pearls.Pearls.HUD_TEXTS) {
                        graphics.drawCenteredString(Minecraft.getInstance().font, t.text, (int) t.x, (int) t.y,
                                t.color);
                    }
                } catch (Throwable t) {
                }

                // Only render HUD if no screen is open or it's the HudMoveScreen
                if (Minecraft.getInstance().screen == null || Minecraft.getInstance().screen instanceof HudMoveScreen) {
                    FeastBakeryHud.onHudRender(graphics);
                    ExperimentationTableHud.onHudRender(graphics);
                }
                if (Minecraft.getInstance().screen == null) {
                    GardenMovement.drawDirectionWarning(graphics);
                }
            });

            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT
                    .register((client, screen, scaledWidth, scaledHeight) -> {
                        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen)
                                .register((screen1, graphics, mouseX, mouseY, tickDelta) -> {
                                    FeastBakeryHud.onHudRender(graphics);
                                    ExperimentationTableHud.onHudRender(graphics);
                                });
                    });

            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                if (client.getCurrentServer() != null) {
                    lastServerData = client.getCurrentServer();
                }
                LowestBinManager.reload();
                AutoExperiments.reset();
                ModUpdater.checkAndUpdate(true);
                
                // Fetch/update Skyblocker WebSocket token
                try {
                    me.bombo.bomboaddons_final.eggfinder.EggAuth.updateToken();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                
                // Proactively fetch local player's rank on join to ensure cache is updated
                if (client.getUser() != null) {
                    String name = client.getUser().getName();
                    if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Player")) {
                        RankCache.fetchAsync(name);
                    }
                }
            });

            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                PlaytimeTracker.sendPlaytimeDataToCloud();
                try {
                    me.bombo.bomboaddons_final.eggfinder.EggFinder.clearEggs();
                    me.bombo.bomboaddons_final.eggfinder.EggWebSocket.disconnect();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });

            ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
                if (world != null && SkyblockUtils.isConnectedToHypixel()) {
                    // Reset locraw variables on lobby change
                    locrawServer = "";
                    locrawGametype = "";
                    locrawMode = "";
                    locrawMap = "";

                    // Schedule a locraw call in 2 seconds (40 ticks)
                    locrawDelayTicks = 40;
                }
            });

            ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
                try {
                    me.bombo.bomboaddons_final.eggfinder.EggFinder.onChatMessage(message, overlay);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                String plain = message.getString().trim();
                // Check if it is a valid locraw JSON response
                if (plain.startsWith("{") && plain.endsWith("}") && plain.contains("\"server\"")
                        && plain.contains("\"gametype\"")) {
                    try {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(plain)
                                .getAsJsonObject();
                        if (json.has("server"))
                            locrawServer = json.get("server").getAsString();
                        if (json.has("gametype"))
                            locrawGametype = json.get("gametype").getAsString();
                        if (json.has("mode"))
                            locrawMode = json.get("mode").getAsString();
                        if (json.has("map"))
                            locrawMap = json.get("map").getAsString();

                        // Dynamically update the current area immediately
                        String area = SkyblockUtils.mapLocrawToArea(locrawMode, locrawMap);
                        if (!area.equals("Unknown")) {
                            currentArea = area;
                        } else if (!"SKYBLOCK".equals(locrawGametype) && json.has("server")) {
                            String srv = json.get("server").getAsString().toLowerCase();
                            if (srv.contains("lobby")) {
                                currentArea = "Lobby";
                            } else if (srv.contains("limbo")) {
                                currentArea = "Limbo";
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (expectingLocrawCount > 0) {
                        expectingLocrawCount = Math.max(0, expectingLocrawCount - 1);
                        return false; // Blocks/hides the message from chat
                    }
                }
                return true;
            });

            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                String clean = message.getString().replaceAll("§.", "");
                DebugUtils.debug("chat", clean);
                DiceTracker.onChatMessage(clean);
                if (overlay) {
                    me.bombo.bomboaddons_final.kuudra.pearls.Pearls.onTitleReceived(clean);
                }
                processChatMessage(message.getString());
            });

        } catch (Throwable t) {
            Bomboaddons.LOGGER.error("[BomboAddons] CRITICAL ERROR in onInitializeClient!", t);
        }
    }

    private void registerTickEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                long now = System.currentTimeMillis();
                java.util.List<PendingCommand> toRun = new java.util.ArrayList<>();
                for (PendingCommand pc : pendingCommands) {
                    if (now >= pc.triggerTime) {
                        toRun.add(pc);
                    }
                }
                for (PendingCommand pc : toRun) {
                    executeTracked(pc.command);
                    pendingCommands.remove(pc);
                }
            } catch (Throwable t) {
            }
            try {
                me.bombo.bomboaddons_final.eggfinder.EggFinder.tick();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try {
                if (client.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
                    if (autoReconnectTicks > 0) {
                        autoReconnectTicks--;
                        int secondsLeft = (autoReconnectTicks + 19) / 20;
                        if (activeReconnectBtn != null) {
                            activeReconnectBtn.setMessage(
                                    Component.literal("Reconnect (" + secondsLeft + "s)"));
                        }
                        if (autoReconnectTicks == 0) {
                            reconnect(activeParent, client);
                        }
                    }
                } else {
                    autoReconnectTicks = -1;
                    activeReconnectBtn = null;
                    activeParent = null;
                }
            } catch (Throwable t) {
                // Safely catch any unexpected errors during menu tick
            }

            if (client.player != null) {
                if (client.player.tickCount % 20 == 0) {
                    currentArea = SkyblockUtils.getLocation();
                    currentSubArea = SkyblockUtils.getSubArea();
                }

                // Coord Binds check
                try {
                    BomboConfig.Settings s = BomboConfig.get();
                    if (s.coordBinds != null) {
                        List<BomboConfig.CoordBind> activeBinds = s.coordBinds.get(s.activeProfile);
                        List<BomboConfig.CoordBind> generalBinds = s.coordBinds.get("General");
                        List<BomboConfig.CoordBind> binds = new ArrayList<>();
                        if (activeBinds != null) binds.addAll(activeBinds);
                        if (generalBinds != null && !s.activeProfile.equals("General")) binds.addAll(generalBinds);

                        net.minecraft.world.phys.Vec3 playerPos = client.player.position();
                        for (BomboConfig.CoordBind bind : binds) {
                            if (!bind.enabled) continue;

                            // Check required island
                            if (bind.requiredIsland != null && !bind.requiredIsland.trim().isEmpty()) {
                                String currentAreaLocal = BomboaddonsClient.currentArea;
                                if (currentAreaLocal == null) currentAreaLocal = "";
                                String target = bind.requiredIsland.trim().toLowerCase();
                                boolean matched = currentAreaLocal.toLowerCase().contains(target);
                                if (!matched && client.level != null) {
                                    var scoreboard = client.level.getScoreboard();
                                    var sidebar = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
                                    if (sidebar != null) {
                                        for (String line : SkyblockUtils.getSidebarLines(scoreboard, sidebar)) {
                                            String clean = line.replaceAll("(?i)§.", "").trim().toLowerCase();
                                            if (clean.contains(target)) {
                                                matched = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (!matched) {
                                    bind.wasInside = false;
                                    continue;
                                }
                            }

                            double dist = playerPos.distanceTo(new net.minecraft.world.phys.Vec3(bind.x, bind.y, bind.z));
                            double r = bind.radius <= 0.0 ? 3.0 : bind.radius;
                            if (dist <= r) {
                                if (!bind.wasInside) {
                                    double minD = bind.minDelay;
                                    double maxD = bind.maxDelay;
                                    if (maxD > minD && maxD > 0.0) {
                                        double delaySec = minD + Math.random() * (maxD - minD);
                                        long triggerTime = System.currentTimeMillis() + (long)(delaySec * 1000.0);
                                        pendingCommands.add(new PendingCommand(bind.command, triggerTime));
                                    } else {
                                        executeTracked(bind.command);
                                    }
                                    bind.wasInside = true;
                                }
                            } else if (dist > r + 1.5) {
                                bind.wasInside = false;
                            }
                        }
                    }
                } catch (Throwable t) {
                    // Ignored
                }
            } else {
                if (menuTickCount++ % 20 == 0) {
                    currentArea = SkyblockUtils.getLocation();
                    currentSubArea = SkyblockUtils.getSubArea();
                }
            }
            ParticleTracker.onTick();
            PlaytimeTracker.tick();
            PetManager.onTick();

            // Handle lobby change locraw delay
            if (locrawDelayTicks > 0) {
                locrawDelayTicks--;
                if (locrawDelayTicks == 0) {
                    triggerLocraw();
                }
            }

            // Periodic locraw check (every 5 minutes)
            if (client.player != null && System.currentTimeMillis() - lastLocrawTime > 300000) {
                triggerLocraw();
            }

            // Safe execution of Config GUI logic
            try {
                if (openGuiNextTick && client.player != null) {
                    openGuiNextTick = false;
                    client.setScreen(new BomboConfigGUI(client.screen));
                }
                if (openHudMoveNextTick && client.player != null) {
                    openHudMoveNextTick = false;
                    client.setScreen(new HudMoveScreen());
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
                FuckDiorite.onTick();
            } catch (Throwable t) {
                // Silently ignore
            }

            try {
                AutoCombine.onTick();
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
                                String name = e.getName().getString();
                                if (e.isInvisible())
                                    name += " §7(Invisible)§r";
                                info.append(name).append(" (").append(e.getId()).append("), ");
                            }
                        }
                        DebugUtils.debug("entity", "Total: " + count + " | Nearby: " + info.toString());
                    }
                }
            } catch (Throwable t) {
            }

            try {
                me.bombo.bomboaddons_final.kuudra.pearls.KuudraUtils.onClientTick();
                me.bombo.bomboaddons_final.kuudra.pearls.Pearls.onClientTick();
            } catch (Throwable t) {
            }
        });
    }

    public static void executeTracked(String cmd) {
        if (cmd == null || cmd.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                String cleanCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                try {
                    DebugUtils.debug("command", "Runned: /" + cleanCmd);
                    // Check if it's a client-side command first using our stored dispatcher
                    if (clientDispatcher != null
                            && clientDispatcher.getRoot().getChild(cleanCmd.split(" ")[0]) != null) {
                        clientDispatcher.execute(cleanCmd, (FabricClientCommandSource) mc.player);
                    } else if (mc.player.connection != null) {
                        // Fallback to server
                        mc.player.connection.sendCommand(cleanCmd);
                    } else {
                        mc.player.displayClientMessage(Component.literal("§c[Bombo] Failed to execute: /" + cleanCmd),
                                false);
                    }
                } catch (Exception e) {
                    if (mc.player.connection != null)
                        mc.player.connection.sendCommand(cleanCmd);
                }
            }
        });
    }

    public static void registerAlias(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher,
            String alias) {
        try {
            dispatcher.register(ClientCommandManager.literal(alias)
                    .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                            .executes(context -> {
                                String actualCmd = BomboConfig.get().commandAliases.get(alias);
                                if (actualCmd != null) {
                                    String args = StringArgumentType.getString(context, "args");
                                    executeTracked(actualCmd + " " + args);
                                } else {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null && mc.player.connection != null) {
                                        String args = StringArgumentType.getString(context, "args");
                                        mc.player.connection.sendCommand(alias + " " + args);
                                    }
                                }
                                return 1;
                            }))
                    .executes(context -> {
                        String actualCmd = BomboConfig.get().commandAliases.get(alias);
                        if (actualCmd != null) {
                            executeTracked(actualCmd);
                        } else {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null && mc.player.connection != null) {
                                mc.player.connection.sendCommand(alias);
                            }
                        }
                        return 1;
                    }));
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("Failed to register alias command: " + alias, e);
        }
    }

    public static void registerAliasToDispatcher(
            com.mojang.brigadier.CommandDispatcher<ClientSuggestionProvider> dispatcher, String alias) {
        try {
            dispatcher.register(
                    com.mojang.brigadier.builder.LiteralArgumentBuilder.<ClientSuggestionProvider>literal(alias)
                            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                    .<ClientSuggestionProvider, String>argument("args",
                                            com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String actualCmd = BomboConfig.get().commandAliases.get(alias);
                                        if (actualCmd != null) {
                                            String args = com.mojang.brigadier.arguments.StringArgumentType
                                                    .getString(context, "args");
                                            executeTracked(actualCmd + " " + args);
                                        }
                                        return 1;
                                    }))
                            .executes(context -> {
                                String actualCmd = BomboConfig.get().commandAliases.get(alias);
                                if (actualCmd != null) {
                                    executeTracked(actualCmd);
                                }
                                return 1;
                            }));
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("Failed to register alias to dispatcher: " + alias, e);
        }
    }

    public static void registerAllAliases() {
        if (clientDispatcher == null)
            return;
        for (String alias : BomboConfig.get().commandAliases.keySet()) {
            registerAlias(clientDispatcher, alias);
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
                com.mojang.brigadier.CommandDispatcher<ClientSuggestionProvider> connectionDispatcher = mc.player.connection
                        .getCommands();
                if (connectionDispatcher != null) {
                    for (String alias : BomboConfig.get().commandAliases.keySet()) {
                        registerAliasToDispatcher(connectionDispatcher, alias);
                    }
                }
            }
        } catch (Throwable t) {
            Bomboaddons.LOGGER.error("Failed to register aliases to connection dispatcher", t);
        }
    }

    public static void processChatMessage(String rawMessage) {
        if (rawMessage == null)
            return;
        String cleanMessage = rawMessage.replaceAll("§.", "").trim().toLowerCase();
        if (cleanMessage.contains("[boss] storm: energy heed my call!") || 
            cleanMessage.contains("[boss] storm: thunder let me be your catalyst!")) {
            DungeonPadTimers.onBossMessage();
        }
        if (cleanMessage.startsWith("co-op >") && cleanMessage.contains("noreconnect")) {
            tempDisableReconnect = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component
                        .literal("§8[§bBomboAddons§8] §cAuto-reconnect delayed to 5 minutes by co-op message!"), false);
            }
        }
        List<BomboConfig.ChatTrigger> activeTriggers = BomboConfig.get().profileChatTriggers
                .get(BomboConfig.get().activeProfile);
        List<BomboConfig.ChatTrigger> generalTriggers = BomboConfig.get().profileChatTriggers.get("General");
        List<BomboConfig.ChatTrigger> allTriggers = new ArrayList<>();
        if (activeTriggers != null)
            allTriggers.addAll(activeTriggers);
        if (generalTriggers != null && !BomboConfig.get().activeProfile.equals("General")) {
            allTriggers.addAll(generalTriggers);
        }
        for (BomboConfig.ChatTrigger trigger : allTriggers) {
            if (trigger.enabled && trigger.triggerText != null && !trigger.triggerText.isEmpty()) {
                String cleanTrigger = trigger.triggerText.replaceAll("§.", "").trim().toLowerCase();
                if (!cleanTrigger.isEmpty() && cleanMessage.contains(cleanTrigger)) {
                    if (trigger.commandToRun != null && !trigger.commandToRun.isEmpty()) {
                        executeTracked(trigger.commandToRun);
                    }
                    if (trigger.titleToShow != null && !trigger.titleToShow.isEmpty()) {
                        Minecraft mc = Minecraft.getInstance();
                        String formattedTitle = trigger.titleToShow.replace('&', '§');
                        mc.execute(() -> {
                            mc.gui.setTimes(10, 70, 20);
                            mc.gui.setTitle(Component.literal(formattedTitle));
                        });
                    }
                }
            }
        }
    }

    public static Map<String, Integer> getEnchantments(ItemStack stack) {
        return AutoCombine.getEnchantments(stack);
    }

    private static String cleanName(String name) {
        return name.trim().replaceAll("(?i)§[0-9a-fk-or]", "");
    }

    public static void reconnect(net.minecraft.client.gui.screens.Screen parentScreen, Minecraft mc) {
        net.minecraft.client.multiplayer.ServerData server = lastServerData;
        if (server != null) {
            net.minecraft.client.multiplayer.resolver.ServerAddress address = net.minecraft.client.multiplayer.resolver.ServerAddress
                    .parseString(server.ip);
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                    parentScreen, mc, address, server, false, null);
        }
    }

    public static void triggerLocraw() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null && SkyblockUtils.isConnectedToHypixel()) {
            expectingLocrawCount++;
            lastLocrawTime = System.currentTimeMillis();
            try {
                mc.player.connection.sendCommand("locraw");
            } catch (Exception e) {
                expectingLocrawCount = Math.max(0, expectingLocrawCount - 1);
            }
        }
    }

    private static net.minecraft.network.chat.MutableComponent createHelpLine(String command, String suggestion,
            String description) {
        return Component.literal("§b" + command)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.SuggestCommand(suggestion))
                        .withHoverEvent(SBECommands.createHoverEvent("§b" + suggestion + "\n\n§7" + description)));
    }

    public static List<String> splitCommands(String input) {
        List<String> result = new ArrayList<>();
        if (input == null || input.trim().isEmpty())
            return result;
        if (input.contains("/")) {
            String[] parts = input.split("\\s+(?=/)");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        } else {
            String[] parts = input.split("\\s+");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private static void showRankCommand(FabricClientCommandSource source, String username) {
        source.sendFeedback(Component.literal(PREFIX + "§7Checking rank for §e" + username + "§7..."));
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URI("https://sbecommands-api.icarusphantom.dev/v1/sbecommands/nw/" + username).toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int status = conn.getResponseCode();
                if (status == 200) {
                    try (java.io.InputStreamReader reader = new java.io.InputStreamReader(conn.getInputStream(), "UTF-8")) {
                        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj.has("data")) {
                            com.google.gson.JsonObject data = obj.getAsJsonObject("data");
                            if (data.has("rank")) {
                                String rank = data.get("rank").getAsString();
                                if (rank != null) {
                                    RankCache.setRank(username, rank);
                                    Minecraft.getInstance().execute(() -> {
                                        source.sendFeedback(Component.literal(PREFIX + "§7API Rank for §e" + username + "§7: " + (rank.isEmpty() ? "§7None" : rank)));
                                    });
                                    return;
                                }
                            }
                        }
                    }
                }
                Minecraft.getInstance().execute(() -> {
                    source.sendFeedback(Component.literal(PREFIX + "§cFailed to fetch rank for §e" + username + " §7(Status: " + status + ")"));
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    source.sendFeedback(Component.literal(PREFIX + "§cError fetching rank for §e" + username + "§c: " + e.getMessage()));
                });
            }
        }, "Rank-Fetch-Command-" + username).start();
    }
}
