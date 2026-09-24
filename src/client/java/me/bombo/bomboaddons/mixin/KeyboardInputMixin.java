package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.GardenMovement;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        if (me.bombo.bomboaddons.flavor.Flavor.get().isFreecamActive()) {
            this.keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
            this.moveVector = net.minecraft.world.phys.Vec2.ZERO;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (GardenMovement.isActive() && SkyblockUtils.isInGarden() && GardenMovement.isAllowedScreen(mc.gui.screen())) {
            boolean fwd = GardenMovement.isForward();
            boolean bwd = GardenMovement.isBackward();
            boolean lft = GardenMovement.isLeft();
            boolean rgt = GardenMovement.isRight();

            float forwardImpulse = (fwd == bwd ? 0.0F : (fwd ? 1.0F : -1.0F));
            float leftImpulse = (lft == rgt ? 0.0F : (lft ? 1.0F : -1.0F));

            this.keyPresses = new net.minecraft.world.entity.player.Input(fwd, bwd, lft, rgt, false, false, false);
            this.moveVector = new net.minecraft.world.phys.Vec2(leftImpulse, forwardImpulse);
        }
    }
}
