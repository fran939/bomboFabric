package dev.vy.betterpv.client.gui.crimson.page;

import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.DISABLED;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.DOJO_COLOR;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.ENABLED;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.GAP;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.ITEM_SLOT_BG;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.ITEM_SLOT_BORDER;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.PAD;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.drawHover;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.prettyRingtone;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.sectionSeparator;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.statLine;

import dev.vy.betterpv.client.data.AbiphoneNpcs;
import dev.vy.betterpv.client.data.CrimsonSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.crimson.CrimsonUi.HoverZone;
import dev.vy.betterpv.client.gui.inventories.SkyBlockIconRenderer;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Crimson Abiphone subtab: contact grid + stats. */
public final class AbiphonePage {
	private final List<HoverZone> zones = new ArrayList<>();

	private int abiphoneScroll;
	private int abiphoneMaxScroll;
	private int abiphoneX;
	private int abiphoneY;
	private int abiphoneW;
	private int abiphoneH;

	public void resetScroll() {
		this.abiphoneScroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.abiphoneMaxScroll > 0
			&& mouseX >= this.abiphoneX && mouseX < this.abiphoneX + this.abiphoneW
			&& mouseY >= this.abiphoneY && mouseY < this.abiphoneY + this.abiphoneH) {
			int delta = scrollY > 0 ? -14 : 14;
			int next = Math.max(0, Math.min(this.abiphoneMaxScroll, this.abiphoneScroll + delta));
			if (next != this.abiphoneScroll) {
				this.abiphoneScroll = next;
				return true;
			}
			return false;
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		int statsW = Math.max(150, Math.min(220, w * 32 / 100));
		int iconsW = w - statsW - GAP;
		PvDraw.innerPanel(g, x, y, iconsW, h);
		PvDraw.innerPanel(g, x + iconsW + GAP, y, statsW, h);

		drawAbiphoneStats(g, font, snapshot, x + iconsW + GAP, y, statsW, h);
		drawAbiphoneIcons(g, font, snapshot, x, y, iconsW, h, mouseX, mouseY);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		drawHover(g, font, this.zones, mouseX, mouseY, screenW, screenH);
	}

