package nl.knokko.antixray.chunk.data;

public class ChunkData {
	
	public static final int CHUNK_SIZE = 16;

	private byte[] blocks;

	public ChunkData(byte[] blocks) {
		this.blocks = blocks;
	}
	
	public void addBlocks(byte[] blockData) {
		byte[] newBlocks = new byte[blocks.length + blockData.length];
		System.arraycopy(blocks, 0, newBlocks, 0, blocks.length);
		System.arraycopy(blockData, 0, newBlocks, blocks.length, blockData.length);
	}

	public byte[] getData() {
		return blocks;
	}
	
	public int getDataLength() {
		return blocks.length;
	}
	
	public int getNumberOfOres() {
		return blocks.length / 3;
	}
}