package org.l2jmobius.gameserver.handlers.bypass.communityboard.custom;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.managers.events.EventEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.ShowBoard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Event Board Handler for Community Board
 * Handles event registration, event info, event history
 * @author Mobius Extended
 */
public class EventBoardHandler implements IParseBoardHandler
{
    private static final Logger LOGGER = Logger.getLogger(EventBoardHandler.class.getName());

    private static final String[] COMMANDS =
    {
        "_bbsevent",
        "_bbsevents",
        "_bbsevent_register",
        "_bbsevent_unregister",
        "_bbsevent_info",
        "_bbsevent_history",
        "_bbsevent_myevents"
    };

    private static final String GET_EVENT_SQL = "SELECT * FROM event_schedule WHERE id = ?";
    private static final String GET_PLAYER_EVENTS_SQL = "SELECT * FROM event_participants WHERE char_id = ? AND event_id = ?";

    @Override
    public String[] getCommandList()
    {
        return COMMANDS;
    }

    @Override
    public boolean onCommand(String command, Player player)
    {
        if (!CustomSystemsConfig.EVENT_ENGINE_ENABLED)
        {
            player.sendMessage("Event Engine is disabled.");
            return false;
        }

        String html = null;

        if (command.equals("_bbsevent") || command.equals("_bbsevents") || command.startsWith("_bbsevents;"))
        {
            html = handleEventList(player, command);
        }
        else if (command.startsWith("_bbsevent_register;"))
        {
            html = handleRegister(player, command);
        }
        else if (command.startsWith("_bbsevent_unregister;"))
        {
            html = handleUnregister(player, command);
        }
        else if (command.startsWith("_bbsevent_info;"))
        {
            html = handleEventInfo(player, command);
        }
        else if (command.startsWith("_bbsevent_history;"))
        {
            html = handleEventHistory(player, command);
        }
        else if (command.equals("_bbsevent_myevents"))
        {
            html = handleMyEvents(player);
        }

        if (html != null)
        {
            CommunityBoardHandler.separateAndSend(html, player);
        }
        return false;
    }

    private String handleEventList(Player player, String command)
    {
        String page = "main";
        if (command.contains(";"))
        {
            String[] parts = command.split(";");
            if (parts.length > 1) page = parts[1];
        }

        if (page.equals("main"))
        {
            return generateMainEventPage(player);
        }
        else
        {
            // Specific event type page
            return generateEventTypePage(player, page);
        }
    }

    private String generateMainEventPage(Player player)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/events/main.html");
        if (html == null)
        {
            return "Events HTML not found.";
        }

        // Get active events
        StringBuilder eventHtml = new StringBuilder();
        final EventEngine engine = EventEngine.getInstance();
        
        // We need to access scheduled events - for now build static list based on config
        // In production, we'd iterate through engine.getScheduledEvents()
        
        StringBuilder eventListHtml = new StringBuilder();
        eventListHtml.append("<table border=0 cellpadding=5 cellspacing=0 width=520>");

        // TVT
        eventListHtml.append("<tr>");
        eventListHtml.append("<td align=center width=130><button value=\"TvT\" action=\"bypass _bbstop;events/tvt.html\" width=110 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        eventListHtml.append("<td align=center width=130><button value=\"CTF\" action=\"bypass _bbstop;events/ctf.html\" width=110 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        eventListHtml.append("<td align=center width=130><button value=\"Deathmatch\" action=\"bypass _bbstop;events/dm.html\" width=110 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        eventListHtml.append("<td align=center width=130><button value=\"LMS\" action=\"bypass _bbstop;events/lms.html\" width=110 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        eventListHtml.append("</tr>");

        eventListHtml.append("<tr>");
        eventListHtml.append("<td align=center width=130><button value=\"Arena 1v1\" action=\"bypass _bbstop;events/arena1.html\" width=110 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        eventListHtml.append("<td align=center width=130><button value=\"Arena 2v2\" action=\"bypass _bbstop;events/arena2.html\" width=110 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        eventListHtml.append("<td align=center width=130><button value=\"GvG 5v5\" action=\"bypass _bbstop;events/gvg5.html\" width=110 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        eventListHtml.append("<td align=center width=130><button value=\"GvG 9v9\" action=\"bypass _bbstop;events/gvg9.html\" width=110 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        eventListHtml.append("</tr>");
        eventListHtml.append("</table>");

        html = html.replace("%event_list%", eventListHtml.toString());
        
        // Replace placeholders
        html = html.replace("%navigation%", HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/navigation.html"));
        
        return html;
    }

    private String generateEventTypePage(Player player, String eventType)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/events/" + eventType + ".html");
        if (html == null)
        {
            return "Event type page not found: " + eventType;
        }
        return html;
    }

    private String handleRegister(Player player, String command)
    {
        // command format: _bbsevent_register;eventType
        String eventType = command.substring(command.indexOf(";") + 1);
        EventEngine.getInstance().registerPlayer(player, eventType);
        
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/events/registered.html");
        if (html == null)
        {
            html = "<html><body><center><br><font color=00FF00>Successfully registered for " + eventType + "!</font><br><br><button value=\"Back\" action=\"bypass _bbstop;events/main.html\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></center></body></html>";
        }
        return html;
    }

    private String handleUnregister(Player player, String command)
    {
        String eventType = command.substring(command.indexOf(";") + 1);
        EventEngine.getInstance().unregisterPlayer(player, eventType);
        
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/events/unregistered.html");
        if (html == null)
        {
            html = "<html><body><center><br><font color=FF6347>Unregistered from event.</font><br><br><button value=\"Back\" action=\"bypass _bbstop;events/main.html\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></center></body></html>";
        }
        return html;
    }

    private String handleEventInfo(Player player, String command)
    {
        String eventIdStr = command.substring(command.indexOf(";") + 1);
        try
        {
            int eventId = Integer.parseInt(eventIdStr);
            String html = getEventInfoHtml(eventId, player);
            if (html == null)
            {
                return "Event not found.";
            }
            return html;
        }
        catch (Exception e)
        {
            return "Invalid event ID.";
        }
    }

    private String handleEventHistory(Player player, String command)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/events/history.html");
        if (html == null)
        {
            return generateEventHistoryHtml(player);
        }
        return html;
    }

