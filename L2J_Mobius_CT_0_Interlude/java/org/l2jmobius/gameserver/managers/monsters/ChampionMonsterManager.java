package org.l2jmobius.gameserver.managers.monsters;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.util.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Champion Monster Manager
 * Handles special champion monsters with increased stats and rewards
 * @author Mobius Extended
 */
public class ChampionMonsterManager
{
    private static final Logger LOGGER = Logger.getLogger(ChampionMonsterManager.class.getName());

    private final Map<Integer, ChampionSpawn> _championSpawns = new ConcurrentHashMap<>();
    private final Map<Integer, List<Integer>> _activeChampions = new ConcurrentHashMap<>();
    private ScheduledFuture<?> _spawnTask;
    private ScheduledFuture<?> _announcementTask;

    public static ChampionMonsterManager getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private ChampionMonsterManager()
    {
        if (CustomSystemsConfig.CHAMPION_MONSTERS_ENABLED)
        {
            loadChampionSpawns();
            startSpawnTask();
            startAnnouncements();
            LOGGER.info(getClass().getSimpleName() + ": Champion Monster Manager started with " + _championSpawns.size() + " spawns.");
        }
    }

    public void reload()
    {
        if (_spawnTask != null) _spawnTask.cancel(false);
        if (_announcementTask != null) _announcementTask.cancel(false);
        _championSpawns.clear();
        _activeChampions.clear();
        loadChampionSpawns();
        startSpawnTask();
        startAnnouncements();
    }

