package xyz.msws.crystal.scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import xyz.msws.crystal.game.GameManager;
import xyz.msws.crystal.utils.MSG;
import xyz.msws.crystal.utils.PlayerManager;
import xyz.msws.crystal.utils.TimeManager;
import xyz.msws.crystal.CrystalWars;

public class SBoard {
	Scoreboard board;
	ConfigurationSection scoreboard = CrystalWars.plugin.config.getConfigurationSection("Scoreboard");
	PlayerManager pManager = new PlayerManager();
	TimeManager tManager = new TimeManager();
	GameManager gManager = new GameManager();

	double tick = 0;
	public HashMap<World, List<String>> inGame = new HashMap<>();

	List<String> lobby = flip(scoreboard.getStringList("Lobby")), sLines = new ArrayList<String>();

	/**
	 * Scans worlds and updates scoreboards
	 */
	public void scanWorlds() {
		scoreboard = CrystalWars.plugin.config.getConfigurationSection("Scoreboard");
		lobby = flip(scoreboard.getStringList("Lobby"));
		for (World world : Bukkit.getWorlds()) {
			List<String> inGame = null;
			if (gManager.getStatus(world).equals("disabled"))
				continue;
			if (scoreboard.contains(world.getName())) {
				inGame = scoreboard.getStringList(world.getName());
			} else {
				inGame = scoreboard.getStringList("default");
			}
			this.inGame.put(world, flip(inGame));
		}
	}

