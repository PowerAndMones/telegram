package org.l2jmobius.gameserver.handlers.bypass.communityboard.custom;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.managers.clan.ClanManagementManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Clan Management Board Handler
 * @author Mobius Extended
 */
public class ClanBoardHandler implements IParseBoardHandler
{
    private static final Logger LOGGER = Logger.getLogger(ClanBoardHandler.class.getName());

    private static final String[] COMMANDS =
    {
        "_bbsclan",
        "_bbsclan_info",
        "_bbsclan_warehouse",
        "_bbsclan_shop",
        "_bbsclan_war",
        "_bbsclan_applications",
        "_bbsclan_invitations",
        "_bbsclan_division",
        "_bbsclan_management",
        "_bbsclan_leave",
        "_bbsclan_dismiss",
        "_bbsclan_appoint",
        "_bbsclan_dismiss_member",
        "_bbsclan_set_title",
        "_bbsclan_notice",
        "_bbsclan_join",
        "_bbsclan_create"
    };

    @Override
    public String[] getCommandList()
    {
        return COMMANDS;
    }

    @Override
    public boolean onCommand(String command, Player player)
    {
        if (!CustomSystemsConfig.CLAN_MANAGEMENT_ENABLED)
        {
            player.sendMessage("Clan management is disabled.");
            return false;
        }

        String html = null;

        if (command.equals("_bbsclan") || command.equals("_bbsclan;main"))
        {
            html = generateMainPage(player);
        }
        else if (command.startsWith("_bbsclan_info"))
        {
            html = generateInfoPage(player);
        }
        else if (command.startsWith("_bbsclan_warehouse"))
        {
            html = generateWarehousePage(player);
        }
        else if (command.startsWith("_bbsclan_shop"))
        {
            html = generateShopPage(player);
        }
        else if (command.startsWith("_bbsclan_war"))
        {
            html = generateWarPage(player);
        }
        else if (command.startsWith("_bbsclan_applications"))
        {
            html = generateApplicationsPage(player);
        }
        else if (command.startsWith("_bbsclan_invitations"))
        {
            html = generateInvitationsPage(player);
        }
        else if (command.startsWith("_bbsclan_division"))
        {
            html = generateDivisionPage(player);
        }
        else if (command.startsWith("_bbsclan_management"))
        {
            html = generateManagementPage(player);
        }
        else if (command.startsWith("_bbsclan_leave"))
        {
            handleLeaveClan(player);
            html = generateMainPage(player);
        }
        else if (command.startsWith("_bbsclan_dismiss"))
        {
            html = handleDismiss(player, command);
        }
        else if (command.startsWith("_bbsclan_appoint"))
        {
            html = handleAppoint(player, command);
        }
        else if (command.startsWith("_bbsclan_dismiss_member"))
        {
            html = handleDismissMember(player, command);
        }
        else if (command.startsWith("_bbsclan_set_title"))
        {
            html = handleSetTitle(player, command);
        }
        else if (command.startsWith("_bbsclan_notice"))
        {
            html = handleSetNotice(player, command);
        }
        else if (command.startsWith("_bbsclan_join"))
        {
            html = handleJoinClan(player, command);
        }
        else if (command.startsWith("_bbsclan_create"))
        {
            html = generateCreateClanPage(player);
        }

        if (html != null)
        {
            CommunityBoardHandler.separateAndSend(html, player);
        }
        return false;
    }

    private String generateMainPage(Player player)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/clan/main.html");
        if (html == null)
        {
            html = generateDefaultMainPage(player);
        }
        
        Clan clan = player.getClan();
        if (clan != null)
        {
            html = html.replace("%clan_name%", clan.getName());
            html = html.replace("%clan_level%", String.valueOf(clan.getLevel()));
            html = html.replace("%clan_reputation%", String.valueOf(clan.getReputationScore()));
            html = html.replace("%clan_members%", String.valueOf(clan.getMembersCount()));
            html = html.replace("%clan_division%", ClanManagementManager.getInstance().getDivision(clan.getClanId()));
            html = html.replace("%clan_points%", String.valueOf(ClanManagementManager.getInstance().getClanPoints(clan.getClanId())));
            html = html.replace("%weekly_points%", String.valueOf(ClanManagementManager.getInstance().getWeeklyPoints(clan.getClanId())));
            html = html.replace("%division%", ClanManagementManager.getInstance().getDivision(clan.getClanId()));
        }
        else
        {
            html = html.replace("%clan_name%", "No Clan");
            html = html.replace("%clan_level%", "0");
            html = html.replace("%clan_reputation%", "0");
            html = html.replace("%clan_members%", "0");
            html = html.replace("%clan_division%", "None");
            html = html.replace("%clan_points%", "0");
            html = html.replace("%weekly_points%", "0");
            html = html.replace("%division%", "None");
        }
        
