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
        CheatAutoExecutor.init();
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

        builder.then(ClientCommands.literal("auto")
                .then(ClientCommands.literal("list").executes(context -> {
                    FabricClientCommandSource src = context.getSource();
                    var list = AutoSequenceManager.getSequences();
                    src.sendFeedback(Component.literal("§8[§bBomboAddons§8] §e=== Auto Sequences (§b"
                            + list.size() + "§e) ==="));
                    if (list.isEmpty()) {
                        src.sendFeedback(Component.literal("§7No sequences configured. Add one in §e/b §7-> §eAuto§7."));
                        return 1;
                    }
                    for (int i = 0; i < list.size(); i++) {
                        AutoSequence seq = list.get(i);
                        boolean running = AutoSequenceManager.isRunning(seq);
                        src.sendFeedback(Component.literal("§7#" + i + " §f" + seq.name
                                + " §7[" + (seq.enabled ? "§aON" : "§cOFF") + "§7]"
                                + (running ? " §b▶ RUNNING" : "")
                                + " §8trigger: §e" + (seq.triggerKey == null || seq.triggerKey.isEmpty() ? "none" : seq.triggerKey)
                                + " §8steps: §e" + (seq.actions == null ? 0 : seq.actions.size())));
                    }
                    return 1;
                }))
                .then(ClientCommands.literal("stop").executes(context -> {
                    CheatAutoExecutor.stopAll("Command");
                    context.getSource().sendFeedback(Component.literal("§8[§bBomboAddons§8] §7Auto sequences stopped."));
                    return 1;
                }))
                .then(ClientCommands.literal("run")
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(context -> {
                            String name = StringArgumentType.getString(context, "name").trim();
                            FabricClientCommandSource src = context.getSource();
                            AutoSequence seq = AutoSequenceManager.findByName(name);
                            if (seq == null) {
                                src.sendFeedback(Component.literal("§8[§bBomboAddons§8] §cNo sequence matching §e" + name
                                        + "§c. Try §e/b auto list§c."));
                                return 0;
                            }
                            CheatAutoExecutor.toggle(seq, "Command /b auto run " + name);
                            return 1;
                        }))));
    }

    @Override
    public boolean onInputTrigger(int code, String kind, String label) {
        return CheatAutoExecutor.onInputTrigger(code, kind, label);
    }

    @Override
    public void toggleSequenceByIndex(int index, String kind, String label) {
        CheatAutoExecutor.toggleByIndex(index, kind, label);
    }

    @Override
    public void stopAll() {
        CheatAutoExecutor.stopAll("Command");
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
