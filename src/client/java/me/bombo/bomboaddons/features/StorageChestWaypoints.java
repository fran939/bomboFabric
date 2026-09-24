package me.bombo.bomboaddons.features;

import com.mojang.blaze3d.vertex.PoseStack;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.OrderedSubmitNodeCollector;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /b storage <query>} - puts a highlight on every island chest that still holds a matching
 * item.
 *
 * <p>Chest positions and their contents were already being tracked by {@link StorageTracker} under
 * {@code "Island Chest @ x, y, z"} keys; this class is the missing link that turns that data into
 * something you can walk to:
 * <ul>
 *   <li>Double chests are merged into a single box instead of two overlapping ones.</li>
 *   <li>The match is re-checked whenever a container closes: a chest whose matching items are all
 *       gone loses its waypoint, a chest with items left keeps it and shows the remaining count.</li>
 *   <li>Stored positions that are no longer chests (mined, replaced, different island) are purged
 *       from the storage cache instead of being highlighted forever.</li>
 * </ul>
 */
public final class StorageChestWaypoints {

    private static final String ISLAND_CHEST_PREFIX = "Island Chest @ ";
    private static final Pattern COUNT_PATTERN = Pattern.compile("count:\\s*(\\d+)");

    /** One highlighted chest (or merged double chest). */
    public static final class ChestMatch {
        public String containerKey = "";
        public int minX;
        public int minY;
        public int minZ;
        public int maxX;
        public int maxY;
        public int maxZ;
        public int itemCount;
        public long totalQuantity;
        public String itemLabel = "";
        public boolean partial;

        public BlockPos center() {
            return new BlockPos((minX + maxX) / 2, minY, (minZ + maxZ) / 2);
        }
    }

    private static final List<ChestMatch> matches = new ArrayList<>();
    private static String activeQuery = null;
    private static long lastVerify = 0L;

    private StorageChestWaypoints() {
    }

    public static String getActiveQuery() {
        return activeQuery;
    }

    public static synchronized boolean hasMatches() {
        return !matches.isEmpty();
    }

    public static synchronized int matchCount() {
        return matches.size();
    }

    public static synchronized void clear() {
        matches.clear();
        activeQuery = null;
    }

    // -------------------------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------------------------

    /**
     * Builds waypoints for every island chest containing {@code query}.
     *
     * @return number of chests highlighted
     */
    public static synchronized int search(String query) {
        matches.clear();
        activeQuery = query;
        if (query == null || query.trim().isEmpty()) {
            return 0;
        }
        String needle = normalize(query);

        // containerKey -> matched slot count / quantity
        Map<String, int[]> hits = new LinkedHashMap<>();
        Map<String, Long> quantities = new LinkedHashMap<>();

        for (Map.Entry<String, Map<Integer, String>> container : StorageTracker.storageData.entrySet()) {
            String key = container.getKey();
            if (key == null || !key.startsWith(ISLAND_CHEST_PREFIX)) {
                continue;
            }
            BlockPos pos = parsePosition(key);
            if (pos == null) {
                continue;
            }
            int hitSlots = 0;
            long quantity = 0L;
            for (String nbt : container.getValue().values()) {
                if (nbt == null) continue;
                if (normalize(nbt).contains(needle)) {
                    hitSlots++;
                    quantity += stackCount(nbt);
                }
            }
            if (hitSlots > 0) {
                hits.put(key, new int[]{hitSlots});
                quantities.put(key, quantity);
            }
        }

        for (Map.Entry<String, int[]> hit : hits.entrySet()) {
            BlockPos pos = parsePosition(hit.getKey());
            if (pos == null) continue;
            ChestMatch match = new ChestMatch();
            match.containerKey = hit.getKey();
            match.minX = pos.getX();
            match.minY = pos.getY();
            match.minZ = pos.getZ();
            match.maxX = pos.getX();
            match.maxY = pos.getY();
            match.maxZ = pos.getZ();
            match.itemCount = hit.getValue()[0];
            match.totalQuantity = quantities.getOrDefault(hit.getKey(), (long) match.itemCount);
            match.itemLabel = query.trim();
            matches.add(match);
        }

        mergeDoubleChests();
        return matches.size();
    }

