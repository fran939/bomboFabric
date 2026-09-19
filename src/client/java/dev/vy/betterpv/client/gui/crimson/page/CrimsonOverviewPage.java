package dev.vy.betterpv.client.gui.crimson.page;

import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.BARB_COLOR;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.DOJO_COLOR;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.ENABLED;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.FLIP_MS;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.GAP;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.ITEM_SLOT_BG;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.KUUDRA_COLOR;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.MAGE_COLOR;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.PAD;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.PANEL_HOVER;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.SHOP_HEADER_COLOR;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.STAT_ROW;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.drawItemIcon;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.easeInOutCubic;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.formatAgo;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.sectionSeparator;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.statLine;
import static dev.vy.betterpv.client.gui.crimson.CrimsonUi.trim;

import dev.vy.betterpv.client.data.CrimsonSnapshot;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.crimson.CrimsonUi;
import dev.vy.betterpv.client.gui.crimson.CrimsonUi.HoverZone;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Crimson Overview: factions, essence, Dojo / Crimson Essence Shop flip panel. */
public final class CrimsonOverviewPage {
	/** false = Dojo face shown (default), true = Crimson Essence Shop face shown. */
	private boolean showingShop;
	private boolean flipTarget;
	private long flipStartMs;
	private int flipHitX;
	private int flipHitY;
	private int flipHitW;
	private int flipHitH;
	private final List<HoverZone> zones = new ArrayList<>();

