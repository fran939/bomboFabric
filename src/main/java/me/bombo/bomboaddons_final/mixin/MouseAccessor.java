package me.bombo.bomboaddons_final.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseAccessor {
    @Accessor("x")
    double getX();

    @Accessor("y")
    double getY();
}
