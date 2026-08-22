package me.bombo.bomboaddons.features;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class TextureToggleManager {
   public static final TextureToggleManager INSTANCE = new TextureToggleManager();
   private final Map<String, Map<String, String>> itemIds;
   private final Map<String, GameProfile> cachedItems = new HashMap();
   public final Set<String> whitelistedItems = new HashSet();
   public boolean blacklistMode = false;

   private TextureToggleManager() {
      Map<String, Map<String, String>> loadedItemIds = null;

      try {
         InputStream is = this.getClass().getResourceAsStream("/assets/bomboaddons/ItemDataSet.json");
         if (is != null) {
            loadedItemIds = (Map)(new Gson()).fromJson(new InputStreamReader(is), (new TypeToken<Map<String, Map<String, String>>>() {
               {
                  Objects.requireNonNull(TextureToggleManager.this);
               }
            }).getType());
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      this.itemIds = (Map<String, Map<String, String>>)(loadedItemIds != null ? loadedItemIds : new HashMap());
   }

   public void init() {
      this.loadConfig();
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientLifecycleEvents.ClientStopping)(client) -> this.saveConfig());
   }

   public void toggleItem(String sbId) {
      boolean added = false;
      if (this.whitelistedItems.contains(sbId)) {
         this.whitelistedItems.remove(sbId);
      } else {
         added = this.whitelistedItems.add(sbId);
      }
      this.saveConfig();

      if (Minecraft.getInstance().player != null) {
         if (added) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("[BomboAddons] ").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)).append(Component.literal("Custom texture enabled for ").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))).append(Component.literal(sbId).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA))));
         } else {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("[BomboAddons] ").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)).append(Component.literal("Vanilla texture restored for ").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))).append(Component.literal(sbId).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA))));
         }
      }
   }

   public String skyblockId(ItemStack itemStack) {
      if (itemStack == null || itemStack.isEmpty()) {
         return null;
      }
      String id = me.bombo.bomboaddons.SkyblockUtils.getInternalIdRaw(itemStack);
      if (id != null && !id.isEmpty()) {
         return id;
      }
      CustomData customData = (CustomData)itemStack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         if (tag != null && tag.contains("quiver_arrow")) {
            return "NRP$QUIVER";
         }
      }
      return null;
   }

   public String modelId(ItemStack itemStack) {
      String id = this.skyblockId(itemStack);
      if (id != null && this.itemIds != null) {
         Map<String, String> map = (Map)this.itemIds.get(id);
         return map == null ? null : (String)map.get("model");
      } else {
         return null;
      }
   }

   public String getRawModel(String sbId) {
      if (sbId != null && this.itemIds != null) {
         Map<String, String> map = (Map)this.itemIds.get(sbId);
         return map != null ? (String)map.get("model") : null;
      } else {
         return null;
      }
   }

   public String getRawValue(String sbId) {
      if (sbId != null && this.itemIds != null) {
         Map<String, String> map = (Map)this.itemIds.get(sbId);
         return map != null ? (String)map.get("value") : null;
      } else {
         return null;
      }
   }

   public GameProfile gameProfile(ItemStack itemStack) {
      String sbId = this.skyblockId(itemStack);
      if (sbId == null) return null;
      if (this.itemIds != null && this.itemIds.containsKey(sbId)) {
         Map<String, String> cache = (Map)this.itemIds.get(sbId);
         if (cache != null && cache.containsKey("value")) {
            String value = (String)cache.get("value");
            if (!this.cachedItems.containsKey(sbId)) {
               PropertyMap map = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
               this.cachedItems.put(sbId, new GameProfile(UUID.randomUUID(), "bombo$fakeItem", map));
            }
            return (GameProfile)this.cachedItems.get(sbId);
         }
      }
      return null;
   }

   public boolean isPaperItem(ItemStack itemStack) {
      if (itemStack == null || itemStack.isEmpty()) return false;
      if (itemStack.is(net.minecraft.world.item.Items.PAPER)) return true;
      String path = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();
      if (path.equalsIgnoreCase("paper")) return true;
      String sbId = this.skyblockId(itemStack);
      if (sbId != null) {
         if (sbId.contains("PAPER") || sbId.contains("SILK") || sbId.contains("SCROLL") || sbId.contains("ENCHANTED_PAPER")) {
            return true;
         }
      }
      return false;
   }

   public boolean shouldBypass(ItemStack itemStack) {
      if (!BomboConfig.get().noResourcePack) {
         return false;
      }
      if (itemStack == null || itemStack.isEmpty()) {
         return false;
      }
      String sbId = this.skyblockId(itemStack);
      if (sbId == null) {
         return false;
      }
      if (this.blacklistMode) {
         return this.whitelistedItems.contains(sbId);
      } else {
         if (this.isPaperItem(itemStack)) {
            return false;
         }
         return !this.whitelistedItems.contains(sbId);
      }
   }

   public Identifier fromModelId(ItemStack itemStack, Identifier modelId) {
      String sbId = this.skyblockId(itemStack);
      if (sbId == null) {
         return modelId != null ? modelId : BuiltInRegistries.ITEM.getKey(itemStack.getItem());
      }
      if (!this.shouldBypass(itemStack)) {
         return modelId != null ? modelId : BuiltInRegistries.ITEM.getKey(itemStack.getItem());
      }
      if (this.itemIds != null && this.itemIds.containsKey(sbId)) {
         String id = this.modelId(itemStack);
         if (id != null) {
            if (id.equals("minecraft:player_head")) {
               Map<String, String> cache = (Map)this.itemIds.get(sbId);
               if (cache != null && cache.containsKey("value")) {
                  String value = (String)cache.get("value");
                  if (!this.cachedItems.containsKey(sbId)) {
                     PropertyMap map = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
                     this.cachedItems.put(sbId, new GameProfile(UUID.randomUUID(), "bombo$fakeItem", map));
                  }
               }
            }
            return Identifier.parse(id);
         }
      }
      return BuiltInRegistries.ITEM.getKey(itemStack.getItem());
   }

   private Path getConfigPath() {
      return Paths.get("config", "bomboaddons", "texturetoggle.json");
   }

   private void loadConfig() {
      Path path = this.getConfigPath();
      if (Files.exists(path, new LinkOption[0])) {
         try {
            String json = Files.readString(path);
            JsonObject obj = (JsonObject)(new Gson()).fromJson(json, JsonObject.class);
            if (obj.has("blacklistMode")) {
               this.blacklistMode = obj.get("blacklistMode").getAsBoolean();
            }

            if (obj.has("whitelist")) {
               this.whitelistedItems.clear();

               for(JsonElement e : obj.getAsJsonArray("whitelist")) {
                  this.whitelistedItems.add(e.getAsString());
               }
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

   }

   private void saveConfig() {
      Path path = this.getConfigPath();

      try {
         Files.createDirectories(path.getParent());
         JsonObject obj = new JsonObject();
         obj.addProperty("blacklistMode", this.blacklistMode);
         JsonArray arr = new JsonArray();

         for(String s : this.whitelistedItems) {
            arr.add(s);
         }

         obj.add("whitelist", arr);
         Files.writeString(path, (new Gson()).toJson(obj));
      } catch (Exception e) {
         e.printStackTrace();
      }

   }
}
