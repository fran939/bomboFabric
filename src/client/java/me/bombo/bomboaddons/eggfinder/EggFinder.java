package me.bombo.bomboaddons.eggfinder;

import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import net.fabricmc.loader.api.FabricLoader;
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
   // The flavour is captured generically instead of from an allowlist: Hypixel keeps adding egg
   // types (and renames existing ones), and a name that missed the list meant the pickup was never
   // registered - so a collected egg stayed highlighted for the rest of the lobby.
   private static final Pattern EGG_FOUND_PATTERN = Pattern.compile("(?:HOPPITY'S HUNT You found a Chocolate|You have already collected this Chocolate) (.+?) Egg", 2);
   private static final Pattern NO_EGGS_PATTERN = Pattern.compile("There are no hidden Chocolate Rabbit Eggs nearby! Try again later!", 2);
   private static final Pattern RABBIT_FOUND_PATTERN = Pattern.compile("HOPPITY'S HUNT You found (.+) \\(([A-Z]+)\\)!", 2);
   private static final Set<String> VALID_LOCATIONS = Set.of(
      "Backwater Bayou", "Crimson Isle", "Crystal Hollows", "Deep Caverns",
      "Dungeon Hub", "Dwarven Mines", "Galatea", "Gold Mine", "Hub",
      "Lotus Atoll", "Spider's Den", "The End", "The Farming Islands", "The Park",
      "Torrhus Canyon", "The Rift", "Jerry's Workshop", "Garden"
   );
   private static final List<EggWaypoint> activeWaypoints = new ArrayList();
   /**
    * Egg spawn positions already collected this cycle, keyed {@code TYPE@x,y,z}.
    *
    * <p>Hoppity egg spawns are fixed per island, so remembering the *position* (not just the egg
    * type) is what stops a collected "Brunch Egg" from being highlighted again after a lobby hop
    * wipes {@link #activeWaypoints}.
    */
   private static final Set<String> collectedPositions = new HashSet<>();
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

      synchronized(collectedPositions) {
         collectedPositions.clear();
      }

   }

   private static String positionKey(EggType type, BlockPos pos) {
      return type == null || pos == null ? "" : type.name() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
   }

   /** Records that the egg at this spawn position was already picked up this cycle. */
   public static void markPositionCollected(EggType type, BlockPos pos) {
      if (type == null || pos == null) return;
      synchronized(collectedPositions) {
         collectedPositions.add(positionKey(type, pos));
      }
   }

   /** True when this exact spawn position was already collected, so it must not be highlighted. */
   public static boolean isPositionCollected(EggType type, BlockPos pos) {
      if (type == null || pos == null) return false;
      synchronized(collectedPositions) {
         return collectedPositions.contains(positionKey(type, pos));
      }
   }

   /** Drops the collected memory for one egg type when its hunt cycle resets. */
   private static void forgetCollectedPositions(EggType type) {
      if (type == null) return;
      synchronized(collectedPositions) {
         collectedPositions.removeIf(key -> key.startsWith(type.name() + "@"));
      }
   }

   public static boolean hasWaypoints() {
      if (activeWaypoints.isEmpty()) {
         return false;
      }
      synchronized(activeWaypoints) {
         for (EggWaypoint wp : activeWaypoints) {
            if (!wp.collected) return true;
         }
         return false;
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
         String rawLoc = SkyblockUtils.getLocation();
         if (rawLoc == null || rawLoc.isEmpty()) {
            rawLoc = BomboaddonsClient.currentArea;
         }
         String currentLoc = getSkyblockerLocationName(rawLoc);
         if (!Objects.equals(lastLoc, currentLoc)) {
            if (currentLoc != null) {
               LOGGER.info("[EggFinder] Location updated to " + currentLoc + " (was " + lastLoc + ")");
               lastLoc = currentLoc;
               clearWaypoints();
               EggWebSocket.updateSubscription(currentLoc);
            } else {
               // A reading we cannot map must not tear down a working subscription: that is what
               // made /b egg report "Active Subscription Area: None" on islands we do support.
               LOGGER.info("[EggFinder] Unmapped location '" + rawLoc + "' - keeping subscription " + lastLoc);
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
                  // A new hunt cycle: the old collected positions are valid again.
                  forgetCollectedPositions(type);
                  synchronized(activeWaypoints) {
                     activeWaypoints.removeIf((wp) -> wp.type == type);
                  }
               }
            }
         }

         if (currentLoc != null && VALID_LOCATIONS.contains(currentLoc) && BomboConfig.get().eggFinder) {
            // If Skyblocker is loaded, also sync directly with its EggFinder instance
            if (FabricLoader.getInstance().isModLoaded("skyblocker")) {
               try {
                  Class<?> skyEggFinder = Class.forName("de.hysky.skyblocker.skyblock.chocolatefactory.EggFinder$EggType");
                  Object entries = skyEggFinder.getField("entries").get(null);
                  if (entries instanceof Iterable<?> iter) {
                     for (Object eggTypeEntry : iter) {
                        Field nameField = eggTypeEntry.getClass().getField("name");
                        Field collectedField = eggTypeEntry.getClass().getField("collected");
                        Field eggField = eggTypeEntry.getClass().getField("egg");
                        String eName = (String) nameField.get(eggTypeEntry);
                        boolean collected = (Boolean) collectedField.get(eggTypeEntry);
                        Object eggObj = eggField.get(eggTypeEntry);
                        EggType type = EggFinder.EggType.getTypeByName(eName);
                        if (type != null) {
                           type.collected = collected;
                           if (eggObj != null && !collected) {
                              Field coordsField = eggObj.getClass().getField("coordinates");
                              BlockPos pos = (BlockPos) coordsField.get(eggObj);
                              if (pos != null && !isPositionCollected(type, pos)) {
                                 synchronized(activeWaypoints) {
                                    activeWaypoints.removeIf((wp) -> wp.type == type);
                                    activeWaypoints.add(new EggWaypoint(pos, type));
                                 }
                              }
                           }
                        }
                     }
                  }
               } catch (Throwable ignored) {}
            }

            try {
               List<ArmorStand> nearbyStands = mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(64.0));
               for (ArmorStand stand : nearbyStands) {
                  for (EggType type : EggFinder.EggType.values()) {
                     if (!type.collected && !isPositionCollected(type, stand.blockPosition().above(2)) && checkIfEgg(stand, type)) {
                        BlockPos eggPos = stand.blockPosition().above(2);
                        boolean added = false;
                        synchronized(activeWaypoints) {
                           boolean exists = false;
                           for (EggWaypoint wp : activeWaypoints) {
                              if (wp.type == type && wp.pos.equals(eggPos)) {
                                 exists = true;
                                 break;
                              }
                           }
                           if (!exists) {
                              activeWaypoints.removeIf((wp) -> wp.type == type);
                              activeWaypoints.add(new EggWaypoint(eggPos, type));
                              added = true;
                           }
                        }
                         if (added) {
                            if (EggWebSocket.isConnected()) {
                               EggWebSocket.sendPublish(currentLoc, type.name, eggPos);
                            }
                            me.bombo.bomboaddons.IRCClient.broadcastEgg(currentLoc, type.name, eggPos);
                         }
                        break;
                     }
                  }
               }
            } catch (Throwable ignored) {}
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
                     // Unknown flavour: something *was* just picked up right here, so retire the
                     // closest waypoint rather than leaving a ghost highlight behind.
                     retireNearestWaypoint(typeName);
                     return true;
                  }

                  eggType.collected = true;
                  synchronized(activeWaypoints) {
                     // Remember the positions being retired so a re-discovery after a lobby hop does
                     // not bring the already-collected egg back as a waypoint.
                     for (EggWaypoint wp : activeWaypoints) {
                        if (wp.type == eggType) {
                           markPositionCollected(eggType, wp.pos);
                        }
                     }
                     activeWaypoints.removeIf((wpx) -> wpx.type == eggType);
                  }

                  // Also remember where the egg actually was, even if we never highlighted it.
                  BlockPos foundAt = null;
                  try {
                     Minecraft foundClient = Minecraft.getInstance();
                     if (foundClient.player != null && foundClient.level != null) {
                        List<ArmorStand> near = foundClient.level.getEntitiesOfClass(ArmorStand.class,
                                AABB.ofSize(foundClient.player.position(), 8.0D, 8.0D, 8.0D), (entity) -> checkIfEgg(entity, eggType));
                        if (!near.isEmpty()) {
                           foundAt = near.get(0).blockPosition().above(2);
                           markPositionCollected(eggType, foundAt);
                        }
                     }
                  } catch (Throwable ignored) {
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
                     if (EggWebSocket.isConnected()) {
                        EggWebSocket.sendPublish(lastLoc, eggType.name, eggPos);
                     }
                     me.bombo.bomboaddons.IRCClient.broadcastEgg(lastLoc, eggType.name, eggPos);
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

                        // "You found a Chocolate Rabbit" carries no coordinates, so the piggyback
                        // pickup has to be matched by distance. Only act when the match is
                        // unambiguous: retiring the wrong egg is worse than retiring none, because
                        // a wrongly-cleared egg simply never comes back this hunt cycle.
                        List<EggWaypoint> touching = new ArrayList<>();
                        List<EggWaypoint> nearby = new ArrayList<>();
                        synchronized(activeWaypoints) {
                           for (EggWaypoint wp : activeWaypoints) {
                              double dist = wp.pos.distToCenterSqr(playerPos.x, playerPos.y, playerPos.z);
                              if (dist <= 15.0D * 15.0D) {
                                 touching.add(wp);
                              }
                              if (dist <= 40.0D * 40.0D) {
                                 nearby.add(wp);
                              }
                           }
                        }

                        EggWaypoint target = null;
                        if (touching.size() == 1) {
                           target = touching.get(0);
                        } else if (touching.isEmpty() && nearby.size() == 1) {
                           target = nearby.get(0);
                        }

                        if (target != null) {
                           EggType eggType = target.type;
                           eggType.collected = true;
                           synchronized(activeWaypoints) {
                              for (EggWaypoint wp : activeWaypoints) {
                                 if (wp.type == eggType) {
                                    markPositionCollected(eggType, wp.pos);
                                 }
                              }
                              activeWaypoints.removeIf((wpx) -> wpx.type == eggType);
                           }

                           LOGGER.info("Collected rabbit egg via proximity to " + eggType.name + " Egg!");
                        } else if (!touching.isEmpty() || !nearby.isEmpty()) {
                           LOGGER.info("Rabbit found with " + Math.max(touching.size(), nearby.size())
                                   + " egg(s) in range - leaving every waypoint highlighted.");
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

   /**
    * Retires the waypoint nearest the player when the server reports an egg flavour we do not
    * know. Only used for pickups the player is standing on, so a 64 block cap keeps a mistyped or
    * unknown message from clearing an egg on the other side of the island.
    */
   private static void retireNearestWaypoint(String typeName) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null || client.level == null) return;
      Vec3 playerPos = client.player.position();
      EggWaypoint closest = null;
      double closestDist = Double.MAX_VALUE;
      synchronized(activeWaypoints) {
         for (EggWaypoint wp : activeWaypoints) {
            double dist = wp.pos.distToCenterSqr(playerPos.x, playerPos.y, playerPos.z);
            if (dist < closestDist) {
               closestDist = dist;
               closest = wp;
            }
         }
      }
      if (closest == null || closestDist > 64.0D * 64.0D) {
         LOGGER.info("Collected egg flavour '" + typeName + "' is unknown and no waypoint was close enough to retire.");
         return;
      }
      EggType type = closest.type;
      if (type != null) {
         type.collected = true;
         markPositionCollected(type, closest.pos);
      }
      synchronized(activeWaypoints) {
         activeWaypoints.remove(closest);
      }
      LOGGER.info("Collected unknown egg flavour '" + typeName + "' - retired the nearest waypoint ("
              + (type != null ? type.name() : "?") + ").");
   }

   public static void onWebsocketMessage(String eggTypeStr, BlockPos pos) {
      EggType eggType = EggFinder.EggType.getTypeByName(eggTypeStr);
      if (eggType != null) {
         synchronized(activeWaypoints) {
            activeWaypoints.removeIf((wp) -> wp.type == eggType);
            if (!eggType.collected && !isPositionCollected(eggType, pos)) {
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
      if (!BomboConfig.get().eggFinder) {
         return;
      }
      if (activeWaypoints.isEmpty()) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return;
      }

      Vec3 camPos = mc.gameRenderer.mainCamera().position();
      PoseStack poseStack = context.poseStack();
      OrderedSubmitNodeCollector collector = null;

      synchronized (activeWaypoints) {
         for (int i = 0; i < activeWaypoints.size(); i++) {
            EggWaypoint wp = activeWaypoints.get(i);
            if (wp.collected) continue;

            double x = (double) wp.pos.getX() + 0.5 - camPos.x;
            double y = (double) wp.pos.getY() + 0.5 - camPos.y;
            double z = (double) wp.pos.getZ() + 0.5 - camPos.z;
            double distSq = x * x + y * y + z * z;
            if (distSq > 256.0 * 256.0) continue;

            float distance = (float) Math.sqrt(distSq);
            float scale = 1.0F;
            if (BomboConfig.get().eggFinderThroughWalls && distance > 0.2F) {
               scale = 0.2F / distance;
            }

            if (collector == null) {
               collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());
            }

            float boxWidth = 0.5F * scale;
            float boxHeight = 0.5F * scale;
            float scaledX = (float) x * scale;
            float scaledY = (float) y * scale;
            float scaledZ = (float) z * scale;
            float r = (float) (wp.type.hexColor >> 16 & 255) / 255.0F;
            float g = (float) (wp.type.hexColor >> 8 & 255) / 255.0F;
            float b = (float) (wp.type.hexColor & 255) / 255.0F;
            float a = 1.0F;
            AABB box = new AABB((double) (scaledX - boxWidth), (double) (scaledY - boxHeight), (double) (scaledZ - boxWidth),
                                (double) (scaledX + boxWidth), (double) (scaledY + boxHeight), (double) (scaledZ + boxWidth));
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(),
               (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, 2.0F));

            if (BomboConfig.get().eggFinderBeacon) {
               float beaconWidth = 0.15F * scale;
               AABB beaconBox = new AABB((double) (scaledX - beaconWidth), (double) scaledY, (double) (scaledZ - beaconWidth),
                                         (double) (scaledX + beaconWidth), (double) (scaledY + 256.0F * scale), (double) (scaledZ - beaconWidth));
               collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(),
                  (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, beaconBox, r, g, b, 0.4F, 2.0F));
            }

            String label = wp.type.name + " Egg §7(" + (int) distance + "m)";
            BomboRenderUtils.drawText(poseStack, collector, label, (float) x, (float) y + 0.8F, (float) z, wp.type.hexColor, 0.03F, true, BomboConfig.get().eggFinderThroughWalls);
         }
      }
   }

   /**
    * Maps whatever {@code SkyblockUtils.getLocation()} reported onto the island names the hoppity
    * service uses.
    *
    * <p>This used to fall through and return the raw reading, so any sub-area it did not know
    * ("Bazaar Alley", "Wilderness", "Coal Mine", "Graveyard", ...) produced a value that is not a
    * valid island - the subscription was then cleared and {@code /b egg} reported
    * "Active Subscription Area: None" on an island the mod fully supports. Every SkyBlock sub-area
    * is now listed, and the parent area is consulted as a second opinion.
    */
   public static String getSkyblockerLocationName(String bomboLocation) {
      if (bomboLocation == null) {
         return null;
      }
      String lower = bomboLocation.toLowerCase(Locale.ROOT).trim();
      if (lower.isEmpty()) {
         return null;
      }

      // --- Private island / lobby / limbo: not hoppity locations at all ---------------------
      if (lower.contains("private island") || lower.equals("private")) return null;
      if (lower.contains("limbo")) return null;

      // --- Dedicated hunts ------------------------------------------------------------------
      if (lower.contains("torrhus") || lower.contains("canyon")) return "Torrhus Canyon";
      if (lower.contains("bayou") || lower.contains("backwater")) return "Backwater Bayou";
      if (lower.contains("galatea")) return "Galatea";
      if (lower.contains("lotus") || lower.contains("atoll")) return "Lotus Atoll";
      if (lower.contains("jerry")) return "Jerry's Workshop";
      if (lower.contains("rift") || lower.contains("wizard tower")) return "The Rift";

      // --- Deep Caverns (checked before Dwarven: its sub-areas contain "mines") --------------
      if (lower.contains("coal mine") || lower.contains("gunpowder mines") || lower.contains("obsidian sanctuary")
              || lower.contains("lapis quarry") || lower.contains("pigmen") || lower.contains("slimehill")
              || lower.contains("diamond reserve") || lower.contains("deep cavern") || lower.equals("deep")) {
         return "Deep Caverns";
      }

      // --- Dwarven Mines --------------------------------------------------------------------
      if (lower.contains("dwarven")) return "Dwarven Mines";

      // --- Crystal Hollows ------------------------------------------------------------------
      if (lower.contains("crystal") || lower.contains("hollows") || lower.contains("nucleus")
              || lower.contains("jungle") || lower.contains("precursor") || lower.contains("goblin")
              || lower.contains("mithril") || lower.contains("fairy grotto") || lower.contains("magma field")
              || lower.contains("khazad")) {
         return "Crystal Hollows";
      }

      // --- Remaining Dwarven sub-areas -------------------------------------------------------
      if (lower.contains("mines") && !lower.contains("gold")) return "Dwarven Mines";

      // --- Crimson Isle ---------------------------------------------------------------------
      if (lower.contains("crimson") || lower.contains("isle") || lower.contains("hot spring")
              || lower.contains("burning desert") || lower.contains("dragontail") || lower.contains("the wastes")
              || lower.contains("blazing volcano") || lower.contains("mystic marsh") || lower.contains("dojo")) {
         return "Crimson Isle";
      }

      // --- Gold Mine ------------------------------------------------------------------------
      if (lower.contains("gold mine") || lower.equals("gold")) return "Gold Mine";

      // --- Spider's Den ---------------------------------------------------------------------
      if (lower.contains("spider")) return "Spider's Den";

      // --- The End --------------------------------------------------------------------------
      if (lower.contains("the end") || lower.equals("end") || lower.contains("dragons nest") || lower.contains("dragon's nest")
              || lower.contains("void sepulture") || lower.contains("zealot")) {
         return "The End";
      }

      // --- The Farming Islands --------------------------------------------------------------
      if (lower.contains("farming") || lower.contains("barn") || lower.contains("desert") || lower.contains("mushroom")
              || lower.contains("oasis") || lower.contains("windmill") || lower.contains("farm") || lower.contains("tragic hill")
              || lower.contains("shepherd")) {
         return "The Farming Islands";
      }

      // --- The Park -------------------------------------------------------------------------
      if (lower.contains("park") || lower.contains("spruce") || lower.contains("birch") || lower.contains("savanna")
              || lower.contains("howling") || lower.contains("melancholy") || lower.contains("thicket")
              || lower.contains("woods")) {
         return "The Park";
      }

      // --- Dungeon Hub ----------------------------------------------------------------------
      if (lower.contains("dungeon hub") || lower.contains("catacomb")) return "Dungeon Hub";

      // --- Garden ---------------------------------------------------------------------------
      if (lower.contains("garden") || lower.contains("greenhouse")) return "Garden";

      // --- Hub (all of its sub-areas) -------------------------------------------------------
      if (lower.equals("hub") || lower.contains("the hub") || lower.equals("village") || lower.equals("ruins")
              || lower.contains("bazaar") || lower.contains("auction") || lower.contains("community center")
              || lower.contains("museum") || lower.contains("bank") || lower.contains("fashion")
              || lower.contains("carnival") || lower.contains("colosseum") || lower.contains("graveyard")
              || lower.contains("wilderness") || lower.contains("forest") || lower.contains("mountain")
              || lower.contains("tavern") || lower.contains("library") || lower.contains("pet care")
              || lower.contains("hub island") || lower.equals("lobby")) {
         return "Hub";
      }

      return null;
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
         long hourLen = 50000L;
         long dayLen = hourLen * 24L; // 1,200,000 ms
         long monthLen = dayLen * 31L; // 37,200,000 ms
         long yearLen = monthLen * 12L; // 446,400,000 ms
         long yearCycle = (sbMillis % yearLen + yearLen) % yearLen;
         this.month = (int) (yearCycle / monthLen);
         this.day = (int) ((yearCycle % monthLen) / dayLen) + 1;
         this.hour = (int) ((yearCycle % dayLen) / hourLen);
         this.isSpring = this.month < 3;
      }
   }
}
