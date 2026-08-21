package me.bombo.bomboaddons;

import com.mojang.authlib.properties.Property;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

public class TargetPests {
   public static final Map<String, String> PEST_MAP = new HashMap();
   private static int debugCounter = 0;
   private static final Map<Integer, String> pestCache = new ConcurrentHashMap();
   private static final Map<Integer, String> headHashMap = new ConcurrentHashMap();
   public static final Map<Integer, EntityInfoCache> infoCache;

   public static String getPestName(ArmorStand stand) {
      if (pestCache.size() > 5000) {
         pestCache.clear();
      }

      int id = stand.getId();
      String cached = (String)pestCache.get(id);
      if (cached != null) {
         return cached.isEmpty() ? null : cached;
      } else {
         String pestName = getPestNameInternal(stand);
         pestCache.put(id, pestName == null ? "" : pestName);
         return pestName;
      }
   }

   public static String getHeadTextureValue(Entity entity) {
      if (entity == null) return null;
      if (headHashMap.size() > 5000) {
         headHashMap.clear();
      }
      int id = entity.getId();
      String cached = headHashMap.get(id);
      if (cached != null) {
         return cached.isEmpty() ? null : cached;
      }
      String val = getHeadTextureValueInternal(entity);
      headHashMap.put(id, val == null ? "" : val);
      return val;
   }

