package dev.vy.betterpv.client.gui.page;

import dev.vy.betterpv.client.gui.nav.PvSubTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Minimal page contract for Profile Viewer body routing.
 * Pages with extra params (Home open-scale, Museum sort, inventory split) stay special-cased on the screen.
 */
public interface ProfilePage {
	void render(
		GuiGraphicsExtractor g,
		Font font,
		PvSubTab sub,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	);

	default boolean mouseClicked(double mx, double my, PvSubTab sub) {
		return false;
	}

	default boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		return false;
	}
}
