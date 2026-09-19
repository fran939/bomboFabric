package dev.vy.betterpv.client;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Makes party/friends-list/sender names clickable for {@code /pv} when Hypixel
 * did not already attach a profile click.
 *
 * <p>Does not flatten/rebuild ordinary Hypixel chat. Remapping existing
 * SocialOptions clicks used to rebuild every message and bleach colors. Those
 * clicks are handled in {@link ProfileViewerOpener} instead.
 */
public final class ChatClickProcessor {
	private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
	/** Hypixel often uses a curly apostrophe in "name's profile". */
	private static final Pattern VIEW_PROFILE_HOVER = Pattern.compile(
		"(?i)Click(?: here)? to view ([A-Za-z0-9_]{1,16})['\u2019\u02BC]?s profile"
	);
	/** {@code [MVP+] Name joined the party.} */
	private static final Pattern PARTY_JOIN = Pattern.compile(
		"(?i)^(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+joined the party\\.?\\s*$"
	);
	/** {@code You have joined [MVP+] Name's party!} */
	private static final Pattern YOU_JOINED_PARTY = Pattern.compile(
		"(?i)^You have joined\\s+(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})['\u2019\u02BC]?s?\\s+party!?\\s*$"
	);
	/**
	 * Dungeon / Kuudra Party Finder:
	 * {@code Party Finder > Name joined the dungeon group! (...)}
	 * {@code Party Finder > Name joined the group! (...)}
	 */
	private static final Pattern PARTY_FINDER_JOIN = Pattern.compile(
		"(?i)^Party Finder\\s*>\\s*(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+joined the (?:dungeon )?group!"
	);
	/**
	 * Hypixel {@code /fl} lines:
	 * {@code Name is in SkyBlock - Private Island}
	 * {@code Name is offline}
	 */
	private static final Pattern FRIENDS_LIST_LINE = Pattern.compile(
		"(?i)^(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+is\\s+(?:in\\b|offline\\b)"
	);
	/**
	 * Username immediately before {@code : }, optionally followed by a guild rank
	 * tag such as {@code [Elite]} / {@code [Kitten]} (not the MVP rank before the name).
	 */
	private static final Pattern SENDER_BEFORE_COLON = Pattern.compile(
		"([A-Za-z0-9_]{3,16})(?:\\s*\\[[^\\]]+])?\\s*$"
	);

	private ChatClickProcessor() {
	}

	public static Component process(Component component) {
		if (component == null) {
			return null;
		}

		String plain = component.getString();
		NameRange range = findClickableNameRange(plain);
		if (range == null) {
			return component;
		}

		// Hypixel chat already has profile clicks on the name. Leave the Component
		// tree alone so colors/styles stay intact; ProfileViewerOpener handles the click.
		if (rangeAlreadyHasPlayerClick(component, range)) {
			return component;
		}

		MutableComponent result = Component.empty();
		int[] index = {0};
		boolean[] changed = {false};

		component.visit((style, segment) -> {
			if (!segment.isEmpty()) {
				appendSegment(result, segment, style, index[0], range, changed);
				index[0] += segment.length();
			}
			return Optional.empty();
		}, Style.EMPTY);

		return changed[0] ? result : component;
	}

