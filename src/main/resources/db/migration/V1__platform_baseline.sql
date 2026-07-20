CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(190) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    entity_version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_account_email UNIQUE (email),
    INDEX idx_account_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE escape_games (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    slug VARCHAR(80) NOT NULL,
    title VARCHAR(120) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    intro LONGTEXT NOT NULL,
    cover_image_url VARCHAR(1000),
    accent_color VARCHAR(7) NOT NULL DEFAULT '#8B5CF6',
    secondary_color VARCHAR(7) NOT NULL DEFAULT '#EC4899',
    background_color VARCHAR(7) NOT NULL DEFAULT '#0B1020',
    game_icon VARCHAR(16) NOT NULL DEFAULT '🔐',
    allow_notebook BIT NOT NULL DEFAULT TRUE,
    allow_cluebook BIT NOT NULL DEFAULT TRUE,
    allow_qr_scanner BIT NOT NULL DEFAULT TRUE,
    bgm_url VARCHAR(1000),
    bgm_title VARCHAR(200),
    bgm_creator VARCHAR(200),
    bgm_license VARCHAR(100),
    bgm_license_url VARCHAR(1000),
    bgm_source_url VARCHAR(1000),
    bgm_volume DOUBLE NOT NULL DEFAULT 0.55,
    bgm_loop BIT NOT NULL DEFAULT TRUE,
    story_text_speed INT NOT NULL DEFAULT 32,
    enable_vignette BIT NOT NULL DEFAULT TRUE,
    theme VARCHAR(30) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    estimated_minutes INT NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    entity_version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_game_slug UNIQUE (slug),
    CONSTRAINT fk_game_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id),
    INDEX idx_game_owner_updated (owner_id, updated_at),
    INDEX idx_game_discovery (status, visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE game_stages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_id BIGINT NOT NULL,
    stable_key VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    title VARCHAR(120) NOT NULL,
    story LONGTEXT NOT NULL,
    instruction VARCHAR(500) NOT NULL,
    hint VARCHAR(500) NOT NULL,
    puzzle_type VARCHAR(30) NOT NULL,
    draft_answer LONGTEXT,
    options_text LONGTEXT,
    lock_length INT NOT NULL,
    required_item VARCHAR(36),
    reward_item VARCHAR(36),
    story_effect VARCHAR(20) NOT NULL DEFAULT 'FADE',
    scene_image_url VARCHAR(1000),
    sfx_url VARCHAR(1000),
    sfx_title VARCHAR(200),
    sfx_creator VARCHAR(200),
    sfx_license VARCHAR(100),
    sfx_license_url VARCHAR(1000),
    sfx_source_url VARCHAR(1000),
    sfx_volume DOUBLE NOT NULL DEFAULT 0.8,
    entity_version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stage_game_key UNIQUE (game_id, stable_key),
    CONSTRAINT fk_stage_game FOREIGN KEY (game_id) REFERENCES escape_games (id),
    INDEX idx_stage_game_position (game_id, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE game_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_id BIGINT NOT NULL,
    stable_key VARCHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    emoji VARCHAR(16) NOT NULL,
    item_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOM',
    image_url VARCHAR(500),
    clue_text VARCHAR(2000) NOT NULL DEFAULT '',
    qr_enabled BIT NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_item_game_key UNIQUE (game_id, stable_key),
    CONSTRAINT fk_item_game FOREIGN KEY (game_id) REFERENCES escape_games (id),
    INDEX idx_item_game (game_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE game_releases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_release_game_version UNIQUE (game_id, version_number),
    CONSTRAINT fk_release_game FOREIGN KEY (game_id) REFERENCES escape_games (id),
    INDEX idx_release_game_version (game_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE play_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_token_hash VARCHAR(64) NOT NULL,
    release_id BIGINT NOT NULL,
    progress_index INT NOT NULL,
    inventory_json LONGTEXT NOT NULL,
    notes LONGTEXT,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6),
    attempt_count INT NOT NULL,
    hints_used INT NOT NULL,
    entity_version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_play_release FOREIGN KEY (release_id) REFERENCES game_releases (id),
    INDEX idx_play_device_status (device_token_hash, status, last_activity_at),
    INDEX idx_play_release (release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE play_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    play_session_id BIGINT NOT NULL,
    stage_stable_key VARCHAR(36) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    success BIT NOT NULL,
    attempted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attempt_session FOREIGN KEY (play_session_id) REFERENCES play_sessions (id),
    INDEX idx_attempt_session_time (play_session_id, attempted_at),
    INDEX idx_attempt_stage (stage_stable_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE scanned_clues (
    id BIGINT NOT NULL AUTO_INCREMENT,
    play_session_id BIGINT NOT NULL,
    item_stable_key VARCHAR(36) NOT NULL,
    scanned_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_scanned_clue_session_item UNIQUE (play_session_id, item_stable_key),
    CONSTRAINT fk_clue_session FOREIGN KEY (play_session_id) REFERENCES play_sessions (id),
    INDEX idx_scanned_clue_session_time (play_session_id, scanned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
