ALTER TABLE rental_records
    ADD COLUMN manager_name VARCHAR(50) NULL AFTER student_id,
    ADD COLUMN id_card_submitted BOOLEAN NOT NULL DEFAULT FALSE AFTER manager_name;
