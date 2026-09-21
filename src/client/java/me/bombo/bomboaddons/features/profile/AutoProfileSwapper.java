package me.bombo.bomboaddons.features.profile;

import java.util.List;
import java.util.Locale;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class AutoProfileSwapper {
   private static long lastCheckTime = 0L;
   private static long lastSwapTime = 0L;

   public static void onClientTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;

      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.autoSwapProfiles || s.autoProfileRules == null || s.autoProfileRules.isEmpty()) return;

      long now = System.currentTimeMillis();
      if (now - lastCheckTime < 500L) return; // Evaluate every 500ms
      lastCheckTime = now;

      // Rate limit swaps to at least 2 seconds apart
      if (now - lastSwapTime < 2000L) return;

      for (BomboConfig.ProfileAutoSwapRule rule : s.autoProfileRules) {
         if (rule == null || !rule.enabled) continue;
         if (rule.targetProfile == null || rule.targetProfile.trim().isEmpty()) continue;
         if (rule.targetProfile.equalsIgnoreCase(s.activeProfile)) continue;

         if (evaluateRule(mc, rule)) {
            performSwap(rule.targetProfile.trim());
            break;
         }
      }
   }

   public static void onChatMessage(String cleanText) {
      if (cleanText == null || cleanText.isEmpty()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;

      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.autoSwapProfiles || s.autoProfileRules == null || s.autoProfileRules.isEmpty()) return;

      long now = System.currentTimeMillis();
      if (now - lastSwapTime < 2000L) return;

      String cleanLower = cleanText.toLowerCase(Locale.ROOT);

      for (BomboConfig.ProfileAutoSwapRule rule : s.autoProfileRules) {
         if (rule == null || !rule.enabled) continue;
         if (rule.targetProfile == null || rule.targetProfile.trim().isEmpty()) continue;
         if (rule.targetProfile.equalsIgnoreCase(s.activeProfile)) continue;
         if (rule.chatMessage == null || rule.chatMessage.trim().isEmpty()) continue;

         String trigger = rule.chatMessage.trim().toLowerCase(Locale.ROOT);
         if (cleanLower.contains(trigger)) {
            // Also evaluate other conditions if specified
            if (evaluateRule(mc, rule)) {
               performSwap(rule.targetProfile.trim());
               break;
            }
         }
      }
   }

   private static boolean evaluateRule(Minecraft mc, BomboConfig.ProfileAutoSwapRule rule) {
      // 1. Island / Area match
      if (rule.island != null && !rule.island.trim().isEmpty()) {
         if (!matchesFilter(rule.island.trim(), getCurrentArea())) {
            return false;
         }
      }

      // 2. Subarea match
      if (rule.subarea != null && !rule.subarea.trim().isEmpty()) {
         String currentSub = SkyblockUtils.getSubArea();
         if (currentSub == null) currentSub = "";
         if (!matchesFilter(rule.subarea.trim(), currentSub)) {
            return false;
         }
      }

      // 3. Dungeon Class match
      if (rule.dungeonClass != null && !rule.dungeonClass.trim().isEmpty()) {
         String currentClass = getDetectedDungeonClass();
         if (currentClass == null) currentClass = "";
         if (!matchesFilter(rule.dungeonClass.trim(), currentClass)) {
            return false;
         }
      }

      // 4. Armor match (supports "necron", "!necron", "wither goggles, !storm")
      if (rule.armorRequirement != null && !rule.armorRequirement.trim().isEmpty()) {
         if (!matchesArmorRequirement(mc, rule.armorRequirement.trim())) {
            return false;
         }
      }

      return true;
   }

   private static boolean matchesFilter(String filterExpression, String currentValue) {
      String cur = currentValue != null ? currentValue.toLowerCase(Locale.ROOT).trim() : "";
      String[] tokens = filterExpression.split(",");
      for (String token : tokens) {
         String t = token.trim().toLowerCase(Locale.ROOT);
         if (t.isEmpty()) continue;

         boolean negated = t.startsWith("!");
         String target = negated ? t.substring(1).trim() : t;

         boolean matches = !target.isEmpty() && cur.contains(target);
         if (negated) {
            if (matches) return false; // Contains forbidden term
         } else {
            if (!matches) return false; // Missing required term
         }
      }
      return true;
   }

   private static boolean matchesArmorRequirement(Minecraft mc, String armorExpression) {
      if (mc.player == null) return false;

      // Extract all current equipped armor names and IDs
      StringBuilder armorSb = new StringBuilder();
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = mc.player.getItemBySlot(slot);
         if (stack != null && !stack.isEmpty()) {
            armorSb.append(" ").append(stack.getHoverName().getString());
            String sbId = SkyblockUtils.getInternalIdRaw(stack);
            if (sbId != null) armorSb.append(" ").append(sbId);
         }
      }
      String equippedArmor = armorSb.toString().toLowerCase(Locale.ROOT);

      String[] tokens = armorExpression.split(",");
      for (String token : tokens) {
         String t = token.trim().toLowerCase(Locale.ROOT);
         if (t.isEmpty()) continue;

         boolean negated = t.startsWith("!");
         String target = negated ? t.substring(1).trim() : t;

         boolean found = !target.isEmpty() && equippedArmor.contains(target);
         if (negated) {
            if (found) return false; // Wears forbidden armor piece
         } else {
            if (!found) return false; // Missing required armor piece
         }
      }
      return true;
   }

   public static String getCurrentArea() {
      String loc = SkyblockUtils.getLocation();
      if (loc != null && !loc.isEmpty()) return loc;
      if (BomboaddonsClient.currentArea != null) return BomboaddonsClient.currentArea;
      return "";
   }

   public static String getDetectedDungeonClass() {
      if (!SkyblockUtils.isInDungeon()) return "";
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return "";

      try {
         // Check player tab list lines for class name
         if (mc.getConnection() != null) {
            for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
               if (info.getTabListDisplayName() != null) {
                  String line = info.getTabListDisplayName().getString().toLowerCase(Locale.ROOT);
                  if (line.contains("mage") || line.contains("[mage]")) return "Mage";
                  if (line.contains("berserk") || line.contains("[berserk]")) return "Berserk";
                  if (line.contains("archer") || line.contains("[archer]")) return "Archer";
                  if (line.contains("tank") || line.contains("[tank]")) return "Tank";
                  if (line.contains("healer") || line.contains("[healer]")) return "Healer";
               }
            }
         }
      } catch (Throwable ignored) {}

      return "";
   }

   public static void performSwap(String targetProfile) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) return;
      s.activeProfile = targetProfile;
      BomboConfig.save();
      lastSwapTime = System.currentTimeMillis();

      if (s.autoSwapNotifyChat && Minecraft.getInstance().player != null) {
         Bomboaddons.sendMessage("§8[§6AutoProfile§8] §aSwapped active profile to: §e§l" + targetProfile);
      }

      if (s.autoSwapNotifyScreen && Minecraft.getInstance().gui != null) {
         Minecraft.getInstance().gui.hud.setTitle(Component.literal("§6§lProfile Swapped"));
         Minecraft.getInstance().gui.hud.setSubtitle(Component.literal("§aActive: §e" + targetProfile));
      }

      if (s.autoSwapNotifySound) {
         try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
         } catch (Throwable ignored) {}
      }
   }
}
