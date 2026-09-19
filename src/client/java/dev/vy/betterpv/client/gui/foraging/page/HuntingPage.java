package dev.vy.betterpv.client.gui.foraging.page;

import static dev.vy.betterpv.client.gui.foraging.ForagingUi.BAR_AFTER;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.BAR_HUNT;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.DISABLED;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ENABLED;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.PAD;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT_GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.STAT_ROW;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.foraging.ForagingUi;
import dev.vy.betterpv.client.gui.foraging.ForagingUi.HoverZone;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Hunting subtab (shards left + toolkit right). */
public final class HuntingPage {
	private final List<HoverZone> zones = new ArrayList<>();

	public void reset() {
		this.zones.clear();
	}

	public void render(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.zones.clear();
		int rightW = Math.max(180, w * 45 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		drawShardsLeft(g, font, snapshot, x, y, leftW, h, mx, my);
		drawToolkitRight(g, font, snapshot, x + leftW + GAP, y, rightW, h, mx, my);
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		ForagingUi.drawHover(g, font, this.zones, mx, my, screenW, screenH);
	}

	private void drawShardsLeft(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		ly = ForagingUi.drawBar(g, font, "Hunting", String.valueOf(snapshot.huntingLevel()),
			snapshot.huntingFill(), snapshot.huntingMaxed(), BAR_HUNT,
			snapshot.huntingHover(), lx, ly, lw, this.zones) + BAR_AFTER + 2;

		if (snapshot.uniqueShards() > 0L) {
			ly = ForagingUi.statLine(g, font, "Unique shards", FormatUtil.commas(snapshot.uniqueShards()),
				lx, ly, lw, PvDraw.COLOR_ACCENT);
		}
		ly = ForagingUi.statLine(g, font, "Fused", FormatUtil.commas(snapshot.fusedShards()),
			lx, ly, lw, PvDraw.COLOR_GOLD);
		if (snapshot.peltCount() > 0L) {
			ly = ForagingUi.statLine(g, font, "Pelts", FormatUtil.commas(snapshot.peltCount()),
				lx, ly, lw, PvDraw.COLOR_TEXT);
		}
		ly = ForagingUi.sectionSeparator(g, font, x, ly, w);

		if (!snapshot.huntStats().isEmpty()) {
			PvDraw.text(g, font, "Shard Drops", lx, ly, PvDraw.COLOR_MUTED);
			ly += font.lineHeight + 8;
			for (Map.Entry<String, Long> e : snapshot.huntStats().entrySet()) {
				if (ly + STAT_ROW > bottom) {
					break;
				}
				String label = ForagingUi.pretty(e.getKey().replace("shard_", "").replace("_hunts", ""));
				ly = ForagingUi.statLine(g, font, label, FormatUtil.commas(e.getValue()),
					lx, ly, lw, PvDraw.COLOR_TEXT) + 2;
			}
		}
	}

	private void drawToolkitRight(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Hunting toolkit", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		ry = ForagingUi.statLine(g, font, "Unlocked", snapshot.toolkitUnlocked() ? "Yes" : "No",
			rx, ry, rw, snapshot.toolkitUnlocked() ? ENABLED : DISABLED) + 3;

		List<ForagingSnapshot.ToolkitSlot> slots = snapshot.toolkitSlots();
		if (slots.isEmpty()) {
			PvDraw.text(g, font, "No slots", rx, ry, PvDraw.COLOR_MUTED);
			return;
		}

		String lastGroup = "";
		int cols = Math.max(3, Math.min(5, (rw + SLOT_GAP) / (SLOT + SLOT_GAP)));
		int col = 0;
		int rowY = ry;
		for (ForagingSnapshot.ToolkitSlot slot : slots) {
			if (!slot.group().equals(lastGroup)) {
				if (!lastGroup.isEmpty()) {
					rowY += SLOT + SLOT_GAP + 2;
					col = 0;
				}
				if (rowY + font.lineHeight + SLOT > bottom) {
					break;
				}
				PvDraw.text(g, font, ForagingUi.pretty(slot.group()), rx, rowY, PvDraw.COLOR_MUTED);
				rowY += font.lineHeight + 2;
				lastGroup = slot.group();
			}
			if (col >= cols) {
				col = 0;
				rowY += SLOT + SLOT_GAP;
			}
			if (rowY + SLOT > bottom) {
				break;
			}
			int bx = rx + col * (SLOT + SLOT_GAP);
			int by = rowY;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT;
			PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);
			g.outline(bx, by, SLOT, SLOT, slot.inUse()
				? PvDraw.COLOR_GOLD
				: (hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER));
			InventorySnapshot.Slot item = slot.item();
			boolean filled = item != null && !item.isEmpty();
			ItemStack stack = ItemStack.EMPTY;
			if (filled) {
				stack = SkyBlockItemFactory.toStack(item);
				if (stack == null || stack.isEmpty()) {
					stack = SkyBlockItemFactory.iconStack(item.id());
				}
				if (stack == null || stack.isEmpty()) {
					stack = new ItemStack(Items.TRIPWIRE_HOOK);
				}
				g.item(stack, bx + 1, by + 1);
			}
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(ForagingUi.pretty(slot.group()) + " #" + (slot.index() + 1), PvDraw.COLOR_TEXT));
			if (filled) {
				String name = item.displayName() == null || item.displayName().isBlank()
					? ForagingUi.pretty(item.id()) : item.displayName();
				tip.add(PvTooltip.Line.of(name, PvDraw.COLOR_ACCENT));
			} else {
				tip.add(PvTooltip.Line.of("Empty", PvDraw.COLOR_MUTED));
			}
			if (slot.inUse()) {
				tip.add(PvTooltip.Line.of("In use", PvDraw.COLOR_GOLD));
			}
			this.zones.add(HoverZone.of(bx, by, SLOT, SLOT, tip));
			col++;
		}
	}
}
