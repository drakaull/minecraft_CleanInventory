package fr.leonard.cleaninventory.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Sorts the 27 main inventory slots (hotbar and armor untouched) in creative menu order. */
public final class InventorySorter {
	// Inventory indices 9-35 are the main inventory; in InventoryMenu the menu slot index is the same.
	private static final int FIRST = 9;
	private static final int END = 36;
	// Hotbar slot used as a temporary buffer for swaps; its content is restored after each swap.
	private static final int BUFFER_HOTBAR = 0;

	private InventorySorter() {
	}

	public static void sort(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) {
			return;
		}

		if (player.hasInfiniteMaterials()) {
			sortCreative(mc, player);
		} else {
			sortSurvival(mc, player);
		}
	}

	private static void sortCreative(Minecraft mc, LocalPlayer player) {
		List<ItemStack> merged = new ArrayList<>();
		for (int i = FIRST; i < END; i++) {
			ItemStack stack = player.getInventory().getItem(i).copy();
			for (ItemStack target : merged) {
				if (stack.isEmpty()) {
					break;
				}
				if (ItemStack.isSameItemSameComponents(target, stack)) {
					int moved = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
					target.grow(moved);
					stack.shrink(moved);
				}
			}
			if (!stack.isEmpty()) {
				merged.add(stack);
			}
		}
		merged.sort(order(mc, player));

		for (int i = FIRST; i < END; i++) {
			int n = i - FIRST;
			ItemStack stack = n < merged.size() ? merged.get(n) : ItemStack.EMPTY;
			player.inventoryMenu.getSlot(i).set(stack);
			mc.gameMode.handleCreativeModeItemAdd(stack, i);
		}
	}

	private static void sortSurvival(Minecraft mc, LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu != player.inventoryMenu || !menu.getCarried().isEmpty()) {
			return;
		}

		// 1. Merge partial stacks: pick up a later stack, drop it on an earlier one, put any remainder back.
		for (int i = FIRST; i < END; i++) {
			for (int j = i + 1; j < END; j++) {
				ItemStack a = menu.getSlot(i).getItem();
				ItemStack b = menu.getSlot(j).getItem();
				if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) {
					break;
				}
				if (ItemStack.isSameItemSameComponents(a, b)) {
					click(mc, player, j, 0, ContainerInput.PICKUP);
					click(mc, player, i, 0, ContainerInput.PICKUP);
					if (!menu.getCarried().isEmpty()) {
						click(mc, player, j, 0, ContainerInput.PICKUP);
					}
				}
			}
		}

		// 2. Selection sort towards the target order, using exact swaps.
		List<ItemStack> target = new ArrayList<>();
		for (int i = FIRST; i < END; i++) {
			target.add(menu.getSlot(i).getItem().copy());
		}
		target.sort(order(mc, player));

		for (int p = FIRST; p < END; p++) {
			ItemStack wanted = target.get(p - FIRST);
			for (int j = p; j < END; j++) {
				if (ItemStack.matches(menu.getSlot(j).getItem(), wanted)) {
					if (j != p) {
						swap(mc, player, p, j);
					}
					break;
				}
			}
		}
	}

	// Three hotbar swaps exchange two slots exactly, whatever they hold (a pickup/drop could merge same items).
	private static void swap(Minecraft mc, LocalPlayer player, int a, int b) {
		click(mc, player, a, BUFFER_HOTBAR, ContainerInput.SWAP);
		click(mc, player, b, BUFFER_HOTBAR, ContainerInput.SWAP);
		click(mc, player, a, BUFFER_HOTBAR, ContainerInput.SWAP);
	}

	private static void click(Minecraft mc, LocalPlayer player, int slot, int button, ContainerInput input) {
		mc.gameMode.handleContainerInput(player.containerMenu.containerId, slot, button, input, player);
	}

	// shortcut: items differing only by components (enchanted books, potions) keep their current relative order;
	// compare components too if that ordering matters.
	private static Comparator<ItemStack> order(Minecraft mc, LocalPlayer player) {
		Map<Item, Integer> index = creativeIndex(mc, player);
		return Comparator.comparing(ItemStack::isEmpty)
			.thenComparingInt((ItemStack s) -> index.getOrDefault(s.getItem(), Integer.MAX_VALUE))
			.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
	}

	private static Map<Item, Integer> creativeIndex(Minecraft mc, LocalPlayer player) {
		ClientPacketListener connection = player.connection;
		boolean hasPermissions = player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
		// Same as CreativeModeInventoryScreen: when tabs get rebuilt, the creative search trees must be refreshed too.
		if (CreativeModeTabs.tryRebuildTabContents(connection.enabledFeatures(), hasPermissions, player.level().registryAccess())) {
			List<ItemStack> searchItems = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
			connection.searchTrees().updateCreativeTooltips(player.level().registryAccess(), searchItems);
			connection.searchTrees().updateCreativeTags(searchItems);
		}

		Map<Item, Integer> index = new HashMap<>();
		for (ItemStack stack : CreativeModeTabs.searchTab().getDisplayItems()) {
			index.putIfAbsent(stack.getItem(), index.size());
		}
		return index;
	}
}
