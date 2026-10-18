package me.bombo.bomboaddons.features.kuudra;

import com.mojang.blaze3d.vertex.PoseStack;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.OrderedSubmitNodeCollector;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Kuudra Stun and Kill Helper:
 * - Dynamic Stun Waypoint: Calculates dynamic aim waypoint outside Kuudra so the player's
 *   crosshair is pre-aligned for an instant stun the split second they are teleported into the belly.
 * - Belly Pod Highlight: Outlines target stun pod(s) with wireframe 3D box, beacon beam, and status tag.
 * - Auto Close Shop: Automatically closes shop container after purchasing Human Cannonball.
 * - Backbone Hit Tracker: Notifies when Bone Boomerang strikes target, displaying held weapon, armor, and plays sound.
 */
public class KuudraStunKillHelper {

    public enum StunPod {
        LEFT("Left Pod", -153.0, 27.0, -173.0),
        RIGHT("Right Pod", -168.0, 27.0, -169.0),
        MIDDLE("Middle Pod", -156.0, 28.0, -157.0);

        public final String display;
        public final double x;
        public final double y;
        public final double z;

        StunPod(String display, double x, double y, double z) {
            this.display = display;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final double ENTER_X = -161.0;
    public static final double ENTER_Y = 49.0;
    public static final double ENTER_Z = -186.0;

    public static boolean testMode = false;
    private static boolean stunner = false;
    private static boolean inPhase3 = false;
    private static boolean inBelly = false;

    public static boolean toggleTestMode() {
        testMode = !testMode;
        return testMode;
    }

    public static boolean isInStomach(Player player) {
        if (player == null) return false;
        if (player.getY() >= 55.0) return false;
        double dx = player.getX() - ENTER_X;
        double dz = player.getZ() - ENTER_Z;
        return (dx * dx + dz * dz) <= (40.0 * 40.0);
    }

    public static class StunHUDText {
        public final String text;
        public final float x;
        public final float y;
        public final int color;

        public StunHUDText(String text, float x, float y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    public static final List<StunHUDText> HUD_TEXTS = new ArrayList<>();

    public static void reset() {
        stunner = false;
        inPhase3 = false;
        inBelly = false;
        HUD_TEXTS.clear();
    }

    public static void onRunStart() {
        reset();
    }

    public static void onBallistaReady() {
        inPhase3 = true;
    }

    public static void onHumanCannonballPurchased() {
        stunner = true;
        inPhase3 = true;
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraCloseCannon) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gui != null && mc.gui.screen() != null) {
            mc.execute(() -> mc.player.closeContainer());
        }
    }

    public static void onPlayerEaten(String raw) {
        inPhase3 = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && raw != null) {
            String ign = mc.player.getName().getString();
            if (raw.contains(ign)) {
                stunner = true;
                inBelly = true;
            }
        }
    }

    public static void onStunFinished() {
        inPhase3 = false;
        inBelly = false;
        stunner = false;
    }

    public static void renderWorld(LevelRenderContext context) {
        HUD_TEXTS.clear();
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || (!s.kuudraStunWaypoint && !testMode)) return;
        if (!KuudraUtils.inKuudra() && !testMode) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean inStomach = isInStomach(mc.player);
        inBelly = inStomach;

        boolean isStunPhase = inPhase3 || KuudraSplitsManager.getCurrentPhase() == KuudraSplitsManager.Phase.STUN;
        boolean isEarlyPhase = KuudraSplitsManager.getCurrentPhase() == KuudraSplitsManager.Phase.SUPPLIES ||
                               KuudraSplitsManager.getCurrentPhase() == KuudraSplitsManager.Phase.BUILD;

        boolean active = testMode || inStomach || (!isEarlyPhase && (stunner || isStunPhase));

        if (!active) return;

        // Pods to render based on config
        List<StunPod> pods = new ArrayList<>();
        String podSetting = s.kuudraStunPod != null ? s.kuudraStunPod : "Left Pod";
        if (podSetting.equalsIgnoreCase("Left Pod")) pods.add(StunPod.LEFT);
        else if (podSetting.equalsIgnoreCase("Right Pod")) pods.add(StunPod.RIGHT);
        else if (podSetting.equalsIgnoreCase("Middle Pod")) pods.add(StunPod.MIDDLE);
        else {
            pods.add(StunPod.LEFT);
            pods.add(StunPod.RIGHT);
            pods.add(StunPod.MIDDLE);
        }

