package nl.knokko.antixray.chunk.sections;

import java.io.File;

import nl.knokko.antixray.chunk.data.ChunkData;

class Continent {
	
	private static final String FOLDER_PREFIX = "country";
	
	private static final int COUNTRIES_X = 10;
	private static final int COUNTRIES_Z = 10;
	
	public static final int SIZE_X = COUNTRIES_X * Country.SIZE_X;
	public static final int SIZE_Z = COUNTRIES_Z * Country.SIZE_Z;
	
	private final Country[] countries;
	private final File folder;
	
	private long lastUsed;
	
	public Continent(File ownFolder) {
		this.folder = ownFolder;
		this.countries = new Country[COUNTRIES_X * COUNTRIES_Z];
	}
	
	private File getCountryFolder(int countryIndex) {
		return new File(folder + "/" + FOLDER_PREFIX + countryIndex);
	}
	
	private static int getCountryIndex(int relativeX, int relativeZ) {
		return relativeX / Country.SIZE_X + (relativeZ / Country.SIZE_Z) * COUNTRIES_X;
	}
	
	public void setLastUsed() {
		lastUsed = System.currentTimeMillis();
	}
	
	public long getLastUsed() {
		return lastUsed;
	}
	
	private Country getCountry(int relativeX, int relativeZ) {
		int countryIndex = getCountryIndex(relativeX, relativeZ);
		Country country = countries[countryIndex];
		if (country == null) {
			country = new Country(getCountryFolder(countryIndex));
			countries[countryIndex] = country;
		}
		country.setLastUsed();
		return country;
	}
	
	public boolean hasChunkData(int relativeX, int relativeZ) {
		return getCountry(relativeX, relativeZ).hasChunkData(relativeX % Country.SIZE_X, relativeZ % Country.SIZE_Z);
	}
	
	public ChunkData getChunkData(int relativeX, int relativeZ) {
		return getCountry(relativeX, relativeZ).getChunkData(relativeX % Country.SIZE_X, relativeZ % Country.SIZE_Z);
	}
	
	public ChunkData createChunkData(byte[] rawData, int relativeX, int relativeZ) {
		return getCountry(relativeX, relativeZ).createChunkData(rawData, relativeX % Country.SIZE_X, relativeZ % Country.SIZE_Z);
	}
	
	private static final int UNUSED_TIME = 60000;
	
	public void unloadUnused() {
		long time = System.currentTimeMillis();
		for (int index = 0; index < countries.length; index++) {
			if (countries[index] != null) {
				if (time - countries[index].getLastUsed() >= UNUSED_TIME) {
					countries[index].unload();
					countries[index] = null;
				} else {
					countries[index].unloadUnused();
				}
			}
		}
	}
	
	public void unload() {
		for (int index = 0; index < countries.length; index++) {
			if (countries[index] != null) {
				countries[index].unload();
				countries[index] = null;
			}
		}
	}
}