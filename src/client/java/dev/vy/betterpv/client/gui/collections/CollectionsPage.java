package dev.vy.betterpv.client.gui.collections;

import dev.vy.betterpv.client.data.BossCollections;
import dev.vy.betterpv.client.data.CollectionIds;
import dev.vy.betterpv.client.data.CollectionSnapshot;
import dev.vy.betterpv.client.gui.collections.page.CollectionsListPage;
import dev.vy.betterpv.client.gui.collections.page.MinionsPage;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.price.HypixelCollectionsCache;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Collections: full-width multi-column board; amount/tier/coop on hover.
 * Minions: icon grid + tier checklist.
 */
public final class CollectionsPage {
	private CollectionSnapshot snapshot = CollectionSnapshot.empty();
	private final CollectionsListPage list = new CollectionsListPage();
	private final MinionsPage minions = new MinionsPage();

	public void apply(CollectionSnapshot snapshot) {
		this.snapshot = snapshot == null ? CollectionSnapshot.empty() : snapshot;
		this.list.reset(this.snapshot);
		this.minions.reset(this.snapshot);
		prefetchIcons();
	}

	public void render(
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
	) {
		if (sub == PvSubTab.COLLECTIONS_MINIONS) {
			this.minions.render(this.snapshot, g, font, x, y, w, h, mouseX, mouseY);
		} else {
			this.list.render(this.snapshot, g, font, x, y, w, h, mouseX, mouseY, screenW, screenH);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		this.list.renderTooltip(g, font, mouseX, mouseY, screenW, screenH);
	}

	public boolean mouseClicked(double mx, double my, PvSubTab sub) {
		if (sub == PvSubTab.COLLECTIONS_MINIONS) {
			return this.minions.mouseClicked(mx, my);
		}
		return this.list.mouseClicked(mx, my);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		if (sub == PvSubTab.COLLECTIONS_MINIONS) {
			return this.minions.mouseScrolled(mouseX, mouseY, scrollY);
		}
		return this.list.mouseScrolled(mouseX, mouseY, scrollY);
	}

	private void prefetchIcons() {
		List<String> ids = new ArrayList<>();
		for (HypixelCollectionsCache.Category category : this.snapshot.categories()) {
			for (HypixelCollectionsCache.Item item : category.items()) {
				ids.add(CollectionsUi.resolveIconId(item.id()));
				ids.add(CollectionIds.iconId(item.id()));
				ids.addAll(CollectionIds.lookupKeys(item.id()));
			}
		}
		for (BossCollections.BossDef boss : BossCollections.bosses()) {
			ids.add(boss.iconId());
		}
		for (CollectionSnapshot.MinionEntry minion : this.snapshot.minions()) {
			ids.add(minion.iconId());
			ids.add(minion.id() + "_1");
		}
		SkyBlockItemFactory.prefetchIds(ids);
	}
}
