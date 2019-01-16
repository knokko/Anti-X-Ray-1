package nl.knokko.antixray.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import nl.knokko.antixray.chunk.data.ChunkData;
import nl.knokko.antixray.chunk.data.HiddenBlock;

public class CommandAntiXRay implements CommandExecutor {
	
	private void restore(Chunk chunk) {
		ChunkData data = AntiXRayPlugin.getInstance().getWorldDataManager().getChunkData(chunk);
		byte[] bytes = data.getData();
		for (int index = 0; index < bytes.length; index += 3) {
			if (bytes[index + 2] != -2) {
				int x = HiddenBlock.getRelativeX(bytes[index]) + chunk.getX() * 16;
				int y = bytes[index + 1] & 0xFF;
				int z = HiddenBlock.getRelativeZ(bytes[index]) + chunk.getZ() * 16;
				chunk.getWorld().getBlockAt(x, y, z).setType(HiddenBlock.getMaterial(bytes[index + 2]));
				bytes[index + 2] = -2;
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0) {
			if (args[0].equals("has")) {
				int x = Integer.parseInt(args[1]);
				int z = Integer.parseInt(args[2]);
				sender.sendMessage(AntiXRayPlugin.getInstance().getWorldDataManager().hasChunkData(Bukkit.getWorld("world"), x, z) + "");
			} else if (args[0].equals("restore")) {
				Player player = (Player) sender;
				restore(player.getWorld().getChunkAt(player.getLocation()));
				restore(player.getWorld().getChunkAt(player.getLocation().add(16, 0, 0)));
				restore(player.getWorld().getChunkAt(player.getLocation().add(-16, 0, 0)));
				restore(player.getWorld().getChunkAt(player.getLocation().add(-16, 0, 16)));
				restore(player.getWorld().getChunkAt(player.getLocation().add(-16, 0, -16)));
				restore(player.getWorld().getChunkAt(player.getLocation().add(16, 0, 16)));
				restore(player.getWorld().getChunkAt(player.getLocation().add(16, 0, -16)));
				restore(player.getWorld().getChunkAt(player.getLocation().add(0, 0, 16)));
				restore(player.getWorld().getChunkAt(player.getLocation().add(0, 0, -16)));
			} else if (args[0].equals("process")) {
				Player player = (Player) sender;
				AntiXRayEventHandler.processChunk(player.getLocation().getChunk(), null, true, true, true, true);
			}
		}
		return false;
	}
}