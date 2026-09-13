USE mindharbor;

SET @session_mode_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_sessions'
      AND column_name = 'mode'
);
SET @session_mode_sql = IF(
    @session_mode_exists = 0,
    'ALTER TABLE chat_sessions ADD COLUMN mode VARCHAR(32) NOT NULL DEFAULT ''COMPANION''',
    'SELECT 1'
);
PREPARE session_mode_statement FROM @session_mode_sql;
EXECUTE session_mode_statement;
DEALLOCATE PREPARE session_mode_statement;

SET @favorite_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_messages'
      AND column_name = 'favorite'
);
SET @favorite_sql = IF(
    @favorite_exists = 0,
    'ALTER TABLE chat_messages ADD COLUMN favorite BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
);
PREPARE favorite_statement FROM @favorite_sql;
EXECUTE favorite_statement;
DEALLOCATE PREPARE favorite_statement;
