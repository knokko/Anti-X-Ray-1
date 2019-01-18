package nl.knokko.antixray.plugin;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;

import nl.knokko.antixray.chunk.data.ChunkData;
import nl.knokko.antixray.chunk.data.HiddenBlock;
import nl.knokko.antixray.chunk.sections.WorldDataManager;
import nl.knokko.antixray.settings.State;
import nl.knokko.antixray.settings.WorldState;

public class AntiXRayEventHandler implements Listener {

	private long totalCheckTime;
	private long checkCount;

	@EventHandler
	public void onChunkLoad(final ChunkLoadEvent event) {
		AntiXRayPlugin plug = AntiXRayPlugin.getInstance();
		WorldDataManager manager = plug.getWorldDataManager();

		if (plug.getWorldState(event.getWorld()).getState() == State.ACTIVE && !manager.hasChunkData(event.getChunk()))
			Bukkit.getScheduler().scheduleSyncDelayedTask(AntiXRayPlugin.getInstance(), new Runnable() {

				public void run() {
					safeProcess(event.getChunk());
				}
			}, 5);// TODO What if I use this instead of the populate event?
	}

	@EventHandler
	public void onChunkPopulate(ChunkPopulateEvent event) {
		// safeProcess(event.getChunk());
	}

	private void safeProcess(Chunk chunk) {
		World world = chunk.getWorld();
		int chunkX = chunk.getX();
		int chunkZ = chunk.getZ();
		int x = chunkX * 16;
		int z = chunkZ * 16;

		WorldDataManager manager = AntiXRayPlugin.getInstance().getWorldDataManager();
		ChunkData cdNorth = manager.getChunkData(world, x, z - 16);
		ChunkData cdNorthEast = manager.getChunkData(world, x + 16, z - 16);
		ChunkData cdEast = manager.getChunkData(world, x + 16, z);
		ChunkData cdSouthEast = manager.getChunkData(world, x + 16, z + 16);
		ChunkData cdSouth = manager.getChunkData(world, x, z + 16);
		ChunkData cdSouthWest = manager.getChunkData(world, x - 16, z + 16);
		ChunkData cdWest = manager.getChunkData(world, x - 16, z);
		ChunkData cdNorthWest = manager.getChunkData(world, x - 16, z - 16);
		boolean north = cdNorth != null;
		boolean east = cdEast != null;
		boolean south = cdSouth != null;
		boolean west = cdWest != null;
		boolean northEast = cdNorthEast != null;
		boolean southEast = cdSouthEast != null;
		boolean southWest = cdSouthWest != null;
		boolean northWest = cdNorthWest != null;

		processChunk(chunk, null, north, east, south, west);
		processIfCan(cdEast, world, chunkX + 1, chunkZ, northEast, false, southEast, true);
		processIfCan(cdSouth, world, chunkX, chunkZ + 1, true, southEast, false, southWest);
		processIfCan(cdWest, world, chunkX - 1, chunkZ, northWest, true, southWest, false);
		processIfCan(cdNorth, world, chunkX, chunkZ - 1, false, northEast, true, northWest);
		processIfCan(cdNorthEast, world, chunkX + 1, chunkZ - 1, false, false, east, north);
		processIfCan(cdSouthEast, world, chunkX + 1, chunkZ + 1, east, false, false, south);
		processIfCan(cdSouthWest, world, chunkX - 1, chunkZ + 1, west, south, false, false);
		processIfCan(cdNorthWest, world, chunkX - 1, chunkZ - 1, false, north, west, false);
	}

