package xyz.msws.crystal.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import xyz.msws.crystal.game.GameManager;
import xyz.msws.crystal.scoreboard.SBoard;
import xyz.msws.crystal.utils.MSG;
import xyz.msws.crystal.utils.PlayerManager;
import xyz.msws.crystal.CrystalWars;

public class CrystalCommand implements CommandExecutor, TabCompleter {
	public CrystalCommand() {
		PluginCommand cmd = CrystalWars.plugin.getCommand("cw");
		cmd.setExecutor(this);
		cmd.setTabCompleter(this);
	}

	PlayerManager pManager = new PlayerManager();
	GameManager gManager = new GameManager();
	FileConfiguration data = CrystalWars.plugin.data;

	@SuppressWarnings({ "unchecked", "deprecation" })
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission("crystal.command")) {
			if (CrystalWars.plugin.config.getBoolean("FakeInvalidCommand")) {
				MSG.tell(sender, "Unknown command. Type \"/help\" for help.");
			} else {
				MSG.noPerm(sender);
			}
			return true;
		}
		if (args.length == 0) {
			MSG.sendHelp(sender, 0, "default");
			return true;
		}
		if (((args.length > 0 && !args[0].matches("(?i)(reload|reset|stop)"))) && !isPlayer(sender))
			return true;
		Player player = null;
		OfflinePlayer target;
		String worldName = null;
		if (sender instanceof Player) {
			player = (Player) sender;
			worldName = player.getWorld().getName();
		}
		if (!sender.hasPermission("crystal." + args[0])) {
			MSG.noPerm(sender);
			return true;
		}
		switch (command.getName().toLowerCase()) {
		case "cw":
			if (sender instanceof Player)
				pManager.setInfo(player, "prefix", "/" + label);
			if (args[0].matches("(?i)(create|delete|set|resetstats)")) {
				if (args.length == 1) {
					MSG.sendHelp(sender, 0, args[0]);
					return true;
				} else if (args.length == 2) {
					try {
						MSG.sendHelp(sender, Integer.valueOf(args[1]) - 1, args[0]);
						return true;
					} catch (Exception e) {
					}
				}
			}
			switch (args[0].toLowerCase()) {
			case "refresh":
				gManager.refreshLeaderboards(player.getWorld());
				break;
			case "win": // Beta Testing Reasons
				gManager.winGame(player.getWorld());
				break;
			case "save": // Manually save the world
				MSG.tell(sender, MSG.getString("Game.Saved", "world saved"));
				gManager.saveWorld(player.getWorld());
				break;
			case "load": // Manually load the world, useful for greif prevention if it happens
				MSG.tell(sender, MSG.getString("Game.Loading", "Resetting %world%...").replace("%world%", worldName));
				gManager.reloadWorld(player.getWorld());
				MSG.tell(sender, MSG.getString("Game.Loaded", "world loaded"));
				break;
			case "resetstats":
				target = Bukkit.getPlayer(args[1]);
				if (target == null)
					target = Bukkit.getOfflinePlayer(args[1]);
				for (String res : pManager.getStats(target)) {
					pManager.removeStat(player, res);
				}
				MSG.tell(sender,
						MSG.getString("Stats.Reset", "reset %player%'s stats").replace("%player%", target.getName()));
				break;
			case "stats":
				target = player;
				if (args.length > 1 && player.hasPermission("crystal.stats.other")) {
					target = Bukkit.getPlayer(args[1]);
					if (target == null)
						target = Bukkit.getOfflinePlayer(args[1]);
				}
				player.openInventory(pManager.getGui(target, "statsGui"));
				break;
			case "validate":
			case "checklist":
			case "check":
				MSG.tell(sender, "");
				MSG.tell(sender, "Checklist for " + worldName);
				MSG.tell(sender, "");
				if (!gameExists(player)) {
					MSG.tell(sender, MSG.getString("Checklist.Invalid", "create a game first"));
					break;
				}
				int fail = 0;
				List<String> teams = gManager.getTeams(player.getWorld());
				if (teams == null || teams.size() == 1) {
					MSG.tell(player, MSG.getString("Checklist.Teams", "No teams or not enough teams set"));
					fail++;
				}
				if (teams != null) {
					for (String team : teams) {
						List<Location> usedLocations = new ArrayList<Location>();
						for (String res : new String[] { "coalShop", "endShop", "teamShop" }) {
							Location tmp = CrystalWars.plugin.getLocation("Games." + worldName + ".teams." + team + "." + res);
							if (tmp == null) {
								MSG.tell(sender,
										MSG.getString("Checklist.ShopNull", "%shop% not set")
												.replace("%teamColor%", gManager.getColor(player.getWorld(), team))
												.replace("%teamName%", MSG.camelCase(team)).replace("%shop%", res));
								fail++;
								continue;
							}
							if (usedLocations.contains(tmp)) {
								MSG.tell(sender,
										MSG.getString("Checklist.ShopDuplicate", "%shop%'s location taken")
												.replace("%teamColor%", gManager.getColor(player.getWorld(), team))
												.replace("%teamName%", MSG.camelCase(team)).replace("%shop%", res));
								fail++;
							}
							usedLocations.add(tmp);
						}
						if (!CrystalWars.plugin.data
								.contains("Games." + player.getWorld().getName() + ".teams." + team + ".spawnPoint0")) {
							MSG.tell(sender,
									MSG.getString("Checklist.Spawns", "no spawns for %teamName%")
											.replace("%teamColor%", gManager.getColor(player.getWorld(), team))
											.replace("%teamName%", MSG.camelCase(team)));
							fail++;
						}
					}
				}
				if (!CrystalWars.plugin.data.contains("Games." + worldName + ".builders")) {
					MSG.tell(sender, MSG.getString("Checklist.Builders", "no builders set"));
					fail++;
				}
				if (!CrystalWars.plugin.data.contains("Games." + worldName + ".endLocation")) {
					MSG.tell(sender, MSG.getString("Checklist.EndLocation", "end location isn't set"));
					fail++;
				}
				if (!CrystalWars.plugin.config.contains("Kits")
						|| CrystalWars.plugin.config.getConfigurationSection("Kits").getKeys(false).size() == 0) {
					MSG.tell(sender, MSG.getString("Checklist.Kits", "kits aren't set"));
					fail++;
				}
				if (CrystalWars.plugin.getLocation("Games." + worldName + ".lobby")
						.equals(CrystalWars.plugin.getLocation("Games." + worldName + ".specSpawn"))) {
					MSG.tell(sender, MSG.getString("Checklist.Lobby", "lobby and spec spawns are the same"));
					fail++;
				}
				if (fail == 0) {
					MSG.tell(sender, MSG.getString("Checklist.Pass", "all clear!"));
				} else {
					MSG.tell(sender, MSG.getString("Checklist.Missing", "you missed %amo% items")
							.replace("%amo%", fail + "").replace("%s%", fail == 1 ? "" : "s"));
				}
				break;
			case "revive": // Beta Test or just for fun
				if (!gManager.getStatus(player.getWorld()).contains("ingame")) {
					MSG.tell(sender, MSG.getString("Already.Stopped", "There is no game in play"));
					break;
				}
				if (gManager.getTeams(player.getWorld()).contains(args[1])) {
					for (Player rTarget : gManager.getTeamMembers(player.getWorld(), args[1])) {
						if (rTarget.getWorld() != player.getWorld())
							continue;
						data.set("Games." + player.getWorld().getName() + ".teams." + args[1] + ".members."
								+ rTarget.getName() + ".alive", true);
						pManager.spawnPlayer(rTarget);
						return true;
					}
				}
				Player rTarget = Bukkit.getPlayer(args[1]);
				if (rTarget == null || (rTarget != null && !player.getWorld().getPlayers().contains(rTarget))) {
					MSG.tell(sender, MSG.getString("Invalid.Player", "Invalid Player"));
					break;
				}
				data.set("Games." + player.getWorld().getName() + ".teams." + pManager.getTeam(rTarget) + ".members."
						+ rTarget.getName() + ".alive", true);
				pManager.spawnPlayer(rTarget);
				break;
			case "help": // Sends detailed help messages
				if (args.length == 1) {
					MSG.sendHelp(sender, 0, "default");
					break;
				}
				if (args.length == 3) {
					try {
						MSG.sendHelp(sender, Integer.valueOf(args[2]) - 1, args[1]);
						break;
					} catch (Exception e) {
					}
				}
				try {
					MSG.sendHelp(sender, Integer.valueOf(args[1]) - 1, "default");
				} catch (Exception e) {
					MSG.sendHelp(sender, 0, args[1]);
				}
				break;
			case "join":
			case "tp": // Teleports the player to their specified game
				if (args.length == 1) {
					MSG.tell(sender, MSG.getString("Missing.World", "Specify world"));
					break;
				}
				World jWorld = Bukkit.getWorld(args[1]);
				if (jWorld == null) {
					MSG.tell(sender, MSG.getString("Invalid.World", "World not found"));
					break;
				}
				player.teleport(jWorld.getSpawnLocation());
				break;
			case "aj":
			case "autojoin": // Teleports the player to the game tat is still in a lobby and has most players
				List<World> games = new ArrayList<World>();
				for (String res : data.getConfigurationSection("Games").getKeys(false)) {
					World tmp = Bukkit.getWorld(res);
					if (tmp != null && !gManager.getStatus(tmp).contains("ingame"))
						games.add(tmp);
				}
				int max = 0;
				World join = null;
				for (World world : games)
					if (world.getPlayers().size() >= max && world != player.getWorld()) {
						join = world;
						max = world.getPlayers().size();
					}
				if (join == null) {
					MSG.tell(sender, MSG.getString("Missing.NextGame", "There are no good servers to send you to."));
					break;
				}
				player.teleport(join.getSpawnLocation());
				break;
			case "create": // Create games, worlds, teams, etc
				switch (args[1].toLowerCase()) {
				case "game":
					String name = "";
					if (args.length == 3) {
						name = args[2];
					}
					if ((worldName.contains("world") || data.contains("Games." + worldName))) {
						if (name.equals(""))
							name = "CrystalGame";
						if (Bukkit.getWorld(name) != null) {
							int pos = 0;
							while (Bukkit.getWorld(name + pos) != null)
								pos++;
							name = name + pos;
						}
						MSG.tell(sender, MSG.getString("Created.World", "creating %world%").replace("%world%", name));
						World world = Bukkit.createWorld(WorldCreator.name(name).type(WorldType.FLAT));
						world.setGameRuleValue("doMobSpawning", "false");
						world.setGameRuleValue("mobGreifing", "false");
						data.set("Games." + world.getName() + ".endLocation", player.getWorld().getName());
						player.teleport(world.getSpawnLocation());
						worldName = world.getName();
					}
					data.set("Games." + worldName + ".maxTeamSize", 4);
					data.set("Games." + worldName + ".maxSize", 16);
					data.set("Games." + worldName + ".startAt", 8);
					CrystalWars.plugin.saveLocation("Games." + worldName + ".lobby", player.getLocation());
					CrystalWars.plugin.saveLocation("Games." + worldName + ".specSpawn", player.getLocation());
					gManager.setInfo(player.getWorld(), "status", "lobby");
					MSG.tell(sender, MSG.getString("Created.Game", "Game created"));
					gManager.saveWorld(player.getWorld());
					break;
				case "team":
					if (!data.contains("Games." + worldName)) {
						MSG.tell(sender, MSG.getString("Invalid.Game", "There is no game in this world"));
						break;
					}
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Name", "Specify a team name"));
						break;
					}
					if (args.length == 3) {
						MSG.tell(sender, MSG.getString("Missing.Color", "Specify a team color"));
						break;
					}
					if (data.contains("Games." + worldName + ".teams." + args[2])) {
						MSG.tell(sender, MSG.getString("Already.Team", "Team already exists"));
						break;
					}
					for (String res : new String[] { "coalShop", "endShop", "teamShop", "crystal", "resourceSpawn" }) {
						CrystalWars.plugin.saveLocation("Games." + worldName + ".teams." + args[2] + "." + res,
								player.getLocation());
					}
					data.set("Games." + worldName + ".teams." + args[2] + ".color", args[3]);
					MSG.tell(sender, MSG.getString("Created.Team", "Created a team, total: %total%").replace("%total%",
							data.getConfigurationSection("Games." + worldName + ".teams").getKeys(false).size() + ""));
					gManager.assignTeams(player.getWorld());
					break;
				case "spawn":
					if (!data.contains("Games." + worldName)) {
						MSG.tell(sender, MSG.getString("Missing.Game", "There is no game in this world"));
						break;
					}
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Name", "Specify a team name"));
						break;
					}
					if (!teamExists(sender, args[2]))
						break;
					int pos = 0;
					while (data.contains("Games." + worldName + ".teams." + args[2] + ".spawnPoint" + pos))
						pos++;
					CrystalWars.plugin.saveLocation("Games." + worldName + ".teams." + args[2] + ".spawnPoint" + pos,
							player.getLocation());
					MSG.tell(sender,
							MSG.getString("Created.Spawn", "Created spawn %id% for %teamColor%%teamName%")
									.replace("%id%", pos + "")
									.replace("%teamColor%",
											data.getString("Games." + worldName + ".teams." + args[2] + ".color"))
									.replace("%teamName%", MSG.camelCase(args[2])));
					break;
				default:
					MSG.sendHelp(sender, 0, args[0]);
					break;
				}
				break;
			case "delete":
				if (args.length == 1) {
					MSG.sendHelp(sender, 0, args[0]);
					break;
				}
				switch (args[1].toLowerCase()) {
				case "game":
					if (!data.contains("Games." + worldName)) {
						MSG.tell(sender, MSG.getString("Invalid.Game", "Game doesn't exist in this world"));
						break;
					}
					if (!gManager.getStatus(player.getWorld()).equals("lobby")) {
						gManager.stopGame(player.getWorld());
					}
					data.set("Games." + worldName, null);
					for (Player sTarget : player.getWorld().getPlayers())
						sTarget.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
					MSG.tell(sender, MSG.getString("Deleted.Game", "Deleted the game"));
					break;
				case "team":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Name", "Specify a name"));
						break;
					}
					if (!teamExists(sender, args[2]))
						break;
					MSG.tell(sender,
							MSG.getString("Deleted.Team", "Deleted team %teamColor%%teamName%.")
									.replace("%teamColor%", gManager.getColor(player.getWorld(), args[2]))
									.replace("%teamName%", MSG.camelCase(args[2])));
					data.set("Games." + worldName + ".teams." + args[2], null);
					break;
				case "spawn":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Name", "Specify a name"));
						break;
					}
					if (!teamExists(sender, args[2]))
						break;
					if (!data.contains("Games." + worldName + ".teams." + args[2] + ".spawnPoint" + args[3])) {
						MSG.tell(sender, MSG.getString("Invalid.ID", "spawn doesn't exist"));
						break;
					}
					MSG.tell(sender, MSG.getString("Deleted.Spawn", "Deleted spawn"));
					data.set("Games." + worldName + ".teams." + args[2] + ".spawnPoint" + args[3], null);
					break;
				case "leaderboard":
					String closest = gManager.getClosestLeaderboard(player.getLocation());
					if (closest.equals("")) {
						MSG.tell(sender, MSG.getString("Invalid.Location", "There aren't any leaderboards nearby."));
						break;
					}
					MSG.tell(sender, MSG.getString("Leaderboards.Deleted", "deleted %stat% leaderboard")
							.replace("%stat%", MSG.camelCase(closest)));
					CrystalWars.plugin.data.set("Leaderboards." + worldName + "." + closest, null);
					gManager.refreshLeaderboards(player.getWorld());
					break;
				}
				break;
			case "set": // Set spawns, shop locations, etc.
				if (args.length == 1) {
					MSG.sendHelp(sender, 0, args[0]);
					break;
				} else {
					if (!args[1].matches("(?i)(stat|leaderboard)") && !gameExists(player))
						break;
				}
				switch (args[1].toLowerCase()) {
				case "stat":
				case "stats":
					if (args.length < 5) {
						MSG.tell(sender, MSG.getString("Missing.Stat", "specify a stat"));
						break;
					}
					target = Bukkit.getPlayer(args[2]);
					if (target == null) {
						target = Bukkit.getOfflinePlayer(args[2]);
					}
					pManager.setStat(target, args[3], Double.parseDouble(args[4]));
					MSG.tell(sender,
							MSG.getString("Stats.Set", "set %player%'s %stat% stat to %amo%")
									.replace("%player%", target.getName()).replace("%stat%", args[3])
									.replace("%amo%", Double.parseDouble(args[4]) + ""));
					break;
				case "leaderboard":
					if (args.length < 3) {
						MSG.tell(sender, MSG.getString("Missing.Stat", "You must specify a stat"));
						break;
					}
					CrystalWars.plugin.saveLocation("Leaderboards." + worldName + "." + args[2], player.getLocation());
					gManager.refreshLeaderboards(player.getWorld());
					break;
				case "builders":
					if (!gameExists(player))
						break;
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Builders", "please specify builders"));
						break;
					}
					String builders = "";
					for (String res : args) {
						if (!res.equals(args[0]) && !res.equals(args[1]))
							builders = builders + res + " ";
					}
					builders = builders.trim();
					MSG.tell(sender, MSG.getString("Set.Builders", "builders set to %builders%").replace("%builders%",
							builders));
					CrystalWars.plugin.data.set("Games." + worldName + ".builders", builders);
					break;
				case "spawn":
					if (args.length <= 3) {
						MSG.tell(sender, MSG.getString("Missing.ID", "You must specify what spawn id"));
						break;
					}
					if (!teamExists(sender, args[2]))
						break;
					Integer i = 0;
					try {
						i = Integer.valueOf(args[3]);
					} catch (Exception e) {
						MSG.tell(sender, MSG.getString("Invalid.ID", "No spawn found"));
						break;
					}
					ConfigurationSection spawns = data
							.getConfigurationSection("Games." + worldName + ".teams." + args[2]);
					if (!spawns.contains("spawnPoint" + args[3])) {
						MSG.tell(sender, MSG.getString("Invalid.ID", "No spawn found"));
						break;
					}

					CrystalWars.plugin.saveLocation("Games." + worldName + ".teams." + args[2] + ".spawnPoint" + args[3],
							player.getLocation());
					MSG.tell(sender,
							MSG.getString("Created.Spawn", "Set spawn %id% for %teamColor%%teamName%")
									.replace("%id%", i + "")
									.replace("%teamColor%",
											data.getString("Games." + worldName + ".teams." + args[2] + ".color"))
									.replace("%teamName%", args[2]));
					break;
				case "kit":
					if (args.length <= 2) {
						MSG.sendHelp(sender, 0, args[0]);
						break;
					}
					Player setKitTo = player;
					String kitName = args[2];
					if (args.length > 3) {
						setKitTo = Bukkit.getPlayer(args[2]);
						kitName = args[3];
					}
					if (setKitTo == null) {
						MSG.tell(sender, MSG.getString("Invalid.Player", "player not found"));
						break;
					}
					if (!CrystalWars.plugin.config.contains("Kits." + kitName)) {
						MSG.tell(sender, MSG.getString("Invalid.Kit", "Invalid kit"));
						break;
					}
					pManager.setInfo(setKitTo, "kit", kitName);
					MSG.tell(setKitTo, MSG.getString("Swapped.Kits", "your kit is now %kit%").replace("%kit%",
							MSG.camelCase(kitName)));
					break;
				case "team":
					if (args.length <= 2) {
						MSG.sendHelp(sender, 0, args[0]);
						break;
					}
					Player tTarget = player;
					String team = args[2];
					if (args.length > 3) {
						tTarget = Bukkit.getPlayer(args[2]);
						team = args[3];
					}
					if (tTarget == null) {
						MSG.tell(sender, MSG.getString("Invalid.Player", "player not found"));
						break;
					}
					if (!gManager.getTeams(player.getWorld()).contains(team)) {
						MSG.tell(sender, MSG.getString("Invalid.Team", "Unknown team"));
						break;
					}
					if (pManager.getTeam(tTarget) != null) {
						data.set("Games." + tTarget.getWorld().getName() + ".teams." + pManager.getTeam(tTarget)
								+ ".members." + tTarget.getName() + ".alive", null);
					}
					data.set("Games." + worldName + ".teams." + team + ".members." + tTarget.getName() + ".alive",
							true);
					MSG.tell(tTarget,
							MSG.getString("Set.Team", "your team is now %teamColor%%teamName%")
									.replace("%teamColor%", gManager.getColor(player.getWorld(), team))
									.replace("%teamName%", MSG.camelCase(team)));
					gManager.updateNames(player.getWorld());
					break;
				case "time":
					String time = "";
					double mills = 0;
					switch (args[2]) {
					case "18000":
						time = "Midnight";
						break;
					case "0":
						time = "Morning";
						break;
					case "6000":
						time = "Day";
						break;
					case "1200":
						time = "Sunset";
						break;
					case "day":
						time = "Day";
						mills = 6000;
						break;
					case "morning":
						time = "Morning";
						mills = 0;
						break;
					case "night":
					case "midnight":
						mills = 1800;
						time = "Midnight";
						break;
					case "sunset":
						mills = 1200;
						time = "Sunset";
						break;
					default:
						time = args[2];
						try {
							mills = Double.parseDouble(args[2]);
						} catch (NumberFormatException e) {
							MSG.tell(sender, MSG.getString("Invalid.Mils", "invalid time"));
							return true;
						}
						break;
					}
					player.getWorld().setTime((long) mills);
					player.getWorld().setGameRuleValue("doDaylightCycle", "false");
					MSG.tell(sender, MSG.getString("Set.Time", "time is set to %time%").replace("%time%", time));
					break;
				case "chp":
				case "crystalhp":
				case "crystalhealth": // Beta Testing / for fun
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Name", "Please specify a team's name"));
						break;
					}
					if (args.length == 3) {
						MSG.tell(sender, MSG.getString("Missing.Number", "Please specify a number"));
						break;
					}
					if (!teamExists(sender, args[2]))
						break;
					if (!gManager.getStatus(player.getWorld()).contains("ingame")) {
						MSG.tell(sender, MSG.getString("Already.Stopped", "There is no game in play"));
						break;
					}
					List<String> uuids = (List<String>) gManager.getInfo(player.getWorld(), "removeEntities");
					List<String> crystal = (List<String>) gManager.getInfo(player.getWorld(), args[2] + "crystal");
					if (gManager.getCrystalHP(player.getWorld(), args[2]) <= 0) {
						Entity ent = Bukkit.getWorld(worldName).spawnEntity(
								CrystalWars.plugin.getLocation("Games." + worldName + ".teams." + args[2] + ".crystal"),
								EntityType.ENDER_CRYSTAL);
						uuids.add(ent.getUniqueId() + "");
						crystal.add(ent.getUniqueId() + "");
						gManager.setInfo(player.getWorld(), "removeEntities", uuids);
						gManager.setInfo(player.getWorld(), args[2] + "crystal", crystal);
					}
					if (args[3].equals("0")) {
						for (String res : crystal) {
							Entity ent = (Entity) gManager.getEntityByUUID(UUID.fromString(res), player.getWorld());
							if (ent instanceof EnderCrystal) {
								ent.remove();
								player.getWorld().createExplosion(ent.getLocation(), 0);
								uuids.remove(ent.getUniqueId() + "");
								crystal.remove(ent.getUniqueId() + "");
								break;
							}
						}
						gManager.setInfo(player.getWorld(), args[2] + "crystal", crystal);
						gManager.setInfo(player.getWorld(), "removeEntities", uuids);
					}
					CrystalWars.plugin.data.set("Games." + worldName + ".teams." + args[2] + ".crystalHealth",
							Double.valueOf(args[3]));
					MSG.tell(sender,
							MSG.getString("Set.CrystalHealth", "Set %teamColor%%teamName%'s crystal hp to %hp%")
									.replace("%teamColor%", gManager.getColor(player.getWorld(), args[2]))
									.replace("%teamName%", MSG.camelCase(args[2]))
									.replace("%hp%", Math.round(Double.valueOf(args[3])) + ""));

					break;
				case "resourcespawn":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Name", "Please specify a team's name"));
						break;
					}
					if (!teamExists(sender, args[2]))
						break;
					CrystalWars.plugin.saveLocation("Games." + worldName + ".teams." + args[2] + ".resourceSpawn",
							player.getLocation());
					MSG.tell(sender,
							MSG.getString("Created.Resource", "Created resource spawn %id% for %teamColor%%teamName%")
									.replace("%teamColor%",
											data.getString("Games." + worldName + ".teams." + args[2] + ".color"))
									.replace("%teamName%", MSG.camelCase(args[2])));
					break;
				case "maxsize":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Size", "Please specify a max size"));
						break;
					}
					MSG.tell(sender,
							MSG.getString("Set.MaxSize", "population limit is now %amo%").replace("%amo%", args[2]));
					data.set("Games." + worldName + ".maxSize", Integer.valueOf(args[2]));
					break;
				case "maxteamsize":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Size", "Please specify a max team size"));
						break;
					}
					data.set("Games." + worldName + ".maxTeamSize", Integer.valueOf(args[2]));
					break;
				case "lobby":
					MSG.tell(sender, MSG.getString("Set.Lobby", "Set lobby"));
					CrystalWars.plugin.saveLocation("Games." + worldName + ".lobby", player.getLocation());
					break;
				case "endlocation":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.World", "Please specify which world"));
						break;
					}
					World world = Bukkit.getWorld(args[2]);
					if (world == null) {
						MSG.tell(sender, MSG.getString("Invalid.World", "Invalid world"));
						break;
					}
					MSG.tell(sender, MSG.getString("Set.EndLocation", "Set endlocation to %world%").replace("%world%",
							world.getName()));
					CrystalWars.plugin.saveLocation("Games." + worldName + ".endLocation", world.getSpawnLocation());
					break;
				case "specspawn":
					MSG.tell(sender, MSG.getString("Set.SpecSpawn", "Set spectator spawn"));
					CrystalWars.plugin.saveLocation("Games." + worldName + ".specSpawn", player.getLocation());
					break;
				case "startat":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Number", "Please specify when the game will start"));
						break;
					}
					MSG.tell(sender,
							MSG.getString("Set.StartAt", "Game will start at %amo%").replace("%amo%", args[2]));
					data.set("Games." + worldName + ".startAt", Integer.valueOf(args[2]));
					break;
				case "coalshop":
				case "endshop":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Name", "Please specify a team's name"));
						break;
					}
					if (!teamExists(sender, args[2]))
						break;
					MSG.tell(sender,
							MSG.getString(args[1].equalsIgnoreCase("coalShop") ? "Set.CoalShop" : "Set.EndShop",
									"Succesfully set %teamColor%%teamName%'s " + args[1] + " location")
									.replace("%teamColor%", gManager.getColor(player.getWorld(), args[2]))
									.replace("%teamName%", MSG.camelCase(args[2])));
					CrystalWars.plugin.saveLocation(
							"Games." + worldName + ".teams." + args[2] + "."
									+ (args[1].equalsIgnoreCase("coalshop") ? "coalShop" : "endShop"),
							player.getLocation());
					break;
				case "teamshop":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Name", "Please specify a team's name"));
						break;
					}
					if (!teamExists(sender, args[2]))
						break;
					MSG.tell(sender,
							MSG.getString("Set.TeamShop", "Succesfully set %teamColor%%teamName%'s teamshop location")
									.replace("%teamColor%", gManager.getColor(player.getWorld(), args[2]))
									.replace("%teamName%", MSG.camelCase(args[2])));
					CrystalWars.plugin.saveLocation("Games." + worldName + ".teams." + args[2] + ".teamShop",
							player.getLocation());
					break;
				case "crystal":
					if (args.length == 2) {
						MSG.tell(sender, MSG.getString("Missing.Name", "Please specify a team's name"));
						break;
					}
					if (!teamExists(sender, args[2]))
						break;
					MSG.tell(sender,
							MSG.getString("Set.Crystal", "Succesfully set %teamColor%%teamName%'s crystal location")
									.replace("%teamColor%", gManager.getColor(player.getWorld(), args[2]))
									.replace("%teamName%", MSG.camelCase(args[2])));
					CrystalWars.plugin.saveLocation("Games." + worldName + ".teams." + args[2] + ".crystal",
							player.getLocation());
					break;
				default:
					MSG.sendHelp(sender, 0, args[0]);
					break;
				}
				break;
			case "start":
				if (!gameExists(player))
					break;
				if (!gManager.getStatus(player.getWorld()).equals("lobby")) {
					MSG.tell(sender, MSG.getString("Already.InGame", "Game already going"));
					break;
				}
				int time = CrystalWars.plugin.config.getInt("DefaultCountdown");
				if (args.length == 2)
					try {
						time = Integer.valueOf(args[1]);
					} catch (Exception e) {
						MSG.tell(sender, MSG.getString("Invalid.StartTime", "Invalid start time"));
					}
				gManager.startCountdown(player.getWorld(), time);
				MSG.tell(player.getWorld(), MSG.getString("Game.Started", "%player% started the game")
						.replace("%player%", player.getName()));
				break;
			case "stop":
				if (!gameExists(player))
					break;
				if (gManager.getStatus(player.getWorld()).equals("lobby")) {
					MSG.tell(sender, MSG.getString("Already.Stopped", "Game isn't going"));
					break;
				}
				gManager.stopGame(player.getWorld());
				MSG.tell(player.getWorld(), MSG.getString("Game.Stopped", "%player% stopped the game")
						.replace("%player%", player.getName()));
				break;
			case "crash":
				if (!gameExists(player))
					break;
				if (gManager.getStatus(player.getWorld()).equals("lobby")) {
					MSG.tell(sender, MSG.getString("Already.Stopped", "Game isn't going"));
					break;
				}
				String reason = "";
				if (args.length > 1) {
					for (String res : args)
						if (!res.equals(args[0]))
							reason = reason + res + " ";
					reason = reason.trim();
				} else {
					reason = "Manual Crash issued by " + sender.getName();
				}
				gManager.crashGame(player.getWorld(), reason);
				break;
			case "list":
				ConfigurationSection gameList = data.getConfigurationSection("Games");
				if (args.length == 1) {
					for (String res : gameList.getKeys(false)) {
						MSG.tell(sender, res);
					}
					break;
				}
				switch (args[1].toLowerCase()) {
				case "teams":
					if (!CrystalWars.plugin.data.contains("Games." + worldName + ".teams")) {
						MSG.tell(sender, MSG.getString("Missing.Teams", "There aren't any teams"));
						break;
					}
					for (String res : gManager.getTeams(player.getWorld()))
						MSG.tell(sender, gManager.getColor(player.getWorld(), res) + MSG.camelCase(res));
					break;
				}
				break;
			case "reload":
				if (args.length == 1) {
					for (String res : new String[] { "config", "guis", "lang" })
						CrystalWars.plugin.saveResource(res + ".yml", true);
					CrystalWars.plugin.configYml = new File(CrystalWars.plugin.getDataFolder(), "config.yml");
					CrystalWars.plugin.langYml = new File(CrystalWars.plugin.getDataFolder(), "lang.yml");
					CrystalWars.plugin.guiYml = new File(CrystalWars.plugin.getDataFolder(), "guis.yml");
				} else {
					switch (args[1].toLowerCase()) {
					case "all":
						CrystalWars.plugin.configYml = new File(CrystalWars.plugin.getDataFolder(), "config.yml");
						CrystalWars.plugin.langYml = new File(CrystalWars.plugin.getDataFolder(), "lang.yml");
						CrystalWars.plugin.guiYml = new File(CrystalWars.plugin.getDataFolder(), "guis.yml");
						break;
					case "config":
						CrystalWars.plugin.configYml = new File(CrystalWars.plugin.getDataFolder(), "config.yml");
						CrystalWars.plugin.config = YamlConfiguration.loadConfiguration(CrystalWars.plugin.configYml);
						break;
					case "gui":
					case "guis":
						CrystalWars.plugin.guiYml = new File(CrystalWars.plugin.getDataFolder(), "guis.yml");
						break;
					case "lang":
						CrystalWars.plugin.langYml = new File(CrystalWars.plugin.getDataFolder(), "lang.yml");
						CrystalWars.plugin.gui = YamlConfiguration.loadConfiguration(CrystalWars.plugin.guiYml);
						break;
					default:
						MSG.sendHelp(sender, 0, args[0]);
						return true;
					}
				}
				CrystalWars.plugin.config = YamlConfiguration.loadConfiguration(CrystalWars.plugin.configYml);
				CrystalWars.plugin.gui = YamlConfiguration.loadConfiguration(CrystalWars.plugin.guiYml);
				CrystalWars.plugin.lang = YamlConfiguration.loadConfiguration(CrystalWars.plugin.langYml);
				MSG.tell(sender,
						MSG.getString("Reload", "Reload %file%").replace("%file%",
								args.length > 1
										? (args[1].equalsIgnoreCase("all") ? "All Files" : MSG.camelCase(args[1]))
										: "All Files"));
				new SBoard().scanWorlds();
				break;
			case "reset":
				if (args.length == 1) {
					for (String res : new String[] { "config", "guis", "lang" })
						CrystalWars.plugin.saveResource(res + ".yml", true);
					CrystalWars.plugin.configYml = new File(CrystalWars.plugin.getDataFolder(), "config.yml");
					CrystalWars.plugin.langYml = new File(CrystalWars.plugin.getDataFolder(), "lang.yml");
					CrystalWars.plugin.guiYml = new File(CrystalWars.plugin.getDataFolder(), "guis.yml");
				} else {
					switch (args[1].toLowerCase()) {
					case "all":
						for (String res : new String[] { "config", "guis", "lang" })
							CrystalWars.plugin.saveResource(res + ".yml", true);
						CrystalWars.plugin.configYml = new File(CrystalWars.plugin.getDataFolder(), "config.yml");
						CrystalWars.plugin.langYml = new File(CrystalWars.plugin.getDataFolder(), "lang.yml");
						CrystalWars.plugin.guiYml = new File(CrystalWars.plugin.getDataFolder(), "guis.yml");
						break;
					case "config":
						CrystalWars.plugin.saveResource("config.yml", true);
						CrystalWars.plugin.configYml = new File(CrystalWars.plugin.getDataFolder(), "config.yml");
						break;
					case "gui":
					case "guis":
						CrystalWars.plugin.saveResource("guis.yml", true);
						CrystalWars.plugin.guiYml = new File(CrystalWars.plugin.getDataFolder(), "guis.yml");
						break;
					case "lang":
						CrystalWars.plugin.saveResource("lang.yml", true);
						CrystalWars.plugin.langYml = new File(CrystalWars.plugin.getDataFolder(), "lang.yml");
						break;
					default:
						MSG.sendHelp(sender, 0, args[0]);
						return true;
					}
				}
				CrystalWars.plugin.config = YamlConfiguration.loadConfiguration(CrystalWars.plugin.configYml);
				CrystalWars.plugin.gui = YamlConfiguration.loadConfiguration(CrystalWars.plugin.guiYml);
				CrystalWars.plugin.lang = YamlConfiguration.loadConfiguration(CrystalWars.plugin.langYml);
				MSG.tell(sender,
						MSG.getString("Reset", "Reset %file%").replace("%file%",
								args.length > 1
										? (args[1].equalsIgnoreCase("all") ? "All Files" : MSG.camelCase(args[1]))
										: "All Files"));
				new SBoard().scanWorlds();
				break;
			default:
				try {
					int page = Integer.valueOf(args[0]);
					MSG.sendHelp(sender, page - 1, "default");
					break;
				} catch (Exception e) {
					MSG.tell(sender, MSG.getString("UnknownCommand", "Unknown Command"));
				}
				break;
			}
			break;
		default:
			return false;
		}
		CrystalWars.plugin.saveData();
		if (sender instanceof Player)
			pManager.removeInfo(((OfflinePlayer) sender), "prefix");
		return true;
	}

	boolean isPlayer(CommandSender sender) {
		if (!(sender instanceof Player)) {
			MSG.tell(sender, MSG.getString("MustBePlayer", "You must be a player"));
		}
		return (sender instanceof Player);
	}

	boolean teamExists(CommandSender sender, String team) {
		if (!isPlayer(sender))
			return false;
		if (!data.contains("Games." + ((Player) sender).getWorld().getName() + ".teams." + team)) {
			MSG.tell(sender, MSG.getString("Invalid.Team", "Team not found"));
		}
		return data.contains("Games." + ((Player) sender).getWorld().getName() + ".teams." + team);
	}

	boolean gameExists(Player player) {
		if (!data.contains("Games." + player.getWorld().getName())) {
			MSG.tell(player, MSG.getString("Invalid.Game", "There is no game in this world"));
			return false;
		}
		return true;
	}

	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		List<String> result = new ArrayList<String>();
		if (!sender.hasPermission("crystal.command"))
			return result;
		if (args.length == 1) {
			for (String res : new String[] { "create", "delete", "set", "start", "stop", "help", "list", "reload",
					"reset", "check", "stats", "resetStats" }) {
				if (sender.hasPermission("crystal." + res) && res.toLowerCase().startsWith(args[0].toLowerCase()))
					result.add(res);
			}
		}
		if (args.length > 1) {
			if (args.length == 3 && !args[1].equals("kit")) {
				if (sender instanceof Player) {
					if (!sender.hasPermission("crystal." + args[0]))
						return result;
					if (gManager.getTeams(((Player) sender).getWorld()) != null)
						for (String res : gManager.getTeams(((Player) sender).getWorld())) {
							if (res.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
								result.add(res);
						}
				}
			}
			if (args[1].matches("(?i)(kit|stat)") && sender instanceof Player) {
				Player player = (Player) sender;
				for (Player target : player.getWorld().getPlayers()) {
					if (target.getName().toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
						result.add(target.getName());
					}
				}
				if (CrystalWars.plugin.config.getConfigurationSection("Kits") != null)
					for (String res : CrystalWars.plugin.config.getConfigurationSection("Kits").getKeys(false)) {
						if (res.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
							result.add(res);
					}
			}
			if (args.length < 3)
				switch (args[0].toLowerCase()) {
				case "create":
				case "delete":
					for (String res : new String[] { "game", "team", "spawn", "leaderboard" })
						if (res.startsWith(args[1].toLowerCase()))
							result.add(res);
					break;
				case "set":
					for (String res : new String[] { "spawn", "maxTeamSize", "maxSize", "startAt", "resourceSpawn",
							"coalShop", "endShop", "teamShop", "endLocation", "crystal", "crystalHealth", "specSpawn",
							"time", "kit", "builders", "leaderboard", "stat" })
						if (res.toLowerCase().startsWith(args[1].toLowerCase()))
							result.add(res);
					break;
				case "help":
					for (String res : new String[] { "create", "delete", "set", "start", "stop", "help", "list",
							"reload", "reset" }) {
						if (res.startsWith(args[1].toLowerCase()))
							result.add(res);
					}
					break;
				case "reload":
				case "reset":
					for (String res : new String[] { "config", "lang", "guis", "all" })
						if (res.startsWith(args[1].toLowerCase()))
							result.add(res);
					break;
				}
		}
		return result;
	}
}