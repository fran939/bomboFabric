package me.bombo.bomboaddons.features.misc;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ConditionalKeybindManager {
    public static KeyMapping keyConditionalAction;
    private static long lastTriggerTime = 0L;

    public static void onClientTick() {
        if (keyConditionalAction != null) {
            while (keyConditionalAction.consumeClick()) {
                triggerKeybind("KeyMapping");
            }
        }
    }

    public static void onKeyPressed(int keyCode) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.conditionalKeybindEnabled) return;

        if (s.conditionalKeybindKey != null && !s.conditionalKeybindKey.isEmpty()) {
            int targetCode = ClickLogic.getKeyCode(s.conditionalKeybindKey);
            if (targetCode != -1 && keyCode == targetCode) {
                triggerKeybind("CustomKey");
            }
        }
    }

    public static boolean triggerKeybind(String source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.conditionalKeybindEnabled) return false;

        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < 400L) {
            return false;
        }

        // 1. Check Location / Kuudra Condition
        String reqArea = (s.conditionalKeybindArea != null) ? s.conditionalKeybindArea.trim().toLowerCase() : "kuudra t5";
        boolean areaPass = false;
        if (reqArea.equals("everywhere") || reqArea.equals("any") || reqArea.equals("all") || reqArea.equals("none")) {
            areaPass = true;
        } else if (reqArea.contains("kuudra")) {
            boolean inKuudra = KuudraUtils.inKuudra() || "Kuudra".equalsIgnoreCase(BomboaddonsClient.currentArea);
            if (inKuudra) {
                if (reqArea.contains("5") || reqArea.contains("t5") || s.conditionalKeybindKuudraT5Only) {
                    areaPass = (KuudraUtils.getKuudraTier() == 5);
                } else {
                    areaPass = true;
                }
            }
        } else {
            String currentArea = BomboaddonsClient.currentArea != null ? BomboaddonsClient.currentArea.toLowerCase() : "";
            areaPass = currentArea.contains(reqArea);
        }

        if (!areaPass) {
            return false;
        }

        // 2. Check Height (Y) Condition
        double playerY = mc.player.getY();
        if (s.conditionalKeybindCheckHeight) {
            if (playerY >= s.conditionalKeybindMaxY) {
                return false;
            }
        }

        // Conditions satisfied! Execute command
        lastTriggerTime = now;
        String cmd = (s.conditionalKeybindCommand != null && !s.conditionalKeybindCommand.trim().isEmpty())
                ? s.conditionalKeybindCommand.trim()
                : "/b cd 100 /wd";

        mc.execute(() -> {
            BomboaddonsClient.executeTracked(cmd);
            if (s.conditionalKeybindNotify) {
                mc.player.sendSystemMessage(Component.literal(
                        "§8[§bBomboAddons§8] §aConditional keybind triggered: §e" + cmd + " §7(Y: " + String.format("%.1f", playerY) + ")"));
            }
        });

        return true;
    }

    public static void handleCommand(String rawArgs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        String args = (rawArgs != null) ? rawArgs.trim() : "";
        if (args.isEmpty() || args.equalsIgnoreCase("help") || args.equalsIgnoreCase("info")) {
            double curY = mc.player != null ? mc.player.getY() : 0.0;
            boolean inKuudra = KuudraUtils.inKuudra();
            int tier = KuudraUtils.getKuudraTier();
            String kuudraStatus = inKuudra ? "In Kuudra (T" + tier + ")" : "Not in Kuudra";

            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §e§lConditional Keybind Settings:"));
            mc.player.sendSystemMessage(Component.literal("§7- Enabled: " + (s.conditionalKeybindEnabled ? "§aON" : "§cOFF")));
            mc.player.sendSystemMessage(Component.literal("§7- Command: §b" + s.conditionalKeybindCommand));
            mc.player.sendSystemMessage(Component.literal("§7- Location: §e" + s.conditionalKeybindArea + " §7(Current: " + kuudraStatus + ")"));
            mc.player.sendSystemMessage(Component.literal("§7- Height Condition: " + (s.conditionalKeybindCheckHeight ? "§aY < " + s.conditionalKeybindMaxY : "§cDisabled") + " §7(Current Y: §f" + String.format("%.1f", curY) + "§7)"));
            mc.player.sendSystemMessage(Component.literal("§7- Config Key: §6" + (s.conditionalKeybindKey != null ? s.conditionalKeybindKey : "NONE") + " §7(or bind in Controls menu)"));
            mc.player.sendSystemMessage(Component.literal("§7Commands: §b/b ck cmd <command> §7| §b/b ck y <height|off> §7| §b/b ck tier <t5|any|off> §7| §b/b ck toggle §7| §b/b ck test"));
            return;
        }

        String lower = args.toLowerCase();
        if (lower.equals("toggle") || lower.equals("on") || lower.equals("off")) {
            if (lower.equals("on")) s.conditionalKeybindEnabled = true;
            else if (lower.equals("off")) s.conditionalKeybindEnabled = false;
            else s.conditionalKeybindEnabled = !s.conditionalKeybindEnabled;
            BomboConfig.save();
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Conditional Keybind: " + (s.conditionalKeybindEnabled ? "§aEnabled" : "§cDisabled")));
            return;
        }

        if (lower.startsWith("cmd ") || lower.startsWith("command ")) {
            String newCmd = args.substring(args.indexOf(' ') + 1).trim();
            s.conditionalKeybindCommand = newCmd;
            BomboConfig.save();
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aCommand set to: §b" + newCmd));
            return;
        }

        if (lower.startsWith("y ") || lower.startsWith("height ")) {
            String val = args.substring(args.indexOf(' ') + 1).trim();
            if (val.equalsIgnoreCase("off") || val.equalsIgnoreCase("disable") || val.equalsIgnoreCase("none")) {
                s.conditionalKeybindCheckHeight = false;
                BomboConfig.save();
                mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aHeight condition disabled (will trigger at any height)."));
                return;
            }
            try {
                double y = Double.parseDouble(val);
                s.conditionalKeybindMaxY = y;
                s.conditionalKeybindCheckHeight = true;
                BomboConfig.save();
                mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aHeight condition set to: §eY < " + y));
            } catch (NumberFormatException e) {
                mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cInvalid height number: §e" + val));
            }
            return;
        }

        if (lower.startsWith("tier ") || lower.startsWith("area ") || lower.startsWith("loc ")) {
            String val = args.substring(args.indexOf(' ') + 1).trim();
            if (val.equalsIgnoreCase("5") || val.equalsIgnoreCase("t5") || val.equalsIgnoreCase("kuudra 5") || val.equalsIgnoreCase("kuudra t5")) {
                s.conditionalKeybindArea = "Kuudra T5";
                s.conditionalKeybindKuudraT5Only = true;
            } else if (val.equalsIgnoreCase("any") || val.equalsIgnoreCase("kuudra")) {
                s.conditionalKeybindArea = "Any Kuudra";
                s.conditionalKeybindKuudraT5Only = false;
            } else if (val.equalsIgnoreCase("off") || val.equalsIgnoreCase("none") || val.equalsIgnoreCase("all") || val.equalsIgnoreCase("everywhere")) {
                s.conditionalKeybindArea = "Everywhere";
                s.conditionalKeybindKuudraT5Only = false;
            } else {
                s.conditionalKeybindArea = val;
                s.conditionalKeybindKuudraT5Only = false;
            }
            BomboConfig.save();
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aArea requirement set to: §e" + s.conditionalKeybindArea));
            return;
        }

        if (lower.equals("test")) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Testing conditional keybind manually..."));
            boolean ran = triggerKeybind("ManualTest");
            if (!ran) {
                double curY = mc.player.getY();
                int tier = KuudraUtils.getKuudraTier();
                boolean inKuudra = KuudraUtils.inKuudra();
                mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cConditions NOT met! In Kuudra: " + inKuudra + " (T" + tier + "), Player Y: " + String.format("%.1f", curY) + " (Req: " + s.conditionalKeybindArea + ", Y < " + s.conditionalKeybindMaxY + ")"));
            }
            return;
        }

        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cUnknown argument: §e" + args + "§c. Use §b/b ck help§c."));
    }
}