	private void drawAbiphoneStats(GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		PvDraw.text(g, font, "Abiphone", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font,
			snapshot.abiphoneActive() + " / " + snapshot.abiphoneContacts().size(),
			lx + lw, ly, PvDraw.COLOR_ACCENT);
		ly += font.lineHeight + 4;
		ly = sectionSeparator(g, font, x, ly, w);

		if (!snapshot.abiphoneRingtone().isBlank()) {
			ly = statLine(g, font, "Ringtone", prettyRingtone(snapshot.abiphoneRingtone()),
				lx, ly, lw, PvDraw.COLOR_TEXT);
		}
		if (snapshot.trioContactAddons() > 0) {
			ly = statLine(g, font, "Trio addons", String.valueOf(snapshot.trioContactAddons()),
				lx, ly, lw, PvDraw.COLOR_GOLD);
		}
		if (snapshot.operatorChipRepaired() > 0) {
			ly = statLine(g, font, "Operator chip", "Repaired " + snapshot.operatorChipRepaired(),
				lx, ly, lw, PvDraw.COLOR_ACCENT);
		}
		ly = statLine(g, font, "Quests done",
			snapshot.abiphoneQuestsDone() + "/" + snapshot.abiphoneContacts().size(),
			lx, ly, lw, ENABLED);
		if (snapshot.abiphoneDndCount() > 0) {
			ly = statLine(g, font, "DND contacts", String.valueOf(snapshot.abiphoneDndCount()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}
		if (snapshot.snakeBestScore() > 0 || snapshot.tttLosses() > 0 || snapshot.tttDraws() > 0) {
			String games = "Snake " + snapshot.snakeBestScore()
				+ " · TTT L" + snapshot.tttLosses()
				+ " D" + snapshot.tttDraws();
			ly = statLine(g, font, "Games", games, lx, ly, lw, DOJO_COLOR);
		}
		if (!snapshot.abiphoneSort().isBlank()) {
			ly = statLine(g, font, "Sort", prettyRingtone(snapshot.abiphoneSort()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}
		ly = sectionSeparator(g, font, x, ly, w);
		PvDraw.text(g, font, "Last Called", lx, ly, PvDraw.COLOR_MUTED);
	}

	private void drawAbiphoneIcons(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Contacts", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;

		List<CrimsonSnapshot.AbiphoneContact> contacts = snapshot.abiphoneContacts();
		if (contacts.isEmpty()) {
			PvDraw.textCentered(g, font, "No contacts",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.abiphoneMaxScroll = 0;
			this.abiphoneW = 0;
			this.abiphoneH = 0;
			return;
		}

		int gridH = Math.max(1, bottom - ly);
		// Expand slot size to fill the left panel and reduce empty space.
		int maxCols = Math.max(5, Math.min(10, contacts.size()));
		int slot = 18;
		int gap = 3;
		for (int trySlot = 28; trySlot >= 18; trySlot--) {
			int tryGap = trySlot >= 24 ? 4 : 3;
			int cols = Math.max(1, (lw + tryGap) / (trySlot + tryGap));
			cols = Math.min(cols, maxCols);
			int rows = (contacts.size() + cols - 1) / cols;
			int needH = rows * (trySlot + tryGap) - tryGap;
			if (needH <= gridH || trySlot == 18) {
				slot = trySlot;
				gap = tryGap;
				break;
			}
		}
		int cols = Math.max(1, Math.min(maxCols, (lw + gap) / (slot + gap)));
		// Stretch gaps so the grid uses the full width.
		int usedW = cols * slot;
		int freeW = Math.max(0, lw - usedW);
		int gapX = cols > 1 ? freeW / (cols - 1) : 0;
		gapX = Math.max(gap, gapX);
		int gridRows = (contacts.size() + cols - 1) / cols;
		int contentH = gridRows * (slot + gap) - gap;
		this.abiphoneX = x;
		this.abiphoneY = ly;
		this.abiphoneW = w;
		this.abiphoneH = Math.max(0, bottom - ly);
		this.abiphoneMaxScroll = Math.max(0, contentH - this.abiphoneH);
		this.abiphoneScroll = Math.min(this.abiphoneScroll, this.abiphoneMaxScroll);

		g.enableScissor(lx, this.abiphoneY, lx + lw, this.abiphoneY + this.abiphoneH);
		for (int i = 0; i < contacts.size(); i++) {
			CrimsonSnapshot.AbiphoneContact contact = contacts.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (slot + gapX);
			int by = ly + row * (slot + gap) - this.abiphoneScroll;
			boolean hovered = mx >= bx && mx < bx + slot && my >= by && my < by + slot
				&& my >= this.abiphoneY && my < this.abiphoneY + this.abiphoneH;
			PvDraw.fill(g, bx, by, slot, slot, ITEM_SLOT_BG);
			g.outline(bx, by, slot, slot,
				hovered ? PvDraw.COLOR_ACCENT : (contact.active() ? ITEM_SLOT_BORDER : DISABLED));

			int iconPad = Math.max(0, (slot - 16) / 2);
			drawContactIcon(g, contact.id(), bx + iconPad, by + iconPad);
			if (!contact.active()) {
				PvDraw.fill(g, bx + 1, by + 1, slot - 2, slot - 2, 0x66000000);
			}

			int y0 = Math.max(by, this.abiphoneY);
			int y1 = Math.min(by + slot, this.abiphoneY + this.abiphoneH);
			if (y1 > y0) {
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.of(contact.name(), PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.of(contact.active() ? "Active contact" : "Inactive",
					contact.active() ? ENABLED : PvDraw.COLOR_MUTED));
				if (contact.completedQuest()) {
					tip.add(PvTooltip.Line.of("Quest complete", ENABLED));
				} else if (contact.talkedTo()) {
					tip.add(PvTooltip.Line.of("Talked to", PvDraw.COLOR_MUTED));
				}
				if (contact.incomingCalls() > 0) {
					tip.add(PvTooltip.Line.of("Incoming calls: " + FormatUtil.commas(contact.incomingCalls()),
						PvDraw.COLOR_MUTED));
				}
				if (contact.dnd()) {
					tip.add(PvTooltip.Line.of("Do Not Disturb", PvDraw.COLOR_GOLD));
				}
				this.zones.add(HoverZone.of(bx, y0, slot, y1 - y0, tip));
			}
		}
		g.disableScissor();
	}

	/** Prefer the custom NEU skin texture, then a vanilla stack; never show blank paper. */
	private static void drawContactIcon(GuiGraphicsExtractor g, String contactId, int x, int y) {
		String neuId = AbiphoneNpcs.neuId(contactId);
		ItemStack icon = neuId.isBlank() ? ItemStack.EMPTY : SkyBlockItemFactory.iconStack(neuId);
		if (!neuId.isBlank() && SkyBlockIconRenderer.hasKnownIcon(neuId)) {
			SkyBlockIconRenderer.draw(g, icon, neuId, x, y, 16);
			return;
		}
		if (icon.isEmpty() || icon.is(Items.PAPER) || isUntexturedHead(icon)) {
			icon = new ItemStack(Items.VILLAGER_SPAWN_EGG);
		}
		g.item(icon, x, y);
	}

	private static boolean isUntexturedHead(ItemStack icon) {
		if (icon == null || icon.isEmpty() || !icon.is(Items.PLAYER_HEAD)) {
			return false;
		}
		var profile = icon.get(DataComponents.PROFILE);
		if (profile == null) {
			return true;
		}
		try {
			var props = profile.partialProfile().properties().get("textures");
			if (props == null || props.isEmpty()) {
				return true;
			}
			for (var prop : props) {
				if (prop != null && prop.value() != null && !prop.value().isBlank()) {
					return false;
				}
			}
		} catch (Exception ignored) {
			return true;
		}
		return true;
	}
}
