package me.bombo.bomboaddons_final;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.InteractionResult;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;

public class CorpseHighlight {
    private static final HashSet<Integer> openedCorpses = new HashSet<>();

    public enum CorpseType {
        Lapis,
        Tungsten,
        Umber,
        Vanguard,
        None
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clearCache();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearCache();
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide() && entity instanceof ArmorStand stand) {
                BomboConfig.Settings s = BomboConfig.get();
                if (s.corpseEsp && s.hideOpenedCorpses) {
                    CorpseType type = getCorpseType(stand);
                    if (type != CorpseType.None && hasKeyForCorpse(type)) {
                        openedCorpses.add(stand.getId());
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    public static void clearCache() {
        openedCorpses.clear();
    }

    public static CorpseType getCorpseType(ArmorStand stand) {
        ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet != null && !helmet.isEmpty()) {
            String name = helmet.getHoverName().getString().replaceAll("(?i)§.", "").trim().toLowerCase();
            if (name.contains("lapis armor helmet") || name.contains("lapis helmet")) return CorpseType.Lapis;
            if (name.contains("mineral helmet")) return CorpseType.Tungsten;
            if (name.contains("yog helmet")) return CorpseType.Umber;
            if (name.contains("vanguard helmet")) return CorpseType.Vanguard;
        }
        return CorpseType.None;
    }

    public static boolean hasKeyForCorpse(CorpseType type) {
        String id = switch (type) {
            case Tungsten -> "TUNGSTEN_KEY";
            case Umber -> "UMBER_KEY";
            case Vanguard -> "SKELETON_KEY";
            default -> "";
        };
        if (!id.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            for (int i = 0; i <= 35; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (!stack.isEmpty() && SkyblockUtils.getInternalId(stack).equals(id)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public static boolean isInMineshaft() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        // 1. Scoreboard Sidebar check
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            List<String> sbLines = SkyblockUtils.getSidebarLines(scoreboard, sidebar);
            for (String line : sbLines) {
                String clean = line.replaceAll("(?i)§.", "").trim().toLowerCase();
                if (clean.contains("mineshaft")) {
                    return true;
                }
            }
        }

        // 2. Tab list check
        if (mc.getConnection() != null) {
            List<net.minecraft.network.chat.Component> tabLines = SkyblockUtils.getTabListLines();
            for (net.minecraft.network.chat.Component c : tabLines) {
                String clean = c.getString().replaceAll("(?i)§.", "").trim().toLowerCase();
                if (clean.contains("mineshaft")) {
                    return true;
                }
            }
        }

        // 3. Locraw fallback
        if (SkyblockUtils.isConnectedToHypixel() && "SKYBLOCK".equals(BomboaddonsClient.locrawGametype)) {
            String mode = BomboaddonsClient.locrawMode != null ? BomboaddonsClient.locrawMode.toLowerCase() : "";
            String map = BomboaddonsClient.locrawMap != null ? BomboaddonsClient.locrawMap.toLowerCase() : "";
            if (mode.contains("mineshaft") || map.contains("mineshaft")) {
                return true;
            }
        }

        return false;
    }

    public static BlockPos findGround(BlockPos pos, int maxDistance) {
        int dist = Math.max(0, Math.min(maxDistance, 256));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return pos;
        for (int i = 0; i <= dist; i++) {
            BlockPos below = pos.below(i);
            if (!mc.level.getBlockState(below).isAir()) {
                return below;
            }
        }
        return pos;
    }

    public static void render(WorldRenderContext context) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.corpseEsp) return;
        if (!isInMineshaft()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.matrices();
        MultiBufferSource consumers = context.consumers();
        if (consumers == null) return;

        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;

            if (s.hideOpenedCorpses && openedCorpses.contains(stand.getId())) {
                continue;
            }

            CorpseType type = getCorpseType(stand);
            if (type == CorpseType.None) continue;

            String outlineColorName = switch (type) {
                case Lapis -> s.lapisOutlineColor;
                case Tungsten -> s.tungstenOutlineColor;
                case Umber -> s.umberOutlineColor;
                case Vanguard -> s.vanguardOutlineColor;
                default -> "WHITE";
            };
            String fillColorName = switch (type) {
                case Lapis -> s.lapisFillColor;
                case Tungsten -> s.tungstenFillColor;
                case Umber -> s.umberFillColor;
                case Vanguard -> s.vanguardFillColor;
                default -> "WHITE";
            };

            int outlineColorHex = BomboRenderUtils.colorNameToHex(outlineColorName);
            int fillColorHex = BomboRenderUtils.colorNameToHex(fillColorName);

            float rOut = ((outlineColorHex >> 16) & 0xFF) / 255.0f;
            float gOut = ((outlineColorHex >> 8) & 0xFF) / 255.0f;
            float bOut = (outlineColorHex & 0xFF) / 255.0f;
            float aOut = 1.0f;

            float rFill = ((fillColorHex >> 16) & 0xFF) / 255.0f;
            float gFill = ((fillColorHex >> 8) & 0xFF) / 255.0f;
            float bFill = (fillColorHex & 0xFF) / 255.0f;
            float aFill = 0.4f;

            double x = stand.getX() - camPos.x;
            double y = stand.getY() - camPos.y;
            double z = stand.getZ() - camPos.z;
            float dist = (float) Math.sqrt(x*x + y*y + z*z);
            float scale = 1.0f;
            if (!s.hideCheats && dist > 0.2f) {
                scale = 0.2f / dist;
            }

            // Bounding box of the armor stand expanded by 0.25 on x and z, and moved relative to camera
            AABB box = stand.getBoundingBox().inflate(0.25, 0.0, 0.25).move(-camPos.x, -camPos.y, -camPos.z);
            if (scale != 1.0f) {
                box = new AABB(
                    box.minX * scale, box.minY * scale, box.minZ * scale,
                    box.maxX * scale, box.maxY * scale, box.maxZ * scale
                );
            }

            if ("Outline".equals(s.corpseEspStyle) || "Both".equals(s.corpseEspStyle)) {
                VertexConsumer lineBuffer = consumers.getBuffer(RenderTypes.linesTranslucent());
                BomboRenderUtils.drawBox(poseStack, lineBuffer, box, rOut, gOut, bOut, aOut, 2.0f);
            }
            if ("Filled".equals(s.corpseEspStyle) || "Both".equals(s.corpseEspStyle)) {
                VertexConsumer fillBuffer = consumers.getBuffer(RenderTypes.debugQuads());
                drawFilledBox(poseStack, fillBuffer, box, rFill, gFill, bFill, aFill);
            }
        }
    }

    public static void drawFilledBox(PoseStack poseStack, VertexConsumer buffer, AABB aabb, float r, float g, float b, float a) {
        drawFilledBox(poseStack, buffer, (float)aabb.minX, (float)aabb.minY, (float)aabb.minZ, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.maxZ, r, g, b, a);
    }

    public static void drawFilledBox(PoseStack poseStack, VertexConsumer buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();

        // Down face
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);

        // Up face
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);

        // North face (z = minZ)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);

        // South face (z = maxZ)
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);

        // West face (x = minX)
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);

        // East face (x = maxX)
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
    }
}
