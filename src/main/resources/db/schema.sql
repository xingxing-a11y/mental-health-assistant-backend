-- Mental Health Assistant database schema.
-- This script creates structure only. It intentionally inserts no seed data.

CREATE DATABASE IF NOT EXISTS mindharbor
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE mindharbor;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(255),
    avatar_url VARCHAR(512),
    gender VARCHAR(32),
    age INT,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(6) NULL,
    deactivated_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    preview VARCHAR(255),
    latest_message_at TIMESTAMP(6),
    context_cleared BOOLEAN NOT NULL DEFAULT FALSE,
    high_risk BOOLEAN NOT NULL DEFAULT FALSE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_chat_sessions_user_id (user_id),
    CONSTRAINT fk_chat_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_messages_session_id (session_id),
    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS assessment_scales (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description LONGTEXT,
    applicable_scene VARCHAR(255),
    published TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS assessment_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scale_id BIGINT NOT NULL,
    question_order INT,
    content VARCHAR(255),
    type VARCHAR(32) NOT NULL DEFAULT 'SINGLE',
    matrix_rows TEXT,
    matrix_columns TEXT,
    PRIMARY KEY (id),
    KEY idx_assessment_questions_scale_id (scale_id),
    CONSTRAINT fk_assessment_questions_scale
        FOREIGN KEY (scale_id) REFERENCES assessment_scales (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS assessment_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    label VARCHAR(255),
    score INT,
    PRIMARY KEY (id),
    KEY idx_assessment_options_question_id (question_id),
    CONSTRAINT fk_assessment_options_question
        FOREIGN KEY (question_id) REFERENCES assessment_questions (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS assessment_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    scale_id BIGINT NOT NULL,
    total_score INT,
    standard_score DOUBLE,
    range_description VARCHAR(255),
    suggestions VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_assessment_results_user_id (user_id),
    KEY idx_assessment_results_scale_id (scale_id),
    CONSTRAINT fk_assessment_results_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_assessment_results_scale
        FOREIGN KEY (scale_id) REFERENCES assessment_scales (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS articles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(255),
    content LONGTEXT,
    updated_at TIMESTAMP(6) NOT NULL,
    updated_by VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    vector_status VARCHAR(32) NOT NULL DEFAULT 'NOT_VECTORIZED',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS diaries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content LONGTEXT NOT NULL,
    emotion VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_diaries_user_id (user_id),
    CONSTRAINT fk_diaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS user_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    default_chat_mode VARCHAR(32) NOT NULL DEFAULT 'COMPANION',
    stream_speed VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    message_retention VARCHAR(32) NOT NULL DEFAULT 'PERMANENT',
    data_consent TINYINT(1) NOT NULL DEFAULT 0,
    theme VARCHAR(32) NOT NULL DEFAULT 'LIGHT',
    font_scale VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    message_sound TINYINT(1) NOT NULL DEFAULT 1,
    crisis_tip TINYINT(1) NOT NULL DEFAULT 1,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_settings_user_id (user_id),
    CONSTRAINT fk_user_settings_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS relax_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    duration_seconds INT NOT NULL,
    rounds INT,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_relax_records_user_created (user_id, created_at),
    CONSTRAINT fk_relax_records_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS stress_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    level INT NOT NULL,
    note VARCHAR(200),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stress_logs_user_created (user_id, created_at),
    CONSTRAINT fk_stress_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS assessment_favorites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    scale_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assessment_favorites_user_scale (user_id, scale_id),
    CONSTRAINT fk_assessment_favorites_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_assessment_favorites_scale
        FOREIGN KEY (scale_id) REFERENCES assessment_scales (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scope VARCHAR(16) NOT NULL,
    target_user_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expire_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_scope_created (scope, created_at),
    KEY idx_notifications_target_created (target_user_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS notification_reads (
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (notification_id, user_id),
    KEY idx_notification_reads_user (user_id),
    CONSTRAINT fk_notification_reads_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_notification_reads_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS sensitive_words (
    id BIGINT NOT NULL AUTO_INCREMENT,
    word VARCHAR(64) NOT NULL,
    level VARCHAR(16) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sensitive_words_word (word)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS sensitive_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id BIGINT NULL,
    word_id BIGINT NULL,
    level VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sensitive_events_user_created (user_id, created_at),
    KEY idx_sensitive_events_level_created (level, created_at),
    CONSTRAINT fk_sensitive_events_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS feedbacks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    contact VARCHAR(200),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    remark VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_feedbacks_status_created (status, created_at),
    CONSTRAINT fk_feedbacks_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS admin_operation_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_id BIGINT NULL,
    operator_name VARCHAR(255),
    type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id BIGINT NULL,
    summary VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_admin_operation_logs_created (created_at),
    KEY idx_admin_operation_logs_type_created (type, created_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(64) NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (setting_key)
) ENGINE = InnoDB;
