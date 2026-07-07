package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HighlightESP {
    public static class HighlightTracer {
        public org.joml.Vector2f start;
        public org.joml.Vector2f end;
        public int color;
        public float thickness;
    }
    public static final java.util.List<HighlightTracer> TRACERS = new java.util.ArrayList<>();
    public static org.joml.Matrix4f lastViewMatrix = new org.joml.Matrix4f();
    public static org.joml.Matrix4f lastProjMatrix = new org.joml.Matrix4f();
    public static int lastEntityCount = 0;
    public static int lastTracerChecks = 0;
    public static int lastTracersAdded = 0;

    public static void render(LevelRenderContext context) {
        TRACERS.clear();
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.highlightsEnabled && !s.tracerTestAllEntities && !s.cheeseTracer && (s.customTracers == null || s.customTracers.isEmpty())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        lastViewMatrix.set(context.poseStack().last().pose());
        lastProjMatrix.set(context.levelState().cameraRenderState.projectionMatrix);
        lastEntityCount = 0;
        lastTracerChecks = 0;
        lastTracersAdded = 0;
        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        
        PoseStack poseStack = context.poseStack();
        me.bombo.bomboaddons.OrderedSubmitNodeCollector collector = new me.bombo.bomboaddons.OrderedSubmitNodeCollector(context.bufferSource());

        org.joml.Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
        float startX = look.x();
        float startY = look.y();
        float startZ = look.z();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            lastEntityCount++;

            boolean drawTracer = false;
            int colorInt = 0xFFFFFF; // Default to white

            if (s.tracerTestAllEntities) {
                drawTracer = true;
                colorInt = 0xFFFF00; // Yellow for testing all
            } else if (s.customTracers != null) {
                String uuidStr = entity.getUUID().toString();
                String entName = entity.getName().getString();
                String itemName = null;
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                    net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
                    if (!stack.isEmpty()) {
                        itemName = stack.getHoverName().getString();
                    }
                }
                
                String matchId = null;
                if (s.customTracers.containsKey(uuidStr)) matchId = uuidStr;
                else if (s.customTracers.containsKey(entName)) matchId = entName;
                else if (itemName != null && s.customTracers.containsKey(itemName)) matchId = itemName;
                else {
                    // Try stripping color codes from names just in case
                    String cleanEntName = entName.replaceAll("(?i)§[0-9A-FK-OR]", "");
                    if (s.customTracers.containsKey(cleanEntName)) matchId = cleanEntName;
                    else if (itemName != null) {
                        String cleanItemName = itemName.replaceAll("(?i)§[0-9A-FK-OR]", "");
                        if (s.customTracers.containsKey(cleanItemName)) matchId = cleanItemName;
                    }
                }

                if (matchId != null) {
                    drawTracer = true;
                    colorInt = BomboRenderUtils.colorNameToHex(s.customTracers.get(matchId).color);
                }
            }
            
            if (!drawTracer) {
                if (s.cheeseTracer && entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                    net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
                    if (!stack.isEmpty()) {
                        if (stack.getComponentsPatch().toString().contains("CHEESE_FUEL")) {
                            drawTracer = true;
                            colorInt = 0xFFD700; // Gold for cheese
                        }
                    }
                }

                if (!drawTracer && s.highlightsEnabled) {
                    me.bombo.bomboaddons.TargetPests.EntityInfoCache cache = getCachedInfo(entity);
                    String name = cache.combinedName;
                    String nametagName = cache.nametagName;

                    if (!name.isEmpty() || nametagName != null) {
                        for (java.util.Map.Entry<String, BomboConfig.HighlightInfo> entry : s.highlights.entrySet()) {
                            String key = entry.getKey();
                            if ((!name.isEmpty() && name.contains(key)) || (nametagName != null && nametagName.contains(key))) {
                                BomboConfig.HighlightInfo info = entry.getValue();
                                if (info.enabled && info.tracer) {
                                    if (!entity.isInvisible() || info.showInvisible) {
                                        drawTracer = true;
                                        colorInt = BomboRenderUtils.colorNameToHex(info.color);
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }

            if (drawTracer) {
                float endX = (float) (entity.getX() - camPos.x);
                float endY = (float) (entity.getY() - camPos.y + entity.getBbHeight() / 2.0f);
                float endZ = (float) (entity.getZ() - camPos.z);

                final float r = ((colorInt >> 16) & 0xFF) / 255.0f;
                final float g = ((colorInt >> 8) & 0xFF) / 255.0f;
                final float b = (colorInt & 0xFF) / 255.0f;

                collector.submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                    BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, endX, endY, endZ, r, g, b, 1.0f, 2.0f);
                });
                
                // Add dummy tracer record for /tracerdebug printing
                HighlightTracer tracer = new HighlightTracer();
                tracer.start = new org.joml.Vector2f(startX, startY);
                tracer.end = new org.joml.Vector2f(endX, endY);
                tracer.color = colorInt;
                tracer.thickness = 2.0f;
                TRACERS.add(tracer);

                lastTracersAdded++;
            }
        }
    }

    public static me.bombo.bomboaddons.TargetPests.EntityInfoCache getCachedInfo(Entity self) {
        if (me.bombo.bomboaddons.TargetPests.infoCache.size() > 5000) {
            me.bombo.bomboaddons.TargetPests.infoCache.clear();
        }
        int id = self.getId();
        long now = System.currentTimeMillis();
        me.bombo.bomboaddons.TargetPests.EntityInfoCache cached = me.bombo.bomboaddons.TargetPests.infoCache.get(id);
        
        if (cached != null && (now - cached.lastCheckMs) < 500) {
            return cached;
        }

        String name = ChatFormatting.stripFormatting(self.getDisplayName().getString());
        StringBuilder combinedName = new StringBuilder(name != null ? name.toLowerCase() : "");

        if (self instanceof ArmorStand) {
            String pestName = me.bombo.bomboaddons.TargetPests
                    .getPestName((ArmorStand) self);
            if (pestName != null) {
                combinedName.append(" | ").append(pestName);
            }
        }

        for (Entity passenger : self.getPassengers()) {
            String pName = ChatFormatting.stripFormatting(passenger.getDisplayName().getString());
            if (pName != null) {
                combinedName.append(" | ").append(pName.toLowerCase());
            }
            if (passenger instanceof ArmorStand) {
                String pestName = me.bombo.bomboaddons.TargetPests
                        .getPestName((ArmorStand) passenger);
                if (pestName != null) {
                    combinedName.append(" | ").append(pestName);
                }
            }
        }

        Entity vehicle = self.getVehicle();
        if (vehicle != null) {
            String vName = ChatFormatting.stripFormatting(vehicle.getDisplayName().getString());
            if (vName != null) {
                combinedName.append(" | ").append(vName.toLowerCase());
            }
            if (vehicle instanceof ArmorStand) {
                String pestName = me.bombo.bomboaddons.TargetPests
                        .getPestName((ArmorStand) vehicle);
                if (pestName != null) {
                    combinedName.append(" | ").append(pestName);
                }
            }
        }

        String finalCombined = combinedName.toString();
        String nametag = getNearbyNametagName(self);

        me.bombo.bomboaddons.TargetPests.EntityInfoCache newValue = new me.bombo.bomboaddons.TargetPests.EntityInfoCache(now, finalCombined, nametag);
        me.bombo.bomboaddons.TargetPests.infoCache.put(id, newValue);
        return newValue;
    }

    private static String getNearbyNametagName(Entity self) {
        if (self instanceof ArmorStand || self.level() == null) {
            return null;
        }
        java.util.List<Entity> nearby = self.level().getEntities(self, self.getBoundingBox().inflate(0.5D, 3.0D, 0.5D),
                (e) -> e instanceof ArmorStand && e.hasCustomName());
        for (Entity e : nearby) {
            String name = ChatFormatting.stripFormatting(e.getCustomName().getString());
            if (name != null && !name.isEmpty()) {
                return name.toLowerCase();
            }
        }
        return null;
    }
}
