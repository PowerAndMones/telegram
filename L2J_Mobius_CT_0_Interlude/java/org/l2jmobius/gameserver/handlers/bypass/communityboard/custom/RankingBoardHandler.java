package org.l2jmobius.gameserver.handlers.bypass.communityboard.custom;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.managers.ranking.RankingManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ranking Board Handler for Community Board
 * @author Mobius Extended
 */
public class RankingBoardHandler implements IParseBoardHandler
{
    private static final Logger LOGGER = Logger.getLogger(RankingBoardHandler.class.getName());

    private static final String[] COMMANDS =
    {
        "_bbsranking",
        "_bbsranking_pvp",
        "_bbsranking_pk",
        "_bbsranking_level",
        "_bbsranking_olympiad",
        "_bbsranking_raid",
        "_bbsranking_enchant",
        "_bbsranking_rich",
        "_bbsranking_playtime",
        "_bbsranking_class",
        "_bbsranking_clan",
        "_bbsranking_clan_rep",
        "_bbsranking_clan_pvp",
        "_bbsranking_clan_siege",
        "_bbsranking_clan_raid",
        "_bbsranking_me"
    };

    @Override
    public String[] getCommandList()
    {
        return COMMANDS;
    }

    @Override
    public boolean onCommand(String command, Player player)
    {
        if (!CustomSystemsConfig.RANKING_SYSTEM_ENABLED)
        {
            player.sendMessage("Ranking system is disabled.");
            return false;
        }

        String html = null;

        if (command.equals("_bbsranking") || command.equals("_bbsranking;main"))
        {
            html = generateMainRankingPage(player);
        }
        else if (command.startsWith("_bbsranking_"))
        {
            String type = command.substring("_bbsranking_".length());
            html = generateRankingPage(player, type);
        }

        if (html != null)
        {
            CommunityBoardHandler.separateAndSend(html, player);
        }
        return false;
    }

    private String generateMainRankingPage(Player player)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/ranking/main.html");
        if (html == null)
        {
            html = generateDefaultMainPage(player);
        }
        
        // Replace placeholders
        html = html.replace("%navigation%", HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/navigation.html"));
        html = html.replace("%player_name%", player.getName());
        
        // Get player's ranks
        RankingManager rm = RankingManager.getInstance();
        int pvpRank = rm.getPlayerRank(player.getObjectId(), RankingManager.RankingType.PVP);
        int pkRank = rm.getPlayerRank(player.getObjectId(), RankingManager.RankingType.PK);
        int levelRank = rm.getPlayerRank(player.getObjectId(), RankingManager.RankingType.LEVEL);
        int olyRank = rm.getPlayerRank(player.getObjectId(), RankingManager.RankingType.OLYMPIAD);
        
        html = html.replace("%my_pvp_rank%", pvpRank > 0 ? "#" + pvpRank : "Unranked");
        html = html.replace("%my_pk_rank%", pkRank > 0 ? "#" + pkRank : "Unranked");
        html = html.replace("%my_level_rank%", levelRank > 0 ? "#" + levelRank : "Unranked");
        html = html.replace("%my_oly_rank%", olyRank > 0 ? "#" + olyRank : "Unranked");
        
        return html;
    }

