package dev.vy.betterpv.client.cosmetics;

import java.util.List;
import java.util.Locale;

final class NameStyleMatcher {
	private NameStyleMatcher() {
	}

	static boolean containsCandidate(String text, List<PlayerCustomizationRegistry.NameCandidate> candidates) {
		return text != null && !text.isEmpty() && !candidates.isEmpty() && findFirstNameMatch(text, candidates, 0) != null;
	}

	static MatchedCustomization findFirstNameMatch(String text, List<PlayerCustomizationRegistry.NameCandidate> candidates, int startIndex) {
		if (text == null || text.isEmpty() || candidates == null || candidates.isEmpty()) {
			return null;
		}
		int from = Math.max(0, startIndex);
		if (from >= text.length()) {
			return null;
		}

		// Candidate values are stored lowercase; lowercasing once beats regionMatches-per-index.
		String lowerText = text.toLowerCase(Locale.ROOT);
		int bestIndex = Integer.MAX_VALUE;
		PlayerCustomizationRegistry.NameCandidate bestCandidate = null;

		for (PlayerCustomizationRegistry.NameCandidate candidate : candidates) {
			String needleRaw = candidate.value();
			if (needleRaw == null || needleRaw.isEmpty()) {
				continue;
			}
			String needle = needleRaw.toLowerCase(Locale.ROOT);
			if (text.length() - from < needle.length()) {
				continue;
			}
			int searchIndex = from;
			while (searchIndex <= lowerText.length() - needle.length()) {
				int candidateIndex = lowerText.indexOf(needle, searchIndex);
				if (candidateIndex == -1 || candidateIndex > bestIndex) {
					break;
				}
				if (!candidate.requiresBoundary() || isNameBoundary(text, candidateIndex, candidateIndex + needle.length())) {
					if (candidateIndex < bestIndex
						|| (candidateIndex == bestIndex && (bestCandidate == null || needle.length() > bestCandidate.value().length()))) {
						bestIndex = candidateIndex;
						bestCandidate = candidate;
					}
					break;
				}
				searchIndex = candidateIndex + 1;
			}
		}

		if (bestCandidate == null) {
			return null;
		}
		// Keep the in-text casing length; candidate.value() may differ in case only.
		return new MatchedCustomization(
			bestIndex,
			text.substring(bestIndex, bestIndex + bestCandidate.value().length()),
			bestCandidate.customization()
		);
	}

	private static boolean isNameBoundary(String text, int start, int endExclusive) {
		return isNameBoundaryCharacter(start > 0 ? text.charAt(start - 1) : null)
			&& isNameBoundaryCharacter(endExclusive < text.length() ? text.charAt(endExclusive) : null);
	}

	private static boolean isNameBoundaryCharacter(Character character) {
		return character == null || (!Character.isLetterOrDigit(character) && character != '_');
	}

	record MatchedCustomization(int index, String matchedName, PlayerCustomizationRegistry.PlayerCustomization customization) {
	}
}
