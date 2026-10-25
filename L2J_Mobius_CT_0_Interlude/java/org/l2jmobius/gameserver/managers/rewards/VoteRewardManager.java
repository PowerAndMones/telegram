package org.l2jmobius.gameserver.managers.rewards;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Vote Reward Manager
 * Supports: Hopzone, Topzone, L2Top, L2Network, etc.
 * @author Mobius Extended
 */
public class VoteRewardManager
{
    private static final Logger LOGGER = Logger.getLogger(VoteRewardManager.class.getName());

    private final Map<String, VoteSite> _voteSites = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, VoteData>> _playerVotes = new ConcurrentHashMap<>();
    private ScheduledFuture<?> _checkTask;

    // Default vote sites configuration
    private static final String[][] DEFAULT_SITES = {
        // name, api_url, reward_item, reward_count, cooldown_hours
        {"Hopzone", "https://api.hopzone.net/lineage2/vote/", "9991", "10", "12"},
        {"Topzone", "https://topzone.net/api/vote/", "9991", "10", "12"},
        {"L2Top", "https://l2top.co/api/vote/", "9991", "10", "12"},
        {"L2Network", "https://l2network.eu/api/vote/", "9991", "10", "12"},
    };

    public static VoteRewardManager getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private VoteRewardManager()
    {
        loadVoteSites();
        loadPlayerVotes();
        startVoteCheck();
    }

    public void reload()
    {
        if (_checkTask != null) _checkTask.cancel(false);
        _voteSites.clear();
        _playerVotes.clear();
        loadVoteSites();
        loadPlayerVotes();
        startVoteCheck();
    }

    private void loadVoteSites()
    {
        // Load from config file or use defaults
        for (String[] site : DEFAULT_SITES)
        {
            VoteSite vs = new VoteSite();
            vs.name = site[0];
            vs.apiUrl = site[1];
            vs.rewardItemId = Integer.parseInt(site[2]);
            vs.rewardCount = Integer.parseInt(site[3]);
            vs.cooldownHours = Integer.parseInt(site[4]);
            vs.enabled = true;
            _voteSites.put(vs.name.toLowerCase(), vs);
        }
        LOGGER.info(getClass().getSimpleName() + ": Loaded " + _voteSites.size() + " vote sites.");
    }

