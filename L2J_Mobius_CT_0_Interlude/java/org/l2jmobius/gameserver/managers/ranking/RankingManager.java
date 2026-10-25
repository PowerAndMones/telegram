package org.l2jmobius.gameserver.managers.ranking;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ranking Manager - handles all server rankings
 * PvP, PK, Clan, Olympiad, Enchant, Raid Boss, Rich, Playtime
 * @author Mobius Extended
 */
public class RankingManager
{
    private static final Logger LOGGER = Logger.getLogger(RankingManager.class.getName());

    private final Map<Integer, RankingData> _playerRankings = new ConcurrentHashMap<>();
    private final Map<Integer, ClanRankingData> _clanRankings = new ConcurrentHashMap<>();
    private ScheduledFuture<?> _updateTask;
    private ScheduledFuture<?> _announceTask;

    public static RankingManager getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private RankingManager()
    {
        if (CustomSystemsConfig.RANKING_SYSTEM_ENABLED)
        {
            loadRankings();
            startUpdateTask();
            LOGGER.info(getClass().getSimpleName() + ": Ranking System initialized.");
        }
    }

    public void reload()
    {
        if (_updateTask != null) _updateTask.cancel(false);
        _playerRankings.clear();
        _clanRankings.clear();
        loadRankings();
    }

    private void loadRankings()
    {
        // Load player rankings
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM character_ranking_data");
             ResultSet rs = ps.executeQuery())
        {
            int count = 0;
            while (rs.next())
            {
                RankingData data = new RankingData();
                data.charId = rs.getInt("char_id");
                data.charName = rs.getString("char_name");
                data.classId = rs.getInt("class_id");
                data.level = rs.getInt("level");
                data.pvpKills = rs.getInt("pvp_kills");
                data.pkKills = rs.getInt("pk_kills");
                data.deaths = rs.getInt("deaths");
                data.olympiadWins = rs.getInt("olympiad_wins");
                data.olympiadLosses = rs.getInt("olympiad_losses");
                data.olympiadPoints = rs.getInt("olympiad_points");
                data.raidBossKills = rs.getInt("raid_boss_kills");
                data.epicBossKills = rs.getInt("epic_boss_kills");
                data.enchantSuccess = rs.getInt("enchant_success");
                data.enchantFailed = rs.getInt("enchant_failed");
                data.highestEnchant = rs.getInt("highest_enchant");
                data.totalAdena = rs.getLong("total_adena");
                data.playTime = rs.getLong("play_time");
                data.lastUpdate = rs.getLong("last_update");
                _playerRankings.put(data.charId, data);
                count++;
            }
            LOGGER.info(getClass().getSimpleName() + ": Loaded " + count + " player rankings.");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to load player rankings: ", e);
        }

