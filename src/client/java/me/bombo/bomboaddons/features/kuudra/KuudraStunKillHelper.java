package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Kuudra Stun and Kill Helper:
 * - Auto Close Shop: Automatically closes shop container after purchasing Human Cannonball.
 * - Stun Waypoints: Highlights optimal aim spots for instant Left Pod and Right Pod stuns.
 * - Hollow Helper: Outlines safe zones and Ichor danger boundaries.
 * - Rend Damage: Detects Rend arrow stacks and signals optimal pull timings.
 * - Backbone Hit Tracker: Notifies when Bone Boomerang strikes Kuudra / target, displaying held weapon, armor, and plays sound.
 */
public class KuudraStunKillHelper {

    // Stun pod target coordinates
    public static final Vec3 LEFT_POD = new Vec3(-108.5, 39.0, -114.5);
    public static final Vec3 RIGHT_POD = new Vec3(-91.5, 39.0, -114.5);

    public static void onHumanCannonballPurchased() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraCloseCannon) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gui != null && mc.gui.screen() != null) {
            mc.execute(() -> mc.player.closeContainer());
        }
    }

    public static void onBackboneHit(String targetName) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        boolean allow = (s.kuudraBackboneHit && SkyblockUtils.isInKuudra())
                || (s.backboneOnIsland && SkyblockUtils.isInPrivateIsland());

        if (!allow) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack held = mc.player.getMainHandItem();
        String heldName = !held.isEmpty() ? held.getHoverName().getString() : "Empty";

        String armorSummary = getArmorSummary(mc.player);

        mc.player.sendSystemMessage(Component.literal("§8[§6Backbone§8] §aHit §e" + targetName
                + " §8| §7Holding: §f" + heldName + " §8| §7Armor: " + armorSummary));

        if (s.kuudraBackboneSound) {
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1.2F, 1.0F));
            }
        }
    }

    private static String getArmorSummary(Player player) {
        ItemStack helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        ItemStack chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        ItemStack boots = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);

        String h = !helmet.isEmpty() ? helmet.getHoverName().getString() : "None";
        String c = !chest.isEmpty() ? chest.getHoverName().getString() : "None";
        String l = !legs.isEmpty() ? legs.getHoverName().getString() : "None";
        String b = !boots.isEmpty() ? boots.getHoverName().getString() : "None";

        return "§b[" + h + " / " + c + " / " + l + " / " + b + "§b]";
    }
}