	public boolean mouseClicked(double mx, double my) {
		if (mx >= this.flipHitX && mx < this.flipHitX + this.flipHitW
			&& my >= this.flipHitY && my < this.flipHitY + this.flipHitH) {
			if (this.flipStartMs == 0L) {
				this.flipTarget = !this.showingShop;
				this.flipStartMs = System.currentTimeMillis();
			}
			return true;
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		this.flipHitW = 0;
		this.flipHitH = 0;
		int rightW = Math.max(200, w * 52 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		drawOverviewLeft(g, font, snapshot, x, y, leftW, h, mouseX, mouseY);
		drawRightFlipPanel(g, font, snapshot, x + leftW + GAP, y, rightW, h, mouseX, mouseY);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		CrimsonUi.drawHover(g, font, this.zones, mouseX, mouseY, screenW, screenH);
	}

	private void drawOverviewLeft(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		ly = statLine(g, font, "Selected faction", snapshot.factionLabel(),
			lx, ly, lw, snapshot.factionColor(MAGE_COLOR, BARB_COLOR, PvDraw.COLOR_MUTED)) + 2;

		ly = drawFactionCards(g, font, snapshot, lx, ly, lw) + 4;

		ly = sectionSeparator(g, font, x, ly, w);
		ly = statLine(g, font, "Crimson essence", FormatUtil.commas(snapshot.crimsonEssence()),
			lx, ly, lw, PvDraw.COLOR_GOLD);
		ly = statLine(g, font, "Matriarch pearls", FormatUtil.commas(snapshot.matriarchPearls()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);
		if (snapshot.matriarchLastAttemptMs() > 0L) {
			ly = statLine(g, font, "Last pearl attempt", formatAgo(snapshot.matriarchLastAttemptMs()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}

		ly = sectionSeparator(g, font, x, ly, w);
		Map<String, Boolean> bosses = snapshot.minibosses();
		int killed = snapshot.minibossesKilled();
		int total = Math.max(1, bosses.size());
		int bossY = ly;
		ly = statLine(g, font, "Minibosses", killed + " / " + total,
			lx, ly, lw, killed >= total ? ENABLED : PvDraw.COLOR_TEXT);
		List<PvTooltip.Line> bossTip = new ArrayList<>();
		bossTip.add(PvTooltip.Line.title("Crimson minibosses", PvDraw.COLOR_TEXT));
		bossTip.add(PvTooltip.Line.divider());
		for (Map.Entry<String, Boolean> e : bosses.entrySet()) {
			boolean done = Boolean.TRUE.equals(e.getValue());
			bossTip.add(PvTooltip.Line.row(
				prettyMiniboss(e.getKey()), PvDraw.COLOR_MUTED,
				done ? "Killed" : "Not killed",
				done ? ENABLED : PvDraw.COLOR_MUTED
			));
		}
		if (!snapshot.lastMinibossesKilled().isEmpty()) {
			bossTip.add(PvTooltip.Line.blank());
			bossTip.add(PvTooltip.Line.meta("Recent"));
			for (String id : snapshot.lastMinibossesKilled()) {
				bossTip.add(PvTooltip.Line.row("Recent", PvDraw.COLOR_MUTED, prettyMiniboss(id), PvDraw.COLOR_ACCENT));
			}
		}
		this.zones.add(HoverZone.of(lx, bossY, lw, STAT_ROW, bossTip));

		ly = sectionSeparator(g, font, x, ly, w);
		ly = statLine(g, font, "Kuudra clears", FormatUtil.commas(snapshot.kuudraTotalCompletions()),
			lx, ly, lw, KUUDRA_COLOR);
		int wave = snapshot.kuudraHighestClearedWave();
		ly = statLine(g, font, "Highest wave", wave > 0 ? String.valueOf(wave) : "-",
			lx, ly, lw, PvDraw.COLOR_TEXT);
		if (!snapshot.dailyQuests().isEmpty()) {
			List<PvTooltip.Line> dailyTip = new ArrayList<>();
			dailyTip.add(PvTooltip.Line.title("Daily quests", PvDraw.COLOR_TEXT));
			dailyTip.add(PvTooltip.Line.divider());
			int i = 1;
			for (String q : snapshot.dailyQuests()) {
				dailyTip.add(PvTooltip.Line.row(
					"#" + i, PvDraw.COLOR_MUTED, prettyDailyQuest(q), PvDraw.COLOR_ACCENT
				));
				i++;
			}
			int dailyY = ly;
			ly = statLine(g, font, "Daily quests", String.valueOf(snapshot.dailyQuests().size()),
				lx, ly, lw, PvDraw.COLOR_ACCENT);
			this.zones.add(HoverZone.of(lx, dailyY, lw, STAT_ROW, dailyTip));
		}
		if (snapshot.cavityNpcs() > 0) {
			ly = statLine(g, font, "Cavity NPCs", String.valueOf(snapshot.cavityNpcs()),
				lx, ly, lw, PvDraw.COLOR_TEXT);
		}
		ly = statLine(g, font, "Kuudra Loremaster",
			snapshot.kuudraLoremaster() ? "Yes" : "No",
			lx, ly, lw, snapshot.kuudraLoremaster() ? ENABLED : PvDraw.COLOR_MUTED);
	}

	private static String prettyDailyQuest(String id) {
		if (id == null || id.isBlank()) {
			return "?";
		}
		String raw = id.trim().toLowerCase(Locale.ROOT);
		if (raw.startsWith("crimson_isle_")) {
			raw = raw.substring("crimson_isle_".length());
		}
		raw = raw.replace('_', ' ').replaceAll("\\s+", " ").trim();
		// Drop trailing letter grades like " a" / " b" / " c" when they look like tier tags.
		if (raw.length() > 2 && raw.charAt(raw.length() - 2) == ' '
			&& Character.isLetter(raw.charAt(raw.length() - 1))) {
			char grade = Character.toLowerCase(raw.charAt(raw.length() - 1));
			if (grade >= 'a' && grade <= 'd') {
				raw = raw.substring(0, raw.length() - 2);
			}
		}
		StringBuilder out = new StringBuilder(raw.length());
		boolean cap = true;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == ' ') {
				out.append(c);
				cap = true;
			} else if (cap) {
				out.append(Character.toUpperCase(c));
				cap = false;
			} else {
				out.append(c);
			}
		}
		String pretty = out.toString()
			.replace("Kuudra Hot Tier", "Kuudra (Hot)")
			.replace("Kuudra Burning Tier", "Kuudra (Burning)")
			.replace("Kuudra Fiery Tier", "Kuudra (Fiery)")
			.replace("Kuudra Infernal Tier", "Kuudra (Infernal)")
			.replace("Dojo Test Of", "Dojo:")
			.replace("Kb Drating", "Knockback")
			.replace("Soulfish", "Soulfish")
			.replace("Fetch Magmag", "Fetch Magmaggies")
			.replace("Kill Magma Boss", "Kill Magma Boss");
		return pretty.isBlank() ? "?" : pretty;
	}

	private static String prettyMiniboss(String id) {
		if (id == null || id.isBlank()) {
			return "?";
		}
		return switch (id.toUpperCase(Locale.ROOT)) {
			case "BLADESOUL" -> "Bladesoul";
			case "MAGE_OUTLAW" -> "Mage Outlaw";
			case "BARBARIAN_DUKE_X" -> "Barbarian Duke X";
			case "ASHFANG" -> "Ashfang";
			case "MAGMA_BOSS" -> "Magma Boss";
			default -> {
				String lower = id.toLowerCase(Locale.ROOT).replace('_', ' ');
				StringBuilder out = new StringBuilder(lower.length());
				boolean cap = true;
				for (int i = 0; i < lower.length(); i++) {
					char c = lower.charAt(i);
					if (c == ' ') {
						out.append(c);
						cap = true;
					} else if (cap) {
						out.append(Character.toUpperCase(c));
						cap = false;
					} else {
						out.append(c);
					}
				}
				yield out.toString();
			}
		};
	}

	/** Side-by-side bordered faction cards. */
	private int drawFactionCards(GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot, int x, int y, int w) {
		int gap = 8;
		int colW = (w - gap) / 2;
		int cardPad = 4;
		int iconSize = 16;
		int cardH = cardPad * 2 + iconSize + 2 + STAT_ROW;

		drawFactionCard(g, font, "MAGE", new ItemStack(Items.BLAZE_POWDER),
			snapshot.magesReputation(), snapshot.magesReputationHighest(), MAGE_COLOR,
			x, y, colW, cardH, cardPad, iconSize);
		drawFactionCard(g, font, "BARBARIAN", new ItemStack(Items.IRON_AXE),
			snapshot.barbariansReputation(), snapshot.barbariansReputationHighest(), BARB_COLOR,
			x + colW + gap, y, colW, cardH, cardPad, iconSize);
		return y + cardH;
	}

	private void drawFactionCard(
		GuiGraphicsExtractor g, Font font, String name, ItemStack icon,
		double reputation, double peak, int color, int x, int y, int w, int h, int pad, int iconSize
	) {
		PvDraw.fill(g, x, y, w, h, ITEM_SLOT_BG);
		g.outline(x, y, w, h, PvDraw.COLOR_BORDER);

		int ix = x + pad;
		int iy = y + pad;
		drawItemIcon(g, icon, ix, iy, iconSize);
		int textY = iy + Math.max(0, (iconSize - font.lineHeight) / 2);
		PvDraw.text(g, font, name, ix + iconSize + 3, textY, color);

		int statY = iy + iconSize + 2;
		PvDraw.text(g, font, "Reputation:", ix, statY, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, FormatUtil.commas(Math.round(reputation)), x + w - pad, statY, color);
		this.zones.add(HoverZone.of(x, y, w, h, List.of(
			PvTooltip.Line.title(name, color),
			PvTooltip.Line.divider(),
			PvTooltip.Line.row("Reputation", PvDraw.COLOR_MUTED,
				FormatUtil.commas(Math.round(reputation)), color),
			PvTooltip.Line.row("Peak", PvDraw.COLOR_MUTED,
				FormatUtil.commas(Math.round(peak)), PvDraw.COLOR_GOLD)
		)));
	}

	private void drawRightFlipPanel(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.flipHitX = x;
		this.flipHitY = y;
		this.flipHitW = w;
		this.flipHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.flipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.flipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.showingShop = this.flipTarget;
				this.flipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showShop = animating
			? (Math.cos(angle) < 0.0 ? this.flipTarget : this.showingShop)
			: this.showingShop;
		float scaleX = 1F;
		float scaleY = 1F;
		if (animating) {
			scaleX = Math.max(0.04F, Math.abs((float) Math.cos(angle)));
			scaleY = 1F - (1F - scaleX) * 0.06F;
		}

		float cxFlip = x + w / 2F;
		float cyFlip = y + h / 2F;
		g.pose().pushMatrix();
		g.pose().translate(cxFlip, cyFlip);
		g.pose().scale(scaleX, scaleY);
		g.pose().translate(-cxFlip, -cyFlip);

		PvDraw.innerPanel(g, x, y, w, h);
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER);
		}

		if (showShop) {
			drawShopFace(g, font, snapshot, x, y, w, h);
		} else {
			drawDojoFace(g, font, snapshot, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawDojoFace(GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		PvDraw.text(g, font, "Dojo", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, "Total Points: " + FormatUtil.commas(snapshot.dojoTotalPoints()),
			lx + lw, ly, DOJO_COLOR);
		ly += font.lineHeight + 2;

		int colGap = 6;
		int colW = (lw - colGap) / 2;
		int leftCol = lx;
		int rightCol = lx + colW + colGap;
		CrimsonSnapshot.DojoChallenge[] challenges = CrimsonSnapshot.DojoChallenge.values();
		int rows = (challenges.length + 1) / 2;
		int bottom = y + h - PAD;
		int available = Math.max(rows * 22, bottom - ly);
		int entryGap = 1;
		// Icon + Points + Rank (no time).
		int entryH = Math.max(16 + STAT_ROW * 2, (available - (rows - 1) * entryGap) / rows);
		entryH = Math.min(entryH, 16 + STAT_ROW * 3);

		for (int i = 0; i < challenges.length; i++) {
			int col = i % 2;
			int row = i / 2;
			int cx = col == 0 ? leftCol : rightCol;
			int cy = ly + row * (entryH + entryGap);
			if (cy + 16 + STAT_ROW > bottom) {
				break;
			}
			drawDojoEntry(g, font, snapshot, challenges[i], cx, cy, colW, entryH);
		}
	}

	private void drawDojoEntry(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot, CrimsonSnapshot.DojoChallenge challenge,
		int x, int y, int w, int entryH
	) {
		CrimsonSnapshot.DojoScore score = snapshot.dojo(challenge);
		int icon = 16;
		drawItemIcon(g, dojoIcon(challenge), x, y, icon);
		int textY = y + Math.max(0, (icon - font.lineHeight) / 2);
		PvDraw.text(g, font, challenge.label(), x + icon + 3, textY, PvDraw.COLOR_TEXT);

		int ly = y + icon + 1;
		ly = statLine(g, font, "Points", FormatUtil.commas(score.points()), x, ly, w, DOJO_COLOR);
		if (ly + font.lineHeight <= y + entryH) {
			String rank = CrimsonSnapshot.dojoRank(score.points());
			statLine(g, font, "Rank", rank, x, ly, w, CrimsonSnapshot.dojoRankColor(rank));
		}
		if (score.timeMs() > 0L) {
			this.zones.add(HoverZone.of(x, y, w, entryH, List.of(
				PvTooltip.Line.title(challenge.label(), DOJO_COLOR),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Points", PvDraw.COLOR_MUTED, FormatUtil.commas(score.points()), DOJO_COLOR),
				PvTooltip.Line.row("Time", PvDraw.COLOR_MUTED, FormatUtil.prettyTime(score.timeMs()), PvDraw.COLOR_TEXT)
			)));
		}
	}

	private static ItemStack dojoIcon(CrimsonSnapshot.DojoChallenge challenge) {
		return switch (challenge) {
			case MOB_KB -> new ItemStack(Items.BLAZE_ROD);
			case WALL_JUMP -> new ItemStack(Items.RABBIT_FOOT);
			case ARCHER -> new ItemStack(Items.LEAD);
			case SWORD_SWAP -> new ItemStack(Items.DIAMOND_SWORD);
			case SNAKE -> new ItemStack(Items.BOW);
			case FIREBALL -> new ItemStack(Items.FIRE_CHARGE);
			case LOCK_HEAD -> new ItemStack(Items.ENDER_EYE);
		};
	}

	private void drawShopFace(GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		DungeonSnapshot.EssenceShop shop = snapshot.crimsonShop();
		int headerIcon = 16;
		int headerH = Math.max(headerIcon, font.lineHeight + 2);
		PvDraw.IconTextAlign headerAlign = PvDraw.IconTextAlign.of(ly, headerH, headerIcon, font.lineHeight);

		drawItemIcon(g, essenceIcon(shop.iconId()), lx, headerAlign.iconY(), headerIcon);
		int labelX = lx + headerIcon + 4;
		String bal = FormatUtil.commas(shop.balance());
		int balW = PvDraw.widthBold(font, bal);
		int nameMax = Math.max(8, lw - (labelX - lx) - balW - 4);
		PvDraw.textBold(g, font, trim(font, "Crimson", nameMax), labelX, headerAlign.textY(), SHOP_HEADER_COLOR);
		PvDraw.textBold(g, font, bal, lx + lw - balW, headerAlign.textY(), SHOP_HEADER_COLOR);

		int perkIconSize = 12;
		int rowH = Math.max(font.lineHeight + 2, perkIconSize + 2);
		int colGap = 10;
		int colW = Math.max(40, (lw - colGap) / 2);
		int leftCol = lx;
		int rightCol = lx + colW + colGap;
		List<DungeonSnapshot.EssencePerk> perks = shop.perks();
		int mid = (perks.size() + 1) / 2;

		drawShopPerkColumn(g, font, perks.subList(0, Math.min(mid, perks.size())),
			leftCol, ly + headerH + 4, colW, rowH, perkIconSize, bottom);
		if (mid < perks.size()) {
			drawShopPerkColumn(g, font, perks.subList(mid, perks.size()),
				rightCol, ly + headerH + 4, colW, rowH, perkIconSize, bottom);
		}
	}

	private void drawShopPerkColumn(
		GuiGraphicsExtractor g, Font font, List<DungeonSnapshot.EssencePerk> perks,
		int x, int y, int w, int rowH, int perkIconSize, int bottom
	) {
		int perkLabelX = x + perkIconSize + 3;
		int ry = y;
		for (DungeonSnapshot.EssencePerk perk : perks) {
			if (ry + font.lineHeight > bottom) {
				break;
			}
			PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(ry, rowH, perkIconSize, font.lineHeight);
			drawItemIcon(g, shopPerkIcon(perk.id()), x, rowAlign.iconY(), perkIconSize);
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			String left = trim(font, perk.name(), Math.max(8, w - (perkLabelX - x) - rightW - 4));
			int valueColor = perk.maxed() ? ENABLED : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, left, perkLabelX, rowAlign.textY(), PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, x + w, rowAlign.textY(), valueColor);
			ry += rowH;
		}
	}

	private static ItemStack essenceIcon(String skyblockId) {
		String id = skyblockId == null || skyblockId.isBlank() ? "ESSENCE_CRIMSON" : skyblockId;
		ItemStack stack = SkyBlockItemFactory.iconStack(id);
		if (stack == null || stack.isEmpty() || stack.is(Items.PAPER) || stack.is(Items.BARRIER)) {
			return new ItemStack(Items.NETHER_STAR);
		}
		return stack;
	}

	/** Crimson essence shop row icons (vanilla stand-ins matching the GUI). */
	private static ItemStack shopPerkIcon(String perkId) {
		if (perkId == null) {
			return new ItemStack(Items.NETHER_STAR);
		}
		return switch (perkId) {
			case "strongarm_kuudra" -> new ItemStack(Items.FISHING_ROD);
			case "fresh_tools_kuudra" -> new ItemStack(Items.IRON_PICKAXE);
			case "headstart_kuudra" -> new ItemStack(Items.GOLD_NUGGET);
			case "master_kuudra" -> new ItemStack(Items.NETHERITE_SWORD);
			case "fungus_fortuna" -> new ItemStack(Items.RED_MUSHROOM);
			case "harena_fortuna" -> new ItemStack(Items.RED_SAND);
			case "crimson_training" -> new ItemStack(Items.BOOK);
			case "wither_piper" -> new ItemStack(Items.WITHER_SKELETON_SKULL);
			default -> new ItemStack(Items.NETHER_STAR);
		};
	}
}
