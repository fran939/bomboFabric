package dev.vy.betterpv.client.gui.crimson;

import dev.vy.betterpv.client.data.CrimsonSnapshot;
import dev.vy.betterpv.client.gui.crimson.page.AbiphonePage;
import dev.vy.betterpv.client.gui.crimson.page.CrimsonOverviewPage;
import dev.vy.betterpv.client.gui.crimson.page.KuudraPage;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Crimson coordinator: Overview / Kuudra / Abiphone. */
public final class CrimsonPage {
	private CrimsonSnapshot snapshot = CrimsonSnapshot.empty();
	private PvSubTab activeSub = PvSubTab.CRIMSON_OVERVIEW;

	private final CrimsonOverviewPage overview = new CrimsonOverviewPage();
	private final KuudraPage kuudra = new KuudraPage();
	private final AbiphonePage abiphone = new AbiphonePage();

	public void apply(CrimsonSnapshot snapshot) {
		this.snapshot = snapshot == null ? CrimsonSnapshot.empty() : snapshot;
		this.kuudra.resetScroll();
		this.abiphone.resetScroll();
	}

	public CrimsonSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.activeSub == PvSubTab.CRIMSON_OVERVIEW) {
			return this.overview.mouseClicked(mx, my);
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		if (sub == PvSubTab.CRIMSON_ABIPHONE) {
			return this.abiphone.mouseScrolled(mouseX, mouseY, scrollY);
		}
		if (sub == PvSubTab.CRIMSON_KUUDRA) {
			return this.kuudra.mouseScrolled(mouseX, mouseY, scrollY);
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.activeSub = sub == null ? PvSubTab.CRIMSON_OVERVIEW : sub;
		if (this.activeSub == PvSubTab.CRIMSON_KUUDRA) {
			this.kuudra.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY, screenW, screenH);
		} else if (this.activeSub == PvSubTab.CRIMSON_ABIPHONE) {
			this.abiphone.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY, screenW, screenH);
		} else {
			this.overview.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY, screenW, screenH);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (this.activeSub == PvSubTab.CRIMSON_KUUDRA) {
			this.kuudra.renderTooltip(g, font, mouseX, mouseY, screenW, screenH);
		} else if (this.activeSub == PvSubTab.CRIMSON_ABIPHONE) {
			this.abiphone.renderTooltip(g, font, mouseX, mouseY, screenW, screenH);
		} else {
			this.overview.renderTooltip(g, font, mouseX, mouseY, screenW, screenH);
		}
	}
}