    private void loadChampionSpawns()
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM champion_monster_spawns WHERE is_active = 1");
             ResultSet rs = ps.executeQuery())
        {
            int count = 0;
            while (rs.next())
            {
                ChampionSpawn spawn = new ChampionSpawn();
                spawn.id = rs.getInt("id");
                spawn.npcId = rs.getInt("npc_id");
                spawn.name = rs.getString("name");
                spawn.tier = rs.getString("tier");
                spawn.x = rs.getInt("x");
                spawn.y = rs.getInt("y");
                spawn.z = rs.getInt("z");
                spawn.heading = rs.getInt("heading");
                spawn.respawnMin = rs.getInt("respawn_min");
                spawn.respawnMax = rs.getInt("respawn_max");
                spawn.rewardAdenaMultiplier = rs.getDouble("reward_adena_multiplier");
                spawn.rewardDropMultiplier = rs.getDouble("reward_drop_multiplier");
                _championSpawns.put(spawn.id, spawn);
                count++;
            }
            LOGGER.info(getClass().getSimpleName() + ": Loaded " + count + " champion monster spawns.");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to load champion monster spawns: ", e);
        }
    }

    private void startSpawnTask()
    {
        // Check every minute for spawns
        _spawnTask = ThreadPool.scheduleAtFixedRate(this::checkSpawns, 60000, 60000);
    }

    private void checkSpawns()
    {
        for (ChampionSpawn spawn : _championSpawns.values())
        {
            List<Integer> active = _activeChampions.get(spawn.id);
            int activeCount = active != null ? active.size() : 0;
            
            // Only spawn 1 per location at a time
            if (activeCount == 0)
            {
                // Check if it's time to spawn
                ChampionSpawnData data = getSpawnData(spawn.id);
                if (data == null || System.currentTimeMillis() >= data.nextSpawnTime)
                {
                    spawnChampion(spawn);
                }
            }
        }
    }

    private void spawnChampion(ChampionSpawn spawn)
    {
        try
        {
            final Monster champion = (Monster) org.l2jmobius.gameserver.model.spawns.Spawn.spawnMonster(spawn.npcId, spawn.x, spawn.y, spawn.z, spawn.heading, false, 0, false);
            if (champion == null) return;

            // Apply champion buffs
            applyChampionBuffs(champion, spawn);
            
            // Track
            _activeChampions.computeIfAbsent(spawn.id, k -> Collections.synchronizedList(new ArrayList<>())).add(champion.getObjectId());
            
            // Announce
            announceChampionSpawn(spawn);
            
            // Schedule despawn check
            ThreadPool.schedule(() -> checkChampionAlive(champion, spawn), 60000);
            
            LOGGER.info("Spawned champion: " + spawn.name + " (" + spawn.tier + ") at " + spawn.x + "," + spawn.y + "," + spawn.z);
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to spawn champion: " + spawn.name, e);
        }
    }

    private void applyChampionBuffs(Monster champion, ChampionSpawn spawn)
    {
        // Visual effect
        champion.broadcastPacket(new MagicSkillUse(champion, champion, 2025, 1, 1000, 0));
        
        // Title
        champion.setTitle("[" + spawn.tier + "] " + spawn.name);
        champion.broadcastTitleInfo();
        
        // Store original stats for reward calculation
        champion.setScriptValue("champion_tier", spawn.tier);
        champion.setScriptValue("reward_adena_mult", String.valueOf(spawn.rewardAdenaMultiplier));
        champion.setScriptValue("reward_drop_mult", String.valueOf(spawn.rewardDropMultiplier));
    }

    private void checkChampionAlive(Monster champion, ChampionSpawn spawn)
    {
        if (champion == null || champion.isDead())
        {
            // Champion died - announce and schedule respawn
            onChampionDeath(champion, spawn);
            return;
        }
        
        // Check again in 1 minute
        ThreadPool.schedule(() -> checkChampionAlive(champion, spawn), 60000);
    }

    private void onChampionDeath(Monster champion, ChampionSpawn spawn)
    {
        // Remove from active
        List<Integer> active = _activeChampions.get(spawn.id);
        if (active != null)
        {
            active.remove(Integer.valueOf(champion.getObjectId()));
        }

        // Schedule respawn
        ChampionSpawnData data = getSpawnData(spawn.id);
        if (data == null)
        {
            data = new ChampionSpawnData();
            data.spawnId = spawn.id;
            _spawnData.put(spawn.id, data);
        }
        
        long respawnTime = spawn.respawnMin + (long) (Math.random() * (spawn.respawnMax - spawn.respawnMin));
        data.nextSpawnTime = System.currentTimeMillis() + (respawnTime * 60000);
        saveSpawnData(data);

        // Announce death
        announceChampionDeath(spawn);
    }

    private void announceChampionSpawn(ChampionSpawn spawn)
    {
        final String msg = "Champion Monster [" + spawn.tier + "] " + spawn.name + " has spawned! Location: " + getLocationName(spawn.x, spawn.y);
        broadcastMessage(msg);
    }

    private void announceChampionDeath(ChampionSpawn spawn)
    {
        final String msg = "Champion Monster [" + spawn.tier + "] " + spawn.name + " has been defeated!";
        broadcastMessage(msg);
    }

    private String getLocationName(int x, int y)
    {
        // Simplified location names
        if (x > 140000) return "Aden / Dragon Valley";
        if (x > 100000) return "Giran / Oren";
        if (x > 80000) return "Dion / Gludio";
        if (x > 40000) return "Rune / Goddard";
        if (x > 0) return "Talking Island / Elven Village";
        if (x > -40000) return "Orc Village / Dwarven Village";
        return "Unknown";
    }

    private void broadcastMessage(String msg)
    {
        for (Player p : org.l2jmobius.gameserver.model.World.getInstance().getPlayers())
        {
            if (p != null && p.isOnline())
            {
                p.sendPacket(new CreatureSay(0, 18, "Champion System", msg));
                p.sendPacket(new ExShowScreenMessage(msg, 10000));
            }
        }
    }

    // Reward handling - called from monster death
    public void onChampionKilled(Monster champion, Player killer)
    {
        String tier = champion.getScriptValue("champion_tier");
        String adenaMultStr = champion.getScriptValue("reward_adena_mult");
        String dropMultStr = champion.getScriptValue("reward_drop_mult");
        
        double adenaMult = 1.0;
        double dropMult = 1.0;
        
        try { adenaMult = Double.parseDouble(adenaMultStr); } catch (Exception e) {}
        try { dropMult = Double.parseDouble(dropMultStr); } catch (Exception e) {}
        
        // Bonus rewards
        if (killer != null && adenaMult > 1.0)
        {
            long bonusAdena = (long) (100000 * adenaMult); // Base 100k * multiplier
            killer.addAdena("Champion Bonus", bonusAdena, null, true);
            killer.sendMessage("Champion bonus: " + Util.formatAdena(bonusAdena) + " Adena!");
        }
        
        // Event coins
        if (killer != null)
        {
            org.l2jmobius.gameserver.managers.rewards.CurrencyManager.getInstance().addEventCoins(killer, 5);
        }
    }

    private ChampionSpawnData getSpawnData(int spawnId)
    {
        return _spawnData.get(spawnId);
    }

    private void saveSpawnData(ChampionSpawnData data)
    {
        ThreadPool.execute(() ->
        {
            try (Connection con = DatabaseFactory.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO champion_spawn_data (spawn_id, next_spawn_time) VALUES (?,?) ON DUPLICATE KEY UPDATE next_spawn_time=?"))
            {
                ps.setInt(1, data.spawnId);
                ps.setLong(2, data.nextSpawnTime);
                ps.setLong(3, data.nextSpawnTime);
                ps.execute();
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING, "Failed to save champion spawn data", e);
            }
        });
    }

    private final Map<Integer, ChampionSpawnData> _spawnData = new ConcurrentHashMap<>();

    public static class ChampionSpawn
    {
        int id;
        int npcId;
        String name;
        String tier;
        int x, y, z, heading;
        int respawnMin;
        int respawnMax;
        double rewardAdenaMultiplier;
        double rewardDropMultiplier;
    }

    private static class ChampionSpawnData
    {
        int spawnId;
        long nextSpawnTime = 0;
    }

    private static class SingletonHolder
    {
        protected static final ChampionMonsterManager INSTANCE = new ChampionMonsterManager();
    }
}
