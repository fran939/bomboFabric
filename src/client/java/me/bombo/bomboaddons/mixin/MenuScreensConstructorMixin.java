package me.bombo.bomboaddons.mixin;

import net.minecraft.client.gui.screens.MenuScreens;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MenuScreens.class)
public class MenuScreensConstructorMixin {
	// Storage overlay interception is handled cleanly in ClientPacketListenerMixin#onHandleOpenScreen
}
