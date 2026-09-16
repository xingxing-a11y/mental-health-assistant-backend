-- Adds recoverable archiving for chat sessions.
-- Run once on existing databases.

USE mindharbor;

ALTER TABLE chat_sessions
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE AFTER pinned;
