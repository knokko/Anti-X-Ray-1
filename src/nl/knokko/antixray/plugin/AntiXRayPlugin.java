package nl.knokko.antixray.plugin;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import nl.knokko.antixray.chunk.sections.WorldDataManager;
import nl.knokko.antixray.settings.WorldState;
import nl.knokko.antixray.settings.WorldStates;

public class AntiXRayPlugin extends JavaPlugin {
	
	private static AntiXRayPlugin instance;
	
	public static AntiXRayPlugin getInstance() {
		return instance;
	}
	
	private WorldDataManager worldDataManager;
	private WorldStates worldStates;
	
	@Override
	public void onEnable() {
		instance = this;
		try {
			worldStates = new WorldStates(getDataFolder());
		} catch (RuntimeException ex) {
			Bukkit.getPluginManager().disablePlugin(this);
			ex.printStackTrace();
		}
		worldDataManager = new WorldDataManager(new File(getDataFolder() + "/worlds"));
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
			worldDataManager.unloadUnused();
		}, 1500, 1200);
		Bukkit.getPluginManager().registerEvents(new AntiXRayEventHandler(), this);
		getCommand("antixray").setExecutor(new CommandAntiXRay());
	}
	
	@Override
	public void onDisable() {
		
		// Might be null if plug-in was disabled during enabling because an error occurred
		if (worldStates != null) {
			worldDataManager.unload();
			worldStates.save();
		}
		instance = null;
	}
	
	public WorldDataManager getWorldDataManager() {
		return worldDataManager;
	}
	
	public WorldState getWorldState(World world) {
		return worldStates.getWorldState(world);
	}
}