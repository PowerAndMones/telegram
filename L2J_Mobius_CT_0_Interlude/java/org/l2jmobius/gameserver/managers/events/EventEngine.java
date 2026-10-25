package org.l2jmobius.gameserver.managers.events;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.CustomSystemsConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Door;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Event Engine for automated PvP events.
 * Supports: TvT, CTF, DM, LMS, Arena (1v1, 2v2, 3v3), GvG (5v5, 9v9)
 * @author Mobius Extended
 */
public class EventEngine
{
    private static final Logger LOGGER = Logger.getLogger(EventEngine.class.getName());

    private final Map<Integer, AbstractEvent> _activeEvents = new ConcurrentHashMap<>();
    private final Map<String, List<Integer>> _registeredPlayers = new ConcurrentHashMap<>();
    private final Map<Integer, ScheduledEvent> _scheduledEvents = new ConcurrentHashMap<>();
    private final AtomicInteger _eventIdCounter = new AtomicInteger(1);
    private ScheduledFuture<?> _schedulerTask;
    private ScheduledFuture<?> _announcementTask;

    public static EventEngine getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private EventEngine()
    {
        if (CustomSystemsConfig.EVENT_ENGINE_ENABLED)
        {
            loadSchedule();
            startScheduler();
            startAnnouncements();
            LOGGER.info(getClass().getSimpleName() + ": Event Engine started.");
        }
    }

    public void reload()
    {
        stopAllEvents();
        if (_schedulerTask != null) _schedulerTask.cancel(false);
        if (_announcementTask != null) _announcementTask.cancel(false);
        _activeEvents.clear();
        _registeredPlayers.clear();
        _scheduledEvents.clear();
        loadSchedule();
        startScheduler();
        startAnnouncements();
    }

