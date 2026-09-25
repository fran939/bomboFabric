package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.features.storageoverlay.BackpackPreview;
import me.bombo.bomboaddons.features.storageoverlay.StorageOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Inject(method = "setItem", at = @At("RETURN"))
	private void onSetStackInSlot(int slot, int revision, ItemStack stack, CallbackInfo ci) {
		if ((Object) this instanceof ChestMenu chestMenu) {
			Screen screen = Minecraft.getInstance().gui.screen();
			if (screen instanceof StorageOverlayScreen storageOverlayScreen) {
				storageOverlayScreen.refreshSearch();
			}
			if (screen != null) {
				BackpackPreview.updateFromContainer(screen.getTitle().getString(), chestMenu.getContainer());
			}
		}
	}

	@Inject(method = "initializeContents", at = @At("RETURN"))
	public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried, CallbackInfo ci) {
		if ((Object) this instanceof ChestMenu chestMenu) {
			Screen screen = Minecraft.getInstance().gui.screen();
			if (screen instanceof StorageOverlayScreen storageOverlayScreen) {
				storageOverlayScreen.refreshSearch();
			}
			if (screen != null) {
				BackpackPreview.updateFromContainer(screen.getTitle().getString(), chestMenu.getContainer());
			}
		}
	}
}
