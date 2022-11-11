package xyz.msws.crystal.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import xyz.msws.crystal.game.GameManager;
import xyz.msws.crystal.CrystalWars;

public class PlayerManager {

	public void setInfo(OfflinePlayer player, String id, Object data) {
		if (!isSaveable(data)) {
			int currentLine = Thread.currentThread().getStackTrace()[2].getLineNumber();

			String fromClass = new Exception().getStackTrace()[1].getClassName();
			if (fromClass.contains("."))
				fromClass = fromClass.split("\\.")[fromClass.split("\\.").length - 1];
			MSG.log("WARNING!!! SAVING ODD DATA FROM " + fromClass + ":" + currentLine);
		}
		CrystalWars.plugin.data.set(player.getUniqueId() + "." + id, data);
	}

	public void deleteInfo(OfflinePlayer player) {
		CrystalWars.plugin.data.set(player.getUniqueId() + "", null);
	}

	public void removeInfo(OfflinePlayer player, String id) {
		CrystalWars.plugin.data.set(player.getUniqueId() + "." + id, null);
	}

	public Object getInfo(OfflinePlayer player, String id) {
		return CrystalWars.plugin.data.get(player.getUniqueId() + "." + id);
	}

	public String getString(OfflinePlayer player, String id) {
		return CrystalWars.plugin.data.getString(player.getUniqueId() + "." + id);
	}

	public Double getDouble(OfflinePlayer player, String id) {
		return CrystalWars.plugin.data.getDouble(player.getUniqueId() + "." + id);
	}

	public Boolean getBoolean(OfflinePlayer player, String id) {
		return CrystalWars.plugin.data.getBoolean(player.getUniqueId() + "." + id);
	}

	public List<String> getStringList(OfflinePlayer player, String id) {
		return CrystalWars.plugin.data.getStringList(player.getUniqueId() + "." + id);
	}

	/**
	 * Returns a number that the player has, kills, deaths, etc. -1 if they don't
	 * have the stat
	 * 
	 * @param player
	 *            Player to get stat from
	 * @param stat
	 *            id of the stat to get, (kills, dmgDealt, dmgReceived, etc, see
	 *            guis.yml for more info)
	 * @return double of the stat, -1 if it doesn't exist
	 */
	public double getNumberStat(OfflinePlayer player, String stat) {
		double result = 0;
		try {
			result = CrystalWars.plugin.stats.getDouble(player.getUniqueId() + "." + stat);
		} catch (Exception e) {
			return -1;
		}
		return result;
	}

	/**
	 * Increments the player's stat by the specified amount, sets if to the amount
	 * if they don't have the stat
	 * 
	 * @param player
	 *            Player whose stat will be modified
	 * @param stat
	 *            Stat id to be modified
	 * @param amo
	 *            double amount of what to add/subtract
	 */
	public void increment(OfflinePlayer player, String stat, double amo) {
		if (!CrystalWars.plugin.stats.contains(player.getUniqueId() + "." + stat)) {
			CrystalWars.plugin.stats.set(player.getUniqueId() + "." + stat, amo);
		} else {
			CrystalWars.plugin.stats.set(player.getUniqueId() + "." + stat, getNumberStat(player, stat) + amo);
		}
	}

