package dev.vy.betterpv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Leveling {
	public record Progress(
		float level,
		float maxXpForLevel,
		boolean maxed,
		int maxLevel,
		float totalXp,
		float xpIntoLevel,
		float overflowXp,
		float overflowLevel
	) {
		public Progress(
			float level,
			float maxXpForLevel,
			boolean maxed,
			int maxLevel,
			float totalXp,
			float xpIntoLevel,
			float overflowXp
		) {
			this(
				level,
				maxXpForLevel,
				maxed,
				maxLevel,
				totalXp,
				xpIntoLevel,
				overflowXp,
				repeatedStepOverflowLevel(maxed, level, maxLevel, overflowXp, maxXpForLevel)
			);
		}

		public float fill() {
			if (maxed || maxXpForLevel <= 0F) {
				return 1.0f;
			}
			return Math.max(0F, Math.min(1F, xpIntoLevel / maxXpForLevel));
		}

		/** Uncapped skill level including overflow (SkyHanni / in-game Skills menu). */
		public int displayLevel() {
			if (maxed) {
				return Math.max(maxLevel, (int) Math.floor(overflowLevel));
			}
			return (int) Math.floor(level);
		}

		/** Soft-cap level for skill bar labels. Overflow stays hover-only. */
		public int cappedLevel() {
			if (maxed) {
				return maxLevel;
			}
			return (int) Math.floor(level);
		}

		public String hoverText() {
			if (maxed) {
				return overflowHoverText();
			}
			long into = Math.round(xpIntoLevel);
			long need = Math.round(maxXpForLevel);
			if (need <= 0L) {
				return FormatUtil.commas(Math.round(totalXp)) + "/" + FormatUtil.commas(Math.round(totalXp)) + " (100%)";
			}
			double pct = fill() * 100.0;
			return FormatUtil.commas(into) + "/" + FormatUtil.commas(need) + " (" + FormatUtil.oneDecimal(pct) + "%)";
		}

		/** Skill hover used by Mining HOTM bars, e.g. {@code HOTM 7 - 60k / 300k to Level 8}. */
		public String skillHover(String name) {
			int lvl = displayLevel();
			if (maxed) {
				return name + " " + lvl + " - " + overflowHoverText();
			}
			int next = Math.min(maxLevel, lvl + 1);
			long into = Math.round(xpIntoLevel);
			long need = Math.round(maxXpForLevel);
			return name + " " + lvl + " - " + FormatUtil.shortXp(into) + " / " + FormatUtil.shortXp(need)
				+ " to Level " + next;
		}

		public List<PvTooltip.Line> skillHoverLines(String name) {
			int lvl = displayLevel();
			String title = (name == null ? "?" : name) + " " + lvl;
			List<PvTooltip.Line> lines = new ArrayList<>(6);
			lines.add(PvTooltip.Line.of(title, PvDraw.COLOR_ACCENT));
			lines.add(PvTooltip.Line.row(
				"Total XP",
				PvDraw.COLOR_MUTED,
				FormatUtil.commas(Math.round(Math.max(0F, totalXp))),
				PvDraw.COLOR_GOLD
			));
			if (overflowXp > 0.5F) {
				lines.add(PvTooltip.Line.row(
					"Overflow XP",
					PvDraw.COLOR_MUTED,
					FormatUtil.commas(Math.round(overflowXp)),
					PvDraw.COLOR_GOLD
				));
			}
			if (maxed) {
				lines.add(PvTooltip.Line.of(
					"Overflow Level: " + FormatUtil.oneDecimal(overflowLevel),
					PvDraw.COLOR_MUTED
				));
				if (maxXpForLevel > 0F) {
					int next = lvl + 1;
					long into = Math.round(xpIntoLevel);
					long need = Math.round(maxXpForLevel);
					lines.add(PvTooltip.Line.of(
						FormatUtil.commas(into) + " / " + FormatUtil.commas(need) + " to Level " + next,
						PvDraw.COLOR_GOLD
					));
				}
			} else {
				int next = Math.min(maxLevel, lvl + 1);
				long into = Math.round(xpIntoLevel);
				long need = Math.round(maxXpForLevel);
				lines.add(PvTooltip.Line.of(
					FormatUtil.shortXp(into) + " / " + FormatUtil.shortXp(need) + " to Level " + next,
					PvDraw.COLOR_GOLD
				));
			}
			return lines;
		}

		private String overflowHoverText() {
			String text = "Overflow Level: " + FormatUtil.oneDecimal(overflowLevel)
				+ " · Overflow XP: " + FormatUtil.shortXp(Math.max(0F, overflowXp));
			if (maxXpForLevel > 0F) {
				text += " · " + FormatUtil.commas(Math.round(xpIntoLevel))
					+ " / " + FormatUtil.commas(Math.round(maxXpForLevel))
					+ " to " + (displayLevel() + 1);
			}
			return text;
		}

		/** e.g. {@code Revenant 9 - 1,240,000 / 2,000,000 XP} or overflow when maxed. */
		public String slayerHover(String name) {
			int tier = (int) Math.floor(level);
			if (maxed) {
				return name + " " + tier + " - Overflow: " + FormatUtil.shortXp(Math.max(0F, overflowXp));
			}
			long into = Math.round(xpIntoLevel);
			long need = Math.round(maxXpForLevel);
			return name + " " + tier + " - " + FormatUtil.commas(into) + " / " + FormatUtil.commas(need) + " XP";
		}

		public List<PvTooltip.Line> slayerHoverLines(String name) {
			int tier = (int) Math.floor(level);
			String title = (name == null ? "?" : name) + " " + tier;
			List<PvTooltip.Line> lines = new ArrayList<>(5);
			lines.add(PvTooltip.Line.of(title, PvDraw.COLOR_ACCENT));
			lines.add(PvTooltip.Line.row(
				"Total XP",
				PvDraw.COLOR_MUTED,
				FormatUtil.commas(Math.round(Math.max(0F, totalXp))),
				PvDraw.COLOR_GOLD
			));
			if (overflowXp > 0.5F) {
				lines.add(PvTooltip.Line.row(
					"Overflow",
					PvDraw.COLOR_MUTED,
					FormatUtil.shortXp(overflowXp),
					PvDraw.COLOR_GOLD
				));
			}
			if (!maxed) {
				long into = Math.round(xpIntoLevel);
				long need = Math.round(maxXpForLevel);
				lines.add(PvTooltip.Line.of(
					FormatUtil.commas(into) + " / " + FormatUtil.commas(need) + " XP",
					PvDraw.COLOR_GOLD
				));
			}
			return lines;
		}

		/**
		 * XP line plus kill counts ({@code T1: n  T2: n …}).
		 * When {@code leveling.json} has reliable XP-per-kill, also estimates kills to next level.
		 */
		public String slayerHoverWithKills(String name, String slayerId, int[] tierKills) {
			StringBuilder sb = new StringBuilder(slayerHover(name));
			if (tierKills != null && tierKills.length > 0) {
				sb.append('\n');
				for (int i = 0; i < tierKills.length; i++) {
					if (i > 0) {
						sb.append("  ");
					}
					sb.append('T').append(i + 1).append(": ").append(FormatUtil.commas(Math.max(0, tierKills[i])));
				}
			}
			JsonArray bossXp = RepoData.slayerBossXp(slayerId);
			if (bossXp != null && !bossXp.isEmpty() && !maxed && maxXpForLevel > 0F) {
				float xpNeeded = Math.max(0F, maxXpForLevel - xpIntoLevel);
				int highest = Math.min(bossXp.size(), RepoData.slayerHighestTier(slayerId));
				int idx = Math.max(0, highest - 1);
				float perKill = bossXp.get(idx).getAsFloat();
				if (perKill > 0F && xpNeeded > 0F) {
					long killsNeeded = (long) Math.ceil(xpNeeded / perKill);
					sb.append('\n').append("≈ ").append(FormatUtil.commas(killsNeeded))
						.append(" T").append(idx + 1).append(" kills to next");
				}
			}
			return sb.toString();
		}

		public List<PvTooltip.Line> slayerHoverLinesWithKills(String name, String slayerId, int[] tierKills) {
			List<PvTooltip.Line> lines = new ArrayList<>(slayerHoverLines(name));
			if (tierKills != null && tierKills.length > 0) {
				StringBuilder kills = new StringBuilder();
				for (int i = 0; i < tierKills.length; i++) {
					if (i > 0) {
						kills.append("  ");
					}
					kills.append('T').append(i + 1).append(": ").append(FormatUtil.commas(Math.max(0, tierKills[i])));
				}
				lines.add(PvTooltip.Line.of(kills.toString(), PvDraw.COLOR_MUTED));
			}
			JsonArray bossXp = RepoData.slayerBossXp(slayerId);
			if (bossXp != null && !bossXp.isEmpty() && !maxed && maxXpForLevel > 0F) {
				float xpNeeded = Math.max(0F, maxXpForLevel - xpIntoLevel);
				int highest = Math.min(bossXp.size(), RepoData.slayerHighestTier(slayerId));
				int idx = Math.max(0, highest - 1);
				float perKill = bossXp.get(idx).getAsFloat();
				if (perKill > 0F && xpNeeded > 0F) {
					long killsNeeded = (long) Math.ceil(xpNeeded / perKill);
					lines.add(PvTooltip.Line.of(
						"≈ " + FormatUtil.commas(killsNeeded) + " T" + (idx + 1) + " kills to next",
						PvDraw.COLOR_MUTED
					));
				}
			}
			return lines;
		}
	}

	private Leveling() {
	}

	/**
	 * SkyHanni {@code SkillUtil.calculateSkillLevel}: after 60 the step starts at 7.6M and
	 * the slope doubles every tenth level. Matches the in-game Skills menu overflow level.
	 */
	private static final int HYPIXEL_OVERFLOW_START = 60;
	private static final long HYPIXEL_OVERFLOW_BASE = 7_000_000L;
	private static final long HYPIXEL_OVERFLOW_SLOPE = 600_000L;

	/** Non-cumulative table (skills / cata): each entry is XP required for that level step. */
	public static Progress getLevel(JsonArray table, float xp, int levelCap, boolean cumulative) {
		return getLevel(table, (double) xp, levelCap, cumulative);
	}

	public static Progress getLevel(JsonArray table, double xp, int levelCap, boolean cumulative) {
		float xpF = (float) Math.max(0D, xp);
		if (table == null || table.isEmpty()) {
			return new Progress(0, 0, false, levelCap, xpF, 0, 0);
		}
		if (!cumulative && usesHypixelSkillOverflow(table)) {
			return hypixelSkillProgress(table, xp, levelCap);
		}
		double xpToCap = xpRequiredForLevel(table, levelCap, cumulative);
		float overflowStep = overflowStepXp(table, levelCap, cumulative);
		double remaining = Math.max(0D, xp);
		for (int level = 0; level < table.size(); level++) {
			double levelXp = table.get(level).getAsDouble();
			if (levelXp > remaining) {
				float maxXpForLevel;
				float resultLevel;
				float into;
				if (cumulative) {
					double previous = level > 0 ? table.get(level - 1).getAsDouble() : 0D;
					maxXpForLevel = (float) (levelXp - previous);
					into = (float) (remaining - previous);
					resultLevel = level + into / Math.max(1F, maxXpForLevel);
				} else {
					maxXpForLevel = (float) levelXp;
					into = (float) remaining;
					resultLevel = level + (float) (remaining / Math.max(1D, levelXp));
				}
				boolean maxed = resultLevel >= levelCap;
				if (maxed) {
					float step = overflowStep > 0F ? overflowStep : maxXpForLevel;
					return new Progress(
						levelCap, step, true, levelCap, xpF, step, (float) Math.max(0D, xp - xpToCap)
					);
				}
				return new Progress(resultLevel, maxXpForLevel, false, levelCap, xpF, into, 0);
			}
			if (!cumulative) {
				remaining -= levelXp;
			}
		}
		int capped = Math.min(table.size(), levelCap);
		float step = overflowStep > 0F ? overflowStep : 0F;
		return new Progress(capped, step, true, levelCap, xpF, 0, (float) Math.max(0D, xp - xpToCap));
	}

	/**
	 * Walk the 60-step skill table, then Hypixel's overflow slope (SkyHanni SkillUtil).
	 * {@code level} stays capped; {@code overflowLevel} is the uncapped in-game level.
	 */
	private static Progress hypixelSkillProgress(JsonArray table, double xp, int levelCap) {
		double remaining = Math.max(0D, xp);
		int level = 0;
		int tableLevels = Math.min(HYPIXEL_OVERFLOW_START, table.size());
		while (level < tableLevels) {
			double need = table.get(level).getAsDouble();
			if (remaining < need) {
				break;
			}
			remaining -= need;
			level++;
		}
		double xpForNext;
		if (level >= HYPIXEL_OVERFLOW_START) {
			long slope = HYPIXEL_OVERFLOW_SLOPE;
			xpForNext = HYPIXEL_OVERFLOW_BASE + slope;
			while (remaining >= xpForNext) {
				level++;
				remaining -= xpForNext;
				xpForNext += slope;
				if (level % 10 == 0) {
					slope *= 2L;
				}
			}
		} else if (level < table.size()) {
			xpForNext = table.get(level).getAsDouble();
		} else {
			xpForNext = 0D;
		}
		double xpToCap = 0D;
		int capSteps = Math.min(Math.max(1, levelCap), table.size());
		for (int i = 0; i < capSteps; i++) {
			xpToCap += table.get(i).getAsDouble();
		}
		float overflowXp = (float) Math.max(0D, xp - xpToCap);
		boolean maxed = level >= levelCap || xp + 0.5D >= xpToCap;
		float overflowLevel = (float) (level + (xpForNext > 0D ? remaining / xpForNext : 0D));
		float cappedLevel = maxed ? levelCap : overflowLevel;
		float into = maxed ? (float) remaining : (float) remaining;
		float step = (float) xpForNext;
		if (maxed && step <= 0F) {
			step = overflowStepXp(table, levelCap, false);
			into = step;
		}
		return new Progress(
			cappedLevel, step, maxed, levelCap, (float) Math.max(0D, xp), into, overflowXp, overflowLevel
		);
	}

	private static boolean usesHypixelSkillOverflow(JsonArray table) {
		if (table == null || table.size() < HYPIXEL_OVERFLOW_START) {
			return false;
		}
		try {
			return Math.abs(table.get(0).getAsDouble() - 50D) < 0.01D
				&& Math.abs(table.get(HYPIXEL_OVERFLOW_START - 1).getAsDouble() - 7_000_000D) < 0.01D;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static float repeatedStepOverflowLevel(
		boolean maxed,
		float level,
		int maxLevel,
		float overflowXp,
		float maxXpForLevel
	) {
		if (!maxed) {
			return level;
		}
		if (maxXpForLevel <= 0F || overflowXp <= 0F) {
			return maxLevel;
		}
		return maxLevel + overflowXp / maxXpForLevel;
	}

	/** XP for one overflow level past the soft cap (repeats the last capped step). */
	private static float overflowStepXp(JsonArray table, int levelCap, boolean cumulative) {
		if (table == null || table.isEmpty() || levelCap <= 0) {
			return 0F;
		}
		int capped = Math.min(levelCap, table.size());
		if (cumulative) {
			float atCap = table.get(capped - 1).getAsFloat();
			float previous = capped > 1 ? table.get(capped - 2).getAsFloat() : 0F;
			return Math.max(0F, atCap - previous);
		}
		return Math.max(0F, table.get(capped - 1).getAsFloat());
	}

	public static float xpRequiredForLevel(JsonArray table, int levelCap, boolean cumulative) {
		if (table == null || table.isEmpty() || levelCap <= 0) {
			return 0F;
		}
		int capped = Math.min(levelCap, table.size());
		if (cumulative) {
			return table.get(capped - 1).getAsFloat();
		}
		double sum = 0D;
		for (int i = 0; i < capped; i++) {
			sum += table.get(i).getAsDouble();
		}
		return (float) sum;
	}

	public static float readSkillXp(JsonObject member, String skill) {
		return (float) readSkillXpDouble(member, skill);
	}

	public static double readSkillXpDouble(JsonObject member, String skill) {
		String apiSkill = skill.equals("social") ? "SOCIAL" : skill.toUpperCase(Locale.ROOT);
		JsonObject playerData = obj(member == null ? null : member.get("player_data"));
		JsonObject experience = playerData == null ? null : obj(playerData.get("experience"));
		if (experience != null) {
			String[] keys = {
				"SKILL_" + apiSkill,
				"SKILL_" + skill.toUpperCase(Locale.ROOT),
				skill.toUpperCase(Locale.ROOT)
			};
			for (String key : keys) {
				Double value = numDouble(experience.get(key));
				if (value != null && value > 0D) {
					return value;
				}
			}
			for (var entry : experience.entrySet()) {
				String key = entry.getKey();
				if (key == null) {
					continue;
				}
				String upper = key.toUpperCase(Locale.ROOT);
				if (upper.contains("EXTRA_LEVEL_CAP")) {
					continue;
				}
				if (upper.contains(apiSkill)) {
					Double value = numDouble(entry.getValue());
					if (value != null && value > 0D) {
						return value;
					}
				}
			}
		}
		Double legacy = numDouble(member == null ? null
			: member.get("experience_skill_" + (skill.equals("social") ? "social2" : skill)));
		return legacy == null ? 0D : legacy;
	}

	public static float readSlayerXp(JsonObject member, String slayer) {
		JsonObject boss = slayerBossObject(member, slayer);
		Float xp = boss == null ? null : num(boss.get("xp"));
		return xp == null ? 0F : xp;
	}

	public static JsonObject slayerBossObject(JsonObject member, String slayer) {
		if (member == null || slayer == null) {
			return null;
		}
		JsonObject slayerRoot = obj(member.get("slayer"));
		JsonObject bosses = slayerRoot == null ? null : obj(slayerRoot.get("slayer_bosses"));
		if (bosses == null) {
			bosses = obj(member.get("slayer_bosses"));
		}
		return bosses == null ? null : obj(bosses.get(slayer));
	}

	/**
	 * Tier kill counts for a slayer boss. Tries {@code boss_kills_tier_0..4} first,
	 * then {@code boss_kills_tier_1..5}. Returns a length-5 array (T1…T5).
	 */
	public static int[] readSlayerBossKills(JsonObject member, String slayer) {
		int[] kills = new int[5];
		JsonObject boss = slayerBossObject(member, slayer);
		if (boss == null) {
			return kills;
		}
		boolean foundZeroBased = false;
		for (int i = 0; i < 5; i++) {
			Float value = num(boss.get("boss_kills_tier_" + i));
			if (value != null) {
				kills[i] = Math.max(0, Math.round(value));
				foundZeroBased = true;
			}
		}
		if (foundZeroBased) {
			return kills;
		}
		for (int i = 0; i < 5; i++) {
			Float value = num(boss.get("boss_kills_tier_" + (i + 1)));
			if (value != null) {
				kills[i] = Math.max(0, Math.round(value));
			}
		}
		return kills;
	}

	public static float readClassXp(JsonObject member, String className) {
		JsonObject dungeons = obj(member.get("dungeons"));
		JsonObject classes = dungeons == null ? null : obj(dungeons.get("player_classes"));
		JsonObject clazz = classes == null ? null : obj(classes.get(className));
		if (clazz == null) {
			return 0F;
		}
		Float xp = num(clazz.get("experience"));
		if (xp == null) {
			xp = num(clazz.get("xp"));
		}
		return xp == null ? 0F : xp;
	}

	public static float readCatacombsXp(JsonObject member) {
		JsonObject dungeons = obj(member.get("dungeons"));
		JsonObject types = dungeons == null ? null : obj(dungeons.get("dungeon_types"));
		JsonObject type = types == null ? null : obj(types.get("catacombs"));
		Float xp = type == null ? null : num(type.get("experience"));
		return xp == null ? 0F : xp;
	}

	public static int skillCap(String skill, JsonObject member) {
		int base = RepoData.skillCap(skill);
		if ("farming".equals(skill)) {
			JsonObject jacobs = obj(member.get("jacobs_contest"));
			if (jacobs == null) {
				jacobs = obj(member.get("jacob2"));
			}
			JsonObject perks = jacobs == null ? null : obj(jacobs.get("perks"));
			if (perks != null && perks.has("farming_level_cap")) {
				base += perks.get("farming_level_cap").getAsInt();
			}
		}
		if ("foraging".equals(skill)) {
			base += foragingExtraLevelCap(member);
		}
		if ("taming".equals(skill)) {
			// George pet sacrifices raise the taming cap by 1 each (50 → 60).
			JsonObject petsData = obj(member.get("pets_data"));
			JsonObject petCare = petsData == null ? null : obj(petsData.get("pet_care"));
			if (petCare != null && petCare.get("pet_types_sacrificed") != null
				&& petCare.get("pet_types_sacrificed").isJsonArray()) {
				int sacrificed = petCare.getAsJsonArray("pet_types_sacrificed").size();
				base = Math.min(60, base + Math.max(0, sacrificed));
			}
		}
		if ("runecrafting".equals(skill) || "social".equals(skill)) {
			return Math.min(base, 25);
		}
		return base;
	}

	/**
	 * Extra Foraging levels from {@code player_data.experience.SKILL_FORAGING_extra_level_cap}.
	 * Absent or malformed values are treated as {@code 0}. Does not affect other skills.
	 */
	public static int foragingExtraLevelCap(JsonObject member) {
		JsonObject playerData = obj(member == null ? null : member.get("player_data"));
		JsonObject experience = playerData == null ? null : obj(playerData.get("experience"));
		if (experience == null) {
			return 0;
		}
		Float extra = num(experience.get("SKILL_FORAGING_extra_level_cap"));
		if (extra == null || extra.isNaN() || extra.isInfinite()) {
			return 0;
		}
		return Math.max(0, Math.round(extra));
	}

	public static JsonArray skillTable(String skill) {
		JsonObject leveling = RepoData.leveling();
		if (leveling == null) {
			return null;
		}
		if ("runecrafting".equals(skill) && leveling.has("runecrafting_xp")) {
			return leveling.getAsJsonArray("runecrafting_xp");
		}
		if ("social".equals(skill) && leveling.has("social")) {
			return leveling.getAsJsonArray("social");
		}
		return RepoData.levelingXp();
	}

	public static Float num(JsonElement element) {
		Double value = numDouble(element);
		return value == null ? null : value.floatValue();
	}

	public static Double numDouble(JsonElement element) {
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return null;
		}
		try {
			return element.getAsDouble();
		} catch (Exception exception) {
			return null;
		}
	}

	public static JsonObject obj(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}
}
