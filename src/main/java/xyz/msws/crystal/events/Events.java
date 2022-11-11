package xyz.msws.crystal.events;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import xyz.msws.crystal.game.GameManager;
import xyz.msws.crystal.utils.ActionBar;
import xyz.msws.crystal.utils.MSG;
import xyz.msws.crystal.utils.PlayerManager;
import xyz.msws.crystal.utils.ReflectionHandler;
import xyz.msws.crystal.CrystalWars;

public class Events implements Listener {
    public Events() {
        Bukkit.getPluginManager().registerEvents(this, CrystalWars.plugin);
    }

    GameManager gManager = new GameManager();
    PlayerManager pManager = new PlayerManager();

    @SuppressWarnings("unchecked")
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        World world = entity.getWorld();
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        if (gManager.getStatus(world).equals("lobby") || gManager.getStatus(world).contains("countdown"))
            event.setCancelled(true);
        List<String> uuids = (List<String>) gManager.getInfo(world, "removeEntities");
        if (uuids != null && entity != null)
            if (uuids.contains(event.getEntity().getUniqueId() + "")) event.setCancelled(true);
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (!pManager.isAlive(player) || pManager.isRespawning(player)) {
                event.setCancelled(true);
                return;
            }
            if (pManager.getInfo(player, "spawnedAt") != null)
                if (System.currentTimeMillis() - pManager.getDouble(player, "spawnedAt") < 5000)
                    event.setCancelled(true);
            if (event.getCause() == DamageCause.FALL) {
                ConfigurationSection kits = CrystalWars.plugin.config.getConfigurationSection("Kits");
                String kit = pManager.getKit(player);
                if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
                if (gManager.getStatus(world).equals("lobby")) return;
                if (player.getLocation().getY() < -10) pManager.killPlayer(player, pManager.getKiller(player));
                if (kits.contains(kit) && gManager.getInfo(world, "beginTimer") == null) {
                    if (player.getLocation().subtract(0, .1, 0).getBlock().getType().isSolid()) {
                        if (kits.contains(kit + ".attributes.fallDamage") && !kits.getBoolean(kit + ".attributes.fallDamage")) {
                            event.setCancelled(true);
                        }
                    }
                }
                if (pManager.getInfo(player, "lastFlapped") == null) return;
                double lastFlap = System.currentTimeMillis() - pManager.getDouble(player, "lastFlapped");
                if (lastFlap < 10000) event.setCancelled(true);
                pManager.setInfo(player, "lastFlapped", null);
            }
        }
    }

    @EventHandler
    public void onArmorStandChange(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() == null) return;
        Entity ent = event.getRightClicked();
        if (!(ent instanceof ArmorStand)) return;
        if (ent.hasMetadata("leaderboard")) event.setCancelled(true);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    @EventHandler
    public void onEntityDamaged(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity(), damager = event.getDamager();
        World world = entity.getWorld();
        Player player;
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        if (gManager.getStatus(world).equals("lobby")) return;
        double dmg = event.getDamage();
        if (damager instanceof Player) {
            player = (Player) damager;
            pManager.setInfo(player, "spawnedAt", null);
            ConfigurationSection kits = CrystalWars.plugin.config.getConfigurationSection("Kits");
            String kit = pManager.getKit(player);
            if (kits.contains(kit)) {
                if (kits.contains(kit + ".attributes.damage")) {
                    dmg = dmg + kits.getDouble(kit + ".attributes.damage");
                    event.setDamage(dmg);
                }
            }
            if (pManager.isAlive(player) && !pManager.isRespawning(player))
                for (String res : new String[]{"coalShop", "endShop", "teamShop"}) {
                    for (String team : gManager.getTeams(world)) {
                        for (String uuid : (List<String>) gManager.getInfo(world, team + res)) {
                            if ((uuid.equals(entity.getUniqueId() + ""))) {
                                player.openInventory(pManager.getGui(player, res));
                            }
                        }
                    }
                }
        }
        List<String> tmp = (List<String>) gManager.getInfo(world, "removeEntities");
        if (tmp == null && !gManager.getStatus(world).contains("countdown")) {
            gManager.crashGame(world, "removeEntities is null");
            return;
        }

        if (event.getEntity() instanceof Player) {
            Player harmed = (Player) entity;
            pManager.setInfo(harmed, "killer", "&e" + damager.getName());
        }
        if (entity == null || damager == null) return;
        if (!(damager instanceof Player || damager instanceof Projectile || damager instanceof TNTPrimed)) return;
        if (damager instanceof Projectile) {
            if (damager instanceof Snowball) dmg += 1;
            Projectile proj = (Projectile) damager;
            if (proj.getShooter() == null) return;
            if (proj.getShooter() instanceof Player) {
                damager = (Player) proj.getShooter();
            } else {
                return;
            }
        }
        Player harmer;
        if (damager instanceof TNTPrimed) {
            harmer = null;
        } else {
            harmer = (Player) damager;
            if (!pManager.isAlive(harmer) || pManager.isRespawning(harmer)) {
                event.setCancelled(true);
                return;
            }
        }
        if (harmer != null && entity instanceof Player) {
            Player harmed = (Player) entity;
            pManager.setInfo(harmed, "killer", gManager.getColor(world, pManager.getTeam(harmer)) + damager.getName());
        }
        if (tmp.contains(entity.getUniqueId() + "")) {
            event.setCancelled(true);
            String team = "";
            for (String theTeam : gManager.getTeams(world)) {
                List<String> crystal = (List<String>) gManager.getInfo(world, theTeam + "crystal");
                if (crystal == null) continue;
                if (crystal.contains(entity.getUniqueId() + "")) {
                    team = theTeam;
                    break;
                }
            }
            if (!team.equals("")) {
                if (damager instanceof Projectile) {
                    dmg = dmg * 4;
                } else if (damager instanceof TNTPrimed) {
                    dmg = dmg * 2;
                } else {
                    dmg = dmg / 3;
                }
                if (harmer != null && pManager.getTeam(harmer).equals(team)) {
                    MSG.nonSpam(harmer, MSG.getString("Invalid.Hit", "That's your own crystal!"));
                    return;
                }
                if (entity instanceof EnderCrystal) {
                    String hpPath = "Games." + world.getName() + ".teams." + team + ".crystalHealth";
                    CrystalWars.plugin.data.set(hpPath, CrystalWars.plugin.data.getDouble(hpPath) - dmg);
                    world.playSound(entity.getLocation(), Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.CrystalHit")), 1, 2);
                    if (harmer != null) {
                        if (!(event.getDamager() instanceof Projectile)) {
                            Vector vel = harmer.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
                            harmer.setVelocity(vel.multiply(dmg / 7));
                        }
                        harmer.playSound(harmer.getLocation(), Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.PlayerHitCrystal")), 2, 2);
                    }
                    if (CrystalWars.plugin.data.getDouble(hpPath) <= 0) {
                        MSG.tell(world, MSG.getString("Game.CrystalDestroyed.Message", "%player% destroyed %team%'s crystal").replace("%player%", harmer == null ? "TNT" : gManager.getColor(world, pManager.getTeam((Player) damager)) + damager.getName()).replace("%team%", gManager.getColor(world, team) + MSG.camelCase(team)));
                        pManager.increment(harmer, "crystalsDestroyed", 1);
                        for (Player target : gManager.getTeamMembers(world, team)) {
                            target.sendTitle(MSG.color(MSG.getString("Game.CrystalDestroyed.Top", "&c&lCRYSTAL DESTROYED")), MSG.color(MSG.getString("Game.CrystalDestroyed.Bottom", "Your crystal has been destroyed!")));
                        }
                        world.createExplosion(entity.getLocation(), 0);
                        List<String> uuids = (List<String>) gManager.getInfo(world, team + "crystal");
                        uuids.remove(entity.getUniqueId() + "");
                        entity.remove();
                    }
                }
            }
        }
        if (!(entity instanceof Player) || harmer == null) return;
        Player damaged = (Player) entity;
        pManager.increment(damaged, "dmgReceived", dmg);
        pManager.increment(harmer, "dmgDealt", dmg);
        if (pManager.getTeam(harmer).equals(pManager.getTeam(damaged))) event.setCancelled(true);
        event.setDamage(dmg);
    }

    @EventHandler
    public void onLand(ProjectileHitEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Arrow && CrystalWars.plugin.data.contains("Games." + entity.getWorld().getName() + ".arrows." + entity.getUniqueId())) {
            double pow = CrystalWars.plugin.data.getDouble("Games." + entity.getWorld().getName() + ".arrows." + entity.getUniqueId());
            if (pow > CrystalWars.plugin.config.getDouble("Kits." + pManager.getKit(((Player) ((Projectile) entity).getShooter())) + ".attributes.poweredBow")) {
                // Code provided by AgainstTheNight
                // https://www.spigotmc.org/threads/get-what-block-an-arrow-hits.322496/#post-3025696
                Bukkit.getScheduler().scheduleSyncDelayedTask(CrystalWars.plugin, () -> {
                    try {
                        Object entityArrow = ReflectionHandler.getClass(ReflectionHandler.ReflectionClassType.OBC, "entity.CraftArrow").getMethod("getHandle").invoke(entity);
                        Field fieldX = ReflectionHandler.getClass(ReflectionHandler.ReflectionClassType.NMS, "EntityArrow").getDeclaredField("d");
                        Field fieldY = ReflectionHandler.getClass(ReflectionHandler.ReflectionClassType.NMS, "EntityArrow").getDeclaredField("e");
                        Field fieldZ = ReflectionHandler.getClass(ReflectionHandler.ReflectionClassType.NMS, "EntityArrow").getDeclaredField("f");
                        fieldX.setAccessible(true);
                        fieldY.setAccessible(true);
                        fieldZ.setAccessible(true);
                        int x = fieldX.getInt(entityArrow);
                        int y = fieldY.getInt(entityArrow);
                        int z = fieldZ.getInt(entityArrow);
                        if (y != -1) {
                            Block block = entity.getWorld().getBlockAt(x, y, z);
                            List<String> mats = CrystalWars.plugin.config.getStringList("ArrowsBreak." + entity.getWorld().getName());
                            if (mats.isEmpty()) mats = CrystalWars.plugin.config.getStringList("ArrowsBreak.default");
                            if (mats.contains(block.getType() + "")) block.setType(Material.AIR);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            entity.remove();
            CrystalWars.plugin.data.set("Games." + entity.getWorld().getName() + ".arrows." + entity.getUniqueId(), null);
        }
    }

    @EventHandler
    public void onShoot(ProjectileLaunchEvent event) {
        ProjectileSource ent = event.getEntity().getShooter();
        if (ent == null || !(ent instanceof Player)) return;
        Player player = (Player) ent;
        World world = player.getWorld();
        if (pManager.getInfo(player, "pulledBow") == null || !gManager.getStatus(world).contains("ingame") || !(event.getEntity() instanceof Arrow))
            return;
        ActionBar.sendHotBarMessage(player, "");
        double power = System.currentTimeMillis() - pManager.getDouble(player, "pulledBow");
        pManager.setInfo(player, "pulledBow", null);
        CrystalWars.plugin.data.set("Games." + player.getWorld().getName() + ".arrows." + event.getEntity().getUniqueId(), power);
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getLines() != null && event.getLine(0).contains("Crystal")) {
            if (!player.hasPermission("crystal.sign.create")) {
                MSG.noPerm(player);
                event.getBlock().breakNaturally();
                return;
            }
            if (CrystalWars.plugin.data.contains("Games." + event.getLine(1))) {
                event.setLine(0, MSG.color(MSG.prefix()));
                event.setLine(2, MSG.color(gManager.getNamedStatus(Bukkit.getWorld(event.getLine(1)))));
                MSG.tell(player, MSG.getString("Sign.Registered", "sign registered"));
                int id = 0;
                while (CrystalWars.plugin.data.contains("SignLocation." + event.getLine(1) + "." + id)) id++;
                CrystalWars.plugin.saveLocation("SignLocation." + event.getLine(1) + "." + id, event.getBlock().getLocation());
            } else {
                MSG.tell(player, MSG.getString("Sign.NotFound", "game not found"));
                event.getBlock().setType(Material.AIR);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.CHEST) {
            if (gManager.getStatus(player.getWorld()).contains("ingame")) {
                String closest = gManager.getClosetCrystal(event.getClickedBlock().getLocation());
                if (!closest.equals(pManager.getTeam(player)) && gManager.getAlivePlayersInTeam(player.getWorld(), closest).size() > 0) {
                    MSG.tell(player, MSG.getString("Invalid.Chest", "%teamColor%%teamName% is still alive").replace("%teamColor%", gManager.getColor(player.getWorld(), closest)).replace("%teamName%", MSG.camelCase(closest)));
                    event.setCancelled(true);
                }
            }
        }
        if (event.getClickedBlock() != null && (Tag.SIGNS.isTagged(event.getClickedBlock().getType()))) {
            if (!(player.getGameMode() == GameMode.CREATIVE && event.getAction() == Action.LEFT_CLICK_BLOCK)) {
                Sign sign = (Sign) event.getClickedBlock().getState();
                if (sign.getLines().length > 0) {
                    if (ChatColor.stripColor(sign.getLine(0)).equals(ChatColor.stripColor(MSG.color(MSG.prefix())))) {
                        World world = Bukkit.getWorld(sign.getLine(1));
                        if (world != player.getWorld()) player.teleport(world.getSpawnLocation());
                        gManager.refreshSigns(player.getWorld());
                    }
                }
            }
        }
        ItemStack hand = event.getItem();
        if (hand == null) return;
        if (hand.getType() == Material.BOW && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && player.getInventory().contains(Material.ARROW, 1))
            if (CrystalWars.plugin.config.contains("Kits." + pManager.getKit(player) + ".attributes.poweredBow"))
                pManager.setInfo(player, "pulledBow", (double) System.currentTimeMillis());
        if (hand.equals(pManager.parseItem(CrystalWars.plugin.gui, "gameSelectorItem", player))) {
            player.openInventory(pManager.getGameSelectorGui());
        }
        if (!hand.hasItemMeta()) return;
        double vel = 0.0;
        if (!hand.hasItemMeta() || !hand.getItemMeta().hasDisplayName()) return;
        if (hand.getItemMeta().getDisplayName().contains("Leap Feather")) vel = 1.3;
        if (hand.getItemMeta().getDisplayName().contains("Super Leap")) vel = 2.5;
        if (vel != 0) {
            Vector dir = player.getLocation().getDirection();
            dir.multiply(vel);
            dir.setY(dir.getY());
            player.setVelocity(dir);
            player.getWorld().playSound(player.getLocation(), Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.DoubleJump")), 2, 2);
            ItemStack tmp = hand;
            if (hand.getAmount() == 1) {
                tmp.setType(Material.AIR);
            } else {
                tmp.setAmount(hand.getAmount() - 1);
            }
            pManager.setInfo(player, "lastFlapped", (double) System.currentTimeMillis());
            player.setItemInHand(tmp);
        }
    }

    @SuppressWarnings("unchecked")
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        // Delete sign locations if it's a sign
        if (Tag.SIGNS.isTagged(event.getBlock().getType())) {
            if (!player.hasPermission("crystal.sign.delete")) {
                MSG.noPerm(player);
                event.setCancelled(true);
                return;
            }
            Sign sign = (Sign) event.getBlock().getState();
            ConfigurationSection signs = CrystalWars.plugin.data.getConfigurationSection("SignLocation." + sign.getLine(1));
            if (signs != null && sign.getLine(1) != null) {
                for (String res : signs.getKeys(false)) {
                    if (res == null) continue;
                    if (CrystalWars.plugin.getLocation("SignLocation." + sign.getLine(1) + "." + res).equals(sign.getLocation())) {
                        CrystalWars.plugin.data.set("SignLocation." + sign.getLine(1) + "." + res, null);
                        MSG.tell(player, MSG.getString("Sign.Deleted", "sign removed"));
                        break;
                    }
                }
            }
        }
        if (!CrystalWars.plugin.data.contains("Games." + world.getName()) || player.getGameMode() == GameMode.CREATIVE)
            return;
        if ((gManager.getStatus(world).equals("lobby") || !pManager.isAlive(player) || pManager.isRespawning(player))) {
            event.setCancelled(true);
            return;
        }
        if (event.getBlock().getType().name().toLowerCase().contains("ore")) {
            int pos = 0;
            while (CrystalWars.plugin.data.contains("Games." + world.getName() + ".oresBroken." + pos)) pos++;
            String path = "Games." + world.getName() + ".oresBroken." + pos;
            CrystalWars.plugin.saveLocation(path, event.getBlock().getLocation());
            CrystalWars.plugin.data.set(path + ".respawnTime", System.currentTimeMillis() + ((Math.random() * 1000 * CrystalWars.plugin.config.getDouble("OreRespawn.Max")) + (CrystalWars.plugin.config.getDouble("OreRespawn.Min") * 1000)));
            CrystalWars.plugin.data.set(path + ".type", event.getBlock().getType() + "");
            return;
        }
        List<String> allowedMats = null;
        if (CrystalWars.plugin.config.contains("BreakableBlocks." + world.getName())) {
            allowedMats = (List<String>) CrystalWars.plugin.config.getList("BreakableBlocks." + world.getName());
        } else {
            allowedMats = (List<String>) CrystalWars.plugin.config.getList("BreakableBlocks.default");
        }
        if (!allowedMats.contains(event.getBlock().getType() + "")) {
            event.setCancelled(true);
        } else {
            pManager.increment(player, "blocksBroken", 1);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!CrystalWars.plugin.data.contains("Games." + world.getName()) || player.getGameMode() == GameMode.CREATIVE)
            return;
        if (!pManager.isAlive(player) || pManager.isRespawning(player)) {
            event.setCancelled(true);
            return;
        }
        if (player.getLocation().getY() > CrystalWars.plugin.getLocation("Games." + world.getName() + ".specSpawn").getY() + 10)
            event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        ConfigurationSection kits = CrystalWars.plugin.config.getConfigurationSection("Kits");
        String kit = pManager.getKit(player);
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        if (gManager.getStatus(world).equals("lobby")) return;
        if (player.getLocation().getY() < -10)
            pManager.killPlayer(player, pManager.getKiller(player).equals("Unknown") ? "Void Damage" : pManager.getKiller(player));
        if (kits.contains(kit) && gManager.getInfo(world, "beginTimer") == null) {
            if (player.getLocation().subtract(0, .1, 0).getBlock().getType().isSolid()) {
                if (kits.contains(kit + ".attributes.doubleJump")) {
                    double dur = System.currentTimeMillis() - (pManager.getDouble(player, "lastDoubleJump"));
                    if (pManager.getInfo(player, "lastDoubleJump") == null || dur > kits.getDouble(kit + ".attributes.doubleJump") * 1000) {
                        player.setAllowFlight(true);
                    }
                }
            }
        }
        if (gManager.getInfo(world, "beginTimer") == null) return;
        double dur = ((double) gManager.getInfo(world, "beginTimer")) - System.currentTimeMillis();
        if (dur > 0 && (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ())) {
            Location tmp = event.getFrom();
            event.setTo(tmp);
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        if (!gManager.getStatus(world).contains("ingame")) return;
        if (!pManager.isAlive(player) || pManager.isRespawning(player) || player.getGameMode() != GameMode.SURVIVAL)
            return;
        Vector dir = player.getLocation().getDirection();
        dir.multiply(1.5);
        dir.setY(dir.getY());
        player.setVelocity(dir);
        player.getWorld().playSound(player.getLocation(), Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.DoubleJump")), 2, 2);
        pManager.setInfo(player, "lastDoubleJump", (double) System.currentTimeMillis());
        pManager.setInfo(player, "lastFlapped", (double) System.currentTimeMillis());
        event.setCancelled(true);
        player.setAllowFlight(false);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = (Player) event.getPlayer();
        World world = player.getWorld();
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        if (event.getItemDrop() != null && event.getItemDrop().getItemStack().hasItemMeta() && event.getItemDrop().getItemStack().getItemMeta().hasDisplayName())
            event.setCancelled(true);
        if (!pManager.isAlive(player) || pManager.isRespawning(player)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = (Player) event.getPlayer();
        if (event.getItem() == null) return;
        if (!CrystalWars.plugin.data.contains("Games." + player.getWorld().getName())) return;
        if (!pManager.isAlive(player) || pManager.isRespawning(player)) {
            event.setCancelled(true);
            return;
        }
        pManager.increment(player, event.getItem().getItemStack().getType() + "pickedUp", event.getItem().getItemStack().getAmount());
    }

    @EventHandler
    public void onChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom();
        newPlayer(player);
        if (CrystalWars.plugin.data.contains("Games." + from.getName())) {
            if (pManager.getTeam(player) != null) {
                CrystalWars.plugin.data.set("Games." + from.getName() + ".teams." + pManager.getTeam(player) + ".members." + player.getName() + ".alive", false);
            }
            if (from.getPlayers().size() < CrystalWars.plugin.data.getInt("Games." + from.getName() + ".startAt") && gManager.getStatus(from).contains("countdown"))
                gManager.setInfo(from, "status", "lobby");
            MSG.tell(from, MSG.getString("Game.Quit", "&e%player% has left the game.").replace("%player%", player.getName()));
        }
        if (!CrystalWars.plugin.data.contains("Games." + player.getWorld().getName())) return;
        if (player.getWorld().getPlayers().size() >= CrystalWars.plugin.data.getInt("Games." + player.getWorld().getName() + ".maxSize")) {
            if (!player.hasPermission("cw.bypass.limit")) {
                MSG.tell(player, MSG.getString("Game.Limit", "Max limit reached"));
                player.teleport(event.getFrom().getSpawnLocation());
            }
        }
        MSG.tell(player.getWorld(), MSG.getString("Game.Join", "&e%player% has joined the game.").replace("%player%", player.getName()));
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (CrystalWars.plugin.data.contains("Games." + world.getName())) {
            if (pManager.getTeam(player) != null) {
                CrystalWars.plugin.data.set("Games." + world.getName() + ".teams." + pManager.getTeam(player) + ".members." + player.getName() + ".alive", false);
            }
            if (world.getPlayers().size() < CrystalWars.plugin.data.getInt("Games." + world.getName() + ".startAt") && gManager.getStatus(world).contains("countdown"))
                gManager.setInfo(world, "status", "lobby");
            MSG.tell(world, MSG.getString("Game.Quit", "&e%player% has left the game.").replace("%player%", player.getName()));
            event.setQuitMessage(null);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        MSG.tell(world, MSG.getString("Game.Join", "&e%player% has joined the game.").replace("%player%", player.getName()));
        MSG.tell(player, MSG.getString("Game.Join", "&e%player% has joined the game.").replace("%player%", player.getName()));
        newPlayer(event.getPlayer());
        event.setJoinMessage(null);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        String team = pManager.getTeam(player);
        pManager.increment(player, "messagesSent", 1);
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        for (Player p : world.getPlayers())
            event.getRecipients().add(p);
        if (gManager.getStatus(world).equals("lobby") || team == null) return;
        if (CrystalWars.plugin.config.getBoolean("ChatFormat.Enabled"))
            event.setFormat(MSG.color(CrystalWars.plugin.config.getString("ChatFormat.Format").replace("%teamColor%", gManager.getColor(world, team)).replace("%teamName%", MSG.camelCase(team))).replace("%player%", player.getDisplayName()).replace("%message%", event.getMessage()));
    }

    /**
     * Add a new player to the world (will put them in specator if it's already
     * going)
     *
     * @param player Player to add to the world
     */
    public void newPlayer(Player player) {
        World world = player.getWorld();
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        if (gManager.getStatus(world).contains("ingame")) {
            MSG.tell(player, MSG.getString("Game.InProgress", "Game in progress"));
            player.teleport(CrystalWars.plugin.getLocation("Games." + world.getName() + ".specSpawn"));
            player.setAllowFlight(true);
            player.setFlying(true);
            for (Player target : world.getPlayers()) {
                if (target == player) continue;
                target.hidePlayer(player);
            }
        } else if (gManager.getStatus(world).equals("lobby")) {
            gManager.assignTeams(world);
            player.teleport(CrystalWars.plugin.getLocation("Games." + world.getName() + ".lobby"));
            if (world.getPlayers().size() > CrystalWars.plugin.data.getInt("Games." + world.getName() + ".startAt")) {
                gManager.startCountdown(world, CrystalWars.plugin.config.getInt("DefaultCountdown"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    @EventHandler
    public void onInteractAtEntity(PlayerInteractEntityEvent event) {
        World world = event.getRightClicked().getWorld();
        Player player = event.getPlayer();
        if (!pManager.isAlive(player) || pManager.isRespawning(player)) {
            event.setCancelled(true);
            return;
        }
        for (String res : new String[]{"coalShop", "endShop", "teamShop"}) {
            for (String team : gManager.getTeams(world)) {
                for (String uuid : (List<String>) gManager.getInfo(world, team + res)) {
                    if ((uuid.equals(event.getRightClicked().getUniqueId() + ""))) {
                        player.openInventory(pManager.getGui(player, res));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        if (gManager.getStatus(world).equals("lobby")) return;
        String team = pManager.getTeam(player);
        if (team == null) return;
        event.setDeathMessage(null);
        event.setKeepInventory(true);
        String killer = "Unknown";
        if (player.getKiller() != null && pManager.getTeam(player.getKiller()) != null) {
            killer = gManager.getColor(world, pManager.getTeam(player.getKiller())) + player.getKiller().getName();
            pManager.setInfo(player.getKiller(), "kills", pManager.getDouble(player.getKiller(), "kills") + 1.0);
        } else {
            EntityDamageEvent ede = player.getLastDamageCause();
            if (ede != null) {
                killer = MSG.color(MSG.camelCase(ede.getCause().name()));
            }
        }
        pManager.setInfo(player, "killer", null);
        pManager.killPlayer(player, killer);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        World world = event.getEntity().getWorld();
        if (!CrystalWars.plugin.data.contains("Games." + world.getName())) return;
        event.blockList().forEach((block) -> {
            if (block.getType() == Material.TNT) {
                block.setType(Material.AIR);
                world.spawnEntity(block.getLocation(), EntityType.PRIMED_TNT);
            }
        });
        event.blockList().clear();
    }

    @EventHandler
    public void onHotBarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (pManager.getInfo(player, "pulledBow") == null || !gManager.getStatus(player.getWorld()).contains("ingame"))
            return;
        ActionBar.sendHotBarMessage(player, "");
        pManager.setInfo(player, "pulledBow", null);
    }

    @SuppressWarnings("unchecked")
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        String id = "", team = pManager.getTeam(player);
        String name = event.getView().getTitle();
        if (name.contains("Game Selection")) {
            event.setCancelled(true);
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                World world = Bukkit.getWorld(ChatColor.stripColor(item.getItemMeta().getDisplayName()));
                if (world != null && world != player.getWorld()) {
                    player.teleport(world.getSpawnLocation());
                }
            }
        }
        if (event.getInventory().getType() != InventoryType.CRAFTING && gManager.getStatus(player.getWorld()).contains("ingame")) {
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) event.setCancelled(true);
        }
        for (String res : CrystalWars.plugin.gui.getKeys(false)) {
            if (pManager.getGui(player, res) == null) continue;
            Inventory inv = pManager.getGui(player, res);
            boolean sameInventory = true;
            if (inv == null || inv.getSize() == 0) continue;
            for (int i = 0; i < inv.getSize(); i++) {
                if (event.getInventory().getItem(i) == null && inv.getItem(i) == null) continue;
                if (event.getInventory().getItem(i) == null || inv.getItem(i) == null) {
                    sameInventory = false;
                    break;
                }
                if (!event.getInventory().getItem(i).equals(inv.getItem(i))) {
                    sameInventory = false;
                    break;
                }
            }
            if (sameInventory) {
                id = res;
                break;
            }
        }
        if (id.equals("")) return;
        event.setCancelled(true);
        String itemId = "";
        ConfigurationSection section = CrystalWars.plugin.gui.getConfigurationSection(id);
        for (String res : section.getKeys(false)) {
            if (!section.contains(res + ".Icon")) continue;
            if (pManager.parseItem(CrystalWars.plugin.gui, id + "." + res, player).equals(item)) {
                itemId = res;
                break;
            }
        }
        if (item == null || itemId.equals("")) {
            player.playSound(player.getLocation(), Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.IncorrectInventory")), 1f, 1f);
            return;
        }
        if (id.equals("kitMenu")) {
            if (CrystalWars.plugin.config.contains("Kits." + itemId)) {
                pManager.setInfo(player, "kit", itemId);
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.KitSelected")), 2, 2);
                MSG.tell(player, MSG.getString("Swapped.Kits", "Your kit is %kit%").replace("%kit%", MSG.camelCase(itemId)));
            }
        }
        ConfigurationSection costs = section.getConfigurationSection(itemId + ".Cost");
        if (costs == null) return;
        for (String material : costs.getKeys(false)) {
            if (!player.getInventory().containsAtLeast(new ItemStack(Material.valueOf(material)), costs.getInt(material))) {
                MSG.nonSpam(player, MSG.getString("Invalid.Balance", "invalid balance"));
                player.playSound(player.getLocation(), Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.InvalidBalance")), 1, 1);
                return;
            }
        }
        ItemStack cItem = new ItemStack(item.getType(), item.getAmount());
        cItem.setDurability(item.getDurability());
        ItemMeta meta = cItem.getItemMeta();
        meta.setLore(new ArrayList<String>());
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
            meta.setDisplayName(MSG.color(item.getItemMeta().getDisplayName()));
        cItem.setItemMeta(meta);
        if (section.contains(itemId + ".AllowSamePurchase") && !section.getBoolean(itemId + ".AllowSamePurchase")) {
            List<String> purchases = null;
            if (section.contains(itemId + ".AddResources")) {
                purchases = (List<String>) gManager.getInfo(player.getWorld(), team + "purchases");
            } else {
                purchases = pManager.getStringList(player, "purchases");
            }
            if (purchases == null) purchases = new ArrayList<String>();
            if (purchases != null) {
                if (purchases.contains(itemId)) {
                    MSG.tell(player, MSG.getString("Invalid.Purchase", "You've already purchased this upgrade!"));
                    player.playSound(player.getLocation(), Sound.ENTITY_HORSE_ANGRY, 1, 1);
                    return;
                }
            }
            purchases.add(itemId);
            if (section.contains(itemId + ".AddResources")) {
                gManager.setInfo(player.getWorld(), team + "purchases", purchases);
            } else {
                pManager.setInfo(player, "purchases", purchases);
            }
        }
        if (section.contains(itemId + ".AddResources")) {
            String rMap = "Games." + player.getWorld().getName() + ".teams." + team + ".resources";
            for (String mat : section.getConfigurationSection(itemId + ".AddResources").getKeys(false)) {
                int amo = 0;
                double rate = 0;
                if (CrystalWars.plugin.data.contains(rMap + "." + mat + ".amo"))
                    amo = CrystalWars.plugin.data.getInt(rMap + "." + mat + ".amo");
                if (CrystalWars.plugin.data.contains(rMap + "." + mat + ".rate"))
                    rate = CrystalWars.plugin.data.getInt(rMap + "." + mat + ".rate");

                if (section.contains(itemId + ".AddResources." + mat + ".Amount"))
                    amo = amo + section.getInt(itemId + ".AddResources." + mat + ".Amount");

                if (section.contains(itemId + ".AddResources." + mat + ".Rate"))
                    rate = rate + section.getDouble(itemId + ".AddResources." + mat + ".Rate");

                CrystalWars.plugin.data.set(rMap + "." + mat + ".amo", amo);
                CrystalWars.plugin.data.set(rMap + "." + mat + ".rate", rate);
                CrystalWars.plugin.saveData();
            }
            for (Player member : gManager.getAlivePlayersInTeam(player.getWorld(), team)) {
                MSG.tell(member, MSG.getString("Shop.TeamPurchase", "%player% purchased %id%").replace("%player%", gManager.getColor(player.getWorld(), team) + player.getName()).replace("%id%", MSG.camelCase(itemId)));
            }
            cItem = new ItemStack(Material.AIR);
        }
        if (section.contains(itemId + ".AddEnchantToTeam")) {
            for (String res : section.getConfigurationSection(itemId + ".AddEnchantToTeam").getKeys(false)) {
                CrystalWars.plugin.data.set("Games." + player.getWorld().getName() + ".teams." + team + ".enchants." + res, section.getInt(itemId + ".AddEnchantToTeam." + res));
            }
            for (Player member : gManager.getAlivePlayersInTeam(player.getWorld(), team)) {
                MSG.tell(member, MSG.getString("Shop.TeamPurchase", "%player% purchased %id%").replace("%player%", gManager.getColor(player.getWorld(), team) + player.getName()).replace("%id%", MSG.camelCase(itemId)));
            }
            cItem = new ItemStack(Material.AIR);
        }
        int pos = 3;
        for (String res : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            if (cItem.getType().name().toLowerCase().contains(res)) {
                ItemStack[] armor = player.getInventory().getArmorContents();
                armor[pos] = cItem;
                player.getInventory().setArmorContents(armor);
                cItem = new ItemStack(Material.AIR);
            }
            pos--;
        }
        if (cItem.getType().name().toLowerCase().contains("sword")) {
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                if (player.getInventory().getItem(i) == null) continue;
                if (player.getInventory().getItem(i).getType().name().toLowerCase().contains("sword")) {
                    player.getInventory().setItem(i, new ItemStack(Material.AIR));
                }
            }
        }
        pManager.increment(player, "purchases", 1);
        for (String material : costs.getKeys(false))
            player.getInventory().removeItem(new ItemStack(Material.valueOf(material), costs.getInt(material)));
        player.playSound(player.getLocation(), Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.Purchase")), 2, 2);
        player.getInventory().addItem(cItem);
    }
}
