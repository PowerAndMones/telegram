
    // Champion Monsters
    public static boolean CHAMPION_MONSTERS_ENABLED;
    public static String CHAMPION_TIER_LOW;
    public static String CHAMPION_TIER_MEDIUM;
    public static String CHAMPION_TIER_HIGH;
    public static String CHAMPION_TIER_ULTRA;
    public static int CHAMPION_GLOBAL_RESPAWN_MIN;
    public static int CHAMPION_GLOBAL_RESPAWN_MAX;
    public static int CHAMPION_MAX_CONCURRENT_LOW;
    public static int CHAMPION_MAX_CONCURRENT_MEDIUM;
    public static int CHAMPION_MAX_CONCURRENT_HIGH;
    public static int CHAMPION_MAX_CONCURRENT_ULTRA;
    public static boolean CHAMPION_ANNOUNCE_SPAWN;
    public static boolean CHAMPION_ANNOUNCE_DEATH;
    public static boolean CHAMPION_EFFECT_GLOW;
    public static boolean CHAMPION_EFFECT_TITLE;
    public static double CHAMPION_EFFECT_SIZE;

    // Load method additions
    private static void loadChampionConfig(ConfigReader config)
    {
        CHAMPION_MONSTERS_ENABLED = config.getBoolean("ChampionMonstersEnabled", true);
        CHAMPION_TIER_LOW = config.getString("ChampionTierLow", "LOW,20,40,2.0,1.5,1.5,2.0,2.0,true");
        CHAMPION_TIER_MEDIUM = config.getString("ChampionTierMedium", "MEDIUM,40,60,3.0,2.0,2.0,3.0,3.0,true");
        CHAMPION_TIER_HIGH = config.getString("ChampionTierHigh", "HIGH,60,75,4.0,2.5,2.5,5.0,4.0,true");
        CHAMPION_TIER_ULTRA = config.getString("ChampionTierUltra", "ULTRA,76,85,6.0,3.0,3.0,10.0,5.0,true");
        CHAMPION_GLOBAL_RESPAWN_MIN = config.getInt("ChampionGlobalRespawnMin", 30);
        CHAMPION_GLOBAL_RESPAWN_MAX = config.getInt("ChampionGlobalRespawnMax", 60);
        CHAMPION_MAX_CONCURRENT_LOW = config.getInt("ChampionMaxConcurrentLow", 10);
        CHAMPION_MAX_CONCURRENT_MEDIUM = config.getInt("ChampionMaxConcurrentMedium", 5);
        CHAMPION_MAX_CONCURRENT_HIGH = config.getInt("ChampionMaxConcurrentHigh", 3);
        CHAMPION_MAX_CONCURRENT_ULTRA = config.getInt("ChampionMaxConcurrentUltra", 1);
        CHAMPION_ANNOUNCE_SPAWN = config.getBoolean("ChampionAnnounceSpawn", true);
        CHAMPION_ANNOUNCE_DEATH = config.getBoolean("ChampionAnnounceDeath", true);
        CHAMPION_EFFECT_GLOW = config.getBoolean("ChampionEffectGlow", true);
        CHAMPION_EFFECT_TITLE = config.getBoolean("ChampionEffectTitle", true);
        CHAMPION_EFFECT_SIZE = config.getDouble("ChampionEffectSize", 1.2);
    }
