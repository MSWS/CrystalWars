package xyz.msws.crystal.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import xyz.msws.crystal.game.GameManager;
import xyz.msws.crystal.utils.MSG;
import xyz.msws.crystal.utils.PlayerManager;
import xyz.msws.crystal.CrystalWars;

public class TeamCommand implements CommandExecutor, TabCompleter {
	public TeamCommand() {
		PluginCommand cmd = CrystalWars.plugin.getCommand("team");
		cmd.setExecutor(this);
		cmd.setTabCompleter(this);
	}

	PlayerManager pManager = new PlayerManager();
	GameManager gManager = new GameManager();
	FileConfiguration data = CrystalWars.plugin.data;

	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			MSG.tell(sender, MSG.getString("MustBePlayer", "You must be a player"));
			return true;
		}
		Player player = (Player) sender;
		String worldName = player.getWorld().getName();
		pManager.setInfo(player, "prefix", "/" + label);
		if (args.length == 0) {
			MSG.sendHelp(sender, 0, "team");
			return true;
		}
		if (gManager.getTeams(player.getWorld()) == null || (gManager.getTeams(player.getWorld()) != null
				&& !gManager.getTeams(player.getWorld()).contains(args[0]))) {
			MSG.tell(sender, MSG.getString("Invalid.Team", "Invalid team name"));
			return true;
		}
		if (!(gManager.getStatus(player.getWorld()).equals("lobby")
				|| gManager.getStatus(player.getWorld()).contains("countdown"))) {
			MSG.tell(sender, MSG.getString("Invalid.Time", "Can't switch teams midgame"));
			return true;
		}
		String cTeam = pManager.getTeam(player);
		if (cTeam == null || cTeam == args[0]) {
			MSG.tell(sender, MSG.getString("Already.Teamed", "Invalid team"));
			return true;
		}
		if (gManager.getTeamMembers(player.getWorld(), args[0]) != null)
			for (Player p : gManager.getTeamMembers(player.getWorld(), args[0])) {
				if (!data.contains("Games." + worldName + ".teams." + args[0] + ".members." + p.getName() + ".wants"))
					continue;
				if (data.getString("Games." + worldName + ".teams." + args[0] + ".members." + p.getName() + ".wants")
						.equals(pManager.getTeam(player))) {
					data.set("Games." + worldName + ".teams." + cTeam + ".members." + p.getName() + ".alive", true);
					data.set("Games." + worldName + ".teams." + args[0] + ".members." + player.getName() + ".alive",
							true);
					data.set("Games." + worldName + ".teams." + cTeam + ".members." + player.getName(), null);
					data.set("Games." + worldName + ".teams." + args[0] + ".members" + p.getName(), null);
					MSG.tell(sender,
							MSG.getString("Swapped.Team", "swapped teams with %teamColor%%name%")
									.replace("%teamColor%", gManager.getColor(player.getWorld(), args[0]))
									.replace("%name%", p.getName()));
					MSG.tell(sender,
							MSG.getString("Swapped.Team", "swapped teams with %teamColor%%name%")
									.replace("%teamColor%", gManager.getColor(player.getWorld(), cTeam))
									.replace("%name%", player.getName()));

					return true;
				}
			}
		data.set("Games." + worldName + ".teams." + cTeam + ".members." + player.getName() + ".wants", args[0]);
		MSG.tell(sender,
				MSG.getString("Swapped.Queue", "You are in queue to swap to %teamColor%%teamName%")
						.replace("%teamColor%", gManager.getColor(player.getWorld(), args[0]))
						.replace("%teamName%", MSG.camelCase(args[0])));
		return true;
	}

	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player))
			return null;
		Player player = (Player) sender;
		if (gManager.getTeams(player.getWorld()) == null)
			return null;
		List<String> result = new ArrayList<String>();
		for (String res : gManager.getTeams(player.getWorld())) {
			if (res.toLowerCase().startsWith(args[0].toLowerCase()))
				result.add(res);
		}
		return result;
	}
}
