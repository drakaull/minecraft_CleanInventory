package fr.leonard.cleaninventory.client;

import fr.leonard.cleaninventory.client.mixin.AbstractMountInventoryScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Identifies the open container so each one keeps its own sort mode. The server does not tell the client
 * where a container is, so it is taken from what the player is looking at when the screen opens.
 */
public final class ContainerKeys {
	private ContainerKeys() {
	}

	/** A key unique per container and world/server, or null if the container cannot be identified. */
	public static @Nullable String of(Minecraft mc, AbstractContainerScreen<?> screen) {
		String world = worldId(mc);
		if (world == null || mc.level == null) {
			return null;
		}

		// The mount is known even when its inventory is opened while riding it.
		if (screen instanceof AbstractMountInventoryScreen<?>) {
			return world + "|entity|" + ((AbstractMountInventoryScreenAccessor) screen).getMount().getUUID();
		}

		HitResult hit = mc.hitResult;
		if (hit instanceof EntityHitResult entityHit) {
			return world + "|entity|" + entityHit.getEntity().getUUID();
		}
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			if (mc.level.getBlockEntity(pos) == null) {
				return null;
			}
			BlockState state = mc.level.getBlockState(pos);
			// Every ender chest shows the same inventory, so they share one mode.
			if (state.getBlock() instanceof EnderChestBlock) {
				return world + "|ender_chest";
			}
			// Both halves of a double chest map to the same key.
			if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
				if (other.compareTo(pos) < 0) {
					pos = other;
				}
			}
			return world + "|" + mc.level.dimension().identifier() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
		}
		return null;
	}

	private static @Nullable String worldId(Minecraft mc) {
		if (mc.getSingleplayerServer() != null) {
			return "singleplayer:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
		}
		if (mc.getCurrentServer() != null) {
			return "server:" + mc.getCurrentServer().ip;
		}
		return null;
	}
}
