package dev.vy.betterpv.client.gui.collections.page;

import dev.vy.betterpv.client.data.CollectionSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.collections.CollectionsUi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static dev.vy.betterpv.client.gui.collections.CollectionsUi.*;

/** Minions grid + tier checklist subpage. */
public final class MinionsPage {
	private int selectedMinion;
	private int minionScroll;
	private int minionMaxScroll;
	private int tierScroll;
	private int tierMaxScroll;
	private int minionTop;
	private int minionH;
	private int tierListTop;
	private int tierListH;
	private final List<SlotHit> minionHits = new ArrayList<>();

	public void reset(CollectionSnapshot snapshot) {
		this.selectedMinion = snapshot == null || snapshot.minions().isEmpty() ? -1 : 0;
		this.minionScroll = 0;
		this.tierScroll = 0;
	}

	public void render(
		CollectionSnapshot snapshot,
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		this.minionHits.clear();
		renderMinions(snapshot, g, font, x, y, w, h, mouseX, mouseY);
	}

	public boolean mouseClicked(double mx, double my) {
		for (SlotHit hit : this.minionHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				this.selectedMinion = hit.index;
				this.tierScroll = 0;
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		int step = SLOT + SLOT_GAP;
		if (mouseY >= this.minionTop && mouseY < this.minionTop + this.minionH) {
			return CollectionsUi.scrollBy(scrollY, this.minionScroll, this.minionMaxScroll, step, v -> this.minionScroll = v);
		}
		if (mouseY >= this.tierListTop && mouseY < this.tierListTop + this.tierListH) {
			return CollectionsUi.scrollBy(scrollY, this.tierScroll, this.tierMaxScroll, 12, v -> this.tierScroll = v);
		}
		return false;
	}

	private void renderMinions(
		CollectionSnapshot snapshot,
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		int rightW = Math.max(96, Math.min(118, (int) Math.round(w * 0.24)));
		int leftW = w - rightW - GAP;
		int rightX = x + leftW + GAP;

		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);

		List<CollectionSnapshot.MinionEntry> minions = snapshot.minions();
		PvDraw.text(g, font, Component.translatable("betterpv.collections.minions").getString(), x + PAD, y + PAD, PvDraw.COLOR_TEXT);
		this.minionTop = y + PAD + font.lineHeight + 4;
		this.minionH = Math.max(20, y + h - PAD - this.minionTop);

		if (minions.isEmpty()) {
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.collections.minions_empty").getString(),
				x + leftW / 2, y + h / 2, PvDraw.COLOR_MUTED
			);
			this.minionMaxScroll = 0;
			this.selectedMinion = -1;
			drawMinionDetail(g, font, null, rightX, y, rightW, h);
			return;
		}
		this.selectedMinion = Math.max(0, Math.min(this.selectedMinion, minions.size() - 1));

		int listX = x + PAD;
		int listW = leftW - PAD * 2;
		int count = minions.size();
		int slot = SLOT;
		int minGap = 5;
		int maxGap = 14;

		// One row shorter than a full-height pack → more columns, wider grid.
		int fitRows = Math.max(1, (this.minionH + minGap) / (slot + minGap));
		int idealRows = Math.max(1, fitRows - 1);
		int maxCols = Math.max(1, (listW + minGap) / (slot + minGap));
		int cols = Math.max(1, Math.min(maxCols, (count + idealRows - 1) / idealRows));
		int rows = (count + cols - 1) / cols;

		// Widen columns if the tall layout would clip below the panel.
		while (cols < maxCols) {
			int trialGapY = rows <= 1 ? 0 : Math.max(minGap, Math.min(maxGap, (this.minionH - rows * slot) / Math.max(1, rows - 1)));
			int trialH = rows * slot + Math.max(0, rows - 1) * trialGapY;
			if (trialH <= this.minionH) {
				break;
			}
			cols++;
			rows = (count + cols - 1) / cols;
		}

		int gapX = cols <= 1 ? 0 : Math.max(minGap, Math.min(maxGap, (listW - cols * slot) / Math.max(1, cols - 1)));
		int gapY = rows <= 1 ? 0 : Math.max(minGap, Math.min(maxGap, (this.minionH - rows * slot) / Math.max(1, rows - 1)));

		int gridW = cols * slot + Math.max(0, cols - 1) * gapX;
		int contentH = rows * slot + Math.max(0, rows - 1) * gapY;
		int startX = listX + Math.max(0, (listW - gridW) / 2);
		int startY = this.minionTop + Math.max(0, (this.minionH - contentH) / 2);
		this.minionMaxScroll = Math.max(0, contentH - this.minionH);
		this.minionScroll = Math.max(0, Math.min(this.minionScroll, this.minionMaxScroll));

