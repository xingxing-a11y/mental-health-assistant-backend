-- 敏感词词库与命中记录：命中记录只保存词条编号与级别，不保存用户原文
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
