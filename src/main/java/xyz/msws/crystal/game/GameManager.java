package xyz.msws.crystal.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import xyz.msws.crystal.utils.MSG;
import xyz.msws.crystal.utils.NBT;
import xyz.msws.crystal.utils.PlayerManager;
import xyz.msws.crystal.utils.TimeManager;
import xyz.msws.crystal.CrystalWars;

public class GameManager {
    FileConfiguration data = CrystalWars.plugin.data;
    PlayerManager pManager = new PlayerManager();
    NBT NBT = new NBT();

    /**
     * Stores certain types of data in data.yml (usually is temporary)
     *
     * @param world World to store data in
     * @param id    Id of data
     * @param data  Object data (should be able to save in YML file)
     */
    public void setInfo(World world, String id, Object data) { // Store info of the game
        if (!pManager.isSaveable(data)) {
            int currentLine = Thread.currentThread().getStackTrace()[2].getLineNumber();

            String fromClass = new Exception().getStackTrace()[1].getClassName();
            if (fromClass.contains("."))
                fromClass = fromClass.split("\\.")[fromClass.split("\\.").length - 1];
            MSG.log("WARNING!!! SAVING ODD DATA FROM " + fromClass + ":" + currentLine);
        }
        this.data.set("Games." + world.getName() + "." + id, data);
    }

    /**
     * Object management
     *
     * @param world World to get info in
     * @param id    Id of object
     * @return returns object (can be null)
     */
    public Object getInfo(World world, String id) {
        return data.get("Games." + world.getName() + "." + id);
    }

    /**
     * Get status of a world
     *
     * @param world World to get status in
     * @return Returns status, "disabled" if there is no game
     */
    public String getStatus(World world) {
        if (getInfo(world, "status") == null)
            return "disabled";
        return (String) getInfo(world, "status");
    }

    /**
     * Returns how much HP a crystal has
     *
     * @param world World that crystal is in
     * @param team  Team's crystal
     * @return 0.0 if dead, otherwise a real double
     */
    public double getCrystalHP(World world, String team) {
        return CrystalWars.plugin.data.getDouble("Games." + world.getName() + ".teams." + team + ".crystalHealth");
    }

    /**
     * Gets the an ArrayList of alive players
     *
     * @param world World to get alive players in
     * @return List of alive players
     */

    public List<Player> alivePlayers(World world) {
        ConfigurationSection teams = data.getConfigurationSection("Games." + world.getName() + ".teams");
        if (teams == null)
            return null;
        List<Player> players = new ArrayList<Player>();
        for (String res : getTeams(world)) {
            ConfigurationSection team = teams.getConfigurationSection(res + ".members");
            if (team == null)
                continue;
            for (String name : team.getKeys(false)) {
                if (team.getBoolean(name + ".alive"))
                    players.add(Bukkit.getPlayer(name));
            }
        }
        return players;
    }

