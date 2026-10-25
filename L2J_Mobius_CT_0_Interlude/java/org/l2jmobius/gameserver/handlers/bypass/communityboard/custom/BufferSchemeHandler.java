package org.l2jmobius.gameserver.handlers.bypass.communityboard.custom;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.serverpackets.MagicSkillUse;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.ShowBoard;
import org.l2jmobius.gameserver.util.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Buffer Scheme Handler for Community Board
 * Allows players to save/load/delete custom buff schemes
 * @author Mobius Extended
 */
public class BufferSchemeHandler implements IParseBoardHandler
{
    private static final Logger LOGGER = Logger.getLogger(BufferSchemeHandler.class.getName());

    private static final String[] COMMANDS =
    {
        "_bbsbuffscheme",
        "_bbsbuffsave",
        "_bbsbuffload",
        "_bbsbuffdelete",
        "_bbsbuffcreate",
        "_bbsbufflist"
    };

    private static final String SAVE_SCHEME_SQL =
        "INSERT INTO character_buff_schemes (char_id, scheme_name, buff_list, is_default, created_time) " +
        "VALUES (?,?,?,?,?)";
    
    private static final String UPDATE_SCHEME_SQL =
        "UPDATE character_buff_schemes SET buff_list=?, is_default=?, created_time=? WHERE char_id=? AND scheme_name=?";
    
    private static final String DELETE_SCHEME_SQL =
        "DELETE FROM character_buff_schemes WHERE char_id=? AND scheme_name=?";
    
    private static final String LOAD_SCHEMES_SQL =
        "SELECT scheme_name, buff_list, is_default FROM character_buff_schemes WHERE char_id=?";
    
    private static final String LOAD_SCHEME_SQL =
        "SELECT buff_list FROM character_buff_schemes WHERE char_id=? AND scheme_name=?";
    
    private static final String CHECK_SCHEME_EXISTS_SQL =
        "SELECT COUNT(*) FROM character_buff_schemes WHERE char_id=? AND scheme_name=?";
    
    private static final String COUNT_SCHEMES_SQL =
        "SELECT COUNT(*) FROM character_buff_schemes WHERE char_id=?";

    @Override
    public String[] getCommandList()
    {
        return COMMANDS;
    }

    @Override
    public boolean onCommand(String command, Player player)
    {
        if (!CommunityBoardConfig.COMMUNITYBOARD_ENABLE_BUFFS || !CommunityBoardConfig.COMMUNITY_ENABLE_BUFF_SCHEMES)
        {
            player.sendMessage("Buffer schemes are disabled.");
            return false;
        }

        String html = null;

        if (command.startsWith("_bbsbuffscheme"))
        {
            html = handleSchemeMenu(player, command);
        }
        else if (command.startsWith("_bbsbuffsave"))
        {
            html = handleSaveScheme(player, command);
        }
        else if (command.startsWith("_bbsbuffload"))
        {
            html = handleLoadScheme(player, command);
        }
        else if (command.startsWith("_bbsbuffdelete"))
        {
            html = handleDeleteScheme(player, command);
        }
        else if (command.startsWith("_bbsbuffcreate"))
        {
            html = handleCreateScheme(player, command);
        }
        else if (command.startsWith("_bbsbufflist"))
        {
            html = handleListSchemes(player);
        }

        if (html != null)
        {
            CommunityBoardHandler.separateAndSend(html, player);
        }
        return false;
    }

    private String handleSchemeMenu(Player player, String command)
    {
        String schemeName = "";
        if (command.contains(";"))
        {
            schemeName = command.substring(command.indexOf(";") + 1);
        }

        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/buffer/main.html");
        if (html == null)
        {
            return "Buffer HTML not found.";
        }

        // Replace scheme list
        StringBuilder schemeListHtml = new StringBuilder();
        List<BuffScheme> schemes = loadSchemes(player.getObjectId());
        
        if (schemes.isEmpty())
        {
            schemeListHtml.append("<tr><td align=center colspan=4 height=40>");
            schemeListHtml.append("<font color=CCCCCC>Нет сохраненных схем. Нажмите \"Новая схема\" для создания.</font>");
            schemeListHtml.append("</td></tr>");
        }
        else
        {
            for (BuffScheme scheme : schemes)
            {
                schemeListHtml.append("<tr>");
                schemeListHtml.append("<td align=center width=150>");
                schemeListHtml.append("<font color=").append(scheme.isDefault ? "FFD700" : "FFFFFF").append(">").append(Util.escapeHtml(scheme.name)).append("</font>");
                schemeListHtml.append(scheme.isDefault ? " <font color=00FF00>[DEFAULT]</font>" : "");
                schemeListHtml.append("</td>");
                schemeListHtml.append("<td align=center width=100>");
                schemeListHtml.append("<font color=CCCCCC size=1>").append(scheme.buffCount).append(" баффов</font>");
                schemeListHtml.append("</td>");
                schemeListHtml.append("<td align=center width=100>");
                schemeListHtml.append("<button value=\"Загрузить\" action=\"bypass _bbsbuffload;").append(Util.escapeHtml(scheme.name)).append("\" width=70 height=22 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\">");
                schemeListHtml.append("</td>");
                schemeListHtml.append("<td align=center width=100>");
                if (!scheme.isDefault)
                {
                    schemeListHtml.append("<button value=\"Удалить\" action=\"bypass _bbsbuffdelete;").append(Util.escapeHtml(scheme.name)).append("\" width=70 height=22 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\">");
                }
                else
                {
                    schemeListHtml.append("<font color=888888>Основная</font>");
                }
                schemeListHtml.append("</td>");
                schemeListHtml.append("</tr>");
            }
        }

        html = html.replace("%scheme_count%", String.valueOf(schemes.size()));
        html = html.replace("%scheme_list%", schemeListHtml.toString());

        // Generate buffer skill buttons
        html = html.replace("%buffer_fighter%", generateBufferButtons("fighter"));
        html = html.replace("%buffer_mage%", generateBufferButtons("mage"));
        html = html.replace("%buffer_songs%", generateBufferButtons("songs"));
        html = html.replace("%buffer_special%", generateBufferButtons("special"));

        return html;
    }

