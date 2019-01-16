package nl.knokko.antixray.chunk.sections;

import java.io.File;

import nl.knokko.antixray.chunk.data.ChunkData;

class Country {
	
	private static final String FOLDER_PREFIX = "region";
	
	private static final int REGIONS_X = 10;
	private static final int REGIONS_Z = 10;
	
	public static final int SIZE_X = REGIONS_X * Region.SIZE_X;
	public static final int SIZE_Z = REGIONS_Z * Region.SIZE_Z;
	
	private final Region[] regions;
	
	private final File folder;
	
	private long lastUsed;
	
	public Country(File ownFolder) {
		this.folder = ownFolder;
		this.regions = new Region[REGIONS_X * REGIONS_Z];
	}
	
	public void setLastUsed() {
		lastUsed = System.currentTimeMillis();
	}
	
	public long getLastUsed() {
		return lastUsed;
	}
	
	private File getRegionFolder(int regionIndex) {
		return new File(folder + "/" + FOLDER_PREFIX + regionIndex);
	}
	
	private static int getRegionIndex(int relativeX, int relativeZ) {
		return (relativeX / Region.SIZE_X) + (relativeZ / Region.SIZE_Z) * REGIONS_X;
	}
	
	private Region getRegion(int relativeX, int relativeZ) {
		int regionIndex = getRegionIndex(relativeX, relativeZ);
		Region region = regions[regionIndex];
		if (region == null) {
			region = new Region(getRegionFolder(regionIndex));
			regions[regionIndex] = region;
		}
		region.setLastUsed();
		return region;
	}
	
	public boolean hasChunkData(int relativeX, int relativeZ) {
		return getRegion(relativeX, relativeZ).hasChunkData(relativeX % Region.SIZE_X, relativeZ % Region.SIZE_Z);
	}
	
	public ChunkData getChunkData(int relativeX, int relativeZ) {
		return getRegion(relativeX, relativeZ).getChunkData(relativeX % Region.SIZE_X, relativeZ % Region.SIZE_Z);
	}
	
	public ChunkData createChunkData(byte[] data, int relativeX, int relativeZ) {
		return getRegion(relativeX, relativeZ).createChunkData(data, relativeX % Region.SIZE_X, relativeZ % Region.SIZE_Z);
	}
	
	private static final int UNUSED_TIME = 60000;
	
	/**
	 * Unloads all regions and villages that have not been used for 1 minute. Their data will be saved to disk
	 * and they will be made ready for GC.
	 */
	public void unloadUnused() {
		long time = System.currentTimeMillis();
		for (int index = 0; index < regions.length; index++) {
			if (regions[index] != null) {
				if (time - regions[index].getLastUsed() >= UNUSED_TIME) {
					regions[index].unload();
					regions[index] = null;
				} else {
					regions[index].unloadUnusedVillages();
				}
			}
		}
	}
	
	/**
	 * Unloads this country. All data will be saved to disk and the country with everything in it will be
	 * made ready for GC.
	 */
	public void unload() {
		for (int index = 0; index < regions.length; index++) {
			if (regions[index] != null) {
				regions[index].unload();
				regions[index] = null;
			}
		}
	}
}