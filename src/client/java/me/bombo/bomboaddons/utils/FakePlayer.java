package me.bombo.bomboaddons.utils;

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class FakePlayer extends RemotePlayer {
   private final List<ItemStack> armor;
   private final Component username;

   public FakePlayer(GameProfile gameProfile, List<ItemStack> armor, Component username) {
      super(Minecraft.getInstance().level, gameProfile);
      this.armor = armor;
      this.username = username;
   }

   public ItemStack getItemBySlot(EquipmentSlot slot) {
      if (slot.getType() == Type.HUMANOID_ARMOR) {
         int idx = slot.getIndex();
         if (this.armor != null && this.armor.size() > idx) {
            return (ItemStack)this.armor.get(idx);
         }
      }

      return super.getItemBySlot(slot);
   }

   public Component getDisplayName() {
      return this.username;
   }

   public Vec3 position() {
      return Minecraft.getInstance().getCameraEntity() != null ? Minecraft.getInstance().getCameraEntity().position() : super.position();
   }
}
