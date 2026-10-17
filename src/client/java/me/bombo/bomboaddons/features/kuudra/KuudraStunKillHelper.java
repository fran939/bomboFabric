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

    private static boolean stunner = false;
    private static boolean inPhase3 = false;
    private static boolean inBelly = false;

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
    }

    public static void renderWorld(LevelRenderContext context) {
        HUD_TEXTS.clear();
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraStunWaypoint) return;
        if (!KuudraUtils.inKuudra()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Position check for belly
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        if (px < -140.0 && pz < -150.0) {
            inBelly = true;
        } else if (px > -140.0 && pz > -150.0) {
            inBelly = false;
        }

        boolean phase3Active = inPhase3 || inBelly || stunner ||
                KuudraSplitsManager.getCurrentPhase() == KuudraSplitsManager.Phase.STUN;

        if (!phase3Active) return;

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

        for (StunPod pod : pods) {
            AABB worldBox;
            if (inBelly) {
                // True pod block coordinates inside Kuudra's belly
                worldBox = new AABB(pod.x, pod.y, pod.z, pod.x + 1.0, pod.y + 1.0, pod.z + 1.0);
            } else {
                // Dynamic waypoint: offsets from player by (pod - ENTER) so looking at it outside matches
                // the exact yaw and pitch needed when spawning inside the belly at ENTER_X/Y/Z
                double offsetX = mc.player.getX() - ENTER_X;
                double offsetY = mc.player.getY() - ENTER_Y;
                double offsetZ = mc.player.getZ() - ENTER_Z;
                worldBox = new AABB(pod.x + offsetX, pod.y + offsetY, pod.z + offsetZ,
                                    pod.x + 1.0 + offsetX, pod.y + 1.0 + offsetY, pod.z + 1.0 + offsetZ);
            }

            double rx = (worldBox.minX + worldBox.maxX) * 0.5 - camPos.x;
            double ry = worldBox.minY - camPos.y;
            double rz = (worldBox.minZ + worldBox.maxZ) * 0.5 - camPos.z;

            AABB relBox = new AABB(worldBox.minX - camPos.x, worldBox.minY - camPos.y, worldBox.minZ - camPos.z,
                                   worldBox.maxX - camPos.x, worldBox.maxY - camPos.y, worldBox.maxZ - camPos.z);

            final float finalR = r;
            final float finalG = g;
            final float finalB = b;
            final float finalA = a;

            // Wireframe 3D Box
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) ->
                BomboRenderUtils.drawBox(pose.pose(), vc, relBox, finalR, finalG, finalB, finalA, 2.0F));

            // Beacon beam
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) ->
                BomboRenderUtils.drawBeaconBeam(poseStack, vc, rx, ry, rz, finalR, finalG, finalB, 0.7F));

            // 2D Text Label
            String label = inBelly ? ("§b" + pod.display) : ("§b" + pod.display + " Stun Aim");
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
