package me.bombo.bomboaddons_final;

public class CommandTracker {
    private static String lastEc = "ec";
    private static String lastBp = "bp";
    private static String lastSh = "ahs";

    public static void onCommandSent(String command) {
        String lower = command.trim().toLowerCase();
        
        // Handle /ec, /ender, /enderchest
        if (lower.startsWith("ec") || lower.startsWith("ender")) {
            // Check if it's exactly ec/ender or starts with ec /ender 
            if (isMatch(lower, "ec") || isMatch(lower, "ender") || isMatch(lower, "enderchest")) {
                lastEc = command;
            }
        } 
        // Handle /bp, /backpack
        else if (lower.startsWith("bp") || lower.startsWith("backpack")) {
            if (isMatch(lower, "bp") || isMatch(lower, "backpack")) {
                lastBp = command;
            }
        }
        // Handle /ahs, /bz, /bazaar, /ah search
        else if (lower.startsWith("ahs") || lower.startsWith("bz") || lower.startsWith("bazaar") || lower.startsWith("ah")) {
            if (isMatch(lower, "ahs") || isMatch(lower, "bz") || isMatch(lower, "bazaar") || lower.startsWith("ah search ")) {
                lastSh = command;
            }
        }
    }

    private static boolean isMatch(String lower, String prefix) {
        return lower.equals(prefix) || lower.startsWith(prefix + " ");
    }

    public static String getLastEc() {
        return lastEc;
    }

    public static String getLastBp() {
        return lastBp;
    }

    public static String getLastSh() {
        return lastSh;
    }
}