   public static String getHeadTextureFromStack(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return null;
      if (stack.is(Items.PLAYER_HEAD)) {
         ResolvableProfile profile = (ResolvableProfile)stack.get(DataComponents.PROFILE);
         if (profile != null) {
            if (profile.partialProfile() != null && profile.partialProfile().properties() != null) {
               for (Property prop : profile.partialProfile().properties().get("textures")) {
                  if (prop != null && prop.value() != null && !prop.value().isEmpty()) {
                     return prop.value();
                  }
               }
            }
            try {
               for (java.lang.reflect.Method m : profile.getClass().getMethods()) {
                  if (m.getParameterCount() == 0 && (m.getName().equals("gameProfile") || m.getName().equals("partialProfile") || m.getName().equals("properties"))) {
                     Object res = m.invoke(profile);
                     if (res != null) {
                        if (res instanceof com.mojang.authlib.properties.PropertyMap) {
                           for (Property prop : ((com.mojang.authlib.properties.PropertyMap)res).get("textures")) {
                              if (prop != null && prop.value() != null && !prop.value().isEmpty()) return prop.value();
                           }
                        } else {
                           for (java.lang.reflect.Method m2 : res.getClass().getMethods()) {
                              if (m2.getParameterCount() == 0 && (m2.getName().equals("properties") || m2.getName().equals("getProperties"))) {
                                 Object props = m2.invoke(res);
                                 if (props instanceof com.mojang.authlib.properties.PropertyMap) {
                                    for (Property prop : ((com.mojang.authlib.properties.PropertyMap)props).get("textures")) {
                                       if (prop != null && prop.value() != null && !prop.value().isEmpty()) return prop.value();
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            } catch (Throwable ignored) {}
         }
         net.minecraft.world.item.component.CustomData customData = (net.minecraft.world.item.component.CustomData)stack.get(DataComponents.CUSTOM_DATA);
         if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            if (tag.contains("SkullOwner")) {
               net.minecraft.nbt.CompoundTag owner = tag.getCompound("SkullOwner").orElse(null);
               if (owner != null && owner.contains("Properties")) {
                  net.minecraft.nbt.CompoundTag props = owner.getCompound("Properties").orElse(null);
                  if (props != null && props.contains("textures")) {
                     net.minecraft.nbt.ListTag list = props.getList("textures").orElse(null);
                     if (list != null && !list.isEmpty()) {
                        for (int i = 0; i < list.size(); i++) {
                           net.minecraft.nbt.CompoundTag texTag = list.getCompound(i).orElse(null);
                           if (texTag != null && texTag.contains("Value")) {
                              String val = texTag.getString("Value").orElse(null);
                              if (val != null && !val.isEmpty()) return val;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
      return null;
   }

   private static final java.util.regex.Pattern HASH_PATTERN = java.util.regex.Pattern.compile("[0-9a-fA-F]{64}");

   private static String getHeadTextureValueInternal(Entity entity) {
      if (entity instanceof net.minecraft.world.entity.item.ItemEntity) {
         ItemStack stack = ((net.minecraft.world.entity.item.ItemEntity)entity).getItem();
         String tex = getHeadTextureFromStack(stack);
         if (tex != null) return tex;
      }
      if (entity instanceof net.minecraft.world.entity.player.Player player) {
         if (player.getGameProfile() != null && player.getGameProfile().properties() != null) {
            for (Property prop : player.getGameProfile().properties().get("textures")) {
               if (prop != null && prop.value() != null && !prop.value().isEmpty()) {
                  return prop.value();
               }
            }
         }
         try {
            if (player instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
               Object skin = acp.getSkin();
               if (skin != null) {
                  for (java.lang.reflect.Method m : skin.getClass().getMethods()) {
                     if (m.getParameterCount() == 0 && (m.getName().equals("texture") || m.getName().equals("body") || m.getName().equals("skin") || m.getName().equals("textureUrl"))) {
                        Object res = m.invoke(skin);
                        if (res != null) return res.toString();
                     }
                  }
                  return skin.toString();
               }
            }
         } catch (Throwable ignored) {}
         ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
         String tex = getHeadTextureFromStack(head);
         if (tex != null) return tex;
      }
      if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
         for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = living.getItemBySlot(slot);
            String tex = getHeadTextureFromStack(stack);
            if (tex != null) return tex;
         }
      }
      if (entity != null) {
         for (Entity pass : entity.getPassengers()) {
            if (pass instanceof net.minecraft.world.entity.LivingEntity passLiving) {
               ItemStack head = passLiving.getItemBySlot(EquipmentSlot.HEAD);
               String tex = getHeadTextureFromStack(head);
               if (tex != null) return tex;
            }
         }
         if (entity.getVehicle() instanceof net.minecraft.world.entity.LivingEntity vLiving) {
            ItemStack head = vLiving.getItemBySlot(EquipmentSlot.HEAD);
            String tex = getHeadTextureFromStack(head);
            if (tex != null) return tex;
         }
         if (entity.level() != null && !(entity instanceof ArmorStand)) {
            net.minecraft.world.phys.AABB box = entity.getBoundingBox().inflate(0.5, 2.0, 0.5);
            for (Entity e2 : entity.level().getEntities(entity, box)) {
               if (e2 instanceof ArmorStand stand) {
                  ItemStack h = stand.getItemBySlot(EquipmentSlot.HEAD);
                  String tex = getHeadTextureFromStack(h);
                  if (tex != null) return tex;
               }
            }
         }
      }
      return null;
   }

   public static String extractTextureHash(String input) {
      if (input == null || input.isEmpty()) return null;
      java.util.regex.Matcher m = HASH_PATTERN.matcher(input);
      if (m.find()) {
         return m.group().toLowerCase();
      }
      try {
         String decoded = new String(java.util.Base64.getDecoder().decode(input), java.nio.charset.StandardCharsets.UTF_8);
         java.util.regex.Matcher m2 = HASH_PATTERN.matcher(decoded);
         if (m2.find()) {
            return m2.group().toLowerCase();
         }
      } catch (Exception ignored) {
      }
      return null;
   }

   private static String getPestNameInternal(ArmorStand stand) {
      ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
      if (head != null && head.is(Items.PLAYER_HEAD)) {
         ResolvableProfile profile = (ResolvableProfile)head.get(DataComponents.PROFILE);
         if (profile != null && profile.partialProfile() != null && profile.partialProfile().properties() != null) {
            for(Property prop : profile.partialProfile().properties().get("textures")) {
               String val = prop.value();
               if (BomboConfig.get().debugEntities && SkyblockUtils.isInGarden()) {
                  System.out.println("DEBUG: ArmorStand Texture: " + val);
               }

               if (PEST_MAP.containsKey(val)) {
                  return (String)PEST_MAP.get(val);
               }
            }
         }
      }

      return null;
   }

   static {
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTY5NzQ3MDQ1OTc0NywKICAicHJvZmlsZUlkIiA6ICIyNTBlNzc5MjZkNDM0ZDIyYWM2MTQ4N2EyY2M3YzAwNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMdW5hMTIxMDUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQwM2JhNDAyN2EzMzNkOGQyZmQzMmFiNTlkMWNmZGJhYTdkOTA4ZDgwZDIzODFkYjJhNjljYmU2NTQ1MGFkOCIKICAgIH0KICB9Cn0=", "worm");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTY5NzQ3MDQ3ODAzMCwKICAicHJvZmlsZUlkIiA6ICI0NmY3N2NjNmQ2MjU0NjEzYjc2NmYyZDRmMDM2MzZhNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaXNzV29sZiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mZDQwYWE1MDkwNTIzNWI2MjhlNzM3OWViMzFmYTQ1Y2Q0MWI1MDNmMDk3MjFkYjNjNDM3ZmNlZTM5MjA3ZGZjIgogICAgfQogIH0KfQ==", "wormass");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTY5Njk0NTAyOTQ2MSwKICAicHJvZmlsZUlkIiA6ICI3NTE0NDQ4MTkxZTY0NTQ2OGM5NzM5YTZlMzk1N2JlYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGFua3NNb2phbmciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTJhOWZlMDViYzY2M2VmY2QxMmU1NmEzY2NjNWVjMDM1YmY1NzdiNzg3MDg1NDhiNmY0ZmZjZjFkMzBlY2NmZSIKICAgIH0KICB9Cn0=", "mosquito");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTYxODQxOTcwMTc1MywKICAicHJvZmlsZUlkIiA6ICI3MzgyZGRmYmU0ODU0NTVjODI1ZjkwMGY4OGZkMzJmOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJCdUlJZXQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYThhYmI0NzFkYjBhYjc4NzAzMDExOTc5ZGM4YjQwNzk4YTk0MWYzYTRkZWMzZWM2MWNiZWVjMmFmOGNmZmU4IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=", "rat");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTY5Njk0NTA2MzI4MSwKICAicHJvZmlsZUlkIiA6ICJjN2FmMWNkNjNiNTE0Y2YzOGY4NWQ2ZDUxNzhjYThlNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJtb25zdGVyZ2FtZXIzMTUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ5MGU3Nzc4MjZhNTI0NjEzNjhlMjZkMWIyZTE5YmZhMWJhNTgyZDYwMjQ4M2U1NDVmNDEyNGQwZjczMTg0MiIKICAgIH0KICB9Cn0=", "fly");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTY5Njg3MDQxOTcyNSwKICAicHJvZmlsZUlkIiA6ICJkYjYzNWE3MWI4N2U0MzQ5YThhYTgwOTMwOWFhODA3NyIsCiAgInByb2ZpbGVOYW1lIiA6ICJFbmdlbHMxNzQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmU2YmFmNjQzMWE5ZGFhMmNhNjA0ZDVhM2MyNmU5YTc2MWQ1OTUyZjA4MTcxNzRhNGZlMGI3NjQ2MTZlMjFmZiIKICAgIH0KICB9Cn0=", "spider");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTY5Njg3MDQxOTcyNSwKICAicHJvZmlsZUlkIiA6ICJkYjYzNWE3MWI4N2U0MzQ5YThhYTgwOTMwOWFhODA3NyIsCiAgInByb2ZpbGVOYW1lIiA6ICJFbmdlbHMxNzQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmU2YmFmNjQzMWE5ZGFhMmNhNjA0ZDVhM2MyNmU5YTc2MWQ1OTUyZjA4MTcxNzRhNGZlMGI3NjQ2MTZlMjFmZiIKICAgIH0KICB9Cn0=", "mite");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTY5Njg3MDQwNTk1NCwKICAicHJvZmlsZUlkIiA6ICJiMTUyZDlhZTE1MTM0OWNmOWM2NmI0Y2RjMTA5NTZjOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaXNxdW90aCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS82NTQ4NWM0YjM0ZTViNTQ3MGJlOTRkZTEwMGU2MWY3ODE2ZjgxYmM1YTExZGZkZjBlY2NmODkwMTcyZGE1ZDBhIgogICAgfQogIH0KfQ==", "moth");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTcyMzE3OTgxMTI2NCwKICAicHJvZmlsZUlkIiA6ICJjZjc4YzFkZjE3ZTI0Y2Q5YTIxYmU4NWQ0NDk5ZWE4ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNYXR0c0FybW9yU3RhbmRzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2EyNGM2OWY5NmNlNTU2MjIxZTE5NWM4ZWYyYmZhZDcxZWJmN2Y5NWY1YWU5MTRhNDg0YThkMGVjMjE2NzI2NzQiCiAgICB9CiAgfQp9", "cricket");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTcyMzE3OTc4OTkzNCwKICAicHJvZmlsZUlkIiA6ICJlMjc5NjliODYyNWY0NDg1YjkyNmM5NTBhMDljMWMwMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJLRVZJTktFTE9LRSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83MGExZTgzNmJmMTk2OGIyZWFhNDgzNzIyN2ExOTIwNGYxNzI5NWQ4NzBlZTllNzU0YmQ2YjZkNjBkZGJlZDNjIgogICAgfQogIH0KfQ==", "beetle");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTY5NzQ3MDQ0MzA4MiwKICAicHJvZmlsZUlkIiA6ICJkOGNkMTNjZGRmNGU0Y2IzODJmYWZiYWIwOGIyNzQ4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJaYWNoeVphY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2E3OWQwZmQ2NzdiNTQ1MzA5NjExMTdlZjg0YWRjMjA2ZTJjYzUwNDVjMTM0NGQ2MWQ3NzZiZjhhYzJmZTFiYSIKICAgIH0KICB9Cn0=", "slug");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTY5NzU1NzA3NzAzNywKICAicHJvZmlsZUlkIiA6ICI0YjJlMGM1ODliZjU0ZTk1OWM1ZmJlMzg5MjQ1MzQzZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJfTmVvdHJvbl8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGIyNGE0ODJhMzJkYjFlYTc4ZmI5ODA2MGIwYzJmYTRhMzczY2JkMThhNjhlZGRkZWI3NDE5NDU1YTU5Y2RhOSIKICAgIH0KICB9Cn0=", "locust");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTcyNzkwNDc5NzQ1OSwKICAicHJvZmlsZUlkIiA6ICI0MmIwOTMyZDUwMWI0MWQ1YTM4YjEwOTcxYTYwYmYxMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJBaXJib2x0MDc4IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2YzNzllMDkyNTI4MTczMTRiZDBiNjk0ZjdkNTNiNDhhZjJjN2ZhODQ5OTEwOTgwMmE0MWJiMjk0ZDJmOTNlM2UiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==", "field mouse");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQxOTYxMiwKICAicHJvZmlsZUlkIiA6ICI0OWIzODUyNDdhMWY0NTM3YjBmN2MwZTFmMTVjMTc2NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJiY2QyMDMzYzYzZWM0YmY4IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFlMDRiYjYzNjdjYWE0ZTg4ZjVmZDBlZTgwZjA3NDVkMTM3YTYwNjAyMjNkYmJjNDJhMTY0NzFmZGY2NGJiODMiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==", "praying mantis");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQxODQzNywKICAicHJvZmlsZUlkIiA6ICIwNjY5Y2E1MGYyZWU0NTQxODhlYWQ3YTM3NTkzNDRlMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJDcjR6eWNsb3duVFYiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU0YWZmNGMwYjJkY2UzYTY3MjM0OWNjMGVlOWU2ZjNhOWRlZWJlNGIzNTU2ZTg0NjExZWNhMjUwYTc4MjFiZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9", "dragonfly");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQyMjEzNiwKICAicHJvZmlsZUlkIiA6ICIzNDY4Y2VjMWFlOTY0YWRmYWQyNjEzMGEwZGQ0NjRkYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJzdXJlZWxta18iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNlNzllOTBhZGYzNDcxOGYzMTNlYzI0ZDZjNjEzNWI2OWIzNzg4YzYxODQ5ODQ0NmNjYzgzY2E2NDBjMGIxNCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9", "firefly");
      PEST_MAP.put("ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQyMzg4OSwKICAicHJvZmlsZUlkIiA6ICIyY2Y2MzExZjUyMTM0NTE2YTEyNTY3NWUwMzk3NmU2MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJmaWdodHN0b2NrIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzNlNTI3ODJkN2YyYWFlZThhZjViYTI5MjhmZWM3ODg1ZTk0ODc5MzM0YzIyOTZiYzllN2UyZGJjNTQxOGU1OGYiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==", "pest");
      infoCache = new ConcurrentHashMap();
   }

   public static class EntityInfoCache {
      public final long lastCheckMs;
      public final String combinedName;
      public final String nametagName;

      public EntityInfoCache(long lastCheckMs, String combinedName, String nametagName) {
         this.lastCheckMs = lastCheckMs;
         this.combinedName = combinedName;
         this.nametagName = nametagName;
      }
   }
}
