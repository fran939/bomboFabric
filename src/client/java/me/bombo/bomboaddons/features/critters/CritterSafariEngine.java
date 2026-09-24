package me.bombo.bomboaddons.features.critters;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.OrderedSubmitNodeCollector;
import me.bombo.bomboaddons.cheat.esp.TargetPests;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class CritterSafariEngine {

    public static final Map<String, String> BESTIARY_HASH_MAP = new ConcurrentHashMap<>();
    private static final AtomicBoolean isFetchingBestiary = new AtomicBoolean(false);
    private static long lastBestiaryFetchTime = 0L;

    // Bee nest locations in Forest
    public static final List<BlockPos> BEE_NESTS = List.of(
        new BlockPos(-17, 68, 41),
        new BlockPos(-22, 68, 39),
        new BlockPos(23, 70, 46),
        new BlockPos(25, 70, 41),
        new BlockPos(-9, 81, 58),
        new BlockPos(-1, 81, 60),
        new BlockPos(15, 88, 31),
        new BlockPos(20, 88, 33),
        new BlockPos(-1, 84, 9),
        new BlockPos(-13, 103, 21),
        new BlockPos(-11, 103, 13)
    );
    public static final Set<BlockPos> clearedBeeNests = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Breakable walls for Snoozle (Cavern)
    public static final List<BlockPos> SNOOZLE_WALLS = List.of(
        new BlockPos(-126, 41, 74),
        new BlockPos(114, 41, 87),
        new BlockPos(-70, 41, 68),
        new BlockPos(-96, 42, 17),
        new BlockPos(-95, 42, 42)
    );
    public static final Set<BlockPos> brokenWalls = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Nearby entity counts found per critter
    public static final Map<String, Integer> nearbyFoundCounts = new ConcurrentHashMap<>();

    static {
        // Icy
        BESTIARY_HASH_MAP.put("9924c105aa431dabd47952dc1dddd6f751f883423f4db1487d9bacc2cfe99c7a", "Mantis Shrimp");
        BESTIARY_HASH_MAP.put("53de4135a3b19a2187029c86a0020e58c907c7bdd4e37b7643f120e16a0aa9ab", "Troodon");
        // Haunted
        BESTIARY_HASH_MAP.put("407b3c3d2c3fe259d69207a14ca5cd99713c7096ba122bb40326f3489e5d0d6c", "Gazer");
        BESTIARY_HASH_MAP.put("8b329e108ac28b0bec8d47b7cdce253df1db80b46052b5915d963e1bcbab0db4", "Gimmiegold");
        BESTIARY_HASH_MAP.put("3504f1f2327a5110e643bb8667082512815fa434a29ed37f4ca83bb16d2db53", "Hideyho");
        // Cavern
        BESTIARY_HASH_MAP.put("fc63cd0d480971a7beae5fd503e5d51658cd906330843cbad92018f5b98b4fe5", "Chuckwalla");
        BESTIARY_HASH_MAP.put("f4c4f8e5fce1ec2d299cb8a395792ecddc497a1d8af86faaa5e20373016c7225", "Driftling");
        BESTIARY_HASH_MAP.put("eacd215ccde2f677c7c144e2b698ff33ea06a87aaf468d05d1f0dc5ec2bdbfe8", "Hunter Reeves");
        BESTIARY_HASH_MAP.put("e4d0b57123433985923d6085dbea9ef760c2dbedd9c65ee9ba85a26148582459", "Hunter Jasper");
        BESTIARY_HASH_MAP.put("4538c727edceb5e29cc53d2b5c4231802ac5d7b9899d423ace81e7366e2e7ebc", "Hunter Luigi");
        BESTIARY_HASH_MAP.put("a89a76deedd42b410344100df2fa79b6eeac7e6f287745d656179368340ffade", "Flittler");
        BESTIARY_HASH_MAP.put("5dbaab74d1acd0abe9d04abe9928725de5d4495fcb63b647228caf6944c20800", "Rockmite");
        BESTIARY_HASH_MAP.put("a684e00e7394cb0c84c082e4dbc7c7e91ea67f6bc718a1aace7f99596b65d422", "Shyworm");
        BESTIARY_HASH_MAP.put("b4287b8a0a642dac535a6ee29459efd17d5cee1eb359e436bc8e7abba3da14b7", "Shyworm");
    }

    public static void fetchBestiaryAsync() {
        long now = System.currentTimeMillis();
        if (now - lastBestiaryFetchTime < 300000L) return;
        if (!isFetchingBestiary.compareAndSet(false, true)) return;
        lastBestiaryFetchTime = now;

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
                String[] endpoints = new String[]{
                    "https://api.bombo.dpdns.org/mod/bestiary",
                    "https://api.bombo.dpdns.org/bestiary",
                    "https://bombo.dpdns.org/api/bestiary"
                };
                for (String url : endpoints) {
                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).build();
                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 200 && resp.body() != null) {
                            String body = resp.body().trim();
                            if (body.startsWith("{")) {
                                JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                                    if (entry.getValue().isJsonPrimitive()) {
                                        String hash = entry.getKey().trim().toLowerCase(Locale.ROOT);
                                        String name = entry.getValue().getAsString().trim();
                                        if (!hash.isEmpty() && !name.isEmpty()) {
                                            BESTIARY_HASH_MAP.put(hash, name);
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                isFetchingBestiary.set(false);
            }
        }, "BestiaryFetchThread").start();
    }

    // Hideyho tracking
    private static Vec3 hideyhoInitialPos = null;
    private static Vec3 lastHideyhoPos = null;
    private static long lastHideyhoMoveTime = 0L;
    private static boolean hideyhoAccepted = false;
    private static boolean hideyhoTeleported = false;
    private static boolean hideyhoNavSent = false;

    // Scan trajectories recording
    public static boolean recordingTrajectory = false;
    public static final List<Vec3> recordedTrajectoryPoints = new ArrayList<>();

    public static void onChatMessage(String message) {
        if (message == null || message.isEmpty()) return;
        String clean = message.replaceAll("§.", "").trim();

        // Snoozle wall break trigger
        if (clean.contains("You destroyed the wall! Maybe something was hiding behind it...")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                BlockPos playerPos = mc.player.blockPosition();
                BlockPos closest = null;
                double closestDistSq = Double.MAX_VALUE;
                for (BlockPos wall : SNOOZLE_WALLS) {
                    double distSq = wall.distSqr(playerPos);
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closest = wall;
                    }
                }
                if (closest != null && closestDistSq <= 64.0) {
                    brokenWalls.add(closest);
                }
            }
        }

        // Hideyho auto-accept dialog
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.autoAcceptHideyho) {
            if (clean.contains("Select an option: [Sure] [No thanks...]") || clean.contains("Hehe, ok! Close your eyes") || (clean.contains("Hideyho") && clean.contains("[Sure]"))) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.connection.sendCommand("selectnpcoption hideyho r_4_1");
                    hideyhoAccepted = true;
                    hideyhoTeleported = false;
                    hideyhoNavSent = false;
                    hideyhoInitialPos = null;
                    lastHideyhoPos = null;
                    lastHideyhoMoveTime = System.currentTimeMillis();
                    if (s.critterDebug) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3BomboAddons§8] §aAuto-accepted Hideyho challenge (/selectnpcoption hideyho r_4_1)"));
                    }
                }
            }
        }

        // Escape warning: "The Snoozle escaped your Critter Capsule!"
        if ((clean.contains("escaped your Critter Capsule!") || clean.contains("dodged your critter capsule")) && !clean.contains("[!]")) {
            // Already logged or handled; do not call sendSystemMessage inside chat listener with matched substring to avoid recursion
        }
    }

    public static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!SafariLocation.inSafari()) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        fetchBestiaryAsync();

        SafariBiome playerBiome = SafariLocation.biome();
        SafariSession session = CritterSessionManager.currentOrLast();
        boolean uniqueMode = !"All".equalsIgnoreCase(s.critterTrackingMode);

        PoseStack poseStack = context.poseStack();
        OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        Vector3fc look = mc.gameRenderer.mainCamera().forwardVector();
        float startX = look.x();
        float startY = look.y();
        float startZ = look.z();

        net.minecraft.client.renderer.rendertype.RenderType lineType = RenderTypes.secondaryBlockOutline();

        // Track closest string tracer target
        Entity closestStringTarget = null;
        double minStringDistSq = Double.MAX_VALUE;

        // Track closest unique critter tracer target
        Entity closestCritterTarget = null;
        int closestCritterColor = 0;
        double minCritterDistSq = Double.MAX_VALUE;

        // Track sparkling mobs
        List<Entity> sparklingMobs = new ArrayList<>();

        // Hideyho tracking & stationary detector
        Entity hideyhoEntity = null;

        // 1. Group String / Cobweb entities by block and render glowing highlight
        if (s.critterHighlightStrings) {
            Map<BlockPos, List<Entity>> stringBlockMap = new HashMap<>();
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity == mc.player) continue;
                String name = entity.hasCustomName() ? entity.getCustomName().getString() : "";
                String cleanName = SafariLocation.strip(name).toLowerCase(Locale.ROOT);
                String typeName = entity.getType().toString().toLowerCase(Locale.ROOT);

                boolean isStringMob = cleanName.contains("honeybuzz") || cleanName.contains("string") || cleanName.contains("web") || typeName.contains("string") || typeName.contains("cobweb");
                if (!isStringMob && entity instanceof net.minecraft.world.entity.Display.ItemDisplay display) {
                    if (display.getItemStack() != null && display.getItemStack().getItem() == net.minecraft.world.item.Items.STRING) {
                        isStringMob = true;
                    }
                }

                if (isStringMob) {
                    // Filter out lobby/boat non-biome zones
                    if (playerBiome != null && !playerBiome.contains(entity.getX(), entity.getZ())) {
                        continue;
                    }
                    if (SafariBiome.fromCoordinates(entity.getX(), entity.getZ()) == null) {
                        continue;
                    }
                    BlockPos bPos = entity.blockPosition();
                    stringBlockMap.computeIfAbsent(bPos, k -> new ArrayList<>()).add(entity);
                }
            }

            for (Map.Entry<BlockPos, List<Entity>> entry : stringBlockMap.entrySet()) {
                List<Entity> list = entry.getValue();
                if (list.isEmpty()) continue;

                // Middle entity
                Entity centerEntity = list.get(list.size() / 2);
                double ex = centerEntity.getX() - camPos.x;
                double ey = centerEntity.getY() - camPos.y;
                double ez = centerEntity.getZ() - camPos.z;

                AABB box = new AABB(ex - 0.3, ey, ez - 0.3, ex + 0.3, ey + 0.6, ez + 0.3);
                collector.submitCustomGeometry(poseStack, lineType, (pose, vertexConsumer) ->
                    BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, 1.0f, 0.65f, 0.0f, 0.85f, 2.0f));

                double distSq = centerEntity.distanceToSqr(mc.player);
                if (distSq < minStringDistSq) {
                    minStringDistSq = distSq;
                    closestStringTarget = centerEntity;
                }
            }
        }

        // 2. Scan & Match Critter Entities
        Map<String, List<Entity>> foundCritterEntities = new HashMap<>();
        Map<String, Integer> countsThisTick = new HashMap<>();
        Set<Integer> countedEntityIds = new HashSet<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;

            // Check for Sparkling nametag
            String rawCustomName = entity.hasCustomName() ? entity.getCustomName().getString() : "";
            if (rawCustomName.contains("Sparkling") || (entity.getCustomName() != null && entity.getCustomName().getString().toLowerCase(Locale.ROOT).contains("sparkling"))) {
                sparklingMobs.add(entity);
            }

            // Exclude entities in non-biome / lobby center
            if (SafariBiome.fromCoordinates(entity.getX(), entity.getZ()) == null) {
                continue;
            }

            String critterName = identifyCritter(entity);
            if (critterName != null) {
                foundCritterEntities.computeIfAbsent(critterName, k -> new ArrayList<>()).add(entity);
                if (countedEntityIds.add(entity.getId())) {
                    countsThisTick.merge(critterName, 1, Integer::sum);
                }
                if (critterName.equalsIgnoreCase("Hideyho")) {
                    hideyhoEntity = entity;
                }
            }
        }

        for (Map.Entry<String, Integer> countEntry : countsThisTick.entrySet()) {
            nearbyFoundCounts.merge(countEntry.getKey(), countEntry.getValue(), Math::max);
        }

        // Hideyho jump / teleport detection: wait until moved > 2.0 blocks, then stationary for 0.8s
        if (hideyhoEntity != null) {
            Vec3 curPos = hideyhoEntity.position();
            long now = System.currentTimeMillis();

            if (hideyhoInitialPos == null) {
                hideyhoInitialPos = curPos;
                lastHideyhoPos = curPos;
                lastHideyhoMoveTime = now;
            } else {
                double distFromStart = curPos.distanceTo(hideyhoInitialPos);
                if (!hideyhoTeleported) {
                    if (distFromStart > 2.0) {
                        hideyhoTeleported = true;
                        lastHideyhoPos = curPos;
                        lastHideyhoMoveTime = now;
                    }
                } else {
                    double distMovedSq = lastHideyhoPos != null ? curPos.distanceToSqr(lastHideyhoPos) : 0.0;
                    if (distMovedSq > 0.3) {
                        lastHideyhoPos = curPos;
                        lastHideyhoMoveTime = now;
                    } else if (hideyhoAccepted && !hideyhoNavSent && (now - lastHideyhoMoveTime >= 800L)) {
                        hideyhoNavSent = true;
                        if ("shnav".equalsIgnoreCase(s.hideyhoNavMode)) {
                            int hx = (int) Math.floor(curPos.x);
                            int hy = (int) Math.floor(curPos.y);
                            int hz = (int) Math.floor(curPos.z);
                            mc.player.connection.sendCommand("shnav " + hx + " " + hy + " " + hz);
                            if (s.critterDebug) {
                                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3BomboAddons§8] §aTriggered /shnav " + hx + " " + hy + " " + hz + " for Hideyho (stationary for 0.8s)"));
                            }
                        }
                    }
                }
            }
        }

        // Render mob highlights & identify closest unique critter
        if (s.critterHighlightMobs) {
            Set<String> toHighlight = new HashSet<>();
            for (Critter critter : Critters.all()) {
                boolean completed = session != null && session.isComplete(critter, uniqueMode);
                if (completed) continue;

                if (playerBiome != null) {
                    if (critter.biome() == playerBiome) {
                        toHighlight.add(critter.name());
                    }
                } else {
                    toHighlight.add(critter.name());
                }
            }

            for (Map.Entry<String, List<Entity>> entry : foundCritterEntities.entrySet()) {
                String name = entry.getKey();
                List<Entity> entities = entry.getValue();
                if (entities.isEmpty()) continue;

                boolean shouldRender = toHighlight.contains(name);
                boolean isHideyho = name.equalsIgnoreCase("Hideyho");
                if (!shouldRender && !isHideyho) continue;

                // For Hideyho: only highlight/trace after he jumps/teleports to destination and settles
                if (isHideyho && hideyhoAccepted && (!hideyhoTeleported || (System.currentTimeMillis() - lastHideyhoMoveTime < 800L))) {
                    continue;
                }

                // Sort by distance to player
                entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));

                // EXACTLY 1 entity per unique missing species
                Entity mob = entities.get(0);
                double mx = mob.getX() - camPos.x;
                double my = mob.getY() - camPos.y;
                double mz = mob.getZ() - camPos.z;

                int col = 0x55FF55;
                Critter cObj = Critters.byName(name);
                if (cObj != null) col = cObj.biome().colour();
                if (isHideyho) col = 0x00AAFF; // Blue for Hideyho

                double distSq = mob.distanceToSqr(mc.player);
                if (isHideyho && "Tracer".equalsIgnoreCase(s.hideyhoNavMode)) {
                    // Prioritize Hideyho blue tracer if in tracer mode
                    closestCritterTarget = mob;
                    closestCritterColor = 0x00AAFF;
                    minCritterDistSq = -1.0;
                } else if (distSq < minCritterDistSq && minCritterDistSq >= 0) {
                    minCritterDistSq = distSq;
                    closestCritterTarget = mob;
                    closestCritterColor = col;
                }

                // Render highlight box around entity
                AABB mobBox = mob.getBoundingBox().move(-camPos.x, -camPos.y, -camPos.z);
                float hr = (float)(col >> 16 & 255) / 255.0f;
                float hg = (float)(col >> 8 & 255) / 255.0f;
                float hb = (float)(col & 255) / 255.0f;
                collector.submitCustomGeometry(poseStack, lineType, (pose, vertexConsumer) ->
                    BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, mobBox, hr, hg, hb, 0.85f, 2.0f));

                BomboRenderUtils.drawText(poseStack, collector, "§e" + name + " §7(" + String.format("%.1f", mob.distanceTo(mc.player)) + "m)", (float) mx, (float) my + (float) mob.getBbHeight() + 0.4f, (float) mz, 0xFFFFFFFF, 0.03f, true, true);
            }
        }

        // 3. Bee Nests Highlight (Forest only, NOT regular beehive)
        if (s.critterHighlightBeeNests && (playerBiome == null || playerBiome == SafariBiome.FOREST)) {
            boolean hasHoneybug = session != null && session.isComplete(Critters.byName("Honeybug"), uniqueMode);
            if (!hasHoneybug) {
                List<BlockPos> validNests = new ArrayList<>();
                for (BlockPos bPos : BEE_NESTS) {
                    if (clearedBeeNests.contains(bPos)) continue;
                    // Ensure block is specifically bee_nest
                    net.minecraft.world.level.block.state.BlockState bs = mc.level.getBlockState(bPos);
                    if (bs.is(net.minecraft.world.level.block.Blocks.BEE_NEST)) {
                        validNests.add(bPos);
                    }
                }
                validNests.sort(Comparator.comparingDouble(p -> p.distSqr(mc.player.blockPosition())));

                if (!validNests.isEmpty()) {
                    BlockPos p = validNests.get(0);
                    double bx = p.getX() - camPos.x;
                    double by = p.getY() - camPos.y;
                    double bz = p.getZ() - camPos.z;
                    AABB nBox = new AABB(bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0);
                    collector.submitCustomGeometry(poseStack, lineType, (pose, vertexConsumer) ->
                        BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, nBox, 1.0f, 0.84f, 0.0f, 0.85f, 2.0f));

                    BomboRenderUtils.drawText(poseStack, collector, "§6Bee Nest", (float) bx + 0.5f, (float) by + 1.2f, (float) bz + 0.5f, 0xFFFFFFFF, 0.03f, true, true);
                }
            }
        }

        // 4. Snoozle Breakable Walls (Cavern)
        if (s.critterHighlightWalls && (playerBiome == null || playerBiome == SafariBiome.CAVERN)) {
            boolean hasSnoozle = session != null && session.isComplete(Critters.byName("Snoozle"), uniqueMode);
            if (!hasSnoozle) {
                List<BlockPos> validWalls = new ArrayList<>();
                for (BlockPos wall : SNOOZLE_WALLS) {
                    if (brokenWalls.contains(wall)) continue;
                    validWalls.add(wall);
                }
                validWalls.sort(Comparator.comparingDouble(p -> p.distSqr(mc.player.blockPosition())));

                if (!validWalls.isEmpty()) {
                    BlockPos p = validWalls.get(0);
                    double wx = p.getX() - camPos.x;
                    double wy = p.getY() - camPos.y;
                    double wz = p.getZ() - camPos.z;
                    AABB wBox = new AABB(wx, wy, wz, wx + 1.0, wy + 1.0, wz + 1.0);
                    collector.submitCustomGeometry(poseStack, lineType, (pose, vertexConsumer) ->
                        BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, wBox, 0.4f, 0.7f, 1.0f, 0.8f, 2.0f));

                    BomboRenderUtils.drawText(poseStack, collector, "§bBreakable Wall (Snoozle)", (float) wx + 0.5f, (float) wy + 1.2f, (float) wz + 0.5f, 0xFFFFFFFF, 0.03f, true, true);
                }
            }
        }

        // 5. Draw 1 Tracer to closest string
        if (s.critterHighlightStrings && closestStringTarget != null) {
            double sx = closestStringTarget.getX() - camPos.x;
            double sy = closestStringTarget.getY() - camPos.y + (closestStringTarget.getBbHeight() * 0.5);
            double sz = closestStringTarget.getZ() - camPos.z;

            collector.submitCustomGeometry(poseStack, lineType, (pose, vertexConsumer) ->
                BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, (float) sx, (float) sy, (float) sz, 1.0f, 0.65f, 0.0f, 1.0f, 2.5f));
        }

        // 6. Draw 1 Tracer to closest unique critter (chest/center height)
        if (s.critterTracers && closestCritterTarget != null) {
            double tx = closestCritterTarget.getX() - camPos.x;
            double ty = closestCritterTarget.getY() - camPos.y + (closestCritterTarget.getBbHeight() * 0.5);
            double tz = closestCritterTarget.getZ() - camPos.z;

            float tr = (float)(closestCritterColor >> 16 & 255) / 255.0f;
            float tg = (float)(closestCritterColor >> 8 & 255) / 255.0f;
            float tb = (float)(closestCritterColor & 255) / 255.0f;

            collector.submitCustomGeometry(poseStack, lineType, (pose, vertexConsumer) ->
                BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, (float) tx, (float) ty, (float) tz, tr, tg, tb, 1.0f, 2.5f));
        }

        // 7. Sparkling Mobs Tracer & Highlight (Through walls, customizable purple)
        if (!sparklingMobs.isEmpty()) {
            int sparkColor = BomboRenderUtils.colorNameToHex(s.sparklingTracerColor != null ? s.sparklingTracerColor : "LIGHT_PURPLE");
            float sr = (float)(sparkColor >> 16 & 255) / 255.0f;
            float sg = (float)(sparkColor >> 8 & 255) / 255.0f;
            float sb = (float)(sparkColor & 255) / 255.0f;

            sparklingMobs.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
            Entity sparkMob = sparklingMobs.get(0);
            double spX = sparkMob.getX() - camPos.x;
            double spY = sparkMob.getY() - camPos.y + (sparkMob.getBbHeight() * 0.5);
            double spZ = sparkMob.getZ() - camPos.z;

            collector.submitCustomGeometry(poseStack, lineType, (pose, vertexConsumer) ->
                BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, (float) spX, (float) spY, (float) spZ, sr, sg, sb, 1.0f, 3.0f));

            BomboRenderUtils.drawText(poseStack, collector, "§d§l✦ Sparkling ✦", (float) spX, (float) (spY + sparkMob.getBbHeight() * 0.5 + 0.4f), (float) spZ, 0xFFFF55FF, 0.035f, true, true);
        }
    }

    public static String identifyCritter(Entity entity) {
        if (entity == null) return null;

        // Check custom name first
        if (entity.hasCustomName()) {
            String cleanName = SafariLocation.strip(entity.getCustomName().getString());
            Critter byName = Critters.byName(cleanName);
            if (byName != null) return byName.name();
            if (cleanName.equalsIgnoreCase("Hideyho")) return "Hideyho";
        }

        // Check head texture hash
        String headTex = TargetPests.getHeadTextureValue(entity);
        if (headTex != null) {
            String hash = TargetPests.extractTextureHash(headTex);
            if (hash != null) {
                String matched = BESTIARY_HASH_MAP.get(hash);
                if (matched != null) return matched;
            }
        }

        // Specific entity type recognition
        String simpleName = entity.getClass().getSimpleName();
        String typeKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();

        if (typeKey.equalsIgnoreCase("goat") || simpleName.equalsIgnoreCase("Goat")) return "Billygoat";
        if (typeKey.equalsIgnoreCase("dolphin") || simpleName.equalsIgnoreCase("Dolphin")) return "Nozzlenose";
        if (typeKey.equalsIgnoreCase("polar_bear") || simpleName.equalsIgnoreCase("PolarBear")) return "Polaris";
        if (typeKey.equalsIgnoreCase("glow_squid") || typeKey.equalsIgnoreCase("squid") || simpleName.contains("Squid")) return "Shuddersquid";
        if (typeKey.equalsIgnoreCase("tropical_fish") || simpleName.equalsIgnoreCase("TropicalFish")) {
            if (entity.getX() <= -40 && entity.getZ() <= 0) return "Tepid";
            return "Cavernfish";
        }
        if (typeKey.equalsIgnoreCase("ravager") || simpleName.equalsIgnoreCase("Ravager")) return "Wumpa";
        if (typeKey.equalsIgnoreCase("cave_spider") || simpleName.equalsIgnoreCase("CaveSpider")) return "Areita";
        if (typeKey.equalsIgnoreCase("bat") || simpleName.equalsIgnoreCase("Bat")) {
            if (entity.getZ() < 0) return "Bloodbat";
            return "Flittler";
        }
        if (typeKey.equalsIgnoreCase("warden") || simpleName.equalsIgnoreCase("Warden")) return "Doomspiral";
        if (typeKey.equalsIgnoreCase("endermite") || simpleName.equalsIgnoreCase("Endermite")) return "Litterbug";
        if (typeKey.equalsIgnoreCase("phantom") || simpleName.equalsIgnoreCase("Phantom")) return "Solsnatcher";
        if (typeKey.equalsIgnoreCase("shulker") || simpleName.equalsIgnoreCase("Shulker")) {
            if (entity.getZ() < 0) return "Hideonwall";
            return "Hideonfloor";
        }
        if (typeKey.equalsIgnoreCase("parrot") || simpleName.equalsIgnoreCase("Parrot")) {
            if (me.bombo.bomboaddons.EntityVariantHelper.matchesVariant(entity, "blue")) return "Bluebird";
            if (me.bombo.bomboaddons.EntityVariantHelper.matchesVariant(entity, "green")) return "Parakeet";
            return "Macaw";
        }
        if (typeKey.equalsIgnoreCase("panda") || simpleName.equalsIgnoreCase("Panda")) return "Fluffling";
        if (typeKey.equalsIgnoreCase("fox") || simpleName.equalsIgnoreCase("Fox")) return "Foxtrot";
        if (typeKey.equalsIgnoreCase("bee") || simpleName.equalsIgnoreCase("Bee")) return "Honeybug";
        if (typeKey.equalsIgnoreCase("frog") || simpleName.equalsIgnoreCase("Frog")) return "Treefrog";
        if (typeKey.equalsIgnoreCase("vex") || simpleName.equalsIgnoreCase("Vex")) return "Gemzie";
        if (typeKey.equalsIgnoreCase("armadillo") || simpleName.equalsIgnoreCase("Armadillo")) return "Scrappy";
        if (typeKey.equalsIgnoreCase("sniffer") || simpleName.equalsIgnoreCase("Sniffer")) return "Snoozle";
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            if ("04e4d7c6-78f1-262b-b86e-d9256e5dcd93".equalsIgnoreCase(player.getStringUUID()) || "Hideyho".equalsIgnoreCase(player.getName().getString())) {
                return "Hideyho";
            }
        }

        return null;
    }

    public static void printDebugReport() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        SafariSession session = CritterSessionManager.currentOrLast();
        if (session == null || session.isEmpty()) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3BomboAddons§8] §cNo active Critter Safari session."));
            return;
        }

        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3BomboAddons§8] §6=== Safari Session Report (Hover for Details) ==="));
        long elapsedSec = session.elapsedMillis(System.currentTimeMillis()) / 1000;
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Duration: §e" + (elapsedSec / 60) + "m " + (elapsedSec % 60) + "s"));

        Map<String, Map<SafariBiome, Integer>> uniques = session.uniquePerPlayer();
        Map<String, Integer> totals = session.totalPerPlayer();

        if (totals.isEmpty()) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7- No players tracked yet."));
        } else {
            for (Map.Entry<String, Integer> entry : totals.entrySet()) {
                String player = entry.getKey();
                int total = entry.getValue();
                Map<SafariBiome, Integer> biomeMap = uniques.getOrDefault(player, Collections.emptyMap());
                int uniqueCount = biomeMap.values().stream().mapToInt(Integer::intValue).sum();

                SafariBiome topBiome = null;
                int maxCount = -1;
                for (SafariBiome b : SafariBiome.values()) {
                    int count = biomeMap.getOrDefault(b, 0);
                    if (count > maxCount) {
                        maxCount = count;
                        topBiome = b;
                    }
                }

                // Detailed hover for Uniques (lists mob names per biome)
                StringBuilder uniquesHover = new StringBuilder("§6" + player + "'s Uniques Breakdown:\n");
                for (SafariBiome b : SafariBiome.values()) {
                    List<String> mobNames = new ArrayList<>();
                    for (Critter c : Critters.inBiome(b)) {
                        if (session.catchersOf(c).contains(player)) {
                            mobNames.add(c.name());
                        }
                    }
                    uniquesHover.append("§e").append(b.displayName()).append(" (").append(mobNames.size()).append("): §f")
                        .append(mobNames.isEmpty() ? "§7None" : String.join(", ", mobNames))
                        .append("\n");
                }

                // Detailed hover for Total Catches
                StringBuilder caughtHover = new StringBuilder("§6" + player + "'s Total Catches Breakdown:\n");
                for (SafariBiome b : SafariBiome.values()) {
                    List<String> mobDetails = new ArrayList<>();
                    int biomeCatchCount = 0;
                    for (Critter c : Critters.inBiome(b)) {
                        if (session.catchersOf(c).contains(player)) {
                            int count = session.partyCatches(c);
                            biomeCatchCount += count;
                            mobDetails.add(c.name() + " (" + count + ")");
                        }
                    }
                    caughtHover.append("§a").append(b.displayName()).append(" [").append(biomeCatchCount).append(" caught]: §f")
                        .append(mobDetails.isEmpty() ? "§7None" : String.join(", ", mobDetails))
                        .append("\n");
                }

                String biomeName = topBiome != null ? topBiome.displayName() : "Unknown";

                net.minecraft.network.chat.MutableComponent baseLine = net.minecraft.network.chat.Component.literal("§7- §b" + player + " §7| Biome: §a" + biomeName + " §7| ");
                
                net.minecraft.network.chat.MutableComponent caughtPart = net.minecraft.network.chat.Component.literal("Caught: §e" + total + " ");
                caughtPart.setStyle(net.minecraft.network.chat.Style.EMPTY.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal(caughtHover.toString().trim()))));

                net.minecraft.network.chat.MutableComponent uniquePart = net.minecraft.network.chat.Component.literal("§7(Uniques: §6" + uniqueCount + "§7)");
                uniquePart.setStyle(net.minecraft.network.chat.Style.EMPTY.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal(uniquesHover.toString().trim()))));

                baseLine.append(caughtPart).append(uniquePart);
                mc.player.sendSystemMessage(baseLine);
            }
        }
    }
}