		g.enableScissor(listX, this.minionTop, listX + listW, this.minionTop + this.minionH);
		for (int i = 0; i < count; i++) {
			int col = i % cols;
			int row = i / cols;
			int sx = startX + col * (slot + gapX);
			int sy = startY + row * (slot + gapY) - this.minionScroll;
			if (sy + slot < this.minionTop || sy > this.minionTop + this.minionH) {
				continue;
			}
			CollectionSnapshot.MinionEntry minion = minions.get(i);
			boolean selected = i == this.selectedMinion;
			boolean hover = mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + slot;
			PvDraw.fill(g, sx, sy, slot, slot, selected ? 0xFF2A3A55 : hover ? 0xFF222230 : 0xFF101018);
			g.outline(sx, sy, slot, slot, selected ? PvDraw.COLOR_ACCENT : hover ? 0xFF4A4A5A : 0xFF2A2A35);
			CollectionsUi.drawIcon(g, minion.iconId(), sx + (slot - ITEM_ICON) / 2, sy + (slot - ITEM_ICON) / 2 - 1);
			boolean maxed = minion.maxCrafted() >= minion.tierCap() && minion.tierCap() > 0;
			PvDraw.textRight(
				g, font,
				String.valueOf(minion.maxCrafted()),
				sx + slot - 1, sy + slot - font.lineHeight + 1,
				maxed ? PvDraw.COLOR_GOLD : MINION_LOCKED
			);
			this.minionHits.add(new SlotHit(sx, sy, slot, slot, i));
		}
		g.disableScissor();

		drawMinionDetail(g, font, minions.get(this.selectedMinion), rightX, y, rightW, h);
	}

	private void drawMinionDetail(
		GuiGraphicsExtractor g,
		Font font,
		CollectionSnapshot.MinionEntry minion,
		int x,
		int y,
		int w,
		int h
	) {
		int cx = x + PAD;
		int cy = y + PAD;
		int contentW = w - PAD * 2;
		if (minion == null) {
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.collections.select_minion").getString(),
				x + w / 2, y + h / 2, PvDraw.COLOR_MUTED
			);
			this.tierListTop = cy;
			this.tierListH = 0;
			this.tierMaxScroll = 0;
			return;
		}

		CollectionsUi.drawIcon(g, minion.iconId(), cx, cy);
		PvDraw.text(
			g, font,
			CollectionsUi.trim(font, minion.displayName(), contentW - ITEM_ICON - 6),
			cx + ITEM_ICON + 4,
			cy + (ITEM_ICON - font.lineHeight) / 2,
			PvDraw.COLOR_TEXT
		);
		cy += ITEM_ICON + 4;
		boolean maxed = minion.maxCrafted() >= minion.tierCap() && minion.tierCap() > 0;
		PvDraw.text(
			g, font,
			"T" + minion.maxCrafted() + " / " + minion.tierCap(),
			cx, cy,
			maxed ? PvDraw.COLOR_GOLD : MINION_LOCKED
		);
		cy += font.lineHeight + 2;
		PvDraw.text(
			g, font,
			Component.translatable("betterpv.collections.minions_shared").getString(),
			cx, cy,
			PvDraw.COLOR_MUTED
		);
		cy += font.lineHeight + 3;

		PvDraw.text(g, font, Component.translatable("betterpv.collections.tiers").getString(), cx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 2;
		this.tierListTop = cy;
		this.tierListH = Math.max(20, y + h - PAD - this.tierListTop);

		int tiers = Math.max(1, minion.tierCap());
		int naturalRow = font.lineHeight + 1;
		int fitRow = Math.max(8, this.tierListH / tiers);
		int rowH = Math.min(naturalRow, fitRow);
		float tierScale = rowH < font.lineHeight ? rowH / (float) font.lineHeight : 1.0f;
		this.tierMaxScroll = Math.max(0, tiers * rowH - this.tierListH);
		this.tierScroll = Math.max(0, Math.min(this.tierScroll, this.tierMaxScroll));

		g.enableScissor(cx, this.tierListTop, cx + contentW, this.tierListTop + this.tierListH);
		int rowY = this.tierListTop - this.tierScroll;
		for (int t = 1; t <= tiers; t++) {
			if (rowY + rowH >= this.tierListTop && rowY <= this.tierListTop + this.tierListH) {
				boolean crafted = minion.crafted(t);
				int color = crafted ? MINION_UNLOCKED : MINION_LOCKED;
				String label = "T" + t;
				if (tierScale >= 0.99f) {
					PvDraw.text(g, font, label, cx, rowY, color);
					Component mark = PvDraw.styled(crafted ? MINION_TICK : MINION_CROSS, color, true);
					PvDraw.text(g, font, mark, cx + contentW - PvDraw.width(font, mark), rowY);
				} else {
					PvDraw.textScaled(g, font, label, cx, rowY, color, tierScale);
					String mark = crafted ? MINION_TICK : MINION_CROSS;
					int tw = Math.max(1, Math.round(PvDraw.widthBold(font, mark) * tierScale));
					PvDraw.textScaled(g, font, mark, cx + contentW - tw, rowY, color, tierScale);
				}
			}
			rowY += rowH;
		}
		g.disableScissor();
	}

	private record SlotHit(int x, int y, int w, int h, int index) {
	}
}
