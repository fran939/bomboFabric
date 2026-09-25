package me.bombo.bomboaddons.features.storageoverlay;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.util.MessageScheduler;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2dc;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class StorageOverlayScreen extends AbstractContainerScreen<StorageOverlayScreenHandler> {
	private static final Minecraft CLIENT = Minecraft.getInstance();
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("social_interactions/background");
	private static final Pattern CHANGING_BACKPACK_PATTERN = Pattern.compile("(Placing backpack)|(Removed backpack)");

	private static final int HEADER_H = 17;
	private static final int SLOT_SIZE = 18;
	private static final int MAX_ROW_BATCH = 6;
	private static final int MAX_COL_BATCH = 9;
	private static final int BOTTOM_V = 215;
	private static final int EDGE_PADDING = 7;

	private static final int NOT_MATCHED_COLOR = 0x99000000;

	protected static int openStorage;
	private static double savedScroll = 0;
	private static String savedSearch = "";
	private static int savedIndex = -1;
	private static boolean disableOnNextLoad = false;
	private static boolean registeredEvents = false;

	@Nullable
	private BackpackGridWidget grid;
	private int oldHeight;
	private final StorageOverlayScreenHandler handler;
	private final Component name;
	private final ChestMenu defaultHandler;
	private boolean saveMousePosition;
	private static @Nullable Vector2dc previousMousePosition;

	public static @Nullable Vector2dc getPreviousMousePosition() {
		Vector2dc screenPosition = previousMousePosition;
		previousMousePosition = null;
		return screenPosition;
	}

	public static void setup() {
		if (registeredEvents) return;
		registeredEvents = true;

		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (SkyblockUtils.isOnSkyblock() && !overlay && CHANGING_BACKPACK_PATTERN.matcher(message.getString()).find()) {
				disableOnNextLoad = true;
			}
			return true;
		});

		ClientSendMessageEvents.MODIFY_COMMAND.register(command -> {
			if (!BomboConfig.get().storageOverlay || savedIndex < 0 || Minecraft.getInstance().gui.screen() instanceof StorageOverlayScreen || !command.equals("storage")) {
				return command;
			} else {
				String c = getCommandForIndex(savedIndex);
				savedIndex = -1;
				return c.substring(1);
			}
		});
	}

	public StorageOverlayScreen(StorageOverlayScreenHandler handler, ChestMenu defaultHandler, Component name, Inventory inventory, int height) {
		super(handler, inventory, name, 176, height);
		this.titleLabelY = -1000;
		this.inventoryLabelY += 2;
		this.handler = handler;
		this.defaultHandler = defaultHandler;
		this.name = name;
		this.oldHeight = height - 87;
		setup();
	}

	public static boolean enabled(String title) {
		openStorage = BackpackPreview.getStorageIndexFromTitle(title);
		boolean enabled = BomboConfig.get().storageOverlay && (title.equals("storage") || openStorage != -1) && !disableOnNextLoad;
		disableOnNextLoad = false;
		return enabled;
	}

	protected void switchOpenStorage(int index) {
		if (grid == null) return;

		savedScroll = grid.getScrollAmount();
		MessageScheduler.INSTANCE.sendMessageAfterCooldown(getCommandForIndex(index), true);
		saveMousePosition = BomboConfig.get().storageOverlayDoNotResetCursor;
	}

	public void refreshSearch() {
		if (grid == null) return;
		grid.refreshSearch();
	}

	private static String getCommandForIndex(int index) {
		if (index <= 8) {
			return "/echest " + (index + 1);
		} else {
			return "/backpack " + (index - 8);
		}
	}

	protected static String getStorageName(int index) {
		return BackpackPreview.getStorageName(index);
	}

	private void hide(Button button) {
		if (CLIENT.player == null) return;
		CLIENT.player.containerMenu = defaultHandler;
		CLIENT.gui.setScreen(new ContainerScreen(defaultHandler, CLIENT.player.getInventory(), name));
	}

	private void home(Button button) {
		MessageScheduler.INSTANCE.sendMessageAfterCooldown("/storage", true);
		disableOnNextLoad = true;
		savedIndex = -1;
	}

	private void toolkit(Button button) {
		MessageScheduler.INSTANCE.sendMessageAfterCooldown("/farmingtoolkit", true);
	}

	private void huntingToolkit(Button button) {
		MessageScheduler.INSTANCE.sendMessageAfterCooldown("/huntingtoolkit", true);
	}

	private int getMinLeftPos() {
		return (this.width - this.getMaxWidth()) / 2;
	}

	private int getLeftPos() {
		return (this.width - this.getWidth()) / 2;
	}

	private int getMaxWidth() {
		return (this.width / 8) * 7;
	}

	private int getWidth() {
		return this.grid != null ? this.grid.getWidth() + 16 : getMaxWidth();
	}

	private int getHeight() {
		return this.height - (this.height / 5) - 87;
	}

	public Rect2i getMainExclusionZone() {
		return new Rect2i(getLeftPos(), 0, getWidth(), height);
	}

	public Rect2i getButtonsExclusionZone() {
		return new Rect2i(width - 90, height - 80, width, height);
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();

		this.topPos = (this.height / 10);
		if (grid != null) {
			grid.setY(this.topPos + 8);
		}
		handler.updateBackpackSlots(height);

		int change = getHeight() - oldHeight;
		oldHeight = getHeight();
		handler.shiftInventory(change);
		this.inventoryLabelY += change;
	}

	public boolean isSlotHoverAllowed(Slot slot, double xm, double ym) {
		return grid == null || slot.container instanceof Inventory || grid.getGridRectangle().containsPoint((int) xm, (int) ym);
	}

	@Override
	protected void init() {
		super.init();

		int storagesPerRow = BomboConfig.get().storageOverlayStoragesPerRow;
		storagesPerRow = storagesPerRow > 0 ? storagesPerRow : Integer.MAX_VALUE;
		int internalCols = BomboConfig.get().storageOverlayBackpackWidth;
		internalCols = internalCols > 0 ? internalCols : 9;

		grid = new BackpackGridWidget(getMinLeftPos() + 8, this.topPos + 8, getMaxWidth() - 16, getHeight() - 16, storagesPerRow, internalCols, true);
		grid.setSearch(savedSearch);
		grid.setScrollAmount(savedScroll);
		this.addRenderableWidget(grid);

		LinearLayout extraButtons = new LinearLayout(width - 90, height - 84, LinearLayout.Orientation.VERTICAL);
		extraButtons.spacing(5);

		extraButtons.addChild(Button.builder(Component.literal("Farming Toolkit"), this::toolkit)
				.size(80, 16)
				.build());
		extraButtons.addChild(Button.builder(Component.literal("Hunting Toolkit"), this::huntingToolkit)
				.size(80, 16)
				.build());
		extraButtons.addChild(Button.builder(Component.literal("Storage Home"), this::home)
				.size(80, 16)
				.build());
		extraButtons.addChild(Button.builder(Component.literal("Hide Overlay"), this::hide)
				.size(80, 16)
				.build());

		extraButtons.arrangeElements();
		extraButtons.visitWidgets(this::addRenderableWidget);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		this.extractTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public void onClose() {
		savedScroll = 0;
		if (BomboConfig.get().storageOverlayRememberSearch && grid != null) {
			savedScroll = grid.getScrollAmount();
		} else {
			savedSearch = "";
		}
		if (BomboConfig.get().storageOverlayRememberOpened && grid != null && grid.openBackpack != null) {
			savedIndex = grid.openBackpack.index;
			savedScroll = grid.getScrollAmount();
		} else {
			savedIndex = -1;
		}
		super.onClose();
	}

	@Override
	protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
		if (slot.container instanceof Inventory || grid == null) {
			super.extractSlot(graphics, slot, mouseX, mouseY);
		} else {
			ScreenRectangle rectangle = grid.getGridRectangle();
			rectangle = new ScreenRectangle(rectangle.position().x() - leftPos, rectangle.position().y() - topPos, rectangle.width(), rectangle.height());
			graphics.enableScissor(rectangle.left(), rectangle.top(), rectangle.right(), rectangle.bottom());
			super.extractSlot(graphics, slot, mouseX, mouseY);
			graphics.disableScissor();
			if (grid.openBackpack != null && !grid.openBackpack.matchedSlots.get(slot.getContainerSlot())) {
				graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, NOT_MATCHED_COLOR);
			}
		}
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int xo, int yo) {
		return super.hasClickedOutside(mouseX, mouseY, xo, yo) && (mouseX < getLeftPos() || mouseX > getLeftPos() + width || mouseY < this.topPos || mouseY > this.topPos + getHeight());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
			return super.mouseScrolled(x, y, scrollX, scrollY);
		}
		return this.getChildAt(x, y).filter(child -> child.mouseScrolled(x, y, scrollX, scrollY)).isPresent();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		ComponentPath focusPath = this.getCurrentFocusPath();
		if (this.minecraft.options.keyInventory.matches(event) && focusPath != null && focusPath.leafComponent() instanceof EditBox) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		super.removed();
		if (saveMousePosition) {
			previousMousePosition = new Vector2d(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, getLeftPos(), this.topPos, getWidth(), getHeight());
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos + getHeight() - 3, 0, 132, 175, 90, 256, 256);
	}

	private class BackpackGridWidget extends SearchableGridWidget {
		private final List<BackpackWidget> backpackWidgets = new ArrayList<>();
		private StorageOverlayScreen.@Nullable BackpackWidget openBackpack = null;
		@Nullable
		private final Button reloadButton;

		private BackpackGridWidget(int x, int y, int width, int height, int maxStoragesPerRow, int internalCols, boolean packed) {
			int expectedWidth = internalCols * SLOT_SIZE + EDGE_PADDING * 2;
			while (expectedWidth > width - AbstractScrollArea.SCROLLBAR_WIDTH && internalCols > 1) {
				expectedWidth = --internalCols * SLOT_SIZE + EDGE_PADDING * 2;
			}

			super(x, y, width, height, Component.literal("BackPack grid"), expectedWidth, maxStoragesPerRow, packed, true);

			boolean storageLoaded = false;
			BackpackPreview.Storage[] storages = BackpackPreview.getStorages();
			for (int i = 0; i < storages.length; i++) {
				BackpackPreview.Storage storage = storages[i];
				boolean open = StorageOverlayScreen.openStorage == i;
				if (storage != null) {
					storageLoaded = true;
					BackpackWidget widget = new BackpackWidget(internalCols, i, storage, open);
					if (open) {
						openBackpack = widget;
					}
					backpackWidgets.add(widget);
				}
			}

			if (!storageLoaded) {
				reloadButton = Button.builder(Component.literal("Open /storage to discover backpacks"), this::reload)
						.size(Math.min(300, width - 20), 30)
						.build();
			} else {
				reloadButton = null;
			}
		}

		private void reload(Button button) {
			MessageScheduler.INSTANCE.sendMessageAfterCooldown("/storage", true);
		}

		@Override
		protected Collection<? extends AbstractWidget> filterWidgets(String input) {
			savedSearch = input;
			if (reloadButton != null) {
				return List.of(reloadButton);
			}
			return backpackWidgets.stream().filter(backpack -> backpack.matches(input)).toList();
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			if (hoveredSlot != null && getGridRectangle().containsPoint((int) mouseX, (int) mouseY)) {
				return false;
			}
			return super.isMouseOver(mouseX, mouseY);
		}

		@Override
		protected double scrollRate() {
			return 15;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
			super.extractWidgetRenderState(context, mouseX, mouseY, deltaTicks);

			if (openBackpack != null && (openBackpack.getBottom() < getY() + HEADER_H || openBackpack.getY() >= getBottom())) {
				handler.moveBackpackSlots(0, -1000, 9);
			}
		}
	}

	private class BackpackWidget extends AbstractWidget {
		private final int rows;
		private final int columns;
		private final String label;
		private final int index;
		private final BackpackPreview.Storage storage;
		private final boolean open;
		private final BitSet matchedSlots = new BitSet();

		private BackpackWidget(int columns, int index, BackpackPreview.Storage storage, Boolean open) {
			int rows = Math.max(1, Math.ceilDiv(storage.size() - 9, columns));
			if (open) {
				rows = Math.max(1, Math.ceilDiv(handler.getContainer().getContainerSize() - 9, columns));
			}
			super(0, 0, columns * SLOT_SIZE + EDGE_PADDING * 2, rows * SLOT_SIZE + HEADER_H + EDGE_PADDING, Component.literal("Backpack preview"));
			this.rows = rows;
			this.columns = columns;
			this.label = getStorageName(index);
			this.index = index;
			this.storage = storage;
			this.open = open;
		}

		private int size() {
			if (open) {
				return handler.getContainer().getContainerSize();
			} else {
				return storage.size();
			}
		}

		public boolean matches(String filter) {
			matchedSlots.clear();
			final String filterLowerCase = filter.toLowerCase(Locale.ENGLISH).trim();

			for (int i = 9; i < size(); ++i) {
				ItemStack currentStack = open ? handler.getContainer().getItem(i) : storage.getStack(i);
				if (!currentStack.isEmpty()) {
					if (filterLowerCase.isEmpty()) {
						matchedSlots.set(i);
						continue;
					}
					if (currentStack.getHoverName().getString().toLowerCase(Locale.ENGLISH).contains(filterLowerCase)) {
						matchedSlots.set(i);
						continue;
					}
					ItemLore lore = currentStack.get(DataComponents.LORE);
					if (lore != null) {
						for (Component line : lore.lines()) {
							if (line.getString().toLowerCase(Locale.ENGLISH).contains(filterLowerCase)) {
								matchedSlots.set(i);
								break;
							}
						}
					}
				}
			}
			return matchedSlots.cardinality() != 0 || open || filterLowerCase.isEmpty();
		}

		private void customInventorySize(GuiGraphicsExtractor graphics, int x, int y, int rows, int columns) {
			extractSection(graphics, x, y, columns, HEADER_H, 0);

			int rowsRemaining = rows;
			while (rowsRemaining > 0) {
				int rowBatch = Math.min(rowsRemaining, MAX_ROW_BATCH);
				int batchH = rowBatch * SLOT_SIZE;
				int rowY = y + HEADER_H + (rows - rowsRemaining) * SLOT_SIZE;
				extractSection(graphics, x, rowY, columns, batchH, HEADER_H);
				rowsRemaining -= rowBatch;
			}

			int bottomY = y + HEADER_H + rows * SLOT_SIZE;
			extractSection(graphics, x, bottomY, columns, EDGE_PADDING, BOTTOM_V);
		}

		private void extractSection(GuiGraphicsExtractor graphics, int x, int y, int columns, int height, int textureYOffset) {
			int columnsRemaining = columns;
			while (columnsRemaining > 0) {
				int batch = Math.min(columnsRemaining, MAX_COL_BATCH);
				int batchX = (columns - columnsRemaining) * SLOT_SIZE + EDGE_PADDING + x;
				graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, batchX, y, EDGE_PADDING, textureYOffset, batch * SLOT_SIZE, height, 256, 256);
				columnsRemaining -= batch;
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, textureYOffset, EDGE_PADDING, height, 256, 256);
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, columns * SLOT_SIZE + EDGE_PADDING + x, y, 169, textureYOffset, EDGE_PADDING, height, 256, 256);
		}

		private void renderCustomBackpack(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int rows, int columns, String label, BackpackPreview.Storage storage) {
			customInventorySize(graphics, x, y, rows, columns);

			Font textRenderer = CLIENT.font;
			graphics.text(textRenderer, label, x + 8, y + 6, 0xFF404040, false);

			for (int i = size() - 9; i < rows * columns; ++i) {
				int itemX = x + i % columns * SLOT_SIZE + 8;
				int itemY = y + i / columns * SLOT_SIZE + SLOT_SIZE;
				graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xB0000000);
			}

			if (!open) {
				for (int i = 9; i < size(); ++i) {
					ItemStack currentStack = storage.getStack(i);
					int itemX = x + (i - 9) % columns * SLOT_SIZE + 8;
					int itemY = y + (i - 9) / columns * SLOT_SIZE + SLOT_SIZE;
					if (!currentStack.isEmpty()) {
						graphics.item(currentStack, itemX, itemY);
						graphics.itemDecorations(textRenderer, currentStack, itemX, itemY);

						if (graphics.containsPointInScissor(mouseX, mouseY) && mouseX > itemX && mouseX <= itemX + SLOT_SIZE && mouseY > itemY && mouseY <= itemY + SLOT_SIZE && mouseY > topPos && mouseY < topPos + StorageOverlayScreen.this.getHeight()) {
							graphics.setTooltipForNextFrame(CLIENT.font, currentStack, mouseX, mouseY);
						}
					}

					if (!matchedSlots.get(i)) {
						graphics.fill(itemX, itemY, itemX + 16, itemY + 16, NOT_MATCHED_COLOR);
					}
				}
			} else {
				handler.moveBackpackSlots(getX() - leftPos + 8, getY() - topPos + SLOT_SIZE, columns);
			}
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			renderCustomBackpack(graphics, mouseX, mouseY, getX(), getY(), rows, columns, label, storage);
			if (open) {
				graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFFFFFF00);
			}
			if (isHovered() && !open) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), 0x1AFFFFFF);
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			super.onClick(event, doubleClick);
			if (!open) {
				switchOpenStorage(index);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {}
	}
}