    private void loadSchedule()
    {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM event_schedule WHERE is_active = 1");
             ResultSet rs = ps.executeQuery())
        {
            int count = 0;
            while (rs.next())
            {
                final ScheduledEvent event = new ScheduledEvent();
                event.id = rs.getInt("id");
                event.type = rs.getString("event_type");
                event.name = rs.getString("event_name");
                event.cron = rs.getString("cron_expression");
                event.minLevel = rs.getInt("min_level");
                event.maxLevel = rs.getInt("max_level");
                event.minPlayers = rs.getInt("min_players");
                event.maxPlayers = rs.getInt("max_players");
                event.rewards = rs.getString("reward_items");
                _scheduledEvents.put(event.id, event);
                count++;
            }
            LOGGER.info(getClass().getSimpleName() + ": Loaded " + count + " scheduled events.");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to load event schedule: ", e);
        }
    }

    private void startScheduler()
    {
        _schedulerTask = ThreadPool.scheduleAtFixedRate(this::checkScheduledEvents, 30000, 30000);
    }

    private void startAnnouncements()
    {
        _announcementTask = ThreadPool.scheduleAtFixedRate(this::sendPeriodicAnnouncements, 300000, 300000);
    }

    private void checkScheduledEvents()
    {
        final long now = System.currentTimeMillis();
        for (ScheduledEvent event : _scheduledEvents.values())
        {
            if (shouldRunNow(event.cron, now) && !_activeEvents.containsKey(event.id))
            {
                startEvent(event);
            }
        }
    }

    private boolean shouldRunNow(String cron, long now)
    {
        try
        {
            final String[] parts = cron.split(" ");
            if (parts.length != 5) return false;

            final java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(now);

            final int min = cal.get(java.util.Calendar.MINUTE);
            final int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            final int day = cal.get(java.util.Calendar.DAY_OF_MONTH);
            final int month = cal.get(java.util.Calendar.MONTH) + 1;
            final int weekday = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1;

            if (!matchesCronPart(parts[0], min)) return false;
            if (!matchesCronPart(parts[1], hour)) return false;
            if (!matchesCronPart(parts[2], day)) return false;
            if (!matchesCronPart(parts[3], month)) return false;
            if (!matchesCronPart(parts[4], weekday)) return false;

            return event.lastRun != now / 60000;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private boolean matchesCronPart(String part, int value)
    {
        if (part.equals("*")) return true;
        if (part.contains("-"))
        {
            final String[] range = part.split("-");
            return value >= Integer.parseInt(range[0]) && value <= Integer.parseInt(range[1]);
        }
        if (part.contains(","))
        {
            for (String v : part.split(","))
            {
                if (Integer.parseInt(v) == value) return true;
            }
            return false;
        }
        return Integer.parseInt(part) == value;
    }

    private void startEvent(ScheduledEvent scheduled)
    {
        AbstractEvent event = createEvent(scheduled.type, scheduled);
        if (event == null)
        {
            LOGGER.warning("Unknown event type: " + scheduled.type);
            return;
        }

        _activeEvents.put(scheduled.id, event);
        scheduled.lastRun = System.currentTimeMillis() / 60000;

        announceEventStart(event);

        ThreadPool.schedule(() -> openRegistration(event), 1000);
    }

    private AbstractEvent createEvent(String type, ScheduledEvent scheduled)
    {
        switch (type.toUpperCase())
        {
            case "TVT":
                return new TvTEvent(scheduled);
            case "CTF":
                return new CTFEvent(scheduled);
            case "DM":
                return new DeathmatchEvent(scheduled);
            case "LMS":
                return new LMSEvent(scheduled);
            case "ARENA_1V1":
                return new ArenaEvent(scheduled, 1);
            case "ARENA_2V2":
                return new ArenaEvent(scheduled, 2);
            case "ARENA_3V3":
                return new ArenaEvent(scheduled, 3);
            case "GVG_5V5":
                return new GvGEvent(scheduled, 5);
            case "GVG_9V9":
                return new GvGEvent(scheduled, 9);
            default:
                return null;
        }
    }

    private void openRegistration(AbstractEvent event)
    {
        event.setState(EventState.REGISTRATION);
        announceRegistrationOpen(event);

        ThreadPool.schedule(() -> closeRegistration(event), CustomSystemsConfig.EVENT_ANNOUNCE_TIME * 60 * 1000);
    }

    private void closeRegistration(AbstractEvent event)
    {
        if (event.getState() != EventState.REGISTRATION)
        {
            return;
        }

        final List<Player> participants = getRegisteredPlayers(event.getType());
        if (participants.size() < CustomSystemsConfig.EVENT_MIN_PLAYERS)
        {
            announceEventCancelled(event, "Not enough participants (" + participants.size() + "/" + CustomSystemsConfig.EVENT_MIN_PLAYERS + ")");
            cancelEvent(event.getId());
            return;
        }

        if (participants.size() > CustomSystemsConfig.EVENT_MAX_PLAYERS)
        {
            Collections.shuffle(participants);
            final List<Player> selected = participants.subList(0, CustomSystemsConfig.EVENT_MAX_PLAYERS);
            for (Player p : participants)
            {
                if (!selected.contains(p))
                {
                    unregisterPlayer(p, event.getType());
                    p.sendMessage("Event registration full. You were not selected.");
                }
            }
        }

        startEventBattle(event);
    }

    private void startEventBattle(AbstractEvent event)
    {
        event.setState(EventState.STARTING);
        event.teleportPlayersToArena();
        ThreadPool.schedule(() -> event.startBattle(), 10000);
    }

    public void registerPlayer(Player player, String eventType)
    {
        if (!_registeredPlayers.containsKey(eventType))
        {
            _registeredPlayers.put(eventType, Collections.synchronizedList(new ArrayList<>()));
        }

        final List<Integer> list = _registeredPlayers.get(eventType);
        if (list.contains(player.getObjectId()))
        {
            player.sendMessage("You are already registered for this event.");
            return;
        }

        final ScheduledEvent scheduled = getScheduledEventByType(eventType);
        if (scheduled != null)
        {
            if (player.getLevel() < scheduled.minLevel || player.getLevel() > scheduled.maxLevel)
            {
                player.sendMessage("Your level doesn't meet event requirements (" + scheduled.minLevel + "-" + scheduled.maxLevel + ").");
                return;
            }
        }

        if (!CustomSystemsConfig.EVENT_ALLOW_SAME_IP)
        {
            // Check dualbox
            for (int objId : list)
            {
                final Player p = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(objId);
                if (p != null && p.getIPAddress().equals(player.getIPAddress()))
                {
                    player.sendMessage("A player from your IP is already registered.");
                    return;
                }
            }
        }

        list.add(player.getObjectId());
        player.sendMessage("Successfully registered for " + eventType + "!");
    }

    public void unregisterPlayer(Player player, String eventType)
    {
        final List<Integer> list = _registeredPlayers.get(eventType);
        if (list != null)
        {
            list.remove(Integer.valueOf(player.getObjectId()));
        }
    }

    public List<Player> getRegisteredPlayers(String eventType)
    {
        final List<Player> result = new ArrayList<>();
        final List<Integer> list = _registeredPlayers.get(eventType);
        if (list != null)
        {
            for (int objId : list)
            {
                final Player player = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(objId);
                if (player != null && player.isOnline())
                {
                    result.add(player);
                }
            }
        }
        return result;
    }

    public void cancelEvent(int eventId)
    {
        final AbstractEvent event = _activeEvents.remove(eventId);
        if (event != null)
        {
            event.abort();
        }
        final List<Integer> list = _registeredPlayers.remove(event.getType());
        if (list != null) list.clear();
    }

    public void stopAllEvents()
    {
        for (AbstractEvent event : _activeEvents.values())
        {
            event.abort();
        }
        _activeEvents.clear();
        _registeredPlayers.clear();
    }

    private void announceEventStart(AbstractEvent event)
    {
        final String msg = "Event " + event.getName() + " (" + event.getType() + ") will start in " + CustomSystemsConfig.EVENT_ANNOUNCE_TIME + " minutes! Register via Community Board (Alt+B) -> Events.";
        broadcastMessage(msg);
    }

    private void announceRegistrationOpen(AbstractEvent event)
    {
        final String msg = "Registration for " + event.getName() + " is now OPEN! Use Alt+B -> Events to join.";
        broadcastMessage(msg);
    }

    private void announceEventCancelled(AbstractEvent event, String reason)
    {
        final String msg = "Event " + event.getName() + " cancelled: " + reason;
        broadcastMessage(msg);
    }

    private void broadcastMessage(String msg)
    {
        for (Player player : org.l2jmobius.gameserver.model.World.getInstance().getPlayers())
        {
            if (player != null && player.isOnline())
            {
                player.sendPacket(new CreatureSay(0, 18, "Event Manager", msg));
                player.sendPacket(new ExShowScreenMessage(msg, 10000));
            }
        }
    }

    private void sendPeriodicAnnouncements()
    {
        if (_scheduledEvents.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("<Event Schedule>");
        for (ScheduledEvent e : _scheduledEvents.values())
        {
            sb.append("\n").append(e.name).append(" (").append(e.type).append(") - Check Alt+B for schedule!");
        }
        final String msg = sb.toString();

        for (Player player : org.l2jmobius.gameserver.model.World.getInstance().getPlayers())
        {
            if (player != null && player.isOnline())
            {
                player.sendPacket(new CreatureSay(0, 18, "Event Manager", msg));
            }
        }
    }

    private ScheduledEvent getScheduledEventByType(String type)
    {
        for (ScheduledEvent e : _scheduledEvents.values())
        {
            if (e.type.equalsIgnoreCase(type))
            {
                return e;
            }
        }
        return null;
    }

    // ===== Abstract Event Classes =====

    public enum EventState
    {
        INACTIVE, REGISTRATION, STARTING, RUNNING, FINISHED, ABORTED
    }

    public abstract static class AbstractEvent
    {
        protected final ScheduledEvent _scheduled;
        protected EventState _state = EventState.INACTIVE;
        protected List<Player> _participants = new ArrayList<>();
        protected Map<Integer, Integer> _scores = new ConcurrentHashMap<>();
        protected Map<Integer, Integer> _kills = new ConcurrentHashMap<>();
        protected Map<Integer, Integer> _deaths = new ConcurrentHashMap<>();
        protected int _winningTeam = 0;
        protected int _winnerId = 0;

        protected AbstractEvent(ScheduledEvent scheduled)
        {
            _scheduled = scheduled;
        }

        public int getId() { return _scheduled.id; }
        public String getType() { return _scheduled.type; }
        public String getName() { return _scheduled.name; }
        public EventState getState() { return _state; }
        public void setState(EventState state) { _state = state; }
        public List<Player> getParticipants() { return _participants; }

        public abstract void teleportPlayersToArena();
        public abstract void startBattle();
        public abstract void onPlayerDeath(Player victim, Player killer);
        public abstract void onPlayerKill(Player killer, Player victim);
        public abstract void checkEndCondition();
        public abstract void finishEvent();
        public abstract void abort();

        protected void rewardParticipants()
        {
            if (_scheduled.rewards == null || _scheduled.rewards.isEmpty()) return;

            for (Player player : _participants)
            {
                if (player == null || !player.isOnline()) continue;

                final String[] rewards = _scheduled.rewards.split(";");
                for (String reward : rewards)
                {
                    final String[] parts = reward.split(",");
                    if (parts.length == 2)
                    {
                        final int itemId = Integer.parseInt(parts[0]);
                        final long count = Long.parseLong(parts[1]);
                        player.addItem("Event Reward", itemId, count, player, true);
                    }
                }
            }
        }

        protected void saveEventHistory()
        {
            ThreadPool.execute(() ->
            {
                try (Connection con = DatabaseFactory.getConnection();
                     PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO event_history (event_type, event_name, winner_team, winner_char, participants_count, start_time, end_time, rewards_data) " +
                         "VALUES (?,?,?,?,?,?,?,?)"))
                {
                    ps.setString(1, _scheduled.type);
                    ps.setString(2, _scheduled.name);
                    ps.setInt(3, _winningTeam);
                    ps.setInt(4, _winnerId);
                    ps.setInt(5, _participants.size());
                    ps.setLong(6, _scheduled.startTime);
                    ps.setLong(7, System.currentTimeMillis());
                    ps.setString(8, _scheduled.rewards);
                    ps.execute();
                }
                catch (Exception e)
                {
                    LOGGER.log(Level.WARNING, "Failed to save event history", e);
                }
            });
        }
    }

    // ===== Concrete Event Types =====

    public static class TvTEvent extends AbstractEvent
    {
        private final List<Player> _team1 = new ArrayList<>();
        private final List<Player> _team2 = new ArrayList<>();
        private int _team1Score = 0;
        private int _team2Score = 0;
        private static final int MAX_SCORE = 50;

        public TvTEvent(ScheduledEvent scheduled)
        {
            super(scheduled);
        }

        @Override
        public void teleportPlayersToArena()
        {
            _participants = getRegisteredPlayers(_scheduled.type);
            if (_participants.isEmpty()) return;

            Collections.shuffle(_participants);
            final int half = _participants.size() / 2;
            _team1.addAll(_participants.subList(0, half));
            _team2.addAll(_participants.subList(half, _participants.size()));

            final Location[] team1Spawns = { new Location(-80000, 250000, -3200), new Location(-81000, 250000, -3200) };
            final Location[] team2Spawns = { new Location(-70000, 250000, -3200), new Location(-69000, 250000, -3200) };

            for (int i = 0; i < _team1.size(); i++)
            {
                final Player p = _team1.get(i);
                if (p != null) p.teleToLocation(team1Spawns[i % team1Spawns.length], 0);
            }
            for (int i = 0; i < _team2.size(); i++)
            {
                final Player p = _team2.get(i);
                if (p != null) p.teleToLocation(team2Spawns[i % team2Spawns.length], 0);
            }
        }

        @Override
        public void startBattle()
        {
            _state = EventState.RUNNING;
            _scheduled.startTime = System.currentTimeMillis();

            for (Player p : _participants)
            {
                if (p != null)
                {
                    p.sendPacket(new ExShowScreenMessage("TvT Event Started! Kill enemy team members!", 5000));
                    p.broadcastUserInfo();
                }
            }
        }

        @Override
        public void onPlayerDeath(Player victim, Player killer)
        {
            if (_state != EventState.RUNNING || killer == null || victim == null) return;

            if (_team1.contains(killer) && _team2.contains(victim))
            {
                _team1Score++;
                _kills.merge(killer.getObjectId(), 1, Integer::sum);
                _deaths.merge(victim.getObjectId(), 1, Integer::sum);
                checkEndCondition();
            }
            else if (_team2.contains(killer) && _team1.contains(victim))
            {
                _team2Score++;
                _kills.merge(killer.getObjectId(), 1, Integer::sum);
                _deaths.merge(victim.getObjectId(), 1, Integer::sum);
                checkEndCondition();
            }

            ThreadPool.schedule(() ->
            {
                if (victim != null && victim.isOnline())
                {
                    victim.doRevive();
                    victim.setCurrentHpMp(victim.getMaxHp(), victim.getMaxMp());
                    victim.setCurrentCp(victim.getMaxCp());
                    if (_team1.contains(victim))
                        victim.teleToLocation(new Location(-80000, 250000, -3200), 0);
                    else
                        victim.teleToLocation(new Location(-70000, 250000, -3200), 0);
                }
            }, 5000);
        }

        @Override
        public void onPlayerKill(Player killer, Player victim) { }

        @Override
        public void checkEndCondition()
        {
            if (_team1Score >= MAX_SCORE || _team2Score >= MAX_SCORE)
            {
                finishEvent();
            }
        }

        @Override
        public void finishEvent()
        {
            _state = EventState.FINISHED;

            if (_team1Score > _team2Score)
            {
                _winningTeam = 1;
                announceWinner(_team1, "Team 1 (Blue) wins TvT with " + _team1Score + " points!");
            }
            else if (_team2Score > _team1Score)
            {
                _winningTeam = 2;
                announceWinner(_team2, "Team 2 (Red) wins TvT with " + _team2Score + " points!");
            }
            else
            {
                announceToAll("TvT Event ended in a draw!");
            }

            rewardParticipants();
            saveEventHistory();
            teleportPlayersBack();

            ThreadPool.schedule(() -> EventEngine.getInstance().cancelEvent(getId()), 30000);
        }

        @Override
        public void abort()
        {
            _state = EventState.ABORTED;
            teleportPlayersBack();
        }

        private void announceWinner(List<Player> winners, String msg)
        {
            for (Player p : _participants)
            {
                if (p != null && p.isOnline())
                {
                    p.sendPacket(new ExShowScreenMessage(msg, 10000));
                }
            }
        }

        private void announceToAll(String msg)
        {
            for (Player p : _participants)
            {
                if (p != null && p.isOnline())
                {
                    p.sendPacket(new ExShowScreenMessage(msg, 10000));
                }
            }
        }

        private void teleportPlayersBack()
        {
            for (Player p : _participants)
            {
                if (p != null && p.isOnline())
                {
                    p.teleToLocation(p.getLastTownTeleport(), 0);
                }
            }
        }
    }

    public static class CTFEvent extends AbstractEvent
    {
        private final List<Player> _team1 = new ArrayList<>();
        private final List<Player> _team2 = new ArrayList<>();
        private int _team1Flags = 0;
        private int _team2Flags = 0;
        private static final int MAX_FLAGS = 5;

        public CTFEvent(ScheduledEvent scheduled) { super(scheduled); }

        @Override public void teleportPlayersToArena()
        {
            _participants = getRegisteredPlayers(_scheduled.type);
            if (_participants.isEmpty()) return;

            Collections.shuffle(_participants);
            final int half = _participants.size() / 2;
            _team1.addAll(_participants.subList(0, half));
            _team2.addAll(_participants.subList(half, _participants.size()));

            final Location[] team1Spawns = { new Location(-80000, 250000, -3200) };
            final Location[] team2Spawns = { new Location(-70000, 250000, -3200) };

            for (int i = 0; i < _team1.size(); i++) if (_team1.get(i) != null) _team1.get(i).teleToLocation(team1Spawns[i % team1Spawns.length], 0);
            for (int i = 0; i < _team2.size(); i++) if (_team2.get(i) != null) _team2.get(i).teleToLocation(team2Spawns[i % team2Spawns.length], 0);
        }

        @Override public void startBattle() { _state = EventState.RUNNING; _scheduled.startTime = System.currentTimeMillis(); }
        @Override public void onPlayerDeath(Player victim, Player killer) { /* flag drop logic */ respawn(victim); }
        @Override public void onPlayerKill(Player killer, Player victim) { }
        @Override public void checkEndCondition() { if (_team1Flags >= MAX_FLAGS || _team2Flags >= MAX_FLAGS) finishEvent(); }
        @Override public void finishEvent() { _state = EventState.FINISHED; rewardParticipants(); saveEventHistory(); teleportPlayersBack(); cancelAfterDelay(); }
        @Override public void abort() { _state = EventState.ABORTED; teleportPlayersBack(); }
        private void respawn(Player victim) { /* respawn logic */ }
        private void teleportPlayersBack() { for (Player p : _participants) if (p != null && p.isOnline()) p.teleToLocation(p.getLastTownTeleport(), 0); }
        private void cancelAfterDelay() { ThreadPool.schedule(() -> EventEngine.getInstance().cancelEvent(getId()), 30000); }
    }

    public static class DeathmatchEvent extends AbstractEvent
    {
        private static final int MAX_KILLS = 30;

        public DeathmatchEvent(ScheduledEvent scheduled) { super(scheduled); }

        @Override public void teleportPlayersToArena()
        {
            _participants = getRegisteredPlayers(_scheduled.type);
            if (_participants.isEmpty()) return;
            final Location spawn = new Location(-75000, 250000, -3200);
            for (Player p : _participants) if (p != null) p.teleToLocation(spawn, 0);
        }

        @Override public void startBattle() { _state = EventState.RUNNING; _scheduled.startTime = System.currentTimeMillis(); }
        @Override public void onPlayerDeath(Player victim, Player killer) { _kills.merge(killer.getObjectId(), 1, Integer::sum); checkEndCondition(); respawn(victim); }
        @Override public void onPlayerKill(Player killer, Player victim) { }
        @Override public void checkEndCondition() { if (_kills.values().stream().max(Integer::compare).orElse(0) >= MAX_KILLS) finishEvent(); }
        @Override public void finishEvent() { _state = EventState.FINISHED; /* announce winner */ rewardParticipants(); saveEventHistory(); teleportPlayersBack(); cancelAfterDelay(); }
        @Override public void abort() { _state = EventState.ABORTED; teleportPlayersBack(); }
        private void respawn(Player victim) { ThreadPool.schedule(() -> { if (victim != null && victim.isOnline()) { victim.doRevive(); victim.setCurrentHpMp(victim.getMaxHp(), victim.getMaxMp()); victim.setCurrentCp(victim.getMaxCp()); victim.teleToLocation(new Location(-75000, 250000, -3200), 0); } }, 5000); }
        private void teleportPlayersBack() { for (Player p : _participants) if (p != null && p.isOnline()) p.teleToLocation(p.getLastTownTeleport(), 0); }
        private void cancelAfterDelay() { ThreadPool.schedule(() -> EventEngine.getInstance().cancelEvent(getId()), 30000); }
    }

    public static class LMSEvent extends AbstractEvent
    {
        public LMSEvent(ScheduledEvent scheduled) { super(scheduled); }

        @Override public void teleportPlayersToArena()
        {
            _participants = getRegisteredPlayers(_scheduled.type);
            if (_participants.isEmpty()) return;
            final Location spawn = new Location(-75000, 250000, -3200);
            for (Player p : _participants) if (p != null) p.teleToLocation(spawn, 0);
        }

        @Override public void startBattle() { _state = EventState.RUNNING; _scheduled.startTime = System.currentTimeMillis(); }
        @Override public void onPlayerDeath(Player victim, Player killer) { _participants.remove(victim); checkEndCondition(); }
        @Override public void onPlayerKill(Player killer, Player victim) { _kills.merge(killer.getObjectId(), 1, Integer::sum); }
        @Override public void checkEndCondition() { if (_participants.size() <= 1) finishEvent(); }
        @Override public void finishEvent() { _state = EventState.FINISHED; if (!_participants.isEmpty()) { _winnerId = _participants.get(0).getObjectId(); } rewardParticipants(); saveEventHistory(); teleportPlayersBack(); cancelAfterDelay(); }
        @Override public void abort() { _state = EventState.ABORTED; teleportPlayersBack(); }
        private void teleportPlayersBack() { for (Player p : _participants) if (p != null && p.isOnline()) p.teleToLocation(p.getLastTownTeleport(), 0); }
        private void cancelAfterDelay() { ThreadPool.schedule(() -> EventEngine.getInstance().cancelEvent(getId()), 30000); }
    }

    public static class ArenaEvent extends AbstractEvent
    {
        private final int _teamSize;

        public ArenaEvent(ScheduledEvent scheduled, int teamSize)
        {
            super(scheduled);
            _teamSize = teamSize;
        }

        @Override public void teleportPlayersToArena()
        {
            _participants = getRegisteredPlayers(_scheduled.type);
            if (_participants.isEmpty()) return;
            // Pair players into teams
            Collections.shuffle(_participants);
        }

        @Override public void startBattle() { _state = EventState.RUNNING; _scheduled.startTime = System.currentTimeMillis(); }
        @Override public void onPlayerDeath(Player victim, Player killer) { /* eliminate team */ checkEndCondition(); }
        @Override public void onPlayerKill(Player killer, Player victim) { _kills.merge(killer.getObjectId(), 1, Integer::sum); }
        @Override public void checkEndCondition() { /* check if only one team remains */ }
        @Override public void finishEvent() { _state = EventState.FINISHED; rewardParticipants(); saveEventHistory(); teleportPlayersBack(); cancelAfterDelay(); }
        @Override public void abort() { _state = EventState.ABORTED; teleportPlayersBack(); }
        private void teleportPlayersBack() { for (Player p : _participants) if (p != null && p.isOnline()) p.teleToLocation(p.getLastTownTeleport(), 0); }
        private void cancelAfterDelay() { ThreadPool.schedule(() -> EventEngine.getInstance().cancelEvent(getId()), 30000); }
    }

    public static class GvGEvent extends AbstractEvent
    {
        private final int _teamSize;

        public GvGEvent(ScheduledEvent scheduled, int teamSize)
        {
            super(scheduled);
            _teamSize = teamSize;
        }

        @Override public void teleportPlayersToArena()
        {
            _participants = getRegisteredPlayers(_scheduled.type);
            if (_participants.isEmpty()) return;
            // Clan-based teams
            Collections.shuffle(_participants);
        }

        @Override public void startBattle() { _state = EventState.RUNNING; _scheduled.startTime = System.currentTimeMillis(); }
        @Override public void onPlayerDeath(Player victim, Player killer) { /* clan team elimination */ }
        @Override public void onPlayerKill(Player killer, Player victim) { _kills.merge(killer.getObjectId(), 1, Integer::sum); }
        @Override public void checkEndCondition() { /* check clan team status */ }
        @Override public void finishEvent() { _state = EventState.FINISHED; /* add clan rating points */ ClanRatingManager.getInstance().addSiegeWinPoints(/* winner clan */); rewardParticipants(); saveEventHistory(); teleportPlayersBack(); cancelAfterDelay(); }
        @Override public void abort() { _state = EventState.ABORTED; teleportPlayersBack(); }
        private void teleportPlayersBack() { for (Player p : _participants) if (p != null && p.isOnline()) p.teleToLocation(p.getLastTownTeleport(), 0); }
        private void cancelAfterDelay() { ThreadPool.schedule(() -> EventEngine.getInstance().cancelEvent(getId()), 30000); }
    }

    // ===== Data Classes =====

    private static class ScheduledEvent
    {
        int id;
        String type;
        String name;
        String cron;
        int minLevel = 1;
        int maxLevel = 85;
        int minPlayers = 4;
        int maxPlayers = 50;
        String rewards = "";
        long startTime = 0;
        long lastRun = 0;
    }

    private static class SingletonHolder
    {
        protected static final EventEngine INSTANCE = new EventEngine();
    }
}
