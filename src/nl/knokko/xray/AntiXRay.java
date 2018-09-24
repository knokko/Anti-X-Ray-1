package nl.knokko.xray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
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
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

public class AntiXRay extends JavaPlugin implements Listener, CommandExecutor {
	
	private static final String KEY_RESTORING = "is_restoring_world";
	private static final String KEY_UNLOAD_TIME = "seconds_before_unloading_chunk_data";
	private static final String KEY_RESTORE_PER_TICK = "chunks_to_restore_per_tick";
	
	private long expireNanoTime = 10000000000L;
	
	private Map<ChunkLocation,ChunkData> data = new TreeMap<ChunkLocation,ChunkData>();
	private Map<UUID,Set<CoordPair>> processedChunks;
	
	private long totalCheckTime;
	private int checkCount;
	
	private boolean isRestoring;
	private int restorePerTick;

	public AntiXRay() {}

	public AntiXRay(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
		super(loader, description, dataFolder, file);
	}
	
	private void loadConfig(){
		FileConfiguration config = getConfig();
		config.addDefault(KEY_RESTORING, false);
		config.addDefault(KEY_UNLOAD_TIME, 10);
		config.addDefault(KEY_RESTORE_PER_TICK, 1);
		config.options().copyDefaults(true);
		saveConfig();
		isRestoring = config.getBoolean(KEY_RESTORING);
		expireNanoTime = 1000000000L * config.getInt(KEY_UNLOAD_TIME);
		restorePerTick = config.getInt(KEY_RESTORE_PER_TICK);
		if(isRestoring)
			startRestoring();
	}
	
