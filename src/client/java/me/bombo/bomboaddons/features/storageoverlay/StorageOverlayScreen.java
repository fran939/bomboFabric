package me.bombo.bomboaddons.features.storageoverlay;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ClickLogic;
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

	//
	// The toolbar is a fixed set of three buttons - the player can move and resize it from the HUD
	// editor (/b gui) but not add, rename, disable or remove buttons.
	public static final String[][] FIXED_TOOLBAR = {
			{ "Hide Overlay", "hide" },
			{ "Farming Toolkit", "/farmingtoolkit" },
			{ "Hunting Toolkit", "/huntingtoolkit" }
	};

	// Toolbar metrics (unscaled). The whole toolbar is scaled by storageToolbarScale.
	public static final int TOOLBAR_BASE_W = 104;
	public static final int TOOLBAR_BASE_H = 16;
	public static final int TOOLBAR_GAP = 4;

	public static int openStorage;
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

	public static void setup() { //already had init therefore called setup
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
		this.titleLabelY = -1000; //let title exist for backpack previews caching to work.
		this.inventoryLabelY += 2;
		this.handler = handler;
		this.defaultHandler = defaultHandler;
		this.name = name;
		this.oldHeight = height - 87;
		setup();
	}

	public static boolean enabled(String rawTitle) {
		if (rawTitle == null) return false;
		String title = net.minecraft.ChatFormatting.stripFormatting(rawTitle).trim().toLowerCase(Locale.ROOT);
		openStorage = BackpackPreview.getStorageIndexFromTitle(title);
		boolean isMainStorageMenu = title.equals("storage") || title.startsWith("storage ") || title.equals("almacenamiento") || title.startsWith("almacenamiento ");
		boolean isStorageMenu = isMainStorageMenu || title.contains("ender chest") || title.contains("cofre de ender") || title.contains("backpack") || title.contains("mochila");
		if (disableOnNextLoad) {
			disableOnNextLoad = false;
			me.bombo.bomboaddons.Bomboaddons.LOGGER.info("[StorageOverlay] StorageOverlayScreen.enabled: disableOnNextLoad was true! Bypassing overlay once.");
			return false;
		}
		boolean result = BomboConfig.get().storageOverlay && (isStorageMenu || openStorage != -1);
		me.bombo.bomboaddons.Bomboaddons.LOGGER.info("[StorageOverlay] StorageOverlayScreen.enabled check: raw='{}', title='{}', isStorageMenu={}, openStorage={}, configOn={}, result={}", 
				rawTitle, title, isStorageMenu, openStorage, BomboConfig.get().storageOverlay, result);
		return result;
	}

	protected void switchOpenStorage(int index) {
		if (grid != null) {
			savedScroll = grid.getScrollAmount();
			savedSearch = grid.getSearch();
		}
		MessageScheduler.INSTANCE.sendMessageAfterCooldown(getCommandForIndex(index), true);
		saveMousePosition = BomboConfig.get().storageOverlayDoNotResetCursor;
	}

	public void refreshSearch() {
		if (grid == null) return;
		grid.rebuildBackpacks();
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
		CLIENT.setScreenAndShow(new ContainerScreen(defaultHandler, CLIENT.player.getInventory(), name));
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
		if (savedSearch != null && !savedSearch.isEmpty()) {
			grid.setSearch(savedSearch);
		}
		grid.refreshSearch();
		grid.setScrollAmount(savedScroll);
		if (grid.openBackpack != null) {
			grid.scrollToWidget(grid.openBackpack);
			savedScroll = grid.getScrollAmount();
		}
		this.addRenderableWidget(grid);

		BomboConfig.Settings s = BomboConfig.get();
		if (s == null) return;
		if (s.storageOverlayButtons == null) {
			s.storageOverlayButtons = new ArrayList<>();
		}

		int tx = s.storageToolbarX >= 0 ? s.storageToolbarX : getToolbarDefaultX(this.width);
		int ty = s.storageToolbarY >= 0 ? s.storageToolbarY : getToolbarDefaultY(this.height);
		int buttonW = getToolbarWidth();
		int buttonH = toolbarButtonHeight();
		int gap = toolbarGap();
		int y = ty;
		for (String[] entry : FIXED_TOOLBAR) {
			final String label = entry[0];
			final String command = entry[1];
			this.addRenderableWidget(Button.builder(Component.literal(label), b -> runToolbarAction(command))
					.bounds(tx, y, buttonW, buttonH)
					.build());
			y += buttonH + gap;
		}
	}

	/** Runs a fixed toolbar button: {@code hide}/{@code close}/{@code exit} closes the overlay, anything else is sent as a command. */
	private void runToolbarAction(String command) {
		if (command == null) return;
		String cmd = command.trim();
		if (cmd.isEmpty()) return;
		if (cmd.equalsIgnoreCase("hide") || cmd.equalsIgnoreCase("close") || cmd.equalsIgnoreCase("exit")) {
			hide(null);
			return;
		}
		if (!cmd.startsWith("/")) cmd = "/" + cmd;
		MessageScheduler.INSTANCE.sendMessageAfterCooldown(cmd, true);
	}

	public static int getToolbarButtonCount() {
		return FIXED_TOOLBAR.length;
	}

	private static float toolbarScale() {
		BomboConfig.Settings s = BomboConfig.get();
		float scale = s == null ? 1.0f : s.storageToolbarScale;
		return scale > 0.1f ? scale : 1.0f;
	}

	private static int toolbarButtonHeight() {
		return Math.max(9, Math.round(TOOLBAR_BASE_H * toolbarScale()));
	}

	private static int toolbarGap() {
		return Math.max(1, Math.round(TOOLBAR_GAP * toolbarScale()));
	}

	public static int getToolbarWidth() {
		return Math.max(40, Math.round(TOOLBAR_BASE_W * toolbarScale()));
	}

	public static int getToolbarHeight() {
		int count = Math.max(1, getToolbarButtonCount());
		int buttonH = toolbarButtonHeight();
		return count * buttonH + (count - 1) * toolbarGap();
	}

	public static int getToolbarDefaultX(int screenWidth) {
		return Math.max(2, screenWidth - getToolbarWidth() - 6);
	}

	public static int getToolbarDefaultY(int screenHeight) {
		return Math.max(20, (screenHeight - getToolbarHeight()) / 2);
	}

	/** Preview used by the HUD edit screen so the toolbar can be positioned and scaled with /b gui. */
	public static void drawToolbarPreview(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		BomboConfig.Settings s = BomboConfig.get();
		if (s == null) return;
		int width = getToolbarWidth();
		int buttonH = toolbarButtonHeight();
		int gap = toolbarGap();
		int cursor = y;
		boolean any = false;
		if (s.storageOverlayButtons != null) {
			for (BomboConfig.StorageOverlayButton entry : s.storageOverlayButtons) {
				if (entry == null || !entry.enabled) continue;
				any = true;
				String label = entry.label == null || entry.label.isEmpty()
						? (entry.command == null || entry.command.isEmpty() ? "Button" : entry.command)
						: entry.label;
				graphics.fill(x, cursor, x + width, cursor + buttonH, 0xEE0B0F19);
				graphics.fill(x, cursor, x + width, cursor + 1, 0xFF38BDF8);
				graphics.centeredText(font, "§f" + label, x + width / 2, cursor + (buttonH - 8) / 2, -1);
				cursor += buttonH + gap;
			}
		}
		if (!any) {
			int h = toolbarButtonHeight();
			graphics.fill(x, y, x + width, y + h, 0xEE0B0F19);
			graphics.centeredText(font, "§7No Buttons", x + width / 2, y + (h - 8) / 2, -1);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		try (me.bombo.bomboaddons.PerformanceProfiler.Scope p = me.bombo.bomboaddons.PerformanceProfiler.scope("Storage: Overlay Render")) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
			this.extractTooltip(graphics, mouseX, mouseY);
		}
	}

	@Override
	public void onClose() {
		if (grid != null) {
			savedScroll = grid.getScrollAmount();
			if (BomboConfig.get().storageOverlayRememberSearch || !grid.getSearch().isEmpty()) {
				savedSearch = grid.getSearch();
			} else {
				savedSearch = "";
			}
			if (BomboConfig.get().storageOverlayRememberOpened && grid.openBackpack != null) {
				savedIndex = grid.openBackpack.index;
			} else {
				savedIndex = -1;
			}
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

			String search = grid.getSearch() != null ? grid.getSearch().trim().toLowerCase(Locale.ENGLISH) : "";
			if (!search.isEmpty() && grid.openBackpack != null) {
				boolean matches = false;
				ItemStack stack = slot.getItem();
				if (!stack.isEmpty()) {
					if (stack.getHoverName().getString().toLowerCase(Locale.ENGLISH).contains(search)) {
						matches = true;
					} else {
						ItemLore lore = stack.get(DataComponents.LORE);
						if (lore != null) {
							for (Component line : lore.lines()) {
								if (line.getString().toLowerCase(Locale.ENGLISH).contains(search)) {
									matches = true;
									break;
								}
							}
						}
					}
				}
				if (!matches) {
					graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, NOT_MATCHED_COLOR);
				}
			}
		}
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int xo, int yo) {
		return super.hasClickedOutside(mouseX, mouseY, xo, yo) && (mouseX < getLeftPos() || mouseX > getLeftPos() + width || mouseY < this.topPos || mouseY > this.topPos + getHeight());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (grid != null && grid.mouseScrolled(x, y, scrollX, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (grid != null && grid.backpackWidgets != null) {
			for (BackpackWidget bw : grid.backpackWidgets) {
				if (bw.isEditingTitle && bw.inlineTitleBox != null) {
					if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
						bw.commitInlineRename();
						return true;
					}
					if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
						bw.cancelInlineRename();
						return true;
					}
					if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
						String val = bw.inlineTitleBox.getValue();
						if (!val.isEmpty()) {
							bw.inlineTitleBox.setValue(val.substring(0, val.length() - 1));
						}
						return true;
					}
					bw.inlineTitleBox.setFocused(true);
					if (bw.inlineTitleBox.keyPressed(event)) {
						return true;
					}
					// Always consume keys while editing title so inventory/movement keys don't close the GUI
					return true;
				}
			}
		}
		ComponentPath focusPath = this.getCurrentFocusPath();
		if (this.minecraft.options.keyInventory.matches(event) && focusPath != null && focusPath.leafComponent() instanceof EditBox) {
			return true;
		}

		// Hotkey navigation: Next Page, Prev Page, Smart Go Back
		boolean isTypingInSearch = grid != null && grid.searchField != null && grid.searchField.isFocused();
		if (!isTypingInSearch) {
			int key = event.key();
			int nextKey = ClickLogic.getKeyCode(BomboConfig.get().nextPageKey);
			int prevKey = ClickLogic.getKeyCode(BomboConfig.get().prevPageKey);
			int goBackKey = ClickLogic.getKeyCode(BomboConfig.get().goBackKey);
			int smartBackKey = ClickLogic.getKeyCode(BomboConfig.get().smartGoBackKey);

			if (key != -1 && key == nextKey) {
				int nextIdx = (openStorage >= 0) ? openStorage + 1 : 0;
				if (nextIdx < BackpackPreview.STORAGE_SIZE) {
					switchOpenStorage(nextIdx);
					return true;
				}
			}
			if (key != -1 && (key == prevKey || key == smartBackKey || key == goBackKey)) {
				if (openStorage > 0) {
					switchOpenStorage(openStorage - 1);
					return true;
				} else if (openStorage == 0 || key == smartBackKey || key == goBackKey) {
					home(null);
					return true;
				}
			}
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (grid != null && grid.backpackWidgets != null) {
			for (BackpackWidget bw : grid.backpackWidgets) {
				if (bw.isEditingTitle && bw.inlineTitleBox != null) {
					char c = (char) event.codepoint();
					if (c >= 32 && c != 127) {
						bw.inlineTitleBox.insertText(Character.toString(c));
						return true;
					}
				}
			}
		}
		return super.charTyped(event);
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int mouseButton, net.minecraft.world.inventory.ContainerInput type) {
		super.slotClicked(slot, slotId, mouseButton, type);
		int openIdx = openStorage;
		if (openIdx >= 0 && openIdx < BackpackPreview.STORAGE_SIZE && handler != null) {
			BackpackPreview.updateStorageDirectly(openIdx, handler.getContainer());
			refreshSearch();
		}
	}

	@Override
	public void removed() {
		super.removed();
		int openIdx = openStorage;
		if (openIdx >= 0 && openIdx < BackpackPreview.STORAGE_SIZE && handler != null) {
			BackpackPreview.updateStorageDirectly(openIdx, handler.getContainer());
		}
		if (saveMousePosition) {
			previousMousePosition = new Vector2d(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		if (grid != null) {
			savedScroll = grid.getScrollAmount();
		}
		BomboConfig.Settings s = BomboConfig.get();
		String theme = s != null && s.storageOverlayTheme != null ? s.storageOverlayTheme.toUpperCase(Locale.ROOT) : "DEFAULT";
		boolean transparent = s != null && (s.storageOverlayTransparent || theme.equals("TRANSPARENT"));

		if (transparent) {
			int customColor = 0x66000000;
			try {
				if (s != null && s.storageOverlayCustomColor != null && !s.storageOverlayCustomColor.isEmpty()) {
					customColor = (int) Long.parseLong(s.storageOverlayCustomColor.replace("#", ""), 16);
				}
			} catch (Throwable ignored) {}
			graphics.fill(getLeftPos(), this.topPos, getLeftPos() + getWidth(), this.topPos + getHeight(), customColor);
			graphics.outline(getLeftPos(), this.topPos, getWidth(), getHeight(), 0x44FFFFFF);
		} else if (theme.equals("DARK")) {
			graphics.fill(getLeftPos(), this.topPos, getLeftPos() + getWidth(), this.topPos + getHeight(), 0xEE1A1A1A);
			graphics.outline(getLeftPos(), this.topPos, getWidth(), getHeight(), 0xFF333333);
		} else if (theme.equals("LIGHT")) {
			graphics.fill(getLeftPos(), this.topPos, getLeftPos() + getWidth(), this.topPos + getHeight(), 0xEEF0F0F0);
			graphics.outline(getLeftPos(), this.topPos, getWidth(), getHeight(), 0xFFCCCCCC);
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, getLeftPos(), this.topPos, getWidth(), getHeight());
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos + getHeight() - 3, 0, 132, 175, 90, 256, 256);
		}
	}

	private class BackpackGridWidget extends SearchableGridWidget {
		private final List<BackpackWidget> backpackWidgets = new ArrayList<>();
		private StorageOverlayScreen.@Nullable BackpackWidget openBackpack = null;
		@Nullable
		private Button reloadButton;
		private final int internalCols;

		private BackpackGridWidget(int x, int y, int width, int height, int maxStoragesPerRow, int internalCols, boolean packed) {
			int expectedWidth = internalCols * SLOT_SIZE + EDGE_PADDING * 2;
			while (expectedWidth > width - AbstractScrollArea.SCROLLBAR_WIDTH && internalCols > 1) {
				expectedWidth = --internalCols * SLOT_SIZE + EDGE_PADDING * 2;
			}

			super(x, y, width, height, Component.literal("BackPack grid"), expectedWidth, maxStoragesPerRow, packed, true);
			this.internalCols = internalCols;
			rebuildBackpacks();
		}

		public void rebuildBackpacks() {
			backpackWidgets.clear();
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
						.size(Math.min(300, getWidth() - 20), 30)
						.build();
			} else {
				reloadButton = null;
			}
		}

		private void reload(Button button) {
			disableOnNextLoad = true;
			MessageScheduler.INSTANCE.sendMessageAfterCooldown("/storage", true);
		}

		@Override
		protected Collection<? extends AbstractWidget> filterWidgets(String input) {
			savedSearch = input;
			if (backpackWidgets.isEmpty()) {
				rebuildBackpacks();
			}
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
		private String label;
		private final int index;
		private final BackpackPreview.Storage storage;
		private final boolean open;
		private final BitSet matchedSlots = new BitSet();
		private boolean isEditingTitle = false;
		private EditBox inlineTitleBox = null;

		private BackpackWidget(int columns, int index, BackpackPreview.Storage storage, Boolean open) {
			int rows = Math.max(1, Math.ceilDiv(storage.size() - 9, columns));
			if (open) {
				rows = Math.max(rows, Math.ceilDiv(handler.getContainer().getContainerSize() - 9, columns));
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

		public void commitInlineRename() {
			if (isEditingTitle && inlineTitleBox != null) {
				String newName = inlineTitleBox.getValue().trim();
				BackpackPreview.setCustomStorageName(index, newName);
				this.label = getStorageName(index);
				isEditingTitle = false;
				inlineTitleBox = null;
			}
		}

		public void cancelInlineRename() {
			isEditingTitle = false;
			inlineTitleBox = null;
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
			BomboConfig.Settings s = BomboConfig.get();
			String theme = s != null && s.storageOverlayTheme != null ? s.storageOverlayTheme.toUpperCase(Locale.ROOT) : "DEFAULT";
			boolean transparent = s != null && (s.storageOverlayTransparent || theme.equals("TRANSPARENT"));

			if (transparent || theme.equals("DARK") || theme.equals("LIGHT")) {
				int bg = theme.equals("LIGHT") ? 0xDDEEEEEE : (transparent ? 0x55111111 : 0xEE202020);
				int border = theme.equals("LIGHT") ? 0xFFBBBBBB : (transparent ? 0x44FFFFFF : 0xFF383838);
				int w = columns * SLOT_SIZE + EDGE_PADDING * 2;
				int h = rows * SLOT_SIZE + HEADER_H + EDGE_PADDING;
				graphics.fill(x, y, x + w, y + h, bg);
				graphics.outline(x, y, w, h, border);
				return;
			}

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
			BomboConfig.Settings s = BomboConfig.get();
			String theme = s != null && s.storageOverlayTheme != null ? s.storageOverlayTheme.toUpperCase(Locale.ROOT) : "DEFAULT";
			boolean transparent = s != null && (s.storageOverlayTransparent || theme.equals("TRANSPARENT"));
			int titleColor = theme.equals("LIGHT") ? 0xFF222222 : ((transparent || theme.equals("DARK")) ? 0xFFE0E0E0 : 0xFF404040);

			if (isEditingTitle && inlineTitleBox != null) {
				inlineTitleBox.setPosition(x + 6, y + 3);
				inlineTitleBox.extractRenderState(graphics, mouseX, mouseY, 0);
			} else {
				graphics.text(textRenderer, label, x + 8, y + 6, titleColor, false);
			}

			for (int i = size() - 9; i < rows * columns; ++i) {
				int itemX = x + i % columns * SLOT_SIZE + 8;
				int itemY = y + i / columns * SLOT_SIZE + SLOT_SIZE;
				graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xB0000000);
			}

			if (!open) {
				String search = StorageOverlayScreen.this.grid != null ? StorageOverlayScreen.this.grid.getSearch().trim().toLowerCase(Locale.ENGLISH) : "";
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

					if (!search.isEmpty() && !matchedSlots.get(i)) {
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
			if (event.y() >= getY() && event.y() <= getY() + HEADER_H) {
				// Commit any other backpack that is currently being edited
				if (StorageOverlayScreen.this.grid != null && StorageOverlayScreen.this.grid.backpackWidgets != null) {
					for (BackpackWidget other : StorageOverlayScreen.this.grid.backpackWidgets) {
						if (other != this && other.isEditingTitle) {
							other.commitInlineRename();
						}
					}
				}
				if (!isEditingTitle) {
					isEditingTitle = true;
					int boxW = Math.max(80, getWidth() - 16);
					inlineTitleBox = new EditBox(CLIENT.font, getX() + 6, getY() + 3, boxW, 12, Component.literal("Rename"));
					inlineTitleBox.setMaxLength(32);
					inlineTitleBox.setValue(label);
					inlineTitleBox.setFocused(true);
					StorageOverlayScreen.this.setFocused(inlineTitleBox);
				}
				return;
			}
			if (isEditingTitle) {
				commitInlineRename();
			}
			// Also commit any other backpack being edited
			if (StorageOverlayScreen.this.grid != null && StorageOverlayScreen.this.grid.backpackWidgets != null) {
				for (BackpackWidget other : StorageOverlayScreen.this.grid.backpackWidgets) {
					if (other != this && other.isEditingTitle) {
						other.commitInlineRename();
					}
				}
			}
			if (!open) {
				switchOpenStorage(index);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {}
	}
}
