package org.l2jmobius.gameserver.managers.rewards;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Daily Login Rewards Manager
 * @author Mobius Extended
 */
public class DailyRewardManager
{
    private static final Logger LOGGER = Logger.getLogger(DailyRewardManager.class.getName());

    private final Map<Integer, DailyRewardData> _playerRewards = new ConcurrentHashMap<>();
    private ScheduledFuture<?> _resetTask;

    // Rewards configuration
    private static final int[][] DAILY_REWARDS = {
        // day, itemId, count
        {1, 57, 50000},      // Day 1: 50k Adena
        {2, 728, 10},        // Day 2: 10 Mana Potions
        {3, 1540, 5},        // Day 3: 5 Greater CP Potions
        {4, 57, 100000},     // Day 4: 100k Adena
        {5, 1539, 10},       // Day 5: 10 CP Potions
        {6, 729, 1},         // Day 6: 1 Enchant Weapon S
        {7, 6651, 5},        // Day 7: 5 Noblesse Stones (BIG REWARD)
    };

    private static final int[][] PREMIUM_REWARDS = {
        {1, 9990, 2},        // 2 PvP Coins
        {2, 9991, 3},        // 3 Event Coins
        {3, 9992, 1},        // 1 Boss Coin
        {4, 9990, 5},        // 5 PvP Coins
        {5, 9991, 5},        // 5 Event Coins
        {6, 9993, 1},        // 1 Premium Coin
        {7, 9993, 3},        // 3 Premium Coins (BIG REWARD)
    };

    public static DailyRewardManager getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private DailyRewardManager()
    {
        load();
        startResetTask();
    }

    public void reload()
    {
        if (_resetTask != null) _resetTask.cancel(false);
        _playerRewards.clear();
        load();
        startResetTask();
    }

