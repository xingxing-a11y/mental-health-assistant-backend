-- 高危预警的人工处理结果。
--
-- 背景：原来只有 archived_at 一个时间戳，管理员点了"标记处理"也没记录做了什么，
-- 事后回看只知道"有人点过"，不知道是打了电话、转介了机构，还是判定为误报。
--
-- MySQL 8.0 的 ADD COLUMN 不支持 IF NOT EXISTS（那是 MariaDB 的扩展），
-- 这里先查 information_schema 再动态执行，保证重复部署不会报 Duplicate column。

SET @add_handle_result := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE risk_alerts ADD COLUMN handle_result VARCHAR(32) NULL COMMENT ''处理方式：CONTACTED/FAMILY_NOTIFIED/REFERRED/OBSERVING/FALSE_ALARM''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'risk_alerts'
      AND COLUMN_NAME = 'handle_result');
PREPARE stmt_result FROM @add_handle_result;
EXECUTE stmt_result;
DEALLOCATE PREPARE stmt_result;

SET @add_handle_note := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE risk_alerts ADD COLUMN handle_note VARCHAR(200) NULL COMMENT ''处理备注''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'risk_alerts'
      AND COLUMN_NAME = 'handle_note');
PREPARE stmt_note FROM @add_handle_note;
EXECUTE stmt_note;
DEALLOCATE PREPARE stmt_note;

SET @add_handled_by := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE risk_alerts ADD COLUMN handled_by BIGINT NULL COMMENT ''处理人（管理员用户编号）''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'risk_alerts'
      AND COLUMN_NAME = 'handled_by');
PREPARE stmt_by FROM @add_handled_by;
EXECUTE stmt_by;
DEALLOCATE PREPARE stmt_by;
