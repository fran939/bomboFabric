package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class OrderedWaypoints {
   private static final List<Waypoint> waypoints = new ArrayList<>();
   public static int currentWpIndex = 0;
   public static String routeRequiredIsland = "";
   public static String routeRequiredArmor = "";

   static {
      loadFromConfig();
   }

   public static List<Waypoint> getWaypoints() {
      synchronized (waypoints) {
         return new ArrayList<>(waypoints);
      }
   }

   public static void saveToConfig() {
      try {
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null) return;
         if (s.orderedRoutes == null) s.orderedRoutes = new java.util.LinkedHashMap<>();
         String active = s.activeOrderedRoute != null && !s.activeOrderedRoute.isEmpty() ? s.activeOrderedRoute : "Default";
         BomboConfig.OrderedRoute route = s.orderedRoutes.computeIfAbsent(active, BomboConfig.OrderedRoute::new);
         route.requiredIsland = routeRequiredIsland != null ? routeRequiredIsland : "";
         route.requiredArmor = routeRequiredArmor != null ? routeRequiredArmor : "";
         route.waypoints.clear();
         synchronized (waypoints) {
            for (Waypoint wp : waypoints) {
               BomboConfig.OrderedWaypointData data = new BomboConfig.OrderedWaypointData(wp.position.x, wp.position.y, wp.position.z, wp.name, wp.color);
               data.requiredIsland = wp.requiredIsland != null ? wp.requiredIsland : "";
               data.requiredArmor = wp.requiredArmor != null ? wp.requiredArmor : "";
               data.enabled = wp.enabled;
               route.waypoints.add(data);
            }
         }
         BomboConfig.save();
      } catch (Throwable ignored) {}
   }

   public static void loadFromConfig() {
      try {
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null) return;
         if (s.orderedRoutes == null) s.orderedRoutes = new java.util.LinkedHashMap<>();
         String active = s.activeOrderedRoute != null && !s.activeOrderedRoute.isEmpty() ? s.activeOrderedRoute : "Default";
         if (!s.orderedRoutes.containsKey(active)) {
            if (s.orderedRoutes.isEmpty()) {
               s.orderedRoutes.put("Default", new BomboConfig.OrderedRoute("Default"));
            } else {
               active = s.orderedRoutes.keySet().iterator().next();
               s.activeOrderedRoute = active;
            }
         }
         BomboConfig.OrderedRoute route = s.orderedRoutes.get(active);
         if (route != null) {
            routeRequiredIsland = route.requiredIsland != null ? route.requiredIsland : "";
            routeRequiredArmor = route.requiredArmor != null ? route.requiredArmor : "";
            synchronized (waypoints) {
               waypoints.clear();
               if (route.waypoints != null) {
                  for (BomboConfig.OrderedWaypointData data : route.waypoints) {
                     Waypoint wp = new Waypoint(new Vec3(data.x, data.y, data.z), data.name);
                     wp.color = data.color != null ? data.color : "Aqua";
                     wp.requiredIsland = data.requiredIsland != null ? data.requiredIsland : "";
                     wp.requiredArmor = data.requiredArmor != null ? data.requiredArmor : "";
                     wp.enabled = data.enabled;
                     wp.updateRgbFromColor();
                     waypoints.add(wp);
                  }
               }
               currentWpIndex = 0;
            }
         }
      } catch (Throwable ignored) {}
   }

   public static void selectRoute(String routeName) {
      if (routeName == null || routeName.trim().isEmpty()) return;
      saveToConfig();
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null) {
         s.activeOrderedRoute = routeName.trim();
         loadFromConfig();
         BomboConfig.save();
      }
   }

   public static void createRoute(String routeName) {
      if (routeName == null || routeName.trim().isEmpty()) return;
      saveToConfig();
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null) {
         if (s.orderedRoutes == null) s.orderedRoutes = new java.util.LinkedHashMap<>();
         String name = routeName.trim();
         s.orderedRoutes.put(name, new BomboConfig.OrderedRoute(name));
         s.activeOrderedRoute = name;
         loadFromConfig();
         BomboConfig.save();
      }
   }

   public static void deleteRoute(String routeName) {
      if (routeName == null || routeName.trim().isEmpty()) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.orderedRoutes != null) {
         s.orderedRoutes.remove(routeName);
         if (s.orderedRoutes.isEmpty()) {
            s.orderedRoutes.put("Default", new BomboConfig.OrderedRoute("Default"));
         }
         s.activeOrderedRoute = s.orderedRoutes.keySet().iterator().next();
         loadFromConfig();
         BomboConfig.save();
      }
   }

   public static List<String> getRouteNames() {
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || s.orderedRoutes == null || s.orderedRoutes.isEmpty()) {
         return List.of("Default");
      }
      return new ArrayList<>(s.orderedRoutes.keySet());
   }

   public static void toggleRouteEnabled(String routeName) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.orderedRoutes != null && s.orderedRoutes.containsKey(routeName)) {
         BomboConfig.OrderedRoute r = s.orderedRoutes.get(routeName);
         r.enabled = !r.enabled;
         for (BomboConfig.OrderedWaypointData data : r.waypoints) {
            data.enabled = r.enabled;
         }
         loadFromConfig();
         BomboConfig.save();
      }
   }

   public static void toggleRouteCollapsed(String routeName) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.orderedRoutes != null && s.orderedRoutes.containsKey(routeName)) {
         BomboConfig.OrderedRoute r = s.orderedRoutes.get(routeName);
         r.collapsed = !r.collapsed;
         BomboConfig.save();
      }
   }

   public static void setRouteColor(String routeName, String color) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.orderedRoutes != null && s.orderedRoutes.containsKey(routeName)) {
         BomboConfig.OrderedRoute r = s.orderedRoutes.get(routeName);
         r.color = color;
         for (BomboConfig.OrderedWaypointData data : r.waypoints) {
            data.color = color;
         }
         loadFromConfig();
         BomboConfig.save();
      }
   }

   public static void addWaypointToRoute(String routeName, Vec3 pos, String name, String color) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null) {
         if (s.orderedRoutes == null) s.orderedRoutes = new java.util.LinkedHashMap<>();
         BomboConfig.OrderedRoute r = s.orderedRoutes.computeIfAbsent(routeName, BomboConfig.OrderedRoute::new);
         int idx = r.waypoints.size() + 1;
         String wpName = (name != null && !name.isEmpty()) ? name : "Waypoint " + idx;
         String wpColor = (color != null && !color.isEmpty()) ? color : (r.color != null ? r.color : "Aqua");
         r.waypoints.add(new BomboConfig.OrderedWaypointData(pos.x, pos.y, pos.z, wpName, wpColor));
         loadFromConfig();
         BomboConfig.save();
      }
   }

   public static void removeWaypointFromRoute(String routeName, int index) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.orderedRoutes != null && s.orderedRoutes.containsKey(routeName)) {
         BomboConfig.OrderedRoute r = s.orderedRoutes.get(routeName);
         if (index >= 0 && index < r.waypoints.size()) {
            r.waypoints.remove(index);
            loadFromConfig();
            BomboConfig.save();
         }
      }
   }

   public static void toggleWaypointInRoute(String routeName, int index) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.orderedRoutes != null && s.orderedRoutes.containsKey(routeName)) {
         BomboConfig.OrderedRoute r = s.orderedRoutes.get(routeName);
         if (index >= 0 && index < r.waypoints.size()) {
            r.waypoints.get(index).enabled = !r.waypoints.get(index).enabled;
            loadFromConfig();
            BomboConfig.save();
         }
      }
   }

   public static void setWaypointColorInRoute(String routeName, int index, String color) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.orderedRoutes != null && s.orderedRoutes.containsKey(routeName)) {
         BomboConfig.OrderedRoute r = s.orderedRoutes.get(routeName);
         if (index >= 0 && index < r.waypoints.size()) {
            r.waypoints.get(index).color = color;
            loadFromConfig();
            BomboConfig.save();
         }
      }
   }

   public static void reorderWaypointInRoute(String routeName, int fromIndex, int toIndex) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.orderedRoutes != null && s.orderedRoutes.containsKey(routeName)) {
         BomboConfig.OrderedRoute r = s.orderedRoutes.get(routeName);
         if (fromIndex >= 0 && fromIndex < r.waypoints.size() && toIndex >= 0 && toIndex < r.waypoints.size() && fromIndex != toIndex) {
            BomboConfig.OrderedWaypointData item = r.waypoints.remove(fromIndex);
            r.waypoints.add(toIndex, item);
            loadFromConfig();
            BomboConfig.save();
         }
      }
   }

   public static void clear() {
      synchronized (waypoints) {
         waypoints.clear();
         currentWpIndex = 0;
         saveToConfig();
      }
   }

   public static void moveUp(int index) {
      synchronized (waypoints) {
         if (index > 0 && index < waypoints.size()) {
            Collections.swap(waypoints, index, index - 1);
            saveToConfig();
         }
      }
   }

   public static void moveDown(int index) {
      synchronized (waypoints) {
         if (index >= 0 && index < waypoints.size() - 1) {
            Collections.swap(waypoints, index, index + 1);
            saveToConfig();
         }
      }
   }

   public static void addWaypoint(int index, Waypoint wp) {
      synchronized (waypoints) {
         if (wp == null) return;
         wp.updateRgbFromColor();
         if (index >= 0 && index <= waypoints.size()) {
            waypoints.add(index, wp);
         } else {
            waypoints.add(wp);
         }
         saveToConfig();
      }
   }

   public static Waypoint removeWaypoint(int index) {
      synchronized (waypoints) {
         if (index >= 0 && index < waypoints.size()) {
            Waypoint wp = waypoints.remove(index);
            if (currentWpIndex >= waypoints.size()) {
               currentWpIndex = Math.max(0, waypoints.size() - 1);
            }
            saveToConfig();
            return wp;
         }
         return null;
      }
   }

   public static void delete(int index) {
      removeWaypoint(index);
   }

   public static void setColor(int index, String colorName) {
      synchronized (waypoints) {
         if (index >= 0 && index < waypoints.size()) {
            Waypoint wp = waypoints.get(index);
            wp.color = colorName;
            wp.updateRgbFromColor();
            saveToConfig();
         }
      }
   }

   public static boolean matchesIsland(String island) {
      if (island == null || island.trim().isEmpty() || island.equalsIgnoreCase("all")) return true;
      String currentLoc = SkyblockUtils.getLocation();
      if (currentLoc == null || currentLoc.equals("Unknown") || currentLoc.equals("None")) {
         currentLoc = BomboaddonsClient.currentArea;
      }
      if (currentLoc == null) return false;
      return currentLoc.toLowerCase().contains(island.toLowerCase().trim());
   }

   public static boolean matchesArmor(String armor) {
      if (armor == null || armor.trim().isEmpty() || armor.equalsIgnoreCase("all")) return true;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return false;
      String req = armor.toLowerCase().trim();
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = mc.player.getItemBySlot(slot);
         if (stack != null && !stack.isEmpty()) {
            String name = stack.getHoverName().getString().toLowerCase();
            String sbId = SkyblockUtils.getSkyblockId(stack);
            if (name.contains(req) || (sbId != null && sbId.toLowerCase().contains(req))) {
               return true;
            }
         }
      }
      return false;
   }

   public static String exportSkyHanniJson() {
      List<Waypoint> list = getWaypoints();
      if (list.isEmpty()) return "[]";
      JsonArray arr = new JsonArray();
      for (Waypoint wp : list) {
         JsonObject obj = new JsonObject();
         obj.addProperty("x", Math.floor(wp.position.x));
         obj.addProperty("y", Math.floor(wp.position.y));
         obj.addProperty("z", Math.floor(wp.position.z));
         obj.addProperty("r", wp.r);
         obj.addProperty("g", wp.g);
         obj.addProperty("b", wp.b);

         JsonObject opts = new JsonObject();
         opts.addProperty("name", wp.name);
         if (wp.requiredIsland != null && !wp.requiredIsland.isEmpty()) {
            opts.addProperty("island", wp.requiredIsland);
         }
         if (wp.requiredArmor != null && !wp.requiredArmor.isEmpty()) {
            opts.addProperty("armor", wp.requiredArmor);
         }
         opts.addProperty("color", wp.color);
         opts.addProperty("showBeacon", true);
         obj.add("options", opts);
         arr.add(obj);
      }
      String json = arr.toString();
      try { Minecraft.getInstance().keyboardHandler.setClipboard(json); } catch (Exception ignored) {}
      return json;
   }

   public static String exportSkyblockerV1() {
      List<Waypoint> list = getWaypoints();
      if (list.isEmpty()) return "[Skyblocker-Waypoint-Data-V1]";
      JsonArray routes = new JsonArray();
      JsonObject route = new JsonObject();
      route.addProperty("name", "Bombo Route");
      JsonArray wps = new JsonArray();
      for (Waypoint wp : list) {
         JsonObject obj = new JsonObject();
         obj.addProperty("name", wp.name);
         JsonArray pos = new JsonArray();
         pos.add(Math.floor(wp.position.x));
         pos.add(Math.floor(wp.position.y));
         pos.add(Math.floor(wp.position.z));
         obj.add("pos", pos);
         wps.add(obj);
      }
      route.add("waypoints", wps);
      routes.add(route);
      byte[] jsonBytes = routes.toString().getBytes(StandardCharsets.UTF_8);
      byte[] compressed = compressGzip(jsonBytes);
      String base64 = Base64.getEncoder().encodeToString(compressed != null ? compressed : jsonBytes);
      String out = "[Skyblocker-Waypoint-Data-V1]" + base64;
      try { Minecraft.getInstance().keyboardHandler.setClipboard(out); } catch (Exception ignored) {}
      return out;
   }

   public static String exportWaypointerWpl() {
      List<Waypoint> list = getWaypoints();
      if (list.isEmpty()) return "WPL:1:";
      JsonArray arr = new JsonArray();
      for (Waypoint wp : list) {
         JsonObject obj = new JsonObject();
         obj.addProperty("x", Math.floor(wp.position.x));
         obj.addProperty("y", Math.floor(wp.position.y));
         obj.addProperty("z", Math.floor(wp.position.z));
         obj.addProperty("r", wp.r);
         obj.addProperty("g", wp.g);
         obj.addProperty("b", wp.b);
         JsonObject opts = new JsonObject();
         opts.addProperty("name", wp.name);
         obj.add("options", opts);
         arr.add(obj);
      }
      byte[] jsonBytes = arr.toString().getBytes(StandardCharsets.UTF_8);
      byte[] compressed = compressGzip(jsonBytes);
      String encoded = encodeBase91(compressed != null ? compressed : jsonBytes);
      String out = "WPL:1:" + encoded;
      try { Minecraft.getInstance().keyboardHandler.setClipboard(out); } catch (Exception ignored) {}
      return out;
   }

   private static byte[] compressGzip(byte[] data) {
      try {
         java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
         try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
         }
         return baos.toByteArray();
      } catch (Exception ignored) {
         return null;
      }
   }

   private static String encodeBase91(byte[] data) {
      StringBuilder sb = new StringBuilder();
      int ebq = 0, en = 0;
      for (byte b : data) {
         ebq |= (b & 255) << en;
         en += 8;
         if (en > 13) {
            int v = ebq & 8191;
            if (v > 88) {
               ebq >>= 13;
               en -= 13;
            } else {
               v = ebq & 16383;
               ebq >>= 14;
               en -= 14;
            }
            sb.append(BASE91_ALPHABET.charAt(v % 91));
            sb.append(BASE91_ALPHABET.charAt(v / 91));
         }
      }
      if (en > 0) {
         sb.append(BASE91_ALPHABET.charAt(ebq % 91));
         if (en > 7 || ebq > 90) {
            sb.append(BASE91_ALPHABET.charAt(ebq / 91));
         }
      }
      return sb.toString();
   }

   public static int importWaypointsFromClipboard(String data) {
      if (data == null || data.trim().isEmpty()) return 0;
      data = data.trim();
      int count = 0;
      List<Waypoint> tempWaypoints = new ArrayList<>();

      try {
         if (data.startsWith("[Skyblocker-Waypoint-Data-V1]")) {
            data = data.substring("[Skyblocker-Waypoint-Data-V1]".length());
            byte[] decoded = Base64.getDecoder().decode(data);
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(decoded));
                 BufferedReader br = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
               String jsonStr = br.lines().collect(Collectors.joining());
               parseSkyblockerJson(jsonStr, tempWaypoints);
            }
         } else if (data.startsWith("WPL:1:")) {
            String encoded = data.substring("WPL:1:".length()).trim();
            byte[] rawBytes = decodeBase91(encoded);
            String jsonStr = null;
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(rawBytes));
                 BufferedReader br = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
               jsonStr = br.lines().collect(Collectors.joining());
            } catch (Exception ignored) {}

            if (jsonStr == null) {
               try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(rawBytes));
                    BufferedReader br = new BufferedReader(new InputStreamReader(iis, StandardCharsets.UTF_8))) {
                  jsonStr = br.lines().collect(Collectors.joining());
               } catch (Exception ignored) {}
            }

            if (jsonStr == null) {
               jsonStr = new String(rawBytes, StandardCharsets.UTF_8);
            }

            if (jsonStr != null && !jsonStr.isEmpty()) {
               parseGenericJson(jsonStr, tempWaypoints);
            }
         } else if (data.startsWith("[") && data.endsWith("]")) {
            parseGenericJson(data, tempWaypoints);
         } else if (data.startsWith("{") && data.endsWith("}")) {
            parseGenericJson(data, tempWaypoints);
         }

         try {
            tempWaypoints.sort((a, b) -> {
               try {
                  return Integer.compare(Integer.parseInt(a.name.replaceAll("[^0-9]", "")),
                                         Integer.parseInt(b.name.replaceAll("[^0-9]", "")));
               } catch (Exception var3) {
                  return a.name.compareTo(b.name);
               }
            });
         } catch (Exception ignored) {}

         if (!tempWaypoints.isEmpty()) {
            synchronized (waypoints) {
               waypoints.clear();
               waypoints.addAll(tempWaypoints);
               currentWpIndex = 0;
            }
            saveToConfig();
            count = tempWaypoints.size();
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      return count;
   }

   private static void parseGenericJson(String json, List<Waypoint> dest) {
      try {
         JsonElement el = JsonParser.parseString(json);
         if (el.isJsonArray()) {
            for (JsonElement wpEl : el.getAsJsonArray()) {
               if (wpEl.isJsonObject()) {
                  parseWaypointObject(wpEl.getAsJsonObject(), dest);
               }
            }
         } else if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("waypoints") && obj.get("waypoints").isJsonArray()) {
               for (JsonElement wpEl : obj.getAsJsonArray("waypoints")) {
                  if (wpEl.isJsonObject()) {
                     parseWaypointObject(wpEl.getAsJsonObject(), dest);
                  }
               }
            } else if (obj.has("x") && obj.has("y") && obj.has("z")) {
               parseWaypointObject(obj, dest);
            }
         }
      } catch (Exception ignored) {}
   }

   private static void parseSkyblockerJson(String json, List<Waypoint> dest) {
      try {
         JsonElement el = JsonParser.parseString(json);
         if (el.isJsonArray()) {
            for (JsonElement routeEl : el.getAsJsonArray()) {
               if (!routeEl.isJsonObject()) continue;
               JsonObject route = routeEl.getAsJsonObject();
               if (route.has("waypoints")) {
                  for (JsonElement wpEl : route.getAsJsonArray("waypoints")) {
                     if (!wpEl.isJsonObject()) continue;
                     JsonObject wp = wpEl.getAsJsonObject();
                     if (wp.has("pos") && wp.get("pos").isJsonArray()) {
                        JsonArray pos = wp.getAsJsonArray("pos");
                        String name = wp.has("name") ? wp.get("name").getAsString() : "Waypoint";
                        double x = pos.get(0).getAsDouble();
                        double y = pos.get(1).getAsDouble();
                        double z = pos.get(2).getAsDouble();
                        dest.add(new Waypoint(new Vec3(x, y, z), name));
                     }
                  }
               }
            }
         }
      } catch (Exception ignored) {}
   }

   private static void parseWaypointObject(JsonObject wp, List<Waypoint> dest) {
      double x = 0, y = 0, z = 0;
      if (wp.has("x") && wp.has("y") && wp.has("z")) {
         x = wp.get("x").getAsDouble();
         y = wp.get("y").getAsDouble();
         z = wp.get("z").getAsDouble();
      } else if (wp.has("pos") && wp.get("pos").isJsonArray()) {
         JsonArray pos = wp.getAsJsonArray("pos");
         x = pos.get(0).getAsDouble();
         y = pos.get(1).getAsDouble();
         z = pos.get(2).getAsDouble();
      } else {
         return;
      }

      String name = "Waypoint";
      String island = "";
      String armor = "";
      String color = "Aqua";
      float r = 0.0f, g = 1.0f, b = 1.0f;
      boolean showBeacon = true;

      if (wp.has("r") && wp.has("g") && wp.has("b")) {
         r = wp.get("r").getAsFloat();
         g = wp.get("g").getAsFloat();
         b = wp.get("b").getAsFloat();
      }

      if (wp.has("options") && wp.get("options").isJsonObject()) {
         JsonObject opt = wp.getAsJsonObject("options");
         if (opt.has("name")) name = opt.get("name").getAsString();
         if (opt.has("island")) island = opt.get("island").getAsString();
         if (opt.has("armor")) armor = opt.get("armor").getAsString();
         if (opt.has("color")) color = opt.get("color").getAsString();
         if (opt.has("showBeacon")) showBeacon = opt.get("showBeacon").getAsBoolean();
      } else if (wp.has("name")) {
         name = wp.get("name").getAsString();
      }

      Waypoint w = new Waypoint(new Vec3(x, y, z), name);
      w.color = color != null ? color : "Aqua";
      w.requiredIsland = island != null ? island : "";
      w.requiredArmor = armor != null ? armor : "";
      if (wp.has("r") && wp.has("g") && wp.has("b")) {
         w.r = r;
         w.g = g;
         w.b = b;
      } else {
         w.updateRgbFromColor();
      }
      dest.add(w);
   }

   private static final String BASE91_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$%&()*+,./:;<=>?@[]^_`{|}~\"";

   private static byte[] decodeBase91(String data) {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      int ebq = 0, en = 0, v = -1;
      for (int i = 0; i < data.length(); ++i) {
         char c = data.charAt(i);
         int d = BASE91_ALPHABET.indexOf(c);
         if (d == -1) continue;
         if (v < 0) {
            v = d;
         } else {
            v += d * 91;
            ebq |= v << en;
            en += (v & 8191) > 88 ? 13 : 14;
            do {
               baos.write((byte) ebq);
               ebq >>= 8;
               en -= 8;
            } while (en > 7);
            v = -1;
         }
      }
      if (v != -1) {
         baos.write((byte) (ebq | v << en));
      }
      return baos.toByteArray();
   }

   public static void render(LevelRenderContext context) {
      if (waypoints.isEmpty()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) return;

      if (!matchesIsland(routeRequiredIsland) || !matchesArmor(routeRequiredArmor)) {
         return;
      }

      synchronized (waypoints) {
         if (waypoints.isEmpty()) return;
         if (currentWpIndex >= waypoints.size()) {
            currentWpIndex = 0;
         }

         Waypoint current = waypoints.get(currentWpIndex);
         double distToCurrent = current.position.distanceTo(mc.player.position());
         if (distToCurrent < 3.0) {
            ++currentWpIndex;
            if (currentWpIndex >= waypoints.size()) {
               currentWpIndex = 0;
            }
            current = waypoints.get(currentWpIndex);
         }

         BomboConfig.Settings s = BomboConfig.get();
         float borderWidth = s != null && s.waypointBorderWidth > 0 ? s.waypointBorderWidth : 2.5f;
         boolean throughWalls = s == null || s.orderedWaypointsThroughWalls;
         int visibleAhead = (s != null && s.orderedWaypointsVisibleCount > 0) ? s.orderedWaypointsVisibleCount : 999;

         Vec3 camPos = mc.gameRenderer.mainCamera().position();
         PoseStack poseStack = context.poseStack();
         OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());

         int endIndex = Math.min(waypoints.size(), currentWpIndex + visibleAhead);
         for (int i = currentWpIndex; i < endIndex; i++) {
            Waypoint wp = waypoints.get(i);
            boolean isTarget = (i == currentWpIndex);
            float wpR = isTarget ? wp.r : wp.r * 0.75f;
            float wpG = isTarget ? wp.g : wp.g * 0.75f;
            float wpB = isTarget ? wp.b : wp.b * 0.75f;

            double bx = Math.floor(wp.position.x) - camPos.x;
            double by = Math.floor(wp.position.y) - camPos.y;
            double bz = Math.floor(wp.position.z) - camPos.z;
            AABB box = new AABB(bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0);

            // Bounding box
            net.minecraft.client.renderer.rendertype.RenderType lineType = throughWalls ? RenderTypes.secondaryBlockOutline() : RenderTypes.linesTranslucent();
            collector.submitCustomGeometry(poseStack, lineType, (pose, vertexConsumer) ->
                    BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, wpR, wpG, wpB, isTarget ? 1.0f : 0.6f, isTarget ? borderWidth : 1.5f));

            // Fill box
            if (s != null && s.waypointShowFill) {
               float fillAlpha = Math.max(0.05f, Math.min(1.0f, (float) s.waypointFillOpacity / 100.0f)) * (isTarget ? 1.0f : 0.6f);
               net.minecraft.client.renderer.rendertype.RenderType fillType = throughWalls ? RenderTypes.debugFilledBox() : RenderTypes.debugQuads();
               collector.submitCustomGeometry(poseStack, fillType, (pose, vertexConsumer) ->
                       BomboRenderUtils.drawFilledBox(pose.pose(), vertexConsumer, (float) bx, (float) by, (float) bz, (float) (bx + 1.0), (float) (by + 1.0), (float) (bz + 1.0), wpR, wpG, wpB, fillAlpha));
            }

            // Waypoint Label
            double wpDist = wp.position.distanceTo(mc.player.position());
            String label = isTarget ? String.format(Locale.US, "§b▶ #%d §f%s §7(%.0fm)", (i + 1), wp.name, wpDist)
                                    : String.format(Locale.US, "§7#%d §f%s §8(%.0fm)", (i + 1), wp.name, wpDist);
            BomboRenderUtils.drawText(poseStack, collector, label, (float) (bx + 0.5), (float) (by + 1.3), (float) (bz + 0.5), isTarget ? 0xFFFFFFFF : 0xFFAAAAAA, 0.035f, true, throughWalls);

            // Path Tracer Line to Active Target
            if (isTarget) {
               float startX = mc.gameRenderer.mainCamera().forwardVector().x();
               float startY = mc.gameRenderer.mainCamera().forwardVector().y();
               float startZ = mc.gameRenderer.mainCamera().forwardVector().z();
               float endX = (float) (wp.position.x - camPos.x);
               float endY = (float) (wp.position.y - camPos.y + 0.5);
               float endZ = (float) (wp.position.z - camPos.z);
               float dist = (float) Math.sqrt(endX * endX + endY * endY + endZ * endZ);
               float scale = dist > 0.2f ? (0.2f / dist) : 1.0f;

               float startXScaled = startX * scale;
               float startYScaled = startY * scale;
               float startZScaled = startZ * scale;
               float endXScaled = endX * scale;
               float endYScaled = endY * scale;
               float endZScaled = endZ * scale;

               collector.submitCustomGeometry(poseStack, lineType, (pose, vertexConsumer) ->
                       BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startXScaled, startYScaled, startZScaled, endXScaled, endYScaled, endZScaled, wpR, wpG, wpB, 0.9f, 2.0f));
            }
         }
      }
   }

   public static class Waypoint {
      public final Vec3 position;
      public String name;
      public String color = "Aqua";
      public float r = 0.0f;
      public float g = 1.0f;
      public float b = 1.0f;
      public String requiredIsland = "";
      public String requiredArmor = "";
      public boolean enabled = true;

      public Waypoint(Vec3 position, String name) {
         this.position = position;
         this.name = name != null ? name : "Waypoint";
         this.updateRgbFromColor();
      }

      public void updateRgbFromColor() {
         int colHex = BomboRenderUtils.colorNameToHex(this.color != null ? this.color : "Aqua");
         this.r = (float) ((colHex >> 16) & 0xFF) / 255.0f;
         this.g = (float) ((colHex >> 8) & 0xFF) / 255.0f;
         this.b = (float) (colHex & 0xFF) / 255.0f;
      }
   }
}
