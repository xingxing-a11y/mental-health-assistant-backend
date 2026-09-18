-- 用户偏好设置表与注销时间字段，注销只做匿名化，不删除业务数据
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

ALTER TABLE users
    ADD COLUMN deactivated_at TIMESTAMP(6) NULL;
