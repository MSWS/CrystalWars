package xyz.msws.crystal.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xyz.msws.crystal.CrystalWars;

public class MSG {
	public static String color(String msg) {
		if (msg == null || msg.isEmpty())
			return null;
		return ChatColor.translateAlternateColorCodes('&', msg);
	}

	public static String camelCase(String string) {
		String prevChar = " ";
		String res = "";
		for (int i = 0; i < string.length(); i++) {
			if (i > 0)
				prevChar = string.charAt(i - 1) + "";
			if (!prevChar.matches("[a-zA-Z]")) {
				res = res + ((string.charAt(i) + "").toUpperCase());
			} else {
				res = res + ((string.charAt(i) + "").toLowerCase());
			}
		}
		return res.replace("_", " ");
	}

	public static void nonSpam(Player player, String msg) {
		PlayerManager pManager = new PlayerManager();
		if (pManager.getDouble(player, msg + "time") == null) {
			tell(player, msg);
			pManager.setInfo(player, msg + "time", (double) System.currentTimeMillis());
			return;
		}
		if (System.currentTimeMillis() - pManager.getDouble(player, msg + "time") > 2000) {
			tell(player, msg);
			pManager.setInfo(player, msg + "time", (double) System.currentTimeMillis());
			return;
		}
	}

	public static void nonSpam(Player player, String msg, double delay) {
		PlayerManager pManager = new PlayerManager();
		if (pManager.getDouble(player, msg) == null) {
			tell(player, msg);
			pManager.setInfo(player, msg, (double) System.currentTimeMillis());
			return;
		}
		if (System.currentTimeMillis() - pManager.getDouble(player, msg) > delay) {
			tell(player, msg);
			pManager.setInfo(player, msg, (double) System.currentTimeMillis());
			return;
		}
	}

	public static String getString(String id, String def) {
		return CrystalWars.plugin.lang.contains(id) ? CrystalWars.plugin.lang.getString(id) : "[" + id + "] " + def;
	}

	public static void tell(CommandSender sender, String msg) {
		if (msg != null)
			sender.sendMessage(color(msg.replace("%prefix%", prefix())));
	}

	public static void tell(World world, String msg) {
		if (world != null && msg != null) {
			for (Player target : world.getPlayers()) {
				tell(target, msg);
			}
		}
	}

	public static String prefix() {
		return CrystalWars.plugin.config.contains("Prefix") ? CrystalWars.plugin.config.getString("Prefix") : "CrystalWars:";
	}

	public static void noPerm(CommandSender sender) {
		tell(sender, getString("NoPermission", "Insufficient Permissions"));
	}

	public static void log(String msg) {
		int currentLine = Thread.currentThread().getStackTrace()[2].getLineNumber();

		String fromClass = new Exception().getStackTrace()[1].getClassName();
		if (fromClass.contains("."))
			fromClass = fromClass.split("\\.")[fromClass.split("\\.").length - 1];
		String prefix = "";
		if (CrystalWars.plugin.config.getBoolean("DetailedLogs")) {
			prefix = "[" + CrystalWars.plugin.getDescription().getName() + "|" + fromClass + ":" + currentLine + "] ";
		} else {
			prefix = "[" + CrystalWars.plugin.getDescription().getName() + "] ";
		}
		tell(Bukkit.getConsoleSender(), prefix + msg);
	}

	public static void sendHelp(CommandSender sender, int page, String command) {
		PlayerManager pManager = new PlayerManager();
		if (!CrystalWars.plugin.lang.contains("Help." + command.toLowerCase())) {
			tell(sender, getString("UnknownCommand", "There is no help available for this command."));
			return;
		}
		int length = CrystalWars.plugin.config.getInt("HelpLength");
		List<String> help = CrystalWars.plugin.lang.getStringList("Help." + command.toLowerCase()),
				list = new ArrayList<String>();
		for (String res : help) {
			if (res.startsWith("perm:")) {
				String perm = "";
				res = res.substring(5, res.length());
				for (char a : res.toCharArray()) {
					if (a == ' ')
						break;
					perm = perm + a;
				}
				if (!sender.hasPermission("crystal." + perm))
					continue;
				res = res.replace(perm + " ", "");
			}
			String prefix = "/crystal";
			if (sender instanceof Player)
				prefix = pManager.getString(((OfflinePlayer) sender), "prefix");
			list.add(res.replace("%prefix%", prefix));
		}
		if (help.size() > length)
			tell(sender, "Page: " + (page + 1) + " of " + (int) Math.ceil((list.size() / length) + 1));
		for (int i = page * length; i < list.size() && i < page * length + length; i++) {
			String res = list.get(i);
			tell(sender, res);
		}
		if (command.equals("default"))
			tell(sender, "&4&lCrystalWars &ev" + CrystalWars.plugin.getDescription().getVersion() + " &7created by &aMSWS");
	}

	public static String progressBar(double prog, double total, int length) {
		return progressBar("&a\u258D", "&c\u258D", prog, total, length);
	}

	public static String progressBar(String progChar, String incomplete, double prog, double total, int length) {
		String disp = "";
		double progress = Math.abs(prog / total);
		int len = length;
		for (double i = 0; i < len; i++) {
			if (i / len < progress) {
				disp = disp + progChar;
			} else {
				disp = disp + incomplete;
			}
		}
		return color(disp);
	}

	public static Integer colorToByte(String msg) {
		if (msg == null)
			return null;
		String tmp = msg.trim();
		tmp = tmp.substring(tmp.length() - 1);
		switch (tmp.toLowerCase()) {
		case "f":
		case "r":
			return 0;
		case "6":
			return 1;
		case "b":
			return 3;
		case "e":
			return 4;
		case "a":
			return 5;
		case "d":
			return 6;
		case "8":
			return 7;
		case "7":
			return 8;
		case "3":
			return 9;
		case "5":
			return 10;
		case "9":
		case "1":
			return 11;
		case "2":
			return 13;
		case "4":
		case "c":
			return 14;
		case "0":
			return 15;
		}
		return null;
	}

	public static Color colorToColor(String color) {
		String tmp = color.trim();
		tmp = tmp.substring(tmp.length() - 1);
		switch (tmp.toLowerCase()) {
		case "f":
		case "r":
			return Color.fromRGB(255, 255, 255);
		case "6":
			return Color.fromRGB(253, 165, 15);
		case "b":
			return Color.fromRGB(135, 206, 250);
		case "e":
			return Color.fromRGB(255, 244, 79);
		case "a":
			return Color.fromRGB(100, 248, 100);
		case "d":
			return Color.fromRGB(255, 192, 203);
		case "8":
			return Color.fromRGB(105, 105, 105);
		case "7":
			return Color.fromRGB(190, 190, 190);
		case "3":
			return Color.fromRGB(0, 206, 209);
		case "5":
			return Color.fromRGB(138, 43, 226);
		case "9":
			return Color.fromRGB(100, 149, 237);
		case "1":
			return Color.fromRGB(0, 0, 205);
		case "2":
			return Color.fromRGB(60, 179, 113);
		case "4":
			return Color.fromRGB(178, 43, 43);
		case "c":
			return Color.fromRGB(235, 82, 82);
		case "0":
			return Color.fromRGB(0, 0, 0);
		}
		return null;
	}
}
