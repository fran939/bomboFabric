package me.bombo.bomboaddons.features.critters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CritterChatParser {

    private static final Pattern SHARD_AMOUNT = Pattern.compile("(\\d[\\d,]*)x\\s+\\S");
    private static final Pattern LOOT_SHARE_CATCHER = Pattern.compile("from\\s+(\\w{1,16})\\s+(?:catching|finding)\\b");
    private static final Pattern ATTEMPT = Pattern.compile("^You threw a Critter Capsule at the (.+)!$");
    private static final Pattern FAILED = Pattern.compile("^The (.+?) (?:escaped your Critter Capsule|dodged your critter capsule)\\b");
    private static final Pattern ENTERED = Pattern.compile("^(?:\\[[^]]+]\\s*)?(\\w{1,16}) entered Critter Safari!$");

    private static final Pattern PLAYER_SAID = Pattern.compile(
        "^(?:Party|Guild|Officer|Co-op|Team|Friend) >.*"
            + "|^(?:From|To) .*"
            + "|^(?:\\[(?!NPC]|MOB])[^]]+]\\s*)?\\w{1,16}: .*"
    );

    private CritterChatParser() {}

    public static boolean playerSaid(String line) {
        return PLAYER_SAID.matcher(line).matches();
    }

    public static String clean(String raw) {
        String text = raw.replaceAll("§.", "").trim();
        text = text.replaceAll("\\s*(?:\\(\\s*[x×]?\\s*\\d+\\s*\\)|\\[\\s*[x×]?\\s*\\d+\\s*])$", "");
        return text.trim();
    }

    public static CritterEvent parse(String line, String selfName) {
        if (line.startsWith("CAPTURE!")) {
            Critter critter = Critters.findIn(line);
            if (critter == null) return null;
            return new CritterEvent(CritterEvent.Type.OWN_CATCH, critter, null,
                shardAmount(line), line.contains("SPARKLING"));
        }

        if (line.startsWith("LOOT SHARE!")) {
            Critter critter = Critters.findIn(line);
            if (critter == null) return null;
            Matcher catcher = LOOT_SHARE_CATCHER.matcher(line);
            if (!catcher.find()) return null;
            return new CritterEvent(CritterEvent.Type.SHARED_CATCH, critter, catcher.group(1),
                shardAmount(line), line.contains("SPARKLING"));
        }

        Matcher attempt = ATTEMPT.matcher(line);
        if (attempt.matches()) {
            Critter critter = Critters.byName(attempt.group(1));
            if (critter == null) return null;
            return new CritterEvent(CritterEvent.Type.ATTEMPT, critter, null, 0, false);
        }

        Matcher failed = FAILED.matcher(line);
        if (failed.find()) {
            Critter critter = Critters.byName(failed.group(1));
            if (critter == null) return null;
            return new CritterEvent(CritterEvent.Type.FAILED, critter, null, 0, false);
        }

        Matcher entered = ENTERED.matcher(line);
        if (entered.matches() && selfName != null && selfName.equalsIgnoreCase(entered.group(1))) {
            return new CritterEvent(CritterEvent.Type.ENTERED_SAFARI, null, selfName, 0, false);
        }

        return null;
    }

    private static int shardAmount(String line) {
        Matcher matcher = SHARD_AMOUNT.matcher(line);
        if (!matcher.find()) return 1;
        try {
            return Integer.parseInt(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
