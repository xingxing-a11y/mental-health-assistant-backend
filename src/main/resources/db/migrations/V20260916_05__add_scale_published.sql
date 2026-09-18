-- 量表新增发布状态：0=未发布（草稿，用户端不可见），1=已发布
ALTER TABLE assessment_scales
    ADD COLUMN published TINYINT(1) NOT NULL DEFAULT 0;

UPDATE assessment_scales SET published = 1;
