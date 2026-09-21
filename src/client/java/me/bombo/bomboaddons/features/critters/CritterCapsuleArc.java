package me.bombo.bomboaddons.features.critters;

import com.mojang.blaze3d.vertex.PoseStack;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.OrderedSubmitNodeCollector;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CritterCapsuleArc {

    public static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.critterCapsuleTrajectory) return;

        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();

        boolean holdingCapsule = isCapsule(mainHand) || isCapsule(offHand);
        if (!holdingCapsule) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        PoseStack poseStack = context.poseStack();
        OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());

        // Physics trajectory parameters for thrown capsule
        Vec3 playerEye = mc.player.getEyePosition();
        Vec3 lookAngle = mc.player.getLookAngle();
        double speed = 1.6;
        double vx = lookAngle.x * speed;
        double vy = lookAngle.y * speed;
        double vz = lookAngle.z * speed;
        double gravity = 0.035;
        double drag = 0.99;

        double curX = playerEye.x;
        double curY = playerEye.y - 0.1;
        double curZ = playerEye.z;

        List<Vec3> arcPoints = new ArrayList<>();
        arcPoints.add(new Vec3(curX, curY, curZ));

        Entity hitCritter = null;
        Vec3 landingPos = null;

        for (int step = 0; step < 120; step++) {
            double nextX = curX + vx;
            double nextY = curY + vy;
            double nextZ = curZ + vz;

            vy -= gravity;
            vx *= drag;
            vy *= drag;
            vz *= drag;

            Vec3 startSeg = new Vec3(curX, curY, curZ);
            Vec3 endSeg = new Vec3(nextX, nextY, nextZ);
            arcPoints.add(endSeg);

            // Check collision with world blocks
            net.minecraft.world.phys.BlockHitResult blockHit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                startSeg, endSeg,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mc.player
            ));

            if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                landingPos = blockHit.getLocation();
                break;
            }

            // Check collision with critters
            AABB stepBox = new AABB(curX, curY, curZ, nextX, nextY, nextZ).inflate(0.5);
            for (Entity e : mc.level.getEntities(mc.player, stepBox)) {
                String cName = CritterSafariEngine.identifyCritter(e);
                if (cName != null) {
                    hitCritter = e;
                    landingPos = endSeg;
                    break;
                }
            }

            if (hitCritter != null) break;

            curX = nextX;
            curY = nextY;
            curZ = nextZ;
        }

        boolean willHit = hitCritter != null;
        float r = willHit ? 0.2f : 1.0f;
        float g = willHit ? 1.0f : 0.6f;
        float b = willHit ? 0.2f : 0.0f;

        // Render trajectory line segments
        for (int i = 0; i < arcPoints.size() - 1; i++) {
            Vec3 p1 = arcPoints.get(i);
            Vec3 p2 = arcPoints.get(i + 1);
            float sx = (float) (p1.x - camPos.x);
            float sy = (float) (p1.y - camPos.y);
            float sz = (float) (p1.z - camPos.z);
            float ex = (float) (p2.x - camPos.x);
            float ey = (float) (p2.y - camPos.y);
            float ez = (float) (p2.z - camPos.z);

            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) ->
                BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, sx, sy, sz, ex, ey, ez, r, g, b, 0.9f, 2.5f));
        }

        // Render landing indicator
        if (landingPos != null) {
            double lx = landingPos.x - camPos.x;
            double ly = landingPos.y - camPos.y;
            double lz = landingPos.z - camPos.z;

            AABB landBox = new AABB(lx - 0.25, ly - 0.05, lz - 0.25, lx + 0.25, ly + 0.25, lz + 0.25);
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) ->
                BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, landBox, r, g, b, 0.9f, 2.0f));

            if (willHit) {
                String hitName = CritterSafariEngine.identifyCritter(hitCritter);
                BomboRenderUtils.drawText(poseStack, collector, "§a✔ Hits " + hitName + "!", (float) lx, (float) ly + 0.5f, (float) lz, 0xFF55FF55, 0.03f, true, true);
            }
        }
    }

    public static boolean recordFlightDiagnostics = false;
    public static final List<String> flightDiagnosticLogs = new ArrayList<>();
    private static final Map<Integer, List<Vec3>> trackedProjectiles = new HashMap<>();

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!recordFlightDiagnostics) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.projectile.ThrowableProjectile || e.getType().toString().contains("projectile") || e.getType().toString().contains("snowball") || e.getType().toString().contains("egg")) {
                int id = e.getId();
                Vec3 p = e.position();
                Vec3 delta = e.getDeltaMovement();
                List<Vec3> history = trackedProjectiles.computeIfAbsent(id, k -> new ArrayList<>());
                history.add(p);
                if (history.size() > 1) {
                    double speed = delta.length();
                    String log = String.format("Tick %d: pos=(%.2f, %.2f, %.2f) vel=(%.3f, %.3f, %.3f) speed=%.3f", history.size(), p.x, p.y, p.z, delta.x, delta.y, delta.z, speed);
                    flightDiagnosticLogs.add(log);
                    if (mc.player != null && history.size() % 5 == 0) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3BomboAddons§8] §7" + log));
                    }
                }
            }
        }
    }

    private static boolean isCapsule(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String name = stack.getHoverName().getString();
        return name.contains("Critter Capsule") || name.contains("Masterful Critter Capsule");
    }
}
