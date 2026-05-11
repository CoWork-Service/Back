CREATE TABLE expense_photo_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id BIGINT NOT NULL,
    photo_id BIGINT NOT NULL,
    UNIQUE KEY uq_expense_photo_links_expense_photo (expense_id, photo_id),
    CONSTRAINT fk_expense_photo_links_expense
        FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_photo_links_photo
        FOREIGN KEY (photo_id) REFERENCES event_photos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE mobile_sessions
    ADD COLUMN expense_id BIGINT NULL AFTER photo_path,
    ADD CONSTRAINT fk_mobile_sessions_expense
        FOREIGN KEY (expense_id) REFERENCES expenses(id);
