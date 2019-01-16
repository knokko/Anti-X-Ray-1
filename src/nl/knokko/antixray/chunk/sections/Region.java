package nl.knokko.antixray.chunk.sections;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import nl.knokko.antixray.AntiXRay;
import nl.knokko.antixray.chunk.data.ChunkData;

class Region {
	
	private static final String FILE_PREFIX = "village";
	private static final String FILE_SUFFIX = ".vil";
	
	public static final int VILLAGES_X = 10;
	public static final int VILLAGES_Z = 10;
	
	public static final int SIZE_X = VILLAGES_X * Village.SIZE_X;
	public static final int SIZE_Z = VILLAGES_Z * Village.SIZE_Z;
	
	private final Village[] villages;
	private final boolean[] hasVillages;
	
	private final File folder;
	
	private long lastUsed;
	
	public Region(File ownFolder) {
		this.folder = ownFolder;
		this.villages = new Village[VILLAGES_X * VILLAGES_Z];
		this.hasVillages = new boolean[villages.length];
		
		String[] villageFiles = folder.list((File fir, String fileName) -> {
			return fileName.startsWith(FILE_PREFIX) && fileName.endsWith(FILE_SUFFIX);
		});
		
		// Check what villages have been created already
		if (villageFiles != null) {
			for (String f: villageFiles) {
				try {
					int villageIndex = Integer.parseInt(f.substring(FILE_PREFIX.length(), f.length() - FILE_SUFFIX.length()));
					if (villageIndex >= 0 && villageIndex < hasVillages.length) {
						hasVillages[villageIndex] = true;
					} else {
						Bukkit.getLogger().warning("Strange filename " + f + " in the anti x-ray filesystem");
					}
				} catch (NumberFormatException ex) {
					Bukkit.getLogger().warning("Strange filename " + f + " in the anti x-ray file system");
				}
			}
		}
		
		// The folder does not yet exist, so there are no villages yet and thus everything in array stays false
	}
	
	public void setLastUsed() {
		this.lastUsed = System.currentTimeMillis();
	}
	
	public long getLastUsed() {
		return lastUsed;
	}
	
	private static int getVillageIndex(int relativeX, int relativeZ) {
		return (relativeZ / Village.SIZE_Z) * VILLAGES_X + relativeX / Village.SIZE_X;
	}
	
	private File getVillageFile(int villageIndex) {
		return new File(folder + "/" + FILE_PREFIX + villageIndex + FILE_SUFFIX);
	}
	
	private Village loadVillage(int villageIndex) {
		try {
			return new Village(AntiXRay.readFile(getVillageFile(villageIndex)));
		} catch (IOException ex) {
			Bukkit.getLogger().log(Level.SEVERE, "Failed to load data about a written chunk village", ex);
			
			// The old data will simply be lost, but I can't help that either...
			return new Village();
		}
	}
	
	public boolean hasChunkData(int relativeX, int relativeZ) {
		int villageIndex = getVillageIndex(relativeX, relativeZ);
		if (hasVillages[villageIndex]) {
			Village village = villages[villageIndex];
			if (village == null) {
				village = loadVillage(villageIndex);
				villages[villageIndex] = village;
			}
			return village.hasChunkData(relativeX % Village.SIZE_X, relativeZ % Village.SIZE_Z);
		} else {
			return false;
		}
	}
	
	public ChunkData getChunkData(int relativeX, int relativeZ) {
		int villageIndex = getVillageIndex(relativeX, relativeZ);
		if (hasVillages[villageIndex]) {
			Village village = villages[villageIndex];
			if (village == null) {
				village = loadVillage(villageIndex);
				villages[villageIndex] = village;
			}
			village.setLastUsed();
			return village.getChunkData(relativeX % Village.SIZE_X, relativeZ % Village.SIZE_Z);
		} else {
			return null;
		}
	}
	
	public ChunkData createChunkData(byte[] data, int relativeX, int relativeZ) {
		int villageIndex = getVillageIndex(relativeX, relativeZ);
		Village village;
		if (hasVillages[villageIndex]) {
			village = villages[villageIndex];
			if (village == null) {
				village = loadVillage(villageIndex);
				villages[villageIndex] = village;
			}
		} else {
			village = new Village();
			villages[villageIndex] = village;
			hasVillages[villageIndex] = true;
		}
		return village.createChunkData(data, relativeX % Village.SIZE_X, relativeZ % Village.SIZE_Z);
	}
	
	private static final int UNLOAD_TIME = 60000;
	
	/**
	 * Unloads all villages that have not been used for 1 minute. The memory occupied by the villages will
	 * be written to its file and then released.
	 */
	public void unloadUnusedVillages() {
		long time = System.currentTimeMillis();
		folder.mkdirs();
		for (int index = 0; index < villages.length; index++) {
			if (villages[index] != null) {
				if (time - villages[index].getLastUsed() >= UNLOAD_TIME) {
					unloadVillage(index);
				}
			}
		}
	}
	
	/**
	 * This method should be called before removing the Region from the Country. It will write all data
	 * to the file system.
	 */
	public void unload() {
		folder.mkdirs();
		for (int index = 0; index < villages.length; index++) {
			if (villages[index] != null) {
				unloadVillage(index);
			}
		}
	}
	
	private void unloadVillage(int villageIndex) {
		byte[] villageData = villages[villageIndex].saveData();
		try {
			OutputStream output = Files.newOutputStream(getVillageFile(villageIndex).toPath());
			output.write(villageData);
			output.flush();
			output.close();
			villages[villageIndex] = null;
		} catch (IOException ioex) {
			Bukkit.getLogger().log(Level.SEVERE, "Failed to save some chunk data", ioex);
		}
	}
}