        // Load clan rankings
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM clan_ranking_data");
             ResultSet rs = ps.executeQuery())
        {
            int count = 0;
            while (rs.next())
            {
                ClanRankingData data = new ClanRankingData();
                data.clanId = rs.getInt("clan_id");
                data.clanName = rs.getString("clan_name");
                data.level = rs.getInt("clan_level");
                data.reputation = rs.getInt("reputation_score");
                data.memberCount = rs.getInt("member_count");
                data.pvpKills = rs.getInt("pvp_kills");
                data.siegeWins = rs.getInt("siege_wins");
                data.siegeLosses = rs.getInt("siege_losses");
                data.territoryWins = rs.getInt("territory_wins");
                data.raidBossKills = rs.getInt("raid_boss_kills");
                data.epicBossKills = rs.getInt("epic_boss_kills");
                data.castleCount = rs.getInt("castle_count");
                data.fortressCount = rs.getInt("fortress_count");
                data.totalAdena = rs.getLong("total_adena");
                data.lastUpdate = rs.getLong("last_update");
                _clanRankings.put(data.clanId, data);
                count++;
            }
            LOGGER.info(getClass().getSimpleName() + ": Loaded " + count + " clan rankings.");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to load clan rankings: ", e);
        }
    }

    private void startUpdateTask()
    {
        // Update rankings every 5 minutes
        ThreadPool.scheduleAtFixedRate(this::updateAllRankings, 300000, 300000);
        
        // Announce top players every hour
        ThreadPool.scheduleAtFixedRate(this::announceTopPlayers, 3600000, 3600000);
    }

    private void updateAllRankings()
    {
        // Update player rankings from live data
        for (Player player : org.l2jmobius.gameserver.model.World.getInstance().getPlayers())
        {
            if (player != null && player.isOnline())
            {
                updatePlayerRanking(player);
            }
        }

        // Update clan rankings
        for (Clan clan : ClanTable.getInstance().getClans())
        {
            if (clan != null)
            {
                updateClanRanking(clan);
            }
        }

        // Save to database
        saveAllRankings();
        
        LOGGER.fine("Rankings updated.");
    }

    public void updatePlayerRanking(Player player)
    {
        if (player == null || !player.isOnline()) return;

        RankingData data = _playerRankings.computeIfAbsent(player.getObjectId(), k -> new RankingData(player));
        
        data.charName = player.getName();
        data.classId = player.getClassId().getId();
        data.level = player.getLevel();
        data.pvpKills = player.getPvpKills();
        data.pkKills = player.getPkKills();
        data.deaths = player.getDeaths(); // Would need custom tracking
        data.olympiadWins = getOlympiadWins(player.getObjectId());
        data.olympiadLosses = getOlympiadLosses(player.getObjectId());
        data.olympiadPoints = getOlympiadPoints(player.getObjectId());
        data.raidBossKills = getRaidBossKills(player.getObjectId());
        data.epicBossKills = getEpicBossKills(player.getObjectId());
        data.enchantSuccess = getEnchantSuccess(player.getObjectId());
        data.enchantFailed = getEnchantFailed(player.getObjectId());
        data.highestEnchant = getHighestEnchant(player.getObjectId());
        data.totalAdena = player.getAdena() + getWarehouseAdena(player.getObjectId());
        data.playTime = getPlayerPlayTime(player.getObjectId());
        data.lastUpdate = System.currentTimeMillis();
    }

    public void updateClanRanking(Clan clan)
    {
        if (clan == null) return;

        ClanRankingData data = _clanRankings.computeIfAbsent(clan.getClanId(), k -> new ClanRankingData(clan));
        
        data.clanName = clan.getName();
        data.level = clan.getLevel();
        data.reputation = clan.getReputationScore();
        data.memberCount = clan.getMembersCount();
        data.pvpKills = getClanPvPKills(clan.getClanId());
        data.siegeWins = getClanSiegeWins(clan.getClanId());
        data.siegeLosses = getClanSiegeLosses(clan.getClanId());
        data.territoryWins = getClanTerritoryWins(clan.getClanId());
        data.raidBossKills = getClanRaidBossKills(clan.getClanId());
        data.epicBossKills = getClanEpicBossKills(clan.getClanId());
        data.castleCount = clan.getCastleId() > 0 ? 1 : 0;
        data.fortressCount = clan.getFortressId() > 0 ? 1 : 0;
        data.totalAdena = getClanWarehouseAdena(clan.getClanId());
        data.lastUpdate = System.currentTimeMillis();
    }

    public List<RankingData> getTopPlayers(RankingType type, int limit)
    {
        List<RankingData> list = new ArrayList<>(_playerRankings.values());
        
        switch (type)
        {
            case PVP:
                list.sort(Comparator.comparingInt(r -> -r.pvpKills));
                break;
            case PK:
                list.sort(Comparator.comparingInt(r -> -r.pkKills));
                break;
            case LEVEL:
                list.sort(Comparator.comparingInt(r -> -r.level).thenComparingLong(r -> -r.playTime));
                break;
            case OLYMPIAD:
                list.sort(Comparator.comparingInt(r -> -r.olympiadPoints).thenComparingInt(r -> -r.olympiadWins));
                break;
            case RAID_BOSS:
                list.sort(Comparator.comparingInt(r -> -r.raidBossKills).thenComparingInt(r -> -r.epicBossKills));
                break;
            case ENCHANT:
                list.sort(Comparator.comparingInt(r -> -r.highestEnchant).thenComparingInt(r -> -r.enchantSuccess));
                break;
            case RICH:
                list.sort(Comparator.comparingLong(r -> -r.totalAdena));
                break;
            case PLAYTIME:
                list.sort(Comparator.comparingLong(r -> -r.playTime));
                break;
            case CLASS:
                // Will be filtered by class externally
                break;
        }
        
        return list.subList(0, Math.min(limit, list.size()));
    }

    public List<RankingData> getTopPlayersByClass(RankingType type, int classId, int limit)
    {
        List<RankingData> list = new ArrayList<>();
        for (RankingData data : _playerRankings.values())
        {
            if (data.classId == classId)
            {
                list.add(data);
            }
        }
        
        switch (type)
        {
            case PVP: list.sort(Comparator.comparingInt(r -> -r.pvpKills)); break;
            case OLYMPIAD: list.sort(Comparator.comparingInt(r -> -r.olympiadPoints)); break;
            case RAID_BOSS: list.sort(Comparator.comparingInt(r -> -r.raidBossKills)); break;
            case ENCHANT: list.sort(Comparator.comparingInt(r -> -r.highestEnchant)); break;
            case RICH: list.sort(Comparator.comparingLong(r -> -r.totalAdena)); break;
        }
        
        return list.subList(0, Math.min(limit, list.size()));
    }

    public List<ClanRankingData> getTopClans(ClanRankingType type, int limit)
    {
        List<ClanRankingData> list = new ArrayList<>(_clanRankings.values());
        
        switch (type)
        {
            case REPUTATION: list.sort(Comparator.comparingInt(c -> -c.reputation)); break;
            case PVP: list.sort(Comparator.comparingInt(c -> -c.pvpKills)); break;
            case SIEGE: list.sort(Comparator.comparingInt(c -> -c.siegeWins)); break;
            case RAID_BOSS: list.sort(Comparator.comparingInt(c -> -c.raidBossKills)); break;
            case LEVEL: list.sort(Comparator.comparingInt(c -> -c.level)); break;
            case MEMBERS: list.sort(Comparator.comparingInt(c -> -c.memberCount)); break;
            case RICH: list.sort(Comparator.comparingLong(c -> -c.totalAdena)); break;
            case CASTLES: list.sort(Comparator.comparingInt(c -> -c.castleCount).thenComparingInt(c -> -c.fortressCount)); break;
        }
        
        return list.subList(0, Math.min(limit, list.size()));
    }

    public RankingData getPlayerRanking(int charId)
    {
        return _playerRankings.get(charId);
    }

    public ClanRankingData getClanRanking(int clanId)
    {
        return _clanRankings.get(clanId);
    }

    public int getPlayerRank(int charId, RankingType type)
    {
        List<RankingData> list = getTopPlayers(type, _playerRankings.size());
        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i).charId == charId)
            {
                return i + 1;
            }
        }
        return -1;
    }

    public int getClanRank(int clanId, ClanRankingType type)
    {
        List<ClanRankingData> list = getTopClans(type, _clanRankings.size());
        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i).clanId == clanId)
            {
                return i + 1;
            }
        }
        return -1;
    }

    private void saveAllRankings()
    {
        ThreadPool.execute(() ->
        {
            // Save player rankings
            try (Connection con = DatabaseFactory.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO character_ranking_data (char_id, char_name, class_id, level, pvp_kills, pk_kills, deaths, " +
                     "olympiad_wins, olympiad_losses, olympiad_points, raid_boss_kills, epic_boss_kills, enchant_success, " +
                     "enchant_failed, highest_enchant, total_adena, play_time, last_update) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE char_name=?, class_id=?, level=?, pvp_kills=?, pk_kills=?, deaths=?, " +
                     "olympiad_wins=?, olympiad_losses=?, olympiad_points=?, raid_boss_kills=?, epic_boss_kills=?, " +
                     "enchant_success=?, enchant_failed=?, highest_enchant=?, total_adena=?, play_time=?, last_update=?"))
            {
                for (RankingData data : _playerRankings.values())
                {
                    int idx = 1;
                    ps.setInt(idx++, data.charId);
                    ps.setString(idx++, data.charName);
                    ps.setInt(idx++, data.classId);
                    ps.setInt(idx++, data.level);
                    ps.setInt(idx++, data.pvpKills);
                    ps.setInt(idx++, data.pkKills);
                    ps.setInt(idx++, data.deaths);
                    ps.setInt(idx++, data.olympiadWins);
                    ps.setInt(idx++, data.olympiadLosses);
                    ps.setInt(idx++, data.olympiadPoints);
                    ps.setInt(idx++, data.raidBossKills);
                    ps.setInt(idx++, data.epicBossKills);
                    ps.setInt(idx++, data.enchantSuccess);
                    ps.setInt(idx++, data.enchantFailed);
                    ps.setInt(idx++, data.highestEnchant);
                    ps.setLong(idx++, data.totalAdena);
                    ps.setLong(idx++, data.playTime);
                    ps.setLong(idx++, data.lastUpdate);
                    // Update
                    ps.setString(idx++, data.charName);
                    ps.setInt(idx++, data.classId);
                    ps.setInt(idx++, data.level);
                    ps.setInt(idx++, data.pvpKills);
                    ps.setInt(idx++, data.pkKills);
                    ps.setInt(idx++, data.deaths);
                    ps.setInt(idx++, data.olympiadWins);
                    ps.setInt(idx++, data.olympiadLosses);
                    ps.setInt(idx++, data.olympiadPoints);
                    ps.setInt(idx++, data.raidBossKills);
                    ps.setInt(idx++, data.epicBossKills);
                    ps.setInt(idx++, data.enchantSuccess);
                    ps.setInt(idx++, data.enchantFailed);
                    ps.setInt(idx++, data.highestEnchant);
                    ps.setLong(idx++, data.totalAdena);
                    ps.setLong(idx++, data.playTime);
                    ps.setLong(idx++, data.lastUpdate);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING, "Failed to save player rankings", e);
            }

            // Save clan rankings
            try (Connection con = DatabaseFactory.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO clan_ranking_data (clan_id, clan_name, clan_level, reputation_score, member_count, " +
                     "pvp_kills, siege_wins, siege_losses, territory_wins, raid_boss_kills, epic_boss_kills, " +
                     "castle_count, fortress_count, total_adena, last_update) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE clan_name=?, clan_level=?, reputation_score=?, member_count=?, " +
                     "pvp_kills=?, siege_wins=?, siege_losses=?, territory_wins=?, raid_boss_kills=?, " +
                     "epic_boss_kills=?, castle_count=?, fortress_count=?, total_adena=?, last_update=?"))
            {
                for (ClanRankingData data : _clanRankings.values())
                {
                    int idx = 1;
                    ps.setInt(idx++, data.clanId);
                    ps.setString(idx++, data.clanName);
                    ps.setInt(idx++, data.level);
                    ps.setInt(idx++, data.reputation);
                    ps.setInt(idx++, data.memberCount);
                    ps.setInt(idx++, data.pvpKills);
                    ps.setInt(idx++, data.siegeWins);
                    ps.setInt(idx++, data.siegeLosses);
                    ps.setInt(idx++, data.territoryWins);
                    ps.setInt(idx++, data.raidBossKills);
                    ps.setInt(idx++, data.epicBossKills);
                    ps.setInt(idx++, data.castleCount);
                    ps.setInt(idx++, data.fortressCount);
                    ps.setLong(idx++, data.totalAdena);
                    ps.setLong(idx++, data.lastUpdate);
                    // Update
                    ps.setString(idx++, data.clanName);
                    ps.setInt(idx++, data.level);
                    ps.setInt(idx++, data.reputation);
                    ps.setInt(idx++, data.memberCount);
                    ps.setInt(idx++, data.pvpKills);
                    ps.setInt(idx++, data.siegeWins);
                    ps.setInt(idx++, data.siegeLosses);
                    ps.setInt(idx++, data.territoryWins);
                    ps.setInt(idx++, data.raidBossKills);
                    ps.setInt(idx++, data.epicBossKills);
                    ps.setInt(idx++, data.castleCount);
                    ps.setInt(idx++, data.fortressCount);
                    ps.setLong(idx++, data.totalAdena);
                    ps.setLong(idx++, data.lastUpdate);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING, "Failed to save clan rankings", e);
            }
        });
    }

    private void announceTopPlayers()
    {
        // Announce top 3 PvP players
        List<RankingData> topPvP = getTopPlayers(RankingType.PVP, 3);
        if (!topPvP.isEmpty())
        {
            StringBuilder msg = new StringBuilder("🏆 Top PvP Killers: ");
            for (int i = 0; i < topPvP.size(); i++)
            {
                RankingData r = topPvP.get(i);
                msg.append(i + 1).append(". ").append(r.charName).append(" (").append(r.pvpKills).append(") ");
            }
            broadcastMessage(msg.toString());
        }
    }

    private void broadcastMessage(String msg)
    {
        for (Player p : org.l2jmobius.gameserver.model.World.getInstance().getPlayers())
        {
            if (p != null && p.isOnline())
            {
                p.sendPacket(new org.l2jmobius.gameserver.network.serverpackets.CreatureSay(0, 18, "Ranking System", msg));
            }
        }
    }

    // Helper methods (would query database in real implementation)
    private int getOlympiadWins(int charId) { return 0; }
    private int getOlympiadLosses(int charId) { return 0; }
    private int getOlympiadPoints(int charId) { return 0; }
    private int getRaidBossKills(int charId) { return 0; }
    private int getEpicBossKills(int charId) { return 0; }
    private int getEnchantSuccess(int charId) { return 0; }
    private int getEnchantFailed(int charId) { return 0; }
    private int getHighestEnchant(int charId) { return 0; }
    private long getWarehouseAdena(int charId) { return 0; }
    private long getPlayerPlayTime(int charId) { return 0; }
    private int getClanPvPKills(int clanId) { return 0; }
    private int getClanSiegeWins(int clanId) { return 0; }
    private int getClanSiegeLosses(int clanId) { return 0; }
    private int getClanTerritoryWins(int clanId) { return 0; }
    private int getClanRaidBossKills(int clanId) { return 0; }
    private int getClanEpicBossKills(int clanId) { return 0; }
    private long getClanWarehouseAdena(int clanId) { return 0; }

    public enum RankingType
    {
        PVP, PK, LEVEL, OLYMPIAD, RAID_BOSS, ENCHANT, RICH, PLAYTIME, CLASS
    }

    public enum ClanRankingType
    {
        REPUTATION, PVP, SIEGE, RAID_BOSS, LEVEL, MEMBERS, RICH, CASTLES
    }

    public static class RankingData
    {
        int charId;
        String charName;
        int classId;
        int level;
        int pvpKills;
        int pkKills;
        int deaths;
        int olympiadWins;
        int olympiadLosses;
        int olympiadPoints;
        int raidBossKills;
        int epicBossKills;
        int enchantSuccess;
        int enchantFailed;
        int highestEnchant;
        long totalAdena;
        long playTime;
        long lastUpdate;

        RankingData() {}
        RankingData(int charId) { this.charId = charId; }
    }

    public static class ClanRankingData
    {
        int clanId;
        String clanName;
        int level;
        int reputation;
        int memberCount;
        int pvpKills;
        int siegeWins;
        int siegeLosses;
        int territoryWins;
        int raidBossKills;
        int epicBossKills;
        int castleCount;
        int fortressCount;
        long totalAdena;
        long lastUpdate;

        ClanRankingData() {}
        ClanRankingData(int clanId) { this.clanId = clanId; }
    }

    private static class SingletonHolder
    {
        protected static final RankingManager INSTANCE = new RankingManager();
    }
}
