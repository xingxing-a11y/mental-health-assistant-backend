-- 放松小工具：练习记录与压力打卡
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
