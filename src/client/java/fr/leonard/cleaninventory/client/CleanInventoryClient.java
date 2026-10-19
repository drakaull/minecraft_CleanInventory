package fr.leonard.cleaninventory.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.InputConstants;
import fr.leonard.cleaninventory.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

public class CleanInventoryClient implements ClientModInitializer {
	// R sorts the player inventory; Shift + the same key sorts the open container.
	private static final KeyMapping SORT_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.cleaninventory.sort", InputConstants.KEY_R, KeyMapping.Category.INVENTORY));
	private static final KeyMapping CONFIG_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.cleaninventory.config", InputConstants.KEY_K, KeyMapping.Category.INVENTORY));

	// Buttons sit at the right end of a label row: offsets from the screen's top-left corner.
	private static final int BUTTON_SIZE = 10;
	private static final int BUTTON_RIGHT_MARGIN = 16;
	private static final int BUTTON_ABOVE_LABEL = 2;
	// Creative inventory tab: right of the main inventory, above the destroy slot.
	private static final int CREATIVE_X = 176;
	private static final int CREATIVE_Y = 56;

	@Override
	public void onInitializeClient() {
		SortConfig.load();
		SortConfig config = SortConfig.get();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (CONFIG_KEY.consumeClick()) {
				client.gui.setScreen(new ConfigScreen(null));
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			Supplier<SortMode> inventoryMode = config::inventoryMode;
			Consumer<SortMode> sortInventory = mode -> InventorySorter.sortPlayerInventory(client, screen, mode);
			Supplier<SortMode> containerMode;

			if (screen instanceof CreativeModeInventoryScreen creative) {
				Button button = addSortButton(screen, CREATIVE_X, CREATIVE_Y, "button.cleaninventory.sort",
					inventoryMode, config::setInventoryMode, sortInventory);
				ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
					button.visible = creative.isInventoryOpen());
				containerMode = null;
			} else if (screen instanceof AbstractContainerScreen<?> containerScreen
				&& (screen instanceof InventoryScreen || InventorySorter.isSortableContainer(containerScreen.getMenu()))) {
				AbstractContainerScreenAccessor layout = (AbstractContainerScreenAccessor) screen;
				int rightX = layout.getImageWidth() - BUTTON_RIGHT_MARGIN;
				addSortButton(screen, rightX, layout.getInventoryLabelY() - BUTTON_ABOVE_LABEL, "button.cleaninventory.sort",
					inventoryMode, config::setInventoryMode, sortInventory);

				if (screen instanceof InventoryScreen) {
					containerMode = null;
				} else {
					String key = ContainerKeys.of(client, containerScreen);
					Consumer<SortMode> setContainerMode;
					if (key != null) {
						containerMode = () -> config.containerMode(key);
						setContainerMode = mode -> config.setContainerMode(key, mode);
					} else {
						// Unidentified container: the right-click choice only lasts while this screen is open.
						SortMode[] screenMode = {config.defaultMode()};
						containerMode = () -> screenMode[0];
						setContainerMode = mode -> screenMode[0] = mode;
					}
					addSortButton(screen, rightX, layout.getTitleLabelY() - BUTTON_ABOVE_LABEL, "button.cleaninventory.sort_container",
						containerMode, setContainerMode, mode -> InventorySorter.sortContainer(client, mode));
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
					sortInventory.accept(inventoryMode.get());
				} else if (containerMode != null) {
					InventorySorter.sortContainer(client, containerMode.get());
				}
			});
		});
	}

	// Left click sorts with the current mode; right click switches to the next mode, saves it and sorts.
	private static Button addSortButton(Screen screen, int x, int y, String tooltipKey,
		Supplier<SortMode> mode, Consumer<SortMode> setMode, Consumer<SortMode> sort) {
		AbstractContainerScreenAccessor pos = (AbstractContainerScreenAccessor) screen;
		SortButton button = new SortButton(pos.getLeftPos() + x, pos.getTopPos() + y, () -> sort.accept(mode.get()));
		Runnable updateTooltip = () -> button.setTooltip(Tooltip.create(Component.translatable(tooltipKey, mode.get().displayName())));
		button.rightClick = () -> {
			SortMode next = mode.get().next();
			setMode.accept(next);
			updateTooltip.run();
			sort.accept(next);
		};
		updateTooltip.run();
		Screens.getWidgets(screen).add(button);
		// The recipe book shifts the inventory when toggled, so follow it every frame.
		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
			button.setPosition(pos.getLeftPos() + x, pos.getTopPos() + y));
		return button;
	}

	private static final class SortButton extends Button.Plain {
		private Runnable rightClick = () -> { };

		private SortButton(int x, int y, Runnable leftClick) {
			super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.literal("S"), b -> leftClick.run(), DEFAULT_NARRATION);
		}

		@Override
		protected boolean isValidClickButton(MouseButtonInfo info) {
			return info.button() == InputConstants.MOUSE_BUTTON_LEFT || info.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (input instanceof MouseButtonEvent event && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				rightClick.run();
			} else {
				super.onPress(input);
			}
		}
	}
}
