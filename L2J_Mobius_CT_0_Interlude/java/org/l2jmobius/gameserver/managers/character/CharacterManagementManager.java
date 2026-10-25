package org.l2jmobius.gameserver.managers.character;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.base.ClassId;
import org.l2jmobius.gameserver.network.SystemMessageId;
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
 * Character Management Manager
 * Handles: Nickname change, Gender change, Character delete/restore, Stuck character, Class change
 * @author Mobius Extended
 */
public class CharacterManagementManager
{
    private static final Logger LOGGER = Logger.getLogger(CharacterManagementManager.class.getName());

    private final Map<Integer, CharacterManagementData> _playerData = new ConcurrentHashMap<>();
    private ScheduledFuture<?> _cleanupTask;

    public static CharacterManagementManager getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private CharacterManagementManager()
    {
        if (CustomSystemsConfig.CHARACTER_MANAGEMENT_ENABLED)
        {
            loadData();
            startCleanupTask();
            LOGGER.info(getClass().getSimpleName() + ": Character Management System initialized.");
        }
    }

    public void reload()
    {
        if (_cleanupTask != null) _cleanupTask.cancel(false);
        _playerData.clear();
        loadData();
        startCleanupTask();
    }

    private void loadData()
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM character_management_data");
             ResultSet rs = ps.executeQuery())
        {
            int count = 0;
            while (rs.next())
            {
                CharacterManagementData data = new CharacterManagementData();
                data.charId = rs.getInt("char_id");
                data.nicknameChanges = rs.getInt("nickname_changes");
                data.lastNicknameChange = rs.getLong("last_nickname_change");
                data.genderChanges = rs.getInt("gender_changes");
                data.lastGenderChange = rs.getLong("last_gender_change");
                data.classChanges = rs.getInt("class_changes");
                data.lastClassChange = rs.getLong("last_class_change");
                data.deletionRequested = rs.getInt("deletion_requested") == 1;
                data.deletionTime = rs.getLong("deletion_time");
                _playerData.put(data.charId, data);
                count++;
            }
            LOGGER.info(getClass().getSimpleName() + ": Loaded " + count + " character management records.");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to load character management data: ", e);
        }
    }

    private void startCleanupTask()
    {
        _cleanupTask = ThreadPool.scheduleAtFixedRate(this::cleanupExpiredDeletions, 3600000, 3600000);
    }

    private void cleanupExpiredDeletions()
    {
        long now = System.currentTimeMillis();
        for (CharacterManagementData data : _playerData.values())
        {
            if (data.deletionRequested && data.deletionTime > 0 && now >= data.deletionTime)
            {
                // Actually delete the character
                deleteCharacterPermanently(data.charId);
            }
        }
    }

    // ===== NICKNAME CHANGE =====
    public boolean canChangeNickname(Player player)
    {
        if (!CustomSystemsConfig.NICKNAME_CHANGE_ENABLED) return false;
        
        CharacterManagementData data = _playerData.get(player.getObjectId());
        if (data == null)
        {
            data = new CharacterManagementData(player.getObjectId());
            _playerData.put(player.getObjectId(), data);
        }

        long now = System.currentTimeMillis();
        long cooldown = CustomSystemsConfig.NICKNAME_CHANGE_COOLDOWN * 3600000L;
        
        if (now - data.lastNicknameChange < cooldown)
        {
            return false;
        }

        if (data.nicknameChanges >= CustomSystemsConfig.NICKNAME_CHANGE_MAX_PER_MONTH)
        {
            // Check if month has passed
            if (now - data.lastNicknameChange < 30L * 24 * 3600000L)
            {
                return false;
            }
            data.nicknameChanges = 0; // Reset monthly counter
        }

        return true;
    }

    public void changeNickname(Player player, String newName)
    {
        if (!canChangeNickname(player)) return;

        // Validate name
        if (!isValidName(newName))
        {
            player.sendMessage("Invalid name format.");
            return;
        }

        // Check if name is taken
        if (isNameTaken(newName))
        {
            player.sendMessage("Name already taken.");
            return;
        }

        // Check cost
        int cost = CustomSystemsConfig.NICKNAME_CHANGE_COST;
        if (!player.reduceAdena("Nickname Change", cost, null, true))
        {
            player.sendMessage("Not enough adena.");
            return;
        }

        // Change name
        String oldName = player.getName();
        player.setName(newName);
        player.broadcastUserInfo();

        // Update data
        CharacterManagementData data = _playerData.get(player.getObjectId());
        data.nicknameChanges++;
        data.lastNicknameChange = System.currentTimeMillis();
        savePlayerData(data);

        // Log
        logCharacterAction(player.getObjectId(), "NICKNAME_CHANGE", "Old: " + oldName + " -> New: " + newName);

        player.sendMessage("Nickname changed successfully to " + newName + "!");
        broadcastAnnouncement(player.getName() + " changed nickname from " + oldName + " to " + newName);
    }

    // ===== GENDER CHANGE =====
    public boolean canChangeGender(Player player)
    {
        if (!CustomSystemsConfig.GENDER_CHANGE_ENABLED) return false;

        CharacterManagementData data = _playerData.get(player.getObjectId());
        if (data == null) return true;

        long now = System.currentTimeMillis();
        long cooldown = CustomSystemsConfig.GENDER_CHANGE_COOLDOWN * 3600000L;
        
        return now - data.lastGenderChange >= cooldown;
    }

    public void changeGender(Player player)
    {
        if (!canChangeGender(player)) return;

        int cost = CustomSystemsConfig.GENDER_CHANGE_COST;
        if (!player.reduceAdena("Gender Change", cost, null, true))
        {
            player.sendMessage("Not enough adena.");
            return;
        }

        // Toggle gender
        player.getAppearance().setSex(!player.getAppearance().getSex());
        player.broadcastUserInfo();
        player.decayMe();
        player.spawnMe(player.getX(), player.getY(), player.getZ());

        // Update data
        CharacterManagementData data = _playerData.computeIfAbsent(player.getObjectId(), k -> new CharacterManagementData(player.getObjectId()));
        data.genderChanges++;
        data.lastGenderChange = System.currentTimeMillis();
        savePlayerData(data);

        logCharacterAction(player.getObjectId(), "GENDER_CHANGE", "Gender changed");
        player.sendMessage("Gender changed successfully!");
    }

    // ===== CHARACTER DELETION =====
    public void requestCharacterDeletion(Player player)
    {
        if (!CustomSystemsConfig.CHARACTER_DELETION_ENABLED) return;

        CharacterManagementData data = _playerData.computeIfAbsent(player.getObjectId(), k -> new CharacterManagementData(player.getObjectId()));
        
        if (data.deletionRequested)
        {
            player.sendMessage("Character deletion already requested.");
            return;
        }

        long delay = CustomSystemsConfig.CHARACTER_DELETION_DELAY_HOURS * 3600000L;
        data.deletionRequested = true;
        data.deletionTime = System.currentTimeMillis() + delay;
        savePlayerData(data);

        player.sendMessage("Character deletion requested. It will be deleted in " + CustomSystemsConfig.CHARACTER_DELETION_DELAY_HOURS + " hours. Use cancel to abort.");
    }

    public void cancelCharacterDeletion(Player player)
    {
        CharacterManagementData data = _playerData.get(player.getObjectId());
        if (data == null || !data.deletionRequested)
        {
            player.sendMessage("No deletion request pending.");
            return;
        }

        data.deletionRequested = false;
        data.deletionTime = 0;
        savePlayerData(data);

        player.sendMessage("Character deletion cancelled.");
    }

    private void deleteCharacterPermanently(int charId)
    {
        ThreadPool.execute(() ->
        {
            try (Connection con = DatabaseFactory.getConnection())
            {
                // Delete character and all related data
                String[] tables = {
                    "characters", "character_skills", "character_skills_save", "character_hennas",
                    "character_shortcuts", "character_macros", "character_recipebook",
                    "character_quests", "character_recommends", "items", "items_ground",
                    "character_friends", "character_blocklist", "character_ui_categories",
                    "character_ui_keys", "character_variables", "character_cursed_weapons",
                    "character_management_data", "character_pvp_stats", "character_class_rating",
                    "character_currencies", "character_quests", "character_achievements",
                    "character_battle_pass", "character_buff_schemes", "character_daily_rewards",
                    "character_vote_rewards", "character_hwid", "character_talents"
                };

                for (String table : tables)
                {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + table + " WHERE char_id = ?"))
                    {
                        ps.setInt(1, charId);
                        ps.executeUpdate();
                    }
                    catch (Exception ignored) {}
                }

                LOGGER.info("Character " + charId + " permanently deleted.");
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING, "Failed to delete character " + charId, e);
            }
        });
    }

    // ===== STUCK CHARACTER =====
    public void unstuckCharacter(Player player)
    {
        if (!CustomSystemsConfig.UNSTUCK_ENABLED) return;

        long now = System.currentTimeMillis();
        CharacterManagementData data = _playerData.get(player.getObjectId());
        if (data != null)
        {
            long cooldown = CustomSystemsConfig.UNSTUCK_COOLDOWN * 60000L;
            if (now - data.lastUnstuck < cooldown)
            {
                player.sendMessage("Unstuck is on cooldown. Try again later.");
                return;
            }
            data.lastUnstuck = now;
            savePlayerData(data);
        }

        // Teleport to nearest town
        int[] loc = getNearestTownLocation(player.getX(), player.getY(), player.getZ());
        player.teleToLocation(loc[0], loc[1], loc[2], 0);
        player.sendMessage("Character unstuck! Teleported to nearest town.");
    }

    // ===== CLASS CHANGE =====
    public boolean canChangeClass(Player player, int newClassId)
    {
        if (!CustomSystemsConfig.CLASS_CHANGE_ENABLED) return false;

        // Check if valid class change path
        ClassId currentClass = player.getClassId();
        ClassId targetClass = ClassId.values()[newClassId];
        
        if (!isValidClassChange(currentClass, targetClass))
        {
            return false;
        }

        CharacterManagementData data = _playerData.get(player.getObjectId());
        if (data == null) return true;

        long now = System.currentTimeMillis();
        long cooldown = CustomSystemsConfig.CLASS_CHANGE_COOLDOWN * 3600000L;
        
        return now - data.lastClassChange >= cooldown;
    }

    public void changeClass(Player player, int newClassId)
    {
        if (!canChangeClass(player, newClassId)) return;

        int cost = CustomSystemsConfig.CLASS_CHANGE_COST;
        if (!player.reduceAdena("Class Change", cost, null, true))
        {
            player.sendMessage("Not enough adena.");
            return;
        }

        player.setClassId(newClassId);
        if (player.isSubClassActive())
        {
            player.getSubClasses().get(player.getClassIndex()).setClassId(newClassId);
        }
        else
        {
            player.setBaseClass(newClassId);
        }

        // Refresh skills
        player.rewardSkills();
        player.sendSkillList();
        player.broadcastUserInfo();

        // Update data
        CharacterManagementData data = _playerData.computeIfAbsent(player.getObjectId(), k -> new CharacterManagementData(player.getObjectId()));
        data.classChanges++;
        data.lastClassChange = System.currentTimeMillis();
        savePlayerData(data);

        logCharacterAction(player.getObjectId(), "CLASS_CHANGE", "New class: " + ClassId.values()[newClassId].name());
        player.sendMessage("Class changed successfully!");
    }

    // ===== HELPER METHODS =====
    private boolean isValidName(String name)
    {
        return name != null && name.length() >= 3 && name.length() <= 16 && name.matches("^[a-zA-Z0-9_-]+$");
    }

    private boolean isNameTaken(String name)
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM characters WHERE char_name = ?"))
        {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next();
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to check name availability", e);
            return true;
        }
    }

    private int[] getNearestTownLocation(int x, int y, int z)
    {
        // Simplified - return Giran as default
        return new int[]{82698, 148638, -3473};
    }

    private boolean isValidClassChange(ClassId current, ClassId target)
    {
        // Simplified - allow any class change for PvP server
        return true;
    }

    private void broadcastAnnouncement(String msg)
    {
        for (Player p : org.l2jmobius.gameserver.model.World.getInstance().getPlayers())
        {
            if (p != null && p.isOnline())
            {
                p.sendPacket(new org.l2jmobius.gameserver.network.serverpackets.CreatureSay(0, 18, "System", msg));
            }
        }
    }

    private void logCharacterAction(int charId, String action, String details)
    {
        ThreadPool.execute(() ->
        {
            try (Connection con = DatabaseFactory.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO character_action_log (char_id, action, details, timestamp) VALUES (?,?,?,?)"))
            {
                ps.setInt(1, charId);
                ps.setString(2, action);
                ps.setString(3, details);
                ps.setLong(4, System.currentTimeMillis());
                ps.execute();
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING, "Failed to log character action", e);
            }
        });
    }

    private void savePlayerData(CharacterManagementData data)
    {
        ThreadPool.execute(() ->
        {
            try (Connection con = DatabaseFactory.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO character_management_data (char_id, nickname_changes, last_nickname_change, gender_changes, last_gender_change, class_changes, last_class_change, deletion_requested, deletion_time, last_unstuck) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE nickname_changes=?, last_nickname_change=?, gender_changes=?, last_gender_change=?, class_changes=?, last_class_change=?, deletion_requested=?, deletion_time=?, last_unstuck=?"))
            {
                int idx = 1;
                ps.setInt(idx++, data.charId);
                ps.setInt(idx++, data.nicknameChanges);
                ps.setLong(idx++, data.lastNicknameChange);
                ps.setInt(idx++, data.genderChanges);
                ps.setLong(idx++, data.lastGenderChange);
                ps.setInt(idx++, data.classChanges);
                ps.setLong(idx++, data.lastClassChange);
                ps.setInt(idx++, data.deletionRequested ? 1 : 0);
                ps.setLong(idx++, data.deletionTime);
                ps.setLong(idx++, data.lastUnstuck);
                // Update
                ps.setInt(idx++, data.nicknameChanges);
                ps.setLong(idx++, data.lastNicknameChange);
                ps.setInt(idx++, data.genderChanges);
                ps.setLong(idx++, data.lastGenderChange);
                ps.setInt(idx++, data.classChanges);
                ps.setLong(idx++, data.lastClassChange);
                ps.setInt(idx++, data.deletionRequested ? 1 : 0);
                ps.setLong(idx++, data.deletionTime);
                ps.setLong(idx++, data.lastUnstuck);
                ps.execute();
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING, "Failed to save character management data", e);
            }
        });
    }

    public static class CharacterManagementData
    {
        int charId;
        int nicknameChanges = 0;
        long lastNicknameChange = 0;
        int genderChanges = 0;
        long lastGenderChange = 0;
        int classChanges = 0;
        long lastClassChange = 0;
        boolean deletionRequested = false;
        long deletionTime = 0;
        long lastUnstuck = 0;

        CharacterManagementData() {}
        CharacterManagementData(int charId) { this.charId = charId; }
    }

    private static class SingletonHolder
    {
        protected static final CharacterManagementManager INSTANCE = new CharacterManagementManager();
    }
}
