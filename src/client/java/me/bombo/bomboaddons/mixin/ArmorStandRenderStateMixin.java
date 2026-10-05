package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.util.HeadOnlyRenderState;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ArmorStandRenderState.class)
public abstract class ArmorStandRenderStateMixin implements HeadOnlyRenderState {
    @Unique
    private boolean bombo$headOnly = false;

    @Override
    public boolean bombo$isHeadOnly() {
        return this.bombo$headOnly;
    }

    @Override
    public void bombo$setHeadOnly(boolean headOnly) {
        this.bombo$headOnly = headOnly;
    }
}
