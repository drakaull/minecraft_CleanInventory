package fr.leonard.cleaninventory.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.leonard.cleaninventory.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class CleanInventoryClient implements ClientModInitializer {
	private static final KeyMapping SORT_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.cleaninventory.sort", InputConstants.KEY_R, KeyMapping.Category.INVENTORY));

	// Button offsets from the screen's top-left corner.
	// Survival: right end of the "Inventory" label row. Creative: right of the main inventory, above the destroy slot.
	private static final int SURVIVAL_X = 160;
	private static final int SURVIVAL_Y = 70;
	private static final int CREATIVE_X = 176;
	private static final int CREATIVE_Y = 56;
	private static final int BUTTON_SIZE = 10;

	@Override
	public void onInitializeClient() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				addSortButton(client, screen, SURVIVAL_X, SURVIVAL_Y);
			} else if (screen instanceof CreativeModeInventoryScreen creative) {
				Button button = addSortButton(client, screen, CREATIVE_X, CREATIVE_Y);
				ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
					button.visible = creative.isInventoryOpen());
			}

			if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
				ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
					boolean creativeOtherTab = s instanceof CreativeModeInventoryScreen creative && !creative.isInventoryOpen();
					if (SORT_KEY.matches(event) && !s.isInputCaptured() && !creativeOtherTab) {
						InventorySorter.sort(client);
					}
				});
			}
		});
	}

	private static Button addSortButton(Minecraft client, Screen screen, int x, int y) {
		AbstractContainerScreenAccessor pos = (AbstractContainerScreenAccessor) screen;
		Button button = Button.builder(Component.literal("S"), b -> InventorySorter.sort(client))
			.bounds(pos.getLeftPos() + x, pos.getTopPos() + y, BUTTON_SIZE, BUTTON_SIZE)
			.tooltip(Tooltip.create(Component.translatable("button.cleaninventory.sort")))
			.build();
		Screens.getWidgets(screen).add(button);
		// The recipe book shifts the inventory when toggled, so follow it every frame.
		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
			button.setPosition(pos.getLeftPos() + x, pos.getTopPos() + y));
		return button;
	}
}