    private String generateDefaultMainPage(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<table width=700><tr><td height=10></td></tr></table>");
        sb.append("<table width=20><tr><td>%navigation%</td><td><center>");
        sb.append("<table width=550 bgcolor=\"0a0a1a\" cellpadding=0 cellspacing=0>");
        sb.append("<tr><td height=15></td></tr>");
        sb.append("<tr><td align=center height=50>");
        sb.append("<font color=\"FFD700\" size=3>🏆 RANKINGS & STATISTICS</font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=5></td></tr>");
        sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<font color=CCCCCC>Your Current Ranks:</font><br>");
        sb.append("<table border=0 cellpadding=5 cellspacing=0 width=500>");
        sb.append("<tr><td align=left width=250><font color=00FFFF>⚔️ PvP Rank:</font></td>");
        sb.append("<td align=right width=250><font color=FFD700>%my_pvp_rank%</font></td></tr>");
        sb.append("<tr><td align=left width=250><font color=FF6347>🩸 PK Rank:</font></td>");
        sb.append("<td align=right width=250><font color=FFD700>%my_pk_rank%</font></td></tr>");
        sb.append("<tr><td align=left width=250><font color=00FF00>📊 Level Rank:</font></td>");
        sb.append("<td align=right width=250><font color=FFD700>%my_level_rank%</font></td></tr>");
        sb.append("<tr><td align=left width=250><font color=FFA500>🏅 Olympiad Rank:</font></td>");
        sb.append("<td align=right width=250><font color=FFD700>%my_oly_rank%</font></td></tr>");
        sb.append("</table>");
        sb.append("</td></tr>");

        sb.append("<tr><td height=20></td></tr>");
        sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<font color=FFD700 size=2>📊 PLAYER RANKINGS</font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<table border=0 cellpadding=5 cellspacing=0 width=520>");
        sb.append("<tr>");
        sb.append("<td align=center width=130><button value=\"⚔️ PvP Kills\" action=\"bypass _bbsranking_pvp\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"🩸 PK Kills\" action=\"bypass _bbsranking_pk\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"📊 Level\" action=\"bypass _bbsranking_level\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"🏅 Olympiad\" action=\"bypass _bbsranking_olympiad\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td align=center width=130><button value=\"👑 Raid Boss\" action=\"bypass _bbsranking_raid\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"✨ Enchant\" action=\"bypass _bbsranking_enchant\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"💰 Richest\" action=\"bypass _bbsranking_rich\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"⏱️ Playtime\" action=\"bypass _bbsranking_playtime\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td align=center width=130><button value=\"🎭 By Class\" action=\"bypass _bbsranking_class\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130></td>");
        sb.append("<td align=center width=130></td>");
        sb.append("<td align=center width=130></td>");
        sb.append("</tr>");
        sb.append("</table>");
        sb.append("</td></tr>");

        sb.append("<tr><td height=20></td></tr>");
        sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<font color=FFD700 size=2>🏰 CLAN RANKINGS</font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<table border=0 cellpadding=5 cellspacing=0 width=520>");
        sb.append("<tr>");
        sb.append("<td align=center width=130><button value=\"🏆 Reputation\" action=\"bypass _bbsranking_clan_rep\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"⚔️ PvP Kills\" action=\"bypass _bbsranking_clan_pvp\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"🏰 Sieges\" action=\"bypass _bbsranking_clan_siege\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"👑 Raid Boss\" action=\"bypass _bbsranking_clan_raid\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("</tr>");
        sb.append("</table>");
        sb.append("</td></tr>");

        sb.append("<tr><td height=20></td></tr>");
        sb.append("</table>");
        sb.append("<table border=0 cellpadding=0 cellspacing=0 width=550>");
        sb.append("<tr><td height=30 align=center><font color=888888><br>LINEAGE II PvP x1000 - COMMUNITY BOARD</font></td></tr>");
        sb.append("</table></center></td></tr></table></body></html>");

        return sb.toString();
    }

    private String generateRankingPage(Player player, String type)
    {
        RankingManager rm = RankingManager.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<table width=700><tr><td height=10></td></tr></table>");
        sb.append("<table width=20><tr><td>%navigation%</td><td><center>");
        sb.append("<table width=550 bgcolor=\"0a0a1a\" cellpadding=0 cellspacing=0>");
        sb.append("<tr><td height=15></td></tr>");

        String title = "";
        List<RankingManager.RankingData> players = null;
        List<RankingManager.ClanRankingData> clans = null;

        switch (type.toLowerCase())
        {
            case "pvp":
                players = RankingManager.getInstance().getTopPlayers(RankingManager.RankingType.PVP, 50);
                title = "⚔️ TOP PVP KILLERS";
                break;
            case "pk":
                players = RankingManager.getInstance().getTopPlayers(RankingManager.RankingType.PK, 50);
                title = "🩸 TOP PK KILLERS";
                break;
            case "level":
                players = RankingManager.getInstance().getTopPlayers(RankingManager.RankingType.LEVEL, 50);
                title = "📊 TOP LEVELS";
                break;
            case "olympiad":
                players = RankingManager.getInstance().getTopPlayers(RankingManager.RankingType.OLYMPIAD, 50);
                title = "🏅 OLYMPIAD RANKING";
                break;
            case "raid":
                players = RankingManager.getInstance().getTopPlayers(RankingManager.RankingType.RAID_BOSS, 50);
                title = "👑 RAID BOSS KILLERS";
                break;
            case "enchant":
                players = RankingManager.getInstance().getTopPlayers(RankingManager.RankingType.ENCHANT, 50);
                title = "✨ ENCHANT MASTERS";
                break;
            case "rich":
                players = RankingManager.getInstance().getTopPlayers(RankingManager.RankingType.RICH, 50);
                title = "💰 RICHEST PLAYERS";
                break;
            case "playtime":
                players = RankingManager.getInstance().getTopPlayers(RankingManager.RankingType.PLAYTIME, 50);
                title = "⏱️ MOST PLAYTIME";
                break;
            case "clan_rep":
                clans = RankingManager.getInstance().getTopClans(RankingManager.ClanRankingType.REPUTATION, 50);
                title = "🏆 CLAN REPUTATION";
                break;
            case "clan_pvp":
                clans = RankingManager.getInstance().getTopClans(RankingManager.ClanRankingType.PVP, 50);
                title = "⚔️ CLAN PVP";
                break;
            case "clan_siege":
                clans = RankingManager.getInstance().getTopClans(RankingManager.ClanRankingType.SIEGE, 50);
                title = "🏰 CLAN SIEGES";
                break;
            case "clan_raid":
                clans = RankingManager.getInstance().getTopClans(RankingManager.ClanRankingType.RAID_BOSS, 50);
                title = "👑 CLAN RAID BOSS";
                break;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<table width=700><tr><td height=10></td></tr></table>");
        sb.append("<table width=20><tr><td>%navigation%</td><td><center>");
        sb.append("<table width=550 bgcolor=\"0a0a1a\" cellpadding=0 cellspacing=0>");
        sb.append("<tr><td height=15></td></tr>");
        sb.append("<tr><td align=center height=50>");
        sb.append("<font color=\"FFD700\" size=3>").append(title).append("</font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=5></td></tr>");
        sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        if (clans != null)
        {
            // Clan ranking table
            sb.append("<tr><td align=center>");
            sb.append("<table border=0 cellpadding=5 cellspacing=0 width=520>");
            sb.append("<tr>");
            sb.append("<td align=center width=50><font color=FFD700>#</font></td>");
            sb.append("<td align=left width=200><font color=FFD700>Clan Name</font></td>");
            sb.append("<td align=center width=80><font color=FFD700>Level</font></td>");
            sb.append("<td align=center width=80><font color=FFD700>Members</font></td>");
            sb.append("<td align=center width=100><font color=FFD700>Value</font></td>");
            sb.append("</tr>");

            int rank = 1;
            for (RankingManager.ClanRankingData clan : clans)
            {
                String clanName = clan.clanName;
                int value = 0;
                String valueLabel = "";
                
                // Determine what value to show based on context
                // This would need context from the type
                
                String rankColor = rank <= 3 ? "FFD700" : (rank <= 10 ? "C0C0C0" : "CD7F32");
                if (rank > 10) rankColor = "FFFFFF";
                
                sb.append("<tr>");
                sb.append("<td align=center width=50><font color=").append(rankColor).append(">").append(rank).append("</font></td>");
                sb.append("<td align=left width=200><font color=FFFFFF>").append(clan.clanName).append("</font></td>");
                sb.append("<td align=center width=80><font color=CCCCCC>").append(clan.level).append("</font></td>");
                sb.append("<td align=center width=80><font color=CCCCCC>").append(clan.memberCount).append("</font></td>");
                sb.append("<td align=center width=100><font color=FFD700>").append(clan.reputation).append("</font></td>");
                sb.append("</tr>");
                rank++;
            }
            sb.append("</table>");
            sb.append("</td></tr>");
        }
        else if (players != null)
        {
            // Player ranking table
            sb.append("<tr><td align=center>");
            sb.append("<table border=0 cellpadding=5 cellspacing=0 width=520>");
            sb.append("<tr>");
            sb.append("<td align=center width=50><font color=FFD700>#</font></td>");
            sb.append("<td align=left width=150><font color=FFD700>Character</font></td>");
            sb.append("<td align=center width=80><font color=FFD700>Class</font></td>");
            sb.append("<td align=center width=60><font color=FFD700>Level</font></td>");
            sb.append("<td align=center width=100><font color=FFD700>Value</font></td>");
            sb.append("</tr>");

            int rank = 1;
            for (RankingManager.RankingData p : players)
            {
                String rankColor = rank <= 3 ? "FFD700" : (rank <= 10 ? "C0C0C0" : "CD7F32");
                if (rank > 10) rankColor = "FFFFFF";

                int value = 0;
                String className = getClassName(p.classId);

                sb.append("<tr>");
                sb.append("<td align=center width=50><font color=").append(rankColor).append(">").append(rank).append("</font></td>");
                sb.append("<td align=left width=150><font color=FFFFFF>").append(p.charName).append("</font></td>");
                sb.append("<td align=center width=80><font color=CCCCCC>").append(className).append("</font></td>");
                sb.append("<td align=center width=60><font color=CCCCCC>").append(p.level).append("</font></td>");
                sb.append("<td align=center width=100><font color=FFD700>").append(getValue(p)).append("</font></td>");
                sb.append("</tr>");
                rank++;
            }
            sb.append("</table>");
            sb.append("</td></tr>");
        }

        String html = sb.toString();
        html = html.replace("%navigation%", HtmCache.getInstance().getHtm(null, "data/html/CommunityBoard/Custom/navigation.html"));
        return html;
    }

    private String getClassName(int classId)
    {
        // Simplified class names
        String[] classes = {
            "Human Fighter", "Warrior", "Gladiator", "Warlord", "Knight", "Paladin", "Dark Avenger",
            "Rogue", "Treasure Hunter", "Hawkeye", "Mystic", "Wizard", "Sorcerer", "Necromancer",
            "Warlock", "Cleric", "Bishop", "Prophet", "Elven Fighter", "Temple Knight", "Swordsinger",
            "Elven Mystic", "Spellsinger", "Elemental Summoner", "Elven Wizard", "Oracle",
            "Dark Fighter", "Palus Knight", "Shillien Knight", "Bladedancer", "Dark Mystic",
            "Spellhowler", "Phantom Summoner", "Dark Wizard", "Shillien Oracle", "Orc Fighter",
            "Destroyer", "Tyrant", "Orc Mystic", "Overlord", "Warcryer", "Dwarven Fighter",
            "Bounty Hunter", "Warsmith"
        };
        if (classId >= 0 && classId < classes.length) return classes[classId];
        return "Class " + classId;
    }

    private String getValue(RankingManager.RankingData p)
    {
        // Would need context - simplified
        return String.valueOf(p.pvpKills);
    }

    public static void register()
    {
        CommunityBoardHandler.getInstance().registerHandler(new RankingBoardHandler());
    }
}
