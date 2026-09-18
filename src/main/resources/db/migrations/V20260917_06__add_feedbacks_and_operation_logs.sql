-- 用户反馈工单与管理员操作日志
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
