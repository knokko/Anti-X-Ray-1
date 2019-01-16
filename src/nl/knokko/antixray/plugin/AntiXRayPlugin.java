package nl.knokko.antixray.plugin;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import nl.knokko.antixray.chunk.sections.WorldDataManager;

public class AntiXRayPlugin extends JavaPlugin {
	
	private static AntiXRayPlugin instance;
	
	public static AntiXRayPlugin getInstance() {
		return instance;
	}
	
	private WorldDataManager worldDataManager;
	
	@Override
	public void onEnable() {
		instance = this;
		worldDataManager = new WorldDataManager(new File(getDataFolder() + "/worlds"));
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
			worldDataManager.unloadUnused();
		}, 1500, 1200);
		Bukkit.getPluginManager().registerEvents(new AntiXRayEventHandler(), this);
		getCommand("antixray").setExecutor(new CommandAntiXRay());
	}
	
	@Override
	public void onDisable() {
		worldDataManager.unload();
		instance = null;
	}
	
	public WorldDataManager getWorldDataManager() {
		return worldDataManager;
	}
}