package fr.leonard.cleaninventory.client;

import net.minecraft.network.chat.Component;

public enum SortMode {
	CREATIVE,
	NAME,
	QUANTITY;

	public SortMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public Component displayName() {
		return Component.translatable("sort_mode.cleaninventory." + name().toLowerCase());
	}
}