        // Color parsing
        float r = 0.0F, g = 1.0F, b = 1.0F, a = 1.0F;
        int colorInt = 0xFF00FFFF;
        if (s.kuudraStunWaypointColor != null && s.kuudraStunWaypointColor.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(s.kuudraStunWaypointColor.substring(1), 16);
                r = ((rgb >> 16) & 0xFF) / 255.0F;
                g = ((rgb >> 8) & 0xFF) / 255.0F;
                b = (rgb & 0xFF) / 255.0F;
                colorInt = 0xFF000000 | rgb;
            } catch (Exception ignored) {}
        }

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        PoseStack poseStack = context.poseStack();
        OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());

        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double px = net.minecraft.util.Mth.lerp((double)pt, mc.player.xo, mc.player.getX());
        double py = net.minecraft.util.Mth.lerp((double)pt, mc.player.yo, mc.player.getY());
        double pz = net.minecraft.util.Mth.lerp((double)pt, mc.player.zo, mc.player.getZ());

        for (StunPod pod : pods) {
            double rx, ry, rz;
            float hs = 0.15F;
            if (testMode && !me.bombo.bomboaddons.kuudra.pearls.KuudraUtils.inKuudra()) {
                // Testing outside Kuudra: place test box 4 blocks directly in front of player's crosshair
                Vec3 look = mc.player.getLookAngle();
                if (mc.options.getCameraType().isFirstPerson()) {
                    rx = look.x * 4.0;
                    ry = look.y * 4.0;
                    rz = look.z * 4.0;
                } else {
                    Vec3 front = new Vec3(px + look.x * 4.0, py + mc.player.getEyeHeight() - 0.5 + look.y * 4.0, pz + look.z * 4.0);
                    rx = front.x - camPos.x;
                    ry = front.y - camPos.y;
                    rz = front.z - camPos.z;
                }
            } else if (inStomach) {
                // True pod block coordinates inside Kuudra's belly (centered in pod block)
                rx = (pod.x + 0.5) - camPos.x;
                ry = (pod.y + 0.5) - camPos.y;
                rz = (pod.z + 0.5) - camPos.z;
            } else {
                // Dynamic waypoint outside belly: offset vector (pod - ENTER)
                double offX = (pod.x + 0.5) - ENTER_X;
                double offY = (pod.y + 0.5) - ENTER_Y;
                double offZ = (pod.z + 0.5) - ENTER_Z;
                if (mc.options.getCameraType().isFirstPerson()) {
                    rx = offX;
                    ry = offY;
                    rz = offZ;
                } else {
                    rx = px - camPos.x + offX;
                    ry = py - camPos.y + offY;
                    rz = pz - camPos.z + offZ;
                }
            }

            AABB relBox = new AABB(rx - hs, ry - hs, rz - hs, rx + hs, ry + hs, rz + hs);

            final float finalR = r;
            final float finalG = g;
            final float finalB = b;
            final float finalA = a;

            // Wireframe 3D Box (rendered through walls with no depth)
            collector.submitCustomGeometry(poseStack, me.bombo.bomboaddons.render.CustomRenderLayer.PRIMITIVE_LINES_NO_DEPTH, (pose, vc) ->
                BomboRenderUtils.drawBox(pose.pose(), vc, relBox, finalR, finalG, finalB, finalA, 3.0F));

            // Translucent Filled Box (rendered through walls with no depth)
            collector.submitCustomGeometry(poseStack, me.bombo.bomboaddons.render.CustomRenderLayer.PRIMITIVE_QUADS_NO_DEPTH, (pose, vc) ->
                BomboRenderUtils.drawFilledBox(pose.pose(), vc,
                    (float) relBox.minX, (float) relBox.minY, (float) relBox.minZ,
                    (float) relBox.maxX, (float) relBox.maxY, (float) relBox.maxZ,
                    finalR, finalG, finalB, 0.20F));

            // In-world 3D billboard text (rendered through walls)
            String label = inStomach ? ("§b" + pod.display) : ("§b" + pod.display + " Stun Aim");
            BomboRenderUtils.drawText(poseStack, collector, label, (float) rx, (float) (ry + 1.3), (float) rz, colorInt, 0.035f, true, true);

            // 2D Screen Projection HUD tag
            org.joml.Matrix4f modelView = new org.joml.Matrix4f(poseStack.last().pose());
            org.joml.Matrix4f projection = new org.joml.Matrix4f(context.levelState().cameraRenderState.projectionMatrix);
            Vector4f clip = new Vector4f((float) rx, (float) (ry + 1.4), (float) rz, 1.0F);
            modelView.transform(clip);
            projection.transform(clip);
            if (clip.w > 0.05F) {
                float ndcX = clip.x / clip.w;
                float ndcY = clip.y / clip.w;
                if (ndcX >= -1.2F && ndcX <= 1.2F && ndcY >= -1.2F && ndcY <= 1.2F) {
                    int screenWidth = mc.getWindow().getGuiScaledWidth();
                    int screenHeight = mc.getWindow().getGuiScaledHeight();
                    float screenX = (ndcX + 1.0F) * 0.5F * (float) screenWidth;
                    float screenY = (1.0F - ndcY) * 0.5F * (float) screenHeight;
                    HUD_TEXTS.add(new StunHUDText(label, screenX, screenY, colorInt));
                }
            }
        }
    }

    public static void onBackboneHit(String targetName) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        boolean allow = (s.kuudraBackboneHit && SkyblockUtils.isInKuudra())
                || (s.backboneOnIsland && SkyblockUtils.isInPrivateIsland());

        if (!allow) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack held = mc.player.getMainHandItem();
        String heldName = !held.isEmpty() ? held.getHoverName().getString() : "Empty";

        String armorSummary = getArmorSummary(mc.player);

        mc.player.sendSystemMessage(Component.literal("§8[§6Backbone§8] §aHit §e" + targetName
                + " §8| §7Holding: §f" + heldName + " §8| §7Armor: " + armorSummary));

        if (s.kuudraBackboneSound) {
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1.2F, 1.0F));
            }
        }
    }

    private static String getArmorSummary(Player player) {
        ItemStack helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        ItemStack chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        ItemStack boots = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);

        String h = !helmet.isEmpty() ? helmet.getHoverName().getString() : "None";
        String c = !chest.isEmpty() ? chest.getHoverName().getString() : "None";
        String l = !legs.isEmpty() ? legs.getHoverName().getString() : "None";
        String b = !boots.isEmpty() ? boots.getHoverName().getString() : "None";

        return "§b[" + h + " / " + c + " / " + l + " / " + b + "§b]";
    }
}
