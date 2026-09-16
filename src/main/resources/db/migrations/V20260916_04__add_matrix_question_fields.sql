ALTER TABLE assessment_questions
    ADD COLUMN matrix_rows TEXT NULL AFTER type,
    ADD COLUMN matrix_columns TEXT NULL AFTER matrix_rows;
