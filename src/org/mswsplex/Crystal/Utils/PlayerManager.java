package org.mswsplex.Crystal.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.mswsplex.Crystal.Game.GameManager;
import org.mswsplex.MSWS.Crystal.Main;

public class PlayerManager {

	public void setInfo(OfflinePlayer player, String id, Object data) {
		if (!isSaveable(data)) {
			int currentLine = Thread.currentThread().getStackTrace()[2].getLineNumber();

			String fromClass = new Exception().getStackTrace()[1].getClassName();
			if (fromClass.contains("."))
				fromClass = fromClass.split("\\.")[fromClass.split("\\.").length - 1];
			MSG.log("WARNING!!! SAVING ODD DATA FROM " + fromClass + ":" + currentLine);
		}
		Main.plugin.data.set(player.getUniqueId() + "." + id, data);
	}

	public void deleteInfo(OfflinePlayer player) {
		Main.plugin.data.set(player.getUniqueId() + "", null);
	}

	public void removeInfo(OfflinePlayer player, String id) {
		Main.plugin.data.set(player.getUniqueId() + "." + id, null);
	}

	public Object getInfo(OfflinePlayer player, String id) {
		return Main.plugin.data.get(player.getUniqueId() + "." + id);
	}

	public String getString(OfflinePlayer player, String id) {
		return Main.plugin.data.getString(player.getUniqueId() + "." + id);
	}

	public Double getDouble(OfflinePlayer player, String id) {
		return Main.plugin.data.getDouble(player.getUniqueId() + "." + id);
	}

	public Boolean getBoolean(OfflinePlayer player, String id) {
		return Main.plugin.data.getBoolean(player.getUniqueId() + "." + id);
	}

