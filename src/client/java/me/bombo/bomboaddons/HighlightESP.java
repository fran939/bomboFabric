package me.bombo.bomboaddons;

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
   private static final java.util.concurrent.atomic.AtomicBoolean isFetchingAliases = new java.util.concurrent.atomic.AtomicBoolean(false);
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
      if (now - lastAliasFetchTime < 300000L) return;
      if (!isFetchingAliases.compareAndSet(false, true)) return;
      lastAliasFetchTime = now;
      Thread t = new Thread(() -> {
         try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            String[] endpoints = new String[]{
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
                              String alias = entry.getValue().getAsString().trim().replaceAll("^\"+|\"+$", "").toLowerCase();
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
                           if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue;
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
                                 String k = trimmed.substring(0, firstSpace).trim().replaceAll("^\"+|\"+$", "").toLowerCase();
                                 String v = trimmed.substring(firstSpace + 1).trim().replaceAll("^\"+|\"+$", "").toLowerCase();
                                 if (!k.isEmpty() && !v.isEmpty()) {
                                    ALIAS_MAP.put(k, v);
                                    ALIAS_MAP.put(v, k);
                                    foundAny = true;
                                 }
                              }
                           }
                        }
                        if (foundAny) break;
                     }
                  }
               } catch (Throwable ignored) {}
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
      public boolean isVisible;
      public long visibilityTimestamp;

      public EntityHighlightInfo(long timestamp, boolean highlighted, Integer glowColor, boolean isTracer, int tracerColor, boolean showInvisible) {
         this.timestamp = timestamp;
         this.highlighted = highlighted;
         this.glowColor = glowColor;
         this.isTracer = isTracer;
         this.tracerColor = tracerColor;
         this.showInvisible = showInvisible;
         this.isVisible = false;
         this.visibilityTimestamp = 0L;
      }
   }

   public static final Map<Integer, EntityHighlightInfo> HIGHLIGHT_CACHE = new ConcurrentHashMap<>();

   public static void clearHighlightCache() {
      HIGHLIGHT_CACHE.clear();
      TargetPests.infoCache.clear();
   }

   public static boolean matchesIsland(String requiredIsland) {
      if (requiredIsland == null || requiredIsland.trim().isEmpty() || "All".equalsIgnoreCase(requiredIsland.trim()) || "*".equals(requiredIsland.trim())) {
         return true;
      }
      String current = BomboaddonsClient.currentArea;
      if (current == null || current.isEmpty()) return false;
      String req = requiredIsland.trim();
      if (req.startsWith("!")) {
         String negated = req.substring(1).trim();
         return !current.toLowerCase(java.util.Locale.ROOT).contains(negated.toLowerCase(java.util.Locale.ROOT));
      }
      return current.toLowerCase(java.util.Locale.ROOT).contains(req.toLowerCase(java.util.Locale.ROOT));
   }

   public static boolean hasActiveTracers(BomboConfig.Settings s) {
      if (s == null) return false;
      if (s.tracerTestAllEntities || s.cheeseTracer) return true;
      if (s.customTracers != null && !s.customTracers.isEmpty()) return true;
      if (s.highlightsEnabled && s.highlights != null) {
         for (BomboConfig.HighlightInfo hi : s.highlights.values()) {
            if (hi != null && hi.enabled && hi.tracer && matchesIsland(hi.requiredIsland)) return true;
         }
      }
      return false;
   }

   public static final Set<Integer> activeTracerEntityIds = ConcurrentHashMap.newKeySet();

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         activeTracerEntityIds.clear();
         return;
      }
      if (mc.player.tickCount % 10 != 0) {
         return;
      }
      BomboConfig.Settings s = BomboConfig.get();
      if (!hasActiveTracers(s)) {
         activeTracerEntityIds.clear();
         return;
      }
      Set<Integer> newTracers = new HashSet<>();
      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity == mc.player || ignoredEntities.contains(entity.getId())) continue;
         EntityHighlightInfo info = getHighlightInfo(entity);
         if (info != null && info.isTracer) {
            newTracers.add(entity.getId());
         }
      }
      activeTracerEntityIds.clear();
      activeTracerEntityIds.addAll(newTracers);
   }

   public static void render(LevelRenderContext context) {
      try {
         TRACERS.clear();
         BomboConfig.Settings s = BomboConfig.get();
         if (!hasActiveTracers(s)) {
            return;
         }
         if (!s.tracerTestAllEntities && activeTracerEntityIds.isEmpty()) {
            return;
         }
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null || mc.player == null) {
            return;
         }
         if (context.poseStack() != null && !context.poseStack().isEmpty()) {
            lastViewMatrix.set(context.poseStack().last().pose());
         }
         try {
            if (context.levelState() != null && context.levelState().cameraRenderState != null && context.levelState().cameraRenderState.projectionMatrix != null) {
               lastProjMatrix.set(context.levelState().cameraRenderState.projectionMatrix);
            }
         } catch (Throwable ignored) {}
         lastEntityCount = 0;
         lastTracerChecks = 0;
         lastTracersAdded = 0;
         Vec3 camPos = mc.gameRenderer.getMainCamera().position();
         PoseStack poseStack = context.poseStack();
         OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());
         Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
         float startX = look.x();
         float startY = look.y();
         float startZ = look.z();

         if (s.tracerTestAllEntities) {
            for (Entity entity : mc.level.entitiesForRendering()) {
               if (entity == mc.player || ignoredEntities.contains(entity.getId())) continue;
               float endX = (float) (entity.getX() - camPos.x);
               float endY = (float) (entity.getY() - camPos.y + (double) (entity.getBbHeight() / 2.0F));
               float endZ = (float) (entity.getZ() - camPos.z);
               collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, endX, endY, endZ, 1.0F, 1.0F, 0.0F, 1.0F, 2.0F));
            }
            return;
         }

         for (Integer id : activeTracerEntityIds) {
            Entity entity = mc.level.getEntity(id);
            if (entity == null || entity == mc.player || ignoredEntities.contains(id)) continue;
            ++lastEntityCount;
            EntityHighlightInfo info = getHighlightInfo(entity);
            if (info == null || !info.isTracer) continue;
            if (s.hideCheats && !info.showInvisible && !HighlightESP.isEntityVisibleCached(mc, entity)) {
               continue;
            }

            float endX = (float) (entity.getX() - camPos.x);
            float endY = (float) (entity.getY() - camPos.y + (double) (entity.getBbHeight() / 2.0F));
            float endZ = (float) (entity.getZ() - camPos.z);
            int colorInt = info.tracerColor;
            float r = (float) (colorInt >> 16 & 0xFF) / 255.0F;
            float g = (float) (colorInt >> 8 & 0xFF) / 255.0F;
            float b = (float) (colorInt & 0xFF) / 255.0F;
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, endX, endY, endZ, r, g, b, 1.0F, 2.0F));
            HighlightTracer tracer = new HighlightTracer();
            tracer.start = new Vector2f(startX, startY);
            tracer.end = new Vector2f(endX, endY);
            tracer.color = colorInt;
            tracer.thickness = 2.0F;
            TRACERS.add(tracer);
            ++lastTracersAdded;
         }
      } catch (Throwable ignored) {
      }
   }

   public static boolean matchesKey(String text, String key) {
      if (text == null || text.isEmpty() || key == null || key.isEmpty()) return false;
      if (text.contains(key)) return true;
      String lKey = key.toLowerCase();
      String lText = text.toLowerCase();
      if (lText.contains(lKey)) return true;
      String mappedHash = ALIAS_MAP.get(lKey);
      if (mappedHash != null && lText.contains(mappedHash)) return true;
      return false;
   }

   public static TargetPests.EntityInfoCache getCachedInfo(Entity self) {
      if (TargetPests.infoCache.size() > 2000) {
         TargetPests.infoCache.clear();
      }
      int id = self.getId();
      long now = System.currentTimeMillis();
      TargetPests.EntityInfoCache cached = TargetPests.infoCache.get(id);
      if (cached != null && now - cached.lastCheckMs < 1000L) {
         return cached;
      }
      String name = ChatFormatting.stripFormatting(self.getDisplayName().getString());
      StringBuilder combinedName = new StringBuilder(name != null ? name.toLowerCase() : "");

      String selfTex = TargetPests.getHeadTextureValue(self);
      if (selfTex != null) {
         String hash = TargetPests.extractTextureHash(selfTex);
         if (hash != null) {
            if (combinedName.length() > 0) combinedName.append(" | ");
            combinedName.append(hash.toLowerCase());
         }
      }

      if (self instanceof ArmorStand) {
         String pestName = TargetPests.getPestName((ArmorStand) self);
         if (pestName != null) {
            if (combinedName.length() > 0) combinedName.append(" | ");
            combinedName.append(pestName);
         }
      }
      for (Entity passenger : self.getPassengers()) {
         String pName = ChatFormatting.stripFormatting(passenger.getDisplayName().getString());
         if (pName != null) {
            if (combinedName.length() > 0) combinedName.append(" | ");
            combinedName.append(pName.toLowerCase());
         }
         String pTex = TargetPests.getHeadTextureValue(passenger);
         if (pTex != null) {
            String pHash = TargetPests.extractTextureHash(pTex);
            if (pHash != null) {
               if (combinedName.length() > 0) combinedName.append(" | ");
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

   public static String getNearbyNametagName(Entity target) {
      if (target == null || target.level() == null) return null;
      AABB box = target.getBoundingBox().inflate(0.5, 2.5, 0.5);
      for (Entity e2 : target.level().getEntities(target, box)) {
         if (!(e2 instanceof ArmorStand) || !e2.hasCustomName()) continue;
         String name = ChatFormatting.stripFormatting(e2.getCustomName().getString());
         if (name != null && !name.isEmpty()) {
            return name.toLowerCase();
         }
      }
      return null;
   }

   public static boolean isEntityVisibleCached(Minecraft mc, Entity entity) {
      if (entity == null || mc.player == null) return false;
      EntityHighlightInfo info = getHighlightInfo(entity);
      if (info == null) return false;
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
      if (self == null) return null;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) return null;
      if (!s.highlightsEnabled && !s.tracerTestAllEntities && !s.cheeseTracer && (s.customTracers == null || s.customTracers.isEmpty()) && BedwarsESP.getEntityColor(self) == null) {
         return null;
      }
      int id = self.getId();
      long now = System.currentTimeMillis();
      EntityHighlightInfo cached = HIGHLIGHT_CACHE.get(id);
      if (cached != null && now - cached.timestamp < 1000L) {
         return cached;
      }
      EntityHighlightInfo computed = computeHighlightInfo(self, now);
      if (HIGHLIGHT_CACHE.size() > 3000) HIGHLIGHT_CACHE.clear();
      HIGHLIGHT_CACHE.put(id, computed);
      return computed;
   }

   private static EntityHighlightInfo computeHighlightInfo(Entity self, long now) {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (self == mc.player || ignoredEntities.contains(self.getId())) {
            return new EntityHighlightInfo(now, false, null, false, 0xFFFFFF, false);
         }
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null) {
            return new EntityHighlightInfo(now, false, null, false, 0xFFFFFF, false);
         }

         Integer bedwarsColor = BedwarsESP.getEntityColor(self);
         if (bedwarsColor != null) {
            return new EntityHighlightInfo(now, true, bedwarsColor, false, bedwarsColor, true);
         }

         if (s.tracerTestAllEntities) {
            return new EntityHighlightInfo(now, true, 0xFFFF00, true, 0xFFFF00, true);
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
            if (s.customTracers.containsKey(uuidStr)) matchKey = uuidStr;
            else if (skullHash != null && s.customTracers.containsKey(skullHash.toLowerCase())) matchKey = skullHash.toLowerCase();
            else if (s.customTracers.containsKey(entName)) matchKey = entName;
            else if (itemName != null && s.customTracers.containsKey(itemName)) matchKey = itemName;
            else {
               String cleanEntName = ChatFormatting.stripFormatting(entName);
               if (s.customTracers.containsKey(cleanEntName)) matchKey = cleanEntName;
               else if (itemName != null) {
                  String cleanItemName = ChatFormatting.stripFormatting(itemName);
                  if (s.customTracers.containsKey(cleanItemName)) matchKey = cleanItemName;
               }
               if (matchKey == null) {
                  TargetPests.EntityInfoCache cache = HighlightESP.getCachedInfo(self);
                  String combined = cache != null ? cache.combinedName : "";
                  String nametag = cache != null ? cache.nametagName : null;
                  for (String ctKey : s.customTracers.keySet()) {
                     if (matchesKey(combined, ctKey) || (nametag != null && matchesKey(nametag, ctKey)) || matchesKey(entName, ctKey) || (itemName != null && matchesKey(itemName, ctKey)) || (skullHash != null && matchesKey(skullHash, ctKey))) {
                        matchKey = ctKey;
                        break;
                     }
                  }
               }
            }
            if (matchKey != null) {
               BomboConfig.Settings.CustomTracerInfo ctInfo = s.customTracers.get(matchKey);
               int color = ctInfo != null && ctInfo.color != null ? BomboRenderUtils.colorNameToHex(ctInfo.color) : 0xFFFFFF;
               return new EntityHighlightInfo(now, true, color, true, color, true);
            }
         }

         if (s.highlightsEnabled && s.highlights != null && !s.highlights.isEmpty()) {
            TargetPests.EntityInfoCache cache = HighlightESP.getCachedInfo(self);
            String name = cache != null ? cache.combinedName : "";
            String nametagName = cache != null ? cache.nametagName : null;
            boolean isPlayer = self instanceof Player;
            for (Map.Entry<String, BomboConfig.HighlightInfo> entry : s.highlights.entrySet()) {
               String key = entry.getKey();
               if (key == null || key.isEmpty()) continue;
               boolean keyIsPlayer = key.equalsIgnoreCase("player") || key.equalsIgnoreCase("players");
               boolean matches = (isPlayer && keyIsPlayer)
                  || matchesKey(name, key)
                  || (nametagName != null && matchesKey(nametagName, key));
               if (!matches) continue;
               BomboConfig.HighlightInfo info = entry.getValue();
               if (info == null || !info.enabled || !matchesIsland(info.requiredIsland) || (self.isInvisible() && !info.showInvisible)) continue;
               int color = BomboRenderUtils.colorNameToHex(info.color);
               return new EntityHighlightInfo(now, true, color, info.tracer, color, info.showInvisible);
            }
         }
      } catch (Throwable ignored) {}
      return new EntityHighlightInfo(now, false, null, false, 0xFFFFFF, false);
   }

   public static boolean isEntityHighlighted(Entity self) {
      EntityHighlightInfo info = getHighlightInfo(self);
      return info != null && info.highlighted;
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