	private void startRestoring(){
		isRestoring = true;
		getConfig().set(KEY_RESTORING, true);
		saveConfig();
		getDataFolder().mkdirs();
		File[] worlds = getDataFolder().listFiles();
		Iterator<Entry<ChunkLocation,ChunkData>> it = data.entrySet().iterator();
		while(it.hasNext()){
			Entry<ChunkLocation,ChunkData> entry = it.next();
			saveChunkData(entry.getKey(), entry.getValue());
		}
		data.clear();
		RestoreTask task = new RestoreTask(worlds);
		int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, task, 0, 1);
		task.taskID = id;
	}
	
	@Override
	public void onEnable(){
		super.onEnable();
		loadConfig();
		try {
			byte[] chunkData = read(new File(getDataFolder() + File.separator + "created chunks.set"));
			ByteBuffer buffer = ByteBuffer.wrap(chunkData);
			processedChunks = new HashMap<UUID,Set<CoordPair>>();
			int size = buffer.getInt();
			for(int i = 0; i < size; i++){
				Set<CoordPair> set = new TreeSet<CoordPair>();
				UUID worldID = new UUID(buffer.getLong(), buffer.getLong());
				int amount = buffer.getInt();
				for(int j = 0; j < amount; j++)
					set.add(new CoordPair(buffer.getInt(), buffer.getInt()));
				processedChunks.put(worldID, set);
			}
		} catch(IOException ex){
			Bukkit.getLogger().log(Level.WARNING, "Couldn't load set of already created chunks; assuming this plugin runs for the first time: " + ex.getLocalizedMessage());
			processedChunks = new HashMap<UUID,Set<CoordPair>>();
		}
		Bukkit.getPluginManager().registerEvents(this, this);
		getCommand("antixray").setExecutor(this);
		getDataFolder().mkdirs();
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable(){

			public void run() {
				long time = System.nanoTime();
				Iterator<Entry<ChunkLocation,ChunkData>> it = data.entrySet().iterator();
				while(it.hasNext()){
					Entry<ChunkLocation,ChunkData> entry = it.next();
					if(time - entry.getValue().lastUsed >= expireNanoTime){
						saveChunkData(entry.getKey(), entry.getValue());
						it.remove();
					}
				}
			}
			
		}, 0, expireNanoTime / 1000000000 * 20);
	}
	
	@Override
	public void onDisable(){
		super.onDisable();
		if(checkCount != 0)
			System.out.println("Average check time was " + (totalCheckTime / checkCount) / 1000 + " microseconds.");
		else
			System.out.println("No blocks were checked.");
		getDataFolder().mkdirs();
		Iterator<Entry<ChunkLocation,ChunkData>> it = data.entrySet().iterator();
		while(it.hasNext()){
			Entry<ChunkLocation,ChunkData> entry = it.next();
			saveChunkData(entry.getKey(), entry.getValue());
		}
		data.clear();
		try {
			FileOutputStream output = new FileOutputStream(new File(getDataFolder() + File.separator + "created chunks.set"));
			Iterator<Entry<UUID,Set<CoordPair>>> iter = processedChunks.entrySet().iterator();
			output.write(ByteBuffer.allocate(4).putInt(processedChunks.size()).array());
			while(iter.hasNext()){
				Entry<UUID,Set<CoordPair>> entry = iter.next();
				Set<CoordPair> set = entry.getValue();
				ByteBuffer buffer = ByteBuffer.allocate(set.size() * 8 + 4);
				buffer.putInt(set.size());
				for(CoordPair pair : set){
					buffer.putInt(pair.x);
					buffer.putInt(pair.z);
				}
				output.write(buffer.array());
			}
			output.close();
		} catch(IOException ex){
			Bukkit.getLogger().log(Level.SEVERE, "Failed to save the set of created chunks:", ex);
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(!sender.isOp()){
			sender.sendMessage("Only operators can use this command.");
			return true;
		}
		if(args.length == 1 && sender instanceof Player){
			Chunk chunk = ((Player)sender).getLocation().getChunk();
			if(args[0].equals("process")){
				processChunk(chunk, getChunkData(chunk), true, true, true, true);
				sender.sendMessage("Processed your current chunk.");
				return true;
			}
			if(args[0].equals("restore")){
				ChunkData cd = getChunkData(chunk);
				for(HiddenBlock hb : cd.blocks)
					chunk.getWorld().getBlockAt(hb.x, hb.y, hb.z).setType(hb.type);
				sender.sendMessage("Restored " + cd.blocks.size() + " hidden ores.");
				cd.blocks.clear();
				return true;
			}
		}
		if(args.length == 1 && args[0].equals("uninstall")){
			if(!isRestoring){
				startRestoring();
				sender.sendMessage("New chunks will no longer be processed and the restore process has started...");
				sender.sendMessage("It will be broadcasted when the process is done. The process will resume when the server restarts.");
			}
			else
				sender.sendMessage("The plug-in is already uninstalling");
			return true;
		}
		if(args.length == 2 && args[0].equals("uninstall")){
			try {
				setRestorePerTick(Integer.parseInt(args[1]));
			} catch(NumberFormatException ex){
				sender.sendMessage("'" + args[1] + "' is no valid number.");
				return true;
			}
			if(!isRestoring){
				startRestoring();
				sender.sendMessage("New chunks will no longer be processed and the restore process has started...");
				sender.sendMessage("It will be broadcasted when the process is done. The process will resume when the server restarts.");
			}
			else
				sender.sendMessage("The uninstall speed has been set to " + restorePerTick + " chunks per tick");
			return true;
		}
		sender.sendMessage("Available commands are:");
		if(sender instanceof Player){
			sender.sendMessage("/axr process     (Use this command to hide all ores in the chunk you are in at the moment.)");
			sender.sendMessage("/axr restore     (Use this command to place all ores in your current chunk back where they were.)");
		}
		sender.sendMessage("/axr uninstall [chunks to restore per tick]     (There are 20 ticks within 1 second.)");
		sender.sendMessage("This command will make sure no new ores will be hidden.");
		sender.sendMessage("Also, ores can no longer be found when breaking the block next to it.");
		sender.sendMessage("The plug-in will start with restoring the worlds chunk by chunk.");
		return true;
	}
	
	private void setRestorePerTick(int amount){
		restorePerTick = amount;
		getConfig().set(KEY_RESTORE_PER_TICK, amount);
		saveConfig();
	}
	
	@EventHandler
	public void onChunkLoad(final ChunkLoadEvent event){
		if(event.getWorld().getEnvironment() == Environment.NORMAL && !hasChunkData(event.getChunk()))
			Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){
				
				public void run(){
					safeProcess(event.getChunk());
				}
			}, 5);//TODO What if I use this instead of the populate event?
	}
	
	@EventHandler
	public void onChunkPopulate(ChunkPopulateEvent event){
		//safeProcess(event.getChunk());
	}
	
	private void safeProcess(Chunk chunk){
		if(isRestoring)
			return;
		World world = chunk.getWorld();
		if(world.getEnvironment() != Environment.NORMAL)
			return;
		int x = chunk.getX();
		int z = chunk.getZ();
		ChunkData cdNorth = getChunkData(world.getUID(), x, z - 1);
		ChunkData cdNorthEast = getChunkData(world.getUID(), x + 1, z - 1);
		ChunkData cdEast = getChunkData(world.getUID(), x + 1, z);
		ChunkData cdSouthEast = getChunkData(world.getUID(), x + 1, z + 1);
		ChunkData cdSouth = getChunkData(world.getUID(), x, z + 1);
		ChunkData cdSouthWest = getChunkData(world.getUID(), x - 1, z + 1);
		ChunkData cdWest = getChunkData(world.getUID(), x - 1, z);
		ChunkData cdNorthWest = getChunkData(world.getUID(), x - 1, z - 1);
		boolean north = cdNorth != null;
		boolean east = cdEast != null;
		boolean south = cdSouth != null;
		boolean west = cdWest != null;
		boolean northEast = cdNorthEast != null;
		boolean southEast = cdSouthEast != null;
		boolean southWest = cdSouthWest != null;
		boolean northWest = cdNorthWest != null;
		processChunk(chunk, null, north, east, south, west);
		processIfCan(cdEast, world, x + 1, z, northEast, false, southEast, true);
		processIfCan(cdSouth, world, x, z + 1, true, southEast, false, southWest);
		processIfCan(cdWest, world, x - 1, z, northWest, true, southWest, false);
		processIfCan(cdNorth, world, x, z - 1, false, northEast, true, northWest);
		processIfCan(cdNorthEast, world, x + 1, z - 1, false, false, east, north);
		processIfCan(cdSouthEast, world, x + 1, z + 1, east, false, false, south);
		processIfCan(cdSouthWest, world, x - 1, z + 1, west, south, false, false);
		processIfCan(cdNorthWest, world, x - 1, z - 1, false, north, west, false);
	}
	
	private void processIfCan(ChunkData cd, World world, int x, int z, boolean north, boolean east, boolean south, boolean west){
		if(cd != null)
			processChunk(world.getChunkAt(x, z), cd, north, east, south, west);
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event){
		if(!isRestoring && event.getBlock().getWorld().getEnvironment() == Environment.NORMAL)
			onBlockRemove(event.getBlock());
	}
	
	private void onBlockRemove(Block block){
		long startTime = System.nanoTime();
		if(block.getY() > 128 || block.getWorld().getEnvironment() != Environment.NORMAL)
			return;
		ChunkData cd = getChunkData(block.getChunk());
		if(cd != null){
			cd.lastUsed = System.nanoTime();
			Iterator<HiddenBlock> it = cd.blocks.iterator();
			while(it.hasNext()){
				HiddenBlock b = it.next();
				if(isClose(block.getX(), block.getY(), block.getZ(), b.x, b.y, b.z)){
					block.getWorld().getBlockAt(b.x, b.y, b.z).setType(b.type);
					it.remove();
				}
			}
			long endTime = System.nanoTime();
			totalCheckTime += (endTime - startTime);
			checkCount++;
		}
		else {
			Bukkit.getLogger().warning("No data for chunk " + block.getChunk() + " [is in set: " + hasChunkData(block.getChunk()) + "]");//TODO fix this (sometimes returns true and sometimes returns false)
			processChunk(block.getChunk(), null, true, true, true, true);
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void updateExploded(BlockExplodeEvent event){
		if(isRestoring || event.getBlock().getWorld().getEnvironment() != Environment.NORMAL)
			return;
		List<Block> blocks = event.blockList();
		for(Block block : blocks)
			onBlockRemove(block);
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void updateExploded(EntityExplodeEvent event){
		if(isRestoring || event.getEntity().getWorld().getEnvironment() != Environment.NORMAL)
			return;
		List<Block> blocks = event.blockList();
		for(Block block : blocks)
			onBlockRemove(block);
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void checkPiston(BlockPistonExtendEvent event){
		if(isRestoring || event.getBlock().getWorld().getEnvironment() != Environment.NORMAL)
			return;
		List<Block> blocks = event.getBlocks();
		for(Block block : blocks)
			onBlockRemove(block);
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void checkPiston(BlockPistonRetractEvent event){
		if(isRestoring || event.getBlock().getWorld().getEnvironment() != Environment.NORMAL)
			return;
		List<Block> blocks = event.getBlocks();
		for(Block block : blocks)
			onBlockRemove(block);
	}
	
	private void processChunk(Chunk chunk, ChunkData current, boolean hasNorth, boolean hasEast, boolean hasSouth, boolean hasWest){
		List<HiddenBlock> ores;
		if(current == null)
			ores = new ArrayList<HiddenBlock>(300);
		else
			ores = current.blocks;
		int boundX = hasEast ? 16 : 15;
		int boundZ = hasSouth ? 16 : 15;
		int minX = hasWest ? 0 : 1;
		int minZ = hasNorth ? 0 : 1;
		for(int cx = minX; cx < boundX; cx++){
			for(int cy = 1; cy < 64; cy++){
				for(int cz = minZ; cz < boundZ; cz++){
					Block block = chunk.getWorld().getBlockAt(cx + chunk.getX() * 16, cy, cz + chunk.getZ() * 16);
					Material type = block.getType();
					if(needsHide(type) && isHidden(block)){
						ores.add(new HiddenBlock(chunk.getX() * 16 + cx, cy, chunk.getZ() * 16 + cz, type));
						block.setType(Material.STONE);
					}
				}
			}
		}
		if(current == null)
			data.put(new ChunkLocation(chunk), new ChunkData(ores));
		Set<CoordPair> set = processedChunks.get(chunk.getWorld().getUID());
		if(set == null){
			set = new TreeSet<CoordPair>();
			processedChunks.put(chunk.getWorld().getUID(), set);
		}
		set.add(new CoordPair(chunk.getX(), chunk.getZ()));
	}
	
	private boolean needsHide(Material t){
		return t == Material.COAL_ORE || t == Material.IRON_ORE || t == Material.LAPIS_ORE || t == Material.GOLD_ORE
				|| t == Material.REDSTONE_ORE || t == Material.DIAMOND_ORE || t == Material.EMERALD_ORE;
	}
	
	private boolean isClose(int x1, int y1, int z1, int x2, int y2, int z2){
		if(x2 == x1 + 1 && y1 == y2 && z1 == z2)
			return true;
		if(x2 == x1 - 1 && y1 == y2 && z1 == z2)
			return true;
		if(x1 == x2 && y1 == y2 + 1 && z1 == z2)
			return true;
		if(x1 == x2 && y1 == y2 - 1 && z1 == z2)
			return true;
		if(x1 == x2 && y1 == y2 && z1 == z2 + 1)
			return true;
		return x1 == x2 && y1 == y2 && z1 == z2 - 1;
	}
	
	private boolean isHidden(Block block){
		if(!block.getRelative(BlockFace.NORTH).getType().isOccluding())
			return false;
		if(!block.getRelative(BlockFace.EAST).getType().isOccluding())
			return false;
		if(!block.getRelative(BlockFace.SOUTH).getType().isOccluding())
			return false;
		if(!block.getRelative(BlockFace.WEST).getType().isOccluding())
			return false;
		if(!block.getRelative(BlockFace.UP).getType().isOccluding())
			return false;
		return block.getRelative(BlockFace.DOWN).getType().isOccluding();
	}
	
	private ChunkData getChunkData(UUID worldID, int chunkX, int chunkZ){
		ChunkLocation cl = new ChunkLocation(worldID, chunkX, chunkZ);
		ChunkData cd = data.get(cl);
		if(cd != null){
			cd.lastUsed = System.nanoTime();
			return cd;
		}
		cd = loadChunkData(worldID, chunkX, chunkZ);
		if(cd != null){
			data.put(cl, cd);
			cd.lastUsed = System.nanoTime();
		}
		return cd;
	}
	
	private boolean hasChunkData(Chunk chunk){
		return hasChunkData(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
	}
	
	private boolean hasChunkData(UUID id, int x, int z){
		Set<CoordPair> set = processedChunks.get(id);
		if(set != null)
			return set.contains(new CoordPair(x, z));
		return false;
	}
	
	private ChunkData getChunkData(Chunk chunk){
		ChunkLocation cl = new ChunkLocation(chunk);
		ChunkData cd = data.get(cl);
		if(cd != null){
			cd.lastUsed = System.nanoTime();
			return cd;
		}
		cd = loadChunkData(chunk);
		if(cd != null){
			data.put(cl, cd);
			cd.lastUsed = System.nanoTime();
		}
		return cd;
	}
	
	private void saveChunkData(ChunkLocation chunk, ChunkData cd){
		try {
			byte[] data = cd.save(chunk);
			FileOutputStream output = new FileOutputStream(getChunkFile(chunk));
			output.write(data);
			output.close();
		} catch(IOException ex){
			System.out.println("Couldn't save data of chunk(" + chunk.x + "," + chunk.z + "): " + ex.getLocalizedMessage());
		}
	}
	
	private byte[] read(File file) throws IOException {
		if(file.length() > Integer.MAX_VALUE)
			throw new IllegalArgumentException("File is too large: (" + file + ") (" + file.length() + ")");
		byte[] data = new byte[(int) file.length()];
		FileInputStream input = new FileInputStream(file);
		input.read(data);
		input.close();
		return data;
	}
	
	private ChunkData loadChunkData(Chunk chunk){
		File file = getChunkFile(chunk);
		if(!file.exists())
			return null;
		try {
			byte[] bytes = read(file);
			return new ChunkData(chunk.getX(), chunk.getZ(), bytes);
		} catch(IOException ex){
			System.out.println("Couldn't load data of chunk(" + chunk.getX() + "," + chunk.getZ() + "): " + ex.getLocalizedMessage() + "; Processing chunk...");
			processChunk(chunk, null, true, true, true, true);
			return null;
		}
	}
	
	private ChunkData loadChunkData(UUID worldID, int x, int z){
		File file = getChunkFile(worldID, x, z);
		if(!file.exists())
			return null;
		try {
			byte[] bytes = read(file);
			return new ChunkData(x, z, bytes);
		} catch(IOException ex){
			System.out.println("Couldn't load data of chunk(" + x + "," + z + "): " + ex.getLocalizedMessage());
			return null;
		}
	}
	
	private File getChunkFile(Chunk chunk){
		return getChunkFile(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
	}
	
	private File getChunkFile(ChunkLocation location){
		return getChunkFile(location.id, location.x, location.z);
	}
	
	private File getChunkFile(UUID id, int x, int z){
		new File(getDataFolder() + File.separator + id).mkdirs();
		return new File(getDataFolder() + File.separator + id + File.separator + x + " " + z);
	}
	
	private static class HiddenBlock {
		
		private final int x;
		private final int y;
		private final int z;
		
		private final Material type;
		
		private HiddenBlock(int x, int y, int z, Material type){
			this.x = x;
			this.y = y;
			this.z = z;
			this.type = type;
		}
	}
	
	private static class ChunkLocation implements Comparable<ChunkLocation> {
		
		private final int x;
		private final int z;
		
		private final UUID id;
		
		private ChunkLocation(Chunk chunk){
			x = chunk.getX();
			z = chunk.getZ();
			id = chunk.getWorld().getUID();
		}
		
		private ChunkLocation(UUID worldID, int x, int z){
			id = worldID;
			this.x = x;
			this.z = z;
		}
		
		@Override
		public boolean equals(Object other){
			if(other instanceof ChunkLocation){
				ChunkLocation cl = (ChunkLocation) other;
				return cl.x == x && cl.z == z && cl.id.equals(id);
			}
			return false;
		}
		
		@Override
		public String toString(){
			return "ChunkLocation(" + x + "," + z + ")";
		}
		
		public int compareTo(ChunkLocation cl){
			int cid = id.compareTo(cl.id);
			if(cid == 0){
				if(x > cl.x)
					return 1;
				if(x < cl.x)
					return -1;
				if(z > cl.z)
					return 1;
				if(z < cl.z)
					return -1;
				return 0;
			}
			return cid;
		}
	}
	
	private static class CoordPair implements Comparable<CoordPair> {
		
		private final int x;
		private final int z;
		
		private CoordPair(int x, int z){
			this.x = x;
			this.z = z;
		}
		
		@Override
		public boolean equals(Object other){
			if(other instanceof CoordPair){
				CoordPair cp = (CoordPair) other;
				return cp.x == x && cp.z == z;
			}
			return false;
		}

		public int compareTo(CoordPair cp) {
			if(cp.x > x)
				return 1;
			if(cp.x < 1)
				return -1;
			if(cp.z > z)
				return 1;
			if(cp.z < z)
				return -1;
			return 0;
		}
	}
	
	private static class ChunkData {
		
		private static final byte ID_EMERALD = 0;
		private static final byte ID_DIAMOND = 1;
		private static final byte ID_GOLD = 2;
		private static final byte ID_REDSTONE = 3;
		private static final byte ID_LAPIS = 4;
		private static final byte ID_IRON = 5;
		private static final byte ID_COAL = 6;
		
		private static final byte BIT_XZ = 4;
		private static final byte BIT_Y = 7;
		private static final byte BIT_MATERIAL = 3;
		
		private static final int CHUNK_SIZE = 16;
		
		private static byte idForMaterial(Material material){
			switch(material){
			case EMERALD_ORE : return ID_EMERALD;
			case DIAMOND_ORE : return ID_DIAMOND;
			case GOLD_ORE : return ID_GOLD;
			case REDSTONE_ORE : return ID_REDSTONE;
			case LAPIS_ORE : return ID_LAPIS;
			case IRON_ORE : return ID_IRON;
			case COAL_ORE : return ID_COAL;
			default:
				throw new IllegalArgumentException("No id for material " + material);
			}
		}
		
		private static Material materialForId(byte id){
			if(id == ID_COAL)
				return Material.COAL_ORE;
			if(id == ID_IRON)
				return Material.IRON_ORE;
			if(id == ID_REDSTONE)
				return Material.REDSTONE_ORE;
			if(id == ID_GOLD)
				return Material.GOLD_ORE;
			if(id == ID_LAPIS)
				return Material.LAPIS_ORE;
			if(id == ID_DIAMOND)
				return Material.DIAMOND_ORE;
			if(id == ID_EMERALD)
				return Material.EMERALD_ORE;
			throw new IllegalArgumentException("No material for id " + id);
		}
		
		private final List<HiddenBlock> blocks;
		
		private long lastUsed;
		
		private ChunkData(List<HiddenBlock> hiddenBlocks){
			blocks = hiddenBlocks;
		}
		
		private ChunkData(int chunkX, int chunkZ, byte[] bytes){
			BitBuffer buffer = new BitBuffer(bytes);
			int size = buffer.readInt();
			blocks = new ArrayList<HiddenBlock>(size);
			for(int index = 0; index < size; index++){
				blocks.add(new HiddenBlock((int) (chunkX * CHUNK_SIZE + buffer.readNumber(BIT_XZ, false)),
						(int) buffer.readNumber(BIT_Y, false),
						(int) (chunkZ * CHUNK_SIZE + buffer.readNumber(BIT_XZ, false)),
						materialForId((byte) buffer.readNumber(BIT_MATERIAL, false))));
			}
		}
		
		private byte[] save(ChunkLocation chunk){
			BitBuffer buffer = new BitBuffer(32 + blocks.size() * 17);
			buffer.addInt(blocks.size());
			for(HiddenBlock hb : blocks){
				buffer.addNumber(hb.x - chunk.x * CHUNK_SIZE, BIT_XZ, false);
				buffer.addNumber(hb.y, BIT_Y, false);
				buffer.addNumber(hb.z - chunk.z * CHUNK_SIZE, BIT_XZ, false);
				buffer.addNumber(idForMaterial(hb.type), BIT_MATERIAL, false);
			}
			return buffer.toBytes();
		}
	}
	
	private class RestoreTask implements Runnable {
		
		private final File[] worlds;
		private int taskID;
		
		private int worldIndex;
		
		private UUID currentID;
		private World currentWorld;
		private Set<CoordPair> currentSet;
		
		private File[] chunks;
		private int chunkIndex;
		
		private RestoreTask(File[] worldFolders){
			List<File> list = new ArrayList<File>();
			for(File world : worldFolders){
				if(world.isDirectory()){
					try {
						UUID.fromString(world.getName());
						list.add(world);
					} catch(IllegalArgumentException ex){
						Bukkit.getLogger().warning("Ignored file " + world + " because it is no UUID.");
					}
				}
				else
					Bukkit.getLogger().warning("Ignored file " + world + " because it is no folder.");
			}
			worlds = list.toArray(new File[list.size()]);
		}
		
		public void run(){
			for(int i = 0; i < restorePerTick; i++){
				restore1();
				if(taskID == -10)
					return;
			}
		}

		public void restore1() {
			if(currentID == null){
				if(worldIndex >= worlds.length){
					new File(getDataFolder() + File.separator + "created chunks.set").deleteOnExit();
					Bukkit.getScheduler().cancelTask(taskID);
					Bukkit.broadcastMessage("Finished restoring worlds!");
					Bukkit.broadcastMessage("You can delete the plug-in when the server stops.");
					taskID = -10;
					return;
				}
				currentID = UUID.fromString(worlds[worldIndex].getName());
				currentWorld = Bukkit.getWorld(currentID);
				currentSet = processedChunks.get(currentID);
				if(currentSet == null)
					Bukkit.getLogger().warning("It appears that there is no set of processed chunks for world id " + currentID);
				chunks = worlds[worldIndex].listFiles();
				chunkIndex = 0;
				if(currentWorld == null){
					Bukkit.getLogger().warning("Skipped world folder with id " + currentID);
					currentID = null;
					worldIndex++;
				}
				return;
			}
			if(chunkIndex >= chunks.length){
				currentID = null;
				worlds[worldIndex].delete();
				worldIndex++;
				return;
			}
			File chunkFile = chunks[chunkIndex];
			chunkIndex++;
			String name = chunkFile.getName();
			int index = name.indexOf(" ");
			if(index != -1){
				try {
					int x = Integer.parseInt(name.substring(0, index));
					int z = Integer.parseInt(name.substring(index + 1));
					Chunk chunk = currentWorld.getChunkAt(x, z);
					ChunkData data = new ChunkData(x, z, read(chunkFile));
					for(HiddenBlock hb : data.blocks)
						chunk.getBlock(hb.x - x * ChunkData.CHUNK_SIZE, hb.y, hb.z - z * ChunkData.CHUNK_SIZE).setType(hb.type);
					chunkFile.delete();
				} catch(Exception ex){
					Bukkit.getLogger().warning("skipped file with name " + name + ": " + ex.getMessage());
				}
			}
			else
				Bukkit.getLogger().warning("skipped file with name " + name);
		}
	}
}
