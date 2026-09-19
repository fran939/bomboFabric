package dev.vy.betterpv.client.gui.events.page;

import dev.vy.betterpv.client.data.ChocolateEmployees;
import dev.vy.betterpv.client.data.EventsSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.HoppityRabbitsData;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.events.EventsUi;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.vy.betterpv.client.gui.events.EventsUi.*;

/** Chocolate Factory subpage (owns rabbit-list scroll). */
public final class ChocolatePage {
	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;

	public void resetScroll() {
		this.scroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, int contentX, int contentW) {
		if (this.maxScroll <= 0 || this.scrollH <= 0) {
			return false;
		}
		if (mouseY < this.scrollTop || mouseY >= this.scrollTop + this.scrollH) {
			return false;
		}
		if (mouseX < contentX || mouseX >= contentX + contentW) {
			return false;
		}
		int step = STAT_ROW * 3;
		int next = Math.max(0, Math.min(this.maxScroll, this.scroll + (scrollY > 0 ? -step : step)));
		if (next != this.scroll) {
			this.scroll = next;
			return true;
		}
		return false;
	}

	public void render(
		EventsSnapshot snapshot,
		EventsUi ui,
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mx,
		int my
	) {
		EventsSnapshot.Chocolate choc = snapshot.chocolate();
		int rightW = Math.max(200, w * 52 / 100);
		int leftW = w - rightW - GAP;
		int lx = x;
		int rx = x + leftW + GAP;
		PvDraw.innerPanel(g, lx, y, leftW, h);
		PvDraw.innerPanel(g, rx, y, rightW, h);

		int bottom = y + h - PAD;
		int cx = lx + PAD;
		int cy = y + PAD;
		int cw = leftW - PAD * 2;

		cy += PvDraw.sectionHeader(g, font, "Chocolate", cx, cy, cw);
		if (!choc.present()) {
			PvDraw.text(g, font, "No chocolate data", cx, cy, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		cy = ui.tipStat(g, font, "Current", FormatUtil.shortCoins(choc.chocolate()), COLOR_CHOCOLATE, cx, cy, cw, mx, my,
			tipTitle("Chocolate", COLOR_CHOCOLATE,
				PvTooltip.Line.row("Purse", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocolate()), COLOR_CHOCOLATE),
				PvTooltip.Line.row("All-time", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.totalChocolate()), PvDraw.COLOR_GOLD)));
		cy = ui.tipStat(g, font, "All-time", FormatUtil.shortCoins(choc.totalChocolate()), PvDraw.COLOR_GOLD, cx, cy, cw, mx, my,
			tipTitle("All-time chocolate", PvDraw.COLOR_GOLD,
				PvTooltip.Line.meta(FormatUtil.commas(choc.totalChocolate()) + " chocolate")));
		cy = ui.tipStat(g, font, "This prestige", FormatUtil.shortCoins(choc.chocolateSincePrestige()), PvDraw.COLOR_TEXT,
			cx, cy, cw, mx, my,
			tipTitle("This prestige", PvDraw.COLOR_TEXT,
				PvTooltip.Line.row("Earned", PvDraw.COLOR_MUTED,
					FormatUtil.commas(choc.chocolateSincePrestige()), PvDraw.COLOR_TEXT)));

		if (cy + SEP_GAP + STAT_ROW * 3 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Factory", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = ui.tipStat(g, font, "Level", FormatUtil.commas(choc.chocolateLevel()), PvDraw.COLOR_ACCENT, cx, cy, cw, mx, my,
				tipTitle("Factory level", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.chocolateLevel()), PvDraw.COLOR_ACCENT)));
			cy = ui.tipStat(g, font, "Click upgrades", FormatUtil.commas(choc.clickUpgrades()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			cy = ui.tipStat(g, font, "Multiplier", FormatUtil.commas(choc.multiplierUpgrades()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Rabbit rarity", FormatUtil.commas(choc.rabbitRarityUpgrades()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Barn capacity", FormatUtil.commas(choc.barnCapacityLevel()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
		}

		if (cy + SEP_GAP + font.lineHeight + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Time Tower", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = ui.tipStat(g, font, "Level", FormatUtil.commas(choc.timeTowerLevel()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			if (cy + STAT_ROW <= bottom) {
				int chargeColor = choc.timeTowerCharges() >= 3 ? COLOR_COMPLETE : PvDraw.COLOR_ACCENT;
				cy = ui.tipStat(g, font, "Charges", choc.timeTowerCharges() + " / 3", chargeColor, cx, cy, cw, mx, my,
					tipTitle("Time Tower charges", PvDraw.COLOR_ACCENT,
						PvTooltip.Line.row("Charges", PvDraw.COLOR_MUTED, choc.timeTowerCharges() + " / 3", chargeColor)));
			}
			if (choc.timeTowerActivationMs() > 0L && cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Last active", formatAgo(choc.timeTowerActivationMs()), PvDraw.COLOR_MUTED,
					cx, cy, cw, mx, my,
					tipTitle("Time Tower", PvDraw.COLOR_ACCENT,
						PvTooltip.Line.meta("Activated " + formatAgo(choc.timeTowerActivationMs()))));
			}
		}

		if (cy + SEP_GAP + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Shop", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = ui.tipStat(g, font, "Cocoa fortune", FormatUtil.commas(choc.cocoaFortuneUpgrades()), PvDraw.COLOR_TEXT,
				cx, cy, cw, mx, my, null);
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Spent", FormatUtil.shortCoins(choc.chocolateSpent()), COLOR_CHOCOLATE,
					cx, cy, cw, mx, my,
					tipTitle("Chocolate shop", COLOR_CHOCOLATE,
						PvTooltip.Line.row("Spent", PvDraw.COLOR_MUTED,
							FormatUtil.commas(choc.chocolateSpent()), COLOR_CHOCOLATE)));
			}
		}

		if (cy + SEP_GAP + STAT_ROW * 3 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Rabbits & eggs", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			cy = ui.tipStat(g, font, "Unique", FormatUtil.commas(choc.uniqueRabbits()), PvDraw.COLOR_ACCENT, cx, cy, cw, mx, my,
				tipTitle("Rabbits", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Unique", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.uniqueRabbits()), PvDraw.COLOR_ACCENT),
					PvTooltip.Line.row("Copies", PvDraw.COLOR_MUTED, FormatUtil.commas(choc.totalRabbitDuplicates()), PvDraw.COLOR_TEXT)));
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Copies", FormatUtil.commas(choc.totalRabbitDuplicates()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Breakfast eggs", FormatUtil.shortCoins(choc.breakfastEggs()), COLOR_CHOCOLATE,
					cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Lunch eggs", FormatUtil.shortCoins(choc.lunchEggs()), COLOR_CHOCOLATE, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Dinner eggs", FormatUtil.shortCoins(choc.dinnerEggs()), COLOR_CHOCOLATE, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Hitmen slots", FormatUtil.commas(choc.hitmenSlots()), PvDraw.COLOR_TEXT, cx, cy, cw, mx, my, null);
			}
			if (cy + STAT_ROW <= bottom) {
				ui.tipStat(g, font, "Missed eggs", FormatUtil.commas(choc.missedEggs()),
					choc.missedEggs() > 0 ? 0xFFFF8888 : PvDraw.COLOR_MUTED, cx, cy, cw, mx, my,
					choc.missedEggs() > 0
						? tipTitle("Missed eggs", 0xFFFF8888,
						PvTooltip.Line.meta("Uncollected eggs from rabbit hitmen"))
						: null);
			}
		}

		int rcx = rx + PAD;
		int ry = y + PAD;
		int rcw = rightW - PAD * 2;
		ry += PvDraw.sectionHeader(g, font, "Employees", rcx, ry, rcw);
		List<EventsSnapshot.Employee> employees = choc.employees();
		if (employees.isEmpty()) {
			PvDraw.text(g, font, "None", rcx, ry, PvDraw.COLOR_MUTED);
			ry += STAT_ROW + 2;
		} else {
			int empRow = RABBIT_ROW;
			for (EventsSnapshot.Employee emp : employees) {
				if (ry + empRow > bottom) {
					break;
				}
				drawEmployeeRow(ui, g, font, emp, rcx, ry, rcw);
				ry += empRow;
			}
		}

		if (ry + SEP_GAP + font.lineHeight + STAT_ROW <= bottom) {
			ry = sectionSeparator(g, rx, ry, rightW);
			PvDraw.text(g, font, "Top rabbits", rcx, ry, PvDraw.COLOR_MUTED);
			ry += font.lineHeight + 3;
			List<EventsSnapshot.Rabbit> rabbits = choc.topRabbits();
			if (rabbits.isEmpty()) {
				PvDraw.text(g, font, "None", rcx, ry, PvDraw.COLOR_MUTED);
				this.maxScroll = 0;
				return;
			}
			int viewH = Math.max(0, bottom - ry);
			int contentH = rabbits.size() * STAT_ROW;
			this.scrollTop = ry;
			this.scrollH = viewH;
			this.maxScroll = Math.max(0, contentH - viewH);
			this.scroll = Math.min(this.scroll, this.maxScroll);
			g.enableScissor(rcx, ry, rcx + rcw, ry + viewH);
			int gy = ry - this.scroll;
			for (EventsSnapshot.Rabbit rabbit : rabbits) {
				String rarity = rabbit.rarity() == null || rabbit.rarity().isBlank()
					? HoppityRabbitsData.rarityOf(rabbit.id())
					: rabbit.rarity();
				int rarityColor = SkyBlockItemFactory.tierArgb(rarity);
				EventsUi.statLine(g, font, trim(font, rabbit.name(), Math.max(24, rcw - font.width("×" + rabbit.count()) - 8)),
					"×" + rabbit.count(), rcx, gy, rcw, rarityColor);
				ui.addClippedHover(rcx, gy, rcw, STAT_ROW, rcx, ry, rcw, viewH, tipTitle(rabbit.name(), rarityColor,
					PvTooltip.Line.row("Rarity", PvDraw.COLOR_MUTED, prettyModifier(rarity), rarityColor),
					PvTooltip.Line.row("Copies", PvDraw.COLOR_MUTED, FormatUtil.commas(rabbit.count()), PvDraw.COLOR_GOLD)));
				gy += STAT_ROW;
			}
			g.disableScissor();
		} else {
			this.maxScroll = 0;
		}
	}

	private void drawEmployeeRow(
		EventsUi ui, GuiGraphicsExtractor g, Font font, EventsSnapshot.Employee emp, int x, int y, int w
	) {
		String rarity = ChocolateEmployees.rarityOf(emp.id());
		int rarityColor = SkyBlockItemFactory.tierArgb(rarity);
		int slotBg = raritySlotBackground(rarityColor);
		PvDraw.fill(g, x, y + 1, RABBIT_SLOT, RABBIT_SLOT, slotBg);
		g.outline(x, y + 1, RABBIT_SLOT, RABBIT_SLOT, SLOT_BORDER);

		ItemStack icon = employeeIcon(emp.id());
		PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(y + 1, RABBIT_SLOT, 16, font.lineHeight);
		g.item(icon, x, rowAlign.iconY());

		String name = ChocolateEmployees.displayName(emp.id(), emp.name());
		String level = "Lvl " + emp.level();
		int textX = x + RABBIT_SLOT + 4;
		int textW = Math.max(8, w - RABBIT_SLOT - 4);
		int leftMax = Math.max(8, textW - font.width(level) - 6);
		PvDraw.text(g, font, trim(font, name, leftMax), textX, rowAlign.textY(), rarityColor);
		PvDraw.textRight(g, font, level, x + w, rowAlign.textY(), PvDraw.COLOR_TEXT);

		ui.addClippedHover(x, y, w, RABBIT_ROW, ui.contentX, ui.contentY, ui.contentW, ui.contentH,
			tipTitle(name, rarityColor,
				PvTooltip.Line.row("Rarity", PvDraw.COLOR_MUTED, prettyModifier(rarity), rarityColor),
				PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, FormatUtil.commas(emp.level()), PvDraw.COLOR_TEXT)));
	}

	private static ItemStack employeeIcon(String employeeId) {
		String value = ChocolateEmployees.skullValue(employeeId);
		if (value != null && !value.isBlank()) {
			ItemStack head = SkyBlockItemFactory.texturedHead(value);
			if (head != null && !head.isEmpty()) {
				return head;
			}
		}
		ItemStack fallback = SkyBlockItemFactory.iconStack("CHOCO_RABBIT_PERSONALITY");
		if (fallback != null && !fallback.isEmpty()) {
			return fallback;
		}
		return new ItemStack(Items.RABBIT_FOOT);
	}

	/** Soft rarity tint like pets/museum slots. */
	private static int raritySlotBackground(int rarityArgb) {
		int r = (rarityArgb >> 16) & 0xFF;
		int g = (rarityArgb >> 8) & 0xFF;
		int b = rarityArgb & 0xFF;
		int mixR = (r * 70 + 16 * 186) / 256;
		int mixG = (g * 70 + 16 * 186) / 256;
		int mixB = (b * 70 + 24 * 186) / 256;
		return 0xFF000000 | (mixR << 16) | (mixG << 8) | mixB;
	}
}
