package org.l2jmobius.gameserver.managers.clan;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanMember;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Clan Management Manager
 * Handles: Clan ranks, warehouse, academy, wars, points, shop, divisions, applications, invitations
 * @author Mobius Extended
 */
public class ClanManagementManager
{
    private static final Logger LOGGER = Logger.getLogger(ClanManagementManager.class.getName());

    private final Map<Integer, ClanManagementData> _clanData = new ConcurrentHashMap<>();
    private ScheduledFuture<?> _updateTask;

    public static ClanManagementManager getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private ClanManagementManager()
    {
        if (CustomSystemsConfig.CLAN_MANAGEMENT_ENABLED)
        {
            loadData();
            startUpdateTask();
            LOGGER.info(getClass().getSimpleName() + ": Clan Management System initialized.");
        }
    }

    public void reload()
    {
        if (_updateTask != null) _updateTask.cancel(false);
        _clanData.clear();
        loadData();
    }

    private void loadData()
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM clan_management_data");
             ResultSet rs = ps.executeQuery())
        {
            int count = 0;
            while (rs.next())
            {
                ClanManagementData data = new ClanManagementData();
                data.clanId = rs.getInt("clan_id");
                data.clanPoints = rs.getLong("clan_points");
                data.weeklyPoints = rs.getLong("weekly_points");
                data.seasonPoints = rs.getLong("season_points");
                data.warehouseSlots = rs.getInt("warehouse_slots");
                data.privateWarehouseSlots = rs.getInt("private_warehouse_slots");
                data.reputationShopPurchases = rs.getInt("reputation_shop_purchases");
                data.warWins = rs.getInt("war_wins");
                data.warLosses = rs.getInt("war_losses");
                data.territoryWins = rs.getInt("territory_wins");
                data.division = rs.getString("division");
                data.divisionPoints = rs.getLong("division_points");
                data.lastUpdate = rs.getLong("last_update");
                _clanData.put(data.clanId, data);
                count++;
            }
            LOGGER.info(getClass().getSimpleName() + ": Loaded " + count + " clan management records.");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to load clan management data: ", e);
        }
    }

    private void startUpdateTask()
    {
        ThreadPool.scheduleAtFixedRate(this::updateAllClanData, 300000, 300000);
    }

    private void updateAllClanData()
    {
        for (Clan clan : ClanTable.getInstance().getClans())
        {
            if (clan != null)
            {
                updateClanData(clan);
            }
        }
        saveAllClanData();
    }

    public void updateClanData(Clan clan)
    {
        if (clan == null) return;

        ClanManagementData data = _clanData.computeIfAbsent(clan.getClanId(), k -> new ClanManagementData(clan.getClanId()));
        
        data.clanPoints = clan.getReputationScore();
        data.memberCount = clan.getMembersCount();
        data.level = clan.getLevel();
        data.hasCastle = clan.getCastleId() > 0;
        data.hasFortress = clan.getFortressId() > 0;
        data.lastUpdate = System.currentTimeMillis();
    }

    // ===== CLAN POINTS SYSTEM =====
    public void addClanPoints(int clanId, long points)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.clanPoints += points;
        data.weeklyPoints += points;
        data.seasonPoints += points;
        data.lastUpdate = System.currentTimeMillis();
        saveClanData(clanId);
    }

    public void addWarWin(int clanId)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.warWins++;
        data.lastUpdate = System.currentTimeMillis();
        saveClanData(clanId);
    }

    public void addWarLoss(int clanId)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.warLosses++;
        data.lastUpdate = System.currentTimeMillis();
        saveClanData(clanId);
    }

    public void addTerritoryWin(int clanId)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.territoryWins++;
        data.lastUpdate = System.currentTimeMillis();
        saveClanData(clanId);
    }

    public void addReputationShopPurchase(int clanId)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.reputationShopPurchases++;
        data.lastUpdate = System.currentTimeMillis();
        saveClanData(clanId);
    }

    public void setDivision(int clanId, String division)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.division = division;
        data.lastUpdate = System.currentTimeMillis();
        saveClanData(clanId);
    }

    public void addDivisionPoints(int clanId, long points)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.divisionPoints += points;
        data.lastUpdate = System.currentTimeMillis();
        saveClanData(clanId);
    }

    // ===== CLAN WAREHOUSE =====
    public void upgradeWarehouse(int clanId, int slots)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.warehouseSlots += slots;
        saveClanData(clanId);
    }

    public void upgradePrivateWarehouse(int clanId, int slots)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.privateWarehouseSlots += slots;
        saveClanData(clanId);
    }

    // ===== CLAN SHOP =====
    public void purchaseFromReputationShop(int clanId, int itemId, int count, int reputationCost)
    {
        Clan clan = ClanTable.getInstance().getClan(clanId);
        if (clan == null || clan.getReputationScore() < reputationCost)
        {
            return;
        }

        clan.takeReputationScore(reputationCost, true);
        addReputationShopPurchase(clan.getClanId());
        
        // Give item to clan warehouse
        // Implementation would add item to clan warehouse
    }

    // ===== CLAN DIVISIONS =====
    public String getDivision(int clanId)
    {
        ClanManagementData data = _clanData.get(clanId);
        return data != null ? data.division : "LOWWAR";
    }

    public void updateDivision(int clanId)
    {
        ClanManagementData data = _clanData.get(clanId);
        if (data == null) return;

        if (data.divisionPoints >= CustomSystemsConfig.CLAN_DIVISION_BIGWAR_THRESHOLD)
        {
            data.division = "BIGWAR";
        }
        else if (data.divisionPoints >= CustomSystemsConfig.CLAN_DIVISION_MIDWAR_THRESHOLD)
        {
            data.division = "MIDWAR";
        }
        else
        {
            data.division = "LOWWAR";
        }
        saveClanData(clanId);
    }

    public void addDivisionPoints(int clanId, long points)
    {
        ClanManagementData data = _clanData.computeIfAbsent(clanId, k -> new ClanManagementData(clanId));
        data.divisionPoints += points;
        data.lastUpdate = System.currentTimeMillis();
        saveClanData(clanId);
        
        updateDivision(clanId);
    }

    // ===== CLAN WAR =====
    public void declareWar(int attackerClanId, int targetClanId)
    {
        // Implementation for war declaration
        // Would check requirements, start war timer, etc.
    }

    public void endWar(int clanId, boolean won)
    {
        if (won)
        {
            addWarWin(clanId);
        }
        else
        {
            addWarLoss(clanId);
        }
    }

    // ===== CLAN APPLICATIONS =====
    public void applyToClan(int playerId, int clanId)
    {
        // Store application in database
        // Notify clan leader
    }

    public void acceptApplication(int clanId, int playerId)
    {
        // Accept player into clan
        // Notify player
    }

    public void rejectApplication(int clanId, int playerId)
    {
        // Reject application
        // Notify player
    }

    // ===== CLAN INVITATIONS =====
    public void invitePlayer(int clanId, int playerId)
    {
        // Send invitation to player
        // Store invitation with expiry
    }

    public void acceptInvitation(int playerId, int clanId)
    {
        // Add player to clan
    }

    public void rejectInvitation(int playerId, int clanId)
    {
        // Reject invitation
    }

    // ===== CLAN POINTS REWARDS =====
    public void giveWeeklyRewards()
    {
        // Give rewards to top clans based on weekly points
    }

    public void giveSeasonalRewards()
    {
        // Give seasonal rewards
    }

    // ===== CLAN WAREHOUSE =====
    public void openClanWarehouse(Player player)
    {
        // Open clan warehouse for player
    }

    public void openPrivateWarehouse(Player player)
    {
        // Open private warehouse
    }

    // ===== CLAN STATISTICS =====
    public ClanManagementData getClanData(int clanId)
    {
        return _clanData.get(clanId);
    }

    public long getClanPoints(int clanId)
    {
        ClanManagementData data = _clanData.get(clanId);
        return data != null ? data.clanPoints : 0;
    }

    public long getWeeklyPoints(int clanId)
    {
        ClanManagementData data = _clanData.get(clanId);
        return data != null ? data.weeklyPoints : 0;
    }

    public String getDivision(int clanId)
    {
        ClanManagementData data = _clanData.get(clanId);
        return data != null ? data.division : "LOWWAR";
    }

    // ===== DATABASE OPERATIONS =====
    private void loadData()
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM clan_management_data");
             ResultSet rs = ps.executeQuery())
        {
            int count = 0;
            while (rs.next())
            {
                ClanManagementData data = new ClanManagementData();
                data.clanId = rs.getInt("clan_id");
                data.clanPoints = rs.getLong("clan_points");
                data.weeklyPoints = rs.getLong("weekly_points");
                data.seasonPoints = rs.getLong("season_points");
                data.warehouseSlots = rs.getInt("warehouse_slots");
                data.privateWarehouseSlots = rs.getInt("private_warehouse_slots");
                data.reputationShopPurchases = rs.getInt("reputation_shop_purchases");
                data.warWins = rs.getInt("war_wins");
                data.warLosses = rs.getInt("war_losses");
                data.territoryWins = rs.getInt("territory_wins");
                data.division = rs.getString("division");
                data.divisionPoints = rs.getLong("division_points");
                data.lastUpdate = rs.getLong("last_update");
                _clanData.put(data.clanId, data);
                count++;
            }
            LOGGER.info(getClass().getSimpleName() + ": Loaded " + count + " clan management records.");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to load clan management data: ", e);
        }
    }

    private void saveClanData(int clanId)
    {
        ThreadPool.execute(() ->
        {
            ClanManagementData data = _clanData.get(clanId);
            if (data == null) return;

            try (Connection con = DatabaseFactory.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO clan_management_data (clan_id, clan_points, weekly_points, season_points, " +
                     "warehouse_slots, private_warehouse_slots, reputation_shop_purchases, war_wins, war_losses, " +
                     "territory_wins, division, division_points, last_update) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE clan_points=?, weekly_points=?, season_points=?, " +
                     "warehouse_slots=?, private_warehouse_slots=?, reputation_shop_purchases=?, war_wins=?, " +
                     "war_losses=?, territory_wins=?, division=?, division_points=?, last_update=?"))
            {
                int idx = 1;
                ps.setInt(idx++, data.clanId);
                ps.setLong(idx++, data.clanPoints);
                ps.setLong(idx++, data.weeklyPoints);
                ps.setLong(idx++, data.seasonPoints);
                ps.setInt(idx++, data.warehouseSlots);
                ps.setInt(idx++, data.privateWarehouseSlots);
                ps.setInt(idx++, data.reputationShopPurchases);
                ps.setInt(idx++, data.warWins);
                ps.setInt(idx++, data.warLosses);
                ps.setInt(idx++, data.territoryWins);
                ps.setString(idx++, data.division);
                ps.setLong(idx++, data.divisionPoints);
                ps.setLong(idx++, data.lastUpdate);
                // Update
                ps.setLong(idx++, data.clanPoints);
                ps.setLong(idx++, data.weeklyPoints);
                ps.setLong(idx++, data.seasonPoints);
                ps.setInt(idx++, data.warehouseSlots);
                ps.setInt(idx++, data.privateWarehouseSlots);
                ps.setInt(idx++, data.reputationShopPurchases);
                ps.setInt(idx++, data.warWins);
                ps.setInt(idx++, data.warLosses);
                ps.setInt(idx++, data.territoryWins);
                ps.setString(idx++, data.division);
                ps.setLong(idx++, data.divisionPoints);
                ps.setLong(idx++, data.lastUpdate);
                ps.execute();
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING, "Failed to save clan data for clan " + clanId, e);
            }
        });
    }

    private void saveAllClanData()
    {
        ThreadPool.execute(() ->
        {
            for (ClanManagementData data : _clanData.values())
            {
                saveClanData(data.clanId);
            }
        });
    }

    public static class ClanManagementData
    {
        int clanId;
        long clanPoints = 0;
        long weeklyPoints = 0;
        long seasonPoints = 0;
        int warehouseSlots = 100;
        int privateWarehouseSlots = 50;
        int reputationShopPurchases = 0;
        int warWins = 0;
        int warLosses = 0;
        int territoryWins = 0;
        String division = "LOWWAR";
        long divisionPoints = 0;
        long lastUpdate = 0;

        ClanManagementData() {}
        ClanManagementData(int clanId) { this.clanId = clanId; }
    }

    private static class SingletonHolder
    {
        protected static final ClanManagementManager INSTANCE = new ClanManagementManager();
    }
}
