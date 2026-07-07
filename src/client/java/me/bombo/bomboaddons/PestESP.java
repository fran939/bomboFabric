package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Vector2f;
import org.joml.Vector4f;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

public class PestESP {
    public static class PestTracer {
        public Vector2f start;
        public Vector2f end;
        public int color;
        public float thickness;
    }
    public static final List<PestTracer> TRACERS = new ArrayList<>();

    private static int debugTicks = 0;

    public static void render(LevelRenderContext context) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.pestEsp) return;
        
        // Debug garden detection
        boolean inGarden = SkyblockUtils.isInGarden();
        if (debugTicks++ % 200 == 0 && s.debugEntities) {
            System.out.println("DEBUG: PestESP render call. InGarden=" + inGarden + " Location=" + SkyblockUtils.getLocation());
        }
        
        if (!inGarden) {
            TRACERS.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            TRACERS.clear();
            return;
        }

        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.poseStack();
        me.bombo.bomboaddons.OrderedSubmitNodeCollector collector = new me.bombo.bomboaddons.OrderedSubmitNodeCollector(context.bufferSource());

        // Use color name from config
        int colorInt = BomboRenderUtils.colorNameToHex(s.pestEspColor);
        float r = ((colorInt >> 16) & 0xFF) / 255.0f;
        float g = ((colorInt >> 8) & 0xFF) / 255.0f;
        float b = (colorInt & 0xFF) / 255.0f;
        float a = 1.0f;
        float lineWidth = s.pestEspThickness;

        TRACERS.clear();

        int count = 0;
        int armorStands = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            armorStands++;

            String pestName = TargetPests.getPestName(stand);
            if (pestName == null) continue;

            count++;
            double x = entity.getX() - camPos.x;
            double y = entity.getY() - camPos.y;
            double z = entity.getZ() - camPos.z;

            // Perspective scaling trick: draw the ESP very close to the camera
            // so it doesn't get occluded by walls, but scale it down so it appears the same size.
            float dist = (float) Math.sqrt(x*x + y*y + z*z);
            float scale = 1.0f;
            if (!s.hideCheats && dist > 0.2f) {
                scale = 0.2f / dist;
            }

            float boxWidth = 1.0f * scale;
            float boxHeight = 1.0f * scale;
            float boxYOffset = 1.3f * scale; 

            if (pestName.contains("worm")) {
                boxYOffset = 0.5f * scale;
                boxHeight = 0.8f * scale;
            } else if (pestName.equals("slug")) {
                boxYOffset = 1.3f * scale;
                boxWidth = 0.8f * scale;
                boxHeight = 0.4f * scale;
            } else if (pestName.equals("field mouse")) {
                boxYOffset = 1.5f * scale;
            }

            // The text should not be scaled with the trick because text rendering has a built-in see-through mode
            // We'll use the original positions for the text.
            float textYOffset = 1.3f + 1.0f + 0.2f; // Default
            if (pestName.contains("worm")) textYOffset = 0.5f + 0.8f + 0.2f;
            else if (pestName.equals("slug")) textYOffset = 1.3f + 0.4f + 0.2f;
            else if (pestName.equals("field mouse")) textYOffset = 1.5f + 1.0f + 0.2f;

            float scaledX = (float)x * scale;
            float scaledY = (float)y * scale;
            float scaledZ = (float)z * scale;

            AABB box = new AABB(scaledX - boxWidth/2, scaledY + boxYOffset, scaledZ - boxWidth/2, scaledX + boxWidth/2, scaledY + boxYOffset + boxHeight, scaledZ + boxWidth/2);
            net.minecraft.client.renderer.rendertype.RenderType renderType = s.hideCheats ? RenderTypes.lines() : RenderTypes.linesTranslucent();
            collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
                BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, lineWidth);
            });

            boolean shouldTrace = false;
            if ("rat".equals(pestName)) shouldTrace = s.tracerRat;
            else if ("worm".equals(pestName) || "wormass".equals(pestName)) shouldTrace = s.tracerWorm;
            else if ("slug".equals(pestName)) shouldTrace = s.tracerSlug;
            else if ("fly".equals(pestName)) shouldTrace = s.tracerFly;
            else if ("locust".equals(pestName)) shouldTrace = s.tracerLocust;
            else if ("beetle".equals(pestName)) shouldTrace = s.tracerBeetle;
            else if ("cricket".equals(pestName)) shouldTrace = s.tracerCricket;
            else if ("spider".equals(pestName)) shouldTrace = s.tracerSpider;
            else if ("moth".equals(pestName)) shouldTrace = s.tracerMoth;
            else if ("mite".equals(pestName)) shouldTrace = s.tracerMite;
            else if ("field mouse".equals(pestName)) shouldTrace = s.tracerMouse;
            else if ("mosquito".equals(pestName)) shouldTrace = s.tracerMosquito;

            if (!s.hideCheats && s.pestEspTracer && shouldTrace) {
                final float endX = (float) x;
                float tempEndY = (float) (y + 1.3f + 0.5f);
                if (pestName.contains("worm")) tempEndY = (float) (y + 0.5f + 0.4f);
                else if (pestName.equals("slug")) tempEndY = (float) (y + 1.3f + 0.2f);
                else if (pestName.equals("field mouse")) tempEndY = (float) (y + 1.5f + 0.5f);
                final float endY = tempEndY;
                final float endZ = (float) z;

                org.joml.Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
                float startX = look.x();
                float startY = look.y();
                float startZ = look.z();

                final float finalR = r;
                final float finalG = g;
                collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                    BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, endX, endY, endZ, finalR, finalG, b, a, 2.0f);
                });
            }

            String displayName = pestName.substring(0, 1).toUpperCase() + pestName.substring(1);
            BomboRenderUtils.drawText(poseStack, collector, "§e" + displayName, (float)x, (float)(y + textYOffset), (float)z, 0xFFFFFF, 0.03f, true, !s.hideCheats);
        }

        if (debugTicks % 200 == 0 && s.debugEntities) {
            System.out.println("DEBUG: Scanned " + armorStands + " armor stands, found " + count + " pests.");
        }
    }
}
