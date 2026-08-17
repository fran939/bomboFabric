package me.bombo.bomboaddons.eggfinder;

import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.OrderedSubmitNodeCollector;
import me.bombo.bomboaddons.SBECommands;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EggFinder {
   private static final Logger LOGGER = LoggerFactory.getLogger("bomboaddons-eggfinder");
   private static final Pattern EGG_FOUND_PATTERN = Pattern.compile("(?:HOPPITY'S HUNT You found a Chocolate|You have already collected this Chocolate) (Breakfast|Lunch|Dinner|Brunch|D[eé]jeuner|Supper) Egg", 2);
   private static final Pattern NO_EGGS_PATTERN = Pattern.compile("There are no hidden Chocolate Rabbit Eggs nearby! Try again later!", 2);
   private static final Pattern RABBIT_FOUND_PATTERN = Pattern.compile("HOPPITY'S HUNT You found (.+) \\(([A-Z]+)\\)!", 2);
   private static final Set<String> VALID_LOCATIONS = Set.of("Backwater Bayou", "Crimson Isle", "Crystal Hollows", "Deep Caverns", "Dungeon Hub", "Dwarven Mines", "Galatea", "Gold Mine", "Hub", "Lotus Atoll", "Spider's Den", "The End", "The Farming Islands", "The Park");
   private static final List<EggWaypoint> activeWaypoints = new ArrayList();
   private static SkyblockTimeInfo lastTime = null;
   private static String lastLoc = null;

   public static void init() {
   }

   public static void clearEggs() {
      synchronized(activeWaypoints) {
         activeWaypoints.clear();
      }

      for(EggType type : EggFinder.EggType.values()) {
         type.collected = false;
      }

   }

   public static boolean hasWaypoints() {
      synchronized(activeWaypoints) {
         return !activeWaypoints.isEmpty();
      }
   }

   public static void clearWaypoints() {
      synchronized(activeWaypoints) {
         activeWaypoints.clear();
      }
   }

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         if (mc.player.tickCount % 20 != 0) return;
         String rawLoc = BomboaddonsClient.currentArea;
         String currentLoc = getSkyblockerLocationName(rawLoc);
         if (!Objects.equals(lastLoc, currentLoc)) {
            LOGGER.info("[EggFinder] Location changed from " + lastLoc + " to " + currentLoc);
            lastLoc = currentLoc;
            clearWaypoints();
            if (VALID_LOCATIONS.contains(currentLoc)) {
               EggWebSocket.updateSubscription(currentLoc);
            } else {
               EggWebSocket.updateSubscription((String)null);
            }
         }

         SkyblockTimeInfo time = new SkyblockTimeInfo(System.currentTimeMillis());
         if ((lastTime == null || lastTime.isSpring != time.isSpring) && !time.isSpring) {
            clearEggs();
         }

         if (lastTime != null && time.isSpring && lastTime.hour != time.hour) {
            int dayNumber = time.month * 31 + time.day;
            boolean isOdd = dayNumber % 2 == 1;

            for(EggType type : EggFinder.EggType.values()) {
               if (time.hour == type.resetHour && isOdd == type.oddDay) {
                  type.collected = false;
                  synchronized(activeWaypoints) {
                     activeWaypoints.removeIf((wp) -> wp.type == type);
                  }
               }
            }
         }

         lastTime = time;
      } else {
         if (lastLoc != null) {
            lastLoc = null;
            EggWebSocket.updateSubscription((String)null);
            clearWaypoints();
         }
      }
   }

   public static boolean onChatMessage(Component text, boolean overlay) {
      if (!overlay && BomboConfig.get().eggFinder) {
         String msg = text.getString().replaceAll("§.", "");
         Matcher matcher = NO_EGGS_PATTERN.matcher(msg);
         if (matcher.find()) {
            synchronized(activeWaypoints) {
               for(EggWaypoint wp : activeWaypoints) {
                  wp.collected = true;
               }

               activeWaypoints.clear();
            }

            for(EggType type : EggFinder.EggType.values()) {
               type.collected = true;
            }

            return true;
         } else {
            matcher.usePattern(EGG_FOUND_PATTERN);
            if (matcher.find()) {
               try {
                  String typeName = matcher.group(1);
                  EggType eggType = EggFinder.EggType.getTypeByName(typeName);
                  if (eggType == null) {
                     return true;
                  }

                  eggType.collected = true;
                  synchronized(activeWaypoints) {
                     activeWaypoints.removeIf((wpx) -> wpx.type == eggType);
                  }

                  LOGGER.info("Collected or found a Chocolate " + typeName + " Egg!");
                  Minecraft client = Minecraft.getInstance();
                  if (client.player == null || client.level == null) {
                     return true;
                  }

                  List<ArmorStand> entities = client.level.getEntitiesOfClass(ArmorStand.class, AABB.ofSize(client.player.position(), (double)8.0F, (double)8.0F, (double)8.0F), (entity) -> checkIfEgg(entity, eggType));
                  if (entities.isEmpty()) {
                     LOGGER.info("No egg armor stand found nearby.");
                     return true;
                  }

                  BlockPos eggPos = ((ArmorStand)entities.get(0)).blockPosition().above(2);
                  if (VALID_LOCATIONS.contains(lastLoc)) {
                     EggWebSocket.sendPublish(lastLoc, eggType.name, eggPos);
                  }

                  if (BomboConfig.get().eggFinderChat) {
                     LocalPlayer var10000 = client.player;
                     MutableComponent var10001 = Component.literal("§8[§bBomboAddons§8] §aYou found a ").append(Component.literal(eggType.name + " Egg").withStyle((style) -> style.withColor(eggType.chatColor)));
                     int var10002 = eggPos.getX();
                     var10000.sendSystemMessage(var10001.append(" §aat " + var10002 + ", " + eggPos.getY() + ", " + eggPos.getZ() + "!"));
                  }
               } catch (Exception e) {
                  LOGGER.error("Failed to process egg chat message: " + e.getMessage(), e);
               }

               return true;
            } else {
               matcher.usePattern(RABBIT_FOUND_PATTERN);
               if (matcher.find()) {
                  try {
                     Minecraft client = Minecraft.getInstance();
                     if (client.player != null) {
                        Vec3 playerPos = client.player.position();
                        EggWaypoint closestWp = null;
                        double closestDist = Double.MAX_VALUE;
                        synchronized(activeWaypoints) {
                           for(EggWaypoint wp : activeWaypoints) {
                              double dist = wp.pos.distToCenterSqr(playerPos.x, playerPos.y, playerPos.z);
                              if (dist < closestDist) {
                                 closestDist = dist;
                                 closestWp = wp;
                              }
                           }
                        }

                        if (closestWp != null && closestDist < (double)225.0F) {
                           EggType eggType = closestWp.type;
                           eggType.collected = true;
                           synchronized(activeWaypoints) {
                              activeWaypoints.removeIf((wpx) -> wpx.type == eggType);
                           }

                           LOGGER.info("Collected rabbit egg via proximity to " + eggType.name + " Egg!");
                        } else {
                           LOGGER.info("Found a rabbit but no egg waypoint was nearby.");
                        }
                     }
                  } catch (Exception e) {
                     LOGGER.error("Failed to process rabbit found message: " + e.getMessage(), e);
                  }
               }

               return true;
            }
         }
      } else {
         return true;
      }
   }

   public static void onWebsocketMessage(String eggTypeStr, BlockPos pos) {
      EggType eggType = EggFinder.EggType.getTypeByName(eggTypeStr);
      if (eggType != null) {
         synchronized(activeWaypoints) {
            activeWaypoints.removeIf((wp) -> wp.type == eggType);
            if (!eggType.collected) {
               activeWaypoints.add(new EggWaypoint(pos, eggType));
            }
         }

         if (BomboConfig.get().eggFinderChat && !eggType.collected) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
               Component eggName = Component.literal(eggType.name + " Egg").withStyle((style) -> style.withColor(eggType.chatColor));
               LocalPlayer var10000 = client.player;
               MutableComponent var10001 = Component.literal("§8[§bBomboAddons§8] §aNew ").append(eggName);
               int var10002 = pos.getX();
               var10000.sendSystemMessage(var10001.append(" §adiscovered at §e" + var10002 + ", " + pos.getY() + ", " + pos.getZ() + "!").withStyle((style) -> {
                  int var10003 = pos.getX();
                  return style.withClickEvent(new ClickEvent.RunCommand("/shnav " + var10003 + " " + pos.getY() + " " + pos.getZ())).withHoverEvent(SBECommands.createHoverEvent("§aClick to navigate to this egg!"));
               }));
            }
         }

      }
   }

   public static boolean checkIfEgg(ArmorStand armorStand, EggType eggType) {
      if (!armorStand.hasCustomName() && armorStand.isInvisible() && !armorStand.showBasePlate()) {
         ItemStack head = armorStand.getItemBySlot(EquipmentSlot.HEAD);
         if (head != null && head.is(Items.PLAYER_HEAD)) {
            ResolvableProfile profile = (ResolvableProfile)head.get(DataComponents.PROFILE);
            if (profile != null && profile.partialProfile() != null && profile.partialProfile().properties() != null) {
               for(Property prop : profile.partialProfile().properties().get("textures")) {
                  if (Objects.equals(prop.value(), eggType.texture)) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static void render(LevelRenderContext context) {
      if (BomboConfig.get().eggFinder) {
         SkyblockTimeInfo time = lastTime;
         if (time != null && time.isSpring) {
            List<EggWaypoint> wps;
            synchronized(activeWaypoints) {
               wps = new ArrayList(activeWaypoints);
            }

            if (!wps.isEmpty()) {
               Minecraft mc = Minecraft.getInstance();
               Vec3 camPos = mc.gameRenderer.getMainCamera().position();
               PoseStack poseStack = context.poseStack();
               OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());

               for(EggWaypoint wp : wps) {
                  double x = (double)wp.pos.getX() + (double)0.5F - camPos.x;
                  double y = (double)wp.pos.getY() + (double)0.5F - camPos.y;
                  double z = (double)wp.pos.getZ() + (double)0.5F - camPos.z;
                  double dist = wp.pos.distToCenterSqr(camPos.x, camPos.y, camPos.z);
                  float distance = (float)Math.sqrt(dist);
                  float scale = 1.0F;
                  if (BomboConfig.get().eggFinderThroughWalls && distance > 0.2F) {
                     scale = 0.2F / distance;
                  }

                  float boxWidth = 0.5F * scale;
                  float boxHeight = 0.5F * scale;
                  float scaledX = (float)x * scale;
                  float scaledY = (float)y * scale;
                  float scaledZ = (float)z * scale;
                  float r = (float)(wp.type.hexColor >> 16 & 255) / 255.0F;
                  float g = (float)(wp.type.hexColor >> 8 & 255) / 255.0F;
                  float b = (float)(wp.type.hexColor & 255) / 255.0F;
                  float a = 1.0F;
                  AABB box = new AABB((double)(scaledX - boxWidth), (double)(scaledY - boxHeight), (double)(scaledZ - boxWidth), (double)(scaledX + boxWidth), (double)(scaledY + boxHeight), (double)(scaledZ + boxWidth));
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, 2.0F));
                  if (BomboConfig.get().eggFinderBeacon) {
                     float beaconWidth = 0.15F * scale;
                     AABB beaconBox = new AABB((double)(scaledX - beaconWidth), (double)scaledY, (double)(scaledZ - beaconWidth), (double)(scaledX + beaconWidth), (double)(scaledY + 256.0F * scale), (double)(scaledZ + beaconWidth));
                     collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, beaconBox, r, g, b, 0.4F, 2.0F));
                  }

                  String label = wp.type.name + " Egg §7(" + (int)distance + "m)";
                  BomboRenderUtils.drawText(poseStack, collector, label, (float)x, (float)y + 0.8F, (float)z, wp.type.hexColor, 0.03F, true, BomboConfig.get().eggFinderThroughWalls);
               }

            }
         }
      }
   }

   public static String getSkyblockerLocationName(String bomboLocation) {
      if (bomboLocation == null) {
         return null;
      } else {
         String lower = bomboLocation.toLowerCase().trim();
         if (!lower.contains("bayou") && !lower.contains("backwater")) {
            if (!lower.contains("crimson") && !lower.contains("isle")) {
               if (!lower.contains("crystal") && !lower.contains("hollows")) {
                  if (!lower.contains("deep") && !lower.contains("cavern")) {
                     if (lower.contains("dungeon hub")) {
                        return "Dungeon Hub";
                     } else if (!lower.contains("dwarven") && (!lower.contains("mines") || lower.contains("gold") || lower.contains("coal"))) {
                        if (lower.contains("galatea")) {
                           return "Galatea";
                        } else if (!lower.contains("gold mine") && !lower.equals("gold") && (!lower.contains("gold") || !lower.contains("mine"))) {
                           if (!lower.equals("hub") && !lower.contains("the hub") && !lower.equals("village") && !lower.equals("ruins") && !lower.equals("bazaar")) {
                              if (!lower.contains("lotus") && !lower.contains("atoll")) {
                                 if (!lower.contains("spider") && !lower.equals("spider's den") && !lower.equals("spiders den")) {
                                    if (!lower.contains("end") && !lower.equals("nest")) {
                                       if (!lower.contains("farming") && !lower.contains("barn") && !lower.contains("desert") && !lower.contains("mushroom") && !lower.contains("oasis") && !lower.contains("windmill")) {
                                          return !lower.contains("park") && !lower.contains("spruce") && !lower.contains("birch") && !lower.contains("savanna") && !lower.contains("howling") && !lower.contains("melancholy") && !lower.contains("thicket") ? bomboLocation : "The Park";
                                       } else {
                                          return "The Farming Islands";
                                       }
                                    } else {
                                       return "The End";
                                    }
                                 } else {
                                    return "Spider's Den";
                                 }
                              } else {
                                 return "Lotus Atoll";
                              }
                           } else {
                              return "Hub";
                           }
                        } else {
                           return "Gold Mine";
                        }
                     } else {
                        return "Dwarven Mines";
                     }
                  } else {
                     return "Deep Caverns";
                  }
               } else {
                  return "Crystal Hollows";
               }
            } else {
               return "Crimson Isle";
            }
         } else {
            return "Backwater Bayou";
         }
      }
   }

   public static List<EggWaypoint> getActiveWaypoints() {
      synchronized(activeWaypoints) {
         return new ArrayList(activeWaypoints);
      }
   }

   public static enum EggType {
      BREAKFAST("Breakfast", 16755200, ChatFormatting.GOLD, 7, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY3MzE0OSwKICAicHJvZmlsZUlkIiA6ICJiN2I4ZTlhZjEwZGE0NjFmOTY2YTQxM2RmOWJiM2U4OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbmFiYW5hbmFZZzciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTQ5MzMzZDg1YjhhMzE1ZDAzMzZlYjJkZjM3ZDhhNzE0Y2EyNGM1MWI4YzYwNzRmMWI1YjkyN2RlYjUxNmMyNCIKICAgIH0KICB9Cn0=", true),
      LUNCH("Lunch", 5592575, ChatFormatting.BLUE, 14, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjU2ODExMiwKICAicHJvZmlsZUlkIiA6ICI3NzUwYzFhNTM5M2Q0ZWQ0Yjc2NmQ4ZGUwOWY4MjU0NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWVkcmVsIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83YWU2ZDJkMzFkODE2N2JjYWY5NTI5M2I2OGE0YWNkODcyZDY2ZTc1MWRiNWEzNGYyY2JjNjc2NmEwMzU2ZDBhIgogICAgfQogIH0KfQ==", true),
      DINNER("Dinner", 5635925, ChatFormatting.GREEN, 21, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY0OTcwMSwKICAicHJvZmlsZUlkIiA6ICI3NGEwMzQxNWY1OTI0ZTA4YjMyMGM2MmU1NGE3ZjJhYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZXp6aXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVlMzYxNjU4MTlmZDI4NTBmOTg1NTJlZGNkNzYzZmY5ODYzMTMxMTkyODNjMTI2YWNlMGM0Y2M0OTVlNzZhOCIKICAgIH0KICB9Cn0=", true),
      BRUNCH("Brunch", 16755200, ChatFormatting.GOLD, 7, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY3MzE0OSwKICAicHJvZmlsZUlkIiA6ICJiN2I4ZTlhZjEwZGE0NjFmOTY2YTQxM2RmOWJiM2U4OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbmFiYW5hbmFZZzciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTQ5MzMzZDg1YjhhMzE1ZDAzMzZlYjJkZjM3ZDhhNzE0Y2EyNGM1MWI4YzYwNzRmMWI1YjkyN2RlYjUxNmMyNCIKICAgIH0KICB9Cn0=", false),
      DEJEUNER("Déjeuner", 5592575, ChatFormatting.BLUE, 14, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjU2ODExMiwKICAicHJvZmlsZUlkIiA6ICI3NzUwYzFhNTM5M2Q0ZWQ0Yjc2NmQ4ZGUwOWY4MjU0NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWVkcmVsIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83YWU2ZDJkMzFkODE2N2JjYWY5NTI5M2I2OGE0YWNkODcyZDY2ZTc1MWRiNWEzNGYyY2JjNjc2NmEwMzU2ZDBhIgogICAgfQogIH0KfQ==", false),
      SUPPER("Supper", 5635925, ChatFormatting.GREEN, 21, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY0OTcwMSwKICAicHJvZmlsZUlkIiA6ICI3NGEwMzQxNWY1OTI0ZTA4YjMyMGM2MmU1NGE3ZjJhYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZXp6aXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVlMzYxNjU4MTlmZDI4NTBmOTg1NTJlZGNkNzYzZmY5ODYzMTMxMTkyODNjMTI2YWNlMGM0Y2M0OTVlNzZhOCIKICAgIH0KICB9Cn0=", false);

      public final String name;
      public final int hexColor;
      public final ChatFormatting chatColor;
      public final int resetHour;
      public final String texture;
      public final boolean oddDay;
      public boolean collected = false;

      private EggType(String name, int hexColor, ChatFormatting chatColor, int resetHour, String texture, boolean oddDay) {
         this.name = name;
         this.hexColor = hexColor;
         this.chatColor = chatColor;
         this.resetHour = resetHour;
         this.texture = texture;
         this.oddDay = oddDay;
      }

      public static EggType getTypeByName(String name) {
         for(EggType type : values()) {
            if (type.name.equalsIgnoreCase(name) || type.name().equalsIgnoreCase(name)) {
               return type;
            }
         }

         return null;
      }

      // $FF: synthetic method
      private static EggType[] $values() {
         return new EggType[]{BREAKFAST, LUNCH, DINNER, BRUNCH, DEJEUNER, SUPPER};
      }
   }

   public static class EggWaypoint {
      public final BlockPos pos;
      public final EggType type;
      public boolean collected = false;

      public EggWaypoint(BlockPos pos, EggType type) {
         this.pos = pos;
         this.type = type;
      }
   }

   public static class SkyblockTimeInfo {
      public final boolean isSpring;
      public final int month;
      public final int day;
      public final int hour;

      public SkyblockTimeInfo(long epochMs) {
         long sbMillis = epochMs - 1560275700000L;
         double hourLen = (double)50000.0F;
         double dayLen = hourLen * (double)24.0F;
         double monthLen = dayLen * (double)31.0F;
         this.month = (int)(Math.floor((double)sbMillis / monthLen) % (double)12.0F);
         this.day = (int)(Math.floor((double)sbMillis / dayLen) % (double)31.0F + (double)1.0F);
         this.hour = (int)(Math.floor((double)sbMillis / hourLen) % (double)24.0F);
         this.isSpring = this.month / 3 == 0;
      }
   }
}
