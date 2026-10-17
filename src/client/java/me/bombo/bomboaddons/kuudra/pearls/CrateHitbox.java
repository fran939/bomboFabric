package me.bombo.bomboaddons.kuudra.pearls;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record CrateHitbox(
        Vec3 crateCenter,
        List<AABB> zombies,
        @Nullable AABB union,
        @Nullable Vec3 cylCenter,
        float cylRadius,
        float cylHeight,
        @Nullable Vec3 aimPoint,
        boolean draggable
) {}
