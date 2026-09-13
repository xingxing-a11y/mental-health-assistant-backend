-- MindHarbor database migration
-- Adds diary storage and the standardized assessment score.
-- Safe to run once; existing objects are preserved.

USE mindharbor;

CREATE TABLE IF NOT EXISTS diaries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content LONGTEXT NOT NULL,
    emotion VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_diaries_user_id (user_id),
    CONSTRAINT fk_diaries_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'assessment_results'
      AND column_name = 'standard_score'
);

SET @add_column_sql = IF(
    @column_exists = 0,
    'ALTER TABLE assessment_results ADD COLUMN standard_score DOUBLE NULL AFTER total_score',
    'SELECT 1'
);

PREPARE add_column_statement FROM @add_column_sql;
EXECUTE add_column_statement;
DEALLOCATE PREPARE add_column_statement;