	/**
	 * Returns the string but with %(stat)% replace appropriately
	 * 
	 * @param player
	 *            Player to replace the stats with
	 * @param msg
	 *            String to replace with stats
	 * @return Returns msg but with stats replaced Eg: parseStats(offline, "test
	 *         %kills% %deaths%"); could return "test 13 20"
	 */
	public String parseStats(OfflinePlayer player, String msg) {
		String line = MSG.color(msg);
		if (line == null || line.isEmpty()) {
			return "";
		}
		for (String replace : new String[] { "blocksBroken", "wins", "losses", "plays", "quits", "kills", "finalKills",
				"finalDeaths", "deaths", "purchases", "crystalsDestroyed", "dmgDealt", "dmgReceived" }) {
			int stat = (int) getNumberStat(player, replace);
			line = line.replace("%" + replace + "%", stat + "");
		}
		if (getNumberStat(player, "deaths") == 0) {
			line = line.replace("%kdr%", parseDecimal(getNumberStat(player, "kills") + ""));
		} else {
			String kdr = (getNumberStat(player, "kills") / getNumberStat(player, "deaths") + "");
			line = line.replace("%kdr%", parseDecimal(kdr));
		}
		if (getNumberStat(player, "finalDeaths") == 0) {
			line = line.replace("%fkdr%", getNumberStat(player, "finalKills") + "");
		} else {
			String kdr = (getNumberStat(player, "finalKills") / getNumberStat(player, "finalDeaths") + "");
			line = line.replace("%fkdr%", parseDecimal(kdr));
		}
		if (getNumberStat(player, "dmgReceived") == 0) {
			line = line.replace("%dmgRatio%", getNumberStat(player, "dmgRatio") + "");
		} else {
			String kdr = (getNumberStat(player, "dmgDealt") / getNumberStat(player, "dmgReceived") + "");
			line = line.replace("%dmgRatio%", parseDecimal(kdr));
		}
		if (getNumberStat(player, "losses") == 0) {
			line = line.replace("%winRatio%", getNumberStat(player, "wins") + "");
		} else {
			String kdr = (getNumberStat(player, "wins") / getNumberStat(player, "losses") + "");
			line = line.replace("%winRatio%", parseDecimal(kdr));
		}
		line = line.replace("%time%", new TimeManager().getTime(getNumberStat(player, "playtime"), 2));
		return line;
	}

	public String parseDecimal(String name) {
		if (name.contains(".")) {
			if (name.split("\\.")[1].length() > 2) {
				name = name.split("\\.")[0] + "."
						+ name.split("\\.")[1].substring(0, Math.min(name.split("\\.")[1].length(), 2));
			}
		}
		return name;
	}

	public void setStat(OfflinePlayer player, String stat, double amo) {
		CrystalWars.plugin.stats.set(player.getUniqueId() + "." + stat, amo);
	}

	public void removeStat(OfflinePlayer player, String stat) {
		CrystalWars.plugin.stats.set(player.getUniqueId() + "." + stat, null);
	}

	public List<String> getStats(OfflinePlayer player) {
		List<String> stats = new ArrayList<String>();
		ConfigurationSection statEntries = CrystalWars.plugin.stats.getConfigurationSection(player.getUniqueId() + "");
		if (statEntries == null)
			return stats;
		statEntries.getKeys(false).forEach((stat) -> stats.add(stat));
		return stats;
	}

