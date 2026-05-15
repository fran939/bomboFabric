package me.bombo.bomboaddons_final;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
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

    public static void render(WorldRenderContext context) {
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
        PoseStack poseStack = context.matrices();
        MultiBufferSource consumers = context.consumers();
        if (consumers == null) return;

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

            // Acquire buffer INSIDE the loop to prevent 'Not building!' crashes after drawText
            VertexConsumer lineBuffer = consumers.getBuffer(RenderTypes.linesTranslucent());

            count++;
            double x = entity.getX() - camPos.x;
            double y = entity.getY() - camPos.y;
            double z = entity.getZ() - camPos.z;

            // Perspective scaling trick: draw the ESP very close to the camera
            // so it doesn't get occluded by walls, but scale it down so it appears the same size.
            float dist = (float) Math.sqrt(x*x + y*y + z*z);
            float scale = 1.0f;
            if (dist > 0.2f) {
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
            BomboRenderUtils.drawBox(poseStack, lineBuffer, box, r, g, b, a, lineWidth);

            if (s.pestEspTracer) {
                // Project 3D position to 2D HUD coordinates using the correct matrices from WorldRenderContext
                Matrix4f view = context.matrices().last().pose();
                Matrix4f proj = mc.gameRenderer.getProjectionMatrix(mc.getDeltaTracker().getGameTimeDeltaTicks());

                // Original world position (unscaled) of the pest's center
                Vector4f pos = new Vector4f((float)x, (float)(y + 1.3f + 0.5f), (float)z, 1.0f);
                pos.mul(view);
                pos.mul(proj);

                if (pos.w > 0.0f) {
                    float ndcX = pos.x / pos.w;
                    float ndcY = pos.y / pos.w;

                    int screenWidth = mc.getWindow().getGuiScaledWidth();
                    int screenHeight = mc.getWindow().getGuiScaledHeight();

                    float screenX = (ndcX + 1.0f) * 0.5f * screenWidth;
                    float screenY = (1.0f - ndcY) * 0.5f * screenHeight;

                    PestTracer tracer = new PestTracer();
                    tracer.end = new Vector2f(screenX, screenY);
                    tracer.start = new Vector2f(screenWidth / 2.0f, screenHeight); // Bottom center of screen
                    tracer.color = colorInt;
                    tracer.thickness = lineWidth;
                    TRACERS.add(tracer);
                }
            }

            String displayName = pestName.substring(0, 1).toUpperCase() + pestName.substring(1);
            BomboRenderUtils.drawText(poseStack, consumers, "§e" + displayName, (float)x, (float)(y + textYOffset), (float)z, 0xFFFFFF, 0.03f, true);
        }

        if (debugTicks % 200 == 0 && s.debugEntities) {
            System.out.println("DEBUG: Scanned " + armorStands + " armor stands, found " + count + " pests.");
        }
    }
}
