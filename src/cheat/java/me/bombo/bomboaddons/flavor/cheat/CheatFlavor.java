package me.bombo.bomboaddons.flavor.cheat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Constants;
import me.bombo.bomboaddons.flavor.FlavorBridge;
import me.bombo.bomboaddons.features.auto.AutoSequenceManager;
import me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * The cheat ({@code bomboclient}) flavor.
 *
 * <p>This class - and everything it can reach - is compiled from {@code src/cheat/java} and
 * packaged only into the {@code bomboclient} jar. The legit build resolves
 * {@link me.bombo.bomboaddons.flavor.Flavor} to {@code LegitFlavor} because this class is
 * simply not on its classpath, so the cheat commands below cannot exist there.
 */
public final class CheatFlavor implements FlavorBridge {

    @Override
    public boolean isCheat() {
        return true;
    }

    @Override
    public void init() {
        // Shared runtime; registering it here too keeps exactly-one registration even if
        // the legit init path ever stops calling it in this build's classloader layout.
        me.bombo.bomboaddons.features.auto.AutoSequenceExecutor.init();
    }

    @Override
    public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder.then(ClientCommands.literal("hide").executes(context -> {
            BomboConfig.Settings s = BomboConfig.get();
            s.hideCheats = !s.hideCheats;
            FabricClientCommandSource src = context.getSource();
            if (s.hideCheats) {
                src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aCheats are now §chidden §afrom the GUI!"));
            } else {
                src.sendFeedback(Component.literal("§8[§3Bombo§8]§r §aCheats are now §avisible §ain the GUI!"));
            }
            BomboConfig.save();
            return 1;
        }));

        builder.then(ClientCommands.literal("stealth").executes(context -> {
            BomboConfig.Settings s = BomboConfig.get();
            s.stealthMode = !s.stealthMode;
            BomboConfig.save();
            context.getSource().sendFeedback(Component.literal("§8[§3Bombo§8]§r §7Stealth (presence hygiene): "
                    + (s.stealthMode ? "§aON" : "§cOFF")));
            if (s.stealthMode) {
                context.getSource().sendFeedback(Component.literal("§7- No more bridge presence, no egg publishing, vanilla brand on join."));
                context.getSource().sendFeedback(Component.literal("§8This is client-side identity hygiene only; it does not defeat behavioural detection."));
            }
            return 1;
        }));

        // NB: /b auto, /b chathistory and /b noobfuscate are registered by shared code
        // (BomboaddonsClient), so this build only contributes what genuinely needs cheat code.
    }

    @Override
    public boolean onInputTrigger(int code, String kind, String label) {
        return me.bombo.bomboaddons.features.auto.AutoSequenceExecutor.onInputTrigger(code, kind, label);
    }

    @Override
    public void toggleSequenceByIndex(int index, String kind, String label) {
        me.bombo.bomboaddons.features.auto.AutoSequenceExecutor.toggleByIndex(index, kind, label);
    }

    @Override
    public void stopAll() {
        me.bombo.bomboaddons.features.auto.AutoSequenceExecutor.stopAll("Command");
    }

    @Override
    public boolean hasSequences() {
        return !AutoSequenceManager.getSequences().isEmpty();
    }

    /** Unused placeholder kept so the flavor name is visible in stack traces/logs. */
    static String flavorId() {
        return Constants.FLAVOR_CHEAT;
    }
}
