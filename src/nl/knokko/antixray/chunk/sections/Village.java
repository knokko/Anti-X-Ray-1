package nl.knokko.antixray.chunk.sections;

import nl.knokko.antixray.AntiXRay;
import nl.knokko.antixray.chunk.data.ChunkData;

import static nl.knokko.antixray.chunk.data.ChunkData.CHUNK_SIZE;

/**
 * A village is a region of SIZE_X x SIZE_Z chunks. Every Village has its own
 * file. Every village is either loaded or unloaded; when the village is loaded,
 * all chunk data is in memory. When it is unloaded, no chunk data is in memory.
 * This seemed like a funny class name for splitting the worlds into sections.
 * 
 * @author knokko
 *
 */
class Village {

	public static final int CHUNKS_X = 10;
	public static final int CHUNKS_Z = 10;
	
	public static final int SIZE_X = CHUNKS_X * CHUNK_SIZE;
	public static final int SIZE_Z = CHUNKS_Z * CHUNK_SIZE;

	private final ChunkData[] chunks;
	
	private long lastUsed;

	/**
	 * Initialize a new Village without any data
	 */
	public Village() {
		this.chunks = new ChunkData[CHUNKS_X * CHUNKS_Z];
		this.lastUsed = -1;
	}

	/**
	 * Load a Village from the bytes that were saved by it the previous time
	 * 
	 * @param bytes The bytes that were created with the save method of the previous
	 *              instance
	 */
	public Village(byte[] bytes) {
		this.chunks = new ChunkData[CHUNKS_X * CHUNKS_Z];
		this.lastUsed = -1;
		
		// First read the sizes
		int[] sizes = new int[chunks.length];
		int byteIndex = 0;
		for (int chunkIndex = 0; chunkIndex < chunks.length; chunkIndex++) {
			sizes[chunkIndex] = AntiXRay.makeInt(bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++], bytes[byteIndex++]);
		}
		
		// Then read the data
		for (int chunkIndex = 0; chunkIndex < chunks.length; chunkIndex++) {
			int size = sizes[chunkIndex];
			
			// Load the chunk data if there was any
			if (size != -1) {
				byte[] chunkData = new byte[size];
				System.arraycopy(bytes, byteIndex, chunkData, 0, size);
				chunks[chunkIndex] = new ChunkData(chunkData);
				byteIndex += size;
			}
			// If the chunk data doesn't exist yet, it will remain null
		}
	}
	
	public void setLastUsed() {
		this.lastUsed = System.currentTimeMillis();
	}
	
	public boolean hasBeenUsed() {
		return lastUsed != -1;
	}
	
	public long getLastUsed() {
		return lastUsed;
	}

	public byte[] saveData() {

		// Determine size of the byte array
		int length = 4 * CHUNKS_X * CHUNKS_Z;
		for (ChunkData data : chunks) {
			if (data != null) {
				length += data.getDataLength();
			}
		}

		byte[] bytes = new byte[length];
		int byteIndex = 0;

		// Put the sizes of the data of the chunks first
		for (ChunkData data : chunks) {
			int dataSize;
			if (data == null) {
				dataSize = -1;
			} else {
				dataSize = data.getDataLength();
			}
			bytes[byteIndex++] = AntiXRay.int0(dataSize);
			bytes[byteIndex++] = AntiXRay.int1(dataSize);
			bytes[byteIndex++] = AntiXRay.int2(dataSize);
			bytes[byteIndex++] = AntiXRay.int3(dataSize);
		}

		// Now save the actual data
		for (ChunkData data : chunks) {
			if (data != null) {
				byte[] chunkData = data.getData();
				System.arraycopy(chunkData, 0, bytes, byteIndex, chunkData.length);
				byteIndex += chunkData.length;
			}
		}

		return bytes;
	}
	
	private static int getChunkIndex(int relativeX, int relativeZ) {
		return (relativeZ / CHUNK_SIZE) * CHUNKS_X + relativeX / CHUNK_SIZE;
	}
	
	public boolean hasChunkData(int relativeX, int relativeZ) {
		return chunks[getChunkIndex(relativeX, relativeZ)] != null;
	}
	
	public ChunkData getChunkData(int relativeX, int relativeZ) {
		return chunks[getChunkIndex(relativeX, relativeZ)];
	}
	
	public ChunkData createChunkData(byte[] data, int relativeX, int relativeZ) {
		if (hasChunkData(relativeX, relativeZ)) throw new IllegalStateException("Data at (" + relativeX + ", " + relativeZ + ") is already present");
		ChunkData chunkData = new ChunkData(data);
		chunks[getChunkIndex(relativeX, relativeZ)] = chunkData;
		return chunkData;
	}
}