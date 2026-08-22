package me.bombo.bomboaddons;

import java.util.Locale;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.world.phys.BlockHitResult;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.bombo.bomboaddons.auth.AccountManager;
import me.bombo.bomboaddons.eggfinder.EggAuth;
import me.bombo.bomboaddons.eggfinder.EggFinder;
import me.bombo.bomboaddons.eggfinder.EggWebSocket;
import me.bombo.bomboaddons.features.StorageTracker;
import me.bombo.bomboaddons.features.TextureToggleManager;
import me.bombo.bomboaddons.features.dungeons.ClearInfoHUD;
import me.bombo.bomboaddons.features.profile.ProfileFetcher;
import me.bombo.bomboaddons.gui.CustomSoundsScreen;
import me.bombo.bomboaddons.gui.GlobalStorageScreen;
import me.bombo.bomboaddons.gui.ProfileViewerScreen;
import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import me.bombo.bomboaddons.kuudra.pearls.Pearls;
import me.bombo.bomboaddons.util.BomboApiUrl;
import me.bombo.bomboaddons.util.ChatMessageTracker;
import me.bombo.bomboaddons.util.IChatComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

@Environment(EnvType.CLIENT)
public class BomboaddonsClient implements ClientModInitializer {
   public static final java.util.List<String> commandHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

   public static void recordCommand(String cmd) {
      if (cmd == null || cmd.trim().isEmpty()) return;
      if (commandHistory.size() > 0 && commandHistory.get(commandHistory.size() - 1).equalsIgnoreCase(cmd)) return;
      commandHistory.add(cmd);
      if (commandHistory.size() > 200) commandHistory.remove(0);
   }

    public static class NpcOptionItem {
        public String text;
        public String color;
        public String command;
        public NpcOptionItem(String text, String color, String command) {
            this.text = text;
            this.color = color;
            this.command = command;
        }
    }
    public static final Map<String, Set<String>> npcClickedOptions = new HashMap<>();
   public static final List<PendingCommand> pendingCommands = new CopyOnWriteArrayList();
   public static final Set<String> clickedNpcOptions = new HashSet();
   public static final Set<String> clickedNpcTextOptions = new HashSet();
   private static final String PREFIX = "§8[§3Bombo§8]§r ";
   private static boolean openGuiNextTick = false;
   private static String pendingConfigSearch = null;
   private static int lastInventoryStateId = -1;
   private static boolean openHudMoveNextTick = false;
   private static boolean openCustomizeGuiNextTick = false;
   public static CommandDispatcher<FabricClientCommandSource> clientDispatcher;
   public static String currentArea = "None";
   public static String currentSubArea = "None";
   public static String currentHypixelChannel = "a";
   private static int menuTickCount = 0;
   public static ServerData lastServerData = null;
   public static Button activeReconnectBtn = null;
   public static Screen activeParent = null;
   public static int autoReconnectTicks = -1;
   public static boolean tempDisableReconnect = false;
   public static String locrawServer = "";
   public static String locrawGametype = "";
   public static String locrawMode = "";
   public static String locrawMap = "";
   public static long lastLocrawTime = 0L;
   public static long lastHoppityCallHeaderTime = 0L;
   public static int locrawDelayTicks = -1;
   public static int expectingLocrawCount = 0;
   public static String lastDetectedCommand = null;

   public static void trackCommandFromChat(String rawMessage) {
      if (rawMessage == null) return;
      String clean = rawMessage.replaceAll("§[0-9a-fk-orxX]", "").trim();
      int slashIdx = clean.indexOf("/");
      if (slashIdx != -1) {
         String possibleCmd = clean.substring(slashIdx).trim();
         if (!clean.contains("://") && possibleCmd.matches("^/[a-zA-Z0-9_-]+.*")) {
            lastDetectedCommand = possibleCmd;
         }
      }
   }

