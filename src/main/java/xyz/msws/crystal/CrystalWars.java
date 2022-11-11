package xyz.msws.crystal;

import java.io.File;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.msws.crystal.commands.CrystalCommand;
import xyz.msws.crystal.commands.KitCommand;
import xyz.msws.crystal.commands.TeamCommand;
import xyz.msws.crystal.events.Events;
import xyz.msws.crystal.game.GameManager;
import xyz.msws.crystal.game.Timer;
import xyz.msws.crystal.scoreboard.SBoard;
import xyz.msws.crystal.utils.MSG;
import xyz.msws.crystal.utils.TimeManager;

public class CrystalWars extends JavaPlugin {
	public static CrystalWars plugin;

	// Data folders, lang will contain most messages
	public FileConfiguration config, data, lang, gui, stats;
	public File configYml = new File(getDataFolder(), "config.yml"), dataYml = new File(getDataFolder(), "data.yml"),
			langYml = new File(getDataFolder(), "lang.yml"), guiYml = new File(getDataFolder(), "guis.yml"),
			statsYml = new File(getDataFolder(), "stats.yml");

	// Basic utilities
	GameManager gManager;
	double startTime;
	TimeManager tManager = new TimeManager();

	/*
	 * Permissions: crystal.command - allows usage of /cw crystal.[command] - allows
	 * usage of /cw set, /cw create, etc crystal.kit.[kitName] crystal.sign - allows
	 * creation of CrystalWars signs crystal.sign.delete crystal.stats.others -
	 * allows viewing of other's stats More information at lang.yml
	 */

	public void onEnable() {
		plugin = this;
		startTime = System.currentTimeMillis();
		if (!configYml.exists())
			saveResource("config.yml", true);
		if (!langYml.exists())
			saveResource("lang.yml", true);
		if (!guiYml.exists())
			saveResource("guis.yml", true);
		config = YamlConfiguration.loadConfiguration(configYml);
		data = YamlConfiguration.loadConfiguration(dataYml);
		lang = YamlConfiguration.loadConfiguration(langYml);
		gui = YamlConfiguration.loadConfiguration(guiYml);
		stats = YamlConfiguration.loadConfiguration(statsYml);
		for (String section : new String[] { "Games" }) {
			if (data.getConfigurationSection(section) == null)
				data.createSection(section);
		}

		// Load game maps if they aren't loaded
		for (String res : data.getConfigurationSection("Games").getKeys(false))
			if (Bukkit.getWorld(res) == null) {
				MSG.log("Loading Game Map: " + res);
				World world = Bukkit.createWorld(WorldCreator.name(res).type(WorldType.FLAT));
				world.setGameRuleValue("doMobSpawning", "false");
				world.setGameRuleValue("doMobLoot", "false");
			}

		// Make arraylists in the config unique so the scoreboard doesn't break
		for (String section : config.getConfigurationSection("Scoreboard").getKeys(false)) {
			List<String> lines = config.getStringList("Scoreboard." + section);
			if (lines == null || lines.isEmpty())
				continue;
			for (int i = 0; i < lines.size(); i++) {
				String tmp = lines.get(i);
				lines.remove(i);
				while (lines.contains(tmp) || tmp.equals(""))
					tmp = tmp + " ";
				lines.add(i, tmp);
			}
			config.set("Scoreboard." + section, lines);
			saveConfig();
		}

		new CrystalCommand(); // Main command
		new TeamCommand();
		new KitCommand();
		new Events();
		new SBoard().register();
		new Timer().register();
		gManager = new GameManager(); // as GameManager references a class that isn't loaded, I load it after the
										// other classes have been loaded

		for (World world : Bukkit.getWorlds()) {
			if (data.contains("Games." + world.getName())) {
				gManager.assignTeams(world);
				gManager.setInfo(world, "status", "lobby");
			}
			gManager.refreshSigns(world);
		}

		MSG.log("&aSuccessfully Enabled! (Took " + tManager.getTime(System.currentTimeMillis() - startTime, 2) + ")");
	}

	public void onDisable() {
		CrystalWars.plugin.saveStats();
		if (gManager == null)
			return;
		// Stop games that are currently going
		for (World world : Bukkit.getWorlds()) {
			if (!data.contains("Games." + world.getName()))
				continue;
			if (!gManager.getStatus(world).equals("lobby")) {
				MSG.tell(world, MSG.getString("Game.Reload", "server is reloading"));
				gManager.stopGame(world);
			}
		}
		plugin = null;
	}

	public void saveData() {
		try {
			data.save(dataYml);
		} catch (Exception e) {
			MSG.log("&cError saving data file");
			MSG.log("&a----------Start of Stack Trace----------");
			e.printStackTrace();
			MSG.log("&a----------End of Stack Trace----------");
		}
	}

	public void saveConfig() {
		try {
			config.save(configYml);
		} catch (Exception e) {
			MSG.log("&cError saving config file");
			MSG.log("&a----------Start of Stack Trace----------");
			e.printStackTrace();
			MSG.log("&a----------End of Stack Trace----------");
		}
	}

	public void saveStats() {
		try {
			stats.save(statsYml);
		} catch (Exception e) {
			MSG.log("&cError saving stats file");
			MSG.log("&a----------Start of Stack Trace----------");
			e.printStackTrace();
			MSG.log("&a----------End of Stack Trace----------");
		}
	}

	public void saveLocation(String path, Location loc) {
		data.set(path + ".WORLD", loc.getWorld().getName());
		data.set(path + ".X", loc.getX());
		data.set(path + ".Y", loc.getY());
		data.set(path + ".Z", loc.getZ());
		data.set(path + ".YAW", loc.getYaw());
		data.set(path + ".PITCH", loc.getPitch());
	}

	public Location getLocation(String path) {
		if (!data.contains(path))
			return null;
		return new Location(Bukkit.getWorld(data.getString(path + ".WORLD")), data.getDouble(path + ".X"),
				data.getDouble(path + ".Y"), data.getDouble(path + ".Z"), data.getInt(path + ".YAW"),
				data.getInt(path + ".PITCH"));
	}
}
