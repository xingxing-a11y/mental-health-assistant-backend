USE mindharbor;

ALTER TABLE assessment_questions
    ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'SINGLE'
    AFTER content;
