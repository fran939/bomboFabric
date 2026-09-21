package me.bombo.bomboaddons.features.critters;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;

public final class CritterSessionManager {

    private static final int LEAVE_GRACE_TICKS = 40;
    private static final int AWAY_TICKS = 100;
    private static final long RECENT_ACTIVITY_MILLIS = 60_000;

    private static SafariSession current;
    private static SafariSession lastSession;

    private static int ticksAway;
    private static boolean arrivalStartsRun;
    private static long lastEventMillis;

    private CritterSessionManager() {}

    public static void tick() {
        if (SafariLocation.inSafari()) {
            if (ticksAway >= AWAY_TICKS) arrivalStartsRun = true;
            ticksAway = 0;
            if (arrivalStartsRun) {
                arrivalStartsRun = false;
                startSession();
            }
            return;
        }

        ticksAway++;

        if (current == null) return;
        if (System.currentTimeMillis() - lastEventMillis < RECENT_ACTIVITY_MILLIS) return;
        if (ticksAway < LEAVE_GRACE_TICKS) return;
        endSession();
    }

    public static void onChatMessage(String rawText) {
        String line = CritterChatParser.clean(rawText);
        if (line.isEmpty()) return;

        CritterEvent event = CritterChatParser.parse(line, selfName());
        if (event == null) return;

        if (event.type() == CritterEvent.Type.ENTERED_SAFARI) {
            startSession();
            SafariLocation.markEntered();
            ticksAway = 0;
            return;
        }

        SafariLocation.markEntered();

        if (current == null) startSession();
        lastEventMillis = System.currentTimeMillis();

        if (event.type() == CritterEvent.Type.OWN_CATCH || event.type() == CritterEvent.Type.SHARED_CATCH) {
            me.bombo.bomboaddons.BomboConfig.Settings s = me.bombo.bomboaddons.BomboConfig.get();
            if (s != null && s.critterDebug && Minecraft.getInstance().player != null) {
                boolean wasAlreadyCaught = current.caughtByParty(event.critter());
                String tag = !wasAlreadyCaught ? "§a(Unique)§r" : "§c(Non-Unique)§r";
                String catcher = event.type() == CritterEvent.Type.OWN_CATCH ? selfName() : event.catcher();
                String critterName = event.critter() != null ? event.critter().name() : "Unknown";
                String msg = "§8[§3BomboAddons§8] §b" + catcher + " §7captured §e" + critterName + " " + tag;
                Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            }
        }

        boolean wasBiomeCompleteBefore = event.critter() != null && current.biomeComplete(event.critter().biome(), true);
        current.record(event, lastEventMillis);

        if (event.critter() != null && !wasBiomeCompleteBefore && current.biomeComplete(event.critter().biome(), true)) {
            SafariBiome completedBiome = event.critter().biome();
            String catcher = event.type() == CritterEvent.Type.OWN_CATCH ? selfName() : event.catcher();
            long elapsed = current.elapsedMillis(System.currentTimeMillis());
            long sec = elapsed / 1000;
            String timeStr = String.format("%d:%02d", sec / 60, sec % 60);

            BomboConfig.Settings s = BomboConfig.get();
            if (s != null && !"Disabled".equalsIgnoreCase(s.critterCompletionNotify)) {
                String notification = "§8[§6Critter Safari§8] §b" + catcher + " §afinished §e" + completedBiome.displayName() + " Biome §auniques in §6" + timeStr + "§a!";
                if (Minecraft.getInstance().player != null) {
                    if ("Party (/pc)".equalsIgnoreCase(s.critterCompletionNotify)) {
                        Minecraft.getInstance().player.connection.sendCommand("pc " + catcher + " finished " + completedBiome.displayName() + " Biome in " + timeStr + "!");
                    } else {
                        Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(notification));
                    }
                }
            }
        }
    }

    public static void startSession() {
        if (current != null && !current.isEmpty()) {
            lastSession = current;
        }
        current = new SafariSession(selfName(), System.currentTimeMillis());
    }

    public static void endSession() {
        if (current != null && !current.isEmpty()) {
            lastSession = current;
        }
        current = null;
    }

    public static void onWorldChange() {
        endSession();
        ticksAway = 0;
        arrivalStartsRun = true;
    }

    public static SafariSession current() {
        return current;
    }

    public static SafariSession currentOrLast() {
        return current != null ? current : lastSession;
    }

    public static void reset() {
        current = new SafariSession(selfName(), System.currentTimeMillis());
    }

    private static String selfName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() != null ? mc.getUser().getName() : "You";
    }
}
