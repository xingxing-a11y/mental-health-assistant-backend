-- 全局系统配置：知识库分片参数等
CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(64) NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (setting_key)
) ENGINE = InnoDB;
