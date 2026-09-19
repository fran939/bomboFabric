package dev.vy.betterpv.client.gui.crimson.page;

import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.DISABLED;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.ENABLED;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.GAP;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.ITEM_SLOT_BG;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.ITEM_SLOT_BORDER;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.KUUDRA_COLOR;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.PAD;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.SEP_GAP;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.SLOT;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.SLOT_GAP;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.STAT_ROW;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.drawHover;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.sectionSeparator;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.statLine;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.stripFormatting;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.trim;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.visible;

import dev.vy.betterpv.client.data.CrimsonKuudraCard;
import dev.vy.betterpv.client.data.CrimsonSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.crimson.CrimsonUi.HoverZone;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Crimson Kuudra subtab: tier stats + scrollable Kuudra card. */
public final class KuudraPage {
	private final List<HoverZone> zones = new ArrayList<>();

	private int kuudraScroll;
	private int kuudraMaxScroll;
	private int kuudraX;
	private int kuudraY;
	private int kuudraW;
	private int kuudraH;

	public void resetScroll() {
		this.kuudraScroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.kuudraMaxScroll > 0
			&& mouseX >= this.kuudraX && mouseX < this.kuudraX + this.kuudraW
			&& mouseY >= this.kuudraY && mouseY < this.kuudraY + this.kuudraH) {
			int delta = scrollY > 0 ? -14 : 14;
			int next = Math.max(0, Math.min(this.kuudraMaxScroll, this.kuudraScroll + delta));
			if (next != this.kuudraScroll) {
				this.kuudraScroll = next;
				return true;
			}
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		int leftW = Math.max(140, w * 40 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);
		drawKuudraTiers(g, font, snapshot, x, y, leftW, h);
		drawKuudraCard(g, font, snapshot, x + leftW + GAP, y, rightW, h, mouseX, mouseY);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		drawHover(g, font, this.zones, mouseX, mouseY, screenW, screenH);
	}

