package dev.vy.betterpv.client;

import dev.vy.betterpv.client.gui.ProfileViewerScreen;
import dev.vy.betterpv.client.gui.nav.PvTab;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class ProfileViewerOpener {
	private static volatile String pendingName;
	private static volatile PvTab pendingTab;
	private static volatile int openDelayTicks;

	private ProfileViewerOpener() {
	}

	/**
	 * Chat {@code RunCommand} clicks go to the server via {@code sendUnattendedCommand}.
	 * BetterPV's {@code /pv} is a Fabric client command, so intercept here instead.
	 *
	 * @return true if this was a BetterPV pv command and was handled client-side
	 */
	public static boolean tryHandleChatCommand(String command) {
		if (command == null || command.isBlank()) {
			return false;
		}
		String trimmed = command.trim();
		if (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.equals("b pv") || lower.equals("bombo pv")) {
			openSelfOr(null);
			return true;
		}
		if (lower.startsWith("b pv ")) {
			handleTypedArg(trimmed.substring("b pv ".length()).trim());
			return true;
		}
		if (lower.startsWith("bombo pv ")) {
			handleTypedArg(trimmed.substring("bombo pv ".length()).trim());
			return true;
		}
		// Hypixel name clicks stay on the original Component (keeps chat colors).
		// Open BetterPV instead of forwarding SocialOptions / viewprofile to the server.
		String hypixelName = ChatClickProcessor.usernameFromCommand(trimmed);
		if (hypixelName != null) {
			open(hypixelName);
			return true;
		}
		return false;
	}

	/**
	 * Resolves {@code /pv} args:
	 * <ul>
	 *   <li>{@code /pv} or blank -> self, Home</li>
	 *   <li>{@code /pv <name>} -> that player, Home</li>
	 *   <li>{@code /pv <name> <page>} -> that player on that page</li>
	 * </ul>
	 * The first token is always a player name so usernames that match page aliases still work.
	 */
	public static void handleTypedArg(String raw) {
		if (raw == null || raw.isBlank()) {
			openSelfOr(null);
			return;
		}
		String[] parts = raw.trim().split("\\s+");
		if (parts.length == 0 || parts[0].isEmpty()) {
			openSelfOr(null);
			return;
		}
		String name = parts[0];
		if (parts.length == 1) {
			openSelfOr(name);
			return;
		}
		PvTab page = PvTab.fromCommandArg(parts[1]);
		openSelfOr(name, page != null ? page : PvTab.HOME);
	}

	public static void openSelfOr(String name) {
		openSelfOr(name, null);
	}

	public static void openSelfOr(String name, PvTab tab) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return;
		}
		String target = name == null || name.isBlank()
			? client.player.getGameProfile().name()
			: name.trim();
		open(target, tab);
	}

	public static void open(String name) {
		open(name, null);
	}

	public static void open(String name, PvTab tab) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		pendingName = name == null || name.isBlank() ? null : name.trim();
		if (pendingName == null && client.player != null) {
			pendingName = client.player.getGameProfile().name();
		}
		pendingTab = tab;
		// Defer past chat close so ChatScreen does not immediately replace us.
		openDelayTicks = 2;
	}

	public static void tick(Minecraft client) {
		if (openDelayTicks <= 0 || pendingName == null || client == null) {
			return;
		}
		openDelayTicks--;
		if (openDelayTicks > 0) {
			return;
		}
		String name = pendingName;
		PvTab tab = pendingTab == null ? PvTab.HOME : pendingTab;
		pendingName = null;
		pendingTab = null;
		if (name == null || name.isBlank()) {
			return;
		}
		client.setScreenAndShow(new ProfileViewerScreen(name, tab));
	}
}