    private String handleCreateScheme(Player player, String command)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/buffer/create.html");
        if (html == null)
        {
            return "Create scheme HTML not found.";
        }

        // Generate checkboxes for all buff categories
        html = html.replace("%create_fighter_checkboxes%", generateCheckboxes("fighter"));
        html = html.replace("%create_mage_checkboxes%", generateCheckboxes("mage"));
        html = html.replace("%create_songs_checkboxes%", generateCheckboxes("songs"));
        html = html.replace("%create_special_checkboxes%", generateCheckboxes("special"));

        return html;
    }

    private String handleSaveScheme(Player player, command)
    {
        // command format: _bbsbuffsave;schemeName;skillId1,level1;skillId2,level2;...
        String[] parts = command.split(";", 3);
        if (parts.length < 3)
        {
            player.sendMessage("Invalid save command format.");
            return handleSchemeMenu(player, "");
        }

        String schemeName = parts[1];
        String buffList = parts[2];

        // Validate scheme name
        if (schemeName.length() > 20)
        {
            player.sendMessage("Scheme name too long (max 20 chars).");
            return handleSchemeMenu(player, "");
        }

        // Check scheme count
        int count = getSchemeCount(player.getObjectId());
        if (count >= CommunityBoardConfig.COMMUNITY_MAX_BUFF_SCHEMES)
        {
            player.sendMessage("Maximum schemes reached (" + CommunityBoardConfig.COMMUNITY_MAX_BUFF_SCHEMES + ").");
            return handleSchemeMenu(player, "");
        }

        // Check if scheme exists
        boolean exists = schemeExists(player.getObjectId(), schemeName);
        
        // Check price
        int price = CommunityBoardConfig.COMMUNITY_BUFF_SCHEME_SAVE_PRICE;
        if (price > 0 && !player.reduceAdena("BuffSchemeSave", price, null, true))
        {
            player.sendMessage("Not enough adena to save scheme.");
            return handleSchemeMenu(player, "");
        }

        try (Connection con = DatabaseFactory.getConnection())
        {
            if (exists)
            {
                try (PreparedStatement ps = con.prepareStatement(UPDATE_SCHEME_SQL))
                {
                    ps.setString(1, buffList);
                    ps.setInt(2, 0);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.setInt(4, player.getObjectId());
                    ps.setString(5, schemeName);
                    ps.executeUpdate();
                }
            }
            else
            {
                try (PreparedStatement ps = con.prepareStatement(SAVE_SCHEME_SQL))
                {
                    ps.setInt(1, player.getObjectId());
                    ps.setString(2, schemeName);
                    ps.setString(3, buffList);
                    ps.setInt(4, count == 0 ? 1 : 0); // First scheme is default
                    ps.setLong(5, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }
            player.sendMessage("Scheme '" + schemeName + "' saved successfully!");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to save buff scheme: ", e);
            player.sendMessage("Failed to save scheme.");
        }

        return handleSchemeMenu(player, "");
    }

    private String handleLoadScheme(Player player, String command)
    {
        String schemeName = command.substring(command.indexOf(";") + 1);
        
        String buffList = getSchemeBuffList(player.getObjectId(), schemeName);
        if (buffList == null || buffList.isEmpty())
        {
            player.sendMessage("Scheme not found or empty.");
            return handleSchemeMenu(player, "");
        }

        // Apply buffs
        applyBuffScheme(player, buffList);
        player.sendMessage("Scheme '" + schemeName + "' loaded and applied!");
        
        return handleSchemeMenu(player, "");
    }

    private String handleDeleteScheme(Player player, String command)
    {
        String schemeName = command.substring(command.indexOf(";") + 1);
        
        // Don't allow deleting default scheme
        if (isDefaultScheme(player.getObjectId(), schemeName))
        {
            player.sendMessage("Cannot delete default scheme.");
            return handleSchemeMenu(player, "");
        }

        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement(DELETE_SCHEME_SQL))
        {
            ps.setInt(1, player.getObjectId());
            ps.setString(2, schemeName);
            int deleted = ps.executeUpdate();
            
            if (deleted > 0)
            {
                player.sendMessage("Scheme '" + schemeName + "' deleted.");
            }
            else
            {
                player.sendMessage("Scheme not found.");
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to delete buff scheme: ", e);
            player.sendMessage("Failed to delete scheme.");
        }

        return handleSchemeMenu(player, "");
    }

    private String handleListSchemes(Player player)
    {
        return handleSchemeMenu(player, "");
    }

    private void applyBuffScheme(Player player, String buffList)
    {
        String[] buffs = buffList.split(";");
        final Summon pet = player.getSummon();
        final List<Creature> targets = new ArrayList<>(4);
        targets.add(player);
        if (pet != null)
        {
            targets.add(pet);
        }

        for (String buff : buffs)
        {
            String[] parts = buff.split(",");
            if (parts.length >= 2)
            {
                try
                {
                    int skillId = Integer.parseInt(parts[0]);
                    int skillLevel = Integer.parseInt(parts[1]);
                    
                    // Verify skill is allowed
                    if (!CommunityBoardConfig.COMMUNITY_AVAILABLE_BUFFS.contains(skillId))
                    {
                        continue;
                    }

                    final var skill = org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(skillId, skillLevel);
                    if (skill == null)
                    {
                        continue;
                    }

                    for (Creature target : targets)
                    {
                        skill.applyEffects(player, target);
                        if (CommunityBoardConfig.COMMUNITYBOARD_CAST_ANIMATIONS)
                        {
                            player.sendPacket(new MagicSkillUse(player, target, skillId, skillLevel, skill.getHitTime(), skill.getReuseDelay()));
                        }
                    }
                }
                catch (Exception e)
                {
                    LOGGER.log(Level.WARNING, "Failed to apply buff: " + buff, e);
                }
            }
        }
    }

    private List<BuffScheme> loadSchemes(int charId)
    {
        List<BuffScheme> schemes = new ArrayList<>();
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement(LOAD_SCHEMES_SQL))
        {
            ps.setInt(1, charId);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    BuffScheme scheme = new BuffScheme();
                    scheme.name = rs.getString("scheme_name");
                    scheme.buffList = rs.getString("buff_list");
                    scheme.isDefault = rs.getInt("is_default") == 1;
                    scheme.buffCount = scheme.buffList.isEmpty() ? 0 : scheme.buffList.split(";").length;
                    schemes.add(scheme);
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to load buff schemes for char " + charId, e);
        }
        return schemes;
    }

    private String getSchemeBuffList(int charId, String schemeName)
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement(LOAD_SCHEME_SQL))
        {
            ps.setInt(1, charId);
            ps.setString(2, schemeName);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    return rs.getString("buff_list");
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to load buff scheme: ", e);
        }
        return null;
    }

    private int getSchemeCount(int charId)
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement(COUNT_SCHEMES_SQL))
        {
            ps.setInt(1, charId);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    return rs.getInt(1);
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to count buff schemes: ", e);
        }
        return 0;
    }

    private boolean schemeExists(int charId, String schemeName)
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement(CHECK_SCHEME_EXISTS_SQL))
        {
            ps.setInt(1, charId);
            ps.setString(2, schemeName);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    return rs.getInt(1) > 0;
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to check scheme exists: ", e);
        }
        return false;
    }

    private boolean isDefaultScheme(int charId, String schemeName)
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT is_default FROM character_buff_schemes WHERE char_id=? AND scheme_name=?"))
        {
            ps.setInt(1, charId);
            ps.setString(2, schemeName);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    return rs.getInt(1) == 1;
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to check default scheme: ", e);
        }
        return false;
    }

    private String generateCheckboxes(String category)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border=0 cellpadding=2 cellspacing=0 width=500>");
        
        // This is a simplified version - in real implementation you'd iterate through actual skill lists
        // For now, showing the structure
        sb.append("<tr><td align=center colspan=5>");
        sb.append("<font color=CCCCCC size=1>Select buffs for ").append(category).append(" category</font>");
        sb.append("</td></tr>");
        sb.append("</table>");
        
        return sb.toString();
    }

    private String generateBufferButtons(String category)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border=0 cellpadding=2 cellspacing=0 width=500>");
        
        // This would generate actual skill buttons from the CommunityAvailableBuffs config
        // For now, placeholder
        sb.append("<tr><td align=center colspan=8>");
        sb.append("<font color=CCCCCC size=1>").append(category.toUpperCase()).append(" buffs - use quick categories above</font>");
        sb.append("</td></tr>");
        sb.append("</table>");
        
        return sb.toString();
    }

    private static class BuffScheme
    {
        String name;
        String buffList;
        boolean isDefault;
        int buffCount;
    }

    // Registration
    public static void register()
    {
        CommunityBoardHandler.getInstance().registerHandler(new BufferSchemeHandler());
    }
}