   private static void openProfileViewer(String username) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.sendSystemMessage(Component.literal("§eFetching profile for §b" + username + "§e..."));
      }

      CompletableFuture<me.bombo.bomboaddons.features.profile.ProfileFetcher.ProfileData> var10000 = me.bombo.bomboaddons.features.profile.ProfileFetcher.fetchProfile(username);
      Consumer<me.bombo.bomboaddons.features.profile.ProfileFetcher.ProfileData> var10001 = (data) -> {
         if (data == null) {
            if (mc.player != null) {
               mc.player.sendSystemMessage(Component.literal("§cFailed to fetch profile for " + username + "!"));
            }
         } else {
            mc.setScreen(new me.bombo.bomboaddons.gui.ProfileViewerScreen(data));
         }

      };
      Objects.requireNonNull(mc);
      var10000.thenAcceptAsync(var10001, mc::execute);
   }

   public void onInitializeClient() {
      BomboConfig.load();
      ChatModifier.load();
      WaypointManager.init();
      StructureScanner.loadPatterns();
      ComposterHud.init();
      TabWidgetHud.init();
      HoppityHud.init();
      checkResourcePackStartup();
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
         BlockPos pos = hitResult.getBlockPos();
         if (!BlockHighlight.targetChestPosList.isEmpty() && BlockHighlight.targetChestPosList.contains(pos)) {
            BlockHighlight.targetChestPosList.remove(pos);

            try {
               BlockState state = world.getBlockState(pos);
               if (state.getBlock() instanceof ChestBlock) {
                  ChestType type = (ChestType)state.getValue(BlockStateProperties.CHEST_TYPE);
                  if (type != ChestType.SINGLE) {
                     Direction connectedDir = ChestBlock.getConnectedDirection(state);
                     BlockHighlight.targetChestPosList.remove(pos.relative(connectedDir));
                  }
               }
            } catch (Exception var10) {
            }
         }

         BlockState state = world.getBlockState(pos);
         if (state.getBlock() instanceof ChestBlock) {
            ChestType type = (ChestType)state.getValue(BlockStateProperties.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
               Direction connectedDir = ChestBlock.getConnectedDirection(state);
               BlockPos otherPos = pos.relative(connectedDir);
               if (otherPos.compareTo(pos) < 0) {
                  pos = otherPos;
               }
            } else {
               for(Direction d : Plane.HORIZONTAL) {
                  BlockPos adj = pos.relative(d);
                  if (world.getBlockState(adj).getBlock() instanceof ChestBlock) {
                     if (adj.compareTo(pos) < 0) {
                        pos = adj;
                     }
                     break;
                  }
               }
            }
         }

         StorageTracker.lastClickedBlockPos = pos;
         return InteractionResult.PASS;
      });
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientLifecycleEvents.ClientStopping)(client) -> {
         BomboConfig.save();
         StorageTracker.save();
      });
      ScreenEvents.BEFORE_INIT.register((ScreenEvents.BeforeInit)(client, screen, scaledWidth, scaledHeight) -> ScreenMouseEvents.allowMouseClick(screen).register((ScreenMouseEvents.AllowMouseClick)(screen1, event) -> {
            double mouseX = event.x();
            double mouseY = event.y();
            int button = event.button();
            BomboConfig.Settings s = BomboConfig.get();
            if (s.hoppityHud && HoppityHud.onMouseClick(mouseX, mouseY, button)) {
               return false;
            } else {
               if (s.diceTracker && DiceTracker.shouldShowHud()) {
                  int w = (int)(260.0F * s.diceHudScale);
                  int h = (int)(52.0F * s.diceHudScale);
                  if (mouseX >= (double)s.diceHudX && mouseX <= (double)(s.diceHudX + w) && mouseY >= (double)s.diceHudY && mouseY <= (double)(s.diceHudY + h) && button == 0) {
                     s.diceDisplayMode = s.diceDisplayMode.equals("Lifetime") ? "Current" : "Lifetime";
                     BomboConfig.save();
                     return false;
                  }
               }

               return true;
            }
         }));
      AccountManager.init();
      if (AccountManager.currentAccount != null) {
         AccountManager.refreshAccount(AccountManager.currentAccount).thenAccept((refreshed) -> {
            if (refreshed != null) {
               AccountManager.setSession(refreshed);
            }

         });
      }

      StorageTracker.init();
      TextureToggleManager.INSTANCE.init();
      StopwatchManager.init();
      AlphaTrackerHud.init();
      Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
      Thread.UncaughtExceptionHandler customHandler = (thread, throwable) -> {
         try {
            File file = new File("crash_exception.log");
            PrintWriter pw = new PrintWriter(new FileWriter(file, true));

            try {
               pw.println("=== UNCAUGHT EXCEPTION ===");
               pw.println("Thread: " + thread.getName());
               throwable.printStackTrace(pw);
               pw.println("==========================");
            } catch (Throwable var8) {
               try {
                  pw.close();
               } catch (Throwable x2) {
                  var8.addSuppressed(x2);
               }

               throw var8;
            }

            pw.close();
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

         for(Object obj : Class.forName("net.minecraft.client.multiplayer.ServerData$Type").getEnumConstants()) {
            System.out.println("Enum constant: " + String.valueOf(obj));
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
               String processed = ChromaTextHelper.processChroma(message).replace("&", "§");
               mc.player.sendSystemMessage(Component.literal(processed));
            }

         });
      };

      try {
         ClientCommandRegistrationCallback.EVENT.register((ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> {
            clientDispatcher = dispatcher;
            registerMultiPlayerPartyCommands(dispatcher);
            registerAllAliases();
            dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("bombo_highlight_slot").then(ClientCommands.argument("slot", IntegerArgumentType.integer()).then(ClientCommands.argument("command", StringArgumentType.greedyString()).executes((context) -> {
               int slot = IntegerArgumentType.getInteger(context, "slot");
               String cmd = StringArgumentType.getString(context, "command");
               SlotHighlight.setTargetSlot(slot, -1442775296);
               String toSend = cmd.startsWith("/") ? cmd.substring(1) : cmd;
               Minecraft.getInstance().player.connection.sendCommand(toSend);
               return 1;
            }))));

            registerAllAliases();

            try {
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("clicks").executes((context) -> {
                  ClickLogic.listTargets((FabricClientCommandSource)context.getSource());
                  return 1;
               }));
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("click").then(ClientCommands.literal("list").executes((context) -> {
                  ClickLogic.listTargets((FabricClientCommandSource)context.getSource());
                  return 1;
               }))).then(ClientCommands.literal("debug").executes((context) -> {
                  ClickLogic.toggleDebug();
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Click Debug: " + (ClickLogic.isDebugMode() ? "§aON" : "§cOFF")));
                  return 1;
               }))).then(ClientCommands.literal("add").then(ClientCommands.argument("item", StringArgumentType.string()).then(ClientCommands.argument("gui", StringArgumentType.string()).then(ClientCommands.argument("key", StringArgumentType.string()).then(((RequiredArgumentBuilder)ClientCommands.argument("type", StringArgumentType.string()).executes((context) -> {
                  String item = StringArgumentType.getString(context, "item");
                  String gui = StringArgumentType.getString(context, "gui");
                  String key = StringArgumentType.getString(context, "key");
                  String type = StringArgumentType.getString(context, "type");
                  ClickLogic.setTarget(item, gui, key, type, false);
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAdded click target for §e" + item));
                  return 1;
               })).then(ClientCommands.argument("auto", BoolArgumentType.bool()).executes((context) -> {
                  String item = StringArgumentType.getString(context, "item");
                  String gui = StringArgumentType.getString(context, "gui");
                  String key = StringArgumentType.getString(context, "key");
                  String type = StringArgumentType.getString(context, "type");
                  boolean auto = BoolArgumentType.getBool(context, "auto");
                  ClickLogic.setTarget(item, gui, key, type, auto);
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAdded " + (auto ? "auto " : "") + "click target for §e" + item));
                  return 1;
               })))))))).then(ClientCommands.literal("remove").then(ClientCommands.argument("id", StringArgumentType.string()).executes((context) -> {
                  String id = StringArgumentType.getString(context, "id");

                  try {
                     int index = Integer.parseInt(id);
                     ClickLogic.removeTarget(index);
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aRemoved click target §e#" + index));
                  } catch (Exception var3) {
                     ClickLogic.removeTargetById(id);
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aRemoved click target for §e" + id));
                  }

                  return 1;
               }))));
            } catch (Throwable t) {
               Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register click commands!", t);
            }

            try {
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("timer").executes((context) -> {
                  StopwatchManager.start();
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aStopwatch started! Open inventory to Pause or Stop."));
                  return 1;
               })).then(((RequiredArgumentBuilder)ClientCommands.argument("arg1", StringArgumentType.word()).executes((context) -> {
                  String arg1 = StringArgumentType.getString(context, "arg1");
                  if (!arg1.equalsIgnoreCase("clear") && !arg1.equalsIgnoreCase("reset")) {
                     long durationMs = CustomTimerManager.parseTimeMs(arg1);
                     if (durationMs > 0L) {
                        CustomTimerManager.startTimer("Timer", durationMs);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Started default timer for §e" + arg1 + "§7."));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendError(Component.literal("§8[§bBomboAddons§8] §cInvalid duration format: " + arg1));
                     }

                     return 1;
                  } else {
                     CustomTimerManager.clearTimers();
                     Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §cCleared all active custom timers!"));
                     return 1;
                  }
               })).then(ClientCommands.argument("arg2", StringArgumentType.word()).executes((context) -> {
                  String name = StringArgumentType.getString(context, "arg1");
                  String durationStr = StringArgumentType.getString(context, "arg2");
                  long durationMs = CustomTimerManager.parseTimeMs(durationStr);
                  if (durationMs > 0L) {
                     CustomTimerManager.startTimer(name, durationMs);
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Started timer '§e" + name + "§7' for §e" + durationStr + "§7."));
                  } else {
                     ((FabricClientCommandSource)context.getSource()).sendError(Component.literal("§8[§bBomboAddons§8] §cInvalid duration format: " + durationStr));
                  }

                  return 1;
               }))));
            } catch (Throwable t) {
               Bomboaddons.LOGGER.error("[BomboAddons] Failed to register timer command!", t);
            }

            try {
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("lf").executes((context) -> {
                  System.out.println("[Bombo] Executing /lf (help)");
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Usage: /lf <username> [query]"));
                  return 1;
               })).then(((RequiredArgumentBuilder)ClientCommands.argument("username", StringArgumentType.string()).suggests((ctx, sb) -> TabCompletionManager.getUsernameSuggestions(ctx, sb)).executes((context) -> {
                  String user = StringArgumentType.getString(context, "username");
                  System.out.println("[Bombo] Executing /lf for user: " + user);
                  LF.show(user, "", false);
                  return 1;
               })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                  String user = StringArgumentType.getString(context, "username");
                  String query = StringArgumentType.getString(context, "query");
                  System.out.println("[Bombo] Executing /lf for user: " + user + " with query: " + query);
                  LF.show(user, query, false);
                  return 1;
               }))));
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("lfc").executes((context) -> {
                  System.out.println("[Bombo] Executing /lfc (help)");
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Usage: /lfc <username> [query]"));
                  return 1;
               })).then(((RequiredArgumentBuilder)ClientCommands.argument("username", StringArgumentType.string()).suggests((ctx, sb) -> TabCompletionManager.getUsernameSuggestions(ctx, sb)).executes((context) -> {
                  String user = StringArgumentType.getString(context, "username");
                  System.out.println("[Bombo] Executing /lfc for user: " + user);
                  LF.show(user, "", true);
                  return 1;
               })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                  String user = StringArgumentType.getString(context, "username");
                  String query = StringArgumentType.getString(context, "query");
                  System.out.println("[Bombo] Executing /lfc for user: " + user + " with query: " + query);
                  LF.show(user, query, true);
                  return 1;
               }))));
               String[] sbeSubs = new String[]{"nw", "cata", "skills", "slayer", "trophyfish", "crimson", "crimsom"};

               for(String s : sbeSubs) {
                  String commandName = s.equals("crimsom") ? "crimson" : s;
                  dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(s).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        SBECommands.handleCommand(commandName, mc.player.getName().getString(), (String)null);
                     }

                     return 1;
                  })).then(((RequiredArgumentBuilder)ClientCommands.argument("username", StringArgumentType.string()).suggests((ctx, sb) -> TabCompletionManager.getUsernameSuggestions(ctx, sb)).executes((context) -> {
                     String name = StringArgumentType.getString(context, "username");
                     SBECommands.handleCommand(commandName, name, (String)null);
                     return 1;
                  })).then(ClientCommands.argument("profile", StringArgumentType.word()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "username");
                     String profile = StringArgumentType.getString(context, "profile");
                     SBECommands.handleCommand(commandName, name, profile);
                     return 1;
                  }))));
               }

               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("lb").executes((context) -> {
                  System.out.println("[Bombo] Executing /lb");
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null) {
                     String name = mc.player.getName().getString();
                     System.out.println("[Bombo] /lb for self: " + name);
                     LF.show(name, "", false);
                  }

                  return 1;
               })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                  String query = StringArgumentType.getString(context, "query");
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null) {
                     String name = mc.player.getName().getString();
                     System.out.println("[Bombo] /lb for self: " + name + " with query: " + query);
                     LF.show(name, query, false);
                  }

                  return 1;
               })));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("bitem").executes((context) -> {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player == null) {
                     return 1;
                  } else {
                     ItemStack stack = mc.player.getMainHandItem();
                     if (stack.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§c[Bombo] You must hold an item."));
                        return 1;
                     } else {
                        Item originalItem = stack.getItem();
                        String skyblockId = SkyblockUtils.getInternalIdRaw(stack);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§6=== Item Debug ==="));
                        FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
                        String var10001 = stack.getHoverName().getString();
                        var10000.sendFeedback(Component.literal("§7Name: §f" + var10001));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7SkyBlock ID: §e" + (skyblockId.isEmpty() ? "None" : skyblockId)));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Vanilla Item: §c" + BuiltInRegistries.ITEM.getKey(originalItem).toString()));
                        String currentSkinValue = null;
                        String currentSkinSignature = null;
                        ResolvableProfile profile = (ResolvableProfile)stack.get(DataComponents.PROFILE);
                        if (profile != null && profile.partialProfile() != null && profile.partialProfile().properties() != null) {
                           for(Property prop : profile.partialProfile().properties().get("textures")) {
                              if (prop != null) {
                                 currentSkinValue = prop.value();
                                 currentSkinSignature = prop.signature();
                                 break;
                              }
                           }
                        }

                        if (currentSkinValue != null) {
                           var10000 = (FabricClientCommandSource)context.getSource();
                           var10001 = currentSkinValue.length() > 30 ? currentSkinValue.substring(0, 30) + "..." : currentSkinValue;
                           var10000.sendFeedback(Component.literal("§7Current Skin Value: §d" + var10001));
                        }

                        if (currentSkinSignature != null) {
                           String var18 = currentSkinSignature.length() > 30 ? currentSkinSignature.substring(0, 30) + "..." : currentSkinSignature;
                           MutableComponent sigComp = Component.literal("§7Current Head Signature: §d" + var18);
                           sigComp.setStyle(sigComp.getStyle().withClickEvent(new ClickEvent.CopyToClipboard(currentSkinSignature)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy full signature"))));
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(sigComp);
                        } else if (stack.is(Items.PLAYER_HEAD)) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Current Head Signature: §cNone"));
                        }

                        if (!skyblockId.isEmpty()) {
                           String nrpModel = TextureToggleManager.INSTANCE.getRawModel(skyblockId);
                           String nrpValue = TextureToggleManager.INSTANCE.getRawValue(skyblockId);
                           if (nrpModel != null) {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Custom Pack Model: §e" + nrpModel));
                           }

                           if (nrpValue != null) {
                              MutableComponent nrpValComp = Component.literal("§7Custom Pack Skin Value: §d" + nrpValue);
                              nrpValComp.setStyle(nrpValComp.getStyle().withClickEvent(new ClickEvent.CopyToClipboard(nrpValue)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy full skin value"))));
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(nrpValComp);
                           }

                           SkyblockItemManager.SkyblockItemInfo info = SkyblockItemManager.getInfo(skyblockId);
                           if (info != null) {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Expected Material (API): §a" + info.material));
                              Item overrideItem = SkyblockItemManager.getOverrideItem(info.material);
                              if (overrideItem != null) {
                                 ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Overridden Item: §b" + BuiltInRegistries.ITEM.getKey(overrideItem).toString()));
                              } else {
                                 ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Overridden Item: §cNone (Failed to resolve)"));
                              }

                              if (info.skinValue != null) {
                                 var10000 = (FabricClientCommandSource)context.getSource();
                                 var10001 = info.skinValue.length() > 30 ? info.skinValue.substring(0, 30) + "..." : info.skinValue;
                                 var10000.sendFeedback(Component.literal("§7Expected Skin Value: §a" + var10001));
                              }

                              if (info.skinSignature != null) {
                                 String var20 = info.skinSignature.length() > 30 ? info.skinSignature.substring(0, 30) + "..." : info.skinSignature;
                                 MutableComponent expSigComp = Component.literal("§7Expected Head Signature: §a" + var20);
                                 expSigComp.setStyle(expSigComp.getStyle().withClickEvent(new ClickEvent.CopyToClipboard(info.skinSignature)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy full signature"))));
                                 ((FabricClientCommandSource)context.getSource()).sendFeedback(expSigComp);
                              } else if ("SKULL_ITEM".equalsIgnoreCase(info.material)) {
                                 ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Expected Head Signature: §cNone (API)"));
                              }
                           } else {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Expected Material (API): §cNot found in database"));
                           }
                        }

                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§6=================="));
                        return 1;
                     }
                  }
               }));
            } catch (Throwable t) {
               System.err.println("[Bombo] FAILED to register core search commands!");
               t.printStackTrace();
            }

            try {
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("server").then(ClientCommands.argument("ip", StringArgumentType.greedyString()).executes((context) -> {
                  String ip = StringArgumentType.getString(context, "ip");
                  Minecraft mc = Minecraft.getInstance();
                  mc.execute(() -> {
                     if (mc.getConnection() != null) {
                        mc.getConnection().getConnection().disconnect(Component.literal("Connecting to " + ip));
                     }

                     ServerAddress address = ServerAddress.parseString(ip);
                     ServerData server = new ServerData("Server", ip, Type.OTHER);
                     ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, address, server, false, (TransferState)null);
                  });
                  return 1;
               })));
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("afk").executes((ctx) -> {
                  AFKManager.toggleAfk((String)null);
                  return 1;
               })).then(ClientCommands.argument("island", StringArgumentType.greedyString()).executes((ctx) -> {
                  String island = StringArgumentType.getString(ctx, "island");
                  AFKManager.toggleAfk(island);
                  return 1;
               })));
            } catch (Throwable t) {
               System.err.println("[Bombo] FAILED to register server command!");
               t.printStackTrace();
            }

            try {
               LiteralArgumentBuilder<FabricClientCommandSource> bBuilder = ClientCommands.literal("b");
               LiteralArgumentBuilder<FabricClientCommandSource> baBuilder = ClientCommands.literal("bomboaddons");
               LiteralArgumentBuilder<FabricClientCommandSource> bomboBuilder = ClientCommands.literal("bombo");
               Consumer<LiteralArgumentBuilder<FabricClientCommandSource>> setupCommands = (builder) -> {
                  builder.executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     mc.execute(() -> mc.setScreen(BomboConfigGUI.create()));
                     return 1;
                  });
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("history").executes((context) -> {
                     return showCommandHistory((FabricClientCommandSource)context.getSource(), 5, null);
                  })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                     String q = StringArgumentType.getString(context, "query").trim();
                     if (q.startsWith("|")) q = q.substring(1).trim();
                     try {
                        int count = Integer.parseInt(q);
                        return showCommandHistory((FabricClientCommandSource)context.getSource(), count, null);
                     } catch (NumberFormatException ignored) {
                        return showCommandHistory((FabricClientCommandSource)context.getSource(), 20, q);
                     }
                  })));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("behighlight").then(ClientCommands.literal("add").then(ClientCommands.argument("mob", StringArgumentType.greedyString()).executes((context) -> {
                     String mob = StringArgumentType.getString(context, "mob").trim();
                     return handleBestiaryAddCommand((FabricClientCommandSource)context.getSource(), mob, null);
                  })))).then(ClientCommands.argument("mob", StringArgumentType.greedyString()).executes((context) -> {
                     String mob = StringArgumentType.getString(context, "mob").trim();
                     return handleBestiaryAddCommand((FabricClientCommandSource)context.getSource(), mob, null);
                  })));
                  builder.then(ClientCommands.literal("help").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8----------------- §b[BomboAddons Help] §8-----------------"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Hover over any command to see what it does! Click to suggest it.\n"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b", "/b", "Opens the main config GUI.").append(Component.literal(" §7- Opens the main config GUI")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b help", "/b help", "Shows this help menu.").append(Component.literal(" §7- Shows this help menu")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b prof", "/b prof", "Opens config GUI directly to Profile Binds.").append(Component.literal(" §7- Opens Profile Binds config")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b bw", "/b bw", "Opens config GUI directly to Bedwars settings.").append(Component.literal(" §7- Opens Bedwars ESP settings")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b gui", "/b gui", "Opens the HUD Editor to reposition overlays.").append(Component.literal(" §7- Opens the HUD Editor")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b api", "/b api", "Reloads lowest BIN prices and checks status.").append(Component.literal(" §7- Reloads and checks APIs")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b ks", "/b ks", "Resets active Garden Movement states.").append(Component.literal(" §7- Resets Garden Movement states")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b sugarcane", "/b sugarcane", "Toggles Sugar Cane mode for lane warnings.").append(Component.literal(" §7- Toggles Sugar Cane mode")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b highlight", "/b highlight", "Configures persistent entity highlights.").append(Component.literal(" §7- Persistent Highlights config")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b anvil", "/b anvil", "Configures persistent auto-combine goals.").append(Component.literal(" §7- Anvil Auto-Combine config")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b pt", "/b pt", "Opens Playtime statistics GUI.").append(Component.literal(" §7- Opens Playtime GUI")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b update", "/b update", "Manually checks for mod updates.").append(Component.literal(" §7- Checks for mod updates")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b hide", "/b hide", "Toggles visibility of cheats in the GUI.").append(Component.literal(" §7- Toggles GUI cheat visibility")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b area", "/b area", "Shows the current SkyBlock area.").append(Component.literal(" §7- Shows current Area")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b online", "/b online", "Shows who is online with the mod and their version.").append(Component.literal(" §7- Shows online mod users")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b color", "/b color", "Shows Minecraft text color and formatting codes.").append(Component.literal(" §7- Shows text color codes")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b time", "/b time", "Opens config GUI directly to Time Changer settings.").append(Component.literal(" §7- Opens Time Changer Settings")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b subarea", "/b subarea", "Shows the current SkyBlock subarea.").append(Component.literal(" §7- Shows current Subarea")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b container", "/b container", "Logs active virtual container structures.").append(Component.literal(" §7- Logs container info")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b sb", "/b sb", "Logs current scoreboard lines to chat.").append(Component.literal(" §7- Logs scoreboard lines")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b tab", "/b tab", "Logs current tab list lines to chat.").append(Component.literal(" §7- Logs tab list lines")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b kick", "/b kick", "Safely disconnects you from the server.").append(Component.literal(" §7- Safely disconnects from server")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b play <ip>", "/b play ", "Safely disconnects and connects to a server.").append(Component.literal(" §7- Connects to a server")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b resetdice", "/b resetdice", "Resets High Class Archfiend Dice stats.").append(Component.literal(" §7- Resets Dice statistics")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b lf <name>", "/b lf ", "Searches a player's inventory.").append(Component.literal(" §7- Searches player's inventory")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b lfc <name>", "/b lfc ", "Searches a player's inventory with NBT components.").append(Component.literal(" §7- Searches inventory with NBT components")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b lb", "/b lb", "Searches your own inventory.").append(Component.literal(" §7- Searches your own inventory")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b view <name> <p>", "/b view ", "Opens virtual container paths.").append(Component.literal(" §7- Opens virtual container paths")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b msg <message>", "/b msg ", "Simulates a chat message with §-color code support.").append(Component.literal(" §7- Simulates a chat message")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b get <alias>", "/b get ", "Checks inventory and runs /gfs to refill item up to target.").append(Component.literal(" §7- Refills items from sack")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b rank [name]", "/b rank", "Fetches and displays the Hypixel rank of a player.").append(Component.literal(" §7- Fetches and displays player rank")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b chat", "/b chat", "Toggles the global IRC mod chat.").append(Component.literal(" §7- Toggles IRC chat")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b custom", "/b custom", "Customizes the material and name of the held item.").append(Component.literal(" §7- Customizes the held item")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b import", "/b import", "Imports waypoints from your clipboard.").append(Component.literal(" §7- Imports waypoints")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b player", "/b player", "Gets skin and UUID of the player/mob in front to copy.").append(Component.literal(" §7- Inspects player/mob skin & UUID")));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b debug [api|chat]", "/b debug ", "Runs diagnostics for APIs or BomboChat socket.").append(Component.literal(" §7- Debug diagnostics (api, chat)")));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8---------------------------------------------------------"));
                     return 1;
                  }));
                                    builder.then(((LiteralArgumentBuilder)ClientCommands.literal("player").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null || mc.level == null) return 0;
                     Entity target = mc.crosshairPickEntity;
                     if (target == null) {
                        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
                        net.minecraft.world.phys.Vec3 view = mc.player.getViewVector(1.0F);
                        net.minecraft.world.phys.Vec3 reach = eye.add(view.x * 12.0, view.y * 12.0, view.z * 12.0);
                        net.minecraft.world.phys.AABB aabb = mc.player.getBoundingBox().expandTowards(view.scale(12.0)).inflate(1.0);
                        double closestDist = 12.0 * 12.0;
                        for (Entity e : mc.level.getEntities(mc.player, aabb, ent -> ent != mc.player)) {
                           net.minecraft.world.phys.AABB eBox = e.getBoundingBox().inflate(0.3);
                           var hit = eBox.clip(eye, reach);
                           if (hit.isPresent()) {
                              double d = eye.distanceToSqr(hit.get());
                              if (d < closestDist) {
                                 target = e;
                                 closestDist = d;
                              }
                           }
                        }
                     }

                     if (target == null) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §cNo player/mob found in front of you."));
                        return 0;
                     }

                     String entName = target.getName().getString();
                     String uuid = target.getUUID().toString();
                     String tex = TargetPests.getHeadTextureValue(target);
                     String hash = tex != null ? TargetPests.extractTextureHash(tex) : null;
                     String copyVal = hash != null ? hash : uuid;
                     mc.keyboardHandler.setClipboard(copyVal);

                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §a=== Targeted Entity Info ==="));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Name: §e" + entName + " §7(Type: " + target.getType().getDescription().getString() + ")"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- UUID: §d" + uuid));
                     if (hash != null) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Skin Hash: §b" + hash));
                     }
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§a✔ Copied §e" + (hash != null ? "Skin Hash" : "UUID") + " §ato clipboard! You can use it in Highlights or Custom Tracers."));
                     return 1;
                  })));

                  builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("debug").executes((context) -> {
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §e=== Debug Diagnostics ==="));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b debug api", "/b debug api", "Tests all API endpoints for status.").append(Component.literal(" §7- Tests Lowest BIN, Hypixel & GitHub APIs")));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b debug bestiary", "/b debug bestiary", "Shows live Bestiary sync diagnostics.").append(Component.literal(" §7- Bestiary rules, heads & counts")));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b debug chat", "/b debug chat", "Displays detailed BomboChat socket & connection info.").append(Component.literal(" §7- BomboChat socket/port diagnostics")));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(createHelpLine("/b debug chat toggle", "/b debug chat toggle", "Toggles live raw BomboChat debug logging.").append(Component.literal(" §7- Toggles raw chat debug logs")));
                      return 1;
                   })).then(ClientCommands.literal("api").executes((context) -> {
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §eTesting API endpoints..."));
                      (new Thread(() -> {
                         checkApiEndpoint("Moulberry Lowest BIN API", "https://moulberry.codes/lowestbin.json");
                         checkApiEndpoint("Hypixel Bazaar API", "https://api.hypixel.net/v2/skyblock/bazaar");
                         checkApiEndpoint("GitHub Release API", "https://api.github.com/repos/fran939/bomboFabric/releases/latest");
                      })).start();
                      return 1;
                   }))).then(ClientCommands.literal("bestiary").executes((context) -> {
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §eFetching & refreshing live Bestiary database..."));
                      me.bombo.bomboaddons.features.BestiaryDataFetcher.fetchBestiaryDataAsync();
                      int rulesCount = me.bombo.bomboaddons.features.BestiaryDataFetcher.getRulesCount();
                      int skullCount = me.bombo.bomboaddons.features.BestiaryDataFetcher.getHeadLookupCount();
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §aBestiary Database Status: §e" + rulesCount + " §amob rules, §e" + skullCount + " §ahead texture mappings loaded."));
                      return 1;
                   }))).then(((LiteralArgumentBuilder)ClientCommands.literal("chat").executes((context) -> {
                      boolean connected = IRCClient.isConnected();
                      String connType = IRCClient.getConnectionType();
                      String endpoint = IRCClient.activeEndpoint;
                      String err = IRCClient.lastError;
                      int users = IRCClient.getOnlinePlayers().size();
                      boolean isDebug = BomboConfig.get().debugChat;
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §e=== BomboChat Diagnostic Report ==="));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Status: " + (connected ? "§aCONNECTED" : "§cDISCONNECTED")));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Connection Type: §b" + connType));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Active Endpoint: §f" + endpoint));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Last Log / Socket Info: §f" + err));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Online Mod Users: §a" + users + " §7(Use §e/b online§7 to list)"));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Raw Chat Debug Logs: " + (isDebug ? "§aENABLED" : "§cDISABLED") + " §7(Run §e/b debug chat toggle§7 to flip)"));
                      return 1;
                   })).then(ClientCommands.literal("toggle").executes((context) -> {
                      BomboConfig.get().debugMaster = true;
                      BomboConfig.get().debugChat = !BomboConfig.get().debugChat;
                      BomboConfig.save();
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7BomboChat Raw Debug Logs set to: " + (BomboConfig.get().debugChat ? "§aENABLED" : "§cDISABLED")));
                      return 1;
                   }))));
                  builder.then(ClientCommands.literal("play").then(ClientCommands.argument("ip", StringArgumentType.greedyString()).executes((context) -> {
                     String rawIp = StringArgumentType.getString(context, "ip");
                     if (!rawIp.equalsIgnoreCase("a") && !rawIp.equalsIgnoreCase("alpha")) {
                        if (rawIp.equalsIgnoreCase("h") || rawIp.equalsIgnoreCase("hypixel")) {
                           rawIp = "hypixel.net";
                        }
                     } else {
                        rawIp = "alpha.hypixel.net";
                     }

                     final String targetIp = rawIp;
                     Minecraft mc = Minecraft.getInstance();
                     mc.execute(() -> {
                        if (mc.getConnection() != null) {
                           mc.getConnection().getConnection().disconnect(Component.literal("Connecting to " + targetIp));
                        }

                        ServerAddress address = ServerAddress.parseString(targetIp);
                        ServerData server = new ServerData("Server", targetIp, Type.OTHER);
                        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, address, server, false, (TransferState)null);
                     });
                     return 1;
                  })));
                  builder.then(ClientCommands.literal("restore").executes((context) -> {
                     BomboConfig.load();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSuccessfully reloaded bomboaddons.json!"));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("loadlegacy").executes((context) -> {
                     BomboConfig.load();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSuccessfully reloaded bomboaddons.json!"));
                     return 1;
                  }));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("prof").then(ClientCommands.literal("list").executes((context) -> {
                     printProfilesList((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(ClientCommands.argument("name", StringArgumentType.string()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name");
                     BomboConfig.Settings s = BomboConfig.get();
                     if (!s.profileBinds.containsKey(name) && !name.equals("default")) {
                        s.profileBinds.put(name, new ArrayList());
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §aCreated new config profile: §e" + name));
                     }

                     s.activeProfile = name;
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §aSwitched to config profile: §e" + name));
                     printProfilesList((FabricClientCommandSource)context.getSource());
                     return 1;
                  })));
                  builder.then(ClientCommands.literal("sounds").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     mc.execute(() -> mc.setScreen(new CustomSoundsScreen((Screen)null)));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("import").executes((context) -> {
                     try {
                        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                        if (clipboard != null && !clipboard.isEmpty()) {
                           int imported = GardenWaypoints.importWaypointsFromClipboard(clipboard);
                           if (imported > 0) {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aSuccessfully imported " + imported + " waypoints from clipboard."));
                           } else {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cNo valid waypoints found in clipboard."));
                           }
                        } else {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cClipboard is empty."));
                        }
                     } catch (Exception e) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cFailed to import waypoints: " + e.getMessage()));
                     }

                     return 1;
                  }));
                  builder.then(ClientCommands.literal("wpo").then(ClientCommands.literal("import").executes((context) -> {
                     try {
                        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                        if (clipboard != null && !clipboard.isEmpty()) {
                           int imported = OrderedWaypoints.importWaypointsFromClipboard(clipboard);
                           if (imported > 0) {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aSuccessfully imported " + imported + " ordered waypoints from clipboard."));
                           } else {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cNo valid waypoints found in clipboard."));
                           }
                        } else {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cClipboard is empty."));
                        }
                     } catch (Exception e) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cFailed to import ordered waypoints: " + e.getMessage()));
                     }

                     return 1;
                  })));
                  builder.then(ClientCommands.literal("bw").executes((context) -> {
                     pendingConfigSearch = "Bedwars";
                     openGuiNextTick = true;
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("time").executes((context) -> {
                     pendingConfigSearch = "Time";
                     openGuiNextTick = true;
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("friends").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §bFriends in Cache (" + TabCompletionManager.friends.size() + "):"));
                     if (TabCompletionManager.friends.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- §cNone"));
                     } else {
                        List<String> sorted = new ArrayList(TabCompletionManager.friends);
                        Collections.sort(sorted);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- §e" + String.join(", ", sorted)));
                     }

                     return 1;
                  }));
                  builder.then(ClientCommands.literal("guild").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §bGuild Members in Cache (" + TabCompletionManager.guild.size() + "):"));
                     if (TabCompletionManager.guild.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- §cNone"));
                     } else {
                        List<String> sorted = new ArrayList(TabCompletionManager.guild);
                        Collections.sort(sorted);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- §e" + String.join(", ", sorted)));
                     }

                     return 1;
                  }));
                  builder.then(ClientCommands.literal("party").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §bParty Members in Cache (" + TabCompletionManager.party.size() + "):"));
                     if (TabCompletionManager.party.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- §cNone"));
                     } else {
                        List<String> sorted = new ArrayList(TabCompletionManager.party);
                        Collections.sort(sorted);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- §e" + String.join(", ", sorted)));
                     }

                     return 1;
                  }));
                  builder.then(ClientCommands.literal("kick").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.getConnection() != null) {
                        mc.getConnection().getConnection().disconnect(Component.literal("Kicked via /b kick"));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNot currently connected to any server!"));
                     }

                     return 1;
                  }));
                  builder.then(ClientCommands.literal("area").executes((context) -> {
                     String loc = SkyblockUtils.getLocation();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Current Area: §a" + loc));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("block").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null || mc.level == null) return 0;
                     
                     BlockPos targetPos = null;
                     if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && mc.hitResult instanceof BlockHitResult bhr) {
                        targetPos = bhr.getBlockPos();
                     } else {
                        Vec3 eye = mc.player.getEyePosition();
                        Vec3 view = mc.player.getViewVector(1.0F);
                        Vec3 reach = eye.add(view.scale(16.0));
                        BlockHitResult bhr = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, reach, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
                        if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                           targetPos = bhr.getBlockPos();
                        }
                     }

                     if (targetPos == null) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §cNo block found in front of you."));
                        return 0;
                     }

                     BlockState state = mc.level.getBlockState(targetPos);
                     String rawId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                     String shortId = rawId.replace("minecraft:", "");
                     String blockName = state.getBlock().getName().getString();
                     String coordsStr = targetPos.getX() + " " + targetPos.getY() + " " + targetPos.getZ();

                     StringBuilder propsSb = new StringBuilder();
                     for (net.minecraft.world.level.block.state.properties.Property<?> prop : state.getProperties()) {
                        if (propsSb.length() > 0) propsSb.append(", ");
                        propsSb.append(prop.getName()).append("=").append(state.getValue(prop).toString());
                     }
                     String propsStr = propsSb.toString();
                     String stateWithProps = shortId + (!propsStr.isEmpty() ? "[" + propsStr + "]" : "");

                     mc.keyboardHandler.setClipboard(shortId);

                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §a=== Targeted Block Info ==="));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Name: §e" + blockName));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- ID: §b" + rawId + " §7(short: §e" + shortId + "§7)"));
                     if (!propsStr.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Properties: §f" + propsStr));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Full State: §e" + stateWithProps));
                     }
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Coords: §6" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ()));

                     MutableComponent actions = Component.literal("§7Actions: ");

                     MutableComponent copyIdBtn = Component.literal("§b[Copy ID] ");
                     copyIdBtn.setStyle(copyIdBtn.getStyle().withClickEvent(new ClickEvent.CopyToClipboard(shortId)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy '" + shortId + "' to clipboard"))));
                     actions.append(copyIdBtn);

                     if (!propsStr.isEmpty()) {
                        MutableComponent copyStateBtn = Component.literal("§e[Copy State] ");
                        copyStateBtn.setStyle(copyStateBtn.getStyle().withClickEvent(new ClickEvent.CopyToClipboard(stateWithProps)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy '" + stateWithProps + "' to clipboard"))));
                        actions.append(copyStateBtn);
                     }

                     MutableComponent copyCoordsBtn = Component.literal("§6[Copy Coords] ");
                     copyCoordsBtn.setStyle(copyCoordsBtn.getStyle().withClickEvent(new ClickEvent.CopyToClipboard(coordsStr)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy coords: " + coordsStr))));
                     actions.append(copyCoordsBtn);

                     MutableComponent highlightBtn = Component.literal("§a[+ Highlight Block] ");
                     highlightBtn.setStyle(highlightBtn.getStyle().withClickEvent(new ClickEvent.SuggestCommand("/b bh add " + shortId + " GOLD")).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to highlight all " + shortId))));
                     actions.append(highlightBtn);

                     if (!propsStr.isEmpty()) {
                        MutableComponent highlightStateBtn = Component.literal("§d[+ Highlight State]");
                        highlightStateBtn.setStyle(highlightStateBtn.getStyle().withClickEvent(new ClickEvent.SuggestCommand("/b bh add " + stateWithProps + " GOLD")).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to highlight exact state " + stateWithProps))));
                        actions.append(highlightStateBtn);
                     }

                     ((FabricClientCommandSource)context.getSource()).sendFeedback(actions);
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§a✔ Copied §e" + shortId + " §ato clipboard!"));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("pos1").executes((context) -> {
                     BlockPos pos = StructureScanner.getTargetOrPlayerPos();
                     StructureScanner.setPos1(pos);
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("pos2").executes((context) -> {
                     BlockPos pos = StructureScanner.getTargetOrPlayerPos();
                     StructureScanner.setPos2(pos);
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("chroma").then(ClientCommands.literal("test").executes((context) -> {
                     String sampleText = "Rainbow Wave Test";
                     String wavePreview = ChromaTextHelper.processChroma("&w" + sampleText);
                     String staticPreview = ChromaTextHelper.processChroma("&q" + sampleText);
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §d=== Chroma Preview ==="));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7&w (wave): §r" + wavePreview));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7&q (static): §r" + staticPreview));
                     String allRainbow = "&q" + getChromaAllRainbowText();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7&q all colors: §r" + ChromaTextHelper.processChroma(allRainbow)));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Type §e&w§7 or §e&q§7 in chat to use chroma!"));
                     return 1;
                  })));
                  builder.then(ClientCommands.literal("subarea").executes((context) -> {
                     String sub = SkyblockUtils.getSubArea();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Current Subarea: §d" + sub));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("container").executes((context) -> {
                     LF.printContainerInfo();
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("sb").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6Scoreboard Lines:"));

                     for(String line : SkyblockUtils.getSidebarLines(Minecraft.getInstance().level.getScoreboard(), Minecraft.getInstance().level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR))) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- §r" + line));
                     }

                     return 1;
                  }));
                  builder.then(ClientCommands.literal("tab").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §bTab List Lines:"));

                     for(Component line : SkyblockUtils.getTabListLines()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.empty().append("§7- ").append(line));
                     }

                     return 1;
                  }));
                  builder.then(ClientCommands.literal("api").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §eChecking and Reloading APIs..."));
                     LowestBinManager.reload();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal(LowestBinManager.getStatus()));
                     BitsManager.fetchTopBits(3).thenAccept((lines) -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                           mc.execute(() -> {
                              for(String l : lines) {
                                 mc.player.sendSystemMessage(Component.literal(l));
                              }

                           });
                        }

                     });
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("wd").then(ClientCommands.argument("slot", IntegerArgumentType.integer(1)).executes((context) -> {
                     int slot = IntegerArgumentType.getInteger(context, "slot");
                     WardrobeHelper.equip(slot);
                     return 1;
                  })));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("ld").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        mc.player.connection.sendCommand("loadout");
                     }

                     return 1;
                  })).then(ClientCommands.argument("slot", IntegerArgumentType.integer(1, 27)).executes((context) -> {
                     int slot = IntegerArgumentType.getInteger(context, "slot");
                     WardrobeHelper.equipLoadout(slot);
                     return 1;
                  })));
                  builder.then(ClientCommands.literal("hide").executes((context) -> {
                     BomboConfig.Settings s = BomboConfig.get();
                     s.hideCheats = !s.hideCheats;
                     if (s.hideCheats) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aCheats are now §chidden §afrom the GUI!"));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aCheats are now §avisible §ain the GUI!"));
                     }

                     BomboConfig.save();
                     return 1;
                  }));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("look").executes((context) -> LookCommand.execute((String)null, false))).then(ClientCommands.argument("subcmd", StringArgumentType.greedyString()).executes((context) -> LookCommand.execute(StringArgumentType.getString(context, "subcmd"), false))));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("looks").executes((context) -> LookCommand.execute((String)null, true))).then(ClientCommands.argument("subcmd", StringArgumentType.greedyString()).executes((context) -> LookCommand.execute(StringArgumentType.getString(context, "subcmd"), true))));
                  String[] sbeSubs = new String[]{"nw", "nwc", "cata", "skills", "slayer", "trophyfish", "crimson"};

                  for(String s : sbeSubs) {
                     builder.then(((LiteralArgumentBuilder)ClientCommands.literal(s).executes((context) -> {
                        SBECommands.handleCommand(s, Minecraft.getInstance().player.getName().getString(), (String)null);
                        return 1;
                     })).then(((RequiredArgumentBuilder)ClientCommands.argument("name", StringArgumentType.word()).executes((context) -> {
                        SBECommands.handleCommand(s, StringArgumentType.getString(context, "name"), (String)null);
                        return 1;
                     })).then(ClientCommands.argument("profile", StringArgumentType.word()).executes((context) -> {
                        SBECommands.handleCommand(s, StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "profile"));
                        return 1;
                     }))));
                  }

                  try {
                     dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("lb").executes((context) -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                           LF.show(mc.getUser().getName(), "", false);
                        }
                        return 1;
                     })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                        String query = StringArgumentType.getString(context, "query");
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                           LF.show(mc.getUser().getName(), query, false);
                        }
                        return 1;
                     })));

                     dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("lbc").executes((context) -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                           LF.show(mc.getUser().getName(), "", true);
                        }
                        return 1;
                     })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                        String query = StringArgumentType.getString(context, "query");
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                           LF.show(mc.getUser().getName(), query, true);
                        }
                        return 1;
                     })));

                     dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("lf").then(((RequiredArgumentBuilder)ClientCommands.argument("username", StringArgumentType.string()).executes((context) -> {
                        LF.show(StringArgumentType.getString(context, "username"), "", false);
                        return 1;
                     })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                        LF.show(StringArgumentType.getString(context, "username"), StringArgumentType.getString(context, "query"), false);
                        return 1;
                     }))));

                     dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("lfc").then(((RequiredArgumentBuilder)ClientCommands.argument("username", StringArgumentType.string()).executes((context) -> {
                        LF.show(StringArgumentType.getString(context, "username"), "", true);
                        return 1;
                     })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                        LF.show(StringArgumentType.getString(context, "username"), StringArgumentType.getString(context, "query"), true);
                        return 1;
                     }))));
                  } catch (Throwable t) {
                     Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register SBE root commands!", t);
                  }

                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("rank").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        String name = mc.player.getName().getString();
                        showRankCommand((FabricClientCommandSource)context.getSource(), name);
                     }

                     return 1;
                  })).then(ClientCommands.argument("name", StringArgumentType.word()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name");
                     showRankCommand((FabricClientCommandSource)context.getSource(), name);
                     return 1;
                  })));
                  builder.then(ClientCommands.literal("chat").executes((context) -> {
                     try {
                        BomboConfig.get().ircChatEnabled = !BomboConfig.get().ircChatEnabled;
                        BomboConfig.save();
                        IRCClient.onEnabledToggled();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7IRC Chat: " + (BomboConfig.get().ircChatEnabled ? "§aON" : "§cOFF")));
                     } catch (Throwable t) {
                        Bomboaddons.LOGGER.error("[BomboAddons] Error toggling IRC Chat via command", t);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cError toggling IRC Chat: " + t.getMessage()));
                     }

                     return 1;
                  }));
                  builder.then(ClientCommands.literal("wp").then(ClientCommands.literal("import").executes((context) -> {
                     String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                     if (clipboard != null && !clipboard.trim().isEmpty()) {
                        int imported = GardenWaypoints.importWaypointsFromClipboard(clipboard);
                        if (imported > 0) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSuccessfully imported " + imported + " waypoints!"));
                        } else {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cFailed to parse any waypoints from clipboard. Make sure it's a valid Skyblocker or SkyHanni export."));
                        }

                        return 1;
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cClipboard is empty!"));
                        return 1;
                     }
                  })));
                  builder.then(ClientCommands.literal("import").executes((context) -> {
                     String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                     if (clipboard != null && !clipboard.trim().isEmpty()) {
                        int imported = GardenWaypoints.importWaypointsFromClipboard(clipboard);
                        if (imported > 0) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSuccessfully imported " + imported + " waypoints!"));
                        } else {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cFailed to parse any waypoints from clipboard. Make sure it's a valid Skyblocker or SkyHanni export."));
                        }

                        return 1;
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cClipboard is empty!"));
                        return 1;
                     }
                  }));
                  builder.then(ClientCommands.literal("ec").executes((context) -> {
                     executeTracked(CommandTracker.getLastEc());
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("bp").executes((context) -> {
                     executeTracked(CommandTracker.getLastBp());
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("sh").executes((context) -> {
                     executeTracked(CommandTracker.getLastSh());
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("ks").executes((context) -> {
                     GardenMovement.onWarpTriggered();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cGarden Movement Reset! §7(States cleared)"));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("sc").executes((context) -> {
                     BomboConfig.Settings s = BomboConfig.get();
                     s.gardenSugarCane = !s.gardenSugarCane;
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Sugar Cane Mode: " + (s.gardenSugarCane ? "§aON" : "§cOFF")));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("sugarcane").executes((context) -> {
                     BomboConfig.Settings s = BomboConfig.get();
                     s.gardenSugarCane = !s.gardenSugarCane;
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Sugar Cane Mode: " + (s.gardenSugarCane ? "§aON" : "§cOFF")));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("test").executes((context) -> {
                     String version = ((ModContainer)FabricLoader.getInstance().getModContainer("bomboaddons").get()).getMetadata().getVersion().getFriendlyString();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aCurrent Version: §e" + version));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("custom").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        return 1;
                     } else {
                        ItemStack stack = mc.player.getMainHandItem();
                        if (stack.isEmpty()) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§c[Bombo] You must hold an item to customize it."));
                           return 1;
                        } else {
                           openCustomizeGuiNextTick = true;
                           return 1;
                        }
                     }
                  }));
                  builder.then(ClientCommands.literal("customize").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        return 1;
                     } else {
                        ItemStack stack = mc.player.getMainHandItem();
                        if (stack.isEmpty()) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§c[Bombo] You must hold an item to customize it."));
                           return 1;
                        } else {
                           openCustomizeGuiNextTick = true;
                           return 1;
                        }
                     }
                  }));
                  builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("wp").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cPlayer not found!"));
                        return 0;
                     } else {
                        Vec3 pos = mc.player.position();
                        String name = "Waypoint";
                        GardenWaypoints.addWaypoint(pos, name);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAdded waypoint '§e" + name + "§a' at your current position: " + String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z)));
                        return 1;
                     }
                  })).then(ClientCommands.literal("clear").executes((context) -> {
                     GardenWaypoints.clear();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAll waypoints cleared!"));
                     return 1;
                  }))).then(ClientCommands.literal("import").executes((context) -> {
                     try {
                        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                        if (clipboard != null && !clipboard.isEmpty()) {
                           int imported = GardenWaypoints.importWaypointsFromClipboard(clipboard);
                           if (imported > 0) {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aSuccessfully imported " + imported + " waypoints from clipboard."));
                           } else {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cNo valid waypoints found in clipboard."));
                           }
                        } else {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cClipboard is empty."));
                        }
                     } catch (Exception e) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cFailed to import waypoints: " + e.getMessage()));
                     }

                     return 1;
                  }))).then(ClientCommands.argument("x", StringArgumentType.word()).then(ClientCommands.argument("y", StringArgumentType.word()).then(ClientCommands.argument("z", StringArgumentType.word()).then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes((context) -> {
                     try {
                        String xStr = StringArgumentType.getString(context, "x");
                        String yStr = StringArgumentType.getString(context, "y");
                        String zStr = StringArgumentType.getString(context, "z");
                        String name = StringArgumentType.getString(context, "name");
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player == null) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cPlayer not found!"));
                           return 0;
                        } else {
                           Vec3 playerPos = mc.player.position();
                           double x;
                           if (xStr.startsWith("~")) {
                              x = playerPos.x + (xStr.length() > 1 ? Double.parseDouble(xStr.substring(1)) : (double)0.0F);
                           } else {
                              x = Double.parseDouble(xStr);
                           }

                           double y;
                           if (yStr.startsWith("~")) {
                              y = playerPos.y + (yStr.length() > 1 ? Double.parseDouble(yStr.substring(1)) : (double)0.0F);
                           } else {
                              y = Double.parseDouble(yStr);
                           }

                           double z;
                           if (zStr.startsWith("~")) {
                              z = playerPos.z + (zStr.length() > 1 ? Double.parseDouble(zStr.substring(1)) : (double)0.0F);
                           } else {
                              z = Double.parseDouble(zStr);
                           }

                           Vec3 targetPos = new Vec3(x, y, z);
                           GardenWaypoints.addWaypoint(targetPos, name);
                           WaypointManager.addWaypoint(x, y, z, name);
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAdded waypoint '§e" + name + "§a' at " + String.format("%.1f, %.1f, %.1f", x, y, z)));
                           return 1;
                        }
                     } catch (NumberFormatException var14) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cInvalid coordinates format!"));
                        return 0;
                     }
                   }))))));

                   builder.then(ClientCommands.literal("scan").executes((context) -> {
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6Usage: §e/b scan copy§7, §e/b scan paste [name]§7, §e/b scan pos1§7, §e/b scan pos2§7, §e/b scan clear§7, §e/b scan <name> [radius]§7, §e/b scan list§7, §e/b scan reload"));
                      return 1;
                   }).then(ClientCommands.literal("copy").executes((context) -> {
                      StructureScanner.copyMatchedStructure();
                      return 1;
                   })).then(ClientCommands.literal("paste").executes((context) -> {
                      Minecraft mc = Minecraft.getInstance();
                      if (mc.player == null) return 0;
                      BlockPos origin = mc.player.blockPosition();
                      StructureScanner.pasteStructure(null, origin);
                      return 1;
                   }).then(ClientCommands.argument("name", StringArgumentType.word()).executes((context) -> {
                      String name = StringArgumentType.getString(context, "name");
                      Minecraft mc = Minecraft.getInstance();
                      if (mc.player == null) return 0;
                      BlockPos origin = mc.player.blockPosition();
                      StructureScanner.pasteStructure(name, origin);
                      return 1;
                   }))).then(ClientCommands.literal("pos1").executes((context) -> {
                      BlockPos pos = StructureScanner.getTargetOrPlayerPos();
                      StructureScanner.setPos1(pos);
                      return 1;
                   })).then(ClientCommands.literal("pos2").executes((context) -> {
                      BlockPos pos = StructureScanner.getTargetOrPlayerPos();
                      StructureScanner.setPos2(pos);
                      return 1;
                   })).then(ClientCommands.literal("clear").executes((context) -> {
                      StructureScanner.pos1 = null;
                      StructureScanner.pos2 = null;
                      StructureScanner.pastedStructureOrigin = null;
                      StructureScanner.pastedStructurePattern = null;
                      StructureFinder.clear();
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aStructure scan positions, waypoints, and markers cleared!"));
                      return 1;
                   })).then(ClientCommands.literal("list").executes((context) -> {
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6--- Loaded Structure Templates ---"));
                      if (StructureScanner.loadedPatterns.isEmpty()) {
                         ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §7No patterns loaded."));
                      } else {
                         for (StructureScanner.StructurePattern p : StructureScanner.loadedPatterns.values()) {
                            ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §e" + p.name + " §7(" + p.blocks.size() + " blocks, size: " + p.sizeX + "x" + p.sizeY + "x" + p.sizeZ + ")"));
                         }
                      }
                      return 1;
                   })).then(ClientCommands.literal("reload").executes((context) -> {
                      StructureScanner.loadPatterns();
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aReloaded " + StructureScanner.loadedPatterns.size() + " structure templates!"));
                      return 1;
                   })).then(ClientCommands.literal("debug").executes((context) -> {
                      StructureScanner.runScanDebug();
                      return 1;
                   })).then(ClientCommands.literal("find").then(ClientCommands.argument("name", StringArgumentType.word()).executes((context) -> {
                      String name = StringArgumentType.getString(context, "name");
                      StructureScanner.scanArea(name, 16);
                      return 1;
                   }).then(ClientCommands.argument("radius", IntegerArgumentType.integer(1, 128)).executes((context) -> {
                      String name = StringArgumentType.getString(context, "name");
                      int rad = IntegerArgumentType.getInteger(context, "radius");
                      StructureScanner.scanArea(name, rad);
                      return 1;
                   })))));

                   builder.then(ClientCommands.literal("pos1").executes((context) -> {
                      BlockPos pos = StructureScanner.getTargetOrPlayerPos();
                      StructureScanner.setPos1(pos);
                      return 1;
                   }));
                   builder.then(ClientCommands.literal("pos2").executes((context) -> {
                      BlockPos pos = StructureScanner.getTargetOrPlayerPos();
                      StructureScanner.setPos2(pos);
                      return 1;
                   }));
                  builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("cycle").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cUsage: /b cycle add <name> <commands...>, /b cycle apply <name>, /b cycle remove <name>, or /b cycle list"));
                     return 1;
                  })).then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word()).then(ClientCommands.argument("commands", StringArgumentType.greedyString()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name").toLowerCase();
                     String commandsStr = StringArgumentType.getString(context, "commands");
                     List<String> cmds = splitCommands(commandsStr);
                     if (cmds.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo commands specified!"));
                        return 0;
                     } else {
                        BomboConfig.get().commandCycles.put(name, cmds);
                        BomboConfig.get().commandCycleIndices.put(name, 0);
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAdded cycle §e" + name + " §awith §6" + cmds.size() + "§a commands: §7" + String.join(", ", cmds)));
                        return 1;
                     }
                  }))))).then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.word()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name").toLowerCase();
                     if (BomboConfig.get().commandCycles.remove(name) != null) {
                        BomboConfig.get().commandCycleIndices.remove(name);
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aRemoved cycle §e" + name));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cCycle §e" + name + " §cdoes not exist!"));
                     }

                     return 1;
                  })))).then(ClientCommands.literal("list").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6--- Command Cycles ---"));
                     if (BomboConfig.get().commandCycles.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §7None"));
                     } else {
                        for(Map.Entry<String, List<String>> entry : BomboConfig.get().commandCycles.entrySet()) {
                           String name = (String)entry.getKey();
                           List<String> cmds = (List)entry.getValue();
                           int index = (Integer)BomboConfig.get().commandCycleIndices.getOrDefault(name, 0);
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §e" + name + " §7(Next index: §b" + index + "§7/§b" + cmds.size() + "§7) -> §7" + String.join(", ", cmds)));
                        }
                     }

                     return 1;
                  }))).then(ClientCommands.literal("apply").then(ClientCommands.argument("name", StringArgumentType.word()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name").toLowerCase();
                     List<String> cmds = (List)BomboConfig.get().commandCycles.get(name);
                     if (cmds != null && !cmds.isEmpty()) {
                        int index = (Integer)BomboConfig.get().commandCycleIndices.getOrDefault(name, 0);
                        if (index >= cmds.size() || index < 0) {
                           index = 0;
                        }

                        String cmd = (String)cmds.get(index);
                        int nextIndex = (index + 1) % cmds.size();
                        BomboConfig.get().commandCycleIndices.put(name, nextIndex);
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aRunning: §b" + cmd + " §7(Next: " + (String)cmds.get(nextIndex) + ")"));
                        executeTracked(cmd);
                        return 1;
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cCycle §e" + name + " §cdoes not exist or is empty!"));
                        return 0;
                     }
                  }))));
                  builder.then(ClientCommands.literal("storage").executes((context) -> {
                     Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new GlobalStorageScreen()));
                     return 1;
                  }));
                  builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("pt").executes((context) -> {
                     long now = System.currentTimeMillis();
                     long secsSinceSync = (now - PlaytimeTracker.lastCloudSyncTime) / 1000L;
                     String syncAgo = secsSinceSync < 60L ? secsSinceSync + "s ago" : (secsSinceSync < 3600L ? secsSinceSync / 60L + "m ago" : secsSinceSync / 3600L + "h ago");
                     long nextSync = Math.max(0L, 300L - secsSinceSync);
                     String nextStr = nextSync <= 0L ? "§aany moment now" : "§ein ~" + nextSync + "s";
                     Minecraft mc = Minecraft.getInstance();
                     boolean online = mc.getConnection() != null && !mc.getConnection().getConnection().isMemoryConnection();
                     String status = online ? "§aOnline" : "§8Offline";
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Status: " + status + " §7| Last Sync: §a" + syncAgo + " §7| Next Sync: " + nextStr));
                     FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
                     String var10001 = PlaytimeTracker.formatTime(PlaytimeTracker.getSessionTime());
                     var10000.sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Total Playtime: §e" + var10001 + " §7| Area: §b" + (online ? currentArea : "N/A") + " §7| AFK: " + (PlaytimeTracker.isAfk() ? "§cYes" : "§aNo")));
                     if (online) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Auto-syncs every §e5 min §7and on disconnect."));
                     }

                     Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new PlaytimeGUI((JsonObject)null)));
                     return 1;
                  })).then(ClientCommands.literal("sync").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aManually syncing your playtime data to the cloud..."));
                     PlaytimeTracker.sendPlaytimeDataToCloud();
                     return 1;
                  }))).then(ClientCommands.argument("username", StringArgumentType.string()).executes((context) -> {
                     String username = StringArgumentType.getString(context, "username");
                     Minecraft mc = Minecraft.getInstance();
                     boolean isOnline = false;
                     if (mc.getConnection() != null && !username.equalsIgnoreCase(mc.getUser().getName())) {
                        for(PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                           if (info.getProfile().name().equalsIgnoreCase(username)) {
                              isOnline = true;
                              if (mc.player != null) {
                                 mc.player.connection.sendCommand("msg " + info.getProfile().name() + " [BomboPlaytimeSyncRequest]");
                              }
                              break;
                           }
                        }
                     }

                     final boolean targetOnline = isOnline;
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aFetching playtime data for §e" + username + "§a..."));
                     (new Thread(() -> {
                        try {
                           if (targetOnline) {
                              Thread.sleep(1000L);
                           }

                           URL url = (new URI(BomboApiUrl.getApiUrl("/playtime/" + username))).toURL();
                           HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                           conn.setRequestMethod("GET");
                           int responseCode = conn.getResponseCode();
                           if (responseCode == 200) {
                              InputStreamReader reader = new InputStreamReader(conn.getInputStream());

                              try {
                                 JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
                                 Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new PlaytimeGUI(data)));
                              } catch (Throwable var10) {
                                 try {
                                    reader.close();
                                 } catch (Throwable x2) {
                                    var10.addSuppressed(x2);
                                 }

                                 throw var10;
                              }

                              reader.close();
                           } else {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo playtime data found for §e" + username + "§c."));
                           }
                        } catch (Exception e) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cError fetching playtime data: " + e.getMessage()));
                        }

                     })).start();
                     return 1;
                  })));
                  builder.then(ClientCommands.literal("update").executes((context) -> {
                     ModUpdater.checkAndUpdate(false);
                     return 1;
                  }));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("coords").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        int x = (int)mc.player.getX();
                        int y = (int)mc.player.getY();
                        int z = (int)mc.player.getZ();
                        String coords = "x: " + x + ", y: " + y + ", z: " + z;
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aCurrent Coords: §e" + coords));
                     }

                     return 1;
                  })).then(ClientCommands.argument("command", StringArgumentType.greedyString()).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        int x = (int)mc.player.getX();
                        int y = (int)mc.player.getY();
                        int z = (int)mc.player.getZ();
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
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("tracer").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null && mc.level != null) {
                        Entity target = LookCommand.findLookTarget(mc, false);
                        if (target == null) {
                           target = mc.crosshairPickEntity;
                        }

                        if (target != null) {
                           String uuidStr = target.getUUID().toString();
                           String traceId = uuidStr;
                           String name = target.getName().getString();
                           String headTex = TargetPests.getHeadTextureValue(target);
                           String skullHash = headTex != null ? TargetPests.extractTextureHash(headTex) : null;
                           if (skullHash != null) {
                              traceId = skullHash.toLowerCase();
                              name = target.hasCustomName() ? target.getCustomName().getString() : (target.getName().getString().equals("Armor Stand") ? "Head: " + skullHash.substring(0, Math.min(8, skullHash.length())) : target.getName().getString());
                           } else if (target instanceof ItemEntity) {
                              ItemEntity itemEntity = (ItemEntity)target;
                              String itemName = itemEntity.getItem().getHoverName().getString();
                              name = "Item: " + itemName;
                              traceId = itemName;
                           }

                            BomboConfig.Settings s = BomboConfig.get();
                            boolean removed = false;
                            if (HighlightESP.ignoredEntities.contains(target.getId())) {
                               HighlightESP.ignoredEntities.remove(target.getId());
                               mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aUn-ignored entity (§eID: " + target.getId() + "§a)!"));
                               return 1;
                            }
                            if (s.customTracers.containsKey(traceId)) {
                               s.customTracers.remove(traceId);
                               removed = true;
                            } else if (s.customTracers.containsKey(uuidStr)) {
                               s.customTracers.remove(uuidStr);
                               removed = true;
                            } else if (skullHash != null && s.customTracers.containsKey(skullHash.toLowerCase())) {
                               s.customTracers.remove(skullHash.toLowerCase());
                               removed = true;
                            }
                            
                            if (removed) {
                               HighlightESP.ignoredEntities.add(target.getId());
                               BomboConfig.save();
                               mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cRemoved tracer & highlight from: §e" + name));
                            } else {
                               s.customTracers.put(traceId, new BomboConfig.Settings.CustomTracerInfo(name, "green"));
                               HighlightESP.ignoredEntities.remove(target.getId());
                               BomboConfig.save();
                               mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aAdded tracer & highlight to: §e" + name + " §7(Through blocks)"));
                            }
                        } else {
                           mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cNo entity found in line of sight!"));
                        }

                        return 1;
                     } else {
                        return 0;
                     }
                  })).then(ClientCommands.literal("clear").executes((context) -> {
                     BomboConfig.get().customTracers.clear();
                     BomboConfig.save();
                     Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cCleared all custom tracers!"));
                     return 1;
                  })));
                  builder.then(ClientCommands.literal("last").executes((context) -> {
                     String lastCmd = SkyblockUtils.lastExecutedCommand;
                     if (lastCmd != null && !lastCmd.trim().isEmpty()) {
                        String clean = lastCmd.trim();
                        FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
                        String var10001 = clean.startsWith("/") ? clean : "/" + clean;
                        var10000.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aExecuting last command: §e" + var10001));
                        executeTracked(clean);
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo previous command recorded!"));
                     }

                     return 1;
                  }));
                  LiteralArgumentBuilder<FabricClientCommandSource> acNode = (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("ac").executes((context) -> {
                     if (AutoCroesus.active) {
                        AutoCroesus.stopActive((FabricClientCommandSource)context.getSource());
                     } else {
                        AutoCroesus.startActive((FabricClientCommandSource)context.getSource());
                     }

                     return 1;
                  })).then(ClientCommands.literal("go").executes((context) -> {
                     AutoCroesus.startActive((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(ClientCommands.literal("start").executes((context) -> {
                     AutoCroesus.startActive((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(ClientCommands.literal("step").executes((context) -> {
                     AutoCroesus.performStep((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(ClientCommands.literal("stop").executes((context) -> {
                     AutoCroesus.stopActive((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(ClientCommands.literal("profit").executes((context) -> {
                     AutoCroesus.printProfitSummary((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(ClientCommands.literal("stats").executes((context) -> {
                     AutoCroesus.printProfitSummary((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(ClientCommands.literal("sync").executes((context) -> {
                     AutoCroesus.syncProfitData((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(((LiteralArgumentBuilder)ClientCommands.literal("kismet").executes((context) -> {
                     BomboConfig.Settings settings = BomboConfig.get();
                     String statusStr = settings.autoKismet ? "§aENABLED" : "§cDISABLED";
                     String var10000 = LowestBinManager.formatPrice(settings.kismetThreshold);
                     String threshStr = var10000 + " (" + String.format("%,d", settings.kismetThreshold) + " coins)";
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bAutoCroesus§8] §7Auto Kismet: " + statusStr + " §7| Threshold: §e" + threshStr));
                     return 1;
                  })).then(ClientCommands.argument("param", StringArgumentType.string()).executes((context) -> {
                     String param = StringArgumentType.getString(context, "param").trim().toLowerCase();
                     BomboConfig.Settings settings = BomboConfig.get();
                     if (!"on".equals(param) && !"true".equals(param) && !"enable".equals(param)) {
                        if (!"off".equals(param) && !"false".equals(param) && !"disable".equals(param)) {
                           long parsedVal = parseMoneyValue(param);
                           if (parsedVal > 0L) {
                              settings.kismetThreshold = parsedVal;
                              settings.autoKismet = true;
                              BomboConfig.save();
                              String var10000 = LowestBinManager.formatPrice(parsedVal);
                              String formatted = var10000 + " (" + String.format("%,d", parsedVal) + " coins)";
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bAutoCroesus§8] §aAuto Kismet set to §e" + formatted + " §a(ENABLED)§7! Chests under this value will be rerolled using Kismet Feathers."));
                           } else {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bAutoCroesus§8] §cInvalid value! Use e.g. §e/b ac kismet 3m§c, §e/b ac kismet 500k§c, §e/b ac kismet on§c, or §e/b ac kismet off§c."));
                           }
                        } else {
                           settings.autoKismet = false;
                           BomboConfig.save();
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bAutoCroesus§8] §cAuto Kismet Reroll §cDISABLED§7."));
                        }
                     } else {
                        settings.autoKismet = true;
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bAutoCroesus§8] §aAuto Kismet Reroll §aENABLED§7! Threshold: §e" + LowestBinManager.formatPrice(settings.kismetThreshold)));
                     }

                     return 1;
                  })))).then(ClientCommands.literal("clear").executes((context) -> {
                     AutoCroesus.resetProfitTracker();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bAutoCroesus§8] §aCleared cumulative profit statistics!"));
                     return 1;
                  }));
                  builder.then(acNode);
                  LiteralArgumentBuilder<FabricClientCommandSource> autoCroesusNode = (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("autocroesus").executes((context) -> {
                     if (AutoCroesus.active) {
                        AutoCroesus.stopActive((FabricClientCommandSource)context.getSource());
                     } else {
                        AutoCroesus.startActive((FabricClientCommandSource)context.getSource());
                     }

                     return 1;
                  })).then(ClientCommands.literal("step").executes((context) -> {
                     AutoCroesus.performStep((FabricClientCommandSource)context.getSource());
                     return 1;
                  }));
                  dispatcher.register(autoCroesusNode);
                  LiteralArgumentBuilder<FabricClientCommandSource> kuudraNode = (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("kuudra").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     String localUser = mc.getUser() != null ? mc.getUser().getName() : "user";
                     KuudraSummaryOverlay.fetchAndPrintPlayerKuudraData((FabricClientCommandSource)context.getSource(), localUser);
                     return 1;
                  })).then(ClientCommands.argument("player", StringArgumentType.string()).suggests((context, sugBuilder) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.getConnection() != null) {
                        String remaining = sugBuilder.getRemaining().toLowerCase();

                        for(PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                           String pName = info.getProfile().name();
                           if (pName != null && !pName.startsWith("!") && pName.matches("^[a-zA-Z0-9_]{2,16}$") && pName.toLowerCase().startsWith(remaining)) {
                              sugBuilder.suggest(pName);
                           }
                        }
                     }

                     return sugBuilder.buildFuture();
                  }).executes((context) -> {
                     String target = StringArgumentType.getString(context, "player");
                     KuudraSummaryOverlay.fetchAndPrintPlayerKuudraData((FabricClientCommandSource)context.getSource(), target);
                     return 1;
                  }));
                  builder.then(kuudraNode);
                  dispatcher.register(kuudraNode);
                  LiteralArgumentBuilder<FabricClientCommandSource> uuidNode = (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("uuid").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        return 0;
                     } else {
                        String name = mc.getUser().getName();
                        String uuidStr = mc.getUser().getProfileId().toString();
                        Component msg = Component.literal("§8[§bBomboAddons§8] §7Your UUID: ").append(Component.literal("§b" + uuidStr).withStyle((style) -> style.withClickEvent(new ClickEvent.CopyToClipboard(uuidStr)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy UUID!")))));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(msg);
                        return 1;
                     }
                  })).then(ClientCommands.argument("player", StringArgumentType.string()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "player").trim();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §eFetching UUID for §b" + name + "§e..."));
                     CompletableFuture.runAsync(() -> {
                        try {
                           String mojangUrl = "https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8);
                           HttpURLConnection conn = (HttpURLConnection)(new URL(mojangUrl)).openConnection();
                           conn.setRequestMethod("GET");
                           conn.setConnectTimeout(4000);
                           conn.setReadTimeout(4000);
                           if (conn.getResponseCode() == 200) {
                              InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                              JsonObject mJson = JsonParser.parseReader(reader).getAsJsonObject();
                              reader.close();
                              if (mJson.has("id")) {
                                 String rawUuid = mJson.get("id").getAsString();
                                 String formattedUuid = rawUuid.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                                 Minecraft.getInstance().execute(() -> {
                                    Component msg = Component.literal("§8[§bBomboAddons§8] §7Player: §e" + name + " §7| UUID: ").append(Component.literal("§b" + formattedUuid).withStyle((style) -> style.withClickEvent(new ClickEvent.CopyToClipboard(formattedUuid)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy UUID!")))));
                                    ((FabricClientCommandSource)context.getSource()).sendFeedback(msg);
                                 });
                                 return;
                              }
                           }

                           Minecraft.getInstance().execute(() -> ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §cCould not find UUID for player §e" + name + "§c.")));
                        } catch (Exception e) {
                           Minecraft.getInstance().execute(() -> ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §cError fetching UUID: " + e.getMessage())));
                        }

                     });
                     return 1;
                  }));
                  builder.then(uuidNode);
                  dispatcher.register(uuidNode);
                  builder.then(ClientCommands.literal("ks").executes((context) -> {
                     AutoFishing.stopAllAutomation("manual");
                     return 1;
                  }));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("fishing").then(ClientCommands.argument("msg", StringArgumentType.greedyString()).executes((context) -> {
                     String triggerInput = StringArgumentType.getString(context, "msg").trim();
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        return 0;
                     } else {
                        if (!triggerInput.equalsIgnoreCase("clear") && !triggerInput.equalsIgnoreCase("reset")) {
                           String existing = BomboConfig.get().autoFishingStopChatMessage;
                           String updated;
                           if (existing != null && !existing.trim().isEmpty()) {
                              List<String> currentList = new ArrayList();

                              for(String s : existing.split(",")) {
                                 if (!s.trim().isEmpty()) {
                                    currentList.add(s.trim());
                                 }
                              }

                              for(String s : triggerInput.split(",")) {
                                 String clean = s.trim();
                                 if (!clean.isEmpty() && currentList.stream().noneMatch((c) -> c.equalsIgnoreCase(clean))) {
                                    currentList.add(clean);
                                 }
                              }

                              updated = String.join(", ", currentList);
                           } else {
                              updated = triggerInput;
                           }

                           BomboConfig.get().autoFishingStopChatMessage = updated;
                           BomboConfig.save();
                           mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §aAuto Fishing triggers updated: §e\"" + updated + "\" §7(type /b fishing clear to reset)"));
                        } else {
                           BomboConfig.get().autoFishingStopChatMessage = "";
                           BomboConfig.save();
                           mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §cCleared all Auto Fishing chat stop triggers!"));
                        }

                        return 1;
                     }
                  }))).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        return 0;
                     } else {
                        String curMsg = BomboConfig.get().autoFishingStopChatMessage;
                        if (curMsg != null && !curMsg.trim().isEmpty()) {
                           mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §aActive Auto Fishing chat stop triggers: §e\"" + curMsg + "\" §7(type /b fishing clear to reset)"));
                        } else {
                           mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §7No Auto Fishing chat stop trigger set. Usage: §e/b fishing <trigger>"));
                        }
                        return 1;
                     }
                  }));
                  builder.then(ClientCommands.literal("accept").executes((context) -> {
                     String acceptCmd = ChatMessageTracker.findBestAcceptCommand();
                     if (acceptCmd != null && !acceptCmd.trim().isEmpty()) {
                        String clean = acceptCmd.trim();
                        FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
                        String var10001 = clean.startsWith("/") ? clean : "/" + clean;
                        var10000.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aExecuting accept command: §e" + var10001));
                        executeTracked(clean);
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo recent accept link/command found in chat!"));
                     }
                     return 1;
                  }));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("recipe").then(ClientCommands.argument("item", StringArgumentType.greedyString()).suggests((context, builder2) -> getItemSuggestions(builder2)).executes((context) -> {
                     String item = StringArgumentType.getString(context, "item");
                     String resolvedId = resolveItemId(item);
                     Minecraft mc = Minecraft.getInstance();
                     mc.execute(() -> mc.setScreen(new RecipeViewerScreen(resolvedId, mc.screen)));
                     return 1;
                  }))).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     mc.execute(() -> mc.setScreen(new RecipeViewerScreen("", mc.screen)));
                     return 1;
                  }));
                  dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("collection").then(ClientCommands.argument("name", StringArgumentType.greedyString()).suggests((context, builder2) -> getCollectionSuggestions(builder2)).executes((context) -> {
                     String coll = StringArgumentType.getString(context, "name");
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null && mc.getConnection() != null) {
                        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("collection " + coll));
                     }
                     return 1;
                  }))).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null && mc.getConnection() != null) {
                        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("collection"));
                     }
                     return 1;
                  }));
                  dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("coll").then(ClientCommands.argument("name", StringArgumentType.greedyString()).suggests((context, builder2) -> getCollectionSuggestions(builder2)).executes((context) -> {
                     String coll = StringArgumentType.getString(context, "name");
                     Minecraft mc = Minecraft.getInstance();
                      if (mc.player != null && mc.getConnection() != null) {
                         mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("collection " + coll));
                     }
                     return 1;
                  }))).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                      if (mc.player != null && mc.getConnection() != null) {
                         mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("collection"));
                     }
                     return 1;
                  }));
                  dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("calendar").then(ClientCommands.literal("api").executes((context) -> {
                     fetchAndShowCalendarApi((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(ClientCommands.literal("gui").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null && mc.player.connection != null) {
                        mc.player.connection.sendCommand("calendar");
                     }

                     return 1;
                  }))).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null && mc.player.connection != null) {
                        mc.player.connection.sendCommand("calendar");
                     }

                     return 1;
                  }));
                  builder.then(ClientCommands.literal("debug").then(ClientCommands.literal("api").executes((context) -> {
                     runApiDebugTests();
                     return 1;
                  })));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("paste").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        return 0;
                     } else {
                        String clip = mc.keyboardHandler.getClipboard().trim();
                        if (clip.isEmpty()) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cClipboard is empty!"));
                           return 0;
                        } else {
                           String cleanCmd = clip.replaceAll("^/+", "").trim();
                           if (cleanCmd.isEmpty()) {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cClipboard contains no valid command!"));
                              return 0;
                           } else {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aExecuting pasted command: §e/" + cleanCmd));
                              mc.execute(() -> {
                                 if (mc.player != null && mc.player.connection != null) {
                                    mc.player.connection.sendCommand(cleanCmd);
                                 }

                              });
                              return 1;
                           }
                        }
                     }
                  })).then(ClientCommands.argument("cmdPrefix", StringArgumentType.greedyString()).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        return 0;
                     } else {
                        String clip = mc.keyboardHandler.getClipboard().trim();
                        String prefix = StringArgumentType.getString(context, "cmdPrefix").trim();
                        String cleanPrefix = prefix.replaceAll("^/+", "").trim();
                        String cleanCmd = clip.replaceAll("^/+", "").trim();
                        String fullCmd = cleanPrefix + " " + cleanCmd;
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aExecuting pasted command: §e/" + fullCmd));
                        mc.execute(() -> {
                           if (mc.player != null && mc.player.connection != null) {
                              mc.player.connection.sendCommand(fullCmd);
                           }
                        });
                        return 1;
                     }
                  })));
                  LiteralArgumentBuilder<FabricClientCommandSource> entityInspectNode = ClientCommands.literal("entity").executes((context) -> {
                      Minecraft mc = Minecraft.getInstance();
                      FabricClientCommandSource src = (FabricClientCommandSource)context.getSource();
                      if (mc.player != null && mc.level != null) {
                         List<Entity> targets = LookCommand.findLookTargets(mc, 5, false);
                         if (targets.isEmpty() && mc.crosshairPickEntity != null) {
                            targets = Collections.singletonList(mc.crosshairPickEntity);
                         }
                         if (!targets.isEmpty()) {
                            int total = targets.size();
                            for (int i = 0; i < total; i++) {
                               displayEntityDetails(src, mc, targets.get(i), i + 1, total);
                            }
                         } else {
                            src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo entity found in line of sight!"));
                         }
                      }
                      return 1;
                   }).then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 20)).executes(context -> {
                      int count = IntegerArgumentType.getInteger(context, "count");
                      Minecraft mc = Minecraft.getInstance();
                      FabricClientCommandSource src = (FabricClientCommandSource)context.getSource();
                      if (mc.player != null && mc.level != null) {
                         List<Entity> targets = LookCommand.findLookTargets(mc, count, false);
                         if (targets.isEmpty() && mc.crosshairPickEntity != null) {
                            targets = Collections.singletonList(mc.crosshairPickEntity);
                         }
                         if (!targets.isEmpty()) {
                            int total = targets.size();
                            for (int i = 0; i < total; i++) {
                               displayEntityDetails(src, mc, targets.get(i), i + 1, total);
                            }
                         } else {
                            src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo entities found in line of sight!"));
                         }
                      }
                      return 1;
                   }));
                  builder.then(entityInspectNode);

                  LiteralArgumentBuilder<FabricClientCommandSource> nameInspectNode = ClientCommands.literal("name").executes((context) -> {
                      Minecraft mc = Minecraft.getInstance();
                      FabricClientCommandSource src = (FabricClientCommandSource)context.getSource();
                      if (mc.player != null && mc.level != null) {
                         Entity target = LookCommand.findLookTarget(mc, false);
                         if (target == null) target = mc.crosshairPickEntity;
                         if (target != null) {
                            String rawName = target.getName().getString();
                            String dispName = target.getDisplayName().getString();
                            String customName = target.getCustomName() != null ? target.getCustomName().getString() : null;
                            String headTex = TargetPests.getHeadTextureValue(target);
                            String skullHash = headTex != null ? TargetPests.extractTextureHash(headTex) : null;
                            
                            src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §e=== Target Name Info ==="));
                            src.sendFeedback(Component.literal(" §7Name: §f" + rawName));
                            src.sendFeedback(Component.literal(" §7Display Name: §f" + dispName));
                            if (customName != null && !customName.equals("None")) {
                               src.sendFeedback(Component.literal(" §7Custom Name: §f" + customName));
                            }
                            if (skullHash != null) {
                               src.sendFeedback(Component.literal(" §7Skull Hash: §e" + skullHash));
                            }
                            ClickEvent copyName = LF.createClickEventRobust("COPY_TO_CLIPBOARD", rawName);
                            ClickEvent addHl = LF.createClickEventRobust("RUN_COMMAND", "/b highlight add " + (skullHash != null ? skullHash : rawName) + " GOLD");
                            Component act = Component.literal(" §a[+ Highlight Target]")
                               .withStyle(style -> addHl != null ? style.withClickEvent(addHl) : style)
                               .append(Component.literal(" §b[Copy Name]").withStyle(style -> copyName != null ? style.withClickEvent(copyName) : style));
                            src.sendFeedback(act);

                            if (target instanceof net.minecraft.world.entity.player.Player) {
                               fetchAndShowNameHistory(src, rawName);
                            }
                         } else {
                            src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Usage: /b name <username>"));
                         }
                      }
                      return 1;
                   }).then(ClientCommands.argument("username", StringArgumentType.word()).executes(context -> {
                      String targetUser = StringArgumentType.getString(context, "username");
                      FabricClientCommandSource src = (FabricClientCommandSource)context.getSource();
                      fetchAndShowNameHistory(src, targetUser);
                      return 1;
                   }));
                   builder.then(nameInspectNode);
                   builder.then(ClientCommands.literal("gdrag").executes(context -> {
                     BomboConfig.Settings s = BomboConfig.get();
                     s.goldenDragonNestFinder = !s.goldenDragonNestFinder;
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §7Golden Dragon Nest Finder: " + (s.goldenDragonNestFinder ? "§aEnabled" : "§cDisabled")));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("gdragfinder").executes(context -> {
                     BomboConfig.Settings s = BomboConfig.get();
                     s.goldenDragonNestFinder = !s.goldenDragonNestFinder;
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §7Golden Dragon Nest Finder: " + (s.goldenDragonNestFinder ? "§aEnabled" : "§cDisabled")));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("nest").executes(context -> {
                     BomboConfig.Settings s = BomboConfig.get();
                     s.goldenDragonNestFinder = !s.goldenDragonNestFinder;
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §7Golden Dragon Nest Finder: " + (s.goldenDragonNestFinder ? "§aEnabled" : "§cDisabled")));
                     return 1;
                  }));
                  LiteralArgumentBuilder<FabricClientCommandSource> setNode = ClientCommands.literal("set");
                  setNode.executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Usage: /b set <block> (e.g. air, stone) OR /b set <setting> <value>"));
                     return 1;
                  });
                  setNode.then(ClientCommands.argument("firstArg", StringArgumentType.word()).executes((context) -> {
                     String blockName = StringArgumentType.getString(context, "firstArg").toLowerCase().trim();
                     Minecraft mc = Minecraft.getInstance();
                     FabricClientCommandSource src = (FabricClientCommandSource)context.getSource();
                     if (mc.player != null && mc.level != null) {
                        net.minecraft.world.phys.HitResult hit = mc.player.pick(20.0D, 0.0F, false);
                        if (hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit instanceof net.minecraft.world.phys.BlockHitResult) {
                           BlockPos pos = ((net.minecraft.world.phys.BlockHitResult)hit).getBlockPos();
                           Identifier blockId = null;
                           try {
                              blockId = Identifier.parse(blockName.contains(":") ? blockName : "minecraft:" + blockName);
                           } catch (Exception ignored) {}
                           if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
                              net.minecraft.world.level.block.Block targetBlock = BuiltInRegistries.BLOCK.getValue(blockId);
                              if (targetBlock != null) {
                                 mc.level.setBlock(pos, targetBlock.defaultBlockState(), 3);
                                 src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSet block at §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " §ato §b" + targetBlock.getName().getString()));
                                 return 1;
                              }
                           }
                           src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §cUnknown block: §e" + blockName));
                           return 1;
                        } else {
                           src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo block in line of sight (or looking too far away)!"));
                           return 1;
                        }
                     }
                     return 1;
                  }).then(ClientCommands.argument("value", StringArgumentType.greedyString()).executes((context) -> {
                     String field = StringArgumentType.getString(context, "firstArg");
                     String value = StringArgumentType.getString(context, "value");
                     FabricClientCommandSource src = (FabricClientCommandSource)context.getSource();
                     BomboConfig.Settings s = BomboConfig.get();
                     try {
                        java.lang.reflect.Field f = BomboConfig.Settings.class.getField(field);
                        Class<?> fType = f.getType();
                        if (fType == boolean.class || fType == Boolean.class) {
                           boolean bVal = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("1") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("enable");
                           f.setBoolean(s, bVal);
                           BomboConfig.save();
                           src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSet §e" + field + " §ato §b" + bVal));
                        } else if (fType == int.class || fType == Integer.class) {
                           int iVal = Integer.parseInt(value.trim());
                           f.setInt(s, iVal);
                           BomboConfig.save();
                           src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSet §e" + field + " §ato §b" + iVal));
                        } else if (fType == double.class || fType == Double.class) {
                           double dVal = Double.parseDouble(value.trim());
                           f.setDouble(s, dVal);
                           BomboConfig.save();
                           src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSet §e" + field + " §ato §b" + dVal));
                        } else if (fType == float.class || fType == Float.class) {
                           float flVal = Float.parseFloat(value.trim());
                           f.setFloat(s, flVal);
                           BomboConfig.save();
                           src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSet §e" + field + " §ato §b" + flVal));
                        } else if (fType == String.class) {
                           f.set(s, value);
                           BomboConfig.save();
                           src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aSet §e" + field + " §ato §b" + value));
                        } else {
                           src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §cField type " + fType.getSimpleName() + " is not directly settable via /b set."));
                        }
                     } catch (NoSuchFieldException e) {
                        src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §cUnknown setting field: §e" + field));
                     } catch (Exception e) {
                        src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §cFailed to set value: " + e.getMessage()));
                     }
                     return 1;
                  })));
                  builder.then(setNode);
                  builder.then(ClientCommands.literal("head").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        ItemStack stack = mc.player.getMainHandItem();
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
                  LiteralArgumentBuilder<FabricClientCommandSource> highlightCmd = ClientCommands.literal("highlight");
                  highlightCmd.then(ClientCommands.literal("remove").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null && mc.level != null) {
                        Entity target = LookCommand.findLookTarget(mc, false);
                        if (target == null) target = mc.crosshairPickEntity;
                        if (target != null) {
                           HighlightESP.ignoredEntities.add(target.getId());
                           String uuidStr = target.getUUID().toString();
                           BomboConfig.get().customTracers.remove(uuidStr);
                           String name = target.getName().getString();
                           if (target instanceof ItemEntity) {
                              ItemStack stack = ((ItemEntity)target).getItem();
                              if (!stack.isEmpty()) BomboConfig.get().customTracers.remove(stack.getHoverName().getString());
                           }
                           BomboConfig.save();
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cRemoved highlight & tracer from looked-at entity instance (§eID: " + target.getId() + " - " + name + "§c)!"));
                        } else {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo entity found in line of sight! Specify name: /b highlight remove <name>"));
                        }
                     }
                     return 1;
                  }).then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name").toLowerCase().replaceAll("^\"+|\"+$", "").trim();
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        Entity target = LookCommand.findLookTarget(mc, false);
                        if (target == null) target = mc.crosshairPickEntity;
                        if (target != null && HighlightESP.ignoredEntities.contains(target.getId())) {
                           HighlightESP.ignoredEntities.remove(target.getId());
                        }
                     }
                     if (BomboConfig.get().highlights.remove(name) != null) {
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aRemoved highlight for: §e" + name));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo highlight found for: §e" + name));
                     }

                     return 1;
                  })));
                  highlightCmd.then(ClientCommands.literal("toggle").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name").toLowerCase().replaceAll("^\"+|\"+$", "").trim();
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        Entity target = LookCommand.findLookTarget(mc, false);
                        if (target == null) target = mc.crosshairPickEntity;
                        if (target != null && HighlightESP.ignoredEntities.contains(target.getId())) {
                           HighlightESP.ignoredEntities.remove(target.getId());
                        }
                     }
                     BomboConfig.HighlightInfo info = (BomboConfig.HighlightInfo)BomboConfig.get().highlights.get(name);
                     if (info != null) {
                        info.enabled = !info.enabled;
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aToggled highlight for: §e" + name + " §a(" + (info.enabled ? "Enabled" : "Disabled") + ")"));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo highlight found for: §e" + name));
                     }

                     return 1;
                  })));
                  highlightCmd.then(ClientCommands.literal("list").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6--- Persistent Entity Highlights ---"));
                     if (BomboConfig.get().highlights.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §7None"));
                     } else {
                        for(Map.Entry<String, BomboConfig.HighlightInfo> entry : BomboConfig.get().highlights.entrySet()) {
                           String targetName = (String)entry.getKey();
                           String color = ((BomboConfig.HighlightInfo)entry.getValue()).color;
                           boolean enabled = ((BomboConfig.HighlightInfo)entry.getValue()).enabled;
                           ClickEvent toggleClick = LF.createClickEventRobust("RUN_COMMAND", "/b highlight toggle " + targetName);
                           Component toggleBtn = enabled ? Component.literal(" §a[Enabled]") : Component.literal(" §c[Disabled]");
                           if (toggleClick != null) {
                              toggleBtn = (enabled ? Component.literal(" §a[Enabled]") : Component.literal(" §c[Disabled]")).withStyle((style) -> style.withClickEvent(toggleClick));
                           }

                           ClickEvent click = LF.createClickEventRobust("RUN_COMMAND", "/b highlight remove " + targetName);
                           Component removeBtn = Component.literal(" §c[Remove]");
                           if (click != null) {
                              removeBtn = Component.literal(" §c[Remove]").withStyle((style) -> style.withClickEvent(click));
                           }

                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §e" + targetName + " §7- §b" + color).append(toggleBtn).append(removeBtn));
                        }
                     }

                     return 1;
                  }));
                  highlightCmd.then(ClientCommands.literal("clear").executes((context) -> {
                     BomboConfig.get().highlights.clear();
                     HighlightESP.ignoredEntities.clear();
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aCleared all highlights."));
                     return 1;
                  }));
                  highlightCmd.then(ClientCommands.literal("add").then(ClientCommands.argument("mob", StringArgumentType.word()).then(((RequiredArgumentBuilder)ClientCommands.argument("color", StringArgumentType.word()).suggests((context, builder2) -> {
                     for(String c : SlotHighlight.COLORS) {
                        builder2.suggest(c);
                     }

                     return builder2.buildFuture();
                  }).executes((context) -> {
                     String mob = StringArgumentType.getString(context, "mob");
                     String color = StringArgumentType.getString(context, "color").toUpperCase();
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        Entity target = LookCommand.findLookTarget(mc, false);
                        if (target == null) target = mc.crosshairPickEntity;
                        if (target != null && HighlightESP.ignoredEntities.contains(target.getId())) {
                           HighlightESP.ignoredEntities.remove(target.getId());
                        }
                     }
                     BomboConfig.get().highlights.put(mob.toLowerCase(), new BomboConfig.HighlightInfo(color, false));
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aHighlight added for §e" + mob + " §awith color §b" + color));
                     return 1;
                  })).then(ClientCommands.argument("showInvisible", IntegerArgumentType.integer(0, 1)).executes((context) -> {
                     String mob = StringArgumentType.getString(context, "mob");
                     String color = StringArgumentType.getString(context, "color").toUpperCase();
                     int siInt = IntegerArgumentType.getInteger(context, "showInvisible");
                     boolean si = siInt == 1;
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        Entity target = LookCommand.findLookTarget(mc, false);
                        if (target == null) target = mc.crosshairPickEntity;
                        if (target != null && HighlightESP.ignoredEntities.contains(target.getId())) {
                           HighlightESP.ignoredEntities.remove(target.getId());
                        }
                     }
                     BomboConfig.get().highlights.put(mob.toLowerCase(), new BomboConfig.HighlightInfo(color, si));
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aHighlight added for §e" + mob + " §7(Invis: " + si + ")"));
                     return 1;
                  }).then(ClientCommands.argument("island", StringArgumentType.greedyString()).executes((context) -> {
                     String mob = StringArgumentType.getString(context, "mob");
                     String color = StringArgumentType.getString(context, "color").toUpperCase();
                     int siInt = IntegerArgumentType.getInteger(context, "showInvisible");
                     String island = StringArgumentType.getString(context, "island").trim();
                     boolean si = siInt == 1;
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        Entity target = LookCommand.findLookTarget(mc, false);
                        if (target == null) target = mc.crosshairPickEntity;
                        if (target != null && HighlightESP.ignoredEntities.contains(target.getId())) {
                           HighlightESP.ignoredEntities.remove(target.getId());
                        }
                     }
                     BomboConfig.get().highlights.put(mob.toLowerCase(), new BomboConfig.HighlightInfo(color, si, true, false, island));
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aHighlight added for §e" + mob + " §7(Island: §b" + island + "§7, Invis: " + si + ")"));
                     return 1;
                  }))))).executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Usage: /b highlight <mob> <color> [showInvis: 0/1] [island (e.g. garden, !garden)]"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Subcommands: list, remove <name>, clear, toggle <name>"));
                     return 1;
                  }));
                  builder.then(highlightCmd);
                  builder.then(createBlockHighlightCommand("bh"));
                  builder.then(createBlockHighlightCommand("blockhighlight"));
                  builder.then(ClientCommands.literal("left").executes((context) -> {
                     BomboConfig.get().gardenMovement = true;
                     GardenMovement.toggleLeft();
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("right").executes((context) -> {
                     BomboConfig.get().gardenMovement = true;
                     GardenMovement.toggleRight();
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("back").executes((context) -> {
                     BomboConfig.get().gardenMovement = true;
                     GardenMovement.toggleBackward();
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("forw").executes((context) -> {
                     BomboConfig.get().gardenMovement = true;
                     GardenMovement.toggleForward();
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("break").executes((context) -> {
                     BomboConfig.get().gardenMovement = true;
                     GardenMovement.toggleBreak();
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("use").executes((context) -> {
                     BomboConfig.get().gardenMovement = true;
                     GardenMovement.toggleUse();
                     return 1;
                  }));
                  builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("ac").executes((context) -> {
                     AutoCroesus.performStep((FabricClientCommandSource)context.getSource());
                     return 1;
                  })).then(ClientCommands.literal("step").executes((context) -> {
                     AutoCroesus.performStep((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))).then(((LiteralArgumentBuilder)ClientCommands.literal("star").then(ClientCommands.argument("stars", IntegerArgumentType.integer(0, 10)).executes((context) -> {
                     int stars = IntegerArgumentType.getInteger(context, "stars");
                     AutoCroesus.showStarSalvage((FabricClientCommandSource)context.getSource(), "helmet", stars);
                     return 1;
                  }))).then(ClientCommands.argument("item", StringArgumentType.word()).then(ClientCommands.argument("stars", IntegerArgumentType.integer(0, 10)).executes((context) -> {
                     String item = StringArgumentType.getString(context, "item");
                     int stars = IntegerArgumentType.getInteger(context, "stars");
                     AutoCroesus.showStarSalvage((FabricClientCommandSource)context.getSource(), item, stars);
                     return 1;
                  }))))).then(ClientCommands.literal("kismet").then(ClientCommands.argument("value", StringArgumentType.greedyString()).executes((context) -> {
                     String val = StringArgumentType.getString(context, "value").trim().toLowerCase();
                     if (!val.equals("on") && !val.equals("true") && !val.equals("1")) {
                        if (!val.equals("off") && !val.equals("false") && !val.equals("0")) {
                           long threshold = parseMoneyValue(val);
                           if (threshold > 0L) {
                              BomboConfig.get().autoKismet = true;
                              BomboConfig.get().kismetThreshold = threshold;
                              BomboConfig.save();
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAuto Kismet Feather reroll enabled with threshold §b" + LowestBinManager.formatPrice(threshold) + " coins§a!"));
                           } else {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cInvalid money amount: " + val));
                           }
                        } else {
                           BomboConfig.get().autoKismet = false;
                           BomboConfig.save();
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cAuto Kismet Feather reroll disabled!"));
                        }
                     } else {
                        BomboConfig.get().autoKismet = true;
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAuto Kismet Feather reroll enabled!"));
                     }

                     return 1;
                  }))));
                  builder.then(ClientCommands.literal("gui").executes((context) -> {
                     openHudMoveNextTick = true;
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("tracerdebug").executes((context) -> {
                     BomboConfig.Settings s = BomboConfig.get();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§6§l=== Tracer Debug ==="));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§ePest ESP: " + s.pestEsp + " | Pest Tracers: " + s.pestEspTracer));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§eHighlights Enabled: " + s.highlightsEnabled + " | Test All: " + s.tracerTestAllEntities));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§eCorpse ESP: " + s.corpseEsp + " | Corpse Tracers: " + s.corpseEspStyleTracer));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cHighlightESP Render Stats:"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  - Loop Entities Count: " + HighlightESP.lastEntityCount));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  - Stored Tracers: " + HighlightESP.TRACERS.size()));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§bPest Tracers Count: " + HighlightESP.lastTracersAdded));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§bHighlights Tracers Count: " + HighlightESP.lastTracersAdded));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§bCorpse Tracers Count: " + HighlightESP.lastTracersAdded));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("corpsedebug").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.level == null) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cWorld is null!"));
                        return 1;
                     } else {
                        int totalCount = 0;
                        int standCount = 0;

                        for(Entity entity : mc.level.entitiesForRendering()) {
                           ++totalCount;
                           if (entity instanceof ArmorStand) {
                              ArmorStand stand = (ArmorStand)entity;
                              ++standCount;
                              ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
                              String itemStr = "None";
                              if (helmet != null && !helmet.isEmpty()) {
                                 itemStr = helmet.getHoverName().getString();
                              }

                              FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
                              int var10001 = stand.getId();
                              var10000.sendFeedback(Component.literal("§eStand ID: " + var10001 + " §7- Pos: " + String.valueOf(stand.blockPosition()) + " §7- Head: §b" + itemStr));
                           }
                        }

                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aScan complete. Checked " + totalCount + " entities. Found " + standCount + " armor stands."));
                        return 1;
                     }
                  }));
                  builder.then(ClientCommands.literal("resetdice").executes((context) -> {
                     DiceTracker.reset();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aDice Tracker statistics have been reset!"));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("perf").executes((context) -> {
                     BomboConfig.Settings s = BomboConfig.get();
                     s.performanceDebug = !s.performanceDebug;
                     BomboConfig.save();
                     if (s.performanceDebug) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §aPerformance Debug logging §2ENABLED§a! Profiling all features to §ebombo_perf_debug.log"));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §cPerformance Debug logging §4DISABLED§c."));
                        PerformanceProfiler.printReportToChat();
                     }
                     return 1;
                  }).then(ClientCommands.literal("report").executes((context) -> {
                     PerformanceProfiler.printReportToChat();
                     return 1;
                  })));
                  builder.then(ClientCommands.literal("msg").then(ClientCommands.argument("message", StringArgumentType.greedyString()).executes((context) -> {
                     String msg = StringArgumentType.getString(context, "message").replace('&', '§');
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.gui != null && mc.gui.getChat() != null) {
                        mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
                     }

                     processChatMessage(msg);
                     return 1;
                  })));

                  for(String argName : new String[]{"particles", "particle"}) {
                     builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(argName).executes((context) -> {
                        Map<String, Integer> summary = ParticleTracker.getSummary(ParticleTracker.espRadius);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6Nearby Particles (last 5s, radius §e" + (int)ParticleTracker.espRadius + "§6 blocks):"));
                        if (summary.isEmpty()) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §7None detected."));
                        } else {
                           for(Map.Entry<String, Integer> entry : summary.entrySet()) {
                              String keyName = (String)entry.getKey();
                              String highlightName = keyName.toLowerCase();
                              String rawType = "Unknown";
                              double lastX = (double)0.0F;
                              double lastY = (double)0.0F;
                              double lastZ = (double)0.0F;

                              for(ParticleTracker.ParticleEntry p : ParticleTracker.getEspPoints((String)null)) {
                                 if (p.type.equals(keyName)) {
                                    rawType = p.rawType;
                                    lastX = p.x;
                                    lastY = p.y;
                                    lastZ = p.z;
                                    break;
                                 }
                              }

                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §7» Highlight name: §e" + highlightName + " §8x" + String.valueOf(entry.getValue()) + " §7(Raw/Debug: §d" + rawType + "§7, Last Pos: §a" + String.format("%.2f, %.2f, %.2f", lastX, lastY, lastZ) + "§7)"));
                           }
                        }

                        return 1;
                     })).then(((LiteralArgumentBuilder)ClientCommands.literal("esp").executes((context) -> {
                        ParticleTracker.espEnabled = !ParticleTracker.espEnabled;
                        ParticleESP.typeFilter = null;
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Particle ESP: " + (ParticleTracker.espEnabled ? "§aON" : "§cOFF")));
                        return 1;
                     })).then(ClientCommands.argument("type", StringArgumentType.greedyString()).executes((context) -> {
                        String filter = StringArgumentType.getString(context, "type");
                        if (!filter.equals("off") && !filter.equals("none")) {
                           ParticleESP.typeFilter = filter;
                           ParticleTracker.espEnabled = true;
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aParticle ESP §aON §7— filtering: §e" + filter));
                        } else {
                           ParticleESP.typeFilter = null;
                           ParticleTracker.espEnabled = false;
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cParticle ESP disabled."));
                        }

                        return 1;
                     })))).then(ClientCommands.literal("radius").then(ClientCommands.argument("r", IntegerArgumentType.integer(1, 128)).executes((context) -> {
                        int r = IntegerArgumentType.getInteger(context, "r");
                        ParticleTracker.espRadius = (double)r;
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Particle ESP radius set to §e" + r + "§7 blocks."));
                        return 1;
                     })))).then(ClientCommands.literal("clear").executes((context) -> {
                        ParticleTracker.clear();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aParticle history cleared."));
                        return 1;
                     })));
                     builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("debug").executes((context) -> {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6Usage: /b debug off, /b debug commands, /b debug chat, /b debug api"));
                        return 1;
                     })).then(ClientCommands.literal("off").executes((context) -> {
                        BomboConfig.Settings s = BomboConfig.get();
                        s.debugMaster = false;
                        s.debugCommands = false;
                        s.debugChat = false;
                        s.debugGuis = false;
                        s.debugEntities = false;
                        s.debugSounds = false;
                        s.debugParticles = false;
                        s.debugMode = false;
                        s.apiDebug = false;
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAll debug logs and overlays have been disabled."));
                        return 1;
                     }))).then(ClientCommands.literal("commands").executes((context) -> {
                        BomboConfig.Settings s = BomboConfig.get();
                        s.debugCommands = !s.debugCommands;
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Command debug logging: " + (s.debugCommands ? "§aENABLED" : "§cDISABLED")));
                        return 1;
                     }))).then(((LiteralArgumentBuilder)ClientCommands.literal("chat").executes((context) -> {
                        boolean connected = IRCClient.isConnected();
                        String connType = IRCClient.getConnectionType();
                        String endpoint = IRCClient.activeEndpoint;
                        String err = IRCClient.lastError;
                        int users = IRCClient.getOnlinePlayers().size();
                        boolean isDebug = BomboConfig.get().debugChat;
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §e=== BomboChat Diagnostic Report ==="));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Status: " + (connected ? "§aCONNECTED" : "§cDISCONNECTED")));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Connection Type: §b" + connType));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Active Endpoint: §f" + endpoint));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Last Log / Socket Info: §f" + err));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Online Mod Users: §a" + users + " §7(Use §e/b online§7 to list)"));
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- Raw Chat Debug Logs: " + (isDebug ? "§aENABLED" : "§cDISABLED") + " §7(Run §e/b debug chat toggle§7 to flip)"));
                        return 1;
                     })).then(ClientCommands.literal("toggle").executes((context) -> {
                        BomboConfig.Settings s = BomboConfig.get();
                        s.debugChat = !s.debugChat;
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Chat debug logging: " + (s.debugChat ? "§aENABLED" : "§cDISABLED")));
                        return 1;
                     }))).then(ClientCommands.literal("events").executes((context) -> {
                        showDebugApiEvents((FabricClientCommandSource)context.getSource());
                        return 1;
                     }))).then(((LiteralArgumentBuilder)ClientCommands.literal("api").executes((context) -> {
                        runApiDebugTests();
                        return 1;
                     })).then(ClientCommands.literal("events").executes((context) -> {
                        showDebugApiEvents((FabricClientCommandSource)context.getSource());
                        return 1;
                     }))).then(((LiteralArgumentBuilder)ClientCommands.literal("bestiary").executes((context) -> {
                        return showBestiaryDebug((FabricClientCommandSource)context.getSource(), null);
                     })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                        String q = StringArgumentType.getString(context, "query").trim();
                        return showBestiaryDebug((FabricClientCommandSource)context.getSource(), q);
                     }))));
                  }

                  builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("anvil").then(((LiteralArgumentBuilder)ClientCommands.literal("add").then(ClientCommands.argument("tier", IntegerArgumentType.integer(1, 100)).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        return 0;
                     } else {
                        ItemStack hand = mc.player.getMainHandItem();
                        if (hand.isEmpty()) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cPlease hold an enchanted book in your main hand!"));
                           return 0;
                        } else {
                           Map<String, Integer> enchants = getEnchantments(hand);
                           if (enchants.isEmpty()) {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo enchantments found on this item! §8(NBT may be flat or missing ExtraAttributes)"));
                              return 0;
                           } else {
                              int tier = IntegerArgumentType.getInteger(context, "tier");

                              for(String enc : enchants.keySet()) {
                                 BomboConfig.get().anvilAutoCombine.put(enc, tier);
                                 ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAdded anvil auto-combine: §e" + enc + " §7(Target Tier: " + tier + ")"));
                              }

                              BomboConfig.save();
                              return 1;
                           }
                        }
                     }
                  }))).then(ClientCommands.argument("enchant", StringArgumentType.word()).then(ClientCommands.argument("tier", IntegerArgumentType.integer(1, 100)).executes((context) -> {
                     String enc = StringArgumentType.getString(context, "enchant").toLowerCase();
                     int tier = IntegerArgumentType.getInteger(context, "tier");
                     BomboConfig.get().anvilAutoCombine.put(enc, tier);
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAdded anvil auto-combine: §e" + enc + " §7(Target Tier: " + tier + ")"));
                     return 1;
                  }))))).then(ClientCommands.literal("remove").then(ClientCommands.argument("enchant", StringArgumentType.word()).executes((context) -> {
                     String enc = StringArgumentType.getString(context, "enchant").toLowerCase();
                     if (BomboConfig.get().anvilAutoCombine.remove(enc) != null) {
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aRemoved anvil auto-combine for: §e" + enc));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo anvil auto-combine found for: §e" + enc));
                     }

                     return 1;
                  })))).then(ClientCommands.literal("list").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6--- Anvil Auto-Combine ---"));
                     if (BomboConfig.get().anvilAutoCombine.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §7None"));
                     } else {
                        for(Map.Entry<String, Integer> entry : BomboConfig.get().anvilAutoCombine.entrySet()) {
                           FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
                           String var10001 = (String)entry.getKey();
                           var10000.sendFeedback(Component.literal("  §e" + var10001 + " §7- §bTier " + String.valueOf(entry.getValue())));
                        }
                     }

                     return 1;
                  })));
                  builder.then(ClientCommands.literal("view").then(ClientCommands.argument("username", StringArgumentType.string()).then(ClientCommands.argument("path", StringArgumentType.greedyString()).executes((context) -> {
                     String user = StringArgumentType.getString(context, "username");
                     String pathWithHighlight = StringArgumentType.getString(context, "path");
                     int highlight = -1;
                     String path = pathWithHighlight;
                     if (pathWithHighlight.contains(" ")) {
                        try {
                           int lastSpace = pathWithHighlight.lastIndexOf(" ");
                           highlight = Integer.parseInt(pathWithHighlight.substring(lastSpace + 1));
                           path = pathWithHighlight.substring(0, lastSpace);
                        } catch (Exception var6) {
                        }
                     }

                     LF.openVirtualContainer(user, path.replace("\"", ""), highlight);
                     return 1;
                  }))));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("lb").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        LF.show(mc.getUser().getName(), "", false);
                     }

                     return 1;
                  })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                     String query = StringArgumentType.getString(context, "query");
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        String name = mc.getUser().getName();
                        LF.show(name, query, false);
                     }

                     return 1;
                  })));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("lbc").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        LF.show(mc.getUser().getName(), "", true);
                     }

                     return 1;
                  })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                     String query = StringArgumentType.getString(context, "query");
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        String name = mc.getUser().getName();
                        LF.show(name, query, true);
                     }

                     return 1;
                  })));
                  builder.then(ClientCommands.literal("lf").then(((RequiredArgumentBuilder)ClientCommands.argument("username", StringArgumentType.string()).executes((context) -> {
                     LF.show(StringArgumentType.getString(context, "username"), "", false);
                     return 1;
                  })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                     LF.show(StringArgumentType.getString(context, "username"), StringArgumentType.getString(context, "query"), false);
                     return 1;
                  }))));
                  builder.then(ClientCommands.literal("lfc").then(((RequiredArgumentBuilder)ClientCommands.argument("username", StringArgumentType.string()).executes((context) -> {
                     LF.show(StringArgumentType.getString(context, "username"), "", true);
                     return 1;
                  })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                     LF.show(StringArgumentType.getString(context, "username"), StringArgumentType.getString(context, "query"), true);
                     return 1;
                  }))));
                  builder.then(((LiteralArgumentBuilder)ClientCommands.literal("lb").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        LF.show(mc.getUser().getName(), "", false);
                     }

                     return 1;
                  })).then(ClientCommands.argument("query", StringArgumentType.greedyString()).executes((context) -> {
                     String query = StringArgumentType.getString(context, "query");
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        String name = mc.getUser().getName();
                        LF.show(name, query, false);
                     }

                     return 1;
                  })));
                  builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("pet").then(ClientCommands.literal("save").then(ClientCommands.argument("slot", StringArgumentType.word()).executes((context) -> {
                     String slot = StringArgumentType.getString(context, "slot");
                     PetManager.savePet((FabricClientCommandSource)context.getSource(), slot);
                     return 1;
                  })))).then(ClientCommands.literal("apply").then(ClientCommands.argument("slot", StringArgumentType.word()).executes((context) -> {
                     String slot = StringArgumentType.getString(context, "slot");
                     PetManager.applyPet((FabricClientCommandSource)context.getSource(), slot);
                     return 1;
                  })))).then(ClientCommands.argument("slot", StringArgumentType.word()).executes((context) -> {
                     String slot = StringArgumentType.getString(context, "slot");
                     PetManager.applyPet((FabricClientCommandSource)context.getSource(), slot);
                     return 1;
                  })));
                  builder.then(ClientCommands.literal("ep").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        int pearlCount = 0;

                        for(int j = 0; j < mc.player.getInventory().getContainerSize(); ++j) {
                           ItemStack stack = mc.player.getInventory().getItem(j);
                           if (!stack.isEmpty()) {
                              String internalId = SkyblockUtils.getInternalId(stack);
                              if ("ENDER_PEARL".equals(internalId) || stack.is(Items.ENDER_PEARL)) {
                                 pearlCount += stack.getCount();
                              }
                           }
                        }

                        if (pearlCount < 16) {
                           int toGet = 16 - pearlCount;
                           mc.player.connection.sendCommand("gfs ENDER_PEARL " + toGet);
                           if (BomboConfig.get().debugCommands || BomboConfig.get().debugMaster) {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7[Bombo] Found §e" + pearlCount + "§7 pearls. Requesting §e" + toGet + "§7 more from sack!"));
                           }
                        } else if (BomboConfig.get().debugCommands || BomboConfig.get().debugMaster) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7[Bombo] Already have §e" + pearlCount + "§7 pearls (>= 16)."));
                        }
                     }

                     return 1;
                  }));
                  builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("get").then(ClientCommands.literal("add").then(ClientCommands.argument("number", IntegerArgumentType.integer(1)).then(ClientCommands.argument("alias", StringArgumentType.word()).executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player == null) {
                        return 0;
                     } else {
                        ItemStack hand = mc.player.getMainHandItem();
                        if (hand.isEmpty()) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cPlease hold the item you want to add in your main hand!"));
                           return 0;
                        } else {
                           String itemId = SkyblockUtils.getInternalId(hand);
                           if (itemId == null || itemId.isEmpty()) {
                              itemId = BuiltInRegistries.ITEM.getKey(hand.getItem()).getPath().toUpperCase();
                           }

                           int number = IntegerArgumentType.getInteger(context, "number");
                           String alias = StringArgumentType.getString(context, "alias").toLowerCase();
                           BomboConfig.get().getTargets.put(alias, new BomboConfig.GetTarget(itemId, number));
                           BomboConfig.save();
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAdded get target: §e" + itemId + " §7(Target: " + number + ") under alias §b" + alias));
                           return 1;
                        }
                     }
                  }))))).then(ClientCommands.literal("remove").then(ClientCommands.argument("alias", StringArgumentType.word()).executes((context) -> {
                     String alias = StringArgumentType.getString(context, "alias").toLowerCase();
                     if (BomboConfig.get().getTargets.remove(alias) != null) {
                        BomboConfig.save();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aRemoved get target for alias §e" + alias));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo get target found for alias §e" + alias));
                     }

                     return 1;
                  })))).then(ClientCommands.literal("list").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6--- Get Targets ---"));
                     if (BomboConfig.get().getTargets.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §7None"));
                     } else {
                        for(Map.Entry<String, BomboConfig.GetTarget> entry : BomboConfig.get().getTargets.entrySet()) {
                           FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
                           String var10001 = (String)entry.getKey();
                           var10000.sendFeedback(Component.literal("  §eb get " + var10001 + " §7-> §b" + ((BomboConfig.GetTarget)entry.getValue()).itemId + " §7(Target: " + ((BomboConfig.GetTarget)entry.getValue()).targetAmount + ")"));
                        }
                     }

                     return 1;
                  }))).then(ClientCommands.argument("alias_or_id", StringArgumentType.word()).executes((context) -> {
                     String alias = StringArgumentType.getString(context, "alias_or_id").toLowerCase();
                     BomboConfig.GetTarget target = (BomboConfig.GetTarget)BomboConfig.get().getTargets.get(alias);
                     if (target == null) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cNo get target found for alias §e" + alias));
                        return 0;
                     } else {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                           int currentCount = 0;

                           for(int j = 0; j < mc.player.getInventory().getContainerSize(); ++j) {
                              ItemStack stack = mc.player.getInventory().getItem(j);
                              if (!stack.isEmpty()) {
                                 String itemId = SkyblockUtils.getInternalId(stack);
                                 if (itemId == null || itemId.isEmpty()) {
                                    itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase();
                                 }

                                 if (itemId.equalsIgnoreCase(target.itemId)) {
                                    currentCount += stack.getCount();
                                 }
                              }
                           }

                           if (currentCount < target.targetAmount) {
                              int missing = target.targetAmount - currentCount;
                              mc.player.connection.sendCommand("gfs " + target.itemId + " " + missing);
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Requesting §e" + missing + " §7more §e" + target.itemId + " §7(missing §e" + missing + "/" + target.targetAmount + "§7)."));
                           } else {
                              ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Already have §e" + currentCount + "/" + target.targetAmount + " §e" + target.itemId + "§7."));
                           }
                        }

                        return 1;
                     }
                  }).then(ClientCommands.argument("number", IntegerArgumentType.integer(1)).then(ClientCommands.argument("alias", StringArgumentType.word()).executes((context) -> {
                     String itemId = StringArgumentType.getString(context, "alias_or_id").toUpperCase();
                     int number = IntegerArgumentType.getInteger(context, "number");
                     String alias = StringArgumentType.getString(context, "alias").toLowerCase();
                     BomboConfig.get().getTargets.put(alias, new BomboConfig.GetTarget(itemId, number));
                     BomboConfig.save();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §aAdded get target: §e" + itemId + " §7(Target: " + number + ") under alias §b" + alias));
                     return 1;
                   })))));
                   builder.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("egg").then(ClientCommands.literal("status").executes((context) -> {
                      boolean connected = EggWebSocket.isConnected();
                      boolean connecting = EggWebSocket.isConnecting();
                      String sub = EggWebSocket.getActiveSubscription();
                      String statusColor = connected ? "§aConnected" : (connecting ? "§eConnecting..." : "§cDisconnected");
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: " + statusColor));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Active Subscription Area: §e" + (sub != null ? sub : "None")));
                      return 1;
                   }))).then(ClientCommands.literal("reconnect").executes((context) -> {
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §eRe-authenticating and reconnecting to Egg Finder WebSocket..."));
                      EggAuth.forceUpdateToken();
                      EggWebSocket.forceReconnect();
                      return 1;
                   }))).executes((context) -> {
                      boolean connected = EggWebSocket.isConnected();
                      boolean connecting = EggWebSocket.isConnecting();
                      String sub = EggWebSocket.getActiveSubscription();
                      String statusColor = connected ? "§aConnected" : (connecting ? "§eConnecting..." : "§cDisconnected");
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Egg Finder WebSocket Status: " + statusColor));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Active Subscription Area: §e" + (sub != null ? sub : "None")));
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Usage: §e/b egg status §7or §e/b egg reconnect"));
                      return 1;
                   }));
                  builder.then(ClientCommands.literal("command").executes((context) -> {
                      if (lastDetectedCommand != null && !lastDetectedCommand.isEmpty()) {
                         Minecraft mc = Minecraft.getInstance();
                         if (mc.player != null && mc.player.connection != null) {
                            String toRun = lastDetectedCommand.startsWith("/") ? lastDetectedCommand.substring(1) : lastDetectedCommand;
                            ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §aExecuting: §e/" + toRun));
                            mc.player.connection.sendCommand(toRun);
                         }
                      } else {
                         ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §cNo command detected in recent chat messages."));
                      }
                      return 1;
                   }));
                   builder.then(ClientCommands.literal("cmd").executes((context) -> {
                      if (lastDetectedCommand != null && !lastDetectedCommand.isEmpty()) {
                         Minecraft mc = Minecraft.getInstance();
                         if (mc.player != null && mc.player.connection != null) {
                            String toRun = lastDetectedCommand.startsWith("/") ? lastDetectedCommand.substring(1) : lastDetectedCommand;
                            ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §aExecuting: §e/" + toRun));
                            mc.player.connection.sendCommand(toRun);
                         }
                      } else {
                         ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §cNo command detected in recent chat messages."));
                      }
                      return 1;
                   }));
                   builder.then(ClientCommands.literal("online").executes((context) -> {
                     if (!BomboConfig.get().ircChatEnabled) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §cIRC Chat is currently disabled. Toggle it ON in the config GUI to see online users."));
                        return 1;
                     } else if (!IRCClient.isConnected()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Connecting to IRC server... (Please wait a moment and try again)"));
                        IRCClient.start();
                        return 1;
                     } else {
                        IRCClient.requestNames();
                        Map<String, IRCClient.ModUser> onlineMap = IRCClient.getOnlinePlayers();
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §6Online Mod Users:"));
                        int count = 0;
                        for(IRCClient.ModUser user : onlineMap.values()) {
                           if (user.username.equalsIgnoreCase("Discord") || user.username.startsWith("Discord_")) {
                              continue;
                           }
                           String areaStr = (user.area != null && !user.area.isEmpty() && !user.area.equalsIgnoreCase("Unknown") && !user.area.equalsIgnoreCase("None")) ? " §7[§b" + user.area + "§7]" : "";
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §7» §a" + user.username + " §7— Version: §e" + user.version + areaStr));
                           ++count;
                        }

                         if (count == 0) {
                            ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §7No other users detected yet (or currently fetching names list)."));
                         } else {
                            ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7Total online: §b" + count));
                         }

                         return 1;
                      }
                   }));
                   builder.then(ClientCommands.literal("server").then(ClientCommands.argument("server_ip", StringArgumentType.greedyString()).executes((context) -> {
                      String serverIp = StringArgumentType.getString(context, "server_ip").trim();
                      ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Fetching info for server §e" + serverIp + "§7..."));
                      (new Thread(() -> {
                         try {
                            java.net.URI uri = java.net.URI.create("https://api.bombo.dpdns.org/mc/" + java.net.URLEncoder.encode(serverIp, java.nio.charset.StandardCharsets.UTF_8));
                            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(6)).build();
                            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                            if (resp.statusCode() == 200 && resp.body() != null) {
                               com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
                               boolean online = obj.has("online") && obj.get("online").getAsBoolean();
                               Minecraft mc = Minecraft.getInstance();
                               if (mc != null && mc.player != null) {
                                  mc.execute(() -> {
                                     if (mc.player != null) {
                                        mc.player.sendSystemMessage(Component.literal("§8---------------- §b[Server Info: " + serverIp + "] §8----------------"));
                                        if (!online) {
                                           mc.player.sendSystemMessage(Component.literal("§cStatus: OFFLINE / Unreachable"));
                                        } else {
                                           mc.player.sendSystemMessage(Component.literal("§aStatus: §2ONLINE"));
                                           if (obj.has("version")) {
                                              mc.player.sendSystemMessage(Component.literal("§7Version: §e" + obj.get("version").getAsString()));
                                           }
                                           if (obj.has("players") && obj.get("players").isJsonObject()) {
                                              com.google.gson.JsonObject pObj = obj.getAsJsonObject("players");
                                              int pOnline = pObj.has("online") ? pObj.get("online").getAsInt() : 0;
                                              int pMax = pObj.has("max") ? pObj.get("max").getAsInt() : 0;
                                              mc.player.sendSystemMessage(Component.literal("§7Players: §b" + pOnline + "§7/§b" + pMax));
                                           }
                                           if (obj.has("motd") && obj.get("motd").isJsonObject()) {
                                              com.google.gson.JsonObject mObj = obj.getAsJsonObject("motd");
                                              if (mObj.has("raw") && mObj.get("raw").isJsonArray()) {
                                                 mc.player.sendSystemMessage(Component.literal("§7MOTD:"));
                                                 for (com.google.gson.JsonElement el : mObj.getAsJsonArray("raw")) {
                                                    mc.player.sendSystemMessage(Component.literal(el.getAsString().replace('&', '§')));
                                                 }
                                              } else if (mObj.has("clean") && mObj.get("clean").isJsonArray()) {
                                                 mc.player.sendSystemMessage(Component.literal("§7MOTD:"));
                                                 for (com.google.gson.JsonElement el : mObj.getAsJsonArray("clean")) {
                                                    mc.player.sendSystemMessage(Component.literal("§f" + el.getAsString()));
                                                 }
                                              }
                                           }
                                           if (obj.has("ip")) {
                                              String ip = obj.get("ip").getAsString();
                                              int port = obj.has("port") ? obj.get("port").getAsInt() : 25565;
                                              mc.player.sendSystemMessage(Component.literal("§7Direct IP: §8" + ip + ":" + port));
                                           }
                                        }
                                        mc.player.sendSystemMessage(Component.literal("§8----------------------------------------------------"));
                                     }
                                  });
                               }
                            } else {
                               Minecraft mc = Minecraft.getInstance();
                               if (mc != null && mc.player != null) {
                                  mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §cFailed to fetch info for " + serverIp + " (HTTP " + resp.statusCode() + ")")));
                               }
                            }
                         } catch (Throwable t) {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null && mc.player != null) {
                               mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §cError fetching server info: " + t.getMessage())));
                            }
                         }
                      }, "Bombo-Server-Status-Thread")).start();
                      return 1;
                   })));
                  builder.then(ClientCommands.literal("color").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8---------------- §b[Color Codes] §8----------------"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §0&0 - Black        §1&1 - Dark Blue"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §2&2 - Dark Green   §3&3 - Dark Aqua"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §4&4 - Dark Red     §5&5 - Dark Purple"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §6&6 - Gold         §7&7 - Gray"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §8&8 - Dark Gray    §9&9 - Blue"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §a&a - Green        §b&b - Aqua"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §c&c - Red          §d&d - Light Purple"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §e&e - Yellow       §f&f - White"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8Formatting Codes:"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §k&k - Obfuscated   §l&l - Bold"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §m&m - Strikethrough§n&n - Underline"));
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("  §o&o - Italic       §r&r - Reset"));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("area").executes((context) -> {
                     String area = SkyblockUtils.getLocation();
                     String sub = SkyblockUtils.getSubArea();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBombo§8] §7Area: §e" + area + " §7| Subarea: §a" + sub));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("subarea").executes((context) -> {
                     String sub = SkyblockUtils.getSubArea();
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBombo§8] §7Subarea: §a" + sub));
                     return 1;
                  }));
                  builder.then(ClientCommands.literal("secrets").executes((context) -> {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.getConnection() != null) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBombo§8] §7Fetching current secrets..."));

                        for(PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                           String name = info.getProfile().name();
                           if (name != null && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                              UUID uuid = info.getProfile().id();
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
               dispatcher.register(createBlockHighlightCommand("bh"));
               dispatcher.register(createBlockHighlightCommand("blockhighlight"));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("bomboprof").executes((context) -> {
                  pendingConfigSearch = "Profile";
                  openGuiNextTick = true;
                  return 1;
               }));
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("bnav").executes((context) -> {
                  String currentLoc = SkyblockUtils.getLocation();
                  if (currentLoc == null) {
                     currentLoc = "Unknown";
                  }

                  String formattedLoc = currentLoc.replace(" ", "_");
                  List<String> names = new ArrayList();

                  for(SkyblockItemManager.SkyblockItemInfo npc : SkyblockItemManager.getAllNpcs()) {
                     if (npc.island != null) {
                        String nIsle = npc.island.toLowerCase().replace(" ", "_");
                        String cLoc = currentLoc.toLowerCase().replace(" ", "_");
                        String fLoc = formattedLoc.toLowerCase();
                        boolean matches = false;
                        if (!nIsle.equals(cLoc) && !nIsle.equals(fLoc)) {
                           if (!nIsle.equals("hub") || !cLoc.equals("the_hub") && !cLoc.equals("village")) {
                              if (!nIsle.equals("the_farming_islands") || !cLoc.contains("barn") && !cLoc.contains("mushroom")) {
                                 if (nIsle.equals("spider_den") && cLoc.equals("spider's_den")) {
                                    matches = true;
                                 }
                              } else {
                                 matches = true;
                              }
                           } else {
                              matches = true;
                           }
                        } else {
                           matches = true;
                        }

                        if (matches) {
                           names.add(npc.name != null ? npc.name.replaceAll("(?i)§[0-9a-fk-or]", "") : npc.id);
                        }
                     }
                  }

                  Collections.sort(names);
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aNPCs on " + currentLoc + ":"));

                  for(String n : names) {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§e- " + n));
                  }

                  return 1;
               })).then(ClientCommands.literal("clear").executes((context) -> {
                  WaypointManager.clearNav();
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aNavigation cleared."));
                  return 1;
               }))).then(ClientCommands.argument("npc", StringArgumentType.greedyString()).suggests((context, builder) -> {
                  String currentLoc = SkyblockUtils.getLocation();
                  if (currentLoc != null && !currentLoc.equals("Unknown")) {
                     String formattedLoc = currentLoc.replace(" ", "_");
                     List<String> suggestions = new ArrayList();

                     for(SkyblockItemManager.SkyblockItemInfo npc : SkyblockItemManager.getAllNpcs()) {
                        if (npc.island != null) {
                           String nIsle = npc.island.toLowerCase().replace(" ", "_");
                           String cLoc = currentLoc.toLowerCase().replace(" ", "_");
                           String fLoc = formattedLoc.toLowerCase();
                           boolean matches = false;
                           if (!nIsle.equals(cLoc) && !nIsle.equals(fLoc)) {
                              if (!nIsle.equals("hub") || !cLoc.equals("the_hub") && !cLoc.equals("village")) {
                                 if (!nIsle.equals("the_farming_islands") || !cLoc.contains("barn") && !cLoc.contains("mushroom")) {
                                    if (nIsle.equals("spider_den") && cLoc.equals("spider's_den")) {
                                       matches = true;
                                    }
                                 } else {
                                    matches = true;
                                 }
                              } else {
                                 matches = true;
                              }
                           } else {
                              matches = true;
                           }

                           if (matches) {
                              suggestions.add(npc.name != null ? npc.name.replaceAll("(?i)§[0-9a-fk-or]", "") : npc.id);
                           }
                        }
                     }

                     Collections.sort(suggestions);
                     String remaining = builder.getRemaining().toLowerCase();

                     for(String s : suggestions) {
                        if (s.toLowerCase().startsWith(remaining)) {
                           builder.suggest(s);
                        }
                     }

                     return builder.buildFuture();
                  } else {
                     return builder.buildFuture();
                  }
               }).executes((context) -> {
                  String name = StringArgumentType.getString(context, "npc");
                  String[] parts = name.split(" ");
                  if (parts.length == 3) {
                     try {
                        double nx = Double.parseDouble(parts[0]);
                        double ny = Double.parseDouble(parts[1]);
                        double nz = Double.parseDouble(parts[2]);
                        WaypointManager.setNavPath(nx, ny, nz, "Destination", (String)null);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aNavigating to §e" + nx + ", " + ny + ", " + nz));
                        return 1;
                     } catch (NumberFormatException var9) {
                     }
                  }

                  SkyblockItemManager.SkyblockItemInfo npc = SkyblockItemManager.getNpcByName(name);
                  if (npc != null) {
                     WaypointManager.setNavPath((double)npc.x, (double)npc.y, (double)npc.z, npc.name, npc.island);
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aNavigating to §e" + npc.name));
                     if (npc.island != null && !npc.island.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7(Island: " + npc.island + ")"));
                     }
                  } else {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cNPC not found."));
                  }

                  return 1;
               })));
            } catch (Throwable t) {
               Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register /bombo commands!", t);
            }

            try {
               String[] sbeRoots = new String[]{"nw", "nwc", "cata", "skills", "slayer", "trophyfish", "crimson"};

               for(String s : sbeRoots) {
                  dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(s).executes((context) -> {
                     SBECommands.handleCommand(s, Minecraft.getInstance().player.getName().getString(), (String)null);
                     return 1;
                  })).then(((RequiredArgumentBuilder)ClientCommands.argument("name", StringArgumentType.word()).executes((context) -> {
                     SBECommands.handleCommand(s, StringArgumentType.getString(context, "name"), (String)null);
                     return 1;
                  })).then(ClientCommands.argument("profile", StringArgumentType.word()).executes((context) -> {
                     SBECommands.handleCommand(s, StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "profile"));
                     return 1;
                  }))));
               }
            } catch (Throwable t) {
               Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register SBE root commands!", t);
            }

            try {
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("bombo_highlight_slot").then(ClientCommands.argument("slots", StringArgumentType.string()).then(ClientCommands.argument("command", StringArgumentType.greedyString()).executes((context) -> {
                  String slots = StringArgumentType.getString(context, "slots");
                  String cmd = StringArgumentType.getString(context, "command");

                  for(String s : slots.replace("\"", "").split(",")) {
                     try {
                        SlotHighlight.addTargetSlot(Integer.parseInt(s), -2147418368);
                     } catch (NumberFormatException var8) {
                        SlotHighlight.addTargetName(s, -2147418368);
                     }
                  }

                  executeTracked(cmd);
                  return 1;
               }))));
               long[] lastMuseumClick = new long[]{0L};
               String[] lastMuseumTarget = new String[]{""};
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("bombo_museum_click").then(ClientCommands.argument("username", StringArgumentType.string()).then(ClientCommands.argument("slot", IntegerArgumentType.integer()).executes((context) -> {
                  String user = StringArgumentType.getString(context, "username");
                  int slot = IntegerArgumentType.getInteger(context, "slot");
                  long now = System.currentTimeMillis();
                  String target = user + ":" + slot;
                  if (now - lastMuseumClick[0] < 2000L && target.equals(lastMuseumTarget[0])) {
                     executeTracked("/warp museum");
                     lastMuseumClick[0] = 0L;
                     lastMuseumTarget[0] = "";
                  } else {
                     lastMuseumClick[0] = now;
                     lastMuseumTarget[0] = target;
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7[Bombo] Click again within 2s to §b/warp museum§7!"));
                  }

                  return 1;
               }))));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("tk").then(((RequiredArgumentBuilder)ClientCommands.argument("username", StringArgumentType.string()).executes((context) -> {
                  LF.showToolkit(StringArgumentType.getString(context, "username"), 50);
                  return 1;
               })).then(ClientCommands.argument("limit", IntegerArgumentType.integer(1)).executes((context) -> {
                  LF.showToolkit(StringArgumentType.getString(context, "username"), IntegerArgumentType.getInteger(context, "limit"));
                  return 1;
               }))));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("deal").executes((context) -> {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null && mc.level != null) {
                     Set<String> tabPlayerNames = new HashSet();
                     if (mc.getConnection() != null) {
                        for(PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                           String name = info.getProfile().name();
                           if (name != null) {
                              tabPlayerNames.add(cleanName(name));
                           }

                           if (info.getTabListDisplayName() != null) {
                              tabPlayerNames.add(cleanName(info.getTabListDisplayName().getString()));
                           }
                        }
                     }

                     List<Player> nearbyPlayers = new ArrayList();

                     for(Player p : mc.level.players()) {
                        if (p != mc.player) {
                           String pName = p.getGameProfile().name();
                           if (tabPlayerNames.contains(cleanName(pName))) {
                              double distSq = p.distanceToSqr(mc.player);
                              if (distSq <= (double)100.0F) {
                                 nearbyPlayers.add(p);
                              }
                           }
                        }
                     }

                     String targetName = null;
                     if (nearbyPlayers.isEmpty()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cNo players nearby within 10 blocks!"));
                        return 0;
                     } else {
                        label81: {
                           if (nearbyPlayers.size() == 1) {
                              targetName = ((Player)nearbyPlayers.get(0)).getGameProfile().name();
                           } else {
                              HitResult patt0$temp = mc.hitResult;
                              if (!(patt0$temp instanceof EntityHitResult)) {
                                 break label81;
                              }

                              EntityHitResult ehr = (EntityHitResult)patt0$temp;
                              Entity patt1$temp = ehr.getEntity();
                              if (!(patt1$temp instanceof Player)) {
                                 break label81;
                              }

                              Player p = (Player)patt1$temp;
                              String pName = p.getGameProfile().name();
                              if (!tabPlayerNames.contains(cleanName(pName))) {
                                 ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cLooking at an NPC, not a real player!"));
                                 return 0;
                              }

                              if (!(p.distanceToSqr(mc.player) <= (double)100.0F)) {
                                 ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cPlayer too far away!"));
                                 return 0;
                              }

                              targetName = pName;
                           }

                           if (targetName != null) {
                              final String finalTargetName = targetName;
                              mc.execute(() -> {
                                 if (mc.player != null) {
                                    mc.player.connection.sendCommand("trade " + finalTargetName);
                                 }

                              });
                           }

                           return 1;
                        }

                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cMultiple players nearby. Look at one!"));
                        return 0;
                     }
                  } else {
                     return 0;
                  }
               }));
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("bits").executes((context) -> {
                  BitsManager.fetchTopBits(5).thenAccept((lines) -> Minecraft.getInstance().execute(() -> {
                        for(String line : lines) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal(line));
                        }

                     }));
                  return 1;
               })).then(ClientCommands.argument("amount", IntegerArgumentType.integer(1, 100)).executes((context) -> {
                  int amount = IntegerArgumentType.getInteger(context, "amount");
                  BitsManager.fetchTopBits(amount).thenAccept((lines) -> Minecraft.getInstance().execute(() -> {
                        for(String line : lines) {
                           ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal(line));
                        }

                     }));
                  return 1;
               })));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("bclick").executes((context) -> {
                  ClickLogic.listTargets((FabricClientCommandSource)context.getSource());
                  return 1;
               }));
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("bc").executes((context) -> {
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §cUsage: /bc <message>"));
                  return 1;
               })).then(ClientCommands.argument("message", StringArgumentType.greedyString()).executes((context) -> {
                  if (!BomboConfig.get().ircChatEnabled) {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §cIRC Chat is currently disabled! Toggle it on with §e/b chat§c."));
                     return 1;
                  } else {
                     String message = StringArgumentType.getString(context, "message");
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.player != null) {
                        int x = (int)Math.floor(mc.player.getX());
                        int y = (int)Math.floor(mc.player.getY());
                        int z = (int)Math.floor(mc.player.getZ());
                        if (message.contains("$coords")) {
                           message = message.replace("$coords", x + " " + y + " " + z);
                        }

                        if (message.contains("$coord")) {
                           message = message.replace("$coord", "x: " + x + ", y: " + y + ", z: " + z);
                        }
                     }

                     IRCClient.sendMessage(message);
                     return 1;
                  }
               })));
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("bctest").executes((context) -> {
                  boolean conn = IRCClient.isConnected();
                  FabricClientCommandSource var10000 = (FabricClientCommandSource)context.getSource();
                  String var10001 = conn ? "§aCONNECTED to bombo.dpdns.org" : "§cDISCONNECTED (" + IRCClient.lastError + ")";
                  var10000.sendFeedback(Component.literal("§8[§bBomboAddons§8] §eBomboChat Status: " + var10001));
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Simulating incoming Discord relay message..."));
                  Minecraft.getInstance().player.sendSystemMessage(Component.literal("§r§8[§r§3Bombo§r§8] §9[DC] bumboclat§f: §rwawawa!"));
                  return 1;
               })).then(ClientCommands.argument("message", StringArgumentType.greedyString()).executes((context) -> {
                  String msg = StringArgumentType.getString(context, "message");
                  boolean conn = IRCClient.isConnected();
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §eBomboChat Connected: " + conn + " | Sending test message: §f" + msg));
                  IRCClient.sendMessage(msg);
                  Minecraft.getInstance().player.sendSystemMessage(Component.literal("§r§8[§r§3Bombo§r§8] §9[DC] bumboclat§f: §r" + msg));
                  return 1;
               })));
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("chat").then(ClientCommands.literal("b").executes((context) -> {
                  BomboConfig.get().ircDefaultChat = true;
                  BomboConfig.save();
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Default chat set to §eIRC§7. Messages will be sent to IRC chat."));
                  return 1;
               }))).then(ClientCommands.argument("channel", StringArgumentType.greedyString()).executes((context) -> {
                  String channel = StringArgumentType.getString(context, "channel");
                  BomboConfig.get().ircDefaultChat = false;
                  BomboConfig.save();
                  if (Minecraft.getInstance().getConnection() != null) {
                     Minecraft.getInstance().getConnection().send(new ServerboundChatCommandPacket("chat " + channel));
                  }

                  return 1;
               }))).executes((context) -> {
                  if (Minecraft.getInstance().getConnection() != null) {
                     Minecraft.getInstance().getConnection().send(new ServerboundChatCommandPacket("chat"));
                  }

                  return 1;
               }));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("c").then(ClientCommands.argument("expression", StringArgumentType.greedyString()).executes((context) -> {
                  SkyblockCalculator.EvaluationResult res = SkyblockCalculator.evaluate(StringArgumentType.getString(context, "expression"));
                  ((FabricClientCommandSource)context.getSource()).sendFeedback((Component)(res.error != null ? Component.literal(res.error) : res.breakdown));
                  return 1;
               })));
            } catch (Throwable t) {
               Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register util commands!", t);
            }

            try {
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("checki").then(ClientCommands.literal("list").executes((context) -> {
                  InventoryManager.listSnapshots((FabricClientCommandSource)context.getSource());
                  return 1;
               }))).then(((RequiredArgumentBuilder)ClientCommands.argument("name", StringArgumentType.string()).suggests((context, builder) -> {
                  for(String name : InventoryManager.getSnapshotNames()) {
                     if (name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                        builder.suggest(name.contains(" ") ? "\"" + name + "\"" : name);
                     }
                  }

                  return builder.buildFuture();
               }).executes((context) -> {
                  InventoryManager.openSnapshot(StringArgumentType.getString(context, "name"), 1);
                  return 1;
               })).then(ClientCommands.argument("index", IntegerArgumentType.integer(1)).executes((context) -> {
                  InventoryManager.openSnapshot(StringArgumentType.getString(context, "name"), IntegerArgumentType.getInteger(context, "index"));
                  return 1;
               }))));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("savei").executes((context) -> {
                  InventoryManager.captureCurrentGUI();
                  return 1;
               }));
               Consumer<String> registerHotbarCommand = (nameLiteral) -> dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(nameLiteral).then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.string()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name");
                     if (HotbarSwapper.saveSnapshot(name)) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aSaved hotbar snapshot: §e" + name));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cFailed to save hotbar snapshot (player is null)."));
                     }

                     return 1;
                  })))).then(ClientCommands.literal("s").then(ClientCommands.argument("name", StringArgumentType.string()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name");
                     if (HotbarSwapper.saveSnapshot(name)) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aSaved hotbar snapshot: §e" + name));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cFailed to save hotbar snapshot (player is null)."));
                     }

                     return 1;
                  })))).then(ClientCommands.literal("delete").then(ClientCommands.argument("name", StringArgumentType.string()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name");
                     if (HotbarSwapper.deleteSnapshot(name)) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aDeleted hotbar snapshot: §e" + name));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cSnapshot not found: §e" + name));
                     }

                     return 1;
                  })))).then(ClientCommands.literal("list").executes((context) -> {
                     ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§6--- Hotbar Snapshots ---"));

                     for(String id : HotbarSwapper.list()) {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§7- §e" + id));
                     }

                     return 1;
                  }))).then(ClientCommands.literal("apply").then(ClientCommands.argument("name", StringArgumentType.string()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name");
                     if (HotbarSwapper.exists(name)) {
                        HotbarSwapper.apply(name);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aApplied hotbar snapshot: §e" + name));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cSnapshot not found: §e" + name));
                     }

                     return 1;
                  })))).then(ClientCommands.literal("a").then(ClientCommands.argument("name", StringArgumentType.string()).executes((context) -> {
                     String name = StringArgumentType.getString(context, "name");
                     if (HotbarSwapper.exists(name)) {
                        HotbarSwapper.apply(name);
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aApplied hotbar snapshot: §e" + name));
                     } else {
                        ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§cSnapshot not found: §e" + name));
                     }

                     return 1;
                  }))));
               registerHotbarCommand.accept("bombohb");
               registerHotbarCommand.accept("bhb");
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("v").then(ClientCommands.argument("player", StringArgumentType.greedyString()).executes((c) -> {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.getConnection() != null) {
                     mc.getConnection().sendCommand("visit " + StringArgumentType.getString(c, "player"));
                  }

                  return 1;
               })));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("tp").then(ClientCommands.argument("plot", StringArgumentType.greedyString()).suggests((context, builder) -> {
                  if (SkyblockUtils.isInGarden()) {
                     builder.suggest("barn");
                     for (int i = 1; i <= 24; ++i) {
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
               }).executes((context) -> {
                  String plot = StringArgumentType.getString(context, "plot").trim();
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null && mc.getConnection() != null) {
                     if (SkyblockUtils.isInGarden()) {
                        if (plot.equalsIgnoreCase("barn")) {
                           mc.player.connection.sendCommand("warp garden");
                        } else {
                           mc.player.connection.sendCommand("tptoplot " + plot);
                        }
                     } else {
                        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("tp " + plot));
                     }
                  }
                  return 1;
               })));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("pv").executes((context) -> {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null) {
                     String username = mc.player.getGameProfile().name();
                     openProfileViewer(username);
                  }

                  return 1;
               }));
               dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("bpv").then(ClientCommands.argument("username", StringArgumentType.word()).suggests((context, builder) -> {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.getConnection() != null) {
                     for(PlayerInfo playerInfo : mc.getConnection().getOnlinePlayers()) {
                        builder.suggest(playerInfo.getProfile().name());
                     }
                  }

                  return builder.buildFuture();
               }).executes((context) -> {
                  String username = StringArgumentType.getString(context, "username");
                  openProfileViewer(username);
                  return 1;
               }))).executes((context) -> {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null) {
                     String username = mc.player.getGameProfile().name();
                     openProfileViewer(username);
                  }

                  return 1;
               }));
               dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("mod").executes((context) -> {
                  Path modsFolder = FabricLoader.getInstance().getGameDir().resolve("mods");
                  Util.getPlatform().openUri(modsFolder.toUri());
                  ((FabricClientCommandSource)context.getSource()).sendFeedback(Component.literal("§aOpening mods folder..."));
                  return 1;
               }));
               String[] floorNames = new String[]{"one", "two", "three", "four", "five", "six", "seven"};

               for(int i = 1; i <= 7; ++i) {
                  final int floorIdx = i;
                  dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("f" + i).executes((ctx) -> {
                     if (BomboConfig.get().quickJoinCommands) {
                        Minecraft.getInstance().execute(() -> {
                           if (Minecraft.getInstance().player != null) {
                              Minecraft.getInstance().player.connection.sendCommand("joininstance catacombs_floor_" + floorNames[floorIdx - 1]);
                           }

                        });
                        return 1;
                     } else {
                        return 0;
                     }
                  }));
                  dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("m" + i).executes((ctx) -> {
                     if (BomboConfig.get().quickJoinCommands) {
                        Minecraft.getInstance().execute(() -> {
                           if (Minecraft.getInstance().player != null) {
                              Minecraft.getInstance().player.connection.sendCommand("joininstance master_catacombs_floor_" + floorNames[floorIdx - 1]);
                           }

                        });
                        return 1;
                     } else {
                        return 0;
                     }
                  }));
               }
               String[] kuudraTiers = new String[]{"normal", "hot", "burning", "fiery", "infernal"};

               for(int i = 1; i <= 5; ++i) {
                  final int tierIdx = i;
                  dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("t" + i).executes((ctx) -> {
                     if (BomboConfig.get().quickJoinCommands) {
                        Minecraft.getInstance().execute(() -> {
                           if (Minecraft.getInstance().player != null) {
                              Minecraft.getInstance().player.connection.sendCommand("joininstance kuudra_" + kuudraTiers[tierIdx - 1]);
                           }

                        });
                        return 1;
                     } else {
                        return 0;
                     }
                  }));
               }
            } catch (Throwable t) {
               Bomboaddons.LOGGER.error("[BomboAddons] FAILED to register inventory commands!", t);
            }

         });
         ClientPlayConnectionEvents.DISCONNECT.register((ClientPlayConnectionEvents.Disconnect)(handler, client) -> PlaytimeTracker.sendPlaytimeDataToCloud());
         RankCache.load();
         PlaytimeTracker.load();
         DiceTracker.load();
         ChatPeek.init();
         BazaarUtils.init();
         LowestBinManager.ensureLoaded();
         ItemHotkeys.init();
         ModUpdater.init();
         TabCompletionManager.load();
         this.registerTickEvents();
         DiceHud.init();
         KuudraTimer.init();
         CustomTimerManager.init();
         DungeonPadTimers.init();
         CorpseHighlight.init();
         me.bombo.bomboaddons.features.FrozenBlazeAFKTracker.init();
         HighlightESP.fetchOnlineAliasesAsync();
         IRCClient.start();
         LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register((LevelRenderEvents.AfterTranslucentFeatures)(context) -> {
            BomboConfig.Settings s = BomboConfig.get();
            if (s == null) return;

            if (GardenWaypoints.hasAnyVisibleWaypoints()) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: GardenWaypoints")) {
                  GardenWaypoints.render(context);
               } catch (Throwable ignored) {}
            }

            if (WaypointManager.hasWaypoints()) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: WaypointManager")) {
                  WaypointManager.render(context);
               } catch (Throwable ignored) {}
            }

            if (!OrderedWaypoints.getWaypoints().isEmpty()) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: OrderedWaypoints")) {
                  OrderedWaypoints.render(context);
               } catch (Throwable ignored) {}
            }

            if (s.eggFinder && AlphaTrackerHud.isHoppityActive() && EggFinder.hasWaypoints()) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: EggFinder")) {
                  EggFinder.render(context);
               } catch (Throwable ignored) {}
            }

            if (s.corpseEsp && ("Mineshaft".equalsIgnoreCase(currentArea) || "Glacite Mineshafts".equalsIgnoreCase(currentArea) || "Dwarven Mines".equalsIgnoreCase(currentArea))) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: CorpseHighlight")) {
                  CorpseHighlight.render(context);
               } catch (Throwable ignored) {}
            }

            if (s.highlightsEnabled && !HighlightESP.activeTracerEntityIds.isEmpty()) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: HighlightESP")) {
                  HighlightESP.render(context);
               } catch (Throwable ignored) {}
            }

            if (s.blockHighlightsEnabled && s.blockHighlights != null && !s.blockHighlights.isEmpty()) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: BlockHighlight")) {
                  BlockHighlight.render(context);
               } catch (Throwable ignored) {}
            }

            if (s.goldenDragonNestFinder && ("Crystal Hollows".equalsIgnoreCase(currentArea) || "Hollows".equalsIgnoreCase(currentArea))) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: GoldenDragonNestFinder")) {
                  GoldenDragonNestFinder.render(context);
               } catch (Throwable ignored) {}
            }

            if (s.structureFinder && (StructureFinder.isScanAllowed() || "Crystal Hollows".equalsIgnoreCase(currentArea) || "Hollows".equalsIgnoreCase(currentArea))) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: StructureFinder")) {
                  StructureFinder.render(context);
               } catch (Throwable ignored) {}
            }

            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: StructureScanner")) {
               StructureScanner.render(context);
            } catch (Throwable ignored) {}

            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Render: ParticleESP")) {
               ParticleESP.render(context);
            } catch (Throwable ignored) {}
         });
         HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "main_hud"), (graphics, deltaTracker) -> {
            if (BomboConfig.get().tracerTestMode) {
               int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
               int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
               float centerX = (float)screenWidth / 2.0F;
               float centerY = (float)screenHeight / 2.0F;
               BomboRenderUtils.draw2DLine(graphics, centerX, centerY, 100.0F, 100.0F, -65536, 3.0F);
               BomboRenderUtils.draw2DLine(graphics, centerX, centerY, (float)screenWidth - 100.0F, 100.0F, -16711936, 3.0F);
               BomboRenderUtils.draw2DLine(graphics, centerX, centerY, centerX, 50.0F, -16776961, 3.0F);
            }

            AutoFishing.renderTimer(graphics);

            try {
               if (BomboConfig.get().kuudraDebug) {
                  graphics.text(Minecraft.getInstance().font, "§d§lHUD RENDER TEST ACTIVE", 10, 50, -65281, true);
               }

               for(Pearls.PearlHUDText t : Pearls.HUD_TEXTS) {
                  int var10003 = (int)t.x;
                  int var10004 = (int)t.y;
                  graphics.centeredText(Minecraft.getInstance().font, t.text, var10003, var10004, t.color);
               }
            } catch (Throwable var6) {
            }

            if (Minecraft.getInstance().screen == null || Minecraft.getInstance().screen instanceof HudMoveScreen) {
               FeastBakeryHud.onHudRender(graphics);
               ExperimentationTableHud.onHudRender(graphics);
            }

            if (Minecraft.getInstance().screen == null) {
               GardenMovement.drawDirectionWarning(graphics);
            }

         });
         ScreenEvents.BEFORE_INIT.register((ScreenEvents.BeforeInit)(client, screen, scaledWidth, scaledHeight) -> ScreenEvents.afterExtract(screen).register((ScreenEvents.AfterExtract)(screen1, graphics, mouseX, mouseY, tickDelta) -> {
               FeastBakeryHud.onHudRender(graphics);
               ExperimentationTableHud.onHudRender(graphics);
            }));
         ClientPlayConnectionEvents.JOIN.register((ClientPlayConnectionEvents.Join)(handler, sender, client) -> {
            StructureFinder.clear();
            me.bombo.bomboaddons.features.FrozenBlazeAFKTracker.reset();
            currentHypixelChannel = "a";
            if (client.getCurrentServer() != null) {
               lastServerData = client.getCurrentServer();
            } else if (lastServerData == null) {
               lastServerData = new ServerData("Hypixel", "hypixel.net", Type.OTHER);
            }

            LowestBinManager.reload();
            AutoExperiments.reset();
            ModUpdater.checkAndUpdate(true);

            try {
               EggAuth.updateToken();
            } catch (Throwable t) {
               t.printStackTrace();
            }

            if (client.getUser() != null) {
               String name = client.getUser().getName();
               if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Player")) {
                  RankCache.fetchAsync(name);
               }
            }

         });
         ClientPlayConnectionEvents.DISCONNECT.register((ClientPlayConnectionEvents.Disconnect)(handler, client) -> {
            StructureFinder.clear();
            me.bombo.bomboaddons.features.FrozenBlazeAFKTracker.reset();
            PlaytimeTracker.sendPlaytimeDataToCloud();

            try {
               EggFinder.clearEggs();
               EggWebSocket.disconnect();
            } catch (Throwable t) {
               t.printStackTrace();
            }

            try {
               TabCompletionManager.party.clear();
            } catch (Throwable var3) {
            }

         });
         ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((ClientLevelEvents.AfterClientLevelChange)(client, world) -> {
            StructureFinder.clear();
            if (world != null && SkyblockUtils.isConnectedToHypixel()) {
               locrawServer = "";
               locrawGametype = "";
               locrawMode = "";
               locrawMap = "";
               clickedNpcOptions.clear();
               clickedNpcTextOptions.clear();
               locrawDelayTicks = 40;
            }

         });
         ClientReceiveMessageEvents.MODIFY_GAME.register((ClientReceiveMessageEvents.ModifyGame)(message, overlay) -> {
            if (BomboConfig.get().showCommandOnHover) {
               message = addHoverToCommands(message);
            }

            return message;
         });
         ClientReceiveMessageEvents.ALLOW_GAME.register((ClientReceiveMessageEvents.AllowGame)(message, overlay) -> {
            String plain = message.getString().trim();
            if (!plain.contains("DailyRewardDebug") && !plain.contains("[BomboAddons]")) {
               if (BomboConfig.get().autoHoppityCalls && !overlay) {
                  if (plain.contains("Hoppity") && (plain.contains("✆") || plain.contains("Call") || plain.contains("incoming"))) {
                     lastHoppityCallHeaderTime = System.currentTimeMillis();
                  }

                  boolean isCallMessage = (plain.contains("BUZZ...") || plain.contains("RING...")) && plain.contains("[PICK UP]");
                  boolean isHoppityCall = System.currentTimeMillis() - lastHoppityCallHeaderTime < 10000L || plain.contains("Hoppity");
                  if (isCallMessage && isHoppityCall) {
                     message.visit((style, text) -> {
                        ClickEvent patt0$temp = style.getClickEvent();
                        if (patt0$temp instanceof ClickEvent.RunCommand runCmd) {
                           String command = runCmd.command();
                           if (command.startsWith("/")) {
                              command = command.substring(1);
                           }

                           Minecraft mcInstance = Minecraft.getInstance();
                           if (mcInstance.player != null && mcInstance.player.connection != null) {
                              mcInstance.player.connection.sendCommand(command);
                           }
                        }

                        return Optional.empty();
                     }, Style.EMPTY);
                  }
               }

               if (BomboConfig.get().autoAcceptNpcLore && !overlay) {
                  if (plain.contains("[Debug]")) {
                     return true;
                  }

                  if (plain.contains("Select an option:")) {
                     List<NpcOptionItem> options = new ArrayList<>();
                     message.visit((style, text) -> {
                        ClickEvent patt0$temp = style.getClickEvent();
                        if (patt0$temp instanceof ClickEvent.RunCommand runCmd) {
                           String t = text.trim();
                           if (!t.isEmpty() && !t.equals("[") && !t.equals("]")) {
                                                      class NpcOption {
                            String text;
                            String color;
                            String command;
                            NpcOption(String text, String color, String command) {
                                this.text = text;
                                this.color = color;
                                this.command = command;
                            }
                        }

                              options.add(new NpcOptionItem(t, style.getColor() != null ? style.getColor().toString() : "none", runCmd.command()));
                           }
                        }

                        return Optional.empty();
                     }, Style.EMPTY);
                     if (!options.isEmpty()) {
                        String menuId = (String)options.stream().map((o) -> o.text).collect(Collectors.joining("|"));
                        Set<String> clickedHere = BomboaddonsClient.npcClickedOptions.computeIfAbsent(menuId, (k) -> new HashSet());
                        NpcOptionItem toClick = null;

                        for (NpcOptionItem opt : options) {
                           if (!clickedHere.contains(opt.text)) {
                              String colorLower = opt.color.toLowerCase();
                              String textLower = opt.text.toLowerCase().replaceAll("[\\[\\]]", "").trim();
                              String cmdLower = opt.command != null ? opt.command.toLowerCase() : "";
                              
                              boolean isDeny = textLower.equals("no") || textLower.startsWith("no ") || textLower.contains("decline") 
                                 || textLower.contains("cancel") || textLower.contains("deny") || textLower.contains("never")
                                 || colorLower.contains("red") || colorLower.contains("ff5555") || colorLower.contains("aa0000")
                                 || cmdLower.endsWith("r_2_2") || cmdLower.contains("leave") || cmdLower.contains("safari_manager");

                              if (!isDeny) {
                                 boolean isAffirmative = textLower.equals("yes") || textLower.startsWith("yes ") || textLower.contains("absolutely") 
                                    || textLower.contains("confirm") || textLower.contains("trade") || textLower.contains("accept") || textLower.contains("buy");
                                 boolean isGreen = colorLower.contains("green") || colorLower.contains("55ff55") || colorLower.contains("00aa00");
                                 if (isGreen || isAffirmative) {
                                    toClick = opt;
                                    break;
                                 }
                              }
                           }
                        }

                        if (toClick == null) {
                           for (NpcOptionItem opt : options) {
                              if (!clickedHere.contains(opt.text)) {
                                 String colorLower = opt.color.toLowerCase();
                                 String textLower = opt.text.toLowerCase().replaceAll("[\\[\\]]", "").trim();
                                 String cmdLower = opt.command != null ? opt.command.toLowerCase() : "";

                                 boolean isDeny = textLower.equals("no") || textLower.startsWith("no ") || textLower.contains("decline") 
                                    || textLower.contains("cancel") || textLower.contains("deny") || textLower.contains("never")
                                    || textLower.contains("tell me more") || textLower.contains("not right now")
                                    || colorLower.contains("red") || colorLower.contains("ff5555") || colorLower.contains("aa0000")
                                    || cmdLower.endsWith("r_2_2") || cmdLower.contains("leave") || cmdLower.contains("safari_manager");

                                 if (!isDeny) {
                                    toClick = opt;
                                    break;
                                 }
                              }
                           }
                        }

                        if (toClick != null) {
                           clickedHere.add(toClick.text);
                           String cmd = toClick.command;
                           if (cmd.startsWith("/")) {
                              cmd = cmd.substring(1);
                           }

                           if (BomboConfig.get().npcLoreDebug && Minecraft.getInstance().player != null) {
                              Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §d[Debug] §aExecuting: §e/" + cmd + " §7for option: §e" + toClick.text));
                           }

                           Minecraft.getInstance().getConnection().sendCommand(cmd);
                        } else if (BomboConfig.get().npcLoreDebug && Minecraft.getInstance().player != null) {
                           Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §c[Debug] §7All options exhausted for this NPC prompt."));
                        }
                     }
                  }
               }

               if (plain.contains("[BomboPlaytimeSyncRequest]")) {
                  try {
                     PlaytimeTracker.sendPlaytimeDataToCloud();
                  } catch (Throwable t) {
                     t.printStackTrace();
                  }

                  return false;
               } else {
                  try {
                     EggFinder.onChatMessage(message, overlay);
                  } catch (Throwable t) {
                     t.printStackTrace();
                  }

                  if (plain.startsWith("{") && plain.endsWith("}") && plain.contains("\"server\"") && plain.contains("\"gametype\"")) {
                     try {
                        JsonObject json = JsonParser.parseString(plain).getAsJsonObject();
                        if (json.has("server")) {
                           String newServer = json.get("server").getAsString();
                           if (!newServer.equals(locrawServer)) {
                              StructureFinder.clear();
                           }
                           locrawServer = newServer;
                        }

                        if (json.has("gametype")) {
                           locrawGametype = json.get("gametype").getAsString();
                        }

                        if (json.has("mode")) {
                           locrawMode = json.get("mode").getAsString();
                        }

                        if (json.has("map")) {
                           locrawMap = json.get("map").getAsString();
                        }

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
                        return false;
                     }
                  }

                  return true;
               }
            } else {
               return true;
            }
         });
         ClientReceiveMessageEvents.GAME.register((ClientReceiveMessageEvents.Game)(message, overlay) -> {
            String clean = message.getString().replaceAll("§.", "");
            if (!overlay) {
               DebugUtils.debug("chat", clean);
            }

            DiceTracker.onChatMessage(clean);
            DungeonSecretsTracker.onChatMessage(clean);
            AFKManager.onChatMessage(clean);
            ClearInfoHUD.onChatMessage(clean);
            if (!overlay && BomboConfig.get().customTimers != null) {
               for(BomboConfig.CustomTimerDef def : BomboConfig.get().customTimers) {
                  if (def.enabled && def.triggerText != null && !def.triggerText.isEmpty() && clean.contains(def.triggerText)) {
                     CustomTimerManager.startTimer(def.name, def.durationSeconds * 1000L, false, def.logoItemId, def.showOnlyWhenReady, def.keepReadyState);
                  }
               }
            }

            Matcher ratMatcher = Pattern.compile("^CHEESE! You buffed (\\S+) giving them (.+) for\\s+(\\d+)\\s+seconds!").matcher(clean);
            if (ratMatcher.find()) {
               String target = ratMatcher.group(1);
               String stat = ratMatcher.group(2);
               long duration = Long.parseLong(ratMatcher.group(3)) * 1000L;
               CustomTimerManager.startTimer("Rat: " + target + " (" + stat + ")", duration);
            }

            CustomBindsProcessor.processChatTrigger(clean);
            if (overlay) {
               Pearls.onTitleReceived(clean);
            }

            if (BomboConfig.get().autoTrevorQuest && clean.contains("Accept the trapper's task to hunt the animal?")) {
               findAndClickYes(message);
            }

            if (clean.contains("You are now in the ") && clean.contains("channel") || clean.contains("Opened a chat conversation with ") && clean.contains("minutes")) {
               if (clean.contains("You are now in the ") && clean.contains("channel")) {
                  if (!clean.contains("ALL CHAT") && !clean.contains("ALL")) {
                     if (clean.contains("GUILD")) {
                        currentHypixelChannel = "g";
                     } else if (clean.contains("PARTY")) {
                        currentHypixelChannel = "p";
                     } else if (clean.contains("OFFICER")) {
                        currentHypixelChannel = "o";
                     } else if (clean.contains("CO-OP") || clean.contains("COOP")) {
                        currentHypixelChannel = "c";
                     }
                  } else {
                     currentHypixelChannel = "a";
                  }
               } else if (clean.contains("Opened a chat conversation with ") && clean.contains("minutes")) {
                  int idx = clean.indexOf("Opened a conversation with ");
                  if (idx == -1) {
                     idx = clean.indexOf("Opened a chat conversation with ");
                  }

                  int forNextIndex = clean.indexOf(" for the next");
                  if (idx != -1 && forNextIndex != -1) {
                     String namePart = clean.substring(idx + (clean.contains("Opened a chat conversation with ") ? "Opened a chat conversation with ".length() : "Opened a conversation with ".length()), forNextIndex).trim();
                     String[] parts = namePart.split("\\s+");
                     if (parts.length > 0) {
                        currentHypixelChannel = parts[parts.length - 1].toLowerCase();
                     }
                  }
               }

               if (BomboConfig.get().ircDefaultChat) {
                  BomboConfig.get().ircDefaultChat = false;
                  BomboConfig.save();
                  Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7 (detected channel change)."));
               }
            }

            processChatMessage(message.getString());
         });
      } catch (Throwable t) {
         Bomboaddons.LOGGER.error("[BomboAddons] CRITICAL ERROR in onInitializeClient!", t);
      }

      class MenuCache {
         static Map<String, Set<String>> menuToClickedOptions = new HashMap();

         MenuCache() {
            Objects.requireNonNull(BomboaddonsClient.this);
            super();
         }
      }

   }

   private void registerTickEvents() {
      try {
         Class<?> categoryClass = Class.forName("net.minecraft.client.gui.screens.options.controls.KeyBindsList$CategoryEntry");
         System.out.println("CategoryEntry fields:");

         for(Field f : categoryClass.getDeclaredFields()) {
            PrintStream var10000 = System.out;
            String var10001 = f.getName();
            var10000.println(var10001 + " " + f.getType().getName());
         }

         Class<?> keyClass = Class.forName("net.minecraft.client.gui.screens.options.controls.KeyBindsList$KeyEntry");
         System.out.println("KeyEntry fields:");

         for(Field f : keyClass.getDeclaredFields()) {
            PrintStream var12 = System.out;
            String var13 = f.getName();
            var12.println(var13 + " " + f.getType().getName());
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         ++ChromaTextHelper.tickCounter;
         PerformanceProfiler.onTick();
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null) return;

         if (CustomTimerManager.hasTimers()) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: CustomTimerManager")) {
               CustomTimerManager.tick();
            } catch (Throwable var38) {
            }
         }

         if (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: StorageTracker")) {
               StorageTracker.onGuiTick();
            } catch (Throwable var37) {
            }
         }

         if (s.autoFishingEnabled) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: AutoFishing")) {
               AutoFishing.onTick(client);
            } catch (Throwable var36) {
            }
         }

         try {
            if (client.player != null && client.player.inventoryMenu != null) {
               int currentState = client.player.inventoryMenu.getStateId();
               if (currentState != lastInventoryStateId) {
                  lastInventoryStateId = currentState;
                  StorageTracker.updatePlayerInventory(client);
               }
            }
         } catch (Throwable var35) {
         }

         try {
            long now = System.currentTimeMillis();
            List<PendingCommand> toRun = new ArrayList();

            for(PendingCommand pc : pendingCommands) {
               if (now >= pc.triggerTime) {
                  toRun.add(pc);
               }
            }

            for(PendingCommand pc : toRun) {
               executeTracked(pc.command);
               pendingCommands.remove(pc);
            }
         } catch (Throwable var42) {
         }

         if (s.eggFinder && AlphaTrackerHud.isHoppityActive()) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: EggFinder")) {
               EggFinder.tick();
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }

         if (s.bedwarsEsp) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: BedwarsESP")) {
               BedwarsESP.tick();
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }

         if (s.frozenBlazeWarning) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: FrozenBlazeAFK")) {
               me.bombo.bomboaddons.features.FrozenBlazeAFKTracker.onTick(client);
            } catch (Throwable ignored) {}
         }

         if (s.replaceGrayCarpetDwarven) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: DwarvenCarpet")) {
               DwarvenCarpetReplacer.tick(client);
            } catch (Throwable ignored) {}
         }

         if (client.player != null && client.player.tickCount % 10 == 0 && s.highlightsEnabled && HighlightESP.hasActiveTracers(s)) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: HighlightESP")) {
               HighlightESP.onTick();
            } catch (Throwable ignored) {}
         }

         try {
            if (client.screen instanceof DisconnectedScreen) {
               if (autoReconnectTicks > 0) {
                  --autoReconnectTicks;
                  int secondsLeft = (autoReconnectTicks + 19) / 20;
                  if (activeReconnectBtn != null) {
                     activeReconnectBtn.setMessage(Component.literal("Reconnect (" + secondsLeft + "s)"));
                  }

                  if (BomboConfig.get().debugReconnect && autoReconnectTicks % 20 == 0) {
                     DebugUtils.debug("reconnect", "Auto-reconnect countdown: " + secondsLeft + "s");
                  }

                  if (autoReconnectTicks == 0) {
                     if (BomboConfig.get().debugReconnect) {
                        DebugUtils.debug("reconnect", "Countdown finished (0s). Executing reconnect().");
                     }

                     reconnect(activeParent, client);
                  }
               }
            } else {
               autoReconnectTicks = -1;
               activeReconnectBtn = null;
               activeParent = null;
            }
         } catch (Throwable var32) {
         }

         if (client.player != null) {
            if (client.player.tickCount % 20 == 0) {
               String prevArea = currentArea;
               currentArea = SkyblockUtils.getLocation();
               currentSubArea = SkyblockUtils.getSubArea();
               if (currentArea != null && !currentArea.equals("None") && !currentArea.equals("Unknown") && !currentArea.equals(prevArea)) {
                  IRCClient.broadcastArea(currentArea);
               }
            }

            try {
               if (s.coordBinds != null) {
                  List<BomboConfig.CoordBind> activeBinds = (List)s.coordBinds.get(s.activeProfile);
                  List<BomboConfig.CoordBind> generalBinds = (List)s.coordBinds.get("General");
                  List<BomboConfig.CoordBind> binds = new ArrayList();
                  if (activeBinds != null) {
                     binds.addAll(activeBinds);
                  }

                  if (generalBinds != null && !s.activeProfile.equals("General")) {
                     binds.addAll(generalBinds);
                  }

                  Vec3 playerPos = client.player.position();

                  for(BomboConfig.CoordBind bind : binds) {
                     if (bind.enabled) {
                        if (!SkyblockUtils.matchesIslandRequirement(bind.requiredIsland)) {
                           bind.wasInside = false;
                           continue;
                        }

                        double dist = playerPos.distanceTo(new Vec3(bind.x, bind.y, bind.z));
                        double r = bind.radius <= (double)0.0F ? (double)3.0F : bind.radius;
                        if (dist <= r) {
                           if (!bind.wasInside) {
                              double minD = bind.minDelay;
                              double maxD = bind.maxDelay;
                              if (maxD > minD && maxD > (double)0.0F) {
                                 double delaySec = minD + Math.random() * (maxD - minD);
                                 long triggerTime = System.currentTimeMillis() + (long)(delaySec * (double)1000.0F);
                                 pendingCommands.add(new PendingCommand(bind.command, triggerTime));
                              } else {
                                 executeTracked(bind.command);
                              }

                              bind.wasInside = true;
                           }
                        } else if (dist > r + (double)1.5F) {
                           bind.wasInside = false;
                        }
                     }
                  }
               }
            } catch (Throwable var41) {
            }
         } else if (menuTickCount++ % 20 == 0) {
            currentArea = SkyblockUtils.getLocation();
            currentSubArea = SkyblockUtils.getSubArea();
         }

         if (ParticleTracker.isParticleTrackingNeeded()) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: ParticleTracker")) {
               ParticleTracker.onTick();
            } catch (Throwable ignored) {}
         }

         if (client.player != null && client.player.tickCount % 20 == 0) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: PlaytimeTracker")) {
               PlaytimeTracker.tick();
            } catch (Throwable ignored) {}
         }

         if (PetManager.isBusy()) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: PetManager")) {
               PetManager.onTick();
            } catch (Throwable ignored) {}
         }

         if (locrawDelayTicks > 0) {
            --locrawDelayTicks;
            if (locrawDelayTicks == 0) {
               triggerLocraw();
            }
         }

         if (client.player != null && System.currentTimeMillis() - lastLocrawTime > 300000L) {
            triggerLocraw();
         }

         try {
            if (openGuiNextTick && client.player != null) {
               System.out.println("DEBUG: Tick opening config GUI");
               openGuiNextTick = false;
               pendingConfigSearch = null;
               client.setScreen(BomboConfigGUI.create());
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
         } catch (Throwable var31) {
            Throwable t = var31;
            Bomboaddons.LOGGER.error("[BomboAddons] Error opening screen!", var31);

            try {
               File file = new File("crash_exception.log");
               PrintWriter pw = new PrintWriter(new FileWriter(file, true));

               try {
                  pw.println("=== TICK SCREEN OPEN EXCEPTION ===");
                  t.printStackTrace(pw);
                  pw.println("==================================");
               } catch (Throwable var29) {
                  try {
                     pw.close();
                  } catch (Throwable x2) {
                     var29.addSuppressed(x2);
                  }

                  throw var29;
               }

               pw.close();
            } catch (Throwable var30) {
            }
         }

         if (LeftClickEtherwarp.isBusy()) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: LeftClickEtherwarp")) {
               LeftClickEtherwarp.onTick();
            } catch (Throwable var27) {
            }
         }

         if (s.autoExperiments && !s.hideCheats && client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: AutoExperiments")) {
               AutoExperiments.onTick();
            } catch (Throwable var26) {
            }
         }

         try {
            if (client.player != null) {
               ItemStack held = client.player.getMainHandItem();
               if (!held.isEmpty()) {
                  String cleanName = held.getHoverName().getString().replaceAll("(?i)§.", "").toLowerCase();
                  if ((cleanName.contains("aspect of the end") || cleanName.contains("aspect of the void") || cleanName.contains("hyperion") || cleanName.contains("valkyrie") || cleanName.contains("scylla") || cleanName.contains("astraea")) && client.options.keyUse.isDown()) {
                     GardenMacroDetector.recordWeaponUse();
                  }
               }
            }
         } catch (Throwable var40) {
         }

         if (s.gardenMovement && (GardenMovement.isActive() || SkyblockUtils.isInGarden())) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: GardenMovement")) {
               GardenMovement.onTick(client);
            } catch (Throwable var25) {
            }
         }

         if (s.fuckDiorite && ("Dungeons".equals(currentArea) || "Private Island".equals(currentArea))) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: FuckDiorite")) {
               FuckDiorite.onTick();
            } catch (Throwable var24) {
            }
         }



         if (s.anvilAutoCombineEnabled && !s.hideCheats && (client.screen instanceof net.minecraft.client.gui.screens.inventory.AnvilScreen || (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs && (acs.getTitle().getString().toLowerCase().contains("anvil") || acs.getTitle().getString().toLowerCase().contains("combine"))))) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: AutoCombine")) {
               AutoCombine.onTick();
            } catch (Throwable var23) {
            }
         }

         try {
            if (BomboConfig.get().goldenDragonNestFinder && ("Crystal Hollows".equalsIgnoreCase(currentArea) || "Hollows".equalsIgnoreCase(currentArea))) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: GoldenDragonNestFinder")) {
                  GoldenDragonNestFinder.onTick();
               } catch (Throwable var21) {}
            }

            if (BomboConfig.get().structureFinder && (StructureFinder.isScanAllowed() || "Crystal Hollows".equalsIgnoreCase(currentArea) || "Hollows".equalsIgnoreCase(currentArea))) {
               try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: StructureFinder")) {
                  StructureFinder.onTick();
               } catch (Throwable var21) {}
            }

            if (BomboConfig.get().debugEntities && client.player != null && client.player.tickCount % 100 == 0 && client.level != null) {
               int count = 0;
               StringBuilder info = new StringBuilder("Entities near you: ");

               for(Entity e : client.level.entitiesForRendering()) {
                  ++count;
                  if (e.distanceTo(client.player) < 10.0F) {
                     String name = e.getName().getString();
                     if (e.isInvisible()) {
                        name = name + " §7(Invisible)§r";
                     }

                     info.append(name).append(" (").append(e.getId()).append("), ");
                  }
               }

               DebugUtils.debug("entity", "Total: " + count + " | Nearby: " + info.toString());
            }
         } catch (Throwable var39) {
         }

         if (KuudraUtils.inKuudra()) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: KuudraPearls")) {
               KuudraUtils.onClientTick();
               Pearls.onClientTick();
            } catch (Throwable var22) {}
         }

         if (BomboConfig.get().blockHighlightsEnabled && BomboConfig.get().blockHighlights != null && !BomboConfig.get().blockHighlights.isEmpty()) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: BlockHighlight")) {
               BlockHighlight.onTick();
            } catch (Throwable var21) {}
         }

         if (BomboConfig.get().goldenDragonNestFinder && ("Crystal Hollows".equalsIgnoreCase(currentArea) || "Hollows".equalsIgnoreCase(currentArea))) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: GoldenDragonNestFinder")) {
               GoldenDragonNestFinder.onTick();
            } catch (Throwable var21) {}
         }

         if (s.autoCroesus && (client.screen == null || client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("Tick: AutoCroesus")) {
               AutoCroesus.checkCroesusNpcClick();
            } catch (Throwable var20) {}
         }

      });
   }

   public static void executeTracked(String cmd) {
      if (cmd != null && !cmd.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         mc.execute(() -> {
            if (mc.player != null) {
               String cleanCmd;
               for(cleanCmd = cmd.trim(); cleanCmd.startsWith("/"); cleanCmd = cleanCmd.substring(1).trim()) {
               }

               try {
                  DebugUtils.debug("command", "Runned: /" + cleanCmd);
                  if (clientDispatcher != null && clientDispatcher.getRoot().getChild(cleanCmd.split(" ")[0]) != null) {
                     clientDispatcher.execute(cleanCmd, (FabricClientCommandSource)mc.player);
                  } else if (mc.player.connection != null) {
                     mc.player.connection.sendCommand(cleanCmd);
                  } else {
                     mc.player.sendSystemMessage(Component.literal("§c[Bombo] Failed to execute: /" + cleanCmd));
                  }
               } catch (Exception var4) {
                  if (mc.player.connection != null) {
                     mc.player.connection.sendCommand(cleanCmd);
                  }
               }
            }

         });
      }
   }

   public static String getAliasTarget(String alias) {
      if (alias == null) {
         return null;
      } else {
         String clean = alias.trim().toLowerCase();
         if (clean.startsWith("/")) {
            clean = clean.substring(1).trim();
         }

         if (BomboConfig.get().commandAliases != null) {
            String target = (String)BomboConfig.get().commandAliases.get(clean);
            if (target != null && !target.trim().isEmpty()) {
               return target.trim();
            }
         }

         return null;
      }
   }

   public static void registerAlias(CommandDispatcher<FabricClientCommandSource> dispatcher, String alias) {
      if (alias != null && !alias.trim().isEmpty()) {
         String raw = alias.trim().toLowerCase();
         String cleanAlias = raw.startsWith("/") ? raw.substring(1).trim() : raw;

         try {
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(cleanAlias).then(ClientCommands.argument("args", StringArgumentType.greedyString()).executes((context) -> {
               String actualCmd = getAliasTarget(cleanAlias);
               if (actualCmd != null) {
                  String args = StringArgumentType.getString(context, "args");
                  executeTracked(actualCmd + " " + args);
               } else {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null && mc.getConnection() != null) {
                     String args = StringArgumentType.getString(context, "args");
                     mc.getConnection().send(new ServerboundChatCommandPacket(cleanAlias + " " + args));
                  }
               }

               return 1;
            }))).executes((context) -> {
               String actualCmd = getAliasTarget(cleanAlias);
               if (actualCmd != null) {
                  executeTracked(actualCmd);
               } else {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null && mc.getConnection() != null) {
                     mc.getConnection().send(new ServerboundChatCommandPacket(cleanAlias));
                  }
               }

               return 1;
            }));
         } catch (Exception e) {
            Bomboaddons.LOGGER.error("Failed to register alias command: " + cleanAlias, e);
         }

      }
   }

   public static void registerAliasToDispatcher(CommandDispatcher<ClientSuggestionProvider> dispatcher, String alias) {
      if (alias != null && !alias.trim().isEmpty()) {
         String raw = alias.trim().toLowerCase();
         String cleanAlias = raw.startsWith("/") ? raw.substring(1).trim() : raw;

         try {
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)LiteralArgumentBuilder.literal(cleanAlias).then(RequiredArgumentBuilder.argument("args", StringArgumentType.greedyString()).executes((context) -> {
               String actualCmd = getAliasTarget(cleanAlias);
               if (actualCmd != null) {
                  String args = StringArgumentType.getString(context, "args");
                  executeTracked(actualCmd + " " + args);
               }

               return 1;
            }))).executes((context) -> {
               String actualCmd = getAliasTarget(cleanAlias);
               if (actualCmd != null) {
                  executeTracked(actualCmd);
               }

               return 1;
            }));
         } catch (Exception e) {
            Bomboaddons.LOGGER.error("Failed to register alias to dispatcher: " + cleanAlias, e);
         }

      }
   }

   public static void registerMsgCommandsToDispatcher(CommandDispatcher<ClientSuggestionProvider> dispatcher) {
      try {
         String[] msgCmds = new String[]{"w", "tell"};

         for(String cmd : msgCmds) {
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)LiteralArgumentBuilder.literal(cmd).executes((context) -> {
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null && mc.player.connection != null) {
                  mc.player.connection.sendCommand(cmd);
               }

               return 1;
            })).then(((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("username", StringArgumentType.string()).suggests((ctx, sb) -> TabCompletionManager.getUsernameSuggestions(ctx, sb)).executes((context) -> {
               String name = StringArgumentType.getString(context, "username");
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null && mc.player.connection != null) {
                  mc.player.connection.sendCommand(cmd + " " + name);
               }

               return 1;
            })).then(RequiredArgumentBuilder.argument("message", StringArgumentType.greedyString()).executes((context) -> {
               String name = StringArgumentType.getString(context, "username");
               String msg = StringArgumentType.getString(context, "message");
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

   public static void registerBCommandsToDispatcher(CommandDispatcher<ClientSuggestionProvider> dispatcher) {
      if (clientDispatcher != null) {
         copyClientCommands(clientDispatcher, dispatcher);
      }

   }

   public static void removeCommand(CommandDispatcher<?> dispatcher, String name) {
      try {
         RootCommandNode<?> root = dispatcher.getRoot();
         Field childrenField = CommandNode.class.getDeclaredField("children");
         childrenField.setAccessible(true);
         Map<String, ?> children = (Map)childrenField.get(root);
         children.remove(name);
         Field literalsField = CommandNode.class.getDeclaredField("literals");
         literalsField.setAccessible(true);
         Map<String, ?> literals = (Map)literalsField.get(root);
         literals.remove(name);
         Field argumentsField = CommandNode.class.getDeclaredField("arguments");
         argumentsField.setAccessible(true);
         Map<String, ?> arguments = (Map)argumentsField.get(root);
         arguments.remove(name);
      } catch (Throwable var9) {
      }

   }

   public static void copyClientCommands(CommandDispatcher<FabricClientCommandSource> source, CommandDispatcher<ClientSuggestionProvider> target) {
      try {
         String[] toWipe = new String[]{"b", "bomboaddons", "bombo", "tp", "w", "tell", "msg", "p", "party", "f", "friend", "v", "visit"};

         for(String cmd : toWipe) {
            removeCommand(target, cmd);
         }

         RootCommandNode<FabricClientCommandSource> sourceRoot = source.getRoot();
         RootCommandNode<ClientSuggestionProvider> targetRoot = target.getRoot();

         java.util.Set<String> bomboCommands = new java.util.HashSet<>(java.util.Arrays.asList(
            "b", "bomboaddons", "bombo", "tp", "w", "tell", "msg", "p", "party", "f", "friend", "v", "visit",
            "bc", "bombochat", "lf", "lfc", "lb", "nw", "nwc", "cata", "skills", "slayer", "trophyfish", "crimson", "crimsom",
            "bombo_highlight_slot", "bombo_museum_click"
         ));
         if (BomboConfig.get() != null && BomboConfig.get().commandAliases != null) {
            bomboCommands.addAll(BomboConfig.get().commandAliases.keySet());
         }

         for(CommandNode<FabricClientCommandSource> child : sourceRoot.getChildren()) {
            if (bomboCommands.contains(child.getName().toLowerCase())) {
               CommandNode<ClientSuggestionProvider> connectionChild = wrapNode(child);
               if (connectionChild != null) {
                  targetRoot.addChild(connectionChild);
               }
            }
         }
      } catch (Throwable t) {
         Bomboaddons.LOGGER.error("Failed to copy client commands to connection dispatcher", t);
      }
   }

   public static CommandNode<ClientSuggestionProvider> wrapNode(CommandNode<FabricClientCommandSource> node) {
      try {
         ArgumentBuilder<ClientSuggestionProvider, ?> builder = null;
         if (node instanceof LiteralCommandNode) {
            builder = LiteralArgumentBuilder.literal(node.getName());
         } else if (node instanceof ArgumentCommandNode) {
            ArgumentCommandNode<FabricClientCommandSource, ?> argNode = (ArgumentCommandNode)node;
            RequiredArgumentBuilder rawBuilder = RequiredArgumentBuilder.argument(node.getName(), argNode.getType());
            if (argNode.getCustomSuggestions() != null) {
               rawBuilder.suggests(wrapSuggestionProvider(argNode.getCustomSuggestions()));
            }

            builder = rawBuilder;
         }

         if (builder == null) {
            return null;
         } else {
            builder.requires((source) -> true);
            if (node.getCommand() != null) {
               builder.executes(wrapCommand(node.getCommand()));
            }

            CommandNode<ClientSuggestionProvider> connectionNode = builder.build();

            for(CommandNode<FabricClientCommandSource> child : node.getChildren()) {
               CommandNode<ClientSuggestionProvider> connectionChild = wrapNode(child);
               if (connectionChild != null) {
                  connectionNode.addChild(connectionChild);
               }
            }

            return connectionNode;
         }
      } catch (Throwable var6) {
         return null;
      }
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   public static Command<ClientSuggestionProvider> wrapCommand(Command<FabricClientCommandSource> clientCommand) {
      return clientCommand == null ? null : (context) -> {
         FabricClientCommandSource mockSource = createMockSource((ClientSuggestionProvider)context.getSource());

         Field sourceField;
         Object originalSource;
         try {
            sourceField = CommandContext.class.getDeclaredField("source");
            sourceField.setAccessible(true);
            originalSource = sourceField.get(context);
            sourceField.set(context, mockSource);
         } catch (Throwable t) {
            throw new RuntimeException(t);
         }

         int t;
         try {
            Command rawCmd = (Command) clientCommand;
            t = rawCmd.run(context);
         } finally {
            try {
               sourceField.set(context, originalSource);
            } catch (Throwable var13) {
            }

         }

         return t;
      };
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   public static SuggestionProvider<ClientSuggestionProvider> wrapSuggestionProvider(SuggestionProvider<FabricClientCommandSource> clientProvider) {
      return clientProvider == null ? null : (context, builder) -> {
         try {
            FabricClientCommandSource mockSource = createMockSource((ClientSuggestionProvider)context.getSource());
            Field sourceField = CommandContext.class.getDeclaredField("source");
            sourceField.setAccessible(true);
            Object originalSource = sourceField.get(context);
            sourceField.set(context, mockSource);

            CompletableFuture var6;
            try {
               SuggestionProvider rawProvider = (SuggestionProvider) clientProvider;
               var6 = rawProvider.getSuggestions(context, builder);
            } finally {
               sourceField.set(context, originalSource);
            }

            return var6;
         } catch (Throwable var11) {
            return builder.buildFuture();
         }
      };
   }

   public static FabricClientCommandSource createMockSource(ClientSuggestionProvider originalSource) {
      return (FabricClientCommandSource)Proxy.newProxyInstance(BomboaddonsClient.class.getClassLoader(), new Class[]{FabricClientCommandSource.class}, (proxy, method, args) -> {
         Minecraft mc = Minecraft.getInstance();
         String methodName = method.getName();
         if (methodName.equals("sendFeedback")) {
            Component comp = (Component)args[0];
            if (mc.player != null) {
               mc.player.sendSystemMessage(comp);
            }

            return null;
         } else if (methodName.equals("sendError")) {
            Component comp = (Component)args[0];
            if (mc.player != null) {
               mc.player.sendSystemMessage(Component.literal("§c").append(comp));
            }

            return null;
         } else if (methodName.equals("getClient")) {
            return mc;
         } else if (methodName.equals("getPlayer")) {
            return mc.player;
         } else {
            return !methodName.equals("getWorld") && !methodName.equals("getLevel") ? method.invoke(originalSource, args) : mc.level;
         }
      });
   }

   public static void registerAllAliases() {
      if (clientDispatcher != null) {
         Set<String> allAliases = new HashSet();
         if (BomboConfig.get().commandAliases != null) {
            for(String alias : BomboConfig.get().commandAliases.keySet()) {
               if (alias != null && !alias.trim().isEmpty()) {
                  String clean = alias.trim().toLowerCase();
                  if (clean.startsWith("/")) {
                     clean = clean.substring(1).trim();
                  }

                  allAliases.add(clean);
               }
            }
         }

         for(String alias : allAliases) {
            registerAlias(clientDispatcher, alias);
         }

         try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
               CommandDispatcher<ClientSuggestionProvider> connectionDispatcher = mc.player.connection.getCommands();
               if (connectionDispatcher != null) {
                  for(String alias : allAliases) {
                     registerAliasToDispatcher(connectionDispatcher, alias);
                  }
               }
            }
         } catch (Throwable t) {
            Bomboaddons.LOGGER.error("Failed to register aliases to connection dispatcher", t);
         }

      }
   }

   public static String normalizeChannel(String channel) {
      if (channel == null) {
         return "";
      } else {
         String lower = channel.toLowerCase().trim();
         if (!lower.equals("a") && !lower.equals("all")) {
            if (!lower.equals("g") && !lower.equals("guild")) {
               if (!lower.equals("p") && !lower.equals("party")) {
                  if (!lower.equals("o") && !lower.equals("officer")) {
                     return !lower.equals("c") && !lower.equals("coop") ? lower : "c";
                  } else {
                     return "o";
                  }
               } else {
                  return "p";
               }
            } else {
               return "g";
            }
         } else {
            return "a";
         }
      }
   }

   public static void processChatMessage(String rawMessage) {
      if (rawMessage != null) {
         me.bombo.bomboaddons.features.BestiaryManager.onChatMessage(rawMessage);
         try {
            TabCompletionManager.onChatMessage(rawMessage);
         } catch (Throwable var22) {
         }

         String cleanMessage = rawMessage.replaceAll("§.", "").trim().toLowerCase();
         if (BomboConfig.get().partyCommandsEnabled) {
            List<String> prefixes = new ArrayList();
            String rawPrefixes = BomboConfig.get().partyCommandPrefixes;
            if (rawPrefixes != null && !rawPrefixes.trim().isEmpty()) {
               for(String p : rawPrefixes.split(",")) {
                  String trimmed = p.trim();
                  if (!trimmed.isEmpty()) {
                     prefixes.add(trimmed);
                  }
               }
            } else {
               prefixes.add("!");
            }

            if (prefixes.isEmpty()) {
               prefixes.add("!");
            }

            StringBuilder prefixRegex = new StringBuilder("(");

            for(int i = 0; i < prefixes.size(); ++i) {
               if (i > 0) {
                  prefixRegex.append("|");
               }

               prefixRegex.append(Pattern.quote((String)prefixes.get(i)));
            }

            prefixRegex.append(")");
            String regexPattern = "^party\\s*>\\s*(?:\\[[^\\]]+\\]\\s*)?(\\w+)\\s*:\\s*" + prefixRegex.toString() + "(\\w+)(?:\\s+(.+))?$";
            Matcher m = Pattern.compile(regexPattern, 2).matcher(cleanMessage);
            if (m.find()) {
               String senderName = m.group(1);

               try {
                  String rawNoFormat = rawMessage.replaceAll("§.", "").trim();
                  Matcher mRaw = Pattern.compile(regexPattern, 2).matcher(rawNoFormat);
                  if (mRaw.find()) {
                     senderName = mRaw.group(1);
                  }
               } catch (Exception var21) {
               }

               String command = m.group(3).toLowerCase();
               String args = m.group(4) != null ? m.group(4).trim() : "";
               if (command.equals("timer") && BomboConfig.get().partyCommandTimer) {
                  if (!args.isEmpty()) {
                     long durationMs = CustomTimerManager.parseTimeMs(args);
                     if (durationMs > 0L) {
                        CustomTimerManager.startTimer(senderName, durationMs, true, (String)null, false);
                        Bomboaddons.sendMessage("&8[&bBomboAddons&8] &7Started a &e" + args + " &7timer for &a" + senderName + "&7.");
                     }
                  }
               } else if (command.equals("warp") && BomboConfig.get().partyCommandWarp) {
                  pendingCommands.add(new PendingCommand("party warp", System.currentTimeMillis() + 300L));
               } else if (command.equals("psa") && BomboConfig.get().partyCommandPsa) {
                  pendingCommands.add(new PendingCommand("party settings allinvite", System.currentTimeMillis() + 300L));
               } else {
                  for(BomboConfig.CustomPartyCommand cpc : BomboConfig.get().customPartyCommands) {
                     if (cpc.enabled && cpc.triggerText.equalsIgnoreCase(command)) {
                        String finalCmd = cpc.commandToRun;
                        if (!args.isEmpty()) {
                           if (finalCmd.contains("%args%")) {
                              finalCmd = finalCmd.replace("%args%", args);
                           } else {
                              finalCmd = finalCmd + " " + args;
                           }
                        }

                        pendingCommands.add(new PendingCommand(finalCmd, System.currentTimeMillis() + 300L));
                        break;
                     }
                  }
               }
            }
         }

         if (cleanMessage.contains("[boss] storm: energy heed my call!") || cleanMessage.contains("[boss] storm: thunder let me be your catalyst!")) {
            DungeonPadTimers.onBossMessage();
         }

         if (cleanMessage.startsWith("co-op >") && cleanMessage.contains("noreconnect")) {
            tempDisableReconnect = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cAuto-reconnect delayed to 5 minutes by co-op message!"));
            }
         }

         List<BomboConfig.ChatTrigger> activeTriggers = (List)BomboConfig.get().profileChatTriggers.get(BomboConfig.get().activeProfile);
         List<BomboConfig.ChatTrigger> generalTriggers = (List)BomboConfig.get().profileChatTriggers.get("General");
         List<BomboConfig.ChatTrigger> allTriggers = new ArrayList();
         if (activeTriggers != null) {
            allTriggers.addAll(activeTriggers);
         }

         if (generalTriggers != null && !BomboConfig.get().activeProfile.equals("General")) {
            allTriggers.addAll(generalTriggers);
         }

         for(BomboConfig.ChatTrigger trigger : allTriggers) {
            if (trigger.enabled && trigger.triggerText != null && !trigger.triggerText.isEmpty()) {
               String triggerText = trigger.triggerText.replaceAll("§.", "").trim();
               if (!triggerText.isEmpty()) {
                  boolean hasVars = triggerText.contains("${");
                  if (hasVars) {
                     String[] parts = triggerText.split("\\$\\{[a-zA-Z0-9_]+\\}", -1);
                     Matcher varMatcher = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}").matcher(triggerText);
                     StringBuilder regexBuilder = new StringBuilder();
                     List<String> varNames = new ArrayList();

                     int partIdx;
                     for(partIdx = 0; varMatcher.find(); ++partIdx) {
                        regexBuilder.append(Pattern.quote(parts[partIdx]));
                        varNames.add(varMatcher.group(1));
                        if (partIdx != parts.length - 1 && (partIdx != parts.length - 2 || !parts[partIdx + 1].isEmpty())) {
                           regexBuilder.append("(.*?)");
                        } else {
                           regexBuilder.append("(.*)");
                        }
                     }

                     if (partIdx < parts.length && !parts[partIdx].isEmpty()) {
                        regexBuilder.append(Pattern.quote(parts[partIdx]));
                        regexBuilder.append(".*");
                     }

                     try {
                        Pattern pattern = Pattern.compile(regexBuilder.toString(), 2);
                        Matcher matcher = pattern.matcher(cleanMessage);
                        if (matcher.find()) {
                           String cmd = trigger.commandToRun;
                           String title = trigger.titleToShow;

                           for(int i = 0; i < varNames.size(); ++i) {
                              String varName = (String)varNames.get(i);
                              String value = matcher.group(i + 1);
                              if (cmd != null) {
                                 cmd = cmd.replace("${" + varName + "}", value);
                              }

                              if (title != null) {
                                 title = title.replace("${" + varName + "}", value);
                              }
                           }

                           if (cmd != null && !cmd.isEmpty()) {
                              executeTracked(cmd);
                           }

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

      }
   }

   public static void playTriggerSound(String soundId, int times) {
      if (soundId == null || soundId.trim().isEmpty()) return;
      int count = Math.max(1, times);
      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> {
         try {
            String clean = soundId.trim();
            Identifier id = Identifier.tryParse(clean.contains(":") ? clean : "minecraft:" + clean);
            if (id != null) {
               net.minecraft.sounds.SoundEvent se = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id);
               for (int i = 0; i < count; i++) {
                  final int delay = i * 150;
                  if (delay == 0) {
                     mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(se, 1.0F));
                  } else {
                     new Thread(() -> {
                        try {
                           Thread.sleep(delay);
                           mc.execute(() -> mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(se, 1.0F)));
                        } catch (Exception ignored) {}
                     }).start();
                  }
               }
            }
         } catch (Exception ignored) {}
      });
   }

   public static Map<String, Integer> getEnchantments(ItemStack stack) {
      return AutoCombine.getEnchantments(stack);
   }

   private static String getChromaAllRainbowText() {
      return "Red Orange Yellow Green Aqua Blue Pink Purple";
   }

   private static String cleanName(String name) {
      return name.trim().replaceAll("(?i)§[0-9a-fk-or]", "");
   }

   public static void reconnect(Screen parentScreen, Minecraft mc) {
      if (mc == null) {
         mc = Minecraft.getInstance();
      }

      ServerData server = lastServerData;
      if (server == null) {
         server = new ServerData("Hypixel", "hypixel.net", Type.OTHER);
      }

      if (BomboConfig.get().debugReconnect) {
         String var10001 = server.ip;
         DebugUtils.debug("reconnect", "Executing reconnect to server IP: " + var10001 + " (parent=" + String.valueOf(parentScreen) + ")");
      }

      ServerAddress address = ServerAddress.parseString(server.ip);
      Screen parent = (Screen)(parentScreen != null ? parentScreen : new JoinMultiplayerScreen(new TitleScreen()));
      ConnectScreen.startConnecting(parent, mc, address, server, false, (TransferState)null);
   }

   public static void triggerLocraw() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.connection != null && SkyblockUtils.isConnectedToHypixel()) {
         ++expectingLocrawCount;
         lastLocrawTime = System.currentTimeMillis();

         try {
            mc.player.connection.sendCommand("locraw");
         } catch (Exception var2) {
            expectingLocrawCount = Math.max(0, expectingLocrawCount - 1);
         }
      }

   }

   private static MutableComponent createHelpLine(String command, String suggestion, String description) {
      return Component.literal("§b" + command).withStyle((style) -> style.withClickEvent(new ClickEvent.SuggestCommand(suggestion)).withHoverEvent(SBECommands.createHoverEvent("§b" + suggestion + "\n\n§7" + description)));
   }

   public static List<String> splitCommands(String input) {
      List<String> result = new ArrayList();
      if (input != null && !input.trim().isEmpty()) {
         if (input.contains("/")) {
            String[] parts = input.split("\\s+(?=/)");

            for(String p : parts) {
               String trimmed = p.trim();
               if (!trimmed.isEmpty()) {
                  result.add(trimmed);
               }
            }
         } else {
            String[] parts = input.split("\\s+");

            for(String p : parts) {
               String trimmed = p.trim();
               if (!trimmed.isEmpty()) {
                  result.add(trimmed);
               }
            }
         }

         return result;
      } else {
         return result;
      }
   }

   private static void showRankCommand(FabricClientCommandSource source, String username) {
      source.sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Checking rank for §e" + username + "§7..."));
      (new Thread(() -> {
         try {
            URL url = (new URI(BomboApiUrl.getApiUrl("/nw/" + username))).toURL();
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int status = conn.getResponseCode();
            if (status == 200) {
               label49: {
                  InputStreamReader reader = new InputStreamReader(conn.getInputStream(), "UTF-8");

                  label41: {
                     try {
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj.has("data")) {
                           JsonObject data = obj.getAsJsonObject("data");
                           if (data.has("rank")) {
                              String rank = data.get("rank").getAsString();
                              if (rank != null) {
                                 RankCache.setRank(username, rank);
                                 Minecraft.getInstance().execute(() -> source.sendFeedback(Component.literal("§8[§3Bombo§8]§r §7API Rank for §e" + username + "§7: " + (rank.isEmpty() ? "§7None" : rank))));
                                 break label41;
                              }
                           }
                        }
                     } catch (Throwable var10) {
                        try {
                           reader.close();
                        } catch (Throwable x2) {
                           var10.addSuppressed(x2);
                        }

                        throw var10;
                     }

                     reader.close();
                     break label49;
                  }

                  reader.close();
                  return;
               }
            }

            Minecraft.getInstance().execute(() -> source.sendFeedback(Component.literal("§8[§3Bombo§8]§r §cFailed to fetch rank for §e" + username + " §7(Status: " + status + ")")));
         } catch (Exception e) {
            Minecraft.getInstance().execute(() -> source.sendFeedback(Component.literal("§8[§3Bombo§8]§r §cError fetching rank for §e" + username + "§c: " + e.getMessage())));
         }

      }, "Rank-Fetch-Command-" + username)).start();
   }

      public static int showCommandHistory(FabricClientCommandSource src, int limit, String filter) {
      if (commandHistory.isEmpty()) {
         src.sendFeedback(Component.literal("§8[§bBomboAddons§8] §cNo command history recorded yet."));
         return 0;
      }
      java.util.List<String> list = new java.util.ArrayList<>(commandHistory);
      if (filter != null && !filter.trim().isEmpty()) {
         String f = filter.toLowerCase(Locale.ROOT).trim();
         list.removeIf(cmd -> !cmd.toLowerCase(Locale.ROOT).contains(f));
      }
      if (list.isEmpty()) {
         src.sendFeedback(Component.literal("§8[§bBomboAddons§8] §cNo commands matched filter: §e" + filter));
         return 0;
      }
      int total = list.size();
      int start = Math.max(0, total - limit);
      src.sendFeedback(Component.literal("§8[§bBomboAddons§8] §e=== Command History (" + (total - start) + "/" + total + ") ==="));
      for (int i = start; i < total; i++) {
         String cmd = list.get(i);
         ClickEvent runClick = LF.createClickEventRobust("RUN_COMMAND", cmd);
         ClickEvent sugClick = LF.createClickEventRobust("SUGGEST_COMMAND", cmd);
         Component line = Component.literal(" §7" + (i + 1) + ". §f" + cmd + " ")
            .append(Component.literal("§a[Run]").withStyle(st -> runClick != null ? st.withClickEvent(runClick) : st))
            .append(Component.literal(" "))
            .append(Component.literal("§b[Suggest]").withStyle(st -> sugClick != null ? st.withClickEvent(sugClick) : st));
         src.sendFeedback(line);
      }
      return 1;
   }

      public static int showBestiaryDebug(FabricClientCommandSource src, String filter) {
      String island = SkyblockUtils.getLocation();
      String subarea = SkyblockUtils.getSubArea();
      int totalRules = me.bombo.bomboaddons.features.BestiaryDataFetcher.getTotalRuleCount();
      src.sendFeedback(Component.literal("§8[§bBomboAddons§8] §e=== Bestiary Diagnostics ==="));
      src.sendFeedback(Component.literal("§7- Current Island: §e" + (island.isEmpty() ? "Unknown" : island)));
      src.sendFeedback(Component.literal("§7- Current SubArea: §b" + (subarea.isEmpty() ? "None" : subarea)));
      src.sendFeedback(Component.literal("§7- Total Loaded Rules: §a" + totalRules + " §7(from https://api.bombo.dpdns.org/mod/bestiary)"));
      
      java.util.List<me.bombo.bomboaddons.features.BestiaryDataFetcher.BestiaryMobRule> list = new java.util.ArrayList<>();
      if (filter != null && !filter.trim().isEmpty()) {
         String f = filter.toLowerCase(Locale.ROOT).trim();
         var allRules = me.bombo.bomboaddons.features.BestiaryDataFetcher.getAllRules();
         if (allRules != null) {
            for (var rule : allRules) {
               if (rule.name != null && rule.name.toLowerCase(Locale.ROOT).contains(f)) {
                  list.add(rule);
               }
            }
         }
         src.sendFeedback(Component.literal("§7- Matching Mobs for filter '§e" + filter + "§7' (" + list.size() + "):"));
      } else {
         var islandRules = me.bombo.bomboaddons.features.BestiaryDataFetcher.getRulesForIsland(island);
         if (islandRules != null) list.addAll(islandRules);
         src.sendFeedback(Component.literal("§7- Mobs for " + island + " (" + list.size() + "):"));
      }
      
      for (var r : list) {
         String ent = r.entityType != null && !r.entityType.isEmpty() ? r.entityType : "entity";
         String extra = "";
         if (r.armor != null && !r.armor.isEmpty()) extra += " §8(armor: " + r.armor + "§8)";
         String sz = r.mobSize != null && !r.mobSize.isEmpty() ? r.mobSize : r.size;
         if (sz != null && !sz.isEmpty()) extra += " §8(size: " + sz + "§8)";
         if (r.subarea != null && !r.subarea.isEmpty()) extra += " §8(subarea: " + r.subarea + "§8)";
         src.sendFeedback(Component.literal(" §7• §e" + r.name + " §7| Max Tier: §a" + (r.maxTier > 0 ? r.maxTier : 20) + " §7| Entity: §f" + ent + extra));
      }
      return 1;
   }

      public static int handleBestiaryAddCommand(FabricClientCommandSource src, String rawMob, String colorOverride) {
      if (rawMob == null || rawMob.trim().isEmpty()) {
         src.sendFeedback(Component.literal("§8[§3Bombo§8] §cUsage: /b behighlight add <mob name>"));
         return 0;
      }
      String cleanMob = me.bombo.bomboaddons.features.BestiaryManager.cleanMobName(rawMob).trim();
      var rule = me.bombo.bomboaddons.features.BestiaryDataFetcher.getRule(cleanMob, null);
      String islandReq = (rule != null && rule.island != null) ? rule.island : "";
      String cat = me.bombo.bomboaddons.features.BestiaryManager.getCategoryForIsland(islandReq);
      String color = (colorOverride != null && !colorOverride.trim().isEmpty()) ? colorOverride.toUpperCase(Locale.ROOT) : me.bombo.bomboaddons.features.BestiaryManager.getCategoryColor(cat);
      boolean tracer = me.bombo.bomboaddons.features.BestiaryManager.getCategoryTracer(cat);

      BomboConfig.Settings s = BomboConfig.get();
      if (s.highlights == null) s.highlights = new java.util.HashMap<>();
      BomboConfig.HighlightInfo info = new BomboConfig.HighlightInfo(color, false, true, tracer, islandReq, true);
      if (rule != null) {
         if (rule.entityType != null) info.entityType = rule.entityType;
         if (rule.heads != null && !rule.heads.isEmpty()) info.headHashes = new java.util.ArrayList<>(rule.heads);
         if (rule.armor != null) info.armorType = rule.armor;
         if (rule.playerName != null) info.playerName = rule.playerName;
         if (rule.riding != null) info.ridingType = rule.riding;
         if (rule.heldItem != null) info.heldItem = rule.heldItem;
         if (rule.subarea != null) info.requiredSubarea = rule.subarea;
      }
      s.highlights.put(cleanMob.toLowerCase(Locale.ROOT), info);
      s.highlightsEnabled = true;
      BomboConfig.save();

      String islandDisplay = islandReq.isEmpty() ? "Everywhere" : islandReq;
      src.sendFeedback(Component.literal("§8[§3Bombo§8] §aAdded Bestiary highlight for §e" + cleanMob + " §7(Category: §b" + cat + "§7, Color: " + me.bombo.bomboaddons.features.BestiaryManager.getColorFormatting(color) + color + "§7)"));
      return 1;
   }

   public static void displayEntityDetails(FabricClientCommandSource src, Minecraft mc, Entity target, int index, int total) {
      if (target == null) return;
      String type = target.getType().getDescription().getString();
      String rawName = target.getName().getString();
      String dispName = target.getDisplayName().getString();
      String customName = target.getCustomName() != null ? target.getCustomName().getString() : "None";
      String uuidStr = target.getUUID().toString();
      int id = target.getId();
      double dist = mc.player != null ? Math.sqrt(mc.player.distanceToSqr(target)) : 0.0;
      double x = Math.round(target.getX() * 100.0) / 100.0;
      double y = Math.round(target.getY() * 100.0) / 100.0;
      double z = Math.round(target.getZ() * 100.0) / 100.0;

      String header = (total > 1) ? "§8[§3Bombo§8] §e=== Entity Details (#" + index + "/" + total + ") ===" : "§8[§3Bombo§8] §e=== Entity Details ===";
      src.sendFeedback(Component.literal(header));
      src.sendFeedback(Component.literal(" §7Type: §f" + type + " §8(§7Class: " + target.getClass().getSimpleName() + "§8)"));
      src.sendFeedback(Component.literal(" §7Name: §f" + rawName + " §7| Display: §f" + dispName));
      if (!customName.equals("None")) {
         src.sendFeedback(Component.literal(" §7Custom Name: §f" + customName));
      }
      src.sendFeedback(Component.literal(" §7UUID: §e" + uuidStr + " §7| ID: §a" + id + " §7| Dist: §b" + String.format(Locale.ROOT, "%.1fm", dist)));
      src.sendFeedback(Component.literal(" §7Position: §f" + x + ", " + y + ", " + z));

      double w = target.getBbWidth();
      double h = target.getBbHeight();
      double scale = Math.max(w / 0.6, h / 1.8);
      String sizeCategory = "normal";
      if (target instanceof net.minecraft.world.entity.monster.Slime) {
         int sz = ((net.minecraft.world.entity.monster.Slime)target).getSize();
         if (sz >= 4) sizeCategory = "Big";
         else if (sz == 1) sizeCategory = "Small";
         else sizeCategory = "Medium";
         scale = (double) sz;
      } else if (scale >= 2.0) {
         sizeCategory = "Big";
      } else if (scale <= 0.6) {
         sizeCategory = "Small";
      }
      String sizeStr = String.format(Locale.ROOT, "%.2f (%s)", scale, sizeCategory);
      src.sendFeedback(Component.literal(" §7Size: §f" + sizeStr));

      // Specialized mob variant inspection
      EntityVariantHelper.VariantResult varRes = EntityVariantHelper.inspect(target);
      if (varRes != null) {
         if (varRes.lineText != null) {
            src.sendFeedback(Component.literal(varRes.lineText));
         }
         if (varRes.buttonText != null && varRes.command != null) {
            ClickEvent addHl = LF.createClickEventRobust("RUN_COMMAND", varRes.command);
            if (addHl != null) {
               src.sendFeedback(Component.literal(" " + varRes.buttonText).withStyle(s -> s.withClickEvent(addHl)));
            }
         }
      }

      // Equipment Inspection
      if (target instanceof net.minecraft.world.entity.LivingEntity living) {
         StringBuilder equipStr = new StringBuilder();
         for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack is = living.getItemBySlot(slot);
            if (!is.isEmpty()) {
               if (equipStr.length() > 0) equipStr.append(", ");
               equipStr.append(slot.getName()).append(": ").append(is.getHoverName().getString());
            }
         }
         if (equipStr.length() > 0) {
            src.sendFeedback(Component.literal(" §7Equipment: §f" + equipStr.toString()));
         }
      }

      String headTex = TargetPests.getHeadTextureValue(target);
      if (headTex == null) {
         for (Entity p : target.getPassengers()) {
            headTex = TargetPests.getHeadTextureValue(p);
            if (headTex != null) break;
         }
      }
      if (headTex == null && target.getVehicle() != null) {
         headTex = TargetPests.getHeadTextureValue(target.getVehicle());
      }
      if (headTex != null) {
         String hash = TargetPests.extractTextureHash(headTex);
         String toCopy = hash != null ? hash : headTex;
         mc.keyboardHandler.setClipboard(toCopy);
         src.sendFeedback(Component.literal(" §a✓ Copied head ID to clipboard: §e" + toCopy));
         src.sendFeedback(Component.literal(" §7Skull Texture Hash: §e" + (hash != null ? hash : "N/A")));
         if (hash != null) {
            ClickEvent addHl = LF.createClickEventRobust("RUN_COMMAND", "/b highlight add " + hash + " GOLD");
            ClickEvent copyHash = LF.createClickEventRobust("COPY_TO_CLIPBOARD", hash);
            Component hashAction = Component.literal(" §a[+ Highlight Head]")
               .withStyle(style -> addHl != null ? style.withClickEvent(addHl) : style)
               .append(Component.literal(" §b[Copy Hash]").withStyle(style -> copyHash != null ? style.withClickEvent(copyHash) : style));
            src.sendFeedback(hashAction);
         }
      } else if (target instanceof ItemEntity) {
         ItemStack item = ((ItemEntity)target).getItem();
         if (!item.isEmpty()) {
            String iName = item.getHoverName().getString();
            src.sendFeedback(Component.literal(" §7Item Entity: §e" + iName));
         }
      }
      ClickEvent copyUuid = LF.createClickEventRobust("COPY_TO_CLIPBOARD", uuidStr);
      String hlTarget = rawName.contains(" ") ? "\"" + rawName + "\"" : rawName;
      ClickEvent addHlTarget = LF.createClickEventRobust("RUN_COMMAND", "/b highlight add " + hlTarget + " GOLD");
      ClickEvent toggleTracer = LF.createClickEventRobust("RUN_COMMAND", "/b tracer");
      ClickEvent removeHl = LF.createClickEventRobust("RUN_COMMAND", "/b highlight remove");
      Component actionRow = Component.literal(" §a[+ Highlight]")
         .withStyle(style -> addHlTarget != null ? style.withClickEvent(addHlTarget) : style)
         .append(Component.literal(" §b[Copy UUID]").withStyle(style -> copyUuid != null ? style.withClickEvent(copyUuid) : style))
         .append(Component.literal(" §e[Tracer]").withStyle(style -> toggleTracer != null ? style.withClickEvent(toggleTracer) : style))
         .append(Component.literal(" §c[Remove]").withStyle(style -> removeHl != null ? style.withClickEvent(removeHl) : style));
      src.sendFeedback(actionRow);
   }

   public static void fetchAndShowNameHistory(FabricClientCommandSource src, String username) {
      if (username == null || username.trim().isEmpty()) return;
      src.sendFeedback(Component.literal("§8[§3Bombo§8] §7Fetching name history for §b" + username + "§7..."));
      new Thread(() -> {
         try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String url = BomboApiUrl.getApiUrl("/name/" + username.trim());
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().trim().isEmpty()) {
               String body = resp.body().trim();
               Minecraft mc = Minecraft.getInstance();
               mc.execute(() -> {
                  try {
                     JsonElement parsed = JsonParser.parseString(body);
                     class NameRecord {
                        String name;
                        String dateShort;
                        String fullDate;
                        String relative;
                        boolean isOriginal;
                     }
                     List<NameRecord> records = new ArrayList<>();

                     JsonArray arr = null;
                     if (parsed.isJsonArray()) {
                        arr = parsed.getAsJsonArray();
                     } else if (parsed.isJsonObject()) {
                        JsonObject obj = parsed.getAsJsonObject();
                        if (obj.has("history") && obj.get("history").isJsonArray()) arr = obj.getAsJsonArray("history");
                        else if (obj.has("names") && obj.get("names").isJsonArray()) arr = obj.getAsJsonArray("names");
                        else if (obj.has("name_history") && obj.get("name_history").isJsonArray()) arr = obj.getAsJsonArray("name_history");
                     }

                     if (arr != null) {
                        for (JsonElement el : arr) {
                           if (el.isJsonObject()) {
                              JsonObject o = el.getAsJsonObject();
                              if (o.has("name") && !o.get("name").isJsonNull()) {
                                 NameRecord rec = new NameRecord();
                                 rec.name = o.get("name").getAsString();
                                 
                                 boolean orig = false;
                                 if (o.has("num") && o.get("num").getAsInt() == 1) orig = true;
                                 if (o.has("timeFormatted") && !o.get("timeFormatted").isJsonNull()) {
                                    String tf = o.get("timeFormatted").getAsString();
                                    if (tf.equalsIgnoreCase("Original Name") || tf.toLowerCase().contains("original")) {
                                       orig = true;
                                    } else {
                                       rec.fullDate = tf;
                                    }
                                 }
                                 if (o.has("relativeTime") && !o.get("relativeTime").isJsonNull()) {
                                    rec.relative = o.get("relativeTime").getAsString();
                                 }
                                 if (o.has("changedAtTimestamp") && !o.get("changedAtTimestamp").isJsonNull()) {
                                    try {
                                       long ts = o.get("changedAtTimestamp").getAsLong();
                                       if (ts > 0) {
                                          rec.dateShort = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(ts));
                                          if (rec.fullDate == null) {
                                             rec.fullDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(ts));
                                          }
                                       } else {
                                          orig = true;
                                       }
                                    } catch (Exception ignored) {}
                                 } else if (o.has("changedAt") && !o.get("changedAt").isJsonNull()) {
                                    String ca = o.get("changedAt").getAsString();
                                    if (ca.isEmpty() || ca.equalsIgnoreCase("null")) orig = true;
                                    else if (rec.dateShort == null) rec.dateShort = ca.split("T")[0];
                                 } else if (!o.has("changedAtTimestamp") && !o.has("changedAt")) {
                                    orig = true;
                                 }
                                 rec.isOriginal = orig;
                                 records.add(rec);
                              }
                           } else if (el.isJsonPrimitive()) {
                              NameRecord rec = new NameRecord();
                              rec.name = el.getAsString();
                              records.add(rec);
                           }
                        }
                     }

                     if (records.isEmpty()) {
                        src.sendFeedback(Component.literal("§8[§3Bombo§8] §cNo name history found for §e" + username));
                        return;
                     }

                     if (records.size() > 1 && records.get(records.size() - 1).fullDate == null) {
                        records.get(records.size() - 1).isOriginal = true;
                     }

                     src.sendFeedback(Component.literal("§8[§3Bombo§8] §eName history for §b" + username + " §7(" + records.size() + "):"));
                     for (int i = 0; i < records.size(); i++) {
                        NameRecord rec = records.get(i);
                        boolean isCurrent = (i == 0);
                        boolean isOrig = rec.isOriginal || (i == records.size() - 1 && records.size() > 1);

                        String prefix = isCurrent ? "§a" : (isOrig ? "§e" : "§f");
                        String dateStr = isOrig ? " §8(Original)" : (rec.dateShort != null ? " §8(" + rec.dateShort + ")" : "");

                        String hoverText;
                        if (isOrig) {
                           hoverText = "§aOriginal Account Name";
                        } else {
                           String d = rec.fullDate != null ? rec.fullDate : (rec.dateShort != null ? rec.dateShort : "Unknown");
                           hoverText = "§eChanged on: §f" + d + (rec.relative != null ? "\n§7Relative: §b" + rec.relative : "");
                        }

                        net.minecraft.network.chat.MutableComponent nameComp = Component.literal(prefix + rec.name);
                        HoverEvent hover = LF.createHoverEventRobust(hoverText + "\n§7Click to copy name: §f" + rec.name);
                        ClickEvent click = LF.createClickEventRobust("COPY_TO_CLIPBOARD", rec.name);
                        if (hover != null) nameComp.setStyle(nameComp.getStyle().withHoverEvent(hover));
                        if (click != null) nameComp.setStyle(nameComp.getStyle().withClickEvent(click));

                        net.minecraft.network.chat.MutableComponent line = Component.literal(" §7" + (i + 1) + ". ").append(nameComp).append(Component.literal(dateStr));
                        src.sendFeedback(line);
                     }
                  } catch (Exception ex) {
                     src.sendFeedback(Component.literal("§8[§3Bombo§8] §cFailed to parse name history: §e" + ex.getMessage()));
                  }
               });
            } else {
               Minecraft.getInstance().execute(() -> {
                  src.sendFeedback(Component.literal("§8[§3Bombo§8] §cFailed to fetch name history (HTTP " + resp.statusCode() + ") for §e" + username));
               });
            }
         } catch (Exception e) {
            Minecraft.getInstance().execute(() -> {
               src.sendFeedback(Component.literal("§8[§3Bombo§8] §cError fetching name history: §e" + e.getMessage()));
            });
         }
      }, "Bombo-NameHistory-Fetcher").start();
   }

   private static void findAndClickYes(Component component) {
      if (component != null) {
         Style style = component.getStyle();
         ClickEvent clickEvent = style.getClickEvent();
         if (BomboConfig.get().debugChat) {
            String var10001 = component.getString();
            DebugUtils.debug("chat", "Inspecting Component: text=\"" + var10001 + "\", style=" + String.valueOf(style) + ", clickEvent=" + String.valueOf(clickEvent));
         }

         if (clickEvent != null) {
            try {
               ClickEvent.Action actionObj = null;
               String valueStr = null;
               Class<?> clazz = clickEvent.getClass();
               if (BomboConfig.get().debugChat) {
                  DebugUtils.debug("chat", "ClickEvent Class: " + clazz.getName());
               }

               while(clazz != null && clazz != Object.class) {
                  for(Field field : clazz.getDeclaredFields()) {
                     try {
                        field.setAccessible(true);
                        Object val = field.get(clickEvent);
                        if (BomboConfig.get().debugChat) {
                           String var24 = field.getName();
                           DebugUtils.debug("chat", "Field: " + var24 + " of type " + field.getType().getName() + " = " + String.valueOf(val));
                        }

                        if (val instanceof ClickEvent.Action) {
                           actionObj = (ClickEvent.Action)val;
                        } else if (val instanceof String) {
                           valueStr = (String)val;
                        }
                     } catch (Throwable t) {
                        if (BomboConfig.get().debugChat) {
                           String var23 = field.getName();
                           DebugUtils.debug("chat", "Field error (" + var23 + "): " + t.getMessage());
                        }
                     }
                  }

                  for(Method method : clazz.getDeclaredMethods()) {
                     if (method.getParameterCount() == 0 && !method.getName().equals("toString") && !method.getName().equals("name")) {
                        try {
                           method.setAccessible(true);
                           Object val = method.invoke(clickEvent);
                           if (BomboConfig.get().debugChat) {
                              String var26 = method.getName();
                              DebugUtils.debug("chat", "Method: " + var26 + " returning " + method.getReturnType().getName() + " = " + String.valueOf(val));
                           }

                           if (val instanceof ClickEvent.Action) {
                              actionObj = (ClickEvent.Action)val;
                           } else if (val instanceof String) {
                              valueStr = (String)val;
                           }
                        } catch (Throwable t) {
                           if (BomboConfig.get().debugChat) {
                              String var25 = method.getName();
                              DebugUtils.debug("chat", "Method error (" + var25 + "): " + t.getMessage());
                           }
                        }
                     }
                  }

                  clazz = clazz.getSuperclass();
               }

               if (BomboConfig.get().debugChat) {
                  String var27 = String.valueOf(actionObj);
                  DebugUtils.debug("chat", "Resolved Action: " + var27 + ", Resolved Value: " + valueStr);
               }

               if (actionObj == null) {
                  String className = clickEvent.getClass().getSimpleName().toLowerCase();
                  if (className.contains("runcommand")) {
                     actionObj = Action.RUN_COMMAND;
                  }
               }

               if (actionObj == Action.RUN_COMMAND && valueStr != null) {
                  String lower = valueStr.toLowerCase();
                  if (lower.startsWith("/chatprompt ") && lower.endsWith(" yes")) {
                     executeTracked(valueStr);
                     Bomboaddons.sendMessage("&8[&bBomboAddons&8] &aAuto-accepted Trevor's quest!");
                     return;
                  }
               }
            } catch (Throwable var13) {
               if (BomboConfig.get().debugChat) {
                  DebugUtils.debug("chat", "Reflection critical error: " + var13.getMessage());
               }

               var13.printStackTrace();
            }
         }

         for(Component sibling : component.getSiblings()) {
            findAndClickYes(sibling);
         }

      }
   }

   public static void registerMultiPlayerPartyCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
      String[] cmdNames = new String[]{"p", "party", "f", "friend", "v", "visit", "w", "tell"};

      for(String name : cmdNames) {
         RequiredArgumentBuilder<FabricClientCommandSource, String> argsNode = (RequiredArgumentBuilder)ClientCommands.argument("args", StringArgumentType.greedyString()).suggests(TabCompletionManager::getUsernameSuggestions).executes((context) -> {
            String args = StringArgumentType.getString(context, "args");
            String actualName = name.equals("v") ? "visit" : (name.equals("p") ? "party" : (name.equals("f") ? "friend" : (!name.equals("w") && !name.equals("tell") ? name : "msg")));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
               mc.player.connection.send(new ServerboundChatCommandPacket(actualName + " " + args));
            }

            return 1;
         });
         LiteralArgumentBuilder<FabricClientCommandSource> builder = (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(name).executes((context) -> {
            String actualName = name.equals("v") ? "visit" : (name.equals("p") ? "party" : (name.equals("f") ? "friend" : (!name.equals("w") && !name.equals("tell") ? name : "msg")));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
               mc.player.connection.send(new ServerboundChatCommandPacket(actualName));
            }

            return 1;
         })).then(argsNode);
         dispatcher.register(builder);
         dispatcher.register(ClientCommands.literal("B").executes((context) -> { openGuiNextTick = true; return 1; }));
         dispatcher.register(ClientCommands.literal("bombo").executes((context) -> { openGuiNextTick = true; return 1; }));
         dispatcher.register(ClientCommands.literal("Bombo").executes((context) -> { openGuiNextTick = true; return 1; }));
         dispatcher.register(ClientCommands.literal("BOMBO").executes((context) -> { openGuiNextTick = true; return 1; }));
      }

   }

   private static Component extractHoverText(HoverEvent event) {
      if (event == null) {
         return null;
      } else {
         try {
            for(Field f : event.getClass().getDeclaredFields()) {
               if (Component.class.isAssignableFrom(f.getType())) {
                  f.setAccessible(true);
                  return (Component)f.get(event);
               }
            }
         } catch (Exception var5) {
         }

         return null;
      }
   }

   private static void checkApiEndpoint(String name, String urlStr) {
      try {
         URL url = new URL(urlStr);
         HttpURLConnection conn = (HttpURLConnection)url.openConnection();
         conn.setRequestMethod("GET");
         conn.setConnectTimeout(4000);
         conn.setReadTimeout(4000);
         conn.setRequestProperty("User-Agent", "BomboAddons/26.1.2");
         int code = conn.getResponseCode();
         String msg = code >= 200 && code < 300 ? "§aOK (" + code + ")" : "§cHTTP " + code;
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7" + name + ": " + msg)));
         }
      } catch (Exception e) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7" + name + ": §cFAILED (" + e.getMessage() + ")")));
         }
      }

   }

   private static Component addHoverToCommands(Component component) {
      if (component == null) {
         return null;
      } else {
         MutableComponent newComp = component.copy();
         newComp.getSiblings().clear();
         Style style = newComp.getStyle();
         if (style != null) {
            ClickEvent var4 = style.getClickEvent();
            if (var4 instanceof ClickEvent.RunCommand) {
               ClickEvent.RunCommand runCmd = (ClickEvent.RunCommand)var4;
               String command = runCmd.command();
               HoverEvent existingHoverEvent = style.getHoverEvent();
               Component oldText = extractHoverText(existingHoverEvent);
               if (oldText != null) {
                  MutableComponent combined = Component.empty().append(oldText).append("\n§7Run on click: §e" + command);
                  newComp.setStyle(style.withHoverEvent(SBECommands.createHoverEventFromComponent(combined)));
               } else {
                  newComp.setStyle(style.withHoverEvent(SBECommands.createHoverEvent("§7Run on click: §e" + command)));
               }
            }
         }

         for(Component sibling : component.getSiblings()) {
            newComp.append(addHoverToCommands(sibling));
         }

         return newComp;
      }
   }

   public static String formatDuration(long seconds) {
      if (seconds <= 0L) {
         return "0s";
      } else {
         long h = seconds / 3600L;
         long m = seconds % 3600L / 60L;
         long s = seconds % 60L;
         if (h > 0L) {
            return String.format("%dh %dm %ds", h, m, s);
         } else {
            return m > 0L ? String.format("%dm %ds", m, s) : String.format("%ds", s);
         }
      }
   }

   private static void printEventInfo(FabricClientCommandSource source, String eventName, long startOffset, long durationSec, long cyclePos, long cycleLength) {
      long endOffset = startOffset + durationSec;
      if (cyclePos >= startOffset && cyclePos < endOffset) {
         long remaining = endOffset - cyclePos;
         source.sendFeedback(Component.literal("  §7» §e" + eventName + ": §a§lACTIVE §7(Ends in §b" + formatDuration(remaining) + "§7)"));
      } else {
         long untilNext;
         if (cyclePos < startOffset) {
            untilNext = startOffset - cyclePos;
         } else {
            untilNext = cycleLength - cyclePos + startOffset;
         }

         source.sendFeedback(Component.literal("  §7» §e" + eventName + ": §cInactive §7(Starts in §b" + formatDuration(untilNext) + "§7)"));
      }

   }

   public static void showDebugApiEvents(FabricClientCommandSource source) {
      source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §6=== SkyBlock Calendar & Event Schedule ==="));
      long now = System.currentTimeMillis() / 1000L;
      long skyblockEpoch = 1560275700L;
      long yearLength = 446400L;
      long monthLength = 37200L;
      long dayLength = 1200L;
      long elapsed = now - skyblockEpoch;
      long yearCycle = (elapsed % yearLength + yearLength) % yearLength;
      long secInHour = now % 3600L;
      if (secInHour >= 3300L) {
         source.sendFeedback(Component.literal("  §7» §eDark Auction: §a§lACTIVE §7(Ends in §b" + formatDuration(3600L - secInHour) + "§7)"));
      } else {
         source.sendFeedback(Component.literal("  §7» §eDark Auction: §cInactive §7(Starts in §b" + formatDuration(3300L - secInHour) + "§7)"));
      }

      printEventInfo(source, "Hoppity's Hunt", 0L, 3L * monthLength, yearCycle, yearLength);
      printEventInfo(source, "Traveling Zoo (Summer)", 3L * monthLength, 3L * dayLength, yearCycle, yearLength);
      printEventInfo(source, "Spooky Festival", 8L * monthLength + 28L * dayLength, 3L * dayLength, yearCycle, yearLength);
      printEventInfo(source, "Traveling Zoo (Winter)", 9L * monthLength, 3L * dayLength, yearCycle, yearLength);
      printEventInfo(source, "Jerry's Workshop (Winter)", 11L * monthLength, monthLength, yearCycle, yearLength);
      printEventInfo(source, "New Year Celebration", 11L * monthLength + 28L * dayLength, 3L * dayLength, yearCycle, yearLength);
   }

   public static void runApiDebugTests() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §eTesting BomboAPI Endpoints..."));
         (new Thread(() -> {
            String[] endpoints = new String[]{"https://api.bombo.dpdns.org/prices", "https://api.bombo.dpdns.org/prices2", "https://api.bombo.dpdns.org/bazaar", "https://api.bombo.dpdns.org/calendar"};

            for(String urlStr : endpoints) {
               long start = System.currentTimeMillis();

               try {
                  URL url = new URL(urlStr);
                  HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                  conn.setConnectTimeout(5000);
                  conn.setReadTimeout(5000);
                  conn.setRequestMethod("GET");
                  int code = conn.getResponseCode();
                  long duration = System.currentTimeMillis() - start;
                  String name = urlStr.substring(urlStr.lastIndexOf(47));
                  if (code == 200) {
                     mc.execute(() -> {
                        if (mc.player != null) {
                           mc.player.sendSystemMessage(Component.literal("§7- §e" + name + " §7: §a" + code + " OK §7(" + duration + "ms)"));
                        }

                     });
                  } else {
                     mc.execute(() -> {
                        if (mc.player != null) {
                           mc.player.sendSystemMessage(Component.literal("§7- §e" + name + " §7: §c" + code + " §7(" + duration + "ms)"));
                        }

                     });
                  }
               } catch (Throwable t) {
                  long duration = System.currentTimeMillis() - start;
                  String name = urlStr.substring(urlStr.lastIndexOf(47));
                  mc.execute(() -> {
                     if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§7- §e" + name + " §7: §cError (" + t.getMessage() + ") §7(" + duration + "ms)"));
                     }

                  });
               }
            }

         }, "BomboApiDebugThread")).start();
      }
   }

   public static CompletableFuture<Suggestions> getItemSuggestions(SuggestionsBuilder builder) {
      String fullRemaining = builder.getRemaining();
      int lastSpaceIndex = fullRemaining.lastIndexOf(32);
      String remaining;
      SuggestionsBuilder actualBuilder;
      if (lastSpaceIndex != -1) {
         remaining = fullRemaining.substring(lastSpaceIndex + 1).toLowerCase();
         actualBuilder = builder.createOffset(builder.getStart() + lastSpaceIndex + 1);
      } else {
         remaining = fullRemaining.toLowerCase();
         actualBuilder = builder;
      }

      Set<String> added = new HashSet();
      Map<String, SkyblockItemManager.SkyblockItemInfo> itemMap = SkyblockItemManager.getItemCache();
      if (itemMap != null) {
         for(SkyblockItemManager.SkyblockItemInfo info : itemMap.values()) {
            if (info != null && info.name != null) {
               String cleanName = info.name.replaceAll("(?i)§.", "").trim();
               String lowerName = cleanName.toLowerCase();
               String lowerId = info.id != null ? info.id.toLowerCase() : "";
               if ((remaining.isEmpty() || lowerName.startsWith(remaining) || lowerId.startsWith(remaining) || lowerName.contains(remaining)) && added.add(cleanName)) {
                  actualBuilder.suggest(cleanName);
               }
            }
         }
      }

      return actualBuilder.buildFuture();
   }

   public static CompletableFuture<Suggestions> getCollectionSuggestions(SuggestionsBuilder builder) {
      String fullRemaining = builder.getRemaining();
      int lastSpaceIndex = fullRemaining.lastIndexOf(32);
      String remaining;
      SuggestionsBuilder actualBuilder;
      if (lastSpaceIndex != -1) {
         remaining = fullRemaining.substring(lastSpaceIndex + 1).toLowerCase();
         actualBuilder = builder.createOffset(builder.getStart() + lastSpaceIndex + 1);
      } else {
         remaining = fullRemaining.toLowerCase();
         actualBuilder = builder;
      }

      String[] collections = new String[]{"wheat", "carrot", "potato", "pumpkin", "melon", "seeds", "red_mushroom", "brown_mushroom", "cactus", "cocoa_beans", "sugar_cane", "feather", "leather", "porkchop", "chicken", "mutton", "raw_beef", "rabbit", "nether_wart", "cobblestone", "coal", "iron_ingot", "gold_ingot", "diamond", "lapis_lazuli", "emerald", "redstone", "quartz", "obsidian", "glowstone_dust", "gravel", "ice", "netherrack", "sand", "end_stone", "mithril", "titanium", "gemstone", "hard_stone", "rotten_flesh", "bone", "string", "spider_eye", "gunpowder", "ender_pearl", "blaze_rod", "slimeball", "magma_cream", "ghast_tear", "raw_fish", "raw_salmon", "clownfish", "pufferfish", "prismarine_shards", "prismarine_crystals", "clay", "water_lily", "ink_sac", "sponge", "oak_wood", "spruce_wood", "birch_wood", "jungle_wood", "acacia_wood", "dark_oak_wood"};

      for(String c : collections) {
         String lower = c.toLowerCase();
         String clean = c.replace("_", " ");
         if (remaining.isEmpty() || lower.startsWith(remaining) || clean.toLowerCase().startsWith(remaining) || lower.contains(remaining)) {
            actualBuilder.suggest(clean);
         }
      }

      return actualBuilder.buildFuture();
   }

   public static void fetchAndShowCalendarApi(FabricClientCommandSource source) {
      source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §eFetching SkyBlock Calendar API data..."));
      (new Thread(() -> {
         try {
            URL url = new URL("https://api.bombo.dpdns.org/calendar");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code == 200) {
               InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);

               try {
                  JsonElement parsed = JsonParser.parseReader(reader);
                  Minecraft mc = Minecraft.getInstance();
                  mc.execute(() -> {
                     source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §6=== SkyBlock Calendar API Data ==="));
                     if (parsed.isJsonObject() && parsed.getAsJsonObject().has("events")) {
                        for(JsonElement el : parsed.getAsJsonObject().getAsJsonArray("events")) {
                           if (el.isJsonObject()) {
                              JsonObject ev = el.getAsJsonObject();
                              String name = ev.has("name") ? ev.get("name").getAsString() : "Event";
                              String status = ev.has("status") ? ev.get("status").getAsString() : "Unknown";
                              String time = ev.has("time") ? ev.get("time").getAsString() : "";
                              source.sendFeedback(Component.literal("  §7» §e" + name + ": §b" + status + (time.isEmpty() ? "" : " §7(" + time + ")")));
                           }
                        }
                     } else {
                        showDebugApiEvents(source);
                     }

                  });
               } catch (Throwable var8) {
                  try {
                     reader.close();
                  } catch (Throwable x2) {
                     var8.addSuppressed(x2);
                  }

                  throw var8;
               }

               reader.close();
            } else {
               Minecraft mc = Minecraft.getInstance();
               mc.execute(() -> showDebugApiEvents(source));
            }
         } catch (Throwable var9) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> showDebugApiEvents(source));
         }

      }, "CalendarApiThread")).start();
   }

   public static String resolveItemId(String query) {
      if (query != null && !query.trim().isEmpty()) {
         String clean = query.trim().replaceAll("(?i)§.", "");
         String cleanUpper = clean.toUpperCase().replace(" ", "_");
         Map<String, SkyblockItemManager.SkyblockItemInfo> cache = SkyblockItemManager.getItemCache();
         if (cache == null) {
            return cleanUpper;
         } else if (cache.containsKey(cleanUpper)) {
            return cleanUpper;
         } else {
            for(SkyblockItemManager.SkyblockItemInfo info : cache.values()) {
               if (info != null && info.name != null) {
                  String cName = info.name.replaceAll("(?i)§.", "").trim();
                  if (cName.equalsIgnoreCase(clean)) {
                     return info.id;
                  }
               }
            }

            for(SkyblockItemManager.SkyblockItemInfo info : cache.values()) {
               if (info != null && info.name != null) {
                  String cName = info.name.replaceAll("(?i)§.", "").trim();
                  if (cName.toLowerCase().startsWith(clean.toLowerCase())) {
                     return info.id;
                  }
               }
            }

            for(SkyblockItemManager.SkyblockItemInfo info : cache.values()) {
               if (info != null && info.name != null) {
                  String cName = info.name.replaceAll("(?i)§.", "").trim();
                  if (cName.toLowerCase().contains(clean.toLowerCase())) {
                     return info.id;
                  }
               }
            }

            return cleanUpper;
         }
      } else {
         return "";
      }
   }

   public static void printProfilesList(FabricClientCommandSource source) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gui != null) {
         ChatComponent var3 = mc.gui.getChat();
         if (var3 instanceof IChatComponent) {
            IChatComponent chatComp = (IChatComponent)var3;
            chatComp.bombo$removeProfileListMessages();
         }
      }

      BomboConfig.Settings s = BomboConfig.get();
      Set<String> set = new LinkedHashSet();
      String[] defaultProfs = new String[]{"default", "healer", "archer", "mage", "archerm6", "mining", "farming", "rend", "rendd", "fishing", "General", "foraging"};

      for(String dp : defaultProfs) {
         set.add(dp);
      }

      if (s.profileBinds != null) {
         set.addAll(s.profileBinds.keySet());
      }

      if (s.keybindBinds != null) {
         set.addAll(s.keybindBinds.keySet());
      }

      source.sendFeedback(Component.literal("§8[§bBomboAddons§8] §aYour Config Profiles:"));
      String active = s.activeProfile != null && !s.activeProfile.trim().isEmpty() ? s.activeProfile.trim() : "default";

      for(String p : set) {
         MutableComponent lineComp;
         if (p.equalsIgnoreCase(active)) {
            lineComp = Component.literal("▶ §e" + p + " §7(Active)");
         } else {
            lineComp = Component.literal("  §7- §e" + p);
         }

         lineComp.setStyle(lineComp.getStyle().withClickEvent(new ClickEvent.RunCommand("/b prof " + p)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to switch to profile §b" + p))));
         source.sendFeedback(lineComp);
      }

   }

   public static long parseMoneyValue(String input) {
      if (input != null && !input.trim().isEmpty()) {
         String str = input.trim().toLowerCase().replaceAll(",", "");

         try {
            if (str.endsWith("m")) {
               double val = Double.parseDouble(str.substring(0, str.length() - 1));
               return (long)(val * (double)1000000.0F);
            } else if (str.endsWith("k")) {
               double val = Double.parseDouble(str.substring(0, str.length() - 1));
               return (long)(val * (double)1000.0F);
            } else if (str.endsWith("b")) {
               double val = Double.parseDouble(str.substring(0, str.length() - 1));
               return (long)(val * (double)1.0E9F);
            } else {
               return (long)Double.parseDouble(str);
            }
         } catch (Exception var4) {
            return -1L;
         }
      } else {
         return 0L;
      }
   }

   public static void checkResourcePackStartup() {
      CompletableFuture.runAsync(() -> {
         try {
            BomboConfig.Settings s = BomboConfig.get();
            if (s == null || !s.bypassResourcePack) return;
            File packsDir = new File(Minecraft.getInstance().gameDirectory, "resourcepacks");
            if (!packsDir.exists()) packsDir.mkdirs();
            File packFile = new File(packsDir, "Hypixel_Skyblock.zip");

            String apiUrl = me.bombo.bomboaddons.util.BomboApiUrl.getApiUrl("/resourcepack");
            try {
               HttpClient client = HttpClient.newBuilder()
                  .followRedirects(HttpClient.Redirect.ALWAYS)
                  .connectTimeout(Duration.ofSeconds(10L))
                  .build();
               HttpRequest req = HttpRequest.newBuilder()
                  .uri(URI.create(apiUrl))
                  .timeout(Duration.ofSeconds(15L))
                  .header("User-Agent", "BomboAddons/1.0")
                  .GET()
                  .build();
               HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
               if (resp.statusCode() == 200) {
                  String jsonBody = resp.body();
                  String downloadUrl = null;
                  try {
                     com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(jsonBody).getAsJsonObject();
                     if (obj.has("versions") && obj.get("versions").isJsonArray()) {
                        com.google.gson.JsonArray va = obj.getAsJsonArray("versions");
                        for (com.google.gson.JsonElement el : va) {
                           if (el.isJsonObject()) {
                              com.google.gson.JsonObject vo = el.getAsJsonObject();
                              if (vo.has("packFormat") && vo.get("packFormat").getAsInt() == 84) {
                                 if (vo.has("url") && !vo.get("url").isJsonNull()) {
                                    downloadUrl = vo.get("url").getAsString();
                                    break;
                                 }
                              }
                           }
                        }
                        if (downloadUrl == null && va.size() > 0) {
                           com.google.gson.JsonObject v0 = va.get(0).getAsJsonObject();
                           if (v0.has("url") && !v0.get("url").isJsonNull()) {
                              downloadUrl = v0.get("url").getAsString();
                           }
                        }
                     }
                     if (downloadUrl == null && obj.has("selectedVersion") && obj.get("selectedVersion").isJsonObject()) {
                        com.google.gson.JsonObject sv = obj.getAsJsonObject("selectedVersion");
                        if (sv.has("url") && !sv.get("url").isJsonNull()) {
                           downloadUrl = sv.get("url").getAsString();
                        }
                     }
                  } catch (Exception parseEx) {
                     if (jsonBody.startsWith("http://") || jsonBody.startsWith("https://")) {
                        downloadUrl = jsonBody.trim();
                     }
                  }

                  if (downloadUrl != null && !downloadUrl.isEmpty()) {
                     HttpRequest dlReq = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .timeout(Duration.ofSeconds(60L))
                        .header("User-Agent", "BomboAddons/1.0")
                        .GET()
                        .build();
                     HttpResponse<InputStream> dlResp = client.send(dlReq, HttpResponse.BodyHandlers.ofInputStream());
                     if (dlResp.statusCode() == 200) {
                        try (InputStream is = dlResp.body()) {
                           Files.copy(is, packFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                           System.out.println("[Bombo] Downloaded Skyblock texture pack from " + downloadUrl);
                        }
                     }
                  }
               }
            } catch (Exception ignored) {
            }

            if (packFile.exists()) {
               me.bombo.bomboaddons.util.ResourcePackHelper.enableSkyblockPack();
            }
         } catch (Throwable t) {
            t.printStackTrace();
         }
      });
   }

   public static LiteralArgumentBuilder<FabricClientCommandSource> createBlockHighlightCommand(String name) {
      LiteralArgumentBuilder<FabricClientCommandSource> cmd = ClientCommands.literal(name);
      cmd.then(ClientCommands.literal("add").then(ClientCommands.argument("block", StringArgumentType.string()).executes((ctx) -> {
         String block = StringArgumentType.getString(ctx, "block").toLowerCase().trim();
         BomboConfig.get().blockHighlights.put(block, new BomboConfig.BlockHighlightInfo("GOLD", false));
         BomboConfig.get().blockHighlightsEnabled = true;
         BomboConfig.save();
         BlockHighlight.highlightedBlocks.clear();
         ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §aAdded block highlight for: §e" + block + " §7(Color: GOLD)"));
         return 1;
      }).then(ClientCommands.argument("color", StringArgumentType.string()).executes((ctx) -> {
         String block = StringArgumentType.getString(ctx, "block").toLowerCase().trim();
         String color = StringArgumentType.getString(ctx, "color").toUpperCase().trim();
         BomboConfig.get().blockHighlights.put(block, new BomboConfig.BlockHighlightInfo(color, false));
         BomboConfig.get().blockHighlightsEnabled = true;
         BomboConfig.save();
         BlockHighlight.highlightedBlocks.clear();
         ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §aAdded block highlight for: §e" + block + " §7(Color: " + color + ")"));
         return 1;
      }))));
      cmd.then(ClientCommands.literal("remove").then(ClientCommands.argument("block", StringArgumentType.greedyString()).executes((ctx) -> {
         String block = StringArgumentType.getString(ctx, "block").toLowerCase().trim();
         if (BomboConfig.get().blockHighlights.remove(block) != null) {
            BomboConfig.save();
            BlockHighlight.highlightedBlocks.clear();
            ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §aRemoved block highlight for: §e" + block));
         } else {
            ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §cNo block highlight found for: §e" + block));
         }
         return 1;
      })));
      cmd.then(ClientCommands.literal("clear").executes((ctx) -> {
         BomboConfig.get().blockHighlights.clear();
         BomboConfig.save();
         BlockHighlight.highlightedBlocks.clear();
         ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §aCleared all block highlights!"));
         return 1;
      }));
      cmd.then(ClientCommands.literal("toggle").executes((ctx) -> {
         BomboConfig.get().blockHighlightsEnabled = !BomboConfig.get().blockHighlightsEnabled;
         BomboConfig.save();
         BlockHighlight.highlightedBlocks.clear();
         ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §aBlock highlights " + (BomboConfig.get().blockHighlightsEnabled ? "enabled" : "disabled") + "!"));
         return 1;
      }));
      cmd.then(ClientCommands.literal("list").executes((ctx) -> {
         ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §6=== Block Highlights ==="));
         if (BomboConfig.get().blockHighlights.isEmpty()) {
            ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("  §7None"));
         } else {
            for (Map.Entry<String, BomboConfig.BlockHighlightInfo> entry : BomboConfig.get().blockHighlights.entrySet()) {
               ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("  §7• §e" + entry.getKey() + " §7(Color: " + entry.getValue().color + ")"));
            }
         }
         return 1;
      }));
      cmd.executes((ctx) -> {
         ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§3Bombo§8] §7Usage: /b bh add <block> [color], /b bh remove <block>, /b bh list, /b bh toggle, /b bh clear"));
         return 1;
      });
      return cmd;
   }

   public static class PendingCommand {
      public final String command;
      public final long triggerTime;

      public PendingCommand(String command, long triggerTime) {
         this.command = command;
         this.triggerTime = triggerTime;
      }
   }
}