	/**
	 * Returns List of OfflinePlayers in order of highest score of the stat to
	 * lowest (size is defined)
	 * 
	 * @param stat
	 *            Stat to order players by
	 * @param amo
	 *            Size of the array
	 * @return List of offlineplayers within that array, may be empty if no users
	 *         exist
	 */
	public List<OfflinePlayer> getHighestRanks(String stat, int amo) {
		List<OfflinePlayer> top = new ArrayList<OfflinePlayer>();
		for (int i = 0; i < amo; i++) {
			double cStat = 0.0;
			String cPlayer = null;
			for (String res : CrystalWars.plugin.stats.getKeys(false)) {
				OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(res));
				if (top.contains(target))
					continue;
				double tmp = CrystalWars.plugin.stats.getDouble(res + "." + stat);
				if (tmp >= cStat) {
					cStat = tmp;
					cPlayer = res;
				}
			}
			if (cPlayer == null)
				return top;
			OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(cPlayer));
			if (top.contains(target))
				return top;
			top.add(target);
		}
		return top;
	}

	/**
	 * Kills the player if they're ingame and have a team
	 * 
	 * @param player
	 *            Player to kill
	 * @param killer
	 *            String of who killed them (Can be "Void Damage" or other)
	 */
	@SuppressWarnings("deprecation")
	public void killPlayer(Player player, String killer) {
		GameManager gManager = new GameManager();
		String team = getTeam(player);
		if (team == null)
			return;
		if (!isAlive(player))
			return;
		World world = player.getWorld();
		for (Player target : player.getWorld().getPlayers())
			target.hidePlayer(player);
		increment(player, "deaths", 1);
		setInfo(player, "killer", null);
		player.setHealth(20);
		player.teleport(CrystalWars.plugin.getLocation("Games." + player.getWorld().getName() + ".specSpawn"));
		player.setAllowFlight(true);
		player.setFlying(true);
		String vName = gManager.getColor(world, team) + player.getName();
		if (CrystalWars.plugin.data.getDouble("Games." + world.getName() + ".teams." + team + ".crystalHealth") <= 0) {
			CrystalWars.plugin.data.set(
					"Games." + world.getName() + ".teams." + team + ".members." + player.getName() + ".alive", false);
			player.sendTitle(MSG.color(MSG.getString("Game.PlayerKilled.Top", "&c&lYOU DIED!")),
					MSG.color(MSG.getString("Game.PlayerKilled.Bottom", "")));
			MSG.tell(player.getWorld(),
					MSG.color(MSG.getString("Game.PlayerKilled.Message", "%culprit% killed %victim%")
							.replace("%culprit%", killer).replace("%victim%", vName))
							.replace("%prefix%", MSG.color(MSG.prefix())));
			increment(player, "finalDeaths", 1);
			if (player.getKiller() != null) {
				increment(player.getKiller(), "finalKills", 1);
				player.getKiller().playSound(player.getLocation(),
						Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.Kill")), 2, 2);
			}
		} else {
			if (player.getInventory().contains(Material.BLAZE_ROD)) {
				player.getInventory().remove(Material.BLAZE_ROD);
				MSG.tell(player, MSG.getString("Shop.InvSaved", "Your inventory was saved."));
				for (int i = 0; i < player.getInventory().getSize(); i++) {
					ItemStack item = player.getInventory().getItem(i);
					if (item == null)
						continue;
					CrystalWars.plugin.data.set("SavedInventories." + player.getName() + "." + i, item);
				}
				CrystalWars.plugin.data.set("SavedInventories." + player.getName() + ".armor",
						player.getInventory().getArmorContents());
			}
			MSG.tell(player,
					MSG.getString("Game.PlayerKilled.KilledBy", "%culprit% killed %victim%")
							.replace("%culprit%", killer).replace("%victim%", player.getName())
							.replace("%prefix%", MSG.color(MSG.prefix())));
			if (player.getKiller() != null) {
				MSG.tell(player.getKiller(),
						MSG.color(MSG.getString("Game.PlayerKilled.Private", "%culprit% killed %victim%")
								.replace("%culprit%", killer).replace("%victim%", vName))
								.replace("%prefix%", MSG.color(MSG.prefix())));
				increment(player.getKiller(), "kills", 1);
			}
		}
		gManager.updateNames(world);
		player.getInventory().clear();
		player.getInventory().setArmorContents(null);
		if (CrystalWars.plugin.data.getDouble("Games." + world.getName() + ".teams." + team + ".crystalHealth") > 0) {
			setInfo(player, "diedAt", (double) System.currentTimeMillis());
		}

	}

	public void spawnPlayer(Player player) {
		GameManager gManager = new GameManager();
		World world = player.getWorld();
		String team = getTeam(player);
		int size = 0, pos = 0;
		setInfo(player, "purchases", null);
		while (CrystalWars.plugin.data.contains("Games." + world.getName() + ".teams." + team + ".spawnPoint" + size))
			size++;
		if (size == 0) {
			gManager.crashGame(world, "No spawns set for " + team + " team");
			return;
		}
		for (Player target : world.getPlayers())
			target.showPlayer(player);
		setInfo(player, "spawnedAt", (double) System.currentTimeMillis());
		player.getInventory().clear();
		if (CrystalWars.plugin.data.contains("SavedInventories." + player.getName())) {
			for (String res : CrystalWars.plugin.data.getConfigurationSection("SavedInventories." + player.getName())
					.getKeys(false)) {
				if (res.equals("armor")) {
					player.getInventory().setArmorContents(
							(ItemStack[]) CrystalWars.plugin.data.get("SavedInventories." + player.getName() + ".armor"));
					continue;
				}
				int slot = Integer.parseInt(res);
				player.getInventory().setItem(slot,
						CrystalWars.plugin.data.getItemStack("SavedInventories." + player.getName() + "." + res));
			}
			CrystalWars.plugin.data.set("SavedInventories." + player.getName(), null);
		} else {
			ItemStack[] armor = new ItemStack[4];
			pos = 3;
			for (Material mat : new Material[] { Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
					Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS }) {
				ItemStack item = new ItemStack(mat);
				LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
				meta.setColor(MSG.colorToColor(gManager.getColor(world, team)));
				meta.setDisplayName(MSG.color(gManager.getColor(world, team) + "&l" + MSG.camelCase(team) + " Armor"));
//				meta.spigot().setUnbreakable(true);
				meta.setUnbreakable(true);
				item.setItemMeta(meta);
				armor[pos] = item;
				pos--;
			}
			player.getInventory().setArmorContents(armor);
			ConfigurationSection kits = CrystalWars.plugin.config.getConfigurationSection("Kits");
			String kit = getKit(player);
			if (kits.contains(kit)) {
				for (String res : kits.getConfigurationSection(kit).getKeys(false)) {
					if (res.equalsIgnoreCase("receive") || !kits.contains(kit + "." + res + ".Icon")) {
						continue;
					}
					player.getInventory().addItem(parseItem(CrystalWars.plugin.config, "Kits." + kit + "." + res, player));
				}
			}
		}

		player.setGameMode(GameMode.SURVIVAL);
		player.setFlying(false);
		player.setAllowFlight(false);
		player.setFallDistance(0);
		pos = 0;
		for (Player target : gManager.getTeamMembers(world, team)) {
			if (target == player)
				target.teleport(
						CrystalWars.plugin.getLocation("Games." + world.getName() + ".teams." + team + ".spawnPoint" + pos));
			pos = (pos + 1) % size;
		}
	}

	public String getTeam(Player player) {
		if (new GameManager().getTeams(player.getWorld()) == null)
			return null;
		for (String res : new GameManager().getTeams(player.getWorld())) {
			if (CrystalWars.plugin.data.contains(
					"Games." + player.getWorld().getName() + ".teams." + res + ".members." + player.getName()))
				return res;
		}
		return null;
	}

	public String getKit(Player player) {
		if (getInfo(player, "kit") != null) {
			return getString(player, "kit");
		}
		return "None";
	}

	public Inventory getGameSelectorGui() {
		GameManager gManager = new GameManager();
		Inventory inv = Bukkit.createInventory(null, 54, "Game Selection");
		int pos = 0;
		for (String world : CrystalWars.plugin.data.getConfigurationSection("Games").getKeys(false)) {
			ItemStack icon = new ItemStack(Material.DIAMOND_BLOCK);
			String name = world;
			List<String> status = new ArrayList<String>(), newList = new ArrayList<String>();
			World tmp = Bukkit.getWorld(world);
			ConfigurationSection section = CrystalWars.plugin.gui.getConfigurationSection("gameSelectionGui.Status");
			if (tmp == null) {
				icon.setType(Material.BEDROCK);
				status.add(section.getString("Offline"));
			} else if (gManager.getStatus(tmp).contains("ingame")) {
				status.add(section.getString("InGame").replace("%players%", gManager.alivePlayers(tmp).size() + ""));
			} else if (gManager.getStatus(tmp).contains("countdown")) {
				String time = new TimeManager()
						.getRoundTimeMillis(Double.valueOf(gManager.getStatus(tmp).substring("countdown".length()))
								- System.currentTimeMillis() + 1000);
				status.add(section.getString("Countdown").replace("%time%", time));
			} else {
				status.add(section.getString("Waiting").replace("%players%", tmp.getPlayers().size() + "")
						.replace("%max%", CrystalWars.plugin.data.getInt("Games." + world + ".maxSize") + ""));
			}
			status.forEach((line) -> newList.add(MSG.color(line)));
			ItemMeta meta = icon.getItemMeta();
			meta.setLore(newList);
			meta.setDisplayName(MSG.color("&e&l" + name));
			icon.setItemMeta(meta);
			inv.setItem(pos, icon);
			pos++;
		}
		return inv;
	}

	public int getKills(Player player) {
		if (player == null || getInfo(player, "kills") == null)
			return 0;
		return (int) Math.round(getDouble(player, "kills"));
	}

	public boolean isAlive(Player player) {
		return CrystalWars.plugin.data.getBoolean("Games." + player.getWorld().getName() + ".teams." + getTeam(player)
				+ ".members." + player.getName() + ".alive");
	}

	public boolean isRespawning(Player player) {
		return getInfo(player, "diedAt") != null;
	}

	public String getKiller(Player player) {
		return getString(player, "killer") == null ? "Unknown" : getString(player, "killer");
	}

	public Inventory getGui(OfflinePlayer player, String id) {
		if (!CrystalWars.plugin.gui.contains(id))
			return null;
		ConfigurationSection gui = CrystalWars.plugin.gui.getConfigurationSection(id);
		if (!gui.contains("Size") || !gui.contains("Title"))
			return null;
		Inventory inv = Bukkit.createInventory(null, gui.getInt("Size"),
				MSG.color(gui.getString("Title").replace("%player%", player.getName())));
		ItemStack bg = null;
		boolean empty = true;
		for (String res : gui.getKeys(false)) {
			if (!gui.contains(res + ".Icon"))
				continue;
			empty = false;
			if (player.isOnline()) {
				if (gui.contains(res + ".Permission")
						&& !((Player) player).hasPermission(gui.getString(res + ".Permission"))) {
					continue;
				}
			}
			ItemStack item = parseItem(CrystalWars.plugin.gui, id + "." + res, player);
			if (res.equals("BACKGROUND_ITEM")) {
				bg = item;
				continue;
			}
			int slot = 0;
			if (!gui.contains(res + ".Slot")) {
				while (inv.getItem(slot) != null)
					slot++;
				inv.setItem(slot, item);
			} else {
				inv.setItem(gui.getInt(res + ".Slot"), item);
			}
		}
		if (empty)
			return null;
		if (bg != null) {
			for (int i = 0; i < inv.getSize(); i++) {
				if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
					inv.setItem(i, bg);
				}
			}
		}
		return inv;
	}

	public ItemStack parseItem(ConfigurationSection section, String path, OfflinePlayer player) {
		ConfigurationSection gui = section.getConfigurationSection(path);
		ItemStack item = new ItemStack(Material.valueOf(gui.getString("Icon")));
		List<String> lore = new ArrayList<String>();
		if (gui.contains("Amount"))
			item.setAmount(gui.getInt("Amount"));
		if (gui.contains("Data"))
			item.setDurability((short) gui.getInt("Data"));
		if (gui.contains("SetDataTo") && player.isOnline()) {
			if (getTeam((Player) player) != null) {
				String color = new GameManager().getColor(((Player) player).getWorld(), getTeam((Player) player));
				item.setDurability(MSG.colorToByte(color).shortValue());
			}
		}
		ItemMeta meta = item.getItemMeta();
		if (gui.contains("Name"))
			meta.setDisplayName(MSG.color("&r" + parseStats(player, gui.getString("Name"))));
		if (gui.contains("Lore")) {
			for (String temp : gui.getStringList("Lore"))
				lore.add(MSG.color("&r" + parseStats(player, temp)));
		}
		if (gui.getBoolean("Unbreakable")) {
//			meta.spigot().setUnbreakable(true);
			meta.setUnbreakable(true);
			meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
		}
		if (gui.contains("Cost")) {
			HashMap<Material, Integer> mats = new HashMap<>();
			ConfigurationSection costs = gui.getConfigurationSection("Cost");
			for (String material : costs.getKeys(false))
				mats.put(Material.valueOf(material), costs.getInt(material));
			lore.add("");
			if (mats.size() == 1) {
				lore.add(MSG.color("&aCost: &c" + mats.values().toArray()[0] + " "
						+ MSG.camelCase(mats.keySet().toArray()[0] + "")));
			} else {
				lore.add(MSG.color("&aCost:"));
				for (Material mat : mats.keySet()) {
					lore.add(MSG.color("&c* " + mats.get(mat) + " "
							+ MSG.camelCase(mat.name() + (mats.get(mat) == 1 ? "" : "s"))));
				}
			}
		}
		if (gui.contains("Enchantments")) {
			ConfigurationSection enchs = gui.getConfigurationSection("Enchantments");
			for (String enchant : enchs.getKeys(false)) {
				int level = 1;
				if (enchs.contains(enchant + ".Level"))
					level = enchs.getInt(enchant + ".Level");
				if (enchs.contains(enchant + ".Visible") && !enchs.getBoolean(enchant + ".Visible"))
					meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
				item.setItemMeta(meta);
				item.addUnsafeEnchantment(Enchantment.getByName(enchant.toUpperCase()), level);
				meta = item.getItemMeta();
			}
		}
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * Get whether an object is saveable in YAML
	 * 
	 * @param obj
	 *            Object type to test
	 * @return True if saveable, false otherwise
	 */
	public boolean isSaveable(Object obj) {
		return (obj instanceof String || obj instanceof Integer || obj instanceof ArrayList || obj instanceof Boolean
				|| obj == null || obj instanceof Double || obj instanceof Short || obj instanceof Long
				|| obj instanceof Character);
	}
}
