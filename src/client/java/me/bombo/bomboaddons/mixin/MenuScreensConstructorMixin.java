package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
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

@Mixin(MenuScreens.class)
public class MenuScreensConstructorMixin {
	@Inject(method = "create", at = @At("HEAD"), cancellable = true)
	private static <T extends AbstractContainerMenu> void bomboaddons$openStorageOverlay(MenuType<T> type, Minecraft client, int id, Component name, CallbackInfo ci) {
		LocalPlayer player = client.player;
		if (player == null) {
			Bomboaddons.LOGGER.debug("[StorageOverlay] MenuScreens.create called but player is null (id: {})", id);
			return;
		}

		boolean configOn = BomboConfig.get().storageOverlay;
		String rawTitle = name != null ? name.getString() : "null";
		String nameClean = net.minecraft.ChatFormatting.stripFormatting(rawTitle).trim().toLowerCase(Locale.ROOT);
		
		boolean isEligible = StorageOverlayScreen.enabled(nameClean);
		Bomboaddons.LOGGER.info("[StorageOverlay] Intercepted menu create: id={}, title='{}' (clean='{}'), configEnabled={}, eligible={}", 
				id, rawTitle, nameClean, configOn, isEligible);

		if (!configOn) return;
		if (!isEligible) {
			Bomboaddons.LOGGER.info("[StorageOverlay] Skipped overlay: title '{}' not recognized as storage/backpack/echest or disabled for next load", nameClean);
			return;
		}

		try {
			T screenHandler = type.create(id, player.getInventory());
			Bomboaddons.LOGGER.info("[StorageOverlay] Created handler: {} (isChestMenu={})", 
					(screenHandler != null ? screenHandler.getClass().getName() : "null"), 
					(screenHandler instanceof ChestMenu));

			if (screenHandler instanceof ChestMenu containerScreenHandler) {
				int height = client.getWindow().getGuiScaledHeight() - (client.getWindow().getGuiScaledHeight() / 5);
				boolean isBackpack = BackpackPreview.getStorageIndexFromTitle(nameClean) != -1;
				Bomboaddons.LOGGER.info("[StorageOverlay] Opening StorageOverlayScreen: height={}, isBackpack={}, windowScaledH={}", 
						height, isBackpack, client.getWindow().getGuiScaledHeight());

				StorageOverlayScreenHandler storageOverlayScreenHandler = new StorageOverlayScreenHandler(containerScreenHandler, isBackpack, height, player.getInventory());
				client.player.containerMenu = storageOverlayScreenHandler;
				client.setScreenAndShow(new StorageOverlayScreen(storageOverlayScreenHandler, containerScreenHandler, name, player.getInventory(), height));
				ci.cancel();
				Bomboaddons.LOGGER.info("[StorageOverlay] Successfully launched StorageOverlayScreen and cancelled vanilla screen!");
			} else {
				Bomboaddons.LOGGER.warn("[StorageOverlay] Handler is not ChestMenu! Cannot wrap menu type {}", type);
			}
		} catch (Throwable t) {
			Bomboaddons.LOGGER.error("[StorageOverlay] Exception while initializing StorageOverlayScreen: " + t.getMessage(), t);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("§c[StorageOverlay Error] " + t.getClass().getSimpleName() + ": " + t.getMessage()));
			}
		}
	}
}
