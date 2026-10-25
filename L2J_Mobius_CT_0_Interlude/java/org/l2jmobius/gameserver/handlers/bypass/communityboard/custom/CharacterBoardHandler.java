package org.l2jmobius.gameserver.handlers.bypass.communityboard.custom;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IParseBoardHandler;
import org.l2jmobius.gameserver.managers.character.CharacterManagementManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Character Management Board Handler
 * @author Mobius Extended
 */
public class CharacterBoardHandler implements IParseBoardHandler
{
    private static final Logger LOGGER = Logger.getLogger(CharacterBoardHandler.class.getName());

    private static final String[] COMMANDS =
    {
        "_bbscharacter",
        "_bbscharacter_nickname",
        "_bbscharacter_gender",
        "_bbscharacter_delete",
        "_bbscharacter_cancel_delete",
        "_bbscharacter_unstuck",
        "_bbscharacter_class",
        "_bbscharacter_nickname_confirm",
        "_bbscharacter_gender_confirm",
        "_bbscharacter_class_confirm"
    };

    @Override
    public String[] getCommandList()
    {
        return COMMANDS;
    }

    @Override
    public boolean onCommand(String command, Player player)
    {
        if (!CustomSystemsConfig.CHARACTER_MANAGEMENT_ENABLED)
        {
            player.sendMessage("Character management is disabled.");
            return false;
        }

        String html = null;

        if (command.equals("_bbscharacter") || command.equals("_bbscharacter;main"))
        {
            html = generateMainPage(player);
        }
        else if (command.startsWith("_bbscharacter_nickname"))
        {
            if (command.contains("_confirm"))
            {
                html = handleNicknameConfirm(player, command);
            }
            else
            {
                html = generateNicknamePage(player);
            }
        }
        else if (command.startsWith("_bbscharacter_gender"))
        {
            if (command.contains("_confirm"))
            {
                html = handleGenderConfirm(player);
            }
            else
            {
                html = generateGenderPage(player);
            }
        }
        else if (command.startsWith("_bbscharacter_delete"))
        {
            if (command.contains("_confirm"))
            {
                html = handleDeleteConfirm(player);
            }
            else if (command.contains("cancel"))
            {
                html = handleCancelDelete(player);
            }
            else
            {
                html = generateDeletePage(player);
            }
        }
        else if (command.equals("_bbscharacter_unstuck"))
        {
            CharacterManagementManager.getInstance().unstuckCharacter(player);
            html = generateMainPage(player);
        }
        else if (command.startsWith("_bbscharacter_class"))
        {
            if (command.contains("_confirm"))
            {
                html = handleClassConfirm(player, command);
            }
            else
            {
                html = generateClassPage(player);
            }
        }

        if (html != null)
        {
            CommunityBoardHandler.separateAndSend(html, player);
        }
        return false;
    }

    private String generateMainPage(Player player)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/character/main.html");
        if (html == null)
        {
            html = generateDefaultMainPage(player);
        }
        
