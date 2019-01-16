package nl.knokko.antixray.chunk.sections;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import nl.knokko.antixray.chunk.data.ChunkData;

class WorldData {
	
	private static final String FOLDER_PREFIX = "continent";
	
	private final Collection<Entry> continents;
	
	private final File folder;
	
	private long lastUsed;
	
	public WorldData(File ownFolder) {
		folder = ownFolder;
		
		// The amount of continents will rarely exceed 4
		continents = new ArrayList<Entry>(4);
	}
	
	public void setLastUsed() {
		lastUsed = System.currentTimeMillis();
	}
	
	public long getLastUsed() {
		return lastUsed;
	}
	
	private File getContinentFolder(int continentX, int continentZ) {
		return new File(folder + "/" + FOLDER_PREFIX + continentX + "_" + continentZ);
	}
	
	private Continent getContinent(int blockX, int blockZ) {
		int continentX = Math.floorDiv(blockX, Continent.SIZE_X);
		int continentZ = Math.floorDiv(blockZ, Continent.SIZE_Z);
		for (Entry continent : continents) {
			if (continent.continentX == continentX && continent.continentZ == continentZ) {
				continent.continent.setLastUsed();
				return continent.continent;
			}
		}
		Entry newEntry = new Entry(new Continent(getContinentFolder(continentX, continentZ)), continentX, continentZ);
		continents.add(newEntry);
		newEntry.continent.setLastUsed();
		return newEntry.continent;
	}
	
	private static int mod(int x, int n) {
		// Copied from https://stackoverflow.com/questions/4412179/best-way-to-make-javas-modulus-behave-like-it-should-with-negative-numbers
		int remainder = (x % n); // may be negative if x is negative
		//if remainder is negative, adds n, otherwise adds 0
		return ((remainder >> 31) & n) + remainder;
	}
	
	private static int getRelX(int blockX) {
		return mod(blockX, Continent.SIZE_X);
	}
	
	private static int getRelZ(int blockZ) {
		return mod(blockZ, Continent.SIZE_Z);
	}
	
	public boolean hasChunkData(int blockX, int blockZ) {
		return getContinent(blockX, blockZ).hasChunkData(getRelX(blockX), getRelZ(blockZ));
	}
	
	public ChunkData getChunkData(int blockX, int blockZ) {
		return getContinent(blockX, blockZ).getChunkData(getRelX(blockX), getRelZ(blockZ));
	}
	
	public ChunkData createChunkData(byte[] rawData, int blockX, int blockZ) {
		return getContinent(blockX, blockZ).createChunkData(rawData, getRelX(blockX), getRelZ(blockZ));
	}
	
	private static final int UNUSED_TIME = 60000;
	
	public void unloadUnused() {
		long time = System.currentTimeMillis();
		Iterator<Entry> iterator = continents.iterator();
		while (iterator.hasNext()) {
			Entry next = iterator.next();
			if (time - next.continent.getLastUsed() >= UNUSED_TIME) {
				next.continent.unload();
				iterator.remove();
			} else {
				next.continent.unloadUnused();
			}
		}
	}
	
	public void unload() {
		for (Entry entry : continents) {
			entry.continent.unload();
		}
		continents.clear();
	}
	
	private static class Entry {
		
		private int continentX;
		private int continentZ;
		
		private Continent continent;
		
		private Entry(Continent continent, int x, int z) {
			this.continent = continent;
			continentX = x;
			continentZ = z;
		}
	}
}