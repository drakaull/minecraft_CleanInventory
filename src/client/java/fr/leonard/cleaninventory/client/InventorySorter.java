package fr.leonard.cleaninventory.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Sorts inventory slots in creative menu order, merging partial stacks. */
public final class InventorySorter {
	// Player inventory indices 9-35 are the main inventory (0-8 is the hotbar).
	private static final int MAIN_FIRST = 9;
	private static final int MAIN_END = 36;

	private InventorySorter() {
	}

	/** Chests (all kinds), barrels, ender chests, shulker boxes, dispensers, droppers, hoppers, animal chests. */
	public static boolean isSortableContainer(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof DispenserMenu
			|| menu instanceof HopperMenu || menu instanceof HorseInventoryMenu;
	}

	/** Sorts the 27 main inventory slots; hotbar and armor are untouched. */
	public static void sortPlayerInventory(Minecraft mc, Screen screen) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) {
			return;
		}

		if (screen instanceof CreativeModeInventoryScreen) {
			sortCreative(mc, player);
			return;
		}

		Inventory inventory = player.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (Slot slot : player.containerMenu.slots) {
			if (slot.container == inventory && slot.getContainerSlot() >= MAIN_FIRST && slot.getContainerSlot() < MAIN_END) {
				slots.add(slot.index);
			}
		}
		sortByClicks(mc, player, slots);
	}

	/** Sorts the open container's own slots; animal saddle and armor slots are untouched. */
	public static void sortContainer(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || !isSortableContainer(player.containerMenu)) {
			return;
		}

		List<Integer> slots = new ArrayList<>();
		for (Slot slot : player.containerMenu.slots) {
			if (slot.container != player.getInventory() && !(slot instanceof ArmorSlot)) {
				slots.add(slot.index);
			}
		}
		sortByClicks(mc, player, slots);
	}

	// The creative screen has its own menu, so slots are written directly like vanilla creative does.
	private static void sortCreative(Minecraft mc, LocalPlayer player) {
		List<ItemStack> merged = new ArrayList<>();
		for (int i = MAIN_FIRST; i < MAIN_END; i++) {
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

		// In InventoryMenu the menu slot index equals the inventory index for the main inventory.
		for (int i = MAIN_FIRST; i < MAIN_END; i++) {
			int n = i - MAIN_FIRST;
			ItemStack stack = n < merged.size() ? merged.get(n) : ItemStack.EMPTY;
			player.inventoryMenu.getSlot(i).set(stack);
			mc.gameMode.handleCreativeModeItemAdd(stack, i);
		}
	}

	// Simulates player clicks, so it works on any server without the mod.
	private static void sortByClicks(Minecraft mc, LocalPlayer player, List<Integer> slots) {
		AbstractContainerMenu menu = player.containerMenu;
		if (slots.isEmpty()) {
			return;
		}

		// An item held on the cursor is set down in an empty slot during the sort, then picked up again.
		// With no room for it, it stays held: hotbar swaps ignore the cursor, but merging needs it empty, so it is skipped.
		int parked = -1;
		if (!menu.getCarried().isEmpty()) {
			parked = parkingSlot(menu, slots);
			if (parked >= 0) {
				click(mc, player, parked, 0, ContainerInput.PICKUP);
				slots = new ArrayList<>(slots);
				slots.remove(Integer.valueOf(parked));
			}
		}

		int buffer = swapBuffer(player, menu, slots);
		if (buffer >= 0) {
			if (menu.getCarried().isEmpty()) {
				merge(mc, player, menu, slots);
			}
			sort(mc, player, menu, slots, buffer);
		}

		if (parked >= 0) {
			click(mc, player, parked, 0, ContainerInput.PICKUP);
		}
	}

	// An empty slot accepting the held item, outside the sorted slots if possible, else the last empty sorted slot; or -1.
	private static int parkingSlot(AbstractContainerMenu menu, List<Integer> slots) {
		ItemStack carried = menu.getCarried();
		for (Slot slot : menu.slots) {
			if (!slots.contains(slot.index) && !slot.hasItem() && slot.mayPlace(carried)
				&& slot.getMaxStackSize(carried) >= carried.getCount()) {
				return slot.index;
			}
		}
		for (int x = slots.size() - 1; x >= 0; x--) {
			Slot slot = menu.getSlot(slots.get(x));
			if (!slot.hasItem() && slot.mayPlace(carried) && slot.getMaxStackSize(carried) >= carried.getCount()) {
				return slot.index;
			}
		}
		return -1;
	}

	// Merges partial stacks: pick up a later stack, drop it on an earlier one, put any remainder back.
	private static void merge(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu, List<Integer> slots) {
		for (int x = 0; x < slots.size(); x++) {
			int i = slots.get(x);
			for (int y = x + 1; y < slots.size(); y++) {
				int j = slots.get(y);
				ItemStack a = menu.getSlot(i).getItem();
				if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) {
					break;
				}
				if (ItemStack.isSameItemSameComponents(a, menu.getSlot(j).getItem())) {
					click(mc, player, j, 0, ContainerInput.PICKUP);
					click(mc, player, i, 0, ContainerInput.PICKUP);
					if (!menu.getCarried().isEmpty()) {
						click(mc, player, j, 0, ContainerInput.PICKUP);
					}
				}
			}
		}
	}

	// Selection sort towards the target order, using exact swaps.
	private static void sort(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu, List<Integer> slots, int buffer) {
		List<ItemStack> target = new ArrayList<>();
		for (int slot : slots) {
			target.add(menu.getSlot(slot).getItem().copy());
		}
		target.sort(order(mc, player));

		for (int x = 0; x < slots.size(); x++) {
			for (int y = x; y < slots.size(); y++) {
				if (ItemStack.matches(menu.getSlot(slots.get(y)).getItem(), target.get(x))) {
					if (y != x) {
						swap(mc, player, slots.get(x), slots.get(y), buffer);
					}
					break;
				}
			}
		}
	}

	// A hotbar slot whose content may pass through every sorted slot (e.g. no shulker box into a shulker box), or -1.
	private static int swapBuffer(LocalPlayer player, AbstractContainerMenu menu, List<Integer> slots) {
		for (int h = 0; h < Inventory.getSelectionSize(); h++) {
			ItemStack held = player.getInventory().getItem(h);
			if (held.isEmpty() || slots.stream().allMatch(s -> menu.getSlot(s).mayPlace(held))) {
				return h;
			}
		}
		return -1;
	}

	// Three hotbar swaps exchange two slots exactly, whatever they hold (a pickup/drop could merge same items).
	private static void swap(Minecraft mc, LocalPlayer player, int a, int b, int buffer) {
		click(mc, player, a, buffer, ContainerInput.SWAP);
		click(mc, player, b, buffer, ContainerInput.SWAP);
		click(mc, player, a, buffer, ContainerInput.SWAP);
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
