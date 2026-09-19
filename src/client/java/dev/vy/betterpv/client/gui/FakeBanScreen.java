package dev.vy.betterpv.client.gui;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Vanilla {@link net.minecraft.client.gui.screens.DisconnectedScreen}-shaped face that looks like
 * a Hypixel temporary ban. Appeal URL text stays Hypixel; click opens a rickroll.
 */
public final class FakeBanScreen extends Screen {
	private static final Component TO_SERVER_LIST = Component.translatable("gui.toMenu");
	private static final Component TO_TITLE = Component.translatable("gui.toTitle");
	private static final String APPEAL_LABEL = "https://www.hypixel.net/appeal";
	private static final URI RICKROLL = URI.create("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
	private static final long BAN_DURATION_MS = 30L * 24L * 60L * 60L * 1000L;
	/** Start a couple seconds into the 30d so it never shows a clean “30d 0h…”. */
	private static final long BAN_ELAPSED_AT_START_MS = 2_000L;
	private static final char[] BAN_ID_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

	private final Screen parent;
	private final String banId;
	private final long banExpiresAtMs;
	/** Mirrors vanilla DisconnectedScreen: vertical layout, no custom spacing. */
	private final LinearLayout layout = LinearLayout.vertical();
	private MultiLineTextWidget reasonWidget;
	private long lastShownSecond = -1L;

	public FakeBanScreen(Screen parent) {
		super(Component.translatable("disconnect.lost"));
		this.parent = parent;
		this.banId = randomBanId();
		this.banExpiresAtMs = System.currentTimeMillis() + BAN_DURATION_MS - BAN_ELAPSED_AT_START_MS;
	}

	public static void show(Minecraft client) {
		if (client == null) {
			return;
		}
		JoinMultiplayerScreen parent = new JoinMultiplayerScreen(new TitleScreen());
		client.disconnect(new FakeBanScreen(parent), false);
	}

	@Override
	protected void init() {
		// Exact DisconnectedScreen.init() layout contract.
		this.layout.defaultCellSetting().alignHorizontallyCenter().padding(10);
		this.layout.addChild(new StringWidget(this.title, this.font));

		this.reasonWidget = new MultiLineTextWidget(reasonMessage(), this.font)
			.setMaxWidth(this.width - 50)
			.setCentered(true);
		this.reasonWidget.setComponentClickHandler(this::handleStyleClick);
		this.layout.addChild(this.reasonWidget);

		this.layout.defaultCellSetting().padding(2);

		Button backButton;
		if (this.minecraft != null && this.minecraft.allowsMultiplayer()) {
			backButton = Button.builder(TO_SERVER_LIST, button -> this.minecraft.setScreenAndShow(this.parent)).width(200).build();
		} else {
			backButton = Button.builder(TO_TITLE, button -> this.minecraft.setScreenAndShow(new TitleScreen())).width(200).build();
		}
		this.layout.addChild(backButton);

		this.layout.arrangeElements();
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.lastShownSecond = remainingSeconds();
	}

	@Override
	protected void repositionElements() {
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	@Override
	public void tick() {
		super.tick();
		long sec = remainingSeconds();
		if (sec == this.lastShownSecond || this.reasonWidget == null) {
			return;
		}
		this.lastShownSecond = sec;
		this.reasonWidget.setMessage(reasonMessage());
		this.reasonWidget.setMaxWidth(this.width - 50);
		this.layout.arrangeElements();
		this.repositionElements();
	}

	@Override
	public Component getNarrationMessage() {
		return CommonComponents.joinForNarration(this.title, reasonMessage());
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private void handleStyleClick(Style style) {
		if (style == null || this.minecraft == null) {
			return;
		}
		ClickEvent event = style.getClickEvent();
		if (event != null) {
			defaultHandleClickEvent(event, this.minecraft, this);
		}
	}

	/**
	 * Hypixel kick body: red ban line, blank line, then reason / link / id / warning.
	 * Uses vanilla § formatting colours and newline spacing (not separate layout rows).
	 */
	private Component reasonMessage() {
		MutableComponent body = Component.empty();
		body.append(
			Component.literal(
				"You are temporarily banned for " + formatRemaining(remainingMs()) + " from this server!"
			).withStyle(ChatFormatting.RED)
		);
		body.append(Component.literal("\n\n"));
		body.append(
			Component.literal("Reason: Suspicious account activity/Other").withStyle(ChatFormatting.WHITE)
		);
		body.append(Component.literal("\n"));
		body.append(Component.literal("Find out more: ").withStyle(ChatFormatting.WHITE));
		body.append(
			Component.literal(APPEAL_LABEL).withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(RICKROLL)))
		);
		body.append(Component.literal("\n"));
		body.append(Component.literal("Ban ID: #" + this.banId).withStyle(ChatFormatting.WHITE));
		body.append(Component.literal("\n"));
		body.append(
			Component.literal("Sharing your Ban ID may affect the processing of your appeal!")
				.withStyle(ChatFormatting.GRAY)
		);
		return body;
	}

	private long remainingMs() {
		return Math.max(0L, this.banExpiresAtMs - System.currentTimeMillis());
	}

	private long remainingSeconds() {
		return remainingMs() / 1000L;
	}

	static String formatRemaining(long ms) {
		long totalSec = Math.max(0L, ms / 1000L);
		long days = totalSec / 86_400L;
		long hours = (totalSec % 86_400L) / 3_600L;
		long mins = (totalSec % 3_600L) / 60L;
		long secs = totalSec % 60L;
		return days + "d " + hours + "h " + mins + "m " + secs + "s";
	}

	static String randomBanId() {
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		int len = 7;
		StringBuilder sb = new StringBuilder(len);
		boolean hasLetter = false;
		boolean hasDigit = false;
		for (int i = 0; i < len; i++) {
			char c = BAN_ID_ALPHABET[rng.nextInt(BAN_ID_ALPHABET.length)];
			sb.append(c);
			if (Character.isLetter(c)) {
				hasLetter = true;
			} else {
				hasDigit = true;
			}
		}
		if (!hasLetter) {
			sb.setCharAt(rng.nextInt(len), (char) ('A' + rng.nextInt(26)));
		}
		if (!hasDigit) {
			sb.setCharAt(rng.nextInt(len), (char) ('0' + rng.nextInt(10)));
		}
		return sb.toString();
	}
}
