ALTER TABLE cohort_members
    MODIFY COLUMN department VARCHAR(100) NULL;

ALTER TABLE workspaces
    MODIFY COLUMN department VARCHAR(100) NULL;

ALTER TABLE cowork_events
    MODIFY COLUMN lead_department VARCHAR(100) NULL;

ALTER TABLE expenses
    MODIFY COLUMN department VARCHAR(100) NOT NULL;

ALTER TABLE file_items
    MODIFY COLUMN department VARCHAR(100) NULL;

ALTER TABLE memos
    MODIFY COLUMN department VARCHAR(100) NULL;
