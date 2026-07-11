package me.bombo.bomboaddons;

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
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
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

    private static final String PREFIX = "§8[§3Bombo§8]§r ";
    private static boolean openGuiNextTick = false;
    private static int lastInventoryStateId = -1;
    private static boolean openHudMoveNextTick = false;
    private static boolean openCustomizeGuiNextTick = false;
    public static com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> clientDispatcher;
    public static String currentArea = "None";
    public static String currentSubArea = "None";
    public static String currentHypixelChannel = "a";
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
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            net.minecraft.core.BlockPos pos = hitResult.getBlockPos();
            if (me.bombo.bomboaddons.BlockHighlight.targetChestPos != null && pos.equals(me.bombo.bomboaddons.BlockHighlight.targetChestPos)) {
                me.bombo.bomboaddons.BlockHighlight.targetChestPos = null;
            }
            
            // Normalize double chest coordinates so both halves save to the same data
            net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                net.minecraft.world.level.block.state.properties.ChestType type = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.CHEST_TYPE);
                if (type != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                    net.minecraft.core.Direction connectedDir = net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state);
                    net.minecraft.core.BlockPos otherPos = pos.relative(connectedDir);
                    // use the minimum of the two positions
                    if (otherPos.compareTo(pos) < 0) {
                        pos = otherPos;
                    }
                } else {
                    for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        net.minecraft.core.BlockPos adj = pos.relative(d);
                        if (world.getBlockState(adj).getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                            if (adj.compareTo(pos) < 0) pos = adj;
                            break;
                        }
                    }
                }
            }
            
            me.bombo.bomboaddons.features.StorageTracker.lastClickedBlockPos = pos;
            return net.minecraft.world.InteractionResult.PASS;
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            me.bombo.bomboaddons.features.StorageTracker.save();
        });

        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenMouseEvents.allowMouseClick(screen).register((screen1, event) -> {
                double mouseX = event.x();
                double mouseY = event.y();
                int button = event.button();
                BomboConfig.Settings s = BomboConfig.get();
                if (s.diceTracker && DiceTracker.shouldShowHud()) {
                    int w = (int) (260 * s.diceHudScale);
                    int h = (int) (52 * s.diceHudScale);
                    if (mouseX >= s.diceHudX && mouseX <= s.diceHudX + w && mouseY >= s.diceHudY && mouseY <= s.diceHudY + h) {
                        if (button == 0) {
                            s.diceDisplayMode = s.diceDisplayMode.equals("Lifetime") ? "Current" : "Lifetime";
                            BomboConfig.save();
                            return false;
                        }
                    }
                }
                return true;
            });
        });
        me.bombo.bomboaddons.auth.AccountManager.init();
        me.bombo.bomboaddons.features.StorageTracker.init();
        me.bombo.bomboaddons.features.TextureToggleManager.INSTANCE.init();
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.UncaughtExceptionHandler customHandler = (thread, throwable) -> {
            try {
                java.io.File file = new java.io.File("crash_exception.log");
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file, true))) {
                    pw.println("=== UNCAUGHT EXCEPTION ===");
                    pw.println("Thread: " + thread.getName());
                    throwable.printStackTrace(pw);
                    pw.println("==========================");
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(customHandler);
        Thread.currentThread().setUncaughtExceptionHandler(customHandler);

        try {
            NEUDownloader.checkAndDownloadAsync();
            SkyblockItemManager.ensureLoaded();
            System.out.println("=== ServerData.Type Enum Constants ===");
            for (Object obj : Class.forName("net.minecraft.client.multiplayer.ServerData$Type").getEnumConstants()) {
                System.out.println("Enum constant: " + obj);
            }
            System.out.println("======================================");
        } catch (Throwable t) {
            System.err.println("[BomboAddons] Error triggering SkyblockItemManager:");
            t.printStackTrace();
        }

        Bomboaddons.sendMessageConsumer = (message) -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal(message.replace("&", "§")));
                }
            });
        };
        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                clientDispatcher = dispatcher;
                registerMultiPlayerPartyCommands(dispatcher);

                dispatcher.register(ClientCommands.literal("bombo_highlight_slot")
                    .then(ClientCommands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .then(ClientCommands.argument("command", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(context -> {
                                int slot = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "slot");
                                String cmd = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "command");
                                me.bombo.bomboaddons.SlotHighlight.setTargetSlot(slot, 0xAA00FF00);
                                String toSend = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                                Minecraft.getInstance().player.connection.sendCommand(toSend);
                                return 1;
                            })
                        )
                    )
                );

                // --- Hoppity Egg Finder commands ---
                try {
                    dispatcher.register(ClientCommands.literal("skyblocker")
                            .then(ClientCommands.literal("eggFinder")
                                    .then(ClientCommands.literal("shareLocation")
                                            .then(ClientCommands.argument("eggType", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String typeStr = StringArgumentType.getString(context,
                                                                "eggType");
                                                        me.bombo.bomboaddons.eggfinder.EggFinder.EggType type = me.bombo.bomboaddons.eggfinder.EggFinder.EggType
                                                                .getTypeByName(typeStr);
                                                        if (type == null) {
                                                            context.getSource()
                                                                    .sendError(Component.literal(
                                                                            "§8[§bBomboAddons§8] §cInvalid egg type: "
                                                                                    + typeStr));
                                                            return 1;
                                                        }
                                                        List<me.bombo.bomboaddons.eggfinder.EggFinder.EggWaypoint> wps = me.bombo.bomboaddons.eggfinder.EggFinder
                                                                .getActiveWaypoints();
                                                        me.bombo.bomboaddons.eggfinder.EggFinder.EggWaypoint targetWp = null;
                                                        for (var wp : wps) {
                                                            if (wp.type == type) {
                                                                targetWp = wp;
                                                                break;
                                                            }
                                                        }
                                                        if (targetWp == null) {
                                                            context.getSource().sendError(Component.literal(
                                                                    "§8[§bBomboAddons§8] §cUnable to share egg location - not found."));
                                                            return 1;
                                                        }
                                                        String chatMsg = "[Skyblocker] Chocolate " + type.name
                                                                + " Egg found at " + targetWp.pos.getX() + ", "
                                                                + targetWp.pos.getY() + ", " + targetWp.pos.getZ();
                                                        Minecraft.getInstance().player.connection.sendChat(chatMsg);
                                                        return 1;
                                                    })))
                                    .then(ClientCommands.literal("sharelocation")
                                            .then(ClientCommands.argument("eggType", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String typeStr = StringArgumentType.getString(context,
                                                                "eggType");
                                                        me.bombo.bomboaddons.eggfinder.EggFinder.EggType type = me.bombo.bomboaddons.eggfinder.EggFinder.EggType
                                                                .getTypeByName(typeStr);
                                                        if (type == null) {
                                                            context.getSource()
                                                                    .sendError(Component.literal(
                                                                            "§8[§bBomboAddons§8] §cInvalid egg type: "
                                                                                    + typeStr));
                                                            return 1;
                                                        }
                                                        List<me.bombo.bomboaddons.eggfinder.EggFinder.EggWaypoint> wps = me.bombo.bomboaddons.eggfinder.EggFinder
                                                                .getActiveWaypoints();
                                                        me.bombo.bomboaddons.eggfinder.EggFinder.EggWaypoint targetWp = null;
                                                        for (var wp : wps) {
                                                            if (wp.type == type) {
                                                                targetWp = wp;
                                                                break;
                                                            }
                                                        }
                                                        if (targetWp == null) {
                                                            context.getSource().sendError(Component.literal(
                                                                    "§8[§bBomboAddons§8] §cUnable to share egg location - not found."));
                                                            return 1;
                                                        }
                                                        String chatMsg = "[Skyblocker] Chocolate " + type.name
                                                                + " Egg found at " + targetWp.pos.getX() + ", "
                                                                + targetWp.pos.getY() + ", " + targetWp.pos.getZ();
                                                        Minecraft.getInstance().player.connection.sendChat(chatMsg);
                                                        return 1;
                                                    })))
                                    .then(ClientCommands.literal("status")
                                            .executes(context -> {
                                                boolean connected = me.bombo.bomboaddons.eggfinder.EggWebSocket
                                                        .isConnected();
                                                boolean connecting = me.bombo.bomboaddons.eggfinder.EggWebSocket
                                                        .isConnecting();
                                                String sub = me.bombo.bomboaddons.eggfinder.EggWebSocket
                                                        .getActiveSubscription();
                                                String statusColor = connected ? "§aConnected"
                                                        : (connecting ? "§eConnecting..." : "§cDisconnected");
                                                context.getSource()
                                                        .sendFeedback(Component.literal(
                                                                "§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: "
                                                                        + statusColor));
                                                context.getSource()
                                                        .sendFeedback(Component.literal(
                                                                "§8[§bBomboAddons§8] §7Active Subscription Area: §e"
                                                                        + (sub != null ? sub : "None")));
                                                return 1;
                                            }))
                                    .then(ClientCommands.literal("reconnect")
                                            .executes(context -> {
                                                context.getSource().sendFeedback(Component.literal(
                                                        "§8[§bBomboAddons§8] §eRe-authenticating and reconnecting to Egg Finder WebSocket..."));
                                                me.bombo.bomboaddons.eggfinder.EggAuth.forceUpdateToken();
                                                me.bombo.bomboaddons.eggfinder.EggWebSocket.forceReconnect();
                                                return 1;
                                            })))
                            .then(ClientCommands.literal("eggfinder")
                                    .then(ClientCommands.literal("shareLocation")
                                            .then(ClientCommands.argument("eggType", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String typeStr = StringArgumentType.getString(context,
                                                                "eggType");
                                                        me.bombo.bomboaddons.eggfinder.EggFinder.EggType type = me.bombo.bomboaddons.eggfinder.EggFinder.EggType
                                                                .getTypeByName(typeStr);
                                                        if (type == null) {
                                                            context.getSource()
                                                                    .sendError(Component.literal(
                                                                            "§8[§bBomboAddons§8] §cInvalid egg type: "
                                                                                    + typeStr));
                                                            return 1;
                                                        }
                                                        List<me.bombo.bomboaddons.eggfinder.EggFinder.EggWaypoint> wps = me.bombo.bomboaddons.eggfinder.EggFinder
                                                                .getActiveWaypoints();
                                                        me.bombo.bomboaddons.eggfinder.EggFinder.EggWaypoint targetWp = null;
                                                        for (var wp : wps) {
                                                            if (wp.type == type) {
                                                                targetWp = wp;
                                                                break;
                                                            }
                                                        }
                                                        if (targetWp == null) {
                                                            context.getSource().sendError(Component.literal(
                                                                    "§8[§bBomboAddons§8] §cUnable to share egg location - not found."));
                                                            return 1;
                                                        }
                                                        String chatMsg = "[Skyblocker] Chocolate " + type.name
                                                                + " Egg found at " + targetWp.pos.getX() + ", "
                                                                + targetWp.pos.getY() + ", " + targetWp.pos.getZ();
                                                        Minecraft.getInstance().player.connection.sendChat(chatMsg);
                                                        return 1;
                                                    })))
                                    .then(ClientCommands.literal("sharelocation")
                                            .then(ClientCommands.argument("eggType", StringArgumentType.word())
                                                    .executes(context -> {
                                                        String typeStr = StringArgumentType.getString(context,
                                                                "eggType");
                                                        me.bombo.bomboaddons.eggfinder.EggFinder.EggType type = me.bombo.bomboaddons.eggfinder.EggFinder.EggType
                                                                .getTypeByName(typeStr);
                                                        if (type == null) {
                                                            context.getSource()
                                                                    .sendError(Component.literal(
                                                                            "§8[§bBomboAddons§8] §cInvalid egg type: "
                                                                                    + typeStr));
                                                            return 1;
                                                        }
                                                        List<me.bombo.bomboaddons.eggfinder.EggFinder.EggWaypoint> wps = me.bombo.bomboaddons.eggfinder.EggFinder
                                                                .getActiveWaypoints();
                                                        me.bombo.bomboaddons.eggfinder.EggFinder.EggWaypoint targetWp = null;
                                                        for (var wp : wps) {
                                                            if (wp.type == type) {
                                                                targetWp = wp;
                                                                break;
                                                            }
                                                        }
                                                        if (targetWp == null) {
                                                            context.getSource().sendError(Component.literal(
                                                                    "§8[§bBomboAddons§8] §cUnable to share egg location - not found."));
                                                            return 1;
                                                        }
                                                        String chatMsg = "[Skyblocker] Chocolate " + type.name
                                                                + " Egg found at " + targetWp.pos.getX() + ", "
                                                                + targetWp.pos.getY() + ", " + targetWp.pos.getZ();
                                                        Minecraft.getInstance().player.connection.sendChat(chatMsg);
                                                        return 1;
                                                    })))
                                    .then(ClientCommands.literal("status")
                                            .executes(context -> {
                                                boolean connected = me.bombo.bomboaddons.eggfinder.EggWebSocket
                                                        .isConnected();
                                                boolean connecting = me.bombo.bomboaddons.eggfinder.EggWebSocket
                                                        .isConnecting();
                                                String sub = me.bombo.bomboaddons.eggfinder.EggWebSocket
                                                        .getActiveSubscription();
                                                String statusColor = connected ? "§aConnected"
                                                        : (connecting ? "§eConnecting..." : "§cDisconnected");
                                                context.getSource()
                                                        .sendFeedback(Component.literal(
                                                                "§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: "
                                                                        + statusColor));
                                                context.getSource()
                                                        .sendFeedback(Component.literal(
                                                                "§8[§bBomboAddons§8] §7Active Subscription Area: §e"
                                                                        + (sub != null ? sub : "None")));
                                                return 1;
                                            }))
                                    .then(ClientCommands.literal("reconnect")
                                            .executes(context -> {
                                                context.getSource().sendFeedback(Component.literal(
                                                        "§8[§bBomboAddons§8] §eRe-authenticating and reconnecting to Egg Finder WebSocket..."));
                                                me.bombo.bomboaddons.eggfinder.EggAuth.forceUpdateToken();
                                                me.bombo.bomboaddons.eggfinder.EggWebSocket.forceReconnect();
                                                return 1;
                                            }))));

                    // Register direct /eggfinder command
                    dispatcher.register(ClientCommands.literal("eggfinder")
                            .then(ClientCommands.literal("status")
                                    .executes(context -> {
                                        boolean connected = me.bombo.bomboaddons.eggfinder.EggWebSocket.isConnected();
                                        boolean connecting = me.bombo.bomboaddons.eggfinder.EggWebSocket.isConnecting();
                                        String sub = me.bombo.bomboaddons.eggfinder.EggWebSocket
                                                .getActiveSubscription();
                                        String statusColor = connected ? "§aConnected"
                                                : (connecting ? "§eConnecting..." : "§cDisconnected");
                                        context.getSource().sendFeedback(Component.literal(
                                                "§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: " + statusColor));
                                        context.getSource().sendFeedback(
                                                Component.literal("§8[§bBomboAddons§8] §7Active Subscription Area: §e"
                                                        + (sub != null ? sub : "None")));
                                        return 1;
                                    }))
                            .then(ClientCommands.literal("reconnect")
                                    .executes(context -> {
                                        context.getSource().sendFeedback(Component.literal(
                                                "§8[§bBomboAddons§8] §eRe-authenticating and reconnecting to Egg Finder WebSocket..."));
                                        me.bombo.bomboaddons.eggfinder.EggAuth.forceUpdateToken();
                                        me.bombo.bomboaddons.eggfinder.EggWebSocket.forceReconnect();
                                        return 1;
                                    })));

                    // Register direct /eggFinder command
                    dispatcher.register(ClientCommands.literal("eggFinder")
                            .then(ClientCommands.literal("status")
                                    .executes(context -> {
                                        boolean connected = me.bombo.bomboaddons.eggfinder.EggWebSocket.isConnected();
                                        boolean connecting = me.bombo.bomboaddons.eggfinder.EggWebSocket.isConnecting();
                                        String sub = me.bombo.bomboaddons.eggfinder.EggWebSocket
                                                .getActiveSubscription();
                                        String statusColor = connected ? "§aConnected"
                                                : (connecting ? "§eConnecting..." : "§cDisconnected");
                                        context.getSource().sendFeedback(Component.literal(
                                                "§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: " + statusColor));
                                        context.getSource().sendFeedback(
                                                Component.literal("§8[§bBomboAddons§8] §7Active Subscription Area: §e"
                                                        + (sub != null ? sub : "None")));
                                        return 1;
                                    }))
                            .then(ClientCommands.literal("reconnect")
                                    .executes(context -> {
                                        context.getSource().sendFeedback(Component.literal(
                                                "§8[§bBomboAddons§8] §eRe-authenticating and reconnecting to Egg Finder WebSocket..."));
                                        me.bombo.bomboaddons.eggfinder.EggAuth.forceUpdateToken();
                                        me.bombo.bomboaddons.eggfinder.EggWebSocket.forceReconnect();
                                        return 1;
                                    })));
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] Failed to register skyblocker eggFinder commands!", t);
                }

                registerAllAliases();

                // --- PRIORITY 1: /click and /clicks ---
                try {
                    dispatcher.register(ClientCommands.literal("clicks")
                            .executes(context -> {
                                ClickLogic.listTargets(context.getSource());
                                return 1;
                            }));
                    dispatcher.register(ClientCommands.literal("click")
                            .then(ClientCommands.literal("list")
                                    .executes(context -> {
                                        ClickLogic.listTargets(context.getSource());
                                        return 1;
                                    }))
                            .then(ClientCommands.literal("debug")
                                    .executes(context -> {
                                        ClickLogic.toggleDebug();
                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§7Click Debug: "
                                                + (ClickLogic.isDebugMode() ? "§aON" : "§cOFF")));
                                        return 1;
                                    }))
                            .then(ClientCommands.literal("add")
                                    .then(ClientCommands.argument("item", StringArgumentType.string())
                                            .then(ClientCommands.argument("gui", StringArgumentType.string())
                                                    .then(ClientCommands
                                                            .argument("key", StringArgumentType.string())
                                                            .then(ClientCommands
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
                                                                    .then(ClientCommands.argument("auto",
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
                            .then(ClientCommands.literal("remove")
                                    .then(ClientCommands.argument("id", StringArgumentType.string())
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

                // --- Custom Timer commands ---
                try {
                    dispatcher.register(ClientCommands.literal("timer")
                            .executes(context -> {
                                context.getSource().sendFeedback(Component.literal(
                                        "§8[§bBomboAddons§8] §7Usage: /timer <duration> OR /timer <name> <duration>"));
                                return 1;
                            })
                            .then(ClientCommands.argument("arg1", StringArgumentType.word())
                                    .executes(context -> {
                                        String arg1 = StringArgumentType.getString(context, "arg1");
                                        long durationMs = CustomTimerManager.parseTimeMs(arg1);
                                        if (durationMs > 0) {
                                            CustomTimerManager.startTimer("Timer", durationMs);
                                            context.getSource()
                                                    .sendFeedback(Component.literal(
                                                            "§8[§bBomboAddons§8] §7Started default timer for §e" + arg1
                                                                    + "§7."));
                                        } else {
                                            context.getSource().sendError(Component
                                                    .literal("§8[§bBomboAddons§8] §cInvalid duration format: " + arg1));
                                        }
                                        return 1;
                                    })
                                    .then(ClientCommands.argument("arg2", StringArgumentType.word())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "arg1");
                                                String durationStr = StringArgumentType.getString(context, "arg2");
                                                long durationMs = CustomTimerManager.parseTimeMs(durationStr);
                                                if (durationMs > 0) {
                                                    CustomTimerManager.startTimer(name, durationMs);
                                                    context.getSource().sendFeedback(
                                                            Component.literal("§8[§bBomboAddons§8] §7Started timer '§e"
                                                                    + name + "§7' for §e" + durationStr + "§7."));
                                                } else {
                                                    context.getSource()
                                                            .sendError(Component.literal(
                                                                    "§8[§bBomboAddons§8] §cInvalid duration format: "
                                                                            + durationStr));
                                                }
                                                return 1;
                                            }))));
                } catch (Throwable t) {
                    Bomboaddons.LOGGER.error("[BomboAddons] Failed to register timer command!", t);
                }

                // --- PRIORITY 1: Core Search ---
                try {
                    dispatcher.register(ClientCommands.literal("lf")
                            .executes(context -> {
                                System.out.println("[Bombo] Executing /lf (help)");
                                context.getSource()
                                        .sendFeedback(Component.literal(PREFIX + "§7Usage: /lf <username> [query]"));
                                return 1;
                            })
                            .then(ClientCommands.argument("username", StringArgumentType.string())
                                    .suggests((ctx, sb) -> TabCompletionManager.getUsernameSuggestions(ctx, sb))
                                    .executes(context -> {
                                        String user = StringArgumentType.getString(context, "username");
                                        System.out.println("[Bombo] Executing /lf for user: " + user);
                                        LF.show(user, "", false);
                                        return 1;
                                    })
                                    .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String user = StringArgumentType.getString(context, "username");
                                                String query = StringArgumentType.getString(context, "query");
                                                System.out.println("[Bombo] Executing /lf for user: " + user
                                                        + " with query: " + query);
                                                LF.show(user, query, false);
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommands.literal("lfc")
                            .executes(context -> {
                                System.out.println("[Bombo] Executing /lfc (help)");
                                context.getSource()
                                        .sendFeedback(Component.literal(PREFIX + "§7Usage: /lfc <username> [query]"));
                                return 1;
                            })
                            .then(ClientCommands.argument("username", StringArgumentType.string())
                                    .suggests((ctx, sb) -> TabCompletionManager.getUsernameSuggestions(ctx, sb))
                                    .executes(context -> {
                                        String user = StringArgumentType.getString(context, "username");
                                        System.out.println("[Bombo] Executing /lfc for user: " + user);
                                        LF.show(user, "", true);
                                        return 1;
                                    })
                                    .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String user = StringArgumentType.getString(context, "username");
                                                String query = StringArgumentType.getString(context, "query");
                                                System.out.println("[Bombo] Executing /lfc for user: " + user
                                                        + " with query: " + query);
                                                LF.show(user, query, true);
                                                return 1;
                                            }))));

                    // --- SBE Commands (Top-Level with Auto-Completions) ---
                    String[] sbeSubs = { "nw", "cata", "skills", "slayer", "trophyfish", "crimson", "crimsom" };
                    for (String s : sbeSubs) {
                        final String commandName = s.equals("crimsom") ? "crimson" : s;
                        dispatcher.register(ClientCommands.literal(s)
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        SBECommands.handleCommand(commandName, mc.player.getName().getString(), null);
                                    }
                                    return 1;
                                })
                                .then(ClientCommands.argument("username", StringArgumentType.string())
                                        .suggests((ctx, sb) -> TabCompletionManager.getUsernameSuggestions(ctx, sb))
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "username");
                                            SBECommands.handleCommand(commandName, name, null);
                                            return 1;
                                        })
                                    .then(ClientCommands.argument("profile", StringArgumentType.word())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "username");
                                                    String profile = StringArgumentType.getString(context, "profile");
                                                    SBECommands.handleCommand(commandName, name, profile);
                                                    return 1;
                                                }))));
                    }


                    dispatcher.register(ClientCommands.literal("lb")
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
                            .then(ClientCommands.argument("query", StringArgumentType.greedyString())
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
                    dispatcher.register(ClientCommands.literal("bitem")
                            .executes(context -> {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc.player == null)
                                    return 1;
                                ItemStack stack = mc.player.getMainHandItem();
                                if (stack.isEmpty()) {
                                    context.getSource()
                                            .sendFeedback(Component.literal("§c[Bombo] You must hold an item."));
                                    return 1;
                                }

                                net.minecraft.world.item.Item originalItem = stack.getItem();

                                String skyblockId = SkyblockUtils.getInternalIdRaw(stack);
                                context.getSource().sendFeedback(Component.literal("§6=== Item Debug ==="));
                                context.getSource().sendFeedback(
                                        Component.literal("§7Name: §f" + stack.getHoverName().getString()));
                                context.getSource().sendFeedback(Component
                                        .literal("§7SkyBlock ID: §e" + (skyblockId.isEmpty() ? "None" : skyblockId)));
                                context.getSource()
                                        .sendFeedback(Component.literal("§7Vanilla Item: §c"
                                                + net.minecraft.core.registries.BuiltInRegistries.ITEM
                                                        .getKey(originalItem).toString()));

                                if (!skyblockId.isEmpty()) {
                                    SkyblockItemManager.SkyblockItemInfo info = SkyblockItemManager.getInfo(skyblockId);
                                    if (info != null) {
                                        context.getSource().sendFeedback(
                                                Component.literal("§7Expected Material (API): §a" + info.material));
                                        net.minecraft.world.item.Item overrideItem = SkyblockItemManager
                                                .getOverrideItem(info.material);
                                        if (overrideItem != null) {
                                            context.getSource()
                                                    .sendFeedback(Component.literal("§7Overridden Item: §b"
                                                            + net.minecraft.core.registries.BuiltInRegistries.ITEM
                                                                    .getKey(overrideItem).toString()));
                                        } else {
                                            context.getSource().sendFeedback(
                                                    Component.literal("§7Overridden Item: §cNone (Failed to resolve)"));
                                        }
                                        if (info.skinValue != null) {
                                            context.getSource().sendFeedback(
                                                    Component.literal("§7Skin Value: §d" + (info.skinValue.length() > 20
                                                            ? info.skinValue.substring(0, 20) + "..."
                                                            : info.skinValue)));
                                        }
                                    } else {
                                        context.getSource().sendFeedback(Component
                                                .literal("§7Expected Material (API): §cNot found in database"));
                                    }
                                }
                                context.getSource().sendFeedback(Component.literal("§6=================="));
                                return 1;
                            }));
                } catch (Throwable t) {
                    System.err.println("[Bombo] FAILED to register core search commands!");
                    t.printStackTrace();
                }

                // --- PRIORITY 1.5: /server command ---
                try {
                    dispatcher.register(ClientCommands.literal("server")
                            .then(ClientCommands.argument("ip", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String ip = StringArgumentType.getString(context, "ip");
                                        Minecraft mc = Minecraft.getInstance();
                                        mc.execute(() -> {
                                            if (mc.getConnection() != null) {
                                                mc.getConnection().getConnection()
                                                        .disconnect(Component.literal("Connecting to " + ip));
                                            }
                                            net.minecraft.client.multiplayer.resolver.ServerAddress address = net.minecraft.client.multiplayer.resolver.ServerAddress
                                                    .parseString(ip);
                                            net.minecraft.client.multiplayer.ServerData server = new net.minecraft.client.multiplayer.ServerData(
                                                    "Server", ip,
                                                    net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                                            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                                                    new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(
                                                            new net.minecraft.client.gui.screens.TitleScreen()),
                                                    mc, address, server, false, null);
                                        });
                                        return 1;
                                    })));

                    dispatcher.register(ClientCommands.literal("afk")
                            .executes(ctx -> {
                                AFKManager.toggleAfk(null);
                                return 1;
                            })
                            .then(ClientCommands.argument("island", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String island = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "island");
                                    AFKManager.toggleAfk(island);
                                    return 1;
                                })
                            ));

                } catch (Throwable t) {
                    System.err.println("[Bombo] FAILED to register server command!");
                    t.printStackTrace();
                }

                // --- PRIORITY 2: /b, /ba, /bombo and subcommands ---
                try {
                    LiteralArgumentBuilder<FabricClientCommandSource> bBuilder = ClientCommands.literal("b");
                    LiteralArgumentBuilder<FabricClientCommandSource> baBuilder = ClientCommands
                            .literal("bomboaddons");
                    LiteralArgumentBuilder<FabricClientCommandSource> bomboBuilder = ClientCommands
                            .literal("bombo");

                    java.util.function.Consumer<LiteralArgumentBuilder<FabricClientCommandSource>> setupCommands = builder -> {
                        builder.executes(context -> {
                            openGuiNextTick = true;
                            return 1;
                        });

                        builder.then(ClientCommands.literal("help").executes(context -> {
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
                                    createHelpLine("/b bw", "/b bw", "Opens config GUI directly to Bedwars settings.")
                                            .append(Component.literal(" §7- Opens Bedwars ESP settings")));
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
                                    createHelpLine("/b online", "/b online",
                                            "Shows who is online with the mod and their version.")
                                            .append(Component.literal(" §7- Shows online mod users")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b color", "/b color",
                                            "Shows Minecraft text color and formatting codes.")
                                            .append(Component.literal(" §7- Shows text color codes")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b time", "/b time", "Opens config GUI directly to Time Changer settings.")
                                            .append(Component.literal(" §7- Opens Time Changer Settings")));
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
                            context.getSource().sendFeedback(
                                    createHelpLine("/b play <ip>", "/b play ",
                                            "Safely disconnects and connects to a server.")
                                            .append(Component.literal(" §7- Connects to a server")));
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
                                    createHelpLine("/b rank [name]", "/b rank",
                                            "Fetches and displays the Hypixel rank of a player.")
                                            .append(Component.literal(" §7- Fetches and displays player rank")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b chat", "/b chat", "Toggles the global IRC mod chat.")
                                            .append(Component.literal(" §7- Toggles IRC chat")));
                            context.getSource().sendFeedback(
                                    createHelpLine("/b custom", "/b custom",
                                            "Customizes the material and name of the held item.")
                                            .append(Component.literal(" §7- Customizes the held item")));

                            context.getSource().sendFeedback(
                                    Component.literal("§8---------------------------------------------------------"));
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("play")
                                .then(ClientCommands.argument("ip", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String ip = StringArgumentType.getString(context, "ip");
                                            Minecraft mc = Minecraft.getInstance();
                                            mc.execute(() -> {
                                                if (mc.getConnection() != null) {
                                                    mc.getConnection().getConnection()
                                                            .disconnect(Component.literal("Connecting to " + ip));
                                                }
                                                net.minecraft.client.multiplayer.resolver.ServerAddress address = net.minecraft.client.multiplayer.resolver.ServerAddress
                                                        .parseString(ip);
                                                net.minecraft.client.multiplayer.ServerData server = new net.minecraft.client.multiplayer.ServerData(
                                                        "Server", ip,
                                                        net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                                                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                                                        new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(
                                                                new net.minecraft.client.gui.screens.TitleScreen()),
                                                        mc, address, server, false, null);
                                            });
                                            return 1;
                                        })));

                        builder.then(ClientCommands.literal("prof").executes(context -> {
                            BomboConfigGUI.selectedCategory = 5;
                            openGuiNextTick = true;
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("bw").executes(context -> {
                            BomboConfigGUI.selectedCategory = 23;
                            openGuiNextTick = true;
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("time").executes(context -> {
                            BomboConfigGUI.selectedCategory = 1;
                            openGuiNextTick = true;
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("friends").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    PREFIX + "§bFriends in Cache (" + TabCompletionManager.friends.size() + "):"));
                            if (TabCompletionManager.friends.isEmpty()) {
                                context.getSource().sendFeedback(Component.literal("§7- §cNone"));
                            } else {
                                java.util.List<String> sorted = new java.util.ArrayList<>(TabCompletionManager.friends);
                                java.util.Collections.sort(sorted);
                                context.getSource()
                                        .sendFeedback(Component.literal("§7- §e" + String.join(", ", sorted)));
                            }
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("guild").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    PREFIX + "§bGuild Members in Cache (" + TabCompletionManager.guild.size() + "):"));
                            if (TabCompletionManager.guild.isEmpty()) {
                                context.getSource().sendFeedback(Component.literal("§7- §cNone"));
                            } else {
                                java.util.List<String> sorted = new java.util.ArrayList<>(TabCompletionManager.guild);
                                java.util.Collections.sort(sorted);
                                context.getSource()
                                        .sendFeedback(Component.literal("§7- §e" + String.join(", ", sorted)));
                            }
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("party").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    PREFIX + "§bParty Members in Cache (" + TabCompletionManager.party.size() + "):"));
                            if (TabCompletionManager.party.isEmpty()) {
                                context.getSource().sendFeedback(Component.literal("§7- §cNone"));
                            } else {
                                java.util.List<String> sorted = new java.util.ArrayList<>(TabCompletionManager.party);
                                java.util.Collections.sort(sorted);
                                context.getSource()
                                        .sendFeedback(Component.literal("§7- §e" + String.join(", ", sorted)));
                            }
                            return 1;
                        }));

                        // --- Diagnostics ---
                        builder.then(ClientCommands.literal("kick").executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.getConnection() != null) {
                                mc.getConnection().getConnection().disconnect(Component.literal("Kicked via /b kick"));
                            } else {
                                context.getSource().sendFeedback(
                                        Component.literal(PREFIX + "§cNot currently connected to any server!"));
                            }
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("area").executes(context -> {
                            String loc = SkyblockUtils.getLocation();
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§7Current Area: §a" + loc));
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("subarea").executes(context -> {
                            String sub = SkyblockUtils.getSubArea();
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§7Current Subarea: §d" + sub));
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("container").executes(context -> {
                            LF.printContainerInfo();
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("sb").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§6Scoreboard Lines:"));
                            List<String> lines = SkyblockUtils.getSidebarLines(
                                    Minecraft.getInstance().level.getScoreboard(),
                                    Minecraft.getInstance().level.getScoreboard()
                                            .getDisplayObjective(DisplaySlot.SIDEBAR));
                            for (String line : lines)
                                context.getSource().sendFeedback(Component.literal("§7- §r" + line));
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("tab").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(PREFIX + "§bTab List Lines:"));
                            for (Component line : SkyblockUtils.getTabListLines()) {
                                context.getSource().sendFeedback(Component.empty().append("§7- ").append(line));
                            }
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("api").executes(context -> {
                            context.getSource()
                                    .sendFeedback(Component.literal(PREFIX + "§eChecking and Reloading APIs..."));
                            LowestBinManager.reload();
                            context.getSource().sendFeedback(Component.literal(LowestBinManager.getStatus()));
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("wd")
                                .then(ClientCommands.argument("slot", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int slot = IntegerArgumentType.getInteger(context, "slot");
                                            me.bombo.bomboaddons.WardrobeHelper.equip(slot);
                                            return 1;
                                        })));

                        builder.then(ClientCommands.literal("hide").executes(context -> {
                            BomboConfig.Settings s = BomboConfig.get();
                            s.hideCheats = !s.hideCheats;
                            if (s.hideCheats) {
                                if (BomboConfigGUI.selectedCategory == 2 || BomboConfigGUI.selectedCategory == 9 || BomboConfigGUI.selectedCategory == 23) {
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
                            builder.then(ClientCommands.literal(s)
                                    .executes(context -> {
                                        SBECommands.handleCommand(s,
                                                Minecraft.getInstance().player.getName().getString(), null);
                                        return 1;
                                    })
                                    .then(ClientCommands.argument("name", StringArgumentType.word())
                                            .executes(context -> {
                                                SBECommands.handleCommand(s,
                                                        StringArgumentType.getString(context, "name"),
                                                        null);
                                                return 1;
                                            })
                                            .then(ClientCommands.argument("profile", StringArgumentType.word())
                                                    .executes(context -> {
                                                        SBECommands.handleCommand(s,
                                                                StringArgumentType.getString(context, "name"),
                                                                StringArgumentType.getString(context, "profile"));
                                                        return 1;
                                                    }))));
                        }

                        builder.then(ClientCommands.literal("rank")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        String name = mc.player.getName().getString();
                                        showRankCommand(context.getSource(), name);
                                    }
                                    return 1;
                                })
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            showRankCommand(context.getSource(), name);
                                            return 1;
                                        })));

                        builder.then(ClientCommands.literal("chat")
                                .executes(context -> {
                                    try {
                                        BomboConfig.get().ircChatEnabled = !BomboConfig.get().ircChatEnabled;
                                        BomboConfig.save();
                                        IRCClient.onEnabledToggled();
                                        context.getSource().sendFeedback(Component.literal(PREFIX + "§7IRC Chat: "
                                                + (BomboConfig.get().ircChatEnabled ? "§aON" : "§cOFF")));
                                    } catch (Throwable t) {
                                        Bomboaddons.LOGGER.error("[BomboAddons] Error toggling IRC Chat via command",
                                                t);
                                        context.getSource().sendFeedback(Component
                                                .literal(PREFIX + "§cError toggling IRC Chat: " + t.getMessage()));
                                    }
                                    return 1;
                                }));

                        // --- Waypoints ---
                        builder.then(ClientCommands.literal("wp")
                                .then(ClientCommands.literal("import")
                                        .executes(context -> {
                                            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                                            if (clipboard == null || clipboard.trim().isEmpty()) {
                                                context.getSource().sendFeedback(Component.literal(PREFIX + "\u00a7cClipboard is empty!"));
                                                return 1;
                                            }
                                            int imported = GardenWaypoints.importWaypointsFromClipboard(clipboard);
                                            if (imported > 0) {
                                                context.getSource().sendFeedback(Component.literal(PREFIX + "\u00a7aSuccessfully imported " + imported + " waypoints!"));
                                            } else {
                                                context.getSource().sendFeedback(Component.literal(PREFIX + "\u00a7cFailed to parse any waypoints from clipboard. Make sure it's a valid Skyblocker or SkyHanni export."));
                                            }
                                            return 1;
                                        })));

                        // --- Utilities ---
                        builder.then(ClientCommands.literal("ec")
                                .executes(context -> {
                                    executeTracked(CommandTracker.getLastEc());
                                    return 1;
                                }));
                        builder.then(ClientCommands.literal("bp")
                                .executes(context -> {
                                    executeTracked(CommandTracker.getLastBp());
                                    return 1;
                                }));
                        builder.then(ClientCommands.literal("sh")
                                .executes(context -> {
                                    executeTracked(CommandTracker.getLastSh());
                                    return 1;
                                }));

                        // --- Garden ---
                        builder.then(ClientCommands.literal("ks")
                                .executes(context -> {
                                    GardenMovement.onWarpTriggered();
                                    context.getSource().sendFeedback(
                                            Component.literal(PREFIX + "§cGarden Movement Reset! §7(States cleared)"));
                                    return 1;
                                }));
                        builder.then(ClientCommands.literal("sc")
                                .executes(context -> {
                                    BomboConfig.Settings s = BomboConfig.get();
                                    s.gardenSugarCane = !s.gardenSugarCane;
                                    BomboConfig.save();
                                    context.getSource().sendFeedback(Component.literal(
                                            PREFIX + "§7Sugar Cane Mode: " + (s.gardenSugarCane ? "§aON" : "§cOFF")));
                                    return 1;
                                }));
                        builder.then(ClientCommands.literal("sugarcane")
                                .executes(context -> {
                                    BomboConfig.Settings s = BomboConfig.get();
                                    s.gardenSugarCane = !s.gardenSugarCane;
                                    BomboConfig.save();
                                    context.getSource().sendFeedback(Component.literal(
                                            PREFIX + "§7Sugar Cane Mode: " + (s.gardenSugarCane ? "§aON" : "§cOFF")));
                                    return 1;
                                }));
                        builder.then(ClientCommands.literal("test")
                                .executes(context -> {
                                    String version = FabricLoader.getInstance().getModContainer("bomboaddons").get()
                                            .getMetadata().getVersion().getFriendlyString();
                                    context.getSource().sendFeedback(
                                            Component.literal(PREFIX + "§aCurrent Version: §e" + version));
                                    return 1;
                                }));
                        builder.then(ClientCommands.literal("custom")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player == null)
                                        return 1;
                                    ItemStack stack = mc.player.getMainHandItem();
                                    if (stack.isEmpty()) {
                                        context.getSource().sendFeedback(
                                                Component.literal("§c[Bombo] You must hold an item to customize it."));
                                        return 1;
                                    }
                                    openCustomizeGuiNextTick = true;
                                    return 1;
                                }));
                        builder.then(ClientCommands.literal("customize")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player == null)
                                        return 1;
                                    ItemStack stack = mc.player.getMainHandItem();
                                    if (stack.isEmpty()) {
                                        context.getSource().sendFeedback(
                                                Component.literal("§c[Bombo] You must hold an item to customize it."));
                                        return 1;
                                    }
                                    openCustomizeGuiNextTick = true;
                                    return 1;
                                }));

                        builder.then(ClientCommands.literal("wp")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player == null) {
                                        context.getSource().sendFeedback(
                                                Component.literal(PREFIX + "§cPlayer not found!"));
                                        return 0;
                                    }
                                    Vec3 pos = mc.player.position();
                                    String name = "Waypoint";
                                    GardenWaypoints.addWaypoint(pos, name);
                                    context.getSource().sendFeedback(Component.literal(PREFIX
                                            + "§aAdded waypoint '§e" + name + "§a' at your current position: "
                                            + String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z)));
                                    return 1;
                                })
                                .then(ClientCommands.literal("clear")
                                        .executes(context -> {
                                            GardenWaypoints.clear();
                                            context.getSource().sendFeedback(
                                                    Component.literal(PREFIX + "§aAll waypoints cleared!"));
                                            return 1;
                                        }))
                                .then(ClientCommands.argument("x", StringArgumentType.word())
                                        .then(ClientCommands.argument("y", StringArgumentType.word())
                                                .then(ClientCommands.argument("z", StringArgumentType.word())
                                                        .then(ClientCommands
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

                        builder.then(ClientCommands.literal("cycle")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal(PREFIX
                                            + "§cUsage: /b cycle add <name> <commands...>, /b cycle apply <name>, /b cycle remove <name>, or /b cycle list"));
                                    return 1;
                                })
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                                .then(ClientCommands
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
                                .then(ClientCommands.literal("remove")
                                        .then(ClientCommands.argument("name", StringArgumentType.word())
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
                                .then(ClientCommands.literal("list")
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
                                .then(ClientCommands.literal("apply")
                                        .then(ClientCommands.argument("name", StringArgumentType.word())
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

                        // --- Storage Menu ---
                        builder.then(ClientCommands.literal("storage")
                                .executes(context -> {
                                    Minecraft.getInstance().execute(() -> {
                                        Minecraft.getInstance().setScreen(new me.bombo.bomboaddons.gui.GlobalStorageScreen());
                                    });
                                    return 1;
                                }));

                        // --- Playtime ---
                        builder.then(ClientCommands.literal("pt")
                                .executes(context -> {
                                    Minecraft.getInstance().execute(() -> {
                                        Minecraft.getInstance().setScreenAndShow(new PlaytimeGUI(null));
                                    });
                                    return 1;
                                })
                                .then(ClientCommands.literal("sync")
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Component.literal(
                                                    PREFIX + "§aManually syncing your playtime data to the cloud..."));
                                            PlaytimeTracker.sendPlaytimeDataToCloud();
                                            return 1;
                                        }))
                                .then(ClientCommands.argument("username", StringArgumentType.string())
                                        .executes(context -> {
                                            String username = StringArgumentType.getString(context, "username");
                                            Minecraft mc = Minecraft.getInstance();
                                            boolean targetOnline = false;
                                            if (mc.getConnection() != null && !username.equalsIgnoreCase(mc.getUser().getName())) {
                                                for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                                                    if (info.getProfile().name().equalsIgnoreCase(username)) {
                                                        targetOnline = true;
                                                        if (mc.player != null) {
                                                            mc.player.connection.sendCommand("msg " + info.getProfile().name() + " [BomboPlaytimeSyncRequest]");
                                                        }
                                                        break;
                                                    }
                                                }
                                            }
                                            final boolean delayFetch = targetOnline;
                                            context.getSource().sendFeedback(Component.literal(
                                                    PREFIX + "§aFetching playtime data for §e" + username + "§a..."));
                                            new Thread(() -> {
                                                try {
                                                    if (delayFetch) {
                                                        Thread.sleep(1000);
                                                    }
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
                                                                        .setScreenAndShow(new PlaytimeGUI(data));
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
                        builder.then(ClientCommands.literal("update")
                                .executes(context -> {
                                    ModUpdater.checkAndUpdate(false);
                                    return 1;
                                }));

                        // --- Coords Command ---
                        builder.then(ClientCommands.literal("coords")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        int x = (int) mc.player.getX();
                                        int y = (int) mc.player.getY();
                                        int z = (int) mc.player.getZ();
                                        String coords = "x: " + x + ", y: " + y + ", z: " + z;
                                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aCurrent Coords: §e" + coords));
                                    }
                                    return 1;
                                })
                                .then(ClientCommands.argument("command", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.player != null) {
                                                int x = (int) mc.player.getX();
                                                int y = (int) mc.player.getY();
                                                int z = (int) mc.player.getZ();
                                                String coords = "x: " + x + ", y: " + y + ", z: " + z;
                                                String cmd = StringArgumentType.getString(context, "command");
                                                if (cmd.startsWith("/")) {
                                                    cmd = cmd.substring(1);
                                                    executeTracked(cmd + " " + coords);
                                                } else {
                                                    executeTracked(cmd + " " + coords);
                                                }
                                            }
                                            return 1;
                                        })));

                        // --- Tracer Command ---
                        builder.then(ClientCommands.literal("tracer")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    net.minecraft.world.entity.Entity target = mc.crosshairPickEntity;
                                    if (target == null && mc.player != null && mc.level != null) {
                                        // Try to find a small entity like an item drop
                                        net.minecraft.world.phys.Vec3 eyePos = mc.player.getEyePosition();
                                        net.minecraft.world.phys.Vec3 lookVec = mc.player.getViewVector(1.0f);
                                        net.minecraft.world.phys.Vec3 traceEnd = eyePos.add(lookVec.x * 5, lookVec.y * 5, lookVec.z * 5);
                                        net.minecraft.world.phys.AABB aabb = mc.player.getBoundingBox().expandTowards(lookVec.scale(5)).inflate(1.0D);
                                        for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, aabb, ent -> !ent.isSpectator() && ent.isPickable() || ent instanceof net.minecraft.world.entity.item.ItemEntity)) {
                                            net.minecraft.world.phys.AABB entAabb = e.getBoundingBox().inflate(e.getPickRadius() + 0.3f);
                                            java.util.Optional<net.minecraft.world.phys.Vec3> hit = entAabb.clip(eyePos, traceEnd);
                                            if (hit.isPresent()) {
                                                target = e;
                                                break;
                                            }
                                        }
                                    }

                                    if (target != null) {
                                        String uuidStr = target.getUUID().toString();
                                        String traceId = uuidStr;
                                        String name = target.getName().getString();
                                        
                                        if (target instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                                            String itemName = itemEntity.getItem().getHoverName().getString();
                                            name = "Item: " + itemName;
                                            traceId = itemName; // Use Item name instead of UUID for dropped items
                                        }

                                        BomboConfig.Settings s = BomboConfig.get();
                                        if (s.customTracers.containsKey(traceId)) {
                                            s.customTracers.remove(traceId);
                                            BomboConfig.save();
                                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cRemoved tracer from entity!"));
                                        } else if (s.customTracers.containsKey(uuidStr)) {
                                            // Fallback in case they already had the specific UUID traced
                                            s.customTracers.remove(uuidStr);
                                            BomboConfig.save();
                                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cRemoved tracer from entity!"));
                                        } else {
                                            s.customTracers.put(traceId, new BomboConfig.Settings.CustomTracerInfo(name, "green"));
                                            BomboConfig.save();
                                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aAdded tracer to entity: " + name));
                                        }
                                    } else {
                                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cYou are not looking at an entity!"));
                                    }
                                    return 1;
                                })
                                .then(ClientCommands.literal("clear")
                                        .executes(context -> {
                                            BomboConfig.get().customTracers.clear();
                                            BomboConfig.save();
                                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cCleared all custom tracers!"));
                                            return 1;
                                        })));

                        // --- Sim Command ---
                        builder.then(ClientCommands.literal("sim")
                                .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String msg = StringArgumentType.getString(context, "message").replace("&", "§");
                                            Minecraft.getInstance().player.sendSystemMessage(Component.literal(msg));
                                            return 1;
                                        })));

                        // --- Head Command ---
                        builder.then(ClientCommands.literal("head")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        net.minecraft.world.item.ItemStack stack = mc.player.getMainHandItem();
                                        if (stack != null && !stack.isEmpty()) {
                                            String data = stack.getComponentsPatch().toString();
                                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aHead Data printed to console and copied to clipboard!"));
                                            System.out.println("Head Data: " + data);
                                            mc.keyboardHandler.setClipboard(data);
                                        } else {
                                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cYou are not holding an item!"));
                                        }
                                    }
                                    return 1;
                                }));

                        // --- Highlight Command ---
                        builder.then(ClientCommands.literal("highlight")
                                .then(ClientCommands.literal("remove")
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
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

                                .then(ClientCommands.literal("toggle")
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name")
                                                            .toLowerCase();
                                                    BomboConfig.HighlightInfo info = BomboConfig.get().highlights
                                                            .get(name);
                                                    if (info != null) {
                                                        info.enabled = !info.enabled;
                                                        BomboConfig.save();
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§aToggled highlight for: §e" + name + " §a("
                                                                        + (info.enabled ? "Enabled" : "Disabled")
                                                                        + ")"));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(
                                                                PREFIX + "§cNo highlight found for: §e" + name));
                                                    }
                                                    return 1;
                                                })))

                                .then(ClientCommands.literal("list")
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
                                                    Component toggleBtn = enabled ? Component.literal(" §a[Enabled]")
                                                            : Component.literal(" §c[Disabled]");
                                                    if (toggleClick != null) {
                                                        toggleBtn = (enabled ? Component.literal(" §a[Enabled]")
                                                                : Component.literal(" §c[Disabled]"))
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
                                .then(ClientCommands.literal("clear")
                                        .executes(context -> {
                                            BomboConfig.get().highlights.clear();
                                            BomboConfig.save();
                                            context.getSource().sendFeedback(
                                                    Component.literal(PREFIX + "§aCleared all highlights."));
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands.argument("mob", StringArgumentType.word())
                                                .then(ClientCommands.argument("color", StringArgumentType.word())
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
                                                        .then(ClientCommands.argument("showInvisible",
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
                                .then(ClientCommands.argument("mob", StringArgumentType.word())
                                        .then(ClientCommands.argument("color", StringArgumentType.word())
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
                                                .then(ClientCommands
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
                                            Component.literal(PREFIX
                                                    + "§7Subcommands: list, remove <name>, clear, toggle <name>"));
                                    return 1;
                                }));

                        builder.then(ClientCommands.literal("left").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal(
                                        "§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleLeft();
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("right").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal(
                                        "§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleRight();
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("back").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal(
                                        "§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleBackward();
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("forw").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal(
                                        "§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleForward();
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("break").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal(
                                        "§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleBreak();
                            return 1;
                        }));
                        builder.then(ClientCommands.literal("use").executes(context -> {
                            if (!BomboConfig.get().gardenMovement || !SkyblockUtils.isInGarden()) {
                                context.getSource().sendFeedback(Component.literal(
                                        "§8[§bBomboAddons§8] §cGarden Movement is disabled or you are not in the Garden!"));
                                return 1;
                            }
                            GardenMovement.toggleUse();
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("gui").executes(context -> {
                            openHudMoveNextTick = true;
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("tracerdebug").executes(context -> {
                            BomboConfig.Settings s = BomboConfig.get();
                            context.getSource().sendFeedback(Component.literal("§6§l=== Tracer Debug ==="));
                            context.getSource().sendFeedback(Component.literal("§ePest ESP: " + s.pestEsp + " | Pest Tracers: " + s.pestEspTracer));
                            context.getSource().sendFeedback(Component.literal("§eHighlights Enabled: " + s.highlightsEnabled + " | Test All: " + s.tracerTestAllEntities));
                            context.getSource().sendFeedback(Component.literal("§eCorpse ESP: " + s.corpseEsp + " | Corpse Tracers: " + s.corpseEspStyleTracer));
                            
                            context.getSource().sendFeedback(Component.literal("§cHighlightESP Render Stats:"));
                            context.getSource().sendFeedback(Component.literal("  - Loop Entities Count: " + HighlightESP.lastEntityCount));
                            context.getSource().sendFeedback(Component.literal("  - Stored Tracers: " + HighlightESP.TRACERS.size()));
                            
                            context.getSource().sendFeedback(Component.literal("§bPest Tracers Count: " + HighlightESP.lastTracersAdded));
                            context.getSource().sendFeedback(Component.literal("§bHighlights Tracers Count: " + HighlightESP.lastTracersAdded));
                            context.getSource().sendFeedback(Component.literal("§bCorpse Tracers Count: " + HighlightESP.lastTracersAdded));
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("corpsedebug").executes(context -> {
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
                                    net.minecraft.world.item.ItemStack helmet = stand
                                            .getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                                    String itemStr = "None";
                                    if (helmet != null && !helmet.isEmpty()) {
                                        itemStr = helmet.getHoverName().getString();
                                    }
                                    context.getSource().sendFeedback(Component.literal(
                                            "§eStand ID: " + stand.getId() + " §7- Pos: " + stand.blockPosition()
                                                    + " §7- Head: §b" + itemStr));
                                }
                            }
                            context.getSource().sendFeedback(Component.literal(
                                    "§aScan complete. Checked " + totalCount + " entities. Found " + standCount
                                            + " armor stands."));
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("resetdice").executes(context -> {
                            DiceTracker.reset();
                            context.getSource().sendFeedback(
                                    Component.literal(PREFIX + "§aDice Tracker statistics have been reset!"));
                            return 1;
                        }));

                        builder.then(ClientCommands.literal("msg")
                                .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String msg = StringArgumentType.getString(context, "message").replace('&',
                                                    '§');
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.gui != null && mc.gui.getChat() != null) {
                                                mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
                                            }
                                            processChatMessage(msg);
                                            return 1;
                                        })));

                        // --- Particle List + ESP ---
                        for (String argName : new String[] { "particles", "particle" }) {
                            builder.then(ClientCommands.literal(argName)
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
                                                String keyName = entry.getKey();
                                                String highlightName = keyName.toLowerCase();

                                                // Find details of a matching entry
                                                String rawType = "Unknown";
                                                double lastX = 0, lastY = 0, lastZ = 0;
                                                for (ParticleTracker.ParticleEntry p : ParticleTracker
                                                        .getEspPoints(null)) {
                                                    if (p.type.equals(keyName)) {
                                                        rawType = p.rawType;
                                                        lastX = p.x;
                                                        lastY = p.y;
                                                        lastZ = p.z;
                                                        break;
                                                    }
                                                }

                                                context.getSource().sendFeedback(Component.literal(
                                                        "  §7» Highlight name: §e" + highlightName + " §8x"
                                                                + entry.getValue() +
                                                                " §7(Raw/Debug: §d" + rawType + "§7, Last Pos: §a"
                                                                + String.format("%.2f, %.2f, %.2f", lastX, lastY, lastZ)
                                                                + "§7)"));
                                            }
                                        }
                                        return 1;
                                    })
                                    .then(ClientCommands.literal("esp")
                                            .executes(context -> {
                                                ParticleTracker.espEnabled = !ParticleTracker.espEnabled;
                                                ParticleESP.typeFilter = null;
                                                context.getSource()
                                                        .sendFeedback(Component.literal(PREFIX + "§7Particle ESP: "
                                                                + (ParticleTracker.espEnabled ? "§aON" : "§cOFF")));
                                                return 1;
                                            })
                                            .then(ClientCommands.argument("type", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        String filter = StringArgumentType.getString(context, "type");
                                                        if (filter.equals("off") || filter.equals("none")) {
                                                            ParticleESP.typeFilter = null;
                                                            ParticleTracker.espEnabled = false;
                                                            context.getSource().sendFeedback(
                                                                    Component.literal(
                                                                            PREFIX + "§cParticle ESP disabled."));
                                                        } else {
                                                            ParticleESP.typeFilter = filter;
                                                            ParticleTracker.espEnabled = true;
                                                            context.getSource().sendFeedback(Component.literal(PREFIX
                                                                    + "§aParticle ESP §aON §7— filtering: §e"
                                                                    + filter));
                                                        }
                                                        return 1;
                                                    })))
                                    .then(ClientCommands.literal("radius")
                                            .then(ClientCommands
                                                    .argument("r",
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.integer(
                                                                    1,
                                                                    128))
                                                    .executes(context -> {
                                                        int r = com.mojang.brigadier.arguments.IntegerArgumentType
                                                                .getInteger(context, "r");
                                                        ParticleTracker.espRadius = r;
                                                        context.getSource().sendFeedback(Component.literal(PREFIX
                                                                + "§7Particle ESP radius set to §e" + r
                                                                + "§7 blocks."));
                                                        return 1;
                                                    })))
                                    .then(ClientCommands.literal("clear")
                                            .executes(context -> {
                                                ParticleTracker.clear();
                                                context.getSource().sendFeedback(
                                                        Component.literal(PREFIX + "§aParticle history cleared."));
                                                return 1;
                                            })));
                        }

                        builder.then(ClientCommands.literal("anvil")
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands
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
                                        .then(ClientCommands.argument("enchant", StringArgumentType.word())
                                                .then(ClientCommands
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
                                .then(ClientCommands.literal("remove")
                                        .then(ClientCommands.argument("enchant", StringArgumentType.word())
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
                                .then(ClientCommands.literal("list")
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

                        builder.then(ClientCommands.literal("view")
                                .then(ClientCommands.argument("username", StringArgumentType.string())
                                        .then(ClientCommands.argument("path", StringArgumentType.greedyString())
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
                        builder.then(ClientCommands.literal("lf")
                                .then(ClientCommands.argument("username", StringArgumentType.string())
                                        .executes(context -> {
                                            LF.show(StringArgumentType.getString(context, "username"), "", false);
                                            return 1;
                                        })
                                        .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    LF.show(StringArgumentType.getString(context, "username"),
                                                            StringArgumentType.getString(context, "query"), false);
                                                    return 1;
                                                }))));
                        builder.then(ClientCommands.literal("lfc")
                                .then(ClientCommands.argument("username", StringArgumentType.string())
                                        .executes(context -> {
                                            LF.show(StringArgumentType.getString(context, "username"), "", true);
                                            return 1;
                                        })
                                        .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    LF.show(StringArgumentType.getString(context, "username"),
                                                            StringArgumentType.getString(context, "query"), true);
                                                    return 1;
                                                }))));
                        builder.then(ClientCommands.literal("lb")
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null)
                                        LF.show(mc.getUser().getName(), "", false);
                                    return 1;
                                })
                                .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String query = StringArgumentType.getString(context, "query");
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.player != null) {
                                                String name = mc.getUser().getName();
                                                LF.show(name, query, false);
                                            }
                                            return 1;
                                        })));

                        builder.then(ClientCommands.literal("pet")
                                .then(ClientCommands.literal("save")
                                        .then(ClientCommands.argument("slot", StringArgumentType.word())
                                                .executes(context -> {
                                                    String slot = StringArgumentType.getString(context, "slot");
                                                    PetManager.savePet(context.getSource(), slot);
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("apply")
                                        .then(ClientCommands.argument("slot", StringArgumentType.word())
                                                .executes(context -> {
                                                    String slot = StringArgumentType.getString(context, "slot");
                                                    PetManager.applyPet(context.getSource(), slot);
                                                    return 1;
                                                })))
                                .then(ClientCommands.argument("slot", StringArgumentType.word())
                                        .executes(context -> {
                                            String slot = StringArgumentType.getString(context, "slot");
                                            PetManager.applyPet(context.getSource(), slot);
                                            return 1;
                                        })));

                        builder.then(ClientCommands.literal("ep")
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

                        builder.then(ClientCommands.literal("get")
                                .then(ClientCommands.literal("add")
                                        .then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                                                .then(ClientCommands.argument("alias", StringArgumentType.word())
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
                                .then(ClientCommands.literal("remove")
                                        .then(ClientCommands.argument("alias", StringArgumentType.word())
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
                                .then(ClientCommands.literal("list")
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
                                .then(ClientCommands.argument("alias_or_id", StringArgumentType.word())
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
                                        .then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                                                .then(ClientCommands.argument("alias", StringArgumentType.word())
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

                        builder.then(ClientCommands.literal("online")
                                .executes(context -> {
                                    if (!BomboConfig.get().ircChatEnabled) {
                                        context.getSource().sendFeedback(Component.literal(PREFIX
                                                + "§cIRC Chat is currently disabled. Toggle it ON in the config GUI to see online users."));
                                        return 1;
                                    }
                                    if (!IRCClient.isConnected()) {
                                        context.getSource().sendFeedback(Component.literal(PREFIX
                                                + "§7Connecting to IRC server... (Please wait a moment and try again)"));
                                        IRCClient.start();
                                        return 1;
                                    }

                                    java.util.Map<String, String> onlineMap = IRCClient.getOnlinePlayers();
                                    context.getSource().sendFeedback(Component.literal(PREFIX + "§6Online Mod Users:"));
                                    if (onlineMap.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal(
                                                "  §7No other users detected yet (or currently fetching names list)."));
                                    } else {
                                        int count = 0;
                                        for (String nick : onlineMap.values()) {
                                            IRCClient.ModUser user = IRCClient.parseNick(nick);
                                            context.getSource().sendFeedback(Component.literal(
                                                    "  §7» §a" + user.username + " §7— Version: §e" + user.version));
                                            count++;
                                        }
                                        context.getSource()
                                                .sendFeedback(Component.literal("§7Total online: §b" + count));
                                    }
                                    return 1;
                                }));

                        builder.then(ClientCommands.literal("color")
                                .executes(context -> {
                                    context.getSource().sendFeedback(
                                            Component.literal("§8---------------- §b[Color Codes] §8----------------"));
                                    context.getSource()
                                            .sendFeedback(Component.literal("  §0&0 - Black        §1&1 - Dark Blue"));
                                    context.getSource()
                                            .sendFeedback(Component.literal("  §2&2 - Dark Green   §3&3 - Dark Aqua"));
                                    context.getSource().sendFeedback(
                                            Component.literal("  §4&4 - Dark Red     §5&5 - Dark Purple"));
                                    context.getSource()
                                            .sendFeedback(Component.literal("  §6&6 - Gold         §7&7 - Gray"));
                                    context.getSource()
                                            .sendFeedback(Component.literal("  §8&8 - Dark Gray    §9&9 - Blue"));
                                    context.getSource()
                                            .sendFeedback(Component.literal("  §a&a - Green        §b&b - Aqua"));
                                    context.getSource().sendFeedback(
                                            Component.literal("  §c&c - Red          §d&d - Light Purple"));
                                    context.getSource()
                                            .sendFeedback(Component.literal("  §e&e - Yellow       §f&f - White"));
                                    context.getSource().sendFeedback(Component.literal("§8Formatting Codes:"));
                                    context.getSource()
                                            .sendFeedback(Component.literal("  §k&k - Obfuscated   §l&l - Bold"));
                                    context.getSource()
                                            .sendFeedback(Component.literal("  §m&m - Strikethrough§n&n - Underline"));
                                    context.getSource()
                                            .sendFeedback(Component.literal("  §o&o - Italic       §r&r - Reset"));
                                    return 1;
                                }));

                        builder.then(ClientCommands.literal("secrets").executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.getConnection() != null) {
                                context.getSource().sendFeedback(Component.literal("§8[§bBombo§8] §7Fetching current secrets..."));
                                for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                                    String name = info.getProfile().name();
                                    if (name != null && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                                        java.util.UUID uuid = info.getProfile().id();
                                        DungeonSecretsTracker.fetchAndPrintSecrets(name, uuid);
                                    }
                                }
                            }
                            return 1;
                        }));
                    };

                    setupCommands.accept(bBuilder);
                    setupCommands.accept(baBuilder);
                    setupCommands.accept(bomboBuilder);

                    dispatcher.register(bBuilder);
                    dispatcher.register(baBuilder);
                    dispatcher.register(bomboBuilder);

                    dispatcher.register(ClientCommands.literal("bomboprof")
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
                        dispatcher.register(ClientCommands.literal(s)
                                .executes(context -> {
                                    SBECommands.handleCommand(s, Minecraft.getInstance().player.getName().getString(),
                                            null);
                                    return 1;
                                })
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            SBECommands.handleCommand(s, StringArgumentType.getString(context, "name"),
                                                    null);
                                            return 1;
                                        })
                                        .then(ClientCommands.argument("profile", StringArgumentType.word())
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
                    dispatcher.register(ClientCommands.literal("bombo_highlight_slot")
                            .then(ClientCommands.argument("slots", StringArgumentType.string())
                                    .then(ClientCommands.argument("command", StringArgumentType.greedyString())
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
                    dispatcher.register(ClientCommands.literal("bombo_museum_click")
                            .then(ClientCommands.argument("username", StringArgumentType.string())
                                    .then(ClientCommands.argument("slot", IntegerArgumentType.integer())
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
                    dispatcher.register(ClientCommands.literal("tk")
                            .then(ClientCommands.argument("username", StringArgumentType.string())
                                    .executes(context -> {
                                        LF.showToolkit(StringArgumentType.getString(context, "username"), 50);
                                        return 1;
                                    })
                                    .then(ClientCommands.argument("limit", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                LF.showToolkit(StringArgumentType.getString(context, "username"),
                                                        IntegerArgumentType.getInteger(context, "limit"));
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommands.literal("deal")
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
                    dispatcher.register(ClientCommands.literal("bits")
                            .executes(context -> {
                                BitsManager.fetchTopBits(5).thenAccept(lines -> {
                                    for (String line : lines)
                                        context.getSource().sendFeedback(Component.literal(line));
                                });
                                return 1;
                            })
                            .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1, 100))
                                    .executes(context -> {
                                        int amount = IntegerArgumentType.getInteger(context, "amount");
                                        BitsManager.fetchTopBits(amount).thenAccept(lines -> {
                                            for (String line : lines)
                                                context.getSource().sendFeedback(Component.literal(line));
                                        });
                                        return 1;
                                    })));
                    dispatcher.register(ClientCommands.literal("bclick")
                            .executes(context -> {
                                ClickLogic.listTargets(context.getSource());
                                return 1;
                            }));
                    dispatcher.register(ClientCommands.literal("bc")
                            .executes(context -> {
                                context.getSource()
                                        .sendFeedback(Component.literal("§8[§bBomboAddons§8] §cUsage: /bc <message>"));
                                return 1;
                            })
                            .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        if (!BomboConfig.get().ircChatEnabled) {
                                            context.getSource().sendFeedback(Component.literal(
                                                    "§8[§bBomboAddons§8] §cIRC Chat is currently disabled! Toggle it on with §e/b chat§c."));
                                            return 1;
                                        }
                                        String message = StringArgumentType.getString(context, "message");
                                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                                        if (message.contains("$coords") && mc.player != null) {
                                            int x = (int) mc.player.getX();
                                            int y = (int) mc.player.getY();
                                            int z = (int) mc.player.getZ();
                                            message = message.replace("$coords", "x: " + x + ", y: " + y + ", z: " + z);
                                        }
                                        IRCClient.sendMessage(message);
                                        return 1;
                                    })));

                    dispatcher.register(ClientCommands.literal("chat")
                            .then(ClientCommands.literal("b")
                                    .executes(context -> {
                                        BomboConfig.get().ircDefaultChat = true;
                                        BomboConfig.save();
                                        context.getSource().sendFeedback(Component.literal(
                                                "§8[§bBomboAddons§8] §7Default chat set to §eIRC§7. Messages will be sent to IRC chat."));
                                        return 1;
                                    }))
                            .then(ClientCommands.argument("channel", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String channel = StringArgumentType.getString(context, "channel");
                                        BomboConfig.get().ircDefaultChat = false;
                                        BomboConfig.save();
                                        if (Minecraft.getInstance().getConnection() != null) {
                                            Minecraft.getInstance().getConnection()
                                                    .send(new ServerboundChatCommandPacket("chat " + channel));
                                        }
                                        return 1;
                                    }))
                            .executes(context -> {
                                if (Minecraft.getInstance().getConnection() != null) {
                                    Minecraft.getInstance().getConnection()
                                            .send(new ServerboundChatCommandPacket("chat"));
                                }
                                return 1;
                            }));

                    dispatcher.register(ClientCommands.literal("c")
                            .then(ClientCommands.argument("expression", StringArgumentType.greedyString())
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
                    dispatcher.register(ClientCommands.literal("checki")
                            .then(ClientCommands.literal("list")
                                    .executes(context -> {
                                        InventoryManager.listSnapshots(context.getSource());
                                        return 1;
                                    }))
                            .then(ClientCommands.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        InventoryManager.openSnapshot(StringArgumentType.getString(context, "name"), 1);
                                        return 1;
                                    })
                                    .then(ClientCommands.argument("index", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                InventoryManager.openSnapshot(
                                                        StringArgumentType.getString(context, "name"),
                                                        IntegerArgumentType.getInteger(context, "index"));
                                                return 1;
                                            }))));
                    dispatcher.register(ClientCommands.literal("savei")
                            .executes(context -> {
                                InventoryManager.captureCurrentGUI();
                                return 1;
                            }));

                    java.util.function.Consumer<String> registerHotbarCommand = nameLiteral -> {
                        dispatcher.register(ClientCommands.literal(nameLiteral)
                                .then(ClientCommands.literal("save")
                                        .then(ClientCommands.argument("name", StringArgumentType.string())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    if (HotbarSwapper.saveSnapshot(name)) {
                                                        context.getSource().sendFeedback(
                                                                Component
                                                                        .literal("§aSaved hotbar snapshot: §e" + name));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(
                                                                "§cFailed to save hotbar snapshot (player is null)."));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("s")
                                        .then(ClientCommands.argument("name", StringArgumentType.string())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    if (HotbarSwapper.saveSnapshot(name)) {
                                                        context.getSource().sendFeedback(
                                                                Component
                                                                        .literal("§aSaved hotbar snapshot: §e" + name));
                                                    } else {
                                                        context.getSource().sendFeedback(Component.literal(
                                                                "§cFailed to save hotbar snapshot (player is null)."));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("delete")
                                        .then(ClientCommands.argument("name", StringArgumentType.string())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    if (HotbarSwapper.deleteSnapshot(name)) {
                                                        context.getSource().sendFeedback(
                                                                Component.literal(
                                                                        "§aDeleted hotbar snapshot: §e" + name));
                                                    } else {
                                                        context.getSource().sendFeedback(
                                                                Component.literal("§cSnapshot not found: §e" + name));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("list")
                                        .executes(context -> {
                                            context.getSource()
                                                    .sendFeedback(Component.literal("§6--- Hotbar Snapshots ---"));
                                            for (String id : HotbarSwapper.list()) {
                                                context.getSource().sendFeedback(Component.literal("§7- §e" + id));
                                            }
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("apply")
                                        .then(ClientCommands.argument("name", StringArgumentType.string())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    if (HotbarSwapper.exists(name)) {
                                                        HotbarSwapper.apply(name);
                                                        context.getSource().sendFeedback(
                                                                Component.literal(
                                                                        "§aApplied hotbar snapshot: §e" + name));
                                                    } else {
                                                        context.getSource().sendFeedback(
                                                                Component.literal("§cSnapshot not found: §e" + name));
                                                    }
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("a")
                                        .then(ClientCommands.argument("name", StringArgumentType.string())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    if (HotbarSwapper.exists(name)) {
                                                        HotbarSwapper.apply(name);
                                                        context.getSource().sendFeedback(
                                                                Component.literal(
                                                                        "§aApplied hotbar snapshot: §e" + name));
                                                    } else {
                                                        context.getSource().sendFeedback(
                                                                Component.literal("§cSnapshot not found: §e" + name));
                                                    }
                                                    return 1;
                                                }))));
                    };

                    registerHotbarCommand.accept("bombohb");
                    registerHotbarCommand.accept("bhb");

                    dispatcher.register(ClientCommands.literal("v")
                            .then(ClientCommands.argument("player", StringArgumentType.greedyString())
                                    .executes(c -> {
                                        Minecraft mc = Minecraft.getInstance();
                                        if (mc.getConnection() != null) {
                                            mc.getConnection().sendCommand("visit " + StringArgumentType.getString(c, "player"));
                                        }
                                        return 1;
                                    })
                            )
                    );

                    dispatcher.register(ClientCommands.literal("tp")
                            .then(ClientCommands.argument("plot", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        if (SkyblockUtils.isInGarden()) {
                                            for (int i = 1; i <= 24; i++) {
                                                builder.suggest(String.valueOf(i));
                                            }
                                        } else {
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.getConnection() != null) {
                                                for (net.minecraft.client.multiplayer.PlayerInfo playerInfo : mc.getConnection().getOnlinePlayers()) {
                                                    builder.suggest(playerInfo.getProfile().name());
                                                }
                                            }
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> {
                                        String plot = StringArgumentType.getString(context, "plot");
                                        Minecraft mc = Minecraft.getInstance();
                                        if (mc.player != null && mc.player.connection != null) {
                                            if (SkyblockUtils.isInGarden()) {
                                                mc.player.connection.sendCommand("tptoplot " + plot);
                                            } else {
                                                mc.player.connection.sendCommand("tp " + plot);
                                            }
                                        }
                                        return 1;
                                    })));

                    dispatcher.register(ClientCommands.literal("mod")
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
                        dispatcher.register(ClientCommands.literal("f" + i).executes(c -> {
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
                        dispatcher.register(ClientCommands.literal("m" + i).executes(c -> {
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
                        dispatcher.register(ClientCommands.literal("t" + i).executes(c -> {
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
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                PlaytimeTracker.sendPlaytimeDataToCloud();
            });
            RankCache.load();
            PlaytimeTracker.load();
            DiceTracker.load();
            ChatPeek.init();
            BazaarUtils.init();
            LowestBinManager.ensureLoaded();
            ItemHotkeys.init();

            ModUpdater.init();
            TabCompletionManager.load();
            registerTickEvents();
            DiceHud.init();
            KuudraTimer.init();
            CustomTimerManager.init();
            DungeonPadTimers.init();
            CorpseHighlight.init();
            IRCClient.start();

            LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
                if (BomboConfig.get().debugEntities)
                    System.out.println("DEBUG: AFTER_TRANSLUCENT_FEATURES Fired!");
                HighlightESP.render(context);
                PestESP.render(context);
                ParticleESP.render(context);
                try {
                    me.bombo.bomboaddons.kuudra.pearls.Pearls.render(context);
                } catch (Throwable t) {
                }
                try {
                    GardenWaypoints.render(context);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                try {
                    me.bombo.bomboaddons.eggfinder.EggFinder.render(context);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                try {
                    CorpseHighlight.render(context);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                try {
                    BlockHighlight.render(context);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });

            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "main_hud"),
                    (graphics, deltaTracker) -> {


                        if (BomboConfig.get().tracerTestMode) {
                            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                            float centerX = screenWidth / 2.0f;
                            float centerY = screenHeight / 2.0f;
                            BomboRenderUtils.draw2DLine(graphics, centerX, centerY, 100f, 100f, 0xFFFF0000, 3.0f);
                            BomboRenderUtils.draw2DLine(graphics, centerX, centerY, screenWidth - 100f, 100f, 0xFF00FF00, 3.0f);
                            BomboRenderUtils.draw2DLine(graphics, centerX, centerY, centerX, 50f, 0xFF0000FF, 3.0f);
                        }

                        try {
                            if (BomboConfig.get().kuudraDebug) {
                                graphics.text(Minecraft.getInstance().font, "§d§lHUD RENDER TEST ACTIVE", 10, 50,
                                        0xFFFF00FF, true);
                            }
                            for (me.bombo.bomboaddons.kuudra.pearls.Pearls.PearlHUDText t : me.bombo.bomboaddons.kuudra.pearls.Pearls.HUD_TEXTS) {
                                graphics.centeredText(Minecraft.getInstance().font, t.text, (int) t.x, (int) t.y,
                                        t.color);
                            }
                        } catch (Throwable t) {
                        }

                        // Only render HUD if no screen is open or it's the HudMoveScreen
                        if (Minecraft.getInstance().screen == null
                                || Minecraft.getInstance().screen instanceof HudMoveScreen) {
                            FeastBakeryHud.onHudRender(graphics);
                            ExperimentationTableHud.onHudRender(graphics);
                        }
                        if (Minecraft.getInstance().screen == null) {
                            GardenMovement.drawDirectionWarning(graphics);
                        }
                    });

            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT
                    .register((client, screen, scaledWidth, scaledHeight) -> {
                        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen)
                                .register((screen1, graphics, mouseX, mouseY, tickDelta) -> {
                                    FeastBakeryHud.onHudRender(graphics);
                                    ExperimentationTableHud.onHudRender(graphics);
                                });
                    });

            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                currentHypixelChannel = "a";
                if (client.getCurrentServer() != null) {
                    lastServerData = client.getCurrentServer();
                }
                LowestBinManager.reload();
                AutoExperiments.reset();
                ModUpdater.checkAndUpdate(true);

                // Fetch/update Skyblocker WebSocket token
                try {
                    me.bombo.bomboaddons.eggfinder.EggAuth.updateToken();
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
                    me.bombo.bomboaddons.eggfinder.EggFinder.clearEggs();
                    me.bombo.bomboaddons.eggfinder.EggWebSocket.disconnect();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                try {
                    TabCompletionManager.party.clear();
                } catch (Throwable t) {
                }
            });

            ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) -> {
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
                String plain = message.getString().trim();
                if (plain.contains("[BomboPlaytimeSyncRequest]")) {
                    try {
                        PlaytimeTracker.sendPlaytimeDataToCloud();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    return false;
                }
                try {
                    me.bombo.bomboaddons.eggfinder.EggFinder.onChatMessage(message, overlay);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
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
                DungeonSecretsTracker.onChatMessage(clean);
                AFKManager.onChatMessage(clean);
                me.bombo.bomboaddons.features.dungeons.ClearInfoHUD.onChatMessage(clean);
                
                java.util.regex.Matcher ratMatcher = java.util.regex.Pattern.compile("^CHEESE! You buffed (\\S+) giving them (.+) for\\s+(\\d+)\\s+seconds!").matcher(clean);
                if (ratMatcher.find()) {
                    String target = ratMatcher.group(1);
                    String stat = ratMatcher.group(2);
                    long duration = Long.parseLong(ratMatcher.group(3)) * 1000;
                    me.bombo.bomboaddons.CustomTimerManager.startTimer("Rat: " + target + " (" + stat + ")", duration);
                }
                if (overlay) {
                    me.bombo.bomboaddons.kuudra.pearls.Pearls.onTitleReceived(clean);
                }
                if (BomboConfig.get().autoTrevorQuest) {
                    if (clean.contains("Accept the trapper's task to hunt the animal?")) {
                        findAndClickYes(message);
                    }
                }
                if ((clean.contains("You are now in the ") && clean.contains("channel")) ||
                        (clean.contains("Opened a chat conversation with ") && clean.contains("minutes"))) {

                    if (clean.contains("You are now in the ") && clean.contains("channel")) {
                        if (clean.contains("ALL CHAT") || clean.contains("ALL")) {
                            currentHypixelChannel = "a";
                        } else if (clean.contains("GUILD")) {
                            currentHypixelChannel = "g";
                        } else if (clean.contains("PARTY")) {
                            currentHypixelChannel = "p";
                        } else if (clean.contains("OFFICER")) {
                            currentHypixelChannel = "o";
                        } else if (clean.contains("CO-OP") || clean.contains("COOP")) {
                            currentHypixelChannel = "c";
                        }
                    } else if (clean.contains("Opened a chat conversation with ") && clean.contains("minutes")) {
                        int idx = clean.indexOf("Opened a conversation with ");
                        if (idx == -1)
                            idx = clean.indexOf("Opened a chat conversation with ");
                        int forNextIndex = clean.indexOf(" for the next");
                        if (idx != -1 && forNextIndex != -1) {
                            String namePart = clean.substring(idx + (clean.contains("Opened a chat conversation with ")
                                    ? "Opened a chat conversation with ".length()
                                    : "Opened a conversation with ".length()), forNextIndex).trim();
                            String[] parts = namePart.split("\\s+");
                            if (parts.length > 0) {
                                currentHypixelChannel = parts[parts.length - 1].toLowerCase();
                            }
                        }
                    }

                    if (BomboConfig.get().ircDefaultChat) {
                        BomboConfig.get().ircDefaultChat = false;
                        BomboConfig.save();
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                                "§8[§bBomboAddons§8] §7Default chat set to §ePublic§7 (detected channel change)."));
                    }
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
                CustomTimerManager.tick();
            } catch (Throwable t) {
            }
            try {
                me.bombo.bomboaddons.features.StorageTracker.onGuiTick();
            } catch (Throwable t) {
            }
            try {
                if (client.player != null && client.player.inventoryMenu != null) {
                    int currentState = client.player.inventoryMenu.getStateId();
                    if (currentState != lastInventoryStateId) {
                        lastInventoryStateId = currentState;
                        me.bombo.bomboaddons.features.StorageTracker.updatePlayerInventory(client);
                    }
                }
            } catch (Throwable t) {
            }
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
                me.bombo.bomboaddons.eggfinder.EggFinder.tick();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try {
                me.bombo.bomboaddons.BedwarsESP.tick();
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
                        if (activeBinds != null)
                            binds.addAll(activeBinds);
                        if (generalBinds != null && !s.activeProfile.equals("General"))
                            binds.addAll(generalBinds);

                        net.minecraft.world.phys.Vec3 playerPos = client.player.position();
                        for (BomboConfig.CoordBind bind : binds) {
                            if (!bind.enabled)
                                continue;

                            // Check required island
                            if (bind.requiredIsland != null && !bind.requiredIsland.trim().isEmpty()) {
                                String currentAreaLocal = BomboaddonsClient.currentArea;
                                if (currentAreaLocal == null)
                                    currentAreaLocal = "";
                                String target = bind.requiredIsland.trim().toLowerCase();
                                boolean matched = currentAreaLocal.toLowerCase().contains(target);
                                if (!matched && client.level != null) {
                                    var scoreboard = client.level.getScoreboard();
                                    var sidebar = scoreboard
                                            .getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
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

                            double dist = playerPos
                                    .distanceTo(new net.minecraft.world.phys.Vec3(bind.x, bind.y, bind.z));
                            double r = bind.radius <= 0.0 ? 3.0 : bind.radius;
                            if (dist <= r) {
                                if (!bind.wasInside) {
                                    double minD = bind.minDelay;
                                    double maxD = bind.maxDelay;
                                    if (maxD > minD && maxD > 0.0) {
                                        double delaySec = minD + Math.random() * (maxD - minD);
                                        long triggerTime = System.currentTimeMillis() + (long) (delaySec * 1000.0);
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
                    System.out.println("DEBUG: Tick opening config GUI");
                    openGuiNextTick = false;
                    client.setScreen(new BomboConfigGUI(client.screen));
                    System.out.println("DEBUG: Config GUI set screen success");
                }
                if (openHudMoveNextTick && client.player != null) {
                    System.out.println("DEBUG: Tick opening HUD move screen");
                    openHudMoveNextTick = false;
                    client.setScreen(new HudMoveScreen());
                    System.out.println("DEBUG: HUD move screen set success");
                }
                if (openCustomizeGuiNextTick && client.player != null) {
                    System.out.println("DEBUG: Tick opening customize screen");
                    openCustomizeGuiNextTick = false;
                    client.setScreen(new ItemCustomizeScreen(client.screen));
                    System.out.println("DEBUG: Customize screen set screen success");
                }
            } catch (Throwable t) {
                Bomboaddons.LOGGER.error("[BomboAddons] Error opening screen!", t);
                try {
                    java.io.File file = new java.io.File("crash_exception.log");
                    try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file, true))) {
                        pw.println("=== TICK SCREEN OPEN EXCEPTION ===");
                        t.printStackTrace(pw);
                        pw.println("==================================");
                    }
                } catch (Throwable ignore) {
                }
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
                if (client.player != null) {
                    net.minecraft.world.item.ItemStack held = client.player.getMainHandItem();
                    if (!held.isEmpty()) {
                        String cleanName = held.getHoverName().getString().replaceAll("(?i)§.", "").toLowerCase();
                        if (cleanName.contains("aspect of the end") ||
                                cleanName.contains("aspect of the void") ||
                                cleanName.contains("hyperion") ||
                                cleanName.contains("valkyrie") ||
                                cleanName.contains("scylla") ||
                                cleanName.contains("astraea")) {
                            if (client.options.keyUse.isDown()) {
                                GardenMacroDetector.recordWeaponUse();
                            }
                        }
                    }
                }
            } catch (Throwable t) {
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
                me.bombo.bomboaddons.kuudra.pearls.KuudraUtils.onClientTick();
                me.bombo.bomboaddons.kuudra.pearls.Pearls.onClientTick();
            } catch (Throwable t) {
            }
            try {
                BlockHighlight.onTick();
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
                        mc.player.sendSystemMessage(Component.literal("§c[Bombo] Failed to execute: /" + cleanCmd));
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
            dispatcher.register(ClientCommands.literal(alias)
                    .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                            .executes(context -> {
                                String actualCmd = BomboConfig.get().commandAliases.get(alias);
                                if (actualCmd != null) {
                                    String args = StringArgumentType.getString(context, "args");
                                    executeTracked(actualCmd + " " + args);
                                } else {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null && mc.getConnection() != null) {
                                        String args = StringArgumentType.getString(context, "args");
                                        mc.getConnection().send(new ServerboundChatCommandPacket(alias + " " + args));
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
                            if (mc.player != null && mc.getConnection() != null) {
                                mc.getConnection().send(new ServerboundChatCommandPacket(alias));
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

    public static void registerMsgCommandsToDispatcher(
            com.mojang.brigadier.CommandDispatcher<ClientSuggestionProvider> dispatcher) {
        try {
            String[] msgCmds = { "w", "tell" };
            for (String cmd : msgCmds) {
                dispatcher.register(
                        com.mojang.brigadier.builder.LiteralArgumentBuilder.<ClientSuggestionProvider>literal(cmd)
                                .executes(context -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null && mc.player.connection != null) {
                                        mc.player.connection.sendCommand(cmd);
                                    }
                                    return 1;
                                })
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                        .<ClientSuggestionProvider, String>argument("username",
                                                com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .suggests((ctx, sb) -> TabCompletionManager.getUsernameSuggestions(ctx, sb))
                                        .executes(context -> {
                                            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "username");
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.player != null && mc.player.connection != null) {
                                                mc.player.connection.sendCommand(cmd + " " + name);
                                            }
                                            return 1;
                                        })
                                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                                .<ClientSuggestionProvider, String>argument("message",
                                                        com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "username");
                                                    String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "message");
                                                    Minecraft mc = Minecraft.getInstance();
                                                    if (mc.player != null && mc.player.connection != null) {
                                                        mc.player.connection.sendCommand(cmd + " " + name + " " + msg);
                                                    }
                                                    return 1;
                                                }))));
            }
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("Failed to register msg commands to dispatcher: " + e.getMessage(), e);
        }
    }

    public static void registerBCommandsToDispatcher(
            com.mojang.brigadier.CommandDispatcher<ClientSuggestionProvider> dispatcher) {
        if (clientDispatcher != null) {
            copyClientCommands(clientDispatcher, dispatcher);
        }
    }

    public static void removeCommand(com.mojang.brigadier.CommandDispatcher<?> dispatcher, String name) {
        try {
            com.mojang.brigadier.tree.RootCommandNode<?> root = dispatcher.getRoot();
            java.lang.reflect.Field childrenField = com.mojang.brigadier.tree.CommandNode.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            java.util.Map<String, ?> children = (java.util.Map<String, ?>) childrenField.get(root);
            children.remove(name);

            java.lang.reflect.Field literalsField = com.mojang.brigadier.tree.CommandNode.class.getDeclaredField("literals");
            literalsField.setAccessible(true);
            java.util.Map<String, ?> literals = (java.util.Map<String, ?>) literalsField.get(root);
            literals.remove(name);

            java.lang.reflect.Field argumentsField = com.mojang.brigadier.tree.CommandNode.class.getDeclaredField("arguments");
            argumentsField.setAccessible(true);
            java.util.Map<String, ?> arguments = (java.util.Map<String, ?>) argumentsField.get(root);
            arguments.remove(name);
        } catch (Throwable t) {
            // Suppress error
        }
    }

    @SuppressWarnings("unchecked")
    public static void copyClientCommands(
            com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> source,
            com.mojang.brigadier.CommandDispatcher<ClientSuggestionProvider> target) {
        try {
            String[] toWipe = { "b", "bomboaddons", "bombo", "tp", "w", "tell", "msg", "p", "party" };
            for (String cmd : toWipe) {
                removeCommand(target, cmd);
            }

            com.mojang.brigadier.tree.RootCommandNode<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> sourceRoot = source.getRoot();
            com.mojang.brigadier.tree.RootCommandNode<ClientSuggestionProvider> targetRoot = target.getRoot();

            for (com.mojang.brigadier.tree.CommandNode<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> child : sourceRoot.getChildren()) {
                com.mojang.brigadier.tree.CommandNode<ClientSuggestionProvider> connectionChild = wrapNode(child);
                if (connectionChild != null) {
                    targetRoot.addChild(connectionChild);
                }
            }
        } catch (Throwable t) {
            Bomboaddons.LOGGER.error("Failed to copy client commands to connection dispatcher", t);
        }
    }

    public static com.mojang.brigadier.tree.CommandNode<ClientSuggestionProvider> wrapNode(
            com.mojang.brigadier.tree.CommandNode<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> node) {
        try {
            com.mojang.brigadier.builder.ArgumentBuilder<ClientSuggestionProvider, ?> builder = null;
            if (node instanceof com.mojang.brigadier.tree.LiteralCommandNode) {
                builder = com.mojang.brigadier.builder.LiteralArgumentBuilder.literal(node.getName());
            } else if (node instanceof com.mojang.brigadier.tree.ArgumentCommandNode) {
                com.mojang.brigadier.tree.ArgumentCommandNode<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, ?> argNode = 
                    (com.mojang.brigadier.tree.ArgumentCommandNode<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, ?>) node;
                com.mojang.brigadier.builder.RequiredArgumentBuilder rawBuilder = com.mojang.brigadier.builder.RequiredArgumentBuilder.argument(node.getName(), argNode.getType());
                if (argNode.getCustomSuggestions() != null) {
                    rawBuilder.suggests((com.mojang.brigadier.suggestion.SuggestionProvider) (Object) wrapSuggestionProvider(argNode.getCustomSuggestions()));
                }
                builder = rawBuilder;
            }
            
            if (builder == null) return null;
            
            builder.requires(source -> true);
            if (node.getCommand() != null) {
                builder.executes(wrapCommand(node.getCommand()));
            }
            
            com.mojang.brigadier.tree.CommandNode<ClientSuggestionProvider> connectionNode = builder.build();
            
            for (com.mojang.brigadier.tree.CommandNode<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> child : node.getChildren()) {
                com.mojang.brigadier.tree.CommandNode<ClientSuggestionProvider> connectionChild = wrapNode(child);
                if (connectionChild != null) {
                    connectionNode.addChild(connectionChild);
                }
            }
            
            return connectionNode;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static com.mojang.brigadier.Command<ClientSuggestionProvider> wrapCommand(
            com.mojang.brigadier.Command<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> clientCommand) {
        if (clientCommand == null) return null;
        return context -> {
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource mockSource = createMockSource(context.getSource());
            java.lang.reflect.Field sourceField;
            Object originalSource;
            try {
                sourceField = com.mojang.brigadier.context.CommandContext.class.getDeclaredField("source");
                sourceField.setAccessible(true);
                originalSource = sourceField.get(context);
                sourceField.set(context, mockSource);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            try {
                return clientCommand.run((com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>) (Object) context);
            } finally {
                try {
                    sourceField.set(context, originalSource);
                } catch (Throwable ignored) {}
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static com.mojang.brigadier.suggestion.SuggestionProvider<ClientSuggestionProvider> wrapSuggestionProvider(
            com.mojang.brigadier.suggestion.SuggestionProvider<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> clientProvider) {
        if (clientProvider == null) return null;
        return (context, builder) -> {
            try {
                net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource mockSource = createMockSource(context.getSource());
                java.lang.reflect.Field sourceField = com.mojang.brigadier.context.CommandContext.class.getDeclaredField("source");
                sourceField.setAccessible(true);
                Object originalSource = sourceField.get(context);
                sourceField.set(context, mockSource);
                try {
                    return clientProvider.getSuggestions((com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>) (Object) context, builder);
                } finally {
                    sourceField.set(context, originalSource);
                }
            } catch (Throwable t) {
                return builder.buildFuture();
            }
        };
    }

    public static net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource createMockSource(
            net.minecraft.client.multiplayer.ClientSuggestionProvider originalSource) {
        return (net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource) java.lang.reflect.Proxy.newProxyInstance(
                BomboaddonsClient.class.getClassLoader(),
                new Class[]{net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource.class},
                (proxy, method, args) -> {
                    Minecraft mc = Minecraft.getInstance();
                    String methodName = method.getName();
                    if (methodName.equals("sendFeedback")) {
                        Component comp = (Component) args[0];
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(comp);
                        }
                        return null;
                    }
                    if (methodName.equals("sendError")) {
                        Component comp = (Component) args[0];
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§c").append(comp));
                        }
                        return null;
                    }
                    if (methodName.equals("getClient")) {
                        return mc;
                    }
                    if (methodName.equals("getPlayer")) {
                        return mc.player;
                    }
                    if (methodName.equals("getWorld") || methodName.equals("getLevel")) {
                        return mc.level;
                    }
                    return method.invoke(originalSource, args);
                }
        );
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

    public static String normalizeChannel(String channel) {
        if (channel == null)
            return "";
        String lower = channel.toLowerCase().trim();
        if (lower.equals("a") || lower.equals("all")) {
            return "a";
        }
        if (lower.equals("g") || lower.equals("guild")) {
            return "g";
        }
        if (lower.equals("p") || lower.equals("party")) {
            return "p";
        }
        if (lower.equals("o") || lower.equals("officer")) {
            return "o";
        }
        if (lower.equals("c") || lower.equals("coop")) {
            return "c";
        }
        return lower;
    }

    public static void processChatMessage(String rawMessage) {
        if (rawMessage == null)
            return;
        try {
            TabCompletionManager.onChatMessage(rawMessage);
        } catch (Throwable t) {
        }
        String cleanMessage = rawMessage.replaceAll("§.", "").trim().toLowerCase();

        // Party command automation parsing
        if (BomboConfig.get().partyCommandsEnabled) {
            java.util.List<String> prefixes = new java.util.ArrayList<>();
            String rawPrefixes = BomboConfig.get().partyCommandPrefixes;
            if (rawPrefixes == null || rawPrefixes.trim().isEmpty()) {
                prefixes.add("!");
            } else {
                for (String p : rawPrefixes.split(",")) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) {
                        prefixes.add(trimmed);
                    }
                }
            }
            if (prefixes.isEmpty()) {
                prefixes.add("!");
            }

            StringBuilder prefixRegex = new StringBuilder("(");
            for (int i = 0; i < prefixes.size(); i++) {
                if (i > 0)
                    prefixRegex.append("|");
                prefixRegex.append(java.util.regex.Pattern.quote(prefixes.get(i)));
            }
            prefixRegex.append(")");

            String regexPattern = "^party\\s*>\\s*(?:\\[[^\\]]+\\]\\s*)?(\\w+)\\s*:\\s*" + prefixRegex.toString()
                    + "(\\w+)(?:\\s+(.+))?$";
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile(regexPattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanMessage);
            if (m.find()) {
                String senderName = m.group(1);
                try {
                    String rawNoFormat = rawMessage.replaceAll("§.", "").trim();
                    java.util.regex.Matcher mRaw = java.util.regex.Pattern
                            .compile(regexPattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(rawNoFormat);
                    if (mRaw.find()) {
                        senderName = mRaw.group(1);
                    }
                } catch (Exception ignored) {
                }

                String command = m.group(3).toLowerCase();
                String args = m.group(4) != null ? m.group(4).trim() : "";

                if (command.equals("timer") && BomboConfig.get().partyCommandTimer) {
                    if (!args.isEmpty()) {
                        long durationMs = CustomTimerManager.parseTimeMs(args);
                        if (durationMs > 0) {
                            CustomTimerManager.startTimer(senderName, durationMs, true);
                            Bomboaddons.sendMessage("&8[&bBomboAddons&8] &7Started a &e" + args + " &7timer for &a"
                                    + senderName + "&7.");
                        }
                    }
                } else if (command.equals("warp") && BomboConfig.get().partyCommandWarp) {
                    pendingCommands.add(new PendingCommand("party warp", System.currentTimeMillis() + 300));
                } else if (command.equals("psa") && BomboConfig.get().partyCommandPsa) {
                    pendingCommands
                            .add(new PendingCommand("party settings allinvite", System.currentTimeMillis() + 300));
                } else {
                    for (BomboConfig.CustomPartyCommand cpc : BomboConfig.get().customPartyCommands) {
                        if (cpc.enabled && cpc.triggerText.equalsIgnoreCase(command)) {
                            String finalCmd = cpc.commandToRun;
                            if (!args.isEmpty()) {
                                if (finalCmd.contains("%args%")) {
                                    finalCmd = finalCmd.replace("%args%", args);
                                } else {
                                    finalCmd = finalCmd + " " + args;
                                }
                            }
                            pendingCommands.add(new PendingCommand(finalCmd, System.currentTimeMillis() + 300));
                            break;
                        }
                    }
                }
            }
        }

        if (cleanMessage.contains("[boss] storm: energy heed my call!") ||
                cleanMessage.contains("[boss] storm: thunder let me be your catalyst!")) {
            DungeonPadTimers.onBossMessage();
        }
        if (cleanMessage.startsWith("co-op >") && cleanMessage.contains("noreconnect")) {
            tempDisableReconnect = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component
                        .literal("§8[§bBomboAddons§8] §cAuto-reconnect delayed to 5 minutes by co-op message!"));
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
                String triggerText = trigger.triggerText.replaceAll("§.", "").trim();
                if (triggerText.isEmpty()) continue;
                
                boolean hasVars = triggerText.contains("${");
                if (hasVars) {
                    String[] parts = triggerText.split("\\$\\{[a-zA-Z0-9_]+\\}", -1);
                    java.util.regex.Matcher varMatcher = java.util.regex.Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}").matcher(triggerText);
                    StringBuilder regexBuilder = new StringBuilder();
                    java.util.List<String> varNames = new java.util.ArrayList<>();
                    int partIdx = 0;
                    while (varMatcher.find()) {
                        regexBuilder.append(java.util.regex.Pattern.quote(parts[partIdx]));
                        varNames.add(varMatcher.group(1));
                        
                        // If this is the last variable and there's no trailing literal, use greedy match
                        if (partIdx == parts.length - 1 || (partIdx == parts.length - 2 && parts[partIdx + 1].isEmpty())) {
                            regexBuilder.append("(.*)");
                        } else {
                            regexBuilder.append("(.*?)");
                        }
                        partIdx++;
                    }
                    if (partIdx < parts.length && !parts[partIdx].isEmpty()) {
                        regexBuilder.append(java.util.regex.Pattern.quote(parts[partIdx]));
                        regexBuilder.append(".*"); // match the rest of the line to be safe
                    }
                    
                    try {
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regexBuilder.toString(), java.util.regex.Pattern.CASE_INSENSITIVE);
                        java.util.regex.Matcher matcher = pattern.matcher(cleanMessage);
                        if (matcher.find()) {
                            String cmd = trigger.commandToRun;
                            String title = trigger.titleToShow;
                            
                            for (int i = 0; i < varNames.size(); i++) {
                                String varName = varNames.get(i);
                                String value = matcher.group(i + 1);
                                if (cmd != null) cmd = cmd.replace("${" + varName + "}", value);
                                if (title != null) title = title.replace("${" + varName + "}", value);
                            }
                            
                            if (cmd != null && !cmd.isEmpty()) executeTracked(cmd);
                            if (title != null && !title.isEmpty()) {
                                Minecraft mc = Minecraft.getInstance();
                                String formattedTitle = title.replace('&', '§');
                                mc.execute(() -> {
                                    mc.gui.setTimes(10, 70, 20);
                                    mc.gui.setTitle(Component.literal(formattedTitle));
                                });
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    String cleanTrigger = triggerText.toLowerCase();
                    if (cleanMessage.toLowerCase().contains(cleanTrigger)) {
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
                java.net.URL url = new java.net.URI(
                        "https://sbecommands-api.icarusphantom.dev/v1/sbecommands/nw/" + username).toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int status = conn.getResponseCode();
                if (status == 200) {
                    try (java.io.InputStreamReader reader = new java.io.InputStreamReader(conn.getInputStream(),
                            "UTF-8")) {
                        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseReader(reader)
                                .getAsJsonObject();
                        if (obj.has("data")) {
                            com.google.gson.JsonObject data = obj.getAsJsonObject("data");
                            if (data.has("rank")) {
                                String rank = data.get("rank").getAsString();
                                if (rank != null) {
                                    RankCache.setRank(username, rank);
                                    Minecraft.getInstance().execute(() -> {
                                        source.sendFeedback(Component.literal(PREFIX + "§7API Rank for §e" + username
                                                + "§7: " + (rank.isEmpty() ? "§7None" : rank)));
                                    });
                                    return;
                                }
                            }
                        }
                    }
                }
                Minecraft.getInstance().execute(() -> {
                    source.sendFeedback(Component.literal(
                            PREFIX + "§cFailed to fetch rank for §e" + username + " §7(Status: " + status + ")"));
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    source.sendFeedback(Component
                            .literal(PREFIX + "§cError fetching rank for §e" + username + "§c: " + e.getMessage()));
                });
            }
        }, "Rank-Fetch-Command-" + username).start();
    }

    private static void findAndClickYes(Component component) {
        if (component == null)
            return;
        Style style = component.getStyle();
        ClickEvent clickEvent = style.getClickEvent();

        if (BomboConfig.get().debugChat) {
            DebugUtils.debug("chat", "Inspecting Component: text=\"" + component.getString() + "\", style=" + style
                    + ", clickEvent=" + clickEvent);
        }

        if (clickEvent != null) {
            try {
                ClickEvent.Action actionObj = null;
                String valueStr = null;

                Class<?> clazz = clickEvent.getClass();
                if (BomboConfig.get().debugChat) {
                    DebugUtils.debug("chat", "ClickEvent Class: " + clazz.getName());
                }

                while (clazz != null && clazz != Object.class) {
                    // 1. Scan fields via reflection
                    for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                        try {
                            field.setAccessible(true);
                            Object val = field.get(clickEvent);
                            if (BomboConfig.get().debugChat) {
                                DebugUtils.debug("chat", "Field: " + field.getName() + " of type "
                                        + field.getType().getName() + " = " + val);
                            }
                            if (val instanceof ClickEvent.Action) {
                                actionObj = (ClickEvent.Action) val;
                            } else if (val instanceof String) {
                                valueStr = (String) val;
                            }
                        } catch (Throwable t) {
                            if (BomboConfig.get().debugChat) {
                                DebugUtils.debug("chat", "Field error (" + field.getName() + "): " + t.getMessage());
                            }
                        }
                    }

                    // 2. Scan zero-argument methods via reflection
                    for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                        if (method.getParameterCount() == 0 && !method.getName().equals("toString")
                                && !method.getName().equals("name")) {
                            try {
                                method.setAccessible(true);
                                Object val = method.invoke(clickEvent);
                                if (BomboConfig.get().debugChat) {
                                    DebugUtils.debug("chat", "Method: " + method.getName() + " returning "
                                            + method.getReturnType().getName() + " = " + val);
                                }
                                if (val instanceof ClickEvent.Action) {
                                    actionObj = (ClickEvent.Action) val;
                                } else if (val instanceof String) {
                                    valueStr = (String) val;
                                }
                            } catch (Throwable t) {
                                if (BomboConfig.get().debugChat) {
                                    DebugUtils.debug("chat",
                                            "Method error (" + method.getName() + "): " + t.getMessage());
                                }
                            }
                        }
                    }
                    clazz = clazz.getSuperclass();
                }

                if (BomboConfig.get().debugChat) {
                    DebugUtils.debug("chat", "Resolved Action: " + actionObj + ", Resolved Value: " + valueStr);
                }

                // Fallback: check if class name contains the type of command
                if (actionObj == null) {
                    String className = clickEvent.getClass().getSimpleName().toLowerCase();
                    if (className.contains("runcommand")) {
                        actionObj = ClickEvent.Action.RUN_COMMAND;
                    }
                }

                if (actionObj == ClickEvent.Action.RUN_COMMAND && valueStr != null) {
                    String lower = valueStr.toLowerCase();
                    if (lower.startsWith("/chatprompt ") && lower.endsWith(" yes")) {
                        executeTracked(valueStr);
                        Bomboaddons.sendMessage("&8[&bBomboAddons&8] &aAuto-accepted Trevor's quest!");
                        return;
                    }
                }
            } catch (Throwable t) {
                if (BomboConfig.get().debugChat) {
                    DebugUtils.debug("chat", "Reflection critical error: " + t.getMessage());
                }
                t.printStackTrace();
            }
        }
        for (Component sibling : component.getSiblings()) {
            findAndClickYes(sibling);
        }
    }

    public static void registerMultiPlayerPartyCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher) {
        String[] cmdNames = { "p", "party", "v", "w", "tell" };
        for (String name : cmdNames) {
            var argsNode = net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .suggests(TabCompletionManager::getUsernameSuggestions)
                .executes(context -> {
                    String args = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "args");
                    String actualName = name.equals("v") ? "visit" : name.equals("p") ? "party" : (name.equals("w") || name.equals("tell")) ? "msg" : name;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && mc.player.connection != null) {
                        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(actualName + " " + args));
                    }
                    return 1;
                });

            var builder = net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(name)
                .executes(context -> {
                    String actualName = name.equals("v") ? "visit" : name.equals("p") ? "party" : (name.equals("w") || name.equals("tell")) ? "msg" : name;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && mc.player.connection != null) {
                        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(actualName));
                    }
                    return 1;
                })
                .then(argsNode);

            dispatcher.register(builder);
        }
    }
}
