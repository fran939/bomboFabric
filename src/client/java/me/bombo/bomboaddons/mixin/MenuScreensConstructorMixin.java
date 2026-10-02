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
		int storageIdx = BackpackPreview.getStorageIndexFromTitle(nameClean);

		boolean isStorageRelated = nameClean.contains("storage") || nameClean.contains("almacenamiento")
				|| nameClean.contains("ender chest") || nameClean.contains("cofre de ender")
				|| nameClean.contains("backpack") || nameClean.contains("mochila")
				|| storageIdx != -1;

		Bomboaddons.LOGGER.info("[StorageOverlay] Intercepted menu create: id={}, title='{}' (clean='{}'), configEnabled={}, eligible={}, storageIdx={}", 
				id, rawTitle, nameClean, configOn, isEligible, storageIdx);

		if (isStorageRelated && player != null) {
			String statusText = !configOn ? "§cDisabled in /b config" : (isEligible ? "§aLaunching Overlay" : "§eBypassed/Ineligible");
			player.sendSystemMessage(Component.literal(String.format(
					"§8[§bStorageOverlay Debug§8] §7Title: '§f%s§7' | Index: §e%d §7| Status: %s",
					rawTitle, storageIdx, statusText)));
		}

		if (!configOn || !isEligible) {
			return;
		}

		try {
			ChestMenu containerScreenHandler = null;
			if (player.containerMenu instanceof ChestMenu cm && cm.containerId == id) {
				containerScreenHandler = cm;
			} else {
				T created = type.create(id, player.getInventory());
				if (created instanceof ChestMenu cm) {
					containerScreenHandler = cm;
				}
			}

			if (containerScreenHandler != null) {
				int height = client.getWindow().getGuiScaledHeight() - (client.getWindow().getGuiScaledHeight() / 5);
				boolean isBackpack = storageIdx != -1;
				Bomboaddons.LOGGER.info("[StorageOverlay] Opening StorageOverlayScreen: height={}, isBackpack={}, windowScaledH={}", 
						height, isBackpack, client.getWindow().getGuiScaledHeight());

				StorageOverlayScreenHandler storageOverlayScreenHandler = new StorageOverlayScreenHandler(containerScreenHandler, isBackpack, height, player.getInventory());
				client.player.containerMenu = storageOverlayScreenHandler;
				client.setScreenAndShow(new StorageOverlayScreen(storageOverlayScreenHandler, containerScreenHandler, name, player.getInventory(), height));
				ci.cancel();
				Bomboaddons.LOGGER.info("[StorageOverlay] Successfully launched StorageOverlayScreen and cancelled vanilla screen!");
			} else {
				Bomboaddons.LOGGER.warn("[StorageOverlay] Handler is not ChestMenu! Cannot wrap menu type {}", type);
				if (player != null) {
					player.sendSystemMessage(Component.literal("§8[§bStorageOverlay Debug§8] §cHandler is not ChestMenu for menu type: " + type));
				}
			}
		} catch (Throwable t) {
			Bomboaddons.LOGGER.error("[StorageOverlay] Exception while initializing StorageOverlayScreen: " + t.getMessage(), t);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("§c[StorageOverlay Error] " + t.getClass().getSimpleName() + ": " + t.getMessage()));
			}
		}
	}
}
