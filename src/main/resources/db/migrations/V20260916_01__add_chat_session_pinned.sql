-- Adds persistent pinning for chat sessions.
-- Run once on existing databases.

USE mindharbor;

ALTER TABLE chat_sessions
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE AFTER high_risk;