        html = html.replace("%navigation%", HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/navigation.html"));
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
        sb.append("<font color=\"FFD700\" size=3>🏰 CLAN MANAGEMENT</font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=5></td></tr>");
        sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        if (player.getClan() != null)
        {
            sb.append("<tr><td align=center>");
            sb.append("<font color=CCCCCC>Clan: <font color=FFD700>%clan_name%</font> | Level: <font color=00FFFF>%clan_level%</font> | Reputation: <font color=FFA500>%clan_reputation%</font></font><br>");
            sb.append("<font color=CCCCCC>Members: <font color=00FFFF>%clan_members%</font> | Division: <font color=FFA500>%division%</font></font><br>");
            sb.append("<font color=CCCCCC>Clan Points: <font color=FFD700>%clan_points%</font> | Weekly: <font color=00FFFF>%weekly_points%</font></font>");
            sb.append("</td></tr>");

            sb.append("<tr><td height=20></td></tr>");
            sb.append("<tr><td align=center>");
            sb.append("<font color=FFD700 size=2>🏰 CLAN ACTIONS</font>");
            sb.append("</td></tr>");
            sb.append("<tr><td height=10></td></tr>");
            sb.append("<tr><td align=center>");
            sb.append("<table border=0 cellpadding=5 cellspacing=0 width=520>");
            sb.append("<tr>");
            sb.append("<td align=center width=130><button value=\"ℹ️ Info\" action=\"bypass _bbsclan_info\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center width=130><button value=\"🏪 Warehouse\" action=\"bypass _bbsclan_warehouse\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center width=130><button value=\"🏪 Shop\" action=\"bypass _bbsclan_shop\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center width=130><button value=\"⚔️ War\" action=\"bypass _bbsclan_war\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td align=center width=130><button value=\"📝 Applications\" action=\"bypass _bbsclan_applications\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center width=130><button value=\"📨 Invitations\" action=\"bypass _bbsclan_invitations\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center width=130><button value=\"🏆 Division\" action=\"bypass _bbsclan_division\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center width=130><button value=\"⚙️ Management\" action=\"bypass _bbsclan_management\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("</tr>");
            sb.append("</table>");
            sb.append("</td></tr>");
        }
        else
        {
            sb.append("<tr><td align=center>");
            sb.append("<font color=CCCCCC>You are not in a clan.</font><br>");
            sb.append("<font color=00FFFF>Create a new clan or join an existing one.</font>");
            sb.append("</td></tr>");

            sb.append("<tr><td height=20></td></tr>");
            sb.append("<tr><td align=center>");
            sb.append("<table border=0 cellpadding=5 cellspacing=0 width=520>");
            sb.append("<tr>");
            sb.append("<td align=center width=130><button value=\"🏰 Create Clan\" action=\"bypass _bbsclan_create\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center width=130><button value=\"🔍 Find Clan\" action=\"bypass _bbsclan_find\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center width=130></td>");
            sb.append("<td align=center width=130></td>");
            sb.append("</tr></table>");
            sb.append("</td></tr>");
        }

        sb.append("<tr><td height=20></td></tr>");
        sb.append("</table>");
        sb.append("<table border=0 cellpadding=0 cellspacing=0 width=550>");
        sb.append("<tr><td height=30 align=center><font color=888888><br>LINEAGE II PvP x1000 - COMMUNITY BOARD</font></td></tr>");
        sb.append("</table></center></td></tr></table></body></html>");

        return sb.toString();
    }

    // Placeholder methods for other pages
    private String generateInfoPage(Player player) { return generateMainPage(player); }
    private String generateWarehousePage(Player player) { return generateMainPage(player); }
    private String generateShopPage(Player player) { return generateMainPage(player); }
    private String generateWarPage(Player player) { return generateMainPage(player); }
    private String generateApplicationsPage(Player player) { return generateMainPage(player); }
    private String generateInvitationsPage(Player player) { return generateMainPage(player); }
    private String generateDivisionPage(Player player) { return generateMainPage(player); }
    private String generateManagementPage(Player player) { return generateMainPage(player); }
    private void handleLeaveClan(Player player) {}
    private String handleDismiss(Player player, String command) { return generateMainPage(player); }
    private String handleAppoint(Player player, String command) { return generateMainPage(player); }
    private String handleDismissMember(Player player, String command) { return generateMainPage(player); }
    private String handleSetTitle(Player player, String command) { return generateMainPage(player); }
    private String handleSetNotice(Player player, String command) { return generateMainPage(player); }
    private String handleJoinClan(Player player, String command) { return generateMainPage(player); }
    private String generateCreateClanPage(Player player) { return generateMainPage(player); }

    public static void register()
    {
        CommunityBoardHandler.getInstance().registerHandler(new ClanBoardHandler());
    }
}
