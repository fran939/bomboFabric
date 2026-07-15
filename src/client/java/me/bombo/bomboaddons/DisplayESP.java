package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4f;

public class DisplayESP {

    public static void render(LevelRenderContext context) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.displayEsp) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack poseStack = context.poseStack();
        me.bombo.bomboaddons.OrderedSubmitNodeCollector collector = new me.bombo.bomboaddons.OrderedSubmitNodeCollector(context.bufferSource());

        int colorInt = BomboRenderUtils.colorNameToHex(s.displayEspColor);
        float r = ((colorInt >> 16) & 0xFF) / 255f;
        float g = ((colorInt >> 8) & 0xFF) / 255f;
        float b = (colorInt & 0xFF) / 255f;
        float a = 1.0f;

        float lineWidth = s.displayEspThickness;

        net.minecraft.world.phys.Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        org.joml.Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
        float startX = look.x();
        float startY = look.y();
        float startZ = look.z();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Display || entity instanceof net.minecraft.world.entity.Interaction) {
                net.minecraft.world.phys.AABB aabb = (entity instanceof Display display) ? display.getBoundingBoxForCulling() : entity.getBoundingBox();
                
                String typeStr = "Unknown";
                String extraInfo = "";
                if (entity instanceof Display.TextDisplay td) {
                    typeStr = "Text Display";
                    if (td.getText() != null) {
                        String textStr = td.getText().getString();
                        extraInfo = textStr.substring(0, Math.min(textStr.length(), 20));
                    }
                } else if (entity instanceof Display.ItemDisplay id) {
                    typeStr = "Item Display";
                    if (id.getItemStack() != null) {
                        extraInfo = id.getItemStack().getHoverName().getString();
                    }
                } else if (entity instanceof Display.BlockDisplay bd) {
                    typeStr = "Block Display";
                    if (bd.getBlockState() != null) {
                        extraInfo = bd.getBlockState().getBlock().getName().getString();
                    }
                } else if (entity instanceof net.minecraft.world.entity.Interaction interaction) {
                    typeStr = "Interaction";
                    extraInfo = String.format("W:%.1f H:%.1f", interaction.getBbWidth(), interaction.getBbHeight());
                }

                String filterStr = s.displayEspFilter != null ? s.displayEspFilter.trim() : "";
                if (!filterStr.isEmpty()) {
                    boolean matches = false;
                    String[] filters = filterStr.toLowerCase().split(",");
                    String searchTarget = (typeStr + " " + entity.getUUID().toString() + " " + extraInfo).toLowerCase();
                    for (String filter : filters) {
                        String f = filter.trim();
                        if (!f.isEmpty() && searchTarget.contains(f)) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) continue;
                }

                double camX = camPos.x;
                double camY = camPos.y;
                double camZ = camPos.z;

                float minX = (float)(aabb.minX - camX);
                float minY = (float)(aabb.minY - camY);
                float minZ = (float)(aabb.minZ - camZ);
                float maxX = (float)(aabb.maxX - camX);
                float maxY = (float)(aabb.maxY - camY);
                float maxZ = (float)(aabb.maxZ - camZ);
                
                collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                    Matrix4f matrix = pose.pose();
                    // Bottom
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, lineWidth);
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, lineWidth);
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, lineWidth);
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a, lineWidth);
                    // Top
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth);
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth);
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, lineWidth);
                    // Pillars
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, lineWidth);
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth);
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
                    BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth);
                });

                if (s.displayEspTracer) {
                    float endX = (float) (entity.getX() - camX);
                    float endY = (float) (entity.getY() - camY + aabb.getYsize() / 2.0f);
                    float endZ = (float) (entity.getZ() - camZ);

                    collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                        BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, endX, endY, endZ, r, g, b, a, lineWidth);
                    });
                }
                
                String label = String.format("§e%s §7(UUID: %s) §f%s", typeStr, entity.getUUID().toString().substring(0, 8), extraInfo);
                
                float labelX = (float) (entity.getX() - camX);
                float labelY = (float) (entity.getY() - camY + aabb.getYsize() + 0.5);
                float labelZ = (float) (entity.getZ() - camZ);
                
                BomboRenderUtils.drawText(poseStack, collector, label, labelX, labelY, labelZ, 0xFFFFFF, 0.025f, true, true);
            }
        }
    }
}
