package me.bombo.bomboaddons;

import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3fc;

public class HighlightESP {
   public static final List<HighlightTracer> TRACERS = new ArrayList<>();
   public static final Set<Integer> ignoredEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
   public static final Map<String, String> ALIAS_MAP = new ConcurrentHashMap<>();
   private static final java.util.concurrent.atomic.AtomicBoolean isFetchingAliases = new java.util.concurrent.atomic.AtomicBoolean(
         false);
   private static long lastAliasFetchTime = 0L;

   public static Matrix4f lastViewMatrix = new Matrix4f();
   public static Matrix4f lastProjMatrix = new Matrix4f();
   public static int lastEntityCount = 0;
   public static int lastTracerChecks = 0;
   public static int lastTracersAdded = 0;

   static {
      ALIAS_MAP.put("sos", "c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b");
      ALIAS_MAP.put("c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b", "sos");
   }

   public static void fetchOnlineAliasesAsync() {
      long now = System.currentTimeMillis();
      if (now - lastAliasFetchTime < 300000L)
         return;
      if (!isFetchingAliases.compareAndSet(false, true))
         return;
      lastAliasFetchTime = now;
      Thread t = new Thread(() -> {
         try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            String[] endpoints = new String[] {
                  "https://api.bombo.dpdns.org/mod/highlight",
                  "https://api.bombo.dpdns.org/highlight",
                  "https://bombo.dpdns.org/api/highlight"
            };
            for (String url : endpoints) {
               try {
                  HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).build();
                  HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                  if (resp.statusCode() == 200 && resp.body() != null) {
                     String body = resp.body().trim();
                     if (body.startsWith("{")) {
                        JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                           if (entry.getValue().isJsonPrimitive()) {
                              String hashOrKey = entry.getKey().trim().replaceAll("^\"+|\"+$", "").toLowerCase();
                              String alias = entry.getValue().getAsString().trim().replaceAll("^\"+|\"+$", "")
                                    .toLowerCase();
                              if (!hashOrKey.isEmpty() && !alias.isEmpty()) {
                                 ALIAS_MAP.put(alias, hashOrKey);
                                 ALIAS_MAP.put(hashOrKey, alias);
                              }
                           }
                        }
                        break;
                     } else if (!body.isEmpty()) {
                        String[] lines = body.split("\\r?\\n");
                        boolean foundAny = false;
                        for (String line : lines) {
                           String trimmed = line.trim();
                           if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//"))
                              continue;
                           String[] parts = trimmed.split("[\\t,:]+|\\s{2,}", 2);
                           if (parts.length == 2) {
                              String k = parts[0].trim().replaceAll("^\"+|\"+$", "").toLowerCase();
                              String v = parts[1].trim().replaceAll("^\"+|\"+$", "").toLowerCase();
                              if (!k.isEmpty() && !v.isEmpty()) {
                                 ALIAS_MAP.put(k, v);
                                 ALIAS_MAP.put(v, k);
                                 foundAny = true;
                              }
                           } else {
                              int firstSpace = trimmed.indexOf(' ');
                              if (firstSpace > 0) {
                                 String k = trimmed.substring(0, firstSpace).trim().replaceAll("^\"+|\"+$", "")
                                       .toLowerCase();
                                 String v = trimmed.substring(firstSpace + 1).trim().replaceAll("^\"+|\"+$", "")
                                       .toLowerCase();
                                 if (!k.isEmpty() && !v.isEmpty()) {
                                    ALIAS_MAP.put(k, v);
                                    ALIAS_MAP.put(v, k);
                                    foundAny = true;
                                 }
                              }
                           }
                        }
                        if (foundAny)
                           break;
                     }
                  }
               } catch (Throwable ignored) {
               }
            }
         } catch (Throwable ignored) {
         } finally {
            isFetchingAliases.set(false);
         }
      }, "Bombo-Highlight-Alias-Fetcher");
      t.setDaemon(true);
      t.start();
   }

   public static class EntityHighlightInfo {
      public final long timestamp;
      public final boolean highlighted;
      public final Integer glowColor;
      public final boolean isTracer;
      public final int tracerColor;
      public final boolean showInvisible;
      public final boolean highlightHeadOnly;
      public boolean isVisible;
      public long visibilityTimestamp;

      public EntityHighlightInfo(long timestamp, boolean highlighted, Integer glowColor, boolean isTracer,
            int tracerColor, boolean showInvisible) {
         this(timestamp, highlighted, glowColor, isTracer, tracerColor, showInvisible, false);
      }

      public EntityHighlightInfo(long timestamp, boolean highlighted, Integer glowColor, boolean isTracer,
            int tracerColor, boolean showInvisible, boolean highlightHeadOnly) {
         this.timestamp = timestamp;
         this.highlighted = highlighted;
         this.glowColor = glowColor;
         this.isTracer = isTracer;
         this.tracerColor = tracerColor;
         this.showInvisible = showInvisible;
         this.highlightHeadOnly = highlightHeadOnly;
         this.isVisible = false;
         this.visibilityTimestamp = 0L;
      }
   }

   public static final Map<Integer, EntityHighlightInfo> HIGHLIGHT_CACHE = new ConcurrentHashMap<>();
   public static final java.util.Set<Integer> ALERTED_SPAWN_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();

   public static void clearHighlightCache() {
      HIGHLIGHT_CACHE.clear();
      ALERTED_SPAWN_IDS.clear();
      TargetPests.infoCache.clear();
   }

   public static boolean matchesIsland(String requiredIsland) {
      if (requiredIsland == null || requiredIsland.trim().isEmpty() || "All".equalsIgnoreCase(requiredIsland.trim())
            || "*".equals(requiredIsland.trim())) {
         return true;
      }
      String current = BomboaddonsClient.currentArea;
      if (current == null || current.isEmpty()) {
         current = SkyblockUtils.getLocation();
      }
      if (current == null || current.isEmpty())
         return false;
      String req = requiredIsland.trim();
      if (req.startsWith("!")) {
         String negated = req.substring(1).trim().toLowerCase(java.util.Locale.ROOT);
         String curr = current.toLowerCase(java.util.Locale.ROOT);
         if (negated.equals("catacombs") || negated.equals("dungeon") || negated.equals("dungeons")) {
            return !curr.contains("catacombs") && !curr.contains("dungeon");
         }
         return !curr.contains(negated);
      }
      String reqLower = req.toLowerCase(java.util.Locale.ROOT);
      String currLower = current.toLowerCase(java.util.Locale.ROOT);
      if (reqLower.equals("catacombs") || reqLower.equals("dungeon") || reqLower.equals("dungeons")) {
         return currLower.contains("catacombs") || currLower.contains("dungeon");
      }
      if (reqLower.contains("garden") && (currLower.contains("garden") || currLower.contains("plot") || SkyblockUtils.isInGarden())) {
         return true;
      }
      return currLower.contains(reqLower);
   }

   public static boolean hasActiveTracers(BomboConfig.Settings s) {
      if (s == null)
         return false;
      if (s.tracerTestAllEntities || s.cheeseTracer)
         return true;
      if (s.dungeonKeyHighlight && s.dungeonKeyTracers && matchesIsland("Catacombs"))
         return true;
      if (s.dungeonStarredMobHighlight && s.dungeonStarredMobTracers)
         return true;
      if (s.customTracers != null && !s.customTracers.isEmpty())
         return true;
      if (s.highlightsEnabled && s.highlights != null) {
         for (BomboConfig.HighlightInfo hi : s.highlights.values()) {
            if (hi != null && hi.enabled && hi.tracer && matchesIsland(hi.requiredIsland))
               return true;
         }
      }
      return false;
   }

   public static final Map<Integer, EntityHighlightInfo> ACTIVE_TRACER_INFOS = new ConcurrentHashMap<>();
   public static final Set<Integer> activeTracerEntityIds = ConcurrentHashMap.newKeySet();
   public static final Set<Integer> activeHeadHighlightEntityIds = ConcurrentHashMap.newKeySet();

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         activeTracerEntityIds.clear();
         activeHeadHighlightEntityIds.clear();
         ACTIVE_TRACER_INFOS.clear();
         return;
      }
      if (mc.player.tickCount % 10 != 0) {
         return;
      }
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || (!s.highlightsEnabled && !s.dungeonKeyHighlight && !s.tracerTestAllEntities && !s.cheeseTracer && (s.customTracers == null || s.customTracers.isEmpty()))) {
         activeTracerEntityIds.clear();
         activeHeadHighlightEntityIds.clear();
         ACTIVE_TRACER_INFOS.clear();
         return;
      }
      Set<Integer> newTracers = new HashSet<>();
      Set<Integer> newHeadHighlights = new HashSet<>();
      Map<Integer, EntityHighlightInfo> newTracerInfos = new HashMap<>();
      try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("HighlightESP: Entity Scan")) {
         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || entity == mc.player || entity.isRemoved() || ignoredEntities.contains(entity.getId()))
               continue;
            if (mc.player.distanceToSqr(entity) > 16384.0) // skip entities > 128 blocks away
               continue;
            EntityHighlightInfo info = getHighlightInfo(entity);
            if (info != null) {
               if (info.isTracer && hasActiveTracers(s)) {
                  newTracers.add(entity.getId());
                  newTracerInfos.put(entity.getId(), info);
               }
               if (info.highlightHeadOnly) {
                  newHeadHighlights.add(entity.getId());
               }
            }
         }
      }
      activeTracerEntityIds.clear();
      activeTracerEntityIds.addAll(newTracers);
      ACTIVE_TRACER_INFOS.clear();
      ACTIVE_TRACER_INFOS.putAll(newTracerInfos);
      activeHeadHighlightEntityIds.clear();
      activeHeadHighlightEntityIds.addAll(newHeadHighlights);
   }

   public static void render(LevelRenderContext context) {
      try {
         TRACERS.clear();
         BomboConfig.Settings s = BomboConfig.get();
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null || mc.player == null) {
            return;
         }
         if (context.poseStack() != null && !context.poseStack().isEmpty()) {
            lastViewMatrix.set(context.poseStack().last().pose());
         }
         try {
            if (context.levelState() != null && context.levelState().cameraRenderState != null
                  && context.levelState().cameraRenderState.projectionMatrix != null) {
               lastProjMatrix.set(context.levelState().cameraRenderState.projectionMatrix);
            }
         } catch (Throwable ignored) {
         }
         lastEntityCount = 0;
         lastTracerChecks = 0;
         lastTracersAdded = 0;
         Vec3 camPos = mc.gameRenderer.mainCamera().position();
         PoseStack poseStack = context.poseStack();
         OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());

         // Render custom head-only highlights (e.g. Dungeon Keys)
         if (!activeHeadHighlightEntityIds.isEmpty()) {
            try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("HighlightESP: Head Boxes")) {
               for (Integer id : activeHeadHighlightEntityIds) {
                  Entity entity = mc.level.getEntity(id);
                  if (entity == null || entity == mc.player || ignoredEntities.contains(id))
                     continue;
                  EntityHighlightInfo info = getHighlightInfo(entity);
                  if (info == null || !info.highlightHeadOnly)
                     continue;
                  int colorInt = info.glowColor != null ? info.glowColor : 0xFFFFFF;
                  float r = (float) (colorInt >> 16 & 0xFF) / 255.0F;
                  float g = (float) (colorInt >> 8 & 0xFF) / 255.0F;
                  float b = (float) (colorInt & 0xFF) / 255.0F;
                  double x = entity.getX() - camPos.x;
                  double y = entity.getY() - camPos.y;
                  double z = entity.getZ() - camPos.z;
                  AABB headBox = new AABB(x - 0.45, y + 1.25, z - 0.45, x + 0.45, y + 2.05, z + 0.45);
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(),
                        (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, headBox, r, g, b, 1.0F, 2.0F));
                  collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
                        (pose, vertexConsumer) -> BomboRenderUtils.drawFilledBox(pose.pose(), vertexConsumer, headBox, r, g, b, 0.25F));
               }
            }
         }

         if (!hasActiveTracers(s)) {
            return;
         }
         if (!s.tracerTestAllEntities && activeTracerEntityIds.isEmpty()) {
            return;
         }
         Vector3fc look = mc.gameRenderer.mainCamera().forwardVector();
         float startX = look.x();
         float startY = look.y();
         float startZ = look.z();

         try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("HighlightESP: Tracers")) {
            if (s.tracerTestAllEntities) {
               for (Entity entity : mc.level.entitiesForRendering()) {
                  if (entity == mc.player || ignoredEntities.contains(entity.getId()))
                     continue;
                  float endX = (float) (entity.getX() - camPos.x);
                  float endY = (float) (entity.getY() - camPos.y + (double) (entity.getBbHeight() / 2.0F));
                  float endZ = (float) (entity.getZ() - camPos.z);
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(),
                        (pose, vertexConsumer) -> BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY,
                              startZ, endX, endY, endZ, 1.0F, 1.0F, 0.0F, 1.0F, 2.0F));
               }
               return;
            }

            for (Integer id : activeTracerEntityIds) {
               Entity entity = mc.level.getEntity(id);
               if (entity == null || entity == mc.player || ignoredEntities.contains(id))
                  continue;
               ++lastEntityCount;
               EntityHighlightInfo info = ACTIVE_TRACER_INFOS.get(id);
               if (info == null) {
                  info = getHighlightInfo(entity);
               }
               if (info == null || !info.isTracer)
                  continue;
               if (s.hideCheats && (entity.isInvisible() || !HighlightESP.isEntityVisibleCached(mc, entity))) {
                  continue;
               }

               float endX = (float) (entity.getX() - camPos.x);
               float endY = (float) (entity.getY() - camPos.y + (double) (entity.getBbHeight() / 2.0F));
               float endZ = (float) (entity.getZ() - camPos.z);
               int colorInt = info.tracerColor;
               float r = (float) (colorInt >> 16 & 0xFF) / 255.0F;
               float g = (float) (colorInt >> 8 & 0xFF) / 255.0F;
               float b = (float) (colorInt & 0xFF) / 255.0F;
               float trWidth = Math.max(0.5F, Math.min(s.tracerWidth, 10.0F));
               collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(),
                     (pose, vertexConsumer) -> BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY,
                           startZ, endX, endY, endZ, r, g, b, 1.0F, trWidth));
               HighlightTracer tracer = new HighlightTracer();
               tracer.start = new Vector2f(startX, startY);
               tracer.end = new Vector2f(endX, endY);
               tracer.color = colorInt;
               TRACERS.add(tracer);
               ++lastTracersAdded;
            }
         }
      } catch (Throwable ignored) {
      }
   }

   public static boolean matchesKey(String text, String key) {
      if (text == null || text.isEmpty() || key == null || key.isEmpty())
         return false;
      if (text.contains(key))
         return true;
      String lKey = key.toLowerCase();
      String lText = text.toLowerCase();
      if (lText.contains(lKey))
         return true;
      String mappedHash = ALIAS_MAP.get(lKey);
      if (mappedHash != null && lText.contains(mappedHash))
         return true;
      return false;
   }

   public static TargetPests.EntityInfoCache getCachedInfo(Entity self) {
      if (TargetPests.infoCache.size() > 2000) {
         TargetPests.infoCache.clear();
      }
      int id = self.getId();
      long now = System.currentTimeMillis();
      TargetPests.EntityInfoCache cached = TargetPests.infoCache.get(id);
      if (cached != null && now - cached.lastCheckMs < 2000L) {
         return cached;
      }
      String name = ChatFormatting.stripFormatting(self.getDisplayName().getString());
      StringBuilder combinedName = new StringBuilder(name != null ? name.toLowerCase() : "");

      String selfTex = TargetPests.getHeadTextureValue(self);
      if (selfTex != null) {
         String hash = TargetPests.extractTextureHash(selfTex);
         if (hash != null) {
            if (combinedName.length() > 0)
               combinedName.append(" | ");
            combinedName.append(hash.toLowerCase());
         }
      }

      if (self instanceof ArmorStand) {
         String pestName = TargetPests.getPestName((ArmorStand) self);
         if (pestName != null) {
            if (combinedName.length() > 0)
               combinedName.append(" | ");
            combinedName.append(pestName);
         }
      }
      for (Entity passenger : self.getPassengers()) {
         String pName = ChatFormatting.stripFormatting(passenger.getDisplayName().getString());
         if (pName != null) {
            if (combinedName.length() > 0)
               combinedName.append(" | ");
            combinedName.append(pName.toLowerCase());
         }
         String pTex = TargetPests.getHeadTextureValue(passenger);
         if (pTex != null) {
            String pHash = TargetPests.extractTextureHash(pTex);
            if (pHash != null) {
               if (combinedName.length() > 0)
                  combinedName.append(" | ");
               combinedName.append(pHash.toLowerCase());
            }
         }
      }

      String finalCombined = combinedName.toString();
      String nametag = null;
      if (!(self instanceof Player) && !(self instanceof ArmorStand)) {
         nametag = HighlightESP.getNearbyNametagName(self);
      }
      TargetPests.EntityInfoCache newValue = new TargetPests.EntityInfoCache(now, finalCombined, nametag);
      TargetPests.infoCache.put(id, newValue);
      return newValue;
   }

   private static final Map<Integer, Long> NAMETAG_CHECK_CACHE = new ConcurrentHashMap<>();
   private static final Set<Integer> NAMETAG_LIVING_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());

   public static String getNearbyNametagName(Entity target) {
      if (target == null || target.level() == null)
         return null;
      AABB box = target.getBoundingBox().inflate(0.5, 2.5, 0.5);
      for (Entity e2 : target.level().getEntities(target, box)) {
         if (!(e2 instanceof ArmorStand) || !e2.hasCustomName())
            continue;
         String name = ChatFormatting.stripFormatting(e2.getCustomName().getString());
         if (name != null && !name.isEmpty()) {
            return cleanMobNametag(name);
         }
      }
      return null;
   }

   public static String cleanMobNametag(String raw) {
      if (raw == null)
         return "";
      String stripped = ChatFormatting.stripFormatting(raw).trim();
      // Remove trailing/leading health like [Lv100] Crypt Ghoul 2000/2000❤ or Crypt
      // Ghoul 1.5k/2k ❤
      String cleaned = stripped.replaceAll("(?i)\\s*\\d+(\\.\\d+)?[kmb]?/\\d+(\\.\\d+)?[kmb]?\\s*❤?", "")
            .replaceAll("(?i)\\s*\\d+(\\.\\d+)?[kmb]?\\s*❤", "")
            .replaceAll("(?i)\\[lv\\s*\\d+\\]", "")
            .trim();
      return cleaned.isEmpty() ? stripped.toLowerCase(Locale.ROOT) : cleaned.toLowerCase(Locale.ROOT);
   }

   public static boolean isEntityVisibleCached(Minecraft mc, Entity entity) {
      if (entity == null || mc.player == null)
         return false;
      EntityHighlightInfo info = getHighlightInfo(entity);
      if (info == null)
         return false;
      long now = System.currentTimeMillis();
      if (now - info.visibilityTimestamp < 150L) {
         return info.isVisible;
      }
      boolean vis = mc.player.hasLineOfSight(entity);
      info.isVisible = vis;
      info.visibilityTimestamp = now;
      return vis;
   }

   public static boolean isEntityVisible(Minecraft mc, Entity entity) {
      if (mc.player == null || mc.level == null || entity == null) {
         return false;
      }
      return mc.player.hasLineOfSight(entity);
   }

   public static EntityHighlightInfo getHighlightInfo(Entity self) {
      if (self == null)
         return null;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null)
         return null;
      if (!s.highlightsEnabled && !s.tracerTestAllEntities && !s.cheeseTracer
            && !s.dungeonKeyHighlight && !s.dungeonStarredMobHighlight
            && !s.critterHighlightMobs
            && (s.customTracers == null || s.customTracers.isEmpty()) && BedwarsESP.getEntityColor(self) == null) {
         return null;
      }
      int id = self.getId();
      long now = System.currentTimeMillis();
      EntityHighlightInfo cached = HIGHLIGHT_CACHE.get(id);
      if (cached != null && now - cached.timestamp < 1000L) {
         return cached;
      }
      EntityHighlightInfo computed = computeHighlightInfo(self, now);
      if (HIGHLIGHT_CACHE.size() > 3000)
         HIGHLIGHT_CACHE.clear();
      HIGHLIGHT_CACHE.put(id, computed);
      return computed;
   }

   public static boolean isNametagForLivingEntity(Entity self) {
      if (self == null || self.level() == null || !(self instanceof ArmorStand as))
         return false;
      if (!as.isInvisible() && !as.isMarker())
         return false;
      if (!as.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty())
         return false;
      int id = self.getId();
      long now = System.currentTimeMillis();
      Long lastCheck = NAMETAG_CHECK_CACHE.get(id);
      if (lastCheck != null && now - lastCheck < 3000L) {
         return NAMETAG_LIVING_IDS.contains(id);
      }
      if (NAMETAG_CHECK_CACHE.size() > 2000) {
         NAMETAG_CHECK_CACHE.clear();
         NAMETAG_LIVING_IDS.clear();
      }
      NAMETAG_CHECK_CACHE.put(id, now);
      AABB box = self.getBoundingBox().inflate(1.5, 4.0, 1.5);
      for (Entity other : self.level().getEntities(self, box)) {
         if (other != self && other instanceof net.minecraft.world.entity.LivingEntity
               && !(other instanceof ArmorStand)) {
            NAMETAG_LIVING_IDS.add(id);
            return true;
         }
      }
      NAMETAG_LIVING_IDS.remove(id);
      return false;
   }

   public static boolean isEntityEffectivelyVisible(Entity entity) {
      if (entity == null)
         return false;
      if (!entity.isInvisible())
         return true;
      if (entity instanceof ArmorStand as) {
         return !as.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
               || !as.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
               || !as.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()
               || !as.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty();
      }
      return false;
   }

   private static EntityHighlightInfo computeHighlightInfo(Entity self, long now) {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (self == mc.player || ignoredEntities.contains(self.getId())) {
            return new EntityHighlightInfo(now, false, null, false, 0xFFFFFF, false);
         }
         if (isNametagForLivingEntity(self)) {
            return new EntityHighlightInfo(now, false, null, false, 0xFFFFFF, false);
         }
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null) {
            return new EntityHighlightInfo(now, false, null, false, 0xFFFFFF, false);
         }

         if (s.tracerTestAllEntities) {
            return new EntityHighlightInfo(now, true, 16776960, true, 16776960, true);
         }

         Integer bwColor = BedwarsESP.getEntityColor(self);
         if (bwColor != null) {
            return new EntityHighlightInfo(now, true, bwColor, false, 0xFFFFFF, true);
         }

         // Sparkling Mob Detection (nametag contains Sparkling <Mob>)
         if (self.hasCustomName() || !(self instanceof Player || self instanceof ArmorStand)) {
            String nameRaw = self.hasCustomName() ? self.getCustomName().getString() : "";
            TargetPests.EntityInfoCache cache = HighlightESP.getCachedInfo(self);
            String nametag = cache != null ? cache.nametagName : null;
            if (nameRaw.contains("Sparkling") || (nametag != null && nametag.contains("sparkling"))) {
               int sparkColor = BomboRenderUtils.colorNameToHex(s.sparklingTracerColor != null ? s.sparklingTracerColor : "LIGHT_PURPLE");
               return new EntityHighlightInfo(now, true, sparkColor, true, sparkColor, true);
            }
         }

         // Safari Missing Critters Glowing ESP
         if (s.critterHighlightMobs && me.bombo.bomboaddons.features.critters.SafariLocation.inSafari()) {
            me.bombo.bomboaddons.features.critters.SafariBiome playerBiome = me.bombo.bomboaddons.features.critters.SafariLocation.biome();
            if (playerBiome == null || playerBiome.contains(self.getX(), self.getZ())) {
               String critterName = me.bombo.bomboaddons.features.critters.CritterSafariEngine.identifyCritter(self);
               if (critterName != null) {
                  me.bombo.bomboaddons.features.critters.SafariSession session = me.bombo.bomboaddons.features.critters.CritterSessionManager.currentOrLast();
                  boolean uniqueMode = !"All".equalsIgnoreCase(s.critterTrackingMode);
                  me.bombo.bomboaddons.features.critters.Critter cObj = me.bombo.bomboaddons.features.critters.Critters.byName(critterName);
                  boolean completed = cObj != null && session != null && session.isComplete(cObj, uniqueMode);
                  if (!completed || critterName.equalsIgnoreCase("Hideyho")) {
                     int cColor = 0x55FF55;
                     if (cObj != null) cColor = cObj.biome().colour();
                     if (critterName.equalsIgnoreCase("Hideyho")) cColor = 0x00AAFF;
                     return new EntityHighlightInfo(now, true, cColor, false, cColor, true);
                  }
               }
            }
         }

         if (s.dungeonKeyHighlight && matchesIsland("Catacombs") && !me.bombo.bomboaddons.features.dungeons.DungeonBossManager.isBloodDoorOpened()) {
            String headTex = TargetPests.getHeadTextureValue(self);
            String skullHash = headTex != null ? TargetPests.extractTextureHash(headTex) : null;
            if (skullHash != null && (skullHash.equalsIgnoreCase("20de5e8974940375934d32f71c91ad2d5728d38e51647dcc8f39206c099a54c2")
                  || skullHash.equalsIgnoreCase("e49ec7d82b1415acae2059f78cd1d1754b9de9b18ca59f609024c4af843d4d24"))) {
               int keyColor = BomboRenderUtils.colorNameToHex(s.dungeonKeyColor != null ? s.dungeonKeyColor : "GOLD");
               boolean headOnly = (self instanceof ArmorStand);
               return new EntityHighlightInfo(now, true, keyColor, s.dungeonKeyTracers, keyColor, true, headOnly);
            }
         }

         if (s.dungeonStarredMobHighlight && matchesIsland("Catacombs")) {
            boolean isStarred = false;
            if (self.hasCustomName()) {
               String cn = self.getCustomName().getString();
               if (cn.contains("✯") || cn.contains("★") || cn.contains("✪") || cn.contains("☆")) {
                  isStarred = true;
               }
            }
            if (!isStarred && !(self instanceof Player) && !(self instanceof ArmorStand)) {
               TargetPests.EntityInfoCache cache = HighlightESP.getCachedInfo(self);
               String nametag = cache != null ? cache.nametagName : null;
               if (nametag != null && (nametag.contains("✯") || nametag.contains("★") || nametag.contains("✪") || nametag.contains("☆"))) {
                  isStarred = true;
               } else if (self.level() != null) {
                  AABB box = self.getBoundingBox().inflate(0.5, 2.5, 0.5);
                  for (Entity e2 : self.level().getEntities(self, box)) {
                     if (e2 instanceof ArmorStand as && as.hasCustomName()) {
                        String rawName = as.getCustomName().getString();
                        if (rawName.contains("✯") || rawName.contains("★") || rawName.contains("✪") || rawName.contains("☆")) {
                           isStarred = true;
                           break;
                        }
                     }
                  }
               }
            }
            if (isStarred) {
               int starColor = BomboRenderUtils.colorNameToHex(s.dungeonStarredMobColor != null ? s.dungeonStarredMobColor : "GOLD");
               return new EntityHighlightInfo(now, true, starColor, s.dungeonStarredMobTracers, starColor, true);
            }
         }

         if (s.cheeseTracer && self instanceof ItemEntity) {
            ItemStack stack = ((ItemEntity) self).getItem();
            if (!stack.isEmpty() && stack.getComponentsPatch().toString().contains("CHEESE_FUEL")) {
               return new EntityHighlightInfo(now, true, 16766720, true, 16766720, true);
            }
         }

         if (s.customTracers != null && !s.customTracers.isEmpty()) {
            String uuidStr = self.getUUID().toString();
            String entName = self.getName().getString();
            String itemName = null;
            if (self instanceof ItemEntity) {
               ItemStack stack2 = ((ItemEntity) self).getItem();
               if (!stack2.isEmpty()) {
                  itemName = stack2.getHoverName().getString();
               }
            }
            String headTex = TargetPests.getHeadTextureValue(self);
            String skullHash = headTex != null ? TargetPests.extractTextureHash(headTex) : null;
            String matchKey = null;
            if (s.customTracers.containsKey(uuidStr))
               matchKey = uuidStr;
            else if (skullHash != null && s.customTracers.containsKey(skullHash.toLowerCase()))
               matchKey = skullHash.toLowerCase();
            else if (s.customTracers.containsKey(entName))
               matchKey = entName;
            else if (itemName != null && s.customTracers.containsKey(itemName))
               matchKey = itemName;
            else {
               String cleanEntName = ChatFormatting.stripFormatting(entName);
               if (s.customTracers.containsKey(cleanEntName))
                  matchKey = cleanEntName;
               else if (itemName != null) {
                  String cleanItemName = ChatFormatting.stripFormatting(itemName);
                  if (s.customTracers.containsKey(cleanItemName))
                     matchKey = cleanItemName;
               }
               if (matchKey == null) {
                  TargetPests.EntityInfoCache cache = HighlightESP.getCachedInfo(self);
                  String combined = cache != null ? cache.combinedName : "";
                  String nametag = cache != null ? cache.nametagName : null;
                  for (String ctKey : s.customTracers.keySet()) {
                     if (matchesKey(combined, ctKey) || (nametag != null && matchesKey(nametag, ctKey))
                           || matchesKey(entName, ctKey) || (itemName != null && matchesKey(itemName, ctKey))
                           || (skullHash != null && matchesKey(skullHash, ctKey))) {
                        matchKey = ctKey;
                        break;
                     }
                  }
               }
            }
            if (matchKey != null) {
               BomboConfig.CustomTracerInfo ctInfo = s.customTracers.get(matchKey);
               int color = ctInfo != null && ctInfo.color != null ? BomboRenderUtils.colorNameToHex(ctInfo.color)
                     : 0xFFFFFF;
               return new EntityHighlightInfo(now, true, color, true, color, true);
            }
         }

         if (s.highlightsEnabled && s.highlights != null && !s.highlights.isEmpty()) {
            TargetPests.EntityInfoCache cache = HighlightESP.getCachedInfo(self);
            String name = cache != null ? cache.combinedName : "";
            String nametagName = cache != null ? cache.nametagName : null;
            boolean isPlayer = self instanceof Player;
            String skullHash = null;
            String directSkullHash = null;
            boolean checkedSkullHash = false;
            boolean checkedDirectSkullHash = false;

            for (Map.Entry<String, BomboConfig.HighlightInfo> entry : s.highlights.entrySet()) {
               String key = entry.getKey();
               if (key == null || key.isEmpty())
                  continue;
               BomboConfig.HighlightInfo info = entry.getValue();
               if (info == null || !info.enabled)
                  continue;
               if (!matchesIsland(info.requiredIsland))
                  continue;
               if (info.requiredSubarea != null && !info.requiredSubarea.isEmpty()
                     && !matchesSubarea(info.requiredSubarea))
                  continue;

               // VISIBILITY FILTER:
               boolean effVis = isEntityEffectivelyVisible(self);
               if (info.visibility != null && !info.visibility.trim().isEmpty()) {
                  String v = info.visibility.trim().toUpperCase(Locale.ROOT);
                  if (v.equals("VISIBLE_ONLY") || v.equals("VISIBLE")) {
                     if (!effVis)
                        continue;
                  } else if (v.equals("INVISIBLE_ONLY") || v.equals("INVISIBLE")) {
                     if (effVis)
                        continue;
                  }
               } else {
                  if (!effVis && !info.showInvisible)
                     continue;
               }

               // STRICT ATTRIBUTE FILTERS:
               if (info.itemDisplayId != null && !info.itemDisplayId.trim().isEmpty()
                     && !matchesItemDisplay(self, info.itemDisplayId))
                  continue;
               if (info.mobSize != null && !info.mobSize.trim().isEmpty() && !matchesMobSize(self, info.mobSize))
                  continue;
               if (info.armorPiece != null && !info.armorPiece.trim().isEmpty()
                     && !matchesArmorPiece(self, info.armorPiece))
                  continue;
               if (info.armorType != null && !info.armorType.trim().isEmpty() && !matchesArmor(self, info.armorType))
                  continue;
               if (info.ridingType != null && !info.ridingType.trim().isEmpty()
                     && !matchesRiding(self, info.ridingType))
                  continue;
               if (info.heldItem != null && !info.heldItem.trim().isEmpty() && !matchesHeldItem(self, info.heldItem))
                  continue;

               boolean matched = false;
               if (info.itemDisplayId != null && !info.itemDisplayId.trim().isEmpty()) {
                  matched = true;
               }
               boolean isDirectHeadRule = (info.headHashes != null && !info.headHashes.isEmpty())
                     || (key.length() == 64 && key.matches("^[0-9a-fA-F]{64}$"));

               if (isDirectHeadRule) {
                  if (!checkedDirectSkullHash) {
                     checkedDirectSkullHash = true;
                     String directHeadTex = TargetPests.getDirectHeadTextureValue(self);
                     directSkullHash = directHeadTex != null ? TargetPests.extractTextureHash(directHeadTex) : null;
                  }
               } else {
                  if (!checkedSkullHash) {
                     checkedSkullHash = true;
                     String headTex = TargetPests.getHeadTextureValue(self);
                     skullHash = headTex != null ? TargetPests.extractTextureHash(headTex) : null;
                  }
               }
               String hashToUse = isDirectHeadRule ? directSkullHash : skullHash;

               // 1. Skull hash match
               if (hashToUse != null) {
                  if (key.equalsIgnoreCase(hashToUse) || matchesKey(hashToUse, key)) {
                     matched = true;
                  }
                  if (!matched && info.headHashes != null && !info.headHashes.isEmpty()) {
                     for (String h : info.headHashes) {
                        if (h != null && h.equalsIgnoreCase(hashToUse)) {
                           matched = true;
                           break;
                        }
                     }
                  }
                  if (!matched) {
                     me.bombo.bomboaddons.features.BestiaryDataFetcher.BestiaryMobRule bestiaryRule = me.bombo.bomboaddons.features.BestiaryDataFetcher
                           .getRule(key, info.requiredIsland);
                     if (bestiaryRule != null && bestiaryRule.hasHead(hashToUse)) {
                        matched = true;
                     }
                  }
                  if (!matched) {
                     me.bombo.bomboaddons.features.BestiaryDataFetcher.BestiaryMobRule headRule = me.bombo.bomboaddons.features.BestiaryDataFetcher
                           .getRuleByHead(hashToUse);
                     if (headRule != null && (matchesKey(headRule.name, key) || key.equalsIgnoreCase(headRule.name)
                           || (headRule.name != null
                                 && headRule.name.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT))))) {
                        matched = true;
                     }
                  }
               }

               // 2. Player name match
               if (!matched && info.playerName != null && !info.playerName.isEmpty()) {
                  if (isPlayer && self.getName().getString().toLowerCase(Locale.ROOT)
                        .contains(info.playerName.toLowerCase(Locale.ROOT))) {
                     matched = true;
                  }
               }

               // 3. Entity Type match (with duplicate mob & nametag check)
               boolean hasHeadRequirement = (info.headHashes != null && !info.headHashes.isEmpty());
               if (!matched && info.entityType != null && !info.entityType.isEmpty() && !hasHeadRequirement) {
                  if (matchesEntityType(self, info.entityType)) {
                     String cleanKey = key.toLowerCase(Locale.ROOT).trim();
                     boolean wolfActive = s.highlights.containsKey("wolf") && s.highlights.get("wolf").enabled;
                     boolean oldWolfActive = s.highlights.containsKey("old wolf")
                           && s.highlights.get("old wolf").enabled;
                     boolean bothWolvesActive = wolfActive && oldWolfActive;
                     boolean isOldWolfMob = (nametagName != null
                           && nametagName.toLowerCase(Locale.ROOT).contains("old wolf"))
                           || name.toLowerCase(Locale.ROOT).contains("old wolf");

                     if (cleanKey.equals("old wolf")) {
                        if (isOldWolfMob) {
                           matched = true;
                        } else if (bothWolvesActive) {
                           matched = true;
                        }
                     } else if (cleanKey.equals("wolf")) {
                        if (bothWolvesActive) {
                           matched = true;
                        } else {
                           if (!isOldWolfMob && (nametagName == null
                                 || !nametagName.toLowerCase(Locale.ROOT).contains("old wolf"))) {
                              matched = true;
                           }
                        }
                     } else {
                        // Check if this entity has multiple bestiary mob duplicates sharing the same entity type & size on this island
                        List<me.bombo.bomboaddons.features.BestiaryDataFetcher.BestiaryMobRule> islandRules = me.bombo.bomboaddons.features.BestiaryDataFetcher
                              .getRulesForIsland(info.requiredIsland);
                        List<String> duplicateMobNames = new ArrayList<>();
                        for (me.bombo.bomboaddons.features.BestiaryDataFetcher.BestiaryMobRule r : islandRules) {
                           if (r != null && r.entityType != null && !r.entityType.isEmpty() && (r.heads == null || r.heads.isEmpty())) {
                              if (r.entityType.equalsIgnoreCase(info.entityType)) {
                                 String rSize = r.mobSize != null && !r.mobSize.isEmpty() ? r.mobSize : r.size;
                                 String infoSize = info.mobSize != null ? info.mobSize : "";
                                 if ((rSize == null && infoSize.isEmpty()) || (rSize != null && rSize.equalsIgnoreCase(infoSize))) {
                                    duplicateMobNames.add(r.name.toLowerCase(Locale.ROOT).trim());
                                 }
                              }
                           }
                        }

                        if (duplicateMobNames.size() > 1 && duplicateMobNames.contains(cleanKey)) {
                           // Check if all duplicates are enabled in highlights
                           boolean allDuplicatesEnabled = true;
                           for (String dup : duplicateMobNames) {
                              if (!s.highlights.containsKey(dup) || !s.highlights.get(dup).enabled) {
                                 allDuplicatesEnabled = false;
                                 break;
                              }
                           }

                           if (allDuplicatesEnabled) {
                              // All duplicate entities enabled -> highlight entity directly
                              matched = true;
                           } else {
                              // Not all enabled (e.g. one completed or disabled) -> require nametag match
                              if ((nametagName != null && nametagName.contains(cleanKey))
                                    || name.toLowerCase(Locale.ROOT).contains(cleanKey)) {
                                 matched = true;
                              }
                           }
                        } else if (cleanKey.contains(" ")) {
                           if ((nametagName != null && nametagName.contains(cleanKey))
                                 || name.toLowerCase(Locale.ROOT).contains(cleanKey)) {
                              matched = true;
                           }
                        } else {
                           matched = true;
                        }
                     }
                  }
               }

               // 4. Bestiary Rule fallback
               if (!matched && info.isBestiary) {
                  me.bombo.bomboaddons.features.BestiaryDataFetcher.BestiaryMobRule rule = me.bombo.bomboaddons.features.BestiaryDataFetcher
                        .getRule(key, info.requiredIsland);
                  if (rule != null) {
                     if (rule.heads != null && !rule.heads.isEmpty() && hashToUse != null && rule.hasHead(hashToUse)) {
                        matched = true;
                     }
                     if (!matched && rule.playerName != null && !rule.playerName.isEmpty() && isPlayer && self.getName()
                           .getString().toLowerCase(Locale.ROOT).contains(rule.playerName.toLowerCase(Locale.ROOT))) {
                        matched = true;
                     }
                     if (!matched && rule.entityType != null && !rule.entityType.isEmpty()
                           && (rule.heads == null || rule.heads.isEmpty())) {
                        if (matchesEntityType(self, rule.entityType) && matchesArmor(self, rule.armor)
                              && matchesRiding(self, rule.riding) && matchesHeldItem(self, rule.heldItem)
                              && matchesSubarea(rule.subarea, self) && matchesMobSize(self,
                                    rule.mobSize != null && !rule.mobSize.isEmpty() ? rule.mobSize : rule.size)) {
                           String cleanKey = key.toLowerCase(Locale.ROOT).trim();
                           if (cleanKey.equals("old wolf")) {
                              if ((nametagName != null && nametagName.contains("old wolf"))
                                    || name.toLowerCase(Locale.ROOT).contains("old wolf")) {
                                 matched = true;
                              }
                           } else if (cleanKey.equals("wolf")) {
                              if (nametagName == null || !nametagName.contains("old wolf")) {
                                 matched = true;
                              }
                           } else {
                              List<me.bombo.bomboaddons.features.BestiaryDataFetcher.BestiaryMobRule> islandRules = me.bombo.bomboaddons.features.BestiaryDataFetcher
                                    .getRulesForIsland(rule.island);
                              List<String> duplicateMobNames = new ArrayList<>();
                              for (me.bombo.bomboaddons.features.BestiaryDataFetcher.BestiaryMobRule r : islandRules) {
                                 if (r != null && r.entityType != null && !r.entityType.isEmpty() && (r.heads == null || r.heads.isEmpty())) {
                                    if (r.entityType.equalsIgnoreCase(rule.entityType)) {
                                       String rSize = r.mobSize != null && !r.mobSize.isEmpty() ? r.mobSize : r.size;
                                       String ruleSize = rule.mobSize != null && !rule.mobSize.isEmpty() ? rule.mobSize : rule.size;
                                       if ((rSize == null && ruleSize == null) || (rSize != null && rSize.equalsIgnoreCase(ruleSize))) {
                                          duplicateMobNames.add(r.name.toLowerCase(Locale.ROOT).trim());
                                       }
                                    }
                                 }
                              }

                              if (duplicateMobNames.size() > 1 && duplicateMobNames.contains(cleanKey)) {
                                 boolean allDuplicatesEnabled = true;
                                 for (String dup : duplicateMobNames) {
                                    if (!s.highlights.containsKey(dup) || !s.highlights.get(dup).enabled) {
                                       allDuplicatesEnabled = false;
                                       break;
                                    }
                                 }

                                 if (allDuplicatesEnabled) {
                                    matched = true;
                                 } else {
                                    if ((nametagName != null && nametagName.contains(cleanKey))
                                          || name.toLowerCase(Locale.ROOT).contains(cleanKey)) {
                                       matched = true;
                                    }
                                 }
                              } else {
                                 matched = true;
                              }
                           }
                        }
                     }
                  }
               }

               // 5. Name / Nametag / Raw key match
               if (!matched) {
                  String hashForRaw = isDirectHeadRule ? directSkullHash : skullHash;
                  if (hashForRaw != null && (key.equalsIgnoreCase(hashForRaw) || matchesKey(hashForRaw, key))) {
                     matched = true;
                  } else {
                     boolean hasSpecificHead = (info.headHashes != null && !info.headHashes.isEmpty());
                     if (!hasSpecificHead) {
                        boolean keyIsPlayer = key.equalsIgnoreCase("player") || key.equalsIgnoreCase("players");
                        String cleanKey = key.toLowerCase(Locale.ROOT).trim();
                        if (cleanKey.equals("wolf") && nametagName != null && nametagName.contains("old wolf")) {
                           // Skip old wolves when matching regular wolf
                        } else if ((isPlayer && keyIsPlayer) || (!isDirectHeadRule && matchesKey(name, key))
                              || (!isDirectHeadRule && nametagName != null && matchesKey(nametagName, key))
                              || EntityVariantHelper.matchesVariant(self, cleanKey)) {
                           matched = true;
                        }
                     }
                  }
               }

               if (matched) {
                  if (info.showTitleOnSpawn || info.playSoundOnSpawn) {
                     int entId = self.getId();
                     if (!ALERTED_SPAWN_IDS.contains(entId)) {
                        ALERTED_SPAWN_IDS.add(entId);
                        if (ALERTED_SPAWN_IDS.size() > 5000)
                           ALERTED_SPAWN_IDS.clear();
                        String titleName = nametagName != null && !nametagName.isEmpty() ? nametagName
                              : (name != null ? name : key);
                        if (titleName.contains(" | ")) {
                           titleName = titleName.substring(0, titleName.indexOf(" | ")).trim();
                        }
                        final String finalTitle = titleName;
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> {
                           if (info.showTitleOnSpawn && client.gui != null) {
                              client.gui.hud.setTitle(
                                    net.minecraft.network.chat.Component.literal("§e" + finalTitle + " §aSpawned!"));
                           }
                           if (info.playSoundOnSpawn && client.player != null) {
                              client.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F,
                                    1.5F);
                           }
                        });
                     }
                  }
                  int color = BomboRenderUtils.colorNameToHex(info.color);
                  return new EntityHighlightInfo(now, true, color, info.tracer, color, info.showInvisible);
               }
            }
         }
      } catch (Throwable ignored) {
      }
      return new EntityHighlightInfo(now, false, null, false, 0xFFFFFF, false);
   }

   public static boolean matchesSubarea(String requiredSubarea) {
      return matchesSubarea(requiredSubarea, null);
   }

   public static boolean matchesSubarea(String requiredSubarea, Entity entity) {
      if (requiredSubarea == null || requiredSubarea.isEmpty())
         return true;
      if (requiredSubarea.equalsIgnoreCase("jungle") && entity != null) {
         double x = entity.getX();
         double z = entity.getZ();
         if (x >= 201.0 && x <= 512.0 && z >= 201.0 && z <= 512.0)
            return true;
      }
      String subarea = SkyblockUtils.getSubArea();
      return subarea != null && subarea.toLowerCase(Locale.ROOT).contains(requiredSubarea.toLowerCase(Locale.ROOT));
   }

   public static boolean matchesEntityType(Entity entity, String targetType) {
      if (entity == null || targetType == null || targetType.isEmpty())
         return false;
      String cleanTarget = targetType.toLowerCase(Locale.ROOT).replace(" ", "_");
      if (entity instanceof ArmorStand && !cleanTarget.contains("armor") && !cleanTarget.contains("stand")) {
         return false;
      }
      String typeStr = entity.getType().toString().toLowerCase(Locale.ROOT);

      // Check for variant tag like tropical_fish:blue or tropical_fish:green
      if (cleanTarget.contains(":")) {
         String[] parts = cleanTarget.split(":", 2);
         String baseType = parts[0].trim();
         String variantReq = parts[1].trim();
         if ((typeStr.contains(baseType) || (baseType.equals("display") && typeStr.contains("display")))
               && EntityVariantHelper.matchesVariant(entity, variantReq)) {
            return true;
         }
      }

      if (typeStr.contains(cleanTarget))
         return true;
      if (EntityVariantHelper.matchesVariant(entity, cleanTarget))
         return true;
      if (cleanTarget.equals("mooshroom") && typeStr.contains("cow"))
         return true;
      if (cleanTarget.equals("magma_cube") && (typeStr.contains("magma") || typeStr.contains("slime")))
         return true;
      if (cleanTarget.equals("zombified_piglin")
            && (typeStr.contains("zombified_piglin") || typeStr.contains("piglin") || typeStr.contains("pig_zombie")))
         return true;
      return false;
   }

   public static boolean matchesArmor(Entity entity, String armorType) {
      if (armorType == null || armorType.isEmpty())
         return true;
      if (!(entity instanceof net.minecraft.world.entity.LivingEntity))
         return false;
      net.minecraft.world.entity.LivingEntity living = (net.minecraft.world.entity.LivingEntity) entity;
      ItemStack head = living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
      ItemStack chest = living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
      ItemStack legs = living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
      ItemStack feet = living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);

      switch (armorType.toLowerCase(Locale.ROOT)) {
         case "chainmail_no_helmet":
            return head.isEmpty() && chest.is(net.minecraft.world.item.Items.CHAINMAIL_CHESTPLATE)
                  && legs.is(net.minecraft.world.item.Items.CHAINMAIL_LEGGINGS)
                  && feet.is(net.minecraft.world.item.Items.CHAINMAIL_BOOTS);
         case "gold_no_helmet":
            return head.isEmpty() && chest.is(net.minecraft.world.item.Items.GOLDEN_CHESTPLATE)
                  && legs.is(net.minecraft.world.item.Items.GOLDEN_LEGGINGS)
                  && feet.is(net.minecraft.world.item.Items.GOLDEN_BOOTS);
         case "no_armor":
            return head.isEmpty() && chest.isEmpty() && legs.isEmpty() && feet.isEmpty();
         case "diamond":
            return chest.is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)
                  || legs.is(net.minecraft.world.item.Items.DIAMOND_LEGGINGS)
                  || feet.is(net.minecraft.world.item.Items.DIAMOND_BOOTS)
                  || head.is(net.minecraft.world.item.Items.DIAMOND_HELMET);
         case "gold":
            return chest.is(net.minecraft.world.item.Items.GOLDEN_CHESTPLATE)
                  || legs.is(net.minecraft.world.item.Items.GOLDEN_LEGGINGS)
                  || feet.is(net.minecraft.world.item.Items.GOLDEN_BOOTS)
                  || head.is(net.minecraft.world.item.Items.GOLDEN_HELMET);
         case "leather_blue":
            return chest.is(net.minecraft.world.item.Items.LEATHER_CHESTPLATE)
                  || legs.is(net.minecraft.world.item.Items.LEATHER_LEGGINGS)
                  || feet.is(net.minecraft.world.item.Items.LEATHER_BOOTS);
         default:
            return true;
      }
   }

   public static boolean matchesRiding(Entity entity, String ridingType) {
      if (ridingType == null || ridingType.isEmpty())
         return true;
      Entity vehicle = entity.getVehicle();
      if (vehicle == null)
         return false;
      String vType = vehicle.getType().toString().toLowerCase(Locale.ROOT);
      return vType.contains(ridingType.toLowerCase(Locale.ROOT));
   }

   public static final java.util.Map<Integer, Double> MOB_SCALE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

   public static boolean matchesMobSize(Entity entity, String reqSize) {
      if (reqSize == null || reqSize.trim().isEmpty())
         return true;
      String req = reqSize.trim().toLowerCase(Locale.ROOT);
      
      int id = entity.getId();
      Double cachedScale = MOB_SCALE_CACHE.get(id);
      double scale;
      if (cachedScale != null) {
         scale = cachedScale;
      } else {
         double w = entity.getBbWidth();
         double h = entity.getBbHeight();
         scale = Math.max(w / 0.6, h / 1.8);
         if (entity instanceof net.minecraft.world.entity.monster.cubemob.Slime) {
            int sz = ((net.minecraft.world.entity.monster.cubemob.Slime) entity).getSize();
            scale = (double) sz;
         }
         if (MOB_SCALE_CACHE.size() > 2000) MOB_SCALE_CACHE.clear();
         MOB_SCALE_CACHE.put(id, scale);
      }

      if (entity instanceof net.minecraft.world.entity.monster.cubemob.Slime) {
         int sz = (int) scale;
         if (req.equals("big"))
            return sz >= 4;
         if (req.equals("small"))
            return sz == 1;
         if (req.equals("medium"))
            return sz > 1 && sz < 4;
      }
      if (req.equals("big"))
         return scale >= 2.0;
      if (req.equals("small"))
         return scale <= 0.6;
      if (req.equals("normal"))
         return scale > 0.6 && scale < 2.0;

      try {
         if (req.endsWith("+")) {
            double min = Double.parseDouble(req.substring(0, req.length() - 1).trim());
            return scale >= (min - 0.1);
         } else if (req.startsWith(">=")) {
            double min = Double.parseDouble(req.substring(2).trim());
            return scale >= (min - 0.1);
         } else if (req.startsWith(">")) {
            double min = Double.parseDouble(req.substring(1).trim());
            return scale > (min + 0.1);
         } else if (req.endsWith("-")) {
            double max = Double.parseDouble(req.substring(0, req.length() - 1).trim());
            return scale <= (max + 0.1);
         } else if (req.startsWith("<=")) {
            double max = Double.parseDouble(req.substring(2).trim());
            return scale <= (max + 0.1);
         } else if (req.startsWith("<")) {
            double max = Double.parseDouble(req.substring(1).trim());
            return scale < (max - 0.1);
         }
         double target = Double.parseDouble(req.replace("x", "").trim());
         return Math.abs(scale - target) <= 0.6;
      } catch (Throwable ignored) {
      }
      return true;
   }

   public static boolean matchesArmorPiece(Entity entity, String armorPiece) {
      if (armorPiece == null || armorPiece.trim().isEmpty())
         return true;
      if (!(entity instanceof net.minecraft.world.entity.LivingEntity))
         return false;
      net.minecraft.world.entity.LivingEntity living = (net.minecraft.world.entity.LivingEntity) entity;
      String p = armorPiece.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
      for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
         if (slot.isArmor()) {
            ItemStack item = living.getItemBySlot(slot);
            if (!item.isEmpty()) {
               String id = item.getItem().toString().toLowerCase(Locale.ROOT);
               String name = item.getHoverName().getString().toLowerCase(Locale.ROOT);
               if (id.contains(p) || name.contains(p))
                  return true;
            }
         }
      }
      return false;
   }

   public static boolean matchesHeldItem(Entity entity, String heldItem) {
      if (heldItem == null || heldItem.isEmpty())
         return true;
      if (!(entity instanceof net.minecraft.world.entity.LivingEntity))
         return false;
      ItemStack main = ((net.minecraft.world.entity.LivingEntity) entity).getMainHandItem();
      return !main.isEmpty()
            && main.getItem().toString().toLowerCase(Locale.ROOT).contains(heldItem.toLowerCase(Locale.ROOT));
   }

   public static boolean matchesItemDisplay(Entity entity, String targetId) {
      if (entity == null || targetId == null || targetId.trim().isEmpty())
         return true;
      String simpleName = entity.getClass().getSimpleName();
      if (!simpleName.equalsIgnoreCase("ItemDisplay") && !simpleName.toLowerCase(Locale.ROOT).contains("itemdisplay")) {
         return false;
      }
      EntityVariantHelper.VariantResult vr = EntityVariantHelper.inspect(entity);
      if (vr != null) {
         String target = targetId.toLowerCase(Locale.ROOT).trim();
         if (target.equals("*") || target.equals("any") || target.equals("item_display") || target.equals("display"))
            return true;
         if (vr.matchKeyword != null && vr.matchKeyword.contains(target))
            return true;
         if (vr.lineText != null && vr.lineText.toLowerCase(Locale.ROOT).contains(target))
            return true;
      }
      return false;
   }

   public static boolean isEntityHighlighted(Entity self) {
      EntityHighlightInfo info = getHighlightInfo(self);
      return info != null && info.highlighted;
   }

   public static boolean shouldEntityGlow(Entity self) {
      EntityHighlightInfo info = getHighlightInfo(self);
      return info != null && info.highlighted;
   }

   public static boolean shouldHideBody(Entity self) {
      EntityHighlightInfo info = getHighlightInfo(self);
      return info != null && info.highlightHeadOnly;
   }

   public static Integer getEntityGlowColor(Entity self) {
      EntityHighlightInfo info = getHighlightInfo(self);
      return info != null ? info.glowColor : null;
   }

   public static class HighlightTracer {
      public Vector2f start;
      public Vector2f end;
      public int color;
      public float thickness;
   }
}
