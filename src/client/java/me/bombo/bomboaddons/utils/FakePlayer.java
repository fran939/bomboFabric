package me.bombo.bomboaddons.utils;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.List;

public class FakePlayer extends RemotePlayer {
    private final List<ItemStack> armor;
    private final Component username;

    public FakePlayer(GameProfile gameProfile, List<ItemStack> armor, Component username) {
        super(Minecraft.getInstance().level, gameProfile);
        this.armor = armor;
        this.username = username;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            int idx = slot.getIndex();
            if (this.armor != null && this.armor.size() > idx) {
                return this.armor.get(idx);
            }
        }
        return super.getItemBySlot(slot);
    }

    @Override
    public Component getDisplayName() {
        return this.username;
    }

    @Override
    public net.minecraft.world.phys.Vec3 position() {
        if (Minecraft.getInstance().getCameraEntity() != null) {
            return Minecraft.getInstance().getCameraEntity().position();
        }
        return super.position();
    }
}