    /** Merges adjacent matches at the same Y into one bounded box (double / trapped chests). */
    private static void mergeDoubleChests() {
        boolean merged = true;
        while (merged) {
            merged = false;
            outer:
            for (int i = 0; i < matches.size(); i++) {
                for (int j = i + 1; j < matches.size(); j++) {
                    ChestMatch a = matches.get(i);
                    ChestMatch b = matches.get(j);
                    if (a.minY != b.minY) continue;
                    boolean adjacentX = Math.abs(a.minZ - b.minZ) == 0 && Math.abs(a.minX - b.minX) == 1;
                    boolean adjacentZ = Math.abs(a.minX - b.minX) == 0 && Math.abs(a.minZ - b.minZ) == 1;
                    if (!adjacentX && !adjacentZ) continue;
                    a.minX = Math.min(a.minX, b.minX);
                    a.maxX = Math.max(a.maxX, b.maxX);
                    a.minZ = Math.min(a.minZ, b.minZ);
                    a.maxZ = Math.max(a.maxZ, b.maxZ);
                    a.itemCount += b.itemCount;
                    a.totalQuantity += b.totalQuantity;
                    matches.remove(j);
                    merged = true;
                    break outer;
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Verification & purge
    // -------------------------------------------------------------------------------------------

    /**
     * Re-reads the stored chests: updates counts, drops chests that no longer hold anything
     * matching, and purges stored positions that are no longer chest blocks.
     */
    public static synchronized void verify(Minecraft mc) {
        if (matches.isEmpty() && (activeQuery == null || activeQuery.isEmpty())) {
            return;
        }
        String needle = normalize(activeQuery);

        // 1. Purge positions that are verified air / not a container any more.
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, String>> container : StorageTracker.storageData.entrySet()) {
            String key = container.getKey();
            if (key == null || !key.startsWith(ISLAND_CHEST_PREFIX)) continue;
            BlockPos pos = parsePosition(key);
            if (pos == null) {
                broken.add(key);
                continue;
            }
            if (mc.level == null || !mc.level.isLoaded(pos)) continue;
            BlockState state = mc.level.getBlockState(pos);
            if (!isContainer(state)) {
                broken.add(key);
            }
        }
        for (String key : broken) {
            StorageTracker.storageData.remove(key);
        }
        if (!broken.isEmpty()) {
            StorageTracker.save();
        }

        if (needle == null || needle.isEmpty()) return;

        // 2. Refresh the counts for the chests we are highlighting.
        Iterator<ChestMatch> iterator = matches.iterator();
        while (iterator.hasNext()) {
            ChestMatch match = iterator.next();
            Map<Integer, String> contents = StorageTracker.storageData.get(match.containerKey);
            if (contents == null) {
                iterator.remove();
                continue;
            }
            int hitSlots = 0;
            long quantity = 0L;
            for (String nbt : contents.values()) {
                if (nbt == null) continue;
                if (normalize(nbt).contains(needle)) {
                    hitSlots++;
                    quantity += stackCount(nbt);
                }
            }
            if (hitSlots == 0) {
                // Everything matching was taken out - the waypoint goes away.
                iterator.remove();
                continue;
            }
            match.itemCount = hitSlots;
            match.totalQuantity = quantity;
            match.partial = hitSlots > 0;
        }
    }

    /** Called after a container closes so a chest whose item was just taken updates immediately. */
    public static void onContainerChanged() {
        Minecraft mc = Minecraft.getInstance();
        verify(mc);
    }

    public static void onClientTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastVerify < 1000L) return;
        lastVerify = now;
        try {
            verify(mc);
        } catch (Throwable ignored) {
        }
    }

    // -------------------------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------------------------

    public static void render(LevelRenderContext context) {
        List<ChestMatch> snapshot;
        synchronized (StorageChestWaypoints.class) {
            if (matches.isEmpty()) return;
            snapshot = new ArrayList<>(matches);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        PoseStack poseStack = context.poseStack();
        OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());

        for (ChestMatch match : snapshot) {
            double x = match.minX - camPos.x;
            double y = match.minY - camPos.y;
            double z = match.minZ - camPos.z;
            double width = (match.maxX - match.minX) + 1.0D;
            double depth = (match.maxZ - match.minZ) + 1.0D;

            float r = match.partial ? 0.10F : 0.55F;
            float g = 0.85F;
            float b = 1.0F;
            AABB box = new AABB(x, y, z, x + width, y + 1.0D, z + depth);
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) ->
                    BomboRenderUtils.drawBox(pose.pose(), vc, box, r, g, b, 1.0F, 2.5F));
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) ->
                    BomboRenderUtils.drawBox(pose.pose(), vc, box.inflate(0.03D), r, g, b, 0.35F, 2.0F));

            String label = "§b" + (match.totalQuantity > 1
                    ? match.totalQuantity + "x "
                    : "") + match.itemLabel;
            String sub = "§8" + match.itemCount + " slot" + (match.itemCount == 1 ? "" : "s")
                    + (match.partial ? " §7- items left" : " §7- full");
            BomboRenderUtils.drawText(poseStack, collector, label, (float) x + (float) width / 2.0F,
                    (float) y + 1.4F, (float) z + (float) depth / 2.0F, 0x00E5FF, 0.035F, true, true);
            BomboRenderUtils.drawText(poseStack, collector, sub, (float) x + (float) width / 2.0F,
                    (float) y + 1.15F, (float) z + (float) depth / 2.0F, 0x94A3B8, 0.025F, true, true);
        }
    }

    /** Chat summary for the command that started the search. */
    public static synchronized String describe() {
        if (activeQuery == null || activeQuery.isEmpty()) {
            return "§7No storage search active.";
        }
        if (matches.isEmpty()) {
            return "§7No island chest currently holds §e" + activeQuery + "§7.";
        }
        int slots = 0;
        long quantity = 0L;
        for (ChestMatch match : matches) {
            slots += match.itemCount;
            quantity += match.totalQuantity;
        }
        return "§7Found §e" + matches.size() + " §7chest" + (matches.size() == 1 ? "" : "s")
                + " holding §b" + (quantity > 0 ? quantity + "x " : "") + activeQuery
                + " §7(§f" + slots + " slot" + (slots == 1 ? "" : "s") + "§7) - waypoints active.";
    }

    // -------------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------------

    private static boolean isContainer(BlockState state) {
        if (state == null) return false;
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private static long stackCount(String nbt) {
        Matcher matcher = COUNT_PATTERN.matcher(nbt);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1L;
    }

    /** {@code "Island Chest @ 12, 71, -30"} -> BlockPos. */
    public static BlockPos parsePosition(String key) {
        if (key == null || !key.startsWith(ISLAND_CHEST_PREFIX)) return null;
        String coords = key.substring(ISLAND_CHEST_PREFIX.length()).trim();
        String[] parts = coords.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Lower-cases and strips § codes / separators so NBT text and user input compare cleanly. */
    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("§.", "")
                .replaceAll("[^a-z0-9]", "");
    }

    /** Convenience for the command: chat line describing the search that ran. */
    public static Component describeComponent() {
        return Component.literal(describe());
    }
}
