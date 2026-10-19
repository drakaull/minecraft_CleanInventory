package fr.leonard.cleaninventory.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import fr.leonard.cleaninventory.CleanInventory;
import net.fabricmc.loader.api.FabricLoader;

/** Sort modes saved in config/cleaninventory.json: a default, plus the modes chosen by right-clicking a sort button. */
public final class SortConfig {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(CleanInventory.MOD_ID + ".json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static SortConfig instance = new SortConfig();

	private SortMode defaultMode = SortMode.CREATIVE;
	// Null until the inventory sort button is right-clicked.
	private SortMode inventoryMode;
	// Keyed by ContainerKeys.of(...).
	private Map<String, SortMode> containerModes = new HashMap<>();

	public static SortConfig get() {
		return instance;
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			SortConfig loaded = GSON.fromJson(Files.readString(FILE), SortConfig.class);
			if (loaded != null) {
				if (loaded.defaultMode == null) {
					loaded.defaultMode = SortMode.CREATIVE;
				}
				if (loaded.containerModes == null) {
					loaded.containerModes = new HashMap<>();
				}
				loaded.containerModes.values().removeIf(mode -> mode == null);
				instance = loaded;
			}
		} catch (IOException | JsonParseException e) {
			CleanInventory.LOGGER.warn("Could not read {}, using defaults", FILE, e);
		}
	}

	private void save() {
		try {
			Files.writeString(FILE, GSON.toJson(this));
		} catch (IOException e) {
			CleanInventory.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	public SortMode defaultMode() {
		return defaultMode;
	}

	public void setDefaultMode(SortMode mode) {
		defaultMode = mode;
		save();
	}

	public SortMode inventoryMode() {
		return inventoryMode != null ? inventoryMode : defaultMode;
	}

	public void setInventoryMode(SortMode mode) {
		inventoryMode = mode;
		save();
	}

	public SortMode containerMode(String key) {
		return containerModes.getOrDefault(key, defaultMode);
	}

	public void setContainerMode(String key, SortMode mode) {
		containerModes.put(key, mode);
		save();
	}

	/** Forgets every right-click choice, so everything follows the default mode again. */
	public void resetChosenModes() {
		inventoryMode = null;
		containerModes.clear();
		save();
	}
}
