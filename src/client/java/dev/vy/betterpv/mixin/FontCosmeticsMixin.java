package dev.vy.betterpv.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Intentionally idle / not registered. Global Font prepareText rewrites were
 * bleaching HUD and chat colors. Name cosmetics stay on entity/tab/SkyHanni mixins.
 */
@Mixin(Font.class)
public abstract class FontCosmeticsMixin {
	@SuppressWarnings("unused")
	private static boolean betterpv$idle() {
		Minecraft client = Minecraft.getInstance();
		return client != null;
	}

	@SuppressWarnings("unused")
	private static String betterpv$passthrough(String text) {
		return text;
	}

	@SuppressWarnings("unused")
	private static FormattedCharSequence betterpv$passthrough(FormattedCharSequence text) {
		return text;
	}

	@SuppressWarnings("unused")
	private static FormattedText betterpv$passthrough(FormattedText text) {
		return text;
	}
}
