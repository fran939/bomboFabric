package me.bombo.bomboaddons.cheat.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.util.BomboApiUrl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoExperiments {
   public enum ExperimentTier {
      BEGINNER(5, 5),
      LOW(5, 5),
      MEDIUM(7, 5),
      HIGH(9, 7),
      GRAND(9, 7),
      SUPREME(9, 7),
      TRANSCENDENT(9, 7),
      METAPHYSICAL(12, 9);

      public final int chronoTarget;
      public final int ultraTarget;

      ExperimentTier(int chronoTarget, int ultraTarget) {
         this.chronoTarget = chronoTarget;
         this.ultraTarget = ultraTarget;
      }
   }

   private static String lastDetectedTitle = "";
   private static ExperimentTier lastDetectedTier = ExperimentTier.METAPHYSICAL;
   private static final HashMap<Integer, Integer> ultrasequencerOrder = new HashMap<>();
   private static final List<Integer> chronomatronOrder = new ArrayList<>(28);
   private static long lastClickTime = 0L;
   private static int lastGlowingSlot = -1;
   private static boolean noteInProgress = false;
   private static boolean hasAdded = false;
   private static boolean wasInInputPhase = false;
   private static boolean wasInShowingPhase = false;
   private static int lastAdded = -1;
   private static String debugStr = "None";
   private static int clicks = 0;
   private static final Random random = new Random();
   private static long tickCount = 0L;
   private static long lastDebugTime = 0L;
   private static long superpairsDoneTime = 0L;

   public static void closeScreenSafely(Minecraft mc) {
      mc.execute(() -> {
         if (mc.player != null) {
            mc.player.closeContainer();
         }
         mc.setScreenAndShow((Screen) null);
      });
   }
   private static String lastInventoryHash = "";

   // API Metaphysical Serum tracking
   private static int cachedSerumsDrank = -1;
   private static long lastSerumFetchTime = 0L;
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
         .connectTimeout(Duration.ofSeconds(5))
         .build();

   public static String getDebugStr() {
      return debugStr;
   }

   public static String getDetectedTitle() {
      return lastDetectedTitle;
   }

   public static ExperimentTier getLastDetectedTier() {
      return lastDetectedTier;
   }

   public static int getEffectiveSerumCount() {
      if (cachedSerumsDrank >= 0) {
         return Math.max(0, Math.min(3, cachedSerumsDrank));
      }
      return Math.max(0, Math.min(3, BomboConfig.get().experimentSerumCount));
   }

   public static void fetchPlayerSerumsAsync(String username) {
      if (username == null || username.trim().isEmpty() || username.equalsIgnoreCase("Player")) return;
      long now = System.currentTimeMillis();
      if (now - lastSerumFetchTime < 60_000L) return; // 1 min cache
      lastSerumFetchTime = now;

      CompletableFuture.runAsync(() -> {
         try {
            String url = BomboApiUrl.api("/data/" + URLEncoder.encode(username.trim(), StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                  .uri(URI.create(url))
                  .header("Accept", "application/json")
                  .timeout(Duration.ofSeconds(10))
                  .GET()
                  .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isEmpty()) {
               JsonElement el = JsonParser.parseString(resp.body());
               int serums = parseSerumsFromProfiles(el);
               if (serums >= 0) {
                  cachedSerumsDrank = Math.max(0, Math.min(3, serums));
                  BomboConfig.get().experimentSerumCount = cachedSerumsDrank;
               }
            }
         } catch (Throwable ignored) {}
      });
   }

   private static int parseSerumsFromProfiles(JsonElement el) {
      if (el == null) return -1;
      JsonArray profiles = null;
      if (el.isJsonObject()) {
         JsonObject obj = el.getAsJsonObject();
         if (obj.has("all_profiles") && obj.get("all_profiles").isJsonArray()) {
            profiles = obj.getAsJsonArray("all_profiles");
         } else if (obj.has("profiles") && obj.get("profiles").isJsonArray()) {
            profiles = obj.getAsJsonArray("profiles");
         } else if (obj.has("members") && obj.get("members").isJsonObject()) {
            return extractSerumsFromMembers(obj.getAsJsonObject("members"));
         }
      } else if (el.isJsonArray()) {
         profiles = el.getAsJsonArray();
      }

      if (profiles != null) {
         // 1. Look for selected/active profile first
         for (JsonElement pEl : profiles) {
            if (pEl.isJsonObject()) {
               JsonObject pObj = pEl.getAsJsonObject();
               boolean isSelected = (pObj.has("selected") && pObj.get("selected").getAsBoolean())
                     || (pObj.has("current") && pObj.get("current").getAsBoolean());
               if (isSelected && pObj.has("members") && pObj.get("members").isJsonObject()) {
                  int s = extractSerumsFromMembers(pObj.getAsJsonObject("members"));
                  if (s >= 0) return s;
               }
            }
         }

         // 2. Fallback: highest serum count across any profile
         int maxFound = -1;
         for (JsonElement pEl : profiles) {
            if (pEl.isJsonObject()) {
               JsonObject pObj = pEl.getAsJsonObject();
               if (pObj.has("members") && pObj.get("members").isJsonObject()) {
                  int s = extractSerumsFromMembers(pObj.getAsJsonObject("members"));
                  if (s > maxFound) maxFound = s;
               }
            }
         }
         return maxFound;
      }
      return -1;
   }

   private static int extractSerumsFromMembers(JsonObject members) {
      if (members == null) return -1;
      Minecraft mc = Minecraft.getInstance();
      String playerUuid = mc.player != null ? mc.player.getStringUUID().replace("-", "").toLowerCase() : null;

      if (playerUuid != null && members.has(playerUuid)) {
         JsonElement mEl = members.get(playerUuid);
         if (mEl.isJsonObject() && mEl.getAsJsonObject().has("experimentation")) {
            JsonObject exp = mEl.getAsJsonObject().getAsJsonObject("experimentation");
            if (exp.has("serums_drank") && exp.get("serums_drank").isJsonPrimitive()) {
               return exp.get("serums_drank").getAsInt();
            }
         }
      }

      for (Map.Entry<String, JsonElement> entry : members.entrySet()) {
         if (entry.getValue().isJsonObject()) {
            JsonObject m = entry.getValue().getAsJsonObject();
            if (m.has("experimentation") && m.get("experimentation").isJsonObject()) {
               JsonObject exp = m.getAsJsonObject("experimentation");
               if (exp.has("serums_drank") && exp.get("serums_drank").isJsonPrimitive()) {
                  return exp.get("serums_drank").getAsInt();
               }
            }
         }
      }
      return -1;
   }

   public static ExperimentTier detectTier(String cleanTitle, AbstractContainerScreen<?> screen, boolean isChrono) {
      if (cleanTitle != null) {
         String lower = cleanTitle.toLowerCase();
         if (lower.contains("metaphysical")) return ExperimentTier.METAPHYSICAL;
         if (lower.contains("transcendent")) return ExperimentTier.TRANSCENDENT;
         if (lower.contains("supreme")) return ExperimentTier.SUPREME;
         if (lower.contains("grand")) return ExperimentTier.GRAND;
         if (lower.contains("high")) return ExperimentTier.HIGH;
         if (lower.contains("medium")) return ExperimentTier.MEDIUM;
         if (lower.contains("low")) return ExperimentTier.LOW;
         if (lower.contains("beginner")) return ExperimentTier.BEGINNER;
      }

      // Analyze board note count if available
      if (screen != null && screen.getMenu() != null) {
         List<Slot> slots = screen.getMenu().slots;
         Minecraft mc = Minecraft.getInstance();
         if (isChrono) {
            int noteCount = 0;
            for (int i = 0; i < 54 && i < slots.size(); i++) {
               int row = i / 9;
               int col = i % 9;
               if (row >= 1 && row <= 4 && col >= 1 && col <= 7) {
                  Slot s = slots.get(i);
                  if (s.container != mc.player.getInventory() && isTrueNoteBlock(s.getItem())) {
                     noteCount++;
                  }
               }
            }
            if (noteCount >= 10) return ExperimentTier.METAPHYSICAL;
            if (noteCount >= 8) return ExperimentTier.TRANSCENDENT;
            if (noteCount >= 6) return ExperimentTier.SUPREME;
            if (noteCount == 5) return ExperimentTier.GRAND;
            if (noteCount >= 3) return ExperimentTier.HIGH;
            if (noteCount == 2) return ExperimentTier.MEDIUM;
            if (noteCount == 1) return ExperimentTier.LOW;
         }
      }

      return lastDetectedTier != null ? lastDetectedTier : ExperimentTier.METAPHYSICAL;
   }

   public static int getTargetRound(boolean isChrono, ExperimentTier tier) {
      BomboConfig.Settings config = BomboConfig.get();
      if (config.experimentGetMaxXp) {
         return isChrono ? 15 : 20;
      }
      int serums = getEffectiveSerumCount();
      int base = isChrono ? tier.chronoTarget : tier.ultraTarget;
      return Math.max(2, base - serums);
   }

   public static void reset() {
      ultrasequencerOrder.clear();
      chronomatronOrder.clear();
      hasAdded = false;
      wasInShowingPhase = false;
      wasInInputPhase = false;
      lastAdded = -1;
      lastGlowingSlot = -1;
      noteInProgress = false;
      clicks = 0;
   }

   public static void onTick() {
      ++tickCount;
      Minecraft mc = Minecraft.getInstance();
      BomboConfig.Settings config = BomboConfig.get();
      if (mc.player != null && !config.hideCheats && config.autoExperiments) {
         String title = mc.gui.screen() != null ? mc.gui.screen().getTitle().getString().trim() : "";
         if (mc.gui.screen() == null) {
            if (!lastDetectedTitle.isEmpty()) {
               lastDetectedTitle = "";
               reset();
            }

            long var10000 = tickCount % 20L;
            debugStr = "Waiting for GUI... " + (var10000 < 10L ? "|" : "-");
         } else {
            String cleanTitle = title.replaceAll("(?i)§[0-9a-fk-or]", "");
            if (!cleanTitle.equals(lastDetectedTitle)) {
               if (lastDetectedTitle.contains("Chronomatron") != cleanTitle.contains("Chronomatron")
                     || lastDetectedTitle.contains("Ultrasequencer") != cleanTitle.contains("Ultrasequencer")
                     || cleanTitle.contains("Stakes") || lastDetectedTitle.contains("Stakes")
                     || cleanTitle.contains("Experiment Over")) {
                  reset();
               }

               lastDetectedTitle = cleanTitle;

               // Update tier if stakes/tier name present in title
               String lower = cleanTitle.toLowerCase();
               if (lower.contains("metaphysical")) lastDetectedTier = ExperimentTier.METAPHYSICAL;
               else if (lower.contains("transcendent")) lastDetectedTier = ExperimentTier.TRANSCENDENT;
               else if (lower.contains("supreme")) lastDetectedTier = ExperimentTier.SUPREME;
               else if (lower.contains("grand")) lastDetectedTier = ExperimentTier.GRAND;
               else if (lower.contains("high")) lastDetectedTier = ExperimentTier.HIGH;
               else if (lower.contains("medium")) lastDetectedTier = ExperimentTier.MEDIUM;
               else if (lower.contains("low")) lastDetectedTier = ExperimentTier.LOW;
               else if (lower.contains("beginner")) lastDetectedTier = ExperimentTier.BEGINNER;
            }

            // Sync serums from API whenever an experiment GUI or table is open
            if (cleanTitle.contains("Chronomatron") || cleanTitle.contains("Ultrasequencer")
                  || cleanTitle.contains("Superpairs") || cleanTitle.contains("Table") || cleanTitle.contains("Stakes")) {
               if (mc.getUser() != null) {
                  fetchPlayerSerumsAsync(mc.getUser().getName());
               }
            }

            Screen screen = mc.gui.screen();
            if (screen instanceof AbstractContainerScreen && !cleanTitle.contains("Stakes") && !cleanTitle.contains("Table")) {
               AbstractContainerScreen<?> acs = (AbstractContainerScreen)screen;
               int timerSlot = findTimerSlot(acs.getMenu().slots);
               if (System.currentTimeMillis() - lastDebugTime > 1000L) {
                  String timerInfo = timerSlot != -1 ? ((Slot)acs.getMenu().slots.get(timerSlot)).getItem().getItem().toString() : "NONE";
                  debugStr = String.format("Timer: %s | Mode: %s | Tier: %s | Serums: %d",
                        timerInfo, cleanTitle.contains("Chron") ? "Chrono" : "Ultra", lastDetectedTier.name(), getEffectiveSerumCount());
                  lastDebugTime = System.currentTimeMillis();
               }

               if (cleanTitle.contains("Chronomatron")) {
                  solveChronomatron(mc, acs);
               } else if (cleanTitle.contains("Ultrasequencer")) {
                  solveUltraSequencer(mc, acs);
               } else if (cleanTitle.contains("Superpairs")) {
                  checkSuperpairsAutoClose(mc, acs);
               }
            } else if (cleanTitle.contains("Stakes") || cleanTitle.contains("Table")) {
               debugStr = "In Menu (Ignored)";
            }
         }
      }
   }

   private static String getInventoryHash(List<Slot> slots) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < 54 && i < slots.size(); ++i) {
         ItemStack stack = ((Slot)slots.get(i)).getItem();
         sb.append(stack.getItem().toString()).append(stack.getCount()).append(hasGlint(stack));
      }

      return sb.toString();
   }

   private static boolean hasGlint(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.hasFoil() || stack.getComponents().has(DataComponents.ENCHANTMENTS) || stack.getComponents().has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
      }
   }

   private static void solveChronomatron(Minecraft mc, AbstractContainerScreen<?> screen) {
      BomboConfig.Settings config = BomboConfig.get();
      List<Slot> slots = screen.getMenu().slots;
      int timerSlot = findTimerSlot(slots);
      if (timerSlot != -1) {
         ItemStack timerStack = ((Slot)slots.get(timerSlot)).getItem();
         boolean isInputPhase = isClock(timerStack);
         boolean isShowingPhase = isGlowstone(timerStack) || isGlowItem(timerStack);
         boolean terracottaFound = false;
         int currentFlashedSlot = -1;

         for (int i = 0; i < slots.size() && i < 54; ++i) {
            Slot slot = (Slot)slots.get(i);
            if (slot.container != mc.player.getInventory() && i != timerSlot) {
               ItemStack stack = slot.getItem();
               String currentItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
               boolean isFlashMaterial = currentItem.contains("terracotta") || currentItem.contains("wool") || currentItem.contains("concrete") || currentItem.contains("glowstone");
               if (isFlashMaterial && isNoteItem(stack)) {
                  terracottaFound = true;
                  currentFlashedSlot = i;
                  break;
               }
            }
         }

         if (terracottaFound) {
            if (!noteInProgress) {
               noteInProgress = true;
               lastGlowingSlot = currentFlashedSlot;
               if (config.debugMode) {
                  mc.player.sendSystemMessage(Component.literal("§7[Bombo] Chrono: Flash detected at slot " + currentFlashedSlot));
               }
            }
         } else {
            noteInProgress = false;
         }

         if (isInputPhase && !wasInInputPhase) {
            if (lastGlowingSlot != -1) {
               chronomatronOrder.add(lastGlowingSlot);
               if (config.debugMode) {
                  LocalPlayer player = mc.player;
                  int orderNum = chronomatronOrder.size();
                  player.sendSystemMessage(Component.literal("§a[Bombo] Chrono: Note #" + orderNum + " captured: " + lastGlowingSlot));
               }

               lastGlowingSlot = -1;
            } else if (config.debugMode) {
               mc.player.sendSystemMessage(Component.literal("§c[Bombo] Chrono: No note captured this round!"));
            }

            clicks = 0;
            hasAdded = true;
            noteInProgress = false;
         }

         wasInInputPhase = isInputPhase;
         debugStr = String.format("Chrono | Phase: %s | Clicks: %d/%d | Glow: %d | Tier: %s",
               isShowingPhase ? "SHOWING" : (isInputPhase ? "INPUT" : "WAIT"),
               clicks, chronomatronOrder.size(), lastGlowingSlot, lastDetectedTier.name());

         if (isInputPhase && hasAdded && chronomatronOrder.size() > clicks) {
            long delay = (long)(config.experimentClickDelay + (random.nextInt(41) - 20));
            if (delay < 0L) {
               delay = 0L;
            }

            if (System.currentTimeMillis() - lastClickTime > delay) {
               int slotIndex = (Integer)chronomatronOrder.get(clicks);
               clickSlot(mc, screen, slotIndex);
               if (config.debugMode) {
                  int clickNum = clicks + 1;
                  mc.player.sendSystemMessage(Component.literal("§b[Bombo] Chrono Click #" + clickNum + " (Slot " + slotIndex + ")"));
               }

               lastClickTime = System.currentTimeMillis();
               ++clicks;
               if (clicks >= chronomatronOrder.size()) {
                  hasAdded = false;
                  if (config.debugMode) {
                     mc.player.sendSystemMessage(Component.literal("§7[Bombo] Chrono: Round finished, waiting for next note..."));
                  }

                  ExperimentTier tier = detectTier(lastDetectedTitle, screen, true);
                  lastDetectedTier = tier;
                  int targetRound = getTargetRound(true, tier);
                  int serums = getEffectiveSerumCount();

                  if (config.experimentAutoClose && chronomatronOrder.size() >= targetRound && chronomatronOrder.size() > 2) {
                     mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §aAuto Experiments: Target chain reached (§e"
                           + targetRound + "§a) for §d" + tier.name() + " §7(Serums: §e" + serums + "§7)! Closing."));
                     closeScreenSafely(mc);
                  }
               }
            }
         }
      }
   }

   private static void solveUltraSequencer(Minecraft mc, AbstractContainerScreen<?> screen) {
      BomboConfig.Settings config = BomboConfig.get();
      List<Slot> slots = screen.getMenu().slots;
      int timerSlot = findTimerSlot(slots);
      if (timerSlot != -1) {
         ItemStack timerStack = ((Slot)slots.get(timerSlot)).getItem();
         boolean isInputPhase = isClock(timerStack);
         boolean isShowingPhase = isGlowstone(timerStack) || isGlowItem(timerStack);
         debugStr = String.format("Ultra | Added: %s | Clicks: %d/%d | Timer: %s | Tier: %s",
               hasAdded, clicks, ultrasequencerOrder.size(), timerStack.getItem().toString(), lastDetectedTier.name());

         if (isShowingPhase) {
            if (!wasInShowingPhase) {
               ultrasequencerOrder.clear();
               if (config.debugMode) {
                  mc.player.sendSystemMessage(Component.literal("§7[Bombo] Ultra: Showing phase started, clearing map."));
               }
            }

            for (int i = 0; i < slots.size() && i < 54; ++i) {
               Slot slot = (Slot)slots.get(i);
               if (slot.container != mc.player.getInventory() && i != timerSlot) {
                  ItemStack stack = slot.getItem();
                  if (isNoteItem(stack)) {
                     int order = stack.getCount();
                     if (order > 0 && !ultrasequencerOrder.containsKey(order - 1)) {
                        ultrasequencerOrder.put(order - 1, i);
                        if (config.debugMode) {
                           mc.player.sendSystemMessage(Component.literal("§a[Bombo] Ultra: Captured dye #" + order + " at slot " + i));
                        }
                     }
                  }
               }
            }

            if (!ultrasequencerOrder.isEmpty()) {
               hasAdded = true;
               clicks = 0;
            }
         }

         wasInShowingPhase = isShowingPhase;
         if (isInputPhase && hasAdded && !ultrasequencerOrder.isEmpty() && ultrasequencerOrder.size() > clicks) {
            long delay = (long)(config.experimentClickDelay + (random.nextInt(41) - 20));
            if (delay < 0L) {
               delay = 0L;
            }

            if (System.currentTimeMillis() - lastClickTime > delay) {
               if (ultrasequencerOrder.containsKey(clicks)) {
                  int slotIndex = (Integer)ultrasequencerOrder.get(clicks);
                  clickSlot(mc, screen, slotIndex);
                  if (config.debugMode) {
                     int clickNum = clicks + 1;
                     mc.player.sendSystemMessage(Component.literal("§b[Bombo] Ultra Click #" + clickNum + " (Slot " + slotIndex + ")"));
                  }

                  lastClickTime = System.currentTimeMillis();
                  ++clicks;
                  if (clicks >= ultrasequencerOrder.size()) {
                     ExperimentTier tier = detectTier(lastDetectedTitle, screen, false);
                     lastDetectedTier = tier;
                     int targetRound = getTargetRound(false, tier);
                     int serums = getEffectiveSerumCount();

                     if (config.experimentAutoClose && ultrasequencerOrder.size() >= targetRound && ultrasequencerOrder.size() > 1) {
                        mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §aAuto Experiments: Target series reached (§e"
                              + targetRound + "§a) for §d" + tier.name() + " §7(Serums: §e" + serums + "§7)! Closing."));
                        closeScreenSafely(mc);
                     }
                  }
               } else {
                  ++clicks;
               }
            }
         }
      }
   }

   private static int findTimerSlot(List<Slot> slots) {
      Minecraft mc = Minecraft.getInstance();
      if (slots.size() > 49) {
         Slot s49 = (Slot)slots.get(49);
         if (s49.container != mc.player.getInventory()) {
            ItemStack item = s49.getItem();
            if (isClock(item) || isGlowstone(item) || isGlowItem(item)) {
               return 49;
            }
         }
      }

      for (int i = 0; i < slots.size() && i < 54; ++i) {
         Slot slot = (Slot)slots.get(i);
         if (slot.container != mc.player.getInventory()) {
            ItemStack stack = slot.getItem();
            if (isClock(stack) || isGlowstone(stack) || isGlowItem(stack)) {
               return i;
            }
         }
      }

      return -1;
   }

   private static void clickSlot(Minecraft mc, AbstractContainerScreen<?> screen, int slotIndex) {
      if (mc.gameMode != null && mc.player != null) {
         BomboConfig.Settings s = BomboConfig.get();
         int button = 0;
         ContainerInput type = ContainerInput.PICKUP;
         switch (s.experimentClickType) {
            case 1:
               button = 2;
               type = ContainerInput.CLONE;
               break;
            case 2:
               button = 0;
               type = ContainerInput.QUICK_MOVE;
         }

         mc.gameMode.handleContainerInput(screen.getMenu().containerId, slotIndex, button, type, mc.player);
      }
   }

   private static boolean isGlowstone(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.is(Items.GLOWSTONE_DUST) || stack.is(Items.GLOWSTONE);
      }
   }

   private static boolean isGlowItem(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.is(Items.SEA_LANTERN) || stack.is(Items.GLOW_INK_SAC) || stack.is(Items.GLOW_ITEM_FRAME)
               || stack.is(Items.BEACON) || stack.is(Items.MAGMA_BLOCK) || stack.is(Items.COMMAND_BLOCK)
               || stack.is(Items.REPEATING_COMMAND_BLOCK);
      }
   }

   private static boolean isClock(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.is(Items.CLOCK) || stack.is(Items.PLAYER_HEAD) || stack.is(Items.MAP) || stack.is(Items.FILLED_MAP);
      }
   }

   private static boolean isTrueNoteBlock(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return false;
      String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
      if (path.contains("glass") || path.contains("dust") || path.contains("clock") || path.contains("head") || path.contains("star")) {
         return false;
      }
      return path.contains("terracotta") || path.contains("wool") || path.contains("concrete") || path.contains("dye")
            || path.equals("bone_meal") || path.equals("ink_sac") || path.equals("lapis_lazuli") || path.equals("cocoa_beans");
   }

   private static boolean isNoteItem(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
         if (path.contains("glass_pane")) {
            return false;
         } else {
            return path.contains("dye") || path.contains("stained_glass") || path.contains("skull")
                  || path.contains("head") || path.contains("star") || path.contains("dust")
                  || path.contains("terracotta") || path.contains("wool") || path.contains("concrete")
                  || path.equals("bone_meal") || path.equals("ink_sac") || path.equals("lapis_lazuli")
                  || path.equals("cocoa_beans") || path.equals("player_head");
         }
      }
   }

   private static boolean isDye(ItemStack stack) {
      return isNoteItem(stack);
   }
   private static void checkSuperpairsAutoClose(Minecraft mc, AbstractContainerScreen<?> screen) {
      BomboConfig.Settings config = BomboConfig.get();
      if (!config.experimentAutoClose) return;

      boolean isFinished = false;
      List<Slot> slots = screen.getMenu().slots;
      for (int i = 0; i < 54 && i < slots.size(); i++) {
         Slot s = slots.get(i);
         if (s.container != mc.player.getInventory()) {
            ItemStack stack = s.getItem();
            if (!stack.isEmpty()) {
               String name = stack.getHoverName().getString().toLowerCase();
               if (name.contains("remaining clicks: 0") || name.contains("clicks remaining: 0")
                     || name.contains("0 clicks left") || name.contains("0 clicks remaining")
                     || name.contains("game over") || name.contains("experiment over") || name.contains("no clicks left")) {
                  isFinished = true;
                  break;
               }
               net.minecraft.world.item.component.ItemLore lore = stack.get(DataComponents.LORE);
               if (lore != null) {
                  for (Component line : lore.lines()) {
                     String lineStr = line.getString().toLowerCase();
                     if (lineStr.contains("remaining clicks: 0") || lineStr.contains("clicks remaining: 0")
                           || lineStr.contains("0 clicks left") || lineStr.contains("0 clicks remaining")
                           || lineStr.contains("game over") || lineStr.contains("experiment over") || lineStr.contains("no clicks left")) {
                        isFinished = true;
                        break;
                     }
                  }
               }
            }
         }
         if (isFinished) break;
      }

      if (isFinished) {
         if (superpairsDoneTime == 0L) {
            superpairsDoneTime = System.currentTimeMillis();
         } else if (System.currentTimeMillis() - superpairsDoneTime > 750L) {
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §aAuto Experiments: Superpairs completed! Closing."));
            closeScreenSafely(mc);
            superpairsDoneTime = 0L;
         }
      } else {
         superpairsDoneTime = 0L;
      }
   }
}
