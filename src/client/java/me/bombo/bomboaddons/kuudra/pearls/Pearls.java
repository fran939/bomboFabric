package me.bombo.bomboaddons.kuudra.pearls;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pearls {
    private static final int[][] PEARL_DELAY = {
            //  0    1     2     3     4     5
            {   0,  3000, 4000, 5000, 6000, 6000 }, // No tali
            {   0,  2750, 3750, 4500, 5500, 5500 }, // T1 tali
            {   0,  2500, 3250, 4000, 5000, 5000 }, // T2 tali
            {   0,  2250, 3000, 3500, 4250, 4250 }  // T3 tali
    };

    public static int cachedInitialDelay = 100;
    public static int cachedDoubleDelay = 200;
    public static float textSizeSky = 1.5f;
    public static float textSizeFlat = 1.125f;
    public static float waypointSizeSky = 0.5f;
    public static float waypointSizeFlat = 0.375f;
    public static float timerOffset = 1.0f;

    private static final List<PearlRenderData> pearlSolutions = new ArrayList<>();
    private static final List<Vec3> currentSupplies = new ArrayList<>();
    private static final Pattern progressPattern = Pattern.compile("\\[(.*)]\\s*(\\d+)%");

    private static long progressStartTime = -1;
    private static boolean trackingPickup = false;
    private static Vec3 mySupply = null;
    private static Vec3 myDouble = null;
    private static long lastTitleEvent = -1;
    private static int tickCounter = 0;

    public static class PearlHUDText {
        public String text;
        public float x;
        public float y;
        public int color;
        public PearlHUDText(String text, float x, float y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }
    public static final List<PearlHUDText> HUD_TEXTS = new ArrayList<>();

    public static void onWorldUnload() {
        currentSupplies.clear();
        pearlSolutions.clear();
        HUD_TEXTS.clear();
        mySupply = null;
        myDouble = null;
        resetTracking();
    }

    public static void render(LevelRenderContext context) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.pearlCalculator) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (!KuudraUtils.inKuudra()) return;

        HUD_TEXTS.clear();

        if (pearlSolutions.isEmpty()) {
            // Even if empty, draw the static test so they can check it
        }

        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.poseStack();
        net.minecraft.client.renderer.OrderedSubmitNodeCollector collector = context.submitNodeCollector();
        if (collector == null) return;

        // Static test render requested by user (only when kuudraDebug is enabled)
        if (s.kuudraDebug) {
            double testRelX = -102.0 - camPos.x;
            double testRelY = 79.0 - camPos.y;
            double testRelZ = -179.0 - camPos.z;
            double testDist = Math.sqrt(testRelX*testRelX + testRelY*testRelY + testRelZ*testRelZ);
            double testProjScale = 1.0;
            if (testDist > 0.2) testProjScale = 0.2 / testDist;
            double testScaledX = testRelX * testProjScale;
            double testScaledY = testRelY * testProjScale;
            double testScaledZ = testRelZ * testProjScale;
            double testScaledHs = 0.25 * testProjScale;

            AABB testBox = new AABB(
                testScaledX - testScaledHs,
                testScaledY - testScaledHs,
                testScaledZ - testScaledHs,
                testScaledX + testScaledHs,
                testScaledY + testScaledHs,
                testScaledZ + testScaledHs
            );
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, testBox, 0.0f, 1.0f, 1.0f, 1.0f, 2.0f); // Cyan box
            });

            // Static test projection to HUD
            org.joml.Vector4f testScreenPos = new org.joml.Vector4f(
                -102.0f,
                80.0f,
                -179.0f,
                1.0f
            );
            testScreenPos.mul(poseStack.last().pose());
            testScreenPos.mul(context.levelState().cameraRenderState.projectionMatrix);
            if (debugRenderCounter % 100 == 0) {
                if (mc.gui != null && mc.gui != null && mc.gui.getChat() != null) {
                    mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[KuudraDebug] testScreenPos: x=" + testScreenPos.x + ", y=" + testScreenPos.y + ", z=" + testScreenPos.z + ", w=" + testScreenPos.w
                    ));
                }
            }
            if (testScreenPos.w > 0.0f) {
                float ndcX = testScreenPos.x / testScreenPos.w;
                float ndcY = testScreenPos.y / testScreenPos.w;
                int screenWidth = mc.getWindow().getGuiScaledWidth();
                int screenHeight = mc.getWindow().getGuiScaledHeight();
                float screenX = (ndcX + 1.0f) * 0.5f * screenWidth;
                float screenY = (1.0f - ndcY) * 0.5f * screenHeight;

                HUD_TEXTS.add(new PearlHUDText(
                    "§b§lSTATIC TEST 300ms",
                    screenX,
                    screenY,
                    0xFF00FFFF
                ));
            }
        }

        boolean hasSupplies = isHoldingSupplies();

        for (PearlRenderData data : pearlSolutions) {
            boolean isMySupply = (mySupply != null && vecEquals(mySupply, data.target));
            boolean isDouble = data.isDouble;
            
            // Dynamic color: Green if timer <= 0 or holding supply, otherwise Red for countdown
            boolean isReady = (data.time <= 0 || hasSupplies) && (isMySupply || isDouble);
            
            float r = isReady ? 0.0f : 1.0f;
            float g = isReady ? 1.0f : 0.0f;
            float b = 0.0f;
            float a = 1.0f;

            // Non-my supplies (all other waypoints when showAll is active) should be red unless they are also ready
            if (!isMySupply && !isDouble) {
                boolean isOtherReady = (data.time <= 0 || hasSupplies);
                r = isOtherReady ? 0.0f : 1.0f;
                g = isOtherReady ? 1.0f : 0.0f;
            }

            Vec3 pos = data.solution;
            float waypointSize = data.isSky ? waypointSizeSky : waypointSizeFlat;
            float textScale = data.isSky ? textSizeSky : textSizeFlat;

            double hs = waypointSize / 2.0;
            double relX = pos.x - camPos.x;
            double relY = pos.y - camPos.y;
            double relZ = pos.z - camPos.z;

            // Perspective scaling trick to see through walls
            float dist = (float) Math.sqrt(relX * relX + relY * relY + relZ * relZ);
            float scale = 1.0f;
            if (dist > 0.2f) {
                scale = 0.2f / dist;
            }
            double scaledX = relX * scale;
            double scaledY = relY * scale;
            double scaledZ = relZ * scale;
            double scaledHs = hs * scale;

            AABB box = new AABB(
                scaledX - scaledHs, scaledY - scaledHs, scaledZ - scaledHs,
                scaledX + scaledHs, scaledY + scaledHs, scaledZ + scaledHs
            );

            final float finalR = r;
            final float finalG = g;

            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, finalR, finalG, b, a, 1.5f);
            });

            if (s.showTimer) {
                int textColor = isReady ? 0xFF00FF00 : 0xFFFF0000;
                org.joml.Vector4f screenPos = new org.joml.Vector4f(
                    (float) pos.x,
                    (float) (pos.y + (data.isSky ? 1.5f : 1.0f)),
                    (float) pos.z,
                    1.0f
                );
                screenPos.mul(poseStack.last().pose());
                screenPos.mul(context.levelState().cameraRenderState.projectionMatrix);

                if (screenPos.w > 0.0f) {
                    float ndcX = screenPos.x / screenPos.w;
                    float ndcY = screenPos.y / screenPos.w;

                    int screenWidth = mc.getWindow().getGuiScaledWidth();
                    int screenHeight = mc.getWindow().getGuiScaledHeight();

                    float screenX = (ndcX + 1.0f) * 0.5f * screenWidth;
                    float screenY = (1.0f - ndcY) * 0.5f * screenHeight;

                    HUD_TEXTS.add(new PearlHUDText(
                        data.cachedDisplay,
                        screenX,
                        screenY,
                        textColor
                    ));
                }
            }
        }

        debugRenderCounter++;
        if (s.kuudraDebug && debugRenderCounter % 100 == 0) {
            if (mc.gui != null && mc.gui != null && mc.gui.getChat() != null) {
                mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7[KuudraDebug] Pearls rendering " + pearlSolutions.size() + " solutions. MySupply=" + mySupply + ", HUD_TEXTS=" + HUD_TEXTS.size()
                ));
                for (int i = 0; i < pearlSolutions.size(); i++) {
                    PearlRenderData d = pearlSolutions.get(i);
                    mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[KuudraDebug] #" + i + " " + d.cachedDisplay + " at " + String.format("%.1f, %.1f, %.1f", d.solution.x, d.solution.y, d.solution.z)
                    ));
                }
            }
        }
    }

    private static int debugRenderCounter = 0;

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (!KuudraUtils.inKuudra()) {
            onWorldUnload();
            return;
        }

        if (lastTitleEvent != -1 && System.currentTimeMillis() - lastTitleEvent > 750) {
            resetTracking();
        }

        tickCounter++;
        if (tickCounter % 4 != 0) {
            boolean hasSupplies = isHoldingSupplies();
            for (PearlRenderData data : pearlSolutions) {
                data.updateDisplay(hasSupplies, progressStartTime, trackingPickup);
            }
            return;
        }

        currentSupplies.clear();
        currentSupplies.addAll(KuudraUtils.getAllUncompletedSupplies());

        BomboConfig.Settings s = BomboConfig.get();
        if (!s.pearlCalculator) return;

        Vec3 eyePos = mc.player.getEyePosition();
        if (eyePos == null) return;

        PickupSpot closestSpot = PickupSpot.getClosestSpot(eyePos);
        mySupply = getMyDropSpot(eyePos, closestSpot);
        pearlSolutions.clear();
        boolean hasSupplies = isHoldingSupplies();

        if (s.showAll) {
            for (Vec3 targetSupply : currentSupplies) {
                if (s.showSkyPearls) {
                    PearlSolution sky = TrajectorySolver.solvePearl(true, eyePos, targetSupply);
                    tryAddPearl(sky, targetSupply, false, true, hasSupplies);
                }
                if (s.showFlatPearls) {
                    PearlSolution flat = TrajectorySolver.solvePearl(false, eyePos, targetSupply);
                    tryAddPearl(flat, targetSupply, false, false, hasSupplies);
                }
            }
        } else {
            if (mySupply != null) {
                if (s.showSkyPearls) {
                    PearlSolution sky = TrajectorySolver.solvePearl(true, eyePos, mySupply);
                    tryAddPearl(sky, mySupply, false, true, hasSupplies);
                }
                if (s.showFlatPearls) {
                    PearlSolution flat = TrajectorySolver.solvePearl(false, eyePos, mySupply);
                    tryAddPearl(flat, mySupply, false, false, hasSupplies);
                }
            }
        }

        myDouble = null;
        if (s.showDoublePearls && closestSpot != PickupSpot.NONE) {
            for (DoublePearl pearlSpot : DoublePearlRegistry.getRoutesFrom(closestSpot)) {
                Vec3 targetSpot = pearlSpot.getLocation();
                if (myDouble == null) myDouble = targetSpot;
                PearlSolution doubleSol = TrajectorySolver.solvePearl(true, eyePos, targetSpot);
                tryAddPearl(doubleSol, targetSpot, true, true, hasSupplies);
            }
        }
    }

    private static void tryAddPearl(PearlSolution sol, Vec3 target, boolean isDouble, boolean isSky, boolean hasSupplies) {
        if (sol == null || sol.solution == null) return;

        PearlRenderData data = new PearlRenderData(sol, target, isDouble, isSky, hasSupplies, progressStartTime, trackingPickup);
        pearlSolutions.add(data);
    }

    public static void onTitleReceived(String raw) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.pearlCalculator) return;

        // Strip color and formatting codes so they don't break the progress regex match
        String clean = raw.replaceAll("(?i)§.", "");

        Matcher matcher = progressPattern.matcher(clean);
        if (!matcher.find()) return;

        int percent = Integer.parseInt(matcher.group(2));
        if (s.kuudraDebug) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null && mc.gui != null && mc.gui.getChat() != null) {
                mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("§7[KuudraDebug] Matched Progress: §a" + percent + "%"));
            }
        }

        long now = System.currentTimeMillis();
        lastTitleEvent = now;
        if (percent >= 0 && percent < 100) {
            if (!trackingPickup) {
                trackingPickup = true;
            }
            // Each percent of progress takes ~30ms of a 3000ms supply crate grab
            progressStartTime = now - (percent * 30L);
        } else if (percent == 100) {
            resetTracking();
        }
    }

    public static void onChatMessage(String message) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.pearlCalculator) return;

        if ("You moved and the Chest slipped out of your hands!".equals(message)
                || " ☠ You were killed by Kuudra Follower and became a ghost.".equals(message)
                || "You retrieved some of Elle's supplies from the Lava!".equals(message)) {
            resetTracking();
        }
    }

    private static void resetTracking() {
        trackingPickup = false;
        progressStartTime = -1;
        lastTitleEvent = -1;
    }

    private static Vec3 getMyDropSpot(Vec3 vector, PickupSpot pickUpSpot) {
        Supply[] supplies = KuudraUtils.getSupplies();
        SupplySpot noPre = NoPre.getNoPreSpot();

        boolean noPreIsNothing = false;
        if (noPre != null) {
            Supply noPreSupply = getSupply(supplies, noPre.ordinal());
            noPreIsNothing = (noPreSupply != null && noPreSupply.getStatus() == SupplyStatus.NOTHING);
        }

        Vec3 fallback = noPreIsNothing ? noPre.getLocation() : vector;

        if (pickUpSpot == PickupSpot.SQUARE && noPreIsNothing) return noPre.getLocation();

        Supply supply = null;
        switch (pickUpSpot) {
            case SHOP:     supply = getSupply(supplies, 0); break;
            case X:        supply = getSupply(supplies, 1); break;
            case X_CANNON: supply = getSupply(supplies, 2); break;
            case EQUALS:   supply = getSupply(supplies, 3); break;
            case SLASH:    supply = getSupply(supplies, 4); break;
            case TRIANGLE: supply = getSupply(supplies, 5); break;
            default:       break;
        }

        if (supply != null && supply.getStatus() == SupplyStatus.NOTHING) return supply.getSpot().getLocation();
        return getClosestSupply(vector).orElse(fallback);
    }

    private static Supply getSupply(Supply[] supplies, int index) {
        if (supplies == null || index < 0 || index >= supplies.length) return null;
        return supplies[index];
    }

    private static Optional<Vec3> getClosestSupply(Vec3 vector) {
        double minDistSq = Double.MAX_VALUE;
        Vec3 closest = null;

        for (Supply supply : KuudraUtils.getSupplies()) {
            if (supply.getStatus() != SupplyStatus.NOTHING) continue;
            Vec3 loc = supply.getSpot().getLocation();
            double distSq = vector.distanceToSqr(loc);
            if (distSq < minDistSq) {
                minDistSq = distSq;
                closest = loc;
            }
        }

        return Optional.ofNullable(closest);
    }

    private static String lastHeldItemName = "";

    public static boolean isHoldingSupplies() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var stack = mc.player.getMainHandItem();
        String name = stack.isEmpty() ? "" : stack.getHoverName().getString();

        if (BomboConfig.get().kuudraDebug && !name.equals(lastHeldItemName)) {
            lastHeldItemName = name;
            if (mc.gui != null && mc.gui != null && mc.gui.getChat() != null) {
                mc.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("§7[KuudraDebug] Held Item changed: §e" + (name.isEmpty() ? "Empty" : name)));
            }
        }

        return name != null && !name.isEmpty() && (name.contains("Supply") || name.contains("Supplies") || name.contains("Chest") || name.contains("Crate"));
    }

    public static int getPearlDelay(int talismanTier, int kuudraTier) {
        if (talismanTier < 0) talismanTier = 0;
        if (talismanTier > 3) talismanTier = 3;
        if (kuudraTier < 1) kuudraTier = 1;
        if (kuudraTier > 5) kuudraTier = 5;

        return PEARL_DELAY[talismanTier][kuudraTier];
    }

    private static boolean vecEquals(Vec3 a, Vec3 b) {
        if (a == null || b == null) return false;
        return Math.abs(a.x - b.x) < 0.1 && Math.abs(a.y - b.y) < 0.1 && Math.abs(a.z - b.z) < 0.1;
    }
}