    private String handleMyEvents(Player player)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/events/myevents.html");
        if (html == null)
        {
            return generateMyEventsHtml(player);
        }
        return html;
    }

    private String getEventInfoHtml(int eventId, Player player)
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM event_schedule WHERE id = ?"))
        {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append("<html><body><center><br>");
                    sb.append("<table width=550 bgcolor=0a0a1a>");
                    sb.append("<tr><td height=20></td></tr>");
                    sb.append("<tr><td align=center><font color=FFD700 size=3>").append(rs.getString("event_name")).append("</font></td></tr>");
                    sb.append("<tr><td height=10></td></tr>");
                    sb.append("<tr><td align=center><font color=CCCCCC>Type: ").append(rs.getString("event_type")).append("</font></td></tr>");
                    sb.append("<tr><td align=center><font color=CCCCCC>Level: ").append(rs.getInt("min_level")).append(" - ").append(rs.getInt("max_level")).append("</font></td></tr>");
                    sb.append("<tr><td align=center><font color=CCCCCC>Players: ").append(rs.getInt("min_players")).append(" - ").append(rs.getInt("max_players")).append("</font></td></tr>");
                    sb.append("<tr><td align=center><font color=CCCCCC>Schedule: ").append(rs.getString("cron_expression")).append("</font></td></tr>");
                    sb.append("<tr><td align=center><font color=FFD700>Rewards: ").append(rs.getString("reward_items")).append("</font></td></tr>");
                    sb.append("<tr><td height=20></td></tr>");
                    sb.append("<tr><td align=center>");
                    sb.append("<button value=\"Register\" action=\"bypass _bbsevent_register;").append(rs.getString("event_type")).append("\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\">");
                    sb.append("</td></tr>");
                    sb.append("<tr><td height=10></td></tr>");
                    sb.append("<tr><td align=center><button value=\"Back\" action=\"bypass _bbstop;events/main.html\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td></tr>");
                    sb.append("</table></center></body></html>");
                    return sb.toString();
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to get event info", e);
        }
        return null;
    }

    private String generateEventHistoryHtml(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center><br>");
        sb.append("<table width=550 bgcolor=0a0a1a>");
        sb.append("<tr><td height=20></td></tr>");
        sb.append("<tr><td align=center><font color=FFD700 size=3>Event History</font></td></tr>");
        sb.append("<tr><td height=10></td></tr>");
        sb.append("<tr><td align=center><font color=CCCCCC>Recent events will be shown here.</font></td></tr>");
        sb.append("<tr><td height=20></td></tr>");
        sb.append("<tr><td align=center><button value=\"Back\" action=\"bypass _bbstop;events/main.html\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td></tr>");
        sb.append("</table></center></body></html>");
        return sb.toString();
    }

    private String generateMyEventsHtml(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center><br>");
        sb.append("<table width=550 bgcolor=0a0a1a>");
        sb.append("<tr><td height=20></td></tr>");
        sb.append("<tr><td align=center><font color=FFD700 size=3>My Events</font></td></tr>");
        sb.append("<tr><td height=10></td></tr>");
        sb.append("<tr><td align=center><font color=CCCCCC>You are not registered for any events.</font></td></tr>");
        sb.append("<tr><td height=20></td></tr>");
        sb.append("<tr><td align=center><button value=\"Back\" action=\"bypass _bbstop;events/main.html\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td></tr>");
        sb.append("</table></center></body></html>");
        return sb.toString();
    }

    public static void register()
    {
        CommunityBoardHandler.getInstance().registerHandler(new EventBoardHandler());
    }
}
