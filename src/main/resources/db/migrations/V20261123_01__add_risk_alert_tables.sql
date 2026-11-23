-- 高危信号预警事件链：事件主表（兼发布 outbox）、消费幂等、24h 回访任务、按日统计。
-- 项目未引入 Flyway/Liquibase，本文件需在部署时手工执行一次再推代码。

CREATE TABLE IF NOT EXISTS risk_alerts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 幂等键：chat:{messageId} / assessment:{resultId} / diary:{userId}:{yyyyMMdd}
    alert_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    -- CHAT_HIGH / ASSESSMENT_HIGH / DIARY_STREAK
    source VARCHAR(24) NOT NULL,
    -- HIGH / MEDIUM
    level VARCHAR(16) NOT NULL,
    -- 只描述事件，绝不写入用户原文
    summary VARCHAR(500) NOT NULL,
    -- JSON：sessionId / messageId / resultId 等关联编号
    refs VARCHAR(255) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    -- PENDING / SENT / FAILED；这张表同时是发布的 outbox
    publish_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    publish_attempts INT NOT NULL DEFAULT 0,
    -- 管理员人工标记「已处理」的时间，NULL 表示未处理
    archived_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_alerts_alert_id (alert_id),
    KEY idx_risk_alerts_publish (publish_status, created_at),
    KEY idx_risk_alerts_user_created (user_id, created_at),
    KEY idx_risk_alerts_source_created (source, created_at),
    CONSTRAINT fk_risk_alerts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

-- 消费幂等：唯一键即锁，与业务写在同一事务内插入，业务失败一起回滚。
CREATE TABLE IF NOT EXISTS risk_alert_consumptions (
    alert_id VARCHAR(64) NOT NULL,
    -- NOTIFY / STATS / FOLLOWUP
    consumer VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (alert_id, consumer),
    KEY idx_risk_consumptions_created (created_at)
) ENGINE = InnoDB;

-- 24h 回访任务：这张表是真源，延迟队列只是「闹钟」；消息丢了由补偿任务扫描补发。
CREATE TABLE IF NOT EXISTS care_followups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    alert_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    due_at TIMESTAMP(6) NOT NULL,
    -- PENDING / SENT / SKIPPED
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_care_followups_alert (alert_id),
    KEY idx_care_followups_due (status, due_at),
    CONSTRAINT fk_care_followups_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

-- 按日统计：写入走 INSERT ... ON DUPLICATE KEY UPDATE total = total + 1，并发不丢计数。
CREATE TABLE IF NOT EXISTS risk_alert_daily_stats (
    stat_date DATE NOT NULL,
    source VARCHAR(24) NOT NULL,
    total INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (stat_date, source)
) ENGINE = InnoDB;
