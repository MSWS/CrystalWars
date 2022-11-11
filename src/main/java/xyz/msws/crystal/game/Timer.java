package xyz.msws.crystal.game;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.msws.crystal.utils.ActionBar;
import xyz.msws.crystal.utils.MSG;
import xyz.msws.crystal.utils.PlayerManager;
import xyz.msws.crystal.utils.TimeManager;
import xyz.msws.crystal.CrystalWars;

public class Timer {
	PlayerManager pManager = new PlayerManager();
	TimeManager tManager = new TimeManager();
	GameManager gManager = new GameManager();
	int timer = 0;
	FileConfiguration data = CrystalWars.plugin.data;

	public void register() {
		new BukkitRunnable() {
			@SuppressWarnings("unchecked")
			public void run() {
				for (World world : Bukkit.getWorlds()) {
					if (timer % 5 == 0) // Update signs
						gManager.refreshSigns(world);
					if (timer % (CrystalWars.plugin.config.getDouble("LeaderboardRefreshRate") * 20) == 0) {
						gManager.refreshLeaderboards(world);
					}
					if (!data.contains("Games." + world.getName()))
						continue;
					if (gManager.getStatus(world).contains("ingame")
							&& gManager.getTeamsWithPlayers(world).size() < 2) {
						gManager.winGame(world);
						continue;
					}
					ConfigurationSection blocks = data
							.getConfigurationSection("Games." + world.getName() + ".oresBroken");
					if (blocks != null) {
						for (String res : blocks.getKeys(false)) {
							String path = "Games." + world.getName() + ".oresBroken." + res;
							if (System.currentTimeMillis() >= data.getDouble(path + ".respawnTime")) {
								Block block = world.getBlockAt(CrystalWars.plugin.getLocation(path));
								block.setType(Material.valueOf(data.getString(path + ".type")));
								data.set(path, null);
							}
						}
					}
					if (gManager.getInfo(world, "beginTimer") != null) {
						double raw = (double) gManager.getInfo(world, "beginTimer"); // time it will start
						double time = System.currentTimeMillis();
						double dur = raw - time;
						double pTime = System.currentTimeMillis() - (double) gManager.getInfo(world, "lastPling");
						double total = CrystalWars.plugin.config.getDouble("BeginningTimer") * 1000;
						if (dur > 0) {
							String msg = CrystalWars.plugin.config.getString("Scoreboard.ActionTimeLeft")
									.replace("%progressBar%", MSG.progressBar(total - dur, total, 20))
									.replace("%time%", tManager.getRoundTimeMillis(dur + 1000));
							for (Player target : world.getPlayers()) {
								if (pTime > 1000) {
									target.playSound(target.getLocation(),
											Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.ActionSound")), 2, 2);
									gManager.setInfo(world, "lastPling", (double) System.currentTimeMillis());
								}
								ActionBar.sendHotBarMessage(target, MSG.color(msg));
							}
						} else {
							for (Player target : world.getPlayers()) {
								ActionBar.sendHotBarMessage(target, "");
								target.playSound(target.getLocation(),
										Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.ActionStart")), 2, 2);
							}
							gManager.setInfo(world, "beginTimer", null);
							gManager.setInfo(world, "lastPling", null);
						}
					}
					for (Player player : world.getPlayers()) {
						player.setSaturation(2);
						player.setFoodLevel(20);
						if (gManager.getStatus(world).equals("lobby"))
							player.setHealth(20);
						if (!gManager.getStatus(world).contains("ingame") || gManager.getTeams(world) == null)
							continue;
						// Sending bow charging action bar messages
						String kit = pManager.getKit(player);
						if (pManager.getInfo(player, "pulledBow") != null) {
							double power = System.currentTimeMillis() - pManager.getDouble(player, "pulledBow"),
									fullPower = CrystalWars.plugin.config.getDouble("Kits." + kit + ".attributes.poweredBow");
							if (power < fullPower) {
								player.playSound(player.getLocation(),
										Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.BowCharge")), .2f,
										(float) (power / fullPower * 2));
								ActionBar.sendHotBarMessage(player,
										MSG.color(MSG.getString("Attributes.Bow.BowCharging", "Full Power %bar% %time%")
												.replace("%bar%", MSG.progressBar(power, fullPower, 20))
												.replace("%time%", tManager.getTime(fullPower - power, 1))));
							} else {
								ActionBar.sendHotBarMessage(player,
										MSG.color(MSG.getString("Attributes.Bow.BowCharged", "fully charged")));
							}
						}
						// KIT STUFF
						ConfigurationSection kits = CrystalWars.plugin.config.getConfigurationSection("Kits");
						ConfigurationSection receive = CrystalWars.plugin.config
								.getConfigurationSection("Kits." + kit + ".receive");
						if (kit != null && receive != null) {
							for (String res : receive.getKeys(false)) {
								if (!receive.contains(res + ".Rate"))
									continue;
								if (pManager.getInfo(player, "lastKit" + res) == null
										|| System.currentTimeMillis()
												- pManager.getDouble(player, "lastKit" + res) > receive
														.getDouble(res + ".Rate") * 1000
												&& pManager.isAlive(player) && !pManager.isRespawning(player)) {
									ItemStack item = pManager.parseItem(CrystalWars.plugin.config,
											"Kits." + kit + ".receive." + res, player);
									if (receive.contains(res + ".Max")) {
										int amo = 0;
										for (ItemStack tempItem : player.getInventory().getContents()) {
											if (tempItem == null)
												continue;
											ItemStack tmp = tempItem.clone();
											tmp.setAmount(1);
											item.setAmount(1);
											if (!tmp.equals(item))
												continue;
											// if(tempItem==null|| tempItem.getType()!=item.getType() )
											// continue;

											amo += tempItem.getAmount();
										}
										ItemStack cursor = player.getOpenInventory().getCursor();
										if (cursor != null && cursor.getType() == item.getType())
											amo += cursor.getAmount();
										if (amo >= receive.getInt(res + ".Max"))
											continue;
									}
									player.getInventory().addItem(item);
									player.playSound(player.getLocation(),
											Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.ItemGiven")), 1, 1);
									pManager.setInfo(player, "lastKit" + res, (double) System.currentTimeMillis());
								}
							}
						}
						if (kits.contains(kit + ".attributes.doubleJump")) {
							if (pManager.getInfo(player, "lastDoubleJump") != null) {
								double dur = (kits.getDouble(kit + ".attributes.doubleJump") * 1000)
										- (System.currentTimeMillis() - pManager.getDouble(player, "lastDoubleJump"));
								if (dur < 0) {
									ActionBar.sendHotBarMessage(player, MSG.color(
											MSG.getString("Attributes.DoubleJump.Ready", "&a&lDouble Jump Refreshed")));
									pManager.setInfo(player, "lastDoubleJump", null);
								} else {
									ActionBar.sendHotBarMessage(player, MSG.color(
											MSG.getString("Attributes.DoubleJump.Cooldown", "Double Jump %bar% %time%")
													.replace("%bar%", MSG.progressBar(
															kits.getDouble(kit + ".attributes.doubleJump") * 1000 - dur,
															kits.getDouble(kit + ".attributes.doubleJump") * 1000, 20))
													.replace("%time%", tManager.getTime(dur, 2))));
								}
							}
						}
						if (pManager.getInfo(player, "diedAt") == null)
							continue;
						double dur = (pManager.getDouble(player, "diedAt")
								+ (CrystalWars.plugin.config.getDouble("RespawnTime") * 1000)) - System.currentTimeMillis();
						if (dur < 0) {
							pManager.spawnPlayer(player);
							pManager.setInfo(player, "diedAt", null);
							ActionBar.sendHotBarMessage(player, "");
						} else {
							ActionBar.sendHotBarMessage(player,
									MSG.color(MSG.getString("Game.Respawn", "You will respawn in %time%")
											.replace("%time%", tManager.getRoundTimeMillis(dur + 1000))));
						}
					}
					if (!gManager.getStatus(world).contains("ingame") || gManager.getTeams(world) == null)
						continue;
					List<String> tmp = (List<String>) gManager.getInfo(world, "removeEntities");
					if (tmp == null) {
						gManager.crashGame(world, "Entities failed to be loaded");
						continue;
					}
					for (String team : gManager.getTeams(world)) {
						ConfigurationSection enchants = data
								.getConfigurationSection("Games." + world.getName() + ".teams." + team + ".enchants");
						if (enchants != null) {
							for (String enchant : enchants.getKeys(false)) {
								Enchantment ench = Enchantment.getByName(enchant);
								if (ench == null)
									continue;
								for (Player target : gManager.getAlivePlayersInTeam(world, team)) {
									for (int i = 0; i < target.getInventory().getSize(); i++) {
										ItemStack item = target.getInventory().getItem(i);
										if (item == null)
											continue;
										if (!ench.canEnchantItem(item))
											continue;
										item.addUnsafeEnchantment(ench, enchants.getInt(enchant));
										target.getInventory().setItem(i, item);
									}
									ItemStack[] armor = target.getInventory().getArmorContents();
									for (ItemStack item : armor) {
										if (item == null)
											continue;
										if (!ench.canEnchantItem(item))
											continue;
										item.addUnsafeEnchantment(ench, enchants.getInt(enchant));
									}
								}
							}
						}
						if (gManager.getAlivePlayersInTeam(world, team).size() <= 0
								&& gManager.getCrystalHP(world, team) > 0) {
							List<String> uuids = (List<String>) gManager.getInfo(world, team + "crystal");
							for (int i = 0; i < uuids.size(); i++) {
								String res = uuids.get(i);
								Entity ent = gManager.getEntityByUUID(UUID.fromString(res), world);
								if (ent instanceof EnderCrystal) {
									uuids.remove(i);
									ent.remove();
								}
							}
							data.set("Games." + world.getName() + ".teams." + team + ".crystalHealth", 0);
							MSG.tell(world, MSG.getString("Game.CrystalDestroyed.Unknown", "%team%'s crystal removed")
									.replace("%team%", gManager.getColor(world, team) + MSG.camelCase(team)));
						}
						ConfigurationSection resources = data
								.getConfigurationSection("Games." + world.getName() + ".teams." + team + ".resources");
						if (gManager.getInfo(world, "beginTimer") == null)
							for (String mat : resources.getKeys(false)) {
								if (!resources.contains(mat + ".amo"))
									continue;
								if (timer % resources.getDouble(mat + ".rate") > 0)
									continue;
								Location spawn = (Location) CrystalWars.plugin
										.getLocation("Games." + world.getName() + ".teams." + team + ".resourceSpawn");
								world.dropItem(spawn, new ItemStack(Material.valueOf(mat.toUpperCase()),
										resources.getInt(mat + ".amo")));
							}
						gManager.setInfo(world, "removeEntities", tmp);
						for (String shop : new String[] { "coalShop", "endShop", "teamShop" }) {
							for (String res : (List<String>) gManager.getInfo(world, team + shop)) {
								Entity ent = (Entity) gManager.getEntityByUUID(UUID.fromString(res), world);
								if (ent == null)
									gManager.crashGame(world, team + "'s shops failed to spawn");
								ent.teleport(CrystalWars.plugin
										.getLocation("Games." + world.getName() + ".teams." + team + "." + shop));
								if (ent instanceof ArmorStand) {
									ent.teleport(CrystalWars.plugin
											.getLocation("Games." + world.getName() + ".teams." + team + "." + shop)
											.add(0, 1, 0));
								}
							}
						}
						if (((List<String>) gManager.getInfo(world, team + "crystal")).size() > 3) {
							gManager.crashGame(world, "Incorrect crystal entities for " + team);
						}
						int pos = 0;
						for (String res : (List<String>) gManager.getInfo(world, team + "crystal")) {
							Entity ent = (Entity) gManager.getEntityByUUID(UUID.fromString(res), world);
							if (ent == null)
								continue;
							if (ent instanceof ArmorStand) {
								if (pos == 0) {
									if (data.getDouble(
											"Games." + world.getName() + ".teams." + team + ".crystalHealth") <= 0) {
										ent.setCustomName(MSG.color(gManager.getColor(world, team) + "&l"
												+ gManager.getTeamStatus(world, team)));
									} else {
										ent.setCustomName(MSG.progressBar(
												data.getDouble("Games." + world.getName() + ".teams." + team
														+ ".crystalHealth"),
												CrystalWars.plugin.config.getDouble("MaxCrystalHealth"),
												CrystalWars.plugin.config.getInt("HealthBarLength")));
									}
									ent.teleport(CrystalWars.plugin
											.getLocation("Games." + world.getName() + ".teams." + team + ".crystal"));
								} else if (pos == 1) {
									if (data.getDouble(
											"Games." + world.getName() + ".teams." + team + ".crystalHealth") <= 0) {
										ent.setCustomName(MSG.color(gManager.getColor(world, team) + MSG.camelCase(team)
												+ "'s Crystal (&l" + CrystalWars.plugin.config.getString("Status.Dead")
												+ MSG.color(gManager.getColor(world, team)) + ")"));
									} else {
										ent.setCustomName(MSG.color(gManager.getColor(world, team) + MSG.camelCase(team)
												+ "'s Crystal "
												+ Math.round(data.getDouble("Games." + world.getName() + ".teams."
														+ team + ".crystalHealth"))
												+ "/" + Math.round(CrystalWars.plugin.config.getDouble("MaxCrystalHealth"))));
									}
									ent.teleport(CrystalWars.plugin
											.getLocation("Games." + world.getName() + ".teams." + team + ".crystal")
											.add(0, .2, 0));
								}
								pos++;
							} else {
								ent.teleport(CrystalWars.plugin
										.getLocation("Games." + world.getName() + ".teams." + team + ".crystal"));
							}
						}
					}
				}
				timer++;
			}
		}.runTaskTimer(CrystalWars.plugin, 0, 1);
	}
}