        html = html.replace("%navigation%", HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/navigation.html"));
        html = html.replace("%character_name%", player.getName());
        html = html.replace("%character_level%", String.valueOf(player.getLevel()));
        html = html.replace("%character_class%", player.getClassId().name());
        
        // Replace cooldown info
        // This would be filled from CharacterManagementManager
        
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
        sb.append("<font color=\"FFD700\" size=3>👤 CHARACTER MANAGEMENT</font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=5></td></tr>");
        sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<font color=CCCCCC>Character: <font color=FFD700>%character_name%</font> | Level: <font color=00FFFF>%character_level%</font> | Class: <font color=FFA500>%character_class%</font></font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=20></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<font color=FFD700 size=2>📋 CHARACTER ACTIONS</font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<table border=0 cellpadding=5 cellspacing=0 width=520>");
        sb.append("<tr>");
        sb.append("<td align=center width=130><button value=\"✏️ Nickname\" action=\"bypass _bbscharacter_nickname\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"⚥ Gender\" action=\"bypass _bbscharacter_gender\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"🗑️ Delete\" action=\"bypass _bbscharacter_delete\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130><button value=\"🔄 Unstuck\" action=\"bypass _bbscharacter_unstuck\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td align=center width=130><button value=\"🎭 Class Change\" action=\"bypass _bbscharacter_class\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=130></td>");
        sb.append("<td align=center width=130></td>");
        sb.append("<td align=center width=130></td>");
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

    private String generateNicknamePage(Player player)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/character/nickname.html");
        if (html == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("<html><body>");
            sb.append("<table width=700><tr><td height=10></td></tr></table>");
            sb.append("<table width=20><tr><td>%navigation%</td><td><center>");
            sb.append("<table width=550 bgcolor=\"0a0a1a\" cellpadding=0 cellspacing=0>");
            sb.append("<tr><td height=15></td></tr>");
            sb.append("<tr><td align=center height=50>");
            sb.append("<font color=\"FFD700\" size=3>✏️ NICKNAME CHANGE</font>");
            sb.append("</td></tr>");
            sb.append("<tr><td height=5></td></tr>");
            sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
            sb.append("<tr><td height=10></td></tr>");

            sb.append("<tr><td align=center>");
            sb.append("<font color=CCCCCC>Current: <font color=FFD700>").append(player.getName()).append("</font></font><br>");
            sb.append("<font color=CCCCCC>Cost: <font color=FFFF00>").append(CustomSystemsConfig.NICKNAME_CHANGE_COST).append(" Adena</font></font><br>");
            sb.append("<font color=CCCCCC>Cooldown: <font color=00FFFF>").append(CustomSystemsConfig.NICKNAME_CHANGE_COOLDOWN).append(" days</font></font><br>");
            sb.append("<font color=FF6347 size=1>Min 3, max 16 chars. Letters, numbers, _ and - only.</font>");
            sb.append("</td></tr>");

            sb.append("<tr><td height=15></td></tr>");
            sb.append("<tr><td align=center>");
            sb.append("<table border=0 cellpadding=5 cellspacing=0 width=500>");
            sb.append("<tr><td align=center colspan=2>");
            sb.append("<edit var=\"new_nickname\" width=200 height=20 length=16>");
            sb.append("</td></tr>");
            sb.append("<tr>");
            sb.append("<td align=center><button value=\"✅ CHANGE\" action=\"bypass _bbscharacter_nickname_confirm;$new_nickname\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center><button value=\"❌ CANCEL\" action=\"bypass _bbscharacter;main\" width=120 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("</tr></table>");
            sb.append("</td></tr>");

            sb.append("<tr><td height=20></td></tr>");
            sb.append("</table>");
            sb.append("<table border=0 cellpadding=0 cellspacing=0 width=550>");
            sb.append("<tr><td height=30 align=center><font color=888888><br>LINEAGE II PvP x1000 - COMMUNITY BOARD</font></td></tr>");
            sb.append("</table></center></td></tr></table></body></html>");

            html = sb.toString();
        }

        html = html.replace("%navigation%", HtmCache.getInstance().getHtm(null, "data/html/CommunityBoard/Custom/navigation.html"));
        return html;
    }

    private String handleNicknameConfirm(Player player, String command)
    {
        String newName = command.substring(command.indexOf("$") + 1);
        CharacterManagementManager.getInstance().changeNickname(player, newName);
        return generateMainPage(player);
    }

    private String generateGenderPage(Player player)
    {
        String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/Custom/character/gender.html");
        if (html == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("<html><body>");
            sb.append("<table width=700><tr><td height=10></td></tr></table>");
            sb.append("<table width=20><tr><td>%navigation%</td><td><center>");
            sb.append("<table width=550 bgcolor=\"0a0a1a\" cellpadding=0 cellspacing=0>");
            sb.append("<tr><td height=15></td></tr>");
            sb.append("<tr><td align=center height=50>");
            sb.append("<font color=\"FFD700\" size=3>⚥ GENDER CHANGE</font>");
            sb.append("</td></tr>");
            sb.append("<tr><td height=5></td></tr>");
            sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
            sb.append("<tr><td height=10></td></tr>");

            sb.append("<tr><td align=center>");
            sb.append("<font color=CCCCCC>Current: <font color=FFD700>").append(player.getAppearance().getSex() ? "Female" : "Male").append("</font></font><br>");
            sb.append("<font color=CCCCCC>Cost: <font color=FFFF00>").append(CustomSystemsConfig.GENDER_CHANGE_COST).append(" Adena</font></font><br>");
            sb.append("<font color=CCCCCC>Cooldown: <font color=00FFFF>").append(CustomSystemsConfig.GENDER_CHANGE_COOLDOWN).append(" days</font></font><br>");
            sb.append("<font color=FFA500>Changes appearance only. No stat changes.</font>");
            sb.append("</td></tr>");

            sb.append("<tr><td height=15></td></tr>");
            sb.append("<tr><td align=center>");
            sb.append("<table border=0 cellpadding=5 cellspacing=0 width=500>");
            sb.append("<tr>");
            sb.append("<td align=center><button value=\"⚥ CHANGE GENDER\" action=\"bypass _bbscharacter_gender_confirm\" width=150 height=35 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("<td align=center><button value=\"❌ CANCEL\" action=\"bypass _bbscharacter;main\" width=150 height=35 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
            sb.append("</tr></table>");
            sb.append("</td></tr>");

            sb.append("<tr><td height=20></td></tr>");
            sb.append("</table>");
            sb.append("<table border=0 cellpadding=0 cellspacing=0 width=550>");
            sb.append("<tr><td height=30 align=center><font color=888888><br>LINEAGE II PvP x1000 - COMMUNITY BOARD</font></td></tr>");
            sb.append("</table></center></td></tr></table></body></html>");

            html = sb.toString();
        }

        html = html.replace("%navigation%", HtmCache.getInstance().getHtm(null, "data/html/CommunityBoard/Custom/navigation.html"));
        return html;
    }

    private String handleGenderConfirm(Player player)
    {
        CharacterManagementManager.getInstance().changeGender(player);
        return generateMainPage(player);
    }

    private String generateDeletePage(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<table width=700><tr><td height=10></td></tr></table>");
        sb.append("<table width=20><tr><td>%navigation%</td><td><center>");
        sb.append("<table width=550 bgcolor=\"0a0a1a\" cellpadding=0 cellspacing=0>");
        sb.append("<tr><td height=15></td></tr>");
        sb.append("<tr><td align=center height=50>");
        sb.append("<font color=\"FF6347\" size=3>🗑️ CHARACTER DELETION</font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=5></td></tr>");
        sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<font color=FF6347 size=2>⚠️ WARNING: THIS ACTION IS IRREVERSIBLE!</font><br><br>");
        sb.append("<font color=CCCCCC>Your character <font color=FFD700>").append(player.getName()).append("</font> will be permanently deleted after <font color=FFA500>").append(CustomSystemsConfig.CHARACTER_DELETION_DELAY_HOURS).append(" hours</font>.</font><br>");
        sb.append("<font color=FF6347>All items, skills, quests, and progress will be LOST FOREVER.</font><br><br>");
        sb.append("<font color=00FFFF>You can cancel within the delay period.</font>");
        sb.append("</td></tr>");

        sb.append("<tr><td height=20></td></tr>");
        sb.append("<tr><td align=center>");
        sb.append("<table border=0 cellpadding=5 cellspacing=0 width=500>");
        sb.append("<tr>");
        sb.append("<td align=center><button value=\"🗑️ CONFIRM DELETE\" action=\"bypass _bbscharacter_delete_confirm\" width=150 height=35 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center><button value=\"❌ CANCEL\" action=\"bypass _bbscharacter;main\" width=150 height=35 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("</tr></table>");
        sb.append("</td></tr>");

        sb.append("<tr><td height=20></td></tr>");
        sb.append("</table>");
        sb.append("<table border=0 cellpadding=0 cellspacing=0 width=550>");
        sb.append("<tr><td height=30 align=center><font color=888888><br>LINEAGE II PvP x1000 - COMMUNITY BOARD</font></td></tr>");
        sb.append("</table></center></td></tr></table></body></html>");

        return sb.toString().replace("%navigation%", HtmCache.getInstance().getHtm(null, "data/html/CommunityBoard/Custom/navigation.html"));
    }

    private String handleDeleteConfirm(Player player)
    {
        CharacterManagementManager.getInstance().requestCharacterDeletion(player);
        return generateMainPage(player);
    }

    private String handleCancelDelete(Player player)
    {
        CharacterManagementManager.getInstance().cancelCharacterDeletion(player);
        return generateMainPage(player);
    }

    private String generateClassPage(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<table width=700><tr><td height=10></td></tr></table>");
        sb.append("<table width=20><tr><td>%navigation%</td><td><center>");
        sb.append("<table width=550 bgcolor=\"0a0a1a\" cellpadding=0 cellspacing=0>");
        sb.append("<tr><td height=15></td></tr>");
        sb.append("<tr><td align=center height=50>");
        sb.append("<font color=\"FFD700\" size=3>🎭 CLASS CHANGE</font>");
        sb.append("</td></tr>");
        sb.append("<tr><td height=5></td></tr>");
        sb.append("<tr><td><center><img src=\"L2UI_CH3.herotower_deco\" width=300 height=2></center></td></tr>");
        sb.append("<tr><td height=10></td></tr>");

        sb.append("<tr><td align=center>");
        sb.append("<font color=CCCCCC>Current Class: <font color=FFD700>").append(player.getClassId().name()).append("</font></font><br>");
        sb.append("<font color=CCCCCC>Cost: <font color=FFFF00>").append(CustomSystemsConfig.CLASS_CHANGE_COST).append(" Adena</font></font><br>");
        sb.append("<font color=CCCCCC>Cooldown: <font color=00FFFF>").append(CustomSystemsConfig.CLASS_CHANGE_COOLDOWN).append(" days</font></font><br>");
        sb.append("<font color=FFA500>Available for all classes on PvP server.</font>");
        sb.append("</td></tr>");

        sb.append("<tr><td height=15></td></tr>");
        sb.append("<tr><td align=center>");
        sb.append("<table border=0 cellpadding=5 cellspacing=0 width=520>");
        sb.append("<tr>");
        sb.append("<td align=center width=170><button value=\"Fighter\" action=\"bypass _bbscharacter_class;fighter\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=170><button value=\"Mage\" action=\"bypass _bbscharacter_class;mage\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("<td align=center width=170><button value=\"All Classes\" action=\"bypass _bbscharacter_class;all\" width=100 height=30 back=\"L2UI_CH3.btn1_normal\" fore=\"L2UI_CH3.btn1_down\"></td>");
        sb.append("</tr></table>");
        sb.append("</td></tr>");

        sb.append("<tr><td height=20></td></tr>");
        sb.append("</table>");
        sb.append("<table border=0 cellpadding=0 cellspacing=0 width=550>");
        sb.append("<tr><td height=30 align=center><font color=888888><br>LINEAGE II PvP x1000 - COMMUNITY BOARD</font></td></tr>");
        sb.append("</table></center></td></tr></table></body></html>");

        String html = sb.toString().replace("%navigation%", HtmCache.getInstance().getHtm(null, "data/html/CommunityBoard/Custom/navigation.html"));
        return html;
    }

    private String handleClassConfirm(Player player, String command)
    {
        String classType = command.substring(command.indexOf(";") + 1);
        // For PvP server, allow any class - simplified
        // In real implementation, would show class selection list
        player.sendMessage("Class change system ready. Use admin command for specific class.");
        return generateMainPage(player);
    }

    public static void register()
    {
        CommunityBoardHandler.getInstance().registerHandler(new CharacterBoardHandler());
    }
}