	private void drawKuudraTiers(GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		PvDraw.text(g, font, "Kuudra", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;
		ly = sectionSeparator(g, font, x, ly, w);

		for (CrimsonSnapshot.KuudraTier tier : CrimsonSnapshot.KuudraTier.values()) {
			if (ly + STAT_ROW * 2 + 6 > y + h - PAD) {
				break;
			}
			CrimsonSnapshot.KuudraTierStats stats = snapshot.kuudra(tier);
			PvDraw.text(g, font, tier.label() + ":", lx, ly, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, FormatUtil.commas(stats.completions()),
				lx + lw, ly, KUUDRA_COLOR);
			ly += STAT_ROW;
			PvDraw.text(g, font, "Highest wave:", lx, ly, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font,
				stats.highestWave() > 0 ? String.valueOf(stats.highestWave()) : "-",
				lx + lw, ly, PvDraw.COLOR_TEXT);
			ly += STAT_ROW + 6;
		}
	}

	private void drawKuudraCard(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		CrimsonKuudraCard card = snapshot.kuudraCard();
		int lx = x + PAD;
		int contentTop = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		int contentH = measureKuudraCard(font, card);
		this.kuudraX = x;
		this.kuudraY = contentTop;
		this.kuudraW = w;
		this.kuudraH = Math.max(0, bottom - contentTop);
		this.kuudraMaxScroll = Math.max(0, contentH - this.kuudraH);
		this.kuudraScroll = Math.min(this.kuudraScroll, this.kuudraMaxScroll);

		g.enableScissor(lx, this.kuudraY, lx + lw, this.kuudraY + this.kuudraH);
		int ly = contentTop - this.kuudraScroll;
		drawKuudraCardBody(g, font, card, lx, ly, lw, mx, my, this.kuudraY, this.kuudraY + this.kuudraH);
		g.disableScissor();
	}

	private int measureKuudraCard(Font font, CrimsonKuudraCard card) {
		int ly = 0;
		ly += font.lineHeight + 2;
		ly += STAT_ROW;
		ly += STAT_ROW;
		ly += STAT_ROW;
		ly += SEP_GAP;
		ly += font.lineHeight + 3;
		ly += card.importantItems().size() * STAT_ROW;
		ly += SEP_GAP;
		ly += font.lineHeight + 3;
		ly += SLOT + 4;
		return ly;
	}

	private void drawKuudraCardBody(
		GuiGraphicsExtractor g, Font font, CrimsonKuudraCard card,
		int lx, int ly, int lw, int mx, int my, int clipTop, int clipBottom
	) {
		String scoreText = FormatUtil.shortXp(card.kuudraScore())
			+ " [Level: " + FormatUtil.commas(card.kuudraLevel()) + "]";
		PvDraw.text(g, font, "Kuudra Score:", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, scoreText, lx + lw, ly, KUUDRA_COLOR);
		if (visible(ly, font.lineHeight, clipTop, clipBottom)) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			for (String line : card.scoreHoverLines()) {
				tip.add(PvTooltip.Line.of(line, PvDraw.COLOR_MUTED));
			}
			this.zones.add(HoverZone.of(lx, Math.max(ly, clipTop), lw,
				Math.min(ly + font.lineHeight, clipBottom) - Math.max(ly, clipTop), tip));
		}
		ly += font.lineHeight + 2;

		String mp = FormatUtil.commas(card.magicalPower())
			+ " [" + card.selectedPowerLabel() + "]";
		ly = statLine(g, font, "Magical Power", trim(font, mp, lw - font.width("Magical Power") - 8),
			lx, ly, lw, PvDraw.COLOR_ACCENT);

		String skills = "Cata " + card.cataLevel()
			+ ", Combat " + card.combatLevel()
			+ ", Foraging " + card.foragingLevel()
			+ ", SB " + card.skyBlockLevel();
		ly = statLine(g, font, "Skills", trim(font, skills, lw - font.width("Skills") - 8),
			lx, ly, lw, PvDraw.COLOR_TEXT);

		String vanq = String.format(Locale.ROOT, "%.3f%%", card.vanquisherChancePct());
		int vanqY = ly;
		ly = statLine(g, font, "Vanquisher Chance", vanq, lx, ly, lw, ENABLED);
		if (visible(vanqY, STAT_ROW, clipTop, clipBottom)) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of("Vanquisher Chance", PvDraw.COLOR_TEXT));
			for (String line : card.vanquisherHover()) {
				tip.add(PvTooltip.Line.of(line, PvDraw.COLOR_MUTED));
			}
			this.zones.add(HoverZone.of(lx, Math.max(vanqY, clipTop), lw,
				Math.min(vanqY + STAT_ROW, clipBottom) - Math.max(vanqY, clipTop), tip));
		}

		ly = sectionSeparator(g, font, lx - PAD, ly, lw + PAD * 2);
		PvDraw.text(g, font, "Important Items", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;

		for (CrimsonKuudraCard.ImportantItem item : card.importantItems()) {
			if (visible(ly, STAT_ROW, clipTop, clipBottom)) {
				int color = item.owned() ? ENABLED : DISABLED;
				String mark = item.owned() ? "✔ " : "✘ ";
				String detail = item.details().isEmpty() ? "" : " [" + String.join(" · ", item.details()) + "]";
				String line = mark + item.label() + detail;
				PvDraw.text(g, font, trim(font, line, lw), lx, ly, color);
				addLoreHoverZone(lx, Math.max(ly, clipTop), lw,
					Math.min(ly + STAT_ROW, clipBottom) - Math.max(ly, clipTop),
					item.label(), item.displayName(), item.owned(), item.details(), item.lore());
			}
			ly += STAT_ROW;
		}

		ly = sectionSeparator(g, font, lx - PAD, ly, lw + PAD * 2);
		int colGap = 10;
		int colW = Math.max(SLOT, (lw - colGap) / 2);
		int rightColX = lx + colW + colGap;
		PvDraw.text(g, font, "Mage Armor", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, "Archer Armor", rightColX, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		drawArmorRow(g, font, card.mageArmor(), lx, ly, mx, my, clipTop, clipBottom);
		drawArmorRow(g, font, card.archerArmor(), rightColX, ly, mx, my, clipTop, clipBottom);
	}

	private int drawArmorRow(
		GuiGraphicsExtractor g, Font font, List<CrimsonKuudraCard.ArmorPiece> pieces,
		int lx, int ly, int mx, int my, int clipTop, int clipBottom
	) {
		int i = 0;
		for (CrimsonKuudraCard.ArmorPiece piece : pieces) {
			int bx = lx + i * (SLOT + SLOT_GAP);
			int by = ly;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				&& my >= clipTop && my < clipBottom;
			if (visible(by, SLOT, clipTop, clipBottom)) {
				PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);
				g.outline(bx, by, SLOT, SLOT,
					hovered ? PvDraw.COLOR_ACCENT : (piece.owned() ? ITEM_SLOT_BORDER : DISABLED));
				ItemStack icon = armorIconStack(piece);
				int iconPad = Math.max(0, (SLOT - 16) / 2);
				if (!icon.isEmpty()) {
					g.item(icon, bx + iconPad, by + iconPad);
					if (!piece.owned()) {
						PvDraw.fill(g, bx + 1, by + 1, SLOT - 2, SLOT - 2, 0x88000000);
					}
				} else {
					g.item(new ItemStack(Items.BARRIER), bx + iconPad, by + iconPad);
				}
				if (piece.owned() && piece.dyeColor() != null) {
					PvDraw.fill(g, bx + SLOT - 4, by + 1, 3, 3, 0xFF000000 | (piece.dyeColor() & 0xFFFFFF));
				}
				int y0 = Math.max(by, clipTop);
				int y1 = Math.min(by + SLOT, clipBottom);
				if (y1 > y0) {
					addLoreHoverZone(bx, y0, SLOT, y1 - y0,
						piece.label().isBlank() ? piece.slot() : piece.label(),
						piece.displayName(),
						piece.owned(), piece.details(), piece.lore());
				}
			}
			i++;
		}
		return ly + SLOT + 4;
	}

	private static ItemStack armorIconStack(CrimsonKuudraCard.ArmorPiece piece) {
		ItemStack icon = SkyBlockItemFactory.iconStack(piece.iconId());
		if (icon.isEmpty()) {
			return icon;
		}
		ItemStack copy = icon.copy();
		if (piece.dyeColor() != null) {
			var item = copy.getItem();
			if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE
				|| item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS
				|| item == Items.LEATHER_HORSE_ARMOR) {
				copy.set(DataComponents.DYED_COLOR,
					new net.minecraft.world.item.component.DyedItemColor(piece.dyeColor() & 0xFFFFFF));
			}
		}
		return copy;
	}

	/** Full item lore (when present) via legacy-formatted components, else a plain fallback tip. */
	private void addLoreHoverZone(
		int x, int y, int w, int h,
		String label, String displayName, boolean owned, List<String> details, List<String> lore
	) {
		if (lore != null && !lore.isEmpty()) {
			List<Component> tip = new ArrayList<>();
			String title = displayName != null && !displayName.isBlank() ? displayName : label;
			tip.add(SkyBlockItemFactory.legacyLine(title == null ? "" : title));
			String titlePlain = stripFormatting(title);
			int start = 0;
			if (!lore.isEmpty()) {
				String firstPlain = stripFormatting(lore.get(0));
				if (!titlePlain.isBlank() && !firstPlain.isBlank()
					&& (firstPlain.equalsIgnoreCase(titlePlain)
					|| titlePlain.regionMatches(true, 0, firstPlain, 0, Math.min(titlePlain.length(), firstPlain.length()))
					|| firstPlain.regionMatches(true, 0, titlePlain, 0, Math.min(titlePlain.length(), firstPlain.length())))) {
					start = 1;
				}
			}
			for (int i = start; i < lore.size(); i++) {
				String loreLine = lore.get(i);
				tip.add(loreLine == null || loreLine.isBlank()
					? Component.empty()
					: SkyBlockItemFactory.legacyLine(loreLine));
			}
			this.zones.add(HoverZone.ofComponents(x, y, w, h, tip));
			return;
		}
		List<PvTooltip.Line> tip = new ArrayList<>();
		String title = displayName != null && !displayName.isBlank() ? stripFormatting(displayName) : label;
		tip.add(PvTooltip.Line.of(title == null ? "" : title, PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.of(owned ? "Owned" : "Missing", owned ? ENABLED : DISABLED));
		for (String d : details) {
			tip.add(PvTooltip.Line.of(d, PvDraw.COLOR_MUTED));
		}
		this.zones.add(HoverZone.of(x, y, w, h, tip));
	}
}