	private static boolean rangeAlreadyHasPlayerClick(Component component, NameRange range) {
		int[] index = {0};
		boolean[] found = {false};
		component.visit((style, segment) -> {
			if (found[0] || segment.isEmpty()) {
				return Optional.empty();
			}
			int start = index[0];
			int end = start + segment.length();
			index[0] = end;
			if (end <= range.start || start >= range.end) {
				return Optional.empty();
			}
			if (usernameFromHypixelStyle(style) != null) {
				found[0] = true;
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found[0];
	}

	private static NameRange findClickableNameRange(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String trimmed = text.trim();
		NameRange party = partyJoinRange(text, trimmed);
		if (party != null) {
			return party;
		}
		NameRange friends = friendsListRange(text, trimmed);
		if (friends != null) {
			return friends;
		}
		return findChatSenderRange(text);
	}

	private static NameRange friendsListRange(String full, String trimmed) {
		Matcher m = FRIENDS_LIST_LINE.matcher(trimmed);
		if (!m.find()) {
			return null;
		}
		return rangeForGroup(full, trimmed, m, 1);
	}

	private static NameRange partyJoinRange(String full, String trimmed) {
		Matcher m = PARTY_JOIN.matcher(trimmed);
		if (m.find()) {
			return rangeForGroup(full, trimmed, m, 1);
		}
		m = YOU_JOINED_PARTY.matcher(trimmed);
		if (m.find()) {
			return rangeForGroup(full, trimmed, m, 1);
		}
		m = PARTY_FINDER_JOIN.matcher(trimmed);
		if (m.find()) {
			return rangeForGroup(full, trimmed, m, 1);
		}
		return null;
	}

	private static NameRange rangeForGroup(String full, String trimmed, Matcher matcher, int group) {
		String name = matcher.group(group);
		if (name == null || !PLAYER_NAME_PATTERN.matcher(name).matches()) {
			return null;
		}
		int trimOffset = full.indexOf(trimmed);
		if (trimOffset < 0) {
			trimOffset = 0;
		}
		int start = trimOffset + matcher.start(group);
		int end = trimOffset + matcher.end(group);
		if (start < 0 || end > full.length() || start >= end) {
			return null;
		}
		return new NameRange(start, end, name);
	}

	private static NameRange findChatSenderRange(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}

		int delimiter = text.indexOf(": ");
		if (delimiter <= 0) {
			return null;
		}
		String beforeColon = text.substring(0, delimiter);
		if (isRosterOrSystemLabel(beforeColon)) {
			return null;
		}

		Matcher matcher = SENDER_BEFORE_COLON.matcher(beforeColon);
		if (!matcher.find()) {
			return null;
		}
		String name = matcher.group(1);
		if (!PLAYER_NAME_PATTERN.matcher(name).matches()) {
			return null;
		}
		int start = matcher.start(1);
		int end = matcher.end(1);
		return new NameRange(start, end, name);
	}

	/**
	 * Skip party/guild roster dumps ("PARTY LEADER: ...") but allow real chat
	 * ("Party > Name: ...", "Guild > Name: ...", "From Name: ...").
	 */
	private static boolean isRosterOrSystemLabel(String beforeColon) {
		String upper = beforeColon.trim().toUpperCase(Locale.ROOT);
		if (upper.contains(" > ")) {
			return false;
		}
		if (upper.startsWith("FROM ") || upper.startsWith("TO ")) {
			return false;
		}
		return upper.startsWith("PARTY ")
			|| upper.equals("PARTY")
			|| upper.equals("PARTY LEADER")
			|| upper.equals("PARTY MODERATORS")
			|| upper.equals("PARTY MEMBERS")
			|| upper.startsWith("GUILD ")
			|| upper.equals("GUILD")
			|| upper.equals("OFFICER")
			|| upper.startsWith("OFFICER ")
			|| upper.startsWith("ONLINE ")
			|| upper.startsWith("MEMBERS ")
			|| upper.startsWith("TRADE");
	}

	private static void appendSegment(
		MutableComponent target,
		String text,
		Style style,
		int segmentStart,
		NameRange range,
		boolean[] changed
	) {
		// Never remap existing Hypixel profile styles here. Flattening them bleached chat colors.
		int segmentEnd = segmentStart + text.length();
		if (range == null || range.end <= segmentStart || range.start >= segmentEnd) {
			target.append(Component.literal(text).setStyle(style));
			return;
		}

		int localStart = Math.max(0, range.start - segmentStart);
		int localEnd = Math.min(text.length(), range.end - segmentStart);

		if (localStart > 0) {
			target.append(Component.literal(text.substring(0, localStart)).setStyle(style));
		}

		target.append(Component.literal(text.substring(localStart, localEnd)).setStyle(pvStyle(style, range.name)));
		changed[0] = true;

		if (localEnd < text.length()) {
			target.append(Component.literal(text.substring(localEnd)).setStyle(style));
		}
	}

	static String usernameFromHypixelStyle(Style style) {
		if (style == null) {
			return null;
		}
		ClickEvent click = style.getClickEvent();
		if (click instanceof ClickEvent.RunCommand run) {
			String name = usernameFromCommand(run.command());
			if (name != null) {
				return name;
			}
		}
		if (click instanceof ClickEvent.SuggestCommand suggest) {
			String name = usernameFromCommand(suggest.command());
			if (name != null) {
				return name;
			}
		}

		HoverEvent hover = style.getHoverEvent();
		if (hover instanceof HoverEvent.ShowText show) {
			String hoverText = show.value().getString();
			Matcher matcher = VIEW_PROFILE_HOVER.matcher(hoverText);
			if (matcher.find()) {
				String name = matcher.group(1);
				if (PLAYER_NAME_PATTERN.matcher(name).matches()) {
					return name;
				}
			}
		}
		return null;
	}

	static String usernameFromCommand(String command) {
		if (command == null || command.isBlank()) {
			return null;
		}
		String trimmed = command.trim();
		if (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		String rest;
		if (lower.startsWith("socialoptions ")) {
			rest = trimmed.substring("socialoptions ".length()).trim();
		} else if (lower.startsWith("viewprofile ")) {
			rest = trimmed.substring("viewprofile ".length()).trim();
		} else {
			return null;
		}
		int space = rest.indexOf(' ');
		if (space > 0) {
			rest = rest.substring(0, space);
		}
		return PLAYER_NAME_PATTERN.matcher(rest).matches() ? rest : null;
	}

	private static Style pvStyle(Style style, String name) {
		Component tip = Component.translatable("betterpv.chat.click_pv", name);
		return style
			.withClickEvent(new ClickEvent.RunCommand("/pv " + name))
			.withHoverEvent(new HoverEvent.ShowText(tip));
	}

	private record NameRange(int start, int end, String name) {
	}
}
