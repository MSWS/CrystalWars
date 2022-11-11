package xyz.msws.crystal.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import xyz.msws.crystal.game.GameManager;
import xyz.msws.crystal.utils.MSG;
import xyz.msws.crystal.utils.PlayerManager;
import xyz.msws.crystal.CrystalWars;

public class KitCommand implements CommandExecutor, TabCompleter {
	public KitCommand() {
		PluginCommand cmd = CrystalWars.plugin.getCommand("kit");
		cmd.setExecutor(this);
	}

	PlayerManager pManager = new PlayerManager();
	GameManager gManager = new GameManager();

	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			MSG.tell(sender, MSG.getString("MustBePlayer", "You must be a player."));
			return true;
		}
		Player player = (Player) sender;
		if (args.length == 0) {
			MSG.tell(sender, MSG.getString("Set.Kit", "your kit is %kit%").replace("%kit%",
					MSG.camelCase(pManager.getKit(player))));
			if (gManager.getStatus(player.getWorld()).equals("lobby")
					|| gManager.getStatus(player.getWorld()).contains("countdown"))
				player.openInventory(pManager.getGui(player, "kitMenu"));
			return true;
		}
		if (!(gManager.getStatus(player.getWorld()).equals("lobby")
				|| gManager.getStatus(player.getWorld()).contains("countdown"))) {
			MSG.tell(sender, MSG.getString("Invalid.Time", "Can't switch kits midgame"));
			return true;
		}
		ConfigurationSection kits = CrystalWars.plugin.config.getConfigurationSection("Kits");
		if (kits == null) {
			MSG.tell(sender, MSG.getString("Invalid.Kit", "Kit not found"));
			return true;
		} else if (!kits.contains(args[0])) {
			MSG.tell(sender, MSG.getString("Invalid.Kit", "Kit not found"));
			return true;
		}
		if (!sender.hasPermission("crystal.kit." + args[0])) {
			MSG.noPerm(sender);
			return true;
		}
		pManager.setInfo(player, "kit", args[0]);
		MSG.tell(sender, MSG.getString("Swapped.Kits", "Your kit is %kit%").replace("%kit%", MSG.camelCase(args[0])));
		CrystalWars.plugin.saveData();
		return true;
	}

	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		List<String> result = new ArrayList<String>();
		ConfigurationSection kits = CrystalWars.plugin.config.getConfigurationSection("Kits");
		if (kits == null)
			return result;
		if (args.length == 1) {
			for (String res : kits.getKeys(false)) {
				if (sender.hasPermission("crystal.kit." + res) && res.toLowerCase().startsWith(args[0].toLowerCase()))
					result.add(res);
			}
		}
		return result;
	}
}
