package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.storageoverlay.BackpackPreview;
import me.bombo.bomboaddons.features.storageoverlay.StorageOverlayScreen;
import me.bombo.bomboaddons.features.storageoverlay.StorageOverlayScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(MenuScreens.ScreenConstructor.class)
public interface MenuScreensConstructorMixin<T extends AbstractContainerMenu> {
	@Inject(method = "fromPacket", at = @At("HEAD"), cancellable = true)
	private void bomboaddons$openStorageOverlay(Component name, MenuType<T> type, Minecraft client, int id, CallbackInfo ci) {
		LocalPlayer player = client.player;
		if (player == null) return;
		if (!SkyblockUtils.isOnSkyblock()) return;
		if (!BomboConfig.get().storageOverlay) return;

		T screenHandler = type.create(id, player.getInventory());
		String nameLowercase = name.getString().trim().toLowerCase(Locale.ENGLISH);

		if (screenHandler instanceof ChestMenu containerScreenHandler && StorageOverlayScreen.enabled(nameLowercase)) {
			int height = client.getWindow().getGuiScaledHeight() - (client.getWindow().getGuiScaledHeight() / 5);
			boolean isBackpack = BackpackPreview.getStorageIndexFromTitle(nameLowercase) != -1;
			StorageOverlayScreenHandler storageOverlayScreenHandler = new StorageOverlayScreenHandler(containerScreenHandler, isBackpack, height, player.getInventory());
			client.player.containerMenu = storageOverlayScreenHandler;
			client.gui.setScreen(new StorageOverlayScreen(storageOverlayScreenHandler, containerScreenHandler, name, client.player.getInventory(), height));

			ci.cancel();
		}
	}
}
