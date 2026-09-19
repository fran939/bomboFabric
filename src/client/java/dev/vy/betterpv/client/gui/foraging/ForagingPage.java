package dev.vy.betterpv.client.gui.foraging;

import dev.vy.betterpv.client.data.ForagingHotfData;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.gui.foraging.page.AttributeShardsPage;
import dev.vy.betterpv.client.gui.foraging.page.ForagingOverviewPage;
import dev.vy.betterpv.client.gui.foraging.page.HotfPage;
import dev.vy.betterpv.client.gui.foraging.page.HuntingPage;
import dev.vy.betterpv.client.gui.foraging.page.SafariPage;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Foraging: Overview / HOTF / Hunting / Safari / Attribute Shards. */
public final class ForagingPage {
	private ForagingSnapshot snapshot = ForagingSnapshot.empty();
	private PvSubTab lastSub;

	private final ForagingOverviewPage overview = new ForagingOverviewPage();
	private final HotfPage hotf = new HotfPage();
	private final HuntingPage hunting = new HuntingPage();
	private final SafariPage safari = new SafariPage();
	private final AttributeShardsPage attributeShards = new AttributeShardsPage();

	public void apply(ForagingSnapshot snapshot) {
		this.snapshot = snapshot == null ? ForagingSnapshot.empty() : snapshot;
		this.overview.reset();
		this.hotf.reset();
		this.hunting.reset();
		this.safari.reset();
		this.attributeShards.reset();
		ForagingHotfData.ensureLoaded();
	}

	public ForagingSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.lastSub != PvSubTab.FORAGING_OVERVIEW) {
			return false;
		}
		return this.overview.mouseClicked(mx, my);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		PvSubTab mode = sub == null ? PvSubTab.FORAGING_OVERVIEW : sub;
		return switch (mode) {
			case FORAGING_ATTRIBUTE_SHARDS -> this.attributeShards.mouseScrolled(mouseX, mouseY, scrollY);
			case FORAGING_SAFARI -> this.safari.mouseScrolled(mouseX, mouseY, scrollY);
			default -> false;
		};
	}

	public void render(
		GuiGraphicsExtractor g, Font font, PvSubTab sub,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		PvSubTab mode = sub == null ? PvSubTab.FORAGING_OVERVIEW : sub;
		if (mode != this.lastSub) {
			this.lastSub = mode;
			this.attributeShards.resetScroll();
			this.safari.resetScroll();
			if (mode != PvSubTab.FORAGING_OVERVIEW) {
				this.overview.onLeave();
			}
		}

		switch (mode) {
			case FORAGING_HOTF -> this.hotf.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY);
			case FORAGING_HUNTING -> this.hunting.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY);
			case FORAGING_SAFARI -> this.safari.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY);
			case FORAGING_ATTRIBUTE_SHARDS ->
				this.attributeShards.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY);
			default -> this.overview.render(g, font, this.snapshot, x, y, w, h, mouseX, mouseY);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		PvSubTab mode = this.lastSub == null ? PvSubTab.FORAGING_OVERVIEW : this.lastSub;
		switch (mode) {
			case FORAGING_HOTF -> this.hotf.drawHover(g, font, mouseX, mouseY, screenW, screenH);
			case FORAGING_HUNTING -> this.hunting.drawHover(g, font, mouseX, mouseY, screenW, screenH);
			case FORAGING_SAFARI -> this.safari.drawHover(g, font, mouseX, mouseY, screenW, screenH);
			case FORAGING_ATTRIBUTE_SHARDS ->
				this.attributeShards.drawHover(g, font, mouseX, mouseY, screenW, screenH);
			default -> this.overview.drawHover(g, font, mouseX, mouseY, screenW, screenH);
		}
	}
}