	public void register() {
		scanWorlds();
		new BukkitRunnable() {
			public void run() {
				for (World world : Bukkit.getWorlds()) {
					if (gManager.getStatus(world).equals("disabled"))
						continue;
					String status = "ingame";
					sLines = inGame.get(world);
					if (gManager.getStatus(world).equals("lobby") || gManager.getStatus(world).contains("countdown")) {
						sLines = lobby;
						status = "lobby";
					}
					if (sLines == null) {
						scanWorlds();
						continue;
					}
					if (gManager.getStatus(world).startsWith("countdown")
							&& Double.valueOf(gManager.getStatus(world).substring("countdown".length()))
									- System.currentTimeMillis() < 0) {
						gManager.startGame(world);
					}
					String name = scoreboard.getString("Title.InGame");
					for (Player player : world.getPlayers()) {
						List<String> lines = new ArrayList<String>();
						if (gManager.getStatus(world).equals("lobby")) {
							name = scoreboard.getString("Title.Lobby");
						} else if (gManager.getStatus(world).contains("countdown")) {
							name = scoreboard.getString("Title.Countdown");
						}
						board = player.getScoreboard();
						// Anti Lag/Flash Scoreboard functions
						if (board != null && player.getScoreboard().getObjective("crystalwars") != null
								&& pManager.getInfo(player, "oldBoard") != null
								&& pManager.getInfo(player, "oldBoard").equals(status)) {
							if (board.getObjectives().size() >= sLines.size())
								continue;
							Objective obj = board.getObjective("crystalwars");
							List<String> oldLines = pManager.getStringList(player, "oldLines");
							for (int i = 0; i < 15 && i < sLines.size() && i < oldLines.size(); i++) {
								lines.add(parse(player, sLines.get(i)));
								if (board.getEntries().contains(parse(player, sLines.get(i))))
									continue;
								board.resetScores(parse(player, oldLines.get(i)));
								obj.getScore(parse(player, sLines.get(i))).setScore(i + 1);
							}
							pManager.setInfo(player, "oldLines", lines);
							if (name.length() > 32) {
								name = name.substring((int) Math.round((tick / 2) % name.length()))
										+ name.substring(0, (int) (tick / 2) % name.length());
								name = name.substring(0, Math.min(30, name.length()));
							}
							obj.setDisplayName(MSG.color(parse(player, name)));
						} else {
							board = Bukkit.getScoreboardManager().getNewScoreboard();
							Objective obj = board.registerNewObjective("crystalwars", "dummy");
							player.setScoreboard(board);
							obj.setDisplaySlot(DisplaySlot.SIDEBAR);
							int pos = 1;
							for (String res : sLines) {
								obj.getScore(parse(player, res)).setScore(pos);
								lines.add(parse(player, res));
								if (pos >= 15 || pos >= sLines.size())
									break;
								pos++;
							}
							pManager.setInfo(player, "oldLines", lines);
							pManager.setInfo(player, "oldBoard", status);
						}
						if (board.getEntries().size() > sLines.size())
							refresh(player);
					}
					if (((String) gManager.getInfo(world, "status")).startsWith("countdown")) {
						double amo = Double.valueOf(gManager.getStatus(world).substring("countdown".length()))
								- System.currentTimeMillis();
						if (amo < 10000) {
							double cnt = System.currentTimeMillis() - (double) gManager.getInfo(world, "lastPling");
							if (cnt > 1000) {
								for (Player player : world.getPlayers())
									player.playSound(player.getLocation(),
											Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.Countdown")), 2, 2);
								gManager.setInfo(world, "lastPling", (double) System.currentTimeMillis());
							}
						}
					}
				}
				tick++;
			}
		}.runTaskTimer(CrystalWars.plugin, 0, 1);
	}

	private String parse(Player player, String entry) {
		String team = pManager.getTeam(player);
		String result = MSG.color(entry
				.replace("%player%", player.getName()).replace("%players%",
						player.getWorld().getPlayers().size() + "")
				.replace("%maxSize%", CrystalWars.plugin.data.getInt("Games." + player.getWorld().getName() + ".maxSize") + "")
				.replace("%teamName%", (team == null) ? "None" : MSG.camelCase(team))
				.replace("%startTime%",
						gManager.getStatus(player.getWorld()).startsWith("countdown") ? tManager.getRoundTimeMillis(
								Double.valueOf(gManager.getStatus(player.getWorld()).substring("countdown".length()))
										- System.currentTimeMillis() + 1000)
								: "")
				.replace("%alivePlayers%",
						gManager.alivePlayers(player.getWorld()) == null ? "0"
								: gManager.alivePlayers(player.getWorld()).size() + "")
				.replace("%world%", player.getWorld().getName()).replace("%kit%", pManager.getKit(player)));
		if (result == null)
			return result;
		if (team != null && gManager.getColor(player.getWorld(), team) != null) {
			result = result.replace("%teamColor%", gManager.getColor(player.getWorld(), team));
		} else {
			result = result.replace("%teamColor%", "");
		}
		if (gManager.getTeams(player.getWorld()) != null)
			result = result.replace("%aliveTeams%", gManager.getTeamsWithPlayers(player.getWorld()).size() + "");
		if (gManager.getStatus(player.getWorld()).startsWith("ingame")) {
			Double dur = System.currentTimeMillis()
					- Double.valueOf(gManager.getStatus(player.getWorld()).substring("ingame".length()));
			if (dur > 1000 * 60) {
				result = result.replace("%time%", gManager.getStatus(player.getWorld()).startsWith("ingame")
						? tManager.getTime(System.currentTimeMillis()
								- Double.valueOf(gManager.getStatus(player.getWorld()).substring("ingame".length())), 1)
						: "");
			} else {
				result = result.replace("%time%", gManager.getStatus(player.getWorld()).startsWith("ingame")
						? tManager.getRoundTimeMillis(System.currentTimeMillis()
								- Double.valueOf(gManager.getStatus(player.getWorld()).substring("ingame".length())))
						: "");
			}
		}
		// Parses to get team from "%team_[teamName]%"
		while (result.contains("%team_")) {
			int f = 0, pPos = 0;
			for (int pos = 0; pos < result.length() - "%team_".length(); pos++) {
				String tmp = "";
				for (int i = pos; i < pos + "%team_".length(); i++) {
					tmp = tmp + result.charAt(i);
				}
				if (tmp.equals("%team_")) {
					f = pos + "%team_".length();
					break;
				}
			}
			for (int i = f; i < result.length(); i++) {
				if (result.charAt(i) == '%') {
					pPos = i;
					break;
				}
			}
			String name = result.substring(f, pPos);
			result = result.replace("%team_" + name + "%",
					gManager.getColor(player.getWorld(), name) + "&l" + MSG.camelCase(name));
		}
		// Parses to get team from "%[teamName]_Status%]"
		while (result.contains("_Status%")) {
			int f = 0;
			for (int pos = 0; pos < result.length() - "_Status%".length() + 1; pos++) {
				String tmp = "";
				for (int i = pos; i < pos + "_Status%".length(); i++) {
					tmp = tmp + result.charAt(i);
				}
				if (tmp.equals("_Status%")) {
					f = pos;
					break;
				}
			}
			String name = "";
			for (int i = f - 1; i > 0; i--) {
				if (result.charAt(i) == '%')
					break;
				name = name + result.charAt(i);
			}
			String tmp = "";
			for (int i = name.length() - 1; i >= 0; i--)
				tmp = tmp + name.charAt(i);
			name = tmp;
			result = result.replace("%" + name + "_Status%",
					gManager.getColor(player.getWorld(), name) + gManager.getTeamStatus(player.getWorld(), name));
		}
		return MSG.color(result);
	}

	private List<String> flip(List<String> array) {
		List<String> result = new ArrayList<String>();
		for (int i = array.size() - 1; i >= 0; i--) {
			result.add(array.get(i));
		}
		return result;
	}

	private void refresh(Player player) {
		for (String res : board.getEntries()) {
			boolean keep = false;
			for (String line : sLines) {
				if (parse(player, res).equals(parse(player, line)))
					keep = true;
			}
			if (!keep)
				board.resetScores(res);
		}
	}
}