package dev.vy.betterpv.client.gui;

import dev.vy.betterpv.client.api.EliteBotApiClient;
import dev.vy.betterpv.client.api.BetterPvSessionAuth;
import dev.vy.betterpv.client.api.HypixelApiClient;
import dev.vy.betterpv.client.api.ProfileFetcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.BestiaryData;
import dev.vy.betterpv.client.data.EventsSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.GardenSnapshot;
import dev.vy.betterpv.client.data.GuildStatus;
import dev.vy.betterpv.client.data.MiscStatsSnapshot;
import dev.vy.betterpv.client.data.MuseumCache;
import dev.vy.betterpv.client.data.PlayerStatsSnapshot;
import dev.vy.betterpv.client.data.PlayerStatus;
import dev.vy.betterpv.client.data.ProfileSnapshot;
import dev.vy.betterpv.client.data.UsernameHistory;
import dev.vy.betterpv.client.gui.auctions.AuctionPage;
import dev.vy.betterpv.client.gui.bestiary.BestiaryPage;
import dev.vy.betterpv.client.gui.collections.CollectionsPage;
import dev.vy.betterpv.client.gui.crimson.CrimsonPage;
import dev.vy.betterpv.client.gui.dungeons.DungeonPage;
import dev.vy.betterpv.client.gui.events.EventsPage;
import dev.vy.betterpv.client.gui.fishing.FishingPage;
import dev.vy.betterpv.client.gui.foraging.ForagingPage;
import dev.vy.betterpv.client.gui.garden.GardenPage;
import dev.vy.betterpv.client.gui.home.HomePage;
import dev.vy.betterpv.client.gui.home.page.MiscStatsPage;
import dev.vy.betterpv.client.gui.inventories.InventoryPage;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.mining.MiningPage;
import dev.vy.betterpv.client.gui.museum.MuseumPage;
import dev.vy.betterpv.client.gui.nav.IconButtonBar;
import dev.vy.betterpv.client.gui.nav.InventoryPane;
import dev.vy.betterpv.client.gui.nav.MuseumSort;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.gui.nav.PvTab;
import dev.vy.betterpv.client.gui.pets.PetsPage;
import dev.vy.betterpv.client.gui.rift.RiftPage;
import dev.vy.betterpv.client.gui.mining.MiningUi;
import dev.vy.betterpv.BetterPV;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ProfileViewerScreen extends Screen {
	private static final int PAD = 8;
	private static final int SEARCH_H = 16;
	private static final int SEARCH_GAP = 4;
	private static final int INV_SELECTOR_COLS = 5;
	private static final long OPEN_ANIM_MS = 260L;
	private static final float OPEN_SCALE_START = 0.12F;
	private static final int PROFILE_MENU_PAD = 6;
	private static final int PROFILE_MENU_COUNTS_W = 56;
	private static final int PROFILE_BADGE_SIZE = 10;
	private static final int PROFILE_COOP_ICON = 8;
	private static final int PROFILE_COOP_FLYOUT_W = 168;
	private static final int PROFILE_COOP_FLYOUT_PAD = 6;
	private static final int PROFILE_COOP_FLYOUT_ROW = 14;
	private static final int PROFILE_COOP_FLYOUT_BRIDGE = 6;
	private static final int COLOR_COOP_CURRENT = 0xFF55DD55;
	private static final int COLOR_COOP_FORMER = 0xFFFF6666;

	private final String requestedName;
	private final HomePage homePage;
	private final MiscStatsPage homeMiscPage = new MiscStatsPage();
	private final DungeonPage dungeonPage = new DungeonPage();
	private final InventoryPage inventoryPage = new InventoryPage();
	private final PetsPage petsPage = new PetsPage();
	private final AuctionPage auctionPage = new AuctionPage();
	private final CollectionsPage collectionsPage = new CollectionsPage();
	private final GardenPage gardenPage = new GardenPage();
	private final MiningPage miningPage = new MiningPage();
	private final ForagingPage foragingPage = new ForagingPage();
	private final FishingPage fishingPage = new FishingPage();
	private final CrimsonPage crimsonPage = new CrimsonPage();
	private final RiftPage riftPage = new RiftPage();
	private final MuseumPage museumPage = new MuseumPage();
	private final BestiaryPage bestiaryPage = new BestiaryPage();
	private final EventsPage eventsPage = new EventsPage();
	private final IconButtonBar topBar = new IconButtonBar();
	private final IconButtonBar sideBar = new IconButtonBar();
	private final IconButtonBar inventoryBar = new IconButtonBar();
	private final Map<PvTab, PvSubTab> subSelection = new EnumMap<>(PvTab.class);
	private final long openAnimStartMs = System.currentTimeMillis();
	private float openPivotX;
	private float openPivotY;

	private PvTab tab = PvTab.HOME;
	private MuseumSort museumSort = MuseumSort.ALL;
	private boolean fetchStarted;
	private boolean dataReady;
	/** Bumps on each fetch/switch so stale async results cannot overwrite newer state. */
	private int loadGeneration;
	private final Consumer<ProfileFetcher.LoadedProfile> networthListener = this::onDeferredNetworth;
	private boolean breakScheduled;
	private String profileId;
	private UUID playerUuid;
	private boolean gardenFetchStarted;
	private boolean gardenContestsFetchStarted;
	private boolean gardenWeightFetchStarted;
	private boolean museumFetchStarted;
	private boolean museumFetchInFlight;
	private boolean usernameHistoryFetchStarted;
	private boolean statusFetchStarted;
	private boolean bingoFetchStarted;
	private boolean bingoFetchInFlight;
	private long bingoRetryAtMs;
	private EditBox inventorySearch;
	private String inventorySearchQuery = "";
	private Component inventoryPaneTip;
	private int inventoryPaneTipX;
	private int inventoryPaneTipY;
	private int invSelectorX;
	private int invSelectorY;
	private int invSelectorW;
	private int invSelectorH;
	private int invSelectorScroll;
	private int invSelectorMaxScroll;
	private Component bestiaryCategoryTip;
	private int bestiaryCategoryTipX;
	private int bestiaryCategoryTipY;
	private List<IconButtonBar.Entry> topTabEntries;
	private PvTab cachedSideTab;
	private List<IconButtonBar.Entry> sideTabEntries = List.of();

	private String cachedProfileFooter = "";
	private String cachedProfileFooterName = "";
	private JsonObject profilesRoot;
	private List<ProfileFetcher.ProfileChoice> profileChoices = List.of();
	private boolean profileMenuOpen;
	private ProfileFetcher.ProfileChoice profileMenuHoverChoice;
	private int profileCoopFlyoutScroll;
	private final List<CoopMemberHit> coopMemberHits = new ArrayList<>();
	private int profileFooterX;
	private int profileFooterY;
	private int profileFooterW;
	private int profileFooterH;
	private int panelXCache;
	private int panelYCache;
	private int panelWCache;
	private int panelHCache;
	private EditBox playerSearch;
	private String playerSearchError = "";
	private long playerSearchErrorUntilMs;
	private boolean playerRankFetchStarted;

	public ProfileViewerScreen(String playerName) {
		this(playerName, PvTab.HOME);
	}

	public ProfileViewerScreen(String playerName, PvTab initialTab) {
		super(Component.translatable("betterpv.screen.title"));
		this.requestedName = playerName == null || playerName.isBlank() ? "?" : playerName.trim();
		this.homePage = new HomePage(ProfileSnapshot.loading(this.requestedName));
		this.tab = initialTab == null ? PvTab.HOME : initialTab;
		for (PvTab t : PvTab.values()) {
			PvSubTab[] subs = t.subTabs();
			if (subs.length > 0) {
				this.subSelection.put(t, subs[0]);
			}
		}
	}

	@Override
	protected void init() {
		super.init();
		this.inventorySearch = new EditBox(
			this.font,
			0,
			0,
			100,
			SEARCH_H,
			Component.translatable("betterpv.inv.search")
		);
		this.inventorySearch.setMaxLength(64);
		this.inventorySearch.setBordered(false);
		// EditBox colours are ARGB - stripping alpha makes typed text invisible.
		this.inventorySearch.setTextColor(PvDraw.COLOR_TEXT);
		this.inventorySearch.setTextColorUneditable(PvDraw.COLOR_MUTED);
		this.inventorySearch.setValue(this.inventorySearchQuery);
		this.inventorySearch.setResponder(value -> {
			this.inventorySearchQuery = value;
			this.inventoryPage.setSearchQuery(value);
			this.bestiaryPage.setSearchQuery(value);
		});
		this.inventorySearch.setVisible(false);
		this.addWidget(this.inventorySearch);

		this.playerSearch = new EditBox(
			this.font,
			0,
			0,
			120,
			SEARCH_H,
			Component.translatable("betterpv.home.search_hint")
		);
		this.playerSearch.setMaxLength(16);
		this.playerSearch.setBordered(false);
		this.playerSearch.setTextColor(PvDraw.COLOR_TEXT);
		this.playerSearch.setTextColorUneditable(PvDraw.COLOR_MUTED);
		this.playerSearch.setHint(Component.translatable("betterpv.home.search_hint"));
		this.playerSearch.setVisible(false);
		this.addWidget(this.playerSearch);

		if (this.fetchStarted) {
			return;
		}
		this.fetchStarted = true;
		int generation = ++this.loadGeneration;
		ProfileFetcher.prioritizeTab(this.tab);
		ProfileFetcher.addNetworthListener(this.networthListener);
		ProfileFetcher.fetch(this.requestedName, updated -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this || generation != this.loadGeneration) {
					return;
				}
				if (updated == null || !updated.ok()) {
					return;
				}
				applyLoadedProfile(updated);
			});
		}).whenComplete((loaded, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this || generation != this.loadGeneration) {
					return;
				}
				ProfileFetcher.LoadedProfile displayed = loaded;
				if (error != null || loaded == null) {
					BetterPV.LOGGER.warn(
						"Profile fetch failed for {}",
						this.requestedName,
						error
					);
					String message = error != null && error.getMessage() != null
						? error.getMessage()
						: "Profile fetch failed";
					displayed = ProfileFetcher.failed(this.requestedName, message);
				}
				if (!displayed.ok()) {
					BetterPV.LOGGER.warn("Profile fetch failed for {}: {}", this.requestedName, displayed.error());
					BetterPvSessionAuth.notifyPlayerIfNeeded();
					// Stay on the loading face (easter egg) instead of an empty template.
					// Intentionally do NOT set dataReady — Loading... + eventual kick remain.
					return;
				}
				applyLoadedProfile(displayed);
				this.dataReady = true;
			});
		});
	}

	private void onDeferredNetworth(ProfileFetcher.LoadedProfile updated) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || updated == null || !updated.ok()) {
			return;
		}
		client.execute(() -> {
			if (client.gui.screen() != this || !this.dataReady) {
				return;
			}
			if (this.playerUuid != null && updated.snapshot() != null
				&& this.playerUuid.equals(updated.snapshot().playerUuid())
				&& (this.profileId == null || this.profileId.equals(updated.profileId()))) {
				applyLoadedProfile(updated);
			}
		});
	}

	@Override
	public void removed() {
		ProfileFetcher.removeNetworthListener(this.networthListener);
		super.removed();
	}

	private void applyLoadedProfile(ProfileFetcher.LoadedProfile loaded) {
		if (loaded == null) {
			return;
		}
		UUID prevUuid = this.playerUuid;
		String prevProfileId = this.profileId;
		UUID nextUuid = loaded.snapshot() == null ? null : loaded.snapshot().playerUuid();
		String nextProfileId = loaded.profileId();
		boolean sameIdentity = prevUuid != null
			&& prevUuid.equals(nextUuid)
			&& prevProfileId != null
			&& prevProfileId.equals(nextProfileId);

		this.cachedProfileFooterName = "";
		this.homePage.applyLoaded(
			loaded.snapshot(),
			loaded.senither(),
			loaded.lily(),
			loaded.networthNormal(),
			loaded.networthNonCosmetic(),
			loaded.networthUnsoulbound(),
			loaded.networthUnsoulboundNonCosmetic(),
			loaded.armor(),
			loaded.playerStats(),
			loaded.error()
		);
		this.homeMiscPage.reset();
		this.homeMiscPage.apply(loaded.misc() == null ? MiscStatsSnapshot.empty() : loaded.misc());
		this.homeMiscPage.setGuildClickHandler(this::ensureGuild);
		this.dungeonPage.apply(loaded.dungeons());
		this.inventoryPage.apply(loaded.inventories());
		this.petsPage.apply(loaded.pets());
		this.auctionPage.apply(loaded.auctions());
		this.collectionsPage.apply(loaded.collections());
		this.gardenPage.apply(loaded.garden());
		this.miningPage.apply(loaded.mining());
		this.foragingPage.apply(loaded.foraging());
		this.fishingPage.apply(loaded.fishing());
		this.crimsonPage.apply(loaded.crimson());
		this.riftPage.apply(loaded.rift());
		this.bestiaryPage.apply(loaded.bestiary());
		this.eventsPage.apply(loaded.events());
		if (loaded.museumMember() != null) {
			this.museumPage.applyMuseum(loaded.museumMember());
		} else if (!sameIdentity) {
			this.museumPage.reset();
		}
		this.profileId = loaded.profileId();
		this.playerUuid = nextUuid;
		if (loaded.profilesRoot() != null) {
			this.profilesRoot = loaded.profilesRoot();
		}
		if (loaded.profiles() != null && !loaded.profiles().isEmpty()) {
			this.profileChoices = loaded.profiles();
			String viewed = this.playerUuid == null ? "" : HypixelApiClient.undashed(this.playerUuid);
			ProfileFetcher.warmCoopMemberNames(
				this.profileChoices,
				viewed,
				this.homePage.playerName(),
				() -> {
					Minecraft client = Minecraft.getInstance();
					if (client != null) {
						client.execute(() -> {
							if (client.gui.screen() == this) {
								this.profileCoopFlyoutScroll = 0;
							}
						});
					}
				}
			);
		}
		this.profileMenuOpen = false;
		this.profileMenuHoverChoice = null;
		this.profileCoopFlyoutScroll = 0;
		if (!sameIdentity) {
			this.gardenFetchStarted = false;
			this.gardenContestsFetchStarted = false;
			this.gardenWeightFetchStarted = false;
			this.museumFetchStarted = loaded.museumMember() != null;
			this.bingoFetchStarted = false;
			this.bingoFetchInFlight = false;
			this.bingoRetryAtMs = 0L;
			this.usernameHistoryFetchStarted = false;
			this.statusFetchStarted = false;
			this.playerRankFetchStarted = false;
			this.eventsPage.resetBingoFetch();
			ensurePlayerRank();
		} else if (loaded.museumMember() != null) {
			this.museumFetchStarted = true;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		this.topBar.clearHits();
		this.sideBar.clearHits();
		this.inventoryBar.clearHits();

		int topRoom = IconButtonBar.TAB + 4;
		int leftRoom = IconButtonBar.TAB + 4;

		int panelW = Math.min(520, Math.max(420, this.width - 80 - leftRoom));
		int contentH = this.homePage.preferredHeight(this.font, panelW - PAD * 2);
		if (this.tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_MISC) {
			contentH = Math.max(contentH, 220);
		}
		int maxPanelH = Math.max(200, this.height - topRoom - 24);
		int panelH = Math.min(maxPanelH, Math.max(200, contentH + PAD * 2));

		int panelX = (this.width - panelW) / 2 + leftRoom / 2;
		int panelY = (this.height - panelH - topRoom) / 2 + topRoom;
		this.panelXCache = panelX;
		this.panelYCache = panelY;
		this.panelWCache = panelW;
		this.panelHCache = panelH;

		PvDraw.fill(graphics, 0, 0, this.width, this.height, 0x99000000);

		float scale = openScale();
		this.openPivotX = panelX + panelW / 2.0F;
		this.openPivotY = panelY + panelH / 2.0F;
		boolean scaled = scale < 0.999F;
		if (scaled) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.openPivotX, this.openPivotY);
			graphics.pose().scale(scale, scale);
			graphics.pose().translate(-this.openPivotX, -this.openPivotY);
		}

		PvDraw.panel(graphics, panelX, panelY, panelW, panelH);

		if (!this.dataReady) {
			hideInventorySearch();
			hidePlayerSearch();
			drawLoadingFace(graphics, panelX, panelY, panelW, panelH);
		} else {
			this.topBar.drawTopFrameTabs(
				graphics, this.font, panelX, panelY, mouseX, mouseY, topTabEntries(), this.tab
			);

			Object[] left = this.tab.leftTabs();
			if (left.length > 0) {
				List<IconButtonBar.Entry> side = sideTabEntries();
				Object selectedLeft = selectedLeftKey();
				this.sideBar.drawLeftFrameTabs(
					graphics, this.font, panelX, panelY, panelH, mouseX, mouseY, side, selectedLeft
				);
			}

			// Draw before body tooltips so item / pet tips are not covered by the search box.
			positionPlayerSearch(graphics, panelX, panelY, panelW, panelH);

			renderBody(graphics, panelX + PAD, panelY + PAD, panelW - PAD * 2, panelH - PAD * 2, mouseX, mouseY, delta);

			String profileName = this.homePage.profileName();
			ProfileFetcher.ProfileChoice selectedChoice = selectedProfileChoice();
			String footerKey = profileName + "|" + (selectedChoice == null ? "" : selectedChoice.gameMode())
				+ "|" + this.profileMenuOpen;
			if (!footerKey.equals(this.cachedProfileFooterName)) {
				this.cachedProfileFooterName = footerKey;
				this.cachedProfileFooter = Component.translatable("betterpv.home.profile", profileName).getString();
			}
			this.profileFooterX = panelX + 2;
			this.profileFooterY = panelY + panelH + 3;
			this.profileFooterH = this.font.lineHeight;
			ItemStack footerBadge = profileModeBadge(selectedChoice);
			String footerArrow = this.profileChoices.size() > 1
				? (this.profileMenuOpen ? " ▲" : " ▼")
				: "";
			int footerTextW = this.font.width(this.cachedProfileFooter);
			int footerBadgeW = footerBadge.isEmpty() ? 0 : 2 + PROFILE_BADGE_SIZE;
			int footerArrowW = footerArrow.isEmpty() ? 0 : this.font.width(footerArrow);
			this.profileFooterW = footerTextW + footerBadgeW + footerArrowW;
			boolean footerHover = mouseX >= this.profileFooterX && mouseX < this.profileFooterX + this.profileFooterW
				&& mouseY >= this.profileFooterY && mouseY < this.profileFooterY + this.profileFooterH;
			int footerColor = footerHover && this.profileChoices.size() > 1 ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_MUTED;
			PvDraw.text(
				graphics, this.font, this.cachedProfileFooter,
				this.profileFooterX, this.profileFooterY,
				footerColor
			);
			int footerX = this.profileFooterX + footerTextW;
			if (!footerBadge.isEmpty()) {
				footerX += 2;
				int badgeY = this.profileFooterY + Math.max(0, (this.profileFooterH - PROFILE_BADGE_SIZE) / 2);
				MiningUi.drawItemIcon(graphics, footerBadge, footerX, badgeY, PROFILE_BADGE_SIZE);
				footerX += PROFILE_BADGE_SIZE;
			}
			if (!footerArrow.isEmpty()) {
				PvDraw.text(graphics, this.font, footerArrow, footerX, this.profileFooterY, footerColor);
			}
			if (this.profileMenuOpen) {
				updateProfileMenuHover(mouseX, mouseY);
				drawProfileMenu(graphics, mouseX, mouseY);
				drawProfileCoopFlyout(graphics, mouseX, mouseY);
			} else {
				this.profileMenuHoverChoice = null;
			}
			if (this.playerSearchErrorUntilMs > System.currentTimeMillis() && !this.playerSearchError.isBlank()) {
				PvDraw.text(
					graphics, this.font, this.playerSearchError,
					panelX + panelW - 2 - this.font.width(this.playerSearchError),
					panelY + panelH + 3 + SEARCH_H + 2,
					0xFFFF5555
				);
			}

			// Higher stratum than EditBox / search chrome so tall item tips are not covered
			// by the footer "Search player" field (vanilla deferred tips use the same pattern).
			graphics.nextStratum();
			renderDeferredTooltips(graphics, panelX, panelY, panelW, panelH, mouseX, mouseY);

			if (this.tab == PvTab.DUNGEONS) {
				this.dungeonPage.renderOverlay(graphics, this.font, this.width, this.height, mouseX, mouseY);
			} else if (this.tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
				this.homePage.renderSlayerOverlay(graphics, this.font, this.width, this.height, mouseX, mouseY);
			} else if (this.tab.isInventorySplit()) {
				this.inventoryPage.renderOverlay(graphics, this.font, this.width, this.height, mouseX, mouseY);
			}
		}

		if (scaled) {
			graphics.pose().popMatrix();
		}

		PvDraw.text(
			graphics,
			this.font,
			Component.translatable("betterpv.screen.flip_tip").getString(),
			4,
			this.height - this.font.lineHeight - 4,
			0xFFFFFF55
		);
	}

	private float openScale() {
		long elapsed = System.currentTimeMillis() - this.openAnimStartMs;
		if (elapsed >= OPEN_ANIM_MS) {
			return 1.0F;
		}
		float t = Math.max(0.0F, Math.min(1.0F, elapsed / (float) OPEN_ANIM_MS));
		// Cubic ease-out: snappy expand from center.
		float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
		return OPEN_SCALE_START + (1.0F - OPEN_SCALE_START) * eased;
	}

	private boolean uiInteractive() {
		return this.dataReady && openScale() >= 0.98F;
	}

	private void drawLoadingFace(GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH) {
		long elapsedMs = System.currentTimeMillis() - this.openAnimStartMs;
		List<LoadingEgg.Stage> stages = LoadingEgg.stagesUnlocked(elapsedMs);
		int cx = panelX + panelW / 2;
		int maxW = panelW - 24;
		int lineH = this.font.lineHeight + 3;

		long phase = (System.currentTimeMillis() / 400L) % 4L;
		String dots = switch ((int) phase) {
			case 1 -> ".";
			case 2 -> "..";
			case 3 -> "...";
			default -> "";
		};
		String loading = "Loading Data" + dots;

		if (elapsedMs >= LoadingEgg.BREAK_AT_MS) {
			scheduleBreakFinale();
			PvDraw.fill(g, panelX + 2, panelY + 2, panelW - 4, panelH - 4, 0xCC330000);
		} else if (!stages.isEmpty() && stages.get(stages.size() - 1).shout()) {
			PvDraw.fill(g, panelX + 4, panelY + 4, panelW - 8, panelH - 8, 0x33AA0000);
		}

		// Loading Data + every unlocked threat, stacked oldest → newest.
		int rows = 1 + stages.size();
		int blockH = rows * lineH - 3;
		int topY = panelY + Math.max(10, (panelH - blockH) / 2);
		PvDraw.textCentered(g, this.font, loading, cx, topY, PvDraw.COLOR_MUTED);
		int ty = topY + lineH;
		for (LoadingEgg.Stage stage : stages) {
			drawEggLine(g, stage.line(), cx, ty, maxW);
			ty += lineH;
		}
	}

	private void drawEggLine(GuiGraphicsExtractor g, Component line, int cx, int y, int maxW) {
		int tw = this.font.width(line);
		if (tw <= maxW || tw <= 0) {
			PvDraw.textCentered(g, this.font, line, cx, y);
			return;
		}
		float scale = maxW / (float) tw;
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		PvDraw.text(g, this.font, line, -tw / 2, 0);
		g.pose().popMatrix();
	}

	private void scheduleBreakFinale() {
		if (this.breakScheduled) {
			return;
		}
		this.breakScheduled = true;
		BetterPV.LOGGER.warn("Loading egg hit finale for {} - starting limbo + fake ban", this.requestedName);
		LoadingEggFinale.start();
	}

	private Object selectedLeftKey() {
		if (this.tab == PvTab.MUSEUM) {
			return this.museumSort;
		}
		return this.subSelection.get(this.tab);
	}

	private List<IconButtonBar.Entry> topTabEntries() {
		if (this.topTabEntries == null) {
			List<IconButtonBar.Entry> entries = new ArrayList<>();
			for (PvTab t : PvTab.values()) {
				entries.add(new IconButtonBar.Entry(t, t.icon(), t.label(), () -> {
					if (t != PvTab.DUNGEONS) {
						this.dungeonPage.blurField();
					}
					this.homePage.forceCloseSbLevelOverlay();
					this.tab = t;
					ProfileFetcher.prioritizeTab(t);
				}));
			}
			this.topTabEntries = List.copyOf(entries);
		}
		return this.topTabEntries;
	}

	private List<IconButtonBar.Entry> sideTabEntries() {
		if (this.cachedSideTab == this.tab && !this.sideTabEntries.isEmpty()) {
			return this.sideTabEntries;
		}
		Object[] left = this.tab.leftTabs();
		List<IconButtonBar.Entry> side = new ArrayList<>(left.length);
		for (Object entry : left) {
			if (entry instanceof PvSubTab sub) {
				side.add(new IconButtonBar.Entry(
					entry, sub.icon(), sub.label(),
					() -> {
						this.homePage.forceCloseSbLevelOverlay();
						this.subSelection.put(this.tab, sub);
					},
					sub.textureIcon(), sub.textureSize()
				));
			} else if (entry instanceof MuseumSort sort) {
				side.add(new IconButtonBar.Entry(entry, sort.icon(), sort.label(), () -> {
					this.museumSort = sort;
					this.museumPage.setSort(sort);
				}));
			}
		}
		this.cachedSideTab = this.tab;
		this.sideTabEntries = List.copyOf(side);
		return this.sideTabEntries;
	}

	private void prepareStandardBody() {
		hideInventorySearch();
		this.dungeonPage.blurField();
	}

	private PvSubTab activeSub(PvSubTab fallback) {
		return this.subSelection.getOrDefault(this.tab, fallback);
	}

	private void renderBody(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
		switch (this.tab) {
			case HOME -> {
				prepareStandardBody();
				PvSubTab homeSub = activeSub(PvSubTab.HOME_OVERVIEW);
				if (homeSub == PvSubTab.HOME_MISC) {
					this.homeMiscPage.render(
						g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height
					);
				} else {
					this.homePage.render(
						g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height,
						openScale(), this.openPivotX, this.openPivotY
					);
				}
			}
			case DUNGEONS -> {
				hideInventorySearch();
				this.dungeonPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case INVENTORIES -> {
				this.dungeonPage.blurField();
				renderInventorySplit(g, x, y, w, h, mouseX, mouseY, delta);
			}
			case BESTIARY -> {
				this.dungeonPage.blurField();
				renderBestiarySplit(g, x, y, w, h, mouseX, mouseY, delta);
			}
			case PETS -> {
				prepareStandardBody();
				this.petsPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case AUCTIONS -> {
				prepareStandardBody();
				this.auctionPage.render(
					g, this.font, activeSub(PvSubTab.AUCTION_STATS),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case COLLECTIONS -> {
				prepareStandardBody();
				this.collectionsPage.render(
					g, this.font, activeSub(PvSubTab.COLLECTIONS_LIST),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case GARDEN -> {
				prepareStandardBody();
				ensureGardenIsland();
				ensureGardenWeight();
				PvSubTab sub = activeSub(PvSubTab.GARDEN_OVERVIEW);
				if (sub == PvSubTab.GARDEN_JACOB) {
					ensureGardenContests();
				}
				this.gardenPage.render(g, this.font, sub, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case MINING -> {
				prepareStandardBody();
				this.miningPage.render(
					g, this.font, activeSub(PvSubTab.MINING_OVERVIEW),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case FORAGING -> {
				prepareStandardBody();
				this.foragingPage.render(
					g, this.font, activeSub(PvSubTab.FORAGING_OVERVIEW),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case FISHING -> {
				prepareStandardBody();
				this.fishingPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case CRIMSON -> {
				prepareStandardBody();
				this.crimsonPage.render(
					g, this.font, activeSub(PvSubTab.CRIMSON_OVERVIEW),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case RIFT -> {
				prepareStandardBody();
				this.riftPage.render(
					g, this.font, activeSub(PvSubTab.RIFT_OVERVIEW),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
			case MUSEUM -> {
				prepareStandardBody();
				this.museumPage.setSort(this.museumSort);
				ensureMuseum(false);
				this.museumPage.render(g, this.font, x, y, w, h, mouseX, mouseY, this.width, this.height);
			}
			case EVENTS -> {
				prepareStandardBody();
				ensureBingo(false);
				this.eventsPage.render(
					g, this.font, activeSub(PvSubTab.EVENTS_BINGO),
					x, y, w, h, mouseX, mouseY, this.width, this.height
				);
			}
		}
	}

	private void renderInventorySplit(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
		PvDraw.fill(g, x, y, w, SEARCH_H, 0xFF101018);
		g.outline(x, y, w, SEARCH_H, PvDraw.COLOR_BORDER);

		int bodyY = y + SEARCH_H + SEARCH_GAP;
		int bodyH = h - SEARCH_H - SEARCH_GAP;

		List<InventoryPane> panes = this.inventoryPage.visiblePanes();
		int[] rowWidths = inventoryRowWidths(panes.size());
		int btn = 22;
		int btnGap = 14;
		int pad = 10;
		int rows = rowWidths.length;
		// Keep the pre-Carnival selector width (5 columns) so extra icons wrap
		// inside the selector instead of shifting the inventory split.
		int maxCols = INV_SELECTOR_COLS;
		int gridW = maxCols * btn + (maxCols - 1) * btnGap;

		int gap = 8;
		int rightW = gridW + pad * 2;
		int leftW = w - rightW - gap;
		if (leftW < 150) {
			leftW = Math.max(130, (int) (w * 0.52));
			rightW = w - leftW - gap;
		}
		int rightX = x + leftW + gap;

		PvDraw.innerPanel(g, x, bodyY, leftW, bodyH);
		this.inventoryPage.setSearchQuery(this.inventorySearchQuery);
		this.inventoryPage.render(g, this.font, x, bodyY, leftW, bodyH, mouseX, mouseY, this.width, this.height);

		PvDraw.innerPanel(g, rightX, bodyY, rightW, bodyH);

		int startX = rightX + (rightW - gridW) / 2;
		int startY = bodyY + pad;
		int searchBoxY = bodyY + bodyH - pad - SEARCH_H;
		int gridClipBottom = Math.max(startY + btn, searchBoxY - pad);
		int gridAreaH = Math.max(btn, gridClipBottom - startY);
		int gridContentH = rows <= 0 ? 0 : rows * btn + Math.max(0, rows - 1) * btnGap;
		this.invSelectorMaxScroll = Math.max(0, gridContentH - gridAreaH);
		this.invSelectorScroll = Math.max(0, Math.min(this.invSelectorScroll, this.invSelectorMaxScroll));
		this.invSelectorX = rightX;
		this.invSelectorY = startY;
		this.invSelectorW = rightW;
		this.invSelectorH = gridAreaH;

		int index = 0;
		this.inventoryPaneTip = null;
		g.enableScissor(rightX + 1, startY, rightX + rightW - 1, gridClipBottom);
		for (int row = 0; row < rows && index < panes.size(); row++) {
			int width = rowWidths[row];
			int rowW = width * btn + (width - 1) * btnGap;
			int rowStartX = rightX + (rightW - rowW) / 2;
			for (int col = 0; col < width && index < panes.size(); col++) {
				InventoryPane pane = panes.get(index++);
				int bx = rowStartX + col * (btn + btnGap);
				int by = startY + row * (btn + btnGap) - this.invSelectorScroll;
				if (by + btn <= startY || by >= gridClipBottom) {
					continue;
				}
				boolean selected = pane == this.inventoryPage.pane();
				boolean hovered = mouseX >= bx && mouseX < bx + btn
					&& mouseY >= Math.max(by, startY) && mouseY < Math.min(by + btn, gridClipBottom);
				boolean searchHit = this.inventoryPage.searchHighlightsPane(pane);
				int bg = selected ? 0xFF2A3A55 : hovered ? 0xFF222230 : 0xFF16161E;
				int border = searchHit ? 0xFF55FF55 : selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
				PvDraw.fill(g, bx, by, btn, btn, bg);
				g.outline(bx, by, btn, btn, border);
				Identifier textureIcon = pane.textureIcon();
				if (textureIcon != null) {
					int ix = bx + (btn - 16) / 2;
					int iy = by + (btn - 16) / 2;
					g.blit(
						RenderPipelines.GUI_TEXTURED,
						textureIcon,
						ix, iy,
						0, 0,
						16, 16,
						16, 16,
						16, 16
					);
				} else {
					g.item(pane.icon(), bx + (btn - 16) / 2, by + (btn - 16) / 2);
				}
				this.inventoryBar.addHit(bx, by, btn, btn, () -> this.inventoryPage.setPane(pane));
				if (hovered) {
					// Defer tip until after all buttons / search so later icons don't cover it.
					this.inventoryPaneTip = pane.label();
					this.inventoryPaneTipX = bx - 4;
					this.inventoryPaneTipY = by + btn / 2;
				}
			}
		}
		g.disableScissor();

		int searchBoxX = rightX + pad;
		int searchBoxW = rightW - pad * 2;
		PvDraw.fill(g, searchBoxX, searchBoxY, searchBoxW, SEARCH_H, 0xFF101018);
		g.outline(
			searchBoxX,
			searchBoxY,
			searchBoxW,
			SEARCH_H,
			this.inventorySearch != null && this.inventorySearch.isFocused() ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER
		);
		layoutInventorySearch(searchBoxX, searchBoxY, searchBoxW);
		this.inventorySearch.extractWidgetRenderState(g, mouseX, mouseY, delta);
		// EditBox only draws its hint when unfocused; keep placeholder visible while empty.
		if (this.inventorySearch.getValue().isEmpty()) {
			String hint = Component.translatable("betterpv.inv.search_hint").getString();
			PvDraw.text(
				g,
				this.font,
				hint,
				this.inventorySearch.getX(),
				this.inventorySearch.getY(),
				PvDraw.COLOR_MUTED
			);
		}
	}

	private void renderBestiarySplit(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
		PvDraw.fill(g, x, y, w, SEARCH_H, 0xFF101018);
		g.outline(x, y, w, SEARCH_H, PvDraw.COLOR_BORDER);
		String header = "Tiers "
			+ FormatUtil.commas(this.bestiaryPage.totalUnlockedTiers())
			+ "/"
			+ FormatUtil.commas(this.bestiaryPage.totalMaxTiers())
			+ "  ·  Milestone "
			+ FormatUtil.commas(this.bestiaryPage.milestone());
		PvDraw.text(g, this.font, header, x + 6, y + (SEARCH_H - this.font.lineHeight) / 2, PvDraw.COLOR_MUTED);

		int bodyY = y + SEARCH_H + SEARCH_GAP;
		int bodyH = h - SEARCH_H - SEARCH_GAP;

		BestiaryData.ensureLoaded();
		List<BestiaryData.Category> cats = BestiaryData.categories();
		int[] rowWidths = bestiaryRowWidths(cats.size());
		int btn = 22;
		int btnGap = 10;
		int pad = 8;
		int rows = rowWidths.length;
		int maxCols = 0;
		for (int width : rowWidths) {
			maxCols = Math.max(maxCols, width);
		}
		int gridW = maxCols <= 0 ? btn : maxCols * btn + (maxCols - 1) * btnGap;

		int gap = 8;
		int rightW = gridW + pad * 2;
		int leftW = w - rightW - gap;
		if (leftW < 160) {
			leftW = Math.max(140, (int) (w * 0.58));
			rightW = w - leftW - gap;
		}
		int rightX = x + leftW + gap;

		PvDraw.innerPanel(g, x, bodyY, leftW, bodyH);
		this.bestiaryPage.setSearchQuery(this.inventorySearchQuery);
		this.bestiaryPage.render(g, this.font, x, bodyY, leftW, bodyH, mouseX, mouseY, this.width, this.height);

		PvDraw.innerPanel(g, rightX, bodyY, rightW, bodyH);

		int startY = bodyY + pad;
		int index = 0;
		this.bestiaryCategoryTip = null;
		for (int row = 0; row < rows && index < cats.size(); row++) {
			int width = rowWidths[row];
			int rowW = width * btn + (width - 1) * btnGap;
			int rowStartX = rightX + (rightW - rowW) / 2;
			for (int col = 0; col < width && index < cats.size(); col++) {
				BestiaryData.Category cat = cats.get(index++);
				int bx = rowStartX + col * (btn + btnGap);
				int by = startY + row * (btn + btnGap);
				boolean selected = cat.id().equals(this.bestiaryPage.categoryId());
				boolean hovered = mouseX >= bx && mouseX < bx + btn && mouseY >= by && mouseY < by + btn;
				boolean searchHit = this.bestiaryPage.searchHighlightsCategory(cat.id());
				int bg = selected ? 0xFF2A3A55 : hovered ? 0xFF222230 : 0xFF16161E;
				int border = searchHit ? 0xFF55FF55 : selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
				PvDraw.fill(g, bx, by, btn, btn, bg);
				g.outline(bx, by, btn, btn, border);
				ItemStack icon = this.bestiaryPage.categoryIcon(cat.id());
				if (!icon.isEmpty()) {
					g.item(icon, bx + (btn - 16) / 2, by + (btn - 16) / 2);
				}
				String catId = cat.id();
				this.inventoryBar.addHit(bx, by, btn, btn, () -> this.bestiaryPage.setCategory(catId));
				if (hovered) {
					this.bestiaryCategoryTip = Component.literal(cat.name());
					this.bestiaryCategoryTipX = bx - 4;
					this.bestiaryCategoryTipY = by + btn / 2;
				}
			}
		}

		int searchBoxY = bodyY + bodyH - pad - SEARCH_H;
		int searchBoxX = rightX + pad;
		int searchBoxW = Math.max(20, rightW - pad * 2);
		PvDraw.fill(g, searchBoxX, searchBoxY, searchBoxW, SEARCH_H, 0xFF101018);
		g.outline(
			searchBoxX,
			searchBoxY,
			searchBoxW,
			SEARCH_H,
			this.inventorySearch != null && this.inventorySearch.isFocused() ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER
		);
		layoutInventorySearch(searchBoxX, searchBoxY, searchBoxW);
		this.inventorySearch.extractWidgetRenderState(g, mouseX, mouseY, delta);
		if (this.inventorySearch.getValue().isEmpty()) {
			String hint = Component.translatable("betterpv.bestiary.search_hint").getString();
			PvDraw.text(
				g,
				this.font,
				hint,
				this.inventorySearch.getX(),
				this.inventorySearch.getY(),
				PvDraw.COLOR_MUTED
			);
		}
	}

	private static int[] inventoryRowWidths(int count) {
		if (count <= 0) {
			return new int[0];
		}
		int rows = (count + INV_SELECTOR_COLS - 1) / INV_SELECTOR_COLS;
		int[] widths = new int[rows];
		int remaining = count;
		for (int i = 0; i < rows; i++) {
			widths[i] = Math.min(INV_SELECTOR_COLS, remaining);
			remaining -= widths[i];
		}
		return widths;
	}

	private static boolean isDirectionalKey(int key) {
		return key == 262 || key == 263 || key == 264 || key == 265;
	}

	private boolean hitInvSelector(double mouseX, double mouseY) {
		return mouseX >= this.invSelectorX && mouseX < this.invSelectorX + this.invSelectorW
			&& mouseY >= this.invSelectorY && mouseY < this.invSelectorY + this.invSelectorH;
	}

	private static int[] bestiaryRowWidths(int count) {
		if (count <= 0) {
			return new int[0];
		}
		if (count <= 5) {
			return new int[] { count };
		}
		if (count <= 10) {
			return new int[] { 5, count - 5 };
		}
		if (count <= 15) {
			return new int[] { 5, 5, count - 10 };
		}
		if (count <= 19) {
			return new int[] { 5, 5, 5, count - 15 };
		}
		return new int[] { 5, 5, 5, 5, Math.max(1, count - 20) };
	}

	private void layoutInventorySearch(int x, int y, int w) {
		if (this.inventorySearch == null) {
			return;
		}
		int inset = 4;
		// Unbordered EditBox draws text at getY() (not vertically centred) - match the box.
		int textH = Math.max(8, this.font.lineHeight);
		int textY = y + Math.max(0, (SEARCH_H - textH) / 2);
		this.inventorySearch.setWidth(Math.max(20, w - inset * 2));
		this.inventorySearch.setHeight(textH);
		this.inventorySearch.setX(x + inset);
		this.inventorySearch.setY(textY);
		this.inventorySearch.setVisible(true);
	}

	private void hideInventorySearch() {
		if (this.inventorySearch == null) {
			return;
		}
		this.inventorySearch.setFocused(false);
		this.inventorySearch.setVisible(false);
	}

	private void positionPlayerSearch(GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH) {
		if (this.playerSearch == null) {
			return;
		}
		int boxW = 110;
		int inset = 4;
		int textH = Math.max(8, this.font.lineHeight);
		int boxX = panelX + panelW - boxW;
		int boxY = panelY + panelH + 2;
		boolean focused = this.playerSearch.isFocused();
		PvDraw.fill(g, boxX, boxY, boxW, SEARCH_H, 0xAA101018);
		g.outline(boxX, boxY, boxW, SEARCH_H, focused ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER);
		this.playerSearch.setWidth(Math.max(20, boxW - inset * 2));
		this.playerSearch.setHeight(textH);
		this.playerSearch.setX(boxX + inset);
		this.playerSearch.setY(boxY + Math.max(0, (SEARCH_H - textH) / 2));
		this.playerSearch.setVisible(true);
		this.playerSearch.extractWidgetRenderState(g, 0, 0, 0f);
		if (this.playerSearch.getValue().isEmpty() && !focused) {
			String hint = Component.translatable("betterpv.home.search_hint").getString();
			PvDraw.text(
				g, this.font, hint,
				this.playerSearch.getX(),
				this.playerSearch.getY(),
				PvDraw.COLOR_MUTED
			);
		}
	}

	private void hidePlayerSearch() {
		if (this.playerSearch == null) {
			return;
		}
		this.playerSearch.setFocused(false);
		this.playerSearch.setVisible(false);
	}

	private void renderDeferredTooltips(
		GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH, int mouseX, int mouseY
	) {
		boolean footerHover = mouseX >= this.profileFooterX && mouseX < this.profileFooterX + this.profileFooterW
			&& mouseY >= this.profileFooterY && mouseY < this.profileFooterY + this.profileFooterH;
		List<PvTooltip.Line> profileTip = profileSelectorTooltip(mouseX, mouseY, footerHover);
		if (profileTip != null && !this.profileMenuOpen) {
			PvTooltip.drawStyled(g, this.font, profileTip, mouseX, mouseY, this.width, this.height);
		}
		if (this.profileMenuOpen && profileMenuCombinedHover(mouseX, mouseY)) {
			return;
		}

		int bodyX = panelX + PAD;
		int bodyY = panelY + PAD;
		int bodyW = panelW - PAD * 2;
		int bodyH = panelH - PAD * 2;

		switch (this.tab) {
			case HOME -> {
				if (activeSub(PvSubTab.HOME_MISC) == PvSubTab.HOME_MISC) {
					this.homeMiscPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
				} else {
					this.homePage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
				}
			}
			case DUNGEONS -> this.dungeonPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case PETS -> this.petsPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case AUCTIONS -> this.auctionPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case COLLECTIONS -> this.collectionsPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case GARDEN -> this.gardenPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case MINING -> this.miningPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case FORAGING -> this.foragingPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case FISHING -> this.fishingPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case CRIMSON -> this.crimsonPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case RIFT -> this.riftPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case MUSEUM -> this.museumPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			case EVENTS -> this.eventsPage.renderTooltip(
				g, this.font, mouseX, mouseY, this.width, this.height, bodyX, bodyY, bodyW, bodyH
			);
			default -> { }
		}

		if (this.tab.isInventorySplit()) {
			this.inventoryPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			if (this.inventoryPaneTip != null) {
				this.inventoryBar.maybeTooltip(
					g,
					this.font,
					this.inventoryPaneTip,
					true,
					this.inventoryPaneTipX,
					this.inventoryPaneTipY
				);
			}
		}
		if (this.tab.isBestiarySplit()) {
			this.bestiaryPage.renderTooltip(g, this.font, mouseX, mouseY, this.width, this.height);
			if (this.bestiaryCategoryTip != null) {
				this.inventoryBar.maybeTooltip(
					g,
					this.font,
					this.bestiaryCategoryTip,
					true,
					this.bestiaryCategoryTipX,
					this.bestiaryCategoryTipY
				);
			}
		}
	}

	private record ProfileMenuLayout(int menuX, int menuY, int menuW, int menuH, int lineH) {
	}

	private ProfileMenuLayout profileMenuLayout() {
		int lineH = this.font.lineHeight + 4;
		int menuW = PROFILE_MENU_PAD * 2 + PROFILE_MENU_COUNTS_W;
		for (ProfileFetcher.ProfileChoice choice : this.profileChoices) {
			menuW = Math.max(menuW, profileChoiceLineWidth(choice) + PROFILE_MENU_PAD * 2);
		}
		menuW = Math.max(menuW, 96);
		int menuH = this.profileChoices.size() * lineH + 4;
		int menuX = this.profileFooterX;
		int menuY = this.profileFooterY + this.profileFooterH + 2;
		return new ProfileMenuLayout(menuX, menuY, menuW, menuH, lineH);
	}

	private ProfileFetcher.ProfileChoice profileMenuChoiceAt(int mouseX, int mouseY) {
		ProfileMenuLayout layout = profileMenuLayout();
		if (mouseX < layout.menuX() || mouseX >= layout.menuX() + layout.menuW()
			|| mouseY < layout.menuY() || mouseY >= layout.menuY() + layout.menuH()) {
			return null;
		}
		int cy = layout.menuY() + 2;
		for (ProfileFetcher.ProfileChoice choice : this.profileChoices) {
			if (mouseY >= cy && mouseY < cy + layout.lineH()) {
				return choice;
			}
			cy += layout.lineH();
		}
		return null;
	}

	private void updateProfileMenuHover(int mouseX, int mouseY) {
		ProfileFetcher.ProfileChoice rowHover = profileMenuChoiceAt(mouseX, mouseY);
		if (rowHover != null) {
			if (rowHover != this.profileMenuHoverChoice) {
				this.profileCoopFlyoutScroll = 0;
			}
			this.profileMenuHoverChoice = rowHover;
			return;
		}
		if (this.profileMenuHoverChoice != null && profileCoopFlyoutHover(mouseX, mouseY, this.profileMenuHoverChoice)) {
			return;
		}
		this.profileMenuHoverChoice = null;
		this.profileCoopFlyoutScroll = 0;
	}

	private int profileCoopFlyoutX(ProfileMenuLayout layout) {
		return layout.menuX() + layout.menuW() + 2 - PROFILE_COOP_FLYOUT_BRIDGE;
	}

	private int profileCoopFlyoutHeight(ProfileFetcher.ProfileChoice choice) {
		return profileCoopFlyoutContentBottom(choice);
	}

	private int profileCoopFlyoutContentBottom(ProfileFetcher.ProfileChoice choice) {
		if (choice == null) {
			return 0;
		}
		int cy = PROFILE_COOP_FLYOUT_PAD;
		cy += this.font.lineHeight + 3;
		if (choice.createdAtMs() > 0L) {
			if (!FormatUtil.prettyDate(choice.createdAtMs()).isBlank()) {
				cy += this.font.lineHeight + 1;
			}
			if (!FormatUtil.ago(choice.createdAtMs()).isBlank()) {
				cy += this.font.lineHeight + 1;
			}
		}
		cy += 2 + 1 + 4;
		if (choice.coop().currentMembers().isEmpty() && choice.coop().formerMembers().isEmpty()) {
			cy += this.font.lineHeight;
		} else {
			if (!choice.coop().currentMembers().isEmpty()) {
				cy += this.font.lineHeight + 4;
				cy += choice.coop().currentMembers().size() * PROFILE_COOP_FLYOUT_ROW;
				if (!choice.coop().formerMembers().isEmpty()) {
					cy += 2;
				}
			}
			if (!choice.coop().formerMembers().isEmpty()) {
				cy += this.font.lineHeight + 4;
				cy += choice.coop().formerMembers().size() * PROFILE_COOP_FLYOUT_ROW;
			}
		}
		return cy + PROFILE_COOP_FLYOUT_PAD;
	}

	private boolean profileCoopFlyoutHover(int mouseX, int mouseY, ProfileFetcher.ProfileChoice choice) {
		if (choice == null) {
			return false;
		}
		ProfileMenuLayout layout = profileMenuLayout();
		int flyoutX = profileCoopFlyoutX(layout);
		int flyoutY = layout.menuY();
		int flyoutW = PROFILE_COOP_FLYOUT_W;
		int flyoutH = Math.min(profileCoopFlyoutHeight(choice), this.height - flyoutY - 4);
		return mouseX >= flyoutX && mouseX < flyoutX + flyoutW + PROFILE_COOP_FLYOUT_BRIDGE
			&& mouseY >= flyoutY && mouseY < flyoutY + flyoutH;
	}

	private boolean profileMenuCombinedHover(int mouseX, int mouseY) {
		ProfileMenuLayout layout = profileMenuLayout();
		boolean inMenu = mouseX >= layout.menuX() && mouseX < layout.menuX() + layout.menuW()
			&& mouseY >= layout.menuY() && mouseY < layout.menuY() + layout.menuH();
		if (inMenu) {
			return true;
		}
		return this.profileMenuHoverChoice != null
			&& profileCoopFlyoutHover(mouseX, mouseY, this.profileMenuHoverChoice);
	}

	private void drawProfileMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (this.profileChoices.size() <= 1) {
			return;
		}
		ProfileMenuLayout layout = profileMenuLayout();
		PvDraw.fill(g, layout.menuX(), layout.menuY(), layout.menuW(), layout.menuH(), 0xF0101018);
		g.outline(layout.menuX(), layout.menuY(), layout.menuW(), layout.menuH(), PvDraw.COLOR_BORDER);
		int cy = layout.menuY() + 2;
		for (ProfileFetcher.ProfileChoice choice : this.profileChoices) {
			boolean hover = choice == this.profileMenuHoverChoice
				|| profileMenuChoiceAt(mouseX, mouseY) == choice;
			if (hover || choice.selected()) {
				PvDraw.fill(
					g,
					layout.menuX() + 1,
					cy,
					layout.menuW() - 2,
					layout.lineH(),
					hover ? 0x33FFFFFF : 0x22AA88FF
				);
			}
			PvDraw.text(
				g, this.font, choice.cuteName(),
				layout.menuX() + PROFILE_MENU_PAD, cy + 2,
				choice.selected() ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_TEXT
			);
			ItemStack badge = profileModeBadge(choice);
			if (!badge.isEmpty()) {
				int badgeX = layout.menuX() + PROFILE_MENU_PAD + this.font.width(choice.cuteName()) + 3;
				int badgeY = cy + 1 + Math.max(0, (layout.lineH() - 2 - PROFILE_BADGE_SIZE) / 2);
				MiningUi.drawItemIcon(g, badge, badgeX, badgeY, PROFILE_BADGE_SIZE);
			}
			drawProfileCoopCounts(g, choice, layout.menuX(), layout.menuW(), cy + 2);
			cy += layout.lineH();
		}
	}

	private void drawProfileCoopCounts(
		GuiGraphicsExtractor g,
		ProfileFetcher.ProfileChoice choice,
		int menuX,
		int menuW,
		int textY
	) {
		int x = menuX + menuW - PROFILE_MENU_PAD;
		if (choice.coop().soloProfile()) {
			String solo = "Solo";
			PvDraw.text(g, this.font, solo, x - this.font.width(solo), textY, PvDraw.COLOR_MUTED);
			return;
		}
		String formerText = String.valueOf(choice.coop().formerCount());
		x -= this.font.width(formerText);
		PvDraw.text(g, this.font, formerText, x, textY, COLOR_COOP_FORMER);
		x -= 2;
		x -= PROFILE_COOP_ICON;
		drawCoopMemberIcon(g, x, textY - 1, COLOR_COOP_FORMER);
		x -= 8;
		String currentText = String.valueOf(choice.coop().currentOthers());
		x -= this.font.width(currentText);
		PvDraw.text(g, this.font, currentText, x, textY, COLOR_COOP_CURRENT);
		x -= 2;
		x -= PROFILE_COOP_ICON;
		drawCoopMemberIcon(g, x, textY - 1, COLOR_COOP_CURRENT);
	}

	private static void drawCoopMemberIcon(GuiGraphicsExtractor g, int x, int y, int tint) {
		int body = tint & 0x00FFFFFF;
		PvDraw.fill(g, x + 2, y, 4, 4, 0xFF000000 | body);
		PvDraw.fill(g, x + 1, y + 4, 6, 5, 0xFF000000 | body);
	}

	private void drawProfileCoopFlyout(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		this.coopMemberHits.clear();
		ProfileFetcher.ProfileChoice choice = this.profileMenuHoverChoice;
		if (choice == null) {
			return;
		}
		ProfileMenuLayout layout = profileMenuLayout();
		int flyoutX = profileCoopFlyoutX(layout) + PROFILE_COOP_FLYOUT_BRIDGE;
		int flyoutY = layout.menuY();
		int flyoutW = PROFILE_COOP_FLYOUT_W;
		int contentH = profileCoopFlyoutHeight(choice);
		int flyoutH = Math.min(contentH, this.height - flyoutY - 4);
		PvDraw.fill(g, flyoutX, flyoutY, flyoutW, flyoutH, 0xF0101018);
		g.outline(flyoutX, flyoutY, flyoutW, flyoutH, PvDraw.COLOR_BORDER);

		int innerX = flyoutX + PROFILE_COOP_FLYOUT_PAD;
		int innerW = flyoutW - PROFILE_COOP_FLYOUT_PAD * 2;
		int cy = flyoutY + PROFILE_COOP_FLYOUT_PAD - this.profileCoopFlyoutScroll;

		g.enableScissor(flyoutX + 1, flyoutY + 1, flyoutX + flyoutW - 1, flyoutY + flyoutH - 1);
		PvDraw.text(g, this.font, choice.cuteName(), innerX, cy, PvDraw.COLOR_TEXT);
		ItemStack badge = profileModeBadge(choice);
		if (!badge.isEmpty()) {
			int badgeX = innerX + this.font.width(choice.cuteName()) + 3;
			MiningUi.drawItemIcon(g, badge, badgeX, cy - 1, PROFILE_BADGE_SIZE);
		}
		cy += this.font.lineHeight + 3;
		if (choice.createdAtMs() > 0L) {
			String date = FormatUtil.prettyDate(choice.createdAtMs());
			String age = FormatUtil.ago(choice.createdAtMs());
			if (!date.isBlank()) {
				drawFlyoutMetaRow(g, "Created", date, PvDraw.COLOR_TEXT, innerX, innerW, cy);
				cy += this.font.lineHeight + 1;
			}
			if (!age.isBlank()) {
				drawFlyoutMetaRow(g, "Age", age, PvDraw.COLOR_GOLD, innerX, innerW, cy);
				cy += this.font.lineHeight + 1;
			}
		}
		cy += 2;
		PvDraw.fill(g, innerX, cy, innerW, 1, PvDraw.COLOR_DIVIDER);
		cy += 4;

		if (choice.coop().currentMembers().isEmpty() && choice.coop().formerMembers().isEmpty()) {
			PvDraw.text(g, this.font, "Solo profile", innerX, cy, PvDraw.COLOR_MUTED);
			g.disableScissor();
			return;
		}
		if (!choice.coop().currentMembers().isEmpty()) {
			PvDraw.text(g, this.font, "Current members", innerX, cy, COLOR_COOP_CURRENT);
			cy += this.font.lineHeight + 4;
			for (ProfileFetcher.CoopMemberRef member : choice.coop().currentMembers()) {
				drawCoopFlyoutMember(g, member, innerX, innerW, cy, false, mouseX, mouseY, flyoutY, flyoutH);
				cy += PROFILE_COOP_FLYOUT_ROW;
			}
			cy += 2;
		}
		if (!choice.coop().formerMembers().isEmpty()) {
			PvDraw.text(g, this.font, "Former members", innerX, cy, COLOR_COOP_FORMER);
			cy += this.font.lineHeight + 4;
			for (ProfileFetcher.CoopMemberRef member : choice.coop().formerMembers()) {
				drawCoopFlyoutMember(g, member, innerX, innerW, cy, true, mouseX, mouseY, flyoutY, flyoutH);
				cy += PROFILE_COOP_FLYOUT_ROW;
			}
		}
		g.disableScissor();
	}

	private void drawFlyoutMetaRow(
		GuiGraphicsExtractor g,
		String label,
		String value,
		int valueColor,
		int x,
		int w,
		int y
	) {
		PvDraw.text(g, this.font, label, x, y, PvDraw.COLOR_MUTED);
		int valueW = this.font.width(value);
		PvDraw.text(g, this.font, value, x + w - valueW, y, valueColor);
	}

	private void drawCoopFlyoutMember(
		GuiGraphicsExtractor g,
		ProfileFetcher.CoopMemberRef member,
		int x,
		int w,
		int y,
		boolean former,
		int mouseX,
		int mouseY,
		int flyoutY,
		int flyoutH
	) {
		boolean visible = y + PROFILE_COOP_FLYOUT_ROW > flyoutY && y < flyoutY + flyoutH;
		boolean resolved = ProfileFetcher.coopNameResolved(member);
		boolean hover = visible && resolved
			&& mouseX >= x && mouseX < x + w
			&& mouseY >= y && mouseY < y + PROFILE_COOP_FLYOUT_ROW;
		if (hover) {
			PvDraw.fill(g, x - 2, y - 1, w + 4, PROFILE_COOP_FLYOUT_ROW, 0x33FFFFFF);
		}
		UUID uuid = HypixelApiClient.parseUndashedUuid(member.uuid());
		if (uuid != null) {
			MiningUi.drawItemIcon(g, coopMemberHead(uuid), x, y - 1, PROFILE_COOP_ICON);
		}
		String name = ProfileFetcher.coopMemberDisplayName(member);
		int nameColor = former ? COLOR_COOP_FORMER : COLOR_COOP_CURRENT;
		if (!resolved) {
			nameColor = PvDraw.COLOR_MUTED;
		}
		int nameX = x + PROFILE_COOP_ICON + 4;
		int maxW = w - PROFILE_COOP_ICON - 4;
		String clipped = this.font.plainSubstrByWidth(name, maxW);
		PvDraw.text(g, this.font, clipped, nameX, y, nameColor);
		if (visible && resolved) {
			this.coopMemberHits.add(new CoopMemberHit(x, y, w, PROFILE_COOP_FLYOUT_ROW, member.uuid(), name));
		}
	}

	private static ItemStack coopMemberHead(UUID uuid) {
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		if (uuid == null) {
			return head;
		}
		try {
			head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(uuid));
		} catch (Throwable ignored) {
			try {
				head.set(
					DataComponents.PROFILE,
					ResolvableProfile.createResolved(new GameProfile(uuid, "Player"))
				);
			} catch (Throwable ignored2) {
			}
		}
		return head;
	}

	private int profileChoiceLineWidth(ProfileFetcher.ProfileChoice choice) {
		int w = this.font.width(choice.cuteName());
		if (!profileModeBadge(choice).isEmpty()) {
			w += 3 + PROFILE_BADGE_SIZE;
		}
		if (choice.coop().soloProfile()) {
			w += 8 + this.font.width("Solo");
		} else {
			w += 8 + PROFILE_MENU_COUNTS_W;
		}
		return w;
	}

	private static ItemStack profileModeBadge(ProfileFetcher.ProfileChoice choice) {
		if (choice == null || choice.gameMode() == null || choice.gameMode().isBlank()) {
			return ItemStack.EMPTY;
		}
		return switch (choice.gameMode().toLowerCase(Locale.ROOT)) {
			case "ironman" -> new ItemStack(Items.SHIELD);
			case "bingo" -> {
				ItemStack card = SkyBlockItemFactory.iconStack("BINGO_CARD");
				yield card == null || card.isEmpty() ? new ItemStack(Items.FILLED_MAP) : card;
			}
			case "stranded", "island" -> new ItemStack(Items.OAK_SAPLING);
			default -> ItemStack.EMPTY;
		};
	}

	private ProfileFetcher.ProfileChoice selectedProfileChoice() {
		for (ProfileFetcher.ProfileChoice choice : this.profileChoices) {
			if (choice.selected()) {
				return choice;
			}
		}
		return this.profileChoices.isEmpty() ? null : this.profileChoices.get(0);
	}

	private List<PvTooltip.Line> profileSelectorTooltip(int mouseX, int mouseY, boolean footerHover) {
		if (this.profileMenuOpen && this.profileChoices.size() > 1) {
			ProfileFetcher.ProfileChoice hover = profileMenuChoiceAt(mouseX, mouseY);
			if (hover != null) {
				return profileCreatedTooltip(hover);
			}
			return null;
		}
		if (footerHover) {
			return profileCreatedTooltip(selectedProfileChoice());
		}
		return null;
	}

	private static List<PvTooltip.Line> profileCreatedTooltip(ProfileFetcher.ProfileChoice choice) {
		if (choice == null || choice.createdAtMs() <= 0L) {
			return null;
		}
		String date = FormatUtil.prettyDate(choice.createdAtMs());
		String age = FormatUtil.ago(choice.createdAtMs());
		List<PvTooltip.Line> lines = new ArrayList<>(4);
		lines.add(PvTooltip.Line.title(choice.cuteName(), PvDraw.COLOR_TEXT));
		lines.add(PvTooltip.Line.divider());
		if (!date.isBlank()) {
			lines.add(PvTooltip.Line.row("Created", PvDraw.COLOR_MUTED, date, PvDraw.COLOR_TEXT));
		}
		if (!age.isBlank()) {
			lines.add(PvTooltip.Line.row("Age", PvDraw.COLOR_MUTED, age, PvDraw.COLOR_GOLD));
		}
		String mode = choice.gameModeLabel();
		if (!mode.isBlank()) {
			lines.add(PvTooltip.Line.row("Mode", PvDraw.COLOR_MUTED, mode, PvDraw.COLOR_GOLD));
		}
		return lines;
	}

	private boolean clickProfileFooter(double mx, double my) {
		if (this.profileChoices.size() <= 1) {
			return false;
		}
		if (mx >= this.profileFooterX && mx < this.profileFooterX + this.profileFooterW
			&& my >= this.profileFooterY && my < this.profileFooterY + this.profileFooterH) {
			this.profileMenuOpen = !this.profileMenuOpen;
			if (!this.profileMenuOpen) {
				this.profileMenuHoverChoice = null;
			}
			return true;
		}
		if (!this.profileMenuOpen) {
			return false;
		}
		if (!profileMenuCombinedHover((int) mx, (int) my)) {
			this.profileMenuOpen = false;
			this.profileMenuHoverChoice = null;
			this.coopMemberHits.clear();
			return true;
		}
		ProfileFetcher.ProfileChoice choice = profileMenuChoiceAt((int) mx, (int) my);
		if (choice == null) {
			return clickCoopFlyoutMember(mx, my);
		}
		this.profileMenuOpen = false;
		this.profileMenuHoverChoice = null;
		this.coopMemberHits.clear();
		if (!choice.selected()) {
			switchProfile(choice.profileId());
		}
		return true;
	}

	private boolean clickCoopFlyoutMember(double mx, double my) {
		for (CoopMemberHit hit : this.coopMemberHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				openCoopMemberProfile(hit.name);
				return true;
			}
		}
		return true;
	}

	private void openCoopMemberProfile(String name) {
		if (name == null || name.isBlank()) {
			return;
		}
		String current = this.homePage.playerName();
		if (current != null && current.equalsIgnoreCase(name)) {
			return;
		}
		Minecraft.getInstance().setScreenAndShow(new ProfileViewerScreen(name));
	}

	private record CoopMemberHit(int x, int y, int w, int h, String uuid, String name) {
	}

	private void switchProfile(String nextProfileId) {
		if (nextProfileId == null || nextProfileId.isBlank() || this.profilesRoot == null || this.playerUuid == null) {
			return;
		}
		if (nextProfileId.equals(this.profileId)) {
			return;
		}
		String name = this.homePage.playerName();
		UUID uuid = this.playerUuid;
		JsonObject root = this.profilesRoot;
		int generation = ++this.loadGeneration;
		ProfileFetcher.switchToProfile(name, uuid, root, nextProfileId, updated -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this || generation != this.loadGeneration) {
					return;
				}
				if (updated == null || !updated.ok()) {
					return;
				}
				applyLoadedProfile(updated);
			});
		}).whenComplete((loaded, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this || generation != this.loadGeneration) {
					return;
				}
				if (error != null || loaded == null || !loaded.ok()) {
					BetterPV.LOGGER.warn(
						"Profile switch failed for {} -> {}",
						name,
						nextProfileId,
						error
					);
					// Keep prior Home visible; do not force loading-egg for a failed switch.
					return;
				}
				applyLoadedProfile(loaded);
				this.dataReady = true;
			});
		});
	}

	private void submitPlayerSearch() {
		if (this.playerSearch == null) {
			return;
		}
		String trimmed = this.playerSearch.getValue() == null ? "" : this.playerSearch.getValue().trim();
		if (trimmed.isEmpty()) {
			this.playerSearchError = Component.translatable("betterpv.home.search_empty").getString();
			this.playerSearchErrorUntilMs = System.currentTimeMillis() + 2_500L;
			return;
		}
		this.playerSearchError = "";
		Minecraft.getInstance().setScreenAndShow(new ProfileViewerScreen(trimmed));
	}

	private void ensurePlayerRank() {
		if (this.playerUuid == null || this.playerRankFetchStarted) {
			return;
		}
		this.playerRankFetchStarted = true;
		UUID uuid = this.playerUuid;
		HypixelApiClient.player(uuid).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					this.homeMiscPage.applyFreeCookie(0L);
					return;
				}
				this.homePage.applyPlayerRank(opt.get());
				long freeCookie = 0L;
				var player = opt.get();
				if (player.has("skyblock_free_cookie") && player.get("skyblock_free_cookie").isJsonPrimitive()) {
					try {
						freeCookie = (long) player.get("skyblock_free_cookie").getAsDouble();
					} catch (Exception ignored) {
					}
				}
				this.homeMiscPage.applyFreeCookie(freeCookie);
			});
		});
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click == null) {
			return super.mouseClicked(click, doubled);
		}
		if (!uiInteractive()) {
			return true;
		}
		double mx = click.x();
		double my = click.y();
		if (clickProfileFooter(mx, my)) {
			return true;
		}
		if (routePageClick(mx, my)) {
			return true;
		}
		if (this.topBar.click(mx, my) || this.sideBar.click(mx, my) || this.inventoryBar.click(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
			if (this.homePage.slayerMouseClicked(mx, my)) {
				return true;
			}
			if (this.homePage.clickSbLevelPanel(mx, my)) {
				return true;
			}
			if (this.homePage.isSbLevelOverlayActive()) {
				return true;
			}
			if (this.homePage.hitName(mx, my)) {
				ensureUsernameHistory();
				return true;
			}
			if (this.homePage.hitStatus(mx, my)) {
				ensurePlayerStatus();
				return true;
			}
			if (this.homePage.clickWeight(mx, my)) {
				return true;
			}
			if (this.homePage.clickNetworth(mx, my, click.button())) {
				return true;
			}
			if (this.homePage.clickLeftPanel(mx, my)) {
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	private boolean routePageClick(double mx, double my) {
		return switch (this.tab) {
			case HOME -> activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_MISC
				&& this.homeMiscPage.mouseClicked(mx, my);
			case DUNGEONS -> this.dungeonPage.mouseClicked(mx, my);
			case MINING -> this.miningPage.mouseClicked(mx, my);
			case FORAGING -> this.foragingPage.mouseClicked(mx, my);
			case FISHING -> this.fishingPage.mouseClicked(mx, my);
			case CRIMSON -> this.crimsonPage.mouseClicked(mx, my);
			case RIFT -> this.riftPage.mouseClicked(mx, my, activeSub(PvSubTab.RIFT_OVERVIEW));
			case MUSEUM -> {
				if (this.museumPage.clickRefresh(mx, my)) {
					ensureMuseum(true);
					yield true;
				}
				yield false;
			}
			case GARDEN -> this.gardenPage.mouseClicked(mx, my);
			case PETS -> this.petsPage.mouseClicked(mx, my);
			case AUCTIONS -> this.auctionPage.mouseClicked(mx, my, activeSub(PvSubTab.AUCTION_STATS));
			case COLLECTIONS -> this.collectionsPage.mouseClicked(mx, my, activeSub(PvSubTab.COLLECTIONS_LIST));
			case INVENTORIES -> this.inventoryPage.mouseClicked(mx, my);
			default -> false;
		};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!uiInteractive()) {
			return true;
		}
		if (this.profileMenuOpen
			&& this.profileMenuHoverChoice != null
			&& profileCoopFlyoutHover((int) mouseX, (int) mouseY, this.profileMenuHoverChoice)) {
			int contentH = profileCoopFlyoutHeight(this.profileMenuHoverChoice);
			ProfileMenuLayout layout = profileMenuLayout();
			int visibleH = Math.min(contentH, this.height - layout.menuY() - 4);
			int maxScroll = Math.max(0, contentH - visibleH);
			this.profileCoopFlyoutScroll = Math.max(
				0,
				Math.min(maxScroll, this.profileCoopFlyoutScroll - (int) Math.round(scrollY * 12))
			);
			return true;
		}
		if (routePageScroll(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean routePageScroll(double mouseX, double mouseY, double scrollY) {
		return switch (this.tab) {
			case HOME -> {
				if (activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_MISC) {
					yield this.homeMiscPage.mouseScrolled(mouseX, mouseY, scrollY);
				}
				yield this.homePage.mouseScrolled(mouseX, mouseY, scrollY);
			}
			case MUSEUM -> this.museumPage.mouseScrolled(mouseX, mouseY, scrollY);
			case DUNGEONS -> this.dungeonPage.mouseScrolled(scrollY);
			case PETS -> this.petsPage.mouseScrolled(scrollY);
			case AUCTIONS -> this.auctionPage.mouseScrolled(mouseX, mouseY, scrollY);
			case COLLECTIONS -> this.collectionsPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.COLLECTIONS_LIST));
			case GARDEN -> this.gardenPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.GARDEN_OVERVIEW));
			case MINING -> this.miningPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.MINING_OVERVIEW));
			case FORAGING -> this.foragingPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.FORAGING_OVERVIEW));
			case CRIMSON -> this.crimsonPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.CRIMSON_OVERVIEW));
			case RIFT -> this.riftPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.RIFT_OVERVIEW));
			case BESTIARY -> this.bestiaryPage.mouseScrolled(mouseX, mouseY, scrollY);
			case EVENTS -> this.eventsPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.EVENTS_BINGO));
			case INVENTORIES -> {
				if (hitInvSelector(mouseX, mouseY) && this.invSelectorMaxScroll > 0) {
					int next = Math.max(0, Math.min(
						this.invSelectorMaxScroll,
						this.invSelectorScroll - (int) Math.round(scrollY * 18)
					));
					if (next != this.invSelectorScroll) {
						this.invSelectorScroll = next;
						yield true;
					}
				}
				yield this.inventoryPage.mouseScrolled(mouseX, mouseY, scrollY);
			}
			default -> false;
		};
	}

	private void ensureUsernameHistory() {
		if (this.playerUuid == null) {
			this.homePage.applyUsernameHistory(UsernameHistory.error("Missing player"));
			return;
		}
		// Allow re-click refresh when already READY/ERROR so a truncated prior fetch can be replaced.
		if (this.homePage.usernameHistory().state() == UsernameHistory.State.LOADING) {
			return;
		}
		this.usernameHistoryFetchStarted = true;
		this.homePage.applyUsernameHistory(UsernameHistory.loading());
		UUID uuid = this.playerUuid;
		HypixelApiClient.usernameHistory(uuid).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					this.homePage.applyUsernameHistory(UsernameHistory.error("History unavailable"));
					return;
				}
				List<UsernameHistory.Entry> entries = new ArrayList<>();
				JsonArray arr = opt.get();
				for (JsonElement el : arr) {
					if (el == null || !el.isJsonObject()) {
						continue;
					}
					JsonObject obj = el.getAsJsonObject();
					String name = obj.has("username") && obj.get("username").isJsonPrimitive()
						? obj.get("username").getAsString() : "";
					String changed = obj.has("changed_at") && obj.get("changed_at").isJsonPrimitive()
						? obj.get("changed_at").getAsString() : "";
					if (!name.isBlank()) {
						entries.add(new UsernameHistory.Entry(name, changed));
					}
				}
				this.homePage.applyUsernameHistory(UsernameHistory.ready(entries));
			});
		});
	}

	private void ensureGuild() {
		if (this.playerUuid == null) {
			this.homeMiscPage.applyGuild(GuildStatus.error("Missing player"));
			return;
		}
		if (this.homeMiscPage.guild().state() == GuildStatus.State.LOADING
			|| this.homeMiscPage.guild().state() == GuildStatus.State.READY) {
			return;
		}
		this.homeMiscPage.applyGuild(GuildStatus.loading());
		UUID uuid = this.playerUuid;
		HypixelApiClient.guild(uuid).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					this.homeMiscPage.applyGuild(GuildStatus.error("Guild unavailable"));
					return;
				}
				this.homeMiscPage.applyGuild(GuildStatus.fromHypixel(
					opt.get(),
					HypixelApiClient.undashed(uuid)
				));
			});
		});
	}

	private void ensurePlayerStatus() {
		if (this.playerUuid == null) {
			this.homePage.applyPlayerStatus(PlayerStatus.error("Missing player"));
			return;
		}
		if (this.homePage.playerStatus().state() == PlayerStatus.State.LOADING) {
			return;
		}
		this.statusFetchStarted = true;
		this.homePage.applyPlayerStatus(PlayerStatus.loading());
		UUID uuid = this.playerUuid;
		HypixelApiClient.status(uuid).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					this.homePage.applyPlayerStatus(PlayerStatus.error("Status unavailable"));
					return;
				}
				JsonObject root = opt.get();
				JsonObject session = root.has("session") && root.get("session").isJsonObject()
					? root.getAsJsonObject("session")
					: root;
				boolean online = session.has("online") && session.get("online").isJsonPrimitive()
					&& session.get("online").getAsBoolean();
				if (!online) {
					this.homePage.applyPlayerStatus(PlayerStatus.offline());
					return;
				}
				String game = session.has("gameType") && session.get("gameType").isJsonPrimitive()
					? session.get("gameType").getAsString() : "";
				String mode = session.has("mode") && session.get("mode").isJsonPrimitive()
					? session.get("mode").getAsString() : "";
				String map = session.has("map") && session.get("map").isJsonPrimitive()
					? session.get("map").getAsString() : "";
				this.homePage.applyPlayerStatus(PlayerStatus.online(game, mode, map));
			});
		});
	}

	private void ensureGardenIsland() {
		if (this.gardenFetchStarted) {
			return;
		}
		GardenSnapshot current = this.gardenPage.snapshot();
		if (current.islandLoaded()) {
			return;
		}
		String id = this.profileId;
		if (id == null || id.isBlank()) {
			this.gardenPage.apply(current.withIslandError("Missing profile id"));
			this.gardenFetchStarted = true;
			return;
		}
		this.gardenFetchStarted = true;
		this.gardenPage.apply(current.withIslandLoading());
		HypixelApiClient.skyblockGarden(id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this) {
					return;
				}
				GardenSnapshot base = this.gardenPage.snapshot();
				if (error != null) {
					BetterPV.LOGGER.warn("Garden fetch failed for profile {}", id, error);
					this.gardenPage.apply(base.withIslandError(
						error.getMessage() == null ? "Garden fetch failed" : error.getMessage()
					));
					return;
				}
				if (opt == null || opt.isEmpty()) {
					this.gardenPage.apply(base.withIslandError("Garden unavailable"));
					return;
				}
				try {
					this.gardenPage.apply(base.withIsland(opt.get()));
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Garden parse failed for profile {}", id, exception);
					this.gardenPage.apply(base.withIslandError("Garden parse failed"));
				}
			});
		});
	}

	private void ensureGardenContests() {
		if (this.gardenContestsFetchStarted) {
			return;
		}
		GardenSnapshot current = this.gardenPage.snapshot();
		UUID uuid = this.playerUuid;
		String id = this.profileId;
		if (uuid == null || id == null || id.isBlank()) {
			this.gardenPage.apply(current.withContestsError("Missing profile id"));
			this.gardenContestsFetchStarted = true;
			return;
		}
		this.gardenContestsFetchStarted = true;
		this.gardenPage.apply(current.withContestsLoading());
		EliteBotApiClient.contests(uuid, id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this) {
					return;
				}
				GardenSnapshot base = this.gardenPage.snapshot();
				if (error != null) {
					BetterPV.LOGGER.warn("Elite contests failed for {}", id, error);
					if (!base.contests().isEmpty()) {
						this.gardenPage.patch(base.withContestsReady());
					} else {
						this.gardenPage.patch(base.withContestsError(
							error.getMessage() == null ? "Contests unavailable" : error.getMessage()
						));
					}
					return;
				}
				if (opt == null || opt.isEmpty()) {
					// Keep Hypixel contest list if Elite miss.
					if (!base.contests().isEmpty()) {
						this.gardenPage.patch(base.withContestsReady());
					} else {
						this.gardenPage.patch(base.withContestsError("Contests unavailable"));
					}
					return;
				}
				try {
					this.gardenPage.patch(base.withEliteContests(opt.get()));
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Elite contests parse failed for {}", id, exception);
					this.gardenPage.patch(base.withContestsError("Contest parse failed"));
				}
			});
		});
	}

	private void ensureGardenWeight() {
		if (this.gardenWeightFetchStarted) {
			return;
		}
		GardenSnapshot current = this.gardenPage.snapshot();
		UUID uuid = this.playerUuid;
		String id = this.profileId;
		if (uuid == null || id == null || id.isBlank()) {
			this.gardenPage.apply(current.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Missing profile id")));
			this.gardenWeightFetchStarted = true;
			return;
		}
		this.gardenWeightFetchStarted = true;
		this.gardenPage.patch(current.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.pending()));
		EliteBotApiClient.weight(uuid, id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this) {
					return;
				}
				GardenSnapshot base = this.gardenPage.snapshot();
				if (error != null) {
					BetterPV.LOGGER.warn("Elite weight failed for {}", id, error);
					this.gardenPage.patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed(
						error.getMessage() == null ? "Weight unavailable" : error.getMessage()
					)));
					return;
				}
				if (opt == null || opt.isEmpty()) {
					this.gardenPage.patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Weight unavailable")));
					return;
				}
				try {
					this.gardenPage.patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.fromElite(opt.get())));
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Elite weight parse failed for {}", id, exception);
					this.gardenPage.patch(base.withFarmingWeight(GardenSnapshot.FarmingWeightInfo.failed("Weight parse failed")));
				}
			});
		});
	}

	private void ensureMuseum(boolean force) {
		UUID uuid = this.playerUuid;
		String id = this.profileId;
		if (uuid == null || id == null || id.isBlank()) {
			this.museumPage.applyError("Missing profile id");
			this.museumFetchStarted = true;
			return;
		}
		MuseumCache.Entry cached = MuseumCache.get(uuid, id);
		if (!force && cached != null) {
			// applyMuseum no-ops when the same cached member is already applied.
			this.museumPage.applyMuseum(cached.museumMember());
			this.museumFetchStarted = true;
			return;
		}
		if (this.museumFetchStarted && !force) {
			return;
		}
		if (force && !MuseumCache.canRefresh(uuid, id)) {
			this.museumPage.applyError("Refresh ready in " + dev.vy.betterpv.client.data.FormatUtil.prettySpan(
				MuseumCache.refreshReadyInMs(uuid, id)
			));
			return;
		}
		this.museumFetchStarted = true;
		this.museumPage.applyLoading();
		HypixelApiClient.skyblockMuseum(uuid, id).whenComplete((opt, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (client.gui.screen() != this) {
					return;
				}
				if (error != null || opt == null || opt.isEmpty()) {
					BetterPV.LOGGER.warn("Museum fetch failed for {}", id, error);
					this.museumPage.applyError(error == null || error.getMessage() == null ? "Museum unavailable" : error.getMessage());
					return;
				}
				try {
					String undashed = HypixelApiClient.undashed(uuid);
					var member = ProfileFetcher.findMuseumMember(opt.get(), id, undashed);
					if (member == null) {
						this.museumPage.applyError("Museum unavailable");
						return;
					}
					int itemCount = member.entrySet().size();
					MuseumCache.put(uuid, id, member, itemCount);
					this.museumPage.applyMuseum(member);
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Museum parse failed for {}", id, exception);
					this.museumPage.applyError("Museum parse failed");
				}
			});
		});
	}

	private void ensureBingo(boolean forceRefresh) {
		if (this.playerUuid == null) {
			this.eventsPage.applyBingoError("Missing player");
			return;
		}
		if (!forceRefresh
			&& this.eventsPage.bingoState() == EventsPage.BingoLoadState.READY
			&& !this.eventsPage.needsBingoHistory()) {
			this.bingoFetchStarted = true;
			return;
		}
		if (this.bingoFetchInFlight) {
			return;
		}
		boolean historyOnly = !forceRefresh
			&& this.eventsPage.bingoState() == EventsPage.BingoLoadState.READY
			&& this.eventsPage.needsBingoHistory();
		if (historyOnly) {
			if (System.currentTimeMillis() < this.bingoRetryAtMs) {
				return;
			}
		} else if (!forceRefresh && this.bingoFetchStarted) {
			if (this.eventsPage.bingoState() != EventsPage.BingoLoadState.ERROR
				|| System.currentTimeMillis() < this.bingoRetryAtMs) {
				return;
			}
		}
		this.bingoFetchStarted = true;
		this.bingoFetchInFlight = true;
		if (!historyOnly) {
			this.eventsPage.applyBingoLoading();
		}
		UUID uuid = this.playerUuid;
		var resFut = historyOnly
			? java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.<com.google.gson.JsonObject>empty())
			: HypixelApiClient.skyblockBingoResources();
		var histFut = HypixelApiClient.skyblockBingo(uuid);
		java.util.concurrent.CompletableFuture.allOf(resFut, histFut).whenComplete((ignored, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				this.bingoFetchInFlight = false;
				if (client.gui.screen() != this) {
					return;
				}
				if (error != null) {
					BetterPV.LOGGER.warn("Bingo fetch failed for {}", uuid, error);
					if (!historyOnly) {
						this.eventsPage.applyBingoError(
							error.getMessage() == null ? "Bingo fetch failed" : error.getMessage());
						this.bingoRetryAtMs = System.currentTimeMillis() + 5_000L;
					} else {
						this.bingoRetryAtMs = System.currentTimeMillis() + 15_000L;
					}
					return;
				}
				try {
					com.google.gson.JsonObject resources = resFut.join().orElse(null);
					com.google.gson.JsonObject history = histFut.join().orElse(null);
					if (!historyOnly && resources == null && history == null) {
						this.eventsPage.applyBingoError("Bingo unavailable");
						this.bingoRetryAtMs = System.currentTimeMillis() + 5_000L;
						return;
					}
					EventsSnapshot events = this.eventsPage.snapshot();
					if (resources != null) {
						events = events.withBingoResources(resources);
					}
					boolean historyLoaded = history != null;
					if (historyLoaded) {
						events = events.withBingoHistory(history);
						this.bingoRetryAtMs = 0L;
					} else {
						BetterPV.LOGGER.warn(
							"Bingo history missing for {} (worker miss / no local API key); will retry",
							uuid
						);
						this.bingoRetryAtMs = System.currentTimeMillis() + 15_000L;
					}
					this.eventsPage.applyBingoReady(events, historyLoaded);
				} catch (Exception exception) {
					BetterPV.LOGGER.warn("Bingo parse failed for {}", uuid, exception);
					if (!historyOnly) {
						this.eventsPage.applyBingoError("Bingo parse failed");
						this.bingoRetryAtMs = System.currentTimeMillis() + 5_000L;
					} else {
						this.bingoRetryAtMs = System.currentTimeMillis() + 15_000L;
					}
				}
			});
		});
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event != null) {
			boolean typingInvSearch = this.inventorySearch != null
				&& this.inventorySearch.isVisible()
				&& this.inventorySearch.isFocused();
			boolean typingPlayerSearch = this.playerSearch != null
				&& this.playerSearch.isVisible()
				&& this.playerSearch.isFocused();
			if (typingPlayerSearch && (event.key() == 257 || event.key() == 335)) {
				submitPlayerSearch();
				return true;
			}
			boolean directional = isDirectionalKey(event.key());
			if (directional && !typingInvSearch && !typingPlayerSearch) {
				MoulberryMode.keyPressed(event.key());
				if (uiInteractive() && this.tab == PvTab.DUNGEONS && this.dungeonPage.keyPressed(event.key())) {
					return true;
				}
				// Screen arrow-key focus navigation would otherwise steal these
				// into the inventory/player search boxes.
				return true;
			}
			// Always feed B/A/Enter into Konami unless typing in search.
			if (!typingInvSearch && !typingPlayerSearch && MoulberryMode.keyPressed(event.key())) {
				return true;
			}
			if (uiInteractive()
				&& this.tab == PvTab.HOME
				&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW
				&& this.homePage.slayerKeyPressed(event.key())) {
				return true;
			}
			if (uiInteractive()
				&& this.tab == PvTab.HOME
				&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW
				&& (event.key() == 256 || event.key() == 259)
				&& this.homePage.requestSbLevelBack()) {
				// ESC / Backspace collapses SB Level overlay without closing PV.
				return true;
			}
			if (uiInteractive() && this.tab == PvTab.DUNGEONS && this.dungeonPage.keyPressed(event.key())) {
				return true;
			}
			if (uiInteractive() && this.profileMenuOpen && event.key() == 256) {
				this.profileMenuOpen = false;
				this.profileMenuHoverChoice = null;
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event != null) {
			boolean typingSearch = (this.inventorySearch != null
				&& this.inventorySearch.isVisible()
				&& this.inventorySearch.isFocused())
				|| (this.playerSearch != null
				&& this.playerSearch.isVisible()
				&& this.playerSearch.isFocused());
			if (!typingSearch) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && MoulberryMode.charTyped(text.charAt(0))) {
					return true;
				}
			}
			if (uiInteractive() && this.tab == PvTab.DUNGEONS) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && this.dungeonPage.charTyped(text.charAt(0))) {
					return true;
				}
			}
			if (uiInteractive()
				&& this.tab == PvTab.HOME
				&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && this.homePage.slayerCharTyped(text.charAt(0))) {
					return true;
				}
			}
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
