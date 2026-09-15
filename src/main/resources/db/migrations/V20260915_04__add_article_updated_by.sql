USE mindharbor;

ALTER TABLE articles
    ADD COLUMN updated_by VARCHAR(255) NULL AFTER updated_at;
