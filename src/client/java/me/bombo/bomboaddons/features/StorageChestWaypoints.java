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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.Direction;
import me.bombo.bomboaddons.SlotHighlight;
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
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

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
    public static final class DecodedItem {
        public final String nameAndId;
        public final String loreText;
        public final String searchString;
        public final long count;
        public final String displayName;
        public final String skyblockId;

        public DecodedItem(String nameAndId, String loreText, String searchString, long count, String displayName, String skyblockId) {
            this.nameAndId = nameAndId;
            this.loreText = loreText;
            this.searchString = searchString;
            this.count = count;
            this.displayName = displayName;
            this.skyblockId = skyblockId != null ? skyblockId : "";
        }

        public DecodedItem(String nameAndId, String loreText, String searchString, long count, String displayName) {
            this(nameAndId, loreText, searchString, count, displayName, "");
        }
    }

    private static final Map<String, DecodedItem> DECODE_CACHE = new ConcurrentHashMap<>();

    private static String extractCleanText(Tag t) {
        if (t == null) return "";
        String raw = t.asString().orElse(t.toString());
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }
        raw = raw.replaceAll("§.", "");
        if (raw.contains("\"text\"")) {
            Matcher m = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"").matcher(raw);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                sb.append(m.group(1)).append(" ");
            }
            if (sb.length() > 0) {
                return sb.toString().trim();
            }
        }
        return raw.trim();
    }

    public static DecodedItem getDecoded(String nbtStr) {
        if (nbtStr == null || nbtStr.isEmpty()) {
            return new DecodedItem("", "", "", 0L, "");
        }
        DecodedItem cached = DECODE_CACHE.get(nbtStr);
        if (cached != null) {
            return cached;
        }

        try {
            CompoundTag tag;
            if (nbtStr.startsWith("B64:")) {
                byte[] decoded = Base64.getDecoder().decode(nbtStr.substring(4));
                ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
                tag = NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap());
            } else {
                tag = TagParser.parseCompoundFully(nbtStr);
            }

            long count = 1L;
            if (tag.contains("count")) {
                count = (Integer) tag.getInt("count").orElse(1);
            } else if (tag.contains("Count")) {
                count = (Byte) tag.getByte("Count").orElse((byte) 1);
            }

            String vanillaId = tag.getString("id").orElse("");
            if (vanillaId.startsWith("minecraft:")) {
                vanillaId = vanillaId.substring(10);
            }

            String displayName = "";
            String skyblockId = "";
            StringBuilder loreBuilder = new StringBuilder();

            CompoundTag components = tag.getCompound("components").orElse(null);
            if (components != null) {
                if (components.contains("minecraft:custom_name")) {
                    displayName = extractCleanText(components.get("minecraft:custom_name"));
                }
                if (displayName.isEmpty() && components.contains("minecraft:item_name")) {
                    displayName = extractCleanText(components.get("minecraft:item_name"));
                }
                CompoundTag customData = components.getCompound("minecraft:custom_data").orElse(null);
                if (customData != null) {
                    CompoundTag ea = customData.getCompound("ExtraAttributes").orElse(null);
                    if (ea != null && ea.contains("id")) {
                        skyblockId = ea.getString("id").orElse("");
                    } else if (customData.contains("id")) {
                        skyblockId = customData.getString("id").orElse("");
                    }
                }
                net.minecraft.nbt.ListTag loreList = components.getList("minecraft:lore").orElse(null);
                if (loreList != null) {
                    for (int i = 0; i < loreList.size(); i++) {
                        loreBuilder.append(extractCleanText(loreList.get(i))).append(" ");
                    }
                }
            }

            if (tag.contains("display")) {
                CompoundTag display = tag.getCompound("display").orElse(null);
                if (display != null) {
                    if (displayName.isEmpty() && display.contains("Name")) {
                        displayName = extractCleanText(display.get("Name"));
                    }
                    if (display.contains("Lore")) {
                        net.minecraft.nbt.ListTag loreList = display.getList("Lore").orElse(null);
                        if (loreList != null) {
                            for (int i = 0; i < loreList.size(); i++) {
                                loreBuilder.append(extractCleanText(loreList.get(i))).append(" ");
                            }
                        }
                    }
                }
            }
            if (skyblockId.isEmpty() && tag.contains("ExtraAttributes")) {
                CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                if (ea != null && ea.contains("id")) {
                    skyblockId = ea.getString("id").orElse("");
                }
            }

            if (displayName.isEmpty()) {
                displayName = vanillaId;
            }

            String nameAndId = normalize(displayName + " " + skyblockId + " " + vanillaId);
            String loreText = normalize(loreBuilder.toString());
            String fullText = normalize(tag.toString());

            DecodedItem item = new DecodedItem(nameAndId, loreText, fullText, Math.max(1L, count), displayName, skyblockId);
            DECODE_CACHE.put(nbtStr, item);
            return item;
        } catch (Throwable t) {
            DecodedItem fallback = new DecodedItem(normalize(nbtStr), "", normalize(nbtStr), 1L, "");
            DECODE_CACHE.put(nbtStr, fallback);
            return fallback;
        }
    }

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
        SlotHighlight.clearTargetSlot();
        me.bombo.bomboaddons.BlockHighlight.targetChestPosList.clear();
    }

    public static synchronized void clearActiveQuery() {
        activeQuery = null;
    }

    public static synchronized boolean removeMatchAt(BlockPos pos) {
        if (pos == null || matches.isEmpty()) return false;
        boolean removed = false;
        Iterator<ChestMatch> it = matches.iterator();
        while (it.hasNext()) {
            ChestMatch m = it.next();
            if (pos.getX() >= m.minX && pos.getX() <= m.maxX &&
                pos.getY() >= m.minY && pos.getY() <= m.maxY &&
                pos.getZ() >= m.minZ && pos.getZ() <= m.maxZ) {
                it.remove();
                removed = true;
            }
        }
        if (matches.isEmpty()) {
            activeQuery = null;
        }
        return removed;
    }

    // -------------------------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------------------------

    /**
     * Builds waypoints for every island chest containing {@code query}.
     * Supports "l:<lore>" or "lore:<lore>" to search lore specifically.
     *
     * @return number of chests highlighted
     */
    public static synchronized int search(String query) {
        matches.clear();
        activeQuery = query;
        if (query == null || query.trim().isEmpty()) {
            SlotHighlight.clearTargetSlot();
            me.bombo.bomboaddons.BlockHighlight.targetChestPosList.clear();
            return 0;
        }

        boolean searchLore = false;
        String cleanQuery = query.trim();
        String lower = cleanQuery.toLowerCase(Locale.ROOT);
        if (lower.startsWith("l:") || lower.startsWith("lore:")) {
            searchLore = true;
            cleanQuery = cleanQuery.substring(cleanQuery.indexOf(':') + 1).trim();
        }
        SlotHighlight.addTargetName(cleanQuery, -1442775296);

        String needle = normalize(cleanQuery);
        me.bombo.bomboaddons.BlockHighlight.targetChestPosList.clear();

        // containerKey -> matched slot count / quantity
        Map<String, int[]> hits = new LinkedHashMap<>();
        Map<String, Long> quantities = new LinkedHashMap<>();

        for (Map.Entry<String, Map<Integer, String>> container : StorageTracker.storageData.entrySet()) {
            String key = container.getKey();
            if (key == null || (!key.startsWith(ISLAND_CHEST_PREFIX) && !key.startsWith("Island Double Chest @ "))) {
                continue;
            }
            int hitSlots = 0;
            long quantity = 0L;
            for (String nbt : container.getValue().values()) {
                if (nbt == null) continue;
                DecodedItem decoded = getDecoded(nbt);
                boolean matched = matchesItem(decoded, needle, searchLore);
                if (matched) {
                    hitSlots++;
                    quantity += decoded.count;
                }
            }
            if (hitSlots > 0) {
                hits.put(key, new int[]{hitSlots});
                quantities.put(key, quantity);
            }
        }

        for (Map.Entry<String, int[]> hit : hits.entrySet()) {
            String key = hit.getKey();
            ChestMatch match = new ChestMatch();
            match.containerKey = key;
            if (key.startsWith("Island Double Chest @ ")) {
                BlockPos[] pair = parseDoublePosition(key);
                if (pair == null) continue;
                match.minX = Math.min(pair[0].getX(), pair[1].getX());
                match.minY = Math.min(pair[0].getY(), pair[1].getY());
                match.minZ = Math.min(pair[0].getZ(), pair[1].getZ());
                match.maxX = Math.max(pair[0].getX(), pair[1].getX());
                match.maxY = Math.max(pair[0].getY(), pair[1].getY());
                match.maxZ = Math.max(pair[0].getZ(), pair[1].getZ());
                me.bombo.bomboaddons.BlockHighlight.targetChestPosList.add(pair[0]);
                me.bombo.bomboaddons.BlockHighlight.targetChestPosList.add(pair[1]);
            } else {
                BlockPos pos = parsePosition(key);
                if (pos == null) continue;
                match.minX = pos.getX();
                match.minY = pos.getY();
                match.minZ = pos.getZ();
                match.maxX = pos.getX();
                match.maxY = pos.getY();
                match.maxZ = pos.getZ();
                me.bombo.bomboaddons.BlockHighlight.targetChestPosList.add(pos);
            }
            match.itemCount = hit.getValue()[0];
            match.totalQuantity = quantities.getOrDefault(key, (long) match.itemCount);
            match.itemLabel = cleanQuery;
            matches.add(match);
        }

        if (!matches.isEmpty()) {
            me.bombo.bomboaddons.BlockHighlight.targetChestTime = System.currentTimeMillis();
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

    public static boolean matchesItem(DecodedItem decoded, String needle, boolean searchLore) {
        if (decoded == null || needle == null || needle.isEmpty()) return false;
        if (searchLore) {
            if (decoded.loreText.contains(needle)) return true;
        } else {
            if (decoded.nameAndId.contains(needle)) return true;
        }

        String sbId = decoded.skyblockId;
        if (sbId != null && !sbId.isEmpty()) {
            if (needle.equals("museum") || needle.equals("donatable")) {
                if (me.bombo.bomboaddons.features.museum.MuseumManager.isDonatable(sbId)) return true;
                if (needle.equals("museum") && me.bombo.bomboaddons.features.museum.MuseumManager.isMuseumItem(sbId)) return true;
            } else if (needle.equals("donated")) {
                if (me.bombo.bomboaddons.features.museum.MuseumManager.isDonated(sbId)) return true;
            }

            if (needle.equals("sack") || needle.equals("sacks")) {
                if (me.bombo.bomboaddons.features.sacks.SacksManager.fitsInSack(sbId)) return true;
            }

            if (searchLore) {
                if (me.bombo.bomboaddons.features.museum.MuseumManager.isDonatable(sbId)) {
                    if ("donatabletomuseum".contains(needle)) return true;
                    String counts = me.bombo.bomboaddons.features.museum.MuseumManager.getCountsAsDescription(sbId);
                    if (counts != null && counts.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").contains(needle)) return true;
                }
                if (me.bombo.bomboaddons.features.museum.MuseumManager.isDonated(sbId)) {
                    if ("donatedtomuseum".contains(needle)) return true;
                }
                if (me.bombo.bomboaddons.features.sacks.SacksManager.fitsInSack(sbId)) {
                    if ("fitsinsacks".contains(needle)) return true;
                    String sName = me.bombo.bomboaddons.features.sacks.SacksManager.getPrimarySackName(sbId);
                    if (sName != null && sName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").contains(needle)) return true;
                }
            }
        }
        return false;
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

        boolean searchLore = false;
        String cleanQuery = activeQuery != null ? activeQuery.trim() : "";
        String lower = cleanQuery.toLowerCase(Locale.ROOT);
        if (lower.startsWith("l:") || lower.startsWith("lore:")) {
            searchLore = true;
            cleanQuery = cleanQuery.substring(cleanQuery.indexOf(':') + 1).trim();
        }
        String needle = normalize(cleanQuery);

        // 1. Purge positions that are verified air / not a container any more.
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, String>> container : StorageTracker.storageData.entrySet()) {
            String key = container.getKey();
            if (key == null) continue;
            if (key.startsWith(ISLAND_CHEST_PREFIX)) {
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
            } else if (key.startsWith("Island Double Chest @ ")) {
                BlockPos[] pair = parseDoublePosition(key);
                if (pair == null) {
                    broken.add(key);
                    continue;
                }
                if (mc.level == null) continue;
                if (mc.level.isLoaded(pair[0]) && mc.level.isLoaded(pair[1])) {
                    BlockState s0 = mc.level.getBlockState(pair[0]);
                    BlockState s1 = mc.level.getBlockState(pair[1]);
                    if (!isContainer(s0) || !isContainer(s1)) {
                        broken.add(key);
                    }
                }
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
                DecodedItem decoded = getDecoded(nbt);
                boolean matched = searchLore ? decoded.loreText.contains(needle) : decoded.nameAndId.contains(needle);
                if (matched) {
                    hitSlots++;
                    quantity += decoded.count;
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
            if (match.minX == match.maxX && match.minZ == match.maxZ) {
                BlockPos p = new BlockPos(match.minX, match.minY, match.minZ);
                if (mc.level.isLoaded(p)) {
                    BlockState state = mc.level.getBlockState(p);
                    if (state.getBlock() instanceof ChestBlock) {
                        ChestType type = state.hasProperty(BlockStateProperties.CHEST_TYPE) ? state.getValue(BlockStateProperties.CHEST_TYPE) : ChestType.SINGLE;
                        if (type != ChestType.SINGLE) {
                            Direction connected = ChestBlock.getConnectedDirection(state);
                            BlockPos partner = p.relative(connected);
                            match.minX = Math.min(p.getX(), partner.getX());
                            match.maxX = Math.max(p.getX(), partner.getX());
                            match.minZ = Math.min(p.getZ(), partner.getZ());
                            match.maxZ = Math.max(p.getZ(), partner.getZ());
                        }
                    }
                }
            }

            double x = match.minX - camPos.x;
            double y = match.minY - camPos.y;
            double z = match.minZ - camPos.z;
            double width = (match.maxX - match.minX) + 1.0D;
            double depth = (match.maxZ - match.minZ) + 1.0D;

            float r = match.partial ? 0.10F : 0.55F;
            float g = 0.85F;
            float b = 1.0F;
            AABB box = new AABB(x, y, z, x + width, y + 1.0D, z + depth);
            collector.submitCustomGeometry(poseStack, RenderTypes.secondaryBlockOutline(), (pose, vc) ->
                    BomboRenderUtils.drawBox(pose.pose(), vc, box, r, g, b, 1.0F, 2.5F));
            collector.submitCustomGeometry(poseStack, RenderTypes.secondaryBlockOutline(), (pose, vc) ->
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

    public static boolean isContainer(BlockState state) {
        if (state == null) return false;
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private static long stackCount(String nbt) {
        return getDecoded(nbt).count;
    }

    /** {@code "Island Chest @ 12, 71, -30"} -> BlockPos. */
    public static BlockPos parsePosition(String key) {
        if (key == null) return null;
        if (key.startsWith("Island Double Chest @ ")) {
            BlockPos[] pair = parseDoublePosition(key);
            return pair != null ? pair[0] : null;
        }
        if (!key.startsWith(ISLAND_CHEST_PREFIX)) return null;
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

    public static BlockPos[] parseDoublePosition(String key) {
        if (key == null || !key.startsWith("Island Double Chest @ ")) return null;
        String coords = key.substring("Island Double Chest @ ".length()).trim();
        String[] halves = coords.split("&");
        if (halves.length != 2) return null;
        String[] p0 = halves[0].trim().split(",");
        String[] p1 = halves[1].trim().split(",");
        if (p0.length != 3 || p1.length != 3) return null;
        try {
            BlockPos pos0 = new BlockPos(Integer.parseInt(p0[0].trim()), Integer.parseInt(p0[1].trim()), Integer.parseInt(p0[2].trim()));
            BlockPos pos1 = new BlockPos(Integer.parseInt(p1[0].trim()), Integer.parseInt(p1[1].trim()), Integer.parseInt(p1[2].trim()));
            return new BlockPos[]{pos0, pos1};
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