    private void load()
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM character_daily_rewards");
             ResultSet rs = ps.executeQuery())
        {
            int count = 0;
            while (rs.next())
            {
                DailyRewardData data = new DailyRewardData();
                data.charId = rs.getInt("char_id");
                data.lastRewardDay = rs.getInt("last_reward_day");
                data.streakDays = rs.getInt("streak_days");
                data.totalRewards = rs.getInt("total_rewards");
                data.premiumStreak = rs.getInt("premium_streak");
                _playerRewards.put(data.charId, data);
                count++;
            }
            LOGGER.info(getClass().getSimpleName() + ": Loaded " + count + " daily reward records.");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to load daily rewards: ", e);
        }
    }

    private void startResetTask()
    {
        // Reset at 06:30 server time daily
        _resetTask = ThreadPool.scheduleAtFixedRate(this::checkDailyReset, 60000, 60000);
    }

    private void checkDailyReset()
    {
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) == 6 && cal.get(java.util.Calendar.MINUTE) == 30)
        {
            // New day - reset streak tracking for offline players
            // Online players will be handled on login
        }
    }

    public void onPlayerLogin(Player player)
    {
        if (player == null) return;

        DailyRewardData data = _playerRewards.computeIfAbsent(player.getObjectId(), k -> new DailyRewardData(player.getObjectId()));
        
        final int currentDay = getCurrentDayNumber();
        
        // Check if already claimed today
        if (data.lastRewardDay == currentDay)
        {
            player.sendMessage("Daily reward already claimed today. Next reward in " + getTimeUntilReset() + ".");
            return;
        }

        // Calculate streak
        int yesterday = currentDay - 1;
        if (data.lastRewardDay == yesterday)
        {
            data.streakDays++;
        }
        else if (data.lastRewardDay < yesterday)
        {
            data.streakDays = 1; // Streak broken
        }
        
        // Cap at 7 days (weekly cycle)
        if (data.streakDays > 7) data.streakDays = 1;
        
        data.lastRewardDay = currentDay;
        data.totalRewards++;
        
        // Give daily reward
        int dayIndex = (data.streakDays - 1) % 7;
        int itemId = DAILY_REWARDS[dayIndex][1];
        int count = DAILY_REWARDS[dayIndex][2];
        
        player.addItem("Daily Reward", itemId, count, player, true);
        player.sendMessage("Daily reward Day " + data.streakDays + ": Received " + count + "x " + ItemData.getInstance().getTemplate(itemId).getName() + "!");

        // Premium bonus
        if (CustomSystemsConfig.PREMIUM_EXTENDED_ENABLED && 
            org.l2jmobius.gameserver.managers.PremiumBattlePassManager.getInstance().isPremiumActive(player.getAccountName()))
        {
            int pItemId = PREMIUM_REWARDS[dayIndex][1];
            int pCount = PREMIUM_REWARDS[dayIndex][2];
            player.addItem("Premium Daily Bonus", pItemId, pCount, player, true);
            player.sendMessage("Premium bonus: " + pCount + "x " + ItemData.getInstance().getTemplate(pItemId).getName() + "!");
            
            data.premiumStreak++;
        }

        savePlayerData(data);
        
        // Show reward window
        showDailyRewardWindow(player, data);
    }

    public void claimDailyReward(Player player)
    {
        // Manual claim via UI
        onPlayerLogin(player);
    }

    private void showDailyRewardWindow(Player player, DailyRewardData data)
    {
        StringBuilder html = new StringBuilder();
        html.append("<html><body><center><br>");
        html.append("<table width=550 bgcolor=0a0a1a>");
        html.append("<tr><td height=20></td></tr>");
        html.append("<tr><td align=center><font color=FFD700 size=3>🎁 DAILY REWARD</font></td></tr>");
        html.append("<tr><td height=10></td></tr>");
        html.append("<tr><td align=center><font color=00FFFF>Day ").append(data.streakDays).append(" of 7</font></td></tr>");
        html.append("<tr><td height=10></td></tr>");
        
        // Show next 7 days
        html.append("<tr><td align=center><table border=0 cellpadding=3 cellspacing=0 width=500>");
        for (int i = 0; i < 7; i++)
        {
            int itemId = DAILY_REWARDS[i][1];
            int count = DAILY_REWARDS[i][2];
            String itemName = ItemData.getInstance().getTemplate(itemId).getName();
            
            html.append("<tr>");
            if (i + 1 == data.streakDays)
            {
                html.append("<td align=center width=70><font color=FFD700>DAY ").append(i + 1).append("</font></td>");
                html.append("<td align=left width=200><font color=FFD700>").append(count).append("x ").append(itemName).append(" ✓</font></td>");
            }
            else if (i + 1 < data.streakDays)
            {
                html.append("<td align=center width=70><font color=888888>DAY ").append(i + 1).append("</font></td>");
                html.append("<td align=left width=200><font color=888888>").append(count).append("x ").append(itemName).append(" ✓</font></td>");
            }
            else
            {
                html.append("<td align=center width=70><font color=CCCCCC>DAY ").append(i + 1).append("</font></td>");
                html.append("<td align=left width=200><font color=CCCCCC>").append(count).append("x ").append(itemName).append("</font></td>");
            }
            html.append("</tr>");
        }
        html.append("</table></td></tr>");
        
        html.append("<tr><td height=10></td></tr>");
        html.append("<tr><td align=center><font color=FFA500>Streak: ").append(data.streakDays).append("/7 | Total claimed: ").append(data.totalRewards).append("</font></td></tr>");
        html.append("<tr><td height=20></td></tr>");
        html.append("<tr><td align=center><button value=\"Claim\" action=\"bypass _bbsdailyclaim\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td></tr>");
        html.append("</table></center></body></html>");
        
        player.sendPacket(new NpcHtmlMessage(0, html.toString()));
    }

    private int getCurrentDayNumber()
    {
        return (int) (System.currentTimeMillis() / 86400000);
    }

    private String getTimeUntilReset()
    {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 6);
        cal.set(java.util.Calendar.MINUTE, 30);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        
        long diff = cal.getTimeInMillis() - System.currentTimeMillis();
        long hours = diff / 3600000;
        long minutes = (diff % 3600000) / 60000;
        return hours + "h " + minutes + "m";
    }

    private void savePlayerData(DailyRewardData data)
    {
        ThreadPool.execute(() ->
        {
            try (Connection con = DatabaseFactory.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO character_daily_rewards (char_id, last_reward_day, streak_days, total_rewards, premium_streak) " +
                     "VALUES (?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE last_reward_day=?, streak_days=?, total_rewards=?, premium_streak=?"))
            {
                ps.setInt(1, data.charId);
                ps.setInt(2, data.lastRewardDay);
                ps.setInt(3, data.streakDays);
                ps.setInt(4, data.totalRewards);
                ps.setInt(5, data.premiumStreak);
                // Update
                ps.setInt(6, data.lastRewardDay);
                ps.setInt(7, data.streakDays);
                ps.setInt(8, data.totalRewards);
                ps.setInt(9, data.premiumStreak);
                ps.execute();
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING, "Failed to save daily reward data", e);
            }
        });
    }

    public static class DailyRewardData
    {
        int charId;
        int lastRewardDay = -1;
        int streakDays = 0;
        int totalRewards = 0;
        int premiumStreak = 0;

        DailyRewardData() {}
        DailyRewardData(int charId) { this.charId = charId; }
    }

    private static class SingletonHolder
    {
        protected static final DailyRewardManager INSTANCE = new DailyRewardManager();
    }
}
