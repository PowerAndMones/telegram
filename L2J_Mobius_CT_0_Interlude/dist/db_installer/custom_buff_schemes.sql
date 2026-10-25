-- Custom Buff Schemes Table
-- Run this after main database installation

CREATE TABLE IF NOT EXISTS `character_buff_schemes` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `char_id` INT NOT NULL,
  `scheme_name` VARCHAR(64) NOT NULL,
  `buff_list` TEXT NOT NULL,
  `is_default` TINYINT(1) NOT NULL DEFAULT 0,
  `created_time` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_char_scheme` (`char_id`, `scheme_name`),
  KEY `idx_char_id` (`char_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Event Engine Tables
CREATE TABLE IF NOT EXISTS `event_participants` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `event_type` VARCHAR(32) NOT NULL,
  `event_id` INT NOT NULL,
  `char_id` INT NOT NULL,
  `team_id` INT NOT NULL DEFAULT 0,
  `score` INT NOT NULL DEFAULT 0,
  `kills` INT NOT NULL DEFAULT 0,
  `deaths` INT NOT NULL DEFAULT 0,
  `reward_given` TINYINT(1) NOT NULL DEFAULT 0,
  `register_time` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_event` (`event_type`, `event_id`),
  KEY `idx_char` (`char_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `event_history` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `event_type` VARCHAR(32) NOT NULL,
  `event_name` VARCHAR(128) NOT NULL,
  `winner_team` INT NOT NULL DEFAULT 0,
  `winner_char` INT NOT NULL DEFAULT 0,
  `participants_count` INT NOT NULL DEFAULT 0,
  `start_time` BIGINT NOT NULL DEFAULT 0,
  `end_time` BIGINT NOT NULL DEFAULT 0,
  `rewards_data` TEXT,
  PRIMARY KEY (`id`),
  KEY `idx_type_time` (`event_type`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `event_schedule` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `event_type` VARCHAR(32) NOT NULL,
  `event_name` VARCHAR(128) NOT NULL,
  `cron_expression` VARCHAR(64) NOT NULL,
  `min_level` INT NOT NULL DEFAULT 1,
  `max_level` INT NOT NULL DEFAULT 85,
  `min_players` INT NOT NULL DEFAULT 2,
  `max_players` INT NOT NULL DEFAULT 50,
  `reward_items` TEXT,
  `is_active` TINYINT(1) NOT NULL DEFAULT 1,
  `created_time` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Daily Rewards
CREATE TABLE IF NOT EXISTS `character_daily_rewards` (
  `char_id` INT NOT NULL,
  `last_reward_day` INT NOT NULL DEFAULT 0,
  `streak_days` INT NOT NULL DEFAULT 0,
  `total_rewards` INT NOT NULL DEFAULT 0,
  `premium_streak` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`char_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Vote Rewards
CREATE TABLE IF NOT EXISTS `character_vote_rewards` (
  `char_id` INT NOT NULL,
  `site_name` VARCHAR(32) NOT NULL,
  `last_vote_time` BIGINT NOT NULL DEFAULT 0,
  `total_votes` INT NOT NULL DEFAULT 0,
  `rewards_claimed` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`char_id`, `site_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Champion Monsters
CREATE TABLE IF NOT EXISTS `champion_monster_spawns` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `npc_id` INT NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `tier` VARCHAR(16) NOT NULL,
  `x` INT NOT NULL,
  `y` INT NOT NULL,
  `z` INT NOT NULL,
  `heading` INT NOT NULL DEFAULT 0,
  `respawn_min` INT NOT NULL DEFAULT 60,
  `respawn_max` INT NOT NULL DEFAULT 120,
  `reward_adena_multiplier` DOUBLE NOT NULL DEFAULT 1.0,
  `reward_drop_multiplier` DOUBLE NOT NULL DEFAULT 1.0,
  `is_active` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_npc` (`npc_id`),
  KEY `idx_tier` (`tier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Anti-cheat HWID
CREATE TABLE IF NOT EXISTS `character_hwid` (
  `char_id` INT NOT NULL,
  `hwid` VARCHAR(64) NOT NULL,
  `first_seen` BIGINT NOT NULL DEFAULT 0,
  `last_seen` BIGINT NOT NULL DEFAULT 0,
  `is_banned` TINYINT(1) NOT NULL DEFAULT 0,
  `ban_reason` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`char_id`),
  KEY `idx_hwid` (`hwid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Clan Divisions
CREATE TABLE IF NOT EXISTS `clan_division` (
  `clan_id` INT NOT NULL,
  `division` VARCHAR(16) NOT NULL DEFAULT 'LOWWAR',
  `points` BIGINT NOT NULL DEFAULT 0,
  `wins` INT NOT NULL DEFAULT 0,
  `losses` INT NOT NULL DEFAULT 0,
  `last_update` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`clan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Battle Pass
CREATE TABLE IF NOT EXISTS `battle_pass_seasons` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `season_number` INT NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `start_time` BIGINT NOT NULL,
  `end_time` BIGINT NOT NULL,
  `max_level` INT NOT NULL DEFAULT 100,
  `xp_per_level` INT NOT NULL DEFAULT 1000,
  `free_rewards` TEXT,
  `premium_rewards` TEXT,
  `is_active` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_number` (`season_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `character_battle_pass` (
  `char_id` INT NOT NULL,
  `season_id` INT NOT NULL,
  `current_level` INT NOT NULL DEFAULT 1,
  `current_xp` BIGINT NOT NULL DEFAULT 0,
  `is_premium` TINYINT(1) NOT NULL DEFAULT 0,
  `claimed_free` TEXT DEFAULT '[]',
  `claimed_premium` TEXT DEFAULT '[]',
  `last_update` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`char_id`, `season_id`),
  KEY `idx_season` (`season_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Player Market
CREATE TABLE IF NOT EXISTS `market_listings` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `seller_id` INT NOT NULL,
  `seller_name` VARCHAR(45) NOT NULL,
  `item_id` INT NOT NULL,
  `item_count` INT NOT NULL,
  `item_enchant` INT NOT NULL DEFAULT 0,
  `item_attributes` TEXT,
  `price` BIGINT NOT NULL,
  `fee` BIGINT NOT NULL DEFAULT 0,
  `status` TINYINT(1) NOT NULL DEFAULT 0,
  `created_time` BIGINT NOT NULL DEFAULT 0,
  `sold_time` BIGINT DEFAULT NULL,
  `buyer_id` INT DEFAULT NULL,
  `buyer_name` VARCHAR(45) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_seller` (`seller_id`),
  KEY `idx_item` (`item_id`),
  KEY `idx_status_price` (`status`, `price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Subclass/Noblesse Custom
CREATE TABLE IF NOT EXISTS `character_subclass_data` (
  `char_id` INT NOT NULL,
  `class_id` INT NOT NULL,
  `level` INT NOT NULL DEFAULT 40,
  `exp` BIGINT NOT NULL DEFAULT 0,
  `sp` BIGINT NOT NULL DEFAULT 0,
  `is_active` TINYINT(1) NOT NULL DEFAULT 0,
  `certification` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`char_id`, `class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