    private void loadPlayerVotes()
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM character_vote_rewards");
             ResultSet rs = ps.executeQuery())
        {
            int count = 0;
            while (rs.next())
            {
                VoteData data = new VoteData();
                data.charId = rs.getInt("char_id");
                data.siteName = rs.getString("site_name");
                data.lastVoteTime = rs.getLong("last_vote_time");
                data.totalVotes = rs.getInt("total_votes");
                data.rewardsClaimed = rs.getInt("rewards_claimed");
                
                _playerVotes.computeIfAbsent(data.charId, k -> new ConcurrentHashMap<>()).put(data.siteName.toLowerCase(), data);
                count++;
            }
            LOGGER.info(getClass().getSimpleName() + ": Loaded " + count + " player vote records.");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to load player votes: ", e);
        }
    }

    private void startVoteCheck()
    {
        // Check every 5 minutes
        _checkTask = ThreadPool.scheduleAtFixedRate(this::checkPendingVotes, 300000, 300000);
    }

    private void checkPendingVotes()
    {
        // In production, this would check vote site APIs for new votes
        // For now, we rely on callback URLs or manual claim
    }

    public void onVoteCallback(String siteName, String playerName, String ipAddress)
    {
        // Called by vote site callback URL
        // Verify IP matches player's IP
        final Player player = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(playerName);
        if (player != null && player.getIPAddress().equals(ipAddress))
        {
            recordVote(player, siteName);
        }
    }

    public void recordVote(Player player, String siteName)
    {
        VoteSite site = _voteSites.get(siteName.toLowerCase());
        if (site == null || !site.enabled) return;

        VoteData data = _playerVotes.computeIfAbsent(player.getObjectId(), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(siteName.toLowerCase(), k -> new VoteData(player.getObjectId(), siteName));

        long now = System.currentTimeMillis();
        long cooldownMs = site.cooldownHours * 3600000L;

        if (now - data.lastVoteTime < cooldownMs)
        {
            player.sendMessage("You can vote on " + site.name + " again in " + 
                ((cooldownMs - (now - data.lastVoteTime)) / 3600000) + " hours.");
            return;
        }

        data.lastVoteTime = now;
        data.totalVotes++;
        saveVoteData(data);
        
        player.sendMessage("Vote recorded on " + site.name + "! Use Alt+B -> Vote to claim rewards.");
    }

    public void claimVoteReward(Player player, String siteName)
    {
        VoteSite site = _voteSites.get(siteName.toLowerCase());
        if (site == null || !site.enabled) return;

        VoteData data = _playerVotes.getOrDefault(player.getObjectId(), new ConcurrentHashMap<>()).get(siteName.toLowerCase());
        if (data == null)
        {
            player.sendMessage("No votes recorded for " + site.name + ".");
            return;
        }

        if (data.rewardsClaimed >= data.totalVotes)
        {
            player.sendMessage("All rewards already claimed.");
            return;
        }

        // Give reward
        player.addItem("Vote Reward", site.rewardItemId, site.rewardCount, player, true);
        data.rewardsClaimed++;
        saveVoteData(data);
        
        player.sendMessage("Claimed " + site.rewardCount + "x " + 
            org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(site.rewardItemId).getName() + 
            " from " + site.name + "!");
    }

    public void claimAllRewards(Player player)
    {
        int totalClaimed = 0;
        for (Map.Entry<String, VoteData> entry : _playerVotes.getOrDefault(player.getObjectId(), new ConcurrentHashMap<>()).entrySet())
        {
            VoteData data = entry.getValue();
            VoteSite site = _voteSites.get(entry.getKey());
            if (site != null && site.enabled && data.rewardsClaimed < data.totalVotes)
            {
                int toClaim = data.totalVotes - data.rewardsClaimed;
                player.addItem("Vote Reward", site.rewardItemId, toClaim * site.rewardCount, player, true);
                data.rewardsClaimed = data.totalVotes;
                saveVoteData(data);
                totalClaimed += toClaim * site.rewardCount;
            }
        }
        
        if (totalClaimed > 0)
        {
            player.sendMessage("Claimed all pending vote rewards!");
        }
        else
        {
            player.sendMessage("No pending rewards to claim.");
        }
    }

    public void showVoteWindow(Player player)
    {
        StringBuilder html = new StringBuilder();
        html.append("<html><body><center><br>");
        html.append("<table width=550 bgcolor=0a0a1a>");
        html.append("<tr><td height=20></td></tr>");
        html.append("<tr><td align=center><font color=FFD700 size=3>🗳️ VOTE REWARDS</font></td></tr>");
        html.append("<tr><td height=10></td></tr>");
        
        html.append("<tr><td align=center><table border=0 cellpadding=5 cellspacing=0 width=500>");
        
        for (VoteSite site : _voteSites.values())
        {
            if (!site.enabled) continue;
            
            VoteData data = _playerVotes.getOrDefault(player.getObjectId(), new ConcurrentHashMap<>()).get(site.name.toLowerCase());
            int pending = (data != null) ? (data.totalVotes - data.rewardsClaimed) : 0;
            long lastVote = (data != null) ? data.lastVoteTime : 0;
            boolean canVote = (System.currentTimeMillis() - lastVote) >= (site.cooldownHours * 3600000L);
            
            html.append("<tr>");
            html.append("<td align=left width=150><font color=FFD700>").append(site.name).append("</font></td>");
            html.append("<td align=center width=100><font color=").append(canVote ? "00FF00" : "FF6347").append(">")
                .append(canVote ? "READY" : "COOLDOWN").append("</font></td>");
            html.append("<td align=center width=100><font color=CCCCCC>").append(data != null ? data.totalVotes : 0).append(" votes</font></td>");
            html.append("<td align=center width=100><font color=00FFFF>").append(pending).append(" pending</font></td>");
            html.append("<td align=center width=100>");
            if (canVote)
            {
                html.append("<button value=\"Vote\" action=\"bypass _bbsvote;").append(site.name.toLowerCase()).append("\" width=70 height=22 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\">");
            }
            html.append("</td>");
            html.append("</tr>");
        }
        html.append("</table></td></tr>");
        
        html.append("<tr><td height=10></td></tr>");
        html.append("<tr><td align=center><button value=\"Claim All\" action=\"bypass _bbsvoteclaimall\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td></tr>");
        html.append("</table></center></body></html>");
        
        player.sendPacket(new org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage(0, html.toString()));
    }

    private void saveVoteData(VoteData data)
    {
        ThreadPool.execute(() ->
        {
            try (Connection con = DatabaseFactory.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO character_vote_rewards (char_id, site_name, last_vote_time, total_votes, rewards_claimed) " +
                     "VALUES (?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE last_vote_time=?, total_votes=?, rewards_claimed=?"))
            {
                ps.setInt(1, data.charId);
                ps.setString(2, data.siteName);
                ps.setLong(3, data.lastVoteTime);
                ps.setInt(4, data.totalVotes);
                ps.setInt(5, data.rewardsClaimed);
                // Update
                ps.setLong(6, data.lastVoteTime);
                ps.setInt(7, data.totalVotes);
                ps.setInt(8, data.rewardsClaimed);
                ps.execute();
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING, "Failed to save vote data", e);
            }
        });
    }

    public static class VoteSite
    {
        String name;
        String apiUrl;
        int rewardItemId;
        int rewardCount;
        int cooldownHours;
        boolean enabled = true;
    }

    public static class VoteData
    {
        int charId;
        String siteName;
        long lastVoteTime = 0;
        int totalVotes = 0;
        int rewardsClaimed = 0;

        VoteData() {}
        VoteData(int charId, String siteName)
        {
            this.charId = charId;
            this.siteName = siteName;
        }
    }

    private static class SingletonHolder
    {
        protected static final VoteRewardManager INSTANCE = new VoteRewardManager();
    }
}
