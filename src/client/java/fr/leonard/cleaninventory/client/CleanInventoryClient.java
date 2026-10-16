package fr.leonard.cleaninventory.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.leonard.cleaninventory.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class CleanInventoryClient implements ClientModInitializer {
	// R sorts the player inventory; Shift + the same key sorts the open container.
	private static final KeyMapping SORT_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.cleaninventory.sort", InputConstants.KEY_R, KeyMapping.Category.INVENTORY));

	// Buttons sit at the right end of a label row: offsets from the screen's top-left corner.
	private static final int BUTTON_SIZE = 10;
	private static final int BUTTON_RIGHT_MARGIN = 16;
	private static final int BUTTON_ABOVE_LABEL = 2;
	// Creative inventory tab: right of the main inventory, above the destroy slot.
	private static final int CREATIVE_X = 176;
	private static final int CREATIVE_Y = 56;

	@Override
	public void onInitializeClient() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof CreativeModeInventoryScreen creative) {
				Button button = addSortButton(screen, CREATIVE_X, CREATIVE_Y, "button.cleaninventory.sort",
					() -> InventorySorter.sortPlayerInventory(client, screen));
				ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
					button.visible = creative.isInventoryOpen());
			} else if (screen instanceof AbstractContainerScreen<?> containerScreen
				&& (screen instanceof InventoryScreen || InventorySorter.isSortableContainer(containerScreen.getMenu()))) {
				AbstractContainerScreenAccessor layout = (AbstractContainerScreenAccessor) screen;
				int rightX = layout.getImageWidth() - BUTTON_RIGHT_MARGIN;
				addSortButton(screen, rightX, layout.getInventoryLabelY() - BUTTON_ABOVE_LABEL, "button.cleaninventory.sort",
					() -> InventorySorter.sortPlayerInventory(client, screen));
				if (!(screen instanceof InventoryScreen)) {
					addSortButton(screen, rightX, layout.getTitleLabelY() - BUTTON_ABOVE_LABEL, "button.cleaninventory.sort_container",
						() -> InventorySorter.sortContainer(client));
				}
			} else {
				return;
			}

			ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
				if (!SORT_KEY.matches(event) || s.isInputCaptured()
					|| s instanceof CreativeModeInventoryScreen creative && !creative.isInventoryOpen()) {
					return;
				}
				if (!event.hasShiftDown()) {
					InventorySorter.sortPlayerInventory(client, s);
				} else if (s instanceof AbstractContainerScreen<?> c && InventorySorter.isSortableContainer(c.getMenu())) {
					InventorySorter.sortContainer(client);
				}
			});
		});
	}

	private static Button addSortButton(Screen screen, int x, int y, String tooltipKey, Runnable action) {
		AbstractContainerScreenAccessor pos = (AbstractContainerScreenAccessor) screen;
		Button button = Button.builder(Component.literal("S"), b -> action.run())
			.bounds(pos.getLeftPos() + x, pos.getTopPos() + y, BUTTON_SIZE, BUTTON_SIZE)
			.tooltip(Tooltip.create(Component.translatable(tooltipKey)))
			.build();
		Screens.getWidgets(screen).add(button);
		// The recipe book shifts the inventory when toggled, so follow it every frame.
		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
			button.setPosition(pos.getLeftPos() + x, pos.getTopPos() + y));
		return button;
	}
}