    /**
     * Automatically assigns teams to players that don't have a team
     *
     * @param world World to assign teams in
     */
    public void assignTeams(World world) {
        if (getTeams(world) == null)
            return;
        if (!data.contains("Games." + world.getName()))
            return;
        for (Player player : world.getPlayers()) {
            if (pManager.getTeam(player) != null)
                continue;
            if (getLeastTeam(player.getWorld()) == null)
                continue;
            String team = getLeastTeam(player.getWorld());
            CrystalWars.plugin.data.set(
                    "Games." + world.getName() + ".teams." + team + ".members." + player.getName() + ".alive", true);
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
        updateNames(world);
    }

    /**
     * Update player display names and tablistnames compatible with if the player is
     * alive or not
     *
     * @param world World to update names
     */
    public void updateNames(World world) {
        for (Player player : world.getPlayers()) {
            String team = pManager.getTeam(player);
            if (team == null)
                continue;
            String dName = getColor(world, team) + player.getName();
            if (!pManager.isAlive(player)) {
                player.setDisplayName(
                        MSG.color("&7" + CrystalWars.plugin.config.getString("Status.Dead") + " " + dName + "&f"));
            } else {
                player.setDisplayName(MSG.color(dName + "&f"));

            }
            player.setPlayerListName(MSG.color(getColor(player.getWorld(), team) + player.getName() + "&r"));
        }
    }

    /**
     * @param world World to get list of teams in
     * @return List of all teams in the world
     */
    public List<String> getTeams(World world) {
        List<String> teams = new ArrayList<String>();
        ConfigurationSection teamList = data.getConfigurationSection("Games." + world.getName() + ".teams");
        if (teamList == null)
            return null;
        for (String res : teamList.getKeys(false))
            teams.add(res);
        return teams;
    }

    /**
     * @param world World team is in
     * @param team  to get alive players in
     * @return List of all players in the team
     */
    public List<Player> getAlivePlayersInTeam(World world, String team) {
        List<Player> players = getTeamMembers(world, team);
        List<Player> result = new ArrayList<Player>();
        for (Player alive : alivePlayers(world)) {
            if (players.contains(alive)) {
                result.add(alive);
            }
        }

        return result;
    }

    /**
     * @param world World that team is in
     * @param team  Team to get status of
     * @return Returns string of status based on Config values
     */
    public String getTeamStatus(World world, String team) {
        if (data.getDouble("Games." + world.getName() + ".teams." + team + ".crystalHealth") > 0) {
            return CrystalWars.plugin.config.getString("Status.CrystalAlive");
        } else if (getAlivePlayersInTeam(world, team).size() > 0) {
            return CrystalWars.plugin.config.getString("Status.CrystalDead")
                    .replace("%amo%", getAlivePlayersInTeam(world, team).size() + "")
                    .replace("%s%", getAlivePlayersInTeam(world, team).size() == 1 ? "" : "s");
        } else {
            return CrystalWars.plugin.config.getString("Status.Dead");
        }
    }

    /**
     * @param world World that team is in
     * @param team  Team to get members
     * @return List of team members in a team
     */
    public List<Player> getTeamMembers(World world, String team) {
        List<Player> players = new ArrayList<Player>();
        for (Player target : world.getPlayers()) {
            if (pManager.getTeam(target) == null)
                continue;
            if (pManager.getTeam(target).equals(team))
                players.add(target);
        }
        return players;
    }

    /**
     * @param world World to get alive players in
     * @return List of teams with ALIVE players
     */
    public List<String> getTeamsWithPlayers(World world) {
        List<String> teams = new ArrayList<String>();
        for (String team : getTeams(world)) {
            if (getAlivePlayersInTeam(world, team).size() > 0)
                teams.add(team);
        }
        return teams;
    }

    /**
     * @param world World to get teams
     * @return Returns what team has the least amount of players useful for
     * assigning teams
     */
    public String getLeastTeam(World world) {
        if (getTeams(world) == null)
            return null;
        int size = 0;
        String team = "";
        for (String res : getTeams(world)) {
            if (getTeamMembers(world, res).size() <= size) {
                size = getTeamMembers(world, res).size();
                team = res;
            }
        }
        if (team.equals(""))
            return null;
        return team;
    }

    /**
     * @param world World that the team is in
     * @param team  Name of team
     * @return Return what color the team has (&amp;c, &amp;e, etc)
     */
    public String getColor(World world, String team) {
        return (data.getString("Games." + world.getName() + ".teams." + team + ".color"));
    }

    /**
     * Returns who gets the most kills (null if no kills)
     *
     * @param world World to get the MVP in
     * @return Player (if any) with most kills (null if none)
     */
    public Player getMVP(World world) {
        double record = 0;
        Player winner = null;
        for (Player player : world.getPlayers()) {
            if (pManager.getDouble(player, "kills") > record) {
                record = pManager.getDouble(player, "kills");
                winner = player;
            }
        }
        return winner;
    }

    public String getBuilders(World world) {
        return CrystalWars.plugin.data.contains("Games." + world.getName() + ".builders")
                ? CrystalWars.plugin.data.getString("Games." + world.getName() + ".builders")
                : "None";
    }

    /**
     * Teleports and starts the game, use startCountdown if you want a countdown
     *
     * @param world World to start
     */
    public void startGame(World world) {
        setInfo(world, "status", "ingame" + System.currentTimeMillis());
        setInfo(world, "beginTimer",
                System.currentTimeMillis() + (CrystalWars.plugin.config.getDouble("BeginningTimer") * 1000));
        setInfo(world, "lastPling", (double) System.currentTimeMillis());
        saveWorld(world);
        List<String> tmp = new ArrayList<String>();
        CrystalWars.plugin.lang.getStringList("Game.StartMsg").forEach((res) -> MSG.tell(world,
                res.replace("%map%", world.getName()).replace("%builders%", getBuilders(world))));
        for (String team : getTeams(world)) {
            String rMap = "Games." + world.getName() + ".teams." + team + ".resources";
            setInfo(world, team + "purchases", null);
            data.set("Games." + world.getName() + ".teams." + team + ".enchants", null);
            data.set(rMap, null);
            data.set("Games." + world.getName() + ".teams." + team + ".crystalHealth",
                    CrystalWars.plugin.config.getDouble("MaxCrystalHealth"));
            data.set(rMap + ".COAL.amo", 1);
            data.set(rMap + ".COAL.rate", 20 * 2.5);
            int pos = 0;
            for (Player target : getTeamMembers(world, team)) {
                pManager.spawnPlayer(target);
                pManager.setInfo(target, "kills", 0.0);
                pManager.setInfo(target, "purchases", null);
                pManager.increment(target, "plays", 1);
                pManager.increment(target, "quits", 1);
                data.set("Games." + target.getWorld().getName() + ".teams." + team + ".members." + target.getName()
                        + ".alive", true);
            }
            List<String> shops = new ArrayList<String>();

            EnderCrystal crystal = (EnderCrystal) world.spawnEntity(
                    CrystalWars.plugin.getLocation("Games." + world.getName() + ".teams." + team + ".crystal"),
                    EntityType.ENDER_CRYSTAL);
            ArmorStand name = (ArmorStand) world.spawnEntity(crystal.getLocation(), EntityType.ARMOR_STAND);
            ArmorStand hp = (ArmorStand) world.spawnEntity(crystal.getLocation().subtract(0, .2, 0),
                    EntityType.ARMOR_STAND);
            name.setCustomName(MSG.color(getColor(world, team) + MSG.camelCase(team) + "'s Crystal "
                    + data.getDouble("Games." + world.getName() + ".teams." + team + ".crystalHealth") + "/"
                    + CrystalWars.plugin.config.getDouble("MaxCrystalHealth")));
            hp.setCustomName(MSG.progressBar(
                    data.getDouble("Games." + world.getName() + ".teams." + team + ".crystalHealth"),
                    CrystalWars.plugin.config.getDouble("MaxCrystalHealth"), CrystalWars.plugin.config.getInt("HealthBarLength")));
            name.setVisible(false);
            hp.setVisible(false);
            name.setCustomNameVisible(true);
            hp.setCustomNameVisible(true);
            hp.setGravity(false);
            name.setGravity(false);
            tmp.add(crystal.getUniqueId() + "");
            tmp.add(hp.getUniqueId() + "");
            tmp.add(name.getUniqueId() + "");
            shops.add(crystal.getUniqueId() + "");
            shops.add(hp.getUniqueId() + "");
            shops.add(name.getUniqueId() + "");
            setInfo(world, team + "crystal", shops);

            String[] names = {"Coal Shop", "End Shop", "Team Shop"};
            pos = 0;
            for (String res : new String[]{"coalShop", "endShop", "teamShop"}) {
                shops = new ArrayList<String>();
                Location loc = CrystalWars.plugin.getLocation("Games." + world.getName() + ".teams." + team + "." + res);
                // For some reason Location#add messes up with the Location
                Enderman shop = (Enderman) world.spawnEntity(loc, EntityType.ENDERMAN);
                ArmorStand stand = (ArmorStand) world.spawnEntity(loc.add(0, 1, 0), EntityType.ARMOR_STAND);
                NBT.setNBT("NoAI", shop, true);
                NBT.setNBT("Silent", shop, true);
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setCustomName(MSG.color(getColor(world, team) + MSG.camelCase(team) + " " + names[pos]));
                stand.setCustomNameVisible(true);
                shop.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 999999, 255, true, false));
                tmp.add(shop.getUniqueId() + "");
                tmp.add(stand.getUniqueId() + "");
                shops.add(shop.getUniqueId() + "");
                shops.add(stand.getUniqueId() + "");
                setInfo(world, team + res, shops);
                pos++;
            }
        }
        setInfo(world, "removeEntities", tmp);
        updateNames(world);
    }

    public void refreshSigns(World world) {
        ConfigurationSection signs = CrystalWars.plugin.data.getConfigurationSection("SignLocation." + world.getName());
        if (signs == null)
            return;
        for (String res : signs.getKeys(false)) {
            if (res == null)
                continue;
            Location loc = CrystalWars.plugin.getLocation("SignLocation." + world.getName() + "." + res);
            Block block = loc.getBlock();
            if (!(block.getState() instanceof WallSign sign))
                return;
            ((Sign) sign).setLine(2, MSG.color(getNamedStatus(Bukkit.getWorld(((Sign) block.getState()).getLine(1)))));
        }
    }

    public void refreshLeaderboards(World world) {
        ConfigurationSection boards = CrystalWars.plugin.data.getConfigurationSection("Leaderboards." + world.getName());
        if (boards == null)
            return;
        for (Entity ent : world.getEntities()) {
            if (ent.hasMetadata("leaderboard"))
                ent.remove();
        }
        for (String res : boards.getKeys(false)) {
            if (res == null)
                continue;
            Location loc = CrystalWars.plugin.getLocation("Leaderboards." + world.getName() + "." + res);
            List<String> lines = CrystalWars.plugin.lang.getStringList("Leaderboards.Lines");
            List<OfflinePlayer> players = pManager.getHighestRanks(res, lines.size());
            lines = flip(lines);
            for (String line : lines) {
                String number = "";
                for (int i = line.indexOf("%") + 1; i < line.length(); i++) {
                    if (line.charAt(i) == '%')
                        break;
                    number = number + line.charAt(i) + "";
                }
                int val = -1;
                try {
                    val = Integer.parseInt(number) - 1;
                } catch (Exception e) {
                }
                String name = line.replace("%statType%", MSG.camelCase(res));
                ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMetadata("leaderboard", new FixedMetadataValue(CrystalWars.plugin, null));
                if (val != -1) {
                    if (players.size() > val) {
                        name = name.replace("%" + (val + 1) + "%",
                                MSG.getString("Leaderboards.Format", "%player% %stat%").replace("%player%",
                                        players.get(val).getName()));
                        if (res.equals("playtime")) {
                            name = name.replace("%stat%",
                                    new TimeManager().getTime(pManager.getNumberStat(players.get(val), res), 2));
                        } else {
                            name = name.replace("%stat%", ((int) pManager.getNumberStat(players.get(val), res) + "&r"));
                        }
                    } else {
                        name = name.replace("%" + (val + 1) + "%", "-");
                    }
                }
                stand.setCustomName(MSG.color((name)).trim());
                stand.setCustomNameVisible(true);
                loc.add(0, .3, 0);
            }
        }
    }

    public String getClosestLeaderboard(Location loc) {
        ConfigurationSection boards = CrystalWars.plugin.data
                .getConfigurationSection("Leaderboards." + loc.getWorld().getName());
        if (boards == null)
            return "";
        String closest = "";
        double dist = 0.0;
        for (String res : boards.getKeys(false)) {
            if (res == null)
                continue;
            Location tmp = CrystalWars.plugin.getLocation("Leaderboards." + loc.getWorld().getName() + "." + res);
            if (tmp.distanceSquared(loc) <= dist || dist == 0) {
                dist = tmp.distanceSquared(loc);
                closest = res;
            }
        }
        return closest;
    }

    private List<String> flip(List<String> array) {
        List<String> result = new ArrayList<String>();
        for (int i = array.size() - 1; i >= 0; i--) {
            result.add(array.get(i));
        }
        return result;
    }

    /**
     * Ends the game and broadcasts win message
     *
     * @param world World to stop
     */
    @SuppressWarnings("deprecation")
    public void winGame(World world) {
        String winner = getTeamsWithPlayers(world).get(0), winColor = getColor(world, winner);
        Player mvp = getMVP(world);
        for (String res : CrystalWars.plugin.lang.getStringList("Game.Over"))
            MSG.tell(world,
                    res.replace("%team%", MSG.camelCase(winner)).replace("%winColor%", getColor(world, winner))
                            .replace("%time%",
                                    new TimeManager().getTime(System.currentTimeMillis()
                                            - Double.valueOf(getStatus(world).substring("ingame".length())), 2))
                            .replace("%mvp%",
                                    mvp == null ? "None"
                                            : MSG.color(getColor(world, pManager.getTeam(mvp)) + mvp.getName()))
                            .replace("%kills%", mvp == null ? "0" : pManager.getKills(mvp) + "")
                            .replace("%s%", pManager.getKills(mvp) == 1 ? "" : "s").replace("%map%", world.getName())
                            .replace("%builders%", getBuilders(world)));
        for (Player player : world.getPlayers()) {
            if (pManager.getTeam(player).equals(winner)) {
                pManager.increment(player, "wins", 1);
            } else {
                pManager.increment(player, "losses", 1);
            }
            pManager.increment(player, "quits", -1);
            pManager.increment(player, "playtime",
                    System.currentTimeMillis() - Double.valueOf(getStatus(world).substring("ingame".length())));
            player.playSound(player.getLocation(), Sound.valueOf(CrystalWars.plugin.config.getString("Sounds.GameOver")), 2,
                    2);
            player.sendTitle(
                    MSG.color(MSG.getString("Game.Won.Top", "%teamColor%%teamName%").replace("%teamColor%", winColor)
                            .replace("%teamName%", MSG.camelCase(winner))),
                    MSG.color(MSG.getString("Game.Won.Bottom", "%teamColor% won the game")
                            .replace("%teamColor%", winColor).replace("%teamName%", MSG.camelCase(winner))));
        }
        stopGame(world);
    }

    public String getNamedStatus(World world) {
        ConfigurationSection section = CrystalWars.plugin.gui.getConfigurationSection("gameSelectionGui.Status");
        if (world == null) {
            return section.getString("Offline");
        } else if (getStatus(world).contains("ingame")) {
            return section.getString("InGame").replace("%players%", alivePlayers(world).size() + "");
        } else if (getStatus(world).contains("countdown")) {
            String time = new TimeManager()
                    .getRoundTimeMillis(Double.valueOf(getStatus(world).substring("countdown".length()))
                            - System.currentTimeMillis() + 1000);
            return "Starts in " + time;
        } else {
            return "Waiting (" + world.getPlayers().size() + "/"
                    + CrystalWars.plugin.data.getInt("Games." + world.getName() + ".maxSize") + ")";
        }
    }

    /**
     * Ends the game, use winGame to broadcast win message
     *
     * @param world World to stop
     */
    @SuppressWarnings("unchecked")
    public void stopGame(World world) {
        for (Player target : world.getPlayers()) {
            target.getEnderChest().clear();
            target.teleport(CrystalWars.plugin.getLocation("Games." + world.getName() + ".lobby"));
            target.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            target.getInventory().clear();
            target.getInventory().setArmorContents(null);
            for (Player player : world.getPlayers())
                player.showPlayer(target);
            target.setDisplayName(target.getName());
            target.setPlayerListName(target.getName());
            target.getInventory().addItem(pManager.parseItem(CrystalWars.plugin.gui, "gameSelectorItem", target));
        }
        setInfo(world, "status", "lobby");
        setInfo(world, "beginTimer", null);
        for (String team : getTeams(world)) {
            CrystalWars.plugin.data.set("Games." + world.getName() + ".teams." + team + ".members", null);
            // Deleting old data
            for (String res : new String[]{"crystal", "coalShop", "teamShop", "endShop"}) {
                CrystalWars.plugin.data.set("Games." + world.getName() + ".team" + res, null);
            }
        }
        if (getInfo(world, "removeEntities") != null)
            for (String uuid : ((List<String>) getInfo(world, "removeEntities"))) {
                Entity ent = getEntityByUUID(UUID.fromString(uuid), world);
                if (ent != null)
                    ent.remove();
            }
        reloadWorld(world);
        setInfo(world, "removeEntities", null);
        CrystalWars.plugin.data.set("Games." + world.getName() + ".removeEntities", null);
        CrystalWars.plugin.saveStats();
    }

    /**
     * Used for chest protection
     *
     * @param loc Location to compare
     * @return Returns what team's crystal is closest
     */
    public String getClosetCrystal(Location loc) {
        World world = loc.getWorld();
        List<String> teams = getTeams(world);
        if (teams == null)
            return null;
        double dist = 0.0;
        String closest = "";
        for (String res : teams) {
            double tempDist = CrystalWars.plugin.getLocation("Games." + world.getName() + ".teams." + res + ".crystal")
                    .distanceSquared(loc);
            if (tempDist < dist || dist == 0) {
                closest = res;
                dist = tempDist;
            }
        }
        return closest;
    }

    /**
     * Saves the world
     *
     * @param world World to save
     */
    public void saveWorld(World world) {
        world.save();
        world.setAutoSave(false);
    }

    /**
     * Reloads the world, kicking players or teleporting them to endLocaion (if
     * specified)
     *
     * @param world World to reload
     */
    public void reloadWorld(World world) {
        Location loc = CrystalWars.plugin.getLocation("Games." + world.getName() + ".endLocation");
        for (Player player : world.getPlayers()) {
            if (loc == null || loc.getWorld() == null)
                player.kickPlayer(MSG.getString("Game.Kicked", "Dead World"));
            player.teleport(loc);
        }
        Bukkit.unloadWorld(world, false);
        World tWorld = Bukkit.createWorld(WorldCreator.name(world.getName()));
        tWorld.setGameRuleValue("doMobSpawning", "false");
        tWorld.setGameRuleValue("doMobLoot", "false");
        for (Entity ent : tWorld.getEntities())
            ent.remove();
    }

    /**
     * Forces a game to crash
     *
     * @param world  World to stop game
     * @param reason Reason to stop world
     */
    @SuppressWarnings("unchecked")
    public void crashGame(World world, String reason) {
        setInfo(world, "status", "lobby");
        setInfo(world, "beginTimer", null);
        if (getInfo(world, "removeEntities") != null)
            for (String uuid : ((List<String>) getInfo(world, "removeEntities"))) {
                Entity ent = getEntityByUUID(UUID.fromString(uuid), world);
                if (ent == null)
                    continue;
                ent.remove();
            }
        for (String team : getTeams(world)) {
            CrystalWars.plugin.data.set("Games." + world.getName() + ".teams." + team + ".members", null);
            // Deleting old data
            for (String res : new String[]{"crystal", "coalShop", "teamShop", "endShop"}) {
                CrystalWars.plugin.data.set("Games." + world.getName() + ".team" + res, null);
            }
        }
        setInfo(world, "removeEntities", null);
        int currentLine = Thread.currentThread().getStackTrace()[2].getLineNumber();
        String fromClass = new Exception().getStackTrace()[1].getClassName();
        if (fromClass.contains("."))
            fromClass = fromClass.split("\\.")[fromClass.split("\\.").length - 1];
        MSG.tell(world, MSG.getString("Game.Error", "Error: %reason% %class%:%line%").replace("%reason%", reason)
                .replace("%class%", fromClass).replace("%line%", currentLine + ""));
        MSG.log(MSG.getString("Game.Error", "Error: %class%:%line%").replace("%reason%", reason)
                .replace("%class%", fromClass).replace("%line%", currentLine + "") + " (World: " + world.getName()
                + ")");
    }

    /**
     * Returns what entity has the UUID, if world is null then will search all
     * worlds
     *
     * @param uuid  UUID to search by
     * @param world World to search for UUID in, can be null
     * @return Entity by the UUID, if not found then null
     */
    public Entity getEntityByUUID(UUID uuid, World world) {
        if (world == null) {
            Entity ent = null;
            for (World tWorld : Bukkit.getWorlds()) {
                ent = getEntityByUUID(uuid, tWorld);
                if (ent != null)
                    return ent;
            }
            return null;
        }
        for (Entity ent : world.getEntities()) {
            if (ent.getUniqueId().equals(uuid))
                return ent;
        }
        return null;
    }

    public void startCountdown(World world, int startTime) {
        setInfo(world, "status", "countdown" + Math.max((System.currentTimeMillis() + (startTime * 1000)), 1000));
        setInfo(world, "lastPling", (double) System.currentTimeMillis());
    }
}
