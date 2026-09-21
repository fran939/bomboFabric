package me.bombo.bomboaddons.features.party;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class PartyManager {

    private static volatile boolean inParty = false;
    private static volatile String partyLeader = "";
    private static final Set<String> partyMembers = ConcurrentHashMap.newKeySet();

    private static final Pattern CLEAN_COLOR_PATTERN = Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final Pattern PARTY_LEADER_PATTERN = Pattern.compile("^Party Leader: (?:(?:\\[[^\\]]+\\])?\\s*)([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOINED_PARTY_PATTERN = Pattern.compile("^You have joined (?:(?:\\[[^\\]]+\\])?\\s*)([a-zA-Z0-9_]+)'s party!", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMBER_JOINED_PATTERN = Pattern.compile("^(?:(?:\\[[^\\]]+\\])?\\s*)([a-zA-Z0-9_]+) joined the party\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMBER_LEFT_PATTERN = Pattern.compile("^(?:(?:\\[[^\\]]+\\])?\\s*)([a-zA-Z0-9_]+) (?:has left|was removed from|has been kicked from) the party\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_TRANSFERRED_PATTERN = Pattern.compile("^The party was transferred to (?:(?:\\[[^\\]]+\\])?\\s*)([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE);

    public static boolean isInParty() {
        return inParty;
    }

    public static String getPartyLeader() {
        return partyLeader;
    }

    public static Set<String> getPartyMembers() {
        return Collections.unmodifiableSet(partyMembers);
    }

    public static String getPartySerialized() {
        if (!inParty) return "";
        StringBuilder sb = new StringBuilder();
        if (!partyLeader.isEmpty()) {
            sb.append(partyLeader);
        }
        for (String m : partyMembers) {
            if (!m.equalsIgnoreCase(partyLeader)) {
                if (sb.length() > 0) sb.append(";");
                sb.append(m);
            }
        }
        return sb.toString();
    }

    public static String getPartySummary() {
        if (!inParty) return "None";
        int size = partyMembers.size();
        if (!partyLeader.isEmpty()) {
            if (size <= 1) {
                return "Leader: " + partyLeader;
            }
            return "Leader: " + partyLeader + " (" + size + " members)";
        }
        return size > 0 ? size + " members" : "In Party";
    }

    public static void onChatMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) return;
        String clean = CLEAN_COLOR_PATTERN.matcher(rawMessage).replaceAll("").trim();

        // 1. Full party listing
        Matcher mLeader = PARTY_LEADER_PATTERN.matcher(clean);
        if (mLeader.find()) {
            inParty = true;
            partyLeader = mLeader.group(1);
            partyMembers.add(partyLeader);
            ensureSelfAdded();
            return;
        }

        if (clean.startsWith("Party Members:") || clean.startsWith("Party Moderators:")) {
            inParty = true;
            String rest = clean.substring(clean.indexOf(":") + 1).trim();
            for (String part : rest.split("●|,")) {
                String name = extractUsername(part.trim());
                if (!name.isEmpty()) {
                    partyMembers.add(name);
                }
            }
            ensureSelfAdded();
            return;
        }

        // 2. Joining / Left events
        Matcher mJoin = JOINED_PARTY_PATTERN.matcher(clean);
        if (mJoin.find()) {
            inParty = true;
            partyMembers.clear();
            partyLeader = mJoin.group(1);
            partyMembers.add(partyLeader);
            ensureSelfAdded();
            return;
        }

        Matcher mMemberJoin = MEMBER_JOINED_PATTERN.matcher(clean);
        if (mMemberJoin.find()) {
            inParty = true;
            partyMembers.add(mMemberJoin.group(1));
            ensureSelfAdded();
            return;
        }

        Matcher mMemberLeft = MEMBER_LEFT_PATTERN.matcher(clean);
        if (mMemberLeft.find()) {
            partyMembers.remove(mMemberLeft.group(1));
            return;
        }

        Matcher mTransfer = PARTY_TRANSFERRED_PATTERN.matcher(clean);
        if (mTransfer.find()) {
            partyLeader = mTransfer.group(1);
            partyMembers.add(partyLeader);
            return;
        }

        // 3. Disband / Leave events
        if (clean.equals("You left the party.")
                || clean.contains("disbanded the party")
                || clean.contains("The party was disbanded")
                || clean.equals("You are not currently in a party.")
                || clean.equals("You are not in a party!")) {
            inParty = false;
            partyLeader = "";
            partyMembers.clear();
        }
    }

    private static void ensureSelfAdded() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            String name = mc.getUser().getName();
            if (name != null && !name.isEmpty()) {
                partyMembers.add(name);
            }
        }
    }

    private static String extractUsername(String token) {
        String noRank = token.replaceAll("^\\[[^\\]]+\\]\\s*", "").trim();
        String[] parts = noRank.split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }
}
