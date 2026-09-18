-- 系统通知：全体推送只落一行，已读状态放在关联表，采用读时扇出
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