	private void processIfCan(ChunkData cd, World world, int chunkX, int chunkZ, boolean north, boolean east,
			boolean south, boolean west) {
		if (cd != null) {
			processChunk(world.getChunkAt(chunkX, chunkZ), cd, north, east, south, west);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		// TODO check if not restoring and if world needs protection
		// if(!isRestoring && event.getBlock().getWorld().getEnvironment() ==
		// Environment.NORMAL)
		onBlockRemove(event.getBlock());
	}

	private void onBlockRemove(Block block) {
		if (!block.getType().isOccluding()) {
			// Don't bother checking because this block can't hide anything
			return;
		}

		WorldState state = AntiXRayPlugin.getInstance().getWorldState(block.getWorld());

		int blockY = block.getY();

		if (state.getState() != State.ACTIVE || blockY < state.getMinY() - 1 || blockY > state.getMaxY() + 1) {
			// Not active or out of range
			return;
		}

		if (block.getRelative(BlockFace.DOWN).getType() != Material.STONE
				&& block.getRelative(BlockFace.UP).getType() != Material.STONE
				&& block.getRelative(BlockFace.NORTH).getType() != Material.STONE
				&& block.getRelative(BlockFace.SOUTH).getType() != Material.STONE
				&& block.getRelative(BlockFace.EAST).getType() != Material.STONE
				&& block.getRelative(BlockFace.WEST).getType() != Material.STONE) {

			// Only stone blocks could be hidden ores, so there is no block neighbour to
			// hide
			return;
		}
		long startTime = System.nanoTime();
		WorldDataManager manager = AntiXRayPlugin.getInstance().getWorldDataManager();
		int blockX = block.getX();
		int blockZ = block.getZ();
		List<Chunk> chunkList = new ArrayList<Chunk>(3);
		chunkList.add(block.getChunk());
		if (block.getRelative(BlockFace.NORTH).getChunk() != block.getChunk()) {
			chunkList.add(block.getRelative(BlockFace.NORTH).getChunk());
		} else if (block.getRelative(BlockFace.SOUTH).getChunk() != block.getChunk()) {
			chunkList.add(block.getRelative(BlockFace.SOUTH).getChunk());
		}
		if (block.getRelative(BlockFace.EAST).getChunk() != block.getChunk()) {
			chunkList.add(block.getRelative(BlockFace.EAST).getChunk());
		} else if (block.getRelative(BlockFace.WEST).getChunk() != block.getChunk()) {
			chunkList.add(block.getRelative(BlockFace.WEST).getChunk());
		}
		for (Chunk chunk : chunkList) {
			ChunkData cd = manager.getChunkData(chunk);
			if (cd != null) {
				byte[] data = cd.getData();
				for (int dataIndex = 0; dataIndex < data.length; dataIndex += 3) {

					// The value of -2 marks a hidden block as already restored
					if (data[dataIndex + 2] != -2) {
						int y = data[dataIndex + 1] & 0xFF;
						if (y == blockY - 1 || y == blockY || y == blockY + 1) {
							int x = HiddenBlock.getRelativeX(data[dataIndex]) + chunk.getX() * ChunkData.CHUNK_SIZE;
							int z = HiddenBlock.getRelativeZ(data[dataIndex]) + chunk.getZ() * ChunkData.CHUNK_SIZE;
							if (isClose(blockX, blockY, blockZ, x, y, z)) {
								chunk.getWorld().getBlockAt(x, y, z)
										.setType(HiddenBlock.getMaterial(data[dataIndex + 2]));

								// Mark as already restored
								data[dataIndex + 2] = -2;
							}
						}
					}
				}
				long endTime = System.nanoTime();
				totalCheckTime += (endTime - startTime);
				checkCount++;
			} else {
				Bukkit.getLogger().warning("No data for chunk " + block.getChunk());
				processChunk(block.getChunk(), null, true, true, true, true);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void updateExploded(BlockExplodeEvent event) {
		if (AntiXRayPlugin.getInstance().getWorldState(event.getBlock().getWorld()).getState() == State.ACTIVE) {
			List<Block> blocks = event.blockList();
			for (Block block : blocks) {
				onBlockRemove(block);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void updateExploded(EntityExplodeEvent event) {
		if (AntiXRayPlugin.getInstance().getWorldState(event.getEntity().getWorld()).getState() == State.ACTIVE) {
			List<Block> blocks = event.blockList();
			for (Block block : blocks)
				onBlockRemove(block);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void checkPiston(BlockPistonExtendEvent event) {
		if (AntiXRayPlugin.getInstance().getWorldState(event.getBlock().getWorld()).getState() == State.ACTIVE) {
			List<Block> blocks = event.getBlocks();
			for (Block block : blocks)
				onBlockRemove(block);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void checkPiston(BlockPistonRetractEvent event) {
		if (AntiXRayPlugin.getInstance().getWorldState(event.getBlock().getWorld()).getState() == State.ACTIVE) {
			List<Block> blocks = event.getBlocks();
			for (Block block : blocks)
				onBlockRemove(block);
		}
	}

	private void processChunk(Chunk chunk, ChunkData current, boolean hasNorth, boolean hasEast, boolean hasSouth,
			boolean hasWest) {
		int boundX = hasEast ? 16 : 15;
		int boundZ = hasSouth ? 16 : 15;
		int minX = hasWest ? 0 : 1;
		int minZ = hasNorth ? 0 : 1;
		WorldState state = AntiXRayPlugin.getInstance().getWorldState(chunk.getWorld());
		ByteArrayList newData = new ByteArrayList(700);
		for (int cx = minX; cx < boundX; cx++) {
			for (int cy = state.getMinY(); cy <= state.getMaxY(); cy++) {
				for (int cz = minZ; cz < boundZ; cz++) {
					Block block = chunk.getWorld().getBlockAt(cx + chunk.getX() * 16, cy, cz + chunk.getZ() * 16);
					Material type = block.getType();
					if (needsHide(type) && isHidden(block)) {
						block.setType(Material.STONE);
						newData.add(HiddenBlock.getXZ(cx, cz));
						newData.add((byte) cy);
						newData.add(HiddenBlock.getMaterialID(type));
					}
				}
			}
		}
		WorldDataManager manager = AntiXRayPlugin.getInstance().getWorldDataManager();
		if (current == null) {
			manager.createChunkData(newData.toByteArray(), chunk);
		} else {
			current.addBlocks(newData.toByteArray());
		}
	}

	private static boolean needsHide(Material t) {
		return t == Material.COAL_ORE || t == Material.IRON_ORE || t == Material.LAPIS_ORE || t == Material.GOLD_ORE
				|| t == Material.REDSTONE_ORE || t == Material.DIAMOND_ORE || t == Material.EMERALD_ORE;
	}

	private boolean isClose(int x1, int y1, int z1, int x2, int y2, int z2) {
		if (x2 == x1 + 1 && y1 == y2 && z1 == z2)
			return true;
		if (x2 == x1 - 1 && y1 == y2 && z1 == z2)
			return true;
		if (x1 == x2 && y1 == y2 + 1 && z1 == z2)
			return true;
		if (x1 == x2 && y1 == y2 - 1 && z1 == z2)
			return true;
		if (x1 == x2 && y1 == y2 && z1 == z2 + 1)
			return true;
		return x1 == x2 && y1 == y2 && z1 == z2 - 1;
	}

	private static boolean isHidden(Block block) {
		if (!block.getRelative(BlockFace.NORTH).getType().isOccluding())
			return false;
		if (!block.getRelative(BlockFace.EAST).getType().isOccluding())
			return false;
		if (!block.getRelative(BlockFace.SOUTH).getType().isOccluding())
			return false;
		if (!block.getRelative(BlockFace.WEST).getType().isOccluding())
			return false;
		if (!block.getRelative(BlockFace.UP).getType().isOccluding())
			return false;
		return block.getRelative(BlockFace.DOWN).getType().isOccluding();
	}
}