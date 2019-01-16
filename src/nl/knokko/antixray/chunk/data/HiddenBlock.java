package nl.knokko.antixray.chunk.data;

import org.bukkit.Material;

import static nl.knokko.antixray.chunk.data.ChunkData.CHUNK_SIZE;

import java.util.Arrays;

public class HiddenBlock {
	
	private static final Material[] MATERIAL_LOOKUP = {
			Material.EMERALD_ORE,
			Material.DIAMOND_ORE,
			Material.GOLD_ORE,
			Material.REDSTONE_ORE,
			Material.LAPIS_ORE,
			Material.IRON_ORE,
			Material.COAL_ORE
	};
	
	private static final byte[] MATERIAL_ID_LOOKUP = new byte[Material.values().length];
	
	static {
		// This makes it easier to find bugs as this will rapidly lead to exceptions if an invalid material is used
		Arrays.fill(MATERIAL_ID_LOOKUP, (byte) -1);
		
		// This should be the opposite of the MATERIAL_LOOKUP
		MATERIAL_ID_LOOKUP[Material.EMERALD_ORE.ordinal()] = 0;
		MATERIAL_ID_LOOKUP[Material.DIAMOND_ORE.ordinal()] = 1;
		MATERIAL_ID_LOOKUP[Material.GOLD_ORE.ordinal()] = 2;
		MATERIAL_ID_LOOKUP[Material.REDSTONE_ORE.ordinal()] = 3;
		MATERIAL_ID_LOOKUP[Material.LAPIS_ORE.ordinal()] = 4;
		MATERIAL_ID_LOOKUP[Material.IRON_ORE.ordinal()] = 5;
		MATERIAL_ID_LOOKUP[Material.COAL_ORE.ordinal()] = 6;
	}
	
	public static int getRelativeX(byte xz) {
		return (xz & 0xFF) / CHUNK_SIZE;
	}
	
	public static int getRelativeZ(byte xz) {
		return (xz & 0xFF) % CHUNK_SIZE;
	}
	
	public static Material getMaterial(byte materialID) {
		return MATERIAL_LOOKUP[materialID];
	}
	
	public static byte getXZ(int relativeX, int relativeZ) {
		return (byte) (relativeX * CHUNK_SIZE + relativeZ);
	}
	
	public static byte getMaterialID(Material material) {
		return MATERIAL_ID_LOOKUP[material.ordinal()];
	}
}