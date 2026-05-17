DROP PROCEDURE IF EXISTS add_receipt_datetime;
CREATE PROCEDURE add_receipt_datetime()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'expenses'
          AND COLUMN_NAME = 'receipt_datetime'
    ) THEN
        ALTER TABLE expenses ADD COLUMN receipt_datetime DATETIME NULL COMMENT 'OCR 추출 결제 시각 (통장 대사 매칭용)';
    END IF;
END;
CALL add_receipt_datetime();
DROP PROCEDURE IF EXISTS add_receipt_datetime;
