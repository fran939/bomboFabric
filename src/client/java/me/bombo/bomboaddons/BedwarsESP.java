package me.bombo.bomboaddons;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public class BedwarsESP {
   public static final Map<UUID, Integer> PLAYER_COLORS = new ConcurrentHashMap();
   private static long lastUpdateMs = 0L;

   public static boolean isInBedwars() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return false;
      } else if (SkyblockUtils.isConnectedToHypixel() && "BEDWARS".equals(BomboaddonsClient.locrawGametype)) {
         return true;
      } else {
         Scoreboard scoreboard = mc.level.getScoreboard();
         Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
         if (sidebar != null) {
            String title = ChatFormatting.stripFormatting(sidebar.getDisplayName().getString()).toLowerCase();
            if (title.contains("bed wars") || title.contains("bedwars")) {
               return true;
            }
         }

         return false;
      }
   }

   public static void tick() {
      long now = System.currentTimeMillis();
      if (now - lastUpdateMs >= 500L) {
         lastUpdateMs = now;
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null && mc.getConnection() != null) {
            if (!BomboConfig.get().bedwarsEsp) {
               PLAYER_COLORS.clear();
            } else if (!isInBedwars()) {
               PLAYER_COLORS.clear();
            } else {
               for(PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                  if (info.getProfile() != null && info.getProfile().id() != null) {
                     UUID uuid = info.getProfile().id();
                     Component displayName = info.getTabListDisplayName();
                     if (displayName != null) {
                        Integer color = getTeamColorFromComponent(displayName, info.getProfile().name());
                        if (color != null) {
                           PLAYER_COLORS.put(uuid, color);
                        }
                     }
                  }
               }

            }
         } else {
            PLAYER_COLORS.clear();
         }
      }
   }

   public static Integer getTeamColorFromComponent(Component component, String username) {
      if (component == null) {
         return null;
      } else {
         AtomicReference<Integer> parsedColor = new AtomicReference((Object)null);
         component.visit((style, text) -> {
            if (text != null && text.toLowerCase().contains(username.toLowerCase())) {
               TextColor textColor = style.getColor();
               if (textColor != null) {
                  parsedColor.set(textColor.getValue());
                  return Optional.of(textColor.getValue());
               }
            }

            return Optional.empty();
         }, Style.EMPTY);
         if (parsedColor.get() != null) {
            return (Integer)parsedColor.get();
         } else {
            component.visit((style, text) -> {
               if (text != null && !text.trim().isEmpty()) {
                  TextColor textColor = style.getColor();
                  if (textColor != null) {
                     parsedColor.set(textColor.getValue());
                     return Optional.of(textColor.getValue());
                  }
               }

               return Optional.empty();
            }, Style.EMPTY);
            return (Integer)parsedColor.get();
         }
      }
   }

   public static boolean isOnOwnTeam(Player player) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return false;
      } else if (player == mc.player) {
         return true;
      } else if (mc.player.getTeam() != null && player.getTeam() != null && mc.player.getTeam().isAlliedTo(player.getTeam())) {
         return true;
      } else {
         Integer ownColor = (Integer)PLAYER_COLORS.get(mc.player.getUUID());
         Integer playerColor = (Integer)PLAYER_COLORS.get(player.getUUID());
         return ownColor != null && playerColor != null && ownColor.equals(playerColor);
      }
   }

   public static boolean shouldGlowEntity(Entity entity) {
      BomboConfig.Settings config = BomboConfig.get();
      if (!config.bedwarsEsp) {
         return false;
      } else if (entity instanceof Player) {
         Player player = (Player)entity;
         Minecraft mc = Minecraft.getInstance();
         if (player == mc.player) {
            return false;
         } else if (!isInBedwars()) {
            return false;
         } else {
            boolean isOwnTeam = isOnOwnTeam(player);
            if (isOwnTeam && !config.bedwarsEspOwnTeam) {
               return false;
            } else {
               return !config.hideCheats || mc.player == null || mc.player.hasLineOfSight(player);
            }
         }
      } else {
         return false;
      }
   }

   public static Integer getEntityColor(Entity entity) {
      BomboConfig.Settings config = BomboConfig.get();
      if (!config.bedwarsEsp) {
         return null;
      } else if (entity instanceof Player) {
         Player player = (Player)entity;
         if (!isInBedwars()) {
            return null;
         } else {
            Integer color = (Integer)PLAYER_COLORS.get(player.getUUID());
            if (color != null) {
               return color;
            } else {
               if (player.getTeam() != null) {
                  ChatFormatting format = player.getTeam().getColor();
                  if (format != null) {
                     TextColor textColor = TextColor.fromLegacyFormat(format);
                     if (textColor != null) {
                        return textColor.getValue();
                     }
                  }
               }

               return null;
            }
         }
      } else {
         return null;
      }
   }
}