	public List<String> getStringList(OfflinePlayer player, String id) {
		return Main.plugin.data.getStringList(player.getUniqueId() + "." + id);
	}

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
		setInfo(player, "killer", null);
		player.setHealth(20);
		player.teleport(Main.plugin.getLocation("Games." + player.getWorld().getName() + ".specSpawn"));
		player.setAllowFlight(true);
		player.setFlying(true);
		String vName = gManager.getColor(world, team) + player.getName();
		if (Main.plugin.data.getDouble("Games." + world.getName() + ".teams." + team + ".crystalHealth") <= 0) {
			Main.plugin.data.set(
					"Games." + world.getName() + ".teams." + team + ".members." + player.getName() + ".alive", false);
			player.sendTitle(MSG.color(MSG.getString("Game.PlayerKilled.Top", "&c&lYOU DIED!")),
					MSG.color(MSG.getString("Game.PlayerKilled.Bottom", "")));
			MSG.tell(player.getWorld(),
					MSG.color(MSG.getString("Game.PlayerKilled.Message", "%culprit% killed %victim%")
							.replace("%culprit%", killer).replace("%victim%", vName))
							.replace("%prefix%", MSG.color(MSG.prefix())));
		} else {
			if (player.getInventory().contains(Material.BLAZE_ROD)) {
				player.getInventory().remove(Material.BLAZE_ROD);
				MSG.tell(player, MSG.getString("Shop.InvSaved", "Your inventory was saved."));
				for (int i = 0; i < player.getInventory().getSize(); i++) {
					ItemStack item = player.getInventory().getItem(i);
					if (item == null)
						continue;
					Main.plugin.data.set("SavedInventories." + player.getName() + "." + i, item);
				}
				Main.plugin.data.set("SavedInventories." + player.getName() + ".armor",
						player.getInventory().getArmorContents());
			}
			MSG.tell(player,
					MSG.getString("Game.PlayerKilled.KilledBy", "%culprit% killed %victim%")
							.replace("%culprit%", killer).replace("%victim%", player.getName())
							.replace("%prefix%", MSG.color(MSG.prefix())));
			if (player.getKiller() != null)
				MSG.tell(player.getKiller(),
						MSG.color(MSG.getString("Game.PlayerKilled.Private", "%culprit% killed %victim%")
								.replace("%culprit%", killer).replace("%victim%", vName))
								.replace("%prefix%", MSG.color(MSG.prefix())));
		}
		gManager.updateNames(world);
		player.getInventory().clear();
		player.getInventory().setArmorContents(null);
		if (Main.plugin.data.getDouble("Games." + world.getName() + ".teams." + team + ".crystalHealth") > 0) {
			setInfo(player, "diedAt", (double) System.currentTimeMillis());
		}

	}

	public void spawnPlayer(Player player) {
		GameManager gManager = new GameManager();
		World world = player.getWorld();
		String team = getTeam(player);
		int size = 0, pos = 0;
		setInfo(player, "purchases", null);
		while (Main.plugin.data.contains("Games." + world.getName() + ".teams." + team + ".spawnPoint" + size))
			size++;
		if (size == 0) {
			gManager.crashGame(world, "No spawns set for " + team + " team");
			return;
		}
		for (Player target : world.getPlayers())
			target.showPlayer(player);
		setInfo(player, "spawnedAt", (double) System.currentTimeMillis());
		player.getInventory().clear();
		if (Main.plugin.data.contains("SavedInventories." + player.getName())) {
			for (String res : Main.plugin.data.getConfigurationSection("SavedInventories." + player.getName())
					.getKeys(false)) {
				if (res.equals("armor")) {
					player.getInventory().setArmorContents(
							(ItemStack[]) Main.plugin.data.get("SavedInventories." + player.getName() + ".armor"));
					continue;
				}
				int slot = Integer.parseInt(res);
				player.getInventory().setItem(slot,
						Main.plugin.data.getItemStack("SavedInventories." + player.getName() + "." + res));
			}
			Main.plugin.data.set("SavedInventories." + player.getName(), null);
		} else {
			ItemStack[] armor = new ItemStack[4];
			pos = 3;
			for (Material mat : new Material[] { Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
					Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS }) {
				ItemStack item = new ItemStack(mat);
				LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
				meta.setColor(MSG.colorToColor(gManager.getColor(world, team)));
				meta.setDisplayName(MSG.color(gManager.getColor(world, team) + "&l" + MSG.camelCase(team) + " Armor"));
				meta.spigot().setUnbreakable(true);
				item.setItemMeta(meta);
				armor[pos] = item;
				pos--;
			}
			player.getInventory().setArmorContents(armor);
			ConfigurationSection kits = Main.plugin.config.getConfigurationSection("Kits");
			String kit = getKit(player);
			if (kits.contains(kit)) {
				for (String res : kits.getConfigurationSection(kit).getKeys(false)) {
					if (res.equalsIgnoreCase("receive") || !kits.contains(kit + "." + res + ".Icon")) {
						continue;
					}
					player.getInventory().addItem(parseItem(Main.plugin.config, "Kits." + kit + "." + res, player));
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
						Main.plugin.getLocation("Games." + world.getName() + ".teams." + team + ".spawnPoint" + pos));
			pos = (pos + 1) % size;
		}
	}

	public String getTeam(Player player) {
		if (new GameManager().getTeams(player.getWorld()) == null)
			return null;
		for (String res : new GameManager().getTeams(player.getWorld())) {
			if (Main.plugin.data.contains(
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
		for (String world : Main.plugin.data.getConfigurationSection("Games").getKeys(false)) {
			ItemStack icon = new ItemStack(Material.DIAMOND_BLOCK);
			String name = world;
			List<String> status = new ArrayList<String>(), newList = new ArrayList<String>();
			World tmp = Bukkit.getWorld(world);
			ConfigurationSection section = Main.plugin.gui.getConfigurationSection("gameSelectionGui.Status");
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
						.replace("%max%", Main.plugin.data.getInt("Games." + world + ".maxSize") + ""));
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
		return Main.plugin.data.getBoolean("Games." + player.getWorld().getName() + ".teams." + getTeam(player)
				+ ".members." + player.getName() + ".alive");
	}

	public boolean isRespawning(Player player) {
		return getInfo(player, "diedAt") != null;
	}

	public String getKiller(Player player) {
		return getString(player, "killer") == null ? "Unknown" : getString(player, "killer");
	}

	public Inventory getGui(Player player, String id) {
		if (!Main.plugin.gui.contains(id))
			return null;
		ConfigurationSection gui = Main.plugin.gui.getConfigurationSection(id);
		if (!gui.contains("Size") || !gui.contains("Title"))
			return null;
		Inventory inv = Bukkit.createInventory(null, gui.getInt("Size"), gui.getString("Title"));
		ItemStack bg = null;
		boolean empty = true;
		for (String res : gui.getKeys(false)) {
			if (!gui.contains(res + ".Icon"))
				continue;
			empty = false;
			if (gui.contains(res + ".Permission") && !player.hasPermission(gui.getString(res + ".Permission"))) {
				continue;
			}
			ItemStack item = parseItem(Main.plugin.gui, id + "." + res, player);
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

	public ItemStack parseItem(ConfigurationSection section, String path, Player player) {
		ConfigurationSection gui = section.getConfigurationSection(path);
		ItemStack item = new ItemStack(Material.valueOf(gui.getString("Icon")));
		List<String> lore = new ArrayList<String>();
		if (gui.contains("Amount"))
			item.setAmount(gui.getInt("Amount"));
		if (gui.contains("Data"))
			item.setDurability((short) gui.getInt("Data"));
		if (gui.contains("SetDataTo")) {
			if (getTeam(player) != null) {
				String color = new GameManager().getColor(player.getWorld(), getTeam(player));
				item.setDurability(MSG.colorToByte(color).shortValue());
			}
		}
		ItemMeta meta = item.getItemMeta();
		if (gui.contains("Name"))
			meta.setDisplayName(MSG.color("&r" + gui.getString("Name")));
		if (gui.contains("Lore")) {
			for (String temp : gui.getStringList("Lore"))
				lore.add(MSG.color("&r" + temp));
		}
		if (gui.getBoolean("Unbreakable")) {
			meta.spigot().setUnbreakable(true);
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

	public boolean isSaveable(Object obj) {
		return (obj instanceof String || obj instanceof Integer || obj instanceof ArrayList || obj instanceof Boolean
				|| obj == null || obj instanceof Double || obj instanceof Short || obj instanceof Long
				|| obj instanceof Character);
	}
}
