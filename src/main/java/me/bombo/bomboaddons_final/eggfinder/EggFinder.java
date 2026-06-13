package me.bombo.bomboaddons_final.eggfinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.bombo.bomboaddons_final.BomboConfig;
import me.bombo.bomboaddons_final.BomboRenderUtils;
import me.bombo.bomboaddons_final.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EggFinder {
    private static final Logger LOGGER = LoggerFactory.getLogger("bomboaddons-eggfinder");
    
    private static final Pattern EGG_FOUND_PATTERN = Pattern.compile("^(?:HOPPITY'S HUNT You found a Chocolate|You have already collected this Chocolate) (Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg");
    private static final Pattern NO_EGGS_PATTERN = Pattern.compile("^There are no hidden Chocolate Rabbit Eggs nearby! Try again later!$");

    private static final Set<String> VALID_LOCATIONS = Set.of(
            "Backwater Bayou", "Crimson Isle", "Crystal Hollows", "Deep Caverns",
            "Dungeon Hub", "Dwarven Mines", "Galatea", "Gold Mine", "Hub",
            "Lotus Atoll", "Spider's Den", "The End", "The Farming Islands", "The Park"
    );

    private static final List<EggWaypoint> activeWaypoints = new ArrayList<>();
    private static SkyblockTimeInfo lastTime = null;
    private static String lastLoc = null;

    public static void init() {
        // Initial setup
    }

    public static void clearEggs() {
        synchronized (activeWaypoints) {
            activeWaypoints.clear();
        }
        for (EggType type : EggType.values()) {
            type.collected = false;
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (lastLoc != null) {
                lastLoc = null;
                EggWebSocket.updateSubscription(null);
                clearEggs();
            }
            return;
        }

        // Update Location Subscription
        String rawLoc = SkyblockUtils.getLocation();
        String currentLoc = getSkyblockerLocationName(rawLoc);
        if (!Objects.equals(lastLoc, currentLoc)) {
            LOGGER.info("[EggFinder] Location changed from " + lastLoc + " to " + currentLoc);
            lastLoc = currentLoc;
            clearEggs();
            if (VALID_LOCATIONS.contains(currentLoc)) {
                EggWebSocket.updateSubscription(currentLoc);
            } else {
                EggWebSocket.updateSubscription(null);
            }
        }

        // Update Skyblock Time resets
        SkyblockTimeInfo time = new SkyblockTimeInfo(System.currentTimeMillis());
        if (lastTime == null || lastTime.isSpring != time.isSpring) {
            if (!time.isSpring) {
                clearEggs();
            }
        }
        if (lastTime != null && time.isSpring && lastTime.hour != time.hour) {
            int dayNumber = time.month * 31 + time.day;
            boolean isOdd = dayNumber % 2 == 1;
            for (EggType type : EggType.values()) {
                if (time.hour == type.resetHour && isOdd == type.oddDay) {
                    type.collected = false;
                    // Remove expired waypoint of this type
                    synchronized (activeWaypoints) {
                        activeWaypoints.removeIf(wp -> wp.type == type);
                    }
                }
            }
        }
        lastTime = time;
    }

    public static boolean onChatMessage(Component text, boolean overlay) {
        if (overlay || !BomboConfig.get().eggFinder) return true;

        String msg = text.getString();
        Matcher matcher = NO_EGGS_PATTERN.matcher(msg);
        if (matcher.matches()) {
            synchronized (activeWaypoints) {
                for (EggWaypoint wp : activeWaypoints) {
                    wp.collected = true;
                }
                activeWaypoints.clear();
            }
            for (EggType type : EggType.values()) {
                type.collected = true;
            }
            return true;
        }

        matcher.usePattern(EGG_FOUND_PATTERN);
        if (matcher.find()) {
            try {
                String typeName = matcher.group(1);
                EggType eggType = EggType.getTypeByName(typeName);
                if (eggType == null) return true;

                eggType.collected = true;
                
                // Mark matching active waypoints as collected
                synchronized (activeWaypoints) {
                    activeWaypoints.removeIf(wp -> wp.type == eggType);
                }

                LOGGER.info("Collected or found a Chocolate " + typeName + " Egg!");
                Minecraft client = Minecraft.getInstance();
                if (client.player == null || client.level == null) return true;

                // Scan surrounding for egg armor stand
                List<ArmorStand> entities = client.level.getEntitiesOfClass(ArmorStand.class,
                        AABB.ofSize(client.player.position(), 8f, 8f, 8f),
                        entity -> checkIfEgg(entity, eggType)
                );

                if (entities.isEmpty()) {
                    LOGGER.info("No egg armor stand found nearby.");
                    return true;
                }

                BlockPos eggPos = entities.get(0).blockPosition().above(2);
                
                // Share position to WebSocket
                if (VALID_LOCATIONS.contains(lastLoc)) {
                    EggWebSocket.sendPublish(lastLoc, eggType.name, eggPos);
                }

                if (BomboConfig.get().eggFinderChat) {
                    client.player.displayClientMessage(Component.literal("§8[§bBomboAddons§8] §aYou found a ")
                            .append(Component.literal(eggType.name + " Egg").withStyle(style -> style.withColor(eggType.chatColor)))
                            .append(" §aat " + eggPos.getX() + ", " + eggPos.getY() + ", " + eggPos.getZ() + "!"), false);
                }

            } catch (Exception e) {
                LOGGER.error("Failed to process egg chat message: " + e.getMessage(), e);
            }
        }

        return true;
    }

    public static void onWebsocketMessage(String eggTypeStr, BlockPos pos) {
        EggType eggType = EggType.getTypeByName(eggTypeStr);
        if (eggType == null) return;

        // Add to active waypoints
        synchronized (activeWaypoints) {
            activeWaypoints.removeIf(wp -> wp.type == eggType);
            if (!eggType.collected) {
                activeWaypoints.add(new EggWaypoint(pos, eggType));
            }
        }

        if (BomboConfig.get().eggFinderChat && !eggType.collected) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                Component eggName = Component.literal(eggType.name + " Egg").withStyle(style -> style.withColor(eggType.chatColor));
                client.player.displayClientMessage(
                        Component.literal("§8[§bBomboAddons§8] §aNew ")
                                .append(eggName)
                                .append(" §adiscovered at §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "!")
                                .withStyle(style -> style
                                        .withClickEvent(new ClickEvent.RunCommand("/skyblocker eggFinder shareLocation " + eggType.name().toLowerCase()))
                                        .withHoverEvent(me.bombo.bomboaddons_final.SBECommands.createHoverEvent("§aClick to share this egg location in chat!"))
                                ), false
                );
            }
        }
    }

    public static boolean checkIfEgg(ArmorStand armorStand, EggType eggType) {
        if (armorStand.hasCustomName() || !armorStand.isInvisible() || armorStand.showBasePlate()) return false;
        ItemStack head = armorStand.getItemBySlot(EquipmentSlot.HEAD);
        if (head != null && head.is(Items.PLAYER_HEAD)) {
            ResolvableProfile profile = head.get(DataComponents.PROFILE);
            if (profile != null && profile.partialProfile() != null && profile.partialProfile().properties() != null) {
                for (com.mojang.authlib.properties.Property prop : profile.partialProfile().properties().get("textures")) {
                    if (Objects.equals(prop.value(), eggType.texture)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void render(WorldRenderContext context) {
        if (!BomboConfig.get().eggFinder) return;
        
        SkyblockTimeInfo time = lastTime;
        if (time == null || !time.isSpring) return;

        List<EggWaypoint> wps;
        synchronized (activeWaypoints) {
            wps = new ArrayList<>(activeWaypoints);
        }

        if (wps.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.matrices();
        MultiBufferSource consumers = context.consumers();
        if (consumers == null) return;

        for (EggWaypoint wp : wps) {
            double x = wp.pos.getX() - camPos.x;
            double y = wp.pos.getY() - camPos.y;
            double z = wp.pos.getZ() - camPos.z;

            double dist = wp.pos.distToCenterSqr(camPos.x, camPos.y, camPos.z);
            float distance = (float) Math.sqrt(dist);

            float scale = 1.0f;
            if (BomboConfig.get().eggFinderThroughWalls) {
                if (distance > 0.2f) {
                    scale = 0.2f / distance;
                }
            }

            float boxWidth = 0.5f * scale;
            float boxHeight = 0.5f * scale;

            float scaledX = (float) x * scale;
            float scaledY = (float) y * scale;
            float scaledZ = (float) z * scale;

            // Colors
            float r = ((wp.type.hexColor >> 16) & 0xFF) / 255.0f;
            float g = ((wp.type.hexColor >> 8) & 0xFF) / 255.0f;
            float b = (wp.type.hexColor & 0xFF) / 255.0f;
            float a = 1.0f;

            VertexConsumer lineBuffer = consumers.getBuffer(RenderTypes.linesTranslucent());
            AABB box = new AABB(
                    scaledX - boxWidth, scaledY - boxHeight, scaledZ - boxWidth,
                    scaledX + boxWidth, scaledY + boxHeight, scaledZ + boxWidth
            );
            BomboRenderUtils.drawBox(poseStack, lineBuffer, box, r, g, b, a, 2.0f);

            if (BomboConfig.get().eggFinderBeacon) {
                float beaconWidth = 0.15f * scale;
                AABB beaconBox = new AABB(
                        scaledX - beaconWidth, scaledY, scaledZ - beaconWidth,
                        scaledX + beaconWidth, scaledY + (256.0f * scale), scaledZ + beaconWidth
                );
                BomboRenderUtils.drawBox(poseStack, lineBuffer, beaconBox, r, g, b, 0.4f, 2.0f);
            }

            // Draw label
            String label = wp.type.name + " Egg §7(" + (int) distance + "m)";
            BomboRenderUtils.drawText(poseStack, consumers, label, (float) x, (float) y + 0.8f, (float) z, wp.type.hexColor, 0.03f, true, BomboConfig.get().eggFinderThroughWalls);
        }
    }

    public static String getSkyblockerLocationName(String bomboLocation) {
        if ("The Hub".equalsIgnoreCase(bomboLocation)) {
            return "Hub";
        }
        if ("Farming Islands".equalsIgnoreCase(bomboLocation)) {
            return "The Farming Islands";
        }
        return bomboLocation;
    }

    public static List<EggWaypoint> getActiveWaypoints() {
        synchronized (activeWaypoints) {
            return new ArrayList<>(activeWaypoints);
        }
    }

    public enum EggType {
        BREAKFAST("Breakfast", 0xFFAA00, ChatFormatting.GOLD, 7, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY3MzE0OSwKICAicHJvZmlsZUlkIiA6ICJiN2I4ZTlhZjEwZGE0NjFmOTY2YTQxM2RmOWJiM2U4OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbmFiYW5hbmFZZzciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTQ5MzMzZDg1YjhhMzE1ZDAzMzZlYjJkZjM3ZDhhNzE0Y2EyNGM1MWI4YzYwNzRmMWI1YjkyN2RlYjUxNmMyNCIKICAgIH0KICB9Cn0=", true),
        LUNCH("Lunch", 0x5555FF, ChatFormatting.BLUE, 14, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjU2ODExMiwKICAicHJvZmlsZUlkIiA6ICI3NzUwYzFhNTM5M2Q0ZWQ0Yjc2NmQ4ZGUwOWY4MjU0NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWVkcmVsIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83YWU2ZDJkMzFkODE2N2JjYWY5NTI5M2I2OGE0YWNkODcyZDY2ZTc1MWRiNWEzNGYyY2JjNjc2NmEwMzU2ZDBhIgogICAgfQogIH0KfQ==", true),
        DINNER("Dinner", 0x55FF55, ChatFormatting.GREEN, 21, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY0OTcwMSwKICAicHJvZmlsZUlkIiA6ICI3NGEwMzQxNWY1OTI0ZTA4YjMyMGM2MmU1NGE3ZjJhYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZXp6aXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVlMzYxNjU4MTlmZDI4NTBmOTg1NTJlZGNkNzYzZmY5ODYzMTMxMTkyODNjMTI2YWNlMGM0Y2M0OTVlNzZhOCIKICAgIH0KICB9Cn0=", true),
        BRUNCH("Brunch", 0xFFAA00, ChatFormatting.GOLD, 7, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY3MzE0OSwKICAicHJvZmlsZUlkIiA6ICJiN2I4ZTlhZjEwZGE0NjFmOTY2YTQxM2RmOWJiM2U4OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbmFiYW5hbmFZZzciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTQ5MzMzZDg1YjhhMzE1ZDAzMzZlYjJkZjM3ZDhhNzE0Y2EyNGM1MWI4YzYwNzRmMWI1YjkyN2RlYjUxNmMyNCIKICAgIH0KICB9Cn0=", false),
        DEJEUNER("Déjeuner", 0x5555FF, ChatFormatting.BLUE, 14, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjU2ODExMiwKICAicHJvZmlsZUlkIiA6ICI3NzUwYzFhNTM5M2Q0ZWQ0Yjc2NmQ4ZGUwOWY4MjU0NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWVkcmVsIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83YWU2ZDJkMzFkODE2N2JjYWY5NTI5M2I2OGE0YWNkODcyZDY2ZTc1MWRiNWEzNGYyY2JjNjc2NmEwMzU2ZDBhIgogICAgfQogIH0KfQ==", false),
        SUPPER("Supper", 0x55FF55, ChatFormatting.GREEN, 21, "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY0OTcwMSwKICAicHJvZmlsZUlkIiA6ICI3NGEwMzQxNWY1OTI0ZTA4YjMyMGM2MmU1NGE3ZjJhYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZXp6aXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVlMzYxNjU4MTlmZDI4NTBmOTg1NTJlZGNkNzYzZmY5ODYzMTMxMTkyODNjMTI2YWNlMGM0Y2M0OTVlNzZhOCIKICAgIH0KICB9Cn0=", false);

        public final String name;
        public final int hexColor;
        public final ChatFormatting chatColor;
        public final int resetHour;
        public final String texture;
        public final boolean oddDay;

        public boolean collected = false;

        EggType(String name, int hexColor, ChatFormatting chatColor, int resetHour, String texture, boolean oddDay) {
            this.name = name;
            this.hexColor = hexColor;
            this.chatColor = chatColor;
            this.resetHour = resetHour;
            this.texture = texture;
            this.oddDay = oddDay;
        }

        public static EggType getTypeByName(String name) {
            for (EggType type : values()) {
                if (type.name.equalsIgnoreCase(name) || type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
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
        public final int month; // 0-11
        public final int day;   // 1-31
        public final int hour;  // 0-23

        public SkyblockTimeInfo(long epochMs) {
            long sbMillis = epochMs - 1560275700000L;
            double hourLen = 50000.0;
            double dayLen = hourLen * 24.0;
            double monthLen = dayLen * 31.0;

            this.month = (int) (Math.floor(sbMillis / monthLen) % 12);
            this.day = (int) (Math.floor(sbMillis / dayLen) % 31 + 1);
            this.hour = (int) (Math.floor(sbMillis / hourLen) % 24);
            this.isSpring = (this.month / 3 == 0);
        }
    }
}
