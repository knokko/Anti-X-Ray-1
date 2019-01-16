package nl.knokko.antixray.chunk.sections;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.World;

import nl.knokko.antixray.chunk.data.ChunkData;

public class WorldDataManager {
	
	private final Collection<Entry> worlds;
	
	private final File folder;
	
	public WorldDataManager(File ownFolder) {
		worlds = new ArrayList<Entry>();
		folder = ownFolder;
	}
	
	private static final int UNUSED_TIME = 60000;
	
	public void unloadUnused() {
		long time = System.currentTimeMillis();
		Iterator<Entry> iterator = worlds.iterator();
		while (iterator.hasNext()) {
			Entry next = iterator.next();
			if (time - next.data.getLastUsed() >= UNUSED_TIME) {
				next.data.unload();
				iterator.remove();
			} else {
				next.data.unloadUnused();
			}
		}
	}
	
	public void unload() {
		for (Entry world : worlds) {
			world.data.unload();
		}
		worlds.clear();
	}
	
	private File getWorldFolder(UUID id) {
		return new File(folder + "/" + id);
	}
	
	private WorldData getWorld(World world) {
		UUID id = world.getUID();
		for (Entry entry : worlds) {
			if (entry.id.equals(id)) {
				entry.data.setLastUsed();
				return entry.data;
			}
		}
		Entry newEntry = new Entry(new WorldData(getWorldFolder(id)), id);
		newEntry.data.setLastUsed();
		worlds.add(newEntry);
		return newEntry.data;
	}
	
	public boolean hasChunkData(Chunk chunk) {
		return hasChunkData(chunk.getWorld(), chunk.getX() * ChunkData.CHUNK_SIZE, chunk.getZ() * ChunkData.CHUNK_SIZE);
	}
	
	public boolean hasChunkData(World world, int blockX, int blockZ) {
		return getWorld(world).hasChunkData(blockX, blockZ);
	}
	
	public ChunkData getChunkData(Chunk chunk) {
		return getChunkData(chunk.getWorld(), chunk.getX() * ChunkData.CHUNK_SIZE, chunk.getZ() * ChunkData.CHUNK_SIZE);
	}
	
	public ChunkData getChunkData(World world, int blockX, int blockZ) {
		return getWorld(world).getChunkData(blockX, blockZ);
	}
	
	public ChunkData createChunkData(byte[] data, Chunk chunk) {
		return createChunkData(data, chunk.getWorld(), chunk.getX() * ChunkData.CHUNK_SIZE, chunk.getZ() * ChunkData.CHUNK_SIZE);
	}
	
	public ChunkData createChunkData(byte[] data, World world, int blockX, int blockZ) {
		return getWorld(world).createChunkData(data, blockX, blockZ);
	}
	
	private static class Entry {
		
		private UUID id;
		private WorldData data;
		
		private Entry(WorldData data, UUID id) {
			this.data = data;
			this.id = id;
		}
	}
}