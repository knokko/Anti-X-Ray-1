package nl.knokko.antixray.settings;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;

import nl.knokko.antixray.AntiXRay;

public class WorldStates {

	private static final byte ENCODING_1 = 0;

	private Map<UUID, WorldState> worldSettings;
	
	private File dataFolder;

	public WorldStates(File dataFolder) {
		this.dataFolder = dataFolder;
		File dataFile = new File(dataFolder + "/worldstates.data");
		if (dataFile.exists()) {
			try {
				byte[] input = AntiXRay.readFile(dataFile);
				byte encoding = input[0];
				if (encoding == ENCODING_1) {
					ByteBuffer buffer = ByteBuffer.wrap(input, 1, input.length - 1);
					int size = buffer.getInt();
					for (int counter = 0; counter < size; counter++) {
						worldSettings.put(new UUID(buffer.getLong(), buffer.getLong()), new WorldState(buffer));
					}
				} else {
					throw new IllegalArgumentException("Unknown world states encoding: " + encoding);
				}
			} catch (IOException ex) {
				Bukkit.getLogger().log(Level.SEVERE, "Failed to load the world states; disabling AntiXRay", ex);

				// Make sure plug-in won't start to prevent damage
				throw new RuntimeException();
			}
		} else {
			Bukkit.getLogger().info("Couldn't find world states of Anti X-Ray, assuming this is the first time plug-in is used.");
		}
	}

	public WorldState getWorldState(World world) {
		WorldState settings = worldSettings.get(world.getUID());
		if (settings == null) {
			settings = new WorldState();
			worldSettings.put(world.getUID(), settings);
		}
		return settings;
	}

	public void save() {
		dataFolder.mkdirs();
		try {
			OutputStream output = Files.newOutputStream(new File(dataFolder + "/worldstates.data").toPath());
			output.write(ENCODING_1);
			int size = worldSettings.size();
			output.write(AntiXRay.int0(size));
			output.write(AntiXRay.int1(size));
			output.write(AntiXRay.int2(size));
			output.write(AntiXRay.int3(size));
			ByteBuffer buffer = ByteBuffer.allocate(16);
			Set<Entry<UUID,WorldState>> entrySet = worldSettings.entrySet();
			for (Entry<UUID,WorldState> entry : entrySet) {
				buffer.putLong(0, entry.getKey().getMostSignificantBits());
				buffer.putLong(8, entry.getKey().getLeastSignificantBits());
				output.write(buffer.array());
				output.write(entry.getValue().save());
			}
			output.flush();
			output.close();
		} catch (IOException ioex) {
			Bukkit.getLogger().log(Level.SEVERE, "Failed to save the world states", ioex);
		}
	}
}