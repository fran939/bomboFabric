package me.bombo.bomboaddons.features.storageoverlay;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

public class StorageOverlayScreenHandler extends ChestMenu {
	private static final sun.misc.Unsafe UNSAFE;
	private static final long X_OFFSET;
	private static final long Y_OFFSET;

	static {
		sun.misc.Unsafe u = null;
		long xOff = -1L;
		long yOff = -1L;
		try {
			java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			u = (sun.misc.Unsafe) f.get(null);
			java.lang.reflect.Field fx = Slot.class.getDeclaredField("x");
			java.lang.reflect.Field fy = Slot.class.getDeclaredField("y");
			xOff = u.objectFieldOffset(fx);
			yOff = u.objectFieldOffset(fy);
		} catch (Throwable t) {
			try {
				if (u != null) {
					for (java.lang.reflect.Field field : Slot.class.getDeclaredFields()) {
						if (field.getType() == int.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
							if (field.getName().equals("x") || field.getName().endsWith("x")) {
								xOff = u.objectFieldOffset(field);
							} else if (field.getName().equals("y") || field.getName().endsWith("y")) {
								yOff = u.objectFieldOffset(field);
							}
						}
					}
				}
			} catch (Throwable ignored) {}
		}
		UNSAFE = u;
		X_OFFSET = xOff;
		Y_OFFSET = yOff;
	}

	public static void setSlotPosition(Slot slot, int x, int y) {
		if (UNSAFE != null && X_OFFSET != -1L && Y_OFFSET != -1L) {
			UNSAFE.putInt(slot, X_OFFSET, x);
			UNSAFE.putInt(slot, Y_OFFSET, y);
		}
	}
	private final int rows;

	public StorageOverlayScreenHandler(MenuType<?> type, int syncId, Inventory playerInventory, Container inventory, int rows, boolean isBackpack, int height) {
		super(type, syncId, playerInventory, inventory, rows);
		this.rows = rows;
		int yOffset = rows * 18;
		shiftInventory(height - yOffset - 113);

		// disable all slots on main "Storage" page
		if (!isBackpack) {
			for (int i = 0; i < rows * 9; i++) {
				Slot slot = slots.get(i);
				slots.set(i, new InactiveSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y, i));
			}
		}
		// if backpack / echest
		else {
			// disable top row (unneeded navigation)
			for (int i = 0; i < 9; i++) {
				Slot slot = slots.get(i);
				slots.set(i, new InactiveSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y, i));
			}
			// disable slots when not in menu and set to off screen until moved
			updateBackpackSlots(height);
		}
	}

	public void shiftInventory(int shift) {
		// Shift inventory slots to line up with storage overlay
		for (int i = rows * 9; i < slots.size(); i++) {
			Slot originalSlot = slots.get(i);
			Slot slot = new Slot(originalSlot.container, originalSlot.getContainerSlot(), originalSlot.x, originalSlot.y + shift);
			slot.index = i;
			slots.set(i, slot);
		}
	}

	public void updateBackpackSlots(int height) {
		for (int i = 9; i < rows * 9; ++i) {
			Slot originalSlot = slots.get(i);
			Slot slot = new Slot(originalSlot.container, originalSlot.getContainerSlot(), 0, -20) {
				@Override
				public boolean isActive() {
					return this.y > 8 && this.y < height - 94;
				}

				@Override
				public boolean isHighlightable() {
					return this.y > 8 && this.y < height - 94;
				}
			};
			slot.index = i;
			slots.set(i, slot);
		}
	}

	public StorageOverlayScreenHandler(ChestMenu handler, boolean isBackpack, int height, Inventory playerInventory) {
		this(handler.getType(), handler.containerId, playerInventory, handler.getContainer(), handler.getRowCount(), isBackpack, height);
	}

	/**
	 * Moves containers slots to new top left position
	 *
	 * @param x       left position
	 * @param y       top position
	 * @param columns how many columns the container has
	 */
	public void moveBackpackSlots(int x, int y, int columns) {
		for (int i = 9; i < rows * 9; ++i) {
			int itemX = x + (i - 9) % columns * 18;
			int itemY = y + (i - 9) / columns * 18;
			Slot slot = slots.get(i);
			setSlotPosition(slot, itemX, itemY);
		}
	}

	private static class InactiveSlot extends Slot {
		InactiveSlot(Container container, int slot, int x, int y, int index) {
			super(container, slot, x, y);
			this.index = index;
		}

		@Override
		public boolean isActive() {
			return false;
		}
	}
}
