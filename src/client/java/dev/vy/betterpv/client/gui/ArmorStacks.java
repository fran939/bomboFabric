package dev.vy.betterpv.client.gui;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Armor stacks for the home mannequin from SkyBlock {@code inv_armor}. */
public final class ArmorStacks {
	private ArmorStacks() {
	}

	/** @return boots, leggings, chestplate, helmet (Hypixel slot order) */
	public static ItemStack[] fromMember(JsonObject member) {
		ItemStack[] armor = new ItemStack[] {
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
		};
		if (member == null) {
			return armor;
		}
		List<InventoryDecoder.Stack> slots = InventoryDecoder.readArmorSlots(member);
		for (int i = 0; i < Math.min(4, slots.size()); i++) {
			InventoryDecoder.Stack stack = slots.get(i);
			if (stack == null || stack.id() == null || stack.id().isBlank()) {
				continue;
			}
			InventorySnapshot.Slot slot = new InventorySnapshot.Slot(
				stack.id(),
				Math.max(1, stack.count()),
				stack.lore(),
				stack.displayName(),
				stack.dyeColor(),
				stack.skullValue(),
				stack.skullSignature()
			);
			ItemStack rendered = SkyBlockItemFactory.toStack(slot);
			if (rendered != null && !rendered.isEmpty()) {
				armor[i] = rendered;
			}
		}
		return armor;
	}
}
