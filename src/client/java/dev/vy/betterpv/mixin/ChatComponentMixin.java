package dev.vy.betterpv.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Intentionally idle. Rewriting chat Components here bleached message colors
 * (false sender matches like {@code OdinClient:} and Hypixel click remaps).
 * Name clicks open BetterPV via {@link ScreenClickCommandMixin} + Hypixel
 * SocialOptions / viewprofile intercept in {@code ProfileViewerOpener}.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	// Kept so older refs / docs stay valid. Not registered in mixins.json.
	@SuppressWarnings("unused")
	private static Component betterpv$passthrough(Component component) {
		return component;
	}
}
