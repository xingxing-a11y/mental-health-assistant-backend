-- 测评量表收藏
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
